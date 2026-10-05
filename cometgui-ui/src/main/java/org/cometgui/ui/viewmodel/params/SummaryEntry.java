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
 * One finding of the session's report as the validation summary lists it: severity in words, the
 * message, and the parameter and category it is attached to. An entry with a parameter knows the
 * field to move focus to (exit gate item 6: the error is reachable by keyboard).
 *
 * @param finding the report's finding
 * @param parameterDisplayName the display name of the parameter it points at, where the release
 *     models that parameter
 */
public record SummaryEntry(Finding finding, Optional<String> parameterDisplayName) {

    /** Validates presence. */
    public SummaryEntry {
        Objects.requireNonNull(finding, "finding");
        Objects.requireNonNull(parameterDisplayName, "parameterDisplayName");
    }

    /**
     * Every finding of a report as an entry, in the report's order, each pointing at the field of
     * the session's release that shows it.
     *
     * @param report the report
     * @param session the session whose fields name the parameters
     * @return the entries
     */
    static List<SummaryEntry> listOf(ValidationReport report, ParameterSession session) {
        List<SummaryEntry> entries = new ArrayList<>();
        for (Finding finding : report.findings()) {
            Optional<String> shown =
                    finding.parameters().stream().findFirst().flatMap(session::displayNameOf);
            entries.add(new SummaryEntry(finding, shown));
        }
        return List.copyOf(entries);
    }

    /**
     * The severity in words.
     *
     * @return {@code Error} or {@code Warning}
     */
    public String severityText() {
        return finding.isError() ? FieldState.ERROR.words() : FieldState.WARNING.words();
    }

    /**
     * The finding's message.
     *
     * @return the message
     */
    public String message() {
        return finding.message();
    }

    /**
     * The parameter the finding is shown at: its first responsible parameter.
     *
     * @return the name, or empty for a finding about the file as a whole
     */
    public Optional<String> parameter() {
        return finding.parameters().stream().findFirst();
    }

    /**
     * The category the finding is attached to.
     *
     * @return the category, or empty
     */
    public Optional<ParameterCategory> category() {
        return finding.category();
    }

    /**
     * The field a view moves focus to when the entry is activated.
     *
     * @return the parameter's name, or empty when the release has no field for it
     */
    public Optional<String> focusTarget() {
        return parameterDisplayName.isPresent() ? parameter() : Optional.empty();
    }

    /**
     * The entry in one line of text.
     *
     * @return for example {@code Error -- Precursor tolerance, lower bound
     *     (peptide_mass_tolerance_lower), Precursor mass and isotope handling: ...}
     */
    public String text() {
        StringBuilder line = new StringBuilder(severityText());
        parameter()
                .ifPresent(
                        name -> {
                            line.append(" -- ");
                            parameterDisplayName.ifPresent(
                                    shown -> line.append(shown).append(" ("));
                            line.append(name);
                            parameterDisplayName.ifPresent(shown -> line.append(')'));
                        });
        category().ifPresent(group -> line.append(", ").append(group.displayName()));
        return line.append(": ").append(message()).toString();
    }
}
