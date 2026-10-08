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

package org.cometgui.tools.percolator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.tools.api.ToolRunOutcome;
import org.cometgui.tools.api.ToolRunner;

/**
 * {@code R-PERC-02}'s functional capability probe: run the binary and read what it wrote.
 *
 * <p>This class has the shape of {@code org.cometgui.install.probe.CapabilityProber} without naming
 * it. {@code cometgui-tools} depends on {@code cometgui-domain} and {@code cometgui-process} and
 * not on {@code cometgui-install}, so it cannot implement an interface declared there; the port is
 * stated entirely in domain vocabulary precisely so that a method reference -- {@code
 * percolatorProbe::probe} -- satisfies it from the composition root, which is the only place that
 * sees both modules.
 *
 * <h2>Why a text probe would prove nothing</h2>
 *
 * <p>The {@code noxml} and {@code XML_SUPPORT=ON} builds of Percolator 3.07.1 print
 * <strong>byte-identical</strong> help text -- 17928 characters each, both listing {@code
 * --xmloutput} and {@code --decoy-xml-output} -- so a probe that read the help text would call both
 * builds capable and would have no way to be wrong about either. {@code
 * scripts/feasibility/probe_xml_capability.py} is wrong for exactly that reason and is not the
 * ancestor of this class.
 *
 * <h2>The two capabilities are established by two runs</h2>
 *
 * <p>{@code XML_OUTPUT} by {@code -X}, {@code XML_DECOY_OUTPUT} by {@code -X -Z}, each judged on
 * the document it produced. They are <em>separate runs</em> rather than one run read two ways
 * because 3.09 has already shown that a release can drop one XML feature and keep others, and one
 * combined flag would make such a release indistinguishable from a fully capable one -- which is
 * the argument {@code ToolCapability.XML_DECOY_OUTPUT} carries.
 *
 * <h2>What "no answer" means, and why it is not an empty set</h2>
 *
 * <p>{@code R-TOOL-08} makes an empty capability set positive evidence of absence, so a probe that
 * could not exercise the binary at all must <strong>throw</strong>, not return nothing. The test
 * for "it ran" is functional: every Percolator run prints its version banner, and the Percolator
 * 3.09 Debian payload on this host prints {@code error while loading shared libraries:
 * libboost_filesystem.so.1.83.0} and exits 127 without one. Reporting that as "not XML-capable" is
 * the exact defect {@code phases/PHASE-05-tool-registry.rst} names, produced by a probe that never
 * got as far as looking.
 *
 * <h2>Every other capability is established by a run of its own, too</h2>
 *
 * <p>Phase 09 (design decision P9-4) extended this probe -- this one, rather than a second one --
 * to the capabilities a real rescoring run needs. <strong>One run per capability</strong>, each
 * over the same fixture, each carrying exactly the option or options under test and nothing else
 * but {@code --no-analytics} once that was observed (below), and each judged on its own observable:
 *
 * <ul>
 *   <li>{@code PSM_TSV_OUTPUT}: {@code --results-psms} writes a table of the fixture's targets;
 *   <li>{@code PEPTIDE_TSV_OUTPUT}: {@code --results-peptides} writes one too;
 *   <li>{@code DECOY_OUTPUT}: {@code --decoy-results-psms} and {@code --decoy-results-peptides} in
 *       one run write two tables of the fixture's decoys -- one capability, so both are required;
 *   <li>{@code WEIGHTS_OUTPUT}: {@code --weights} writes the learned weights of the fixture's
 *       features;
 *   <li>{@code SEED_OPTION}, {@code THREAD_OPTION}, {@code TEST_FDR_OPTION}, {@code
 *       TRAIN_FDR_OPTION} and {@code MAX_ITERATIONS_OPTION}: the option is <em>accepted on a run
 *       that completed</em> -- exit 0 and the full peptide table of the fixture's targets on
 *       standard output, which is where Percolator writes it when no file is named. Each is passed
 *       at Percolator's own documented default ({@code --seed 1}, {@code --num-threads 3}, {@code
 *       --testFDR 0.01}, {@code --trainFDR 0.01}, {@code --maxiter 10}), so the run computes
 *       exactly what the default run computes and the option is the only thing that differs.
 * </ul>
 *
 * <h2>{@code --no-analytics} first, and then on every run that follows</h2>
 *
 * <p>Percolator posts usage analytics to Google on every run unless it is given {@code
 * --no-analytics}, and the owner decided ({@code D-013}, 2026-10-08) that CometGUI always passes it
 * -- which includes the probe's own runs. So the <strong>first</strong> run is {@code
 * NO_ANALYTICS_OPTION}'s own: {@code --no-analytics} and the fixture, judged exactly as the other
 * option-only capabilities are (exit 0 and the full target peptide table on standard output). Once
 * it is observed, every later run of this probe carries {@code --no-analytics} too, as its last
 * option before the fixture; the observation run itself carries it by construction. A build that
 * refuses it simply does not get it: it loses that capability and nothing else, because the later
 * runs then go without it -- and the refused run itself cannot have posted anything, since real
 * Percolator refuses an unknown option before it does any work. Passing {@code --no-analytics}
 * alongside an option under test changes nothing the probe judges: it is not an output and not a
 * scoring parameter.
 *
 * <p>The spellings are {@link PercolatorOption}'s, so the options a command builder emits are the
 * ones this probe watched being accepted. <strong>Why not one combined run:</strong> real
 * Percolator refuses an unknown option outright -- banner, {@code Exception caught}, exit 1, no
 * output at all -- so one unsupported option in a combined run would take every other capability
 * down with it, and one capability would hide another. Separate runs make a build that rejects one
 * option lose exactly that capability and keep the rest.
 *
 * <p><strong>What it costs.</strong> Twelve runs. Measured on this project's host on 2026-10-07
 * over the 64 plus 64 fixture, before the twelfth was added: about half a second each, 5.3 s for
 * 3.06.5, 5.8 s for 3.07.1 and 3.8 s for 3.09, whose two XML runs refuse at once. That is paid once
 * per install or registration (and again when the executable's checksum changes, {@code
 * R-TOOL-07}), not per search.
 *
 * <p><strong>What none of this is.</strong> It is not {@code --help} parsing: help text is evidence
 * of a name, never of a capability ({@code R-PERC-02}). And it is not a results parser: {@link
 * ProbeArtefacts} reads only enough of each table to say it was written with the expected header
 * and rows; the parsers are {@code org.cometgui.results.parser}'s.
 */
