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

package org.cometgui.provenance.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Reads back exactly the JSON {@link JsonWriter} writes, and refuses everything else.
 *
 * <p>The inverse of {@link JsonWriter}, and written for the same reason there is no JSON library on
 * the other side: {@code provenance.json} is the artefact the product exists to produce, so what
 * counts as a valid one has to be a property of this repository rather than of a dependency's
 * default leniency. A permissive parser is the danger here. It accepts a corrupted provenance
 * record -- a truncated write, a half-merged file, a hand-edited document -- and hands back an
 * object graph that looks like a run that never happened. This one accepts a small, stated grammar
 * and says no to the rest, at a stated position.
 *
 * <h2>What is rejected, and why each one matters</h2>
 *
 * <dl>
 *   <dt>Trailing commas, comments, single-quoted strings, unquoted member names.
 *   <dd>None of them is JSON. Each is a dialect some tool emits, and every one of them in a
 *       provenance file means the file was written or edited by something that is not this
 *       application.
 *   <dt>A duplicate member name in one object.
 *   <dd>The one malformation with no safe recovery: "first wins" and "last wins" are both
 *       defensible and they disagree, so a document containing {@code "status"} twice has two
 *       different meanings depending on who reads it. A provenance record that means two things is
 *       worse than one that cannot be read.
 *   <dt>{@code NaN}, {@code Infinity}, leading zeros, a fraction, an exponent, a {@code +} sign.
 *   <dd>Not JSON, or not this format. Every number in a manifest is a byte count, an exit code or a
 *       schema version, all of them whole; a size that arrived as {@code 1.23456789E12} would be a
 *       file length nobody could verify against a disk.
 *   <dt>An integer outside the range of a {@code long}.
 *   <dd>Rejected rather than widened to a {@code double}. Silently losing the low digits of a byte
 *       count is precisely the class of defect a provenance record exists to rule out.
 *   <dt>A raw control character inside a string, an unknown escape, a malformed {@code \\u} escape,
 *       an unpaired surrogate.
 *   <dd>The first is forbidden by the JSON grammar; the rest cannot be decoded to text at all. An
 *       unpaired surrogate in particular is not a character: it would travel through the model as a
 *       {@link String} and fail, much later, at the point something tried to write it out as UTF-8.
 *   <dt>Anything after the top-level value, and a byte-order mark before it.
 *   <dd>Two documents concatenated by a failed atomic write look exactly like one valid document
 *       followed by rubbish, and that is the shape this catches.
 * </dl>
 *
 * <h2>Depth is bounded, because this is a parser</h2>
 *
 * <p>A recursive-descent parser fed {@code [[[[[...} recurses once per bracket and dies with a
 * {@link StackOverflowError} -- an {@link Error}, not an exception, thrown from an unpredictable
 * frame, catchable only by code that should not be catching {@link Error}s at all. A provenance
 * document can arrive from anywhere, so malformed input is treated as hostile rather than merely
 * unexpected: nesting deeper than {@link #MAX_DEPTH} is refused as a normal parse failure, with a
 * position, before the stack is anywhere near its limit. The manifest's own deepest nesting is five
 * levels.
 *
 * <h2>Errors say where, never what</h2>
 *
 * <p>See {@link JsonParseException}: the message names the rule that was broken and the line and
 * column it was broken at, and contains no character of the document. A parse error is the one
 * moment at which the document has certainly not been through {@link
 * org.cometgui.domain.secrets.SecretRedactor}, so quoting it back is how a credential reaches a
 * log.
 *
 * <h2>Not thread-safe, and single-use</h2>
 *
 * <p>One instance reads one document, and there is no way to obtain one: {@link #parse(String)}
 * makes it, uses it and drops it, so the position state cannot be shared between two documents.
 */
public final class JsonReader {

    /**
     * The deepest nesting of objects and arrays this reader will accept.
     *
     * <p>Sixty-four, against a manifest that nests five levels. The number is a bound on the stack
     * rather than a statement about the format: it is far above anything this project writes and
     * far below the depth at which recursion becomes a {@link StackOverflowError}.
     */
    public static final int MAX_DEPTH = 64;

    /** The first character JSON allows unescaped inside a string; everything below is a control. */
    private static final char FIRST_UNESCAPED = 0x20;

    /** How many hexadecimal digits a {@code \\u} escape has. */
    private static final int UNICODE_ESCAPE_DIGITS = 4;

    /** Bits per hexadecimal digit, for assembling a {@code \\u} escape. */
    private static final int BITS_PER_HEX_DIGIT = 4;

    /** The document being read. */
    private final String document;

    /** The index of the next character to read. */
    private int index;

    /** The 1-based line the next character is on. */
    private int line = 1;

    /** The 1-based column the next character is at. */
    private int column = 1;

    /** How many objects and arrays are open around the current position. */
    private int depth;

    /**
     * Where the high surrogate of a pair was, while its low surrogate is still outstanding.
     *
     * <p>{@code null} whenever no half-pair is pending. Holding the position rather than a flag is
     * what lets the failure be reported at the escape that opened the pair rather than at the end
     * of the string.
     */
    private Position pendingHighSurrogate;

    /**
     * Use {@link #parse(String)}.
     *
     * @param document the document to read
     */
    private JsonReader(String document) {
        this.document = document;
    }

    /**
     * Reads one whole document.
     *
     * @param document the JSON text, which must contain exactly one value and nothing after it
     * @return the value the document holds
     * @throws NullPointerException if {@code document} is {@code null}
     * @throws JsonParseException if the document is not the JSON this reader accepts; the message
     *     names the rule and the position, and quotes nothing from the document
     */
    public static JsonValue parse(String document) {
        Objects.requireNonNull(document, "document");
        return new JsonReader(document).readDocument();
    }

    /** Reads the single top-level value, with nothing before it and nothing after it. */
    private JsonValue readDocument() {
        if (document.startsWith("﻿")) {
            throw error("a JSON document must not begin with a byte-order mark");
        }
        skipWhitespace();
        if (atEnd()) {
            throw error("a JSON document must contain one value, and this one contains none");
        }
        JsonValue root = readValue();
        skipWhitespace();
        if (!atEnd()) {
            throw error("a JSON document must end after its one top-level value");
        }
        return root;
    }

    /** Reads one value of any kind, starting at the current position. */
    private JsonValue readValue() {
        if (atEnd()) {
            throw error("a value was expected here, and the document ends instead");
        }
        return switch (peek()) {
            case '{' -> readObject();
            case '[' -> readArray();
            case '"' -> new JsonValue.JsonString(readString());
            case 't' -> readKeyword("true", JsonValue.JsonBoolean.TRUE);
            case 'f' -> readKeyword("false", JsonValue.JsonBoolean.FALSE);
            case 'n' -> readKeyword("null", JsonValue.JsonNull.NULL);
            case '\'' ->
                    throw error("a JSON string is delimited by double quotes, not single ones");
            default -> readNumber();
        };
    }

    /** Reads an object, from its opening brace to its closing one. */
    private JsonValue readObject() {
        Position opening = here();
        enterContainer();
        next();
        Map<String, JsonValue> members = new LinkedHashMap<>();
        skipWhitespace();
        if (!atEnd() && peek() == '}') {
            next();
            leaveContainer();
            return new JsonValue.JsonObject(members);
        }
        while (true) {
            skipWhitespace();
            if (atEnd()) {
                throw errorAt("an object was opened here and never closed", opening);
            }
            if (peek() != '"') {
                throw error("an object member name must be a double-quoted string");
            }
            Position nameAt = here();
            String name = readString();
            if (members.containsKey(name)) {
                throw errorAt("an object must not name the same member twice", nameAt);
            }
            skipWhitespace();
            if (atEnd() || peek() != ':') {
                throw error("an object member name must be followed by a colon");
            }
            next();
            skipWhitespace();
            members.put(name, readValue());
            skipWhitespace();
            if (atEnd()) {
                throw errorAt("an object was opened here and never closed", opening);
            }
            if (peek() == '}') {
                next();
                leaveContainer();
                return new JsonValue.JsonObject(members);
            }
            Position commaAt = here();
            if (peek() != ',') {
                throw error("an object member must be followed by a comma or by the closing brace");
            }
            next();
            skipWhitespace();
            if (!atEnd() && peek() == '}') {
                throw errorAt("an object must not have a trailing comma", commaAt);
            }
        }
    }

    /** Reads an array, from its opening bracket to its closing one. */
    private JsonValue readArray() {
        Position opening = here();
        enterContainer();
        next();
        List<JsonValue> elements = new ArrayList<>();
        skipWhitespace();
        if (!atEnd() && peek() == ']') {
            next();
            leaveContainer();
            return new JsonValue.JsonArray(elements);
        }
        while (true) {
            skipWhitespace();
            elements.add(readValue());
            skipWhitespace();
            if (atEnd()) {
                throw errorAt("an array was opened here and never closed", opening);
            }
            if (peek() == ']') {
                next();
                leaveContainer();
                return new JsonValue.JsonArray(elements);
            }
            Position commaAt = here();
            if (peek() != ',') {
                throw error(
                        "an array element must be followed by a comma or by the closing bracket");
            }
            next();
            skipWhitespace();
            if (!atEnd() && peek() == ']') {
                throw errorAt("an array must not have a trailing comma", commaAt);
            }
        }
    }

    /**
     * Reads one of the three bare words, which must be spelled exactly.
     *
     * @param keyword the word that must be there
     * @param value the value it stands for
     * @return {@code value}
     */
    private JsonValue readKeyword(String keyword, JsonValue value) {
        Position start = here();
        if (!document.startsWith(keyword, index)) {
            throw errorAt("a value was expected here, and this is not the start of one", start);
        }
        for (int letter = 0; letter < keyword.length(); letter++) {
            next();
        }
        return value;
    }

    /**
     * Reads a whole number, rejecting every numeric form this format does not write.
     *
     * @return the number
     */
    private JsonValue readNumber() {
        Position start = here();
        if (document.startsWith("NaN", index)
                || document.startsWith("Infinity", index)
                || document.startsWith("-Infinity", index)) {
            throw errorAt("NaN and Infinity are not JSON values and are not accepted", start);
        }
        if (peek() == '-') {
            next();
        }
        if (atEnd() || !isDigit(peek())) {
            throw errorAt("a value was expected here, and this is not the start of one", start);
        }
        if (peek() == '0') {
            next();
            if (!atEnd() && isDigit(peek())) {
                throw error("a number must not have a leading zero");
            }
        } else {
            while (!atEnd() && isDigit(peek())) {
                next();
            }
        }
        if (!atEnd() && peek() == '.') {
            throw error("this format writes whole numbers, so a fractional part is not accepted");
        }
        if (!atEnd() && (peek() == 'e' || peek() == 'E')) {
            throw error("this format writes whole numbers, so an exponent is not accepted");
        }
        try {
            return new JsonValue.JsonNumber(Long.parseLong(document, start.offset(), index, 10));
        } catch (NumberFormatException tooLarge) {
            throw errorAt("a number must fit in a signed 64-bit integer", start);
        }
    }

    /**
     * Reads a string literal and returns its decoded text.
     *
     * @return the text between the quotes, with every escape resolved
     */
    private String readString() {
        Position opening = here();
        next();
        StringBuilder text = new StringBuilder();
        pendingHighSurrogate = null;
        while (true) {
            if (atEnd()) {
                throw errorAt("a string was opened here and never closed", opening);
            }
            Position at = here();
            char character = peek();
            if (character == '"') {
                next();
                break;
            }
            if (character == '\\') {
                next();
                appendEscape(text, at);
                continue;
            }
            if (character < FIRST_UNESCAPED) {
                throw error("a control character inside a string must be written as an escape");
            }
            next();
            append(text, character, at);
        }
        if (pendingHighSurrogate != null) {
            throw errorAt(
                    "a high surrogate must be followed by the low surrogate of its pair",
                    pendingHighSurrogate);
        }
        return text.toString();
    }

    /**
     * Decodes the escape whose backslash has just been consumed.
     *
     * @param text the string being built
     * @param escape where the backslash was
     */
    private void appendEscape(StringBuilder text, Position escape) {
        if (atEnd()) {
            throw errorAt("an escape was started here and the document ends inside it", escape);
        }
        char marker = next();
        switch (marker) {
            case '"' -> append(text, '"', escape);
            case '\\' -> append(text, '\\', escape);
            case '/' -> append(text, '/', escape);
            case 'b' -> append(text, '\b', escape);
            case 'f' -> append(text, '\f', escape);
            case 'n' -> append(text, '\n', escape);
            case 'r' -> append(text, '\r', escape);
            case 't' -> append(text, '\t', escape);
            case 'u' -> append(text, readUnicodeEscape(escape), escape);
            default ->
                    throw errorAt(
                            "an escape must be one of \\\" \\\\ \\/ \\b \\f \\n \\r \\t or \\uXXXX",
                            escape);
        }
    }

    /**
     * Reads the four hexadecimal digits of a {@code \\u} escape.
     *
     * @param escape where the backslash was
     * @return the character those digits denote
     */
    private char readUnicodeEscape(Position escape) {
        int value = 0;
        for (int digit = 0; digit < UNICODE_ESCAPE_DIGITS; digit++) {
            if (atEnd()) {
                throw errorAt(
                        "a \\u escape needs four hexadecimal digits and the document ends"
                                + " inside it",
                        escape);
            }
            int nibble = hexValue(peek());
            if (nibble < 0) {
                throw error("a \\u escape must be followed by exactly four hexadecimal digits");
            }
            next();
            value = (value << BITS_PER_HEX_DIGIT) | nibble;
        }
        return (char) value;
    }

    /**
     * Appends one decoded character, keeping surrogate pairs whole.
     *
     * @param text the string being built
     * @param character the character to append
     * @param at where in the document it came from
     */
    private void append(StringBuilder text, char character, Position at) {
        if (pendingHighSurrogate != null) {
            if (!Character.isLowSurrogate(character)) {
                throw errorAt(
                        "a high surrogate must be followed by the low surrogate of its pair",
                        pendingHighSurrogate);
            }
            pendingHighSurrogate = null;
        } else if (Character.isHighSurrogate(character)) {
            pendingHighSurrogate = at;
        } else if (Character.isLowSurrogate(character)) {
            throw errorAt("a low surrogate must be preceded by the high surrogate of its pair", at);
        }
        text.append(character);
    }

    /** Skips the four characters JSON counts as whitespace, and refuses a comment. */
    private void skipWhitespace() {
        while (!atEnd()) {
            char character = peek();
            if (character == ' ' || character == '\t' || character == '\n' || character == '\r') {
                next();
                continue;
            }
            if (character == '/') {
                throw error("JSON has no comments, so a document must not contain one");
            }
            return;
        }
    }

    /** Records that a container has been entered, refusing to nest past the bound. */
    private void enterContainer() {
        depth++;
        if (depth > MAX_DEPTH) {
            throw error(
                    "a JSON document must not nest more than " + MAX_DEPTH + " containers deep");
        }
    }

    /** Records that a container has been closed. */
    private void leaveContainer() {
        depth--;
    }

    /**
     * Whether the whole document has been consumed.
     *
     * @return true if there is no character left to read
     */
    private boolean atEnd() {
        return index >= document.length();
    }

    /**
     * The next character, without consuming it.
     *
     * @return the character at the current position
     */
    private char peek() {
        return document.charAt(index);
    }

    /**
     * Consumes the next character, keeping the line and column in step.
     *
     * @return the character that was consumed
     */
    private char next() {
        char character = document.charAt(index);
        index++;
        if (character == '\n') {
            line++;
            column = 1;
        } else {
            column++;
        }
        return character;
    }

    /**
     * Where the reader is now.
     *
     * @return the current position
     */
    private Position here() {
        return new Position(line, column, index);
    }

    /**
     * A failure at the current position.
     *
     * @param rule what the document did that no accepted document does
     * @return the exception to throw
     */
    private JsonParseException error(String rule) {
        return errorAt(rule, here());
    }

    /**
     * A failure at a position the reader has already passed.
     *
     * @param rule what the document did that no accepted document does
     * @param where the position to report
     * @return the exception to throw
     */
    private static JsonParseException errorAt(String rule, Position where) {
        return new JsonParseException(rule, where.line(), where.column(), where.offset());
    }

    /**
     * Whether a character is an ASCII digit.
     *
     * @param character the character to test
     * @return true for {@code 0} to {@code 9}
     */
    private static boolean isDigit(char character) {
        return character >= '0' && character <= '9';
    }

    /**
     * The value of one hexadecimal digit.
     *
     * <p>Written out rather than delegated to {@link Character#digit(char, int)}, which also
     * accepts the digits of every other script -- Devanagari, Thai, fullwidth Latin -- so that
     * {@code \\u06f1} would be a valid escape. The JSON grammar's {@code HEXDIG} is ASCII.
     *
     * @param character the character to interpret
     * @return its value from 0 to 15, or -1 if it is not an ASCII hexadecimal digit
     */
    private static int hexValue(char character) {
        if (character >= '0' && character <= '9') {
            return character - '0';
        }
        if (character >= 'a' && character <= 'f') {
            return character - 'a' + 10;
        }
        if (character >= 'A' && character <= 'F') {
            return character - 'A' + 10;
        }
        return -1;
    }

    /**
     * One place in the document: the line and column to report, and the offset to slice from.
     *
     * @param line the 1-based line
     * @param column the 1-based column
     * @param offset the 0-based character index
     */
    private record Position(int line, int column, int offset) {}
}
