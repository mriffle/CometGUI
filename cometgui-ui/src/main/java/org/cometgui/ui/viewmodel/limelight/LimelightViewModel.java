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

package org.cometgui.ui.viewmodel.limelight;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.value.ObservableValue;
import org.cometgui.domain.run.AttemptOutcome;
import org.cometgui.domain.run.RunId;
import org.cometgui.domain.tools.InstallHandle;
import org.cometgui.domain.tools.InstallPhase;
import org.cometgui.domain.tools.InstallProgress;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.percolator.PercolatorSettings;
import org.cometgui.params.percolator.resolution.CompatibilityAction;
import org.cometgui.ui.viewmodel.NonNullProperty;
import org.cometgui.ui.viewmodel.ToolRowViewModel;
import org.cometgui.ui.viewmodel.params.ActiveRun;
import org.cometgui.ui.viewmodel.params.PercolatorRequest;
import org.cometgui.ui.viewmodel.params.RunNotStartedException;
import org.cometgui.ui.viewmodel.params.RunObserver;
import org.cometgui.ui.viewmodel.results.ResultsRun;
import org.cometgui.ui.viewmodel.results.ResultsViewModel;
import org.cometgui.workflow.engine.RunResult;
import org.cometgui.workflow.engine.StepTransition;
import org.cometgui.workflow.limelight.LimelightCompatibility;
import org.cometgui.workflow.limelight.LimelightConversionRefusedException;
import org.cometgui.workflow.limelight.LimelightConverterStatus;
import org.cometgui.workflow.limelight.LimelightPrerequisite;
import org.cometgui.workflow.limelight.LimelightReadiness;
import org.cometgui.workflow.limelight.LimelightRefusal;
import org.cometgui.workflow.state.EngineStep;
import org.cometgui.workflow.state.Plan;

/**
 * The Limelight section's conversion half (phase 12, work unit 6; {@code R-LL-01}..{@code
 * R-LL-05}): for the run chosen in the Results section, whether it can be converted -- shown before
 * anything can be attempted -- the action offered when it cannot, the converter's settings, and the
 * conversion with its live log and its validated result.
 *
 * <h2>One selection</h2>
 *
 * <p>The run is the Results section's {@link ResultsViewModel#chosenRunProperty()}. This section
 * keeps no selection of its own; choosing another run there is choosing it here. A compatible rerun
 * started here makes the run it creates the Results section's run when it has finished, so that the
 * run that can now be converted is the one shown.
 *
 * <h2>Compatibility first ({@code R-LL-01}, {@code R-LL-02}, gate item 3)</h2>
 *
 * <p>Until the port has answered for the chosen run, and whenever that answer is that the run's own
 * Percolator wrote no XML the converter reads, or that the run is executing, every conversion
 * control is disabled ({@link #settingsEnabledProperty()}, {@link #decoyEnabledProperty()}, {@link
 * #convertEnabledProperty()}) and the workflow's explanation is shown ({@link
 * #compatibilityTextProperty()}). The action offered is the workflow's {@link CompatibilityAction}:
 * its label is shown exactly as the action gives it ({@link #actionTextProperty()}) -- no label is
 * written here -- and pressing it ({@link #act()}) either starts Phase 09's compatible rerun
 * through the port, installing the build through the Tool Manager first when it is not installed,
 * or, where no XML-capable build can be obtained here, starts the Percolator section's one
 * local-binary registration.
 *
 * <h2>The converter's settings</h2>
 *
 * <p>The Limelight q-value cutoff, decoy import, the independent-decoy prefix and open-modification
 * mode are the run's own Limelight settings, saved through the port, which checks each with the
 * workflow's rule; a refusal is shown in {@link #settingsStatusProperty()} and blocks the
 * conversion until it is corrected. The cutoff is never a display filter: nothing here reads or
 * writes one (design decision P12-6). Decoy import is offered only when the workflow says the run's
 * own Percolator output allows it ({@link LimelightReadiness#decoyImportUnavailable()}); otherwise
 * its control is disabled with the workflow's explanation, and {@link #setImportDecoys(boolean)}
 * refuses (P12-7: at configuration time). The FASTA and the pepXML directory are shown read-only,
 * as the workflow resolved them.
 *
 * <h2>Converting</h2>
 *
 * <p>{@link #convert()} with the converter not installed installs it through the Tool Manager --
 * cancellable, on the Tool Manager's own threads, holding up no search -- and then converts. The
 * conversion runs on the {@code background} executor; each line the converter logs is shown as it
 * arrives ({@link #logProperty()}); the result is the workflow's: valid Limelight XML with its
 * path, PSM count and recorded SHA-256, or every refusal by name, or the output judge's reason.
 *
 * <h2>Two threads</h2>
 *
 * <p>The port is called only on {@code background}; every answer, install report, log line and
 * rerun transition is applied on {@code ui}.
 */
public final class LimelightViewModel {

    /** The converter's state before it has been read. */
    public static final String NOT_READ = "The Limelight converter's state has not been read yet.";

    /** The run text when no run is open. */
    public static final String NO_RUN = "No run's results are open in the Results section.";

    /** The compatibility text while the chosen run is being read. */
    public static final String CHECKING =
            "Checking whether this run can be converted to Limelight XML.";

    /** The outcome before anything was converted. */
    public static final String NOTHING_CONVERTED =
            "No run has been converted to Limelight XML in this session.";

    /** The refusals text when every prerequisite is met. */
    public static final String EVERY_PREREQUISITE_MET =
            "Every prerequisite of the conversion is met.";

    /** The decoy text when decoy import can be chosen. */
    public static final String DECOYS_AVAILABLE =
            "This run's Percolator output holds decoy PSMs, so they can be imported.";

    /** The action outcome before any action was taken. */
    public static final String NO_ACTION_TAKEN = "";

    /** The action outcome after a registration was started. */
    public static final String REGISTERING =
            "Choose the Percolator executable to register. The Percolator section registers it --"
                    + " running it to read its version and probe what it can do -- and shows the"
                    + " outcome; this section checks the run again when the builds are read again.";

    /** The action outcome when no registration could start. */
    public static final String NOT_REGISTERING =
            "No Percolator binary was registered. The Percolator section's registration status"
                    + " says why.";

