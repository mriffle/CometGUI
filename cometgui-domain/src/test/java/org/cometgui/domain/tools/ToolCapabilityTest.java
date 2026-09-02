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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.cometgui.domain.testing.Nulls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for {@link ToolCapability}.
 *
 * <p>The two capability lists are hand-typed from the specification's <em>Capability and runtime
 * probing</em> section. The owning tool is asserted for every constant, because the guard this type
 * provides -- that a manifest cannot claim {@code THERMO_RAW_WINDOWS} for Percolator -- is only as
 * good as the ownership table behind it.
 */
class ToolCapabilityTest {

    private static final List<String> PERCOLATOR_CAPABILITIES =
            List.of(
                    "XML_OUTPUT",
                    "XML_DECOY_OUTPUT",
                    "PSM_TSV_OUTPUT",
                    "PEPTIDE_TSV_OUTPUT",
                    "DECOY_OUTPUT",
                    "WEIGHTS_OUTPUT",
                    "THREAD_OPTION",
                    "SEED_OPTION");

    private static final List<String> COMET_CAPABILITIES =
            List.of(
                    "PEPXML_OUTPUT",
                    "PIN_OUTPUT",
                    "COMPLETE_PARAMS_QUERY",
                    "THERMO_RAW_WINDOWS",
                    "FRAGMENT_ION_INDEX",
                    "PEPTIDE_INDEX",
                    "SCAN_RANGE",
                    "OUTPUT_BASENAME");

    private static List<String> namesOf(Set<ToolCapability> capabilities) {
        List<String> names = new ArrayList<>();
        for (ToolCapability capability : capabilities) {
            names.add(capability.name());
        }
        return names;
    }

    @Test
    @DisplayName("the sixteen capabilities the specification names are the sixteen that exist")
    void theCapabilitiesArePinned() {
        List<String> names = new ArrayList<>();
        for (ToolCapability capability : ToolCapability.values()) {
            names.add(capability.name());
        }

        List<String> expected = new ArrayList<>(PERCOLATOR_CAPABILITIES);
        expected.addAll(COMET_CAPABILITIES);
        assertEquals(expected, names);
    }

    @Test
    @DisplayName("each tool owns exactly the capabilities the specification gives it")
    void ownershipIsPinned() {
        assertAll(
                () ->
                        assertEquals(
                                PERCOLATOR_CAPABILITIES,
                                namesOf(ToolCapability.declarableFor(ToolName.PERCOLATOR))),
                () ->
                        assertEquals(
                                COMET_CAPABILITIES,
                                namesOf(ToolCapability.declarableFor(ToolName.COMET))),
                () -> assertEquals(List.of(), namesOf(ToolCapability.declarableFor(ToolName.PDV))),
                () ->
                        assertEquals(
                                List.of(),
                                namesOf(
                                        ToolCapability.declarableFor(
                                                ToolName.LIMELIGHT_CONVERTER))));
    }

    @Test
    @DisplayName("XML_DECOY_OUTPUT is a capability of its own, not part of XML_OUTPUT")
    void theTwoXmlCapabilitiesAreSeparate() {
        assertAll(
                () ->
                        assertTrue(
                                ToolCapability.declarableFor(ToolName.PERCOLATOR)
                                        .contains(ToolCapability.XML_OUTPUT)),
                () ->
                        assertTrue(
                                ToolCapability.declarableFor(ToolName.PERCOLATOR)
                                        .contains(ToolCapability.XML_DECOY_OUTPUT)),
                () ->
                        assertNotEquals(
                                ToolCapability.XML_OUTPUT,
                                ToolCapability.XML_DECOY_OUTPUT,
                                "the two XML capabilities must remain distinct"));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @EnumSource(ToolCapability.class)
    @DisplayName("a capability belongs to its own tool and to no other")
    void belongsToOnlyItsOwnTool(ToolCapability capability) {
        for (ToolName tool : ToolName.values()) {
            assertEquals(
                    tool == capability.tool(),
                    capability.belongsTo(tool),
                    capability.id() + " against " + tool.id());
        }
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @EnumSource(ToolCapability.class)
    @DisplayName("a capability cannot be attached to any tool but its own")
    void aCapabilityCannotBeAttachedToTheWrongTool(ToolCapability capability) {
        /*
         * The full cross, not three examples. The rejection does not depend on which capability or
         * which tool is involved, so grading it at one pair would leave both axes untested -- the
         * shape that let a blank-note rule be switched off for a single enum constant in
         * DeclaredCapability and still pass 108 tests. Driven off values(), so a seventeenth
         * capability or a fifth tool is graded the day it is declared.
         */
        List<Executable> assertions = new ArrayList<>();
        for (ToolName tool : ToolName.values()) {
            if (tool == capability.tool()) {
                continue;
            }
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
                                                    () -> capability.requireBelongsTo(tool))
                                            .getMessage(),
                                    capability.id() + " offered to " + tool.id()));
        }

