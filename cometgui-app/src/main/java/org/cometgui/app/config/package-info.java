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
 * The composition root, and the application data directory layout.
 *
 * <p>This package holds the real implementation of every injectable seam {@code R-PROC-01} names,
 * and {@link org.cometgui.app.config.ApplicationServices} is where they are chosen and handed out.
 *
 * <p><strong>It is the only package in the product that may call {@code System.getenv} or {@code
 * System.getProperty}.</strong> Everywhere else reads the environment through {@link
 * org.cometgui.domain.ports.EnvironmentReader}, which is what makes the host-dependent behaviour of
 * {@code R-PLAT-01} testable on a machine that is not the host in question.
 */
package org.cometgui.app.config;
