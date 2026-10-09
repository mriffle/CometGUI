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

import java.util.Objects;
import org.cometgui.results.filtering.PsmQValueFilter;
import org.cometgui.results.filtering.QValueFilter;
import org.cometgui.results.filtering.store.Category;
import org.cometgui.results.filtering.store.TableKind;

/**
 * The words an export writes for the enums it records -- in its file name, its sidecar and its
 * provenance event. Each is a field-free mapping written out here rather than derived from {@code
 * name()}, so renaming a Java constant cannot change the format of an export already written; the
 * tests pin every word as a hand-typed literal.
 */
final class ExportVocabulary {

    private ExportVocabulary() {
        throw new AssertionError("ExportVocabulary is never instantiated");
    }

    /**
     * A table's word.
     *
     * @param kind the table
     * @return {@code target-psms}, {@code decoy-psms}, {@code target-peptides} or {@code
     *     decoy-peptides}
     */
    static String table(TableKind kind) {
        return switch (Objects.requireNonNull(kind, "kind")) {
            case TARGET_PSMS -> "target-psms";
            case DECOY_PSMS -> "decoy-psms";
            case TARGET_PEPTIDES -> "target-peptides";
            case DECOY_PEPTIDES -> "decoy-peptides";
        };
    }

    /**
     * A category's word.
     *
     * @param category the category
     * @return {@code passing}, {@code unknown-q-value}, {@code failing} or {@code all}
     */
    static String category(Category category) {
        return switch (Objects.requireNonNull(category, "category")) {
            case PASSING -> "passing";
            case UNKNOWN_Q_VALUE -> "unknown-q-value";
            case FAILING -> "failing";
            case ALL -> "all";
        };
    }

    /**
     * Which of the two display filters it is.
     *
     * @param filter the filter
     * @return {@code psm-q-value} or {@code peptide-q-value}
     */
    static String filter(QValueFilter filter) {
        Objects.requireNonNull(filter, "filter");
        return filter instanceof PsmQValueFilter ? "psm-q-value" : "peptide-q-value";
    }
}
