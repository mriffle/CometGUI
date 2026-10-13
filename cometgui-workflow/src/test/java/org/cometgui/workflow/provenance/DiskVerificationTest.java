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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.ports.HashService;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.domain.secrets.SecretRedactor;
import org.cometgui.provenance.hashing.CachingHashService;
import org.cometgui.provenance.hashing.StreamingHashService;
import org.cometgui.provenance.manifest.ExecutionRecord;
import org.cometgui.provenance.manifest.FileDirection;
import org.cometgui.provenance.manifest.FileRecord;
import org.cometgui.provenance.manifest.LogRecord;
import org.cometgui.provenance.manifest.ProvenanceManifest;
import org.cometgui.provenance.manifest.ProvenanceStatus;
import org.cometgui.provenance.manifest.ToolRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * "Verify against disk" ({@code P13-7}, the product half): every listed file hashed again, cache
 * bypassed, and each reported as matching, differing, missing or unreadable -- naming the file.
 */
class DiskVerificationTest {

    @TempDir private Path tmp;

    private RunDirectories dirs;

    private CompleteRun run;

    @BeforeEach
    void create() throws IOException {
        dirs = RunDirectories.create(tmp);
        run = CompleteRun.create(dirs, SecretRedactor.patternsOnly());
    }

    @AfterEach
    void close() throws IOException {
        dirs.close();
    }

    @Test
    @DisplayName("an untouched run: every file, log and tool matches, in record order")
    void allMatch() throws IOException {
        DiskVerification result =
                LoadedRunViewTest.reader().load(run.layout()).verifyAgainstDisk(() -> false);

        assertAll(
                () -> assertTrue(result.complete()),
                () -> assertTrue(result.allMatch()),
                () -> assertEquals(15, result.checks().size(), "10 files, 3 logs, 2 executables"),
                () -> assertEquals(15L, result.count(DiskOutcome.MATCHES)),
                () ->
                        assertEquals(
                                List.of(
                                        "input", "input", "input", "output", "output", "output",
                                        "output", "output", "input", "output", "log", "log", "log",
                                        "tool", "tool"),
                                result.checks().stream()
                                        .map(DiskVerification.Check::kind)
                                        .toList()));
        DiskVerification.Check comet = result.checks().get(13);
        assertAll(
                () -> assertEquals("comet", comet.role()),
                () -> assertEquals(run.cometExe().toString(), comet.path()),
                () -> assertEquals(Optional.of(RunDirectories.ABC_MD5), comet.foundMd5()),
                () -> assertEquals(Optional.of(RunDirectories.ABC_SHA256), comet.foundSha256()),
                () -> assertEquals("comet-01", result.checks().get(10).role()),
                () -> assertEquals(run.cometLog1().toString(), result.checks().get(10).path()));
    }

    @Test
    @DisplayName("a deleted file is missing and a modified one differs, each named, both digests")
    void missingAndDiffers() throws IOException {
        FileHashes before = RunDirectories.digests(run.pin2());
        Files.delete(run.pepXml1());
        Files.writeString(run.pin2(), "SpecId\tLabel\n2\t-1\n", StandardCharsets.UTF_8);
        FileHashes after = RunDirectories.digests(run.pin2());
        assertNotEquals(before, after);

        DiskVerification result =
                LoadedRunViewTest.reader().load(run.layout()).verifyAgainstDisk(() -> false);

        DiskVerification.Check missing = check(result, run.pepXml1());
        DiskVerification.Check differs = check(result, run.pin2());
        assertAll(
                () -> assertFalse(result.allMatch()),
                () -> assertTrue(result.complete()),
                () -> assertEquals(1L, result.count(DiskOutcome.MISSING)),
                () -> assertEquals(1L, result.count(DiskOutcome.DIFFERS)),
                () -> assertEquals(13L, result.count(DiskOutcome.MATCHES)),
                () -> assertEquals(DiskOutcome.MISSING, missing.outcome()),
                () -> assertEquals(run.pepXml1().toString(), missing.path()),
                () -> assertEquals("pepxml", missing.role()),
                () -> assertEquals(Optional.empty(), missing.foundSha256()),
                () -> assertEquals(DiskOutcome.DIFFERS, differs.outcome()),
                () -> assertEquals(run.pin2().toString(), differs.path()),
                () -> assertEquals(before.md5(), differs.recordedMd5()),
                () -> assertEquals(before.sha256(), differs.recordedSha256()),
                () -> assertEquals(Optional.of(after.md5()), differs.foundMd5()),
                () -> assertEquals(Optional.of(after.sha256()), differs.foundSha256()));
    }

