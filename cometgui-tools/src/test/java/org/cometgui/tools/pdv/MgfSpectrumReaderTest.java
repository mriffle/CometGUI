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
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import org.cometgui.tools.testing.Nulls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The MGF reader, on constructed files. */
class MgfSpectrumReaderTest {

    @TempDir private Path directory;

    private Path file(String text) throws IOException {
        return Files.writeString(directory.resolve("in.mgf"), text, StandardCharsets.UTF_8);
    }

    private static List<SourceSpectrum> read(Path file) throws IOException {
        List<SourceSpectrum> spectra = new ArrayList<>();
        MgfSpectrumReader.read(file, spectra::add);
        return spectra;
    }

    private String failure(String text) throws IOException {
        Path file = file(text);
        SpectrumFileException thrown = assertThrows(SpectrumFileException.class, () -> read(file));
        assertEquals(file, thrown.file());
        return thrown.getMessage().substring(file.toString().length() + 2);
    }

    @Test
    @DisplayName("each entry's title, ordinal, precursor text, charge, time and peaks are reported")
    void readsEntries() throws IOException {
        Path file =
                file(
                        "# a comment\n"
                                + "COM=made by hand\n"
                                + "MASS=Monoisotopic\n"
                                + "\n"
                                + "BEGIN IONS\n"
                                + "TITLE=controllerType=0 controllerNumber=1 scan=11188\n"
                                + "PEPMASS=615.788391113281 3092787.4\n"
                                + "CHARGE=2+\n"
                                + "RTINSECONDS=4740.95599999998\n"
                                + "SCANS=11188\n"
                                + "; inside\n"
                                + "103.490119934082 12446.078125\n"
                                + "  1144.85205078125\t0.5  \n"
                                + "END IONS\n"
                                + "BEGIN IONS\r\n"
                                + "title=second\r\n"
                                + "pepmass=1.5e2\r\n"
                                + "charge=3-\r\n"
                                + "END IONS\r\n"
                                + "BEGIN IONS\n"
                                + "TITLE=third\n"
                                + "CHARGE=4\n"
                                + "1 2\n"
                                + "END IONS\n");

        List<SourceSpectrum> spectra = read(file);

        assertEquals(3, spectra.size());
        SourceSpectrum first = spectra.get(0);
        SourceSpectrum second = spectra.get(1);
        SourceSpectrum third = spectra.get(2);
        assertAll(
                () ->
                        assertEquals(
                                "controllerType=0 controllerNumber=1 scan=11188", first.nativeId()),
                () -> assertEquals(0, first.index()),
                () -> assertEquals(1, first.position()),
                () -> assertEquals(OptionalLong.of(11188), first.scanNumber()),
                () -> assertEquals(Optional.of("615.788391113281"), first.precursorMz()),
                () -> assertEquals(OptionalInt.of(2), first.charge()),
                () ->
                        assertEquals(
                                Optional.of(
                                        new RetentionTime(
                                                "4740.95599999998", RetentionTime.Unit.SECOND)),
                                first.retentionTime()),
                () ->
                        assertArrayEquals(
                                new double[] {103.490119934082, 1144.85205078125},
                                first.peaks().mz()),
                () ->
                        assertArrayEquals(
                                new double[] {12446.078125, 0.5}, first.peaks().intensity()),
                () -> assertEquals(PeakEncoding.FLOAT64, first.peaks().mzEncoding()),
                () -> assertEquals(PeakEncoding.FLOAT64, first.peaks().intensityEncoding()),
                () -> assertEquals("second", second.nativeId()),
                () -> assertEquals(1, second.index()),
                () -> assertEquals(2, second.position()),
                () -> assertEquals(Optional.of("1.5e2"), second.precursorMz()),
                () -> assertEquals(OptionalInt.of(-3), second.charge()),
                () -> assertEquals(Optional.empty(), second.retentionTime()),
                () -> assertEquals(0, second.peaks().size()),
                () -> assertEquals(OptionalInt.of(4), third.charge()),
                () -> assertEquals(Optional.empty(), third.precursorMz()),
                () -> assertEquals(OptionalLong.empty(), third.scanNumber()));
    }

