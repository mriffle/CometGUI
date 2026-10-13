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

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.model.ParameterEntry;
import org.cometgui.params.comet.model.ValueOrigin;
import org.cometgui.params.comet.presets.Preset;
import org.cometgui.params.comet.presets.PresetApplication;
import org.cometgui.provenance.manifest.ProvenanceSchema;

/**
 * The provenance settings that say where a search's Comet parameter values came from (design
 * decision P13-4, as amended): every key in one place, beside {@link CometWorkflow}'s other {@code
 * comet.*} keys and {@link PercolatorProvenance}, each of the shape {@link
 * ProvenanceSchema#SETTINGS_KEY_PATTERN}.
 *
 * <p><strong>Parameter names are in the values, never in the keys.</strong> 39 of Comet's parameter
 * names have upper-case letters ({@code add_C_cysteine}, {@code use_A_ions}, {@code equal_I_and_L}
 * ...), which no settings key may hold, so a key is never made from a name and a name is recorded
 * exactly as Comet spells it.
 *
 * <ul>
 *   <li>{@code comet.origin.<word>} -- for each {@link ValueOrigin} but {@link
 *       ValueOrigin#COMET_DEFAULT} that at least one modelled parameter has, the names of those
 *       parameters, in the release's schema order, separated by single spaces. The word is {@link
 *       #wireWord(ValueOrigin)}. A parameter at Comet's own default is in no list: its value is the
 *       release's {@code comet -q} value. The enzyme table and unknown parameters carry no origin
 *       in the model, so they have none here.
 *   <li>{@code comet.preset.<nn>.id}, {@code .name}, {@code .origin}, {@code .comet-version} and
 *       {@code .schema-version} -- each preset application still describing the configuration
 *       ({@link PresetApplication#describes}), in the order they were applied, {@code nn} from
 *       {@code 01}: the preset's identity.
 *   <li>{@code comet.preset.<nn>.applied.<mm>} -- every value that application set, {@code mm} from
 *       {@code 01} in schema order, as one {@code comet.params} line: {@code <name> = <value
 *       text>}. No parameter name holds whitespace or {@code =}, so the name is everything before
 *       the first {@code " = "}.
 * </ul>
 *
 * <p><strong>Two consequences, both deliberate.</strong> A release change migrates the
 * configuration and the migration carries each value's origin with it, while the applied presets
 * are forgotten (they were made for the other release's configuration): a carried {@code PRESET}
 * value is therefore recorded under {@code comet.origin.preset} with no preset named. And a
 * <em>retry</em> of a run -- Run again with inputs that digest exactly as the run recorded them --
 * is another attempt of the same prepared run, so it records the settings fixed when that run was
 * first prepared, even if an origin changed since without changing a value. None of these settings
 * is an input of any step: they change no fingerprint and no rerun preview.
 *
 * <p>Every value is fixed when the run is prepared, from the configuration the run's parameter file
 * is written from, so they are in the document however the attempt ends.
 */
public final class CometParameterProvenance {

    /** The prefix of each origin's list of parameters: {@code comet.origin.<word>}. */
    public static final String ORIGIN_PREFIX = "comet.origin.";

    /** The prefix of each preset application: {@code comet.preset.<nn>.}. */
    public static final String PRESET_PREFIX = "comet.preset.";

    /** After an application's prefix: the preset's id. */
    public static final String PRESET_ID = "id";

    /** After an application's prefix: the preset's display name. */
    public static final String PRESET_NAME = "name";

    /** After an application's prefix: {@code built-in} or {@code user}. */
    public static final String PRESET_ORIGIN = "origin";

    /** After an application's prefix: the Comet version the preset was made against. */
    public static final String PRESET_COMET_VERSION = "comet-version";

    /** After an application's prefix: the metadata schema version the preset was made against. */
    public static final String PRESET_SCHEMA_VERSION = "schema-version";

    /** After an application's prefix: {@code applied.<mm>}, one value the preset set. */
    public static final String PRESET_APPLIED = "applied.";

    /** Between a parameter's name and its value text in an applied value: as in comet.params. */
    public static final String APPLIED_SEPARATOR = " = ";

