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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.UnaryOperator;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.fixtures.CometFixtures;
import org.cometgui.params.comet.fixtures.DeclarationLines;
import org.cometgui.params.comet.fixtures.MigrationFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What the metadata curates about Comet 2024.01.0, the migration fixture's release -- its version
 * record, its tuple layout, its two different defaults and the version ranges of the parameters it
 * lacks -- and proof that each piece is <em>checked</em> against that release's real {@code -q}
 * output, not trusted: undoing any of it makes the drift test report it.
 */
class OlderReleaseCurationTest {

    private static final CuratedMetadata BUNDLED = MetadataLoader.loadBundled();

    private static final ToolVersion OLDER = ToolVersion.parse("2024.01.0");

    private static final ToolVersion NEWER = ToolVersion.parse("2026.02.2");

    /** Typed from a comparison of the two real dumps, to check the derivation below. */
    private static final Set<String> NEW_IN_2026 =
            Set.of(
                    "compoundmods_file",
                    "index_search_type",
                    "min_precursor_charge",
                    "percentage_base_peak",
                    "pinfile_protein_delimiter",
                    "print_ascorepro_score",
                    "protein_modslist_file",
                    "spectral_library_ms_level",
                    "spectral_library_name");

    private static DriftReport drift(CuratedMetadata metadata) throws IOException {
        return SchemaDrift.compare(
                SchemaDiscovery.discover(
                        MigrationFixtures.text(CometFixtures.Mode.COMPLETE),
                        DiscoveryMode.COMPLETE),
                metadata);
    }

    private static Set<String> declared(List<String> lines) {
        return new TreeSet<>(DeclarationLines.names(lines));
    }

    @Test
    @DisplayName("the 2024.01.0 record: the binary's marker, pages, source, and a one-loss tuple")
    void theVersionRecord() {
        CometVersionRecord older = BUNDLED.version(OLDER).orElseThrow();
        assertEquals("2024.01 rev. 0 (f00df0c)", older.marker().text());
        assertEquals(OLDER, older.marker().toolVersion());
        assertEquals(
                "https://uwpr.github.io/Comet/parameters/parameters_202401/",
                older.parameterPages());
        assertEquals("https://github.com/UWPR/Comet/tree/v2024.01.0", older.source());
        VariableModLayout layout = older.variableModTuple();
        VariableModLayout newer = BUNDLED.version(NEWER).orElseThrow().variableModTuple();
        assertEquals(
                newer.fields().stream().map(VariableModLayout.Entry::field).toList(),
                layout.fields().stream().map(VariableModLayout.Entry::field).toList(),
                "same eight fields in the same order");
        assertFalse(layout.entry(VariableModField.NEUTRAL_LOSS).orElseThrow().acceptsPair());
        assertTrue(newer.entry(VariableModField.NEUTRAL_LOSS).orElseThrow().acceptsPair());
        assertTrue(layout.entry(VariableModField.COUNT).orElseThrow().acceptsPair());
        assertEquals(
                "https://github.com/UWPR/Comet/blob/v2024.01.0/Comet.cpp#L567-L601",
                layout.source());
        assertEquals(
                Map.of("fragindex_num_spectrumpeaks", "100", "fragindex_skipreadprecursors", "0"),
                older.defaults());
        assertEquals(Map.of(), BUNDLED.version(NEWER).orElseThrow().defaults());
    }

    @Test
    @DisplayName("the version ranges claim exactly what the two real -q dumps declare")
    void theRangesMatchTheDumps() throws IOException {
        Set<String> older = declared(MigrationFixtures.lines(CometFixtures.Mode.COMPLETE));
        Set<String> newer =
                declared(
                        CometFixtures.lines(
                                CometFixtures.COMET_2026_02_2,
                                CometFixtures.LINUX_X86_64,
                                CometFixtures.Mode.COMPLETE));
        Set<String> claimedOlder = new TreeSet<>();
        BUNDLED.parametersFor(OLDER).forEach(p -> claimedOlder.add(p.name()));
        assertEquals(109, older.size());
        assertEquals(older, claimedOlder);
        Set<String> derived = new TreeSet<>(newer);
        derived.removeAll(older);
        assertEquals(new TreeSet<>(NEW_IN_2026), derived, "derived from the dumps");
        Set<String> gone = new TreeSet<>(older);
        gone.removeAll(newer);
        assertEquals(Set.of(), gone, "Comet 2026.02.2 declares every 2024.01.0 parameter");
        for (String name : NEW_IN_2026) {
            assertEquals(
                    new VersionRange(NEWER, Optional.empty()),
                    BUNDLED.parameter(name).orElseThrow().supportedVersions(),
                    name);
        }
    }

