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
 * The {@code FxUiDriver} abstraction and its two implementations, plus the harness that starts the
 * real application headlessly.
 *
 * <p><strong>Test sources, deliberately.</strong> specification.rst's {@code R-TEST-06} requires
 * that test-only machinery never reach a shipped artefact, and the cheapest way to keep that true
 * is for the machinery to live where it cannot be packaged: {@code src/test/java}. Nothing in this
 * package is compiled into {@code cometgui-app.jar}.
 *
 * <p><strong>Why an abstraction at all.</strong> The specification says that if TestFX cannot
 * operate reliably, "the project shall retain the same test semantics behind a small {@code
 * FxUiDriver} abstraction and use a compatible robot or accessibility automation mechanism". Phase
 * 00's spike (docs/feasibility/gui-automation-spike.rst) found that TestFX 4.0.18 does work on the
 * pinned JDK and JavaFX pair, and recommended keeping the fallback -- plain {@code
 * javafx.scene.robot.Robot} -- as a first-class citizen rather than a contingency. Both are here,
 * and the navigation tests run through both, so the day TestFX breaks against a new JavaFX the
 * fallback is already proved rather than proposed.
 */
package org.cometgui.app.uidriver;
