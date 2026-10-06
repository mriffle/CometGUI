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

package org.cometgui.params.comet.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.Stream;
import org.cometgui.domain.params.CometIndexDescription;
import org.cometgui.domain.params.PreRunFacts;
import org.cometgui.domain.run.IndexMode;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.schema.ParameterCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The pre-run check "selected index and search options are compatible", judged on the
 * self-descriptions of REAL indexes the two pinned Comet binaries built ({@link
 * IndexDescriptions}). Every finding is asserted on rule, severity, parameters and its whole,
 * hand-typed message.
 */
class IndexCompatibilityRulesTest {

    private static final ToolVersion NEWER = ToolVersion.parse("2026.03.0");

    private static final ToolVersion OLDER = ToolVersion.parse("2026.02.2");

    private static final String FILE = IndexDescriptions.INDEX.toString();

    private static ValidationReport validate(CometParameters model, CometIndexDescription index) {
        return CometValidator.standard().validate(model, PreRunFacts.none().withIndex(index));
    }

    /** The findings of the index rules alone, in report order. */
    private static List<Finding> indexFindings(ValidationReport report) {
        return report.findings().stream()
                .filter(
                        finding ->
                                finding.rule() == Rule.INDEX_FORMAT_UNREADABLE
                                        || finding.rule() == Rule.INDEX_CONTRADICTS_SEARCH
                                        || finding.rule() == Rule.INDEX_OPTION_UNRECORDED)
                .toList();
    }

    private static String contradiction(
            String recorded, String parameter, String value, String consequence, String fix) {
        return "the index "
                + FILE
                + " records "
                + recorded
                + ", and Comet searches an existing index with what it records, so "
                + parameter
                + " = "
                + value
                + " would be "
                + consequence
                + "; "
                + fix
                + ", or rebuild the index from its FASTA with this search's settings";
    }

    private static void assertContradiction(
            Finding finding, String parameter, CometParameters model, String message) {
        assertEquals(Rule.INDEX_CONTRADICTS_SEARCH, finding.rule());
        assertEquals(Severity.ERROR, finding.severity());
        assertEquals(List.of(parameter, "database_name"), finding.parameters());
        assertEquals(Optional.of(model.definition(parameter).category()), finding.category());
        assertEquals(message, finding.message());
    }

    @Test
    @DisplayName("format: one index judged for both releases by each release's own data")
    void formatByRelease() {
        CometIndexDescription formatFour = IndexDescriptions.formatFour(IndexMode.FRAGMENT_ION);
        CometIndexDescription formatFive = IndexDescriptions.formatFive(IndexMode.FRAGMENT_ION);

        Finding refused =
                Models.only(
                        new ValidationReport(
                                indexFindings(validate(Models.enforced(NEWER), formatFour))));
        Models.assertAttached(
                refused,
                Rule.INDEX_FORMAT_UNREADABLE,
                ParameterCategory.DATABASE_PEFF,
                "database_name");
        assertEquals(Severity.ERROR, refused.severity());
        assertEquals(
                "the index "
                        + FILE
                        + " is in format v4 (written by Comet 2026.02 rev. 2 (6edec91)), and Comet"
                        + " 2026.03.0 reads index format v5, so it would stop before searching;"
                        + " rebuild the index from its FASTA with Comet 2026.03.0",
                refused.message());

        List<Finding> olderOnFour = indexFindings(validate(Models.enforced(OLDER), formatFour));
        assertEquals(
                List.of(Rule.INDEX_OPTION_UNRECORDED),
                olderOnFour.stream().map(Finding::rule).toList(),
                "Comet 2026.02.2 reads format 4: no format finding, only the unrecorded options");

        assertEquals(
                List.of(),
                indexFindings(validate(Models.enforced(NEWER), formatFive)),
                "Comet 2026.03.0 reads its own format-5 index, built from its own defaults");
        Finding olderOnFive =
                Models.only(
                        new ValidationReport(
                                indexFindings(validate(Models.enforced(OLDER), formatFive))));
        assertEquals(Rule.INDEX_FORMAT_UNREADABLE, olderOnFive.rule());
        assertEquals(
                "the index "
                        + FILE
                        + " is in format v5 (written by Comet 2026.03 rev. 0 (fa08489)), and Comet"
                        + " 2026.02.2 reads index format v4, so it would stop before searching;"
                        + " rebuild the index from its FASTA with Comet 2026.02.2",
                olderOnFive.message());
    }

