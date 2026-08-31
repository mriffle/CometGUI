/*
 * CometGUI -- Comet to Percolator proteomics search workflow with provenance.
 * Copyright (C) 2026 The CometGUI authors.
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License, version 3, as published
 * by the Free Software Foundation. It is distributed WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for details.
 *
 * The full licence is the LICENSE file at the root of this repository. If it
 * is missing, see <https://www.gnu.org/licenses/gpl-3.0.html>.
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package org.cometgui.provenance.events;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.cometgui.domain.secrets.SecretRedactor;

/**
 * Recovers everything recoverable from an event log, however badly the writing process died.
 *
 * <p>This class is phase 04's exit gate item 4: "a crash simulated mid-run leaves a parsable event
 * log with usable history". The reader is the half that makes that true, and its contract is
 * deliberately unusual for a parser -- <strong>it does not throw on damage</strong>. Damage is what
 * it was written for. An {@link IOException} means the file could not be read at all; anything
 * wrong with the file's <em>content</em> comes back as an {@link EventLogDefect} on a {@link
 * RecoveredEventLog}, beside every event that survived.
 *
 * <p><strong>Why not throw, and why not shrug.</strong> Both of the obvious behaviours fail the
 * requirement in opposite directions. A reader that threw on the first bad byte would discard a
 * crashed run's entire history -- and the crashed run is the one whose history is worth having,
 * because the successful one has a manifest. A reader that quietly skipped what it could not parse
 * would hand back a plausible-looking history with an unannounced hole in it, and a scientist would
 * have no way to know that the log they are reading is not the log the run wrote. So every event
 * that can be recovered is recovered, and every discrepancy is reported with its line, its byte
 * offset and what was wrong.
 *
 * <p><strong>What the reader treats as damage.</strong>
 *
 * <ul>
 *   <li><b>A torn final line</b> -- bytes after the last newline. This is the ordinary crash: the
 *       process died between {@code write} and the terminator. Those bytes are never parsed, even
 *       when they look complete, because without the terminator there is no way to tell a whole
 *       record from one whose tail never reached the disk.
 *   <li><b>A malformed line</b> -- one that ends in a newline but is not a record this application
 *       wrote. That covers an empty line, a line that is not valid UTF-8, and a line whose JSON is
 *       not the exact form {@link EventLineFormat} writes. Trailing {@code NUL} bytes, which is
 *       what several filesystems leave in a file's last block after a power loss, arrive here.
 *   <li><b>A sequence gap</b> -- a line whose number is not one more than the previous one. This is
 *       the only damage that leaves no trace in the bytes: a lost record takes its whole line with
 *       it and every remaining line is perfectly well formed. Reading continues from the number
 *       actually found, so one hole produces one defect rather than one per later line.
 * </ul>
 *
 * <p><strong>Decoding is strict.</strong> {@link String#String(byte[], java.nio.charset.Charset)}
 * would replace a truncated or corrupt UTF-8 sequence with {@code U+FFFD} and hand back a string
 * that looks like text, so a path with a mangled byte in it would be recovered as a path that was
 * never on the disk. The decoder here reports instead of replacing, and the line becomes a defect.
 *
 * <p>The file is read one line at a time and only one line is ever held in memory, so a log of any
 * length can be recovered; a corrupt file that contains no newline at all is the one case where
 * that line is the whole file.
 *
 * <p><strong>This class is the boundary at which untrusted text enters the application, and it is
 * where the shared rule set is applied.</strong> Almost every defect detail is built from offsets
 * and counts, but one is not: a line whose payload key has the wrong shape is reported with {@link
 * ProvenanceEvent}'s own message, and that message quotes the key. A key is a name, and quoting it
 * is what makes the message useful to the phase author who typed {@code runId} at a call site --
 * but the reader is pointed at files it did not write, and a hostile or corrupted log can carry an
 * arbitrary byte sequence in key position, including a credential. {@code R-SEC-03} has no
 * exception clause in it, so every detail built from a parse failure goes through {@link
 * SecretRedactor} before it becomes an {@link EventLogDefect}, and is then bounded at {@value
 * #MAX_DETAIL_LENGTH} characters so that a megabyte of junk in key position cannot become a
 * megabyte of defect detail.
 *
 * <p><strong>Redaction happens before truncation, and the order is not arbitrary.</strong>
 * Truncating first would cut a secret in half, and half a secret matches neither a pattern rule nor
 * a registered literal -- the exact blind spot {@code SeededSecretCorpusTest} records, where a
 * one-character change to a key body hid it from a sweep that was looking for the whole thing.
 * Cleaning first and cutting second cannot reintroduce what has already been replaced.
 */
