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

package org.cometgui.workflow.storage;

import static org.cometgui.workflow.storage.StorageFixtures.PROJECT_JSON;
import static org.cometgui.workflow.storage.StorageFixtures.RUN_JSON;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;
import org.cometgui.domain.build.BuildIdentity;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.ports.RunIdSource;
import org.cometgui.domain.project.ProjectDescriptor;
import org.cometgui.domain.project.ProjectId;
import org.cometgui.domain.project.ProjectLayout;
import org.cometgui.domain.project.SchemaVerdict;
import org.cometgui.domain.project.UnsupportedSchemaVersionException;
import org.cometgui.domain.run.ArchivedFile;
import org.cometgui.domain.run.AttemptOutcome;
import org.cometgui.domain.run.RecordedInput;
import org.cometgui.domain.run.RunAttempt;
import org.cometgui.domain.run.RunDescriptor;
import org.cometgui.domain.run.RunId;
import org.cometgui.domain.run.RunIdentity;
import org.cometgui.domain.run.RunImmutabilityException;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.parser.ReleaseDefaults;
import org.cometgui.params.comet.schema.MetadataLoader;
import org.cometgui.params.comet.writer.CanonicalParamsWriter;
import org.cometgui.params.comet.writer.WrittenParams;
import org.cometgui.provenance.hashing.StreamingHashService;
import org.cometgui.provenance.manifest.ManifestWriter;
import org.cometgui.provenance.report.ProvenanceReportWriter;
import org.cometgui.workflow.testing.Nulls;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link ProjectStore} and {@link RunStore} against a real directory: what is created, what is
 * written byte for byte, what is refused, and that a refused write leaves the file byte-identical.
 */
class RunStoreTest {

    private static final Clock PROJECT_CLOCK =
            Clock.fixed(Instant.parse("2026-08-28T23:00:00.000Z"), ZoneOffset.UTC);

    /** Sub-millisecond on purpose: the reserved run must be dated to the millisecond. */
    private static final Clock RUN_CLOCK =
            Clock.fixed(Instant.parse("2026-08-28T23:15:00.250999Z"), ZoneOffset.UTC);

    @TempDir private Path temporary;

    private ProjectLayout project;

    private ProjectLock lock;

    private ProjectDescriptor created;

    private final Deque<String> ids = new ArrayDeque<>(List.of("run-0001", "run-0002"));

    private final RunIdSource runIds = () -> new RunId(ids.removeFirst());

    private RunStore store;

    @BeforeEach
    void createProject() throws IOException {
        project = new ProjectLayout(temporary.resolve("MyProject"));
        created = new ProjectStore(PROJECT_CLOCK).create(project, new ProjectId("project-beta"));
        lock = ProjectLock.acquire(project, RUN_CLOCK);
        store = new RunStore(project, RUN_CLOCK, runIds);
    }

    @AfterEach
    void releaseLock() throws IOException {
        lock.close();
    }

    private static String sha256(Path file) throws IOException, NoSuchAlgorithmException {
        return HexFormat.of()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
    }

    /** RUN_JSON with no attempts: the hand-typed document cut at "attempts", by hand. */
    private static String freshRunJson() {
        return RUN_JSON.substring(0, RUN_JSON.indexOf("  \"attempts\": ["))
                + "  \"attempts\": []\n}\n";
    }

    /** Reserves run-0001 and puts a 12345-byte parameter file where the identity says it is. */
    private RunLayout reserveWithParameters() throws IOException {
        ReservedRun reserved = store.reserve(lock);
        Files.write(reserved.layout().cometParamsFile(), new byte[12345]);
        return reserved.layout();
    }

    @Test
    @DisplayName("creating a project writes the hand-typed project.json and a runs/ directory")
    void createsProject() throws IOException {
        assertAll(
                () ->
                        assertEquals(
                                PROJECT_JSON,
                                Files.readString(project.projectFile(), StandardCharsets.UTF_8)),
                () -> assertTrue(Files.isDirectory(project.runsDirectory())),
                () ->
                        assertEquals(
                                new ProjectDescriptor(
                                        new ProjectId("project-beta"),
                                        Instant.parse("2026-08-28T23:00:00.000Z")),
                                created),
                () ->
                        assertEquals(
                                new ProjectDescriptor(
                                        new ProjectId("project-beta"),
                                        Instant.parse("2026-08-28T23:00:00.000Z")),
                                ProjectStore.read(project)));
    }

