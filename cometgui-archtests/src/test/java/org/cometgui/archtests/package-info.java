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
 * Architecture tests over every product module: the domain must not depend on JavaFX; tool
 * adapters, provenance and hashing must not depend on the UI; the parameter parser and writer must
 * not depend on JavaFX; no cycles between major layers; and ProcessBuilder construction is confined
 * to the process service (R-PROC-02).
 *
 * <p>Filled by phase 01 unit 3, which adds ArchUnit and the rules themselves.
 */
package org.cometgui.archtests;
