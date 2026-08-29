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
 * Root of the JavaFX layer. JavaFX comes from the modules bundled in the pinned Liberica JDK, not
 * from a Maven dependency. Controllers translate user actions into domain commands and observe
 * state; they contain no process, hashing, download or parsing logic.
 *
 * <p>Filled by phase 02 (application shell and navigation) and by every UI-bearing phase after it.
 */
package org.cometgui.ui;
