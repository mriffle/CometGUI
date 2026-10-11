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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.cometgui.domain.ports.EnvironmentReader;
import org.cometgui.domain.ports.ProcessRunner;
import org.cometgui.domain.secrets.CredentialStore;
import org.cometgui.domain.secrets.SessionCredentialStore;
import org.cometgui.domain.secrets.SessionSecrets;
import org.cometgui.domain.tools.HostOperatingSystem;
import org.cometgui.domain.tools.HostPlatform;

/**
 * Which credential store this computer gets, and why (phase 12, design decision P12-17).
 *
 * <ul>
 *   <li><strong>Linux</strong>: the Secret Service through {@code secret-tool}, when {@code
 *       secret-tool} is on the {@code PATH}, a session bus is announced, and a probe lookup is
 *       answered cleanly. Otherwise the session store, with the reason.
 *   <li><strong>macOS</strong>: the session store. The keychain route that keeps the secret off
 *       every argument array is {@code security -i} with commands on standard input; how a secret
 *       must be quoted inside that command language could not be tested without a Mac, and a
 *       quoting mistake there is a credential written wrongly or echoed. It is not attempted.
 *   <li><strong>Windows</strong>: the session store. {@code cmdkey} puts the secret on its argument
 *       array, and the Credential Manager API needs native access that has not been tested.
 * </ul>
 *
 * <p>The session store is never worse than "forgotten when CometGUI closes"; the scientist is told
 * which store holds their key, and why it is not the keychain when it is not.
 */
public final class CredentialStores {

    /** How long the probe and each later call of {@code secret-tool} may take. */
    public static final Duration SECRET_TOOL_TIMEOUT = Duration.ofSeconds(20);

    /** The account the availability probe looks up, which nothing ever stores. */
    static final String PROBE_ACCOUNT = "cometgui-availability-probe";

    /** The variables {@code secret-tool} is given, when this computer has them. */
    static final List<String> PASSED_VARIABLES =
            List.of(
                    "DBUS_SESSION_BUS_ADDRESS",
                    "XDG_RUNTIME_DIR",
                    "HOME",
                    "DISPLAY",
                    "WAYLAND_DISPLAY",
                    "LANG");

    private CredentialStores() {}

    /**
     * The store this computer gets, and the explanation shown beside the credential field.
     *
     * @param store the store
     * @param explanation why it is this store, in the scientist's words
     */
    public record Choice(CredentialStore store, String explanation) {

        /**
         * Checks the choice.
         *
         * @throws NullPointerException if a component is {@code null}
         */
        public Choice {
            Objects.requireNonNull(store, "store");
            Objects.requireNonNull(explanation, "explanation");
        }
    }

    /**
     * Chooses the store for this computer, probing the keychain where there is one to probe.
     *
     * @param host this computer
     * @param environment its environment, for {@code PATH} and the session bus
     * @param processes the process service
     * @param secrets the session's secrets
     * @param workingDirectory an absolute directory to start {@code secret-tool} in
     * @return the store and why
     */
    public static Choice forThisComputer(
            HostPlatform host,
            EnvironmentReader environment,
            ProcessRunner processes,
            SessionSecrets secrets,
            Path workingDirectory) {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(processes, "processes");
        Objects.requireNonNull(secrets, "secrets");
        Objects.requireNonNull(workingDirectory, "workingDirectory");
        HostOperatingSystem system = host.operatingSystem();
        if (system == HostOperatingSystem.MACOS) {
            return session(
                    "On macOS the key is kept for this session only: storing it in the keychain"
                            + " without exposing it on a command line has not been verified.");
        }
        if (system != HostOperatingSystem.LINUX) {
            return session(
                    "On Windows the key is kept for this session only: storing it in the"
                            + " Credential Manager without exposing it on a command line has not"
                            + " been verified.");
        }
        Optional<Path> tool = onPath(environment, "secret-tool");
        if (tool.isEmpty()) {
            return session(
                    "The key is kept for this session only: secret-tool (libsecret-tools) is not"
                            + " installed, so the desktop keyring cannot be reached.");
        }
        Map<String, String> passed = new LinkedHashMap<>();
        for (String name : PASSED_VARIABLES) {
            environment.environmentVariable(name).ifPresent(value -> passed.put(name, value));
        }
        if (!passed.containsKey("DBUS_SESSION_BUS_ADDRESS")) {
            return session(
                    "The key is kept for this session only: there is no desktop session bus, so"
                            + " the desktop keyring cannot be reached.");
        }
        SecretToolCredentialStore keyring =
                new SecretToolCredentialStore(
                        tool.get(),
                        workingDirectory,
                        passed,
                        processes,
                        secrets,
                        SECRET_TOOL_TIMEOUT);
        try {
            keyring.find(PROBE_ACCOUNT);
        } catch (IOException | RuntimeException unanswered) {
            return session(
                    "The key is kept for this session only: the desktop keyring did not answer"
                            + " ("
                            + secrets.redactor().redactText(String.valueOf(unanswered.getMessage()))
                            + ").");
        }
        return new Choice(
                keyring, "The key is kept in " + SecretToolCredentialStore.DESCRIPTION + ".");
    }

    private static Choice session(String explanation) {
        return new Choice(new SessionCredentialStore(), explanation);
    }

    /**
     * The first executable regular file with this name in a directory of {@code PATH}.
     *
     * @param environment the environment
     * @param name the file's name
     * @return its absolute path, or empty
     */
    static Optional<Path> onPath(EnvironmentReader environment, String name) {
        String path = environment.environmentVariable("PATH").orElse("");
        for (String entry : path.split(":", -1)) {
            if (entry.isEmpty()) {
                continue;
            }
            try {
                Path candidate = Path.of(entry).resolve(name);
                if (candidate.isAbsolute()
                        && Files.isRegularFile(candidate)
                        && Files.isExecutable(candidate)) {
                    return Optional.of(candidate);
                }
            } catch (InvalidPathException unusable) {
                // an entry that is not a path names no tool
            }
        }
        return Optional.empty();
    }
}
