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
 * PDV adapter: opening a selected spectrum for visualisation and driving the CLI figure generation
 * used by the tests.
 *
 * <p><strong>Phase 05 unit 7 landed the identity probe</strong>, which reads the version out of the
 * JAR's own manifest because PDV has no version option and builds a Swing frame before reading its
 * first argument. Opening a spectrum and driving the figure CLI are still phase 11's.
 */
package org.cometgui.tools.pdv;
