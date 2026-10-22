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
 * Tier A of the end-to-end harness (specification, "End-to-end harness: two tiers"): the real
 * application module in-process, over a fresh temporary home and tool cache, composed as design
 * decision P14-4 says -- the production composition root with only the environment and the
 * file-chooser factory supplied by the test -- running the tier-neutral scenarios of {@code
 * e2e.scenario}. Every class here is opt-in ({@link org.cometgui.app.e2e.support.EndToEnd}).
 */
package org.cometgui.app.e2e.tiera;
