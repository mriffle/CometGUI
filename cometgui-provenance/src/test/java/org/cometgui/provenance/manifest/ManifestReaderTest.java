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

package org.cometgui.provenance.manifest;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.run.RunId;
import org.cometgui.provenance.manifest.ManifestReader.InvalidManifestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

/**
 * The reader, proved by parsing hand-typed documents and asserting hand-typed values.
 *
 * <p><strong>{@link ManifestWriter} appears nowhere in this file.</strong> That is the whole design
 * of it. A round trip proves a writer and a reader agree; if both used the key {@code "runid"} the
 * round trip would be perfectly green and every document on disk would be wrong. So the documents
 * below were typed out from the format {@link ManifestWriter} documents -- schema version first,
 * records in declaration order, sorted maps, {@code null} for an absent optional, UTC timestamps
 * with three fractional digits -- and every value asserted against them was typed out beside it. If
 * the reader reads the wrong key, converts the wrong way, or drops a field, these tests fail and
 * nothing else in the repository has to change for them to.
 *
 * <p>The digest pairs are the published RFC 1321 and NIST vectors for the empty string and for
 * {@code "abc"}, transcribed rather than computed, so that no expected value here can have come
 * from CometGUI code.
 *
 * <p><strong>The mutation helper is guarded on purpose.</strong> {@link #exceptFor} takes a
 * hand-typed fragment out of a hand-typed document and puts a hand-typed replacement in its place,
 * and it <em>asserts that the fragment was there</em>. A replacement that silently matched nothing
 * would leave the document valid and the rejection test would pass while proving nothing, which is
 * the failure mode this phase has already met once.
 *
 * <p><strong>The two path-bearing documents are disabled on Windows, and that is a real hole named
 * rather than hidden.</strong> A manifest records the paths a run actually used, so a POSIX
 * document read on Windows produces {@link Path}s that a hand-typed POSIX expectation cannot match,
 * and {@code FileRecord} would reject {@code /data/...} as relative there. The path-free document
 * runs everywhere. Phase 15 owns the platform matrix and should add the Windows twin rather than
 * relaxing these.
 */
class ManifestReaderTest {

    /** MD5 of {@code "abc"}, RFC 1321. */
    private static final String ABC_MD5 = "900150983cd24fb0d6963f7d28e17f72";

    /** SHA-256 of {@code "abc"}, NIST. */
    private static final String ABC_SHA256 =
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    /** MD5 of the empty file, RFC 1321. */
    private static final String EMPTY_MD5 = "d41d8cd98f00b204e9800998ecf8427e";

    /** SHA-256 of the empty file, NIST. */
    private static final String EMPTY_SHA256 =
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    /** A secret with no other reason to appear anywhere, hand-typed from the seeded corpus. */
    private static final String SECRET = "ghp_S3cr3tT0k3nExampleValue0123456789ab";

    /** A second seeded secret, used where the first would not fit the position under test. */
    private static final String PASSPHRASE = "correct-horse-battery-staple";

    /** A temporary directory for the tests that read a document off disk. */
    @TempDir private Path tempDir;

    /**
     * The whole of a finished run, typed out.
     *
     * @return the document
     */
    private static String completedRunDocument() {
        return """
            {
              "schemaVersion": 1,
              "run": {
                "runId": "run-20260831-091500",
                "projectId": "project-alpha",
                "status": "partial",
                "start": "2026-08-31T09:14:00.250Z",
                "end": "2026-08-31T09:48:00.000Z",
                "durationMillis": 2039750
              },
              "application": {
                "cometGuiVersion": "0.1.0-SNAPSHOT",
                "buildIdentifier": "9f8c1d2e4b7a",
                "osName": "Linux",
                "osVersion": "6.8.0-137-generic",
                "architecture": "amd64",
                "jvmVersion": "25.0.4.1",
                "locale": "en-US",
                "formatLocale": "de-DE",
                "zoneId": "Europe/Berlin"
              },
              "settings": {
                "comet.num-threads": "8",
                "limelight.upload-url": "https://ll-user:[REDACTED]@ll.example.org/up",
                "percolator.seed": "9001"
              },
              "tools": [
                {
                  "name": "comet",
                  "version": "2026.02.2",
                  "releaseTag": "v2026.02.2",
                  "executablePath": "/opt/cometgui/tools/comet-2026.02.2/comet",
                  "md5": "900150983cd24fb0d6963f7d28e17f72",
                  "sha256": "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                  "managed": true,
                  "artefactIdentity": "comet-2026.02.2-linux-x86_64.tar.gz",
                  "capabilities": [
                    "mzml",
                    "mzxml"
                  ],
                  "stageId": "search",
                  "execution": {
                    "argv": [
                      "/opt/cometgui/tools/comet-2026.02.2/comet",
                      "-P",
                      "comet.params",
                      "--password",
                      "[REDACTED]"
                    ],
                    "workingDirectory": "/var/cometgui/runs/run-20260831-091500",
                    "environment": {
                      "COMET_PARAMS": "comet.params",
                      "LIMELIGHT_API_KEY": "[REDACTED]",
                      "PATH": "/usr/bin:/bin"
                    },
                    "start": "2026-08-31T09:15:00.000Z",
                    "end": "2026-08-31T09:47:30.500Z",
                    "durationMillis": 1950500,
                    "exitCode": 0,
                    "stdout": {
                      "path": "/var/cometgui/runs/run-20260831-091500/comet.stdout.log",
                      "md5": "900150983cd24fb0d6963f7d28e17f72",
                      "sha256": "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
                    },
                    "stderr": null,
                    "status": "completed"
                  },
                  "warnings": []
                },
                {
                  "name": "percolator",
                  "version": "3.07.1",
                  "releaseTag": null,
                  "executablePath": "/usr/local/bin/percolator",
                  "md5": "d41d8cd98f00b204e9800998ecf8427e",
                  "sha256": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                  "managed": false,
                  "artefactIdentity": null,
                  "capabilities": [],
                  "stageId": null,
                  "execution": {
                    "argv": [
                      "/usr/local/bin/percolator",
                      "--results-psms",
                      "percolator.psms.txt"
                    ],
                    "workingDirectory": "/var/cometgui/runs/run-20260831-091500",
                    "environment": {},
                    "start": "2026-08-31T09:47:31.000Z",
                    "end": "2026-08-31T09:49:02.125Z",
                    "durationMillis": 91125,
                    "exitCode": 1,
                    "stdout": null,
                    "stderr": {
                      "path": "/var/cometgui/runs/run-20260831-091500/percolator.stderr.log",
                      "md5": "d41d8cd98f00b204e9800998ecf8427e",
                      "sha256": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
                    },
                    "status": "failed"
                  },
                  "warnings": [
                    "this build has no xml capability",
                    "skipped 2 spectra in /data/protéomique/HeLa_1µg_rep1.mzML",
                    "wrote /data/🧬-run/résultats.txt"
                  ]
                }
              ],
              "files": [
                {
                  "direction": "input",
                  "role": "spectra",
                  "path": "/data/proteomics/HeLa_1ug_rep1.mzML",
                  "sizeBytes": 1234567890123,
                  "modifiedAt": "2026-08-30T18:00:00.001Z",
                  "md5": "900150983cd24fb0d6963f7d28e17f72",
                  "sha256": "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                  "status": "completed"
                },
                {
                  "direction": "output",
                  "role": "spectrum-export",
                  "path": "/data/exports/\\"quoted\\"\\\\name.txt",
                  "sizeBytes": 0,
                  "modifiedAt": "2026-08-31T09:49:02.000Z",
                  "md5": "d41d8cd98f00b204e9800998ecf8427e",
                  "sha256": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                  "status": "partial"
                }
              ]
            }
            """;
    }

