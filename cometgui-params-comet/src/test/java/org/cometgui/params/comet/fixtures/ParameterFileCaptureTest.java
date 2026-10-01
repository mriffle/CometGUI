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

package org.cometgui.params.comet.fixtures;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * The capture's failure paths, driven by <strong>constructed</strong> stand-in executables -- tiny
 * {@code /bin/sh} scripts, never presented as Comet -- run through the same {@code ProcessService}
 * path as the real binary.
 */
@EnabledOnOs(
        value = OS.LINUX,
        disabledReason =
                "the stand-ins are /bin/sh scripts, and the capture they exercise is only ever run"
                        + " against the linux/x86-64 Comet binary")
class ParameterFileCaptureTest {

    private static Path script(Path directory, String body) throws IOException {
        Path script = directory.resolve("stand-in.sh");
        Files.writeString(script, "#!/bin/sh\n" + body + "\n", StandardCharsets.UTF_8);
        Files.setPosixFilePermissions(
                script,
                Set.of(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.OWNER_EXECUTE));
        return script;
    }

    @Test
    @DisplayName("the file the executable writes is returned exactly, and it got the mode's option")
    void theWrittenBytesAreReturned(@TempDir Path directory)
            throws IOException, InterruptedException {
        Path executable = script(directory, "printf '%s\\n' \"$1\" > comet.params.new");
        Path run = Files.createDirectory(directory.resolve("run"));

        byte[] written = ParameterFileCapture.capture(executable, CometFixtures.Mode.COMPLETE, run);

        assertArrayEquals("-q\n".getBytes(StandardCharsets.US_ASCII), written);
    }

    @Test
    @DisplayName("a non-zero exit FAILS, naming the exit code and the output")
    void aNonZeroExitFails(@TempDir Path directory) throws IOException, InterruptedException {
        Path executable =
                script(directory, "printf 'x' > comet.params.new; echo broken >&2; exit 3");
        Path run = Files.createDirectory(directory.resolve("run"));

        AssertionError failure =
                assertThrows(
                        AssertionError.class,
                        () ->
                                ParameterFileCapture.capture(
                                        executable, CometFixtures.Mode.DEFAULTS, run));

        String message = failure.getMessage();
        assertTrue(
                message.contains("\"-p\"]")
                        && message.contains("exited 3 instead of 0")
                        && message.contains("[stderr] broken"),
                message);
    }

    @Test
    @DisplayName("exit 0 with no comet.params.new FAILS: the file is the evidence")
    void aMissingOutputFileFails(@TempDir Path directory) throws IOException, InterruptedException {
        Path executable = script(directory, "echo ' Comet version'; exit 0");
        Path run = Files.createDirectory(directory.resolve("run"));

        AssertionError failure =
                assertThrows(
                        AssertionError.class,
                        () ->
                                ParameterFileCapture.capture(
                                        executable, CometFixtures.Mode.COMPLETE, run));

        String message = failure.getMessage();
        assertTrue(
                message.contains("exited 0 but wrote no comet.params.new")
                        && message.contains("[stdout]  Comet version"),
                message);
    }

    @Test
    @DisplayName("a working directory that is not empty is refused before anything runs")
    void aNonEmptyDirectoryIsRefused(@TempDir Path directory)
            throws IOException, InterruptedException {
        Path executable = script(directory, "exit 0");
        Path run = Files.createDirectory(directory.resolve("run"));
        Files.writeString(run.resolve("comet.params.new"), "left over\n");

        AssertionError failure =
                assertThrows(
                        AssertionError.class,
                        () ->
                                ParameterFileCapture.capture(
                                        executable, CometFixtures.Mode.COMPLETE, run));

        assertTrue(failure.getMessage().contains("is not empty"), failure.getMessage());
    }
}
