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
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Tests for {@link DeclaredCapability}.
 *
 * <p>The blank-note rejection is the interesting one. It is not input hygiene: a manifest row whose
 * note is empty is indistinguishable from one nobody ever checked, and the whole purpose of
 * carrying evidence is that a reader can go and look at it.
 */
class DeclaredCapabilityTest {

    private static final String NOTE = "executed on linux-x86-64 by phase 00 and phase 05";

    private static final List<String> BLANK_NOTES = List.of("", " ", "\t", "\n", "   \t  \n");

    private static final String BLANK_NOTE_REJECTION =
            "note must not be blank: evidence with no provenance is not evidence, so say where it"
                    + " came from -- for example \"executed on linux-x86-64 by phase 00 and phase"
                    + " 05\"";

    @Test
    @DisplayName("a claim keeps its capability, its evidence and its note")
    void keepsItsParts() {
        DeclaredCapability declared =
                new DeclaredCapability(
                        ToolCapability.XML_OUTPUT, CapabilityEvidence.OBSERVED_BY_EXECUTION, NOTE);

        assertAll(
                () -> assertEquals(ToolCapability.XML_OUTPUT, declared.capability()),
                () -> assertEquals(CapabilityEvidence.OBSERVED_BY_EXECUTION, declared.evidence()),
                () -> assertEquals(NOTE, declared.note()),
                () -> assertTrue(declared.isObserved()));
    }

    @Test
    @DisplayName("a claim that was not observed by execution does not report itself as observed")
    void inferenceIsNotObservation() {
        DeclaredCapability inferred =
                new DeclaredCapability(
                        ToolCapability.XML_OUTPUT,
                        CapabilityEvidence.INFERRED_FROM_ARTEFACT_BYTES,
                        "writer literal found in the windows portable zip; never executed");
        DeclaredCapability unverified =
                new DeclaredCapability(
                        ToolCapability.XML_OUTPUT,
                        CapabilityEvidence.UNVERIFIED,
                        "local binary registered by the user; not probed yet");

        assertAll(
                () -> assertFalse(inferred.isObserved()),
                () -> assertFalse(unverified.isObserved()));
    }

    @Test
    @DisplayName("the note is stripped, so a note that is only whitespace cannot slip through")
    void theNoteIsStripped() {
        DeclaredCapability declared =
                new DeclaredCapability(
                        ToolCapability.PIN_OUTPUT,
                        CapabilityEvidence.OBSERVED_BY_EXECUTION,
                        "  " + NOTE + "\n");

        assertEquals(NOTE, declared.note());
    }

    /*
     * THE REJECTION IS ASSERTED ON BOTH AXES, AND THIS IS WHY.
     *
     * The first version of this test fixed the evidence at OBSERVED_BY_EXECUTION and varied only
     * the blank string. The phase orchestrator injected
     *
     *     if (note.isBlank() && evidence != CapabilityEvidence.UNVERIFIED)
     *
     * -- "an unverified capability has no provenance to state, so let its note be empty" -- and 108
     * tests across four classes passed with the rule switched off. The blank axis was covered
     * thoroughly and the evidence axis was not covered at all.
     *
     * UNVERIFIED is not an arbitrary constant to have missed. It is the value every Windows and
     * every macOS capability row in this phase's manifest carries, because no Windows or macOS
     * binary has ever been executed anywhere in this project, and the note is the only field that
     * records why such a row is unverified. A row reading XML_OUTPUT / UNVERIFIED / "" is an
     * unverified claim with no provenance at all.
     *
     * The rejection does not depend on the evidence or on the capability, so it is asserted over
     * every value of both. Both sources are driven off values(), so a fourth CapabilityEvidence or
     * a seventeenth ToolCapability is covered the day it is declared rather than the day someone
     * remembers to add it here.
     */
    @ParameterizedTest(name = "[{index}] evidence={0}")
    @EnumSource(CapabilityEvidence.class)
    @DisplayName("a blank note is rejected whatever the evidence says, naming the field")
    void aBlankNoteIsRejectedForEveryEvidence(CapabilityEvidence evidence) {
        List<Executable> assertions = new ArrayList<>();
        for (String blank : BLANK_NOTES) {
            assertions.add(
                    () ->
                            assertEquals(
                                    BLANK_NOTE_REJECTION,
                                    assertThrows(
                                                    IllegalArgumentException.class,
                                                    () ->
                                                            new DeclaredCapability(
                                                                    ToolCapability.XML_OUTPUT,
                                                                    evidence,
                                                                    blank))
                                            .getMessage(),
                                    "evidence "
                                            + evidence.name()
                                            + " with note \""
                                            + blank
                                            + "\""));
        }

        assertAll(assertions);
    }

