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

package org.cometgui.tools.api;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.tools.testing.ScriptedRunner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Collecting one short invocation: both streams, the exit code, the cap and the timeout. */
class ToolRunnerTest {

    private static ToolCommand command(Path directory) {
        return new ToolCommand(List.of("/bin/does-not-matter"), directory, Map.of());
    }

    @Test
    @DisplayName("both streams are collected separately and in order")
    void bothStreamsAreCollected(@TempDir Path directory) throws IOException {
        ScriptedRunner runner =
                new ScriptedRunner()
                        .thenPrints(7, List.of("err 1", "err 2"), List.of("out 1", "out 2"));

        ToolRunOutcome outcome =
                new ToolRunner(runner, Duration.ofSeconds(5)).run(command(directory));

        assertAll(
                () -> assertEquals(OptionalInt.of(7), outcome.exitCode()),
                () -> assertEquals(List.of("out 1", "out 2"), outcome.standardOutput()),
                () -> assertEquals(List.of("err 1", "err 2"), outcome.standardError()),
                () -> assertEquals(1, runner.played()));
    }

    @Test
    @DisplayName("output past the cap is dropped rather than held, and the cap is exactly 500")
    void outputIsCapped(@TempDir Path directory) throws IOException {
        List<String> flood = new ArrayList<>();
        for (int line = 0; line < ToolRunner.MAX_LINES_PER_STREAM + 250; line++) {
            flood.add("line " + line);
        }
        ScriptedRunner runner = new ScriptedRunner().thenPrints(0, flood, flood);

        ToolRunOutcome outcome =
                new ToolRunner(runner, Duration.ofSeconds(5)).run(command(directory));

        assertAll(
                () -> assertEquals(500, ToolRunner.MAX_LINES_PER_STREAM),
                () -> assertEquals(500, outcome.standardError().size()),
                () -> assertEquals(500, outcome.standardOutput().size()),
                () -> assertEquals("line 0", outcome.standardError().get(0)),
                () -> assertEquals("line 499", outcome.standardError().get(499)));
    }

    @Test
    @DisplayName("a run that never finishes is cancelled and comes back with no exit code")
    void aRunThatNeverFinishes(@TempDir Path directory) throws IOException {
        ScriptedRunner runner = new ScriptedRunner().thenNeverFinishes();

        ToolRunOutcome outcome =
                new ToolRunner(runner, Duration.ofMillis(50)).run(command(directory));

        assertAll(
                () -> assertTrue(outcome.timedOut()),
                () -> assertEquals(OptionalInt.empty(), outcome.exitCode()),
                () ->
                        assertTrue(
                                runner.lastProcess().wasCancelled(),
                                "a probe that gave up on a process and left it running would leak"
                                        + " it for the life of the application"));
    }

    @Test
    @DisplayName("a process that cannot be started propagates, because that is not an empty answer")
    void aProcessThatCannotStart(@TempDir Path directory) {
        ScriptedRunner runner = new ScriptedRunner().thenFailsToStart("no such file");

        assertEquals(
                "no such file",
                assertThrows(
                                IOException.class,
                                () ->
                                        new ToolRunner(runner, Duration.ofSeconds(5))
                                                .run(command(directory)))
                        .getMessage());
    }

    @Test
    @DisplayName("the timeout is required to be positive, and is reported")
    void theTimeoutMustBePositive() {
        ScriptedRunner runner = new ScriptedRunner();

        assertAll(
                () ->
                        assertEquals(
                                "timeout must be positive, but was: PT0S",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () -> new ToolRunner(runner, Duration.ZERO))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "timeout must be positive, but was: PT-1S",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () ->
                                                        new ToolRunner(
                                                                runner, Duration.ofSeconds(-1)))
                                        .getMessage()),
                () ->
                        assertEquals(
                                Duration.ofSeconds(3),
                                new ToolRunner(runner, Duration.ofSeconds(3)).timeout()),
                () ->
                        assertEquals(
                                "processes",
                                assertThrows(
                                                NullPointerException.class,
                                                () -> new ToolRunner(null, Duration.ofSeconds(1)))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "timeout",
                                assertThrows(
                                                NullPointerException.class,
                                                () -> new ToolRunner(runner, null))
                                        .getMessage()));
    }

    @Test
    @DisplayName("the command is required, and is passed through unchanged")
    void theCommandIsPassedThrough(@TempDir Path directory) throws IOException {
        ScriptedRunner runner = new ScriptedRunner().thenPrints(0, List.of(), List.of());
        ToolCommand asked =
                new ToolCommand(List.of("/bin/tool", "--flag"), directory, Map.of("A", "b"));

        new ToolRunner(runner, Duration.ofSeconds(5)).run(asked);

        assertAll(
                () -> assertEquals(List.of(asked), runner.commands()),
                () ->
                        assertEquals(
                                "command",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new ToolRunner(
                                                                        runner,
                                                                        Duration.ofSeconds(5))
                                                                .run(null))
                                        .getMessage()));
    }
}
