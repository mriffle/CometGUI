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

import static org.cometgui.ui.viewmodel.params.Sessions.BUILD;
import static org.cometgui.ui.viewmodel.params.Sessions.C02;
import static org.cometgui.ui.viewmodel.params.Sessions.C03;
import static org.cometgui.ui.viewmodel.params.Sessions.startingIn;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.model.Diagnostic;
import org.cometgui.params.comet.model.ValueOrigin;
import org.cometgui.params.comet.parser.ParamsHighlighting.LineKind;
import org.cometgui.params.comet.parser.ParamsHighlighting.Token;
import org.cometgui.params.comet.parser.ParamsHighlighting.TokenKind;
import org.cometgui.params.comet.presets.DiffRow;
import org.cometgui.params.comet.presets.PresetLoader;
import org.cometgui.params.comet.validation.Finding;
import org.cometgui.params.comet.validation.Rule;
import org.cometgui.params.comet.validation.Severity;
import org.cometgui.provenance.hashing.StreamingHashService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Expert mode (exit gate item 4, {@code AC-PAR-07}, {@code R-PARAM-08}). The canonical text's line
 * numbers are fixed by the writer's layout -- a four-line header, then the parameters in the
 * metadata's order -- so line 8 is {@code num_threads}, 25 {@code allowed_missed_cleavage} and 27
 * {@code variable_mod02} in both releases; every expected text below is typed by hand.
 */
class ExpertViewModelTest {

    private static final String MALFORMED_8 =
            "Error, line 8: line 8 is not a comment, a declaration or an enzyme row: not a comment,"
                    + " a blank line or a declaration: there is no '=' before any '#':"
                    + " \"num_threads 0\"";

    private static final String UNREADABLE_25 =
            "Error, line 25: line 25: allowed_missed_cleavage, value: \"lots\" is not a whole"
                    + " number";

    private static ExpertViewModel expert(ParameterSession session) {
        return new ExpertViewModel(session, BUILD, files(session));
    }

    private static ParameterFilesViewModel files(ParameterSession session) {
        return new ParameterFilesViewModel(session, BUILD, new StreamingHashService());
    }

    /** The draft with one line (1-based) replaced. */
    private static String replaceLine(String text, int number, String line) {
        String[] lines = text.split("\n", -1);
        lines[number - 1] = line;
        return String.join("\n", lines);
    }

    @Nested
    @DisplayName("the canonical text and the draft")
    class CanonicalAndDraft {

        @ParameterizedTest(name = "Comet {0}")
        @CsvSource({
            "2026.03.0, 10725, # comet_version 2026.03 rev. 0 (fa08489)",
            "2026.02.2, 10656, # comet_version 2026.02 rev. 2 (6edec91)"
        })
        @DisplayName("the canonical text is the writer's, for the selected release")
        void canonical(String release, int length, String marker) {
            ParameterSession session = startingIn(ToolVersion.parse(release));
            ExpertViewModel expert = expert(session);
            String text = expert.canonicalText();
            List<String> lines = text.lines().toList();
            assertEquals(139, lines.size());
            assertEquals(length, text.length());
            assertEquals(marker, lines.get(0));
            assertEquals(
                    "# Written by CometGUI 0.1.0-SNAPSHOT for Comet "
                            + release
                            + ". Canonical form, generated from the typed model.",
                    lines.get(1));
            assertEquals(
                    "num_threads = 0                        # 0=poll CPU to set num threads; else"
                            + " specify num threads directly (max 128)",
                    lines.get(7));
            assertTrue(text.contains("\noutput_percolatorfile = 1 "), "outputs enforced");
            assertEquals(Optional.empty(), expert.canonicalRefusal());
            assertEquals(text, expert.draft());
            assertFalse(expert.isDraftEdited());
        }

