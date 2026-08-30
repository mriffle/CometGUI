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

package org.cometgui.domain.log;

import java.util.Objects;

/**
 * How much attention one console line deserves.
 *
 * <p>The constants are declared from least to most severe, and that declaration order <em>is</em>
 * the ordering: {@link #atLeast(MessageSeverity)} compares by it, and the console's "show me at
 * least ..." filter is built on it. Reordering the constants therefore changes what every stored
 * filter means, so the order is part of this type's contract rather than an accident of layout.
 *
 * <p>{@link #STDERR} exists because a tool's standard error stream is not the same thing as an
 * error. Comet and Percolator both write ordinary progress to stderr, so folding that stream into
 * {@link #ERROR} would make every successful run look like a failure; folding it into {@link #INFO}
 * would throw away the one fact the reader needs when a tool dies without saying anything, which is
 * which stream a line arrived on. It therefore ranks above {@code INFO} -- worth surfacing -- and
 * below {@code WARNING} -- not, on its own, a problem.
 */
public enum MessageSeverity {

    /** Ordinary progress: the application's own narration, or a tool's standard output. */
    INFO,

    /** A line a tool wrote to its standard error stream. See the note on this type. */
    STDERR,

    /** Something the user should know about that did not stop the run. */
    WARNING,

    /** A failure: a stage that could not complete, or an output that could not be produced. */
    ERROR;

    /**
     * Whether this severity is at least as severe as {@code minimum}, in declaration order.
     *
     * @param minimum the least severity that passes the filter
     * @return {@code true} if this severity is {@code minimum} itself or something more severe
     * @throws NullPointerException if {@code minimum} is {@code null}
     */
    public boolean atLeast(MessageSeverity minimum) {
        Objects.requireNonNull(minimum, "minimum");
        return compareTo(minimum) >= 0;
    }
}
