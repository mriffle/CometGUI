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

package org.cometgui.app.gui;

import static org.cometgui.app.gui.ParameterEditorApp.enter;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Labeled;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import org.cometgui.app.config.ApplicationServices;
import org.cometgui.app.config.PlatformFileSystemAccess;
import org.cometgui.app.config.RunWiring;
import org.cometgui.app.config.ToolManagerWiring;
import org.cometgui.app.testing.ArtefactMirror;
import org.cometgui.app.testing.InstalledComet;
import org.cometgui.app.testing.LimelightFiles;
import org.cometgui.app.testing.LoopbackArtefactServer;
import org.cometgui.app.testing.RealPercolators;
import org.cometgui.app.testing.RealSearch;
import org.cometgui.app.testing.RealSearch.LaunchRecorder;
import org.cometgui.app.testing.SecretSweep;
import org.cometgui.app.testing.StagedAndManaged;
import org.cometgui.app.uidriver.FxUiDriver;
import org.cometgui.app.uidriver.TestFxUiDriver;
import org.cometgui.domain.build.BuildIdentity;
import org.cometgui.domain.log.BoundedMessageLog;
import org.cometgui.domain.log.LogMessage;
import org.cometgui.domain.platform.GlibcVersion;
import org.cometgui.domain.ports.EnvironmentReader;
import org.cometgui.domain.tools.HostArchitecture;
import org.cometgui.domain.tools.HostOperatingSystem;
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.domain.tools.ToolManager;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.install.probe.HostRuntimeVersions;
import org.cometgui.install.registry.ArtefactRecord;
import org.cometgui.tools.limelight.fake.FakeLimelightServer;
import org.cometgui.tools.process.ProcessService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * Phase 12 gate items 6 and 7 ({@code R-SEC-03}, {@code R-SEC-04}, {@code AC-LL-06}) through the
 * interface, with real binaries end to end and the one loopback fake ({@code D-007}): a real search
 * -- the real Comet and Percolator 3.07.1, Limelight on -- run through the Run section and
 * converted through the Limelight section from an empty tool cache, then uploaded from the
 * Limelight section.
 *
 * <ul>
 *   <li><strong>Gate 6.</strong> The fake is listening before the run starts, and has received
 *       nothing when the run has ended and nothing when the conversion has. With the server, the
 *       project and the key typed, Upload sends nothing and shows the destination -- the normalised
 *       server, the project and its page, the file, its size and the SHA-256 this test computes;
 *       Cancel sends nothing; a changed project withdraws it. With the fake refusing the key, Send
 *       shows the server's flag and words and the run's {@code events.log} gains {@code
 *       limelight.unuploaded}; with the key accepted, Send uploads -- the fake validates every
 *       request against the wire schema and the file against the converter's own XSD -- and the
 *       screen shows the fake's own tracking id, which {@code limelight.uploaded} records with the
 *       file's SHA-256. The key, remembered, is then used for a second upload without being typed
 *       or shown. A Tab walk reaches every upload control.
 *   <li><strong>Gate 7.</strong> A distinctive seeded key ({@code CGSEED-} and 48 random
 *       characters, made for this run) is typed for a refused and two submitted uploads, and
 *       remembered; the Results section exports through its buttons. Then no text of any node in
 *       the window holds it, nor any console message; the application is stopped; and every file
 *       under the project (every run, export and log), the tool cache, the application's data
 *       directory, this test's temporary directory, and every file written since this class started
 *       under the JVM's temporary directory and the user's home -- each found by walking the tree
 *       from its root -- is searched for the key in every form {@link SecretSweep} knows: none
 *       holds it. The search's own control plants each form and finds it.
 * </ul>
 *
 * <p>Every identifier is a hand-typed literal (P7-5). The application sees the real environment
 * without a session bus, so that the credential store is the session store on any computer and no
 * test key is ever written to a real desktop keyring. Files read outside this module: those of
 * {@link RealSearch}, {@link RealPercolators} and {@link ArtefactMirror} -- {@code
 * scratch/fixture/}, {@code scratch/phase05/artefacts/} -- the converter JAR again for the fake's
 * schema, {@code manifests/tools.json} on the class path, and, for gate 7's walk, the JVM's
 * temporary directory and the user's home (files written since the class started only).
 */
