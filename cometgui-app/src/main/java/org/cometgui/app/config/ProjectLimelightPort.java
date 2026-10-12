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

package org.cometgui.app.config;

import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.cometgui.domain.log.LogMessage;
import org.cometgui.domain.run.RunId;
import org.cometgui.domain.tools.InstallHandle;
import org.cometgui.domain.tools.InstallProgressListener;
import org.cometgui.domain.tools.ToolManager;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.percolator.PercolatorSettings;
import org.cometgui.params.percolator.resolution.CompatibilityAction;
import org.cometgui.tools.limelight.LimelightQValueCutoff;
import org.cometgui.tools.process.RunMessageSink;
import org.cometgui.ui.viewmodel.limelight.LimelightOptions;
import org.cometgui.ui.viewmodel.limelight.LimelightPort;
import org.cometgui.ui.viewmodel.limelight.LimelightRerun;
import org.cometgui.ui.viewmodel.limelight.LimelightResult;
import org.cometgui.ui.viewmodel.limelight.LimelightRunState;
import org.cometgui.ui.viewmodel.params.RunNotStartedException;
import org.cometgui.ui.viewmodel.params.RunObserver;
import org.cometgui.ui.viewmodel.results.ResultsRun;
import org.cometgui.workflow.limelight.LimelightCompatibility;
import org.cometgui.workflow.limelight.LimelightConversions;
import org.cometgui.workflow.limelight.LimelightConverted;
import org.cometgui.workflow.limelight.LimelightConverterStatus;
import org.cometgui.workflow.limelight.LimelightProvisioning;
import org.cometgui.workflow.storage.LimelightSettings;
import org.cometgui.workflow.storage.LimelightSettingsStore;

/**
 * The Limelight section's port over the Tool Manager, the session's project, the session's engine
 * and {@link LimelightConversions} -- the one conversion path (phase 12, work unit 6; {@link
 * LimelightPort}).
 *
 * <p>The converter's state and its install are {@link LimelightProvisioning}'s over the one Tool
 * Manager. Whether a run can convert, every refusal, the settings' rules and the conversion are
 * {@link LimelightConversions}'; a run's settings are its {@code results/limelight-settings.json}.
 * The compatible rerun is the session engine's ({@link SessionEngine#rerunCompatible}), which is
 * Phase 09's rerun. This class only finds the run, turns the section's text into the workflow's
 * values with the workflow's own rules, refuses a run that is executing, and routes the converter's
 * log lines to the conversion that asked for them.
 *
 * <p>Every method may block and is called off the JavaFX thread.
 */
final class ProjectLimelightPort implements LimelightPort {

    private final ProjectSession project;

    private final Supplier<Optional<ToolManager>> tools;

    private final String toolsUnavailable;

    private final Optional<LimelightConversions> conversions;

    private final String noConversions;

    private final SessionEngine engine;

    private final ConverterLines lines;

    /**
     * The port.
     *
     * @param project the session's project
     * @param tools the Tool Manager, or empty
     * @param toolsUnavailable why there is no Tool Manager, when {@code tools} is empty
     * @param conversions the one conversion path; empty when this application can launch nothing
     * @param noConversions why nothing can be converted, when {@code conversions} is empty
     * @param engine the session's engine: its executing runs and the compatible rerun
     * @param lines the sink {@code conversions} writes the converter's log lines to
     */
    ProjectLimelightPort(
            ProjectSession project,
            Supplier<Optional<ToolManager>> tools,
            String toolsUnavailable,
            Optional<LimelightConversions> conversions,
            String noConversions,
            SessionEngine engine,
            ConverterLines lines) {
        this.project = Objects.requireNonNull(project, "project");
        this.tools = Objects.requireNonNull(tools, "tools");
        this.toolsUnavailable = Objects.requireNonNull(toolsUnavailable, "toolsUnavailable");
        this.conversions = Objects.requireNonNull(conversions, "conversions");
        this.noConversions = Objects.requireNonNull(noConversions, "noConversions");
        this.engine = Objects.requireNonNull(engine, "engine");
        this.lines = Objects.requireNonNull(lines, "lines");
    }

    @Override
    public LimelightConverterStatus converter() {
        return tools.get()
                .map(manager -> new LimelightProvisioning(manager).status())
                .orElseGet(() -> LimelightConverterStatus.unavailable(toolsUnavailable));
    }

    @Override
    public InstallHandle installConverter(ToolVersion version, InstallProgressListener listener)
            throws IOException {
        ToolManager manager = tools.get().orElseThrow(() -> new IOException(toolsUnavailable));
        try {
            return new LimelightProvisioning(manager).install(version, listener);
        } catch (IllegalArgumentException notOffered) {
            throw new IOException(notOffered.getMessage(), notOffered);
        }
    }

    @Override
    public InstallHandle installPercolator(ToolVersion version, InstallProgressListener listener)
            throws IOException {
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(listener, "listener");
        ToolManager manager = tools.get().orElseThrow(() -> new IOException(toolsUnavailable));
        try {
            return manager.install(ToolName.PERCOLATOR, version, listener);
        } catch (IllegalArgumentException notOffered) {
            throw new IOException(notOffered.getMessage(), notOffered);
        }
    }

    @Override
    public LimelightRunState check(ResultsRun run, String output) throws IOException {
        Objects.requireNonNull(run, "run");
        Objects.requireNonNull(output, "output");
        LimelightConversions converting = conversions();
        ProjectRuns.Located located = ProjectRuns.locate(project, run);
        LimelightSettings settings = LimelightSettingsStore.read(located.layout());
        return new LimelightRunState(
                converting.check(
                        located.layout(), located.identity(), settings, outputPath(output)),
                options(settings));
    }

