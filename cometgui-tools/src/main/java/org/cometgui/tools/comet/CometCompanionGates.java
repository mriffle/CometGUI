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

package org.cometgui.tools.comet;

import java.util.Set;
import org.cometgui.domain.tools.HostOperatingSystem;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.tools.api.CompanionGate;

/**
 * Comet's companion rules, for a caller with no manifest row to build them from.
 *
 * <p>The artefact manifest is the authority: each of the three companions on the {@code comet /
 * windows / x86-64} row carries {@code "gatesCapability": "THERMO_RAW_WINDOWS"}, and a composition
 * that has the row in its hand builds the gate from it. This class exists for the composition that
 * does not -- a registered local Comet binary has no manifest row at all -- and it states the same
 * rule from the same source the manifest states it from: {@code R-TOOL-02}, which names the three
 * libraries, and {@code ToolCapability.THERMO_RAW_WINDOWS}, whose documentation repeats them.
 *
 * <p>Checking these three names against the shipped manifest's own {@code gatesCapability}
 * companions cannot be done from this module: {@code cometgui-tools} cannot read the manifest,
 * whose reader lives in {@code cometgui-install}. That check belongs in the composition's own test,
 * where both are visible, and phase 05's report names it as the place it has to be made.
 */
public final class CometCompanionGates {

    private CometCompanionGates() {}

    /**
     * The Thermo RAW gate: Windows, plus the three libraries beside the executable.
     *
     * @return the gate
     */
    public static CompanionGate thermoRawWindows() {
        return new CompanionGate(
                ToolCapability.THERMO_RAW_WINDOWS,
                HostOperatingSystem.WINDOWS,
                Set.of(
                        "CometWrapper.dll",
                        "ThermoFisher.CommonCore.Data.dll",
                        "ThermoFisher.CommonCore.RawFileReader.dll"));
    }
}
