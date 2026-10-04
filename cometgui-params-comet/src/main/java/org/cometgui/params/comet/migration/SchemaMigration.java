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

package org.cometgui.params.comet.migration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.model.Diagnostic;
import org.cometgui.params.comet.model.ParameterEntry;
import org.cometgui.params.comet.model.ParameterValue;
import org.cometgui.params.comet.model.ParameterValueCodec;
import org.cometgui.params.comet.model.UnknownParameter;
import org.cometgui.params.comet.model.ValueOrigin;
import org.cometgui.params.comet.parser.CometParamsParser;
import org.cometgui.params.comet.parser.ParamsLine;
import org.cometgui.params.comet.parser.ParamsLineReader;
import org.cometgui.params.comet.parser.ParseResult;
import org.cometgui.params.comet.schema.CometVersionMarker;
import org.cometgui.params.comet.schema.CuratedMetadata;
import org.cometgui.params.comet.schema.ParameterDefinition;
import org.cometgui.params.comet.schema.ValueMigration;
import org.cometgui.params.comet.validation.CometValidator;
import org.cometgui.params.comet.validation.Finding;
import org.cometgui.params.comet.validation.Rule;
import org.cometgui.params.comet.value.ValueSyntaxException;

/**
 * Moves a parameter set from one curated Comet version's schema to another's, and reports what
 * happened to every parameter.
 *
 * <p>Migration is <strong>explicit</strong> ({@code R-TOOL-09}): nothing in the parser, the writer
 * or the model calls it, a model of one version is never silently treated as another's, and the
 * result is a new model beside an unchanged source, with a {@link MigrationReport} the user reviews
 * before using it. The rules, per parameter:
 *
 * <ul>
 *   <li>modelled in both versions: the value is carried by {@link VersionConversion} with its
 *       origin -- {@link MigrationEntry.Outcome#CARRIED} as the same text, {@link
 *       MigrationEntry.Outcome#RESHAPED} re-written in the target's syntax with the same meaning,
 *       {@link MigrationEntry.Outcome#CONVERTED} as the equivalent the target's version record
 *       states, {@link MigrationEntry.Outcome#NOTED} carried with the record's notice, or {@link
 *       MigrationEntry.Outcome#NEEDS_ATTENTION} when the target cannot hold it: the target's
 *       default is used and the source value is reported, never silently lost or guessed at;
 *   <li>new in the target ({@link MigrationEntry.Outcome#ADDED}): the target's default, origin
 *       {@link ValueOrigin#COMET_DEFAULT};
 *   <li>not in the target ({@link MigrationEntry.Outcome#REMOVED_KEPT_AS_UNKNOWN}): kept as an
 *       unknown parameter with its value text and curated inline comment, and reported -- never
 *       dropped ({@code R-PARAM-07}); validation then blocks it until the user removes it;
 *   <li>the source's unknown parameters: kept ({@link MigrationEntry.Outcome#UNKNOWN_CARRIED}), or,
 *       where the target models the name, read as its value ({@link
 *       MigrationEntry.Outcome#UNKNOWN_ADOPTED}, origin {@link ValueOrigin#IMPORTED}).
 * </ul>
 *
 * <p>Which values the target's version record converts, flags or notes is data ({@link
 * ValueMigration}, decision C-2), keyed by the source release; an entry matched by a validation
 * rule applies where the source model's own validation finds that rule on the parameter. Nothing
 * here asks which version a model is.
 *
 * <p>The enzyme table is the file's content, not the schema's, and is carried unchanged. The
 * migrated model carries no parse diagnostics: it was not produced by a parse, and the source keeps
 * its own.
 */
public final class SchemaMigration {

    /**
     * The line an unknown parameter made by migration reports: it was declared on no line of any
     * file, so the 1-based line number is 0.
     */
    public static final int NOT_FROM_A_FILE = 0;

    private SchemaMigration() {}

    /**
     * Migrates a model to another curated version.
     *
     * @param source the model to migrate; unchanged
     * @param target the Comet version to migrate to
     * @return the migrated model and the report
     * @throws IllegalArgumentException if the metadata was not curated against the target version
     */
    public static MigrationResult migrate(CometParameters source, ToolVersion target) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        CuratedMetadata metadata = source.metadata();
        ToolVersion from = source.version();
        VersionConversion conversion = VersionConversion.between(metadata, from, target);
        Map<String, Set<String>> sourceRules = sourceRules(source, target, conversion);
        ParameterValueCodec codec = ParameterValueCodec.forVersion(metadata, target);
        Map<String, UnknownParameter> sourceUnknowns = new LinkedHashMap<>();
        source.unknownParameters().forEach(u -> sourceUnknowns.put(u.name(), u));

