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
import org.cometgui.params.comet.schema.ParameterCategory;
import org.cometgui.params.comet.validation.Finding;
import org.cometgui.params.comet.validation.ValidationReport;

/**
 * One line of the validation summary: either a finding of the session's report, or a field holding
 * an edit the model refused, which is therefore shown but not applied.
 *
 * <p>An entry says its kind in words, the message, and the parameter and category it is attached
 * to; an entry with a field knows the field to move focus to (exit gate item 6: reachable by
 * keyboard).
 *
 * @param kind error, warning, or an edit that was not applied
 * @param finding the report's finding, present exactly for an error or a warning
 * @param parameter the parameter shown at, or empty for a finding about the file as a whole
 * @param parameterDisplayName the display name of that parameter, where the release models it
 * @param category the category the entry is attached to, or empty
 * @param message the finding's message, or the model's refusal
 */
public record SummaryEntry(
        Kind kind,
        Optional<Finding> finding,
        Optional<String> parameter,
        Optional<String> parameterDisplayName,
        Optional<ParameterCategory> category,
        String message) {

    /** What an entry reports. */
    public enum Kind {

        /** An edit the model refused: the field shows text the configuration does not hold. */
        NOT_APPLIED("Not applied", true),

        /** An error of the report. */
        ERROR("Error", true),

        /** A warning of the report. */
        WARNING("Warning", false);

        private final String words;

        private final boolean blocksRun;

        Kind(String words, boolean blocksRun) {
            this.words = words;
            this.blocksRun = blocksRun;
        }

        /**
         * The kind in words.
         *
         * @return for example {@code Not applied}
         */
        public String words() {
            return words;
        }

        /**
         * Whether an entry of this kind keeps the Run control disabled.
         *
         * @return {@code true} for an error and an edit that was not applied
         */
        public boolean blocksRun() {
            return blocksRun;
        }
    }

    /**
     * Validates the pairing of kind and finding.
     *
     * @throws IllegalArgumentException if a finding's entry has no finding or the wrong kind, or an
     *     unapplied edit's has one
     */
    public SummaryEntry {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(finding, "finding");
        Objects.requireNonNull(parameter, "parameter");
        Objects.requireNonNull(parameterDisplayName, "parameterDisplayName");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(message, "message");
        Kind expected =
                finding.map(f -> f.isError() ? Kind.ERROR : Kind.WARNING).orElse(Kind.NOT_APPLIED);
        if (kind != expected) {
            throw new IllegalArgumentException(
                    "a summary entry of kind " + kind + " must be of kind " + expected);
        }
    }

    /**
     * The entry for one finding of the report.
     *
     * @param finding the finding
     * @param parameterDisplayName the display name of its first parameter, where modelled
     * @return the entry
     */
    public static SummaryEntry of(Finding finding, Optional<String> parameterDisplayName) {
        return new SummaryEntry(
                finding.isError() ? Kind.ERROR : Kind.WARNING,
                Optional.of(finding),
                finding.parameters().stream().findFirst(),
                parameterDisplayName,
                finding.category(),
                finding.message());
    }

    /**
     * The entry for a field holding a refused edit.
     *
     * @param field the field
     * @return the entry
     * @throws IllegalArgumentException if the field holds no refused edit
     */
    public static SummaryEntry notApplied(FieldViewModel field) {
        String refusal =
                field.refusal()
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                field.name() + " holds no refused edit"));
        return new SummaryEntry(
                Kind.NOT_APPLIED,
                Optional.empty(),
                Optional.of(field.name()),
                Optional.of(field.displayName()),
                Optional.of(field.category()),
                refusal);
    }

    /**
     * Every field of the session holding a refused edit, in field order, then every finding of a
     * report, in the report's order.
     *
     * @param report the report
     * @param session the session whose fields are shown
     * @return the entries
     */
    static List<SummaryEntry> listOf(ValidationReport report, ParameterSession session) {
        List<SummaryEntry> entries = new ArrayList<>();
        for (FieldViewModel field : session.pendingRefusals()) {
            entries.add(notApplied(field));
        }
        for (Finding finding : report.findings()) {
            entries.add(
                    of(
                            finding,
                            finding.parameters().stream()
                                    .findFirst()
                                    .flatMap(session::displayNameOf)));
        }
        return List.copyOf(entries);
    }

    /**
     * The kind in words.
     *
     * @return {@code Not applied}, {@code Error} or {@code Warning}
     */
    public String severityText() {
        return kind.words();
    }

    /**
     * The field a view moves focus to when the entry is activated.
     *
     * @return the parameter's name, or empty when the release has no field for it
     */
    public Optional<String> focusTarget() {
        return parameterDisplayName.isPresent() ? parameter : Optional.empty();
    }

    /**
     * The entry in one line of text.
     *
     * @return for example {@code Error -- Precursor tolerance, lower bound
     *     (peptide_mass_tolerance_lower), Precursor mass and isotope handling: ...}
     */
    public String text() {
        StringBuilder line = new StringBuilder(severityText());
        parameter.ifPresent(
                name -> {
                    line.append(" -- ");
                    parameterDisplayName.ifPresent(shown -> line.append(shown).append(" ("));
                    line.append(name);
                    parameterDisplayName.ifPresent(shown -> line.append(')'));
                });
        category.ifPresent(group -> line.append(", ").append(group.displayName()));
        return line.append(": ").append(message).toString();
    }
}
