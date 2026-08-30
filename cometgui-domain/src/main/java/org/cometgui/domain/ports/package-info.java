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
 * The injection seams the rest of the product is built against: everything that would otherwise
 * reach the operating system directly.
 *
 * <p>{@code R-PROC-01} requires the clock, the environment reader, the process runner, the
 * downloader, the filesystem abstraction, the run-ID source and the hash service to be injectable,
 * because deterministic tests depend on it. This package holds those ports. There is deliberately
 * <em>no</em> clock interface here: everything that needs the time takes a {@link java.time.Clock},
 * which the JDK already makes injectable and fixable.
 *
 * <p>Almost every type here is an interface with no implementation in this module. That is the
 * point of a port: the domain states what it needs, and the adapter modules -- and only they --
 * hold the code that touches a process, a socket, a digest or a file. The implementations arrive in
 * phase 03 (process runner), phase 04 (hash service) and phase 05 (downloader).
 *
 * <p>The two exceptions are {@link org.cometgui.domain.ports.ToolCommand} and {@link
 * org.cometgui.domain.ports.FileHashes}, which are validated value types rather than seams: what a
 * command line and a pair of checksums are allowed to be is a domain rule, not an adapter's.
 */
package org.cometgui.domain.ports;
