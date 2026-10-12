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
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.value.ObservableValue;
import org.cometgui.domain.secrets.Credential;
import org.cometgui.ui.viewmodel.NonNullProperty;
import org.cometgui.ui.viewmodel.results.ResultsRun;
import org.cometgui.ui.viewmodel.results.ResultsViewModel;
import org.cometgui.workflow.limelight.LimelightUploadRefusedException;
import org.cometgui.workflow.limelight.LimelightUploadWatcher;

/**
 * The Limelight section's upload half (phase 12, work unit 7; {@code R-SEC-03}, {@code R-SEC-04};
 * design decisions P12-10, P12-12, P12-15, P12-17, P12-19): one of the chosen run's converted
 * Limelight XML files sent to a Limelight server's project, only after the exact destination has
 * been shown and the scientist has pressed Send.
 *
 * <h2>Two steps, and nothing is sent by the first</h2>
 *
 * <p>{@link #prepare()} -- the Upload control -- asks the port to check the destination and shows
 * it: the normalised server address, the project id and its page, the file, its size and its
 * SHA-256 ({@link #destinationShownProperty()}). It sends nothing. Only {@link #send()} -- the
 * "Send to Limelight" control, and only while a destination is shown -- sends, and it hands back
 * exactly the destination shown, once: a second upload needs a second Upload and a second Send.
 * {@link #cancel()} withdraws the destination, and so does any change to the file, the server, the
 * project or the key, and choosing another run: what was shown is then no longer what would be
 * sent. A destination being checked when one of those changes is dropped when its answer arrives.
 * Nothing else in the application starts an upload: no run and no conversion does.
 *
 * <h2>The submit key</h2>
 *
 * <p>The key is typed into a password field and handed here once, by {@link #takeKey(String)}, the
 * moment the field gives it up; it is admitted to the session through the port at once -- so the
 * one redactor knows it before anything else can see it -- and only the {@link Credential} is kept.
 * No property of this class holds the key, and no text it publishes contains it. The key is used
 * for the next upload and then dropped, unless "remember" was set and the server queued the import:
 * then the port keeps it for that server in this computer's credential store, whose explanation is
 * {@link #storeTextProperty()}. A key kept for the server is used without being shown, and can be
 * forgotten.
 *
 * <h2>Cancelling</h2>
 *
 * <p>An upload cannot be stopped once sent: the upload service takes no cancellation, and stopping
 * the thread part-way would leave the upload without the record that says how it ended. The section
 * says so while it sends ({@link #NO_CANCEL}). Every protocol call is bounded by the client's own
 * time limits.
 *
 * <h2>Two threads</h2>
 *
 * <p>The port is called only on {@code background}, except {@link LimelightUploadPort#admit}, which
 * is in memory and never blocks; every answer, log line and byte count is applied on {@code ui}.
 */
public final class LimelightUploadViewModel {

    /** The files text when no run is open. */
    public static final String NO_RUN = "No run's results are open in the Results section.";

    /** The store text before the store has been read. */
    public static final String STORE_NOT_READ =
            "Where a kept submit key is held has not been read yet.";

    /** What the server field's status says while it is empty. */
    public static final String SERVER_HELP =
            "Type the address of the Limelight web application, as your browser shows it, for"
                    + " example https://limelight.example.org/limelight. Only https is used, except"
                    + " to this computer.";

    /** What the project field's status says while it is empty. */
    public static final String PROJECT_HELP =
            "Type the Limelight project's id: the number at the end of the project's address,"
                    + " .../d/pg/project/<id>. It is typed, not chosen from a list, because"
                    + " Limelight lists projects only to a signed-in browser, never to a submit"
                    + " key.";

    /** The key text when no key is available. */
    public static final String NO_KEY =
            "No submit key: type your Limelight submit-import key. It is never shown.";

    /** The key text when a key was typed. */
    public static final String KEY_TYPED =
            "A submit key was typed (not shown). It is used for the next upload and then"
                    + " forgotten, unless \"Remember the key\" is ticked.";

    /** What the section says while an upload is being sent, about stopping it. */
    public static final String NO_CANCEL =
            "An upload cannot be stopped once it has been sent: the upload service has no way to"
                    + " stop it part-way and still record how it ended. It ends when the server"
                    + " answers, or at the client's time limits.";

    /** The outcome before anything was prepared. */
    public static final String NOTHING_SENT = "Nothing has been sent to Limelight in this session.";

    /** The outcome when a destination is shown. */
    public static final String CHECK_AND_SEND =
            "Nothing has been sent. Check the destination below; press \"Send to Limelight\" to"
                    + " upload this file there, or Cancel.";

    /** The outcome when a shown destination was withdrawn because an input changed. */
    public static final String DESTINATION_CHANGED =
            "The destination shown was withdrawn because the file, the server, the project, the"
                    + " key or the run changed; nothing was sent. Press Upload to check the new"
                    + " one.";

