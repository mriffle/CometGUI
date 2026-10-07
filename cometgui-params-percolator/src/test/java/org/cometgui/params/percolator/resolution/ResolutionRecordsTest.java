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

import static org.cometgui.params.percolator.resolution.Offers.linux3071Installed;
import static org.cometgui.params.percolator.resolution.Offers.local309;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.cometgui.domain.tools.CapabilityEvidence;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.params.percolator.testing.Nulls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** The requirement table, the remedies, the advisories and the records resolution returns. */
class ResolutionRecordsTest {

    private static final StageAvailability AVAILABLE =
            new StageAvailability(
                    DownstreamStage.LIMELIGHT_CONVERSION, true, Optional.empty(), List.of());

    @Nested
    @DisplayName("the stage requirement table")
    class Table {

        @Test
        @DisplayName("Limelight conversion needs XML_OUTPUT and nothing else")
        void limelight() {
            DownstreamStage stage = DownstreamStage.LIMELIGHT_CONVERSION;
            assertEquals(1, DownstreamStage.values().length);
            assertEquals("limelight-conversion", stage.id());
            assertEquals("Limelight conversion", stage.label());
            assertEquals(Set.of(ToolCapability.XML_OUTPUT), stage.requiredCapabilities());
            assertEquals(
                    "the Limelight converter reads the Percolator XML that XML_OUTPUT writes",
                    stage.why());
            assertEquals(
                    List.of(
                            StageRemedy.REGISTER_LOCAL_BINARY,
                            StageRemedy.CONVERT_ON_SUPPORTED_PLATFORM),
                    stage.remedies());
            assertTrue(stage.isSatisfiedBy(Set.of(ToolCapability.XML_OUTPUT)));
            assertTrue(
                    stage.isSatisfiedBy(
                            EnumSet.of(ToolCapability.XML_OUTPUT, ToolCapability.SEED_OPTION)));
            assertFalse(stage.isSatisfiedBy(Set.of(ToolCapability.XML_DECOY_OUTPUT)));
            assertFalse(stage.isSatisfiedBy(Set.of()));
            assertThrows(
                    NullPointerException.class, () -> stage.isSatisfiedBy(Nulls.of(Set.class)));
            assertThrows(
                    UnsupportedOperationException.class,
                    () -> stage.requiredCapabilities().add(ToolCapability.SEED_OPTION));
        }

        @Test
        @DisplayName("the two remedies, word for word")
        void remedies() {
            assertEquals("register-local-binary", StageRemedy.REGISTER_LOCAL_BINARY.id());
            assertEquals(
                    "Register a local Percolator binary that can write Percolator XML, from the"
                            + " Tool Manager. CometGUI probes it, and offers Limelight conversion"
                            + " if the probe observes XML_OUTPUT.",
                    StageRemedy.REGISTER_LOCAL_BINARY.text());
            assertEquals(
                    "convert-on-supported-platform",
                    StageRemedy.CONVERT_ON_SUPPORTED_PLATFORM.id());
            assertEquals(
                    "Run the Limelight conversion on a computer whose platform has an XML-capable"
                            + " Percolator, rerunning Percolator there from this run's merged"
                            + " PIN.",
                    StageRemedy.CONVERT_ON_SUPPORTED_PLATFORM.text());
        }
    }

    @Nested
    @DisplayName("advisories, from the manifest's data")
    class Advisories {

        @Test
        @DisplayName("3.07.1's two advisories, in manifest order, for selection and provenance")
        void threeOhSevenOne() {
            assertEquals(
                    List.of(
                            "Percolator 3.07.1 predates 3.08's change of the default PEP regressor"
                                    + " to I-splines, so its posterior error probabilities are"
                                    + " computed the older way.",
                            "Percolator 3.07.1 predates the fix for PEP values exceeding 1.0"
                                    + " (upstream issue #394, fixed in 3.08.1 and 3.09), so a PEP"
                                    + " above 1.0 can appear in its output."),
                    AdvisoryRendering.forSelection(linux3071Installed()));
            Map<String, String> recorded = AdvisoryRendering.forProvenance(linux3071Installed());
            assertEquals(
                    List.of(
                            "percolator.3-07-1-predates-i-spline-pep-regressor",
                            "percolator.3-07-1-predates-pep-above-one-fix"),
                    List.copyOf(recorded.keySet()));
            assertEquals(
                    "Percolator 3.07.1 predates the fix for PEP values exceeding 1.0 (upstream"
                            + " issue #394, fixed in 3.08.1 and 3.09), so a PEP above 1.0 can"
                            + " appear in its output.",
                    recorded.get("percolator.3-07-1-predates-pep-above-one-fix"));
            assertThrows(UnsupportedOperationException.class, () -> recorded.put("a", "b"));
        }

