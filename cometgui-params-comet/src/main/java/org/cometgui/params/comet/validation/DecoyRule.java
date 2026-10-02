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

import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.model.ParameterValue;

/**
 * The decoy rules a parameter model can check without a FASTA, a PIN or a run ({@code R-DEC-01}).
 *
 * <p>The decoy source itself is one value, {@link org.cometgui.params.comet.model.DecoySource},
 * mapped to and from {@code decoy_search} on the model; an undocumented {@code decoy_search} is the
 * {@code choice} validator's error. Here: {@code decoy_prefix} must not be empty. Whatever the
 * source, Percolator and the Limelight converter tell decoys from targets by this prefix ({@code
 * R-DEC-03}), and with an internal decoy search Comet prepends it to every decoy; Comet 2026.02.2
 * reads the value as one token ({@code Comet.cpp} line 348), so an empty value is an empty prefix.
 * White space in it is {@link TextTokenRule}'s error.
 *
 * <p>Not here, because they need data: whether the FASTA already holds decoys ({@code R-DEC-02})
 * and whether the PIN holds both targets and decoys ({@code R-DEC-04}) are checked by the workflow
 * (Phase 08).
 */
final class DecoyRule {

    /** The parameter holding the prefix. */
    static final String PREFIX = "decoy_prefix";

    private DecoyRule() {}

    static void check(CometParameters model, Findings findings) {
        model.entry(PREFIX)
                .filter(entry -> ((ParameterValue.Text) entry.value()).text().isEmpty())
                .ifPresent(
                        entry ->
                                findings.add(
                                        Rule.DECOY_PREFIX_EMPTY,
                                        PREFIX,
                                        PREFIX
                                                + " is empty, but Percolator and the Limelight"
                                                + " converter tell decoys from targets by it;"
                                                + " use a prefix such as DECOY_"));
    }
}
