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

import static org.cometgui.params.percolator.resolution.Offers.ALL_ELEVEN;
import static org.cometgui.params.percolator.resolution.Offers.NINE_WITHOUT_XML;
import static org.cometgui.params.percolator.resolution.Offers.linux3065NotInstalled;
import static org.cometgui.params.percolator.resolution.Offers.linux3071Installed;
import static org.cometgui.params.percolator.resolution.Offers.local;
import static org.cometgui.params.percolator.resolution.Offers.local309;
import static org.cometgui.params.percolator.resolution.Offers.macos3065NotInstalled;
import static org.cometgui.params.percolator.resolution.Offers.macos3071NotInstalled;
import static org.cometgui.params.percolator.resolution.Offers.macos309NotInstalled;
import static org.cometgui.params.percolator.resolution.Offers.managed;
import static org.cometgui.params.percolator.resolution.Offers.observed;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import org.cometgui.domain.tools.CapabilityEvidence;
import org.cometgui.domain.tools.DeclaredCapability;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.domain.tools.ToolInstallState;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolOrigin;
import org.cometgui.domain.tools.ToolVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Latest-compatible resolution over offers shaped like the real Tool Manager's. Every expected
 * version, capability and sentence is typed by hand.
 */
class PercolatorResolverTest {

    private static final Set<DownstreamStage> LIMELIGHT =
            EnumSet.of(DownstreamStage.LIMELIGHT_CONVERSION);

    private static final Set<DownstreamStage> NONE = EnumSet.noneOf(DownstreamStage.class);

    private static final String WHY_LIMELIGHT =
            "Limelight conversion needs (the Limelight converter reads the Percolator XML that"
                    + " XML_OUTPUT writes)";

    private static final String UNAVAILABLE_OPENING =
            "Limelight conversion is unavailable: no Percolator that can be used on this computer"
                    + " has been observed to have XML_OUTPUT, and the Limelight converter reads"
                    + " the Percolator XML that XML_OUTPUT writes.";

    private static String versionOf(PercolatorResolution resolution) {
        return resolution.selected().orElseThrow().version().text();
    }

    private static List<String> skippedVersions(PercolatorResolution resolution) {
        List<String> versions = new ArrayList<>();
        for (SkippedVersion skip : resolution.skipped()) {
            versions.add(skip.offer().version().text());
        }
        return versions;
    }

    @Nested
    @DisplayName("the Linux set: 3.09 local, 3.07.1 installed, 3.06.5 installable")
    class LinuxSet {

        private final List<ToolOffer> offers =
                List.of(linux3071Installed(), linux3065NotInstalled(), local309());

        @Test
        @DisplayName("Limelight on: 3.07.1, naming 3.09 and XML_OUTPUT as the reason")
        void limelightOn() {
            PercolatorResolution resolution = PercolatorResolver.resolve(offers, LIMELIGHT);

            assertEquals("3.07.1", versionOf(resolution));
            assertEquals(ToolOrigin.MANAGED, resolution.selected().orElseThrow().origin());
            assertEquals(List.of("3.09"), skippedVersions(resolution));
            SkippedVersion skip = resolution.skipped().get(0);
            assertEquals(ToolOrigin.LOCAL, skip.offer().origin());
            assertEquals(
                    List.of(
                            new MissingCapability(
                                    ToolCapability.XML_OUTPUT,
                                    DownstreamStage.LIMELIGHT_CONVERSION,
                                    Optional.empty())),
                    skip.missing());
            String reason =
                    "Using Percolator 3.07.1 rather than 3.09 (registered local binary) because"
                            + " 3.09 (registered local binary) lacks XML_OUTPUT, which "
                            + WHY_LIMELIGHT
                            + ".";
            assertEquals(reason, skip.reason());
            assertEquals(reason, resolution.selectionReason());
            assertTrue(resolution.isAvailable(DownstreamStage.LIMELIGHT_CONVERSION));
            assertEquals(
                    new StageAvailability(
                            DownstreamStage.LIMELIGHT_CONVERSION,
                            true,
                            Optional.empty(),
                            List.of()),
                    resolution.availability(DownstreamStage.LIMELIGHT_CONVERSION));
            assertEquals(Set.of(DownstreamStage.LIMELIGHT_CONVERSION), resolution.enabledStages());
            assertEquals(List.of(), resolution.excluded());
            assertEquals(
                    List.of(
                            "percolator.3-07-1-predates-i-spline-pep-regressor",
                            "percolator.3-07-1-predates-pep-above-one-fix"),
                    resolution.advisories().stream().map(a -> a.id()).toList());
            assertEquals(EnumSet.copyOf(ALL_ELEVEN), resolution.selectedCapabilities());
        }