    @Test
    @DisplayName("an unreadable index is judged for nothing else")
    void unreadableIsJudgedForNothingElse() {
        CometParameters contradicting =
                Models.with(NEWER, "decoy_search", "1", "search_enzyme_number", "3");
        assertEquals(
                List.of(Rule.INDEX_FORMAT_UNREADABLE),
                indexFindings(
                                validate(
                                        contradicting,
                                        IndexDescriptions.formatFour(IndexMode.PEPTIDE)))
                        .stream()
                        .map(Finding::rule)
                        .toList());
    }

    @Test
    @DisplayName("format 4: the digestion options it does not record are a warning naming them")
    void unrecorded() {
        CometParameters model = Models.enforced(OLDER);
        Finding warning =
                Models.only(
                        new ValidationReport(
                                indexFindings(
                                        validate(
                                                model,
                                                IndexDescriptions.formatFour(
                                                        IndexMode.FRAGMENT_ION)))));
        Models.assertAttached(
                warning,
                Rule.INDEX_OPTION_UNRECORDED,
                ParameterCategory.DIGESTION_ENZYMES,
                "num_enzyme_termini",
                "allowed_missed_cleavage",
                "clip_nterm_methionine",
                "database_name");
        assertEquals(Severity.WARNING, warning.severity());
        assertEquals(
                "the index "
                        + FILE
                        + " (format v4, written by Comet 2026.02 rev. 2 (6edec91)) does not record"
                        + " num_enzyme_termini, allowed_missed_cleavage, clip_nterm_methionine,"
                        + " although its peptides were digested with them and Comet searches the"
                        + " peptides it holds; CometGUI cannot check them against this search's"
                        + " num_enzyme_termini = 2, allowed_missed_cleavage = 2,"
                        + " clip_nterm_methionine = 0, so make sure the index was built with the"
                        + " same, or rebuild it from its FASTA",
                warning.message());
        assertFalse(
                validate(model, IndexDescriptions.formatFour(IndexMode.FRAGMENT_ION)).hasErrors());
    }

    @Test
    @DisplayName("an index that records some digestion options is warned about the others only")
    void partlyUnrecorded() {
        CometIndexDescription full = IndexDescriptions.formatFive(IndexMode.PEPTIDE);
        CometIndexDescription noClip =
                rebuilt(full, OptionalInt.of(2), OptionalInt.empty(), Optional.empty());
        Finding warning =
                Models.only(
                        new ValidationReport(
                                indexFindings(validate(Models.enforced(NEWER), noClip))));
        assertEquals(
                List.of("allowed_missed_cleavage", "clip_nterm_methionine", "database_name"),
                warning.parameters());
        assertTrue(
                warning.message()
                        .contains(
                                "does not record allowed_missed_cleavage, clip_nterm_methionine,"
                                        + " although"),
                warning.message());
        assertTrue(
                warning.message()
                        .contains(
                                "this search's allowed_missed_cleavage = 2,"
                                        + " clip_nterm_methionine = 0, so"),
                warning.message());
        CometIndexDescription noTermini =
                rebuilt(full, OptionalInt.empty(), OptionalInt.of(2), Optional.of(false));
        assertEquals(
                List.of("num_enzyme_termini", "database_name"),
                Models.only(
                                new ValidationReport(
                                        indexFindings(validate(Models.enforced(NEWER), noTermini))))
                        .parameters());
    }

