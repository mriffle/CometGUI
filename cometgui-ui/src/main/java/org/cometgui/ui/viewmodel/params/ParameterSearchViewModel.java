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
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javafx.beans.property.ReadOnlyObjectProperty;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.model.UnknownParameter;
import org.cometgui.params.comet.validation.Finding;
import org.cometgui.params.comet.validation.Severity;
import org.cometgui.params.comet.validation.ValidationReport;
import org.cometgui.ui.viewmodel.NonNullProperty;

/**
 * The global parameter search (<em>Global parameter search</em>, exit gate item 8): one query over
 * every parameter of the selected release -- its name, display name, short help, category and
 * curated aliases -- and over the configuration's unknown parameters by name, narrowed by the
 * filters of {@link SearchFilter}.
 *
 * <p>Matching is a case-insensitive substring test ({@link Locale#ROOT}), the query trimmed and
 * taken whole, so {@code precursor tolerance} is one phrase. It is presentation logic, not a rule
 * of the model, and needs no regular expression. Every attribute is the selected release's own
 * ({@code CuratedMetadata.parameter(name, version)}), so a release whose help differs is searched
 * in its own words. Each hit says which attributes matched, and which aliases.
 *
 * <p>The results follow the query, the filters, the configuration, its report and its refused
 * edits: they are recomputed whenever any of them changes (a release switch changes the
 * configuration, and with it the fields).
 */
public final class ParameterSearchViewModel {

    private final ParameterSession session;

    private final NonNullProperty<String> query;

    private final NonNullProperty<Set<SearchFilter>> filters;

    private final NonNullProperty<List<SearchHit>> results;

    /**
     * The search of a session, with an empty query and no filter.
     *
     * @param session the session
     */
    public ParameterSearchViewModel(ParameterSession session) {
        this.session = Objects.requireNonNull(session, "session");
        this.query = new NonNullProperty<>(this, "query", "");
        this.filters = new NonNullProperty<>(this, "filters", Set.of());
        this.results = new NonNullProperty<>(this, "results", List.of());
        session.modelProperty().addListener((observable, before, after) -> refresh());
        session.reportProperty().addListener((observable, before, after) -> refresh());
        session.pendingRefusalsProperty().addListener((observable, before, after) -> refresh());
        refresh();
    }

    /**
     * The query.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<String> queryProperty() {
        return query.getReadOnlyProperty();
    }

    /**
     * Sets the query.
     *
     * @param text what the scientist typed
     */
    public void setQuery(String text) {
        query.set(Objects.requireNonNull(text, "text"));
        refresh();
    }

    /**
     * The active filters.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<Set<SearchFilter>> filtersProperty() {
        return filters.getReadOnlyProperty();
    }

    /**
     * The active filters.
     *
     * @return the filters, immutable
     */
    public Set<SearchFilter> filters() {
        return filters.get();
    }

    /**
     * Switches one filter on or off.
     *
     * @param filter the filter
     * @param active whether it is to apply
     */
    public void setFilter(SearchFilter filter, boolean active) {
        Objects.requireNonNull(filter, "filter");
        Set<SearchFilter> next = EnumSet.noneOf(SearchFilter.class);
        next.addAll(filters.get());
        if (active) {
            next.add(filter);
        } else {
            next.remove(filter);
        }
        filters.set(Collections.unmodifiableSet(next));
        refresh();
    }

    /**
     * The parameters found, modelled ones in the release's order, then unknown ones in the
     * configuration's order.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<List<SearchHit>> resultsProperty() {
        return results.getReadOnlyProperty();
    }

    /**
     * The parameters found.
     *
     * @return the hits, immutable
     */
    public List<SearchHit> results() {
        return results.get();
    }

    /**
     * The result line in words.
     *
     * @return for example {@code 3 parameters found}
     */
    public String headline() {
        int count = results.get().size();
        return count + (count == 1 ? " parameter found" : " parameters found");
    }

