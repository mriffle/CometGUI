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
import static org.cometgui.params.percolator.resolution.Offers.inferred;
import static org.cometgui.params.percolator.resolution.Offers.linux3065NotInstalled;
import static org.cometgui.params.percolator.resolution.Offers.linux3071Installed;
import static org.cometgui.params.percolator.resolution.Offers.local;
import static org.cometgui.params.percolator.resolution.Offers.local309;
import static org.cometgui.params.percolator.resolution.Offers.macos3071NotInstalled;
import static org.cometgui.params.percolator.resolution.Offers.macos309NotInstalled;
import static org.cometgui.params.percolator.resolution.Offers.managed;
import static org.cometgui.params.percolator.resolution.Offers.observed;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.domain.tools.ToolInstallState;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolOrigin;
import org.cometgui.params.percolator.resolution.CompatibilityAction.Kind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The action offered for a run that cannot feed Limelight conversion (phase 12, P12-9; {@code
 * R-LL-01}, {@code R-LL-02}), over Tool Manager offers built here. Every expected label and
 * sentence is typed by hand; the version in a label is never the one a literal would give.
 */
class CompatibilityActionTest {

    private static final DownstreamStage LIMELIGHT = DownstreamStage.LIMELIGHT_CONVERSION;

    private static final String WHY =
            "Limelight conversion needs (the Limelight converter reads the Percolator XML that"
                    + " XML_OUTPUT writes)";

    private static final String RERUN_MIDDLE =
            " is the Percolator that a run with Limelight conversion enabled resolves to here, and"
                    + " it has been observed to have XML_OUTPUT. The rerun makes a new run that"
                    + " rescores this run's merged PIN with it; Comet is not run again, and this"
                    + " run is kept as it is.";

    private static final String UNAVAILABLE =
            "Limelight conversion is unavailable: no Percolator that can be used on this computer"
                    + " has been observed to have XML_OUTPUT, and the Limelight converter reads"
                    + " the Percolator XML that XML_OUTPUT writes.";

    private static final String NO_MANAGED_BUILD =
            "No managed Percolator build that can write XML_OUTPUT exists for this platform, so"
                    + " CometGUI cannot install one and offers no Percolator rerun for Limelight.";

    private static final String NOT_OBTAINABLE =
            "No Percolator that can be used on this computer has been observed to have"
                    + " XML_OUTPUT, so CometGUI offers no Percolator rerun for Limelight.";

    private static final String REGISTER = StageRemedy.REGISTER_LOCAL_BINARY.text();

    @Nested
    @DisplayName("an XML-capable build can be obtained here: the compatible rerun, named by it")
    class Rerun {

        @Test
        @DisplayName(
                "the Linux set: 'Rerun Percolator with 3.07.1 for Limelight', installed, the"
                        + " resolver's skip reason quoted")
        void linuxSet() {
            List<ToolOffer> offers =
                    List.of(linux3071Installed(), linux3065NotInstalled(), local309());
            CompatibilityAction action = CompatibilityAction.forStage(offers, LIMELIGHT);

            assertEquals(Kind.RERUN_PERCOLATOR, action.kind());
            assertTrue(action.offersRerun());
            assertFalse(action.installFirst());
            assertSame(LIMELIGHT, action.stage());
            assertEquals(offers.get(0), action.build().orElseThrow());
            assertEquals("Rerun Percolator with 3.07.1 for Limelight", action.label());
            assertEquals(
                    "Percolator 3.07.1"
                            + RERUN_MIDDLE
                            + " Using Percolator 3.07.1 rather than 3.09 (registered local"
                            + " binary) because 3.09 (registered local binary) lacks XML_OUTPUT,"
                            + " which "
                            + WHY
                            + ".",
                    action.explanation());
            assertEquals(List.of(), action.remedies());
            assertEquals(EnumSet.of(LIMELIGHT), action.resolution().enabledStages());
            assertEquals(action.build(), action.resolution().selected());
        }

        @Test
        @DisplayName(
                "a hypothetical XML-capable 3.08.2 resolved: the label names 3.08.2, never a"
                        + " literal 3.07.1, and it is installed first")
        void whateverResolutionReturns() {
            ToolOffer next =
                    managed(
                            "3.08.2",
                            ToolInstallState.NOT_INSTALLED,
                            observed(
                                    List.of(
                                            ToolCapability.XML_OUTPUT,
                                            ToolCapability.XML_DECOY_OUTPUT)),
                            List.of());
            CompatibilityAction action =
                    CompatibilityAction.forStage(
                            List.of(linux3071Installed(), next, local309()), LIMELIGHT);

            assertEquals(Kind.RERUN_PERCOLATOR, action.kind());
            assertEquals(next, action.build().orElseThrow());
            assertEquals("Rerun Percolator with 3.08.2 for Limelight", action.label());
            assertTrue(action.installFirst());
            assertEquals(
                    "Percolator 3.08.2"
                            + RERUN_MIDDLE
                            + " Percolator 3.08.2 is not installed yet: it is installed when the"
                            + " rerun is chosen, and not before."
                            + " Using Percolator 3.08.2 rather than 3.09 (registered local"
                            + " binary) because 3.09 (registered local binary) lacks XML_OUTPUT,"
                            + " which "
                            + WHY
                            + ".",
                    action.explanation());
        }