@EnabledOnOs(
        value = OS.LINUX,
        disabledReason =
                "only the linux/x86-64 Comet and Percolator binaries have ever been executed in"
                        + " this project")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LimelightUploadUiTest {

    private static final BuildIdentity BUILD =
            BuildIdentity.of("0.0.0-guitest", "unknown", Instant.parse("2026-10-12T00:00:00Z"));

    private static final HostPlatform LINUX =
            new HostPlatform(HostOperatingSystem.LINUX, HostArchitecture.X86_64);

    private static final HostRuntimeVersions DEBIAN_12 =
            new HostRuntimeVersions(
                    Optional.of(GlibcVersion.parse("2.36")),
                    Optional.of(GlibcVersion.parse("3.4.30")));

    private static final String CONVERTER_JAR = "v2.8.1__cometPercolator2LimelightXML.jar";

    private static final String ENGINE_CAN_RUN = "The workflow engine can run this search.";

    /** How long a first-use install and one conversion may take. A bound, not a delay. */
    private static final Duration CONVERT_BOUND = Duration.ofMinutes(5);

    /** How long a check of a destination or an upload to the loopback fake may take. */
    private static final Duration UPLOAD_BOUND = Duration.ofMinutes(2);

    private static final String PROJECT = "7";

    private static final String NOTHING_SENT_YET =
            "Nothing has been sent. Check the destination below; press \"Send to Limelight\" to"
                    + " upload this file there, or Cancel.";

    private static final String KEY_TYPED =
            "A submit key was typed (not shown). It is used for the next upload and then"
                    + " forgotten, unless \"Remember the key\" is ticked.";

    /** The seeded key: a fixed, recognisable prefix and 48 random characters made for this run. */
    private static final String SECRET = seed();

    @TempDir private static Path scratch;

    private static final AtomicBoolean STOPPED = new AtomicBoolean();

    private static FileTime started;

    private static ParameterEditorApp app;

    private static FxUiDriver driver;

    private static BoundedMessageLog console;

    private static LoopbackArtefactServer served;

    private static FakeLimelightServer fake;

    private static Path project;

    private static Path cacheRoot;

    private static String seed() {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        SecureRandom random = new SecureRandom();
        StringBuilder seed = new StringBuilder("CGSEED-");
        for (int index = 0; index < 48; index++) {
            seed.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return seed.toString();
    }

    /** The real environment, without a session bus: no desktop keyring can be reached. */
    private static EnvironmentReader withoutSessionBus(EnvironmentReader real) {
        return new EnvironmentReader() {
            @Override
            public Optional<String> environmentVariable(String name) {
                return "DBUS_SESSION_BUS_ADDRESS".equals(name)
                        ? Optional.empty()
                        : real.environmentVariable(name);
            }

            @Override
            public Optional<String> systemProperty(String name) {
                return real.systemProperty(name);
            }
        };
    }

    @BeforeAll
    static void launch() throws IOException {
        started = FileTime.from(Instant.now().minusSeconds(2));
        Path root = scratch.toRealPath();
        Path comet = RealSearch.stageComet(root.resolve("bin/comet"));
        ToolOffer percolator = RealPercolators.installed3071(root.resolve("bin/percolator"));
        Path inputs = Files.createDirectories(root.resolve("inputs"));
        List<Path> spectra = RealSearch.spectra(inputs);
        Path subset = RealSearch.subset(inputs.resolve("subset.fasta"));
        project = root.resolve("project");
        cacheRoot = root.resolve("cometgui-data");
        ArtefactRecord converter =
                ArtefactMirror.record(ToolName.LIMELIGHT_CONVERTER, "2.8.1", LINUX);
        served =
                new LoopbackArtefactServer()
                        .serve(converter.url(), ArtefactMirror.artefact(CONVERTER_JAR));
        fake = FakeLimelightServer.start(SECRET, Integer.parseInt(PROJECT));
        LaunchRecorder launches = new LaunchRecorder(new ProcessService(Clock.systemUTC()));
        ToolManager managed =
                ToolManagerWiring.toolManager(
                        LINUX,
                        DEBIAN_12,
                        cacheRoot,
                        launches,
                        Clock.systemUTC(),
                        ToolManagerWiring.installThreads(),
                        served);
        ToolManager tools =
                new StagedAndManaged(
                        InstalledComet.at(RealSearch.RELEASE, comet).with(percolator),
                        managed,
                        Set.of(ToolName.LIMELIGHT_CONVERTER));
        console = new BoundedMessageLog();
        app =
                ParameterEditorApp.launch(
                        BUILD,
                        launches,
                        console,
                        new RunWiring.Setup(() -> tools, project),
                        LimelightUploadUiTest::withoutSessionBus);
        driver = new TestFxUiDriver(app.application());

        app.chooser().spectra(spectra.get(0), spectra.get(1));
        ParameterEditorApp.openEditor(driver);
        driver.clickOn("param-mode-essentials");
        driver.clickOn("ess-spectra-add");
        app.chooser().database(subset);
        driver.clickOn("ess-database_name-choose");
        ParameterEditorApp.choose(
                driver,
                "ess-decoy_search",
                "Concatenated: targets and decoys compete, one result per spectrum");
        enter(driver, "ess-num_threads", "4");
    }

    @AfterAll
    static void stop() throws IOException {
        try {
            if (app != null && !STOPPED.get()) {
                app.stop();
            }
        } finally {
            try {
                if (served != null) {
                    served.close();
                }
            } finally {
                if (fake != null) {
                    fake.close();
                }
            }
        }
    }

    @Test
    @Order(1)
    @DisplayName(
            "gate 6: a real run and a real conversion send nothing to the listening fake; before"
                    + " the conversion nothing is offered to upload, with the reason")
    void aRealRunAndConversionSendNothing() throws IOException {
        driver.clickOn("nav-percolator");
        RunSection.awaitText(
                driver,
                "percolator-offers",
                text -> text.endsWith("can run."),
                RunSection.CHECK_BOUND);
        driver.clickOn("percolator-limelight");
        driver.clickOn("nav-run");
        assertEquals(ENGINE_CAN_RUN, RunSection.awaitEngineAnswer(driver));
        driver.clickOn("run-start");
        String outcome =
                RunSection.awaitText(
                        driver,
                        "run-outcome",
                        text -> text.startsWith("The run "),
                        RunSection.RUN_BOUND);
        Path run = onlyRun();
        assertEquals("The run succeeded: run " + idOf(run) + " in " + run + ".", outcome);
        assertEquals(List.of(), fake.requests(), "the run's completion sent nothing");

        driver.clickOn("nav-results");
        ResultsSection.awaitOpened(driver, idOf(run), ResultsSection.OPEN_BOUND);
        driver.clickOn("nav-limelight");
        String nothing =
                RunSection.awaitText(
                        driver,
                        "limelight-upload-files-status",
                        text -> text.startsWith("Nothing can be uploaded"),
                        RunSection.CHECK_BOUND);
        String storeText =
                RunSection.awaitText(
                        driver,
                        "limelight-upload-store",
                        text -> !text.startsWith("Where a kept submit key is held has not"),
                        RunSection.CHECK_BOUND);
        assertAll(
                "before any conversion",
                () ->
                        assertEquals(
                                "Nothing can be uploaded from run "
                                        + idOf(run)
                                        + " yet: no Limelight XML has been converted from it."
                                        + " Convert it above first.",
                                nothing),
                () -> assertTrue(RunSection.isDisabled(driver, "limelight-upload-prepare")),
                () -> assertTrue(RunSection.isDisabled(driver, "limelight-upload-file")),
                () ->
                        assertTrue(
                                List.of(
                                                "The key is kept for this session only:"
                                                        + " secret-tool (libsecret-tools) is not"
                                                        + " installed, so the desktop keyring"
                                                        + " cannot be reached.",
                                                "The key is kept for this session only: there is"
                                                        + " no desktop session bus, so the desktop"
                                                        + " keyring cannot be reached.")
                                        .contains(storeText),
                                storeText),
                () ->
                        assertEquals(
                                "Nothing has been sent to Limelight in this session.",
                                driver.textOf("limelight-upload-outcome")),
                () -> assertFalse(driver.isVisible("limelight-upload-destination")));

        driver.clickOn("limelight-convert");
        String converted =
                RunSection.awaitText(
                        driver,
                        "limelight-outcome",
                        text ->
                                text.startsWith("Run " + idOf(run) + " was converted")
                                        || text.startsWith("Nothing was converted")
                                        || text.startsWith("The converter's output was not kept")
                                        || text.startsWith("The Limelight converter could not"),
                        CONVERT_BOUND);
        assertTrue(converted.startsWith("Run " + idOf(run) + " was converted"), converted);
        String files =
                RunSection.awaitText(
                        driver,
                        "limelight-upload-files-status",
                        text -> text.startsWith("1 Limelight XML file"),
                        RunSection.CHECK_BOUND);
        assertAll(
                "after the conversion",
                () -> assertEquals(List.of(), fake.requests(), "the conversion sent nothing"),
                () ->
                        assertEquals(
                                "1 Limelight XML file was converted from run "
                                        + idOf(run)
                                        + ", newest first.",
                                files),
                () ->
                        assertEquals(
                                run.resolve("outputs/limelight/limelight-1.xml").toString(),
                                comboValue("limelight-upload-file")),
                () -> assertFalse(driver.isVisible("limelight-upload-destination")),
                () ->
                        assertEquals(
                                List.of(),
                                LimelightFiles.events(run, "limelight.uploading"),
                                "nothing was even about to be sent"));
    }

    @Test
    @Order(2)
    @DisplayName("gate 7's control: the search finds every form of a key when it is planted")
    void theSearchFindsEveryForm(@TempDir Path control) throws IOException {
        List<SecretSweep.Form> forms = SecretSweep.forms(SECRET);
        String variant = "CG SEED+/é?&" + SECRET.substring(7, 20);
        List<SecretSweep.Form> variantForms = SecretSweep.forms(variant);
        Map<Path, String> planted = new LinkedHashMap<>();
        int index = 0;
        for (List<SecretSweep.Form> set : List.of(forms, variantForms)) {
            for (SecretSweep.Form form : set) {
                Path file = control.resolve("form-" + index++ + ".bin");
                byte[] before = "before: ".getBytes(StandardCharsets.US_ASCII);
                byte[] after = " :after".getBytes(StandardCharsets.US_ASCII);
                byte[] bytes = new byte[before.length + form.bytes().length + after.length];
                System.arraycopy(before, 0, bytes, 0, before.length);
                System.arraycopy(form.bytes(), 0, bytes, before.length, form.bytes().length);
                System.arraycopy(
                        after, 0, bytes, before.length + form.bytes().length, after.length);
                Files.write(file, bytes);
                planted.put(file, form.name());
            }
        }
        // the key inside longer Base64 texts, at each of the three alignments, as a writer makes it
        for (int offset = 0; offset < 3; offset++) {
            byte[] inner = ("x".repeat(offset) + SECRET + "tail").getBytes(StandardCharsets.UTF_8);
            Path file = control.resolve("embedded-" + offset + ".b64");
            Files.writeString(
                    file, "{\"blob\": \"" + Base64.getEncoder().encodeToString(inner) + "\"}");
            planted.put(file, "Base64, inside a longer text at byte offset " + offset);
        }
        // across a chunk boundary of the reader
        Path straddling = control.resolve("straddling.bin");
        byte[] big = new byte[4 * 1024 * 1024 + 64];
        byte[] key = SECRET.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(key, 0, big, 4 * 1024 * 1024 - 10, key.length);
        Files.write(straddling, big);
        planted.put(straddling, "UTF-8");
        Path clean = control.resolve("clean.txt");
        Files.writeString(
                clean, SECRET.substring(0, SECRET.length() - 1) + " and " + SECRET.substring(1));

        List<SecretSweep.Form> both = new ArrayList<>(forms);
        both.addAll(variantForms);
        SecretSweep.Walk walk = SecretSweep.walk(control, both, Optional.empty(), Set.of());
        List<String> missed = new ArrayList<>();
        for (Map.Entry<Path, String> plant : planted.entrySet()) {
            boolean found = false;
            for (String hit : walk.hits()) {
                if (hit.startsWith(plant.getKey() + " holds the secret as ")
                        && hit.contains(plant.getValue())) {
                    found = true;
                }
            }
            if (!found) {
                missed.add(plant.getKey().getFileName() + " (" + plant.getValue() + ")");
            }
        }
        assertAll(
                "every planted form is found, and nothing else",
                () -> assertTrue(forms.size() >= 15, "forms: " + forms),
                () -> assertEquals(List.of(), missed, "planted and not found"),
                () -> assertEquals(planted.size(), walk.hits().size(), walk.hits()::toString),
                () -> assertEquals(planted.size() + 1, walk.searched()),
                () ->
                        assertFalse(
                                walk.hits().stream()
                                        .anyMatch(hit -> hit.startsWith(clean.toString())),
                                "a near miss is not a hit"),
                () ->
                        assertFalse(
                                walk.hits().stream().anyMatch(hit -> hit.contains(SECRET)),
                                "a hit never prints the key"),
                () ->
                        assertFalse(
                                java.util.Arrays.equals(
                                        variant.getBytes(StandardCharsets.UTF_8),
                                        urlEncoded(variantForms)),
                                "the variant's URL encoding is a form of its own"));
    }

    @Test
    @Order(3)
    @DisplayName(
            "gate 6: Upload shows the destination and sends nothing; Cancel and a changed field"
                    + " send nothing; a refused key shows the server's words; Send uploads to the"
                    + " fake, which validates it, and the screen shows the fake's tracking id")
    void gate6() throws IOException {
        Path run = onlyRun();
        Path file = run.resolve("outputs/limelight/limelight-1.xml");
        String sha256 = RealSearch.sha256(file);
        String server = fake.baseUrl();
        String page = server + "/d/pg/project/" + PROJECT;
        driver.clickOn("nav-limelight");

        enter(driver, "limelight-upload-server", server);
        assertEquals(
                "Uploads go to " + server + ".",
                RunSection.awaitText(
                        driver,
                        "limelight-upload-server-status",
                        text -> text.startsWith("Uploads go to") || text.startsWith("This server"),
                        UPLOAD_BOUND));
        enter(driver, "limelight-upload-project", PROJECT);
        assertEquals(
                "Project 7.",
                RunSection.awaitText(
                        driver,
                        "limelight-upload-project-status",
                        text -> text.startsWith("Project ") || text.startsWith("This project"),
                        UPLOAD_BOUND));
        typeKey();

        // the keyboard reaches every upload control that can be used now
        driver.clickOn("nav-limelight");
        List<String> reached = new ArrayList<>();
        for (int press = 0; press < 80; press++) {
            driver.tab();
            String focused = driver.focusedNodeId();
            if ("nav-limelight".equals(focused)) {
                break;
            }
            reached.add(focused);
        }
        assertTrue(
                reached.containsAll(
                        List.of(
                                "limelight-upload-file",
                                "limelight-upload-server",
                                "limelight-upload-project",
                                "limelight-upload-key",
                                "limelight-upload-remember",
                                "limelight-upload-prepare")),
                () -> "reached by Tab: " + reached);
        assertEquals(
                KEY_TYPED, driver.textOf("limelight-upload-key-status"), "the walk took nothing");

        // Upload: the destination, and nothing sent
        upload();
        assertAll(
                "the destination shown before anything is sent",
                () -> assertEquals(List.of(), fake.requests(), "Upload sent nothing"),
                () -> assertTrue(driver.isVisible("limelight-upload-destination")),
                () -> assertEquals(server, driver.textOf("limelight-upload-confirm-server")),
                () -> assertEquals(PROJECT, driver.textOf("limelight-upload-confirm-project")),
                () -> assertEquals(page, driver.textOf("limelight-upload-confirm-page")),
                () -> assertEquals(file.toString(), driver.textOf("limelight-upload-confirm-file")),
                () ->
                        assertEquals(
                                Files.size(file) + " bytes",
                                driver.textOf("limelight-upload-confirm-size")),
                () -> assertEquals(sha256, driver.textOf("limelight-upload-confirm-sha256")),
                () -> assertFalse(RunSection.isDisabled(driver, "limelight-upload-send")),
                () -> assertEquals(List.of(), LimelightFiles.events(run, "limelight.uploading")));

        // Cancel: nothing sent
        driver.clickOn("limelight-upload-cancel");
        assertAll(
                () ->
                        assertEquals(
                                "Cancelled: nothing was sent to Limelight.",
                                driver.textOf("limelight-upload-outcome")),
                () -> assertFalse(driver.isVisible("limelight-upload-destination")),
                () -> assertEquals(List.of(), fake.requests(), "Cancel sent nothing"));

        // a changed field withdraws the destination: nothing sent
        upload();
        enter(driver, "limelight-upload-project", "8");
        assertAll(
                () ->
                        assertEquals(
                                "The destination shown was withdrawn because the file, the server,"
                                        + " the project, the key or the run changed; nothing was"
                                        + " sent. Press Upload to check the new one.",
                                driver.textOf("limelight-upload-outcome")),
                () -> assertFalse(driver.isVisible("limelight-upload-destination")),
                () -> assertTrue(RunSection.isDisabled(driver, "limelight-upload-send")),
                () -> assertEquals(List.of(), fake.requests(), "the change sent nothing"));
        enter(driver, "limelight-upload-project", PROJECT);
        RunSection.awaitText(
                driver,
                "limelight-upload-project-status",
                text -> text.equals("Project 7."),
                UPLOAD_BOUND);

        // the server refuses the key: its flag and words, and limelight.unuploaded
        fake.refuseKey(true);
        upload();
        String refused = send(text -> text.startsWith("The Limelight server refused"));
        assertEquals(
                "The Limelight server refused the upload at step auth-test (HTTP 200), and no"
                        + " import was queued. Its flags: userSubmitImportProgramKeyNotFound. The"
                        + " server said: The supplied submit import key (in param"
                        + " '--user-submit-import-key') is invalid.  The first 2 characters are"
                        + " 'CG'.",
                refused);
        List<Map<String, Object>> unuploaded = LimelightFiles.events(run, "limelight.unuploaded");
        assertAll(
                "the refusal, recorded",
                () -> assertEquals(1, unuploaded.size()),
                () -> assertEquals("refused-by-server", unuploaded.get(0).get("upload.failure")),
                () -> assertEquals("auth-test", unuploaded.get(0).get("upload.step")),
                () ->
                        assertEquals(
                                "userSubmitImportProgramKeyNotFound",
                                unuploaded.get(0).get("limelight.server-flags")),
                () ->
                        assertTrue(
                                String.valueOf(unuploaded.get(0).get("limelight.server-message"))
                                        .startsWith("The supplied submit import key")),
                () -> assertEquals(server, unuploaded.get(0).get("limelight.server")),
                () -> assertEquals(sha256, unuploaded.get(0).get("upload.file.sha256")),
                () ->
                        assertEquals(
                                "The upload's log: " + run.resolve("logs/limelight-upload.log"),
                                driver.textOf("limelight-upload-log-path")),
                () -> assertEquals("", driver.textOf("limelight-upload-tracking-id")),
                () -> assertEquals(List.of(), fake.uploads(), "no import was initialised"),
                () ->
                        assertEquals(
                                "No submit key: type your Limelight submit-import key. It is never"
                                        + " shown.",
                                driver.textOf("limelight-upload-key-status"),
                                "the typed key was used once and dropped"));

        // the key accepted, and remembered: Send uploads, and the fake validated what it took
        fake.refuseKey(false);
        typeKey();
        driver.clickOn("limelight-upload-remember");
        upload();
        String submitted = send(text -> text.startsWith("Submitted to Limelight"));
        FakeLimelightServer.Upload received = fake.uploads().get(0);
        List<Map<String, Object>> uploaded = LimelightFiles.events(run, "limelight.uploaded");
        assertAll(
                "submitted",
                () ->
                        assertEquals(
                                "Submitted to Limelight: the server queued the import of "
                                        + file
                                        + " into project 7 on "
                                        + server
                                        + ". Its tracking id (uploadKey) is "
                                        + received.trackingId()
                                        + ". The import runs on the server; follow it on the"
                                        + " project's page, "
                                        + page
                                        + ". The submit key is kept for "
                                        + server
                                        + ": this session only (held in memory, forgotten when"
                                        + " CometGUI closes).",
                                submitted),
                () -> assertEquals("4100", received.trackingId()),
                () ->
                        assertEquals(
                                received.trackingId(),
                                driver.textOf("limelight-upload-tracking-id"),
                                "the fake's own tracking id, on screen"),
                () ->
                        assertEquals(
                                received.importerSubDir(),
                                driver.textOf("limelight-upload-importer-subdir")),
                () -> assertEquals(page, driver.textOf("limelight-upload-project-page")),
                () ->
                        assertEquals(
                                "The upload's log: " + run.resolve("logs/limelight-upload.1.log"),
                                driver.textOf("limelight-upload-log-path")),
                () -> assertEquals("VALID", received.verdict(), "valid against the XSD"),
                () -> assertTrue(received.submitted()),
                () -> assertEquals(sha256, received.receivedSha256()),
                () -> assertEquals(Files.size(file), received.receivedBytes()),
                () -> assertEquals(List.of(), fake.wireViolations()),
                () -> assertEquals(1, uploaded.size()),
                () ->
                        assertEquals(
                                received.trackingId(), uploaded.get(0).get("limelight.upload-key")),
                () -> assertEquals(sha256, uploaded.get(0).get("upload.file.sha256")),
                () ->
                        assertEquals(
                                received.importerSubDir(),
                                uploaded.get(0).get("limelight.importer-sub-dir")),
                () -> assertEquals(PROJECT, uploaded.get(0).get("limelight.project-id")),
                () -> assertTrue(driver.textOf("limelight-upload-log").contains("Submitted.")),
                () -> assertFalse(driver.textOf("limelight-upload-log").contains(SECRET)));

        // the kept key: used without being typed or shown
        assertTrue(
                driver.textOf("limelight-upload-key-status")
                        .endsWith(
                                "A submit key for "
                                        + server
                                        + " is kept on this computer; it is used without being"
                                        + " shown."),
                driver.textOf("limelight-upload-key-status"));
        upload();
        send(text -> text.startsWith("Submitted to Limelight"));
        assertAll(
                "a second upload with the kept key",
                () -> assertEquals(2, fake.uploads().size()),
                () -> assertEquals("4101", driver.textOf("limelight-upload-tracking-id")),
                () -> assertEquals(2, LimelightFiles.events(run, "limelight.uploaded").size()),
                () -> assertEquals(List.of(), fake.wireViolations()));
    }

    @Test
    @Order(4)
    @DisplayName(
            "gate 7 / AC-LL-06: after uploads and exports, the seeded key is in no text of the"
                    + " window, no console message, and no file under any root walked")
    void gate7() throws IOException, TimeoutException {
        Path run = onlyRun();
        driver.clickOn("nav-results");
        ResultsSection.settle(driver, ResultsSection.QUERY_BOUND);
        String table = ResultsSection.export(driver, "results-export-table");
        String weights = ResultsSection.export(driver, "results-export-weights");
        assertAll(
                () -> assertTrue(table.startsWith("Exported "), table),
                () -> assertTrue(weights.startsWith("Exported "), weights));

        // the window: every node's every text, the password field included
        List<String> texts =
                driver.callOnFxThread(
                        () -> {
                            List<String> found = new ArrayList<>();
                            collectTexts(driver.node("shell-root").getScene().getRoot(), found);
                            return found;
                        });
        PasswordField key = (PasswordField) driver.node("limelight-upload-key");
        List<String> consoleTexts = new ArrayList<>();
        for (LogMessage message : console.snapshot()) {
            consoleTexts.add(message.text());
        }
        assertAll(
                "the window and the console",
                () -> assertTrue(texts.size() > 1000, "texts read in the window: " + texts.size()),
                () -> assertEquals(0, texts.stream().filter(text -> text.contains(SECRET)).count()),
                () -> assertEquals("", driver.callOnFxThread(key::getText)),
                () -> assertFalse(consoleTexts.isEmpty(), "the console has messages"),
                () ->
                        assertEquals(
                                0,
                                consoleTexts.stream()
                                        .filter(text -> text.contains(SECRET))
                                        .count()));

        app.stopApplication();
        STOPPED.set(true);

        List<SecretSweep.Form> forms = SecretSweep.forms(SECRET);
        Path root = scratch.toRealPath();
        Path temporary = Path.of(System.getProperty("java.io.tmpdir")).toRealPath();
        Path home = Path.of(System.getProperty("user.home")).toRealPath();
        Path applicationData =
                new PlatformFileSystemAccess(
                                withoutSessionBus(ApplicationServices.forThisHost().environment()))
                        .applicationDataDirectory();
        List<SecretSweep.Walk> walks = new ArrayList<>();
        SecretSweep.Walk projectWalk = SecretSweep.walk(project, forms, Optional.empty(), Set.of());
        walks.add(projectWalk);
        SecretSweep.Walk cacheWalk = SecretSweep.walk(cacheRoot, forms, Optional.empty(), Set.of());
        walks.add(cacheWalk);
        if (Files.isDirectory(applicationData)) {
            walks.add(SecretSweep.walk(applicationData, forms, Optional.empty(), Set.of()));
        }
        walks.add(SecretSweep.walk(root, forms, Optional.empty(), Set.of(project, cacheRoot)));
        walks.add(SecretSweep.walk(temporary, forms, Optional.of(started), Set.of(root)));
        if (!home.startsWith(temporary) && !temporary.startsWith(home)) {
            walks.add(SecretSweep.walk(home, forms, Optional.of(started), Set.of(temporary)));
        } else {
            walks.add(SecretSweep.walk(home, forms, Optional.of(started), Set.of(temporary, root)));
        }
        List<String> hits = new ArrayList<>();
        StringBuilder report = new StringBuilder("gate 7's walk, by root:");
        for (SecretSweep.Walk walk : walks) {
            hits.addAll(walk.hits());
            report.append("\n  ").append(walk.summary());
        }
        report.append("\n  the application's data directory ")
                .append(applicationData)
                .append(Files.isDirectory(applicationData) ? " was walked" : " does not exist");
        System.out.println(report);

        Path runs = project.resolve("runs");
        List<Path> expected =
                List.of(
                        project.resolve("project.json"),
                        run.resolve("run.json"),
                        run.resolve("provenance/events.log"),
                        run.resolve("provenance/provenance.json"),
                        run.resolve("provenance/provenance.rst"),
                        run.resolve("logs/limelight-converter.log"),
                        run.resolve("logs/limelight-upload.log"),
                        run.resolve("logs/limelight-upload.1.log"),
                        run.resolve("logs/limelight-upload.2.log"),
                        run.resolve("outputs/limelight/limelight-1.xml"),
                        run.resolve("results/limelight-settings.json"));
        List<Path> exports;
        try (Stream<Path> listed = Files.list(run.resolve("exports"))) {
            exports = listed.toList();
        }
        assertAll(
                "the walk saw what the run wrote, and found the key nowhere",
                () -> assertEquals(List.of(), hits, "files holding the seeded key"),
                () ->
                        assertTrue(
                                projectWalk.searchedFiles().containsAll(expected),
                                () -> {
                                    List<Path> missing = new ArrayList<>(expected);
                                    missing.removeAll(projectWalk.searchedFiles());
                                    return "not searched: " + missing;
                                }),
                () -> assertTrue(exports.size() >= 4, "two exports and their sidecars: " + exports),
                () -> assertTrue(projectWalk.searchedFiles().containsAll(exports)),
                () -> assertTrue(Files.isDirectory(runs)),
                () -> assertEquals(List.of(), projectWalk.unreadable()),
                () -> assertEquals(projectWalk.files(), projectWalk.searched()),
                () -> assertTrue(projectWalk.searched() >= 20, projectWalk::summary),
                () -> assertTrue(cacheWalk.searched() >= 2, cacheWalk::summary),
                () -> assertEquals(List.of(), cacheWalk.unreadable()));
    }

    // ------------------------------------------------------------------------------ helpers --

    /** Types the seeded key into the password field and commits it with Enter. */
    private static void typeKey() {
        driver.typeInto("limelight-upload-key", SECRET);
        driver.press(KeyCode.ENTER);
        assertAll(
                () -> assertEquals(KEY_TYPED, driver.textOf("limelight-upload-key-status")),
                () -> assertEquals("", driver.textOf("limelight-upload-key"), "the field emptied"));
    }

    /** Presses Upload and waits for the destination to be shown. */
    private static void upload() {
        driver.clickOn("limelight-upload-prepare");
        assertEquals(
                NOTHING_SENT_YET,
                RunSection.awaitText(
                        driver,
                        "limelight-upload-outcome",
                        text ->
                                text.equals(NOTHING_SENT_YET)
                                        || text.startsWith("The destination was not accepted")
                                        || text.startsWith("Nothing was checked"),
                        UPLOAD_BOUND));
    }

    /** Presses Send and waits for how the upload ended. */
    private static String send(java.util.function.Predicate<String> expected) {
        driver.clickOn("limelight-upload-send");
        String ended =
                RunSection.awaitText(
                        driver,
                        "limelight-upload-outcome",
                        text -> !text.startsWith("Sending ") && !text.equals(NOTHING_SENT_YET),
                        UPLOAD_BOUND);
        assertTrue(expected.test(ended), ended);
        return ended;
    }

    private static byte[] urlEncoded(List<SecretSweep.Form> forms) {
        for (SecretSweep.Form form : forms) {
            if (form.name().equals("URL-encoded (form)")) {
                return form.bytes();
            }
        }
        throw new AssertionError("no URL-encoded form: " + forms);
    }

    private static void collectTexts(Node node, List<String> texts) {
        if (node instanceof Labeled labeled) {
            texts.add(String.valueOf(labeled.getText()));
            Tooltip tip = labeled.getTooltip();
            if (tip != null) {
                texts.add(String.valueOf(tip.getText()));
            }
        }
        if (node instanceof TextInputControl input) {
            texts.add(String.valueOf(input.getText()));
            texts.add(String.valueOf(input.getPromptText()));
        }
        if (node instanceof ComboBox<?> box) {
            texts.add(String.valueOf(box.getValue()));
            texts.add(String.valueOf(box.getItems()));
        }
        texts.add(String.valueOf(node.getAccessibleText()));
        texts.add(String.valueOf(node.getAccessibleHelp()));
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                collectTexts(child, texts);
            }
        }
    }

    private static String comboValue(String id) {
        ComboBox<?> box = (ComboBox<?>) driver.node(id);
        return driver.callOnFxThread(() -> String.valueOf(box.getValue()));
    }

    private static Path onlyRun() {
        try (Stream<Path> runs = Files.list(project.resolve("runs"))) {
            List<Path> sorted = runs.sorted().toList();
            assertEquals(1, sorted.size(), () -> "the runs: " + sorted);
            return sorted.get(0);
        } catch (IOException unreadable) {
            throw new AssertionError("the project's runs cannot be listed", unreadable);
        }
    }

    private static String idOf(Path directory) {
        String name = String.valueOf(directory.getFileName());
        return name.substring(name.indexOf('-') + 1);
    }
}
