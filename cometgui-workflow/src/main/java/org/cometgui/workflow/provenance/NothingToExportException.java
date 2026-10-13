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

package org.cometgui.workflow.provenance;

import java.io.IOException;
import java.io.Serial;

/**
 * Thrown when a run is asked for an export and has no sealed record to export ({@code P13-3}).
 *
 * <p>An export is the loaded {@code provenance.json} rendered again, never a copy of bytes and
 * never something assembled from {@code run.json} and the event log. A run with no sealed record --
 * a crash before finalisation, a run still going, a record that cannot be read -- therefore has
 * nothing to export, and this says so in words a scientist can act on. An {@link IOException}, so
 * that a caller that reports a failed export reports this one with it.
 */
public final class NothingToExportException extends IOException {

    @Serial private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message which run, and why it has no record to export
     */
    NothingToExportException(String message) {
        super(message);
    }
}
