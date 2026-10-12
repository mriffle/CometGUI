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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.cometgui.app.testing.FakeEnvironment;
import org.cometgui.app.testing.RealSearch;
import org.cometgui.app.testing.RecordingProcessRunner;
import org.cometgui.app.testing.ResultRuns;
import org.cometgui.domain.project.ProjectLayout;
import org.cometgui.domain.run.AttemptOutcome;
import org.cometgui.domain.run.RunId;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.domain.secrets.Credential;
import org.cometgui.domain.secrets.SecretRedactor;
import org.cometgui.domain.secrets.SessionCredentialStore;
import org.cometgui.domain.secrets.SessionSecrets;
import org.cometgui.provenance.events.ProvenanceEventLog;
import org.cometgui.provenance.events.ProvenanceEventType;
import org.cometgui.provenance.hashing.StreamingHashService;
import org.cometgui.results.filtering.store.TableKind;
import org.cometgui.tools.credentials.CredentialStores;
import org.cometgui.tools.limelight.LimelightUploadClient;
import org.cometgui.tools.limelight.fake.FakeLimelightServer;
import org.cometgui.ui.viewmodel.limelight.LimelightUploadPreview;
import org.cometgui.ui.viewmodel.limelight.LimelightUploaded;
import org.cometgui.ui.viewmodel.results.ResultsRun;
import org.cometgui.workflow.limelight.LimelightConversions;
import org.cometgui.workflow.limelight.LimelightUploadRefusedException;
import org.cometgui.workflow.limelight.LimelightUploadWatcher;
import org.cometgui.workflow.limelight.LimelightUploads;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The Limelight section's upload port over a constructed run with a recorded conversion of the
 * known-good Limelight XML, the real upload service and the one loopback fake ({@code D-007}): the
 * converted files newest first; the server's and project's rules; a typed key admitted to the
 * session's own secrets; a destination checked with nothing sent; a send that uses the typed key,
 * or the one kept for the server, or refuses without either before anything is sent; a destination
 * sent at most once; the key kept only after a submitted upload when asked; the outcome in the
 * section's words; and the credential store chosen once, with its reason.
 *
 * <p>Files read outside this module: {@code scratch/scientific-path/limelight/limelight.xml} (held
 * to its SHA-256) and, through the fake, {@code
 * scratch/phase05/artefacts/v2.8.1__cometPercolator2LimelightXML.jar}.
 */
class ProjectLimelightUploadPortTest {

    private static final Instant CREATED = Instant.parse("2026-10-12T08:00:00Z");

    private static final String RUN = "limelight-upload-port-run";

    private static final String KEY = "PORTSEEDKEY-3b8e0f6a2c9d71";

    private static final String KNOWN_GOOD = "scratch/scientific-path/limelight/limelight.xml";

    private static final String KNOWN_GOOD_SHA256 =
            "25c218a8f5658ea19273703517040c823530b1c5890681b0e231f001408c0a11";

    private static final String STORE_REASON = "The key is kept for this session only, in a test.";

    @TempDir private Path scratch;

    private ProjectSession session;

    private RunLayout run;

    private Path first;

    private Path second;

    private final Set<RunId> executing = new HashSet<>();

    private final SessionSecrets secrets = new SessionSecrets();

    private final SessionCredentialStore store = new SessionCredentialStore();

    private final AtomicInteger chosen = new AtomicInteger();

    private FakeLimelightServer fake;

