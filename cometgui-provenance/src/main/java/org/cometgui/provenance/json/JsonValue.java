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

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * One value out of a JSON document, as {@link JsonReader} rebuilt it.
 *
 * <p>A closed set of six shapes, sealed so that a reader can switch over them and the compiler
 * checks the switch is exhaustive. There is no seventh kind and no {@code Object} anywhere: a
 * consumer that has a {@link JsonValue} either has the kind it wanted or can say so precisely,
 * which is what lets {@link org.cometgui.provenance.manifest.ManifestReader} report "{@code
 * run.status} must be a string" rather than throwing a {@link ClassCastException} out of a cast.
 *
 * <p><strong>{@link JsonNull} is a value, not an absence.</strong> {@code provenance.json} writes
 * an absent optional as {@code null} and never omits the key, precisely so that a missing key means
 * something different from an empty one -- a schema disagreement rather than a run with nothing to
 * record. Modelling JSON null as Java {@code null} would collapse that distinction at the first map
 * lookup, so {@link JsonObject#member(String)} returns an empty {@link Optional} for a member that
 * is not in the document and {@code Optional.of(new JsonNull())} for one that is there and is
 * {@code null}.
 *
 * <p><strong>Every value here is immutable</strong>, and the two container kinds copy what they are
 * given and hand out copies again. A parsed document is a value, and a caller that could mutate one
 * could change a provenance record after it had been checked.
 *
 * <p><strong>These types are data, and their {@code toString} shows their content.</strong> That is
 * what a record is for and what makes a failing assertion readable. It also means a parsed value
 * must never be concatenated into an exception message or a log line: a document read from disk may
 * contain anything, and this package's whole error policy is that no character of a document
 * reaches a message. Nothing in {@link JsonReader} or in {@code ManifestReader} renders a {@link
 * JsonValue}, and both have tests that prove it.
 */
public sealed interface JsonValue {

    /**
     * A JSON object: member names to values, with no order.
     *
     * <p>Unordered deliberately. A JSON object is a set of members, this project's writer fixes the
     * order the document is written in, and a reader that let member order matter would be
     * inventing a rule the format does not have. Duplicate names cannot appear here at all: {@link
     * JsonReader} rejects a document containing one, so a name maps to exactly one value.
     *
     * @param members the members, keyed by name
     */
    record JsonObject(Map<String, JsonValue> members) implements JsonValue {

        /**
         * Copies the members into an immutable map.
         *
         * @throws NullPointerException if {@code members} is {@code null}, or if it holds a null
         *     name or a null value
         */
        public JsonObject {
            Objects.requireNonNull(members, "members");
            members = Map.copyOf(members);
        }

        /**
         * The members, immutable.
         *
         * <p>The copy is free -- the component is already an immutable map, and {@link Map#copyOf}
         * returns such a map unchanged -- and it is written out rather than relied upon so that the
         * guarantee is visible at the call site, and to SpotBugs, which reports a record accessor
         * handing out a collection field as {@code EI_EXPOSE_REP}.
         *
         * @return the members, immutable
         */
        @Override
        public Map<String, JsonValue> members() {
            return Map.copyOf(members);
        }

        /**
         * Looks a member up by name.
         *
         * @param name the member name to find
         * @return the member's value, or empty if the object has no member with that name; a member
         *     that is present and JSON {@code null} yields a present {@link JsonNull}
         * @throws NullPointerException if {@code name} is {@code null}
         */
        public Optional<JsonValue> member(String name) {
            return Optional.ofNullable(members.get(Objects.requireNonNull(name, "name")));
        }
    }

    /**
     * A JSON array, in the order the document wrote it.
     *
     * <p>Order is kept because an array's order is information: an argument array reordered is a
     * different command, and a list of warnings reordered is a different story about the run.
     *
     * @param elements the elements, in document order
     */
    record JsonArray(List<JsonValue> elements) implements JsonValue {

        /**
         * Copies the elements into an immutable list.
         *
         * @throws NullPointerException if {@code elements} is {@code null} or holds a null element
         */
        public JsonArray {
            Objects.requireNonNull(elements, "elements");
            elements = List.copyOf(elements);
        }

        /**
         * The elements, immutable and in document order.
         *
         * @return the elements, immutable
         */
        @Override
        public List<JsonValue> elements() {
            return List.copyOf(elements);
        }
    }

    /**
     * A JSON string, with every escape already decoded.
     *
     * @param value the text the document held, after {@code \\u} escapes and short escapes were
     *     resolved
     */
    record JsonString(String value) implements JsonValue {

        /**
         * Validates the value.
         *
         * @throws NullPointerException if {@code value} is {@code null}; JSON null is {@link
         *     JsonNull}, never a string with nothing in it
         */
        public JsonString {
            Objects.requireNonNull(value, "value");
        }
    }

    /**
     * A JSON number, which in this format is always a whole number.
     *
     * <p>{@link org.cometgui.provenance.json.JsonWriter} renders every number with {@link
     * Long#toString(long)} and the manifest's numbers are byte counts, exit codes and a schema
     * version -- there is no quantity in a provenance record that is not an integer. So this holds
     * a {@code long} rather than a {@code double} or a {@link java.math.BigDecimal}, and {@link
     * JsonReader} rejects a fraction, an exponent and a literal too large for the type rather than
     * silently losing precision on the way in. A byte count that arrived as {@code 1.23456789E12}
     * would be a file size nobody could verify.
     *
     * @param value the whole number the document held
     */
    record JsonNumber(long value) implements JsonValue {}

    /**
     * JSON {@code true} or {@code false}.
     *
     * @param value the flag the document held
     */
    record JsonBoolean(boolean value) implements JsonValue {

        /** JSON {@code true}. */
        public static final JsonBoolean TRUE = new JsonBoolean(true);

        /** JSON {@code false}. */
        public static final JsonBoolean FALSE = new JsonBoolean(false);
    }

    /**
     * JSON {@code null}: a value that is present and says there is nothing to record.
     *
     * <p>See the interface documentation for why this is a value rather than a Java {@code null}.
     * The record has no components, so every instance equals every other and {@link #NULL} is
     * simply the one worth reusing.
     */
    record JsonNull() implements JsonValue {

        /** The one instance worth allocating. */
        public static final JsonNull NULL = new JsonNull();
    }
}
