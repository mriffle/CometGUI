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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.cometgui.domain.secrets.SecretRedactor;
import org.cometgui.domain.secrets.SecretRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The byte-level contract of {@link JsonWriter}, pinned as hand-typed literals.
 *
 * <p><strong>No expected value in this file was produced by running the writer.</strong> Every one
 * of them was typed from what the format is specified to be -- two-space indentation, {@code ": "}
 * after a name, a comma at the end of the preceding line, {@code \n} everywhere, one trailing
 * newline, sorted map keys, ASCII digits, non-ASCII characters as themselves. A round trip through
 * this writer and a reader would prove only that the two agree with each other, which is not what
 * the on-disk format has to be right about.
 */
class JsonWriterTest {

    /** The three corpus secrets this file uses, hand-transcribed from the phase's seeded corpus. */
    private static final String SWORDFISH = "swordfish-42";

    /** A corpus password, used where a bare value must be cleared by the registry alone. */
    private static final String URL_PASSWORD = "Tr0ub4dor-26-3";

    /** Turkish, where a careless {@code toLowerCase()} turns {@code I} into a dotless {@code i}. */
    private static final Locale TURKISH = Locale.of("tr", "TR");

    /** German, where a careless number format writes {@code 1.234} for one thousand two hundred. */
    private static final Locale GERMAN = Locale.GERMANY;

    /** A locale whose numbering system is Thai digits, which are not ASCII. */
    private static final Locale THAI_DIGITS = Locale.forLanguageTag("th-TH-u-nu-thai");

    /**
     * A writer with the pattern rules only: the weakest redactor the class permits, never an
     * opt-out.
     *
     * @return a fresh writer
     */
    private static JsonWriter writer() {
        return JsonWriter.redactingWith(SecretRedactor.patternsOnly());
    }

    /**
     * A writer whose registry knows the corpus secrets a production run would have registered.
     *
     * @return a fresh writer
     */
    private static JsonWriter loadedWriter() {
        return JsonWriter.redactingWith(
                SecretRedactor.with(SecretRegistry.of(SWORDFISH, URL_PASSWORD)));
    }

    /**
     * A {@code null} of whatever type the call site needs, by a route no analyser folds away.
     *
     * <p>The same idiom, and the same reason, as {@code SecretRedactorTest.deliberateNull}:
     * SpotBugs at effort Max reports a null <em>literal</em> handed to a parameter that is
     * dereferenced on every path as {@code NP_NULL_PARAM_DEREF_ALL_TARGETS_DANGEROUS}, which is the
     * test's own purpose reported as a defect.
     *
     * @param <T> the type the call site needs
     * @return {@code null}
     */
    private static <T> T deliberateNull() {
        return Optional.<T>empty().orElse(null);
    }

    @Nested
    @DisplayName("The document's shape")
    class Shape {

        @Test
        @DisplayName("an empty object is one line, and the document ends with one newline")
        void anEmptyObjectIsOneLine() {
            assertEquals("{}\n", writer().beginObject().endObject().finish());
        }

        @Test
        @DisplayName("an empty array is one line")
        void anEmptyArrayIsOneLine() {
            assertEquals("[]\n", writer().beginArray().endArray().finish());
        }

        @Test
        @DisplayName("a bare string, number, boolean or null may be the whole document")
        void aScalarMayBeTheWholeDocument() {
            assertAll(
                    () -> assertEquals("\"hello\"\n", writer().value("hello").finish()),
                    () -> assertEquals("42\n", writer().value(42L).finish()),
                    () -> assertEquals("true\n", writer().value(true).finish()),
                    () -> assertEquals("false\n", writer().value(false).finish()),
                    () -> assertEquals("null\n", writer().nullValue().finish()));
        }

