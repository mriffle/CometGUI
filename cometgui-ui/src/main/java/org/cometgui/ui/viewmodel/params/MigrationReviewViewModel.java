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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.cometgui.params.comet.migration.MigrationEntry;
import org.cometgui.params.comet.migration.MigrationReport;
import org.cometgui.params.comet.migration.MigrationReview;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.model.UnknownParameter;

/**
 * The migration under review as a reviewable diff ({@code R-PARAM-13}, decision P7-3): one row per
 * change the model's {@link MigrationReport} records -- outcome, parameter, source value, value
 * now, explanation -- with the unchanged entries counted rather than listed.
 *
 * <p>An entry that needs the scientist ({@code NEEDS_ATTENTION}) blocks a run until it is resolved:
 * the session's one report carries it as an error ({@code MigrationReview#validate}), and its row
 * says so. It is resolved by accepting the value the configuration holds ({@link #accept}, the
 * model's {@code resolve}) or by a value the scientist sets on the parameter at its field (the
 * row's focus target); the row then says which.
 *
 * <p>Every answer is read from the session when asked, so it always shows the review and the
 * configuration as they are.
 */
public final class MigrationReviewViewModel {

    private final ParameterSession session;

    /**
     * The review of a session's migration.
     *
     * @param session the session
     */
    public MigrationReviewViewModel(ParameterSession session) {
        this.session = Objects.requireNonNull(session, "session");
    }

    /**
     * Whether a migration is under review.
     *
     * @return {@code true} after a release switch or an adopted migration, until a new
     *     configuration starts
     */
    public boolean underReview() {
        return session.review().isPresent();
    }

    /**
     * The review in one line.
     *
     * @return for example {@code Comet 2026.02.2 -> 2026.03.0: 118 parameters, 1 changed, 117
     *     unchanged; 0 need your decision}; empty when nothing is under review
     */
    public String headline() {
        Optional<MigrationReview> review = session.review();
        if (review.isEmpty()) {
            return "";
        }
        MigrationReport report = review.get().report();
        int changed = report.changes().size();
        return "Comet "
                + report.from().text()
                + " -> "
                + report.to().text()
                + ": "
                + report.entries().size()
                + " parameters, "
                + changed
                + " changed, "
                + unchangedCount()
                + " unchanged; "
                + unresolvedCount()
                + (unresolvedCount() == 1 ? " needs" : " need")
                + " your decision";
    }

    /**
     * The entries migration carried unchanged: counted, not listed.
     *
     * @return the count, 0 when nothing is under review
     */
    public int unchangedCount() {
        return session.review()
                .map(r -> r.report().entries().size() - r.report().changes().size())
                .orElse(0);
    }

    /**
     * The entries that still need the scientist.
     *
     * @return the count, 0 when nothing is under review
     */
    public int unresolvedCount() {
        return session.unresolved().size();
    }

    /**
     * One row per change, in the report's order.
     *
     * @return the rows, empty when nothing is under review
     */
    public List<MigrationRow> rows() {
        Optional<MigrationReview> review = session.review();
        if (review.isEmpty()) {
            return List.of();
        }
        CometParameters model = session.model();
        List<String> open = session.unresolved().stream().map(MigrationEntry::parameter).toList();
        MigrationReport report = review.get().report();
        List<MigrationRow> rows = new ArrayList<>();
        for (MigrationEntry entry : report.changes()) {
            String name = entry.parameter();
            Optional<String> shown = session.displayNameOf(name);
            Optional<String> resolution = Optional.empty();
            if (entry.outcome() == MigrationEntry.Outcome.NEEDS_ATTENTION && !open.contains(name)) {
                resolution =
                        Optional.of(
                                review.get().acknowledged().contains(name)
                                        ? "Resolved: you accepted the value it holds"
                                        : "Resolved: you set its value");
            }
            rows.add(
                    new MigrationRow(
                            entry,
                            shown,
                            entry.sourceText().orElse("(new in Comet " + report.to().text() + ")"),
                            valueNow(model, name),
                            resolution,
                            shown.map(n -> name)));
        }
        return List.copyOf(rows);
    }

    /**
     * The scientist accepts the value the configuration holds for an entry that needs a decision.
     *
     * @param name the entry's parameter
     * @return accepted, or why not -- nothing under review, not an entry needing a decision, or
     *     already resolved, in the model's words
     */
    public EditOutcome accept(String name) {
        Objects.requireNonNull(name, "name");
        if (session.review().isEmpty()) {
            return EditOutcome.refused(
                    "No migration is under review, so there is nothing to accept.");
        }
        try {
            session.resolve(name);
        } catch (IllegalArgumentException refused) {
            return EditOutcome.refused(refused.getMessage());
        }
        return EditOutcome.applied();
    }

    static String valueNow(CometParameters model, String name) {
        if (model.entry(name).isPresent()) {
            return model.text(name);
        }
        for (UnknownParameter unknown : model.unknownParameters()) {
            if (unknown.name().equals(name)) {
                return unknown.value();
            }
        }
        return DiffRowView.ABSENT;
    }
}
