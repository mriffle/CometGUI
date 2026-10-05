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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.migration.VersionConversion;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.model.ValueOrigin;
import org.cometgui.params.comet.presets.Preset;
import org.cometgui.params.comet.validation.Rule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Presets (exit gate item 3, {@code AC-PAR-08}): a preview is a diff that changes nothing; apply
 * all, apply exactly the selected subset, or cancel and keep the very same configuration. Every
 * expected row is typed by hand from the developer page's table of the built-in presets and each
 * release's own {@code comet -q} values.
 */
class PresetsViewModelTest {

    /** Low-low against either release's defaults: the eight rows, as display name (name). */
    private static final List<String> LOW_LOW_LABELS =
            List.of(
                    "Precursor tolerance, upper bound (peptide_mass_tolerance_upper)",
                    "Precursor tolerance, lower bound (peptide_mass_tolerance_lower)",
                    "Precursor tolerance units (peptide_mass_units)",
                    "Precursor tolerance applies to (precursor_tolerance_type)",
                    "Precursor isotope offsets (isotope_error)",
                    "Fragment bin width (fragment_bin_tol)",
                    "Fragment bin offset (fragment_bin_offset)",
                    "Flanking-bin scoring (theoretical_fragment_ions)");

    private static final List<String> LOW_LOW_CURRENT =
            List.of("20.0", "-20.0", "2", "1", "2", "0.02", "0.0", "0");

    private static final List<String> LOW_LOW_PRESET =
            List.of("3.0", "-3.0", "0", "0", "0", "1.0005", "0.4", "1");

    private static Preset builtIn(PresetsViewModel presets, String id) {
        return presets.presets().stream().filter(p -> p.id().equals(id)).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("offers the three built-in presets, then the user's")
    void offered() {
        ParameterSession session = startingIn(C03);
        Preset mine =
                Preset.fromModel(
                        "my-threads",
                        "Four threads",
                        "CONSTRUCTED user preset",
                        session.model().withText("num_threads", "4", ValueOrigin.USER),
                        List.of("num_threads"));
        PresetsViewModel presets = new PresetsViewModel(session, List.of(mine));
        assertEquals(
                List.of("low-low", "high-low", "high-high", "my-threads"),
                presets.presets().stream().map(Preset::id).toList());
        assertEquals(Optional.empty(), presets.preview());
        assertEquals(
                "preset elsewhere is not one of the presets offered here",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        presets.preview(
                                                Preset.fromModel(
                                                        "elsewhere",
                                                        "Elsewhere",
                                                        "CONSTRUCTED, not offered",
                                                        session.model(),
                                                        List.of("num_threads"))))
                        .getMessage());
    }

    @ParameterizedTest(name = "Comet {0}")
    @ValueSource(strings = {"2026.03.0", "2026.02.2"})
    @DisplayName("previewing low-low shows the eight rows and changes nothing")
    void previewLowLow(String release) {
        ParameterSession session = startingIn(ToolVersion.parse(release));
        CometParameters before = session.model();
        PresetsViewModel presets = new PresetsViewModel(session, List.of());

        PresetPreview preview = presets.preview(builtIn(presets, "low-low"));

        assertSame(before, session.model());
        assertEquals(Optional.of(preview), presets.preview());
        assertEquals(Optional.of(preview), presets.previewProperty().get());
        assertEquals(Optional.empty(), presets.lastAppliedProperty().get());
        assertEquals(LOW_LOW_LABELS, preview.rows().stream().map(r -> r.view().label()).toList());
        assertEquals(
                LOW_LOW_CURRENT, preview.rows().stream().map(r -> r.view().current()).toList());
        assertEquals(LOW_LOW_PRESET, preview.rows().stream().map(r -> r.view().other()).toList());
        assertTrue(preview.rows().stream().allMatch(PresetRowViewModel::isSelected));
        assertEquals(List.of(), preview.problems());
        assertEquals(List.of(), preview.conversions());
        assertEquals(
                "2026.02.2".equals(release)
                        ? "Made for Comet 2026.02.2"
                        : "Made for Comet 2026.02.2; this configuration is for Comet 2026.03.0",
                preview.madeFor());
        assertEquals("low-low", preview.preset().id());
    }

