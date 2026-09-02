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
 * View-models: the UI-independent half of the MVVM boundary the specification's <em>Architectural
 * style</em> requires.
 *
 * <p>Everything in this package is state and behaviour with no scene graph behind it. There is no
 * {@code Node}, no {@code Scene}, no {@code Stage}, no FXML and -- deliberately, and stated in each
 * class -- no {@code Platform.runLater}. What it does use is JavaFX <em>properties and observable
 * collections</em> ({@code javafx.beans}, {@code javafx.collections}), which need neither a started
 * toolkit nor a display, so every class here is exercised by ordinary unit tests rather than by a
 * robot driving a window.
 *
 * <p>That is not a testing convenience, it is the boundary itself. The views in {@code
 * org.cometgui.ui.view} translate user actions into calls on these classes and observe their
 * properties; marshalling onto the JavaFX application thread is the view's job, on the view's side
 * of the line. Anything that needs a {@code Node} belongs there, not here.
 *
 * <p>The package holds, as of phase 02:
 *
 * <ul>
 *   <li>{@link org.cometgui.ui.viewmodel.SectionId} -- the specification's information architecture
 *       as a type: eight primary sections, two secondary ones, each with a stable identifier used
 *       verbatim as an {@code fx:id} and in tests.
 *   <li>{@link org.cometgui.ui.viewmodel.NavigationViewModel} -- which section is selected, and the
 *       keyboard movement the phase's first exit-gate item requires.
 *   <li>{@link org.cometgui.ui.viewmodel.ConsoleViewModel} -- the stage and severity filters over
 *       the domain's bounded message log, and an honest statement of what its cap discarded.
 *   <li>{@link org.cometgui.ui.viewmodel.StageStepperViewModel} -- a state per workflow stage and
 *       the run state derived from them by the workflow module's own derivation.
 *   <li>{@link org.cometgui.ui.viewmodel.HostBaselineViewModel} -- the startup banner for a host
 *       that does not meet the baseline.
 * </ul>
 *
 * <p>The specification gives this package a coverage target of its own -- 80% line on
 * "UI-independent view-model and presenter logic" -- and the parent POM's {@code
 * coverage-check-viewmodel} rule enforces it against {@code org.cometgui.ui.viewmodel*} alone. A
 * class put here to escape the JavaFX layer's lack of a numeric target, or moved out of here to
 * escape this one, is a weakening of a gate.
 */
package org.cometgui.ui.viewmodel;
