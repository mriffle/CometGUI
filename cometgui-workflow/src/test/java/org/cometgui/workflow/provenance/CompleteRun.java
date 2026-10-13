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

import static org.cometgui.workflow.provenance.RunDirectories.event;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.cometgui.domain.run.AttemptOutcome;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.domain.secrets.SecretRedactor;
import org.cometgui.provenance.events.ProvenanceEventType;
import org.cometgui.provenance.manifest.FileDirection;
import org.cometgui.provenance.manifest.FileRecord;
import org.cometgui.provenance.manifest.ProvenanceManifest;
import org.cometgui.provenance.manifest.ProvenanceStatus;
import org.cometgui.provenance.manifest.ToolRecord;

/**
 * A fully populated sealed run: two Comet invocations, one Percolator invocation, every kind of
 * file, recorded origins and an applied preset, every Percolator warning setting, and an event log
 * with post-seal events. Written by the product's writers; every value is chosen here.
 */
record CompleteRun(
        RunLayout layout,
        ProvenanceManifest manifest,
        Path cometExe,
        Path percolatorExe,
        Path pepXml1,
        Path pin2,
        Path cometLog1,
        Path percolatorLog,
        Path settingsFile) {

    static final String RUN_ID = "run-0001";

    static final String ADVISORY =
            "Percolator 3.07.1 is the newest build that writes pout XML on every platform";

    static final String WEIGHTS =
            "R-PERC-08: no learned weights file was written, so the weights are not available"
                    + " from a file: this build has no --weights option";

    static final String SKIP_REASON = "it cannot write pout XML, which Limelight needs";

    static final String NOT_EMITTED_REASON = "this build does not accept --weights";

    static CompleteRun create(RunDirectories dirs, SecretRedactor redactor) throws IOException {
        return create(dirs, redactor, ProvenanceStatus.COMPLETED);
    }

    static CompleteRun create(RunDirectories dirs, SecretRedactor redactor, ProvenanceStatus status)
            throws IOException {
        RunLayout layout =
                dirs.recordedRun(RUN_ID, RunDirectories.CREATED, RunDirectories.cometParams());
        dirs.attempt(
                layout,
                RunDirectories.STARTED,
                status == ProvenanceStatus.COMPLETED
                        ? AttemptOutcome.SUCCEEDED
                        : status == ProvenanceStatus.FAILED
                                ? AttemptOutcome.FAILED
                                : AttemptOutcome.CANCELLED,
                RunDirectories.ENDED);
        Path cometDirectory = Files.createDirectories(dirs.root().resolve("tools/comet/2026.03.0"));
        Path cometExe = cometDirectory.resolve("comet.linux.exe");
        Files.writeString(cometExe, RunDirectories.COMET_EXE_CONTENT, StandardCharsets.US_ASCII);
        Path percolatorDirectory =
                Files.createDirectories(dirs.root().resolve("tools/percolator/3.07.1"));
        Path percolatorExe = percolatorDirectory.resolve("percolator");
        Files.writeString(
                percolatorExe, RunDirectories.PERCOLATOR_EXE_CONTENT, StandardCharsets.US_ASCII);

        Path pepXml1 =
                RunDirectories.write(layout, "outputs/comet/k562_1.pep.xml", "<pepxml 1/>\n");
        Path pin1 =
                RunDirectories.write(layout, "outputs/comet/k562_1.pin", "SpecId\tLabel\n1\t1\n");
        Path pepXml2 =
                RunDirectories.write(layout, "outputs/comet/k562_2.pep.xml", "<pepxml 2/>\n");
        Path pin2 =
                RunDirectories.write(layout, "outputs/comet/k562_2.pin", "SpecId\tLabel\n2\t1\n");
        Path settingsFile =
                RunDirectories.write(
                        layout, "parameters/percolator-settings.json", "{\"seed\": \"1\"}\n");
        Path psms =
                RunDirectories.write(
                        layout, "outputs/percolator/percolator.target.psms.txt", "PSMId\tq\n");
        Path cometLog1 = RunDirectories.write(layout, "logs/comet-01.log", "comet 1 log\n");
        Path cometLog2 = RunDirectories.write(layout, "logs/comet-02.log", "comet 2 log\n");
        Path percolatorLog = RunDirectories.write(layout, "logs/percolator.log", "perc log\n");

        boolean ok = status == ProvenanceStatus.COMPLETED;
        ProvenanceStatus outputs = ok ? ProvenanceStatus.COMPLETED : ProvenanceStatus.PARTIAL;
        List<FileRecord> files =
                List.of(
                        RunDirectories.fileRecord(
                                FileDirection.INPUT,
                                "spectrum",
                                dirs.inputFile("k562_1.mzML"),
                                ProvenanceStatus.COMPLETED),
                        RunDirectories.fileRecord(
                                FileDirection.INPUT,
                                "spectrum",
                                dirs.inputFile("k562_2.mzML"),
                                ProvenanceStatus.COMPLETED),
                        RunDirectories.fileRecord(
                                FileDirection.INPUT,
                                "fasta",
                                dirs.inputFile("db.fasta"),
                                ProvenanceStatus.COMPLETED),
                        RunDirectories.fileRecord(
                                FileDirection.OUTPUT,
                                "comet-params",
                                layout.cometParamsFile(),
                                ProvenanceStatus.COMPLETED),
                        RunDirectories.fileRecord(
                                FileDirection.OUTPUT,
                                "pepxml",
                                pepXml1,
                                ProvenanceStatus.COMPLETED),
                        RunDirectories.fileRecord(
                                FileDirection.OUTPUT, "pin", pin1, ProvenanceStatus.COMPLETED),
                        RunDirectories.fileRecord(
                                FileDirection.OUTPUT,
                                "pepxml",
                                pepXml2,
                                ProvenanceStatus.COMPLETED),
                        RunDirectories.fileRecord(FileDirection.OUTPUT, "pin", pin2, outputs),
                        RunDirectories.fileRecord(
                                FileDirection.INPUT,
                                "percolator-settings",
                                settingsFile,
                                ProvenanceStatus.COMPLETED),
                        RunDirectories.fileRecord(
                                FileDirection.OUTPUT, "percolator-psms", psms, outputs));

        ToolRecord comet1 =
                RunDirectories.tool(
                        "comet",
                        "2026.03.0",
                        cometExe,
                        true,
                        Set.of(),
                        "comet-01",
                        List.of(
                                cometExe.toString(),
                                "-P" + layout.cometParamsFile(),
                                "-N" + layout.root().resolve("outputs/comet/k562_1"),
                                dirs.inputFile("k562_1.mzML").toString()),
                        layout.root(),
                        Map.of(),
                        Instant.parse("2026-10-13T09:00:02.000Z"),
                        Instant.parse("2026-10-13T09:05:00.250Z"),
                        0,
                        ProvenanceStatus.COMPLETED,
                        cometLog1,
                        List.of());
        ToolRecord comet2 =
                RunDirectories.tool(
                        "comet",
                        "2026.03.0",
                        cometExe,
                        true,
                        Set.of(),
                        "comet-02",
                        List.of(
                                cometExe.toString(),
                                "-P" + layout.cometParamsFile(),
                                "-N" + layout.root().resolve("outputs/comet/k562_2"),
                                dirs.inputFile("k562_2.mzML").toString()),
                        layout.root(),
                        Map.of(),
                        Instant.parse("2026-10-13T09:00:01.500Z"),
                        // Ends after comet-01 ends: start order and end order differ.
                        Instant.parse("2026-10-13T09:05:30.000Z"),
                        0,
                        ProvenanceStatus.COMPLETED,
                        cometLog2,
                        List.of());
        ToolRecord percolator =
                RunDirectories.tool(
                        "percolator",
                        "3.07.1",
                        percolatorExe,
                        true,
                        Set.of("pin-input", "xml-output"),
                        "percolator",
                        List.of(
                                percolatorExe.toString(),
                                "--seed",
                                "1",
                                "--results-psms",
                                psms.toString(),
                                layout.mergedPinFile().toString()),
                        layout.root(),
                        Map.of("OMP_NUM_THREADS", "3"),
                        Instant.parse("2026-10-13T09:06:00.000Z"),
                        Instant.parse("2026-10-13T09:12:30.125Z"),
                        ok ? 0 : status == ProvenanceStatus.CANCELLED ? 143 : 1,
                        status,
                        percolatorLog,
                        List.of(ADVISORY, WEIGHTS, "the log on stderr was truncated at 1 GiB"));

        Map<String, String> settings =
                RunDirectories.settings(
                        "comet.release", "2026.03.0",
                        "comet.origin.user", "num_threads peptide_mass_tolerance_upper",
                        "comet.origin.workflow-enforced", "output_pepxmlfile output_percolatorfile",
                        "comet.origin.preset", "fragment_bin_tol",
                        "comet.preset.01.id", "high-res",
                        "comet.preset.01.name", "High resolution",
                        "comet.preset.01.origin", "built-in",
                        "comet.preset.01.comet-version", "2026.03.0",
                        "comet.preset.01.schema-version", "1",
                        "comet.preset.01.applied.01", "fragment_bin_tol = 1.0005",
                        "comet.preset.01.applied.02", "num_threads = 8",
                        "percolator.version", "3.07.1",
                        "percolator.seed", "1",
                        "percolator.advisory.xml-final", ADVISORY,
                        "percolator.skipped.01.version", "3.09 managed",
                        "percolator.skipped.01.missing", "xml-output",
                        "percolator.skipped.01.reason", SKIP_REASON,
                        "percolator.skipped.02.version", "3.08 local",
                        "percolator.skipped.02.missing", "xml-output pin-input",
                        "percolator.skipped.02.reason", "it is a local build without XML",
                        "percolator.not-emitted.01.option", "--weights",
                        "percolator.not-emitted.01.reason", NOT_EMITTED_REASON,
                        "percolator.not-emitted.02.option", "--no-analytics",
                        "percolator.not-emitted.02.reason", "this build has no such option",
                        "percolator.weights-warning", WEIGHTS,
                        "workflow.attempt", "1");

        ProvenanceManifest manifest =
                ProvenanceManifest.current(
                        RunDirectories.runRecord(layout, status),
                        RunDirectories.application(),
                        settings,
                        List.of(comet1, comet2, percolator),
                        files);
        RunDirectories.seal(layout, manifest, redactor);
        RunDirectories.events(
                layout,
                Instant.parse("2026-10-13T09:00:05Z"),
                List.of(
                        event(ProvenanceEventType.RUN_STARTED, "run.id", RUN_ID, "attempt", "1"),
                        event(
                                ProvenanceEventType.STAGE_FINISHED,
                                "stage",
                                "run-comet",
                                "state",
                                "succeeded"),
                        event(
                                ProvenanceEventType.RUN_FINISHED,
                                "status",
                                status.wireName(),
                                "run.id",
                                RUN_ID),
                        event(
                                ProvenanceEventType.PDV_LAUNCHED,
                                "run.id",
                                RUN_ID,
                                "tool",
                                "pdv",
                                "tool.version",
                                "2.1.4")));
        return new CompleteRun(
                layout,
                manifest,
                cometExe,
                percolatorExe,
                pepXml1,
                pin2,
                cometLog1,
                percolatorLog,
                settingsFile);
    }
}
