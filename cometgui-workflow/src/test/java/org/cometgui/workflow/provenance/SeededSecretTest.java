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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.cometgui.domain.run.AttemptOutcome;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.domain.secrets.SecretRedactor;
import org.cometgui.domain.secrets.SecretRegistry;
import org.cometgui.provenance.events.ProvenanceEventType;
import org.cometgui.provenance.hashing.CachingHashService;
import org.cometgui.provenance.hashing.StreamingHashService;
import org.cometgui.provenance.manifest.FileDirection;
import org.cometgui.provenance.manifest.ProvenanceManifest;
import org.cometgui.provenance.manifest.ProvenanceStatus;
import org.cometgui.provenance.manifest.ToolRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A secret the run did not know when it wrote its record, and the session knows now, is absent from
 * both exports -- searched as bytes -- and from every display string the read model produces: every
 * text of the view, the run list and a verification, found by walking the records rather than by
 * naming the fields a reviewer happened to think of.
 */
class SeededSecretTest {

    /** Registered with the session only: the run's own redactor never saw it. */
    private static final String SECRET = "Zq7Kx-limelight-91xW";

    @TempDir private Path tmp;

    private RunDirectories dirs;

    @BeforeEach
    void create() throws IOException {
        dirs = RunDirectories.create(tmp);
    }

    @AfterEach
    void close() throws IOException {
        dirs.close();
    }

