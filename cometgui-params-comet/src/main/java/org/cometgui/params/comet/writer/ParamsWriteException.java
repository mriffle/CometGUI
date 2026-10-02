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

package org.cometgui.params.comet.writer;

import java.io.Serial;
import java.util.Objects;

/**
 * Thrown when the canonical writer refuses a model: today, one whose {@code search_enzyme_number},
 * {@code search_enzyme2_number} or {@code sample_enzyme_number} names an enzyme absent from the
 * table it would write. Nothing is written when it is thrown.
 */
public final class ParamsWriteException extends IllegalArgumentException {

    @Serial private static final long serialVersionUID = 1L;

    /** The parameter at fault. */
    private final String parameter;

    /**
     * Creates the exception.
     *
     * @param parameter the parameter at fault
     * @param message the refusal, naming the parameter and the value
     */
    public ParamsWriteException(String parameter, String message) {
        super(Objects.requireNonNull(message, "message"));
        this.parameter = Objects.requireNonNull(parameter, "parameter");
    }

    /**
     * The parameter at fault.
     *
     * @return for example {@code search_enzyme_number}
     */
    public String parameter() {
        return parameter;
    }
}
