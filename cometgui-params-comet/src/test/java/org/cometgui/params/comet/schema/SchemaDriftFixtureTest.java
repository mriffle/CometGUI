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

package org.cometgui.params.comet.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.cometgui.params.comet.fixtures.CometFixtures;
import org.cometgui.params.comet.fixtures.CometManifest;
import org.cometgui.params.comet.fixtures.MigrationFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * THE drift test ({@code AC-PAR-01}, {@code AC-PAR-02}, phase 06 gate item 2): the bundled metadata
 * against the real {@code comet -q} output of every Comet version {@code manifests/tools.json}
 * names -- the same enumeration unit 1's fixture-matrix test makes, so a new version without
 * fixtures already fails there. Any finding fails, and the failure names every parameter involved.
 */
class SchemaDriftFixtureTest {

    private static final CuratedMetadata BUNDLED = MetadataLoader.loadBundled();

    private static String dump(String version, CometFixtures.Mode mode) throws IOException {
        return Files.readString(
                CometFixtures.file(version, CometFixtures.LINUX_X86_64, mode),
                StandardCharsets.UTF_8);
    }

    @TestFactory
    @DisplayName("the bundled metadata agrees with comet -q of every manifest Comet version")
    List<DynamicTest> theDriftTest() throws IOException {
        Set<String> versions = CometManifest.cometVersions(CometManifest.repositoryManifest());
        assertFalse(versions.isEmpty(), "the manifest names no Comet version to check");
        List<DynamicTest> tests = new ArrayList<>();
        for (String version : versions) {
            tests.add(
                    DynamicTest.dynamicTest(
                            "Comet " + version + " -q",
                            () -> {
                                DriftReport report =
                                        SchemaDrift.compare(
                                                SchemaDiscovery.discover(
                                                        dump(version, CometFixtures.Mode.COMPLETE),
                                                        DiscoveryMode.COMPLETE),
                                                BUNDLED);
                                System.out.println("[drift] " + report.describe());
                                assertTrue(
                                        report.isClean(),
                                        () -> "schema drift:\n" + report.describe());
                                assertEquals(
                                        report.declared(),
                                        report.modelled() + report.allowListed(),
                                        report::describe);
                            }));
        }
        return tests;
    }

    @Test
    @DisplayName("2026.02.2: 118 declared = 118 modelled + 0 allow-listed")
    void theCountsFor202602() throws IOException {
        DriftReport report =
                SchemaDrift.compare(
                        SchemaDiscovery.discover(
                                dump(CometFixtures.COMET_2026_02_2, CometFixtures.Mode.COMPLETE),
                                DiscoveryMode.COMPLETE),
                        BUNDLED);
        System.out.println(
                "[AC-PAR-01] Comet 2026.02.2: modelled "
                        + report.modelled()
                        + ", allow-listed "
                        + report.allowListed()
                        + ", total "
                        + report.declared());
        assertEquals(
                List.of(118, 118, 0),
                List.of(report.declared(), report.modelled(), report.allowListed()),
                "[declared, modelled, allow-listed] for Comet 2026.02.2");
    }

    @Test
    @DisplayName("PARTIAL_DISCOVERY from the real -p output reports none of the 22 -q-only names")
    void thePartialDumpReportsNothingRemoved() throws IOException {
        DiscoveredSchema partial =
                SchemaDiscovery.discover(
                        dump(CometFixtures.COMET_2026_02_2, CometFixtures.Mode.DEFAULTS),
                        DiscoveryMode.PARTIAL_DISCOVERY);
        Set<String> absent = new TreeSet<>();
        for (ParameterDefinition definition : BUNDLED.parameters()) {
            if (partial.parameter(definition.name()).isEmpty()) {
                absent.add(definition.name());
            }
        }
        assertEquals(22, absent.size(), "the metadata claims 22 names -p leaves out: " + absent);
        DriftReport report = SchemaDrift.compare(partial, BUNDLED);
        assertTrue(report.isClean(), report::describe);
        assertEquals(96, report.declared());
        assertEquals(96, report.modelled());
    }

    @Test
    @DisplayName("...and the same -p output still reports a parameter the metadata lacks")
    void thePartialDumpStillReportsAnUnmodelledName() throws IOException {
        DiscoveredSchema partial =
                SchemaDiscovery.discover(
                        dump(CometFixtures.COMET_2026_02_2, CometFixtures.Mode.DEFAULTS),
                        DiscoveryMode.PARTIAL_DISCOVERY);
        DriftReport report = SchemaDrift.compare(partial, without(BUNDLED, "num_threads"));
        assertEquals(1, report.findings().size(), report::describe);
        DriftFinding finding = report.findings().get(0);
        assertEquals(DriftFinding.Kind.UNMODELLED, finding.kind());
        assertEquals("num_threads", finding.parameter());
    }

    @Test
    @DisplayName("the complete -q output reports a removed metadata entry as unmodelled")
    void theCompleteDumpReportsARemovedEntry() throws IOException {
        DriftReport report =
                SchemaDrift.compare(
                        SchemaDiscovery.discover(
                                dump(CometFixtures.COMET_2026_02_2, CometFixtures.Mode.COMPLETE),
                                DiscoveryMode.COMPLETE),
                        without(BUNDLED, "variable_mod15"));
        assertEquals(1, report.findings().size(), report::describe);
        assertEquals(DriftFinding.Kind.UNMODELLED, report.findings().get(0).kind());
        assertEquals("variable_mod15", report.findings().get(0).parameter());
        assertEquals(117, report.modelled());
    }

    @Test
    @DisplayName("the migration fixture's Comet 2024.01.0 -q: 109 declared = 109 modelled, clean")
    void theOlderReleaseHasNoDrift() throws IOException {
        DriftReport report =
                SchemaDrift.compare(
                        SchemaDiscovery.discover(
                                MigrationFixtures.text(CometFixtures.Mode.COMPLETE),
                                DiscoveryMode.COMPLETE),
                        BUNDLED);
        System.out.println("[drift] " + report.describe());
        assertTrue(report.isClean(), () -> "schema drift:\n" + report.describe());
        assertEquals(
                List.of(109, 109, 0),
                List.of(report.declared(), report.modelled(), report.allowListed()),
                "[declared, modelled, allow-listed] for Comet 2024.01.0");
    }

    @Test
    @DisplayName(
            "the migration fixture's Comet 2024.01.0 -p: PARTIAL_DISCOVERY, 87 declared, clean")
    void theOlderReleasesPartialDumpHasNoDrift() throws IOException {
        DriftReport report =
                SchemaDrift.compare(
                        SchemaDiscovery.discover(
                                MigrationFixtures.text(CometFixtures.Mode.DEFAULTS),
                                DiscoveryMode.PARTIAL_DISCOVERY),
                        BUNDLED);
        assertTrue(report.isClean(), report::describe);
        assertEquals(List.of(87, 87), List.of(report.declared(), report.modelled()));
    }

    private static CuratedMetadata without(CuratedMetadata metadata, String name) {
        List<ParameterDefinition> kept =
                metadata.parameters().stream().filter(p -> !p.name().equals(name)).toList();
        assertEquals(metadata.parameters().size() - 1, kept.size(), name + " was not there");
        return new CuratedMetadata(
                metadata.schemaVersion(),
                metadata.versions(),
                kept,
                metadata.internal(),
                metadata.enzymeTable());
    }
}
