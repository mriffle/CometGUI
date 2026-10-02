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

package org.cometgui.params.comet.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.schema.CuratedMetadata;
import org.cometgui.params.comet.schema.ParameterDefinition;
import org.cometgui.params.comet.schema.ValueKind;
import org.cometgui.params.comet.value.EnzymeTable;
import org.cometgui.params.comet.value.IonSeriesSelection;
import org.cometgui.params.comet.value.TolerancePair;

/**
 * The typed parameter set of one Comet version: the model the parser builds, the canonical writer
 * writes, the validators read and the editor binds to.
 *
 * <p>It holds exactly one {@link ParameterEntry} for every parameter the curated metadata models
 * for the version, in the metadata's order (which is the order {@code comet -q} writes), each with
 * its typed value and {@link ValueOrigin}; the {@code [COMET_ENZYME_INFO]} table; the unknown
 * parameters an imported file carried ({@code R-PARAM-07}); and the warnings of the parse that
 * produced it. It is immutable: every {@code with...} method returns a new model and leaves this
 * one as it was.
 */
public final class CometParameters {

    private final CuratedMetadata metadata;

    private final ToolVersion version;

    private final ParameterValueCodec codec;

    private final Map<String, ParameterEntry> entries;

    private final EnzymeTable enzymeTable;

    private final List<UnknownParameter> unknownParameters;

    private final List<Diagnostic> diagnostics;

    private CometParameters(
            CuratedMetadata metadata,
            ToolVersion version,
            ParameterValueCodec codec,
            Map<String, ParameterEntry> entries,
            EnzymeTable enzymeTable,
            List<UnknownParameter> unknownParameters,
            List<Diagnostic> diagnostics) {
        this.metadata = metadata;
        this.version = version;
        this.codec = codec;
        this.entries = Collections.unmodifiableMap(new LinkedHashMap<>(entries));
        this.enzymeTable = enzymeTable;
        this.unknownParameters = List.copyOf(unknownParameters);
        this.diagnostics = List.copyOf(diagnostics);
    }

    /**
     * Builds a model, checking that it is complete and consistent.
     *
     * @param metadata the curated metadata
     * @param version the Comet version; the metadata must have been curated against it
     * @param entries one entry for every parameter the metadata models for the version, in any
     *     order; each entry's definition must be the metadata's
     * @param enzymeTable the enzyme table
     * @param unknownParameters parameters the schema does not model for the version, distinct
     * @param diagnostics the warnings of the parse that produced the values; never an error
     * @return the model
     * @throws IllegalArgumentException if a modelled parameter is missing or given twice, an entry
     *     is not the version's, an unknown parameter is modelled or named twice, or a diagnostic is
     *     an error
     */
    public static CometParameters of(
            CuratedMetadata metadata,
            ToolVersion version,
            List<ParameterEntry> entries,
            EnzymeTable enzymeTable,
            List<UnknownParameter> unknownParameters,
            List<Diagnostic> diagnostics) {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(enzymeTable, "enzymeTable");
        ParameterValueCodec codec = ParameterValueCodec.forVersion(metadata, version);
        Map<String, ParameterEntry> given = new LinkedHashMap<>();
        for (ParameterEntry entry : entries) {
            if (given.put(entry.name(), entry) != null) {
                throw new IllegalArgumentException(entry.name() + " is given twice");
            }
        }
        Map<String, ParameterEntry> ordered = new LinkedHashMap<>();
        for (ParameterDefinition definition : metadata.parametersFor(version)) {
            ParameterEntry entry = given.remove(definition.name());
            if (entry == null) {
                throw new IllegalArgumentException(
                        definition.name()
                                + " is modelled for Comet "
                                + version.text()
                                + " and has no value; every modelled parameter needs one");
            }
            if (!entry.definition().equals(definition)) {
                throw new IllegalArgumentException(
                        "the entry for "
                                + definition.name()
                                + " carries a definition that is not the metadata's");
            }
            ordered.put(definition.name(), entry);
        }
        if (!given.isEmpty()) {
            throw new IllegalArgumentException(
                    given.keySet()
                            + " are not modelled for Comet "
                            + version.text()
                            + "; such parameters are unknown parameters, not entries");
        }
        Set<String> unknownNames = new HashSet<>();
        for (UnknownParameter unknown : unknownParameters) {
            if (ordered.containsKey(unknown.name())) {
                throw new IllegalArgumentException(
                        unknown.name() + " is modelled, so it cannot also be unknown");
            }
            if (!unknownNames.add(unknown.name())) {
                throw new IllegalArgumentException(
                        "the unknown parameter " + unknown.name() + " is given twice");
            }
        }
        for (Diagnostic diagnostic : diagnostics) {
            if (diagnostic.isError()) {
                throw new IllegalArgumentException(
                        "a model cannot carry an error; a parse with errors produces no model: "
                                + diagnostic.message());
            }
        }
        return new CometParameters(
                metadata, version, codec, ordered, enzymeTable, unknownParameters, diagnostics);
    }

