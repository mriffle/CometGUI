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

package org.cometgui.tools.credentials;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.cometgui.domain.ports.EnvironmentReader;
import org.cometgui.domain.ports.ProcessListener;
import org.cometgui.domain.ports.ProcessRunner;
import org.cometgui.domain.ports.RunningProcess;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.domain.secrets.Credential;
import org.cometgui.domain.secrets.SessionCredentialStore;
import org.cometgui.domain.secrets.SessionSecrets;
import org.cometgui.domain.tools.HostArchitecture;
import org.cometgui.domain.tools.HostOperatingSystem;
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.tools.process.ProcessService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The Linux Secret Service store and the choice of store, against a <strong>scripted
 * stand-in</strong> for {@code secret-tool} (a bash script beside this test that records its
 * argument array, its standard input and the bus variable it was given) started through the real
 * process service. Nothing here talks to a keyring: none runs on this machine. What is proved is
 * where the secret travels -- on standard input, never on the argument array -- and that a secret
 * read back is registered before it is returned.
 */
class SecretToolCredentialStoreTest {

    /** The seeded secret, 31 characters. */
    private static final String SECRET = "LLkeyring-seeded-secret-0123456";

    private static final String ACCOUNT = "https://limelight.example.org/limelight";

    private static final Map<String, String> BUS =
            Map.of("DBUS_SESSION_BUS_ADDRESS", "unix:path=/run/user/1000/bus");

    private static final ProcessRunner PROCESSES = new ProcessService(Clock.systemUTC());

    /** The stand-in, copied into a directory of its own with an empty keyring. */
    static Path standIn(Path directory) throws IOException {
        Files.createDirectories(directory.resolve("keyring"));
        Path tool = directory.resolve("secret-tool");
        try (InputStream script =
                SecretToolCredentialStoreTest.class.getResourceAsStream(
                        "secret-tool-stand-in.sh")) {
            Files.copy(script, tool);
        }
        Files.setPosixFilePermissions(tool, PosixFilePermissions.fromString("rwx------"));
        return tool;
    }

    private static SecretToolCredentialStore store(Path tool, SessionSecrets secrets) {
        return new SecretToolCredentialStore(
                tool, folder(tool), BUS, PROCESSES, secrets, Duration.ofSeconds(30));
    }

    private static Path folder(Path tool) {
        return java.util.Objects.requireNonNull(tool.getParent(), "the stand-in's directory");
    }

    private static String record(Path tool, String name) throws IOException {
        Path file = folder(tool).resolve(name);
        return Files.exists(file) ? Files.readString(file, StandardCharsets.UTF_8) : "";
    }

    @Test
    @DisplayName("store: the secret is in the tool's standard input and not in its argv")
    void theSecretTravelsOnStandardInput(@TempDir Path tmp) throws IOException {
        Path tool = standIn(tmp.resolve("bin"));
        SessionSecrets secrets = new SessionSecrets();

        store(tool, secrets).save(ACCOUNT, secrets.admit(SECRET));

        assertEquals(SECRET + "\n--\n", record(tool, "stdin.record"));
        String argv = record(tool, "argv.record");
        assertFalse(argv.contains(SECRET), "the secret is on the argument array");
        assertFalse(argv.contains(SECRET.substring(0, 12)));
        assertEquals(
                "store\n--label=CometGUI credential for "
                        + ACCOUNT
                        + "\nservice\ncometgui\naccount\n"
                        + ACCOUNT
                        + "\n--\n",
                argv);
        assertEquals("unix:path=/run/user/1000/bus\n", record(tool, "env.record"));
    }

