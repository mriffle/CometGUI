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

package org.cometgui.params.comet.validation;

import java.util.stream.Collectors;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.model.ParameterEntry;
import org.cometgui.params.comet.schema.Choice;

/**
 * Validator {@code choice}: an enumerated parameter's value is one of its labelled choices.
 *
 * <p>The choices are the metadata's, curated from Comet's source at {@code v2026.02.2} where it
 * differs from the documentation ({@code isotope_error} 0 to 7, {@code peff_format} 0 to 5). Comet
 * itself replaces many out-of-range codes with a default without a word -- {@code decoy_search}
 * outside 0 to 2 becomes 0, {@code peptide_mass_units} outside 0 to 2 becomes 0 ({@code
 * CometSearchManager.cpp}) -- which is why an unlisted value is an error here and not a warning.
 */
final class ChoiceRule {

    private ChoiceRule() {}

    static void check(CometParameters model, ParameterEntry entry, Findings findings) {
        String text = model.text(entry.name());
        for (Choice choice : entry.definition().choices()) {
            if (choice.value().equals(text)) {
                return;
            }
        }
        findings.add(
                Rule.CHOICE_NOT_LISTED,
                entry.name(),
                entry.name()
                        + " = "
                        + text
                        + " is not one of its values; use one of "
                        + entry.definition().choices().stream()
                                .map(choice -> choice.value() + " (" + choice.label() + ")")
                                .collect(Collectors.joining(", ")));
    }
}
