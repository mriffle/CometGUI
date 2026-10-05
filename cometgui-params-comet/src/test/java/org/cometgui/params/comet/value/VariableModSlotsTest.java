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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.schema.CuratedMetadata;
import org.cometgui.params.comet.schema.MetadataLoader;
import org.cometgui.params.comet.schema.ParameterDefinition;
import org.cometgui.params.comet.schema.ResidueAlphabet;
import org.cometgui.params.comet.schema.VariableModField;
import org.cometgui.params.comet.schema.VariableModLayout;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * A slot's value changed one part at a time, through the release's layout and alphabet: every
 * expected tuple below is typed by hand, and each release-dependent fact is checked on both offered
 * releases (2026.03.0 and 2026.02.2) and, where its layout differs, on 2024.01.0.
 */
class VariableModSlotsTest {

    private static final CuratedMetadata METADATA = MetadataLoader.loadBundled();

    private static final String OXIDATION = "15.9949 M 0 3 -1 0 0 0.0";

    private static final String EVERYTHING = "79.966331 STY 3 2,4 5 2 1 97.976896,79.966331";

    private static final List<VariableModPart> EIGHT_FIELDS_WITH_PAIRS =
            List.of(
                    VariableModPart.MASS,
                    VariableModPart.RESIDUES,
                    VariableModPart.BINARY_GROUP,
                    VariableModPart.MINIMUM_COUNT,
                    VariableModPart.MAXIMUM_COUNT,
                    VariableModPart.TERMINAL_DISTANCE,
                    VariableModPart.TERMINUS,
                    VariableModPart.REQUIRED,
                    VariableModPart.NEUTRAL_LOSS,
                    VariableModPart.SECOND_NEUTRAL_LOSS);

    private static VariableModSlots slots(String release) {
        return VariableModSlots.forRelease(METADATA, ToolVersion.parse(release));
    }

    private static VariableModCodec codec(String release) {
        return VariableModCodec.forVersion(METADATA, ToolVersion.parse(release));
    }

    private static VariableModification read(String release, String text) {
        return codec(release).parse("variable_mod03", text);
    }

    /** Sets one part of {@code start} and writes the result through the same release's codec. */
    private static String set(String release, String start, VariableModPart part, String text) {
        VariableModification changed =
                slots(release).withPart("variable_mod03", read(release, start), part, text);
        return codec(release).format(changed);
    }

    @Nested
    @DisplayName("the release's slots, parts and unused value")
    class TheRelease {

        @ParameterizedTest(name = "Comet {0}")
        @ValueSource(strings = {"2026.03.0", "2026.02.2"})
        @DisplayName("fifteen slots, ten parts in layout order, unused = 0.0 X 0 3 -1 0 0 0.0")
        void offeredReleases(String release) {
            VariableModSlots slots = slots(release);
            List<String> expected = new ArrayList<>();
            for (int slot = 1; slot <= 15; slot++) {
                expected.add(String.format(java.util.Locale.ROOT, "variable_mod%02d", slot));
            }
            assertEquals(expected, slots.slots());
            assertEquals("variable_mod01", slots.slots().get(0));
            assertEquals("variable_mod15", slots.slots().get(14));
            assertEquals(EIGHT_FIELDS_WITH_PAIRS, slots.parts());
            assertEquals("0.0 X 0 3 -1 0 0 0.0", codec(release).format(slots.unused()));
            assertSame(codec(release).layout().residueAlphabet(), slots.alphabet());
            assertEquals(codec(release).layout(), slots.layout());
        }

        @Test
        @DisplayName("2024.01.0 takes one neutral loss, so it has no second-loss part")
        void olderReleaseHasNoSecondLoss() {
            assertEquals(EIGHT_FIELDS_WITH_PAIRS.subList(0, 9), slots("2024.01.0").parts());
        }