public final class PercolatorCapabilityProbe {

    /** Where the probe writes its fixture and the binary's output. */
    private static final String WORKSPACE_PREFIX = "cometgui-percolator-probe-";

    private static final String TARGETS_FILE = "targets.pout.xml";
    private static final String DECOYS_FILE = "decoys.pout.xml";
    private static final String PSMS_FILE = "psms.tsv";
    private static final String PEPTIDES_FILE = "peptides.tsv";
    private static final String DECOY_PSMS_FILE = "decoy-psms.tsv";
    private static final String DECOY_PEPTIDES_FILE = "decoy-peptides.tsv";
    private static final String WEIGHTS_FILE = "weights.txt";

    /*
     * Each option-only capability is exercised at Percolator's own documented default, so the run
     * computes what the default run computes and the option is the only difference: --help of
     * 3.07.1 and 3.09 alike says "Default = 1" for --seed, "Default (one thread per CV fold) = 3"
     * for --num-threads, "Default = 0.01" for --testFDR and --trainFDR, and "Default = 10" for
     * --maxiter.  A non-default value could change the run's outcome and make the option's verdict
     * depend on the fixture rather than on whether the option is accepted.
     */
    private static final String DEFAULT_SEED = "1";
    private static final String DEFAULT_THREADS = "3";
    private static final String DEFAULT_FDR = "0.01";
    private static final String DEFAULT_MAX_ITERATIONS = "10";

    private final ToolRunner runner;
    private final int targetRows;

    /**
     * Creates the probe, with the fixture size {@code R-PERC-02} fixes.
     *
     * @param runner how one invocation is run and collected; every process in this product goes
     *     through the process service ({@code R-PROC-02})
     * @throws NullPointerException if {@code runner} is {@code null}
     */
    public PercolatorCapabilityProbe(ToolRunner runner) {
        this(runner, SyntheticPin.PROBE_TARGET_ROWS);
    }

