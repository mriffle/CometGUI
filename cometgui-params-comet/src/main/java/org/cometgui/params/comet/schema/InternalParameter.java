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

package org.cometgui.params.comet.schema;

import java.util.Objects;

/**
 * A parameter Comet declares that the metadata deliberately does not model, and why.
 *
 * <p>The specification permits such an allow-list for hidden or internal parameters only. A
 * parameter a user could reasonably set is not internal, and the bundled metadata for Comet
 * 2026.02.2 lists none.
 *
 * @param name the parameter name
 * @param reason why it is not modelled, in a sentence a reviewer can check
 */
public record InternalParameter(String name, String reason) {

    /** Validates the components. */
    public InternalParameter {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(reason, "reason");
    }
}
