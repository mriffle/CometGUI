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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.cometgui.domain.tools.ToolVersion;
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
 * Gate item 3: every tuple form round-trips in every slot.
 *
 * <p>For each of the {@link TupleForms#ALL} forms (CONSTRUCTED from upstream documentation, see
 * there) and each tuple slot the bundled metadata gives Comet 2026.02.2, a one-line {@code
 * comet.params} text {@code variable_modNN = <form>} is classified by the project's line reader,
 * its declaration's value is parsed by the codec built from the version's layout, the typed value
 * is checked field by field against the form's expected value, and formatting it gives back the
 * form's text exactly. One dynamic test per form and slot, so surefire's count is the product.
 */
class VariableModRoundTripTest {

    private static final CuratedMetadata METADATA = MetadataLoader.loadBundled();

    private static final ToolVersion COMET = ToolVersion.parse("2026.02.2");

    private static final VariableModCodec CODEC = VariableModCodec.forVersion(METADATA, COMET);

    private static final AtomicInteger RAN = new AtomicInteger();

    @Test
    @DisplayName("Comet 2026.02.2 has fifteen slots, variable_mod01 to variable_mod15")
    void fifteenSlots() {
        List<String> expected =
                IntStream.rangeClosed(1, 15)
                        .mapToObj(n -> "variable_mod" + (n < 10 ? "0" : "") + n)
                        .toList();
        assertEquals(expected, CODEC.slots());
    }

    @TestFactory
    @DisplayName("every form, in every slot")
    List<DynamicTest> everyFormInEverySlot() {
        List<DynamicTest> tests = new ArrayList<>();
        for (String slot : CODEC.slots()) {
            for (TupleForms.Form form : TupleForms.ALL) {
                tests.add(DynamicTest.dynamicTest(slot + ": " + form, () -> roundTrip(slot, form)));
            }
        }
        assertEquals(15 * TupleForms.ALL.size(), tests.size());
        return tests;
    }

    private static void roundTrip(String slot, TupleForms.Form form) {
        String line = slot + " = " + form.text() + "\n";
        List<ParamsLine.Declaration> declarations = ParamsLineReader.read(line).declarations();
        assertEquals(1, declarations.size(), line);
        ParamsLine.Declaration declaration = declarations.get(0);
        assertEquals(slot, declaration.name());
        VariableModification value = CODEC.parse(declaration.name(), declaration.value());
        assertEquals(form.expected(), value, form.what());
        assertEquals(form.text(), CODEC.format(value), form.what());
        assertEquals(value, CODEC.parse(slot, CODEC.format(value)), form.what());
        RAN.incrementAndGet();
    }

    @AfterAll
    static void reportTheCount() {
        // Visible in the surefire output: the number of form x slot round trips that completed.
        System.out.println(
                "VariableModRoundTripTest: "
                        + RAN.get()
                        + " round trips completed ("
                        + TupleForms.ALL.size()
                        + " forms x "
                        + CODEC.slots().size()
                        + " slots)");
    }
}
