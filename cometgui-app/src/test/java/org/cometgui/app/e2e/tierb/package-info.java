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
 * Tier B of the end-to-end harness (specification, "Tier B -- packaged-artefact E2E"): the exact
 * packaged archive, installed outside the build tree, launched as an external process with a fresh
 * home on the project's Xvfb, and driven through the test-only loopback bridge ({@code
 * e2e.bridge}).
 *
 * <ul>
 *   <li>{@link org.cometgui.app.e2e.tierb.InstalledCometGui} installs and removes the archive
 *       ({@code scripts/package/install-archive.sh}); {@link
 *       org.cometgui.app.e2e.tierb.PackagedDisplay} is the display and the environment the
 *       installed launcher needs on this host; {@link org.cometgui.app.e2e.tierb.LaunchedCometGui}
 *       starts the installed {@code bin/CometGUI} through the process service with the bridge
 *       attached, and stops it the way a user does.
 *   <li>{@link org.cometgui.app.e2e.tierb.BridgeClient} speaks the bridge's protocol; {@link
 *       org.cometgui.app.e2e.tierb.BridgedUiDriver} is the {@link
 *       org.cometgui.app.uidriver.RemotableUiDriver} over it and {@link
 *       org.cometgui.app.e2e.tierb.BridgedChooserScript} the {@link
 *       org.cometgui.app.e2e.scenario.ChooserScript}, so the tier-neutral scenarios run unchanged.
 *   <li>{@link org.cometgui.app.e2e.tierb.BridgeJar} builds the agent jar from the test classes
 *       this JVM is running ({@code scripts/e2e/build-bridge.sh}).
 *   <li>{@link org.cometgui.app.e2e.tierb.TierBApplication} is Tier B's composition, shared by
 *       {@code TierBCanonicalScenarioTest} and {@code TierBNoXmlScenarioTest}, which run the same
 *       scenario steps as Tier A against the installed artefact; {@code TierBLocaleControlTest}
 *       shows the launched-process locale check failing with no locale; {@link
 *       org.cometgui.app.e2e.tierb.PdvOnThisHost} is what step 25 can assert on a host without a
 *       system X11 library (Phase 14 E6).
 * </ul>
 *
 * <p>Every class here is opt-in ({@link org.cometgui.app.e2e.support.EndToEnd}) where it is a test.
 */
package org.cometgui.app.e2e.tierb;
