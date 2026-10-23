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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.cometgui.domain.run.IndexMode;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolOrigin;
import org.cometgui.params.comet.model.DecoySource;
import org.cometgui.params.comet.model.ValueOrigin;
import org.cometgui.params.percolator.PercolatorSettings;
import org.cometgui.tools.percolator.SyntheticPin;
import org.cometgui.workflow.engine.RunResult;
import org.cometgui.workflow.engine.StepStateListener;
import org.cometgui.workflow.engine.StepTransition;
import org.cometgui.workflow.state.EngineStep;
import org.cometgui.workflow.state.StepState;

/**
 * Stand-in tools for the failure paths that happen while a run is under way (Phase 14 unit 8b): a
 * Comet whose outputs pass every check the real workflow makes, so the run reaches Percolator, and
 * a Percolator that writes all of its files and then <em>keeps running</em> until it is cancelled
 * or released.
 *
 * <p>Both are POSIX shell scripts run through the real process service, whose tool environment is
 * exactly {@code LANG=C.UTF-8} with no {@code PATH}: everything is a shell built-in except the one
 * long-running child, {@code /bin/sleep}, named by absolute path. That child is the tool's own
 * work, bounded at {@value #CHILD_SECONDS} seconds so a test killed midway cannot strand it for
 * ever; a test never waits on it -- it waits on the lines the stand-in prints.
 *
 * <ul>
 *   <li><strong>Comet</strong> reads its {@code -N} base and its spectrum file from the command
 *       line, writes {@code <base>.pep.xml} -- a pepXML naming that input and base -- and {@code
 *       <base>.pin}, CometGUI's own synthetic 64 + 64 PIN ({@link SyntheticPin}), and exits 0.
 *   <li><strong>Percolator</strong> writes every file its command line names, as {@link
 *       FakePercolator} does, then starts {@code /bin/sleep} in the background, prints {@code child
 *       <pid>} and {@code pid <pid>}, and waits for that child. {@link Ending#CANCELLED_ONLY} exits
 *       with the child's status, so only a cancellation ends it; {@link Ending#RELEASABLE} exits 0
 *       once the child has gone, so a test ends the stage normally by killing the child.
 * </ul>
 */
final class StandInTools {

    /** How long the stand-in Percolator's child runs at most. A watchdog, never a wait. */
    static final int CHILD_SECONDS = 300;

    /** The decoy prefix of a run over the synthetic PIN. */
    static final String PREFIX = SyntheticPin.DECOY_PROTEIN_PREFIX;

    /** The synthetic PIN's rows: 64 target and 64 decoy. */
    static final int PIN_ROWS = 2 * SyntheticPin.PROBE_TARGET_ROWS;

    private StandInTools() {}

    /** How the stand-in Percolator ends once it is waiting. */
    enum Ending {
        /**
         * With its child's exit status: a cancellation kills both and the stage is not a success.
         */
        CANCELLED_ONLY,
        /** With 0, once its child has gone: killing the child ends the stage as a success. */
        RELEASABLE
    }

    /**
     * Writes the stand-in Comet.
     *
     * @param file where it goes
     * @return the executable
     */
    static Path comet(Path file) throws IOException {
        return comet(file, false);
    }

