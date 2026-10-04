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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.fixtures.CometFixtures;
import org.cometgui.params.comet.fixtures.ParamsFiles;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.model.Diagnostic;
import org.cometgui.params.comet.model.ParameterValue;
import org.cometgui.params.comet.model.ValueOrigin;
import org.cometgui.params.comet.parser.CometParamsParser;
import org.cometgui.params.comet.parser.ParamsLine;
import org.cometgui.params.comet.parser.ParamsLineReader;
import org.cometgui.params.comet.parser.ParseResult;
import org.cometgui.params.comet.value.EnzymeDefinition;
import org.cometgui.params.comet.value.ValueSyntaxException;
import org.cometgui.params.comet.value.VariableModCodec;
import org.cometgui.params.comet.value.VariableModification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Phase 06 gate items 1, 4 (writer half) and 6, for every release the project installs, driven by
 * the per-release facts in {@link #RELEASES} rather than by a copy of the test per release.
 *
 * <p>The input is each release's REAL {@code comet -q} output (its fixture); every edit to it is
 * CONSTRUCTED and labelled so. The expected bytes and SHA-256 of each release's canonical text are
 * typed here: 2026.02.2's is the value signed off in Phase 06 ({@link CanonicalWriterTest} pins the
 * same), 2026.03.0's the value this test was first seen to produce, after the round trips below
 * showed it stable -- re-pinned once, when the 2026.03.0 inline comment of {@code output_txtfile}
 * stopped naming 2026.02.2 ({@code (2026.03.0 treats 2 as 1)}): that one substitution reversed
 * gives the earlier value, {@code 3aecc834...7444}, byte for byte. A release added to the fixtures
 * without a row here fails {@link #everyFixtureReleaseHasARow()}.
 */
class ReleaseWriterGateTest {

    /**
     * One release's facts.
     *
     * @param version the release
     * @param bytes the length of its canonical -q text
     * @param sha256 that text's SHA-256
     * @param marker line 1 of it
     * @param acceptsProteinTermini whether its tuple accepts {@code ^} and {@code $}
     */
    record Release(
            String version,
            int bytes,
            String sha256,
            String marker,
            boolean acceptsProteinTermini) {

        ToolVersion tool() {
            return ToolVersion.parse(version);
        }

        @Override
        public String toString() {
            return "Comet " + version;
        }
    }

    static final List<Release> RELEASES =
            List.of(
                    new Release(
                            "2026.02.2",
                            10_656,
                            "f381afe1d48749d49a0bb5e97a0375be7e691f3c6bfa2f740e96589b4502d62b",
                            "# comet_version 2026.02 rev. 2 (6edec91)",
                            false),
                    new Release(
                            "2026.03.0",
                            10_725,
                            "c600c64f473ec46baaa760c7b1f55c78faa196dd96c36748da06c8ea9d5fcf2e",
                            "# comet_version 2026.03 rev. 0 (fa08489)",
                            true));

    static Stream<Release> releases() {
        return RELEASES.stream();
    }

    private static final CanonicalParamsWriter WRITER =
            new CanonicalParamsWriter(ParamsFiles.build());

    private static CometParamsParser parser(Release release) {
        return new CometParamsParser(ParamsFiles.metadata(), release.tool());
    }

    private static CometParameters model(Release release, String text) {
        ParseResult result = parser(release).parse(text);
        assertTrue(result.succeeded(), () -> release + ": the parse failed: " + result.errors());
        return result.model().orElseThrow();
    }

    private static String text(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static String sha256(byte[] bytes) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    @Test
    @DisplayName("every release with fixtures has a row here, and every row has fixtures")
    void everyFixtureReleaseHasARow() throws java.io.IOException {
        assertEquals(
                CometFixtures.versions(CometFixtures.root()),
                RELEASES.stream().map(Release::version).sorted().toList());
    }

    @ParameterizedTest
    @MethodSource("releases")
    @DisplayName("gate item 1: parse(-q) -> write = A; A is byte-stable over two more round trips")
    void gateItem1(Release release) throws NoSuchAlgorithmException {
        String fixture = ParamsFiles.complete(release.tool());
        byte[] first = WRITER.bytes(model(release, fixture));
        byte[] second = WRITER.bytes(model(release, text(first)));
        byte[] third = WRITER.bytes(model(release, text(second)));
        Bytes.assertSame(first, second, release + ": the second round trip");
        Bytes.assertSame(first, third, release + ": the third round trip");
        System.out.println(
                "ReleaseWriterGateTest: "
                        + release
                        + " canonical -q text, "
                        + first.length
                        + " bytes, SHA-256 "
                        + sha256(first));
        assertEquals(release.sha256(), sha256(first), release.toString());
        assertEquals(release.bytes(), first.length, release.toString());

        ParseResult again = parser(release).parse(text(first));
        assertEquals(List.of(), again.diagnostics(), release.toString());
        assertEquals(model(release, fixture), again.model().orElseThrow(), release.toString());

        List<String> lines = List.of(text(first).split("\n", -1));
        assertEquals(release.marker(), lines.get(0));
        assertEquals(
                "# Written by CometGUI 0.1.0-SNAPSHOT for Comet "
                        + release.version()
                        + ". Canonical form, generated from the typed model.",
                lines.get(1));

        List<ParamsLine.Declaration> theirs = ParamsLineReader.read(fixture).declarations();
        List<ParamsLine.Declaration> ours = ParamsLineReader.read(text(first)).declarations();
        assertEquals(118, theirs.size(), release.toString());
        assertEquals(
                theirs.stream().map(ParamsLine.Declaration::name).toList(),
                ours.stream().map(ParamsLine.Declaration::name).toList());
        for (int index = 0; index < theirs.size(); index++) {
            assertEquals(
                    theirs.get(index).value(),
                    ours.get(index).value(),
                    release + " " + theirs.get(index).name());
        }
    }

    @ParameterizedTest
    @MethodSource("releases")
    @DisplayName(
            "gate item 4: the release's table round-trips, a custom enzyme survives,"
                    + " an absent number is refused")
    void gateItem4(Release release) {
        String fixture = ParamsFiles.complete(release.tool());
        CometParameters model = model(release, fixture);
        String written = text(WRITER.bytes(model));
        List<String> theirs =
                ParamsLineReader.read(fixture).enzymeRows().stream().map(ParamsLine::text).toList();
        List<String> ours =
                ParamsLineReader.read(written).enzymeRows().stream().map(ParamsLine::text).toList();
        assertEquals(12, theirs.size(), release.toString());
        assertEquals(theirs, ours, release + ": the enzyme rows are Comet's own lines");
        assertEquals(model.enzymeTable(), model(release, written).enzymeTable());

        for (String name :
                List.of("search_enzyme_number", "search_enzyme2_number", "sample_enzyme_number")) {
            CometParameters broken =
                    model.withValue(name, new ParameterValue.Whole(42), ValueOrigin.USER);
            ParamsWriteException refusal =
                    assertThrows(ParamsWriteException.class, () -> WRITER.bytes(broken), name);
            assertEquals(name, refusal.parameter());
            assertEquals(
                    name
                            + " = 42 names enzyme 42, which is not in the [COMET_ENZYME_INFO]"
                            + " table being written (its numbers are [0, 1, 2, 3, 4, 5, 6, 7,"
                            + " 8, 9, 10, 11]); the file is not written",
                    refusal.getMessage());
        }

        // CONSTRUCTED: a custom enzyme 12, selected.
        EnzymeDefinition gluC =
                new EnzymeDefinition(12, "Glu_C", EnzymeDefinition.Sense.AFTER_RESIDUE, "DE", "P");
        CometParameters custom =
                model.withEnzymeTable(model.enzymeTable().with(gluC))
                        .withValue(
                                "search_enzyme_number",
                                new ParameterValue.Whole(12),
                                ValueOrigin.USER);
        byte[] withCustom = WRITER.bytes(custom);
        assertTrue(
                text(withCustom).endsWith("\n12. Glu_C                  1      DE          P\n"),
                release.toString());
        CometParameters back = model(release, text(withCustom));
        assertEquals(Optional.of(gluC), back.enzymeTable().byNumber(12));
        assertEquals(new ParameterValue.Whole(12), back.value("search_enzyme_number"));
        Bytes.assertSame(withCustom, WRITER.bytes(back), release + ": the custom enzyme");
    }

    @ParameterizedTest
    @MethodSource("releases")
    @DisplayName("gate item 6: an unknown parameter survives two round trips and is reported")
    void gateItem6(Release release) {
        String imported =
                ParamsFiles.completeWith(
                        release.tool(),
                        "num_results",
                        "# constructed: MS1 window\n"
                                + "ms1_mass_range = 400.0 1600.0   # MS1 m/z window\n"
                                + "precursor_NL_ions = 97.976896\n");
        ParseResult firstParse = parser(release).parse(imported);
        byte[] first = WRITER.bytes(firstParse.model().orElseThrow());
        String written = text(first);
        assertTrue(
                written.contains(
                        "\n\n"
                                + CanonicalParamsWriter.UNKNOWN_SECTION
                                + " (Comet "
                                + release.version()
                                + ")\n\n"
                                + "# constructed: MS1 window\n"
                                + "ms1_mass_range = 400.0 1600.0          # MS1 m/z window\n"
                                + "precursor_NL_ions = 97.976896\n\n#\n"),
                written);
        ParseResult secondParse = parser(release).parse(written);
        Bytes.assertSame(
                first,
                WRITER.bytes(secondParse.model().orElseThrow()),
                release + ": the second round trip with unknown parameters");
        for (ParseResult result : List.of(firstParse, secondParse)) {
            List<String> named = new ArrayList<>();
            for (Diagnostic diagnostic : result.warnings()) {
                assertEquals(Diagnostic.Code.UNKNOWN_PARAMETER, diagnostic.code());
                named.add(diagnostic.parameter().orElseThrow());
            }
            assertEquals(List.of("ms1_mass_range", "precursor_NL_ions"), named);
        }
    }

    /** CONSTRUCTED slots with the protein-terminus codes, as a user would set them. */
    private static final List<List<String>> PROTEIN_SLOTS =
            List.of(
                    List.of("variable_mod02", "42.010565 ^ 0 1 -1 0 0 0.0"),
                    List.of("variable_mod03", "-0.984016 $ 0 1 -1 0 0 0.0"),
                    List.of("variable_mod15", "42.010565 ^M 0 1 -1 0 0 0.0"));

    @ParameterizedTest
    @MethodSource("releases")
    @DisplayName("^ and $ slots: written and read back where accepted, refused by name elsewhere")
    void proteinTerminusSlots(Release release) {
        CometParameters model = model(release, ParamsFiles.complete(release.tool()));
        String line = "variable_mod02 = 42.010565 ^ 0 1 -1 0 0 0.0";
        String file =
                ParamsFiles.complete(release.tool())
                        .replace("\nvariable_mod02 = 0.0 X 0 3 -1 0 0 0.0\n", "\n" + line + "\n");
        assertTrue(
                file.contains("\n" + line + "\n"), release + ": the CONSTRUCTED edit did not land");
        if (release.acceptsProteinTermini()) {
            CometParameters edited = model;
            for (List<String> slot : PROTEIN_SLOTS) {
                edited = edited.withText(slot.get(0), slot.get(1), ValueOrigin.USER);
            }
            byte[] first = WRITER.bytes(edited);
            for (List<String> slot : PROTEIN_SLOTS) {
                assertTrue(
                        text(first).contains("\n" + slot.get(0) + " = " + slot.get(1) + "\n"),
                        slot.toString());
            }
            CometParameters back = model(release, text(first));
            for (List<String> slot : PROTEIN_SLOTS) {
                assertEquals(edited.value(slot.get(0)), back.value(slot.get(0)));
            }
            Bytes.assertSame(first, WRITER.bytes(back), release + ": ^/$ second round trip");
            assertTrue(parser(release).parse(file).succeeded(), release.toString());
            return;
        }
        String because =
                "\"^\" holds '^', which Comet "
                        + release.version()
                        + " does not accept in a residue token; its residue alphabet is A-Z, n"
                        + " (N-terminus), c (C-terminus)";
        ValueSyntaxException typed =
                assertThrows(
                        ValueSyntaxException.class,
                        () ->
                                assertNotNull(
                                        model.withText(
                                                "variable_mod02",
                                                "42.010565 ^ 0 1 -1 0 0 0.0",
                                                ValueOrigin.USER),
                                        "the text should have been refused"));
        assertEquals("variable_mod02, field 2 (residues): " + because, typed.getMessage());
        ParseResult parsed = parser(release).parse(file);
        assertEquals(1, parsed.errors().size(), parsed.errors().toString());
        assertEquals(Diagnostic.Code.UNREADABLE_VALUE, parsed.errors().get(0).code());
        assertTrue(
                parsed.errors().get(0).message().endsWith(because),
                parsed.errors().get(0).message());
        VariableModification protein =
                VariableModCodec.forVersion(
                                ParamsFiles.metadata(),
                                ToolVersion.parse(
                                        RELEASES.stream()
                                                .filter(Release::acceptsProteinTermini)
                                                .findFirst()
                                                .orElseThrow()
                                                .version()))
                        .parse("variable_mod02", "42.010565 ^ 0 1 -1 0 0 0.0");
        CometParameters smuggled =
                model.withValue(
                        "variable_mod02", new ParameterValue.Tuple(protein), ValueOrigin.USER);
        IllegalArgumentException refusal =
                assertThrows(IllegalArgumentException.class, () -> WRITER.bytes(smuggled));
        assertEquals(because + ", so it cannot be written", refusal.getMessage());
    }
}
