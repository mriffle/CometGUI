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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.fixtures.CometManifest;
import org.cometgui.params.comet.parser.ParamsLine;
import org.cometgui.params.comet.parser.ParamsLineReader;
import org.cometgui.params.comet.schema.CuratedMetadata;
import org.cometgui.params.comet.schema.MetadataLoader;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * Gate item 3: every tuple form round-trips in every slot, for every curated release the project
 * installs.
 *
 * <p>For each release in {@link #RELEASES} -- Comet 2026.02.2 and 2026.03.0 -- and each tuple slot
 * the bundled metadata gives that release, every one of the {@link TupleForms#ALL} forms
 * (CONSTRUCTED from upstream documentation, see there) is put on a one-line {@code comet.params}
 * text {@code variable_modNN = <form>}, classified by the project's line reader, parsed by the
 * codec built from that release's version record, checked field by field against the form's
 * expected value, and formatted back to the form's text exactly. The {@link
 * TupleForms#PROTEIN_TERMINI} forms ({@code ^}, {@code $}) round-trip the same way where the
 * release accepts them and are REFUSED in every slot where it does not, with a diagnostic naming
 * the release -- including Comet 2024.01.0, the migration fixture's release. Which releases accept
 * them is typed here, not read from the metadata, so a version record that gave the codes to the
 * wrong release fails this test. One dynamic test per release, form and slot.
 */
class VariableModRoundTripTest {

    private static final CuratedMetadata METADATA = MetadataLoader.loadBundled();

    /**
     * Each release under test, and whether it accepts {@code ^} and {@code $}: Comet 2026.03.0
     * added them (its own variable_modXX page and source); 2026.02.2 and 2024.01.0 have no code for
     * them (docs/developer/comet_parameter_schema.rst).
     */
    private static final Map<String, Boolean> RELEASES =
            new TreeMap<>(Map.of("2026.02.2", false, "2026.03.0", true, "2024.01.0", false));

    /** The releases the project installs, whose round trip is gate item 3. */
    private static final List<String> INSTALLED = List.of("2026.02.2", "2026.03.0");

    private static final Map<String, AtomicInteger> RAN = new ConcurrentSkipListMap<>();

    private static final Map<String, AtomicInteger> REFUSED = new ConcurrentSkipListMap<>();

    private static VariableModCodec codec(String release) {
        return VariableModCodec.forVersion(METADATA, ToolVersion.parse(release));
    }

    @Test
    @DisplayName("Comet 2026.02.2 and 2026.03.0 each have fifteen slots, variable_mod01 to 15")
    void fifteenSlots() {
        List<String> expected =
                IntStream.rangeClosed(1, 15)
                        .mapToObj(n -> "variable_mod" + (n < 10 ? "0" : "") + n)
                        .toList();
        for (String release : INSTALLED) {
            assertEquals(expected, codec(release).slots(), release);
        }
    }

    /**
     * Gate item 3 for Comet 2026.02.2: 17 forms x 15 slots. Kept as its own entry point, with its
     * count, because {@code scripts/verify-param-gates.sh} selects it by name and grades it by that
     * count.
     */
    @TestFactory
    @DisplayName("every form, in every slot: Comet 2026.02.2")
    List<DynamicTest> everyFormInEverySlot() {
        List<DynamicTest> tests = everyFormInEverySlotOf("2026.02.2");
        assertEquals(15 * TupleForms.ALL.size(), tests.size());
        return tests;
    }

    /** Gate item 3 for Comet 2026.03.0: the 17 forms and the 9 with {@code ^}/{@code $}, x 15. */
    @TestFactory
    @DisplayName("every form, in every slot: Comet 2026.03.0, with ^ and $")
    List<DynamicTest> everyFormInEverySlotOfComet202603() {
        List<DynamicTest> tests = everyFormInEverySlotOf("2026.03.0");
        assertEquals(
                15 * (TupleForms.ALL.size() + TupleForms.PROTEIN_TERMINI.size()), tests.size());
        return tests;
    }

    @Test
    @DisplayName("the releases with an entry point above are the releases the manifest installs")
    void everyInstalledReleaseIsCovered() throws java.io.IOException {
        assertEquals(
                INSTALLED,
                CometManifest.cometRows(CometManifest.repositoryManifest()).stream()
                        .map(CometManifest.Row::version)
                        .distinct()
                        .sorted()
                        .toList());
    }

    /**
     * The round trips of one release: its forms -- with ^ and $ where it accepts them -- x slots.
     */
    private static List<DynamicTest> everyFormInEverySlotOf(String release) {
        List<DynamicTest> tests = new ArrayList<>();
        VariableModCodec codec = codec(release);
        List<TupleForms.Form> forms = new ArrayList<>(TupleForms.ALL);
        if (RELEASES.get(release)) {
            forms.addAll(TupleForms.PROTEIN_TERMINI);
        }
        for (String slot : codec.slots()) {
            for (TupleForms.Form form : forms) {
                tests.add(
                        DynamicTest.dynamicTest(
                                release + " " + slot + ": " + form,
                                () -> roundTrip(release, codec, slot, form)));
            }
        }
        return tests;
    }

    @TestFactory
    @DisplayName("^ and $ are refused in every slot of a release that does not accept them")
    List<DynamicTest> proteinTerminiRefusedElsewhere() {
        List<DynamicTest> tests = new ArrayList<>();
        for (Map.Entry<String, Boolean> release : RELEASES.entrySet()) {
            if (release.getValue()) {
                continue;
            }
            VariableModCodec codec = codec(release.getKey());
            for (String slot : codec.slots()) {
                for (TupleForms.Form form : TupleForms.PROTEIN_TERMINI) {
                    tests.add(
                            DynamicTest.dynamicTest(
                                    release.getKey() + " " + slot + ": " + form,
                                    () -> refused(release.getKey(), codec, slot, form)));
                }
            }
        }
        assertEquals(2 * 15 * TupleForms.PROTEIN_TERMINI.size(), tests.size());
        return tests;
    }

    private static void roundTrip(
            String release, VariableModCodec codec, String slot, TupleForms.Form form) {
        String line = slot + " = " + form.text() + "\n";
        List<ParamsLine.Declaration> declarations = ParamsLineReader.read(line).declarations();
        assertEquals(1, declarations.size(), line);
        ParamsLine.Declaration declaration = declarations.get(0);
        assertEquals(slot, declaration.name());
        VariableModification value = codec.parse(declaration.name(), declaration.value());
        assertEquals(form.expected(), value, form.what());
        assertEquals(form.text(), codec.format(value), form.what());
        assertEquals(value, codec.parse(slot, codec.format(value)), form.what());
        RAN.computeIfAbsent(release, r -> new AtomicInteger()).incrementAndGet();
    }

    private static void refused(
            String release, VariableModCodec codec, String slot, TupleForms.Form form) {
        String line = slot + " = " + form.text() + "\n";
        ParamsLine.Declaration declaration =
                ParamsLineReader.read(line).declarations().stream().findFirst().orElseThrow();
        String residues = form.expected().residues();
        char first =
                (char) residues.chars().filter(c -> c == '^' || c == '$').findFirst().orElseThrow();
        String because =
                "\""
                        + residues
                        + "\" holds '"
                        + first
                        + "', which Comet "
                        + release
                        + " does not accept in a residue token; its residue alphabet is A-Z, n"
                        + " (N-terminus), c (C-terminus)";
        ValueSyntaxException reading =
                assertThrows(
                        ValueSyntaxException.class,
                        () -> codec.parse(declaration.name(), declaration.value()),
                        form.what());
        assertEquals(slot + ", field 2 (residues): " + because, reading.getMessage());
        IllegalArgumentException writing =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> codec.format(form.expected()),
                        form.what());
        assertEquals(because + ", so it cannot be written", writing.getMessage());
        REFUSED.computeIfAbsent(release, r -> new AtomicInteger()).incrementAndGet();
    }

    @AfterAll
    static void reportTheCount() {
        // Visible in the surefire output: the number of release x slot x form cases completed.
        System.out.println(
                "VariableModRoundTripTest: round trips completed "
                        + RAN
                        + " ("
                        + TupleForms.ALL.size()
                        + " forms, plus "
                        + TupleForms.PROTEIN_TERMINI.size()
                        + " with ^ or $ where accepted, x 15 slots); ^/$ refusals completed "
                        + REFUSED);
    }
}
