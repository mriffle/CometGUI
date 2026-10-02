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

package org.cometgui.params.comet.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.fixtures.CometFixtures;
import org.cometgui.params.comet.fixtures.DeclarationLines;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The metadata file that ships in the module: it loads, and it says what the specification needs.
 */
class BundledMetadataTest {

    private static final CuratedMetadata METADATA = MetadataLoader.loadBundled();

    private static ParameterDefinition definition(String name) {
        return METADATA.parameter(name)
                .orElseThrow(() -> new AssertionError(name + " is not in the bundled metadata"));
    }

    private static List<String> values(String name) {
        return definition(name).choices().stream().map(Choice::value).toList();
    }

    @Test
    @DisplayName("it loads, and models every name comet -q declares, in -q's order")
    void itModelsTheCompleteDumpInOrder() throws IOException {
        List<String> declared =
                DeclarationLines.names(
                        CometFixtures.lines(
                                CometFixtures.COMET_2026_02_2,
                                CometFixtures.LINUX_X86_64,
                                CometFixtures.Mode.COMPLETE));
        List<String> modelled =
                METADATA.parameters().stream().map(ParameterDefinition::name).toList();
        assertEquals(declared, modelled);
        assertEquals(118, modelled.size());
        assertEquals(List.of(), METADATA.internal(), "the 2026.02.2 allow-list is empty");
        assertEquals(118, METADATA.parametersFor(ToolVersion.parse("2026.02.2")).size());
    }

    @Test
    @DisplayName("it records 2026.02.2 under the marker the binary writes")
    void itRecordsTheVersionMarker() {
        CometVersionRecord record = METADATA.version(ToolVersion.parse("2026.02.2")).orElseThrow();
        assertEquals("2026.02 rev. 2 (6edec91)", record.marker().text());
        assertEquals(
                "https://uwpr.github.io/Comet/parameters/parameters_202602/",
                record.parameterPages());
    }

    @Test
    @DisplayName("the enum labels the specification names are all there")
    void theSpecificationsEnumsAreLabelled() {
        assertEquals(List.of("0", "1", "2", "3", "4", "5", "6", "7"), values("isotope_error"));
        assertEquals(List.of("1", "2", "8", "9"), values("num_enzyme_termini"));
        assertEquals(List.of("0", "1", "2"), values("decoy_search"));
        assertEquals(List.of("0", "1", "2"), values("peptide_mass_units"));
        for (ParameterDefinition definition : METADATA.parameters()) {
            for (Choice choice : definition.choices()) {
                assertFalse(choice.label().isBlank(), definition.name() + " " + choice.value());
            }
        }
    }

    @Test
    @DisplayName("the specification's search aliases find the right parameters")
    void theSearchAliasesAreThere() {
        assertTrue(
                definition("peptide_mass_tolerance_upper")
                        .aliases()
                        .contains("precursor tolerance"));
        assertTrue(
                definition("peptide_mass_tolerance_lower")
                        .aliases()
                        .contains("precursor tolerance"));
        assertTrue(definition("allowed_missed_cleavage").aliases().contains("missed cleavage"));
        for (int slot = 1; slot <= 15; slot++) {
            String name = String.format(java.util.Locale.ROOT, "variable_mod%02d", slot);
            assertTrue(definition(name).aliases().contains("oxidation"), name);
            assertEquals(ValueKind.VARIABLE_MOD_TUPLE, definition(name).kind(), name);
        }
    }

    @Test
    @DisplayName("structural kinds land where the specification puts them")
    void structuralKindsAreRight() {
        assertEquals(
                ValueKind.TOLERANCE_PAIR_MEMBER, definition("peptide_mass_tolerance_lower").kind());
        assertEquals(
                List.of(ValidatorId.SIGNED_TOLERANCE_PAIR),
                definition("peptide_mass_tolerance_lower").validators());
        assertEquals(ValueKind.INTEGER_RANGE, definition("peptide_length_range").kind());
        assertEquals(
                SerializationRule.EMPTY_ALLOWED,
                definition("pinfile_protein_delimiter").serialization());
        assertEquals(SerializationRule.EMPTY_ALLOWED, definition("peff_obo").serialization());
        assertEquals(ValueKind.FILE_PATH, definition("database_name").kind());
        assertEquals(ValueKind.ENZYME_REFERENCE, definition("search_enzyme2_number").kind());
        Set<String> ions =
                METADATA.parameters().stream()
                        .filter(p -> p.kind() == ValueKind.ION_SERIES_FLAG)
                        .map(ParameterDefinition::name)
                        .collect(Collectors.toSet());
        assertEquals(
                Set.of(
                        "use_A_ions",
                        "use_B_ions",
                        "use_C_ions",
                        "use_X_ions",
                        "use_Y_ions",
                        "use_Z_ions",
                        "use_Z1_ions"),
                ions);
    }

    @Test
    @DisplayName("all three editor levels are used, and every parameter cites a source")
    void levelsAndReferences() {
        Set<VisibilityLevel> used = EnumSet.noneOf(VisibilityLevel.class);
        for (ParameterDefinition definition : METADATA.parameters()) {
            used.add(definition.visibility());
            assertTrue(
                    definition
                                    .detailedHelpRef()
                                    .startsWith(
                                            "https://uwpr.github.io/Comet/parameters/parameters_202602/")
                            || definition
                                    .detailedHelpRef()
                                    .startsWith("https://github.com/UWPR/Comet/blob/v2026.02.2/"),
                    definition.name() + " cites " + definition.detailedHelpRef());
        }
        assertEquals(EnumSet.allOf(VisibilityLevel.class), used);
        assertEquals(VisibilityLevel.ESSENTIALS, definition("database_name").visibility());
        assertEquals(VisibilityLevel.ESSENTIALS, definition("num_enzyme_termini").visibility());
    }
}
