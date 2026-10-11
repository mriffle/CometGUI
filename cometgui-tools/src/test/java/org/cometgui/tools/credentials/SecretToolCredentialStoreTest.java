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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.cometgui.domain.ports.EnvironmentReader;
import org.cometgui.domain.ports.ProcessRunner;
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