    @Test
    @DisplayName("a parameter's default is the version's: the override where there is one")
    void defaultsAreTheVersions() {
        String name = "fragindex_num_spectrumpeaks";
        assertEquals("150", BUNDLED.parameter(name).orElseThrow().defaultValue());
        assertEquals("150", BUNDLED.parameter(name, NEWER).orElseThrow().defaultValue());
        assertEquals("100", BUNDLED.parameter(name, OLDER).orElseThrow().defaultValue());
        assertEquals(
                "100",
                BUNDLED.parametersFor(OLDER).stream()
                        .filter(p -> p.name().equals(name))
                        .findFirst()
                        .orElseThrow()
                        .defaultValue());
        assertEquals(
                "0",
                BUNDLED.parameter("fragindex_skipreadprecursors", OLDER)
                        .orElseThrow()
                        .defaultValue());
        assertEquals(Optional.empty(), BUNDLED.parameter("spectral_library_name", OLDER));
        assertEquals(Optional.empty(), BUNDLED.parameter("no_such_parameter", OLDER));
        ParameterDefinition plain = BUNDLED.parameter("num_threads").orElseThrow();
        assertEquals(plain, BUNDLED.parameter("num_threads", OLDER).orElseThrow());
        assertEquals(
                plain.defaultValue(),
                BUNDLED.parameter("num_threads", ToolVersion.parse("2025.01.0"))
                        .orElseThrow()
                        .defaultValue(),
                "an uncurated version in the range has no record and so no overrides");
    }

    @Test
    @DisplayName("without the two overrides, the drift test reports exactly those two defaults")
    void theOverridesAreChecked() throws IOException {
        List<CometVersionRecord> stripped = new ArrayList<>();
        for (CometVersionRecord record : BUNDLED.versions()) {
            stripped.add(
                    new CometVersionRecord(
                            record.version(),
                            record.marker(),
                            record.parameterPages(),
                            record.source(),
                            record.variableModTuple()));
        }
        CuratedMetadata metadata =
                new CuratedMetadata(
                        BUNDLED.schemaVersion(),
                        stripped,
                        BUNDLED.parameters(),
                        BUNDLED.internal(),
                        BUNDLED.enzymeTable());
        DriftReport report = drift(metadata);
        assertEquals(
                List.of(
                        "DEFAULT_DIFFERS fragindex_num_spectrumpeaks",
                        "DEFAULT_DIFFERS fragindex_skipreadprecursors"),
                report.findings().stream().map(f -> f.kind() + " " + f.parameter()).toList(),
                report::describe);
    }

    @Test
    @DisplayName("claiming a 2026-only parameter for 2024.01.0 is reported as not declared")
    void anOverclaimIsReported() throws IOException {
        DriftReport report =
                drift(
                        redefine(
                                "pinfile_protein_delimiter",
                                d -> withRange(d, new VersionRange(OLDER, Optional.empty()))));
        assertEquals(1, report.findings().size(), report::describe);
        assertEquals(DriftFinding.Kind.NOT_DECLARED, report.findings().get(0).kind());
        assertEquals("pinfile_protein_delimiter", report.findings().get(0).parameter());
    }

    @Test
    @DisplayName("not claiming a parameter 2024.01.0 declares is reported as unmodelled")
    void anUnderclaimIsReported() throws IOException {
        DriftReport report =
                drift(
                        redefine(
                                "allowed_missed_cleavage",
                                d -> withRange(d, new VersionRange(NEWER, Optional.empty()))));
        assertEquals(1, report.findings().size(), report::describe);
        assertEquals(DriftFinding.Kind.UNMODELLED, report.findings().get(0).kind());
        assertEquals("allowed_missed_cleavage", report.findings().get(0).parameter());
    }

    @Test
    @DisplayName("a range closed at 2024.01.0 is clean there and reported by the 2026.02.2 dump")
    void aClosedRangeIsReportedByTheNewerDump() throws IOException {
        CuratedMetadata metadata =
                redefine(
                        "num_threads",
                        d -> withRange(d, new VersionRange(OLDER, Optional.of(OLDER))));
        assertEquals(List.of(), drift(metadata).findings());
        DriftReport newer =
                SchemaDrift.compare(
                        SchemaDiscovery.discover(
                                new String(
                                        CometFixtures.bytes(
                                                CometFixtures.COMET_2026_02_2,
                                                CometFixtures.LINUX_X86_64,
                                                CometFixtures.Mode.COMPLETE),
                                        java.nio.charset.StandardCharsets.UTF_8),
                                DiscoveryMode.COMPLETE),
                        metadata);
        assertEquals(
                List.of("UNMODELLED num_threads"),
                newer.findings().stream().map(f -> f.kind() + " " + f.parameter()).toList());
    }

    private static CuratedMetadata redefine(
            String name, UnaryOperator<ParameterDefinition> change) {
        List<ParameterDefinition> parameters = new ArrayList<>();
        for (ParameterDefinition definition : BUNDLED.parameters()) {
            parameters.add(definition.name().equals(name) ? change.apply(definition) : definition);
        }
        return new CuratedMetadata(
                BUNDLED.schemaVersion(),
                BUNDLED.versions(),
                parameters,
                BUNDLED.internal(),
                BUNDLED.enzymeTable());
    }

    private static ParameterDefinition withRange(ParameterDefinition d, VersionRange range) {
        return new ParameterDefinition(
                d.name(),
                d.displayName(),
                d.category(),
                d.kind(),
                d.visibility(),
                d.defaultValue(),
                d.minimum(),
                d.maximum(),
                d.choices(),
                d.shortHelp(),
                d.inlineComment(),
                d.detailedHelpRef(),
                range,
                d.serialization(),
                d.validators(),
                d.aliases(),
                d.related());
    }
}
