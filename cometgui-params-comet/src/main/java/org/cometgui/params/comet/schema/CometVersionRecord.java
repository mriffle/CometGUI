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
 * <p>A parameter's curated {@code default} holds for every curated version its range claims, except
 * where a version's own {@code -q} output writes another value: that version's record then
 * overrides it in {@link #defaults()} (Comet 2024.01.0 writes {@code fragindex_num_spectrumpeaks =
 * 100} where 2026.02.2 writes {@code 150}). {@link CuratedMetadata#parametersFor(ToolVersion)}
 * applies the overrides, so a model of a version is built from that version's defaults.
 *
 * @param version the version as the manifest spells it, such as {@code 2026.02.2}
 * @param marker how that release's binary spells itself on its {@code # comet_version} line
 * @param parameterPages the upstream parameter documentation for the release
 * @param source the upstream source tree at the release's tag
 * @param variableModTuple the field layout of the release's variable-modification tuple
 * @param defaults parameter name to the default this version writes where it differs from the
 *     parameter's curated default; empty for most versions
 */
public record CometVersionRecord(
        ToolVersion version,
        CometVersionMarker marker,
        String parameterPages,
        String source,
        VariableModLayout variableModTuple,
        Map<String, String> defaults) {

    /** Validates the components and takes an immutable, name-ordered copy of the overrides. */
    public CometVersionRecord {
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(marker, "marker");
        Objects.requireNonNull(parameterPages, "parameterPages");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(variableModTuple, "variableModTuple");
        defaults = Map.copyOf(defaults);
    }

    /**
     * A record with no default overrides.
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
     * The default overrides, immutable and ordered by name.
     *
     * @return parameter name to this version's default
     */
    @Override
    public Map<String, String> defaults() {
        return Collections.unmodifiableSortedMap(new TreeMap<>(defaults));
    }

    /**
     * This version's own default for a parameter, where it overrides the curated one.
     *
     * @param name the parameter name
     * @return the overriding default, or empty if the parameter's curated default holds
     */
    public Optional<String> defaultOverride(String name) {
        Objects.requireNonNull(name, "name");
        return Optional.ofNullable(defaults.get(name));
    }
}