        @Test
        @DisplayName("Limelight off: 3.09, the newest, with nothing skipped")
        void limelightOff() {
            PercolatorResolution resolution = PercolatorResolver.resolve(offers, NONE);

            assertEquals("3.09", versionOf(resolution));
            assertEquals(ToolOrigin.LOCAL, resolution.selected().orElseThrow().origin());
            assertEquals(List.of(), resolution.skipped());
            assertEquals(
                    "Percolator 3.09 (registered local binary) is the newest Percolator that can"
                            + " be used on this computer.",
                    resolution.selectionReason());
            assertTrue(resolution.isAvailable(DownstreamStage.LIMELIGHT_CONVERSION));
            assertEquals(Set.of(), resolution.enabledStages());
            assertEquals(List.of(), resolution.advisories());
            assertEquals(EnumSet.copyOf(NINE_WITHOUT_XML), resolution.selectedCapabilities());
        }

        @Test
        @DisplayName("the input order does not change the answer")
        void orderIndependent() {
            List<ToolOffer> reversed =
                    List.of(local309(), linux3065NotInstalled(), linux3071Installed());
            assertEquals("3.07.1", versionOf(PercolatorResolver.resolve(reversed, LIMELIGHT)));
            assertEquals("3.09", versionOf(PercolatorResolver.resolve(reversed, NONE)));
        }

        @Test
        @DisplayName("without 3.07.1, Limelight on: 3.06.5, skipping 3.09")
        void olderXmlBuild() {
            PercolatorResolution resolution =
                    PercolatorResolver.resolve(
                            List.of(local309(), linux3065NotInstalled()), LIMELIGHT);
            assertEquals("3.06.5", versionOf(resolution));
            assertEquals(List.of("3.09"), skippedVersions(resolution));
            assertEquals(
                    "Using Percolator 3.06.5 rather than 3.09 (registered local binary) because"
                            + " 3.09 (registered local binary) lacks XML_OUTPUT, which "
                            + WHY_LIMELIGHT
                            + ".",
                    resolution.selectionReason());
        }
    }

    @Nested
    @DisplayName("no observed XML-capable build")
    class NoXml {

        @Test
        @DisplayName("only 3.09: it is the default and Limelight is unavailable with both remedies")
        void only309() {
            PercolatorResolution resolution =
                    PercolatorResolver.resolve(List.of(local309()), LIMELIGHT);

            assertEquals("3.09", versionOf(resolution));
            assertEquals(List.of(), resolution.skipped());
            assertEquals(
                    "Percolator 3.09 (registered local binary) is the newest Percolator that can"
                            + " be used on this computer. Limelight conversion is switched on but"
                            + " unavailable, because no Percolator here has been observed to have"
                            + " XML_OUTPUT.",
                    resolution.selectionReason());
            StageAvailability limelight =
                    resolution.availability(DownstreamStage.LIMELIGHT_CONVERSION);
            assertFalse(limelight.available());
            assertFalse(resolution.isAvailable(DownstreamStage.LIMELIGHT_CONVERSION));
            assertEquals(Optional.of(UNAVAILABLE_OPENING), limelight.explanation());
            assertEquals(
                    List.of(
                            StageRemedy.REGISTER_LOCAL_BINARY,
                            StageRemedy.CONVERT_ON_SUPPORTED_PLATFORM),
                    limelight.remedies());
        }

        @Test
        @DisplayName("Limelight is reported unavailable even while it is switched off")
        void unavailableWhileOff() {
            PercolatorResolution resolution = PercolatorResolver.resolve(List.of(local309()), NONE);
            assertEquals("3.09", versionOf(resolution));
            assertEquals(
                    "Percolator 3.09 (registered local binary) is the newest Percolator that can"
                            + " be used on this computer.",
                    resolution.selectionReason());
            assertFalse(resolution.isAvailable(DownstreamStage.LIMELIGHT_CONVERSION));
            assertEquals(
                    Optional.of(UNAVAILABLE_OPENING),
                    resolution.availability(DownstreamStage.LIMELIGHT_CONVERSION).explanation());
        }

