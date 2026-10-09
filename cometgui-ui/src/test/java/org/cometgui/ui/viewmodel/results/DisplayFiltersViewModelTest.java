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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.cometgui.results.filtering.DisplayFilters;
import org.cometgui.results.filtering.PeptideQValueFilter;
import org.cometgui.results.filtering.PsmQValueFilter;
import org.cometgui.ui.testing.Editors.ScriptedChooser;
import org.cometgui.ui.testing.Nulls;
import org.cometgui.ui.testing.Percolators;
import org.cometgui.ui.testing.ScriptedEngine;
import org.cometgui.ui.viewmodel.percolator.PercolatorViewModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The interface's one display-filter state (design decisions P10-1, P10-9; phase 10 gate item 1 at
 * the view-model): defaults 0.01 and 0.01, independent, inclusive values accepted at both ends,
 * refused text leaving the applied filter as it was, and one instance shared by the Percolator and
 * the Results sections. Every expected text is typed here.
 */
class DisplayFiltersViewModelTest {

    private final DisplayFiltersViewModel filters = new DisplayFiltersViewModel();

    private final List<DisplayFilters> changes = new ArrayList<>();

    {
        filters.displayFiltersProperty().addListener((o, before, after) -> changes.add(after));
    }

    private static PsmQValueFilter psm(String cutoff) {
        return new PsmQValueFilter(new BigDecimal(cutoff));
    }

    private static PeptideQValueFilter peptide(String cutoff) {
        return new PeptideQValueFilter(new BigDecimal(cutoff));
    }

    @Test
    @DisplayName("both default to 0.01, with the explanation and no refusal")
    void defaults() {
        assertAll(
                () -> assertEquals(psm("0.01"), filters.filters().psm()),
                () -> assertEquals(peptide("0.01"), filters.filters().peptide()),
                () -> assertEquals("0.01", filters.psmFilterTextProperty().get()),
                () -> assertEquals("0.01", filters.peptideFilterTextProperty().get()),
                () ->
                        assertEquals(
                                "The PSM and the peptide q-value filters (each 0.01 by default,"
                                        + " from 0 to 1, a q-value equal to the cutoff passing)"
                                        + " change only which PSMs and peptides are displayed and"
                                        + " exported. Changing them never reruns Percolator or"
                                        + " any other tool, and they are not Percolator's testFDR"
                                        + " or trainFDR, the learning thresholds under Advanced"
                                        + " settings.",
                                filters.filtersStatusProperty().get()));
    }

    @Test
    @DisplayName("0 and 1 are accepted, each filter alone, the other untouched")
    void bothEndsAndIndependence() {
        assertTrue(filters.editPsmFilter("0"));
        assertEquals(new DisplayFilters(psm("0"), peptide("0.01")), filters.filters());
        assertTrue(filters.editPeptideFilter(" 1 "));
        assertEquals(new DisplayFilters(psm("0"), peptide("1")), filters.filters());
        assertEquals("1", filters.peptideFilterTextProperty().get(), "the model's own text");
        assertTrue(filters.editPsmFilter("0.005"));
        assertEquals(new DisplayFilters(psm("0.005"), peptide("1")), filters.filters());
        assertEquals(
                List.of(
                        new DisplayFilters(psm("0"), peptide("0.01")),
                        new DisplayFilters(psm("0"), peptide("1")),
                        new DisplayFilters(psm("0.005"), peptide("1"))),
                changes);
    }

    @Test
    @DisplayName("refused text stays in its field, is explained, and changes no filter")
    void refused() {
        assertTrue(filters.editPsmFilter("0.05"));
        changes.clear();
        assertFalse(filters.editPsmFilter("1.01"));
        assertFalse(filters.editPeptideFilter("-0.0001"));
        assertFalse(filters.editPeptideFilter("0,01"));
        assertAll(
                () ->
                        assertEquals(
                                new DisplayFilters(psm("0.05"), peptide("0.01")),
                                filters.filters()),
                () -> assertEquals(List.of(), changes, "no change event for refused text"),
                () -> assertEquals("1.01", filters.psmFilterTextProperty().get()),
                () -> assertEquals("0,01", filters.peptideFilterTextProperty().get()),
                () ->
                        assertTrue(
                                filters.filtersStatusProperty()
                                        .get()
                                        .endsWith(
                                                "\nThe PSM filter's text is not used: The PSM"
                                                        + " q-value filter must be between 0 and 1"
                                                        + " inclusive, but was 1.01\nThe peptide"
                                                        + " filter's text is not used: The peptide"
                                                        + " q-value filter must be a number"
                                                        + " between 0 and 1 inclusive, written with"
                                                        + " a '.' decimal point, but was '0,01'"),
                                filters.filtersStatusProperty()::get));
        assertTrue(filters.editPsmFilter("0.05"));
        assertEquals(List.of(), changes, "the same value again is no change");
        assertFalse(
                filters.filtersStatusProperty().get().contains("PSM filter's text"),
                "an accepted value lifts the PSM refusal");
        assertTrue(filters.filtersStatusProperty().get().contains("peptide filter's text"));
    }

