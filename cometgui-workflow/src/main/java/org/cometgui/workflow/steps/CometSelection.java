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

package org.cometgui.workflow.steps;

import java.nio.file.Path;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.cometgui.domain.tools.DeclaredCapability;
import org.cometgui.domain.tools.ToolAdvisory;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolVersion;

/**
 * The Comet a run is to use, as the user (or the Tool Manager) selected it: the executable, the
 * release it is, the SHA-256 it was selected at, and whether CometGUI installed it.
 *
 * <p>The SHA-256 is the selection's promise about the bytes. The pre-run check and the run's {@code
 * resolve-comet} step both re-hash the executable and refuse a mismatch naming both digests, so a
 * binary replaced after it was selected never runs under the selected identity.
 *
 * <p>The advisories and the unconfirmed claims are the Tool Manager's offer's, carried so that the
 * run's Comet tool record warns what the offer warned ({@link ToolWarnings}): an advisory active
 * for the build -- a registered local binary is unverified, a release needs a runtime -- and each
 * capability the manifest claims that the install's probe did not find ({@code R-TOOL-07}).
 *
 * <p>The capabilities are the offer's observed ones -- what the install's probe established and the
 * completion marker recorded -- carried so that the run's Comet tool record holds the probed set
 * the specification's tool provenance requires, as Percolator's does.
 *
 * @param executable the Comet executable; absolute, normalised by this constructor
 * @param release the release the executable is, which must be the release the parameter model is
 *     for
 * @param sha256 the executable's SHA-256 when it was selected, 64 hexadecimal characters; kept in
 *     lower case
 * @param managed whether CometGUI installed it, rather than the user registering a local copy
 * @param artefactIdentity the upstream or managed artefact identity, when known (for a managed
 *     install, the manifest artefact it came from)
 * @param advisories the offer's advisories ({@link ToolOffer#advisories()}), in its order
 * @param unconfirmedClaims the offer's unconfirmed claims ({@link ToolOffer#unconfirmedClaims()}),
 *     in its order; each a Comet capability
 * @param capabilities the offer's observed capabilities (evidence {@code observed-by-execution});
 *     each a Comet capability
 */
public record CometSelection(
        Path executable,
        ToolVersion release,
        String sha256,
        boolean managed,
        Optional<String> artefactIdentity,
        List<ToolAdvisory> advisories,
        List<DeclaredCapability> unconfirmedClaims,
        Set<ToolCapability> capabilities) {

    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    /**
     * Validates and normalises.
     *
     * @throws NullPointerException naming a component that is {@code null}
     * @throws IllegalArgumentException if the executable is not absolute, the digest is not 64
     *     hexadecimal characters, quoting it, or an unconfirmed claim is not a Comet capability
     */
    public CometSelection {
        Objects.requireNonNull(executable, "executable");
        if (!executable.isAbsolute()) {
            throw new IllegalArgumentException(
                    "the Comet executable must be an absolute path, not \"" + executable + "\"");
        }
        executable = executable.normalize();
        Objects.requireNonNull(release, "release");
        Objects.requireNonNull(sha256, "sha256");
        String lower = sha256.toLowerCase(Locale.ROOT);
        if (!SHA256.matcher(lower).matches()) {
            throw new IllegalArgumentException(
                    "a SHA-256 is 64 hexadecimal characters, not \"" + sha256 + "\"");
        }
        sha256 = lower;
        Objects.requireNonNull(artefactIdentity, "artefactIdentity");
        advisories = List.copyOf(Objects.requireNonNull(advisories, "advisories"));
        unconfirmedClaims =
                List.copyOf(Objects.requireNonNull(unconfirmedClaims, "unconfirmedClaims"));
        for (DeclaredCapability claim : unconfirmedClaims) {
            claim.capability().requireBelongsTo(ToolName.COMET);
        }
        Set<ToolCapability> probed = EnumSet.noneOf(ToolCapability.class);
        for (ToolCapability capability : Objects.requireNonNull(capabilities, "capabilities")) {
            capability.requireBelongsTo(ToolName.COMET);
            probed.add(capability);
        }
        capabilities = Collections.unmodifiableSet(probed);
    }

    /**
     * A selection with no probed capability recorded.
     *
     * @param executable the Comet executable; absolute
     * @param release the release the executable is
     * @param sha256 the executable's SHA-256 when it was selected
     * @param managed whether CometGUI installed it
     * @param artefactIdentity the upstream or managed artefact identity, when known
     * @param advisories the offer's advisories, in its order
     * @param unconfirmedClaims the offer's unconfirmed claims, in its order
     * @throws NullPointerException naming a component that is {@code null}
     * @throws IllegalArgumentException as the canonical constructor
     */
    public CometSelection(
            Path executable,
            ToolVersion release,
            String sha256,
            boolean managed,
            Optional<String> artefactIdentity,
            List<ToolAdvisory> advisories,
            List<DeclaredCapability> unconfirmedClaims) {
        this(
                executable,
                release,
                sha256,
                managed,
                artefactIdentity,
                advisories,
                unconfirmedClaims,
                Set.of());
    }

    /**
     * A selection with no advisory, no unconfirmed claim and no probed capability recorded.
     *
     * @param executable the Comet executable; absolute
     * @param release the release the executable is
     * @param sha256 the executable's SHA-256 when it was selected
     * @param managed whether CometGUI installed it
     * @param artefactIdentity the upstream or managed artefact identity, when known
     * @throws NullPointerException naming a component that is {@code null}
     * @throws IllegalArgumentException as the canonical constructor
     */
    public CometSelection(
            Path executable,
            ToolVersion release,
            String sha256,
            boolean managed,
            Optional<String> artefactIdentity) {
        this(
                executable,
                release,
                sha256,
                managed,
                artefactIdentity,
                List.of(),
                List.of(),
                Set.of());
    }

    @Override
    public List<ToolAdvisory> advisories() {
        return List.copyOf(advisories);
    }

    @Override
    public List<DeclaredCapability> unconfirmedClaims() {
        return List.copyOf(unconfirmedClaims);
    }

    @Override
    public Set<ToolCapability> capabilities() {
        return Collections.unmodifiableSet(capabilities);
    }
}
