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
 * Argument-array process execution: independent stdout and stderr streaming, timestamped lines,
 * bounded in-memory buffers with logs written to disk as they arrive, cancellation with descendant
 * termination, and explicit working directory and environment. This is the only package permitted
 * to construct a ProcessBuilder; an ArchUnit rule in cometgui-archtests enforces that (R-PROC-02).
 *
 * <p>The core, built by phase 03:
 *
 * <ul>
 *   <li>{@link org.cometgui.tools.process.ProcessService} -- the {@code ProcessRunner}
 *       implementation and the one {@code ProcessBuilder} in the product. It launches from an
 *       argument array, in an explicit working directory, with an environment it constructs rather
 *       than inherits ({@code R-PROC-04}), and closes the tool's standard input at once.
 *   <li>{@link org.cometgui.tools.process.StartedProcess} -- the handle: exit code, duration from
 *       the injected clock ({@code R-PROC-01}), and cancellation that kills descendants before
 *       their parent.
 *   <li>{@code StreamPump} and {@code LineSplitter} -- one daemon thread per stream, delivering
 *       complete lines as they arrive and never accumulating output, with a per-line cap so that a
 *       tool writing hundreds of megabytes without a newline cannot exhaust the heap ({@code
 *       R-PROC-03}).
 *   <li>{@code GuardedListener} and {@code ProcessTree} -- the two pieces of pure logic the rest
 *       depends on: a listener that cannot break a pump by throwing, and the order in which a
 *       process tree must be killed.
 * </ul>
 */
package org.cometgui.tools.process;
