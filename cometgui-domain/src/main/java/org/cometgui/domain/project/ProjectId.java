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

package org.cometgui.domain.project;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The identifier of one project.
 *
 * <p>It is written into {@code project.json}, into every {@code run.json} the project owns and into
 * the provenance manifest's {@code run.projectId}, so a run copied out of its project can still say
 * which project it came from. It is constrained exactly as {@link org.cometgui.domain.run.RunId} is
 * -- letters, digits, {@code .}, {@code -} and {@code _}, starting with a letter or a digit, at
 * most {@value #MAX_LENGTH} characters -- because it is the same kind of thing: a name that is
 * quoted in messages and may become part of a file name, and that must never be able to become
 * {@code ..}.
 *
 * <p>Who chooses it is not this class's business. {@code ProjectStore.create} takes one from its
 * caller, so a test chooses a readable one and the application a random one.
 *
 * @param value the identifier text
 */
public record ProjectId(String value) {

    /** Longest permitted identifier. */
    public static final int MAX_LENGTH = 64;

    private static final Pattern PERMITTED = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");

    /**
     * Validates the identifier.
     *
     * @throws NullPointerException if {@code value} is {@code null}
     * @throws IllegalArgumentException if it is empty, longer than {@value #MAX_LENGTH} characters,
     *     or contains anything else -- with a message naming the rejected text
     */
    public ProjectId {
        Objects.requireNonNull(value, "value");
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "a project id must be at most "
                            + MAX_LENGTH
                            + " characters, but was "
                            + value.length()
                            + ": \""
                            + value
                            + "\"");
        }
        if (!PERMITTED.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "a project id must start with a letter or digit and contain only letters,"
                            + " digits, '.', '-' and '_', but was: \""
                            + value
                            + "\"");
        }
    }

    /**
     * The identifier itself, so that it can be concatenated into a message without punctuation.
     *
     * @return {@link #value()}
     */
    @Override
    public String toString() {
        return value;
    }
}
