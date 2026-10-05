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

import static org.cometgui.ui.viewmodel.params.Sessions.C02;
import static org.cometgui.ui.viewmodel.params.Sessions.C03;
import static org.cometgui.ui.viewmodel.params.Sessions.startingIn;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.parser.CometParamsParser;
import org.cometgui.params.comet.validation.Finding;
import org.cometgui.params.comet.validation.Rule;
import org.cometgui.params.comet.validation.Severity;
import org.cometgui.params.comet.validation.ValidationReport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The global parameter search (exit gate item 8). Each of the gate's four attributes is proved with
 * a query that matches its parameter through that attribute ONLY -- the hit's {@code matchedBy} is
 * exactly that one attribute, which proves the query does not match through the others -- on both
 * offered releases. The queries are the metadata's real words, picked by hand.
 */
class ParameterSearchViewModelTest {

    private static List<String> names(ParameterSearchViewModel search) {
        return search.results().stream().map(SearchHit::name).toList();
    }

    private static SearchHit only(ParameterSearchViewModel search) {
        assertEquals(1, search.results().size(), () -> names(search).toString());
        return search.results().get(0);
    }

    private static List<String> slots() {
        return List.of(
                "variable_mod01",
                "variable_mod02",
                "variable_mod03",
                "variable_mod04",
                "variable_mod05",
                "variable_mod06",
                "variable_mod07",
                "variable_mod08",
                "variable_mod09",
                "variable_mod10",
                "variable_mod11",
                "variable_mod12",
                "variable_mod13",
                "variable_mod14",
                "variable_mod15");
    }

    @Nested
    @DisplayName("each attribute on its own (gate item 8), on both releases")
    class ByAttribute {

        @ParameterizedTest(name = "Comet {0}")
        @ValueSource(strings = {"2026.03.0", "2026.02.2"})
        @DisplayName("by name: \"allowed_missed\" matches allowed_missed_cleavage's name only")
        void byName(String release) {
            ParameterSearchViewModel search =
                    new ParameterSearchViewModel(startingIn(ToolVersion.parse(release)));
            search.setQuery("allowed_missed");
            SearchHit hit = only(search);
            assertEquals("allowed_missed_cleavage", hit.name());
            assertEquals(List.of(SearchMatch.NAME), hit.matchedBy());
            assertEquals("Matched by name", hit.why());
            assertEquals("Allowed missed cleavages (allowed_missed_cleavage)", hit.label());
            assertEquals("allowed_missed_cleavage", hit.field().orElseThrow().name());
            assertFalse(hit.unknown());
        }

        @ParameterizedTest(name = "Comet {0}")
        @ValueSource(strings = {"2026.03.0", "2026.02.2"})
        @DisplayName("by display name: \"Enzymatic termini\", case-insensitively")
        void byDisplayName(String release) {
            ParameterSearchViewModel search =
                    new ParameterSearchViewModel(startingIn(ToolVersion.parse(release)));
            search.setQuery("  ENZYMATIC termini ");
            SearchHit hit = only(search);
            assertEquals("num_enzyme_termini", hit.name());
            assertEquals(List.of(SearchMatch.DISPLAY_NAME), hit.matchedBy());
            assertEquals("Matched by display name", hit.why());
        }

        @ParameterizedTest(name = "Comet {0}")
        @ValueSource(strings = {"2026.03.0", "2026.02.2"})
        @DisplayName("by help text: \"placeholder\" is in two parameters' help and nowhere else")
        void byHelpText(String release) {
            ParameterSearchViewModel search =
                    new ParameterSearchViewModel(startingIn(ToolVersion.parse(release)));
            search.setQuery("placeholder");
            assertEquals(List.of("database_name", "spectral_library_name"), names(search));
            for (SearchHit hit : search.results()) {
                assertEquals(List.of(SearchMatch.HELP_TEXT), hit.matchedBy(), hit.name());
                assertEquals("Matched by help text", hit.why());
            }
        }

