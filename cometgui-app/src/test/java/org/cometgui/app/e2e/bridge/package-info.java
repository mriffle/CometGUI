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
 * The test-only loopback control bridge (specification {@code R-TEST-06}; Phase 14 design decision
 * P14-11): a Java agent that Tier B attaches, at launch, to the <em>unmodified</em> installed
 * CometGUI, so that the tier-neutral scenarios can drive the packaged application in another
 * process.
 *
 * <h2>What it is</h2>
 *
 * <ul>
 *   <li>{@link org.cometgui.app.e2e.bridge.BridgeAgent} -- the agent's entry point. It does nothing
 *       at all in a JVM that is not the packaged CometGUI launcher; the child JVMs the product
 *       starts (the Limelight converter, PDV) may inherit the environment that attached it.
 *   <li>{@link org.cometgui.app.e2e.bridge.BridgeServer} -- a server on {@code 127.0.0.1} only, on
 *       an ephemeral port written to a file the test names, refusing every request that does not
 *       carry the per-launch token it wrote beside the port. Every operation it performs on the
 *       interface is a {@link org.cometgui.app.uidriver.RemotableUiDriver} method executed by one
 *       {@link org.cometgui.app.uidriver.RobotFxUiDriver} (P14-3): synthetic input and scene-graph
 *       reads, never a view-model, a port or the workflow, which is how it cannot bypass parameter
 *       serialisation, validation or orchestration.
 *   <li>{@link org.cometgui.app.e2e.bridge.DialogsTransformer} and {@link
 *       org.cometgui.app.e2e.bridge.BridgeDialogs} -- the native file dialog, and only it, replaced
 *       by answers the test queued; the product's own chooser logic (title, filters, what a
 *       cancelled dialog means) still runs.
 *   <li>{@link org.cometgui.app.e2e.bridge.BridgeProtocol} -- the wire format, shared with the test
 *       side's client in {@code e2e.tierb}.
 * </ul>
 *
 * <h2>Never shipped</h2>
 *
 * <p>These are test classes: they are compiled into {@code cometgui-app/target/test-classes} and
 * jarred by {@code scripts/e2e/build-bridge.sh}, never packaged. {@code
 * scripts/ci/release-no-test-bridge.sh} inspects the packaged artefact and fails on any class of
 * this package, of the driver package, or of the test frameworks the jar carries, and on a {@code
 * -javaagent} or the end-to-end flag in the launcher's configuration (P14-12).
 */
package org.cometgui.app.e2e.bridge;
