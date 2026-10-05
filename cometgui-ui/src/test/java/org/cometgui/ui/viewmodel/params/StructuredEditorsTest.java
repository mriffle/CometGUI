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
import static org.cometgui.ui.viewmodel.params.Sessions.startingIn;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.model.ValueOrigin;
import org.cometgui.params.comet.validation.Finding;
import org.cometgui.params.comet.validation.Rule;
import org.cometgui.params.comet.value.EnzymeDefinition;
import org.cometgui.params.comet.value.IonSeries;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The enzyme, static-modification, ion-series, tolerance and range view-models: every structured
 * value set through the model, findings from the session's one report, expectations typed by hand
 * and, where the release could matter, run on both offered releases.
 */
class StructuredEditorsTest {

    static List<String> releases() {
        return Sessions.releases();
    }

    private static ParameterSession session(String release) {
        return startingIn(ToolVersion.parse(release));
    }

    @Nested
    @DisplayName("enzymes")
    class Enzymes {

        private EnzymeOption option(EnzymesViewModel enzymes, String label) {
            return enzymes.options().stream()
                    .filter(option -> option.label().equals(label))
                    .findFirst()
                    .orElseThrow();
        }

        @ParameterizedTest(name = "Comet {0}")
        @MethodSource("org.cometgui.ui.viewmodel.params.StructuredEditorsTest#releases")
        @DisplayName("three selectors over the table's twelve rows, each rule in words")
        void selectorsAndRows(String release) {
            EnzymesViewModel enzymes = new EnzymesViewModel(session(release));
            assertEquals(
                    List.of(
                            "search_enzyme_number",
                            "search_enzyme2_number",
                            "sample_enzyme_number"),
                    enzymes.selectors().stream().map(FieldViewModel::name).toList());
            assertEquals(
                    List.of(
                            "0. Cut_everywhere",
                            "1. Trypsin",
                            "2. Trypsin/P",
                            "3. Lys_C",
                            "4. Lys_N",
                            "5. Arg_C",
                            "6. Asp_N",
                            "7. CNBr",
                            "8. Asp-N_ambic",
                            "9. PepsinA",
                            "10. Chymotrypsin",
                            "11. No_cut"),
                    enzymes.options().stream().map(EnzymeOption::label).toList());
            assertEquals(
                    "No enzyme rule: non-specific cleavage (no cut and no no-cut residues)",
                    enzymes.options().get(0).description());
            assertEquals(
                    "Cleaves on the C-terminal side of the cut residues; cut residues KR; no-cut"
                            + " residues P",
                    enzymes.options().get(1).description());
            assertEquals(
                    "Cleaves on the N-terminal side of the cut residues; cut residues K; no-cut"
                            + " residues none",
                    enzymes.options().get(4).description());
            assertEquals(
                    "1. Trypsin", enzymes.selected("search_enzyme_number").orElseThrow().label());
            assertEquals(
                    "0. Cut_everywhere",
                    enzymes.selected("search_enzyme2_number").orElseThrow().label());
            assertEquals("12", enzymes.nextNumberText());
            assertEquals(
                    List.of("1", "2", "8", "9"),
                    enzymes.termini().choices().stream().map(ChoiceOption::token).toList());
            assertEquals(
                    "Fully specific: both termini", enzymes.termini().choices().get(1).label());
            assertEquals("allowed_missed_cleavage", enzymes.missedCleavages().name());
            assertEquals(List.of(), enzymes.findings());
        }