    @Test
    @DisplayName("lookup: the secret read back is registered with the session before it returns")
    void aSecretReadBackIsRegistered(@TempDir Path tmp) throws IOException {
        Path tool = standIn(tmp.resolve("bin"));
        SessionSecrets writer = new SessionSecrets();
        store(tool, writer).save(ACCOUNT, writer.admit(SECRET));
        SessionSecrets reader = new SessionSecrets();

        Credential found = store(tool, reader).find(ACCOUNT).orElseThrow();

        assertEquals(SECRET, found.reveal());
        assertEquals(1, reader.size());
        assertEquals("k=[REDACTED]", reader.redactor().redactText("k=" + SECRET));
        assertEquals(
                "lookup\nservice\ncometgui\naccount\n" + ACCOUNT + "\n--\n",
                record(tool, "argv.record")
                        .substring(record(tool, "argv.record").indexOf("lookup")));
        assertFalse(record(tool, "argv.record").contains(SECRET));
    }

    @Test
    @DisplayName("nothing kept: empty; forgotten: empty; forgetting nothing is not an error")
    void absentAndForgotten(@TempDir Path tmp) throws IOException {
        Path tool = standIn(tmp.resolve("bin"));
        SessionSecrets secrets = new SessionSecrets();
        SecretToolCredentialStore store = store(tool, secrets);

        assertEquals(Optional.empty(), store.find(ACCOUNT));
        store.forget(ACCOUNT);
        store.save(ACCOUNT, secrets.admit(SECRET));
        assertTrue(store.find(ACCOUNT).isPresent());
        store.forget(ACCOUNT);
        assertEquals(Optional.empty(), store.find(ACCOUNT));
        assertTrue(store.persistent());
        assertEquals(
                "your desktop keyring (the Linux Secret Service, through secret-tool)",
                store.description());
        assertEquals("SecretToolCredentialStore[" + tool + "]", store.toString());
    }

    @Test
    @DisplayName("no keyring: every call fails with the tool's own words, never the secret")
    void noKeyring(@TempDir Path tmp) throws IOException {
        Path tool = standIn(tmp.resolve("bin"));
        Files.createFile(folder(tool).resolve("no-keyring"));
        SessionSecrets secrets = new SessionSecrets();
        SecretToolCredentialStore store = store(tool, secrets);
        Credential credential = secrets.admit(SECRET);

        IOException saving = assertThrows(IOException.class, () -> store.save(ACCOUNT, credential));
        IOException finding = assertThrows(IOException.class, () -> store.find(ACCOUNT));
        IOException forgetting = assertThrows(IOException.class, () -> store.forget(ACCOUNT));

        assertEquals(
                "secret-tool could not store the credential for "
                        + ACCOUNT
                        + " (exit status 1): Cannot autolaunch D-Bus without X11 $DISPLAY",
                saving.getMessage());
        assertEquals(
                "secret-tool could not look up the credential for "
                        + ACCOUNT
                        + " (exit status 1): Cannot autolaunch D-Bus without X11 $DISPLAY",
                finding.getMessage());
        assertTrue(forgetting.getMessage().startsWith("secret-tool could not forget"));
        assertFalse(saving.getMessage().contains(SECRET));
    }

    @Test
    @DisplayName("a tool that does not answer in time is given up on")
    void aToolThatHangs(@TempDir Path tmp) throws IOException {
        Path tool = tmp.resolve("secret-tool");
        Files.writeString(tool, "#!/bin/bash\nexec /bin/sleep 30\n");
        Files.setPosixFilePermissions(tool, PosixFilePermissions.fromString("rwx------"));
        SecretToolCredentialStore store =
                new SecretToolCredentialStore(
                        tool, tmp, BUS, PROCESSES, new SessionSecrets(), Duration.ofMillis(300));

        IOException hung = assertThrows(IOException.class, () -> store.find(ACCOUNT));

        assertEquals("secret-tool did not answer within 300 ms", hung.getMessage());
    }