    /** A description with other digestion options recorded. */
    private static CometIndexDescription rebuilt(
            CometIndexDescription index,
            OptionalInt termini,
            OptionalInt missed,
            Optional<Boolean> clip) {
        return new CometIndexDescription(
                index.file(),
                index.formatVersion(),
                index.firstLine(),
                index.cometVersion(),
                index.type(),
                index.inputDatabase(),
                index.massRange(),
                index.lengthRange(),
                index.parentMassType(),
                index.fragmentMassType(),
                index.decoySearch(),
                index.decoyPrefix(),
                index.enzyme(),
                index.secondEnzyme(),
                termini,
                missed,
                clip,
                index.peptides(),
                index.staticMods(),
                index.variableMods(),
                index.proteinModList(),
                index.requireVariableMod(),
                index.maxVariableModsInPeptide());
    }

    static Stream<Arguments> contradictions() {
        String slotOne =
                "slot 1 of \"VariableMod:\", M:15.994900:0.000000:0.000000:3:-1:0, requirement 0";
        String slotTwo =
                "slot 2 of \"VariableMod:\", X:0.000000:0.000000:0.000000:3:-1:0, requirement 0";
        String ignored = "silently ignored";
        return Stream.of(
                Arguments.of(
                        "decoy_search",
                        "1",
                        "\"DecoySearch: 0\"",
                        ignored,
                        "set decoy_search to 0"),
                Arguments.of(
                        "decoy_prefix",
                        "REV_",
                        "\"DecoyPrefix: DECOY_\"",
                        ignored,
                        "set decoy_prefix to DECOY_"),
                Arguments.of(
                        "search_enzyme_number",
                        "3",
                        "\"Enzyme: Trypsin [1 KR P]\"",
                        "silently ignored (it selects Lys_C [1 K P])",
                        "select the enzyme Trypsin [1 KR P]"),
                Arguments.of(
                        "search_enzyme2_number",
                        "3",
                        "\"Enzyme2: Cut_everywhere [0 - -]\"",
                        "silently ignored (it selects Lys_C [1 K P])",
                        "select the enzyme Cut_everywhere [0 - -]"),
                Arguments.of(
                        "num_enzyme_termini",
                        "1",
                        "\"NumEnzymeTermini: 2\"",
                        ignored,
                        "set num_enzyme_termini to 2"),
                Arguments.of(
                        "allowed_missed_cleavage",
                        "0",
                        "\"AllowedMissedCleavage: 2\"",
                        ignored,
                        "set allowed_missed_cleavage to 2"),
                Arguments.of(
                        "clip_nterm_methionine",
                        "1",
                        "\"ClipNtermMethionine: 0\"",
                        ignored,
                        "set clip_nterm_methionine to 0"),
                Arguments.of(
                        "mass_type_parent",
                        "0",
                        "\"MassType: 1 1\"",
                        ignored,
                        "set mass_type_parent to 1"),
                Arguments.of(
                        "mass_type_fragment",
                        "0",
                        "\"MassType: 1 1\"",
                        ignored,
                        "set mass_type_fragment to 1"),
                Arguments.of(
                        "digest_mass_range",
                        "400.0 6000.0",
                        "\"MassRange: 600.000000 5000.000000\" and holds no peptide outside it",
                        "silently narrowed to it",
                        "keep digest_mass_range within 600.000000 5000.000000"),
                Arguments.of(
                        "digest_mass_range",
                        "599.9 5000.0",
                        "\"MassRange: 600.000000 5000.000000\" and holds no peptide outside it",
                        "silently narrowed to it",
                        "keep digest_mass_range within 600.000000 5000.000000"),
                Arguments.of(
                        "digest_mass_range",
                        "600.0 5000.1",
                        "\"MassRange: 600.000000 5000.000000\" and holds no peptide outside it",
                        "silently narrowed to it",
                        "keep digest_mass_range within 600.000000 5000.000000"),
                Arguments.of(
                        "peptide_length_range",
                        "4 50",
                        "\"LengthRange: 5 50\" and holds no peptide outside it",
                        "silently narrowed to it",
                        "keep peptide_length_range within 5 50"),
                Arguments.of(
                        "add_C_cysteine",
                        "0.0",
                        "the static modification 57.021464 on C (\"StaticMod:\")",
                        ignored,
                        "set add_C_cysteine to 57.021464"),
                Arguments.of(
                        "add_C_cysteine",
                        "57.021465",
                        "the static modification 57.021464 on C (\"StaticMod:\")",
                        ignored,
                        "set add_C_cysteine to 57.021464"),
                Arguments.of(
                        "add_Nterm_peptide",
                        "1.5",
                        "the static modification 0.000000 on peptide N-terminus (\"StaticMod:\")",
                        ignored,
                        "set add_Nterm_peptide to 0.000000"),
                Arguments.of(
                        "add_Cterm_protein",
                        "-0.98",
                        "the static modification 0.000000 on protein C-terminus (\"StaticMod:\")",
                        ignored,
                        "set add_Cterm_protein to 0.000000"),
                Arguments.of(
                        "add_Z_user_amino_acid",
                        "1.0",
                        "the static modification 0.000000 on Z (\"StaticMod:\")",
                        ignored,
                        "set add_Z_user_amino_acid to 0.000000"),
                Arguments.of(
                        "variable_mod01",
                        "0.0 M 0 3 -1 0 0 0.0",
                        slotOne,
                        ignored,
                        "set variable_mod01 to what the index records"),
                Arguments.of(
                        "variable_mod01",
                        "15.995 M 0 3 -1 0 0 0.0",
                        slotOne,
                        ignored,
                        "set variable_mod01 to what the index records"),
                Arguments.of(
                        "variable_mod01",
                        "15.9949 MW 0 3 -1 0 0 0.0",
                        slotOne,
                        ignored,
                        "set variable_mod01 to what the index records"),
                Arguments.of(
                        "variable_mod01",
                        "15.9949 M 0 3 -1 0 0 97.9",
                        slotOne,
                        ignored,
                        "set variable_mod01 to what the index records"),
                Arguments.of(
                        "variable_mod01",
                        "15.9949 M 0 3 -1 0 0 0.0,1.0",
                        slotOne,
                        ignored,
                        "set variable_mod01 to what the index records"),
                Arguments.of(
                        "variable_mod01",
                        "15.9949 M 0 2 -1 0 0 0.0",
                        slotOne,
                        ignored,
                        "set variable_mod01 to what the index records"),
                Arguments.of(
                        "variable_mod01",
                        "15.9949 M 0 3 -1 0 1 0.0",
                        slotOne,
                        ignored,
                        "set variable_mod01 to what the index records"),
                Arguments.of(
                        "variable_mod01",
                        "15.9949 M 0 3 2 3 0 0.0",
                        slotOne,
                        ignored,
                        "set variable_mod01 to what the index records"),
                Arguments.of(
                        "variable_mod02",
                        "79.966331 STY 0 3 -1 0 0 0.0",
                        slotTwo,
                        ignored,
                        "set variable_mod02 to what the index records"),
                Arguments.of(
                        "variable_mod06",
                        "79.966331 STY 0 3 -1 0 0 0.0",
                        "only 5 variable-modification slots (\"VariableMod:\")",
                        ignored,
                        "switch variable_mod06 off, or move the modification into a slot from"
                                + " variable_mod01 to variable_mod05"),
                Arguments.of(
                        "require_variable_mod",
                        "1",
                        "\"RequireVariableMod: 0 ...\" (its lowest bit is require_variable_mod)",
                        ignored,
                        "set require_variable_mod to 0"),
                Arguments.of(
                        "max_variable_mods_in_peptide",
                        "4",
                        "\"MaxVariableModsInPeptide: 5\"",
                        ignored,
                        "set max_variable_mods_in_peptide to 5"),
                Arguments.of(
                        "index_search_type",
                        "0",
                        "\"IndexSearchType: fragment ion index\"",
                        ignored,
                        "set index_search_type to 1"));
    }

