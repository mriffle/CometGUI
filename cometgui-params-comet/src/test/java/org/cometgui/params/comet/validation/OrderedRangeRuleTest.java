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

package org.cometgui.params.comet.validation;

import static org.cometgui.params.comet.validation.Models.assertAttached;
import static org.cometgui.params.comet.validation.Models.only;
import static org.cometgui.params.comet.validation.Models.validate;
import static org.cometgui.params.comet.validation.Models.with;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.schema.CuratedMetadata;
import org.cometgui.params.comet.schema.ParameterCategory;
import org.cometgui.params.comet.schema.ParameterDefinition;
import org.cometgui.params.comet.schema.ValidatorId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The generic ordering rule over the other two-value ranges, with Comet's own meanings of zero for
 * {@code scan_range} and {@code precursor_charge}. Values are CONSTRUCTED edits of the real {@code
 * -q} model.
 */
class OrderedRangeRuleTest {

    @Test
    @DisplayName("the metadata gives the rule to exactly the five two-value ranges")
    void whichParameters() {
        Set<String> ordered =
                Models.METADATA.parameters().stream()
                        .filter(p -> p.validators().contains(ValidatorId.ORDERED_RANGE))
                        .map(ParameterDefinition::name)
                        .collect(Collectors.toSet());
        assertEquals(
                Set.of(
                        "peptide_length_range",
                        "digest_mass_range",
                        "scan_range",
                        "precursor_charge",
                        "clear_mz_range"),
                ordered);
    }

    @Test
    @DisplayName("a reversed range is an error at that parameter and its category")
    void reversed() {
        Object[][] cases = {
            {"peptide_length_range", "50 5", ParameterCategory.SEARCH_RANGES},
            {"digest_mass_range", "5000.0 600.0", ParameterCategory.SEARCH_RANGES},
            {"clear_mz_range", "200.0 100.0", ParameterCategory.SPECTRAL_PROCESSING},
            {"scan_range", "500 100", ParameterCategory.SPECTRUM_FILTERS},
            {"precursor_charge", "4 2", ParameterCategory.SPECTRUM_FILTERS},
        };
        for (Object[] c : cases) {
            String name = (String) c[0];
            Finding finding = only(validate(with(name, (String) c[1])));
            assertAttached(finding, Rule.RANGE_REVERSED, (ParameterCategory) c[2], name);
            assertEquals(
                    name
                            + " = "
                            + c[1]
                            + ": the first value is greater than the second, and Comet would"
                            + " silently ignore the range; give the smaller value first",
                    finding.message());
        }
    }

    @Test
    @DisplayName("equal and ordered values are clean")
    void ordered() {
        assertEquals(List.of(), validate(with("peptide_length_range", "7 7")).findings());
        assertEquals(List.of(), validate(with("peptide_length_range", "6 7")).findings());
        assertEquals(List.of(), validate(with("digest_mass_range", "600.0 600.00")).findings());
        assertEquals(List.of(), validate(with("clear_mz_range", "100.0 200.0")).findings());
    }

    @Test
    @DisplayName("scan_range: 0 as either end leaves it open, so 500 0 and 0 1000 are legal")
    void scanRangeZero() {
        assertEquals(List.of(), validate(with("scan_range", "500 0")).findings());
        assertEquals(List.of(), validate(with("scan_range", "0 1000")).findings());
        assertEquals(List.of(), validate(with("scan_range", "0 0")).findings());
        assertEquals(List.of(), validate(with("scan_range", "100 500")).findings());
        assertAttached(
                only(validate(with("scan_range", "2 1"))),
                Rule.RANGE_REVERSED,
                ParameterCategory.SPECTRUM_FILTERS,
                "scan_range");
    }

    @Test
    @DisplayName("precursor_charge: 0 first switches it off; a second value is then a warning")
    void precursorChargeZero() {
        assertEquals(List.of(), validate(with("precursor_charge", "0 0")).findings());
        assertEquals(List.of(), validate(with("precursor_charge", "2 4")).findings());
        Finding finding = only(validate(with("precursor_charge", "0 4")));
        assertAttached(
                finding,
                Rule.RANGE_SECOND_IGNORED,
                ParameterCategory.SPECTRUM_FILTERS,
                "precursor_charge");
        assertEquals(Severity.WARNING, finding.severity());
        assertTrue(finding.message().startsWith("precursor_charge = 0 4: "), finding.message());
        assertAttached(
                only(validate(with("precursor_charge", "1 0"))),
                Rule.RANGE_REVERSED,
                ParameterCategory.SPECTRUM_FILTERS,
                "precursor_charge");
    }

    @Test
    @DisplayName("the zero meanings are those two parameters' only")
    void zeroIsNotSpecialElsewhere() {
        assertAttached(
                only(validate(with("clear_mz_range", "10.0 0.0"))),
                Rule.RANGE_REVERSED,
                ParameterCategory.SPECTRAL_PROCESSING,
                "clear_mz_range");
        assertAttached(
                only(validate(with("digest_mass_range", "600.0 0.0"))),
                Rule.RANGE_REVERSED,
                ParameterCategory.SEARCH_RANGES,
                "digest_mass_range");
    }

    @Test
    @DisplayName("CONSTRUCTED metadata naming the rule on a non-range parameter is refused")
    void notARange() {
        CuratedMetadata bad =
                Models.redefine(
                        "allowed_missed_cleavage",
                        d -> Models.withValidators(d, List.of(ValidatorId.ORDERED_RANGE)));
        CometParameters model =
                CometParameters.defaults(bad, Models.COMET, Models.real().enzymeTable());
        IllegalStateException refused =
                assertThrows(
                        IllegalStateException.class,
                        () -> CometValidator.standard().validate(model));
        assertTrue(refused.getMessage().startsWith("allowed_missed_cleavage names the"));
    }
}
