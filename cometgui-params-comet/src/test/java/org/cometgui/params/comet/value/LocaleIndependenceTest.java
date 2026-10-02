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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.fixtures.CometFixtures;
import org.cometgui.params.comet.parser.ParamsLineReader;
import org.cometgui.params.comet.schema.MetadataLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code R-PARAM-11}: every codec reads and writes the same text under a comma-decimal default
 * locale as under {@link Locale#ROOT}. The default locale is restored after every test.
 */
class LocaleIndependenceTest {

    private Locale saved;

    @BeforeEach
    void save() {
        saved = Locale.getDefault();
    }

    @AfterEach
    void restore() {
        Locale.setDefault(saved);
    }

    /** Everything every codec writes, for one set of inputs. */
    private static List<String> everythingWritten() throws IOException {
        VariableModCodec codec =
                VariableModCodec.forVersion(
                        MetadataLoader.loadBundled(), ToolVersion.parse("2026.02.2"));
        List<String> out = new ArrayList<>();
        for (TupleForms.Form form : TupleForms.ALL) {
            VariableModification value = codec.parse("variable_mod07", form.text());
            out.add(codec.format(value));
            out.add(value.summary("Mod"));
        }
        String dump =
                new String(
                        CometFixtures.bytes(
                                CometFixtures.COMET_2026_02_2,
                                CometFixtures.LINUX_X86_64,
                                CometFixtures.Mode.COMPLETE),
                        StandardCharsets.UTF_8);
        out.addAll(
                EnzymeTableCodec.format(
                        EnzymeTableCodec.parse(ParamsLineReader.read(dump).enzymeRows())));
        TolerancePair pair = TolerancePair.parse("-20.0", "1234.5678");
        out.add(pair.lowerText());
        out.add(pair.upperText());
        out.add(DecimalRange.parse("digest_mass_range", "600.0 5000.0").text());
        out.add(IntegerRange.parse("peptide_length_range", "5 50").text());
        out.add(DecimalList.parse("mass_offsets", "0.0 42.0123 1500.25").text());
        return out;
    }

    @Test
    @DisplayName("German and French default locales write byte-identical text to Locale.ROOT")
    void commaDecimalLocalesWriteTheSameText() throws IOException {
        Locale.setDefault(Locale.ROOT);
        List<String> root = everythingWritten();
        assertEquals("15.9949 M 0 3 -1 0 0 0.0", root.get(0));
        for (Locale comma : List.of(Locale.GERMANY, Locale.FRANCE)) {
            Locale.setDefault(comma);
            assertEquals(
                    "1,5",
                    NumberFormat.getInstance().format(1.5),
                    "the locale under test really does write a decimal comma");
            assertEquals(root, everythingWritten(), comma.toString());
        }
    }

    @Test
    @DisplayName("a comma decimal is not read as a number, under any default locale")
    void aCommaDecimalIsNeverANumber() {
        Locale.setDefault(Locale.GERMANY);
        assertThrows(ValueSyntaxException.class, () -> TolerancePair.parse("-20,0", "20,0"));
        assertThrows(
                ValueSyntaxException.class, () -> DecimalRange.parse("clear_mz_range", "0,0 0,0"));
        assertThrows(ValueSyntaxException.class, () -> DecimalList.parse("mass_offsets", "42,01"));
        assertEquals(new BigDecimal("15.9949"), Numbers.decimal("x", "y", "15.9949"));
        assertNotEquals(Locale.ROOT, Locale.getDefault());
    }
}