    /**
     * A run that has started and not finished: the document a crash leaves behind, and the one that
     * carries no path so that it can be asserted on every platform.
     *
     * @return the document
     */
    private static String runningRunDocument() {
        return """
            {
              "schemaVersion": 1,
              "run": {
                "runId": "run-20260831-101500",
                "projectId": "project-beta",
                "status": "running",
                "start": "2026-08-31T10:15:00.000Z",
                "end": null,
                "durationMillis": null
              },
              "application": {
                "cometGuiVersion": "0.1.0-SNAPSHOT",
                "buildIdentifier": "9f8c1d2e4b7a",
                "osName": "Windows 11",
                "osVersion": "10.0",
                "architecture": "aarch64",
                "jvmVersion": "25.0.4.1",
                "locale": "und",
                "formatLocale": "tr-TR",
                "zoneId": "UTC"
              },
              "settings": {},
              "tools": [],
              "files": []
            }
            """;
    }

    /**
     * A path-free document with a finished run, for the rules that need an interval.
     *
     * @return the document
     */
    private static String finishedButEmptyDocument() {
        return exceptFor(
                exceptFor(
                        exceptFor(
                                runningRunDocument(),
                                "\"status\": \"running\"",
                                "\"status\": \"completed\""),
                        "\"end\": null",
                        "\"end\": \"2026-08-31T10:45:30.500Z\""),
                "\"durationMillis\": null",
                "\"durationMillis\": 1830500");
    }

    /**
     * Replaces one hand-typed fragment of a document with another, proving the fragment was there.
     *
     * @param document the document to alter
     * @param original the fragment that must be present
     * @param replacement what to put in its place
     * @return the altered document
     */
    private static String exceptFor(String document, String original, String replacement) {
        assertTrue(
                document.contains(original),
                "the fixture does not contain the fragment this test replaces: " + original);
        return document.replace(original, replacement);
    }

    /**
     * Parses a document that must be refused, and returns the refusal.
     *
     * @param document the document
     * @return the exception the reader threw
     */
    private static InvalidManifestException refused(String document) {
        return assertThrows(InvalidManifestException.class, () -> ManifestReader.parse(document));
    }

    /**
     * Builds an absolute POSIX path, adding the leading separator here rather than in a literal.
     *
     * <p>The argument has no leading slash on purpose: SpotBugs at effort Max reports a string
     * constant that looks like an absolute pathname as {@code DMI_HARDCODED_ABSOLUTE_FILENAME}, and
     * this is the narrow fix {@code ManifestWriterTest} already uses.
     *
     * @param posixPath a path in POSIX form, without its leading separator
     * @return the absolute path
     */
    private static Path absolute(String posixPath) {
        return Path.of("/" + posixPath);
    }

    @Nested
    @DisplayName("A finished run")
    @DisabledOnOs(
            value = OS.WINDOWS,
            disabledReason =
                    "the hand-typed document carries POSIX paths, which a Windows JVM reads as"
                            + " relative and the model rejects")
    class FinishedRun {

        @Test
        @DisplayName("comes back with exactly this run record")
        void comesBackWithExactlyThisRunRecord() {
            RunRecord run = ManifestReader.parse(completedRunDocument()).run();

            assertAll(
                    () -> assertEquals(new RunId("run-20260831-091500"), run.runId()),
                    () -> assertEquals("run-20260831-091500", run.runId().value()),
                    () -> assertEquals("project-alpha", run.projectId()),
                    () -> assertEquals(ProvenanceStatus.PARTIAL, run.status()),
                    () -> assertEquals(Instant.parse("2026-08-31T09:14:00.250Z"), run.start()),
                    () ->
                            assertEquals(
                                    Optional.of(Instant.parse("2026-08-31T09:48:00Z")), run.end()),
                    () -> assertEquals(Optional.of(Duration.ofMillis(2039750)), run.duration()));
        }

