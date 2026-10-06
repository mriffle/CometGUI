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

package org.cometgui.domain.project;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * What {@code project.json} says about a project: which project it is and when it was created.
 *
 * <p><strong>Deliberately small.</strong> Phase 08 needs a project to have an identity that its
 * runs can name, and a schema version that a later CometGUI can refuse. Everything else the
 * specification's <em>Project model</em> calls "mutable user intent" -- the prospective parameter
 * configuration, the selected inputs, the chosen tools, presets -- is added by the phases that
 * build those editors, each as a schema-version bump with a migration from version 1 registered in
 * {@link SchemaVersionPolicy}. {@code docs/reference/project_format.rst} lists them.
 *
 * <p><strong>Millisecond precision.</strong> {@code created} is truncated to milliseconds on
 * construction, because that is the precision {@code project.json} records; without it, a
 * descriptor written and read back would not equal the one written.
 *
 * @param id the project's identifier
 * @param created when the project was created
 */
public record ProjectDescriptor(ProjectId id, Instant created) {

    /** The {@code project.json} format this build reads and writes. */
    public static final int SCHEMA_VERSION = 1;

    /**
     * Validates presence and truncates {@code created} to milliseconds.
     *
     * @throws NullPointerException naming a component that is {@code null}
     */
    public ProjectDescriptor {
        Objects.requireNonNull(id, "id");
        created = Objects.requireNonNull(created, "created").truncatedTo(ChronoUnit.MILLIS);
    }
}
