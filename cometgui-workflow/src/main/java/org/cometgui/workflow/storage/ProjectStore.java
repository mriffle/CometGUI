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

import java.io.IOException;
import java.nio.file.Files;
import java.time.Clock;
import java.util.Objects;
import org.cometgui.domain.project.ProjectDescriptor;
import org.cometgui.domain.project.ProjectId;
import org.cometgui.domain.project.ProjectLayout;
import org.cometgui.provenance.io.AtomicDocumentWriter;

/**
 * Creates a project directory and reads its {@code project.json}.
 *
 * <p>The clock is injected ({@code R-PROC-01}), so a test chooses the creation time it then asserts
 * in the hand-typed document.
 *
 * <p>{@code project.json} is written once, by {@link #create}: a directory that already holds one
 * is refused with {@link AlreadyWrittenException} and left untouched. Phases that add mutable
 * project state bump the schema version and add an update path; version 1 has nothing that changes.
 */
public final class ProjectStore {

    private final Clock clock;

    /**
     * Creates the store.
     *
     * @param clock the clock new projects are dated by
     * @throws NullPointerException if {@code clock} is {@code null}
     */
    public ProjectStore(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Creates a project: its directory if missing, {@code project.json} and {@code runs/}.
     *
     * <p>The existence check and the atomic write are two steps. That is safe against everything
     * but a second process creating a project in the same directory in the same instant, which no
     * user action produces; everything that changes a project afterwards happens under {@link
     * ProjectLock}.
     *
     * @param project where the project goes
     * @param id the new project's identifier
     * @return the descriptor, as read back from the file written
     * @throws AlreadyWrittenException if the directory already holds a {@code project.json}
     * @throws IOException if a directory or the file cannot be written
     * @throws NullPointerException if an argument is {@code null}
     */
    public ProjectDescriptor create(ProjectLayout project, ProjectId id) throws IOException {
        Objects.requireNonNull(project, "project");
        Objects.requireNonNull(id, "id");
        Files.createDirectories(project.root());
        if (Files.exists(project.projectFile())) {
            throw new AlreadyWrittenException(
                    project.projectFile(),
                    "this directory already holds a project; project.json is written once");
        }
        Files.createDirectories(project.runsDirectory());
        AtomicDocumentWriter.write(
                project.projectFile(),
                ProjectJson.render(new ProjectDescriptor(id, clock.instant())));
        return read(project);
    }

    /**
     * Reads a project's {@code project.json}.
     *
     * @param project the project
     * @return its descriptor
     * @throws IOException if the file is missing or cannot be read
     * @throws org.cometgui.domain.project.UnsupportedSchemaVersionException if it is of another
     *     schema version
     * @throws InvalidDocumentException if it is not a version-1 {@code project.json}
     */
    public static ProjectDescriptor read(ProjectLayout project) throws IOException {
        Objects.requireNonNull(project, "project");
        return ProjectJson.parse(
                DocumentFields.readText(project.projectFile()), project.projectFile().toString());
    }
}