        @Test
        @DisplayName("members are one per line, two spaces per level, comma at the end")
        void membersAreOnePerLineTwoSpacesPerLevel() {
            String document =
                    writer().beginObject()
                            .name("first")
                            .value("one")
                            .name("second")
                            .value(2L)
                            .name("third")
                            .beginObject()
                            .name("nested")
                            .value(true)
                            .name("deeper")
                            .beginObject()
                            .name("bottom")
                            .nullValue()
                            .endObject()
                            .endObject()
                            .endObject()
                            .finish();

            assertEquals(
                    """
                    {
                      "first": "one",
                      "second": 2,
                      "third": {
                        "nested": true,
                        "deeper": {
                          "bottom": null
                        }
                      }
                    }
                    """,
                    document);
        }

        @Test
        @DisplayName("array elements are one per line and indented like members")
        void arrayElementsAreOnePerLine() {
            String document =
                    writer().beginObject()
                            .name("argv")
                            .beginArray()
                            .value("/opt/comet/comet")
                            .value("-P")
                            .value("comet.params")
                            .endArray()
                            .name("counts")
                            .beginArray()
                            .value(0L)
                            .value(1L)
                            .endArray()
                            .endObject()
                            .finish();

            assertEquals(
                    """
                    {
                      "argv": [
                        "/opt/comet/comet",
                        "-P",
                        "comet.params"
                      ],
                      "counts": [
                        0,
                        1
                      ]
                    }
                    """,
                    document);
        }

        @Test
        @DisplayName("an empty container nested inside another stays on one line")
        void anEmptyNestedContainerStaysOnOneLine() {
            String document =
                    writer().beginObject()
                            .name("settings")
                            .beginObject()
                            .endObject()
                            .name("tools")
                            .beginArray()
                            .endArray()
                            .name("after")
                            .value(1L)
                            .endObject()
                            .finish();

            assertEquals(
                    """
                    {
                      "settings": {},
                      "tools": [],
                      "after": 1
                    }
                    """,
                    document);
        }

        @Test
        @DisplayName("closing a nested container leaves the parent knowing it is not empty")
        void closingANestedContainerLeavesTheParentNotEmpty() {
            // The defect this catches is a writer that tracks "is the current container empty" in
            // one field instead of one per level: the comma after the closing bracket disappears.
            String document =
                    writer().beginArray()
                            .beginArray()
                            .endArray()
                            .beginObject()
                            .endObject()
                            .value("last")
                            .endArray()
                            .finish();

            assertEquals(
                    """
                    [
                      [],
                      {},
                      "last"
                    ]
                    """,
                    document);
        }
    }

    @Nested
    @DisplayName("Map ordering")
    class MapOrdering {

        @Test
        @DisplayName("is ascending by key, whatever order the map iterates in")
        void isAscendingByKey() {
            Map<String, String> insertionOrdered = new LinkedHashMap<>();
            insertionOrdered.put("percolator.seed", "9001");
            insertionOrdered.put("comet.num-threads", "8");
            insertionOrdered.put("limelight.project", "42");

            String document = writer().sortedObject(insertionOrdered).finish();

            assertEquals(
                    """
                    {
                      "comet.num-threads": "8",
                      "limelight.project": "42",
                      "percolator.seed": "9001"
                    }
                    """,
                    document);
        }

        @Test
        @DisplayName("is code-point order, not a locale's collation order")
        void isCodePointOrderNotCollation() {
            Locale originalDefault = Locale.getDefault();
            Locale originalFormat = Locale.getDefault(Locale.Category.FORMAT);
            try {
                // A German or Swedish Collator sorts "a", "ä", "b"; String's natural ordering
                // sorts "a", "b", "ä", because U+00E4 is above 'b'.  The format must not move
                // when the JVM's locale does, so the second is the required answer.
                Locale.setDefault(GERMAN);
                Map<String, String> keys = new LinkedHashMap<>();
                keys.put("ä", "a-umlaut");
                keys.put("b", "bee");
                keys.put("a", "ay");

                String document = writer().sortedObject(keys).finish();

                assertEquals(
                        """
                        {
                          "a": "ay",
                          "b": "bee",
                          "ä": "a-umlaut"
                        }
                        """,
                        document);
            } finally {
                Locale.setDefault(originalDefault);
                Locale.setDefault(Locale.Category.FORMAT, originalFormat);
            }
        }