    @ParameterizedTest(name = "Comet {0}")
    @ValueSource(strings = {"2026.03.0", "2026.02.2"})
    @DisplayName("applying a subset applies exactly that subset, origin PRESET")
    void applySubset(String release) {
        ParameterSession session = startingIn(ToolVersion.parse(release));
        PresetsViewModel presets = new PresetsViewModel(session, List.of());
        PresetPreview preview = presets.preview(builtIn(presets, "low-low"));
        for (PresetRowViewModel row : preview.rows()) {
            if (!List.of(
                            "peptide_mass_tolerance_upper",
                            "peptide_mass_tolerance_lower",
                            "fragment_bin_tol")
                    .contains(row.parameter())) {
                assertEquals(EditOutcome.applied(), row.setSelected(false));
            }
        }
        assertEquals(
                List.of(
                        "peptide_mass_tolerance_upper",
                        "peptide_mass_tolerance_lower",
                        "fragment_bin_tol"),
                preview.selected());

        assertEquals(EditOutcome.applied(), presets.applySelected());

        CometParameters after = session.model();
        Map<String, String> applied =
                Map.of(
                        "peptide_mass_tolerance_upper", "3.0",
                        "peptide_mass_tolerance_lower", "-3.0",
                        "fragment_bin_tol", "1.0005");
        applied.forEach(
                (name, text) -> {
                    assertEquals(text, after.text(name), name);
                    assertEquals(ValueOrigin.PRESET, after.origin(name), name);
                });
        Map<String, String> untouched =
                Map.of(
                        "peptide_mass_units", "2",
                        "precursor_tolerance_type", "1",
                        "isotope_error", "2",
                        "fragment_bin_offset", "0.0",
                        "theoretical_fragment_ions", "0");
        untouched.forEach(
                (name, text) -> {
                    assertEquals(text, after.text(name), name);
                    assertEquals(ValueOrigin.COMET_DEFAULT, after.origin(name), name);
                });
        assertEquals(ValueOrigin.WORKFLOW_ENFORCED, after.origin("output_percolatorfile"));
        assertEquals(Optional.empty(), presets.preview());
        assertEquals(
                List.of(
                        "Precursor tolerance, upper bound (peptide_mass_tolerance_upper)",
                        "Precursor tolerance, lower bound (peptide_mass_tolerance_lower)",
                        "Fragment bin width (fragment_bin_tol)"),
                presets.lastAppliedRows().stream().map(DiffRowView::label).toList());
        // the applied set's findings are the session's one report: a 3 ppm window is no error
        assertEquals(List.of(), session.report().findings());
        assertEquals("3.0", session.field("peptide_mass_tolerance_upper").text());
    }

    @Test
    @DisplayName("applying all sets the eight values; cancelling keeps the very same model")
    void applyAllAndCancel() {
        ParameterSession session = startingIn(C03);
        PresetsViewModel presets = new PresetsViewModel(session, List.of());

        presets.preview(builtIn(presets, "low-low"));
        CometParameters before = session.model();
        presets.cancel();
        assertSame(before, session.model());
        assertEquals(Optional.empty(), presets.preview());
        assertEquals(
                EditOutcome.refused("No preset is being previewed, so there is nothing to apply."),
                presets.applyAll());
        assertEquals(
                EditOutcome.refused("No preset is being previewed, so there is nothing to apply."),
                presets.applySelected());
        assertSame(before, session.model());
        assertEquals(List.of(), presets.lastAppliedRows());

        PresetPreview preview = presets.preview(builtIn(presets, "low-low"));
        preview.rows().get(0).setSelected(false);
        assertEquals(EditOutcome.applied(), presets.applyAll());
        for (int index = 0; index < LOW_LOW_PRESET.size(); index++) {
            String name = preview.rows().get(index).parameter();
            assertEquals(LOW_LOW_PRESET.get(index), session.model().text(name), name);
            assertEquals(ValueOrigin.PRESET, session.model().origin(name), name);
        }
        assertEquals(8, presets.lastApplied().orElseThrow().applied().size());
    }