        @Test
        @DisplayName("an unedited draft follows the configuration; an edited one is kept")
        void following() {
            ParameterSession session = startingIn(C03);
            ExpertViewModel expert = expert(session);
            session.edit("num_threads", "4");
            assertTrue(expert.draft().contains("\nnum_threads = 4 "));
            assertEquals(expert.canonicalText(), expert.draftProperty().get());

            expert.setDraft("num_threads = 6\n");
            assertTrue(expert.isDraftEdited());
            session.edit("num_threads", "5");
            assertEquals("num_threads = 6\n", expert.draft());
            assertTrue(expert.canonicalTextProperty().get().contains("\nnum_threads = 5 "));

            expert.revertDraft();
            assertFalse(expert.isDraftEdited());
            assertTrue(expert.draft().contains("\nnum_threads = 5 "));
        }

        @Test
        @DisplayName("a configuration the writer refuses: the refusal, and the draft kept")
        void refused() {
            ParameterSession session = startingIn(C03);
            ExpertViewModel expert = expert(session);
            String before = expert.draft();
            session.edit("search_enzyme_number", "42");
            assertEquals("", expert.canonicalText());
            assertEquals(
                    Optional.of(
                            "search_enzyme_number = 42 names enzyme 42, which is not in the"
                                    + " [COMET_ENZYME_INFO] table being written (its numbers are"
                                    + " [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11]); the file is not"
                                    + " written"),
                    expert.canonicalRefusal());
            assertEquals(before, expert.draft());
            assertFalse(expert.isDraftEdited());
            session.edit("search_enzyme_number", "2");
            assertEquals(Optional.empty(), expert.canonicalRefusal());
            assertTrue(expert.draft().contains("\nsearch_enzyme_number = 2 "));
        }
    }

    @Nested
    @DisplayName("lines and diagnostics of the draft")
    class Lines {

        @Test
        @DisplayName("each line's kind and spans come from the model")
        void highlighting() {
            ExpertViewModel expert = expert(startingIn(C03));
            List<ExpertLine> lines = expert.lines();
            assertEquals(139, lines.size());
            assertEquals(LineKind.VERSION_MARKER, lines.get(0).line().kind());
            assertEquals(LineKind.COMMENT, lines.get(1).line().kind());
            ExpertLine threads = lines.get(7);
            assertEquals(LineKind.DECLARATION, threads.line().kind());
            assertEquals(
                    List.of(
                            new Token(TokenKind.NAME, 0, 11),
                            new Token(TokenKind.VALUE, 14, 15),
                            new Token(TokenKind.INLINE_COMMENT, 39, 115)),
                    threads.line().tokens());
            assertEquals("", threads.stateText());
            assertEquals(List.of(), threads.diagnostics());
            assertEquals(LineKind.ENZYME_HEADER, lines.get(126).line().kind());
            assertEquals(LineKind.ENZYME_ROW, lines.get(138).line().kind());
            assertEquals(List.of(), expert.diagnostics());
        }

        @Test
        @DisplayName("each line in words: number, kind and the diagnostics naming it")
        void inWords() {
            ExpertViewModel expert = expert(startingIn(C03));
            String draft = replaceLine(expert.canonicalText(), 8, "num_threads 0");
            expert.setDraft(draft);
            List<ExpertLine> lines = expert.lines();
            assertEquals(
                    List.of(
                            "Line 1, version marker",
                            "Line 2, comment",
                            "Line 8, not readable -- " + MALFORMED_8,
                            "Line 9, parameter",
                            "Line 127, enzyme table heading",
                            "Line 139, enzyme table row"),
                    List.of(
                            lines.get(0).description(),
                            lines.get(1).description(),
                            lines.get(7).description(),
                            lines.get(8).description(),
                            lines.get(126).description(),
                            lines.get(138).description()));
            expert.setDraft("num_threads = 4\n\nnum_threads = 5\n");
            List<ExpertLine> twice = expert.lines();
            assertEquals("blank", twice.get(1).kindWords());
            assertEquals("Error", twice.get(0).stateText(), "declared twice");
            assertTrue(
                    twice.get(2)
                            .description()
                            .startsWith("Line 3, parameter -- Error, lines 1, 3: "));
        }

