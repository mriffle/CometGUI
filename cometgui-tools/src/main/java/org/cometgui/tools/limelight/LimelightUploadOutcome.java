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

import java.net.URI;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * How an upload ended: submitted, refused by the server, or failed before the server could answer
 * (phase 12, design decision P12-15). None of the three carries the key, and none prints it.
 */
public sealed interface LimelightUploadOutcome {

    /**
     * The step the upload reached.
     *
     * @return the last step sent
     */
    LimelightUploadStep step();

    /**
     * The server took the file and queued its import.
     *
     * @param serverProgramVersion the submit-program version the server said it is coded for
     * @param uploadKey the server's {@code uploadKey}: the import's tracking id
     * @param importerSubDir the server's {@code importerSubDir}
     * @param projectPage where the scientist sees the import
     * @param bytesSent how many bytes of the file were sent
     */
    record Submitted(
            int serverProgramVersion,
            String uploadKey,
            String importerSubDir,
            URI projectPage,
            long bytesSent)
            implements LimelightUploadOutcome {

        /**
         * Checks the result.
         *
         * @throws NullPointerException if a component is {@code null}
         */
        public Submitted {
            Objects.requireNonNull(uploadKey, "uploadKey");
            Objects.requireNonNull(importerSubDir, "importerSubDir");
            Objects.requireNonNull(projectPage, "projectPage");
        }

        @Override
        public LimelightUploadStep step() {
            return LimelightUploadStep.FINAL_SUBMIT;
        }
    }

    /**
     * The server answered {@code statusSuccess="false"}: its own reason flags and message, as it
     * sent them.
     *
     * @param step the step it refused
     * @param httpStatus the HTTP status the answer came with (200, or 400 for a refused file)
     * @param flags every attribute the server set to true, by its wire name, sorted
     * @param message the server's {@code statusFailErrorMessage}, when it sent one
     */
    record Refused(
            LimelightUploadStep step,
            int httpStatus,
            Map<String, String> flags,
            Optional<String> message)
            implements LimelightUploadOutcome {

        /**
         * Checks and copies the result.
         *
         * @throws NullPointerException if a component is {@code null}
         */
        public Refused {
            Objects.requireNonNull(step, "step");
            flags = Map.copyOf(new TreeMap<>(Objects.requireNonNull(flags, "flags")));
            Objects.requireNonNull(message, "message");
        }

        /**
         * The flags in wire-name order.
         *
         * @return a sorted copy
         */
        @Override
        public Map<String, String> flags() {
            return new TreeMap<>(flags);
        }
    }

    /**
     * No answer the protocol defines arrived: the connection failed, timed out or was cut, or the
     * server answered with a status or body that is not a submit-import answer.
     *
     * @param step the step that failed
     * @param reason what happened, in words
     */
    record Failed(LimelightUploadStep step, String reason) implements LimelightUploadOutcome {

        /**
         * Checks the result.
         *
         * @throws NullPointerException if a component is {@code null}
         */
        public Failed {
            Objects.requireNonNull(step, "step");
            Objects.requireNonNull(reason, "reason");
        }
    }
}