        @ParameterizedTest(name = "Comet {0}")
        @ValueSource(strings = {"2026.03.0", "2026.02.2"})
        @DisplayName("by alias: \"semi-tryptic\" and the specification's \"oxidation\"")
        void byAlias(String release) {
            ParameterSearchViewModel search =
                    new ParameterSearchViewModel(startingIn(ToolVersion.parse(release)));
            search.setQuery("semi-tryptic");
            SearchHit hit = only(search);
            assertEquals("num_enzyme_termini", hit.name());
            assertEquals(List.of(SearchMatch.ALIAS), hit.matchedBy());
            assertEquals(List.of("semi-tryptic"), hit.aliases());
            assertEquals("Matched by alias \"semi-tryptic\"", hit.why());

            search.setQuery("Oxidation");
            assertEquals(slots(), names(search));
            for (SearchHit slot : search.results()) {
                assertEquals(List.of(SearchMatch.ALIAS), slot.matchedBy(), slot.name());
                assertEquals(List.of("oxidation"), slot.aliases());
            }
        }

        @ParameterizedTest(name = "Comet {0}")
        @ValueSource(strings = {"2026.03.0", "2026.02.2"})
        @DisplayName("by category: \"Spectral pre-processing\" lists its six parameters")
        void byCategory(String release) {
            ParameterSearchViewModel search =
                    new ParameterSearchViewModel(startingIn(ToolVersion.parse(release)));
            search.setQuery("spectral pre-processing");
            assertEquals(
                    List.of(
                            "minimum_peaks",
                            "minimum_intensity",
                            "remove_precursor_peak",
                            "remove_precursor_tolerance",
                            "clear_mz_range",
                            "percentage_base_peak"),
                    names(search));
            assertTrue(
                    search.results().stream()
                            .allMatch(h -> h.matchedBy().equals(List.of(SearchMatch.CATEGORY))));
            assertEquals("6 parameters found", search.headline());
        }
    }

    @Nested
    @DisplayName("the specification's examples")
    class Examples {

        @Test
        @DisplayName("\"precursor tolerance\" and \"missed cleavage\", with every reason")
        void examples() {
            ParameterSearchViewModel search = new ParameterSearchViewModel(startingIn(C03));
            search.setQuery("precursor tolerance");
            assertEquals(
                    List.of(
                            "peptide_mass_tolerance_upper",
                            "peptide_mass_tolerance_lower",
                            "peptide_mass_units",
                            "precursor_tolerance_type",
                            "isotope_error"),
                    names(search));
            assertEquals(
                    List.of(SearchMatch.DISPLAY_NAME, SearchMatch.ALIAS),
                    search.results().get(0).matchedBy());
            assertEquals(
                    List.of(SearchMatch.DISPLAY_NAME, SearchMatch.HELP_TEXT, SearchMatch.ALIAS),
                    search.results().get(2).matchedBy());
            assertEquals(List.of(SearchMatch.HELP_TEXT), search.results().get(4).matchedBy());

            search.setQuery("missed cleavage");
            SearchHit hit = only(search);
            assertEquals("allowed_missed_cleavage", hit.name());
            assertEquals(List.of(SearchMatch.DISPLAY_NAME, SearchMatch.ALIAS), hit.matchedBy());
            assertEquals(List.of("missed cleavage", "missed cleavages"), hit.aliases());
            assertEquals(
                    "Matched by display name, alias \"missed cleavage\", \"missed cleavages\"",
                    hit.why());
        }

        @Test
        @DisplayName("a query matching nothing finds nothing; an empty one lists everything")
        void emptyAndNothing() {
            ParameterSearchViewModel search = new ParameterSearchViewModel(startingIn(C03));
            assertEquals(118, search.results().size());
            assertEquals("Listed by the filters", search.results().get(0).why());
            assertEquals(List.of(), search.results().get(0).matchedBy());
            search.setQuery("zebrafish");
            assertEquals(List.of(), search.results());
            assertEquals("0 parameters found", search.headline());
            search.setQuery("allowed_missed");
            assertEquals("1 parameter found", search.headline());
            assertEquals("allowed_missed", search.queryProperty().get());
        }
    }

    @Test
    @DisplayName("help text is the selected release's own")
    void releaseHelp() {
        ParameterSession session = startingIn(C03);
        ParameterSearchViewModel search = new ParameterSearchViewModel(session);
        // 2026.03.0's index_search_type help says "-1, the default, means not set"
        search.setQuery("not set");
        assertEquals(List.of("index_search_type"), names(search));
        assertEquals(List.of(SearchMatch.HELP_TEXT), search.results().get(0).matchedBy());
        // 2026.02.2's spectral_library_ms_level help says the parameter "is ignored"
        search.setQuery("is ignored");
        assertEquals(List.of(), names(search));

        assertEquals(EditOutcome.applied(), session.selectRelease(C02));
        assertEquals(List.of("spectral_library_ms_level"), names(search));
        assertEquals(List.of(SearchMatch.HELP_TEXT), search.results().get(0).matchedBy());
        search.setQuery("not set");
        assertEquals(List.of(), names(search));
    }

