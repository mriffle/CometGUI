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

package org.cometgui.install.probe;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.cometgui.domain.tools.ToolVersion;

/**
 * Stage 2 of {@code R-TOOL-06}: what does this build say it is?
 *
 * <h2>Standard error is read first, and that is the whole point of this class</h2>
 *
 * <p>Percolator prints its banner and its entire {@code --help} listing on <strong>standard
 * error</strong> and writes <strong>nothing at all</strong> to standard output -- measured on this
 * project's host, where {@code percolator --help} exits 0 with 0 bytes on standard output. A probe
 * reading standard output alone therefore sees an empty string and concludes the version is
 * unparseable, on a binary that just told it exactly what it was. Tier 1's standing direction for
 * this phase names that assumption as one a later agent re-introduces, so it is pinned by a test
 * rather than only written down here.
 *
 * <p>Both streams are searched, because the same tool answers differently to different arguments:
 * Comet prints its banner on standard error for {@code -h} and on standard output when run with no
 * arguments at all. Reading only one stream would be right for one of those and wrong for the
 * other.
 */
public final class IdentityProbe {

    private IdentityProbe() {
        throw new AssertionError("IdentityProbe is a utility class and is never instantiated");
    }

    /**
     * Reads the version out of what a tool printed.
     *
     * @param banner how this tool spells its version
     * @param standardError everything the tool wrote to standard error, in order
     * @param standardOutput everything it wrote to standard output, in order
     * @return the version, or empty when neither stream carries a parseable banner -- which is
     *     {@code ProbeFailureKind.UNPARSEABLE_VERSION} and never a guess
     * @throws NullPointerException if any argument is {@code null}
     */
    public static Optional<ToolVersion> identify(
            VersionBanner banner, List<String> standardError, List<String> standardOutput) {
        Objects.requireNonNull(banner, "banner");
        Objects.requireNonNull(standardError, "standardError");
        Objects.requireNonNull(standardOutput, "standardOutput");
        return banner.readFrom(errorFirst(standardError, standardOutput));
    }

    /**
     * Both streams, standard error first.
     *
     * @param standardError the standard error lines
     * @param standardOutput the standard output lines
     * @return the two streams concatenated, standard error first
     * @throws NullPointerException if either argument is {@code null}
     */
    public static List<String> errorFirst(List<String> standardError, List<String> standardOutput) {
        List<String> both = new ArrayList<>(Objects.requireNonNull(standardError, "standardError"));
        both.addAll(Objects.requireNonNull(standardOutput, "standardOutput"));
        return List.copyOf(both);
    }
}
