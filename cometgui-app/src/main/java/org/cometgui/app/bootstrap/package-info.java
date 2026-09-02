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
 * Application startup: the {@code javafx.application.Application} subclass and the {@code main}
 * method that launches it.
 *
 * <p>{@link org.cometgui.app.bootstrap.CometGuiApplication} builds the composition root, runs the
 * host-baseline check of {@code R-PLAT-01}, applies the theme, and shows the shell. {@link
 * org.cometgui.app.bootstrap.CometGuiLauncher} is the entry point {@code jpackage} names. The seams
 * themselves are chosen in {@link org.cometgui.app.config}, not here.
 */
package org.cometgui.app.bootstrap;
