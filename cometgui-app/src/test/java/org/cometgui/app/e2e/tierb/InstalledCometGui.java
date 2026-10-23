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

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.cometgui.app.testing.RealSearch;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.tools.api.ToolRunOutcome;
import org.cometgui.tools.api.ToolRunner;
import org.cometgui.tools.process.ProcessService;

/**
 * The packaged archive, installed the way a user installs it (Phase 14 design decision P14-10):
 * {@code scripts/package/install-archive.sh} checks it against its SHA-256 file, extracts it into a
 * fresh directory outside the repository and makes the tree read-only -- so anything that writes
 * into its own install directory fails loudly. Run through the process service.
 */
public final class InstalledCometGui {

    /** Where {@code scripts/package/build-app-image.sh} writes the Linux archive, from the root. */
    static final String BUILT = "_build/package/linux";

    private static final String INSTALL_SCRIPT = "scripts/package/install-archive.sh";

    private static final Duration SCRIPT_BOUND = Duration.ofMinutes(5);

    private final Path installDirectory;

    private InstalledCometGui(Path installDirectory) {
        this.installDirectory = installDirectory;
    }

    /**
     * The archive {@code build-app-image.sh} built, which is the artefact Tier B tests.
     *
     * @return the one {@code CometGUI-*-linux-x86_64.tar.gz} in {@value #BUILT}
     * @throws IOException if the directory cannot be listed
     * @throws AssertionError if there is not exactly one, naming the command that builds it
     */
    public static Path builtArchive() throws IOException {
        Path built = RealSearch.repositoryRoot().resolve(BUILT);
        List<Path> archives = new ArrayList<>();
        if (Files.isDirectory(built)) {
            try (DirectoryStream<Path> listed =
                    Files.newDirectoryStream(built, "CometGUI-*-linux-x86_64.tar.gz")) {
                listed.forEach(archives::add);
            }
        }
        if (archives.size() != 1) {
            throw new AssertionError(
                    "Tier B tests the packaged archive, and "
                            + built
                            + " holds "
                            + archives.size()
                            + " of them ("
                            + archives
                            + "). Build it with: bash scripts/package/build-app-image.sh");
        }
        return archives.get(0);
    }

    /**
     * Installs an archive.
     *
     * @param archive the {@code .tar.gz}, with its {@code .sha256} beside it
     * @param installDirectory where to install: must not exist, outside the repository
     * @return the installation
     * @throws IOException if the script cannot be started
     * @throws AssertionError if the install failed, with the script's output
     */
    public static InstalledCometGui install(Path archive, Path installDirectory)
            throws IOException {
        ToolRunOutcome outcome =
                script(
                        List.of(
                                "/bin/bash",
                                RealSearch.repositoryRoot().resolve(INSTALL_SCRIPT).toString(),
                                archive.toString(),
                                installDirectory.toString()));
        InstalledCometGui installed = new InstalledCometGui(installDirectory.toRealPath());
        if (!outcome.exitedZero() || !Files.isExecutable(installed.launcher())) {
            throw new AssertionError(
                    INSTALL_SCRIPT + " did not install " + archive + ": " + outcome.joinedOutput());
        }
        return installed;
    }

    /**
     * The directory the archive was installed into.
     *
     * @return the install directory
     */
    public Path installDirectory() {
        return installDirectory;
    }

    /**
     * The installed application image.
     *
     * @return {@code <install>/CometGUI}
     */
    public Path applicationDirectory() {
        return installDirectory.resolve("CometGUI");
    }

    /**
     * The installed native launcher a user runs.
     *
     * @return {@code <install>/CometGUI/bin/CometGUI}
     */
    public Path launcher() {
        return applicationDirectory().resolve("bin").resolve("CometGUI");
    }

    /**
     * The installed bundled runtime.
     *
     * @return {@code <install>/CometGUI/lib/runtime}
     */
    public Path runtime() {
        return applicationDirectory().resolve("lib").resolve("runtime");
    }

    /**
     * The installed runtime's {@code java}, from which the product starts the converter and PDV.
     *
     * @return {@code <install>/CometGUI/lib/runtime/bin/java}
     */
    public Path runtimeJava() {
        return runtime().resolve("bin").resolve("java");
    }

    /**
     * Removes the installation (restoring write permission first).
     *
     * @throws IOException if the script cannot be started
     * @throws AssertionError if it did not remove the tree
     */
    public void uninstall() throws IOException {
        ToolRunOutcome outcome =
                script(
                        List.of(
                                "/bin/bash",
                                RealSearch.repositoryRoot().resolve(INSTALL_SCRIPT).toString(),
                                "--uninstall",
                                installDirectory.toString()));
        if (!outcome.exitedZero() || Files.exists(installDirectory)) {
            throw new AssertionError(
                    INSTALL_SCRIPT
                            + " --uninstall did not remove "
                            + installDirectory
                            + ": "
                            + outcome.joinedOutput());
        }
    }

    private static ToolRunOutcome script(List<String> argv) throws IOException {
        Objects.requireNonNull(argv, "argv");
        return new ToolRunner(new ProcessService(Clock.systemUTC()), SCRIPT_BOUND)
                .run(
                        new ToolCommand(
                                argv,
                                RealSearch.repositoryRoot(),
                                Map.of("PATH", "/usr/bin:/bin", "LC_ALL", "C.UTF-8")));
    }

    @Override
    public String toString() {
        return "CometGUI installed at " + installDirectory;
    }
}