        @Test
        @DisplayName("an empty map is an empty object")
        void anEmptyMapIsAnEmptyObject() {
            assertEquals("{}\n", writer().sortedObject(Map.of()).finish());
        }

        @Test
        @DisplayName("an array of strings keeps the collection's order, because argv order matters")
        void anArrayOfStringsKeepsItsOrder() {
            String document = writer().arrayOfStrings(List.of("zulu", "alpha", "mike")).finish();

            assertEquals(
                    """
                    [
                      "zulu",
                      "alpha",
                      "mike"
                    ]
                    """,
                    document);
        }

        @Test
        @DisplayName("an empty collection is an empty array")
        void anEmptyCollectionIsAnEmptyArray() {
            assertEquals("[]\n", writer().arrayOfStrings(List.of()).finish());
        }
    }

    @Nested
    @DisplayName("Escaping")
    class Escaping {

        @Test
        @DisplayName("covers the quote, the backslash and the five short control forms")
        void coversTheQuoteBackslashAndShortForms() {
            assertAll(
                    () -> assertEquals("\"a\\\"b\"\n", writer().value("a\"b").finish()),
                    () -> assertEquals("\"a\\\\b\"\n", writer().value("a\\b").finish()),
                    () -> assertEquals("\"a\\bb\"\n", writer().value("a\bb").finish()),
                    () -> assertEquals("\"a\\fb\"\n", writer().value("a\fb").finish()),
                    () -> assertEquals("\"a\\nb\"\n", writer().value("a\nb").finish()),
                    () -> assertEquals("\"a\\rb\"\n", writer().value("a\rb").finish()),
                    () -> assertEquals("\"a\\tb\"\n", writer().value("a\tb").finish()));
        }

        @Test
        @DisplayName("writes every other control character as a four-digit hex escape")
        void writesOtherControlCharactersAsHex() {
            assertAll(
                    () -> assertEquals("\"\\u0000\"\n", writer().value("\u0000").finish()),
                    () -> assertEquals("\"\\u0001\"\n", writer().value("\u0001").finish()),
                    () -> assertEquals("\"\\u000b\"\n", writer().value("\u000b").finish()),
                    () -> assertEquals("\"\\u000e\"\n", writer().value("\u000e").finish()),
                    () -> assertEquals("\"\\u001b\"\n", writer().value("\u001b").finish()),
                    () -> assertEquals("\"\\u001f\"\n", writer().value("\u001f").finish()));
        }

        @Test
        @DisplayName("escapes U+001F and leaves U+0020 alone, which is where the boundary is")
        void escapesUpToButNotIncludingSpace() {
            assertAll(
                    () -> assertEquals("\"[\\u001f]\"\n", writer().value("[\u001f]").finish()),
                    () -> assertEquals("\"[ ]\"\n", writer().value("[\u0020]").finish()));
        }

        @Test
        @DisplayName("puts both hex digits in the right order, so 0x1b is 1 then b")
        void putsBothHexDigitsInTheRightOrder() {
            // Each fixture has two DIFFERENT nibbles, so an escaper that swapped the high and the
            // low one would read \u00b1, \u0001 and \u00e0 instead.  A fixture whose nibbles were
            // equal could not tell a correct escaper from one that shifted the wrong way.
            assertAll(
                    () -> assertEquals("\"\\u001b\"\n", writer().value("\u001b").finish()),
                    () -> assertEquals("\"\\u0010\"\n", writer().value("\u0010").finish()),
                    () -> assertEquals("\"\\u000e\"\n", writer().value("\u000e").finish()));
        }

        @Test
        @DisplayName("leaves the solidus and the delete character unescaped, as JSON permits")
        void leavesSolidusAndDeleteUnescaped() {
            assertAll(
                    () -> assertEquals("\"a/b\"\n", writer().value("a/b").finish()),
                    () -> assertEquals("\"a\u007fb\"\n", writer().value("a\u007fb").finish()));
        }

