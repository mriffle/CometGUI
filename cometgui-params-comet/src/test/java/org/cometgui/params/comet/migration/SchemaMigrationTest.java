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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.fixtures.ConstructedVersions;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.model.UnknownParameter;
import org.cometgui.params.comet.model.ValueOrigin;
import org.cometgui.params.comet.schema.CuratedMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Migration rules on CONSTRUCTED edits of the real files (each edit is test input, labelled where
 * it is made): unknown parameters carried or adopted, a tuple re-laid-out for another layout,
 * origins, the explicit-only contract, and every refusal.
 */
class SchemaMigrationTest {

    @Test
    @DisplayName(
            "the source's unknown parameters are carried, or adopted where the target models them")
    void unknownParameters() {
        String edited =
                Sets.inserting(
                        Sets.olderText(),
                        "num_threads =",
                        // CONSTRUCTED: three declarations Comet 2024.01.0's -q does not write.
                        "pinfile_protein_delimiter = ;\n"
                                + "future_knob = 7                 # constructed\n"
                                + "min_precursor_charge = abc\n");
        CometParameters source = Sets.parse(Sets.OLDER, edited);
        assertEquals(
                List.of("pinfile_protein_delimiter", "future_knob", "min_precursor_charge"),
                source.unknownParameters().stream().map(UnknownParameter::name).toList());
        MigrationResult result = SchemaMigration.migrate(source, Sets.NEWER);
        MigrationReport report = result.report();

        assertEquals(
                List.of("pinfile_protein_delimiter"),
                report.names(MigrationEntry.Outcome.UNKNOWN_ADOPTED));
        assertEquals(";", result.model().text("pinfile_protein_delimiter"));
        assertEquals(ValueOrigin.IMPORTED, result.model().origin("pinfile_protein_delimiter"));
        assertEquals(";", report.entry("pinfile_protein_delimiter").orElseThrow().targetText());

        assertEquals(List.of("future_knob"), report.names(MigrationEntry.Outcome.UNKNOWN_CARRIED));
        UnknownParameter knob = result.model().unknownParameters().get(0);
        assertEquals(source.unknownParameters().get(1), knob, "kept exactly as imported");
        assertEquals(1, result.model().unknownParameters().size());

        MigrationEntry unreadable = report.entry("min_precursor_charge").orElseThrow();
        assertEquals(MigrationEntry.Outcome.NEEDS_ATTENTION, unreadable.outcome());
        assertEquals(Optional.of("abc"), unreadable.sourceText());
        assertTrue(unreadable.explanation().contains("cannot read"), unreadable.explanation());
        assertEquals(
                Sets.METADATA
                        .parameter("min_precursor_charge", Sets.NEWER)
                        .orElseThrow()
                        .defaultValue(),
                result.model().text("min_precursor_charge"));
        assertEquals(ValueOrigin.COMET_DEFAULT, result.model().origin("min_precursor_charge"));

        assertEquals(
                List.of(
                        "index_search_type",
                        "compoundmods_file",
                        "spectral_library_name",
                        "spectral_library_ms_level",
                        "protein_modslist_file",
                        "print_ascorepro_score",
                        "percentage_base_peak"),
                report.names(MigrationEntry.Outcome.ADDED));
        assertTrue(
                report.entries().indexOf(report.entry("future_knob").orElseThrow())
                        == report.entries().size() - 1,
                "the source's unknown parameters are reported last");
    }

    @Test
    @DisplayName("a parameter kept as unknown goes after the source's own unknown parameters")
    void keptUnknownsComeAfterImportedOnes() {
        String edited =
                Sets.inserting(
                        Sets.newerText(),
                        "num_threads =",
                        "future_knob = 7\n"); // CONSTRUCTED declaration
        CometParameters source = Sets.parse(Sets.NEWER, edited);
        MigrationResult result = SchemaMigration.migrate(source, Sets.OLDER);
        List<String> names =
                result.model().unknownParameters().stream().map(UnknownParameter::name).toList();
        assertEquals("future_knob", names.get(0));
        assertEquals(Sets.NEW_IN_2026, names.subList(1, names.size()));
        List<MigrationEntry> entries = result.report().entries();
        assertEquals(
                MigrationEntry.Outcome.REMOVED_KEPT_AS_UNKNOWN,
                entries.get(entries.size() - 1).outcome());
        assertEquals(
                MigrationEntry.Outcome.UNKNOWN_CARRIED,
                result.report().entry("future_knob").orElseThrow().outcome());
    }

