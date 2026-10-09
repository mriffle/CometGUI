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

package org.cometgui.app.testing;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A minimal JSON reader written for the tests, so that a sidecar or an event-log line is read back
 * by code that shares nothing with the product's writer or reader ({@code org.cometgui.provenance.
 * json}): an object is a {@link LinkedHashMap}, an array a {@link List}, a string a {@link String},
 * a number a {@link BigDecimal}, and {@code true}, {@code false} and {@code null} themselves. It
 * accepts strict JSON only and throws {@link AssertionError} at the first thing it does not.
 */
public final class TestJson {

    private final String text;

    private int at;

    private TestJson(String text) {
        this.text = text;
    }

    /**
     * Reads one JSON document.
     *
     * @param text the document
     * @return its value
     * @throws AssertionError if it is not one strict JSON value
     */
    public static Object parse(String text) {
        TestJson reader = new TestJson(text);
        Object value = reader.value();
        reader.space();
        if (reader.at != text.length()) {
            throw reader.wrong("text after the value");
        }
        return value;
    }

    /**
     * Reads one JSON object.
     *
     * @param text the document
     * @return its members, in order
     * @throws AssertionError if it is not one JSON object
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> object(String text) {
        Object value = parse(text);
        if (!(value instanceof Map<?, ?>)) {
            throw new AssertionError("not a JSON object: " + text);
        }
        return (Map<String, Object>) value;
    }

    /**
     * A member of a member of ... an object.
     *
     * @param object the object
     * @param path the member names, outermost first
     * @return the value; {@link AssertionError} if a name is missing
     */
    @SuppressWarnings("unchecked")
    public static Object at(Map<String, Object> object, String... path) {
        Object cursor = object;
        for (String name : path) {
            if (!(cursor instanceof Map<?, ?> map) || !map.containsKey(name)) {
                throw new AssertionError("no member " + String.join(".", path) + " in " + object);
            }
            cursor = ((Map<String, Object>) map).get(name);
        }
        return cursor;
    }

    private Object value() {
        space();
        if (at >= text.length()) {
            throw wrong("the end of the text where a value belongs");
        }
        char c = text.charAt(at);
        return switch (c) {
            case '{' -> objectValue();
            case '[' -> arrayValue();
            case '"' -> string();
            case 't' -> literal("true", Boolean.TRUE);
            case 'f' -> literal("false", Boolean.FALSE);
            case 'n' -> literal("null", null);
            default -> number();
        };
    }

    private Map<String, Object> objectValue() {
        Map<String, Object> members = new LinkedHashMap<>();
        at++;
        space();
        if (peek() == '}') {
            at++;
            return members;
        }
        while (true) {
            space();
            String name = string();
            space();
            expect(':');
            if (members.put(name, value()) != null) {
                throw wrong("the member \"" + name + "\" twice");
            }
            space();
            if (peek() == ',') {
                at++;
                continue;
            }
            expect('}');
            return members;
        }
    }

    private List<Object> arrayValue() {
        List<Object> elements = new ArrayList<>();
        at++;
        space();
        if (peek() == ']') {
            at++;
            return elements;
        }
        while (true) {
            elements.add(value());
            space();
            if (peek() == ',') {
                at++;
                continue;
            }
            expect(']');
            return elements;
        }
    }

    private String string() {
        expect('"');
        StringBuilder built = new StringBuilder();
        while (true) {
            if (at >= text.length()) {
                throw wrong("an unterminated string");
            }
            char c = text.charAt(at++);
            if (c == '"') {
                return built.toString();
            }
            if (c < 0x20) {
                throw wrong("a raw control character in a string");
            }
            if (c != '\\') {
                built.append(c);
                continue;
            }
            char escaped = text.charAt(at++);
            switch (escaped) {
                case '"', '\\', '/' -> built.append(escaped);
                case 'b' -> built.append('\b');
                case 'f' -> built.append('\f');
                case 'n' -> built.append('\n');
                case 'r' -> built.append('\r');
                case 't' -> built.append('\t');
                case 'u' -> {
                    built.append((char) Integer.parseInt(text.substring(at, at + 4), 16));
                    at += 4;
                }
                default -> throw wrong("the escape \\" + escaped);
            }
        }
    }

    private BigDecimal number() {
        int start = at;
        while (at < text.length() && "+-0123456789.eE".indexOf(text.charAt(at)) >= 0) {
            at++;
        }
        String number = text.substring(start, at);
        if (!number.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")) {
            at = start;
            throw wrong("not a JSON value");
        }
        return new BigDecimal(number);
    }

    private Object literal(String word, Object value) {
        if (!text.startsWith(word, at)) {
            throw wrong("not a JSON value");
        }
        at += word.length();
        return value;
    }

    private void space() {
        while (at < text.length() && " \t\r\n".indexOf(text.charAt(at)) >= 0) {
            at++;
        }
    }

    private char peek() {
        return at < text.length() ? text.charAt(at) : '\0';
    }

    private void expect(char wanted) {
        if (peek() != wanted) {
            throw wrong("'" + wanted + "' expected");
        }
        at++;
    }

    private AssertionError wrong(String what) {
        int from = Math.max(0, at - 20);
        int to = Math.min(text.length(), at + 20);
        return new AssertionError(
                "JSON: "
                        + what
                        + " at offset "
                        + at
                        + ", near \""
                        + text.substring(from, to)
                        + "\"");
    }
}