    @Test
    @DisplayName("many peaks are all kept, in order")
    void manyPeaks() throws IOException {
        StringBuilder text = new StringBuilder("BEGIN IONS\nTITLE=t\n");
        for (int i = 0; i < 1000; i++) {
            text.append(i).append(".25 ").append(i * 2).append('\n');
        }
        double[] peaks = read(file(text.append("END IONS\n").toString())).get(0).peaks().mz();

        assertAll(
                () -> assertEquals(1000, peaks.length),
                () -> assertEquals(0.25, peaks[0]),
                () -> assertEquals(999.25, peaks[999]));
    }

    @Test
    @DisplayName("an empty file holds no spectra")
    void empty() throws IOException {
        assertEquals(List.of(), read(file("")));
    }

    @Test
    @DisplayName("structural damage is refused, naming the line")
    void structure() throws IOException {
        assertAll(
                () ->
                        assertEquals(
                                "line 1: \"hello\" is outside any entry and is neither a parameter"
                                        + " nor BEGIN IONS",
                                failure("hello\n")),
                () ->
                        assertEquals(
                                "line 3: BEGIN IONS inside the entry opened on line 1",
                                failure("BEGIN IONS\nTITLE=a\nBEGIN IONS\n")),
                () ->
                        assertEquals(
                                "the entry opened on line 2 has no END IONS before the end of the"
                                        + " file",
                                failure("\nBEGIN IONS\nTITLE=a\n1 2\n")),
                () ->
                        assertEquals(
                                "the entry opened on line 1 has no TITLE, so nothing could find its"
                                        + " spectrum again",
                                failure("BEGIN IONS\n1 2\nEND IONS\n")),
                () ->
                        assertEquals(
                                "the entry opened on line 1 has no TITLE, so nothing could find its"
                                        + " spectrum again",
                                failure("BEGIN IONS\nTITLE=\nEND IONS\n")));
    }

    @Test
    @DisplayName("global parameters that would set a default for every entry are refused")
    void globals() throws IOException {
        for (String key : List.of("CHARGE", "PEPMASS", "TITLE", "RTINSECONDS", "charge")) {
            assertEquals(
                    "line 2: a global "
                            + key.toUpperCase(java.util.Locale.ROOT)
                            + "= before the first entry sets a default for every entry, which this"
                            + " reader does not apply",
                    failure("COM=x\n" + key + "=2+\nBEGIN IONS\nTITLE=a\nEND IONS\n"));
        }
    }

    @Test
    @DisplayName("peak lines must be exactly two decimal numbers")
    void peaks() throws IOException {
        String open = "BEGIN IONS\nTITLE=a\n";
        assertAll(
                () ->
                        assertEquals(
                                "line 3: the peak line \"1 2 1+\" has 3 "
                                        + "fields; exactly two (m/z and"
                                        + " intensity) can be carried over",
                                failure(open + "1 2 1+\nEND IONS\n")),
                () ->
                        assertEquals(
                                "line 3: the peak line \"100.5\" has 1 fields; exactly two (m/z and"
                                        + " intensity) can be carried over",
                                failure(open + "100.5\nEND IONS\n")),
                () ->
                        assertEquals(
                                "line 3: the m/z \"NaN\" is not a decimal number",
                                failure(open + "NaN 2\nEND IONS\n")),
                () ->
                        assertEquals(
                                "line 3: the intensity \"0x1p3\" is not a decimal number",
                                failure(open + "1 0x1p3\nEND IONS\n")),
                () ->
                        assertEquals(
                                "line 3: the intensity \"2d\" is not a decimal number",
                                failure(open + "1 2d\nEND IONS\n")),
                () ->
                        assertEquals(
                                "line 3: the m/z \"1e400\" is beyond the range of a 64-bit float",
                                failure(open + "1e400 2\nEND IONS\n")),
                () ->
                        assertEquals(
                                "line 3: the intensity \"-1e999\" is beyond the range of a 64-bit"
                                        + " float",
                                failure(open + "1 -1e999\nEND IONS\n")));
    }