    @Test
    @DisplayName("a carried value keeps its origin; the source model is unchanged")
    void originsAreKeptAndTheSourceIsUntouched() {
        CometParameters source =
                Sets.newer()
                        .withText("num_threads", "4", ValueOrigin.USER)
                        .withOrigin("decoy_search", ValueOrigin.PRESET);
        CometParameters snapshot =
                Sets.newer()
                        .withText("num_threads", "4", ValueOrigin.USER)
                        .withOrigin("decoy_search", ValueOrigin.PRESET);
        MigrationResult result = SchemaMigration.migrate(source, Sets.OLDER);
        assertEquals(ValueOrigin.USER, result.model().origin("num_threads"));
        assertEquals(ValueOrigin.PRESET, result.model().origin("decoy_search"));
        assertEquals(ValueOrigin.IMPORTED, result.model().origin("num_results"));
        assertEquals(snapshot, source);
        assertEquals(source, result.source());
        assertEquals(Sets.NEWER, source.version());
    }

    @Test
    @DisplayName("migrating to the same version carries everything and changes nothing")
    void sameVersion() {
        CometParameters source = Sets.newer();
        MigrationResult result = SchemaMigration.migrate(source, Sets.NEWER);
        assertEquals(source, result.model());
        assertEquals(List.of(), result.report().changes());
        assertEquals(118, result.report().names(MigrationEntry.Outcome.CARRIED).size());
    }

    @Test
    @DisplayName(
            "a tuple is re-laid-out for a CONSTRUCTED version whose fields are in another order")
    void aTupleIsReshaped() {
        CuratedMetadata metadata =
                ConstructedVersions.withConstructedVersion(
                        String.join(
                                ", ",
                                ConstructedVersions.field("MASS", "DECIMAL", false),
                                ConstructedVersions.field("RESIDUES", "RESIDUES", false),
                                ConstructedVersions.field("BINARY_GROUP", "INTEGER", false),
                                ConstructedVersions.field("COUNT", "INTEGER", true),
                                ConstructedVersions.field("TERMINAL_DISTANCE", "INTEGER", false),
                                ConstructedVersions.field("TERMINUS", "INTEGER", false),
                                ConstructedVersions.field("NEUTRAL_LOSS", "DECIMAL", true),
                                ConstructedVersions.field("REQUIRED", "INTEGER", false)));
        ToolVersion constructed = ToolVersion.parse("2099.01.0");
        CometParameters source =
                new org.cometgui.params.comet.parser.CometParamsParser(metadata, Sets.NEWER)
                        .parse(Sets.newerText())
                        .model()
                        .orElseThrow()
                        .withText(
                                "variable_mod03",
                                "79.966331 STY 0 3 -1 0 1 97.976896",
                                ValueOrigin.USER);
        MigrationResult result = SchemaMigration.migrate(source, constructed);
        MigrationEntry entry = result.report().entry("variable_mod03").orElseThrow();
        assertEquals(MigrationEntry.Outcome.RESHAPED, entry.outcome());
        assertEquals(Optional.of("79.966331 STY 0 3 -1 0 1 97.976896"), entry.sourceText());
        assertEquals("79.966331 STY 0 3 -1 0 97.976896 1", entry.targetText());
        assertEquals(source.value("variable_mod03"), result.model().value("variable_mod03"));
        assertEquals(ValueOrigin.USER, result.model().origin("variable_mod03"));
        assertEquals(15, result.report().names(MigrationEntry.Outcome.RESHAPED).size());
        assertTrue(entry.explanation().contains("same meaning"), entry.explanation());
    }

    @Test
    @DisplayName("a file migrates from the version its own marker names")
    void aFileMigratesFromItsMarker() {
        MigrationResult result =
                SchemaMigration.migrateFile(Sets.METADATA, Sets.newerText(), Sets.OLDER);
        assertEquals(Sets.NEWER, result.source().version());
        assertEquals(Sets.OLDER, result.model().version());
    }

