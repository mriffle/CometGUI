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

package org.cometgui.install.probe;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import org.cometgui.domain.platform.GlibcVersion;
import org.cometgui.domain.tools.LoaderDiagnostic;
import org.cometgui.domain.tools.ProbeFailureKind;
import org.cometgui.install.archive.ArtefactMirror;
import org.cometgui.tools.process.ProcessService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * The probe run against the <strong>real upstream binaries</strong>, through the real process
 * service.
 *
 * <p>This is where {@code R-PLAT-03} stops being a string in a fixture. The Percolator 3.09 Debian
 * payload is executed here and produces both of its real loader failures -- the missing shared
 * object as published, and the {@code GLIBCXX_3.4.32} symbol failure exposed behind a stub {@code
 * libboost_filesystem} -- and the whole diagnostic is asserted, hand-typed. The real 3.06.5 and
 * 3.07.1 binaries are executed and identified. The real {@code aarch64} Comet build is the
 * wrong-architecture case.
 *
 * <p>The host's own versions are <strong>supplied as constants</strong> rather than detected, so
 * that the assertions are on this unit's wording and not on whatever {@code libstdc++} the machine
 * happens to carry. That the constants are what this host really has is {@code
 * HostCxxRuntimeTest}'s job, and it proves it against a bracket the loader itself drew.
 */
@EnabledOnOs(
        value = OS.LINUX,
        disabledReason =
                "these are real Linux ELF binaries from this phase's artefact mirror; no Windows"
                        + " or macOS Percolator binary has ever been executed anywhere in this"
                        + " project, which is recorded as residue rather than covered here")
class RealBinaryProbeTest {

    /** Debian 12: glibc 2.36, GLIBCXX_3.4.30. */
    private static final HostRuntimeVersions DEBIAN_12 =
            new HostRuntimeVersions(
                    Optional.of(GlibcVersion.parse("2.36")),
                    Optional.of(GlibcVersion.parse("3.4.30")));

    private static final List<String> ALTERNATIVES =
            List.of("percolator 3.07.1 linux-x86-64", "percolator 3.06.5 linux-x86-64");

    private static final Duration GENEROUS = Duration.ofSeconds(5);

    @Test
    @DisplayName("the fixtures are the real published bytes, checked before anything is run")
    void theFixturesArePinned() throws IOException {
        assertAll(
                () ->
                        StagedBinaries.assertIsPinned(
                                StagedBinaries.percolator3071(),
                                2538632,
                                ProbeRecords.shippedPercolator("3.07.1")
                                        .member()
                                        .orElseThrow()
                                        .hashes()
                                        .sha256()),
                () ->
                        StagedBinaries.assertIsPinned(
                                StagedBinaries.percolator3065(),
                                2448768,
                                ProbeRecords.shippedPercolator("3.06.5")
                                        .member()
                                        .orElseThrow()
                                        .hashes()
                                        .sha256()),
                () ->
                        StagedBinaries.assertIsPinned(
                                StagedBinaries.payload309(),
                                StagedBinaries.PAYLOAD_309_SIZE,
                                StagedBinaries.PAYLOAD_309_SHA256));
    }

    @Test
    @DisplayName("layer 1, executed: the 3.09 payload as published, whole diagnostic")
    void theRealMissingSharedObject(@TempDir Path staged) throws IOException {
        Path executable = StagedBinaries.stage(StagedBinaries.payload309(), staged, "percolator");

        LoadabilityResult result =
                probe(GENEROUS)
                        .probe(executable, List.of("--help"), Map.of(), context("percolator"));

        LoaderDiagnostic diagnostic = result.failure().orElseThrow();
        assertAll(
                () -> assertFalse(result.started()),
                () -> assertEquals(OptionalInt.of(127), result.exitCode()),
                () -> assertEquals(List.of(), result.standardOutput()),
                () -> assertEquals(ProbeFailureKind.MISSING_SHARED_OBJECT, diagnostic.kind()),
                () -> assertEquals("libboost_filesystem.so.1.83.0", diagnostic.objectName()),
                () ->
                        assertEquals(
                                "This build cannot run on this host: the dynamic loader could not"
                                        + " find the shared library libboost_filesystem.so.1.83.0."
                                        + " Required: not named by the "
                                        + "loader. Available on this host:"
                                        + " none found. Alternatives: "
                                        + "percolator 3.07.1 linux-x86-64;"
                                        + " percolator 3.06.5 linux-x86-64.",
                                diagnostic.message()));
    }

