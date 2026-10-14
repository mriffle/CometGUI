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

package org.cometgui.ui.viewmodel.provenance;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import org.cometgui.ui.viewmodel.NonNullProperty;
import org.cometgui.ui.viewmodel.params.FileChooserPort;
import org.cometgui.workflow.provenance.DiskVerification;
import org.cometgui.workflow.provenance.ProvenanceView;
import org.cometgui.workflow.provenance.RecordState;
import org.cometgui.workflow.provenance.RunListing;

/**
 * The Provenance section (phase 13, work unit 5; the specification's <em>Provenance UI</em>): every
 * run of the session's project, and for the chosen one the seven parts the specification names --
 * summary, tools, inputs and outputs, parameters, timeline, logs, warnings -- with the six actions:
 * Copy MD5, Copy SHA-256, Copy command, Open file location, Export JSON and Export RST, and Verify
 * against disk.
 *
 * <h2>Every run, whatever became of it</h2>
 *
 * <p>The run list is the port's: completed, failed, cancelled, running, interrupted, never started,
 * and one whose {@code run.json} cannot be read, each said in words. A run without a sealed record,
 * and one whose record cannot be read, opens all the same and says which it is ({@link
 * #recordTextProperty()}): never an empty run (P13-5).
 *
 * <h2>Display text only</h2>
 *
 * <p>Every text published here is the read model's display text, laid out: nothing is parsed,
 * hashed, compared or redacted here. What Copy puts on the clipboard is exactly the read model's
 * value -- the recorded digest, or the command's display form -- and is returned to the pane, which
 * owns the clipboard.
 *
 * <h2>Two threads (P13-8)</h2>
 *
 * <p>The port is called only on {@code background} -- listing, opening, exporting, verifying and
 * opening a location all block -- and every answer is applied on {@code ui}. The file chooser, a
 * modal dialog, is asked on the thread an action is called on, as the parameter editor's is.
 *
 * <h2>Stale answers are dropped</h2>
 *
 * <p>Each listing and each opening is numbered. An answer for a run that is no longer the chosen
 * one -- the scientist chose another while it was being read -- is dropped, as is an older listing
 * that arrives after a newer one; and so is the answer of an export, a verification or a location
 * started on a run that is no longer open.
 */
public final class ProvenanceViewModel {

    /** The run list's state before the runs have been read. */
    public static final String RUNS_NOT_READ = "The project's runs have not been read yet.";

    /** The run list's state while the runs are being read. */
    public static final String READING_RUNS = "Reading the project's runs.";

    /** The run list's state when the project has no run. */
    public static final String NO_RUNS = "This project has no runs yet.";

    /** The record's state when no run is chosen. */
    public static final String NO_RUN =
            "No run is chosen. Choose one of the project's runs above to see its provenance.";

    /** The record's state while the chosen run is being read. */
    public static final String OPENING = "Reading the chosen run's provenance.";

    /** The parts' text while no record is shown. */
    public static final String NOTHING_SHOWN = "";

    /** The verification's state before anything was verified. */
    public static final String NOT_VERIFIED =
            "Not verified: Verify against disk hashes every file the record lists again and"
                    + " compares it with the record.";

    private final ProvenancePort port;

    private final FileChooserPort chooser;

    private final Executor background;

    private final Executor ui;

    // ---- state, on the interface thread ----

    private List<RunListing> listed = List.of();

    /**
     * The newest listing's number. Atomic, as the Tool Manager's refresh numbers are: answers are
     * applied on the interface thread, and the number is compared there.
     */
    private final AtomicLong listGeneration = new AtomicLong();

    private Optional<RunListing> chosen = Optional.empty();

    /** The run shown; null while none is. */
    private OpenedRecord opened;

    /** The newest opening's number; every action's answer is checked against it. */
    private final AtomicLong openGeneration = new AtomicLong();

    /** Whether an export is being written. */
    private final AtomicBoolean exporting = new AtomicBoolean();

    /** An export refused because its target exists, awaiting the scientist's Replace. */
    private PendingExport pending;

    /** The running verification's stop switch; null when none is running. */
    private AtomicBoolean verification;

    // ---- published ----

    private final NonNullProperty<List<String>> runs;

    private final NonNullProperty<String> runsStatus;

    private final ReadOnlyIntegerWrapper chosenIndex;

    private final NonNullProperty<String> recordText;