    /** How many of the converter's log lines are kept on screen. */
    public static final int LOG_LINES = 500;

    private final LimelightPort port;

    private final ResultsViewModel results;

    private final ObservableValue<PercolatorRequest> percolator;

    private final BooleanSupplier registerLocal;

    private final Executor background;

    private final Executor ui;

    private final Runnable installEnded;

    // ---- state, on the interface thread ----

    private LimelightConverterStatus converter;

    private long converterGeneration;

    private InstallHandle install;

    /** Whether an install this section started has not yet reported its end. */
    private boolean installActive;

    /** What that install is of; null before the first. */
    private Installing installingWhat;

    private Optional<ResultsRun> run = Optional.empty();

    /** The workflow's answer for {@link #run}; null until it is read. */
    private LimelightReadiness readiness;

    /** Why the chosen run's state could not be read; null when it was, or is being read. */
    private String readFailure;

    /** Every answer older than this is dropped. */
    private long checkGeneration;

    /** The settings as shown; null until the run's are read. */
    private LimelightOptions options;

    /** The settings as last saved; null until the run's are read. */
    private LimelightOptions saved;

    /** Why the settings shown are not the saved ones; empty when they are. */
    private String settingsProblem = "";

    private String output = "";

    private boolean converting;

    private long conversionGeneration;

    private final Deque<String> logLines = new ArrayDeque<>();

    private boolean rerunning;

    private Object rerunAttempt;

    private ActiveRun rerunHandle;

    private RunId rerunRun;

    private String rerunDescription = "";

    private Plan rerunPlan;

    /** A derived run to choose in the Results section as soon as it is listed there. */
    private RunId awaited;

    // ---- published ----

    private final NonNullProperty<String> runText;

    private final NonNullProperty<String> converterText;

    private final ReadOnlyBooleanWrapper installing;

    private final ReadOnlyDoubleWrapper installProgress;

    private final NonNullProperty<String> compatibilityText;

    private final ReadOnlyBooleanWrapper convertible;

    private final ReadOnlyBooleanWrapper actionVisible;

    private final NonNullProperty<String> actionText;

    private final NonNullProperty<String> actionExplanation;

    private final ReadOnlyBooleanWrapper actionEnabled;

    private final NonNullProperty<String> actionOutcome;

    private final ReadOnlyBooleanWrapper rerunCancelEnabled;

    private final ReadOnlyBooleanWrapper settingsEnabled;

    private final NonNullProperty<String> qCutoffText;

    private final NonNullProperty<String> outputText;

    private final NonNullProperty<String> outputStatus;

    private final ReadOnlyBooleanWrapper importDecoys;

    private final ReadOnlyBooleanWrapper decoyEnabled;

    private final NonNullProperty<String> decoyText;

    private final NonNullProperty<String> decoyPrefixText;

    private final ReadOnlyBooleanWrapper openMod;

    private final NonNullProperty<String> settingsStatus;

    private final NonNullProperty<String> fastaText;

    private final NonNullProperty<String> pepXmlText;

    private final NonNullProperty<String> refusalsText;

    private final NonNullProperty<String> convertRefusal;

    private final ReadOnlyBooleanWrapper convertEnabled;

    private final NonNullProperty<String> log;

    private final NonNullProperty<String> outcome;

    private final ReadOnlyBooleanWrapper valid;

    private final NonNullProperty<String> resultFile;

    private final NonNullProperty<String> resultPsms;

    private final NonNullProperty<String> resultSha256;

    /** What an install this section started does when it ends. */
    private interface AfterInstall {

        /** The port refused to start it. */
        void refused(String why);

        /** It started. */
        void started(ToolVersion version);

        /** It finished; what it was for continues. */
        void done();

        /** It was cancelled or failed. */
        void ended(InstallPhase phase);
    }

    /** What is being installed: where its progress is said. */
    private enum Installing {
        CONVERTER,
        PERCOLATOR
    }

    /**
     * The section, with nothing read. Nothing is read until {@link #refresh()} or a run is chosen.
     *
     * @param port the application's Limelight conversion
     * @param results the Results section, whose chosen run this section acts on
     * @param percolator the Percolator section's request, whose settings a compatible rerun runs
     *     with -- as Phase 09's rerun does
     * @param registerLocal starts the Percolator section's local-binary registration; {@code false}
     *     when none could start
     * @param background where the port is called: never the interface thread
     * @param ui where every answer is applied: the interface thread
     * @param installEnded called on the interface thread when an install this section started has
     *     ended, however it ended -- the composition root reads the Tool Manager again
     */
    public LimelightViewModel(
            LimelightPort port,
            ResultsViewModel results,
            ObservableValue<PercolatorRequest> percolator,
            BooleanSupplier registerLocal,
            Executor background,
            Executor ui,
            Runnable installEnded) {
        this.port = Objects.requireNonNull(port, "port");
        this.results = Objects.requireNonNull(results, "results");
        this.percolator = Objects.requireNonNull(percolator, "percolator");
        this.registerLocal = Objects.requireNonNull(registerLocal, "registerLocal");
        this.background = Objects.requireNonNull(background, "background");
        this.ui = Objects.requireNonNull(ui, "ui");
        this.installEnded = Objects.requireNonNull(installEnded, "installEnded");
        runText = new NonNullProperty<>(this, "runText", NO_RUN);
        converterText = new NonNullProperty<>(this, "converterText", NOT_READ);
        installing = new ReadOnlyBooleanWrapper(this, "installing", false);
        installProgress = new ReadOnlyDoubleWrapper(this, "installProgress", 0);
        compatibilityText = new NonNullProperty<>(this, "compatibilityText", NO_RUN);
        convertible = new ReadOnlyBooleanWrapper(this, "convertible", false);
        actionVisible = new ReadOnlyBooleanWrapper(this, "actionVisible", false);
        actionText = new NonNullProperty<>(this, "actionText", "");
        actionExplanation = new NonNullProperty<>(this, "actionExplanation", "");
        actionEnabled = new ReadOnlyBooleanWrapper(this, "actionEnabled", false);
        actionOutcome = new NonNullProperty<>(this, "actionOutcome", NO_ACTION_TAKEN);
        rerunCancelEnabled = new ReadOnlyBooleanWrapper(this, "rerunCancelEnabled", false);
        settingsEnabled = new ReadOnlyBooleanWrapper(this, "settingsEnabled", false);
        qCutoffText = new NonNullProperty<>(this, "qCutoffText", "");
        outputText = new NonNullProperty<>(this, "outputText", "");
        outputStatus = new NonNullProperty<>(this, "outputStatus", "");
        importDecoys = new ReadOnlyBooleanWrapper(this, "importDecoys", false);
        decoyEnabled = new ReadOnlyBooleanWrapper(this, "decoyEnabled", false);
        decoyText = new NonNullProperty<>(this, "decoyText", "");
        decoyPrefixText = new NonNullProperty<>(this, "decoyPrefixText", "");
        openMod = new ReadOnlyBooleanWrapper(this, "openMod", false);
        settingsStatus = new NonNullProperty<>(this, "settingsStatus", "");
        fastaText = new NonNullProperty<>(this, "fastaText", "");
        pepXmlText = new NonNullProperty<>(this, "pepXmlText", "");
        refusalsText = new NonNullProperty<>(this, "refusalsText", "");
        convertRefusal = new NonNullProperty<>(this, "convertRefusal", NO_RUN);
        convertEnabled = new ReadOnlyBooleanWrapper(this, "convertEnabled", false);
        log = new NonNullProperty<>(this, "log", "");
        outcome = new NonNullProperty<>(this, "outcome", NOTHING_CONVERTED);
        valid = new ReadOnlyBooleanWrapper(this, "valid", false);
        resultFile = new NonNullProperty<>(this, "resultFile", "");
        resultPsms = new NonNullProperty<>(this, "resultPsms", "");
        resultSha256 = new NonNullProperty<>(this, "resultSha256", "");
        results.chosenRunProperty().addListener((observable, before, after) -> runChanged(after));
        results.runsProperty().addListener((observable, before, after) -> chooseAwaited(after));
        percolator.addListener((observable, before, after) -> publish());
        runChanged(results.chosenRunProperty().get());
    }