    /**
     * The schema defaults of a version: every modelled parameter at its curated default -- the
     * value {@code comet -q} writes -- with origin {@link ValueOrigin#COMET_DEFAULT}.
     *
     * @param metadata the curated metadata
     * @param version the Comet version
     * @param enzymeTable the enzyme table to start from; the metadata does not curate the rows
     * @return the model, with no unknown parameters and no diagnostics
     * @throws IllegalArgumentException if the metadata was not curated against the version
     */
    public static CometParameters defaults(
            CuratedMetadata metadata, ToolVersion version, EnzymeTable enzymeTable) {
        ParameterValueCodec codec = ParameterValueCodec.forVersion(metadata, version);
        List<ParameterEntry> entries = new ArrayList<>();
        for (ParameterDefinition definition : metadata.parametersFor(version)) {
            entries.add(defaultEntry(codec, definition));
        }
        return of(metadata, version, entries, enzymeTable, List.of(), List.of());
    }

    private static ParameterEntry defaultEntry(
            ParameterValueCodec codec, ParameterDefinition definition) {
        return new ParameterEntry(
                definition,
                codec.parse(definition, definition.defaultValue()),
                ValueOrigin.COMET_DEFAULT);
    }

    /**
     * The curated metadata the model was built from.
     *
     * @return the metadata
     */
    public CuratedMetadata metadata() {
        return metadata;
    }

    /**
     * The Comet version the model is for.
     *
     * @return the version
     */
    public ToolVersion version() {
        return version;
    }

    /**
     * The reader and writer of this version's value texts.
     *
     * @return the codec
     */
    public ParameterValueCodec codec() {
        return codec;
    }

    /**
     * Every modelled parameter, in the metadata's order.
     *
     * @return the entries, immutable
     */
    public List<ParameterEntry> entries() {
        return List.copyOf(entries.values());
    }

    /**
     * One parameter, by name.
     *
     * @param name the parameter name
     * @return its entry, or empty if the parameter is not modelled for this version
     */
    public Optional<ParameterEntry> entry(String name) {
        Objects.requireNonNull(name, "name");
        return Optional.ofNullable(entries.get(name));
    }