        @Test
        @DisplayName("writes non-ASCII characters as themselves, including a surrogate pair")
        void writesNonAsciiAsItself() {
            assertAll(
                    () ->
                            assertEquals(
                                    "\"/data/protéomique/HeLa_1µg.mzML\"\n",
                                    writer().value("/data/protéomique/HeLa_1µg.mzML").finish()),
                    () ->
                            assertEquals(
                                    "\"/data/実験/结果.txt\"\n",
                                    writer().value("/data/実験/结果.txt").finish()),
                    () ->
                            assertEquals(
                                    "\"/data/🧬-run/x.mzML\"\n",
                                    writer().value("/data/🧬-run/x.mzML").finish()));
        }

        @Test
        @DisplayName("keeps a surrogate pair as two chars that still form one code point")
        void keepsASurrogatePairIntact() {
            String document = writer().value("🧬").finish();

            // The document is `"🧬"\n`: quote, high surrogate, low surrogate, quote, newline.
            assertAll(
                    () -> assertEquals(5, document.length()),
                    () -> assertEquals(0xd83e, document.charAt(1)),
                    () -> assertEquals(0xddec, document.charAt(2)),
                    () -> assertEquals(0x1f9ec, document.codePointAt(1)));
        }

        @Test
        @DisplayName("escapes a member name the same way, but never redacts one")
        void escapesNamesButNeverRedactsThem() {
            String document =
                    loadedWriter()
                            .beginObject()
                            .name("a\"quoted\"\tname")
                            .value("plain")
                            .name(SWORDFISH)
                            .value(SWORDFISH)
                            .endObject()
                            .finish();

            // The key "swordfish-42" is a REGISTERED SECRET and still comes out verbatim: a name
            // is schema, chosen here, never run data, and a redacted one would be unparsable.
            // The identical string as a VALUE is cleared.
            assertEquals(
                    """
                    {
                      "a\\"quoted\\"\\tname": "plain",
                      "swordfish-42": "[REDACTED]"
                    }
                    """,
                    document);
        }
    }

    @Nested
    @DisplayName("Numbers")
    class Numbers {

        @Test
        @DisplayName("render the extremes of long exactly and with no grouping")
        void renderTheExtremesOfLongExactly() {
            assertAll(
                    () ->
                            assertEquals(
                                    "-9223372036854775808\n",
                                    writer().value(Long.MIN_VALUE).finish()),
                    () ->
                            assertEquals(
                                    "9223372036854775807\n",
                                    writer().value(Long.MAX_VALUE).finish()),
                    () -> assertEquals("0\n", writer().value(0L).finish()),
                    () -> assertEquals("-1\n", writer().value(-1L).finish()),
                    () -> assertEquals("1234567890123\n", writer().value(1234567890123L).finish()));
        }

        @Test
        @DisplayName("render an int exactly, by widening rather than by a second code path")
        void renderAnIntExactly() {
            int minimum = Integer.MIN_VALUE;
            int exitCode = 127;

            assertAll(
                    () -> assertEquals("-2147483648\n", writer().value(minimum).finish()),
                    () -> assertEquals("127\n", writer().value(exitCode).finish()));
        }

        @Test
        @DisplayName("are ASCII with no separators under Turkish, German and Thai-digit locales")
        void areAsciiUnderHostileDefaultLocales() {
            Locale originalDefault = Locale.getDefault();
            Locale originalFormat = Locale.getDefault(Locale.Category.FORMAT);
            try {
                Locale.setDefault(TURKISH);
                String turkish = writer().value(1234567890123L).finish();
                Locale.setDefault(GERMAN);
                String german = writer().value(1234567890123L).finish();
                Locale.setDefault(THAI_DIGITS);
                String thai = writer().value(1234567890123L).finish();

                // A German number format writes 1.234.567.890.123 and a Thai-digit one writes the
                // same value in Thai digits; R-PROV-04 records the locale precisely because it can
                // reach serialisation, and this is one of the places it must not.
                assertAll(
                        () -> assertEquals("1234567890123\n", turkish),
                        () -> assertEquals("1234567890123\n", german),
                        () -> assertEquals("1234567890123\n", thai));
            } finally {
                Locale.setDefault(originalDefault);
                Locale.setDefault(Locale.Category.FORMAT, originalFormat);
            }
        }
    }

