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

package org.cometgui.tools.limelight;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.tools.api.JavaRuntime;
import org.cometgui.tools.api.ToolRunOutcome;
import org.cometgui.tools.api.ToolRunner;

/**
 * The Limelight converter's identity, established by starting it.
 *
 * <p>Executed on this project's Debian 12 host on 2026-09-03 against the real pinned artefact
 * {@code cometPercolator2LimelightXML.jar} (SHA-256 {@code 843573396ce0...}): {@code java -jar
 * cometPercolator2LimelightXML.jar --version} exits <strong>0</strong> and prints, on
 * <strong>standard output</strong>,
 *
 * <pre>cometPercolator2LimelightXML.jar v2.8.1</pre>
 *
 * <p>{@code -V} prints the same thing. Both streams are searched anyway, because every other tool
 * in this project puts its banner on standard error and a probe that assumed one stream has already
 * been wrong here twice.
 *
 * <p>Unlike PDV, this JAR really does have to be launched: its manifest carries no {@code
 * Implementation-Version} at all -- what it carries is {@code LIMELIGHT_RELEASE_TAG: v2.8.1} and
 * {@code GIT-Tag-at-HEAD: v2.8.1}, both prefixed with a {@code v} that is part of the release tag
 * rather than of the version. Running the program asks the program, which is the stronger question,
 * and the answer is the string the program itself prints.
 */
public final class LimelightConverterIdentity {

    /** What to pass to make the converter print its version and exit. */
    public static final List<String> VERSION_ARGUMENTS = List.of("--version");

    /** The line the converter prints, whose one group is the version. */
    public static final Pattern PATTERN =
            Pattern.compile(
                    "cometPercolator2LimelightXML\\.jar v(\\d{1,4}\\.\\d{1,4}(?:\\.\\d{1,4})?)");

    private final ToolRunner runner;
    private final JavaRuntime java;

    /**
     * Creates the probe.
     *
     * @param runner how one invocation is run and collected
     * @param java the runtime the JAR is started on
     * @throws NullPointerException if either argument is {@code null}
     */
    public LimelightConverterIdentity(ToolRunner runner, JavaRuntime java) {
        this.runner = Objects.requireNonNull(runner, "runner");
        this.java = Objects.requireNonNull(java, "java");
    }

    /**
     * Starts the converter and reads the version it prints.
     *
     * <p>The signature is {@code org.cometgui.install.probe.JavaArtefactIdentity}'s.
     *
     * @param tool which tool this is; must be {@link ToolName#LIMELIGHT_CONVERTER}
     * @param platform the host, taken so that the method fits the port
     * @param jar the installed JAR
     * @return the version it printed
     * @throws IOException if it could not be started, did not finish, exited non-zero or printed no
     *     version line -- each with its own message and the output it did produce
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if {@code tool} is not {@link ToolName#LIMELIGHT_CONVERTER}
     */
    public ToolVersion identify(ToolName tool, HostPlatform platform, Path jar) throws IOException {
        Objects.requireNonNull(tool, "tool");
        Objects.requireNonNull(platform, "platform");
        Objects.requireNonNull(jar, "jar");
        if (tool != ToolName.LIMELIGHT_CONVERTER) {
            throw new IllegalArgumentException(
                    "this reads the Limelight converter's identity and was asked about "
                            + tool.id());
        }
        ToolRunOutcome outcome =
                runner.run(java.jarCommand(jar.toAbsolutePath(), VERSION_ARGUMENTS, Map.of()));
        if (outcome.timedOut()) {
            throw new IOException(
                    "the Limelight converter at "
                            + jar
                            + " did not answer --version within "
                            + runner.timeout()
                            + ", so it has not been identified");
        }
        if (!outcome.exitedZero()) {
            throw new IOException(
                    "the Limelight converter at "
                            + jar
                            + " answered --version with exit "
                            + outcome.exitCode().orElse(-1)
                            + " saying: "
                            + outcome.joinedOutput());
        }
        return readFrom(outcome, jar);
    }

    private static ToolVersion readFrom(ToolRunOutcome outcome, Path jar) throws IOException {
        for (String line : outcome.errorFirst()) {
            Matcher matcher = PATTERN.matcher(line);
            if (matcher.find()) {
                return parse(matcher.group(1), line, jar);
            }
        }
        throw new IOException(
                "the JAR at "
                        + jar
                        + " started and exited 0 but printed no line matching \""
                        + PATTERN.pattern()
                        + "\", so it is not the Limelight converter this product installs. It"
                        + " printed: "
                        + outcome.joinedOutput());
    }

    private static ToolVersion parse(String text, String line, Path jar) throws IOException {
        try {
            return ToolVersion.parse(text);
        } catch (IllegalArgumentException notAVersion) {
            throw new IOException(
                    "the JAR at "
                            + jar
                            + " printed \""
                            + line
                            + "\", whose version is not one this product accepts: "
                            + notAVersion.getMessage(),
                    notAVersion);
        }
    }
}
