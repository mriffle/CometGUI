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
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import org.cometgui.domain.ports.RunIdSource;
import org.cometgui.domain.project.ProjectDescriptor;
import org.cometgui.domain.project.ProjectLayout;
import org.cometgui.domain.run.RunDescriptor;
import org.cometgui.domain.run.RunId;
import org.cometgui.domain.run.RunIdentity;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.provenance.io.AtomicDocumentWriter;

/**
 * Creates run directories and reads and writes {@code run.json} so that a run can never be changed
 * after the fact ({@code R-RUN-03}..{@code R-RUN-06}).
 *
 * <h2>The life of a run's record</h2>
 *
 * <ol>
 *   <li>{@link #reserve} takes a run id from the injected {@link RunIdSource} and a time from the
 *       injected {@link Clock} ({@code R-PROC-01}) and creates the run's directories (design
 *       decision P8-3). The run directory is created with {@link Files#createDirectory}, which
 *       fails if it exists, so a repeated id can never write into an earlier run.
 *   <li>The caller writes the canonical parameter file with {@code CanonicalParamsWriter.writeOnce}
 *       into {@link RunLayout#cometParamsFile()} -- which refuses an existing file -- and hashes
 *       the inputs with the one {@code HashService}.
 *   <li>{@link #record} writes {@code run.json} with the identity and no attempts. Once: a second
 *       call is refused with {@link AlreadyWrittenException} and the file is not touched.
 *   <li>{@link #update} replaces {@code run.json} atomically with a successor -- an attempt
 *       started, a step's fingerprint recorded, an attempt ended, a retry -- after checking against
 *       the file <em>on disk</em> that the successor changes nothing {@link
 *       RunDescriptor#requireSuccessor} forbids. A refused update leaves the file byte-identical.
 * </ol>
 *
 * <p>Every change takes the {@link ProjectLock} as an argument and requires it held on this
 * project; reading does not, because every write is atomic.
 */
public final class RunStore {

    private final ProjectLayout project;

    private final Clock clock;

    private final RunIdSource runIds;

    /**
     * Creates the store for one project.
     *
     * @param project the project
     * @param clock the clock runs are dated by
     * @param runIds the source of run identifiers
     * @throws NullPointerException if an argument is {@code null}
     */
    public RunStore(ProjectLayout project, Clock clock, RunIdSource runIds) {
        this.project = Objects.requireNonNull(project, "project");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.runIds = Objects.requireNonNull(runIds, "runIds");
    }

    /**
     * Reserves a new run: fixes its id and creation time and creates its directories.
     *
     * @param lock the project's lock, held
     * @return the reserved run
     * @throws IOException if the project has no {@code runs/} directory, the run directory already
     *     exists, or a directory cannot be created
     * @throws IllegalStateException if the lock is not held on this project
     */
    public ReservedRun reserve(ProjectLock lock) throws IOException {
        Objects.requireNonNull(lock, "lock").requireHeldFor(project);
        if (!Files.isDirectory(project.runsDirectory())) {
            throw new NoSuchFileException(
                    project.runsDirectory().toString(), null, "not a CometGUI project: no runs/");
        }
        RunId runId = Objects.requireNonNull(runIds.newRunId(), "the run-id source returned null");
        Instant created = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        RunLayout layout = RunLayout.of(project, created, runId);
        Files.createDirectory(layout.root());
        for (Path directory : layout.directories().subList(1, layout.directories().size())) {
            Files.createDirectory(directory);
        }
        return new ReservedRun(runId, created, layout);
    }

