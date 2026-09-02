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
 * Getting files out of an artefact without letting the artefact decide where they land.
 *
 * <h2>The rule this package exists for</h2>
 *
 * <p>{@code R-SEC-05}: archive and package-payload extraction is implemented <strong>once, in one
 * class</strong>, with the traversal, absolute-path, symbolic-link and decompression-bomb checks
 * applied <strong>uniformly to every artefact kind</strong>. That is structural here, not a
 * convention:
 *
 * <ul>
 *   <li>{@link org.cometgui.install.archive.ArtefactExtractor} is the only entry point, and it
 *       chooses the container from the manifest's declared {@link
 *       org.cometgui.domain.tools.ArtefactKind} -- never from a URL suffix ({@code R-TOOL-01}) --
 *       with a switch that has no {@code default}, so adding a kind is a compilation error.
 *   <li>The per-format readers -- zip, tar, cpio, {@code ar}, {@code xar} -- only describe what
 *       they find. Not one of them can create, write, link, copy or delete a file, and {@code
 *       GuardBypassStructureTest} reads the compiled classes to prove it.
 *   <li>{@link org.cometgui.install.archive.ExtractionGuard} is the single place a file is put on
 *       disk, so every check runs for every kind whether or not anyone remembered.
 * </ul>
 *
 * <h2>The two extraction modes, and why both are needed</h2>
 *
 * <p>In <strong>named-member</strong> mode the manifest names the member and the destination, and
 * the archive's own entry name never places a file. In <strong>whole-artefact</strong> mode every
 * entry is unpacked under its own name, which is where the guards do their work in production; PDV
 * is the one artefact in the manifest installed that way.
 *
 * <p>The first mode exists because of a real upstream artefact: {@code
 * rel-3-06-05/percolator-noxml-osx-portable.zip} holds a single member named {@code
 * ../my_build/percolator-noxml/src/percolator}, which a correct traversal guard rejects. Naming the
 * destination in the manifest is <em>stronger</em> than sanitising that name, because no
 * attacker-controlled string reaches the file system at all -- and taking the basename instead
 * would have been the weakening this project forbids. The design must not, however, become the
 * reason the guard is never exercised: the same archive is a test case in both modes, extracted in
 * the first and rejected in the second.
 *
 * <h2>What is deliberately absent</h2>
 *
 * <p>There is no {@code NSIS_PAYLOAD} extractor and there must not be one: {@code D-002} option C,
 * an owner decision, deleted it. {@code DEB_PAYLOAD} and {@code PKG_PAYLOAD} survive only to fetch
 * the two Percolator XSD companion files that no portable archive ships, and no installer is ever
 * executed -- payloads are read.
 *
 * <p>The Java runtime decodes gzip and zlib and nothing else these containers may use, so a payload
 * compressed with xz, zstd or bzip2 is refused by name rather than half-read. This project adds no
 * dependency to gain a codec, and a wrong guess about a container would be worse than a clear
 * refusal.
 */
package org.cometgui.install.archive;
