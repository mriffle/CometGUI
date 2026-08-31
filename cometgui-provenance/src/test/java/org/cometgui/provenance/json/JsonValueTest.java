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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
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

/**
 * Tests for the parsed-value model itself, independently of the parser that fills it.
 *
 * <p>Two properties are worth proving here and neither is provable through {@link JsonReader}: that
 * a container copies what it is handed and hands back a copy, so a parsed document cannot be
 * mutated after it has been checked; and that a present JSON {@code null} is distinguishable from
 * an absent member, which is the distinction {@code provenance.json} depends on for every optional
 * field.
 */
class JsonValueTest {

    /**
     * A {@code null} of whatever type the call site needs, by a route no analyser folds away.
     *
     * <p>The same idiom, and the same reason, as {@code ManifestWriterTest.deliberateNull}:
     * SpotBugs at effort Max reports a literal {@code null} passed to a parameter the callee
     * dereferences, and the test whose whole purpose is to prove the rejection would fail the
     * build.
     *
     * @param <T> the type the call site needs
     * @return {@code null}
     */
    private static <T> T deliberateNull() {
        return Optional.<T>empty().orElse(null);
    }

    @Nested
    @DisplayName("An object")
    class Objects {

        @Test
        @DisplayName("finds a member it has and reports one it does not")
        void findsAMemberItHas() {
            JsonObject object =
                    new JsonObject(
                            Map.of("name", new JsonString("comet"), "exitCode", new JsonNumber(0)));

            assertAll(
                    () -> assertEquals(Optional.of(new JsonString("comet")), object.member("name")),
                    () -> assertEquals(Optional.of(new JsonNumber(0)), object.member("exitCode")),
                    () -> assertEquals(Optional.empty(), object.member("stageId")));
        }

        @Test
        @DisplayName("tells a present null apart from an absent member")
        void tellsAPresentNullApartFromAnAbsentMember() {
            JsonObject object = new JsonObject(Map.of("end", JsonNull.NULL));

            assertAll(
                    () -> assertEquals(Optional.of(new JsonNull()), object.member("end")),
                    () -> assertEquals(Optional.empty(), object.member("durationMillis")));
        }

        @Test
        @DisplayName("does not change when the map it was built from is changed afterwards")
        void doesNotChangeWhenItsSourceMapChanges() {
            Map<String, JsonValue> source = new LinkedHashMap<>();
            source.put("name", new JsonString("comet"));
            JsonObject object = new JsonObject(source);

            source.put("name", new JsonString("percolator"));
            source.put("version", new JsonString("3.07.1"));

            assertAll(
                    () -> assertEquals(Optional.of(new JsonString("comet")), object.member("name")),
                    () -> assertEquals(Optional.empty(), object.member("version")),
                    () -> assertEquals(1, object.members().size()));
        }

        @Test
        @DisplayName("hands out a map that cannot be modified")
        void handsOutAMapThatCannotBeModified() {
            JsonObject object = new JsonObject(Map.of("name", new JsonString("comet")));
            Map<String, JsonValue> handedOut = object.members();

            assertAll(
                    () ->
                            assertThrows(
                                    UnsupportedOperationException.class,
                                    () -> handedOut.put("name", JsonNull.NULL)),
                    () -> assertEquals(Map.of("name", new JsonString("comet")), object.members()));
        }

        @Test
        @DisplayName("is equal to another with the same members, whatever order they were given in")
        void isEqualToAnotherWithTheSameMembers() {
            Map<String, JsonValue> forwards = new LinkedHashMap<>();
            forwards.put("a", new JsonNumber(1));
            forwards.put("b", new JsonNumber(2));
            Map<String, JsonValue> backwards = new LinkedHashMap<>();
            backwards.put("b", new JsonNumber(2));
            backwards.put("a", new JsonNumber(1));

            assertAll(
                    () -> assertEquals(new JsonObject(forwards), new JsonObject(backwards)),
                    () ->
                            assertNotEquals(
                                    new JsonObject(forwards),
                                    new JsonObject(Map.of("a", new JsonNumber(1)))));
        }