    /** The outcome after Cancel. */
    public static final String CANCELLED = "Cancelled: nothing was sent to Limelight.";

    /** The outcome while a destination is being checked. */
    public static final String CHECKING =
            "Checking the destination. Nothing is sent until you press \"Send to Limelight\".";

    /** How many of the upload log's lines are kept on screen. */
    public static final int LOG_LINES = 500;

    private final LimelightUploadPort port;

    private final Executor background;

    private final Executor ui;

    // ---- state, on the interface thread ----

    private Optional<ResultsRun> run = Optional.empty();

    private List<Path> files = List.of();

    private boolean filesRead;

    private String filesFailure;

    private long filesGeneration;

    private Path file;

    private String serverTyped = "";

    private String server;

    private String serverProblem = "";

    private long serverGeneration;

    private String projectTyped = "";

    private Integer project;

    private String projectProblem = "";

    private long projectGeneration;

    /** The key typed in this session, admitted; null when none is held. */
    private Credential typedKey;

    private boolean keyKept;

    /** Whether the key's field holds something not yet taken: never what. */
    private boolean keyFieldFilled;

    private String keyNote = "";

    private boolean remember;

    private LimelightUploadPreview preview;

    private boolean preparing;

    /** Every destination answer older than this is dropped. */
    private long destinationGeneration;

    private boolean sending;

    private long sendGeneration;

    private final Deque<String> logLines = new ArrayDeque<>();

    // ---- published ----

    private final NonNullProperty<List<String>> fileChoices;

    private final NonNullProperty<String> chosenFile;

    private final NonNullProperty<String> filesStatus;

    private final NonNullProperty<String> serverText;

    private final NonNullProperty<String> serverStatus;

    private final NonNullProperty<String> projectText;

    private final NonNullProperty<String> projectStatus;

    private final NonNullProperty<String> keyStatus;

    private final ReadOnlyBooleanWrapper rememberKey;

    private final ReadOnlyBooleanWrapper rememberEnabled;

    private final ReadOnlyBooleanWrapper forgetEnabled;

    private final NonNullProperty<String> storeText;

    private final ReadOnlyBooleanWrapper inputsEnabled;

    private final ReadOnlyBooleanWrapper prepareEnabled;

    private final NonNullProperty<String> prepareRefusal;

    private final ReadOnlyBooleanWrapper destinationShown;

    private final NonNullProperty<String> confirmServer;

    private final NonNullProperty<String> confirmProject;

    private final NonNullProperty<String> confirmPage;

    private final NonNullProperty<String> confirmFile;

    private final NonNullProperty<String> confirmSize;

    private final NonNullProperty<String> confirmSha256;

    private final ReadOnlyBooleanWrapper sendEnabled;

    private final ReadOnlyBooleanWrapper cancelEnabled;

    private final ReadOnlyBooleanWrapper sendingNow;

    private final ReadOnlyDoubleWrapper progress;

    private final NonNullProperty<String> log;

    private final NonNullProperty<String> outcome;

    private final NonNullProperty<String> trackingId;

    private final NonNullProperty<String> importerSubDir;

    private final NonNullProperty<String> projectPage;

    private final NonNullProperty<String> logPath;

