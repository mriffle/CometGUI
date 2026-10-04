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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.schema.MetadataLoader;
import org.cometgui.params.comet.schema.TerminalCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** The typed tuple value: its shape rules, the meanings it derives, and its summary in words. */
class VariableModificationTest {

    private static final VariableModCodec CODEC =
            VariableModCodec.forVersion(
                    MetadataLoader.loadBundled(), ToolVersion.parse("2026.02.2"));

    /** Comet 2026.03.0's codec: its alphabet holds the protein-terminus codes ^ and $. */
    private static final VariableModCodec CODEC_2026_03 =
            VariableModCodec.forVersion(
                    MetadataLoader.loadBundled(), ToolVersion.parse("2026.03.0"));

    private static VariableModification read(String text) {
        return CODEC.parse("variable_mod01", text);
    }

    private static VariableModification read202603(String text) {
        return CODEC_2026_03.parse("variable_mod01", text);
    }

    private static VariableModification withResidues(String residues) {
        return new VariableModification(
                BigDecimal.ONE,
                residues,
                0,
                OptionalInt.empty(),
                3,
                -1,
                0,
                0,
                List.of(BigDecimal.ZERO));
    }

    private static VariableModification withLosses(List<BigDecimal> losses) {
        return new VariableModification(
                BigDecimal.ONE, "K", 0, OptionalInt.empty(), 3, -1, 0, 0, losses);
    }

    @Nested
    @DisplayName("what cannot be a tuple at all")
    class Shape {

        @Test
        void anEmptyResidueToken() {
            assertEquals(
                    "the residue token is empty",
                    assertThrows(IllegalArgumentException.class, () -> withResidues(""))
                            .getMessage());
        }

        @Test
        void aResidueTokenOutsideTheAlphabet() {
            for (String bad : List.of("K-", "N@", "k", "1", "*", "Ka", "#", "^a")) {
                IllegalArgumentException failure =
                        assertThrows(IllegalArgumentException.class, () -> withResidues(bad), bad);
                assertEquals(
                        "\""
                                + bad
                                + "\" holds '"
                                + bad.chars()
                                        .mapToObj(c -> String.valueOf((char) c))
                                        .filter(c -> !c.matches("[A-Z^]"))
                                        .findFirst()
                                        .orElseThrow()
                                + "'; a residue token is letters A-Z and the terminal codes n, c,"
                                + " ^ and $",
                        failure.getMessage());
            }
            // The type holds every character some release can mean; which release accepts which is
            // the version's alphabet, applied by the codec (VariableModCodecAlphabetTest).
            for (String good :
                    List.of(
                            "A",
                            "Z",
                            "n",
                            "c",
                            "nKc",
                            "ACDEFGHIKLMNPQRSTVWY",
                            "^",
                            "$",
                            "^M",
                            "n^",
                            "^$",
                            "$c",
                            "^^")) {
                assertEquals(good, withResidues(good).residues());
            }
        }

        @Test
        void noneOrThreeNeutralLosses() {
            assertThrows(IllegalArgumentException.class, () -> withLosses(List.of()));
            IllegalArgumentException three =
                    assertThrows(
                            IllegalArgumentException.class,
                            () ->
                                    withLosses(
                                            List.of(
                                                    BigDecimal.ONE,
                                                    BigDecimal.TEN,
                                                    BigDecimal.ONE)));
            assertEquals(
                    "a neutral-loss field holds one value or a pair, not 3", three.getMessage());
            assertEquals(
                    2, withLosses(List.of(BigDecimal.ONE, BigDecimal.TEN)).neutralLosses().size());
        }

        @Test
        void theLossesAreAnImmutableCopy() {
            List<BigDecimal> losses = new ArrayList<>(List.of(BigDecimal.ONE));
            VariableModification value = withLosses(losses);
            losses.add(BigDecimal.TEN);
            assertEquals(List.of(BigDecimal.ONE), value.neutralLosses());
            assertThrows(
                    UnsupportedOperationException.class,
                    () -> value.neutralLosses().add(BigDecimal.TEN));
        }
    }

    @Nested
    @DisplayName("what the fields mean")
    class Meanings {