        List<ParameterEntry> entries = new ArrayList<>();
        List<MigrationEntry> report = new ArrayList<>();
        for (ParameterDefinition definition : metadata.parametersFor(target)) {
            String name = definition.name();
            Optional<ParameterEntry> carried = source.entry(name);
            if (carried.isPresent()) {
                VersionConversion.Result result =
                        conversion.convert(
                                name, source.text(name), sourceRules.getOrDefault(name, Set.of()));
                if (result.status().usable()) {
                    entries.add(
                            new ParameterEntry(
                                    definition,
                                    codec.parse(definition, result.targetText().orElseThrow()),
                                    carried.get().origin()));
                    report.add(
                            new MigrationEntry(
                                    name,
                                    outcome(result),
                                    Optional.of(result.sourceText()),
                                    result.targetText().orElseThrow(),
                                    result.explanation()));
                } else {
                    ParameterEntry fallback = defaultEntry(codec, definition);
                    entries.add(fallback);
                    report.add(
                            new MigrationEntry(
                                    name,
                                    MigrationEntry.Outcome.NEEDS_ATTENTION,
                                    Optional.of(result.sourceText()),
                                    definition.defaultValue(),
                                    result.explanation()
                                            + "; the migrated set holds Comet "
                                            + target.text()
                                            + "'s default instead, and the source value is"
                                            + " kept only in this report"));
                }
            } else if (sourceUnknowns.containsKey(name)) {
                UnknownParameter unknown = sourceUnknowns.remove(name);
                adopt(codec, definition, unknown, from, target, entries, report);
            } else {
                entries.add(defaultEntry(codec, definition));
                report.add(
                        new MigrationEntry(
                                name,
                                MigrationEntry.Outcome.ADDED,
                                Optional.empty(),
                                definition.defaultValue(),
                                name
                                        + " is new in Comet "
                                        + target.text()
                                        + " (Comet "
                                        + from.text()
                                        + " has no such parameter) and takes its default"));
            }
        }

