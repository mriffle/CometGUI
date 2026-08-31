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

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.domain.run.RunId;

/**
 * Valid neighbours for the record under test, and nothing else.
 *
 * <p>Every constant here is a hand-typed literal. The two digest pairs are the published RFC 1321
 * and NIST vectors for the empty string and for {@code "abc"}, transcribed rather than computed, so
 * that no expected value anywhere in this package can have come from CometGUI code.
 *
 * <p>This class deliberately holds <em>no</em> expected values. A test that asserted against a
 * fixture's idea of what a record should contain would be comparing two copies of the same
 * assumption; the expected value of every assertion in this package is typed out at the assertion.
 * What lives here is only the valid surrounding objects a record needs in order to exist at all --
 * a {@link FileHashes} to hold, a {@link ToolCommand} to describe, an {@link ExecutionRecord} for a
 * {@link ToolRecord} to wrap.
 */
final class ManifestFixtures {

    /** MD5 of the empty string, RFC 1321. */
    static final String EMPTY_MD5 = "d41d8cd98f00b204e9800998ecf8427e";

    /** SHA-256 of the empty string, NIST. */
    static final String EMPTY_SHA256 =
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    /** MD5 of {@code "abc"}, RFC 1321. */
    static final String ABC_MD5 = "900150983cd24fb0d6963f7d28e17f72";

    /** SHA-256 of {@code "abc"}, NIST. */
    static final String ABC_SHA256 =
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    /** The published empty-file digests as a pair. */
    static final FileHashes EMPTY_HASHES = new FileHashes(EMPTY_MD5, EMPTY_SHA256);

    /** The published {@code "abc"} digests as a pair. */
    static final FileHashes ABC_HASHES = new FileHashes(ABC_MD5, ABC_SHA256);

    /**
     * An absolute directory built by resolving a relative name rather than written as a literal: an
     * absolute literal is neither portable to a Windows runner nor acceptable to SpotBugs.
     */
    static final Path RUN_DIRECTORY =
            Path.of("cometgui-test-runs", "run-20260831-091500").toAbsolutePath();

    private ManifestFixtures() {
        throw new AssertionError("ManifestFixtures is a fixture holder and is never instantiated");
    }

    /**
     * An absolute path inside the notional run directory.
     *
     * @param name the file name
     * @return the absolute path
     */
    static Path runFile(String name) {
        return RUN_DIRECTORY.resolve(name);
    }

    /**
     * A launchable command with one environment variable.
     *
     * @param variable the environment variable name
     * @param value the environment variable value
     * @return the command
     */
    static ToolCommand command(String variable, String value) {
        return new ToolCommand(
                List.of("/opt/comet/comet", "-P", "comet.params"),
                RUN_DIRECTORY,
                Map.of(variable, value));
    }

    /**
     * A finished execution of {@link #command(String, String)}.
     *
     * @param variable the environment variable name
     * @param value the environment variable value
     * @return the execution record
     */
    static ExecutionRecord execution(String variable, String value) {
        return new ExecutionRecord(
                command(variable, value),
                Instant.parse("2026-08-31T09:15:00Z"),
                Instant.parse("2026-08-31T09:47:30Z"),
                0,
                Optional.empty(),
                Optional.empty(),
                ProvenanceStatus.COMPLETED);
    }

    /**
     * A managed tool wrapping {@link #execution(String, String)}.
     *
     * @param name the logical tool name
     * @param variable the environment variable name
     * @param value the environment variable value
     * @return the tool record
     */
    static ToolRecord tool(String name, String variable, String value) {
        return new ToolRecord(
                name,
                "2026.02.2",
                Optional.of("v2026.02.2"),
                runFile("comet"),
                ABC_HASHES,
                true,
                Optional.of("comet-2026.02.2-linux-x86_64.tar.gz"),
                Set.of("mzml"),
                execution(variable, value),
                List.of());
    }

    /**
     * An input file record.
     *
     * @param role the file's role in the run
     * @param name the file name inside the run directory
     * @return the file record
     */
    static FileRecord inputFile(String role, String name) {
        return new FileRecord(
                FileDirection.INPUT,
                role,
                runFile(name),
                1024L,
                Instant.parse("2026-08-30T18:00:00Z"),
                ABC_HASHES,
                ProvenanceStatus.COMPLETED);
    }

    /**
     * A run that has finished.
     *
     * @return the run record
     */
    static RunRecord completedRun() {
        return new RunRecord(
                new RunId("run-20260831-091500"),
                "project-alpha",
                ProvenanceStatus.COMPLETED,
                Instant.parse("2026-08-31T09:14:00Z"),
                Optional.of(Instant.parse("2026-08-31T09:48:00Z")));
    }

    /**
     * An application record with fixed, obviously synthetic values.
     *
     * @return the application record
     */
    static ApplicationRecord application() {
        return new ApplicationRecord(
                "0.1.0-SNAPSHOT",
                "e97d863",
                "Frobnitz OS",
                "9.4-alpha",
                "sparc64",
                "25.0.4.1",
                Locale.of("tr", "TR"),
                ZoneId.of("Pacific/Chatham"));
    }
}