    private final ReadOnlyBooleanWrapper recordShown;

    private final NonNullProperty<String> summaryText;

    private final NonNullProperty<List<String>> tools;

    private final NonNullProperty<List<String>> files;

    private final NonNullProperty<String> cometParamsText;

    private final NonNullProperty<String> cometParamsStatus;

    private final NonNullProperty<String> percolatorSettingsText;

    private final NonNullProperty<List<String>> parameters;

    private final NonNullProperty<String> presetsText;

    private final NonNullProperty<String> parameterNotes;

    private final NonNullProperty<List<String>> executions;

    private final NonNullProperty<List<String>> events;

    private final NonNullProperty<List<String>> logs;

    private final NonNullProperty<List<String>> warnings;

    private final NonNullProperty<String> actionStatus;

    private final ReadOnlyBooleanWrapper exportEnabled;

    private final ReadOnlyBooleanWrapper replaceOffered;

    private final NonNullProperty<String> exportStatus;

    private final ReadOnlyBooleanWrapper verifyEnabled;

    private final ReadOnlyBooleanWrapper verifying;

    private final NonNullProperty<String> verifyStatus;

    private final NonNullProperty<List<String>> verificationRows;

    /** The two export formats. */
    private enum Format {
        JSON("provenance JSON", "Export provenance JSON", ".json"),
        RST("provenance RST", "Export provenance RST", ".rst");

        private final String words;

        private final String title;

        private final String suffix;

        Format(String words, String title, String suffix) {
            this.words = words;
            this.title = title;
            this.suffix = suffix;
        }
    }

    /** An export the port refused because its target exists. */
    private record PendingExport(Format format, Path target) {}

    /**
     * The section, with nothing read. Nothing is read until {@link #refresh()}.
     *
     * @param port the application's provenance
     * @param chooser asks where an export is written: a modal dialog, or a test's script
     * @param background where the port is called: never the interface thread
     * @param ui where every answer is applied: the interface thread
     */
    public ProvenanceViewModel(
            ProvenancePort port, FileChooserPort chooser, Executor background, Executor ui) {
        this.port = Objects.requireNonNull(port, "port");
        this.chooser = Objects.requireNonNull(chooser, "chooser");
        this.background = Objects.requireNonNull(background, "background");
        this.ui = Objects.requireNonNull(ui, "ui");
        runs = new NonNullProperty<>(this, "runs", List.of());
        runsStatus = new NonNullProperty<>(this, "runsStatus", RUNS_NOT_READ);
        chosenIndex = new ReadOnlyIntegerWrapper(this, "chosenIndex", -1);
        recordText = new NonNullProperty<>(this, "recordText", NO_RUN);
        recordShown = new ReadOnlyBooleanWrapper(this, "recordShown", false);
        summaryText = new NonNullProperty<>(this, "summaryText", NOTHING_SHOWN);
        tools = new NonNullProperty<>(this, "tools", List.of());
        files = new NonNullProperty<>(this, "files", List.of());
        cometParamsText = new NonNullProperty<>(this, "cometParamsText", NOTHING_SHOWN);
        cometParamsStatus = new NonNullProperty<>(this, "cometParamsStatus", NOTHING_SHOWN);
        percolatorSettingsText =
                new NonNullProperty<>(this, "percolatorSettingsText", NOTHING_SHOWN);
        parameters = new NonNullProperty<>(this, "parameters", List.of());
        presetsText = new NonNullProperty<>(this, "presetsText", NOTHING_SHOWN);
        parameterNotes = new NonNullProperty<>(this, "parameterNotes", NOTHING_SHOWN);
        executions = new NonNullProperty<>(this, "executions", List.of());
        events = new NonNullProperty<>(this, "events", List.of());
        logs = new NonNullProperty<>(this, "logs", List.of());
        warnings = new NonNullProperty<>(this, "warnings", List.of());
        actionStatus = new NonNullProperty<>(this, "actionStatus", NOTHING_SHOWN);
        exportEnabled = new ReadOnlyBooleanWrapper(this, "exportEnabled", false);
        replaceOffered = new ReadOnlyBooleanWrapper(this, "replaceOffered", false);
        exportStatus = new NonNullProperty<>(this, "exportStatus", NOTHING_SHOWN);
        verifyEnabled = new ReadOnlyBooleanWrapper(this, "verifyEnabled", false);
        verifying = new ReadOnlyBooleanWrapper(this, "verifying", false);
        verifyStatus = new NonNullProperty<>(this, "verifyStatus", NOT_VERIFIED);
        verificationRows = new NonNullProperty<>(this, "verificationRows", List.of());
    }

