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
 * Turning a verified download into a tool directory that is either completely there or not there at
 * all.
 *
 * <p>This package is the specification's <em>Installation shall be atomic</em> sequence and the two
 * rules that make it mean something.
 *
 * <h2>The eight steps</h2>
 *
 * <p>{@link org.cometgui.install.cache.InstallStep} is the specification's list, in order, and
 * {@link org.cometgui.install.cache.InstallPipeline} runs exactly those steps and no others -- its
 * constructor refuses to build a pipeline that has no action for a step, so adding a step to the
 * enumeration without implementing it stops the installer rather than skipping the step quietly.
 *
 * <h2>{@code R-TOOL-04}: installed means marker present <em>and</em> checksums match</h2>
 *
 * <p><em>"A tool directory shall be considered installed only when a completion marker written last
 * is present and its recorded checksums match."</em> Both halves are enforced by {@link
 * org.cometgui.install.cache.ToolCache#verify}: the marker is written last, after the atomic move,
 * so a directory that exists without one is not an install; and a marker whose recorded digest no
 * longer matches the file on disk makes the entry {@link
 * org.cometgui.install.cache.InstallationState#CHECKSUM_MISMATCH} rather than installed, so a
 * corrupted or swapped cache entry is not a valid install either.
 *
 * <p>An install is therefore never repaired in place. An entry that fails verification is discarded
 * whole and rebuilt, because a directory that half matches its marker is a directory nobody can say
 * anything true about.
 *
 * <h2>{@code R-TOOL-05}: two processes, not two threads</h2>
 *
 * <p>{@link org.cometgui.install.cache.InstallLock} takes a {@link java.nio.channels.FileLock},
 * which is held by the <em>JVM</em> rather than by a thread -- so a second lock attempt inside one
 * JVM raises {@link java.nio.channels.OverlappingFileLockException} instead of waiting, which is a
 * different code path from the one a second CometGUI process hits. The lock therefore takes a
 * JVM-wide monitor first and the file lock second: threads serialise on the monitor, processes on
 * the file lock, and both wait rather than fail.
 *
 * <p>Everything is built in a staging directory under {@code cache/staging} and moved into the tool
 * cache in one rename, so a partially written entry is never anywhere a second process looks.
 *
 * <h2>What this package does not do</h2>
 *
 * <p>It does not probe. {@link org.cometgui.install.cache.ToolProbe} is the narrow seam step 6
 * calls and this package implements none of it -- phase 05 unit 6 builds loadability and identity,
 * unit 7 the functional capability probe. Declaring the interface before its implementation is the
 * same shape {@link org.cometgui.domain.ports.Downloader}, {@link
 * org.cometgui.domain.ports.HashService} and {@link org.cometgui.domain.ports.ProcessRunner} were
 * declared in.
 */
package org.cometgui.install.cache;
