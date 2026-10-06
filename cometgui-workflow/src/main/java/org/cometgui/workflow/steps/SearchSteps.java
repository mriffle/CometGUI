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
import java.util.Optional;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.domain.run.OutputBase;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.params.comet.validation.Finding;
import org.cometgui.params.comet.validation.ValidationReport;
import org.cometgui.provenance.manifest.FileDirection;
import org.cometgui.tools.comet.CometIndexCommand;
import org.cometgui.tools.comet.CometOutputException;
import org.cometgui.tools.comet.CometPepXmlValidator;
import org.cometgui.tools.comet.CometPinValidator;
import org.cometgui.tools.comet.CometSearchCommands;
import org.cometgui.tools.comet.PepXmlSummary;
import org.cometgui.tools.comet.PinDecoyConfiguration;
import org.cometgui.tools.comet.PinMergeRecord;
import org.cometgui.tools.comet.PinMerger;
import org.cometgui.tools.comet.PinSummary;
import org.cometgui.workflow.engine.DeclaredFile;
import org.cometgui.workflow.engine.Invocation;
import org.cometgui.workflow.engine.StepAction;
import org.cometgui.workflow.engine.StepContext;
import org.cometgui.workflow.engine.StepDeclaration;
import org.cometgui.workflow.engine.StepFailedException;

/**
 * The steps that produce and check a Comet search's results: building or reusing the index,
 * searching each spectrum file, validating each file's outputs, merging the PINs, and closing the
 * core provenance.
 */
final class SearchSteps {

    /** The detail recording whether the index was built or reused. */
    static final String INDEX_CACHE_DETAIL = "index.cache";

    /** The detail recording the index's SHA-256. */
    static final String INDEX_SHA256_DETAIL = "index.sha256";

    private SearchSteps() {}

    /**
     * {@code build-comet-index}: the project's cached index for the run's key, built with {@code
     * -i} or {@code -j} through the engine when the run was prepared without a complete entry,
     * otherwise reused after it is re-hashed against its marker. Either way the index's header is
     * read and judged by the validator's index rules before the search may read it, so an index the
     * release cannot search, or one built with options the search contradicts, fails here -- before
     * Comet starts searching.
     *
     * <p><strong>Comet 2026.03.0's {@code index_search_type}.</strong> In this flow the index type
     * comes from {@code -i}/{@code -j}, and the search reaches the index through {@code -D}, so
     * {@code database_name} still names the FASTA. The validator's {@code
     * index_search_type.ignored_without_idx} warning, which reads {@code database_name} alone, can
     * therefore appear in an index-mode run's report although the search does read an index. It is
     * a warning and blocks nothing; it is kept rather than filtered, because the decisive check is
     * stricter and comes later: the built (or reused) index's header is judged here, where an
     * {@code index_search_type} of 0 or 1 that contradicts the index's own type is an error ({@code
     * index.contradicts_search}), and {@code -1}, 2026.03.0's "not set", is never a finding.
     */
    static final class BuildIndex implements StepAction {

        private final CometRun run;

        BuildIndex(CometRun run) {
            this.run = Objects.requireNonNull(run, "run");
        }

        @Override
        public StepDeclaration declaration() {
            return RunDeclarations.buildIndex(run);
        }

        @Override
        public void execute(StepContext context)
                throws StepFailedException, IOException, InterruptedException {
            IndexCacheEntry entry = run.cacheEntry().orElseThrow();
            Optional<IndexCacheEntry.Completion> done = entry.completion();
            if (done.isPresent()) {
                // Reused: either as prepared, or because an earlier run completed the entry since.
                verifyReusable(entry, done.get());
                context.addDetail(INDEX_CACHE_DETAIL, "reused");
                context.addDetail(INDEX_SHA256_DETAIL, done.get().sha256());
                return;
            }
            if (!run.buildIndex()) {
                throw new StepFailedException(
                        "the cached index "
                                + entry.indexFile()
                                + " was complete when the run was prepared and is not now; the"
                                + " run declared no index build, so prepare a new run to rebuild"
                                + " it");
            }
            Files.createDirectories(entry.directory());
            entry.clearIncomplete();
            CometIndexCommand command =
                    new CometIndexCommand(
                            run.comet().executable(),
                            run.layout().cometParamsFile(),
                            run.identity().indexMode(),
                            entry.directory(),
                            run.database());
            if (!command.indexFile().equals(entry.indexFile())) {
                throw new IllegalStateException(
                        "the index command writes "
                                + command.indexFile()
                                + ", but the cache entry is "
                                + entry.indexFile());
            }
            command.linkDatabase();
            context.invoke(
                    new Invocation(
                            RunDeclarations.INDEX_INVOCATION, run.tool(), command.command()));
            if (!Files.isRegularFile(entry.indexFile())) {
                throw new StepFailedException(
                        "Comet exited 0 but wrote no index at " + entry.indexFile());
            }
            judge(entry.indexFile());
            FileHashes built = run.hashes().rehash(entry.indexFile());
            entry.markComplete(
                    new IndexCacheEntry.Completion(built.sha256(), Files.size(entry.indexFile())));
            context.addDetail(INDEX_CACHE_DETAIL, "built");
            context.addDetail(INDEX_SHA256_DETAIL, built.sha256());
        }