    /**
     * The upload half, with nothing read. Nothing is read until {@link #refresh()} or a run is
     * chosen.
     *
     * @param port the application's Limelight upload
     * @param results the Results section, whose chosen run this half uploads from
     * @param converted the conversion half's last valid file ({@link
     *     LimelightViewModel#resultFileProperty()}): when it changes, the run's converted files are
     *     read again
     * @param background where the port is called: never the interface thread
     * @param ui where every answer is applied: the interface thread
     */
    public LimelightUploadViewModel(
            LimelightUploadPort port,
            ResultsViewModel results,
            ObservableValue<String> converted,
            Executor background,
            Executor ui) {
        this.port = Objects.requireNonNull(port, "port");
        Objects.requireNonNull(results, "results");
        Objects.requireNonNull(converted, "converted");
        this.background = Objects.requireNonNull(background, "background");
        this.ui = Objects.requireNonNull(ui, "ui");
        fileChoices = new NonNullProperty<>(this, "fileChoices", List.of());
        chosenFile = new NonNullProperty<>(this, "chosenFile", "");
        filesStatus = new NonNullProperty<>(this, "filesStatus", NO_RUN);
        serverText = new NonNullProperty<>(this, "serverText", "");
        serverStatus = new NonNullProperty<>(this, "serverStatus", SERVER_HELP);
        projectText = new NonNullProperty<>(this, "projectText", "");
        projectStatus = new NonNullProperty<>(this, "projectStatus", PROJECT_HELP);
        keyStatus = new NonNullProperty<>(this, "keyStatus", NO_KEY);
        rememberKey = new ReadOnlyBooleanWrapper(this, "rememberKey", false);
        rememberEnabled = new ReadOnlyBooleanWrapper(this, "rememberEnabled", false);
        forgetEnabled = new ReadOnlyBooleanWrapper(this, "forgetEnabled", false);
        storeText = new NonNullProperty<>(this, "storeText", STORE_NOT_READ);
        inputsEnabled = new ReadOnlyBooleanWrapper(this, "inputsEnabled", true);
        prepareEnabled = new ReadOnlyBooleanWrapper(this, "prepareEnabled", false);
        prepareRefusal = new NonNullProperty<>(this, "prepareRefusal", NO_RUN);
        destinationShown = new ReadOnlyBooleanWrapper(this, "destinationShown", false);
        confirmServer = new NonNullProperty<>(this, "confirmServer", "");
        confirmProject = new NonNullProperty<>(this, "confirmProject", "");
        confirmPage = new NonNullProperty<>(this, "confirmPage", "");
        confirmFile = new NonNullProperty<>(this, "confirmFile", "");
        confirmSize = new NonNullProperty<>(this, "confirmSize", "");
        confirmSha256 = new NonNullProperty<>(this, "confirmSha256", "");
        sendEnabled = new ReadOnlyBooleanWrapper(this, "sendEnabled", false);
        cancelEnabled = new ReadOnlyBooleanWrapper(this, "cancelEnabled", false);
        sendingNow = new ReadOnlyBooleanWrapper(this, "sending", false);
        progress = new ReadOnlyDoubleWrapper(this, "progress", 0);
        log = new NonNullProperty<>(this, "log", "");
        outcome = new NonNullProperty<>(this, "outcome", NOTHING_SENT);
        trackingId = new NonNullProperty<>(this, "trackingId", "");
        importerSubDir = new NonNullProperty<>(this, "importerSubDir", "");
        projectPage = new NonNullProperty<>(this, "projectPage", "");
        logPath = new NonNullProperty<>(this, "logPath", "");
        results.chosenRunProperty().addListener((observable, before, after) -> runChanged(after));
        converted.addListener(
                (observable, before, after) -> {
                    if (run.isPresent()) {
                        readFiles();
                    }
                });
        runChanged(results.chosenRunProperty().get());
    }

    // =========================================================================== actions ====

    /**
     * Reads where a kept key is held, the chosen run's converted files and whether a key is kept
     * for the server, again, on the background executor. The composition root calls this at start.
     */
    public void refresh() {
        background.execute(
                () -> {
                    String explanation;
                    try {
                        explanation = port.credentialStore();
                    } catch (IOException | RuntimeException failed) {
                        explanation =
                                "Where a kept submit key is held could not be decided: "
                                        + describe(failed);
                    }
                    String answer = explanation;
                    ui.execute(() -> storeText.set(answer));
                });
        if (run.isPresent()) {
            readFiles();
        }
        if (server != null) {
            readKeyKept(server, serverGeneration, "");
        }
    }

    /**
     * Chooses which of the run's converted files to upload, by its path as offered. Withdraws a
     * destination shown for another file.
     *
     * @param text the file's path, one of {@link #fileChoicesProperty()}
     * @return {@code true} if it is now the chosen file
     */
    public boolean chooseFile(String text) {
        Objects.requireNonNull(text, "text");
        if (sending) {
            return false;
        }
        for (Path offered : files) {
            if (offered.toString().equals(text)) {
                if (!offered.equals(file)) {
                    file = offered;
                    withdraw(DESTINATION_CHANGED);
                }
                publish();
                return true;
            }
        }
        return false;
    }

    /**
     * Commits the server address as typed: checked and normalised by the workflow's rule, through
     * the port. Withdraws a destination shown for another address.
     *
     * @param text the address as typed
     */
    public void editServer(String text) {
        Objects.requireNonNull(text, "text");
        if (sending || text.equals(serverTyped)) {
            return;
        }
        serverTyped = text;
        serverText.set(text);
        server = null;
        serverProblem = "";
        keyKept = false;
        keyNote = "";
        serverGeneration++;
        long mine = serverGeneration;
        withdraw(DESTINATION_CHANGED);
        publish();
        if (text.isBlank()) {
            return;
        }
        background.execute(
                () -> {
                    String normal;
                    try {
                        normal = port.server(text);
                    } catch (LimelightUploadRefusedException | RuntimeException refused) {
                        String why = describe(refused);
                        ui.execute(() -> serverChecked(mine, null, why));
                        return;
                    }
                    ui.execute(() -> serverChecked(mine, normal, ""));
                    readKeyKept(normal, mine, "");
                });
    }