    /**
     * Writes a reserved run's {@code run.json}, once.
     *
     * @param lock the project's lock, held
     * @param identity the run's identity; its id and creation time are those {@link #reserve}
     *     returned
     * @return the record, as read back from the file written
     * @throws AlreadyWrittenException if the run already has a {@code run.json}; it is not touched
     * @throws NoSuchFileException if the run was never reserved, or its parameter file is missing
     * @throws IllegalArgumentException if the identity names another project, or its parameter
     *     file's recorded size is not the size on disk
     * @throws IOException if the file cannot be written
     * @throws IllegalStateException if the lock is not held on this project
     */
    public RunDescriptor record(ProjectLock lock, RunIdentity identity) throws IOException {
        Objects.requireNonNull(lock, "lock").requireHeldFor(project);
        Objects.requireNonNull(identity, "identity");
        RunLayout layout = layoutOf(identity);
        if (!Files.isDirectory(layout.root())) {
            throw new NoSuchFileException(
                    layout.root().toString(), null, "the run was not reserved in this project");
        }
        if (Files.exists(layout.runFile())) {
            throw new AlreadyWrittenException(
                    layout.runFile(),
                    "run "
                            + identity.runId()
                            + " is already recorded; a run's identity is written once");
        }
        ProjectDescriptor owner = ProjectStore.read(project);
        if (!owner.id().equals(identity.projectId())) {
            throw new IllegalArgumentException(
                    "run "
                            + identity.runId()
                            + " names project "
                            + identity.projectId()
                            + ", but "
                            + project.root()
                            + " is project "
                            + owner.id());
        }
        Path parameters = layout.root().resolve(identity.parameters().path());
        if (!Files.isRegularFile(parameters)) {
            throw new NoSuchFileException(
                    parameters.toString(), null, "the recorded parameter file is not in the run");
        }
        long onDisk = Files.size(parameters);
        if (onDisk != identity.parameters().size()) {
            throw new IllegalArgumentException(
                    "the parameter file "
                            + parameters
                            + " is "
                            + onDisk
                            + " bytes, but the run would record "
                            + identity.parameters().size());
        }
        AtomicDocumentWriter.write(layout.runFile(), RunJson.render(RunDescriptor.of(identity)));
        return read(layout);
    }

    /**
     * Reads a run's {@code run.json}, and requires it to belong to its directory.
     *
     * @param layout the run
     * @return its record
     * @throws IOException if the file is missing or cannot be read
     * @throws org.cometgui.domain.project.UnsupportedSchemaVersionException if it is of another
     *     schema version
     * @throws InvalidDocumentException if it is not a version-1 {@code run.json}, or names a run
     *     whose directory is not this one
     */
    public RunDescriptor read(RunLayout layout) throws IOException {
        Objects.requireNonNull(layout, "layout");
        Path file = layout.runFile();
        RunDescriptor descriptor = RunJson.parse(DocumentFields.readText(file), file.toString());
        RunIdentity identity = descriptor.identity();
        String expected = RunLayout.directoryName(identity.created(), identity.runId());
        Path name = layout.root().getFileName();
        if (name == null || !expected.equals(name.toString())) {
            throw new InvalidDocumentException(
                    file.toString(),
                    "runId",
                    file
                            + " is not valid: \"runId\" and \"created\" do not name the directory"
                            + " the file is in");
        }
        return descriptor;
    }

    /**
     * Replaces a run's {@code run.json} with a permitted successor, atomically.
     *
     * @param lock the project's lock, held
     * @param next the successor
     * @return {@code next}, as read back from the file written
     * @throws org.cometgui.domain.run.RunImmutabilityException if {@code next} changes what the
     *     run's record on disk makes immutable; the file is not touched
     * @throws IOException if the file cannot be read or written
     * @throws IllegalStateException if the lock is not held on this project
     */
    public RunDescriptor update(ProjectLock lock, RunDescriptor next) throws IOException {
        Objects.requireNonNull(lock, "lock").requireHeldFor(project);
        Objects.requireNonNull(next, "next");
        RunLayout layout = layoutOf(next.identity());
        RunDescriptor current = read(layout);
        current.requireSuccessor(next);
        AtomicDocumentWriter.write(layout.runFile(), RunJson.render(next));
        return read(layout);
    }

    /**
     * The layout of a run of this project.
     *
     * @param identity the run's identity
     * @return its directory's layout
     */
    public RunLayout layoutOf(RunIdentity identity) {
        Objects.requireNonNull(identity, "identity");
        return RunLayout.of(project, identity.created(), identity.runId());
    }
}
