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

package org.cometgui.workflow.steps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.model.DecoySource;
import org.cometgui.params.comet.model.ValueOrigin;
import org.cometgui.params.comet.parser.ReleaseDefaults;
import org.cometgui.params.comet.presets.AppliedPreset;
import org.cometgui.params.comet.presets.Preset;
import org.cometgui.params.comet.presets.PresetApplication;
import org.cometgui.params.comet.presets.PresetDelta;
import org.cometgui.params.comet.presets.PresetDiff;
import org.cometgui.params.comet.presets.PresetLoader;
import org.cometgui.params.comet.schema.CometVersionRecord;
import org.cometgui.params.comet.schema.CuratedMetadata;
import org.cometgui.params.comet.schema.MetadataLoader;
import org.cometgui.params.comet.schema.ParameterDefinition;
import org.cometgui.provenance.manifest.ProvenanceSchema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The origin and preset settings a search records (P13-4, as amended: parameter names in the
 * values, never in the keys). Every expected map is typed by hand.
 */
class CometParameterProvenanceTest {

    private static final CuratedMetadata METADATA = MetadataLoader.loadBundled();

    private static final Pattern KEY = Pattern.compile(ProvenanceSchema.SETTINGS_KEY_PATTERN);

    private static final ToolVersion OLDER = ToolVersion.parse(RealComet.OLDER);

    /** Comet 2026.02.2's starting set with the user's database, threads and decoys. */
    private static CometParameters configured() {
        return RealComet.model(
                RealComet.OLDER, Path.of("db.fasta"), DecoySource.COMET_INTERNAL_CONCATENATED, 4);
    }

    private static Preset lowLow() {
        return PresetLoader.loadBundled(METADATA).stream()
                .filter(preset -> "low-low".equals(preset.id()))
                .findFirst()
                .orElseThrow();
    }

    /** A CONSTRUCTED user preset: test input. */
    private static Preset user(String id, String name, String parameter, String value) {
        return new Preset(
                id,
                name,
                "A constructed user preset.",
                Preset.Origin.USER,
                OLDER,
                1,
                Optional.empty(),
                List.of(new PresetDelta(parameter, value, Optional.empty())));
    }

    private static final Map<String, String> LOW_LOW_APPLIED =
            Map.of(
                    "comet.preset.01.applied.01", "peptide_mass_tolerance_upper = 3.0",
                    "comet.preset.01.applied.02", "peptide_mass_tolerance_lower = -3.0",
                    "comet.preset.01.applied.03", "peptide_mass_units = 0",
                    "comet.preset.01.applied.04", "precursor_tolerance_type = 0",
                    "comet.preset.01.applied.05", "isotope_error = 0",
                    "comet.preset.01.applied.06", "fragment_bin_tol = 1.0005",
                    "comet.preset.01.applied.07", "fragment_bin_offset = 0.4",
                    "comet.preset.01.applied.08", "theoretical_fragment_ions = 1");

    private static Map<String, String> lowLowIdentity() {
        Map<String, String> identity = new TreeMap<>(LOW_LOW_APPLIED);
        identity.put("comet.preset.01.id", "low-low");
        identity.put("comet.preset.01.name", "Low-res precursor, low-res fragments");
        identity.put("comet.preset.01.origin", "built-in");
        identity.put("comet.preset.01.comet-version", "2026.02.2");
        identity.put("comet.preset.01.schema-version", "1");
        return identity;
    }

    @Test
    @DisplayName(
            "known origins: one list per non-default origin, names in schema order, and nothing"
                    + " for a parameter at Comet's own default")
    void knownOrigins() {
        Map<String, String> expected = new TreeMap<>();
        expected.put("comet.origin.cometgui-default", "spectral_library_name");
        expected.put("comet.origin.user", "database_name decoy_search num_threads");
        expected.put("comet.origin.workflow-enforced", "output_pepxmlfile output_percolatorfile");
        assertEquals(expected, CometParameterProvenance.settings(configured(), List.of()));

        CometParameters more =
                configured()
                        .withText("fragment_bin_tol", "0.5", ValueOrigin.IMPORTED)
                        .withText("add_C_cysteine", "0.0", ValueOrigin.USER)
                        .withText("use_A_ions", "1", ValueOrigin.PRESET);
        expected.put("comet.origin.imported", "fragment_bin_tol");
        expected.put("comet.origin.preset", "use_A_ions");
        expected.put("comet.origin.user", "database_name decoy_search num_threads add_C_cysteine");
        assertEquals(expected, CometParameterProvenance.settings(more, List.of()));
    }

