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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.run.RecordedInput;
import org.cometgui.domain.run.SpectrumInput;
import org.cometgui.workflow.engine.StepAction;
import org.cometgui.workflow.engine.StepContext;
import org.cometgui.workflow.engine.StepDeclaration;
import org.cometgui.workflow.engine.StepFailedException;

/**
 * The steps that prepare a Comet search and decide whether it may start: validating the
 * configuration, resolving Comet, verifying the archived parameter file and hashing the inputs.
 * Each re-checks inside the run something the caller checked before it, because an attempt -- a
 * retry hours later included -- must not rely on what was true when the run was prepared.
 */
final class PreparationSteps {

    private PreparationSteps() {}

    /**
     * {@code validate-configuration}: the pre-run check again, in {@code VALIDATING}, so a blocked
     * configuration fails before anything is launched.
     */
    static final class ValidateConfiguration implements StepAction {

        private final CometRun run;

        ValidateConfiguration(CometRun run) {
            this.run = Objects.requireNonNull(run, "run");
        }

        @Override
        public StepDeclaration declaration() {
            return StepDeclaration.NOTHING;
        }

        @Override
        public boolean validates() {
            return true;
        }

        @Override
        public void validate(StepContext context) throws StepFailedException {
            PreRunReport report =
                    run.checks()
                            .check(
                                    run.project(),
                                    run.model(),
                                    run.spectra(),
                                    run.comet(),
                                    run.identity().indexMode())
                            .report();
            if (report.blocked()) {
                throw new StepFailedException(report.message());
            }
        }

        @Override
        public void execute(StepContext context) {
            // Everything is in validate(), which the engine runs in VALIDATING.
        }
    }

    /**
     * {@code resolve-comet}: the selected executable re-hashed -- never from the cache -- and held
     * to the SHA-256 it was selected at.
     */
    static final class ResolveComet implements StepAction {

        private final CometRun run;

        ResolveComet(CometRun run) {
            this.run = Objects.requireNonNull(run, "run");
        }

        @Override
        public StepDeclaration declaration() {
            return StepDeclaration.NOTHING;
        }

        @Override
        public void execute(StepContext context) throws StepFailedException, IOException {
            Path executable = run.comet().executable();
            if (!Files.isRegularFile(executable) || !Files.isExecutable(executable)) {
                throw new StepFailedException(
                        "the selected Comet executable "
                                + executable
                                + " does not exist or cannot be executed");
            }
            String now = run.hashes().rehash(executable).sha256();
            if (!now.equals(run.comet().sha256())) {
                throw new StepFailedException(
                        "the Comet executable "
                                + executable
                                + " has SHA-256 "
                                + now
                                + ", but Comet "
                                + run.comet().release().text()
                                + " was selected at "
                                + run.comet().sha256()
                                + "; it was replaced after it was selected, and is not run");
            }
        }
    }

    /**
     * {@code serialise-comet-params}: the file {@code CanonicalParamsWriter.writeOnce} wrote before
     * the run was recorded, re-hashed against the digest the run recorded. The writer's plain
     * {@code CREATE_NEW} write is not atomic, so a file truncated or changed since is caught here,
     * before Comet reads it ({@code R-PARAM-12}).
     */
    static final class SerialiseParams implements StepAction {

        private final CometRun run;

        SerialiseParams(CometRun run) {
            this.run = Objects.requireNonNull(run, "run");
        }

        @Override
        public StepDeclaration declaration() {
            return RunDeclarations.serialise(run);
        }

        @Override
        public void execute(StepContext context) throws StepFailedException, IOException {
            verifyArchivedParameters(run);
        }
    }

    /**
     * Re-hashes the archived {@code comet.params} and requires the digest and size the run recorded
     * when it was written.
     *
     * @return the digests, equal to the recorded ones
     */
    static FileHashes verifyArchivedParameters(CometRun run)
            throws StepFailedException, IOException {
        Path file = run.layout().cometParamsFile();
        if (!Files.isRegularFile(file)) {
            throw new StepFailedException(
                    "the run's archived parameter file " + file + " no longer exists");
        }
        FileHashes now = run.hashes().rehash(file);
        long size = Files.size(file);
        String recorded = run.identity().parameters().hashes().sha256();
        if (!now.sha256().equals(recorded) || size != run.identity().parameters().size()) {
            throw new StepFailedException(
                    "the run's archived parameter file "
                            + file
                            + " has SHA-256 "
                            + now.sha256()
                            + " ("
                            + size
                            + " bytes), but the run recorded "
                            + recorded
                            + " ("
                            + run.identity().parameters().size()
                            + " bytes) when it was written; a run's parameters are written once,"
                            + " so this run cannot continue");
        }
        return now;
    }

    /**
     * {@code hash-inputs}: every spectrum file and the database hashed by the one hasher and held
     * to the hashes the run recorded. A run's inputs are fixed when it is created ({@code
     * R-RUN-06}), so a changed input fails the step, naming the file and both digests.
     */
    static final class HashInputs implements StepAction {

        private final CometRun run;

        HashInputs(CometRun run) {
            this.run = Objects.requireNonNull(run, "run");
        }

        @Override
        public StepDeclaration declaration() {
            return RunDeclarations.hashInputs(run);
        }

        @Override
        public void execute(StepContext context) throws StepFailedException, IOException {
            List<String> changed = new ArrayList<>();
            for (SpectrumInput spectrum : run.identity().spectra()) {
                requireUnchanged(
                        context, "spectrum file " + spectrum.position(), spectrum.file(), changed);
            }
            requireUnchanged(context, "database", run.identity().fasta(), changed);
            if (!changed.isEmpty()) {
                throw new StepFailedException(
                        String.join("; ", changed)
                                + ". A run's inputs are fixed when it is created; start a new run"
                                + " to search the files as they are now");
            }
        }

        private void requireUnchanged(
                StepContext context, String what, RecordedInput input, List<String> changed)
                throws StepFailedException, IOException {
            if (context.isCancellationRequested()) {
                throw new StepFailedException("hashing the inputs was cancelled");
            }
            Path file = input.path();
            if (!Files.isRegularFile(file)) {
                changed.add("the " + what + " " + file + " no longer exists");
                return;
            }
            String now = run.hashes().hash(file).sha256();
            if (!now.equals(input.hashes().sha256())) {
                changed.add(
                        "the "
                                + what
                                + " "
                                + file
                                + " has changed since the run was recorded: recorded SHA-256 "
                                + input.hashes().sha256()
                                + ", now "
                                + now);
            }
        }
    }
}
