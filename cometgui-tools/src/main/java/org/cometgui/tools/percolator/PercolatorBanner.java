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

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.cometgui.domain.tools.ToolVersion;

/**
 * The line every Percolator run prints about itself, and what it proves.
 *
 * <p>Observed by execution on this project's Debian 12 host on 2026-09-02 and again on 2026-09-03:
 * the real portable binaries of 3.06.5 and 3.07.1 print {@code Percolator version 3.07.1, Build
 * Date Jun 20 2024 13:20:18} on <strong>standard error</strong> and leave standard output empty for
 * {@code --help}; the same line opens the standard error of every {@code -X} run. A probe reading
 * standard output alone sees an empty string.
 *
 * <p><strong>It is used for two different questions and they are not the same.</strong> {@link
 * #readFrom} answers "which release is this?", which is the identity stage of {@code R-TOOL-06} and
 * is what a local binary's {@code >= 3.05} floor is checked against. {@link #isPresentIn} answers
 * "did this binary run at all?", which is what separates <em>the probe got an answer and the answer
 * was no</em> from <em>the probe got no answer</em> -- the distinction {@code R-TOOL-08} turns on,
 * because an empty capability set is positive evidence of absence and a probe that never started
 * the binary has established nothing. The Percolator 3.09 Debian payload on this host prints {@code
 * error while loading shared libraries: libboost_filesystem.so.1.83.0} and exits 127 with no banner
 * at all, which is exactly the case that must not become "not XML-capable".
 *
 * <p><strong>A second reading of the same banner exists</strong> in {@code
 * org.cometgui.install.probe.VersionBanner.percolator()}, which the managed-install identity stage
 * uses. The two live in modules that cannot see each other -- {@code cometgui-tools} depends on
 * {@code cometgui-domain} and {@code cometgui-process} and not on {@code cometgui-install} -- and
 * {@code PercolatorBannerTest} pins this one against the verbatim strings that one's evidence
 * sentence quotes, so a drift between them fails a test rather than being discovered by a user. The
 * honest resolution is to move the pattern into {@code org.cometgui.domain.tools}, which is
 * reported to the phase orchestrator rather than done here.
 */
public final class PercolatorBanner {

    /**
     * The pattern that reads the version out of the banner.
     *
     * <p>The trailing comma is deliberate: the banner is {@code Percolator version 3.07.1, Build
     * Date ...}, and without the comma the pattern would also match a truncated line, which would
     * let a partly-read version reach a provenance record as a fact.
     */
    public static final Pattern PATTERN =
            Pattern.compile("Percolator version (\\d{1,4}\\.\\d{1,4}(?:\\.\\d{1,4})?),");

    /** What to pass the executable to make it print the banner and exit. */
    public static final List<String> VERSION_ARGUMENTS = List.of("--help");

    private PercolatorBanner() {}

    /**
     * Reads the version out of lines a binary printed.
     *
     * @param lines the lines to search, in the order they should be searched -- standard error
     *     first, because that is the stream the banner arrives on
     * @return the version, or empty when no line carries the banner or what it carries is not a
     *     version this product accepts
     * @throws NullPointerException if {@code lines} is {@code null}
     */
    public static Optional<ToolVersion> readFrom(List<String> lines) {
        Objects.requireNonNull(lines, "lines");
        for (String line : lines) {
            if (line == null) {
                continue;
            }
            Matcher matcher = PATTERN.matcher(line);
            if (matcher.find()) {
                return parse(matcher.group(1));
            }
        }
        return Optional.empty();
    }

    /**
     * Whether these lines carry the banner at all.
     *
     * @param lines the lines to search
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

    /*
     * A banner that matched but does not spell a version this product accepts is unreadable, not a
     * guess: a partly-read version recorded in provenance is a fact nobody can look up.
     */
    private static Optional<ToolVersion> parse(String text) {
        try {
            return Optional.of(ToolVersion.parse(text));
        } catch (IllegalArgumentException notAVersion) {
            return Optional.empty();
        }
    }
}
