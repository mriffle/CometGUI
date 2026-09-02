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
 * The headless GUI suite: the tests that drive the real, running application through {@code
 * FxUiDriver}.
 *
 * <p>Four of phase 02's five exit-gate items are proved here, one class each, and each class starts
 * its own application in its own JVM ({@code reuseForks=false}):
 *
 * <ul>
 *   <li>{@code SectionNavigationUiTest} -- items 1 (by mouse) and 2: a robot click on every
 *       navigation entry, and every section asserted present and showing by its stable identifier
 *       while the other nine are not.
 *   <li>{@code KeyboardOnlyNavigationUiTest} -- item 1 (by keyboard alone): Tab into the
 *       navigation, then every primary section reached with arrow keys and nothing else.
 *   <li>{@code AccessibleNameEnumerationUiTest} -- item 4: every {@code Control} in the whole scene
 *       graph enumerated, each required to have a non-blank accessible name.
 *   <li>{@code ConsoleFloodUiTest} -- item 5: the console pane under a flood far larger than either
 *       cap, with the retained model, the rendered document, the discarded-count summary and the
 *       retained heap all asserted.
 * </ul>
 *
 * <p>Every one of them asserts a value -- an identifier found, a text read, the number of showing
 * panes, the sequence number of the oldest retained line, a measured number of bytes. None of them
 * asserts that nothing threw.
 */
package org.cometgui.app.gui;
