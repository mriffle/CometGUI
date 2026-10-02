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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import org.cometgui.params.comet.parser.ParamsLine;

/**
 * Reads the rows of the {@code [COMET_ENZYME_INFO]} table into an {@link EnzymeTable} and writes
 * them back.
 *
 * <p><strong>The row form read.</strong> Five white-space separated fields: the number followed by
 * a full stop ({@code 10.}), the name, the sense ({@code 0} or {@code 1}), the cut residues and the
 * no-cut residues, {@code -} meaning none. Comet's own reader is {@code sscanf} and accepts any
 * white space between them, so this does too; leading and trailing white space, a trailing carriage
 * return included, is ignored.
 *
 * <p><strong>The row form written.</strong> Exactly the columns of the table {@code comet -q}
 * writes, so that Comet's default table is written back byte for byte: the number and its full stop
 * left-aligned in a column of 4 characters, the name in 23, the sense in 7, the cut residues in 12,
 * then the no-cut residues with no trailing space. A field as wide as its column or wider is
 * followed by one space instead, so a long custom name stays readable by Comet. The table header
 * line and the blank line Comet writes after the rows are the file writer's, not this codec's.
 */
public final class EnzymeTableCodec {

    private static final Pattern NUMBER = Pattern.compile("[0-9]+[.]");

    private static final int NUMBER_COLUMN = 4;

    private static final int NAME_COLUMN = 23;

    private static final int SENSE_COLUMN = 7;

    private static final int CUT_COLUMN = 12;

    private static final List<String> ROW_FIELDS =
            List.of("number", "name", "sense", "cut residues", "no-cut residues");

    private EnzymeTableCodec() {}

    /**
     * Reads the rows of a table, each named by its line in diagnostics.
     *
     * @param rows the rows as the line reader classified them
     * @return the table
     * @throws ValueSyntaxException naming the line and field of the first row that cannot be read,
     *     or the line of a row whose number an earlier row already has
     */
    public static EnzymeTable parse(List<ParamsLine.EnzymeRow> rows) {
        List<EnzymeDefinition> definitions = new ArrayList<>();
        for (ParamsLine.EnzymeRow row : rows) {
            String subject = "enzyme row at line " + row.number();
            EnzymeDefinition definition = parseRow(subject, row.text());
            for (EnzymeDefinition earlier : definitions) {
                if (earlier.number() == definition.number()) {
                    throw new ValueSyntaxException(
                            subject,
                            "number",
                            "enzyme number "
                                    + definition.number()
                                    + " is already defined, as "
                                    + earlier.name()
                                    + "; Comet would silently use the later row");
                }
            }
            definitions.add(definition);
        }
        return new EnzymeTable(definitions);
    }

    /**
     * Reads one row.
     *
     * @param text the row's text
     * @return the row
     * @throws ValueSyntaxException naming the field, if the text is not a row
     */
    public static EnzymeDefinition parseRow(String text) {
        return parseRow("enzyme row", text);
    }

    private static EnzymeDefinition parseRow(String subject, String text) {
        Objects.requireNonNull(text, "text");
        String[] fields = Numbers.tokens(text);
        if (fields.length != ROW_FIELDS.size()) {
            throw new ValueSyntaxException(
                    subject,
                    "the row",
                    "\""
                            + text.strip()
                            + "\" holds "
                            + fields.length
                            + " fields; a row is "
                            + String.join(", ", ROW_FIELDS));
        }
        if (!NUMBER.matcher(fields[0]).matches()) {
            throw new ValueSyntaxException(
                    subject,
                    ROW_FIELDS.get(0),
                    "\"" + fields[0] + "\" is not a number followed by a full stop");
        }
        String digits = fields[0].substring(0, fields[0].length() - 1);
        int number = Numbers.whole(subject, ROW_FIELDS.get(0), digits);
        int senseCode = Numbers.whole(subject, ROW_FIELDS.get(2), fields[2]);
        EnzymeDefinition.Sense sense =
                EnzymeDefinition.Sense.fromCode(senseCode)
                        .orElseThrow(
                                () ->
                                        new ValueSyntaxException(
                                                subject,
                                                ROW_FIELDS.get(2),
                                                "\""
                                                        + fields[2]
                                                        + "\" is not 0 (cleave before) or 1"
                                                        + " (cleave after)"));
        return new EnzymeDefinition(
                number, fields[1], sense, residues(fields[3]), residues(fields[4]));
    }

    private static String residues(String field) {
        return EnzymeDefinition.NONE.equals(field) ? "" : field;
    }

    /**
     * Writes a table's rows.
     *
     * @param table the table
     * @return one line per row, in table order, without line terminators
     */
    public static List<String> format(EnzymeTable table) {
        return table.rows().stream().map(EnzymeTableCodec::formatRow).toList();
    }

    /**
     * Writes one row in the column form described above.
     *
     * @param row the row
     * @return the line, without a line terminator
     */
    public static String formatRow(EnzymeDefinition row) {
        return column(row.number() + ".", NUMBER_COLUMN)
                + column(row.name(), NAME_COLUMN)
                + column(Integer.toString(row.sense().code()), SENSE_COLUMN)
                + column(written(row.cutResidues()), CUT_COLUMN)
                + written(row.noCutResidues());
    }

    private static String written(String residues) {
        return residues.isEmpty() ? EnzymeDefinition.NONE : residues;
    }

    private static String column(String text, int width) {
        return text + " ".repeat(Math.max(1, width - text.length()));
    }
}