    @Test
    @DisplayName("a second project in the same directory is refused and project.json is unchanged")
    void secondProjectRefused() throws IOException, NoSuchAlgorithmException {
        String before = sha256(project.projectFile());
        AlreadyWrittenException thrown =
                assertThrows(
                        AlreadyWrittenException.class,
                        () -> new ProjectStore(RUN_CLOCK).create(project, new ProjectId("other")));
        assertAll(
                () ->
                        assertEquals(
                                project.projectFile()
                                        + ": this directory already holds a project; project.json"
                                        + " is written once",
                                thrown.getMessage()),
                () -> assertEquals(before, sha256(project.projectFile())));
    }

    @Test
    @DisplayName("a newer or older project.json is refused and left byte-identical")
    void projectSchemaVersions() throws IOException, NoSuchAlgorithmException {
        for (int version : new int[] {2, 0}) {
            String text =
                    PROJECT_JSON.replace("\"schemaVersion\": 1", "\"schemaVersion\": " + version);
            Files.writeString(project.projectFile(), text, StandardCharsets.UTF_8);
            String before = sha256(project.projectFile());
            UnsupportedSchemaVersionException thrown =
                    assertThrows(
                            UnsupportedSchemaVersionException.class,
                            () -> ProjectStore.read(project));
            assertAll(
                    () ->
                            assertEquals(
                                    version == 2 ? SchemaVerdict.NEWER : SchemaVerdict.OLDER,
                                    thrown.verdict()),
                    () -> assertEquals(project.projectFile().toString(), thrown.document()),
                    () -> assertEquals(before, sha256(project.projectFile())),
                    () -> assertEquals(text, Files.readString(project.projectFile())));
        }
    }

    @Test
    @DisplayName("reserving a run takes the injected id and clock and creates exactly P8-3's tree")
    void reserves() throws IOException {
        ReservedRun reserved = store.reserve(lock);
        Path root = project.runsDirectory().resolve("20260828T231500Z-run-0001");
        List<String> tree;
        try (Stream<Path> walk = Files.walk(root)) {
            tree = walk.map(path -> root.relativize(path).toString()).sorted().toList();
        }
        assertAll(
                () -> assertEquals(new RunId("run-0001"), reserved.runId()),
                () -> assertEquals(Instant.parse("2026-08-28T23:15:00.250Z"), reserved.created()),
                () -> assertEquals(root, reserved.layout().root()),
                () ->
                        assertEquals(
                                List.of(
                                        "",
                                        "inputs",
                                        "inputs/pin",
                                        "logs",
                                        "outputs",
                                        "outputs/comet",
                                        "parameters",
                                        "provenance"),
                                tree));
    }

    @Test
    @DisplayName("a repeated run id never writes into the earlier run")
    void repeatedIdRefused() throws IOException {
        Deque<String> same = new ArrayDeque<>(List.of("run-0001", "run-0001"));
        RunStore repeating = new RunStore(project, RUN_CLOCK, () -> new RunId(same.removeFirst()));
        RunLayout first = repeating.reserve(lock).layout();
        Files.writeString(first.logsDirectory().resolve("marker"), "first");
        assertThrows(FileAlreadyExistsException.class, () -> repeating.reserve(lock));
        assertEquals("first", Files.readString(first.logsDirectory().resolve("marker")));
    }

    @Test
    @DisplayName("recording writes run.json once, byte for byte the hand-typed fresh document")
    void records() throws IOException {
        RunLayout layout = reserveWithParameters();
        RunDescriptor recorded = store.record(lock, StorageFixtures.identity());
        assertAll(
                () -> assertEquals(freshRunJson(), Files.readString(layout.runFile())),
                () -> assertEquals(RunDescriptor.of(StorageFixtures.identity()), recorded),
                () -> assertEquals(layout, store.layoutOf(StorageFixtures.identity())));
    }