    @ParameterizedTest(name = "{0} = {1}")
    @MethodSource("contradictions")
    @DisplayName("each option the index records and the search contradicts is an error naming both")
    void contradictionsRefused(
            String parameter, String value, String recorded, String consequence, String fix) {
        CometParameters model = Models.with(NEWER, parameter, value);
        Finding finding =
                Models.only(
                        new ValidationReport(
                                indexFindings(
                                        validate(
                                                model,
                                                IndexDescriptions.formatFive(
                                                        IndexMode.FRAGMENT_ION)))));
        assertContradiction(
                finding,
                parameter,
                model,
                contradiction(recorded, parameter, value, consequence, fix));
    }

    @Test
    @DisplayName("index_search_type: a peptide index asked to be fragment-ion; -1 asks nothing")
    void indexType() {
        CometParameters model = Models.with(NEWER, "index_search_type", "1");
        Finding finding =
                Models.only(
                        new ValidationReport(
                                indexFindings(
                                        validate(
                                                model,
                                                IndexDescriptions.formatFive(IndexMode.PEPTIDE)))));
        assertContradiction(
                finding,
                "index_search_type",
                model,
                contradiction(
                        "\"IndexSearchType: peptide index\"",
                        "index_search_type",
                        "1",
                        "silently ignored",
                        "set index_search_type to 0"));
        for (IndexMode mode : List.of(IndexMode.PEPTIDE, IndexMode.FRAGMENT_ION)) {
            assertEquals(
                    List.of(),
                    indexFindings(
                            validate(
                                    Models.with(NEWER, "index_search_type", "-1"),
                                    IndexDescriptions.formatFive(mode))));
        }
        assertEquals(
                List.of(),
                indexFindings(
                        validate(
                                Models.with(NEWER, "index_search_type", "0"),
                                IndexDescriptions.formatFive(IndexMode.PEPTIDE))));
    }

