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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.cometgui.provenance.json.JsonValue.JsonArray;
import org.cometgui.provenance.json.JsonValue.JsonBoolean;
import org.cometgui.provenance.json.JsonValue.JsonNull;
import org.cometgui.provenance.json.JsonValue.JsonNumber;
import org.cometgui.provenance.json.JsonValue.JsonObject;
import org.cometgui.provenance.json.JsonValue.JsonString;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * The strict parser, proved against hand-typed documents and hand-typed expected values.
 *
 * <p><strong>No test here uses {@link JsonWriter}.</strong> Every document below was typed out, and
 * every expected value beside it was typed out, so nothing in this file can pass because a writer
 * and a reader made the same mistake. The round trip between the two is proved elsewhere and proves
 * something weaker; this is the file that fails when the reader is wrong.
 *
 * <p>The rejection tests assert the whole message, location included. That is deliberate: a parse
 * error whose position is wrong is nearly as bad as no error at all -- it sends whoever is
 * repairing a corrupt provenance record to the wrong line -- and asserting only the exception type
 * would let the position rot silently.
 */
class JsonReaderTest {

    // ---------------------------------------------------------------------------------------
    // The seeded secret corpus, hand-transcribed from SeededSecretCorpusTest character for
    // character, so that a leak into a parse message fails here as well as in the sweep.
    // ---------------------------------------------------------------------------------------

    /** The thirteen strings a parse message must never contain. */
    private static final List<String> CORPUS =
            List.of(
                    "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
                    "ghp_S3cr3tT0k3nExampleValue0123456789ab",
                    "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxIn0"
                            + ".dBjftJeZ4CVPmB92K27uhbUJU1p1r_wW1gFWFOEjXk",
                    "hunter2-not-a-real-password",
                    "Tr0ub4dor-26-3",
                    "ll_live_9f8e7d6c5b4a39281706",
                    "correct-horse-battery-staple",
                    "swordfish-42",
                    "tok_live_abcdef0123456789",
                    "AKIAIOSFODNN7EXAMPLE",
                    "MIIBVAIBADANBgkqhkiG9w0BAQEFAASCAT4wggE6AgEAAkEAnotArealKeyExample",
                    "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ+/",
                    "ThisIsNotARealPrivateKeyItIsAFixtureForCometGUIPhase04Tests012==");

    /**
     * Parses a document that must be rejected, and returns the rejection.
     *
     * @param document the malformed document
     * @return the exception the reader threw
     */
    private static JsonParseException rejected(String document) {
        return assertThrows(JsonParseException.class, () -> JsonReader.parse(document));
    }

    /**
     * A {@code null} of whatever type the call site needs, by a route no analyser folds away.
     *
     * @param <T> the type the call site needs
     * @return {@code null}
     */
    private static <T> T deliberateNull() {
        return Optional.<T>empty().orElse(null);
    }

    @Nested
    @DisplayName("A well-formed document")
    class WellFormed {

        @Test
        @DisplayName("comes back as exactly this object graph, member by member")
        void comesBackAsExactlyThisObjectGraph() {
            String document =
                    """
                    {
                      "name": "comet",
                      "version": "2026.02.2",
                      "releaseTag": null,
                      "managed": true,
                      "sizeBytes": 1234567890123,
                      "exitCode": -1,
                      "capabilities": [
                        "mzml",
                        "mzxml"
                      ],
                      "execution": {
                        "argv": [
                          "/opt/comet",
                          "-P"
                        ],
                        "environment": {},
                        "warnings": []
                      }
                    }
                    """;

            Map<String, JsonValue> execution = new LinkedHashMap<>();
            execution.put(
                    "argv",
                    new JsonArray(List.of(new JsonString("/opt/comet"), new JsonString("-P"))));
            execution.put("environment", new JsonObject(Map.of()));
            execution.put("warnings", new JsonArray(List.of()));
            Map<String, JsonValue> expected = new LinkedHashMap<>();
            expected.put("name", new JsonString("comet"));
            expected.put("version", new JsonString("2026.02.2"));
            expected.put("releaseTag", new JsonNull());
            expected.put("managed", new JsonBoolean(true));
            expected.put("sizeBytes", new JsonNumber(1234567890123L));
            expected.put("exitCode", new JsonNumber(-1));
            expected.put(
                    "capabilities",
                    new JsonArray(List.of(new JsonString("mzml"), new JsonString("mzxml"))));
            expected.put("execution", new JsonObject(execution));

            assertEquals(new JsonObject(expected), JsonReader.parse(document));
        }

