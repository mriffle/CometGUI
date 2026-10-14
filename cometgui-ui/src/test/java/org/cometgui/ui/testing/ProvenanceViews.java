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

package org.cometgui.ui.testing;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.cometgui.workflow.provenance.DiskOutcome;
import org.cometgui.workflow.provenance.ProvenanceView;
import org.cometgui.workflow.provenance.RecordState;
import org.cometgui.workflow.provenance.RunCondition;
import org.cometgui.workflow.provenance.RunListing;
import org.cometgui.workflow.provenance.WarningSource;

/**
 * Read-model values a test types out: a failed two-tool run with a partial output, and the run
 * listings around it. Every digest is a hand-typed string; nothing here is computed.
 */
public final class ProvenanceViews {

    /** Comet's recorded MD5. */
    public static final String COMET_MD5 = "0123456789abcdef0123456789abcdef";

    /** Comet's recorded SHA-256. */
    public static final String COMET_SHA256 =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    /** Percolator's recorded MD5. */
    public static final String PERCOLATOR_MD5 = "fedcba9876543210fedcba9876543210";

    /** Percolator's recorded SHA-256. */
    public static final String PERCOLATOR_SHA256 =
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    /** The spectrum file's recorded MD5. */
    public static final String SPECTRUM_MD5 = "11111111111111111111111111111111";

    /** The spectrum file's recorded SHA-256. */
    public static final String SPECTRUM_SHA256 =
            "1111111111111111111111111111111111111111111111111111111111111111";

    /** The partial pepXML's recorded MD5. */
    public static final String PEPXML_MD5 = "22222222222222222222222222222222";

    /** The partial pepXML's recorded SHA-256. */
    public static final String PEPXML_SHA256 =
            "2222222222222222222222222222222222222222222222222222222222222222";

    /** Comet's log's recorded SHA-256. */
    public static final String LOG_SHA256 =
            "3333333333333333333333333333333333333333333333333333333333333333";

    /** The Comet execution's display command. */
    public static final String COMET_COMMAND =
            "[\"/cache/comet\", \"-P/run/parameters/comet.params\", \"-N/run/outputs/comet/a\","
                    + " \"/data/a.mzML\"]";

    /** The Percolator execution's display command. */
    public static final String PERCOLATOR_COMMAND =
            "[\"/cache/percolator\", \"--no-analytics\", \"/run/inputs/pin/merged.pin\"]";

    /** Where Comet's executable is, to act on. */
    public static final Path COMET = Path.of("cache/comet");

    /** Where the spectrum file is, to act on. */
    public static final Path SPECTRUM = Path.of("data/a.mzML");

    /** Where the partial pepXML is, to act on. */
    public static final Path PEPXML = Path.of("run/outputs/comet/a.pep.xml");

    /** Where Comet's log is, to act on. */
    public static final Path COMET_LOG = Path.of("run/logs/comet-01.log");

    private ProvenanceViews() {}

    /**
     * A listing of a run CometGUI made.
     *
     * @param name the run's directory name, which also gives its identifier after the dash
     * @param condition what its {@code run.json} says
     * @param sealed whether it has a {@code provenance.json}
     * @return the listing
     */
    public static RunListing listing(String name, RunCondition condition, boolean sealed) {
        return new RunListing(
                name,
                Path.of("project/runs").resolve(name),
                Optional.of(name.substring(name.indexOf('-') + 1)),
                Optional.of("2026-10-14T08:00:00Z"),
                condition,
                sealed,
                Optional.empty());
    }

    /**
     * A listing of a directory whose {@code run.json} cannot be read.
     *
     * @param name the directory's name
     * @param problem why
     * @return the listing
     */
    public static RunListing unreadable(String name, String problem) {
        return new RunListing(
                name,
                Path.of("project/runs").resolve(name),
                Optional.empty(),
                Optional.empty(),
                RunCondition.UNREADABLE,
                false,
                Optional.of(problem));
    }

    /**
     * A failed run with a sealed record: Comet and Percolator, a spectrum input, a partial pepXML.
     *
     * @return the view
     */
    public static ProvenanceView failedRun() {
        return failedRun(RecordState.SEALED, "failed");
    }