    @Test
    @DisplayName(
            "Comet 2026.02.2's default index_search_type = 1 contradicts its own peptide index")
    void olderDefaultAgainstPeptideIndex() {
        CometParameters model = Models.enforced(OLDER);
        List<Finding> found =
                indexFindings(validate(model, IndexDescriptions.formatFour(IndexMode.PEPTIDE)));
        assertEquals(2, found.size(), found::toString);
        assertContradiction(
                found.get(0),
                "index_search_type",
                model,
                contradiction(
                        "\"IndexSearchType: peptide index\"",
                        "index_search_type",
                        "1",
                        "silently ignored",
                        "set index_search_type to 0"));
        assertEquals(Rule.INDEX_OPTION_UNRECORDED, found.get(1).rule());
    }

    static Stream<Arguments> agreements() {
        return Stream.of(
                Arguments.of("digest_mass_range", "600.0 5000.0"),
                Arguments.of("digest_mass_range", "700.0 1500.0"),
                Arguments.of("peptide_length_range", "5 50"),
                Arguments.of("peptide_length_range", "7 30"),
                Arguments.of("add_C_cysteine", "57.0214641"),
                Arguments.of("add_C_cysteine", "57.0214645"),
                Arguments.of("add_C_cysteine", "57.0214635"),
                Arguments.of("variable_mod01", "15.994900 M 0 3 -1 0 0 0.000"),
                Arguments.of("variable_mod01", "15.9949 M 1 3 -1 0 0 0.0"),
                Arguments.of("variable_mod01", "15.9949 M 0 0,3 -1 0 0 0.0"),
                Arguments.of("variable_mod02", "0.0 STY 0 3 -1 0 0 0.0"),
                Arguments.of("max_variable_mods_in_peptide", "-1"),
                Arguments.of("decoy_prefix", "DECOY_"),
                Arguments.of("protein_modslist_file", "/data/proteins.txt"));
    }

