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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.domain.tools.HostOperatingSystem;
import org.cometgui.domain.tools.HostPlatform;

/**
 * The Java launcher a JAR tool is started with, and the command that starts it.
 *
 * <p><strong>A {@code .jar} is not an executable file on any platform this product
 * supports.</strong> Two of the four managed tools -- PDV and the Limelight converter -- are JARs,
 * so "run the installed file" is not a thing that can be done to them: the installed file is an
 * argument to a launcher, not the launcher. That is the whole reason this type exists, and it is
 * why the two Java tools cannot be probed by handing {@code
 * org.cometgui.install.probe.LoadabilityProbe} a version banner: that class builds its argument
 * array as the executable followed by the banner's arguments, which for a JAR would ask the
 * operating system to execute a ZIP file.
 *
 * <p>The launcher is the one running this application, found through {@code java.home}. CometGUI
 * ships its own Liberica runtime, so on a user's machine that is the bundled JRE inside the
 * application image and not whatever {@code java} happens to be on the {@code PATH} -- which is the
 * point: the tool runs on the runtime this product has verified, not on an unknown one.
 *
 * @param launcher the absolute path of the {@code java} executable
 */
public record JavaRuntime(Path launcher) {

    /**
     * Validates the launcher path.
     *
     * @throws NullPointerException if {@code launcher} is {@code null}
     * @throws IllegalArgumentException if it is not absolute
     */
    public JavaRuntime {
        Objects.requireNonNull(launcher, "launcher");
        if (!launcher.isAbsolute()) {
            throw new IllegalArgumentException(
                    "the java launcher path must be absolute, but was: " + launcher);
        }
    }

    /**
     * The runtime this application is itself running on.
     *
     * @return the launcher inside {@code java.home}
     * @throws IOException if {@code java.home} names no {@code java} executable, which means the
     *     runtime this application is running on cannot start a second JVM and the two JAR tools
     *     cannot be probed at all
     */
    public static JavaRuntime ofThisApplication() throws IOException {
        return ofJavaHome(
                Path.of(System.getProperty("java.home")),
                operatingSystemOf(System.getProperty("os.name"), System.getProperty("os.arch")));
    }

    /*
     * The host is read through HostPlatform.of, which is the domain's own detection rule and the
     * only one in this product: a second "is this Windows?" test here would be the duplicated
     * abstraction this project has already paid for twice, and it would be the one that decides
     * whether the launcher is called java or java.exe.  Taken as parameters rather than read from
     * the system properties inside, so that the refusal below is reachable from a test on a host
     * this product does recognise.
     */
    static HostOperatingSystem operatingSystemOf(String osName, String osArch) throws IOException {
        return HostPlatform.of(osName, osArch)
                .orElseThrow(
                        () ->
                                new IOException(
                                        "this host is os.name=\""
                                                + osName
                                                + "\" os.arch=\""
                                                + osArch
                                                + "\", which CometGUI does not recognise, so it"
                                                + " cannot say what the Java launcher is called"
                                                + " here"))
                .operatingSystem();
    }

    /**
     * The runtime inside a named {@code java.home}.
     *
     * @param javaHome the runtime directory
     * @param operatingSystem which host, because only Windows names the launcher {@code java.exe}
     * @return the launcher
     * @throws IOException if the directory holds no {@code java} executable, naming the path looked
     *     for
     * @throws NullPointerException if either argument is {@code null}
     */
    public static JavaRuntime ofJavaHome(Path javaHome, HostOperatingSystem operatingSystem)
            throws IOException {
        Objects.requireNonNull(javaHome, "javaHome");
        Objects.requireNonNull(operatingSystem, "operatingSystem");
        String name = operatingSystem == HostOperatingSystem.WINDOWS ? "java.exe" : "java";
        Path launcher = javaHome.toAbsolutePath().resolve("bin").resolve(name);
        if (!Files.isRegularFile(launcher)) {
            throw new IOException(
                    "no Java launcher at "
                            + launcher
                            + ", so a JAR tool cannot be started: PDV and the Limelight converter"
                            + " are JARs, and a JAR is not an executable file on any platform");
        }
        return new JavaRuntime(launcher);
    }

    /**
     * The command that runs a JAR with the given arguments.
     *
     * <p>The working directory is the JAR's own directory. PDV's manifest carries a {@code
     * Class-Path} of 170 entries under {@code lib/}, resolved relative to the JAR, and running
     * beside it is also what keeps a tool that writes a file next to itself from writing into
     * whatever directory the application happened to be started from.
     *
     * @param jar the JAR to run, which must be an absolute path
     * @param arguments what to pass the program itself, after {@code -jar <jar>}
     * @param environment the environment the child gets, constructed rather than inherited ({@code
     *     R-PROC-04})
     * @return the command
     * @throws IOException if the JAR has no directory to run in
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if {@code jar} is not absolute
     */
    public ToolCommand jarCommand(Path jar, List<String> arguments, Map<String, String> environment)
            throws IOException {
        Objects.requireNonNull(jar, "jar");
        Objects.requireNonNull(arguments, "arguments");
        Objects.requireNonNull(environment, "environment");
        if (!jar.isAbsolute()) {
            throw new IllegalArgumentException("jar must be an absolute path, but was: " + jar);
        }
        Path directory = jar.getParent();
        if (directory == null || !Files.isDirectory(directory)) {
            throw new IOException(
                    "the JAR " + jar + " has no directory to run in, so it cannot be probed");
        }
        List<String> argv = new ArrayList<>(arguments.size() + 3);
        argv.add(launcher.toString());
        argv.add("-jar");
        argv.add(jar.toString());
        argv.addAll(arguments);
        return new ToolCommand(argv, directory, environment);
    }
}
