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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.fixtures.CometFixtures;
import org.cometgui.params.comet.fixtures.CometManifest;
import org.cometgui.params.comet.fixtures.MigrationFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

/**
 * THE drift test ({@code AC-PAR-01}, {@code AC-PAR-02}, phase 06 gate item 2): the bundled metadata
 * against the real {@code comet -q} output of every Comet version that has fixtures under {@code
 * fixtures/comet/} <em>and</em> every version {@code manifests/tools.json} names. A manifest
 * version without fixtures fails here (and in the fixture-matrix test); a captured version the
 * metadata has no record of fails here on its {@code VERSION_RECORD} finding. Any finding fails,
 * and the failure names every parameter involved.
 */
class SchemaDriftFixtureTest {

    private static final CuratedMetadata BUNDLED = MetadataLoader.loadBundled();

    private static String dump(String version, CometFixtures.Mode mode) throws IOException {
        return Files.readString(
                CometFixtures.file(version, CometFixtures.LINUX_X86_64, mode),
                StandardCharsets.UTF_8);
    }

    /**
     * The versions the drift test runs for: every fixture directory, then every manifest version.
     */
    static Set<String> driftVersions(Path fixtureRoot, Path manifest) throws IOException {
        Set<String> versions = new LinkedHashSet<>(CometFixtures.versions(fixtureRoot));
        versions.addAll(CometManifest.cometVersions(manifest));
        return versions;
    }

