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

package org.cometgui.ui.testing;

/**
 * Produces a {@code null} of a chosen type, for the tests that have to prove a method rejects one.
 *
 * <p>Passing a {@code null} literal to a method whose parameter SpotBugs has inferred to be
 * non-null is reported as {@code NP_NULL_PARAM_DEREF_ALL_TARGETS_DANGEROUS} -- the analyser
 * describing the test's entire purpose as the defect. The project's rule is that a SpotBugs finding
 * is fixed in the code rather than excluded, and this is the fix: the null arrives through a value
 * the analyser cannot constant-fold, so the finding does not arise and the assertion -- that the
 * rejection happens, with the right message -- is unchanged.
 *
 * <p>The same class exists in {@code org.cometgui.domain.testing} and {@code
 * org.cometgui.workflow.testing}, added by the units that met the same finding first. It is
 * duplicated rather than shared because a test-scope module dependency between product modules,
 * added only to carry three lines of scaffolding, is a worse thing to inherit.
 *
 * <p>It is test scaffolding and belongs nowhere near main sources.
 */
public final class Nulls {

    private Nulls() {}

    /**
     * A {@code null} typed as {@code T}.
     *
     * @param <T> the type the caller needs
     * @param type the class of that type, so that the call site reads as what it is doing
     * @return {@code null}, always
     */
    public static <T> T of(Class<T> type) {
        return type.cast(null);
    }
}
