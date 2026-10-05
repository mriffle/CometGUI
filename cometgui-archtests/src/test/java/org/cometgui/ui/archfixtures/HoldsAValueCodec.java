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

import org.cometgui.params.comet.value.VariableModCodec;

/** Holds a value codec, without calling it: one refactor from splitting a tuple in the UI. */
public final class HoldsAValueCodec {

    private final VariableModCodec codec;

    HoldsAValueCodec(VariableModCodec codec) {
        this.codec = codec;
    }

    VariableModCodec codec() {
        return codec;
    }
}