        @Test
        void residuesAndTermini() {
            VariableModification nk = read("42.010565 nK 0 3 -1 0 0 0.0");
            assertEquals("K", nk.residueLetters());
            assertTrue(nk.nTerminal());
            assertFalse(nk.cTerminal());
            VariableModification c = read("28.0 c 0 3 8 1 0 0.0");
            assertEquals("", c.residueLetters());
            assertFalse(c.nTerminal());
            assertTrue(c.cTerminal());
            VariableModification sty = read("79.966331 STY 0 3 -1 0 0 0.0");
            assertEquals("STY", sty.residueLetters());
            assertFalse(sty.nTerminal());
            assertFalse(sty.cTerminal());
        }

        @Test
        @DisplayName("^ and $ are terminal codes, not residues; n and c take precedence")
        void proteinTerminusCodes() {
            VariableModification protein = read202603("42.010565 ^ 0 1 -1 0 0 0.0");
            assertEquals("", protein.residueLetters());
            assertTrue(protein.nTerminal());
            assertFalse(protein.cTerminal());
            assertEquals(Optional.of(TerminalCode.PROTEIN_N), protein.nTerminus());
            assertEquals(Optional.empty(), protein.cTerminus());
            assertTrue(protein.has(TerminalCode.PROTEIN_N));
            assertFalse(protein.has(TerminalCode.PEPTIDE_N));
            VariableModification amide = read202603("-0.984016 $ 0 1 -1 0 0 0.0");
            assertFalse(amide.nTerminal());
            assertTrue(amide.cTerminal());
            assertEquals(Optional.empty(), amide.nTerminus());
            assertEquals(Optional.of(TerminalCode.PROTEIN_C), amide.cTerminus());
            VariableModification both = read202603("42.010565 ^M$ 0 1 -1 0 0 0.0");
            assertEquals("M", both.residueLetters());
            assertEquals(Optional.of(TerminalCode.PROTEIN_N), both.nTerminus());
            assertEquals(Optional.of(TerminalCode.PROTEIN_C), both.cTerminus());
            // Comet: "'n' together with '^' is just 'n'" (CometSearchManager.cpp at v2026.03.0).
            assertEquals(
                    Optional.of(TerminalCode.PEPTIDE_N),
                    read202603("42.010565 n^ 0 1 -1 0 0 0.0").nTerminus());
            assertEquals(
                    Optional.of(TerminalCode.PEPTIDE_N),
                    read202603("42.010565 ^n 0 1 -1 0 0 0.0").nTerminus());
            assertEquals(
                    Optional.of(TerminalCode.PEPTIDE_C),
                    read202603("-0.984016 $c 0 1 -1 0 0 0.0").cTerminus());
            assertEquals(
                    Optional.of(TerminalCode.PEPTIDE_C),
                    read202603("-0.984016 c$ 0 1 -1 0 0 0.0").cTerminus());
            assertEquals("KR", read202603("1.0 ^K$Rnc 0 3 -1 0 0 0.0").residueLetters());
            assertEquals(
                    Optional.of(TerminalCode.PEPTIDE_N), read("1.0 nK 0 3 -1 0 0 0.0").nTerminus());
            assertEquals(Optional.empty(), read("1.0 K 0 3 -1 0 0 0.0").nTerminus());
            assertEquals(Optional.empty(), read("1.0 K 0 3 -1 0 0 0.0").cTerminus());
        }

        @Test
        void binaryAndUnused() {
            assertTrue(read("6.0 R 1 3 -1 0 0 0.0").isBinary());
            assertTrue(read("6.0 R -1 3 -1 0 0 0.0").isBinary());
            assertFalse(read("6.0 R 0 3 -1 0 0 0.0").isBinary());
            assertTrue(read("0.0 X 0 3 -1 0 0 0.0").isUnused());
            assertTrue(read("0 X 0 3 -1 0 0 0.0").isUnused());
            assertFalse(read("0.0001 X 0 3 -1 0 0 0.0").isUnused());
            assertFalse(read("-0.0001 X 0 3 -1 0 0 0.0").isUnused());
        }