    @Test
    @DisplayName("the seeded secret is in the record on disk and in no export and no display text")
    void absentEverywhere() throws IOException, IllegalAccessException, InvocationTargetException {
        RunLayout layout =
                dirs.recordedRun(
                        "run-0003",
                        RunDirectories.CREATED,
                        RunDirectories.replaceLine(
                                RunDirectories.cometParams(),
                                "database_name",
                                "database_name = /data/" + SECRET + "/human.fasta"));
        dirs.attempt(layout, RunDirectories.STARTED, AttemptOutcome.FAILED, RunDirectories.ENDED);
        Path exe =
                Files.createDirectories(dirs.root().resolve("tools/" + SECRET))
                        .resolve("comet.exe");
        Files.writeString(exe, RunDirectories.COMET_EXE_CONTENT, StandardCharsets.US_ASCII);
        Path output = RunDirectories.write(layout, "outputs/comet/" + SECRET + ".pin", "pin\n");
        Path log = RunDirectories.write(layout, "logs/comet-01.log", "log " + SECRET + "\n");
        ToolRecord comet =
                RunDirectories.tool(
                        "comet",
                        "2026.03.0",
                        exe,
                        false,
                        Set.of(),
                        "comet-01",
                        List.of(
                                exe.toString(),
                                "--api-key",
                                SECRET,
                                "-Psecret-at-" + SECRET,
                                dirs.inputFile("k562_1.mzML").toString()),
                        layout.root(),
                        Map.of("LIMELIGHT_KEY", SECRET, "WORKDIR", "/scratch/" + SECRET),
                        Instant.parse("2026-10-13T09:00:02Z"),
                        Instant.parse("2026-10-13T09:00:03Z"),
                        1,
                        ProvenanceStatus.FAILED,
                        log,
                        List.of("a warning quoting " + SECRET));
        ProvenanceManifest manifest =
                ProvenanceManifest.current(
                        RunDirectories.runRecord(layout, ProvenanceStatus.FAILED),
                        RunDirectories.application(),
                        RunDirectories.settings(
                                "comet.release",
                                "2026.03.0",
                                "comet.origin.user",
                                "database_name",
                                "comet.preset.01.id",
                                "p-" + SECRET,
                                "comet.preset.01.name",
                                "named " + SECRET,
                                "comet.preset.01.origin",
                                "user",
                                "comet.preset.01.comet-version",
                                "2026.03.0",
                                "comet.preset.01.schema-version",
                                "1",
                                "comet.preset.01.applied.01",
                                "database_name = /x/" + SECRET + "/db.fasta",
                                "percolator.advisory.leak",
                                "advisory with " + SECRET,
                                "percolator.weights-warning",
                                "weights " + SECRET),
                        List.of(comet),
                        List.of(
                                RunDirectories.fileRecord(
                                        FileDirection.OUTPUT,
                                        "comet-params",
                                        layout.cometParamsFile(),
                                        ProvenanceStatus.COMPLETED),
                                RunDirectories.fileRecord(
                                        FileDirection.OUTPUT,
                                        "pin",
                                        output,
                                        ProvenanceStatus.PARTIAL)));
        // The run's own redactor knew no secret, so the record on disk carries it in the clear.
        RunDirectories.seal(layout, manifest, SecretRedactor.patternsOnly());
        RunDirectories.events(
                layout,
                Instant.parse("2026-10-13T09:00:04Z"),
                List.of(
                        event(
                                ProvenanceEventType.STAGE_FINISHED,
                                "stage",
                                "run-comet",
                                "message",
                                "failed reading " + SECRET),
                        event(ProvenanceEventType.WARNING_RAISED, "message", "seen " + SECRET),
                        event(
                                ProvenanceEventType.LIMELIGHT_UPLOAD_FAILED,
                                "upload.failure",
                                "no-answer",
                                "message",
                                "key " + SECRET + " refused")));
        Files.writeString(
                layout.cometParamsFile(),
                "# changed for " + SECRET + "\n",
                StandardCharsets.UTF_8,
                StandardOpenOption.APPEND);
        // Control: the secret really is in every file the read model reads.
        for (Path file :
                List.of(
                        layout.provenanceJsonFile(), layout.provenanceRstFile(),
                        layout.eventLogFile(), layout.cometParamsFile())) {
            assertTrue(
                    Files.readString(file, StandardCharsets.UTF_8).contains(SECRET),
                    file.toString());
        }

        SecretRedactor session = SecretRedactor.with(SecretRegistry.of(SECRET));
        ProvenanceReader reader =
                new ProvenanceReader(
                        () -> session, new CachingHashService(new StreamingHashService()));
        LoadedRun loaded = reader.load(layout);
        Path json = loaded.exportJson(tmp.resolve("export.json"), false);
        Path rst = loaded.exportRst(tmp.resolve("export.rst"), false);
        DiskVerification verification = loaded.verifyAgainstDisk(() -> false);
        List<RunListing> listing = reader.list(dirs.project(), Set.of());

        byte[] needle = SECRET.getBytes(StandardCharsets.UTF_8);
        for (Path export : List.of(json, rst)) {
            byte[] bytes = Files.readAllBytes(export);
            assertFalse(contains(bytes, needle), export + " carries the secret");
            assertTrue(
                    new String(bytes, StandardCharsets.UTF_8).contains("[REDACTED]"),
                    export + " shows where it was");
        }
        List<String> texts = new ArrayList<>();
        collect(loaded.view(), texts);
        collect(verification, texts);
        collect(listing, texts);
        loaded.recordProblem().ifPresent(texts::add);
        assertTrue(texts.size() > 200, "the walk found " + texts.size() + " texts");
        List<String> leaks = new ArrayList<>();
        for (String text : texts) {
            if (contains(text.getBytes(StandardCharsets.UTF_8), needle)) {
                leaks.add(text);
            }
        }
        assertEquals(List.of(), leaks);
        // And the redaction is visible where the secret was, in each kind of text.
        ProvenanceView view = loaded.view();
        assertTrue(view.tools().get(0).command().contains("\"--api-key\", \"[REDACTED]\""));
        assertTrue(view.tools().get(0).path().contains("[REDACTED]"));
        assertTrue(
                view.timeline()
                        .executions()
                        .get(0)
                        .environment()
                        .contains("LIMELIGHT_KEY=[REDACTED]"));
        assertTrue(
                view.parameters()
                        .cometParams()
                        .orElseThrow()
                        .text()
                        .orElseThrow()
                        .contains("database_name = /data/[REDACTED]/human.fasta"));
        assertEquals(
                "/data/[REDACTED]/human.fasta",
                LoadedRunViewTest.row(view.parameters(), "database_name").value());
    }

    /** Every string reachable from a record, list or optional, walking record components. */
    static void collect(Object value, List<String> texts)
            throws IllegalAccessException, InvocationTargetException {
        if (value == null || value instanceof Path) {
            // A Path is a location to act on and never a display string; see ProvenanceView.
            return;
        }
        if (value instanceof String text) {
            texts.add(text);
        } else if (value instanceof Optional<?> optional) {
            if (optional.isPresent()) {
                collect(optional.get(), texts);
            }
        } else if (value instanceof Collection<?> collection) {
            for (Object element : collection) {
                collect(element, texts);
            }
        } else if (value instanceof Enum<?> constant) {
            texts.add(constant.name());
        } else if (value.getClass().isRecord()) {
            for (RecordComponent component : value.getClass().getRecordComponents()) {
                collect(component.getAccessor().invoke(value), texts);
            }
        }
    }

    static boolean contains(byte[] haystack, byte[] needle) {
        outer:
        for (int start = 0; start + needle.length <= haystack.length; start++) {
            for (int index = 0; index < needle.length; index++) {
                if (haystack[start + index] != needle[index]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }
}
