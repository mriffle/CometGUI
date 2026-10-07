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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.cometgui.domain.tools.CapabilityEvidence;
import org.cometgui.domain.tools.DeclaredCapability;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.domain.tools.ToolInstallState;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolOrigin;

/**
 * <em>Latest compatible</em> resolution ({@code R-PERC-02}): which Percolator a run uses by
 * default, computed from the Tool Manager's offers and the enabled downstream stages, never
 * hard-coded.
 *
 * <p><strong>Candidates.</strong> An offer that is {@link ToolInstallState#INSTALLED} (managed or a
 * registered local binary), or a managed one that is {@link ToolInstallState#NOT_INSTALLED} or
 * {@link ToolInstallState#INSTALLING} -- installable here. {@code INSTALLING} counts because it is
 * the same build, with the same manifest claims, as the {@code NOT_INSTALLED} offer it was a moment
 * earlier: excluding it would make the default jump away while the scientist installs the very
 * build it named, and jump back when the install finishes. Offers that are {@link
 * ToolInstallState#UNAVAILABLE_ON_THIS_PLATFORM}, {@link
 * ToolInstallState#HOST_REQUIREMENTS_NOT_MET} or {@link ToolInstallState#FAILED} are never
 * selected; they are kept, and named when they are why a stage is unavailable.
 *
 * <p><strong>Only observed capabilities count</strong> ({@code R-TOOL-08}): a claim whose evidence
 * is not {@link CapabilityEvidence#OBSERVED_BY_EXECUTION} -- a macOS row inferred from bytes, say
 * -- does not satisfy a stage, and the messages say it is unobserved and that installing the build
 * will probe it.
 *
 * <p><strong>The default</strong> is the most preferred candidate that satisfies the most enabled
 * stages; with one stage that is "the newest candidate satisfying every enabled stage, or the
 * newest candidate when none does" -- so with no XML-capable Percolator here, the newest non-XML
 * one is selected and Limelight conversion is reported unavailable, never "no Percolator". The
 * preference order is total: version, newest first; then a managed build before a registered local
 * binary of the same version, because a managed build is attributable to a pinned upstream artefact
 * ({@code R-PERC-01}); then installed before installing before not installed, because an installed
 * build runs without a download; then the order the offers were given in, which the Tool Manager
 * keeps stable.
 *
 * <p><strong>No version number decides anything here.</strong> A version only orders candidates and
 * names them. A future release that writes XML wins with no change to this class, and one that
 * stops writing it is passed over with the reason.
 */
public final class PercolatorResolver {

    private static final Comparator<ToolOffer> PREFERENCE =
            Comparator.comparing(ToolOffer::version)
                    .reversed()
                    .thenComparingInt(PercolatorResolver::originRank)
                    .thenComparingInt(PercolatorResolver::stateRank);

    private PercolatorResolver() {}

    /**
     * Resolves the default Percolator.
     *
     * @param offers the Tool Manager's Percolator offers, in its order
     * @param enabledStages the downstream stages switched on
     * @return the resolution, never {@code null}; with no candidate at all, one that selects
     *     nothing and says why
     * @throws NullPointerException if an argument is {@code null}, or {@code enabledStages} holds
     *     {@code null}
     * @throws IllegalArgumentException if an offer is {@code null} or is not a Percolator offer,
     *     naming its position
     */
    public static PercolatorResolution resolve(
            List<ToolOffer> offers, Set<DownstreamStage> enabledStages) {
        Objects.requireNonNull(offers, "offers");
        EnumSet<DownstreamStage> enabled = EnumSet.noneOf(DownstreamStage.class);
        enabled.addAll(Objects.requireNonNull(enabledStages, "enabledStages"));

        List<ToolOffer> candidates = new ArrayList<>();
        List<ToolOffer> excluded = new ArrayList<>();
        for (int index = 0; index < offers.size(); index++) {
            ToolOffer offer = offers.get(index);
            if (offer == null) {
                throw new IllegalArgumentException("offers[" + index + "] must not be null");
            }
            if (offer.tool() != ToolName.PERCOLATOR) {
                throw new IllegalArgumentException(
                        "offers["
                                + index
                                + "] is an offer of "
                                + offer.tool().id()
                                + ", not of percolator");
            }
            if (isCandidate(offer)) {
                candidates.add(offer);
            } else {
                excluded.add(offer);
            }
        }
        candidates.sort(PREFERENCE);

        List<StageAvailability> stages = new ArrayList<>();
        List<StageAvailability> enabledButUnavailable = new ArrayList<>();
        for (DownstreamStage stage : DownstreamStage.values()) {
            StageAvailability availability = availabilityOf(stage, candidates, excluded);
            stages.add(availability);
            if (enabled.contains(stage) && !availability.available()) {
                enabledButUnavailable.add(availability);
            }
        }

        ToolOffer selected = null;
        int bestScore = -1;
        for (ToolOffer candidate : candidates) {
            int score = satisfiedCount(candidate, enabled);
            if (score > bestScore) {
                bestScore = score;
                selected = candidate;
            }
        }
        if (selected == null) {
            return new PercolatorResolution(
                    enabled,
                    Optional.empty(),
                    ResolutionMessages.noPercolator(),
                    List.of(),
                    stages,
                    excluded,
                    List.of());
        }

        List<SkippedVersion> skipped = new ArrayList<>();
        for (ToolOffer candidate : candidates) {
            if (candidate.version().compareTo(selected.version()) <= 0) {
                break;
            }
            List<MissingCapability> missing = missingFor(candidate, enabled);
            skipped.add(
                    new SkippedVersion(
                            candidate,
                            missing,
                            ResolutionMessages.skipped(selected, candidate, missing)));
        }
        String reason;
        if (skipped.isEmpty()) {
            reason = ResolutionMessages.newest(selected, enabledButUnavailable);
        } else {
            List<String> sentences = new ArrayList<>();
            for (SkippedVersion skip : skipped) {
                sentences.add(skip.reason());
            }
            reason = String.join(" ", sentences);
        }
        return new PercolatorResolution(
                enabled,
                Optional.of(selected),
                reason,
                skipped,
                stages,
                excluded,
                selected.advisories());
    }

