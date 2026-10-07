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

package org.cometgui.app.testing;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import org.cometgui.domain.tools.InstallHandle;
import org.cometgui.domain.tools.InstallProgressListener;
import org.cometgui.domain.tools.ToolInstallState;
import org.cometgui.domain.tools.ToolManager;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolOrigin;
import org.cometgui.domain.tools.ToolVersion;

/**
 * A Tool Manager whose only offers are Comet builds registered at given paths, as {@link
 * ToolOrigin#LOCAL} and {@link ToolInstallState#INSTALLED} -- what the Tool Manager reports after
 * the scientist registers a Comet already on the computer. It installs nothing; it records which
 * threads asked for the offers.
 */
public final class InstalledComet implements ToolManager {

    private final List<ToolOffer> offers;

    private final List<String> askedOn = Collections.synchronizedList(new ArrayList<>());

    private InstalledComet(List<ToolOffer> offers) {
        this.offers = List.copyOf(offers);
    }

    /**
     * Comet of one release, installed at a path.
     *
     * @param release the release
     * @param executable the executable
     * @return the manager
     */
    public static InstalledComet at(String release, Path executable) {
        return new InstalledComet(
                List.of(
                        new ToolOffer(
                                ToolName.COMET,
                                ToolVersion.parse(release),
                                ToolOrigin.LOCAL,
                                ToolInstallState.INSTALLED,
                                List.of(),
                                List.of(),
                                Optional.empty(),
                                Optional.of(executable),
                                OptionalLong.empty())));
    }

    /**
     * A manager that offers nothing at all.
     *
     * @return the manager
     */
    public static InstalledComet nothing() {
        return new InstalledComet(List.of());
    }

    @Override
    public List<ToolOffer> offers() {
        askedOn.add(Thread.currentThread().getName());
        return offers;
    }

    /**
     * The names of the threads that asked for the offers, oldest first.
     *
     * @return the names
     */
    public List<String> askedOn() {
        synchronized (askedOn) {
            return List.copyOf(askedOn);
        }
    }

    @Override
    public InstallHandle install(
            ToolName tool, ToolVersion version, InstallProgressListener listener) {
        throw new UnsupportedOperationException("this test's Tool Manager installs nothing");
    }

    @Override
    public ToolOffer registerLocalBinary(ToolName tool, Path executable) {
        throw new UnsupportedOperationException("this test's Tool Manager registers nothing");
    }
}