        @Test
        @DisplayName("comes back with exactly this schema version and application record")
        void comesBackWithExactlyThisApplicationRecord() {
            ProvenanceManifest manifest = ManifestReader.parse(completedRunDocument());
            ApplicationRecord application = manifest.application();

            assertAll(
                    () -> assertEquals(1, manifest.schemaVersion()),
                    () -> assertEquals("0.1.0-SNAPSHOT", application.cometGuiVersion()),
                    () -> assertEquals("9f8c1d2e4b7a", application.buildIdentifier()),
                    () -> assertEquals("Linux", application.osName()),
                    () -> assertEquals("6.8.0-137-generic", application.osVersion()),
                    () -> assertEquals("amd64", application.architecture()),
                    () -> assertEquals("25.0.4.1", application.jvmVersion()),
                    () -> assertEquals(Locale.forLanguageTag("en-US"), application.locale()),
                    () -> assertEquals("en-US", application.locale().toLanguageTag()),
                    () -> assertEquals(Locale.forLanguageTag("de-DE"), application.formatLocale()),
                    () -> assertEquals("de-DE", application.formatLocale().toLanguageTag()),
                    () -> assertEquals(ZoneId.of("Europe/Berlin"), application.zoneId()));
        }

        @Test
        @DisplayName("comes back with exactly these three settings, sorted by key")
        void comesBackWithExactlyTheseSettings() {
            Map<String, String> settings = ManifestReader.parse(completedRunDocument()).settings();

            assertAll(
                    () -> assertEquals(3, settings.size()),
                    () -> assertEquals("8", settings.get("comet.num-threads")),
                    () ->
                            assertEquals(
                                    "https://ll-user:[REDACTED]@ll.example.org/up",
                                    settings.get("limelight.upload-url")),
                    () -> assertEquals("9001", settings.get("percolator.seed")),
                    () ->
                            assertEquals(
                                    List.of(
                                            "comet.num-threads",
                                            "limelight.upload-url",
                                            "percolator.seed"),
                                    List.copyOf(settings.keySet())));
        }

        @Test
        @DisplayName("comes back with exactly this managed tool")
        void comesBackWithExactlyThisManagedTool() {
            ToolRecord comet = ManifestReader.parse(completedRunDocument()).tools().get(0);

            assertAll(
                    () -> assertEquals("comet", comet.name()),
                    () -> assertEquals("2026.02.2", comet.version()),
                    () -> assertEquals(Optional.of("v2026.02.2"), comet.releaseTag()),
                    () ->
                            assertEquals(
                                    absolute("opt/cometgui/tools/comet-2026.02.2/comet"),
                                    comet.executablePath()),
                    () -> assertEquals(ABC_MD5, comet.hashes().md5()),
                    () -> assertEquals(ABC_SHA256, comet.hashes().sha256()),
                    () -> assertTrue(comet.managed()),
                    () ->
                            assertEquals(
                                    Optional.of("comet-2026.02.2-linux-x86_64.tar.gz"),
                                    comet.artefactIdentity()),
                    () -> assertEquals(Set.of("mzml", "mzxml"), comet.capabilities()),
                    () -> assertEquals(Optional.of("search"), comet.stageId()),
                    () -> assertEquals(List.of(), comet.warnings()));
        }

        @Test
        @DisplayName("comes back with exactly this execution of the managed tool")
        void comesBackWithExactlyThisExecution() {
            ExecutionRecord execution =
                    ManifestReader.parse(completedRunDocument()).tools().get(0).execution();

            assertAll(
                    () ->
                            assertEquals(
                                    List.of(
                                            "/opt/cometgui/tools/comet-2026.02.2/comet",
                                            "-P",
                                            "comet.params",
                                            "--password",
                                            "[REDACTED]"),
                                    execution.command().argv()),
                    () ->
                            assertEquals(
                                    absolute("var/cometgui/runs/run-20260831-091500"),
                                    execution.command().workingDirectory()),
                    () ->
                            assertEquals(
                                    Map.of(
                                            "COMET_PARAMS",
                                            "comet.params",
                                            "LIMELIGHT_API_KEY",
                                            "[REDACTED]",
                                            "PATH",
                                            "/usr/bin:/bin"),
                                    execution.command().environment()),
                    () -> assertEquals(Instant.parse("2026-08-31T09:15:00Z"), execution.start()),
                    () -> assertEquals(Instant.parse("2026-08-31T09:47:30.500Z"), execution.end()),
                    () -> assertEquals(Duration.ofMillis(1950500), execution.duration()),
                    () -> assertEquals(0, execution.exitCode()),
                    () -> assertEquals(ProvenanceStatus.COMPLETED, execution.status()),
                    () ->
                            assertEquals(
                                    absolute(
                                            "var/cometgui/runs/run-20260831-091500"
                                                    + "/comet.stdout.log"),
                                    execution.stdout().orElseThrow().path()),
                    () ->
                            assertEquals(
                                    new FileHashes(ABC_MD5, ABC_SHA256),
                                    execution.stdout().orElseThrow().hashes()),
                    () -> assertEquals(Optional.empty(), execution.stderr()));
        }

        @Test
        @DisplayName("comes back with every optional of the unmanaged tool absent")
        void comesBackWithEveryOptionalOfTheUnmanagedToolAbsent() {
            ToolRecord percolator = ManifestReader.parse(completedRunDocument()).tools().get(1);

            assertAll(
                    () -> assertEquals("percolator", percolator.name()),
                    () -> assertEquals("3.07.1", percolator.version()),
                    () -> assertEquals(Optional.empty(), percolator.releaseTag()),
                    () ->
                            assertEquals(
                                    absolute("usr/local/bin/percolator"),
                                    percolator.executablePath()),
                    () -> assertEquals(EMPTY_MD5, percolator.hashes().md5()),
                    () -> assertEquals(EMPTY_SHA256, percolator.hashes().sha256()),
                    () -> assertFalse(percolator.managed()),
                    () -> assertEquals(Optional.empty(), percolator.artefactIdentity()),
                    () -> assertEquals(Set.of(), percolator.capabilities()),
                    () -> assertEquals(Optional.empty(), percolator.stageId()),
                    () -> assertEquals(Map.of(), percolator.execution().command().environment()),
                    () -> assertEquals(1, percolator.execution().exitCode()),
                    () -> assertEquals(ProvenanceStatus.FAILED, percolator.execution().status()),
                    () -> assertEquals(Optional.empty(), percolator.execution().stdout()),
                    () ->
                            assertEquals(
                                    absolute(
                                            "var/cometgui/runs/run-20260831-091500"
                                                    + "/percolator.stderr.log"),
                                    percolator.execution().stderr().orElseThrow().path()));
        }

