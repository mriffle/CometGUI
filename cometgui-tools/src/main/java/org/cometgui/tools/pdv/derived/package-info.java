/*
 * CometGUI -- Comet to Percolator proteomics search workflow with provenance.
 * Copyright (C) 2026 The CometGUI authors.
 *
 * DERIVED FILE. This file is derived from Noble-Lab/CasanovoGUI and has been
 * modified for CometGUI. Upstream project:
 * <https://github.com/Noble-Lab/CasanovoGUI>, licensed GPL-3.0.
 * Copyright (C) the CasanovoGUI authors.
 *
 * The attribution above is collective because upstream carries no per-file
 * copyright notice: every CasanovoGUI source file begins with its package
 * statement, and `grep -rl Copyright --include=*.java src` in a clone of that
 * repository matches nothing. No notice was dropped in copying.
 *
 * WHICH upstream file, and at WHICH commit, is recorded per file in the
 * documentation comment below, because this header block is fixed and
 * identical in every derived file. config/checkstyle/checkstyle-derived.xml
 * requires that record and fails the build when it is missing.
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
 * PDV's live control path, reused from Noble-Lab/CasanovoGUI and kept in one place so that the
 * reuse is visible in the directory listing rather than only in a header ({@code R-PDV-05}).
 *
 * <p>THE CONVENTION ({@code CONTRIBUTING.rst}, <em>Files derived from CasanovoGUI</em>): a file is
 * derived if and only if its path contains a {@code /derived/} segment. Every file here carries
 * {@code config/license/java-header-derived.txt} and its own derivation record, and is graded by
 * the derived Spotless and Checkstyle file sets.
 *
 * <p>This package-info is new writing, not copied text; it carries the derivation record below
 * because the rule that enforces the record admits no exception for a file name. Read the record as
 * a statement about the material this package holds:
 *
 * <p>Derived from Noble-Lab/CasanovoGUI src/main/java/org/casanovo/gui/core/PdvController.java at
 * commit 480b3013e7f8fb51a2b8c58681043821e3e7f865, GPL-3.0, modified.
 *
 * <p>The package holds two classes, each of whose own documentation names its upstream file and
 * says what was kept and what was changed:
 *
 * <ul>
 *   <li>{@link org.cometgui.tools.pdv.derived.PdvController}, from {@code PdvController.java}: one
 *       PDV {@code denovo-gui} window per mzTab, on an ephemeral loopback port, {@code /ready}
 *       polled, {@code /select} debounced, shut down;
 *   <li>{@link org.cometgui.tools.pdv.derived.DenovoGuiCommand}, from {@code PdvLauncher.java}'s
 *       {@code launchDenovo} argument array only. Nothing of upstream's download, unpacking,
 *       version or GitHub code is here: PDV is installed only by the Tool Manager (design decision
 *       P11-1).
 * </ul>
 *
 * <p>Tests of this code are not derived material and live outside this path, in {@code
 * org.cometgui.tools.pdv}.
 */
package org.cometgui.tools.pdv.derived;
