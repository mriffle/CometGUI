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
import java.util.List;
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
 * <p>Validation facts that differ by release are here too: {@link #ruleSeverities()} states, for
 * each version-scoped validation rule, whether this release's binary refuses, warns about or
 * accepts what the rule checks ({@link RuleSeverity}), so a rule reads its severity from the
 * version the model carries rather than asking which version it is.
 *
 * <p>So is what this release does with a value written for another: {@link #valueMigrations()}
 * holds, keyed by the source release, the values schema migration converts, flags or carries with a
 * notice on the way into this release ({@link ValueMigration}) -- Comet 2026.02.2's {@code
 * index_search_type = 1}, which Comet 2026.03.0 spells {@code -1}.
 *
 * <p>And so is which formats of an existing {@code .idx} file the release can search, {@link
 * #indexFormats()}: Comet 2026.03.0 reads format 5 only, Comet 2026.02.2 format 4 only, so the
 * validator refuses an index the selected release would refuse, before Comet starts.
 *
 * @param version the version as the manifest spells it, such as {@code 2026.02.2}
 * @param marker how that release's binary spells itself on its {@code # comet_version} line
 * @param parameterPages the upstream parameter documentation for the release
 * @param source the upstream source tree at the release's tag
 * @param variableModTuple the field layout of the release's variable-modification tuple
 * @param overrides parameter name to what this version says differently about it; empty for a
 *     version that agrees with every curated definition
 * @param ruleSeverities rule identifier to this release's severity for that version-scoped rule
 * @param valueMigrations what migration into this release does with particular values written for
 *     another, in the metadata's order; empty when it carries every value by its typed meaning
 * @param indexFormats the formats of existing index the release can search
 */
public record CometVersionRecord(
        ToolVersion version,
        CometVersionMarker marker,
        String parameterPages,
        String source,
        VariableModLayout variableModTuple,
        Map<String, ParameterOverride> overrides,
        Map<String, RuleSeverity> ruleSeverities,
        List<ValueMigration> valueMigrations,
        IndexFormats indexFormats) {

    /**
     * Validates the components and takes immutable, name-ordered copies of the overrides and the
     * rule severities, and an immutable copy of the value migrations.
     *
     * @throws IllegalArgumentException if an override is filed under another parameter's name, or a
     *     rule severity under another rule's identifier, or a value migration is from this release
     *     itself
     */
    public CometVersionRecord {
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(marker, "marker");
        Objects.requireNonNull(parameterPages, "parameterPages");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(variableModTuple, "variableModTuple");
        Objects.requireNonNull(indexFormats, "indexFormats");
        overrides = Collections.unmodifiableSortedMap(new TreeMap<>(overrides));
        overrides.forEach(
                (name, override) -> {
                    if (!override.name().equals(name)) {
                        throw new IllegalArgumentException(
                                "the override filed under " + name + " is for " + override.name());
                    }
                });
        ruleSeverities = Collections.unmodifiableSortedMap(new TreeMap<>(ruleSeverities));
        ruleSeverities.forEach(
                (rule, severity) -> {
                    if (!severity.rule().equals(rule)) {
                        throw new IllegalArgumentException(
                                "the severity filed under " + rule + " is for " + severity.rule());
                    }
                });
        valueMigrations = List.copyOf(valueMigrations);
        for (ValueMigration migration : valueMigrations) {
            if (migration.from().equals(version)) {
                throw new IllegalArgumentException(
                        "Comet "
                                + version.text()
                                + " has a value migration from itself: "
                                + migration.matches());
            }
        }
    }

    /**
     * A record with overrides, rule severities and value migrations, stating no index format
     * ({@link IndexFormats#unstated()}: it reads no existing index).
     *
     * @param version the version as the manifest spells it
     * @param marker how that release's binary spells itself
     * @param parameterPages the upstream parameter documentation for the release
     * @param source the upstream source tree at the release's tag
     * @param variableModTuple the field layout of the release's variable-modification tuple
     * @param overrides parameter name to what this version says differently about it
     * @param ruleSeverities rule identifier to this release's severity for that rule
     * @param valueMigrations what migration into this release does with values written for another
     */
    public CometVersionRecord(
            ToolVersion version,
            CometVersionMarker marker,
            String parameterPages,
            String source,
            VariableModLayout variableModTuple,
            Map<String, ParameterOverride> overrides,
            Map<String, RuleSeverity> ruleSeverities,
            List<ValueMigration> valueMigrations) {
        this(
                version,
                marker,
                parameterPages,
                source,
                variableModTuple,
                overrides,
                ruleSeverities,
                valueMigrations,
                IndexFormats.unstated());
    }

    /**
     * A record with overrides and rule severities and no value migrations.
     *
     * @param version the version as the manifest spells it
     * @param marker how that release's binary spells itself
     * @param parameterPages the upstream parameter documentation for the release
     * @param source the upstream source tree at the release's tag
     * @param variableModTuple the field layout of the release's variable-modification tuple
     * @param overrides parameter name to what this version says differently about it
     * @param ruleSeverities rule identifier to this release's severity for that rule
     */
    public CometVersionRecord(
            ToolVersion version,
            CometVersionMarker marker,
            String parameterPages,
            String source,
            VariableModLayout variableModTuple,
            Map<String, ParameterOverride> overrides,
            Map<String, RuleSeverity> ruleSeverities) {
        this(
                version,
                marker,
                parameterPages,
                source,
                variableModTuple,
                overrides,
                ruleSeverities,
                List.of());
    }

    /**
     * A record with no overrides and no rule severities.
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
     * A record with overrides and no rule severities.
     *
     * @param version the version as the manifest spells it
     * @param marker how that release's binary spells itself
     * @param parameterPages the upstream parameter documentation for the release
     * @param source the upstream source tree at the release's tag
     * @param variableModTuple the field layout of the release's variable-modification tuple
     * @param overrides parameter name to what this version says differently about it
     */
    public CometVersionRecord(
            ToolVersion version,
            CometVersionMarker marker,
            String parameterPages,
            String source,
            VariableModLayout variableModTuple,
            Map<String, ParameterOverride> overrides) {
        this(version, marker, parameterPages, source, variableModTuple, overrides, Map.of());
    }

    /**
     * This record with other rule severities, everything else kept.
     *
     * @param severities the rule severities
     * @return the record
     */
    public CometVersionRecord withRuleSeverities(Map<String, RuleSeverity> severities) {
        return new CometVersionRecord(
                version,
                marker,
                parameterPages,
                source,
                variableModTuple,
                overrides,
                severities,
                valueMigrations,
                indexFormats);
    }

    /**
     * This record with other value migrations, everything else kept.
     *
     * @param migrations the value migrations
     * @return the record
     */
    public CometVersionRecord withValueMigrations(List<ValueMigration> migrations) {
        return new CometVersionRecord(
                version,
                marker,
                parameterPages,
                source,
                variableModTuple,
                overrides,
                ruleSeverities,
                migrations,
                indexFormats);
    }

    /**
     * This record with other index formats, everything else kept.
     *
     * @param formats the formats of existing index the release can search
     * @return the record
     */
    public CometVersionRecord withIndexFormats(IndexFormats formats) {
        return new CometVersionRecord(
                version,
                marker,
                parameterPages,
                source,
                variableModTuple,
                overrides,
                ruleSeverities,
                valueMigrations,
                formats);
    }

    /**
     * The value migrations into this release from one source release.
     *
     * @param from the release the values were written for
     * @return its entries, in the metadata's order; empty when there are none
     */
    public List<ValueMigration> valueMigrationsFrom(ToolVersion from) {
        Objects.requireNonNull(from, "from");
        return valueMigrations.stream().filter(m -> m.from().equals(from)).toList();
    }

    /**
     * This release's severity for one version-scoped validation rule.
     *
     * @param rule the rule's stable identifier
     * @return the severity, or empty if this record does not state one
     */
    public Optional<RuleSeverity> ruleSeverity(String rule) {
        Objects.requireNonNull(rule, "rule");
        return Optional.ofNullable(ruleSeverities.get(rule));
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
