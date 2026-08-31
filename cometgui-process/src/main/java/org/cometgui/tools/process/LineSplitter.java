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

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Splits a stream of character chunks into complete lines, one at a time.
 *
 * <p>This exists instead of {@link java.io.BufferedReader#readLine()} for three reasons, each of
 * which is a requirement rather than a preference.
 *
 * <ol>
 *   <li><strong>A lone carriage return ends a line.</strong> Comet and Percolator draw progress
 *       bars by rewriting one line with {@code \r}. {@code readLine} does treat a lone {@code \r}
 *       as a terminator, but it does so by reading ahead one character, which blocks until the tool
 *       writes again -- so a progress bar would only appear after the next update. Here the line is
 *       emitted the moment the {@code \r} is seen.
 *   <li><strong>A line has a maximum length.</strong> {@code readLine} accumulates without limit,
 *       so a tool that writes 500 MB with no newline exhausts the heap through a single buffer,
 *       which is precisely what {@code R-PROC-03} exists to prevent. Here the accumulated
 *       characters are emitted as a line at {@link #DEFAULT_MAXIMUM_LINE_LENGTH} and accumulation
 *       continues, so the split is visible in the log rather than fatal.
 *   <li><strong>It is pure, so it is testable.</strong> Line splitting is the only interesting
 *       logic in a stream pump, and logic inside a thread cannot be tested exhaustively. This class
 *       is fed chunks by hand, including chunks that split a {@code \r\n} pair down the middle.
 * </ol>
 *
 * <p>Every line is delivered <em>without</em> its terminator. An empty line between two terminators
 * is a line and is delivered. The final unterminated segment is a line and is delivered by {@link
 * #endOfStream()}.
 *
 * <p>Not thread safe: one instance belongs to one stream pump thread.
 */
final class LineSplitter {

    /**
     * The default cap on a single line, in characters.
     *
     * <p>64 Ki characters is far longer than any line a scientific tool writes on purpose and far
     * shorter than a heap. A tool that exceeds it is malfunctioning, and the log shows where.
     */
    static final int DEFAULT_MAXIMUM_LINE_LENGTH = 65_536;

    private final int maximumLineLength;
    private final Consumer<String> sink;
    private final StringBuilder pending = new StringBuilder();

    /**
     * Set when the previous character was a carriage return, so that a {@code \n} arriving next --
     * possibly at the start of the next chunk -- is recognised as the second half of one {@code
     * \r\n} terminator rather than as a second line break.
     */
    private boolean afterCarriageReturn;

    private boolean ended;

    /**
     * A splitter capping lines at {@link #DEFAULT_MAXIMUM_LINE_LENGTH}.
     *
     * @param sink receives each complete line, without its terminator
     */
    LineSplitter(Consumer<String> sink) {
        this(DEFAULT_MAXIMUM_LINE_LENGTH, sink);
    }

    /**
     * A splitter with an explicit cap.
     *
     * @param maximumLineLength the greatest number of characters a delivered line may contain; at
     *     least one
     * @param sink receives each complete line, without its terminator
     * @throws IllegalArgumentException if {@code maximumLineLength} is less than one
     * @throws NullPointerException if {@code sink} is null
     */
    LineSplitter(int maximumLineLength, Consumer<String> sink) {
        if (maximumLineLength < 1) {
            throw new IllegalArgumentException(
                    "maximumLineLength must be at least 1, but was: " + maximumLineLength);
        }
        this.maximumLineLength = maximumLineLength;
        this.sink = Objects.requireNonNull(sink, "sink");
    }

    /**
     * Feeds one chunk of decoded characters, delivering every line it completes.
     *
     * @param buffer the characters
     * @param offset the first index to read
     * @param length how many characters to read
     * @throws NullPointerException if {@code buffer} is null
     * @throws IndexOutOfBoundsException if the range is not within the buffer
     */
    void accept(char[] buffer, int offset, int length) {
        Objects.requireNonNull(buffer, "buffer");
        Objects.checkFromIndexSize(offset, length, buffer.length);
        int end = offset + length;
        for (int index = offset; index < end; index++) {
            char character = buffer[index];
            if (afterCarriageReturn) {
                afterCarriageReturn = false;
                if (character == '\n') {
                    /* The second half of a CRLF, possibly the first character of a new chunk.
                     * The line was already emitted when the carriage return was seen. */
                    continue;
                }
            }
            if (character == '\n') {
                emit();
            } else if (character == '\r') {
                emit();
                afterCarriageReturn = true;
            } else {
                if (pending.length() >= maximumLineLength) {
                    emit();
                }
                pending.append(character);
            }
        }
    }

    /**
     * Declares the stream finished, delivering the final unterminated segment if there is one.
     *
     * <p>Idempotent: a pump that ends both normally and through a failure calls this twice.
     */
    void endOfStream() {
        if (ended) {
            return;
        }
        ended = true;
        if (pending.length() > 0) {
            emit();
        }
    }

    private void emit() {
        String line = pending.toString();
        pending.setLength(0);
        sink.accept(line);
    }
}