public final class ProvenanceEventLogReader {

    /** Bytes read from the file at a time; a line is assembled from the stream, not from this. */
    private static final int READ_BUFFER = 1 << 16;

    /**
     * The longest a defect's detail may be: {@value}.
     *
     * <p>Long enough that the fixed part of the longest message this package produces -- the
     * payload-key rule, which is about 190 characters before the key itself -- still leaves a wide
     * window of the offending key to read, and short enough that a defect list stays a thing a
     * person can look at. See the class documentation for why a bound is needed at all.
     */
    private static final int MAX_DETAIL_LENGTH = 512;

    /** What marks a detail that was cut short. Counted inside {@link #MAX_DETAIL_LENGTH}. */
    private static final String TRUNCATION_MARKER = " [truncated]";

    /**
     * Never instantiated: this is a single operation over a path, with no state of its own.
     *
     * <p>It throws rather than being an empty private constructor so that the intent is enforced
     * for the one caller that can still reach it -- reflection -- instead of merely being implied.
     */
    private ProvenanceEventLogReader() {
        throw new AssertionError(
                "ProvenanceEventLogReader is a utility class and is never instantiated");
    }

    /**
     * Reads an event log, recovering every complete record and reporting everything else.
     *
     * <p>An empty file is a valid, intact log with no events: a run can die before its first
     * append, and there is nothing wrong with what it left behind.
     *
     * @param path the log file to recover
     * @return what was recovered and what was wrong, never {@code null}
     * @throws IOException if the file cannot be opened or read at all -- a missing file, a
     *     directory, a permission failure. Damaged <em>content</em> never throws.
     * @throws NullPointerException if {@code path} is {@code null}
     */
    public static RecoveredEventLog recover(Path path) throws IOException {
        return recover(path, SecretRedactor.patternsOnly());
    }

    /**
     * Reads an event log, cleaning what it quotes with a redactor that knows this run's secrets.
     *
     * <p>The pattern rules alone catch a credential that announces itself -- an {@code
     * Authorization} header, a credential-bearing URL, a secret-looking assignment. They cannot
     * catch a bare token that only the application knows the value of, which is what {@code
     * SecretRegistry} is for. A caller that holds registered credentials should recover through
     * this overload; {@link #recover(Path)} uses the pattern rules alone, which is the right answer
     * for a tool inspecting someone else's log and the wrong one for a live run. {@link
     * ProvenanceEventLog} passes its own redactor here when it reopens a log after a crash.
     *
     * @param path the log file to recover
     * @param redactor the rule set every quoted fragment is cleaned by before it is reported
     * @return what was recovered and what was wrong, never {@code null}
     * @throws IOException if the file cannot be opened or read at all
     * @throws NullPointerException if either argument is {@code null}
     */
    public static RecoveredEventLog recover(Path path, SecretRedactor redactor) throws IOException {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(redactor, "redactor");
        Recovery recovery = new Recovery(redactor);
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        long offset = 0;
        long lineStart = 0;
        try (InputStream in = new BufferedInputStream(Files.newInputStream(path), READ_BUFFER)) {
            int read;
            while ((read = in.read()) >= 0) {
                offset++;
                if (read == EventLineFormat.LINE_TERMINATOR) {
                    recovery.acceptCompleteLine(line.toByteArray(), lineStart);
                    line.reset();
                    lineStart = offset;
                } else {
                    line.write(read);
                }
            }
        }
        if (line.size() > 0) {
            recovery.acceptTornTail(line.size(), lineStart);
        }
        return recovery.result();
    }

    /**
     * The state of one recovery: what has been read, what was wrong, and where we are.
     *
     * <p>A class rather than four local variables because the line-level and file-level rules need
     * the same state and the same two lists, and passing five arguments to each of them would make
     * the order of the arguments the only thing keeping the line numbers right.
     */
    private static final class Recovery {

        /** Reports a bad byte sequence rather than replacing it; see the class documentation. */
        private final CharsetDecoder decoder =
                StandardCharsets.UTF_8
                        .newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT);

