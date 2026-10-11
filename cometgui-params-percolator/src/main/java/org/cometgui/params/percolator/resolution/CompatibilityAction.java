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

package org.cometgui.params.percolator.resolution;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.cometgui.domain.tools.DeclaredCapability;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.domain.tools.ToolInstallState;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolOrigin;

/**
 * What a scientist is offered when a finished run cannot feed a downstream stage because its
 * Percolator wrote nothing that stage reads -- computed from {@link PercolatorResolver}'s
 * resolution of a run with that stage enabled, over the Percolator builds this computer can obtain
 * now, and never from a platform name or a version number ({@code R-LL-01}, {@code R-LL-02}; phase
 * 12 design decision P12-9).
 *
 * <ul>
 *   <li><strong>{@link Kind#RERUN_PERCOLATOR}</strong> when resolution finds a build observed to
 *       have the stage's capabilities -- an installed or installable managed build, or a registered
 *       local binary. The build is the one resolution selects, and the label names it: "Rerun
 *       Percolator with 3.07.1 for Limelight" today, and whatever version resolution returns
 *       tomorrow. The action itself is Phase 09's compatible rerun.
 *   <li><strong>{@link Kind#REGISTER_LOCAL_BINARY}</strong> when none is: no rerun is offered,
 *       because the interface never offers a rerun with a version it cannot obtain. When no managed
 *       build for this platform even claims the capabilities, the explanation says plainly that no
 *       managed build exists for this platform; when one claims them but cannot be used here or has
 *       not been observed to have them, the resolver's own explanation names it.
 * </ul>
 *
 * <p>Every sentence is built by {@link ResolutionMessages}, beside the resolver's own.
 *
 * @param stage the downstream stage the action is for
 * @param kind what is offered
 * @param resolution the resolution of a run with only {@code stage} enabled, over the offers given
 * @param build the build the rerun uses -- present exactly for {@link Kind#RERUN_PERCOLATOR}
 * @param label the action's label, as a button shows it
 * @param explanation why this is the action, in sentences
 * @param remedies the stage's remedies when no rerun is offered; empty for a rerun
 */
public record CompatibilityAction(
        DownstreamStage stage,
        Kind kind,
        PercolatorResolution resolution,
        Optional<ToolOffer> build,
        String label,
        String explanation,
        List<StageRemedy> remedies) {

    /** What is offered. */
    public enum Kind {

        /** Phase 09's compatible rerun, with the build resolution returns. */
        RERUN_PERCOLATOR,

        /** No build here can serve the stage: register a local binary that can. */
        REGISTER_LOCAL_BINARY
    }

    /**
     * Validates the action.
     *
     * @throws NullPointerException if a component is {@code null}
     * @throws IllegalArgumentException if a rerun names no build or another kind names one, or the
     *     label or explanation is blank
     */
    public CompatibilityAction {
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(resolution, "resolution");
        Objects.requireNonNull(build, "build");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(explanation, "explanation");
        remedies = List.copyOf(remedies);
        if ((kind == Kind.RERUN_PERCOLATOR) != build.isPresent()) {
            throw new IllegalArgumentException(
                    "a Percolator rerun names the build it uses, and nothing else does: "
                            + kind
                            + " with "
                            + build.map(offer -> offer.version().text()).orElse("no build"));
        }
        if (label.isBlank() || explanation.isBlank()) {
            throw new IllegalArgumentException("an action has a label and an explanation");
        }
    }

    /**
     * The action for a downstream stage over the Percolator builds offered here now.
     *
     * @param offers the Tool Manager's Percolator offers, in its order
     * @param stage the stage a finished run cannot feed
     * @return the action, never {@code null}
     * @throws NullPointerException if an argument is {@code null}
     * @throws IllegalArgumentException as {@link PercolatorResolver#resolve}
     */
    public static CompatibilityAction forStage(List<ToolOffer> offers, DownstreamStage stage) {
        Objects.requireNonNull(stage, "stage");
        PercolatorResolution resolution = PercolatorResolver.resolve(offers, EnumSet.of(stage));
        if (resolution.isAvailable(stage)) {
            ToolOffer build = resolution.selected().orElseThrow();
            return new CompatibilityAction(
                    stage,
                    Kind.RERUN_PERCOLATOR,
                    resolution,
                    Optional.of(build),
                    ResolutionMessages.rerunLabel(build, stage),
                    ResolutionMessages.rerun(build, stage, resolution.selectionReason()),
                    List.of());
        }
        StageAvailability availability = resolution.availability(stage);
        String opening =
                managedClaimHere(offers, stage)
                        ? ResolutionMessages.notObtainable(stage)
                        : ResolutionMessages.noManagedBuild(stage);
        return new CompatibilityAction(
                stage,
                Kind.REGISTER_LOCAL_BINARY,
                resolution,
                Optional.empty(),
                ResolutionMessages.registerLabel(stage),
                opening
                        + " "
                        + availability.explanation().orElseThrow()
                        + " "
                        + StageRemedy.REGISTER_LOCAL_BINARY.text(),
                availability.remedies());
    }

    /**
     * How resolution names a build known only by what a run recorded of it: its version as upstream
     * wrote it, and {@code (registered local binary)} after a local one -- the words every
     * resolution message uses.
     *
     * @param version the version text, for example {@code 3.09}
     * @param origin where the build came from
     * @return for example {@code 3.09 (registered local binary)}
     * @throws NullPointerException if an argument is {@code null}
     */
    public static String buildName(String version, ToolOrigin origin) {
        return ResolutionMessages.label(
                Objects.requireNonNull(version, "version"),
                Objects.requireNonNull(origin, "origin"));
    }

    /**
     * Whether a rerun is offered.
     *
     * @return {@code true} for {@link Kind#RERUN_PERCOLATOR}
     */
    public boolean offersRerun() {
        return kind == Kind.RERUN_PERCOLATOR;
    }

    /**
     * Whether the rerun's build must be installed before the rerun can start: it is a managed build
     * resolution chose that is not installed yet.
     *
     * @return {@code true} when a rerun is offered with a build that is not installed
     */
    public boolean installFirst() {
        return build.map(offer -> offer.state() != ToolInstallState.INSTALLED).orElse(false);
    }

    /**
     * Whether a managed build for this platform -- one upstream publishes here, whatever its state
     * -- claims every capability the stage needs, with any evidence.
     */
    private static boolean managedClaimHere(List<ToolOffer> offers, DownstreamStage stage) {
        for (ToolOffer offer : offers) {
            if (offer.origin() == ToolOrigin.MANAGED
                    && offer.state() != ToolInstallState.UNAVAILABLE_ON_THIS_PLATFORM
                    && claimsAll(offer, stage)) {
                return true;
            }
        }
        return false;
    }

    private static boolean claimsAll(ToolOffer offer, DownstreamStage stage) {
        EnumSet<ToolCapability> claimed = EnumSet.noneOf(ToolCapability.class);
        for (DeclaredCapability declared : offer.capabilities()) {
            claimed.add(declared.capability());
        }
        return stage.isSatisfiedBy(claimed);
    }
}
