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
 * Test-only helpers shared by the {@code cometgui-ui} test suite.
 *
 * <p>Nothing here is shipped: the package exists only under {@code src/test/java}. It holds the
 * support a test needs and a production class must not grow -- currently {@link
 * org.cometgui.ui.testing.ViewModelSources}, which reads the view-model package's own sources for
 * the test that proves the layer never reaches for the JavaFX toolkit.
 */
package org.cometgui.ui.testing;
