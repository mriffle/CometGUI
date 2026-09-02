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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.cometgui.domain.testing.Nulls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Tests for {@link ToolOffer}.
 *
 * <p>An offer is what the Tool Manager renders, so the assertions here are about what it refuses to
 * represent: a capability belonging to another tool, the same capability answered twice, an
 * advisory identifier a test could not tell apart, and an installed tool the application cannot
 * point at.
 *
 * <p><strong>Every rejection is graded over every install state.</strong> None of these rules
 * depends on the state -- a capability of the wrong tool is wrong whether the tool is installed,
 * failed or unavailable here -- so pinning the state at one constant would leave the state axis
 * untested, which is exactly the shape that let a blank-note rule be switched off for a single enum
 * constant in {@link DeclaredCapability} and still pass 108 tests. The one rule that <em>does</em>
 * depend on the state is the installed-path requirement, and it is graded on both sides of that
 * dependency.
 */
class ToolOfferTest {

    private static final ToolVersion PERCOLATOR_3_07_1 = ToolVersion.parse("3.07.1");

    private static final DeclaredCapability XML_OBSERVED =
            new DeclaredCapability(
                    ToolCapability.XML_OUTPUT,
                    CapabilityEvidence.OBSERVED_BY_EXECUTION,
                    "executed on linux-x86-64 by phase 00 and phase 05");

    private static final DeclaredCapability XML_DECOY_OBSERVED =
            new DeclaredCapability(
                    ToolCapability.XML_DECOY_OUTPUT,
                    CapabilityEvidence.OBSERVED_BY_EXECUTION,
                    "executed on linux-x86-64 by phase 05 with -X -Z");

    private static final ToolAdvisory PEP_REGRESSOR =
            new ToolAdvisory(
                    "percolator.pep-regressor-changed-in-3-08",
                    "3.07.1 predates 3.08's change of default PEP regressor to I-splines.");

    /** An installed path has to be present when the state is INSTALLED, so states differ here. */
    private static Optional<Path> pathFor(ToolInstallState state) {
        return state == ToolInstallState.INSTALLED
                ? Optional.of(Path.of("cometgui-tools", "percolator").toAbsolutePath())
                : Optional.empty();
    }

    private static ToolOffer offer(
            ToolInstallState state,
            List<DeclaredCapability> capabilities,
            List<ToolAdvisory> advisories,
            Optional<Path> installedPath) {
        return new ToolOffer(
                ToolName.PERCOLATOR,
                PERCOLATOR_3_07_1,
                ToolOrigin.MANAGED,
                state,
                capabilities,
                advisories,
                Optional.empty(),
                installedPath);
    }

    @Nested
    @DisplayName("what an offer carries")
    class Contents {

        @Test
        @DisplayName("an installed Percolator keeps everything the Tool Manager renders")
        void anInstalledOfferKeepsItsParts() {
            Path installed =
                    Path.of("cometgui-tools", "percolator", "3.07.1", "percolator")
                            .toAbsolutePath();

            ToolOffer offer =
                    new ToolOffer(
                            ToolName.PERCOLATOR,
                            PERCOLATOR_3_07_1,
                            ToolOrigin.MANAGED,
                            ToolInstallState.INSTALLED,
                            List.of(XML_OBSERVED, XML_DECOY_OBSERVED),
                            List.of(PEP_REGRESSOR),
                            Optional.empty(),
                            Optional.of(installed));

            assertAll(
                    () -> assertEquals(ToolName.PERCOLATOR, offer.tool()),
                    () -> assertEquals("3.07.1", offer.version().text()),
                    () -> assertEquals(ToolOrigin.MANAGED, offer.origin()),
                    () -> assertEquals(ToolInstallState.INSTALLED, offer.state()),
                    () ->
                            assertEquals(
                                    List.of(XML_OBSERVED, XML_DECOY_OBSERVED),
                                    offer.capabilities()),
                    () -> assertEquals(List.of(PEP_REGRESSOR), offer.advisories()),
                    () -> assertEquals(Optional.empty(), offer.loaderDiagnostic()),
                    () -> assertEquals(Optional.of(installed), offer.installedPath()));
        }

