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

package org.cometgui.ui.viewmodel.params;

import static org.cometgui.ui.viewmodel.params.Sessions.C02;
import static org.cometgui.ui.viewmodel.params.Sessions.C03;
import static org.cometgui.ui.viewmodel.params.Sessions.METADATA;
import static org.cometgui.ui.viewmodel.params.Sessions.startingIn;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.parser.CometParamsParser;
import org.cometgui.params.comet.parser.ReleaseDefaults;
import org.cometgui.params.comet.schema.ParameterCategory;
import org.cometgui.params.comet.validation.Finding;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** The validation summary and run readiness follow the session's one report and nothing else. */
class SummaryAndReadinessTest {

    @Nested
    @DisplayName("the validation summary")
    class Summary {

        @Test
        @DisplayName("Comet's defaults with the outputs on: nothing to report")
        void clean() {
            ValidationSummaryViewModel summary = new ValidationSummaryViewModel(startingIn(C03));
            assertEquals(List.of(), summary.entries());
            assertEquals(0, summary.errorCount());
            assertEquals(0, summary.warningCount());
            assertEquals("No errors or warnings.", summary.headline());
        }

        @Test
        @DisplayName("an error and a warning: the report's entries, in its order, with counts")
        void entries() {
            ParameterSession session = startingIn(C03);
            ValidationSummaryViewModel summary = new ValidationSummaryViewModel(session);

            session.edit("num_enzyme_termini", "3");
            session.edit("peptide_mass_tolerance_lower", "5.0");

            List<SummaryEntry> entries = summary.entries();
            assertEquals(
                    session.report().findings(),
                    entries.stream().map(e -> e.finding().orElseThrow()).toList());
            assertEquals(2, entries.size());
            assertEquals(1, summary.errorCount());
            assertEquals(1, summary.warningCount());
            assertEquals(1, summary.errorCountProperty().get());
            assertEquals(1, summary.warningCountProperty().get());
            assertEquals("1 error and 1 warning.", summary.headline());
            assertEquals(summary.headline(), summary.headlineProperty().get());
            assertEquals(entries, summary.entriesProperty().get());

            SummaryEntry termini = only(entries, "num_enzyme_termini");
            assertEquals("Error", termini.severityText());
            assertEquals(Optional.of(ParameterCategory.DIGESTION_ENZYMES), termini.category());
            assertEquals(Optional.of("num_enzyme_termini"), termini.focusTarget());
            assertEquals(Optional.of("Enzymatic termini"), termini.parameterDisplayName());
            assertEquals(
                    "Error -- Enzymatic termini (num_enzyme_termini), Digestion and enzymes: "
                            + termini.message(),
                    termini.text());
            assertEquals(termini.finding().orElseThrow().message(), termini.message());
            assertEquals(SummaryEntry.Kind.ERROR, termini.kind());

            SummaryEntry window = only(entries, "peptide_mass_tolerance_lower");
            assertEquals("Warning", window.severityText());
            assertEquals(Optional.of(ParameterCategory.PRECURSOR_MASS), window.category());
            assertEquals(Optional.of("peptide_mass_tolerance_lower"), window.focusTarget());

            session.edit("num_enzyme_termini", "2");
            assertEquals("0 errors and 1 warning.", summary.headline());
        }

        @Test
        @DisplayName("an imported unknown parameter has no field to focus and no category")
        void unknownParameter() {
            String text =
                    new String(ReleaseDefaults.bundledFile(C03), StandardCharsets.UTF_8)
                            .replace(
                                    "[COMET_ENZYME_INFO]",
                                    "ms1_mass_range = 0.0 0.0\n[COMET_ENZYME_INFO]");
            CometParameters imported =
                    new CometParamsParser(METADATA, C03).parse(text).model().orElseThrow();
            ParameterSession session = startingIn(C03);
            ValidationSummaryViewModel summary = new ValidationSummaryViewModel(session);

            session.adopt(imported, Adoption.IMPORTED);

            SummaryEntry entry = only(summary.entries(), "ms1_mass_range");
            assertEquals(Optional.empty(), entry.parameterDisplayName());
            assertEquals(Optional.empty(), entry.focusTarget());
            assertEquals(Optional.empty(), entry.category());
            assertEquals("Warning -- ms1_mass_range: " + entry.message(), entry.text());
        }

