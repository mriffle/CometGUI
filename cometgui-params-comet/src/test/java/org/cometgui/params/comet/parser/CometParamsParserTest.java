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

package org.cometgui.params.comet.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.fixtures.ParamsFiles;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.model.Diagnostic;
import org.cometgui.params.comet.model.ParameterEntry;
import org.cometgui.params.comet.model.ParameterValue;
import org.cometgui.params.comet.model.UnknownParameter;
import org.cometgui.params.comet.model.ValueOrigin;
import org.cometgui.params.comet.schema.CuratedMetadata;
import org.cometgui.params.comet.schema.MetadataLoader;
import org.cometgui.params.comet.value.DecimalList;
import org.cometgui.params.comet.value.DecimalRange;
import org.cometgui.params.comet.value.EnzymeDefinition;
import org.cometgui.params.comet.value.IntegerRange;
import org.cometgui.params.comet.value.IonSeries;
import org.cometgui.params.comet.value.TolerancePair;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The typed parser over the REAL {@code comet -q} and {@code -p} output of Comet 2026.02.2, and
 * over CONSTRUCTED edits of the {@code -q} file that exercise one rule each. Every constructed
 * input is labelled as such; none is presented as Comet's output.
 */
class CometParamsParserTest {

    private static final CuratedMetadata METADATA = ParamsFiles.metadata();

    private static final CometParamsParser PARSER =
            new CometParamsParser(METADATA, ParamsFiles.COMET);

    private static CometParameters parsed(String text) {
        ParseResult result = PARSER.parse(text);
        assertTrue(result.succeeded(), () -> "expected a model, got " + result.errors());
        return result.model().orElseThrow();
    }

    private static Diagnostic onlyFinding(ParseResult result, Diagnostic.Code code) {
        List<Diagnostic> matching =
                result.diagnostics().stream().filter(d -> d.code() == code).toList();
        assertEquals(1, matching.size(), () -> "findings: " + result.diagnostics());
        return matching.get(0);
    }

    private static int lineOf(String text, String start) {
        String[] lines = text.split("\n", -1);
        for (int index = 0; index < lines.length; index++) {
            if (lines[index].startsWith(start)) {
                return index + 1;
            }
        }
        throw new AssertionError("no line starts with " + start);
    }

    private static void assertFailedWith(ParseResult result, Diagnostic.Code code) {
        assertFalse(result.succeeded(), "a parse with an error must produce no model");
        assertEquals(Optional.empty(), result.model());
        assertTrue(
                result.errors().stream().anyMatch(d -> d.code() == code),
                () -> "expected " + code + " among " + result.diagnostics());
    }

    @Nested
    @DisplayName("the real -q output of Comet 2026.02.2")
    class RealComplete {

        private final CometParameters model = parsed(ParamsFiles.complete());

        @Test
        @DisplayName("parses with no finding at all: every name known, the marker the selected one")
        void parsesCleanly() {
            ParseResult result = PARSER.parse(ParamsFiles.complete());
            assertEquals(List.of(), result.diagnostics());
            assertEquals(List.of(), result.model().orElseThrow().diagnostics());
        }

        @Test
        @DisplayName("all 118 parameters are IMPORTED, in -q order, none unknown")
        void everyParameterImported() {
            assertEquals(118, model.entries().size());
            assertTrue(model.entries().stream().allMatch(e -> e.origin() == ValueOrigin.IMPORTED));
            assertEquals(List.of(), model.unknownParameters());
            assertEquals("database_name", model.entries().get(0).name());
            assertEquals("add_Z_user_amino_acid", model.entries().get(117).name());
            assertEquals(ParamsFiles.COMET, model.version());
        }

