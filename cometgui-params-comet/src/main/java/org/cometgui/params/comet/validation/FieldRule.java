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
import org.cometgui.params.comet.model.ParameterEntry;

/** The implementation of one metadata {@link org.cometgui.params.comet.schema.ValidatorId}. */
@FunctionalInterface
interface FieldRule {

    /**
     * Checks one parameter that names this rule's validator.
     *
     * @param model the whole model, for rules that need another parameter or the enzyme table
     * @param entry the parameter
     * @param findings where to record what is found
     */
    void check(CometParameters model, ParameterEntry entry, Findings findings);
}
