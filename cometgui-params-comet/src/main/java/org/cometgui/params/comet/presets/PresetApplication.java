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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.model.ParameterEntry;
import org.cometgui.params.comet.model.ValueOrigin;

/**
 * One application of a preset to a configuration, as a run's provenance records it: which preset --
 * its id, name, origin, and the Comet version and metadata schema it was made against -- and the
 * values it set, each as the configuration's own value text right after it was applied.
 *
 * <p>It is a fact about the configuration's history, not part of the configuration: it is never in
 * {@link CometParameters}, so it takes no part in a model's equality, its parameter file or any
 * fingerprint computed from them. Whether the configuration that runs is still described by it is
 * {@link #describes(CometParameters)}.
 *
 * @param id the preset's id
 * @param displayName the preset's display name
 * @param origin built into CometGUI, or a user's
 * @param cometVersion the Comet version the preset was made against
 * @param schemaVersion the metadata {@code schemaVersion} the preset was made against
 * @param values every parameter the application set, to the value text it set, in schema order; at
 *     least one
 */
public record PresetApplication(
        String id,
        String displayName,
        Preset.Origin origin,
        ToolVersion cometVersion,
        int schemaVersion,
        Map<String, String> values) {

    /**
     * Validates the components and takes an immutable, order-keeping copy of the values.
     *
     * @throws IllegalArgumentException if the application set no value
     */
    public PresetApplication {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(cometVersion, "cometVersion");
        Objects.requireNonNull(values, "values");
        if (values.isEmpty()) {
            throw new IllegalArgumentException(
                    "an application of preset " + id + " set no value, so it is not one");
        }
        values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    /**
     * The application of a preset whose rows were applied: the preset's identity and, for every
     * applied row, the applied model's value text for that parameter.
     *
     * @param preset the preset the diff was taken from
     * @param applied what applying all or selected rows of that diff produced
     * @return the application
     * @throws IllegalArgumentException if no row was applied
     */
    public static PresetApplication of(Preset preset, AppliedPreset applied) {
        Objects.requireNonNull(preset, "preset");
        Objects.requireNonNull(applied, "applied");
        Map<String, String> values = new LinkedHashMap<>();
        for (DiffRow row : applied.applied()) {
            values.put(row.key(), applied.model().text(row.key()));
        }
        return new PresetApplication(
                preset.id(),
                preset.displayName(),
                preset.origin(),
                preset.cometVersion(),
                preset.schemaVersion(),
                values);
    }

    /**
     * Whether a configuration is still described by this application: at least one parameter it set
     * still holds the value it set, with origin {@link ValueOrigin#PRESET}. A value the scientist
     * has since edited (origin {@code USER}), reset, imported over or that a later preset replaced
     * does not count, so an application none of whose values survive is not a fact about the
     * configuration any more, and a run must not claim it.
     *
     * @param model the configuration
     * @return {@code true} if at least one value this application set is still in place
     */
    public boolean describes(CometParameters model) {
        Objects.requireNonNull(model, "model");
        for (Map.Entry<String, String> value : values.entrySet()) {
            Optional<ParameterEntry> entry = model.entry(value.getKey());
            if (entry.isPresent()
                    && entry.get().origin() == ValueOrigin.PRESET
                    && model.text(value.getKey()).equals(value.getValue())) {
                return true;
            }
        }
        return false;
    }

    /**
     * The values, immutable, in schema order.
     *
     * @return parameter name to value text
     */
    @Override
    public Map<String, String> values() {
        return values;
    }
}