        @Test
        @DisplayName("every structural kind is read into its typed value")
        void everyKindTyped() {
            assertEquals(new ParameterValue.Whole(0), model.value("num_threads"));
            assertEquals(new ParameterValue.Whole(1), model.value("search_enzyme_number"));
            assertEquals(new ParameterValue.Whole(2), model.value("isotope_error"));
            assertEquals(
                    new ParameterValue.Decimal(new BigDecimal("0.02")),
                    model.value("fragment_bin_tol"));
            assertEquals(
                    new ParameterValue.Decimal(new BigDecimal("0.0000")),
                    model.value("add_G_glycine"));
            assertEquals(new ParameterValue.Flag(true), model.value("output_pepxmlfile"));
            assertEquals(new ParameterValue.Flag(false), model.value("output_percolatorfile"));
            assertEquals(new ParameterValue.Flag(true), model.value("use_B_ions"));
            assertEquals(new ParameterValue.Text("DECOY_"), model.value("decoy_prefix"));
            assertEquals(new ParameterValue.Text("ALL"), model.value("activation_method"));
            assertEquals(
                    new ParameterValue.Text("/some/path/db.fasta"), model.value("database_name"));
            assertEquals(
                    new ParameterValue.WholeRange(new IntegerRange(5, 50)),
                    model.value("peptide_length_range"));
            assertEquals(
                    new ParameterValue.DecimalPair(
                            new DecimalRange(new BigDecimal("600.0"), new BigDecimal("5000.0"))),
                    model.value("digest_mass_range"));
            assertEquals(
                    new ParameterValue.Decimals(new DecimalList(List.of())),
                    model.value("mass_offsets"));
            ParameterValue.Tuple oxidation = (ParameterValue.Tuple) model.value("variable_mod01");
            assertEquals(new BigDecimal("15.9949"), oxidation.modification().mass());
            assertEquals("M", oxidation.modification().residues());
            assertEquals(
                    new TolerancePair(new BigDecimal("-20.0"), new BigDecimal("20.0")),
                    model.tolerancePair());
            assertEquals(Set.of(IonSeries.B, IonSeries.Y), model.ionSeries().series());
            assertFalse(model.ionSeries().neutralLossPeaks());
        }

        @Test
        @DisplayName("an empty value is kept as empty and IMPORTED, not taken as absent")
        void emptyIsAValue() {
            for (String name :
                    List.of(
                            "peff_obo",
                            "compoundmods_file",
                            "protein_modslist_file",
                            "pinfile_protein_delimiter")) {
                assertEquals(new ParameterValue.Text(""), model.value(name), name);
                assertEquals(ValueOrigin.IMPORTED, model.origin(name), name);
                assertEquals("", model.text(name), name);
            }
        }

        @Test
        @DisplayName("the enzyme table's twelve rows are read, numbers 0 to 11")
        void enzymeTable() {
            assertEquals(12, model.enzymeTable().rows().size());
            EnzymeDefinition trypsin = model.enzymeTable().byNumber(1).orElseThrow();
            assertEquals("Trypsin", trypsin.name());
            assertEquals("KR", trypsin.cutResidues());
            assertEquals("P", trypsin.noCutResidues());
            assertEquals("No_cut", model.enzymeTable().byNumber(11).orElseThrow().name());
        }

        @Test
        @DisplayName("a CRLF copy of the file parses to the same model")
        void crlfIsTheSame() {
            String crlf = ParamsFiles.complete().replace("\n", "\r\n");
            assertEquals(model, parsed(crlf));
        }
    }

    @Test
    @DisplayName("the real -p output: 96 IMPORTED, the 22 -q-only parameters at COMET_DEFAULT")
    void realDefaultsFile() {
        CometParameters model = parsed(ParamsFiles.defaults());
        CometParameters complete = parsed(ParamsFiles.complete());
        Set<String> defaulted = new TreeSet<>();
        for (ParameterEntry entry : model.entries()) {
            if (entry.origin() == ValueOrigin.COMET_DEFAULT) {
                defaulted.add(entry.name());
            } else {
                assertEquals(ValueOrigin.IMPORTED, entry.origin(), entry.name());
            }
            assertEquals(complete.value(entry.name()), entry.value(), entry.name());
        }
        assertEquals(
                new TreeSet<>(
                        List.of(
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
                                "compoundmods_file",
                                "mass_type_fragment",
                                "mass_type_parent",
                                "num_results",
                                "peff_format",
                                "peff_obo",
                                "pinfile_protein_delimiter",
                                "print_ascorepro_score",
                                "print_expect_score",
                                "protein_modslist_file",
                                "spectral_library_ms_level",
                                "spectral_library_name")),
                defaulted);
        assertEquals(118, model.entries().size());
        assertEquals(List.of(), model.diagnostics());
    }

