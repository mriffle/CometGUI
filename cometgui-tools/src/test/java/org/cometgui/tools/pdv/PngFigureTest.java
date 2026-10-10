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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import javax.imageio.ImageIO;
import org.cometgui.tools.testing.Nulls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The figure verifier, on PNGs built here byte by byte -- every filter type, every way of being
 * broken -- and on one written by the JDK's own encoder.
 */
class PngFigureTest {

    private static final int WHITE = 0xFFFFFF;

    @TempDir private Path directory;

    // ------------------------------------------------------------------ a small PNG encoder

    /** Builds a PNG from pixels, one filter type per row, so every filter can be exercised. */
    static final class Png {
        private int width;
        private int height;
        private int bitDepth = 8;
        private int colourType = 2;
        private int compression;
        private int filter;
        private int interlace;
        private final int[][] pixels;
        private final int[] filters;
        private boolean alpha;

        Png(int[][] pixels, int[] filters) {
            this.pixels = pixels;
            this.filters = filters;
            this.height = pixels.length;
            this.width = pixels[0].length;
        }

        static Png of(int[][] pixels, int... filters) {
            return new Png(pixels, filters);
        }

        Png withAlpha() {
            alpha = true;
            colourType = 6;
            return this;
        }

        byte[] ihdr() {
            return ByteBuffer.allocate(13)
                    .putInt(width)
                    .putInt(height)
                    .put((byte) bitDepth)
                    .put((byte) colourType)
                    .put((byte) compression)
                    .put((byte) filter)
                    .put((byte) interlace)
                    .array();
        }

        byte[] raw() {
            int bpp = alpha ? 4 : 3;
            int stride = width * bpp;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] previous = new byte[stride];
            for (int y = 0; y < height; y++) {
                byte[] row = new byte[stride];
                for (int x = 0; x < width; x++) {
                    int p = pixels[y][x];
                    row[x * bpp] = (byte) (p >>> 16);
                    row[x * bpp + 1] = (byte) (p >>> 8);
                    row[x * bpp + 2] = (byte) p;
                    if (alpha) {
                        row[x * bpp + 3] = (byte) (p >>> 24);
                    }
                }
                int type = filters[y % filters.length];
                out.write(type);
                for (int x = 0; x < stride; x++) {
                    int a = x >= bpp ? row[x - bpp] & 0xFF : 0;
                    int b = previous[x] & 0xFF;
                    int c = x >= bpp ? previous[x - bpp] & 0xFF : 0;
                    int predicted =
                            switch (type) {
                                case 0 -> 0;
                                case 1 -> a;
                                case 2 -> b;
                                case 3 -> (a + b) / 2;
                                case 4 -> paeth(a, b, c);
                                default -> 0;
                            };
                    out.write((row[x] - predicted) & 0xFF);
                }
                previous = row;
            }
            return out.toByteArray();
        }

        private static int paeth(int a, int b, int c) {
            int p = a + b - c;
            int pa = Math.abs(p - a);
            int pb = Math.abs(p - b);
            int pc = Math.abs(p - c);
            return pa <= pb && pa <= pc ? a : pb <= pc ? b : c;
        }

