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
import org.cometgui.params.comet.model.CometParameters;

/**
 * A raw Expert edit that parsed, waiting for explicit confirmation before it changes the typed
 * configuration (<em>Expert</em>: "explicit confirmation before a raw edit changes the typed
 * configuration").
 *
 * @param base the configuration the draft was checked against; confirming is refused once the
 *     configuration is another
 * @param proposed what confirming would make the configuration: the draft's parse, every value the
 *     draft changed with origin {@code USER} and every other keeping its origin, outputs enforced
 * @param changes what confirming would change, as diff rows (current, then proposed)
 * @param warnings the parse's warnings in words -- a version mismatch among them ({@code
 *     R-PARAM-06})
 * @param enforced the draft's values the workflow will not take, each with the value kept and why
 */
public record RawApplyProposal(
        CometParameters base,
        CometParameters proposed,
        List<DiffRowView> changes,
        List<String> warnings,
        List<String> enforced) {

    /** Validates presence and takes immutable copies. */
    public RawApplyProposal {
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(proposed, "proposed");
        changes = List.copyOf(changes);
        warnings = List.copyOf(warnings);
        enforced = List.copyOf(enforced);
    }
}
