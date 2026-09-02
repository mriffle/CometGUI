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

import java.net.URI;

/**
 * The licence an upstream artefact is published under, as upstream itself states it.
 *
 * <p>The specification requires every managed artefact record to carry licence metadata, and this
 * is deliberately <em>metadata</em> rather than a determination: {@code AC-REL-03}'s licence audit
 * is a human sign-off recorded in {@code DECISIONS.rst}, and no agent settles a licensing question
 * on its own. What is recorded here is what the named file at the named tag says.
 *
 * <p>The URL is pinned to a release tag rather than to a branch, for the same reason every download
 * URL is: a branch moves, and a licence statement read from a moving branch is not the one the
 * artefact was published under.
 *
 * @param spdx the SPDX identifier upstream's own metadata uses, for example {@code Apache-2.0}
 * @param url where that statement can be read, pinned to the release tag
 * @param note what the statement actually says, and anything about it a reader needs -- PDV's
 *     {@code LICENSE} and {@code pom.xml} disagree with each other, and recording only the winner
 *     would hide that
 */
public record ArtefactLicence(String spdx, URI url, String note) {

    /**
     * Validates the licence metadata.
     *
     * @throws NullPointerException if any component is {@code null}
     * @throws IllegalArgumentException if {@code spdx} or {@code note} is blank, or if {@code url}
     *     is not an absolute https URL, naming the field
     */
    public ArtefactLicence {
        spdx = ArtefactValues.requiredText(spdx, "spdx");
        url = ArtefactValues.downloadUrl(url, "licence url");
        note = ArtefactValues.requiredText(note, "licence note");
    }
}
