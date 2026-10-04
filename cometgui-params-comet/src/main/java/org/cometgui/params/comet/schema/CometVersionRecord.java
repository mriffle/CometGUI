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

package org.cometgui.params.comet.schema;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import org.cometgui.domain.tools.ToolVersion;

/**
 * The metadata's record of one Comet version it was curated against.
 *
 * <p>The variable-modification tuple's field layout is version-dependent ({@code R-PARAM-09}), so
 * it is recorded here, per version, as data the codec reads.
 *
 * <p>A parameter's curated definition holds for every curated version its range claims, except
 * where a version says something else about it: that version's record then carries a {@link
 * ParameterOverride} for the parameter in {@link #overrides()} -- another default (Comet 2024.01.0
 * writes {@code fragindex_num_spectrumpeaks = 100} where 2026.02.2 writes {@code 150}), other
 * choices (Comet 2026.03.0 adds {@code index_search_type = -1}), another inline comment, help or
 * help reference. {@link CuratedMetadata#parametersFor(ToolVersion)} and {@link
 * CuratedMetadata#parameter(String, ToolVersion)} apply the overrides, so a model of a version is
 * built from, written with and validated against that version's own definitions.
 *
 * @param version the version as the manifest spells it, such as {@code 2026.02.2}
 * @param marker how that release's binary spells itself on its {@code # comet_version} line
 * @param parameterPages the upstream parameter documentation for the release
 * @param source the upstream source tree at the release's tag
 * @param variableModTuple the field layout of the release's variable-modification tuple
 * @param overrides parameter name to what this version says differently about it; empty for a
 *     version that agrees with every curated definition
 */
public record CometVersionRecord(
        ToolVersion version,
        CometVersionMarker marker,
        String parameterPages,
        String source,
        VariableModLayout variableModTuple,
        Map<String, ParameterOverride> overrides) {

    /**
     * Validates the components and takes an immutable, name-ordered copy of the overrides.
     *
     * @throws IllegalArgumentException if an override is filed under another parameter's name
     */
    public CometVersionRecord {
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(marker, "marker");
        Objects.requireNonNull(parameterPages, "parameterPages");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(variableModTuple, "variableModTuple");
        overrides = Collections.unmodifiableSortedMap(new TreeMap<>(overrides));
        overrides.forEach(
                (name, override) -> {
                    if (!override.name().equals(name)) {
                        throw new IllegalArgumentException(
                                "the override filed under " + name + " is for " + override.name());
                    }
                });
    }

    /**
     * A record with no overrides.
     *
     * @param version the version as the manifest spells it
     * @param marker how that release's binary spells itself
     * @param parameterPages the upstream parameter documentation for the release
     * @param source the upstream source tree at the release's tag
     * @param variableModTuple the field layout of the release's variable-modification tuple
     */
    public CometVersionRecord(
            ToolVersion version,
            CometVersionMarker marker,
            String parameterPages,
            String source,
            VariableModLayout variableModTuple) {
        this(version, marker, parameterPages, source, variableModTuple, Map.of());
    }

    /**
     * This version's override for one parameter.
     *
     * @param name the parameter name
     * @return the override, or empty if the curated definition holds for this version
     */
    public Optional<ParameterOverride> override(String name) {
        Objects.requireNonNull(name, "name");
        return Optional.ofNullable(overrides.get(name));
    }

    /**
     * The default overrides alone, immutable and ordered by name.
     *
     * @return parameter name to this version's default, for the overrides that replace one
     */
    public Map<String, String> defaults() {
        TreeMap<String, String> defaults = new TreeMap<>();
        overrides.forEach(
                (name, override) ->
                        override.defaultValue().ifPresent(value -> defaults.put(name, value)));
        return Collections.unmodifiableSortedMap(defaults);
    }

    /**
     * This version's own default for a parameter, where it overrides the curated one.
     *
     * @param name the parameter name
     * @return the overriding default, or empty if the parameter's curated default holds
     */
    public Optional<String> defaultOverride(String name) {
        return override(name).flatMap(ParameterOverride::defaultValue);
    }
}
