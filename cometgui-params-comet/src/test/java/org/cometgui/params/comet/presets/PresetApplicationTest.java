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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.model.ValueOrigin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** One application of a preset, and whether a configuration is still described by it (P13-4). */
class PresetApplicationTest {

    private static AppliedPreset lowLowOnNewer() {
        return PresetDiff.of(Real.newer(), Real.builtIn("low-low")).applyAll();
    }

    @Test
    @DisplayName("of: the preset's identity and every applied value, in schema order")
    void of() {
        PresetApplication application =
                PresetApplication.of(Real.builtIn("low-low"), lowLowOnNewer());
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("peptide_mass_tolerance_upper", "3.0");
        expected.put("peptide_mass_tolerance_lower", "-3.0");
        expected.put("peptide_mass_units", "0");
        expected.put("precursor_tolerance_type", "0");
        expected.put("isotope_error", "0");
        expected.put("fragment_bin_tol", "1.0005");
        expected.put("fragment_bin_offset", "0.4");
        expected.put("theoretical_fragment_ions", "1");
        assertEquals("low-low", application.id());
        assertEquals("Low-res precursor, low-res fragments", application.displayName());
        assertEquals(Preset.Origin.BUILT_IN, application.origin());
        assertEquals("2026.02.2", application.cometVersion().text());
        assertEquals(1, application.schemaVersion());
        assertEquals(expected, application.values());
        assertEquals(List.copyOf(expected.keySet()), List.copyOf(application.values().keySet()));
        assertThrows(
                UnsupportedOperationException.class,
                () -> application.values().put("num_threads", "1"));
    }

    @Test
    @DisplayName("of: only the rows applied")
    void ofSelected() {
        AppliedPreset applied =
                PresetDiff.of(Real.newer(), Real.builtIn("low-low"))
                        .applySelected(List.of("fragment_bin_offset"));
        assertEquals(
                Map.of("fragment_bin_offset", "0.4"),
                PresetApplication.of(Real.builtIn("low-low"), applied).values());
    }

    @Test
    @DisplayName("an application that set nothing is refused, naming the preset")
    void nothingApplied() {
        AppliedPreset none =
                PresetDiff.of(Real.newer(), Real.builtIn("low-low")).applySelected(List.of());
        IllegalArgumentException refused =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> PresetApplication.of(Real.builtIn("low-low"), none));
        assertEquals(
                "an application of preset low-low set no value, so it is not one",
                refused.getMessage());
    }

    @Test
    @DisplayName("the values are copied: a later change to the caller's map changes nothing")
    void copied() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("num_threads", "4");
        PresetApplication application =
                new PresetApplication("p", "P", Preset.Origin.USER, Real.NEWER, 1, values);
        values.put("fragment_bin_tol", "0.5");
        assertEquals(Map.of("num_threads", "4"), application.values());
    }

    @Test
    @DisplayName(
            "describes: while one value it set is in place with origin PRESET; not after every one"
                    + " was edited, reset, imported over or replaced by another preset")
    void describes() {
        AppliedPreset applied = lowLowOnNewer();
        PresetApplication application = PresetApplication.of(Real.builtIn("low-low"), applied);
        CometParameters model = applied.model();
        assertTrue(application.describes(model));

        CometParameters allButOne = model;
        for (String name : application.values().keySet()) {
            if (!"fragment_bin_offset".equals(name)) {
                allButOne = allButOne.resetToDefault(name);
            }
        }
        assertTrue(application.describes(allButOne), "one value still in place");

        assertFalse(
                application.describes(
                        allButOne.withText("fragment_bin_offset", "0.4", ValueOrigin.USER)),
                "the same text, but the user's");
        assertFalse(
                application.describes(
                        allButOne.withText("fragment_bin_offset", "0.4", ValueOrigin.IMPORTED)),
                "the same text, but imported");
        assertFalse(
                application.describes(
                        allButOne.withText("fragment_bin_offset", "0.3", ValueOrigin.PRESET)),
                "another preset's value");
        assertFalse(application.describes(allButOne.resetToDefault("fragment_bin_offset")));
        assertFalse(application.describes(Real.newer()), "the configuration it was applied to");
    }

    @Test
    @DisplayName(
            "describes: a parameter the configuration's release does not model counts for nothing")
    void notModelled() {
        PresetApplication application =
                new PresetApplication(
                        "p",
                        "P",
                        Preset.Origin.USER,
                        Real.NEWER,
                        1,
                        Map.of("no_such_parameter", "1"));
        assertFalse(application.describes(Real.newer()));
    }
}
