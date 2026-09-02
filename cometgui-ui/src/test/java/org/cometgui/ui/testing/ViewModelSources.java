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

package org.cometgui.ui.testing;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Reads the view-model package's own source files, for the tests that assert a property of the code
 * itself rather than of its behaviour.
 *
 * <p>There is exactly one such property and it is a real constraint rather than a style rule: the
 * view-model layer may not touch the JavaFX scene graph, the toolkit or the application thread. No
 * behavioural test can see that, because the failure mode is a class that works perfectly well in
 * an application and throws {@code IllegalStateException: Toolkit not initialized} in a test -- by
 * which time the layer has already stopped being independently testable, which was the whole point
 * of having it.
 *
 * <p>The directory is located rather than assumed. Surefire runs with the module directory as its
 * working directory, but a test that silently found nothing would pass over any number of
 * violations, so a missing directory is an error naming the directory that was looked for.
 */
public final class ViewModelSources {

    /** The package's source directory, relative to the module or to the repository root. */
    private static final String RELATIVE = "src/main/java/org/cometgui/ui/viewmodel";

    private ViewModelSources() {}

    /**
     * The view-model package's source directory.
     *
     * @return the directory, which exists and holds at least one {@code .java} file
     * @throws IllegalStateException if it cannot be found from the working directory, naming both
     */
    public static Path directory() {
        Path fromModule = Path.of(RELATIVE);
        if (Files.isDirectory(fromModule)) {
            return fromModule;
        }
        Path fromRoot = Path.of("cometgui-ui").resolve(RELATIVE);
        if (Files.isDirectory(fromRoot)) {
            return fromRoot;
        }
        throw new IllegalStateException(
                "cannot find the view-model sources: neither "
                        + fromModule.toAbsolutePath()
                        + " nor "
                        + fromRoot.toAbsolutePath()
                        + " is a directory (working directory: "
                        + Path.of("").toAbsolutePath()
                        + ")");
    }

    /**
     * Every source file in the view-model package, by file name.
     *
     * @return file name to file content, in file-name order, never empty
     * @throws IllegalStateException if the directory holds no {@code .java} file, which would make
     *     every test built on this vacuously true
     * @throws UncheckedIOException if a file cannot be read
     */
    public static Map<String, String> all() {
        Path directory = directory();
        List<Path> files;
        try (Stream<Path> listing = Files.list(directory)) {
            files = listing.sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot list " + directory.toAbsolutePath(), e);
        }
        Map<String, String> sources = new LinkedHashMap<>();
        for (Path file : files) {
            // relativize rather than getFileName: the latter is declared @Nullable, which SpotBugs
            // reports at threshold=Low, and the relative name is what this map is keyed by anyway.
            String name = directory.relativize(file).toString();
            if (name.endsWith(".java")) {
                sources.put(name, read(file));
            }
        }
        if (sources.isEmpty()) {
            throw new IllegalStateException(
                    "no .java file in "
                            + directory.toAbsolutePath()
                            + "; a source scan over an"
                            + " empty directory proves nothing");
        }
        return sources;
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + file.toAbsolutePath(), e);
        }
    }
}