    @ParameterizedTest(name = "{0} = {1}")
    @MethodSource("agreements")
    @DisplayName("what agrees with the index, or Comet applies anyway, draws no index finding")
    void agreementsPass(String parameter, String value) {
        assertEquals(
                List.of(),
                indexFindings(
                        validate(
                                Models.with(NEWER, parameter, value),
                                IndexDescriptions.formatFive(IndexMode.FRAGMENT_ION))));
    }

    @Test
    @DisplayName("format 4 records no distance or terminus, so a slot's position is not compared")
    void unrecordedPosition() {
        CometParameters model = Models.with(OLDER, "variable_mod01", "15.9949 M 0 3 2 3 0 0.0");
        assertEquals(
                List.of(Rule.INDEX_OPTION_UNRECORDED),
                indexFindings(validate(model, IndexDescriptions.formatFour(IndexMode.FRAGMENT_ION)))
                        .stream()
                        .map(Finding::rule)
                        .toList());
    }

    @Test
    @DisplayName("the modifications index: merged, capped, rewritten slots agree; slot 6 does not")
    void modsIndex() {
        for (boolean newer : List.of(true, false)) {
            ToolVersion release = newer ? NEWER : OLDER;
            CometParameters model = Models.with(release, IndexDescriptions.MODS_EDITS);
            List<Finding> found = indexFindings(validate(model, IndexDescriptions.mods(newer)));
            assertEquals(newer ? 1 : 2, found.size(), () -> release.text() + ": " + found);
            assertContradiction(
                    found.get(0),
                    "variable_mod06",
                    model,
                    contradiction(
                            "only 5 variable-modification slots (\"VariableMod:\")",
                            "variable_mod06",
                            "-17.026549 Q 0 1 0 2 0 0.0",
                            "silently ignored",
                            "switch variable_mod06 off, or move the modification into a slot from"
                                    + " variable_mod01 to variable_mod05"));
            if (!newer) {
                assertEquals(Rule.INDEX_OPTION_UNRECORDED, found.get(1).rule());
            }
        }
    }

    @Test
    @DisplayName(
            "the modifications index: a slot Comet merged, or the capped count, can still differ")
    void modsIndexDifferences() {
        List<String> edits = new ArrayList<>(List.of(IndexDescriptions.MODS_EDITS));
        edits.addAll(List.of("variable_mod06", "0.0 X 0 3 -1 0 0 0.0"));
        CometParameters clean = Models.with(NEWER, edits.toArray(String[]::new));
        assertEquals(List.of(), indexFindings(validate(clean, IndexDescriptions.mods(true))));

        List<String> unmerged = new ArrayList<>(edits);
        unmerged.addAll(List.of("variable_mod02", "15.9949 W 0 2 -1 0 0 0.0"));
        List<Finding> found =
                indexFindings(
                        validate(
                                Models.with(NEWER, unmerged.toArray(String[]::new)),
                                IndexDescriptions.mods(true)));
        assertEquals(
                List.of("variable_mod01", "variable_mod02"),
                found.stream().map(finding -> finding.parameters().get(0)).toList(),
                "W no longer merges into slot 1, so slot 1 is M alone and slot 2 is active");

        List<String> recount = new ArrayList<>(edits);
        recount.addAll(List.of("variable_mod03", "79.966331 STY 0 4 -1 0 1 97.976896"));
        assertEquals(
                List.of("variable_mod03"),
                indexFindings(
                                validate(
                                        Models.with(NEWER, recount.toArray(String[]::new)),
                                        IndexDescriptions.mods(true)))
                        .stream()
                        .map(finding -> finding.parameters().get(0))
                        .toList(),
                "a count of 4 is not the 5 Comet capped 7 to");

        List<String> peptideTerminus = new ArrayList<>(edits);
        peptideTerminus.addAll(List.of("variable_mod04", "42.010565 n 0 1 -1 0 0 0.0"));
        assertEquals(
                List.of("variable_mod04"),
                indexFindings(
                                validate(
                                        Models.with(NEWER, peptideTerminus.toArray(String[]::new)),
                                        IndexDescriptions.mods(true)))
                        .stream()
                        .map(finding -> finding.parameters().get(0))
                        .toList(),
                "n at any peptide N-terminus is not the index's protein N-terminus ^");
    }