    /**
     * Commits the project id as typed: checked by the workflow's rule, through the port. Withdraws
     * a destination shown for another project.
     *
     * @param text the id as typed
     */
    public void editProject(String text) {
        Objects.requireNonNull(text, "text");
        if (sending || text.equals(projectTyped)) {
            return;
        }
        projectTyped = text;
        projectText.set(text);
        project = null;
        projectProblem = "";
        projectGeneration++;
        long mine = projectGeneration;
        withdraw(DESTINATION_CHANGED);
        publish();
        if (text.isBlank()) {
            return;
        }
        background.execute(
                () -> {
                    int id;
                    try {
                        id = port.project(text);
                    } catch (LimelightUploadRefusedException | RuntimeException refused) {
                        String why = describe(refused);
                        ui.execute(() -> projectChecked(mine, null, why));
                        return;
                    }
                    ui.execute(() -> projectChecked(mine, id, ""));
                });
    }

    /**
     * Takes the submit key the moment its password field gives it up: admitted to the session at
     * once through the port -- registered with the one redactor before anything else holds it --
     * and kept only as a {@link Credential}. The caller clears the field. A blank text is not a key
     * and changes nothing. Withdraws a destination shown with another key.
     *
     * @param typed the key's characters
     * @return {@code true} if the key was taken
     */
    public boolean takeKey(String typed) {
        Objects.requireNonNull(typed, "typed");
        if (sending || typed.isEmpty()) {
            return false;
        }
        Credential admitted;
        try {
            admitted = port.admit(typed);
        } catch (RuntimeException refused) {
            typedKey = null;
            remember = false;
            keyNote = "The submit key was not taken: " + describe(refused) + ".";
            withdraw(DESTINATION_CHANGED);
            publish();
            return false;
        }
        typedKey = admitted;
        keyNote = "";
        withdraw(DESTINATION_CHANGED);
        publish();
        return true;
    }

    /**
     * Whether the key's password field holds text not yet taken -- never the text -- so that Upload
     * can be pressed with the key still in its field; Upload takes it first.
     *
     * @param filled whether the field holds anything
     */
    public void keyFieldFilled(boolean filled) {
        keyFieldFilled = filled;
        publish();
    }

    /**
     * Whether to keep the typed key for the server once the server has queued an import sent with
     * it. Refused while no key was typed.
     *
     * @param on whether to keep it
     * @return {@code true} if the choice is now {@code on}
     */
    public boolean setRemember(boolean on) {
        if (on && (typedKey == null || sending)) {
            return false;
        }
        remember = on;
        publish();
        return true;
    }

    /**
     * Forgets the key kept for the server, through the port. Withdraws a destination shown.
     *
     * @return {@code true} if the request was sent
     */
    public boolean forgetKey() {
        if (!forgetEnabled.get()) {
            return false;
        }
        String target = server;
        long mine = serverGeneration;
        withdraw(DESTINATION_CHANGED);
        background.execute(
                () -> {
                    String note;
                    try {
                        port.forgetKey(target);
                        note = "The submit key kept for " + target + " was forgotten.";
                    } catch (IOException | RuntimeException failed) {
                        note =
                                "The submit key kept for "
                                        + target
                                        + " could not be forgotten: "
                                        + describe(failed);
                    }
                    readKeyKept(target, mine, note);
                });
        publish();
        return true;
    }

    /**
     * The Upload control: asks the port to check the destination and shows it. <strong>Sends
     * nothing.</strong> Refused, with the reason in {@link #outcomeProperty()}, when {@link
     * #prepareRefusalProperty()} is not empty.
     *
     * @return {@code true} if the destination is being checked
     */
    public boolean prepare() {
        String refusal = prepareRefusal.get();
        if (!refusal.isEmpty()) {
            outcome.set("Nothing was checked and nothing was sent: " + refusal);
            return false;
        }
        withdraw(CHECKING);
        destinationGeneration++;
        long mine = destinationGeneration;
        preparing = true;
        ResultsRun target = run.orElseThrow();
        Path chosen = file;
        String to = serverTyped;
        String into = projectTyped;
        outcome.set(CHECKING);
        publish();
        background.execute(
                () -> {
                    LimelightUploadPreview checked = null;
                    String failure = null;
                    try {
                        checked = port.prepare(target, chosen, to, into);
                    } catch (IOException | RuntimeException refused) {
                        failure = describe(refused);
                    }
                    LimelightUploadPreview answer = checked;
                    String why = failure;
                    ui.execute(() -> prepared(mine, answer, why));
                });
        return true;
    }

