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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

/**
 * One row of the Tool Manager: a tool build, what is known about it, and what the user may do with
 * it.
 *
 * <p>This is the whole vocabulary the Tool Manager renders, which is why it lives in the domain and
 * not in the installer. The architecture rules restrict {@code org.cometgui.ui..} to the domain and
 * the application APIs and name neither {@code org.cometgui.install..} nor {@code
 * org.cometgui.tools..}, so a view cannot see an installer type at all. Everything a scientist is
 * shown about a tool is expressible here, or it is not shown.
 *
 * <p>{@code R-PERC-01} governs what may be built into one: the application must not present a
 * version and platform combination as a one-click install unless a verified artefact exists and its
 * probe has passed. An offer therefore carries its {@link ToolInstallState} and, when it cannot run
 * here, the {@link LoaderDiagnostic} that says why -- so the Tool Manager can show an unavailable
 * build honestly instead of omitting it and leaving the user to wonder.
 *
 * <p><strong>The download size is here because a scientist is shown it.</strong> PDV is a 103 407
 * 417-byte download and the Tool Manager has to be able to say so before asking anyone to start one
 * -- and, under this record's own rule that everything a scientist is shown about a tool is
 * expressible here, a size that is not a component is a size that is not shown. It is the length
 * the manifest pins for the artefact <em>this host</em> would fetch, so it is absent in exactly the
 * two cases where this host would fetch nothing: a {@link ToolOrigin#LOCAL} binary, which was
 * already on the machine, and a build that is {@link
 * ToolInstallState#UNAVAILABLE_ON_THIS_PLATFORM}, for which no artefact exists here.
 *
 * <p>Capabilities arrive as {@link DeclaredCapability} rather than bare constants, so the interface
 * can distinguish what was observed by execution from what was inferred from artefact bytes.
 * Nothing in this record lets a capability be attached to a tool it does not belong to: the
 * constructor rejects that outright.
 *
 * @param tool which tool
 * @param version which version of it, as upstream names the release
 * @param origin whether CometGUI installed it or the user pointed at it
 * @param state what can be done with it on this machine
 * @param capabilities what it can do, each with the evidence behind the claim; in the order the
 *     manifest or the probe produced them, with no capability named twice
 * @param advisories the caveats to show at selection time and record in provenance ({@code
 *     R-PERC-11}), in the order they should be shown, with no identifier used twice
 * @param loaderDiagnostic why the build will not run here, when it will not; absent otherwise
 * @param installedPath where the executable or JAR is, absolute; required when the state is {@link
 *     ToolInstallState#INSTALLED} and otherwise absent
 * @param downloadSizeBytes the length the manifest pins for this build's artefact on this host --
 *     the artefact itself, not counting any companion download; present exactly when this host has
 *     an artefact to fetch, and positive when it is present
 */
