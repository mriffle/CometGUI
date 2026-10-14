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

package org.cometgui.ui.viewmodel.provenance;

import java.util.Optional;
import org.cometgui.workflow.provenance.ProvenanceView;
import org.cometgui.workflow.provenance.RunListing;

/**
 * One run, opened by {@link ProvenancePort#open}: what the Provenance section shows for it, and the
 * handle the port's exports and verification act on.
 *
 * <p>The application implements it over the read model's loaded run, so that both exports and the
 * verification act on <em>the record that was read and shown</em>, never on one read again later
 * (P13-3). The view-model hands it back to the port and reads nothing else from it.
 */
public interface OpenedRecord {

    /**
     * The run, as the run list named it.
     *
     * @return the listing the run was opened from
     */
    RunListing listing();

    /**
     * What the section shows: the seven parts, every text already redacted.
     *
     * @return the view
     */
    ProvenanceView view();

    /**
     * Why the record could not be read, when it exists and could not be: the reader's message.
     *
     * @return the message, or empty
     */
    Optional<String> recordProblem();

    /**
     * Whether there is a sealed, readable record to export and to verify.
     *
     * @return {@code true} only for a run whose record was read
     */
    boolean canExport();
}