    @Test
    @DisplayName("Comet's own starting set records nothing at all")
    void cometDefaultsRecordNothing() {
        CometParameters defaults =
                CometParameters.defaults(
                        METADATA, OLDER, ReleaseDefaults.load(METADATA, OLDER).enzymeTable());
        assertEquals(Map.of(), CometParameterProvenance.settings(defaults, List.of()));
    }

    @Test
    @DisplayName("a preset applied: its identity, every value it set, its parameters' origin")
    void aPresetApplied() {
        Preset preset = lowLow();
        AppliedPreset applied = PresetDiff.of(configured(), preset).applyAll();
        Map<String, String> expected = lowLowIdentity();
        expected.put("comet.origin.cometgui-default", "spectral_library_name");
        expected.put("comet.origin.user", "database_name decoy_search num_threads");
        expected.put(
                "comet.origin.preset",
                "peptide_mass_tolerance_upper peptide_mass_tolerance_lower peptide_mass_units"
                        + " precursor_tolerance_type isotope_error fragment_bin_tol"
                        + " fragment_bin_offset theoretical_fragment_ions");
        expected.put("comet.origin.workflow-enforced", "output_pepxmlfile output_percolatorfile");
        assertEquals(
                expected,
                CometParameterProvenance.settings(
                        applied.model(), List.of(PresetApplication.of(preset, applied))));
    }

    @Test
    @DisplayName(
            "a preset applied, then one of its parameters edited: that parameter is the user's,"
                    + " and the preset is still named with the value it set")
    void aPresetThenAnEdit() {
        Preset preset = lowLow();
        AppliedPreset applied = PresetDiff.of(configured(), preset).applyAll();
        CometParameters edited =
                applied.model().withText("fragment_bin_tol", "0.5", ValueOrigin.USER);
        Map<String, String> expected = lowLowIdentity();
        expected.put("comet.origin.cometgui-default", "spectral_library_name");
        expected.put(
                "comet.origin.user", "database_name decoy_search num_threads fragment_bin_tol");
        expected.put(
                "comet.origin.preset",
                "peptide_mass_tolerance_upper peptide_mass_tolerance_lower peptide_mass_units"
                        + " precursor_tolerance_type isotope_error fragment_bin_offset"
                        + " theoretical_fragment_ions");
        expected.put("comet.origin.workflow-enforced", "output_pepxmlfile output_percolatorfile");
        assertEquals(
                expected,
                CometParameterProvenance.settings(
                        edited, List.of(PresetApplication.of(preset, applied))));
    }

    @Test
    @DisplayName(
            "a preset none of whose values is still in place is not named, and the next one is"
                    + " numbered 01")
    void aPresetNoLongerDescribing() {
        Preset threads = user("eight-threads", "Eight threads", "num_threads", "8");
        AppliedPreset first = PresetDiff.of(configured(), threads).applyAll();
        PresetApplication stale = PresetApplication.of(threads, first);
        CometParameters edited = first.model().withText("num_threads", "2", ValueOrigin.USER);
        Map<String, String> settings = CometParameterProvenance.settings(edited, List.of(stale));
        assertFalse(settings.keySet().stream().anyMatch(key -> key.startsWith("comet.preset.")));

        Preset bins = user("wide-bins", "Wide bins", "fragment_bin_tol", "1.0005");
        AppliedPreset second = PresetDiff.of(edited, bins).applyAll();
        Map<String, String> both =
                CometParameterProvenance.settings(
                        second.model(), List.of(stale, PresetApplication.of(bins, second)));
        Map<String, String> presets = new TreeMap<>();
        for (Map.Entry<String, String> setting : both.entrySet()) {
            if (setting.getKey().startsWith("comet.preset.")) {
                presets.put(setting.getKey(), setting.getValue());
            }
        }
        assertEquals(
                Map.of(
                        "comet.preset.01.id", "wide-bins",
                        "comet.preset.01.name", "Wide bins",
                        "comet.preset.01.origin", "user",
                        "comet.preset.01.comet-version", "2026.02.2",
                        "comet.preset.01.schema-version", "1",
                        "comet.preset.01.applied.01", "fragment_bin_tol = 1.0005"),
                presets);
        assertEquals("fragment_bin_tol", both.get("comet.origin.preset"));
    }