        @Test
        @DisplayName("comes back with the tool warnings in order, Unicode and emoji intact")
        void comesBackWithTheWarningsInOrder() {
            ToolRecord percolator = ManifestReader.parse(completedRunDocument()).tools().get(1);

            assertEquals(
                    List.of(
                            "this build has no xml capability",
                            "skipped 2 spectra in /data/protéomique/HeLa_1µg_rep1.mzML",
                            "wrote /data/🧬-run/résultats.txt"),
                    percolator.warnings());
        }

        @Test
        @DisplayName("comes back with exactly these two files, sizes and all")
        void comesBackWithExactlyTheseTwoFiles() {
            List<FileRecord> files = ManifestReader.parse(completedRunDocument()).files();

            assertAll(
                    () -> assertEquals(2, files.size()),
                    () -> assertEquals(FileDirection.INPUT, files.get(0).direction()),
                    () -> assertEquals("spectra", files.get(0).role()),
                    () ->
                            assertEquals(
                                    absolute("data/proteomics/HeLa_1ug_rep1.mzML"),
                                    files.get(0).path()),
                    () -> assertEquals(1234567890123L, files.get(0).sizeBytes()),
                    () ->
                            assertEquals(
                                    Instant.parse("2026-08-30T18:00:00.001Z"),
                                    files.get(0).modifiedAt()),
                    () -> assertEquals(new FileHashes(ABC_MD5, ABC_SHA256), files.get(0).hashes()),
                    () -> assertEquals(ProvenanceStatus.COMPLETED, files.get(0).status()),
                    () -> assertEquals(FileDirection.OUTPUT, files.get(1).direction()),
                    () -> assertEquals("spectrum-export", files.get(1).role()),
                    () ->
                            assertEquals(
                                    absolute("data/exports/\"quoted\"\\name.txt"),
                                    files.get(1).path()),
                    () -> assertEquals(0L, files.get(1).sizeBytes()),
                    () ->
                            assertEquals(
                                    Instant.parse("2026-08-31T09:49:02Z"),
                                    files.get(1).modifiedAt()),
                    () ->
                            assertEquals(
                                    new FileHashes(EMPTY_MD5, EMPTY_SHA256), files.get(1).hashes()),
                    () -> assertEquals(ProvenanceStatus.PARTIAL, files.get(1).status()));
        }
    }

    @Nested
    @DisplayName("A run still in progress")
    class RunningRun {

        @Test
        @DisplayName("comes back with no end, no duration and empty collections")
        void comesBackWithNoEndAndEmptyCollections() {
            ProvenanceManifest manifest = ManifestReader.parse(runningRunDocument());

            assertAll(
                    () -> assertEquals(1, manifest.schemaVersion()),
                    () -> assertEquals(new RunId("run-20260831-101500"), manifest.run().runId()),
                    () -> assertEquals("project-beta", manifest.run().projectId()),
                    () -> assertEquals(ProvenanceStatus.RUNNING, manifest.run().status()),
                    () ->
                            assertEquals(
                                    Instant.parse("2026-08-31T10:15:00Z"), manifest.run().start()),
                    () -> assertEquals(Optional.empty(), manifest.run().end()),
                    () -> assertEquals(Optional.empty(), manifest.run().duration()),
                    () -> assertEquals(Map.of(), manifest.settings()),
                    () -> assertEquals(List.of(), manifest.tools()),
                    () -> assertEquals(List.of(), manifest.files()));
        }

        @Test
        @DisplayName("reads the und language tag back as the root locale, not as a missing one")
        void readsTheUndTagBackAsTheRootLocale() {
            ApplicationRecord application =
                    ManifestReader.parse(runningRunDocument()).application();

            assertAll(
                    () -> assertEquals(Locale.ROOT, application.locale()),
                    () -> assertEquals("und", application.locale().toLanguageTag()),
                    () -> assertEquals(Locale.forLanguageTag("tr-TR"), application.formatLocale()),
                    () -> assertEquals("Windows 11", application.osName()),
                    () -> assertEquals(ZoneId.of("UTC"), application.zoneId()));
        }
    }

    @Nested
    @DisplayName("The schema version")
    class SchemaVersion {

        @Test
        @DisplayName("is refused, saying so plainly, when the document declares a higher one")
        void refusesAHigherVersion() {
            String document =
                    exceptFor(runningRunDocument(), "\"schemaVersion\": 1", "\"schemaVersion\": 2");

            assertEquals(
                    "this provenance manifest declares schema version 2, and this build of CometGUI"
                            + " reads version 1; it was written by a newer CometGUI, which may have"
                            + " changed what a field means rather than only added one, so it is"
                            + " refused rather than half-understood",
                    refused(document).getMessage());
        }

        @Test
        @DisplayName("is refused, naming the missing migration, when it declares a lower one")
        void refusesALowerVersion() {
            String document =
                    exceptFor(runningRunDocument(), "\"schemaVersion\": 1", "\"schemaVersion\": 0");

            assertEquals(
                    "this provenance manifest declares schema version 0, and this build of CometGUI"
                            + " reads version 1; an older document must be migrated explicitly, and"
                            + " no migration to version 1 is registered",
                    refused(document).getMessage());
        }

        @Test
        @DisplayName("is decided before anything else in the document is interpreted")
        void isDecidedBeforeAnythingElse() {
            String document =
                    exceptFor(
                            exceptFor(
                                    runningRunDocument(),
                                    "\"schemaVersion\": 1",
                                    "\"schemaVersion\": 7"),
                            "\"status\": \"running\"",
                            "\"status\": \"not-a-status\"");

            assertTrue(
                    refused(document).getMessage().startsWith("this provenance manifest declares"),
                    "the reader interpreted the document before deciding it could read it");
        }

