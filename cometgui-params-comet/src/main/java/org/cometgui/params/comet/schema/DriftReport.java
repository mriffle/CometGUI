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

package org.cometgui.params.comet.schema;

import java.util.List;
import java.util.Objects;
import org.cometgui.domain.tools.ToolVersion;

/**
 * The result of comparing one dump with the curated metadata: every finding, and the counts that
 * {@code AC-PAR-01} asks for.
 *
 * @param version the Comet version the dump's marker names
 * @param mode whether the dump was complete or partial
 * @param findings every disagreement, in dump order then metadata order
 * @param declared how many parameters the dump declares
 * @param modelled how many of those have metadata for this version
 * @param allowListed how many of those are on the internal allow-list
 */
public record DriftReport(
        ToolVersion version,
        DiscoveryMode mode,
        List<DriftFinding> findings,
        int declared,
        int modelled,
        int allowListed) {

    /** Takes an immutable copy. */
    public DriftReport {
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(mode, "mode");
        findings = List.copyOf(findings);
    }

    /**
     * The findings, immutable.
     *
     * @return the findings
     */
    @Override
    public List<DriftFinding> findings() {
        return List.copyOf(findings);
    }

    /**
     * Whether the dump and the metadata agree.
     *
     * @return {@code true} when there is no finding
     */
    public boolean isClean() {
        return findings.isEmpty();
    }

    /**
     * A summary a test failure or a log can show: the counts, then one line per finding.
     *
     * @return the summary
     */
    public String describe() {
        StringBuilder text =
                new StringBuilder()
                        .append("Comet ")
                        .append(version.text())
                        .append(' ')
                        .append(mode)
                        .append(": declared ")
                        .append(declared)
                        .append(", modelled ")
                        .append(modelled)
                        .append(", allow-listed ")
                        .append(allowListed)
                        .append(", findings ")
                        .append(findings.size());
        for (DriftFinding finding : findings) {
            text.append("\n  ").append(finding.kind()).append(": ").append(finding.message());
        }
        return text.toString();
    }
}
