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

package org.cometgui.app.e2e.tierb;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.cometgui.app.e2e.oracle.DirectorySnapshot;
import org.cometgui.app.e2e.scenario.ChooserScript;
import org.cometgui.app.e2e.scenario.ScenarioLayout;
import org.cometgui.app.e2e.support.FreshHome;
import org.cometgui.app.testing.RealSearch;
import org.cometgui.app.uidriver.RemotableUiDriver;

/**
 * Tier B's composition, said once for every Tier B scenario (Phase 14 unit 7; design decisions
 * P14-10 and P14-11): the exact archive {@code scripts/package/build-app-image.sh} built, installed
 * by {@code scripts/package/install-archive.sh} into a fresh directory outside the repository and
 * made read-only, launched through its own native launcher -- unmodified, with the test-only bridge
 * attached through {@code JAVA_TOOL_OPTIONS} -- on the project Xvfb, with {@code
 * PATH=/usr/bin:/bin} (which holds no {@code java}, checked here), {@code JAVA_HOME} unset, {@code
 * LC_ALL=C.UTF-8} and a fresh home under a path with a space and an accented letter ({@link
 * PackagedDisplay}).
 *
 * <h2>What only this tier can see, checked on every launch</h2>
 *
 * <ul>
 *   <li>the launched process reports a UTF-8 {@code sun.jnu.encoding} (P14-9), the installed
 *       bundled runtime as its {@code java.home}, the installed launcher as its own path, the
 *       product's launcher class, the fresh home, the fresh XDG data home, the launch directory as
 *       its working directory, and no {@code JAVA_HOME};
 *   <li>a close is a real one: the window's {@code WM_DELETE_WINDOW}, the process's own exit with
 *       status 0, reaped; a restart is a new process with a new pid and a new bridge token;
 *   <li>nothing is written into the install tree (a snapshot before the first launch -- every
 *       entry's bytes, size, mtime and inode -- against one taken when asked) or into any launch
 *       directory.
 * </ul>
 *
 * <h2>The scenario sees the application only as Tier A's does</h2>
 *
 * <p>Through {@link RemotableUiDriver} -- the packaged application's own {@code RobotFxUiDriver},
 * over the bridge ({@link BridgedUiDriver}) -- and through a {@link ChooserScript} that answers the
 * product's own file dialogs ({@link BridgedChooserScript}), always of the launch now running.
 *
 * <h2>Files read outside this module</h2>
 *
 * <p>{@code _build/package/linux} (the archive), {@code scripts/package} (the install, the window
 * helper), {@code scripts/e2e/build-bridge.sh}, {@code manifests/tools.json}, the {@code D-006}
 * local fixture under {@code scratch/fixture} (through {@link RealSearch}), and for the display
 * {@code tools/x11-bookworm-20260829}, {@code tools/fontstack-bookworm-20260829} and {@code
 * scripts/feasibility/gui-spike/xkbcomp-path-shim.c} (compiled with the host's {@code gcc}).
 */
final class TierBApplication implements AutoCloseable {

    /** How long a launch may take to show its window and answer on its bridge. */
    static final Duration START = Duration.ofMinutes(3);

    /** How long the process may take to end after its window was closed. */
    static final Duration STOP = Duration.ofSeconds(60);

    /** The launcher's {@code PATH}, which {@link PackagedDisplay} sets. */
    static final List<String> PATH = List.of("/usr/bin", "/bin");

    private final Path work;

    private final Path bridgeJar;

    private final InstalledCometGui installed;

    private final DirectorySnapshot installedTree;

    private final PackagedDisplay display;

    private final FreshHome home;

    private final ScenarioLayout layout;

    private final List<LaunchedCometGui> launches = new ArrayList<>();

    private final List<Map<String, String>> reported = new ArrayList<>();

    private TierBApplication(
            Path work,
            Path bridgeJar,
            InstalledCometGui installed,
            DirectorySnapshot installedTree,
            PackagedDisplay display,
            FreshHome home,
            ScenarioLayout layout) {
        this.work = work;
        this.bridgeJar = bridgeJar;
        this.installed = installed;
        this.installedTree = installedTree;
        this.display = display;
        this.home = home;
        this.layout = layout;
    }

