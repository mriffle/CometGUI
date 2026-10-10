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

import static org.cometgui.tools.pdv.MzMlSpectrumReader.FLOAT32;
import static org.cometgui.tools.pdv.MzMlSpectrumReader.FLOAT64;
import static org.cometgui.tools.pdv.MzMlSpectrumReader.INT32;
import static org.cometgui.tools.pdv.MzMlSpectrumReader.INT64;
import static org.cometgui.tools.pdv.MzMlSpectrumReader.INTENSITY_ARRAY;
import static org.cometgui.tools.pdv.MzMlSpectrumReader.MZ_ARRAY;
import static org.cometgui.tools.pdv.MzMlSpectrumReader.NO_COMPRESSION;
import static org.cometgui.tools.pdv.MzMlSpectrumReader.ZLIB;
import static org.cometgui.tools.pdv.MzMlText.array;
import static org.cometgui.tools.pdv.MzMlText.base64;
import static org.cometgui.tools.pdv.MzMlText.cv;
import static org.cometgui.tools.pdv.MzMlText.cvUnit;
import static org.cometgui.tools.pdv.MzMlText.doubles;
import static org.cometgui.tools.pdv.MzMlText.encode64;
import static org.cometgui.tools.pdv.MzMlText.floats;
import static org.cometgui.tools.pdv.MzMlText.level;
import static org.cometgui.tools.pdv.MzMlText.minutes;
import static org.cometgui.tools.pdv.MzMlText.seconds;
import static org.cometgui.tools.pdv.MzMlText.selectedIon;
import static org.cometgui.tools.pdv.MzMlText.spectrum;
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
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import org.cometgui.tools.testing.K562Run;
import org.cometgui.tools.testing.Nulls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The mzML reader, on constructed documents and on the real K562 {@code _3} fixture. */
class MzMlSpectrumReaderTest {

    private static final String ID = "controllerType=0 controllerNumber=1 scan=42";

    @TempDir private Path directory;

    private static List<SourceSpectrum> k562;

    private static synchronized List<SourceSpectrum> k562() throws IOException {
        if (k562 == null) {
            k562 = read(K562Run.mzml());
        }
        return k562;
    }

    private static List<SourceSpectrum> read(Path file) throws IOException {
        List<SourceSpectrum> spectra = new ArrayList<>();
        MzMlSpectrumReader.read(file, spectra::add);
        return spectra;
    }

    private Path one(String spectrumXml) throws IOException {
        return MzMlText.document().spectrum(spectrumXml).write(directory, "one.mzML");
    }

    /** The message reading a document holding one spectrum fails with. */
    private String failure(String spectrumXml) throws IOException {
        Path file = one(spectrumXml);
        SpectrumFileException thrown = assertThrows(SpectrumFileException.class, () -> read(file));
        assertEquals(file, thrown.file());
        return thrown.getMessage();
    }

    private String prefix() {
        return directory.resolve("one.mzML") + ": spectrum \"" + ID + "\" (index 0): ";
    }

    private static String ms2With(String arrays) {
        return spectrum(0, ID, 2, level(2), "", selectedIon("500.5", "2"), arrays);
    }

    private static String goodIntensity() {
        return doubles(INTENSITY_ARRAY, false, 10.0, 20.0);
    }

