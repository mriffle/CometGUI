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

package org.cometgui.params.comet.value;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.fixtures.CometFixtures;
import org.cometgui.params.comet.parser.ParamsLine;
import org.cometgui.params.comet.parser.ParamsLineReader;
import org.cometgui.params.comet.schema.CuratedMetadata;
import org.cometgui.params.comet.schema.MetadataLoader;
import org.cometgui.params.comet.schema.ParameterDefinition;
import org.cometgui.params.comet.schema.ValueKind;
import org.cometgui.params.comet.schema.VariableModField;
import org.cometgui.params.comet.schema.VariableModLayout;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** The tuple codec: driven by the layout, refusing what it cannot read, citing slot and field. */
class VariableModCodecTest {

    private static final CuratedMetadata METADATA = MetadataLoader.loadBundled();

    private static final ToolVersion COMET = ToolVersion.parse("2026.02.2");

    private static final VariableModCodec CODEC = VariableModCodec.forVersion(METADATA, COMET);

    private static ValueSyntaxException refused(String text, String field, String fragment) {
        ValueSyntaxException failure =
                assertThrows(ValueSyntaxException.class, () -> CODEC.parse("variable_mod04", text));
        assertEquals("variable_mod04", failure.subject(), failure.getMessage());
        assertEquals(field, failure.field(), failure.getMessage());
        assertTrue(
                failure.getMessage().contains(fragment),
                () -> "expected \"" + fragment + "\" in: " + failure.getMessage());
        assertTrue(failure.getMessage().startsWith("variable_mod04, " + field + ": "));
        return failure;
    }

    private static VariableModLayout.Entry entry(VariableModField field, boolean pair) {
        return new VariableModLayout.Entry(field, field.kind(), pair);
    }

    private static VariableModLayout layout(VariableModLayout.Entry... entries) {
        return new VariableModLayout(List.of(entries), "https://example.org/constructed");
    }

    @Nested
    @DisplayName("the bundled 2026.02.2 layout")
    class Bundled {

        @Test
        @DisplayName("is Comet's eight fields in order, with pairs on the count and neutral loss")
        void isCometsEightFields() {
            VariableModLayout layout = CODEC.layout();
            assertEquals(
                    List.of(
                            entry(VariableModField.MASS, false),
                            entry(VariableModField.RESIDUES, false),
                            entry(VariableModField.BINARY_GROUP, false),
                            entry(VariableModField.COUNT, true),
                            entry(VariableModField.TERMINAL_DISTANCE, false),
                            entry(VariableModField.TERMINUS, false),
                            entry(VariableModField.REQUIRED, false),
                            entry(VariableModField.NEUTRAL_LOSS, true)),
                    layout.fields());
            assertEquals(
                    "https://github.com/UWPR/Comet/blob/v2026.02.2/Comet.cpp#L552-L621",
                    layout.source());
        }

        @Test
        @DisplayName("every curated tuple default reads and writes back unchanged")
        void everyCuratedDefaultRoundTrips() {
            int checked = 0;
            for (ParameterDefinition definition : METADATA.parametersFor(COMET)) {
                if (definition.kind() == ValueKind.VARIABLE_MOD_TUPLE) {
                    VariableModification value =
                            CODEC.parse(definition.name(), definition.defaultValue());
                    assertEquals(definition.defaultValue(), CODEC.format(value));
                    checked++;
                }
            }
            assertEquals(15, checked);
        }

        @Test
        @DisplayName("the fifteen slots of the real comet -q output read and write back unchanged")
        void theRealDumpsSlotsRoundTrip() throws IOException {
            String text =
                    new String(
                            CometFixtures.bytes(
                                    CometFixtures.COMET_2026_02_2,
                                    CometFixtures.LINUX_X86_64,
                                    CometFixtures.Mode.COMPLETE),
                            StandardCharsets.UTF_8);
            List<String> slots = new ArrayList<>();
            for (ParamsLine.Declaration declaration : ParamsLineReader.read(text).declarations()) {
                if (declaration.name().startsWith("variable_mod")) {
                    VariableModification value =
                            CODEC.parse(declaration.name(), declaration.value());
                    assertEquals(declaration.value(), CODEC.format(value));
                    slots.add(declaration.name());
                    if (declaration.name().equals("variable_mod01")) {
                        assertEquals(new BigDecimal("15.9949"), value.mass());
                        assertEquals("M", value.residues());
                        assertEquals(3, value.maximumCount());
                        assertTrue(value.minimumCount().isEmpty());
                    } else {
                        assertTrue(value.isUnused(), declaration.name());
                    }
                }
            }
            assertEquals(CODEC.slots(), slots);
        }

