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

package org.cometgui.params.comet.parser;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.model.Diagnostic;
import org.cometgui.params.comet.model.ParameterEntry;
import org.cometgui.params.comet.model.ParameterValue;
import org.cometgui.params.comet.model.ParameterValueCodec;
import org.cometgui.params.comet.model.UnknownParameter;
import org.cometgui.params.comet.model.ValueOrigin;
import org.cometgui.params.comet.schema.CometVersionMarker;
import org.cometgui.params.comet.schema.CometVersionRecord;
import org.cometgui.params.comet.schema.CuratedMetadata;
import org.cometgui.params.comet.schema.ParameterDefinition;
import org.cometgui.params.comet.value.EnzymeDefinition;
import org.cometgui.params.comet.value.EnzymeTable;
import org.cometgui.params.comet.value.EnzymeTableCodec;
import org.cometgui.params.comet.value.ValueSyntaxException;

/**
 * Parses a whole {@code comet.params} text into a typed {@link CometParameters} for the selected
 * Comet version, built on {@link ParamsLineReader} (the one place that knows how a line is shaped)
 * and the value codecs.
 *
 * <p><strong>All or nothing.</strong> A finding is an {@link Diagnostic.Severity#ERROR} when the
 * file cannot be represented faithfully, and then no model is produced ({@code R-PARAM-08}):
 *
 * <ul>
 *   <li>a malformed line ({@link Diagnostic.Code#MALFORMED_LINE});
 *   <li>a parameter declared twice ({@link Diagnostic.Code#DUPLICATE_PARAMETER}), naming both
 *       lines. Comet 2026.02.2 reads every declaration in order and keeps the last one without a
 *       word ({@code LoadParameters} in {@code Comet.cpp}; {@code SetParam} in {@code
 *       CometSearchManager.cpp} replaces an existing entry), so such a file does not mean what its
 *       first declaration says;
 *   <li>a second {@code # comet_version} line;
 *   <li>a modelled parameter whose value text cannot be read as its kind;
 *   <li>no {@code [COMET_ENZYME_INFO]} table, an enzyme row that cannot be read, or two rows with
 *       one number.
 * </ul>
 *
 * <p>A finding is a {@link Diagnostic.Severity#WARNING} when the file can be represented and the
 * user should still know: a {@code # comet_version} marker naming another version than the selected
 * one, or one that cannot be read, or none at all ({@code R-PARAM-06}; the warning names both
 * versions); and a parameter the schema does not model for the version ({@code R-PARAM-07}), which
 * is kept with its value and comments as an {@link UnknownParameter} and written back.
 *
 * <p>A modelled parameter the file declares takes its value from the file, with origin {@link
 * ValueOrigin#IMPORTED}; one it does not declare takes the schema default, with origin {@link
 * ValueOrigin#COMET_DEFAULT}. An empty value is a value: {@code peff_obo =} imports the empty text,
 * not the default.
 *
 * <p>The file's comment structure is preserved on {@link ParseResult#comments()} ({@code
 * R-PARAM-05}): the marker line, each modelled parameter's block comment, inline comment and
 * continuation lines, and the comments around the enzyme table.
 */
public final class CometParamsParser {

    private final CuratedMetadata metadata;

    private final ToolVersion selected;

    private final CometVersionRecord record;

    private final ParameterValueCodec codec;

