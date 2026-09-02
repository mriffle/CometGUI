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
 * Run values and the workflow state model: NOT_STARTED, VALIDATING, READY, RUNNING, SUCCEEDED,
 * FAILED, CANCEL_REQUESTED, CANCELLED and SKIPPED, plus the derived overall run state.
 *
 * <p>Phase 02 added {@link org.cometgui.domain.run.RunId}, the validated identifier that names a
 * run's directory and its provenance manifest, and {@link org.cometgui.domain.run.StageTag}, the
 * one thing the domain needs to know about a workflow stage. The state model itself is filled by
 * phase 08 (workflow engine and Comet adapter).
 */
package org.cometgui.domain.run;
