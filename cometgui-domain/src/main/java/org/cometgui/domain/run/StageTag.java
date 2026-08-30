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

package org.cometgui.domain.run;

/**
 * A workflow stage, seen from the domain.
 *
 * <p>It exists for one reason: the bounded console log lives in this module and has to tag each
 * message with the stage that produced it, but the domain must not depend on the workflow module.
 * So the console takes a {@code StageTag}, and {@code org.cometgui.workflow.state.WorkflowStage}
 * implements it. Nothing else about a stage -- its order, its transitions, whether it can be
 * skipped -- belongs here.
 */
public interface StageTag {

    /**
     * A stable identifier for the stage, suitable for a log file, a provenance record or a test
     * assertion. It does not change with the user's language or with the display text.
     *
     * @return the identifier, never {@code null} or blank
     */
    String id();

    /**
     * The stage's name as shown to the user.
     *
     * @return the display name, never {@code null} or blank
     */
    String displayName();
}
