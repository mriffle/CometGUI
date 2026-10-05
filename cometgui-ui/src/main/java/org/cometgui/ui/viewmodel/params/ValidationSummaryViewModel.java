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
 * <p>It follows the session's report and its pending refusals and nothing else: first every field
 * holding an edit the model refused (kind "Not applied", in field order), then every finding of the
 * report, none added, dropped or re-ordered.
 */
public final class ValidationSummaryViewModel {

    private final ParameterSession session;

    private final NonNullProperty<List<SummaryEntry>> entries;

    private final ReadOnlyIntegerWrapper notAppliedCount;

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
        this.notAppliedCount = new ReadOnlyIntegerWrapper(this, "notAppliedCount", 0);
        this.errorCount = new ReadOnlyIntegerWrapper(this, "errorCount", 0);
        this.warningCount = new ReadOnlyIntegerWrapper(this, "warningCount", 0);
        this.headline = new NonNullProperty<>(this, "headline", headlineFor(0, 0, 0));
        show(session.report());
        session.reportProperty().addListener((observable, before, after) -> show(after));
        session.pendingRefusalsProperty()
                .addListener((observable, before, after) -> show(session.report()));
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
     * How many fields hold an edit the model refused.
     *
     * @return the read-only property
     */
    public ReadOnlyIntegerProperty notAppliedCountProperty() {
        return notAppliedCount.getReadOnlyProperty();
    }

    /**
     * How many fields hold an edit the model refused.
     *
     * @return the count
     */
    public int notAppliedCount() {
        return notAppliedCount.get();
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
     * The sentence for a set of counts.
     *
     * @param notApplied how many fields hold a refused edit
     * @param errors how many errors
     * @param warnings how many warnings
     * @return for example {@code 1 edit not applied, 2 errors and 0 warnings.}
     */
    public static String headlineFor(int notApplied, int errors, int warnings) {
        if (notApplied == 0 && errors == 0 && warnings == 0) {
            return "No errors or warnings.";
        }
        String counts = count(errors, "error") + " and " + count(warnings, "warning") + ".";
        return notApplied == 0 ? counts : count(notApplied, "edit") + " not applied, " + counts;
    }

    private static String count(int number, String noun) {
        return number + " " + noun + (number == 1 ? "" : "s");
    }

    private void show(ValidationReport report) {
        int notApplied = session.pendingRefusals().size();
        int errors = report.errors().size();
        int warnings = report.warnings().size();
        entries.set(SummaryEntry.listOf(report, session));
        notAppliedCount.set(notApplied);
        errorCount.set(errors);
        warningCount.set(warnings);
        headline.set(headlineFor(notApplied, errors, warnings));
    }
}
