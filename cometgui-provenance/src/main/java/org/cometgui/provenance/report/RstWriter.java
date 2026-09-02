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

package org.cometgui.provenance.report;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import org.cometgui.domain.secrets.SecretRedactor;

/**
 * Builds one reStructuredText document out of headings, field lists and list-tables, with every
 * value redacted and escaped on the way through.
 *
 * <p>This is to {@code provenance.rst} what {@link org.cometgui.provenance.json.JsonWriter} is to
 * {@code provenance.json}, and it exists for the same reason: <strong>the redactor is a constructor
 * argument and there is no overload without one</strong>, so that a field added to the report next
 * year cannot open a leak path. Every string that carries run data reaches the document through
 * {@link #value(String)}, and {@link #value(String)} calls {@link
 * SecretRedactor#redactText(String)}. A design in which each call site remembers to redact is a
 * design in which one call site eventually does not.
 *
 * <p><strong>Prose is not redacted, and prose is not run data.</strong> Headings, field names,
 * paragraphs and column titles are chosen in this repository, not supplied by a run, exactly as a
 * JSON member name is; a report whose section was called {@code [REDACTED]} would be unreadable and
 * no safer. They are validated instead -- see {@link #requirePrintableAscii(String, String)}.
 *
 * <h2>The document has to survive {@code sphinx-build -n -W}</h2>
 *
 * <p>The project builds its documentation with warnings as errors, so a generated document that
 * merely "looks like" reStructuredText is a build failure waiting for the first run whose file name
 * has a star in it. Two consequences are built into this class.
 *
 * <ul>
 *   <li><b>An underline is exactly as long as its title, always,</b> because it is generated from
 *       the title rather than typed beside it. A one-character-short underline is {@code WARNING:
 *       Title underline too short}, which under {@code -W} fails the build. Headings are therefore
 *       restricted to printable ASCII: {@link String#length()} counts UTF-16 units, so a heading
 *       containing an emoji would be given an underline two characters too long, and one containing
 *       a combining accent an underline one character too long for what a reader sees.
 *   <li><b>A table's rows all have the column count its header declares,</b> because a {@code
 *       list-table} whose rows disagree is a docutils error rather than a warning.
 * </ul>
 *
 * <h2>Values: inline literals, and what to do when that is not possible</h2>
 *
 * <p>A value is rendered as an inline literal, {@code ``like this``}. Inside one, {@code *}, {@code
 * _}, {@code |}, {@code :} and a leading {@code ..} are all inert, which is what makes a provenance
 * record safe to build out of paths, versions and captured output.
 *
 * <p><strong>An inline literal has no escape mechanism, and four kinds of value therefore cannot go
 * in one.</strong> Each was checked against the project's own Sphinx before this rule was written,
 * because two of the four fail silently rather than loudly:
 *
 * <ol>
 *   <li><em>The empty value.</em> Four backticks in a row are not an empty literal; docutils
 *       reports {@code ERROR: Unexpected section title or transition} and drops the field. A
 *       settings value and an environment value are both permitted to be empty.
 *   <li><em>A value containing a backtick.</em> There is no way to escape one: whether it survives
 *       depends on what else is on the line, because the end-string is matched greedily. A value
 *       that is nothing but a backtick produces the same {@code ERROR} as the empty value, and one
 *       beside another literal on the same line produces {@code WARNING: Inline literal
 *       start-string without end-string} and renders as raw punctuation.
 *   <li><em>A value beginning or ending with whitespace.</em> The start-string may not be followed
 *       by whitespace, and this one is silent: {@code `` foo``} builds clean and renders as the
 *       four literal characters, backticks and all, with the value no longer marked up at all.
 *   <li><em>A value containing a control character.</em> A line feed inside a value would end the
 *       line and, with it, the field, the table row or the bullet it was part of.
 * </ol>
 *
 * <p>Such a value is rendered instead as a <strong>double-quoted string with backslash
 * escapes</strong>, still inside an inline literal: {@code ``"first line\nsecond line"``}. The
 * escapes are JSON's, plus a backtick written as its six-character Unicode escape, so that the
 * escaped form itself can never contain a backtick. The quotation marks are what tell a reader
 * which form they are looking at, and the report's own preamble says so.
 *
 * <p><strong>The residual ambiguity, stated rather than hidden.</strong> A value that genuinely
 * begins and ends with a quotation mark and contains a backslash renders the same as an escaped
 * one. That is accepted here: {@code provenance.json} is the machine-readable half of the pair and
 * carries the exact characters, and this document is the half a human reads.
 *
 * <p><strong>Determinism.</strong> Line endings are {@code \n} -- {@link System#lineSeparator()}
 * would make the document depend on the machine that wrote it -- and the two open-ended maps a
 * caller can hand over are sorted <em>here</em>, in ascending key order, rather than trusted from
 * the caller, for the reason {@link org.cometgui.provenance.json.JsonWriter#sortedObject(Map)}
 * gives: {@link Map#copyOf} promises nothing about iteration order and two identical runs must
 * still produce byte-identical documents.
 *
 * <p><strong>Not thread-safe, and single-use.</strong> One instance builds one document; {@link
 * #finish()} closes it.
 */