        @Test
        @DisplayName("an offer that will not run here carries the diagnostic that says why")
        void anUnrunnableOfferCarriesItsDiagnostic() {
            LoaderDiagnostic diagnostic =
                    new LoaderDiagnostic(
                            ProbeFailureKind.MISSING_SYMBOL_VERSION,
                            "libc.so.6",
                            Optional.of("GLIBC_2.38"),
                            Optional.of("GLIBC_2.36"),
                            List.of("install Percolator 3.07.1, which needs only GLIBC_2.34"));

            ToolOffer offer =
                    new ToolOffer(
                            ToolName.PERCOLATOR,
                            ToolVersion.parse("3.09"),
                            ToolOrigin.MANAGED,
                            ToolInstallState.HOST_REQUIREMENTS_NOT_MET,
                            List.of(),
                            List.of(),
                            Optional.of(diagnostic),
                            Optional.empty());

            assertAll(
                    () -> assertEquals(ToolInstallState.HOST_REQUIREMENTS_NOT_MET, offer.state()),
                    () ->
                            assertEquals(
                                    "This build cannot run on this host: libc.so.6 on this host"
                                            + " does not provide a symbol version this build"
                                            + " needs."
                                            + " Required: GLIBC_2.38."
                                            + " Available on this host: GLIBC_2.36."
                                            + " Alternatives: install Percolator 3.07.1, which"
                                            + " needs only GLIBC_2.34.",
                                    offer.loaderDiagnostic().orElseThrow().message()));
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @EnumSource(ToolOrigin.class)
        @DisplayName("a local binary is an offer like any other, with no artefact behind it")
        void anyOriginIsAnOffer(ToolOrigin origin) {
            ToolOffer offer =
                    new ToolOffer(
                            ToolName.PERCOLATOR,
                            PERCOLATOR_3_07_1,
                            origin,
                            ToolInstallState.INSTALLED,
                            List.of(),
                            List.of(),
                            Optional.empty(),
                            Optional.of(Path.of("local-bin", "percolator").toAbsolutePath()));

            assertAll(
                    () -> assertEquals(origin, offer.origin()),
                    () -> assertEquals(List.of(), offer.capabilities()));
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @EnumSource(
                value = ToolInstallState.class,
                names = {
                    "NOT_INSTALLED",
                    "INSTALLING",
                    "FAILED",
                    "UNAVAILABLE_ON_THIS_PLATFORM",
                    "HOST_REQUIREMENTS_NOT_MET"
                })
        @DisplayName("a state other than INSTALLED needs no installed path")
        void otherStatesNeedNoPath(ToolInstallState state) {
            assertEquals(
                    Optional.empty(),
                    offer(state, List.of(), List.of(), Optional.empty()).installedPath());
        }
    }

    @Nested
    @DisplayName("what an offer refuses to represent")
    class Rejections {

        @ParameterizedTest(name = "[{index}] state={0}")
        @EnumSource(ToolInstallState.class)
        @DisplayName("a capability of another tool is rejected in every state, for every mismatch")
        void aCapabilityOfAnotherToolIsRejected(ToolInstallState state) {
            List<Executable> assertions = new ArrayList<>();
            for (ToolCapability capability : ToolCapability.values()) {
                for (ToolName tool : ToolName.values()) {
                    if (tool == capability.tool()) {
                        continue;
                    }
                    DeclaredCapability declared =
                            new DeclaredCapability(
                                    capability,
                                    CapabilityEvidence.UNVERIFIED,
                                    "declared for the wrong tool on purpose");
                    assertions.add(
                            () ->
                                    assertEquals(
                                            capability.id()
                                                    + " is a capability of "
                                                    + capability.tool().id()
                                                    + " and cannot be declared for "
                                                    + tool.id(),
                                            assertThrows(
                                                            IllegalArgumentException.class,
                                                            () ->
                                                                    new ToolOffer(
                                                                            tool,
                                                                            PERCOLATOR_3_07_1,
                                                                            ToolOrigin.MANAGED,
                                                                            state,
                                                                            List.of(declared),
                                                                            List.of(),
                                                                            Optional.empty(),
                                                                            pathFor(state)))
                                                    .getMessage(),
                                            capability.id() + " on " + tool.id() + " in " + state));
                }
            }

            assertAll(assertions);
        }

        @ParameterizedTest(name = "[{index}] state={0}")
        @EnumSource(ToolInstallState.class)
        @DisplayName("the same capability declared twice is rejected in every state, naming it")
        void aDuplicateCapabilityIsRejected(ToolInstallState state) {
            DeclaredCapability xmlInferred =
                    new DeclaredCapability(
                            ToolCapability.XML_OUTPUT,
                            CapabilityEvidence.INFERRED_FROM_ARTEFACT_BYTES,
                            "writer literal found in the macOS portable zip");
            DeclaredCapability pinObserved =
                    new DeclaredCapability(
                            ToolCapability.PIN_OUTPUT,
                            CapabilityEvidence.OBSERVED_BY_EXECUTION,
                            "executed on linux-x86-64 by phase 00");
            DeclaredCapability pinUnverified =
                    new DeclaredCapability(
                            ToolCapability.PIN_OUTPUT,
                            CapabilityEvidence.UNVERIFIED,
                            "not probed on this platform");

            assertAll(
                    () ->
                            assertEquals(
                                    "capabilities names XML_OUTPUT more than once, so the offer"
                                            + " claims two answers for one question",
                                    assertThrows(
                                                    IllegalArgumentException.class,
                                                    () ->
                                                            offer(
                                                                    state,
                                                                    List.of(
                                                                            XML_OBSERVED,
                                                                            xmlInferred),
                                                                    List.of(),
                                                                    pathFor(state)))
                                            .getMessage(),
                                    "percolator in " + state),
                    () ->
                            assertEquals(
                                    "capabilities names PIN_OUTPUT more than once, so the offer"
                                            + " claims two answers for one question",
                                    assertThrows(
                                                    IllegalArgumentException.class,
                                                    () ->
                                                            new ToolOffer(
                                                                    ToolName.COMET,
                                                                    ToolVersion.parse("2026.02.2"),
                                                                    ToolOrigin.MANAGED,
                                                                    state,
                                                                    List.of(
                                                                            pinObserved,
                                                                            pinUnverified),
                                                                    List.of(),
                                                                    Optional.empty(),
                                                                    pathFor(state)))
                                            .getMessage(),
                                    "comet in " + state));
        }

        @ParameterizedTest(name = "[{index}] state={0}")
        @EnumSource(ToolInstallState.class)
        @DisplayName("an advisory identifier used twice is rejected in every state, quoting it")
        void aDuplicateAdvisoryIdIsRejected(ToolInstallState state) {
            ToolAdvisory sameIdOtherText =
                    new ToolAdvisory(
                            "percolator.pep-regressor-changed-in-3-08", "worded another way");

            IllegalArgumentException rejected =
                    assertThrows(
                            IllegalArgumentException.class,
                            () ->
                                    offer(
                                            state,
                                            List.of(),
                                            List.of(PEP_REGRESSOR, sameIdOtherText),
                                            pathFor(state)));

            assertEquals(
                    "advisories names the id \"percolator.pep-regressor-changed-in-3-08\" more"
                            + " than once",
                    rejected.getMessage());
        }

        @Test
        @DisplayName("an installed offer with no path is rejected")
        void anInstalledOfferWithoutAPathIsRejected() {
            IllegalArgumentException rejected =
                    assertThrows(
                            IllegalArgumentException.class,
                            () ->
                                    offer(
                                            ToolInstallState.INSTALLED,
                                            List.of(),
                                            List.of(),
                                            Optional.empty()));

            assertEquals(
                    "installedPath is required when the state is INSTALLED: an installed tool the"
                            + " application cannot point at is not installed",
                    rejected.getMessage());
        }

        @ParameterizedTest(name = "[{index}] state={0}")
        @EnumSource(ToolInstallState.class)
        @DisplayName("a relative installed path is rejected in every state, quoting it")
        void aRelativeInstalledPathIsRejected(ToolInstallState state) {
            /*
             * Graded over every state deliberately. The absoluteness rule applies whenever a path
             * is present and does NOT depend on the state -- only the "a path must be present"
             * half does, and that half is asserted separately above and in otherStatesNeedNoPath.
             */
            IllegalArgumentException rejected =
                    assertThrows(
                            IllegalArgumentException.class,
                            () ->
                                    offer(
                                            state,
                                            List.of(),
                                            List.of(),
                                            Optional.of(Path.of("tools/percolator"))));

            assertEquals(
                    "installedPath must be absolute, but was: tools/percolator",
                    rejected.getMessage());
        }

        @ParameterizedTest(name = "[{index}] state={0}")
        @EnumSource(ToolInstallState.class)
        @DisplayName("a null entry in either list is rejected in every state, naming its position")
        void aNullEntryIsRejected(ToolInstallState state) {
            assertAll(
                    () ->
                            assertEquals(
                                    "capabilities[1] must not be null",
                                    assertThrows(
                                                    IllegalArgumentException.class,
                                                    () ->
                                                            offer(
                                                                    state,
                                                                    Arrays.asList(
                                                                            XML_OBSERVED, null),
                                                                    List.of(),
                                                                    pathFor(state)))
                                            .getMessage(),
                                    state.name()),
                    () ->
                            assertEquals(
                                    "advisories[0] must not be null",
                                    assertThrows(
                                                    IllegalArgumentException.class,
                                                    () ->
                                                            offer(
                                                                    state,
                                                                    List.of(),
                                                                    Arrays.asList(
                                                                            null, PEP_REGRESSOR),
                                                                    pathFor(state)))
                                            .getMessage(),
                                    state.name()));
        }

        @ParameterizedTest(name = "[{index}] state={0}")
        @EnumSource(ToolInstallState.class)
        @DisplayName("a null part is rejected by name in every state")
        void nullPartsAreRejectedByName(ToolInstallState state) {
            List<DeclaredCapability> absentCapabilities = Nulls.of(List.class);
            List<ToolAdvisory> absentAdvisories = Nulls.of(List.class);
            Optional<LoaderDiagnostic> absentDiagnostic = Nulls.of(Optional.class);
            Optional<Path> absentPath = Nulls.of(Optional.class);

            assertAll(
                    () ->
                            assertEquals(
                                    "tool",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () ->
                                                            new ToolOffer(
                                                                    Nulls.of(ToolName.class),
                                                                    PERCOLATOR_3_07_1,
                                                                    ToolOrigin.MANAGED,
                                                                    state,
                                                                    List.of(),
                                                                    List.of(),
                                                                    Optional.empty(),
                                                                    pathFor(state)))
                                            .getMessage(),
                                    state.name()),
                    () ->
                            assertEquals(
                                    "version",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () ->
                                                            new ToolOffer(
                                                                    ToolName.PERCOLATOR,
                                                                    Nulls.of(ToolVersion.class),
                                                                    ToolOrigin.MANAGED,
                                                                    state,
                                                                    List.of(),
                                                                    List.of(),
                                                                    Optional.empty(),
                                                                    pathFor(state)))
                                            .getMessage(),
                                    state.name()),
                    () ->
                            assertEquals(
                                    "origin",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () ->
                                                            new ToolOffer(
                                                                    ToolName.PERCOLATOR,
                                                                    PERCOLATOR_3_07_1,
                                                                    Nulls.of(ToolOrigin.class),
                                                                    state,
                                                                    List.of(),
                                                                    List.of(),
                                                                    Optional.empty(),
                                                                    pathFor(state)))
                                            .getMessage(),
                                    state.name()),
                    () ->
                            assertEquals(
                                    "capabilities",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () ->
                                                            offer(
                                                                    state,
                                                                    absentCapabilities,
                                                                    List.of(),
                                                                    pathFor(state)))
                                            .getMessage(),
                                    state.name()),
                    () ->
                            assertEquals(
                                    "advisories",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () ->
                                                            offer(
                                                                    state,
                                                                    List.of(),
                                                                    absentAdvisories,
                                                                    pathFor(state)))
                                            .getMessage(),
                                    state.name()),
                    () ->
                            assertEquals(
                                    "loaderDiagnostic",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () ->
                                                            new ToolOffer(
                                                                    ToolName.PERCOLATOR,
                                                                    PERCOLATOR_3_07_1,
                                                                    ToolOrigin.MANAGED,
                                                                    state,
                                                                    List.of(),
                                                                    List.of(),
                                                                    absentDiagnostic,
                                                                    pathFor(state)))
                                            .getMessage(),
                                    state.name()),
                    () ->
                            assertEquals(
                                    "installedPath",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () ->
                                                            offer(
                                                                    state,
                                                                    List.of(),
                                                                    List.of(),
                                                                    absentPath))
                                            .getMessage(),
                                    state.name()));
        }

        @Test
        @DisplayName("a null state is rejected by name before the installed path is examined")
        void aNullStateIsRejectedByName() {
            assertEquals(
                    "state",
                    assertThrows(
                                    NullPointerException.class,
                                    () ->
                                            new ToolOffer(
                                                    ToolName.PERCOLATOR,
                                                    PERCOLATOR_3_07_1,
                                                    ToolOrigin.MANAGED,
                                                    Nulls.of(ToolInstallState.class),
                                                    List.of(),
                                                    List.of(),
                                                    Optional.empty(),
                                                    Optional.empty()))
                            .getMessage());
        }
    }

    @Nested
    @DisplayName("value semantics")
    class ValueSemantics {

        @Test
        @DisplayName("both lists are copies that cannot be modified")
        void listsAreCopied() {
            List<DeclaredCapability> capabilities = new ArrayList<>(List.of(XML_OBSERVED));
            List<ToolAdvisory> advisories = new ArrayList<>(List.of(PEP_REGRESSOR));

            ToolOffer built =
                    offer(
                            ToolInstallState.NOT_INSTALLED,
                            capabilities,
                            advisories,
                            Optional.empty());

            capabilities.add(XML_DECOY_OBSERVED);
            advisories.add(new ToolAdvisory("percolator.pep-above-one", "PEP may exceed 1.0."));

            assertAll(
                    () -> assertEquals(List.of(XML_OBSERVED), built.capabilities()),
                    () -> assertEquals(List.of(PEP_REGRESSOR), built.advisories()),
                    () ->
                            assertThrows(
                                    UnsupportedOperationException.class,
                                    () -> built.capabilities().add(XML_DECOY_OBSERVED)),
                    () ->
                            assertThrows(
                                    UnsupportedOperationException.class,
                                    () -> built.advisories().add(PEP_REGRESSOR)));
        }

        @Test
        @DisplayName("two offers built from the same parts are equal")
        void equality() {
            assertEquals(
                    offer(
                            ToolInstallState.NOT_INSTALLED,
                            List.of(XML_OBSERVED),
                            List.of(PEP_REGRESSOR),
                            Optional.empty()),
                    offer(
                            ToolInstallState.NOT_INSTALLED,
                            List.of(XML_OBSERVED),
                            List.of(PEP_REGRESSOR),
                            Optional.empty()));
        }
    }
}
