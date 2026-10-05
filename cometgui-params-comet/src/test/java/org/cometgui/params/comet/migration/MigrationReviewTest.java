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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.fixtures.CometFixtures;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.model.ValueOrigin;
import org.cometgui.params.comet.schema.ParameterCategory;
import org.cometgui.params.comet.validation.CometValidator;
import org.cometgui.params.comet.validation.Finding;
import org.cometgui.params.comet.validation.Rule;
import org.cometgui.params.comet.validation.Severity;
import org.cometgui.params.comet.validation.ValidationReport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link MigrationReview}: an unresolved {@code NEEDS_ATTENTION} entry is an error in the one
 * validation report ({@code R-PARAM-13}, Phase 07 decision P7-3), and resolving it clears exactly
 * its finding.
 *
 * <p>The sources are the REAL Comet 2026.02.2 {@code -q} file, with CONSTRUCTED one-line edits
 * (test input, not Comet's output) where a value needing attention is wanted. Every expected
 * finding and message is typed here by hand.
 */
class MigrationReviewTest {

    static final ToolVersion V2026_03 = ToolVersion.parse(CometFixtures.COMET_2026_03_0);

    /** The real 2026.02.2 {@code -q} file with one line replaced. */
    String edited(String name, String value) {
        return Sets.replacing(Sets.newerText(), name + " =", name + " = " + value);
    }

    MigrationResult toNewest(String text) {
        return SchemaMigration.migrateFile(Sets.METADATA, text, V2026_03);
    }

    /** Each finding as severity, rule, parameters and category, for hand-typed comparison. */
    List<String> shown(ValidationReport report) {
        return report.findings().stream()
                .map(
                        f ->
                                f.severity()
                                        + " "
                                        + f.rule().id()
                                        + " "
                                        + f.parameters()
                                        + " "
                                        + f.category().map(ParameterCategory::id).orElse("-"))
                .toList();
    }

    ValidationReport plain(CometParameters model) {
        return CometValidator.standard().validate(model);
    }

    /** Typed by hand: the 2026.03.0 terminus entry's reason and source, as the record states. */
    static final String TERMINUS_REASON =
            "With a terminal distance of 0 or more, Comet 2026.02.2 matches no terminus outside"
                    + " 0-3, so this modification was never applied (searches gave the same"
                    + " results as with the slot switched off), and Comet 2026.03.0 refuses the"
                    + " setting. Choose the terminus that was meant (0 protein N, 1 protein C, 2"
                    + " peptide N, 3 peptide C), or switch the slot off."
                    + " (https://github.com/UWPR/Comet/blob/v2026.02.2/CometSearch/CometSearch.cpp"
                    + "#L5373-L5390)";

    @Nested
    @DisplayName(
            "the real 2026.02.2 file, variable_mod01 at terminus 4 and distance 2, to 2026.03.0")
    class ActiveOxidation {

        static final String SOURCE_VALUE = "15.9949 M 0 3 2 4 0 0.0";

        /** Typed by hand: 2026.03.0's default for variable_mod01, an active oxidation. */
        static final String DEFAULT = "15.9949 M 0 3 -1 0 0 0.0";

        static final String MESSAGE =
                "variable_mod01 needs your decision: migrating from Comet 2026.02.2 to Comet"
                        + " 2026.03.0 could not keep its value 15.9949 M 0 3 2 4 0 0.0 and put"
                        + " Comet 2026.03.0's default 15.9949 M 0 3 -1 0 0 0.0 in its place; the"
                        + " set now holds 15.9949 M 0 3 -1 0 0 0.0. Set a value, or accept this"
                        + " one, before running. Why: variable_mod01 = 15.9949 M 0 3 2 4 0 0.0"
                        + " (Comet 2026.02.2) has no equivalent in Comet 2026.03.0: "
                        + TERMINUS_REASON
                        + "; the migrated set holds Comet 2026.03.0's default instead, and the"
                        + " source value is kept only in this report";

        private final MigrationResult result = toNewest(edited("variable_mod01", SOURCE_VALUE));

        private final MigrationReview review = MigrationReview.of(result);

        private final CometParameters model = result.model();

        @Test
        @DisplayName("the review holds exactly that entry, and the set holds the active default")
        void theEntry() {
            assertEquals(
                    List.of("variable_mod01"),
                    review.report().needingAttention().stream()
                            .map(MigrationEntry::parameter)
                            .toList());
            assertEquals(Set.of(), review.acknowledged());
            assertSame(result, review.result());
            assertSame(result.report(), review.report());
            assertEquals(DEFAULT, model.text("variable_mod01"));
            assertEquals(ValueOrigin.COMET_DEFAULT, model.origin("variable_mod01"));
            assertEquals(
                    List.of("variable_mod01"),
                    review.unresolved(model).stream().map(MigrationEntry::parameter).toList());
        }

        @Test
        @DisplayName("the one report has the error at the field and its category, and blocks a run")
        void blocks() {
            ValidationReport report = review.validate(model);
            assertEquals(
                    List.of(
                            "ERROR workflow_enforced.output_off [output_percolatorfile] output",
                            "ERROR migration.needs_attention [variable_mod01] variable_mods"),
                    shown(report));
            assertTrue(report.hasErrors());
            Finding finding =
                    new Finding(
                            Rule.MIGRATION_NEEDS_ATTENTION,
                            Severity.ERROR,
                            List.of("variable_mod01"),
                            Optional.of(ParameterCategory.VARIABLE_MODS),
                            MESSAGE);
            assertEquals(MESSAGE, report.forParameter("variable_mod01").get(0).message());
            assertEquals(List.of(finding), report.forParameter("variable_mod01"));
            assertEquals(List.of(finding), report.forCategory(ParameterCategory.VARIABLE_MODS));
            assertEquals(List.of(finding), report.of(Rule.MIGRATION_NEEDS_ATTENTION));
            assertTrue(report.errors().contains(finding));
            assertFalse(report.warnings().contains(finding));
        }

        @Test
        @DisplayName("with the workflow's outputs on, the entry is the one thing that blocks a run")
        void theOnlyError() {
            CometParameters enforced = model.withWorkflowEnforcedOutputs();
            assertEquals(List.of(), plain(enforced).findings());
            ValidationReport report = review.validate(enforced);
            assertEquals(
                    List.of("ERROR migration.needs_attention [variable_mod01] variable_mods"),
                    shown(report));
            assertTrue(report.hasErrors());
            ValidationReport resolved = review.resolve("variable_mod01").validate(enforced);
            assertEquals(List.of(), resolved.findings());
            assertFalse(resolved.hasErrors());
        }

        @Test
        @DisplayName("acknowledging clears exactly its finding; the rest is the plain validator's")
        void acknowledged() {
            MigrationReview resolved = review.resolve("variable_mod01");
            assertEquals(Set.of("variable_mod01"), resolved.acknowledged());
            assertEquals(Set.of(), review.acknowledged(), "the first review is unchanged");
            assertEquals(1, review.validate(model).of(Rule.MIGRATION_NEEDS_ATTENTION).size());
            ValidationReport report = resolved.validate(model);
            assertEquals(plain(model), report);
            assertEquals(
                    List.of("ERROR workflow_enforced.output_off [output_percolatorfile] output"),
                    shown(report));
            assertTrue(report.hasErrors(), "the rest still has its own error");
            assertEquals(List.of(), resolved.unresolved(model));
            assertEquals(DEFAULT, model.text("variable_mod01"), "accepting changes no value");
        }

        @Test
        @DisplayName("a value the scientist sets resolves it; a reset or a preset does not")
        void setByTheScientist() {
            CometParameters chosen =
                    model.withText("variable_mod01", "15.9949 M 0 3 2 3 0 0.0", ValueOrigin.USER);
            assertEquals(plain(chosen), review.validate(chosen));
            assertEquals(List.of(), review.unresolved(chosen));
            CometParameters kept = model.withText("variable_mod01", DEFAULT, ValueOrigin.USER);
            assertEquals(plain(kept), review.validate(kept));
            CometParameters reset = chosen.resetToDefault("variable_mod01");
            assertEquals(
                    List.of("variable_mod01"),
                    review.validate(reset).of(Rule.MIGRATION_NEEDS_ATTENTION).stream()
                            .map(f -> f.parameters().get(0))
                            .toList());
            for (ValueOrigin origin :
                    List.of(
                            ValueOrigin.PRESET,
                            ValueOrigin.IMPORTED,
                            ValueOrigin.COMET_DEFAULT,
                            ValueOrigin.WORKFLOW_ENFORCED)) {
                CometParameters other = model.withText("variable_mod01", DEFAULT, origin);
                assertEquals(
                        1,
                        review.validate(other).of(Rule.MIGRATION_NEEDS_ATTENTION).size(),
                        origin.name());
            }
        }

        @Test
        @DisplayName("the message names the value the set holds now, not only the default")
        void namesTheCurrentValue() {
            CometParameters preset =
                    model.withText("variable_mod01", "0.0 X 0 3 -1 0 0 0.0", ValueOrigin.PRESET);
            String message =
                    review.validate(preset).of(Rule.MIGRATION_NEEDS_ATTENTION).get(0).message();
            assertTrue(
                    message.contains(
                            "put Comet 2026.03.0's default 15.9949 M 0 3 -1 0 0 0.0 in its place;"
                                    + " the set now holds 0.0 X 0 3 -1 0 0 0.0. Set a value"),
                    message);
        }
    }

    @Nested
    @DisplayName("two entries needing attention")
    class TwoEntries {

        private final MigrationResult result =
                toNewest(
                        Sets.replacing(
                                edited("variable_mod01", "15.9949 M 0 3 2 4 0 0.0"),
                                "variable_mod02 =",
                                "variable_mod02 = 42.010565 n 0 1 0 5 0 0.0"));

        private final MigrationReview review = MigrationReview.of(result);

        private final CometParameters model = result.model().withWorkflowEnforcedOutputs();

        @Test
        @DisplayName("each is an error, in report order, after every finding of the validator")
        void bothInOrder() {
            assertEquals(
                    List.of(
                            "ERROR migration.needs_attention [variable_mod01] variable_mods",
                            "ERROR migration.needs_attention [variable_mod02] variable_mods"),
                    shown(review.validate(model)));
            CometParameters warned =
                    model.withText("index_search_type", "0", ValueOrigin.USER)
                            .withText("database_name", "/data/human.fasta", ValueOrigin.USER);
            assertEquals(
                    List.of(
                            "WARNING index_search_type.ignored_without_idx [index_search_type,"
                                    + " database_name] fragment_index",
                            "ERROR migration.needs_attention [variable_mod01] variable_mods",
                            "ERROR migration.needs_attention [variable_mod02] variable_mods"),
                    shown(review.validate(warned)));
        }

        @Test
        @DisplayName("resolving one clears exactly its finding, and the other stays")
        void exactlyOne() {
            assertEquals(
                    List.of("ERROR migration.needs_attention [variable_mod01] variable_mods"),
                    shown(review.resolve("variable_mod02").validate(model)));
            assertEquals(
                    List.of("ERROR migration.needs_attention [variable_mod02] variable_mods"),
                    shown(review.resolve("variable_mod01").validate(model)));
            CometParameters chosen =
                    model.withText("variable_mod02", "0.0 X 0 3 -1 0 0 0.0", ValueOrigin.USER);
            assertEquals(
                    List.of("ERROR migration.needs_attention [variable_mod01] variable_mods"),
                    shown(review.validate(chosen)));
            MigrationReview both = review.resolve("variable_mod02").resolve("variable_mod01");
            assertEquals(Set.of("variable_mod01", "variable_mod02"), both.acknowledged());
            assertEquals(List.of(), both.validate(model).findings());
        }
    }

    @Nested
    @DisplayName("the real 2026.02.2 file with two neutral losses, back to 2024.01.0")
    class TwoLosses {

        static final String PAIRED = "79.966331 STY 0 3 -1 0 0 97.976896,79.966331";

        private final MigrationResult result =
                SchemaMigration.migrate(
                        Sets.newer().withText("variable_mod02", PAIRED, ValueOrigin.USER),
                        Sets.OLDER);

        private final MigrationReview review = MigrationReview.of(result);

        @Test
        @DisplayName("the entry is an error at variable_mod02 after the validator's own findings")
        void blocks() {
            CometParameters model = result.model();
            assertEquals(ValueOrigin.COMET_DEFAULT, model.origin("variable_mod02"));
            ValidationReport report = review.validate(model);
            List<String> expected =
                    List.of(
                            "ERROR workflow_enforced.output_off [output_percolatorfile] output",
                            "ERROR version.parameter_unavailable [index_search_type] -",
                            "ERROR version.parameter_unavailable [compoundmods_file] -",
                            "ERROR version.parameter_unavailable [spectral_library_name] -",
                            "ERROR version.parameter_unavailable [spectral_library_ms_level] -",
                            "ERROR version.parameter_unavailable [protein_modslist_file] -",
                            "ERROR version.parameter_unavailable [print_ascorepro_score] -",
                            "ERROR version.parameter_unavailable [pinfile_protein_delimiter] -",
                            "ERROR version.parameter_unavailable [min_precursor_charge] -",
                            "ERROR version.parameter_unavailable [percentage_base_peak] -",
                            "ERROR migration.needs_attention [variable_mod02] variable_mods");
            assertEquals(expected, shown(report));
            assertEquals(
                    plain(model).findings(),
                    report.findings().subList(0, report.findings().size() - 1));
            Finding finding = report.forParameter("variable_mod02").get(0);
            assertEquals(
                    "variable_mod02 needs your decision: migrating from Comet 2026.02.2 to Comet"
                            + " 2024.01.0 could not keep its value "
                            + PAIRED
                            + " and put Comet 2024.01.0's default 0.0 X 0 3 -1 0 0 0.0 in its"
                            + " place; the set now holds 0.0 X 0 3 -1 0 0 0.0. Set a value, or"
                            + " accept this one, before running. Why: variable_mod02 = "
                            + PAIRED
                            + " (Comet 2026.02.2) cannot be written for Comet 2024.01.0: "
                            + TWO_LOSS_REASON
                            + "; the migrated set holds Comet 2024.01.0's default instead, and"
                            + " the source value is kept only in this report",
                    finding.message());
            assertEquals(plain(model), review.resolve("variable_mod02").validate(model));
        }
    }

    /** Typed by hand: what Comet 2024.01.0's one-loss tuple codec says about a pair. */
    static final String TWO_LOSS_REASON =
            "this Comet version's neutral loss field takes one value, so two neutral losses"
                    + " cannot be written under it";

    @Nested
    @DisplayName("a migration with nothing needing attention")
    class NothingNeedsAttention {

        @Test
        @DisplayName("the report is exactly the plain validator's, for both older real files")
        void plainReport() {
            MigrationResult fromFebruary = toNewest(Sets.newerText());
            MigrationResult from2024 = toNewest(Sets.olderText());
            for (MigrationResult result : List.of(fromFebruary, from2024)) {
                assertEquals(List.of(), result.report().needingAttention());
                ValidationReport report = MigrationReview.of(result).validate(result.model());
                assertEquals(plain(result.model()), report);
                assertEquals(
                        List.of(
                                "ERROR workflow_enforced.output_off [output_percolatorfile]"
                                        + " output"),
                        shown(report));
                CometParameters enforced = result.model().withWorkflowEnforcedOutputs();
                assertEquals(List.of(), MigrationReview.of(result).validate(enforced).findings());
                assertEquals(List.of(), MigrationReview.of(result).unresolved(enforced));
            }
        }
    }

    @Nested
    @DisplayName("refusals")
    class Refusals {

        private final MigrationResult result =
                toNewest(edited("variable_mod01", "15.9949 M 0 3 2 4 0 0.0"));

        private final MigrationReview review = MigrationReview.of(result);

        @Test
        @DisplayName("a name that is no entry, or an entry that needs no attention, is refused")
        void notAnEntry() {
            IllegalArgumentException unknown =
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> assertNotNull(review.resolve("no_such_name")));
            assertEquals(
                    "no_such_name is not an entry of the migration from Comet 2026.02.2 to Comet"
                            + " 2026.03.0 that needs attention, so there is nothing to resolve",
                    unknown.getMessage());
            IllegalArgumentException converted =
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> assertNotNull(review.resolve("index_search_type")));
            assertEquals(
                    "index_search_type is not an entry of the migration from Comet 2026.02.2 to"
                            + " Comet 2026.03.0 that needs attention, so there is nothing to"
                            + " resolve",
                    converted.getMessage());
            IllegalArgumentException built =
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> new MigrationReview(result, Set.of("database_name")));
            assertEquals(
                    "database_name is not an entry of the migration from Comet 2026.02.2 to Comet"
                            + " 2026.03.0 that needs attention, so there is nothing to resolve",
                    built.getMessage());
        }

        @Test
        @DisplayName("an entry already resolved is refused, naming it")
        void twice() {
            MigrationReview once = review.resolve("variable_mod01");
            IllegalArgumentException again =
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> assertNotNull(once.resolve("variable_mod01")));
            assertEquals(
                    "variable_mod01 has already been resolved in this migration's review",
                    again.getMessage());
        }

        @Test
        @DisplayName("a set of another release is refused")
        void anotherRelease() {
            IllegalArgumentException other =
                    assertThrows(
                            IllegalArgumentException.class, () -> review.validate(result.source()));
            assertEquals(
                    "this review is of a migration to Comet 2026.03.0, not of a Comet 2026.02.2"
                            + " set",
                    other.getMessage());
            assertThrows(IllegalArgumentException.class, () -> review.unresolved(result.source()));
        }

        @Test
        @DisplayName("the acknowledged names are an immutable copy")
        void immutable() {
            Set<String> names = new HashSet<>(Set.of("variable_mod01"));
            MigrationReview built = new MigrationReview(result, names);
            names.clear();
            assertEquals(Set.of("variable_mod01"), built.acknowledged());
            assertThrows(UnsupportedOperationException.class, () -> built.acknowledged().clear());
            assertEquals(built, review.resolve("variable_mod01"));
            assertNotEquals(built, review);
        }
    }

    @Nested
    @DisplayName("a CONSTRUCTED report whose entry names a parameter the set does not model")
    class Uncategorised {

        @Test
        @DisplayName("it is attached to the name alone, with no category, as unknown names are")
        void noCategory() {
            CometParameters source = Sets.newer();
            MigrationResult migrated = SchemaMigration.migrate(source, V2026_03);
            MigrationEntry made =
                    new MigrationEntry(
                            "made_up_parameter",
                            MigrationEntry.Outcome.NEEDS_ATTENTION,
                            Optional.of("7"),
                            "3",
                            "made_up_parameter = 7 cannot be kept");
            MigrationResult constructed =
                    new MigrationResult(
                            source,
                            migrated.model(),
                            new MigrationReport(Sets.NEWER, V2026_03, List.of(made)));
            CometParameters model = migrated.model().withWorkflowEnforcedOutputs();
            ValidationReport report = MigrationReview.of(constructed).validate(model);
            assertEquals(
                    List.of(
                            new Finding(
                                    Rule.MIGRATION_NEEDS_ATTENTION,
                                    Severity.ERROR,
                                    List.of("made_up_parameter"),
                                    Optional.empty(),
                                    "made_up_parameter needs your decision: migrating from Comet"
                                            + " 2026.02.2 to Comet 2026.03.0 could not keep its"
                                            + " value 7 and put Comet 2026.03.0's default 3 in"
                                            + " its place; the set now holds 3. Set a value, or"
                                            + " accept this one, before running. Why:"
                                            + " made_up_parameter = 7 cannot be kept")),
                    report.findings());
            assertEquals(
                    List.of(),
                    MigrationReview.of(constructed)
                            .resolve("made_up_parameter")
                            .validate(model)
                            .findings());
        }
    }
}