    @Test
    @DisplayName("decimal forms Double.parseDouble and the reader both accept are read exactly")
    void decimalForms() throws IOException {
        double[] mz =
                read(file(
                                "BEGIN IONS\nTITLE=a\n"
                                        + "+1 1\n-2. 1\n.5 1\n1E3 1\n1.5e-3 1\n"
                                        + "END IONS\n"))
                        .get(0)
                        .peaks()
                        .mz();

        assertArrayEquals(new double[] {1, -2, 0.5, 1000, 0.0015}, mz);
    }

    @Test
    @DisplayName("bad, repeated or multiple parameter values are refused")
    void parameters() throws IOException {
        String open = "BEGIN IONS\nTITLE=a\n";
        assertAll(
                () ->
                        assertEquals(
                                "line 3: TITLE appears twice in one entry",
                                failure(open + "TITLE=b\nEND IONS\n")),
                () ->
                        assertEquals(
                                "line 4: PEPMASS appears twice in one entry",
                                failure(open + "PEPMASS=1\nPEPMASS=2\nEND IONS\n")),
                () ->
                        assertEquals(
                                "line 4: CHARGE appears twice in one entry",
                                failure(open + "CHARGE=2+\nCHARGE=2+\nEND IONS\n")),
                () ->
                        assertEquals(
                                "line 4: RTINSECONDS appears twice in one entry",
                                failure(open + "RTINSECONDS=1\nRTINSECONDS=1\nEND IONS\n")),
                () ->
                        assertEquals(
                                "line 3: PEPMASS \"abc 12\" does not start with a decimal m/z",
                                failure(open + "PEPMASS=abc 12\nEND IONS\n")),
                () ->
                        assertEquals(
                                "line 3: CHARGE \"2+ and 3+\" is not one charge such as 2+",
                                failure(open + "CHARGE=2+ and 3+\nEND IONS\n")),
                () ->
                        assertEquals(
                                "line 3: RTINSECONDS: a retention time must "
                                        + "be a decimal number, but"
                                        + " was \"10-20\"",
                                failure(open + "RTINSECONDS=10-20\nEND IONS\n")));
    }

    @Test
    @DisplayName("a file that is not UTF-8 is refused")
    void notUtf8() throws IOException {
        Path file =
                Files.write(
                        directory.resolve("latin.mgf"),
                        new byte[] {
                            'B',
                            'E',
                            'G',
                            'I',
                            'N',
                            ' ',
                            'I',
                            'O',
                            'N',
                            'S',
                            '\n',
                            'T',
                            'I',
                            'T',
                            'L',
                            'E',
                            '=',
                            (byte) 0xE9,
                            '\n'
                        });

        assertTrue(
                assertThrows(SpectrumFileException.class, () -> read(file))
                        .getMessage()
                        .startsWith(file + ": is not UTF-8 text after line "));
    }

    @Test
    @DisplayName("a visitor's exception stops the read and reaches the caller unchanged")
    void visitorException() throws IOException {
        Path file = file("BEGIN IONS\nTITLE=a\nEND IONS\n");
        IOException stop = new IOException("stop");

        assertSame(
                stop,
                assertThrows(
                        IOException.class,
                        () ->
                                MgfSpectrumReader.read(
                                        file,
                                        s -> {
                                            throw stop;
                                        })));
    }

    @Test
    @DisplayName("comments are lines starting #, ;, ! or /")
    void comments() {
        assertAll(
                () -> assertTrue(MgfSpectrumReader.isComment("#x")),
                () -> assertTrue(MgfSpectrumReader.isComment(";x")),
                () -> assertTrue(MgfSpectrumReader.isComment("!x")),
                () -> assertTrue(MgfSpectrumReader.isComment("/x")),
                () -> assertEquals(false, MgfSpectrumReader.isComment("1 2")),
                () -> assertEquals(false, MgfSpectrumReader.isComment("BEGIN IONS")));
    }

    @Test
    @DisplayName("both arguments are required")
    void nulls() {
        Path file = directory.resolve("x.mgf");
        assertAll(
                () ->
                        assertEquals(
                                "file",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        MgfSpectrumReader.read(
                                                                Nulls.of(Path.class), s -> {}))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "visitor",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        MgfSpectrumReader.read(
                                                                file,
                                                                Nulls.of(SpectrumVisitor.class)))
                                        .getMessage()));
    }
}
