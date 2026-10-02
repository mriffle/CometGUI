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

package org.cometgui.params.comet.migration;

import java.util.Objects;
import org.cometgui.params.comet.model.CometParameters;

/**
 * A migrated parameter set and the report of how it was made. The source model is unchanged; it is
 * kept here so that a caller can show both, and so that the parse warnings of an imported source
 * file ({@link CometParameters#diagnostics()}) stay with it.
 *
 * @param source the model that was migrated, unchanged
 * @param model the new model, of the target version
 * @param report what happened to every parameter
 */
public record MigrationResult(
        CometParameters source, CometParameters model, MigrationReport report) {

    /**
     * Validates the components.
     *
     * @throws IllegalArgumentException if the report's versions are not the two models'
     */
    public MigrationResult {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(report, "report");
        if (!report.from().equals(source.version()) || !report.to().equals(model.version())) {
            throw new IllegalArgumentException(
                    "the report is of a migration from Comet "
                            + report.from().text()
                            + " to "
                            + report.to().text()
                            + ", not of these models");
        }
    }
}