        @Test
        @DisplayName(
                "an unreleased 3.10 observed with XML wins with no code change (version-blind)")
        void aFutureBuild() {
            ToolOffer future =
                    managed("3.10", ToolInstallState.INSTALLED, observed(ALL_ELEVEN), List.of());
            CompatibilityAction action =
                    CompatibilityAction.forStage(
                            List.of(linux3071Installed(), future, local309()), LIMELIGHT);

            assertEquals("Rerun Percolator with 3.10 for Limelight", action.label());
            assertFalse(action.installFirst());
            assertEquals(
                    "Percolator 3.10"
                            + RERUN_MIDDLE
                            + " Percolator 3.10 is the newest Percolator that can be used on this"
                            + " computer.",
                    action.explanation());
        }

        @Test
        @DisplayName(
                "only a registered local binary can write XML: the rerun names it as one, and"
                        + " nothing is installed")
        void aRegisteredLocalBinary() {
            ToolOffer registered = local("3.07.1", observed(ALL_ELEVEN));
            CompatibilityAction action =
                    CompatibilityAction.forStage(
                            List.of(local309(), registered, macos309NotInstalled()), LIMELIGHT);

            assertEquals(registered, action.build().orElseThrow());
            assertEquals(
                    "Rerun Percolator with 3.07.1 (registered local binary) for Limelight",
                    action.label());
            assertFalse(action.installFirst());
        }
    }

    @Nested
    @DisplayName("no XML-capable build can be obtained here (R-LL-02): registration, and no rerun")
    class NoRerun {

        private void assertRegistration(CompatibilityAction action) {
            assertEquals(Kind.REGISTER_LOCAL_BINARY, action.kind());
            assertFalse(action.offersRerun());
            assertFalse(action.installFirst());
            assertEquals(Optional.empty(), action.build());
            assertEquals("Register a local Percolator binary for Limelight", action.label());
            assertEquals(
                    List.of(
                            StageRemedy.REGISTER_LOCAL_BINARY,
                            StageRemedy.CONVERT_ON_SUPPORTED_PLATFORM),
                    action.remedies());
            assertFalse(action.label().contains("Rerun"), action.label());
        }

        @Test
        @DisplayName(
                "no managed build for this platform claims XML: said plainly, before the"
                        + " resolver's explanation")
        void noManagedBuildForThisPlatform() {
            ToolOffer elsewhere =
                    managed(
                            "3.07.1",
                            ToolInstallState.UNAVAILABLE_ON_THIS_PLATFORM,
                            observed(List.of(ToolCapability.XML_OUTPUT)),
                            List.of());
            ToolOffer nine = managed("3.09", ToolInstallState.INSTALLED, List.of(), List.of());
            CompatibilityAction action =
                    CompatibilityAction.forStage(List.of(nine, elsewhere, local309()), LIMELIGHT);

            assertRegistration(action);
            assertEquals(
                    NO_MANAGED_BUILD
                            + " "
                            + UNAVAILABLE
                            + " Percolator 3.07.1 declares XML_OUTPUT but cannot be used here:"
                            + " upstream publishes no build of it for this platform. "
                            + REGISTER,
                    action.explanation());
            assertEquals("3.09", action.resolution().selected().orElseThrow().version().text());
        }

        @Test
        @DisplayName("no Percolator offered at all: no managed build, said plainly")
        void nothingOffered() {
            CompatibilityAction action = CompatibilityAction.forStage(List.of(), LIMELIGHT);

            assertRegistration(action);
            assertEquals(
                    NO_MANAGED_BUILD + " " + UNAVAILABLE + " " + REGISTER, action.explanation());
        }

        @Test
        @DisplayName(
                "a registered binary that only claims XML is not a managed build: no managed"
                        + " build, said plainly")
        void aLocalClaimIsNotAManagedBuild() {
            ToolOffer claim =
                    local(
                            "3.07.1",
                            inferred(
                                    List.of(
                                            ToolCapability.XML_OUTPUT,
                                            ToolCapability.SEED_OPTION)));
            CompatibilityAction action =
                    CompatibilityAction.forStage(List.of(local309(), claim), LIMELIGHT);

            assertRegistration(action);
            assertTrue(
                    action.explanation().startsWith(NO_MANAGED_BUILD + " " + UNAVAILABLE),
                    action::explanation);
        }