        @Test
        @DisplayName("where a line starts in the draft, for moving the caret to it")
        void lineStart() {
            ExpertViewModel expert = expert(startingIn(C03));
            expert.setDraft("num_threads = 4\r\n\nallowed_missed_cleavage = 1\n");
            assertEquals(0, expert.lineStart(1));
            assertEquals(17, expert.lineStart(2));
            assertEquals(18, expert.lineStart(3));
            assertEquals(
                    "the draft has no line 9",
                    assertThrows(IllegalArgumentException.class, () -> expert.lineStart(9))
                            .getMessage());
            ExpertViewModel canonical = expert(startingIn(C03));
            int eight = canonical.lineStart(8);
            assertTrue(canonical.draft().startsWith("num_threads = 0 ", eight));
            assertEquals('\n', canonical.draft().charAt(eight - 1));
        }

        @ParameterizedTest(name = "Comet {0}")
        @ValueSource(strings = {"2026.03.0", "2026.02.2"})
        @DisplayName("every diagnostic with its lines; a malformed line is one of the reader's")
        void diagnostics(String release) {
            ExpertViewModel expert = expert(startingIn(ToolVersion.parse(release)));
            String draft = replaceLine(expert.canonicalText(), 8, "num_threads 0");
            draft = replaceLine(draft, 25, "allowed_missed_cleavage = lots");
            expert.setDraft(draft);
            assertEquals(List.of(MALFORMED_8, UNREADABLE_25), expert.diagnosticTexts());
            ExpertLine eight = expert.lines().get(7);
            assertEquals(LineKind.MALFORMED, eight.line().kind());
            assertEquals("Error", eight.stateText());
            assertEquals(Diagnostic.Code.MALFORMED_LINE, eight.diagnostics().get(0).code());
            assertEquals(
                    Optional.of(
                            "not a comment, a blank line or a declaration: there is no '=' before"
                                    + " any '#'"),
                    eight.line().problem());
            assertEquals("Error", expert.lines().get(24).stateText());
        }

        @Test
        @DisplayName("a ^ residue: an error at its line for 2026.02.2, accepted by 2026.03.0")
        void caretPerRelease() {
            String caret = "variable_mod02 = 42.010565 ^ 0 1 -1 0 0 0.0";
            ExpertViewModel older = expert(startingIn(C02));
            older.setDraft(replaceLine(older.canonicalText(), 27, caret));
            assertEquals(
                    List.of(
                            "Error, line 27: line 27: variable_mod02, field 2 (residues): \"^\""
                                    + " holds '^', which Comet 2026.02.2 does not accept in a"
                                    + " residue token; its residue alphabet is A-Z, n"
                                    + " (N-terminus), c (C-terminus)"),
                    older.diagnosticTexts());
            assertEquals("Error", older.lines().get(26).stateText());
            assertFalse(older.apply().accepted());
            assertEquals(1, older.applyErrors().size());

            // the same draft, after its session moved to 2026.03.0: the old failure is dropped,
            // the edited draft is kept, and 2026.03.0 reads the ^ (with the marker's warning)
            ParameterSession moved = startingIn(C02);
            ExpertViewModel movedExpert = expert(moved);
            movedExpert.setDraft(replaceLine(movedExpert.canonicalText(), 27, caret));
            assertFalse(movedExpert.apply().accepted());
            assertEquals(EditOutcome.applied(), moved.selectRelease(C03));
            assertEquals(List.of(), movedExpert.applyErrors());
            assertTrue(movedExpert.draft().contains(caret));
            assertEquals(EditOutcome.applied(), movedExpert.apply());
            assertEquals(1, movedExpert.proposal().orElseThrow().warnings().size());

            ParameterSession newer = startingIn(C03);
            ExpertViewModel expert = expert(newer);
            expert.setDraft(replaceLine(expert.canonicalText(), 27, caret));
            assertEquals(List.of(), expert.diagnosticTexts());
            assertEquals(EditOutcome.applied(), expert.apply());
            assertEquals(EditOutcome.applied(), expert.confirm());
            assertEquals("42.010565 ^ 0 1 -1 0 0 0.0", newer.model().text("variable_mod02"));
            assertEquals(ValueOrigin.USER, newer.model().origin("variable_mod02"));
        }

