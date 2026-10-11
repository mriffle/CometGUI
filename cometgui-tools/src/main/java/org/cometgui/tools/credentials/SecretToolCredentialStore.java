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
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.cometgui.domain.ports.ProcessListener;
import org.cometgui.domain.ports.ProcessRunner;
import org.cometgui.domain.ports.RunningProcess;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.domain.secrets.Credential;
import org.cometgui.domain.secrets.CredentialStore;
import org.cometgui.domain.secrets.SessionSecrets;

/**
 * Credentials in the Linux Secret Service -- the desktop keyring (GNOME Keyring, KWallet,
 * KeePassXC) -- through libsecret's {@code secret-tool}, started by the one process service (phase
 * 12, design decisions P12-12 and P12-17).
 *
 * <p><strong>The secret never touches an argument array.</strong> {@code secret-tool store} reads
 * the secret from its standard input, and that is where it is written ({@link
 * ProcessRunner#startWithInput}); the argument array holds only the label and the two lookup
 * attributes, {@code service cometgui} and {@code account <server URL>}. {@code
 * /proc/<pid>/cmdline} and {@code ps} therefore never show it.
 *
 * <p><strong>Read back, it is registered before anything else touches it.</strong> {@code
 * secret-tool lookup} prints the secret on standard output. The process service hands that line to
 * this class's own listener and to nothing else -- no stage runner, no log -- and the listener's
 * first act is {@link SessionSecrets#admit}, so from the moment the value is in this JVM the one
 * redactor knows it. Standard error is kept for a failure's message and passes through that
 * redactor first.
 *
 * <p>Only ever used after {@link CredentialStores#forThisComputer} has found {@code secret-tool}
 * and seen it answer; anywhere else the session store is used and the scientist is told why.
 *
 * <p><strong>Verified only against a scripted stand-in.</strong> No Secret Service runs on the
 * machine this was built on; the tests run a shell script that records its argument array and its
 * standard input and behaves as {@code secret-tool} documents. Its behaviour against a real keyring
 * is unverified.
 */
public final class SecretToolCredentialStore implements CredentialStore {

    /** The attribute every CometGUI credential is stored under, and its value. */
    public static final String SERVICE = "cometgui";

    /** What the store is, as shown beside the credential field. */
    public static final String DESCRIPTION =
            "your desktop keyring (the Linux Secret Service, through secret-tool)";

    private final Path secretTool;
    private final Path workingDirectory;
    private final Map<String, String> environment;
    private final ProcessRunner processes;
    private final SessionSecrets secrets;
    private final Duration timeout;

    /**
     * A store over one {@code secret-tool}.
     *
     * @param secretTool the absolute path of {@code secret-tool}
     * @param workingDirectory an absolute directory to start it in
     * @param environment what it is given: the session bus address and the like
     * @param processes the process service
     * @param secrets the session's secrets, which every credential read back is admitted to
     * @param timeout how long one call may take
     */
    public SecretToolCredentialStore(
            Path secretTool,
            Path workingDirectory,
            Map<String, String> environment,
            ProcessRunner processes,
            SessionSecrets secrets,
            Duration timeout) {
        this.secretTool = Objects.requireNonNull(secretTool, "secretTool");
        this.workingDirectory = Objects.requireNonNull(workingDirectory, "workingDirectory");
        this.environment = Map.copyOf(Objects.requireNonNull(environment, "environment"));
        this.processes = Objects.requireNonNull(processes, "processes");
        this.secrets = Objects.requireNonNull(secrets, "secrets");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
    }

    @Override
    public String description() {
        return DESCRIPTION;
    }

    @Override
    public boolean persistent() {
        return true;
    }

    @Override
    public void save(String account, Credential credential) throws IOException {
        Objects.requireNonNull(credential, "credential");
        byte[] input = credential.reveal().getBytes(StandardCharsets.UTF_8);
        try {
            Result stored =
                    run(
                            List.of(
                                    secretTool.toString(),
                                    "store",
                                    "--label=CometGUI credential for " + checked(account),
                                    "service",
                                    SERVICE,
                                    "account",
                                    account),
                            input,
                            false);
            if (stored.exitCode() != 0) {
                throw failure("store the credential for " + account, stored);
            }
        } finally {
            Arrays.fill(input, (byte) 0);
        }
    }

