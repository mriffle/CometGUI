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

package org.cometgui.tools.pdv;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.tools.api.JarAttributes;

/**
 * PDV's identity, read from the JAR's own manifest.
 *
 * <h2>Why not by running it, and this was established by execution</h2>
 *
 * <p>PDV has <strong>no version option</strong>. Its usage text, printed by {@code -h} and quoted
 * in {@code docs/feasibility/pdv-converter-spike.rst}, lists twenty-one options and none of them
 * reports a version. And it cannot be asked anything at all on a machine with no display: {@code
 * PDVCLI.PDVCLIMainClass} extends {@code javax.swing.JFrame} and the frame is constructed at the
 * top of its constructor, before the first argument is read. Executed on this project's Debian 12
 * host on 2026-09-03, {@code java -jar PDV-2.7.0.jar} with each of {@code -h}, {@code -v}, {@code
 * -V} and {@code --version} exits <strong>1</strong> with {@code java.awt.HeadlessException} thrown
 * from {@code java.awt.Window.<init>} by way of {@code PDVCLI.PDVCLIMainClass.<init>} line 203.
 * {@code -Djava.awt.headless=true} does not help; it is what makes the constructor throw.
 *
 * <p>So a probe that insisted on a printed banner would report every correctly installed PDV as
 * unidentifiable, on every headless machine and on every machine at all. The JAR's own manifest
 * carries {@code Implementation-Title: PDV} and {@code Implementation-Version: 2.7.0}, stamped by
 * the build that produced the bytes whose SHA-256 the installer verified at install step 2, four
 * steps before anything is probed. That is what is read here.
 *
 * <p><strong>This corrects a claim in the tree.</strong> {@code
 * org.cometgui.install.probe.StagedToolProbe} and {@code VersionBanner} both say that a JAR's
 * identity "needs a JVM launch". For the Limelight converter that is true and {@code
 * org.cometgui.tools.limelight.LimelightConverterIdentity} does exactly that. For PDV it is not
 * true, and no banner exists to be supplied.
 */
public final class PdvJarIdentity {

    /** The manifest attribute PDV's build stamps its version into. */
    public static final String VERSION_ATTRIBUTE = "Implementation-Version";

    /** The manifest attribute that names the program, checked so that any JAR is not PDV. */
    public static final String TITLE_ATTRIBUTE = "Implementation-Title";

    /** The value {@link #TITLE_ATTRIBUTE} carries in the artefact the manifest pins. */
    public static final String EXPECTED_TITLE = "PDV";

    /**
     * Reads PDV's version out of its JAR.
     *
     * <p>The signature is {@code org.cometgui.install.probe.JavaArtefactIdentity}'s.
     *
     * @param tool which tool this is; must be {@link ToolName#PDV}
     * @param platform the host, unused here because a manifest attribute is the same on every
     *     platform -- taken so that the method fits the port every Java artefact's identity uses
     * @param jar the installed JAR
     * @return the version its own manifest declares
     * @throws IOException if the file is not a JAR, carries no manifest, is not PDV, or declares no
     *     version this product accepts -- each with its own message
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if {@code tool} is not {@link ToolName#PDV}
     */
    public ToolVersion identify(ToolName tool, HostPlatform platform, Path jar) throws IOException {
        Objects.requireNonNull(tool, "tool");
        Objects.requireNonNull(platform, "platform");
        Objects.requireNonNull(jar, "jar");
        if (tool != ToolName.PDV) {
            throw new IllegalArgumentException(
                    "this reads PDV's identity and was asked about " + tool.id());
        }
        JarAttributes attributes = JarAttributes.of(jar);
        String title = attributes.value(TITLE_ATTRIBUTE).orElse("");
        if (!EXPECTED_TITLE.equals(title)) {
            throw new IOException(
                    jar
                            + " is not PDV: its manifest declares "
                            + TITLE_ATTRIBUTE
                            + " as \""
                            + title
                            + "\" and PDV's declares \""
                            + EXPECTED_TITLE
                            + "\"");
        }
        String declared =
                attributes
                        .value(VERSION_ATTRIBUTE)
                        .orElseThrow(
                                () ->
                                        new IOException(
                                                jar
                                                        + " declares no "
                                                        + VERSION_ATTRIBUTE
                                                        + " in its manifest, and PDV prints no"
                                                        + " version at all, so there is nothing"
                                                        + " left to identify it by"));
        return parse(declared, jar);
    }

    private static ToolVersion parse(String declared, Path jar) throws IOException {
        try {
            return ToolVersion.parse(declared);
        } catch (IllegalArgumentException notAVersion) {
            throw new IOException(
                    jar
                            + " declares "
                            + VERSION_ATTRIBUTE
                            + " as \""
                            + declared
                            + "\", which is not a version this product accepts: "
                            + notAVersion.getMessage(),
                    notAVersion);
        }
    }
}