    @Test
    @DisplayName(
            "the fragment-ion cap of 5 applies to a fragment-ion index, not to a peptide index")
    void countCapByType() {
        CometParameters model =
                Models.with(
                        NEWER,
                        "variable_mod01",
                        "15.9949 M 0 9 -1 0 0 0.0",
                        "max_variable_mods_in_peptide",
                        "9");
        CometIndexDescription peptide = IndexDescriptions.formatFive(IndexMode.PEPTIDE);
        List<CometIndexDescription.VariableMod> slots = new ArrayList<>(peptide.variableMods());
        CometIndexDescription.VariableMod first = slots.get(0);
        slots.set(
                0,
                new CometIndexDescription.VariableMod(
                        first.residues(),
                        first.mass(),
                        first.neutralLoss(),
                        first.secondNeutralLoss(),
                        5,
                        first.terminalDistance(),
                        first.terminus(),
                        first.requirement()));
        for (IndexMode mode : List.of(IndexMode.FRAGMENT_ION, IndexMode.PEPTIDE)) {
            CometIndexDescription index =
                    new CometIndexDescription(
                            peptide.file(),
                            5,
                            peptide.firstLine(),
                            peptide.cometVersion(),
                            mode,
                            peptide.inputDatabase(),
                            peptide.massRange(),
                            peptide.lengthRange(),
                            1,
                            1,
                            0,
                            peptide.decoyPrefix(),
                            peptide.enzyme(),
                            peptide.secondEnzyme(),
                            peptide.enzymeTermini(),
                            peptide.missedCleavages(),
                            peptide.clipNtermMethionine(),
                            peptide.peptides(),
                            peptide.staticMods(),
                            slots,
                            false,
                            0,
                            9);
            List<String> found =
                    indexFindings(validate(model, index)).stream()
                            .map(finding -> finding.parameters().get(0))
                            .toList();
            assertEquals(
                    mode == IndexMode.FRAGMENT_ION ? List.of() : List.of("variable_mod01"),
                    found,
                    mode.toString());
        }
    }

    /** A format-5 description with other variable-modification slots. */
    private static CometIndexDescription withSlots(
            CometIndexDescription index, List<CometIndexDescription.VariableMod> slots) {
        return new CometIndexDescription(
                index.file(),
                index.formatVersion(),
                index.firstLine(),
                index.cometVersion(),
                index.type(),
                index.inputDatabase(),
                index.massRange(),
                index.lengthRange(),
                index.parentMassType(),
                index.fragmentMassType(),
                index.decoySearch(),
                index.decoyPrefix(),
                index.enzyme(),
                index.secondEnzyme(),
                index.enzymeTermini(),
                index.missedCleavages(),
                index.clipNtermMethionine(),
                index.peptides(),
                index.staticMods(),
                slots,
                index.proteinModList(),
                index.requireVariableMod(),
                index.maxVariableModsInPeptide());
    }

    private static CometIndexDescription.VariableMod held(
            String residues, String mass, int count, int distance, int terminus) {
        return new CometIndexDescription.VariableMod(
                residues,
                new java.math.BigDecimal(mass),
                java.math.BigDecimal.ZERO,
                java.math.BigDecimal.ZERO,
                count,
                OptionalInt.of(distance),
                OptionalInt.of(terminus),
                0);
    }

