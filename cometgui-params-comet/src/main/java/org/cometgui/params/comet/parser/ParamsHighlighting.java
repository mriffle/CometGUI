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

package org.cometgui.params.comet.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.schema.CometVersionMarker;

/**
 * What the Expert editor colours, line by line: each line's kind and the spans of its parts, as
 * {@link ParamsLineReader} classifies it (Phase 07, unit 5).
 *
 * <p>The editor may not read {@code comet.params} lines itself (decision P7-1; an architecture rule
 * keeps {@code org.cometgui.ui} away from the line reader), so this is the reader's classification
 * made available to it: one {@link Line} per line of the text, none dropped, in order, with the
 * reader's own kind and, for a declaration, where its name, value and inline comment are. Nothing
 * here classifies a line a second way: the kind is the reader's variant, and a declaration's spans
 * are where the reader's own name, value and comment texts lie in the line.
 *
 * <p>Spans are offsets into {@link Line#text()} -- the line as read, without its {@code \n} -- and
 * never cover a trailing {@code \r}.
 */
public final class ParamsHighlighting {

    /** What a whole line is: one value per {@link ParamsLine} variant. */
    public enum LineKind {

        /** The {@code # comet_version} line. */
        VERSION_MARKER,

        /** A whole-line comment. */
        COMMENT,

        /** A line of white space only. */
        BLANK,

        /** {@code name = value # comment}. */
        DECLARATION,

        /** The {@code [COMET_ENZYME_INFO]} line. */
        ENZYME_HEADER,

        /** A row of the enzyme table. */
        ENZYME_ROW,

        /** A line that is none of these; {@link Line#problem()} says why. */
        MALFORMED
    }

    /** What one span of a line is. */
    public enum TokenKind {

        /** The whole version marker line. */
        VERSION_MARKER,

        /** A whole-line comment. */
        COMMENT,

        /** A declaration's parameter name. */
        NAME,

        /** A declaration's value; absent from an empty value. */
        VALUE,

        /** A declaration's inline comment, from its {@code #} to the end of the line. */
        INLINE_COMMENT,

        /** The whole {@code [COMET_ENZYME_INFO]} line. */
        ENZYME_HEADER,

        /** A whole enzyme row. */
        ENZYME_ROW,

        /** A whole malformed line. */
        MALFORMED
    }

    /**
     * One span of a line.
     *
     * @param kind what it is
     * @param start the offset of its first character in the line
     * @param end the offset just after its last character
     */
    public record Token(TokenKind kind, int start, int end) {

        /**
         * Validates the span.
         *
         * @throws IllegalArgumentException if it is empty or starts before the line
         */
        public Token {
            Objects.requireNonNull(kind, "kind");
            if (start < 0 || end <= start) {
                throw new IllegalArgumentException(
                        "a " + kind + " span runs from " + start + " to " + end);
            }
        }
    }

    /**
     * One line, classified.
     *
     * @param number the 1-based line number
     * @param text the line as read, without its {@code \n}
     * @param kind the line's kind
     * @param tokens its spans, in line order; empty for a blank line
     * @param problem why a malformed line is malformed, in the reader's words; empty otherwise
     */
    public record Line(
            int number, String text, LineKind kind, List<Token> tokens, Optional<String> problem) {

        /**
         * Validates the components and takes an immutable copy of the spans.
         *
         * @throws IllegalArgumentException if a span runs past the line, or the problem's presence
         *     does not match the kind
         */
        public Line {
            Objects.requireNonNull(text, "text");
            Objects.requireNonNull(kind, "kind");
            tokens = List.copyOf(tokens);
            Objects.requireNonNull(problem, "problem");
            for (Token token : tokens) {
                if (token.end() > text.length()) {
                    throw new IllegalArgumentException(
                            "line " + number + ": a " + token.kind() + " span runs past the line");
                }
            }
            if (problem.isPresent() != (kind == LineKind.MALFORMED)) {
                throw new IllegalArgumentException(
                        "line " + number + ": only a malformed line has a problem, and it has one");
            }
        }

        /**
         * The spans, immutable.
         *
         * @return the spans in line order
         */
        @Override
        public List<Token> tokens() {
            return List.copyOf(tokens);
        }

        /**
         * The text of one span.
         *
         * @param token one of this line's spans
         * @return the characters it covers
         */
        public String textOf(Token token) {
            return text.substring(token.start(), token.end());
        }
    }

