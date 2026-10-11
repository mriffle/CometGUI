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

package org.cometgui.tools.limelight;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * A Limelight conversion was refused before it started, or its output was judged not to be a
 * Limelight XML file.
 *
 * <p>Every way of failing has its own name ({@link #failure()}) and, where a file is the reason,
 * the file ({@link #file()}); the message names both. The converter's exit status is never the
 * reason a conversion <em>succeeded</em>: it exits 0 with no arguments, on an unknown option and on
 * an unparseable {@code -q}, each time writing nothing (design decision P12-3).
 */
public final class LimelightConversionException extends IOException {

    private static final long serialVersionUID = 1L;

    /** The ways a conversion is refused or fails. */
    public enum Failure {
        /** The Percolator file given is not a Percolator XML document. */
        NOT_PERCOLATOR_XML,
        /** The Percolator XML holds no PSM, so no pepXML could be derived from it. */
        NO_PSMS,
        /** A PSM id does not have the {@code <file>_<scan>_<charge>_<rank>} shape. */
        PSM_ID_UNDERIVABLE,
        /** A pepXML the converter will look for is not in the pepXML directory. */
        PEPXML_MISSING,
        /** A pepXML the converter will read is not one of the run's recorded Comet outputs. */
        PEPXML_NOT_OF_THIS_RUN,
        /** A pepXML's SHA-256 differs from the one the run recorded for it. */
        PEPXML_CHECKSUM_MISMATCH,
        /** Something is already at the output path; a conversion never overwrites. */
        OUTPUT_EXISTS,
        /** The converter JAR holds no {@code limelight-xml.xsd} to validate against. */
        NO_SCHEMA,
        /** The converter ran past its timeout and was stopped. */
        TIMED_OUT,
        /** The converter exited with a status other than 0. */
        EXITED_NON_ZERO,
        /** No file is at the path the converter was told to write. */
        NO_OUTPUT,
        /** The file is there and holds zero bytes. */
        EMPTY_OUTPUT,
        /** The file is there and cannot be read. */
        UNREADABLE_OUTPUT,
        /** The file is not well-formed XML, or carries a DOCTYPE. */
        NOT_WELL_FORMED,
        /** The document element is not {@code limelight_input}. */
        WRONG_ROOT,
        /** The document is not valid against the converter's own schema. */
        SCHEMA_INVALID,
        /** The file does not carry the q-value cutoff the conversion asked for. */
        Q_CUTOFF_NOT_APPLIED
    }

    private final Failure failure;
    private final transient Path file;

    /**
     * Creates the error.
     *
     * @param failure which way the conversion failed
     * @param file the file that is the reason, or {@code null} when none is
     * @param message the whole message, naming the file and the reason
     * @param cause the underlying failure, or {@code null}
     */
    LimelightConversionException(Failure failure, Path file, String message, Throwable cause) {
        super(message, cause);
        this.failure = Objects.requireNonNull(failure, "failure");
        this.file = file;
    }

    /**
     * Which way the conversion failed.
     *
     * @return the failure
     */
    public Failure failure() {
        return failure;
    }

    /**
     * The file that is the reason.
     *
     * @return the file, or empty when the failure is not about one file
     */
    public Optional<Path> file() {
        return Optional.ofNullable(file);
    }
}
