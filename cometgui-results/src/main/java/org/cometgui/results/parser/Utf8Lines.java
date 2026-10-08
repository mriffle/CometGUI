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

package org.cometgui.results.parser;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Splits a byte stream into UTF-8 lines exactly as {@link java.io.BufferedReader#readLine} does --
 * a line ends at {@code \n}, {@code \r} or {@code \r\n}, a last line without a terminator is a
 * line, and the end of the stream is {@code null} -- while recording where each line starts in the
 * stream and how many bytes it holds, so that a row can be read back later by its offset ({@code
 * R-RES-03}).
 *
 * <p>Lines are split on bytes before they are decoded, which is sound for UTF-8: the bytes of
 * {@code \n} and {@code \r} never occur inside a multi-byte sequence. Each line is then decoded
 * strictly, so a byte sequence that is not UTF-8 is refused with a {@link
 * java.nio.charset.MalformedInputException}, never replaced. Not thread-safe.
 */
final class Utf8Lines implements Closeable {

    private static final int BUFFER_BYTES = 1 << 16;

    private final InputStream in;
    private final byte[] buffer = new byte[BUFFER_BYTES];
    private final CharsetDecoder decoder = strictDecoder();
    private int position;
    private int limit;
    private long bufferStart;
    private byte[] line = new byte[256];
    private int lineLength;
    private long lineStart = -1;
    private boolean closed;

    Utf8Lines(InputStream in) {
        this.in = in;
    }

    /**
     * Reads the next line.
     *
     * @return the line without its terminator, or {@code null} at the end of the stream
     * @throws IOException if the stream cannot be read, has been closed ({@code Stream closed}, as
     *     {@code BufferedReader} says), or the line is not UTF-8
     */
    String next() throws IOException {
        if (closed) {
            throw new IOException("Stream closed");
        }
        long start = bufferStart + position;
        lineLength = 0;
        boolean ascii = true;
        while (true) {
            if (position == limit && !fill()) {
                if (lineLength == 0) {
                    // Nothing since the last terminator: the stream has ended.
                    return null;
                }
                break;
            }
            int scan = position;
            while (scan < limit) {
                byte b = buffer[scan];
                if (b == '\n' || b == '\r') {
                    break;
                }
                if (b < 0) {
                    ascii = false;
                }
                scan++;
            }
            append(position, scan - position);
            position = scan;
            if (scan < limit) {
                byte terminator = buffer[scan];
                position++;
                if (terminator == '\r'
                        && (position < limit || fill())
                        && buffer[position] == '\n') {
                    position++;
                }
                break;
            }
        }
        lineStart = start;
        return ascii
                ? new String(line, 0, lineLength, StandardCharsets.ISO_8859_1)
                : decode(decoder, line, 0, lineLength);
    }

    /**
     * Where the line {@link #next} last returned starts.
     *
     * @return its first byte's offset from the start of the stream
     * @throws IllegalStateException if no line has been read
     */
    long lastLineStart() {
        if (lineStart < 0) {
            throw new IllegalStateException("no line has been read");
        }
        return lineStart;
    }

    /**
     * How many bytes the line {@link #next} last returned holds.
     *
     * @return its length in bytes, without its terminator
     * @throws IllegalStateException if no line has been read
     */
    int lastLineLength() {
        lastLineStart();
        return lineLength;
    }

    @Override
    public void close() throws IOException {
        closed = true;
        in.close();
    }

    /**
     * Decodes bytes as UTF-8, strictly.
     *
     * @param bytes the bytes
     * @param offset where they start
     * @param length how many
     * @return the text
     * @throws CharacterCodingException if they are not UTF-8
     */
    static String decode(byte[] bytes, int offset, int length) throws CharacterCodingException {
        return decode(strictDecoder(), bytes, offset, length);
    }

    private static String decode(CharsetDecoder decoder, byte[] bytes, int offset, int length)
            throws CharacterCodingException {
        decoder.reset();
        return decoder.decode(ByteBuffer.wrap(bytes, offset, length)).toString();
    }

    private static CharsetDecoder strictDecoder() {
        return StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
    }

    /** Refills the buffer; {@code false} at the end of the stream. */
    private boolean fill() throws IOException {
        bufferStart += limit;
        position = 0;
        limit = 0;
        int read;
        do {
            read = in.read(buffer, 0, buffer.length);
        } while (read == 0);
        if (read < 0) {
            return false;
        }
        limit = read;
        return true;
    }

    private void append(int from, int count) {
        if (lineLength + count > line.length) {
            line = Arrays.copyOf(line, Math.max(line.length * 2, lineLength + count));
        }
        System.arraycopy(buffer, from, line, lineLength, count);
        lineLength += count;
    }
}
