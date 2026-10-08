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

package org.cometgui.results.testing;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A deliberately small JSON reader for test code: enough to read the large fixture's {@code
 * manifest.json}, and no more. {@code cometgui-results} depends on {@code cometgui-domain} alone,
 * and a test helper is no reason to add a dependency.
 *
 * <p>Objects become an unmodifiable {@link Map} in document order, arrays an unmodifiable {@link
 * List}, strings {@link String}, numbers {@link BigDecimal} (exact, so a count is never rounded),
 * {@code true}/{@code false} {@link Boolean}, and {@code null} {@link #NULL}. Malformed input
 * throws {@link IllegalArgumentException} naming the offset.
 */
public final class MiniJson {

    /** JSON {@code null}; a {@link Map} or {@link List} cannot hold Java's. */
    public static final Object NULL = new Object();

    private final String text;
    private int at;

    private MiniJson(String text) {
        this.text = text;
    }

    /**
     * Parses one JSON document.
     *
     * @param text the document
     * @return its value
     * @throws IllegalArgumentException if the text is not one well-formed JSON value
     */
    public static Object parse(String text) {
        MiniJson reader = new MiniJson(text);
        Object value = reader.value();
        reader.space();
        if (reader.at != text.length()) {
            throw reader.error("trailing content");
        }
        return value;
    }

    private Object value() {
        space();
        if (at >= text.length()) {
            throw error("unexpected end");
        }
        char c = text.charAt(at);
        switch (c) {
            case '{':
                return object();
            case '[':
                return array();
            case '"':
                return string();
            case 't':
                return literal("true", Boolean.TRUE);
            case 'f':
                return literal("false", Boolean.FALSE);
            case 'n':
                return literal("null", NULL);
            default:
                return number();
        }
    }

    private Map<String, Object> object() {
        at++;
        Map<String, Object> members = new LinkedHashMap<>();
        space();
        if (peek() == '}') {
            at++;
            return Collections.unmodifiableMap(members);
        }
        while (true) {
            space();
            if (peek() != '"') {
                throw error("expected a member name");
            }
            String name = string();
            space();
            expect(':');
            if (members.put(name, value()) != null) {
                throw error("duplicate member " + name);
            }
            space();
            char c = next();
            if (c == '}') {
                return Collections.unmodifiableMap(members);
            }
            if (c != ',') {
                throw error("expected ',' or '}'");
            }
        }
    }

    private List<Object> array() {
        at++;
        List<Object> elements = new ArrayList<>();
        space();
        if (peek() == ']') {
            at++;
            return Collections.unmodifiableList(elements);
        }
        while (true) {
            elements.add(value());
            space();
            char c = next();
            if (c == ']') {
                return Collections.unmodifiableList(elements);
            }
            if (c != ',') {
                throw error("expected ',' or ']'");
            }
        }
    }

    private String string() {
        expect('"');
        StringBuilder out = new StringBuilder();
        while (true) {
            char c = next();
            if (c == '"') {
                return out.toString();
            }
            if (c < 0x20) {
                throw error("control character in a string");
            }
            if (c != '\\') {
                out.append(c);
                continue;
            }
            char escape = next();
            switch (escape) {
                case '"', '\\', '/' -> out.append(escape);
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                case 'u' -> {
                    if (at + 4 > text.length()) {
                        throw error("short \\u escape");
                    }
                    out.append((char) Integer.parseInt(text.substring(at, at + 4), 16));
                    at += 4;
                }
                default -> throw error("bad escape \\" + escape);
            }
        }
    }

    private BigDecimal number() {
        int start = at;
        while (at < text.length() && "+-0123456789.eE".indexOf(text.charAt(at)) >= 0) {
            at++;
        }
        String token = text.substring(start, at);
        if (!token.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")) {
            at = start;
            throw error("not a JSON value");
        }
        return new BigDecimal(token);
    }

    private Object literal(String word, Object value) {
        if (!text.startsWith(word, at)) {
            throw error("expected " + word);
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
        if (at >= text.length()) {
            throw error("unexpected end");
        }
        return text.charAt(at);
    }

    private char next() {
        char c = peek();
        at++;
        return c;
    }

    private void expect(char wanted) {
        if (next() != wanted) {
            at--;
            throw error("expected '" + wanted + "'");
        }
    }

    private IllegalArgumentException error(String what) {
        return new IllegalArgumentException("malformed JSON at offset " + at + ": " + what);
    }
}