        @Test
        @DisplayName("may be a bare string, number, boolean or null")
        void mayBeABareScalar() {
            assertAll(
                    () -> assertEquals(new JsonString("comet"), JsonReader.parse("\"comet\"")),
                    () -> assertEquals(new JsonNumber(42), JsonReader.parse("42")),
                    () -> assertEquals(new JsonBoolean(true), JsonReader.parse("true")),
                    () -> assertEquals(new JsonBoolean(false), JsonReader.parse("false")),
                    () -> assertEquals(new JsonNull(), JsonReader.parse("null")));
        }

        @Test
        @DisplayName("may be an empty object or an empty array, with or without space inside")
        void mayBeAnEmptyContainer() {
            assertAll(
                    () -> assertEquals(new JsonObject(Map.of()), JsonReader.parse("{}")),
                    () -> assertEquals(new JsonArray(List.of()), JsonReader.parse("[]")),
                    () -> assertEquals(new JsonObject(Map.of()), JsonReader.parse("{   }")),
                    () -> assertEquals(new JsonArray(List.of()), JsonReader.parse("[\n\n]")));
        }

        @Test
        @DisplayName("may be surrounded and separated by all four JSON whitespace characters")
        void toleratesEveryJsonWhitespaceCharacter() {
            String document =
                    " \t\r\n{ \t\r\n\"a\" \t\r\n: \t\r\n1 \t\r\n, \t\r\n\"b\""
                            + " \t\r\n: \t\r\n[ \t\r\n2 \t\r\n] \t\r\n} \t\r\n";

            Map<String, JsonValue> expected = new LinkedHashMap<>();
            expected.put("a", new JsonNumber(1));
            expected.put("b", new JsonArray(List.of(new JsonNumber(2))));

            assertEquals(new JsonObject(expected), JsonReader.parse(document));
        }

        @Test
        @DisplayName("nests objects and arrays inside each other to any legal depth")
        void nestsContainersInsideEachOther() {
            JsonValue innermost = new JsonObject(Map.of("b", new JsonArray(List.of())));
            JsonValue middle = new JsonObject(Map.of("a", new JsonArray(List.of(innermost))));

            assertEquals(
                    new JsonArray(List.of(middle)), JsonReader.parse("[{\"a\":[{\"b\":[]}]}]"));
        }

        @Test
        @DisplayName(
                "keeps an array's order, because an argument array reordered is a different"
                        + " command")
        void keepsAnArraysOrder() {
            assertEquals(
                    new JsonArray(
                            List.of(
                                    new JsonString("/opt/comet"),
                                    new JsonString("-P"),
                                    new JsonString("comet.params"))),
                    JsonReader.parse("[\"/opt/comet\", \"-P\", \"comet.params\"]"));
        }
    }

    @Nested
    @DisplayName("A string")
    class Strings {

        @Test
        @DisplayName("comes back with every short escape decoded")
        void decodesEveryShortEscape() {
            assertAll(
                    () -> assertEquals(new JsonString("\""), JsonReader.parse("\"\\\"\"")),
                    () -> assertEquals(new JsonString("\\"), JsonReader.parse("\"\\\\\"")),
                    () -> assertEquals(new JsonString("/"), JsonReader.parse("\"\\/\"")),
                    () -> assertEquals(new JsonString("\b"), JsonReader.parse("\"\\b\"")),
                    () -> assertEquals(new JsonString("\f"), JsonReader.parse("\"\\f\"")),
                    () -> assertEquals(new JsonString("\n"), JsonReader.parse("\"\\n\"")),
                    () -> assertEquals(new JsonString("\r"), JsonReader.parse("\"\\r\"")),
                    () -> assertEquals(new JsonString("\t"), JsonReader.parse("\"\\t\"")));
        }

