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
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.domain.tools.ToolInstallState;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.percolator.resolution.PercolatorResolver;

/**
 * The Percolator a run is to use: the Tool Manager's offer for an installed build (managed, or a
 * registered local binary), its executable, and the SHA-256 the executable had when it was selected
 * -- the Percolator counterpart of {@link CometSelection}.
 *
 * <p>The SHA-256 is the selection's promise about the bytes: the pre-run check and the run's {@code
 * resolve-percolator} step both re-hash the executable and refuse a mismatch naming both digests.
 *
 * <p><strong>The capabilities are the offer's observed ones and no others</strong> ({@link
 * PercolatorResolver#observedCapabilities}): an installed build's probed set, never a manifest
 * claim inferred from bytes ({@code R-TOOL-08}). They are what the command builder reads, and what
 * provenance records as the probed set.
 *
 * @param offer the Tool Manager's offer for the build; a Percolator offer that is {@link
 *     ToolInstallState#INSTALLED}, whose installed path is {@code executable}
 * @param executable the Percolator executable; absolute, normalised by this constructor
 * @param sha256 the executable's SHA-256 when it was selected, 64 hexadecimal characters; kept in
 *     lower case
 */
public record PercolatorSelection(ToolOffer offer, Path executable, String sha256) {

    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    /**
     * Validates and normalises.
     *
     * @throws NullPointerException naming a component that is {@code null}
     * @throws IllegalArgumentException if the offer is not an installed Percolator, the executable
     *     is relative or is not the offer's installed path, or the digest is not 64 hexadecimal
     *     characters -- each quoting what was given
     */
    public PercolatorSelection {
        Objects.requireNonNull(offer, "offer");
        Objects.requireNonNull(executable, "executable");
        Objects.requireNonNull(sha256, "sha256");
        if (offer.tool() != ToolName.PERCOLATOR) {
            throw new IllegalArgumentException(
                    "a Percolator selection needs a Percolator offer, not one of "
                            + offer.tool().id());
        }
        if (offer.state() != ToolInstallState.INSTALLED) {
            throw new IllegalArgumentException(
                    "only an installed Percolator can run, but Percolator "
                            + offer.version().text()
                            + " is "
                            + offer.state());
        }
        if (!executable.isAbsolute()) {
            throw new IllegalArgumentException(
                    "the Percolator executable must be an absolute path, not \""
                            + executable
                            + "\"");
        }
        executable = executable.normalize();
        if (!offer.installedPath().map(Path::normalize).equals(Optional.of(executable))) {
            throw new IllegalArgumentException(
                    "the Percolator executable "
                            + executable
                            + " is not the offer's installed path "
                            + offer.installedPath().map(Path::toString).orElse("(none)"));
        }
        String lower = sha256.toLowerCase(Locale.ROOT);
        if (!SHA256.matcher(lower).matches()) {
            throw new IllegalArgumentException(
                    "a SHA-256 is 64 hexadecimal characters, not \"" + sha256 + "\"");
        }
        sha256 = lower;
    }

    /**
     * The build's version.
     *
     * @return the offer's version, for display and recording only (P9-2)
     */
    public ToolVersion version() {
        return offer.version();
    }

    /**
     * Whether CometGUI installed the build, rather than the user registering a local binary.
     *
     * @return {@code true} for a managed build
     */
    public boolean managed() {
        return offer.origin().isManaged();
    }

    /**
     * The origin as provenance records it.
     *
     * @return {@code managed} or {@code local}
     */
    public String originId() {
        return managed() ? "managed" : "local";
    }

    /**
     * The build's observed capabilities: what the command builder reads.
     *
     * @return the observed set, immutable
     */
    public Set<ToolCapability> capabilities() {
        return PercolatorResolver.observedCapabilities(offer);
    }
}
