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

package org.cometgui.ui.testing;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import org.cometgui.domain.run.RunId;
import org.cometgui.domain.tools.InstallHandle;
import org.cometgui.domain.tools.InstallProgressListener;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.percolator.PercolatorSettings;
import org.cometgui.params.percolator.resolution.CompatibilityAction;
import org.cometgui.ui.viewmodel.limelight.LimelightOptions;
import org.cometgui.ui.viewmodel.limelight.LimelightPort;
import org.cometgui.ui.viewmodel.limelight.LimelightRerun;
import org.cometgui.ui.viewmodel.limelight.LimelightResult;
import org.cometgui.ui.viewmodel.limelight.LimelightRunState;
import org.cometgui.ui.viewmodel.params.RunNotStartedException;
import org.cometgui.ui.viewmodel.params.RunObserver;
import org.cometgui.ui.viewmodel.results.ResultsRun;
import org.cometgui.workflow.limelight.LimelightConverterStatus;

/**
 * A Limelight port a test scripts: what the converter's state is, what a run's check and save
 * answer, what a conversion returns or throws and which log lines it emits, and what a rerun
 * returns -- recording every call, and the install listeners and rerun observers so that the test
 * can drive them.
 */
public class ScriptedLimelight implements LimelightPort {

    /** What a check, a save or a conversion answers. */
    @FunctionalInterface
    public interface Answer<T> {

        /**
         * The answer.
         *
         * @param run the run asked about
         * @return the answer
         * @throws IOException to refuse
         */
        T answer(ResultsRun run) throws IOException;
    }

    /** One install the port started. */
    public record Install(String tool, ToolVersion version, InstallProgressListener listener) {}

    /** One save the port was asked for. */
    public record Save(ResultsRun run, LimelightOptions options, String output) {}

    /** One conversion the port was asked for. */
    public record Conversion(ResultsRun run, LimelightOptions options, String output) {}

    /** One rerun the port was asked for. */
    public record Rerun(
            ResultsRun run,
            CompatibilityAction action,
            PercolatorSettings settings,
            RunObserver observer) {}

    private LimelightConverterStatus converter;

    private Answer<LimelightRunState> check;

    private Answer<LimelightRunState> save;

    private Answer<LimelightResult> convert;

    private List<String> logLines = List.of();

    private final Deque<RunNotStartedException> rerunRefusals = new ArrayDeque<>();

    private final Deque<IOException> installRefusals = new ArrayDeque<>();

    private final List<Install> installs = new ArrayList<>();

    private final List<String> checks = new ArrayList<>();

    private final List<Save> saves = new ArrayList<>();

    private final List<Conversion> conversions = new ArrayList<>();

    private final List<Rerun> reruns = new ArrayList<>();

    private int cancels;

    private int rerunCancels;

    /**
     * A port with no converter and no run that can be read, both saying why.
     *
     * @param why the reason
     */
    public ScriptedLimelight(String why) {
        this.converter = LimelightConverterStatus.unavailable(why);
        this.check =
                run -> {
                    throw new IOException(why);
                };
        this.save = check;
        this.convert =
                run -> {
                    throw new IOException(why);
                };
    }

    /**
     * Sets the converter's state.
     *
     * @param status the state
     */
    public void converter(LimelightConverterStatus status) {
        this.converter = Objects.requireNonNull(status, "status");
    }

    /**
     * Sets what a check answers.
     *
     * @param answer the answer
     */
    public void check(Answer<LimelightRunState> answer) {
        this.check = Objects.requireNonNull(answer, "answer");
    }

    /**
     * Sets what a save answers.
     *
     * @param answer the answer
     */
    public void save(Answer<LimelightRunState> answer) {
        this.save = Objects.requireNonNull(answer, "answer");
    }

    /**
     * Sets what a conversion answers, and the log lines it emits first.
     *
     * @param answer the answer
     * @param lines the converter's log lines
     */
    public void convert(Answer<LimelightResult> answer, String... lines) {
        this.convert = Objects.requireNonNull(answer, "answer");
        this.logLines = List.of(lines);
    }

    /**
     * Makes the next install refused.
     *
     * @param refusal what it throws
     */
    public void refuseNextInstall(IOException refusal) {
        installRefusals.add(refusal);
    }

    /**
     * Makes the next rerun refused.
     *
     * @param refusal what it throws
     */
    public void refuseNextRerun(RunNotStartedException refusal) {
        rerunRefusals.add(refusal);
    }

    @Override
    public LimelightConverterStatus converter() {
        return converter;
    }

    @Override
    public InstallHandle installConverter(ToolVersion version, InstallProgressListener listener)
            throws IOException {
        return install("limelight-converter", version, listener);
    }

    @Override
    public InstallHandle installPercolator(ToolVersion version, InstallProgressListener listener)
            throws IOException {
        return install("percolator", version, listener);
    }

    private InstallHandle install(
            String tool, ToolVersion version, InstallProgressListener listener) throws IOException {
        IOException refusal = installRefusals.pollFirst();
        if (refusal != null) {
            throw refusal;
        }
        installs.add(new Install(tool, version, listener));
        return () -> cancels++;
    }

    @Override
    public LimelightRunState check(ResultsRun run, String output) throws IOException {
        checks.add(run.id().value() + "|" + output);
        return check.answer(run);
    }

    @Override
    public LimelightRunState save(ResultsRun run, LimelightOptions options, String output)
            throws IOException {
        saves.add(new Save(run, options, output));
        return save.answer(run);
    }

    @Override
    public LimelightResult convert(
            ResultsRun run, LimelightOptions options, String output, Consumer<String> log)
            throws IOException {
        conversions.add(new Conversion(run, options, output));
        for (String line : logLines) {
            log.accept(line);
        }
        return convert.answer(run);
    }

    @Override
    public LimelightRerun rerun(
            ResultsRun run,
            CompatibilityAction action,
            PercolatorSettings settings,
            RunObserver observer)
            throws RunNotStartedException {
        RunNotStartedException refusal = rerunRefusals.pollFirst();
        if (refusal != null) {
            throw refusal;
        }
        reruns.add(new Rerun(run, action, settings, observer));
        return new LimelightRerun(
                new RunId("run-derived"),
                Path.of("project/runs/run-derived").toAbsolutePath(),
                () -> rerunCancels++);
    }

    /**
     * The installs started, oldest first.
     *
     * @return the installs
     */
    public List<Install> installs() {
        return List.copyOf(installs);
    }

    /**
     * The checks asked for, as {@code run|output}, oldest first.
     *
     * @return the checks
     */
    public List<String> checks() {
        return List.copyOf(checks);
    }

    /**
     * The saves asked for, oldest first.
     *
     * @return the saves
     */
    public List<Save> saves() {
        return List.copyOf(saves);
    }

    /**
     * The conversions asked for, oldest first.
     *
     * @return the conversions
     */
    public List<Conversion> conversions() {
        return List.copyOf(conversions);
    }

    /**
     * The reruns asked for, oldest first.
     *
     * @return the reruns
     */
    public List<Rerun> reruns() {
        return List.copyOf(reruns);
    }

    /**
     * How many install cancellations reached the port.
     *
     * @return the count
     */
    public int cancels() {
        return cancels;
    }

    /**
     * How many rerun cancellations reached the port.
     *
     * @return the count
     */
    public int rerunCancels() {
        return rerunCancels;
    }
}
