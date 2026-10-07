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

package org.cometgui.params.comet.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.cometgui.params.comet.fixtures.CometFixtures;
import org.cometgui.params.comet.fixtures.DeclarationLines;
import org.cometgui.params.comet.fixtures.MigrationFixtures;
import org.cometgui.params.comet.fixtures.ParamsFiles;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.model.ParameterEntry;
import org.cometgui.params.comet.model.ParameterValue;
import org.cometgui.params.comet.model.UnknownParameter;
import org.cometgui.params.comet.model.ValueOrigin;
import org.cometgui.params.comet.parser.CometParamsParser;
import org.cometgui.params.comet.parser.ParseResult;
import org.cometgui.params.comet.validation.CometValidator;
import org.cometgui.params.comet.validation.Finding;
import org.cometgui.params.comet.validation.Rule;
import org.cometgui.params.comet.validation.ValidationReport;
import org.cometgui.params.comet.writer.CanonicalParamsWriter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Schema migration between two REAL Comet versions: the migration fixture's Comet 2024.01.0 {@code
 * -q} file and Comet 2026.02.2's. The expected parameter differences are derived from the two real
 * dumps here <strong>and</strong> typed by hand in {@link Sets}, so each checks the other.
 */
class RealMigrationTest {

    private static List<String> declared(List<String> lines) {
        return DeclarationLines.names(lines);
    }

    private static List<String> olderNames() throws IOException {
        return declared(MigrationFixtures.lines(CometFixtures.Mode.COMPLETE));
    }

    private static List<String> newerNames() throws IOException {
        return declared(
                CometFixtures.lines(
                        CometFixtures.COMET_2026_02_2,
                        CometFixtures.LINUX_X86_64,
                        CometFixtures.Mode.COMPLETE));
    }

    /** {@code a} minus {@code b}, in {@code a}'s order. */
    private static List<String> minus(List<String> a, List<String> b) {
        Set<String> without = new LinkedHashSet<>(a);
        without.removeAll(b);
        return new ArrayList<>(without);
    }

    private static List<String> errorIds(ValidationReport report) {
        return report.errors().stream().map(f -> f.rule().id()).toList();
    }

    @Nested
    @DisplayName("the real Comet 2024.01.0 -q file, migrated to Comet 2026.02.2")
    class Forward {

        private final MigrationResult result =
                SchemaMigration.migrateFile(Sets.METADATA, Sets.olderText(), Sets.NEWER);

        @Test
        @DisplayName("the report adds exactly the parameters the two real dumps differ by")
        void addedIsTheDifferenceOfTheDumps() throws IOException {
            MigrationReport report = result.report();
            System.out.println("[migration] " + report.describe());
            List<String> derived = minus(newerNames(), olderNames());
            List<String> added = report.names(MigrationEntry.Outcome.ADDED);
            assertEquals(Sets.NEW_IN_2026, derived, "hand-typed list against the dumps");
            assertEquals(new LinkedHashSet<>(derived), new LinkedHashSet<>(added));
            assertEquals(Sets.NEW_IN_2026, added, "in 2026.02.2's schema order");
            assertEquals(new ArrayList<>(Sets.GONE_IN_2026), minus(olderNames(), newerNames()));
            assertEquals(List.of(), report.names(MigrationEntry.Outcome.REMOVED_KEPT_AS_UNKNOWN));
            assertEquals(
                    new LinkedHashSet<>(olderNames()),
                    new LinkedHashSet<>(report.names(MigrationEntry.Outcome.CARRIED)));
            assertEquals(109, report.names(MigrationEntry.Outcome.CARRIED).size());
            assertEquals(List.of(), report.needingAttention());
            assertEquals(List.of(), report.names(MigrationEntry.Outcome.RESHAPED));
            assertEquals(118, report.entries().size());
            assertEquals(Sets.NEW_IN_2026.size(), report.changes().size());
        }