    @Nested
    @DisplayName("Redaction inside the writer")
    class Redaction {

        @Test
        @DisplayName("clears a credential URL that arrives as an ordinary string value")
        void clearsACredentialUrlValue() {
            String document =
                    writer().value("https://ll-user:Tr0ub4dor-26-3@ll.example.org/up").finish();

            assertEquals("\"https://ll-user:[REDACTED]@ll.example.org/up\"\n", document);
        }

        @Test
        @DisplayName("clears a bearer header, an assignment and a known token shape")
        void clearsTheOtherPatternFamilies() {
            assertAll(
                    () ->
                            assertEquals(
                                    "\"Authorization: Bearer [REDACTED]\"\n",
                                    writer().value("Authorization: Bearer abc.def.ghi").finish()),
                    () ->
                            assertEquals(
                                    "\"password=[REDACTED]\"\n",
                                    writer().value("password=letmein").finish()),
                    () ->
                            assertEquals(
                                    "\"[REDACTED]\"\n",
                                    writer().value("AKIAIOSFODNN7EXAMPLE").finish()));
        }

        @Test
        @DisplayName("clears a short bare value that only the registry can recognise")
        void clearsAShortRegisteredValue() {
            // Twelve characters, no syntax around it at all.  This is the carrier a
            // size-conditioned
            // "fast path" in the redactor would leak, and the one a writer that skipped short
            // strings would leak; both are invisible to a test whose inputs are all long.
            assertEquals("\"[REDACTED]\"\n", loadedWriter().value(SWORDFISH).finish());
        }

        @Test
        @DisplayName("redacts before escaping, so the rules see the text a run really produced")
        void redactsBeforeEscaping() {
            String document = writer().value("password=\"letmein\"").finish();

            // Redact-then-escape gives `password="[REDACTED]"`, whose two quotes are then escaped.
            // Escape-then-redact would hand the assignment rule `password=\"letmein\"`, where the
            // quoted-value alternative no longer matches and the bare-value one swallows the
            // backslashes too, producing `password=[REDACTED]` with no quotes at all.  The two
            // orders give different documents, which is what makes this literal a real assertion
            // about the order rather than about the rules.
            assertEquals("\"password=\\\"[REDACTED]\\\"\"\n", document);
        }

        @Test
        @DisplayName("applies to every string in a map and an array, not only to named members")
        void appliesToMapsAndArrays() {
            Map<String, String> environment = new LinkedHashMap<>();
            environment.put("SAFE", "/usr/bin");
            environment.put("URL", "https://u:Tr0ub4dor-26-3@h/x");

            String document =
                    loadedWriter()
                            .beginObject()
                            .name("environment")
                            .sortedObject(environment)
                            .name("argv")
                            .arrayOfStrings(List.of("/bin/upload", SWORDFISH))
                            .endObject()
                            .finish();

            assertEquals(
                    """
                    {
                      "environment": {
                        "SAFE": "/usr/bin",
                        "URL": "https://u:[REDACTED]@h/x"
                      },
                      "argv": [
                        "/bin/upload",
                        "[REDACTED]"
                      ]
                    }
                    """,
                    document);
        }

        @Test
        @DisplayName("leaves ordinary scientific text exactly as it was")
        void leavesOrdinaryTextAlone() {
            // The other half of a redaction gate: a rule set that destroyed everything would pass
            // every "no secret survives" sweep ever written.
            String document =
                    writer().arrayOfStrings(
                                    List.of(
                                            "/data/HeLa_1ug_rep1.mzML",
                                            "peptide-level q < 0.01",
                                            "Carbamidomethyl (C) +57.021464"))
                            .finish();

            assertEquals(
                    """
                    [
                      "/data/HeLa_1ug_rep1.mzML",
                      "peptide-level q < 0.01",
                      "Carbamidomethyl (C) +57.021464"
                    ]
                    """,
                    document);
        }
    }

    @Nested
    @DisplayName("Misuse")
    class Misuse {

