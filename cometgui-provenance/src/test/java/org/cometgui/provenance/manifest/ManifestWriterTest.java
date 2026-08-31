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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.domain.run.RunId;
import org.cometgui.domain.secrets.SecretRedactor;
import org.cometgui.domain.secrets.SecretRegistry;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * The whole of {@code provenance.json}, pinned as a hand-typed document.
 *
 * <p><strong>The two documents below were typed out, not captured.</strong> Neither was produced by
 * running {@link ManifestWriter} and pasting its answer: they were written from the format this
 * phase decided on -- schema version first, records in declaration order, sorted maps, {@code null}
 * for an absent optional, UTC timestamps with three fractional digits, two-space indentation, one
 * trailing newline -- and the fixtures were then built to match them. A round trip through this
 * writer and a reader would prove that the two agree with each other, which is not the thing an
 * on-disk format has to be right about.
 *
 * <p>When one of these assertions fails it prints two whole documents, which is what makes the
 * failure readable: the difference is a line, in context, rather than a field name.
 *
 * <p><strong>The path-bearing document is disabled on Windows and that is a real hole, named rather
 * than hidden.</strong> A manifest records the paths a run actually used, so on Windows the same
 * fixture would produce {@code C:\...} with escaped backslashes and no hand-typed POSIX document
 * could match it. What is <em>not</em> lost there: {@link #aRunStillInProgressIsExactlyThis}
 * carries no path and runs everywhere, and {@code JsonWriterTest} pins the escaping of a backslash
 * independently of any platform. Phase 15 owns the platform matrix and should add the Windows twin
 * of the pinned document rather than relaxing this one.
 *
 * <p><strong>A non-ASCII path cannot be tested on a JVM without a UTF-8 locale, and the reason is
 * worth carrying forward.</strong> {@code sun.jnu.encoding} is the charset the JDK uses to turn a
 * {@link String} into the bytes of a Unix path. With no {@code LANG} in the environment it is
 * {@code ANSI_X3.4-1968}, and {@code Path.of("/data/protéomique/x")} throws {@link
 * InvalidPathException} -- before any of this project's code is reached. So the pinned document
 * carries its non-ASCII text and its emoji in a tool warning, where a {@link String} is a {@link
 * String}, and the byte-level UTF-8 assertion is unconditional; {@link
 * #aNonAsciiPathIsWrittenAsItself} covers the path case and aborts where the JVM cannot represent
 * one. <strong>A CometGUI launcher must set a UTF-8 locale</strong> or a scientist whose data
 * directory has an accent in it cannot run at all, which is a packaging concern for phase 14 rather
 * than a provenance one.
 */
class ManifestWriterTest {

    // ---------------------------------------------------------------------------------------
    // Digest vectors, hand-transcribed from RFC 1321 and NIST, exactly as ManifestFixtures does.
    // No expected value in this file can therefore have come from CometGUI code.
    // ---------------------------------------------------------------------------------------

    /** MD5 and SHA-256 of {@code "abc"}. */
    private static final FileHashes ABC_HASHES =
            new FileHashes(
                    "900150983cd24fb0d6963f7d28e17f72",
                    "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");

    /** MD5 and SHA-256 of the empty file. */
    private static final FileHashes EMPTY_HASHES =
            new FileHashes(
                    "d41d8cd98f00b204e9800998ecf8427e",
                    "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");

    /** The run directory every path in the pinned document sits under. */
    private static final String RUN_DIRECTORY = "var/cometgui/runs/run-20260831-091500";

    // ---------------------------------------------------------------------------------------
    // The seeded secret corpus, hand-transcribed from the work unit's brief and from
    // SeededSecretCorpusTest, character for character.  The last three are the lines of the PEM
    // body: a whole-key check is defeated by a one-character rewrite, so the sweep looks for
    // whole LINES of key material.  See that class's documentation for both blind spots.
    // ---------------------------------------------------------------------------------------

    /** An AWS secret access key. */
    private static final String AWS_SECRET = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY";

    /** A GitHub personal access token. */
    private static final String GITHUB_TOKEN = "ghp_S3cr3tT0k3nExampleValue0123456789ab";

    /** A JSON web token. */
    private static final String JWT =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxIn0"
                    + ".dBjftJeZ4CVPmB92K27uhbUJU1p1r_wW1gFWFOEjXk";

    /** A password passed on a command line. */
    private static final String PASSWORD = "hunter2-not-a-real-password";

    /** The password inside a credential-bearing URL. */
    private static final String URL_PASSWORD = "Tr0ub4dor-26-3";

    /** A Limelight API key. */
    private static final String LIMELIGHT_KEY = "ll_live_9f8e7d6c5b4a39281706";

    /** A private-key passphrase. */
    private static final String PASSPHRASE = "correct-horse-battery-staple";

    /** The twelve-character secret every SHORT carrier is built from. */
    private static final String SWORDFISH = "swordfish-42";

    /** A vendor token with no published prefix worth pattern-matching. */
    private static final String LIVE_TOKEN = "tok_live_abcdef0123456789";

    /** An AWS access key id. */
    private static final String AWS_ACCESS_KEY_ID = "AKIAIOSFODNN7EXAMPLE";

    /** The base64 body of a synthetic PEM private key. */
    private static final String PEM_BODY =
            "MIIBVAIBADANBgkqhkiG9w0BAQEFAASCAT4wggE6AgEAAkEAnotArealKeyExample\n"
                    + "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ+/\n"
                    + "ThisIsNotARealPrivateKeyItIsAFixtureForCometGUIPhase04Tests012==";

    /** The thirteen strings the sweep looks for, in the order the work unit lists them. */
    private static final List<String> CORPUS =
            List.of(
                    "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
                    "ghp_S3cr3tT0k3nExampleValue0123456789ab",
                    "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxIn0"
                            + ".dBjftJeZ4CVPmB92K27uhbUJU1p1r_wW1gFWFOEjXk",
                    "hunter2-not-a-real-password",
                    "Tr0ub4dor-26-3",
                    "ll_live_9f8e7d6c5b4a39281706",
                    "correct-horse-battery-staple",
                    "swordfish-42",
                    "tok_live_abcdef0123456789",
                    "AKIAIOSFODNN7EXAMPLE",
                    "MIIBVAIBADANBgkqhkiG9w0BAQEFAASCAT4wggE6AgEAAkEAnotArealKeyExample",
                    "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ+/",
                    "ThisIsNotARealPrivateKeyItIsAFixtureForCometGUIPhase04Tests012==");

    // ---------------------------------------------------------------------------------------
    // The pinned documents.
    // ---------------------------------------------------------------------------------------

    /**
     * A finished run with two tools and two files: every field of every record type, set.
     *
     * <p>Three redactions are pinned inside it, one per level at which redaction happens, so that
     * removing any one of them fails here and not only in the secrets sweep. The {@code
     * limelight.upload-url} setting is cleared by the writer's own text rules; the argument after
     * {@code --password} is cleared positionally, which no text rule could do; and {@code
     * LIMELIGHT_API_KEY} is cleared by its name, whatever its value looked like.
     *
     * <p>In this Java text block a backslash is typed twice, so {@code \\"} is the two characters a
     * JSON escaped quote consists of and {@code \\\\} is the two an escaped backslash consists of.
     */
    private static final String PINNED_COMPLETED_RUN = pinnedCompletedRun();

    /**
     * The completed-run document, returned from a method rather than assigned as a constant.
     *
     * <p>A {@code static final String} whose initialiser is a constant expression is a <em>constant
     * variable</em>, so javac copies the whole four-kilobyte literal into the class file of every
     * nested test class that mentions it. SpotBugs reports that as {@code
     * HSC_HUGE_SHARED_STRING_CONSTANT}, and it is right: five copies of one expected document is
     * five places for them to stop agreeing. A method call is not a constant expression, so there
     * is exactly one copy.
     *
     * @return the document
     */
    private static String pinnedCompletedRun() {
        return """
            {
              "schemaVersion": 1,
              "run": {
                "runId": "run-20260831-091500",
                "projectId": "project-alpha",
                "status": "partial",
                "start": "2026-08-31T09:14:00.250Z",
                "end": "2026-08-31T09:48:00.000Z"
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
     * A run that has started and not finished: the document a crash leaves behind.
     *
     * <p>{@code R-PROV-05} requires that a crash still leaves useful history, so this shape is not
     * an edge case but the normal state of the file for the whole length of a run. It pins the four
     * things the full document cannot: {@code "end": null} for an absent optional at the top level,
     * an empty object for {@code settings}, empty arrays for {@code tools} and {@code files}, and
     * the {@code und} language tag of {@link Locale#ROOT}. It carries no path, so it is the part of
     * the format that is pinned on every platform.
     */
    private static final String PINNED_RUNNING_RUN = pinnedRunningRun();

    /**
     * The running-run document; see {@link #pinnedCompletedRun()} for why it is a method.
     *
     * @return the document
     */
    private static String pinnedRunningRun() {
        return """
            {
              "schemaVersion": 1,
              "run": {
                "runId": "run-20260831-101500",
                "projectId": "project-beta",
                "status": "running",
                "start": "2026-08-31T10:15:00.000Z",
                "end": null
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

    /** A temporary directory for the tests that put a document on disk. */
    @TempDir private Path tempDir;

    // ---------------------------------------------------------------------------------------
    // Fixtures.
    // ---------------------------------------------------------------------------------------

    /**
     * A writer with the pattern rules only, which is the weakest one the class permits.
     *
     * @return a writer
     */
    private static ManifestWriter writer() {
        return ManifestWriter.redactingWith(SecretRedactor.patternsOnly());
    }

    /**
     * Builds an absolute POSIX path, adding the leading separator here rather than in a literal.
     *
     * <p><strong>The argument has no leading slash on purpose.</strong> SpotBugs at effort Max
     * reports every string constant that looks like an absolute pathname as {@code
     * DMI_HARDCODED_ABSOLUTE_FILENAME} -- a sound warning about production code, and wrong about a
     * fixture whose whole purpose is to be a fixed, recognisable path inside a pinned document. The
     * repository's policy is to fix a finding in the code rather than filter the pattern away, and
     * this is the narrow fix: no constant in this file is an absolute pathname, and the one place
     * that makes one absolute says why.
     *
     * @param posixPath a path in POSIX form, without its leading separator
     * @return the absolute path
     */
    private static Path absolute(String posixPath) {
        return Path.of("/" + posixPath);
    }

    /**
     * The manifest the pinned completed-run document describes.
     *
     * @return the manifest
     */
    private static ProvenanceManifest completedRunFixture() {
        return completedRunFixture(false);
    }

    /**
     * A settings value that is a credential-bearing URL, so the pinned document also pins the
     * redaction the writer's own text rules perform on an ordinary string value.
     *
     * @return the URL, with the password still in it
     */
    private static String credentialUrlSetting() {
        return "https://ll-user:" + URL_PASSWORD + "@ll.example.org/up";
    }

    /**
     * The manifest the pinned completed-run document describes, built in one of two orders.
     *
     * @param reverseInsertion whether to build the settings and the environment back to front
     * @return the manifest
     */
    private static ProvenanceManifest completedRunFixture(boolean reverseInsertion) {
        // Neither insertion order is the sorted one: the sorted output has to be a property of the
        // format, not of the order somebody happened to build the map in.
        Map<String, String> settings = new LinkedHashMap<>();
        if (reverseInsertion) {
            settings.put("comet.num-threads", "8");
            settings.put("limelight.upload-url", credentialUrlSetting());
            settings.put("percolator.seed", "9001");
        } else {
            settings.put("percolator.seed", "9001");
            settings.put("limelight.upload-url", credentialUrlSetting());
            settings.put("comet.num-threads", "8");
        }

        return new ProvenanceManifest(
                1,
                new RunRecord(
                        new RunId("run-20260831-091500"),
                        "project-alpha",
                        ProvenanceStatus.PARTIAL,
                        // Nine tenths of a millisecond past .250, so the document proves the
                        // timestamp is truncated and not rounded.
                        Instant.parse("2026-08-31T09:14:00.250999999Z"),
                        Optional.of(Instant.parse("2026-08-31T09:48:00Z"))),
                new ApplicationRecord(
                        "0.1.0-SNAPSHOT",
                        "9f8c1d2e4b7a",
                        "Linux",
                        "6.8.0-137-generic",
                        "amd64",
                        "25.0.4.1",
                        Locale.forLanguageTag("en-US"),
                        Locale.forLanguageTag("de-DE"),
                        ZoneId.of("Europe/Berlin")),
                settings,
                List.of(cometTool(reverseInsertion), percolatorTool()),
                List.of(spectraInput(), escapedOutput()));
    }

    /**
     * The managed Comet installation of the pinned document.
     *
     * @param reverseInsertion whether to build the environment map back to front
     * @return the tool record
     */
    private static ToolRecord cometTool(boolean reverseInsertion) {
        Map<String, String> environment = new LinkedHashMap<>();
        if (reverseInsertion) {
            environment.put("COMET_PARAMS", "comet.params");
            environment.put("LIMELIGHT_API_KEY", LIMELIGHT_KEY);
            environment.put("PATH", "/usr/bin:/bin");
        } else {
            environment.put("PATH", "/usr/bin:/bin");
            environment.put("LIMELIGHT_API_KEY", LIMELIGHT_KEY);
            environment.put("COMET_PARAMS", "comet.params");
        }

        return new ToolRecord(
                "comet",
                "2026.02.2",
                Optional.of("v2026.02.2"),
                absolute("opt/cometgui/tools/comet-2026.02.2/comet"),
                ABC_HASHES,
                true,
                Optional.of("comet-2026.02.2-linux-x86_64.tar.gz"),
                // Out of order, so that the sorted output is a property and not a coincidence.
                new LinkedHashSet<>(List.of("mzxml", "mzml")),
                Optional.of("search"),
                new ExecutionRecord(
                        new ToolCommand(
                                List.of(
                                        "/opt/cometgui/tools/comet-2026.02.2/comet",
                                        "-P",
                                        "comet.params",
                                        "--password",
                                        SWORDFISH),
                                absolute(RUN_DIRECTORY),
                                environment),
                        Instant.parse("2026-08-31T09:15:00Z"),
                        Instant.parse("2026-08-31T09:47:30.500Z"),
                        0,
                        Optional.of(
                                new LogRecord(
                                        absolute(RUN_DIRECTORY + "/comet.stdout.log"), ABC_HASHES)),
                        Optional.empty(),
                        ProvenanceStatus.COMPLETED),
                List.of());
    }

    /**
     * The unmanaged Percolator of the pinned document: every optional absent, and it failed.
     *
     * @return the tool record
     */
    private static ToolRecord percolatorTool() {
        return new ToolRecord(
                "percolator",
                "3.07.1",
                Optional.empty(),
                absolute("usr/local/bin/percolator"),
                EMPTY_HASHES,
                false,
                Optional.empty(),
                Set.of(),
                Optional.empty(),
                new ExecutionRecord(
                        new ToolCommand(
                                List.of(
                                        "/usr/local/bin/percolator",
                                        "--results-psms",
                                        "percolator.psms.txt"),
                                absolute(RUN_DIRECTORY),
                                Map.of()),
                        Instant.parse("2026-08-31T09:47:31Z"),
                        Instant.parse("2026-08-31T09:49:02.125Z"),
                        1,
                        Optional.empty(),
                        Optional.of(
                                new LogRecord(
                                        absolute(RUN_DIRECTORY + "/percolator.stderr.log"),
                                        EMPTY_HASHES)),
                        ProvenanceStatus.FAILED),
                List.of(
                        "this build has no xml capability",
                        "skipped 2 spectra in /data/protéomique/HeLa_1µg_rep1.mzML",
                        "wrote /data/🧬-run/résultats.txt"));
    }

    /**
     * An input file whose size is beyond the range of an {@code int}.
     *
     * @return the file record
     */
    private static FileRecord spectraInput() {
        return new FileRecord(
                FileDirection.INPUT,
                "spectra",
                absolute("data/proteomics/HeLa_1ug_rep1.mzML"),
                // Above Integer.MAX_VALUE, so an int somewhere in the number path would overflow.
                1234567890123L,
                Instant.parse("2026-08-30T18:00:00.001Z"),
                ABC_HASHES,
                ProvenanceStatus.COMPLETED);
    }

    /**
     * An output file whose path carries a quote and a backslash, both of which JSON escapes.
     *
     * @return the file record
     */
    private static FileRecord escapedOutput() {
        return new FileRecord(
                FileDirection.OUTPUT,
                "spectrum-export",
                absolute("data/exports/\"quoted\"\\name.txt"),
                0L,
                Instant.parse("2026-08-31T09:49:02Z"),
                EMPTY_HASHES,
                ProvenanceStatus.PARTIAL);
    }

    /**
     * The manifest the pinned running-run document describes.
     *
     * @return the manifest
     */
    private static ProvenanceManifest runningRunFixture() {
        return new ProvenanceManifest(
                1,
                new RunRecord(
                        new RunId("run-20260831-101500"),
                        "project-beta",
                        ProvenanceStatus.RUNNING,
                        Instant.parse("2026-08-31T10:15:00Z"),
                        Optional.empty()),
                new ApplicationRecord(
                        "0.1.0-SNAPSHOT",
                        "9f8c1d2e4b7a",
                        "Windows 11",
                        "10.0",
                        "aarch64",
                        "25.0.4.1",
                        Locale.ROOT,
                        Locale.forLanguageTag("tr-TR"),
                        ZoneId.of("UTC")),
                Map.of(),
                List.of(),
                List.of());
    }

    /**
     * A {@code null} of whatever type the call site needs, by a route no analyser folds away.
     *
     * <p>The same idiom, and the same reason, as {@code SecretRedactorTest.deliberateNull}.
     *
     * @param <T> the type the call site needs
     * @return {@code null}
     */
    private static <T> T deliberateNull() {
        return Optional.<T>empty().orElse(null);
    }

    @Nested
    @DisplayName("The document")
    class Document {

        @Test
        @DisplayName("of a finished run is exactly this, character for character")
        @DisabledOnOs(
                value = OS.WINDOWS,
                disabledReason =
                        "the pinned document contains POSIX paths, which a Windows JVM would"
                                + " render with backslashes and reject as relative")
        void aFinishedRunIsExactlyThis() {
            assertEquals(PINNED_COMPLETED_RUN, writer().render(completedRunFixture()));
        }

        @Test
        @DisplayName("of a run still in progress is exactly this, on every platform")
        void aRunStillInProgressIsExactlyThis() {
            assertEquals(PINNED_RUNNING_RUN, writer().render(runningRunFixture()));
        }

        @Test
        @DisplayName("begins with the schema version, before anything a reader would have to parse")
        void beginsWithTheSchemaVersion() {
            // Asserted on the document's own first lines rather than on the pinned literal, so
            // that moving the field still fails even if somebody regenerated the literal too.
            List<String> firstTwoLines =
                    writer().render(runningRunFixture()).lines().limit(2).toList();

            assertEquals(List.of("{", "  \"schemaVersion\": 1,"), firstTwoLines);
        }

        @Test
        @DisplayName("ends with exactly one newline")
        void endsWithExactlyOneNewline() {
            String document = writer().render(runningRunFixture());

            assertAll(
                    () -> assertTrue(document.endsWith("}\n")),
                    () -> assertFalse(document.endsWith("\n\n")));
        }

        @Test
        @DisplayName("uses no carriage return, whatever the platform's line separator is")
        void usesNoCarriageReturn() {
            assertFalse(writer().render(runningRunFixture()).contains("\r"));
        }

        @Test
        @DisplayName("writes a genuinely non-ASCII path as itself, where the JVM can hold one")
        void aNonAsciiPathIsWrittenAsItself() {
            Path path = representableOrAbort("data/protéomique/résultats🧬.mzML");
            ProvenanceManifest manifest = manifestWithOneFile(path);
            // Hand-typed, including the six spaces of indentation and the trailing comma, rather
            // than built from the fixture string: an accented character, a micro-sign-free but
            // multi-byte word and a surrogate pair, all written as themselves.
            String expectedLine = "      \"path\": \"/data/protéomique/résultats🧬.mzML\",\n";

            String document = writer().render(manifest);

            assertAll(
                    () -> assertTrue(document.contains(expectedLine), document),
                    // Nothing in the document was turned into a backslash-u escape: a provenance
                    // record a scientist cannot read against their own disk is a worse record, and
                    // no character here is one JSON requires escaping.
                    () -> assertFalse(document.contains("\\u")));
        }

        @Test
        @DisplayName("writes an absent optional as null and never omits the key")
        @DisabledOnOs(value = OS.WINDOWS, disabledReason = "POSIX paths, as above")
        void writesAnAbsentOptionalAsNull() {
            String document = writer().render(completedRunFixture());

            // Every key exists in every document of a schema version, so a MISSING key means a
            // schema disagreement and a null one means this run had no such value.
            assertAll(
                    () -> assertTrue(document.contains("\"releaseTag\": null")),
                    () -> assertTrue(document.contains("\"artefactIdentity\": null")),
                    () -> assertTrue(document.contains("\"stageId\": null")),
                    () -> assertTrue(document.contains("\"stdout\": null")),
                    () -> assertTrue(document.contains("\"stderr\": null")),
                    () -> assertFalse(document.contains("\"end\": null")));
        }
    }

    @Nested
    @DisplayName("Determinism")
    class Determinism {

        @Test
        @DisplayName("survives a Turkish, a German and a Thai-digit default locale unchanged")
        @DisabledOnOs(value = OS.WINDOWS, disabledReason = "POSIX paths, as above")
        void survivesHostileDefaultLocales() {
            Locale originalDefault = Locale.getDefault();
            Locale originalFormat = Locale.getDefault(Locale.Category.FORMAT);
            try {
                Locale.setDefault(Locale.of("tr", "TR"));
                String turkish = writer().render(completedRunFixture());
                Locale.setDefault(Locale.GERMANY);
                String german = writer().render(completedRunFixture());
                Locale.setDefault(Locale.forLanguageTag("th-TH-u-nu-thai"));
                String thai = writer().render(completedRunFixture());

                assertAll(
                        () -> assertEquals(PINNED_COMPLETED_RUN, turkish),
                        () -> assertEquals(PINNED_COMPLETED_RUN, german),
                        () -> assertEquals(PINNED_COMPLETED_RUN, thai));
            } finally {
                Locale.setDefault(originalDefault);
                Locale.setDefault(Locale.Category.FORMAT, originalFormat);
            }
        }

        @Test
        @DisplayName("survives a default time zone fourteen hours from UTC unchanged")
        @DisabledOnOs(value = OS.WINDOWS, disabledReason = "POSIX paths, as above")
        void survivesAForeignDefaultTimeZone() {
            TimeZone originalZone = TimeZone.getDefault();
            try {
                TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"));

                assertEquals(PINNED_COMPLETED_RUN, writer().render(completedRunFixture()));
            } finally {
                TimeZone.setDefault(originalZone);
            }
        }

        @Test
        @DisplayName("does not depend on the order the environment map was built in")
        @DisabledOnOs(value = OS.WINDOWS, disabledReason = "POSIX paths, as above")
        void doesNotDependOnMapInsertionOrder() {
            // The same manifest built forwards and backwards.  Both must equal the ONE pinned
            // document; asserting only that they equal each other would pass for two identically
            // wrong orders.
            String first = writer().render(completedRunFixture(false));
            String second = writer().render(completedRunFixture(true));

            assertAll(
                    () -> assertEquals(PINNED_COMPLETED_RUN, first),
                    () -> assertEquals(PINNED_COMPLETED_RUN, second));
        }
    }

    @Nested
    @DisplayName("On disk")
    class OnDisk {

        @Test
        @DisplayName("the file holds exactly the rendered document, UTF-8 encoded")
        @DisabledOnOs(value = OS.WINDOWS, disabledReason = "POSIX paths, as above")
        void theFileHoldsExactlyTheRenderedDocument() throws IOException {
            Path target = tempDir.resolve(ManifestWriter.FILE_NAME);

            writer().writeTo(target, completedRunFixture());

            // Both the decoded text and the raw bytes: the byte assertion is what proves the emoji
            // and the accented characters were encoded as UTF-8 rather than as the platform's
            // default charset, which on a Windows host would be windows-1252.
            assertAll(
                    () -> assertEquals(PINNED_COMPLETED_RUN, Files.readString(target, UTF_8)),
                    () ->
                            assertArrayEquals(
                                    PINNED_COMPLETED_RUN.getBytes(UTF_8),
                                    Files.readAllBytes(target)));
        }

        @Test
        @DisplayName("writeInto puts it under the standard name and returns that path")
        void writeIntoUsesTheStandardName() throws IOException {
            Path written = writer().writeInto(tempDir, runningRunFixture());

            assertAll(
                    () -> assertEquals("provenance.json", ManifestWriter.FILE_NAME),
                    () -> assertEquals(tempDir.resolve("provenance.json"), written),
                    () -> assertEquals(PINNED_RUNNING_RUN, Files.readString(written, UTF_8)));
        }

        @Test
        @DisplayName("no temporary file is left beside the manifest")
        void noTemporaryFileIsLeftBehind() throws IOException {
            writer().writeInto(tempDir, runningRunFixture());

            try (Stream<Path> entries = Files.list(tempDir)) {
                assertEquals(
                        List.of("provenance.json"),
                        entries.map(Path::getFileName).map(Path::toString).sorted().toList());
            }
        }

        @Test
        @DisplayName("a rewrite is never observed as a truncated or missing document")
        void aRewriteIsNeverObservedTruncated() throws IOException, InterruptedException {
            ManifestWriter manifestWriter = writer();
            Path target = tempDir.resolve(ManifestWriter.FILE_NAME);
            ProvenanceManifest small = runningRunFixture();
            ProvenanceManifest large = manifestWithManyFiles(150);
            // The two complete documents are the ONLY states a reader may ever observe.  They are
            // the writer's own output because that is what "complete" means here; the assertion is
            // about torn state, and a torn file matches neither.
            String smallText = manifestWriter.render(small);
            String largeText = manifestWriter.render(large);
            manifestWriter.writeTo(target, small);

            AtomicBoolean stop = new AtomicBoolean();
            List<String> observed = Collections.synchronizedList(new ArrayList<>());
            Thread reader =
                    new Thread(
                            () -> {
                                while (!stop.get()) {
                                    try {
                                        String seen = Files.readString(target, UTF_8);
                                        if (!seen.equals(smallText) && !seen.equals(largeText)) {
                                            observed.add(
                                                    "a reader saw a document of "
                                                            + seen.length()
                                                            + " characters, which is neither of"
                                                            + " the two written");
                                        }
                                    } catch (NoSuchFileException vanished) {
                                        observed.add("the target vanished during a rewrite");
                                    } catch (IOException failure) {
                                        observed.add("a read failed: " + failure);
                                    }
                                }
                            },
                            "manifest-reader");
            reader.start();
            try {
                for (int rewrite = 0; rewrite < 24; rewrite++) {
                    manifestWriter.writeTo(target, rewrite % 2 == 0 ? large : small);
                }
            } finally {
                stop.set(true);
                reader.join(30_000L);
            }

            assertAll(
                    () ->
                            assertTrue(
                                    largeText.length() > 8 * smallText.length(),
                                    "the two documents are too alike for a torn read to show"),
                    () -> assertEquals(List.of(), observed),
                    // The last rewrite is number 23, which is odd, so the small one is what
                    // must be on disk when the loop ends.
                    () -> assertEquals(smallText, Files.readString(target, UTF_8)));
        }

        @Test
        @DisplayName("a write into a directory that does not exist fails and creates nothing")
        void aWriteIntoAMissingDirectoryCreatesNothing() throws IOException {
            Path missing = tempDir.resolve("no-such-run");

            assertThrows(
                    NoSuchFileException.class,
                    () -> writer().writeInto(missing, runningRunFixture()));

            try (Stream<Path> entries = Files.list(tempDir)) {
                assertEquals(List.of(), entries.toList());
            }
        }

        @Test
        @DisplayName("a write over a directory fails and leaves the directory intact")
        void aWriteOverADirectoryLeavesItIntact() throws IOException {
            Path target = tempDir.resolve(ManifestWriter.FILE_NAME);
            Files.createDirectory(target);
            Files.writeString(target.resolve("inside.txt"), "kept", UTF_8);

            IOException thrown =
                    assertThrows(
                            IOException.class, () -> writer().writeTo(target, runningRunFixture()));

            assertAll(
                    () ->
                            assertEquals(
                                    "Cannot write " + target + ": it is an existing directory",
                                    thrown.getMessage()),
                    () ->
                            assertEquals(
                                    "kept", Files.readString(target.resolve("inside.txt"), UTF_8)));
        }
    }

    @Nested
    @DisplayName("The seeded secret corpus")
    class Secrets {

        @Test
        @DisplayName("does not survive anywhere in the rendered document or the file on disk")
        void doesNotSurviveAnywhere() throws IOException {
            ManifestWriter loaded =
                    ManifestWriter.redactingWith(
                            SecretRedactor.with(SecretRegistry.copyOf(CORPUS)));
            ProvenanceManifest manifest = manifestCarryingTheCorpus();

            String document = loaded.render(manifest);
            Path target = loaded.writeInto(tempDir, manifest);
            String onDisk = Files.readString(target, UTF_8);

            List<String> leaks = new ArrayList<>();
            for (int secret = 0; secret < CORPUS.size(); secret++) {
                // The secret itself is never named in a failure message, for the reason the
                // redaction package exists.
                if (document.contains(CORPUS.get(secret))) {
                    leaks.add(
                            "corpus secret #"
                                    + secret
                                    + " (length "
                                    + CORPUS.get(secret).length()
                                    + ") survived into the rendered document");
                }
                if (onDisk.contains(CORPUS.get(secret))) {
                    leaks.add(
                            "corpus secret #"
                                    + secret
                                    + " (length "
                                    + CORPUS.get(secret).length()
                                    + ") survived into provenance.json");
                }
            }

            assertAll(
                    () -> assertEquals(List.of(), leaks),
                    () ->
                            assertTrue(
                                    document.contains("[REDACTED]"),
                                    "no marker at all, so nothing was redacted"),
                    () -> assertEquals(document, onDisk));
        }

        @Test
        @DisplayName("is carried in short values too, where a size-conditioned leak would hide")
        void isCarriedInShortValuesToo() {
            // Blind spot (2) of SeededSecretCorpusTest: a redactor -- or a writer -- that took a
            // fast path on small inputs leaks in clear and is invisible to a corpus whose carriers
            // are all long.  These are the short ones this manifest actually carries.
            List<String> shortCarriers =
                    List.of(
                            SWORDFISH,
                            "auth=" + SWORDFISH,
                            "pw=" + SWORDFISH,
                            "ftp://u:" + SWORDFISH + "@h/",
                            AWS_ACCESS_KEY_ID);

            for (String carrier : shortCarriers) {
                assertTrue(
                        carrier.length() < 32,
                        "the carrier \""
                                + carrier.replace(SWORDFISH, "...")
                                + "\" has grown to "
                                + carrier.length()
                                + " characters; see blind spot (2) in SeededSecretCorpusTest");
            }
            assertEquals(12, SWORDFISH.length());
        }

        @Test
        @DisplayName("leaves the run's ordinary scientific text intact")
        void leavesOrdinaryTextIntact() {
            // The other half of the gate.  A writer that emitted an empty document, or a rule set
            // that destroyed every string, would pass the sweep above and be useless.
            ManifestWriter loaded =
                    ManifestWriter.redactingWith(
                            SecretRedactor.with(SecretRegistry.copyOf(CORPUS)));

            String document = loaded.render(manifestCarryingTheCorpus());

            assertAll(
                    () -> assertTrue(document.contains("\"/data/HeLa_1ug_rep1.mzML\"")),
                    () -> assertTrue(document.contains("\"COMET_PARAMS\": \"/data/comet.params\"")),
                    () -> assertTrue(document.contains("\"name\": \"limelight-upload\"")),
                    () -> assertTrue(document.contains("\"role\": \"spectra\"")));
        }
    }

    @Nested
    @DisplayName("Null arguments and description")
    class Contract {

        @Test
        @DisplayName("a writer cannot be built without a redactor")
        void aWriterCannotBeBuiltWithoutARedactor() {
            SecretRedactor nullRedactor = deliberateNull();

            assertEquals(
                    "redactor",
                    assertThrows(
                                    NullPointerException.class,
                                    () -> ManifestWriter.redactingWith(nullRedactor))
                            .getMessage());
        }

        @Test
        @DisplayName("every entry point rejects a null argument by parameter name")
        void everyEntryPointRejectsNull() {
            ProvenanceManifest nullManifest = deliberateNull();
            Path nullPath = deliberateNull();

            assertAll(
                    () ->
                            assertEquals(
                                    "manifest",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () -> writer().render(nullManifest))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "target",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () ->
                                                            writer().writeTo(
                                                                            nullPath,
                                                                            runningRunFixture()))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "manifest",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () ->
                                                            writer().writeTo(
                                                                            tempDir.resolve(
                                                                                    "x.json"),
                                                                            nullManifest))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "runDirectory",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () ->
                                                            writer().writeInto(
                                                                            nullPath,
                                                                            runningRunFixture()))
                                            .getMessage()));
        }

        @Test
        @DisplayName("toString names the rule set and no secret")
        void toStringNamesTheRuleSetAndNoSecret() {
            ManifestWriter loaded =
                    ManifestWriter.redactingWith(SecretRedactor.with(SecretRegistry.of(SWORDFISH)));

            String described = loaded.toString();

            assertAll(
                    () ->
                            assertEquals(
                                    "ManifestWriter[SecretRedactor[SecretRegistry[secretCount=1]]]",
                                    described),
                    () -> assertFalse(described.contains(SWORDFISH)));
        }
    }

    // ---------------------------------------------------------------------------------------
    // Fixtures that are not part of a pinned document.
    // ---------------------------------------------------------------------------------------

    /**
     * A manifest whose every free-text carrier holds a seeded secret.
     *
     * <p>Long carriers and short ones, and one carrier per rule family: an environment variable
     * cleared by its name, an argument cleared by its position, a credential URL, a bearer header,
     * a known token shape, a PEM block, and four carriers under 32 characters that only the
     * registry can clear.
     *
     * @return the manifest
     */
    private static ProvenanceManifest manifestCarryingTheCorpus() {
        Map<String, String> settings = new LinkedHashMap<>();
        settings.put(
                "limelight.upload-url",
                "https://limelight-user:" + URL_PASSWORD + "@limelight.example.org/api/upload");
        settings.put("limelight.api-token", GITHUB_TOKEN);
        settings.put("percolator.seed", SWORDFISH);
        settings.put("comet.note", "pw=" + SWORDFISH);
        settings.put("aws.key", AWS_ACCESS_KEY_ID);

        Map<String, String> environment = new LinkedHashMap<>();
        environment.put("AWS_SECRET_ACCESS_KEY", AWS_SECRET);
        environment.put("GITHUB_TOKEN", GITHUB_TOKEN);
        environment.put("LIMELIGHT_API_KEY", LIMELIGHT_KEY);
        environment.put("PERCOLATOR_PASSWORD", PASSPHRASE);
        environment.put("COMET_PARAMS", "/data/comet.params");
        environment.put("NOTE", SWORDFISH);

        ToolRecord upload =
                new ToolRecord(
                        "limelight-upload",
                        "1.4.0",
                        Optional.of(SWORDFISH),
                        absolute("opt/limelight/bin/upload"),
                        ABC_HASHES,
                        true,
                        Optional.of(JWT),
                        Set.of("upload"),
                        Optional.of("limelight"),
                        new ExecutionRecord(
                                new ToolCommand(
                                        List.of(
                                                "/opt/limelight/bin/upload",
                                                "--password",
                                                PASSWORD,
                                                "--password=" + PASSWORD,
                                                "--api-key",
                                                AWS_ACCESS_KEY_ID,
                                                "-k",
                                                AWS_SECRET,
                                                "-k",
                                                SWORDFISH,
                                                "--input",
                                                "/data/HeLa_1ug_rep1.mzML"),
                                        absolute("var/cometgui/runs/secrets"),
                                        environment),
                                Instant.parse("2026-08-31T09:15:00Z"),
                                Instant.parse("2026-08-31T09:15:01Z"),
                                0,
                                Optional.of(
                                        new LogRecord(
                                                absolute("var/log/upload-" + SWORDFISH + ".log"),
                                                ABC_HASHES)),
                                Optional.of(
                                        new LogRecord(
                                                absolute("var/log/" + LIVE_TOKEN + ".log"),
                                                EMPTY_HASHES)),
                                ProvenanceStatus.COMPLETED),
                        List.of(
                                "-----BEGIN RSA PRIVATE KEY-----\n"
                                        + PEM_BODY
                                        + "\n-----END RSA PRIVATE KEY-----",
                                "Authorization: Bearer " + JWT,
                                "connecting with password: " + PASSPHRASE,
                                "response body {\"token\":\"" + LIVE_TOKEN + "\"}",
                                "auth=" + SWORDFISH,
                                "ftp://u:" + SWORDFISH + "@h/"));

        return new ProvenanceManifest(
                1,
                new RunRecord(
                        new RunId("run-secret-carriers"),
                        SWORDFISH,
                        ProvenanceStatus.COMPLETED,
                        Instant.parse("2026-08-31T09:14:00Z"),
                        Optional.of(Instant.parse("2026-08-31T09:15:02Z"))),
                new ApplicationRecord(
                        "0.1.0-SNAPSHOT",
                        "auth=" + SWORDFISH,
                        "Linux",
                        "6.8.0-137-generic",
                        "amd64",
                        "25.0.4.1",
                        Locale.forLanguageTag("en-US"),
                        Locale.forLanguageTag("en-US"),
                        ZoneId.of("UTC")),
                settings,
                List.of(upload),
                List.of(
                        new FileRecord(
                                FileDirection.INPUT,
                                "spectra",
                                absolute("data/" + LIMELIGHT_KEY + "/HeLa_1ug_rep1.mzML"),
                                4096L,
                                Instant.parse("2026-08-30T18:00:00Z"),
                                ABC_HASHES,
                                ProvenanceStatus.COMPLETED)));
    }

    /**
     * Builds a path, or aborts the calling test when this JVM cannot represent it.
     *
     * <p>Not a skip for convenience: {@code Path.of} throws {@link InvalidPathException} for a
     * non-ASCII name whenever {@code sun.jnu.encoding} is not a Unicode charset, which is what a
     * process with no {@code LANG} gets. The abort message names the encoding so that a skipped run
     * is diagnosable rather than mysterious.
     *
     * @param posixPath the path to build, without its leading separator
     * @return the path, if this JVM can hold it
     */
    private static Path representableOrAbort(String posixPath) {
        try {
            return absolute(posixPath);
        } catch (InvalidPathException notRepresentable) {
            return Assumptions.abort(
                    "this JVM's sun.jnu.encoding is "
                            + System.getProperty("sun.jnu.encoding")
                            + ", which cannot represent a non-ASCII path; re-run under a UTF-8"
                            + " locale (LANG=C.UTF-8) to exercise this test");
        }
    }

    /**
     * A running-run manifest carrying exactly one output file.
     *
     * @param path the file's path
     * @return the manifest
     */
    private static ProvenanceManifest manifestWithOneFile(Path path) {
        ProvenanceManifest running = runningRunFixture();
        return new ProvenanceManifest(
                running.schemaVersion(),
                running.run(),
                running.application(),
                running.settings(),
                running.tools(),
                List.of(
                        new FileRecord(
                                FileDirection.OUTPUT,
                                "spectra",
                                path,
                                1L,
                                Instant.parse("2026-08-31T10:16:00Z"),
                                ABC_HASHES,
                                ProvenanceStatus.COMPLETED)));
    }

    /**
     * A manifest with enough file records to make a torn write visible.
     *
     * @param files how many file records to include
     * @return the manifest
     */
    private ProvenanceManifest manifestWithManyFiles(int files) {
        List<FileRecord> records = new ArrayList<>(files);
        for (int index = 0; index < files; index++) {
            records.add(
                    new FileRecord(
                            FileDirection.OUTPUT,
                            "fraction-" + index,
                            tempDir.resolve("fraction-" + index + ".mzML"),
                            index,
                            Instant.parse("2026-08-31T09:14:00Z"),
                            ABC_HASHES,
                            ProvenanceStatus.COMPLETED));
        }
        ProvenanceManifest running = runningRunFixture();
        return new ProvenanceManifest(
                running.schemaVersion(),
                running.run(),
                running.application(),
                running.settings(),
                running.tools(),
                records);
    }
}