        @Test
        @DisplayName("a parameter declared twice: one error naming both lines, and a warning line")
        void twoLinesAndWarnings() {
            ExpertViewModel expert = expert(startingIn(C02));
            String draft =
                    replaceLine(
                            expert.canonicalText(), 1, "# comet_version 2026.03 rev. 0 (fa08489)");
            draft = replaceLine(draft, 11, draft.split("\n")[10] + "\nnum_threads = 2");
            expert.setDraft(draft);
            assertEquals(
                    List.of(
                            "Warning, line 1: line 1 says the file was written for Comet 2026.03.0"
                                    + " (\"2026.03 rev. 0 (fa08489)\"), and the selected Comet is"
                                    + " 2026.02.2 (\"2026.02 rev. 2 (6edec91)\"); the parameters"
                                    + " are read and will be written as that version's",
                            "Error, lines 8, 12: num_threads is declared on line 8 and again on"
                                    + " line 12; Comet would silently use the value on line 12, so"
                                    + " the file does not say what its first declaration says."
                                    + " Keep one of them"),
                    expert.diagnosticTexts());
            assertEquals("Warning", expert.lines().get(0).stateText());
            assertEquals("Error", expert.lines().get(11).stateText());
            assertEquals("Error", expert.lines().get(7).stateText());
        }
    }

    @Nested
    @DisplayName("apply and confirm (gate item 4, R-PARAM-08)")
    class Apply {

        @ParameterizedTest(name = "Comet {0}")
        @ValueSource(strings = {"2026.03.0", "2026.02.2"})
        @DisplayName(
                "a draft that fails to parse leaves the typed model untouched and names the lines")
        void failedParseChangesNothing(String release) {
            ParameterSession session = startingIn(ToolVersion.parse(release));
            ExpertViewModel expert = expert(session);
            CometParameters before = session.model();
            String canonicalBefore = expert.canonicalText();
            String draft = replaceLine(canonicalBefore, 8, "num_threads 0");
            draft = replaceLine(draft, 25, "allowed_missed_cleavage = lots");
            expert.setDraft(draft);

            EditOutcome outcome = expert.apply();

            assertEquals(
                    EditOutcome.refused(
                            "The draft was not applied; the configuration is unchanged.\n"
                                    + MALFORMED_8
                                    + "\n"
                                    + UNREADABLE_25),
                    outcome);
            assertSame(before, session.model());
            assertEquals(canonicalBefore, expert.canonicalText());
            assertEquals(Optional.empty(), expert.proposal());
            assertEquals(
                    List.of(Diagnostic.Code.MALFORMED_LINE, Diagnostic.Code.UNREADABLE_VALUE),
                    expert.applyErrors().stream().map(Diagnostic::code).toList());
            assertEquals(
                    List.of(8, 25),
                    expert.offendingLines().stream().map(l -> l.line().number()).toList());
            assertEquals(
                    List.of("num_threads 0", "allowed_missed_cleavage = lots"),
                    expert.offendingLines().stream().map(l -> l.line().text()).toList());
            assertEquals(
                    EditOutcome.refused(
                            "Nothing is waiting for confirmation; apply the draft first."),
                    expert.confirm());
            assertSame(before, session.model());
            assertEquals(draft, expert.draft());

            // editing the draft clears the failure
            expert.setDraft(canonicalBefore);
            assertEquals(List.of(), expert.applyErrorsProperty().get());
            assertEquals(List.of(), expert.offendingLines());
        }

