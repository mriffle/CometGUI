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

package org.cometgui.workflow.provenance;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.domain.project.ProjectId;
import org.cometgui.domain.project.ProjectLayout;
import org.cometgui.domain.run.ArchivedFile;
import org.cometgui.domain.run.AttemptOutcome;
import org.cometgui.domain.run.DatabaseDelivery;
import org.cometgui.domain.run.IndexMode;
import org.cometgui.domain.run.RecordedInput;
import org.cometgui.domain.run.RunDescriptor;
import org.cometgui.domain.run.RunId;
import org.cometgui.domain.run.RunIdentity;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.domain.secrets.SecretRedactor;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.parser.ReleaseDefaults;
import org.cometgui.provenance.events.ProvenanceEventLog;
import org.cometgui.provenance.events.ProvenanceEventType;
import org.cometgui.provenance.manifest.ApplicationRecord;
import org.cometgui.provenance.manifest.ExecutionRecord;
import org.cometgui.provenance.manifest.FileDirection;
import org.cometgui.provenance.manifest.FileRecord;
import org.cometgui.provenance.manifest.LogRecord;
import org.cometgui.provenance.manifest.ManifestWriter;
import org.cometgui.provenance.manifest.ProvenanceManifest;
import org.cometgui.provenance.manifest.ProvenanceStatus;
import org.cometgui.provenance.manifest.RunRecord;
import org.cometgui.provenance.manifest.ToolRecord;
import org.cometgui.provenance.report.ProvenanceReportWriter;
import org.cometgui.workflow.storage.ProjectLock;
import org.cometgui.workflow.storage.ProjectStore;
import org.cometgui.workflow.storage.ReservedRun;
import org.cometgui.workflow.storage.RunStore;

/**
 * Builds project and run directories for the read model's tests, by the product's own storage
 * writers ({@link RunStore}, {@link ManifestWriter}, {@link ProvenanceReportWriter}, {@link
 * ProvenanceEventLog}) -- never by the code under test.
 *
 * <p>Every digest a record carries is computed here, by this class's own {@link MessageDigest}
 * loop, so that no expectation is a value the read model or the product's hasher produced.
 */
final class RunDirectories {

    static final String PROJECT_ID = "project-p13";

    static final Instant CREATED = Instant.parse("2026-10-13T09:00:00Z");

    static final Instant STARTED = Instant.parse("2026-10-13T09:00:01Z");

    static final Instant ENDED = Instant.parse("2026-10-13T09:12:35.567Z");

    /** Content of the fake Comet executable: the RFC 1321 / FIPS 180-2 vector "abc". */
    static final String COMET_EXE_CONTENT = "abc";

    static final String ABC_MD5 = "900150983cd24fb0d6963f7d28e17f72";

    static final String ABC_SHA256 =
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    /** Content of the fake Percolator executable: the RFC 1321 vector "message digest". */
    static final String PERCOLATOR_EXE_CONTENT = "message digest";

    static final String MESSAGE_DIGEST_MD5 = "f96b697d7cb7938d525a2f31aaf161d0";

    static final String MESSAGE_DIGEST_SHA256 =
            "f7846f55cf23e14eebeab5b4e1550cad5b509e3348fbc4efa3a1413d393cb650";

    static final String RELEASE = "2026.03.0";

    private final Path root;

    private final ProjectLayout project;

    private final ProjectLock lock;

    private RunDirectories(Path root, ProjectLayout project, ProjectLock lock) {
        this.root = root;
        this.project = project;
        this.lock = lock;
    }

    static RunDirectories create(Path tmp) throws IOException {
        Path root = tmp.toRealPath();
        ProjectLayout project = new ProjectLayout(root.resolve("project"));
        Clock clock = Clock.fixed(CREATED, ZoneOffset.UTC);
        new ProjectStore(clock).create(project, new ProjectId(PROJECT_ID));
        return new RunDirectories(root, project, ProjectLock.acquire(project, clock));
    }

    Path root() {
        return root;
    }

    ProjectLayout project() {
        return project;
    }

    void close() throws IOException {
        lock.close();
    }

    // ------------------------------------------------------------------- run directories --