        @Test
        @DisplayName("a name outside an object is rejected, naming the name")
        void aNameOutsideAnObjectIsRejected() {
            assertAll(
                    () ->
                            assertEquals(
                                    "a name may only be written inside an object, and \"top\" was"
                                            + " not",
                                    assertThrows(
                                                    IllegalStateException.class,
                                                    () -> writer().name("top"))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "a name may only be written inside an object, and \"in-array\""
                                            + " was not",
                                    assertThrows(
                                                    IllegalStateException.class,
                                                    () -> writer().beginArray().name("in-array"))
                                            .getMessage()));
        }

        @Test
        @DisplayName("a value inside an object with no name is rejected")
        void aValueInsideAnObjectWithNoNameIsRejected() {
            IllegalStateException thrown =
                    assertThrows(
                            IllegalStateException.class,
                            () -> writer().beginObject().value("orphan"));

            assertEquals(
                    "a value inside an object must be preceded by a name", thrown.getMessage());
        }

        @Test
        @DisplayName("a second name before the first has a value is rejected")
        void aSecondNameBeforeAValueIsRejected() {
            IllegalStateException thrown =
                    assertThrows(
                            IllegalStateException.class,
                            () -> writer().beginObject().name("first").name("second"));

            assertEquals("the last name written has no value", thrown.getMessage());
        }

        @Test
        @DisplayName("closing an object with a dangling name is rejected")
        void closingWithADanglingNameIsRejected() {
            IllegalStateException thrown =
                    assertThrows(
                            IllegalStateException.class,
                            () -> writer().beginObject().name("first").endObject());

            assertEquals("the last name written has no value", thrown.getMessage());
        }

        @Test
        @DisplayName("closing with the wrong bracket is rejected, naming what is open")
        void closingWithTheWrongBracketIsRejected() {
            assertAll(
                    () ->
                            assertEquals(
                                    "the innermost open container is an array, which cannot be"
                                            + " closed with '}'",
                                    assertThrows(
                                                    IllegalStateException.class,
                                                    () -> writer().beginArray().endObject())
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "the innermost open container is an object, which cannot be"
                                            + " closed with ']'",
                                    assertThrows(
                                                    IllegalStateException.class,
                                                    () -> writer().beginObject().endArray())
                                            .getMessage()));
        }

        @Test
        @DisplayName("closing when nothing is open is rejected")
        void closingWhenNothingIsOpenIsRejected() {
            assertAll(
                    () ->
                            assertEquals(
                                    "there is no open container to close with '}'",
                                    assertThrows(
                                                    IllegalStateException.class,
                                                    () -> writer().endObject())
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "there is no open container to close with ']'",
                                    assertThrows(
                                                    IllegalStateException.class,
                                                    () -> writer().endArray())
                                            .getMessage()));
        }

        @Test
        @DisplayName("finishing with a container still open is rejected, counting them")
        void finishingWithAContainerOpenIsRejected() {
            IllegalStateException thrown =
                    assertThrows(
                            IllegalStateException.class,
                            () -> writer().beginObject().name("a").beginArray().finish());

            assertEquals("the document still has 2 unclosed container(s)", thrown.getMessage());
        }

        @Test
        @DisplayName("finishing with a dangling name is rejected, before the unclosed-count check")
        void finishingWithADanglingNameIsRejected() {
            IllegalStateException thrown =
                    assertThrows(
                            IllegalStateException.class,
                            () -> writer().beginObject().name("a").finish());

            // Two things are wrong here -- a name with no value and an object still open -- and
            // the message names the one the caller can act on first.
            assertEquals("the last name written has no value", thrown.getMessage());
        }

        @Test
        @DisplayName("finishing an empty document is rejected")
        void finishingAnEmptyDocumentIsRejected() {
            IllegalStateException thrown =
                    assertThrows(IllegalStateException.class, () -> writer().finish());

            assertEquals("the document has no root value", thrown.getMessage());
        }

        @Test
        @DisplayName("a second root value is rejected")
        void aSecondRootValueIsRejected() {
            JsonWriter json = writer();
            json.value("first");

            IllegalStateException thrown =
                    assertThrows(IllegalStateException.class, () -> json.value("second"));

            assertEquals("a JSON document has exactly one root value", thrown.getMessage());
        }

