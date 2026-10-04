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

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.fixtures.ConstructedVersions;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.model.ValueOrigin;
import org.cometgui.params.comet.schema.CuratedMetadata;
import org.cometgui.params.comet.schema.ValueMigration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link VersionConversion} applying a target release's value migrations: the bundled 2026.03.0 and
 * 2026.02.2 records' entries, value by value -- as the preset compatibility check uses them, and
 * with the source's rule findings given, as migration does -- and a CONSTRUCTED target ({@code
 * 2099.01.0}, test input built on the bundled metadata) whose reordered tuple layout and entries
 * show how a conversion, a notice and a re-laid-out tuple combine.
 */
class ValueMigrationConversionTest {

    private static final ToolVersion V2026_03 = MigrationTo202603Test.V2026_03;

    private static final ToolVersion V2026_02 = MigrationTo202603Test.V2026_02;

    private static final String DISTANCE = "variable_mod_tuple.distance_undocumented";

    private static final String TERMINUS = "variable_mod_tuple.terminus_undocumented";

    private static final String NEGATIVE_GROUP = "variable_mod_tuple.binary_group_negative";

    private static final String CONVERT_REASON =
            "Comet 2026.02.2 reads index_search_type only to choose the index it builds for a named"
                    + " .idx file that does not exist yet, and builds a fragment-ion index for"
                    + " every value but 0, so 1 (what its comet -q writes) is its ordinary"
                    + " setting. Comet 2026.03.0's -1 (not set, what its comet -q writes) does"
                    + " exactly the same and never warns; 1 there draws a warning on every FASTA"
                    + " search that the value is ignored."
                    + " (https://github.com/UWPR/Comet/blob/v2026.02.2/CometSearch/"
                    + "CometSearchManager.cpp#L1524)";

    @Nested
    @DisplayName("the bundled records, value by value")
    class Bundled {

        private final VersionConversion up =
                VersionConversion.between(MigrationTo202603Test.METADATA, V2026_02, V2026_03);

        @Test
        @DisplayName("2026.02.2's index_search_type = 1 is CONVERTED to -1, the entry named")
        void one() {
            VersionConversion.Result result = up.convert("index_search_type", "1");
            assertEquals(VersionConversion.Status.CONVERTED, result.status());
            assertEquals("1", result.sourceText());
            assertEquals(Optional.of("-1"), result.targetText());
            assertEquals(
                    "index_search_type = 1 (Comet 2026.02.2) is written -1 for Comet 2026.03.0,"
                            + " which means the same there: "
                            + CONVERT_REASON,
                    result.explanation());
            assertEquals(1, result.applied().size());
            assertEquals(ValueMigration.Action.CONVERT, result.applied().get(0).action());
        }

        @Test
        @DisplayName("0 is SAME, with the notice in the explanation and the entry named")
        void zero() {
            VersionConversion.Result result = up.convert("index_search_type", "0");
            assertEquals(VersionConversion.Status.SAME, result.status());
            assertEquals(Optional.of("0"), result.targetText());
            assertEquals(ValueMigration.Action.NOTICE, result.applied().get(0).action());
            assertEquals(
                    "index_search_type = 0 means the same in Comet 2026.02.2 and Comet 2026.03.0,"
                            + " but: "
                            + result.applied().get(0).reason()
                            + " ("
                            + result.applied().get(0).source()
                            + ")",
                    result.explanation());
        }

        @Test
        @DisplayName("an entry matched by rule applies only when the source has that finding")
        void byRule() {
            String slot = "15.9949 M 0 3 -3 0 0 0.0";
            VersionConversion.Result plain = up.convert("variable_mod01", slot);
            assertEquals(VersionConversion.Status.SAME, plain.status());
            assertEquals(List.of(), plain.applied());
            assertEquals(
                    "variable_mod01 = 15.9949 M 0 3 -3 0 0 0.0 means the same in Comet 2026.02.2"
                            + " and Comet 2026.03.0",
                    plain.explanation());

            VersionConversion.Result found = up.convert("variable_mod01", slot, Set.of(DISTANCE));
            assertEquals(VersionConversion.Status.CONVERTED, found.status());
            assertEquals(Optional.of("15.9949 M 0 3 -1 0 0 0.0"), found.targetText());
            assertEquals(Optional.of("-1"), found.applied().get(0).becomes());

            VersionConversion.Result never =
                    up.convert("variable_mod02", "15.9949 M 0 3 2 4 0 0.0", Set.of(TERMINUS));
            assertEquals(VersionConversion.Status.NOT_CONVERTIBLE, never.status());
            assertEquals(Optional.empty(), never.targetText());
            assertEquals(
                    "variable_mod02 = 15.9949 M 0 3 2 4 0 0.0 (Comet 2026.02.2) has no equivalent"
                            + " in Comet 2026.03.0: "
                            + never.applied().get(0).reason()
                            + " ("
                            + never.applied().get(0).source()
                            + ")",
                    never.explanation());

            assertEquals(
                    VersionConversion.Status.SAME,
                    up.convert("variable_mod01", slot, Set.of(NEGATIVE_GROUP)).status());
        }

