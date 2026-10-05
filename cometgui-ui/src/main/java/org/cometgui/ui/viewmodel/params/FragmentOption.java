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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.cometgui.params.comet.presets.Preset;
import org.cometgui.params.comet.presets.PresetDelta;

/**
 * One instrument setting of the fragment-ion parameters, in the built-in presets' words: the
 * fragment-scoring values one or more of Comet's example parameter files share, with those files'
 * names, so that the scientist picks "low-res fragments" rather than recalling 1.0005 and 0.4.
 *
 * <p>Choosing one applies those rows of its {@link #source()} preset through the model's preset
 * diff, so the values carry origin {@code PRESET}: they are the preset's values, not ones the
 * scientist typed.
 *
 * @param presetNames the display names of the built-in presets that use these values
 * @param values the values by parameter, as the presets write them, in schema order
 * @param source the first of those presets, whose rows choosing this option applies
 */
public record FragmentOption(List<String> presetNames, Map<String, String> values, Preset source) {

    /**
     * Validates presence, takes immutable copies, and holds every value to the source preset's.
     *
     * @throws IllegalArgumentException if no preset or value is named, or a value is not the one
     *     the source preset sets
     */
    public FragmentOption {
        presetNames = List.copyOf(presetNames);
        values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
        Objects.requireNonNull(source, "source");
        if (presetNames.isEmpty() || values.isEmpty()) {
            throw new IllegalArgumentException(
                    "a fragment setting names at least one preset and one value");
        }
        for (Map.Entry<String, String> value : values.entrySet()) {
            Optional<String> set = source.delta(value.getKey()).map(PresetDelta::value);
            if (!set.equals(Optional.of(value.getValue()))) {
                throw new IllegalArgumentException(
                        value.getKey()
                                + " = "
                                + value.getValue()
                                + " is not what preset "
                                + source.id()
                                + " sets, so choosing it could not apply that preset");
            }
        }
    }

    /**
     * What the choice is called.
     *
     * @return for example {@code Low-res precursor, low-res fragments / High-res precursor, low-res
     *     fragments}
     */
    public String words() {
        return String.join(" / ", presetNames);
    }

    /**
     * The values in words, for the choice's help.
     *
     * @return for example {@code fragment_bin_tol = 1.0005, fragment_bin_offset = 0.4}
     */
    public String valuesText() {
        return String.join(
                ", ",
                values.entrySet().stream().map(e -> e.getKey() + " = " + e.getValue()).toList());
    }

    /**
     * The values, immutable.
     *
     * @return by parameter
     */
    @Override
    public Map<String, String> values() {
        return values;
    }
}
