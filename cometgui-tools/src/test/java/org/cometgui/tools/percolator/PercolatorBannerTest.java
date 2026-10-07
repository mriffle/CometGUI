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

package org.cometgui.tools.percolator;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.cometgui.domain.tools.ToolVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Reading Percolator's banner, against the lines the real binaries print.
 *
 * <p>The two version lines here are hand-typed from runs on this project's Debian 12 host, and they
 * are the same two strings {@code org.cometgui.install.probe.VersionBanner.percolator()}'s evidence
 * sentence quotes. That is deliberate: the two readings live in modules that cannot see each other,
 * and this test is what would notice if they stopped agreeing about what a Percolator banner looks
 * like.
 */
class PercolatorBannerTest {

    /** Observed 2026-09-03 on standard error, from the 3.07.1 portable binary. */
    private static final String BANNER_3071 =
            "Percolator version 3.07.1, Build Date Jun 20 2024 13:20:18";

    /** Observed 2026-09-03 on standard error, from the 3.06.5 portable binary. */
    private static final String BANNER_3065 =
            "Percolator version 3.06.5, Build Date Feb  8 2024 10:00:35";

    @Test
    @DisplayName("the two banners the real binaries print are read as their releases")
    void theRealBanners() {
        assertAll(
                () ->
                        assertEquals(
                                Optional.of(ToolVersion.parse("3.07.1")),
                                PercolatorBanner.readFrom(List.of(BANNER_3071))),
                () ->
                        assertEquals(
                                Optional.of(ToolVersion.parse("3.06.5")),
                                PercolatorBanner.readFrom(List.of(BANNER_3065))),
                () -> assertTrue(PercolatorBanner.isPresentIn(List.of(BANNER_3071))),
                () -> assertTrue(PercolatorBanner.isPresentIn(List.of(BANNER_3065))));
    }

    @Test
    @DisplayName("the banner is found wherever in the output it is, and the first one wins")
    void theBannerIsFoundAnywhere() {
        List<String> output =
                List.of(
                        "Copyright (c) 2006-9 University of Washington. All rights reserved.",
                        BANNER_3071,
                        "Percolator version 9.99.9, Build Date Jan  1 2099 00:00:00");

        assertAll(
                () ->
                        assertEquals(
                                Optional.of(ToolVersion.parse("3.07.1")),
                                PercolatorBanner.readFrom(output)),
                () -> assertTrue(PercolatorBanner.isPresentIn(output)));
    }

    @ParameterizedTest(name = "[{index}] \"{0}\" -> {1}")
    @CsvSource({
        "'Percolator version 3.09, Build Date Jan  1 2026 00:00:00', 3.09",
        "'Percolator version 3.5.1, Build Date x', 3.5.1",
        "'  Percolator version 3.07.1, x', 3.07.1"
    })
    @DisplayName("the version is what the line says, whatever surrounds it")
    void versionsThatParse(String line, String expected) {
        assertEquals(
                Optional.of(ToolVersion.parse(expected)), PercolatorBanner.readFrom(List.of(line)));
    }

    @ParameterizedTest(name = "[{index}] \"{0}\" -> {1} {2}")
    @CsvSource(
            delimiter = '|',
            value = {
                "Percolator version 3.05, Build Date Jan  1 2021 00:00:00 | 3.05 | 3 5",
                "Percolator version 3.05.0, Build Date Jan  1 2021 00:00:00 | 3.05.0 | 3 5",
                "Percolator version 3.06.5, Build Date Feb  8 2024 10:00:35 | 3.06.5 | 3 6 5",
                "Percolator version 3.07.1, Build Date Jun 20 2024 13:20:18 | 3.07.1 | 3 7 1",
                "Percolator version 3.08.0, Build Date Mar  1 2025 00:00:00 | 3.08.0 | 3 8",
                "Percolator version 3.08.1, Build Date Jul  8 2025 00:00:00 | 3.08.1 | 3 8 1",
                "Percolator version 3.09.0, Build Date May 21 2026 17:16:38 | 3.09.0 | 3 9",
                "Exception caught: Percolator version 3.09.0, Build Date May 21 2026 17:16:38"
                        + " | 3.09.0 | 3 9",
                "Percolator version 3.09.1, Build Date Jan  1 2027 00:00:00 | 3.09.1 | 3 9 1",
                "Percolator version 3.10, Build Date Jan  1 2027 00:00:00 | 3.10 | 3 10",
                "Percolator version 3.10.0, Build Date Jan  1 2027 00:00:00 | 3.10.0 | 3 10",
                "Percolator version 4.0, Build Date Jan  1 2030 00:00:00 | 4.0 | 4 0",
                "Percolator version 4.0.2, Build Date Jan  1 2030 00:00:00 | 4.0.2 | 4 0 2"
            })
    @DisplayName("every release line from 3.05 to 3.09, and the ones that do not exist yet")
    void releasesFrom305ToTheFuture(String line, String text, String components) {
        ToolVersion read = PercolatorBanner.readFrom(List.of(line)).orElseThrow();

        assertAll(
                () -> assertEquals(text, read.text(), "the version as upstream spelled it"),
                () ->
                        assertEquals(
                                Arrays.stream(components.split(" ")).map(Integer::valueOf).toList(),
                                read.components(),
                                "and its numeric components, trailing zeros dropped"),
                () -> assertTrue(PercolatorBanner.isPresentIn(List.of(line))));
    }