    @Nested
    @DisplayName("filters")
    class Filters {

        @Test
        @DisplayName("Modified only: the value decides, not the origin")
        void modified() {
            ParameterSession session = startingIn(C03);
            ParameterSearchViewModel search = new ParameterSearchViewModel(session);
            search.setFilter(SearchFilter.MODIFIED, true);
            // the workflow's PIN output differs from Comet's default 0: a real change
            assertEquals(List.of("output_percolatorfile"), names(search));
            session.edit("allowed_missed_cleavage", "1");
            assertEquals(
                    List.of("allowed_missed_cleavage", "output_percolatorfile"), names(search));
            // set back by hand: origin USER, value the default -- not modified
            session.edit("allowed_missed_cleavage", "2");
            assertEquals(List.of("output_percolatorfile"), names(search));
            search.setQuery("cleavage");
            assertEquals(List.of(), names(search));
            search.setFilter(SearchFilter.MODIFIED, false);
            // search_enzyme_number's help speaks of cleavage too
            assertEquals(List.of("search_enzyme_number", "allowed_missed_cleavage"), names(search));
            assertEquals(Set.of(), search.filters());
            assertEquals(Set.of(), search.filtersProperty().get());
            assertEquals(search.results(), search.resultsProperty().get());
        }

        @Test
        @DisplayName("Errors only and Warnings only follow the report and refused edits")
        void errorsAndWarnings() {
            ParameterSession session = startingIn(C03);
            ParameterSearchViewModel search = new ParameterSearchViewModel(session);
            search.setFilter(SearchFilter.ERRORS, true);
            assertEquals(List.of(), names(search));
            session.edit("num_threads", "many");
            assertEquals(List.of("num_threads"), names(search));
            session.edit("num_threads", "4");
            assertEquals(List.of(), names(search));
            // a reversed window is an error at both bounds
            session.edit("peptide_mass_tolerance_lower", "30.0");
            assertEquals(
                    List.of("peptide_mass_tolerance_upper", "peptide_mass_tolerance_lower"),
                    names(search));
            search.setFilter(SearchFilter.ERRORS, false);
            search.setFilter(SearchFilter.WARNINGS, true);
            assertEquals(List.of(), names(search));
            // an asymmetric window is a warning at both bounds
            session.edit("peptide_mass_tolerance_lower", "-10.0");
            assertEquals(
                    List.of("peptide_mass_tolerance_upper", "peptide_mass_tolerance_lower"),
                    names(search));
            // both filters must hold
            search.setFilter(SearchFilter.ERRORS, true);
            assertEquals(List.of(), names(search));
            assertEquals(Set.of(SearchFilter.ERRORS, SearchFilter.WARNINGS), search.filters());
        }

        @Test
        @DisplayName("Errors only follows a resolution that changes the report and not the set")
        void resolution() {
            ParameterSession session = startingIn(C02);
            session.edit("variable_mod01", "15.9949 M 0 3 2 4 0 0.0");
            session.selectRelease(C03);
            ParameterSearchViewModel search = new ParameterSearchViewModel(session);
            search.setFilter(SearchFilter.ERRORS, true);
            assertEquals(List.of("variable_mod01"), names(search));
            session.resolve("variable_mod01");
            assertEquals(List.of(), names(search));
        }

        @Test
        @DisplayName("an unknown parameter's errors and warnings are the report's")
        void unknownFindings() {
            ValidationReport error =
                    new ValidationReport(
                            List.of(
                                    new Finding(
                                            Rule.UNAVAILABLE_IN_VERSION,
                                            Severity.ERROR,
                                            List.of("old_option"),
                                            Optional.empty(),
                                            "CONSTRUCTED: unavailable")));
            assertTrue(
                    ParameterSearchViewModel.admitsUnknown(
                            "old_option", error, Set.of(SearchFilter.ERRORS)));
            assertFalse(
                    ParameterSearchViewModel.admitsUnknown(
                            "old_option", error, Set.of(SearchFilter.WARNINGS)));
            assertFalse(
                    ParameterSearchViewModel.admitsUnknown(
                            "other", error, Set.of(SearchFilter.ERRORS)));
            assertTrue(
                    ParameterSearchViewModel.admitsUnknown(
                            "old_option", error, Set.of(SearchFilter.MODIFIED)));
        }