        byte[] bytes() {
            return file(chunk("IHDR", ihdr()), chunk("IDAT", deflate(raw())), chunk("IEND"));
        }
    }

    static byte[] deflate(byte[] raw) {
        Deflater deflater = new Deflater();
        deflater.setInput(raw);
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        while (!deflater.finished()) {
            out.write(buffer, 0, deflater.deflate(buffer));
        }
        deflater.end();
        return out.toByteArray();
    }

    static byte[] chunk(String type, byte[] data) {
        byte[] typeBytes = type.getBytes(StandardCharsets.ISO_8859_1);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        return ByteBuffer.allocate(12 + data.length)
                .putInt(data.length)
                .put(typeBytes)
                .put(data)
                .putInt((int) crc.getValue())
                .array();
    }

    static byte[] chunk(String type) {
        return chunk(type, new byte[0]);
    }

    static byte[] file(byte[]... chunks) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(PngFigure.SIGNATURE);
        for (byte[] chunk : chunks) {
            out.writeBytes(chunk);
        }
        return out.toByteArray();
    }

    /** A 10 x 10 image whose first {@code coloured} pixels are coloured. */
    static int[][] image(int coloured) {
        int[][] pixels = new int[10][10];
        for (int i = 0; i < 100; i++) {
            pixels[i / 10][i % 10] = i < coloured ? 0x10 * (i % 7) << 8 | 0x330000 : WHITE;
        }
        return pixels;
    }

    private Path write(byte[] bytes) throws IOException {
        return Files.write(directory.resolve("f.png"), bytes);
    }

    private String refusal(byte[] bytes) throws IOException {
        Path file = write(bytes);
        InvalidFigureException thrown =
                assertThrows(InvalidFigureException.class, () -> PngFigure.read(file));
        assertEquals(file, thrown.file());
        return thrown.getMessage().substring(file.toString().length() + 1);
    }

    // ------------------------------------------------------------------ what passes

    @Test
    @DisplayName("every filter type is reversed exactly: the pixels counted are the pixels drawn")
    void everyFilter() throws IOException {
        int[][] pixels = new int[10][12];
        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 12; x++) {
                pixels[y][x] =
                        (y * 37 + x * 91) % 3 == 0
                                ? WHITE
                                : (y * 0x112233 + x * 0x0F0E0D) & 0xFFFFFF;
            }
        }
        long expectedNonWhite =
                Arrays.stream(pixels).flatMapToInt(Arrays::stream).filter(p -> p != WHITE).count();

        for (int filter = 0; filter <= 4; filter++) {
            PngFigure figure = PngFigure.read(write(Png.of(pixels, filter).bytes()));
            assertAll(
                    () -> assertEquals(12, figure.width()),
                    () -> assertEquals(10, figure.height()),
                    () -> assertEquals(expectedNonWhite, figure.nonWhitePixels()),
                    () -> assertEquals(120 - expectedNonWhite, figure.pixelsOf(WHITE)));
        }
        PngFigure mixed = PngFigure.read(write(Png.of(pixels, 4, 3, 2, 1, 0).bytes()));
        assertEquals(expectedNonWhite, mixed.nonWhitePixels());
        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 12; x++) {
                int want = pixels[y][x];
                long count =
                        Arrays.stream(pixels)
                                .flatMapToInt(Arrays::stream)
                                .filter(p -> p == want)
                                .count();
                assertEquals(count, mixed.pixelsOf(want), Integer.toHexString(want));
            }
        }
    }

    @Test
    @DisplayName(
            "with alpha, a fully transparent pixel counts as white and an opaque one by colour")
    void alpha() throws IOException {
        int[][] pixels = image(50);
        for (int[] row : pixels) {
            for (int x = 0; x < row.length; x++) {
                row[x] |= 0xFF000000;
            }
        }
        pixels[0][0] = 0x00123456;
        pixels[0][1] = 0xFF123456;
        PngFigure figure = PngFigure.read(write(Png.of(pixels, 1, 4).withAlpha().bytes()));

        assertAll(
                () -> assertEquals(49, figure.nonWhitePixels()),
                () -> assertEquals(51, figure.pixelsOf(WHITE)),
                () -> assertEquals(1, figure.pixelsOf(0x123456)));
    }

    @Test
    @DisplayName("exactly 1 % non-white passes; one pixel fewer is blank")
    void floor() throws IOException {
        int[][] pixels = new int[100][100];
        for (int[] row : pixels) {
            Arrays.fill(row, WHITE);
        }
        for (int x = 0; x < 100; x++) {
            pixels[50][x] = 0x333333;
        }
        assertEquals(100, PngFigure.read(write(Png.of(pixels, 0).bytes())).nonWhitePixels());
        pixels[50][0] = WHITE;
        assertEquals(
                "is blank: 99 of its 10000 pixels are not white, fewer than the 1.0 % a drawn"
                        + " spectrum has",
                refusal(Png.of(pixels, 0).bytes()));
    }

    @Test
    @DisplayName("a PNG the JDK's own encoder wrote, with ancillary chunks, is read")
    void jdkEncoded() throws IOException {
        BufferedImage image = new BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 40, 20);
        g.setColor(new Color(0xFF6600));
        g.fillRect(0, 0, 10, 4);
        g.setColor(new Color(0x009900));
        g.fillRect(20, 10, 5, 5);
        g.dispose();
        Path file = directory.resolve("jdk.png");
        assertTrue(ImageIO.write(image, "png", file.toFile()));

        PngFigure figure = PngFigure.read(file);

        assertAll(
                () -> assertEquals(file, figure.file()),
                () -> assertEquals(40, figure.width()),
                () -> assertEquals(20, figure.height()),
                () -> assertEquals(65, figure.nonWhitePixels()),
                () -> assertEquals(40, figure.pixelsOf(0xFF6600)),
                () -> assertEquals(25, figure.pixelsOf(0x009900)),
                () -> assertEquals(0, figure.pixelsOf(0x123456)),
                () ->
                        assertEquals(
                                Map.of(0xFF6600, 40L, 0x009900, 25L, WHITE, 735L),
                                figure.colours()));
    }

    @Test
    @DisplayName("several IDAT chunks are one stream, and an ancillary chunk is skipped")
    void splitData() throws IOException {
        Png png = Png.of(image(60), 0);
        byte[] data = deflate(png.raw());
        int half = data.length / 2;
        byte[] bytes =
                file(
                        chunk("IHDR", png.ihdr()),
                        chunk("tEXt", "Comment\0hi".getBytes(StandardCharsets.ISO_8859_1)),
                        chunk("IDAT", Arrays.copyOfRange(data, 0, half)),
                        chunk("IDAT", Arrays.copyOfRange(data, half, data.length)),
                        chunk("IEND"));

        assertEquals(60, PngFigure.read(write(bytes)).nonWhitePixels());
    }

    @Test
    @DisplayName("Paeth's tie-breaking is PNG's: the left pixel only below both, then above")
    void paethTies() throws IOException {
        // (a, b, c) = (0, 15, 10) and (0, 15, 5): the two configurations where a different
        // tie-break predicts a different value, found by enumerating all three bytes.
        for (int c : new int[] {10, 5}) {
            int[][] pixels = {
                {grey(c), grey(15)},
                {grey(0), grey(77)}
            };
            PngFigure figure = PngFigure.read(write(Png.of(pixels, 0, 4).bytes()));
            assertAll(
                    () -> assertEquals(1, figure.pixelsOf(grey(77)), "c = " + c),
                    () -> assertEquals(1, figure.pixelsOf(grey(0))));
        }
    }

    private static int grey(int level) {
        return level << 16 | level << 8 | level;
    }

    @Test
    @DisplayName("a stream whose end-of-stream block follows the last pixel byte is complete")
    void flushedStream() throws IOException {
        Png png = Png.of(image(60), 0);
        Deflater deflater = new Deflater();
        deflater.setInput(png.raw());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int n;
        while ((n = deflater.deflate(buffer, 0, buffer.length, Deflater.SYNC_FLUSH)) > 0) {
            out.write(buffer, 0, n);
        }
        deflater.finish();
        while (!deflater.finished()) {
            out.write(buffer, 0, deflater.deflate(buffer));
        }
        deflater.end();
        byte[] bytes =
                file(chunk("IHDR", png.ihdr()), chunk("IDAT", out.toByteArray()), chunk("IEND"));

        assertEquals(60, PngFigure.read(write(bytes)).nonWhitePixels());
    }

    @Test
    @DisplayName(
            "a file that is the signature alone has no IEND; a file of exactly the limit is read")
    void boundaries() throws IOException {
        Path exact = directory.resolve("exact.png");
        try (RandomAccessFile sparse = new RandomAccessFile(exact.toFile(), "rw")) {
            sparse.setLength(PngFigure.MAXIMUM_BYTES);
        }
        assertAll(
                () ->
                        assertEquals(
                                "ends at byte 8 without an IEND chunk",
                                refusal(PngFigure.SIGNATURE.clone())),
                () ->
                        assertEquals(
                                exact + " does not begin with the PNG signature",
                                assertThrows(
                                                InvalidFigureException.class,
                                                () -> PngFigure.read(exact))
                                        .getMessage()));
        Files.delete(exact);
    }

    // ------------------------------------------------------------------ what is refused

    @Test
    @DisplayName("a file without the signature, or shorter than it, is refused")
    void signature() throws IOException {
        byte[] good = Png.of(image(50), 0).bytes();
        byte[] bad = good.clone();
        bad[1] = 'Q';
        assertAll(
                () -> assertEquals("does not begin with the PNG signature", refusal(bad)),
                () ->
                        assertEquals(
                                "does not begin with the PNG signature",
                                refusal(new byte[] {(byte) 0x89, 'P'})),
                () -> assertEquals("does not begin with the PNG signature", refusal(new byte[0])));
    }

    @Test
    @DisplayName("a damaged CRC is refused, naming the chunk, its offset and both values")
    void crc() throws IOException {
        byte[] bytes = Png.of(image(50), 0).bytes();
        bytes[8 + 8 + 3] ^= 1;
        CRC32 crc = new CRC32();
        crc.update(bytes, 12, 17);
        String computed = Long.toHexString(crc.getValue());
        String stored =
                Long.toHexString(Integer.toUnsignedLong(ByteBuffer.wrap(bytes, 29, 4).getInt()));

        assertEquals(
                "has a IHDR chunk at byte 8 whose CRC is wrong (stored "
                        + stored
                        + ", computed "
                        + computed
                        + ")",
                refusal(bytes));
    }

    @Test
    @DisplayName("the chunk order is enforced: IHDR first and once, IEND last, data present")
    void order() throws IOException {
        Png png = Png.of(image(50), 0);
        byte[] ihdr = chunk("IHDR", png.ihdr());
        byte[] idat = chunk("IDAT", deflate(png.raw()));
        byte[] iend = chunk("IEND");
        byte[] good = file(ihdr, idat, iend);
        assertAll(
                () ->
                        assertEquals(
                                "has IDAT as its first chunk instead of IHDR",
                                refusal(file(idat, ihdr, iend))),
                () ->
                        assertEquals(
                                "has a second IHDR chunk", refusal(file(ihdr, ihdr, idat, iend))),
                () ->
                        assertEquals(
                                "has no IDAT chunk, so no image data", refusal(file(ihdr, iend))),
                () ->
                        assertEquals(
                                "ends at byte " + (good.length - 12) + " without an IEND chunk",
                                refusal(Arrays.copyOf(good, good.length - 12))),
                () ->
                        assertEquals(
                                "ends at byte " + (good.length - 12) + " without an IEND chunk",
                                refusal(Arrays.copyOf(good, good.length - 1))),
                () ->
                        assertEquals(
                                "has 3 bytes after its IEND chunk",
                                refusal(Arrays.copyOf(good, good.length + 3))));
    }

    @Test
    @DisplayName("a chunk whose length runs past the end of the file is refused")
    void length() throws IOException {
        Png png = Png.of(image(50), 0);
        byte[] idat = chunk("IDAT", deflate(png.raw()));
        int declared = idat.length + 1;
        ByteBuffer.wrap(idat).putInt(0, declared);
        byte[] overlong = file(chunk("IHDR", png.ihdr()), idat, chunk("IEND"));
        byte[] negative = overlong.clone();
        ByteBuffer.wrap(negative).putInt(33, -1);
        assertAll(
                () ->
                        assertEquals(
                                "has a IDAT chunk at byte 33 whose length "
                                        + declared
                                        + " runs past the end of the file",
                                refusal(overlong)),
                () ->
                        assertEquals(
                                "has a IDAT chunk at byte 33 whose length "
                                        + "4294967295 runs past the end of the file",
                                refusal(negative)));
    }

    @Test
    @DisplayName("an IHDR of the wrong size, empty or oversized dimensions are refused")
    void header() throws IOException {
        Png png = Png.of(image(50), 0);
        byte[] idat = chunk("IDAT", deflate(png.raw()));
        Png zero = Png.of(image(50), 0);
        zero.width = 0;
        Png flat = Png.of(image(50), 0);
        flat.height = 0;
        Png negative = Png.of(image(50), 0);
        negative.height = -1;
        Png huge = Png.of(image(50), 0);
        huge.width = 65536;
        huge.height = 1025;
        Png largestAllowed = Png.of(image(50), 0);
        largestAllowed.width = 65536;
        largestAllowed.height = 1024;
        assertAll(
                () ->
                        assertEquals(
                                "has an IHDR chunk of 12 bytes instead of 13",
                                refusal(
                                        file(
                                                chunk("IHDR", Arrays.copyOf(png.ihdr(), 12)),
                                                idat,
                                                chunk("IEND")))),
                () ->
                        assertEquals(
                                "declares 0 x 10 pixels, which is empty or "
                                        + "larger than this decodes",
                                refusal(file(chunk("IHDR", zero.ihdr()), idat, chunk("IEND")))),
                () ->
                        assertEquals(
                                "declares 10 x 0 pixels, which is empty or larger than this"
                                        + " decodes",
                                refusal(file(chunk("IHDR", flat.ihdr()), idat, chunk("IEND")))),
                () ->
                        assertEquals(
                                "declares 10 x 4294967295 pixels, which is "
                                        + "empty or larger than this decodes",
                                refusal(file(chunk("IHDR", negative.ihdr()), idat, chunk("IEND")))),
                () ->
                        assertEquals(
                                "declares 65536 x 1025 pixels, which is empty "
                                        + "or larger than this decodes",
                                refusal(file(chunk("IHDR", huge.ihdr()), idat, chunk("IEND")))),
                () ->
                        assertTrue(
                                refusal(
                                                file(
                                                        chunk("IHDR", largestAllowed.ihdr()),
                                                        idat,
                                                        chunk("IEND")))
                                        .startsWith("has "),
                                "64 Mi pixels is decoded, not refused by size"));
    }

    @Test
    @DisplayName("a layout other than 8-bit truecolour, not interlaced, is refused as unverifiable")
    void layouts() throws IOException {
        String tail =
                "; only 8-bit truecolour, with or without alpha, not interlaced, is verified here";
        int[][] values = {
            {16, 2, 0, 0, 0},
            {8, 0, 0, 0, 0},
            {8, 3, 0, 0, 0},
            {8, 4, 0, 0, 0},
            {8, 2, 1, 0, 0},
            {8, 2, 0, 1, 0},
            {8, 2, 0, 0, 1}
        };
        for (int[] v : values) {
            Png png = Png.of(image(50), 0);
            png.bitDepth = v[0];
            png.colourType = v[1];
            png.compression = v[2];
            png.filter = v[3];
            png.interlace = v[4];
            assertEquals(
                    "has bit depth "
                            + v[0]
                            + ", colour type "
                            + v[1]
                            + ", compression "
                            + v[2]
                            + ", filter "
                            + v[3]
                            + " and interlace "
                            + v[4]
                            + tail,
                    refusal(png.bytes()),
                    Arrays.toString(v));
        }
    }

    @Test
    @DisplayName("image data that is short, long, corrupt or wrongly filtered is refused")
    void data() throws IOException {
        Png png = Png.of(image(50), 0);
        byte[] raw = png.raw();
        byte[] ihdr = chunk("IHDR", png.ihdr());
        byte[] iend = chunk("IEND");
        byte[] badFilter = raw.clone();
        badFilter[31 * 3] = 5;
        byte[] deflated = deflate(raw);
        assertAll(
                () ->
                        assertEquals(
                                "has 299 bytes of image data where its 10 x "
                                        + "10 pixels need 310, or a zlib stream that "
                                        + "does not end",
                                refusal(
                                        file(
                                                ihdr,
                                                chunk("IDAT", deflate(Arrays.copyOf(raw, 299))),
                                                iend))),
                () ->
                        assertEquals(
                                "has more image data than its 10 x 10 pixels need",
                                refusal(
                                        file(
                                                ihdr,
                                                chunk("IDAT", deflate(Arrays.copyOf(raw, 311))),
                                                iend))),
                () ->
                        assertTrue(
                                refusal(
                                                file(
                                                        ihdr,
                                                        chunk(
                                                                "IDAT",
                                                                Arrays.copyOf(
                                                                        deflated,
                                                                        deflated.length - 6)),
                                                        iend))
                                        .startsWith("has "),
                                "a truncated stream is refused"),
                () ->
                        assertTrue(
                                refusal(
                                                file(
                                                        ihdr,
                                                        chunk(
                                                                "IDAT",
                                                                new byte[] {
                                                                    0x78,
                                                                    (byte) 0x9c,
                                                                    (byte) 0xff,
                                                                    (byte) 0xff,
                                                                    1,
                                                                    2
                                                                }),
                                                        iend))
                                        .startsWith(
                                                "has image data that is not a valid zlib"
                                                        + " stream: ")),
                () ->
                        assertEquals(
                                "row 3 has the filter type 5, which PNG does not define",
                                refusal(file(ihdr, chunk("IDAT", deflate(badFilter)), iend))));
    }

    @Test
    @DisplayName("a file larger than the limit is refused before it is read")
    void tooLarge() throws IOException {
        Path file = directory.resolve("big.png");
        try (RandomAccessFile sparse = new RandomAccessFile(file.toFile(), "rw")) {
            sparse.setLength(PngFigure.MAXIMUM_BYTES + 1);
        }
        assertEquals(
                file + " is 67108865 bytes, more than the 67108864 read",
                assertThrows(InvalidFigureException.class, () -> PngFigure.read(file))
                        .getMessage());
        Files.delete(file);
    }

    @Test
    @DisplayName("the record copies its colours and requires its file")
    void record() {
        java.util.HashMap<Integer, Long> colours = new java.util.HashMap<>(Map.of(1, 2L));
        PngFigure figure = new PngFigure(directory, 1, 1, 1, colours);
        colours.put(3, 4L);
        assertAll(
                () -> assertEquals(Map.of(1, 2L), figure.colours()),
                () ->
                        assertEquals(
                                "file",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new PngFigure(
                                                                Nulls.of(Path.class),
                                                                1,
                                                                1,
                                                                1,
                                                                Map.of()))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "file",
                                assertThrows(
                                                NullPointerException.class,
                                                () -> PngFigure.read(Nulls.of(Path.class)))
                                        .getMessage()));
    }
}
