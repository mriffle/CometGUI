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
import org.cometgui.params.comet.value.VariableModCodec;
import org.cometgui.params.comet.value.VariableModSlots;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The bundled common-modification presets -- masses typed here by hand from the Unimod records the
 * file cites -- which release offers which, and the loader's refusal of a CONSTRUCTED bad document
 * per rule.
 */
class ModificationPresetsTest {

    private static final CuratedMetadata METADATA = MetadataLoader.loadBundled();

    private static final ToolVersion C03 = ToolVersion.parse("2026.03.0");

    private static final ToolVersion C02 = ToolVersion.parse("2026.02.2");

    private static final String PRESET =
            """
            {"id": "oxidation-m", "name": "Oxidation", "description": "Oxidised methionine.",
             "cometVersion": "2026.02.2", "tuple": "15.994915 M 0 3 -1 0 0 0.0",
             "massSource": "https://www.unimod.org/modifications_view.php?editid1=35 15.994915",
             "formSource": "https://uwpr.github.io/Comet/x"}""";

    private static String document(String... presets) {
        return "{\"modificationPresetFormat\": 1, \"description\": \"constructed\", \"presets\": ["
                + String.join(",", presets)
                + "]}";
    }

    private static InvalidPresetException rejected(String json, String field, String fragment) {
        InvalidPresetException failure =
                assertThrows(
                        InvalidPresetException.class,
                        () -> ModificationPresets.load(json, METADATA));
        assertEquals(field, failure.field(), failure.getMessage());
        assertTrue(failure.getMessage().contains(fragment), failure.getMessage());
        return failure;
    }

    @Test
    @DisplayName("six presets load, each mass Unimod's and quoted by its source")
    void theBundledPresets() {
        ModificationPresets presets = ModificationPresets.loadBundled(METADATA);
        assertEquals(
                List.of(
                        "oxidation-m",
                        "phospho-sty",
                        "acetyl-protein-n-term",
                        "acetyl-protein-n-term-caret",
                        "deamidation-nq",
                        "gln-pyro-glu"),
                presets.all().stream().map(ModificationPreset::id).toList());
        assertEquals(
                List.of(
                        "15.994915 M 0 3 -1 0 0 0.0",
                        "79.966331 STY 0 3 -1 0 0 0.0",
                        "42.010565 n 0 1 0 0 0 0.0",
                        "42.010565 ^ 0 1 -1 0 0 0.0",
                        "0.984016 NQ 0 3 -1 0 0 0.0",
                        "-17.026549 Q 0 1 0 2 0 0.0"),
                presets.all().stream().map(ModificationPreset::tuple).toList());
        assertEquals(
                List.of(
                        "Oxidation: +15.994915 on M; max 3 per peptide; optional",
                        "Phospho: +79.966331 on STY; max 3 per peptide; optional",
                        "Acetyl: +42.010565 on N-terminus, only at the protein N-terminus; max 1"
                                + " per peptide; optional",
                        "Acetyl: +42.010565 on protein N-terminus; max 1 per peptide; optional",
                        "Deamidation: +0.984016 on NQ; max 3 per peptide; optional",
                        "Gln->pyro-Glu: -17.026549 on Q, only at the peptide N-terminus; max 1 per"
                                + " peptide; optional"),
                presets.all().stream().map(ModificationPreset::summary).toList());
        List<String> unimod =
                List.of(
                        "editid1=35 ",
                        "editid1=21 ",
                        "editid1=1 ",
                        "editid1=1 ",
                        "editid1=7 ",
                        "editid1=28 ");
        for (int index = 0; index < unimod.size(); index++) {
            ModificationPreset preset = presets.all().get(index);
            assertTrue(
                    preset.massSource()
                            .startsWith(
                                    "https://www.unimod.org/modifications_view.php?"
                                            + unimod.get(index)),
                    preset.massSource());
            assertTrue(preset.formSource().startsWith("https://"), preset.formSource());
        }
        assertEquals(C03, presets.byId("acetyl-protein-n-term-caret").orElseThrow().writtenFor());
        assertEquals(C02, presets.byId("oxidation-m").orElseThrow().writtenFor());
        assertEquals(Optional.empty(), presets.byId("nonesuch"));
    }