    /**
     * Creates the probe with a chosen fixture size.
     *
     * <p><strong>This exists so that the fixture size can be shown to matter.</strong> {@code
     * R-PERC-02} says 64 target and 64 decoy rows is sufficient and 8 and 8 is not, and the only
     * way to demonstrate that claim rather than repeat it is to run this same probe, against the
     * same binary, at both sizes and watch the verdict change -- {@code
     * PercolatorRealBinaryTest.theNegativeControl} does exactly that. The size is a number from the
     * requirement, not a knob for a caller: {@link #PercolatorCapabilityProbe(ToolRunner)} is what
     * the product uses, and a test pins it to {@value SyntheticPin#PROBE_TARGET_ROWS}.
     *
     * @param runner how one invocation is run and collected
     * @param targetRows how many target rows the fixture gets; the same number of decoy rows
     *     follows
     * @throws NullPointerException if {@code runner} is {@code null}
     * @throws IllegalArgumentException if {@code targetRows} is not positive
     */
    public PercolatorCapabilityProbe(ToolRunner runner, int targetRows) {
        this.runner = Objects.requireNonNull(runner, "runner");
        if (targetRows < 1) {
            throw new IllegalArgumentException(
                    "a synthetic PIN needs at least one target row, but was asked for "
                            + targetRows);
        }
        this.targetRows = targetRows;
    }

    /**
     * How many target rows this probe's fixture carries.
     *
     * @return the row count, {@value SyntheticPin#PROBE_TARGET_ROWS} for the product's own probe
     */
    public int targetRows() {
        return targetRows;
    }

    /**
     * Probes what a Percolator build can do, by running it.
     *
     * <p>The signature is {@code org.cometgui.install.probe.CapabilityProber}'s.
     *
     * @param tool which tool this is a build of; must be {@link ToolName#PERCOLATOR}
     * @param version the version the identity stage read from the binary, carried into the
     *     diagnostics so a refusal names the build it was about
     * @param platform the host the probe is running on
     * @param executable the absolute path of the staged executable
     * @return the capabilities the build was <em>observed</em> to have, possibly empty
     * @throws IOException if the build could not be exercised at all -- which is never reported as
     *     an empty capability set
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if {@code tool} is not {@link ToolName#PERCOLATOR}
     */
    public Set<ToolCapability> probe(
            ToolName tool, ToolVersion version, HostPlatform platform, Path executable)
            throws IOException {
        Objects.requireNonNull(tool, "tool");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(platform, "platform");
        Objects.requireNonNull(executable, "executable");
        if (tool != ToolName.PERCOLATOR) {
            throw new IllegalArgumentException(
                    "this probe runs Percolator and was asked to probe "
                            + tool.id()
                            + "; a capability of one tool means nothing said of another");
        }
        Path workspace = Files.createTempDirectory(WORKSPACE_PREFIX);
        try {
            return probeIn(workspace, version, executable);
        } finally {
            deleteRecursively(workspace);
        }
    }

    private Set<ToolCapability> probeIn(Path workspace, ToolVersion version, Path executable)
            throws IOException {
        Path pin = SyntheticPin.write(workspace, targetRows, SyntheticPin.PROBE_SEED);
        Run run = new Run(executable, workspace, version, pin);
        Set<ToolCapability> observed = EnumSet.noneOf(ToolCapability.class);
        probeNoAnalytics(run, observed);
        Path targets = workspace.resolve(TARGETS_FILE);
        run.with(PercolatorOption.XML_OUTPUT.spelling(), targets.toString());
        if (writesDocument(targets, targetRows, false)) {
            observed.add(ToolCapability.XML_OUTPUT);
        }
        Path decoys = workspace.resolve(DECOYS_FILE);
        run.with(
                PercolatorOption.XML_OUTPUT.spelling(),
                decoys.toString(),
                PercolatorOption.XML_DECOY_OUTPUT.spelling());
        if (writesDocument(decoys, targetRows * 2, true)) {
            observed.add(ToolCapability.XML_DECOY_OUTPUT);
        }
        probeTables(run, workspace, observed);
        probeOptions(run, observed);
        return Collections.unmodifiableSet(observed);
    }