    @Test
    @DisplayName(
            "CONSTRUCTED: an empty value where the default is not empty imports the empty text")
    void emptyOverridesTheDefault() {
        CometParameters model =
                parsed(ParamsFiles.completeReplacing("decoy_prefix", "decoy_prefix ="));
        assertEquals(new ParameterValue.Text(""), model.value("decoy_prefix"));
        assertEquals(ValueOrigin.IMPORTED, model.origin("decoy_prefix"));
    }

    @Test
    @DisplayName("CONSTRUCTED: a parameter absent from the file takes the schema default")
    void absentTakesTheDefault() {
        String text =
                "# comet_version 2026.02 rev. 2 (6edec91)\n"
                        + "num_threads = 8\n"
                        + "[COMET_ENZYME_INFO]\n"
                        + "0.  Cut_everywhere         0      -           -\n"
                        + "1.  Trypsin                1      KR          P\n";
        CometParameters model = parsed(text);
        assertEquals(new ParameterValue.Whole(8), model.value("num_threads"));
        assertEquals(ValueOrigin.IMPORTED, model.origin("num_threads"));
        assertEquals(new ParameterValue.Text("DECOY_"), model.value("decoy_prefix"));
        assertEquals(ValueOrigin.COMET_DEFAULT, model.origin("decoy_prefix"));
        assertEquals(
                117,
                model.entries().stream()
                        .filter(e -> e.origin() == ValueOrigin.COMET_DEFAULT)
                        .count());
        assertEquals(2, model.enzymeTable().rows().size());
    }

    @Nested
    @DisplayName("the # comet_version marker (R-PARAM-06)")
    class Marker {

        @Test
        @DisplayName("CONSTRUCTED: another version warns, naming both, and still yields a model")
        void mismatchWarnsNamingBoth() {
            String text =
                    ParamsFiles.complete()
                            .replaceFirst(
                                    "^# comet_version 2026.02 rev. 2 \\(6edec91\\)",
                                    "# comet_version 2025.03 rev. 0 (abc1234)");
            ParseResult result = PARSER.parse(text);
            assertTrue(result.succeeded());
            Diagnostic warning = onlyFinding(result, Diagnostic.Code.VERSION_MISMATCH);
            assertEquals(Diagnostic.Severity.WARNING, warning.severity());
            assertEquals(List.of(1), warning.lines());
            assertTrue(warning.message().contains("Comet 2025.03.0"), warning.message());
            assertTrue(warning.message().contains("2025.03 rev. 0 (abc1234)"), warning.message());
            assertTrue(warning.message().contains("Comet is 2026.02.2"), warning.message());
            assertTrue(warning.message().contains("2026.02 rev. 2 (6edec91)"), warning.message());
            assertEquals(List.of(warning), result.model().orElseThrow().diagnostics());
            assertEquals(List.of(warning), result.warnings());
            assertEquals(List.of(), result.errors());
        }

        @Test
        @DisplayName("CONSTRUCTED: the same version from another build is not a mismatch")
        void sameVersionOtherBuild() {
            String text = ParamsFiles.complete().replaceFirst("\\(6edec91\\)", "(0123abc)");
            assertEquals(List.of(), PARSER.parse(text).diagnostics());
        }

        @Test
        @DisplayName("CONSTRUCTED: an unreadable marker warns, quoting it and naming the selected")
        void unreadableMarker() {
            String text =
                    ParamsFiles.complete()
                            .replaceFirst(
                                    "^# comet_version [^\\n]*", "# comet_version banana split");
            ParseResult result = PARSER.parse(text);
            Diagnostic warning = onlyFinding(result, Diagnostic.Code.VERSION_MISMATCH);
            assertTrue(warning.message().contains("\"banana split\""), warning.message());
            assertTrue(warning.message().contains("2026.02.2"), warning.message());
            assertEquals(List.of(1), warning.lines());
            assertTrue(result.succeeded());
        }