        @Test
        @DisplayName("a codec cannot be had for a version the metadata was not curated against")
        void anUncuratedVersionIsRefused() {
            IllegalArgumentException failure =
                    assertThrows(
                            IllegalArgumentException.class,
                            () ->
                                    VariableModCodec.forVersion(
                                            METADATA, ToolVersion.parse("2025.03.0")));
            assertEquals(
                    "the metadata was not curated against Comet 2025.03.0", failure.getMessage());
        }

        @Test
        @DisplayName("the slots are the version's tuple parameters and nothing else")
        void theSlotsAreTheTupleParameters() {
            VariableModCodec built = VariableModCodec.forVersion(METADATA, COMET);
            assertEquals(15, built.slots().size());
            assertEquals("variable_mod01", built.slots().get(0));
            assertEquals("variable_mod15", built.slots().get(14));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> built.parse("use_A_ions", "15.9949 M 0 3 -1 0 0 0.0"));
            assertThrows(UnsupportedOperationException.class, () -> built.slots().clear());
        }

        @Test
        @DisplayName("a codec needs at least one slot")
        void noSlotsIsRefused() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new VariableModCodec(CODEC.layout(), List.of()));
        }
    }

    @Nested
    @DisplayName("text that is not a tuple is refused, naming slot and field")
    class Refusals {

        @Test
        void aSlotTheVersionDoesNotHave() {
            IllegalArgumentException failure =
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> CODEC.parse("variable_mod16", "15.9949 M 0 3 -1 0 0 0.0"));
            assertEquals(
                    "\"variable_mod16\" is not a variable-modification slot of this Comet"
                            + " version; its slots are variable_mod01 to variable_mod15",
                    failure.getMessage());
        }

        @Test
        void oneFieldShort() {
            refused(
                    "15.9949 M 0 3 -1 0 0",
                    "the tuple",
                    "\"15.9949 M 0 3 -1 0 0\" holds 7 fields, and this Comet version's tuple"
                            + " holds 8: mass difference, residues, binary group, count per"
                            + " peptide[,pair], terminal distance, terminus, required, neutral"
                            + " loss[,pair]");
        }

        @Test
        void oneFieldTooMany() {
            refused("15.9949 M 0 3 -1 0 0 0.0 1", "the tuple", "holds 9 fields");
        }

        @Test
        void empty() {
            refused("", "the tuple", "\"\" holds 0 fields");
            refused("   ", "the tuple", "\"\" holds 0 fields");
        }

        @Test
        void aSpaceInsideAPairIsTwoFields() {
            refused("79.966331 STY 0 3 -1 0 0 97.976896, 79.966331", "the tuple", "holds 9");
        }

        @Test
        void aMassThatIsNotANumber() {
            refused("oxidation M 0 3 -1 0 0 0.0", "field 1 (mass difference)", "not a number");
        }

        @Test
        void aCommaDecimalMass() {
            refused(
                    "15,9949 M 0 3 -1 0 0 0.0",
                    "field 1 (mass difference)",
                    "\"15,9949\" is a comma pair, and this Comet version takes one value here");
        }

        @Test
        void aResidueTokenWithADigit() {
            refused("15.9949 M1 0 3 -1 0 0 0.0", "field 2 (residues)", "holds '1'");
        }

        @Test
        void aLowerCaseResidue() {
            refused("15.9949 m 0 3 -1 0 0 0.0", "field 2 (residues)", "holds 'm'");
        }

        @Test
        void aBinaryGroupThatIsNotWhole() {
            refused("15.9949 M 0.5 3 -1 0 0 0.0", "field 3 (binary group)", "not a whole");
        }

        @Test
        void aPairInAFieldThatTakesOne() {
            refused(
                    "15.9949 M 1,2 3 -1 0 0 0.0",
                    "field 3 (binary group)",
                    "\"1,2\" is a comma pair, and this Comet version takes one value here");
        }

        @Test
        void aCountPairWithThreeValues() {
            refused(
                    "15.9949 M 0 2,4,6 -1 0 0 0.0",
                    "field 4 (count per peptide)",
                    "\"2,4,6\" is not a pair");
        }

        @Test
        void aCountPairWithAnEmptyHalf() {
            refused("15.9949 M 0 2, -1 0 0 0.0", "field 4 (count per peptide)", "not a pair");
            refused("15.9949 M 0 ,4 -1 0 0 0.0", "field 4 (count per peptide)", "not a pair");
        }

        @Test
        void aCountPairWithADecimal() {
            refused(
                    "15.9949 M 0 2,4.5 -1 0 0 0.0",
                    "field 4 (count per peptide)",
                    "\"4.5\" is not a whole number");
            refused(
                    "15.9949 M 0 x,4 -1 0 0 0.0",
                    "field 4 (count per peptide)",
                    "\"x\" is not a whole number");
        }

        @Test
        void aDistanceTooLargeForAnInt() {
            refused(
                    "15.9949 M 0 3 99999999999 0 0 0.0",
                    "field 5 (terminal distance)",
                    "too large for Comet's integer");
        }

        @Test
        void aTerminusThatIsNotWhole() {
            refused("15.9949 M 0 3 -1 N 0 0.0", "field 6 (terminus)", "\"N\" is not a whole");
        }

        @Test
        void aRequirementThatIsNotWhole() {
            refused("15.9949 M 0 3 -1 0 yes 0.0", "field 7 (required)", "not a whole number");
        }

        @Test
        void aNeutralLossThatIsNotANumber() {
            refused(
                    "79.966331 STY 0 3 -1 0 0 H3PO4",
                    "field 8 (neutral loss)",
                    "\"H3PO4\" is not a number");
        }

        @Test
        void aSecondNeutralLossThatIsNotANumber() {
            refused(
                    "79.966331 STY 0 3 -1 0 0 97.976896,HPO3",
                    "field 8 (neutral loss)",
                    "\"HPO3\" is not a number");
        }

        @Test
        void threeNeutralLosses() {
            refused(
                    "79.966331 STY 0 3 -1 0 0 97.976896,79.966331,18.010565",
                    "field 8 (neutral loss)",
                    "is not a pair");
        }
    }

    @Nested
    @DisplayName("the layout, not the code, decides the shape (CONSTRUCTED layouts)")
    class LayoutDriven {

        /** A seven-field layout with no neutral-loss field: CONSTRUCTED, not any real release. */
        private final VariableModCodec sevenFields =
                new VariableModCodec(
                        layout(
                                entry(VariableModField.MASS, false),
                                entry(VariableModField.RESIDUES, false),
                                entry(VariableModField.BINARY_GROUP, false),
                                entry(VariableModField.COUNT, false),
                                entry(VariableModField.TERMINAL_DISTANCE, false),
                                entry(VariableModField.TERMINUS, false),
                                entry(VariableModField.REQUIRED, false)),
                        List.of("variable_mod01"));

        /** A three-field layout in another order: CONSTRUCTED. */
        private final VariableModCodec reordered =
                new VariableModCodec(
                        layout(
                                entry(VariableModField.RESIDUES, false),
                                entry(VariableModField.MASS, false),
                                entry(VariableModField.NEUTRAL_LOSS, true)),
                        List.of("variable_mod01", "variable_mod02"));

        @Test
        @DisplayName("seven fields are read where the layout has seven")
        void sevenFieldsAreRead() {
            VariableModification value =
                    sevenFields.parse("variable_mod01", "15.9949 M 0 3 -1 0 1");
            assertEquals(1, value.requirementCode());
            assertEquals(List.of(new BigDecimal("0.0")), value.neutralLosses());
            assertEquals("15.9949 M 0 3 -1 0 1", sevenFields.format(value));
        }

        @Test
        @DisplayName("the eight-field form 2026.02.2 reads is refused where the layout has seven")
        void eightFieldsAreRefused() {
            ValueSyntaxException failure =
                    assertThrows(
                            ValueSyntaxException.class,
                            () -> sevenFields.parse("variable_mod01", "15.9949 M 0 3 -1 0 0 0.0"));
            assertEquals(
                    "variable_mod01, the tuple: \"15.9949 M 0 3 -1 0 0 0.0\" holds 8 fields, and"
                            + " this Comet version's tuple holds 7: mass difference, residues,"
                            + " binary group, count per peptide, terminal distance, terminus,"
                            + " required",
                    failure.getMessage());
        }

        @Test
        @DisplayName("a min,max count is refused where the layout takes one count")
        void aCountPairIsRefusedWhereTheLayoutTakesOne() {
            ValueSyntaxException failure =
                    assertThrows(
                            ValueSyntaxException.class,
                            () -> sevenFields.parse("variable_mod01", "15.9949 M 0 2,4 -1 0 0"));
            assertEquals("field 4 (count per peptide)", failure.field());
            assertTrue(failure.getMessage().contains("takes one value here"));
        }

        @Test
        @DisplayName("a value the layout cannot hold is refused on writing, not dropped")
        void whatTheLayoutCannotHoldIsNotDropped() {
            VariableModification twoLosses =
                    CODEC.parse("variable_mod01", "79.966331 STY 0 3 -1 0 0 97.976896,79.966331");
            IllegalArgumentException noField =
                    assertThrows(
                            IllegalArgumentException.class, () -> sevenFields.format(twoLosses));
            assertEquals(
                    "this Comet version's tuple has no neutral loss field, so a value whose"
                            + " neutral loss is not Comet's default cannot be written under it",
                    noField.getMessage());
            VariableModification minMax =
                    CODEC.parse("variable_mod01", "79.966331 STY 0 2,4 -1 0 0 0.0");
            IllegalArgumentException noPair =
                    assertThrows(IllegalArgumentException.class, () -> sevenFields.format(minMax));
            assertEquals(
                    "this Comet version's count per peptide field takes one value, so a min,max"
                            + " count cannot be written under it",
                    noPair.getMessage());
            for (String text :
                    List.of(
                            "79.966331 STY 0 3 -1 0 0 97.976896",
                            "79.966331 STY 0 3 -1 0 0 0.0,0.0")) {
                VariableModification value = CODEC.parse("variable_mod01", text);
                assertThrows(IllegalArgumentException.class, () -> sevenFields.format(value), text);
            }
        }

        @Test
        @DisplayName("each field a layout lacks is checked against Comet's default when writing")
        void eachMissingFieldIsChecked() {
            VariableModification defaults =
                    new VariableModification(
                            new BigDecimal("15.9949"),
                            "M",
                            0,
                            OptionalInt.empty(),
                            0,
                            -1,
                            0,
                            0,
                            List.of(new BigDecimal("0.0")));
            assertEquals("M 15.9949 0.0", reordered.format(defaults));
            for (String text :
                    List.of(
                            "15.9949 M 1 0 -1 0 0 0.0",
                            "15.9949 M 0 0,0 -1 0 0 0.0",
                            "15.9949 M 0 3 -1 0 0 0.0",
                            "15.9949 M 0 0 0 0 0 0.0",
                            "15.9949 M 0 0 -1 1 0 0.0",
                            "15.9949 M 0 0 -1 0 1 0.0")) {
                VariableModification value = CODEC.parse("variable_mod01", text);
                assertThrows(IllegalArgumentException.class, () -> reordered.format(value), text);
            }
        }

        @Test
        @DisplayName("the order of the fields is the layout's")
        void orderIsTheLayouts() {
            VariableModification value =
                    reordered.parse("variable_mod02", "STY 79.966331 97.976896,79.966331");
            assertEquals(new BigDecimal("79.966331"), value.mass());
            assertEquals("STY", value.residues());
            assertEquals(
                    List.of(new BigDecimal("97.976896"), new BigDecimal("79.966331")),
                    value.neutralLosses());
            assertEquals(0, value.binaryGroup());
            assertEquals(OptionalInt.empty(), value.minimumCount());
            assertEquals(0, value.maximumCount());
            assertEquals(-1, value.terminalDistance());
            assertEquals(0, value.terminusCode());
            assertEquals(0, value.requirementCode());
            assertEquals("STY 79.966331 97.976896,79.966331", reordered.format(value));
            assertEquals("79.966331 STY 0 0 -1 0 0 97.976896,79.966331", CODEC.format(value));
        }

        @Test
        @DisplayName("two neutral losses are refused where the layout takes one")
        void twoLossesAreRefusedWhereTheLayoutTakesOne() {
            VariableModCodec oneLoss =
                    new VariableModCodec(
                            layout(
                                    entry(VariableModField.MASS, false),
                                    entry(VariableModField.RESIDUES, false),
                                    entry(VariableModField.NEUTRAL_LOSS, false)),
                            List.of("variable_mod01"));
            assertEquals(
                    "field 3 (neutral loss)",
                    assertThrows(
                                    ValueSyntaxException.class,
                                    () -> oneLoss.parse("variable_mod01", "1.0 K 2.0,3.0"))
                            .field());
            VariableModification two = reordered.parse("variable_mod01", "K 1.0 2.0,3.0");
            IllegalArgumentException failure =
                    assertThrows(IllegalArgumentException.class, () -> oneLoss.format(two));
            assertEquals(
                    "this Comet version's neutral loss field takes one value, so two neutral"
                            + " losses cannot be written under it",
                    failure.getMessage());
            assertEquals("1.0 K 2.0", oneLoss.format(oneLoss.parse("variable_mod01", "1.0 K 2.0")));
        }
    }
}