    /**
     * The "Send to Limelight" control: uploads the destination shown -- exactly that one, once --
     * with the typed key or the one kept for the server. The scientist's explicit action; the only
     * way anything is sent.
     *
     * @return {@code true} if the upload started
     */
    public boolean send() {
        if (preview == null || sending) {
            return false;
        }
        if (typedKey == null && !keyKept) {
            outcome.set("Nothing was sent: " + NO_KEY);
            return false;
        }
        LimelightUploadPreview confirmed = preview;
        preview = null;
        sending = true;
        sendGeneration++;
        long mine = sendGeneration;
        Optional<Credential> key = Optional.ofNullable(typedKey);
        boolean keep = remember && typedKey != null;
        logLines.clear();
        log.set("");
        progress.set(0);
        trackingId.set("");
        importerSubDir.set("");
        projectPage.set("");
        logPath.set("");
        outcome.set(
                "Sending "
                        + confirmed.file()
                        + " to "
                        + confirmed.server()
                        + ", project "
                        + confirmed.projectId()
                        + ". "
                        + NO_CANCEL);
        publish();
        LimelightUploadWatcher watcher = new Marshalled(this, mine);
        background.execute(
                () -> {
                    LimelightUploaded result = null;
                    String failure = null;
                    boolean before = false;
                    try {
                        result = port.send(confirmed, key, keep, watcher);
                    } catch (LimelightUploadRefusedException refused) {
                        failure = describe(refused);
                        before = true;
                    } catch (IOException | RuntimeException failed) {
                        failure = describe(failed);
                    }
                    LimelightUploaded answer = result;
                    String why = failure;
                    boolean nothingSent = before;
                    ui.execute(() -> sent(mine, confirmed, answer, why, nothingSent));
                });
        return true;
    }

    /**
     * The Cancel control: withdraws the destination shown, or being checked. Nothing is sent.
     *
     * @return {@code true} if there was one to withdraw
     */
    public boolean cancel() {
        if (sending || (preview == null && !preparing)) {
            return false;
        }
        withdraw(CANCELLED);
        publish();
        return true;
    }

    // ========================================================================= published ====

    /**
     * The chosen run's converted Limelight XML files, newest first, as paths.
     *
     * @return the read-only property; empty until a conversion exists
     */
    public ReadOnlyObjectProperty<List<String>> fileChoicesProperty() {
        return fileChoices.getReadOnlyProperty();
    }

    /**
     * The file chosen for upload, or empty text.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<String> chosenFileProperty() {
        return chosenFile.getReadOnlyProperty();
    }

    /**
     * How many files can be uploaded, or why none is offered.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<String> filesStatusProperty() {
        return filesStatus.getReadOnlyProperty();
    }

    /**
     * The server address as typed.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<String> serverTextProperty() {
        return serverText.getReadOnlyProperty();
    }

    /**
     * The normalised server address, or why it cannot be used.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<String> serverStatusProperty() {
        return serverStatus.getReadOnlyProperty();
    }

    /**
     * The project id as typed.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<String> projectTextProperty() {
        return projectText.getReadOnlyProperty();
    }

    /**
     * The project id, or why it cannot be used, or how to find it.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<String> projectStatusProperty() {
        return projectStatus.getReadOnlyProperty();
    }

    /**
     * Which key the next upload uses -- typed, kept for the server, or none -- never the key.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<String> keyStatusProperty() {
        return keyStatus.getReadOnlyProperty();
    }

    /**
     * Whether the typed key is to be kept for the server once an upload with it is submitted.
     *
     * @return the read-only property
     */
    public ReadOnlyBooleanProperty rememberKeyProperty() {
        return rememberKey.getReadOnlyProperty();
    }

    /**
     * Whether "remember" can be set: a key was typed and nothing is being sent.
     *
     * @return the read-only property
     */
    public ReadOnlyBooleanProperty rememberEnabledProperty() {
        return rememberEnabled.getReadOnlyProperty();
    }

    /**
     * Whether a key is kept for the server and can be forgotten.
     *
     * @return the read-only property
     */
    public ReadOnlyBooleanProperty forgetEnabledProperty() {
        return forgetEnabled.getReadOnlyProperty();
    }

    /**
     * Where a kept key is held on this computer, and why there.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<String> storeTextProperty() {
        return storeText.getReadOnlyProperty();
    }

    /**
     * Whether the file, server, project and key can be changed: nothing is being sent.
     *
     * @return the read-only property
     */
    public ReadOnlyBooleanProperty inputsEnabledProperty() {
        return inputsEnabled.getReadOnlyProperty();
    }

    /**
     * Whether the Upload control can check a destination now.
     *
     * @return the read-only property
     */
    public ReadOnlyBooleanProperty prepareEnabledProperty() {
        return prepareEnabled.getReadOnlyProperty();
    }

    /**
     * Why the Upload control cannot check a destination now.
     *
     * @return the read-only property; empty text when it can
     */
    public ReadOnlyObjectProperty<String> prepareRefusalProperty() {
        return prepareRefusal.getReadOnlyProperty();
    }

    /**
     * Whether a checked destination is shown: waiting for Send or Cancel, or being sent.
     *
     * @return the read-only property
     */
    public ReadOnlyBooleanProperty destinationShownProperty() {
        return destinationShown.getReadOnlyProperty();
    }