final class RstWriter {

    /**
     * What a field with no value at all reads. Never wrapped in a literal, so it is unmistakable.
     */
    static final String ABSENT = "(none)";

    /** Lower-case hexadecimal digits for {@code \\u00XX} escapes, so no formatter is needed. */
    private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

    /** The lowest character that may appear in a document as itself; {@code U+0020} is a space. */
    private static final char FIRST_PRINTABLE = 0x20;

    /** The highest character permitted in prose; {@code U+007E} is a tilde. */
    private static final char LAST_PRINTABLE = 0x7e;

    /** {@code U+007F}, which is a control character despite sitting above the printable range. */
    private static final char DELETE = 0x7f;

    /** The character an inline literal is delimited with, and therefore cannot contain. */
    private static final char BACKTICK = '`';

    /** How far the body of a field list item is indented. */
    private static final String BODY_INDENT = "   ";

    /** How far a directive's options and content are indented. */
    private static final String DIRECTIVE_INDENT = "   ";

    /** What introduces the first cell of a {@code list-table} row. */
    private static final String FIRST_CELL = "   * - ";

    /** What introduces every cell of a {@code list-table} row after the first. */
    private static final String NEXT_CELL = "     - ";

    /** The one rule set, applied to every value. Never null; see the class documentation. */
    private final SecretRedactor redactor;

    /** The document so far. Always empty or ending in a newline. */
    private final StringBuilder out = new StringBuilder();

    /** True when the last thing written was a field list item, so the next one is contiguous. */
    private boolean lastWasField;

    /** True once {@link #finish()} has run; nothing may be written afterwards. */
    private boolean finished;

    /**
     * Use {@link #redactingWith(SecretRedactor)}: a writer without a redactor is the leak this
     * class exists to prevent, so there is no constructor that permits one.
     *
     * @param redactor the rule set to apply to every value
     */
    private RstWriter(SecretRedactor redactor) {
        this.redactor = redactor;
    }

    /**
     * Opens a document whose every value passes through the given rule set.
     *
     * @param redactor the project's secret rule set; {@link SecretRedactor#patternsOnly()} is the
     *     weakest permitted argument, not an opt-out, because the pattern rules still run
     * @return a fresh writer with nothing written yet
     * @throws NullPointerException if {@code redactor} is {@code null}
     */
    static RstWriter redactingWith(SecretRedactor redactor) {
        return new RstWriter(Objects.requireNonNull(redactor, "redactor"));
    }

    /**
     * Writes the document title: the text between an overline and an underline of {@code =}.
     *
     * @param text the title, printable ASCII prose from this repository
     * @return this writer
     * @throws NullPointerException if {@code text} is {@code null}
     * @throws IllegalArgumentException if {@code text} is empty or is not printable ASCII
     * @throws IllegalStateException if the document is finished
     */
    RstWriter title(String text) {
        String rule = ruleFor(text, '=');
        beforeBlock();
        out.append(rule).append('\n').append(text).append('\n').append(rule).append('\n');
        return this;
    }

