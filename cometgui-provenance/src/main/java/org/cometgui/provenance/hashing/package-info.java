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
 * Single-pass MD5 plus SHA-256 hashing of files of any size, with the hash cache and its
 * invalidation policy. SHA-256 is the trust mechanism; MD5 is recorded for provenance only
 * (R-SEC-02).
 *
 * <p>Filled by phase 04 (hashing and provenance core).
 */
package org.cometgui.provenance.hashing;