    /*
     * THE TABLE IS THE VERDICT, AS THE DOCUMENT IS FOR XML, AND FOR THE SAME REASON: what decides
     * the capability is whether the artefact the option names was written, with the fixture's rows,
     * not what the process exited with.
     */
    private void probeTables(Run run, Path workspace, Set<ToolCapability> observed)
            throws IOException {
        Path psms = workspace.resolve(PSMS_FILE);
        run.with(PercolatorOption.RESULTS_PSMS.spelling(), psms.toString());
        if (ProbeArtefacts.isResultFile(psms, targetRows, false)) {
            observed.add(ToolCapability.PSM_TSV_OUTPUT);
        }
        Path peptides = workspace.resolve(PEPTIDES_FILE);
        run.with(PercolatorOption.RESULTS_PEPTIDES.spelling(), peptides.toString());
        if (ProbeArtefacts.isResultFile(peptides, targetRows, false)) {
            observed.add(ToolCapability.PEPTIDE_TSV_OUTPUT);
        }
        Path decoyPsms = workspace.resolve(DECOY_PSMS_FILE);
        Path decoyPeptides = workspace.resolve(DECOY_PEPTIDES_FILE);
        run.with(
                PercolatorOption.DECOY_RESULTS_PSMS.spelling(),
                decoyPsms.toString(),
                PercolatorOption.DECOY_RESULTS_PEPTIDES.spelling(),
                decoyPeptides.toString());
        if (ProbeArtefacts.isResultFile(decoyPsms, targetRows, true)
                && ProbeArtefacts.isResultFile(decoyPeptides, targetRows, true)) {
            observed.add(ToolCapability.DECOY_OUTPUT);
        }
        Path weights = workspace.resolve(WEIGHTS_FILE);
        run.with(PercolatorOption.WEIGHTS.spelling(), weights.toString());
        if (ProbeArtefacts.isWeightsFile(weights)) {
            observed.add(ToolCapability.WEIGHTS_OUTPUT);
        }
    }

    /*
     * FIRST, SO THAT EVERY LATER RUN CAN CARRY IT (D-013).  Judged as every option-only capability
     * is; once observed, the Run appends it to every invocation that follows.
     */
    private void probeNoAnalytics(Run run, Set<ToolCapability> observed) throws IOException {
        if (completed(run.with(PercolatorOption.NO_ANALYTICS.spelling()))) {
            observed.add(PercolatorOption.NO_ANALYTICS.capability());
            run.withoutAnalyticsFromNowOn();
        }
    }

    private void probeOptions(Run run, Set<ToolCapability> observed) throws IOException {
        acceptedOn(run, PercolatorOption.SEED, DEFAULT_SEED, observed);
        acceptedOn(run, PercolatorOption.NUM_THREADS, DEFAULT_THREADS, observed);
        acceptedOn(run, PercolatorOption.TEST_FDR, DEFAULT_FDR, observed);
        acceptedOn(run, PercolatorOption.TRAIN_FDR, DEFAULT_FDR, observed);
        acceptedOn(run, PercolatorOption.MAX_ITERATIONS, DEFAULT_MAX_ITERATIONS, observed);
    }

    /*
     * AN OPTION WITH NO ARTEFACT OF ITS OWN IS ACCEPTED WHEN THE RUN CARRYING IT COMPLETED: exit 0
     * AND the whole peptide table of the fixture's targets on standard output.  Both, because each
     * alone has been seen to lie in this project -- comet.linux.exe exits 1 from a correct -h, and
     * an exit code says nothing about whether the run got as far as scoring anything.  Real
     * Percolator refuses an option it does not know with exit 1 and nothing on standard output, so
     * either half fails for it.
     */
    private void acceptedOn(
            Run run, PercolatorOption option, String value, Set<ToolCapability> observed)
            throws IOException {
        if (completed(run.with(option.spelling(), value))) {
            observed.add(option.capability());
        }
    }

    private boolean completed(ToolRunOutcome outcome) {
        return outcome.exitedZero()
                && ProbeArtefacts.isResultTable(outcome.standardOutput(), targetRows, false);
    }

    /** One probe's invocations: the same binary, workspace and fixture, with different options. */
    private final class Run {

