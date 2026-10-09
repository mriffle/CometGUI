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

package org.cometgui.workflow.storage;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import org.cometgui.domain.secrets.SecretRedactor;
import org.cometgui.provenance.json.JsonValue;
import org.cometgui.provenance.json.JsonWriter;
import org.cometgui.results.filtering.DisplayFilters;
import org.cometgui.results.filtering.PeptideQValueFilter;
import org.cometgui.results.filtering.PsmQValueFilter;

/**
 * The {@code results/view-state.json} format, schema version 1: a run's display-filter values
 * ({@code R-RES-01}: "filter values are stored in project/run view state"), written by the one
 * {@link JsonWriter} and read by the one {@code JsonReader}.
 *
 * <pre>
 * {
 *   "schemaVersion": 1,
 *   "psmQValueFilter": "0.01",
 *   "peptideQValueFilter": "0.01"
 * }
 * </pre>
 *
 * <p>Members in that order; the document ends with one newline. Each cutoff is a string holding the
 * filter's own {@linkplain org.cometgui.results.filtering.QValueFilter#text() text}, so the decimal
 * the scientist typed survives exactly -- no binary floating point between the screen and the file.
 * A cutoff is read back through {@link PsmQValueFilter#parse} and {@link
 * PeptideQValueFilter#parse}, the one range rule ({@code [0, 1]}, both ends included); this class
 * has none of its own.
 *
 * <p>The reader applies the same rules as {@code run.json}'s ({@code R-RUN-04}): the schema version
 * first, a newer or older one refused before any other member is read; every member required; no
 * member this build does not know. A refusal names the file and the member and quotes no value.
 * {@code docs/reference/project_format.rst} is the format's reference.
 */
public final class ViewStateJson {

    /** The version this build reads and writes. */
    public static final int SCHEMA_VERSION = 1;

    /** Every member of a version-1 document, in the order they are written. */
    static final List<String> MEMBERS =
            List.of("schemaVersion", "psmQValueFilter", "peptideQValueFilter");

    private ViewStateJson() {
        throw new AssertionError("ViewStateJson is never instantiated");
    }

    /**
     * Renders the display filters.
     *
     * @param filters the filters
     * @return the document
     * @throws NullPointerException if {@code filters} is {@code null}
     */
    public static String render(DisplayFilters filters) {
        Objects.requireNonNull(filters, "filters");
        return JsonWriter.redactingWith(SecretRedactor.patternsOnly())
                .beginObject()
                .name("schemaVersion")
                .value(SCHEMA_VERSION)
                .name("psmQValueFilter")
                .value(filters.psm().text())
                .name("peptideQValueFilter")
                .value(filters.peptide().text())
                .endObject()
                .finish();
    }

    /**
     * Reads the display filters, the schema version first.
     *
     * @param text the document's text
     * @param document the document's path or label, for messages
     * @return the filters
     * @throws org.cometgui.domain.project.UnsupportedSchemaVersionException if the document is of
     *     another schema version, before any other member is read
     * @throws InvalidDocumentException if the document is not a version-1 view state, or a cutoff
     *     is not a number within {@code [0, 1]}
     */
    public static DisplayFilters parse(String text, String document) {
        Objects.requireNonNull(text, "text");
        DocumentFields fields = new DocumentFields(document);
        JsonValue.JsonObject root = fields.root(text);
        fields.requireVersion(root, SCHEMA_VERSION);
        fields.requireOnly(root, DocumentFields.ROOT, MEMBERS);
        PsmQValueFilter psm = cutoff(fields, root, "psmQValueFilter", PsmQValueFilter::parse);
        PeptideQValueFilter peptide =
                cutoff(fields, root, "peptideQValueFilter", PeptideQValueFilter::parse);
        return new DisplayFilters(psm, peptide);
    }

    /**
     * Reads one cutoff through its filter's own parser. The parser's message quotes the text it
     * refused, so it is replaced by the rule, as every refusal of this reader is.
     */
    private static <T> T cutoff(
            DocumentFields fields,
            JsonValue.JsonObject root,
            String name,
            Function<String, T> parser) {
        String text = fields.string(fields.member(root, DocumentFields.ROOT, name), name);
        try {
            return parser.apply(text);
        } catch (IllegalArgumentException refused) {
            throw fields.invalid(
                    name,
                    "must be a decimal number between 0 and 1 inclusive, written with a '.'"
                            + " decimal point");
        }
    }
}