    @Test
    @DisplayName("the last slot an index holds is compared, and is not 'beyond' it")
    void lastHeldSlot() {
        CometIndexDescription plain = IndexDescriptions.formatFive(IndexMode.FRAGMENT_ION);
        List<CometIndexDescription.VariableMod> slots = new ArrayList<>(plain.variableMods());
        slots.set(4, held("K", "42.010565", 2, -1, 3));
        CometIndexDescription index = withSlots(plain, slots);
        assertEquals(
                List.of(),
                indexFindings(
                        validate(
                                Models.with(NEWER, "variable_mod05", "42.010565 K 0 2 -1 3 0 0.0"),
                                index)),
                "slot 5 as held, its terminus 3 kept");
        CometParameters other = Models.with(NEWER, "variable_mod05", "42.010565 K 0 1 -1 3 0 0.0");
        Finding finding = Models.only(new ValidationReport(indexFindings(validate(other, index))));
        assertContradiction(
                finding,
                "variable_mod05",
                other,
                contradiction(
                        "slot 5 of \"VariableMod:\", K:42.010565:0:0:2:-1:3, requirement 0",
                        "variable_mod05",
                        "42.010565 K 0 1 -1 3 0 0.0",
                        "silently ignored",
                        "set variable_mod05 to what the index records"));
    }

    @Test
    @DisplayName("c at distance 0 from the protein C-terminus is held as $, with no position left")
    void proteinCTerminusRewrite() {
        CometIndexDescription plain = IndexDescriptions.formatFive(IndexMode.FRAGMENT_ION);
        List<CometIndexDescription.VariableMod> slots = new ArrayList<>(plain.variableMods());
        slots.set(1, held("$", "-0.984016", 1, -1, 0));
        assertEquals(
                List.of(),
                indexFindings(
                        validate(
                                Models.with(NEWER, "variable_mod02", "-0.984016 c 0 1 0 1 0 0.0"),
                                withSlots(plain, slots))));
        assertEquals(
                "$",
                IndexCompatibilityRule.indexedResidues(
                        ((org.cometgui.params.comet.model.ParameterValue.Tuple)
                                        Models.with(
                                                        NEWER,
                                                        "variable_mod02",
                                                        "-0.984016 c 0 1 0 1 0 0.0")
                                                .value("variable_mod02"))
                                .modification(),
                        VariableModRules.alphabet(Models.enforced(NEWER))));
    }

    @Test
    @DisplayName("several contradictions are each reported, in the rule's order")
    void several() {
        CometParameters model =
                Models.with(
                        NEWER,
                        "decoy_search",
                        "2",
                        "num_enzyme_termini",
                        "1",
                        "add_C_cysteine",
                        "0.0",
                        "require_variable_mod",
                        "1");
        assertEquals(
                List.of(
                        "decoy_search",
                        "num_enzyme_termini",
                        "add_C_cysteine",
                        "require_variable_mod"),
                indexFindings(validate(model, IndexDescriptions.formatFive(IndexMode.PEPTIDE)))
                        .stream()
                        .map(finding -> finding.parameters().get(0))
                        .toList());
        assertTrue(validate(model, IndexDescriptions.formatFive(IndexMode.PEPTIDE)).hasErrors());
    }

    @Test
    @DisplayName("a plain FASTA database with no index description draws no index finding")
    void plainFasta() {
        CometParameters model =
                Models.with(NEWER, "database_name", "/data/subset.fasta", "decoy_search", "1");
        ValidationReport report =
                CometValidator.standard()
                        .validate(
                                model,
                                PreRunFacts.none().withCensus(IndexDescriptions.subsetCensus()));
        assertEquals(List.of(), indexFindings(report));
        assertEquals(Models.validate(model), report);
    }

    @Test
    @DisplayName("an enzyme number naming no row is the enzyme rule's error, not a contradiction")
    void enzymeNotInTable() {
        CometParameters model = Models.with(NEWER, "search_enzyme_number", "42");
        ValidationReport report =
                validate(model, IndexDescriptions.formatFive(IndexMode.FRAGMENT_ION));
        assertEquals(List.of(), indexFindings(report));
        assertEquals(1, report.of(Rule.ENZYME_NOT_IN_TABLE).size());
    }
}
