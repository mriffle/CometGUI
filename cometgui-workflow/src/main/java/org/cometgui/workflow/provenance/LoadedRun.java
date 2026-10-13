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

package org.cometgui.workflow.provenance;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.domain.secrets.SecretRedactor;
import org.cometgui.provenance.events.RecoveredEventLog;
import org.cometgui.provenance.hashing.CachingHashService;
import org.cometgui.provenance.io.AtomicDocumentWriter;
import org.cometgui.provenance.manifest.ProvenanceManifest;

/**
 * One run, loaded: its record (or the stated reason it has none), its recovered event log, what the
 * Provenance section shows, and the three actions that act on the record.
 *
 * <p>Holds no file handle. Everything was read whole and closed before {@link
 * ProvenanceReader#load} returned ({@code P13-1}); the actions open what they need and close it
 * again.
 *
 * <h2>The exports ({@code P13-3})</h2>
 *
 * <p>{@link #exportJson} and {@link #exportRst} render <em>this object's</em> {@link
 * ProvenanceManifest} -- the one read from disk at load -- through phase 04's writers, redacting
 * with the session's {@link SecretRedactor} as it is at the moment of the export, and write the
 * result with {@link AtomicDocumentWriter}. Neither copies bytes: a copy would export whatever is
 * on disk, unredacted by today's rules and unchecked by the reader. A run whose record is a
 * byte-for-byte product of the same writers with the same redactor therefore exports two documents
 * identical to the two on disk.
 *
 * <p><strong>An existing file is not replaced unless the caller says so.</strong> An export is
 * written to a place the scientist chose, which may already hold a document -- another run's
 * record, say -- and overwriting a provenance document by accident destroys evidence that cannot be
 * regenerated. So an existing target is refused with {@link FileAlreadyExistsException} unless
 * {@code replaceExisting} is {@code true}, which is what a view-model passes once a "replace?"
 * confirmation has been answered. And <strong>the run's own sealed {@code provenance.json} and
 * {@code provenance.rst} are never a target</strong>, whatever the flag: an export re-rendered
 * under today's redaction must not quietly rewrite the sealed record.
 */
public final class LoadedRun {

    private final RunLayout layout;

    private final RecordState state;

    private final Optional<ProvenanceManifest> manifest;

    private final Optional<String> recordProblem;

    private final RecoveredEventLog events;

    private final ProvenanceView view;

    private final Supplier<SecretRedactor> redactor;

    private final CachingHashService hashes;

    private final ExportRenderer renderer;

    LoadedRun(
            ViewBuilder.Loaded loaded,
            ProvenanceView view,
            Supplier<SecretRedactor> redactor,
            CachingHashService hashes,
            ExportRenderer renderer) {
        this.layout = loaded.layout();
        this.state = loaded.state();
        this.manifest = loaded.manifest();
        this.recordProblem = loaded.recordProblem();
        this.events = loaded.events();
        this.view = Objects.requireNonNull(view, "view");
        this.redactor = redactor;
        this.hashes = hashes;
        this.renderer = renderer;
    }

    /**
     * The run's directory.
     *
     * @return the layout the run was loaded from
     */
    public RunLayout layout() {
        return layout;
    }

    /**
     * Whether the run has a sealed record.
     *
     * @return the state the load found
     */
    public RecordState state() {
        return state;
    }

    /**
     * The sealed record, exactly as it was read: the one object both exports render.
     *
     * @return the record, or empty when the run has none or it could not be read
     */
    public Optional<ProvenanceManifest> manifest() {
        return manifest;
    }

    /**
     * Why the record could not be read, when it could not: the reader's message, naming the member
     * and the rule, never a value out of the document.
     *
     * @return the message, redacted, or empty
     */
    public Optional<String> recordProblem() {
        return recordProblem;
    }

    /**
     * The run's event log as it was recovered, with its defects.
     *
     * @return the recovered log; empty and intact when the run has none
     */
    public RecoveredEventLog events() {
        return events;
    }

    /**
     * What the Provenance section shows.
     *
     * @return the view
     */
    public ProvenanceView view() {
        return view;
    }