    /**
     * Writes a section heading, underlined with {@code =}.
     *
     * @param text the heading, printable ASCII prose from this repository
     * @return this writer
     * @throws NullPointerException if {@code text} is {@code null}
     * @throws IllegalArgumentException if {@code text} is empty or is not printable ASCII
     * @throws IllegalStateException if the document is finished
     */
    RstWriter section(String text) {
        return heading(text, '=');
    }

    /**
     * Writes a subsection heading, underlined with {@code -}.
     *
     * @param text the heading, printable ASCII prose from this repository
     * @return this writer
     * @throws NullPointerException if {@code text} is {@code null}
     * @throws IllegalArgumentException if {@code text} is empty or is not printable ASCII
     * @throws IllegalStateException if the document is finished
     */
    RstWriter subsection(String text) {
        return heading(text, '-');
    }

    /**
     * Writes a paragraph, one source line per argument.
     *
     * <p>The lines are prose from this repository and are written as given: this is where the
     * report explains its own conventions, so it is the one place inline markup is intended rather
     * than escaped away.
     *
     * @param lines the paragraph's source lines, at least one
     * @return this writer
     * @throws NullPointerException if {@code lines} or any line is {@code null}
     * @throws IllegalArgumentException if no line is given
     * @throws IllegalStateException if the document is finished
     */
    RstWriter paragraph(String... lines) {
        Objects.requireNonNull(lines, "lines");
        if (lines.length == 0) {
            throw new IllegalArgumentException("a paragraph must have at least one line");
        }
        beforeBlock();
        for (String line : lines) {
            out.append(Objects.requireNonNull(line, "line")).append('\n');
        }
        return this;
    }

    /**
     * Writes a field list item whose body is one value.
     *
     * @param name the field name, printable ASCII prose without a colon
     * @param text the value, which is redacted and escaped
     * @return this writer
     * @throws NullPointerException if either argument is {@code null}
     * @throws IllegalArgumentException if {@code name} is empty, is not printable ASCII, or
     *     contains a colon
     * @throws IllegalStateException if the document is finished
     */
    RstWriter fieldValue(String name, String text) {
        Objects.requireNonNull(text, "text");
        return fieldLine(name, value(text));
    }

    /**
     * Writes a field list item that has no value, as {@value #ABSENT}.
     *
     * <p>A present value is always inside an inline literal and this one never is, so a run whose
     * value happens to be the text {@code (none)} is still distinguishable from a run that had no
     * value at all.
     *
     * @param name the field name, printable ASCII prose without a colon
     * @return this writer
     * @throws NullPointerException if {@code name} is {@code null}
     * @throws IllegalArgumentException if {@code name} is empty, is not printable ASCII, or
     *     contains a colon
     * @throws IllegalStateException if the document is finished
     */
    RstWriter fieldAbsent(String name) {
        return fieldLine(name, ABSENT);
    }

    /**
     * Writes a field list item whose body is several values on one line, comma separated.
     *
     * <p>The collection's own iteration order is kept: an order that carries meaning is the
     * caller's to fix, by holding the values in a type that has one.
     *
     * @param name the field name, printable ASCII prose without a colon
     * @param texts the values; an empty collection writes {@value #ABSENT}
     * @return this writer
     * @throws NullPointerException if either argument is {@code null}, or if an element is
     * @throws IllegalArgumentException if {@code name} is empty, is not printable ASCII, or
     *     contains a colon
     * @throws IllegalStateException if the document is finished
     */
    RstWriter fieldValues(String name, Collection<String> texts) {
        Objects.requireNonNull(texts, "texts");
        if (texts.isEmpty()) {
            return fieldAbsent(name);
        }
        StringBuilder joined = new StringBuilder();
        for (String text : texts) {
            if (joined.length() > 0) {
                joined.append(", ");
            }
            joined.append(value(Objects.requireNonNull(text, "text")));
        }
        return fieldLine(name, joined.toString());
    }

