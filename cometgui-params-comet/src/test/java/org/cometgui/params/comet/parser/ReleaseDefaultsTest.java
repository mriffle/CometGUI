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

package org.cometgui.params.comet.parser;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.fixtures.CometFixtures;
import org.cometgui.params.comet.fixtures.ParamsFiles;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.model.ParameterEntry;
import org.cometgui.params.comet.model.ValueOrigin;
import org.cometgui.params.comet.schema.CuratedMetadata;
import org.cometgui.params.comet.schema.MetadataLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The starting set of each offered release is Comet's own {@code -q} output, bundled with the
 * module: the bytes are the checked-in fixture's (SHA-256 typed in from its {@code SHA256SUMS}),
 * the parse is clean, and every value is the fixture model's -- but for {@code
 * spectral_library_name}, which a new configuration starts empty by decision D-012, shown as
 * CometGUI's default and never Comet's.
 */
class ReleaseDefaultsTest {

    /** The parameter D-012 starts empty. */
    private static final String LIBRARY = "spectral_library_name";

    /** What both releases' {@code comet -q} writes for it, typed in from the fixtures. */
    private static final String PLACEHOLDER = "/some/path/speclib.file";

    private static final CuratedMetadata METADATA = ParamsFiles.metadata();

    private static final ToolVersion C202603 = ToolVersion.parse("2026.03.0");

    private static final ToolVersion C202602 = ToolVersion.parse("2026.02.2");

    private static String sha256(byte[] bytes) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    @Test
    @DisplayName("the bundled releases are the two the editor offers, newest first")
    void bundledReleases() {
        assertEquals(List.of(C202603, C202602), ReleaseDefaults.bundledReleases());
        assertTrue(ReleaseDefaults.isBundled(ToolVersion.parse("2026.03.0")));
        assertTrue(ReleaseDefaults.isBundled(ToolVersion.parse("2026.02.2")));
        assertFalse(ReleaseDefaults.isBundled(ToolVersion.parse("2024.01.0")));
    }

    @ParameterizedTest(name = "Comet {0}: the bundled file is the fixture, byte for byte")
    @CsvSource({
        // typed in from fixtures/comet/<version>/linux-x86-64/SHA256SUMS
        "2026.03.0, 4bbf39f943f011f16ea6f2380f396f05a06b85579496ece7d5265d3d7bac7aeb, 12551",
        "2026.02.2, d15048709f485c09a840dcb2a384dc301b4ba4da4566c2ea053e9c17eae58f51, 11844"
    })
    void bundledBytesAreTheFixture(String version, String sha256, int size)
            throws IOException, NoSuchAlgorithmException {
        byte[] bundled = ReleaseDefaults.bundledFile(ToolVersion.parse(version));
        assertEquals(size, bundled.length);
        assertEquals(sha256, sha256(bundled));
        assertArrayEquals(
                CometFixtures.bytes(
                        version, CometFixtures.LINUX_X86_64, CometFixtures.Mode.COMPLETE),
                bundled);
    }

    @ParameterizedTest(
            name = "Comet {0}: the starting set is the parsed fixture at Comet's defaults")
    @CsvSource({"2026.03.0", "2026.02.2"})
    void startingSetIsTheFixtureModel(String version) {
        ToolVersion release = ToolVersion.parse(version);
        CometParameters fixture =
                new CometParamsParser(METADATA, release)
                        .parse(ParamsFiles.complete(release))
                        .model()
                        .orElseThrow();

        CometParameters defaults = ReleaseDefaults.load(METADATA, release);

        assertEquals(release, defaults.version());
        assertEquals(List.of(), defaults.diagnostics());
        assertEquals(List.of(), defaults.unknownParameters());
        assertEquals(118, defaults.entries().size());
        assertEquals(fixture.enzymeTable(), defaults.enzymeTable());
        for (ParameterEntry entry : defaults.entries()) {
            if (entry.name().equals(LIBRARY)) {
                continue;
            }
            assertEquals(ValueOrigin.COMET_DEFAULT, entry.origin(), entry.name());
            assertEquals(fixture.value(entry.name()), entry.value(), entry.name());
            assertEquals(fixture.text(entry.name()), defaults.text(entry.name()), entry.name());
        }
        // D-012, the one departure: Comet writes a placeholder path, CometGUI starts empty
        assertEquals(PLACEHOLDER, fixture.text(LIBRARY));
        assertEquals("", defaults.text(LIBRARY));
        assertEquals(ValueOrigin.COMETGUI_DEFAULT, defaults.origin(LIBRARY));
        // and the curated defaults agree with Comet's own file, with Comet's own enzyme table
        CometParameters curated =
                CometParameters.defaults(METADATA, release, fixture.enzymeTable());
        assertEquals(PLACEHOLDER, curated.text(LIBRARY));
        assertEquals(ValueOrigin.COMET_DEFAULT, curated.origin(LIBRARY));
        assertEquals(curated.withStartingValues(), defaults);
    }

