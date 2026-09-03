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
 * The adapter contract shared by every tool: ToolAdapter, ToolCommand, ToolExecutionRequest,
 * ToolExecutionResult and their exceptions.
 *
 * <p><strong>Phase 05 unit 7 landed the first half of it</strong>: what every probe needs to run a
 * tool and read its answer -- {@code ToolRunner} over the process service, {@code ToolRunOutcome},
 * the {@code JavaRuntime} that starts a JAR, the {@code JarAttributes} that read one, the {@code
 * CompanionGate} that {@code R-TOOL-02} states as manifest data, and {@code JavaToolIdentities},
 * which is the object the composition root hands the installer as its {@code JavaArtefactIdentity}.
 *
 * <p>{@code ToolAdapter}, {@code ToolExecutionRequest} and {@code ToolExecutionResult} -- the
 * contract for <em>running a scientific stage</em> rather than probing a build -- are still phase
 * 08's, which defines the contract the later adapters implement.
 */
package org.cometgui.tools.api;