        private final List<ProvenanceEvent> events = new ArrayList<>();

        private final List<EventLogDefect> defects = new ArrayList<>();

        /** Cleans every fragment that came out of the file before it is reported. */
        private final SecretRedactor redactor;

        /** The sequence number the next recovered event must carry. */
        private long expectedSequence = ProvenanceEvent.FIRST_SEQUENCE;

        /** Counts every line the file contains, sound or not, so that a defect can be found. */
        private long lineNumber;

        Recovery(SecretRedactor redactor) {
            this.redactor = redactor;
        }

        /**
         * Handles one newline-terminated line.
         *
         * @param raw the line's bytes, without the terminator
         * @param lineStart the offset of the first of those bytes in the file
         */
        void acceptCompleteLine(byte[] raw, long lineStart) {
            lineNumber++;
            if (raw.length == 0) {
                malformed(lineStart, "the line is empty");
                return;
            }
            String text;
            try {
                text = decoder.decode(ByteBuffer.wrap(raw)).toString();
            } catch (CharacterCodingException notUtf8) {
                malformed(lineStart, "the line is not valid UTF-8");
                return;
            }
            ProvenanceEvent event;
            try {
                event = EventLineFormat.parse(text);
            } catch (MalformedEventLineException notARecord) {
                // The one detail that can carry bytes out of the file; see the class comment.
                malformed(lineStart, cleanedAndBounded(notARecord.getMessage()));
                return;
            }
            checkSequence(event, lineStart);
            events.add(event);
        }

        /**
         * Handles the bytes after the last newline, which no record ever ends with.
         *
         * @param length how many bytes were left over
         * @param lineStart the offset at which they start
         */
        void acceptTornTail(int length, long lineStart) {
            lineNumber++;
            defects.add(
                    new EventLogDefect(
                            EventLogDefectKind.TORN_FINAL_LINE,
                            lineNumber,
                            lineStart,
                            "the record starting here has no terminating newline; its length on"
                                    + " disk is "
                                    + length));
        }

        /**
         * Reports a gap when an event's number is not the one that was due.
         *
         * <p>The expectation then follows the file rather than the count, so that a single lost
         * record produces a single defect.
         *
         * @param event the recovered event
         * @param lineStart the offset of the line it came from
         */
        private void checkSequence(ProvenanceEvent event, long lineStart) {
            if (event.sequence() != expectedSequence) {
                defects.add(
                        new EventLogDefect(
                                EventLogDefectKind.SEQUENCE_GAP,
                                lineNumber,
                                lineStart,
                                "expected sequence "
                                        + expectedSequence
                                        + ", but this line carries "
                                        + event.sequence()));
            }
            expectedSequence = event.sequence() + 1;
        }

        /**
         * Cleans a fragment that came out of the file, then bounds it.
         *
         * <p>Redaction first, truncation second, for the reason given on the class: half a secret
         * matches no rule. The cut never falls between the two halves of a surrogate pair, because
         * a lone surrogate is not a character and would be replaced the moment the detail was
         * encoded for a log file or a UI.
         *
         * @param detail the message built from the file's content
         * @return the detail with every recognised secret replaced, at most {@value
         *     #MAX_DETAIL_LENGTH} characters long
         */
        private String cleanedAndBounded(String detail) {
            String cleaned = redactor.redactText(detail);
            if (cleaned.length() <= MAX_DETAIL_LENGTH) {
                return cleaned;
            }
            int cut = MAX_DETAIL_LENGTH - TRUNCATION_MARKER.length();
            if (Character.isHighSurrogate(cleaned.charAt(cut - 1))) {
                cut--;
            }
            return cleaned.substring(0, cut) + TRUNCATION_MARKER;
        }

        /**
         * Records a malformed complete line.
         *
         * @param lineStart the offset of the line
         * @param detail what was wrong, quoting nothing from the file
         */
        private void malformed(long lineStart, String detail) {
            defects.add(
                    new EventLogDefect(
                            EventLogDefectKind.MALFORMED_LINE, lineNumber, lineStart, detail));
        }

        /**
         * The finished result.
         *
         * @return the events and the defects, in the order they were found
         */
        RecoveredEventLog result() {
            return new RecoveredEventLog(events, defects);
        }
    }
}
