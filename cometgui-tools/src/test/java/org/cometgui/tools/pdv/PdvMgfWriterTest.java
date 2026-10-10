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

import static org.cometgui.tools.pdv.MzMlSpectrumReader.INTENSITY_ARRAY;
import static org.cometgui.tools.pdv.MzMlSpectrumReader.MZ_ARRAY;
import static org.cometgui.tools.pdv.MzMlText.doubles;
import static org.cometgui.tools.pdv.MzMlText.level;
import static org.cometgui.tools.pdv.MzMlText.ms1;
import static org.cometgui.tools.pdv.MzMlText.ms2;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.InflaterInputStream;
import org.cometgui.tools.testing.K562Run;
import org.cometgui.tools.testing.Nulls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The MGF PDV is given: exact values, both shapes, atomic writing, and the real fixture. */
class PdvMgfWriterTest {

    private static final String ID = "controllerType=0 controllerNumber=1 scan=8";

    @TempDir private Path directory;

    private static SourceSpectrum spectrum(
            String id,
            Optional<String> precursor,
            OptionalInt charge,
            Optional<RetentionTime> time,
            PeakList peaks) {
        return new SourceSpectrum(
                id, 3, 4, SourceSpectrum.scanNumberOf(id), precursor, charge, time, peaks);
    }

    private static PeakList peaks(double mz, PeakEncoding mzEncoding, double intensity) {
        return new PeakList(
                new double[] {mz}, mzEncoding, new double[] {intensity}, PeakEncoding.FLOAT64);
    }

    private String refusal(SourceSpectrum spectrum) {
        Path source = directory.resolve("src.mzML");
        return assertThrows(SpectrumFileException.class, () -> PdvMgfWriter.entry(source, spectrum))
                .getMessage();
    }