    @Test
    @DisplayName("layer 2, executed: the GLIBCXX failure behind the stub, whole diagnostic")
    void theRealSymbolVersionFailure(@TempDir Path staged) throws IOException {
        Path executable = StagedBinaries.stage(StagedBinaries.payload309(), staged, "percolator");
        Map<String, String> withStub =
                Map.of("LD_LIBRARY_PATH", StagedBinaries.stubLibraryDirectory().toString());

        LoadabilityResult result =
                probe(GENEROUS)
                        .probe(executable, List.of("--help"), withStub, context("percolator"));

        LoaderDiagnostic diagnostic = result.failure().orElseThrow();
        assertAll(
                () -> assertEquals(OptionalInt.of(1), result.exitCode()),
                () ->
                        assertEquals(
                                2,
                                result.standardError().size(),
                                () ->
                                        "the loader reports the C++ runtime line and then the C"
                                                + " library line: "
                                                + result.standardError()),
                () -> assertEquals(ProbeFailureKind.MISSING_SYMBOL_VERSION, diagnostic.kind()),
                () -> assertEquals("/lib/x86_64-linux-gnu/libstdc++.so.6", diagnostic.objectName()),
                () ->
                        assertEquals(
                                "This build cannot run on this host:"
                                        + " /lib/x86_64-linux-gnu/libstdc++.so.6 "
                                        + "on this host does not"
                                        + " provide a symbol version this build needs. Required:"
                                        + " GLIBCXX_3.4.32. Available on this host: GLIBCXX_3.4.30."
                                        + " Alternatives: percolator "
                                        + "3.07.1 linux-x86-64; percolator"
                                        + " 3.06.5 linux-x86-64.",
                                diagnostic.message()),
                () ->
                        assertTrue(
                                result.standardError().get(1).contains("GLIBC_2.38"),
                                () ->
                                        "the C library floor is unmet too, and it is the SECOND"
                                                + " line: a check knowing only "
                                                + "glibc would have called"
                                                + " this build runnable: "
                                                + result.standardError()));
    }

    @Test
    @DisplayName("the real 3.07.1 and 3.06.5 binaries start, and say what they are, on stderr")
    void theRealWorkingBinariesStartAndIdentify(@TempDir Path staged) throws IOException {
        assertAll(
                () ->
                        assertIdentifies(
                                staged.resolve("a"), StagedBinaries.percolator3071(), "3.07.1"),
                () ->
                        assertIdentifies(
                                staged.resolve("b"), StagedBinaries.percolator3065(), "3.06.5"));
    }

    private void assertIdentifies(Path staged, Path binary, String expectedVersion)
            throws IOException {
        Files.createDirectories(staged);
        Path executable = StagedBinaries.stage(binary, staged, "percolator");

        LoadabilityResult result =
                probe(GENEROUS)
                        .probe(
                                executable,
                                VersionBanner.percolator().arguments(),
                                Map.of(),
                                context("percolator"));

        assertAll(
                () -> assertTrue(result.started(), () -> "did not start: " + result.output()),
                () -> assertEquals(OptionalInt.of(0), result.exitCode()),
                () ->
                        assertEquals(
                                List.of(),
                                result.standardOutput(),
                                "Percolator writes NOTHING to standard output for --help, so a"
                                        + " probe reading only that stream sees an empty string"),
                () ->
                        assertEquals(
                                Optional.empty(),
                                VersionBanner.percolator().readFrom(result.standardOutput()),
                                "which is what a stdout-only probe would conclude: no version"),
                () ->
                        assertEquals(
                                expectedVersion,
                                IdentityProbe.identify(
                                                VersionBanner.percolator(),
                                                result.standardError(),
                                                result.standardOutput())
                                        .orElseThrow()
                                        .text(),
                                "and reading standard error first finds it"));
    }

