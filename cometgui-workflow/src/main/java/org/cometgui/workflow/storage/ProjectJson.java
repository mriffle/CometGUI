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

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.cometgui.domain.project.ProjectDescriptor;
import org.cometgui.domain.project.ProjectId;
import org.cometgui.domain.secrets.SecretRedactor;
import org.cometgui.provenance.json.CanonicalTimestamp;
import org.cometgui.provenance.json.JsonValue;
import org.cometgui.provenance.json.JsonWriter;

/**
 * The {@code project.json} format, schema version 1: written by the one {@link JsonWriter}, read by
 * the one {@code JsonReader}.
 *
 * <pre>
 * {
 *   "schemaVersion": 1,
 *   "projectId": "project-beta",
 *   "created": "2026-08-28T23:00:00.000Z"
 * }
 * </pre>
 *
 * <p>Members in that order; the document ends with one newline. {@code
 * docs/reference/project_format.rst} is the format's reference.
 */
public final class ProjectJson {

    /** Every member of a version-1 document, in the order they are written. */
    static final List<String> MEMBERS = List.of("schemaVersion", "projectId", "created");

    private ProjectJson() {
        throw new AssertionError("ProjectJson is never instantiated");
    }

    /**
     * Renders a descriptor.
     *
     * @param descriptor the descriptor
     * @return the document
     * @throws NullPointerException if {@code descriptor} is {@code null}
     */
    public static String render(ProjectDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        return JsonWriter.redactingWith(SecretRedactor.patternsOnly())
                .beginObject()
                .name("schemaVersion")
                .value(ProjectDescriptor.SCHEMA_VERSION)
                .name("projectId")
                .value(descriptor.id().value())
                .name("created")
                .value(CanonicalTimestamp.utcMillis(descriptor.created()))
                .endObject()
                .finish();
    }

    /**
     * Reads a descriptor, its schema version first.
     *
     * @param text the document's text
     * @param document the document's path or label, for messages
     * @return the descriptor
     * @throws org.cometgui.domain.project.UnsupportedSchemaVersionException if the document is of
     *     another schema version, before any other member is read
     * @throws InvalidDocumentException if the document is not a version-1 {@code project.json}
     */
    public static ProjectDescriptor parse(String text, String document) {
        Objects.requireNonNull(text, "text");
        DocumentFields fields = new DocumentFields(document);
        JsonValue.JsonObject root = fields.root(text);
        fields.requireVersion(root, ProjectDescriptor.SCHEMA_VERSION);
        fields.requireOnly(root, DocumentFields.ROOT, MEMBERS);
        String id =
                fields.string(fields.member(root, DocumentFields.ROOT, "projectId"), "projectId");
        Instant created =
                fields.timestamp(fields.member(root, DocumentFields.ROOT, "created"), "created");
        ProjectId projectId = fields.rebuilt("projectId", () -> new ProjectId(id));
        return new ProjectDescriptor(projectId, created);
    }
}
