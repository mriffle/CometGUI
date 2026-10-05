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
import org.cometgui.params.comet.model.ParameterValue;
import org.cometgui.params.comet.validation.Finding;
import org.cometgui.params.comet.value.ValueSyntaxException;

/**
 * A two-value range on one line -- {@code peptide_length_range}, {@code digest_mass_range}, {@code
 * scan_range}, {@code precursor_charge}, {@code clear_mz_range} -- as two paired controls under one
 * label. The model reads the two texts ({@code CometParameters.rangeValue}); whether they are in
 * order is the model's {@code ordered_range} rule, shown in {@link #findings()}, and never judged
 * here.
 */
public final class RangeViewModel {

    private final ParameterSession session;

    private final String name;

    RangeViewModel(ParameterSession session, String name) {
        this.session = Objects.requireNonNull(session, "session");
        this.name = Objects.requireNonNull(name, "name");
    }

    /**
     * The range's field: its one label, help, origin, refusal and state in words.
     *
     * @return the field
     */
    public FieldViewModel field() {
        return session.field(name);
    }

    /**
     * The label both controls share.
     *
     * @return for example {@code Peptide length range}
     */
    public String label() {
        return field().displayName();
    }

    /**
     * The first value's text in the configuration.
     *
     * @return for example {@code 5}
     */
    public String firstText() {
        return session.model().rangeTexts(name).get(0);
    }

    /**
     * The second value's text in the configuration.
     *
     * @return for example {@code 50}
     */
    public String secondText() {
        return session.model().rangeTexts(name).get(1);
    }

    /**
     * Sets both values from the two controls' texts.
     *
     * @param first the first value's text
     * @param second the second value's text
     * @return whether the configuration now holds them, or the model's refusal, which the field
     *     shows (with the two texts as entered, for display only)
     */
    public EditOutcome set(String first, String second) {
        ParameterValue value;
        try {
            value = session.model().rangeValue(name, first, second);
        } catch (ValueSyntaxException unreadable) {
            return session.refuse(name, first + " " + second, unreadable.getMessage());
        }
        return session.setValue(name, value);
    }

    /**
     * The range's findings, the ordering rule's among them.
     *
     * @return the findings
     */
    public List<Finding> findings() {
        return field().findings();
    }
}
