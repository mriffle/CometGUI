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
import java.nio.file.attribute.DosFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.cometgui.results.parser.LearnedWeights;
import org.cometgui.results.parser.PercolatorOutputException;
import org.cometgui.results.parser.ResultTableCounts;
import org.cometgui.results.parser.ResultTableReader;
import org.cometgui.results.parser.WeightsReader;
import org.cometgui.tools.comet.PinSummary;
import org.cometgui.tools.percolator.PercolatorArtefact;
import org.cometgui.tools.percolator.PercolatorPinCheck;
import org.cometgui.tools.percolator.PercolatorRefusedException;
import org.cometgui.tools.percolator.PoutDocument;
import org.cometgui.workflow.engine.Invocation;
import org.cometgui.workflow.engine.StepAction;
import org.cometgui.workflow.engine.StepContext;
import org.cometgui.workflow.engine.StepDeclaration;
import org.cometgui.workflow.engine.StepFailedException;

/**
 * The Percolator steps: resolving the selected build, running it once over the merged PIN, and
 * parsing what it wrote (design decisions P9-8, P9-9 and P9-10).
 *
 * <p>Percolator is launched only through {@link StepContext#invoke} -- the engine's one launcher --
 * and only after the merged PIN has passed {@link PercolatorPinCheck} with the run's one decoy
 * configuration. Its raw outputs are made read-only once it has succeeded and every one has been
 * checked ({@code R-PERC-07}); parsing reads them and never writes.
 */
final class PercolatorSteps {

    /** Write permissions, removed from each raw output on a POSIX file system. */
    private static final Set<PosixFilePermission> WRITE =
            EnumSet.of(
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.GROUP_WRITE,
                    PosixFilePermission.OTHERS_WRITE);

    private PercolatorSteps() {}

    /**
     * {@code resolve-percolator}: the selected executable re-hashed -- never from the cache -- and
     * held to the SHA-256 it was selected at ({@code R-RUN-02}'s spirit).
     */
    static final class ResolvePercolator implements StepAction {

        private final PercolatorRun run;

        ResolvePercolator(PercolatorRun run) {
            this.run = Objects.requireNonNull(run, "run");
        }

        @Override
        public StepDeclaration declaration() {
            return StepDeclaration.NOTHING;
        }

        @Override
        public void execute(StepContext context) throws StepFailedException, IOException {
            PercolatorSelection selection = run.choice().selection();
            Path executable = selection.executable();
            if (!Files.isRegularFile(executable) || !Files.isExecutable(executable)) {
                throw new StepFailedException(
                        "the selected Percolator executable "
                                + executable
                                + " does not exist or cannot be executed");
            }
            String now = run.hashes().rehash(executable).sha256();
            if (!now.equals(selection.sha256())) {
                throw new StepFailedException(
                        "the Percolator executable "
                                + executable
                                + " has SHA-256 "
                                + now
                                + ", but Percolator "
                                + selection.version().text()
                                + " was selected at "
                                + selection.sha256()
                                + "; it was replaced after it was selected, and is not run");
            }
        }
    }

    /**
     * {@code run-percolator}: the archived settings verified, the merged PIN checked, then the one
     * invocation; afterwards every artefact the command lists must exist and hold bytes, nothing
     * else may be in the output directory -- no pout XML that was not asked for -- and each
     * artefact is made read-only.
     */
    static final class RunPercolator implements StepAction {

        private final PercolatorRun run;

        RunPercolator(PercolatorRun run) {
            this.run = Objects.requireNonNull(run, "run");
        }

        @Override
        public StepDeclaration declaration() {
            return PercolatorDeclarations.runPercolator(run);
        }

        @Override
        public void execute(StepContext context)
                throws StepFailedException, IOException, InterruptedException {
            PercolatorSettingsFile.verify(
                    PercolatorDeclarations.settingsFile(run.layout()),
                    run.settings(),
                    run.hashes());
            PinSummary pin;
            try {
                pin = PercolatorPinCheck.check(run.layout().mergedPinFile(), run.decoys());
            } catch (PercolatorRefusedException refused) {
                throw new StepFailedException(refused.getMessage(), refused);
            }
            context.addDetail("pin.targets", Long.toString(pin.targets()));
            context.addDetail("pin.decoys", Long.toString(pin.decoys()));
            context.addDetail("pin.features", Integer.toString(pin.featureColumns().size()));

            Path out = Files.createDirectories(run.outputDirectory());
            for (Path artefact : run.command().artefacts().values()) {
                Files.deleteIfExists(artefact);
            }
            context.invoke(
                    new Invocation(
                            PercolatorDeclarations.INVOCATION,
                            run.tool(),
                            run.command().command()));

            Map<PercolatorArtefact, Path> artefacts = run.command().artefacts();
            for (Map.Entry<PercolatorArtefact, Path> artefact : artefacts.entrySet()) {
                Path file = artefact.getValue();
                if (!Files.isRegularFile(file) || Files.size(file) == 0) {
                    throw new StepFailedException(
                            "Percolator exited 0 but its "
                                    + PercolatorDeclarations.roleOf(artefact.getKey())
                                    + " file "
                                    + file
                                    + (Files.isRegularFile(file)
                                            ? " is empty"
                                            : " was not written"));
                }
            }
            List<String> unexpected = unexpected(out, artefacts);
            if (!unexpected.isEmpty()) {
                throw new StepFailedException(
                        "Percolator wrote "
                                + String.join(", ", unexpected)
                                + " in "
                                + out
                                + ", which its command did not ask for"
                                + (run.command().writesXml()
                                        ? ""
                                        : "; this run requested no pout XML and expects none"));
            }
            for (Path file : artefacts.values()) {
                makeReadOnly(file);
            }
            context.addDetail("outputs.count", Integer.toString(artefacts.size()));
            context.addDetail("outputs.read-only", "true");
        }