    // =========================================================================== actions ====

    /**
     * Reads the converter's state and the chosen run's again, on the background executor. The
     * composition root calls this at start and whenever the Tool Manager's rows are read again --
     * after an install or a registration -- and the section's own "check again" control does.
     */
    public void refresh() {
        converterGeneration++;
        long mine = converterGeneration;
        background.execute(
                () -> {
                    LimelightConverterStatus answer = readConverter();
                    ui.execute(() -> converterRead(mine, answer));
                });
        if (run.isPresent() && !converting && !rerunning) {
            check();
        }
    }

    /**
     * Commits the Limelight q-value cutoff as typed: saved through the port, which checks it with
     * the workflow's rule. Ignored while the settings cannot be edited.
     *
     * @param text the cutoff as typed
     */
    public void editCutoff(String text) {
        Objects.requireNonNull(text, "text");
        if (editable() && (!text.equals(options.qCutoff()) || !settingsProblem.isEmpty())) {
            save(options.withQCutoff(text));
        }
    }

    /**
     * Commits the independent-decoy prefix as typed; blank for none.
     *
     * @param text the prefix as typed
     */
    public void editDecoyPrefix(String text) {
        Objects.requireNonNull(text, "text");
        if (editable() && (!text.equals(options.decoyPrefix()) || !settingsProblem.isEmpty())) {
            save(options.withDecoyPrefix(text));
        }
    }

    /**
     * Switches open-modification mode.
     *
     * @param on whether to convert in open-modification mode
     */
    public void setOpenMod(boolean on) {
        if (editable() && on != options.openMod()) {
            save(options.withOpenMod(on));
        } else {
            openMod.set(options != null && options.openMod());
        }
    }

    /**
     * Switches decoy import. Refused, with the workflow's explanation, unless the run's own
     * Percolator output allows it (P12-7): the control is disabled then, and this is the second
     * door.
     *
     * @param on whether to import decoys
     * @return {@code true} if the choice is being saved
     */
    public boolean setImportDecoys(boolean on) {
        if (!editable() || (on && !decoyEnabled.get())) {
            importDecoys.set(options != null && options.importDecoys());
            if (on && readiness != null) {
                settingsStatus.set(
                        "Decoy import was not selected: "
                                + readiness
                                        .decoyImportUnavailable()
                                        .orElse(
                                                "the settings cannot be changed now: "
                                                        + convertRefusal.get()));
            }
            return false;
        }
        if (on == options.importDecoys()) {
            return false;
        }
        save(options.withImportDecoys(on));
        return true;
    }

    /**
     * Commits the output path as typed: blank for a new file under the run's {@code
     * outputs/limelight/}. The workflow checks it again.
     *
     * @param text the path as typed
     */
    public void editOutput(String text) {
        Objects.requireNonNull(text, "text");
        if (!editable() || text.equals(output)) {
            return;
        }
        output = text;
        outputText.set(text);
        check();
    }

    /**
     * Converts the chosen run: at once when the converter is installed, after installing it when it
     * is not. Refused, with the reason in {@link #outcomeProperty()}, when {@link
     * #convertRefusalProperty()} is not empty.
     *
     * @return {@code true} if an install or a conversion started
     */
    public boolean convert() {
        String refusal = convertRefusal.get();
        if (!refusal.isEmpty()) {
            outcome.set("Nothing was converted: " + refusal);
            return false;
        }
        ResultsRun target = run.orElseThrow();
        LimelightOptions using = options;
        String to = output;
        if (converter.state() == LimelightConverterStatus.State.INSTALLED) {
            convertNow(target, using, to);
            return true;
        }
        return install(
                Installing.CONVERTER,
                converter.version().orElseThrow(),
                new AfterInstall() {
                    @Override
                    public void refused(String why) {
                        outcome.set(
                                "The Limelight converter could not be installed, so nothing was"
                                        + " converted: "
                                        + why);
                    }

                    @Override
                    public void started(ToolVersion version) {
                        outcome.set(
                                "The Limelight converter "
                                        + version.text()
                                        + " is being installed; run "
                                        + target.id().value()
                                        + " is converted when it has finished.");
                    }

                    @Override
                    public void done() {
                        converting = true;
                        outcome.set(
                                "The Limelight converter is installed; converting run "
                                        + target.id().value()
                                        + ".");
                        publish();
                        background.execute(
                                () -> {
                                    LimelightConverterStatus read = readConverter();
                                    ui.execute(() -> installedThenConvert(read, target, using, to));
                                });
                    }

                    @Override
                    public void ended(InstallPhase phase) {
                        outcome.set(
                                phase == InstallPhase.CANCELLED
                                        ? "The Limelight converter's install was cancelled, so"
                                                + " nothing was converted."
                                        : "The Limelight converter could not be installed, so"
                                                + " nothing was converted. The Tool Manager"
                                                + " section shows the attempt.");
                    }
                });
    }