        assertEquals(3, assertions.size(), "every capability has exactly three wrong tools");
        assertAll(assertions);
    }

    @Test
    @DisplayName("the three rejection messages the specification's own examples produce")
    void theRejectionMessagesArePinned() {
        assertAll(
                () ->
                        assertEquals(
                                "THERMO_RAW_WINDOWS is a capability of comet and cannot be"
                                        + " declared for percolator",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () ->
                                                        ToolCapability.THERMO_RAW_WINDOWS
                                                                .requireBelongsTo(
                                                                        ToolName.PERCOLATOR))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "XML_OUTPUT is a capability of percolator and cannot be declared"
                                        + " for comet",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () ->
                                                        ToolCapability.XML_OUTPUT.requireBelongsTo(
                                                                ToolName.COMET))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "XML_OUTPUT is a capability of percolator and cannot be declared"
                                        + " for pdv",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () ->
                                                        ToolCapability.XML_OUTPUT.requireBelongsTo(
                                                                ToolName.PDV))
                                        .getMessage()));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @EnumSource(ToolCapability.class)
    @DisplayName("a capability attached to its own tool is returned unchanged")
    void theRightToolIsAccepted(ToolCapability capability) {
        assertEquals(capability, capability.requireBelongsTo(capability.tool()));
    }

    @Test
    @DisplayName("a null tool is rejected by name rather than treated as the wrong tool")
    void nullToolsAreRejectedByName() {
        ToolName absent = Nulls.of(ToolName.class);

        assertAll(
                () ->
                        assertEquals(
                                "candidate",
                                assertThrows(
                                                NullPointerException.class,
                                                () -> ToolCapability.XML_OUTPUT.belongsTo(absent))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "candidate",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        ToolCapability.XML_OUTPUT.requireBelongsTo(
                                                                absent))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "tool",
                                assertThrows(
                                                NullPointerException.class,
                                                () -> ToolCapability.declarableFor(absent))
                                        .getMessage()));
    }

    @Test
    @DisplayName("the set of capabilities a tool may declare cannot be modified")
    void theDeclarableSetIsImmutable() {
        Set<ToolCapability> percolator = ToolCapability.declarableFor(ToolName.PERCOLATOR);

        assertThrows(
                UnsupportedOperationException.class,
                () -> percolator.add(ToolCapability.PIN_OUTPUT));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @EnumSource(ToolCapability.class)
    @DisplayName("every capability's identifier resolves back to it")
    void identifiersResolve(ToolCapability capability) {
        assertEquals(capability, ToolCapability.fromId(capability.id()));
    }

    @ParameterizedTest(name = "[{index}] {0} -> {1}")
    @CsvSource({
        "XML_OUTPUT, XML_OUTPUT",
        "XML_DECOY_OUTPUT, XML_DECOY_OUTPUT",
        "THERMO_RAW_WINDOWS, THERMO_RAW_WINDOWS",
        "COMPLETE_PARAMS_QUERY, COMPLETE_PARAMS_QUERY"
    })
    @DisplayName("the identifier is the token the specification uses")
    void identifiersArePinned(String constant, String expectedId) {
        assertEquals(expectedId, ToolCapability.valueOf(constant).id());
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @ValueSource(strings = {"xml_output", "XML-OUTPUT", " XML_OUTPUT", "XMLOUTPUT", "NOXML", ""})
    @DisplayName("an unknown identifier is rejected by name, with no trimming and no case folding")
    void unknownIdentifiersAreRejected(String unknown) {
        IllegalArgumentException rejected =
                assertThrows(IllegalArgumentException.class, () -> ToolCapability.fromId(unknown));

        assertTrue(
                rejected.getMessage()
                        .startsWith("no tool capability has the id \"" + unknown + "\""),
                rejected.getMessage());
    }

    @Test
    @DisplayName("the rejection message lists every identifier that is accepted")
    void theRejectionMessageDoesNotDrift() {
        IllegalArgumentException rejected =
                assertThrows(
                        IllegalArgumentException.class, () -> ToolCapability.fromId("nonsense"));

        for (ToolCapability capability : ToolCapability.values()) {
            assertTrue(
                    rejected.getMessage().contains(capability.id()),
                    "the rejection message does not mention " + capability.id());
        }
    }

    @Test
    @DisplayName("a null identifier is rejected as null, not as unknown")
    void nullIsRejectedByName() {
        NullPointerException rejected =
                assertThrows(
                        NullPointerException.class,
                        () -> ToolCapability.fromId(Nulls.of(String.class)));

        assertEquals("id", rejected.getMessage());
    }
}
