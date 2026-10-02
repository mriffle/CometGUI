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

package org.cometgui.params.comet.value;

import java.io.Serial;
import java.util.Objects;

/**
 * Thrown when a value's text cannot be read as its structured kind at all: the wrong number of
 * fields, a token that is not a number, a comma pair where the version accepts one value. The
 * message names what was being read (a parameter such as {@code variable_mod03}, or an enzyme row
 * and its line) and the field, so that a parser can attach the diagnostic where it belongs.
 *
 * <p>This is a parse error, not a validation rule: text that reads cleanly but means something
 * illegal (a lower bound above an upper, an enzyme number absent from the table) is the validation
 * package's to report.
 */
public final class ValueSyntaxException extends IllegalArgumentException {

    @Serial private static final long serialVersionUID = 1L;

    /** What was being read, such as {@code variable_mod03}. */
    private final String subject;

    /** The field at fault, such as {@code field 4 (count per peptide)}. */
    private final String field;

    /**
     * Creates the exception.
     *
     * @param subject what was being read, such as a parameter name
     * @param field the field at fault
     * @param problem what is wrong with it
     */
    public ValueSyntaxException(String subject, String field, String problem) {
        super(
                Objects.requireNonNull(subject, "subject")
                        + ", "
                        + Objects.requireNonNull(field, "field")
                        + ": "
                        + Objects.requireNonNull(problem, "problem"));
        this.subject = subject;
        this.field = field;
    }

    /**
     * What was being read.
     *
     * @return for example {@code variable_mod03} or {@code enzyme row at line 205}
     */
    public String subject() {
        return subject;
    }

    /**
     * The field at fault.
     *
     * @return for example {@code field 8 (neutral loss)}
     */
    public String field() {
        return field;
    }
}
