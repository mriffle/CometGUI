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
 * The fake external tools the process service is tested against, and the harness that compiles and
 * addresses them. Test sources only; nothing here ships.
 *
 * <p>The fake itself is {@code src/test/resources/fakes/FakeTool.java}: one self-contained Java
 * program with a scenario per badly-behaved thing a real scientific tool does -- interleaved
 * output, a non-zero exit, a missing or malformed or half-written output file, a flood, a hang, a
 * child process, a process that ignores a polite terminate, malformed UTF-8, an unterminated last
 * line. {@link org.cometgui.tools.process.fakes.FakeTools} compiles it once per JVM and hands out
 * the argument array or the {@code ToolCommand} that launches it.
 *
 * <p>Nothing in this package launches a process. That is the process service's job, and the tests
 * that exercise a scenario go through the service -- with the single, deliberate exception of
 * {@code FakeToolSelfTest}, which must prove the fakes themselves behave as documented without the
 * code under test standing between the assertion and the process.
 */
package org.cometgui.tools.process.fakes;
