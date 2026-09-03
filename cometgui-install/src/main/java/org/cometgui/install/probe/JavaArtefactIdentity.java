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
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolVersion;

/**
 * Stages 1 and 2 of {@code R-TOOL-06} for an artefact that is not an executable file: a JAR.
 *
 * <p><strong>Declared here and implemented in {@code org.cometgui.tools} (phase 05 unit
 * 7)</strong>, the same seam and for the same reason as {@link CapabilityProber}: every parameter
 * and the return type are domain vocabulary, so the module that has to implement it can, without
 * depending on the installer.
 *
 * <h2>Why a version banner is not enough for a JAR</h2>
 *
 * <p>{@link StagedToolProbe} runs a native binary by handing {@link LoadabilityProbe} the
 * executable and a {@link VersionBanner}'s arguments, and the argument array it builds is the
 * executable followed by those arguments. A {@code .jar} is not an executable file on any platform
 * this product supports: the installed file is an <em>argument</em> to a launcher, not the
 * launcher. So the two JAR tools cannot be reached by supplying a banner, which is what phase 05
 * unit 6 expected, and they get this port instead.
 *
 * <h2>The two JAR tools answer in different ways, and that is a fact about them</h2>
 *
 * <p>The Limelight converter answers {@code --version} on standard output with {@code
 * cometPercolator2LimelightXML.jar v2.8.1} and exits 0, so its identity is established by starting
 * it. PDV has <strong>no version option at all</strong> and constructs a Swing frame before reading
 * its first argument -- executed on this project's host, every invocation on a machine with no
 * display exits 1 with {@code java.awt.HeadlessException} -- so its identity is read from the
 * {@code Implementation-Version} its own JAR manifest carries. An implementation records which of
 * the two it did and the run that established it.
 *
 * <p><strong>What this stage does and does not prove for PDV.</strong> For the converter, a version
 * that was printed is proof the JVM started the artefact. For PDV it is not: a manifest attribute
 * is read from the file. That is the strongest available answer -- no argument, on any host, makes
 * PDV print a version -- and it is recorded as a limit rather than dressed up as a launch.
 */
@FunctionalInterface
public interface JavaArtefactIdentity {

    /**
     * Establishes which release an installed JAR is.
     *
     * @param tool which tool the artefact is a build of
     * @param platform the host the probe is running on
     * @param artefact the absolute path of the installed JAR
     * @return the version the artefact was established to be
     * @throws IOException if it cannot be identified: not a JAR, not the tool it should be, no
     *     version, or a launch that failed -- never a guess
     * @throws NullPointerException if any argument is {@code null}
     */
    ToolVersion identify(ToolName tool, HostPlatform platform, Path artefact) throws IOException;
}
