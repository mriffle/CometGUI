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
import org.cometgui.params.comet.model.ParameterValue;
import org.cometgui.params.comet.schema.Choice;
import org.cometgui.params.comet.schema.ParameterCategory;
import org.cometgui.params.comet.validation.Finding;
import org.cometgui.params.comet.value.EnzymeDefinition;
import org.cometgui.params.comet.value.EnzymeTable;
import org.cometgui.params.comet.value.ValueSyntaxException;

/**
 * The enzyme selectors and the custom-enzyme editor: {@code search_enzyme_number}, the second
 * enzyme and the sample enzyme chosen from the configuration's {@code [COMET_ENZYME_INFO]} rows, a
 * custom row added through the model, and the digestion parameters beside them.
 *
 * <p>A selector offers only the table's rows, so the editor never picks a number the table lacks; a
 * row a selector uses cannot be removed; and the model's {@code enzyme_in_table} rule -- with the
 * writer's refusal behind it -- reports any selected number the table lacks anyway (an imported
 * file), at the field. A custom row's texts are read by the model ({@code
 * EnzymeDefinition.fromTexts}); its refusal, and the table's refusal of a number used twice, are
 * shown as they are.
 */
public final class EnzymesViewModel {

    /** The parameter that sets the enzymatic termini. */
    static final String TERMINI = "num_enzyme_termini";

    /** The parameter that sets the missed cleavages. */
    static final String MISSED = "allowed_missed_cleavage";

    private final ParameterSession session;

    /**
     * The enzyme editor of a session.
     *
     * @param session the session
     */
    public EnzymesViewModel(ParameterSession session) {
        this.session = Objects.requireNonNull(session, "session");
    }

    /**
     * The parameters that select a row, in the metadata's order: the search enzyme, the second
     * search enzyme and the sample enzyme.
     *
     * @return their fields
     */
    public List<FieldViewModel> selectors() {
        return session.metadata().enzymeTable().referencedBy().stream()
                .map(session::field)
                .toList();
    }

    /**
     * Every row of the configuration's table, in table order.
     *
     * @return the options
     */
    public List<EnzymeOption> options() {
        List<EnzymeOption> options = new ArrayList<>();
        for (EnzymeDefinition row : session.model().enzymeTable().rows()) {
            options.add(new EnzymeOption(row, senseWords(row.sense())));
        }
        return List.copyOf(options);
    }

    /**
     * The row a selector holds.
     *
     * @param selector one of {@link #selectors()}' parameters
     * @return the row, or empty when the selected number is not in the table (the field then shows
     *     the model's {@code enzyme_in_table} error)
     */
    public Optional<EnzymeOption> selected(String selector) {
        int number = number(selector);
        return options().stream().filter(option -> option.number() == number).findFirst();
    }

    /**
     * Selects a row for a selector.
     *
     * @param selector one of {@link #selectors()}' parameters
     * @param option a row of the configuration's table
     * @return whether the selector now holds it, or why not: the row is not in the table
     */
    public EditOutcome select(String selector, EnzymeOption option) {
        Objects.requireNonNull(option, "option");
        requireSelector(selector);
        if (!session.model().enzymeTable().rows().contains(option.row())) {
            return EditOutcome.refused(
                    option.label()
                            + " is not a row of this configuration's enzyme table, so it cannot be"
                            + " selected");
        }
        return session.setValue(selector, new ParameterValue.Whole(option.number()));
    }

    /**
     * The choices of a custom row's sense, in the metadata's words.
     *
     * @return the choices
     */
    public List<ChoiceOption> senseChoices() {
        List<ChoiceOption> choices = new ArrayList<>();
        for (Choice choice : session.metadata().enzymeTable().senseChoices()) {
            choices.add(new ChoiceOption(choice.value(), choice.label()));
        }
        return List.copyOf(choices);
    }