    // =========================================================================== actions ====

    /**
     * Reads the project's runs again, on the background executor. The chosen run stays chosen while
     * it is listed, and is read again when what the list says about it changed -- a run that was
     * running and has now ended, say. The composition root calls this at start and whenever a run
     * starts or ends; the section's own Read the runs again control does too.
     */
    public void refresh() {
        long mine = listGeneration.incrementAndGet();
        runsStatus.set(READING_RUNS);
        background.execute(
                () -> {
                    List<RunListing> answer;
                    try {
                        answer = List.copyOf(port.runs());
                    } catch (IOException | RuntimeException unreadable) {
                        String why = message(unreadable);
                        ui.execute(() -> runsFailed(mine, why));
                        return;
                    }
                    ui.execute(() -> runsRead(mine, answer));
                });
    }

    /**
     * Chooses one of the listed runs and reads its provenance on the background executor. An answer
     * still coming for the run chosen before is dropped.
     *
     * @param index the run's position in {@link #runsProperty()}
     * @return {@code true} if a run is being read; {@code false} for a position that names none
     */
    public boolean choose(int index) {
        if (index < 0 || index >= listed.size()) {
            return false;
        }
        RunListing run = listed.get(index);
        chosen = Optional.of(run);
        chosenIndex.set(index);
        open(run);
        return true;
    }

    /**
     * The MD5 of a tool's executable, for the clipboard.
     *
     * @param index the tool's position in {@link #toolsProperty()}
     * @return exactly the recorded MD5, or empty when no tool is at that position
     */
    public Optional<String> copyToolMd5(int index) {
        return copy(
                tool(index),
                ProvenanceView.Tool::md5,
                tool -> "the MD5 of " + tool.name() + " " + tool.version() + "'s executable");
    }

    /**
     * The SHA-256 of a tool's executable, for the clipboard.
     *
     * @param index the tool's position in {@link #toolsProperty()}
     * @return exactly the recorded SHA-256, or empty when no tool is at that position
     */
    public Optional<String> copyToolSha256(int index) {
        return copy(
                tool(index),
                ProvenanceView.Tool::sha256,
                tool -> "the SHA-256 of " + tool.name() + " " + tool.version() + "'s executable");
    }

    /**
     * The MD5 of an input or output file, for the clipboard.
     *
     * @param index the file's position in {@link #filesProperty()}
     * @return exactly the recorded MD5, or empty when no file is at that position
     */
    public Optional<String> copyFileMd5(int index) {
        return copy(
                file(index),
                ProvenanceView.File::md5,
                file -> "the MD5 of the " + file.direction() + " " + file.role() + " file");
    }

    /**
     * The SHA-256 of an input or output file, for the clipboard.
     *
     * @param index the file's position in {@link #filesProperty()}
     * @return exactly the recorded SHA-256, or empty when no file is at that position
     */
    public Optional<String> copyFileSha256(int index) {
        return copy(
                file(index),
                ProvenanceView.File::sha256,
                file -> "the SHA-256 of the " + file.direction() + " " + file.role() + " file");
    }

    /**
     * The command of one process execution, for the clipboard: exactly the read model's display
     * form, the argument array quoted and escaped, never a shell command line.
     *
     * @param index the execution's position in {@link #executionsProperty()}
     * @return the command, or empty when no execution is at that position
     */
    public Optional<String> copyCommand(int index) {
        return copy(
                execution(index),
                ProvenanceView.Execution::command,
                execution ->
                        "the command of "
                                + execution.stageId().orElse(execution.tool())
                                + " ("
                                + execution.tool()
                                + " "
                                + execution.version()
                                + ")");
    }

    /**
     * Says that what a copy returned could not be put on the clipboard. Called by the pane, which
     * owns the clipboard.
     *
     * @param why the clipboard's refusal
     */
    public void clipboardRefused(String why) {
        actionStatus.set(
                "Nothing was copied: the clipboard refused the text: "
                        + Objects.requireNonNull(why, "why"));
    }

