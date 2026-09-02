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
 * Durable, atomic file writing: the single way this project puts a document on disk when a reader,
 * a later run, or a power cut must never be able to see it half-written.
 *
 * <p>The provenance manifest and the appendable event log are both written through {@link
 * org.cometgui.provenance.io.AtomicDocumentWriter}, which is what makes R-PROV-05's "finalisation
 * shall be atomic (write-temp-then-rename)" a property of the code rather than a habit each caller
 * has to remember.
 *
 * <p>Filled by phase 04 (hashing and provenance core).
 */
package org.cometgui.provenance.io;
