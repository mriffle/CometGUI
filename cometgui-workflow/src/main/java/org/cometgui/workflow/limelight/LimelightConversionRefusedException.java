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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A Limelight conversion, or a change of a run's Limelight settings, that was refused before
 * anything was started: one or more prerequisites are not met. Nothing was written to the run's
 * output directory and no converter process ran.
 *
 * <p>The refusals do not survive serialization -- a {@link LimelightRefusal} names a file by a
 * {@link java.nio.file.Path}, which is not serializable, and nothing in this product serializes an
 * exception -- but the message, which names every refusal, does.
 */
public final class LimelightConversionRefusedException extends IOException {

    private static final long serialVersionUID = 1L;

    private final transient Refusals refusals;

    /**
     * Creates the refusal.
     *
     * @param refusals every prerequisite that is not met; at least one
     * @throws IllegalArgumentException if there is none
     */
    public LimelightConversionRefusedException(List<LimelightRefusal> refusals) {
        super(message(refusals));
        this.refusals = new Refusals(List.copyOf(refusals));
    }

    private static String message(List<LimelightRefusal> refusals) {
        Objects.requireNonNull(refusals, "refusals");
        if (refusals.isEmpty()) {
            throw new IllegalArgumentException("a refusal needs at least one reason");
        }
        List<String> sentences = new ArrayList<>(refusals.size());
        for (LimelightRefusal refusal : refusals) {
            sentences.add(refusal.prerequisite() + ": " + refusal.message());
        }
        return "the Limelight conversion was refused before anything was started -- "
                + String.join(" | ", sentences);
    }

    /**
     * Every prerequisite that is not met.
     *
     * @return the refusals, immutable
     */
    public List<LimelightRefusal> refusals() {
        return refusals == null ? List.of() : refusals.all();
    }

    /** The refusals, held in a type that does not pretend to be serializable. */
    private record Refusals(List<LimelightRefusal> all) {

        Refusals {
            all = List.copyOf(all);
        }

        @Override
        public List<LimelightRefusal> all() {
            return List.copyOf(all);
        }
    }
}