    /**
     * The destination's normalised server address.
     *
     * @return the read-only property; empty when none is shown
     */
    public ReadOnlyObjectProperty<String> confirmServerProperty() {
        return confirmServer.getReadOnlyProperty();
    }

    /**
     * The destination's project id.
     *
     * @return the read-only property; empty when none is shown
     */
    public ReadOnlyObjectProperty<String> confirmProjectProperty() {
        return confirmProject.getReadOnlyProperty();
    }

    /**
     * The destination project's page.
     *
     * @return the read-only property; empty when none is shown
     */
    public ReadOnlyObjectProperty<String> confirmPageProperty() {
        return confirmPage.getReadOnlyProperty();
    }

    /**
     * The file that would be sent.
     *
     * @return the read-only property; empty when none is shown
     */
    public ReadOnlyObjectProperty<String> confirmFileProperty() {
        return confirmFile.getReadOnlyProperty();
    }

    /**
     * The file's size, in bytes.
     *
     * @return the read-only property; empty when none is shown
     */
    public ReadOnlyObjectProperty<String> confirmSizeProperty() {
        return confirmSize.getReadOnlyProperty();
    }

    /**
     * The file's SHA-256, as hashed when the destination was checked.
     *
     * @return the read-only property; empty when none is shown
     */
    public ReadOnlyObjectProperty<String> confirmSha256Property() {
        return confirmSha256.getReadOnlyProperty();
    }

    /**
     * Whether "Send to Limelight" can be pressed: a destination is shown.
     *
     * @return the read-only property
     */
    public ReadOnlyBooleanProperty sendEnabledProperty() {
        return sendEnabled.getReadOnlyProperty();
    }

    /**
     * Whether Cancel can be pressed: a destination is shown or being checked.
     *
     * @return the read-only property
     */
    public ReadOnlyBooleanProperty cancelEnabledProperty() {
        return cancelEnabled.getReadOnlyProperty();
    }

    /**
     * Whether an upload is being sent.
     *
     * @return the read-only property
     */
    public ReadOnlyBooleanProperty sendingProperty() {
        return sendingNow.getReadOnlyProperty();
    }

    /**
     * How much of the file has been sent, 0 to 1.
     *
     * @return the read-only property
     */
    public ReadOnlyDoubleProperty progressProperty() {
        return progress.getReadOnlyProperty();
    }

    /**
     * The upload log's lines of the last upload, as written -- already through the one redactor --
     * newest last.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<String> logProperty() {
        return log.getReadOnlyProperty();
    }

    /**
     * What the last upload, or the last check of a destination, did.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<String> outcomeProperty() {
        return outcome.getReadOnlyProperty();
    }

    /**
     * The server's tracking id ({@code uploadKey}) of the last submitted upload.
     *
     * @return the read-only property; empty unless the last upload was submitted
     */
    public ReadOnlyObjectProperty<String> trackingIdProperty() {
        return trackingId.getReadOnlyProperty();
    }

    /**
     * The server's {@code importerSubDir} of the last submitted upload.
     *
     * @return the read-only property; empty unless the last upload was submitted
     */
    public ReadOnlyObjectProperty<String> importerSubDirProperty() {
        return importerSubDir.getReadOnlyProperty();
    }

    /**
     * Where the last upload's import is followed.
     *
     * @return the read-only property; empty before the first upload
     */
    public ReadOnlyObjectProperty<String> projectPageProperty() {
        return projectPage.getReadOnlyProperty();
    }

    /**
     * The last upload's log file.
     *
     * @return the read-only property; empty before the first upload has ended
     */
    public ReadOnlyObjectProperty<String> logPathProperty() {
        return logPath.getReadOnlyProperty();
    }

    // ========================================================================= internals ====

    /** The Results section chose another run, or none, or the same run's state changed. */
    private void runChanged(Optional<ResultsRun> chosen) {
        boolean another = !chosen.map(ResultsRun::id).equals(run.map(ResultsRun::id));
        run = chosen;
        if (another) {
            files = List.of();
            file = null;
            filesRead = false;
            filesFailure = null;
            withdraw(DESTINATION_CHANGED);
        }
        publish();
        if (chosen.isPresent()) {
            readFiles();
        } else {
            filesGeneration++;
        }
    }

    private void readFiles() {
        filesGeneration++;
        long mine = filesGeneration;
        ResultsRun target = run.orElseThrow();
        background.execute(
                () -> {
                    List<Path> found = null;
                    String failure = null;
                    try {
                        found = List.copyOf(port.convertedFiles(target));
                    } catch (IOException | RuntimeException failed) {
                        failure = describe(failed);
                    }
                    List<Path> answer = found;
                    String why = failure;
                    ui.execute(() -> filesRead(mine, answer, why));
                });
    }