        @Test
        @DisplayName("a summary made after an error shows it at once")
        void madeLate() {
            ParameterSession session = startingIn(C03);
            session.edit("num_enzyme_termini", "3");
            ValidationSummaryViewModel summary = new ValidationSummaryViewModel(session);
            assertEquals(1, summary.entries().size());
            assertEquals("1 error and 0 warnings.", summary.headline());
        }

        @Test
        @DisplayName("the headline counts in words")
        void headlines() {
            assertEquals("No errors or warnings.", ValidationSummaryViewModel.headlineFor(0, 0, 0));
            assertEquals(
                    "1 error and 0 warnings.", ValidationSummaryViewModel.headlineFor(0, 1, 0));
            assertEquals(
                    "2 errors and 3 warnings.", ValidationSummaryViewModel.headlineFor(0, 2, 3));
            assertEquals(
                    "1 edit not applied, 0 errors and 0 warnings.",
                    ValidationSummaryViewModel.headlineFor(1, 0, 0));
            assertEquals(
                    "2 edits not applied, 1 error and 1 warning.",
                    ValidationSummaryViewModel.headlineFor(2, 1, 1));
        }

        private SummaryEntry only(List<SummaryEntry> entries, String parameter) {
            List<SummaryEntry> matching =
                    entries.stream()
                            .filter(e -> e.parameter().equals(Optional.of(parameter)))
                            .toList();
            assertEquals(1, matching.size(), () -> "entries: " + entries);
            return matching.get(0);
        }
    }

    @Nested
    @DisplayName("run readiness (P7-6, gate item 6's parameter half)")
    class Readiness {

        @Test
        @DisplayName(
                "until the pre-run check has answered, the engine's half says so and Run is"
                        + " disabled")
        void engineNotChecked() {
            RunReadinessViewModel readiness = new RunReadinessViewModel(startingIn(C03));
            assertFalse(readiness.parametersBlockRun());
            assertEquals(List.of(), readiness.blockingReasons());
            assertFalse(readiness.runEnabled());
            assertFalse(readiness.runEnabledProperty().get());
            assertEquals(
                    List.of(
                            "The pre-run check has not run yet, so the workflow engine has not"
                                    + " said whether it can run this search."),
                    readiness.engineReasons());
            assertEquals(RunReadinessViewModel.ENGINE_NOT_CHECKED, readiness.reasonsText());
        }

        @Test
        @DisplayName(
                "the engine's reasons follow the parameters' in the text, and only no reason"
                        + " from either half enables Run")
        void engineReasonsJoinTheParameters() {
            ParameterSession session = startingIn(C03);
            RunReadinessViewModel readiness = new RunReadinessViewModel(session);
            readiness.showEngineReasons(List.of("no Comet", "no spectra"));
            assertFalse(readiness.runEnabled());
            assertEquals(List.of("no Comet", "no spectra"), readiness.engineReasons());
            assertEquals(
                    List.of("no Comet", "no spectra"), readiness.engineReasonsProperty().get());
            assertEquals("no Comet\nno spectra", readiness.reasonsText());

            session.edit("peptide_mass_tolerance_lower", "30.0");
            String reason =
                    "Error -- Precursor tolerance, lower bound (peptide_mass_tolerance_lower),"
                            + " Precursor mass and isotope handling: "
                            + session.report().errors().get(0).message();
            assertEquals(reason + "\nno Comet\nno spectra", readiness.reasonsText());

            readiness.showEngineReasons(List.of());
            assertFalse(readiness.runEnabled(), "the parameters still block");
            assertEquals(reason, readiness.reasonsText());

            session.edit("peptide_mass_tolerance_lower", "-20.0");
            assertTrue(readiness.runEnabled());
            assertEquals("Ready to run.", readiness.reasonsText());

            readiness.showEngineReasons(List.of("a run is in progress"));
            assertFalse(readiness.runEnabled(), "the engine alone blocks");
            assertFalse(readiness.parametersBlockRun());
        }