    @ParameterizedTest(name = "Comet {0}: spectral_library_name is the only starting value")
    @CsvSource({"2026.03.0", "2026.02.2"})
    void theOnlyStartingValue(String version) {
        ToolVersion release = ToolVersion.parse(version);
        CometParameters defaults = ReleaseDefaults.load(METADATA, release);
        List<String> departures = new ArrayList<>();
        for (ParameterEntry entry : defaults.entries()) {
            if (entry.origin() != ValueOrigin.COMET_DEFAULT) {
                departures.add(entry.name() + "=" + defaults.text(entry.name()));
            }
            assertEquals(
                    entry.origin() == ValueOrigin.COMETGUI_DEFAULT,
                    METADATA.startingValue(entry.name(), release).isPresent(),
                    entry.name());
        }
        assertEquals(List.of(LIBRARY + "="), departures);
        assertEquals(Optional.of(""), METADATA.startingValue(LIBRARY, release));
        assertEquals(
                Optional.of("D-012"),
                METADATA.version(release).orElseThrow().override(LIBRARY).orElseThrow().decision());
    }

    @ParameterizedTest(name = "Comet {0}: a file read in keeps the library it names")
    @CsvSource({"2026.03.0", "2026.02.2"})
    void anImportedFileKeepsItsLibrary(String version) {
        ToolVersion release = ToolVersion.parse(version);
        // Comet's own -q file, read as a file the scientist imports: its placeholder is kept
        CometParameters comets =
                new CometParamsParser(METADATA, release)
                        .parse(
                                new String(
                                        ReleaseDefaults.bundledFile(release),
                                        StandardCharsets.UTF_8))
                        .model()
                        .orElseThrow();
        assertEquals(PLACEHOLDER, comets.text(LIBRARY));
        assertEquals(ValueOrigin.IMPORTED, comets.origin(LIBRARY));
        // and a file naming a real library keeps that library
        String named =
                new String(ReleaseDefaults.bundledFile(release), StandardCharsets.UTF_8)
                        .replace(
                                "spectral_library_name = " + PLACEHOLDER,
                                "spectral_library_name = /data/human.msp");
        CometParameters imported =
                new CometParamsParser(METADATA, release).parse(named).model().orElseThrow();
        assertEquals("/data/human.msp", imported.text(LIBRARY));
        assertEquals(ValueOrigin.IMPORTED, imported.origin(LIBRARY));
    }

    @ParameterizedTest(
            name = "Comet {0}: a file without the line has no library, as Comet reads it")
    @CsvSource({"2026.03.0", "2026.02.2"})
    void aFileWithoutTheLineHasNoLibrary(String version) {
        ToolVersion release = ToolVersion.parse(version);
        String without =
                new String(ReleaseDefaults.bundledFile(release), StandardCharsets.UTF_8)
                        .replace("spectral_library_name = " + PLACEHOLDER + "\n", "");
        assertFalse(without.contains("spectral_library_name"), "the line is removed");
        CometParameters imported =
                new CometParamsParser(METADATA, release).parse(without).model().orElseThrow();
        assertEquals("", imported.text(LIBRARY));
        assertEquals(ValueOrigin.COMETGUI_DEFAULT, imported.origin(LIBRARY));
        assertEquals(ValueOrigin.IMPORTED, imported.origin("num_threads"));
    }

