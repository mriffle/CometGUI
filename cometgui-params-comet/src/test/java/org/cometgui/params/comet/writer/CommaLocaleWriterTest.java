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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import org.cometgui.params.comet.fixtures.ParamsFiles;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.model.ValueOrigin;
import org.cometgui.params.comet.parser.CometParamsParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Gate item 5 / {@code AC-PAR-11} / {@code R-PARAM-11}: the canonical text of the REAL {@code -q}
 * file's model -- and of a CONSTRUCTED variant with more decimals in it -- is byte-identical under
 * comma-decimal default locales and under {@link Locale#ROOT}. Each locale is first shown really to
 * write {@code 1.5} as {@code 1,5}, so the test cannot pass by testing nothing. The default locale
 * is restored after every test.
 */
class CommaLocaleWriterTest {

    private static final List<Locale> COMMA_LOCALES =
            List.of(Locale.forLanguageTag("de-DE"), Locale.forLanguageTag("fr-FR"));

    private Locale saved;

    @BeforeEach
    void save() {
        saved = Locale.getDefault();
    }

    @AfterEach
    void restore() {
        Locale.setDefault(saved);
    }

    /** Parses and writes under whatever the default locale is now. */
    private static List<byte[]> writeEverything() {
        CometParamsParser parser = new CometParamsParser(ParamsFiles.metadata(), ParamsFiles.COMET);
        CanonicalParamsWriter writer = new CanonicalParamsWriter(ParamsFiles.build());
        CometParameters real = parser.parse(ParamsFiles.complete()).model().orElseThrow();
        CometParameters edited =
                parser.parse(
                                ParamsFiles.completeWith(
                                        "num_results",
                                        "ms1_mass_range = 400.25 1600.5 # constructed\n"))
                        .model()
                        .orElseThrow()
                        .withText("fragment_bin_tol", "1.0005", ValueOrigin.USER)
                        .withText("fragment_bin_offset", "0.4", ValueOrigin.USER)
                        .withText("peptide_mass_tolerance_lower", "-1.5", ValueOrigin.USER)
                        .withText("mass_offsets", "0.0 1.5 2.25", ValueOrigin.USER)
                        .withText("clear_mz_range", "125.5 131.75", ValueOrigin.USER)
                        .withText(
                                "variable_mod02",
                                "79.966331 STY 0 2,4 -1 0 0 97.976896,79.966331",
                                ValueOrigin.USER);
        return List.of(writer.bytes(real), writer.bytes(edited));
    }

    @Test
    @DisplayName("de-DE and fr-FR write 1.5 as 1,5, and the writer's bytes are ROOT's anyway")
    void byteIdenticalUnderCommaLocales() {
        Locale.setDefault(Locale.ROOT);
        assertEquals("1.5", String.format("%.1f", 1.5));
        List<byte[]> root = writeEverything();
        assertTrue(
                new String(root.get(1), java.nio.charset.StandardCharsets.UTF_8)
                        .contains("\nfragment_bin_tol = 1.0005 "));
        for (Locale locale : COMMA_LOCALES) {
            Locale.setDefault(locale);
            assertEquals(
                    "1,5",
                    String.format("%.1f", 1.5),
                    locale + " does not write a decimal comma, so it proves nothing");
            assertEquals("1,5", NumberFormat.getInstance().format(1.5), locale.toString());
            List<byte[]> written = writeEverything();
            for (int index = 0; index < root.size(); index++) {
                Bytes.assertSame(
                        root.get(index),
                        written.get(index),
                        "model " + index + " written under " + locale);
            }
        }
    }

    @Test
    @DisplayName("the default locale is the one saved before the test (restored by @AfterEach)")
    void restoredBeforeThisTest() {
        assertEquals(saved, Locale.getDefault());
    }
}