    @BeforeEach
    void build() throws IOException {
        Path root = scratch.toRealPath();
        ProjectLayout project = ResultRuns.project(root.resolve("project"));
        Path psms = root.resolve("psms.tsv");
        Files.writeString(
                psms,
                "PSMId\tscore\tq-value\tposterior_error_prob\tpeptide\tproteinIds\n",
                StandardCharsets.UTF_8);
        run =
                ResultRuns.run(
                        project,
                        RUN,
                        CREATED,
                        List.of(root.resolve("inputs/k562_3.mgf")),
                        Map.of(TableKind.TARGET_PSMS, psms),
                        Optional.empty());
        session =
                new ProjectSession(
                        root.resolve("project"),
                        Clock.systemUTC(),
                        ApplicationServices.forThisHost().runIds());
        Path knownGood = RealSearch.repositoryRoot().resolve(KNOWN_GOOD);
        assertEquals(KNOWN_GOOD_SHA256, RealSearch.sha256(knownGood), knownGood.toString());
        Path outputs = Files.createDirectories(run.limelightOutputDirectory());
        first = Files.copy(knownGood, outputs.resolve("limelight-1.xml"));
        second = Files.copy(knownGood, outputs.resolve("limelight-2.xml"));
        try (ProvenanceEventLog log =
                ProvenanceEventLog.openAppend(
                        run.eventLogFile(), SecretRedactor.patternsOnly(), Clock.systemUTC())) {
            for (Path converted : List.of(first, second)) {
                log.append(
                        ProvenanceEventType.LIMELIGHT_CONVERTED,
                        Map.of(
                                LimelightConversions.OUTPUT_PATH,
                                converted.toString(),
                                LimelightConversions.OUTPUT_SHA256,
                                KNOWN_GOOD_SHA256));
            }
        }
        fake = FakeLimelightServer.start(KEY, 7);
    }

    @AfterEach
    void stop() throws IOException {
        fake.close();
    }

    private ProjectLimelightUploadPort port() {
        return new ProjectLimelightUploadPort(
                session,
                new LimelightUploads(
                        new LimelightUploadClient(),
                        new StreamingHashService(),
                        secrets,
                        Clock.systemUTC(),
                        () -> Set.copyOf(executing)),
                secrets,
                () -> {
                    chosen.incrementAndGet();
                    return new CredentialStores.Choice(store, STORE_REASON);
                });
    }

    private static ResultsRun listed() {
        return new ResultsRun(
                new RunId(RUN),
                CREATED,
                AttemptOutcome.SUCCEEDED,
                EnumSet.of(TableKind.TARGET_PSMS),
                false);
    }

    /** A watcher that keeps the lines it hears. */
    private static final class Lines implements LimelightUploadWatcher {

        private final List<String> lines = new ArrayList<>();

        private long sent;

        @Override
        public synchronized void line(String line) {
            lines.add(line);
        }

        @Override
        public synchronized void bytesSent(long bytes, long total) {
            sent = bytes;
        }

        synchronized List<String> lines() {
            return List.copyOf(lines);
        }

        synchronized long sent() {
            return sent;
        }
    }

    @Test
    @DisplayName("the run's converted files, newest first")
    void convertedFilesNewestFirst() throws IOException {
        assertEquals(List.of(second, first), port().convertedFiles(listed()));
    }

    @Test
    @DisplayName("the server and project by the workflow's rules, http only to this computer")
    void theRules() throws IOException {
        ProjectLimelightUploadPort port = port();
        LimelightUploadRefusedException plain =
                assertThrows(
                        LimelightUploadRefusedException.class,
                        () -> port.server("http://limelight.example.org/limelight"));
        LimelightUploadRefusedException zero =
                assertThrows(LimelightUploadRefusedException.class, () -> port.project("0"));
        assertAll(
                () ->
                        assertEquals(
                                "https://limelight.example.org/limelight",
                                port.server(" HTTPS://Limelight.Example.org/limelight/ ")),
                () ->
                        assertEquals(
                                "http://127.0.0.1:8080/limelight",
                                port.server("http://127.0.0.1:8080/limelight")),
                () -> assertEquals(LimelightUploadRefusedException.Reason.SERVER, plain.reason()),
                () ->
                        assertTrue(
                                plain.getMessage().contains("plain http would send the submit key"),
                                plain.getMessage()),
                () -> assertEquals(42, port.project(" 42 ")),
                () -> assertEquals(LimelightUploadRefusedException.Reason.PROJECT, zero.reason()),
                () ->
                        assertTrue(
                                zero.getMessage().contains("positive whole number"),
                                zero.getMessage()));
    }