    @Test
    @DisplayName("something at the path that is not a readable file is unreadable, with a reason")
    void unreadable() throws IOException {
        Files.delete(run.cometLog1());
        Files.createDirectory(run.cometLog1());

        DiskVerification result =
                LoadedRunViewTest.reader().load(run.layout()).verifyAgainstDisk(() -> false);

        DiskVerification.Check log = check(result, run.cometLog1());
        assertAll(
                () -> assertEquals(DiskOutcome.UNREADABLE, log.outcome()),
                () -> assertEquals("log", log.kind()),
                () ->
                        assertEquals(
                                Optional.of(
                                        run.cometLog1()
                                                + " is not a regular file that can be read"),
                                log.problem()),
                () -> assertEquals(1L, result.count(DiskOutcome.UNREADABLE)));
    }

    @Test
    @DisplayName("a file that vanishes while it is being hashed is missing")
    void vanishedWhileHashing() throws IOException {
        Path target = run.pepXml1();
        HashService vanishing =
                path -> {
                    if (path.equals(target)) {
                        throw new NoSuchFileException(path.toString());
                    }
                    return new StreamingHashService().hash(path);
                };

        DiskVerification result =
                new ProvenanceReader(
                                SecretRedactor::patternsOnly, new CachingHashService(vanishing))
                        .load(run.layout())
                        .verifyAgainstDisk(() -> false);

        assertEquals(DiskOutcome.MISSING, check(result, target).outcome());
        assertEquals(14L, result.count(DiskOutcome.MATCHES));
    }

    @Test
    @DisplayName("separate stdout and stderr logs are each listed and each verified")
    void separateLogs() throws IOException {
        RunDirectories other = RunDirectories.create(Files.createDirectory(tmp.resolve("other")));
        try {
            RunLayout layout =
                    other.recordedRun(
                            "run-0007", RunDirectories.CREATED, RunDirectories.cometParams());
            Path exe = RunDirectories.write(layout, "bin/tool", "tool\n");
            Path out = RunDirectories.write(layout, "logs/out.log", "out\n");
            Path err = RunDirectories.write(layout, "logs/err.log", "err\n");
            LogRecord stdout = new LogRecord(out, RunDirectories.digests(out));
            LogRecord stderr = new LogRecord(err, RunDirectories.digests(err));
            ToolRecord both = toolWithLogs(exe, layout, Optional.of(stdout), Optional.of(stderr));
            ToolRecord onlyErr = toolWithLogs(exe, layout, Optional.empty(), Optional.of(stderr));
            ProvenanceManifest manifest =
                    ProvenanceManifest.current(
                            RunDirectories.runRecord(layout, ProvenanceStatus.COMPLETED),
                            RunDirectories.application(),
                            Map.of(),
                            List.of(both, onlyErr),
                            List.of());
            RunDirectories.seal(layout, manifest, SecretRedactor.patternsOnly());
            Files.delete(err);

            LoadedRun loaded = LoadedRunViewTest.reader().load(layout);
            DiskVerification result = loaded.verifyAgainstDisk(() -> false);

            assertEquals(
                    List.of("stdout", "stderr", "stderr"),
                    loaded.view().logs().stream().map(ProvenanceView.Log::streams).toList());
            assertEquals(
                    List.of(out, err, err),
                    loaded.view().logs().stream().map(ProvenanceView.Log::location).toList());
            assertEquals(
                    List.of("log", "log", "tool"),
                    result.checks().stream().map(DiskVerification.Check::kind).toList(),
                    "each log once, the shared executable once");
            assertEquals(DiskOutcome.MATCHES, check(result, out).outcome());
            assertEquals(DiskOutcome.MISSING, check(result, err).outcome());
        } finally {
            other.close();
        }
    }

