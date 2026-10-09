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

package org.cometgui.results.export;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DecimalStyle;
import java.util.Locale;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Names for new export files, and the lock that keeps two exports from choosing the same one.
 *
 * <p><strong>An existing file is never overwritten; a fresh name is chosen instead.</strong> An
 * export's name says what it is -- {@code <stem>_<yyyyMMdd'T'HHmmss.SSS'Z'>.tsv} -- and if that
 * name, or its sidecar's ({@code <name>.json}), is taken by anything at all (a file, a directory, a
 * dangling link), {@code -2}, {@code -3} ... is added before the extension until both are free.
 * Refusing instead would make a second export in the same millisecond an error the scientist did
 * nothing to cause.
 *
 * <p><strong>The lock.</strong> The name is chosen, and the whole export written and recorded,
 * while {@link #LOCK} is held, so no two exports in this JVM can choose one name, and no two open
 * the run's provenance event log at once (two open logs would number their events twice). The lock
 * cannot see another process: two CometGUI processes exporting the same table, cutoff and category
 * of one run in the same millisecond could still collide, and the atomic writer's rename would then
 * replace the first file. That is the one residue, stated rather than hidden.
 */
final class ExportFiles {

    /** Held for the whole of every export in this JVM. */
    static final ReentrantLock LOCK = new ReentrantLock();

    /** The most suffixes tried before giving up. */
    static final int MAX_ATTEMPTS = 10_000;

    /** The extension of an exported table or weights file. */
    static final String TABLE_EXTENSION = ".tsv";

    /** What a sidecar's name adds to its export's name. */
    static final String SIDECAR_EXTENSION = ".json";

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss.SSS'Z'", Locale.ROOT)
                    .withZone(ZoneOffset.UTC)
                    .withDecimalStyle(DecimalStyle.STANDARD);

    private ExportFiles() {
        throw new AssertionError("ExportFiles is never instantiated");
    }

    /**
     * The time part of an export's name.
     *
     * @param created when the export was made
     * @return for example {@code 20261009T120000.123Z}, in UTC
     */
    static String stamp(Instant created) {
        return STAMP.format(created);
    }

    /**
     * A name in a directory that neither the export nor its sidecar has yet.
     *
     * @param directory the exports directory
     * @param stem the name before its extension
     * @return {@code <stem>.tsv}, or {@code <stem>-<n>.tsv} for the smallest {@code n >= 2} that is
     *     free
     * @throws FileAlreadyExistsException if {@value #MAX_ATTEMPTS} names are all taken
     */
    static Path fresh(Path directory, String stem) throws IOException {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            String name = (attempt == 1 ? stem : stem + "-" + attempt) + TABLE_EXTENSION;
            Path candidate = directory.resolve(name);
            if (free(candidate) && free(sidecarOf(candidate))) {
                return candidate;
            }
        }
        throw new FileAlreadyExistsException(
                directory.resolve(stem + TABLE_EXTENSION).toString(),
                null,
                "no free name for a new export after " + MAX_ATTEMPTS + " attempts");
    }

    /**
     * An export's sidecar.
     *
     * @param export the export file
     * @return {@code <export>.json} beside it
     */
    static Path sidecarOf(Path export) {
        return export.resolveSibling(export.getFileName() + SIDECAR_EXTENSION);
    }

    private static boolean free(Path candidate) {
        return !Files.exists(candidate, LinkOption.NOFOLLOW_LINKS);
    }
}