    /**
     * Writes a field list item whose body is a bullet list, one value per bullet.
     *
     * <p>The collection's own iteration order is kept, for the reason given on {@link
     * #fieldValues(String, Collection)}: an argument array reordered is a different command.
     *
     * @param name the field name, printable ASCII prose without a colon
     * @param texts the values; an empty collection writes {@value #ABSENT}
     * @return this writer
     * @throws NullPointerException if either argument is {@code null}, or if an element is
     * @throws IllegalArgumentException if {@code name} is empty, is not printable ASCII, or
     *     contains a colon
     * @throws IllegalStateException if the document is finished
     */
    RstWriter fieldBullets(String name, Collection<String> texts) {
        Objects.requireNonNull(texts, "texts");
        if (texts.isEmpty()) {
            return fieldAbsent(name);
        }
        fieldHeader(name);
        out.append('\n');
        for (String text : texts) {
            out.append(BODY_INDENT)
                    .append("* ")
                    .append(value(Objects.requireNonNull(text, "text")))
                    .append('\n');
        }
        return this;
    }

    /**
     * Writes a field list item whose body is a bullet list of {@code name = value} pairs, in
     * <strong>ascending key order</strong>.
     *
     * <p>The key is redacted and escaped like any other value. A key is not a schema name here --
     * it is an environment variable a run happened to have -- so it is run data, and treating it as
     * prose would be the leak path this class exists to close.
     *
     * @param name the field name, printable ASCII prose without a colon
     * @param entries the pairs; an empty map writes {@value #ABSENT}
     * @return this writer
     * @throws NullPointerException if either argument is {@code null}, or if a key or value is
     * @throws IllegalArgumentException if {@code name} is empty, is not printable ASCII, or
     *     contains a colon
     * @throws IllegalStateException if the document is finished
     */
    RstWriter fieldMapping(String name, Map<String, String> entries) {
        Objects.requireNonNull(entries, "entries");
        if (entries.isEmpty()) {
            return fieldAbsent(name);
        }
        fieldHeader(name);
        out.append('\n');
        for (Map.Entry<String, String> entry : new TreeMap<>(entries).entrySet()) {
            out.append(BODY_INDENT)
                    .append("* ")
                    .append(value(entry.getKey()))
                    .append(" = ")
                    .append(value(entry.getValue()))
                    .append('\n');
        }
        return this;
    }

    /**
     * Writes a {@code list-table} directive with one header row.
     *
     * <p>{@code list-table} rather than a drawn table, deliberately: a grid or simple table's
     * column boundaries are its own text, so a value one character wider than its column silently
     * changes the table's shape, and the widest values here are 64-character digests and absolute
     * paths.
     *
     * @param columns the column titles and their relative widths, at least one
     * @param rows the body rows, at least one, each with exactly as many cells as there are
     *     columns; every cell is redacted and escaped
     * @return this writer
     * @throws NullPointerException if any argument, column, row or cell is {@code null}
     * @throws IllegalArgumentException if there are no columns, if there are no rows, if a row's
     *     cell count differs from the column count, or if a column title is empty or is not
     *     printable ASCII
     * @throws IllegalStateException if the document is finished
     */
    RstWriter listTable(List<Column> columns, List<List<String>> rows) {
        Objects.requireNonNull(columns, "columns");
        Objects.requireNonNull(rows, "rows");
        if (columns.isEmpty()) {
            throw new IllegalArgumentException("a list-table must have at least one column");
        }
        if (rows.isEmpty()) {
            // A list-table whose only row is its header is a docutils error, and an empty
            // collection is a normal state of a manifest.  The caller says so in prose instead.
            throw new IllegalArgumentException(
                    "a list-table must have at least one body row; write a paragraph instead");
        }
        beforeBlock();
        out.append(".. list-table::\n").append(DIRECTIVE_INDENT).append(":header-rows: 1\n");
        out.append(DIRECTIVE_INDENT).append(":widths:");
        for (Column column : columns) {
            out.append(' ').append(column.width());
        }
        out.append("\n\n");
        List<String> titles = new ArrayList<>(columns.size());
        for (Column column : columns) {
            titles.add(requirePrintableAscii(column.title(), "a column title"));
        }
        appendCells(titles);
        for (List<String> row : rows) {
            Objects.requireNonNull(row, "row");
            if (row.size() != columns.size()) {
                throw new IllegalArgumentException(
                        "a list-table row must have "
                                + columns.size()
                                + " cell(s) to match its header, but one had "
                                + row.size());
            }
            List<String> cells = new ArrayList<>(row.size());
            for (String cell : row) {
                cells.add(value(Objects.requireNonNull(cell, "cell")));
            }
            appendCells(cells);
        }
        return this;
    }