    /** A run reserved and recorded with two spectra, a FASTA and the given comet.params text. */
    RunLayout recordedRun(String runId, Instant created, String cometParams) throws IOException {
        RunStore store =
                new RunStore(project, Clock.fixed(created, ZoneOffset.UTC), () -> new RunId(runId));
        ReservedRun reserved = store.reserve(lock);
        RunLayout layout = reserved.layout();
        Files.writeString(layout.cometParamsFile(), cometParams, StandardCharsets.UTF_8);
        List<RecordedInput> spectra = List.of(input("k562_1.mzML"), input("k562_2.mzML"));
        RunIdentity identity =
                new RunIdentity(
                        reserved.runId(),
                        new ProjectId(PROJECT_ID),
                        reserved.created(),
                        RELEASE,
                        RunIdentity.spectraOf(spectra),
                        input("db.fasta"),
                        new ArchivedFile(
                                RunLayout.cometParamsRelativePath(),
                                Files.size(layout.cometParamsFile()),
                                digests(layout.cometParamsFile())),
                        IndexMode.NONE,
                        DatabaseDelivery.PARAMETER_FILE);
        store.record(lock, identity);
        return layout;
    }

    /** Records an attempt that started, and -- unless {@code outcome} is running -- ended. */
    void attempt(RunLayout layout, Instant started, AttemptOutcome outcome, Instant ended)
            throws IOException {
        RunStore store = store();
        RunDescriptor descriptor = store.read(layout).withNewAttempt(started);
        descriptor = store.update(lock, descriptor);
        if (outcome != AttemptOutcome.RUNNING) {
            store.update(lock, descriptor.withAttemptFinished(outcome, ended));
        }
    }

    RunStore store() {
        return new RunStore(project, Clock.fixed(CREATED, ZoneOffset.UTC), () -> new RunId("x"));
    }

    private RecordedInput input(String name) throws IOException {
        Path file = Files.createDirectories(root.resolve("in")).resolve(name);
        if (!Files.exists(file)) {
            Files.writeString(file, "content of " + name + "\n", StandardCharsets.UTF_8);
        }
        return new RecordedInput(
                file, Files.size(file), Files.getLastModifiedTime(file).toInstant(), digests(file));
    }

    Path inputFile(String name) {
        return root.resolve("in").resolve(name);
    }

    // -------------------------------------------------------------------- comet.params --

    /** The bundled 2026.03.0 {@code comet -q} file with four hand-chosen lines changed. */
    static String cometParams() {
        String text =
                new String(
                        ReleaseDefaults.bundledFile(ToolVersion.parse(RELEASE)),
                        StandardCharsets.UTF_8);
        String changed = replaceLine(text, "num_threads", "num_threads = 8");
        changed =
                replaceLine(
                        changed,
                        "peptide_mass_tolerance_upper",
                        "peptide_mass_tolerance_upper = 10.0");
        changed = replaceLine(changed, "output_percolatorfile", "output_percolatorfile = 1");
        return replaceLine(changed, "database_name", "database_name = /data/human.fasta");
    }

    static String replaceLine(String text, String name, String replacement) {
        StringBuilder out = new StringBuilder();
        int replaced = 0;
        for (String line : text.split("\n", -1)) {
            if (line.startsWith(name + " ") || line.startsWith(name + "=")) {
                out.append(replacement);
                replaced++;
            } else {
                out.append(line);
            }
            out.append('\n');
        }
        if (replaced != 1) {
            throw new IllegalStateException(name + " appears " + replaced + " times");
        }
        return out.substring(0, out.length() - 1);
    }

    // ---------------------------------------------------------------------- the record --