    /**
     * The number a new custom row is offered: one more than the table's highest.
     *
     * @return the number, as the editor's number field starts
     */
    public String nextNumberText() {
        return Integer.toString(session.model().enzymeTable().nextNumber());
    }

    /**
     * Adds a custom row from the editor's texts.
     *
     * @param numberText the row number
     * @param name the enzyme's name
     * @param sense one of {@link #senseChoices()}
     * @param cutResidues the cut residues; empty or {@code -} for none
     * @param noCutResidues the no-cut residues; empty or {@code -} for none
     * @return whether the table now holds it, or the model's refusal
     */
    public EditOutcome addCustom(
            String numberText,
            String name,
            ChoiceOption sense,
            String cutResidues,
            String noCutResidues) {
        Objects.requireNonNull(sense, "sense");
        EnzymeDefinition.Sense side = null;
        for (EnzymeDefinition.Sense candidate : EnzymeDefinition.Sense.values()) {
            if (Integer.toString(candidate.code()).equals(sense.token())) {
                side = candidate;
            }
        }
        if (side == null) {
            return EditOutcome.refused(
                    "\"" + sense.withToken() + "\" is not one of the enzyme table's senses");
        }
        EnzymeTable table = session.model().enzymeTable();
        EnzymeTable added;
        try {
            added =
                    table.with(
                            EnzymeDefinition.fromTexts(
                                    numberText, name, side, cutResidues, noCutResidues));
        } catch (ValueSyntaxException unreadable) {
            return EditOutcome.refused(unreadable.getMessage());
        } catch (IllegalArgumentException duplicate) {
            return EditOutcome.refused(duplicate.getMessage());
        }
        session.setEnzymeTable(added);
        return EditOutcome.applied();
    }

    /**
     * Removes a row. A row a selector holds is refused, naming the selector, so the editor never
     * leaves a selected number without its row.
     *
     * @param option a row of the configuration's table
     * @return whether it was removed, or why not
     */
    public EditOutcome remove(EnzymeOption option) {
        Objects.requireNonNull(option, "option");
        for (FieldViewModel selector : selectors()) {
            if (number(selector.name()) == option.number()) {
                return EditOutcome.refused(
                        option.label()
                                + " is selected as "
                                + selector.displayName()
                                + " ("
                                + selector.name()
                                + "); select another enzyme there before removing it");
            }
        }
        EnzymeTable table = session.model().enzymeTable();
        if (!table.rows().contains(option.row())) {
            return EditOutcome.refused(
                    option.label() + " is not a row of this configuration's enzyme table");
        }
        session.setEnzymeTable(table.without(option.number()));
        return EditOutcome.applied();
    }

    /**
     * The enzymatic termini, whose choices carry the release's words for 1, 2, 8 and 9.
     *
     * @return the field of {@code num_enzyme_termini}
     */
    public FieldViewModel termini() {
        return session.field(TERMINI);
    }

    /**
     * The allowed missed cleavages.
     *
     * @return the field of {@code allowed_missed_cleavage}
     */
    public FieldViewModel missedCleavages() {
        return session.field(MISSED);
    }

    /**
     * The findings of the digestion category: the enzyme numbers, the table's rows and numbering,
     * termini and missed cleavages, in report order.
     *
     * @return the findings
     */
    public List<Finding> findings() {
        return session.report().forCategory(ParameterCategory.DIGESTION_ENZYMES);
    }

    private int number(String selector) {
        requireSelector(selector);
        return ((ParameterValue.Whole) session.model().value(selector)).value();
    }

    private void requireSelector(String selector) {
        if (!session.metadata().enzymeTable().referencedBy().contains(selector)) {
            throw new IllegalArgumentException(selector + " does not select an enzyme row");
        }
    }

    private String senseWords(EnzymeDefinition.Sense sense) {
        String token = Integer.toString(sense.code());
        for (ChoiceOption choice : senseChoices()) {
            if (choice.token().equals(token)) {
                return choice.label();
            }
        }
        return "sense " + token;
    }
}