    /**
     * The capabilities an offer was observed to have: those of its declared capabilities whose
     * evidence is {@link CapabilityEvidence#OBSERVED_BY_EXECUTION}, and no others.
     *
     * @param offer the offer
     * @return its observed capabilities, immutable
     * @throws NullPointerException if {@code offer} is {@code null}
     */
    public static Set<ToolCapability> observedCapabilities(ToolOffer offer) {
        EnumSet<ToolCapability> observed = EnumSet.noneOf(ToolCapability.class);
        for (DeclaredCapability declared : Objects.requireNonNull(offer, "offer").capabilities()) {
            if (declared.isObserved()) {
                observed.add(declared.capability());
            }
        }
        return Collections.unmodifiableSet(observed);
    }

    private static boolean isCandidate(ToolOffer offer) {
        return switch (offer.state()) {
            case INSTALLED -> true;
            case NOT_INSTALLED, INSTALLING -> offer.origin() == ToolOrigin.MANAGED;
            default -> false;
        };
    }

    private static int originRank(ToolOffer offer) {
        return offer.origin() == ToolOrigin.MANAGED ? 0 : 1;
    }

    private static int stateRank(ToolOffer offer) {
        return switch (offer.state()) {
            case INSTALLED -> 0;
            case INSTALLING -> 1;
            default -> 2;
        };
    }

    private static int satisfiedCount(ToolOffer candidate, Set<DownstreamStage> enabled) {
        Set<ToolCapability> observed = observedCapabilities(candidate);
        int count = 0;
        for (DownstreamStage stage : enabled) {
            if (stage.isSatisfiedBy(observed)) {
                count++;
            }
        }
        return count;
    }

    private static StageAvailability availabilityOf(
            DownstreamStage stage, List<ToolOffer> candidates, List<ToolOffer> excluded) {
        for (ToolOffer candidate : candidates) {
            if (stage.isSatisfiedBy(observedCapabilities(candidate))) {
                return new StageAvailability(stage, true, Optional.empty(), List.of());
            }
        }
        return new StageAvailability(
                stage,
                false,
                Optional.of(ResolutionMessages.unavailable(stage, candidates, excluded)),
                stage.remedies());
    }

    private static List<MissingCapability> missingFor(
            ToolOffer candidate, Set<DownstreamStage> enabled) {
        Set<ToolCapability> observed = observedCapabilities(candidate);
        List<MissingCapability> missing = new ArrayList<>();
        for (DownstreamStage stage : enabled) {
            for (ToolCapability capability : stage.requiredCapabilities()) {
                if (!observed.contains(capability)) {
                    missing.add(
                            new MissingCapability(
                                    capability, stage, claimedEvidence(candidate, capability)));
                }
            }
        }
        return missing;
    }

    private static Optional<CapabilityEvidence> claimedEvidence(
            ToolOffer offer, ToolCapability capability) {
        for (DeclaredCapability declared : offer.capabilities()) {
            if (declared.capability() == capability) {
                return Optional.of(declared.evidence());
            }
        }
        return Optional.empty();
    }
}
