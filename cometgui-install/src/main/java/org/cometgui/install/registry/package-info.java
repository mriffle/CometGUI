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
 * The versioned, release-bundled managed artefact manifest and the queries over it: which artefact
 * for this tool, version, operating system and architecture.
 *
 * <p><strong>This package is the only place a tool location comes from.</strong> The
 * specification's rule is that tool locations come from a versioned, release-bundled manifest and
 * never from ad hoc URL construction spread through the code, and the way that rule is kept is that
 * there is nowhere else in the product to get one. A download URL, an expected digest, an artefact
 * kind or an install path written in code would be a fact about upstream living outside the file
 * that holds every such fact.
 *
 * <p>The manifest itself is {@code manifests/tools.json} at the repository root, and there is
 * exactly one copy of it: {@code cometgui-install}'s POM ships that file into the jar, so the
 * authoritative copy in the source tree and the shipped copy are the same bytes. {@link
 * org.cometgui.install.registry.ArtefactManifestReader#readFromClasspath()} is how the product
 * reads it.
 *
 * <h2>What the reader refuses, and why it refuses rather than tolerates</h2>
 *
 * <p>{@link org.cometgui.install.registry.ArtefactManifestReader} rejects a missing member, an
 * unknown member, a malformed digest, an unknown enumerated identifier, a capability attached to
 * the wrong tool, a record declaring both extraction modes or neither, and two records describing
 * the same tool, version and platform. Every rejection names the member and the record, because a
 * manifest is a data file and a message that says only "invalid manifest" turns a one-character
 * mistake into a debugging session.
 *
 * <h2>What the manifest is not allowed to claim</h2>
 *
 * <p>Every capability carries the evidence behind it. No Windows or macOS binary has ever been
 * executed anywhere in this project, so no non-Linux row may claim {@code observed-by-execution},
 * and {@code ArtefactManifestContentTest} holds the shipped file to that mechanically. {@code
 * R-TOOL-07} then makes the host probe the final authority; these declarations exist so the Tool
 * Manager can say what is known before the probe has run.
 *
 * <p>An absence is a claim too. Percolator 3.09 has no Linux row, because {@code rel-3-09}
 * publishes no Linux portable archive and its {@code .deb} needs both {@code GLIBC_2.38} and a
 * Boost library it does not ship. {@code R-PERC-12}: absent is honest, a fabricated entry is not.
 *
 * <p>Filled by phase 05 (tool registry and installer).
 */
package org.cometgui.install.registry;