        @Test
        @DisplayName("is refused when it is not a whole number, and when it is absent")
        void refusesANonNumericOrAbsentVersion() {
            String text =
                    exceptFor(
                            runningRunDocument(),
                            "\"schemaVersion\": 1",
                            "\"schemaVersion\": \"1\"");
            String absent = exceptFor(runningRunDocument(), "  \"schemaVersion\": 1,\n", "");
            String tooLarge =
                    exceptFor(
                            runningRunDocument(),
                            "\"schemaVersion\": 1",
                            "\"schemaVersion\": 2147483648");

            assertAll(
                    () ->
                            assertEquals(
                                    "the provenance manifest is not valid: \"schemaVersion\" must"
                                            + " be a whole number",
                                    refused(text).getMessage()),
                    () ->
                            assertEquals(
                                    "the provenance manifest is not valid: the document has no"
                                            + " member \"schemaVersion\"",
                                    refused(absent).getMessage()),
                    () ->
                            assertEquals(
                                    "the provenance manifest is not valid: \"schemaVersion\" must"
                                            + " fit in a signed 32-bit integer",
                                    refused(tooLarge).getMessage()));
        }
    }

    @Nested
    @DisplayName("A member that is missing, null or the wrong kind")
    class Members {

        @Test
        @DisplayName("is refused when a key the schema requires is absent")
        void refusesAnAbsentMember() {
            String document =
                    exceptFor(runningRunDocument(), "    \"projectId\": \"project-beta\",\n", "");

            assertEquals(
                    "the provenance manifest is not valid: \"run\" has no member \"projectId\"",
                    refused(document).getMessage());
        }

        @Test
        @DisplayName("is refused when an optional's key is absent, which is not the same as null")
        void refusesAnAbsentOptionalKey() {
            String document = exceptFor(runningRunDocument(), "    \"end\": null,\n", "");

            assertEquals(
                    "the provenance manifest is not valid: \"run\" has no member \"end\"",
                    refused(document).getMessage());
        }

        @Test
        @DisplayName("is refused when a member is null where the model has no optional")
        void refusesANullWhereThereIsNoOptional() {
            String document =
                    exceptFor(runningRunDocument(), "\"status\": \"running\"", "\"status\": null");

            assertEquals(
                    "the provenance manifest is not valid: \"run.status\" must be a string",
                    refused(document).getMessage());
        }

        @Test
        @DisplayName("is refused when a member is of the wrong JSON kind, naming its path")
        void refusesTheWrongKind() {
            String numericStart =
                    exceptFor(
                            runningRunDocument(),
                            "\"start\": \"2026-08-31T10:15:00.000Z\"",
                            "\"start\": 1756636500000");
            String objectTools = exceptFor(runningRunDocument(), "\"tools\": []", "\"tools\": {}");
            String arraySettings =
                    exceptFor(runningRunDocument(), "\"settings\": {}", "\"settings\": []");

            assertAll(
                    () ->
                            assertEquals(
                                    "the provenance manifest is not valid: \"run.start\" must be a"
                                            + " string",
                                    refused(numericStart).getMessage()),
                    () ->
                            assertEquals(
                                    "the provenance manifest is not valid: \"tools\" must be a JSON"
                                            + " array",
                                    refused(objectTools).getMessage()),
                    () ->
                            assertEquals(
                                    "the provenance manifest is not valid: \"settings\" must be a"
                                            + " JSON object",
                                    refused(arraySettings).getMessage()),
                    () ->
                            assertEquals(
                                    "the provenance manifest is not valid: the document must be a"
                                            + " JSON object",
                                    refused("[]").getMessage()));
        }

        @Test
        @DisplayName("is ignored when the document carries a member this build does not know")
        void ignoresAnUnknownMember() {
            String document =
                    exceptFor(
                            runningRunDocument(),
                            "\"projectId\": \"project-beta\",",
                            "\"projectId\": \"project-beta\",\n    \"weatherOnTheDay\": \"rain\",");

            assertEquals("project-beta", ManifestReader.parse(document).run().projectId());
        }

        @Test
        @DisplayName("is refused when a wire name is not one the enum defines, listing what is")
        void refusesAnUnknownWireName() {
            String badStatus =
                    exceptFor(
                            runningRunDocument(),
                            "\"status\": \"running\"",
                            "\"status\": \"Running\"");
            String badDirection =
                    exceptFor(
                            completedRunDocument(),
                            "\"direction\": \"input\"",
                            "\"direction\": \"in\"");

            assertAll(
                    () ->
                            assertEquals(
                                    "the provenance manifest is not valid: \"run.status\" must be"
                                            + " one of [running, completed, partial, failed,"
                                            + " cancelled]",
                                    refused(badStatus).getMessage()),
                    () ->
                            assertEquals(
                                    "the provenance manifest is not valid: \"files[0].direction\""
                                            + " must be one of [input, output]",
                                    refused(badDirection).getMessage()));
        }
    }

    @Nested
    @DisplayName("A timestamp, locale or zone")
    class Conversions {

        /** The rejection every timestamp of the wrong shape earns. */
        private static final String NOT_A_TIMESTAMP =
                "the provenance manifest is not valid: \"run.start\" must be a UTC timestamp of the"
                        + " form uuuu-MM-dd'T'HH:mm:ss.SSS'Z'";

        /** The rejection every language tag that is not canonical earns. */
        private static final String NOT_A_LANGUAGE_TAG =
                "the provenance manifest is not valid: \"application.formatLocale\" must be a BCP"
                        + " 47 language tag in the form Locale.toLanguageTag() writes";

        /**
         * The path-free document with a different run start.
         *
         * @param timestamp the text to put in the {@code start} member
         * @return the document
         */
        private static String withStart(String timestamp) {
            return exceptFor(
                    runningRunDocument(),
                    "\"start\": \"2026-08-31T10:15:00.000Z\"",
                    "\"start\": \"" + timestamp + "\"");
        }

