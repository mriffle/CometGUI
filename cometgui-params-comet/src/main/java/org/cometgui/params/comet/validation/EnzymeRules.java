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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.model.ParameterEntry;
import org.cometgui.params.comet.model.ParameterValue;
import org.cometgui.params.comet.schema.ParameterCategory;
import org.cometgui.params.comet.value.EnzymeDefinition;
import org.cometgui.params.comet.value.EnzymeTable;

/**
 * Validator {@code enzyme_in_table}, and the rows of the {@code [COMET_ENZYME_INFO]} table.
 *
 * <p><strong>References.</strong> Every enzyme number ({@code search_enzyme_number}, {@code
 * search_enzyme2_number}, {@code sample_enzyme_number}) names a row of the model's table -- the
 * same rule the canonical writer enforces by refusing to write, reported here as a finding at the
 * parameter <em>before</em> anything is written. Comet 2026.02.2 does not catch an undefined
 * number: its "is missing definition" checks compare the enzyme name with {@code "-"} ({@code
 * Comet.cpp} lines 698-719 at {@code v2026.02.2}), but the names they test are those of the {@code
 * EnzymeInfo} declared at line 646, whose constructor sets them to {@code ""} and {@code
 * "Cut_everywhere"} ({@code CometSearch/CometData.h} lines 345-365), so the checks cannot fire.
 *
 * <p><strong>Rows.</strong> Comet reads a row whose number is referenced with {@code sscanf} and
 * {@code "%lf %47s %d %19s %19s"} ({@code Comet.cpp} lines 654-688), so a name longer than {@value
 * #NAME_LIMIT} bytes, or residues longer than {@value #RESIDUE_LIMIT}, is cut short and the fields
 * after it misread: an error for a referenced row. Comet reads no other row, so for a row nothing
 * references it is a warning. Comet's page asks for rows numbered from 0, increasing by 1 ("This
 * number list must start from 0 and sequentially increase by 1", {@code search_enzyme_number} page
 * of the 2026.02 documentation); the source does not enforce it, so a gap is a warning.
 */
final class EnzymeRules {

    /** The longest enzyme name Comet reads, {@code %47s}. */
    static final int NAME_LIMIT = 47;

    /** The longest residue text Comet reads, {@code %19s}. */
    static final int RESIDUE_LIMIT = 19;

    private EnzymeRules() {}

    static void checkReference(CometParameters model, ParameterEntry entry, Findings findings) {
        int number = ((ParameterValue.Whole) entry.value()).value();
        EnzymeTable table = model.enzymeTable();
        if (!table.contains(number)) {
            findings.add(
                    Rule.ENZYME_NOT_IN_TABLE,
                    entry.name(),
                    entry.name()
                            + " = "
                            + number
                            + " names enzyme "
                            + number
                            + ", which is not in the [COMET_ENZYME_INFO] table (its numbers are "
                            + table.rows().stream().map(EnzymeDefinition::number).toList()
                            + "); choose one of its enzymes, or add a row numbered "
                            + number);
        }
    }

    static void checkTable(CometParameters model, Findings findings) {
        Map<Integer, List<String>> referencedBy = new LinkedHashMap<>();
        for (String name : model.metadata().enzymeTable().referencedBy()) {
            model.entry(name)
                    .ifPresent(
                            entry ->
                                    referencedBy
                                            .computeIfAbsent(
                                                    ((ParameterValue.Whole) entry.value()).value(),
                                                    number -> new ArrayList<>())
                                            .add(name));
        }
        List<EnzymeDefinition> rows = model.enzymeTable().rows();
        for (EnzymeDefinition row : rows) {
            List<String> problems = new ArrayList<>();
            tooLong(problems, "name", row.name(), NAME_LIMIT);
            tooLong(problems, "cut residues", row.cutResidues(), RESIDUE_LIMIT);
            tooLong(problems, "no-cut residues", row.noCutResidues(), RESIDUE_LIMIT);
            if (problems.isEmpty()) {
                continue;
            }
            String what =
                    "enzyme row "
                            + row.number()
                            + " ("
                            + row.name()
                            + "): "
                            + String.join("; ", problems)
                            + ", so Comet would misread the row";
            List<String> referencing = referencedBy.get(row.number());
            if (referencing == null) {
                findings.addToCategory(
                        Rule.ENZYME_ROW_UNREADABLE_UNUSED,
                        ParameterCategory.DIGESTION_ENZYMES,
                        what + " if it were selected; shorten it before using it");
            } else {
                findings.add(
                        Rule.ENZYME_ROW_UNREADABLE,
                        referencing,
                        what
                                + ", and it is selected by "
                                + String.join(" and ", referencing)
                                + "; shorten it");
            }
        }
        for (int index = 0; index < rows.size(); index++) {
            if (rows.get(index).number() != index) {
                findings.addToCategory(
                        Rule.ENZYME_NUMBERING,
                        ParameterCategory.DIGESTION_ENZYMES,
                        "the enzyme rows are numbered "
                                + rows.stream().map(EnzymeDefinition::number).toList()
                                + "; Comet's documentation asks for 0, 1, 2 ... in order, so"
                                + " renumber them");
                return;
            }
        }
    }

    private static void tooLong(List<String> problems, String field, String text, int limit) {
        int bytes = text.getBytes(StandardCharsets.UTF_8).length;
        if (bytes > limit) {
            problems.add(
                    "its "
                            + field
                            + " is "
                            + bytes
                            + " characters long, and Comet reads at most "
                            + limit);
        }
    }
}
