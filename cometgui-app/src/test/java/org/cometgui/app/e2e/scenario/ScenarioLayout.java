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

package org.cometgui.app.e2e.scenario;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.cometgui.app.e2e.support.FreshHome;

/**
 * Where one run of the canonical scenario keeps everything: every path is a parameter, never a
 * constant, so that the same steps run in Tier A (an in-process application over a fresh home) and
 * in Tier B (a packaged application in another process, over a home of the test's choosing -- Phase
 * 14 unit 7).
 *
 * @param home the fresh home the application is pointed at; its {@link
 *     FreshHome#applicationDataDirectory()} is where the tool cache and the project are looked for
 * @param inputs the directory holding the spectrum files and the FASTA, outside the home, which the
 *     scenario snapshots before the run (nothing may be written beside the inputs)
 * @param spectra the two spectrum files, in {@code inputs}, in the order they are chosen
 * @param database the FASTA, in {@code inputs}
 * @param projectDirectory where the application's default project is expected to appear
 * @param toolCache where the application's installed tools are expected to appear
 * @param manifest the shipped tool manifest the scenario reads with its own reader
 * @param work a directory of the scenario's own, outside the home and the inputs, for the oracles'
 *     scratch files
 */
public record ScenarioLayout(
        FreshHome home,
        Path inputs,
        List<Path> spectra,
        Path database,
        Path projectDirectory,
        Path toolCache,
        Path manifest,
        Path work) {

    /**
     * Requires every part, two spectrum files, and absolute paths.
     *
     * @throws NullPointerException if a part is {@code null}
     * @throws IllegalArgumentException if there are not exactly two spectrum files, or a path is
     *     relative
     */
    public ScenarioLayout {
        Objects.requireNonNull(home, "home");
        spectra = List.copyOf(spectra);
        if (spectra.size() != 2) {
            throw new IllegalArgumentException(
                    "the canonical scenario searches two spectrum files, not " + spectra);
        }
        for (Path path :
                List.of(
                        inputs,
                        spectra.get(0),
                        spectra.get(1),
                        database,
                        projectDirectory,
                        toolCache,
                        manifest,
                        work)) {
            if (!path.isAbsolute()) {
                throw new IllegalArgumentException("not an absolute path: " + path);
            }
        }
    }

    /**
     * The layout the product's documented defaults give a fresh home: the project is {@code
     * projects/default} and the tool cache {@code tools}, both under the application data directory
     * -- the rule typed out here from the product's documentation, never asked of the product.
     *
     * @param home the fresh home
     * @param inputs the inputs' directory
     * @param spectra the two spectrum files
     * @param database the FASTA
     * @param manifest the shipped tool manifest
     * @param work the oracles' scratch directory
     * @return the layout
     */
    public static ScenarioLayout defaultsUnder(
            FreshHome home,
            Path inputs,
            List<Path> spectra,
            Path database,
            Path manifest,
            Path work) {
        Path data = home.applicationDataDirectory();
        return new ScenarioLayout(
                home,
                inputs,
                spectra,
                database,
                data.resolve("projects").resolve("default"),
                data.resolve("tools"),
                manifest,
                work);
    }
}