        private final Path executable;
        private final Path workspace;
        private final ToolVersion version;
        private final Path pin;
        private boolean noAnalytics;

        Run(Path executable, Path workspace, ToolVersion version, Path pin) {
            this.executable = executable;
            this.workspace = workspace;
            this.version = version;
            this.pin = pin;
        }

        /* Once the build was observed to accept --no-analytics, every later run carries it. */
        void withoutAnalyticsFromNowOn() {
            noAnalytics = true;
        }

        /*
         * The fixture is always the last argument, after the options under test and then
         * --no-analytics when it was observed, which is the shape Percolator's usage line gives:
         * "percolator [other options] pin.tsv".
         */
        ToolRunOutcome with(String... options) throws IOException {
            List<String> arguments = new ArrayList<>(List.of(options));
            if (noAnalytics) {
                arguments.add(PercolatorOption.NO_ANALYTICS.spelling());
            }
            arguments.add(pin.toString());
            return exercise(executable, workspace, version, arguments);
        }
    }

    /*
     * Runs one invocation and refuses unless the binary demonstrably ran.  The banner is the test:
     * a loader failure prints its own complaint and never reaches Percolator's own code, and
     * treating that as "this build cannot write XML" is the specific defect this phase exists to
     * avoid.
     */
    private ToolRunOutcome exercise(
            Path executable, Path workspace, ToolVersion version, List<String> arguments)
            throws IOException {
        List<String> argv = new ArrayList<>();
        argv.add(executable.toString());
        argv.addAll(arguments);
        ToolRunOutcome outcome = runner.run(new ToolCommand(argv, workspace, Map.of()));
        if (outcome.timedOut()) {
            throw new IOException(
                    "Percolator "
                            + version.text()
                            + " at "
                            + executable
                            + " did not finish within "
                            + runner.timeout()
                            + ", so this probe established nothing about it; a probe that got no"
                            + " answer has not established that a capability is absent");
        }
        if (!PercolatorBanner.isPresentIn(outcome.errorFirst())) {
            throw new IOException(
                    "Percolator "
                            + version.text()
                            + " at "
                            + executable
                            + " never printed its version banner, so it did not run far enough to"
                            + " be asked what it can do; this is a loadability failure and must not"
                            + " be reported as a missing capability. It exited "
                            + outcome.exitCode().orElse(-1)
                            + " saying: "
                            + outcome.joinedOutput());
        }
        return outcome;
    }

    /*
     * THE DOCUMENT IS THE VERDICT, AND THE EXIT CODE IS DELIBERATELY NOT PART OF IT.  An aborted
     * Percolator run exits 1 AND leaves a zero-byte file, so a rule that short-circuited on the
     * exit code would never reach the zero-byte check on the one run this project has ever seen
     * produce a zero-byte file -- a guard that cannot fire is not a guard, which is this project's
     * signature defect.  Reading the file is also strictly the stronger test: 64 psm elements in
     * the percolator_out/15 namespace cannot be produced by a build that cannot write pout XML,
     * whatever it exited with, and comet.linux.exe answering -h with exit 1 and a correct banner is
     * this project's standing reminder that a tool's exit code is not its verdict.
     */
    private static boolean writesDocument(
            Path written, int expectedPsmCount, boolean requireBothDecoys) {
        PoutDocument document;
        try {
            document = PoutDocument.read(written);
        } catch (IOException noUsableDocument) {
            return false;
        }
        if (!document.isPercolatorOutput() || document.psmCount() != expectedPsmCount) {
            return false;
        }
        return !requireBothDecoys || document.hasBothDecoyValues();
    }

    /*
     * Best effort, and deliberately silent: this runs in a finally block, and a failure to tidy a
     * temporary directory must not replace the IOException that says the binary could not be
     * exercised.  A left-behind temporary directory is a nuisance; a lost diagnostic sends somebody
     * to the wrong problem.
     */
    private static void deleteRecursively(Path directory) {
        try (Stream<Path> walk = Files.walk(directory)) {
            for (Path entry : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(entry);
            }
        } catch (IOException tidyingFailed) {
            /* Nothing useful can be done, and nothing about the probe's verdict changes. */
        }
    }
}