    @Test
    @DisplayName(
            "a working binary that exits non-zero has still started -- Percolator with no args")
    void aNonZeroExitFromAWorkingBinaryIsNotALoaderFailure(@TempDir Path staged)
            throws IOException {
        Path executable =
                StagedBinaries.stage(StagedBinaries.percolator3071(), staged, "percolator");

        LoadabilityResult result =
                probe(GENEROUS).probe(executable, List.of(), Map.of(), context("percolator"));

        assertAll(
                () -> assertEquals(OptionalInt.of(1), result.exitCode()),
                () ->
                        assertTrue(
                                result.started(),
                                () ->
                                        "a probe reading the exit code would refuse a working"
                                                + " binary: "
                                                + result.output()),
                () ->
                        assertEquals(
                                List.of(
                                        "Error: too few arguments.",
                                        "Invoke with -h option for help"),
                                result.standardError()));
    }

    @Test
    @DisplayName("the real aarch64 Comet build is refused by architecture, read from its header")
    void theRealForeignArchitectureBuild(@TempDir Path staged) throws IOException {
        Path executable =
                StagedBinaries.stage(
                        ArtefactMirror.artefact("v2026.02.2__comet.aarch64.linux.exe"),
                        staged,
                        "comet");

        LoadabilityResult result =
                probe(GENEROUS).probe(executable, List.of("-h"), Map.of(), context("comet"));

        LoaderDiagnostic diagnostic = result.failure().orElseThrow();
        assertAll(
                () -> assertEquals(ProbeFailureKind.WRONG_ARCHITECTURE, diagnostic.kind()),
                () ->
                        assertEquals(
                                "This build cannot run on this host: comet was built for a"
                                        + " different processor architecture from the one this host"
                                        + " runs. Required: not named by "
                                        + "the loader. Available on this"
                                        + " host: none found. Alternatives: percolator 3.07.1"
                                        + " linux-x86-64; percolator 3.06.5 linux-x86-64.",
                                diagnostic.message()),
                () ->
                        assertEquals(
                                OptionalInt.empty(),
                                result.exitCode(),
                                "it is refused before it is launched, because launching it does"
                                        + " NOT produce an exec-format "
                                        + "error: glibc retries an ENOEXEC"
                                        + " file through /bin/sh, which "
                                        + "starts a process and prints a"
                                        + " shell syntax error"),
                () ->
                        assertEquals(
                                Optional.of(org.cometgui.domain.tools.HostArchitecture.X86_64),
                                ExecutableFormat.architectureOf(StagedBinaries.percolator3071()),
                                "and an x86-64 build on an x86-64 host is not refused"));
    }

    @Test
    @DisplayName("a file without its executable bit is NOT_EXECUTABLE, from the real refusal")
    void aFileWithoutItsExecutableBit(@TempDir Path staged) throws IOException {
        Path executable = staged.resolve("percolator");
        Files.writeString(executable, "#!/bin/sh\nexit 0\n");
        assertTrue(executable.toFile().setExecutable(false, false), "could not clear the bit");

        LoadabilityResult result =
                probe(GENEROUS).probe(executable, List.of(), Map.of(), context("percolator"));

        assertEquals(
                "This build cannot run on this host: percolator is not executable on this host."
                        + " Required: not named by the loader. Available on this host: none found."
                        + " Alternatives: percolator 3.07.1 linux-x86-64; percolator 3.06.5"
                        + " linux-x86-64.",
                result.failure().orElseThrow().message());
    }