    /** Writes a file under the run and returns its absolute path. */
    static Path write(RunLayout layout, String relative, String content) throws IOException {
        Path file = layout.root().resolve(relative);
        Files.createDirectories(Objects.requireNonNull(file.getParent(), relative));
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    /** A file record whose digests, size and time are this class's own reading of the file. */
    static FileRecord fileRecord(
            FileDirection direction, String role, Path file, ProvenanceStatus status)
            throws IOException {
        return new FileRecord(
                direction,
                role,
                file,
                Files.size(file),
                Instant.parse("2026-10-13T09:05:00.250Z"),
                digests(file),
                status);
    }

    static ApplicationRecord application() {
        return new ApplicationRecord(
                "0.13.0",
                "abc1234def",
                "Linux",
                "6.8.0-137-generic",
                "amd64",
                "25.0.1",
                Locale.forLanguageTag("en-US"),
                Locale.forLanguageTag("fr-FR"),
                ZoneId.of("Europe/Paris"));
    }

    /** One tool invocation's record. */
    static ToolRecord tool(
            String name,
            String version,
            Path executable,
            boolean managed,
            Set<String> capabilities,
            String stage,
            List<String> argv,
            Path workingDirectory,
            Map<String, String> environment,
            Instant start,
            Instant end,
            int exitCode,
            ProvenanceStatus status,
            Path log,
            List<String> warnings)
            throws IOException {
        Optional<LogRecord> logRecord = Optional.of(new LogRecord(log, digests(log)));
        return new ToolRecord(
                name,
                version,
                Optional.of("v" + version),
                executable,
                digests(executable),
                managed,
                managed ? Optional.of(name + "-" + version + "-linux-x86_64") : Optional.empty(),
                capabilities,
                Optional.of(stage),
                new ExecutionRecord(
                        new ToolCommand(argv, workingDirectory, environment),
                        start,
                        end,
                        exitCode,
                        logRecord,
                        logRecord,
                        status),
                warnings);
    }

    /** Writes provenance.json and provenance.rst by phase 04's writers, as a run's finish does. */
    static void seal(RunLayout layout, ProvenanceManifest manifest, SecretRedactor redactor)
            throws IOException {
        ManifestWriter.redactingWith(redactor).writeTo(layout.provenanceJsonFile(), manifest);
        ProvenanceReportWriter.redactingWith(redactor)
                .writeTo(layout.provenanceRstFile(), manifest);
    }

    static RunRecord runRecord(RunLayout layout, ProvenanceStatus status) {
        String name = String.valueOf(layout.root().getFileName());
        return new RunRecord(
                new RunId(name.substring(name.indexOf('-') + 1)),
                PROJECT_ID,
                status,
                STARTED,
                Optional.of(ENDED));
    }

    /** Appends events through the product's event log, all stamped with one fixed instant. */
    static void events(
            RunLayout layout,
            Instant at,
            List<Map.Entry<ProvenanceEventType, Map<String, String>>> events)
            throws IOException {
        try (ProvenanceEventLog log =
                ProvenanceEventLog.openAppend(
                        layout.eventLogFile(),
                        SecretRedactor.patternsOnly(),
                        Clock.fixed(at, ZoneOffset.UTC))) {
            for (Map.Entry<ProvenanceEventType, Map<String, String>> event : events) {
                log.append(event.getKey(), event.getValue());
            }
        }
    }

    static Map.Entry<ProvenanceEventType, Map<String, String>> event(
            ProvenanceEventType type, String... keysAndValues) {
        Map<String, String> payload = new LinkedHashMap<>();
        for (int index = 0; index < keysAndValues.length; index += 2) {
            payload.put(keysAndValues[index], keysAndValues[index + 1]);
        }
        return Map.entry(type, payload);
    }

    // ------------------------------------------------------------------ the test's hasher --

    /** MD5 and SHA-256 of a file, by this test's own loop -- never the product's hasher. */
    static FileHashes digests(Path file) throws IOException {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            try (InputStream in = Files.newInputStream(file)) {
                int read;
                while ((read = in.read(buffer)) > 0) {
                    md5.update(buffer, 0, read);
                    sha256.update(buffer, 0, read);
                }
            }
            return new FileHashes(
                    HexFormat.of().formatHex(md5.digest()),
                    HexFormat.of().formatHex(sha256.digest()));
        } catch (NoSuchAlgorithmException missing) {
            throw new IllegalStateException(missing);
        }
    }

    /** A sorted copy, for building settings maps readably. */
    static Map<String, String> settings(String... keysAndValues) {
        Map<String, String> settings = new TreeMap<>();
        for (int index = 0; index < keysAndValues.length; index += 2) {
            settings.put(keysAndValues[index], keysAndValues[index + 1]);
        }
        return settings;
    }

    static List<String> list(String... values) {
        return new ArrayList<>(List.of(values));
    }
}