    /**
     * Takes the action offered for a run whose Percolator wrote no XML: Phase 09's compatible rerun
     * -- installing its build through the Tool Manager first when it is not installed -- or the
     * Percolator section's local-binary registration.
     *
     * @return {@code true} if an install, a rerun or a registration started
     */
    public boolean act() {
        if (!actionEnabled.get()) {
            return false;
        }
        CompatibilityAction action = readiness.compatibility().action().orElseThrow();
        if (!action.offersRerun()) {
            boolean started = registerLocal.getAsBoolean();
            actionOutcome.set(started ? REGISTERING : NOT_REGISTERING);
            return started;
        }
        PercolatorSettings settings = percolator.getValue().settings().orElseThrow();
        ResultsRun source = run.orElseThrow();
        if (!action.installFirst()) {
            rerunNow(source, action, settings);
            return true;
        }
        return install(
                Installing.PERCOLATOR,
                action.build().orElseThrow().version(),
                new AfterInstall() {
                    @Override
                    public void refused(String why) {
                        actionOutcome.set(
                                "Percolator could not be installed, so it was not rerun: " + why);
                    }

                    @Override
                    public void started(ToolVersion version) {
                        actionOutcome.set(
                                "Installing Percolator "
                                        + version.text()
                                        + ": starting. Percolator is rerun for run "
                                        + source.id().value()
                                        + " when it has finished.");
                    }

                    @Override
                    public void done() {
                        rerunning = true;
                        actionOutcome.set(
                                "Percolator is installed; checking run "
                                        + source.id().value()
                                        + " again before the rerun.");
                        publish();
                        background.execute(
                                () -> {
                                    LimelightRunState state = null;
                                    String failure = null;
                                    try {
                                        state = port.check(source, "");
                                    } catch (IOException | RuntimeException failed) {
                                        failure = describe(failed);
                                    }
                                    LimelightRunState answer = state;
                                    String why = failure;
                                    ui.execute(
                                            () ->
                                                    installedThenRerun(
                                                            source, settings, answer, why));
                                });
                    }

                    @Override
                    public void ended(InstallPhase phase) {
                        actionOutcome.set(
                                phase == InstallPhase.CANCELLED
                                        ? "The Percolator install was cancelled, so Percolator was"
                                                + " not rerun."
                                        : "Percolator could not be installed, so it was not rerun."
                                                + " The Tool Manager section shows the attempt.");
                    }
                });
    }

    /** Asks the install this section started to stop. Nothing is converted or rerun then. */
    public void cancelInstall() {
        if (installActive && install != null) {
            install.cancel();
        }
    }

    /**
     * Asks the compatible rerun this section started to stop. Returns at once; the request reaches
     * the engine on the background executor.
     *
     * @return {@code true} if the request was sent
     */
    public boolean cancelRerun() {
        ActiveRun target = rerunHandle;
        if (!rerunCancelEnabled.get() || target == null) {
            return false;
        }
        rerunCancelEnabled.set(false);
        actionOutcome.set("Cancelling the Percolator rerun: " + rerunDescription + ".");
        background.execute(target::cancel);
        return true;
    }

    // ========================================================================= published ====

    /**
     * The run chosen in the Results section.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<String> runTextProperty() {
        return runText.getReadOnlyProperty();
    }

    /**
     * The converter's state, or the running install's progress, in words.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<String> converterTextProperty() {
        return converterText.getReadOnlyProperty();
    }

    /**
     * Whether an install this section started is running, so that it can be cancelled.
     *
     * @return the read-only property
     */
    public ReadOnlyBooleanProperty installingProperty() {
        return installing.getReadOnlyProperty();
    }

    /**
     * The install's progress: 0 to 1 while the total is known, -1 while it is not, 0 when nothing
     * is being installed.
     *
     * @return the read-only property
     */
    public ReadOnlyDoubleProperty installProgressProperty() {
        return installProgress.getReadOnlyProperty();
    }

    /**
     * Whether the chosen run can be converted, in the workflow's words: the explanation when its
     * Percolator wrote no XML the converter reads, or the run is executing.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<String> compatibilityTextProperty() {
        return compatibilityText.getReadOnlyProperty();
    }

    /**
     * Whether the chosen run's Percolator wrote the XML the converter reads, as the workflow last
     * answered; {@code false} until it has answered.
     *
     * @return the read-only property
     */
    public ReadOnlyBooleanProperty convertibleProperty() {
        return convertible.getReadOnlyProperty();
    }

    /**
     * Whether an action is offered: the chosen run cannot be converted because of its Percolator.
     *
     * @return the read-only property
     */
    public ReadOnlyBooleanProperty actionVisibleProperty() {
        return actionVisible.getReadOnlyProperty();
    }

    /**
     * The offered action's label, exactly as the workflow's {@link CompatibilityAction#label()}
     * gives it; empty when none is offered.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<String> actionTextProperty() {
        return actionText.getReadOnlyProperty();
    }

    /**
     * Why that is the action, in the workflow's words.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<String> actionExplanationProperty() {
        return actionExplanation.getReadOnlyProperty();
    }

    /**
     * Whether the offered action can be taken now.
     *
     * @return the read-only property
     */
    public ReadOnlyBooleanProperty actionEnabledProperty() {
        return actionEnabled.getReadOnlyProperty();
    }

    /**
     * What the action is doing, or how it ended: the install, the rerun's steps and outcome, the
     * registration.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<String> actionOutcomeProperty() {
        return actionOutcome.getReadOnlyProperty();
    }

    /**
     * Whether a rerun this section started can still be cancelled.
     *
     * @return the read-only property
     */
    public ReadOnlyBooleanProperty rerunCancelEnabledProperty() {
        return rerunCancelEnabled.getReadOnlyProperty();
    }