    private static ToolRecord toolWithLogs(
            Path exe, RunLayout layout, Optional<LogRecord> stdout, Optional<LogRecord> stderr)
            throws IOException {
        return new ToolRecord(
                "tool",
                "1.0",
                Optional.empty(),
                exe,
                RunDirectories.digests(exe),
                false,
                Optional.empty(),
                Set.of(),
                Optional.empty(),
                new ExecutionRecord(
                        new ToolCommand(List.of(exe.toString()), layout.root(), Map.of()),
                        RunDirectories.STARTED,
                        RunDirectories.ENDED,
                        0,
                        stdout,
                        stderr,
                        ProvenanceStatus.COMPLETED),
                List.of());
    }

    /** MD5 and SHA-256 of "abc" (RFC 1321, FIPS 180-2), typed here. */
    private static final String ABC_MD5 = "900150983cd24fb0d6963f7d28e17f72";

    private static final String ABC_SHA256 =
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    /** MD5 and SHA-256 of "message digest", typed here: the wrong half of each record. */
    private static final String OTHER_MD5 = "f96b697d7cb7938d525a2f31aaf161d0";

    private static final String OTHER_SHA256 =
            "f7846f55cf23e14eebeab5b4e1550cad5b509e3348fbc4efa3a1413d393cb650";

    @Test
    @DisplayName("one digest wrong, the other right: differs, for files, a tool and a log")
    void eitherDigestAloneDiffers() throws IOException {
        RunDirectories other = RunDirectories.create(Files.createDirectory(tmp.resolve("half")));
        try {
            RunLayout layout =
                    other.recordedRun(
                            "run-0008", RunDirectories.CREATED, RunDirectories.cometParams());
            Path md5Wrong = RunDirectories.write(layout, "outputs/a.pin", "abc");
            Path shaWrong = RunDirectories.write(layout, "outputs/b.pin", "abc");
            Path exe = RunDirectories.write(layout, "bin/tool", "abc");
            Path stdout = RunDirectories.write(layout, "logs/out.log", "abc");
            Path stderr = RunDirectories.write(layout, "logs/err.log", "abc");
            FileHashes md5Off = new FileHashes(OTHER_MD5, ABC_SHA256);
            FileHashes shaOff = new FileHashes(ABC_MD5, OTHER_SHA256);
            ToolRecord tool =
                    new ToolRecord(
                            "tool",
                            "1.0",
                            Optional.empty(),
                            exe,
                            md5Off,
                            false,
                            Optional.empty(),
                            Set.of(),
                            Optional.of("tool-01"),
                            new ExecutionRecord(
                                    new ToolCommand(
                                            List.of(exe.toString()), layout.root(), Map.of()),
                                    RunDirectories.STARTED,
                                    RunDirectories.ENDED,
                                    0,
                                    Optional.of(new LogRecord(stdout, shaOff)),
                                    Optional.of(new LogRecord(stderr, md5Off)),
                                    ProvenanceStatus.COMPLETED),
                            List.of());
            ProvenanceManifest manifest =
                    ProvenanceManifest.current(
                            RunDirectories.runRecord(layout, ProvenanceStatus.COMPLETED),
                            RunDirectories.application(),
                            Map.of(),
                            List.of(tool),
                            List.of(
                                    new FileRecord(
                                            FileDirection.OUTPUT,
                                            "pin",
                                            md5Wrong,
                                            3L,
                                            RunDirectories.STARTED,
                                            md5Off,
                                            ProvenanceStatus.COMPLETED),
                                    new FileRecord(
                                            FileDirection.INPUT,
                                            "spectrum",
                                            shaWrong,
                                            3L,
                                            RunDirectories.STARTED,
                                            shaOff,
                                            ProvenanceStatus.COMPLETED)));
            RunDirectories.seal(layout, manifest, SecretRedactor.patternsOnly());

            DiskVerification result =
                    LoadedRunViewTest.reader().load(layout).verifyAgainstDisk(() -> false);

            assertEquals(
                    List.of(
                            new DiskVerification.Check(
                                    "output",
                                    "pin",
                                    md5Wrong.toString(),
                                    md5Wrong,
                                    DiskOutcome.DIFFERS,
                                    OTHER_MD5,
                                    ABC_SHA256,
                                    Optional.of(ABC_MD5),
                                    Optional.of(ABC_SHA256),
                                    Optional.empty()),
                            new DiskVerification.Check(
                                    "input",
                                    "spectrum",
                                    shaWrong.toString(),
                                    shaWrong,
                                    DiskOutcome.DIFFERS,
                                    ABC_MD5,
                                    OTHER_SHA256,
                                    Optional.of(ABC_MD5),
                                    Optional.of(ABC_SHA256),
                                    Optional.empty()),
                            new DiskVerification.Check(
                                    "log",
                                    "tool-01",
                                    stdout.toString(),
                                    stdout,
                                    DiskOutcome.DIFFERS,
                                    ABC_MD5,
                                    OTHER_SHA256,
                                    Optional.of(ABC_MD5),
                                    Optional.of(ABC_SHA256),
                                    Optional.empty()),
                            new DiskVerification.Check(
                                    "log",
                                    "tool-01",
                                    stderr.toString(),
                                    stderr,
                                    DiskOutcome.DIFFERS,
                                    OTHER_MD5,
                                    ABC_SHA256,
                                    Optional.of(ABC_MD5),
                                    Optional.of(ABC_SHA256),
                                    Optional.empty()),
                            new DiskVerification.Check(
                                    "tool",
                                    "tool",
                                    exe.toString(),
                                    exe,
                                    DiskOutcome.DIFFERS,
                                    OTHER_MD5,
                                    ABC_SHA256,
                                    Optional.of(ABC_MD5),
                                    Optional.of(ABC_SHA256),
                                    Optional.empty())),
                    result.checks());
            assertEquals(5L, result.count(DiskOutcome.DIFFERS));
            assertFalse(result.allMatch());
        } finally {
            other.close();
        }
    }