    /**
     * Closes the document and returns it.
     *
     * <p>Every block written ends in a newline, so the document ends in exactly one: what makes it
     * a well-formed POSIX text file, so that {@code diff} and {@code wc -l} behave and a reader can
     * pin the whole document as a literal without an invisible difference at the end.
     *
     * @return the finished document
     * @throws IllegalStateException if the document is already finished or nothing was written
     */
    String finish() {
        requireWritable();
        if (out.length() == 0) {
            throw new IllegalStateException("the document is empty");
        }
        finished = true;
        return out.toString();
    }

    /**
     * Describes the writer without disclosing a character of the document it is building.
     *
     * <p>The generated form of a class holding a {@link StringBuilder} would be the document
     * itself, and that is the document which may hold a credential right up until {@link
     * #value(String)} has cleaned it.
     *
     * @return a description safe to put in a log line or an exception message
     */
    @Override
    public String toString() {
        return "RstWriter[characters=" + out.length() + ", finished=" + finished + "]";
    }

    /**
     * One column of a {@code list-table}: what it is called and how wide it is drawn.
     *
     * @param title the column heading, printable ASCII prose from this repository
     * @param width the column's share of the table, a positive relative number
     */
    record Column(String title, int width) {

        /**
         * Validates the column.
         *
         * @throws NullPointerException if {@code title} is {@code null}
         * @throws IllegalArgumentException if {@code width} is not positive, because docutils
         *     rejects a zero-width column and a negative one is not a width at all
         */
        Column {
            Objects.requireNonNull(title, "title");
            if (width < 1) {
                throw new IllegalArgumentException(
                        "a column width must be at least 1, but was: " + width);
            }
        }
    }

    /**
     * Writes a heading and its generated underline.
     *
     * @param text the heading text
     * @param rule the character to underline it with
     * @return this writer
     */
    private RstWriter heading(String text, char rule) {
        String underline = ruleFor(text, rule);
        beforeBlock();
        out.append(text).append('\n').append(underline).append('\n');
        return this;
    }

    /**
     * Builds the overline or underline for a heading: exactly as long as the heading is.
     *
     * @param text the heading text
     * @param rule the character to repeat
     * @return the rule line
     */
    private static String ruleFor(String text, char rule) {
        return String.valueOf(rule).repeat(requirePrintableAscii(text, "a heading").length());
    }

    /**
     * Writes a whole field list item on one line.
     *
     * @param name the field name
     * @param body the already-rendered body
     * @return this writer
     */
    private RstWriter fieldLine(String name, String body) {
        fieldHeader(name);
        out.append(' ').append(body).append('\n');
        return this;
    }

    /**
     * Writes the {@code :name:} part of a field list item.
     *
     * @param name the field name
     */
    private void fieldHeader(String name) {
        String checked = requirePrintableAscii(name, "a field name");
        if (checked.indexOf(':') >= 0) {
            // The colon would close the field name early and turn the rest of it into the value.
            throw new IllegalArgumentException(
                    "a field name must not contain a colon, but was: \"" + checked + "\"");
        }
        beforeField();
        out.append(':').append(checked).append(':');
    }

    /**
     * Writes the cells of one {@code list-table} row, already rendered.
     *
     * @param rendered the cells, in column order
     */
    private void appendCells(List<String> rendered) {
        for (int index = 0; index < rendered.size(); index++) {
            out.append(index == 0 ? FIRST_CELL : NEXT_CELL)
                    .append(rendered.get(index))
                    .append('\n');
        }
    }

    /**
     * Renders one value: redacted first, then escaped, then wrapped in an inline literal.
     *
     * <p>Redaction runs before escaping and the order is load-bearing, for the same reason it is in
     * {@link org.cometgui.provenance.json.JsonWriter#value(String)}: the rules match credential
     * URLs and {@code name=value} assignments as they appear in the real text.
     *
     * @param text the raw value
     * @return the value as reStructuredText inline markup
     */
    private String value(String text) {
        String redacted = redactor.redactText(text);
        return "``" + (needsEscaping(redacted) ? escaped(redacted) : redacted) + "``";
    }

