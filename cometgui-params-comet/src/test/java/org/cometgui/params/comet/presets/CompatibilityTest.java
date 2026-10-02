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

import java.util.List;
import java.util.Optional;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.fixtures.ConstructedVersions;
import org.cometgui.params.comet.migration.VersionConversion;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.model.ValueOrigin;
import org.cometgui.params.comet.schema.CuratedMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A preset made for one Comet version applied to another: every delta the target cannot take is
 * reported, none dropped silently; every delta it can take is diffed and applied as usual.
 */
class CompatibilityTest {

    /** A user's preset made from the real 2026.02.2 set, with three CONSTRUCTED user changes. */
    private static Preset madeFor2026() {
        CometParameters edited =
                Real.newer()
                        .withText("num_threads", "4", ValueOrigin.USER)
                        .withText("pinfile_protein_delimiter", ";", ValueOrigin.USER)
                        .withText(
                                "variable_mod02",
                                "79.966331 STY 0 3 -1 0 0 97.976896,79.966331",
                                ValueOrigin.USER);
        return Preset.fromModel(
                "my-phospho",
                "My phospho search",
                "A user's preset, made on Comet 2026.02.2.",
                edited,
                List.of("variable_mod02", "pinfile_protein_delimiter", "num_threads"));
    }

    @Test
    @DisplayName("a 2026.02.2 user preset on a 2024.01.0 set reports both deltas it cannot take")
    void everyUntakeableDeltaIsReported() {
        Preset preset = madeFor2026();
        assertEquals(Real.NEWER, preset.cometVersion());
        assertEquals(
                List.of("num_threads", "variable_mod02", "pinfile_protein_delimiter"),
                preset.deltas().stream().map(PresetDelta::parameter).toList(),
                "a user preset lists its deltas in schema order");
        CometParameters target = Real.older();
        PresetDiff diff = PresetDiff.of(target, preset);
        CompatibilityReport check = diff.compatibility();
        assertEquals(Real.NEWER, check.presetVersion());
        assertEquals(Real.OLDER, check.targetVersion());
        assertEquals(
                preset.deltas().stream().map(PresetDelta::parameter).toList(),
                check.entries().stream().map(VersionConversion.Result::parameter).toList(),
                "one entry per delta: nothing can go missing between the preset and its diff");
        assertEquals(
                List.of(
                        "variable_mod02 NOT_CONVERTIBLE",
                        "pinfile_protein_delimiter NOT_IN_TARGET"),
                check.problems().stream().map(r -> r.parameter() + " " + r.status()).toList());
        assertFalse(check.isClean());
        assertEquals(List.of(), check.converted());
        assertEquals(List.of("num_threads"), diff.rows().stream().map(DiffRow::key).toList());
        AppliedPreset applied = diff.applyAll();
        assertEquals("4", applied.model().text("num_threads"));
        assertEquals(target.text("variable_mod02"), applied.model().text("variable_mod02"));
        assertEquals(List.of(), applied.model().unknownParameters());
        assertEquals(check.problems(), applied.compatibility().problems());
        assertTrue(
                check.entry("variable_mod02").orElseThrow().explanation().contains("two neutral"),
                check.entry("variable_mod02").orElseThrow().explanation());
        assertEquals(Optional.empty(), check.entry("decoy_search"));
    }

    @Test
    @DisplayName("the built-in 2026.02.2 presets apply to a 2024.01.0 set, every delta the same")
    void builtInsOnTheOlderVersion() {
        CometParameters target = Real.older();
        PresetDiff diff = PresetDiff.of(target, Real.builtIn("low-low"));
        assertTrue(diff.compatibility().isClean());
        assertEquals(8, diff.compatibility().entries().size());
        assertEquals(8, diff.rows().size());
        assertEquals(Real.OLDER, diff.applyAll().model().version());
    }

    @Test
    @DisplayName("a 2024.01.0 user preset on a 2026.02.2 set: every delta the same")
    void olderPresetOnTheNewerVersion() {
        Preset preset =
                Real.user(
                        Real.OLDER,
                        "variable_mod01",
                        "79.966331 STY 0 2,3 -1 0 -1 97.976896",
                        "fragindex_num_spectrumpeaks",
                        "100");
        PresetDiff diff = PresetDiff.of(Real.newer(), preset);
        assertTrue(diff.compatibility().isClean());
        assertEquals(
                List.of("variable_mod01", "fragindex_num_spectrumpeaks"),
                diff.rows().stream().map(DiffRow::key).toList());
    }

    @Test
    @DisplayName("a delta whose syntax changed is converted, reported, and applied in the new form")
    void aConvertedDeltaIsReportedAndApplied() {
        CuratedMetadata metadata =
                ConstructedVersions.withConstructedVersion(
                        String.join(
                                ", ",
                                ConstructedVersions.field("MASS", "DECIMAL", false),
                                ConstructedVersions.field("RESIDUES", "RESIDUES", false),
                                ConstructedVersions.field("BINARY_GROUP", "INTEGER", false),
                                ConstructedVersions.field("COUNT", "INTEGER", true),
                                ConstructedVersions.field("TERMINAL_DISTANCE", "INTEGER", false),
                                ConstructedVersions.field("TERMINUS", "INTEGER", false),
                                ConstructedVersions.field("REQUIRED", "INTEGER", false)),
                        ConstructedVersions.sevenFieldTupleDefaults());
        ToolVersion constructed = ToolVersion.parse("2099.01.0");
        CometParameters target =
                CometParameters.defaults(metadata, constructed, Real.newer().enzymeTable());
        Preset preset = Real.user(Real.NEWER, "variable_mod01", "79.966331 STY 0 3 -1 0 1 0.0");
        PresetDiff diff = PresetDiff.of(target, preset);
        assertEquals(1, diff.compatibility().converted().size());
        assertFalse(diff.compatibility().isClean());
        assertEquals(List.of(), diff.compatibility().problems());
        assertEquals(
                List.of(
                        new DiffRow(
                                DiffRow.Kind.PARAMETER,
                                "variable_mod01",
                                Optional.of("15.9949 M 0 3 -1 0 0"),
                                Optional.of("79.966331 STY 0 3 -1 0 1"))),
                diff.rows());
        AppliedPreset applied = diff.applyAll();
        assertEquals("79.966331 STY 0 3 -1 0 1", applied.model().text("variable_mod01"));
        assertEquals(ValueOrigin.PRESET, applied.model().origin("variable_mod01"));
    }

    @Test
    @DisplayName("a preset for a version the metadata does not describe cannot be diffed")
    void anUncuratedPresetVersionIsRefused() {
        Preset preset = Real.user(ToolVersion.parse("2019.01.5"), "num_threads", "4");
        IllegalArgumentException failure =
                assertThrows(
                        IllegalArgumentException.class, () -> PresetDiff.of(Real.newer(), preset));
        assertTrue(failure.getMessage().contains("2019.01.5"), failure.getMessage());
    }

    @Test
    void reportRules() {
        VersionConversion.Result one =
                new VersionConversion.Result(
                        "x", VersionConversion.Status.SAME, "1", Optional.of("1"), "why");
        assertThrows(
                IllegalArgumentException.class,
                () -> new CompatibilityReport(Real.NEWER, Real.OLDER, List.of(one, one)));
        CompatibilityReport report = new CompatibilityReport(Real.NEWER, Real.OLDER, List.of(one));
        assertThrows(UnsupportedOperationException.class, () -> report.entries().clear());
        assertTrue(report.isClean());
    }
}