        @Test
        @DisplayName("refuses a null map, a null member name and a null member value")
        void refusesNulls() {
            Map<String, JsonValue> nullName = new LinkedHashMap<>();
            nullName.put(deliberateNull(), new JsonNumber(1));
            Map<String, JsonValue> nullValue = new LinkedHashMap<>();
            nullValue.put("a", deliberateNull());

            NullPointerException noMap =
                    assertThrows(
                            NullPointerException.class, () -> new JsonObject(deliberateNull()));

            assertAll(
                    () -> assertEquals("members", noMap.getMessage()),
                    () -> assertThrows(NullPointerException.class, () -> new JsonObject(nullName)),
                    () ->
                            assertThrows(
                                    NullPointerException.class, () -> new JsonObject(nullValue)));
        }

        @Test
        @DisplayName("refuses a null name to look up")
        void refusesANullNameToLookUp() {
            JsonObject object = new JsonObject(Map.of("a", new JsonNumber(1)));

            assertThrows(NullPointerException.class, () -> object.member(deliberateNull()));
        }
    }

    @Nested
    @DisplayName("An array")
    class Arrays {

        @Test
        @DisplayName(
                "keeps the order it was given, because an argument array's order is the command")
        void keepsTheOrderItWasGiven() {
            JsonArray array =
                    new JsonArray(
                            List.of(
                                    new JsonString("/opt/comet"),
                                    new JsonString("-P"),
                                    new JsonString("comet.params")));

            assertEquals(
                    List.of(
                            new JsonString("/opt/comet"),
                            new JsonString("-P"),
                            new JsonString("comet.params")),
                    array.elements());
        }

        @Test
        @DisplayName("does not change when the list it was built from is changed afterwards")
        void doesNotChangeWhenItsSourceListChanges() {
            List<JsonValue> source = new ArrayList<>();
            source.add(new JsonNumber(1));
            JsonArray array = new JsonArray(source);

            source.add(new JsonNumber(2));

            assertEquals(List.of(new JsonNumber(1)), array.elements());
        }

        @Test
        @DisplayName("hands out a list that cannot be modified")
        void handsOutAListThatCannotBeModified() {
            JsonArray array = new JsonArray(List.of(new JsonNumber(1)));
            List<JsonValue> handedOut = array.elements();

            assertAll(
                    () ->
                            assertThrows(
                                    UnsupportedOperationException.class,
                                    () -> handedOut.add(new JsonNumber(2))),
                    () -> assertEquals(List.of(new JsonNumber(1)), array.elements()));
        }

        @Test
        @DisplayName("refuses a null list and a null element")
        void refusesNulls() {
            List<JsonValue> nullElement = new ArrayList<>();
            nullElement.add(deliberateNull());

            NullPointerException noList =
                    assertThrows(NullPointerException.class, () -> new JsonArray(deliberateNull()));

            assertAll(
                    () -> assertEquals("elements", noList.getMessage()),
                    () ->
                            assertThrows(
                                    NullPointerException.class, () -> new JsonArray(nullElement)));
        }
    }

    @Nested
    @DisplayName("A scalar")
    class Scalars {

        @Test
        @DisplayName("holds exactly the text, number or flag it was given")
        void holdsExactlyWhatItWasGiven() {
            assertAll(
                    () -> assertEquals("protéomique", new JsonString("protéomique").value()),
                    () -> assertEquals("", new JsonString("").value()),
                    () -> assertEquals(1234567890123L, new JsonNumber(1234567890123L).value()),
                    () -> assertEquals(Long.MIN_VALUE, new JsonNumber(Long.MIN_VALUE).value()),
                    () -> assertTrue(JsonBoolean.TRUE.value()),
                    () -> assertEquals(new JsonBoolean(false), JsonBoolean.FALSE));
        }

        @Test
        @DisplayName("refuses a null string, because JSON null is its own kind")
        void refusesANullString() {
            NullPointerException thrown =
                    assertThrows(
                            NullPointerException.class, () -> new JsonString(deliberateNull()));

            assertEquals("value", thrown.getMessage());
        }

        @Test
        @DisplayName("makes every JSON null equal to every other")
        void makesEveryNullEqualToEveryOther() {
            assertAll(
                    () -> assertEquals(new JsonNull(), JsonNull.NULL),
                    () -> assertNotEquals(JsonNull.NULL, new JsonString("null")));
        }
    }
}
