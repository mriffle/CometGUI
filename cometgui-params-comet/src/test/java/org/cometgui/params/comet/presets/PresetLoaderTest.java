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
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.schema.CuratedMetadata;
import org.cometgui.params.comet.schema.MetadataLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The bundled presets, and the loader's refusal of a CONSTRUCTED bad preset per rule. */
class PresetLoaderTest {

    private static final CuratedMetadata METADATA = MetadataLoader.loadBundled();

    private static final String GOOD = Docs.userDelta("num_threads", "4");

    private static InvalidPresetException rejected(
            String json, String where, String field, String fragment) {
        InvalidPresetException failure =
                assertThrows(InvalidPresetException.class, () -> PresetLoader.load(json, METADATA));
        assertEquals(where, failure.where(), failure.getMessage());
        assertEquals(field, failure.field(), failure.getMessage());
        assertTrue(failure.getMessage().contains(fragment), failure.getMessage());
        return failure;
    }

    @Test
    @DisplayName(
            "the three instrument-resolution presets load, for Comet 2026.02.2, every value cited")
    void theBuiltInsLoad() {
        List<Preset> presets = PresetLoader.loadBundled(METADATA);
        assertEquals(
                List.of("low-low", "high-low", "high-high"),
                presets.stream().map(Preset::id).toList());
        for (Preset preset : presets) {
            assertEquals(Preset.Origin.BUILT_IN, preset.origin());
            assertEquals(ToolVersion.parse("2026.02.2"), preset.cometVersion());
            assertEquals(1, preset.schemaVersion());
            assertEquals(
                    Optional.of(
                            "https://uwpr.github.io/Comet/parameters/parameters_202602/comet.params."
                                    + preset.id()),
                    preset.source());
            assertEquals(
                    List.of(
                            "peptide_mass_tolerance_upper",
                            "peptide_mass_tolerance_lower",
                            "peptide_mass_units",
                            "precursor_tolerance_type",
                            "isotope_error",
                            "fragment_bin_tol",
                            "fragment_bin_offset",
                            "theoretical_fragment_ions"),
                    preset.deltas().stream().map(PresetDelta::parameter).toList());
            for (PresetDelta delta : preset.deltas()) {
                assertTrue(
                        delta.citation().orElseThrow().contains("comet.params." + preset.id()),
                        delta.citation().orElseThrow());
            }
        }
        Preset lowLow = presets.get(0);
        assertEquals("3.0", lowLow.delta("peptide_mass_tolerance_upper").orElseThrow().value());
        assertEquals("-3.0", lowLow.delta("peptide_mass_tolerance_lower").orElseThrow().value());
        assertEquals("0", lowLow.delta("peptide_mass_units").orElseThrow().value());
        assertEquals("1.0005", lowLow.delta("fragment_bin_tol").orElseThrow().value());
        assertEquals("0.4", lowLow.delta("fragment_bin_offset").orElseThrow().value());
        assertEquals("1", lowLow.delta("theoretical_fragment_ions").orElseThrow().value());
        Preset highHigh = presets.get(2);
        assertEquals("0.02", highHigh.delta("fragment_bin_tol").orElseThrow().value());
        assertEquals("2", highHigh.delta("peptide_mass_units").orElseThrow().value());
        assertEquals(Optional.empty(), highHigh.delta("num_threads"));
    }

    @Test
    void aValidUserPresetLoads() {
        List<Preset> presets = PresetLoader.load(Docs.document(Docs.user("mine", GOOD)), METADATA);
        Preset preset = presets.get(0);
        assertEquals(Preset.Origin.USER, preset.origin());
        assertEquals(Optional.empty(), preset.source());
        assertEquals(
                List.of(new PresetDelta("num_threads", "4", Optional.empty())), preset.deltas());
        assertEquals("Constructed mine", preset.displayName());
    }