    @Test
    @DisplayName("a typed key is admitted to the session's own secrets: its redactor knows it")
    void aKeyIsAdmitted() {
        Credential admitted = port().admit(KEY);
        assertAll(
                () -> assertEquals(KEY, admitted.reveal()),
                () -> assertEquals(1, secrets.size()),
                () ->
                        assertEquals(
                                "key [REDACTED] here",
                                secrets.redactor().redactText("key " + KEY + " here")));
    }

    @Test
    @DisplayName(
            "a destination is checked and shown with nothing sent; Send uploads it once with the"
                    + " typed key, and keeps the key only when asked and submitted")
    void prepareThenSend() throws IOException {
        ProjectLimelightUploadPort port = port();
        LimelightUploadPreview preview = port.prepare(listed(), second, fake.baseUrl(), "7");
        assertAll(
                "the destination, and nothing sent",
                () -> assertEquals(List.of(), fake.requests()),
                () -> assertEquals(new RunId(RUN), preview.run()),
                () -> assertEquals(fake.baseUrl(), preview.server()),
                () -> assertEquals(7, preview.projectId()),
                () -> assertEquals(fake.baseUrl() + "/d/pg/project/7", preview.projectPage()),
                () -> assertEquals(second, preview.file()),
                () -> assertEquals(Files.size(second), preview.size()),
                () -> assertEquals(KNOWN_GOOD_SHA256, preview.sha256()));

        Lines lines = new Lines();
        LimelightUploaded uploaded = port.send(preview, Optional.of(port.admit(KEY)), true, lines);
        assertAll(
                "submitted",
                () -> assertEquals(LimelightUploaded.Kind.SUBMITTED, uploaded.kind()),
                () -> assertEquals("final-submit", uploaded.step()),
                () ->
                        assertEquals(
                                Integer.toString(FakeLimelightServer.FIRST_TRACKING_ID),
                                uploaded.trackingId()),
                () -> assertEquals(fake.uploads().get(0).trackingId(), uploaded.trackingId()),
                () ->
                        assertEquals(
                                fake.uploads().get(0).importerSubDir(), uploaded.importerSubDir()),
                () -> assertFalse(uploaded.importerSubDir().isEmpty()),
                () -> assertEquals(preview.projectPage(), uploaded.projectPage()),
                () -> assertEquals(0, uploaded.httpStatus()),
                () -> assertEquals(List.of(), uploaded.flags()),
                () -> assertEquals("", uploaded.message()),
                () ->
                        assertEquals(
                                run.logsDirectory().resolve("limelight-upload.log"),
                                uploaded.log()),
                () ->
                        assertEquals(
                                "The submit key is kept for "
                                        + fake.baseUrl()
                                        + ": "
                                        + SessionCredentialStore.DESCRIPTION
                                        + ".",
                                uploaded.keyNote()),
                () -> assertEquals(KEY, store.find(fake.baseUrl()).orElseThrow().reveal()),
                () -> assertEquals(List.of(), fake.wireViolations()),
                () -> assertEquals("VALID", fake.uploads().get(0).verdict()),
                () -> assertEquals(Files.size(second), lines.sent()),
                () -> assertFalse(lines.lines().isEmpty()),
                () ->
                        assertEquals(
                                List.of(),
                                lines.lines().stream().filter(line -> line.contains(KEY)).toList(),
                                "no line the section shows carries the key"),
                () -> assertTrue(port.keyKept(fake.baseUrl())));

        int requests = fake.requests().size();
        LimelightUploadRefusedException again =
                assertThrows(
                        LimelightUploadRefusedException.class,
                        () -> port.send(preview, Optional.of(port.admit(KEY)), false, lines));
        assertAll(
                "a destination is sent at most once",
                () ->
                        assertEquals(
                                LimelightUploadRefusedException.Reason.CONFIRMATION_USED,
                                again.reason()),
                () -> assertEquals(requests, fake.requests().size()));
    }