        /**
         * The path-free document with a different format locale.
         *
         * @param tag the text to put in the {@code formatLocale} member
         * @return the document
         */
        private static String withFormatLocale(String tag) {
            return exceptFor(
                    runningRunDocument(),
                    "\"formatLocale\": \"tr-TR\"",
                    "\"formatLocale\": \"" + tag + "\"");
        }

        @Test
        @DisplayName("is refused when a timestamp is not the fixed-width UTC form")
        void refusesATimestampOfAnotherShape() {
            String withoutMilliseconds = withStart("2026-08-31T10:15:00Z");
            String impossibleDate = withStart("2026-02-30T10:15:00.000Z");
            String fourFractionDigits = withStart("2026-08-31T10:15:00.0009Z");
            String lowerCaseZone = withStart("2026-08-31T10:15:00.000z");

            assertAll(
                    () -> assertEquals(NOT_A_TIMESTAMP, refused(withoutMilliseconds).getMessage()),
                    () -> assertEquals(NOT_A_TIMESTAMP, refused(impossibleDate).getMessage()),
                    () -> assertEquals(NOT_A_TIMESTAMP, refused(fourFractionDigits).getMessage()),
                    () -> assertEquals(NOT_A_TIMESTAMP, refused(lowerCaseZone).getMessage()));
        }

        @Test
        @DisplayName("is refused when a language tag is not the canonical form, including its case")
        void refusesANonCanonicalLanguageTag() {
            String wrongCase = withFormatLocale("tr-tr");
            String notATag = withFormatLocale("not a tag");

            assertAll(
                    () -> assertEquals(NOT_A_LANGUAGE_TAG, refused(wrongCase).getMessage()),
                    () -> assertEquals(NOT_A_LANGUAGE_TAG, refused(notATag).getMessage()));
        }

        @Test
        @DisplayName("is refused when a zone id is not one this JVM knows")
        void refusesAnUnknownZone() {
            String document =
                    exceptFor(
                            runningRunDocument(),
                            "\"zoneId\": \"UTC\"",
                            "\"zoneId\": \"Mars/Olympus\"");

            assertEquals(
                    "the provenance manifest is not valid: \"application.zoneId\" was rejected by"
                            + " the manifest model (ZoneRulesException); the model's own message is"
                            + " not repeated here, because it quotes the value it rejected",
                    refused(document).getMessage());
        }
    }

    @Nested
    @DisplayName("A value the model refuses")
    class ModelInvariants {

        @Test
        @DisplayName("is refused on read exactly as it is on construction: a malformed digest")
        void refusesAMalformedDigest() {
            String document =
                    exceptFor(
                            completedRunDocument(),
                            "\"md5\": \"900150983cd24fb0d6963f7d28e17f72\"",
                            "\"md5\": \"not-a-digest\"");

            assertEquals(
                    "the provenance manifest is not valid: \"tools[0].md5\" was rejected by the"
                            + " manifest model (IllegalArgumentException); the model's own message"
                            + " is not repeated here, because it quotes the value it rejected",
                    refused(document).getMessage());
        }

        @Test
        @DisplayName("is refused on read exactly as it is on construction: a negative size")
        void refusesANegativeSize() {
            String document =
                    exceptFor(
                            completedRunDocument(),
                            "\"sizeBytes\": 1234567890123",
                            "\"sizeBytes\": -1");

            assertEquals(
                    "the provenance manifest is not valid: \"files[0]\" was rejected by the"
                            + " manifest model (IllegalArgumentException); the model's own message"
                            + " is not repeated here, because it quotes the value it rejected",
                    refused(document).getMessage());
        }

        @Test
        @DisplayName("is refused on read exactly as it is on construction: an end before a start")
        void refusesAnEndBeforeAStart() {
            String document =
                    exceptFor(
                            exceptFor(
                                    finishedButEmptyDocument(),
                                    "\"end\": \"2026-08-31T10:45:30.500Z\"",
                                    "\"end\": \"2026-08-31T09:45:30.500Z\""),
                            "\"durationMillis\": 1830500",
                            "\"durationMillis\": -1769500");

            assertEquals(
                    "the provenance manifest is not valid: \"run\" was rejected by the manifest"
                            + " model (IllegalArgumentException); the model's own message is not"
                            + " repeated here, because it quotes the value it rejected",
                    refused(document).getMessage());
        }

        @Test
        @DisplayName("is refused on read exactly as it is on construction: a relative path")
        void refusesARelativePath() {
            String document =
                    exceptFor(
                            completedRunDocument(),
                            "\"path\": \"/data/proteomics/HeLa_1ug_rep1.mzML\"",
                            "\"path\": \"data/proteomics/HeLa_1ug_rep1.mzML\"");

            assertEquals(
                    "the provenance manifest is not valid: \"files[0]\" was rejected by the"
                            + " manifest model (IllegalArgumentException); the model's own message"
                            + " is not repeated here, because it quotes the value it rejected",
                    refused(document).getMessage());
        }

        @Test
        @DisplayName("is refused on read exactly as it is on construction: a blank role")
        void refusesABlankRole() {
            String document =
                    exceptFor(completedRunDocument(), "\"role\": \"spectra\"", "\"role\": \"   \"");

            assertEquals(
                    "the provenance manifest is not valid: \"files[0]\" was rejected by the"
                            + " manifest model (IllegalArgumentException); the model's own message"
                            + " is not repeated here, because it quotes the value it rejected",
                    refused(document).getMessage());
        }

        @Test
        @DisplayName(
                "is refused on read exactly as it is on construction: a settings key that is"
                        + " not dotted lower-case")
        void refusesAMalformedSettingsKey() {
            String document =
                    exceptFor(
                            completedRunDocument(),
                            "\"percolator.seed\": \"9001\"",
                            "\"PERCOLATOR_SEED\": \"9001\"");

            assertEquals(
                    "the provenance manifest is not valid: the document was rejected by the"
                            + " manifest model (IllegalArgumentException); the model's own message"
                            + " is not repeated here, because it quotes the value it rejected",
                    refused(document).getMessage());
        }

