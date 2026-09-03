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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import org.cometgui.domain.ports.ProcessRunner;
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.domain.tools.ProbeFailureKind;
import org.cometgui.install.testing.Nulls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The four loadability paths a real binary cannot be made to take on demand, driven through {@link
 * FakeRunner}: a start failure nothing recognises, a cancellation that has to be observed, a
 * process whose exit is delivered, and a probing thread that is interrupted.
 */
class LoadabilityProbeFakeTest {

    private static final Duration BRIEF = Duration.ofMillis(200);
    private static final List<String> ALTERNATIVES = List.of("percolator 3.06.5 linux-x86-64");

    @Test
    @DisplayName("a start failure nothing recognises is still a loadability failure")
    void anUnrecognisedStartFailure(@TempDir Path staged) throws IOException {
        Path executable = file(staged);

        LoadabilityResult result =
                probe(FakeRunner.failingWith(new IOException("something nobody has catalogued")))
                        .probe(executable, List.of(), Map.of(), context());

        assertAll(
                () ->
                        assertEquals(
                                ProbeFailureKind.EXECUTION_FAILED,
                                result.failure().orElseThrow().kind(),
                                "a start that did not happen established nothing, so the safe"
                                        + " direction is a loadability failure"),
                () -> assertEquals(OptionalInt.empty(), result.exitCode()),
                () -> assertFalse(result.started()));
    }

    @Test
    @DisplayName("a process that never ends is asked to stop, and the request is observed")
    void aTimedOutProcessIsCancelled(@TempDir Path staged) throws IOException {
        Path executable = file(staged);
        FakeRunner.NeverEnding runner = new FakeRunner.NeverEnding(true, false);

        LoadabilityResult result = probe(runner).probe(executable, List.of(), Map.of(), context());

        assertAll(
                () ->
                        assertEquals(
                                ProbeFailureKind.TIMED_OUT, result.failure().orElseThrow().kind()),
                () ->
                        assertTrue(
                                runner.wasCancelled(),
                                "a probe that gave up waiting and left the process running would"
                                        + " leak one process per install attempt"));
    }

    @Test
    @DisplayName("an exit that is delivered is seen, and the probe does not wait out its timeout")
    void aDeliveredExitIsSeen(@TempDir Path staged) throws IOException {
        Path executable = file(staged);
        ProcessRunner runner =
                FakeRunner.emitting(
                        List.of("Percolator version 3.07.1, Build Date x"), List.of(), 0);

        LoadabilityResult result = probe(runner).probe(executable, List.of(), Map.of(), context());

        assertAll(
                () -> assertTrue(result.started()),
                () ->
                        assertEquals(
                                OptionalInt.of(0),
                                result.exitCode(),
                                "the exit code has to reach the result; a latch that never opened"
                                        + " would make this a timeout instead"),
                () ->
                        assertEquals(
                                List.of("Percolator version 3.07.1, Build Date x"),
                                result.standardError()));
    }

    @Test
    @DisplayName("an interrupted probing thread gives up, restores the interrupt, and cancels")
    void anInterruptedProbeGivesUp(@TempDir Path staged) throws IOException {
        Path executable = file(staged);
        FakeRunner.NeverEnding runner = new FakeRunner.NeverEnding(false, true);

        LoadabilityResult result =
                probe(runner, Duration.ofSeconds(2))
                        .probe(executable, List.of(), Map.of(), context());

        assertAll(
                () ->
                        assertEquals(
                                ProbeFailureKind.TIMED_OUT,
                                result.failure().orElseThrow().kind(),
                                "the wait was interrupted rather than exhausted"),
                () -> assertTrue(runner.wasCancelled()),
                () ->
                        assertTrue(
                                Thread.interrupted(),
                                "the interrupt is restored for the caller rather than swallowed"));
    }

    @Test
    @DisplayName("output on standard output alone is still output, so the build has started")
    void outputOnStandardOutputAloneCounts(@TempDir Path staged) throws IOException {
        Path executable = file(staged);

        LoadabilityResult result =
                probe(FakeRunner.emitting(List.of(), List.of("a line on stdout"), 1))
                        .probe(executable, List.of(), Map.of(), context());

        assertTrue(
                result.started(),
                "\"it printed nothing at all AND exited non-zero\" is the rule; printing on"
                        + " either stream is printing");
    }

