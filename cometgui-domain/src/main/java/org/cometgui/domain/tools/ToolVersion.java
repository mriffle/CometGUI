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

package org.cometgui.domain.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The version of a scientific tool, parsed, ordered and kept as upstream wrote it.
 *
 * <p>Modelled on {@link org.cometgui.domain.platform.GlibcVersion}, which does the same job for the
 * C library, rather than as a fourth version style: two to four numeric components, compared
 * numerically, with the original text kept for display.
 *
 * <p><strong>Components compare numerically, so a leading zero means nothing.</strong> Percolator
 * calls its releases {@code 3.06.5}, {@code 3.07.1} and {@code 3.09}; Comet calls one {@code
 * 2026.02.2}. Read as text, {@code 3.09} sorts before {@code 3.07.1} and the product would offer
 * the wrong build. Read as numbers, {@code 3.06.5 < 3.07.1 < 3.09}, which is the order upstream
 * means. A shorter version is padded with zeros, so {@code 3.09} and {@code 3.09.0} are one
 * version.
 *
 * <p><strong>What {@code equals} means here.</strong> Two versions are equal when their numeric
 * components are equal after that padding -- so {@code 3.07.1} equals {@code 3.7.1} and {@code
 * 3.09} equals {@code 3.09.0}, even though the three texts differ. That is deliberate and it is
 * consistent with {@link #compareTo}, which a sorted set of versions relies on. It also means
 * {@code equals} is <em>not</em> a test of how a version was written: use {@link #text()} for that.
 *
 * <p><strong>The original text is kept.</strong> {@code 3.07.1} is what upstream calls that release
 * and what its release tag, its download URL and its own version banner say. A provenance record
 * that renamed it {@code 3.7.1} would be recording something the user cannot look up, so {@link
 * #text()} returns what was parsed and only {@link #toString()} normalises.
 */
public final class ToolVersion implements Comparable<ToolVersion> {

    /** The fewest components a tool version may have: a bare {@code 3} is not a version here. */
    public static final int MINIMUM_COMPONENTS = 2;

    /** The most components a tool version may have. */
    public static final int MAXIMUM_COMPONENTS = 4;

    /*
     * Nine digits at most per component, so that every component fits in an int and no version
     * this class accepts can overflow the comparison it exists to perform.
     */
    private static final Pattern SHAPE = Pattern.compile("\\d{1,9}(?:\\.\\d{1,9}){1,3}");

    private static final String EXPECTED =
            " (expected two to four numeric components, such as 3.09, 3.07.1 or 2026.02.2)";

    private final List<Integer> components;
    private final String text;

    private ToolVersion(List<Integer> components, String text) {
        this.components = components;
        this.text = text;
    }

    /**
     * Builds a version from its components, for a requirement stated in code rather than read from
     * a tool or a manifest.
     *
     * @param components two to four components, none negative, most significant first
     * @return the version
     * @throws NullPointerException if {@code components} is {@code null}
     * @throws IllegalArgumentException if there are too few or too many components, or if one is
     *     negative, naming which one
     */
    public static ToolVersion of(int... components) {
        Objects.requireNonNull(components, "components");
        if (components.length < MINIMUM_COMPONENTS || components.length > MAXIMUM_COMPONENTS) {
            throw new IllegalArgumentException(
                    "a tool version must have two to four components, but was given "
                            + components.length);
        }
        List<Integer> values = new ArrayList<>(components.length);
        for (int index = 0; index < components.length; index++) {
            if (components[index] < 0) {
                throw new IllegalArgumentException(
                        "component "
                                + (index + 1)
                                + " of a tool version must not be negative, but was: "
                                + components[index]);
            }
            values.add(components[index]);
        }
        List<Integer> trimmed = withoutTrailingZeros(values);
        return new ToolVersion(trimmed, join(trimmed));
    }

    /**
     * Parses a version as a tool, a release tag or a manifest writes it.
     *
     * @param text the version text, for example {@code 3.09}, {@code 3.07.1} or {@code 2026.02.2};
     *     surrounding whitespace is ignored, because these strings usually arrive from a command's
     *     output
     * @return the parsed version, keeping {@code text} for display
     * @throws NullPointerException if {@code text} is {@code null}
     * @throws IllegalArgumentException if the text is blank or is not two to four numeric
     *     components, with a message quoting exactly what was rejected
     */
    public static ToolVersion parse(String text) {
        Objects.requireNonNull(text, "text");
        String stripped = text.strip();
        if (stripped.isEmpty()) {
            throw new IllegalArgumentException("a tool version must not be blank");
        }
        if (!SHAPE.matcher(stripped).matches()) {
            throw new IllegalArgumentException(
                    "not a recognised tool version: \"" + stripped + "\"" + EXPECTED);
        }
        List<Integer> values = new ArrayList<>(MAXIMUM_COMPONENTS);
        for (String part : stripped.split("\\.")) {
            values.add(Integer.valueOf(part));
        }
        return new ToolVersion(withoutTrailingZeros(values), stripped);
    }

    /*
     * A trailing zero component carries no information -- 3.09.0 IS 3.09 -- so it is dropped here,
     * once, and every later comparison, equality test and rendering works on the same list. Never
     * below two components, so that a version always reads as a version.
     */
    private static List<Integer> withoutTrailingZeros(List<Integer> values) {
        int size = values.size();
        while (size > MINIMUM_COMPONENTS && values.get(size - 1) == 0) {
            size--;
        }
        return List.copyOf(values.subList(0, size));
    }

    private static String join(List<Integer> values) {
        StringBuilder rendered = new StringBuilder(16);
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                rendered.append('.');
            }
            rendered.append(values.get(index).intValue());
        }
        return rendered.toString();
    }

    /**
     * The numeric components, most significant first, with trailing zero components dropped.
     *
     * <p>{@code 3.07.1} answers {@code [3, 7, 1]} and {@code 3.09.0} answers {@code [3, 9]}: the
     * leading zero is not part of the number and the trailing zero is not part of the version.
     *
     * @return the components, immutable and never fewer than two
     */
    public List<Integer> components() {
        return List.copyOf(components);
    }

    /**
     * The text this version was made from, stripped of surrounding whitespace.
     *
     * <p>Diagnostics, provenance records and the Tool Manager quote this, so that a scientist
     * reading "Percolator 3.07.1" recognises the release upstream published under that name.
     *
     * @return the original text for a parsed version, or the normalised form for a constructed one
     */
    public String text() {
        return text;
    }

    /**
     * Whether this version satisfies a minimum.
     *
     * @param required the oldest acceptable version, for example Percolator {@code 3.05}
     * @return {@code true} if this version is the required one or newer
     * @throws NullPointerException if {@code required} is {@code null}
     */
    public boolean isAtLeast(ToolVersion required) {
        return compareTo(Objects.requireNonNull(required, "required")) >= 0;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Component by component, most significant first, with the shorter version padded with
     * zeros.
     */
    @Override
    public int compareTo(ToolVersion other) {
        Objects.requireNonNull(other, "other");
        int width = Math.max(components.size(), other.components.size());
        /*
         * KNOWN EQUIVALENT MUTANT, recorded rather than left for someone to rediscover. PIT's
         * conditional-boundary mutator turns `index < width` into `index <= width` and that mutant
         * survives every test in this class -- necessarily, because the extra iteration compares
         * componentAt(width) on both sides, which is 0 against 0 for any two versions. It is a
         * mutant with no behaviour, not a hole in the tests. The alternative implementation that
         * kills it -- compare the shared prefix, then let the longer version win -- is correct only
         * because withoutTrailingZeros guarantees a normalised version of more than two components
         * ends in a non-zero one, and buying one mutation point with a comparison that silently
         * depends on a distant invariant is the wrong trade.
         */
        for (int index = 0; index < width; index++) {
            int difference = Integer.compare(componentAt(index), other.componentAt(index));
            if (difference != 0) {
                return difference;
            }
        }
        return 0;
    }

    private int componentAt(int index) {
        return index < components.size() ? components.get(index) : 0;
    }

    /*
     * Numeric components only, so that 3.07.1, 3.7.1 and 3.07.1.0 are one version -- and so that
     * equals stays consistent with compareTo, which is what a sorted set of versions relies on. No
     * `this == other` short circuit: it is unobservable, and an unobservable branch is a mutation
     * no honest test can kill.
     */
    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ToolVersion that)) {
            return false;
        }
        return components.equals(that.components);
    }

    @Override
    public int hashCode() {
        return components.hashCode();
    }

    /**
     * The normalised form, so that two versions printed side by side compare visually: {@code
     * 3.07.1} renders as {@code 3.7.1} and {@code 3.09.0} as {@code 3.9}.
     *
     * <p>{@link #text()} keeps what upstream actually called the release, and is what a user
     * interface and a provenance record show.
     *
     * @return the components joined by dots, with no leading or trailing zeros
     */
    @Override
    public String toString() {
        return join(components);
    }
}