        @Test
        @DisplayName("2026.03.0's -1 is written 1 for 2026.02.2; 2024.01.0 has no such entry")
        void keyedBySource() {
            VersionConversion.Result back =
                    VersionConversion.between(MigrationTo202603Test.METADATA, V2026_03, V2026_02)
                            .convert("index_search_type", "-1");
            assertEquals(VersionConversion.Status.CONVERTED, back.status());
            assertEquals(Optional.of("1"), back.targetText());
            VersionConversion.Result same =
                    VersionConversion.between(MigrationTo202603Test.METADATA, V2026_03, V2026_03)
                            .convert("index_search_type", "1");
            assertEquals(VersionConversion.Status.SAME, same.status());
            assertEquals(List.of(), same.applied());
            VersionConversion.Result older =
                    VersionConversion.between(
                                    MigrationTo202603Test.METADATA,
                                    MigrationTo202603Test.V2024_01,
                                    V2026_03)
                            .convert(
                                    "variable_mod01", "15.9949 M 0 3 -3 0 0 0.0", Set.of(DISTANCE));
            assertEquals(Optional.of("15.9949 M 0 3 -1 0 0 0.0"), older.targetText());
            assertEquals(MigrationTo202603Test.V2024_01, older.applied().get(0).from());
        }
    }

    @Nested
    @DisplayName("a CONSTRUCTED target with a reordered tuple and its own entries")
    class Constructed {

        private static final ToolVersion TARGET = ToolVersion.parse("2099.01.0");

        private static String entry(String fields) {
            return "{\"from\": \"2026.02.2\", "
                    + fields
                    + ", \"reason\": \"Constructed "
                    + "reason.\", \"source\": \"https://example.org/constructed\"}";
        }

        private final CuratedMetadata metadata =
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
                                ConstructedVersions.field("REQUIRED", "INTEGER", false)),
                        "",
                        String.join(
                                ", ",
                                entry(
                                        "\"rule\": \""
                                                + DISTANCE
                                                + "\", \"action\": \"CONVERT\", \"field\":"
                                                + " \"TERMINAL_DISTANCE\", \"becomes\": \"-1\""),
                                entry(
                                        "\"rule\": \""
                                                + NEGATIVE_GROUP
                                                + "\", \"action\": \"NOTICE\""),
                                entry(
                                        "\"parameter\": \"index_search_type\", \"value\": \"0\","
                                                + " \"action\": \"NOTICE\"")));

        private final VersionConversion conversion =
                VersionConversion.between(metadata, V2026_02, TARGET);

        @Test
        @DisplayName("a conversion and a notice on one slot: CONVERTED, both reasons, re-laid-out")
        void convertAndNotice() {
            VersionConversion.Result result =
                    conversion.convert(
                            "variable_mod01",
                            "15.9949 M -1 3 -3 0 0 0.0",
                            Set.of(DISTANCE, NEGATIVE_GROUP));
            assertEquals(VersionConversion.Status.CONVERTED, result.status());
            assertEquals(Optional.of("15.9949 M -1 3 -1 0 0.0 0"), result.targetText());
            assertEquals(
                    "variable_mod01 = 15.9949 M -1 3 -3 0 0 0.0 (Comet 2026.02.2) is written"
                            + " 15.9949 M -1 3 -1 0 0.0 0 for Comet 2099.01.0, which means the same"
                            + " there: Constructed reason. (https://example.org/constructed);"
                            + " Constructed reason. (https://example.org/constructed)",
                    result.explanation());
            assertEquals(2, result.applied().size());
        }

        @Test
        @DisplayName("a notice on a re-laid-out slot: CONVERTED with the notice, no rewrite")
        void noticeAndReshape() {
            VersionConversion.Result result =
                    conversion.convert(
                            "variable_mod01", "15.9949 M -1 3 -1 0 0 0.0", Set.of(NEGATIVE_GROUP));
            assertEquals(VersionConversion.Status.CONVERTED, result.status());
            assertEquals(Optional.of("15.9949 M -1 3 -1 0 0.0 0"), result.targetText());
            assertEquals(
                    "variable_mod01 = 15.9949 M -1 3 -1 0 0 0.0 (Comet 2026.02.2) is written"
                            + " 15.9949 M -1 3 -1 0 0.0 0 for Comet 2099.01.0, with the same"
                            + " meaning; Constructed reason. (https://example.org/constructed)",
                    result.explanation());
        }

        @Test
        @DisplayName("through migration: CONVERTED, RESHAPED with its notice, and NOTED")
        void throughMigration() {
            CometParameters source =
                    new org.cometgui.params.comet.parser.CometParamsParser(metadata, V2026_02)
                            .parse(Sets.newerText())
                            .model()
                            .orElseThrow()
                            .withText(
                                    "variable_mod01", "15.9949 M -1 3 -3 0 0 0.0", ValueOrigin.USER)
                            .withText(
                                    "variable_mod02",
                                    "79.966331 STY -1 3 -1 0 0 0.0",
                                    ValueOrigin.USER)
                            .withText("index_search_type", "0", ValueOrigin.USER);
            MigrationReport report = SchemaMigration.migrate(source, TARGET).report();
            MigrationEntry first = report.entry("variable_mod01").orElseThrow();
            assertEquals(MigrationEntry.Outcome.CONVERTED, first.outcome());
            assertEquals("15.9949 M -1 3 -1 0 0.0 0", first.targetText());
            MigrationEntry second = report.entry("variable_mod02").orElseThrow();
            assertEquals(MigrationEntry.Outcome.RESHAPED, second.outcome());
            assertEquals(
                    "variable_mod02 = 79.966331 STY -1 3 -1 0 0 0.0 (Comet 2026.02.2) is written"
                            + " 79.966331 STY -1 3 -1 0 0.0 0 for Comet 2099.01.0, with the same"
                            + " meaning; Constructed reason. (https://example.org/constructed)",
                    second.explanation());
            assertEquals(
                    MigrationEntry.Outcome.NOTED,
                    report.entry("index_search_type").orElseThrow().outcome());
            assertEquals(
                    MigrationEntry.Outcome.RESHAPED,
                    report.entry("variable_mod03").orElseThrow().outcome());
            assertEquals(
                    MigrationEntry.Outcome.CARRIED,
                    report.entry("num_threads").orElseThrow().outcome());
        }
    }
}
