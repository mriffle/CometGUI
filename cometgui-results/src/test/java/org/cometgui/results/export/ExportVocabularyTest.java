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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.cometgui.results.filtering.PeptideQValueFilter;
import org.cometgui.results.filtering.PsmQValueFilter;
import org.cometgui.results.filtering.store.Category;
import org.cometgui.results.filtering.store.TableKind;
import org.cometgui.results.parser.SignConsistency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Every word an export writes, pinned as a hand-typed literal. */
class ExportVocabularyTest {

    @Test
    @DisplayName("the tables, categories, filters and sign verdicts have the words pinned here")
    void words() {
        assertEquals("target-psms", ExportVocabulary.table(TableKind.TARGET_PSMS));
        assertEquals("decoy-psms", ExportVocabulary.table(TableKind.DECOY_PSMS));
        assertEquals("target-peptides", ExportVocabulary.table(TableKind.TARGET_PEPTIDES));
        assertEquals("decoy-peptides", ExportVocabulary.table(TableKind.DECOY_PEPTIDES));
        assertEquals("passing", ExportVocabulary.category(Category.PASSING));
        assertEquals("unknown-q-value", ExportVocabulary.category(Category.UNKNOWN_Q_VALUE));
        assertEquals("failing", ExportVocabulary.category(Category.FAILING));
        assertEquals("all", ExportVocabulary.category(Category.ALL));
        assertEquals("psm-q-value", ExportVocabulary.filter(PsmQValueFilter.DEFAULT));
        assertEquals("peptide-q-value", ExportVocabulary.filter(PeptideQValueFilter.DEFAULT));
        assertEquals("all positive", WeightsTable.words(SignConsistency.ALL_POSITIVE));
        assertEquals("all negative", WeightsTable.words(SignConsistency.ALL_NEGATIVE));
        assertEquals("mixed", WeightsTable.words(SignConsistency.MIXED));
        assertEquals("all zero", WeightsTable.words(SignConsistency.ALL_ZERO));
    }

    @Test
    @DisplayName("null is refused by name")
    void nulls() {
        assertEquals(
                "kind",
                assertThrows(NullPointerException.class, () -> ExportVocabulary.table(null))
                        .getMessage());
        assertEquals(
                "category",
                assertThrows(NullPointerException.class, () -> ExportVocabulary.category(null))
                        .getMessage());
        assertEquals(
                "filter",
                assertThrows(NullPointerException.class, () -> ExportVocabulary.filter(null))
                        .getMessage());
    }
}
