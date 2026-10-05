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

package org.cometgui.ui.viewmodel.params;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.cometgui.params.comet.writer.WrittenParams;

/**
 * What became of one attempt to save the configuration: the file written, or why nothing was.
 *
 * @param written the file, its digests and size, present exactly when it was written
 * @param refusals why it was not, one line each; empty exactly when it was written
 */
public record SaveOutcome(Optional<WrittenParams> written, List<String> refusals) {

    /**
     * Validates the pairing and takes an immutable copy.
     *
     * @throws IllegalArgumentException if a written file carries refusals, or a refusal none
     */
    public SaveOutcome {
        Objects.requireNonNull(written, "written");
        refusals = List.copyOf(refusals);
        if (written.isPresent() != refusals.isEmpty()) {
            throw new IllegalArgumentException(
                    written.isPresent()
                            ? "a saved file has no refusal"
                            : "a refused save has to say why");
        }
    }

    /**
     * A file written.
     *
     * @param file the file
     * @return the outcome
     */
    static SaveOutcome saved(WrittenParams file) {
        return new SaveOutcome(Optional.of(file), List.of());
    }

    /**
     * A save refused.
     *
     * @param reasons why, at least one
     * @return the outcome
     */
    static SaveOutcome refused(List<String> reasons) {
        return new SaveOutcome(Optional.empty(), reasons);
    }

    /**
     * Whether the file was written.
     *
     * @return {@code true} when it was
     */
    public boolean accepted() {
        return written.isPresent();
    }
}