        @Test
        @DisplayName(
                "carried values keep their text and origin; added ones take 2026.02.2's default,"
                        + " or CometGUI's starting value")
        void valuesAndOrigins() {
            CometParameters source = result.source();
            CometParameters migrated = result.model();
            assertEquals(Sets.OLDER, source.version());
            assertEquals(Sets.NEWER, migrated.version());
            for (ParameterEntry entry : source.entries()) {
                String name = entry.name();
                assertEquals(source.text(name), migrated.text(name), name);
                assertEquals(ValueOrigin.IMPORTED, migrated.origin(name), name);
                assertEquals(source.value(name), migrated.value(name), name);
            }
            for (String name : Sets.NEW_IN_2026) {
                MigrationEntry entry = result.report().entry(name).orElseThrow();
                assertEquals(MigrationEntry.Outcome.ADDED, entry.outcome(), name);
                assertEquals(Optional.empty(), entry.sourceText());
                assertEquals(migrated.text(name), entry.targetText());
                if ("spectral_library_name".equals(name)) {
                    // D-012: absent from the 2024.01.0 file, so no library -- as Comet reads a
                    // file without the line -- and said so in the report, not Comet's placeholder
                    assertEquals(ValueOrigin.COMETGUI_DEFAULT, migrated.origin(name), name);
                    assertEquals("", migrated.text(name));
                    assertEquals(
                            "spectral_library_name is new in Comet 2026.02.2 (Comet 2024.01.0 has"
                                    + " no such parameter) and takes CometGUI's starting value,"
                                    + " empty, not Comet's default /some/path/speclib.file: a file"
                                    + " without the line is read so by Comet 2026.02.2 too",
                            entry.explanation());
                    continue;
                }
                assertEquals(ValueOrigin.COMET_DEFAULT, migrated.origin(name), name);
                assertEquals(
                        Sets.METADATA.parameter(name, Sets.NEWER).orElseThrow().defaultValue(),
                        migrated.text(name),
                        name);
            }
            assertEquals(
                    "100",
                    migrated.text("fragindex_num_spectrumpeaks"),
                    "the file's own value is carried, not 2026.02.2's default of 150");
            assertEquals(source.enzymeTable(), migrated.enzymeTable());
            assertEquals("Glu_C", migrated.enzymeTable().byNumber(8).orElseThrow().name());
            assertEquals(List.of(), migrated.unknownParameters());
            assertEquals(List.of(), migrated.diagnostics());
        }

        @Test
        @DisplayName("each real tuple is carried as the same typed value into 2026.02.2's layout")
        void tuplesAreCarried() {
            for (int slot = 1; slot <= 15; slot++) {
                String name = String.format(java.util.Locale.ROOT, "variable_mod%02d", slot);
                ParameterValue before = result.source().value(name);
                ParameterValue after = result.model().value(name);
                assertEquals(before, after, name);
                assertEquals(
                        MigrationEntry.Outcome.CARRIED,
                        result.report().entry(name).orElseThrow().outcome(),
                        name);
            }
            assertEquals("15.9949 M 0 3 -1 0 0 0.0", result.model().text("variable_mod01"));
        }

        @Test
        @DisplayName("the migrated set writes, re-parses as the same model and validates")
        void writesParsesValidates() {
            CometParameters migrated = result.model();
            String text = new CanonicalParamsWriter(ParamsFiles.build()).write(migrated);
            assertTrue(text.startsWith("# comet_version 2026.02 rev. 2 (6edec91)\n"), text);
            ParseResult again = new CometParamsParser(Sets.METADATA, Sets.NEWER).parse(text);
            assertEquals(List.of(), again.diagnostics());
            CometParameters reparsed = again.model().orElseThrow();
            for (ParameterEntry entry : migrated.entries()) {
                assertEquals(migrated.value(entry.name()), reparsed.value(entry.name()));
            }
            assertEquals(migrated.enzymeTable(), reparsed.enzymeTable());
            ValidationReport raw = CometValidator.standard().validate(migrated);
            assertEquals(List.of("workflow_enforced.output_off"), errorIds(raw));
            assertEquals(List.of("output_percolatorfile"), raw.errors().get(0).parameters());
            ValidationReport enforced =
                    CometValidator.standard().validate(migrated.withWorkflowEnforcedOutputs());
            assertEquals(List.of(), enforced.findings());
        }

        @Test
        @DisplayName("an old-form tuple edit -- min,max count, exclusive, one loss -- is carried")
        void anEditedOldFormTupleIsCarried() {
            String edited =
                    Sets.replacing(
                            Sets.olderText(),
                            "variable_mod02 =",
                            "variable_mod02 = 79.966331 STY 0 2,4 -1 0 -1 97.976896");
            CometParameters source = Sets.parse(Sets.OLDER, edited);
            MigrationResult moved = SchemaMigration.migrate(source, Sets.NEWER);
            assertEquals(source.value("variable_mod02"), moved.model().value("variable_mod02"));
            assertEquals(
                    "79.966331 STY 0 2,4 -1 0 -1 97.976896", moved.model().text("variable_mod02"));
            assertEquals(
                    MigrationEntry.Outcome.CARRIED,
                    moved.report().entry("variable_mod02").orElseThrow().outcome());
            String written = new CanonicalParamsWriter(ParamsFiles.build()).write(moved.model());
            assertTrue(
                    written.contains("\nvariable_mod02 = 79.966331 STY 0 2,4 -1 0 -1 97.976896\n"));
        }
    }

