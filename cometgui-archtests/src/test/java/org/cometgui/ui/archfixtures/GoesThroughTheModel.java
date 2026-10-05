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

package org.cometgui.ui.archfixtures;

import java.util.Optional;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.model.ValueOrigin;
import org.cometgui.params.comet.value.ValueSyntaxException;

/**
 * The legal shape: hands the text to the model and shows the model's refusal. It names the value
 * package's exception, which the rule must allow -- only codecs and {@code Numbers} are closed.
 */
public final class GoesThroughTheModel {

    private GoesThroughTheModel() {}

    static Optional<String> set(CometParameters model, String name, String text) {
        try {
            return Optional.of(model.withText(name, text, ValueOrigin.USER).text(name));
        } catch (ValueSyntaxException refused) {
            return Optional.empty();
        }
    }
}
