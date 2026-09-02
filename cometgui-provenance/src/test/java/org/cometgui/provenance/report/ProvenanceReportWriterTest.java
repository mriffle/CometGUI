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

package org.cometgui.provenance.report;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.domain.run.RunId;
import org.cometgui.domain.secrets.SecretRedactor;
import org.cometgui.domain.secrets.SecretRegistry;
import org.cometgui.provenance.manifest.ApplicationRecord;
import org.cometgui.provenance.manifest.ExecutionRecord;
import org.cometgui.provenance.manifest.FileDirection;
import org.cometgui.provenance.manifest.FileRecord;
import org.cometgui.provenance.manifest.LogRecord;
import org.cometgui.provenance.manifest.ProvenanceManifest;
import org.cometgui.provenance.manifest.ProvenanceStatus;
import org.cometgui.provenance.manifest.RunRecord;
import org.cometgui.provenance.manifest.ToolRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * The whole of {@code provenance.rst}, pinned as a hand-typed document.
 *
 * <p><strong>The two documents below were typed out, not captured.</strong> Neither was produced by
 * running {@link ProvenanceReportWriter} and pasting its answer: they were written from the layout
 * this work unit decided on -- title, preamble, summary, application, settings, one numbered
 * subsection per tool, one table of files; underlines generated from their titles; values as inline
 * literals, escaped where an inline literal cannot carry them; {@code (none)} for an absent value;
 * UTC timestamps with three fractional digits -- and the fixtures were then built to match them.
 *
 * <p><strong>Why a pinned document is the primary assertion here.</strong> A report generated from
 * a model can go wrong in two ways that a "does it contain the value" check cannot see: it can stop
 * reporting a field, and it can report one in a shape reStructuredText reads as something else. The
 * whole-document comparison sees both, and it prints two readable documents when it fails.
 *
 * <p><strong>{@link Structure#everyComponentOfTheModelIsReported} is the assertion that keeps this
 * true as the schema grows.</strong> It enumerates the record components of {@link
 * ProvenanceManifest} and of every record reachable from it, reflectively, and checks them against
 * a hand-typed map from component to the exact line that reports it. A component added to the model
 * fails it until the report and the map have both been updated, and a component dropped from the
 * report fails it too. That is what "generated from the same machine-readable model, never
 * maintained independently" has to mean in a test.
 *
 * <p><strong>The path-bearing documents are disabled on Windows and that is a real hole, named
 * rather than hidden</strong> -- exactly as in {@code ManifestWriterTest}, and for the same reason:
 * a manifest records the paths a run actually used, so on Windows the same fixture would produce
 * {@code C:\...} and no hand-typed POSIX document could match it. {@link
 * Document#aRunStillInProgressIsExactlyThis} carries no path and runs everywhere. Phase 15 owns the
 * platform matrix and should add the Windows twin rather than relaxing these.
 */
class ProvenanceReportWriterTest {

    // ---------------------------------------------------------------------------------------
    // Digest vectors, hand-transcribed from RFC 1321 and NIST, exactly as ManifestFixtures does.
    // No expected value in this file can therefore have come from CometGUI code.
    // ---------------------------------------------------------------------------------------

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

    /** The published {@code "abc"} digests as a pair. */
    private static final FileHashes ABC_HASHES = new FileHashes(ABC_MD5, ABC_SHA256);

    /** The published empty-file digests as a pair. */
    private static final FileHashes EMPTY_HASHES = new FileHashes(EMPTY_MD5, EMPTY_SHA256);

    /** The run directory every path in the pinned document sits under, without its separator. */
    private static final String RUN_DIRECTORY = "var/cometgui/runs/run-20260831-091500";

    // ---------------------------------------------------------------------------------------
    // The seeded secret corpus, hand-transcribed from SeededSecretCorpusTest, character for
    // character.  The last three are the LINES of the PEM body: a whole-key check is defeated by
    // a one-character rewrite, so the sweep looks for whole lines of key material.  See that
    // class's documentation for both blind spots, including why short carriers are mandatory.
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
     * <p>It also carries, on purpose, every value shape the escaping rule has to deal with -- an
     * empty settings value, one with a leading space, one with a line feed, one with backticks and
     * an asterisk, a path with a quotation mark and a backslash in it, accented text and an emoji
     * -- because this is the document the phase orchestrator runs {@code sphinx-build -n -W} over.
     * A sample that only contained well-behaved values would prove nothing about the gate.
     *
     * <p>Three redactions are pinned inside it, one per level at which redaction happens, so that
     * removing any one of them fails here and not only in the secrets sweep. The {@code
     * limelight.upload-url} setting is cleared by the writer's own text rules; the argument after
     * {@code --password} is cleared positionally, which no text rule could do; and {@code
     * LIMELIGHT_API_KEY} is cleared by its name, whatever its value looked like.
     *
     * <p>In this Java text block a backslash is typed twice, so {@code \\u0060} below is the six
     * characters of a Unicode escape and {@code \\name.txt} is a single backslash in a path.
     */
    private static final String PINNED_FULL_REPORT = pinnedFullReport();

    /**
     * The fully populated document, returned from a method rather than assigned as a constant.
     *
     * <p>A {@code static final String} whose initialiser is a constant expression is a <em>constant
     * variable</em>, so javac copies the whole literal into the class file of every nested test
     * class that mentions it. SpotBugs reports that as {@code HSC_HUGE_SHARED_STRING_CONSTANT}, and
     * it is right: several copies of one expected document is several places for them to stop
     * agreeing. A method call is not a constant expression, so there is exactly one copy.
     *
     * @return the document
     */
    private static String pinnedFullReport() {
        return """
            ==========================
            CometGUI provenance record
            ==========================

            Generated from this run's provenance manifest -- the same model that produces
            ``provenance.json``, and never maintained independently of it. Every value below
            is the manifest's; a fact missing here is missing from the manifest.

            Values are shown as inline literals. A value that reStructuredText cannot carry
            in one -- an empty value, or one holding a backtick, a control character or an
            edge of whitespace -- is shown instead as a double-quoted string with backslash
            escapes, so that ``\\u0060`` is a backtick and ``\\n`` is a line feed;
            ``provenance.json`` carries the exact characters either way. A field with no
            value at all reads (none).

            Summary
            =======

            :Schema version: ``1``
            :Run ID: ``run-20260831-091500``
            :Project: ``project-alpha``
            :Status: ``partial``
            :Started: ``2026-08-31T09:14:00.250Z``
            :Ended: ``2026-08-31T09:48:00.000Z``
            :Duration (ms): ``2039750``
            :Inputs: ``1``
            :Outputs: ``1``

            .. list-table::
               :header-rows: 1
               :widths: 50 50

               * - Tool
                 - Version
               * - ``comet``
                 - ``2026.02.2``
               * - ``percolator``
                 - ``3.07.1``

            Application and environment
            ===========================

            :CometGUI version: ``0.1.0-SNAPSHOT``
            :Build identifier: ``9f8c1d2e4b7a``
            :Operating system: ``Linux``
            :Operating system version: ``6.8.0-137-generic``
            :Architecture: ``amd64``
            :Java runtime: ``25.0.4.1``
            :Default locale: ``en-US``
            :Format locale: ``de-DE``
            :Time zone: ``Europe/Berlin``

            Settings
            ========

            .. list-table::
               :header-rows: 1
               :widths: 40 60

               * - Setting
                 - Value
               * - ``comet.num-threads``
                 - ``8``
               * - ``limelight.upload-url``
                 - ``https://ll-user:[REDACTED]@ll.example.org/up``
               * - ``percolator.seed``
                 - ``9001``
               * - ``report.blank-value``
                 - ``""``
               * - ``report.escaped-value``
                 - ``"wrote \\u0060weights\\u0060 to 5 * 3 files"``
               * - ``report.leading-space``
                 - ``" indented"``
               * - ``report.multi-line``
                 - ``"first line\\nsecond line"``

            Tools
            =====

            Tool 1
            ------

            :Name: ``comet``
            :Version: ``2026.02.2``
            :Release tag: ``v2026.02.2``
            :Origin: ``managed``
            :Artefact: ``comet-2026.02.2-linux-x86_64.tar.gz``
            :Stage: ``search``
            :Executable: ``/opt/cometgui/tools/comet-2026.02.2/comet``
            :MD5: ``900150983cd24fb0d6963f7d28e17f72``
            :SHA-256: ``ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad``
            :Capabilities: ``mzml``, ``mzxml``
            :Working directory: ``/var/cometgui/runs/run-20260831-091500``
            :Started: ``2026-08-31T09:15:00.000Z``
            :Ended: ``2026-08-31T09:47:30.500Z``
            :Duration (ms): ``1950500``
            :Exit code: ``0``
            :Execution status: ``completed``
            :Stdout log: ``/var/cometgui/runs/run-20260831-091500/comet.stdout.log``
            :Stdout MD5: ``900150983cd24fb0d6963f7d28e17f72``
            :Stdout SHA-256: ``ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad``
            :Stderr log: (none)
            :Stderr MD5: (none)
            :Stderr SHA-256: (none)
            :Command:
               * ``/opt/cometgui/tools/comet-2026.02.2/comet``
               * ``-P``
               * ``comet.params``
               * ``--password``
               * ``[REDACTED]``
            :Environment:
               * ``COMET_PARAMS`` = ``comet.params``
               * ``LIMELIGHT_API_KEY`` = ``[REDACTED]``
               * ``PATH`` = ``/usr/bin:/bin``
            :Warnings: (none)

            Tool 2
            ------

            :Name: ``percolator``
            :Version: ``3.07.1``
            :Release tag: (none)
            :Origin: ``local``
            :Artefact: (none)
            :Stage: (none)
            :Executable: ``/usr/local/bin/percolator``
            :MD5: ``d41d8cd98f00b204e9800998ecf8427e``
            :SHA-256: ``e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855``
            :Capabilities: (none)
            :Working directory: ``/var/cometgui/runs/run-20260831-091500``
            :Started: ``2026-08-31T09:47:31.000Z``
            :Ended: ``2026-08-31T09:49:02.125Z``
            :Duration (ms): ``91125``
            :Exit code: ``1``
            :Execution status: ``failed``
            :Stdout log: (none)
            :Stdout MD5: (none)
            :Stdout SHA-256: (none)
            :Stderr log: ``/var/cometgui/runs/run-20260831-091500/percolator.stderr.log``
            :Stderr MD5: ``d41d8cd98f00b204e9800998ecf8427e``
            :Stderr SHA-256: ``e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855``
            :Command:
               * ``/usr/local/bin/percolator``
               * ``--results-psms``
               * ``percolator.psms.txt``
            :Environment: (none)
            :Warnings:
               * ``this build has no xml capability``
               * ``skipped 2 * 3 spectra in /data/protéomique/HeLa_1µg_rep1.mzML``
               * ``wrote /data/🧬-run/résultats.txt``

            Inputs and outputs
            ==================

            .. list-table::
               :header-rows: 1
               :widths: 8 12 26 10 14 14 16 10

               * - Direction
                 - Role
                 - Path
                 - Size (bytes)
                 - Modified
                 - MD5
                 - SHA-256
                 - Status
               * - ``input``
                 - ``spectra``
                 - ``/data/proteomics/HeLa_1ug_rep1.mzML``
                 - ``1234567890123``
                 - ``2026-08-30T18:00:00.001Z``
                 - ``900150983cd24fb0d6963f7d28e17f72``
                 - ``ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad``
                 - ``completed``
               * - ``output``
                 - ``spectrum-export``
                 - ``/data/exports/"quoted"\\name.txt``
                 - ``0``
                 - ``2026-08-31T09:49:02.000Z``
                 - ``d41d8cd98f00b204e9800998ecf8427e``
                 - ``e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855``
                 - ``partial``
            """;
    }

    /**
     * A run that has started and not finished: the report a crash leaves behind.
     *
     * <p>{@code R-PROV-05} requires that a crash still leaves useful history, so this shape is not
     * an edge case but the normal state of the file for the whole length of a run. It pins the four
     * things the full document cannot: {@code (none)} for an absent end, and the prose each of the
     * three empty collections is written as instead of an empty table. It carries no path, so it is
     * the part of the format that is pinned on every platform.
     */
    private static final String PINNED_RUNNING_REPORT = pinnedRunningReport();

    /**
     * The running-run document; see {@link #pinnedFullReport()} for why it is a method.
     *
     * @return the document
     */
    private static String pinnedRunningReport() {
        return """
            ==========================
            CometGUI provenance record
            ==========================

            Generated from this run's provenance manifest -- the same model that produces
            ``provenance.json``, and never maintained independently of it. Every value below
            is the manifest's; a fact missing here is missing from the manifest.

            Values are shown as inline literals. A value that reStructuredText cannot carry
            in one -- an empty value, or one holding a backtick, a control character or an
            edge of whitespace -- is shown instead as a double-quoted string with backslash
            escapes, so that ``\\u0060`` is a backtick and ``\\n`` is a line feed;
            ``provenance.json`` carries the exact characters either way. A field with no
            value at all reads (none).

            Summary
            =======

            :Schema version: ``1``
            :Run ID: ``run-20260831-101500``
            :Project: ``project-beta``
            :Status: ``running``
            :Started: ``2026-08-31T10:15:00.000Z``
            :Ended: (none)
            :Duration (ms): (none)
            :Inputs: ``0``
            :Outputs: ``0``

            No tool version is recorded for this run.

            Application and environment
            ===========================

            :CometGUI version: ``0.1.0-SNAPSHOT``
            :Build identifier: ``9f8c1d2e4b7a``
            :Operating system: ``Windows 11``
            :Operating system version: ``10.0``
            :Architecture: ``aarch64``
            :Java runtime: ``25.0.4.1``
            :Default locale: ``und``
            :Format locale: ``tr-TR``
            :Time zone: ``UTC``

            Settings
            ========

            No scientific or export setting is recorded for this run.

            Tools
            =====

            No tool invocation is recorded for this run.

            Inputs and outputs
            ==================

            No input or output file is recorded for this run.
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
    private static ProvenanceReportWriter writer() {
        return ProvenanceReportWriter.redactingWith(SecretRedactor.patternsOnly());
    }

    /**
     * Builds an absolute POSIX path, adding the leading separator here rather than in a literal.
     *
     * <p><strong>The argument has no leading slash on purpose.</strong> SpotBugs at effort Max
     * reports every string constant that looks like an absolute pathname as {@code
     * DMI_HARDCODED_ABSOLUTE_FILENAME} -- sound about production code, and wrong about a fixture
     * whose whole purpose is to be a fixed, recognisable path inside a pinned document. The
     * repository's policy is to fix a finding in the code rather than filter the pattern away, and
     * this is the narrow fix.
     *
     * @param posixPath a path in POSIX form, without its leading separator
     * @return the absolute path
     */
    private static Path absolute(String posixPath) {
        return Path.of("/" + posixPath);
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
     * The manifest the pinned full document describes.
     *
     * @return the manifest
     */
    private static ProvenanceManifest fullFixture() {
        return fullFixture(false);
    }

    /**
     * The manifest the pinned full document describes, built in one of two insertion orders.
     *
     * @param reverseInsertion whether to build the settings and the environment back to front
     * @return the manifest
     */
    private static ProvenanceManifest fullFixture(boolean reverseInsertion) {
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
                settings(reverseInsertion),
                List.of(cometTool(reverseInsertion), percolatorTool()),
                List.of(spectraInput(), escapedOutput()));
    }

    /**
     * The run's settings, built forwards or backwards.
     *
     * <p>Neither insertion order is the sorted one: the sorted output has to be a property of the
     * format, not of the order somebody happened to build the map in. The last four exist to carry
     * the value shapes an inline literal cannot hold.
     *
     * @param reverseInsertion whether to insert them back to front
     * @return the settings
     */
    private static Map<String, String> settings(boolean reverseInsertion) {
        List<Map.Entry<String, String>> entries =
                new ArrayList<>(
                        List.of(
                                Map.entry("percolator.seed", "9001"),
                                Map.entry("report.multi-line", "first line\nsecond line"),
                                Map.entry("report.leading-space", " indented"),
                                Map.entry("report.escaped-value", "wrote `weights` to 5 * 3 files"),
                                Map.entry("limelight.upload-url", credentialUrlSetting()),
                                Map.entry("comet.num-threads", "8")));
        if (reverseInsertion) {
            java.util.Collections.reverse(entries);
        }
        Map<String, String> settings = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : entries) {
            settings.put(entry.getKey(), entry.getValue());
        }
        // Map.entry rejects an empty value no more than a full one, but List.of(...) above is
        // easier to read without it; the empty settings value goes in separately.
        settings.put("report.blank-value", "");
        return settings;
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
                        "skipped 2 * 3 spectra in /data/protéomique/HeLa_1µg_rep1.mzML",
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
     * An output file whose path carries a quotation mark and a backslash, neither of which an
     * inline literal needs escaped -- the pinned document is where that is proved.
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
    private static ProvenanceManifest runningFixture() {
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
     * A manifest with two inputs and one output, so that the two counts differ.
     *
     * @return the manifest
     */
    private static ProvenanceManifest manifestWithTwoInputsAndOneOutput() {
        ProvenanceManifest running = runningFixture();
        return new ProvenanceManifest(
                running.schemaVersion(),
                running.run(),
                running.application(),
                running.settings(),
                running.tools(),
                List.of(
                        spectraInput(),
                        new FileRecord(
                                FileDirection.INPUT,
                                "fasta",
                                absolute("data/proteomics/human.fasta"),
                                512L,
                                Instant.parse("2026-08-30T18:00:00Z"),
                                ABC_HASHES,
                                ProvenanceStatus.COMPLETED),
                        escapedOutput()));
    }

    /**
     * The running-run manifest with one setting replaced, for the escaping assertions.
     *
     * @param value the settings value to carry
     * @return a manifest whose only setting is {@code report.value}
     */
    private static ProvenanceManifest manifestWithSetting(String value) {
        ProvenanceManifest running = runningFixture();
        return new ProvenanceManifest(
                running.schemaVersion(),
                running.run(),
                running.application(),
                Map.of("report.value", value),
                running.tools(),
                running.files());
    }

    /**
     * Renders a report whose only setting is the given value.
     *
     * @param value the settings value to carry
     * @return the whole document
     */
    private static String renderedWithSetting(String value) {
        return writer().render(manifestWithSetting(value));
    }

    /**
     * A {@code null} of whatever type the call site needs, by a route no analyser folds away.
     *
     * <p>The same idiom, and the same reason, as {@code ManifestWriterTest.deliberateNull}: an
     * instance method that calls {@code Objects.requireNonNull} plus a literal {@code null} at the
     * call site is {@code NP_NULL_PARAM_DEREF_ALL_TARGETS_DANGEROUS}, which is not excluded.
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
        @DisplayName("of a fully populated run is exactly this, character for character")
        @DisabledOnOs(
                value = OS.WINDOWS,
                disabledReason =
                        "the pinned document contains POSIX paths, which a Windows JVM would"
                                + " render with backslashes and reject as relative")
        void aFullyPopulatedRunIsExactlyThis() {
            assertEquals(PINNED_FULL_REPORT, writer().render(fullFixture()));
        }

        @Test
        @DisplayName("of a run still in progress is exactly this, on every platform")
        void aRunStillInProgressIsExactlyThis() {
            assertEquals(PINNED_RUNNING_REPORT, writer().render(runningFixture()));
        }

        @Test
        @DisplayName("ends with exactly one newline")
        void endsWithExactlyOneNewline() {
            String document = writer().render(runningFixture());

            assertAll(
                    () -> assertTrue(document.endsWith("run.\n"), document),
                    () -> assertFalse(document.endsWith("\n\n")));
        }

        @Test
        @DisplayName("uses no carriage return, whatever the platform's line separator is")
        void usesNoCarriageReturn() {
            assertFalse(writer().render(runningFixture()).contains("\r"));
        }

        @Test
        @DisplayName("underlines every heading with a rule exactly as long as the heading")
        @DisabledOnOs(value = OS.WINDOWS, disabledReason = "POSIX paths, as above")
        void underlinesEveryHeadingExactly() {
            // The invariant docutils checks, checked here on every heading of both documents so
            // that it holds for headings the pinned literals do not happen to contain.  A rule one
            // character short is "WARNING: Title underline too short", an error under -W.
            List<String> mismatches = new ArrayList<>();
            for (ProvenanceManifest manifest : List.of(fullFixture(), runningFixture())) {
                List<String> lines = writer().render(manifest).lines().toList();
                for (int line = 1; line < lines.size(); line++) {
                    String rule = lines.get(line);
                    String heading = lines.get(line - 1);
                    if (isRule(rule) && !heading.isEmpty() && !isRule(heading)) {
                        collectMismatch(mismatches, heading, rule);
                    }
                }
            }

            assertEquals(List.of(), mismatches);
        }

        @Test
        @DisplayName("counts inputs and outputs separately, not the files twice")
        void countsInputsAndOutputsSeparately() {
            // The pinned document has one file on each side, so it cannot tell a count of inputs
            // from a count of outputs.  This one can: two and one, hand-typed.
            String document = writer().render(manifestWithTwoInputsAndOneOutput());

            assertAll(
                    () -> assertTrue(document.contains("\n:Inputs: ``2``\n"), document),
                    () -> assertTrue(document.contains("\n:Outputs: ``1``\n"), document));
        }

        @Test
        @DisplayName("reports a duration a reader can recompute from the timestamps beside it")
        void reportsARecomputableDuration() {
            // Computed by hand from the two timestamps the document itself prints, not from the
            // fixture's raw instants: 09:14:00.250Z to 09:48:00.000Z is 33 minutes 59.75 seconds,
            // which is (33 * 60 + 59) * 1000 + 750 = 2 039 750 milliseconds.  The fixture starts
            // at .250999999Z, so a duration taken from the untruncated instants would be 2 039 749
            // and would contradict the document it appears in.
            String document = writer().render(fullFixture());

            assertAll(
                    () -> assertEquals(2039750L, (33L * 60L + 59L) * 1000L + 750L),
                    () -> assertTrue(document.contains("\n:Duration (ms): ``2039750``\n")),
                    // 32 minutes 30.5 seconds, and 1 minute 31.125 seconds.
                    () -> assertEquals(1950500L, (32L * 60L + 30L) * 1000L + 500L),
                    () -> assertTrue(document.contains("\n:Duration (ms): ``1950500``\n")),
                    () -> assertEquals(91125L, 91L * 1000L + 125L),
                    () -> assertTrue(document.contains("\n:Duration (ms): ``91125``\n")));
        }

        @Test
        @DisplayName("puts the title between an overline and an underline of 26 equals signs")
        void putsTheTitleBetweenARuleOfTwentySixEqualsSigns() {
            // Hand-counted: "CometGUI provenance record" is 26 characters.
            List<String> firstThreeLines =
                    writer().render(runningFixture()).lines().limit(3).toList();

            assertEquals(
                    List.of(
                            "==========================",
                            "CometGUI provenance record",
                            "=========================="),
                    firstThreeLines);
        }
    }

    @Nested
    @DisplayName("Structure")
    class Structure {

        /**
         * Every record component of the model, and the exact line of the report that carries it.
         *
         * <p><strong>Hand-typed, both halves.</strong> The keys are not built by asking the report
         * what it rendered, and the lines are not built by asking the writer what it wrote: they
         * are read off the pinned document above. A component added to {@link ProvenanceManifest}
         * or to any record reachable from it appears in {@link #modelComponents()} and is missing
         * here, which fails; a component that stops being reported keeps its entry here and its
         * line disappears from the document, which also fails.
         *
         * @return the map, in no particular order
         */
        private Map<String, String> reportedAt() {
            Map<String, String> reported = new LinkedHashMap<>();
            reported.put("ProvenanceManifest.schemaVersion", "\n:Schema version: ``1``\n");
            reported.put("ProvenanceManifest.run", "\n:Project: ``project-alpha``\n");
            reported.put(
                    "ProvenanceManifest.application", "\n:CometGUI version: ``0.1.0-SNAPSHOT``\n");
            reported.put("ProvenanceManifest.settings", "\n   * - ``report.multi-line``\n");
            reported.put("ProvenanceManifest.tools", "\nTool 2\n------\n");
            reported.put("ProvenanceManifest.files", "\n     - ``spectrum-export``\n");
            reported.put("RunRecord.runId", "\n:Run ID: ``run-20260831-091500``\n");
            reported.put("RunRecord.projectId", "\n:Project: ``project-alpha``\n");
            reported.put("RunRecord.status", "\n:Status: ``partial``\n");
            reported.put("RunRecord.start", "\n:Started: ``2026-08-31T09:14:00.250Z``\n");
            reported.put("RunRecord.end", "\n:Ended: ``2026-08-31T09:48:00.000Z``\n");
            reported.put("RunId.value", "\n:Run ID: ``run-20260831-091500``\n");
            reported.put(
                    "ApplicationRecord.cometGuiVersion",
                    "\n:CometGUI version: ``0.1.0-SNAPSHOT``\n");
            reported.put(
                    "ApplicationRecord.buildIdentifier", "\n:Build identifier: ``9f8c1d2e4b7a``\n");
            reported.put("ApplicationRecord.osName", "\n:Operating system: ``Linux``\n");
            reported.put(
                    "ApplicationRecord.osVersion",
                    "\n:Operating system version: ``6.8.0-137-generic``\n");
            reported.put("ApplicationRecord.architecture", "\n:Architecture: ``amd64``\n");
            reported.put("ApplicationRecord.jvmVersion", "\n:Java runtime: ``25.0.4.1``\n");
            reported.put("ApplicationRecord.locale", "\n:Default locale: ``en-US``\n");
            reported.put("ApplicationRecord.formatLocale", "\n:Format locale: ``de-DE``\n");
            reported.put("ApplicationRecord.zoneId", "\n:Time zone: ``Europe/Berlin``\n");
            reported.put("ToolRecord.name", "\n:Name: ``percolator``\n");
            reported.put("ToolRecord.version", "\n:Version: ``3.07.1``\n");
            reported.put("ToolRecord.releaseTag", "\n:Release tag: ``v2026.02.2``\n");
            reported.put(
                    "ToolRecord.executablePath", "\n:Executable: ``/usr/local/bin/percolator``\n");
            reported.put("ToolRecord.hashes", "\n:MD5: ``" + ABC_MD5 + "``\n");
            reported.put("ToolRecord.managed", "\n:Origin: ``local``\n");
            reported.put(
                    "ToolRecord.artefactIdentity",
                    "\n:Artefact: ``comet-2026.02.2-linux-x86_64.tar.gz``\n");
            reported.put("ToolRecord.capabilities", "\n:Capabilities: ``mzml``, ``mzxml``\n");
            reported.put("ToolRecord.stageId", "\n:Stage: ``search``\n");
            reported.put("ToolRecord.execution", "\n:Exit code: ``1``\n");
            reported.put("ToolRecord.warnings", "\n   * ``this build has no xml capability``\n");
            reported.put(
                    "ExecutionRecord.command",
                    "\n:Working directory: ``/" + RUN_DIRECTORY + "``\n");
            reported.put("ExecutionRecord.start", "\n:Started: ``2026-08-31T09:47:31.000Z``\n");
            reported.put("ExecutionRecord.end", "\n:Ended: ``2026-08-31T09:49:02.125Z``\n");
            reported.put("ExecutionRecord.exitCode", "\n:Exit code: ``0``\n");
            reported.put(
                    "ExecutionRecord.stdout",
                    "\n:Stdout log: ``/" + RUN_DIRECTORY + "/comet.stdout.log``\n");
            reported.put(
                    "ExecutionRecord.stderr",
                    "\n:Stderr log: ``/" + RUN_DIRECTORY + "/percolator.stderr.log``\n");
            reported.put("ExecutionRecord.status", "\n:Execution status: ``failed``\n");
            reported.put("ToolCommand.argv", "\n   * ``--results-psms``\n");
            reported.put(
                    "ToolCommand.workingDirectory",
                    "\n:Working directory: ``/" + RUN_DIRECTORY + "``\n");
            reported.put("ToolCommand.environment", "\n   * ``PATH`` = ``/usr/bin:/bin``\n");
            reported.put(
                    "LogRecord.path",
                    "\n:Stderr log: ``/" + RUN_DIRECTORY + "/percolator.stderr.log``\n");
            reported.put("LogRecord.hashes", "\n:Stderr MD5: ``" + EMPTY_MD5 + "``\n");
            reported.put("FileHashes.md5", "\n:Stdout MD5: ``" + ABC_MD5 + "``\n");
            reported.put("FileHashes.sha256", "\n:Stdout SHA-256: ``" + ABC_SHA256 + "``\n");
            reported.put("FileRecord.direction", "\n   * - ``output``\n");
            reported.put("FileRecord.role", "\n     - ``spectra``\n");
            reported.put("FileRecord.path", "\n     - ``/data/proteomics/HeLa_1ug_rep1.mzML``\n");
            reported.put("FileRecord.sizeBytes", "\n     - ``1234567890123``\n");
            reported.put("FileRecord.modifiedAt", "\n     - ``2026-08-30T18:00:00.001Z``\n");
            reported.put("FileRecord.hashes", "\n     - ``" + EMPTY_SHA256 + "``\n");
            reported.put("FileRecord.status", "\n     - ``partial``\n");
            return reported;
        }

        @Test
        @DisplayName("the model has exactly the components this test knows where to find")
        void theModelHasExactlyTheComponentsThisTestKnowsWhereToFind() {
            // Adding a component to any record in the manifest fails here until somebody has
            // decided where the report puts it.  This is what stops provenance.rst drifting away
            // from provenance.json as later phases extend the schema.
            assertEquals(new TreeSet<>(reportedAt().keySet()), modelComponents());
        }

        @Test
        @DisplayName("every component of the model appears in the report, on its own line")
        @DisabledOnOs(value = OS.WINDOWS, disabledReason = "POSIX paths, as above")
        void everyComponentOfTheModelIsReported() {
            String document = writer().render(fullFixture());

            List<String> missing = new ArrayList<>();
            for (Map.Entry<String, String> reported : reportedAt().entrySet()) {
                if (!document.contains(reported.getValue())) {
                    missing.add(
                            reported.getKey()
                                    + " is not reported; expected a line reading"
                                    + reported.getValue().replace("\n", "\\n"));
                }
            }

            assertEquals(List.of(), missing, document);
        }

        @Test
        @DisplayName("the reflective walk really does reach every record type, not just the root")
        void theWalkReachesEveryRecordType() {
            // Without this the test above would pass vacuously if the walk stopped at the root:
            // an empty expected set would equal an empty actual set.  The count is hand-counted
            // from the ten record types the model is made of.
            assertAll(
                    () -> assertEquals(53, modelComponents().size()),
                    () -> assertTrue(modelComponents().contains("FileHashes.sha256")),
                    () -> assertTrue(modelComponents().contains("ToolCommand.argv")),
                    () -> assertTrue(modelComponents().contains("RunId.value")));
        }
    }

    @Nested
    @DisplayName("Escaping")
    class Escaping {

        @Test
        @DisplayName("leaves inline markup characters alone, because a literal makes them inert")
        void leavesInlineMarkupAlone() {
            assertAll(
                    () ->
                            assertTrue(
                                    renderedWithSetting("5 * 3 spectra")
                                            .contains("\n     - ``5 * 3 spectra``\n")),
                    () ->
                            assertTrue(
                                    renderedWithSetting("a|b_c:d")
                                            .contains("\n     - ``a|b_c:d``\n")),
                    () ->
                            assertTrue(
                                    renderedWithSetting("..not-a-directive")
                                            .contains("\n     - ``..not-a-directive``\n")),
                    () ->
                            assertTrue(
                                    renderedWithSetting("C:\\Users\\ms\\data.mzML")
                                            .contains("\n     - ``C:\\Users\\ms\\data.mzML``\n")));
        }

        @Test
        @DisplayName("writes a value holding a backtick as a quoted, escaped string")
        void escapesABacktick() {
            // The interesting case: an inline literal has no escape mechanism, so a backtick
            // inside one either survives, breaks the document or renders as punctuation depending
            // on what else is on the line.  It is escaped out of the literal instead.
            String doubled = "\n     - ``\"\\u0060\\u0060code\\u0060\\u0060\"``\n";
            assertAll(
                    () ->
                            assertTrue(
                                    renderedWithSetting("a`b")
                                            .contains("\n     - ``\"a\\u0060b\"``\n")),
                    () ->
                            assertTrue(
                                    renderedWithSetting("`")
                                            .contains("\n     - ``\"\\u0060\"``\n")),
                    () -> assertTrue(renderedWithSetting("``code``").contains(doubled)));
        }

        @Test
        @DisplayName("writes an empty value as an empty quoted string, never as four backticks")
        void escapesAnEmptyValue() {
            assertTrue(renderedWithSetting("").contains("\n     - ``\"\"``\n"));
        }

        @Test
        @DisplayName("writes a value with an edge of whitespace as a quoted string")
        void escapesAnEdgeOfWhitespace() {
            assertAll(
                    () ->
                            assertTrue(
                                    renderedWithSetting(" leading")
                                            .contains("\n     - ``\" leading\"``\n")),
                    () ->
                            assertTrue(
                                    renderedWithSetting("trailing ")
                                            .contains("\n     - ``\"trailing \"``\n")),
                    () -> assertTrue(renderedWithSetting(" ").contains("\n     - ``\" \"``\n")));
        }

        @Test
        @DisplayName("writes every control character as its own escape, and DEL as a hex escape")
        void escapesControlCharacters() {
            assertAll(
                    () ->
                            assertTrue(
                                    renderedWithSetting("first\nsecond")
                                            .contains("\n     - ``\"first\\nsecond\"``\n")),
                    () ->
                            assertTrue(
                                    renderedWithSetting("a\tb\rc")
                                            .contains("\n     - ``\"a\\tb\\rc\"``\n")),
                    () ->
                            assertTrue(
                                    renderedWithSetting("a\bb\fc")
                                            .contains("\n     - ``\"a\\bb\\fc\"``\n")),
                    // DEL on its own, with no other control character in front of it: it sits
                    // ABOVE the printable range, so it is the one control character a "< 0x20"
                    // test alone would let through, and any value that also holds a smaller
                    // control character returns before reaching it.
                    () ->
                            assertTrue(
                                    renderedWithSetting("a\u007fb")
                                            .contains("\n     - ``\"a\\u007fb\"``\n")),
                    // \u0001 and \u001f differ in both nibbles, so a shift or a mask that went the
                    // wrong way would show; \u007f is above the printable range but is a control
                    // character all the same.
                    () ->
                            assertTrue(
                                    renderedWithSetting("a\u0001b\u001fc\u007fd")
                                            .contains(
                                                    "\n     - "
                                                            + "``\"a\\u0001b\\u001fc\\u007fd\"``"
                                                            + "\n")));
        }

        @Test
        @DisplayName("escapes the quotation mark and the backslash only in the escaped form")
        void escapesQuotesAndBackslashesOnlyWhenEscaping() {
            assertAll(
                    // No backtick, no control character, no whitespace edge: written as itself.
                    () ->
                            assertTrue(
                                    renderedWithSetting("\"a\"\\b")
                                            .contains("\n     - ``\"a\"\\b``\n")),
                    // A backtick forces the escaped form, and now both are escaped.
                    () ->
                            assertTrue(
                                    renderedWithSetting("\"a\"\\b`")
                                            .contains(
                                                    "\n     - "
                                                            + "``\"\\\"a\\\"\\\\b\\u0060\"``"
                                                            + "\n")));
        }

        @Test
        @DisplayName("writes non-ASCII text and an emoji as themselves, never as an escape")
        void writesNonAsciiAsItself() {
            String document = renderedWithSetting("protéomique 1µg 🧬");

            // A provenance record a scientist cannot read against their own disk is a worse
            // record, so nothing above U+001F is escaped.  The document as a whole does contain
            // the characters \\u -- its own preamble explains the escape -- so the assertion is
            // that these three characters were not escaped, spelled out one at a time.
            assertAll(
                    () -> assertTrue(document.contains("\n     - ``protéomique 1µg 🧬``\n")),
                    () -> assertFalse(document.contains("\\u00e9")),
                    () -> assertFalse(document.contains("\\u00b5")),
                    () -> assertFalse(document.contains("\\ud83e")));
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
                String turkish = writer().render(fullFixture());
                Locale.setDefault(Locale.GERMANY);
                String german = writer().render(fullFixture());
                Locale.setDefault(Locale.forLanguageTag("th-TH-u-nu-thai"));
                String thai = writer().render(fullFixture());

                assertAll(
                        () -> assertEquals(PINNED_FULL_REPORT, turkish),
                        () -> assertEquals(PINNED_FULL_REPORT, german),
                        () -> assertEquals(PINNED_FULL_REPORT, thai));
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

                assertEquals(PINNED_FULL_REPORT, writer().render(fullFixture()));
            } finally {
                TimeZone.setDefault(originalZone);
            }
        }

        @Test
        @DisplayName("does not depend on the order the settings or the environment were built in")
        @DisabledOnOs(value = OS.WINDOWS, disabledReason = "POSIX paths, as above")
        void doesNotDependOnMapInsertionOrder() {
            // The same manifest built forwards and backwards.  Both must equal the ONE pinned
            // document; asserting only that they equal each other would pass for two identically
            // wrong orders.
            String first = writer().render(fullFixture(false));
            String second = writer().render(fullFixture(true));

            assertAll(
                    () -> assertEquals(PINNED_FULL_REPORT, first),
                    () -> assertEquals(PINNED_FULL_REPORT, second));
        }
    }

    @Nested
    @DisplayName("On disk")
    class OnDisk {

        @Test
        @DisplayName("the file holds exactly the rendered document, UTF-8 encoded")
        @DisabledOnOs(value = OS.WINDOWS, disabledReason = "POSIX paths, as above")
        void theFileHoldsExactlyTheRenderedDocument() throws IOException {
            Path target = tempDir.resolve(ProvenanceReportWriter.FILE_NAME);

            writer().writeTo(target, fullFixture());

            // Both the decoded text and the raw bytes: the byte assertion is what proves the emoji
            // and the accented characters were encoded as UTF-8 rather than as the platform's
            // default charset, which on a Windows host would be windows-1252.
            assertAll(
                    () -> assertEquals(PINNED_FULL_REPORT, Files.readString(target, UTF_8)),
                    () ->
                            assertArrayEquals(
                                    PINNED_FULL_REPORT.getBytes(UTF_8),
                                    Files.readAllBytes(target)));
        }

        @Test
        @DisplayName("writeInto puts it under the standard name and returns that path")
        void writeIntoUsesTheStandardName() throws IOException {
            Path written = writer().writeInto(tempDir, runningFixture());

            assertAll(
                    () -> assertEquals("provenance.rst", ProvenanceReportWriter.FILE_NAME),
                    () -> assertEquals(tempDir.resolve("provenance.rst"), written),
                    () -> assertEquals(PINNED_RUNNING_REPORT, Files.readString(written, UTF_8)));
        }

        @Test
        @DisplayName("no temporary file is left beside the report")
        void noTemporaryFileIsLeftBehind() throws IOException {
            writer().writeInto(tempDir, runningFixture());

            try (Stream<Path> entries = Files.list(tempDir)) {
                assertEquals(
                        List.of("provenance.rst"),
                        entries.map(Path::getFileName).map(Path::toString).sorted().toList());
            }
        }

        @Test
        @DisplayName("a write into a directory that does not exist fails and creates nothing")
        void aWriteIntoAMissingDirectoryCreatesNothing() throws IOException {
            Path missing = tempDir.resolve("no-such-run");

            assertThrows(
                    NoSuchFileException.class, () -> writer().writeInto(missing, runningFixture()));

            try (Stream<Path> entries = Files.list(tempDir)) {
                assertEquals(List.of(), entries.toList());
            }
        }

        @Test
        @DisplayName("a write over a directory fails and leaves the directory intact")
        void aWriteOverADirectoryLeavesItIntact() throws IOException {
            Path target = tempDir.resolve(ProvenanceReportWriter.FILE_NAME);
            Files.createDirectory(target);
            Files.writeString(target.resolve("inside.txt"), "kept", UTF_8);

            IOException thrown =
                    assertThrows(
                            IOException.class, () -> writer().writeTo(target, runningFixture()));

            assertAll(
                    () ->
                            assertEquals(
                                    "Cannot write " + target + ": it is an existing directory",
                                    thrown.getMessage()),
                    () ->
                            assertEquals(
                                    "kept", Files.readString(target.resolve("inside.txt"), UTF_8)));
        }

        @Test
        @DisplayName("leaves a sample report where the documentation gate can be run over it")
        @DisabledOnOs(value = OS.WINDOWS, disabledReason = "POSIX paths, as above")
        void leavesASampleReportForTheDocumentationGate() throws IOException {
            // R-PROC-02 confines process launching to the process service, and an ArchUnit rule
            // enforces it, so this test cannot run sphinx-build itself.  It writes the FULLY
            // POPULATED report -- the one carrying backticks, an emoji, an empty value and a line
            // feed -- into a ready-made Sphinx source tree under target/, so that the check is one
            // command for whoever verifies this work unit.  Nothing outside target/ is touched.
            Path sample = Path.of("target", "provenance-report-sample");
            Files.createDirectories(sample);
            Files.writeString(sample.resolve("conf.py"), sampleConf(), UTF_8);
            Files.writeString(sample.resolve("index.rst"), sampleIndex(), UTF_8);
            Path report = writer().writeInto(sample, fullFixture());

            assertAll(
                    () -> assertEquals(PINNED_FULL_REPORT, Files.readString(report, UTF_8)),
                    () -> assertTrue(Files.isRegularFile(sample.resolve("conf.py"))),
                    () -> assertTrue(Files.isRegularFile(sample.resolve("index.rst"))));
        }
    }

    @Nested
    @DisplayName("The seeded secret corpus")
    class Secrets {

        @Test
        @DisplayName("does not survive anywhere in the rendered report or the file on disk")
        @DisabledOnOs(value = OS.WINDOWS, disabledReason = "POSIX paths, as above")
        void doesNotSurviveAnywhere() throws IOException {
            ProvenanceReportWriter loaded =
                    ProvenanceReportWriter.redactingWith(
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
                                    + ") survived into the rendered report");
                }
                if (onDisk.contains(CORPUS.get(secret))) {
                    leaks.add(
                            "corpus secret #"
                                    + secret
                                    + " (length "
                                    + CORPUS.get(secret).length()
                                    + ") survived into provenance.rst");
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
        @DisabledOnOs(value = OS.WINDOWS, disabledReason = "POSIX paths, as above")
        void leavesOrdinaryTextIntact() {
            // The other half of the gate.  A writer that emitted an empty document, or a rule set
            // that destroyed every string, would pass the sweep above and be useless.
            ProvenanceReportWriter loaded =
                    ProvenanceReportWriter.redactingWith(
                            SecretRedactor.with(SecretRegistry.copyOf(CORPUS)));

            String document = loaded.render(manifestCarryingTheCorpus());

            assertAll(
                    () -> assertTrue(document.contains("``/data/HeLa_1ug_rep1.mzML``")),
                    () ->
                            assertTrue(
                                    document.contains(
                                            "* ``COMET_PARAMS`` = ``/data/comet.params``")),
                    () -> assertTrue(document.contains(":Name: ``limelight-upload``")),
                    () -> assertTrue(document.contains("- ``spectra``")));
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
                                    () -> ProvenanceReportWriter.redactingWith(nullRedactor))
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
                                                                            runningFixture()))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "manifest",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () ->
                                                            writer().writeTo(
                                                                            tempDir.resolve("x"),
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
                                                                            runningFixture()))
                                            .getMessage()));
        }

        @Test
        @DisplayName("toString names the rule set and no secret")
        void toStringNamesTheRuleSetAndNoSecret() {
            ProvenanceReportWriter loaded =
                    ProvenanceReportWriter.redactingWith(
                            SecretRedactor.with(SecretRegistry.of(SWORDFISH)));

            String described = loaded.toString();

            assertAll(
                    () ->
                            assertEquals(
                                    "ProvenanceReportWriter["
                                            + "SecretRedactor[SecretRegistry[secretCount=1]]]",
                                    described),
                    () -> assertFalse(described.contains(SWORDFISH)));
        }
    }

    // ---------------------------------------------------------------------------------------
    // Helpers.
    // ---------------------------------------------------------------------------------------

    /**
     * True if a line is nothing but one repeated heading rule character.
     *
     * @param line the line to inspect
     * @return whether it is an overline or an underline
     */
    private static boolean isRule(String line) {
        if (line.isEmpty()) {
            return false;
        }
        char first = line.charAt(0);
        if (first != '=' && first != '-') {
            return false;
        }
        return line.chars().allMatch(character -> character == first);
    }

    /**
     * Records a heading whose rule is the wrong length.
     *
     * @param mismatches where to record it
     * @param heading the heading text
     * @param rule the rule under or over it
     */
    private static void collectMismatch(List<String> mismatches, String heading, String rule) {
        if (heading.length() != rule.length()) {
            mismatches.add(
                    "the heading \""
                            + heading
                            + "\" is "
                            + heading.length()
                            + " characters and its rule is "
                            + rule.length());
        }
    }

    /**
     * Every record component of {@link ProvenanceManifest} and of every record reachable from it,
     * as {@code SimpleName.component}.
     *
     * <p>Discovered reflectively, never from the report: a set built by asking the writer what it
     * rendered would agree with the writer by construction, which is the defect this project has
     * shipped three times.
     *
     * @return the component names, sorted
     */
    private static Set<String> modelComponents() {
        Set<Class<?>> seen = new LinkedHashSet<>();
        Deque<Class<?>> pending = new ArrayDeque<>();
        pending.add(ProvenanceManifest.class);
        Set<String> names = new TreeSet<>();
        while (!pending.isEmpty()) {
            Class<?> type = pending.removeFirst();
            if (!seen.add(type)) {
                continue;
            }
            for (RecordComponent component : type.getRecordComponents()) {
                names.add(type.getSimpleName() + "." + component.getName());
                collectRecordTypes(component.getGenericType(), pending);
            }
        }
        return names;
    }

    /**
     * Queues every record class mentioned by a type, including inside its type arguments.
     *
     * @param type the type to inspect
     * @param pending the queue to add to
     */
    private static void collectRecordTypes(Type type, Deque<Class<?>> pending) {
        if (type instanceof Class<?> raw) {
            if (raw.isRecord()) {
                pending.add(raw);
            }
            return;
        }
        if (type instanceof ParameterizedType parameterized) {
            collectRecordTypes(parameterized.getRawType(), pending);
            for (Type argument : parameterized.getActualTypeArguments()) {
                collectRecordTypes(argument, pending);
            }
        }
    }

    /**
     * A manifest whose settings, argument array, environment, paths and warnings all carry seeded
     * secrets, long and short.
     *
     * @return the manifest
     */
    private static ProvenanceManifest manifestCarryingTheCorpus() {
        Map<String, String> environment = new LinkedHashMap<>();
        environment.put("AWS_SECRET_ACCESS_KEY", AWS_SECRET);
        environment.put("GITHUB_TOKEN", GITHUB_TOKEN);
        environment.put("LIMELIGHT_API_KEY", LIMELIGHT_KEY);
        environment.put("PERCOLATOR_PASSWORD", PASSPHRASE);
        environment.put("COMET_PARAMS", "/data/comet.params");

        Map<String, String> settings = new LinkedHashMap<>();
        settings.put("limelight.upload-url", "https://ll-user:" + URL_PASSWORD + "@ll.example/up");
        settings.put("limelight.auth-header", "Authorization: Bearer " + JWT);
        settings.put("limelight.response", "response body {\"token\":\"" + LIVE_TOKEN + "\"}");
        settings.put("limelight.key-material", "-----BEGIN RSA PRIVATE KEY-----\n" + PEM_BODY);
        // Short carriers, one per rule family; see blind spot (2) of SeededSecretCorpusTest.
        settings.put("report.short-assignment", "auth=" + SWORDFISH);
        settings.put("report.short-unnamed", "pw=" + SWORDFISH);
        settings.put("report.short-url", "ftp://u:" + SWORDFISH + "@h/");
        settings.put("report.short-bare", SWORDFISH);
        settings.put("report.short-token", AWS_ACCESS_KEY_ID);

        ToolRecord upload =
                new ToolRecord(
                        "limelight-upload",
                        "1.0.0",
                        Optional.of(GITHUB_TOKEN),
                        absolute("opt/limelight/bin/upload"),
                        ABC_HASHES,
                        false,
                        Optional.of(AWS_ACCESS_KEY_ID),
                        Set.of(SWORDFISH),
                        Optional.of("upload"),
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
                                                "--input",
                                                "/data/HeLa_1ug_rep1.mzML"),
                                        absolute("data"),
                                        environment),
                                Instant.parse("2026-08-31T09:15:00Z"),
                                Instant.parse("2026-08-31T09:15:01Z"),
                                0,
                                Optional.empty(),
                                Optional.empty(),
                                ProvenanceStatus.COMPLETED),
                        List.of(
                                "github token " + GITHUB_TOKEN + " accepted",
                                "connecting with password: " + SWORDFISH,
                                "limelight key " + LIMELIGHT_KEY + " accepted"));

        return new ProvenanceManifest(
                1,
                new RunRecord(
                        new RunId("run-20260831-091500"),
                        "project-alpha",
                        ProvenanceStatus.COMPLETED,
                        Instant.parse("2026-08-31T09:14:00Z"),
                        Optional.of(Instant.parse("2026-08-31T09:48:00Z"))),
                new ApplicationRecord(
                        "0.1.0-SNAPSHOT",
                        AWS_ACCESS_KEY_ID,
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
                                absolute("data/HeLa_1ug_rep1.mzML"),
                                1024L,
                                Instant.parse("2026-08-30T18:00:00Z"),
                                ABC_HASHES,
                                ProvenanceStatus.COMPLETED),
                        new FileRecord(
                                FileDirection.OUTPUT,
                                SWORDFISH,
                                absolute("data/" + LIVE_TOKEN + "/results.txt"),
                                2048L,
                                Instant.parse("2026-08-31T09:48:00Z"),
                                EMPTY_HASHES,
                                ProvenanceStatus.COMPLETED)));
    }

    /**
     * A throwaway Sphinx configuration for the sample report, mirroring the one {@code
     * scripts/ci/docs-build.sh} generates for the project documents outside {@code docs/}.
     *
     * @return the configuration file's text
     */
    private static String sampleConf() {
        return """
            # Written by ProvenanceReportWriterTest into target/. Throwaway: rewritten on
            # every test run and never committed. It exists so that the generated sample
            # report can be put through the project's documentation gate:
            #
            #     .venv/bin/sphinx-build -n -W -b html \\
            #         cometgui-provenance/target/provenance-report-sample \\
            #         cometgui-provenance/target/provenance-report-sample-html
            project = "CometGUI provenance report sample"
            author = "The CometGUI project"
            extensions = []
            root_doc = "index"
            exclude_patterns = []
            templates_path = []
            html_static_path = []
            html_theme = "alabaster"
            nitpicky = True
            # No suppress_warnings and no nitpick_ignore: -n -W must bite.
            """;
    }

    /**
     * The master document the sample report hangs off, so that Sphinx does not warn that the report
     * is in no toctree -- which under {@code -W} would be an error about the harness rather than
     * about the report.
     *
     * @return the index document's text
     */
    private static String sampleIndex() {
        return """
            ==========================================
            CometGUI provenance report -- build sample
            ==========================================

            .. toctree::
               :maxdepth: 2

               provenance
            """;
    }
}
