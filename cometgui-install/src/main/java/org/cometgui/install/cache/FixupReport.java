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

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * What the platform fix-up step actually did, so that a caller can check it rather than trust it.
 *
 * <p>The first two lists hold what <em>changed</em>, not what was attempted. A file that already
 * carried the executable bit is not listed, and a file that carried no quarantine attribute is not
 * listed -- so a test can assert that the fix-up was the thing that made the binary runnable,
 * rather than asserting that a method was called.
 *
 * <p>The third list exists because the first macOS run in this project found the quarantine step
 * changing nothing and reporting nothing (see {@link PlatformFixups}). A file the step could not
 * show to be free of {@code com.apple.quarantine} afterwards is named here, so "nothing was
 * cleared" and "nothing could be cleared" are different reports. A file is never in both quarantine
 * lists.
 *
 * @param madeExecutable the files whose executable bits this step set, relative to the install
 *     directory
 * @param quarantineCleared the files whose {@code com.apple.quarantine} attribute this step
 *     removed, and saw removed when it looked again, relative to the install directory
 * @param quarantineNotCleared the files that still carried, or could not be shown not to carry,
 *     {@code com.apple.quarantine} after this step, relative to the install directory
 */
public record FixupReport(
        List<String> madeExecutable,
        List<String> quarantineCleared,
        List<String> quarantineNotCleared) {

    /**
     * Validates the report and takes defensive, immutable copies of all three lists.
     *
     * @throws NullPointerException if any list is {@code null}
     * @throws IllegalArgumentException if a file is reported both cleared and not cleared
     */
    public FixupReport {
        madeExecutable = List.copyOf(Objects.requireNonNull(madeExecutable, "madeExecutable"));
        quarantineCleared =
                List.copyOf(Objects.requireNonNull(quarantineCleared, "quarantineCleared"));
        quarantineNotCleared =
                List.copyOf(Objects.requireNonNull(quarantineNotCleared, "quarantineNotCleared"));
        Set<String> both = new HashSet<>(quarantineCleared);
        both.retainAll(quarantineNotCleared);
        if (!both.isEmpty()) {
            throw new IllegalArgumentException(
                    "a file cannot be reported both cleared of and still carrying the quarantine"
                            + " attribute: "
                            + both.stream().sorted().toList());
        }
    }

    /**
     * A report in which every quarantined file was cleared, or the step did not run.
     *
     * @param madeExecutable the files whose executable bits this step set
     * @param quarantineCleared the files whose quarantine attribute this step removed
     * @throws NullPointerException if either list is {@code null}
     */
    public FixupReport(List<String> madeExecutable, List<String> quarantineCleared) {
        this(madeExecutable, quarantineCleared, List.of());
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
     * The files the step could not show free of the quarantine attribute, immutable.
     *
     * @return the paths, possibly empty
     */
    @Override
    public List<String> quarantineNotCleared() {
        return List.copyOf(quarantineNotCleared);
    }

    /**
     * Whether the step changed nothing.
     *
     * <p>A file that could not be cleared is not a change, so a report naming only such files has
     * changed nothing -- and is not a success either; see {@link #quarantineNotCleared()}.
     *
     * @return {@code true} when neither the executable list nor the cleared list holds anything
     */
    public boolean changedNothing() {
        return madeExecutable.isEmpty() && quarantineCleared.isEmpty();
    }
}