    /**
     * Shows a tool's executable in the platform's file manager.
     *
     * @param index the tool's position in {@link #toolsProperty()}
     * @return {@code true} if the file manager is being started
     */
    public boolean openToolLocation(int index) {
        Optional<ProvenanceView.Tool> tool = tool(index);
        if (tool.isEmpty()) {
            nothingAt("tool");
            return false;
        }
        openLocation(tool.get().location(), tool.get().name() + "'s executable");
        return true;
    }

    /**
     * Shows an input or output file in the platform's file manager.
     *
     * @param index the file's position in {@link #filesProperty()}
     * @return {@code true} if the file manager is being started
     */
    public boolean openFileLocation(int index) {
        Optional<ProvenanceView.File> file = file(index);
        if (file.isEmpty()) {
            nothingAt("file");
            return false;
        }
        openLocation(
                file.get().location(),
                "the " + file.get().direction() + " " + file.get().role() + " file");
        return true;
    }

    /**
     * Shows an archived log in the platform's file manager.
     *
     * @param index the log's position in {@link #logsProperty()}
     * @return {@code true} if the file manager is being started
     */
    public boolean openLogLocation(int index) {
        Optional<ProvenanceView.Log> log = log(index);
        if (log.isEmpty()) {
            nothingAt("log");
            return false;
        }
        openLocation(
                log.get().location(), "the log of " + log.get().stageId().orElse(log.get().tool()));
        return true;
    }

    /**
     * Exports the shown run's record as JSON: asks where, then writes on the background executor.
     * An existing file is not replaced; the section says so and offers {@link #replaceExisting()}.
     *
     * @return {@code true} if an export started
     */
    public boolean exportJson() {
        return export(Format.JSON);
    }

    /**
     * Exports the shown run's record as the RST report, rendered from the same record as the JSON.
     *
     * @return {@code true} if an export started
     */
    public boolean exportRst() {
        return export(Format.RST);
    }

    /**
     * Replaces the file an export was refused for, once the scientist has confirmed it.
     *
     * @return {@code true} if the export started again, replacing the file
     */
    public boolean replaceExisting() {
        PendingExport confirmed = pending;
        if (confirmed == null || opened == null || exporting.get()) {
            return false;
        }
        write(confirmed.format(), confirmed.target(), true);
        return true;
    }

    /**
     * Hashes again every file the shown run's record lists and compares each with its record, on
     * the background executor. Cancellable with {@link #cancelVerification()}.
     *
     * @return {@code true} if the verification started
     */
    public boolean verify() {
        if (opened == null || !opened.canExport()) {
            verifyStatus.set("Nothing was verified: " + nothingToActOn());
            return false;
        }
        if (verification != null) {
            return false;
        }
        OpenedRecord run = opened;
        long mine = openGeneration.get();
        AtomicBoolean stop = new AtomicBoolean();
        verification = stop;
        verificationRows.set(List.of());
        verifyStatus.set("Verifying every file the record lists against the disk.");
        publishEnablement();
        background.execute(
                () -> {
                    DiskVerification found;
                    try {
                        found = port.verify(run, stop::get);
                    } catch (IOException | RuntimeException failed) {
                        String why = message(failed);
                        ui.execute(() -> verificationFailed(mine, stop, why));
                        return;
                    }
                    ui.execute(() -> verified(mine, stop, found));
                });
        return true;
    }

    /**
     * Stops the running verification before its next file.
     *
     * @return {@code true} if a verification was running
     */
    public boolean cancelVerification() {
        if (verification == null) {
            return false;
        }
        verification.set(true);
        verifyStatus.set("Stopping the verification before its next file.");
        return true;
    }

    // ===================================================================== answers, on ui ====

    private void runsRead(long mine, List<RunListing> answer) {
        if (mine != listGeneration.get()) {
            return;
        }
        listed = answer;
        List<String> labels = new ArrayList<>();
        for (RunListing run : answer) {
            labels.add(ProvenanceTexts.runLabel(run));
        }
        runs.set(List.copyOf(labels));
        runsStatus.set(
                answer.isEmpty()
                        ? NO_RUNS
                        : answer.size() == 1
                                ? "This project has 1 run."
                                : "This project has " + answer.size() + " runs.");
        if (chosen.isEmpty()) {
            // nothing chosen: the position is already -1
            return;
        }
        RunListing before = chosen.get();
        for (int index = 0; index < answer.size(); index++) {
            RunListing now = answer.get(index);
            if (now.directory().equals(before.directory())) {
                chosen = Optional.of(now);
                chosenIndex.set(index);
                if (!now.equals(before)) {
                    open(now);
                }
                return;
            }
        }
        chosen = Optional.empty();
        chosenIndex.set(-1);
        openGeneration.incrementAndGet();
        clearRecord(
                "The run that was chosen, " + before.directoryName() + ", is no longer listed.");
    }

