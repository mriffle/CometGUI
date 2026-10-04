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

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.cometgui.domain.tools.ToolVersion;

/**
 * The curated half of the schema, as loaded from the metadata file: what Comet's own output cannot
 * say about each parameter. {@link MetadataLoader} is the only way to obtain one that has been
 * checked; the constructor checks presence only.
 *
 * @param schemaVersion the metadata file format's version
 * @param versions the Comet versions the metadata was curated against
 * @param parameters every modelled parameter, in the order of the newest version's {@code -q}
 *     output
 * @param internal the allow-list: declared parameters deliberately not modelled
 * @param enzymeTable what the metadata says about {@code [COMET_ENZYME_INFO]}
 */
public record CuratedMetadata(
        int schemaVersion,
        List<CometVersionRecord> versions,
        List<ParameterDefinition> parameters,
        List<InternalParameter> internal,
        EnzymeTableMetadata enzymeTable) {

    /** Takes immutable copies. */
    public CuratedMetadata {
        versions = List.copyOf(versions);
        parameters = List.copyOf(parameters);
        internal = List.copyOf(internal);
        Objects.requireNonNull(enzymeTable, "enzymeTable");
    }

    /**
     * The curated versions, immutable.
     *
     * @return the version records
     */
    @Override
    public List<CometVersionRecord> versions() {
        return List.copyOf(versions);
    }

    /**
     * Every modelled parameter, immutable.
     *
     * @return the definitions
     */
    @Override
    public List<ParameterDefinition> parameters() {
        return List.copyOf(parameters);
    }

    /**
     * The allow-list, immutable.
     *
     * @return the internal parameters
     */
    @Override
    public List<InternalParameter> internal() {
        return List.copyOf(internal);
    }

    /**
     * The definition of one parameter, whatever versions it claims, as curated -- not as any
     * version's override of it ({@link CometVersionRecord#overrides()}) has it. For the definition
     * as one version has it, use {@link #parameter(String, ToolVersion)}.
     *
     * @param name the parameter name
     * @return the definition, or empty if the parameter is not modelled
     */
    public Optional<ParameterDefinition> parameter(String name) {
        Objects.requireNonNull(name, "name");
        return parameters.stream().filter(p -> p.name().equals(name)).findFirst();
    }

    /**
     * The definition of one parameter as one version has it: present only if its range claims the
     * version, and with that version's override applied where its record has one -- its own
     * default, choices, inline comment, help and help reference.
     *
     * @param name the parameter name
     * @param version the Comet version
     * @return the definition, or empty if the parameter is not modelled for that version
     */
    public Optional<ParameterDefinition> parameter(String name, ToolVersion version) {
        Objects.requireNonNull(version, "version");
        return parameter(name)
                .filter(p -> p.supportedVersions().contains(version))
                .map(p -> forVersion(p, version));
    }

    /**
     * The definitions that claim a version, in metadata order, each as that version has it: the
     * curated definition, with the version record's override applied where the version says
     * something else about the parameter ({@link ParameterOverride}).
     *
     * @param version the Comet version
     * @return the definitions whose version range contains it
     */
    public List<ParameterDefinition> parametersFor(ToolVersion version) {
        Objects.requireNonNull(version, "version");
        return parameters.stream()
                .filter(p -> p.supportedVersions().contains(version))
                .map(p -> forVersion(p, version))
                .toList();
    }

    private ParameterDefinition forVersion(ParameterDefinition definition, ToolVersion version) {
        return version(version)
                .flatMap(record -> record.override(definition.name()))
                .map(override -> override.applyTo(definition))
                .orElse(definition);
    }

    /**
     * Whether a parameter is on the internal allow-list.
     *
     * @param name the parameter name
     * @return {@code true} if it is allow-listed
     */
    public boolean isAllowListed(String name) {
        Objects.requireNonNull(name, "name");
        return internal.stream().anyMatch(entry -> entry.name().equals(name));
    }

    /**
     * The record of one curated version.
     *
     * @param version the Comet version
     * @return its record, or empty if the metadata was never curated against it
     */
    public Optional<CometVersionRecord> version(ToolVersion version) {
        Objects.requireNonNull(version, "version");
        return versions.stream().filter(v -> v.version().equals(version)).findFirst();
    }
}