        @Test
        @DisplayName("an error blocks Run with its reason in text; fixing it unblocks")
        void errorBlocks() {
            ParameterSession session = startingIn(C03);
            RunReadinessViewModel readiness = new RunReadinessViewModel(session);
            RunReadinessViewModel engineReady = engineReady(session);
            assertTrue(engineReady.runEnabled());
            assertEquals("Ready to run.", engineReady.reasonsText());

            // CONSTRUCTED cross-parameter error: the precursor window reversed
            session.edit("peptide_mass_tolerance_lower", "30.0");

            Finding error = session.report().errors().get(0);
            String reason =
                    "Error -- Precursor tolerance, lower bound (peptide_mass_tolerance_lower),"
                            + " Precursor mass and isotope handling: "
                            + error.message();
            assertTrue(readiness.parametersBlockRun());
            assertTrue(readiness.parametersBlockRunProperty().get());
            assertEquals(List.of(reason), readiness.blockingReasons());
            assertEquals(List.of(reason), readiness.blockingReasonsProperty().get());
            assertEquals(
                    reason + "\n" + RunReadinessViewModel.ENGINE_NOT_CHECKED,
                    readiness.reasonsText());
            assertEquals(readiness.reasonsText(), readiness.reasonsTextProperty().get());
            assertFalse(engineReady.runEnabled());
            assertEquals(reason, engineReady.reasonsText());

            session.edit("peptide_mass_tolerance_lower", "-20.0");
            assertFalse(readiness.parametersBlockRun());
            assertTrue(engineReady.runEnabled());
        }

        @Test
        @DisplayName("a warning alone does not block Run")
        void warningDoesNotBlock() {
            ParameterSession session = startingIn(C02);
            RunReadinessViewModel readiness = engineReady(session);
            session.edit("peptide_mass_tolerance_lower", "5.0");
            assertFalse(session.report().warnings().isEmpty());
            assertFalse(readiness.parametersBlockRun());
            assertTrue(readiness.runEnabled());
        }

        @Test
        @DisplayName("an unresolved migration entry blocks Run until resolved")
        void migrationBlocks() {
            ParameterSession session = startingIn(C02);
            RunReadinessViewModel readiness = engineReady(session);
            session.edit("variable_mod01", "15.9949 M 0 3 2 4 0 0.0");
            session.selectRelease(C03);
            assertTrue(readiness.parametersBlockRun());
            assertEquals(1, readiness.blockingReasons().size());
            assertTrue(
                    readiness
                            .blockingReasons()
                            .get(0)
                            .startsWith(
                                    "Error -- Variable modification 1 (variable_mod01),"
                                            + " Variable modifications: variable_mod01 needs"
                                            + " your decision:"),
                    readiness.blockingReasons().get(0));
            session.resolve("variable_mod01");
            assertFalse(readiness.parametersBlockRun());
            assertTrue(readiness.runEnabled());
        }

