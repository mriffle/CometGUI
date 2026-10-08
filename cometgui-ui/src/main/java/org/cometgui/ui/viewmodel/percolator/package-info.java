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
 * The Percolator section's view-models (Phase 09, decision P9-12): toolkit-free, like the rest of
 * {@code org.cometgui.ui.viewmodel}, and bound by the same rule -- no scene graph, no stage, no
 * application class, no {@code Platform.runLater}.
 *
 * <p>Everything scientific is the model's. Which Percolator is the default, which newer versions
 * were passed over and why, whether Limelight conversion can run and what to do when it cannot, the
 * advisories, which settings a build accepts and what a valid setting is: each is read from {@code
 * cometgui-params-percolator} ({@code PercolatorResolver}, {@code ResolutionChange}, {@code
 * AdvisoryRendering}, {@code PercolatorSettings}) and from {@code cometgui-results}' display
 * filters, and shown in the model's own words. No class here compares a Percolator version (P9-2):
 * a version is only ever shown.
 *
 * <ul>
 *   <li>{@link org.cometgui.ui.viewmodel.percolator.PercolatorViewModel} -- the section: the
 *       builds, the version selector with the resolved default marked, the Limelight-conversion
 *       switch and the notice when it moves the default, the reasons, the advisories, Limelight's
 *       availability, local-binary registration, Advanced settings and the display filters; and the
 *       {@link org.cometgui.ui.viewmodel.params.PercolatorRequest} the Run section runs with.
 *   <li>{@link org.cometgui.ui.viewmodel.percolator.PercolatorRerunViewModel} -- the
 *       compatible-version rerun of the session's last run (P9-11).
 *   <li>{@link org.cometgui.ui.viewmodel.percolator.PercolatorPort} and {@link
 *       org.cometgui.ui.viewmodel.percolator.PercolatorRerunPort} -- what the composition root
 *       implements over the Tool Manager and the workflow engine; never called on the interface
 *       thread.
 * </ul>
 */
package org.cometgui.ui.viewmodel.percolator;
