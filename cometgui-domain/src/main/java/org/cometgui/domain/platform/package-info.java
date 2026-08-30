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
 * The host baseline: what CometGUI requires of the machine it is running on, and how that is
 * checked before a run fails in a way the user cannot read.
 *
 * <p>{@code R-PLAT-01} requires the application to declare a minimum host baseline and verify it at
 * startup -- a 64-bit operating system, and on Linux a glibc version sufficient for the tools the
 * user selects. {@code R-PLAT-03} adds that a failure must be an actionable diagnostic naming the
 * host's value and the required value, never an opaque non-zero exit.
 *
 * <p>Everything here is pure: {@link org.cometgui.domain.platform.HostBaselineVerifier} reads the
 * host through {@link org.cometgui.domain.ports.EnvironmentReader} and {@link
 * org.cometgui.domain.platform.GlibcVersionSource}, so every outcome -- including the 32-bit host
 * and the ancient glibc that nobody here has -- is reachable from a test with a fake.
 *
 * <p><strong>The glibc floor is per tool, not per product.</strong> Comet, Percolator and the
 * Limelight converter are built on different distributions and do not require the same version, and
 * the only reliable requirement is the one a specific binary states. This package supplies the
 * mechanism -- parsing, comparison, outcomes and messages; phase 05's runtime probe ({@code
 * R-PLAT-02}) supplies the number by executing the installed binary.
 */
package org.cometgui.domain.platform;
