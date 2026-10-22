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
 * The end-to-end scenarios, written once and tier-neutral (Phase 14 design decision P14-3): each
 * step drives the interface through {@link org.cometgui.app.uidriver.RemotableUiDriver} alone,
 * answers file dialogs through {@link org.cometgui.app.e2e.scenario.ChooserScript}, runs any tool
 * of its own oracles through {@link org.cometgui.app.e2e.scenario.OracleProcesses}, and proves what
 * the application claims with the test's own readers of the files on disk (P14-7).
 *
 * <p>Nothing in this package touches a scene-graph node, a scene, the JavaFX application thread or
 * a product class, so the same steps run in Tier A, in-process ({@code e2e.tiera}), and in Tier B,
 * against a packaged application in another process ({@code e2e.tierb}). Every path a scenario uses
 * is a parameter ({@link org.cometgui.app.e2e.scenario.ScenarioLayout}), never a constant.
 */
package org.cometgui.app.e2e.scenario;
