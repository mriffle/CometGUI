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

package org.cometgui.tools.process;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Drives the pump synchronously, on the test thread, with no process anywhere near it.
 *
 * <p>{@link StreamPump#run()} is an ordinary method. Calling it directly rather than on a thread is
 * what makes the decoding, the end-of-stream handling and both halves of the {@link IOException}
 * rule provable at all: a fault injected into a real pipe is not reproducible, and a fault that
 * only happens on a background thread is not observable.
 *
 * <p>Every expected value is hand-typed.
 */
class StreamPumpTest {

    /** A failure bound is unnecessary here: nothing in this file waits for anything. */
    private final List<String> lines = new ArrayList<>();

    private static CharsetDecoder utf8() {
        return StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);
    }

    private StreamPump pump(InputStream source, BooleanSupplier cancellationRequested) {
        return new StreamPump(
                source, utf8(), "standard output", 100, lines::add, cancellationRequested);
    }

    private static InputStream bytes(int... values) {
        byte[] raw = new byte[values.length];
        for (int index = 0; index < values.length; index++) {
            raw[index] = (byte) values[index];
        }
        return new ByteArrayInputStream(raw);
    }

    private static InputStream ascii(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.US_ASCII));
    }

    @Test
    @DisplayName("every complete line is delivered, in order, without its terminator")
    void deliversEveryLineInOrder() {
        pump(ascii("out 0\nout 1\nout 2\n"), () -> false).run();

        assertEquals(List.of("out 0", "out 1", "out 2"), lines);
    }

    @Test
    @DisplayName("the final unterminated line is delivered at end of stream")
    void deliversTheFinalUnterminatedLine() {
        pump(ascii("first\nlast-without-newline"), () -> false).run();

        assertEquals(List.of("first", "last-without-newline"), lines);
    }

    @Test
    @DisplayName("a stray byte is replaced, not thrown: 41 C3 28 42 0A decodes to A, U+FFFD, (, B")
    void malformedInputIsReplaced() {
        pump(bytes(0x41, 0xC3, 0x28, 0x42, 0x0A), () -> false).run();

        assertEquals(List.of("A�(B"), lines);
        assertEquals(4, lines.get(0).length());
        assertEquals('�', lines.get(0).charAt(1));
    }

    @Test
    @DisplayName("a line longer than the cap is split at exactly the cap")
    void aLongLineIsSplitAtTheCap() {
        pump(ascii("x".repeat(250) + "\n"), () -> false).run();

        assertEquals(3, lines.size());
        assertEquals(100, lines.get(0).length());
        assertEquals(100, lines.get(1).length());
        assertEquals(50, lines.get(2).length());
    }

    @Test
    @DisplayName("an empty stream delivers nothing at all")
    void anEmptyStreamDeliversNothing() {
        pump(ascii(""), () -> false).run();

        assertEquals(List.of(), lines);
    }

    @Test
    @DisplayName("a read failure BEFORE cancellation is reported as a visible line")
    void aReadFailureBeforeCancellationIsReported() {
        pump(new FailingStream("boom", "ready\n"), () -> false).run();

        assertEquals(
                List.of(
                        "ready",
                        "[cometgui] standard output could not be read: java.io.IOException: boom"),
                lines);
    }

    @Test
    @DisplayName("a read failure AFTER cancellation ends the pump quietly")
    void aReadFailureAfterCancellationIsQuiet() {
        pump(new FailingStream("Stream closed", "ready\n"), () -> true).run();

        assertEquals(List.of("ready"), lines);
    }

    @Test
    @DisplayName("output already written before a failure is delivered, including a partial line")
    void outputBeforeAFailureSurvivesIt() {
        pump(new FailingStream("boom", "done\npartial"), () -> false).run();

        assertEquals(
                List.of(
                        "done",
                        "partial",
                        "[cometgui] standard output could not be read: java.io.IOException: boom"),
                lines);
    }

    @Test
    @DisplayName("the fault line names the stream it came from")
    void theFaultLineNamesItsStream() {
        StreamPump stderrPump =
                new StreamPump(
                        new FailingStream("boom", ""),
                        utf8(),
                        "standard error",
                        100,
                        lines::add,
                        () -> false);

        stderrPump.run();

        assertEquals(
                List.of("[cometgui] standard error could not be read: java.io.IOException: boom"),
                lines);
    }

    @Test
    @DisplayName("the fault prefix marks a line the service wrote rather than the tool")
    void theFaultPrefixIsStable() {
        assertEquals("[cometgui] ", StreamPump.FAULT_PREFIX);
    }

    @Test
    @DisplayName("every constructor argument that must not be null is rejected")
    void nullArgumentsAreRejected() {
        CharsetDecoder decoder = utf8();

        assertTrue(
                assertThrows(
                                NullPointerException.class,
                                () ->
                                        new StreamPump(
                                                null, decoder, "s", 10, lines::add, () -> false))
                        .getMessage()
                        .contains("source"));
        assertTrue(
                assertThrows(
                                NullPointerException.class,
                                () ->
                                        new StreamPump(
                                                ascii(""), null, "s", 10, lines::add, () -> false))
                        .getMessage()
                        .contains("decoder"));
        assertTrue(
                assertThrows(
                                NullPointerException.class,
                                () ->
                                        new StreamPump(
                                                ascii(""),
                                                decoder,
                                                null,
                                                10,
                                                lines::add,
                                                () -> false))
                        .getMessage()
                        .contains("streamName"));
        assertTrue(
                assertThrows(
                                NullPointerException.class,
                                () ->
                                        new StreamPump(
                                                ascii(""), decoder, "s", 10, null, () -> false))
                        .getMessage()
                        .contains("lineSink"));
        assertTrue(
                assertThrows(
                                NullPointerException.class,
                                () -> new StreamPump(ascii(""), decoder, "s", 10, lines::add, null))
                        .getMessage()
                        .contains("cancellationRequested"));
    }

    /** Hands out a fixed prologue and then fails, the way a pipe dies mid-run. */
    private static final class FailingStream extends InputStream {

        private final byte[] prologue;
        private final String message;
        private int position;

        private FailingStream(String message, String prologue) {
            this.message = message;
            this.prologue = prologue.getBytes(StandardCharsets.US_ASCII);
        }

        @Override
        public int read() throws IOException {
            if (position < prologue.length) {
                return prologue[position++] & 0xFF;
            }
            throw new IOException(message);
        }

        @Override
        public int read(byte[] target, int offset, int length) throws IOException {
            if (position >= prologue.length) {
                throw new IOException(message);
            }
            int copied = Math.min(length, prologue.length - position);
            System.arraycopy(prologue, position, target, offset, copied);
            position += copied;
            return copied;
        }
    }
}
