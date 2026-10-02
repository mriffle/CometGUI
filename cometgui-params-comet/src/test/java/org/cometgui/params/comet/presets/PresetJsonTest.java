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
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.model.ValueOrigin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The JSON form of presets, user presets made from a set, and the preset records' own rules. */
class PresetJsonTest {

    @Test
    @DisplayName("user and built-in presets write and read back as themselves")
    void roundTrip() {
        CometParameters model = Real.newer().withText("num_threads", "4", ValueOrigin.USER);
        Preset mine =
                Preset.fromModel(
                        "mine",
                        "Mine",
                        "Four threads.",
                        model,
                        List.of("num_threads", "decoy_search"));
        List<Preset> presets = List.of(mine, Real.builtIn("high-low"));
        String json = PresetJson.write("constructed for a test", presets, Real.METADATA);
        assertEquals(presets, PresetLoader.load(json, Real.METADATA));
        assertTrue(json.contains("\"cometVersion\": \"2026.02.2\""), json);
        assertTrue(json.contains("\"source\": null"), json);
        assertEquals(
                List.of(
                        new PresetDelta("decoy_search", "0", Optional.empty()),
                        new PresetDelta("num_threads", "4", Optional.empty())),
                mine.deltas());
        assertEquals(Preset.Origin.USER, mine.origin());
        assertEquals(1, mine.schemaVersion());
        assertEquals(Optional.empty(), mine.source());
        String bundled = PresetJson.write("again", Real.BUILT_IN, Real.METADATA);
        assertEquals(Real.BUILT_IN, PresetLoader.load(bundled, Real.METADATA));
    }

    @Test
    @DisplayName("a value the secret rules would redact is refused, not saved changed")
    void aRedactedValueIsRefused() {
        Preset leaky = Real.user(Real.NEWER, "decoy_prefix", "Bearer abcdef0123456789");
        IllegalStateException failure =
                assertThrows(
                        IllegalStateException.class,
                        () -> PresetJson.write("x", List.of(leaky), Real.METADATA));
        assertTrue(failure.getMessage().contains("does not read back"), failure.getMessage());
    }

    @Test
    @DisplayName("a user preset records only parameters the set's version has")
    void fromModelRules() {
        CometParameters older = Real.older();
        IllegalArgumentException failure =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                Preset.fromModel(
                                        "x",
                                        "X",
                                        "x",
                                        older,
                                        List.of("pinfile_protein_delimiter")));
        assertEquals(
                "pinfile_protein_delimiter is not a parameter of Comet 2024.01.0, so a preset"
                        + " cannot record it",
                failure.getMessage());
        Preset made = Preset.fromModel("x", "X", "x", older, List.of("num_threads"));
        assertEquals(Real.OLDER, made.cometVersion());
        assertThrows(
                IllegalArgumentException.class,
                () -> Preset.fromModel("x", "X", "x", older, List.of()));
    }

    @Test
    @DisplayName("the records refuse a preset that is not one")
    void recordRules() {
        PresetDelta delta = new PresetDelta("num_threads", "4", Optional.empty());
        assertThrows(
                IllegalArgumentException.class, () -> new PresetDelta(" ", "4", Optional.empty()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new PresetDelta("num_threads", "4", Optional.of(" ")));
        assertThrows(IllegalArgumentException.class, () -> preset("Bad", "N", "d", delta));
        assertThrows(IllegalArgumentException.class, () -> preset("-bad", "N", "d", delta));
        assertThrows(IllegalArgumentException.class, () -> preset("ok", " ", "d", delta));
        assertThrows(IllegalArgumentException.class, () -> preset("ok", "N", " ", delta));
        assertThrows(IllegalArgumentException.class, () -> preset("ok", "N", "d"));
        assertThrows(IllegalArgumentException.class, () -> preset("ok", "N", "d", delta, delta));
        Preset good = preset("ok-2", "N", "d", delta);
        assertThrows(UnsupportedOperationException.class, () -> good.deltas().clear());
        assertEquals(Optional.of(delta), good.delta("num_threads"));
    }

    private static Preset preset(
            String id, String name, String description, PresetDelta... deltas) {
        return new Preset(
                id,
                name,
                description,
                Preset.Origin.USER,
                Real.NEWER,
                1,
                Optional.empty(),
                List.of(deltas));
    }
}