    /*
     * INTERRUPTED WHILE THE REAL TOOL HANGS (Phase 16 unit 4).  Until then an interrupted call
     * returned its failure and left secret-tool running: nothing asked the process service to end
     * it, so a keyring prompt nobody would answer outlived the call that started it.  The stand-in
     * records its own process identifier and then becomes a sleep that would last five minutes;
     * the test interrupts the calling thread and then watches that process, by its handle, until
     * it has ended -- a bounded wait on the process's own state, not a pause of a guessed length.
     */
    @Test
    @DisplayName(
            "interrupted while the tool hangs: the tool is ended through the process service, the"
                    + " call fails as an interrupted one, and the interrupt is kept")
    void anInterruptedCallEndsTheTool(@TempDir Path tmp) throws Exception {
        Path tool = tmp.resolve("secret-tool");
        Path pidRecord = tmp.resolve("pid.record");
        Files.writeString(
                tool,
                "#!/bin/bash\n"
                        + "printf '%s\\n' \"$$\" > \"${0%/*}/pid.record\"\n"
                        + "exec /bin/sleep 300\n");
        Files.setPosixFilePermissions(tool, PosixFilePermissions.fromString("rwx------"));
        SecretToolCredentialStore store =
                new SecretToolCredentialStore(
                        tool, tmp, BUS, PROCESSES, new SessionSecrets(), Duration.ofSeconds(120));
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        AtomicBoolean interruptKept = new AtomicBoolean();
        Thread caller =
                new Thread(
                        () -> {
                            try {
                                store.find(ACCOUNT);
                            } catch (IOException | RuntimeException failed) {
                                thrown.set(failed);
                            }
                            interruptKept.set(Thread.currentThread().isInterrupted());
                        },
                        "interrupted-secret-tool-caller");
        ProcessHandle child = null;
        try {
            caller.start();
            child = startedTool(pidRecord, Duration.ofSeconds(30));
            boolean aliveBeforeTheInterrupt = child.isAlive();

            caller.interrupt();
            caller.join(TimeUnit.SECONDS.toMillis(30));
            boolean callerReturned = !caller.isAlive();
            boolean childEnded = ended(child, Duration.ofSeconds(30));

            assertTrue(aliveBeforeTheInterrupt, "the stand-in was not running to be interrupted");
            assertTrue(callerReturned, "the interrupted call did not return within 30 s");
            assertTrue(
                    childEnded,
                    "secret-tool (pid "
                            + child.pid()
                            + ") was still running 30 s after the call that started it was"
                            + " interrupted");
            assertInstanceOf(IOException.class, thrown.get());
            assertEquals("interrupted while waiting for secret-tool", thrown.get().getMessage());
            assertInstanceOf(InterruptedException.class, thrown.get().getCause());
            assertTrue(interruptKept.get(), "the calling thread's interrupt was swallowed");
        } finally {
            /* Whatever the product did, this test leaves no five-minute sleep behind it. */
            if (child != null) {
                child.destroyForcibly();
            }
            caller.interrupt();
            caller.join(TimeUnit.SECONDS.toMillis(30));
        }
    }

    /** The stand-in's process, once it has recorded its identifier; waits on that record. */
    private static ProcessHandle startedTool(Path pidRecord, Duration limit)
            throws IOException, InterruptedException {
        long deadline = System.nanoTime() + limit.toNanos();
        while (System.nanoTime() < deadline) {
            if (Files.exists(pidRecord)) {
                String recorded = Files.readString(pidRecord, StandardCharsets.UTF_8).strip();
                if (!recorded.isEmpty()) {
                    return ProcessHandle.of(Long.parseLong(recorded))
                            .orElseThrow(
                                    () ->
                                            new AssertionError(
                                                    "the stand-in recorded pid "
                                                            + recorded
                                                            + " and no such process exists"));
                }
            }
            Thread.sleep(10);
        }
        throw new AssertionError("the stand-in did not record its pid within " + limit);
    }

    /** Whether a process has ended, waiting on its own exit for at most {@code limit}. */
    private static boolean ended(ProcessHandle process, Duration limit)
            throws InterruptedException {
        try {
            process.onExit().get(limit.toMillis(), TimeUnit.MILLISECONDS);
            return true;
        } catch (TimeoutException stillRunning) {
            return false;
        } catch (ExecutionException impossible) {
            throw new AssertionError("a process handle's exit future does not fail", impossible);
        }
    }