        @Test
        @DisplayName("an engine that cannot run has to say why")
        void blankEngineReason() {
            ParameterSession session = startingIn(C03);
            assertEquals(
                    "a workflow engine that cannot run has to say why: a blank reason leaves the"
                            + " Run control disabled with no explanation",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () ->
                                            new RunReadinessViewModel(session)
                                                    .showEngineReasons(List.of("fine", " ")))
                            .getMessage());
        }
    }

    @Nested
    @DisplayName("edits the model refused: shown, not applied, so they block Run")
    class RefusedEdits {

        private static final String THREADS_REASON =
                "Not applied -- Search threads (num_threads), CPU and execution: num_threads,"
                        + " value: \"lots\" is not a whole number";

        private static final String CLEAVAGE_REASON =
                "Not applied -- Allowed missed cleavages (allowed_missed_cleavage), Digestion and"
                        + " enzymes: allowed_missed_cleavage, value: \"many\" is not a whole"
                        + " number";

        private final ParameterSession session = startingIn(C03);

        private final RunReadinessViewModel readiness = engineReady(session);

        private final ValidationSummaryViewModel summary = new ValidationSummaryViewModel(session);

        @Test
        @DisplayName("a refused edit blocks Run with its field and the refusal in text")
        void refusedEditBlocks() {
            assertTrue(readiness.runEnabled());

            session.edit("num_threads", "lots");

            assertEquals(List.of(), session.report().findings());
            assertTrue(readiness.parametersBlockRun());
            assertFalse(readiness.runEnabled());
            assertEquals(List.of(THREADS_REASON), readiness.blockingReasons());
            assertEquals(THREADS_REASON, readiness.reasonsText());
            assertEquals(1, summary.entries().size());
            SummaryEntry entry = summary.entries().get(0);
            assertEquals(SummaryEntry.Kind.NOT_APPLIED, entry.kind());
            assertEquals("Not applied", entry.severityText());
            assertEquals(Optional.empty(), entry.finding());
            assertEquals(Optional.of("num_threads"), entry.focusTarget());
            assertEquals(Optional.of(ParameterCategory.CPU_EXECUTION), entry.category());
            assertEquals(THREADS_REASON, entry.text());
            assertEquals(1, summary.notAppliedCount());
            assertEquals(1, summary.notAppliedCountProperty().get());
            assertEquals("1 edit not applied, 0 errors and 0 warnings.", summary.headline());
            assertEquals(
                    List.of(session.field("num_threads")), session.pendingRefusalsProperty().get());
        }

        @Test
        @DisplayName("an accepted edit of the field unblocks")
        void acceptedEditUnblocks() {
            session.edit("num_threads", "lots");
            session.edit("num_threads", "4");
            assertFalse(readiness.parametersBlockRun());
            assertTrue(readiness.runEnabled());
            assertEquals(List.of(), readiness.blockingReasons());
            assertEquals("Ready to run.", readiness.reasonsText());
            assertEquals(List.of(), summary.entries());
            assertEquals(0, summary.notAppliedCount());
            assertEquals("No errors or warnings.", summary.headline());
        }

        @Test
        @DisplayName("a reset of the field unblocks")
        void resetUnblocks() {
            session.edit("num_threads", "lots");
            session.field("num_threads").reset();
            assertFalse(readiness.parametersBlockRun());
            assertTrue(readiness.runEnabled());
            assertEquals(List.of(), summary.entries());
        }

        @Test
        @DisplayName("two refused fields are two reasons, in field order, before the report's")
        void twoInFieldOrder() {
            session.edit("allowed_missed_cleavage", "many");
            session.edit("num_threads", "lots");
            session.edit("num_enzyme_termini", "3");

            Finding termini = session.report().errors().get(0);
            String terminiReason =
                    "Error -- Enzymatic termini (num_enzyme_termini), Digestion and enzymes: "
                            + termini.message();
            assertEquals(
                    List.of(THREADS_REASON, CLEAVAGE_REASON, terminiReason),
                    readiness.blockingReasons());
            assertEquals(
                    List.of("num_threads", "allowed_missed_cleavage", "num_enzyme_termini"),
                    summary.entries().stream().map(e -> e.parameter().orElseThrow()).toList());
            assertEquals("2 edits not applied, 1 error and 0 warnings.", summary.headline());

            session.edit("num_enzyme_termini", "2");
            assertEquals(List.of(THREADS_REASON, CLEAVAGE_REASON), readiness.blockingReasons());
            assertTrue(readiness.parametersBlockRun());
        }

        @Test
        @DisplayName("an adopted set and a release change clear every refusal")
        void adoptAndReleaseChangeUnblock() {
            session.edit("num_threads", "lots");
            session.adopt(session.model(), Adoption.RAW_APPLIED);
            assertFalse(readiness.parametersBlockRun());

            session.edit("num_threads", "lots");
            assertTrue(readiness.parametersBlockRun());
            assertEquals(EditOutcome.applied(), session.selectRelease(C02));
            assertEquals(List.of(), session.pendingRefusals());
            assertFalse(readiness.parametersBlockRun());
            assertEquals(List.of(), summary.entries());
        }

        @Test
        @DisplayName("an entry's kind must match what it holds")
        void kindMatches() {
            session.edit("num_threads", "lots");
            SummaryEntry notApplied = SummaryEntry.notApplied(session.field("num_threads"));
            assertEquals(
                    "a summary entry of kind ERROR must be of kind NOT_APPLIED",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () ->
                                            new SummaryEntry(
                                                    SummaryEntry.Kind.ERROR,
                                                    Optional.empty(),
                                                    notApplied.parameter(),
                                                    notApplied.parameterDisplayName(),
                                                    notApplied.category(),
                                                    notApplied.message()))
                            .getMessage());
            assertEquals(
                    "decoy_prefix holds no refused edit",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () -> SummaryEntry.notApplied(session.field("decoy_prefix")))
                            .getMessage());
            assertTrue(SummaryEntry.Kind.NOT_APPLIED.blocksRun());
            assertTrue(SummaryEntry.Kind.ERROR.blocksRun());
            assertFalse(SummaryEntry.Kind.WARNING.blocksRun());
        }
    }

    /** Readiness over a session whose engine half has nothing against a run. */
    private static RunReadinessViewModel engineReady(ParameterSession session) {
        RunReadinessViewModel readiness = new RunReadinessViewModel(session);
        readiness.showEngineReasons(List.of());
        return readiness;
    }
}