    @Test
    @DisplayName("a second record of the same run is refused and run.json is byte-identical")
    void secondRecordRefused() throws IOException, NoSuchAlgorithmException {
        RunLayout layout = reserveWithParameters();
        store.record(lock, StorageFixtures.identity());
        String before = sha256(layout.runFile());
        AlreadyWrittenException thrown =
                assertThrows(
                        AlreadyWrittenException.class,
                        () -> store.record(lock, StorageFixtures.identity()));
        assertAll(
                () ->
                        assertEquals(
                                layout.runFile()
                                        + ": run run-0001 is already recorded; a run's identity is"
                                        + " written once",
                                thrown.getMessage()),
                () -> assertEquals(before, sha256(layout.runFile())));
    }

    @Test
    @DisplayName("updates that only add to the attempts are written, byte for byte")
    void permittedUpdates() throws IOException {
        RunLayout layout = reserveWithParameters();
        RunDescriptor fresh = store.record(lock, StorageFixtures.identity());
        RunDescriptor started =
                store.update(lock, fresh.withNewAttempt(Instant.parse("2026-08-28T23:15:01.000Z")));
        RunDescriptor full = store.update(lock, StorageFixtures.descriptor());
        assertAll(
                () -> assertEquals(1, started.attempts().size()),
                () -> assertEquals(RUN_JSON, Files.readString(layout.runFile())),
                () -> assertEquals(StorageFixtures.descriptor(), full),
                () -> assertEquals(StorageFixtures.descriptor(), store.read(layout)));
    }

    @Test
    @DisplayName("a retry is a new attempt record; the failed attempt is kept as it was")
    void retryIsANewAttempt() throws IOException {
        reserveWithParameters();
        store.record(lock, StorageFixtures.identity());
        store.update(lock, StorageFixtures.descriptor());
        RunDescriptor retried =
                store.update(
                        lock,
                        StorageFixtures.descriptor()
                                .withAttemptFinished(
                                        AttemptOutcome.CANCELLED,
                                        Instant.parse("2026-08-28T23:18:00.000Z"))
                                .withNewAttempt(Instant.parse("2026-08-28T23:19:00.000Z")));
        assertAll(
                () -> assertEquals(3, retried.attempts().size()),
                () ->
                        assertEquals(
                                StorageFixtures.descriptor().attempts().get(0),
                                retried.attempts().get(0)),
                () -> assertEquals(AttemptOutcome.CANCELLED, retried.attempts().get(1).outcome()),
                () -> assertEquals(AttemptOutcome.RUNNING, retried.attempts().get(2).outcome()));
    }

    @Test
    @DisplayName(
            "an update that re-records an input is refused; run.json and the inputs are unchanged")
    void identityChangeRefused() throws IOException, NoSuchAlgorithmException {
        RunLayout layout = reserveWithParameters();
        RunDescriptor recorded = store.record(lock, StorageFixtures.identity());
        String before = sha256(layout.runFile());
        RunIdentity original = recorded.identity();
        RecordedInput fasta = original.fasta();
        RunIdentity tampered =
                new RunIdentity(
                        original.runId(),
                        original.projectId(),
                        original.created(),
                        original.cometRelease(),
                        original.spectra(),
                        new RecordedInput(
                                fasta.path(),
                                fasta.size(),
                                fasta.modified(),
                                new FileHashes(StorageFixtures.MD5_1, StorageFixtures.SHA_1)),
                        original.parameters(),
                        original.indexMode(),
                        original.databaseDelivery());
        RunImmutabilityException thrown =
                assertThrows(
                        RunImmutabilityException.class,
                        () -> store.update(lock, new RunDescriptor(tampered, List.of())));
        assertAll(
                () -> assertEquals("fasta", thrown.member()),
                () -> assertEquals(before, sha256(layout.runFile())),
                () -> assertEquals(original, store.read(layout).identity()),
                () ->
                        assertEquals(
                                StorageFixtures.SHA_F,
                                store.read(layout).identity().fasta().hashes().sha256()));
    }

