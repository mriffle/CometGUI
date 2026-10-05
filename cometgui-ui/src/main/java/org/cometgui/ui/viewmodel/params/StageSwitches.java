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

package org.cometgui.ui.viewmodel.params;

/**
 * Which of the workflow's downstream stages are enabled, as the editor needs to know it: for an
 * output the workflow requires ({@code R-CMT-01}), whether a stage that reads it is enabled.
 *
 * <p>An input rather than a fact of this package. While such a stage is enabled the output is
 * locked on ({@code AC-PAR-09}); which stages a run enables is the workflow's to say, and no stage
 * can be disabled before Phases 11 and 12 exist, so {@link #ALL_ENABLED} is what the composition
 * root passes today. Which stage needs which output, in words, is the model's ({@code
 * WorkflowOutputs.stageNeeding}); this interface only answers whether that stage is on.
 */
@FunctionalInterface
public interface StageSwitches {

    /** Every downstream stage enabled: every required output locked on. */
    StageSwitches ALL_ENABLED = outputParameter -> true;

    /**
     * Whether a downstream stage that reads an output is enabled.
     *
     * @param outputParameter an output parameter the workflow requires, such as {@code
     *     output_percolatorfile}
     * @return {@code true} if a stage that reads it is enabled, so it must stay on
     */
    boolean dependentStageEnabled(String outputParameter);
}