    /**
     * Creates a parser for one selected Comet version.
     *
     * @param metadata the curated metadata
     * @param selected the Comet version the file is being imported for
     * @throws IllegalArgumentException if the metadata was not curated against that version
     */
    public CometParamsParser(CuratedMetadata metadata, ToolVersion selected) {
        this.metadata = Objects.requireNonNull(metadata, "metadata");
        this.selected = Objects.requireNonNull(selected, "selected");
        this.record =
                metadata.version(selected)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "the metadata was not curated against Comet "
                                                        + selected.text()));
        this.codec = ParameterValueCodec.forVersion(metadata, selected);
    }

    /**
     * Parses a text.
     *
     * @param text the whole file, decoded
     * @return the model and its warnings, or no model and the errors that stopped it
     */
    public ParseResult parse(String text) {
        Objects.requireNonNull(text, "text");
        return new Run(ParamsLineReader.read(text)).result();
    }

    /** One parse: the state accumulated while walking the lines. */
    private final class Run {

        private final ParamsText lines;

        private final List<Diagnostic> found = new ArrayList<>();

        private final Map<String, ParamsLine.Declaration> declared = new LinkedHashMap<>();

        private final Map<String, ParameterValue> values = new LinkedHashMap<>();

        private final List<UnknownParameter> unknowns = new ArrayList<>();

        private final List<String> commentsAbove = new ArrayList<>();

        private final List<String> blockAbove = new ArrayList<>();

        private final Map<String, Declared> declaredComments = new LinkedHashMap<>();

        /** The declaration whose continuation lines may follow, until a non-continuation line. */
        private Optional<Declared> continuing = Optional.empty();

        private final List<String> beforeTable = new ArrayList<>();

        private final List<String> inTable = new ArrayList<>();

        private boolean pastTableHeader;

        private Optional<String> markerLine = Optional.empty();

        Run(ParamsText lines) {
            this.lines = lines;
        }

        ParseResult result() {
            for (ParamsLine.Malformed line : lines.malformed()) {
                found.add(
                        Diagnostic.of(
                                Diagnostic.Code.MALFORMED_LINE,
                                List.of(line.number()),
                                null,
                                "line "
                                        + line.number()
                                        + " is not a comment, a declaration or an enzyme row: "
                                        + line.reason()
                                        + ": \""
                                        + content(line.text())
                                        + "\""));
            }
            versionMarker();
            for (ParamsLine line : lines.lines()) {
                walk(line);
            }
            Optional<EnzymeTable> table = enzymeTable();
            found.sort(Comparator.comparingInt(d -> d.lines().isEmpty() ? 0 : d.lines().get(0)));
            if (found.stream().anyMatch(Diagnostic::isError)) {
                return new ParseResult(Optional.empty(), found, comments());
            }
            List<ParameterEntry> entries = new ArrayList<>();
            for (ParameterDefinition definition : metadata.parametersFor(selected)) {
                ParameterValue value = values.get(definition.name());
                entries.add(
                        value == null
                                ? new ParameterEntry(
                                        definition,
                                        codec.parse(definition, definition.defaultValue()),
                                        ValueOrigin.COMET_DEFAULT)
                                : new ParameterEntry(definition, value, ValueOrigin.IMPORTED));
            }
            CometParameters model =
                    CometParameters.of(
                            metadata, selected, entries, table.orElseThrow(), unknowns, found);
            return new ParseResult(Optional.of(model), found, comments());
        }

        private void versionMarker() {
            List<ParamsLine> markers =
                    lines.lines().stream()
                            .filter(ParamsLine.VersionMarker.class::isInstance)
                            .toList();
            String selectedText =
                    "the selected Comet is "
                            + selected.text()
                            + " (\""
                            + record.marker().text()
                            + "\")";
            if (markers.isEmpty()) {
                found.add(
                        Diagnostic.of(
                                Diagnostic.Code.VERSION_MARKER_MISSING,
                                List.of(),
                                null,
                                "the file has no \""
                                        + CometVersionMarker.LINE_PREFIX.strip()
                                        + "\" line, so the Comet version it was written for is"
                                        + " unknown; "
                                        + selectedText
                                        + ", and its parameters are read and will be written as"
                                        + " that version's"));
                return;
            }
            ParamsLine first = markers.get(0);
            for (ParamsLine again : markers.subList(1, markers.size())) {
                found.add(
                        Diagnostic.of(
                                Diagnostic.Code.DUPLICATE_VERSION_MARKER,
                                List.of(first.number(), again.number()),
                                null,
                                "the Comet version is declared twice, on line "
                                        + first.number()
                                        + " and again on line "
                                        + again.number()));
            }
            String line = content(first.text());
            CometVersionMarker marker;
            try {
                marker = CometVersionMarker.parseLine(line);
            } catch (IllegalArgumentException unreadable) {
                found.add(
                        Diagnostic.of(
                                Diagnostic.Code.VERSION_MISMATCH,
                                List.of(first.number()),
                                null,
                                "line "
                                        + first.number()
                                        + " names the Comet version \""
                                        + line.substring(CometVersionMarker.LINE_PREFIX.length())
                                                .strip()
                                        + "\", which is not a version this project can read; "
                                        + selectedText
                                        + ", and the parameters are read and will be written as"
                                        + " that version's"));
                return;
            }
            if (!marker.toolVersion().equals(selected)) {
                found.add(
                        Diagnostic.of(
                                Diagnostic.Code.VERSION_MISMATCH,
                                List.of(first.number()),
                                null,
                                "line "
                                        + first.number()
                                        + " says the file was written for Comet "
                                        + marker.toolVersion().text()
                                        + " (\""
                                        + marker.text()
                                        + "\"), and "
                                        + selectedText
                                        + "; the parameters are read and will be written as"
                                        + " that version's"));
            }
        }

        private void walk(ParamsLine line) {
            switch (line) {
                case ParamsLine.Comment comment -> {
                    String text = content(comment.text());
                    commentsAbove.add(text);
                    if (continuing.isPresent() && Character.isWhitespace(text.charAt(0))) {
                        continuing.get().continuation().add(text);
                    } else {
                        continuing = Optional.empty();
                        structure(text);
                    }
                }
                case ParamsLine.Blank blank -> {
                    commentsAbove.clear();
                    continuing = Optional.empty();
                    structure(content(blank.text()));
                }
                case ParamsLine.Declaration declaration -> {
                    continuing = Optional.empty();
                    declaration(declaration);
                    commentsAbove.clear();
                    blockAbove.clear();
                }
                case ParamsLine.EnzymeHeader header -> {
                    // No declaration follows the header (the line reader makes one malformed),
                    // so neither comment buffer is read again; they are not cleared here.
                    beforeTable.addAll(blockAbove);
                    pastTableHeader = true;
                }
                case ParamsLine.VersionMarker marker -> {
                    commentsAbove.clear();
                    if (markerLine.isEmpty()) {
                        markerLine = Optional.of(content(marker.text()));
                    }
                }
                default -> continuing = Optional.empty();
            }
        }

        /** Records a comment or blank line of the file's comment structure (R-PARAM-05). */
        private void structure(String text) {
            if (pastTableHeader) {
                inTable.add(text);
            } else {
                blockAbove.add(text);
            }
        }

        private ImportedComments comments() {
            Map<String, ImportedComments.ParameterComments> parameters = new LinkedHashMap<>();
            declaredComments.forEach(
                    (name, collected) ->
                            parameters.put(
                                    name,
                                    new ImportedComments.ParameterComments(
                                            collected.above(),
                                            collected.inline(),
                                            collected.continuation())));
            return new ImportedComments(markerLine, parameters, beforeTable, inTable);
        }

        private void declaration(ParamsLine.Declaration line) {
            String name = line.name();
            ParamsLine.Declaration earlier = declared.putIfAbsent(name, line);
            if (earlier != null) {
                found.add(
                        Diagnostic.of(
                                Diagnostic.Code.DUPLICATE_PARAMETER,
                                List.of(earlier.number(), line.number()),
                                name,
                                name
                                        + " is declared on line "
                                        + earlier.number()
                                        + " and again on line "
                                        + line.number()
                                        + "; Comet would silently use the value on line "
                                        + line.number()
                                        + ", so the file does not say what its first"
                                        + " declaration says. Keep one of them"));
                return;
            }
            Optional<ParameterDefinition> definition = metadata.parameter(name);
            if (definition.isPresent() && definition.get().supportedVersions().contains(selected)) {
                Declared collecting =
                        new Declared(
                                List.copyOf(blockAbove), line.inlineComment(), new ArrayList<>());
                declaredComments.put(name, collecting);
                continuing = Optional.of(collecting);
                try {
                    values.put(name, codec.parse(definition.get(), line.value()));
                } catch (ValueSyntaxException unreadable) {
                    found.add(
                            Diagnostic.of(
                                    Diagnostic.Code.UNREADABLE_VALUE,
                                    List.of(line.number()),
                                    name,
                                    "line " + line.number() + ": " + unreadable.getMessage()));
                }
                return;
            }
            unknown(line, definition.isPresent());
        }

        private void unknown(ParamsLine.Declaration line, boolean otherVersions) {
            String name = line.name();
            UnknownParameter unknown;
            try {
                unknown =
                        new UnknownParameter(
                                name,
                                line.value(),
                                line.inlineComment(),
                                commentsAbove,
                                line.number());
            } catch (IllegalArgumentException unwritable) {
                found.add(
                        Diagnostic.of(
                                Diagnostic.Code.UNREADABLE_VALUE,
                                List.of(line.number()),
                                name,
                                "line "
                                        + line.number()
                                        + ": the unknown parameter "
                                        + name
                                        + " cannot be kept as written: "
                                        + unwritable.getMessage()));
                return;
            }
            unknowns.add(unknown);
            found.add(
                    Diagnostic.of(
                            otherVersions
                                    ? Diagnostic.Code.NOT_IN_VERSION
                                    : Diagnostic.Code.UNKNOWN_PARAMETER,
                            List.of(line.number()),
                            name,
                            "line "
                                    + line.number()
                                    + ": "
                                    + name
                                    + (otherVersions
                                            ? " is modelled for other Comet versions but not for "
                                            : " is not a parameter CometGUI models for Comet ")
                                    + selected.text()
                                    + "; it is kept as imported, with its comments, and written"
                                    + " back unless it is removed"));
        }

        /** The table, or empty after recording the error that makes it unusable. */
        private Optional<EnzymeTable> enzymeTable() {
            boolean header =
                    lines.lines().stream().anyMatch(ParamsLine.EnzymeHeader.class::isInstance);
            if (!header) {
                found.add(
                        Diagnostic.of(
                                Diagnostic.Code.ENZYME_TABLE_MISSING,
                                List.of(),
                                null,
                                "the file has no "
                                        + ParamsLineReader.ENZYME_HEADER
                                        + " table, so no enzyme number it names is defined"));
                return Optional.empty();
            }
            List<EnzymeDefinition> rows = new ArrayList<>();
            Map<Integer, ParamsLine.EnzymeRow> byNumber = new LinkedHashMap<>();
            boolean readable = true;
            for (ParamsLine.EnzymeRow row : lines.enzymeRows()) {
                EnzymeDefinition definition;
                try {
                    definition = EnzymeTableCodec.parseRow(row.text());
                } catch (IllegalArgumentException unreadable) {
                    found.add(
                            Diagnostic.of(
                                    Diagnostic.Code.UNREADABLE_ENZYME_ROW,
                                    List.of(row.number()),
                                    null,
                                    "line " + row.number() + ": " + unreadable.getMessage()));
                    readable = false;
                    continue;
                }
                ParamsLine.EnzymeRow earlier = byNumber.putIfAbsent(definition.number(), row);
                if (earlier != null) {
                    found.add(
                            Diagnostic.of(
                                    Diagnostic.Code.DUPLICATE_ENZYME_NUMBER,
                                    List.of(earlier.number(), row.number()),
                                    null,
                                    "enzyme number "
                                            + definition.number()
                                            + " is defined on line "
                                            + earlier.number()
                                            + " and again on line "
                                            + row.number()
                                            + "; Comet would silently use the row on line "
                                            + row.number()));
                    readable = false;
                    continue;
                }
                rows.add(definition);
            }
            return readable ? Optional.of(new EnzymeTable(rows)) : Optional.empty();
        }
    }

    /** One declared modelled parameter's comments while the walk is still collecting them. */
    private record Declared(
            List<String> above, Optional<String> inline, List<String> continuation) {}

    private static String content(String raw) {
        return raw.endsWith("\r") ? raw.substring(0, raw.length() - 1) : raw;
    }
}
