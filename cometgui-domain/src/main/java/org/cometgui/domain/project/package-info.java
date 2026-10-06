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
 * Project identity and layout: the user's project, its directories, its inputs and the runs it
 * owns.
 *
 * <p>Filled by phase 08 (workflow engine and run storage), as pure models with no file access:
 * {@link org.cometgui.domain.project.ProjectId} and {@link
 * org.cometgui.domain.project.ProjectDescriptor} (what {@code project.json} holds), {@link
 * org.cometgui.domain.project.ProjectLayout} (where a project's files are), {@link
 * org.cometgui.domain.project.LockOwner} (who holds {@code project.lock}, {@code R-RUN-05}) and
 * {@link org.cometgui.domain.project.SchemaVersionPolicy} ({@code R-RUN-04}'s policy for an older
 * or newer document, shared by {@code project.json} and {@code run.json}). Reading and writing them
 * is {@code org.cometgui.workflow.storage}'s.
 */
package org.cometgui.domain.project;