    /**
     * Builds the bridge jar from this JVM's test classes, installs the built archive read-only
     * under the test's directory, snapshots the install tree, starts the project Xvfb, makes a
     * fresh home with a space and a non-ASCII segment, and stages the {@code D-006} inputs beside
     * it. Nothing is launched yet.
     *
     * @param parent a directory of the test's own, outside the repository, whose real path holds no
     *     whitespace ({@code JAVA_TOOL_OPTIONS} is split on it)
     * @return the composition, not yet launched
     * @throws IOException if a directory, the install, the display or a fixture cannot be made
     */
    static TierBApplication prepare(Path parent) throws IOException {
        Path work = parent.toRealPath();
        assertFalse(
                work.toString().chars().anyMatch(Character::isWhitespace),
                "the work directory must have no whitespace (JAVA_TOOL_OPTIONS): " + work);
        assertFalse(
                work.startsWith(RealSearch.repositoryRoot()),
                "Tier B installs outside the repository: " + work);
        for (String directory : PATH) {
            assertFalse(
                    Files.isExecutable(Path.of(directory, "java")),
                    "the launcher's PATH must hold no java, and " + directory + " does");
        }
        Path archive = InstalledCometGui.builtArchive();
        Path bridgeJar = BridgeJar.build(work.resolve("bridge").resolve("cometgui-e2e-bridge.jar"));
        InstalledCometGui installed = InstalledCometGui.install(archive, work.resolve("install"));
        PackagedDisplay display = null;
        try {
            DirectorySnapshot tree = DirectorySnapshot.of(installed.installDirectory());
            display = PackagedDisplay.start(work.resolve("display"));
            FreshHome home =
                    FreshHome.create(
                            Files.createDirectories(work.resolve("homes")),
                            FreshHome.Naming.SPACE_AND_NON_ASCII);
            Path inputs = Files.createDirectories(home.root().resolve("inputs"));
            List<Path> spectra = RealSearch.spectra(inputs);
            Path database = RealSearch.proteome(inputs.resolve("UP000005640_9606.fasta"));
            ScenarioLayout layout =
                    ScenarioLayout.defaultsUnder(
                            home,
                            inputs,
                            spectra,
                            database,
                            RealSearch.repositoryRoot().resolve("manifests/tools.json"),
                            Files.createDirectories(work.resolve("oracle-work")));
            return new TierBApplication(work, bridgeJar, installed, tree, display, home, layout);
        } catch (IOException | RuntimeException | Error failed) {
            if (display != null) {
                display.close();
            }
            installed.uninstall();
            throw failed;
        }
    }

    /**
     * Where the scenario's files are.
     *
     * @return the layout over the fresh home
     */
    ScenarioLayout layout() {
        return layout;
    }

    /**
     * The installation the launches run.
     *
     * @return the installed archive
     */
    InstalledCometGui installed() {
        return installed;
    }

    /**
     * The scenario's answer to the file dialogs: always those of the launch now running.
     *
     * @return the chooser script
     */
    ChooserScript chooserScript() {
        return new ChooserScript() {
            @Override
            public void nextSpectra(List<Path> files) {
                current().chooser().nextSpectra(files);
            }

            @Override
            public void nextDatabase(Path file) {
                current().chooser().nextDatabase(file);
            }

            @Override
            public void nextFile(Path file) {
                current().chooser().nextFile(file);
            }

            @Override
            public void nextExportTarget(Path file) {
                current().chooser().nextExportTarget(file);
            }

            @Override
            public List<String> asked() {
                return current().chooser().asked();
            }
        };
    }

    /**
     * The launch now running.
     *
     * @return the newest launch
     * @throws IllegalStateException before the first
     */
    LaunchedCometGui current() {
        if (launches.isEmpty()) {
            throw new IllegalStateException("nothing has been launched yet");
        }
        return launches.get(launches.size() - 1);
    }

    /**
     * What each launch's JVM reported about itself, in launch order.
     *
     * @return the reports
     */
    List<Map<String, String>> reported() {
        return List.copyOf(reported);
    }

    /**
     * Launches the installed application -- the first time, or a restart after {@link
     * #closeApplication()} -- attaches to it, and holds the launched JVM to what an installed
     * CometGUI must be: a UTF-8 locale, the bundled runtime, the installed launcher, the fresh
     * home, the launch directory, no {@code JAVA_HOME}; a restart a new process with a new token.
     *
     * @return a driver of the running application
     * @throws IOException if the process cannot be started
     */
    RemotableUiDriver start() throws IOException {
        if (!launches.isEmpty()) {
            assertTrue(current().hasExited(), "a restart follows the previous process's exit");
        }
        Path launch = work.resolve("launch-" + (launches.size() + 1));
        Path cwd = launch.resolve("cwd");
        LaunchedCometGui launched =
                LaunchedCometGui.start(
                        installed,
                        display,
                        home.home(),
                        cwd,
                        bridgeJar,
                        launch.resolve("bridge.port"),
                        List.of());
        LaunchedCometGui previous = launches.isEmpty() ? null : current();
        launches.add(launched);
        assertEquals(LaunchedCometGui.WINDOW_TITLE, launched.attach(START));
        launched.locale().assertUtf8();
        Map<String, String> jvm = launched.environment();
        reported.add(jvm);
        assertAll(
                "what the launched packaged JVM reports about itself: " + jvm,
                () -> assertEquals(installed.runtime().toString(), jvm.get("java.home")),
                () -> assertEquals(installed.launcher().toString(), jvm.get("jpackage.app-path")),
                () ->
                        assertEquals(
                                "org.cometgui.app.bootstrap.CometGuiLauncher",
                                jvm.get("sun.java.command")),
                () ->
                        assertTrue(
                                jvm.get("java.class.path")
                                        .startsWith(
                                                installed
                                                        .applicationDirectory()
                                                        .resolve("lib/app")
                                                        .toString()),
                                "the class path is the installed lib/app"),
                () ->
                        assertEquals(
                                Long.toString(launched.endpoint().pid()),
                                jvm.get("pid"),
                                "the port file's pid is the JVM's own"),
                () -> assertEquals(home.home().toString(), jvm.get("HOME")),
                () -> assertEquals(home.dataHome().toString(), jvm.get("XDG_DATA_HOME")),
                () -> assertEquals(String.join(":", PATH), jvm.get("PATH")),
                () -> assertFalse(jvm.containsKey("JAVA_HOME"), "JAVA_HOME is unset"),
                () -> assertEquals(cwd.toString(), jvm.get("user.dir"), "the launch directory"));
        if (previous != null) {
            assertAll(
                    "a restart is a new process",
                    () ->
                            assertNotEquals(
                                    previous.endpoint().pid(),
                                    launched.endpoint().pid(),
                                    "a new pid"),
                    () ->
                            assertNotEquals(
                                    previous.endpoint().token(),
                                    launched.endpoint().token(),
                                    "a new bridge"));
        }
        return launched.driver();
    }

