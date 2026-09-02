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

package org.cometgui.install.probe;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.install.testing.Nulls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for {@link VersionBanner}, against the <strong>verbatim</strong> banner lines the real
 * binaries printed on this project's host on 2026-09-02.
 */
class VersionBannerTest {

    @ParameterizedTest(name = "[{index}] {0} -> {1}")
    @CsvSource({
        "'Percolator version 3.07.1, Build Date Jun 20 2024 13:20:18', 3.07.1",
        "'Percolator version 3.06.5, Build Date Feb  8 2024 10:00:35', 3.06.5",
        "'Percolator version 3.09, Build Date May 21 2026 09:00:00', 3.09",
    })
    @DisplayName("Percolator's real banner lines parse to the version upstream published")
    void percolatorBanners(String line, String expected) {
        ToolVersion version = VersionBanner.percolator().readFrom(List.of(line)).orElseThrow();

        assertAll(
                () -> assertEquals(expected, version.text()),
                () -> assertEquals(ToolVersion.parse(expected), version));
    }

    @Test
    @DisplayName("Comet's real banner reads as the manifest's 2026.02.2, from three separate parts")
    void cometBanner() {
        ToolVersion version =
                VersionBanner.comet()
                        .readFrom(List.of(" Comet version 2026.02 rev. 2 (6edec91)"))
                        .orElseThrow();

        assertAll(
                () ->
                        assertEquals(
                                "2026.02.2",
                                version.text(),
                                "the binary says \"2026.02 rev. 2\" and the manifest says"
                                        + " 2026.02.2; the three captured numbers are the bridge"),
                () -> assertEquals(ToolVersion.parse("2026.02.2"), version));
    }

    @Test
    @DisplayName(
            "Comet's other banner form, printed on standard output with no arguments, reads too")
    void cometQuotedBanner() {
        assertEquals(
                "2026.02.2",
                VersionBanner.comet()
                        .readFrom(List.of(" Comet version \"2026.02 rev. 2 (6edec91)\""))
                        .orElseThrow()
                        .text());
    }

    @Test
    @DisplayName("the first matching line wins, and lines before it are skipped")
    void theFirstMatchingLineWins() {
        assertEquals(
                "3.07.1",
                VersionBanner.percolator()
                        .readFrom(
                                List.of(
                                        "some preamble",
                                        "Percolator version 3.07.1, Build Date x",
                                        "Percolator version 3.09, Build Date y"))
                        .orElseThrow()
                        .text());
    }

    @Test
    @DisplayName("output with no banner in it is empty, not a guess")
    void noBannerIsEmpty() {
        assertAll(
                () ->
                        assertEquals(
                                Optional.empty(), VersionBanner.percolator().readFrom(List.of())),
                () ->
                        assertEquals(
                                Optional.empty(),
                                VersionBanner.percolator()
                                        .readFrom(
                                                List.of(
                                                        "Error: too few arguments.",
                                                        "Invoke with -h option for help"))),
                () ->
                        assertEquals(
                                Optional.empty(),
                                VersionBanner.percolator()
                                        .readFrom(List.of("Percolator version threeish,")),
                                "a banner shape that is not numbers is not a version"),
                () ->
                        assertEquals(
                                Optional.empty(),
                                VersionBanner.comet().readFrom(List.of(" Comet version 2026.02"))));
    }

    @Test
    @DisplayName("a banner whose captured parts are not a version this product accepts is empty")
    void anUnacceptableVersionIsEmpty() {
        VersionBanner oneComponent =
                new VersionBanner(
                        List.of("--version"),
                        Pattern.compile("Thing version (\\d+)"),
                        "a fixture, not a tool this project has run");

        assertEquals(
                Optional.empty(),
                oneComponent.readFrom(List.of("Thing version 3")),
                "a bare 3 is not two to four numeric components, and a probe that accepted it"
                        + " would record a version nobody can look up");
    }

    @Test
    @DisplayName("both shipped banners carry the run they were taken from")
    void everyBannerCarriesItsEvidence() {
        Map<ToolName, VersionBanner> banners = VersionBanner.observedOnThisProject();

        assertAll(
                () ->
                        assertEquals(
                                java.util.Set.of(ToolName.COMET, ToolName.PERCOLATOR),
                                banners.keySet(),
                                "PDV and the Limelight converter are Java artefacts whose identity"
                                        + " needs a JVM launch; a banner nobody has watched print"
                                        + " would be a rule that has never seen its subject"),
                () ->
                        assertTrue(
                                banners.values().stream()
                                        .allMatch(
                                                banner ->
                                                        banner.evidence()
                                                                .contains(
                                                                        "executed on this"
                                                                                + " project's "
                                                                                + "Debian 12"
                                                                                + " host")),
                                banners.toString()),
                () -> assertEquals(List.of("-h"), banners.get(ToolName.COMET).arguments()),
                () -> assertEquals(List.of("--help"), banners.get(ToolName.PERCOLATOR).arguments()),
                () ->
                        assertThrows(
                                UnsupportedOperationException.class,
                                () -> banners.put(ToolName.PDV, VersionBanner.comet())));
    }

    @Test
    @DisplayName("a pattern that captures nothing is rejected, quoting it")
    void aPatternThatCapturesNothingIsRejected() {
        assertEquals(
                "a version banner pattern must capture the version's components, but \"version"
                        + " \\d+\" captures nothing",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        new VersionBanner(
                                                List.of(),
                                                Pattern.compile("version \\d+"),
                                                "a fixture"))
                        .getMessage());
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @ValueSource(strings = {"", " ", "\t"})
    @DisplayName("a banner with no evidence sentence is rejected")
    void blankEvidenceIsRejected(String blank) {
        assertEquals(
                "evidence must say which run this banner was taken from; a banner nobody has seen"
                        + " printed is a rule that has never seen its subject",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        new VersionBanner(
                                                List.of(), Pattern.compile("(\\d+)"), blank))
                        .getMessage());
    }

    @Test
    @DisplayName("a banner copies its argument list and rejects a null part by name")
    void argumentsAreCopiedAndNullsRejected() {
        List<String> arguments = new java.util.ArrayList<>(List.of("--help"));
        VersionBanner banner =
                new VersionBanner(arguments, Pattern.compile("(\\d+)\\.(\\d+)"), "a fixture");
        arguments.add("--verbose");

        assertAll(
                () -> assertEquals(List.of("--help"), banner.arguments()),
                () ->
                        assertThrows(
                                UnsupportedOperationException.class,
                                () -> banner.arguments().add("x")),
                () ->
                        assertEquals(
                                "arguments",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new VersionBanner(
                                                                Nulls.of(List.class),
                                                                Pattern.compile("(\\d+)"),
                                                                "a fixture"))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "pattern",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new VersionBanner(
                                                                List.of(),
                                                                Nulls.of(Pattern.class),
                                                                "a fixture"))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "evidence",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new VersionBanner(
                                                                List.of(),
                                                                Pattern.compile("(\\d+)"),
                                                                Nulls.of(String.class)))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "lines",
                                assertThrows(
                                                NullPointerException.class,
                                                () -> banner.readFrom(Nulls.of(List.class)))
                                        .getMessage()));
    }
}
