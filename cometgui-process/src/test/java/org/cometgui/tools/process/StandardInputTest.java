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

package org.cometgui.tools.process;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.cometgui.domain.ports.ProcessListener;
import org.cometgui.domain.ports.ProcessRunner;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.tools.process.fakes.FakeTools;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link ProcessService#startWithInput}: the bytes reach the tool's standard input and nowhere else
 * -- not its argument array, not the command's rendering -- and the tool then sees end of file
 * (phase 12, design decision P12-17: a keychain tool is handed a secret on standard input).
 */
class StandardInputTest {

    /** A secret the tool is handed, 30 characters. */
    private static final String SECRET = "stdin-seeded-secret-0123456789";

    /** A program that copies its standard input to the file it is given and says how much. */
    private static final String COPIER =
            "import java.nio.file.*;\n"
                    + "public class Copier {\n"
                    + "  public static void main(String[] a) throws Exception {\n"
                    + "    byte[] in = System.in.readAllBytes();\n"
                    + "    Files.write(Path.of(a[0]), in);\n"
                    + "    System.out.println(\"read \" + in.length\n"
                    + "        + \" bytes then end of file\");\n"
                    + "  }\n"
                    + "}\n";

    @Test
    @DisplayName("the tool reads exactly the bytes given, then end of file; no argv holds them")
    void theBytesReachStandardInput(@TempDir Path tmp) throws IOException, InterruptedException {
        Path source = Files.writeString(tmp.resolve("Copier.java"), COPIER);
        Path copy = tmp.resolve("copy.bin");
        ToolCommand command =
                new ToolCommand(
                        List.of(
                                FakeTools.javaExecutable().toString(),
                                source.toString(),
                                copy.toString()),
                        tmp,
                        Map.of());
        RecordingListener listener = new RecordingListener();

        StartedProcess started =
                new ProcessService(Clock.systemUTC())
                        .startWithInput(command, SECRET.getBytes(StandardCharsets.UTF_8), listener);

        assertEquals(0, started.waitForExit());
        assertTrue(listener.exited().await(60, TimeUnit.SECONDS), "the copier never exited");
        assertEquals(List.of("read 30 bytes then end of file"), listener.standardOutput());
        assertArrayEquals(SECRET.getBytes(StandardCharsets.UTF_8), Files.readAllBytes(copy));
        assertFalse(command.argv().toString().contains(SECRET));
        assertFalse(command.toString().contains(SECRET));
        assertFalse(command.displayString().contains(SECRET));
    }

    @Test
    @DisplayName("start without input: a tool that reads standard input sees end of file at once")
    void withoutInputTheToolSeesEndOfFile(@TempDir Path tmp)
            throws IOException, InterruptedException {
        Path source = Files.writeString(tmp.resolve("Copier.java"), COPIER);
        Path copy = tmp.resolve("copy.bin");
        RecordingListener listener = new RecordingListener();

        StartedProcess started =
                new ProcessService(Clock.systemUTC())
                        .start(
                                new ToolCommand(
                                        List.of(
                                                FakeTools.javaExecutable().toString(),
                                                source.toString(),
                                                copy.toString()),
                                        tmp,
                                        Map.of()),
                                listener);

        boolean ended = listener.exited().await(60, TimeUnit.SECONDS);
        if (!ended) {
            started.requestCancellation();
        }
        assertTrue(ended, "the tool waited on a standard input nobody closed");
        assertEquals(List.of("read 0 bytes then end of file"), listener.standardOutput());
    }

    @Test
    @DisplayName("more than 4096 bytes is refused before anything starts")
    void tooMuchInputIsRefused(@TempDir Path tmp) {
        ToolCommand command =
                new ToolCommand(List.of(tmp.resolve("never-started").toString()), tmp, Map.of());

        IllegalArgumentException refused =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                new ProcessService(Clock.systemUTC())
                                        .startWithInput(
                                                command, new byte[4097], new RecordingListener()));

