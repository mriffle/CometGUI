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

package org.cometgui.app.e2e.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.cometgui.app.config.ApplicationServices;
import org.cometgui.app.config.PlatformFileSystemAccess;
import org.cometgui.domain.ports.EnvironmentReader;

/**
 * A brand-new user home and application data root for one end-to-end run (canonical scenario step
 * 1; design decision P14-4), and the means of pointing a production composition root at it.
 *
 * <h2>The layout</h2>
 *
 * <pre>
 *   &lt;parent&gt;/&lt;segment&gt;/                    created here, and must not exist before
 *   &lt;parent&gt;/&lt;segment&gt;/home/               user.home and HOME
 *   &lt;parent&gt;/&lt;segment&gt;/home/.local/share/  XDG_DATA_HOME
 *   &lt;parent&gt;/&lt;segment&gt;/home/AppData/Roaming/  APPDATA
 * </pre>
 *
 * <p>The segment is {@value #PLAIN_SEGMENT}, or with {@link Naming#SPACE_AND_NON_ASCII} {@value
 * #NON_ASCII_SEGMENT} -- a space and an accented letter, the paths the specification's failure list
 * names. The application data directory itself is <strong>not</strong> created: on a fresh home it
 * does not exist until the application makes it, which is part of what "fresh" means.
 *
 * <h2>How the application is pointed at it</h2>
 *
 * <p>Through the product's own seam and nothing else. {@link #environment(EnvironmentReader)} is an
 * {@link EnvironmentReader} that answers {@code user.home}, {@code HOME}, {@code XDG_DATA_HOME} and
 * {@code APPDATA} with this home's paths and delegates every other question to the real reader;
 * {@link #services()} is {@link ApplicationServices#forThisHost()} with that reader, and a {@link
 * PlatformFileSystemAccess} over it, in place of the real ones -- every other service is the
 * production one. That matters on this project's build host doubly: surefire sets the test JVM's
 * own {@code XDG_DATA_HOME} to the font stack under {@code tools/}, so an application left to read
 * the process environment would put its tool cache there.
 *
 * <h2>"No tool is present" is proved from the directory tree</h2>
 *
 * <p>{@link #assertNoToolPresent()} walks the whole home, and the application data directory this
 * class computes by its own rule ({@link #applicationDataDirectory()}, typed out from the platform
 * conventions rather than asked of the product), and fails on any installed-tool directory, any
 * downloaded artefact, and any file named like a Comet, Percolator, PDV or Limelight-converter
 * executable or archive. It never asks the product's Tool Manager or tool cache, which could be
 * wrong in the same way as what it is checking.
 */
public final class FreshHome {

    /** The plain directory name: ASCII, no space. */
    public static final String PLAIN_SEGMENT = "cometgui-e2e-home";

    /** The directory name with a space and a non-ASCII letter. */
    public static final String NON_ASCII_SEGMENT = "protéomique run";

    /**
     * The names an installed or downloaded tool's files have: the managed Comet executables, the
     * Percolator executable and archives, PDV's JAR and archive, and the Limelight converter's JAR.
     * Matched against a file's name in lower case.
     */
    private static final Pattern TOOL_FILE =
            Pattern.compile(
                    "comet(\\.linux|\\.windows|\\.macos)?(\\.exe)?"
                            + "|percolator(\\.exe)?"
                            + "|percolator.*\\.(zip|deb|rpm|pkg|exe|tar\\.gz)"
                            + "|pdv.*\\.(jar|zip)"
                            + "|.*limelight.*\\.jar");

    /** How a fresh home's own directory is named. */
    public enum Naming {
        /** {@value FreshHome#PLAIN_SEGMENT}. */
        PLAIN,
        /** {@value FreshHome#NON_ASCII_SEGMENT}: needs a UTF-8 {@code sun.jnu.encoding}. */
        SPACE_AND_NON_ASCII
    }

    private final Path root;

    private final Path home;

    private final Path dataHome;

    private final Path appData;

    private FreshHome(Path root) {
        this.root = root;
        this.home = root.resolve("home");
        this.dataHome = home.resolve(".local").resolve("share");
        this.appData = home.resolve("AppData").resolve("Roaming");
    }

    /**
     * Creates a fresh home under a directory.
     *
     * @param parent an existing directory, usually a test's {@code @TempDir}
     * @param naming how the home's own directory is named
     * @return the home, with {@code home}, its XDG data home and its {@code APPDATA} created empty
     * @throws IOException if a directory cannot be created -- including because the home's
     *     directory already exists, which would make it not fresh
     * @throws AssertionError if {@code naming} is {@link Naming#SPACE_AND_NON_ASCII} and this JVM's
     *     {@code sun.jnu.encoding} is not UTF-8, with {@link RunnerLocale#diagnostic()} as the
     *     message rather than an {@code InvalidPathException}
     */
    public static FreshHome create(Path parent, Naming naming) throws IOException {
        Objects.requireNonNull(parent, "parent");
        Objects.requireNonNull(naming, "naming");
        String segment = PLAIN_SEGMENT;
        if (naming == Naming.SPACE_AND_NON_ASCII) {
            RunnerLocale.ofThisJvm().assertUtf8();
            segment = NON_ASCII_SEGMENT;
        }
        Path root = parent.toAbsolutePath().normalize().resolve(segment);
        Files.createDirectory(root);
        FreshHome fresh = new FreshHome(root);
        Files.createDirectories(fresh.dataHome);
        Files.createDirectories(fresh.appData);
        return fresh;
    }