    private CometParameterProvenance() {}

    /**
     * The settings a search records about its parameters' origins and presets.
     *
     * @param model the configuration the run's parameter file is written from
     * @param presets every preset applied since the configuration started afresh, in order; those
     *     no longer describing {@code model} are left out
     * @return key to value, sorted
     */
    static Map<String, String> settings(CometParameters model, List<PresetApplication> presets) {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(presets, "presets");
        Map<String, String> settings = new TreeMap<>();
        Map<ValueOrigin, List<String>> byOrigin = new EnumMap<>(ValueOrigin.class);
        for (ParameterEntry entry : model.entries()) {
            if (entry.origin() != ValueOrigin.COMET_DEFAULT) {
                byOrigin.computeIfAbsent(entry.origin(), origin -> new ArrayList<>())
                        .add(entry.name());
            }
        }
        for (Map.Entry<ValueOrigin, List<String>> origin : byOrigin.entrySet()) {
            settings.put(
                    ORIGIN_PREFIX + wireWord(origin.getKey()), String.join(" ", origin.getValue()));
        }
        int number = 0;
        for (PresetApplication application : presets) {
            if (!application.describes(model)) {
                continue;
            }
            number++;
            String prefix = presetPrefix(number);
            settings.put(prefix + PRESET_ID, application.id());
            settings.put(prefix + PRESET_NAME, application.displayName());
            settings.put(prefix + PRESET_ORIGIN, wireWord(application.origin()));
            settings.put(prefix + PRESET_COMET_VERSION, application.cometVersion().text());
            settings.put(
                    prefix + PRESET_SCHEMA_VERSION, Integer.toString(application.schemaVersion()));
            int value = 0;
            for (Map.Entry<String, String> applied : application.values().entrySet()) {
                value++;
                settings.put(
                        prefix + PRESET_APPLIED + twoDigits(value),
                        applied.getKey() + APPLIED_SEPARATOR + applied.getValue());
            }
        }
        return settings;
    }

    /**
     * The key prefix of the {@code number}th recorded preset application.
     *
     * @param number from 1
     * @return {@code comet.preset.<nn>.}, {@code nn} two digits at least
     * @throws IllegalArgumentException if {@code number} is less than 1
     */
    public static String presetPrefix(int number) {
        if (number < 1) {
            throw new IllegalArgumentException(
                    "preset applications are numbered from 1, not " + number);
        }
        return PRESET_PREFIX + twoDigits(number) + ".";
    }

    private static String twoDigits(int number) {
        return String.format(Locale.ROOT, "%02d", number);
    }

    /**
     * A value origin's wire word. A wire word is a token in every document ever written, so each is
     * spelled here and never derived from the constant's name.
     *
     * @param origin the origin
     * @return the word
     */
    public static String wireWord(ValueOrigin origin) {
        Objects.requireNonNull(origin, "origin");
        return switch (origin) {
            case COMET_DEFAULT -> "comet-default";
            case COMETGUI_DEFAULT -> "cometgui-default";
            case PRESET -> "preset";
            case USER -> "user";
            case IMPORTED -> "imported";
            case WORKFLOW_ENFORCED -> "workflow-enforced";
        };
    }

    /**
     * The value origin a wire word names: the inverse of {@link #wireWord(ValueOrigin)}.
     *
     * @param word a wire word
     * @return the origin, or empty if the word is not one
     */
    public static Optional<ValueOrigin> valueOrigin(String word) {
        Objects.requireNonNull(word, "word");
        for (ValueOrigin origin : ValueOrigin.values()) {
            if (wireWord(origin).equals(word)) {
                return Optional.of(origin);
            }
        }
        return Optional.empty();
    }

    /**
     * A preset origin's wire word, spelled here as {@link #wireWord(ValueOrigin)}'s are.
     *
     * @param origin the preset's origin
     * @return {@code built-in} or {@code user}
     */
    public static String wireWord(Preset.Origin origin) {
        Objects.requireNonNull(origin, "origin");
        return switch (origin) {
            case BUILT_IN -> "built-in";
            case USER -> "user";
        };
    }
}