    @Test
    @DisplayName("a blank account is refused before anything starts")
    void blankAccounts(@TempDir Path tmp) throws IOException {
        Path tool = standIn(tmp.resolve("bin"));
        SessionSecrets secrets = new SessionSecrets();
        SecretToolCredentialStore store = store(tool, secrets);
        Credential credential = secrets.admit(SECRET);

        assertThrows(IllegalArgumentException.class, () -> store.find(" "));
        assertThrows(IllegalArgumentException.class, () -> store.save("", credential));
        assertThrows(IllegalArgumentException.class, () -> store.forget("\t"));
        assertEquals("", record(tool, "argv.record"));
    }

    // ------------------------------------------------- against a stand-in in this JVM --

    @Test
    @DisplayName("store: the copy of the secret handed to the tool is zeroed, answered or not")
    void theHandedCopyIsZeroed(@TempDir Path tmp) throws IOException {
        SessionSecrets secrets = new SessionSecrets();
        byte[] expected = SECRET.getBytes(StandardCharsets.UTF_8);
        InProcessTool stored = InProcessTool.answering(0, List.of());
        InProcessTool refused = InProcessTool.answering(1, List.of("the keyring is locked"));
        InProcessTool silent = InProcessTool.silent();

        inProcess(tmp, stored, secrets, 30_000).save(ACCOUNT, secrets.admit(SECRET));
        IOException refusal =
                assertThrows(
                        IOException.class,
                        () ->
                                inProcess(tmp, refused, secrets, 30_000)
                                        .save(ACCOUNT, secrets.admit(SECRET)));
        IOException hung =
                assertThrows(
                        IOException.class,
                        () ->
                                inProcess(tmp, silent, secrets, 50)
                                        .save(ACCOUNT, secrets.admit(SECRET)));

        for (InProcessTool tool : List.of(stored, refused, silent)) {
            assertArrayEquals(expected, tool.inputAsHanded, "what the tool was handed");
            assertArrayEquals(
                    new byte[expected.length], tool.handed.get(0), "left in memory afterwards");
        }
        assertEquals(
                "secret-tool could not store the credential for "
                        + ACCOUNT
                        + " (exit status 1): the keyring is locked",
                refusal.getMessage());
        assertEquals("secret-tool did not answer within 50 ms", hung.getMessage());
    }

    @Test
    @DisplayName("a tool that does not answer in time is cancelled, not left running")
    void aSilentToolIsCancelled(@TempDir Path tmp) {
        InProcessTool silent = InProcessTool.silent();
        SecretToolCredentialStore store = inProcess(tmp, silent, new SessionSecrets(), 50);

        IOException hung = assertThrows(IOException.class, () -> store.find(ACCOUNT));

        assertEquals("secret-tool did not answer within 50 ms", hung.getMessage());
        assertEquals(1, silent.cancellations, "cancellations requested");
    }

    @Test
    @DisplayName("a failure quotes the tool's first twenty lines of standard error, and no more")
    void twentyLinesOfStandardError(@TempDir Path tmp) {
        List<String> said = new ArrayList<>();
        for (int line = 1; line <= 25; line++) {
            said.add("complaint " + line);
        }
        SecretToolCredentialStore store =
                inProcess(tmp, InProcessTool.answering(2, said), new SessionSecrets(), 30_000);

        IOException failed = assertThrows(IOException.class, () -> store.find(ACCOUNT));

        assertEquals(
                "secret-tool could not look up the credential for "
                        + ACCOUNT
                        + " (exit status 2): "
                        + String.join(" / ", said.subList(0, 20)),
                failed.getMessage());
    }

    @Test
    @DisplayName(
            "interrupted while waiting for the tool: refused, the tool is cancelled once, and the"
                    + " interrupt is kept")
    void anInterruptedWaitKeepsTheInterrupt(@TempDir Path tmp) {
        InProcessTool silent = InProcessTool.silent();
        SecretToolCredentialStore store = inProcess(tmp, silent, new SessionSecrets(), 30_000);

        Thread.currentThread().interrupt();
        IOException interrupted;
        boolean stillInterrupted;
        try {
            interrupted = assertThrows(IOException.class, () -> store.find(ACCOUNT));
        } finally {
            stillInterrupted = Thread.interrupted();
        }

        assertEquals("interrupted while waiting for secret-tool", interrupted.getMessage());
        assertInstanceOf(InterruptedException.class, interrupted.getCause());
        assertTrue(stillInterrupted, "the thread's interrupt was swallowed");
        assertEquals(1, silent.cancellations, "cancellations requested of the hanging tool");
    }