    private void runsFailed(long mine, String why) {
        if (mine == listGeneration.get()) {
            runsStatus.set("The project's runs could not be read: " + why);
        }
    }

    private void open(RunListing run) {
        long mine = openGeneration.incrementAndGet();
        clearRecord(OPENING);
        background.execute(
                () -> {
                    OpenedRecord answer;
                    try {
                        answer = Objects.requireNonNull(port.open(run), "the opened run");
                    } catch (IOException | RuntimeException unreadable) {
                        String why = message(unreadable);
                        ui.execute(() -> openFailed(mine, run, why));
                        return;
                    }
                    ui.execute(() -> show(mine, answer));
                });
    }

    private void show(long mine, OpenedRecord answer) {
        if (mine != openGeneration.get()) {
            return;
        }
        opened = answer;
        ProvenanceView view = answer.view();
        recordText.set(
                ProvenanceTexts.recordText(view.summary().recordState(), answer.recordProblem()));
        recordShown.set(true);
        summaryText.set(ProvenanceTexts.summary(view.summary()));
        tools.set(rows(view.tools(), ProvenanceTexts::tool));
        files.set(rows(view.files(), ProvenanceTexts::file));
        ProvenanceView.Parameters shown = view.parameters();
        cometParamsText.set(ProvenanceTexts.archivedText(shown.cometParams()));
        cometParamsStatus.set(
                ProvenanceTexts.archivedStatus("Comet parameter file", shown.cometParams())
                        + shown.cometRelease()
                                .map(release -> " Written for Comet " + release + ".")
                                .orElse(""));
        percolatorSettingsText.set(
                ProvenanceTexts.archivedStatus(
                                "Percolator settings file", shown.percolatorSettingsFile())
                        + "\n"
                        + ProvenanceTexts.settings(shown.percolatorSettings()));
        parameters.set(rows(shown.cometParameters(), ProvenanceTexts::parameter));
        presetsText.set(ProvenanceTexts.presets(shown.presets()));
        parameterNotes.set(String.join("\n", shown.notes()));
        executions.set(rows(view.timeline().executions(), ProvenanceTexts::execution));
        events.set(rows(view.timeline().events(), ProvenanceTexts::event));
        logs.set(rows(view.logs(), ProvenanceTexts::log));
        warnings.set(ProvenanceTexts.warningGroups(view.warnings()));
        publishEnablement();
    }

    private void openFailed(long mine, RunListing run, String why) {
        if (mine == openGeneration.get()) {
            recordText.set(
                    "The provenance of " + run.directoryName() + " could not be read: " + why);
        }
    }

    private void located(long mine, String said) {
        if (mine == openGeneration.get()) {
            actionStatus.set(said);
        }
    }

    private void locationFailed(long mine, String what, String why) {
        if (mine == openGeneration.get()) {
            actionStatus.set(
                    "The location of "
                            + what
                            + " was not shown: no file manager could be"
                            + " started: "
                            + why);
        }
    }

    private void exported(long mine, Format format, Path written) {
        if (mine != openGeneration.get()) {
            return;
        }
        // write() withdrew any Replace offer and pending file when this export started
        exporting.set(false);
        exportStatus.set("Exported " + format.words + " to " + written + ".");
        publishEnablement();
    }

    private void exportExists(
            long mine, Format format, Path target, boolean replacing, String reason) {
        if (mine != openGeneration.get()) {
            return;
        }
        exporting.set(false);
        if (replacing) {
            // refused again: no second offer (write() withdrew the first)
            exportStatus.set("Nothing was exported to " + target + ": " + reason + ".");
        } else {
            pending = new PendingExport(format, target);
            replaceOffered.set(true);
            exportStatus.set(
                    "Nothing was exported: "
                            + target
                            + " already exists ("
                            + reason
                            + "). Press Replace to export "
                            + format.words
                            + " over it, or export again and choose another file.");
        }
        publishEnablement();
    }

