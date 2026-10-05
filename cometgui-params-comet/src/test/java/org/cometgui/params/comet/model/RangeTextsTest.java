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

package org.cometgui.params.comet.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.List;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.parser.ReleaseDefaults;
import org.cometgui.params.comet.schema.MetadataLoader;
import org.cometgui.params.comet.value.DecimalRange;
import org.cometgui.params.comet.value.IntegerRange;
import org.cometgui.params.comet.value.ValueSyntaxException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** A two-value range's two texts, and its value from two texts, on the real starting sets. */
class RangeTextsTest {

    private static CometParameters defaults(String release) {
        return ReleaseDefaults.load(MetadataLoader.loadBundled(), ToolVersion.parse(release));
    }

    @ParameterizedTest(name = "Comet {0}")
    @ValueSource(strings = {"2026.03.0", "2026.02.2"})
    @DisplayName("the five ranges' texts, as comet -q writes them")
    void texts(String release) {
        CometParameters model = defaults(release);
        assertEquals(List.of("5", "50"), model.rangeTexts("peptide_length_range"));
        assertEquals(List.of("600.0", "5000.0"), model.rangeTexts("digest_mass_range"));
        assertEquals(List.of("0", "0"), model.rangeTexts("scan_range"));
        assertEquals(List.of("0", "0"), model.rangeTexts("precursor_charge"));
        assertEquals(List.of("0.0", "0.0"), model.rangeTexts("clear_mz_range"));
    }

    @Test
    @DisplayName("a value from two texts, read as the parameter's kind")
    void values() {
        CometParameters model = defaults("2026.03.0");
        assertEquals(
                new ParameterValue.WholeRange(new IntegerRange(7, 30)),
                model.rangeValue("peptide_length_range", "7", "30"));
        assertEquals(
                new ParameterValue.DecimalPair(
                        new DecimalRange(new BigDecimal("125.5"), new BigDecimal("131.5"))),
                model.rangeValue("clear_mz_range", "125.5", "131.5"));
        CometParameters changed =
                model.withValue(
                        "clear_mz_range",
                        model.rangeValue("clear_mz_range", "125.5", "131.5"),
                        ValueOrigin.USER);
        assertEquals("125.5 131.5", changed.text("clear_mz_range"));
        assertEquals(
                "peptide_length_range, first value: \"7.5\" is not a whole number",
                assertThrows(
                                ValueSyntaxException.class,
                                () -> model.rangeValue("peptide_length_range", "7.5", "30"))
                        .getMessage());
    }

    @Test
    @DisplayName("a parameter that is not a range is refused both ways")
    void notARange() {
        CometParameters model = defaults("2026.02.2");
        assertEquals(
                "fragment_bin_tol is not a two-value range",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> model.rangeTexts("fragment_bin_tol"))
                        .getMessage());
        assertEquals(
                "allowed_missed_cleavage is not a two-value range",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> model.rangeValue("allowed_missed_cleavage", "1", "2"))
                        .getMessage());
        assertEquals(
                "nonesuch is not a parameter modelled for Comet 2026.02.2",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> model.rangeValue("nonesuch", "1", "2"))
                        .getMessage());
    }
}
