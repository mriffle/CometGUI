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

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A caveat about one tool version, carrying a stable identifier.
 *
 * <p>{@code R-PERC-11} requires advisories to be <strong>shown at selection time and recorded in
 * provenance</strong>, which is why the identifier exists: a test can assert that a particular
 * advisory was shown, and a provenance record read a year later can be searched for it, neither of
 * which works if the only handle on an advisory is its prose. The text may be reworded; the
 * identifier may not.
 *
 * <p>Percolator 3.07.1 -- the version resolution returns today for a Limelight-enabled run --
 * carries at least two: it predates 3.08's change of default PEP regressor to I-splines, and it
 * predates the fix for PEP values exceeding 1.0. A scientist choosing it for the Limelight path is
 * entitled to see what they are trading away, which is the whole point of surfacing these rather
 * than silently picking the version.
 *
 * @param id the stable identifier, lower case, made of {@code a-z}, {@code 0-9} and single {@code
 *     .} or {@code -} separators -- for example {@code percolator.pep-regressor-changed-in-3-08}
 * @param text the sentence shown to the user, stripped of surrounding whitespace and never blank
 */
public record ToolAdvisory(String id, String text) {

    private static final Pattern ID_SHAPE = Pattern.compile("[a-z0-9]+(?:[.-][a-z0-9]+)*");

    /**
     * Validates the advisory.
     *
     * @throws NullPointerException if either component is {@code null}
     * @throws IllegalArgumentException if {@code id} is not of the required shape or {@code text}
     *     is blank, with a message naming the field and the rejected value
     */
    public ToolAdvisory {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(text, "text");
        if (!ID_SHAPE.matcher(id).matches()) {
            throw new IllegalArgumentException(
                    "not a usable advisory id: \""
                            + id
                            + "\" (expected lower-case words joined by single dots or hyphens,"
                            + " such as percolator.pep-regressor-changed-in-3-08)");
        }
        if (text.isBlank()) {
            throw new IllegalArgumentException(
                    "text must not be blank: an advisory with no sentence tells the user nothing");
        }
        text = text.strip();
    }
}