        assertEquals(
                "at most 4096 bytes can be written to a process's standard input, but 4097 were"
                        + " given",
                refused.getMessage());
        assertEquals(4096, ProcessService.MAXIMUM_STANDARD_INPUT);
    }

    @Test
    @DisplayName("exactly 4096 bytes are accepted and written")
    void theLimitItselfIsAccepted(@TempDir Path tmp) throws IOException, InterruptedException {
        Path source = Files.writeString(tmp.resolve("Copier.java"), COPIER);
        Path copy = tmp.resolve("copy.bin");
        byte[] input = new byte[4096];
        java.util.Arrays.fill(input, (byte) 'x');
        RecordingListener listener = new RecordingListener();

        new ProcessService(Clock.systemUTC())
                .startWithInput(
                        new ToolCommand(
                                List.of(
                                        FakeTools.javaExecutable().toString(),
                                        source.toString(),
                                        copy.toString()),
                                tmp,
                                Map.of()),
                        input,
                        listener)
                .waitForExit();

        assertTrue(listener.exited().await(60, TimeUnit.SECONDS));
        assertArrayEquals(input, Files.readAllBytes(copy));
    }

    @Test
    @DisplayName(
            "a runner that was not written to feed standard input refuses, naming the tool only")
    void theDefaultRefuses(@TempDir Path tmp) {
        ProcessRunner plain =
                (command, listener) -> {
                    throw new AssertionError("must not be started");
                };
        ToolCommand command =
                new ToolCommand(List.of("/usr/bin/secret-tool", "store"), tmp, Map.of());
        ProcessListener nobody = new RecordingListener();

        IOException refused =
                assertThrows(
                        IOException.class,
                        () ->
                                plain.startWithInput(
                                        command, SECRET.getBytes(StandardCharsets.UTF_8), nobody));

        assertEquals(
                "this process runner cannot write to a process's standard input, so it did not"
                        + " start /usr/bin/secret-tool",
                refused.getMessage());
        assertThrows(
                NullPointerException.class,
                () -> plain.startWithInput(none(ToolCommand.class), new byte[1], nobody));
        assertThrows(
                NullPointerException.class,
                () -> plain.startWithInput(command, none(byte[].class), nobody));
        assertThrows(
                NullPointerException.class,
                () -> plain.startWithInput(command, new byte[1], none(ProcessListener.class)));
    }

    @Test
    @DisplayName("a tool that stopped reading: the write is absorbed and the pipe still closed")
    void aToolThatStoppedReading() {
        PipeProcess broken = new PipeProcess(true);
        PipeProcess reading = new PipeProcess(false);

        assertFalse(ProcessService.writeStandardInput(broken, new byte[] {1, 2, 3}));
        assertTrue(broken.closed, "the pipe was left open");
        assertTrue(ProcessService.writeStandardInput(reading, new byte[] {1, 2, 3}));
        assertTrue(reading.closed);
        assertArrayEquals(new byte[] {1, 2, 3}, reading.written.toByteArray());
    }

    /** A process whose standard input either takes the bytes or fails as a broken pipe does. */
    private static final class PipeProcess extends Process {

        private final boolean broken;
        private final java.io.ByteArrayOutputStream written = new java.io.ByteArrayOutputStream();
        private boolean closed;

        PipeProcess(boolean broken) {
            this.broken = broken;
        }

        @Override
        public java.io.OutputStream getOutputStream() {
            return new java.io.OutputStream() {
                @Override
                public void write(int oneByte) throws IOException {
                    if (broken) {
                        throw new IOException("Broken pipe");
                    }
                    written.write(oneByte);
                }

                @Override
                public void close() throws IOException {
                    closed = true;
                    if (broken) {
                        throw new IOException("Stream closed");
                    }
                }
            };
        }

        @Override
        public java.io.InputStream getInputStream() {
            return java.io.InputStream.nullInputStream();
        }

        @Override
        public java.io.InputStream getErrorStream() {
            return java.io.InputStream.nullInputStream();
        }

        @Override
        public int waitFor() {
            return 0;
        }

        @Override
        public int exitValue() {
            return 0;
        }

        @Override
        public void destroy() {
            // nothing runs
        }
    }

    /** A typed null that static analysis does not trace into the call. */
    private static <T> T none(Class<T> type) {
        return type.cast(null);
    }
}