    @ParameterizedTest(name = "[{index}] capability={0}")
    @EnumSource(ToolCapability.class)
    @DisplayName("a blank note is rejected whatever the capability is")
    void aBlankNoteIsRejectedForEveryCapability(ToolCapability capability) {
        List<Executable> assertions = new ArrayList<>();
        for (CapabilityEvidence evidence : CapabilityEvidence.values()) {
            assertions.add(
                    () ->
                            assertEquals(
                                    BLANK_NOTE_REJECTION,
                                    assertThrows(
                                                    IllegalArgumentException.class,
                                                    () ->
                                                            new DeclaredCapability(
                                                                    capability, evidence, "   "))
                                            .getMessage(),
                                    capability.id() + " with evidence " + evidence.name()));
        }

        assertAll(assertions);
    }

    @Test
    @DisplayName("the blank-note rejection is graded over every evidence value that exists")
    void theEvidenceAxisIsCoveredWhole() {
        /*
         * The guard on the guard. aBlankNoteIsRejectedForEveryEvidence is driven by @EnumSource, so
         * it grows on its own -- but only while nothing narrows it back to a names= list. This
         * assertion fails if the rejection stops holding for every declared evidence value, whether
         * that is because a constant was added or because the rule grew a condition.
         */
        List<String> rejectedFor = new ArrayList<>();
        for (CapabilityEvidence evidence : CapabilityEvidence.values()) {
            try {
                new DeclaredCapability(ToolCapability.XML_OUTPUT, evidence, "");
            } catch (IllegalArgumentException expected) {
                rejectedFor.add(evidence.name());
            }
        }

        assertEquals(
                List.of("OBSERVED_BY_EXECUTION", "INFERRED_FROM_ARTEFACT_BYTES", "UNVERIFIED"),
                rejectedFor,
                "every evidence value must reject a blank note; a value missing here is a"
                        + " capability that can be claimed with no provenance");
    }

    @Test
    @DisplayName("a null part is rejected by name")
    void nullPartsAreRejectedByName() {
        assertAll(
                () ->
                        assertEquals(
                                "capability",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new DeclaredCapability(
                                                                Nulls.of(ToolCapability.class),
                                                                CapabilityEvidence.UNVERIFIED,
                                                                NOTE))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "evidence",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new DeclaredCapability(
                                                                ToolCapability.XML_OUTPUT,
                                                                Nulls.of(CapabilityEvidence.class),
                                                                NOTE))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "note",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new DeclaredCapability(
                                                                ToolCapability.XML_OUTPUT,
                                                                CapabilityEvidence.UNVERIFIED,
                                                                Nulls.of(String.class)))
                                        .getMessage()));
    }

    @Test
    @DisplayName("two claims with the same parts are equal")
    void valueSemantics() {
        assertEquals(
                new DeclaredCapability(
                        ToolCapability.XML_OUTPUT, CapabilityEvidence.OBSERVED_BY_EXECUTION, NOTE),
                new DeclaredCapability(
                        ToolCapability.XML_OUTPUT,
                        CapabilityEvidence.OBSERVED_BY_EXECUTION,
                        " " + NOTE + " "));
    }
}