    @Test
    @DisplayName("2026.03.0 offers all six; 2026.02.2 not the one written with ^")
    void offeredPerRelease() {
        ModificationPresets presets = ModificationPresets.loadBundled(METADATA);
        assertEquals(
                List.of(
                        "oxidation-m",
                        "phospho-sty",
                        "acetyl-protein-n-term",
                        "acetyl-protein-n-term-caret",
                        "deamidation-nq",
                        "gln-pyro-glu"),
                presets.offeredIn(VariableModSlots.forRelease(METADATA, C03)).stream()
                        .map(ModificationPreset::id)
                        .toList());
        assertEquals(
                List.of(
                        "oxidation-m",
                        "phospho-sty",
                        "acetyl-protein-n-term",
                        "deamidation-nq",
                        "gln-pyro-glu"),
                presets.offeredIn(VariableModSlots.forRelease(METADATA, C02)).stream()
                        .map(ModificationPreset::id)
                        .toList());
        assertEquals(
                "42.010565 ^ 0 1 -1 0 0 0.0",
                VariableModCodec.forVersion(METADATA, C03)
                        .format(
                                presets.byId("acetyl-protein-n-term-caret")
                                        .orElseThrow()
                                        .modification()));
    }

    @Test
    @DisplayName("a constructed document with one good preset loads")
    void constructedGood() {
        ModificationPresets presets = ModificationPresets.load(document(PRESET), METADATA);
        assertEquals(1, presets.all().size());
        assertEquals("Oxidised methionine.", presets.all().get(0).description());
    }

    @Test
    @DisplayName("each rule refuses its constructed breach, naming the field")
    void refusals() {
        rejected("not json", "(none)", "");
        rejected(
                document(PRESET)
                        .replace(
                                "\"modificationPresetFormat\": 1",
                                "\"modificationPresetFormat\": 2"),
                "modificationPresetFormat",
                "is 2, and this loader reads format 1 only");
        rejected(
                document(PRESET).replace("\"description\": \"constructed\", ", ""),
                "description",
                "is missing");
        rejected(
                document(PRESET)
                        .replace(
                                "{\"modificationPresetFormat\"",
                                "{\"x\": 1, \"modificationPresetFormat\""),
                "x",
                "is not a field this format has");
        rejected(document(PRESET, PRESET), "id", "is used by two presets");
        rejected(
                document(PRESET.replace("oxidation-m", "Oxidation M")), "id", "is not a preset id");
        rejected(
                document(PRESET.replace("\"2026.02.2\"", "\"2025.01.0\"")),
                "cometVersion",
                "is Comet 2025.01.0, which the curated metadata does not describe");
        rejected(document(PRESET.replace("\"2026.02.2\"", "\"latest\"")), "cometVersion", "latest");
        rejected(
                document(
                        PRESET.replace("15.994915 M 0 3 -1 0 0 0.0", "15.994915 ^ 0 3 -1 0 0 0.0")),
                "tuple",
                "which Comet 2026.02.2 does not accept in a residue token");
        rejected(
                document(PRESET.replace("15.994915 M 0 3 -1 0 0 0.0", "15.994915 M 0 3")),
                "tuple",
                "holds 4 fields");
        rejected(
                document(
                        PRESET.replace("15.994915 M 0 3 -1 0 0 0.0", "0.0 M 0 3 -1 0 0 0.0")
                                .replace("editid1=35 15.994915", "editid1=35 0.0")),
                "tuple",
                "has a mass difference of 0, which leaves a slot unused");
        rejected(
                document(PRESET.replace("editid1=35 15.994915", "editid1=35 15.9949")),
                "massSource",
                "does not quote the mass difference 15.994915 the tuple writes");
        rejected(
                document(PRESET.replace("https://www.unimod.org", "http://www.unimod.org")),
                "massSource",
                "cites no https:// reference");
        rejected(
                document(PRESET.replace("https://uwpr.github.io/Comet/x", "the page")),
                "formSource",
                "\"the page\" cites no https:// reference");
        rejected(
                document(PRESET.replace("\"name\": \"Oxidation\"", "\"name\": \" \"")),
                "name",
                "is blank");
        rejected(document(PRESET.replace("\"name\"", "\"label\"")), "name", "is missing");
        rejected(
                document(PRESET.replace("{\"id\"", "{\"extra\": 1, \"id\"")),
                "extra",
                "is not a field this format has");
    }
}