        @Test
        @DisplayName("macOS, XML only inferred: not counted, said to be unobserved, install probes")
        void macosInferred() {
            PercolatorResolution resolution =
                    PercolatorResolver.resolve(
                            List.of(
                                    macos3071NotInstalled(),
                                    macos3065NotInstalled(),
                                    macos309NotInstalled()),
                            LIMELIGHT);

            assertEquals("3.09", versionOf(resolution));
            assertEquals(List.of(), resolution.skipped());
            StageAvailability limelight =
                    resolution.availability(DownstreamStage.LIMELIGHT_CONVERSION);
            assertFalse(limelight.available());
            assertEquals(
                    Optional.of(
                            UNAVAILABLE_OPENING
                                    + " Percolator 3.07.1 claims XML_OUTPUT from"
                                    + " inferred-from-artefact-bytes evidence, which has not been"
                                    + " observed by running it; installing 3.07.1 will probe it."
                                    + " Percolator 3.06.5 claims XML_OUTPUT from"
                                    + " inferred-from-artefact-bytes evidence, which has not been"
                                    + " observed by running it; installing 3.06.5 will probe"
                                    + " it."),
                    limelight.explanation());
            assertEquals(
                    List.of(
                            StageRemedy.REGISTER_LOCAL_BINARY,
                            StageRemedy.CONVERT_ON_SUPPORTED_PLATFORM),
                    limelight.remedies());
        }

        @Test
        @DisplayName("an observed XML build that cannot run here is named with the reason")
        void excludedXmlBuildNamed() {
            ToolOffer beyondHost =
                    managed(
                            "3.07.1",
                            ToolInstallState.HOST_REQUIREMENTS_NOT_MET,
                            observed(
                                    List.of(
                                            ToolCapability.XML_OUTPUT,
                                            ToolCapability.XML_DECOY_OUTPUT)),
                            List.of());
            ToolOffer failed =
                    managed(
                            "3.06.5",
                            ToolInstallState.FAILED,
                            observed(List.of(ToolCapability.XML_OUTPUT)),
                            List.of());
            PercolatorResolution resolution =
                    PercolatorResolver.resolve(List.of(beyondHost, local309(), failed), LIMELIGHT);

            assertEquals("3.09", versionOf(resolution));
            assertEquals(List.of(beyondHost, failed), resolution.excluded());
            assertEquals(
                    Optional.of(
                            UNAVAILABLE_OPENING
                                    + " Percolator 3.07.1 declares XML_OUTPUT but cannot be used"
                                    + " here: this computer does not meet its host requirements."
                                    + " Percolator 3.06.5 declares XML_OUTPUT but cannot be used"
                                    + " here: its install failed."),
                    resolution.availability(DownstreamStage.LIMELIGHT_CONVERSION).explanation());
        }

        @Test
        @DisplayName("an installed build whose claim is unverified is not told to install")
        void installedUnverifiedClaim() {
            List<DeclaredCapability> claims = new ArrayList<>(observed(NINE_WITHOUT_XML));
            claims.add(
                    new DeclaredCapability(
                            ToolCapability.XML_OUTPUT,
                            CapabilityEvidence.UNVERIFIED,
                            "registered, not probed"));
            ToolOffer localClaiming = local("3.10", claims);
            PercolatorResolution resolution =
                    PercolatorResolver.resolve(List.of(localClaiming), LIMELIGHT);
            assertEquals(
                    Optional.of(
                            UNAVAILABLE_OPENING
                                    + " Percolator 3.10 (registered local binary) claims"
                                    + " XML_OUTPUT from unverified evidence, which has not been"
                                    + " observed by running it."),
                    resolution.availability(DownstreamStage.LIMELIGHT_CONVERSION).explanation());

            PercolatorResolution withXml =
                    PercolatorResolver.resolve(
                            List.of(localClaiming, linux3071Installed()), LIMELIGHT);
            assertEquals("3.07.1", versionOf(withXml));
            assertEquals(
                    "Using Percolator 3.07.1 rather than 3.10 (registered local binary) because"
                            + " the XML_OUTPUT that 3.10 (registered local binary) claims, which "
                            + WHY_LIMELIGHT
                            + ", rests on unverified evidence and has not been observed by"
                            + " running it.",
                    withXml.selectionReason());
            assertEquals(
                    List.of(
                            new MissingCapability(
                                    ToolCapability.XML_OUTPUT,
                                    DownstreamStage.LIMELIGHT_CONVERSION,
                                    Optional.of(CapabilityEvidence.UNVERIFIED))),
                    withXml.skipped().get(0).missing());
        }
    }

