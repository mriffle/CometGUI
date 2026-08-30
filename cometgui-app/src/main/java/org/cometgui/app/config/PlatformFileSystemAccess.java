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

package org.cometgui.app.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.cometgui.domain.ports.EnvironmentReader;
import org.cometgui.domain.ports.FileSystemAccess;

/**
 * The real {@link FileSystemAccess}: {@link java.nio.file.Files} for the questions, and the
 * platform's own convention for where an application keeps its data.
 *
 * <h2>The location is derived from the injected environment, never read from {@code System}</h2>
 *
 * <p>{@link #applicationDataDirectory()} asks an {@link EnvironmentReader} for {@code os.name},
 * {@code user.home}, {@code APPDATA} and {@code XDG_DATA_HOME}. Nothing here touches {@code System}
 * directly, which is what lets a test on this Linux machine assert the Windows and macOS answers --
 * the two platforms the supported matrix calls tier 1 and that no agent can run here. A class that
 * read {@code System.getProperty("os.name")} inline would have exactly one testable branch out of
 * three.
 *
 * <h2>Where the directory goes, and why</h2>
 *
 * <table border="1">
 *   <caption>Application data directory per platform</caption>
 *   <tr><th>Host</th><th>Directory ({@code HOME} is {@code user.home})</th></tr>
 *   <tr><td>Windows</td><td>{@code %APPDATA%\CometGUI}, else
 *       {@code HOME\AppData\Roaming\CometGUI}</td></tr>
 *   <tr><td>macOS</td><td>{@code HOME/Library/Application Support/CometGUI}</td></tr>
 *   <tr><td>Linux and anything else</td><td>{@code $XDG_DATA_HOME/cometgui}, else
 *       {@code HOME/.local/share/cometgui}</td></tr>
 * </table>
 *
 * <p>The name is capitalised on Windows and macOS, where application-support directories carry
 * display names, and lower-cased on Linux, where the XDG base-directory specification's own
 * examples are lower-cased. An operating system this class does not recognise -- including one
 * whose {@code os.name} is not set at all -- gets the XDG layout, because that is the tier the
 * supported matrix puts everything else in and because a dot-directory under the home directory is
 * the least surprising thing to leave on an unknown host.
 *
 * <p>{@code APPDATA} and {@code XDG_DATA_HOME} are honoured only when they hold an
 * <em>absolute</em> path. That is the XDG specification's own rule -- "if an implementation
 * encounters a relative path it MUST consider the value invalid and ignore it" -- and the same
 * treatment is right for {@code APPDATA}: a relative application-data root would put the tool cache
 * wherever the process happened to be started.
 *
 * <h2>Nothing is created here</h2>
 *
 * <p>The constructor performs no I/O and {@link #applicationDataDirectory()} performs none either:
 * it computes a path. {@link FileSystemAccess#applicationDataDirectory()} says the directory is not
 * guaranteed to exist and the caller creates it, and a composition root that made a directory on
 * the user's disk as a side effect of being constructed would be creating one in every test run
 * too.
 */
public final class PlatformFileSystemAccess implements FileSystemAccess {

    /** The directory name used where application-data directories carry display names. */
    public static final String DISPLAY_DIRECTORY_NAME = "CometGUI";

    /** The directory name used under the XDG data home, where the convention is lower case. */
    public static final String XDG_DIRECTORY_NAME = "cometgui";

    /** The Windows environment variable naming the roaming application-data directory. */
    public static final String APPDATA_VARIABLE = "APPDATA";

    /** The XDG base-directory specification's variable naming the user's data home. */
    public static final String XDG_DATA_HOME_VARIABLE = "XDG_DATA_HOME";

    /** The system property naming the user's home directory. */
    public static final String USER_HOME_PROPERTY = "user.home";

    private final EnvironmentReader environment;

    /**
     * Creates the access over the environment it derives the data directory from.
     *
     * @param environment the environment seam; no {@code System} call is made outside it
     * @throws NullPointerException if {@code environment} is {@code null}
     */
    public PlatformFileSystemAccess(EnvironmentReader environment) {
        this.environment = Objects.requireNonNull(environment, "environment");
    }

