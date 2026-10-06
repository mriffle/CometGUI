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

package org.cometgui.workflow.storage;

import static org.cometgui.workflow.testing.TestPaths.absolute;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.project.ProjectId;
import org.cometgui.domain.run.ArchivedFile;
import org.cometgui.domain.run.AttemptOutcome;
import org.cometgui.domain.run.DatabaseDelivery;
import org.cometgui.domain.run.IndexMode;
import org.cometgui.domain.run.RecordedFingerprint;
import org.cometgui.domain.run.RecordedInput;
import org.cometgui.domain.run.RunDescriptor;
import org.cometgui.domain.run.RunId;
import org.cometgui.domain.run.RunIdentity;

/**
 * Hand-chosen values for the storage tests, and the one run descriptor whose {@code run.json} is
 * typed out by hand in {@link #RUN_JSON}.
 *
 * <p>{@link #RUN_JSON} was typed from the format, not captured from {@link RunJson}: the writer
 * test compares the writer's output with it, and the reader test parses it -- the reader never sees
 * a document the writer produced (Phase 04's lesson).
 */
final class StorageFixtures {

    static final String MD5_1 = "0123456789abcdef0123456789abcdef";
    static final String SHA_1 = "a562f6e6b4c1d0e9f8a7b6c5d4e3f2a1b0c9d8e7f6a5b4c3d2e1f0a9b8c7d6e5";
    static final String MD5_2 = "fedcba9876543210fedcba9876543210";
    static final String SHA_2 = "602aad75e4d3c2b1a0f9e8d7c6b5a4f3e2d1c0b9a8f7e6d5c4b3a2f1e0d9c8b7";
    static final String MD5_F = "11111111111111111111111111111111";
    static final String SHA_F = "2222222222222222222222222222222222222222222222222222222222222222";
    static final String MD5_P = "33333333333333333333333333333333";
    static final String SHA_P = "4444444444444444444444444444444444444444444444444444444444444444";
    static final String FP = "5555555555555555555555555555555555555555555555555555555555555555";
    static final String DG_F = "6666666666666666666666666666666666666666666666666666666666666666";
    static final String DG_S = "7777777777777777777777777777777777777777777777777777777777777777";

    static final Instant CREATED = Instant.parse("2026-08-28T23:15:00.250Z");

    /** The run's identity, as {@link #RUN_JSON} records it. */
    static RunIdentity identity() {
        return new RunIdentity(
                new RunId("run-0001"),
                new ProjectId("project-beta"),
                CREATED,
                "2026.03.0",
                RunIdentity.spectraOf(
                        List.of(
                                new RecordedInput(
                                        absolute("data/in/k562_3.mzML"),
                                        2602922,
                                        Instant.parse("2026-08-01T10:00:00.000Z"),
                                        new FileHashes(MD5_1, SHA_1)),
                                new RecordedInput(
                                        absolute("data/in/k562_4.mzML"),
                                        1195947,
                                        Instant.parse("2026-08-01T10:05:30.500Z"),
                                        new FileHashes(MD5_2, SHA_2)))),
                new RecordedInput(
                        absolute("data/db/sub.fasta"),
                        512000,
                        Instant.parse("2026-07-30T08:00:00.000Z"),
                        new FileHashes(MD5_F, SHA_F)),
                new ArchivedFile("parameters/comet.params", 12345, new FileHashes(MD5_P, SHA_P)),
                IndexMode.NONE,
                DatabaseDelivery.PARAMETER_FILE);
    }

    /** One failed attempt that recorded a fingerprint, and a retry still running. */
    static RunDescriptor descriptor() {
        return RunDescriptor.of(identity())
                .withNewAttempt(Instant.parse("2026-08-28T23:15:01.000Z"))
                .withStepSucceeded(
                        "hash-inputs",
                        new RecordedFingerprint(FP, Map.of("spectrum-files", DG_S, "fasta", DG_F)))
                .withAttemptFinished(
                        AttemptOutcome.FAILED, Instant.parse("2026-08-28T23:16:00.000Z"))
                .withNewAttempt(Instant.parse("2026-08-28T23:17:00.000Z"));
    }

    /** {@link #descriptor()}'s run.json, typed by hand from the format. */
    static final String RUN_JSON = runJson();

    /** A project.json typed by hand. */
    static final String PROJECT_JSON = projectJson();

    /**
     * The text of {@link #RUN_JSON}. A method rather than a constant initialiser, so that the
     * literal is not copied into every class that reads it (SpotBugs {@code
     * HSC_HUGE_SHARED_STRING_CONSTANT}).
     *
     * @return the document
     */
    private static String runJson() {
        return """
            {
              "schemaVersion": 1,
              "runId": "run-0001",
              "projectId": "project-beta",
              "created": "2026-08-28T23:15:00.250Z",
              "cometRelease": "2026.03.0",
              "spectra": [
                {
                  "position": 1,
                  "stageId": "comet-01",
                  "base": "k562_3",
                  "path": "/data/in/k562_3.mzML",
                  "size": 2602922,
                  "modified": "2026-08-01T10:00:00.000Z",
                  "md5": "0123456789abcdef0123456789abcdef",
                  "sha256": "a562f6e6b4c1d0e9f8a7b6c5d4e3f2a1b0c9d8e7f6a5b4c3d2e1f0a9b8c7d6e5"
                },
                {
                  "position": 2,
                  "stageId": "comet-02",
                  "base": "k562_4",
                  "path": "/data/in/k562_4.mzML",
                  "size": 1195947,
                  "modified": "2026-08-01T10:05:30.500Z",
                  "md5": "fedcba9876543210fedcba9876543210",
                  "sha256": "602aad75e4d3c2b1a0f9e8d7c6b5a4f3e2d1c0b9a8f7e6d5c4b3a2f1e0d9c8b7"
                }
              ],
              "fasta": {
                "path": "/data/db/sub.fasta",
                "size": 512000,
                "modified": "2026-07-30T08:00:00.000Z",
                "md5": "11111111111111111111111111111111",
                "sha256": "2222222222222222222222222222222222222222222222222222222222222222"
              },
              "parameters": {
                "path": "parameters/comet.params",
                "size": 12345,
                "md5": "33333333333333333333333333333333",
                "sha256": "4444444444444444444444444444444444444444444444444444444444444444"
              },
              "indexMode": "none",
              "databaseDelivery": "parameter-file",
              "attempts": [
                {
                  "number": 1,
                  "started": "2026-08-28T23:15:01.000Z",
                  "ended": "2026-08-28T23:16:00.000Z",
                  "outcome": "failed",
                  "succeededSteps": {
                    "hash-inputs": {
                      "fingerprint": "55555555555555555555555555555555\
            55555555555555555555555555555555",
                      "inputDigests": {
                        "fasta": "6666666666666666666666666666666666666666666666666666666666666666",
                        "spectrum-files": "77777777777777777777777777777777\
            77777777777777777777777777777777"
                      }
                    }
                  }
                },
                {
                  "number": 2,
                  "started": "2026-08-28T23:17:00.000Z",
                  "ended": null,
                  "outcome": "running",
                  "succeededSteps": {}
                }
              ]
            }
            """;
    }

    private static String projectJson() {
        return """
            {
              "schemaVersion": 1,
              "projectId": "project-beta",
              "created": "2026-08-28T23:00:00.000Z"
            }
            """;
    }

    private StorageFixtures() {}
}
