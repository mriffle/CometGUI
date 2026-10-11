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

package org.cometgui.domain.secrets;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The credentials this session of the application has been handed, and the one redactor that knows
 * all of them.
 *
 * <p>{@link SecretRegistry} and {@link SecretRedactor} are immutable on purpose: a redactor already
 * handed to a writer cannot change under it. A credential, though, arrives while the application is
 * running -- typed into the Limelight section, or read back from the OS keychain -- so something
 * has to hold the registry that grows. This is that one thing. {@link #admit(String)} is the entry
 * point every credential passes through (phase 12, design decision P12-12): it checks the value,
 * registers it, and only then returns the {@link Credential} the rest of the application may carry.
 * {@link #redactor()} is the rule set over everything admitted so far, for a writer to take at the
 * moment it writes.
 *
 * <p>Nothing here is written anywhere: the registry lives in memory for the session and is gone
 * when the application exits. {@link #toString()} reports a count and nothing else.
 *
 * <p>Thread safe: the registry is swapped atomically, and a redactor taken before an admission
 * stays valid (it simply does not know the newer value).
 */
public final class SessionSecrets {

    private final AtomicReference<SecretRegistry> registry;

    /** A session that holds no credential yet. */
    public SessionSecrets() {
        this.registry = new AtomicReference<>(SecretRegistry.empty());
    }

    /**
     * Checks a credential, registers it with the session's registry, and returns it.
     *
     * <p>The checks are the registry's (at least {@value SecretRegistry#MINIMUM_SECRET_LENGTH}
     * characters, not blank) and one of this class's own: no control character, because a value
     * with a line break in it cannot be handed to a keychain tool on one line or travel in an XML
     * attribute intact. No message names the value.
     *
     * @param value the credential's characters, as they entered the application
     * @return the credential, already registered
     * @throws NullPointerException if {@code value} is {@code null}
     * @throws SecretTooShortException if it is shorter than {@value
     *     SecretRegistry#MINIMUM_SECRET_LENGTH} characters
     * @throws IllegalArgumentException if it is blank or holds a control character
     */
    public Credential admit(String value) {
        Objects.requireNonNull(value, "credential");
        for (int index = 0; index < value.length(); index++) {
            if (Character.isISOControl(value.charAt(index))) {
                throw new IllegalArgumentException(
                        "a credential must not contain a control character (one was found at"
                                + " position "
                                + index
                                + " of "
                                + value.length()
                                + ")");
            }
        }
        registry.updateAndGet(current -> current.with(value));
        return new Credential(value);
    }

    /**
     * The one secret rule set over every credential admitted so far.
     *
     * @return a redactor holding the session's registry
     */
    public SecretRedactor redactor() {
        return SecretRedactor.with(registry.get());
    }

    /**
     * How many distinct credentials the session holds.
     *
     * @return the count
     */
    public int size() {
        return registry.get().size();
    }

    /**
     * Describes the session's secrets without disclosing any of them.
     *
     * @return a description safe to put in a log line
     */
    @Override
    public String toString() {
        return "SessionSecrets[secretCount=" + size() + "]";
    }
}