    private static SecretToolCredentialStore inProcess(
            Path tmp, InProcessTool tool, SessionSecrets secrets, long timeoutMillis) {
        return new SecretToolCredentialStore(
                tmp.resolve("secret-tool"),
                tmp,
                BUS,
                tool,
                secrets,
                Duration.ofMillis(timeoutMillis));
    }

    /**
     * {@code secret-tool} played in this JVM: it keeps the very array it is handed (and a copy of
     * it as handed), answers at once with an exit status and some standard error -- or never
     * answers -- and counts cancellations.
     */
    private static final class InProcessTool implements ProcessRunner, RunningProcess {

        private final boolean answers;
        private final int exitCode;
        private final List<String> standardError;

        /** The caller's own array, kept by reference on purpose: the store must zero it. */
        private final List<byte[]> handed = new ArrayList<>();

        private byte[] inputAsHanded;
        private int cancellations;

        private InProcessTool(boolean answers, int exitCode, List<String> standardError) {
            this.answers = answers;
            this.exitCode = exitCode;
            this.standardError = List.copyOf(standardError);
        }

        static InProcessTool answering(int exitCode, List<String> standardError) {
            return new InProcessTool(true, exitCode, standardError);
        }

        static InProcessTool silent() {
            return new InProcessTool(false, -1, List.of());
        }

        @Override
        public RunningProcess start(ToolCommand command, ProcessListener listener) {
            if (answers) {
                standardError.forEach(listener::onStandardError);
                listener.onExit(exitCode);
            }
            return this;
        }

        @Override
        public RunningProcess startWithInput(
                ToolCommand command, byte[] standardInput, ProcessListener listener) {
            handed.add(standardInput);
            inputAsHanded = standardInput.clone();
            return start(command, listener);
        }

        @Override
        public boolean isAlive() {
            return !answers && cancellations == 0;
        }

        @Override
        public int waitForExit() {
            return exitCode;
        }

        @Override
        public void requestCancellation() {
            cancellations++;
        }
    }

    // ------------------------------------------------------------------- the choice --

    @Test
    @DisplayName("Linux with secret-tool and a keyring that answers: the keyring, probed once")
    void linuxWithAKeyring(@TempDir Path tmp) throws IOException {
        Path tool = standIn(tmp.resolve("bin"));
        CredentialStores.Choice choice =
                CredentialStores.forThisComputer(
                        LINUX,
                        environment(
                                Map.of(
                                        "PATH",
                                        "::relative:" + tmp.resolve("empty") + ":" + folder(tool),
                                        "DBUS_SESSION_BUS_ADDRESS",
                                        "unix:path=/run/user/1000/bus",
                                        "HOME",
                                        "/home/scientist",
                                        "SECRET_TOKEN",
                                        "never-passed")),
                        PROCESSES,
                        new SessionSecrets(),
                        tmp);

        assertInstanceOf(SecretToolCredentialStore.class, choice.store());
        assertEquals(
                "The key is kept in your desktop keyring (the Linux Secret Service, through"
                        + " secret-tool).",
                choice.explanation());
        assertEquals(
                "lookup\nservice\ncometgui\naccount\ncometgui-availability-probe\n--\n",
                record(tool, "argv.record"));
    }

    @Test
    @DisplayName("Linux whose keyring does not answer: the session store, and why")
    void linuxWithoutAKeyring(@TempDir Path tmp) throws IOException {
        Path tool = standIn(tmp.resolve("bin"));
        Files.createFile(folder(tool).resolve("no-keyring"));
        CredentialStores.Choice choice =
                CredentialStores.forThisComputer(
                        LINUX,
                        environment(
                                Map.of(
                                        "PATH",
                                        folder(tool).toString(),
                                        "DBUS_SESSION_BUS_ADDRESS",
                                        "unix:path=/x")),
                        PROCESSES,
                        new SessionSecrets(),
                        tmp);

        assertInstanceOf(SessionCredentialStore.class, choice.store());
        assertEquals(
                "The key is kept for this session only: the desktop keyring did not answer"
                        + " (secret-tool could not look up the credential for"
                        + " cometgui-availability-probe (exit status 1): Cannot autolaunch D-Bus"
                        + " without X11 $DISPLAY).",
                choice.explanation());
    }