public record ToolOffer(
        ToolName tool,
        ToolVersion version,
        ToolOrigin origin,
        ToolInstallState state,
        List<DeclaredCapability> capabilities,
        List<ToolAdvisory> advisories,
        Optional<LoaderDiagnostic> loaderDiagnostic,
        Optional<Path> installedPath,
        OptionalLong downloadSizeBytes) {

    /**
     * Validates the offer and takes defensive, immutable copies of both lists.
     *
     * @throws NullPointerException if any component is {@code null}
     * @throws IllegalArgumentException if a capability belongs to another tool, if a capability or
     *     an advisory identifier appears twice, if a present installed path is relative, or if an
     *     installed offer names no path -- with a message naming the field and the rejected value
     */
    public ToolOffer {
        Objects.requireNonNull(tool, "tool");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(state, "state");
        capabilities = checkedCapabilities(capabilities, tool);
        advisories = checkedAdvisories(advisories);
        Objects.requireNonNull(loaderDiagnostic, "loaderDiagnostic");
        Objects.requireNonNull(installedPath, "installedPath");
        Objects.requireNonNull(downloadSizeBytes, "downloadSizeBytes");
        checkInstalledPath(state, installedPath);
        checkDownloadSize(origin, state, downloadSizeBytes);
    }

    private static List<DeclaredCapability> checkedCapabilities(
            List<DeclaredCapability> capabilities, ToolName tool) {
        List<DeclaredCapability> copy =
                new ArrayList<>(Objects.requireNonNull(capabilities, "capabilities"));
        Set<ToolCapability> seen = EnumSet.noneOf(ToolCapability.class);
        for (int index = 0; index < copy.size(); index++) {
            DeclaredCapability declared = copy.get(index);
            if (declared == null) {
                throw new IllegalArgumentException("capabilities[" + index + "] must not be null");
            }
            declared.capability().requireBelongsTo(tool);
            if (!seen.add(declared.capability())) {
                throw new IllegalArgumentException(
                        "capabilities names "
                                + declared.capability().id()
                                + " more than once, so the offer claims two answers for one"
                                + " question");
            }
        }
        return List.copyOf(copy);
    }

    private static List<ToolAdvisory> checkedAdvisories(List<ToolAdvisory> advisories) {
        List<ToolAdvisory> copy = new ArrayList<>(Objects.requireNonNull(advisories, "advisories"));
        Set<String> seen = new LinkedHashSet<>();
        for (int index = 0; index < copy.size(); index++) {
            ToolAdvisory advisory = copy.get(index);
            if (advisory == null) {
                throw new IllegalArgumentException("advisories[" + index + "] must not be null");
            }
            if (!seen.add(advisory.id())) {
                throw new IllegalArgumentException(
                        "advisories names the id \"" + advisory.id() + "\" more than once");
            }
        }
        return List.copyOf(copy);
    }

    /*
     * WHETHER THERE IS A DOWNLOAD IS A QUESTION ABOUT THIS HOST, AND IT HAS EXACTLY TWO NEGATIVE
     * ANSWERS.  A LOCAL binary was already on the machine, so CometGUI will fetch nothing for it;
     * a build UNAVAILABLE_ON_THIS_PLATFORM has no artefact here to fetch.  Everywhere else an
     * artefact exists and its length is pinned by the manifest row the offer was built from, so an
     * absent size there is the Tool Manager unable to say how large a download it is about to
     * start -- which is the case this component was added for.
     *
     * The rule is keyed on the origin AND the state because neither alone answers it: a MANAGED
     * build can be one with no artefact here, and a LOCAL binary is in whatever state the
     * registrar put it in.  It is deliberately not keyed on the installed path: an installed
     * managed build was still downloaded, and its size is still what the Tool Manager shows for
     * it.
     */
    private static void checkDownloadSize(
            ToolOrigin origin, ToolInstallState state, OptionalLong downloadSizeBytes) {
        boolean thereIsAnArtefactToFetch =
                origin == ToolOrigin.MANAGED
                        && state != ToolInstallState.UNAVAILABLE_ON_THIS_PLATFORM;
        if (thereIsAnArtefactToFetch && downloadSizeBytes.isEmpty()) {
            throw new IllegalArgumentException(
                    "downloadSizeBytes is required for a "
                            + origin
                            + " offer in state "
                            + state
                            + ": the manifest pins the length of the artefact this host would"
                            + " fetch, and a Tool Manager that cannot say how large a download is"
                            + " cannot honestly ask a user to start one");
        }
        if (!thereIsAnArtefactToFetch && downloadSizeBytes.isPresent()) {
            throw new IllegalArgumentException(
                    "downloadSizeBytes must be absent for a "
                            + origin
                            + " offer in state "
                            + state
                            + ", because this host would fetch nothing for it, but was: "
                            + downloadSizeBytes.getAsLong());
        }
        if (downloadSizeBytes.isPresent() && downloadSizeBytes.getAsLong() <= 0) {
            throw new IllegalArgumentException(
                    "downloadSizeBytes must be positive, but was: "
                            + downloadSizeBytes.getAsLong());
        }
    }

    private static void checkInstalledPath(ToolInstallState state, Optional<Path> installedPath) {
        if (installedPath.isPresent()) {
            Path path = installedPath.get();
            if (!path.isAbsolute()) {
                throw new IllegalArgumentException(
                        "installedPath must be absolute, but was: " + path);
            }
        } else if (state == ToolInstallState.INSTALLED) {
            throw new IllegalArgumentException(
                    "installedPath is required when the state is INSTALLED: an installed tool the"
                            + " application cannot point at is not installed");
        }
    }

    /**
     * What this build can do, each claim with the evidence behind it.
     *
     * <p>Immutable, in the order it was given, and copied for the reason given on {@code
     * org.cometgui.domain.ports.ToolCommand#argv()}, which SpotBugs reports as {@code
     * EI_EXPOSE_REP}.
     *
     * @return the declared capabilities, immutable and possibly empty
     */
    public List<DeclaredCapability> capabilities() {
        return List.copyOf(capabilities);
    }

    /**
     * The caveats to show at selection time and record in provenance.
     *
     * <p>Immutable, in the order they should be shown, and copied for the same reason as {@link
     * #capabilities()}.
     *
     * @return the advisories, immutable and possibly empty
     */
    public List<ToolAdvisory> advisories() {
        return List.copyOf(advisories);
    }
}