        @Test
        @DisplayName("the residue alphabet is the release's: ^ and $ only in 2026.03.0")
        void alphabets() {
            assertEquals(
                    "ABCDEFGHIJKLMNOPQRSTUVWXYZnc^$", slots("2026.03.0").alphabet().characters());
            assertEquals(
                    "ABCDEFGHIJKLMNOPQRSTUVWXYZnc", slots("2026.02.2").alphabet().characters());
        }

        @Test
        @DisplayName("a constructed layout's parts follow its order and its pairs")
        void constructedLayout() {
            VariableModSlots slots = constructed(false);
            assertEquals(
                    List.of(
                            VariableModPart.RESIDUES,
                            VariableModPart.MASS,
                            VariableModPart.MAXIMUM_COUNT,
                            VariableModPart.NEUTRAL_LOSS),
                    slots.parts());
            assertEquals(
                    List.of(
                            VariableModPart.RESIDUES,
                            VariableModPart.MASS,
                            VariableModPart.MINIMUM_COUNT,
                            VariableModPart.MAXIMUM_COUNT,
                            VariableModPart.NEUTRAL_LOSS,
                            VariableModPart.SECOND_NEUTRAL_LOSS),
                    constructed(true).parts());
        }

        @Test
        @DisplayName("a release whose metadata curates no unused slot default is refused")
        void noUnusedDefault() {
            ToolVersion version = ToolVersion.parse("2026.02.2");
            ParameterDefinition first = METADATA.parameter("variable_mod01", version).orElseThrow();
            CuratedMetadata onlyTheActiveSlot =
                    new CuratedMetadata(
                            METADATA.schemaVersion(),
                            METADATA.versions(),
                            List.of(first),
                            METADATA.internal(),
                            METADATA.enzymeTable());
            assertEquals(
                    "the metadata curates no unused variable-modification slot for Comet"
                            + " 2026.02.2",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () -> VariableModSlots.forRelease(onlyTheActiveSlot, version))
                            .getMessage());
        }

