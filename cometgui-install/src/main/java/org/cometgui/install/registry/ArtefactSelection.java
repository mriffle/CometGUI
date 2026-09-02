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

package org.cometgui.install.registry;

import java.util.Objects;
import org.cometgui.domain.tools.ArtefactExecutability;

/**
 * An artefact the product may offer on a particular host, and how that host would run it.
 *
 * <p>The second half is the point. On Apple silicon the only XML-capable macOS Percolator upstream
 * publishes is an x86-64 build, so it is offered and it runs under Rosetta 2 ({@code D-004}). The
 * Tool Manager has to be able to say that in advance rather than let the user discover it as an
 * exec-format error, and a selection that dropped the distinction would make a translated build
 * indistinguishable from a native one.
 *
 * <p>{@link ArtefactExecutability#INCOMPATIBLE} cannot appear here: a selection is by definition
 * something the host can run, and {@code R-PERC-01} forbids presenting a build that cannot run.
 * Selection filters those out, and this record refuses one if a caller builds it by hand.
 *
 * @param artefact the artefact record
 * @param executability how this host runs it -- natively, or translated
 */
public record ArtefactSelection(ArtefactRecord artefact, ArtefactExecutability executability) {

    /**
     * Validates the selection.
     *
     * @throws NullPointerException if either component is {@code null}
     * @throws IllegalArgumentException if the executability is {@link
     *     ArtefactExecutability#INCOMPATIBLE}, which is not something to offer
     */
    public ArtefactSelection {
        Objects.requireNonNull(artefact, "artefact");
        Objects.requireNonNull(executability, "executability");
        if (!executability.isRunnable()) {
            throw new IllegalArgumentException(
                    "a selection is an artefact the host can run, so "
                            + executability.name()
                            + " cannot be one: "
                            + artefact.describe());
        }
    }

    /**
     * Whether this host runs the artefact through Rosetta 2 rather than natively.
     *
     * @return {@code true} only for {@link ArtefactExecutability#TRANSLATED_ROSETTA_2}
     */
    public boolean isTranslated() {
        return executability == ArtefactExecutability.TRANSLATED_ROSETTA_2;
    }
}
