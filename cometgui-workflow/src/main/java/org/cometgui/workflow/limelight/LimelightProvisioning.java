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

package org.cometgui.workflow.limelight;

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
 * The Limelight converter through the one Tool Manager: which build there is, and installing it on
 * first use ({@code R-LL-01}'s install half, {@code AC-LL-01}).
 *
 * <h2>One installer (design decision P12-1)</h2>
 *
 * <p>Everything here is a question to, or a request of, the {@link ToolManager} port -- the object
 * the Tool Manager section shows, built over {@code manifests/tools.json}. The download, its
 * SHA-256, the atomic install, the identity probe and the completion marker are the installer's,
 * behind that port; nothing here fetches a byte, reads the manifest or chooses an artefact. An
 * install is asynchronous and cancellable through the port's own {@link InstallHandle}, and nothing
 * in a search asks for the converter, so a search never waits for it. This mirrors {@code
 * org.cometgui.workflow.pdv.PdvProvisioning}, PDV's first-use install.
 *
 * <h2>"Installed" means verified</h2>
 *
 * <p>The Tool Manager offers a build as {@link ToolInstallState#INSTALLED} only when its completion
 * marker is present <em>and</em> every installed file still hashes to the SHA-256 recorded when it
 * was installed from the pinned artefact; a JAR changed since then is offered as installable again,
 * never as installed. {@link #status()} therefore never hands out an unverified converter. Its
 * reported version is checked again, by starting it, immediately before every conversion ({@link
 * LimelightConversions}).
 *
 * <h2>Which build</h2>
 *
 * <p>The newest installed converter, if any; otherwise the newest being installed; otherwise the
 * newest managed build this computer can install (one whose last attempt failed included). Anything
 * else is {@link LimelightConverterStatus.State#UNAVAILABLE}, with the reason. {@link #status()}
 * hashes installed files and is never called on the interface thread.
 */
public final class LimelightProvisioning {

    private static final Comparator<ToolOffer> BY_VERSION =
            Comparator.comparing(ToolOffer::version);

    private final ToolManager tools;

    /**
     * Provisioning over the Tool Manager.
     *
     * @param tools the one Tool Manager
     */
    public LimelightProvisioning(ToolManager tools) {
        this.tools = Objects.requireNonNull(tools, "tools");
    }

    /**
     * Where the converter stands now.
     *
     * @return the status, read from the Tool Manager's offers
     */
    public LimelightConverterStatus status() {
        List<ToolOffer> converter =
                tools.offers().stream()
                        .filter(offer -> offer.tool() == ToolName.LIMELIGHT_CONVERTER)
                        .toList();
        Optional<ToolOffer> installed =
                newest(
                        converter,
                        offer ->
                                offer.state() == ToolInstallState.INSTALLED
                                        && offer.installedPath().isPresent());
        if (installed.isPresent()) {
            ToolOffer offer = installed.get();
            return LimelightConverterStatus.installed(
                    offer.version(), offer.installedPath().orElseThrow());
        }
        Optional<ToolOffer> installing =
                newest(converter, offer -> offer.state() == ToolInstallState.INSTALLING);
        if (installing.isPresent()) {
            return LimelightConverterStatus.installing(installing.get().version());
        }
        Optional<ToolOffer> installable =
                newest(
                        converter,
                        offer ->
                                offer.origin() == ToolOrigin.MANAGED
                                        && (offer.state() == ToolInstallState.NOT_INSTALLED
                                                || offer.state() == ToolInstallState.FAILED));
        if (installable.isPresent()) {
            ToolOffer offer = installable.get();
            return LimelightConverterStatus.notInstalled(
                    offer.version(),
                    offer.downloadSizeBytes(),
                    offer.state() == ToolInstallState.FAILED);
        }
        if (converter.isEmpty()) {
            return LimelightConverterStatus.unavailable(
                    "The Tool Manager offers no Limelight converter build, so no run can be"
                            + " converted here.");
        }
        StringBuilder states = new StringBuilder();
        for (ToolOffer offer : converter) {
            states.append(states.length() == 0 ? "" : "; ")
                    .append("the Limelight converter ")
                    .append(offer.version().text())
                    .append(" is ")
                    .append(words(offer.state()));
        }
        return LimelightConverterStatus.unavailable(
                "No Limelight converter build can be installed or used on this computer: "
                        + states
                        + ".");
    }

    /**
     * Starts installing a converter build through the Tool Manager, returning as soon as it is
     * under way.
     *
     * @param version the version to install, as a {@link
     *     LimelightConverterStatus.State#NOT_INSTALLED} status names it
     * @param listener told of every progress report and of how the install ends
     * @return the Tool Manager's handle, whose {@link InstallHandle#cancel()} stops it
     * @throws IllegalArgumentException if the Tool Manager offers no such build
     */
    public InstallHandle install(ToolVersion version, InstallProgressListener listener) {
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(listener, "listener");
        return tools.install(ToolName.LIMELIGHT_CONVERTER, version, listener);
    }

    /**
     * The Percolator builds the Tool Manager offers now, in its order: what the action offered for
     * a run without Percolator XML is resolved over (P12-9), from the same one Tool Manager.
     *
     * @return the Percolator offers, immutable
     */
    public List<ToolOffer> percolatorOffers() {
        return tools.offers().stream()
                .filter(offer -> offer.tool() == ToolName.PERCOLATOR)
                .toList();
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