    /**
     * Writes the stand-in Comet, which may wait once its files are written.
     *
     * @param file where it goes
     * @param waits whether, once its files are written, it starts {@code /bin/sleep}, prints {@code
     *     child <pid>} and {@code pid <pid>}, and exits 0 only when that child has gone -- so a
     *     test can change the run's directories while Comet runs and then let it finish
     * @return the executable
     */
    static Path comet(Path file, boolean waits) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("#!/bin/sh");
        lines.add("base=''");
        lines.add("input=''");
        lines.add("for argument in \"$@\"; do");
        lines.add("  case \"$argument\" in");
        lines.add("    -N*) base=\"${argument#-N}\" ;;");
        lines.add("    -*) : ;;");
        lines.add("    *) input=\"$argument\" ;;");
        lines.add("  esac");
        lines.add("done");
        lines.add("[ -n \"$base\" ] && [ -n \"$input\" ] || exit 9");
        lines.add("stem=\"${input%.*}\"");
        lines.add("extension=\".${input##*.}\"");
        lines.add(
                "printf '<?xml version=\"1.0\"?>\\n<msms_pipeline_analysis"
                        + " xmlns=\"http://regis-web.systemsbiology.net/pepXML\"><msms_run_summary"
                        + " base_name=\"%s\" raw_data=\"%s\"><search_summary base_name=\"%s\"/>"
                        + "</msms_run_summary></msms_pipeline_analysis>\\n'"
                        + " \"$stem\" \"$extension\" \"$base\" > \"$base.pep.xml\"");
        lines.add(
                "while IFS= read -r line; do printf '%s\\n' \"$line\"; done > \"$base.pin\""
                        + " <<'PIN'");
        lines.addAll(SyntheticPin.forCapabilityProbe().lines().toList());
        lines.add("PIN");
        if (waits) {
            waitForChild(lines);
        }
        lines.add("exit 0");
        return executable(file, lines);
    }

    /**
     * Writes the stand-in Percolator.
     *
     * @param file where it goes
     * @param ending how it ends once it is waiting
     * @return the executable
     */
    static Path percolator(Path file, Ending ending) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("#!/bin/sh");
        lines.add("table='" + FakePercolator.TABLE + "'");
        lines.add("weights='" + FakePercolator.WEIGHTS + "'");
        lines.add("xml='" + FakePercolator.XML + "'");
        lines.add("while [ $# -gt 1 ]; do");
        lines.add("  case \"$1\" in");
        lines.add("    --no-analytics) shift; continue ;;");
        lines.add(
                "    --results-psms|--results-peptides|--decoy-results-psms"
                        + "|--decoy-results-peptides) printf \"$table\" > \"$2\" ;;");
        lines.add("    --weights) printf \"$weights\" > \"$2\" ;;");
        lines.add("    -X) printf \"$xml\" > \"$2\" ;;");
        lines.add("  esac");
        lines.add("  shift 2");
        lines.add("done");
        waitForChild(lines);
        lines.add(ending == Ending.CANCELLED_ONLY ? "exit $?" : "exit 0");
        return executable(file, lines);
    }

    /**
     * A search of one spectrum file through the stand-in Comet, with the stand-in Percolator as the
     * run's Percolator 3.07.1, over the synthetic PIN's decoy prefix.
     *
     * @param fake the made-up search files, whose executable has been replaced by {@link #comet}
     * @param percolator the stand-in Percolator
     * @return the request
     */
    static SearchRequest request(FakeSearch fake, Path percolator) throws IOException {
        return request(fake, percolator, List.of(fake.spectra().get(0)));
    }

    /**
     * The same search over the given spectrum files.
     *
     * @param fake the made-up search files, whose executable has been replaced by {@link #comet}
     * @param percolator the stand-in Percolator
     * @param spectra the spectrum files to search
     * @return the request
     */
    static SearchRequest request(FakeSearch fake, Path percolator, List<Path> spectra)
            throws IOException {
        ToolOffer offer =
                FakePercolator.offer(
                        percolator, "3.07.1", ToolOrigin.MANAGED, FakePercolator.EVERY, List.of());
        return new SearchRequest(
                fake.model(DecoySource.COMET_INTERNAL_CONCATENATED)
                        .withText("decoy_prefix", PREFIX, ValueOrigin.USER),
                spectra,
                fake.selection(),
                IndexMode.NONE,
                Optional.of(
                        FakePercolator.choice(
                                offer, List.of(offer), Set.of(), PercolatorSettings.defaults())));
    }

    /** The steps of a search with Percolator and no index or downstream stage, in order. */
    static final List<EngineStep> STEPS =
            List.of(
                    EngineStep.VALIDATE_CONFIGURATION,
                    EngineStep.RESOLVE_COMET,
                    EngineStep.RESOLVE_PERCOLATOR,
                    EngineStep.SERIALISE_COMET_PARAMS,
                    EngineStep.HASH_INPUTS,
                    EngineStep.RUN_COMET,
                    EngineStep.VALIDATE_COMET_OUTPUTS,
                    EngineStep.MERGE_PIN,
                    EngineStep.RUN_PERCOLATOR,
                    EngineStep.PARSE_PERCOLATOR,
                    EngineStep.FINALISE_RESULTS,
                    EngineStep.FINALISE_PROVENANCE);

    /**
     * Every step's end when a run ended at one step: each before it succeeded, it ended so, and
     * none after it started.
     *
     * @param ended the step the run ended at
     * @param state how it ended
     * @return step to state, sorted
     */
    static Map<EngineStep, StepState> statesUpTo(EngineStep ended, StepState state) {
        Map<EngineStep, StepState> states = new TreeMap<>();
        int at = STEPS.indexOf(ended);
        for (int index = 0; index < STEPS.size(); index++) {
            states.put(
                    STEPS.get(index),
                    index < at ? StepState.SUCCEEDED : index == at ? state : StepState.NOT_STARTED);
        }
        return states;
    }

    /**
     * The files the stand-in Percolator writes, as the run records them: file name to role and
     * status. No pout XML: no downstream stage that needs it is enabled, so {@code -X} is not
     * passed.
     *
     * @param status the status every one has
     * @return file name to {@code <role> <status>}, sorted
     */
    static Map<String, String> percolatorOutputs(String status) {
        Map<String, String> outputs = new TreeMap<>();
        outputs.put("psms.tsv", "percolator-psms " + status);
        outputs.put("peptides.tsv", "percolator-peptides " + status);
        outputs.put("decoy-psms.tsv", "percolator-decoy-psms " + status);
        outputs.put("decoy-peptides.tsv", "percolator-decoy-peptides " + status);
        outputs.put("weights.txt", "percolator-weights " + status);
        return outputs;
    }

    /**
     * Waits, within {@link RealProject#RUN_BOUND}, for a line the stand-in prints -- and fails at
     * once, naming the run's failures, if the run ends first.
     *
     * @param line the line's future, from {@link RealProject.LineSink#when}
     * @param run the run's listener
     * @return the line
     */
    static String awaitLine(CompletableFuture<String> line, Watching run)
            throws InterruptedException, ExecutionException, TimeoutException {
        CompletableFuture.anyOf(line, run.finished())
                .get(RealProject.RUN_BOUND.toSeconds(), TimeUnit.SECONDS);
        if (!line.isDone()) {
            RunResult ended = run.finished().getNow(null);
            throw new AssertionError(
                    "the run ended before the stand-in printed its line: "
                            + (ended == null
                                    ? "no result"
                                    : ended.outcome() + " " + ended.failures()));
        }
        return line.get();
    }

    /**
     * Parses {@code <prefix><pid>} into the live process.
     *
     * @param line the line
     * @param prefix what precedes the pid
     * @return the process
     */
    static ProcessHandle processOf(String line, String prefix) {
        long pid = Long.parseLong(line.substring(prefix.length()).trim());
        return ProcessHandle.of(pid)
                .orElseThrow(() -> new AssertionError("no process " + pid + " from " + line));
    }

    /**
     * Whether a process is still running. A process that has exited but was never reaped -- a
     * zombie, state {@code Z} in {@code /proc/<pid>/stat} -- is not: it runs no code and holds no
     * file. That case is real here, not hypothetical: when Percolator's shell and its child are
     * killed together the child is re-parented to process 1, and process 1 in this project's
     * containers reaps nothing, so {@link ProcessHandle#isAlive} reports the dead child alive for
     * ever (measured 2026-10-23: {@code (sleep) Z 1 ...}).
     *
     * @param pid the process
     * @return {@code true} if it exists and is not a zombie
     */
    static boolean running(long pid) {
        if (!ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false)) {
            return false;
        }
        Path stat = Path.of("/proc", Long.toString(pid), "stat");
        try {
            String text = Files.readString(stat, StandardCharsets.US_ASCII);
            String state = text.substring(text.lastIndexOf(')') + 1).trim();
            return !(state.startsWith("Z") || state.startsWith("X"));
        } catch (IOException gone) {
            // no /proc entry: either it has gone, or this is not Linux and ProcessHandle decides
            return !Files.isDirectory(Path.of("/proc", "self"));
        }
    }

    /** Keeps every transition, in order, and completes a future when the run finishes. */
    static final class Watching implements StepStateListener {

        private final List<StepTransition> seen = new ArrayList<>();

        private final CompletableFuture<RunResult> finished = new CompletableFuture<>();

        @Override
        public void onTransition(StepTransition transition) {
            synchronized (seen) {
                seen.add(transition);
            }
        }

        @Override
        public void onRunFinished(RunResult result) {
            finished.complete(result);
        }

        CompletableFuture<RunResult> finished() {
            return finished;
        }

        /** The states one step went through, first {@code from} included. */
        List<StepState> path(EngineStep step) {
            List<StepState> path = new ArrayList<>();
            synchronized (seen) {
                for (StepTransition change : seen) {
                    if (change.step() == step) {
                        if (path.isEmpty()) {
                            path.add(change.from());
                        }
                        path.add(change.to());
                    }
                }
            }
            return path;
        }
    }

    private static void waitForChild(List<String> lines) {
        lines.add("/bin/sleep " + CHILD_SECONDS + " &");
        lines.add("child=$!");
        lines.add("echo \"child $child\"");
        lines.add("echo \"pid $$\"");
        lines.add("wait \"$child\"");
    }

    private static Path executable(Path file, List<String> lines) throws IOException {
        Files.createDirectories(RealComet.parentOf(file));
        Files.writeString(file, String.join("\n", lines) + "\n", StandardCharsets.US_ASCII);
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rwx------"));
        return file;
    }
}