    @Test
    @DisplayName("nothing selected, nothing to change, or a stale preview: the same model")
    void nothingChanges() {
        ParameterSession session = startingIn(C03);
        PresetsViewModel presets = new PresetsViewModel(session, List.of());
        CometParameters before = session.model();

        PresetPreview highHigh = presets.preview(builtIn(presets, "high-high"));
        assertEquals(List.of(), highHigh.rows());
        assertEquals(
                EditOutcome.refused(
                        "The configuration already holds every value of this preset that its"
                                + " release can take; nothing was applied."),
                presets.applyAll());
        assertSame(before, session.model());

        PresetPreview highLow = presets.preview(builtIn(presets, "high-low"));
        assertEquals(
                List.of("fragment_bin_tol", "fragment_bin_offset", "theoretical_fragment_ions"),
                highLow.rows().stream().map(PresetRowViewModel::parameter).toList());
        highLow.rows().forEach(row -> row.setSelected(false));
        assertEquals(
                EditOutcome.refused("No change is selected, so nothing was applied."),
                presets.applySelected());
        assertSame(before, session.model());

        session.edit("num_threads", "4");
        CometParameters edited = session.model();
        assertEquals(
                EditOutcome.refused(
                        "The configuration changed after this preview was made, so it no longer"
                                + " shows what applying would change. Preview the preset again."),
                presets.applyAll());
        assertSame(edited, session.model());
        assertEquals("0.02", edited.text("fragment_bin_tol"));
    }

    @Test
    @DisplayName("a release switch drops the preview")
    void releaseSwitchCancels() {
        ParameterSession session = startingIn(C03);
        PresetsViewModel presets = new PresetsViewModel(session, List.of());
        presets.preview(builtIn(presets, "low-low"));
        session.selectRelease(C02);
        assertEquals(Optional.empty(), presets.preview());
    }

    @Test
    @DisplayName("a row whose parameter the workflow locks cannot be selected and is not applied")
    void lockedRow() {
        ParameterSession session = startingIn(C03);
        CometParameters off =
                session.model().withText("output_percolatorfile", "0", ValueOrigin.USER);
        Preset mine =
                Preset.fromModel(
                        "no-pin",
                        "No PIN",
                        "CONSTRUCTED user preset switching the PIN off",
                        off.withText("num_threads", "4", ValueOrigin.USER),
                        List.of("num_threads", "output_percolatorfile"));
        PresetsViewModel presets = new PresetsViewModel(session, List.of(mine));
        PresetPreview preview = presets.preview(mine);
        assertEquals(
                List.of("num_threads", "output_percolatorfile"),
                preview.rows().stream().map(PresetRowViewModel::parameter).toList());
        PresetRowViewModel pin = preview.rows().get(1);
        assertFalse(pin.isSelected());
        assertEquals(
                Optional.of(
                        "Required by CometGUI workflow: Percolator rescoring reads the .pin file"),
                pin.lockReason());
        assertEquals(
                EditOutcome.refused(
                        "Write Percolator input (PIN) (output_percolatorfile) cannot be set by a"
                                + " preset. Required by CometGUI workflow: Percolator rescoring"
                                + " reads the .pin file."),
                pin.setSelected(true));
        assertFalse(pin.selectedProperty().get());
        assertEquals(List.of("num_threads"), preview.applicable());

        assertEquals(EditOutcome.applied(), presets.applyAll());
        assertEquals("4", session.model().text("num_threads"));
        assertEquals(ValueOrigin.PRESET, session.model().origin("num_threads"));
        assertEquals("1", session.model().text("output_percolatorfile"));
        assertEquals(
                ValueOrigin.WORKFLOW_ENFORCED, session.model().origin("output_percolatorfile"));
    }

    @Nested
    @DisplayName("the fragment instrument choice: a preview scoped to the fragment rows")
    class FragmentPreview {

