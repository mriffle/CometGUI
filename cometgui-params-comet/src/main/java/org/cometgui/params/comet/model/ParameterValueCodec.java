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

import java.util.Objects;
import java.util.Optional;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.schema.CuratedMetadata;
import org.cometgui.params.comet.schema.ParameterDefinition;
import org.cometgui.params.comet.schema.ValueKind;
import org.cometgui.params.comet.value.DecimalList;
import org.cometgui.params.comet.value.DecimalRange;
import org.cometgui.params.comet.value.IntegerRange;
import org.cometgui.params.comet.value.Numbers;
import org.cometgui.params.comet.value.ValueSyntaxException;
import org.cometgui.params.comet.value.VariableModCodec;

/**
 * Reads the value text of one declaration into a {@link ParameterValue}, and writes it back, for
 * one Comet version. The parser and the canonical writer both go through here, so a parameter's
 * text has exactly one reader and one writer.
 *
 * <p>Every kind is delegated to the structured value types where one exists, and every number goes
 * through {@link Numbers} -- {@code BigDecimal} and {@code Integer} text, never a locale-sensitive
 * formatter ({@code R-PARAM-11}). Only text that cannot be read at all is refused, with a {@link
 * ValueSyntaxException} naming the parameter: a number that is not one, a flag that is not {@code
 * 0} or {@code 1}, a tuple of the wrong shape. Whether a readable value is legal (a choice, a
 * range, an enzyme number in the table) is validation's question.
 */
public final class ParameterValueCodec {

    private static final String VALUE = "value";

    private static final String ON = "1";

    private static final String OFF = "0";

    private final VariableModCodec tuples;

    private ParameterValueCodec(VariableModCodec tuples) {
        this.tuples = tuples;
    }

    /**
     * The codec for one curated Comet version.
     *
     * @param metadata the curated metadata
     * @param version the Comet version
     * @return the codec
     * @throws IllegalArgumentException if the metadata was not curated against the version
     */
    public static ParameterValueCodec forVersion(CuratedMetadata metadata, ToolVersion version) {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(version, "version");
        return new ParameterValueCodec(VariableModCodec.forVersion(metadata, version));
    }

    /**
     * Reads a declaration's value text as its parameter's kind.
     *
     * @param definition the parameter
     * @param text the text between {@code =} and {@code #}, trimmed; may be empty
     * @return the typed value
     * @throws ValueSyntaxException naming the parameter, if the text cannot be read as its kind
     */
    public ParameterValue parse(ParameterDefinition definition, String text) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(text, "text");
        String name = definition.name();
        return switch (definition.kind()) {
            case INTEGER, INTEGER_ENUM, ENZYME_REFERENCE ->
                    new ParameterValue.Whole(Numbers.whole(name, VALUE, single(name, text)));
            case DECIMAL, TOLERANCE_PAIR_MEMBER ->
                    new ParameterValue.Decimal(Numbers.decimal(name, VALUE, single(name, text)));
            case BOOLEAN_FLAG, ION_SERIES_FLAG -> new ParameterValue.Flag(flag(name, text));
            case STRING, STRING_ENUM, FILE_PATH -> text(name, text);
            case INTEGER_RANGE -> new ParameterValue.WholeRange(IntegerRange.parse(name, text));
            case DECIMAL_RANGE -> new ParameterValue.DecimalPair(DecimalRange.parse(name, text));
            case DECIMAL_LIST -> new ParameterValue.Decimals(DecimalList.parse(name, text));
            case VARIABLE_MOD_TUPLE -> new ParameterValue.Tuple(tuples.parse(name, text));
        };
    }

    private static String single(String name, String text) {
        String[] tokens = Numbers.tokens(text);
        if (tokens.length != 1) {
            throw new ValueSyntaxException(
                    name, VALUE, "\"" + text.strip() + "\" is not one number");
        }
        return tokens[0];
    }

    private static boolean flag(String name, String text) {
        String stripped = text.strip();
        if (!ON.equals(stripped) && !OFF.equals(stripped)) {
            throw new ValueSyntaxException(name, VALUE, "\"" + stripped + "\" is not 0 or 1");
        }
        return ON.equals(stripped);
    }

    private static ParameterValue text(String name, String text) {
        Optional<String> problem = unwritableText(text);
        if (problem.isPresent()) {
            throw new ValueSyntaxException(name, VALUE, problem.get());
        }
        return new ParameterValue.Text(text);
    }

    /**
     * Writes a value as its declaration's value text.
     *
     * @param definition the parameter
     * @param value its value
     * @return the text written after {@code name = }; empty for an empty text or list
     * @throws IllegalArgumentException if the value is not of the variant the parameter's kind
     *     holds, or is a tuple the version's layout cannot write
     */
    public String format(ParameterDefinition definition, ParameterValue value) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(value, "value");
        if (!fits(definition.kind(), value)) {
            throw new IllegalArgumentException(mismatch(definition, value));
        }
        return switch (value) {
            case ParameterValue.Whole whole -> Integer.toString(whole.value());
            case ParameterValue.Decimal decimal -> Numbers.text(decimal.value());
            case ParameterValue.Flag flag -> flag.on() ? ON : OFF;
            case ParameterValue.Text text -> text.text();
            case ParameterValue.WholeRange range -> range.range().text();
            case ParameterValue.DecimalPair range -> range.range().text();
            case ParameterValue.Decimals list -> list.list().text();
            case ParameterValue.Tuple tuple -> tuples.format(tuple.modification());
        };
    }

    /**
     * Whether a value is the variant a kind holds.
     *
     * @param kind the parameter's kind
     * @param value the value
     * @return {@code true} if a parameter of that kind may hold it
     */
    public static boolean fits(ValueKind kind, ParameterValue value) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(value, "value");
        return switch (kind) {
            case INTEGER, INTEGER_ENUM, ENZYME_REFERENCE -> value instanceof ParameterValue.Whole;
            case DECIMAL, TOLERANCE_PAIR_MEMBER -> value instanceof ParameterValue.Decimal;
            case BOOLEAN_FLAG, ION_SERIES_FLAG -> value instanceof ParameterValue.Flag;
            case STRING, STRING_ENUM, FILE_PATH -> value instanceof ParameterValue.Text;
            case INTEGER_RANGE -> value instanceof ParameterValue.WholeRange;
            case DECIMAL_RANGE -> value instanceof ParameterValue.DecimalPair;
            case DECIMAL_LIST -> value instanceof ParameterValue.Decimals;
            case VARIABLE_MOD_TUPLE -> value instanceof ParameterValue.Tuple;
        };
    }

    /**
     * The message for a value of the wrong variant.
     *
     * @param definition the parameter
     * @param value the value offered
     * @return a sentence naming the parameter, its kind and the variant offered
     */
    static String mismatch(ParameterDefinition definition, ParameterValue value) {
        return definition.name()
                + " is of kind "
                + definition.kind()
                + " and cannot hold a "
                + value.getClass().getSimpleName()
                + " value";
    }

    /**
     * Why a text could not be written as a value and read back unchanged, if it could not.
     *
     * @param text the text
     * @return the problem, or empty if the text reads back as written
     */
    static Optional<String> unwritableText(String text) {
        if (text.contains("#")) {
            return Optional.of("it holds '#', where Comet ends a value and starts a comment");
        }
        if (text.contains("\n") || text.contains("\r")) {
            return Optional.of("it holds a line break");
        }
        if (!text.equals(text.strip())) {
            return Optional.of("it has white space at one end, which the reader trims");
        }
        return Optional.empty();
    }
}