        @Test
        @DisplayName(
                "CONSTRUCTED: no marker warns that the version is unknown, naming the selected")
        void missingMarker() {
            String text = ParamsFiles.complete().replaceFirst("^# comet_version [^\\n]*\\n", "");
            ParseResult result = PARSER.parse(text);
            Diagnostic warning = onlyFinding(result, Diagnostic.Code.VERSION_MARKER_MISSING);
            assertEquals(List.of(), warning.lines());
            assertTrue(warning.message().contains("unknown"), warning.message());
            assertTrue(warning.message().contains("2026.02.2"), warning.message());
            assertTrue(warning.message().contains("2026.02 rev. 2 (6edec91)"), warning.message());
            assertTrue(result.succeeded());
            assertEquals(118, result.model().orElseThrow().entries().size());
        }

        @Test
        @DisplayName("CONSTRUCTED: a second marker is an error naming both lines")
        void secondMarker() {
            String text =
                    ParamsFiles.completeWith(
                            "num_threads", "# comet_version 2026.02 rev. 2 (6edec91)\n");
            ParseResult result = PARSER.parse(text);
            assertFailedWith(result, Diagnostic.Code.DUPLICATE_VERSION_MARKER);
            Diagnostic error = onlyFinding(result, Diagnostic.Code.DUPLICATE_VERSION_MARKER);
            assertEquals(16, lineOf(text, "num_threads"));
            assertEquals(List.of(1, 15), error.lines());
            assertTrue(error.message().contains("line 1"), error.message());
            assertTrue(error.message().contains("line 15"), error.message());
        }
    }

    @Nested
    @DisplayName("errors: no model at all (R-PARAM-08)")
    class Errors {

        @Test
        @DisplayName("CONSTRUCTED: a malformed line, reported with its number and text")
        void malformedLine() {
            String text = ParamsFiles.completeWith("num_threads", "this line means nothing\n");
            ParseResult result = PARSER.parse(text);
            assertFailedWith(result, Diagnostic.Code.MALFORMED_LINE);
            Diagnostic error = onlyFinding(result, Diagnostic.Code.MALFORMED_LINE);
            int line = lineOf(text, "this line means nothing");
            assertEquals(15, line);
            assertEquals(List.of(line), error.lines());
            assertEquals(Diagnostic.Severity.ERROR, error.severity());
            assertTrue(error.message().startsWith("line 15 "), error.message());
            assertTrue(error.message().contains("\"this line means nothing\""), error.message());
        }

        @Test
        @DisplayName("CONSTRUCTED: a parameter declared twice names both lines")
        void duplicateParameter() {
            String text = ParamsFiles.completeWith("num_results", "num_threads = 4\n");
            ParseResult result = PARSER.parse(text);
            assertFailedWith(result, Diagnostic.Code.DUPLICATE_PARAMETER);
            Diagnostic error = onlyFinding(result, Diagnostic.Code.DUPLICATE_PARAMETER);
            assertEquals(List.of(15, 126), error.lines());
            assertEquals(Optional.of("num_threads"), error.parameter());
            assertTrue(error.message().contains("line 15"), error.message());
            assertTrue(error.message().contains("line 126"), error.message());
            assertTrue(error.message().contains("silently use the value on line 126"));
        }

        @Test
        @DisplayName("CONSTRUCTED: an unknown parameter declared twice is a duplicate too")
        void duplicateUnknown() {
            String text =
                    ParamsFiles.completeWith(
                            "num_results", "ms1_mass_range = 1 2\nms1_mass_range = 3 4\n");
            ParseResult result = PARSER.parse(text);
            Diagnostic error = onlyFinding(result, Diagnostic.Code.DUPLICATE_PARAMETER);
            assertEquals(List.of(126, 127), error.lines());
            assertFalse(result.succeeded());
        }

        @Test
        @DisplayName("CONSTRUCTED: a value that is not its kind names the line and parameter")
        void unreadableValue() {
            String text = ParamsFiles.completeReplacing("num_threads", "num_threads = many");
            ParseResult result = PARSER.parse(text);
            assertFailedWith(result, Diagnostic.Code.UNREADABLE_VALUE);
            Diagnostic error = onlyFinding(result, Diagnostic.Code.UNREADABLE_VALUE);
            assertEquals(List.of(15), error.lines());
            assertEquals(Optional.of("num_threads"), error.parameter());
            assertEquals(
                    "line 15: num_threads, value: \"many\" is not a whole number", error.message());
        }