    @Test
    @DisplayName(
            "0.010 after 0.01 is the same filter: no change event, the applied filter's text"
                    + " shown")
    void equalValueIsNoChange() {
        assertTrue(filters.editPsmFilter("0.010"));
        assertTrue(filters.editPeptideFilter("1e-2"));
        assertEquals(List.of(), changes);
        assertEquals("0.01", filters.psmFilterTextProperty().get());
        assertEquals("0.01", filters.peptideFilterTextProperty().get());
        assertEquals("0.01", filters.filters().psm().text(), "the filter held is unchanged");
    }

    @Test
    @DisplayName("applying a saved state replaces both filters and lifts every refusal")
    void apply() {
        filters.editPsmFilter("x");
        filters.apply(new DisplayFilters(psm("0.2"), peptide("0.03")));
        assertAll(
                () ->
                        assertEquals(
                                new DisplayFilters(psm("0.2"), peptide("0.03")), filters.filters()),
                () -> assertEquals("0.2", filters.psmFilterTextProperty().get()),
                () -> assertEquals("0.03", filters.peptideFilterTextProperty().get()),
                () ->
                        assertEquals(
                                DisplayFiltersViewModel.EXPLANATION,
                                filters.filtersStatusProperty().get()));
        assertThrows(
                NullPointerException.class, () -> filters.apply(Nulls.of(DisplayFilters.class)));
        assertThrows(
                NullPointerException.class, () -> filters.editPsmFilter(Nulls.of(String.class)));
        assertThrows(
                NullPointerException.class,
                () -> filters.editPeptideFilter(Nulls.of(String.class)));
    }

    @Test
    @DisplayName("a listener on the filters sees the field and status already in step")
    void consistentWhenNotified() {
        List<String> seen = new ArrayList<>();
        filters.displayFiltersProperty()
                .addListener(
                        (o, before, after) ->
                                seen.add(
                                        after.psm().text()
                                                + " "
                                                + filters.psmFilterTextProperty().get()
                                                + " "
                                                + filters.filtersStatusProperty()
                                                        .get()
                                                        .contains("not used")));
        filters.editPsmFilter("x");
        filters.editPsmFilter("0.2");
        assertEquals(List.of("0.2 0.2 false"), seen);
    }

    @Test
    @DisplayName("the Percolator section given the shared instance shows and edits that one state")
    void sharedWithThePercolatorSection() {
        ScriptedEngine.Queue queue = new ScriptedEngine.Queue("both");
        PercolatorViewModel percolator =
                new PercolatorViewModel(
                        new Percolators.Port(),
                        new ScriptedChooser(),
                        queue,
                        queue,
                        () -> {},
                        filters);
        percolator.editPsmFilter("0.05");
        assertEquals(psm("0.05"), filters.filters().psm(), "an edit there is an edit here");
        filters.editPeptideFilter("0.2");
        assertAll(
                () -> assertEquals("0.2", percolator.peptideFilterTextProperty().get()),
                () ->
                        assertEquals(
                                peptide("0.2"),
                                percolator.displayFiltersProperty().get().peptide()),
                () ->
                        assertSame(
                                filters.displayFiltersProperty(),
                                percolator.displayFiltersProperty(),
                                "one property, not a copy"));
        percolator.editPeptideFilter("7");
        assertEquals(peptide("0.2"), filters.filters().peptide());
        assertEquals("7", filters.peptideFilterTextProperty().get());
        assertSame(filters.filtersStatusProperty(), percolator.filtersStatusProperty());
        assertSame(filters.psmFilterTextProperty(), percolator.psmFilterTextProperty());
        assertEquals(PercolatorViewModel.FILTERS_EXPLANATION, DisplayFiltersViewModel.EXPLANATION);
    }
}
