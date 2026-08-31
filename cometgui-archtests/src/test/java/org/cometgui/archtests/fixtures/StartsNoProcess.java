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

package org.cometgui.archtests.fixtures;

/**
 * An ordinary class outside the process service that starts nothing.
 *
 * <p>It is the subject the positive control needs. R-PROC-02's subject clause is "classes that
 * reside outside of the process service", so a class import holding only the legal in-package
 * fixture would match no subject at all and ArchUnit's {@code failOnEmptyShould} would fail the
 * check -- for the right reason, but not the reason the positive control is asking about. Adding
 * this class gives the rule a subject to accept.
 */
public final class StartsNoProcess {

    private StartsNoProcess() {}

    /**
     * @return what this class does, which is nothing a process rule cares about
     */
    static String describe() {
        return "outside the process service, and starts nothing";
    }
}
