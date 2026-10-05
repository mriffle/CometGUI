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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.cometgui.domain.tools.ToolVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The release facts a structured editor offers -- the residue letters and terminal codes of the
 * alphabet, the static-modification targets -- on both offered releases, expectations typed by
 * hand.
 */
class EditorFactsTest {

    private static final CuratedMetadata METADATA = MetadataLoader.loadBundled();

    private static final String ALL_LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private static ResidueAlphabet alphabet(String release) {
        return METADATA.version(ToolVersion.parse(release))
                .orElseThrow()
                .variableModTuple()
                .residueAlphabet();
    }

    @Test
    @DisplayName("2026.03.0 offers n, c, ^ and $; 2026.02.2 n and c; both every letter")
    void terminalCodesByRelease() {
        assertEquals(ALL_LETTERS, alphabet("2026.03.0").letters());
        assertEquals(ALL_LETTERS, alphabet("2026.02.2").letters());
        assertEquals(
                List.of(
                        TerminalCode.PEPTIDE_N,
                        TerminalCode.PEPTIDE_C,
                        TerminalCode.PROTEIN_N,
                        TerminalCode.PROTEIN_C),
                alphabet("2026.03.0").terminalCodes());
        assertEquals(
                List.of(TerminalCode.PEPTIDE_N, TerminalCode.PEPTIDE_C),
                alphabet("2026.02.2").terminalCodes());
    }

    @Test
    @DisplayName("a constructed alphabet: its letters in order, its codes in vocabulary order")
    void constructedAlphabet() {
        ResidueAlphabet constructed = new ResidueAlphabet("$YKAn", "https://example.org/x");
        assertEquals("AKY", constructed.letters());
        assertEquals(
                List.of(TerminalCode.PEPTIDE_N, TerminalCode.PROTEIN_C),
                constructed.terminalCodes());
        assertEquals("AKY, n (N-terminus), $ (protein C-terminus)", constructed.describe());
        ResidueAlphabet codesOnly = new ResidueAlphabet("c", "https://example.org/x");
        assertEquals("", codesOnly.letters());
        assertEquals("c (C-terminus)", codesOnly.describe());
    }

    @ParameterizedTest(name = "Comet {0}")
    @ValueSource(strings = {"2026.03.0", "2026.02.2"})
    @DisplayName("every static-modification parameter has a target: four termini, 26 residues")
    void staticModificationTargets(String release) {
        List<String> words = new ArrayList<>();
        int residues = 0;
        for (ParameterDefinition definition : METADATA.parametersFor(ToolVersion.parse(release))) {
            Optional<StaticModTarget> target = StaticModTarget.of(definition);
            assertEquals(
                    definition.category() == ParameterCategory.STATIC_MODS,
                    target.isPresent(),
                    definition.name());
            if (target.isPresent()) {
                words.add(target.get().words());
                assertEquals(definition.name(), target.get().parameter());
                if (!target.get().isTerminus()) {
                    residues++;
                }
            }
        }
        assertEquals(26, residues);
        assertEquals(
                List.of(
                        "peptide C-terminus",
                        "peptide N-terminus",
                        "protein C-terminus",
                        "protein N-terminus",
                        "glycine (G)",
                        "alanine (A)",
                        "serine (S)",
                        "proline (P)",
                        "valine (V)",
                        "threonine (T)",
                        "cysteine (C)",
                        "leucine (L)",
                        "isoleucine (I)",
                        "asparagine (N)",
                        "aspartic acid (D)",
                        "glutamine (Q)",
                        "lysine (K)",
                        "glutamic acid (E)",
                        "methionine (M)",
                        "histidine (H)",
                        "phenylalanine (F)",
                        "selenocysteine (U)",
                        "arginine (R)",
                        "tyrosine (Y)",
                        "tryptophan (W)",
                        "pyrrolysine (O)",
                        "user amino acid (B)",
                        "user amino acid (J)",
                        "user amino acid (X)",
                        "user amino acid (Z)"),
                words);
    }

    @Test
    @DisplayName("a target names its residue letter, or none for a terminus")
    void residueLetter() {
        ToolVersion version = ToolVersion.parse("2026.03.0");
        StaticModTarget cysteine =
                StaticModTarget.of(METADATA.parameter("add_C_cysteine", version).orElseThrow())
                        .orElseThrow();
        assertEquals(Optional.of('C'), cysteine.residue());
        assertFalse(cysteine.isTerminus());
        StaticModTarget nterm =
                StaticModTarget.of(METADATA.parameter("add_Nterm_protein", version).orElseThrow())
                        .orElseThrow();
        assertEquals(Optional.empty(), nterm.residue());
        assertTrue(nterm.isTerminus());
    }

    @Test
    @DisplayName("a static-modification name outside Comet's two patterns has no target")
    void constructedNames() {
        ParameterDefinition real =
                METADATA.parameter("add_C_cysteine", ToolVersion.parse("2026.03.0")).orElseThrow();
        for (String name :
                List.of(
                        "add_Xterm_peptide",
                        "add_Nterm_residue",
                        "add_Nterm_",
                        "add_Nterm",
                        "add_c_lower",
                        "add_CC_double",
                        "add_C",
                        "add_",
                        "sub_C_cysteine",
                        "add_1_digit")) {
            assertEquals(Optional.empty(), StaticModTarget.of(renamed(real, name)), name);
        }
        assertEquals("x (Q)", StaticModTarget.of(renamed(real, "add_Q_x")).orElseThrow().words());
        assertEquals(
                "x y (Q)", StaticModTarget.of(renamed(real, "add_Q_x_y")).orElseThrow().words());
        assertEquals(
                "protein C-terminus",
                StaticModTarget.of(renamed(real, "add_Cterm_protein")).orElseThrow().words());
        assertEquals(
                Optional.empty(),
                StaticModTarget.of(renamed(real, "add_C_cysteine", ValueKind.INTEGER)));
        ParameterDefinition otherCategory =
                METADATA.parameter("fragment_bin_tol", ToolVersion.parse("2026.03.0"))
                        .orElseThrow();
        assertEquals(
                Optional.empty(), StaticModTarget.of(renamed(otherCategory, "add_C_cysteine")));
    }

    private static ParameterDefinition renamed(ParameterDefinition d, String name) {
        return renamed(d, name, d.kind());
    }

    private static ParameterDefinition renamed(ParameterDefinition d, String name, ValueKind kind) {
        return new ParameterDefinition(
                name,
                d.displayName(),
                d.category(),
                kind,
                d.visibility(),
                d.defaultValue(),
                d.minimum(),
                d.maximum(),
                d.choices(),
                d.shortHelp(),
                d.inlineComment(),
                d.detailedHelpRef(),
                d.supportedVersions(),
                d.serialization(),
                d.validators(),
                d.aliases(),
                d.related());
    }
}
