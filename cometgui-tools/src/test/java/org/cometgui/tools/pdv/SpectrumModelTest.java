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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.tools.testing.Nulls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The values the readers produce: retention time, peaks and a source spectrum. */
class SpectrumModelTest {

    private static PeakList peaks() {
        return new PeakList(
                new double[] {1, 2},
                PeakEncoding.FLOAT32,
                new double[] {3, 4},
                PeakEncoding.FLOAT64);
    }

    @Test
    @DisplayName("seconds are the file's text; minutes are converted by exact decimal arithmetic")
    void retentionTime() {
        assertAll(
                () ->
                        assertEquals(
                                "4.7e2",
                                new RetentionTime("4.7e2", RetentionTime.Unit.SECOND)
                                        .secondsText()),
                () ->
                        assertEquals(
                                "4740.95599999998",
                                new RetentionTime("79.015933333333", RetentionTime.Unit.MINUTE)
                                        .secondsText()),
                () ->
                        assertEquals(
                                "90",
                                new RetentionTime("1.5", RetentionTime.Unit.MINUTE).secondsText()),
                () ->
                        assertEquals(
                                "120",
                                new RetentionTime("2", RetentionTime.Unit.MINUTE).secondsText()),
                () ->
                        assertEquals(
                                "600",
                                new RetentionTime("1e1", RetentionTime.Unit.MINUTE).secondsText()),
                () ->
                        assertEquals(
                                "0.006",
                                new RetentionTime("0.0001", RetentionTime.Unit.MINUTE)
                                        .secondsText()));
    }

