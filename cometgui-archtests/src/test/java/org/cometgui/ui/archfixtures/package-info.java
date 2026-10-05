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
 * Deliberately illegal UI classes for the Phase 07 rule that the UI goes through the parameter
 * model ({@code UiThroughTheModelRule}), and one legal one.
 *
 * <p>They live in {@code org.cometgui.ui} because that is the package the rule governs; they are
 * test sources of {@code cometgui-archtests}, so the product import, which excludes tests, never
 * sees them, and {@code UiThroughTheModelRuleTest} asserts that it does not.
 */
package org.cometgui.ui.archfixtures;