    @Test
    @DisplayName("a cancelled verification stops between files and says it is incomplete")
    void cancelled() throws IOException {
        AtomicInteger asked = new AtomicInteger();
        DiskVerification result =
                LoadedRunViewTest.reader()
                        .load(run.layout())
                        .verifyAgainstDisk(() -> asked.incrementAndGet() > 3);

        assertAll(
                () -> assertFalse(result.complete()),
                () -> assertFalse(result.allMatch()),
                () -> assertEquals(3, result.checks().size()),
                () -> assertEquals(4, asked.get()));
    }

    @Test
    @DisplayName("the cache is bypassed: a remembered digest is never the answer")
    void cacheBypassed() throws IOException {
        Lying delegate = new Lying(run.pepXml1());
        CachingHashService hashes = new CachingHashService(delegate);
        settle();
        // Control: the cache really does remember -- and serve -- the lie.
        FileHashes lie = hashes.hash(run.pepXml1());
        assertEquals(Lying.LIE, lie);
        assertEquals(Lying.LIE, hashes.hash(run.pepXml1()), "the cache serves what it remembered");
        assertEquals(1, delegate.lies.get());
        assertTrue(hashes.size() > 0);

        DiskVerification result =
                new ProvenanceReader(SecretRedactor::patternsOnly, hashes)
                        .load(run.layout())
                        .verifyAgainstDisk(() -> false);

        assertEquals(DiskOutcome.MATCHES, check(result, run.pepXml1()).outcome());
        assertTrue(result.allMatch());
    }

    /** Waits, by polling the clock, until every file written so far is older than this second. */
    private static void settle() {
        Instant written = Instant.now();
        while (!Instant.now().truncatedTo(ChronoUnit.SECONDS).isAfter(written)) {
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
        }
    }

    static DiskVerification.Check check(DiskVerification result, Path path) {
        return result.checks().stream()
                .filter(check -> check.location().equals(path))
                .findFirst()
                .orElseThrow(() -> new AssertionError(path + " was not checked"));
    }

    /** A hasher that lies about one file the first time it is asked, and is honest after. */
    private static final class Lying implements HashService {

        static final FileHashes LIE =
                new FileHashes(RunDirectories.ABC_MD5, RunDirectories.ABC_SHA256);

        private final StreamingHashService real = new StreamingHashService();

        private final Path target;

        private final AtomicInteger lies = new AtomicInteger();

        Lying(Path target) {
            this.target = target;
        }

        @Override
        public FileHashes hash(Path path) throws IOException {
            if (path.equals(target) && lies.getAndIncrement() == 0) {
                return LIE;
            }
            return real.hash(path);
        }
    }
}
