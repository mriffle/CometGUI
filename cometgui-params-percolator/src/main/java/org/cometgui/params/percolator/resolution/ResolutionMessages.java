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
import java.util.Collection;
import java.util.List;
import org.cometgui.domain.tools.DeclaredCapability;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.domain.tools.ToolInstallState;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolOrigin;

/**
 * Every sentence resolution produces, in one place, so that the interface and the provenance record
 * say the same thing in the same words.
 *
 * <p>Versions are named by {@link org.cometgui.domain.tools.ToolVersion#text()}, exactly as
 * upstream wrote them, and capabilities by their identifier.
 */
final class ResolutionMessages {

    private ResolutionMessages() {}

    /** How an offer is named: its version as upstream wrote it, and whether it is local. */
    static String label(ToolOffer offer) {
        String version = offer.version().text();
        return offer.origin() == ToolOrigin.LOCAL
                ? version + " (registered local binary)"
                : version;
    }

    static String noPercolator() {
        return "No Percolator can be used on this computer: none is installed, registered or"
                + " installable here. Install a managed Percolator or register a local binary in"
                + " the Tool Manager.";
    }

    static String newest(ToolOffer selected, List<StageAvailability> enabledButUnavailable) {
        StringBuilder text =
                new StringBuilder("Percolator ")
                        .append(label(selected))
                        .append(" is the newest Percolator that can be used on this computer.");
        for (StageAvailability stage : enabledButUnavailable) {
            text.append(' ')
                    .append(stage.stage().label())
                    .append(
                            " is switched on but unavailable, because no Percolator here has been"
                                    + " observed to have ")
                    .append(capabilities(stage.stage().requiredCapabilities()))
                    .append('.');
        }
        return text.toString();
    }

    static String skipped(ToolOffer selected, ToolOffer skipped, List<MissingCapability> missing) {
        List<String> clauses = new ArrayList<>();
        String name = label(skipped);
        for (MissingCapability gap : missing) {
            String needs = ", which " + gap.stage().label() + " needs (" + gap.stage().why() + ")";
            if (gap.isUnobservedClaim()) {
                clauses.add(
                        "the "
                                + gap.capability().id()
                                + " that "
                                + name
                                + " claims"
                                + needs
                                + ", rests on "
                                + gap.unobservedClaim().orElseThrow().id()
                                + " evidence and has not been observed by running it"
                                + probeHint(skipped));
            } else {
                clauses.add(name + " lacks " + gap.capability().id() + needs);
            }
        }
        return "Using Percolator "
                + label(selected)
                + " rather than "
                + name
                + " because "
                + String.join(", and ", clauses)
                + ".";
    }

    private static String probeHint(ToolOffer offer) {
        return offer.state() == ToolInstallState.INSTALLED
                ? ""
                : "; installing " + label(offer) + " will probe it";
    }

    static String unavailable(
            DownstreamStage stage, List<ToolOffer> candidates, List<ToolOffer> excluded) {
        StringBuilder text =
                new StringBuilder(stage.label())
                        .append(
                                " is unavailable: no Percolator that can be used on this computer"
                                        + " has been observed to have ")
                        .append(capabilities(stage.requiredCapabilities()))
                        .append(", and ")
                        .append(stage.why())
                        .append('.');
        for (ToolOffer candidate : candidates) {
            for (DeclaredCapability claim : claimsOf(candidate, stage)) {
                text.append(" Percolator ")
                        .append(label(candidate))
                        .append(" claims ")
                        .append(claim.capability().id())
                        .append(" from ")
                        .append(claim.evidence().id())
                        .append(" evidence, which has not been observed by running it")
                        .append(probeHint(candidate))
                        .append('.');
            }
        }
        for (ToolOffer offer : excluded) {
            for (DeclaredCapability claim : claimsOf(offer, stage)) {
                text.append(" Percolator ")
                        .append(label(offer))
                        .append(" declares ")
                        .append(claim.capability().id())
                        .append(" but cannot be used here: ")
                        .append(whyNotUsable(offer.state()))
                        .append('.');
            }
        }
        return text.toString();
    }

    /*
     * A candidate reaches here only when the stage is unavailable, so none of its claims to the
     * stage's capabilities is observed -- every claim listed is an unobserved one. An excluded
     * offer's claims are listed whatever their evidence: it cannot run here either way.
     */
    private static List<DeclaredCapability> claimsOf(ToolOffer offer, DownstreamStage stage) {
        List<DeclaredCapability> claims = new ArrayList<>();
        for (DeclaredCapability declared : offer.capabilities()) {
            if (stage.requiredCapabilities().contains(declared.capability())) {
                claims.add(declared);
            }
        }
        return claims;
    }

    static String whyNotUsable(ToolInstallState state) {
        return switch (state) {
            case HOST_REQUIREMENTS_NOT_MET -> "this computer does not meet its host requirements";
            case FAILED -> "its install failed";
            case UNAVAILABLE_ON_THIS_PLATFORM ->
                    "upstream publishes no build of it for this platform";
            default -> "it is not installed";
        };
    }

    static String changed(ToolOffer from, ToolOffer to, String cause) {
        return "The default Percolator changed from "
                + nameOrNone(from)
                + " to "
                + nameOrNone(to)
                + " because "
                + cause
                + ".";
    }

    static String unchanged(ToolOffer still, String cause) {
        return "The default Percolator is still " + nameOrNone(still) + " (" + cause + ").";
    }

    private static String nameOrNone(ToolOffer offer) {
        return offer == null ? "no Percolator" : label(offer);
    }

    static String switchedOn(DownstreamStage stage) {
        return stage.label()
                + " was switched on and needs "
                + capabilities(stage.requiredCapabilities());
    }

    static String switchedOnButUnavailable(DownstreamStage stage) {
        return stage.label()
                + " was switched on and needs "
                + capabilities(stage.requiredCapabilities())
                + ", but no Percolator here has been observed to have it, so "
                + stage.label()
                + " is unavailable";
    }

    static String switchedOff(DownstreamStage stage) {
        return stage.label()
                + " was switched off, so "
                + capabilities(stage.requiredCapabilities())
                + " is no longer needed";
    }

    static String buildsChanged() {
        return "the Percolator builds available on this computer changed";
    }

    static String capabilities(Collection<ToolCapability> capabilities) {
        List<String> ids = new ArrayList<>();
        for (ToolCapability capability : capabilities) {
            ids.add(capability.id());
        }
        return String.join(" and ", ids);
    }
}
