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

package org.cometgui.params.comet.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One finding of the parser, located where it can be: the lines it concerns and the parameter.
 *
 * <p>An {@link Severity#ERROR} means the file cannot be represented faithfully, so the parse
 * produces no model ({@code R-PARAM-08}: a failed parse leaves the typed model untouched). A {@link
 * Severity#WARNING} is carried on the model the parse produced, so that the editor can show it.
 *
 * @param severity whether the finding stops the parse
 * @param code what kind of finding it is
 * @param lines the 1-based lines it concerns, in file order: none, one, or two for a duplicate
 * @param parameter the parameter it concerns, if one
 * @param message the finding in words, naming the line or lines and the parameter
 */
public record Diagnostic(
        Severity severity,
        Code code,
        List<Integer> lines,
        Optional<String> parameter,
        String message) {

    /** Whether a finding stops the parse. */
    public enum Severity {

        /** The file cannot be represented faithfully; no model is produced. */
        ERROR,

        /** The model is produced and carries the finding. */
        WARNING
    }

    /** The kinds of finding, each with its fixed severity. */
    public enum Code {

        /** A line that is no accepted shape. */
        MALFORMED_LINE(Severity.ERROR),

        /** A parameter declared more than once. */
        DUPLICATE_PARAMETER(Severity.ERROR),

        /** More than one {@code # comet_version} line. */
        DUPLICATE_VERSION_MARKER(Severity.ERROR),

        /** A modelled parameter whose value text cannot be read as its kind. */
        UNREADABLE_VALUE(Severity.ERROR),

        /** No {@code [COMET_ENZYME_INFO]} table. */
        ENZYME_TABLE_MISSING(Severity.ERROR),

        /** An enzyme row that cannot be read. */
        UNREADABLE_ENZYME_ROW(Severity.ERROR),

        /** Two enzyme rows with one number. */
        DUPLICATE_ENZYME_NUMBER(Severity.ERROR),

        /**
         * The file names another Comet version than the selected one, or one that cannot be read.
         */
        VERSION_MISMATCH(Severity.WARNING),

        /** The file has no {@code # comet_version} line. */
        VERSION_MARKER_MISSING(Severity.WARNING),

        /** A parameter the schema does not model at all; kept and written back. */
        UNKNOWN_PARAMETER(Severity.WARNING),

        /**
         * A parameter the schema models for other Comet versions but not the selected one; kept and
         * written back like an unknown parameter.
         */
        NOT_IN_VERSION(Severity.WARNING);

        private final Severity severity;

        Code(Severity severity) {
            this.severity = severity;
        }

        /**
         * The severity every finding of this kind has.
         *
         * @return the severity
         */
        public Severity severity() {
            return severity;
        }
    }

    /**
     * Validates the components.
     *
     * @throws IllegalArgumentException if the severity is not the code's
     */
    public Diagnostic {
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(code, "code");
        lines = List.copyOf(lines);
        Objects.requireNonNull(parameter, "parameter");
        Objects.requireNonNull(message, "message");
        if (severity != code.severity()) {
            throw new IllegalArgumentException(
                    code + " is always " + code.severity() + ", not " + severity);
        }
    }

    /**
     * A finding with the severity its code has.
     *
     * @param code the kind of finding
     * @param lines the lines it concerns
     * @param parameter the parameter it concerns, or {@code null} for none
     * @param message the finding in words
     * @return the diagnostic
     */
    public static Diagnostic of(Code code, List<Integer> lines, String parameter, String message) {
        return new Diagnostic(
                code.severity(), code, lines, Optional.ofNullable(parameter), message);
    }

    /**
     * The lines, immutable.
     *
     * @return the 1-based line numbers
     */
    @Override
    public List<Integer> lines() {
        return List.copyOf(lines);
    }

    /**
     * Whether the finding stops the parse.
     *
     * @return {@code true} for an error
     */
    public boolean isError() {
        return severity == Severity.ERROR;
    }
}