    /**
     * Whether the conversion's settings can be edited: the chosen run has been read, its Percolator
     * wrote the XML the converter reads, it is not executing, and nothing is running.
     *
     * @return the read-only property
     */
    public ReadOnlyBooleanProperty settingsEnabledProperty() {
        return settingsEnabled.getReadOnlyProperty();
    }

    /**
     * The Limelight q-value cutoff, as saved -- or as typed, when it was refused.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<String> qCutoffTextProperty() {
        return qCutoffText.getReadOnlyProperty();
    }

    /**
     * The output path as typed; empty for the default.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<String> outputTextProperty() {
        return outputText.getReadOnlyProperty();
    }

    /**
     * Where the next conversion writes, or why it cannot write there.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<String> outputStatusProperty() {
        return outputStatus.getReadOnlyProperty();
    }

    /**
     * Whether decoy import is selected, as saved.
     *
     * @return the read-only property
     */
    public ReadOnlyBooleanProperty importDecoysProperty() {
        return importDecoys.getReadOnlyProperty();
    }

    /**
     * Whether decoy import can be selected: only when the run's own Percolator output allows it.
     *
     * @return the read-only property
     */
    public ReadOnlyBooleanProperty decoyEnabledProperty() {
        return decoyEnabled.getReadOnlyProperty();
    }

    /**
     * Why decoy import cannot be selected, in the workflow's words -- or that it can.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<String> decoyTextProperty() {
        return decoyText.getReadOnlyProperty();
    }

    /**
     * The independent-decoy prefix, as saved or typed.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<String> decoyPrefixTextProperty() {
        return decoyPrefixText.getReadOnlyProperty();
    }

    /**
     * Whether open-modification mode is selected, as saved.
     *
     * @return the read-only property
     */
    public ReadOnlyBooleanProperty openModProperty() {
        return openMod.getReadOnlyProperty();
    }

    /**
     * Whether the settings were saved, or the rule that refused them.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<String> settingsStatusProperty() {
        return settingsStatus.getReadOnlyProperty();
    }

    /**
     * The FASTA the conversion reads, as the workflow resolved it from the run's record: shown, not
     * edited.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<String> fastaTextProperty() {
        return fastaText.getReadOnlyProperty();
    }

    /**
     * The pepXML directory the conversion reads, as the workflow resolved it: shown, not edited.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<String> pepXmlTextProperty() {
        return pepXmlText.getReadOnlyProperty();
    }

    /**
     * Every prerequisite not met, each by its name and the workflow's sentence, one per line.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<String> refusalsTextProperty() {
        return refusalsText.getReadOnlyProperty();
    }

    /**
     * Why the run cannot be converted now.
     *
     * @return the read-only property; empty text when it can
     */
    public ReadOnlyObjectProperty<String> convertRefusalProperty() {
        return convertRefusal.getReadOnlyProperty();
    }

    /**
     * Whether the run can be converted now.
     *
     * @return the read-only property
     */
    public ReadOnlyBooleanProperty convertEnabledProperty() {
        return convertEnabled.getReadOnlyProperty();
    }

    /**
     * The converter's log lines of the last conversion, as they arrived, newest last.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<String> logProperty() {
        return log.getReadOnlyProperty();
    }

    /**
     * What the last conversion did: valid Limelight XML, every refusal by name, the output judge's
     * reason, or the install's end.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<String> outcomeProperty() {
        return outcome.getReadOnlyProperty();
    }

    /**
     * Whether the last conversion produced valid Limelight XML.
     *
     * @return the read-only property
     */
    public ReadOnlyBooleanProperty validProperty() {
        return valid.getReadOnlyProperty();
    }

    /**
     * The last valid Limelight XML file; empty when the last conversion produced none.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<String> resultFileProperty() {
        return resultFile.getReadOnlyProperty();
    }

    /**
     * How many PSMs the last valid file holds.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<String> resultPsmsProperty() {
        return resultPsms.getReadOnlyProperty();
    }

    /**
     * The last valid file's SHA-256, as the conversion's event recorded it.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<String> resultSha256Property() {
        return resultSha256.getReadOnlyProperty();
    }

    // ========================================================================= internals ====

    private boolean editable() {
        return options != null && settingsEnabled.get();
    }

    private boolean busy() {
        return installActive || converting || rerunning;
    }

    private LimelightConverterStatus readConverter() {
        try {
            return port.converter();
        } catch (IOException | RuntimeException failed) {
            return LimelightConverterStatus.unavailable(
                    "The Limelight converter's state could not be read: " + describe(failed));
        }
    }

    private void converterRead(long answered, LimelightConverterStatus read) {
        if (answered != converterGeneration) {
            return;
        }
        converter = read;
        if (!installActive || installingWhat != Installing.CONVERTER) {
            converterText.set(read.explanation());
        }
        publish();
    }

    /** The Results section chose another run, or none, or the same run's state changed. */
    private void runChanged(Optional<ResultsRun> chosen) {
        run = chosen;
        checkGeneration++;
        readiness = null;
        readFailure = null;
        options = null;
        saved = null;
        settingsProblem = "";
        output = "";
        outputText.set("");
        settingsStatus.set("");
        showOptions();
        publish();
        if (chosen.isPresent()) {
            check();
        }
    }

    /** Reads the chosen run's state with its saved settings and the output as typed. */
    private void check() {
        checkGeneration++;
        long mine = checkGeneration;
        ResultsRun target = run.orElseThrow();
        String to = output;
        background.execute(
                () -> {
                    LimelightRunState state = null;
                    String failure = null;
                    try {
                        state = port.check(target, to);
                    } catch (IOException | RuntimeException failed) {
                        failure = describe(failed);
                    }
                    LimelightRunState answer = state;
                    String why = failure;
                    ui.execute(() -> checked(mine, answer, why, false));
                });
    }