        private void verifyReusable(IndexCacheEntry entry, IndexCacheEntry.Completion recorded)
                throws StepFailedException, IOException {
            Path index = entry.indexFile();
            String now = run.hashes().rehash(index).sha256();
            long size = Files.size(index);
            if (!now.equals(recorded.sha256()) || size != recorded.size()) {
                throw new StepFailedException(
                        "the cached index "
                                + index
                                + " has SHA-256 "
                                + now
                                + " ("
                                + size
                                + " bytes), but its cache entry records "
                                + recorded.sha256()
                                + " ("
                                + recorded.size()
                                + " bytes); it changed after it was built and is not searched");
            }
            judge(index);
        }

        private void judge(Path index) throws StepFailedException, IOException {
            ValidationReport report = run.checks().judgeIndex(run.model(), run.database(), index);
            if (report.hasErrors()) {
                StringBuilder text =
                        new StringBuilder("the index ")
                                .append(index)
                                .append(" cannot be searched with these parameters:");
                for (Finding error : report.errors()) {
                    text.append("\n- [")
                            .append(error.rule().id())
                            .append("] ")
                            .append(error.message());
                }
                throw new StepFailedException(text.toString());
            }
        }
    }

    /**
     * {@code run-comet}: one invocation per spectrum file, each with {@code -N} into the run and
     * one input, through the engine's bounded concurrency at the parameters' {@code num_threads}.
     * Outputs left by an earlier attempt are removed first, so a stale file can never pass for a
     * fresh one.
     */
    static final class RunComet implements StepAction {

        private final CometRun run;

        RunComet(CometRun run) {
            this.run = Objects.requireNonNull(run, "run");
        }

        @Override
        public StepDeclaration declaration() {
            return RunDeclarations.runComet(run);
        }

        /**
         * The commands, one per spectrum file in input order -- {@code -D} exactly when the run
         * searches a cached index.
         */
        List<ToolCommand> commands() {
            CometSearchCommands commands =
                    run.cacheEntry().isPresent()
                            ? CometSearchCommands.databaseOverride(
                                    run.comet().executable(), run.layout(), run.searched())
                            : CometSearchCommands.databaseFromParameterFile(
                                    run.comet().executable(), run.layout());
            if (commands.delivery() != run.identity().databaseDelivery()) {
                throw new IllegalStateException(
                        "the run recorded database delivery "
                                + run.identity().databaseDelivery().wireName()
                                + ", but its commands deliver it by "
                                + commands.delivery().wireName());
            }
            return commands.commands(run.outputs());
        }

        @Override
        public void execute(StepContext context)
                throws StepFailedException, IOException, InterruptedException {
            for (DeclaredFile file : declaration().files()) {
                if (file.direction() == FileDirection.OUTPUT) {
                    Files.deleteIfExists(file.path());
                }
            }
            List<OutputBase> outputs = run.outputs();
            List<ToolCommand> commands = commands();
            List<Invocation> invocations = new ArrayList<>();
            for (int index = 0; index < outputs.size(); index++) {
                invocations.add(
                        new Invocation(
                                outputs.get(index).stageId(), run.tool(), commands.get(index)));
            }
            context.invokeAll(invocations, run.threads());
        }
    }

    /**
     * {@code validate-comet-outputs}: each input's pepXML (and, with {@code decoy_search = 2}, its
     * separate decoy pepXML) names its own input and {@code -N} base; each PIN is well formed and
     * holds target and decoy rows ({@code R-DEC-04}), refused with a message naming the decoy
     * configuration.
     */
    static final class ValidateOutputs implements StepAction {

        private final CometRun run;

        ValidateOutputs(CometRun run) {
            this.run = Objects.requireNonNull(run, "run");
        }