    /**
     * Decides whether a value can be carried by an inline literal at all.
     *
     * <p>The four cases are the ones listed on the class, each verified against this project's own
     * Sphinx rather than inferred from the specification.
     *
     * @param text the redacted value
     * @return {@code true} if it must be written in escaped form instead
     */
    private static boolean needsEscaping(String text) {
        if (text.isEmpty()) {
            return true;
        }
        if (Character.isWhitespace(text.charAt(0))
                || Character.isWhitespace(text.charAt(text.length() - 1))) {
            return true;
        }
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character == BACKTICK || character < FIRST_PRINTABLE || character == DELETE) {
                return true;
            }
        }
        return false;
    }

    /**
     * Renders a value that an inline literal cannot carry as a quoted, backslash-escaped string.
     *
     * <p>The quotation marks are part of the output and are what tells a reader which of the two
     * forms they are looking at. A backtick becomes its Unicode escape -- a backslash followed by
     * {@code u0060} -- so that the escaped form can never itself contain the one character that
     * would end the literal early.
     *
     * @param text the redacted value
     * @return the escaped, quoted form, which contains no backtick and no control character
     */
    private static String escaped(String text) {
        StringBuilder quoted = new StringBuilder();
        quoted.append('"');
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            switch (character) {
                case '"' -> quoted.append("\\\"");
                case '\\' -> quoted.append("\\\\");
                case '\b' -> quoted.append("\\b");
                case '\f' -> quoted.append("\\f");
                case '\n' -> quoted.append("\\n");
                case '\r' -> quoted.append("\\r");
                case '\t' -> quoted.append("\\t");
                case BACKTICK -> quoted.append("\\u0060");
                default -> appendPlainOrHex(quoted, character);
            }
        }
        return quoted.append('"').toString();
    }

    /**
     * Appends one character that has no short escape: as itself, or as {@code \\u00XX} if it is a
     * control character.
     *
     * @param quoted the escaped form being built
     * @param character the character to append
     */
    private static void appendPlainOrHex(StringBuilder quoted, char character) {
        if (character >= FIRST_PRINTABLE && character != DELETE) {
            quoted.append(character);
            return;
        }
        quoted.append("\\u00")
                .append(HEX_DIGITS[(character >> 4) & 0xf])
                .append(HEX_DIGITS[character & 0xf]);
    }

    /**
     * Rejects prose that would break the document, naming what was rejected and why.
     *
     * <p>Non-ASCII is rejected rather than accepted because of the underline: {@link
     * String#length()} counts UTF-16 units, so a title with an emoji in it would be underlined two
     * characters too long and a title with a combining accent one character too long for what a
     * reader sees. Every heading, field name and column title in this project's reports is written
     * in this repository, so the restriction costs nothing and removes a whole class of defect.
     *
     * @param text the prose to check
     * @param what what the prose is, for the message
     * @return {@code text}
     */
    private static String requirePrintableAscii(String text, String what) {
        Objects.requireNonNull(text, what);
        if (text.isEmpty()) {
            throw new IllegalArgumentException(what + " must not be empty");
        }
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character < FIRST_PRINTABLE || character > LAST_PRINTABLE) {
                throw new IllegalArgumentException(
                        what
                                + " must be printable ASCII, so that an underline is exactly as"
                                + " long as what it underlines, but was: \""
                                + text
                                + "\"");
            }
        }
        return text;
    }

    /** Separates a block from whatever preceded it with one blank line. */
    private void beforeBlock() {
        requireWritable();
        if (out.length() > 0) {
            out.append('\n');
        }
        lastWasField = false;
    }

    /**
     * Separates a field list item from whatever preceded it, except from another field list item: a
     * blank line between two items would end the field list and start a second one.
     */
    private void beforeField() {
        requireWritable();
        if (out.length() > 0 && !lastWasField) {
            out.append('\n');
        }
        lastWasField = true;
    }

    /** Rejects any write after the document has been finished. */
    private void requireWritable() {
        if (finished) {
            throw new IllegalStateException("the document is finished and cannot be added to");
        }
    }
}
