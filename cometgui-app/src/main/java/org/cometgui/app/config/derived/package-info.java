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
 * Material CometGUI reuses from Noble-Lab/CasanovoGUI, kept in one place so that the reuse is
 * visible in the directory listing rather than only in a header.
 *
 * <p>THE CONVENTION: a file is derived if and only if its path contains a {@code /derived/}
 * segment. That is mechanical on purpose. Every file here carries {@code
 * config/license/java-header-derived.txt} instead of the ordinary CometGUI header, is formatted and
 * header-checked by the Spotless execution {@code spotless-check-derived}, and is graded by {@code
 * config/checkstyle/checkstyle-derived.xml} through the Checkstyle execution {@code
 * checkstyle-check-derived}. The ordinary executions exclude exactly these paths, and {@code
 * scripts/build.sh} proves after every build that the two file sets are disjoint and together cover
 * every {@code .java} file on disk.
 *
 * <p>This package-info is new writing, not copied text; it carries the derivation record below
 * because the required-pattern rule that enforces the record admits no exception for a file name,
 * and this rule set has no suppression filter. Read the record as a statement about the material
 * this package holds:
 *
 * <p>Derived from Noble-Lab/CasanovoGUI src/main/java/org/casanovo/gui/ui/Themes.java at commit
 * 480b3013e7f8fb51a2b8c58681043821e3e7f865, GPL-3.0, modified.
 *
 * <p>Adding a file here means copying upstream material. Adding a file here to escape a rule does
 * not work: the derived rule set is a superset of the ordinary one.
 */
package org.cometgui.app.config.derived;
