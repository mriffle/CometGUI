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

import java.io.IOException;
import java.util.Optional;

/**
 * Where the application keeps a credential between uses: the port (phase 12, design decisions
 * P12-12 and P12-17).
 *
 * <p>Credentials are keyed by an <em>account</em> -- for a Limelight submit key, the server's
 * normalised web-application URL -- so one key per server. Two kinds of store exist:
 *
 * <ul>
 *   <li>{@link SessionCredentialStore}, which holds credentials in memory for this session only. It
 *       always works and is the default and the fallback on every platform.
 *   <li>An OS keychain, where one is practical: the Linux Secret Service through {@code
 *       secret-tool}, with the secret on the tool's standard input and never on its argument array
 *       ({@code org.cometgui.tools.credentials}).
 * </ul>
 *
 * <p>Whatever the store, three rules hold, and an implementation is not one unless it keeps them:
 * every credential it returns was made by {@link SessionSecrets#admit(String)}, so it is registered
 * with the session's redactor the moment it enters the application; it never writes a credential to
 * a file under a project, or to any plain file; and no message it produces names the value.
 */
public interface CredentialStore {

    /**
     * What this store is, in words a scientist reads beside the credential field.
     *
     * @return for example "this session only (held in memory, forgotten when CometGUI closes)"
     */
    String description();

    /**
     * Whether a credential saved here outlives the session.
     *
     * @return {@code true} for an OS keychain, {@code false} for the session store
     */
    boolean persistent();

    /**
     * Keeps a credential for an account, replacing any held before.
     *
     * @param account the account, such as a server URL
     * @param credential the credential, already registered
     * @throws IOException if the store could not keep it; the message never names the value
     */
    void save(String account, Credential credential) throws IOException;

    /**
     * The credential kept for an account, admitted to the session (and so registered) before it is
     * returned.
     *
     * @param account the account
     * @return the credential, or empty when none is kept
     * @throws IOException if the store could not be read; the message never names the value
     */
    Optional<Credential> find(String account) throws IOException;

    /**
     * Forgets the credential kept for an account, if any.
     *
     * @param account the account
     * @throws IOException if the store could not forget it
     */
    void forget(String account) throws IOException;
}
