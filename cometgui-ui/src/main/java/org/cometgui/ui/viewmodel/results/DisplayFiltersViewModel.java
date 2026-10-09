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

import java.util.Objects;
import javafx.beans.property.ReadOnlyObjectProperty;
import org.cometgui.results.filtering.DisplayFilters;
import org.cometgui.results.filtering.PeptideQValueFilter;
import org.cometgui.results.filtering.PsmQValueFilter;
import org.cometgui.ui.viewmodel.NonNullProperty;

/**
 * The one display-filter state of the interface (design decisions P10-1 and P10-9; {@code
 * R-RES-01}, {@code AC-RES-03}): the PSM and the peptide q-value filters, as the scientist typed
 * them and as the model accepted them. The Percolator section and the Results section hold the same
 * instance, built once by the composition root, so a filter changed in one is the filter the other
 * shows.
 *
 * <p>Nothing here reads a number or checks a range. Typed text goes to {@link
 * PsmQValueFilter#parse} or {@link PeptideQValueFilter#parse}, and what that refuses -- text that
 * is not a number, a number outside {@code [0, 1]} -- is kept in the field with the model's own
 * message beside it, and the applied filter stays as it was. The two filters are independent: an
 * edit of one never touches the other.
 *
 * <p>The defaults are the model's, {@link DisplayFilters#DEFAULTS}: 0.01 and 0.01. Applying a run's
 * saved view state ({@link #apply}) replaces both, and lifts any refusal, because the saved values
 * came through the same model.
 *
 * <p>Everything here runs on the interface thread and touches no file: saving the run's view state
 * is the Results section's business ({@link ResultsViewModel}), as a listener on {@link
 * #displayFiltersProperty()}.
 */
public final class DisplayFiltersViewModel {

    /** What the display filters are, and are not ({@code R-RES-01}, {@code R-PERC-04}). */
    public static final String EXPLANATION =
            "The PSM and the peptide q-value filters (each 0.01 by default, from 0 to 1, a q-value"
                    + " equal to the cutoff passing) change only which PSMs and peptides are"
                    + " displayed and exported. Changing them never reruns Percolator or any other"
                    + " tool, and they are not Percolator's testFDR or trainFDR, the learning"
                    + " thresholds under Advanced settings.";

    private String psmRefusal = "";

    private String peptideRefusal = "";

    private final NonNullProperty<String> psmText;

    private final NonNullProperty<String> peptideText;

    private final NonNullProperty<String> status;

    private final NonNullProperty<DisplayFilters> filters;

    /** Both filters at their defaults, 0.01 and 0.01. */
    public DisplayFiltersViewModel() {
        psmText = new NonNullProperty<>(this, "psmFilterText", PsmQValueFilter.DEFAULT.text());
        peptideText =
                new NonNullProperty<>(
                        this, "peptideFilterText", PeptideQValueFilter.DEFAULT.text());
        status = new NonNullProperty<>(this, "filtersStatus", EXPLANATION);
        filters = new NonNullProperty<>(this, "displayFilters", DisplayFilters.DEFAULTS);
    }

    /**
     * Sets the PSM display filter from typed text. Text the model refuses stays in the field, the
     * refusal is stated, and the applied PSM filter is unchanged.
     *
     * @param text for example {@code 0.05}
     * @return {@code true} if the model accepted the text
     */
    public boolean editPsmFilter(String text) {
        Objects.requireNonNull(text, "text");
        try {
            PsmQValueFilter parsed = PsmQValueFilter.parse(text);
            // an equal value keeps the filter held, so the field and the saved state keep one text
            PsmQValueFilter filter =
                    parsed.equals(filters.get().psm()) ? filters.get().psm() : parsed;
            psmRefusal = "";
            psmText.set(filter.text());
            status.set(statusText());
            // last, so that a listener on the filters sees the fields and status already in step
            filters.set(filters.get().withPsm(filter));
            return true;
        } catch (IllegalArgumentException refused) {
            psmRefusal = refused.getMessage();
            psmText.set(text);
            status.set(statusText());
            return false;
        }
    }

    /**
     * Sets the peptide display filter from typed text. Text the model refuses stays in the field,
     * the refusal is stated, and the applied peptide filter is unchanged.
     *
     * @param text for example {@code 0.05}
     * @return {@code true} if the model accepted the text
     */
    public boolean editPeptideFilter(String text) {
        Objects.requireNonNull(text, "text");
        try {
            PeptideQValueFilter parsed = PeptideQValueFilter.parse(text);
            PeptideQValueFilter filter =
                    parsed.equals(filters.get().peptide()) ? filters.get().peptide() : parsed;
            peptideRefusal = "";
            peptideText.set(filter.text());
            status.set(statusText());
            filters.set(filters.get().withPeptide(filter));
            return true;
        } catch (IllegalArgumentException refused) {
            peptideRefusal = refused.getMessage();
            peptideText.set(text);
            status.set(statusText());
            return false;
        }
    }

    /**
     * Shows both filters as given -- a run's saved view state -- and lifts any refusal: the fields
     * hold the filters' own text.
     *
     * @param applied the filters
     */
    public void apply(DisplayFilters applied) {
        Objects.requireNonNull(applied, "applied");
        psmRefusal = "";
        peptideRefusal = "";
        psmText.set(applied.psm().text());
        peptideText.set(applied.peptide().text());
        status.set(statusText());
        filters.set(applied);
    }

    /**
     * The filters as applied.
     *
     * @return both filters
     */
    public DisplayFilters filters() {
        return filters.get();
    }

    /**
     * The text of the PSM filter's field: the applied filter's text, or the refused text.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<String> psmFilterTextProperty() {
        return psmText.getReadOnlyProperty();
    }

    /**
     * The text of the peptide filter's field: the applied filter's text, or the refused text.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<String> peptideFilterTextProperty() {
        return peptideText.getReadOnlyProperty();
    }

    /**
     * What the display filters do, and the model's refusal of either field's text.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<String> filtersStatusProperty() {
        return status.getReadOnlyProperty();
    }

    /**
     * The filters as applied. A change event fires only when a filter's value changes: {@code
     * 0.010} after {@code 0.01} is the same filter and fires none.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<DisplayFilters> displayFiltersProperty() {
        return filters.getReadOnlyProperty();
    }

    private String statusText() {
        StringBuilder text = new StringBuilder(EXPLANATION);
        if (!psmRefusal.isEmpty()) {
            text.append("\nThe PSM filter's text is not used: ").append(psmRefusal);
        }
        if (!peptideRefusal.isEmpty()) {
            text.append("\nThe peptide filter's text is not used: ").append(peptideRefusal);
        }
        return text.toString();
    }
}