        @Test
        void terminusCodes() {
            assertEquals(
                    Optional.of(VariableModification.Terminus.PROTEIN_N),
                    read("1.0 n 0 3 0 0 0 0.0").terminus());
            assertEquals(
                    Optional.of(VariableModification.Terminus.PROTEIN_C),
                    read("1.0 c 0 3 0 1 0 0.0").terminus());
            assertEquals(
                    Optional.of(VariableModification.Terminus.PEPTIDE_N),
                    read("1.0 n 0 3 0 2 0 0.0").terminus());
            assertEquals(
                    Optional.of(VariableModification.Terminus.PEPTIDE_C),
                    read("1.0 c 0 3 0 3 0 0.0").terminus());
            assertEquals(Optional.empty(), read("1.0 c 0 3 0 4 0 0.0").terminus());
            assertEquals(4, read("1.0 c 0 3 0 4 0 0.0").terminusCode());
            for (VariableModification.Terminus terminus : VariableModification.Terminus.values()) {
                assertEquals(
                        Optional.of(terminus),
                        VariableModification.Terminus.fromCode(terminus.code()));
            }
            assertEquals(Optional.empty(), VariableModification.Terminus.fromCode(-1));
        }

        @Test
        void requirementCodes() {
            assertEquals(
                    Optional.of(VariableModification.Requirement.OPTIONAL),
                    read("1.0 K 0 3 -1 0 0 0.0").requirement());
            assertEquals(
                    Optional.of(VariableModification.Requirement.REQUIRED),
                    read("1.0 K 0 3 -1 0 1 0.0").requirement());
            assertEquals(
                    Optional.of(VariableModification.Requirement.EXCLUSIVE),
                    read("1.0 K 0 3 -1 0 -1 0.0").requirement());
            assertEquals(Optional.empty(), read("1.0 K 0 3 -1 0 2 0.0").requirement());
            assertEquals(2, read("1.0 K 0 3 -1 0 2 0.0").requirementCode());
            assertEquals(Optional.empty(), VariableModification.Requirement.fromCode(-2));
            for (VariableModification.Requirement requirement :
                    VariableModification.Requirement.values()) {
                assertEquals(
                        Optional.of(requirement),
                        VariableModification.Requirement.fromCode(requirement.code()));
            }
            assertEquals(-1, VariableModification.Requirement.EXCLUSIVE.code());
            assertEquals("exclusive", VariableModification.Requirement.EXCLUSIVE.words());
            assertEquals(3, VariableModification.Terminus.PEPTIDE_C.code());
        }

        @Test
        void effectiveNeutralLosses() {
            assertEquals(List.of(), read("1.0 K 0 3 -1 0 0 0.0").effectiveNeutralLosses());
            assertEquals(
                    List.of(new BigDecimal("97.976896")),
                    read("1.0 K 0 3 -1 0 0 97.976896").effectiveNeutralLosses());
            assertEquals(
                    List.of(new BigDecimal("97.976896"), new BigDecimal("79.966331")),
                    read("1.0 K 0 3 -1 0 0 97.976896,79.966331").effectiveNeutralLosses());
            assertEquals(
                    List.of(new BigDecimal("79.966331")),
                    read("1.0 K 0 3 -1 0 0 0.0,79.966331").effectiveNeutralLosses());
            assertEquals(
                    List.of(new BigDecimal("-1.5")),
                    read("1.0 K 0 3 -1 0 0 -1.5").effectiveNeutralLosses());
        }
    }

    @Nested
    @DisplayName("the summary in words")
    class Summary {

        @Test
        @DisplayName("the specification's own example, word for word")
        void theSpecificationsExample() {
            assertEquals(
                    "Oxidation: +15.994915 on M; max 3 per peptide; optional",
                    read("15.994915 M 0 3 -1 0 0 0.0").summary("Oxidation"));
        }