    private void exportFailed(long mine, Path target, String why) {
        if (mine != openGeneration.get()) {
            return;
        }
        exporting.set(false);
        exportStatus.set("Nothing was exported to " + target + ": " + why);
        publishEnablement();
    }

    private void verified(long mine, AtomicBoolean stop, DiskVerification found) {
        if (mine != openGeneration.get() || verification != stop) {
            return;
        }
        verification = null;
        verificationRows.set(rows(found.checks(), ProvenanceTexts::check));
        verifyStatus.set(ProvenanceTexts.verificationSummary(found));
        publishEnablement();
    }

    private void verificationFailed(long mine, AtomicBoolean stop, String why) {
        if (mine != openGeneration.get() || verification != stop) {
            return;
        }
        verification = null;
        verifyStatus.set("The files could not be verified: " + why);
        publishEnablement();
    }

    // =========================================================================== helpers ====

    private boolean export(Format format) {
        if (opened == null || !opened.canExport()) {
            exportStatus.set("Nothing was exported: " + nothingToActOn());
            return false;
        }
        if (exporting.get()) {
            return false;
        }
        Optional<Path> target =
                chooser.chooseExportTarget(
                        format.title,
                        "provenance-" + opened.view().summary().runId() + format.suffix);
        if (target.isEmpty()) {
            exportStatus.set("Nothing was exported: no file was chosen.");
            return false;
        }
        write(format, target.get(), false);
        return true;
    }

    private void write(Format format, Path target, boolean replacing) {
        OpenedRecord run = opened;
        long mine = openGeneration.get();
        exporting.set(true);
        pending = null;
        replaceOffered.set(false);
        exportStatus.set(
                (replacing ? "Replacing " + target + " with " : "Exporting ")
                        + format.words
                        + (replacing ? "." : " to " + target + "."));
        publishEnablement();
        background.execute(
                () -> {
                    Path written;
                    try {
                        written =
                                format == Format.JSON
                                        ? port.exportJson(run, target, replacing)
                                        : port.exportRst(run, target, replacing);
                    } catch (FileAlreadyExistsException exists) {
                        String reason =
                                exists.getReason() == null
                                        ? "a file already exists there"
                                        : exists.getReason();
                        ui.execute(() -> exportExists(mine, format, target, replacing, reason));
                        return;
                    } catch (IOException | RuntimeException failed) {
                        String why = message(failed);
                        ui.execute(() -> exportFailed(mine, target, why));
                        return;
                    }
                    ui.execute(() -> exported(mine, format, written));
                });
    }

    private void openLocation(Path location, String what) {
        long mine = openGeneration.get();
        actionStatus.set("Starting the file manager to show " + what + ".");
        background.execute(
                () -> {
                    String said;
                    try {
                        said = port.openLocation(location);
                    } catch (IOException | RuntimeException failed) {
                        String why = message(failed);
                        ui.execute(() -> locationFailed(mine, what, why));
                        return;
                    }
                    ui.execute(() -> located(mine, said));
                });
    }

    private void nothingAt(String what) {
        actionStatus.set("Nothing was done: no " + what + " is selected.");
    }

    private <T> Optional<String> copy(
            Optional<T> row, Function<T, String> value, Function<T, String> what) {
        if (row.isEmpty()) {
            actionStatus.set("Nothing was copied: nothing is selected.");
            return Optional.empty();
        }
        String copied = value.apply(row.get());
        actionStatus.set("Copied " + what.apply(row.get()) + ": " + copied);
        return Optional.of(copied);
    }

    private Optional<ProvenanceView.Tool> tool(int index) {
        return opened == null ? Optional.empty() : at(opened.view().tools(), index);
    }

    private Optional<ProvenanceView.File> file(int index) {
        return opened == null ? Optional.empty() : at(opened.view().files(), index);
    }

    private Optional<ProvenanceView.Execution> execution(int index) {
        return opened == null ? Optional.empty() : at(opened.view().timeline().executions(), index);
    }

    private Optional<ProvenanceView.Log> log(int index) {
        return opened == null ? Optional.empty() : at(opened.view().logs(), index);
    }

    private static <T> Optional<T> at(List<T> rows, int index) {
        return index >= 0 && index < rows.size() ? Optional.of(rows.get(index)) : Optional.empty();
    }

    private static <T> List<String> rows(List<T> values, Function<T, String> shown) {
        List<String> texts = new ArrayList<>();
        for (T value : values) {
            texts.add(shown.apply(value));
        }
        return List.copyOf(texts);
    }

