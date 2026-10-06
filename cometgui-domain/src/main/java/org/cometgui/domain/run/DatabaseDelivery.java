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

package org.cometgui.domain.run;

import java.util.Objects;

/**
 * How the database reached Comet in a run ({@code R-CMT-04}: "the run shall record which mechanism
 * was used").
 */
public enum DatabaseDelivery {

    /** Through {@code database_name} in the canonical parameter file passed with {@code -P}. */
    PARAMETER_FILE("parameter-file"),

    /**
     * Through {@code -D} on the command line, overriding the parameter file's {@code database_name}
     * -- for example to search a cached index (P8-8).
     */
    COMMAND_LINE("command-line");

    private final String wireName;

    DatabaseDelivery(String wireName) {
        this.wireName = wireName;
    }

    /**
     * The name {@code run.json} records.
     *
     * @return {@code parameter-file} or {@code command-line}
     */
    public String wireName() {
        return wireName;
    }

    /**
     * The mechanism a recorded name stands for.
     *
     * @param wireName the recorded name
     * @return the mechanism
     * @throws NullPointerException if {@code wireName} is {@code null}
     * @throws IllegalArgumentException if no mechanism has that name
     */
    public static DatabaseDelivery fromWireName(String wireName) {
        Objects.requireNonNull(wireName, "wireName");
        for (DatabaseDelivery delivery : values()) {
            if (delivery.wireName.equals(wireName)) {
                return delivery;
            }
        }
        throw new IllegalArgumentException("not a database delivery: \"" + wireName + "\"");
    }
}
