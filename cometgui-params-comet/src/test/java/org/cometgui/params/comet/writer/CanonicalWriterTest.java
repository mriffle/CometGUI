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

package org.cometgui.params.comet.writer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.cometgui.domain.build.BuildIdentity;
import org.cometgui.params.comet.fixtures.ParamsFiles;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.model.Diagnostic;
import org.cometgui.params.comet.model.ParameterEntry;
import org.cometgui.params.comet.model.ParameterValue;
import org.cometgui.params.comet.model.ValueOrigin;
import org.cometgui.params.comet.parser.CometParamsParser;
import org.cometgui.params.comet.parser.ParamsLine;
import org.cometgui.params.comet.parser.ParamsLineReader;
import org.cometgui.params.comet.parser.ParseResult;
import org.cometgui.params.comet.value.EnzymeDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The canonical writer over the model of the REAL {@code comet -q} output of Comet 2026.02.2: gate
 * item 1 (byte-stable double round trip), the writer half of gate item 4 (enzyme refusal, a custom
 * enzyme surviving a file round trip) and gate item 6 (an unknown parameter written back and
 * reported). Inputs edited from the fixture are CONSTRUCTED and labelled so.
 */
class CanonicalWriterTest {

    private static final CometParamsParser PARSER =
            new CometParamsParser(ParamsFiles.metadata(), ParamsFiles.COMET);

    private static final CanonicalParamsWriter WRITER =
            new CanonicalParamsWriter(ParamsFiles.build());

    private static ParseResult parse(String text) {
        return PARSER.parse(text);
    }

    private static CometParameters model(String text) {
        ParseResult result = parse(text);
        assertTrue(result.succeeded(), () -> "the parse failed: " + result.errors());
        return result.model().orElseThrow();
    }

