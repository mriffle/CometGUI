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

import java.io.Serial;
import java.util.Objects;

/**
 * Thrown when the curated metadata file is not one {@link MetadataLoader} can stand behind: the
 * message names where the problem is (a parameter by name, or a section), the field, and the rule
 * broken.
 *
 * <p>Unchecked, because the file ships inside the application: a malformed one is a build defect,
 * and the build's own tests load it.
 */
public final class InvalidMetadataException extends RuntimeException {

    @Serial private static final long serialVersionUID = 1L;

    /** Where in the file, such as {@code parameter "isotope_error"} or {@code versions[0]}. */
    private final String where;

    /** The field at fault, such as {@code displayName}. */
    private final String field;

    /**
     * Creates the exception.
     *
     * @param where where in the file
     * @param field the field at fault
     * @param problem what is wrong with it
     */
    public InvalidMetadataException(String where, String field, String problem) {
        super(
                "invalid Comet parameter metadata: "
                        + Objects.requireNonNull(where, "where")
                        + ", field \""
                        + Objects.requireNonNull(field, "field")
                        + "\": "
                        + Objects.requireNonNull(problem, "problem"));
        this.where = where;
        this.field = field;
    }

    /**
     * Where in the file the problem is.
     *
     * @return for example {@code parameter "isotope_error"}
     */
    public String where() {
        return where;
    }

    /**
     * The field at fault.
     *
     * @return the field name
     */
    public String field() {
        return field;
    }
}
