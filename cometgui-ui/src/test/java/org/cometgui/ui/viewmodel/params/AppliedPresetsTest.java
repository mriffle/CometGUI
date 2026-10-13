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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.cometgui.params.comet.migration.SchemaMigration;
import org.cometgui.params.comet.model.ValueOrigin;
import org.cometgui.params.comet.parser.ReleaseDefaults;
import org.cometgui.params.comet.presets.Preset;
import org.cometgui.params.comet.presets.PresetApplication;
import org.cometgui.params.comet.presets.PresetDelta;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The presets a session remembers for a run's provenance (P13-4): every application in order, kept
 * through edits of the same configuration, and forgotten by everything that starts afresh or
 * migrates -- a new configuration, an imported file, a release change. Every expected value is
 * typed by hand.
 */
class AppliedPresetsTest {

    /** Low-low's eight values, as the configuration holds them after it is applied. */
    private static final Map<String, String> LOW_LOW = lowLow();

    private static Map<String, String> lowLow() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("peptide_mass_tolerance_upper", "3.0");
        values.put("peptide_mass_tolerance_lower", "-3.0");
        values.put("peptide_mass_units", "0");
        values.put("precursor_tolerance_type", "0");
        values.put("isotope_error", "0");
        values.put("fragment_bin_tol", "1.0005");
        values.put("fragment_bin_offset", "0.4");
        values.put("theoretical_fragment_ions", "1");
        return values;
    }

    private static PresetsViewModel presets(ParameterSession session, Preset... mine) {
        return new PresetsViewModel(session, List.of(mine));
    }

    private static void applyAll(PresetsViewModel presets, String id) {
        presets.preview(
                presets.presets().stream()
                        .filter(p -> p.id().equals(id))
                        .findFirst()
                        .orElseThrow());
        assertEquals(EditOutcome.applied(), presets.applyAll());
    }

    /** A CONSTRUCTED user preset: test input. */
    private static Preset threads(String value) {
        return new Preset(
                "threads-" + value,
                "Threads " + value,
                "A constructed user preset.",
                Preset.Origin.USER,
                C03,
                1,
                Optional.empty(),
                List.of(new PresetDelta("num_threads", value, Optional.empty())));
    }

    @Test
    @DisplayName("a new session has applied no preset")
    void none() {
        assertEquals(List.of(), startingIn(C03).appliedPresets());
    }

    @Test
    @DisplayName("applying a preset remembers its identity and every value it set, in order")
    void applied() {
        ParameterSession session = startingIn(C03);
        applyAll(presets(session), "low-low");
        assertEquals(
                List.of(
                        new PresetApplication(
                                "low-low",
                                "Low-res precursor, low-res fragments",
                                Preset.Origin.BUILT_IN,
                                C02,
                                1,
                                LOW_LOW)),
                session.appliedPresets());
        assertEquals(
                List.copyOf(LOW_LOW.keySet()),
                List.copyOf(session.appliedPresets().get(0).values().keySet()));
        assertEquals(ValueOrigin.PRESET, session.model().origin("fragment_bin_tol"));
    }

    @Test
    @DisplayName("applying selected rows remembers exactly those values")
    void selected() {
        ParameterSession session = startingIn(C03);
        PresetsViewModel presets = presets(session);
        PresetPreview preview =
                presets.preview(
                        presets.presets().stream()
                                .filter(p -> "low-low".equals(p.id()))
                                .findFirst()
                                .orElseThrow());
        for (PresetRowViewModel row : preview.rows()) {
            row.setSelected("fragment_bin_tol".equals(row.parameter()));
        }
        assertEquals(EditOutcome.applied(), presets.applySelected());
        assertEquals(1, session.appliedPresets().size());
        assertEquals(
                Map.of("fragment_bin_tol", "1.0005"), session.appliedPresets().get(0).values());
    }

    @Test
    @DisplayName(
            "edits, resets of a field, a raw apply and a second preset keep the applications, in"
                    + " order")
    void editsKeep() {
        ParameterSession session = startingIn(C03);
        Preset four = threads("4");
        PresetsViewModel presets = presets(session, four);
        applyAll(presets, "low-low");
        assertEquals(EditOutcome.applied(), session.edit("fragment_bin_tol", "0.5"));
        session.resetField("isotope_error");
        session.adopt(
                session.model().withText("num_threads", "2", ValueOrigin.USER),
                Adoption.RAW_APPLIED);
        applyAll(presets, "threads-4");
        assertEquals(
                List.of("low-low", "threads-4"),
                session.appliedPresets().stream().map(PresetApplication::id).toList());
        assertEquals(Map.of("num_threads", "4"), session.appliedPresets().get(1).values());
        assertEquals(LOW_LOW, session.appliedPresets().get(0).values());
    }

    @Test
    @DisplayName("a new configuration and starting again forget every application")
    void newConfigurationForgets() {
        ParameterSession session = startingIn(C03);
        applyAll(presets(session), "low-low");
        session.resetAll();
        assertEquals(List.of(), session.appliedPresets());

        applyAll(presets(session), "low-low");
        session.newConfiguration(C03);
        assertEquals(List.of(), session.appliedPresets());
    }

    @Test
    @DisplayName("an imported file forgets every application")
    void importForgets() {
        ParameterSession session = startingIn(C03);
        applyAll(presets(session), "low-low");
        session.adopt(
                ReleaseDefaults.load(Sessions.METADATA, C03)
                        .withText("num_threads", "8", ValueOrigin.IMPORTED),
                Adoption.IMPORTED);
        assertEquals(List.of(), session.appliedPresets());
    }

    @Test
    @DisplayName(
            "a release change forgets every application; the migration carries the values'"
                    + " PRESET origin, so a run records that origin with no preset named")
    void releaseChangeForgets() {
        ParameterSession session = startingIn(C03);
        applyAll(presets(session), "low-low");
        assertEquals(EditOutcome.applied(), session.selectRelease(C02));
        assertEquals(C02, session.release());
        assertEquals(List.of(), session.appliedPresets());
        assertEquals(ValueOrigin.PRESET, session.model().origin("fragment_bin_tol"));

        applyAll(presets(session), "high-high");
        assertEquals(1, session.appliedPresets().size());
        session.adoptMigration(SchemaMigration.migrate(session.model(), C03));
        assertEquals(List.of(), session.appliedPresets());
    }

    @Test
    @DisplayName("a preset's set of another release is refused, and nothing is remembered")
    void anotherReleaseRefused() {
        ParameterSession session = startingIn(C03);
        ParameterSession other = startingIn(C02);
        PresetsViewModel elsewhere = presets(other);
        applyAll(elsewhere, "low-low");
        Preset lowLow =
                elsewhere.presets().stream()
                        .filter(p -> "low-low".equals(p.id()))
                        .findFirst()
                        .orElseThrow();
        assertThrows(
                IllegalArgumentException.class,
                () -> session.adoptPreset(lowLow, elsewhere.lastApplied().orElseThrow()));
        assertEquals(List.of(), session.appliedPresets());
    }
}