        @Test
        @DisplayName("CONSTRUCTED: a flag that is not 0 or 1 is unreadable")
        void flagOutOfRange() {
            String text = ParamsFiles.completeReplacing("use_B_ions", "use_B_ions = 2");
            Diagnostic error = onlyFinding(PARSER.parse(text), Diagnostic.Code.UNREADABLE_VALUE);
            assertTrue(error.message().contains("\"2\" is not 0 or 1"), error.message());
        }

        @Test
        @DisplayName("CONSTRUCTED: a tuple with seven fields is unreadable")
        void shortTuple() {
            String text =
                    ParamsFiles.completeReplacing(
                            "variable_mod03", "variable_mod03 = 0.0 X 0 3 -1 0 0");
            Diagnostic error = onlyFinding(PARSER.parse(text), Diagnostic.Code.UNREADABLE_VALUE);
            assertEquals(List.of(55), error.lines());
            assertEquals(Optional.of("variable_mod03"), error.parameter());
        }

        @Test
        @DisplayName("CONSTRUCTED: two numbers where one decimal belongs is unreadable")
        void twoDecimals() {
            String text =
                    ParamsFiles.completeReplacing(
                            "fragment_bin_tol", "fragment_bin_tol = 0.02 0.03");
            Diagnostic error = onlyFinding(PARSER.parse(text), Diagnostic.Code.UNREADABLE_VALUE);
            assertTrue(error.message().contains("is not one number"), error.message());
        }

        @Test
        @DisplayName("CONSTRUCTED: a file with no enzyme table yields no model")
        void noEnzymeTable() {
            String complete = ParamsFiles.complete();
            String text = complete.substring(0, complete.indexOf("[COMET_ENZYME_INFO]"));
            ParseResult result = PARSER.parse(text);
            assertFailedWith(result, Diagnostic.Code.ENZYME_TABLE_MISSING);
            assertEquals(1, result.diagnostics().size(), result.diagnostics()::toString);
            assertEquals(List.of(), result.diagnostics().get(0).lines());
        }

        @Test
        @DisplayName("CONSTRUCTED: an enzyme number defined twice names both lines")
        void duplicateEnzymeNumber() {
            String text =
                    ParamsFiles.complete() + "1.  Glu_C                  1      DE          P\n";
            ParseResult result = PARSER.parse(text);
            assertFailedWith(result, Diagnostic.Code.DUPLICATE_ENZYME_NUMBER);
            Diagnostic error = onlyFinding(result, Diagnostic.Code.DUPLICATE_ENZYME_NUMBER);
            assertEquals(List.of(188, 200), error.lines());
            assertTrue(error.message().contains("enzyme number 1"), error.message());
        }

        @Test
        @DisplayName("CONSTRUCTED: an enzyme row with sense 2 is unreadable")
        void unreadableEnzymeRow() {
            String text =
                    ParamsFiles.complete() + "12. Glu_C                  2      DE          P\n";
            ParseResult result = PARSER.parse(text);
            assertFailedWith(result, Diagnostic.Code.UNREADABLE_ENZYME_ROW);
            Diagnostic error = onlyFinding(result, Diagnostic.Code.UNREADABLE_ENZYME_ROW);
            assertEquals(List.of(200), error.lines());
            assertTrue(error.message().startsWith("line 200: "), error.message());
        }

        @Test
        @DisplayName("CONSTRUCTED: an unknown parameter that could not be written back is refused")
        void unwritableUnknown() {
            String text = ParamsFiles.completeWith("num_results", "odd_knob = 1 # a\rb\n");
            ParseResult result = PARSER.parse(text);
            Diagnostic error = onlyFinding(result, Diagnostic.Code.UNREADABLE_VALUE);
            assertEquals(Optional.of("odd_knob"), error.parameter());
            assertTrue(error.message().contains("cannot be kept as written"), error.message());
            assertFalse(result.succeeded());
        }

