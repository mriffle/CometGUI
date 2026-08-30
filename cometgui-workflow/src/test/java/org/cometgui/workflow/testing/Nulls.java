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

package org.cometgui.workflow.testing;

/**
 * Produces a {@code null} of a chosen type, for the tests that have to prove a method rejects one.
 *
 * <p>A deliberate copy of {@code org.cometgui.domain.testing.Nulls}. That class lives in
 * cometgui-domain's <em>test</em> sources, which are not published as an artefact and are therefore
 * not on this module's class path; the alternatives -- a test-jar dependency between modules, or a
 * shared test-support module -- are both larger architectural changes than a nine-line helper
 * justifies, and neither is this unit's to make.
 *
 * <p>Why it exists at all: passing a {@code null} literal to a method whose parameter SpotBugs has
 * inferred to be non-null is reported as {@code NP_NULL_PARAM_DEREF_ALL_TARGETS_DANGEROUS} -- the
 * analyser describing the test's entire purpose as the defect. The project's rule is that a
 * SpotBugs finding is fixed in the code rather than excluded, and this is the fix: the null arrives
 * through a value the analyser cannot constant-fold, so the finding does not arise and the
 * assertion -- that the rejection happens, with the right message -- is unchanged.
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