        @ParameterizedTest(name = "Comet {0}")
        @ValueSource(strings = {"2026.03.0", "2026.02.2"})
        @DisplayName("a draft that parses is proposed, and only confirming adopts it")
        void confirmAdopts(String release) {
            ParameterSession session = startingIn(ToolVersion.parse(release));
            ExpertViewModel expert = expert(session);
            CometParameters before = session.model();
            String draft =
                    expert.canonicalText()
                            .replace(
                                    "\nallowed_missed_cleavage = 2 ",
                                    "\nallowed_missed_cleavage = 1 ")
                            .replace(
                                    "\noutput_percolatorfile = 1 ", "\noutput_percolatorfile = 0 ");
            expert.setDraft(draft);

            assertEquals(EditOutcome.applied(), expert.apply());

            assertSame(before, session.model());
            RawApplyProposal proposal = expert.proposal().orElseThrow();
            assertSame(before, proposal.base());
            assertEquals(
                    List.of("Allowed missed cleavages (allowed_missed_cleavage): 2 -> 1"),
                    proposal.changes().stream()
                            .map(r -> r.label() + ": " + r.current() + " -> " + r.other())
                            .toList());
            assertEquals(
                    List.of(
                            "output_percolatorfile = 0 in the draft is applied as 1. Required by"
                                    + " CometGUI workflow: Percolator rescoring reads the .pin"
                                    + " file."),
                    proposal.enforced());
            assertEquals(List.of(), proposal.warnings());

            assertEquals(EditOutcome.applied(), expert.confirm());

            CometParameters after = session.model();
            assertEquals("1", after.text("allowed_missed_cleavage"));
            assertEquals(ValueOrigin.USER, after.origin("allowed_missed_cleavage"));
            assertEquals(ValueOrigin.COMET_DEFAULT, after.origin("num_threads"));
            assertEquals("1", after.text("output_percolatorfile"));
            assertEquals(ValueOrigin.WORKFLOW_ENFORCED, after.origin("output_percolatorfile"));
            assertEquals(Optional.empty(), expert.proposal());
            assertFalse(expert.isDraftEdited());
            assertEquals(expert.canonicalText(), expert.draft());
            assertTrue(expert.draft().contains("\nallowed_missed_cleavage = 1 "));
        }

        @Test
        @DisplayName("confirming after the configuration changed is refused; cancel drops it")
        void staleAndCancel() {
            ParameterSession session = startingIn(C03);
            ExpertViewModel expert = expert(session);
            expert.setDraft(
                    expert.canonicalText().replace("\nnum_threads = 0 ", "\nnum_threads = 3 "));
            assertEquals(EditOutcome.applied(), expert.apply());
            expert.setDraft(expert.draft());
            assertEquals(Optional.empty(), expert.proposal(), "a changed draft drops the proposal");
            assertEquals(EditOutcome.applied(), expert.apply());
            expert.cancelApply();
            assertEquals(Optional.empty(), expert.proposalProperty().get());
            assertEquals(
                    EditOutcome.refused(
                            "Nothing is waiting for confirmation; apply the draft first."),
                    expert.confirm());

            assertEquals(EditOutcome.applied(), expert.apply());
            // the model moves on while the proposal is shown: the listener drops it
            session.edit("allowed_missed_cleavage", "1");
            assertEquals(Optional.empty(), expert.proposal());
            assertEquals("0", session.model().text("num_threads"));

            // a set equal to the configuration but another object raises no change event, so the
            // proposal survives; confirming it is still refused, because its base is not the model
            assertEquals(EditOutcome.applied(), expert.apply());
            session.adopt(session.model(), Adoption.RAW_APPLIED);
            CometParameters current = session.model();
            assertTrue(expert.proposal().isPresent());
            assertEquals(
                    EditOutcome.refused(
                            "The configuration changed after the draft was checked; apply it"
                                    + " again."),
                    expert.confirm());
            assertSame(current, session.model());
            assertEquals("0", current.text("num_threads"));
            assertEquals(Optional.empty(), expert.proposal());
        }