    private void filesRead(long answered, List<Path> found, String failure) {
        if (answered != filesGeneration) {
            return;
        }
        filesRead = true;
        filesFailure = failure;
        List<Path> now = found == null ? List.of() : found;
        if (!now.equals(files)) {
            files = now;
            Path newest = now.isEmpty() ? null : now.get(0);
            if (!Objects.equals(newest, file)) {
                file = newest;
                withdraw(DESTINATION_CHANGED);
            }
        }
        publish();
    }

    private void serverChecked(long answered, String normal, String problem) {
        if (answered != serverGeneration) {
            return;
        }
        server = normal;
        serverProblem = problem;
        publish();
    }

    private void readKeyKept(String target, long generation, String note) {
        background.execute(
                () -> {
                    boolean kept = false;
                    String words = note;
                    try {
                        kept = port.keyKept(target);
                    } catch (IOException | RuntimeException failed) {
                        words =
                                (note.isEmpty() ? "" : note + " ")
                                        + "Whether a submit key is kept for "
                                        + target
                                        + " could not be read: "
                                        + describe(failed);
                    }
                    boolean answer = kept;
                    String said = words;
                    ui.execute(() -> keyKeptRead(generation, answer, said));
                });
    }

    private void keyKeptRead(long answered, boolean kept, String note) {
        if (answered != serverGeneration) {
            return;
        }
        keyKept = kept;
        keyNote = note;
        publish();
    }

    private void projectChecked(long answered, Integer id, String problem) {
        if (answered != projectGeneration) {
            return;
        }
        project = id;
        projectProblem = problem;
        publish();
    }

    /**
     * Withdraws the destination shown or being checked: what was shown is no longer what would be
     * sent. Says why only when there was one.
     */
    private void withdraw(String why) {
        boolean had = preview != null || preparing;
        destinationGeneration++;
        preview = null;
        preparing = false;
        confirmServer.set("");
        confirmProject.set("");
        confirmPage.set("");
        confirmFile.set("");
        confirmSize.set("");
        confirmSha256.set("");
        if (had) {
            outcome.set(why);
        }
    }

    private void prepared(long answered, LimelightUploadPreview checked, String failure) {
        if (answered != destinationGeneration) {
            return;
        }
        preparing = false;
        if (checked == null) {
            outcome.set("The destination was not accepted, and nothing was sent: " + failure);
            publish();
            return;
        }
        preview = checked;
        confirmServer.set(checked.server());
        confirmProject.set(Integer.toString(checked.projectId()));
        confirmPage.set(checked.projectPage());
        confirmFile.set(checked.file().toString());
        confirmSize.set(checked.size() + " bytes");
        confirmSha256.set(checked.sha256());
        outcome.set(CHECK_AND_SEND);
        publish();
    }

    private void logged(long upload, String line) {
        if (upload != sendGeneration) {
            return;
        }
        logLines.addLast(line);
        while (logLines.size() > LOG_LINES) {
            logLines.removeFirst();
        }
        log.set(String.join("\n", logLines));
    }

    private void bytes(long upload, long sent, long total) {
        if (upload != sendGeneration) {
            return;
        }
        progress.set(total <= 0 ? 1 : Math.min(1, (double) sent / total));
    }

    private void sent(
            long answered,
            LimelightUploadPreview confirmed,
            LimelightUploaded result,
            String failure,
            boolean nothingSent) {
        if (answered != sendGeneration) {
            return;
        }
        sending = false;
        typedKey = null;
        remember = false;
        withdraw("");
        if (result == null) {
            outcome.set(
                    nothingSent
                            ? "Nothing was sent: " + failure
                            : "The upload did not complete: " + failure);
        } else {
            outcome.set(words(confirmed, result));
            logPath.set("The upload's log: " + result.log());
            projectPage.set(result.projectPage());
            if (result.kind() == LimelightUploaded.Kind.SUBMITTED) {
                trackingId.set(result.trackingId());
                importerSubDir.set(result.importerSubDir());
                progress.set(1);
            }
        }
        String note = result == null ? "" : result.keyNote();
        keyNote = note;
        publish();
        if (server != null) {
            readKeyKept(server, serverGeneration, note);
        }
    }