    @Test
    @DisplayName(
            "an update that rewrites an ended attempt is refused and run.json is byte-identical")
    void endedAttemptRewriteRefused() throws IOException, NoSuchAlgorithmException {
        RunLayout layout = reserveWithParameters();
        store.record(lock, StorageFixtures.identity());
        store.update(lock, StorageFixtures.descriptor());
        String before = sha256(layout.runFile());
        RunAttempt failed = StorageFixtures.descriptor().attempts().get(0);
        RunDescriptor rewritten =
                new RunDescriptor(
                        StorageFixtures.identity(),
                        List.of(
                                new RunAttempt(
                                        1,
                                        failed.started(),
                                        failed.ended(),
                                        AttemptOutcome.SUCCEEDED,
                                        failed.succeededSteps()),
                                StorageFixtures.descriptor().attempts().get(1)));
        RunDescriptor dropped =
                new RunDescriptor(
                        StorageFixtures.identity(),
                        List.of(StorageFixtures.descriptor().attempts().get(0)));
        RunImmutabilityException rewrite =
                assertThrows(RunImmutabilityException.class, () -> store.update(lock, rewritten));
        RunImmutabilityException drop =
                assertThrows(RunImmutabilityException.class, () -> store.update(lock, dropped));
        assertAll(
                () -> assertEquals("attempts[0]", rewrite.member()),
                () -> assertEquals("attempts", drop.member()),
                () -> assertEquals(before, sha256(layout.runFile())),
                () -> assertEquals(StorageFixtures.descriptor(), store.read(layout)));
    }

    @Test
    @DisplayName(
            "the run's parameter file is written once: a second writeOnce is refused, bytes kept")
    void parameterFileWrittenOnce() throws IOException, NoSuchAlgorithmException {
        ReservedRun reserved = store.reserve(lock);
        Path target = reserved.layout().cometParamsFile();
        CanonicalParamsWriter writer =
                new CanonicalParamsWriter(
                        BuildIdentity.of(
                                "0.1.0-test",
                                "0".repeat(40),
                                Instant.parse("2026-08-28T00:00:00Z")));
        CometParameters model =
                ReleaseDefaults.load(MetadataLoader.loadBundled(), ToolVersion.parse("2026.03.0"));
        WrittenParams written = writer.writeOnce(model, target, new StreamingHashService());
        String before = sha256(target);
        assertThrows(
                FileAlreadyExistsException.class,
                () -> writer.writeOnce(model, target, new StreamingHashService()));
        RunIdentity identity =
                new RunIdentity(
                        reserved.runId(),
                        new ProjectId("project-beta"),
                        reserved.created(),
                        "2026.03.0",
                        StorageFixtures.identity().spectra(),
                        StorageFixtures.identity().fasta(),
                        new ArchivedFile(
                                RunLayout.cometParamsRelativePath(),
                                written.size(),
                                written.hashes()),
                        StorageFixtures.identity().indexMode(),
                        StorageFixtures.identity().databaseDelivery());
        RunDescriptor recorded = store.record(lock, identity);
        assertAll(
                () -> assertEquals(before, sha256(target)),
                () -> assertEquals(written.hashes().sha256(), before),
                () -> assertEquals(written.hashes(), recorded.identity().parameters().hashes()),
                () -> assertEquals(Files.size(target), recorded.identity().parameters().size()));
    }