        @Test
        @DisplayName(
                "a version mismatch in the draft is a warning shown before and after confirming")
        void versionMismatch() {
            ParameterSession session = startingIn(C03);
            ExpertViewModel expert = expert(session);
            expert.setDraft(
                    replaceLine(
                            expert.canonicalText(), 1, "# comet_version 2026.02 rev. 2 (6edec91)"));
            String warning =
                    "line 1 says the file was written for Comet 2026.02.2 (\"2026.02 rev. 2"
                            + " (6edec91)\"), and the selected Comet is 2026.03.0 (\"2026.03 rev. 0"
                            + " (fa08489)\"); the parameters are read and will be written as that"
                            + " version's";
            assertEquals(EditOutcome.applied(), expert.apply());
            RawApplyProposal proposal = expert.proposal().orElseThrow();
            assertEquals(List.of("Warning, line 1: " + warning), proposal.warnings());
            assertEquals(List.of(), proposal.changes());

            assertEquals(EditOutcome.applied(), expert.confirm());
            List<Finding> warnings = session.report().warnings();
            assertEquals(1, warnings.size());
            assertEquals(Rule.IMPORT_DIAGNOSTIC, warnings.get(0).rule());
            assertEquals(Severity.WARNING, warnings.get(0).severity());
            assertEquals(warning, warnings.get(0).message());
        }

        @Test
        @DisplayName(
                "a raw edit of a parameter under review resolves it; an unchanged one does not")
        void migrationOrigins() {
            ParameterSession session = startingIn(C02);
            session.edit("variable_mod01", "15.9949 M 0 3 2 4 0 0.0");
            session.selectRelease(C03);
            assertEquals(1, session.unresolved().size());
            ExpertViewModel expert = expert(session);

            assertEquals(EditOutcome.applied(), expert.apply());
            assertEquals(List.of(), expert.proposal().orElseThrow().changes());
            assertEquals(EditOutcome.applied(), expert.confirm());
            assertEquals(1, session.unresolved().size());
            assertEquals(ValueOrigin.COMET_DEFAULT, session.model().origin("variable_mod01"));
            assertTrue(session.review().isPresent());

            expert.setDraft(
                    expert.canonicalText()
                            .replace(
                                    "\nvariable_mod01 = 15.9949 M 0 3 -1 0 0 0.0\n",
                                    "\nvariable_mod01 = 15.9949 M 0 2 -1 0 0 0.0\n"));
            assertEquals(EditOutcome.applied(), expert.apply());
            assertEquals(EditOutcome.applied(), expert.confirm());
            assertEquals(List.of(), session.unresolved());
            assertEquals(ValueOrigin.USER, session.model().origin("variable_mod01"));
            assertTrue(session.review().isPresent());
        }
    }

    @Nested
    @DisplayName("diffs")
    class Diffs {

        @Test
        @DisplayName("versus the release's defaults and versus a preset")
        void defaultsAndPreset() {
            ParameterSession session = startingIn(C03);
            ExpertViewModel expert = expert(session);
            Comparison defaults = expert.againstDefaults();
            assertEquals("Comet 2026.03.0's defaults", defaults.against());
            assertEquals(
                    List.of("Write Percolator input (PIN) (output_percolatorfile): 1 | 0"),
                    defaults.rows().stream()
                            .map(r -> r.label() + ": " + r.current() + " | " + r.other())
                            .toList());
            assertEquals(Optional.empty(), defaults.unavailable());

            Comparison lowLow =
                    expert.againstPreset(PresetLoader.loadBundled(Sessions.METADATA).get(0));
            assertEquals(
                    "Comet 2026.03.0's defaults with preset Low-res precursor, low-res fragments",
                    lowLow.against());
            assertEquals(
                    List.of(
                            "peptide_mass_tolerance_upper: 20.0 | 3.0",
                            "peptide_mass_tolerance_lower: -20.0 | -3.0",
                            "peptide_mass_units: 2 | 0",
                            "precursor_tolerance_type: 1 | 0",
                            "isotope_error: 2 | 0",
                            "fragment_bin_tol: 0.02 | 1.0005",
                            "fragment_bin_offset: 0.0 | 0.4",
                            "theoretical_fragment_ions: 0 | 1",
                            "output_percolatorfile: 1 | 0"),
                    lowLow.rows().stream()
                            .map(r -> r.row().key() + ": " + r.current() + " | " + r.other())
                            .toList());
        }