        @Test
        @DisplayName(
                "macOS before 3.07.1 is installed: a managed build claims XML from bytes, so"
                        + " not 'no managed build' -- the resolver names the unobserved claim")
        void anInferredManagedClaim() {
            CompatibilityAction action =
                    CompatibilityAction.forStage(
                            List.of(macos309NotInstalled(), macos3071NotInstalled()), LIMELIGHT);

            assertRegistration(action);
            assertEquals(
                    NOT_OBTAINABLE
                            + " "
                            + UNAVAILABLE
                            + " Percolator 3.07.1 claims XML_OUTPUT from"
                            + " inferred-from-artefact-bytes evidence, which has not been observed"
                            + " by running it; installing 3.07.1 will probe it. "
                            + REGISTER,
                    action.explanation());
        }

        @Test
        @DisplayName(
                "the managed XML-capable build failed to install here: not 'no managed build',"
                        + " the resolver says why it cannot be used")
        void aFailedManagedBuild() {
            ToolOffer failed =
                    managed("3.07.1", ToolInstallState.FAILED, observed(ALL_ELEVEN), List.of());
            CompatibilityAction action =
                    CompatibilityAction.forStage(List.of(failed, local309()), LIMELIGHT);

            assertRegistration(action);
            assertEquals(
                    NOT_OBTAINABLE
                            + " "
                            + UNAVAILABLE
                            + " Percolator 3.07.1 declares XML_OUTPUT but cannot be used here: its"
                            + " install failed. "
                            + REGISTER,
                    action.explanation());
        }
    }

    @Nested
    @DisplayName("the record")
    class Record {

        private final CompatibilityAction rerun =
                CompatibilityAction.forStage(List.of(linux3071Installed()), LIMELIGHT);

        @Test
        @DisplayName("a rerun without a build, or a registration with one, is refused")
        void kindAndBuildAgree() {
            IllegalArgumentException noBuild =
                    assertThrows(
                            IllegalArgumentException.class,
                            () ->
                                    new CompatibilityAction(
                                            LIMELIGHT,
                                            Kind.RERUN_PERCOLATOR,
                                            rerun.resolution(),
                                            Optional.empty(),
                                            "label",
                                            "why",
                                            List.of()));
            assertEquals(
                    "a Percolator rerun names the build it uses, and nothing else does:"
                            + " RERUN_PERCOLATOR with no build",
                    noBuild.getMessage());
            IllegalArgumentException build =
                    assertThrows(
                            IllegalArgumentException.class,
                            () ->
                                    new CompatibilityAction(
                                            LIMELIGHT,
                                            Kind.REGISTER_LOCAL_BINARY,
                                            rerun.resolution(),
                                            rerun.build(),
                                            "label",
                                            "why",
                                            List.of()));
            assertEquals(
                    "a Percolator rerun names the build it uses, and nothing else does:"
                            + " REGISTER_LOCAL_BINARY with 3.07.1",
                    build.getMessage());
        }

        @Test
        @DisplayName("a blank label or explanation is refused")
        void blanks() {
            for (List<String> texts : List.of(List.of(" ", "why"), List.of("label", ""))) {
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                new CompatibilityAction(
                                        LIMELIGHT,
                                        Kind.RERUN_PERCOLATOR,
                                        rerun.resolution(),
                                        rerun.build(),
                                        texts.get(0),
                                        texts.get(1),
                                        List.of()));
            }
        }

        @Test
        @DisplayName("the remedies are copied")
        void remediesCopied() {
            CompatibilityAction none = CompatibilityAction.forStage(List.of(), LIMELIGHT);
            assertThrows(
                    UnsupportedOperationException.class,
                    () -> none.remedies().add(StageRemedy.REGISTER_LOCAL_BINARY));
        }

        @Test
        @DisplayName("a build a run recorded is named as resolution names one; nulls are refused")
        void buildName() {
            assertEquals(
                    "3.09 (registered local binary)",
                    CompatibilityAction.buildName("3.09", ToolOrigin.LOCAL));
            assertEquals("3.07.1", CompatibilityAction.buildName("3.07.1", ToolOrigin.MANAGED));
            assertThrows(
                    NullPointerException.class,
                    () -> CompatibilityAction.buildName(null, ToolOrigin.LOCAL));
            assertThrows(
                    NullPointerException.class, () -> CompatibilityAction.buildName("3.09", null));
        }

        @Test
        @DisplayName("a null stage is refused")
        void nullStage() {
            assertThrows(
                    NullPointerException.class,
                    () -> CompatibilityAction.forStage(List.of(), null));
        }
    }
}