        @Test
        @DisplayName("is refused when an exit code will not fit in the int the model holds")
        void refusesAnOversizedExitCode() {
            String document =
                    exceptFor(
                            completedRunDocument(), "\"exitCode\": 0", "\"exitCode\": 2147483648");

            assertEquals(
                    "the provenance manifest is not valid: \"tools[0].execution.exitCode\" must fit"
                            + " in a signed 32-bit integer",
                    refused(document).getMessage());
        }

        @Test
        @DisplayName("is refused when an exit code is below the int the model holds")
        void refusesAnUndersizedExitCode() {
            String document =
                    exceptFor(
                            completedRunDocument(), "\"exitCode\": 0", "\"exitCode\": -2147483649");

            assertEquals(
                    "the provenance manifest is not valid: \"tools[0].execution.exitCode\" must fit"
                            + " in a signed 32-bit integer",
                    refused(document).getMessage());
        }

        @Test
        @DisplayName("is refused when a flag is not true or false")
        void refusesANonBooleanFlag() {
            String document =
                    exceptFor(completedRunDocument(), "\"managed\": true", "\"managed\": \"yes\"");

            assertEquals(
                    "the provenance manifest is not valid: \"tools[0].managed\" must be true or"
                            + " false",
                    refused(document).getMessage());
        }

        @Test
        @DisplayName("accepts an exit code at each extreme of the int the model holds")
        void acceptsAnExitCodeAtEachExtreme() {
            String lowest =
                    exceptFor(
                            completedRunDocument(), "\"exitCode\": 0", "\"exitCode\": -2147483648");
            String highest =
                    exceptFor(
                            completedRunDocument(), "\"exitCode\": 0", "\"exitCode\": 2147483647");

            assertAll(
                    () ->
                            assertEquals(
                                    Integer.MIN_VALUE,
                                    ManifestReader.parse(lowest)
                                            .tools()
                                            .get(0)
                                            .execution()
                                            .exitCode()),
                    () ->
                            assertEquals(
                                    Integer.MAX_VALUE,
                                    ManifestReader.parse(highest)
                                            .tools()
                                            .get(0)
                                            .execution()
                                            .exitCode()));
        }
    }

    @Nested
    @DisplayName("The recorded duration")
    class RecordedDuration {

        @Test
        @DisplayName("is checked against the run's own instants, and refused when it disagrees")
        void refusesARunDurationThatDisagrees() {
            String document =
                    exceptFor(
                            finishedButEmptyDocument(),
                            "\"durationMillis\": 1830500",
                            "\"durationMillis\": 1830499");

            assertEquals(
                    "the provenance manifest is not valid: \"run.durationMillis\" is 1830499, but"
                            + " the start and end recorded beside it are 1830500 milliseconds"
                            + " apart",
                    refused(document).getMessage());
        }

        @Test
        @DisplayName("is checked against each execution's own instants too")
        void refusesAnExecutionDurationThatDisagrees() {
            String document =
                    exceptFor(
                            completedRunDocument(),
                            "\"durationMillis\": 1950500",
                            "\"durationMillis\": 1950");

            assertEquals(
                    "the provenance manifest is not valid:"
                            + " \"tools[0].execution.durationMillis\" is 1950, but the start and"
                            + " end recorded beside it are 1950500 milliseconds apart",
                    refused(document).getMessage());
        }

        @Test
        @DisplayName("must be null while the run has no end")
        void refusesADurationOnARunWithNoEnd() {
            String document =
                    exceptFor(
                            runningRunDocument(),
                            "\"durationMillis\": null",
                            "\"durationMillis\": 0");

            assertEquals(
                    "the provenance manifest is not valid: \"run.durationMillis\" must be null"
                            + " while the end is null, because an interval that has not ended has"
                            + " no duration",
                    refused(document).getMessage());
        }

        @Test
        @DisplayName("must not be null once the run has an end")
        void refusesAMissingDurationOnAFinishedRun() {
            String document =
                    exceptFor(
                            finishedButEmptyDocument(),
                            "\"durationMillis\": 1830500",
                            "\"durationMillis\": null");

            assertEquals(
                    "the provenance manifest is not valid: \"run.durationMillis\" must not be null"
                            + " once the interval has an end",
                    refused(document).getMessage());
        }

        @Test
        @DisplayName("is verified, not stored: the model derives its own from the two instants")
        void isVerifiedNotStored() {
            RunRecord run = ManifestReader.parse(finishedButEmptyDocument()).run();

            assertAll(
                    () -> assertEquals(Optional.of(Duration.ofMillis(1830500)), run.duration()),
                    () ->
                            assertEquals(
                                    Duration.between(run.start(), run.end().orElseThrow()),
                                    run.duration().orElseThrow()));
        }
    }

    @Nested
    @DisplayName("A rejection message")
    class Secrecy {

        @Test
        @DisplayName("never repeats a value out of the document, wherever the damage is")
        void neverRepeatsAValueFromTheDocument() {
            List<String> hostile =
                    List.of(
                            exceptFor(
                                    completedRunDocument(),
                                    "\"md5\": \"900150983cd24fb0d6963f7d28e17f72\"",
                                    "\"md5\": \"" + SECRET + "\""),
                            exceptFor(
                                    runningRunDocument(),
                                    "\"status\": \"running\"",
                                    "\"status\": \"" + SECRET + "\""),
                            exceptFor(
                                    runningRunDocument(),
                                    "\"start\": \"2026-08-31T10:15:00.000Z\"",
                                    "\"start\": \"" + SECRET + "\""),
                            exceptFor(
                                    runningRunDocument(),
                                    "\"formatLocale\": \"tr-TR\"",
                                    "\"formatLocale\": \"" + SECRET + "\""),
                            exceptFor(
                                    runningRunDocument(),
                                    "\"zoneId\": \"UTC\"",
                                    "\"zoneId\": \"" + PASSPHRASE + "\""),
                            exceptFor(
                                    runningRunDocument(),
                                    "\"runId\": \"run-20260831-101500\"",
                                    "\"runId\": \"" + SECRET + "/../..\""),
                            exceptFor(
                                    runningRunDocument(),
                                    "\"settings\": {}",
                                    "\"settings\": {\"NOT_A_KEY\": \"" + SECRET + "\"}"),
                            exceptFor(
                                    runningRunDocument(),
                                    "\"projectId\": \"project-beta\",",
                                    "\"projectId\": \"" + SECRET + "\",,"));

            assertAll(
                    hostile.stream()
                            .map(document -> (Executable) () -> assertNoLeak(document))
                            .toList());
        }