    @Override
    public Optional<Credential> find(String account) throws IOException {
        Result found =
                run(
                        List.of(
                                secretTool.toString(),
                                "lookup",
                                "service",
                                SERVICE,
                                "account",
                                checked(account)),
                        null,
                        true);
        if (found.exitCode() == 0 && found.credential().isPresent()) {
            return found.credential();
        }
        if (found.exitCode() == 1 && found.standardError().isEmpty()) {
            return Optional.empty();
        }
        throw failure("look up the credential for " + account, found);
    }

    @Override
    public void forget(String account) throws IOException {
        Result cleared =
                run(
                        List.of(
                                secretTool.toString(),
                                "clear",
                                "service",
                                SERVICE,
                                "account",
                                checked(account)),
                        null,
                        false);
        if (cleared.exitCode() != 0
                && !(cleared.exitCode() == 1 && cleared.standardError().isEmpty())) {
            throw failure("forget the credential for " + account, cleared);
        }
    }

    /**
     * Describes the store without naming anything it holds.
     *
     * @return the tool's path
     */
    @Override
    public String toString() {
        return "SecretToolCredentialStore[" + secretTool + "]";
    }

    /** What one call of the tool produced; standard output is never kept. */
    private record Result(
            int exitCode, List<String> standardError, Optional<Credential> credential) {}

    private Result run(List<String> argv, byte[] input, boolean admitOutput) throws IOException {
        ToolCommand command = new ToolCommand(argv, workingDirectory, environment);
        Listener listener = new Listener(admitOutput);
        RunningProcess process =
                input == null
                        ? processes.start(command, listener)
                        : processes.startWithInput(command, input, listener);
        if (!listener.awaitExit(timeout)) {
            process.requestCancellation();
            throw new IOException(
                    "secret-tool did not answer within " + timeout.toMillis() + " ms");
        }
        return new Result(
                listener.exitCode.get(),
                List.copyOf(listener.standardError),
                Optional.ofNullable(listener.credential.get()));
    }

    private IOException failure(String what, Result result) {
        String said =
                result.standardError().isEmpty()
                        ? ""
                        : ": "
                                + secrets.redactor()
                                        .redactText(String.join(" / ", result.standardError()));
        return new IOException(
                "secret-tool could not "
                        + what
                        + " (exit status "
                        + result.exitCode()
                        + ")"
                        + said);
    }

    private static String checked(String account) {
        Objects.requireNonNull(account, "account");
        if (account.isBlank()) {
            throw new IllegalArgumentException("a credential's account must not be blank");
        }
        return account;
    }

    /**
     * Hears one call. Standard output is never kept: on a lookup its one line is the secret, which
     * is admitted to the session at once; otherwise it is dropped.
     */
    private final class Listener implements ProcessListener {

        private final boolean admitOutput;
        private final List<String> standardError = Collections.synchronizedList(new ArrayList<>());
        private final AtomicReference<Credential> credential = new AtomicReference<>();
        private final AtomicInteger exitCode = new AtomicInteger(-1);
        private final CountDownLatch exited = new CountDownLatch(1);

        Listener(boolean admitOutput) {
            this.admitOutput = admitOutput;
        }

        @Override
        public void onStandardOutput(String line) {
            if (admitOutput && credential.get() == null && !line.isEmpty()) {
                try {
                    credential.set(secrets.admit(line));
                } catch (IllegalArgumentException unusable) {
                    standardError.add(
                            "(the keyring holds a value for this account that is not a usable"
                                    + " credential)");
                }
            }
        }

        @Override
        public void onStandardError(String line) {
            if (standardError.size() < 20) {
                standardError.add(line);
            }
        }

        @Override
        public void onExit(int code) {
            exitCode.set(code);
            exited.countDown();
        }

        boolean awaitExit(Duration limit) throws IOException {
            try {
                return exited.await(limit.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted while waiting for secret-tool", interrupted);
            }
        }
    }
}