        List<UnknownParameter> unknowns = new ArrayList<>();
        List<MigrationEntry> removed = new ArrayList<>();
        for (ParameterEntry entry : source.entries()) {
            String name = entry.name();
            if (metadata.parameter(name, target).isPresent()) {
                continue;
            }
            String text = source.text(name);
            unknowns.add(
                    new UnknownParameter(
                            name,
                            text,
                            entry.definition().inlineComment(),
                            List.of(),
                            NOT_FROM_A_FILE));
            removed.add(
                    new MigrationEntry(
                            name,
                            MigrationEntry.Outcome.REMOVED_KEPT_AS_UNKNOWN,
                            Optional.of(text),
                            text,
                            name
                                    + " is a parameter of Comet "
                                    + from.text()
                                    + " that Comet "
                                    + target.text()
                                    + " does not have; it is kept as an unknown parameter, which"
                                    + " that version would ignore, until it is removed"));
        }
        List<UnknownParameter> kept = new ArrayList<>(sourceUnknowns.values());
        for (UnknownParameter unknown : kept) {
            report.add(
                    new MigrationEntry(
                            unknown.name(),
                            MigrationEntry.Outcome.UNKNOWN_CARRIED,
                            Optional.of(unknown.value()),
                            unknown.value(),
                            unknown.name()
                                    + " was an unknown parameter of the source and is not modelled"
                                    + " for Comet "
                                    + target.text()
                                    + " either; it is kept as imported"));
        }
        report.addAll(removed);
        kept.addAll(unknowns);
        CometParameters migrated =
                CometParameters.of(
                        metadata, target, entries, source.enzymeTable(), kept, List.of());
        return new MigrationResult(source, migrated, new MigrationReport(from, target, report));
    }

    /** What a usable conversion is, in the report's words. */
    private static MigrationEntry.Outcome outcome(VersionConversion.Result result) {
        if (result.applied().stream().anyMatch(m -> m.action() == ValueMigration.Action.CONVERT)) {
            return MigrationEntry.Outcome.CONVERTED;
        }
        if (result.status() == VersionConversion.Status.CONVERTED) {
            return MigrationEntry.Outcome.RESHAPED;
        }
        return result.applied().isEmpty()
                ? MigrationEntry.Outcome.CARRIED
                : MigrationEntry.Outcome.NOTED;
    }

    /**
     * The rules the target's value migrations from the source release are matched by, found on each
     * parameter of the source model by its own validation; empty, and no validation run, when no
     * entry is matched by a rule.
     *
     * @throws IllegalStateException if any of the target's value migrations names a rule that does
     *     not exist
     */
    private static Map<String, Set<String>> sourceRules(
            CometParameters source, ToolVersion target, VersionConversion conversion) {
        for (ValueMigration migration :
                source.metadata().version(target).orElseThrow().valueMigrations()) {
            migration
                    .rule()
                    .filter(id -> Rule.byId(id).isEmpty())
                    .ifPresent(
                            id -> {
                                throw new IllegalStateException(
                                        "Comet "
                                                + target.text()
                                                + "'s version record migrates values by the rule"
                                                + " \""
                                                + id
                                                + "\", which is not a rule");
                            });
        }
        Set<String> consulted = new HashSet<>();
        conversion.migrations().forEach(m -> m.rule().ifPresent(consulted::add));
        Map<String, Set<String>> found = new HashMap<>();
        if (consulted.isEmpty()) {
            return found;
        }
        for (Finding finding : CometValidator.standard().validate(source).findings()) {
            if (consulted.contains(finding.rule().id())) {
                for (String parameter : finding.parameters()) {
                    found.computeIfAbsent(parameter, p -> new HashSet<>()).add(finding.rule().id());
                }
            }
        }
        return found;
    }

    private static void adopt(
            ParameterValueCodec codec,
            ParameterDefinition definition,
            UnknownParameter unknown,
            ToolVersion from,
            ToolVersion target,
            List<ParameterEntry> entries,
            List<MigrationEntry> report) {
        String name = definition.name();
        ParameterValue value;
        try {
            value = codec.parse(definition, unknown.value());
        } catch (ValueSyntaxException unreadable) {
            entries.add(defaultEntry(codec, definition));
            report.add(
                    new MigrationEntry(
                            name,
                            MigrationEntry.Outcome.NEEDS_ATTENTION,
                            Optional.of(unknown.value()),
                            definition.defaultValue(),
                            name
                                    + " = "
                                    + unknown.value()
                                    + " was an unknown parameter of the Comet "
                                    + from.text()
                                    + " set, and Comet "
                                    + target.text()
                                    + " cannot read that value: "
                                    + unreadable.getMessage()
                                    + "; the migrated set holds the default instead"));
            return;
        }
        entries.add(new ParameterEntry(definition, value, ValueOrigin.IMPORTED));
        report.add(
                new MigrationEntry(
                        name,
                        MigrationEntry.Outcome.UNKNOWN_ADOPTED,
                        Optional.of(unknown.value()),
                        codec.format(definition, value),
                        name
                                + " was an unknown parameter of the Comet "
                                + from.text()
                                + " set and is a parameter of Comet "
                                + target.text()
                                + "; its value is now read as one"));
    }

    private static ParameterEntry defaultEntry(
            ParameterValueCodec codec, ParameterDefinition definition) {
        return new ParameterEntry(
                definition,
                codec.parse(definition, definition.defaultValue()),
                ValueOrigin.COMET_DEFAULT);
    }

    /**
     * Reads a parameter file as the Comet version its own {@code # comet_version} marker names, and
     * migrates it to another.
     *
     * @param metadata the curated metadata
     * @param text the whole file
     * @param target the Comet version to migrate to
     * @return the migrated model and the report; the result's source is the file's model, with its
     *     parse warnings
     * @throws MigrationException if the file names no version, names one the metadata was not
     *     curated against, or does not parse as that version -- the message says which, with the
     *     parse's errors
     * @throws IllegalArgumentException if the metadata was not curated against the target version
     */
    public static MigrationResult migrateFile(
            CuratedMetadata metadata, String text, ToolVersion target) {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(target, "target");
        ParamsLine marker =
                ParamsLineReader.read(text).lines().stream()
                        .filter(ParamsLine.VersionMarker.class::isInstance)
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new MigrationException(
                                                "the file has no \""
                                                        + CometVersionMarker.LINE_PREFIX.strip()
                                                        + "\" line, so the Comet version it was"
                                                        + " written for is unknown; parse it as the"
                                                        + " version it is for, then migrate the"
                                                        + " model"));
        ToolVersion from;
        try {
            from = CometVersionMarker.parseLine(marker.text().strip()).toolVersion();
        } catch (IllegalArgumentException unreadable) {
            throw new MigrationException(
                    "line "
                            + marker.number()
                            + " names no readable Comet version: "
                            + marker.text());
        }
        if (metadata.version(from).isEmpty()) {
            throw new MigrationException(
                    "the file is for Comet "
                            + from.text()
                            + ", which CometGUI's parameter metadata does not describe, so it"
                            + " cannot be read as that version or migrated from it");
        }
        ParseResult parsed = new CometParamsParser(metadata, from).parse(text);
        if (!parsed.succeeded()) {
            throw new MigrationException(
                    "the file does not parse as Comet "
                            + from.text()
                            + ", so nothing was migrated: "
                            + parsed.errors().stream().map(Diagnostic::message).toList());
        }
        return migrate(parsed.model().orElseThrow(), target);
    }
}