        @Test
        @DisplayName("versus the last saved configuration, of the same release only")
        void lastSaved(@TempDir Path directory) {
            ParameterSession session = startingIn(C03);
            ParameterFilesViewModel files = files(session);
            ExpertViewModel expert = new ExpertViewModel(session, BUILD, files);
            assertEquals(
                    Comparison.unavailable(
                            "the last saved configuration", "Nothing has been saved yet."),
                    expert.againstLastSaved());
            assertTrue(files.save(directory.resolve("comet.params")).accepted());
            assertEquals(List.of(), expert.againstLastSaved().rows());
            session.edit("num_threads", "4");
            assertEquals(
                    List.of("Search threads (num_threads): 4 | 0"),
                    expert.againstLastSaved().rows().stream()
                            .map(r -> r.label() + ": " + r.current() + " | " + r.other())
                            .toList());
            session.selectRelease(C02);
            assertEquals(
                    Optional.of(
                            "The last saved configuration is for Comet 2026.03.0 and this one is"
                                    + " for Comet 2026.02.2; configurations of two releases are"
                                    + " compared by migrating one, not by a diff."),
                    expert.againstLastSaved().unavailable());
        }
    }

    @Test
    @DisplayName("a comparison that cannot be made has no rows")
    void comparisonRefusal() {
        DiffRowView row =
                new DiffRowView(
                        new DiffRow(
                                DiffRow.Kind.ENZYME_ROW,
                                "12",
                                Optional.of("12.  Glu_C  1  DE  P"),
                                Optional.empty()),
                        Optional.empty());
        assertEquals("[COMET_ENZYME_INFO] row 12", row.label());
        assertEquals("(not present)", row.other());
        assertEquals(
                "a comparison that cannot be made has no rows",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> new Comparison("x", List.of(row), Optional.of("why")))
                        .getMessage());
    }

    @Test
    @DisplayName("unknown parameters are listed with their finding and removed on request")
    void unknownParameters() {
        ParameterSession session = startingIn(C03);
        ParameterFilesViewModel files = files(session);
        ExpertViewModel expert = new ExpertViewModel(session, BUILD, files);
        // CONSTRUCTED: the real 2026.03.0 -q file with one parameter Comet does not have
        String text =
                Sessions.cometQ(C03)
                        .replace(
                                "database_name = /some/path/db.fasta\n",
                                "database_name = /some/path/db.fasta\n"
                                        + "my_custom_option = 7 # mine\n");
        assertEquals(ImportOutcome.Kind.IMPORTED, files.importText(text, "custom.params").kind());
        assertEquals(
                List.of("my_custom_option = 7"),
                expert.unknownParameters().stream()
                        .map(u -> u.name() + " = " + u.value())
                        .toList());
        List<Finding> findings = expert.findingsOf("my_custom_option");
        assertEquals(1, findings.size());
        assertEquals(Rule.UNKNOWN_PARAMETER, findings.get(0).rule());
        assertEquals(
                "line 6: my_custom_option = 7 is not a parameter CometGUI models for Comet"
                        + " 2026.03.0; it is kept as imported and written back unless you remove"
                        + " it",
                findings.get(0).message());
        assertTrue(expert.canonicalText().contains("\nmy_custom_option = 7 "));
        assertEquals(
                List.of(
                        "Write Percolator input (PIN) (output_percolatorfile): 1 | 0",
                        "my_custom_option: 7 | (not present)"),
                expert.againstDefaults().rows().stream()
                        .map(r -> r.label() + ": " + r.current() + " | " + r.other())
                        .toList());

        assertEquals(
                EditOutcome.refused(
                        "other_option is not an unknown parameter of this configuration, so there"
                                + " is nothing to remove"),
                expert.removeUnknown("other_option"));
        assertEquals(1, expert.unknownParameters().size());
        assertEquals(EditOutcome.applied(), expert.removeUnknown("my_custom_option"));
        assertEquals(List.of(), expert.unknownParameters());
        assertEquals(List.of(), expert.findingsOf("my_custom_option"));
        assertFalse(expert.canonicalText().contains("my_custom_option"));
        assertEquals(
                EditOutcome.refused(
                        "my_custom_option is not an unknown parameter of this configuration, so"
                                + " there is nothing to remove"),
                expert.removeUnknown("my_custom_option"));
    }
}