        @Test
        @DisplayName("everything is rejected once the document is finished")
        void everythingIsRejectedAfterFinish() {
            JsonWriter json = writer();
            json.beginObject().endObject().finish();

            String expected = "the document is finished and cannot be added to";
            assertAll(
                    () ->
                            assertEquals(
                                    expected,
                                    assertThrows(IllegalStateException.class, json::finish)
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    expected,
                                    assertThrows(
                                                    IllegalStateException.class,
                                                    () -> json.value("late"))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    expected,
                                    assertThrows(
                                                    IllegalStateException.class,
                                                    () -> json.name("late"))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    expected,
                                    assertThrows(IllegalStateException.class, json::endObject)
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    expected,
                                    assertThrows(IllegalStateException.class, json::endArray)
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    expected,
                                    assertThrows(IllegalStateException.class, json::beginArray)
                                            .getMessage()));
        }

        @Test
        @DisplayName("a rejected call leaves the document exactly as it was")
        void aRejectedCallLeavesTheDocumentUnchanged() {
            JsonWriter json = writer();
            json.beginObject().name("kept").value("value");

            assertThrows(IllegalStateException.class, () -> json.value("orphan"));

            assertEquals(
                    """
                    {
                      "kept": "value"
                    }
                    """,
                    json.endObject().finish());
        }
    }

    @Nested
    @DisplayName("Null arguments")
    class NullArguments {

        @Test
        @DisplayName("a writer cannot be built without a redactor")
        void aWriterCannotBeBuiltWithoutARedactor() {
            NullPointerException thrown =
                    assertThrows(
                            NullPointerException.class,
                            () -> JsonWriter.redactingWith(deliberateNull()));

            assertEquals("redactor", thrown.getMessage());
        }

        @Test
        @DisplayName("a null name, value, map or collection is rejected by parameter name")
        void nullArgumentsAreRejectedByName() {
            String nullName = deliberateNull();
            String nullValue = deliberateNull();
            Map<String, String> nullMap = deliberateNull();
            List<String> nullList = deliberateNull();

            assertAll(
                    () ->
                            assertEquals(
                                    "name",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () -> writer().beginObject().name(nullName))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "value",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () -> writer().value(nullValue))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "entries",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () -> writer().sortedObject(nullMap))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "values",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () -> writer().arrayOfStrings(nullList))
                                            .getMessage()));
        }

        @Test
        @DisplayName("a null element or map value is rejected rather than written as null")
        void nullElementsAreRejected() {
            List<String> withNull = new ArrayList<>();
            withNull.add("present");
            withNull.add(null);
            Map<String, String> valueIsNull = new LinkedHashMap<>();
            valueIsNull.put("key", null);

            // A silent JSON null here would be a manifest field that looks deliberately absent and
            // is actually a defect upstream.  nullValue() is the way to say "absent".
            assertAll(
                    () ->
                            assertEquals(
                                    "value",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () -> writer().arrayOfStrings(withNull))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "value",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () -> writer().sortedObject(valueIsNull))
                                            .getMessage()));
        }
    }

    @Nested
    @DisplayName("toString")
    class ToStringDescription {

        @Test
        @DisplayName("reports progress and never a character of the document")
        void reportsProgressAndNeverTheDocument() {
            JsonWriter json = loadedWriter();
            json.beginObject().name("token").value(SWORDFISH);

            String described = json.toString();

            // Twenty-five characters: `{`, a newline, two spaces, `"token": ` and `"[REDACTED]"`.
            assertAll(
                    () ->
                            assertEquals(
                                    "JsonWriter[depth=1, characters=25, finished=false]",
                                    described),
                    () -> assertFalse(described.contains(SWORDFISH)),
                    () -> assertFalse(described.contains("token")));
        }

        @Test
        @DisplayName("reports the document as finished once it is")
        void reportsTheDocumentAsFinished() {
            JsonWriter json = writer();
            json.beginObject().endObject().finish();

            assertEquals("JsonWriter[depth=0, characters=3, finished=true]", json.toString());
        }
    }
}