        @Test
        @DisplayName("Expert parameters: the curated Expert visibility")
        void expert() {
            ParameterSearchViewModel search = new ParameterSearchViewModel(startingIn(C02));
            search.setQuery("fragment index");
            assertEquals(7, search.results().size());
            search.setFilter(SearchFilter.EXPERT, true);
            assertEquals(
                    List.of(
                            "index_search_type",
                            "fragindex_min_ions_score",
                            "fragindex_min_ions_report",
                            "fragindex_num_spectrumpeaks",
                            "fragindex_min_fragmentmass",
                            "fragindex_max_fragmentmass",
                            "fragindex_skipreadprecursors"),
                    names(search));
            search.setQuery("threads");
            assertEquals(List.of(), names(search));
        }

        @Test
        @DisplayName("Unsupported/imported: the configuration's unknown parameters")
        void unsupported() {
            ParameterSession session = startingIn(C03);
            // CONSTRUCTED: the real 2026.03.0 -q file with one unknown parameter added
            String withUnknown =
                    Sessions.cometQ(C03)
                            .replace(
                                    "database_name = /some/path/db.fasta\n",
                                    "database_name = /some/path/db.fasta\nmy_custom_option = 7\n");
            CometParameters imported =
                    new CometParamsParser(Sessions.METADATA, C03)
                            .parse(withUnknown)
                            .model()
                            .orElseThrow();
            session.adopt(imported, Adoption.IMPORTED);
            ParameterSearchViewModel search = new ParameterSearchViewModel(session);
            assertEquals(119, search.results().size());
            search.setFilter(SearchFilter.UNSUPPORTED, true);
            SearchHit hit = only(search);
            assertEquals("my_custom_option", hit.name());
            assertTrue(hit.unknown());
            assertEquals(Optional.empty(), hit.field());
            assertEquals("my_custom_option (not a parameter of Comet 2026.03.0)", hit.label());
            search.setQuery("CUSTOM");
            assertEquals(List.of(SearchMatch.NAME), only(search).matchedBy());
            search.setQuery("seven");
            assertEquals(List.of(), names(search));
            search.setQuery("");
            // an unknown parameter is a warning, not an error, and not an Expert parameter
            search.setFilter(SearchFilter.WARNINGS, true);
            assertEquals(List.of("my_custom_option"), names(search));
            search.setFilter(SearchFilter.ERRORS, true);
            assertEquals(List.of(), names(search));
            search.setFilter(SearchFilter.ERRORS, false);
            search.setFilter(SearchFilter.EXPERT, true);
            assertEquals(List.of(), names(search));
            search.setFilter(SearchFilter.EXPERT, false);
            search.setFilter(SearchFilter.WARNINGS, false);
            search.setFilter(SearchFilter.MODIFIED, true);
            assertEquals(List.of("my_custom_option"), names(search));

            assertEquals(EditOutcome.applied(), session.removeUnknown("my_custom_option"));
            assertEquals(List.of(), names(search));
        }

        @Test
        @DisplayName("the filters' words")
        void words() {
            assertEquals(
                    List.of(
                            "Modified only",
                            "Errors only",
                            "Warnings only",
                            "Expert parameters",
                            "Unsupported/imported parameters"),
                    Arrays.stream(SearchFilter.values()).map(SearchFilter::words).toList());
        }
    }

    @Test
    @DisplayName("a hit's alias list goes with a match by alias and only with it")
    void hitRefusals() {
        assertEquals(
                "x: a match by alias names the aliases matched, and only it does",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        new SearchHit(
                                                "x",
                                                Optional.empty(),
                                                "x",
                                                List.of(SearchMatch.ALIAS),
                                                List.of()))
                        .getMessage());
        assertEquals(
                "x: a match by alias names the aliases matched, and only it does",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        new SearchHit(
                                                "x",
                                                Optional.empty(),
                                                "x",
                                                List.of(SearchMatch.NAME),
                                                List.of("y")))
                        .getMessage());
        SearchHit same =
                new SearchHit("x", Optional.empty(), "x", List.of(SearchMatch.NAME), List.of());
        assertEquals("Matched by name", same.why());
        assertTrue(same.unknown());
    }
}
