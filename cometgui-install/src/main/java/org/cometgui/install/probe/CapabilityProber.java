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

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolVersion;

/**
 * Stage 3 of {@code R-TOOL-06}: what can this build actually do?
 *
 * <p><strong>Declared here and implemented in {@code org.cometgui.tools} (phase 05 unit
 * 7).</strong> The seam exists because of a module boundary that cannot be crossed the other way:
 * {@code cometgui-tools} depends on {@code cometgui-domain} and {@code cometgui-process} and
 * <em>not</em> on {@code cometgui-install}, so the functional capability probe cannot implement
 * {@code org.cometgui.install.cache.ToolProbe} -- that interface takes an {@code ArtefactRecord},
 * which lives in the installer. Every parameter and the return type here are therefore
 * <strong>domain vocabulary only</strong>, which is what makes the interface implementable from the
 * module that has to implement it. No module's dependency list changes, and the shape is the one
 * {@code Downloader}, {@code HashService} and {@code ProcessRunner} already use.
 *
 * <p><strong>The capability set must be established functionally, never textually</strong> ({@code
 * R-PERC-02}). The {@code noxml} and {@code XML_SUPPORT=ON} Percolator builds print byte-identical
 * help text -- 17928 characters each, both listing {@code --xmloutput} -- so a probe that reads
 * help output discriminates nothing. The implementation runs the binary over a synthetic PIN of 64
 * target and 64 decoy rows and inspects the document it writes; 8 and 8 makes a fully capable
 * binary abort with "median decoy score &lt;= score at 1% FDR" and leave a <em>zero-byte</em> file
 * behind, so "the output file exists" is not a sufficient condition either.
 *
 * <p><strong>Absent positive evidence, a capability is absent</strong> ({@code R-TOOL-08}). Return
 * the capabilities the build was observed to have and no others; an empty set is an honest answer.
 */
@FunctionalInterface
public interface CapabilityProber {

    /**
     * Probes what an installed build can do.
     *
     * <p>Called only after loadability and identity have passed, so the executable is known to
     * start and the version is the probed one rather than the manifest's claim.
     *
     * @param tool which tool this is a build of
     * @param version the version the binary itself reported, from the identity stage
     * @param platform the host the probe is running on
     * @param executable the absolute path of the executable or JAR, already staged and executable
     * @return the capabilities the build was observed to have, possibly empty
     * @throws IOException if the build cannot be exercised at all
     * @throws NullPointerException if any argument is {@code null}
     */
    Set<ToolCapability> probe(
            ToolName tool, ToolVersion version, HostPlatform platform, Path executable)
            throws IOException;
}
