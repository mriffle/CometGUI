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

import static org.cometgui.ui.viewmodel.params.Sessions.C02;
import static org.cometgui.ui.viewmodel.params.Sessions.C03;
import static org.cometgui.ui.viewmodel.params.Sessions.startingIn;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.cometgui.params.comet.migration.MigrationEntry;
import org.cometgui.params.comet.migration.MigrationEntry.Outcome;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.parser.CometParamsParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The migration review (decision P7-3, {@code R-PARAM-13}) on real migrations of each offered
 * release's own starting set: a clean 2026.02.2 migration has its one {@code CONVERTED} row and no
 * blocking row; a terminus outside 0-3 at a distance (CONSTRUCTED edit) is one blocking row until
 * accepted or set; the way back converts the other way.
 */
class MigrationReviewViewModelTest {

    private static final String INDEX_REASON_START =
            "index_search_type = 1 (Comet 2026.02.2) is written -1 for Comet 2026.03.0, which"
                    + " means the same there:";

    @Test
    @DisplayName("nothing under review: no rows, no headline, nothing to accept")
    void nothing() {
        MigrationReviewViewModel review = new MigrationReviewViewModel(startingIn(C03));
        assertFalse(review.underReview());
        assertEquals("", review.headline());
        assertEquals(List.of(), review.rows());
        assertEquals(0, review.unchangedCount());
        assertEquals(0, review.unresolvedCount());
        assertEquals(
                EditOutcome.refused("No migration is under review, so there is nothing to accept."),
                review.accept("variable_mod01"));
    }

    @Test
    @DisplayName("a clean 2026.02.2 migration: one CONVERTED row with its reason, nothing blocking")
    void clean() {
        ParameterSession session = startingIn(C02);
        session.selectRelease(C03);
        MigrationReviewViewModel review = new MigrationReviewViewModel(session);
        assertTrue(review.underReview());
        assertEquals(
                "Comet 2026.02.2 -> 2026.03.0: 118 parameters, 1 changed, 117 unchanged; 0 need"
                        + " your decision",
                review.headline());
        List<MigrationRow> rows = review.rows();
        assertEquals(1, rows.size());
        MigrationRow index = rows.get(0);
        assertEquals("index_search_type", index.parameter());
        assertEquals(Outcome.CONVERTED, index.entry().outcome());
        assertEquals("Converted", index.outcomeWords());
        assertEquals(Optional.of("Index type for an index built on demand"), index.displayName());
        assertEquals("1", index.sourceValue());
        assertEquals("-1", index.valueNow());
        assertTrue(
                index.entry().explanation().startsWith(INDEX_REASON_START),
                index.entry().explanation());
        assertFalse(index.needsDecision());
        assertFalse(index.blocking());
        assertEquals("", index.stateText());
        assertEquals(Optional.of("index_search_type"), index.focusTarget());
        assertEquals(117, review.unchangedCount());
    }

    @Test
    @DisplayName("2026.03.0 back to 2026.02.2: -1 is written 1")
    void wayBack() {
        ParameterSession session = startingIn(C03);
        session.selectRelease(C02);
        MigrationRow index = new MigrationReviewViewModel(session).rows().get(0);
        assertEquals("index_search_type", index.parameter());
        assertEquals("-1", index.sourceValue());
        assertEquals("1", index.valueNow());
        assertEquals(Outcome.CONVERTED, index.entry().outcome());
    }

    @Nested
    @DisplayName("a terminus outside 0-3 at a distance, migrated to 2026.03.0")
    class NeedsAttention {

        private ParameterSession migrated() {
            ParameterSession session = startingIn(C02);
            // CONSTRUCTED edit: an active oxidation with terminus 4 at distance 2
            session.edit("variable_mod01", "15.9949 M 0 3 2 4 0 0.0");
            assertEquals(EditOutcome.applied(), session.selectRelease(C03));
            return session;
        }

        @Test
        @DisplayName("is one blocking row, in the report as an error")
        void blocks() {
            ParameterSession session = migrated();
            MigrationReviewViewModel review = new MigrationReviewViewModel(session);
            assertEquals(
                    "Comet 2026.02.2 -> 2026.03.0: 118 parameters, 2 changed, 116 unchanged; 1"
                            + " needs your decision",
                    review.headline());
            List<MigrationRow> rows = review.rows();
            assertEquals(
                    List.of("index_search_type", "variable_mod01"),
                    rows.stream().map(MigrationRow::parameter).toList());
            MigrationRow slot = rows.get(1);
            assertEquals(Outcome.NEEDS_ATTENTION, slot.entry().outcome());
            assertEquals("Needs your decision", slot.outcomeWords());
            assertEquals(Optional.of("Variable modification 1"), slot.displayName());
            assertEquals("15.9949 M 0 3 2 4 0 0.0", slot.sourceValue());
            assertEquals("15.9949 M 0 3 -1 0 0 0.0", slot.valueNow());
            assertTrue(slot.needsDecision());
            assertTrue(slot.blocking());
            assertEquals("Blocks the run until you decide", slot.stateText());
            assertEquals(Optional.of("variable_mod01"), slot.focusTarget());
            assertEquals(Optional.empty(), slot.resolution());
            assertTrue(
                    slot.entry()
                            .explanation()
                            .startsWith(
                                    "variable_mod01 = 15.9949 M 0 3 2 4 0 0.0 (Comet 2026.02.2) has"
                                            + " no equivalent in Comet 2026.03.0:"),
                    slot.entry().explanation());
            assertEquals(1, review.unresolvedCount());
            assertTrue(session.report().hasErrors());
            assertEquals(1, rows.stream().filter(MigrationRow::blocking).count());
        }