    /** The older fixture from its first line break on: the file without its marker's text. */
    private static String afterFirstLine() {
        String text = Sets.olderText();
        return text.substring(text.indexOf('\n'));
    }

    @Test
    void aFileWithoutAMarkerIsRefused() {
        String text = Sets.olderText();
        String unmarked = text.substring(text.indexOf('\n') + 1);
        MigrationException failure =
                assertThrows(
                        MigrationException.class,
                        () -> SchemaMigration.migrateFile(Sets.METADATA, unmarked, Sets.NEWER));
        assertTrue(failure.getMessage().contains("has no \"# comet_version\" line"));
    }

    @Test
    void aFileForAnUncuratedVersionIsRefused() {
        String text = "# comet_version 2019.01 rev. 5" + afterFirstLine();
        MigrationException failure =
                assertThrows(
                        MigrationException.class,
                        () -> SchemaMigration.migrateFile(Sets.METADATA, text, Sets.NEWER));
        assertTrue(failure.getMessage().contains("Comet 2019.01.5"), failure.getMessage());
        assertTrue(failure.getMessage().contains("does not describe"), failure.getMessage());
    }

    @Test
    void anUnreadableMarkerIsRefused() {
        String text = "# comet_version banana" + afterFirstLine();
        MigrationException failure =
                assertThrows(
                        MigrationException.class,
                        () -> SchemaMigration.migrateFile(Sets.METADATA, text, Sets.NEWER));
        assertTrue(
                failure.getMessage().startsWith("line 1 names no readable Comet version"),
                failure.getMessage());
    }

    @Test
    void aFileThatDoesNotParseIsRefused() {
        String text =
                Sets.inserting(
                        Sets.olderText(), "num_threads =", "num_threads = 2\n"); // CONSTRUCTED
        MigrationException failure =
                assertThrows(
                        MigrationException.class,
                        () -> SchemaMigration.migrateFile(Sets.METADATA, text, Sets.NEWER));
        assertTrue(
                failure.getMessage().contains("does not parse as Comet 2024.01.0"),
                failure.getMessage());
        assertTrue(failure.getMessage().contains("num_threads"), failure.getMessage());
    }

    @Test
    void anUncuratedTargetIsRefused() {
        IllegalArgumentException failure =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                SchemaMigration.migrate(
                                        Sets.newer(), ToolVersion.parse("2019.01.5")));
        assertTrue(failure.getMessage().contains("to it"), failure.getMessage());
    }

    @Test
    @DisplayName("the report's records refuse what would make them lie")
    void recordRules() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new MigrationEntry(
                                "x", MigrationEntry.Outcome.CARRIED, Optional.of("1"), "1", " "));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new MigrationEntry(
                                "x", MigrationEntry.Outcome.ADDED, Optional.of("1"), "1", "why"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new MigrationEntry(
                                "x", MigrationEntry.Outcome.CARRIED, Optional.empty(), "1", "why"));
        MigrationEntry one =
                new MigrationEntry("x", MigrationEntry.Outcome.ADDED, Optional.empty(), "1", "why");
        assertThrows(
                IllegalArgumentException.class,
                () -> new MigrationReport(Sets.OLDER, Sets.NEWER, List.of(one, one)));
        MigrationResult real = SchemaMigration.migrate(Sets.newer(), Sets.OLDER);
        assertThrows(
                IllegalArgumentException.class,
                () -> new MigrationResult(real.model(), real.model(), real.report()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new MigrationResult(real.source(), real.source(), real.report()));
        assertThrows(UnsupportedOperationException.class, () -> real.report().entries().clear());
        assertEquals(Optional.empty(), real.report().entry("no_such_parameter"));
        assertTrue(MigrationEntry.Outcome.ADDED.isChange());
        assertTrue(MigrationEntry.Outcome.NEEDS_ATTENTION.isChange());
        assertTrue(!MigrationEntry.Outcome.CARRIED.isChange());
        assertTrue(!MigrationEntry.Outcome.UNKNOWN_CARRIED.isChange());
        String text = real.report().describe();
        assertTrue(
                text.startsWith(
                        "Comet 2026.02.2 -> 2024.01.0: 118 parameters, 9 changes, 0 needing"
                                + " attention\nREMOVED_KEPT_AS_UNKNOWN index_search_type: "),
                text);
    }
}
