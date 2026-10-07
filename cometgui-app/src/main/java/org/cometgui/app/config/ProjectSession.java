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

package org.cometgui.app.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Objects;
import org.cometgui.domain.ports.RunIdSource;
import org.cometgui.domain.project.ProjectId;
import org.cometgui.domain.project.ProjectLayout;
import org.cometgui.workflow.storage.ProjectLock;
import org.cometgui.workflow.storage.ProjectStore;
import org.cometgui.workflow.storage.RunStore;

/**
 * The project this application session runs in, opened when it is first needed and held, locked,
 * until the application stops ({@code R-RUN-05}).
 *
 * <h2>Where a project lives</h2>
 *
 * <p>Phase 08 needs exactly one project per session and offers no way to choose one: by default it
 * is {@code projects/default} under the application data directory ({@link #defaultDirectory}),
 * beside the Tool Manager's cache, so nothing is written next to the scientist's own files ({@code
 * R-CMT-08}). The directory is a constructor argument, so a test (or a later phase's project
 * chooser) names another.
 *
 * <h2>Opened lazily, and why</h2>
 *
 * <p>The project is created (with {@code project.json} and {@code runs/}) and locked the first time
 * the engine needs it -- the first pre-run check that finds a Comet to run -- not when the window
 * opens. A window that cannot run anything (no Comet installed) therefore creates no directory and
 * takes no lock. Once taken, the lock is held for the session, so a second CometGUI on this machine
 * is refused with the owner named; a refusal is not remembered, so the next check tries again and
 * succeeds once the other instance has gone.
 */
public final class ProjectSession implements AutoCloseable {

    /** The project identifier a project created by this session records. */
    public static final String PROJECT_ID = "cometgui-default";

    private final ProjectLayout layout;

    private final Clock clock;

    private final RunIdSource runIds;

    private ProjectLock lock;

    private RunStore store;

    /**
     * A session over a project directory, not yet opened.
     *
     * @param directory the project directory; created when first needed
     * @param clock the clock the project, its lock and its runs are dated by
     * @param runIds the source of run identifiers
     */
    public ProjectSession(Path directory, Clock clock, RunIdSource runIds) {
        this.layout = new ProjectLayout(Objects.requireNonNull(directory, "directory"));
        this.clock = Objects.requireNonNull(clock, "clock");
        this.runIds = Objects.requireNonNull(runIds, "runIds");
    }

    /**
     * The default project directory: {@code projects/default} under the application data directory.
     *
     * @param applicationData the application data directory
     * @return the project directory
     */
    public static Path defaultDirectory(Path applicationData) {
        return Objects.requireNonNull(applicationData, "applicationData")
                .resolve("projects")
                .resolve("default");
    }

    /**
     * The project directory.
     *
     * @return the directory, whether or not it exists yet
     */
    public Path directory() {
        return layout.root();
    }

    /**
     * The project's lock, creating the project and taking the lock the first time.
     *
     * @return the held lock
     * @throws IOException if the project cannot be created or read, or another process holds its
     *     lock ({@code ProjectLockedException}, naming the owner)
     */
    public synchronized ProjectLock lock() throws IOException {
        if (lock == null) {
            if (!Files.exists(layout.projectFile())) {
                new ProjectStore(clock).create(layout, new ProjectId(PROJECT_ID));
            } else {
                ProjectStore.read(layout);
            }
            ProjectLock acquired = ProjectLock.acquire(layout, clock);
            store = new RunStore(layout, clock, runIds);
            lock = acquired;
        }
        return lock;
    }

    /**
     * The project's run store; the project is opened first if it is not.
     *
     * @return the store
     * @throws IOException as {@link #lock()}
     */
    public synchronized RunStore store() throws IOException {
        lock();
        return store;
    }

    /**
     * Whether this session holds the project's lock.
     *
     * @return {@code true} once the project has been opened, until it is closed
     */
    public synchronized boolean isOpen() {
        return lock != null && lock.held();
    }

    /**
     * Releases the lock, if it was taken. Idempotent.
     *
     * @throws IOException if the lock cannot be released
     */
    @Override
    public synchronized void close() throws IOException {
        if (lock != null) {
            ProjectLock held = lock;
            lock = null;
            store = null;
            held.close();
        }
    }
}
