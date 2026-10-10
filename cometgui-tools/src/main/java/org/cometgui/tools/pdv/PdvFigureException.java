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

package org.cometgui.tools.pdv;

import java.io.IOException;
import java.util.Objects;

/**
 * PDV's command line did not produce the figures asked for.
 *
 * <p>PDV exits 0 having written nothing (Phase 00), so every outcome is judged by the files it left
 * and each way of failing has its own name, which {@link #failure()} returns.
 */
public final class PdvFigureException extends IOException {

    private static final long serialVersionUID = 1L;

    /** The ways a figure run fails. */
    public enum Failure {
        /** PDV ran past its timeout and was stopped. */
        TIMED_OUT,
        /** PDV exited with a status other than 0. */
        EXITED_NON_ZERO,
        /** PDV exited 0 and wrote no figure for a requested spectrum. */
        NO_FIGURE,
        /** A figure PDV wrote is not a valid, drawn PNG, or answers no request. */
        INVALID_FIGURE,
        /** PDV drew every figure but listed problems in its {@code error.txt}. */
        PDV_REPORTED_ERRORS
    }

    private final Failure failure;

    /**
     * Creates the error.
     *
     * @param failure which way the run failed
     * @param message the whole message
     * @param cause the underlying failure, or {@code null}
     */
    PdvFigureException(Failure failure, String message, Throwable cause) {
        super(message, cause);
        this.failure = Objects.requireNonNull(failure, "failure");
    }

    /**
     * Which way the run failed.
     *
     * @return the failure
     */
    public Failure failure() {
        return failure;
    }
}
