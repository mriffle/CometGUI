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

package org.cometgui.tools.percolator;

import java.nio.file.Path;
import java.util.Objects;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.tools.ToolInstallState;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolOrigin;

/**
 * A local binary the user pointed at, once it has been probed and accepted.
 *
 * <p>Two things, kept together because they are recorded together. The {@link ToolOffer} is what
 * the Tool Manager renders and is expressed entirely in the vocabulary the user interface is
 * allowed to see; the {@link FileHashes} are what the provenance record needs and what {@code
 * R-TOOL-07} re-confirms the capability set against if the file ever changes underneath the
 * registration.
 *
 * <p>They are separate because {@code ToolOffer} deliberately carries no checksum: it is the Tool
 * Manager's row, and a scientist is not shown a SHA-256 in a list of tools. That is not a gap to be
 * filled by widening the offer -- it is the reason this record exists.
 *
 * @param offer the row the Tool Manager renders, always {@link ToolOrigin#LOCAL} and {@link
 *     ToolInstallState#INSTALLED}
 * @param checksums the MD5 and SHA-256 of the binary as it was when it was probed
 * @param binary the absolute path that was registered
 */
public record RegisteredLocalBinary(ToolOffer offer, FileHashes checksums, Path binary) {

    /**
     * Validates the registration.
     *
     * @throws NullPointerException if any component is {@code null}
     * @throws IllegalArgumentException if the offer is not a local, installed one, or if the path
     *     is not the one the offer names
     */
    public RegisteredLocalBinary {
        Objects.requireNonNull(offer, "offer");
        Objects.requireNonNull(checksums, "checksums");
        Objects.requireNonNull(binary, "binary");
        if (offer.origin() != ToolOrigin.LOCAL) {
            throw new IllegalArgumentException(
                    "a registered local binary must be recorded as "
                            + ToolOrigin.LOCAL
                            + ", but the offer says "
                            + offer.origin());
        }
        if (offer.state() != ToolInstallState.INSTALLED) {
            throw new IllegalArgumentException(
                    "a registered local binary must be recorded as "
                            + ToolInstallState.INSTALLED
                            + ", but the offer says "
                            + offer.state());
        }
        if (!binary.equals(offer.installedPath().orElse(null))) {
            throw new IllegalArgumentException(
                    "the registered path "
                            + binary
                            + " is not the one the offer names: "
                            + offer.installedPath().orElse(null));
        }
    }
}
