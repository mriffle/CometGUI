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

package org.cometgui.params.percolator.schema;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.cometgui.domain.tools.ToolCapability;

/**
 * Which {@link PercolatorSetting}s one Percolator build accepts, and which it does not.
 *
 * <p>The interface shows the first list under Advanced and nothing from the second; the command
 * builder emits options for the first list only. Both lists are in {@link PercolatorSetting}
 * declaration order, so the interface is stable from build to build.
 *
 * @param supported the settings whose capability the build was observed to have
 * @param unsupported the rest
 */
public record SettingsApplicability(
        List<PercolatorSetting> supported, List<PercolatorSetting> unsupported) {

    /**
     * Copies both lists.
     *
     * @throws NullPointerException if either list is {@code null} or holds {@code null}
     */
    public SettingsApplicability {
        supported = List.copyOf(supported);
        unsupported = List.copyOf(unsupported);
    }

    /**
     * Sorts every setting by whether a build with these observed capabilities accepts it.
     *
     * @param observed the build's observed capabilities -- an unobserved claim must not be in it
     * @return the split, covering every setting exactly once
     * @throws NullPointerException if {@code observed} is {@code null}
     */
    public static SettingsApplicability forCapabilities(Set<ToolCapability> observed) {
        Objects.requireNonNull(observed, "observed");
        List<PercolatorSetting> yes = new ArrayList<>();
        List<PercolatorSetting> no = new ArrayList<>();
        for (PercolatorSetting setting : PercolatorSetting.values()) {
            if (setting.isSupportedBy(observed)) {
                yes.add(setting);
            } else {
                no.add(setting);
            }
        }
        return new SettingsApplicability(yes, no);
    }

    /**
     * Whether the build accepts one setting.
     *
     * @param setting the setting
     * @return {@code true} if it is in {@link #supported()}
     * @throws NullPointerException if {@code setting} is {@code null}
     */
    public boolean isSupported(PercolatorSetting setting) {
        return supported.contains(Objects.requireNonNull(setting, "setting"));
    }
}