        @Test
        @DisplayName(
                "may be empty, and may contain a space, which is the first unescaped character")
        void mayBeEmptyOrContainASpace() {
            assertAll(
                    () -> assertEquals(new JsonString(""), JsonReader.parse("\"\"")),
                    () -> assertEquals(new JsonString(" "), JsonReader.parse("\" \"")),
                    () ->
                            assertEquals(
                                    new JsonString("HeLa 1 ug rep1"),
                                    JsonReader.parse("\"HeLa 1 ug rep1\"")));
        }

        @Test
        @DisplayName("decodes a \\u escape written with any hexadecimal digit, upper or lower case")
        void decodesEveryHexadecimalDigit() {
            assertAll(
                    () -> assertEquals(new JsonString("A"), JsonReader.parse("\"\\u0041\"")),
                    () -> assertEquals(new JsonString("é"), JsonReader.parse("\"\\u00e9\"")),
                    () -> assertEquals(new JsonString("é"), JsonReader.parse("\"\\u00E9\"")),
                    () ->
                            assertEquals(
                                    new JsonString("\u0123\u4567\u89ab\ucdef"),
                                    JsonReader.parse("\"\\u0123\\u4567\\u89ab\\ucdef\"")),
                    () ->
                            assertEquals(
                                    new JsonString("\uABCD\uEF01"),
                                    JsonReader.parse("\"\\uABCD\\uEF01\"")),
                    () -> assertEquals(new JsonString("\u0000"), JsonReader.parse("\"\\u0000\"")),
                    () -> assertEquals(new JsonString("\uffff"), JsonReader.parse("\"\\uffff\"")));
        }

        @Test
        @DisplayName("keeps a surrogate pair whole, whether escaped or written as itself")
        void keepsASurrogatePairWhole() {
            JsonValue escaped = JsonReader.parse("\"\\ud83e\\uddec\"");
            JsonValue literal = JsonReader.parse("\"🧬\"");

            assertAll(
                    () -> assertEquals(new JsonString("🧬"), escaped),
                    () -> assertEquals(new JsonString("🧬"), literal),
                    () -> assertEquals(escaped, literal),
                    () ->
                            assertEquals(
                                    1,
                                    ((JsonString) escaped)
                                            .value()
                                            .codePointCount(
                                                    0, ((JsonString) escaped).value().length())));
        }

        @Test
        @DisplayName("carries non-ASCII text through as itself")
        void carriesNonAsciiThrough() {
            assertEquals(
                    new JsonString("/data/protéomique/HeLa_1µg_rep1.mzML"),
                    JsonReader.parse("\"/data/protéomique/HeLa_1µg_rep1.mzML\""));
        }
    }

    @Nested
    @DisplayName("A number")
    class Numbers {

        @Test
        @DisplayName("comes back exactly, to the extremes of a signed 64-bit integer")
        void comesBackExactly() {
            assertAll(
                    () -> assertEquals(new JsonNumber(0), JsonReader.parse("0")),
                    () -> assertEquals(new JsonNumber(0), JsonReader.parse("-0")),
                    () -> assertEquals(new JsonNumber(9), JsonReader.parse("9")),
                    () -> assertEquals(new JsonNumber(10), JsonReader.parse("10")),
                    () -> assertEquals(new JsonNumber(-1), JsonReader.parse("-1")),
                    () ->
                            assertEquals(
                                    new JsonNumber(1234567890123L),
                                    JsonReader.parse("1234567890123")),
                    () ->
                            assertEquals(
                                    new JsonNumber(Long.MAX_VALUE),
                                    JsonReader.parse("9223372036854775807")),
                    () ->
                            assertEquals(
                                    new JsonNumber(Long.MIN_VALUE),
                                    JsonReader.parse("-9223372036854775808")));
        }

        @Test
        @DisplayName("is refused when it has a leading zero")
        void isRefusedWithALeadingZero() {
            assertAll(
                    () ->
                            assertEquals(
                                    "a number must not have a leading zero (line 1, column 2)",
                                    rejected("01").getMessage()),
                    () ->
                            assertEquals(
                                    "a number must not have a leading zero (line 1, column 3)",
                                    rejected("-01").getMessage()),
                    () ->
                            assertEquals(
                                    "a number must not have a leading zero (line 1, column 9)",
                                    rejected("[1, 2, 00]").getMessage()));
        }

