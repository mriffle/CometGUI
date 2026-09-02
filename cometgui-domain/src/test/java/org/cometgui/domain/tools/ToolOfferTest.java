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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Tests for {@link ToolOffer}.
 *
 * <p>An offer is what the Tool Manager renders, so the assertions here are about what it refuses to
 * represent: a capability belonging to another tool, the same capability answered twice, an
 * advisory identifier a test could not tell apart, and an installed tool the application cannot
 * point at.
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

        @Test
        @DisplayName("a local binary is an offer like any other, with no artefact behind it")
        void aLocalBinaryIsAnOffer() {
            ToolOffer offer =
                    new ToolOffer(
                            ToolName.PERCOLATOR,
                            PERCOLATOR_3_07_1,
                            ToolOrigin.LOCAL,
                            ToolInstallState.INSTALLED,
                            List.of(),
                            List.of(),
                            Optional.empty(),
                            Optional.of(Path.of("local-bin", "percolator").toAbsolutePath()));

            assertAll(
                    () -> assertEquals(ToolOrigin.LOCAL, offer.origin()),
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

        @Test
        @DisplayName("a capability belonging to another tool cannot be attached")
        void aCapabilityOfAnotherToolIsRejected() {
            DeclaredCapability thermo =
                    new DeclaredCapability(
                            ToolCapability.THERMO_RAW_WINDOWS,
                            CapabilityEvidence.UNVERIFIED,
                            "inferred from the Windows companion DLL list");

            IllegalArgumentException rejected =
                    assertThrows(
                            IllegalArgumentException.class,
                            () ->
                                    offer(
                                            ToolInstallState.NOT_INSTALLED,
                                            List.of(thermo),
                                            List.of(),
                                            Optional.empty()));

            assertEquals(
                    "THERMO_RAW_WINDOWS is a capability of comet and cannot be declared for"
                            + " percolator",
                    rejected.getMessage());
        }

        @Test
        @DisplayName("the same capability declared twice is rejected, naming it")
        void aDuplicateCapabilityIsRejected() {
            DeclaredCapability xmlInferred =
                    new DeclaredCapability(
                            ToolCapability.XML_OUTPUT,
                            CapabilityEvidence.INFERRED_FROM_ARTEFACT_BYTES,
                            "writer literal found in the macOS portable zip");

            IllegalArgumentException rejected =
                    assertThrows(
                            IllegalArgumentException.class,
                            () ->
                                    offer(
                                            ToolInstallState.NOT_INSTALLED,
                                            List.of(XML_OBSERVED, xmlInferred),
                                            List.of(),
                                            Optional.empty()));

            assertEquals(
                    "capabilities names XML_OUTPUT more than once, so the offer claims two"
                            + " answers for one question",
                    rejected.getMessage());
        }

        @Test
        @DisplayName("an advisory identifier used twice is rejected, quoting it")
        void aDuplicateAdvisoryIdIsRejected() {
            ToolAdvisory sameIdOtherText =
                    new ToolAdvisory(
                            "percolator.pep-regressor-changed-in-3-08", "worded another way");

            IllegalArgumentException rejected =
                    assertThrows(
                            IllegalArgumentException.class,
                            () ->
                                    offer(
                                            ToolInstallState.NOT_INSTALLED,
                                            List.of(),
                                            List.of(PEP_REGRESSOR, sameIdOtherText),
                                            Optional.empty()));

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

        @Test
        @DisplayName("a relative installed path is rejected, quoting it")
        void aRelativeInstalledPathIsRejected() {
            IllegalArgumentException rejected =
                    assertThrows(
                            IllegalArgumentException.class,
                            () ->
                                    offer(
                                            ToolInstallState.INSTALLED,
                                            List.of(),
                                            List.of(),
                                            Optional.of(Path.of("tools/percolator"))));

            assertEquals(
                    "installedPath must be absolute, but was: tools/percolator",
                    rejected.getMessage());
        }

        @Test
        @DisplayName("a null entry in either list is rejected, naming its position")
        void aNullEntryIsRejected() {
            assertAll(
                    () ->
                            assertEquals(
                                    "capabilities[1] must not be null",
                                    assertThrows(
                                                    IllegalArgumentException.class,
                                                    () ->
                                                            offer(
                                                                    ToolInstallState.NOT_INSTALLED,
                                                                    Arrays.asList(
                                                                            XML_OBSERVED, null),
                                                                    List.of(),
                                                                    Optional.empty()))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "advisories[0] must not be null",
                                    assertThrows(
                                                    IllegalArgumentException.class,
                                                    () ->
                                                            offer(
                                                                    ToolInstallState.NOT_INSTALLED,
                                                                    List.of(),
                                                                    Arrays.asList(
                                                                            null, PEP_REGRESSOR),
                                                                    Optional.empty()))
                                            .getMessage()));
        }

        @Test
        @DisplayName("a null part is rejected by name")
        void nullPartsAreRejectedByName() {
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
                                                                    ToolInstallState.NOT_INSTALLED,
                                                                    List.of(),
                                                                    List.of(),
                                                                    Optional.empty(),
                                                                    Optional.empty()))
                                            .getMessage()),
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
                                                                    ToolInstallState.NOT_INSTALLED,
                                                                    List.of(),
                                                                    List.of(),
                                                                    Optional.empty(),
                                                                    Optional.empty()))
                                            .getMessage()),
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
                                                                    ToolInstallState.NOT_INSTALLED,
                                                                    List.of(),
                                                                    List.of(),
                                                                    Optional.empty(),
                                                                    Optional.empty()))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "state",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () ->
                                                            new ToolOffer(
                                                                    ToolName.PERCOLATOR,
                                                                    PERCOLATOR_3_07_1,
                                                                    ToolOrigin.MANAGED,
                                                                    Nulls.of(
                                                                            ToolInstallState.class),
                                                                    List.of(),
                                                                    List.of(),
                                                                    Optional.empty(),
                                                                    Optional.empty()))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "capabilities",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () ->
                                                            offer(
                                                                    ToolInstallState.NOT_INSTALLED,
                                                                    Nulls.of(List.class),
                                                                    List.of(),
                                                                    Optional.empty()))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "advisories",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () ->
                                                            offer(
                                                                    ToolInstallState.NOT_INSTALLED,
                                                                    List.of(),
                                                                    Nulls.of(List.class),
                                                                    Optional.empty()))
                                            .getMessage()),
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
                                                                    ToolInstallState.NOT_INSTALLED,
                                                                    List.of(),
                                                                    List.of(),
                                                                    Nulls.of(Optional.class),
                                                                    Optional.empty()))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "installedPath",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () ->
                                                            offer(
                                                                    ToolInstallState.NOT_INSTALLED,
                                                                    List.of(),
                                                                    List.of(),
                                                                    Nulls.of(Optional.class)))
                                            .getMessage()));
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
