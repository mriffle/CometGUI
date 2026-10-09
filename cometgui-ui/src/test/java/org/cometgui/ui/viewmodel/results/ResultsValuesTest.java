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

package org.cometgui.ui.viewmodel.results;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.cometgui.domain.run.AttemptOutcome;
import org.cometgui.domain.run.RunId;
import org.cometgui.results.filtering.DisplayFilters;
import org.cometgui.results.filtering.store.ResultStore;
import org.cometgui.results.filtering.store.ResultStores;
import org.cometgui.results.filtering.store.TableKind;
import org.cometgui.workflow.storage.ViewStateReading;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The port's value types refuse what they cannot mean. */
class ResultsValuesTest {

    private static final ViewStateReading NOTHING_SAVED =
            new ViewStateReading(
                    DisplayFilters.DEFAULTS,
                    ViewStateReading.Source.DEFAULTS_NOTHING_SAVED,
                    Optional.empty());

    @Test
    @DisplayName("a run with results has a table, and its tables are copied")
    void run() {
        RunId id = new RunId("r1");
        Instant created = Instant.parse("2026-10-09T08:00:00Z");
        IllegalArgumentException refused =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                new ResultsRun(
                                        id,
                                        created,
                                        AttemptOutcome.SUCCEEDED,
                                        EnumSet.noneOf(TableKind.class),
                                        false));
        assertEquals("a run with results has a table: r1", refused.getMessage());
        Set<TableKind> tables = EnumSet.of(TableKind.TARGET_PSMS);
        ResultsRun run = new ResultsRun(id, created, AttemptOutcome.FAILED, tables, false);
        tables.add(TableKind.DECOY_PSMS);
        assertEquals(Set.of(TableKind.TARGET_PSMS), run.tables());
        assertEquals("r1 -- created 2026-10-09T08:00:00Z, failed", run.label());
    }

    @Test
    @DisplayName("opened results hold a table, each under its own kind")
    void opened() throws IOException {
        RunId id = new RunId("r1");
        IllegalArgumentException empty =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                new OpenedResults(
                                        id, Map.of(), Optional.empty(), Map.of(), NOTHING_SAVED));
        assertEquals("opened results hold a table: r1", empty.getMessage());
        try (ResultStore psms =
                ResultStores.inMemory(
                        ResultsFixtures.copy("real-3.07.1-psms.tsv"), TableKind.TARGET_PSMS)) {
            IllegalArgumentException crossed =
                    assertThrows(
                            IllegalArgumentException.class,
                            () ->
                                    new OpenedResults(
                                            id,
                                            Map.of(TableKind.TARGET_PEPTIDES, psms),
                                            Optional.empty(),
                                            Map.of(),
                                            NOTHING_SAVED));
            assertEquals(
                    "the TARGET_PEPTIDES store holds a TARGET_PSMS table", crossed.getMessage());
            OpenedResults opened =
                    new OpenedResults(
                            id,
                            Map.of(TableKind.TARGET_PSMS, psms),
                            Optional.empty(),
                            Map.of("b", "b.mzML"),
                            NOTHING_SAVED);
            assertEquals(Map.of("b", "b.mzML"), opened.sourceFiles());
        }
    }
}