    @Test
    @DisplayName("Linux without secret-tool, or without a session bus: the session store, and why")
    void linuxWithoutTheTool(@TempDir Path tmp) throws IOException {
        Path tool = standIn(tmp.resolve("bin"));
        Path notExecutable = Files.createDirectories(tmp.resolve("plain"));
        Files.writeString(notExecutable.resolve("secret-tool"), "not executable");

        CredentialStores.Choice none =
                CredentialStores.forThisComputer(
                        LINUX,
                        environment(Map.of("PATH", notExecutable.toString())),
                        PROCESSES,
                        new SessionSecrets(),
                        tmp);
        CredentialStores.Choice noBus =
                CredentialStores.forThisComputer(
                        LINUX,
                        environment(Map.of("PATH", folder(tool).toString())),
                        PROCESSES,
                        new SessionSecrets(),
                        tmp);
        CredentialStores.Choice noPath =
                CredentialStores.forThisComputer(
                        LINUX, environment(Map.of()), PROCESSES, new SessionSecrets(), tmp);

        assertEquals(
                "The key is kept for this session only: secret-tool (libsecret-tools) is not"
                        + " installed, so the desktop keyring cannot be reached.",
                none.explanation());
        assertEquals(
                "The key is kept for this session only: there is no desktop session bus, so the"
                        + " desktop keyring cannot be reached.",
                noBus.explanation());
        assertInstanceOf(SessionCredentialStore.class, noBus.store());
        assertInstanceOf(SessionCredentialStore.class, noPath.store());
        assertEquals("", record(tool, "argv.record"), "nothing was started");
    }

    @Test
    @DisplayName("macOS and Windows: the session store, saying the keychain is unverified")
    void macOsAndWindows(@TempDir Path tmp) {
        CredentialStores.Choice mac =
                CredentialStores.forThisComputer(
                        new HostPlatform(HostOperatingSystem.MACOS, HostArchitecture.AARCH64),
                        environment(Map.of()),
                        PROCESSES,
                        new SessionSecrets(),
                        tmp);
        CredentialStores.Choice windows =
                CredentialStores.forThisComputer(
                        new HostPlatform(HostOperatingSystem.WINDOWS, HostArchitecture.X86_64),
                        environment(Map.of()),
                        PROCESSES,
                        new SessionSecrets(),
                        tmp);

        assertInstanceOf(SessionCredentialStore.class, mac.store());
        assertEquals(
                "On macOS the key is kept for this session only: storing it in the keychain"
                        + " without exposing it on a command line has not been verified.",
                mac.explanation());
        assertInstanceOf(SessionCredentialStore.class, windows.store());
        assertEquals(
                "On Windows the key is kept for this session only: storing it in the Credential"
                        + " Manager without exposing it on a command line has not been verified.",
                windows.explanation());
        assertEquals(
                List.of(
                        "DBUS_SESSION_BUS_ADDRESS",
                        "XDG_RUNTIME_DIR",
                        "HOME",
                        "DISPLAY",
                        "WAYLAND_DISPLAY",
                        "LANG"),
                CredentialStores.PASSED_VARIABLES);
    }

    private static final HostPlatform LINUX =
            new HostPlatform(HostOperatingSystem.LINUX, HostArchitecture.X86_64);

    private static EnvironmentReader environment(Map<String, String> variables) {
        return new EnvironmentReader() {
            @Override
            public Optional<String> environmentVariable(String name) {
                return Optional.ofNullable(variables.get(name));
            }

            @Override
            public Optional<String> systemProperty(String name) {
                return Optional.empty();
            }
        };
    }
}
