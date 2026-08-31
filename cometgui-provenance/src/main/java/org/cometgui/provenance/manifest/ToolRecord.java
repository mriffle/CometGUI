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

package org.cometgui.provenance.manifest;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import org.cometgui.domain.ports.FileHashes;

/**
 * One tool as it existed for one run: which binary, which version, what it could do, and what it
 * did.
 *
 * <p>The specification's tool-provenance list is the field list of this record together with {@link
 * ExecutionRecord}, which holds the launch half. The split is not cosmetic: the identity of a
 * binary is a fact about the installation, while the argument array and the exit code are facts
 * about one invocation of it, and a later phase that runs the same tool twice records two
 * executions of one identity rather than duplicating the checksums.
 *
 * <p><strong>Probed capabilities, not inferred ones.</strong> The single most important verified
 * fact in this project is that what a Percolator build can do is a property of that build, probed,
 * and not something a version number implies -- 3.09 removed XML I/O, the Limelight converter
 * requires it, and the mapping is neither monotonic nor the same on every platform. So capabilities
 * are recorded here as the set that was actually probed. A reader of a manifest can therefore see
 * why a run took the path it took, which a version string alone would not explain.
 *
 * <p><strong>Warnings are recorded, not just displayed.</strong> An advisory that was active for
 * the version in use -- a known defect, a platform caveat, a deprecation -- is part of the run's
 * provenance. A scientist re-reading results a year later has no other way to learn that the
 * application knew something about that build at the time.
 *
 * @param name the logical tool name, such as {@code comet} or {@code percolator}
 * @param version the version the tool itself reported when probed
 * @param releaseTag the upstream release tag or commit, absent when the binary does not reveal one
 * @param executablePath the absolute path of the executable or JAR that was run
 * @param hashes the MD5 and SHA-256 of that executable or JAR
 * @param managed {@code true} if the application installed and owns this binary, {@code false} if
 *     the user pointed at one already on the machine
 * @param artefactIdentity the upstream or managed artefact this binary came from, absent for a
 *     local binary that the application did not install and therefore cannot attribute
 * @param capabilities the capabilities that were probed on this binary; iterated in ascending
 *     order, whatever order the caller supplied
 * @param execution what happened when it was run
 * @param warnings the advisories active for this version at the time of the run, in the order they
 *     were raised
 */
public record ToolRecord(
        String name,
        String version,
        Optional<String> releaseTag,
        Path executablePath,
        FileHashes hashes,
        boolean managed,
        Optional<String> artefactIdentity,
        Set<String> capabilities,
        ExecutionRecord execution,
        List<String> warnings) {

    /**
     * Validates the record and takes defensive, immutable copies of both collections.
     *
     * @throws NullPointerException if any reference component is {@code null}
     * @throws IllegalArgumentException if {@code name} or {@code version} is blank, if {@code
     *     executablePath} is relative, or if a capability or a warning is null or blank -- with a
     *     message naming the field and the rejected value
     */
    public ToolRecord {
        name = ManifestChecks.requireNonBlank(name, "name");
        version = ManifestChecks.requireNonBlank(version, "version");
        Objects.requireNonNull(releaseTag, "releaseTag");
        executablePath = ManifestChecks.requireAbsolute(executablePath, "executablePath");
        Objects.requireNonNull(hashes, "hashes");
        Objects.requireNonNull(artefactIdentity, "artefactIdentity");
        capabilities = ManifestChecks.sortedCopyOfNonBlank(capabilities, "capabilities");
        Objects.requireNonNull(execution, "execution");
        warnings = ManifestChecks.copyOfNonBlank(warnings, "warnings");
    }

    /**
     * The capabilities probed on this binary.
     *
     * <p>Immutable, and <strong>iterated in ascending {@link String#compareTo} order</strong>, so
     * that two runs that probed the same capabilities serialise identically however the probe
     * happened to collect them. The copy is what makes the immutability visible at the call site --
     * and to SpotBugs, which reports a record accessor handing out a collection field as {@code
     * EI_EXPOSE_REP} because nothing there shows which kind of set it received.
     *
     * @return the probed capabilities, immutable and sorted
     */
    public Set<String> capabilities() {
        return Collections.unmodifiableSortedSet(new TreeSet<>(capabilities));
    }

    /**
     * The advisories active for this version at the time of the run.
     *
     * <p>Immutable, in the order they were raised, and copied for the reason given on {@link
     * #capabilities()}. Order is kept rather than sorted here because the sequence is itself
     * information: the first advisory is the one that mattered most at the time it was raised.
     *
     * @return the warnings, immutable
     */
    public List<String> warnings() {
        return List.copyOf(warnings);
    }
}