    @Test
    @DisplayName("every field of an MS2 spectrum is reported as the file states it; MS1 is skipped")
    void readsWhatTheFileStates() throws IOException {
        Path file =
                MzMlText.document()
                        .group("binary32", cv(FLOAT32), cv(NO_COMPRESSION))
                        .spectrum(MzMlText.ms1(0, "controllerType=0 controllerNumber=1 scan=7"))
                        .spectrum(
                                spectrum(
                                        1,
                                        "controllerType=0 controllerNumber=1 scan=8",
                                        2,
                                        level(2),
                                        minutes("79.015933333333"),
                                        selectedIon("615.788391113281", "2"),
                                        doubles(MZ_ARRAY, true, 103.490119934082, 1144.85205078125)
                                                + doubles(
                                                        INTENSITY_ARRAY, true, 12446.078125, 0.5)))
                        .spectrum(
                                spectrum(
                                        5,
                                        "index=5",
                                        3,
                                        level(2),
                                        seconds("4.7e2"),
                                        selectedIon("1.5E2", null),
                                        array(
                                                        List.of(
                                                                "<referenceableParamGroupRef"
                                                                        + " ref=\"binary32\"/>",
                                                                cv(MZ_ARRAY)),
                                                        base64(
                                                                false,
                                                                MzMlText.encode32(
                                                                        100.1f, 200.2f, 300.3f)),
                                                        null)
                                                + floats(INTENSITY_ARRAY, true, 1f, 2f, 3f)))
                        .write(directory, "doc.mzML");

        List<SourceSpectrum> spectra = read(file);

        assertEquals(2, spectra.size(), "the MS1 spectrum is skipped, its broken binary unread");
        SourceSpectrum first = spectra.get(0);
        SourceSpectrum second = spectra.get(1);
        assertAll(
                () -> assertEquals("controllerType=0 controllerNumber=1 scan=8", first.nativeId()),
                () -> assertEquals(1, first.index()),
                () -> assertEquals(2, first.position(), "the MS1 spectrum is position 1"),
                () -> assertEquals(OptionalLong.of(8), first.scanNumber()),
                () -> assertEquals(Optional.of("615.788391113281"), first.precursorMz()),
                () -> assertEquals(OptionalInt.of(2), first.charge()),
                () ->
                        assertEquals(
                                Optional.of(
                                        new RetentionTime(
                                                "79.015933333333", RetentionTime.Unit.MINUTE)),
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
                () -> assertEquals("index=5", second.nativeId()),
                () -> assertEquals(5, second.index()),
                () -> assertEquals(3, second.position()),
                () -> assertEquals(OptionalLong.empty(), second.scanNumber()),
                () -> assertEquals(Optional.of("1.5E2"), second.precursorMz()),
                () -> assertEquals(OptionalInt.empty(), second.charge()),
                () ->
                        assertEquals(
                                Optional.of(new RetentionTime("4.7e2", RetentionTime.Unit.SECOND)),
                                second.retentionTime()),
                () ->
                        assertArrayEquals(
                                new double[] {100.1f, 200.2f, 300.3f},
                                second.peaks().mz(),
                                "a 32-bit value is held as the double it widens to, exactly"),
                () -> assertArrayEquals(new double[] {1, 2, 3}, second.peaks().intensity()),
                () -> assertEquals(PeakEncoding.FLOAT32, second.peaks().mzEncoding()),
                () -> assertEquals(PeakEncoding.FLOAT32, second.peaks().intensityEncoding()));
    }

    @Test
    @DisplayName("an indexed mzML, a padded ms level and a unit given only by name are read")
    void indexedAndLenient() throws IOException {
        Path file =
                MzMlText.document()
                        .indexed()
                        .spectrum(
                                spectrum(
                                        0,
                                        ID,
                                        1,
                                        cv(MzMlSpectrumReader.MS_LEVEL, " 2 "),
                                        cvUnit(
                                                MzMlSpectrumReader.SCAN_START_TIME,
                                                "3",
                                                null,
                                                "second"),
                                        "<selectedIon/>",
                                        doubles(MZ_ARRAY, false, 1.0)
                                                + doubles(INTENSITY_ARRAY, false, 2.0)))
                        .spectrum(
                                spectrum(
                                        1,
                                        "s=2",
                                        1,
                                        level(3),
                                        cvUnit(
                                                MzMlSpectrumReader.SCAN_START_TIME,
                                                "3",
                                                null,
                                                "minute"),
                                        "",
                                        ""))
                        .write(directory, "indexed.mzML");

        List<SourceSpectrum> spectra = read(file);

        assertAll(
                () -> assertEquals(1, spectra.size(), "an MS3 spectrum is skipped"),
                () -> assertEquals(Optional.empty(), spectra.get(0).precursorMz()),
                () ->
                        assertEquals(
                                Optional.of(new RetentionTime("3", RetentionTime.Unit.SECOND)),
                                spectra.get(0).retentionTime()));
    }

    @Test
    @DisplayName("a unit given only by name as minutes is minutes")
    void minutesByName() throws IOException {
        Path file =
                one(
                        spectrum(
                                0,
                                ID,
                                1,
                                level(2),
                                cvUnit(MzMlSpectrumReader.SCAN_START_TIME, "3", null, "minute"),
                                "",
                                doubles(MZ_ARRAY, false, 1.0)
                                        + doubles(INTENSITY_ARRAY, false, 2.0)));

        assertEquals(
                Optional.of(new RetentionTime("3", RetentionTime.Unit.MINUTE)),
                read(file).get(0).retentionTime());
    }

    @Test
    @DisplayName("an arrayLength attribute overrides the spectrum's defaultArrayLength")
    void arrayLengthOverridesTheDefault() throws IOException {
        String mz =
                array(
                        List.of(cv(FLOAT64), cv(NO_COMPRESSION), cv(MZ_ARRAY)),
                        base64(false, encode64(1.0, 2.0)),
                        "2");
        String intensity =
                array(
                        List.of(cv(FLOAT64), cv(NO_COMPRESSION), cv(INTENSITY_ARRAY)),
                        base64(false, encode64(3.0, 4.0)),
                        "2");
        Path file = one(spectrum(0, ID, 99, level(2), "", "", mz + intensity));

        assertArrayEquals(new double[] {1.0, 2.0}, read(file).get(0).peaks().mz());
    }

    @Test
    @DisplayName("whitespace inside base64 text is ignored, as XML line wrapping puts it there")
    void wrappedBase64() throws IOException {
        String text = Base64.getEncoder().encodeToString(encode64(1.0, 2.0));
        String wrapped = text.substring(0, 5) + "\n   " + text.substring(5);
        Path file =
                one(
                        ms2With(
                                array(
                                                List.of(
                                                        cv(FLOAT64),
                                                        cv(NO_COMPRESSION),
                                                        cv(MZ_ARRAY)),
                                                wrapped,
                                                null)
                                        + goodIntensity()));

        assertArrayEquals(new double[] {1.0, 2.0}, read(file).get(0).peaks().mz());
    }

    @Test
    @DisplayName("an empty spectrum decodes to no peaks, zlib or not")
    void emptyArrays() throws IOException {
        Path file =
                one(
                        spectrum(
                                0,
                                ID,
                                0,
                                level(2),
                                "",
                                "",
                                doubles(MZ_ARRAY, true) + doubles(INTENSITY_ARRAY, false)));

        assertEquals(0, read(file).get(0).peaks().size());
    }

    @Test
    @DisplayName("a visitor's exception stops the read and reaches the caller unchanged")
    void visitorExceptionPropagates() throws IOException {
        Path file = one(ms2With(doubles(MZ_ARRAY, false, 1.0, 2.0) + goodIntensity()));
        IOException stop = new IOException("stop");

        assertSame(
                stop,
                assertThrows(
                        IOException.class,
                        () ->
                                MzMlSpectrumReader.read(
                                        file,
                                        spectrum -> {
                                            throw stop;
                                        })));
    }

    @Test
    @DisplayName("MS-Numpress is refused, naming the term, the file and the spectrum")
    void numpress() throws IOException {
        for (String term : MzMlSpectrumReader.NUMPRESS) {
            String mz =
                    array(
                            List.of(cv(FLOAT64), cv(term), cv(MZ_ARRAY)),
                            base64(false, encode64(1.0, 2.0)),
                            null);
            assertEquals(
                    prefix()
                            + "compresses its m/z array with MS-Numpress ("
                            + term
                            + "), which this reader does not decode; re-export the file with zlib"
                            + " or no compression",
                    failure(ms2With(mz + goodIntensity())));
        }
    }

    @Test
    @DisplayName("an array naming no compression, or both, is refused")
    void compressionMustBeStatedOnce() throws IOException {
        String expected =
                prefix()
                        + "names no compression this reader decodes for its m/z array: exactly one"
                        + " of zlib (MS:1000574) and no compression (MS:1000576) must be stated";
        String none =
                array(List.of(cv(FLOAT64), cv(MZ_ARRAY)), base64(false, encode64(1, 2)), null);
        String both =
                array(
                        List.of(cv(FLOAT64), cv(ZLIB), cv(NO_COMPRESSION), cv(MZ_ARRAY)),
                        base64(false, encode64(1, 2)),
                        null);
        assertAll(
                () -> assertEquals(expected, failure(ms2With(none + goodIntensity()))),
                () -> assertEquals(expected, failure(ms2With(both + goodIntensity()))));
    }

    @Test
    @DisplayName("integer arrays and an unstated or doubly stated precision are refused")
    void precision() throws IOException {
        String int32 =
                array(
                        List.of(cv(INT32), cv(NO_COMPRESSION), cv(MZ_ARRAY)),
                        base64(false, encode64(1, 2)),
                        null);
        String int64 =
                array(
                        List.of(cv(INT64), cv(NO_COMPRESSION), cv(MZ_ARRAY)),
                        base64(false, encode64(1, 2)),
                        null);
        String neither =
                array(
                        List.of(cv(NO_COMPRESSION), cv(MZ_ARRAY)),
                        base64(false, encode64(1, 2)),
                        null);
        String both =
                array(
                        List.of(cv(FLOAT32), cv(FLOAT64), cv(NO_COMPRESSION), cv(MZ_ARRAY)),
                        base64(false, encode64(1, 2)),
                        null);
        String integers =
                prefix()
                        + "stores its m/z array as integers, which this reader does not"
                        + " decode";
        assertAll(
                () -> assertEquals(integers, failure(ms2With(int32 + goodIntensity()))),
                () -> assertEquals(integers, failure(ms2With(int64 + goodIntensity()))),
                () ->
                        assertEquals(
                                prefix()
                                        + "states neither of 32-bit float (MS:1000521) and 64-bit"
                                        + " float (MS:1000523) for its m/z array",
                                failure(ms2With(neither + goodIntensity()))),
                () ->
                        assertEquals(
                                prefix()
                                        + "states both of 32-bit float (MS:1000521) and 64-bit"
                                        + " float (MS:1000523) for its m/z array",
                                failure(ms2With(both + goodIntensity()))));
    }

    @Test
    @DisplayName("a decoded length that differs from the declared one is refused, both ways")
    void lengths() throws IOException {
        assertAll(
                () ->
                        assertEquals(
                                prefix()
                                        + "has a m/z array that decodes to 2 values but declares 3",
                                failure(
                                        spectrum(
                                                0,
                                                ID,
                                                3,
                                                level(2),
                                                "",
                                                "",
                                                doubles(MZ_ARRAY, false, 1, 2) + goodIntensity()))),
                () ->
                        assertEquals(
                                prefix()
                                        + "has a m/z array that decodes to 2 values but declares 1",
                                failure(
                                        spectrum(
                                                0,
                                                ID,
                                                1,
                                                level(2),
                                                "",
                                                "",
                                                doubles(MZ_ARRAY, false, 1, 2) + goodIntensity()))),
                () ->
                        assertEquals(
                                prefix()
                                        + "declares the length of its m/z array as \"x\", which is"
                                        + " not an integer",
                                failure(
                                        ms2With(
                                                array(
                                                                List.of(
                                                                        cv(FLOAT64),
                                                                        cv(NO_COMPRESSION),
                                                                        cv(MZ_ARRAY)),
                                                                base64(false, encode64(1, 2)),
                                                                "x")
                                                        + goodIntensity()))),
                () ->
                        assertEquals(
                                prefix()
                                        + "has a m/z array of 12 bytes, which is not a whole"
                                        + " number of 8-byte values",
                                failure(
                                        ms2With(
                                                array(
                                                                List.of(
                                                                        cv(FLOAT64),
                                                                        cv(NO_COMPRESSION),
                                                                        cv(MZ_ARRAY)),
                                                                base64(false, new byte[12]),
                                                                null)
                                                        + goodIntensity()))),
                () ->
                        assertEquals(
                                prefix()
                                        + "has a m/z array of 6 bytes, which is not a whole number"
                                        + " of 4-byte values",
                                failure(
                                        ms2With(
                                                array(
                                                                List.of(
                                                                        cv(FLOAT32),
                                                                        cv(NO_COMPRESSION),
                                                                        cv(MZ_ARRAY)),
                                                                base64(false, new byte[6]),
                                                                null)
                                                        + goodIntensity()))));
    }

    @Test
    @DisplayName("base64 and zlib damage is refused: bad text, truncation, trailing and corrupt")
    void damagedPayloads() throws IOException {
        byte[] deflated = MzMlText.deflate(encode64(1, 2));
        byte[] truncated = Arrays.copyOf(deflated, deflated.length - 4);
        byte[] trailing = Arrays.copyOf(deflated, deflated.length + 3);
        byte[] corrupt = {0x78, (byte) 0x9c, (byte) 0xff, (byte) 0xff, 0x00, 0x01};
        List<String> zlib64 = List.of(cv(FLOAT64), cv(ZLIB), cv(MZ_ARRAY));
        String encoded = Base64.getEncoder().encodeToString(truncated);
        assertAll(
                () ->
                        assertTrue(
                                failure(ms2With(array(zlib64, "@@@@", null) + goodIntensity()))
                                        .startsWith(
                                                prefix() + "has a m/z array that is not base64: ")),
                () ->
                        assertEquals(
                                prefix()
                                        + "has a m/z array whose zlib stream ends before it is"
                                        + " complete",
                                failure(ms2With(array(zlib64, encoded, null) + goodIntensity()))),
                () ->
                        assertEquals(
                                prefix()
                                        + "has a m/z array with 3 bytes after the end of its zlib"
                                        + " stream",
                                failure(
                                        ms2With(
                                                array(
                                                                zlib64,
                                                                Base64.getEncoder()
                                                                        .encodeToString(trailing),
                                                                null)
                                                        + goodIntensity()))),
                () ->
                        assertTrue(
                                failure(
                                                ms2With(
                                                        array(
                                                                        zlib64,
                                                                        Base64.getEncoder()
                                                                                .encodeToString(
                                                                                        corrupt),
                                                                        null)
                                                                + goodIntensity()))
                                        .startsWith(
                                                prefix()
                                                        + "has a m/z array that is not a valid zlib"
                                                        + " stream: ")));
    }

    @Test
    @DisplayName("a zlib stream that needs a preset dictionary cannot be completed, and is refused")
    void presetDictionary() throws IOException {
        java.util.zip.Deflater deflater = new java.util.zip.Deflater();
        deflater.setDictionary(new byte[] {1, 2, 3, 4});
        deflater.setInput(encode64(1, 2));
        deflater.finish();
        byte[] buffer = new byte[256];
        int length = deflater.deflate(buffer);
        deflater.end();
        String mz =
                array(
                        List.of(cv(FLOAT64), cv(ZLIB), cv(MZ_ARRAY)),
                        Base64.getEncoder().encodeToString(Arrays.copyOf(buffer, length)),
                        null);

        assertEquals(
                prefix() + "has a m/z array whose zlib stream ends before it is complete",
                failure(ms2With(mz + goodIntensity())));
    }

    @Test
    @DisplayName("an ms level that is missing or stated twice is refused")
    void msLevel() throws IOException {
        String arrays = doubles(MZ_ARRAY, false, 1, 2) + goodIntensity();
        assertAll(
                () ->
                        assertEquals(
                                prefix() + "states no ms level (MS:1000511)",
                                failure(spectrum(0, ID, 2, "", "", "", arrays))),
                () ->
                        assertEquals(
                                prefix() + "states its ms level (MS:1000511) more than once",
                                failure(spectrum(0, ID, 2, level(2) + level(2), "", "", arrays))));
    }

    @Test
    @DisplayName("two selected ions, a missing or repeated array, and unpaired arrays are refused")
    void structure() throws IOException {
        String arrays = doubles(MZ_ARRAY, false, 1, 2) + goodIntensity();
        assertAll(
                () ->
                        assertEquals(
                                prefix()
                                        + "has 2 selected ions, and an MGF entry can carry only one"
                                        + " precursor",
                                failure(
                                        spectrum(
                                                0,
                                                ID,
                                                2,
                                                level(2),
                                                "",
                                                selectedIon("1", "2") + selectedIon("3", "2"),
                                                arrays))),
                () ->
                        assertEquals(
                                prefix() + "has no m/z array (MS:1000514)",
                                failure(ms2With(goodIntensity()))),
                () ->
                        assertEquals(
                                prefix() + "has no intensity array (MS:1000515)",
                                failure(ms2With(doubles(MZ_ARRAY, false, 1, 2)))),
                () ->
                        assertEquals(
                                prefix() + "has more than one intensity array",
                                failure(ms2With(arrays + goodIntensity()))),
                () ->
                        assertEquals(
                                prefix()
                                        + "has 2 m/z values and 1 intensities, so its peaks cannot"
                                        + " be paired",
                                failure(
                                        ms2With(
                                                doubles(MZ_ARRAY, false, 1, 2)
                                                        + array(
                                                                List.of(
                                                                        cv(FLOAT64),
                                                                        cv(NO_COMPRESSION),
                                                                        cv(INTENSITY_ARRAY)),
                                                                base64(false, encode64(5)),
                                                                "1")))));
    }

    @Test
    @DisplayName("a charge that is not an integer is refused, quoting it")
    void charge() throws IOException {
        assertEquals(
                prefix() + "states the charge state \"2+\", which is not an integer",
                failure(
                        spectrum(
                                0,
                                ID,
                                2,
                                level(2),
                                "",
                                selectedIon("500", "2+"),
                                doubles(MZ_ARRAY, false, 1, 2) + goodIntensity())));
    }

    @Test
    @DisplayName("a retention time in another unit, stated twice, or not a number is refused")
    void retentionTimes() throws IOException {
        String arrays = doubles(MZ_ARRAY, false, 1, 2) + goodIntensity();
        assertAll(
                () ->
                        assertEquals(
                                prefix()
                                        + "states its scan start time in the unit \"UO:0000028\""
                                        + " (\"millisecond\"), which is neither seconds"
                                        + " (UO:0000010) nor minutes (UO:0000031)",
                                failure(
                                        spectrum(
                                                0,
                                                ID,
                                                2,
                                                level(2),
                                                cvUnit(
                                                        MzMlSpectrumReader.SCAN_START_TIME,
                                                        "5",
                                                        "UO:0000028",
                                                        "millisecond"),
                                                "",
                                                arrays))),
                () ->
                        assertEquals(
                                prefix()
                                        + "states its scan start time in the unit \"null\""
                                        + " (\"null\"), which is neither seconds (UO:0000010) nor"
                                        + " minutes (UO:0000031)",
                                failure(
                                        spectrum(
                                                0,
                                                ID,
                                                2,
                                                level(2),
                                                cvUnit(
                                                        MzMlSpectrumReader.SCAN_START_TIME,
                                                        "5",
                                                        null,
                                                        null),
                                                "",
                                                arrays))),
                () ->
                        assertEquals(
                                prefix()
                                        + "states more than one scan start time (MS:1000016), so"
                                        + " it has no single retention time",
                                failure(
                                        spectrum(
                                                0,
                                                ID,
                                                2,
                                                level(2),
                                                minutes("1") + seconds("2"),
                                                "",
                                                arrays))),
                () ->
                        assertEquals(
                                prefix()
                                        + "has a scan start time that a retention time must be a"
                                        + " decimal number, but was \"soon\"",
                                failure(
                                        spectrum(
                                                0, ID, 2, level(2), minutes("soon"), "", arrays))));
    }

    @Test
    @DisplayName("a spectrum without an id or a valid index is refused, naming its position")
    void identity() throws IOException {
        String arrays = doubles(MZ_ARRAY, false, 1, 2) + goodIntensity();
        Path file = directory.resolve("one.mzML");
        assertAll(
                () ->
                        assertEquals(
                                file + ": the spectrum at position 1 has no id attribute",
                                failure(spectrum(0, "", 2, level(2), "", "", arrays))),
                () ->
                        assertEquals(
                                file
                                        + ": spectrum \""
                                        + ID
                                        + "\" (position 1) has the index attribute \"-1\", which"
                                        + " is not a non-negative integer",
                                failure(spectrum(-1, ID, 2, level(2), "", "", arrays))),
                () ->
                        assertEquals(
                                file
                                        + ": spectrum \""
                                        + ID
                                        + "\" (position 1) has the index attribute \"null\","
                                        + " which is not a non-negative integer",
                                failure(
                                        spectrum(0, ID, 2, level(2), "", "", arrays)
                                                .replace("index=\"0\" ", ""))));
    }

    @Test
    @DisplayName("a reference to an undefined parameter group inside a spectrum is refused")
    void undefinedGroup() throws IOException {
        assertEquals(
                directory.resolve("one.mzML")
                        + ": a referenceableParamGroupRef names \"nope\", which no"
                        + " referenceableParamGroup defines",
                failure(
                        spectrum(
                                0,
                                ID,
                                2,
                                level(2) + "<referenceableParamGroupRef ref=\"nope\"/>",
                                "",
                                "",
                                "")));
    }

    @Test
    @DisplayName("an undefined group outside any spectrum is ignored, as the reader never needs it")
    void undefinedGroupOutsideSpectra() throws IOException {
        Path file =
                MzMlText.document()
                        .group("g", "<referenceableParamGroupRef ref=\"missing\"/>")
                        .spectrum(ms2With(doubles(MZ_ARRAY, false, 1, 2) + goodIntensity()))
                        .write(directory, "ok.mzML");

        assertEquals(1, read(file).size());
    }

    @Test
    @DisplayName("a document that is not mzML, or not well-formed XML, is refused")
    void notMzMl() throws IOException {
        Path mzxml = MzMlText.document().root("mzXML").write(directory, "a.mzML");
        Path broken = Files.writeString(directory.resolve("b.mzML"), "<mzML><spectrum>");
        Path empty = Files.writeString(directory.resolve("c.mzML"), "");
        assertAll(
                () ->
                        assertEquals(
                                mzxml + ": is not mzML: its first element is <mzXML>",
                                assertThrows(SpectrumFileException.class, () -> read(mzxml))
                                        .getMessage()),
                () ->
                        assertTrue(
                                assertThrows(SpectrumFileException.class, () -> read(broken))
                                        .getMessage()
                                        .startsWith(
                                                broken + ": is not well-formed XML at line 1,")),
                () ->
                        assertTrue(
                                assertThrows(SpectrumFileException.class, () -> read(empty))
                                        .getMessage()
                                        .startsWith(empty + ": is not well-formed XML")));
    }

    @Test
    @DisplayName("a document type is not processed: an entity it declares is never expanded")
    void noDocumentTypeProcessing() throws IOException {
        String text =
                MzMlText.document()
                        .spectrum(ms2With(doubles(MZ_ARRAY, false, 1, 2) + goodIntensity()))
                        .text()
                        .replace(
                                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n",
                                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<!DOCTYPE mzML [<!ENTITY id \""
                                        + ID
                                        + "\">]>\n")
                        .replace("id=\"" + ID + "\"", "id=\"&id;\"");
        Path file = Files.writeString(directory.resolve("dtd.mzML"), text);

        SpectrumFileException thrown = assertThrows(SpectrumFileException.class, () -> read(file));

        assertTrue(
                thrown.getMessage().startsWith(file + ": is not well-formed XML at line "),
                thrown.getMessage());
    }

    @Test
    @DisplayName("both arguments are required")
    void nulls() {
        Path file = directory.resolve("x.mzML");
        assertAll(
                () ->
                        assertEquals(
                                "file",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        MzMlSpectrumReader.read(
                                                                Nulls.of(Path.class), s -> {}))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "visitor",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        MzMlSpectrumReader.read(
                                                                file,
                                                                Nulls.of(SpectrumVisitor.class)))
                                        .getMessage()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"11892", "11367", "11188"})
    @DisplayName("the real K562 _3 subset: index, position and scan differ, values are the text")
    void theRealFixture(String scan) throws IOException {
        List<SourceSpectrum> spectra = k562();
        SourceSpectrum found =
                spectra.stream()
                        .filter(s -> s.nativeId().endsWith("scan=" + scan))
                        .findFirst()
                        .orElseThrow();
        long scanNumber = Long.parseLong(scan);
        assertAll(
                () ->
                        assertEquals(
                                728, spectra.size(), "Comet's pepXML holds 728 spectrum queries"),
                () ->
                        assertTrue(
                                spectra.stream()
                                        .allMatch(s -> s.scanNumber().getAsLong() != s.position()),
                                "position and scan number differ for every spectrum"),
                () -> assertEquals(OptionalLong.of(scanNumber), found.scanNumber()),
                () -> assertEquals(scanNumber - 11187, found.index(), "index 0 is scan 11187"),
                () -> assertEquals(scanNumber - 11186, found.position()),
                () -> assertEquals(PeakEncoding.FLOAT64, found.peaks().mzEncoding()),
                () -> assertEquals(RetentionTime.Unit.MINUTE, found.retentionTime().get().unit()));
        if ("11367".equals(scan)) {
            assertEquals(Optional.of("755.841003417969"), found.precursorMz());
        }
        if ("11188".equals(scan)) {
            assertAll(
                    () -> assertEquals(Optional.of("615.788391113281"), found.precursorMz()),
                    () -> assertEquals(OptionalInt.of(2), found.charge()),
                    () -> assertEquals("79.015933333333", found.retentionTime().get().text()),
                    () -> assertEquals(92, found.peaks().size()));
        }
        if ("11892".equals(scan)) {
            assertEquals(136, found.peaks().size(), "Phase 00's probe: 136 peaks at position 706");
        }
    }

    @Test
    @DisplayName("the fixture's bytes are read as UTF-8 text whatever the platform default")
    void encodingIsTheDocuments() throws IOException {
        Path file =
                Files.write(
                        directory.resolve("latin.mzML"),
                        MzMlText.document()
                                .spectrum(
                                        spectrum(
                                                0,
                                                "café",
                                                1,
                                                level(2),
                                                "",
                                                "",
                                                doubles(MZ_ARRAY, false, 1)
                                                        + doubles(INTENSITY_ARRAY, false, 2)))
                                .text()
                                .getBytes(StandardCharsets.UTF_8));

        assertEquals("café", read(file).get(0).nativeId());
    }
}
