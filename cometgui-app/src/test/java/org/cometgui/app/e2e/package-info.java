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
 * The end-to-end test tree of Phase 14: the application driven the way a user drives it, through
 * the same commands the interface issues ({@code R-TEST-05}), with what it claims proved by
 * test-owned code reading the files on disk.
 *
 * <p>The phase document's {@code src/test/e2e/} is read as "the end-to-end test tree", not as a new
 * source root (design decision P14-1): a new root would need a new Maven module or a build-helper,
 * changes to the Spotless and Checkstyle file sets and to {@code build.sh}'s census, and this
 * module already carries the headless JavaFX setup. The subpackages are {@code support} (the
 * opt-in, the locale assertion, the fresh home), {@code oracle} (the independent readers and
 * counters), and -- as later work units fill them -- {@code scenario}, {@code tiera}, {@code
 * tierb}, {@code bridge} and {@code failure}.
 *
 * <p>Every end-to-end class carries {@link org.cometgui.app.e2e.support.EndToEnd}: it runs only
 * under {@code -Dcometgui.e2e=true}, is otherwise skipped with a stated reason, and asserts the
 * runner's locale before anything else. The oracles' own tests are ordinary and always run.
 *
 * <p>No end-to-end source waits a fixed time: every wait is {@link
 * org.cometgui.app.uidriver.Await}, and {@code scripts/e2e/check-no-fixed-sleep.sh} proves it over
 * the compiled classes (gate item 6).
 */
package org.cometgui.app.e2e;
