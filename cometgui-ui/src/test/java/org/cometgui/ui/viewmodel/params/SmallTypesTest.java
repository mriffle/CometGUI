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

package org.cometgui.ui.viewmodel.params;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The small value types of the package, with their words typed by hand. */
class SmallTypesTest {

    @Test
    @DisplayName("an outcome is accepted with no refusal, or refused with one")
    void editOutcome() {
        assertEquals(new EditOutcome(true, Optional.empty()), EditOutcome.applied());
        assertEquals(new EditOutcome(false, Optional.of("why")), EditOutcome.refused("why"));
        assertEquals(
                "an accepted edit has no refusal",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> new EditOutcome(true, Optional.of("why")))
                        .getMessage());
        assertEquals(
                "a refused edit has to say why",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> new EditOutcome(false, Optional.empty()))
                        .getMessage());
    }

    @Test
    @DisplayName("new and imported sets start afresh; raw applies and presets edit")
    void adoption() {
        assertTrue(Adoption.NEW.startsAfresh());
        assertTrue(Adoption.IMPORTED.startsAfresh());
        assertFalse(Adoption.RAW_APPLIED.startsAfresh());
        assertFalse(Adoption.PRESET_APPLIED.startsAfresh());
    }

    @Test
    @DisplayName("states and file statuses in words")
    void words() {
        assertEquals(
                List.of("No problems", "Warning", "Error"),
                List.of(FieldState.values()).stream().map(FieldState::words).toList());
        assertEquals(
                List.of(
                        "Found and readable",
                        "Not found",
                        "Found but cannot be read",
                        "A folder, not a file",
                        "Not a usable path",
                        "No file chosen"),
                List.of(FileStatus.values()).stream().map(FileStatus::words).toList());
        assertEquals(
                "Not found: x/y.mgf",
                new InputFile(Path.of("x", "y.mgf"), FileStatus.MISSING).text());
        assertEquals("No file chosen", new InputFile(Path.of(""), FileStatus.NONE).text());
    }

    @Test
    @DisplayName("a choice shows its token for advanced help")
    void choiceOption() {
        assertEquals("ppm [2]", new ChoiceOption("2", "ppm").withToken());
        assertEquals("2", new ChoiceOption("2", "ppm").token());
        assertEquals("ppm", new ChoiceOption("2", "ppm").label());
    }

    @Test
    @DisplayName("every stage enabled answers yes for every output")
    void allEnabled() {
        assertTrue(StageSwitches.ALL_ENABLED.dependentStageEnabled("output_pepxmlfile"));
        assertTrue(StageSwitches.ALL_ENABLED.dependentStageEnabled("output_percolatorfile"));
    }

    @Test
    @DisplayName("each Essentials section says what it is for")
    void sectionPurposes() {
        assertEquals(
                "The result files the CometGUI workflow needs: pepXML and the Percolator input"
                        + " (PIN) are required.",
                EssentialsSection.OUTPUTS.purpose());
        assertEquals(
                "How many search threads Comet runs; 0 chooses one per processor core.",
                EssentialsSection.EXECUTION.purpose());
        for (EssentialsSection section : EssentialsSection.values()) {
            assertFalse(section.purpose().isBlank(), section.name());
        }
    }
}