    @Test
    @DisplayName("a retention time must be a decimal number, and both components are required")
    void retentionTimeRefusals() {
        IllegalArgumentException thrown =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new RetentionTime("12:30", RetentionTime.Unit.MINUTE));
        assertAll(
                () ->
                        assertEquals(
                                "a retention time must be a decimal number, but was \"12:30\"",
                                thrown.getMessage()),
                () -> assertInstanceOf(NumberFormatException.class, thrown.getCause()),
                () ->
                        assertEquals(
                                "text",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new RetentionTime(
                                                                Nulls.of(String.class),
                                                                RetentionTime.Unit.SECOND))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "unit",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new RetentionTime(
                                                                "1",
                                                                Nulls.of(RetentionTime.Unit.class)))
                                        .getMessage()));
    }

    @Test
    @DisplayName("a peak list copies its arrays in and out, and compares by value")
    void peakList() {
        double[] mz = {1, 2};
        PeakList list =
                new PeakList(mz, PeakEncoding.FLOAT32, new double[] {3, 4}, PeakEncoding.FLOAT64);
        mz[0] = 99;
        list.mz()[1] = 99;
        list.intensity()[0] = 99;
        assertAll(
                () -> assertArrayEquals(new double[] {1, 2}, list.mz()),
                () -> assertArrayEquals(new double[] {3, 4}, list.intensity()),
                () -> assertEquals(2, list.size()),
                () -> assertEquals(PeakEncoding.FLOAT32, list.mzEncoding()),
                () -> assertEquals(PeakEncoding.FLOAT64, list.intensityEncoding()),
                () -> assertEquals(peaks(), list),
                () -> assertEquals(peaks().hashCode(), list.hashCode()),
                () ->
                        assertNotEquals(
                                new PeakList(
                                                new double[] {7},
                                                PeakEncoding.FLOAT64,
                                                new double[] {8},
                                                PeakEncoding.FLOAT64)
                                        .hashCode(),
                                list.hashCode()),
                () ->
                        assertNotEquals(
                                new PeakList(
                                        new double[] {1, 2},
                                        PeakEncoding.FLOAT64,
                                        new double[] {3, 4},
                                        PeakEncoding.FLOAT64),
                                list),
                () ->
                        assertNotEquals(
                                new PeakList(
                                        new double[] {1, 2},
                                        PeakEncoding.FLOAT32,
                                        new double[] {3, 4},
                                        PeakEncoding.FLOAT32),
                                list),
                () ->
                        assertNotEquals(
                                new PeakList(
                                        new double[] {1, 5},
                                        PeakEncoding.FLOAT32,
                                        new double[] {3, 4},
                                        PeakEncoding.FLOAT64),
                                list),
                () ->
                        assertNotEquals(
                                new PeakList(
                                        new double[] {1, 2},
                                        PeakEncoding.FLOAT32,
                                        new double[] {3, 5},
                                        PeakEncoding.FLOAT64),
                                list),
                () -> assertNotEquals(list, "not a peak list"),
                () ->
                        assertEquals(
                                "PeakList[2 peaks, m/z FLOAT32, intensity FLOAT64]",
                                list.toString()));
    }

    @Test
    @DisplayName("a peak list needs one intensity per m/z value, and every argument")
    void peakListRefusals() {
        assertAll(
                () ->
                        assertEquals(
                                "a peak list needs one intensity per m/z "
                                        + "value, but has 2 m/z values and 1 intensities",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () ->
                                                        new PeakList(
                                                                new double[2],
                                                                PeakEncoding.FLOAT64,
                                                                new double[1],
                                                                PeakEncoding.FLOAT64))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "mz",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new PeakList(
                                                                Nulls.of(double[].class),
                                                                PeakEncoding.FLOAT64,
                                                                new double[0],
                                                                PeakEncoding.FLOAT64))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "intensity",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new PeakList(
                                                                new double[0],
                                                                PeakEncoding.FLOAT64,
                                                                Nulls.of(double[].class),
                                                                PeakEncoding.FLOAT64))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "mzEncoding",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new PeakList(
                                                                new double[0],
                                                                Nulls.of(PeakEncoding.class),
                                                                new double[0],
                                                                PeakEncoding.FLOAT64))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "intensityEncoding",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new PeakList(
                                                                new double[0],
                                                                PeakEncoding.FLOAT64,
                                                                new double[0],
                                                                Nulls.of(PeakEncoding.class)))
                                        .getMessage()));
    }

    @Test
    @DisplayName("a scan number is read only from a whole scan=<n> token of the native id")
    void scanNumbers() {
        assertAll(
                () ->
                        assertEquals(
                                OptionalLong.of(11367),
                                SourceSpectrum.scanNumberOf(
                                        "controllerType=0 controllerNumber=1 scan=11367")),
                () -> assertEquals(OptionalLong.of(5), SourceSpectrum.scanNumberOf("scan=5")),
                () ->
                        assertEquals(
                                OptionalLong.of(5), SourceSpectrum.scanNumberOf("scan=5 frame=2")),
                () -> assertEquals(OptionalLong.of(5), SourceSpectrum.scanNumberOf("a\tscan=5")),
                () -> assertEquals(OptionalLong.empty(), SourceSpectrum.scanNumberOf("xscan=5")),
                () -> assertEquals(OptionalLong.empty(), SourceSpectrum.scanNumberOf("scan=5x")),
                () -> assertEquals(OptionalLong.empty(), SourceSpectrum.scanNumberOf("scan=")),
                () -> assertEquals(OptionalLong.empty(), SourceSpectrum.scanNumberOf("index=5")),
                () ->
                        assertEquals(
                                OptionalLong.of(123456789012345678L),
                                SourceSpectrum.scanNumberOf("scan=123456789012345678")),
                () ->
                        assertEquals(
                                OptionalLong.empty(),
                                SourceSpectrum.scanNumberOf("scan=1234567890123456789")),
                () ->
                        assertEquals(
                                "nativeId",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        SourceSpectrum.scanNumberOf(
                                                                Nulls.of(String.class)))
                                        .getMessage()));
    }

    @Test
    @DisplayName(
            "a source spectrum needs an id, a non-negative index, a position from 1, every part")
    void sourceSpectrum() {
        PeakList p = peaks();
        Optional<String> mz = Optional.of("1");
        OptionalInt z = OptionalInt.empty();
        Optional<RetentionTime> t = Optional.empty();
        OptionalLong scan = OptionalLong.empty();
        assertAll(
                () -> assertEquals(0, new SourceSpectrum("a", 0, 1, scan, mz, z, t, p).index()),
                () ->
                        assertEquals(
                                "a spectrum's native id must not be empty",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () ->
                                                        new SourceSpectrum(
                                                                "", 0, 1, scan, mz, z, t, p))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "a spectrum's index must not be negative: -1",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () ->
                                                        new SourceSpectrum(
                                                                "a", -1, 1, scan, mz, z, t, p))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "a spectrum's 1-based position must be at least 1: 0",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () ->
                                                        new SourceSpectrum(
                                                                "a", 0, 0, scan, mz, z, t, p))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "nativeId",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new SourceSpectrum(
                                                                Nulls.of(String.class),
                                                                0,
                                                                1,
                                                                scan,
                                                                mz,
                                                                z,
                                                                t,
                                                                p))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "scanNumber",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new SourceSpectrum(
                                                                "a",
                                                                0,
                                                                1,
                                                                Nulls.of(OptionalLong.class),
                                                                mz,
                                                                z,
                                                                t,
                                                                p))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "precursorMz",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new SourceSpectrum(
                                                                "a",
                                                                0,
                                                                1,
                                                                scan,
                                                                Nulls.of(Optional.class),
                                                                z,
                                                                t,
                                                                p))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "charge",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new SourceSpectrum(
                                                                "a",
                                                                0,
                                                                1,
                                                                scan,
                                                                mz,
                                                                Nulls.of(OptionalInt.class),
                                                                t,
                                                                p))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "retentionTime",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new SourceSpectrum(
                                                                "a",
                                                                0,
                                                                1,
                                                                scan,
                                                                mz,
                                                                z,
                                                                Nulls.of(Optional.class),
                                                                p))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "peaks",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new SourceSpectrum(
                                                                "a",
                                                                0,
                                                                1,
                                                                scan,
                                                                mz,
                                                                z,
                                                                t,
                                                                Nulls.of(PeakList.class)))
                                        .getMessage()));
    }

    @Test
    @DisplayName("the errors carry their file and require their parts")
    void errors() {
        Path file = Path.of("x.mzML").toAbsolutePath();
        ToolVersion v = ToolVersion.parse("2.7.0");
        assertAll(
                () ->
                        assertEquals(
                                file + ": broken",
                                new SpectrumFileException(file, "broken").getMessage()),
                () -> assertEquals(file, new SpectrumFileException(file, "broken").file()),
                () ->
                        assertEquals(
                                file + ": spectrum \"s\" (index 4): odd",
                                new SpectrumFileException(file, "s", 4, "odd").getMessage()),
                () ->
                        assertEquals(
                                "file",
                                assertThrows(
                                                NullPointerException.class,
                                                () -> {
                                                    throw new SpectrumFileException(
                                                            Nulls.of(Path.class), "x");
                                                })
                                        .getMessage()),
                () ->
                        assertEquals(
                                file + " is odd",
                                new InvalidFigureException(file, "is odd").getMessage()),
                () ->
                        assertEquals(
                                "file",
                                assertThrows(
                                                NullPointerException.class,
                                                () -> {
                                                    throw new InvalidFigureException(
                                                            Nulls.of(Path.class), "x");
                                                })
                                        .getMessage()),
                () ->
                        assertEquals(
                                "file",
                                assertThrows(
                                                NullPointerException.class,
                                                () -> {
                                                    throw new UnsupportedSpectrumFileException(
                                                            Nulls.of(Path.class), null, v, "m");
                                                })
                                        .getMessage()),
                () ->
                        assertEquals(
                                "pdvVersion",
                                assertThrows(
                                                NullPointerException.class,
                                                () -> {
                                                    throw new UnsupportedSpectrumFileException(
                                                            file,
                                                            null,
                                                            Nulls.of(ToolVersion.class),
                                                            "m");
                                                })
                                        .getMessage()),
                () ->
                        assertEquals(
                                "message",
                                assertThrows(
                                                NullPointerException.class,
                                                () -> {
                                                    throw new UnsupportedSpectrumFileException(
                                                            file, null, v, Nulls.of(String.class));
                                                })
                                        .getMessage()),
                () ->
                        assertEquals(
                                "failure",
                                assertThrows(
                                                NullPointerException.class,
                                                () -> {
                                                    throw new PdvFigureException(
                                                            Nulls.of(
                                                                    PdvFigureException.Failure
                                                                            .class),
                                                            "m",
                                                            null);
                                                })
                                        .getMessage()));
    }
}
