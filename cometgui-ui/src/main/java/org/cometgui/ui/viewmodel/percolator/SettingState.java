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

package org.cometgui.ui.viewmodel.percolator;

import java.util.Objects;
import org.cometgui.params.percolator.schema.PercolatorSetting;

/**
 * One Advanced Percolator setting as the section shows it: the text in its field, whether the
 * selected build accepts it, and what is said about it.
 *
 * @param setting the setting
 * @param text the text its field holds -- the value, or text the model refused
 * @param editable whether the selected build was observed to accept it (or no build's support is
 *     known yet), so its field can be edited
 * @param state the setting's description ({@link PercolatorSetting#description()}), then, on its
 *     own line, why it is not supported or why its text was refused, when either is so
 * @param valid whether the text is a value the model accepted
 */
public record SettingState(
        PercolatorSetting setting, String text, boolean editable, String state, boolean valid) {

    /**
     * Validates.
     *
     * @throws NullPointerException if a component is {@code null}
     * @throws IllegalArgumentException if the state is blank
     */
    public SettingState {
        Objects.requireNonNull(setting, "setting");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(state, "state");
        if (state.isBlank()) {
            throw new IllegalArgumentException("a setting's state is never blank");
        }
    }
}