    private List<String> filesIn(Path dir) throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            return files.map(f -> String.valueOf(f.getFileName())).sorted().toList();
        }
    }

    // ------------------------------------------------------------------ values

    @Test
    @DisplayName("a 32-bit value is written as the float's exact decimal expansion, plain")
    void float32IsExact() {
        assertAll(
                () ->
                        assertEquals(
                                "755.84100341796875",
                                PdvMgfWriter.decimal(755.841f, PeakEncoding.FLOAT32)),
                () ->
                        assertEquals(
                                "100.09999847412109375",
                                PdvMgfWriter.decimal(100.1f, PeakEncoding.FLOAT32)),
                () ->
                        assertEquals(
                                "16777216",
                                PdvMgfWriter.decimal(1.6777216E7f, PeakEncoding.FLOAT32)),
                () ->
                        assertEquals(
                                "0.00999999977648258209228515625",
                                PdvMgfWriter.decimal(0.01f, PeakEncoding.FLOAT32)),
                () -> assertEquals("-2.5", PdvMgfWriter.decimal(-2.5f, PeakEncoding.FLOAT32)));
    }

    @Test
    @DisplayName("a 64-bit value is written as its shortest round-trip decimal, plain")
    void float64IsShortest() {
        assertAll(
                () -> assertEquals("0.1", PdvMgfWriter.decimal(0.1, PeakEncoding.FLOAT64)),
                () ->
                        assertEquals(
                                "755.841003417969",
                                PdvMgfWriter.decimal(755.841003417969, PeakEncoding.FLOAT64)),
                () ->
                        assertEquals(
                                "12345678",
                                PdvMgfWriter.decimal(1.2345678E7, PeakEncoding.FLOAT64)),
                () -> assertEquals("0.000010", PdvMgfWriter.decimal(1.0E-5, PeakEncoding.FLOAT64)),
                () -> assertEquals("-3.5", PdvMgfWriter.decimal(-3.5, PeakEncoding.FLOAT64)));
    }

    @Test
    @DisplayName("zero keeps its sign; NaN, infinity and a false 32-bit claim are refused")
    void edgeValues() {
        assertAll(
                () -> assertEquals("0", PdvMgfWriter.decimal(0.0, PeakEncoding.FLOAT64)),
                () -> assertEquals("-0", PdvMgfWriter.decimal(-0.0, PeakEncoding.FLOAT64)),
                () -> assertEquals("0", PdvMgfWriter.decimal(0.0, PeakEncoding.FLOAT32)),
                () -> assertEquals("-0", PdvMgfWriter.decimal(-0.0, PeakEncoding.FLOAT32)),
                () ->
                        assertEquals(
                                Double.doubleToRawLongBits(-0.0),
                                Double.doubleToRawLongBits(Double.parseDouble("-0"))),
                () ->
                        assertEquals(
                                "NaN has no faithful MGF form",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () ->
                                                        PdvMgfWriter.decimal(
                                                                Double.NaN, PeakEncoding.FLOAT64))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "Infinity has no faithful MGF form",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () ->
                                                        PdvMgfWriter.decimal(
                                                                Double.POSITIVE_INFINITY,
                                                                PeakEncoding.FLOAT32))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "-Infinity has no faithful MGF form",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () ->
                                                        PdvMgfWriter.decimal(
                                                                Double.NEGATIVE_INFINITY,
                                                                PeakEncoding.FLOAT64))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "0.1 is said to be a 32-bit float and is not one",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () ->
                                                        PdvMgfWriter.decimal(
                                                                0.1, PeakEncoding.FLOAT32))
                                        .getMessage()));
    }

    @Test
    @DisplayName(
            "every value round-trips exactly: 200,000 random bit patterns, parsed back as double"
                    + " (and as float for 32-bit), never in exponent notation")
    void roundTripProperty() {
        long[] state = {0x9E3779B97F4A7C15L};
        int checked = 0;
        while (checked < 100_000) {
            double value = Double.longBitsToDouble(next(state));
            if (!Double.isFinite(value)) {
                continue;
            }
            String text = PdvMgfWriter.decimal(value, PeakEncoding.FLOAT64);
            assertEquals(
                    Double.doubleToRawLongBits(value),
                    Double.doubleToRawLongBits(Double.parseDouble(text)),
                    text);
            assertFalse(text.contains("E") || text.contains("e"), text);
            checked++;
        }
        checked = 0;
        while (checked < 100_000) {
            float value = Float.intBitsToFloat((int) next(state));
            if (!Float.isFinite(value)) {
                continue;
            }
            String text = PdvMgfWriter.decimal(value, PeakEncoding.FLOAT32);
            assertEquals(
                    Float.floatToRawIntBits(value),
                    Float.floatToRawIntBits(Float.parseFloat(text)),
                    text);
            assertEquals(
                    Double.doubleToRawLongBits(value),
                    Double.doubleToRawLongBits(Double.parseDouble(text)),
                    "parsed as a double, the text is the float widened: " + text);
            assertFalse(text.contains("E") || text.contains("e"), text);
            checked++;
        }
    }

    /** xorshift64: a fixed, reproducible stream of bit patterns. */
    private static long next(long[] state) {
        long x = state[0];
        x ^= x << 13;
        x ^= x >>> 7;
        x ^= x << 17;
        state[0] = x;
        return x;
    }

    // ------------------------------------------------------------------ one entry

    @Test
    @DisplayName("an entry carries the title, precursor text, signed charge, seconds and peaks")
    void entryText() throws IOException {
        SourceSpectrum full =
                spectrum(
                        ID,
                        Optional.of("615.788391113281"),
                        OptionalInt.of(2),
                        Optional.of(new RetentionTime("1.5", RetentionTime.Unit.MINUTE)),
                        new PeakList(
                                new double[] {100.1f, 200.25f},
                                PeakEncoding.FLOAT32,
                                new double[] {5.5, 1.0E7},
                                PeakEncoding.FLOAT64));
        SourceSpectrum bare =
                spectrum(
                        "t",
                        Optional.of("1.5E2"),
                        OptionalInt.of(-3),
                        Optional.empty(),
                        new PeakList(
                                new double[0],
                                PeakEncoding.FLOAT64,
                                new double[0],
                                PeakEncoding.FLOAT64));
        SourceSpectrum unchargedTimed =
                spectrum(
                        "u",
                        Optional.of("1"),
                        OptionalInt.empty(),
                        Optional.of(new RetentionTime("4.25", RetentionTime.Unit.SECOND)),
                        peaks(1, PeakEncoding.FLOAT64, 2));
        Path source = directory.resolve("src.mzML");
        assertAll(
                () ->
                        assertEquals(
                                "BEGIN IONS\n"
                                        + "TITLE="
                                        + ID
                                        + "\n"
                                        + "PEPMASS=615.788391113281\n"
                                        + "CHARGE=2+\n"
                                        + "RTINSECONDS=90\n"
                                        + "100.09999847412109375 5.5\n"
                                        + "200.25 10000000\n"
                                        + "END IONS\n",
                                PdvMgfWriter.entry(source, full)),
                () ->
                        assertEquals(
                                "BEGIN IONS\nTITLE=t\nPEPMASS=1.5E2\nCHARGE=3-\nEND IONS\n",
                                PdvMgfWriter.entry(source, bare)),
                () ->
                        assertEquals(
                                "BEGIN IONS\nTITLE=u\nPEPMASS=1\nRTINSECONDS=4.25\n1.0 2.0\n"
                                        + "END IONS\n",
                                PdvMgfWriter.entry(source, unchargedTimed)));
    }

    @Test
    @DisplayName("an entry that cannot be written faithfully is refused, naming the spectrum")
    void entryRefusals() {
        PeakList one = peaks(1, PeakEncoding.FLOAT64, 2);
        String prefix = directory.resolve("src.mzML") + ": spectrum \"";
        assertAll(
                () ->
                        assertEquals(
                                prefix
                                        + ID
                                        + "\" (index 3): has no selected ion m/z, which PDV needs"
                                        + " as the MGF PEPMASS",
                                refusal(
                                        spectrum(
                                                ID,
                                                Optional.empty(),
                                                OptionalInt.empty(),
                                                Optional.empty(),
                                                one))),
                () ->
                        assertEquals(
                                prefix
                                        + ID
                                        + "\" (index 3): has the selected ion m/z \"5x\", which is"
                                        + " not a decimal number",
                                refusal(
                                        spectrum(
                                                ID,
                                                Optional.of("5x"),
                                                OptionalInt.empty(),
                                                Optional.empty(),
                                                one))),
                () ->
                        assertEquals(
                                prefix
                                        + ID
                                        + "\" (index 3): states the precursor charge 0, which an"
                                        + " MGF CHARGE cannot carry",
                                refusal(
                                        spectrum(
                                                ID,
                                                Optional.of("5"),
                                                OptionalInt.of(0),
                                                Optional.empty(),
                                                one))),
                () ->
                        assertEquals(
                                prefix
                                        + "a\nb\" (index 3): has a native id that holds a line"
                                        + " break or begins or ends with whitespace, which an MGF"
                                        + " title cannot carry so that PDV finds it again",
                                refusal(
                                        spectrum(
                                                "a\nb",
                                                Optional.of("5"),
                                                OptionalInt.empty(),
                                                Optional.empty(),
                                                one))),
                () ->
                        assertTrue(
                                refusal(
                                                spectrum(
                                                        "a\rb",
                                                        Optional.of("5"),
                                                        OptionalInt.empty(),
                                                        Optional.empty(),
                                                        one))
                                        .contains("has a native id that holds a line break")),
                () ->
                        assertTrue(
                                refusal(
                                                spectrum(
                                                        " a",
                                                        Optional.of("5"),
                                                        OptionalInt.empty(),
                                                        Optional.empty(),
                                                        one))
                                        .contains("has a native id that holds a line break")),
                () ->
                        assertEquals(
                                prefix
                                        + ID
                                        + "\" (index 3): peak 1 cannot be written faithfully: NaN"
                                        + " has no faithful MGF form",
                                refusal(
                                        spectrum(
                                                ID,
                                                Optional.of("5"),
                                                OptionalInt.empty(),
                                                Optional.empty(),
                                                new PeakList(
                                                        new double[] {1, 2},
                                                        PeakEncoding.FLOAT64,
                                                        new double[] {1, Double.NaN},
                                                        PeakEncoding.FLOAT64)))),
                () ->
                        assertEquals(
                                prefix
                                        + ID
                                        + "\" (index 3): peak 0 cannot be written faithfully: 0.1"
                                        + " is said to be a 32-bit float and is not one",
                                refusal(
                                        spectrum(
                                                ID,
                                                Optional.of("5"),
                                                OptionalInt.empty(),
                                                Optional.empty(),
                                                peaks(0.1, PeakEncoding.FLOAT32, 1)))));
    }

    // ------------------------------------------------------------------ whole files

    private Path constructedMzMl() throws IOException {
        return MzMlText.document()
                .spectrum(ms1(0, "scan=1"))
                .spectrum(ms2(1, "scan=2", "500.25", "2", 100.5, 10.0, 200.75, 20.0))
                .spectrum(ms2(2, "scan=3", "600.5", "3", 300.125, 30.0))
                .write(directory, "run.mzML");
    }

    @Test
    @DisplayName("one entry per MS2 spectrum, in source order, and nothing else left behind")
    void everySpectrum() throws IOException {
        Path source = constructedMzMl();
        Path out = Files.createDirectory(directory.resolve("pdv"));

        MgfFile written =
                PdvMgfWriter.writeEverySpectrum(source, SpectrumFileFormat.MZML, out, "run.mgf");

        assertAll(
                () -> assertEquals(out.resolve("run.mgf"), written.file()),
                () -> assertEquals(List.of("scan=2", "scan=3"), written.titles()),
                () -> assertEquals(2, written.entries()),
                () ->
                        assertEquals(
                                "BEGIN IONS\nTITLE=scan=2\nPEPMASS=500.25\nCHARGE=2+\n"
                                        + "RTINSECONDS=90\n100.5 10.0\n200.75 20.0\nEND IONS\n"
                                        + "BEGIN IONS\nTITLE=scan=3\nPEPMASS=600.5\nCHARGE=3+\n"
                                        + "RTINSECONDS=90\n300.125 30.0\nEND IONS\n",
                                Files.readString(written.file(), StandardCharsets.UTF_8)),
                () -> assertEquals(List.of("run.mgf"), filesIn(out), "no partial file is left"));
    }

    @Test
    @DisplayName("writing again replaces the file whole")
    void replaces() throws IOException {
        Path out = Files.createDirectory(directory.resolve("pdv"));
        Files.writeString(out.resolve("run.mgf"), "old");

        PdvMgfWriter.writeEverySpectrum(constructedMzMl(), SpectrumFileFormat.MZML, out, "run.mgf");

        assertTrue(Files.readString(out.resolve("run.mgf")).startsWith("BEGIN IONS\nTITLE=scan=2"));
    }

    @Test
    @DisplayName("a failure leaves no file, partial or final, and an old file untouched")
    void failureLeavesNothing() throws IOException {
        Path source =
                MzMlText.document()
                        .spectrum(ms2(0, "scan=2", "500.25", "2", 1, 2))
                        .spectrum(ms2(1, "scan=3", null, "2", 1, 2))
                        .write(directory, "bad.mzML");
        Path out = Files.createDirectory(directory.resolve("pdv"));
        Path empty = Files.createDirectory(directory.resolve("pdv2"));
        Files.writeString(out.resolve("run.mgf"), "old");

        SpectrumFileException thrown =
                assertThrows(
                        SpectrumFileException.class,
                        () ->
                                PdvMgfWriter.writeEverySpectrum(
                                        source, SpectrumFileFormat.MZML, out, "run.mgf"));
        assertThrows(
                SpectrumFileException.class,
                () ->
                        PdvMgfWriter.writeOnePerRow(
                                source,
                                SpectrumFileFormat.MZML,
                                List.of("scan=3"),
                                empty,
                                "rows.mgf"));

        assertAll(
                () ->
                        assertEquals(
                                source
                                        + ": spectrum \"scan=3\" (index 1): has no selected ion"
                                        + " m/z, which PDV needs as the MGF PEPMASS",
                                thrown.getMessage()),
                () -> assertEquals(List.of("run.mgf"), filesIn(out)),
                () -> assertEquals("old", Files.readString(out.resolve("run.mgf"))),
                () -> assertEquals(List.of(), filesIn(empty)));
    }

    @Test
    @DisplayName("a source with no MS2 spectrum, or a repeated native id, is refused")
    void sourceRefusals() throws IOException {
        Path ms1Only = MzMlText.document().spectrum(ms1(0, "a")).write(directory, "ms1.mzML");
        Path repeated =
                MzMlText.document()
                        .spectrum(ms2(0, "same", "1", "2", 1, 2))
                        .spectrum(ms2(4, "same", "1", "2", 1, 2))
                        .write(directory, "rep.mzML");
        Path out = Files.createDirectory(directory.resolve("pdv"));
        assertAll(
                () ->
                        assertEquals(
                                ms1Only + ": holds no MS2 spectrum to give PDV",
                                assertThrows(
                                                SpectrumFileException.class,
                                                () ->
                                                        PdvMgfWriter.writeEverySpectrum(
                                                                ms1Only,
                                                                SpectrumFileFormat.MZML,
                                                                out,
                                                                "a.mgf"))
                                        .getMessage()),
                () ->
                        assertEquals(
                                repeated
                                        + ": spectrum \"same\" (index 4): repeats the native id of"
                                        + " the spectrum at index 0, so a title could not say which"
                                        + " spectrum it is",
                                assertThrows(
                                                SpectrumFileException.class,
                                                () ->
                                                        PdvMgfWriter.writeEverySpectrum(
                                                                repeated,
                                                                SpectrumFileFormat.MZML,
                                                                out,
                                                                "b.mgf"))
                                        .getMessage()),
                () ->
                        assertEquals(
                                repeated
                                        + ": spectrum \"same\" (index 4): repeats the native id of"
                                        + " the spectrum at index 0, so a title could not say which"
                                        + " spectrum it is",
                                assertThrows(
                                                SpectrumFileException.class,
                                                () ->
                                                        PdvMgfWriter.writeOnePerRow(
                                                                repeated,
                                                                SpectrumFileFormat.MZML,
                                                                List.of("same"),
                                                                out,
                                                                "c.mgf"))
                                        .getMessage()),
                () -> assertEquals(List.of(), filesIn(out)));
    }

    @Test
    @DisplayName("one entry per row, in row order, a repeated spectrum written as an exact copy")
    void onePerRow() throws IOException {
        Path out = Files.createDirectory(directory.resolve("pdv"));

        MgfFile written =
                PdvMgfWriter.writeOnePerRow(
                        constructedMzMl(),
                        SpectrumFileFormat.MZML,
                        List.of("scan=3", "scan=2", "scan=3"),
                        out,
                        "rows.mgf");

        String three =
                "BEGIN IONS\nTITLE=scan=3\nPEPMASS=600.5\nCHARGE=3+\nRTINSECONDS=90\n"
                        + "300.125 30.0\nEND IONS\n";
        String two =
                "BEGIN IONS\nTITLE=scan=2\nPEPMASS=500.25\nCHARGE=2+\nRTINSECONDS=90\n"
                        + "100.5 10.0\n200.75 20.0\nEND IONS\n";
        assertAll(
                () -> assertEquals(List.of("scan=3", "scan=2", "scan=3"), written.titles()),
                () -> assertEquals(three + two + three, Files.readString(written.file())),
                () -> assertEquals(List.of("rows.mgf"), filesIn(out)));
    }

    @Test
    @DisplayName(
            "a row asking for a spectrum the source lacks fails, naming the row; none is empty")
    void rowRefusals() throws IOException {
        Path source = constructedMzMl();
        Path out = Files.createDirectory(directory.resolve("pdv"));
        assertAll(
                () ->
                        assertEquals(
                                source
                                        + ": row 1 asks for the spectrum \"scan=1\", which is not"
                                        + " an MS2 spectrum in this file",
                                assertThrows(
                                                SpectrumFileException.class,
                                                () ->
                                                        PdvMgfWriter.writeOnePerRow(
                                                                source,
                                                                SpectrumFileFormat.MZML,
                                                                List.of("scan=2", "scan=1"),
                                                                out,
                                                                "r.mgf"))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "an MGF for PDV needs at least one row; PDV cannot open an empty"
                                        + " one",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () ->
                                                        PdvMgfWriter.writeOnePerRow(
                                                                source,
                                                                SpectrumFileFormat.MZML,
                                                                List.of(),
                                                                out,
                                                                "r.mgf"))
                                        .getMessage()),
                () -> assertEquals(List.of(), filesIn(out)));
    }

    @Test
    @DisplayName("an MGF source is re-encoded the same way")
    void fromMgf() throws IOException {
        Path source =
                Files.writeString(
                        directory.resolve("in.mgf"),
                        "BEGIN IONS\nTITLE=a\nPEPMASS=100.50 99\nCHARGE=2+\n"
                                + "RTINSECONDS=12.0\n1.50 2.0e1\nEND IONS\n");
        Path out = Files.createDirectory(directory.resolve("pdv"));

        MgfFile written =
                PdvMgfWriter.writeEverySpectrum(source, SpectrumFileFormat.MGF, out, "x.mgf");

        assertEquals(
                "BEGIN IONS\nTITLE=a\nPEPMASS=100.50\nCHARGE=2+\nRTINSECONDS=12.0\n1.5 20.0\n"
                        + "END IONS\n",
                Files.readString(written.file()));
    }

    @Test
    @DisplayName("the file name must be a plain .mgf name without _cus_, in an existing directory")
    void targets() throws IOException {
        Path source = constructedMzMl();
        for (String name : List.of("x.txt", "a/x.mgf", ".mgf", "a_cus_b.mgf", "X.MGF")) {
            assertEquals(
                    "an MGF for PDV needs a plain file name ending .mgf and not containing _cus_"
                            + " (PDV's own key separator), but was \""
                            + name
                            + "\"",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () ->
                                            PdvMgfWriter.writeEverySpectrum(
                                                    source,
                                                    SpectrumFileFormat.MZML,
                                                    directory,
                                                    name))
                            .getMessage(),
                    name);
        }
        Path missing = directory.resolve("missing");
        assertEquals(
                "cannot write the MGF for PDV into " + missing + ": it is not a directory",
                assertThrows(
                                IOException.class,
                                () ->
                                        PdvMgfWriter.writeEverySpectrum(
                                                source, SpectrumFileFormat.MZML, missing, "a.mgf"))
                        .getMessage());
    }

    @Test
    @DisplayName("arguments are required, and an unread format is refused by the dispatcher")
    void nullsAndFormats() {
        Path source = directory.resolve("s.mzML");
        assertAll(
                () ->
                        assertEquals(
                                "source",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        PdvMgfWriter.writeEverySpectrum(
                                                                Nulls.of(Path.class),
                                                                SpectrumFileFormat.MZML,
                                                                directory,
                                                                "a.mgf"))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "format",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        PdvMgfWriter.writeOnePerRow(
                                                                source,
                                                                Nulls.of(SpectrumFileFormat.class),
                                                                List.of("a"),
                                                                directory,
                                                                "a.mgf"))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "directory",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        PdvMgfWriter.writeEverySpectrum(
                                                                source,
                                                                SpectrumFileFormat.MZML,
                                                                Nulls.of(Path.class),
                                                                "a.mgf"))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "fileName",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        PdvMgfWriter.writeEverySpectrum(
                                                                source,
                                                                SpectrumFileFormat.MZML,
                                                                directory,
                                                                Nulls.of(String.class)))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "nativeIds",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        PdvMgfWriter.writeOnePerRow(
                                                                source,
                                                                SpectrumFileFormat.MZML,
                                                                Nulls.of(List.class),
                                                                directory,
                                                                "a.mgf"))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "CometGUI reads no mzXML spectra; such a file is refused before it"
                                        + " is read",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () ->
                                                        SourceSpectra.read(
                                                                source,
                                                                SpectrumFileFormat.MZXML,
                                                                s -> {}))
                                        .getMessage()));
    }

    @Test
    @DisplayName("SourceSpectra reads mzML and MGF through their readers")
    void dispatch() throws IOException {
        Path mzml = constructedMzMl();
        Path mgf = Files.writeString(directory.resolve("a.mgf"), "BEGIN IONS\nTITLE=q\nEND IONS\n");
        List<String> ids = new ArrayList<>();

        SourceSpectra.read(mzml, SpectrumFileFormat.MZML, s -> ids.add(s.nativeId()));
        SourceSpectra.read(mgf, SpectrumFileFormat.MGF, s -> ids.add(s.nativeId()));

        assertEquals(List.of("scan=2", "scan=3", "q"), ids);
    }

    @Test
    @DisplayName("MgfFile copies its titles and requires its file")
    void mgfFile() {
        List<String> titles = new ArrayList<>(List.of("a"));
        MgfFile file = new MgfFile(directory, titles);
        titles.add("b");
        assertAll(
                () -> assertEquals(List.of("a"), file.titles()),
                () -> assertEquals(1, file.entries()),
                () ->
                        assertEquals(
                                "file",
                                assertThrows(
                                                NullPointerException.class,
                                                () -> new MgfFile(Nulls.of(Path.class), List.of()))
                                        .getMessage()));
    }

    // ------------------------------------------------------------------ the real fixture

    @Test
    @DisplayName(
            "the real K562 _3 mzML: every peak of all 728 MS2 spectra parses back from the MGF to"
                    + " the exact double the mzML holds, checked by readers independent of the"
                    + " product's")
    void realFixtureRoundTrip() throws IOException {
        Path source = K562Run.mzml();
        Path out = Files.createTempDirectory(buildDirectory(), "mgf-roundtrip-");
        try {
            MgfFile written =
                    PdvMgfWriter.writeEverySpectrum(
                            source, SpectrumFileFormat.MZML, out, "k562_3.mgf");
            List<IndependentSpectrum> truth = independentMzMl(source);
            List<IndependentSpectrum> mgf = independentMgf(written.file());

            assertEquals(728, truth.size(), "the fixture holds 728 MS2 spectra");
            assertEquals(truth.size(), mgf.size(), "one entry per MS2 spectrum");
            long peaksCompared = 0;
            for (int n = 0; n < truth.size(); n++) {
                IndependentSpectrum want = truth.get(n);
                IndependentSpectrum got = mgf.get(n);
                assertEquals(want.id(), got.id(), "entry " + n);
                assertEquals(want.precursor(), got.precursor(), want.id());
                assertEquals(want.mz().length, got.mz().length, want.id());
                for (int i = 0; i < want.mz().length; i++) {
                    assertEquals(
                            Double.doubleToRawLongBits(want.mz()[i]),
                            Double.doubleToRawLongBits(got.mz()[i]),
                            want.id() + " m/z " + i);
                    assertEquals(
                            Double.doubleToRawLongBits(want.intensity()[i]),
                            Double.doubleToRawLongBits(got.intensity()[i]),
                            want.id() + " intensity " + i);
                    peaksCompared++;
                }
            }
            assertTrue(peaksCompared > 50_000, "peaks compared: " + peaksCompared);
            assertEquals(
                    truth.stream().map(IndependentSpectrum::id).toList(),
                    written.titles(),
                    "the titles are the native ids, in order");
        } finally {
            try (Stream<Path> files = Files.list(out)) {
                for (Path file : files.toList()) {
                    Files.delete(file);
                }
            }
            Files.delete(out);
        }
    }

    private static Path buildDirectory() throws IOException {
        return Files.createDirectories(
                Path.of(System.getProperty("cometgui.buildDirectory", "target")).toAbsolutePath());
    }

    /** A spectrum as a reader that shares nothing with the product's sees it. */
    record IndependentSpectrum(String id, String precursor, double[] mz, double[] intensity) {}

    private static final Pattern SPECTRUM =
            Pattern.compile(
                    "<spectrum [^>]*?id=\"([^\"]*)\"[^>]*>(.*?)</spectrum>", Pattern.DOTALL);
    private static final Pattern ARRAY =
            Pattern.compile("<binaryDataArray[^>]*>(.*?)</binaryDataArray>", Pattern.DOTALL);
    private static final Pattern BINARY = Pattern.compile("<binary>([^<]*)</binary>");
    private static final Pattern SELECTED =
            Pattern.compile(
                    "accession=\"MS:1000744\" name=\"selected ion m/z\" value=\"([^\"]*)\"");

    /** The fixture's MS2 spectra, by regular expression and InflaterInputStream. */
    private static List<IndependentSpectrum> independentMzMl(Path file) throws IOException {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        List<IndependentSpectrum> spectra = new ArrayList<>();
        Matcher spectrum = SPECTRUM.matcher(text);
        while (spectrum.find()) {
            String body = spectrum.group(2);
            if (!body.contains("accession=\"MS:1000511\" name=\"ms level\" value=\"2\"")) {
                continue;
            }
            Matcher selected = SELECTED.matcher(body);
            assertTrue(selected.find(), spectrum.group(1));
            double[] mz = null;
            double[] intensity = null;
            Matcher array = ARRAY.matcher(body);
            while (array.find()) {
                String part = array.group(1);
                assertTrue(
                        part.contains("MS:1000523") && part.contains("MS:1000574"),
                        "this check decodes the fixture's 64-bit zlib arrays only");
                Matcher binary = BINARY.matcher(part);
                assertTrue(binary.find());
                double[] values = inflate64(binary.group(1));
                if (part.contains("MS:1000514")) {
                    mz = values;
                } else if (part.contains("MS:1000515")) {
                    intensity = values;
                }
            }
            spectra.add(
                    new IndependentSpectrum(spectrum.group(1), selected.group(1), mz, intensity));
        }
        return spectra;
    }

    private static double[] inflate64(String base64) throws IOException {
        byte[] raw;
        try (InputStream in =
                new InflaterInputStream(
                        new ByteArrayInputStream(Base64.getMimeDecoder().decode(base64)))) {
            raw = in.readAllBytes();
        }
        ByteBuffer buffer = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
        double[] values = new double[raw.length / 8];
        for (int i = 0; i < values.length; i++) {
            values[i] = buffer.getDouble();
        }
        return values;
    }

    /** The MGF's entries, by splitting lines and BigDecimal. */
    private static List<IndependentSpectrum> independentMgf(Path file) throws IOException {
        List<IndependentSpectrum> entries = new ArrayList<>();
        String id = null;
        String precursor = null;
        List<double[]> peaks = new ArrayList<>();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (line.startsWith("TITLE=")) {
                id = line.substring(6);
            } else if (line.startsWith("PEPMASS=")) {
                precursor = line.substring(8);
            } else if ("END IONS".equals(line)) {
                double[] mz = new double[peaks.size()];
                double[] intensity = new double[peaks.size()];
                for (int i = 0; i < mz.length; i++) {
                    mz[i] = peaks.get(i)[0];
                    intensity[i] = peaks.get(i)[1];
                }
                entries.add(new IndependentSpectrum(id, precursor, mz, intensity));
                peaks.clear();
            } else if (!line.isEmpty() && Character.isDigit(line.charAt(0))) {
                String[] fields = line.split(" ");
                assertEquals(2, fields.length, line);
                peaks.add(
                        new double[] {
                            new BigDecimal(fields[0]).doubleValue(),
                            new BigDecimal(fields[1]).doubleValue()
                        });
            }
        }
        return entries;
    }

    @Test
    @DisplayName("the source spectrum's 0-based index and 1-based position are kept apart")
    void indexAndPosition() throws IOException {
        Path source =
                MzMlText.document()
                        .spectrum(ms1(0, "a"))
                        .spectrum(
                                MzMlText.spectrum(
                                        7,
                                        "b",
                                        1,
                                        level(2),
                                        "",
                                        MzMlText.selectedIon("1", null),
                                        doubles(MZ_ARRAY, false, 1)
                                                + doubles(INTENSITY_ARRAY, false, 2)))
                        .write(directory, "ip.mzML");
        List<SourceSpectrum> read = new ArrayList<>();

        SourceSpectra.read(source, SpectrumFileFormat.MZML, read::add);

        assertAll(
                () -> assertEquals(7, read.get(0).index()),
                () -> assertEquals(2, read.get(0).position()),
                () -> assertEquals(OptionalLong.empty(), read.get(0).scanNumber()));
    }
}
