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
 * Where a project and its runs live on disk, in versioned formats, so that a run can never be
 * changed after the fact ({@code R-RUN-03}..{@code R-RUN-06}).
 *
 * <p>{@link org.cometgui.workflow.storage.ProjectStore} creates a project and reads its {@code
 * project.json}; {@link org.cometgui.workflow.storage.ProjectLock} is {@code R-RUN-05}'s lock with
 * its stale-lock recovery; {@link org.cometgui.workflow.storage.RunStore} reserves a run directory,
 * writes {@code run.json} once and updates it only with what {@link
 * org.cometgui.domain.run.RunDescriptor#requireSuccessor} permits. {@link
 * org.cometgui.workflow.storage.ProjectJson} and {@link org.cometgui.workflow.storage.RunJson} are
 * the two formats, written through the one {@code JsonWriter}, read through the one {@code
 * JsonReader}, and replaced through the one {@code AtomicDocumentWriter} (design decisions P8-1,
 * P8-2, P8-11). The models are pure and live in {@code org.cometgui.domain.project} and {@code
 * org.cometgui.domain.run}. The formats' reference is {@code docs/reference/project_format.rst}.
 *
 * <p>Written by phase 08, work unit 2.
 */
package org.cometgui.workflow.storage;