    /** Why the shown run, or the lack of one, has nothing to export or verify. */
    private String nothingToActOn() {
        if (opened == null) {
            return "no run's provenance is shown.";
        }
        RecordState state = opened.view().summary().recordState();
        return state == RecordState.RECORD_UNREADABLE
                ? "this run's provenance record could not be read: "
                        + opened.recordProblem().orElse("no reason was given")
                : "this run has no sealed provenance record, so there is nothing to export or"
                        + " verify.";
    }

    private void clearRecord(String state) {
        opened = null;
        if (verification != null) {
            verification.set(true);
            verification = null;
        }
        exporting.set(false);
        pending = null;
        recordText.set(state);
        recordShown.set(false);
        summaryText.set(NOTHING_SHOWN);
        tools.set(List.of());
        files.set(List.of());
        cometParamsText.set(NOTHING_SHOWN);
        cometParamsStatus.set(NOTHING_SHOWN);
        percolatorSettingsText.set(NOTHING_SHOWN);
        parameters.set(List.of());
        presetsText.set(NOTHING_SHOWN);
        parameterNotes.set(NOTHING_SHOWN);
        executions.set(List.of());
        events.set(List.of());
        logs.set(List.of());
        warnings.set(List.of());
        actionStatus.set(NOTHING_SHOWN);
        replaceOffered.set(false);
        exportStatus.set(NOTHING_SHOWN);
        verifyStatus.set(NOT_VERIFIED);
        verificationRows.set(List.of());
        publishEnablement();
    }

    private void publishEnablement() {
        boolean actionable = opened != null && opened.canExport();
        exportEnabled.set(actionable && !exporting.get());
        verifyEnabled.set(actionable && verification == null);
        verifying.set(verification != null);
    }

    private static String message(Exception failure) {
        String text = failure.getMessage();
        return text == null || text.isBlank() ? failure.getClass().getSimpleName() : text;
    }

    // ====================================================================== properties ====

    /**
     * The runs of the project, each in words: its identifier, when it was created, what became of
     * it and whether it has a sealed record -- or its directory and why it cannot be read.
     *
     * @return the run labels, in the port's order
     */
    public ReadOnlyObjectProperty<List<String>> runsProperty() {
        return runs.getReadOnlyProperty();
    }

    /**
     * Whether the runs have been read, how many there are, or why they could not be read.
     *
     * @return the run list's state
     */
    public ReadOnlyObjectProperty<String> runsStatusProperty() {
        return runsStatus.getReadOnlyProperty();
    }

    /**
     * The chosen run's position in {@link #runsProperty()}.
     *
     * @return the position, or -1 when no run is chosen
     */
    public ReadOnlyIntegerProperty chosenIndexProperty() {
        return chosenIndex.getReadOnlyProperty();
    }

    /**
     * Whether the chosen run has a sealed record, has none, or has one that cannot be read, in
     * words; or that no run is chosen, or the chosen one is being read.
     *
     * @return the record's state
     */
    public ReadOnlyObjectProperty<String> recordTextProperty() {
        return recordText.getReadOnlyProperty();
    }

    /**
     * Whether a run's provenance is shown.
     *
     * @return {@code true} once the chosen run has been read
     */
    public ReadOnlyBooleanProperty recordShownProperty() {
        return recordShown.getReadOnlyProperty();
    }

    /**
     * The Summary part: run, status, start and end, tool versions, input and output counts.
     *
     * @return the summary, one fact per line
     */
    public ReadOnlyObjectProperty<String> summaryTextProperty() {
        return summaryText.getReadOnlyProperty();
    }

    /**
     * The Tools part: one entry per tool record.
     *
     * @return the tool rows
     */
    public ReadOnlyObjectProperty<List<String>> toolsProperty() {
        return tools.getReadOnlyProperty();
    }

    /**
     * The Inputs/outputs part: one entry per file record, a partial one saying so first.
     *
     * @return the file rows
     */
    public ReadOnlyObjectProperty<List<String>> filesProperty() {
        return files.getReadOnlyProperty();
    }

    /**
     * The exact archived Comet parameter file, as it was executed.
     *
     * @return its text, or empty when it is not shown ({@link #cometParamsStatusProperty()} says
     *     why)
     */
    public ReadOnlyObjectProperty<String> cometParamsTextProperty() {
        return cometParamsText.getReadOnlyProperty();
    }