        @Test
        @DisplayName("selecting a row writes its number through the model, origin USER")
        void select() {
            ParameterSession session = startingIn(C03);
            EnzymesViewModel enzymes = new EnzymesViewModel(session);
            assertEquals(
                    EditOutcome.applied(),
                    enzymes.select("search_enzyme_number", option(enzymes, "3. Lys_C")));
            assertEquals("3", session.model().text("search_enzyme_number"));
            assertEquals(ValueOrigin.USER, session.model().origin("search_enzyme_number"));
            assertEquals(
                    "3. Lys_C", enzymes.selected("search_enzyme_number").orElseThrow().label());
            EnzymeOption foreign =
                    new EnzymeOption(
                            new EnzymeDefinition(
                                    40, "Elsewhere", EnzymeDefinition.Sense.AFTER_RESIDUE, "K", ""),
                            "x");
            assertEquals(
                    EditOutcome.refused(
                            "40. Elsewhere is not a row of this configuration's enzyme table, so it"
                                    + " cannot be selected"),
                    enzymes.select("search_enzyme_number", foreign));
            assertEquals(
                    EditOutcome.refused(
                            "40. Elsewhere is not a row of this configuration's enzyme table"),
                    enzymes.remove(foreign));
            assertEquals(
                    "fragment_bin_tol does not select an enzyme row",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () -> enzymes.selected("fragment_bin_tol"))
                            .getMessage());
            EnzymeOption trypsin = option(enzymes, "1. Trypsin");
            assertEquals(
                    "fragment_bin_tol does not select an enzyme row",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () -> enzymes.select("fragment_bin_tol", trypsin))
                            .getMessage());
        }

        @Test
        @DisplayName("a custom row is added through the model, selected, and removable only unused")
        void customRow() {
            ParameterSession session = startingIn(C02);
            EnzymesViewModel enzymes = new EnzymesViewModel(session);
            ChoiceOption after = enzymes.senseChoices().get(1);
            assertEquals("1", after.token());
            assertEquals(EditOutcome.applied(), enzymes.addCustom("12", "Glu_C", after, "DE", "P"));
            assertEquals(13, session.model().enzymeTable().rows().size());
            EnzymeOption gluC = option(enzymes, "12. Glu_C");
            assertEquals(
                    "Cleaves on the C-terminal side of the cut residues; cut residues DE; no-cut"
                            + " residues P",
                    gluC.description());
            assertEquals(EditOutcome.applied(), enzymes.select("search_enzyme2_number", gluC));
            assertEquals("12", session.model().text("search_enzyme2_number"));
            assertEquals(
                    EditOutcome.refused(
                            "12. Glu_C is selected as Second search enzyme (search_enzyme2_number);"
                                    + " select another enzyme there before removing it"),
                    enzymes.remove(gluC));
            enzymes.select("search_enzyme2_number", option(enzymes, "0. Cut_everywhere"));
            assertEquals(EditOutcome.applied(), enzymes.remove(gluC));
            assertEquals(12, session.model().enzymeTable().rows().size());
            assertEquals(List.of(), session.report().findings());
        }

        @Test
        @DisplayName("the model's refusals of a custom row are shown as they are")
        void customRefusals() {
            ParameterSession session = startingIn(C03);
            EnzymesViewModel enzymes = new EnzymesViewModel(session);
            ChoiceOption before = enzymes.senseChoices().get(0);
            assertEquals(
                    EditOutcome.refused(
                            "enzyme number 1 is defined twice, as Trypsin and as Glu_C"),
                    enzymes.addCustom("1", "Glu_C", before, "DE", ""));
            assertEquals(
                    EditOutcome.refused("custom enzyme, number: \"x\" is not a whole number"),
                    enzymes.addCustom("x", "Glu_C", before, "DE", ""));
            assertEquals(
                    EditOutcome.refused("\"x [7]\" is not one of the enzyme table's senses"),
                    enzymes.addCustom("12", "Glu_C", new ChoiceOption("7", "x"), "DE", ""));
            assertEquals(12, session.model().enzymeTable().rows().size());
        }

        @Test
        @DisplayName("a selected number the table lacks has no row and the model's error")
        void numberNotInTable() {
            ParameterSession session = startingIn(C03);
            EnzymesViewModel enzymes = new EnzymesViewModel(session);
            session.edit("sample_enzyme_number", "40");
            assertEquals(Optional.empty(), enzymes.selected("sample_enzyme_number"));
            List<Finding> findings = enzymes.findings();
            assertEquals(
                    List.of(Rule.ENZYME_NOT_IN_TABLE),
                    findings.stream().map(Finding::rule).toList());
            assertEquals(List.of("sample_enzyme_number"), findings.get(0).parameters());
        }
    }

    @Nested
    @DisplayName("static modifications")
    class StaticMods {

        @ParameterizedTest(name = "Comet {0}")
        @MethodSource("org.cometgui.ui.viewmodel.params.StructuredEditorsTest#releases")
        @DisplayName("thirty rows, termini first; cysteine carbamidomethylated by default")
        void rows(String release) {
            StaticModsViewModel table = new StaticModsViewModel(session(release));
            List<StaticModRow> rows = table.rows();
            assertEquals(30, rows.size());
            assertEquals("peptide C-terminus", rows.get(0).words());
            assertEquals("protein N-terminus", rows.get(3).words());
            assertEquals("glycine (G)", rows.get(4).words());
            StaticModRow cysteine = table.residue('C').orElseThrow();
            assertEquals("cysteine (C)", cysteine.words());
            assertEquals("57.021464", cysteine.massText());
            assertTrue(cysteine.atDefault());
            assertEquals("Default -- Comet " + release + " default", cysteine.stateText());
            assertEquals(Optional.empty(), table.residue('1'));
        }

        @Test
        @DisplayName("a mass set and reset through the model; a refused mass shown at its field")
        void edit() {
            ParameterSession session = startingIn(C03);
            StaticModsViewModel table = new StaticModsViewModel(session);
            assertEquals(
                    EditOutcome.applied(),
                    table.setMass(table.residue('C').orElseThrow(), "57.02146"));
            StaticModRow cysteine = table.residue('C').orElseThrow();
            assertFalse(cysteine.atDefault());
            assertEquals("57.02146", cysteine.massText());
            assertEquals("Changed from default 57.021464 -- Set by you", cysteine.stateText());
            assertEquals(EditOutcome.applied(), table.reset(cysteine));
            assertTrue(table.residue('C').orElseThrow().atDefault());
            EditOutcome refused = table.setMass(table.residue('K').orElseThrow(), "heavy");
            assertEquals(
                    EditOutcome.refused("add_K_lysine, value: \"heavy\" is not a number"), refused);
            assertEquals("heavy", table.residue('K').orElseThrow().massText());
            assertEquals("add_K_lysine", table.field(table.residue('K').orElseThrow()).name());
        }
    }

    @Nested
    @DisplayName("ion series")
    class Ions {

        @ParameterizedTest(name = "Comet {0}")
        @MethodSource("org.cometgui.ui.viewmodel.params.StructuredEditorsTest#releases")
        @DisplayName(
                "named boxes for seven series and the neutral-loss peaks, set through the model")
        void boxes(String release) {
            ParameterSession session = session(release);
            IonSeriesViewModel ions = new IonSeriesViewModel(session);
            assertEquals(
                    List.of(
                            IonSeries.A,
                            IonSeries.B,
                            IonSeries.C,
                            IonSeries.X,
                            IonSeries.Y,
                            IonSeries.Z,
                            IonSeries.Z1),
                    ions.series());
            assertEquals("Score a ions", ions.field(IonSeries.A).displayName());
            assertEquals("use_Z1_ions", ions.field(IonSeries.Z1).name());
            assertTrue(ions.isOn(IonSeries.B));
            assertTrue(ions.isOn(IonSeries.Y));
            assertFalse(ions.isOn(IonSeries.A));
            assertFalse(ions.neutralLossOn());
            assertEquals("Score water and ammonia losses", ions.neutralLossField().displayName());

            assertEquals(EditOutcome.applied(), ions.set(IonSeries.A, true));
            assertEquals(EditOutcome.applied(), ions.set(IonSeries.B, false));
            assertEquals(EditOutcome.applied(), ions.setNeutralLoss(true));
            assertTrue(ions.isOn(IonSeries.A));
            assertFalse(ions.isOn(IonSeries.B));
            assertTrue(ions.neutralLossOn());
            assertEquals("1", session.model().text("use_A_ions"));
            assertEquals("0", session.model().text("use_B_ions"));
            assertEquals("1", session.model().text("use_NL_ions"));
            assertEquals(ValueOrigin.USER, session.model().origin("use_A_ions"));
        }
    }

    @Nested
    @DisplayName("precursor tolerance and fragment bins")
    class Tolerance {

        @ParameterizedTest(name = "Comet {0}")
        @MethodSource("org.cometgui.ui.viewmodel.params.StructuredEditorsTest#releases")
        @DisplayName("one compound control in words; the pair judged by the model's own rule")
        void pair(String release) {
            ParameterSession session = session(release);
            ToleranceViewModel tolerance = new ToleranceViewModel(session);
            assertEquals("peptide_mass_tolerance_lower", tolerance.lower().name());
            assertEquals("peptide_mass_tolerance_upper", tolerance.upper().name());
            assertEquals("peptide_mass_units", tolerance.units().name());
            assertEquals("precursor_tolerance_type", tolerance.type().name());
            assertEquals("isotope_error", tolerance.isotope().name());
            assertEquals(
                    "-20.0 to 20.0 ppm; applied to: Precursor m/z; isotope offsets: 0, +1, +2",
                    tolerance.summary());
            assertEquals(List.of(), tolerance.pairFindings());
            // a finding elsewhere in the report is not the pair's
            assertEquals(EditOutcome.applied(), session.edit("peptide_length_range", "30 7"));
            assertTrue(session.report().hasErrors());
            assertEquals(List.of(), tolerance.pairFindings());

            assertEquals(EditOutcome.applied(), tolerance.setWindow("-10.0", "20.0"));
            assertEquals(
                    List.of(Rule.PAIR_ASYMMETRIC),
                    tolerance.pairFindings().stream().map(Finding::rule).toList());
            assertEquals(EditOutcome.applied(), tolerance.setWindow("20.0", "-20.0"));
            assertEquals(
                    List.of(Rule.PAIR_REVERSED),
                    tolerance.pairFindings().stream().map(Finding::rule).toList());
            assertEquals(
                    List.of("peptide_mass_tolerance_lower", "peptide_mass_tolerance_upper"),
                    tolerance.pairFindings().get(0).parameters());
            assertEquals(EditOutcome.applied(), tolerance.setWindow("5.0", "20.0"));
            assertEquals(
                    List.of(Rule.PAIR_SAME_SIGNED),
                    tolerance.pairFindings().stream().map(Finding::rule).toList());
        }

        @Test
        @DisplayName("a refused bound is its own field's; an undocumented unit is shown as written")
        void refusalsAndUnknownChoice() {
            ParameterSession session = startingIn(C03);
            ToleranceViewModel tolerance = new ToleranceViewModel(session);
            assertEquals(
                    EditOutcome.refused(
                            "peptide_mass_tolerance_lower, value: \"minus ten\" is not one number"),
                    tolerance.setWindow("minus ten", "10.0"));
            assertEquals("10.0", session.model().text("peptide_mass_tolerance_upper"));
            assertEquals(
                    EditOutcome.refused(
                            "peptide_mass_tolerance_upper, value: \"ten\" is not a number"),
                    tolerance.setWindow("-10.0", "ten"));
            assertEquals("-10.0", session.model().text("peptide_mass_tolerance_lower"));
            assertEquals(EditOutcome.applied(), tolerance.units().setText("7"));
            assertEquals(
                    "-10.0 to 10.0 \"7\"; applied to: Precursor m/z; isotope offsets: 0, +1, +2",
                    tolerance.summary());
        }

        @ParameterizedTest(name = "Comet {0}")
        @MethodSource("org.cometgui.ui.viewmodel.params.StructuredEditorsTest#releases")
        @DisplayName("fragment bins in the built-in presets' words, set through the model")
        void fragments(String release) {
            ParameterSession session = session(release);
            ToleranceViewModel tolerance = new ToleranceViewModel(session);
            assertEquals("fragment_bin_tol", tolerance.fragmentBinTol().name());
            assertEquals("fragment_bin_offset", tolerance.fragmentBinOffset().name());
            List<FragmentOption> options = tolerance.fragmentOptions();
            assertEquals(2, options.size());
            assertEquals(
                    "Low-res precursor, low-res fragments / High-res precursor, low-res fragments",
                    options.get(0).words());
            assertEquals(
                    "fragment_bin_tol = 1.0005, fragment_bin_offset = 0.4,"
                            + " theoretical_fragment_ions = 1",
                    options.get(0).valuesText());
            assertEquals("High-res precursor, high-res fragments", options.get(1).words());
            assertEquals(
                    "fragment_bin_tol = 0.02, fragment_bin_offset = 0.0,"
                            + " theoretical_fragment_ions = 0",
                    options.get(1).valuesText());
            assertEquals(
                    "As in: High-res precursor, high-res fragments", tolerance.fragmentWords());

            assertEquals(EditOutcome.applied(), tolerance.chooseFragment(options.get(0)));
            assertEquals("1.0005", session.model().text("fragment_bin_tol"));
            assertEquals("0.4", session.model().text("fragment_bin_offset"));
            assertEquals("1", session.model().text("theoretical_fragment_ions"));
            assertEquals(ValueOrigin.USER, session.model().origin("fragment_bin_offset"));
            assertEquals(options.get(0), tolerance.fragmentMatch().orElseThrow());
            assertEquals(
                    "As in: Low-res precursor, low-res fragments / High-res precursor, low-res"
                            + " fragments",
                    tolerance.fragmentWords());

            assertEquals(EditOutcome.applied(), tolerance.fragmentBinTol().setText("0.05"));
            assertEquals(Optional.empty(), tolerance.fragmentMatch());
            assertEquals("Not one of the built-in instrument settings", tolerance.fragmentWords());
        }

        @Test
        @DisplayName("an option holding text the model refuses reports the first refusal")
        void refusedOption() {
            ParameterSession session = startingIn(C03);
            ToleranceViewModel tolerance = new ToleranceViewModel(session);
            Map<String, String> values = new LinkedHashMap<>();
            values.put("fragment_bin_tol", "wide");
            values.put("fragment_bin_offset", "0.4");
            values.put("theoretical_fragment_ions", "narrow");
            EditOutcome outcome =
                    tolerance.chooseFragment(new FragmentOption(List.of("constructed"), values));
            assertEquals(
                    EditOutcome.refused("fragment_bin_tol, value: \"wide\" is not a number"),
                    outcome);
            assertEquals("0.4", session.model().text("fragment_bin_offset"));
            assertEquals(
                    "a fragment setting names at least one preset and one value",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () -> new FragmentOption(List.of(), values))
                            .getMessage());
            assertEquals(
                    "a fragment setting names at least one preset and one value",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () -> new FragmentOption(List.of("x"), Map.of()))
                            .getMessage());
        }
    }

    @Nested
    @DisplayName("two-value ranges")
    class Ranges {

        @ParameterizedTest(name = "Comet {0}")
        @MethodSource("org.cometgui.ui.viewmodel.params.StructuredEditorsTest#releases")
        @DisplayName("the five ranges, each two texts under one label")
        void five(String release) {
            RangesViewModel ranges = new RangesViewModel(session(release));
            assertEquals(
                    List.of(
                            "scan_range",
                            "precursor_charge",
                            "digest_mass_range",
                            "peptide_length_range",
                            "clear_mz_range"),
                    ranges.ranges().stream().map(range -> range.field().name()).toList());
            RangeViewModel length = ranges.range("peptide_length_range");
            assertEquals("Peptide length range", length.label());
            assertEquals("5", length.firstText());
            assertEquals("50", length.secondText());
            RangeViewModel mass = ranges.range("digest_mass_range");
            assertEquals("600.0", mass.firstText());
            assertEquals("5000.0", mass.secondText());
        }

        @Test
        @DisplayName("set through the model; order judged by the model's rule only")
        void set() {
            ParameterSession session = startingIn(C02);
            RangesViewModel ranges = new RangesViewModel(session);
            RangeViewModel length = ranges.range("peptide_length_range");
            assertEquals(EditOutcome.applied(), length.set("7", "30"));
            assertEquals("7 30", session.model().text("peptide_length_range"));
            assertEquals(List.of(), length.findings());
            assertEquals(EditOutcome.applied(), length.set("30", "7"));
            assertEquals("30 7", session.model().text("peptide_length_range"));
            assertEquals(
                    List.of(Rule.RANGE_REVERSED),
                    length.findings().stream().map(Finding::rule).toList());
            RangeViewModel clear = ranges.range("clear_mz_range");
            assertEquals(EditOutcome.applied(), clear.set("125.5", "131.5"));
            assertEquals("125.5 131.5", session.model().text("clear_mz_range"));
        }

        @Test
        @DisplayName("a value the model cannot read is refused at the field and blocks Run")
        void refused() {
            ParameterSession session = startingIn(C03);
            RangeViewModel length = new RangesViewModel(session).range("peptide_length_range");
            String message = "peptide_length_range, first value: \"x\" is not a whole number";
            assertEquals(EditOutcome.refused(message), length.set("x", "7"));
            assertEquals("5 50", session.model().text("peptide_length_range"));
            assertEquals("x 7", length.field().text());
            assertEquals(Optional.of(message), length.field().refusal());
            assertEquals(
                    List.of("peptide_length_range"),
                    session.pendingRefusals().stream().map(FieldViewModel::name).toList());
            assertEquals(
                    "fragment_bin_tol is not a two-value range",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () -> new RangesViewModel(session).range("fragment_bin_tol"))
                            .getMessage());
        }
    }

    @Test
    @DisplayName("a typed edit of a locked output is refused with the lock's reason")
    void lockedTypedEdit() {
        ParameterSession session = startingIn(C02);
        Map<String, org.cometgui.params.comet.model.ParameterValue> values = new LinkedHashMap<>();
        values.put("use_A_ions", new org.cometgui.params.comet.model.ParameterValue.Flag(true));
        values.put(
                "output_percolatorfile",
                new org.cometgui.params.comet.model.ParameterValue.Flag(false));
        EditOutcome outcome = session.setValues(values);
        assertFalse(outcome.accepted());
        assertTrue(
                outcome.refusal()
                        .orElseThrow()
                        .endsWith(
                                "Required by CometGUI workflow: Percolator rescoring reads the .pin"
                                        + " file."),
                outcome.refusal().orElseThrow());
        assertEquals("0", session.model().text("use_A_ions"));
    }
}
