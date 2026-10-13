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

package org.cometgui.workflow.provenance;

/**
 * Where a warning the Provenance section shows came from.
 *
 * <p>One constant per place in the record, the event log or the run directory that carries
 * something a scientist must be told. The list is the census the read model's tests are written
 * against: each constant has a test that plants its source and finds the warning.
 */
public enum WarningSource {

    /** {@code provenance.json} is absent: the run is shown from {@code run.json} and the log. */
    NO_SEALED_RECORD("no sealed record"),

    /** {@code provenance.json} exists and could not be read; the reader's message says why. */
    RECORD_UNREADABLE("record unreadable"),

    /** {@code run.json} is missing or could not be read. */
    RUN_JSON_UNREADABLE("run.json unreadable"),

    /** {@code events.log} is missing or could not be read at all. */
    EVENT_LOG_UNREADABLE("event log unreadable"),

    /** A defect the event log reader reported: a torn tail, a malformed line, a sequence gap. */
    EVENT_LOG_DEFECT("event log defect"),

    /** The run's recorded outcome is failed or cancelled. */
    RUN_OUTCOME("run outcome"),

    /** A {@code stage.finished} event carrying the message a failed or cancelled step left. */
    STEP_FAILURE("step failure"),

    /** A file the record lists with a status other than {@code completed}, such as partial. */
    PARTIAL_FILE("partial file"),

    /**
     * A version advisory recorded for the Percolator or the Comet build ({@code
     * percolator.advisory.*}, {@code comet.advisory.*}).
     */
    VERSION_ADVISORY("version advisory"),

    /**
     * A capability the artefact manifest claims and the install's probe did not find ({@code
     * R-TOOL-07}; {@code comet.probe-discrepancy.*}, {@code percolator.probe-discrepancy.*}).
     */
    MANIFEST_PROBE_DISCREPANCY("manifest/probe discrepancy"),

    /** A newer Percolator skipped by resolution, and why ({@code percolator.skipped.*}). */
    SKIPPED_VERSION("skipped version"),

    /** An option the command did not emit for this build, and why. */
    OPTION_NOT_EMITTED("option not emitted"),

    /** {@code R-PERC-08}'s warning that no learned weights file was written. */
    WEIGHTS_FALLBACK("weights fallback"),

    /**
     * A warning a tool record carries that no more specific source names: for example a log that
     * could not be hashed. An advisory or a manifest/probe discrepancy the settings also record is
     * shown once, under its own source.
     */
    TOOL_WARNING("tool warning"),

    /** A {@code warning.raised} event in the log. */
    WARNING_EVENT("warning event"),

    /** A Limelight conversion or upload that the log records as having failed. */
    LIMELIGHT_FAILURE("limelight failure"),

    /** An archived parameter file whose digest no longer matches its record, or that is gone. */
    PARAMETER_FILE_CHANGED("parameter file changed");

    private final String word;

    WarningSource(String word) {
        this.word = word;
    }

    /**
     * The words the Provenance section shows for this source.
     *
     * @return the display words, lower case
     */
    public String word() {
        return word;
    }
}