    /**
     * The failed run's view with another record state and status.
     *
     * @param state the record's state
     * @param status the run's status
     * @return the view
     */
    public static ProvenanceView failedRun(RecordState state, String status) {
        ProvenanceView.Summary summary =
                new ProvenanceView.Summary(
                        "r1",
                        "cometgui-default",
                        status,
                        state,
                        "2026-10-14T08:00:00Z",
                        Optional.of("2026-10-14T08:00:05.250Z"),
                        Optional.of("0:00:05.250"),
                        List.of("comet 2026.03.0", "percolator 3.07.1"),
                        1,
                        1,
                        state == RecordState.SEALED
                                ? Optional.of(
                                        new ProvenanceView.Application(
                                                "0.1.0",
                                                "abc1234",
                                                "Linux 6.8",
                                                "amd64",
                                                "25.0.1",
                                                "en-GB",
                                                "en-GB",
                                                "Europe/London"))
                                : Optional.empty());
        List<ProvenanceView.Tool> tools =
                List.of(
                        new ProvenanceView.Tool(
                                1,
                                "comet",
                                "2026.03.0",
                                Optional.of("v2026.03.0"),
                                "managed",
                                "/cache/comet",
                                COMET,
                                COMET_MD5,
                                COMET_SHA256,
                                Optional.of("https://example.org/comet sha256:" + COMET_SHA256),
                                List.of("pin-output", "pepxml-output"),
                                Optional.of("comet-01"),
                                List.of("advisory one"),
                                COMET_COMMAND),
                        new ProvenanceView.Tool(
                                2,
                                "percolator",
                                "3.07.1",
                                Optional.empty(),
                                "local",
                                "/cache/percolator",
                                Path.of("cache/percolator"),
                                PERCOLATOR_MD5,
                                PERCOLATOR_SHA256,
                                Optional.empty(),
                                List.of(),
                                Optional.empty(),
                                List.of(),
                                PERCOLATOR_COMMAND));
        List<ProvenanceView.File> files =
                List.of(
                        new ProvenanceView.File(
                                "input",
                                "spectrum",
                                "/data/a.mzML",
                                SPECTRUM,
                                1234567,
                                "2026-10-01T00:00:00Z",
                                SPECTRUM_MD5,
                                SPECTRUM_SHA256,
                                "completed",
                                false),
                        new ProvenanceView.File(
                                "output",
                                "pepxml",
                                "/run/outputs/comet/a.pep.xml",
                                PEPXML,
                                42,
                                "2026-10-14T08:00:05Z",
                                PEPXML_MD5,
                                PEPXML_SHA256,
                                "partial",
                                true));
        ProvenanceView.Parameters parameters =
                new ProvenanceView.Parameters(
                        Optional.of("2026.03.0"),
                        Optional.of(
                                new ProvenanceView.ArchivedText(
                                        "comet-params",
                                        "/run/parameters/comet.params",
                                        Path.of("run/parameters/comet.params"),
                                        "cccc",
                                        DiskOutcome.MATCHES,
                                        Optional.of("cccc"),
                                        Optional.of("# comet_version 2026.03.0\nnum_threads = 4\n"),
                                        Optional.empty())),
                        Optional.empty(),
                        List.of(new ProvenanceView.Setting("percolator.seed", "1")),
                        List.of(
                                new ProvenanceView.Parameter(
                                        "num_threads",
                                        "4",
                                        Optional.of("preset"),
                                        Optional.of("0"),
                                        true,
                                        Optional.of("8"),
                                        true),
                                new ProvenanceView.Parameter(
                                        "decoy_search",
                                        "1",
                                        Optional.empty(),
                                        Optional.empty(),
                                        false,
                                        Optional.empty(),
                                        false)),
                        List.of("a note"),
                        List.of(
                                new ProvenanceView.Preset(
                                        1,
                                        "lab-threads",
                                        "Lab threads",
                                        "user",
                                        "2026.03.0",
                                        "1",
                                        List.of("num_threads = 8"))));
        ProvenanceView.Timeline timeline =
                new ProvenanceView.Timeline(
                        List.of(
                                new ProvenanceView.Execution(
                                        "comet",
                                        "2026.03.0",
                                        Optional.of("comet-01"),
                                        COMET_COMMAND,
                                        "/run",
                                        "LANG=C.UTF-8",
                                        "2026-10-14T08:00:01Z",
                                        "2026-10-14T08:00:05Z",
                                        "0:00:04.000",
                                        4000,
                                        134,
                                        "failed"),
                                new ProvenanceView.Execution(
                                        "percolator",
                                        "3.07.1",
                                        Optional.empty(),
                                        PERCOLATOR_COMMAND,
                                        "/run",
                                        "",
                                        "2026-10-14T08:00:05Z",
                                        "2026-10-14T08:00:05Z",
                                        "0:00:00.000",
                                        0,
                                        0,
                                        "completed")),
                        List.of(
                                new ProvenanceView.Event(
                                        1,
                                        "2026-10-14T08:00:00Z",
                                        "run.started",
                                        List.of("attempt = 1")),
                                new ProvenanceView.Event(
                                        2, "2026-10-14T08:00:05Z", "run.finished", List.of())));
        List<ProvenanceView.Log> logs =
                List.of(
                        new ProvenanceView.Log(
                                "comet",
                                Optional.of("comet-01"),
                                "stdout and stderr",
                                "/run/logs/comet-01.log",
                                COMET_LOG,
                                "33333333333333333333333333333333",
                                LOG_SHA256));
        List<ProvenanceView.Warning> warnings =
                List.of(
                        new ProvenanceView.Warning(
                                WarningSource.RUN_OUTCOME, "run", "the run failed"),
                        new ProvenanceView.Warning(
                                WarningSource.PARTIAL_FILE,
                                "/run/outputs/comet/a.pep.xml",
                                "this output is partial"),
                        new ProvenanceView.Warning(
                                WarningSource.PARTIAL_FILE,
                                "/run/outputs/comet/a.pin",
                                "this output is partial"));
        return new ProvenanceView(summary, tools, files, parameters, timeline, logs, warnings);
    }
}
