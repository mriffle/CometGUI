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

package org.cometgui.workflow.engine;

import java.util.Objects;
import java.util.Optional;
import org.cometgui.workflow.state.EngineStep;

/**
 * One reason a recorded result cannot be reused ({@code R-RUN-02}): which step, which file or
 * invocation, and what is wrong with it.
 *
 * @param step the step whose reuse is refused; it must execute again
 * @param kind what is wrong
 * @param subject the file's path, or the invocation's stage identifier, or the manifest's path
 * @param role the file's role and direction, as {@code "input file, role spectrum"}; empty for an
 *     invocation or a missing manifest
 * @param recordedSha256 the SHA-256 the manifest records, when there is one
 * @param currentSha256 the SHA-256 the file has now, when it was re-hashed
 */
public record ReuseMismatch(
        EngineStep step,
        Kind kind,
        String subject,
        String role,
        Optional<String> recordedSha256,
        Optional<String> currentSha256) {

    /** What is wrong with a recorded result. */
    public enum Kind {

        /** The file's content differs from what was recorded. */
        CHANGED,

        /** The file no longer exists. */
        MISSING,

        /** The recorded manifest does not list the file. */
        NOT_RECORDED,

        /** The recorded manifest lists the file, but not as complete. */
        NOT_COMPLETED,

        /** The recorded manifest does not list the invocation as completed. */
        INVOCATION_NOT_RECORDED,

        /** There is no readable recorded manifest. */
        NO_MANIFEST
    }

    /**
     * Requires every component.
     *
     * @throws NullPointerException naming a component that is {@code null}
     */
    public ReuseMismatch {
        Objects.requireNonNull(step, "step");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(recordedSha256, "recordedSha256");
        Objects.requireNonNull(currentSha256, "currentSha256");
    }

    /**
     * One sentence for the user, naming the step, the file or invocation, and both hashes where
     * there are two.
     *
     * @return the description
     */
    public String describe() {
        String what = subject + " (" + role + ", of step " + step.id() + ")";
        return switch (kind) {
            case CHANGED ->
                    what
                            + " has changed since it was recorded: recorded SHA-256 "
                            + recordedSha256.orElse("none")
                            + ", now "
                            + currentSha256.orElse("none");
            case MISSING ->
                    what + " no longer exists; recorded SHA-256 " + recordedSha256.orElse("none");
            case NOT_RECORDED -> what + " is not in the recorded manifest";
            case NOT_COMPLETED -> what + " is recorded as incomplete";
            case INVOCATION_NOT_RECORDED ->
                    "invocation "
                            + subject
                            + " of step "
                            + step.id()
                            + " is not recorded as completed";
            case NO_MANIFEST ->
                    "step "
                            + step.id()
                            + " cannot be checked: there is no readable recorded manifest at "
                            + subject;
        };
    }
}
