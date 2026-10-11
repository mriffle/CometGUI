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

/**
 * Where a credential is kept between uses, on this computer: the OS keychain where that is
 * practical and proven, otherwise the session (phase 12, design decisions P12-12 and P12-17).
 *
 * <p>{@link org.cometgui.tools.credentials.CredentialStores} chooses: on Linux the Secret Service
 * through {@code secret-tool} ({@link org.cometgui.tools.credentials.SecretToolCredentialStore},
 * the secret on the tool's standard input, never its argument array) when the tool is installed and
 * a keyring answers; everywhere else, and as the fallback, {@code
 * org.cometgui.domain.secrets.SessionCredentialStore}. The port is {@code
 * org.cometgui.domain.secrets.CredentialStore}.
 *
 * <p>Written by phase 12, work unit 5.
 */
package org.cometgui.tools.credentials;
