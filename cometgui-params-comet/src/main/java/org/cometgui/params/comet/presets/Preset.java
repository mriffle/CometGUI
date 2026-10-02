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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.model.ParameterEntry;

/**
 * A named, versioned configuration <em>delta</em> -- not a replacement file (<em>Presets</em> in
 * the specification). It records the Comet version and the metadata schema it was made against and
 * lists only the parameters it sets, each as value text in the parameter's own syntax.
 *
 * <p>A preset changes nothing by itself. {@link PresetDiff#of(CometParameters, Preset)} shows what
 * it would change and applies all or selected rows to a new model; applying it to another Comet
 * version first runs the compatibility check ({@link CompatibilityReport}).
 *
 * @param id a stable identifier: lower-case letters, digits and hyphens, starting with a letter or
 *     digit
 * @param displayName the name shown to the user
 * @param description what the preset is for, in a sentence or two
 * @param origin built into CometGUI, or made by a user
 * @param cometVersion the Comet version the preset was made against
 * @param schemaVersion the curated metadata's {@code schemaVersion} it was made against
 * @param source for a built-in preset, the upstream document its values come from
 * @param deltas the parameters it sets, at least one, each named once
 */
public record Preset(
        String id,
        String displayName,
        String description,
        Origin origin,
        ToolVersion cometVersion,
        int schemaVersion,
        Optional<String> source,
        List<PresetDelta> deltas) {

    /** What a preset id may be: lower-case letters, digits and hyphens, not starting with one. */
    static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9-]*");

    /** Where a preset came from. */
    public enum Origin {

        /** Shipped with CometGUI; every value is cited. */
        BUILT_IN,

        /** Made by a user from a parameter set. */
        USER
    }

    /**
     * Validates the components and takes an immutable copy of the deltas.
     *
     * @throws IllegalArgumentException if the id is not an identifier, a name or description is
     *     blank, there are no deltas or a parameter is set twice, or a built-in preset lacks its
     *     source or a citation
     */
    public Preset {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(cometVersion, "cometVersion");
        Objects.requireNonNull(source, "source");
        deltas = List.copyOf(deltas);
        if (!ID.matcher(id).matches()) {
            throw new IllegalArgumentException(
                    "\"" + id + "\" is not a preset id: lower-case letters, digits and hyphens");
        }
        if (displayName.isBlank() || description.isBlank()) {
            throw new IllegalArgumentException(
                    "preset " + id + " needs a display name and a description");
        }
        if (deltas.isEmpty()) {
            throw new IllegalArgumentException(
                    "preset " + id + " sets no parameter; a preset is a list of changes");
        }
        Set<String> names = new HashSet<>();
        for (PresetDelta delta : deltas) {
            if (!names.add(delta.parameter())) {
                throw new IllegalArgumentException(
                        "preset " + id + " sets " + delta.parameter() + " twice");
            }
            if (origin == Origin.BUILT_IN && delta.citation().isEmpty()) {
                throw new IllegalArgumentException(
                        "built-in preset "
                                + id
                                + " does not cite where its "
                                + delta.parameter()
                                + " value comes from");
            }
        }
        if (origin == Origin.BUILT_IN && source.isEmpty()) {
            throw new IllegalArgumentException(
                    "built-in preset " + id + " does not name the document it comes from");
        }
    }

    /**
     * The deltas, immutable.
     *
     * @return the parameters the preset sets, in its order
     */
    @Override
    public List<PresetDelta> deltas() {
        return List.copyOf(deltas);
    }

    /**
     * The delta for one parameter.
     *
     * @param parameter the parameter name
     * @return the delta, or empty if the preset does not set the parameter
     */
    public Optional<PresetDelta> delta(String parameter) {
        Objects.requireNonNull(parameter, "parameter");
        return deltas.stream().filter(d -> d.parameter().equals(parameter)).findFirst();
    }

    /**
     * A user's preset made from a parameter set: the named parameters' current values, recording
     * the set's Comet version and metadata schema.
     *
     * @param id the new preset's id
     * @param displayName its name
     * @param description what it is for
     * @param model the parameter set to take the values from
     * @param parameters the parameters to include; each must be modelled for the set's version
     * @return the preset, its deltas in the set's schema order
     * @throws IllegalArgumentException if a parameter is not modelled for the set's version, or
     *     none is named
     */
    public static Preset fromModel(
            String id,
            String displayName,
            String description,
            CometParameters model,
            Collection<String> parameters) {
        Objects.requireNonNull(model, "model");
        Set<String> wanted = new HashSet<>(parameters);
        for (String name : wanted) {
            if (model.entry(name).isEmpty()) {
                throw new IllegalArgumentException(
                        name
                                + " is not a parameter of Comet "
                                + model.version().text()
                                + ", so a preset cannot record it");
            }
        }
        List<PresetDelta> deltas = new ArrayList<>();
        for (ParameterEntry entry : model.entries()) {
            if (wanted.contains(entry.name())) {
                deltas.add(
                        new PresetDelta(entry.name(), model.text(entry.name()), Optional.empty()));
            }
        }
        return new Preset(
                id,
                displayName,
                description,
                Origin.USER,
                model.version(),
                model.metadata().schemaVersion(),
                Optional.empty(),
                deltas);
    }
}
