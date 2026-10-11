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
import java.util.Optional;
import org.cometgui.domain.secrets.SecretRedactor;
import org.cometgui.provenance.json.JsonValue;
import org.cometgui.provenance.json.JsonWriter;
import org.cometgui.tools.limelight.LimelightQValueCutoff;

/**
 * The {@code results/limelight-settings.json} format, schema version 1: a run's {@link
 * LimelightSettings}, written by the one {@link JsonWriter} and read by the one {@code JsonReader}.
 *
 * <pre>
 * {
 *   "schemaVersion": 1,
 *   "limelightQValueCutoff": "0.01",
 *   "importDecoys": false,
 *   "independentDecoyPrefix": null,
 *   "openMod": false
 * }
 * </pre>
 *
 * <p>Members in that order. The cutoff is a string in plain decimal notation, read back through
 * {@link LimelightSettings#parseCutoff}; the prefix is a string or {@code null}. The reader applies
 * {@code run.json}'s rules ({@code R-RUN-04}): the schema version first, every member required, no
 * member this build does not know; a refusal names the file and the member and quotes no value.
 */
public final class LimelightSettingsJson {

    /** The version this build reads and writes. */
    public static final int SCHEMA_VERSION = 1;

    /** Every member of a version-1 document, in the order they are written. */
    static final List<String> MEMBERS =
            List.of(
                    "schemaVersion",
                    "limelightQValueCutoff",
                    "importDecoys",
                    "independentDecoyPrefix",
                    "openMod");

    private LimelightSettingsJson() {
        throw new AssertionError("LimelightSettingsJson is never instantiated");
    }

    /**
     * Renders the settings.
     *
     * @param settings the settings
     * @return the document
     * @throws NullPointerException if {@code settings} is {@code null}
     */
    public static String render(LimelightSettings settings) {
        Objects.requireNonNull(settings, "settings");
        JsonWriter writer =
                JsonWriter.redactingWith(SecretRedactor.patternsOnly())
                        .beginObject()
                        .name("schemaVersion")
                        .value(SCHEMA_VERSION)
                        .name("limelightQValueCutoff")
                        .value(settings.qCutoff().argument())
                        .name("importDecoys")
                        .value(settings.importDecoys())
                        .name("independentDecoyPrefix");
        if (settings.independentDecoyPrefix().isPresent()) {
            writer.value(settings.independentDecoyPrefix().get());
        } else {
            writer.nullValue();
        }
        return writer.name("openMod").value(settings.openMod()).endObject().finish();
    }

    /**
     * Reads the settings, the schema version first.
     *
     * @param text the document's text
     * @param document the document's path or label, for messages
     * @return the settings
     * @throws org.cometgui.domain.project.UnsupportedSchemaVersionException if the document is of
     *     another schema version, before any other member is read
     * @throws InvalidDocumentException if the document is not version-1 Limelight settings
     */
    public static LimelightSettings parse(String text, String document) {
        Objects.requireNonNull(text, "text");
        DocumentFields fields = new DocumentFields(document);
        JsonValue.JsonObject root = fields.root(text);
        fields.requireVersion(root, SCHEMA_VERSION);
        fields.requireOnly(root, DocumentFields.ROOT, MEMBERS);
        String cutoffText =
                fields.string(
                        fields.member(root, DocumentFields.ROOT, "limelightQValueCutoff"),
                        "limelightQValueCutoff");
        LimelightQValueCutoff cutoff;
        try {
            cutoff = LimelightSettings.parseCutoff(cutoffText);
        } catch (IllegalArgumentException refused) {
            throw fields.invalid(
                    "limelightQValueCutoff",
                    "must be a decimal number between 0 and 1 inclusive, written with a '.'"
                            + " decimal point");
        }
        boolean decoys = flag(fields, root, "importDecoys");
        JsonValue prefixValue = fields.member(root, DocumentFields.ROOT, "independentDecoyPrefix");
        Optional<String> prefix =
                prefixValue instanceof JsonValue.JsonNull
                        ? Optional.empty()
                        : Optional.of(fields.string(prefixValue, "independentDecoyPrefix"));
        boolean open = flag(fields, root, "openMod");
        return new LimelightSettings(cutoff, decoys, prefix, open);
    }

    private static boolean flag(DocumentFields fields, JsonValue.JsonObject root, String name) {
        JsonValue value = fields.member(root, DocumentFields.ROOT, name);
        if (value instanceof JsonValue.JsonBoolean asBoolean) {
            return asBoolean.value();
        }
        throw fields.invalid(name, "must be true or false");
    }
}