        @Test
        @DisplayName("is refused when it is fractional or has an exponent")
        void isRefusedWhenNotWhole() {
            assertAll(
                    () ->
                            assertEquals(
                                    "this format writes whole numbers, so a fractional part is not"
                                            + " accepted (line 1, column 2)",
                                    rejected("1.5").getMessage()),
                    () ->
                            assertEquals(
                                    "this format writes whole numbers, so a fractional part is not"
                                            + " accepted (line 1, column 2)",
                                    rejected("1.").getMessage()),
                    () ->
                            assertEquals(
                                    "this format writes whole numbers, so an exponent is not"
                                            + " accepted (line 1, column 2)",
                                    rejected("1e5").getMessage()),
                    () ->
                            assertEquals(
                                    "this format writes whole numbers, so an exponent is not"
                                            + " accepted (line 1, column 2)",
                                    rejected("1E5").getMessage()));
        }

        @Test
        @DisplayName("is refused when it is NaN or an infinity, by name")
        void isRefusedWhenNaNOrInfinite() {
            assertAll(
                    () ->
                            assertEquals(
                                    "NaN and Infinity are not JSON values and are not accepted"
                                            + " (line 1, column 1)",
                                    rejected("NaN").getMessage()),
                    () ->
                            assertEquals(
                                    "NaN and Infinity are not JSON values and are not accepted"
                                            + " (line 1, column 1)",
                                    rejected("Infinity").getMessage()),
                    () ->
                            assertEquals(
                                    "NaN and Infinity are not JSON values and are not accepted"
                                            + " (line 1, column 1)",
                                    rejected("-Infinity").getMessage()));
        }

        @Test
        @DisplayName("is refused when it will not fit in a signed 64-bit integer")
        void isRefusedWhenTooLarge() {
            assertAll(
                    () ->
                            assertEquals(
                                    "a number must fit in a signed 64-bit integer"
                                            + " (line 1, column 1)",
                                    rejected("9223372036854775808").getMessage()),
                    () ->
                            assertEquals(
                                    "a number must fit in a signed 64-bit integer"
                                            + " (line 1, column 1)",
                                    rejected("-9223372036854775809").getMessage()));
        }

        @Test
        @DisplayName("is refused when it is written in a form JSON does not have")
        void isRefusedInANonJsonForm() {
            assertAll(
                    () ->
                            assertEquals(
                                    "a value was expected here, and this is not the start of one"
                                            + " (line 1, column 1)",
                                    rejected("+1").getMessage()),
                    () ->
                            assertEquals(
                                    "a value was expected here, and this is not the start of one"
                                            + " (line 1, column 1)",
                                    rejected(".5").getMessage()),
                    () ->
                            assertEquals(
                                    "a value was expected here, and this is not the start of one"
                                            + " (line 1, column 1)",
                                    rejected("-").getMessage()),
                    () ->
                            assertEquals(
                                    "a JSON document must end after its one top-level value"
                                            + " (line 1, column 2)",
                                    rejected("0x1f").getMessage()));
        }
    }

    @Nested
    @DisplayName("A dialect that is not JSON")
    class Dialects {

        @Test
        @DisplayName("is refused when an object has a trailing comma")
        void refusesATrailingCommaInAnObject() {
            String document =
                    """
                    {
                      "a": 1,
                      "b": 2,
                    }
                    """;

            assertEquals(
                    "an object must not have a trailing comma (line 3, column 9)",
                    rejected(document).getMessage());
        }

        @Test
        @DisplayName("is refused when an array has a trailing comma")
        void refusesATrailingCommaInAnArray() {
            assertEquals(
                    "an array must not have a trailing comma (line 1, column 6)",
                    rejected("[1, 2,]").getMessage());
        }

        @Test
        @DisplayName("is refused when it contains a comment of either shape")
        void refusesAComment() {
            assertAll(
                    () ->
                            assertEquals(
                                    "JSON has no comments, so a document must not contain one"
                                            + " (line 2, column 3)",
                                    rejected("{\n  // the run\n  \"a\": 1\n}").getMessage()),
                    () ->
                            assertEquals(
                                    "JSON has no comments, so a document must not contain one"
                                            + " (line 1, column 2)",
                                    rejected("[/* nothing */]").getMessage()));
        }