        @Test
        @DisplayName("CONSTRUCTED: every error is reported, in line order, not just the first")
        void everyErrorReported() {
            String text =
                    ParamsFiles.completeReplacing("num_threads", "num_threads = many")
                            .replace("use_B_ions = 1\n", "use_B_ions = 1\nnonsense here\n");
            ParseResult result = PARSER.parse(text);
            assertEquals(
                    List.of(Diagnostic.Code.UNREADABLE_VALUE, Diagnostic.Code.MALFORMED_LINE),
                    result.diagnostics().stream().map(Diagnostic::code).toList());
            assertEquals(List.of(15), result.diagnostics().get(0).lines());
            assertEquals(List.of(82), result.diagnostics().get(1).lines());
        }
    }

    @Nested
    @DisplayName("unknown parameters (R-PARAM-07): preserved and reported")
    class Unknown {

        @Test
        @DisplayName(
                "CONSTRUCTED: ms1_mass_range (read by Comet, not written by -q) is kept with its"
                        + " value, inline comment and comment lines, and warned about")
        void keptAndReported() {
            String text =
                    ParamsFiles.completeWith(
                            "num_results",
                            "# constructed: an MS1 window\n"
                                    + "#   second comment line\n"
                                    + "ms1_mass_range = 400.0 1600.0   # MS1 m/z window\n");
            ParseResult result = PARSER.parse(text);
            CometParameters model = result.model().orElseThrow();
            assertEquals(
                    List.of(
                            new UnknownParameter(
                                    "ms1_mass_range",
                                    "400.0 1600.0",
                                    Optional.of("MS1 m/z window"),
                                    List.of(
                                            "# constructed: an MS1 window",
                                            "#   second comment line"),
                                    128)),
                    model.unknownParameters());
            Diagnostic warning = onlyFinding(result, Diagnostic.Code.UNKNOWN_PARAMETER);
            assertEquals(Diagnostic.Severity.WARNING, warning.severity());
            assertEquals(List.of(128), warning.lines());
            assertEquals(Optional.of("ms1_mass_range"), warning.parameter());
            assertTrue(warning.message().contains("ms1_mass_range"), warning.message());
            assertTrue(warning.message().contains("2026.02.2"), warning.message());
            assertTrue(warning.message().contains("kept as imported"), warning.message());
            assertEquals(List.of(warning), model.diagnostics());
            assertEquals(118, model.entries().size());
        }

        @Test
        @DisplayName("CONSTRUCTED: comment lines stop at a blank line; an empty value is kept")
        void commentsStopAtABlankLine() {
            String text =
                    ParamsFiles.completeWith(
                            "num_results", "# not this one\n\n# this one\nprecursor_NL_ions =\n");
            UnknownParameter unknown = parsed(text).unknownParameters().get(0);
            assertEquals(List.of("# this one"), unknown.comments());
            assertEquals("", unknown.value());
            assertEquals(Optional.empty(), unknown.inlineComment());
        }

        @Test
        @DisplayName("CONSTRUCTED: comment lines stop at the previous declaration")
        void commentsStopAtADeclaration() {
            String text =
                    ParamsFiles.completeWith(
                            "num_results", "knob_one = 1\n# about two\nknob_two = 2 #\n");
            CometParameters model = parsed(text);
            assertEquals(2, model.unknownParameters().size());
            assertEquals(List.of(), model.unknownParameters().get(0).comments());
            assertEquals(List.of("# about two"), model.unknownParameters().get(1).comments());
            assertEquals(Optional.of(""), model.unknownParameters().get(1).inlineComment());
            assertEquals(2, model.diagnostics().size());
        }

        @Test
        @DisplayName("CONSTRUCTED: a CRLF comment line is kept without its carriage return")
        void crlfComment() {
            String text = ParamsFiles.completeWith("num_results", "# crlf comment\r\nknob = 1\r\n");
            assertEquals(
                    List.of("# crlf comment"), parsed(text).unknownParameters().get(0).comments());
        }

        @Test
        @DisplayName("CONSTRUCTED: variable_mod16 is unknown for a fifteen-slot version")
        void sixteenthSlot() {
            String text =
                    ParamsFiles.completeWith(
                            "max_variable_mods_in_peptide",
                            "variable_mod16 = 0.0 X 0 3 -1 0 0 0.0\n");
            ParseResult result = PARSER.parse(text);
            assertEquals(
                    "variable_mod16",
                    result.model().orElseThrow().unknownParameters().get(0).name());
            assertEquals(
                    Optional.of("variable_mod16"),
                    onlyFinding(result, Diagnostic.Code.UNKNOWN_PARAMETER).parameter());
        }
    }

