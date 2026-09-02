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

package org.cometgui.domain.tools;

/**
 * Where an install has got to.
 *
 * <p>The first six are the specification's atomic install sequence, collapsed to the steps a user
 * can see: download, verify the SHA-256, extract, install atomically into the cache, probe. The
 * last three are terminal, and they are three rather than one because cancelling is not failing --
 * a user who cancelled a 99 MB PDV download does not need an error, and a run that reports one has
 * lost the difference.
 *
 * <p>Declaration order is the order they occur in, so a listener can tell a later phase from an
 * earlier one without a table.
 */
public enum InstallPhase {

    /** Fetching the artefact to a temporary file. */
    DOWNLOADING(false),

    /**
     * Checking the downloaded bytes against the manifest's expected SHA-256. {@code R-SEC-02} makes
     * this mandatory before anything is executed; MD5 is recorded for provenance and is never the
     * trust mechanism.
     */
    VERIFYING(false),

    /** Unpacking the artefact with the extractor its declared {@link ArtefactKind} selects. */
    EXTRACTING(false),

    /**
     * Moving the verified contents into the tool cache and applying the platform fix-ups --
     * executable bits, macOS quarantine removal.
     */
    INSTALLING(false),

    /** Running the three probe stages against the installed binary ({@code R-TOOL-06}). */
    PROBING(false),

    /** Finished: installed, probed and usable. */
    DONE(true),

    /** Stopped because the user asked. Not a failure, and never reported as one. */
    CANCELLED(true),

    /** Stopped because something went wrong. The reason belongs with the offer, not here. */
    FAILED(true);

    private final boolean terminal;

    InstallPhase(boolean terminal) {
        this.terminal = terminal;
    }

    /**
     * Whether the install has stopped, however it stopped.
     *
     * <p>A progress listener sees exactly one terminal phase per install and can stop listening
     * after it.
     *
     * @return {@code true} for {@link #DONE}, {@link #CANCELLED} and {@link #FAILED}
     */
    public boolean isTerminal() {
        return terminal;
    }
}
