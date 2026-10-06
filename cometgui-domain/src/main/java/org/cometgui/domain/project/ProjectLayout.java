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

package org.cometgui.domain.project;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Where a project's own files are, as paths: the specification's <em>Project model</em>, the
 * project half.
 *
 * <pre>
 *   MyProject/
 *       project.json
 *       project.lock
 *       presets/
 *       index-cache/
 *       runs/
 * </pre>
 *
 * <p>Pure: computing a path touches nothing on disk. The run directories under {@code runs/} are
 * {@link org.cometgui.domain.run.RunLayout}'s, which builds on this one; this type knows nothing
 * about runs, so the two packages do not depend on each other in both directions.
 *
 * <p>{@code index-cache/} is design decision P8-8's: a Comet index built once is reused by every
 * run of the project that needs the same one, so it lives in the project rather than in a run.
 *
 * @param root the project directory, absolute and normalised
 */
public record ProjectLayout(Path root) {

    /** The project descriptor's file name. */
    public static final String PROJECT_FILE_NAME = "project.json";

    /** The lock file's name. */
    public static final String LOCK_FILE_NAME = "project.lock";

    /** The directory holding one directory per run. */
    public static final String RUNS_DIRECTORY_NAME = "runs";

    /** The directory holding the project's parameter presets. */
    public static final String PRESETS_DIRECTORY_NAME = "presets";

    /** The directory holding cached Comet indexes, one subdirectory per key (P8-8). */
    public static final String INDEX_CACHE_DIRECTORY_NAME = "index-cache";

    /**
     * Normalises the root.
     *
     * @throws NullPointerException if {@code root} is {@code null}
     * @throws IllegalArgumentException if {@code root} is not absolute, naming it
     */
    public ProjectLayout {
        Objects.requireNonNull(root, "root");
        if (!root.isAbsolute()) {
            throw new IllegalArgumentException("a project directory must be absolute: " + root);
        }
        root = root.normalize();
    }

    /**
     * The project descriptor.
     *
     * @return {@code <root>/project.json}
     */
    public Path projectFile() {
        return root.resolve(PROJECT_FILE_NAME);
    }

    /**
     * The lock file.
     *
     * @return {@code <root>/project.lock}
     */
    public Path lockFile() {
        return root.resolve(LOCK_FILE_NAME);
    }

    /**
     * The directory of runs.
     *
     * @return {@code <root>/runs}
     */
    public Path runsDirectory() {
        return root.resolve(RUNS_DIRECTORY_NAME);
    }

    /**
     * The presets directory.
     *
     * @return {@code <root>/presets}
     */
    public Path presetsDirectory() {
        return root.resolve(PRESETS_DIRECTORY_NAME);
    }

    /**
     * The index cache.
     *
     * @return {@code <root>/index-cache}
     */
    public Path indexCacheDirectory() {
        return root.resolve(INDEX_CACHE_DIRECTORY_NAME);
    }
}
