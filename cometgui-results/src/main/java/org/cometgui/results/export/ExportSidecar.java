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

package org.cometgui.results.export;

import java.time.Instant;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.secrets.SecretRedactor;
import org.cometgui.provenance.json.CanonicalTimestamp;
import org.cometgui.provenance.json.JsonWriter;
import org.cometgui.results.filtering.FilterCounts;

/**
 * An export's sidecar, {@code <export>.json}, schema version {@value #SCHEMA_VERSION}: what the
 * export is, where it came from and what was applied to make it ({@code R-RES-04}, {@code
 * R-RES-01}: "exported filtered files identifying the cutoff in their metadata"). Written by the
 * one {@link JsonWriter}, so every string value is redacted on the way through ({@code R-SEC-03}).
 * {@code docs/reference/project_format.rst} is the format's reference.
 */
final class ExportSidecar {

    /** The sidecar format this build writes. */
    static final int SCHEMA_VERSION = 1;

    /** The {@code export} member of a filtered table's sidecar. */
    static final String FILTERED_TABLE = "filtered-table";

    /** The {@code export} member of a weights export's sidecar. */
    static final String LEARNED_FEATURE_WEIGHTS = "learned-feature-weights";

    /** How a filter decides, in words. */
    static final String FILTER_RULE =
            "a row passes when its q-value is known and q-value <= cutoff; the cutoff is inclusive";

    /** The unknown-q-value policy ({@code R-RES-02}), in words. */
    static final String UNKNOWN_Q_VALUE_POLICY =
            "a row whose q-value is missing, unparsable or outside [0, 1] is neither passing nor"
                    + " failing: it is counted as unknown q-value, and written only when the"
                    + " category written is unknown-q-value or all";

    /** What a filtered table export holds, in words. */
    static final String ROWS =
            "the raw table's header line, then every row of the category written, each byte for"
                    + " byte as in the raw table, line terminator included, in the raw table's"
                    + " order";

    /** How the weights file writes numbers, in words. */
    static final String NUMBERS =
            "Java Double.toString: the shortest decimal that reads back as the same double, with a"
                    + " '.' decimal point, no grouping, and E notation for very small or large"
                    + " magnitudes";

    private ExportSidecar() {
        throw new AssertionError("ExportSidecar is never instantiated");
    }

    /** The members every sidecar shares. */
    record Common(
            String runId,
            String cometGuiVersion,
            Instant created,
            String filePath,
            long fileSize,
            FileHashes fileHashes,
            String sourcePath,
            long sourceSize,
            FileHashes sourceHashes) {}

    /**
     * A filtered table's sidecar.
     *
     * @param common the shared members
     * @param table the table's word
     * @param filterName which filter
     * @param cutoff the cutoff's text
     * @param category the category's word
     * @param before the counts over the raw table
     * @param rowsWritten the rows in the export
     * @param redactor the one rule set
     * @return the document
     */
    static String table(
            Common common,
            String table,
            String filterName,
            String cutoff,
            String category,
            FilterCounts before,
            long rowsWritten,
            SecretRedactor redactor) {
        JsonWriter json = start(redactor, FILTERED_TABLE, common);
        json.name("source").beginObject().name("table").value(table);
        source(json, common);
        json.name("filter")
                .beginObject()
                .name("name")
                .value(filterName)
                .name("cutoff")
                .value(cutoff)
                .name("rule")
                .value(FILTER_RULE)
                .endObject()
                .name("category")
                .value(category)
                .name("unknownQValuePolicy")
                .value(UNKNOWN_Q_VALUE_POLICY)
                .name("countsBefore")
                .beginObject()
                .name("total")
                .value(before.total())
                .name("passing")
                .value(before.passing())
                .name("failing")
                .value(before.failing())
                .name("unknownQValue")
                .value(before.unknownQValue())
                .endObject()
                .name("rowsWritten")
                .value(rowsWritten)
                .name("rows")
                .value(ROWS)
                .name("textFilterApplied")
                .value(false)
                .name("sortApplied")
                .value(false);
        return json.endObject().finish();
    }

    /**
     * A weights export's sidecar.
     *
     * @param common the shared members
     * @param splitCount the splits, read from the artefact
     * @param featureCount the features, the bias term included
     * @param redactor the one rule set
     * @return the document
     */
    static String weights(
            Common common, int splitCount, int featureCount, SecretRedactor redactor) {
        JsonWriter json = start(redactor, LEARNED_FEATURE_WEIGHTS, common);
        json.name("source").beginObject();
        source(json, common);
        json.name("splitCount")
                .value(splitCount)
                .name("featureCount")
                .value(featureCount)
                .name("numbers")
                .value(NUMBERS);
        return json.endObject().finish();
    }

    private static JsonWriter start(SecretRedactor redactor, String export, Common common) {
        return JsonWriter.redactingWith(redactor)
                .beginObject()
                .name("schemaVersion")
                .value(SCHEMA_VERSION)
                .name("export")
                .value(export)
                .name("runId")
                .value(common.runId())
                .name("cometguiVersion")
                .value(common.cometGuiVersion())
                .name("created")
                .value(CanonicalTimestamp.utcMillis(common.created()))
                .name("file")
                .beginObject()
                .name("path")
                .value(common.filePath())
                .name("size")
                .value(common.fileSize())
                .name("md5")
                .value(common.fileHashes().md5())
                .name("sha256")
                .value(common.fileHashes().sha256())
                .endObject();
    }

    /** The source object's members after any of its own, and its closing brace. */
    private static void source(JsonWriter json, Common common) {
        json.name("path")
                .value(common.sourcePath())
                .name("size")
                .value(common.sourceSize())
                .name("md5")
                .value(common.sourceHashes().md5())
                .name("sha256")
                .value(common.sourceHashes().sha256())
                .endObject();
    }
}
