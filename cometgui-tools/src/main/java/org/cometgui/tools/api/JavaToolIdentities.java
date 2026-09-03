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

package org.cometgui.tools.api;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.tools.limelight.LimelightConverterIdentity;
import org.cometgui.tools.pdv.PdvJarIdentity;

/**
 * The identity of the two tools this product installs as JARs, routed to the adapter that knows how
 * to ask.
 *
 * <p>The two are asked in different ways and that is a fact about the tools, not a design choice.
 * The Limelight converter answers {@code --version} on standard output and exits 0; PDV has no
 * version option and constructs a Swing frame before reading its first argument, so it can only be
 * identified from the manifest inside its own JAR. Each adapter records the run that established
 * its way.
 *
 * <p>The signature is {@code org.cometgui.install.probe.JavaArtefactIdentity}'s, stated in domain
 * vocabulary, so the composition root satisfies that port with {@code javaToolIdentities::identify}
 * without this module depending on the installer.
 */
public final class JavaToolIdentities {

    private final PdvJarIdentity pdv;
    private final LimelightConverterIdentity converter;

    /**
     * Creates the router.
     *
     * @param pdv PDV's adapter
     * @param converter the Limelight converter's adapter
     * @throws NullPointerException if either argument is {@code null}
     */
    public JavaToolIdentities(PdvJarIdentity pdv, LimelightConverterIdentity converter) {
        this.pdv = Objects.requireNonNull(pdv, "pdv");
        this.converter = Objects.requireNonNull(converter, "converter");
    }

    /**
     * The router over this application's own runtime, with a chosen invocation timeout.
     *
     * @param runner how one invocation is run and collected
     * @return the router
     * @throws IOException if this runtime cannot start a second JVM, which is what starting a JAR
     *     needs
     * @throws NullPointerException if {@code runner} is {@code null}
     */
    public static JavaToolIdentities usingThisApplicationsRuntime(ToolRunner runner)
            throws IOException {
        Objects.requireNonNull(runner, "runner");
        return new JavaToolIdentities(
                new PdvJarIdentity(),
                new LimelightConverterIdentity(runner, JavaRuntime.ofThisApplication()));
    }

    /**
     * Identifies one installed JAR.
     *
     * @param tool which tool the artefact is a build of
     * @param platform the host being probed
     * @param jar the installed JAR
     * @return the version established from the artefact
     * @throws IOException if the artefact cannot be identified, with the adapter's own message
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if {@code tool} is not one of this product's two JAR tools,
     *     naming it -- Comet and Percolator are native executables and are identified by their
     *     printed banner in {@code org.cometgui.install.probe.IdentityProbe}
     */
    public ToolVersion identify(ToolName tool, HostPlatform platform, Path jar) throws IOException {
        Objects.requireNonNull(tool, "tool");
        Objects.requireNonNull(platform, "platform");
        Objects.requireNonNull(jar, "jar");
        return switch (tool) {
            case PDV -> pdv.identify(tool, platform, jar);
            case LIMELIGHT_CONVERTER -> converter.identify(tool, platform, jar);
            case COMET, PERCOLATOR ->
                    throw new IllegalArgumentException(
                            tool.id()
                                    + " is a native executable, not a JAR: its identity is the"
                                    + " version banner it prints, and asking for it here would"
                                    + " skip the loadability stage that runs it");
        };
    }
}
