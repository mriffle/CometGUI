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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.cometgui.params.comet.model.ParameterEntry;
import org.cometgui.params.comet.model.ParameterValue;
import org.cometgui.params.comet.schema.ParameterDefinition;
import org.cometgui.params.comet.value.Numbers;

/**
 * Every parameter's curated {@code min} and {@code max}, applied to every number its value holds:
 * the one number of an integer or decimal, both numbers of a two-value range, every number of a
 * list. Applied to every parameter with a bound, whatever validators it names.
 */
final class BoundsRule {

    private BoundsRule() {}

    /** One number of a value, with the words that say which. */
    private record Member(String which, BigDecimal number) {}

    static void check(ParameterEntry entry, Findings findings) {
        ParameterDefinition definition = entry.definition();
        Optional<BigDecimal> minimum = definition.minimum().map(BigDecimal::new);
        Optional<BigDecimal> maximum = definition.maximum().map(BigDecimal::new);
        for (Member member : members(entry.value())) {
            if (minimum.isPresent() && member.number().compareTo(minimum.get()) < 0) {
                findings.add(
                        Rule.BELOW_MINIMUM,
                        entry.name(),
                        entry.name()
                                + ": "
                                + member.which()
                                + Numbers.text(member.number())
                                + " is below the minimum "
                                + definition.minimum().get()
                                + "; use "
                                + definition.minimum().get()
                                + " or more");
            }
            if (maximum.isPresent() && member.number().compareTo(maximum.get()) > 0) {
                findings.add(
                        Rule.ABOVE_MAXIMUM,
                        entry.name(),
                        entry.name()
                                + ": "
                                + member.which()
                                + Numbers.text(member.number())
                                + " is above the maximum "
                                + definition.maximum().get()
                                + "; use "
                                + definition.maximum().get()
                                + " or less");
            }
        }
    }

    private static List<Member> members(ParameterValue value) {
        return switch (value) {
            case ParameterValue.Whole whole ->
                    List.of(new Member("the value ", BigDecimal.valueOf(whole.value())));
            case ParameterValue.Decimal decimal ->
                    List.of(new Member("the value ", decimal.value()));
            case ParameterValue.WholeRange range ->
                    List.of(
                            new Member(
                                    "the first value ", BigDecimal.valueOf(range.range().first())),
                            new Member(
                                    "the second value ",
                                    BigDecimal.valueOf(range.range().second())));
            case ParameterValue.DecimalPair range ->
                    List.of(
                            new Member("the first value ", range.range().first()),
                            new Member("the second value ", range.range().second()));
            case ParameterValue.Decimals list -> {
                List<Member> members = new ArrayList<>();
                List<BigDecimal> values = list.list().values();
                for (int index = 0; index < values.size(); index++) {
                    members.add(new Member("value " + (index + 1) + ", ", values.get(index)));
                }
                yield members;
            }
            default -> List.of();
        };
    }
}