    @Test
    @DisplayName("a build that prints nothing and exits non-zero established nothing")
    void aSilentNonZeroExitIsALoadabilityFailure(@TempDir Path staged) throws IOException {
        Path executable = script(staged, "exit 3\n");

        LoadabilityResult result =
                probe(GENEROUS).probe(executable, List.of(), Map.of(), context("percolator"));

        assertAll(
                () -> assertEquals(OptionalInt.of(3), result.exitCode()),
                () ->
                        assertEquals(
                                ProbeFailureKind.EXECUTION_FAILED,
                                result.failure().orElseThrow().kind(),
                                "the safe direction of an ambiguous outcome is \"we did not"
                                        + " establish that it starts\""));
    }

    @Test
    @DisplayName("a build that never finishes is TIMED_OUT, and is cancelled rather than left")
    void aBuildThatNeverFinishes(@TempDir Path staged) throws IOException {
        Path executable = script(staged, "sleep 30\n");

        LoadabilityResult result =
                probe(Duration.ofMillis(500))
                        .probe(executable, List.of(), Map.of(), context("percolator"));

        assertAll(
                () ->
                        assertEquals(
                                ProbeFailureKind.TIMED_OUT, result.failure().orElseThrow().kind()),
                () -> assertEquals(OptionalInt.empty(), result.exitCode()));
    }

    @Test
    @DisplayName("a path with no directory, and a path that is not a file, each say which it is")
    void aPathThatCannotBeProbed(@TempDir Path staged) throws IOException {
        Path noDirectory = staged.resolve("gone").resolve("percolator");
        Path notAFile = Files.createDirectories(staged.resolve("adirectory"));

        assertAll(
                () ->
                        assertTrue(
                                org.junit.jupiter.api.Assertions.assertThrows(
                                                IOException.class,
                                                () ->
                                                        probe(GENEROUS)
                                                                .probe(
                                                                        noDirectory,
                                                                        List.of(),
                                                                        Map.of(),
                                                                        context("percolator")))
                                        .getMessage()
                                        .endsWith(
                                                "has no directory to run in, so it cannot be"
                                                        + " probed")),
                () ->
                        assertTrue(
                                org.junit.jupiter.api.Assertions.assertThrows(
                                                IOException.class,
                                                () ->
                                                        probe(GENEROUS)
                                                                .probe(
                                                                        notAFile,
                                                                        List.of(),
                                                                        Map.of(),
                                                                        context("percolator")))
                                        .getMessage()
                                        .endsWith("is not a regular file, so it cannot be probed"),
                                "and a directory is not an executable, whatever the loader would"
                                        + " make of it"));
    }

    @Test
    @DisplayName("a start failure is read from the whole cause chain, not from the wrapper")
    void theCauseChainIsWhatTheClassifierReads() {
        assertAll(
                () ->
                        assertEquals(
                                "could not start ToolCommand[...] | Exec failed, error: 13"
                                        + " (Permission denied)",
                                LoadabilityProbe.wholeChain(
                                        new IOException(
                                                "could not start ToolCommand[...]",
                                                new IOException(
                                                        "Exec failed, error: 13 (Permission"
                                                                + " denied)")))),
                () ->
                        assertEquals(
                                "",
                                LoadabilityProbe.wholeChain(new IOException()),
                                "a cause with no message contributes an empty string, not the"
                                        + " word null"));
    }

    private static Path script(Path directory, String body) throws IOException {
        Path file = directory.resolve("percolator");
        Files.writeString(file, "#!/bin/sh\n" + body);
        if (!file.toFile().setExecutable(true, true)) {
            throw new IOException("could not make " + file + " executable");
        }
        return file;
    }

    private static LoadabilityProbe probe(Duration timeout) {
        return new LoadabilityProbe(
                new ProcessService(Clock.systemUTC()),
                new LoaderOutputClassifier(ProbeRecords.LINUX_X86_64, DEBIAN_12),
                ProbeRecords.LINUX_X86_64,
                timeout);
    }

    private static ProbeContext context(String subject) {
        return new ProbeContext(subject, List.of(), ALTERNATIVES);
    }
}