    /**
     * The archived Comet parameter file's path, its recorded SHA-256 and whether the file on disk
     * still has it.
     *
     * @return the statement
     */
    public ReadOnlyObjectProperty<String> cometParamsStatusProperty() {
        return cometParamsStatus.getReadOnlyProperty();
    }

    /**
     * The archived Percolator settings file's state and every Percolator setting the record
     * carries.
     *
     * @return the settings, one per line
     */
    public ReadOnlyObjectProperty<String> percolatorSettingsTextProperty() {
        return percolatorSettingsText.getReadOnlyProperty();
    }

    /**
     * Each Comet parameter with its value, origin, release default and preset value, and each
     * difference named.
     *
     * @return the parameter rows, in schema order
     */
    public ReadOnlyObjectProperty<List<String>> parametersProperty() {
        return parameters.getReadOnlyProperty();
    }

    /**
     * The presets the run records as applied, with what each set.
     *
     * @return the presets, or a sentence saying there were none
     */
    public ReadOnlyObjectProperty<String> presetsTextProperty() {
        return presetsText.getReadOnlyProperty();
    }

    /**
     * What the Parameters part could not show and why, and what the parameter parser reported.
     *
     * @return the notes, one per line; empty when there are none
     */
    public ReadOnlyObjectProperty<String> parameterNotesProperty() {
        return parameterNotes.getReadOnlyProperty();
    }

    /**
     * The Timeline part's process executions, in start order: stage, outcome, exit status, times
     * and the command.
     *
     * @return the execution rows
     */
    public ReadOnlyObjectProperty<List<String>> executionsProperty() {
        return executions.getReadOnlyProperty();
    }

    /**
     * The Timeline part's event log, in sequence order.
     *
     * @return the event rows
     */
    public ReadOnlyObjectProperty<List<String>> eventsProperty() {
        return events.getReadOnlyProperty();
    }

    /**
     * The Logs part: each archived process log.
     *
     * @return the log rows
     */
    public ReadOnlyObjectProperty<List<String>> logsProperty() {
        return logs.getReadOnlyProperty();
    }

    /**
     * The Warnings part, one entry per source in the read model's order.
     *
     * @return the warning groups
     */
    public ReadOnlyObjectProperty<List<String>> warningsProperty() {
        return warnings.getReadOnlyProperty();
    }

    /**
     * What the last copy or Open file location did, or why it did nothing.
     *
     * @return the statement; empty before either
     */
    public ReadOnlyObjectProperty<String> actionStatusProperty() {
        return actionStatus.getReadOnlyProperty();
    }

    /**
     * Whether Export JSON and Export RST can be pressed: a sealed record is shown and no export is
     * being written.
     *
     * @return the switch
     */
    public ReadOnlyBooleanProperty exportEnabledProperty() {
        return exportEnabled.getReadOnlyProperty();
    }

    /**
     * Whether an export was refused because its file exists, and Replace is offered.
     *
     * @return the switch
     */
    public ReadOnlyBooleanProperty replaceOfferedProperty() {
        return replaceOffered.getReadOnlyProperty();
    }

    /**
     * What the last export did: the file written, or why nothing was.
     *
     * @return the statement; empty before any
     */
    public ReadOnlyObjectProperty<String> exportStatusProperty() {
        return exportStatus.getReadOnlyProperty();
    }

    /**
     * Whether Verify against disk can be pressed.
     *
     * @return the switch
     */
    public ReadOnlyBooleanProperty verifyEnabledProperty() {
        return verifyEnabled.getReadOnlyProperty();
    }

    /**
     * Whether a verification is running, which Cancel stops.
     *
     * @return the switch
     */
    public ReadOnlyBooleanProperty verifyingProperty() {
        return verifying.getReadOnlyProperty();
    }

    /**
     * What the last verification found, in one sentence, or why it could not run.
     *
     * @return the statement
     */
    public ReadOnlyObjectProperty<String> verifyStatusProperty() {
        return verifyStatus.getReadOnlyProperty();
    }

    /**
     * What the last verification found, one entry per file, the outcome first.
     *
     * @return the rows
     */
    public ReadOnlyObjectProperty<List<String>> verificationProperty() {
        return verificationRows.getReadOnlyProperty();
    }
}
