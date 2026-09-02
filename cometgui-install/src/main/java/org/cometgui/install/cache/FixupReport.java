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

package org.cometgui.install.cache;

import java.util.List;
import java.util.Objects;

/**
 * What the platform fix-up step actually did, so that a caller can check it rather than trust it.
 *
 * <p>Both lists hold what <em>changed</em>, not what was attempted. A file that already carried the
 * executable bit is not listed, and a file that carried no quarantine attribute is not listed -- so
 * a test can assert that the fix-up was the thing that made the binary runnable, rather than
 * asserting that a method was called.
 *
 * @param madeExecutable the files whose executable bits this step set, relative to the install
 *     directory
 * @param quarantineCleared the files whose {@code com.apple.quarantine} attribute this step
 *     removed, relative to the install directory
 */
public record FixupReport(List<String> madeExecutable, List<String> quarantineCleared) {

    /**
     * Validates the report and takes defensive, immutable copies of both lists.
     *
     * @throws NullPointerException if either list is {@code null}
     */
    public FixupReport {
        madeExecutable = List.copyOf(Objects.requireNonNull(madeExecutable, "madeExecutable"));
        quarantineCleared =
                List.copyOf(Objects.requireNonNull(quarantineCleared, "quarantineCleared"));
    }

    /**
     * The files whose executable bits were set, immutable.
     *
     * @return the paths, possibly empty
     */
    @Override
    public List<String> madeExecutable() {
        return List.copyOf(madeExecutable);
    }

    /**
     * The files whose quarantine attribute was removed, immutable.
     *
     * @return the paths, possibly empty
     */
    @Override
    public List<String> quarantineCleared() {
        return List.copyOf(quarantineCleared);
    }

    /**
     * Whether the step changed nothing.
     *
     * @return {@code true} when neither list holds anything
     */
    public boolean changedNothing() {
        return madeExecutable.isEmpty() && quarantineCleared.isEmpty();
    }
}
