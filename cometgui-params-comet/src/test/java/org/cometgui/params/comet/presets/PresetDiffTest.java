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

package org.cometgui.params.comet.presets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.cometgui.params.comet.migration.VersionConversion;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.model.ParameterEntry;
import org.cometgui.params.comet.model.ValueOrigin;
import org.cometgui.params.comet.validation.CometValidator;
import org.cometgui.params.comet.validation.Rule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code AC-PAR-08}, headless half: a preset shows a diff before anything changes, and applying all
 * or selected rows changes exactly those rows -- over the real {@code comet -q} model.
 */
class PresetDiffTest {

    private static DiffRow row(String name, String current, String preset) {
        return new DiffRow(DiffRow.Kind.PARAMETER, name, Optional.of(current), Optional.of(preset));
    }

    /** Typed from Comet's comet.params.low-low against the real -q defaults, in -q order. */
    private static final List<DiffRow> LOW_LOW_ON_DEFAULTS =
            List.of(
                    row("peptide_mass_tolerance_upper", "20.0", "3.0"),
                    row("peptide_mass_tolerance_lower", "-20.0", "-3.0"),
                    row("peptide_mass_units", "2", "0"),
                    row("precursor_tolerance_type", "1", "0"),
                    row("isotope_error", "2", "0"),
                    row("fragment_bin_tol", "0.02", "1.0005"),
                    row("fragment_bin_offset", "0.0", "0.4"),
                    row("theoretical_fragment_ions", "0", "1"));

    @Test
    @DisplayName("low-low against the real -q defaults: exactly its eight changes, in schema order")
    void theDiffShowsExactlyWhatWouldChange() {
        CometParameters model = Real.newer();
        PresetDiff diff = PresetDiff.of(model, Real.builtIn("low-low"));
        assertEquals(LOW_LOW_ON_DEFAULTS, diff.rows());
        assertEquals(model, Real.newer(), "building the diff changed nothing");
        assertEquals(model, diff.base());
        assertEquals("low-low", diff.preset().id());
        assertTrue(diff.compatibility().isClean());
        assertEquals("fragment_bin_tol", diff.rows().get(5).label());
        for (ParameterEntry entry : model.entries()) {
            assertEquals(ValueOrigin.IMPORTED, model.origin(entry.name()));
        }
    }

    @Test
    @DisplayName("a delta whose value the set already has makes no row")
    void unchangedDeltasMakeNoRow() {
        CometParameters model = Real.newer();
        assertEquals(List.of(), PresetDiff.of(model, Real.builtIn("high-high")).rows());
        assertEquals(
                List.of("fragment_bin_tol", "fragment_bin_offset", "theoretical_fragment_ions"),
                PresetDiff.of(model, Real.builtIn("high-low")).rows().stream()
                        .map(DiffRow::key)
                        .toList());
        CometParameters user = model.withOrigin("fragment_bin_tol", ValueOrigin.USER);
        assertEquals(List.of(), PresetDiff.of(user, Real.builtIn("high-high")).rows());
    }

    @Test
    @DisplayName("apply-selected changes only the selected rows, stamps them PRESET, and validates")
    void applySelectedChangesOnlyTheSelection() {
        CometParameters model = Real.newer();
        PresetDiff diff = PresetDiff.of(model, Real.builtIn("low-low"));
        AppliedPreset applied =
                diff.applySelected(List.of("fragment_bin_offset", "fragment_bin_tol"));
        CometParameters result = applied.model();
        assertEquals(
                List.of(LOW_LOW_ON_DEFAULTS.get(5), LOW_LOW_ON_DEFAULTS.get(6)),
                applied.applied(),
                "applied rows in schema order, whatever the selection's order");
        assertEquals("1.0005", result.text("fragment_bin_tol"));
        assertEquals("0.4", result.text("fragment_bin_offset"));
        assertEquals(ValueOrigin.PRESET, result.origin("fragment_bin_tol"));
        assertEquals(ValueOrigin.PRESET, result.origin("fragment_bin_offset"));
        for (ParameterEntry entry : model.entries()) {
            String name = entry.name();
            if ("fragment_bin_tol".equals(name) || "fragment_bin_offset".equals(name)) {
                continue;
            }
            assertEquals(model.text(name), result.text(name), name + " was not selected");
            assertEquals(model.origin(name), result.origin(name), name + " was not selected");
        }
        assertEquals("0", result.text("theoretical_fragment_ions"));
        assertEquals("20.0", result.text("peptide_mass_tolerance_upper"));
        assertEquals(model, Real.newer(), "the original set is unchanged");
        assertEquals(CometValidator.standard().validate(result), applied.validation());
        assertEquals(
                List.of("workflow_enforced.output_off"),
                applied.validation().errors().stream().map(f -> f.rule().id()).toList());
        assertEquals(diff.compatibility(), applied.compatibility());
        assertEquals(
                List.of("theoretical_fragment_ions"),
                PresetDiff.of(result, Real.builtIn("high-low")).rows().stream()
                        .map(DiffRow::key)
                        .toList(),
                "only the unselected fragment row still differs from high-low");
    }