    private ParameterEntry require(String name) {
        return entry(name)
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        name
                                                + " is not a parameter modelled for Comet "
                                                + version.text()));
    }

    /**
     * A parameter's typed value.
     *
     * @param name the parameter name
     * @return the value
     * @throws IllegalArgumentException if the parameter is not modelled for this version
     */
    public ParameterValue value(String name) {
        return require(name).value();
    }

    /**
     * Where a parameter's value came from.
     *
     * @param name the parameter name
     * @return the origin
     * @throws IllegalArgumentException if the parameter is not modelled for this version
     */
    public ValueOrigin origin(String name) {
        return require(name).origin();
    }

    /**
     * A parameter's curated definition.
     *
     * @param name the parameter name
     * @return the definition
     * @throws IllegalArgumentException if the parameter is not modelled for this version
     */
    public ParameterDefinition definition(String name) {
        return require(name).definition();
    }

    /**
     * A parameter's value as the text written after {@code name = }.
     *
     * @param name the parameter name
     * @return the value text
     * @throws IllegalArgumentException if the parameter is not modelled for this version
     */
    public String text(String name) {
        ParameterEntry entry = require(name);
        return codec.format(entry.definition(), entry.value());
    }

    /**
     * The enzyme table.
     *
     * @return the table
     */
    public EnzymeTable enzymeTable() {
        return enzymeTable;
    }

    /**
     * The parameters an imported file declared that the schema does not model for this version, in
     * file order.
     *
     * @return the unknown parameters, immutable
     */
    public List<UnknownParameter> unknownParameters() {
        return List.copyOf(unknownParameters);
    }

    /**
     * The warnings of the parse that produced this model; empty for a model built from defaults.
     *
     * @return the diagnostics, immutable
     */
    public List<Diagnostic> diagnostics() {
        return List.copyOf(diagnostics);
    }

    /**
     * The signed precursor tolerance pair, as one value.
     *
     * @return the pair
     * @throws IllegalArgumentException if the version does not model both members
     */
    public TolerancePair tolerancePair() {
        return TolerancePair.parse(text(TolerancePair.LOWER), text(TolerancePair.UPPER));
    }

    /**
     * The ion-series family, as one value.
     *
     * @return the selection
     * @throws IllegalArgumentException if the version does not model the whole family
     */
    public IonSeriesSelection ionSeries() {
        Map<String, String> texts = new LinkedHashMap<>();
        for (ParameterEntry entry : entries.values()) {
            ValueKind kind = entry.definition().kind();
            if (kind == ValueKind.ION_SERIES_FLAG
                    || entry.name().equals(IonSeriesSelection.NEUTRAL_LOSS_PARAMETER)) {
                texts.put(entry.name(), codec.format(entry.definition(), entry.value()));
            }
        }
        return IonSeriesSelection.parse(texts);
    }

    /**
     * This model with one parameter's value and origin changed.
     *
     * @param name the parameter name
     * @param value the new value, of the variant the parameter's kind holds
     * @param origin where it came from
     * @return a new model
     * @throws IllegalArgumentException if the parameter is not modelled for this version or the
     *     value is of the wrong variant
     */
    public CometParameters withValue(String name, ParameterValue value, ValueOrigin origin) {
        ParameterEntry entry = require(name);
        ParameterEntry changed = new ParameterEntry(entry.definition(), value, origin);
        Map<String, ParameterEntry> copy = new LinkedHashMap<>(entries);
        copy.put(name, changed);
        return new CometParameters(
                metadata, version, codec, copy, enzymeTable, unknownParameters, diagnostics);
    }

    /**
     * This model with one parameter set from value text, read as its kind.
     *
     * @param name the parameter name
     * @param text the value text, as it would appear after {@code name = }
     * @param origin where it came from
     * @return a new model
     * @throws IllegalArgumentException if the parameter is not modelled for this version
     * @throws org.cometgui.params.comet.value.ValueSyntaxException if the text cannot be read as
     *     the parameter's kind
     */
    public CometParameters withText(String name, String text, ValueOrigin origin) {
        return withValue(name, codec.parse(require(name).definition(), text), origin);
    }

    /**
     * This model with one parameter's origin changed and its value kept -- for example to mark a
     * value the workflow requires as {@link ValueOrigin#WORKFLOW_ENFORCED}.
     *
     * @param name the parameter name
     * @param origin the new origin
     * @return a new model
     * @throws IllegalArgumentException if the parameter is not modelled for this version
     */
    public CometParameters withOrigin(String name, ValueOrigin origin) {
        return withValue(name, require(name).value(), origin);
    }

    /**
     * This model with one parameter back at its schema default, origin {@link
     * ValueOrigin#COMET_DEFAULT}.
     *
     * @param name the parameter name
     * @return a new model
     * @throws IllegalArgumentException if the parameter is not modelled for this version
     */
    public CometParameters resetToDefault(String name) {
        ParameterDefinition definition = require(name).definition();
        ParameterEntry reset = defaultEntry(codec, definition);
        return withValue(name, reset.value(), reset.origin());
    }

    /**
     * This model with another enzyme table, such as one with a custom enzyme added.
     *
     * @param table the new table
     * @return a new model
     */
    public CometParameters withEnzymeTable(EnzymeTable table) {
        Objects.requireNonNull(table, "table");
        return new CometParameters(
                metadata, version, codec, entries, table, unknownParameters, diagnostics);
    }

    /**
     * This model without one unknown parameter: the explicit removal {@code R-PARAM-07} requires
     * before an imported unknown parameter is not written back.
     *
     * @param name the unknown parameter's name
     * @return a new model
     * @throws IllegalArgumentException if the model holds no unknown parameter of that name
     */
    public CometParameters withoutUnknown(String name) {
        Objects.requireNonNull(name, "name");
        List<UnknownParameter> kept =
                unknownParameters.stream().filter(u -> !u.name().equals(name)).toList();
        if (kept.size() == unknownParameters.size()) {
            throw new IllegalArgumentException("there is no unknown parameter named " + name);
        }
        return new CometParameters(
                metadata, version, codec, entries, enzymeTable, kept, diagnostics);
    }

    /**
     * Equal if the version, every entry, the enzyme table, the unknown parameters and the
     * diagnostics are.
     *
     * @param other the other object
     * @return {@code true} if equal
     */
    @Override
    public boolean equals(Object other) {
        if (!(other instanceof CometParameters that)) {
            return false;
        }
        return version.equals(that.version)
                && entries.equals(that.entries)
                && enzymeTable.equals(that.enzymeTable)
                && unknownParameters.equals(that.unknownParameters)
                && diagnostics.equals(that.diagnostics);
    }

    @Override
    public int hashCode() {
        return Objects.hash(version, entries, enzymeTable, unknownParameters, diagnostics);
    }

    @Override
    public String toString() {
        return "CometParameters[Comet "
                + version.text()
                + ", "
                + entries.size()
                + " parameters, "
                + enzymeTable.rows().size()
                + " enzymes, "
                + unknownParameters.size()
                + " unknown, "
                + diagnostics.size()
                + " diagnostics]";
    }
}