    /**
     * The home's own directory, which holds {@link #home()}.
     *
     * @return {@code <parent>/<segment>}
     */
    public Path root() {
        return root;
    }

    /**
     * The user's home directory, reported as {@code user.home} and {@code HOME}.
     *
     * @return {@code <root>/home}
     */
    public Path home() {
        return home;
    }

    /**
     * The XDG data home, reported as {@code XDG_DATA_HOME}.
     *
     * @return {@code <home>/.local/share}
     */
    public Path dataHome() {
        return dataHome;
    }

    /**
     * The Windows roaming application data directory, reported as {@code APPDATA}.
     *
     * @return {@code <home>/AppData/Roaming}
     */
    public Path appData() {
        return appData;
    }

    /**
     * Where the application keeps its data under this home on this platform -- the tool cache's
     * root and the projects' parent -- by this class's own rule, typed out from the platform
     * conventions: {@code <XDG_DATA_HOME>/cometgui} on Linux and anything unrecognised, {@code
     * <home>/Library/Application Support/CometGUI} on macOS, {@code <APPDATA>/CometGUI} on Windows.
     *
     * @return the directory; it need not exist
     */
    public Path applicationDataDirectory() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("mac") || os.contains("darwin")) {
            return home.resolve("Library").resolve("Application Support").resolve("CometGUI");
        }
        if (os.startsWith("windows")) {
            return appData.resolve("CometGUI");
        }
        return dataHome.resolve("cometgui");
    }

    /**
     * An environment that reports this home and otherwise reports the real one.
     *
     * @param real what to delegate every other question to
     * @return the reader
     */
    public EnvironmentReader environment(EnvironmentReader real) {
        Objects.requireNonNull(real, "real");
        FreshHome fresh = this;
        return new EnvironmentReader() {
            @Override
            public Optional<String> environmentVariable(String name) {
                Objects.requireNonNull(name, "name");
                return switch (name) {
                    case "HOME" -> Optional.of(fresh.home.toString());
                    case "XDG_DATA_HOME" -> Optional.of(fresh.dataHome.toString());
                    case "APPDATA" -> Optional.of(fresh.appData.toString());
                    default -> real.environmentVariable(name);
                };
            }

            @Override
            public Optional<String> systemProperty(String name) {
                Objects.requireNonNull(name, "name");
                return "user.home".equals(name)
                        ? Optional.of(fresh.home.toString())
                        : real.systemProperty(name);
            }

            @Override
            public String toString() {
                return "the environment of the fresh home " + fresh.home;
            }
        };
    }

    /**
     * The production composition root, pointed at this home: {@link
     * ApplicationServices#forThisHost()} with {@link #environment(EnvironmentReader)} over its real
     * environment, and a {@link PlatformFileSystemAccess} over that, in place of its own. Every
     * other service -- the clock, the run identifiers, the C library probe, the process service --
     * is the production one, unchanged.
     *
     * @return the services
     */
    public ApplicationServices services() {
        ApplicationServices real = ApplicationServices.forThisHost();
        EnvironmentReader environment = environment(real.environment());
        return new ApplicationServices(
                real.clock(),
                environment,
                new PlatformFileSystemAccess(environment),
                real.runIds(),
                real.glibcVersions(),
                real.processRunner().orElse(null),
                real.hashService().orElse(null),
                real.downloader().orElse(null));
    }

    /**
     * Fails if any Comet, Percolator, PDV or converter is present under this home, proved by
     * walking the directory tree.
     *
     * <p>Present means any of: an entry under {@code <application data>/tools}; an entry under
     * {@code <application data>/cache/downloads}; or a file anywhere under the home whose name is a
     * tool's executable, archive or JAR name.
     *
     * @return every path under the home, relative to it, in walk order -- so that a caller can also
     *     assert exactly what a fresh home holds
     * @throws IOException if the tree cannot be walked
     * @throws AssertionError naming every offending path
     */
    public List<String> assertNoToolPresent() throws IOException {
        List<String> walked = new ArrayList<>();
        List<String> offending = new ArrayList<>();
        Path data = applicationDataDirectory();
        for (Path cache :
                List.of(data.resolve("tools"), data.resolve("cache").resolve("downloads"))) {
            if (Files.isDirectory(cache, LinkOption.NOFOLLOW_LINKS)) {
                try (Stream<Path> entries = Files.list(cache)) {
                    entries.forEach(entry -> offending.add(entry + " (an entry of " + cache + ")"));
                }
            }
        }
        try (Stream<Path> tree = Files.walk(home)) {
            for (Path path : (Iterable<Path>) tree::iterator) {
                if (path.equals(home)) {
                    continue;
                }
                walked.add(home.relativize(path).toString());
                Path name = path.getFileName();
                if (name != null
                        && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                        && TOOL_FILE.matcher(name.toString().toLowerCase(Locale.ROOT)).matches()) {
                    offending.add(path + " (named like a tool's file)");
                }
            }
        }
        if (!offending.isEmpty()) {
            throw new AssertionError(
                    "the home "
                            + home
                            + " is not free of tools: "
                            + offending.size()
                            + " present -- "
                            + String.join("; ", offending));
        }
        return List.copyOf(walked);
    }

    @Override
    public String toString() {
        return "fresh home " + home;
    }
}
