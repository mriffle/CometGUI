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
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import org.cometgui.params.comet.validation.ValidationReport;
import org.cometgui.ui.viewmodel.NonNullProperty;

/**
 * The summary at the top of the editor: every finding of the session's one report, in the report's
 * order, with counts ("Errors shall be ... summarised at the top of the editor, and reachable by
 * keyboard").
 *
 * <p>It follows the session's report and nothing else: no finding is added, dropped or re-ordered
 * here.
 */
public final class ValidationSummaryViewModel {

    private final ParameterSession session;

    private final NonNullProperty<List<SummaryEntry>> entries;

    private final ReadOnlyIntegerWrapper errorCount;

    private final ReadOnlyIntegerWrapper warningCount;

    private final NonNullProperty<String> headline;

    /**
     * A summary following a session's report.
     *
     * @param session the session
     */
    public ValidationSummaryViewModel(ParameterSession session) {
        this.session = Objects.requireNonNull(session, "session");
        this.entries = new NonNullProperty<>(this, "entries", List.of());
        this.errorCount = new ReadOnlyIntegerWrapper(this, "errorCount", 0);
        this.warningCount = new ReadOnlyIntegerWrapper(this, "warningCount", 0);
        this.headline = new NonNullProperty<>(this, "headline", headlineFor(0, 0));
        show(session.report());
        session.reportProperty().addListener((observable, before, after) -> show(after));
    }

    /**
     * The entries, in the report's order.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<List<SummaryEntry>> entriesProperty() {
        return entries.getReadOnlyProperty();
    }

    /**
     * The entries, in the report's order.
     *
     * @return the entries, immutable
     */
    public List<SummaryEntry> entries() {
        return entries.get();
    }

    /**
     * How many errors the report holds.
     *
     * @return the read-only property
     */
    public ReadOnlyIntegerProperty errorCountProperty() {
        return errorCount.getReadOnlyProperty();
    }

    /**
     * How many errors the report holds.
     *
     * @return the count
     */
    public int errorCount() {
        return errorCount.get();
    }

    /**
     * How many warnings the report holds.
     *
     * @return the read-only property
     */
    public ReadOnlyIntegerProperty warningCountProperty() {
        return warningCount.getReadOnlyProperty();
    }

    /**
     * How many warnings the report holds.
     *
     * @return the count
     */
    public int warningCount() {
        return warningCount.get();
    }

    /**
     * The counts in one sentence.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<String> headlineProperty() {
        return headline.getReadOnlyProperty();
    }

    /**
     * The counts in one sentence.
     *
     * @return for example {@code 1 error and 2 warnings.} or {@code No errors or warnings.}
     */
    public String headline() {
        return headline.get();
    }

    /**
     * The sentence for a pair of counts.
     *
     * @param errors how many errors
     * @param warnings how many warnings
     * @return the sentence
     */
    public static String headlineFor(int errors, int warnings) {
        if (errors == 0 && warnings == 0) {
            return "No errors or warnings.";
        }
        return count(errors, "error") + " and " + count(warnings, "warning") + ".";
    }

    private static String count(int number, String noun) {
        return number + " " + noun + (number == 1 ? "" : "s");
    }

    private void show(ValidationReport report) {
        int errors = report.errors().size();
        int warnings = report.warnings().size();
        entries.set(SummaryEntry.listOf(report, session));
        errorCount.set(errors);
        warningCount.set(warnings);
        headline.set(headlineFor(errors, warnings));
    }
}
