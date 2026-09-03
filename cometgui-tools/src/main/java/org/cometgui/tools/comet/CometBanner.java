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

package org.cometgui.tools.comet;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The line Comet prints about itself, used here only to answer "did this binary run at all?".
 *
 * <p>Observed by execution on this project's Debian 12 host: {@code comet -q} exits 0 and prints
 * {@code Comet version "2026.02 rev. 2 (6edec91)"} on <strong>standard output</strong>, quoted,
 * while {@code comet -h} exits <strong>1</strong> -- a non-zero exit from a working binary -- and
 * prints the same numbers unquoted on <strong>standard error</strong>. Both streams are therefore
 * searched, and the quote is optional.
 *
 * <p>Deliberately not a version <em>reader</em>. Identity is {@code R-TOOL-06}'s second stage and
 * belongs to {@code org.cometgui.install.probe.VersionBanner.comet()}, which already parses the
 * three numbers into the manifest's {@code 2026.02.2}; duplicating that translation here would be
 * two places to keep in step for no gain. What the capability probe needs is narrower and cannot
 * drift: whether Comet's own code was reached, which separates "it answered no" from "it never
 * started" and is what stops a loader failure being reported as a missing capability.
 */
public final class CometBanner {

    /** The banner, with the opening quote optional because only some invocations print it. */
    public static final Pattern PATTERN =
            Pattern.compile("Comet version \"?\\d{1,4}\\.\\d{1,2} rev\\. \\d{1,4}");

    private CometBanner() {}

    /**
     * Whether these lines carry the banner.
     *
     * @param lines the lines to search, both streams
     * @return {@code true} if any line carries it
     * @throws NullPointerException if {@code lines} is {@code null}
     */
    public static boolean isPresentIn(List<String> lines) {
        Objects.requireNonNull(lines, "lines");
        for (String line : lines) {
            if (line != null && PATTERN.matcher(line).find()) {
                return true;
            }
        }
        return false;
    }
}