    @Test
    @DisplayName("without a typed key the kept one is used; without either nothing is sent")
    void theKeptKey() throws IOException {
        ProjectLimelightUploadPort port = port();
        LimelightUploadPreview preview = port.prepare(listed(), first, fake.baseUrl(), "7");
        IOException none =
                assertThrows(
                        IOException.class,
                        () -> port.send(preview, Optional.empty(), false, new Lines()));
        assertAll(
                () ->
                        assertEquals(
                                "No submit key was typed and none is kept for "
                                        + fake.baseUrl()
                                        + ", so nothing was sent.",
                                none.getMessage()),
                () -> assertEquals(List.of(), fake.requests()),
                () -> assertFalse(port.keyKept(fake.baseUrl())));

        store.save(fake.baseUrl(), secrets.admit(KEY));
        LimelightUploaded uploaded = port.send(preview, Optional.empty(), true, new Lines());
        assertAll(
                "the destination was not used up by the refusal; the kept key was sent",
                () -> assertEquals(LimelightUploaded.Kind.SUBMITTED, uploaded.kind()),
                () -> assertEquals("", uploaded.keyNote(), "no typed key to keep"));

        port.forgetKey(fake.baseUrl());
        assertFalse(port.keyKept(fake.baseUrl()));
    }

    @Test
    @DisplayName("a refusal carries the server's step, status, flags and words; no key is kept")
    void aRefusal() throws IOException {
        fake.refuseKey(true);
        ProjectLimelightUploadPort port = port();
        LimelightUploadPreview preview = port.prepare(listed(), first, fake.baseUrl(), "7");
        LimelightUploaded refused =
                port.send(preview, Optional.of(port.admit(KEY)), true, new Lines());
        assertAll(
                () -> assertEquals(LimelightUploaded.Kind.REFUSED, refused.kind()),
                () -> assertEquals("auth-test", refused.step()),
                () -> assertEquals(200, refused.httpStatus()),
                () ->
                        assertTrue(
                                refused.flags().contains("userSubmitImportProgramKeyNotFound"),
                                refused.flags().toString()),
                () -> assertFalse(refused.message().isEmpty()),
                () -> assertFalse(refused.message().contains(KEY)),
                () -> assertEquals("", refused.trackingId()),
                () -> assertEquals("", refused.keyNote()),
                () ->
                        assertFalse(
                                store.find(fake.baseUrl()).isPresent(),
                                "a refused key is not kept"));
    }

    @Test
    @DisplayName("a destination this port did not check is refused before anything is sent")
    void aForeignDestination() {
        LimelightUploadPreview foreign =
                new LimelightUploadPreview() {
                    @Override
                    public RunId run() {
                        return new RunId(RUN);
                    }

                    @Override
                    public String server() {
                        return fake.baseUrl();
                    }

                    @Override
                    public int projectId() {
                        return 7;
                    }

                    @Override
                    public String projectPage() {
                        return fake.baseUrl() + "/d/pg/project/7";
                    }

                    @Override
                    public Path file() {
                        return first;
                    }

                    @Override
                    public long size() {
                        return 1;
                    }

                    @Override
                    public String sha256() {
                        return KNOWN_GOOD_SHA256;
                    }
                };
        IOException refused =
                assertThrows(
                        IOException.class,
                        () ->
                                port().send(
                                                foreign,
                                                Optional.of(secrets.admit(KEY)),
                                                false,
                                                new Lines()));
        assertAll(
                () ->
                        assertEquals(
                                "This destination was not checked by this application, so nothing"
                                        + " was sent; press Upload again.",
                                refused.getMessage()),
                () -> assertEquals(List.of(), fake.requests()));
    }

