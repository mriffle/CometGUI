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

package org.cometgui.install.cache;

/**
 * A completion marker is not one this version of CometGUI can read.
 *
 * <p>Unchecked, and that is deliberate: every caller inside this package turns it into an {@link
 * InstallationState#MARKER_UNREADABLE} verdict rather than propagating it, because an unreadable
 * marker is a cache entry to discard and rebuild, not an error to show a scientist. It carries the
 * field that was wrong so that the verdict's detail can say which one.
 */
public final class MarkerFormatException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the rejection.
     *
     * @param message what is wrong with the marker, naming the field
     */
    MarkerFormatException(String message) {
        super(message);
    }

    /**
     * Creates the rejection from an underlying parse failure.
     *
     * @param message what is wrong with the marker
     * @param cause the failure that produced it
     */
    MarkerFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