    @Nested
    @DisplayName("the real Comet 2026.02.2 -q file, migrated back to Comet 2024.01.0")
    class Backward {

        private final CometParameters source = Sets.newer();

        private final MigrationResult result = SchemaMigration.migrate(source, Sets.OLDER);

        @Test
        @DisplayName("the parameters 2024.01.0 lacks are kept as reported unknowns, none dropped")
        void removedAreKept() throws IOException {
            MigrationReport report = result.report();
            System.out.println("[migration] " + report.describe());
            List<String> derived = minus(newerNames(), olderNames());
            assertEquals(Sets.NEW_IN_2026, derived);
            assertEquals(
                    Sets.NEW_IN_2026, report.names(MigrationEntry.Outcome.REMOVED_KEPT_AS_UNKNOWN));
            assertEquals(List.of(), report.names(MigrationEntry.Outcome.ADDED));
            List<UnknownParameter> kept = result.model().unknownParameters();
            assertEquals(Sets.NEW_IN_2026, kept.stream().map(UnknownParameter::name).toList());
            for (UnknownParameter unknown : kept) {
                assertEquals(source.text(unknown.name()), unknown.value(), unknown.name());
                assertEquals(SchemaMigration.NOT_FROM_A_FILE, unknown.line());
                assertEquals(
                        source.definition(unknown.name()).inlineComment(), unknown.inlineComment());
                MigrationEntry entry = report.entry(unknown.name()).orElseThrow();
                assertEquals(Optional.of(unknown.value()), entry.sourceText());
                assertEquals(unknown.value(), entry.targetText());
                assertTrue(entry.explanation().contains("2024.01.0"), entry.explanation());
            }
            assertEquals(109, result.model().entries().size());
            assertEquals(
                    "150",
                    result.model().text("fragindex_num_spectrumpeaks"),
                    "the file's value is carried, not 2024.01.0's default of 100");
        }

        @Test
        @DisplayName("validation blocks each kept parameter, and the written file keeps them all")
        void keptParametersAreBlockedAndWritten() {
            ValidationReport report = CometValidator.standard().validate(result.model());
            List<Finding> unavailable = report.of(Rule.UNAVAILABLE_IN_VERSION);
            assertEquals(
                    Sets.NEW_IN_2026,
                    unavailable.stream().map(f -> f.parameters().get(0)).toList());
            String text = new CanonicalParamsWriter(ParamsFiles.build()).write(result.model());
            assertTrue(text.startsWith("# comet_version 2024.01 rev. 0 (f00df0c)\n"), text);
            ParseResult again = new CometParamsParser(Sets.METADATA, Sets.OLDER).parse(text);
            CometParameters reparsed = again.model().orElseThrow();
            assertEquals(
                    result.model().unknownParameters().stream()
                            .map(u -> u.name() + " = " + u.value())
                            .toList(),
                    reparsed.unknownParameters().stream()
                            .map(u -> u.name() + " = " + u.value())
                            .toList());
            for (ParameterEntry entry : result.model().entries()) {
                assertEquals(result.model().value(entry.name()), reparsed.value(entry.name()));
            }
        }

        @Test
        @DisplayName(
                "two neutral losses, which 2024.01.0 cannot hold, need attention and are reported")
        void aPairedLossNeedsAttention() {
            CometParameters paired =
                    source.withText(
                            "variable_mod02",
                            "79.966331 STY 0 3 -1 0 0 97.976896,79.966331",
                            ValueOrigin.USER);
            MigrationResult moved = SchemaMigration.migrate(paired, Sets.OLDER);
            MigrationEntry entry = moved.report().entry("variable_mod02").orElseThrow();
            assertEquals(MigrationEntry.Outcome.NEEDS_ATTENTION, entry.outcome());
            assertEquals(
                    Optional.of("79.966331 STY 0 3 -1 0 0 97.976896,79.966331"),
                    entry.sourceText());
            assertEquals("0.0 X 0 3 -1 0 0 0.0", entry.targetText());
            assertTrue(entry.explanation().contains("two neutral losses"), entry.explanation());
            assertEquals(List.of(entry), moved.report().needingAttention());
            assertEquals("0.0 X 0 3 -1 0 0 0.0", moved.model().text("variable_mod02"));
            assertEquals(ValueOrigin.COMET_DEFAULT, moved.model().origin("variable_mod02"));
            assertTrue(moved.report().describe().contains("NEEDS_ATTENTION variable_mod02"));
        }
    }
}