    @ParameterizedTest(name = "Comet {0}: reset puts the library back to CometGUI's empty start")
    @CsvSource({"2026.03.0", "2026.02.2"})
    void resetAgreesWithTheStartingSet(String version) {
        ToolVersion release = ToolVersion.parse(version);
        CometParameters start = ReleaseDefaults.load(METADATA, release);
        CometParameters named = start.withText(LIBRARY, "/data/human.msp", ValueOrigin.USER);
        CometParameters reset = named.resetToDefault(LIBRARY);
        assertEquals("", reset.text(LIBRARY));
        assertEquals(ValueOrigin.COMETGUI_DEFAULT, reset.origin(LIBRARY));
        assertEquals(start, reset);
        CometParameters threads =
                start.withText("num_threads", "8", ValueOrigin.USER).resetToDefault("num_threads");
        assertEquals(ValueOrigin.COMET_DEFAULT, threads.origin("num_threads"));
        assertEquals(start, threads);
    }

    @Test
    @DisplayName("values that differ between the releases are each release's own")
    void eachReleasesOwnValues() {
        CometParameters newer = ReleaseDefaults.load(METADATA, C202603);
        CometParameters older = ReleaseDefaults.load(METADATA, C202602);
        assertEquals("-1", newer.text("index_search_type"));
        assertEquals("1", older.text("index_search_type"));
        for (CometParameters set : List.of(newer, older)) {
            assertEquals("0", set.text("output_percolatorfile"));
            assertEquals("1", set.text("output_pepxmlfile"));
            assertEquals("15.9949 M 0 3 -1 0 0 0.0", set.text("variable_mod01"));
            assertEquals("/some/path/db.fasta", set.text("database_name"));
            assertEquals(12, set.enzymeTable().rows().size());
            assertEquals("Trypsin", set.enzymeTable().byNumber(1).orElseThrow().name());
        }
    }

    @Test
    @DisplayName("each load is a fresh, equal set")
    void loadsAreEqual() {
        assertEquals(
                ReleaseDefaults.load(METADATA, C202603), ReleaseDefaults.load(METADATA, C202603));
    }

    @Test
    @DisplayName("a release with no bundled file is refused, naming it")
    void unbundledReleaseRefused() {
        ToolVersion old = ToolVersion.parse("2024.01.0");
        String expected =
                "Comet 2024.01.0 has no bundled starting set; the releases with one are"
                        + " [2026.03.0, 2026.02.2]";
        assertEquals(
                expected,
                assertThrows(
                                IllegalArgumentException.class,
                                () -> ReleaseDefaults.load(METADATA, old))
                        .getMessage());
        assertEquals(
                expected,
                assertThrows(IllegalArgumentException.class, () -> ReleaseDefaults.bundledFile(old))
                        .getMessage());
    }

    @Test
    @DisplayName("a bundled file that does not parse cleanly for its release is refused")
    void uncleanParseRefused() throws IOException {
        // CONSTRUCTED metadata: the bundled metadata with scan_range claimed from 2026.03.0 only,
        // so Comet 2026.02.2's own -q file declares a parameter it does not model for that release
        String json;
        try (InputStream in =
                MetadataLoader.class.getResourceAsStream(
                        "/org/cometgui/params/comet/schema/comet-parameters.json")) {
            json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        int at = json.indexOf("\"name\": \"scan_range\"");
        String from = "\"from\": \"2024.01.0\"";
        int range = json.indexOf(from, at);
        assertTrue(at > 0 && range > at, "the anchor is in the bundled metadata");
        String edited =
                json.substring(0, range)
                        + "\"from\": \"2026.03.0\""
                        + json.substring(range + from.length());
        CuratedMetadata constructed = MetadataLoader.load(edited);

        IllegalStateException refused =
                assertThrows(
                        IllegalStateException.class,
                        () -> ReleaseDefaults.load(constructed, C202602));
        assertTrue(
                refused.getMessage()
                        .startsWith(
                                "the bundled starting set of Comet 2026.02.2 does not parse"
                                        + " cleanly as that release: ["),
                refused.getMessage());
        assertTrue(refused.getMessage().contains("scan_range"), refused.getMessage());
    }
}
