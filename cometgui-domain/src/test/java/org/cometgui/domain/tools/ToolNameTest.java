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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.cometgui.domain.testing.Nulls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for {@link ToolName}.
 *
 * <p>Every identifier is hand-typed here rather than derived from the constant, because the whole
 * reason the identifier is a stored field is that the two must be able to differ. A test that
 * computed the expected value from {@code name().toLowerCase()} would pass on the day someone
 * changed the manifest format by renaming a constant.
 */
class ToolNameTest {

    @Test
    @DisplayName("the four managed tools are the four that exist")
    void theFourToolsArePinned() {
        List<String> names = new ArrayList<>();
        for (ToolName tool : ToolName.values()) {
            names.add(tool.name());
        }

        assertEquals(List.of("COMET", "PERCOLATOR", "PDV", "LIMELIGHT_CONVERTER"), names);
    }

    @ParameterizedTest(name = "[{index}] {0} -> {1}")
    @CsvSource({
        "COMET, comet",
        "PERCOLATOR, percolator",
        "PDV, pdv",
        "LIMELIGHT_CONVERTER, limelight-converter"
    })
    @DisplayName("each tool carries the identifier the manifest and the cache path use")
    void identifiersArePinned(String constant, String expectedId) {
        assertEquals(expectedId, ToolName.valueOf(constant).id());
    }

    @Test
    @DisplayName("no two tools share an identifier")
    void identifiersAreDistinct() {
        Set<String> distinct = new HashSet<>();
        for (ToolName tool : ToolName.values()) {
            assertTrue(distinct.add(tool.id()), "two tools share the id " + tool.id());
        }

        assertEquals(4, distinct.size());
    }

    @Test
    @DisplayName("an identifier is not the constant lower-cased, and could not be")
    void identifiersAreIndependentOfTheConstantName() {
        assertEquals(
                "limelight_converter",
                ToolName.LIMELIGHT_CONVERTER.name().toLowerCase(Locale.ROOT));
        assertEquals("limelight-converter", ToolName.LIMELIGHT_CONVERTER.id());
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvSource({
        "comet, COMET",
        "percolator, PERCOLATOR",
        "pdv, PDV",
        "limelight-converter, LIMELIGHT_CONVERTER"
    })
    @DisplayName("every hand-typed identifier resolves back to its constant")
    void identifiersResolve(String id, String expectedConstant) {
        assertEquals(ToolName.valueOf(expectedConstant), ToolName.fromId(id));
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @ValueSource(
            strings = {
                "Comet",
                "COMET",
                " comet",
                "comet ",
                "limelight_converter",
                "limelight",
                "xtandem",
                ""
            })
    @DisplayName("an unknown identifier is rejected by name, with no trimming and no case folding")
    void unknownIdentifiersAreRejected(String unknown) {
        IllegalArgumentException rejected =
                assertThrows(IllegalArgumentException.class, () -> ToolName.fromId(unknown));

        assertEquals(
                "no tool has the id \""
                        + unknown
                        + "\"; expected one of [comet, percolator, pdv, limelight-converter]",
                rejected.getMessage());
    }

    @Test
    @DisplayName("the rejection message lists every identifier that is accepted")
    void theRejectionMessageDoesNotDriftFromTheConstants() {
        IllegalArgumentException rejected =
                assertThrows(IllegalArgumentException.class, () -> ToolName.fromId("nonsense"));

        for (ToolName tool : ToolName.values()) {
            assertTrue(
                    rejected.getMessage().contains(tool.id()),
                    "the rejection message does not mention " + tool.id());
        }
    }

    @Test
    @DisplayName("a null identifier is rejected as null, not as unknown")
    void nullIsRejectedByName() {
        NullPointerException rejected =
                assertThrows(
                        NullPointerException.class, () -> ToolName.fromId(Nulls.of(String.class)));

        assertEquals("id", rejected.getMessage());
    }
}