    @Test
    @DisplayName("apply-all applies every row; the diff is then empty and validation clean")
    void applyAll() {
        CometParameters model = Real.newer().withWorkflowEnforcedOutputs();
        AppliedPreset applied = PresetDiff.of(model, Real.builtIn("low-low")).applyAll();
        assertEquals(LOW_LOW_ON_DEFAULTS, applied.applied());
        for (DiffRow row : LOW_LOW_ON_DEFAULTS) {
            assertEquals(row.other().orElseThrow(), applied.model().text(row.key()));
            assertEquals(ValueOrigin.PRESET, applied.model().origin(row.key()));
        }
        assertEquals(List.of(), PresetDiff.of(applied.model(), Real.builtIn("low-low")).rows());
        assertEquals(List.of(), applied.validation().findings());
        assertEquals(
                ValueOrigin.WORKFLOW_ENFORCED, applied.model().origin("output_percolatorfile"));
    }

    @Test
    @DisplayName("a preset that makes the set invalid is applied and its errors are reported")
    void invalidResultsAreReported() {
        Preset reversed =
                Real.user(
                        Real.NEWER,
                        "peptide_mass_tolerance_lower",
                        "5.0",
                        "peptide_mass_tolerance_upper",
                        "-5.0");
        AppliedPreset applied =
                PresetDiff.of(Real.newer().withWorkflowEnforcedOutputs(), reversed).applyAll();
        assertTrue(applied.validation().hasErrors());
        assertEquals(
                List.of(Rule.PAIR_REVERSED),
                applied.validation().errors().stream().map(f -> f.rule()).toList());
        assertEquals("5.0", applied.model().text("peptide_mass_tolerance_lower"));
    }

    @Test
    @DisplayName(
            "selecting a row the diff does not have is refused; selecting none changes nothing")
    void selections() {
        CometParameters model = Real.newer();
        PresetDiff diff = PresetDiff.of(model, Real.builtIn("high-low"));
        IllegalArgumentException failure =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> diff.applySelected(List.of("fragment_bin_tol", "num_threads")));
        assertEquals(
                "num_threads is not a row of the diff of preset high-low (rows: [fragment_bin_tol,"
                        + " fragment_bin_offset, theoretical_fragment_ions]), so it cannot be"
                        + " applied",
                failure.getMessage());
        AppliedPreset none = diff.applySelected(List.of());
        assertEquals(model, none.model());
        assertEquals(List.of(), none.applied());
        assertThrows(UnsupportedOperationException.class, () -> diff.rows().clear());
        assertThrows(UnsupportedOperationException.class, () -> none.applied().clear());
    }

    @Test
    @DisplayName("a row's two sides must differ; an enzyme row is labelled as one")
    void rowRules() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new DiffRow(DiffRow.Kind.PARAMETER, "x", Optional.of("1"), Optional.of("1")));
        assertThrows(
                IllegalArgumentException.class,
                () -> new DiffRow(DiffRow.Kind.PARAMETER, " ", Optional.of("1"), Optional.empty()));
        assertEquals(
                "[COMET_ENZYME_INFO] row 12",
                new DiffRow(DiffRow.Kind.ENZYME_ROW, "12", Optional.empty(), Optional.of("r"))
                        .label());
        assertEquals(
                "knob",
                new DiffRow(
                                DiffRow.Kind.UNKNOWN_PARAMETER,
                                "knob",
                                Optional.of("1"),
                                Optional.empty())
                        .label());
        assertEquals(
                VersionConversion.Status.SAME,
                PresetDiff.of(Real.newer(), Real.builtIn("low-low"))
                        .compatibility()
                        .entry("isotope_error")
                        .orElseThrow()
                        .status());
    }
}