    private void refresh() {
        String wanted = query.get().strip().toLowerCase(Locale.ROOT);
        Set<SearchFilter> active = filters.get();
        CometParameters model = session.model();
        ValidationReport report = session.report();
        List<SearchHit> found = new ArrayList<>();
        if (!active.contains(SearchFilter.UNSUPPORTED)) {
            for (FieldViewModel field : session.fields()) {
                if (admits(field, model, report, active)) {
                    match(field, wanted).ifPresent(found::add);
                }
            }
        }
        for (UnknownParameter unknown : model.unknownParameters()) {
            if (admitsUnknown(unknown.name(), report, active)) {
                matchUnknown(unknown.name(), model, wanted).ifPresent(found::add);
            }
        }
        results.set(List.copyOf(found));
    }

    private static boolean admits(
            FieldViewModel field,
            CometParameters model,
            ValidationReport report,
            Set<SearchFilter> active) {
        String name = field.name();
        if (active.contains(SearchFilter.MODIFIED)
                && model.value(name).equals(model.resetToDefault(name).value(name))) {
            return false;
        }
        if (active.contains(SearchFilter.ERRORS)
                && field.refusal().isEmpty()
                && !has(report, name, Severity.ERROR)) {
            return false;
        }
        if (active.contains(SearchFilter.WARNINGS) && !has(report, name, Severity.WARNING)) {
            return false;
        }
        return !active.contains(SearchFilter.EXPERT) || field.isExpert();
    }

    /**
     * Whether the filters admit an unknown parameter. Package-private for its test: with the
     * releases offered today every unknown parameter carries exactly the {@code
     * unknown_parameter.imported} warning, so only a constructed report reaches its error branch.
     */
    static boolean admitsUnknown(String name, ValidationReport report, Set<SearchFilter> active) {
        if (active.contains(SearchFilter.EXPERT)) {
            return false;
        }
        if (active.contains(SearchFilter.ERRORS) && !has(report, name, Severity.ERROR)) {
            return false;
        }
        return !active.contains(SearchFilter.WARNINGS) || has(report, name, Severity.WARNING);
    }

    private static boolean has(ValidationReport report, String name, Severity severity) {
        for (Finding finding : report.forParameter(name)) {
            if (finding.severity() == severity) {
                return true;
            }
        }
        return false;
    }

    private static Optional<SearchHit> match(FieldViewModel field, String wanted) {
        String label = field.displayName() + " (" + field.name() + ")";
        if (wanted.isEmpty()) {
            return Optional.of(
                    new SearchHit(field.name(), Optional.of(field), label, List.of(), List.of()));
        }
        List<SearchMatch> matched = new ArrayList<>();
        if (contains(field.name(), wanted)) {
            matched.add(SearchMatch.NAME);
        }
        if (contains(field.displayName(), wanted)) {
            matched.add(SearchMatch.DISPLAY_NAME);
        }
        if (contains(field.shortHelp(), wanted)) {
            matched.add(SearchMatch.HELP_TEXT);
        }
        if (contains(field.category().displayName(), wanted)) {
            matched.add(SearchMatch.CATEGORY);
        }
        List<String> aliases =
                field.definition().aliases().stream().filter(a -> contains(a, wanted)).toList();
        if (!aliases.isEmpty()) {
            matched.add(SearchMatch.ALIAS);
        }
        if (matched.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(
                new SearchHit(field.name(), Optional.of(field), label, matched, aliases));
    }

    private static Optional<SearchHit> matchUnknown(
            String name, CometParameters model, String wanted) {
        String label = name + " (not a parameter of Comet " + model.version().text() + ")";
        if (wanted.isEmpty()) {
            return Optional.of(new SearchHit(name, Optional.empty(), label, List.of(), List.of()));
        }
        if (!contains(name, wanted)) {
            return Optional.empty();
        }
        return Optional.of(
                new SearchHit(name, Optional.empty(), label, List.of(SearchMatch.NAME), List.of()));
    }

    private static boolean contains(String attribute, String wanted) {
        return attribute.toLowerCase(Locale.ROOT).contains(wanted);
    }
}