    private void save(LimelightOptions wanted) {
        checkGeneration++;
        long mine = checkGeneration;
        ResultsRun target = run.orElseThrow();
        String to = output;
        options = wanted;
        showOptions();
        settingsStatus.set("Saving the Limelight settings of run " + target.id().value() + ".");
        background.execute(
                () -> {
                    LimelightRunState state = null;
                    String failure = null;
                    try {
                        state = port.save(target, wanted, to);
                    } catch (IOException | RuntimeException failed) {
                        failure = describe(failed);
                    }
                    LimelightRunState answer = state;
                    String why = failure;
                    ui.execute(() -> checked(mine, answer, why, true));
                });
    }

    private void checked(long answered, LimelightRunState state, String failure, boolean saving) {
        if (answered != checkGeneration) {
            return;
        }
        if (failure != null) {
            if (saving) {
                settingsProblem = failure;
                options =
                        new LimelightOptions(
                                options.qCutoff(),
                                saved.importDecoys(),
                                options.decoyPrefix(),
                                saved.openMod());
                showOptions();
                settingsStatus.set(
                        "The Limelight settings were not saved, and no conversion starts until"
                                + " they are corrected: "
                                + failure);
            } else {
                readFailure = failure;
            }
            publish();
            return;
        }
        readiness = state.readiness();
        readFailure = null;
        if (saving || settingsProblem.isEmpty() || options == null) {
            saved = state.saved();
            options = saved;
            settingsProblem = "";
            showOptions();
        }
        if (saving) {
            settingsStatus.set(
                    "Saved: the next conversion of run "
                            + readiness.runId().value()
                            + " uses the Limelight q-value cutoff "
                            + saved.qCutoff()
                            + (saved.importDecoys() ? ", imports decoys" : ", imports no decoys")
                            + (saved.decoyPrefix().isBlank()
                                    ? ""
                                    : ", independent-decoy prefix " + saved.decoyPrefix())
                            + (saved.openMod() ? " and open-modification mode." : "."));
        }
        publish();
    }

    private void showOptions() {
        qCutoffText.set(options == null ? "" : options.qCutoff());
        importDecoys.set(options != null && options.importDecoys());
        decoyPrefixText.set(options == null ? "" : options.decoyPrefix());
        openMod.set(options != null && options.openMod());
    }

    /** Starts installing a build; {@code false} if the port refused. */
    private boolean install(Installing what, ToolVersion version, AfterInstall after) {
        installActive = true;
        installingWhat = what;
        installing.set(true);
        installProgress.set(-1);
        progressText(what, version.text(), "starting");
        try {
            install =
                    what == Installing.CONVERTER
                            ? port.installConverter(
                                    version, report -> ui.execute(() -> progressed(report, after)))
                            : port.installPercolator(
                                    version, report -> ui.execute(() -> progressed(report, after)));
        } catch (IOException | RuntimeException refused) {
            installActive = false;
            installing.set(false);
            installProgress.set(0);
            if (what == Installing.CONVERTER && converter != null) {
                converterText.set(converter.explanation());
            }
            after.refused(describe(refused));
            publish();
            return false;
        }
        after.started(version);
        publish();
        return true;
    }

    private void progressText(Installing what, String version, String progress) {
        if (what == Installing.CONVERTER) {
            converterText.set(
                    "Installing the Limelight converter " + version + ": " + progress + ".");
        } else {
            actionOutcome.set("Installing Percolator " + version + ": " + progress + ".");
        }
    }

    private void progressed(InstallProgress report, AfterInstall after) {
        if (!installActive) {
            return;
        }
        if (report.hasKnownTotal() && report.totalBytes() > 0) {
            installProgress.set((double) report.bytesTransferred() / report.totalBytes());
        } else {
            installProgress.set(-1);
        }
        progressText(
                installingWhat, report.version().text(), ToolRowViewModel.progressTextFor(report));
        if (!report.phase().isTerminal()) {
            return;
        }
        installActive = false;
        install = null;
        installing.set(false);
        installProgress.set(0);
        installEnded.run();
        if (report.phase() == InstallPhase.DONE) {
            after.done();
            return;
        }
        after.ended(report.phase());
        refresh();
        publish();
    }

    private void installedThenConvert(
            LimelightConverterStatus read, ResultsRun target, LimelightOptions using, String to) {
        converterGeneration++;
        converter = read;
        converterText.set(read.explanation());
        converting = false;
        if (read.state() != LimelightConverterStatus.State.INSTALLED) {
            outcome.set("Nothing was converted: " + read.explanation());
            publish();
            return;
        }
        convertNow(target, using, to);
    }

    private void convertNow(ResultsRun target, LimelightOptions using, String to) {
        converting = true;
        conversionGeneration++;
        long mine = conversionGeneration;
        logLines.clear();
        log.set("");
        valid.set(false);
        resultFile.set("");
        resultPsms.set("");
        resultSha256.set("");
        outcome.set(
                "Converting run "
                        + target.id().value()
                        + " to Limelight XML with the Limelight q-value cutoff "
                        + using.qCutoff()
                        + ".");
        publish();
        background.execute(
                () -> {
                    LimelightResult result = null;
                    String words;
                    try {
                        result =
                                port.convert(
                                        target,
                                        using,
                                        to,
                                        line -> ui.execute(() -> logged(mine, line)));
                        words = null;
                    } catch (LimelightConversionRefusedException refused) {
                        words =
                                "Nothing was converted: the conversion was refused before the"
                                        + " converter started.\n"
                                        + refusalLines(refused.refusals());
                    } catch (IOException | RuntimeException failed) {
                        words =
                                "The converter's output was not kept, because it is not valid"
                                        + " Limelight XML or the conversion did not finish: "
                                        + describe(failed);
                    }
                    LimelightResult answer = result;
                    String why = words;
                    ui.execute(() -> converted(mine, target, answer, why));
                });
    }

    private void logged(long conversion, String line) {
        if (conversion != conversionGeneration) {
            return;
        }
        logLines.addLast(line);
        while (logLines.size() > LOG_LINES) {
            logLines.removeFirst();
        }
        log.set(String.join("\n", logLines));
    }

