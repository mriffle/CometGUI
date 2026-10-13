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

/**
 * The provenance read model: the one place that turns a run directory into what the Provenance
 * section shows, and that writes the two exports (phase 13, {@code P13-2}).
 *
 * <h2>The shape</h2>
 *
 * <ul>
 *   <li>{@link org.cometgui.workflow.provenance.ProvenanceReader} lists a project's run directories
 *       ({@link org.cometgui.workflow.provenance.RunListing}) -- every one, whatever became of its
 *       run -- and loads one run.
 *   <li>{@link org.cometgui.workflow.provenance.LoadedRun} is a loaded run: its sealed record or
 *       the stated reason it has none ({@link org.cometgui.workflow.provenance.RecordState}), its
 *       recovered event log, its {@link org.cometgui.workflow.provenance.ProvenanceView}, and the
 *       actions: export JSON, export RST, verify against disk.
 *   <li>{@link org.cometgui.workflow.provenance.ProvenanceView} holds the seven parts the
 *       specification's <em>Provenance UI</em> names, as immutable records of display text a
 *       view-model binds without logic.
 *   <li>{@link org.cometgui.workflow.provenance.DiskVerification} is what "Verify against disk"
 *       found, one {@link org.cometgui.workflow.provenance.DiskOutcome} per file.
 * </ul>
 *
 * <p>A view-model reaches this package through a port the application implements (the pattern of
 * phase 12's Limelight section): the port constructs one {@code ProvenanceReader} with the
 * session's redactor and the product's hasher, and calls {@code list}, {@code load} and the actions
 * on the background executor. A test reaches a run's record and both exports in two calls: {@code
 * new ProvenanceReader(...).load(runDirectory)}, then {@code exportJson}/{@code exportRst}.
 *
 * <h2>The rules it keeps</h2>
 *
 * <dl>
 *   <dt>One of everything ({@code P13-2}).
 *   <dd>One reader of the record ({@code ManifestReader.readFrom}), of the event log ({@code
 *       ProvenanceEventLogReader.recover}) and of {@code run.json} ({@code RunStore.read}); one
 *       hasher ({@code CachingHashService.rehash}, never served from the cache); one redactor (the
 *       session's {@code SecretRedactor}); one command rendering (the process service's {@code
 *       ProcessRedactor.redactedDisplayCommand}); one parameter parser ({@code CometParamsParser},
 *       for the recorded release); one JSON writer and one RST generator (phase 04's). Nothing here
 *       validates, hashes, redacts, renders or parses on its own.
 *   <dt>The viewer is never a holder ({@code P13-1}).
 *   <dd>Every file is read whole and closed before a method returns. Nothing here keeps a channel,
 *       a stream, a memory map or a watch on a run directory, so the engine's atomic replace of
 *       {@code provenance.json} is never refused because the Provenance section is open.
 *   <dt>The exports are rendered, never copied ({@code P13-3}).
 *   <dd>Both render the one {@code ProvenanceManifest} the load read, through phase 04's writers
 *       with the session's redactor, and are written with {@code AtomicDocumentWriter}. An existing
 *       target is replaced only when the caller says so, and the run's own sealed documents never.
 *   <dt>A run without a sealed record still opens ({@code P13-5}).
 *   <dd>An absent record is "no sealed record", shown from {@code run.json} and {@code events.log};
 *       one that does not parse is "record unreadable", with the reader's message naming the
 *       member; event-log defects are warnings. Never an empty run.
 *   <dt>Verification recomputes from disk ({@code P13-7}, the product half).
 *   <dd>Every listed input, output, log and tool file is hashed again and reported as matching,
 *       differing (both digests shown), missing or unreadable. The phase's gate tests do not use
 *       this to prove the record: they compute their own digests.
 *   <dt>No secret on screen.
 *   <dd>Every display string -- paths, commands, environments, settings, warnings, event payloads,
 *       the archived parameter text -- has passed the session's redactor. A {@code location}
 *       ({@link java.nio.file.Path}) beside a path is for acting on a file and is never shown.
 * </dl>
 *
 * <h2>Where the warnings come from</h2>
 *
 * <p>{@link org.cometgui.workflow.provenance.WarningSource} is the census: the record's run status,
 * partial files, tool warnings (advisories, an unhashable log, and any manifest/probe discrepancy a
 * phase records against a tool), the {@code percolator.advisory.*}, {@code percolator.skipped.*},
 * {@code percolator.not-emitted.*} and {@code percolator.weights-warning} settings; the event log's
 * {@code warning.raised}, failed-step and failed-Limelight events and its defects; and the run
 * directory's own state -- no or an unreadable record, an unreadable {@code run.json} or event log,
 * an archived parameter file that is gone or changed. No JavaFX, and nothing slow is ever done by a
 * constructor here: every blocking call is named as one.
 */
package org.cometgui.workflow.provenance;