        @Override
        public StepDeclaration declaration() {
            return RunDeclarations.validateOutputs(run);
        }

        @Override
        public void execute(StepContext context) throws StepFailedException, IOException {
            RunLayout layout = run.layout();
            PinDecoyConfiguration decoys = run.decoys();
            boolean separate = RunDeclarations.separateDecoys(run);
            try {
                for (OutputBase output : run.outputs()) {
                    if (context.isCancellationRequested()) {
                        throw new StepFailedException("validating Comet's outputs was cancelled");
                    }
                    PepXmlSummary pepXml = CometPepXmlValidator.validate(layout, output);
                    PinSummary pin =
                            CometPinValidator.validate(layout.pinFile(output.base()), decoys);
                    String prefix = "outputs." + output.stageId() + ".";
                    context.addDetail(
                            prefix + "spectrum-queries", Long.toString(pepXml.spectrumQueries()));
                    context.addDetail(prefix + "targets", Long.toString(pin.targets()));
                    context.addDetail(prefix + "decoys", Long.toString(pin.decoys()));
                    if (separate) {
                        PepXmlSummary decoyPepXml =
                                CometPepXmlValidator.validate(
                                        RunDeclarations.decoyPepXml(layout, output.base()),
                                        output.input(),
                                        layout.cometOutputBase(output.base()));
                        context.addDetail(
                                prefix + "decoy-spectrum-queries",
                                Long.toString(decoyPepXml.spectrumQueries()));
                    }
                }
            } catch (CometOutputException invalid) {
                throw new StepFailedException(invalid.getMessage(), invalid);
            }
        }
    }

    /**
     * {@code merge-pin}: the per-file PINs merged into {@code inputs/pin/merged.pin} under one
     * header, the feature columns checked, and the merge recorded -- inputs, row counts, output
     * hash -- in the step's {@code stage.finished} event ({@code R-CMT-06}, P8-12). A merged file
     * left by an earlier attempt is this step's own output and is replaced.
     */
    static final class MergePin implements StepAction {

        private final CometRun run;

        MergePin(CometRun run) {
            this.run = Objects.requireNonNull(run, "run");
        }

        @Override
        public StepDeclaration declaration() {
            return RunDeclarations.mergePin(run);
        }

        @Override
        public void execute(StepContext context) throws StepFailedException, IOException {
            RunLayout layout = run.layout();
            List<Path> pins = new ArrayList<>();
            for (OutputBase output : run.outputs()) {
                pins.add(layout.pinFile(output.base()));
            }
            Files.deleteIfExists(layout.mergedPinFile());
            PinMergeRecord record;
            try {
                record = PinMerger.merge(layout, pins, run.hashes());
            } catch (CometOutputException mismatch) {
                throw new StepFailedException(mismatch.getMessage(), mismatch);
            }
            List<PinMergeRecord.Input> inputs = record.inputs();
            for (int index = 0; index < inputs.size(); index++) {
                String prefix = "merge." + inputLabel(index + 1) + ".";
                context.addDetail(prefix + "file", inputs.get(index).file().toString());
                context.addDetail(prefix + "rows", Long.toString(inputs.get(index).rows()));
            }
            context.addDetail("merge.total-rows", Long.toString(record.totalRows()));
            context.addDetail("merge.output", record.output().toString());
            context.addDetail("merge.sha256", record.hashes().sha256());
            context.addDetail("merge.md5", record.hashes().md5());
        }
    }

    /**
     * {@code finalise-provenance}: the engine writes {@code provenance.json} and the report when
     * the attempt ends; this step holds the record to the file that was executed -- the archived
     * {@code comet.params} re-hashed after every search, against the digest {@code writeOnce}
     * returned and the run recorded (P8-12, {@code AC-PRV-04}).
     */
    static final class FinaliseProvenance implements StepAction {

        private final CometRun run;

        FinaliseProvenance(CometRun run) {
            this.run = Objects.requireNonNull(run, "run");
        }

        @Override
        public StepDeclaration declaration() {
            return StepDeclaration.NOTHING;
        }

        @Override
        public void execute(StepContext context) throws StepFailedException, IOException {
            FileHashes verified = PreparationSteps.verifyArchivedParameters(run);
            context.addDetail("comet.params-sha256", verified.sha256());
        }
    }

    /**
     * A 1-based input position as a detail-key segment.
     *
     * @param position the position
     * @return {@code input-01}, {@code input-12}, {@code input-123}
     */
    static String inputLabel(int position) {
        return "input-" + (position < 10 ? "0" : "") + position;
    }
}
