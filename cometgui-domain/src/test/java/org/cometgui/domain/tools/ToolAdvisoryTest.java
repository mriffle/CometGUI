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

import org.cometgui.domain.testing.Nulls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for {@link ToolAdvisory}.
 *
 * <p>The two advisories used here are the ones {@code R-PERC-11} requires Percolator 3.07.1 to
 * carry, so the shape of the identifier is exercised against the values it will actually hold.
 */
class ToolAdvisoryTest {

    @Test
    @DisplayName("an advisory keeps its identifier and its sentence")
    void keepsItsParts() {
        ToolAdvisory advisory =
                new ToolAdvisory(
                        "percolator.pep-regressor-changed-in-3-08",
                        "3.07.1 predates 3.08's change of default PEP regressor to I-splines.");

        assertAll(
                () -> assertEquals("percolator.pep-regressor-changed-in-3-08", advisory.id()),
                () ->
                        assertEquals(
                                "3.07.1 predates 3.08's change of default PEP regressor to"
                                        + " I-splines.",
                                advisory.text()));
    }

    @Test
    @DisplayName("the sentence is stripped, so trailing whitespace cannot change identity")
    void theTextIsStripped() {
        assertEquals(
                new ToolAdvisory("percolator.pep-above-one", "PEP values may exceed 1.0."),
                new ToolAdvisory("percolator.pep-above-one", "  PEP values may exceed 1.0. \n"));
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @ValueSource(
            strings = {
                "percolator",
                "percolator.pep-above-one",
                "percolator.3-06-5.peptide-protein-ids",
                "comet.thermo-raw-windows-only",
                "a",
                "a1.b2-c3"
            })
    @DisplayName("an identifier a test or a provenance record can name is accepted")
    void usableIdentifiersAreAccepted(String id) {
        assertEquals(id, new ToolAdvisory(id, "something a user should know").id());
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @ValueSource(
            strings = {
                "",
                " ",
                "Percolator",
                "percolator.PEP",
                "percolator..pep",
                "percolator--pep",
                "percolator.",
                ".percolator",
                "percolator pep",
                "percolator_pep",
                "percolator.pep!"
            })
    @DisplayName("an identifier that cannot be relied on is rejected, quoting it")
    void unusableIdentifiersAreRejected(String id) {
        IllegalArgumentException rejected =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new ToolAdvisory(id, "something a user should know"));

        assertEquals(
                "not a usable advisory id: \""
                        + id
                        + "\" (expected lower-case words joined by single dots or hyphens, such"
                        + " as percolator.pep-regressor-changed-in-3-08)",
                rejected.getMessage());
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @ValueSource(strings = {"", " ", "\t\n"})
    @DisplayName("a blank sentence is rejected, naming the field")
    void aBlankTextIsRejected(String blank) {
        IllegalArgumentException rejected =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new ToolAdvisory("percolator.pep-above-one", blank));

        assertEquals(
                "text must not be blank: an advisory with no sentence tells the user nothing",
                rejected.getMessage());
    }

    @Test
    @DisplayName("a null part is rejected by name")
    void nullPartsAreRejectedByName() {
        assertAll(
                () ->
                        assertEquals(
                                "id",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new ToolAdvisory(
                                                                Nulls.of(String.class), "text"))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "text",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new ToolAdvisory(
                                                                "percolator.pep-above-one",
                                                                Nulls.of(String.class)))
                                        .getMessage()));
    }
}
