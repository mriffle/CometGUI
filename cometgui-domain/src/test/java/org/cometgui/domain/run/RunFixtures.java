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

package org.cometgui.domain.run;

import static org.cometgui.domain.testing.TestPaths.absolute;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.project.ProjectId;

/** Hand-written values for the run model's tests. */
final class RunFixtures {

    static final Instant CREATED = Instant.parse("2026-08-28T23:15:00.250Z");

    static final FileHashes HASHES_A =
            new FileHashes("0123456789abcdef0123456789abcdef", "a562f6e6" + "0".repeat(56));

    static final FileHashes HASHES_B =
            new FileHashes("fedcba9876543210fedcba9876543210", "602aad75" + "1".repeat(56));

    static final FileHashes HASHES_P =
            new FileHashes("00000000000000000000000000000001", "2".repeat(64));

    static final String DIGEST_1 = "1".repeat(64);

    static final String DIGEST_2 = "2".repeat(64);

    static final String DIGEST_3 = "3".repeat(64);

    private RunFixtures() {}

    static RecordedInput input(String path, FileHashes hashes) {
        return new RecordedInput(
                absolute(path), 1000, Instant.parse("2026-08-01T10:00:00Z"), hashes);
    }

    static ArchivedFile params() {
        return new ArchivedFile("parameters/comet.params", 12345, HASHES_P);
    }

    static RunIdentity identity() {
        return new RunIdentity(
                new RunId("run-0001"),
                new ProjectId("project-beta"),
                CREATED,
                "2026.03.0",
                RunIdentity.spectraOf(
                        List.of(
                                input("data/in/k562_3.mzML", HASHES_A),
                                input("data/in/k562_4.mzML", HASHES_B))),
                input("data/db/sub.fasta", HASHES_P),
                params(),
                IndexMode.NONE,
                DatabaseDelivery.PARAMETER_FILE);
    }

    static RunIdentity copy(
            RunIdentity g,
            RunId runId,
            ProjectId projectId,
            Instant created,
            String release,
            List<SpectrumInput> spectra,
            RecordedInput fasta,
            ArchivedFile parameters,
            IndexMode mode,
            DatabaseDelivery delivery) {
        return new RunIdentity(
                runId == null ? g.runId() : runId,
                projectId == null ? g.projectId() : projectId,
                created == null ? g.created() : created,
                release == null ? g.cometRelease() : release,
                spectra == null ? g.spectra() : spectra,
                fasta == null ? g.fasta() : fasta,
                parameters == null ? g.parameters() : parameters,
                mode == null ? g.indexMode() : mode,
                delivery == null ? g.databaseDelivery() : delivery);
    }

    static RunIdentity withCreated(RunIdentity g, Instant created) {
        return new RunIdentity(
                g.runId(),
                g.projectId(),
                created,
                g.cometRelease(),
                g.spectra(),
                g.fasta(),
                g.parameters(),
                g.indexMode(),
                g.databaseDelivery());
    }

    static RunIdentity withRelease(RunIdentity g, String release) {
        return new RunIdentity(
                g.runId(),
                g.projectId(),
                g.created(),
                release,
                g.spectra(),
                g.fasta(),
                g.parameters(),
                g.indexMode(),
                g.databaseDelivery());
    }

    static RecordedFingerprint fingerprint(String value) {
        return new RecordedFingerprint(value, Map.of("comet-parameters", DIGEST_3));
    }
}