    @Nested
    @DisplayName("a future release, with no code change")
    class FutureRelease {

        @Test
        @DisplayName("3.10 with observed XML_OUTPUT wins with Limelight on")
        void futureWithXmlWins() {
            ToolOffer future =
                    managed(
                            "3.10",
                            ToolInstallState.NOT_INSTALLED,
                            observed(
                                    List.of(
                                            ToolCapability.XML_OUTPUT,
                                            ToolCapability.XML_DECOY_OUTPUT)),
                            List.of());
            PercolatorResolution resolution =
                    PercolatorResolver.resolve(
                            List.of(linux3071Installed(), local309(), future), LIMELIGHT);
            assertEquals("3.10", versionOf(resolution));
            assertEquals(List.of(), resolution.skipped());
            assertEquals(
                    "Percolator 3.10 is the newest Percolator that can be used on this computer.",
                    resolution.selectionReason());
        }

        @Test
        @DisplayName("3.10 without XML is skipped, naming 3.10 and XML_OUTPUT")
        void futureWithoutXmlSkipped() {
            ToolOffer future =
                    managed("3.10", ToolInstallState.NOT_INSTALLED, List.of(), List.of());
            PercolatorResolution resolution =
                    PercolatorResolver.resolve(
                            List.of(linux3071Installed(), local309(), future), LIMELIGHT);
            assertEquals("3.07.1", versionOf(resolution));
            assertEquals(List.of("3.10", "3.09"), skippedVersions(resolution));
            assertEquals(
                    "Using Percolator 3.07.1 rather than 3.10 because 3.10 lacks XML_OUTPUT,"
                            + " which "
                            + WHY_LIMELIGHT
                            + ". Using Percolator 3.07.1 rather than 3.09 (registered local"
                            + " binary) because 3.09 (registered local binary) lacks XML_OUTPUT,"
                            + " which "
                            + WHY_LIMELIGHT
                            + ".",
                    resolution.selectionReason());
            assertEquals("3.10", versionOf(PercolatorResolver.resolve(List.of(future), NONE)));
        }

        @Test
        @DisplayName("3.10 whose XML is only inferred is skipped as unobserved, install to probe")
        void futureInferredSkipped() {
            ToolOffer future =
                    managed(
                            "3.10",
                            ToolInstallState.NOT_INSTALLED,
                            Offers.inferred(List.of(ToolCapability.XML_OUTPUT)),
                            List.of());
            PercolatorResolution resolution =
                    PercolatorResolver.resolve(List.of(future, linux3071Installed()), LIMELIGHT);
            assertEquals("3.07.1", versionOf(resolution));
            assertEquals(
                    "Using Percolator 3.07.1 rather than 3.10 because the XML_OUTPUT that 3.10"
                            + " claims, which "
                            + WHY_LIMELIGHT
                            + ", rests on inferred-from-artefact-bytes evidence and has not been"
                            + " observed by running it; installing 3.10 will probe it.",
                    resolution.selectionReason());
            assertTrue(resolution.skipped().get(0).missing().get(0).isUnobservedClaim());
        }
    }

    @Nested
    @DisplayName("candidates and ties")
    class Candidates {

        @Test
        @DisplayName(
                "FAILED, HOST_REQUIREMENTS_NOT_MET, UNAVAILABLE and uninstalled local excluded")
        void excludedStates() {
            ToolOffer failed =
                    managed(
                            "3.10",
                            ToolInstallState.FAILED,
                            observed(List.of(ToolCapability.XML_OUTPUT)),
                            List.of());
            ToolOffer beyond =
                    managed(
                            "3.09",
                            ToolInstallState.HOST_REQUIREMENTS_NOT_MET,
                            List.of(),
                            List.of());
            ToolOffer unpublished =
                    managed(
                            "3.08",
                            ToolInstallState.UNAVAILABLE_ON_THIS_PLATFORM,
                            List.of(),
                            List.of());
            ToolOffer localNotInstalled =
                    local(
                            "3.07.1",
                            ToolInstallState.NOT_INSTALLED,
                            observed(List.of(ToolCapability.XML_OUTPUT)));
            ToolOffer localInstalling = local("3.07.1", ToolInstallState.INSTALLING, List.of());
            List<ToolOffer> offers =
                    List.of(failed, beyond, unpublished, localNotInstalled, localInstalling);

            PercolatorResolution resolution = PercolatorResolver.resolve(offers, LIMELIGHT);

            assertEquals(Optional.empty(), resolution.selected());
            assertEquals(offers, resolution.excluded());
            assertEquals(List.of(), resolution.skipped());
            assertEquals(List.of(), resolution.advisories());
            assertEquals(Set.of(), resolution.selectedCapabilities());
            assertEquals(
                    "No Percolator can be used on this computer: none is installed, registered or"
                            + " installable here. Install a managed Percolator or register a local"
                            + " binary in the Tool Manager.",
                    resolution.selectionReason());
            assertEquals(
                    Optional.of(
                            UNAVAILABLE_OPENING
                                    + " Percolator 3.10 declares XML_OUTPUT but cannot be used"
                                    + " here: its install failed. Percolator 3.07.1 (registered"
                                    + " local binary) declares XML_OUTPUT but cannot be used here:"
                                    + " it is not installed."),
                    resolution.availability(DownstreamStage.LIMELIGHT_CONVERSION).explanation());
        }