        @Test
        @DisplayName("is refused when a string is single-quoted")
        void refusesASingleQuotedString() {
            assertAll(
                    () ->
                            assertEquals(
                                    "a JSON string is delimited by double quotes, not single ones"
                                            + " (line 1, column 7)",
                                    rejected("{\"a\": 'x'}").getMessage()),
                    () ->
                            assertEquals(
                                    "an object member name must be a double-quoted string"
                                            + " (line 1, column 2)",
                                    rejected("{'a': 1}").getMessage()));
        }

        @Test
        @DisplayName("is refused when a member name is not quoted at all")
        void refusesAnUnquotedMemberName() {
            assertEquals(
                    "an object member name must be a double-quoted string (line 1, column 2)",
                    rejected("{a: 1}").getMessage());
        }

        @Test
        @DisplayName("is refused when one object names the same member twice")
        void refusesADuplicateMemberName() {
            String document =
                    """
                    {
                      "status": "completed",
                      "start": "2026-08-31T09:14:00.000Z",
                      "status": "failed"
                    }
                    """;

            assertEquals(
                    "an object must not name the same member twice (line 4, column 3)",
                    rejected(document).getMessage());
        }

        @Test
        @DisplayName("is refused when anything follows the one top-level value")
        void refusesTrailingContent() {
            assertAll(
                    () ->
                            assertEquals(
                                    "a JSON document must end after its one top-level value"
                                            + " (line 1, column 4)",
                                    rejected("{} {}").getMessage()),
                    () ->
                            assertEquals(
                                    "a JSON document must end after its one top-level value"
                                            + " (line 1, column 3)",
                                    rejected("1 2").getMessage()),
                    () ->
                            assertEquals(
                                    "a JSON document must end after its one top-level value"
                                            + " (line 1, column 6)",
                                    rejected("true x").getMessage()),
                    () ->
                            assertEquals(
                                    "a JSON document must end after its one top-level value"
                                            + " (line 2, column 1)",
                                    rejected("{}\nrubbish").getMessage()));
        }

        @Test
        @DisplayName("is refused when it begins with a byte-order mark")
        void refusesAByteOrderMark() {
            assertEquals(
                    "a JSON document must not begin with a byte-order mark (line 1, column 1)",
                    rejected("\ufeff{}").getMessage());
        }

        @Test
        @DisplayName("is refused when it holds no value at all")
        void refusesAnEmptyDocument() {
            assertAll(
                    () ->
                            assertEquals(
                                    "a JSON document must contain one value, and this one contains"
                                            + " none (line 1, column 1)",
                                    rejected("").getMessage()),
                    () ->
                            assertEquals(
                                    "a JSON document must contain one value, and this one contains"
                                            + " none (line 3, column 1)",
                                    rejected("  \n \n").getMessage()));
        }
    }

    @Nested
    @DisplayName("A truncated or damaged document")
    class Damage {

        @Test
        @DisplayName("is refused when a container is never closed, at the bracket that opened it")
        void refusesAnUnclosedContainer() {
            assertAll(
                    () ->
                            assertEquals(
                                    "an object was opened here and never closed (line 1, column 1)",
                                    rejected("{\"a\": 1").getMessage()),
                    () ->
                            assertEquals(
                                    "an array was opened here and never closed (line 1, column 1)",
                                    rejected("[1, 2").getMessage()),
                    () ->
                            assertEquals(
                                    "an object was opened here and never closed (line 2, column 8)",
                                    rejected("{\n  \"a\": {\n    \"b\": 1\n").getMessage()));
        }

        @Test
        @DisplayName("is refused wherever a document is cut off, without ever reading past its end")
        void refusesADocumentTruncatedAtEveryPosition() {
            // One case per place the reader asks whether there is another character: after an
            // opening brace or bracket, after a member name, and after a comma.  A parser that
            // checked the character before checking for the end would read past the end at exactly
            // these points, so each of them is asserted rather than assumed.
            assertAll(
                    () ->
                            assertEquals(
                                    "an object was opened here and never closed (line 1, column 1)",
                                    rejected("{").getMessage()),
                    () ->
                            assertEquals(
                                    "a value was expected here, and the document ends"
                                            + " instead (line 1, column 2)",
                                    rejected("[").getMessage()),
                    () ->
                            assertEquals(
                                    "an object member name must be followed by a colon"
                                            + " (line 1, column 5)",
                                    rejected("{\"a\"").getMessage()),
                    () ->
                            assertEquals(
                                    "an object was opened here and never closed (line 1, column 1)",
                                    rejected("{\"a\": 1,").getMessage()),
                    () ->
                            assertEquals(
                                    "a value was expected here, and the document ends"
                                            + " instead (line 1, column 4)",
                                    rejected("[1,").getMessage()),
                    () ->
                            assertEquals(
                                    "a value was expected here, and the document ends instead"
                                            + " (line 1, column 5)",
                                    rejected("[1, ").getMessage()));
        }

