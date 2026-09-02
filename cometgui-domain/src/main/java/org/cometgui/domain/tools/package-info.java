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
 * The vocabulary in which the Tool Manager, the installer, the probes and the provenance recorder
 * all talk about tools: identity, version, platform, artefact kind, capability, install state,
 * probe outcome, progress and advisories, plus the ports the user interface is allowed to see.
 *
 * <p><strong>Why this is in the domain and not in the installer.</strong> The architecture rules
 * restrict {@code org.cometgui.ui..} to {@code java..}, {@code javax..}, {@code javafx..} and the
 * domain, workflow, results, provenance and params packages; neither {@code org.cometgui.install..}
 * nor {@code org.cometgui.tools..} is on that list, and a second rule forbids the user interface
 * from depending on {@code java.net..}, {@code java.security..}, {@code java.util.zip..} or {@code
 * java.util.jar..}. The Tool Manager therefore cannot see the installer at all, and everything it
 * renders has to be expressible in types that live here. This is the same seam {@code
 * org.cometgui.domain.ports.ProcessRunner} and {@code HashService} already use, and it is what lets
 * the Tool Manager be tested with no installer present.
 *
 * <p><strong>Capability is probed, never inferred from a version number.</strong> Percolator 3.09
 * removed XML output; the {@code noxml} and {@code XML_SUPPORT=ON} twins of 3.07.1 print
 * byte-identical help text and both list {@code -X}. So every capability claim in this package
 * carries a {@link org.cometgui.domain.tools.CapabilityEvidence} saying how it was established, and
 * a claim that was not observed by execution may never be described as verified, confirmed, proven
 * or tested.
 *
 * <p><strong>Which types carry a stable identifier.</strong> A value that is written to the
 * artefact manifest, a cache path or a provenance record carries an {@code id()} and a {@code
 * fromId(String)} -- tool name, operating system, architecture, platform, artefact kind,
 * capability, capability evidence. The identifier is a stored field rather than {@code
 * name().toLowerCase()}, so that renaming a Java constant is a visible change to a file format
 * rather than a silent one, and so that no identifier depends on the JVM's default locale. A value
 * that only ever lives in memory -- install state, origin, probe stage, install phase,
 * executability -- carries no identifier, because inventing one would be inventing a file format
 * nobody reads.
 *
 * <p>Nothing here touches the network, the file system beyond {@link java.nio.file.Path}, archives
 * or cryptography: those belong to {@code org.cometgui.install}, which phase 05 units 2 to 8 build
 * behind {@link org.cometgui.domain.tools.ToolManager}.
 *
 * <p>Filled by phase 05 (tool registry and installer) and phase 09 (Percolator versions).
 */
package org.cometgui.domain.tools;