    @Test
    @DisplayName("two presets still in place are numbered in the order they were applied")
    void twoPresetsInOrder() {
        Preset threads = user("eight-threads", "Eight threads", "num_threads", "8");
        AppliedPreset first = PresetDiff.of(configured(), threads).applyAll();
        Preset bins = user("wide-bins", "Wide bins", "fragment_bin_tol", "1.0005");
        AppliedPreset second = PresetDiff.of(first.model(), bins).applyAll();
        Map<String, String> settings =
                CometParameterProvenance.settings(
                        second.model(),
                        List.of(
                                PresetApplication.of(threads, first),
                                PresetApplication.of(bins, second)));
        assertEquals("eight-threads", settings.get("comet.preset.01.id"));
        assertEquals("num_threads = 8", settings.get("comet.preset.01.applied.01"));
        assertEquals("wide-bins", settings.get("comet.preset.02.id"));
        assertEquals("fragment_bin_tol = 1.0005", settings.get("comet.preset.02.applied.01"));
        assertEquals("num_threads fragment_bin_tol", settings.get("comet.origin.preset"));
        assertFalse(settings.containsKey("comet.preset.03.id"));
    }

    @Test
    @DisplayName(
            "the workflow-enforced outputs are recorded as the workflow's, whatever they were"
                    + " before")
    void workflowEnforced() {
        CometParameters user =
                configured()
                        .withText("output_pepxmlfile", "1", ValueOrigin.USER)
                        .withText("output_percolatorfile", "1", ValueOrigin.USER);
        assertEquals(
                "database_name decoy_search num_threads output_pepxmlfile output_percolatorfile",
                CometParameterProvenance.settings(user, List.of()).get("comet.origin.user"));
        Map<String, String> enforced =
                CometParameterProvenance.settings(user.withWorkflowEnforcedOutputs(), List.of());
        assertEquals(
                "output_pepxmlfile output_percolatorfile",
                enforced.get("comet.origin.workflow-enforced"));
        assertEquals("database_name decoy_search num_threads", enforced.get("comet.origin.user"));
    }

    @Test
    @DisplayName("every wire word is pinned as a literal, and each reads back as its origin")
    void wireWords() {
        assertEquals("comet-default", CometParameterProvenance.wireWord(ValueOrigin.COMET_DEFAULT));
        assertEquals(
                "cometgui-default",
                CometParameterProvenance.wireWord(ValueOrigin.COMETGUI_DEFAULT));
        assertEquals("preset", CometParameterProvenance.wireWord(ValueOrigin.PRESET));
        assertEquals("user", CometParameterProvenance.wireWord(ValueOrigin.USER));
        assertEquals("imported", CometParameterProvenance.wireWord(ValueOrigin.IMPORTED));
        assertEquals(
                "workflow-enforced",
                CometParameterProvenance.wireWord(ValueOrigin.WORKFLOW_ENFORCED));
        assertEquals("built-in", CometParameterProvenance.wireWord(Preset.Origin.BUILT_IN));
        assertEquals("user", CometParameterProvenance.wireWord(Preset.Origin.USER));
        for (ValueOrigin origin : ValueOrigin.values()) {
            assertEquals(
                    Optional.of(origin),
                    CometParameterProvenance.valueOrigin(
                            CometParameterProvenance.wireWord(origin)));
        }
        assertEquals(Optional.empty(), CometParameterProvenance.valueOrigin("USER"));
        assertEquals(Optional.empty(), CometParameterProvenance.valueOrigin("workflow_enforced"));
    }