        /** The fragment rows of low-low against the defaults: label, current, preset. */
        private static final List<List<String>> FRAGMENT_ROWS =
                List.of(
                        List.of("Fragment bin width (fragment_bin_tol)", "0.02", "1.0005"),
                        List.of("Fragment bin offset (fragment_bin_offset)", "0.0", "0.4"),
                        List.of("Flanking-bin scoring (theoretical_fragment_ions)", "0", "1"));

        private static List<List<String>> shown(PresetPreview preview) {
            return preview.rows().stream()
                    .map(r -> List.of(r.view().label(), r.view().current(), r.view().other()))
                    .toList();
        }

        @ParameterizedTest(name = "Comet {0}")
        @ValueSource(strings = {"2026.03.0", "2026.02.2"})
        @DisplayName(
                "previewing changes nothing; cancel changes nothing; apply all sets exactly"
                        + " the three fragment rows")
        void previewCancelApply(String release) {
            ParameterSession session = startingIn(ToolVersion.parse(release));
            // away from the defaults first, so a reset could not pass for "nothing changed"
            assertEquals(EditOutcome.applied(), session.edit("peptide_mass_tolerance_upper", "10"));
            assertEquals(EditOutcome.applied(), session.edit("num_threads", "4"));
            CometParameters before = session.model();
            PresetsViewModel presets = new PresetsViewModel(session, List.of());
            FragmentOption lowRes = new ToleranceViewModel(session).fragmentOptions().get(0);

            PresetPreview preview = presets.previewFragment(lowRes);
            assertSame(before, session.model());
            assertEquals(FRAGMENT_ROWS, shown(preview));
            assertEquals(
                    "Low-res precursor, low-res fragments / High-res precursor, low-res fragments"
                            + " (fragment ions only)",
                    preview.title());
            assertEquals(
                    Optional.of(
                            java.util.Set.of(
                                    "fragment_bin_tol",
                                    "fragment_bin_offset",
                                    "theoretical_fragment_ions")),
                    preview.scope());
            assertEquals("low-low", preview.preset().id());
            assertEquals(Optional.of(preview), presets.preview());

            presets.cancel();
            assertSame(before, session.model());
            assertEquals(Optional.empty(), presets.preview());

            presets.previewFragment(lowRes);
            assertEquals(EditOutcome.applied(), presets.applyAll());
            CometParameters after = session.model();
            assertEquals("1.0005", after.text("fragment_bin_tol"));
            assertEquals("0.4", after.text("fragment_bin_offset"));
            assertEquals("1", after.text("theoretical_fragment_ions"));
            assertEquals(ValueOrigin.PRESET, after.origin("fragment_bin_tol"));
            assertEquals(ValueOrigin.PRESET, after.origin("theoretical_fragment_ions"));
            // the preset's precursor rows were never offered, so nothing else moved
            assertEquals("10", after.text("peptide_mass_tolerance_upper"));
            assertEquals(ValueOrigin.USER, after.origin("peptide_mass_tolerance_upper"));
            assertEquals("-20.0", after.text("peptide_mass_tolerance_lower"));
            assertEquals("2", after.text("peptide_mass_units"));
            assertEquals("4", after.text("num_threads"));
            assertEquals(
                    List.of("fragment_bin_tol", "fragment_bin_offset", "theoretical_fragment_ions"),
                    presets.lastAppliedRows().stream().map(r -> r.row().key()).toList());
        }

        @Test
        @DisplayName("apply selected applies exactly the ticked fragment row")
        void subset() {
            ParameterSession session = startingIn(C03);
            PresetsViewModel presets = new PresetsViewModel(session, List.of());
            PresetPreview preview =
                    presets.previewFragment(
                            new ToleranceViewModel(session).fragmentOptions().get(0));
            preview.rows().get(0).setSelected(false);
            preview.rows().get(2).setSelected(false);
            assertEquals(EditOutcome.applied(), presets.applySelected());
            assertEquals("0.02", session.model().text("fragment_bin_tol"));
            assertEquals("0.4", session.model().text("fragment_bin_offset"));
            assertEquals("0", session.model().text("theoretical_fragment_ions"));
            assertEquals(ValueOrigin.COMET_DEFAULT, session.model().origin("fragment_bin_tol"));
        }