        @Test
        @DisplayName("is refused when a string is never closed, at the quote that opened it")
        void refusesAnUnclosedString() {
            assertEquals(
                    "a string was opened here and never closed (line 1, column 7)",
                    rejected("{\"a\": \"unterminated").getMessage());
        }

        @Test
        @DisplayName("is refused when a member has no colon or a container has no comma")
        void refusesAMissingSeparator() {
            assertAll(
                    () ->
                            assertEquals(
                                    "an object member name must be followed by a colon"
                                            + " (line 1, column 6)",
                                    rejected("{\"a\" 1}").getMessage()),
                    () ->
                            assertEquals(
                                    "an object member must be followed by a comma or by the closing"
                                            + " brace (line 1, column 9)",
                                    rejected("{\"a\": 1 \"b\": 2}").getMessage()),
                    () ->
                            assertEquals(
                                    "an array element must be followed by a comma or by the closing"
                                            + " bracket (line 1, column 4)",
                                    rejected("[1 2]").getMessage()));
        }

        @Test
        @DisplayName("is refused when a value is missing where one is required")
        void refusesAMissingValue() {
            assertAll(
                    () ->
                            assertEquals(
                                    "a value was expected here, and this is not the start of one"
                                            + " (line 1, column 7)",
                                    rejected("{\"a\": }").getMessage()),
                    () ->
                            assertEquals(
                                    "a value was expected here, and this is not the start of one"
                                            + " (line 1, column 2)",
                                    rejected("[,1]").getMessage()),
                    () ->
                            assertEquals(
                                    "a value was expected here, and the document ends instead"
                                            + " (line 1, column 7)",
                                    rejected("{\"a\": ").getMessage()));
        }

        @Test
        @DisplayName("is refused when a bare word is misspelled")
        void refusesAMisspelledKeyword() {
            assertAll(
                    () ->
                            assertEquals(
                                    "a value was expected here, and this is not the start of one"
                                            + " (line 1, column 1)",
                                    rejected("tru").getMessage()),
                    () ->
                            assertEquals(
                                    "a value was expected here, and this is not the start of one"
                                            + " (line 1, column 1)",
                                    rejected("nul").getMessage()),
                    () ->
                            assertEquals(
                                    "a value was expected here, and this is not the start of one"
                                            + " (line 1, column 1)",
                                    rejected("False").getMessage()));
        }

        @Test
        @DisplayName("is refused when a string holds a raw control character")
        void refusesARawControlCharacter() {
            assertAll(
                    () ->
                            assertEquals(
                                    "a control character inside a string must be written as an"
                                            + " escape (line 1, column 4)",
                                    rejected("\"ab\ncd\"").getMessage()),
                    () ->
                            assertEquals(
                                    "a control character inside a string must be written as an"
                                            + " escape (line 1, column 2)",
                                    rejected("\"\u001f\"").getMessage()));
        }

        @Test
        @DisplayName("is refused when an escape is not one JSON defines")
        void refusesAnUnknownEscape() {
            assertAll(
                    () ->
                            assertEquals(
                                    "an escape must be one of \\\" \\\\ \\/ \\b \\f \\n \\r \\t or"
                                            + " \\uXXXX (line 1, column 2)",
                                    rejected("\"\\q\"").getMessage()),
                    () ->
                            assertEquals(
                                    "an escape must be one of \\\" \\\\ \\/ \\b \\f \\n \\r \\t or"
                                            + " \\uXXXX (line 1, column 4)",
                                    rejected("\"ab\\x\"").getMessage()),
                    () ->
                            assertEquals(
                                    "an escape was started here and the document ends inside it"
                                            + " (line 1, column 2)",
                                    rejected("\"\\").getMessage()));
        }