    private ParamsHighlighting() {}

    /**
     * Classifies every line of a text for display.
     *
     * @param text the whole text, as the editor holds it
     * @return one line per line of the text, in order
     */
    public static List<Line> of(String text) {
        List<Line> lines = new ArrayList<>();
        for (ParamsLine line : ParamsLineReader.read(text).lines()) {
            lines.add(classify(line));
        }
        return List.copyOf(lines);
    }

    /**
     * The Comet release a text's own {@code # comet_version} line names, read with {@link
     * CometVersionMarker} -- what an import consults to decide whether to offer a migration.
     *
     * @param text the whole file
     * @return the release of its first marker line, or empty when it has none or the line names no
     *     readable version
     */
    public static Optional<ToolVersion> declaredRelease(String text) {
        for (ParamsLine line : ParamsLineReader.read(text).lines()) {
            if (line instanceof ParamsLine.VersionMarker marker) {
                try {
                    return Optional.of(
                            CometVersionMarker.parseLine(marker.text().strip()).toolVersion());
                } catch (IllegalArgumentException unreadable) {
                    return Optional.empty();
                }
            }
        }
        return Optional.empty();
    }

    private static Line classify(ParamsLine line) {
        String raw = line.text();
        int content = raw.endsWith("\r") ? raw.length() - 1 : raw.length();
        return switch (line) {
            case ParamsLine.VersionMarker marker ->
                    whole(line, LineKind.VERSION_MARKER, TokenKind.VERSION_MARKER, content);
            case ParamsLine.Comment comment ->
                    whole(line, LineKind.COMMENT, TokenKind.COMMENT, content);
            case ParamsLine.Blank blank ->
                    new Line(line.number(), raw, LineKind.BLANK, List.of(), Optional.empty());
            case ParamsLine.EnzymeHeader header ->
                    whole(line, LineKind.ENZYME_HEADER, TokenKind.ENZYME_HEADER, content);
            case ParamsLine.EnzymeRow row ->
                    whole(line, LineKind.ENZYME_ROW, TokenKind.ENZYME_ROW, content);
            case ParamsLine.Malformed malformed ->
                    new Line(
                            line.number(),
                            raw,
                            LineKind.MALFORMED,
                            List.of(new Token(TokenKind.MALFORMED, 0, content)),
                            Optional.of(malformed.reason()));
            case ParamsLine.Declaration declaration -> declaration(declaration, content);
        };
    }

    private static Line whole(ParamsLine line, LineKind kind, TokenKind token, int content) {
        return new Line(
                line.number(),
                line.text(),
                kind,
                List.of(new Token(token, 0, content)),
                Optional.empty());
    }

    /**
     * Where the reader's name, value and comment texts lie: the name is the first thing on the
     * line, the value the first non-blank text after the line's first {@code =}, and the comment
     * starts at the line's first {@code #} -- the reader cuts a declaration at its first {@code =}
     * and refuses one with a {@code #} before it, and a value holds no {@code #}.
     */
    private static Line declaration(ParamsLine.Declaration declaration, int content) {
        String raw = declaration.text();
        List<Token> tokens = new ArrayList<>();
        String name = declaration.name();
        int nameStart = raw.indexOf(name);
        tokens.add(new Token(TokenKind.NAME, nameStart, nameStart + name.length()));
        int equals = raw.indexOf('=');
        String value = declaration.value();
        if (!value.isEmpty()) {
            int valueStart = raw.indexOf(value, equals + 1);
            tokens.add(new Token(TokenKind.VALUE, valueStart, valueStart + value.length()));
        }
        if (declaration.inlineComment().isPresent()) {
            int hash = raw.indexOf('#');
            tokens.add(new Token(TokenKind.INLINE_COMMENT, hash, content));
        }
        return new Line(declaration.number(), raw, LineKind.DECLARATION, tokens, Optional.empty());
    }
}
