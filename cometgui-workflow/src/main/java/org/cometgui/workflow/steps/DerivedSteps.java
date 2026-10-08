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
import java.util.List;
import java.util.Objects;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.run.ArchivedFile;
import org.cometgui.domain.run.RunIdentity;
import org.cometgui.workflow.engine.DeclaredFile;
import org.cometgui.workflow.engine.StepAction;
import org.cometgui.workflow.engine.StepContext;
import org.cometgui.workflow.engine.StepDeclaration;
import org.cometgui.workflow.engine.StepFailedException;

/**
 * The two steps of a derived run that are its own: validating it and finalising its provenance. The
 * Percolator steps are {@link PercolatorSteps}' -- the same three a search with Percolator runs --
 * and the Comet steps are not run at all.
 *
 * <p>Each re-checks inside the run what {@link PercolatorRerun#prepare} checked before it, because
 * an attempt -- a retry hours later included -- must not rely on what was true when the run was
 * made: the run's copies of the merged PIN and of {@code comet.params} are re-hashed against the
 * digests its {@code run.json} recorded when they were copied ({@code R-RUN-02}).
 */
final class DerivedSteps {

    private DerivedSteps() {}

    /**
     * {@code validate-configuration}: the Percolator half's own check (the executable, its digest,
     * the command its capabilities allow) and both copies re-hashed, in {@code VALIDATING}, so a
     * derived run that cannot run fails before anything is launched.
     */
    static final class ValidateDerived implements StepAction {

        private final DerivedRun run;

        ValidateDerived(DerivedRun run) {
            this.run = Objects.requireNonNull(run, "run");
        }

        @Override
        public StepDeclaration declaration() {
            return new StepDeclaration(
                    List.of(
                            DeclaredFile.input(
                                    RunDeclarations.PARAMS, run.layout().cometParamsFile())),
                    List.of());
        }

        @Override
        public boolean validates() {
            return true;
        }

        @Override
        public void validate(StepContext context) throws StepFailedException, IOException {
            List<String> problems =
                    PreRunChecks.percolatorProblems(
                            run.percolator().hashes(), run.project(), run.percolator().choice());
            if (!problems.isEmpty()) {
                throw new StepFailedException(
                        "the rerun cannot start:\n- " + String.join("\n- ", problems));
            }
            verifyCopies(run);
        }

        @Override
        public void execute(StepContext context) {
            // Everything is in validate(), which the engine runs in VALIDATING.
        }
    }

    /**
     * {@code finalise-provenance}: both copies re-hashed once more, and their digests recorded on
     * the step's {@code stage.finished} event.
     *
     * <p>It is ordered only by the planned steps it reads from, and in a derived run none is
     * planned ({@code merge-pin} is provided; {@code finalise-results} is Phase 10's), so it runs
     * alongside the Percolator steps rather than after them -- the same residue a search with
     * Percolator has (Phase 09 unit 5). It only reads; {@code provenance.json} itself is written by
     * the engine when the attempt ends, after every step.
     */
    static final class FinaliseDerived implements StepAction {

        private final DerivedRun run;

        FinaliseDerived(DerivedRun run) {
            this.run = Objects.requireNonNull(run, "run");
        }

        @Override
        public StepDeclaration declaration() {
            return StepDeclaration.NOTHING;
        }

        @Override
        public void execute(StepContext context) throws StepFailedException, IOException {
            Copies verified = verifyCopies(run);
            context.addDetail("pin.merged-sha256", verified.mergedPin().sha256());
            context.addDetail("comet.params-sha256", verified.parameters().sha256());
        }
    }

    /**
     * Re-hashes the run's merged PIN and {@code comet.params} -- never from the cache -- and
     * requires each to have the digest and size {@code run.json} recorded when it was copied.
     *
     * @param run the derived run
     * @return both files' digests, as re-hashed
     * @throws StepFailedException naming the file, its role and both digests, if either differs
     * @throws IOException if either cannot be read
     */
    static Copies verifyCopies(DerivedRun run) throws StepFailedException, IOException {
        RunIdentity identity = run.identity();
        FileHashes pin =
                verify(
                        run,
                        run.layout().mergedPinFile(),
                        RunDeclarations.MERGED_PIN,
                        run.source().mergedPin());
        FileHashes parameters =
                verify(
                        run,
                        run.layout().cometParamsFile(),
                        RunDeclarations.PARAMS,
                        identity.parameters());
        return new Copies(pin, parameters);
    }

    /**
     * The digests of a derived run's two copies, each re-hashed and equal to its record.
     *
     * @param mergedPin the merged PIN's
     * @param parameters the archived parameter file's
     */
    record Copies(FileHashes mergedPin, FileHashes parameters) {}

    private static FileHashes verify(DerivedRun run, Path file, String role, ArchivedFile recorded)
            throws StepFailedException, IOException {
        String name = "run " + run.identity().runId();
        String source = "run " + run.source().runId();
        if (!Files.isRegularFile(file)) {
            throw new StepFailedException(
                    "the file "
                            + file
                            + " (role "
                            + role
                            + ") of "
                            + name
                            + ", copied from "
                            + source
                            + ", no longer exists; "
                            + name
                            + " recorded SHA-256 "
                            + recorded.hashes().sha256());
        }
        FileHashes now = run.percolator().hashes().rehash(file);
        long size = Files.size(file);
        if (!now.sha256().equals(recorded.hashes().sha256()) || size != recorded.size()) {
            throw new StepFailedException(
                    "the file "
                            + file
                            + " (role "
                            + role
                            + ") of "
                            + name
                            + " has SHA-256 "
                            + now.sha256()
                            + " ("
                            + size
                            + " bytes), but "
                            + name
                            + " recorded SHA-256 "
                            + recorded.hashes().sha256()
                            + " ("
                            + recorded.size()
                            + " bytes) when it was copied from "
                            + source
                            + "; a run's inputs are fixed when it is created, so this run cannot"
                            + " continue");
        }
        return now;
    }
}
