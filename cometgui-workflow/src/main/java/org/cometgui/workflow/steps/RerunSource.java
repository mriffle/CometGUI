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

package org.cometgui.workflow.steps;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.project.ProjectDescriptor;
import org.cometgui.domain.project.ProjectLayout;
import org.cometgui.domain.run.ArchivedFile;
import org.cometgui.domain.run.RunAttempt;
import org.cometgui.domain.run.RunDescriptor;
import org.cometgui.domain.run.RunIdentity;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.provenance.hashing.CachingHashService;
import org.cometgui.provenance.manifest.FileDirection;
import org.cometgui.provenance.manifest.FileRecord;
import org.cometgui.provenance.manifest.ManifestReader;
import org.cometgui.provenance.manifest.ProvenanceManifest;
import org.cometgui.provenance.manifest.ProvenanceStatus;
import org.cometgui.provenance.manifest.ToolRecord;
import org.cometgui.workflow.storage.ProjectStore;
import org.cometgui.workflow.storage.RunStore;

/**
 * The earlier run a compatible-version Percolator rerun reuses, read and checked: its {@code
 * run.json}, its {@code provenance.json}, the Comet binary its search ran, and its merged PIN and
 * archived {@code comet.params} -- each re-hashed with {@link CachingHashService#rehash}, which
 * reads the bytes every time, and held to the SHA-256 the run recorded ({@code R-RUN-02}, P8-14).
 *
 * <p>{@link #inspect} only reads. Every refusal is a {@link RerunRefusedException} naming the run,
 * and the file, its role and both digests where a file is the reason, so that a refused rerun has
 * created nothing.
 *
 * @param layout the run's directory
 * @param descriptor its {@code run.json}
 * @param manifest its {@code provenance.json}, as the latest attempt left it
 * @param manifestFile the manifest as it lies on disk, by its path relative to the run, its size
 *     and its digests -- what the derived run records it reused
 * @param cometSha256 the SHA-256 of the Comet executable its search ran, as its provenance records
 *     every Comet invocation
 * @param mergedPin the merged PIN as {@code merge-pin} recorded it, re-hashed and found unchanged
 * @param parameters the archived {@code comet.params}, re-hashed and found unchanged
 */
