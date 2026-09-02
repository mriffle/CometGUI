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
 * Deliberately illegal classes: each one creates or holds a process in a way {@code R-PROC-02}
 * forbids outside {@code org.cometgui.tools.process}.
 *
 * <p><strong>Why they are safe to keep in the tree.</strong> They are test sources, and {@link
 * org.cometgui.archtests.ProductClasses} imports with {@code ImportOption.DoNotIncludeTests}, so
 * the product rule set never sees them and stays green. {@link
 * org.cometgui.archtests.ProcessCreationRuleTest} imports them explicitly, with a plain {@code
 * ClassFileImporter}, and requires the shared R-PROC-02 rule to reject each one -- which is the
 * only way the rule's teeth travel with the rule instead of living in a shell harness that damages
 * a copy of the tree.
 *
 * <p>{@code ProcessCreationRuleTest} also asserts that none of these classes is in {@link
 * org.cometgui.archtests.ProductClasses#all()}, so the day {@code DoNotIncludeTests} is dropped is
 * the day a test says so rather than the day the whole rule set turns red for a reason nobody can
 * place.
 */
package org.cometgui.archtests.fixtures;