    @Test
    @DisplayName("an executing run's destination is refused, as is a file that is not a conversion")
    void refusedDestinations() {
        ProjectLimelightUploadPort port = port();
        executing.add(new RunId(RUN));
        LimelightUploadRefusedException busy =
                assertThrows(
                        LimelightUploadRefusedException.class,
                        () -> port.prepare(listed(), first, fake.baseUrl(), "7"));
        executing.clear();
        LimelightUploadRefusedException notOne =
                assertThrows(
                        LimelightUploadRefusedException.class,
                        () ->
                                port.prepare(
                                        listed(),
                                        run.root().resolve("x.xml"),
                                        fake.baseUrl(),
                                        "7"));
        assertAll(
                () ->
                        assertEquals(
                                LimelightUploadRefusedException.Reason.RUN_EXECUTING,
                                busy.reason()),
                () ->
                        assertEquals(
                                LimelightUploadRefusedException.Reason.NOT_A_CONVERSION,
                                notOne.reason()),
                () -> assertEquals(List.of(), fake.requests()));
    }

    @Test
    @DisplayName("the credential store is chosen once, on first use, and its reason is shown")
    void theStoreIsChosenOnce() throws IOException {
        ProjectLimelightUploadPort port = port();
        assertEquals(0, chosen.get(), "nothing is chosen at construction");
        assertAll(
                () -> assertEquals(STORE_REASON, port.credentialStore()),
                () -> assertFalse(port.keyKept("https://a.example.org")),
                () -> assertEquals(STORE_REASON, port.credentialStore()),
                () -> assertEquals(1, chosen.get()));
    }

    @Test
    @DisplayName(
            "this computer's store: the session store with the reason, when no keyring can be"
                    + " reached, the platform is not recognised or no process can start")
    void thisComputersStore() throws IOException {
        Path emptyPath = Files.createDirectories(scratch.resolve("empty-path"));
        FakeEnvironment linux =
                FakeEnvironment.linux64().withVariable("PATH", emptyPath.toString());
        ApplicationServices real = ApplicationServices.forThisHost();
        RecordingProcessRunner processes = RecordingProcessRunner.inFrontOf(Clock.systemUTC());
        CredentialStores.Choice noTool =
                RunWiring.credentialStore(services(linux, processes), session, secrets);
        CredentialStores.Choice noHost =
                RunWiring.credentialStore(
                        services(new FakeEnvironment(), processes), session, secrets);
        CredentialStores.Choice noProcesses =
                RunWiring.credentialStore(services(linux, null), session, secrets);
        assertAll(
                () -> assertTrue(noTool.store() instanceof SessionCredentialStore),
                () ->
                        assertEquals(
                                "The key is kept for this session only: secret-tool"
                                        + " (libsecret-tools) is not installed, so the desktop"
                                        + " keyring cannot be reached.",
                                noTool.explanation()),
                () -> assertTrue(noHost.store() instanceof SessionCredentialStore),
                () ->
                        assertEquals(
                                "The key is kept for this session only: this machine's operating"
                                        + " system and architecture are not ones CometGUI"
                                        + " recognises.",
                                noHost.explanation()),
                () -> assertTrue(noProcesses.store() instanceof SessionCredentialStore),
                () ->
                        assertEquals(
                                "The key is kept for this session only: this application was"
                                        + " composed without a process service, so no keyring"
                                        + " tool can be started.",
                                noProcesses.explanation()),
                () -> assertTrue(real.environment().osName().isPresent()));
    }

    private static ApplicationServices services(
            FakeEnvironment environment, RecordingProcessRunner processes) {
        ApplicationServices real = ApplicationServices.forThisHost();
        return new ApplicationServices(
                real.clock(),
                environment,
                real.fileSystem(),
                real.runIds(),
                real.glibcVersions(),
                processes,
                null,
                null);
    }
}
