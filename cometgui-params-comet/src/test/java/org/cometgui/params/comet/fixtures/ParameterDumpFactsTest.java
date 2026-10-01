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

package org.cometgui.params.comet.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The {@code R-PARAM-01} facts, checked against the real 2026.02.2 fixtures on every platform.
 *
 * <p>Counting is done by {@link DeclarationLines}' line rule, not a parser. The 22 names {@code -p}
 * omits are typed in below <strong>from the specification</strong>, so that the fixtures are
 * checked against an independent statement and not against themselves.
 */
class ParameterDumpFactsTest {

    /**
     * {@code R-PARAM-01}'s list, typed from {@code specification.rst} revision 11. The
     * specification names some as families -- "the PEFF parameters", "the spectral-library
     * parameters", "both {@code mass_type_*} parameters" -- and those are spelled out here by their
     * names in Comet 2026.02.2, which {@code docs/feasibility/upstream-facts.rst} (row "Comet
     * parameter dump") records as "the specification's list exactly".
     */
    static final Set<String> ONLY_IN_COMPLETE =
            Set.of(
                    "variable_mod06",
                    "variable_mod07",
                    "variable_mod08",
                    "variable_mod09",
                    "variable_mod10",
                    "variable_mod11",
                    "variable_mod12",
                    "variable_mod13",
                    "variable_mod14",
                    "variable_mod15",
                    "mass_type_parent",
                    "mass_type_fragment",
                    "num_results",
                    "peff_format",
                    "peff_obo",
                    "spectral_library_name",
                    "spectral_library_ms_level",
                    "pinfile_protein_delimiter",
                    "print_expect_score",
                    "print_ascorepro_score",
                    "compoundmods_file",
                    "protein_modslist_file");

    private static final Pattern ENZYME_ROW =
            Pattern.compile("^[0-9]+\\.[ \\t]+\\S+[ \\t]+[01][ \\t]+\\S+[ \\t]+\\S+[ \\t]*$");

    private static List<String> lines(CometFixtures.Mode mode) throws IOException {
        return CometFixtures.lines(CometFixtures.COMET_2026_02_2, CometFixtures.LINUX_X86_64, mode);
    }

    private static Set<String> declared(CometFixtures.Mode mode) throws IOException {
        List<String> names = DeclarationLines.names(lines(mode));
        Set<String> distinct = new LinkedHashSet<>(names);
        assertEquals(
                names.size(),
                distinct.size(),
                () -> mode + " declares a name twice, so a count of lines is not a count of names");
        return distinct;
    }

    @Test
    @DisplayName("the hand-typed list is itself 22 names: 10 slots and 12 others")
    void theTypedListIsTheSpecification() {
        long slots =
                ONLY_IN_COMPLETE.stream().filter(name -> name.startsWith("variable_mod")).count();
        assertEquals(22, ONLY_IN_COMPLETE.size());
        assertEquals(10, slots);
    }

    @Test
    @DisplayName("comet -q declares 118 parameters")
    void completeDeclares118() throws IOException {
        assertEquals(118, declared(CometFixtures.Mode.COMPLETE).size());
    }

    @Test
    @DisplayName("comet -p declares 96 parameters")
    void defaultsDeclares96() throws IOException {
        assertEquals(96, declared(CometFixtures.Mode.DEFAULTS).size());
    }

    @Test
    @DisplayName("-q minus -p is exactly R-PARAM-01's 22 names")
    void theDifferenceIsTheSpecificationsList() throws IOException {
        Set<String> difference = new TreeSet<>(declared(CometFixtures.Mode.COMPLETE));
        difference.removeAll(declared(CometFixtures.Mode.DEFAULTS));
        assertEquals(new TreeSet<>(ONLY_IN_COMPLETE), difference);
    }

    @Test
    @DisplayName("nothing is declared by -p that -q does not declare")
    void defaultsIsASubsetOfComplete() throws IOException {
        Set<String> extra = new TreeSet<>(declared(CometFixtures.Mode.DEFAULTS));
        extra.removeAll(declared(CometFixtures.Mode.COMPLETE));
        assertEquals(Set.of(), extra, "-p declares names -q does not");
    }

    @Test
    @DisplayName("the -q fixture's first line is the # comet_version marker")
    void completeStartsWithTheVersionMarker() throws IOException {
        String first = lines(CometFixtures.Mode.COMPLETE).get(0);
        assertTrue(first.startsWith("# comet_version "), first);
        assertEquals(
                "# comet_version 2026.02 rev. 2 (6edec91)",
                first,
                "the marker the 2026.02.2 banner reports, Comet version \"2026.02 rev. 2"
                        + " (6edec91)\" -- not the manifest's spelling 2026.02.2");
    }

    @Test
    @DisplayName("the -q fixture ends with the [COMET_ENZYME_INFO] table")
    void completeEndsWithTheEnzymeTable() throws IOException {
        List<String> lines = lines(CometFixtures.Mode.COMPLETE);
        int marker = lines.indexOf("[COMET_ENZYME_INFO]");
        assertTrue(marker > 0, "no [COMET_ENZYME_INFO] line");
        assertEquals(
                marker,
                lines.lastIndexOf("[COMET_ENZYME_INFO]"),
                "the table marker appears more than once");
        List<String> after = lines.subList(marker + 1, lines.size());
        List<String> rows = after.stream().filter(line -> !line.isBlank()).toList();
        assertFalse(rows.isEmpty(), "the enzyme table has no rows");
        for (String row : rows) {
            assertTrue(
                    ENZYME_ROW.matcher(row).matches(),
                    () -> "after [COMET_ENZYME_INFO] only enzyme rows may follow, not: " + row);
        }
        assertEquals(
                List.of(),
                DeclarationLines.names(after),
                "a parameter is declared after the enzyme table, so the table is not at the end");
        assertTrue(
                rows.get(0).startsWith("0. ") && rows.get(rows.size() - 1).startsWith("11. "),
                "Comet 2026.02.2 writes enzymes 0 to 11: " + rows);
    }
}
