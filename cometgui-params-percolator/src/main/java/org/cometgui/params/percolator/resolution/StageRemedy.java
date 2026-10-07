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

package org.cometgui.params.percolator.resolution;

/**
 * What a scientist can do when a downstream stage is unavailable because no Percolator here has the
 * capability it needs ({@code R-PERC-03}).
 */
public enum StageRemedy {

    /** Register a local Percolator binary that has the capability. */
    REGISTER_LOCAL_BINARY(
            "register-local-binary",
            "Register a local Percolator binary that can write Percolator XML, from the Tool"
                    + " Manager. CometGUI probes it, and offers Limelight conversion if the probe"
                    + " observes XML_OUTPUT."),

    /** Run the stage on a platform where a capable Percolator is available. */
    CONVERT_ON_SUPPORTED_PLATFORM(
            "convert-on-supported-platform",
            "Run the Limelight conversion on a computer whose platform has an XML-capable"
                    + " Percolator, rerunning Percolator there from this run's merged PIN.");

    private final String id;
    private final String text;

    StageRemedy(String id, String text) {
        this.id = id;
        this.text = text;
    }

    /**
     * The stable identifier, for provenance and for tests.
     *
     * @return for example {@code register-local-binary}
     */
    public String id() {
        return id;
    }

    /**
     * The sentence the interface shows.
     *
     * @return the remedy, never blank
     */
    public String text() {
        return text;
    }
}
