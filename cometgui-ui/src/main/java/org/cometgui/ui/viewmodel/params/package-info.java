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
 * The Comet parameter editor's view-models (Phase 07): toolkit-free, like the rest of {@code
 * org.cometgui.ui.viewmodel}, and bound by the same rule -- no scene graph, no stage, no
 * application class, no {@code Platform.runLater}.
 *
 * <p>The parameter model in {@code cometgui-params-comet} is the source of truth (decision P7-1).
 * {@link org.cometgui.ui.viewmodel.params.ParameterSession} holds one immutable {@code
 * CometParameters} and replaces it through the model's own operations; every finding shown anywhere
 * comes from its one {@code ValidationReport}. Nothing here parses a number, splits a tuple or
 * judges a value, and an architecture rule ({@code cometgui-archtests}) keeps this whole module
 * away from the parser's line reader, the value codecs, {@code Numbers} and {@code
 * java.util.regex}.
 *
 * <ul>
 *   <li>{@link org.cometgui.ui.viewmodel.params.ParameterSession} -- the configuration, its
 *       release, a migration under review, adoption with the workflow's outputs enforced, edits,
 *       resets, the decoy source.
 *   <li>{@link org.cometgui.ui.viewmodel.params.FieldViewModel} -- one parameter of the selected
 *       release: its facts, text, origin, findings, refusal, lock, and its state in words.
 *   <li>{@link org.cometgui.ui.viewmodel.params.EssentialsSection} and {@link
 *       org.cometgui.ui.viewmodel.params.AdvancedCategory} -- the curated, task-ordered Essentials
 *       surface and the fourteen Advanced categories.
 *   <li>{@link org.cometgui.ui.viewmodel.params.ValidationSummaryViewModel} and {@link
 *       org.cometgui.ui.viewmodel.params.RunReadinessViewModel} -- the summary of the report, and
 *       whether the Run control may be enabled.
 *   <li>{@link org.cometgui.ui.viewmodel.params.SpectrumInputsViewModel} and {@link
 *       org.cometgui.ui.viewmodel.params.FileChooserPort} -- spectrum files and the database,
 *       chosen through a port the view implements and a test replaces.
 *   <li>The structured-value editors, each handing the model one text per control and never
 *       splitting, reading or judging a value itself: {@link
 *       org.cometgui.ui.viewmodel.params.VariableModsViewModel} (every slot of the release's
 *       layout, presets, add, edit, move, remove, and the two parameters beside the slots), {@link
 *       org.cometgui.ui.viewmodel.params.EnzymesViewModel}, {@link
 *       org.cometgui.ui.viewmodel.params.StaticModsViewModel}, {@link
 *       org.cometgui.ui.viewmodel.params.IonSeriesViewModel}, {@link
 *       org.cometgui.ui.viewmodel.params.ToleranceViewModel} and {@link
 *       org.cometgui.ui.viewmodel.params.RangesViewModel}.
 * </ul>
 *
 * <p>The view-model coverage rule ({@code coverage-check-viewmodel}, {@code
 * org.cometgui.ui.viewmodel*}) applies to this package as a package of its own.
 */
package org.cometgui.ui.viewmodel.params;