    /**
     * Whether there is a record to export.
     *
     * @return {@code true} only for a run with a sealed, readable record
     */
    public boolean canExport() {
        return manifest.isPresent();
    }

    /**
     * Exports the record as {@code provenance.json}, rendered again from the loaded model.
     *
     * <p>Blocking; the caller runs it off the JavaFX thread.
     *
     * @param target where to write the document; its directory must exist
     * @param replaceExisting whether an existing file at {@code target} may be replaced
     * @return {@code target}
     * @throws NothingToExportException if the run has no sealed, readable record
     * @throws FileAlreadyExistsException if {@code target} exists and {@code replaceExisting} is
     *     {@code false}, or {@code target} is one of the run's own sealed documents
     * @throws IOException if the document cannot be written; the target is then as it was
     */
    public Path exportJson(Path target, boolean replaceExisting) throws IOException {
        ProvenanceManifest record = sealed();
        return write(target, replaceExisting, renderer.json(redactor.get(), record));
    }

    /**
     * Exports the record as the {@code provenance.rst} report, rendered again from the loaded model
     * -- the same object {@link #exportJson} renders.
     *
     * <p>Blocking; the caller runs it off the JavaFX thread.
     *
     * @param target where to write the report; its directory must exist
     * @param replaceExisting whether an existing file at {@code target} may be replaced
     * @return {@code target}
     * @throws NothingToExportException if the run has no sealed, readable record
     * @throws FileAlreadyExistsException if {@code target} exists and {@code replaceExisting} is
     *     {@code false}, or {@code target} is one of the run's own sealed documents
     * @throws IOException if the report cannot be written; the target is then as it was
     */
    public Path exportRst(Path target, boolean replaceExisting) throws IOException {
        ProvenanceManifest record = sealed();
        return write(target, replaceExisting, renderer.rst(redactor.get(), record));
    }

    /**
     * Hashes every input, output, archived log and tool executable the record lists again, with the
     * cache bypassed, and compares each with its record ({@code P13-7}, the product half).
     *
     * <p>Blocking and proportional to the bytes listed; the caller runs it off the JavaFX thread.
     * It stops between files when {@code cancelled} answers {@code true} or the thread is
     * interrupted, and says so with {@link DiskVerification#complete()}.
     *
     * @param cancelled asked before each file whether to stop
     * @return what was found, per file
     * @throws NothingToExportException if the run has no sealed, readable record to verify
     */
    public DiskVerification verifyAgainstDisk(BooleanSupplier cancelled)
            throws NothingToExportException {
        Objects.requireNonNull(cancelled, "cancelled");
        return new DiskVerifier(hashes, new DisplayText(redactor.get()))
                .verify(sealed(), cancelled);
    }

    private ProvenanceManifest sealed() throws NothingToExportException {
        if (manifest.isPresent()) {
            return manifest.get();
        }
        String run = String.valueOf(layout.root().getFileName());
        throw new NothingToExportException(
                state == RecordState.RECORD_UNREADABLE
                        ? "run "
                                + run
                                + " has a provenance record that could not be read, so there is"
                                + " nothing to export or verify: "
                                + recordProblem.orElse("no reason was given")
                        : "run "
                                + run
                                + " has no sealed provenance record (its provenance.json does not"
                                + " exist), so there is nothing to export or verify");
    }

    private Path write(Path target, boolean replaceExisting, String document) throws IOException {
        Objects.requireNonNull(target, "target");
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            for (Path own : List.of(layout.provenanceJsonFile(), layout.provenanceRstFile())) {
                if (Files.exists(own) && Files.isSameFile(target, own)) {
                    throw new FileAlreadyExistsException(
                            target.toString(),
                            null,
                            "this is the run's own sealed record, which an export never replaces");
                }
            }
            if (!replaceExisting) {
                throw new FileAlreadyExistsException(
                        target.toString(),
                        null,
                        "a file already exists here; confirm replacing it to export over it");
            }
        }
        AtomicDocumentWriter.write(target, document);
        return target;
    }
}