        @Test
        @DisplayName("an UNAVAILABLE row's claims are named as not published here")
        void unpublishedNamed() {
            ToolOffer unpublished =
                    managed(
                            "3.07.1",
                            ToolInstallState.UNAVAILABLE_ON_THIS_PLATFORM,
                            observed(List.of(ToolCapability.XML_OUTPUT)),
                            List.of());
            PercolatorResolution resolution =
                    PercolatorResolver.resolve(List.of(unpublished), NONE);
            assertEquals(
                    Optional.of(
                            UNAVAILABLE_OPENING
                                    + " Percolator 3.07.1 declares XML_OUTPUT but cannot be used"
                                    + " here: upstream publishes no build of it for this"
                                    + " platform."),
                    resolution.availability(DownstreamStage.LIMELIGHT_CONVERSION).explanation());
        }

        @Test
        @DisplayName("no offers at all: nothing selected, said plainly, not thrown")
        void empty() {
            PercolatorResolution resolution = PercolatorResolver.resolve(List.of(), NONE);
            assertEquals(Optional.empty(), resolution.selected());
            assertEquals(List.of(), resolution.excluded());
            assertTrue(resolution.selectionReason().startsWith("No Percolator can be used"));
            assertEquals(
                    Optional.of(UNAVAILABLE_OPENING),
                    resolution.availability(DownstreamStage.LIMELIGHT_CONVERSION).explanation());
        }

        @Test
        @DisplayName("a managed INSTALLING build is a candidate, so the default does not jump")
        void installingIsCandidate() {
            ToolOffer installing =
                    managed(
                            "3.07.1",
                            ToolInstallState.INSTALLING,
                            observed(List.of(ToolCapability.XML_OUTPUT)),
                            List.of());
            PercolatorResolution resolution =
                    PercolatorResolver.resolve(List.of(local309(), installing), LIMELIGHT);
            assertSame(installing, resolution.selected().orElseThrow());
            assertEquals(List.of(), resolution.excluded());
        }

        @Test
        @DisplayName("equal version: managed before local, whatever the input order")
        void managedBeforeLocal() {
            ToolOffer managedOne = linux3071Installed();
            ToolOffer localOne = local("3.07.1", observed(ALL_ELEVEN));
            assertSame(
                    managedOne,
                    PercolatorResolver.resolve(List.of(localOne, managedOne), LIMELIGHT)
                            .selected()
                            .orElseThrow());
            assertSame(
                    managedOne,
                    PercolatorResolver.resolve(List.of(managedOne, localOne), NONE)
                            .selected()
                            .orElseThrow());
        }

        @Test
        @DisplayName("equal version and origin: installed, then installing, then not installed")
        void stateOrder() {
            List<DeclaredCapability> xml = observed(List.of(ToolCapability.XML_OUTPUT));
            ToolOffer notInstalled =
                    managed("3.07.1", ToolInstallState.NOT_INSTALLED, xml, List.of());
            ToolOffer installing = managed("3.07.1", ToolInstallState.INSTALLING, xml, List.of());
            ToolOffer installed = managed("3.07.1", ToolInstallState.INSTALLED, xml, List.of());
            assertSame(
                    installed,
                    PercolatorResolver.resolve(
                                    List.of(notInstalled, installing, installed), LIMELIGHT)
                            .selected()
                            .orElseThrow());
            assertSame(
                    installing,
                    PercolatorResolver.resolve(List.of(notInstalled, installing), LIMELIGHT)
                            .selected()
                            .orElseThrow());
            assertSame(
                    installing,
                    PercolatorResolver.resolve(List.of(installing, notInstalled), LIMELIGHT)
                            .selected()
                            .orElseThrow());
        }

