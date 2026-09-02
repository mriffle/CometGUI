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
 * Why an install stopped, in the vocabulary the installer itself owns.
 *
 * <p>The download, verification and extraction packages already raise exceptions that name what
 * went wrong there -- an artefact that has gone from its URL, a SHA-256 that does not match, an
 * archive entry that tried to escape -- and this installer lets every one of those through
 * unchanged rather than wrapping it in a poorer message. These are the failures that belong to the
 * install itself.
 */
public enum InstallFailure {

    /**
     * A downloaded file is not the size and SHA-256 the manifest pins.
     *
     * <p>The installer's own {@code R-SEC-02} boundary. In a correct product the download refuses
     * first and this never fires; it exists so that a defect which let an unverified file reach the
     * installer stops here rather than being extracted and executed.
     */
    CHECKSUM_MISMATCH,

    /** A file the manifest names is not where the extraction should have put it. */
    LAYOUT_INCOMPLETE,

    /**
     * A file the manifest pins a digest for came out of the artefact with different bytes.
     *
     * <p>This is what closes the gap the manifest carried until this unit: every archive member the
     * manifest names has a recorded length and digest, and until now nothing in the product
     * compared them against what extraction produced.
     */
    MEMBER_DIGEST_MISMATCH,

    /**
     * The probe refused the installed build, so it never becomes a cache entry.
     *
     * <p>{@code R-TOOL-06}: a tool that fails loadability is never offered. Probing happens before
     * the move for exactly that reason.
     */
    PROBE_FAILED,

    /**
     * The final move into the cache was refused because something else holds the files.
     *
     * <p>Named separately because it is the one failure here that is not the artefact's fault and
     * not this machine's configuration: a virus scanner, a sync client or another CometGUI window
     * holding a file open makes a rename fail on Windows with an access denial. See {@link
     * InstallPipeline} for what this installer deliberately does <em>not</em> do about it.
     */
    CACHE_CONTENDED
}