record RerunSource(
        RunLayout layout,
        RunDescriptor descriptor,
        ProvenanceManifest manifest,
        ArchivedFile manifestFile,
        String cometSha256,
        FileRecord mergedPin,
        FileHashes parameters) {

    RerunSource {
        Objects.requireNonNull(layout, "layout");
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(manifest, "manifest");
        Objects.requireNonNull(manifestFile, "manifestFile");
        Objects.requireNonNull(cometSha256, "cometSha256");
        Objects.requireNonNull(mergedPin, "mergedPin");
        Objects.requireNonNull(parameters, "parameters");
    }

    /** The run's identity. */
    RunIdentity identity() {
        return descriptor.identity();
    }

    /** The run's name in messages: {@code run run-0001}. */
    String name() {
        return "run " + identity().runId();
    }

    /**
     * Reads and checks a run of the project for a rerun.
     *
     * @param store the project's run store
     * @param project the project
     * @param layout the run's directory
     * @param hashes the one hasher
     * @return the checked run
     * @throws RerunRefusedException if the run cannot be rerun: not a run of this project,
     *     unreadable, itself a rerun, still running, never started, without a provenance record or
     *     a recorded Comet invocation, or with a merged PIN or parameter file that is missing, not
     *     recorded complete, or changed since it was recorded
     * @throws IOException if a file the checks need cannot be read
     */
    static RerunSource inspect(
            RunStore store, ProjectLayout project, RunLayout layout, CachingHashService hashes)
            throws IOException, RerunRefusedException {
        Objects.requireNonNull(store, "store");
        Objects.requireNonNull(project, "project");
        Objects.requireNonNull(layout, "layout");
        Objects.requireNonNull(hashes, "hashes");
        Path parent = layout.root().getParent();
        if (parent == null || !parent.equals(project.runsDirectory())) {
            throw new RerunRefusedException(
                    layout.root()
                            + " is not a run directory of the project "
                            + project.root()
                            + "; a rerun reuses a run of the same project");
        }
        RunDescriptor descriptor;
        try {
            descriptor = store.read(layout);
        } catch (IOException | RuntimeException unreadable) {
            throw new RerunRefusedException(
                    "the run at "
                            + layout.root()
                            + " cannot be rerun: its run.json cannot be read: "
                            + unreadable.getMessage());
        }
        RunIdentity identity = descriptor.identity();
        String name = "run " + identity.runId();
        ProjectDescriptor owner = ProjectStore.read(project);
        if (!owner.id().equals(identity.projectId())) {
            throw new RerunRefusedException(
                    name
                            + " names project "
                            + identity.projectId()
                            + ", but "
                            + project.root()
                            + " is project "
                            + owner.id()
                            + "; a rerun reuses a run of the same project");
        }
        if (identity.derivedFrom().isPresent()) {
            throw new RerunRefusedException(
                    name
                            + " is itself a Percolator rerun of run "
                            + identity.derivedFrom().get().runId()
                            + " and ran no Comet search of its own; rerun Percolator from run "
                            + identity.derivedFrom().get().runId()
                            + ", whose Comet results it reused");
        }
        if (descriptor.attempts().isEmpty()) {
            throw new RerunRefusedException(
                    name
                            + " has never been started, so it has no merged PIN to rerun"
                            + " Percolator from");
        }
        Optional<RunAttempt> running = descriptor.runningAttempt();
        if (running.isPresent()) {
            throw new RerunRefusedException(
                    name
                            + " is still running (attempt "
                            + running.get().number()
                            + "); a rerun reuses only a run that has ended");
        }
        Path manifestPath = layout.provenanceJsonFile();
        if (!Files.isRegularFile(manifestPath)) {
            throw new RerunRefusedException(
                    name
                            + " has no provenance record "
                            + manifestPath
                            + ", so what it produced cannot be checked and nothing of it is"
                            + " reused");
        }
        ProvenanceManifest manifest;
        try {
            manifest = ManifestReader.readFrom(manifestPath);
        } catch (IOException | RuntimeException unreadable) {
            throw new RerunRefusedException(
                    name
                            + "'s provenance record "
                            + manifestPath
                            + " cannot be read, so nothing of the run is reused: "
                            + unreadable.getMessage());
        }
        FileHashes manifestHashes = hashes.rehash(manifestPath);
        ArchivedFile manifestFile =
                new ArchivedFile(
                        RunLayout.provenanceJsonRelativePath(),
                        Files.size(manifestPath),
                        manifestHashes);
        String comet = cometSha256(name, manifest);
        FileRecord mergedPin = mergedPin(name, layout, manifest, hashes);
        FileHashes parameters = parameters(name, layout, identity.parameters(), hashes);
        return new RerunSource(
                layout, descriptor, manifest, manifestFile, comet, mergedPin, parameters);
    }

    /** The one Comet binary every recorded Comet invocation ran. */
    private static String cometSha256(String name, ProvenanceManifest manifest)
            throws RerunRefusedException {
        TreeSet<String> binaries = new TreeSet<>();
        for (ToolRecord tool : manifest.tools()) {
            if (CometWorkflow.TOOL_NAME.equals(tool.name())) {
                binaries.add(tool.hashes().sha256());
            }
        }
        if (binaries.isEmpty()) {
            throw new RerunRefusedException(
                    name
                            + "'s provenance records no Comet invocation, so there is no Comet"
                            + " search to reuse");
        }
        if (binaries.size() > 1) {
            throw new RerunRefusedException(
                    name
                            + "'s provenance records Comet invocations of "
                            + binaries.size()
                            + " different executables ("
                            + String.join(", ", binaries)
                            + "), so which search to reuse is not one answer");
        }
        return binaries.first();
    }

    /** The merged PIN, as recorded complete and re-hashed unchanged. */
    private static FileRecord mergedPin(
            String name, RunLayout layout, ProvenanceManifest manifest, CachingHashService hashes)
            throws IOException, RerunRefusedException {
        Path file = layout.mergedPinFile();
        FileRecord recorded = null;
        for (FileRecord record : manifest.files()) {
            if (record.direction() == FileDirection.OUTPUT
                    && RunDeclarations.MERGED_PIN.equals(record.role())
                    && record.path().equals(file)) {
                recorded = record;
            }
        }
        if (recorded == null) {
            throw new RerunRefusedException(
                    name
                            + " has no merged PIN to rerun Percolator from: its provenance records"
                            + " no "
                            + RunDeclarations.MERGED_PIN
                            + " output "
                            + file
                            + ", so merge-pin never succeeded in it");
        }
        String was = recorded.hashes().sha256();
        if (recorded.status() != ProvenanceStatus.COMPLETED) {
            throw new RerunRefusedException(
                    "the merged PIN (role "
                            + RunDeclarations.MERGED_PIN
                            + ") "
                            + file
                            + " of "
                            + name
                            + " is recorded "
                            + recorded.status().wireName()
                            + " (SHA-256 "
                            + was
                            + "), not complete, so Percolator is not rerun from it");
        }
        if (!Files.isRegularFile(file)) {
            throw new RerunRefusedException(
                    "the merged PIN (role "
                            + RunDeclarations.MERGED_PIN
                            + ") "
                            + file
                            + " of "
                            + name
                            + " no longer exists; "
                            + name
                            + " recorded SHA-256 "
                            + was);
        }
        String now = hashes.rehash(file).sha256();
        if (!now.equals(was)) {
            throw new RerunRefusedException(
                    changed(name, RunDeclarations.MERGED_PIN, file, was, now));
        }
        return recorded;
    }

    /** The archived parameter file, re-hashed against the run's own record of it. */
    private static FileHashes parameters(
            String name, RunLayout layout, ArchivedFile recorded, CachingHashService hashes)
            throws IOException, RerunRefusedException {
        Path file = layout.cometParamsFile();
        String was = recorded.hashes().sha256();
        if (!Files.isRegularFile(file)) {
            throw new RerunRefusedException(
                    "the archived parameter file (role "
                            + RunDeclarations.PARAMS
                            + ") "
                            + file
                            + " of "
                            + name
                            + " no longer exists; "
                            + name
                            + " recorded SHA-256 "
                            + was);
        }
        FileHashes now = hashes.rehash(file);
        if (!now.sha256().equals(was)) {
            throw new RerunRefusedException(
                    changed(name, RunDeclarations.PARAMS, file, was, now.sha256()));
        }
        return now;
    }

    /**
     * The refusal of a reused file whose bytes changed: the file, its role and both digests.
     *
     * @param name the run, as {@code run run-0001}
     * @param role the file's provenance role
     * @param file the file
     * @param recorded the SHA-256 the run recorded
     * @param now the SHA-256 of the file as it is
     * @return the message
     */
    static String changed(String name, String role, Path file, String recorded, String now) {
        return "the file "
                + file
                + " (role "
                + role
                + ") of "
                + name
                + " has SHA-256 "
                + now
                + ", but "
                + name
                + " recorded SHA-256 "
                + recorded
                + "; it has changed since it was recorded, so it is not reused and Percolator is"
                + " not rerun from it (R-RUN-02). Nothing was created.";
    }
}