    @Test
    @DisplayName("every key this class can make matches the settings-key pattern")
    void everyKeyMatchesThePattern() {
        assertEquals("comet.origin.", CometParameterProvenance.ORIGIN_PREFIX);
        assertEquals("comet.preset.", CometParameterProvenance.PRESET_PREFIX);
        assertEquals("comet.preset.01.", CometParameterProvenance.presetPrefix(1));
        assertEquals("comet.preset.12.", CometParameterProvenance.presetPrefix(12));
        assertEquals("comet.preset.100.", CometParameterProvenance.presetPrefix(100));
        assertThrows(
                IllegalArgumentException.class, () -> CometParameterProvenance.presetPrefix(0));
        List<String> keys = new ArrayList<>();
        for (ValueOrigin origin : ValueOrigin.values()) {
            keys.add(
                    CometParameterProvenance.ORIGIN_PREFIX
                            + CometParameterProvenance.wireWord(origin));
        }
        for (String suffix :
                List.of(
                        CometParameterProvenance.PRESET_ID,
                        CometParameterProvenance.PRESET_NAME,
                        CometParameterProvenance.PRESET_ORIGIN,
                        CometParameterProvenance.PRESET_COMET_VERSION,
                        CometParameterProvenance.PRESET_SCHEMA_VERSION,
                        CometParameterProvenance.PRESET_APPLIED + "01",
                        CometParameterProvenance.PRESET_APPLIED + "99")) {
            keys.add(CometParameterProvenance.presetPrefix(1) + suffix);
            keys.add(CometParameterProvenance.presetPrefix(99) + suffix);
        }
        for (String key : keys) {
            assertTrue(KEY.matcher(key).matches(), key);
        }
        Preset preset = lowLow();
        AppliedPreset applied = PresetDiff.of(configured(), preset).applyAll();
        Map<String, String> made =
                CometParameterProvenance.settings(
                        applied.model()
                                .withText("use_A_ions", "1", ValueOrigin.IMPORTED)
                                .withText("add_Cterm_peptide", "1.0", ValueOrigin.USER),
                        List.of(PresetApplication.of(preset, applied)));
        assertEquals(18, made.size(), made::toString);
        for (String key : made.keySet()) {
            assertTrue(KEY.matcher(key).matches(), key);
        }
    }

    @Test
    @DisplayName(
            "over every bundled release schema: no parameter name holds whitespace or '=', so a"
                    + " name list splits on spaces and an applied value reads back at ' = '")
    void everyNameOfEveryReleaseCanBeAValue() {
        List<CometVersionRecord> versions = METADATA.versions();
        assertEquals(3, versions.size(), "the releases this test covers");
        int checked = 0;
        for (CometVersionRecord version : versions) {
            List<ParameterDefinition> parameters = METADATA.parametersFor(version.version());
            assertTrue(parameters.size() > 100, version.version().text());
            for (ParameterDefinition parameter : parameters) {
                String name = parameter.name();
                assertFalse(name.isEmpty());
                assertFalse(name.chars().anyMatch(Character::isWhitespace), name);
                assertFalse(name.contains("="), name);
                String line = name + CometParameterProvenance.APPLIED_SEPARATOR + "a = b c";
                int at = line.indexOf(CometParameterProvenance.APPLIED_SEPARATOR);
                assertEquals(name, line.substring(0, at));
                assertEquals("a = b c", line.substring(at + 3));
                checked++;
            }
        }
        assertTrue(checked > 300, "names checked: " + checked);
        for (ToolVersion release : ReleaseDefaults.bundledReleases()) {
            CometParameters all = ReleaseDefaults.load(METADATA, release);
            CometParameters everyOneUsers = all;
            for (var entry : all.entries()) {
                everyOneUsers = everyOneUsers.withOrigin(entry.name(), ValueOrigin.USER);
            }
            String list =
                    CometParameterProvenance.settings(everyOneUsers, List.of())
                            .get("comet.origin.user");
            List<String> names = new ArrayList<>();
            all.entries().forEach(entry -> names.add(entry.name()));
            assertEquals(names, List.of(list.split(" ")), release.text());
        }
    }
}
