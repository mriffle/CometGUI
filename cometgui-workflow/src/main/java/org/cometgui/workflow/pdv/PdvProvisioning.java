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

package org.cometgui.workflow.pdv;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import org.cometgui.domain.tools.InstallHandle;
import org.cometgui.domain.tools.InstallProgressListener;
import org.cometgui.domain.tools.ToolInstallState;
import org.cometgui.domain.tools.ToolManager;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolOrigin;
import org.cometgui.domain.tools.ToolVersion;

/**
 * PDV through the one Tool Manager: which build there is, and installing it on first use.
 *
 * <h2>One installer (design decision P11-1)</h2>
 *
 * <p>Everything here is a question to, or a request of, the {@link ToolManager} port -- the same
 * object the Tool Manager section shows. The download, its checksum, the extraction and the
 * completion marker are the installer's, behind that port; nothing here fetches a byte or chooses
 * an artefact. An install is asynchronous and cancellable through the port's own {@link
 * InstallHandle}, and nothing in a search asks for PDV, so a search never waits for it ({@code PDV
 * Integration}, <em>Managed installation</em>: "shall not be a prerequisite for a search").
 *
 * <h2>Which build</h2>
 *
 * <p>The newest installed PDV, if any is installed; otherwise the newest one being installed;
 * otherwise the newest managed build this computer can install (one whose last attempt failed
 * included, because trying again is the remedy). Anything else -- no PDV offered at all, or only
 * builds this host cannot run -- is {@link PdvStatus.State#UNAVAILABLE}, with the reason.
 *
 * <p>{@link ToolManager#offers()} verifies installed tools' checksums and can take a moment, so
 * {@link #status()} is never called on the interface thread.
 */
public final class PdvProvisioning {

    private static final Comparator<ToolOffer> BY_VERSION =
            Comparator.comparing(ToolOffer::version);

    private final ToolManager tools;

    /**
     * Provisioning over the Tool Manager.
     *
     * @param tools the one Tool Manager
     */
    public PdvProvisioning(ToolManager tools) {
        this.tools = Objects.requireNonNull(tools, "tools");
    }

    /**
     * Where PDV stands now.
     *
     * @return the status, read from the Tool Manager's offers
     */
    public PdvStatus status() {
        List<ToolOffer> pdv =
                tools.offers().stream().filter(offer -> offer.tool() == ToolName.PDV).toList();
        Optional<ToolOffer> installed =
                newest(
                        pdv,
                        offer ->
                                offer.state() == ToolInstallState.INSTALLED
                                        && offer.installedPath().isPresent());
        if (installed.isPresent()) {
            ToolOffer offer = installed.get();
            return PdvStatus.installed(offer.version(), offer.installedPath().orElseThrow());
        }
        Optional<ToolOffer> installing =
                newest(pdv, offer -> offer.state() == ToolInstallState.INSTALLING);
        if (installing.isPresent()) {
            return PdvStatus.installing(installing.get().version());
        }
        Optional<ToolOffer> installable =
                newest(
                        pdv,
                        offer ->
                                offer.origin() == ToolOrigin.MANAGED
                                        && (offer.state() == ToolInstallState.NOT_INSTALLED
                                                || offer.state() == ToolInstallState.FAILED));
        if (installable.isPresent()) {
            ToolOffer offer = installable.get();
            return PdvStatus.notInstalled(
                    offer.version(),
                    offer.downloadSizeBytes(),
                    offer.state() == ToolInstallState.FAILED);
        }
        if (pdv.isEmpty()) {
            return PdvStatus.unavailable(
                    "The Tool Manager offers no PDV build, so no spectrum can be drawn here.");
        }
        StringBuilder states = new StringBuilder();
        for (ToolOffer offer : pdv) {
            states.append(states.length() == 0 ? "" : "; ")
                    .append("PDV ")
                    .append(offer.version().text())
                    .append(" is ")
                    .append(words(offer.state()));
        }
        return PdvStatus.unavailable(
                "No PDV build can be installed or used on this computer: " + states + ".");
    }

    /**
     * Starts installing a PDV build through the Tool Manager, returning as soon as it is under way.
     *
     * @param version the version to install, as a {@link PdvStatus.State#NOT_INSTALLED} status
     *     names it
     * @param listener told of every progress report and of how the install ends
     * @return the Tool Manager's handle, whose {@link InstallHandle#cancel()} stops it
     * @throws IllegalArgumentException if the Tool Manager offers no such build
     */
    public InstallHandle install(ToolVersion version, InstallProgressListener listener) {
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(listener, "listener");
        return tools.install(ToolName.PDV, version, listener);
    }

    private static Optional<ToolOffer> newest(List<ToolOffer> offers, Predicate<ToolOffer> which) {
        return offers.stream().filter(which).max(BY_VERSION);
    }

    private static String words(ToolInstallState state) {
        return switch (state) {
            case NOT_INSTALLED -> "not installed";
            case INSTALLING -> "being installed";
            case INSTALLED -> "installed";
            case FAILED -> "not installed after an attempt that did not succeed";
            case UNAVAILABLE_ON_THIS_PLATFORM -> "not published for this platform";
            case HOST_REQUIREMENTS_NOT_MET -> "not able to run on this computer";
        };
    }
}
