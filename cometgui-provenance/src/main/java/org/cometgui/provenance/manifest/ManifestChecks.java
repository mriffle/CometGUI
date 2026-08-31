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

package org.cometgui.provenance.manifest;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * The validations the manifest records share, written once so that every record rejects the same
 * thing the same way.
 *
 * <p>Package-private on purpose: these are not a public API, they are the implementation of the
 * invariants each record documents for itself. Centralising them is what makes the rejection
 * messages uniform -- every one names the field and prints the rejected value, so a manifest that
 * fails to build says which component was wrong rather than which line threw.
 */
final class ManifestChecks {

    private ManifestChecks() {
        throw new AssertionError("ManifestChecks is a utility class and is never instantiated");
    }

    /**
     * Requires text that carries information.
     *
     * @param value the text to check
     * @param field the component name, used in the rejection message
     * @return {@code value}, unchanged
     */
    static String requireNonBlank(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(
                    field + " must not be blank, but was: \"" + value + "\"");
        }
        return value;
    }

    /**
     * Requires text that carries information, when there is any text at all.
     *
     * <p>An {@code Optional} component says "this fact may not exist". It does not say "this fact
     * may exist and be empty": a release tag of {@code ""} is not a release tag, it is an absent
     * one that was recorded as present, and a reader cannot tell the two apart afterwards.
     *
     * @param value the optional text to check
     * @param field the component name, used in the rejection message
     * @return {@code value}, unchanged
     */
    static Optional<String> nonBlankIfPresent(Optional<String> value, String field) {
        Objects.requireNonNull(value, field);
        return value.map(present -> requireNonBlank(present, field));
    }

    /**
     * Requires an absolute path.
     *
     * <p>The specification asks for "the canonical path at time of run", and a relative path is not
     * one: it means nothing without the working directory that was current when it was captured,
     * which the manifest does not record and a later reader does not have.
     *
     * @param path the path to check
     * @param field the component name, used in the rejection message
     * @return {@code path}, unchanged
     */
    static Path requireAbsolute(Path path, String field) {
        Objects.requireNonNull(path, field);
        if (!path.isAbsolute()) {
            throw new IllegalArgumentException(field + " must be absolute, but was: " + path);
        }
        return path;
    }

    /**
     * Requires that an interval does not run backwards.
     *
     * <p>A negative duration is not a value a report can render or a reader can trust, and it is
     * always a defect at the point of capture rather than something to be discovered later by
     * whoever subtracts the two.
     *
     * @param start the beginning of the interval
     * @param end the end of the interval
     */
    static void requireNotBefore(Instant start, Instant end) {
        if (end.isBefore(start)) {
            throw new IllegalArgumentException(
                    "end must not be before start, but start was " + start + " and end was " + end);
        }
    }

    /**
     * Copies a list, rejecting null elements and naming the offending index.
     *
     * <p>{@link List#copyOf} rejects a null element too, but it throws a {@link
     * NullPointerException} with no message at all, which tells the caller that something in a
     * manifest was null and nothing about which list or which position.
     *
     * @param <T> the element type
     * @param values the caller's list
     * @param field the component name, used in the rejection messages
     * @return an immutable copy in the given order
     */
    static <T> List<T> copyOfNonNull(List<T> values, String field) {
        Objects.requireNonNull(values, field);
        for (int index = 0; index < values.size(); index++) {
            if (values.get(index) == null) {
                throw new IllegalArgumentException(field + "[" + index + "] must not be null");
            }
        }
        return List.copyOf(values);
    }

    /**
     * Copies a list of text, rejecting null and blank elements and naming the offending index.
     *
     * @param values the caller's list
     * @param field the component name, used in the rejection messages
     * @return an immutable copy in the given order
     */
    static List<String> copyOfNonBlank(List<String> values, String field) {
        List<String> copy = copyOfNonNull(values, field);
        for (int index = 0; index < copy.size(); index++) {
            if (copy.get(index).isBlank()) {
                throw new IllegalArgumentException(
                        field
                                + "["
                                + index
                                + "] must not be blank, but was: \""
                                + copy.get(index)
                                + "\"");
            }
        }
        return copy;
    }

    /**
     * Copies a set into sorted order, rejecting null and blank elements.
     *
     * <p>Sorted by {@link String#compareTo}, which compares UTF-16 code units and is therefore the
     * same order on every machine. A locale-sensitive {@link java.text.Collator} would order these
     * differently in Sweden and in Germany, which is exactly the class of defect {@code R-PROV-04}
     * is about: two identical runs producing two different documents.
     *
     * @param values the caller's set
     * @param field the component name, used in the rejection messages
     * @return an immutable copy in ascending order
     */
    static SortedSet<String> sortedCopyOfNonBlank(Set<String> values, String field) {
        SortedSet<String> copy = new TreeSet<>(nonNullElements(values, field));
        for (String value : copy) {
            if (value.isBlank()) {
                throw new IllegalArgumentException(
                        field
                                + " must not contain a blank element, but contained: \""
                                + value
                                + "\"");
            }
        }
        return Collections.unmodifiableSortedSet(copy);
    }

    private static Set<String> nonNullElements(Set<String> values, String field) {
        Objects.requireNonNull(values, field);
        for (String value : values) {
            if (value == null) {
                throw new IllegalArgumentException(field + " must not contain a null element");
            }
        }
        return values;
    }
}
