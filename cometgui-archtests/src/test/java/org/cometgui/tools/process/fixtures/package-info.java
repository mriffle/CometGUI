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

/**
 * Legal process creation: a class that constructs a {@link ProcessBuilder} from inside the package
 * {@code R-PROC-02} confines process creation to.
 *
 * <p>This is the positive control. A rule that rejected every use of {@code ProcessBuilder}
 * anywhere would pass every negative control in {@link
 * org.cometgui.archtests.ProcessCreationRuleTest} and would break the product on the next build;
 * without a fixture the rule must ACCEPT there is no way to tell that rule from a correct one.
 */
package org.cometgui.tools.process.fixtures;
