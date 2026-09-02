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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.cometgui.domain.testing.Nulls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for {@link CapabilityEvidence}.
 *
 * <p>{@link CapabilityEvidence#isObserved()} is asserted for all three constants rather than for
 * the interesting one, because the rule it encodes is a prohibition: everything that is not
 * observation by execution must answer false, and a fourth constant added later without a decision
 * about its evidential weight would fail here.
 */
class CapabilityEvidenceTest {

    @Test
    @DisplayName("the three kinds of evidence are the three that exist")
    void theThreeArePinned() {
        List<String> names = new ArrayList<>();
        for (CapabilityEvidence evidence : CapabilityEvidence.values()) {
            names.add(evidence.name());
        }

        assertEquals(
                List.of("OBSERVED_BY_EXECUTION", "INFERRED_FROM_ARTEFACT_BYTES", "UNVERIFIED"),
                names);
    }

    @Test
    @DisplayName("only execution counts as observation")
    void onlyExecutionIsObserved() {
        assertAll(
                () -> assertTrue(CapabilityEvidence.OBSERVED_BY_EXECUTION.isObserved()),
                () -> assertFalse(CapabilityEvidence.INFERRED_FROM_ARTEFACT_BYTES.isObserved()),
                () -> assertFalse(CapabilityEvidence.UNVERIFIED.isObserved()));
    }

    @Test
    @DisplayName("exactly one of the three is observed")
    void exactlyOneIsObserved() {
        List<String> observed = new ArrayList<>();
        for (CapabilityEvidence evidence : CapabilityEvidence.values()) {
            if (evidence.isObserved()) {
                observed.add(evidence.name());
            }
        }

        assertEquals(List.of("OBSERVED_BY_EXECUTION"), observed);
    }

    @ParameterizedTest(name = "[{index}] {0} -> {1}")
    @CsvSource({
        "OBSERVED_BY_EXECUTION, observed-by-execution",
        "INFERRED_FROM_ARTEFACT_BYTES, inferred-from-artefact-bytes",
        "UNVERIFIED, unverified"
    })
    @DisplayName("each carries the identifier the manifest uses, and it resolves back")
    void identifiersArePinnedAndResolve(String constant, String expectedId) {
        CapabilityEvidence evidence = CapabilityEvidence.valueOf(constant);

        assertEquals(expectedId, evidence.id());
        assertEquals(evidence, CapabilityEvidence.fromId(expectedId));
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @ValueSource(
            strings = {
                "observed",
                "OBSERVED_BY_EXECUTION",
                "observed_by_execution",
                " unverified",
                "verified",
                "proven",
                ""
            })
    @DisplayName(
            "an unknown identifier is rejected by name; \"verified\" is not a kind of evidence")
    void unknownIdentifiersAreRejected(String unknown) {
        IllegalArgumentException rejected =
                assertThrows(
                        IllegalArgumentException.class, () -> CapabilityEvidence.fromId(unknown));

        assertEquals(
                "no capability evidence has the id \""
                        + unknown
                        + "\"; expected one of [observed-by-execution,"
                        + " inferred-from-artefact-bytes, unverified]",
                rejected.getMessage());
    }

    @Test
    @DisplayName("the rejection message lists every identifier that is accepted")
    void theRejectionMessageDoesNotDrift() {
        IllegalArgumentException rejected =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> CapabilityEvidence.fromId("nonsense"));

        for (CapabilityEvidence evidence : CapabilityEvidence.values()) {
            assertTrue(
                    rejected.getMessage().contains(evidence.id()),
                    "the rejection message does not mention " + evidence.id());
        }
    }

    @Test
    @DisplayName("a null identifier is rejected as null, not as unknown")
    void nullIsRejectedByName() {
        NullPointerException rejected =
                assertThrows(
                        NullPointerException.class,
                        () -> CapabilityEvidence.fromId(Nulls.of(String.class)));

        assertEquals("id", rejected.getMessage());
    }
}