    @TestFactory
    @DisplayName("the bundled metadata agrees with comet -q of every fixture and manifest version")
    List<DynamicTest> theDriftTest() throws IOException {
        Set<String> versions =
                driftVersions(CometFixtures.root(), CometManifest.repositoryManifest());
        assertFalse(versions.isEmpty(), "neither fixtures nor the manifest name a Comet version");
        assertTrue(
                versions.containsAll(
                        CometManifest.cometVersions(CometManifest.repositoryManifest())),
                "every manifest version is checked: " + versions);
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

    private static final ToolVersion V2026_03_0 = ToolVersion.parse(CometFixtures.COMET_2026_03_0);

    private static DriftReport complete202603(CuratedMetadata metadata) throws IOException {
        return SchemaDrift.compare(
                SchemaDiscovery.discover(
                        dump(CometFixtures.COMET_2026_03_0, CometFixtures.Mode.COMPLETE),
                        DiscoveryMode.COMPLETE),
                metadata);
    }

    private static List<String> kinds(DriftReport report) {
        return report.findings().stream().map(f -> f.kind() + " " + f.parameter()).toList();
    }

    @Test
    @DisplayName("the drift test enumerates fixture directories the manifest does not name")
    void theEnumerationIncludesUnlistedFixtures(@TempDir Path directory) throws IOException {
        Path root = Files.createDirectories(directory.resolve("fixtures"));
        Files.createDirectories(root.resolve("2099.01.0").resolve(CometFixtures.LINUX_X86_64));
        Files.createDirectories(root.resolve(CometFixtures.COMET_2026_02_2));
        Path manifest = directory.resolve("tools.json");
        Files.writeString(
                manifest,
                "{\"schemaVersion\": 1, \"artefacts\": [{\"tool\": \"comet\", \"version\":"
                        + " \"2099.02.0\", \"releaseTag\": \"v2099.02.0\", \"os\": \"linux\","
                        + " \"arch\": \"x86-64\", \"url\": \"https://example.invalid/c\","
                        + " \"sha256\": \""
                        + "0".repeat(64)
                        + "\"}]}",
                StandardCharsets.UTF_8);
        assertEquals(
                List.of(CometFixtures.COMET_2026_02_2, "2099.01.0", "2099.02.0"),
                List.copyOf(driftVersions(root, manifest)));
        assertTrue(
                driftVersions(CometFixtures.root(), CometManifest.repositoryManifest())
                        .contains(CometFixtures.COMET_2026_03_0),
                "the real 2026.03.0 fixtures are checked whether or not the manifest names it");
    }

    @Test
    @DisplayName("a captured release with no version record fails the drift test")
    void aFixtureVersionWithoutARecordFails() throws IOException {
        String real = dump(CometFixtures.COMET_2026_03_0, CometFixtures.Mode.COMPLETE);
        String firstLine = real.substring(0, real.indexOf('\n'));
        assertEquals("# comet_version 2026.03 rev. 0 (fa08489)", firstLine);
        DriftReport unrecorded =
                SchemaDrift.compare(
                        SchemaDiscovery.discover(
                                "# comet_version 2099.01 rev. 0 (fa08489)"
                                        + real.substring(firstLine.length()),
                                DiscoveryMode.COMPLETE),
                        BUNDLED);
        assertFalse(unrecorded.isClean());
        assertEquals(DriftFinding.Kind.VERSION_RECORD, unrecorded.findings().get(0).kind());
        assertTrue(
                unrecorded.findings().get(0).message().contains("has no record of Comet 2099.01.0"),
                unrecorded::describe);
        DriftReport otherBuild =
                SchemaDrift.compare(
                        SchemaDiscovery.discover(
                                "# comet_version 2026.03 rev. 0 (0000000)"
                                        + real.substring(firstLine.length()),
                                DiscoveryMode.COMPLETE),
                        BUNDLED);
        assertEquals(List.of("VERSION_RECORD 2026.03 rev. 0 (0000000)"), kinds(otherBuild));
    }

    @Test
    @DisplayName("2026.03.0: 118 declared = 118 modelled + 0 allow-listed, no finding")
    void theCountsFor202603() throws IOException {
        DriftReport report = complete202603(BUNDLED);
        System.out.println(
                "[AC-PAR-01] Comet 2026.03.0: modelled "
                        + report.modelled()
                        + ", allow-listed "
                        + report.allowListed()
                        + ", total "
                        + report.declared());
        assertTrue(report.isClean(), report::describe);
        assertEquals(
                List.of(118, 118, 0),
                List.of(report.declared(), report.modelled(), report.allowListed()),
                "[declared, modelled, allow-listed] for Comet 2026.03.0");
    }

    @Test
    @DisplayName("2026.03.0 -p: PARTIAL_DISCOVERY, 95 declared, none of the 23 -q-only reported")
    void thePartialDumpOf202603ReportsNothingRemoved() throws IOException {
        DiscoveredSchema partial =
                SchemaDiscovery.discover(
                        dump(CometFixtures.COMET_2026_03_0, CometFixtures.Mode.DEFAULTS),
                        DiscoveryMode.PARTIAL_DISCOVERY);
        Set<String> absent = new TreeSet<>();
        for (ParameterDefinition definition : BUNDLED.parametersFor(V2026_03_0)) {
            if (partial.parameter(definition.name()).isEmpty()) {
                absent.add(definition.name());
            }
        }
        assertEquals(23, absent.size(), "the metadata claims 23 names -p leaves out: " + absent);
        assertTrue(absent.contains("index_search_type"), absent::toString);
        DriftReport report = SchemaDrift.compare(partial, BUNDLED);
        assertEquals(DiscoveryMode.PARTIAL_DISCOVERY, report.mode());
        assertTrue(report.isClean(), report::describe);
        assertEquals(
                List.of(95, 95, 0),
                List.of(report.declared(), report.modelled(), report.allowListed()));
    }

    @Test
    @DisplayName("2026.03.0, red (a): an entry removed from the metadata is reported unmodelled")
    void anEntryRemovedIsReportedFor202603() throws IOException {
        DriftReport report = complete202603(without(BUNDLED, "index_search_type"));
        assertEquals(List.of("UNMODELLED index_search_type"), kinds(report), report::describe);
        assertTrue(
                report.findings()
                        .get(0)
                        .message()
                        .startsWith("Comet 2026.03.0 declares index_search_type (line 14,"),
                report::describe);
        assertEquals(117, report.modelled());
    }

    @Test
    @DisplayName("2026.03.0, red (b): a parameter claimed by version range that -q lacks")
    void aRangeClaimTheBinaryLacksIsReportedFor202603() throws IOException {
        ParameterDefinition model = BUNDLED.parameter("num_threads").orElseThrow();
        ParameterDefinition claimed =
                new ParameterDefinition(
                        "ms1_mass_range",
                        "Constructed claim",
                        model.category(),
                        model.kind(),
                        model.visibility(),
                        model.defaultValue(),
                        model.minimum(),
                        model.maximum(),
                        model.choices(),
                        model.shortHelp(),
                        model.inlineComment(),
                        model.detailedHelpRef(),
                        new VersionRange(V2026_03_0, Optional.empty()),
                        model.serialization(),
                        model.validators(),
                        model.aliases(),
                        model.related());
        List<ParameterDefinition> parameters = new ArrayList<>(BUNDLED.parameters());
        parameters.add(claimed);
        CuratedMetadata metadata =
                new CuratedMetadata(
                        BUNDLED.schemaVersion(),
                        BUNDLED.versions(),
                        parameters,
                        BUNDLED.internal(),
                        BUNDLED.enzymeTable());
        DriftReport report = complete202603(metadata);
        assertEquals(List.of("NOT_DECLARED ms1_mass_range"), kinds(report), report::describe);
        assertTrue(
                SchemaDrift.compare(
                                SchemaDiscovery.discover(
                                        dump(
                                                CometFixtures.COMET_2026_02_2,
                                                CometFixtures.Mode.COMPLETE),
                                        DiscoveryMode.COMPLETE),
                                metadata)
                        .isClean(),
                "the claim starts at 2026.03.0, so 2026.02.2's drift is untouched");
    }

    @Test
    @DisplayName("2026.03.0, red (b): an override cannot claim what the version range does not")
    void anOverrideClaimOutsideTheRangeIsRefused() throws IOException {
        String json = bundledJson();
        String anchor = "\"name\": \"index_search_type\",";
        int parameter = json.indexOf(anchor, json.indexOf("\"parameters\": ["));
        String range = "\"through\": null";
        int through = json.indexOf(range, parameter);
        String edited =
                json.substring(0, through)
                        + "\"through\": \"2026.02.2\""
                        + json.substring(through + range.length());
        InvalidMetadataException failure =
                assertThrows(InvalidMetadataException.class, () -> MetadataLoader.load(edited));
        assertEquals(
                "versions[0] override for \"index_search_type\"",
                failure.where(),
                failure.getMessage());
        assertTrue(
                failure.getMessage().contains("is not modelled for Comet 2026.03.0"),
                failure.getMessage());
    }

    @Test
    @DisplayName("2026.03.0: without its index_search_type override, -1 is reported as drift")
    void theOverrideIsWhatMakes202603Clean() throws IOException {
        List<CometVersionRecord> records = new ArrayList<>();
        for (CometVersionRecord record : BUNDLED.versions()) {
            Map<String, ParameterOverride> overrides = new LinkedHashMap<>(record.overrides());
            if (record.version().equals(V2026_03_0)) {
                assertTrue(overrides.remove("index_search_type") != null);
            }
            records.add(
                    new CometVersionRecord(
                            record.version(),
                            record.marker(),
                            record.parameterPages(),
                            record.source(),
                            record.variableModTuple(),
                            overrides));
        }
        DriftReport report =
                complete202603(
                        new CuratedMetadata(
                                BUNDLED.schemaVersion(),
                                records,
                                BUNDLED.parameters(),
                                BUNDLED.internal(),
                                BUNDLED.enzymeTable()));
        assertEquals(List.of("DEFAULT_DIFFERS index_search_type"), kinds(report), report::describe);
        assertTrue(
                report.findings()
                        .get(0)
                        .message()
                        .contains("is \"1\" and Comet 2026.03.0 writes \"-1\""),
                report::describe);
    }

    private static String bundledJson() throws IOException {
        try (InputStream in = MetadataLoader.class.getResourceAsStream(MetadataLoader.RESOURCE)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
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