    private void converted(long mine, ResultsRun target, LimelightResult result, String failure) {
        if (mine != conversionGeneration) {
            return;
        }
        converting = false;
        if (result == null) {
            outcome.set(failure);
        } else {
            valid.set(true);
            resultFile.set(result.file().toString());
            resultPsms.set(Integer.toString(result.psms()));
            resultSha256.set(result.sha256());
            outcome.set(
                    "Run "
                            + target.id().value()
                            + " was converted to valid Limelight XML: "
                            + result.file()
                            + ", holding "
                            + result.psms()
                            + " PSMs and "
                            + result.reportedPeptides()
                            + " reported peptides, with the PSM q-value filter "
                            + result.qCutoff()
                            + ". Its SHA-256, as recorded in the run's provenance event log, is "
                            + result.sha256()
                            + ".");
        }
        publish();
        if (run.isPresent()) {
            check();
        }
    }

    private void installedThenRerun(
            ResultsRun source,
            PercolatorSettings settings,
            LimelightRunState state,
            String failure) {
        rerunning = false;
        if (failure != null) {
            actionOutcome.set(
                    "Percolator was installed, but run "
                            + source.id().value()
                            + " could not be checked again, so it was not rerun: "
                            + failure);
            publish();
            return;
        }
        if (run.map(open -> open.id().equals(source.id())).orElse(false)) {
            checkGeneration++;
            readiness = state.readiness();
            readFailure = null;
        }
        Optional<CompatibilityAction> now = state.readiness().compatibility().action();
        if (now.isEmpty() || !now.get().offersRerun() || now.get().installFirst()) {
            actionOutcome.set(
                    "Percolator was installed, but no rerun with an installed build is offered for"
                            + " run "
                            + source.id().value()
                            + " now: "
                            + now.map(CompatibilityAction::explanation)
                                    .orElse(
                                            state.readiness()
                                                    .compatibility()
                                                    .explanation()
                                                    .orElse("it can be converted as it is.")));
            publish();
            return;
        }
        rerunNow(source, now.get(), settings);
    }

    private void rerunNow(ResultsRun source, CompatibilityAction action, PercolatorSettings using) {
        rerunning = true;
        Object mine = new Object();
        rerunAttempt = mine;
        rerunHandle = null;
        rerunRun = null;
        rerunPlan = null;
        rerunDescription = "";
        actionOutcome.set(
                action.label()
                        + ": recording a new run for run "
                        + source.id().value()
                        + " and copying its merged PIN into it.");
        publish();
        RunObserver observer = new Marshalled(this, mine);
        background.execute(
                () -> {
                    try {
                        LimelightRerun started = port.rerun(source, action, using, observer);
                        ui.execute(() -> rerunStarted(mine, started));
                    } catch (RunNotStartedException refused) {
                        ui.execute(
                                () ->
                                        rerunEnded(
                                                mine,
                                                "The Percolator rerun did not start: "
                                                        + refused.getMessage()));
                    } catch (RuntimeException failed) {
                        ui.execute(
                                () ->
                                        rerunEnded(
                                                mine,
                                                "The Percolator rerun did not start: "
                                                        + describe(failed)));
                    }
                });
    }

    private void rerunStarted(Object mine, LimelightRerun started) {
        if (rerunAttempt != mine) {
            return;
        }
        rerunHandle = started.handle();
        rerunRun = started.run();
        rerunCancelEnabled.set(true);
        if (rerunDescription.isEmpty()) {
            rerunDescription = "run " + started.run().value() + " in " + started.directory();
        }
        actionOutcome.set("Rerunning Percolator: " + rerunDescription + ".");
    }

    private void planned(Object mine, Plan plan, String described) {
        if (rerunAttempt != mine) {
            return;
        }
        rerunPlan = plan;
        rerunDescription = described;
        actionOutcome.set("Rerunning Percolator: " + described + ".");
    }

    private void transition(Object mine, StepTransition change) {
        if (rerunAttempt != mine) {
            return;
        }
        actionOutcome.set(
                "Rerunning Percolator: "
                        + rerunDescription
                        + ". "
                        + change.step().id()
                        + ": "
                        + change.to().name().toLowerCase(Locale.ROOT).replace('_', ' ')
                        + ".");
    }

    private void finished(Object mine, RunResult result) {
        if (rerunAttempt != mine) {
            return;
        }
        RunId derived = rerunRun;
        String words =
                switch (result.outcome()) {
                    case SUCCEEDED ->
                            "The Percolator rerun succeeded: "
                                    + rerunDescription
                                    + ". The new run is chosen in the Results section as soon as"
                                    + " its results are listed there, and can then be converted"
                                    + " here.";
                    case CANCELLED ->
                            "The Percolator rerun was cancelled: "
                                    + rerunDescription
                                    + ". Its logs and provenance are kept.";
                    case FAILED -> rerunFailure(result);
                    case RUNNING ->
                            "The Percolator rerun ended without an outcome: "
                                    + rerunDescription
                                    + ".";
                };
        if (!result.finalisationErrors().isEmpty()) {
            words +=
                    " Its provenance could not be finalised: "
                            + String.join("; ", result.finalisationErrors());
        }
        if (result.outcome() == AttemptOutcome.SUCCEEDED && derived != null) {
            awaited = derived;
        }
        rerunEnded(mine, words);
        results.refresh();
    }

    private String rerunFailure(RunResult result) {
        for (EngineStep step : rerunPlan == null ? List.<EngineStep>of() : rerunPlan.steps()) {
            String failure = result.failures().get(step);
            if (failure != null) {
                return "The Percolator rerun failed at step "
                        + step.id()
                        + " ("
                        + step.displayName()
                        + "): "
                        + failure
                        + ". The new run is "
                        + rerunDescription
                        + ".";
            }
        }
        return "The Percolator rerun failed: " + rerunDescription + ".";
    }

    private void rerunEnded(Object mine, String words) {
        if (rerunAttempt != mine) {
            return;
        }
        rerunAttempt = null;
        rerunHandle = null;
        rerunning = false;
        rerunCancelEnabled.set(false);
        actionOutcome.set(words);
        publish();
        if (run.isPresent()) {
            check();
        }
    }

    /** Chooses the derived run a rerun made in the Results section, once it is listed there. */
    private void chooseAwaited(List<ResultsRun> listed) {
        RunId wanted = awaited;
        if (wanted == null) {
            return;
        }
        for (ResultsRun candidate : listed) {
            if (candidate.id().equals(wanted)) {
                awaited = null;
                ui.execute(() -> results.choose(candidate));
                return;
            }
        }
    }

