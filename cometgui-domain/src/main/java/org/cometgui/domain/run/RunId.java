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

package org.cometgui.domain.run;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The identifier of one workflow run.
 *
 * <p>A run identifier is not decoration. It names the run's directory on disk, it is written into
 * the provenance manifest, and it is what a scientist quotes when asking why two searches
 * disagreed. So it is constrained to characters that are safe in a path segment on all three
 * supported platforms and readable in a filename: letters, digits, {@code .}, {@code -} and {@code
 * _}, starting with a letter or a digit.
 *
 * <p>That first-character rule is what stops {@code .}, {@code ..} and hidden names from ever
 * becoming a run directory. Reserved Windows device names ({@code CON}, {@code NUL}) are not
 * excluded here, because a run identifier produced by {@link org.cometgui.domain.ports.RunIdSource}
 * carries a timestamp and cannot collide with one; a phase that lets a user type an identifier owns
 * that check.
 *
 * @param value the identifier text
 */
public record RunId(String value) {

    /** Longest permitted identifier, chosen to keep the run directory well inside path limits. */
    public static final int MAX_LENGTH = 64;

    private static final Pattern PERMITTED = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");

    /**
     * Validates the identifier.
     *
     * @throws NullPointerException if {@code value} is {@code null}
     * @throws IllegalArgumentException if it is blank, longer than {@value #MAX_LENGTH} characters,
     *     or contains anything else -- with a message naming the rejected text
     */
    public RunId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("a run id must not be blank");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "a run id must be at most "
                            + MAX_LENGTH
                            + " characters, but was "
                            + value.length()
                            + ": \""
                            + value
                            + "\"");
        }
        if (!PERMITTED.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "a run id must start with a letter or digit and contain only letters, digits,"
                            + " '.', '-' and '_', but was: \""
                            + value
                            + "\"");
        }
    }

    /**
     * The identifier itself, so that a run id can be concatenated into a message or a path without
     * a wrapper's punctuation appearing in it.
     *
     * @return {@link #value()}
     */
    @Override
    public String toString() {
        return value;
    }
}
