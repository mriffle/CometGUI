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

import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * The wire form of one event: how it is written, and how a line found in a file is read back.
 *
 * <p><strong>One event per line, and that is the whole recovery strategy.</strong> The obvious
 * alternative -- a single top-level JSON array of events -- cannot survive the crash this package
 * exists for, because the closing bracket is written last and a process that dies never writes it.
 * Every general-purpose JSON parser then rejects the entire document, and a run's whole history is
 * lost to one missing character. A newline-delimited log has no such global structure: each line
 * stands alone, the damage a crash does is confined to the last one, and everything before it
 * parses exactly as it did before the crash. That is why this class exists rather than a call to a
 * document serialiser.
 *
 * <p>A line is a JSON object with four members in a fixed order:
 *
 * <pre>{@code
 * {"seq":1,"time":"2026-08-31T09:15:00.000Z","type":"run.started","payload":{"run.id":"R-1"}}
 * }</pre>
 *
 * <p>The bytes are UTF-8 and the terminator is {@code \n} on every platform, including Windows. A
 * provenance record whose bytes depend on the machine that wrote it is not a provenance record: a
 * log written with {@code \r\n} would hash differently for the same run, and the {@code \r} would
 * become part of the last field of every line for a reader that split on {@code \n}.
 *
 * <p><strong>The timestamp is fixed width: exactly three fractional digits and a literal {@code
 * Z}.</strong> {@link Instant#toString()} would have been shorter to write and drops trailing
 * zeros, so the same instant renders as {@code ...:00Z} or {@code ...:00.100Z} depending on its
 * value, and two runs would produce log lines of different widths for no reason a reader can see.
 * It is also range-limited: {@link ProvenanceEvent} rejects a timestamp outside the four-digit
 * years, because {@code DateTimeFormatter} renders year 10000 as {@code +10000-...} and a
 * twenty-five character timestamp would not parse back.
 *
 * <p>The formatter states its {@link Locale} and its {@link ZoneOffset} rather than taking the
 * JVM's. The zone is load-bearing: an {@link Instant} carries no zone, so without the override it
 * cannot be formatted at all, and a <em>default</em> zone would render the same instant differently
 * on two machines. The locale is defensive, and the honest statement of what it does is worth
 * making rather than overstating: digits come from {@link java.time.format.DecimalStyle}, which is
 * {@code STANDARD} unless a caller asks for a locale's own, so this pattern emits ASCII digits
 * under any default locale today -- but {@code withDecimalStyle(DecimalStyle.of(
 * Locale.forLanguageTag("ar-SA-u-nu-arab")))} renders every field of it in Arabic-Indic digits,
 * which is exactly the shape of defect {@code R-PROV-04} exists for, and naming the locale here
 * means the guarantee survives a later edit that adds a text field such as {@code MMM}. Where the
 * default locale really can reach a date is the parsing side; see the note on digits below.
 *
 * <p><strong>Parsing resolves strictly.</strong> {@link ResolverStyle#SMART}, the default, accepts
 * {@code 2026-02-30} and silently moves it to the 28th; a provenance timestamp that quietly becomes
 * a different timestamp is worse than one that is rejected, so a date that does not exist is damage
 * and is reported as such.
 *
 * <p><strong>The parser is strict, and it never quotes what it rejected.</strong> Strict because a
 * line that differs from the form above was not written by this application, and silently accepting
 * it would mean an event log whose contents cannot be re-derived from its own bytes -- the argument
 * {@code ProvenanceStatus.fromWireName} makes about wire names, applied to a whole line. Quoting
 * nothing because the reader is pointed at damaged files, and the bytes in a damaged file are not
 * necessarily bytes this application wrote and redacted: an error message that echoed them would
 * carry unredacted content into an exception, a log line and eventually the provenance UI. Every
 * message this class produces names the character offset and what was expected there, and nothing
 * else.
 *
 * <p>One message is not built from offsets alone: {@link #parse} reports a line that {@link
 * ProvenanceEvent} itself rejected by passing that rejection through, and the payload-key rule
 * quotes the offending key. That quotation is what makes the message useful to the phase author who
 * typed {@code runId} at a call site. It is also the one route by which bytes from a file could
 * reach a log or a UI, so it does not travel: {@link ProvenanceEventLogReader} passes every such
 * message through the shared {@code SecretRedactor} and bounds its length before it becomes a
 * defect, which is why the promise "no message this package produces carries file content into a
 * log" still holds without an exception clause. No payload <em>value</em> reaches a message from
 * either class at all.
 *
 * <p><strong>Digits are ASCII digits, tested character by character.</strong> Neither {@link
 * Character#isDigit(char)} nor {@link Long#parseLong(String)} is limited to {@code 0}-{@code 9}:
 * both accept every decimal digit in Unicode, so {@code Long.parseLong("١٢")} is 12. A sequence
 * number written in Arabic-Indic digits is not something this writer can produce, and accepting one
 * would let a foreign document pass for a CometGUI log.
 */
final class EventLineFormat {

    /** The one byte that separates two events. Never {@code \r\n}; see the class documentation. */
    static final char LINE_TERMINATOR = '\n';

    /** The fixed-width UTC timestamp, with three fractional digits and a literal {@code Z}. */
    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT)
                    .withZone(ZoneOffset.UTC)
                    .withResolverStyle(ResolverStyle.STRICT);

    /**
     * The timestamp form named in a parse failure, as an example rather than as a pattern.
     *
     * <p>A reader looking at {@code uuuu-MM-dd'T'HH:mm:ss.SSS'Z'} has to know {@code
     * DateTimeFormatter} to understand the complaint; a reader looking at a rendered instant does
     * not, and the example is not a value from the file.
     */
    private static final String TIMESTAMP_EXAMPLE = "2026-08-31T09:15:00.000Z";

    /** Lower-case hexadecimal digits for {@code \\u00XX} escapes, indexed by nibble. */
    private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

    /**
     * Never instantiated: this class is the format, and the format has no state.
     *
     * <p>It throws rather than being an empty private constructor so that the intent is enforced
     * for the one caller that can still reach it -- reflection -- instead of merely being implied.
     */
    private EventLineFormat() {
        throw new AssertionError("EventLineFormat is a utility class and is never instantiated");
    }

    /**
     * Renders one event as its line, without the terminating newline.
     *
     * <p>The payload is taken from {@link ProvenanceEvent#payload()}, which is sorted, so two
     * events that differ only in the iteration order of the map they were built from render
     * identically.
     *
     * @param event the event to render
     * @return the line, with no {@code \n} of its own
     * @throws NullPointerException if {@code event} is {@code null}
     */
    static String toLine(ProvenanceEvent event) {
        Objects.requireNonNull(event, "event");
        StringBuilder line = new StringBuilder(128);
        line.append("{\"seq\":").append(event.sequence()).append(",\"time\":");
        appendQuoted(line, formatTimestamp(event.timestamp()));
        line.append(",\"type\":");
        appendQuoted(line, event.type().wireName());
        line.append(",\"payload\":{");
        boolean afterFirst = false;
        for (Map.Entry<String, String> entry : event.payload().entrySet()) {
            if (afterFirst) {
                line.append(',');
            }
            afterFirst = true;
            appendQuoted(line, entry.getKey());
            line.append(':');
            appendQuoted(line, entry.getValue());
        }
        return line.append("}}").toString();
    }

    /**
     * Renders an instant exactly as a line carries it.
     *
     * @param timestamp the instant, already truncated to milliseconds by {@link ProvenanceEvent}
     * @return the fixed-width UTC form, for example {@code 2026-08-31T09:15:00.000Z}
     */
    static String formatTimestamp(Instant timestamp) {
        return TIMESTAMP.format(timestamp);
    }

    /**
     * Reads one line back into an event.
     *
     * @param line one line of an event log, with its terminating newline already removed
     * @return the event the line describes
     * @throws MalformedEventLineException if the line is not one this application wrote, with a
     *     message naming the character offset and what was expected there
     * @throws NullPointerException if {@code line} is {@code null}
     */
    static ProvenanceEvent parse(String line) throws MalformedEventLineException {
        Objects.requireNonNull(line, "line");
        Cursor cursor = new Cursor(line);
        cursor.expect("{\"seq\":");
        long sequence = cursor.readSequence();
        cursor.expect(",\"time\":");
        int timestampOffset = cursor.offset();
        String timestamp = cursor.readString();
        cursor.expect(",\"type\":");
        int typeOffset = cursor.offset();
        String type = cursor.readString();
        cursor.expect(",\"payload\":{");
        Map<String, String> payload = readPayload(cursor);
        cursor.expect("}}");
        cursor.requireEnd();
        return build(sequence, timestamp, timestampOffset, type, typeOffset, payload);
    }

    /**
     * Reads the payload object, from just after its opening brace to just before its closing one.
     *
     * @param cursor the cursor, positioned inside the payload object
     * @return the payload, in the order the line lists it
     * @throws MalformedEventLineException if a member is malformed or a key repeats
     */
    private static Map<String, String> readPayload(Cursor cursor)
            throws MalformedEventLineException {
        Map<String, String> payload = new LinkedHashMap<>();
        // One return, deliberately: an early return of a map that is empty either way is a
        // mutation no test can kill, because replacing an empty map with an empty map changes
        // nothing.  With a single exit every mutation of this method's result is observable.
        boolean more = !cursor.nextIs('}');
        while (more) {
            int keyOffset = cursor.offset();
            String key = cursor.readString();
            cursor.expect(":");
            String value = cursor.readString();
            if (payload.put(key, value) != null) {
                throw new MalformedEventLineException(
                        "a repeated payload key at offset " + keyOffset);
            }
            more = cursor.nextIs(',');
            if (more) {
                cursor.skipOne();
            }
        }
        return payload;
    }

    /**
     * Turns the parsed pieces into an event, converting every rejection into a located complaint.
     *
     * <p>The three {@code catch} blocks are what keeps the reader's promise not to throw: a
     * timestamp that is not a timestamp, a type this build does not know, and a record whose own
     * invariants reject it are all damage found in a file, and damage is reported rather than
     * raised. None of the messages quotes the value it rejected; see the class documentation.
     *
     * @param sequence the parsed sequence number
     * @param timestamp the raw timestamp text
     * @param timestampOffset where that text starts, for the message
     * @param type the raw type text
     * @param typeOffset where that text starts, for the message
     * @param payload the parsed payload
     * @return the event
     * @throws MalformedEventLineException if any piece is not one this application would have
     *     written
     */
    private static ProvenanceEvent build(
            long sequence,
            String timestamp,
            int timestampOffset,
            String type,
            int typeOffset,
            Map<String, String> payload)
            throws MalformedEventLineException {
        Instant instant;
        try {
            instant = Instant.from(TIMESTAMP.parse(timestamp));
        } catch (DateTimeException notATimestamp) {
            throw new MalformedEventLineException(
                    "a timestamp that is not of the form "
                            + TIMESTAMP_EXAMPLE
                            + " at offset "
                            + timestampOffset);
        }
        ProvenanceEventType eventType;
        try {
            eventType = ProvenanceEventType.fromWireName(type);
        } catch (IllegalArgumentException notAKnownType) {
            throw new MalformedEventLineException("an unknown event type at offset " + typeOffset);
        }
        try {
            return new ProvenanceEvent(sequence, instant, eventType, payload);
        } catch (IllegalArgumentException rejectedByTheRecord) {
            throw new MalformedEventLineException(
                    "a record this application would not have written: "
                            + rejectedByTheRecord.getMessage());
        }
    }

    /**
     * Appends a JSON string literal, escaping everything that could end the line or the string.
     *
     * <p>The three that matter most are the quote, the backslash and the newline. A payload value
     * holding a newline -- a captured stderr line, a multi-line warning -- would otherwise split
     * one event across two lines, and the reader would find two malformed fragments where one event
     * used to be. Every character below {@code U+0020} therefore becomes an escape, so no payload
     * can produce a line terminator, and the one-event-per-line invariant is a property of the
     * encoder rather than a rule callers are asked to observe.
     *
     * @param out the line being built
     * @param text the raw text
     */
    private static void appendQuoted(StringBuilder out, String text) {
        out.append('"');
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            switch (character) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> appendPlainOrEscaped(out, character);
            }
        }
        out.append('"');
    }

    /**
     * Appends one character that has no short escape, as itself or as {@code \\u00XX}.
     *
     * @param out the line being built
     * @param character the character
     */
    private static void appendPlainOrEscaped(StringBuilder out, char character) {
        if (character < 0x20) {
            out.append("\\u00")
                    .append(HEX_DIGITS[(character >> 4) & 0xF])
                    .append(HEX_DIGITS[character & 0xF]);
        } else {
            out.append(character);
        }
    }

    /**
     * A position in one line, and the only place an offset is ever computed.
     *
     * <p>Every method either advances the cursor and succeeds, or leaves it where it was and throws
     * with that offset in the message. Nothing here consults the default locale, and nothing
     * accepts a character outside the ASCII forms this format is written in.
     */
    private static final class Cursor {

        private final String line;

        private int index;

        Cursor(String line) {
            this.line = line;
        }

        /**
         * @return the current character offset
         */
        int offset() {
            return index;
        }

        /**
         * Consumes an exact literal.
         *
         * @param token the literal that must appear here
         * @throws MalformedEventLineException if it does not
         */
        void expect(String token) throws MalformedEventLineException {
            if (!line.startsWith(token, index)) {
                throw new MalformedEventLineException("expected " + token + " at offset " + index);
            }
            index += token.length();
        }

        /**
         * Whether the next character is the given one.
         *
         * @param character the character to look for
         * @return {@code true} if the cursor is not at the end and the next character matches
         */
        boolean nextIs(char character) {
            return index < line.length() && line.charAt(index) == character;
        }

        /** Advances one character, which the caller has already looked at. */
        void skipOne() {
            index++;
        }

        /**
         * Reads an unsigned decimal sequence number written in ASCII digits.
         *
         * @return the number
         * @throws MalformedEventLineException if there are no digits, if there is a redundant
         *     leading zero, or if the number does not fit in a {@code long}
         */
        long readSequence() throws MalformedEventLineException {
            int start = index;
            while (index < line.length() && isAsciiDigit(line.charAt(index))) {
                index++;
            }
            if (index == start) {
                throw new MalformedEventLineException(
                        "expected a sequence number at offset " + start);
            }
            if (index - start > 1 && line.charAt(start) == '0') {
                throw new MalformedEventLineException(
                        "a sequence number with a leading zero at offset " + start);
            }
            try {
                return Long.parseLong(line, start, index, 10);
            } catch (NumberFormatException tooLarge) {
                throw new MalformedEventLineException(
                        "a sequence number too large for a long at offset " + start);
            }
        }

        /**
         * Reads one JSON string literal and returns its unescaped text.
         *
         * @return the text between the quotes, with escapes resolved
         * @throws MalformedEventLineException if the string is absent, unterminated, or contains an
         *     unescaped control character or an escape this format does not write
         */
        String readString() throws MalformedEventLineException {
            int start = index;
            if (!nextIs('"')) {
                throw new MalformedEventLineException(
                        "expected a quoted string at offset " + start);
            }
            index++;
            StringBuilder text = new StringBuilder();
            while (true) {
                if (index >= line.length()) {
                    throw new MalformedEventLineException(
                            "an unterminated string from offset " + start);
                }
                char character = line.charAt(index);
                if (character == '"') {
                    index++;
                    return text.toString();
                }
                if (character == '\\') {
                    index++;
                    appendEscaped(text);
                } else if (character < 0x20) {
                    throw new MalformedEventLineException(
                            "an unescaped control character at offset " + index);
                } else {
                    text.append(character);
                    index++;
                }
            }
        }

        /**
         * Consumes the character after a backslash and appends what it stands for.
         *
         * @param text the string being built
         * @throws MalformedEventLineException if the escape is truncated or unknown
         */
        private void appendEscaped(StringBuilder text) throws MalformedEventLineException {
            int escapeStart = index - 1;
            if (index >= line.length()) {
                throw new MalformedEventLineException(
                        "a truncated escape at offset " + escapeStart);
            }
            char character = line.charAt(index);
            index++;
            switch (character) {
                case '"' -> text.append('"');
                case '\\' -> text.append('\\');
                case '/' -> text.append('/');
                case 'b' -> text.append('\b');
                case 'f' -> text.append('\f');
                case 'n' -> text.append('\n');
                case 'r' -> text.append('\r');
                case 't' -> text.append('\t');
                case 'u' -> appendUnicodeEscaped(text, escapeStart);
                default ->
                        throw new MalformedEventLineException(
                                "an unknown escape at offset " + escapeStart);
            }
        }

        /**
         * Consumes the four hexadecimal digits of a {@code \\uXXXX} escape.
         *
         * @param text the string being built
         * @param escapeStart where the backslash was, for the message
         * @throws MalformedEventLineException if fewer than four digits remain or one of them is
         *     not an ASCII hexadecimal digit
         */
        private void appendUnicodeEscaped(StringBuilder text, int escapeStart)
                throws MalformedEventLineException {
            if (index + 4 > line.length()) {
                throw new MalformedEventLineException(
                        "a truncated \\u escape at offset " + escapeStart);
            }
            int value = 0;
            for (int digit = 0; digit < 4; digit++) {
                int nibble = hexValue(line.charAt(index + digit));
                if (nibble < 0) {
                    throw new MalformedEventLineException(
                            "an invalid \\u escape at offset " + escapeStart);
                }
                value = value * 16 + nibble;
            }
            index += 4;
            text.append((char) value);
        }

        /**
         * Rejects anything left over after the closing brace.
         *
         * @throws MalformedEventLineException if the cursor is not at the end of the line
         */
        void requireEnd() throws MalformedEventLineException {
            if (index != line.length()) {
                throw new MalformedEventLineException("trailing characters at offset " + index);
            }
        }

        /**
         * Whether a character is one of {@code 0}-{@code 9}, and not merely a Unicode digit.
         *
         * @param character the character to judge
         * @return {@code true} for an ASCII digit only
         */
        private static boolean isAsciiDigit(char character) {
            return character >= '0' && character <= '9';
        }

        /**
         * The value of an ASCII hexadecimal digit.
         *
         * @param character the character to judge
         * @return 0 to 15, or -1 if it is not an ASCII hexadecimal digit
         */
        private static int hexValue(char character) {
            if (character >= '0' && character <= '9') {
                return character - '0';
            }
            if (character >= 'a' && character <= 'f') {
                return character - 'a' + 10;
            }
            if (character >= 'A' && character <= 'F') {
                return character - 'A' + 10;
            }
            return -1;
        }
    }
}