    @Test
    void aPresetFieldTheFormatLacksIsRefused() {
        rejected(
                Docs.document(Docs.user("mine", GOOD).replace("{\"id\"", "{\"since\": 1, \"id\"")),
                "preset \"mine\"",
                "since",
                "is not a field this format has");
    }

    @Test
    void anUnknownParameterIsRefused() {
        rejected(
                Docs.document(Docs.user("mine", Docs.userDelta("no_such_knob", "1"))),
                "preset \"mine\" delta \"no_such_knob\"",
                "parameter",
                "is not a parameter CometGUI models for any Comet version");
    }

    @Test
    void aParameterOfAnotherVersionIsRefused() {
        rejected(
                Docs.document(
                        Docs.preset(
                                "old",
                                "USER",
                                "2024.01.0",
                                1,
                                null,
                                Docs.userDelta("pinfile_protein_delimiter", ";"))),
                "preset \"old\" delta \"pinfile_protein_delimiter\"",
                "parameter",
                "is not a parameter of Comet 2024.01.0, the version the preset was made against");
    }

    @Test
    void anUnreadableValueIsRefused() {
        rejected(
                Docs.document(Docs.user("mine", Docs.userDelta("num_threads", "many"))),
                "preset \"mine\" delta \"num_threads\"",
                "value",
                "\"many\"");
        rejected(
                Docs.document(
                        Docs.preset(
                                "old",
                                "USER",
                                "2024.01.0",
                                1,
                                null,
                                Docs.userDelta(
                                        "variable_mod01",
                                        "79.966331 STY 0 3 -1 0 0 97.976896,79.966331"))),
                "preset \"old\" delta \"variable_mod01\"",
                "value",
                "variable_mod01");
    }

    @Test
    void aMissingTargetVersionIsRefused() {
        String absent =
                Docs.document(
                        Docs.user("mine", GOOD).replace("\"cometVersion\": \"2026.02.2\", ", ""));
        rejected(absent, "preset \"mine\"", "cometVersion", "is missing");
        rejected(
                Docs.document(Docs.preset("mine", "USER", null, 1, null, GOOD)),
                "preset \"mine\"",
                "cometVersion",
                "a preset must record the Comet version it was made against");
        rejected(
                Docs.document(Docs.preset("mine", "USER", " ", 1, null, GOOD)),
                "preset \"mine\"",
                "cometVersion",
                "a preset must record the Comet version it was made against");
    }

    @Test
    void anUncuratedOrUnreadableVersionIsRefused() {
        rejected(
                Docs.document(Docs.preset("mine", "USER", "2019.01.5", 1, null, GOOD)),
                "preset \"mine\"",
                "cometVersion",
                "is Comet 2019.01.5, which the curated metadata does not describe");
        rejected(
                Docs.document(Docs.preset("mine", "USER", "latest", 1, null, GOOD)),
                "preset \"mine\"",
                "cometVersion",
                "latest");
    }

    @Test
    void anotherSchemaIsRefused() {
        rejected(
                Docs.document(Docs.preset("mine", "USER", "2026.02.2", 2, null, GOOD)),
                "preset \"mine\"",
                "schemaVersion",
                "is 2, and the curated metadata is schema 1");
        rejected(
                Docs.document(Docs.preset("mine", "USER", "2026.02.2", "\"1\"", null, GOOD)),
                "preset \"mine\"",
                "schemaVersion",
                "must be a whole number");
    }

    @Test
    void aBuiltInWithoutCitationsIsRefused() {
        rejected(
                Docs.document(
                        Docs.preset(
                                "mine", "BUILT_IN", "2026.02.2", 1, "https://example.org/p", GOOD)),
                "preset \"mine\"",
                PresetLoader.WHOLE_PRESET,
                "does not cite where its num_threads value comes from");
        rejected(
                Docs.document(
                        Docs.preset(
                                "mine",
                                "BUILT_IN",
                                "2026.02.2",
                                1,
                                null,
                                Docs.delta("num_threads", "4", "https://example.org/x"))),
                "preset \"mine\"",
                PresetLoader.WHOLE_PRESET,
                "does not name the document it comes from");
    }