        @Test
        @DisplayName("none means none, and null is refused")
        void none() {
            assertEquals(List.of(), AdvisoryRendering.forSelection(local309()));
            assertEquals(Map.of(), AdvisoryRendering.forProvenance(local309()));
            assertThrows(NullPointerException.class, () -> AdvisoryRendering.forSelection(null));
            assertThrows(NullPointerException.class, () -> AdvisoryRendering.forProvenance(null));
        }
    }

    @Nested
    @DisplayName("record validation")
    class Records {

        @Test
        @DisplayName("MissingCapability: an observed claim is not missing")
        void missingCapability() {
            MissingCapability absent =
                    new MissingCapability(
                            ToolCapability.XML_OUTPUT,
                            DownstreamStage.LIMELIGHT_CONVERSION,
                            Optional.empty());
            assertFalse(absent.isUnobservedClaim());
            assertTrue(
                    new MissingCapability(
                                    ToolCapability.XML_OUTPUT,
                                    DownstreamStage.LIMELIGHT_CONVERSION,
                                    Optional.of(CapabilityEvidence.INFERRED_FROM_ARTEFACT_BYTES))
                            .isUnobservedClaim());
            IllegalArgumentException observed =
                    assertThrows(
                            IllegalArgumentException.class,
                            () ->
                                    new MissingCapability(
                                            ToolCapability.XML_OUTPUT,
                                            DownstreamStage.LIMELIGHT_CONVERSION,
                                            Optional.of(CapabilityEvidence.OBSERVED_BY_EXECUTION)));
            assertEquals(
                    "XML_OUTPUT was observed by execution, so it is not missing; an unobserved"
                            + " claim must carry evidence that is not observed-by-execution",
                    observed.getMessage());
            assertThrows(
                    NullPointerException.class,
                    () ->
                            new MissingCapability(
                                    null, DownstreamStage.LIMELIGHT_CONVERSION, Optional.empty()));
            assertThrows(
                    NullPointerException.class,
                    () -> new MissingCapability(ToolCapability.XML_OUTPUT, null, Optional.empty()));
            assertThrows(
                    NullPointerException.class,
                    () ->
                            new MissingCapability(
                                    ToolCapability.XML_OUTPUT,
                                    DownstreamStage.LIMELIGHT_CONVERSION,
                                    null));
        }

