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

package org.cometgui.results.parser;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * A Percolator output file this package refuses to read, with a message that names the file and
 * what is wrong with it, written for the scientist who will see it.
 *
 * <p>Every refusal carries a {@link Problem}, so a caller can tell the kinds apart without reading
 * the message.
 */
public final class PercolatorOutputException extends IOException {

    private static final long serialVersionUID = 1L;

    /** Why a file was refused. */
    public enum Problem {
        /** The file does not exist. */
        MISSING_FILE,
        /** The file holds no bytes at all. */
        EMPTY_FILE,
        /** The file could not be read, or is not UTF-8. */
        UNREADABLE,
        /** A result table's header lacks a column the parser needs. */
        MISSING_COLUMN,
        /** A result table's header names a needed column more than once. */
        DUPLICATE_COLUMN,
        /** A row holds a different number of fields than its header allows. */
        ROW_WIDTH,
        /** A line is blank where the format has none. */
        BLANK_LINE,
        /** A weights value is not a finite decimal number. */
        NOT_A_NUMBER,
        /** A weights header names a feature with no name, or the same feature twice. */
        BAD_FEATURE_NAME,
        /** A weights split has its header of feature names but not both weight rows. */
        HEADER_WITHOUT_ROWS,
        /** A weights file holds comment lines only: no cross-validation split at all. */
        NO_SPLITS,
        /** A weights split names different features from the first split. */
        SPLIT_MISMATCH
    }

    private final transient Path file;
    private final Problem problem;

    /**
     * A refusal.
     *
     * @param file the file refused
     * @param problem why
     * @param message the whole message, which names the file
     * @param cause the underlying failure, or {@code null}
     */
    PercolatorOutputException(Path file, Problem problem, String message, Throwable cause) {
        super(message, cause);
        this.file = Objects.requireNonNull(file, "file");
        this.problem = Objects.requireNonNull(problem, "problem");
    }

    /**
     * The file refused.
     *
     * @return its path, as the caller gave it
     */
    public Path file() {
        return file;
    }

    /**
     * Why it was refused.
     *
     * @return the kind of problem
     */
    public Problem problem() {
        return problem;
    }
}
