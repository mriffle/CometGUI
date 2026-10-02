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

package org.cometgui.params.comet.writer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.cometgui.domain.build.BuildIdentity;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.ports.HashService;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.model.ParameterEntry;
import org.cometgui.params.comet.model.ParameterValue;
import org.cometgui.params.comet.model.UnknownParameter;
import org.cometgui.params.comet.parser.ParamsLineReader;
import org.cometgui.params.comet.schema.CometVersionMarker;
import org.cometgui.params.comet.schema.CometVersionRecord;
import org.cometgui.params.comet.value.EnzymeDefinition;
import org.cometgui.params.comet.value.EnzymeTable;
import org.cometgui.params.comet.value.EnzymeTableCodec;

/**
 * Writes a {@link CometParameters} as canonical {@code comet.params} text: the same bytes for the
 * same model and build, under any default locale, on any platform.
 *
 * <p>The form, in order:
 *
 * <ol>
 *   <li>The {@code # comet_version} marker of the model's version, regenerated from the metadata's
 *       record of it ({@code R-PARAM-05}) and always on line 1: Comet 2026.02.2 looks for it only
 *       in the first seven lines and refuses a file without it ({@code LoadParameters} in {@code
 *       Comet.cpp}).
 *   <li>A generated header naming the CometGUI version that wrote the file and the target Comet
 *       version (<em>Canonical serialisation</em>). Comment lines; Comet ignores them.
 *   <li>Every modelled parameter in the metadata's order -- the order {@code comet -q} writes -- as
 *       {@code name = value}, followed by the curated inline comment where the metadata has one,
 *       starting at column 40 as Comet's own comments do. An empty value is written {@code name =}.
 *   <li>Only if there are any: a marked section of the unknown parameters ({@code R-PARAM-07}),
 *       each with the comment lines it was imported with, its value as imported and its inline
 *       comment.
 *   <li>The {@code [COMET_ENZYME_INFO]} table, last: Comet stops reading parameters at that line.
 * </ol>
 *
 * <p>Lines end with {@code \n}, never {@code \r\n}; the text is UTF-8 and ends with a line end.
 * Every value is written by the model's {@link
 * org.cometgui.params.comet.model.ParameterValueCodec}, whose numbers are {@code BigDecimal} and
 * {@code Integer} text, never a locale-sensitive format ({@code R-PARAM-11}).
 *
 * <p><strong>Refusal.</strong> The writer never emits an enzyme number absent from the table it
 * writes (<em>Enzyme definitions</em>): a model whose enzyme-reference parameters -- the metadata's
 * {@code enzymeTable.referencedBy} -- name such a number is refused with a {@link
 * ParamsWriteException} naming the parameter and the number. Comet 2026.02.2 would not catch it:
 * its "is missing definition" checks compare enzyme names against {@code "-"}, but {@code
 * EnzymeInfo}'s constructor starts them as {@code ""} and {@code "Cut_everywhere"}.
 */
public final class CanonicalParamsWriter {

    /** The start of the generated header line that follows the version marker. */
    public static final String HEADER_PREFIX = "# Written by CometGUI ";

    /** The 0-based column where an inline comment's {@code #} goes, as in Comet's own output. */
    public static final int COMMENT_COLUMN = 39;

    /** The comment line that opens the unknown-parameter section. */
    public static final String UNKNOWN_SECTION =
            "# Parameters CometGUI does not model for this Comet version, kept as imported";

    private static final char NEWLINE = '\n';

    private final BuildIdentity build;

    /**
     * Creates a writer for the running build.
     *
     * @param build the build, whose version the header names
     */
    public CanonicalParamsWriter(BuildIdentity build) {
        this.build = Objects.requireNonNull(build, "build");
    }