        @Test
        @DisplayName("an unused value must be unused and writable")
        void unusedValueRefusals() {
            VariableModCodec codec = codec("2026.02.2");
            assertEquals(
                    "an unused slot's value has a mass difference of 0, not 15.9949",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () -> new VariableModSlots(codec, read("2026.02.2", OXIDATION)))
                            .getMessage());
            VariableModification caret = read("2026.03.0", "0.0 ^ 0 3 -1 0 0 0.0");
            assertEquals(
                    "the unused value cannot be written: \"^\" holds '^', which Comet 2026.02.2"
                            + " does not accept in a residue token; its residue alphabet is A-Z, n"
                            + " (N-terminus), c (C-terminus), so it cannot be written",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () -> new VariableModSlots(codec, caret))
                            .getMessage());
        }
    }

    @Nested
    @DisplayName("a part's text")
    class PartText {

        @ParameterizedTest(name = "Comet {0}")
        @ValueSource(strings = {"2026.03.0", "2026.02.2"})
        @DisplayName("every part of the everything-at-once form")
        void everyPart(String release) {
            VariableModSlots slots = slots(release);
            VariableModification value = read(release, EVERYTHING);
            List<String> texts = new ArrayList<>();
            for (VariableModPart part : slots.parts()) {
                texts.add(slots.partText(value, part));
            }
            assertEquals(
                    List.of(
                            "79.966331",
                            "STY",
                            "3",
                            "2",
                            "4",
                            "5",
                            "2",
                            "1",
                            "97.976896",
                            "79.966331"),
                    texts);
        }

        @Test
        @DisplayName("an absent minimum and second loss are empty; the scale is kept")
        void absentHalves() {
            VariableModSlots slots = slots("2026.02.2");
            VariableModification value = read("2026.02.2", "15.99490 M 0 3 -1 0 0 0.0");
            assertEquals("", slots.partText(value, VariableModPart.MINIMUM_COUNT));
            assertEquals("", slots.partText(value, VariableModPart.SECOND_NEUTRAL_LOSS));
            assertEquals("15.99490", slots.partText(value, VariableModPart.MASS));
            assertEquals("0.0", slots.partText(value, VariableModPart.NEUTRAL_LOSS));
        }

        @Test
        @DisplayName("a part the layout lacks has no text")
        void partTheLayoutLacks() {
            VariableModification value = read("2024.01.0", OXIDATION);
            assertEquals(
                    "Comet 2024.01.0 has no second neutral loss in its tuple",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () ->
                                            slots("2024.01.0")
                                                    .partText(
                                                            value,
                                                            VariableModPart.SECOND_NEUTRAL_LOSS))
                            .getMessage());
        }
    }

    @Nested
    @DisplayName("setting one part")
    class SettingAPart {

        @ParameterizedTest(name = "Comet {0}")
        @ValueSource(strings = {"2026.03.0", "2026.02.2"})
        @DisplayName("each part changes its own field and nothing else")
        void eachPart(String release) {
            assertEquals(
                    "15.994915 M 0 3 -1 0 0 0.0",
                    set(release, OXIDATION, VariableModPart.MASS, "15.994915"));
            assertEquals(
                    "15.9949 STY 0 3 -1 0 0 0.0",
                    set(release, OXIDATION, VariableModPart.RESIDUES, "STY"));
            assertEquals(
                    "15.9949 M 2 3 -1 0 0 0.0",
                    set(release, OXIDATION, VariableModPart.BINARY_GROUP, "2"));
            assertEquals(
                    "15.9949 M 0 1,3 -1 0 0 0.0",
                    set(release, OXIDATION, VariableModPart.MINIMUM_COUNT, "1"));
            assertEquals(
                    "15.9949 M 0 5 -1 0 0 0.0",
                    set(release, OXIDATION, VariableModPart.MAXIMUM_COUNT, "5"));
            assertEquals(
                    "15.9949 M 0 1,5 -1 0 0 0.0",
                    set(release, "15.9949 M 0 1,3 -1 0 0 0.0", VariableModPart.MAXIMUM_COUNT, "5"));
            assertEquals(
                    "15.9949 M 0 3 -1 0 0 0.0",
                    set(release, "15.9949 M 0 1,3 -1 0 0 0.0", VariableModPart.MINIMUM_COUNT, ""));
            assertEquals(
                    "15.9949 M 0 3 7 0 0 0.0",
                    set(release, OXIDATION, VariableModPart.TERMINAL_DISTANCE, "7"));
            assertEquals(
                    "15.9949 M 0 3 -1 3 0 0.0",
                    set(release, OXIDATION, VariableModPart.TERMINUS, "3"));
            assertEquals(
                    "15.9949 M 0 3 -1 0 -1 0.0",
                    set(release, OXIDATION, VariableModPart.REQUIRED, "-1"));
            assertEquals(
                    "15.9949 M 0 3 -1 0 0 63.998285",
                    set(release, OXIDATION, VariableModPart.NEUTRAL_LOSS, "63.998285"));
            assertEquals(
                    "15.9949 M 0 3 -1 0 0 0.0,63.998285",
                    set(release, OXIDATION, VariableModPart.SECOND_NEUTRAL_LOSS, "63.998285"));
            assertEquals(
                    "15.9949 M 0 3 -1 0 0 1.5,79.966331",
                    set(
                            release,
                            "15.9949 M 0 3 -1 0 0 97.976896,79.966331",
                            VariableModPart.NEUTRAL_LOSS,
                            "1.5"));
            assertEquals(
                    "15.9949 M 0 3 -1 0 0 97.976896",
                    set(
                            release,
                            "15.9949 M 0 3 -1 0 0 97.976896,79.966331",
                            VariableModPart.SECOND_NEUTRAL_LOSS,
                            " "));
        }

        @Test
        @DisplayName("surrounding white space is ignored and a number is kept as entered")
        void whiteSpaceAndScale() {
            assertEquals(
                    "15.99491500 M 0 3 -1 0 0 0.0",
                    set("2026.02.2", OXIDATION, VariableModPart.MASS, "  15.99491500 "));
            assertEquals(
                    "-17.026549 M 0 3 -1 0 0 0.0",
                    set("2026.02.2", OXIDATION, VariableModPart.MASS, "-17.026549"));
        }

        @Test
        @DisplayName("text a field cannot hold is refused, naming the slot and the part")
        void refusals() {
            VariableModSlots slots = slots("2026.02.2");
            VariableModification value = read("2026.02.2", OXIDATION);
            assertEquals(
                    "variable_mod04, mass difference: \"abc\" is not a number",
                    refusal(slots, "variable_mod04", value, VariableModPart.MASS, "abc"));
            assertEquals(
                    "variable_mod04, maximum count per peptide: \"2.5\" is not a whole number",
                    refusal(slots, "variable_mod04", value, VariableModPart.MAXIMUM_COUNT, "2.5"));
            assertEquals(
                    "variable_mod04, minimum count per peptide: \"2,4\" is not a whole number",
                    refusal(slots, "variable_mod04", value, VariableModPart.MINIMUM_COUNT, "2,4"));
            assertEquals(
                    "variable_mod04, maximum count per peptide: \"\" is not one value; enter one"
                            + " number",
                    refusal(slots, "variable_mod04", value, VariableModPart.MAXIMUM_COUNT, ""));
            assertEquals(
                    "variable_mod04, neutral loss: \"1 2\" is not one value; enter one number",
                    refusal(slots, "variable_mod04", value, VariableModPart.NEUTRAL_LOSS, "1 2"));
            assertEquals(
                    "variable_mod04, second neutral loss: \"1 2\" is not one value; enter one"
                            + " number, or nothing for none",
                    refusal(
                            slots,
                            "variable_mod04",
                            value,
                            VariableModPart.SECOND_NEUTRAL_LOSS,
                            "1 2"));
            assertEquals(
                    "variable_mod04, residues: \"S T\" is not one value; enter one residue token",
                    refusal(slots, "variable_mod04", value, VariableModPart.RESIDUES, "S T"));
            assertEquals(
                    "variable_mod04, residues: \"K#\" holds '#'; a residue token is letters A-Z"
                            + " and the terminal codes n, c, ^ and $",
                    refusal(slots, "variable_mod04", value, VariableModPart.RESIDUES, "K#"));
            assertEquals(
                    "variable_mod04, terminus: \"x\" is not a whole number",
                    refusal(slots, "variable_mod04", value, VariableModPart.TERMINUS, "x"));
            assertEquals(
                    "variable_mod04, required, optional or exclusive: \"y\" is not a whole number",
                    refusal(slots, "variable_mod04", value, VariableModPart.REQUIRED, "y"));
            assertEquals(
                    "variable_mod04, terminal distance: \"z\" is not a whole number",
                    refusal(
                            slots,
                            "variable_mod04",
                            value,
                            VariableModPart.TERMINAL_DISTANCE,
                            "z"));
            assertEquals(
                    "variable_mod04, binary group: \"w\" is not a whole number",
                    refusal(slots, "variable_mod04", value, VariableModPart.BINARY_GROUP, "w"));
        }

        @Test
        @DisplayName("^ and $ are a 2026.03.0 residue token and refused in 2026.02.2")
        void proteinTerminusCodesAreVersionScoped() {
            assertEquals(
                    "42.010565 ^M 0 3 -1 0 0 0.0",
                    set("2026.03.0", "42.010565 M 0 3 -1 0 0 0.0", VariableModPart.RESIDUES, "^M"));
            VariableModification value = read("2026.02.2", OXIDATION);
            assertEquals(
                    "variable_mod03, residues: \"^M\" holds '^', which Comet 2026.02.2 does not"
                            + " accept in a residue token; its residue alphabet is A-Z, n"
                            + " (N-terminus), c (C-terminus)",
                    refusal(
                            slots("2026.02.2"),
                            "variable_mod03",
                            value,
                            VariableModPart.RESIDUES,
                            "^M"));
        }

        @Test
        @DisplayName("a part the release's layout lacks is refused naming the layout")
        void partTheLayoutLacks() {
            VariableModification value = read("2024.01.0", OXIDATION);
            assertEquals(
                    "variable_mod02, second neutral loss: Comet 2024.01.0 has no second neutral"
                            + " loss in its variable-modification tuple, whose fields are mass"
                            + " difference, residues, binary group, count per peptide[,pair],"
                            + " terminal distance, terminus, required, neutral loss",
                    refusal(
                            slots("2024.01.0"),
                            "variable_mod02",
                            value,
                            VariableModPart.SECOND_NEUTRAL_LOSS,
                            "79.966331"));
            VariableModification constructedValue = read("2026.02.2", "15.9949 M 0 3 -1 0 0 0.0");
            assertEquals(
                    "variable_mod01, minimum count per peptide: this Comet version has no minimum"
                            + " count per peptide in its variable-modification tuple, whose fields"
                            + " are residues, mass difference, count per peptide, neutral loss",
                    refusal(
                            constructed(false),
                            "variable_mod01",
                            constructedValue,
                            VariableModPart.MINIMUM_COUNT,
                            "1"));
        }

        @Test
        @DisplayName("a slot the release does not have is refused")
        void unknownSlot() {
            VariableModification value = read("2026.02.2", OXIDATION);
            assertEquals(
                    "\"variable_mod16\" is not a variable-modification slot of Comet 2026.02.2",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () ->
                                            slots("2026.02.2")
                                                    .withPart(
                                                            "variable_mod16",
                                                            value,
                                                            VariableModPart.MASS,
                                                            "1.0"))
                            .getMessage());
            assertEquals(
                    "\"variable_mod16\" is not a variable-modification slot of Comet 2026.03.0",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () ->
                                            slots("2026.03.0")
                                                    .withResidue(
                                                            "variable_mod16", value, 'K', true))
                            .getMessage());
        }
    }

    @Nested
    @DisplayName("the residue multi-select")
    class ResidueSelection {

        @ParameterizedTest(name = "Comet {0}")
        @ValueSource(strings = {"2026.03.0", "2026.02.2"})
        @DisplayName("letters and n/c are added and cleared, written n first and c last")
        void lettersAndPeptideTermini(String release) {
            VariableModSlots slots = slots(release);
            VariableModCodec codec = codec(release);
            VariableModification value = read(release, OXIDATION);
            VariableModification withS = slots.withResidue("variable_mod05", value, 'S', true);
            assertEquals("15.9949 MS 0 3 -1 0 0 0.0", codec.format(withS));
            VariableModification withN = slots.withResidue("variable_mod05", withS, 'n', true);
            assertEquals("15.9949 nMS 0 3 -1 0 0 0.0", codec.format(withN));
            VariableModification withC = slots.withResidue("variable_mod05", withN, 'c', true);
            assertEquals("15.9949 nMSc 0 3 -1 0 0 0.0", codec.format(withC));
            VariableModification withoutM = slots.withResidue("variable_mod05", withC, 'M', false);
            assertEquals("15.9949 nSc 0 3 -1 0 0 0.0", codec.format(withoutM));
            assertSame(withoutM, slots.withResidue("variable_mod05", withoutM, 'S', true));
            assertSame(withoutM, slots.withResidue("variable_mod05", withoutM, 'K', false));
        }

        @Test
        @DisplayName("an imported token is rewritten in the editor's order once it is edited")
        void rewritten() {
            VariableModification value = read("2026.02.2", "42.010565 Kn 0 3 -1 0 0 0.0");
            assertEquals(
                    "42.010565 nKR 0 3 -1 0 0 0.0",
                    codec("2026.02.2")
                            .format(
                                    slots("2026.02.2")
                                            .withResidue("variable_mod01", value, 'R', true)));
        }

        @Test
        @DisplayName("^ and $ are offered by 2026.03.0 and refused by 2026.02.2")
        void proteinTermini() {
            VariableModification value = read("2026.03.0", OXIDATION);
            VariableModification caret =
                    slots("2026.03.0").withResidue("variable_mod02", value, '^', true);
            VariableModification both =
                    slots("2026.03.0").withResidue("variable_mod02", caret, '$', true);
            assertEquals("15.9949 ^M$ 0 3 -1 0 0 0.0", codec("2026.03.0").format(both));
            assertEquals(
                    "variable_mod02, residues: '^' is not offered: Comet 2026.02.2 does not accept"
                            + " it in a residue token; its residue alphabet is A-Z, n (N-terminus),"
                            + " c (C-terminus)",
                    assertThrows(
                                    ValueSyntaxException.class,
                                    () ->
                                            slots("2026.02.2")
                                                    .withResidue(
                                                            "variable_mod02",
                                                            read("2026.02.2", OXIDATION),
                                                            '^',
                                                            true))
                            .getMessage());
        }

        @Test
        @DisplayName("clearing the last residue is refused")
        void lastResidue() {
            VariableModification value = read("2026.02.2", OXIDATION);
            assertEquals(
                    "variable_mod06, residues: a modification applies to at least one residue or"
                            + " terminus; remove the modification to leave the slot unused",
                    assertThrows(
                                    ValueSyntaxException.class,
                                    () ->
                                            slots("2026.02.2")
                                                    .withResidue(
                                                            "variable_mod06", value, 'M', false))
                            .getMessage());
        }
    }

    @Nested
    @DisplayName("choices, explanations and what a release can hold")
    class Words {

        @Test
        @DisplayName("the four termini and the three requirements, with their codes")
        void choices() {
            VariableModSlots slots = slots("2026.02.2");
            assertEquals(
                    List.of(
                            new VariableModChoice(
                                    "0",
                                    "protein N-terminus",
                                    "A terminal distance of 0 or more counts from the protein"
                                            + " N-terminus."),
                            new VariableModChoice(
                                    "1",
                                    "protein C-terminus",
                                    "A terminal distance of 0 or more counts from the protein"
                                            + " C-terminus."),
                            new VariableModChoice(
                                    "2",
                                    "peptide N-terminus",
                                    "A terminal distance of 0 or more counts from the peptide"
                                            + " N-terminus."),
                            new VariableModChoice(
                                    "3",
                                    "peptide C-terminus",
                                    "A terminal distance of 0 or more counts from the peptide"
                                            + " C-terminus.")),
                    slots.choices(VariableModPart.TERMINUS));
            assertEquals(
                    List.of(
                            new VariableModChoice(
                                    "0",
                                    "optional",
                                    "Peptides are analysed with and without the modification; it"
                                            + " is not forced to be present."),
                            new VariableModChoice(
                                    "1",
                                    "required",
                                    "Only peptides that contain the modification are analysed."),
                            new VariableModChoice(
                                    "-1",
                                    "exclusive",
                                    "Only one of the set of exclusive modifications can appear in"
                                            + " a peptide.")),
                    slots.choices(VariableModPart.REQUIRED));
            for (VariableModPart part : VariableModPart.values()) {
                if (part != VariableModPart.TERMINUS && part != VariableModPart.REQUIRED) {
                    assertEquals(List.of(), slots.choices(part), part.name());
                }
            }
        }

        @Test
        @DisplayName("every part has an explanation; three typed out")
        void explanations() {
            for (VariableModPart part : VariableModPart.values()) {
                assertTrue(VariableModSlots.explanation(part).endsWith("."), part.name());
            }
            assertEquals(
                    "-1: no distance constraint. -2: anywhere except the peptide's C-terminal"
                            + " residue. 0: only the terminal residue. N: the terminal residue"
                            + " through the next N residues.",
                    VariableModSlots.explanation(VariableModPart.TERMINAL_DISTANCE));
            assertEquals(
                    "The mass difference the modification adds; 0.0 leaves the slot unused.",
                    VariableModSlots.explanation(VariableModPart.MASS));
            assertEquals(
                    "A second fragment neutral loss; empty for none.",
                    VariableModSlots.explanation(VariableModPart.SECOND_NEUTRAL_LOSS));
        }

        @Test
        @DisplayName("parts name their field and which are optional halves")
        void parts() {
            assertEquals(VariableModField.COUNT, VariableModPart.MINIMUM_COUNT.field());
            assertEquals(VariableModField.COUNT, VariableModPart.MAXIMUM_COUNT.field());
            assertEquals(
                    VariableModField.NEUTRAL_LOSS, VariableModPart.SECOND_NEUTRAL_LOSS.field());
            List<VariableModPart> halves = new ArrayList<>();
            for (VariableModPart part : VariableModPart.values()) {
                if (part.optionalHalf()) {
                    halves.add(part);
                }
            }
            assertEquals(
                    List.of(VariableModPart.MINIMUM_COUNT, VariableModPart.SECOND_NEUTRAL_LOSS),
                    halves);
            assertEquals("maximum count per peptide", VariableModPart.MAXIMUM_COUNT.label());
        }

        @Test
        @DisplayName("a ^ value can be held by 2026.03.0 and not by 2026.02.2")
        void unwritable() {
            VariableModification caret = read("2026.03.0", "42.010565 ^ 0 1 -1 0 0 0.0");
            assertEquals(Optional.empty(), slots("2026.03.0").unwritable(caret));
            assertEquals(
                    Optional.of(
                            "\"^\" holds '^', which Comet 2026.02.2 does not accept in a residue"
                                    + " token; its residue alphabet is A-Z, n (N-terminus), c"
                                    + " (C-terminus), so it cannot be written"),
                    slots("2026.02.2").unwritable(caret));
            VariableModification twoLosses =
                    read("2026.02.2", "79.966331 STY 0 3 -1 0 0 97.976896,79.966331");
            assertEquals(
                    Optional.of(
                            "this Comet version's neutral loss field takes one value, so two"
                                    + " neutral losses cannot be written under it"),
                    slots("2024.01.0").unwritable(twoLosses));
        }
    }

    private static String refusal(
            VariableModSlots slots,
            String slot,
            VariableModification value,
            VariableModPart part,
            String text) {
        return assertThrows(
                        ValueSyntaxException.class, () -> slots.withPart(slot, value, part, text))
                .getMessage();
    }

    /** A CONSTRUCTED four-field layout in an order no release uses. */
    private static VariableModSlots constructed(boolean pairs) {
        VariableModLayout layout =
                new VariableModLayout(
                        List.of(
                                new VariableModLayout.Entry(
                                        VariableModField.RESIDUES,
                                        VariableModField.Kind.RESIDUES,
                                        false),
                                new VariableModLayout.Entry(
                                        VariableModField.MASS,
                                        VariableModField.Kind.DECIMAL,
                                        false),
                                new VariableModLayout.Entry(
                                        VariableModField.COUNT,
                                        VariableModField.Kind.INTEGER,
                                        pairs),
                                new VariableModLayout.Entry(
                                        VariableModField.NEUTRAL_LOSS,
                                        VariableModField.Kind.DECIMAL,
                                        pairs)),
                        "https://example.org/constructed",
                        new ResidueAlphabet("MXn", "https://example.org/constructed"));
        VariableModCodec codec = new VariableModCodec(layout, List.of("variable_mod01"));
        VariableModification unused =
                new VariableModification(
                        new BigDecimal("0.0"),
                        "X",
                        0,
                        OptionalInt.empty(),
                        0,
                        -1,
                        0,
                        0,
                        List.of(new BigDecimal("0.0")));
        return new VariableModSlots(codec, unused);
    }
}