        @Test
        void everyClause() {
            assertEquals(
                    "+79.966331 on STY; max 3 per peptide; optional; neutral loss 97.976896",
                    read("79.966331 STY 0 3 -1 0 0 97.976896").summary());
            assertEquals(
                    "+79.966331 on STY; max 3 per peptide; optional;"
                            + " neutral losses 97.976896 and 79.966331",
                    read("79.966331 STY 0 3 -1 0 0 97.976896,79.966331").summary());
            assertEquals(
                    "+79.966331 on STY; 2 to 4 per peptide; required",
                    read("79.966331 STY 0 2,4 -1 0 1 0.0").summary());
            assertEquals(
                    "+79.966331 on STY; max 3 per peptide; exclusive",
                    read("79.966331 STY 0 3 -1 0 -1 0.0").summary());
            assertEquals(
                    "+42.010565 on K and N-terminus; max 3 per peptide; optional",
                    read("42.010565 nK 0 3 -1 0 0 0.0").summary());
            assertEquals(
                    "+15.994915 on N-terminus, only at the protein N-terminus;"
                            + " max 3 per peptide; optional",
                    read("15.994915 n 0 3 0 0 0 0.0").summary());
            assertEquals(
                    "+28.0 on C-terminus, within 9 residues of the protein C-terminus;"
                            + " max 3 per peptide; optional",
                    read("28.0 c 0 3 8 1 0 0.0").summary());
            assertEquals(
                    "-17.026549 on Q, only at the peptide N-terminus; max 1 per peptide;"
                            + " optional",
                    read("-17.026549 Q 0 1 0 2 0 0.0").summary());
            assertEquals(
                    "+114.042927 on K, except on the peptide's C-terminal residue;"
                            + " max 3 per peptide; optional",
                    read("114.042927 K 0 3 -2 3 0 0.0").summary());
            assertEquals(
                    "+6.0 on R; max 3 per peptide; optional; binary group 1",
                    read("6.0 R 1 3 -1 0 0 0.0").summary());
            assertEquals(
                    "+1.0 on K and N-terminus and C-terminus, within 2 residues of the"
                            + " peptide C-terminus; max 3 per peptide; optional",
                    read("1.0 nKc 0 3 1 3 0 0.0").summary());
        }

        @Test
        @DisplayName("^ and $ in words: the protein's terminus, not any peptide's")
        void proteinTermini() {
            assertEquals(
                    "Acetyl: +42.010565 on protein N-terminus; max 1 per peptide; optional",
                    read202603("42.010565 ^ 0 1 -1 0 0 0.0").summary("Acetyl"));
            assertEquals(
                    "-0.984016 on protein C-terminus; max 1 per peptide; optional",
                    read202603("-0.984016 $ 0 1 -1 0 0 0.0").summary());
            assertEquals(
                    "+42.010565 on M and protein N-terminus; max 1 per peptide; optional",
                    read202603("42.010565 ^M 0 1 -1 0 0 0.0").summary());
            assertEquals(
                    "+42.010565 on protein N-terminus and protein C-terminus; max 1 per peptide;"
                            + " optional",
                    read202603("42.010565 ^$ 0 1 -1 0 0 0.0").summary());
            assertEquals(
                    "+42.010565 on N-terminus; max 1 per peptide; optional",
                    read202603("42.010565 n^ 0 1 -1 0 0 0.0").summary());
            assertEquals(
                    "-0.984016 on C-terminus; max 1 per peptide; optional",
                    read202603("-0.984016 $c 0 1 -1 0 0 0.0").summary());
            assertEquals(
                    "+42.010565 on K and protein N-terminus and C-terminus; max 1 per peptide;"
                            + " optional",
                    read202603("42.010565 K^c$ 0 1 -1 0 0 0.0").summary());
        }

        @Test
        void undocumentedCodesAreNamedNotGuessed() {
            assertEquals(
                    "+1.0 on K, terminal distance code -3; max 3 per peptide;"
                            + " requirement code 2",
                    read("1.0 K 0 3 -3 0 2 0.0").summary());
            assertEquals(
                    "+1.0 on K, only at the terminus code 7; max 3 per peptide; optional",
                    read("1.0 K 0 3 0 7 0 0.0").summary());
        }

        @Test
        void anUnusedSlot() {
            assertEquals("unused (mass difference 0.0)", read("0.0 X 0 3 -1 0 0 0.0").summary());
            assertEquals(
                    "Slot 2: unused (mass difference 0.0)",
                    read("0.0 X 0 3 -1 0 0 0.0").summary("Slot 2"));
        }
    }
}