    @Override
    public LimelightRunState save(ResultsRun run, LimelightOptions options, String output)
            throws IOException {
        Objects.requireNonNull(run, "run");
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(output, "output");
        LimelightConversions converting = conversions();
        refuseExecuting(run.id());
        ProjectRuns.Located located = ProjectRuns.locate(project, run);
        LimelightSettings settings = settings(options);
        converting.saveSettings(located.layout(), settings);
        return check(run, output);
    }

    @Override
    public LimelightResult convert(
            ResultsRun run, LimelightOptions options, String output, Consumer<String> log)
            throws IOException {
        Objects.requireNonNull(run, "run");
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(log, "log");
        LimelightConversions converting = conversions();
        refuseExecuting(run.id());
        ProjectRuns.Located located = ProjectRuns.locate(project, run);
        LimelightSettings settings = settings(options);
        Optional<Path> target = outputPath(output);
        converting.saveSettings(located.layout(), settings);
        LimelightConverted converted;
        synchronized (this) {
            lines.listen(log);
            try {
                converted =
                        converting.convert(located.layout(), located.identity(), settings, target);
            } finally {
                lines.listen(null);
            }
        }
        return new LimelightResult(
                converted.file(),
                converted.output().psmCount(),
                converted.output().reportedPeptideCount(),
                converted.hashes().md5(),
                converted.hashes().sha256(),
                converted.output().psmQValueDefault().orElse(settings.qCutoff().argument()),
                converted.log());
    }

    @Override
    public LimelightRerun rerun(
            ResultsRun run,
            CompatibilityAction action,
            PercolatorSettings settings,
            RunObserver observer)
            throws RunNotStartedException {
        Objects.requireNonNull(run, "run");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(observer, "observer");
        if (engine.executingRuns().contains(run.id())) {
            throw new RunNotStartedException(executing(run.id()), null);
        }
        ProjectRuns.Located located;
        LimelightCompatibility now;
        try {
            LimelightConversions converting = conversions();
            located = ProjectRuns.locate(project, run);
            now = converting.compatibility(located.layout(), located.identity());
        } catch (IOException unreadable) {
            throw new RunNotStartedException(
                    "Whether run "
                            + run.id().value()
                            + " can be rerun could not be read: "
                            + unreadable.getMessage(),
                    unreadable);
        }
        Optional<CompatibilityAction> offered = now.action();
        if (offered.isEmpty()
                || !offered.get().offersRerun()
                || offered.get().installFirst()
                || !offered.get().label().equals(action.label())) {
            throw new RunNotStartedException(
                    "The action offered for run "
                            + run.id().value()
                            + " is no longer \""
                            + action.label()
                            + "\" with an installed build: "
                            + offered.map(CompatibilityAction::label)
                                    .orElse(now.explanation().orElse("it can be converted"))
                            + ". Check again.",
                    null);
        }
        return engine.rerunCompatible(located.layout(), offered.get(), settings, observer);
    }

    private LimelightConversions conversions() throws IOException {
        return conversions.orElseThrow(() -> new IOException(noConversions));
    }

    private void refuseExecuting(RunId run) throws IOException {
        if (engine.executingRuns().contains(run)) {
            throw new IOException(executing(run));
        }
    }

    private static String executing(RunId run) {
        return "run "
                + run.value()
                + " is executing: its results are not final and the workflow engine holds its"
                + " event log. Convert it when the run has ended.";
    }

    /** The settings as the section shows them: the cutoff as the converter is given it. */
    private static LimelightOptions options(LimelightSettings settings) {
        return new LimelightOptions(
                settings.qCutoff().argument(),
                settings.importDecoys(),
                settings.independentDecoyPrefix().orElse(""),
                settings.openMod());
    }

    /** The section's text as the workflow's settings, by the workflow's own rule for the cutoff. */
    private static LimelightSettings settings(LimelightOptions options) throws IOException {
        LimelightQValueCutoff cutoff;
        try {
            cutoff = LimelightSettings.parseCutoff(options.qCutoff().strip());
        } catch (IllegalArgumentException refused) {
            throw new IOException(
                    "\"" + options.qCutoff() + "\" was not accepted: " + refused.getMessage(),
                    refused);
        }
        String prefix = options.decoyPrefix().strip();
        return new LimelightSettings(
                cutoff,
                options.importDecoys(),
                prefix.isEmpty() ? Optional.empty() : Optional.of(prefix),
                options.openMod());
    }

    /** The output as typed: blank for a new file in the run's outputs, otherwise a path. */
    private static Optional<Path> outputPath(String output) throws IOException {
        String typed = output.strip();
        if (typed.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Path.of(typed));
        } catch (InvalidPathException notAPath) {
            throw new IOException(
                    "the output \""
                            + typed
                            + "\" is not a path on this computer: "
                            + notAPath.getReason(),
                    notAPath);
        }
    }

    /**
     * Where the conversion path writes the converter's log lines: the console, always, and the
     * conversion the Limelight section is waiting on, while it runs.
     */
    static final class ConverterLines implements RunMessageSink {

        private final RunMessageSink console;

        private volatile Consumer<String> listener;

        /**
         * Lines that go to the console, and to a listener while one is set.
         *
         * @param console the console's log, as a method reference
         */
        ConverterLines(RunMessageSink console) {
            this.console = Objects.requireNonNull(console, "console");
        }

        void listen(Consumer<String> now) {
            listener = now;
        }

        @Override
        public void append(LogMessage message) {
            console.append(message);
            Consumer<String> current = listener;
            if (current != null) {
                current.accept(message.text());
            }
        }
    }
}