    /** {@inheritDoc} */
    @Override
    public boolean exists(Path path) {
        return Files.exists(Objects.requireNonNull(path, "path"));
    }

    /** {@inheritDoc} */
    @Override
    public boolean isReadable(Path path) {
        return Files.isReadable(Objects.requireNonNull(path, "path"));
    }

    /** {@inheritDoc} */
    @Override
    public boolean isDirectory(Path path) {
        return Files.isDirectory(Objects.requireNonNull(path, "path"));
    }

    /** {@inheritDoc} */
    @Override
    public void createDirectories(Path path) throws IOException {
        Files.createDirectories(Objects.requireNonNull(path, "path"));
    }

    /**
     * {@inheritDoc}
     *
     * <p>See the table in this class's documentation for the answer per platform. The path is
     * computed, not created, and the same instance answers the same way every time it is asked.
     *
     * @throws IllegalStateException if the home directory is needed for this host and {@code
     *     user.home} is unset or blank. A JVM always sets it, so this is a broken environment
     *     rather than an ordinary one; failing loudly at startup is better than silently writing
     *     the tool cache into whatever directory the process was launched from.
     */
    @Override
    public Path applicationDataDirectory() {
        String osName = environment.osName().orElse("").toLowerCase(Locale.ROOT);
        /*
         * macOS is tested FIRST, and Windows is tested with startsWith rather than contains.
         * Both because of one string: a JVM reporting "Darwin" -- which some do -- CONTAINS
         * "win". The first version of this method asked `osName.contains("win")` first and sent
         * every Darwin host to the Windows branch; PlatformFileSystemAccessTest caught it. Every
         * Windows JVM reports a name beginning "Windows", so startsWith loses nothing.
         */
        if (osName.contains("mac") || osName.contains("darwin")) {
            return homeDirectory()
                    .resolve("Library")
                    .resolve("Application Support")
                    .resolve(DISPLAY_DIRECTORY_NAME);
        }
        if (osName.startsWith("windows")) {
            return absoluteEnvironmentPath(APPDATA_VARIABLE)
                    .orElseGet(() -> homeDirectory().resolve("AppData").resolve("Roaming"))
                    .resolve(DISPLAY_DIRECTORY_NAME);
        }
        return absoluteEnvironmentPath(XDG_DATA_HOME_VARIABLE)
                .orElseGet(() -> homeDirectory().resolve(".local").resolve("share"))
                .resolve(XDG_DIRECTORY_NAME);
    }

    /**
     * An environment variable read as an absolute path.
     *
     * @param name the variable to read
     * @return the path, or empty when the variable is unset, blank, relative, or not a path this
     *     filesystem can represent at all
     */
    private Optional<Path> absoluteEnvironmentPath(String name) {
        Optional<String> value = environment.environmentVariable(name).map(String::strip);
        if (value.isEmpty() || value.get().isEmpty()) {
            return Optional.empty();
        }
        try {
            Path path = Path.of(value.get());
            return path.isAbsolute() ? Optional.of(path) : Optional.empty();
        } catch (InvalidPathException notAPath) {
            return Optional.empty();
        }
    }

    /**
     * The user's home directory.
     *
     * @return the home directory as a path
     * @throws IllegalStateException if {@code user.home} is unset, blank or unusable as a path
     */
    private Path homeDirectory() {
        String home =
                environment
                        .systemProperty(USER_HOME_PROPERTY)
                        .map(String::strip)
                        .filter(value -> !value.isEmpty())
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "cannot locate the CometGUI application data"
                                                        + " directory: the system property "
                                                        + USER_HOME_PROPERTY
                                                        + " is not set"));
        try {
            return Path.of(home);
        } catch (InvalidPathException notAPath) {
            throw new IllegalStateException(
                    "cannot locate the CometGUI application data directory: "
                            + USER_HOME_PROPERTY
                            + " is not a usable path: \""
                            + home
                            + "\"",
                    notAPath);
        }
    }
}