        @Test
        @DisplayName(
                "carries no cause when the model rejected a value, because that message would"
                        + " quote it")
        void carriesNoCauseFromTheModel() {
            String document =
                    exceptFor(
                            runningRunDocument(),
                            "\"zoneId\": \"UTC\"",
                            "\"zoneId\": \"" + PASSPHRASE + "\"");

            assertNull(refused(document).getCause());
        }

        @Test
        @DisplayName("keeps the parse failure as its cause, which is the one that quotes nothing")
        void keepsTheParseFailureAsItsCause() {
            String document =
                    exceptFor(
                            runningRunDocument(),
                            "\"projectId\": \"project-beta\",",
                            "\"projectId\": \"project-beta\",,");

            InvalidManifestException thrown = refused(document);

            assertAll(
                    () ->
                            assertEquals(
                                    "the provenance manifest is not well-formed JSON: an object"
                                            + " member name must be a double-quoted string"
                                            + " (line 5, column 33)",
                                    thrown.getMessage()),
                    () ->
                            assertEquals(
                                    "JsonParseException",
                                    thrown.getCause().getClass().getSimpleName()));
        }

        /**
         * Asserts that neither the message nor any cause of the refusal repeats the planted secret.
         *
         * @param document a document with a secret in a position that must be rejected
         */
        private void assertNoLeak(String document) {
            Throwable thrown = refused(document);
            while (thrown != null) {
                assertFalse(
                        thrown.getMessage().contains(SECRET),
                        "the token leaked into: " + thrown.getMessage());
                assertFalse(
                        thrown.getMessage().contains(PASSPHRASE),
                        "the passphrase leaked into: " + thrown.getMessage());
                thrown = thrown.getCause();
            }
        }
    }

    @Nested
    @DisplayName("Reading from disk")
    class FromDisk {

        @Test
        @DisplayName("reads a document written as UTF-8 at an exact path")
        void readsADocumentAtAnExactPath() throws IOException {
            Path file = tempDir.resolve("kept.json");
            Files.writeString(file, runningRunDocument(), StandardCharsets.UTF_8);

            ProvenanceManifest manifest = ManifestReader.readFrom(file);

            assertAll(
                    () -> assertEquals("project-beta", manifest.run().projectId()),
                    () -> assertEquals(ProvenanceStatus.RUNNING, manifest.run().status()));
        }

        @Test
        @DisplayName("reads the provenance.json of a run directory, by the name the writer uses")
        void readsTheProvenanceJsonOfARunDirectory() throws IOException {
            Files.writeString(
                    tempDir.resolve(ManifestWriter.FILE_NAME),
                    runningRunDocument(),
                    StandardCharsets.UTF_8);

            ProvenanceManifest manifest = ManifestReader.readIn(tempDir);

            assertAll(
                    () -> assertEquals("provenance.json", ManifestWriter.FILE_NAME),
                    () -> assertEquals(new RunId("run-20260831-101500"), manifest.run().runId()));
        }

        @Test
        @DisplayName("keeps non-ASCII text intact through the UTF-8 decode")
        @DisabledOnOs(
                value = OS.WINDOWS,
                disabledReason = "the document carries POSIX paths the model rejects on Windows")
        void keepsNonAsciiIntactThroughTheDecode() throws IOException {
            Path file = tempDir.resolve("unicode.json");
            Files.writeString(file, completedRunDocument(), StandardCharsets.UTF_8);

            List<String> warnings = ManifestReader.readFrom(file).tools().get(1).warnings();

            assertEquals("wrote /data/🧬-run/résultats.txt", warnings.get(2));
        }

        @Test
        @DisplayName("fails rather than substituting a replacement character for bad UTF-8")
        void failsOnBytesThatAreNotUtf8() throws IOException {
            Path file = tempDir.resolve("broken.json");
            byte[] document = runningRunDocument().getBytes(StandardCharsets.UTF_8);
            document[10] = (byte) 0xff;
            Files.write(file, document);

            assertThrows(MalformedInputException.class, () -> ManifestReader.readFrom(file));
        }

        @Test
        @DisplayName("reports a missing file as a missing file, not as an invalid manifest")
        void reportsAMissingFile() {
            Path missing = tempDir.resolve("no-such-run");

            assertAll(
                    () ->
                            assertThrows(
                                    NoSuchFileException.class,
                                    () -> ManifestReader.readFrom(missing)),
                    () ->
                            assertThrows(
                                    NoSuchFileException.class,
                                    () -> ManifestReader.readIn(missing)));
        }
    }

    @Nested
    @DisplayName("The reader itself")
    class TheReaderItself {

        @Test
        @DisplayName("cannot be instantiated, even reflectively")
        void cannotBeInstantiated() throws NoSuchMethodException {
            var constructor = ManifestReader.class.getDeclaredConstructor();
            constructor.setAccessible(true);

            var thrown =
                    assertThrows(
                            java.lang.reflect.InvocationTargetException.class,
                            constructor::newInstance);

            assertAll(
                    () -> assertEquals(AssertionError.class, thrown.getCause().getClass()),
                    () ->
                            assertEquals(
                                    "ManifestReader is a utility class and is never instantiated",
                                    thrown.getCause().getMessage()));
        }
    }
}