        @Test
        @DisplayName("SkippedVersion: at least one missing capability and a reason")
        void skippedVersion() {
            MissingCapability gap =
                    new MissingCapability(
                            ToolCapability.XML_OUTPUT,
                            DownstreamStage.LIMELIGHT_CONVERSION,
                            Optional.empty());
            IllegalArgumentException empty =
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> new SkippedVersion(local309(), List.of(), "why"));
            assertEquals(
                    "a skipped version must name at least one missing capability",
                    empty.getMessage());
            IllegalArgumentException blank =
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> new SkippedVersion(local309(), List.of(gap), "  "));
            assertEquals("reason must not be blank", blank.getMessage());
            assertThrows(
                    NullPointerException.class, () -> new SkippedVersion(null, List.of(gap), "w"));
            assertThrows(
                    NullPointerException.class,
                    () -> new SkippedVersion(local309(), List.of(gap), null));
        }

        @Test
        @DisplayName("StageAvailability: available has nothing to explain, unavailable must")
        void stageAvailability() {
            DownstreamStage stage = DownstreamStage.LIMELIGHT_CONVERSION;
            List<StageRemedy> remedies = stage.remedies();
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new StageAvailability(stage, true, Optional.of("x"), List.of()));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new StageAvailability(stage, true, Optional.empty(), remedies));
            IllegalArgumentException silent =
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> new StageAvailability(stage, false, Optional.empty(), remedies));
            assertEquals(
                    "limelight-conversion is unavailable, so it must say why and what to do about"
                            + " it",
                    silent.getMessage());
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new StageAvailability(stage, false, Optional.of("x"), List.of()));
            IllegalArgumentException noisy =
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> new StageAvailability(stage, true, Optional.of("x"), remedies));
            assertEquals(
                    "limelight-conversion is available, so it has nothing to explain or remedy",
                    noisy.getMessage());
            assertTrue(
                    new StageAvailability(stage, false, Optional.of("x"), remedies)
                            .remedies()
                            .contains(StageRemedy.REGISTER_LOCAL_BINARY));
            assertThrows(
                    NullPointerException.class,
                    () -> new StageAvailability(null, true, Optional.empty(), List.of()));
            assertThrows(
                    NullPointerException.class,
                    () -> new StageAvailability(stage, true, null, List.of()));
        }

        @Test
        @DisplayName("PercolatorResolution: one availability per stage, in order, and a reason")
        void resolution() {
            Set<DownstreamStage> none = Set.of();
            IllegalArgumentException missingStage =
                    assertThrows(
                            IllegalArgumentException.class,
                            () ->
                                    new PercolatorResolution(
                                            none,
                                            Optional.empty(),
                                            "r",
                                            List.of(),
                                            List.of(),
                                            List.of(),
                                            List.of()));
            assertEquals(
                    "stages must hold one availability per downstream stage, in declaration"
                            + " order, but named []",
                    missingStage.getMessage());
            IllegalArgumentException twice =
                    assertThrows(
                            IllegalArgumentException.class,
                            () ->
                                    new PercolatorResolution(
                                            none,
                                            Optional.empty(),
                                            "r",
                                            List.of(),
                                            List.of(AVAILABLE, AVAILABLE),
                                            List.of(),
                                            List.of()));
            assertEquals(
                    "stages must hold one availability per downstream stage, in declaration"
                            + " order, but named [LIMELIGHT_CONVERSION, LIMELIGHT_CONVERSION]",
                    twice.getMessage());
            IllegalArgumentException blank =
                    assertThrows(
                            IllegalArgumentException.class,
                            () ->
                                    new PercolatorResolution(
                                            none,
                                            Optional.empty(),
                                            " ",
                                            List.of(),
                                            List.of(AVAILABLE),
                                            List.of(),
                                            List.of()));
            assertEquals("selectionReason must not be blank", blank.getMessage());
            assertThrows(
                    NullPointerException.class,
                    () ->
                            new PercolatorResolution(
                                    null,
                                    Optional.empty(),
                                    "r",
                                    List.of(),
                                    List.of(AVAILABLE),
                                    List.of(),
                                    List.of()));
            assertThrows(
                    NullPointerException.class,
                    () ->
                            new PercolatorResolution(
                                    none,
                                    null,
                                    "r",
                                    List.of(),
                                    List.of(AVAILABLE),
                                    List.of(),
                                    List.of()));
            assertThrows(
                    NullPointerException.class,
                    () ->
                            new PercolatorResolution(
                                    none,
                                    Optional.empty(),
                                    null,
                                    List.of(),
                                    List.of(AVAILABLE),
                                    List.of(),
                                    List.of()));
            PercolatorResolution made =
                    new PercolatorResolution(
                            EnumSet.of(DownstreamStage.LIMELIGHT_CONVERSION),
                            Optional.empty(),
                            "r",
                            List.of(),
                            List.of(AVAILABLE),
                            List.of(),
                            List.of());
            assertThrows(
                    UnsupportedOperationException.class,
                    () -> made.enabledStages().remove(DownstreamStage.LIMELIGHT_CONVERSION));
            assertThrows(
                    NullPointerException.class,
                    () -> made.availability(Nulls.of(DownstreamStage.class)));
            assertTrue(made.isAvailable(DownstreamStage.LIMELIGHT_CONVERSION));
        }
    }
}
