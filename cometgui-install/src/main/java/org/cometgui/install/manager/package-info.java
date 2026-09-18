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
 * The runtime that stands behind {@link org.cometgui.domain.tools.ToolManager}: the rows a
 * scientist is shown, and the install started on their behalf.
 *
 * <p>Everything else in {@code org.cometgui.install} answers one question well -- which artefacts
 * exist, whether these are the bytes the manifest pinned, what came out of the archive, whether the
 * binary starts. This package answers the only question the user interface is allowed to ask, which
 * is all of them at once, and it is the <strong>only</strong> class here that the Tool Manager sees
 * -- through the domain port, because the architecture rules do not let {@code org.cometgui.ui..}
 * name this package at all.
 *
 * <h2>Which question each rule is keyed on</h2>
 *
 * <p>This package asks "which row is this?" more often than anything else in the phase, and the
 * project's answer is written down once, here, so that it is not rediscovered a fourth time:
 * <strong>the download URL is how this product asks whether two things are the same build.</strong>
 * One release can be two rows -- Comet 2026.02.2 publishes an {@code aarch64} and an {@code x86-64}
 * macOS build, and on Apple silicon {@code D-004} offers both -- and one platform carries several
 * releases, so keying on the version merges two real choices and keying on the platform deletes
 * every alternative there is.
 *
 * <p>The counter-examples are as important, and both appear in this package:
 *
 * <ul>
 *   <li><strong>Which releases exist at all</strong> is a question about releases, not rows, and it
 *       is asked of the whole manifest -- otherwise Percolator 3.09, which publishes nothing for
 *       Linux, would silently vanish from a Linux Tool Manager instead of being shown as
 *       unavailable here.
 *   <li><strong>Which cache entry an install is building</strong> is keyed by tool, version and
 *       platform, because that is what the lock file, the download directory and the tool directory
 *       are keyed by; two rows cannot share one of those, and an install that recorded its progress
 *       under a different key from the one the cache uses would be reporting on a directory nobody
 *       else is looking at.
 * </ul>
 *
 * <h2>What is offered, and what is only shown</h2>
 *
 * <p>{@code R-PERC-01} forbids <em>promising</em> a build that cannot run here; it does not ask for
 * one to be hidden. So a release with no artefact for this platform and a build whose declared
 * floors this host does not meet are both shown, with the state that says so and -- for the second
 * -- the {@code R-PLAT-03} diagnostic naming the required version, this host's version and the
 * alternatives. A build that was <em>run</em> and would not start is different in kind, and {@code
 * R-TOOL-06}'s last sentence is literal about it: it is never offered for selection.
 */
package org.cometgui.install.manager;
