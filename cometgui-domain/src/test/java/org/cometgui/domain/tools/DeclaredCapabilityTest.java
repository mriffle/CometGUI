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

import org.cometgui.domain.testing.Nulls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for {@link DeclaredCapability}.
 *
 * <p>The blank-note rejection is the interesting one. It is not input hygiene: a manifest row whose
 * note is empty is indistinguishable from one nobody ever checked, and the whole purpose of
 * carrying evidence is that a reader can go and look at it.
 */
class DeclaredCapabilityTest {

    private static final String NOTE = "executed on linux-x86-64 by phase 00 and phase 05";

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

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @ValueSource(strings = {"", " ", "\t", "\n", "   \t  \n"})
    @DisplayName("a blank note is rejected with a message naming the field")
    void aBlankNoteIsRejected(String blank) {
        IllegalArgumentException rejected =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                new DeclaredCapability(
                                        ToolCapability.XML_OUTPUT,
                                        CapabilityEvidence.OBSERVED_BY_EXECUTION,
                                        blank));

        assertEquals(
                "note must not be blank: evidence with no provenance is not evidence, so say"
                        + " where it came from -- for example \"executed on linux-x86-64 by"
                        + " phase 00 and phase 05\"",
                rejected.getMessage());
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