    private void publish() {
        runText.set(
                run.map(
                                open ->
                                        "Run "
                                                + open.id().value()
                                                + ", created "
                                                + open.created()
                                                + (open.executing() ? ", executing now." : "."))
                        .orElse(NO_RUN));
        LimelightCompatibility compatibility = readiness == null ? null : readiness.compatibility();
        compatibilityText.set(compatibilityWords(compatibility));
        boolean canConvert =
                compatibility != null
                        && compatibility.convertible()
                        && readiness.refusal(LimelightPrerequisite.RUN_ENDED).isEmpty();
        convertible.set(canConvert);
        Optional<CompatibilityAction> action =
                compatibility == null ? Optional.empty() : compatibility.action();
        actionVisible.set(action.isPresent());
        actionText.set(action.map(CompatibilityAction::label).orElse(""));
        actionExplanation.set(action.map(CompatibilityAction::explanation).orElse(""));
        actionEnabled.set(action.isPresent() && actionRefusal(action.get()).isEmpty());
        boolean controls = canConvert && !busy();
        settingsEnabled.set(controls);
        decoyEnabled.set(controls && readiness.decoyImportUnavailable().isEmpty());
        decoyText.set(
                readiness == null
                        ? ""
                        : readiness.decoyImportUnavailable().orElse(DECOYS_AVAILABLE));
        fastaText.set(
                readiness == null
                        ? ""
                        : "The FASTA the run recorded, which the converter reads: "
                                + readiness.fasta());
        pepXmlText.set(
                readiness == null
                        ? ""
                        : "The pepXML directory of the run that searched, which the converter"
                                + " reads: "
                                + readiness.pepXmlDirectory());
        outputStatus.set(outputWords());
        refusalsText.set(readiness == null ? "" : refusalWords(readiness.refusals()));
        String refusal = conversionRefusal(compatibility);
        convertRefusal.set(refusal);
        convertEnabled.set(refusal.isEmpty());
    }

    private String compatibilityWords(LimelightCompatibility compatibility) {
        if (run.isEmpty()) {
            return NO_RUN + " Open a run there to convert it to Limelight XML.";
        }
        if (compatibility == null) {
            return readFailure == null
                    ? CHECKING
                    : "Whether run "
                            + run.get().id().value()
                            + " can be converted could not be read: "
                            + readFailure;
        }
        if (compatibility.convertible()) {
            return "Run "
                    + compatibility.runId().value()
                    + "'s Percolator"
                    + compatibility.percolator().map(build -> " " + build).orElse("")
                    + " wrote the Percolator XML the Limelight converter reads, so the run can be"
                    + " converted.";
        }
        return compatibility.explanation().orElseThrow();
    }

    private String actionRefusal(CompatibilityAction action) {
        if (busy()) {
            return "An install, a conversion or a rerun is running.";
        }
        if (!action.offersRerun()) {
            return "";
        }
        PercolatorRequest request = percolator.getValue();
        if (request == null || request.settings().isEmpty()) {
            return "The Percolator section's settings, which the rerun runs with, are not valid"
                    + " yet.";
        }
        return "";
    }

    private String outputWords() {
        if (readiness == null) {
            return "";
        }
        if (readiness.output().isPresent()) {
            return "The next conversion writes " + readiness.output().get() + ".";
        }
        return readiness
                .refusal(LimelightPrerequisite.OUTPUT)
                .map(LimelightRefusal::message)
                .orElse("Where the next conversion writes is not known yet.");
    }

    private String conversionRefusal(LimelightCompatibility compatibility) {
        if (run.isEmpty()) {
            return NO_RUN + " Open a run there to convert it.";
        }
        if (compatibility == null) {
            return compatibilityWords(null);
        }
        if (installActive) {
            return "An install is running; convert when it has finished.";
        }
        if (converting) {
            return "A conversion is running; the next can start when it has finished.";
        }
        if (rerunning) {
            return "A Percolator rerun is running; convert when it has finished.";
        }
        if (!compatibility.convertible()) {
            return compatibility.explanation().orElseThrow();
        }
        if (!settingsProblem.isEmpty()) {
            return "The Limelight settings shown are not saved: " + settingsProblem;
        }
        List<LimelightRefusal> blocking = new ArrayList<>();
        for (LimelightRefusal refusal : readiness.refusals()) {
            if (refusal.prerequisite() != LimelightPrerequisite.CONVERTER) {
                blocking.add(refusal);
            }
        }
        if (!blocking.isEmpty()) {
            return "The conversion cannot start:\n" + refusalLines(blocking);
        }
        if (converter == null) {
            return NOT_READ;
        }
        return switch (converter.state()) {
            case INSTALLED, NOT_INSTALLED -> "";
            case INSTALLING, UNAVAILABLE -> converter.explanation();
        };
    }

    private static String refusalWords(List<LimelightRefusal> refusals) {
        return refusals.isEmpty() ? EVERY_PREREQUISITE_MET : refusalLines(refusals);
    }

    private static String refusalLines(List<LimelightRefusal> refusals) {
        List<String> lines = new ArrayList<>(refusals.size());
        for (LimelightRefusal refusal : refusals) {
            lines.add(refusal.prerequisite().name() + ": " + refusal.message());
        }
        return String.join("\n", lines);
    }

    private static String describe(Exception failed) {
        String message = failed.getMessage();
        return message == null || message.isBlank() ? failed.toString() : message;
    }

    /** The observer handed to the engine: every call is applied on the interface executor. */
    private static final class Marshalled implements RunObserver {

        private final LimelightViewModel owner;

        private final Object attempt;

        Marshalled(LimelightViewModel owner, Object attempt) {
            this.owner = owner;
            this.attempt = attempt;
        }

        @Override
        public void planned(Plan plan, String description) {
            owner.ui.execute(() -> owner.planned(attempt, plan, description));
        }

        @Override
        public void onTransition(StepTransition transition) {
            owner.ui.execute(() -> owner.transition(attempt, transition));
        }

        @Override
        public void onRunFinished(RunResult result) {
            owner.ui.execute(() -> owner.finished(attempt, result));
        }
    }
}