    private static String words(LimelightUploadPreview to, LimelightUploaded result) {
        String said =
                switch (result.kind()) {
                    case SUBMITTED ->
                            "Submitted to Limelight: the server queued the import of "
                                    + to.file()
                                    + " into project "
                                    + to.projectId()
                                    + " on "
                                    + to.server()
                                    + ". Its tracking id (uploadKey) is "
                                    + result.trackingId()
                                    + ". The import runs on the server; follow it on the project's"
                                    + " page, "
                                    + result.projectPage()
                                    + ".";
                    case REFUSED ->
                            "The Limelight server refused the upload at step "
                                    + result.step()
                                    + " (HTTP "
                                    + result.httpStatus()
                                    + "), and no import was queued. Its flags: "
                                    + (result.flags().isEmpty()
                                            ? "none"
                                            : String.join(", ", result.flags()))
                                    + ". The server said: "
                                    + (result.message().isEmpty()
                                            ? "(no message)"
                                            : result.message());
                    case FAILED ->
                            "The upload failed at step "
                                    + result.step()
                                    + " with no answer from the Limelight server, and no import"
                                    + " was queued: "
                                    + result.message();
                };
        return result.keyNote().isEmpty() ? said : said + " " + result.keyNote();
    }

    private void publish() {
        List<String> choices = new ArrayList<>(files.size());
        for (Path offered : files) {
            choices.add(offered.toString());
        }
        fileChoices.set(List.copyOf(choices));
        chosenFile.set(file == null ? "" : file.toString());
        filesStatus.set(filesWords());
        serverStatus.set(serverWords());
        projectStatus.set(projectWords());
        String base;
        if (typedKey != null) {
            base = KEY_TYPED;
        } else if (keyKept) {
            base =
                    "A submit key for "
                            + server
                            + " is kept on this computer; it is used without being shown.";
        } else {
            base = NO_KEY;
        }
        keyStatus.set(keyNote.isEmpty() ? base : keyNote + " " + base);
        rememberKey.set(remember);
        rememberEnabled.set(typedKey != null && !sending);
        forgetEnabled.set(keyKept && server != null && !sending);
        inputsEnabled.set(!sending);
        String refusal = refusal();
        prepareRefusal.set(refusal);
        prepareEnabled.set(refusal.isEmpty());
        destinationShown.set(preview != null || sending);
        sendEnabled.set(preview != null && !sending);
        cancelEnabled.set((preview != null || preparing) && !sending);
        sendingNow.set(sending);
    }

    private String filesWords() {
        if (run.isEmpty()) {
            return NO_RUN + " Open a run there, convert it above, then upload it.";
        }
        String id = run.get().id().value();
        if (!filesRead) {
            return "Reading which Limelight XML files run " + id + "'s conversions produced.";
        }
        if (filesFailure != null) {
            return "Which Limelight XML files run "
                    + id
                    + "'s conversions produced could not be read: "
                    + filesFailure;
        }
        if (files.isEmpty()) {
            return "Nothing can be uploaded from run "
                    + id
                    + " yet: no Limelight XML has been converted from it. Convert it above"
                    + " first.";
        }
        return files.size()
                + (files.size() == 1 ? " Limelight XML file was" : " Limelight XML files were")
                + " converted from run "
                + id
                + ", newest first.";
    }

    private String serverWords() {
        if (server != null) {
            return "Uploads go to " + server + ".";
        }
        if (!serverProblem.isEmpty()) {
            return "This server address cannot be used: " + serverProblem;
        }
        if (serverTyped.isBlank()) {
            return SERVER_HELP;
        }
        return "Checking the server address.";
    }

    private String projectWords() {
        if (project != null) {
            return "Project " + project + ".";
        }
        if (!projectProblem.isEmpty()) {
            return "This project id cannot be used: " + projectProblem;
        }
        if (projectTyped.isBlank()) {
            return PROJECT_HELP;
        }
        return "Checking the project id.";
    }

    private String refusal() {
        if (sending) {
            return "An upload is being sent.";
        }
        if (run.isEmpty()) {
            return NO_RUN;
        }
        if (run.get().executing()) {
            return "Run "
                    + run.get().id().value()
                    + " is executing: its results are not final. Upload when it has ended.";
        }
        if (!filesRead || filesFailure != null || file == null) {
            // a file is chosen whenever the run has any: none chosen means none converted
            return filesWords();
        }
        if (server == null) {
            return serverWords();
        }
        if (project == null) {
            return projectWords();
        }
        if (typedKey == null && !keyKept && !keyFieldFilled) {
            return NO_KEY;
        }
        if (preparing) {
            return CHECKING;
        }
        return "";
    }

    private static String describe(Exception failed) {
        String message = failed.getMessage();
        return message == null || message.isBlank() ? failed.getClass().getSimpleName() : message;
    }

    /** The watcher handed to the port: every line and byte count is applied on the interface. */
    private static final class Marshalled implements LimelightUploadWatcher {

        private final LimelightUploadViewModel owner;

        private final long upload;

        Marshalled(LimelightUploadViewModel owner, long upload) {
            this.owner = owner;
            this.upload = upload;
        }

        @Override
        public void line(String line) {
            owner.ui.execute(() -> owner.logged(upload, line));
        }

        @Override
        public void bytesSent(long sent, long total) {
            owner.ui.execute(() -> owner.bytes(upload, sent, total));
        }
    }
}
