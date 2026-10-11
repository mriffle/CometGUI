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

package org.cometgui.workflow.limelight;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * One reason a run cannot be converted now: which prerequisite, the sentence a scientist is shown,
 * and the file it is about, when it is about one.
 *
 * @param prerequisite the prerequisite that is not met
 * @param message the explanation, naming the file and what is wrong with it
 * @param file the file, when the refusal is about one
 */
public record LimelightRefusal(
        LimelightPrerequisite prerequisite, String message, Optional<Path> file) {

    /**
     * Validates the refusal.
     *
     * @throws NullPointerException if a component is {@code null}
     * @throws IllegalArgumentException if the message is blank
     */
    public LimelightRefusal {
        Objects.requireNonNull(prerequisite, "prerequisite");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(file, "file");
        if (message.isBlank()) {
            throw new IllegalArgumentException("a refusal must say why");
        }
    }

    /**
     * A refusal about one file.
     *
     * @param prerequisite the prerequisite
     * @param file the file
     * @param message the explanation
     * @return the refusal
     */
    static LimelightRefusal of(LimelightPrerequisite prerequisite, Path file, String message) {
        return new LimelightRefusal(prerequisite, message, Optional.of(file));
    }

    /**
     * A refusal about no one file.
     *
     * @param prerequisite the prerequisite
     * @param message the explanation
     * @return the refusal
     */
    static LimelightRefusal of(LimelightPrerequisite prerequisite, String message) {
        return new LimelightRefusal(prerequisite, message, Optional.empty());
    }
}
