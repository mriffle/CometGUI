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

package org.cometgui.domain.platform;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A GNU C Library version, parsed and comparable.
 *
 * <p>Accepts the shapes real systems emit. {@code gnu_get_libc_version()} answers {@code 2.36};
 * older releases carry a third component, as in {@code 2.3.4}; distributions append their own
 * packaging suffix, so {@code ldd --version} on Ubuntu shows {@code 2.31-0ubuntu9.9}. Surrounding
 * whitespace is ignored, because these strings usually arrive from a command's output.
 *
 * <p>The packaging suffix is retained in {@link #text()} for diagnostics but takes no part in
 * comparison: {@code 2.31-0ubuntu9.9} and {@code 2.31} are the same upstream glibc, and the
 * ordering of one distribution's build numbers means nothing to another's. An absent third
 * component is zero, so {@code 2.36} equals {@code 2.36.0}.
 *
 * <p>Anything else is rejected rather than guessed at. A version this class cannot read is reported
 * as undetermined by {@link HostBaselineVerifier}, which is a warning; a version it misread would
 * be a wrong comparison presented as a fact.
 */
public final class GlibcVersion implements Comparable<GlibcVersion> {

    private static final Pattern SHAPE =
            Pattern.compile("(\\d{1,9})\\.(\\d{1,9})(?:\\.(\\d{1,9}))?(?:-(\\S+))?");

    private final int major;
    private final int minor;
    private final int patch;
    private final String text;

    private GlibcVersion(int major, int minor, int patch, String text) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.text = text;
    }

    /**
     * Builds a version from its components, for a requirement stated in code rather than read from
     * a host.
     *
     * @param major the major component, not negative
     * @param minor the minor component, not negative
     * @param patch the patch component, not negative
     * @return the version
     * @throws IllegalArgumentException if a component is negative, naming which one
     */
    public static GlibcVersion of(int major, int minor, int patch) {
        requireNotNegative(major, "major");
        requireNotNegative(minor, "minor");
        requireNotNegative(patch, "patch");
        return new GlibcVersion(major, minor, patch, major + "." + minor + "." + patch);
    }

    private static void requireNotNegative(int component, String name) {
        if (component < 0) {
            throw new IllegalArgumentException(
                    "the "
                            + name
                            + " component of a glibc version must not be negative, but was: "
                            + component);
        }
    }

    /**
     * Parses a version string as printed by glibc itself or by a distribution's tooling.
     *
     * @param text the version text, for example {@code 2.36}, {@code 2.3.4} or {@code
     *     2.31-0ubuntu9.9}
     * @return the parsed version
     * @throws NullPointerException if {@code text} is {@code null}
     * @throws IllegalArgumentException if the text is blank or is not one of the accepted shapes,
     *     with a message quoting exactly what was rejected
     */
    public static GlibcVersion parse(String text) {
        Objects.requireNonNull(text, "text");
        String stripped = text.strip();
        if (stripped.isEmpty()) {
            throw new IllegalArgumentException("a glibc version must not be blank");
        }
        Matcher matched = SHAPE.matcher(stripped);
        if (!matched.matches()) {
            throw new IllegalArgumentException(
                    "not a recognised glibc version: \""
                            + stripped
                            + "\" (expected a form such as 2.36, 2.3.4 or 2.31-0ubuntu9.9)");
        }
        String third = matched.group(3);
        int patch = third == null ? 0 : Integer.parseInt(third);
        return new GlibcVersion(
                Integer.parseInt(matched.group(1)),
                Integer.parseInt(matched.group(2)),
                patch,
                stripped);
    }

    /**
     * @return the major component, {@code 2} in {@code 2.36.1}
     */
    public int major() {
        return major;
    }

    /**
     * @return the minor component, {@code 36} in {@code 2.36.1}
     */
    public int minor() {
        return minor;
    }

    /**
     * @return the patch component, {@code 1} in {@code 2.36.1} and {@code 0} when absent
     */
    public int patch() {
        return patch;
    }

    /**
     * The text this version was made from, stripped of surrounding whitespace and still carrying
     * any distribution suffix. Diagnostics quote this, so that a user reading "glibc
     * 2.31-0ubuntu9.9" recognises what their own system reports.
     *
     * @return the original text for a parsed version, or the canonical form for a constructed one
     */
    public String text() {
        return text;
    }

    /**
     * Whether this version satisfies a requirement.
     *
     * @param required the minimum acceptable version
     * @return {@code true} if this version is the required one or newer
     * @throws NullPointerException if {@code required} is {@code null}
     */
    public boolean isAtLeast(GlibcVersion required) {
        return compareTo(Objects.requireNonNull(required, "required")) >= 0;
    }

    /** {@inheritDoc} */
    @Override
    public int compareTo(GlibcVersion other) {
        Objects.requireNonNull(other, "other");
        int byMajor = Integer.compare(major, other.major);
        if (byMajor != 0) {
            return byMajor;
        }
        int byMinor = Integer.compare(minor, other.minor);
        if (byMinor != 0) {
            return byMinor;
        }
        return Integer.compare(patch, other.patch);
    }

    /*
     * Numeric components only, so that 2.36, 2.36.0 and 2.36-0ubuntu1 are one version -- and so
     * that equals stays consistent with compareTo, which is what a sorted set of versions relies
     * on. No `this == other` short circuit: it is unobservable, and an unobservable branch is a
     * mutation no honest test can kill.
     */
    @Override
    public boolean equals(Object other) {
        if (!(other instanceof GlibcVersion that)) {
            return false;
        }
        return major == that.major && minor == that.minor && patch == that.patch;
    }

    @Override
    public int hashCode() {
        return Objects.hash(major, minor, patch);
    }

    /**
     * The canonical three-component form, so that two versions printed side by side compare
     * visually. {@link #text()} keeps what the host actually said.
     *
     * @return {@code major.minor.patch}
     */
    @Override
    public String toString() {
        return major + "." + minor + "." + patch;
    }
}