        @Test
        @DisplayName("fully equal rank: the order given decides, deterministically")
        void inputOrderLast() {
            List<DeclaredCapability> xml = observed(List.of(ToolCapability.XML_OUTPUT));
            ToolOffer ubuntu =
                    managed("3.06.5", ToolInstallState.NOT_INSTALLED, xml, List.of(), 1_000L);
            ToolOffer linux =
                    managed("3.06.5", ToolInstallState.NOT_INSTALLED, xml, List.of(), 2_000L);
            assertSame(
                    ubuntu,
                    PercolatorResolver.resolve(List.of(ubuntu, linux), LIMELIGHT)
                            .selected()
                            .orElseThrow());
            assertSame(
                    linux,
                    PercolatorResolver.resolve(List.of(linux, ubuntu), LIMELIGHT)
                            .selected()
                            .orElseThrow());
        }

        @Test
        @DisplayName("an equal-version build passed over is not reported as a newer version")
        void equalVersionNotSkipped() {
            ToolOffer inferredManaged = macos3071NotInstalled();
            ToolOffer localObserved = local("3.07.1", observed(ALL_ELEVEN));
            PercolatorResolution resolution =
                    PercolatorResolver.resolve(List.of(inferredManaged, localObserved), LIMELIGHT);
            assertSame(localObserved, resolution.selected().orElseThrow());
            assertEquals(List.of(), resolution.skipped());
        }

        @Test
        @DisplayName("refuses null and foreign offers, naming the position")
        void refusals() {
            assertThrows(NullPointerException.class, () -> PercolatorResolver.resolve(null, NONE));
            assertThrows(
                    NullPointerException.class,
                    () -> PercolatorResolver.resolve(List.of(local309()), null));
            Set<DownstreamStage> withNull = new HashSet<>();
            withNull.add(null);
            assertThrows(
                    NullPointerException.class,
                    () -> PercolatorResolver.resolve(List.of(), withNull));
            List<ToolOffer> withNullOffer = new ArrayList<>();
            withNullOffer.add(local309());
            withNullOffer.add(null);
            IllegalArgumentException nullOffer =
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> PercolatorResolver.resolve(withNullOffer, NONE));
            assertEquals("offers[1] must not be null", nullOffer.getMessage());
            ToolOffer comet =
                    new ToolOffer(
                            ToolName.COMET,
                            ToolVersion.parse("2026.02.2"),
                            ToolOrigin.LOCAL,
                            ToolInstallState.INSTALLED,
                            List.of(),
                            List.of(),
                            Optional.empty(),
                            Optional.of(Path.of("comet").toAbsolutePath()),
                            OptionalLong.empty());
            IllegalArgumentException foreign =
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> PercolatorResolver.resolve(List.of(local309(), comet), NONE));
            assertEquals("offers[1] is an offer of comet, not of percolator", foreign.getMessage());
        }
    }

    @Nested
    @DisplayName("observed capabilities")
    class Observed {

        @Test
        @DisplayName("only observed-by-execution claims count")
        void onlyObserved() {
            List<DeclaredCapability> mixed = new ArrayList<>();
            mixed.add(
                    new DeclaredCapability(
                            ToolCapability.XML_OUTPUT,
                            CapabilityEvidence.OBSERVED_BY_EXECUTION,
                            "ran"));
            mixed.add(
                    new DeclaredCapability(
                            ToolCapability.XML_DECOY_OUTPUT,
                            CapabilityEvidence.INFERRED_FROM_ARTEFACT_BYTES,
                            "bytes"));
            mixed.add(
                    new DeclaredCapability(
                            ToolCapability.SEED_OPTION, CapabilityEvidence.UNVERIFIED, "nothing"));
            ToolOffer offer = managed("3.07.1", ToolInstallState.NOT_INSTALLED, mixed, List.of());
            assertEquals(
                    Set.of(ToolCapability.XML_OUTPUT),
                    PercolatorResolver.observedCapabilities(offer));
            assertThrows(
                    UnsupportedOperationException.class,
                    () ->
                            PercolatorResolver.observedCapabilities(offer)
                                    .add(ToolCapability.SEED_OPTION));
            assertThrows(
                    NullPointerException.class,
                    () -> PercolatorResolver.observedCapabilities(null));
        }
    }
}