        @Test
        @DisplayName("accepting the value resolves it, and says so")
        void accept() {
            ParameterSession session = migrated();
            MigrationReviewViewModel review = new MigrationReviewViewModel(session);
            assertEquals(EditOutcome.applied(), review.accept("variable_mod01"));
            MigrationRow slot = review.rows().get(1);
            assertFalse(slot.blocking());
            assertEquals(
                    Optional.of("Resolved: you accepted the value it holds"), slot.resolution());
            assertEquals("Resolved: you accepted the value it holds", slot.stateText());
            assertEquals(0, review.unresolvedCount());
            assertFalse(session.report().hasErrors());
            assertEquals(
                    EditOutcome.refused(
                            "variable_mod01 has already been resolved in this migration's review"),
                    review.accept("variable_mod01"));
            assertEquals(
                    EditOutcome.refused(
                            "index_search_type is not an entry of the migration from Comet"
                                    + " 2026.02.2 to Comet 2026.03.0 that needs attention, so"
                                    + " there is nothing to resolve"),
                    review.accept("index_search_type"));
        }

        @Test
        @DisplayName("a value set at the field resolves it too")
        void setAtTheField() {
            ParameterSession session = migrated();
            MigrationReviewViewModel review = new MigrationReviewViewModel(session);
            String target = review.rows().get(1).focusTarget().orElseThrow();
            assertEquals(
                    EditOutcome.applied(),
                    session.field(target).setText("15.9949 M 0 2 -1 0 0 0.0"));
            MigrationRow slot = review.rows().get(1);
            assertEquals(Optional.of("Resolved: you set its value"), slot.resolution());
            assertEquals("15.9949 M 0 2 -1 0 0 0.0", slot.valueNow());
            assertFalse(slot.blocking());
            // a reset puts the entry back to unresolved: the model reads origins at each check
            session.resetField(target);
            assertTrue(review.rows().get(1).blocking());
        }
    }

    @Test
    @DisplayName("the value now of a parameter kept as unknown, and of one removed")
    void valueNowOfUnknowns() {
        // CONSTRUCTED: the real 2026.03.0 -q file with one parameter Comet does not have
        String text =
                Sessions.cometQ(C03)
                        .replace(
                                "database_name = /some/path/db.fasta\n",
                                "database_name = /some/path/db.fasta\nmy_custom_option = 7\n");
        CometParameters model =
                new CometParamsParser(Sessions.METADATA, C03).parse(text).model().orElseThrow();
        assertEquals("7", MigrationReviewViewModel.valueNow(model, "my_custom_option"));
        assertEquals("0", MigrationReviewViewModel.valueNow(model, "num_threads"));
        assertEquals(
                "(not present)",
                MigrationReviewViewModel.valueNow(
                        model.withoutUnknown("my_custom_option"), "my_custom_option"));
    }

    @Test
    @DisplayName("every outcome has its words, and only a decision has a resolution")
    void words() {
        List<String> words =
                Arrays.stream(Outcome.values())
                        .map(
                                outcome ->
                                        new MigrationRow(
                                                        new MigrationEntry(
                                                                "x",
                                                                outcome,
                                                                outcome == Outcome.ADDED
                                                                        ? Optional.empty()
                                                                        : Optional.of("1"),
                                                                "1",
                                                                "why"),
                                                        Optional.empty(),
                                                        "1",
                                                        "1",
                                                        Optional.empty(),
                                                        Optional.empty())
                                                .outcomeWords())
                        .toList();
        assertEquals(
                List.of(
                        "Carried",
                        "Reshaped for the new release's syntax",
                        "Converted",
                        "Carried, with a note",
                        "Added at the new release's default",
                        "Not in the new release; kept as an unknown parameter",
                        "Unknown parameter, kept",
                        "Unknown parameter, now read as a parameter",
                        "Needs your decision"),
                words);
        MigrationEntry carried =
                new MigrationEntry("x", Outcome.CARRIED, Optional.of("1"), "1", "why");
        assertEquals(
                "x needed no decision, so it has no resolution",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        new MigrationRow(
                                                carried,
                                                Optional.empty(),
                                                "1",
                                                "1",
                                                Optional.of("Resolved"),
                                                Optional.empty()))
                        .getMessage());
    }
}