    @Test
    @DisplayName("a path whose absolute form has no parent is refused before anything is launched")
    void aPathWithNoParentAtAll(@TempDir Path staged) {
        Path fileSystemRoot = java.util.Objects.requireNonNull(staged.getRoot(), "root");

        IOException refused =
                assertThrows(
                        IOException.class,
                        () ->
                                probe(FakeRunner.emitting(List.of(), List.of(), 0))
                                        .probe(fileSystemRoot, List.of(), Map.of(), context()));

        assertTrue(
                refused.getMessage().endsWith("has no directory to run in, so it cannot be probed"),
                refused.getMessage());
    }

    @Test
    @DisplayName("the probe rejects a null argument by name")
    void nullArgumentsAreRejectedByName(@TempDir Path staged) throws IOException {
        LoadabilityProbe probe = probe(FakeRunner.emitting(List.of(), List.of(), 0));
        Path executable = file(staged);
        assertAll(
                () ->
                        assertEquals(
                                "processes",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new LoadabilityProbe(
                                                                Nulls.of(ProcessRunner.class),
                                                                classifier(),
                                                                ProbeRecords.LINUX_X86_64,
                                                                BRIEF))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "classifier",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new LoadabilityProbe(
                                                                FakeRunner.emitting(
                                                                        List.of(), List.of(), 0),
                                                                Nulls.of(
                                                                        LoaderOutputClassifier
                                                                                .class),
                                                                ProbeRecords.LINUX_X86_64,
                                                                BRIEF))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "host",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new LoadabilityProbe(
                                                                FakeRunner.emitting(
                                                                        List.of(), List.of(), 0),
                                                                classifier(),
                                                                Nulls.of(HostPlatform.class),
                                                                BRIEF))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "timeout must be positive, but was: PT-1S",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () ->
                                                        new LoadabilityProbe(
                                                                FakeRunner.emitting(
                                                                        List.of(), List.of(), 0),
                                                                classifier(),
                                                                ProbeRecords.LINUX_X86_64,
                                                                Duration.ofSeconds(-1)))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "timeout must be positive, but was: PT0S",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () ->
                                                        new LoadabilityProbe(
                                                                FakeRunner.emitting(
                                                                        List.of(), List.of(), 0),
                                                                classifier(),
                                                                ProbeRecords.LINUX_X86_64,
                                                                Duration.ZERO))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "executable",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        probe.probe(
                                                                Nulls.of(Path.class),
                                                                List.of(),
                                                                Map.of(),
                                                                context()))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "arguments",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        probe.probe(
                                                                executable,
                                                                Nulls.of(List.class),
                                                                Map.of(),
                                                                context()))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "environment",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        probe.probe(
                                                                executable,
                                                                List.of(),
                                                                Nulls.of(Map.class),
                                                                context()))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "context",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        probe.probe(
                                                                executable,
                                                                List.of(),
                                                                Map.of(),
                                                                Nulls.of(ProbeContext.class)))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "failure",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        LoadabilityProbe.wholeChain(
                                                                Nulls.of(Throwable.class)))
                                        .getMessage()),
                () ->
                        assertEquals(
                                ProbeRecords.LINUX_X86_64,
                                probe.host(),
                                "the host is readable, because the composing probe hands it to the"
                                        + " capability stage"));
    }

    private static Path file(Path staged) throws IOException {
        Path executable = staged.resolve("percolator");
        Files.writeString(executable, "not an ELF file, so nothing is refused by architecture");
        return executable;
    }

    private static LoadabilityProbe probe(ProcessRunner runner) {
        return probe(runner, BRIEF);
    }

    private static LoadabilityProbe probe(ProcessRunner runner, Duration timeout) {
        return new LoadabilityProbe(runner, classifier(), ProbeRecords.LINUX_X86_64, timeout);
    }

    private static LoaderOutputClassifier classifier() {
        return new LoaderOutputClassifier(ProbeRecords.LINUX_X86_64, HostRuntimeVersions.unknown());
    }

    private static ProbeContext context() {
        return new ProbeContext("percolator", List.of(), ALTERNATIVES);
    }
}