    @Test
    @DisplayName("future releases order above 3.09 numerically, and are above the 3.05 floor")
    void futureReleasesOrderNumerically() {
        ToolVersion v309 = read("Percolator version 3.09.0, Build Date x");
        ToolVersion v3091 = read("Percolator version 3.09.1, Build Date x");
        ToolVersion v310 = read("Percolator version 3.10.0, Build Date x");
        ToolVersion v40 = read("Percolator version 4.0, Build Date x");

        assertAll(
                () -> assertTrue(v3091.compareTo(v309) > 0, "3.09.1 after 3.09"),
                () ->
                        assertTrue(
                                v310.compareTo(v3091) > 0,
                                "3.10 after 3.09.1, which a comparison of text gets wrong"),
                () -> assertTrue(v40.compareTo(v310) > 0, "4.0 after 3.10"),
                () -> assertEquals(ToolVersion.parse("3.09"), v309, "3.09.0 IS 3.09"),
                () -> assertEquals(ToolVersion.parse("3.10"), v310, "and 3.10.0 is 3.10"),
                () ->
                        assertTrue(
                                v310.isAtLeast(LocalPercolatorRegistration.MINIMUM_VERSION)
                                        && v40.isAtLeast(
                                                LocalPercolatorRegistration.MINIMUM_VERSION),
                                "a future release clears the registration floor with no code"
                                        + " change"));
    }

    private static ToolVersion read(String line) {
        return PercolatorBanner.readFrom(List.of(line)).orElseThrow();
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @ValueSource(
            strings = {
                "Percolator version 3.07.1 Build Date Jun 20 2024",
                "Percolator version 3, Build Date x",
                "Percolator version, Build Date x",
                "percolator version 3.07.1, Build Date x",
                "Percolator 3.07.1, Build Date x",
                "Exception caught: Error: median decoy score <= score at 1% FDR.",
                "percolator: error while loading shared libraries:"
                        + " libboost_filesystem.so.1.83.0: cannot open shared object file",
                ""
            })
    @DisplayName("anything that is not the banner is not a version, and is not evidence it ran")
    void linesThatAreNotTheBanner(String line) {
        assertAll(
                () -> assertEquals(Optional.empty(), PercolatorBanner.readFrom(List.of(line))),
                () -> assertFalse(PercolatorBanner.isPresentIn(List.of(line))));
    }

    @Test
    @DisplayName("the trailing comma matters: a truncated line is not a version")
    void theTrailingCommaMatters() {
        assertAll(
                () ->
                        assertEquals(
                                Optional.empty(),
                                PercolatorBanner.readFrom(List.of("Percolator version 3.07."))),
                () ->
                        assertEquals(
                                Optional.empty(),
                                PercolatorBanner.readFrom(List.of("Percolator version 3.07.1"))),
                () ->
                        assertEquals(
                                Optional.of(ToolVersion.parse("3.07")),
                                PercolatorBanner.readFrom(List.of("Percolator version 3.07, x")),
                                "a two-component release really is a version, so the comma is what"
                                        + " separates \"the whole number\" from \"as much of it"
                                        + " as reached the buffer\""));
    }

    @Test
    @DisplayName("a banner with five components is refused rather than half-read")
    void tooManyComponents() {
        assertEquals(
                Optional.empty(),
                PercolatorBanner.readFrom(List.of("Percolator version 3.07.1.2.3, Build Date x")),
                "3.07.1.2 would be a version this product accepts, and reading one out of a line"
                        + " that says something else is how a fact nobody can look up reaches a"
                        + " provenance record");
    }

    @Test
    @DisplayName("a null line in the output is skipped rather than thrown over")
    void nullLinesAreSkipped() {
        List<String> output = Arrays.asList(null, BANNER_3071, null);

        assertAll(
                () ->
                        assertEquals(
                                Optional.of(ToolVersion.parse("3.07.1")),
                                PercolatorBanner.readFrom(output)),
                () -> assertTrue(PercolatorBanner.isPresentIn(output)));
    }

    @Test
    @DisplayName("no lines at all is no version and no evidence")
    void noLines() {
        assertAll(
                () -> assertEquals(Optional.empty(), PercolatorBanner.readFrom(List.of())),
                () -> assertFalse(PercolatorBanner.isPresentIn(List.of())),
                () ->
                        assertEquals(
                                "lines",
                                assertThrows(
                                                NullPointerException.class,
                                                () -> PercolatorBanner.readFrom(null))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "lines",
                                assertThrows(
                                                NullPointerException.class,
                                                () -> PercolatorBanner.isPresentIn(null))
                                        .getMessage()));
    }

    @Test
    @DisplayName("the version query is --help, which is what prints the banner")
    void theVersionArguments() {
        assertEquals(List.of("--help"), PercolatorBanner.VERSION_ARGUMENTS);
    }
}