    private static String text(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    @Nested
    @DisplayName("gate item 1: the real -q output, parsed and written, then again")
    class DoubleRoundTrip {

        private final byte[] first = WRITER.bytes(model(ParamsFiles.complete()));

        @Test
        @DisplayName("parse(-q) -> write = A; parse(A) -> write = A, byte for byte")
        void secondRoundTripIsIdentical() {
            byte[] second = WRITER.bytes(model(text(first)));
            Bytes.assertSame(first, second, "the second round trip of the real -q output");
            byte[] third = WRITER.bytes(model(text(second)));
            Bytes.assertSame(first, third, "the third round trip of the real -q output");
        }

        @Test
        @DisplayName("A is the exact text signed off at 14e43da (SHA-256 f381afe1...d62b)")
        void textUnchanged() throws java.security.NoSuchAlgorithmException {
            assertEquals(
                    "f381afe1d48749d49a0bb5e97a0375be7e691f3c6bfa2f740e96589b4502d62b",
                    java.util.HexFormat.of()
                            .formatHex(
                                    java.security.MessageDigest.getInstance("SHA-256")
                                            .digest(first)));
            assertEquals(10_656, first.length);
        }

        @Test
        @DisplayName("parse(A) has the same values, the same table and no finding")
        void secondParseIsTheSameModel() {
            ParseResult again = parse(text(first));
            assertEquals(List.of(), again.diagnostics());
            CometParameters original = model(ParamsFiles.complete());
            assertEquals(original, again.model().orElseThrow());
        }

        @Test
        @DisplayName("writing one model twice gives the same bytes")
        void deterministic() {
            CometParameters model = model(ParamsFiles.complete());
            Bytes.assertSame(WRITER.bytes(model), WRITER.bytes(model), "two writes of one model");
            Bytes.assertSame(
                    WRITER.bytes(model),
                    new CanonicalParamsWriter(ParamsFiles.build()).bytes(model),
                    "two writers for one build");
        }

        @Test
        @DisplayName("every value is written exactly as the real -q file writes it")
        void valuesAsComet() {
            List<ParamsLine.Declaration> theirs =
                    ParamsLineReader.read(ParamsFiles.complete()).declarations();
            List<ParamsLine.Declaration> ours = ParamsLineReader.read(text(first)).declarations();
            assertEquals(118, theirs.size());
            assertEquals(
                    theirs.stream().map(ParamsLine.Declaration::name).toList(),
                    ours.stream().map(ParamsLine.Declaration::name).toList());
            for (int index = 0; index < theirs.size(); index++) {
                assertEquals(
                        theirs.get(index).value(),
                        ours.get(index).value(),
                        theirs.get(index).name());
            }
        }

        @Test
        @DisplayName("the enzyme rows are Comet's own lines, after the header, at the end")
        void enzymeTableLast() {
            List<String> theirs =
                    ParamsLineReader.read(ParamsFiles.complete()).enzymeRows().stream()
                            .map(ParamsLine::text)
                            .toList();
            String written = text(first);
            List<String> lines = List.of(written.split("\n", -1));
            int header = lines.indexOf("[COMET_ENZYME_INFO]");
            assertEquals(theirs, lines.subList(header + 1, header + 13));
            assertEquals(
                    List.of(
                            "add_Z_user_amino_acid = 0.0000         # added to Z - avg.   0.0000,"
                                    + " mono.   0.00000",
                            "",
                            "#",
                            "# COMET_ENZYME_INFO _must_ be at the end of this parameters file",
                            "#"),
                    lines.subList(header - 5, header));
            assertEquals(List.of(""), lines.subList(header + 13, lines.size()));
            assertEquals(1, lines.stream().filter("[COMET_ENZYME_INFO]"::equals).count());
            assertTrue(
                    lines.indexOf(
                                    "add_Z_user_amino_acid = 0.0000         # added to Z - avg.  "
                                            + " 0.0000, mono.   0.00000")
                            < header);
        }

        @Test
        @DisplayName("line 1 is the regenerated marker; line 2 names CometGUI and Comet versions")
        void header() {
            List<String> lines = List.of(text(first).split("\n", -1));
            assertEquals("# comet_version 2026.02 rev. 2 (6edec91)", lines.get(0));
            assertEquals(
                    "# Written by CometGUI 0.1.0-SNAPSHOT for Comet 2026.02.2. Canonical form,"
                            + " generated from the typed model.",
                    lines.get(1));
            assertEquals("database_name = /some/path/db.fasta", lines.get(4));
        }

        @Test
        @DisplayName("only \\n line ends, a final \\n, no trailing white space, no \\r")
        void lineEnds() {
            String written = text(first);
            assertFalse(written.contains("\r"));
            assertTrue(written.endsWith("\n"));
            assertFalse(written.endsWith("\n\n"));
            for (String line : written.split("\n")) {
                assertEquals(line.stripTrailing(), line, "trailing white space: " + line);
            }
        }

        @Test
        @DisplayName("curated inline comments, at column 40, where the metadata has one")
        void inlineComments() {
            List<String> lines = List.of(text(first).split("\n", -1));
            assertTrue(
                    lines.contains(
                            "decoy_search = 0                       # 0=no (default),"
                                    + " 1=internal decoy concatenated, 2=internal decoy"
                                    + " separate"),
                    "decoy_search");
            assertTrue(
                    lines.contains(
                            "peff_obo =                             # path to PSI Mod or Unimod"
                                    + " OBO file"),
                    "peff_obo");
            assertTrue(lines.contains("variable_mod02 = 0.0 X 0 3 -1 0 0 0.0"), "no comment");
            assertTrue(
                    lines.contains(
                            "isotope_error = 2                      # 0=off, 1=0/1 (C13 error),"
                                    + " 2=0/1/2, 3=0/1/2/3, 4=-1/0/1/2/3, 5=-1/0/1, 6=-3 to +3,"
                                    + " 7=-8/-4/0/+4/+8"),
                    "isotope_error");
            assertTrue(
                    lines.contains(
                            "spectral_library_ms_level = 1          # ignored by Comet 2026.02.2,"
                                    + " which reads speclib_ms_level instead"),
                    "spectral_library_ms_level");
        }

        @Test
        @DisplayName("a long declaration gets one space before its comment")
        void longDeclaration() {
            CometParameters model =
                    model(ParamsFiles.complete())
                            .withText(
                                    "database_name",
                                    "/a/very/long/path/to/a/protein/database.fasta",
                                    ValueOrigin.USER);
            String written = text(WRITER.bytes(model));
            assertTrue(
                    written.contains(
                            "\ndatabase_name = /a/very/long/path/to/a/protein/database.fasta\n"));
            CometParameters decoys =
                    model.withText("decoy_prefix", "A_VERY_LONG_DECOY_PREFIX__", ValueOrigin.USER);
            assertTrue(
                    text(WRITER.bytes(decoys))
                            .contains(
                                    "\ndecoy_prefix = A_VERY_LONG_DECOY_PREFIX__ # decoy entries"));
        }

        @Test
        @DisplayName("another CometGUI version changes the header line and nothing else")
        void otherBuild() {
            CometParameters model = model(ParamsFiles.complete());
            String ours = text(WRITER.bytes(model));
            String theirs =
                    text(
                            new CanonicalParamsWriter(
                                            BuildIdentity.of(
                                                    "9.9.9",
                                                    BuildIdentity.UNKNOWN_COMMIT,
                                                    Instant.EPOCH))
                                    .bytes(model));
            assertNotEquals(ours, theirs);
            assertEquals(ours.replace("CometGUI 0.1.0-SNAPSHOT", "CometGUI 9.9.9"), theirs);
        }
    }

    @Nested
    @DisplayName("gate item 4, writer half: enzyme numbers")
    class Enzymes {

        private final CometParameters model = model(ParamsFiles.complete());

        @Test
        @DisplayName("each of the three enzyme references is refused when its number is absent")
        void refusesAnAbsentNumber() {
            for (String name :
                    List.of(
                            "search_enzyme_number",
                            "search_enzyme2_number",
                            "sample_enzyme_number")) {
                CometParameters broken =
                        model.withValue(name, new ParameterValue.Whole(42), ValueOrigin.USER);
                ParamsWriteException refusal =
                        assertThrows(ParamsWriteException.class, () -> WRITER.write(broken), name);
                assertEquals(name, refusal.parameter());
                assertEquals(
                        name
                                + " = 42 names enzyme 42, which is not in the [COMET_ENZYME_INFO]"
                                + " table being written (its numbers are [0, 1, 2, 3, 4, 5, 6, 7,"
                                + " 8, 9, 10, 11]); the file is not written",
                        refusal.getMessage());
            }
        }

        @Test
        @DisplayName("removing the row a parameter names makes the model unwritable")
        void refusesARemovedRow() {
            CometParameters broken = model.withEnzymeTable(model.enzymeTable().without(1));
            ParamsWriteException refusal =
                    assertThrows(ParamsWriteException.class, () -> WRITER.bytes(broken));
            assertEquals("search_enzyme_number", refusal.parameter());
            assertTrue(refusal.getMessage().contains("names enzyme 1,"), refusal.getMessage());
        }

        @Test
        @DisplayName("the last row's number is accepted: the refusal is about absence only")
        void acceptsTheLastRow() {
            CometParameters last =
                    model.withValue(
                            "search_enzyme_number", new ParameterValue.Whole(11), ValueOrigin.USER);
            assertTrue(text(WRITER.bytes(last)).contains("\nsearch_enzyme_number = 11 "));
        }

        @Test
        @DisplayName("CONSTRUCTED custom enzyme 12 survives a full file round trip, selected")
        void customEnzymeSurvives() {
            EnzymeDefinition gluC =
                    new EnzymeDefinition(
                            12, "Glu_C", EnzymeDefinition.Sense.AFTER_RESIDUE, "DE", "P");
            CometParameters custom =
                    model.withEnzymeTable(model.enzymeTable().with(gluC))
                            .withValue(
                                    "search_enzyme_number",
                                    new ParameterValue.Whole(12),
                                    ValueOrigin.USER);
            byte[] written = WRITER.bytes(custom);
            assertTrue(
                    text(written).endsWith("\n12. Glu_C                  1      DE          P\n"),
                    text(written));
            CometParameters back = model(text(written));
            assertEquals(Optional.of(gluC), back.enzymeTable().byNumber(12));
            assertEquals(13, back.enzymeTable().rows().size());
            assertEquals(new ParameterValue.Whole(12), back.value("search_enzyme_number"));
            assertEquals(ValueOrigin.IMPORTED, back.origin("search_enzyme_number"));
            Bytes.assertSame(written, WRITER.bytes(back), "the custom enzyme's second write");
        }
    }

    @Nested
    @DisplayName("gate item 6 / AC-PAR-06: unknown parameters are written back and reported")
    class Unknown {

        private static final String BLOCK =
                "# constructed: MS1 window\n"
                        + "ms1_mass_range = 400.0 1600.0   # MS1 m/z window\n"
                        + "precursor_NL_ions = 97.976896\n";

        private final String imported = ParamsFiles.completeWith("num_results", BLOCK);

        @Test
        @DisplayName("CONSTRUCTED: value, inline comment and comment line survive two round trips")
        void survive() {
            ParseResult firstParse = parse(imported);
            byte[] first = WRITER.bytes(firstParse.model().orElseThrow());
            String written = text(first);
            assertTrue(
                    written.contains(
                            "\n\n"
                                    + CanonicalParamsWriter.UNKNOWN_SECTION
                                    + " (Comet 2026.02.2)\n\n"
                                    + "# constructed: MS1 window\n"
                                    + "ms1_mass_range = 400.0 1600.0          # MS1 m/z window\n"
                                    + "precursor_NL_ions = 97.976896\n\n#\n"),
                    written);
            assertTrue(written.indexOf("ms1_mass_range") < written.indexOf("[COMET_ENZYME_INFO]"));
            ParseResult secondParse = parse(written);
            assertEquals(
                    firstParse.model().orElseThrow().unknownParameters().stream()
                            .map(u -> List.of(u.name(), u.value(), u.inlineComment(), u.comments()))
                            .toList(),
                    secondParse.model().orElseThrow().unknownParameters().stream()
                            .map(u -> List.of(u.name(), u.value(), u.inlineComment(), u.comments()))
                            .toList());
            Bytes.assertSame(
                    first,
                    WRITER.bytes(secondParse.model().orElseThrow()),
                    "the second round trip with unknown parameters");
        }

        @Test
        @DisplayName("CONSTRUCTED: each parse reports each unknown parameter by name and line")
        void reported() {
            for (ParseResult result :
                    List.of(
                            parse(imported),
                            parse(text(WRITER.bytes(parse(imported).model().orElseThrow()))))) {
                List<String> named = new ArrayList<>();
                for (Diagnostic diagnostic : result.warnings()) {
                    assertEquals(Diagnostic.Code.UNKNOWN_PARAMETER, diagnostic.code());
                    assertEquals(1, diagnostic.lines().size());
                    named.add(diagnostic.parameter().orElseThrow());
                }
                assertEquals(List.of("ms1_mass_range", "precursor_NL_ions"), named);
            }
        }

        @Test
        @DisplayName("removed explicitly, an unknown parameter is not written")
        void removedExplicitly() {
            CometParameters model =
                    parse(imported).model().orElseThrow().withoutUnknown("ms1_mass_range");
            String written = text(WRITER.bytes(model));
            assertFalse(written.contains("ms1_mass_range"));
            assertTrue(written.contains("\nprecursor_NL_ions = 97.976896\n"));
        }

        @Test
        @DisplayName("no unknown parameters, no unknown section")
        void noSection() {
            assertFalse(
                    text(WRITER.bytes(model(ParamsFiles.complete())))
                            .contains(CanonicalParamsWriter.UNKNOWN_SECTION));
        }

        @Test
        @DisplayName("CONSTRUCTED: a bare '#' comment and an empty value round-trip unchanged")
        void bareHashAndEmpty() {
            String text = ParamsFiles.completeWith("num_results", "odd_knob = #\n");
            byte[] first = WRITER.bytes(model(text));
            assertTrue(text(first).contains("\nodd_knob =                             #\n"));
            Bytes.assertSame(first, WRITER.bytes(model(text(first))), "bare # round trip");
        }
    }

    @Test
    @DisplayName("an imported value's origin does not change what is written, only the model")
    void originIsNotWritten() {
        CometParameters model = model(ParamsFiles.complete());
        List<ParameterEntry> entries = model.entries();
        CometParameters changed = model;
        for (ParameterEntry entry : entries) {
            changed = changed.withOrigin(entry.name(), ValueOrigin.PRESET);
        }
        assertNotEquals(model, changed);
        Bytes.assertSame(WRITER.bytes(model), WRITER.bytes(changed), "origins are not written");
    }
}