        private static List<String> unexpected(Path out, Map<PercolatorArtefact, Path> artefacts)
                throws IOException {
            TreeSet<String> found = new TreeSet<>();
            try (Stream<Path> listed = Files.list(out)) {
                for (Path path : listed.toList()) {
                    if (!artefacts.containsValue(path)) {
                        found.add(String.valueOf(path.getFileName()));
                    }
                }
            }
            return new ArrayList<>(found);
        }
    }

    /**
     * Makes one raw output read-only ({@code R-PERC-07}): every write permission removed on a POSIX
     * file system, the DOS read-only attribute set elsewhere. Only the POSIX half has run.
     *
     * @param file the output
     * @throws IOException if neither view is available or the change cannot be made
     */
    static void makeReadOnly(Path file) throws IOException {
        PosixFileAttributeView posix =
                Files.getFileAttributeView(file, PosixFileAttributeView.class);
        if (posix != null) {
            Set<PosixFilePermission> permissions =
                    EnumSet.copyOf(posix.readAttributes().permissions());
            permissions.removeAll(WRITE);
            posix.setPermissions(permissions);
            return;
        }
        DosFileAttributeView dos = Files.getFileAttributeView(file, DosFileAttributeView.class);
        if (dos == null) {
            throw new IOException(
                    "the raw Percolator output "
                            + file
                            + " cannot be made read-only: its file system offers neither POSIX"
                            + " permissions nor a DOS read-only attribute");
        }
        dos.setReadOnly(true);
    }

    /**
     * {@code parse-percolator}: the PSM and peptide tables (target and decoy) through the one table
     * reader, the weights through the one weights reader -- the split count read from the file
     * ({@code R-PERC-09}) -- and the pout XML through the one pout reader when it was written. The
     * counts are recorded in the step's {@code stage.finished} event. Every file is only read.
     */
    static final class ParsePercolator implements StepAction {

        private final PercolatorRun run;

        ParsePercolator(PercolatorRun run) {
            this.run = Objects.requireNonNull(run, "run");
        }

        @Override
        public StepDeclaration declaration() {
            return PercolatorDeclarations.parsePercolator(run);
        }

        @Override
        public void execute(StepContext context) throws StepFailedException, IOException {
            Map<PercolatorArtefact, Path> artefacts = run.command().artefacts();
            try {
                for (PercolatorArtefact table :
                        List.of(
                                PercolatorArtefact.TARGET_PSMS,
                                PercolatorArtefact.TARGET_PEPTIDES,
                                PercolatorArtefact.DECOY_PSMS,
                                PercolatorArtefact.DECOY_PEPTIDES)) {
                    Path file = artefacts.get(table);
                    if (file != null) {
                        ResultTableCounts counts = ResultTableReader.forEach(file, row -> {});
                        String prefix = "tables." + PercolatorDeclarations.roleOf(table) + ".";
                        context.addDetail(prefix + "rows", Long.toString(counts.rows()));
                        context.addDetail(prefix + "known-q", Long.toString(counts.knownQValues()));
                        context.addDetail(
                                prefix + "unknown-q", Long.toString(counts.unknownQValues()));
                    }
                }
                Path weights = artefacts.get(PercolatorArtefact.WEIGHTS);
                if (weights == null) {
                    context.addDetail(
                            "weights.status",
                            PercolatorProvenance.weightsWarning(run.command()).orElseThrow());
                } else {
                    LearnedWeights learned = WeightsReader.read(weights);
                    context.addDetail("weights.splits", Integer.toString(learned.splitCount()));
                    context.addDetail(
                            "weights.features", Integer.toString(learned.featureNames().size()));
                }
            } catch (PercolatorOutputException refused) {
                throw new StepFailedException(refused.getMessage(), refused);
            }
            Path xml = artefacts.get(PercolatorArtefact.POUT_XML);
            if (xml == null) {
                context.addDetail("pout.status", "not requested");
                return;
            }
            PoutDocument pout = PoutDocument.read(xml);
            if (!pout.isPercolatorOutput()) {
                throw new StepFailedException(
                        "the pout XML "
                                + xml
                                + " is not Percolator output: its root element is "
                                + pout.rootElement()
                                + " in namespace "
                                + pout.namespace());
            }
            context.addDetail("pout.psms", Integer.toString(pout.psmCount()));
            context.addDetail("pout.peptides", Integer.toString(pout.peptideCount()));
            context.addDetail("pout.namespace", pout.namespace());
        }
    }
}
