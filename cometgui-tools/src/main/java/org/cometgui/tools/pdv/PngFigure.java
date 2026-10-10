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

package org.cometgui.tools.pdv;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * A PNG figure PDV wrote, proved by parsing it rather than by its existence (design decision
 * P11-6).
 *
 * <p>{@link #read} checks the eight-byte signature; walks every chunk, recomputing each CRC;
 * requires {@code IHDR} first, at least one {@code IDAT}, and {@code IEND} last and ending exactly
 * at the end of the file, so a truncated file cannot pass; inflates the image data and reverses
 * every row's filter; and counts the pixels. A figure whose non-white pixels are fewer than {@link
 * #MINIMUM_NON_WHITE_FRACTION} of the image is refused as blank: an empty canvas, or a frame with
 * no spectrum in it, is not a figure. Phase 00's real PDV figures were 2.2 to 2.9 % non-white.
 *
 * <p>What is decoded is what PDV writes and nothing more: 8-bit truecolour, with or without alpha,
 * not interlaced. Any other layout is refused as one this cannot verify -- not passed unread.
 *
 * @param file the figure
 * @param width its width in pixels
 * @param height its height in pixels
 * @param nonWhitePixels how many pixels are not white (a fully transparent pixel counts as white)
 * @param colours how many pixels have each colour, keyed by {@code 0xRRGGBB}
 */
public record PngFigure(
        Path file, int width, int height, long nonWhitePixels, Map<Integer, Long> colours) {

    /** The fraction of pixels that must not be white for a figure to count as drawn. */
    public static final double MINIMUM_NON_WHITE_FRACTION = 0.01;

    /** The largest file this reads, so a hostile file cannot exhaust the heap. */
    public static final long MAXIMUM_BYTES = 64L * 1024 * 1024;

    /** The largest image, in pixels, this decodes. */
    public static final long MAXIMUM_PIXELS = 64L * 1024 * 1024;

    static final byte[] SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'};

    private static final int WHITE = 0xFFFFFF;

    /**
     * Validates the record and copies the colour counts.
     *
     * @throws NullPointerException if a component is {@code null}
     */
    public PngFigure {
        Objects.requireNonNull(file, "file");
        colours = Map.copyOf(colours);
    }

    @Override
    public Map<Integer, Long> colours() {
        return Map.copyOf(colours);
    }

    /**
     * How many pixels have one colour.
     *
     * @param rgb the colour as {@code 0xRRGGBB}
     * @return the count, zero if the colour does not occur
     */
    public long pixelsOf(int rgb) {
        return colours.getOrDefault(rgb, 0L);
    }

    /**
     * Reads and verifies a PNG figure.
     *
     * @param file the file
     * @return the verified figure
     * @throws InvalidFigureException if it is not a valid, decodable, drawn PNG, saying why
     * @throws IOException if it cannot be read
     * @throws NullPointerException if {@code file} is {@code null}
     */
    public static PngFigure read(Path file) throws IOException {
        Objects.requireNonNull(file, "file");
        long size = Files.size(file);
        if (size > MAXIMUM_BYTES) {
            throw new InvalidFigureException(
                    file, "is " + size + " bytes, more than the " + MAXIMUM_BYTES + " read");
        }
        return decode(file, Files.readAllBytes(file));
    }

    static PngFigure decode(Path file, byte[] bytes) throws InvalidFigureException {
        if (bytes.length < SIGNATURE.length
                || !Arrays.equals(Arrays.copyOf(bytes, SIGNATURE.length), SIGNATURE)) {
            throw new InvalidFigureException(file, "does not begin with the PNG signature");
        }
        ByteBuffer in = ByteBuffer.wrap(bytes);
        in.position(SIGNATURE.length);
        Header header = null;
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        boolean ended = false;
        while (!ended) {
            if (in.remaining() < 12) {
                throw new InvalidFigureException(
                        file, "ends at byte " + in.position() + " without an IEND chunk");
            }
            int length = in.getInt();
            int typeStart = in.position();
            byte[] typeBytes = new byte[4];
            in.get(typeBytes);
            String type = new String(typeBytes, StandardCharsets.ISO_8859_1);
            if (length < 0 || length > in.remaining() - 4) {
                throw new InvalidFigureException(
                        file,
                        "has a "
                                + type
                                + " chunk at byte "
                                + (typeStart - 4)
                                + " whose length "
                                + Integer.toUnsignedString(length)
                                + " runs past the end of the file");
            }
            byte[] chunk = new byte[length];
            in.get(chunk);
            CRC32 crc = new CRC32();
            crc.update(bytes, typeStart, 4 + length);
            long stored = Integer.toUnsignedLong(in.getInt());
            if (crc.getValue() != stored) {
                throw new InvalidFigureException(
                        file,
                        "has a "
                                + type
                                + " chunk at byte "
                                + (typeStart - 4)
                                + " whose CRC is wrong (stored "
                                + Long.toHexString(stored)
                                + ", computed "
                                + Long.toHexString(crc.getValue())
                                + ")");
            }
            if (header == null && !"IHDR".equals(type)) {
                throw new InvalidFigureException(
                        file, "has " + type + " as its first chunk instead of IHDR");
            }
            switch (type) {
                case "IHDR" -> {
                    if (header != null) {
                        throw new InvalidFigureException(file, "has a second IHDR chunk");
                    }
                    header = Header.of(file, chunk);
                }
                case "IDAT" -> data.write(chunk, 0, chunk.length);
                case "IEND" -> ended = true;
                default -> {
                    // ancillary chunks carry nothing this verification needs
                }
            }
        }
        if (in.hasRemaining()) {
            throw new InvalidFigureException(
                    file, "has " + in.remaining() + " bytes after its IEND chunk");
        }
        if (data.size() == 0) {
            throw new InvalidFigureException(file, "has no IDAT chunk, so no image data");
        }
        return pixels(file, header, inflate(file, data.toByteArray(), header));
    }

    /** The fields of IHDR this verification needs. */
    record Header(int width, int height, int bytesPerPixel) {

        static Header of(Path file, byte[] chunk) throws InvalidFigureException {
            if (chunk.length != 13) {
                throw new InvalidFigureException(
                        file, "has an IHDR chunk of " + chunk.length + " bytes instead of 13");
            }
            ByteBuffer ihdr = ByteBuffer.wrap(chunk);
            int width = ihdr.getInt();
            int height = ihdr.getInt();
            int bitDepth = Byte.toUnsignedInt(ihdr.get());
            int colourType = Byte.toUnsignedInt(ihdr.get());
            int compression = Byte.toUnsignedInt(ihdr.get());
            int filter = Byte.toUnsignedInt(ihdr.get());
            int interlace = Byte.toUnsignedInt(ihdr.get());
            if (width <= 0 || height <= 0 || (long) width * height > MAXIMUM_PIXELS) {
                throw new InvalidFigureException(
                        file,
                        "declares "
                                + Integer.toUnsignedString(width)
                                + " x "
                                + Integer.toUnsignedString(height)
                                + " pixels, which is empty or larger than this decodes");
            }
            if (bitDepth != 8
                    || (colourType != 2 && colourType != 6)
                    || compression != 0
                    || filter != 0
                    || interlace != 0) {
                throw new InvalidFigureException(
                        file,
                        "has bit depth "
                                + bitDepth
                                + ", colour type "
                                + colourType
                                + ", compression "
                                + compression
                                + ", filter "
                                + filter
                                + " and interlace "
                                + interlace
                                + "; only 8-bit truecolour, with or without alpha, not interlaced,"
                                + " is verified here");
            }
            return new Header(width, height, colourType == 6 ? 4 : 3);
        }

        int stride() {
            return width * bytesPerPixel;
        }
    }

    private static byte[] inflate(Path file, byte[] compressed, Header header)
            throws InvalidFigureException {
        long expected = (long) header.height() * (1 + header.stride());
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(compressed);
            while (!inflater.finished() && !inflater.needsInput() && !inflater.needsDictionary()) {
                raw.write(buffer, 0, inflater.inflate(buffer));
                if (raw.size() > expected) {
                    throw new InvalidFigureException(
                            file,
                            "has more image data than its "
                                    + header.width()
                                    + " x "
                                    + header.height()
                                    + " pixels need");
                }
            }
            if (!inflater.finished() || raw.size() != expected) {
                throw new InvalidFigureException(
                        file,
                        "has "
                                + raw.size()
                                + " bytes of image data where its "
                                + header.width()
                                + " x "
                                + header.height()
                                + " pixels need "
                                + expected
                                + ", or a zlib stream that does not end");
            }
            return raw.toByteArray();
        } catch (DataFormatException corrupt) {
            throw new InvalidFigureException(
                    file,
                    "has image data that is not a valid zlib stream: " + corrupt.getMessage());
        } finally {
            inflater.end();
        }
    }

    private static PngFigure pixels(Path file, Header header, byte[] raw)
            throws InvalidFigureException {
        int bpp = header.bytesPerPixel();
        int stride = header.stride();
        byte[] previous = new byte[stride];
        byte[] row = new byte[stride];
        Map<Integer, Long> colours = new HashMap<>();
        long nonWhite = 0;
        for (int y = 0; y < header.height(); y++) {
            int start = y * (stride + 1);
            int filter = Byte.toUnsignedInt(raw[start]);
            for (int x = 0; x < stride; x++) {
                int a = x >= bpp ? Byte.toUnsignedInt(row[x - bpp]) : 0;
                int b = Byte.toUnsignedInt(previous[x]);
                int c = x >= bpp ? Byte.toUnsignedInt(previous[x - bpp]) : 0;
                int value = Byte.toUnsignedInt(raw[start + 1 + x]);
                row[x] = (byte) (value + predictor(file, y, filter, a, b, c));
            }
            for (int x = 0; x < stride; x += bpp) {
                int rgb =
                        (Byte.toUnsignedInt(row[x]) << 16)
                                | (Byte.toUnsignedInt(row[x + 1]) << 8)
                                | Byte.toUnsignedInt(row[x + 2]);
                if (bpp == 4 && row[x + 3] == 0) {
                    rgb = WHITE;
                }
                colours.merge(rgb, 1L, Long::sum);
                if (rgb != WHITE) {
                    nonWhite++;
                }
            }
            byte[] swap = previous;
            previous = row;
            row = swap;
        }
        long total = (long) header.width() * header.height();
        if (nonWhite < MINIMUM_NON_WHITE_FRACTION * total) {
            throw new InvalidFigureException(
                    file,
                    "is blank: "
                            + nonWhite
                            + " of its "
                            + total
                            + " pixels are not white, fewer than the "
                            + MINIMUM_NON_WHITE_FRACTION * 100
                            + " % a drawn spectrum has");
        }
        return new PngFigure(file, header.width(), header.height(), nonWhite, colours);
    }

    private static int predictor(Path file, int y, int filter, int a, int b, int c)
            throws InvalidFigureException {
        return switch (filter) {
            case 0 -> 0;
            case 1 -> a;
            case 2 -> b;
            case 3 -> (a + b) >>> 1;
            case 4 -> paeth(a, b, c);
            default ->
                    throw new InvalidFigureException(
                            file,
                            "row "
                                    + y
                                    + " has the filter type "
                                    + filter
                                    + ", which PNG does not define");
        };
    }

    private static int paeth(int a, int b, int c) {
        int p = a + b - c;
        int pa = Math.abs(p - a);
        int pb = Math.abs(p - b);
        int pc = Math.abs(p - c);
        if (pa <= pb && pa <= pc) {
            return a;
        }
        return pb <= pc ? b : c;
    }
}
