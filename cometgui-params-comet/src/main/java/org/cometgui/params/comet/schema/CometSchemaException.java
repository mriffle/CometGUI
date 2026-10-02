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

package org.cometgui.params.comet.schema;

import java.io.Serial;
import java.util.List;
import java.util.Objects;

/**
 * Thrown when a Comet build could not be asked for its parameters, or its answer could not be used.
 * {@link #failure()} says which of the ways it went wrong, so a caller can tell a build that would
 * not start from one that ran and wrote nothing; {@link #output()} holds what the process printed.
 */
public final class CometSchemaException extends Exception {

    @Serial private static final long serialVersionUID = 1L;

    /** What went wrong. */
    private final Failure failure;

    /** What the process printed, bounded. An array, so that the exception stays serialisable. */
    private final String[] output;

    /**
     * Creates the exception.
     *
     * @param failure what went wrong
     * @param message the whole story, naming the command and the directory
     * @param output what the process printed, possibly empty
     * @param cause the underlying exception, or {@code null}
     */
    public CometSchemaException(
            Failure failure, String message, List<String> output, Throwable cause) {
        super(message, cause);
        this.failure = Objects.requireNonNull(failure, "failure");
        this.output = output.toArray(new String[0]);
    }

    /**
     * What went wrong.
     *
     * @return the failure kind
     */
    public Failure failure() {
        return failure;
    }

    /**
     * What the process printed on either stream, in arrival order, each line prefixed with its
     * stream.
     *
     * @return the lines, possibly empty
     */
    public List<String> output() {
        return List.of(output);
    }

    /** The ways asking a Comet build for its parameters can fail. */
    public enum Failure {

        /** The workspace could not be prepared or the process could not be started. */
        LAUNCH_FAILED,

        /** The process did not finish within the configured time and was cancelled. */
        TIMED_OUT,

        /** The waiting thread was interrupted; the process was cancelled. */
        INTERRUPTED,

        /** The process exited with a code other than 0. */
        NON_ZERO_EXIT,

        /** The process exited 0 and wrote no {@code comet.params.new}. */
        NO_FILE_WRITTEN,

        /** The file it wrote could not be read, or is not a well-formed dump. */
        UNREADABLE_DUMP,

        /** The dump's version marker names a different version from the build's identity. */
        VERSION_MISMATCH
    }
}