        @Test
        @DisplayName("is refused when a \\u escape is not four hexadecimal digits")
        void refusesAMalformedUnicodeEscape() {
            assertAll(
                    () ->
                            assertEquals(
                                    "a \\u escape must be followed by exactly four hexadecimal"
                                            + " digits (line 1, column 6)",
                                    rejected("\"\\u00g1\"").getMessage()),
                    () ->
                            assertEquals(
                                    "a \\u escape must be followed by exactly four hexadecimal"
                                            + " digits (line 1, column 6)",
                                    rejected("\"\\u12\"").getMessage()),
                    () ->
                            assertEquals(
                                    "a \\u escape needs four hexadecimal digits and the document"
                                            + " ends inside it (line 1, column 2)",
                                    rejected("\"\\u12").getMessage()),
                    () ->
                            assertEquals(
                                    "a \\u escape must be followed by exactly four hexadecimal"
                                            + " digits (line 1, column 4)",
                                    rejected("\"\\u" + (char) 0x0661 + "123\"").getMessage()));
        }

        @Test
        @DisplayName("is refused when a surrogate has no partner, escaped or raw")
        void refusesAnUnpairedSurrogate() {
            assertAll(
                    () ->
                            assertEquals(
                                    "a high surrogate must be followed by the low surrogate of its"
                                            + " pair (line 1, column 2)",
                                    rejected("\"\\ud83e\"").getMessage()),
                    () ->
                            assertEquals(
                                    "a high surrogate must be followed by the low surrogate of its"
                                            + " pair (line 1, column 2)",
                                    rejected("\"\\ud83eZ\"").getMessage()),
                    () ->
                            assertEquals(
                                    "a low surrogate must be preceded by the high surrogate of its"
                                            + " pair (line 1, column 2)",
                                    rejected("\"\\uddec\"").getMessage()),
                    () ->
                            assertEquals(
                                    "a high surrogate must be followed by the low surrogate of its"
                                            + " pair (line 1, column 2)",
                                    rejected("\"" + (char) 0xd83e + "\"").getMessage()),
                    () ->
                            assertEquals(
                                    "a low surrogate must be preceded by the high surrogate of its"
                                            + " pair (line 1, column 2)",
                                    rejected("\"" + (char) 0xddec + "\"").getMessage()));
        }

        @Test
        @DisplayName("is refused with a null document, saying which argument was null")
        void refusesANullDocument() {
            NullPointerException thrown =
                    assertThrows(
                            NullPointerException.class, () -> JsonReader.parse(deliberateNull()));

            assertEquals("document", thrown.getMessage());
        }
    }

    @Nested
    @DisplayName("The reported position")
    class Positions {

        @Test
        @DisplayName("counts lines from one and restarts the column at each newline")
        void countsLinesAndColumns() {
            JsonParseException thrown = rejected("{\n  \"a\": 1,\n  \"b\": 01\n}\n");

            assertAll(
                    () -> assertEquals(3, thrown.line()),
                    () -> assertEquals(9, thrown.column()),
                    () -> assertEquals(20, thrown.offset()));
        }

        @Test
        @DisplayName("counts a carriage return as an ordinary character, not as a line")
        void countsACarriageReturnAsAnOrdinaryCharacter() {
            JsonParseException thrown = rejected("[1,\r\n 01]");

            assertAll(
                    () -> assertEquals(2, thrown.line()),
                    () -> assertEquals(3, thrown.column()),
                    () -> assertEquals(7, thrown.offset()));
        }

        @Test
        @DisplayName("is the offset of the character that broke the rule, in characters not bytes")
        void isTheOffsetInCharacters() {
            JsonParseException thrown = rejected("[\"protéomique\", 01]");

            assertAll(
                    () -> assertEquals(1, thrown.line()),
                    () -> assertEquals(18, thrown.column()),
                    () -> assertEquals(17, thrown.offset()));
        }
    }

    @Nested
    @DisplayName("Nesting")
    class Nesting {

