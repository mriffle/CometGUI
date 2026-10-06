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

package org.cometgui.domain.project;

/**
 * What {@link SchemaVersionPolicy} decides about a document's declared schema version.
 *
 * <p>Three answers and no fourth: there is no "read what you can", because {@code R-RUN-04} forbids
 * partial parsing of a newer document and a project file read halfway is a project silently
 * misread.
 */
public enum SchemaVerdict {

    /** The document declares the version this build writes: read it. */
    CURRENT,

    /**
     * The document declares an older version. It is refused: no migration is registered, because
     * version 1 is the first published format and nothing older was ever written.
     */
    OLDER,

    /**
     * The document declares a newer version. It is refused before any other member is read, and the
     * file is left exactly as it was.
     */
    NEWER
}
