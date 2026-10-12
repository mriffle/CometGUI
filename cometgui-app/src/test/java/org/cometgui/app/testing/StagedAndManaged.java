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
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import org.cometgui.domain.tools.InstallHandle;
import org.cometgui.domain.tools.InstallProgressListener;
import org.cometgui.domain.tools.NoManagedBuild;
import org.cometgui.domain.tools.ToolInstallState;
import org.cometgui.domain.tools.ToolManager;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolOrigin;
import org.cometgui.domain.tools.ToolRegistrationException;
import org.cometgui.domain.tools.ToolVersion;

/**
 * A GUI test's Tool Manager: some tools are the product's own managed Tool Manager's -- over an
 * empty cache, fetching real artefacts from a loopback server, installing, verifying and
 * registering exactly as the application does -- and everything else is a staged Tool Manager's, so
 * that a search can run without downloading Comet. It adds nothing of its own, except one switch a
 * test may throw: {@link #hideManagedPercolator(boolean)} offers every managed Percolator build
 * that is not installed as unpublished for this platform -- the offer set of a platform where no
 * XML-capable build can be obtained ({@code R-LL-02}), which this machine is not.
 */
public final class StagedAndManaged implements ToolManager {

    private final ToolManager staged;

    private final ToolManager managed;

    private final Set<ToolName> fromManaged;

    private volatile boolean hidden;

    /**
     * The composition.
     *
     * @param staged answers for every tool not in {@code fromManaged}
     * @param managed the product's managed Tool Manager
     * @param fromManaged the tools the managed one offers, installs and registers
     */
    public StagedAndManaged(ToolManager staged, ToolManager managed, Set<ToolName> fromManaged) {
        this.staged = Objects.requireNonNull(staged, "staged");
        this.managed = Objects.requireNonNull(managed, "managed");
        this.fromManaged = Set.copyOf(fromManaged);
    }

    /**
     * Offers every managed Percolator build that is not installed as unpublished for this platform,
     * or stops doing so.
     *
     * @param hide whether to hide them
     */
    public void hideManagedPercolator(boolean hide) {
        hidden = hide;
    }

    @Override
    public List<ToolOffer> offers() {
        List<ToolOffer> offers = new ArrayList<>();
        for (ToolOffer offer : staged.offers()) {
            if (!fromManaged.contains(offer.tool())) {
                offers.add(offer);
            }
        }
        for (ToolOffer offer : managed.offers()) {
            if (fromManaged.contains(offer.tool())) {
                offers.add(hidden ? hide(offer) : offer);
            }
        }
        return List.copyOf(offers);
    }

    private static ToolOffer hide(ToolOffer offer) {
        if (offer.tool() != ToolName.PERCOLATOR
                || offer.origin() != ToolOrigin.MANAGED
                || offer.state() == ToolInstallState.INSTALLED) {
            return offer;
        }
        return new ToolOffer(
                offer.tool(),
                offer.version(),
                offer.origin(),
                ToolInstallState.UNAVAILABLE_ON_THIS_PLATFORM,
                offer.capabilities(),
                offer.advisories(),
                Optional.empty(),
                Optional.empty(),
                OptionalLong.empty());
    }

    @Override
    public List<NoManagedBuild> noManagedBuild() {
        return List.of();
    }

    @Override
    public InstallHandle install(
            ToolName tool, ToolVersion version, InstallProgressListener listener) {
        if (!fromManaged.contains(tool)) {
            throw new IllegalArgumentException(
                    tool.id() + " is staged in this test and installs nothing");
        }
        return managed.install(tool, version, listener);
    }

    @Override
    public ToolOffer registerLocalBinary(ToolName tool, Path executable)
            throws ToolRegistrationException {
        return fromManaged.contains(tool)
                ? managed.registerLocalBinary(tool, executable)
                : staged.registerLocalBinary(tool, executable);
    }
}