        @Test
        @DisplayName("an option of a preset not offered here is refused")
        void notOffered() {
            ParameterSession session = startingIn(C03);
            Preset elsewhere =
                    Preset.fromModel(
                            "elsewhere",
                            "Elsewhere",
                            "CONSTRUCTED, not offered",
                            session.model(),
                            List.of("fragment_bin_tol"));
            FragmentOption option =
                    new FragmentOption(
                            List.of("constructed"), Map.of("fragment_bin_tol", "0.02"), elsewhere);
            PresetsViewModel presets = new PresetsViewModel(session, List.of());
            assertEquals(
                    "preset elsewhere is not one of the presets offered here",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () -> presets.previewFragment(option))
                            .getMessage());
            assertEquals(Optional.empty(), presets.preview());
        }

        @Test
        @DisplayName("a scoped preview lists only its own parameters' problems and conversions")
        void scopedCompatibility() {
            ParameterSession newer = startingIn(C03);
            Preset caret =
                    Preset.fromModel(
                            "caret-and-bins",
                            "Caret and bins",
                            "CONSTRUCTED user preset made on 2026.03.0",
                            newer.model()
                                    .withText(
                                            "variable_mod02",
                                            "42.010565 ^ 0 1 -1 0 0 0.0",
                                            ValueOrigin.USER)
                                    .withText("fragment_bin_tol", "1.0005", ValueOrigin.USER),
                            List.of("variable_mod02", "fragment_bin_tol"));
            ParameterSession older = startingIn(C02);
            PresetsViewModel onOlder = new PresetsViewModel(older, List.of(caret));
            assertEquals(1, onOlder.preview(caret).problems().size());
            PresetPreview bins =
                    onOlder.previewFragment(
                            new FragmentOption(
                                    List.of("bins"), Map.of("fragment_bin_tol", "1.0005"), caret));
            assertEquals(List.of(), bins.problems());
            assertEquals(
                    List.of("fragment_bin_tol"),
                    bins.rows().stream().map(PresetRowViewModel::parameter).toList());
            PresetPreview slot =
                    onOlder.previewFragment(
                            new FragmentOption(
                                    List.of("slot"),
                                    Map.of("variable_mod02", "42.010565 ^ 0 1 -1 0 0 0.0"),
                                    caret));
            assertEquals(1, slot.problems().size());
            assertEquals(List.of(), slot.rows());

            Preset index =
                    Preset.fromModel(
                            "index-and-bins",
                            "Index and bins",
                            "CONSTRUCTED user preset made on 2026.02.2",
                            older.model(),
                            List.of("index_search_type", "fragment_bin_tol"));
            PresetsViewModel onNewer = new PresetsViewModel(newer, List.of(index));
            assertEquals(1, onNewer.preview(index).conversions().size());
            assertEquals(
                    List.of(),
                    onNewer.previewFragment(
                                    new FragmentOption(
                                            List.of("bins"),
                                            Map.of("fragment_bin_tol", "0.02"),
                                            index))
                            .conversions());
            assertEquals(
                    1,
                    onNewer.previewFragment(
                                    new FragmentOption(
                                            List.of("index"),
                                            Map.of("index_search_type", "1"),
                                            index))
                            .conversions()
                            .size());
        }
    }

    @Nested
    @DisplayName("a preset of another release: the compatibility check in words")
    class Compatibility {

        @Test
        @DisplayName("a ^ slot made on 2026.03.0 cannot be held by 2026.02.2: a problem, no row")
        void caretOnOlderRelease() {
            ParameterSession newer = startingIn(C03);
            Preset caret =
                    Preset.fromModel(
                            "caret",
                            "Protein N-term acetyl",
                            "CONSTRUCTED user preset made on 2026.03.0",
                            newer.model()
                                    .withText(
                                            "variable_mod02",
                                            "42.010565 ^ 0 1 -1 0 0 0.0",
                                            ValueOrigin.USER)
                                    .withText("num_threads", "4", ValueOrigin.USER),
                            List.of("variable_mod02", "num_threads"));
            ParameterSession older = startingIn(C02);
            CometParameters before = older.model();
            PresetsViewModel presets = new PresetsViewModel(older, List.of(caret));
            PresetPreview preview = presets.preview(caret);
            assertEquals(
                    List.of("num_threads"),
                    preview.rows().stream().map(PresetRowViewModel::parameter).toList());
            assertEquals(
                    List.of(
                            "This release cannot hold the value -- variable_mod02 = 42.010565 ^ 0"
                                    + " 1 -1 0 0 0.0 (Comet 2026.03.0) cannot be written for Comet"
                                    + " 2026.02.2: \"^\" holds '^', which Comet 2026.02.2 does"
                                    + " not accept in a residue token; its residue alphabet is"
                                    + " A-Z, n (N-terminus), c (C-terminus), so it cannot be"
                                    + " written"),
                    preview.problems());
            assertEquals(
                    "Made for Comet 2026.03.0; this configuration is for Comet 2026.02.2",
                    preview.madeFor());
            assertSame(before, older.model());

            assertEquals(EditOutcome.applied(), presets.applyAll());
            assertEquals("0.0 X 0 3 -1 0 0 0.0", older.model().text("variable_mod02"));
            assertEquals("4", older.model().text("num_threads"));
            assertEquals(1, presets.lastApplied().orElseThrow().compatibility().problems().size());

            // the same preset on its own release is a row like any other
            PresetsViewModel own = new PresetsViewModel(newer, List.of(caret));
            assertEquals(
                    List.of("num_threads", "variable_mod02"),
                    own.preview(caret).rows().stream().map(PresetRowViewModel::parameter).toList());
        }

        @Test
        @DisplayName("2026.02.2's index_search_type = 1 is converted for 2026.03.0, and says so")
        void conversion() {
            ParameterSession older = startingIn(C02);
            Preset index =
                    Preset.fromModel(
                            "index",
                            "Index as written",
                            "CONSTRUCTED user preset made on 2026.02.2",
                            older.model(),
                            List.of("index_search_type"));
            ParameterSession newer = startingIn(C03);
            PresetsViewModel presets = new PresetsViewModel(newer, List.of(index));
            PresetPreview preview = presets.preview(index);
            assertEquals(List.of(), preview.rows());
            assertEquals(List.of(), preview.problems());
            assertEquals(1, preview.conversions().size());
            assertTrue(
                    preview.conversions()
                            .get(0)
                            .startsWith(
                                    "Converted -- index_search_type = 1 (Comet 2026.02.2) is"
                                            + " written -1 for Comet 2026.03.0, which means the"
                                            + " same there:"),
                    preview.conversions().get(0));
        }

        @Test
        @DisplayName("a parameter the release lacks is said so in words")
        void notInTarget() {
            VersionConversion.Result missing =
                    new VersionConversion.Result(
                            "pinfile_protein_delimiter",
                            VersionConversion.Status.NOT_IN_TARGET,
                            "",
                            Optional.empty(),
                            "Comet 2024.01.0 has no parameter pinfile_protein_delimiter; it is a"
                                    + " parameter of Comet 2026.02.2");
            assertEquals(
                    "Not in this release -- Comet 2024.01.0 has no parameter"
                            + " pinfile_protein_delimiter; it is a parameter of Comet 2026.02.2",
                    PresetPreview.problemWords(missing));
        }
    }

    @Test
    @DisplayName("a preset applied during a migration review keeps the review in the one report")
    void underReview() {
        ParameterSession session = startingIn(C02);
        session.edit("variable_mod01", "15.9949 M 0 3 2 4 0 0.0");
        session.selectRelease(C03);
        PresetsViewModel presets = new PresetsViewModel(session, List.of());
        presets.preview(builtIn(presets, "low-low"));
        assertEquals(EditOutcome.applied(), presets.applyAll());
        assertTrue(session.review().isPresent());
        assertEquals(
                List.of(Rule.MIGRATION_NEEDS_ATTENTION),
                session.report().errors().stream().map(f -> f.rule()).toList());
        assertEquals(
                List.of(),
                presets.lastApplied().orElseThrow().validation().errors(),
                "the model's own validation of the applied set knows no review; the editor shows"
                        + " the session's");
    }
}
