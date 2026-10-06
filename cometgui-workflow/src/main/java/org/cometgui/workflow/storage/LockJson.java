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
import org.cometgui.domain.project.LockOwner;
import org.cometgui.domain.secrets.SecretRedactor;
import org.cometgui.provenance.json.CanonicalTimestamp;
import org.cometgui.provenance.json.JsonValue;
import org.cometgui.provenance.json.JsonWriter;

/**
 * The {@code project.lock} record, schema version 1.
 *
 * <pre>
 * {
 *   "schemaVersion": 1,
 *   "pid": 4242,
 *   "host": "lab-pc",
 *   "started": "2026-10-06T09:00:00.000Z"
 * }
 * </pre>
 *
 * <p>An empty lock file is not a malformed record: it is a lock that was released ({@link
 * ProjectLock#close()} empties the file) or never written, and {@link ProjectLock} reads it as "no
 * owner". Anything else must parse.
 */
final class LockJson {

    /** The lock record's format version. */
    static final int SCHEMA_VERSION = 1;

    /** Every member, in the order written. */
    static final List<String> MEMBERS = List.of("schemaVersion", "pid", "host", "started");

    private LockJson() {
        throw new AssertionError("LockJson is never instantiated");
    }

    /**
     * Renders an owner.
     *
     * @param owner the owner
     * @return the record
     */
    static String render(LockOwner owner) {
        Objects.requireNonNull(owner, "owner");
        return JsonWriter.redactingWith(SecretRedactor.patternsOnly())
                .beginObject()
                .name("schemaVersion")
                .value(SCHEMA_VERSION)
                .name("pid")
                .value(owner.pid())
                .name("host")
                .value(owner.host())
                .name("started")
                .value(CanonicalTimestamp.utcMillis(owner.started()))
                .endObject()
                .finish();
    }

    /**
     * Reads an owner, schema version first.
     *
     * @param text the record's text
     * @param document the lock file's path, for messages
     * @return the owner
     */
    static LockOwner parse(String text, String document) {
        DocumentFields fields = new DocumentFields(document);
        JsonValue.JsonObject root = fields.root(text);
        fields.requireVersion(root, SCHEMA_VERSION);
        fields.requireOnly(root, DocumentFields.ROOT, MEMBERS);
        long pid = fields.integer(fields.member(root, DocumentFields.ROOT, "pid"), "pid");
        String host = fields.string(fields.member(root, DocumentFields.ROOT, "host"), "host");
        Instant started =
                fields.timestamp(fields.member(root, DocumentFields.ROOT, "started"), "started");
        if (pid <= 0) {
            throw fields.invalid("pid", "must be a positive process id");
        }
        return fields.rebuilt("host", () -> new LockOwner(pid, host, started));
    }
}