    @Test
    @DisplayName(
            "recording refuses a run that was not reserved, another project, a wrong parameter"
                    + " size")
    void recordRefusals() throws IOException {
        assertThrows(
                NoSuchFileException.class, () -> store.record(lock, StorageFixtures.identity()));
        RunLayout layout = store.reserve(lock).layout();
        assertThrows(
                NoSuchFileException.class, () -> store.record(lock, StorageFixtures.identity()));
        Files.write(layout.cometParamsFile(), new byte[12344]);
        IllegalArgumentException size =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> store.record(lock, StorageFixtures.identity()));
        RunIdentity g = StorageFixtures.identity();
        RunIdentity otherProject =
                new RunIdentity(
                        g.runId(),
                        new ProjectId("other"),
                        g.created(),
                        g.cometRelease(),
                        g.spectra(),
                        g.fasta(),
                        g.parameters(),
                        g.indexMode(),
                        g.databaseDelivery());
        IllegalArgumentException wrongProject =
                assertThrows(
                        IllegalArgumentException.class, () -> store.record(lock, otherProject));
        assertAll(
                () ->
                        assertEquals(
                                "the parameter file "
                                        + layout.cometParamsFile()
                                        + " is 12344 bytes, but the run would record 12345",
                                size.getMessage()),
                () ->
                        assertEquals(
                                "run run-0001 names project other, but "
                                        + project.root()
                                        + " is project project-beta",
                                wrongProject.getMessage()),
                () -> assertFalse(Files.exists(layout.runFile())));
    }

    @Test
    @DisplayName(
            "a derived run is recorded at version 2, read back equal, and keeps version 2 as its"
                    + " attempts are recorded")
    void derivedRunRoundTrip() throws IOException {
        reserveWithParameters();
        ReservedRun second = store.reserve(lock);
        assertEquals(new RunId("run-0002"), second.runId());
        Files.write(second.layout().cometParamsFile(), new byte[12345]);

        RunDescriptor recorded = store.record(lock, StorageFixtures.derivedIdentity());
        String fresh =
                StorageFixtures.DERIVED_RUN_JSON.substring(
                                0, StorageFixtures.DERIVED_RUN_JSON.indexOf("  \"attempts\": ["))
                        + "  \"attempts\": []\n}\n";
        assertEquals(fresh, Files.readString(second.layout().runFile(), StandardCharsets.UTF_8));
        assertEquals(RunDescriptor.of(StorageFixtures.derivedIdentity()), recorded);

        RunDescriptor ended =
                store.update(
                        lock, recorded.withNewAttempt(Instant.parse("2026-08-28T23:15:01.000Z")));
        ended =
                store.update(
                        lock,
                        ended.withAttemptFinished(
                                AttemptOutcome.SUCCEEDED,
                                Instant.parse("2026-08-28T23:15:30.000Z")));
        assertEquals(
                StorageFixtures.DERIVED_RUN_JSON,
                Files.readString(second.layout().runFile(), StandardCharsets.UTF_8));
        assertEquals(StorageFixtures.derivedDescriptor(), store.read(second.layout()));
        assertEquals(2, ended.schemaVersion());
    }

    @Test
    @DisplayName("a newer run.json is refused before anything else and left byte-identical")
    void newerRunJsonRefused() throws IOException, NoSuchAlgorithmException {
        RunLayout layout = reserveWithParameters();
        Files.writeString(
                layout.runFile(), RUN_JSON.replace("\"schemaVersion\": 1", "\"schemaVersion\": 3"));
        String before = sha256(layout.runFile());
        UnsupportedSchemaVersionException thrown =
                assertThrows(UnsupportedSchemaVersionException.class, () -> store.read(layout));
        assertAll(
                () -> assertEquals(SchemaVerdict.NEWER, thrown.verdict()),
                () -> assertEquals(layout.runFile().toString(), thrown.document()),
                () -> assertEquals(before, sha256(layout.runFile())),
                () ->
                        assertThrows(
                                UnsupportedSchemaVersionException.class,
                                () -> store.update(lock, StorageFixtures.descriptor())),
                () -> assertEquals(before, sha256(layout.runFile())));
    }

    @Test
    @DisplayName("a run.json in another run's directory is refused")
    void wrongDirectory() throws IOException {
        RunLayout layout = reserveWithParameters();
        Files.writeString(
                layout.runFile(),
                RUN_JSON.replace("\"runId\": \"run-0001\"", "\"runId\": \"run-0009\""));
        InvalidDocumentException thrown =
                assertThrows(InvalidDocumentException.class, () -> store.read(layout));
        assertAll(
                () -> assertEquals("runId", thrown.member()),
                () ->
                        assertEquals(
                                layout.runFile()
                                        + " is not valid: \"runId\" and \"created\" do not name the"
                                        + " directory the file is in",
                                thrown.getMessage()));
    }

    @Test
    @DisplayName("a run.json that is not UTF-8, or is absurdly large, is refused")
    void hostileFiles() throws IOException {
        RunLayout layout = reserveWithParameters();
        Files.write(layout.runFile(), new byte[] {'{', (byte) 0xC3, (byte) 0x28, '}'});
        InvalidDocumentException notUtf8 =
                assertThrows(InvalidDocumentException.class, () -> store.read(layout));
        try (RandomAccessFile file = new RandomAccessFile(layout.runFile().toFile(), "rw")) {
            file.setLength(DocumentFields.MAX_DOCUMENT_BYTES + 1);
        }
        InvalidDocumentException large =
                assertThrows(InvalidDocumentException.class, () -> store.read(layout));
        assertAll(
                () ->
                        assertEquals(
                                layout.runFile() + " is not valid: the document is not UTF-8 text",
                                notUtf8.getMessage()),
                () ->
                        assertEquals(
                                layout.runFile()
                                        + " is not valid: the document is 67108865 bytes, larger"
                                        + " than the 67108864 this reader opens",
                                large.getMessage()),
                () -> assertEquals("", large.member()));
    }

    @Test
    @DisplayName("a document exactly at the size bound is read, not refused for its size")
    void sizeBoundInclusive() throws IOException {
        RunLayout layout = reserveWithParameters();
        String padded =
                RUN_JSON
                        + " ".repeat((int) (DocumentFields.MAX_DOCUMENT_BYTES - RUN_JSON.length()));
        Files.writeString(layout.runFile(), padded);
        assertEquals(DocumentFields.MAX_DOCUMENT_BYTES, Files.size(layout.runFile()));
        assertEquals(StorageFixtures.descriptor(), store.read(layout));
    }

    @Test
    @DisplayName("every change needs the lock held on this project")
    void needsTheLock() throws IOException {
        ProjectLayout other = new ProjectLayout(temporary.resolve("Other"));
        new ProjectStore(PROJECT_CLOCK).create(other, new ProjectId("other"));
        try (ProjectLock otherLock = ProjectLock.acquire(other, RUN_CLOCK)) {
            IllegalStateException wrong =
                    assertThrows(IllegalStateException.class, () -> store.reserve(otherLock));
            assertEquals(
                    "the lock held is on " + other.root() + ", not on " + project.root(),
                    wrong.getMessage());
        }
        lock.close();
        IllegalStateException released =
                assertThrows(IllegalStateException.class, () -> store.reserve(lock));
        assertAll(
                () ->
                        assertEquals(
                                "the lock on " + project.root() + " has been released",
                                released.getMessage()),
                () ->
                        assertThrows(
                                IllegalStateException.class,
                                () -> store.record(lock, StorageFixtures.identity())),
                () ->
                        assertThrows(
                                IllegalStateException.class,
                                () -> store.update(lock, StorageFixtures.descriptor())));
    }

    @Test
    @DisplayName("reserving in a directory with no runs/ is refused as not a project")
    void notAProject() throws IOException {
        Files.delete(project.runsDirectory());
        NoSuchFileException thrown =
                assertThrows(NoSuchFileException.class, () -> store.reserve(lock));
        assertEquals(
                project.runsDirectory() + ": not a CometGUI project: no runs/",
                thrown.getMessage());
    }

    @Test
    @DisplayName("the layout's provenance file names are the provenance writers' own")
    void provenanceNamesAgree() {
        assertAll(
                () -> assertEquals(ManifestWriter.FILE_NAME, RunLayout.PROVENANCE_JSON_FILE_NAME),
                () ->
                        assertEquals(
                                ProvenanceReportWriter.FILE_NAME,
                                RunLayout.PROVENANCE_RST_FILE_NAME));
    }

    @Test
    @DisplayName("the stores refuse null collaborators by name")
    void nulls() {
        assertAll(
                () ->
                        assertThrows(
                                NullPointerException.class,
                                () -> new ProjectStore(Nulls.of(Clock.class))),
                () ->
                        assertThrows(
                                NullPointerException.class,
                                () ->
                                        new RunStore(
                                                project, RUN_CLOCK, Nulls.of(RunIdSource.class))));
    }
}