        @Test
        @DisplayName("is accepted at exactly the bound, and refused one level past it")
        void isAcceptedAtTheBoundAndRefusedPastIt() {
            String atTheBound = "[".repeat(JsonReader.MAX_DEPTH) + "]".repeat(JsonReader.MAX_DEPTH);
            String oneTooDeep =
                    "[".repeat(JsonReader.MAX_DEPTH + 1) + "]".repeat(JsonReader.MAX_DEPTH + 1);

            assertAll(
                    () -> assertEquals(64, JsonReader.MAX_DEPTH),
                    () -> JsonReader.parse(atTheBound),
                    () ->
                            assertEquals(
                                    "a JSON document must not nest more than 64 containers deep"
                                            + " (line 1, column 65)",
                                    rejected(oneTooDeep).getMessage()));
        }

        @Test
        @DisplayName("counts objects and arrays alike towards the bound")
        void countsObjectsAndArraysAlike() {
            String mixed =
                    "{\"a\":".repeat(JsonReader.MAX_DEPTH / 2)
                            + "[".repeat(JsonReader.MAX_DEPTH / 2 + 1)
                            + "]".repeat(JsonReader.MAX_DEPTH / 2 + 1)
                            + "}".repeat(JsonReader.MAX_DEPTH / 2);

            assertEquals(
                    "a JSON document must not nest more than 64 containers deep"
                            + " (line 1, column 193)",
                    rejected(mixed).getMessage());
        }

        @Test
        @DisplayName("does not accumulate across containers that are closed, only nested ones")
        void doesNotAccumulateAcrossSiblings() {
            String siblings = "[" + "{},".repeat(499) + "{}]";

            JsonValue parsed = JsonReader.parse(siblings);

            assertEquals(500, ((JsonArray) parsed).elements().size());
        }

        @Test
        @DisplayName(
                "refuses a pathologically deep document as a parse error, not a stack overflow")
        void refusesAPathologicallyDeepDocumentWithoutOverflowing() {
            String hostile = "[".repeat(100_000);

            JsonParseException thrown = rejected(hostile);

            assertEquals(
                    "a JSON document must not nest more than 64 containers deep"
                            + " (line 1, column 65)",
                    thrown.getMessage());
        }
    }

    @Nested
    @DisplayName("A parse failure")
    class Secrecy {

        @Test
        @DisplayName("never repeats a character of the document, wherever the damage is")
        void neverRepeatsTheDocument() {
            String secret = "ghp_S3cr3tT0k3nExampleValue0123456789ab";
            List<String> hostile =
                    List.of(
                            "{\"token\": \"" + secret + "\",}",
                            "{\"" + secret + "\": 1, \"" + secret + "\": 2}",
                            "{\"token\": '" + secret + "'}",
                            "{\"token\": \"" + secret + "\" \"b\": 1}",
                            "{\"token\": \"" + secret + "\\q\"}",
                            "{\"token\": \"" + secret,
                            "\"" + secret + "\" " + secret,
                            "{" + secret + ": 1}",
                            "{\"token\": " + secret + "}");

            assertAll(
                    hostile.stream()
                            .map(
                                    document ->
                                            (Executable)
                                                    () ->
                                                            assertFalse(
                                                                    rejected(document)
                                                                            .getMessage()
                                                                            .contains(secret),
                                                                    "the message repeated the"
                                                                            + " secret"))
                            .toList());
        }

        @Test
        @DisplayName("repeats no member of the seeded secret corpus, in any position")
        void repeatsNoSeededSecret() {
            assertAll(
                    CORPUS.stream()
                            .map(secret -> (Executable) () -> assertNoLeak(secret))
                            .toList());
        }

        @Test
        @DisplayName("carries no cause, so nothing downstream can print what it declined to")
        void carriesNoCause() {
            assertNull(rejected("{\"a\": 1,}").getCause());
        }

        /**
         * Puts one secret in every malformed position and asserts none of them comes back.
         *
         * @param secret the value that must not appear in a message
         */
        private void assertNoLeak(String secret) {
            List<String> hostile =
                    List.of(
                            "{\"k\": \"" + secret + "\",}",
                            "{\"" + secret + "\": 1, \"" + secret + "\": 2}",
                            "{\"k\": \"" + secret + "\\q\"}",
                            "{\"k\": \"" + secret,
                            "[\"" + secret + "\" \"x\"]");
            for (String document : hostile) {
                String message = rejected(document).getMessage();
                assertFalse(message.contains(secret), "leaked into: " + message);
            }
        }
    }
}
