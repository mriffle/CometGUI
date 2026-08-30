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
 * Reusable controls, and the two conventions every view in this module depends on.
 *
 * <p>{@link org.cometgui.ui.controls.UiIds} holds every stable identifier the interface sets, so
 * that the views which set them and the tests which look them up cannot drift apart ({@code
 * R-TEST-04}). {@link org.cometgui.ui.controls.AccessibleControls} is the only way a control is
 * given an accessible name, and it refuses a blank one.
 *
 * <p>{@link org.cometgui.ui.controls.StageStepper} draws the Run screen's workflow diagram with
 * every stage's state in words rather than in colour. {@code
 * org.cometgui.ui.controls.derived.ConsolePane} is the live console; it is derived from
 * Noble-Lab/CasanovoGUI and lives in the {@code derived} sub-package for that reason.
 *
 * <p>Controls required by automated tests carry stable semantic identifiers (R-TEST-04); tests
 * never locate them by pixel position.
 *
 * <p>Extended by phase 07 (Comet parameter editor UI) with the structured parameter editors.
 */
package org.cometgui.ui.controls;
