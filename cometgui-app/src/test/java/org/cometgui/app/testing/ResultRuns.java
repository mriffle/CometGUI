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

package org.cometgui.app.testing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.project.ProjectId;
import org.cometgui.domain.project.ProjectLayout;
import org.cometgui.domain.run.ArchivedFile;
import org.cometgui.domain.run.AttemptOutcome;
import org.cometgui.domain.run.DatabaseDelivery;
import org.cometgui.domain.run.IndexMode;
import org.cometgui.domain.run.RecordedInput;
import org.cometgui.domain.run.RunDescriptor;
import org.cometgui.domain.run.RunId;
import org.cometgui.domain.run.RunIdentity;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.results.filtering.store.TableKind;
import org.cometgui.workflow.steps.RunResultFiles;
import org.cometgui.workflow.storage.ProjectLock;
import org.cometgui.workflow.storage.ProjectStore;
import org.cometgui.workflow.storage.ReservedRun;
import org.cometgui.workflow.storage.RunStore;

/**
 * Constructed runs with Percolator results, for the Results section's GUI tests (Phase 10 unit 8):
 * a real project directory and a real run directory, made through the run store itself -- so its
 * {@code run.json} is one the application reads as it reads its own -- with the result tables put
 * where {@code run-percolator} writes them.
 *
 * <p><strong>Constructed, not produced by Comet or Percolator</strong>: no tool runs. The recorded
 * spectrum and FASTA files need not exist and their hashes are placeholders; the tables are the
 * checked-in copies {@code FIXTURES.txt} (beside this class's resources) describes, held to their
 * SHA-256s, or what a test writes itself.
 */
public final class ResultRuns {

    /** A target PSM table: 15 rows, two at exactly 0.01, one just above, every unknown kind. */
    public static final String PSMS_UNKNOWN_Q = "psms-unknown-q.tsv";

    /** A table of 23 rows used as the target peptide table, with exact 0.01 rows too. */
    public static final String PSMS_SHUFFLED = "psms-shuffled.tsv";

    /** Real Percolator 3.07.1 weights over CometGUI's synthetic PIN: 3 splits, 3 features. */
    public static final String WEIGHTS_3071 = "real-3.07.1-weights.txt";

    private static final Map<String, String> SHA256 =
            Map.of(
                    PSMS_UNKNOWN_Q,
                    "ea58b3b63f9031bf53b5675d117b1e7203137a0ebd2399c9def824334267b6c7",
                    PSMS_SHUFFLED,
                    "3754547ea1ca3ff35b67913a8249b98831e924c0a84029c2a9769fc581a6f5d4",
                    WEIGHTS_3071,
                    "5370f2375d3ea1f13236f6208624c69f81fa8fb190e936e863488cfda4e7a434");

    /** The project identifier a constructed project records. */
    public static final String PROJECT_ID = "cometgui-default";

    private ResultRuns() {}

    /**
     * Copies a checked-in fixture to a file, after checking its SHA-256.
     *
     * @param name one of the fixture names above
     * @param target where to copy it
     * @return {@code target}
     * @throws IOException if it cannot be copied
     */
    public static Path fixture(String name, Path target) throws IOException {
        String pinned = SHA256.get(name);
        if (pinned == null) {
            return fail("no fixture is pinned under the name " + name);
        }
        try (InputStream in = ResultRuns.class.getResourceAsStream("results/" + name)) {
            if (in == null) {
                return fail("the fixture results/" + name + " is not on the test class path");
            }
            Files.createDirectories(Objects.requireNonNull(target.toAbsolutePath().getParent()));
            Files.copy(in, target);
        }
        assertEquals(pinned, sha256(target), "the SHA-256 of the fixture copy " + name);
        return target;
    }

    /**
     * Makes a project directory: {@code project.json} and {@code runs/}.
     *
     * @param directory the project directory, absolute
     * @return its layout
     * @throws IOException if it cannot be made
     */
    public static ProjectLayout project(Path directory) throws IOException {
        ProjectLayout layout = new ProjectLayout(directory);
        new ProjectStore(Clock.systemUTC()).create(layout, new ProjectId(PROJECT_ID));
        return layout;
    }

    /**
     * Makes a run that succeeded, with the given tables and weights under {@code
     * outputs/percolator/}. The project's lock is taken while the run is recorded and released
     * before this returns, so the application can take it.
     *
     * @param project the project
     * @param runId the run's identifier
     * @param created when the run was created, also its directory's timestamp
     * @param spectra the spectrum files the run records, absolute; they need not exist
     * @param tables each table's source file, copied in
     * @param weights the weights file, copied in; empty for none
     * @return the run's layout
     * @throws IOException if it cannot be made
     */
    public static RunLayout run(
            ProjectLayout project,
            String runId,
            Instant created,
            List<Path> spectra,
            Map<TableKind, Path> tables,
            Optional<Path> weights)
            throws IOException {
        Clock clock = Clock.fixed(created, ZoneOffset.UTC);
        RunLayout layout;
        try (ProjectLock lock = ProjectLock.acquire(project, clock)) {
            RunStore store = new RunStore(project, clock, () -> new RunId(runId));
            ReservedRun reserved = store.reserve(lock);
            layout = reserved.layout();
            byte[] parameters =
                    "# the comet.params of a constructed run; no search ran\n"
                            .getBytes(StandardCharsets.UTF_8);
            Files.write(layout.cometParamsFile(), parameters);
            List<RecordedInput> inputs = new ArrayList<>();
            for (Path spectrum : spectra) {
                inputs.add(recorded(spectrum, created));
            }
            RunIdentity identity =
                    new RunIdentity(
                            reserved.runId(),
                            new ProjectId(PROJECT_ID),
                            reserved.created(),
                            "2026.03.0",
                            RunIdentity.spectraOf(inputs),
                            recorded(spectra.get(0).resolveSibling("constructed.fasta"), created),
                            new ArchivedFile(
                                    RunLayout.cometParamsRelativePath(),
                                    parameters.length,
                                    placeholderHashes()),
                            IndexMode.NONE,
                            DatabaseDelivery.PARAMETER_FILE);
            RunDescriptor recorded = store.record(lock, identity);
            store.update(
                    lock,
                    recorded.withNewAttempt(created.plusSeconds(1))
                            .withAttemptFinished(
                                    AttemptOutcome.SUCCEEDED, created.plusSeconds(60)));
        }
        for (Map.Entry<TableKind, Path> table : tables.entrySet()) {
            Path target = RunResultFiles.table(layout, table.getKey());
            Files.createDirectories(RunResultFiles.percolatorOutputDirectory(layout));
            Files.copy(table.getValue(), target);
        }
        if (weights.isPresent()) {
            Path target = RunResultFiles.weights(layout);
            Files.createDirectories(RunResultFiles.percolatorOutputDirectory(layout));
            Files.copy(weights.get(), target);
        }
        return layout;
    }

    /**
     * The SHA-256 of a file, computed here with the JDK alone -- not with the product's hasher.
     *
     * @param file the file
     * @return the digest in lower-case hexadecimal
     * @throws IOException if it cannot be read
     */
    public static String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(file)) {
                byte[] buffer = new byte[1 << 16];
                for (int read = in.read(buffer); read >= 0; read = in.read(buffer)) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("every JDK has SHA-256", impossible);
        }
    }

    private static RecordedInput recorded(Path file, Instant modified) {
        return new RecordedInput(file, 1024, modified, placeholderHashes());
    }

    private static FileHashes placeholderHashes() {
        return new FileHashes("0".repeat(32), "0".repeat(64));
    }
}
