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

package org.cometgui.workflow.engine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.run.RunDescriptor;
import org.cometgui.provenance.hashing.CachingHashService;
import org.cometgui.provenance.manifest.FileRecord;
import org.cometgui.provenance.manifest.ManifestReader;
import org.cometgui.provenance.manifest.ProvenanceManifest;
import org.cometgui.provenance.manifest.ProvenanceStatus;
import org.cometgui.provenance.manifest.ToolRecord;
import org.cometgui.workflow.state.EngineStep;
import org.cometgui.workflow.state.RerunPreview;
import org.cometgui.workflow.state.StepFingerprint;

/**
 * Decides whether recorded results may be reused, by re-reading them ({@code R-RUN-02}, P8-14).
 *
 * <p>The rerun preview says which steps' fingerprints still match; that is a statement about the
 * <em>inputs</em> as the caller describes them. This class checks the <em>files</em>: for every
 * step the preview would reuse, every file the step declares is looked up in the run's recorded
 * {@code provenance.json}, must be recorded there as complete, must still exist, and is re-hashed
 * with {@link CachingHashService#rehash} -- which reads the bytes on every call and never serves a
 * cached digest -- and compared with the recorded SHA-256. Every invocation the step declares must
 * be recorded as completed. Anything else refuses the reuse of that step, by name.
 *
 * <p>Only the latest manifest is consulted, because it is the record of the latest attempt: a step
 * that succeeded in an early attempt and was then re-executed and failed has its outputs recorded
 * as partial, or not at all, in the latest one, and is refused.
 */
final class ReuseValidator {

    private ReuseValidator() {}

    static ReuseCheck check(RunRequest request, CachingHashService hashes) throws IOException {
        RunDescriptor descriptor = request.store().read(request.layout());
        Map<EngineStep, StepFingerprint> recorded =
                RecordedFingerprints.fromRecorded(descriptor.succeededFingerprints());
        RerunPreview preview =
                RerunPreview.compute(request.plan(), request.inputs(), recorded, request.forced());
        List<ReuseMismatch> mismatches = new ArrayList<>();
        Map<EngineStep, List<FileRecord>> verified = new EnumMap<>(EngineStep.class);
        Map<EngineStep, List<ToolRecord>> carried = new EnumMap<>(EngineStep.class);
        if (!preview.reused().isEmpty()) {
            Path manifestFile = request.layout().provenanceJsonFile();
            Optional<ProvenanceManifest> manifest = readManifest(manifestFile);
            for (EngineStep step : preview.reused()) {
                if (manifest.isEmpty()) {
                    mismatches.add(
                            new ReuseMismatch(
                                    step,
                                    ReuseMismatch.Kind.NO_MANIFEST,
                                    manifestFile.toString(),
                                    "",
                                    Optional.empty(),
                                    Optional.empty()));
                } else {
                    StepDeclaration declaration = request.actions().get(step).declaration();
                    verified.put(
                            step,
                            verifyFiles(step, declaration, manifest.get(), hashes, mismatches));
                    carried.put(step, carryTools(step, declaration, manifest.get(), mismatches));
                }
            }
        }
        if (mismatches.isEmpty()) {
            return new ReuseCheck(preview, List.of(), Optional.empty(), verified, carried);
        }
        Set<EngineStep> forced = EnumSet.noneOf(EngineStep.class);
        forced.addAll(request.forced());
        for (ReuseMismatch mismatch : mismatches) {
            forced.add(mismatch.step());
        }
        RerunPreview offered =
                RerunPreview.compute(request.plan(), request.inputs(), recorded, forced);
        return new ReuseCheck(preview, mismatches, Optional.of(offered), Map.of(), Map.of());
    }

    private static Optional<ProvenanceManifest> readManifest(Path file) {
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(ManifestReader.readFrom(file));
        } catch (IOException | RuntimeException unreadable) {
            return Optional.empty();
        }
    }

    private static List<FileRecord> verifyFiles(
            EngineStep step,
            StepDeclaration declaration,
            ProvenanceManifest manifest,
            CachingHashService hashes,
            List<ReuseMismatch> mismatches)
            throws IOException {
        List<FileRecord> verified = new ArrayList<>();
        for (DeclaredFile file : declaration.files()) {
            Optional<FileRecord> recorded = find(manifest, file);
            if (recorded.isEmpty()) {
                mismatches.add(mismatch(step, ReuseMismatch.Kind.NOT_RECORDED, file, null, null));
            } else if (recorded.get().status() != ProvenanceStatus.COMPLETED) {
                mismatches.add(
                        mismatch(
                                step,
                                ReuseMismatch.Kind.NOT_COMPLETED,
                                file,
                                recorded.get().hashes().sha256(),
                                null));
            } else if (!Files.isRegularFile(file.path())) {
                mismatches.add(
                        mismatch(
                                step,
                                ReuseMismatch.Kind.MISSING,
                                file,
                                recorded.get().hashes().sha256(),
                                null));
            } else {
                FileHashes now = hashes.rehash(file.path());
                String was = recorded.get().hashes().sha256();
                if (now.sha256().equals(was)) {
                    verified.add(FileFacts.record(file, now, ProvenanceStatus.COMPLETED));
                } else {
                    mismatches.add(
                            mismatch(step, ReuseMismatch.Kind.CHANGED, file, was, now.sha256()));
                }
            }
        }
        return verified;
    }

    private static List<ToolRecord> carryTools(
            EngineStep step,
            StepDeclaration declaration,
            ProvenanceManifest manifest,
            List<ReuseMismatch> mismatches) {
        List<ToolRecord> carried = new ArrayList<>();
        for (String id : declaration.invocationIds()) {
            Optional<ToolRecord> completed = Optional.empty();
            for (ToolRecord tool : manifest.tools()) {
                if (tool.stageId().equals(Optional.of(id))
                        && tool.execution().status() == ProvenanceStatus.COMPLETED) {
                    completed = Optional.of(tool);
                }
            }
            if (completed.isPresent()) {
                carried.add(completed.get());
            } else {
                mismatches.add(
                        new ReuseMismatch(
                                step,
                                ReuseMismatch.Kind.INVOCATION_NOT_RECORDED,
                                id,
                                "",
                                Optional.empty(),
                                Optional.empty()));
            }
        }
        return carried;
    }

    private static Optional<FileRecord> find(ProvenanceManifest manifest, DeclaredFile file) {
        for (FileRecord record : manifest.files()) {
            if (record.direction() == file.direction() && record.path().equals(file.path())) {
                return Optional.of(record);
            }
        }
        return Optional.empty();
    }

    private static ReuseMismatch mismatch(
            EngineStep step,
            ReuseMismatch.Kind kind,
            DeclaredFile file,
            String recordedSha256,
            String currentSha256) {
        return new ReuseMismatch(
                step,
                kind,
                file.path().toString(),
                FileFacts.roleOf(file),
                Optional.ofNullable(recordedSha256),
                Optional.ofNullable(currentSha256));
    }
}
