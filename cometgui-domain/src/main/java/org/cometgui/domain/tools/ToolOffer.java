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
 * <p>Capabilities arrive as {@link DeclaredCapability} rather than bare constants, so the interface
 * can distinguish what was observed by execution from what was inferred from artefact bytes.
 * Nothing in this record lets a capability be attached to a tool it does not belong to: the
 * constructor rejects that outright.
 *
 * <h2>Why the download size is here</h2>
 *
 * <p>Because the sentence at the top of this documentation is meant literally, and until phase 05
 * unit 8 it was not true. PDV's artefact is <strong>103407417 bytes</strong> -- the one transfer
 * {@code phases/PHASE-05-tool-registry.rst} singles out for deliberate cancellation testing -- and
 * an offer that could not state that size left the Tool Manager unable to warn anyone before the
 * transfer started. A user who is told the number can decide; one who is not finds out by waiting.
 *
 * <p>It is an {@link OptionalLong} because <strong>absent is a real answer, not a missing
 * one</strong>, and it is absent in exactly two circumstances:
 *
 * <ul>
 *   <li>a <strong>registered local binary</strong> ({@link ToolOrigin#LOCAL}), which was never
 *       downloaded -- the file was already on the machine, and quoting a download size for it would
 *       be inventing one;
 *   <li>a build that is {@link ToolInstallState#UNAVAILABLE_ON_THIS_PLATFORM}, where upstream
 *       publishes no artefact for this host at all, so there is no download to size.
 * </ul>
 *
 * <p>Everywhere else it is <strong>required</strong>, and that is the point of the rule rather than
 * a formality: a managed build this machine could fetch has a size in the manifest, so an offer for
 * one that carried no size would be a Tool Manager row that silently cannot say what it is about to
 * cost. It is the artefact's own download length -- what the manifest pins and what {@code
 * R-SEC-02}'s SHA-256 is taken over -- not the installed size, which for PDV's 222-entry archive is
 * a different and larger number.
 *
 * @param tool which tool
 * @param version which version of it, as upstream names the release
 * @param origin whether CometGUI installed it or the user pointed at it
 * @param state what can be done with it on this machine
 * @param downloadSizeBytes how many bytes fetching this build costs, from the manifest record it
 *     comes from; positive when present, and absent only for a local binary or a build with no
 *     artefact for this platform
 * @param capabilities what it can do, each with the evidence behind the claim; in the order the
 *     manifest or the probe produced them, with no capability named twice
 * @param advisories the caveats to show at selection time and record in provenance ({@code
 *     R-PERC-11}), in the order they should be shown, with no identifier used twice
 * @param loaderDiagnostic why the build will not run here, when it will not; absent otherwise
 * @param installedPath where the executable or JAR is, absolute; required when the state is {@link
 *     ToolInstallState#INSTALLED} and otherwise absent
 */
public record ToolOffer(
        ToolName tool,
        ToolVersion version,
        ToolOrigin origin,
        ToolInstallState state,
        OptionalLong downloadSizeBytes,
        List<DeclaredCapability> capabilities,
        List<ToolAdvisory> advisories,
        Optional<LoaderDiagnostic> loaderDiagnostic,
        Optional<Path> installedPath) {

    /**
     * Validates the offer and takes defensive, immutable copies of both lists.
     *
     * @throws NullPointerException if any component is {@code null}
     * @throws IllegalArgumentException if a capability belongs to another tool, if a capability or
     *     an advisory identifier appears twice, if a present installed path is relative, if an
     *     installed offer names no path, or if the download size is present where there is no
     *     download or absent where there is one -- with a message naming the field and the rejected
     *     value
     */
    public ToolOffer {
        Objects.requireNonNull(tool, "tool");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(downloadSizeBytes, "downloadSizeBytes");
        capabilities = checkedCapabilities(capabilities, tool);
        advisories = checkedAdvisories(advisories);
        Objects.requireNonNull(loaderDiagnostic, "loaderDiagnostic");
        Objects.requireNonNull(installedPath, "installedPath");
        checkInstalledPath(state, installedPath);
        checkDownloadSize(origin, state, downloadSizeBytes);
    }

    /*
     * WHETHER THERE IS A DOWNLOAD AT ALL IS DECIDED BY TWO SEPARATE COMPONENTS, and both are
     * checked, because each on its own would leave the other axis ungraded.  A local binary was
     * never downloaded whatever its state, and a build with no artefact for this platform has no
     * download whatever its origin -- so the rule is stated over the pair rather than over one of
     * them with the other silently along for the ride.
     */
    private static void checkDownloadSize(
            ToolOrigin origin, ToolInstallState state, OptionalLong downloadSizeBytes) {
        if (origin == ToolOrigin.LOCAL) {
            requireNoDownload(
                    downloadSizeBytes,
                    "a local binary was not downloaded, so it has no download size; CometGUI did"
                            + " not fetch it and cannot say what fetching it would have cost");
            return;
        }
        if (state == ToolInstallState.UNAVAILABLE_ON_THIS_PLATFORM) {
            requireNoDownload(
                    downloadSizeBytes,
                    "upstream publishes no artefact of this build for this platform, so there is"
                            + " no download to size");
            return;
        }
        if (downloadSizeBytes.isEmpty()) {
            throw new IllegalArgumentException(
                    "downloadSizeBytes is required for a managed offer in state "
                            + state.name()
                            + ": there is an artefact behind it and the manifest pins its length,"
                            + " so an offer that cannot state it is one the Tool Manager cannot"
                            + " warn anybody about");
        }
        if (downloadSizeBytes.getAsLong() <= 0) {
            throw new IllegalArgumentException(
                    "downloadSizeBytes must be positive when present, but was: "
                            + downloadSizeBytes.getAsLong());
        }
    }

    private static void requireNoDownload(OptionalLong downloadSizeBytes, String why) {
        if (downloadSizeBytes.isPresent()) {
            throw new IllegalArgumentException(
                    "downloadSizeBytes must be absent, but was "
                            + downloadSizeBytes.getAsLong()
                            + ": "
                            + why);
        }
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
