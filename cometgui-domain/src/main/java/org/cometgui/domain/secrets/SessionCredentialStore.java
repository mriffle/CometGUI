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

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The credential store that always works: credentials held in this JVM's memory for this session
 * and forgotten when the application closes (phase 12, design decision P12-17). The default on
 * every platform, and the fallback wherever an OS keychain is absent or unproven. It writes nothing
 * to disk, ever.
 */
public final class SessionCredentialStore implements CredentialStore {

    /** What the store is, as shown beside the credential field. */
    public static final String DESCRIPTION =
            "this session only (held in memory, forgotten when CometGUI closes)";

    private final Map<String, Credential> held = new ConcurrentHashMap<>();

    /** An empty store. */
    public SessionCredentialStore() {
        // nothing held yet
    }

    @Override
    public String description() {
        return DESCRIPTION;
    }

    @Override
    public boolean persistent() {
        return false;
    }

    @Override
    public void save(String account, Credential credential) {
        held.put(checked(account), Objects.requireNonNull(credential, "credential"));
    }

    @Override
    public Optional<Credential> find(String account) {
        return Optional.ofNullable(held.get(checked(account)));
    }

    @Override
    public void forget(String account) {
        held.remove(checked(account));
    }

    /**
     * Describes the store without naming what it holds.
     *
     * @return a description with a count only
     */
    @Override
    public String toString() {
        return "SessionCredentialStore[accounts=" + held.size() + "]";
    }

    private static String checked(String account) {
        Objects.requireNonNull(account, "account");
        if (account.isBlank()) {
            throw new IllegalArgumentException("a credential's account must not be blank");
        }
        return account;
    }
}