    /**
     * The canonical text of a model.
     *
     * @param model the model
     * @return the text, every line ending in {@code \n}
     * @throws ParamsWriteException if an enzyme-reference parameter names a number absent from the
     *     model's enzyme table
     */
    public String write(CometParameters model) {
        Objects.requireNonNull(model, "model");
        refuseUndefinedEnzymes(model);
        CometVersionRecord record = model.metadata().version(model.version()).orElseThrow();
        StringBuilder out = new StringBuilder();
        line(out, CometVersionMarker.LINE_PREFIX + record.marker().text());
        line(
                out,
                HEADER_PREFIX
                        + build.version()
                        + " for Comet "
                        + model.version().text()
                        + ". Canonical form, generated from the typed model.");
        line(out, "# Everything following the '#' symbol is treated as a comment.");
        line(out, "#");
        for (ParameterEntry entry : model.entries()) {
            String value = model.codec().format(entry.definition(), entry.value());
            line(out, declaration(entry.name(), value, entry.definition().inlineComment()));
        }
        List<UnknownParameter> unknowns = model.unknownParameters();
        if (!unknowns.isEmpty()) {
            line(out, "");
            line(out, UNKNOWN_SECTION + " (Comet " + model.version().text() + ")");
            line(out, "");
            for (UnknownParameter unknown : unknowns) {
                for (String comment : unknown.comments()) {
                    line(out, comment);
                }
                line(out, declaration(unknown.name(), unknown.value(), unknown.inlineComment()));
            }
        }
        line(out, "");
        line(out, "#");
        line(out, "# COMET_ENZYME_INFO _must_ be at the end of this parameters file");
        line(out, "#");
        line(out, ParamsLineReader.ENZYME_HEADER);
        for (String row : EnzymeTableCodec.format(model.enzymeTable())) {
            line(out, row);
        }
        return out.toString();
    }

    /**
     * The canonical text of a model, as the bytes written to disk.
     *
     * @param model the model
     * @return the UTF-8 bytes of {@link #write(CometParameters)}
     * @throws ParamsWriteException if the model is refused
     */
    public byte[] bytes(CometParameters model) {
        return write(model).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Writes the canonical file once and hashes what was written ({@code R-PARAM-12}).
     *
     * <p>The bytes are produced first -- a refused model touches nothing -- then written to a file
     * that must not exist yet, and the digests are computed by the hash service over that file as
     * it is on disk. The result carries the path and the digests and no text, so a later step
     * passes this path to Comet rather than writing the file again.
     *
     * @param model the model
     * @param target where to write; must not exist
     * @param hashService the hash service that reads the written file
     * @return the path, its digests and its size
     * @throws ParamsWriteException if the model is refused; nothing is written
     * @throws java.nio.file.FileAlreadyExistsException if {@code target} exists; it is not touched
     * @throws IOException if the file cannot be written or read back
     */
    public WrittenParams writeOnce(CometParameters model, Path target, HashService hashService)
            throws IOException {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(hashService, "hashService");
        byte[] bytes = bytes(model);
        Files.write(target, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        FileHashes hashes = hashService.hash(target);
        return new WrittenParams(target, hashes, Files.size(target));
    }

    private static void refuseUndefinedEnzymes(CometParameters model) {
        EnzymeTable table = model.enzymeTable();
        for (String name : model.metadata().enzymeTable().referencedBy()) {
            Optional<ParameterEntry> entry = model.entry(name);
            if (entry.isEmpty()) {
                continue;
            }
            int number = ((ParameterValue.Whole) entry.get().value()).value();
            if (!table.contains(number)) {
                throw new ParamsWriteException(
                        name,
                        name
                                + " = "
                                + number
                                + " names enzyme "
                                + number
                                + ", which is not in the "
                                + ParamsLineReader.ENZYME_HEADER
                                + " table being written (its numbers are "
                                + table.rows().stream().map(EnzymeDefinition::number).toList()
                                + "); the file is not written");
            }
        }
    }

    private static String declaration(String name, String value, Optional<String> comment) {
        String line = value.isEmpty() ? name + " =" : name + " = " + value;
        if (comment.isEmpty()) {
            return line;
        }
        String padding = " ".repeat(Math.max(1, COMMENT_COLUMN - line.length()));
        String text = comment.get();
        return line + padding + (text.isEmpty() ? "#" : "# " + text);
    }

    private static void line(StringBuilder out, String text) {
        out.append(text).append(NEWLINE);
    }
}
