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

package org.cometgui.params.comet.presets;

/**
 * A preset document CometGUI cannot stand behind, naming the preset (or section) and the field at
 * fault.
 */
public final class InvalidPresetException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String where;

    private final String field;

    /**
     * Creates the exception.
     *
     * @param where the preset or section, such as {@code preset "low-low"}
     * @param field the field at fault
     * @param problem what is wrong with it
     */
    public InvalidPresetException(String where, String field, String problem) {
        super("invalid Comet parameter preset: " + where + ", field \"" + field + "\": " + problem);
        this.where = where;
        this.field = field;
    }

    /**
     * The preset or section at fault.
     *
     * @return where
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
