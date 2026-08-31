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

package org.cometgui.provenance.redaction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The exact credential values this run is holding, so that they can be removed from any text
 * whatever the syntax around them.
 *
 * <p>This is the airtight half of the rule set described on {@link SecretRedactor}. The pattern
 * rules there recognise a secret by the shape of the text that carries it; they cannot recognise
 * {@code -k wJalrXUtnFEMI/K7MDENG} as a secret, because a bare argument after a single-letter flag
 * is indistinguishable from an ordinary one. The application does not have that problem: it read
 * the credential out of the OS keychain and knows the literal characters. Registering them here
 * turns "we hope no rule missed it" into "this string cannot appear in the output".
 *
 * <p><strong>Why there is a minimum length, and why it is {@value #MINIMUM_SECRET_LENGTH}.</strong>
 * Registration means unconditional substring replacement over every string this application emits
 * -- every path, every digest, every Comet parameter line, every version number. A short registered
 * value therefore does not redact a secret, it corrupts a document: {@code 1.0} occurs in {@code
 * peptide_mass_tolerance = 1.00}, {@code 2026} occurs in nearly every timestamp, and every
 * four-character hexadecimal run occurs somewhere in a 64-character SHA-256 with probability near
 * one. Eight characters is the shortest length at which an accidental collision with ordinary
 * provenance content stops being expected: over the 16-symbol alphabet of a hex digest a given
 * eight-character run appears in a 64-character digest with probability about 1 in 5x10^7, and
 * ordinary English or file-path text of that length is specific enough that a collision is a
 * surprise rather than a certainty. It is also at or below the length of every credential format
 * this project can be handed -- the shortest thing in phase 04's seeded corpus is twelve
 * characters, and API tokens are far longer -- so the floor costs nothing real.
 *
 * <p><strong>This object does not print what it holds.</strong> {@link #toString()} reports the
 * count and nothing else, and {@link SecretTooShortException} reports a length and nothing else. A
 * registry that leaked its contents into a log line, a debugger's inspection or an exception
 * message would be the single most direct way to defeat the thing it exists to guarantee.
 *
 * <p><strong>Immutable and thread-safe.</strong> The class is final, the one field is final, and it
 * holds an immutable list of immutable strings. {@link #with(String)} returns a new registry rather
 * than mutating this one, so a registry that has already been handed to a {@link SecretRedactor}
 * cannot change under it. Any number of threads may share one instance.
 */
public final class SecretRegistry {

    /**
     * The fewest characters a registered secret may have: 8.
     *
     * <p>The reasoning is in the class documentation. It is a public constant because a caller that
     * takes a credential from the user needs to be able to say why the value was refused before it
     * offers it, rather than catching {@link SecretTooShortException} as a control-flow device.
     */
    public static final int MINIMUM_SECRET_LENGTH = 8;

    /** The empty registry. Immutable, so one instance serves every caller that wants one. */
    private static final SecretRegistry EMPTY = new SecretRegistry(List.of());

    /**
     * The registered values, longest first.
     *
     * <p>The order is not cosmetic. If both {@code abcdefghij} and {@code abcdefgh} are registered
     * -- an API key and the account password it was derived from, say -- replacing the shorter one
     * first would turn {@code abcdefghij} into {@code [REDACTED]ij} and leave two characters of the
     * longer secret in the document. Longest first cannot do that.
     */
    private final List<String> secrets;

    private SecretRegistry(List<String> secrets) {
        this.secrets = secrets;
    }

    /**
     * The registry that holds nothing, for a run with no credential in play.
     *
     * <p>A redactor built on it still applies every pattern rule; only the literal-value pass has
     * nothing to do.
     *
     * @return the empty registry, never {@code null}
     */
    public static SecretRegistry empty() {
        return EMPTY;
    }

    /**
     * A registry holding the given values.
     *
     * @param values the literal credential values, each at least {@link #MINIMUM_SECRET_LENGTH}
     *     characters long
     * @return a new registry
     * @throws NullPointerException if the array or any element is {@code null}
     * @throws SecretTooShortException if any value is shorter than {@link #MINIMUM_SECRET_LENGTH}
     * @throws IllegalArgumentException if any value is blank
     */
    public static SecretRegistry of(String... values) {
        return copyOf(List.of(Objects.requireNonNull(values, "values")));
    }

    /**
     * A registry holding the given values.
     *
     * @param values the literal credential values, each at least {@link #MINIMUM_SECRET_LENGTH}
     *     characters long; duplicates are collapsed
     * @return a new registry
     * @throws NullPointerException if the collection or any element is {@code null}
     * @throws SecretTooShortException if any value is shorter than {@link #MINIMUM_SECRET_LENGTH}
     * @throws IllegalArgumentException if any value is blank
     */
    public static SecretRegistry copyOf(Collection<String> values) {
        Objects.requireNonNull(values, "values");
        Set<String> accepted = new LinkedHashSet<>();
        for (String value : values) {
            accepted.add(checked(value));
        }
        return new SecretRegistry(ordered(accepted));
    }

    /**
     * This registry plus one more value.
     *
     * <p>Returns a new registry; this one is unchanged, which is what lets a redactor already in
     * use by other threads stay valid.
     *
     * @param value the literal credential value to register
     * @return a registry holding everything this one holds, plus {@code value}
     * @throws NullPointerException if {@code value} is {@code null}
     * @throws SecretTooShortException if {@code value} is shorter than {@link
     *     #MINIMUM_SECRET_LENGTH}
     * @throws IllegalArgumentException if {@code value} is blank
     */
    public SecretRegistry with(String value) {
        Set<String> accepted = new LinkedHashSet<>(secrets);
        accepted.add(checked(value));
        return new SecretRegistry(ordered(accepted));
    }

    /**
     * How many distinct values this registry holds.
     *
     * @return the number of registered values, never negative
     */
    public int size() {
        return secrets.size();
    }

    /**
     * Replaces every occurrence of every registered value in {@code text} with the marker.
     *
     * <p>Package-private on purpose: the registry is a rule set, and the only thing entitled to
     * apply it is the redactor that owns the rest of the rules. Exposing it would invite a caller
     * to use the literal pass on its own and skip the pattern rules.
     *
     * @param text the text to clean, never {@code null}
     * @return the text with every registered value replaced by {@link
     *     SecretRedactor#REDACTION_MARKER}
     */
    String redactIn(String text) {
        String cleaned = text;
        for (String secret : secrets) {
            cleaned = cleaned.replace(secret, SecretRedactor.REDACTION_MARKER);
        }
        return cleaned;
    }

    /**
     * Describes the registry without disclosing a single character of what it holds.
     *
     * @return a description safe to put in a log line or an exception message
     */
    @Override
    public String toString() {
        return "SecretRegistry[secretCount=" + secrets.size() + "]";
    }

    /**
     * Validates one candidate value.
     *
     * <p>Length is checked before blankness so that a short run of spaces is reported as the length
     * problem it primarily is; a value long enough to pass the floor but made only of whitespace is
     * a different mistake and gets its own message.
     *
     * @param value the candidate
     * @return the value itself, when it is acceptable
     */
    private static String checked(String value) {
        Objects.requireNonNull(value, "secret");
        if (value.length() < MINIMUM_SECRET_LENGTH) {
            throw new SecretTooShortException(value.length());
        }
        if (value.isBlank()) {
            throw new IllegalArgumentException(
                    "a registered secret must not consist only of whitespace");
        }
        return value;
    }

    /**
     * Sorts the accepted values longest first, breaking ties alphabetically.
     *
     * <p>The tie-break exists so that two registries built from the same values in different orders
     * apply their replacements in the same order, and therefore produce byte-identical output. A
     * redactor whose result depended on the order credentials happened to be registered in would be
     * untestable.
     *
     * @param accepted the distinct accepted values
     * @return an immutable list, longest first
     */
    private static List<String> ordered(Set<String> accepted) {
        List<String> sorted = new ArrayList<>(accepted);
        sorted.sort(
                Comparator.comparingInt(String::length).reversed().thenComparing(value -> value));
        return List.copyOf(sorted);
    }
}
