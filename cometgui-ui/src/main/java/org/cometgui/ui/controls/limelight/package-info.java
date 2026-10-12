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
 * The Limelight section's panes (phase 12, work units 6 and 7): {@link
 * org.cometgui.ui.controls.limelight.LimelightPane}, over {@code
 * org.cometgui.ui.viewmodel.limelight.LimelightViewModel}, and in its upload area {@link
 * org.cometgui.ui.controls.limelight.LimelightUploadPane}, over {@code
 * org.cometgui.ui.viewmodel.limelight.LimelightUploadViewModel}. Every control only calls a
 * view-model; nothing is parsed, hashed, validated, launched or sent from here. The submit key's
 * field is a password field bound to nothing, emptied as soon as its text is handed over.
 */
package org.cometgui.ui.controls.limelight;