    /**
     * Closes the running application the way a user does -- the window manager's close message --
     * and requires the process's own exit, status 0, reaped, with nothing left in its launch
     * directory.
     *
     * @throws IOException if the window helper cannot be started or the directory read
     */
    void closeApplication() throws IOException {
        LaunchedCometGui launched = current();
        long pid = launched.endpoint().pid();
        launched.closeWindow();
        int status = launched.awaitExit(STOP);
        assertAll(
                "the packaged CometGUI's end, after its window was closed",
                () -> assertEquals(0, status, () -> "exit status; it printed " + launched.output()),
                () -> assertTrue(ProcessHandle.of(pid).isEmpty(), "pid " + pid + " is reaped"));
        assertLaunchDirectoriesUntouched();
    }

    /**
     * Fails unless every launch directory is still empty: the product writes nothing into the
     * directory it was started from.
     *
     * @throws IOException if a directory cannot be listed
     */
    void assertLaunchDirectoriesUntouched() throws IOException {
        List<String> written = new ArrayList<>();
        for (int number = 1; number <= launches.size(); number++) {
            Path cwd = work.resolve("launch-" + number).resolve("cwd");
            try (Stream<Path> listed = Files.list(cwd)) {
                listed.forEach(entry -> written.add(entry.toString()));
            }
        }
        assertEquals(List.of(), written, "nothing is written into a launch directory");
    }

    /**
     * Fails unless the install tree is exactly as installed: every entry's kind, size, bytes, mtime
     * and inode, against the snapshot taken before the first launch.
     *
     * @throws IOException if the tree cannot be read
     */
    void assertInstallTreeUntouched() throws IOException {
        installedTree.assertUnchangedIn(
                DirectorySnapshot.of(installed.installDirectory()),
                "the installed application wrote nothing into its own install tree");
    }

    /**
     * How many entries the install tree holds, for the report.
     *
     * @return the count
     */
    int installTreeEntries() {
        return installedTree.entries().size();
    }

    /**
     * The converter ran on the installed runtime's {@code bin/java}: by the run's own {@code
     * limelight.converted} record, the launcher it names, the first element of its argument array,
     * and its exit.
     *
     * @param conversions the run's {@code limelight.converted} payloads
     */
    void assertConvertedOnTheInstalledRuntime(List<Map<String, Object>> conversions) {
        assertEquals(1, conversions.size(), "one limelight.converted event");
        Map<String, Object> converted = conversions.get(0);
        String java = installed.runtimeJava().toString();
        assertAll(
                "the converter ran on the installed runtime: " + converted,
                () -> assertEquals(java, converted.get("converter.java"), "converter.java"),
                () -> assertEquals(java, converted.get("argv.0"), "the argument array's first"),
                () -> assertEquals("-jar", converted.get("argv.1")),
                () -> assertEquals("0", converted.get("process.exit-code"), "exit 0"));
    }

    /**
     * Ends every launch still running, stops the display, and removes the installation.
     *
     * @throws IOException if the uninstall cannot be started
     */
    @Override
    public void close() throws IOException {
        List<Throwable> failures = new ArrayList<>();
        for (LaunchedCometGui launched : launches) {
            try {
                launched.close();
            } catch (RuntimeException | Error failed) {
                failures.add(failed);
            }
        }
        try {
            display.close();
        } finally {
            installed.uninstall();
        }
        if (!failures.isEmpty()) {
            throw new AssertionError("ending a launch failed: " + failures, failures.get(0));
        }
    }

    @Override
    public String toString() {
        return "Tier B over " + installed + ", home " + Objects.toString(home);
    }
}
