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

package org.cometgui.app.config;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A JSON reader written for the provenance gate tests and sharing no code with the product's
 * ({@code org.cometgui.provenance.json.JsonReader}) or its manifest reader: a reader and a writer
 * that agree on a wrong member name, or on a wrong escape, would agree with each other and be
 * caught only by a third party. RFC 8259, strict: one value, objects become {@link Map}s keeping
 * member order and refusing a repeated name, arrays {@link List}s, strings {@link String}s, numbers
 * {@link BigDecimal}s, {@code true}/{@code false} {@link Boolean}s and {@code null} {@link #NULL}.
 */
final class IndependentJson {

    /** What a JSON {@code null} reads as, since a map cannot hold Java's. */
    static final Object NULL =
            new Object() {
                @Override
                public String toString() {
                    return "null";
                }
            };

    private final String text;

    private int at;

    private IndependentJson(String text) {
        this.text = text;
    }

    /**
     * Parses one JSON document.
     *
     * @param text the document
     * @return its value
     * @throws IllegalArgumentException naming the offset of the first thing that is not JSON
     */
    static Object parse(String text) {
        IndependentJson reader = new IndependentJson(text);
        reader.whitespace();
        Object value = reader.value();
        reader.whitespace();
        if (reader.at != text.length()) {
            throw reader.error("text after the document");
        }
        return value;
    }

    /** A member of an object, which must be there. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> object(Object value) {
        if (!(value instanceof Map)) {
            throw new IllegalArgumentException("not an object: " + value);
        }
        return (Map<String, Object>) value;
    }

    /** An array. */
    @SuppressWarnings("unchecked")
    static List<Object> array(Object value) {
        if (!(value instanceof List)) {
            throw new IllegalArgumentException("not an array: " + value);
        }
        return (List<Object>) value;
    }

    /** A string member of an object. */
    static String string(Map<String, Object> object, String name) {
        Object value = object.get(name);
        if (!(value instanceof String)) {
            throw new IllegalArgumentException(
                    "member \"" + name + "\" is not a string but " + value + " in " + object);
        }
        return (String) value;
    }

    /** A member that is a string or null. */
    static String stringOrNull(Map<String, Object> object, String name) {
        if (!object.containsKey(name)) {
            throw new IllegalArgumentException("no member \"" + name + "\" in " + object);
        }
        Object value = object.get(name);
        return value == NULL ? null : string(object, name);
    }

    /** A whole-number member of an object. */
    static long number(Map<String, Object> object, String name) {
        Object value = object.get(name);
        if (!(value instanceof BigDecimal)) {
            throw new IllegalArgumentException(
                    "member \"" + name + "\" is not a number but " + value);
        }
        return ((BigDecimal) value).longValueExact();
    }

    /** A boolean member of an object. */
    static boolean bool(Map<String, Object> object, String name) {
        Object value = object.get(name);
        if (!(value instanceof Boolean)) {
            throw new IllegalArgumentException(
                    "member \"" + name + "\" is not a boolean but " + value);
        }
        return (Boolean) value;
    }

    /** An array member of strings. */
    static List<String> strings(Map<String, Object> object, String name) {
        List<String> strings = new ArrayList<>();
        for (Object element : array(object.get(name))) {
            if (!(element instanceof String)) {
                throw new IllegalArgumentException(name + " holds a non-string: " + element);
            }
            strings.add((String) element);
        }
        return strings;
    }

    private IllegalArgumentException error(String what) {
        return new IllegalArgumentException(what + " at offset " + at);
    }

    private void whitespace() {
        while (at < text.length()) {
            char c = text.charAt(at);
            if (c != ' ' && c != '\t' && c != '\n' && c != '\r') {
                return;
            }
            at++;
        }
    }

    private char peek() {
        if (at >= text.length()) {
            throw error("the document ends");
        }
        return text.charAt(at);
    }

    private void expect(String literal) {
        if (!text.startsWith(literal, at)) {
            throw error("expected " + literal);
        }
        at += literal.length();
    }

    private Object value() {
        char c = peek();
        switch (c) {
            case '{':
                return objectValue();
            case '[':
                return arrayValue();
            case '"':
                return stringValue();
            case 't':
                expect("true");
                return Boolean.TRUE;
            case 'f':
                expect("false");
                return Boolean.FALSE;
            case 'n':
                expect("null");
                return NULL;
            default:
                return numberValue();
        }
    }

    private Map<String, Object> objectValue() {
        expect("{");
        Map<String, Object> members = new LinkedHashMap<>();
        whitespace();
        if (peek() == '}') {
            at++;
            return Collections.unmodifiableMap(members);
        }
        while (true) {
            whitespace();
            String name = stringValue();
            whitespace();
            expect(":");
            whitespace();
            if (members.containsKey(name)) {
                throw error("the member name \"" + name + "\" is repeated");
            }
            members.put(name, value());
            whitespace();
            if (peek() == ',') {
                at++;
                continue;
            }
            expect("}");
            return Collections.unmodifiableMap(members);
        }
    }

    private List<Object> arrayValue() {
        expect("[");
        List<Object> elements = new ArrayList<>();
        whitespace();
        if (peek() == ']') {
            at++;
            return Collections.unmodifiableList(elements);
        }
        while (true) {
            whitespace();
            elements.add(value());
            whitespace();
            if (peek() == ',') {
                at++;
                continue;
            }
            expect("]");
            return Collections.unmodifiableList(elements);
        }
    }

    private String stringValue() {
        expect("\"");
        StringBuilder out = new StringBuilder();
        while (true) {
            char c = peek();
            at++;
            if (c == '"') {
                return out.toString();
            }
            if (c < 0x20) {
                throw error("an unescaped control character in a string");
            }
            if (c != '\\') {
                out.append(c);
                continue;
            }
            char escape = peek();
            at++;
            switch (escape) {
                case '"' -> out.append('"');
                case '\\' -> out.append('\\');
                case '/' -> out.append('/');
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                case 'u' -> {
                    if (at + 4 > text.length()) {
                        throw error("a short \\u escape");
                    }
                    out.append((char) Integer.parseInt(text.substring(at, at + 4), 16));
                    at += 4;
                }
                default -> throw error("an unknown escape \\" + escape);
            }
        }
    }

    private BigDecimal numberValue() {
        int start = at;
        while (at < text.length() && "+-0123456789.eE".indexOf(text.charAt(at)) >= 0) {
            at++;
        }
        if (start == at) {
            throw error("expected a value");
        }
        return new BigDecimal(text.substring(start, at));
    }
}
