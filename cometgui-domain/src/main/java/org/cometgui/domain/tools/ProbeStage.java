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

package org.cometgui.domain.tools;

/**
 * The three ordered stages of a tool probe ({@code R-TOOL-06}).
 *
 * <p>Ordered, and the order is the point. Each stage has a distinct failure state, and a failure at
 * one stage says nothing about the stages after it: a binary that will not load has not been shown
 * to lack a capability, it has been shown not to start. Conflating the two is the specific defect
 * {@code phases/PHASE-05-tool-registry.rst} names -- reporting a missing Visual C++ runtime as "not
 * XML-capable" -- and it is why the stage a failure belongs to is carried on {@link
 * ProbeFailureKind} rather than left to the reader.
 *
 * <p>Declaration order is the probe order, so {@link Enum#compareTo} and {@link Enum#ordinal} both
 * mean "runs before". A stage is only reached when every earlier stage passed.
 */
public enum ProbeStage {

    /**
     * Does the binary start at all? The dynamic loader, the architecture, the executable bit and,
     * on macOS, Gatekeeper. {@code R-TOOL-06} requires a tool that fails this never to be offered
     * for selection.
     */
    LOADABILITY,

    /** What does it say it is? A parsed version string from the binary's own banner. */
    IDENTITY,

    /**
     * What can it actually do? Functional, never textual: the {@code noxml} and {@code
     * XML_SUPPORT=ON} Percolator twins print byte-identical help text, both listing {@code -X}, so
     * a probe that reads help output discriminates nothing ({@code R-PERC-02}).
     */
    CAPABILITY
}
