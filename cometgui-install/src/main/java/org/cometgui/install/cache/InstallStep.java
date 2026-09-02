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

import org.cometgui.domain.tools.InstallPhase;

/**
 * The specification's atomic install sequence, one constant per numbered step, in order.
 *
 * <p>This enumeration is the pipeline rather than a description of it. {@link InstallPipeline}
 * looks up an action for every constant and refuses to be built if one is missing, and {@link
 * InstallPipeline#executedSteps()} records what actually ran -- so a step that is declared and
 * never performed is visible, and a step that is added and not implemented stops the installer.
 *
 * <p><strong>Why a step is the unit of interruption.</strong> {@code R-TOOL-04} requires an
 * interrupted install to leave nothing that reports itself installed, and the honest way to test
 * that is to fail an interruption at each step in turn rather than at one sampled point. A test
 * driven by {@link #values()} keeps covering the pipeline as it grows; a test that counts to eight
 * stops covering it the day a ninth step is added. It is also where cancellation lands: {@link
 * org.cometgui.domain.tools.InstallHandle#cancel()} promises the install stops "when it reaches a
 * point where it safely can", and a step boundary is that point.
 *
 * <p><strong>The marker is step 8 and the move is step 7, in that order.</strong> That leaves a
 * window in which the tool directory exists and holds no marker, and the window is safe by
 * construction: {@code R-TOOL-04} makes the marker the definition of installed, so a directory
 * without one is not an install and is discarded and rebuilt by the next attempt. Writing the
 * marker before the move would close the window and would also mean the marker was not written
 * last, which is the half of the rule that makes an interrupted install detectable at all.
 */
public enum InstallStep {

    /**
     * Step 1. Fetch the artefact, and every companion the record declares, into the download cache.
     *
     * <p>The download directory is keyed by tool, version and platform rather than by the install
     * attempt, so a transfer interrupted by a crash can be resumed by the next attempt instead of
     * starting a 99 MB download again.
     */
    DOWNLOAD_TO_TEMPORARY_FILE(InstallPhase.DOWNLOADING),

    /**
     * Step 2. Check every downloaded file against the size and SHA-256 the manifest pins.
     *
     * <p>{@code R-SEC-02} at the installer's own boundary. The download itself already refuses to
     * hand over bytes that do not match, so in a correct product this step cannot fire -- which is
     * exactly why it is here and exactly how {@link
     * org.cometgui.install.verify.VerificationResult}'s constructor is written: a defect that let
     * an unverified file reach the installer fails loudly here rather than being extracted and
     * executed.
     */
    VERIFY_SHA256(InstallPhase.VERIFYING),

    /**
     * Step 3. Unpack into the staging directory with the {@code R-SEC-05} guards.
     *
     * <p>Into staging, never into the cache. {@link org.cometgui.install.archive.ExtractionGuard}
     * writes nothing outside the directory it is given, but a rejection part way through leaves
     * what it had already written -- so the directory it is given is one this package throws away.
     */
    EXTRACT_WITH_GUARDS(InstallPhase.EXTRACTING),

    /**
     * Step 4. Check that the expected executable and companion layout is what came out.
     *
     * <p>Every path the manifest names must exist, and <strong>every path the manifest pins a
     * digest for is hashed and compared</strong> -- the archive member the record names and every
     * companion member. Until this step {@code ArchiveMember.hashes()} was a value recorded by the
     * manifest, carried through extraction and compared by nothing.
     */
    VERIFY_EXPECTED_LAYOUT(InstallPhase.VERIFYING),

    /**
     * Step 5. Apply the platform fix-ups: executable permission bits, macOS quarantine removal.
     *
     * <p>{@code R-PLAT-05} and {@code R-PLAT-04}. See {@link PlatformFixups} for what each does and
     * for which of them has ever been observed on the platform it is for.
     */
    APPLY_PLATFORM_FIXUPS(InstallPhase.INSTALLING),

    /**
     * Step 6. Probe version, runtime loadability and capabilities, through {@link ToolProbe}.
     *
     * <p>Before the move, so that a build which cannot run on this host never becomes a cache entry
     * that reports itself installed.
     */
    PROBE(InstallPhase.PROBING),

    /** Step 7. Move the staged payload into the tool cache in one rename. */
    MOVE_ATOMICALLY_INTO_CACHE(InstallPhase.INSTALLING),

    /**
     * Step 8. Write the completion marker, last.
     *
     * <p>{@code R-TOOL-04}'s "written last". It carries the checksums {@link ToolCache#verify} then
     * compares against the files on disk, so it is both the flag that says the install finished and
     * the evidence that the entry has not been corrupted or swapped since.
     */
    RECORD_INSTALLATION_METADATA(InstallPhase.INSTALLING);

    /** Where a user-visible progress report says the install has got to during this step. */
    private final InstallPhase phase;

    InstallStep(InstallPhase phase) {
        this.phase = phase;
    }

    /**
     * The user-visible phase this step belongs to.
     *
     * <p>Several steps share a phase: a scientist watching an install does not distinguish
     * "verified the download" from "verified the layout", and {@link InstallPhase} is the
     * vocabulary the Tool Manager renders.
     *
     * @return the phase, never a terminal one
     */
    public InstallPhase phase() {
        return phase;
    }

    /**
     * The step's number in the specification's list, counting from one.
     *
     * @return the position, {@code 1} for the first step
     */
    public int number() {
        return ordinal() + 1;
    }
}