    @Test
    void aCitationWithoutAReferenceIsRefused() {
        rejected(
                Docs.document(Docs.user("mine", Docs.delta("num_threads", "4", "my lab notebook"))),
                "preset \"mine\" delta \"num_threads\"",
                "citation",
                "cites no https:// reference");
        rejected(
                Docs.document(Docs.preset("mine", "USER", "2026.02.2", 1, "a book", GOOD)),
                "preset \"mine\"",
                "source",
                "cites no https:// reference");
    }

    @Test
    void presetRulesAreEnforced() {
        rejected(
                Docs.document(Docs.user("mine")),
                "preset \"mine\"",
                PresetLoader.WHOLE_PRESET,
                "sets no parameter");
        rejected(
                Docs.document(Docs.user("mine", GOOD, Docs.userDelta("num_threads", "8"))),
                "preset \"mine\"",
                PresetLoader.WHOLE_PRESET,
                "sets num_threads twice");
        rejected(
                Docs.document(Docs.user("Mine!", GOOD)),
                "preset \"Mine!\"",
                "id",
                "is not a preset id");
        rejected(
                Docs.document(Docs.user("mine", GOOD), Docs.user("mine", GOOD)),
                "preset \"mine\"",
                "id",
                "is used by two presets");
        rejected(
                Docs.document(Docs.preset("mine", "VENDOR", "2026.02.2", 1, null, GOOD)),
                "preset \"mine\"",
                "origin",
                "\"VENDOR\" is not one of [BUILT_IN, USER]");
    }

    @Test
    void theDocumentShapeIsEnforced() {
        rejected("[", "the document", "(none)", "");
        rejected("[]", "the document", "(root)", "must be a JSON object");
        rejected(
                Docs.document(Docs.user("mine", GOOD))
                        .replace("\"presetFormat\": 1", "\"presetFormat\": 2"),
                "the document",
                "presetFormat",
                "is 2, and this loader reads format 1 only");
        rejected(
                Docs.document(Docs.user("mine", GOOD))
                        .replace("{\"presetFormat\"", "{\"extra\": 1, \"presetFormat\""),
                "the document",
                "extra",
                "is not a field this format has");
        rejected(
                Docs.document(Docs.user("mine", GOOD))
                        .replace(", \"description\": \"constructed\"", ""),
                "the document",
                "description",
                "is missing");
        rejected(
                Docs.document(Docs.user("mine", GOOD))
                        .replace("\"presets\": [", "\"presets\": [3, "),
                "presets[0]",
                "presets",
                "must be a JSON object");
        rejected(
                Docs.document(Docs.user("mine", GOOD).replace("\"value\": \"4\"", "\"value\": 4")),
                "preset \"mine\" delta \"num_threads\"",
                "value",
                "must be a string");
        rejected(
                Docs.document(
                        Docs.user("mine", GOOD)
                                .replaceAll("\"deltas\": \\[.*\\]\\}$", "\"deltas\": \"none\"}")),
                "preset \"mine\"",
                "deltas",
                "must be an array");
        rejected(
                Docs.document(
                        Docs.user("mine", GOOD)
                                .replace(
                                        "\"displayName\": \"Constructed mine\"",
                                        "\"displayName\": \" \"")),
                "preset \"mine\"",
                "displayName",
                "is blank");
        rejected(
                Docs.document(Docs.user("mine", GOOD).replace("\"source\": null", "\"source\": 7")),
                "preset \"mine\"",
                "source",
                "must be a string or null");
        rejected(
                Docs.document(
                        Docs.user("mine", GOOD)
                                .replace(
                                        "\"citation\": null",
                                        "\"citation\": null, \"note\": \"x\"")),
                "preset \"mine\"",
                "note",
                "is not a field this format has");
    }
}