    @Test
    @DisplayName(
            "CONSTRUCTED metadata: a parameter modelled only for a later version is kept as"
                    + " unknown and reported as NOT_IN_VERSION")
    void notInVersion() {
        String json = bundledJson();
        int versionsEnd = json.indexOf("\n  ],\n  \"categories\"");
        String later =
                ",\n    {\"version\": \"2027.01.0\", \"marker\": \"2027.01 rev. 0\","
                        + " \"parameterPages\": \"https://example.org/pages/\","
                        + " \"source\": \"https://example.org/source/\","
                        + " \"variableModTuple\": {\"source\": \"https://example.org/c\","
                        + " \"fields\": ["
                        + "{\"field\": \"MASS\", \"kind\": \"DECIMAL\", \"pair\": false},"
                        + "{\"field\": \"RESIDUES\", \"kind\": \"RESIDUES\", \"pair\": false},"
                        + "{\"field\": \"BINARY_GROUP\", \"kind\": \"INTEGER\", \"pair\": false},"
                        + "{\"field\": \"COUNT\", \"kind\": \"INTEGER\", \"pair\": true},"
                        + "{\"field\": \"TERMINAL_DISTANCE\", \"kind\": \"INTEGER\","
                        + " \"pair\": false},"
                        + "{\"field\": \"TERMINUS\", \"kind\": \"INTEGER\", \"pair\": false},"
                        + "{\"field\": \"REQUIRED\", \"kind\": \"INTEGER\", \"pair\": false},"
                        + "{\"field\": \"NEUTRAL_LOSS\", \"kind\": \"DECIMAL\", \"pair\": true}"
                        + "]}}";
        int parametersEnd = json.lastIndexOf("\n  ]\n}");
        String knob =
                ",\n    {\"name\": \"future_knob\", \"displayName\": \"Future knob\","
                        + " \"category\": \"misc\", \"kind\": \"INTEGER\","
                        + " \"visibility\": \"EXPERT\", \"default\": \"0\", \"min\": null,"
                        + " \"max\": null, \"choices\": [], \"shortHelp\": \"Constructed.\","
                        + " \"inlineComment\": null, \"helpUrl\": \"https://example.org/k\","
                        + " \"versions\": {\"from\": \"2027.01.0\", \"through\": null},"
                        + " \"serialization\": \"SINGLE_VALUE\", \"validators\": [],"
                        + " \"aliases\": [], \"related\": []}";
        String edited =
                json.substring(0, versionsEnd)
                        + later
                        + json.substring(versionsEnd, parametersEnd)
                        + knob
                        + json.substring(parametersEnd);
        CuratedMetadata metadata = MetadataLoader.load(edited);
        String text = ParamsFiles.completeWith("num_results", "future_knob = 3 # later\n");
        ParseResult result = new CometParamsParser(metadata, ParamsFiles.COMET).parse(text);
        Diagnostic warning = onlyFinding(result, Diagnostic.Code.NOT_IN_VERSION);
        assertEquals(Optional.of("future_knob"), warning.parameter());
        assertTrue(warning.message().contains("other Comet versions"), warning.message());
        UnknownParameter kept = result.model().orElseThrow().unknownParameters().get(0);
        assertEquals("future_knob", kept.name());
        assertEquals("3", kept.value());
        assertEquals(Optional.of("later"), kept.inlineComment());
        assertEquals(118, result.model().orElseThrow().entries().size());
    }

    private static String bundledJson() {
        try (var in = MetadataLoader.class.getResourceAsStream(MetadataLoader.RESOURCE)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    @Test
    @DisplayName("a version the metadata was not curated against is refused at construction")
    void uncuratedVersion() {
        IllegalArgumentException failure =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new CometParamsParser(METADATA, ToolVersion.parse("2019.01.5")));
        assertTrue(failure.getMessage().contains("2019.01.5"), failure.getMessage());
    }
}
