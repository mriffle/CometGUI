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
import org.cometgui.params.comet.schema.ValidatorId;
import org.cometgui.params.comet.schema.ValueKind;
import org.cometgui.params.comet.value.DecimalRange;
import org.cometgui.params.comet.value.EnzymeTable;
import org.cometgui.params.comet.value.IntegerRange;
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
     * The two values of a two-value range parameter, as a range control's two fields show them.
     *
     * @param name an {@code INTEGER_RANGE} or {@code DECIMAL_RANGE} parameter, such as {@code
     *     peptide_length_range}
     * @return the first and the second value's text, scale kept
     * @throws IllegalArgumentException if the parameter is not modelled for this version or is not
     *     a two-value range
     */
    public List<String> rangeTexts(String name) {
        return switch (value(name)) {
            case ParameterValue.WholeRange range ->
                    List.of(range.range().firstText(), range.range().secondText());
            case ParameterValue.DecimalPair range ->
                    List.of(range.range().firstText(), range.range().secondText());
            default -> throw notARange(name);
        };
    }

    /**
     * A two-value range parameter's value from its two values entered separately, read as the
     * parameter's kind. Whether the two are in order is validation's question ({@code
     * ordered_range}), never this method's.
     *
     * @param name an {@code INTEGER_RANGE} or {@code DECIMAL_RANGE} parameter
     * @param firstText the first value
     * @param secondText the second value
     * @return the value, for {@link #withValue}
     * @throws IllegalArgumentException if the parameter is not modelled for this version or is not
     *     a two-value range
     * @throws org.cometgui.params.comet.value.ValueSyntaxException naming the parameter and the
     *     value at fault, if either is not one number of the kind
     */
    public ParameterValue rangeValue(String name, String firstText, String secondText) {
        ValueKind kind = require(name).definition().kind();
        return switch (kind) {
            case INTEGER_RANGE ->
                    new ParameterValue.WholeRange(IntegerRange.parse(name, firstText, secondText));
            case DECIMAL_RANGE ->
                    new ParameterValue.DecimalPair(DecimalRange.parse(name, firstText, secondText));
            default -> throw notARange(name);
        };
    }

    private static IllegalArgumentException notARange(String name) {
        return new IllegalArgumentException(name + " is not a two-value range");
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
     * This model with one parameter back at its default: CometGUI's starting value for the version
     * where the metadata records one ({@link CuratedMetadata#startingValue}), origin {@link
     * ValueOrigin#COMETGUI_DEFAULT}; otherwise the schema default, what {@code comet -q} writes,
     * origin {@link ValueOrigin#COMET_DEFAULT}. It is the value a new configuration starts with
     * ({@link #withStartingValues()}), so resetting a field and starting again agree.
     *
     * @param name the parameter name
     * @return a new model
     * @throws IllegalArgumentException if the parameter is not modelled for this version
     */
    public CometParameters resetToDefault(String name) {
        ParameterDefinition definition = require(name).definition();
        ParameterEntry reset = startingEntry(metadata, version, codec, definition);
        return withValue(name, reset.value(), reset.origin());
    }

    /**
     * The entry a parameter takes when nothing names a value for it -- a new configuration, a
     * reset, a parameter a parsed file leaves out, or one that is new in a migration's target:
     * CometGUI's starting value for the version where the metadata records one ({@link
     * CuratedMetadata#startingValue}), origin {@link ValueOrigin#COMETGUI_DEFAULT}; otherwise the
     * schema default, what {@code comet -q} writes, origin {@link ValueOrigin#COMET_DEFAULT}.
     *
     * <p>For a parameter a file leaves out this is what Comet itself does: run on both offered
     * releases, a file without the {@code spectral_library_name} line searches exactly as one with
     * it empty, while {@code -q}'s placeholder value stops the search (D-012). A value a file
     * <em>names</em> never comes through here.
     *
     * @param metadata the curated metadata
     * @param version the Comet version
     * @param codec the version's codec
     * @param definition the parameter's definition as the version has it
     * @return the entry
     */
    public static ParameterEntry startingEntry(
            CuratedMetadata metadata,
            ToolVersion version,
            ParameterValueCodec codec,
            ParameterDefinition definition) {
        Optional<String> starting = metadata.startingValue(definition.name(), version);
        if (starting.isPresent()) {
            return new ParameterEntry(
                    definition,
                    codec.parse(definition, starting.get()),
                    ValueOrigin.COMETGUI_DEFAULT);
        }
        return defaultEntry(codec, definition);
    }

    /**
     * This model with every parameter for which the metadata records CometGUI's starting value for
     * the version set to it, origin {@link ValueOrigin#COMETGUI_DEFAULT}; every other parameter as
     * it was. This is how a <em>new</em> configuration departs from Comet's own {@code -q} output
     * ({@code D-012}); a parsed or imported file is never passed through it, so a file keeps
     * whatever it names.
     *
     * @return a new model
     */
    public CometParameters withStartingValues() {
        CometParameters started = this;
        for (ParameterEntry entry : entries.values()) {
            Optional<String> starting = metadata.startingValue(entry.name(), version);
            if (starting.isPresent()) {
                started =
                        started.withText(
                                entry.name(), starting.get(), ValueOrigin.COMETGUI_DEFAULT);
            }
        }
        return started;
    }

    /**
     * The decoy source ({@code R-DEC-01}): {@code decoy_search} as one of the specification's three
     * sources.
     *
     * @return the source, or empty if {@code decoy_search} holds a value Comet does not document
     * @throws IllegalArgumentException if the version does not model {@code decoy_search}
     */
    public Optional<DecoySource> decoySource() {
        ParameterValue value = value(DecoySource.PARAMETER);
        return DecoySource.fromDecoySearch(((ParameterValue.Whole) value).value());
    }

    /**
     * This model with the decoy source set: {@code decoy_search} written as the source's value.
     *
     * @param source the decoy source
     * @param origin where the choice came from
     * @return a new model
     * @throws IllegalArgumentException if the version does not model {@code decoy_search}
     */
    public CometParameters withDecoySource(DecoySource source, ValueOrigin origin) {
        Objects.requireNonNull(source, "source");
        return withValue(
                DecoySource.PARAMETER, new ParameterValue.Whole(source.decoySearch()), origin);
    }

    /**
     * This model with every output the workflow requires switched on, origin {@link
     * ValueOrigin#WORKFLOW_ENFORCED} ({@code R-CMT-01}).
     *
     * <p>The required outputs are the parameters the metadata marks {@link
     * ValidatorId#WORKFLOW_ENFORCED} -- for Comet 2026.02.2 {@code output_pepxmlfile} (read by PDV
     * and the Limelight converter) and {@code output_percolatorfile} (the {@code .pin} file
     * Percolator reads). Each is set to on and marked as the application's, even where it was
     * already on, so that the editor shows it as locked by the workflow rather than as the user's.
     *
     * @return a new model
     * @throws IllegalStateException if the metadata marks a parameter that is not an on/off flag
     */
    public CometParameters withWorkflowEnforcedOutputs() {
        CometParameters enforced = this;
        for (ParameterEntry entry : entries.values()) {
            ParameterDefinition definition = entry.definition();
            if (!definition.validators().contains(ValidatorId.WORKFLOW_ENFORCED)) {
                continue;
            }
            if (definition.kind() != ValueKind.BOOLEAN_FLAG) {
                throw new IllegalStateException(
                        definition.name()
                                + " is marked "
                                + ValidatorId.WORKFLOW_ENFORCED.id()
                                + " but is of kind "
                                + definition.kind()
                                + "; only an on/off flag can be switched on by the workflow");
            }
            enforced =
                    enforced.withValue(
                            definition.name(),
                            new ParameterValue.Flag(true),
                            ValueOrigin.WORKFLOW_ENFORCED);
        }
        return enforced;
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
