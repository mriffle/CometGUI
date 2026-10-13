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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.cometgui.domain.secrets.SecretRedactor;
import org.cometgui.domain.secrets.SecretRegistry;
import org.cometgui.params.comet.schema.MetadataLoader;
import org.cometgui.provenance.hashing.CachingHashService;
import org.cometgui.provenance.hashing.StreamingHashService;
import org.cometgui.provenance.manifest.ProvenanceManifest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The two exports ({@code P13-3}): rendered from the one loaded object by phase 04's writers,
 * byte-identical to the run's own documents when the redactor is the same, written atomically, and
 * never over a file nobody agreed to replace.
 */
class ProvenanceExportTest {

    /** A credential the run and the session both know; long enough for the registry. */
    private static final String KEY = "ll-submit-7f3a9c2e5b";

    private static final SecretRedactor REDACTOR = SecretRedactor.with(SecretRegistry.of(KEY));

    @TempDir private Path tmp;

    private RunDirectories dirs;

    private CompleteRun run;

    @BeforeEach
    void create() throws IOException {
        dirs = RunDirectories.create(tmp);
        run = CompleteRun.create(dirs, REDACTOR);
    }

    @AfterEach
    void close() throws IOException {
        dirs.close();
    }

    private static ProvenanceReader reader(ExportRenderer renderer) {
        return new ProvenanceReader(
                () -> REDACTOR,
                new CachingHashService(new StreamingHashService()),
                MetadataLoader::loadBundled,
                renderer);
    }

    @Test
    @DisplayName("both exports are rendered from the same object, the one the load read")
    void oneObject() throws IOException {
        List<ProvenanceManifest> json = new ArrayList<>();
        List<ProvenanceManifest> rst = new ArrayList<>();
        ExportRenderer recording =
                new ExportRenderer() {
                    @Override
                    public String json(SecretRedactor redactor, ProvenanceManifest manifest) {
                        json.add(manifest);
                        return ExportRenderer.WRITERS.json(redactor, manifest);
                    }

                    @Override
                    public String rst(SecretRedactor redactor, ProvenanceManifest manifest) {
                        rst.add(manifest);
                        return ExportRenderer.WRITERS.rst(redactor, manifest);
                    }
                };
        LoadedRun loaded = reader(recording).load(run.layout());

        loaded.exportJson(tmp.resolve("a.json"), false);
        loaded.exportRst(tmp.resolve("a.rst"), false);
        loaded.exportJson(tmp.resolve("b.json"), false);

        ProvenanceManifest read = loaded.manifest().orElseThrow();
        assertAll(
                () -> assertEquals(2, json.size()),
                () -> assertEquals(1, rst.size()),
                () -> assertSame(read, json.get(0)),
                () -> assertSame(read, json.get(1)),
                () -> assertSame(read, rst.get(0)),
                () -> assertEquals(run.manifest(), read, "the loaded object is the record"));
    }

    @Test
    @DisplayName("with the run's redactor, the exports are byte-identical to the run's documents")
    void byteIdentical() throws IOException {
        LoadedRun loaded = reader(ExportRenderer.WRITERS).load(run.layout());
        Path json = tmp.resolve("exported.json");
        Path rst = tmp.resolve("exported.rst");

        assertEquals(json, loaded.exportJson(json, false));
        assertEquals(rst, loaded.exportRst(rst, false));

        assertArrayEquals(
                Files.readAllBytes(run.layout().provenanceJsonFile()), Files.readAllBytes(json));
        assertArrayEquals(
                Files.readAllBytes(run.layout().provenanceRstFile()), Files.readAllBytes(rst));
        assertTrue(
                Files.readString(json, StandardCharsets.UTF_8).contains("\"runId\": \"run-0001\""));
    }

    @Test
    @DisplayName("the export is rendered from memory, never copied: it survives the file's removal")
    void renderedNotCopied() throws IOException {
        byte[] sealed = Files.readAllBytes(run.layout().provenanceJsonFile());
        LoadedRun loaded = reader(ExportRenderer.WRITERS).load(run.layout());
        Files.delete(run.layout().provenanceJsonFile());
        Files.delete(run.layout().provenanceRstFile());

        Path json = tmp.resolve("after.json");
        loaded.exportJson(json, false);

        assertArrayEquals(sealed, Files.readAllBytes(json));
    }

    @Test
    @DisplayName("an existing file is replaced only when the caller says so")
    void existingTarget() throws IOException {
        LoadedRun loaded = reader(ExportRenderer.WRITERS).load(run.layout());
        Path target = tmp.resolve("someone-elses.json");
        Files.writeString(target, "another run's record\n", StandardCharsets.UTF_8);

        FileAlreadyExistsException refused =
                assertThrows(
                        FileAlreadyExistsException.class, () -> loaded.exportJson(target, false));
        assertEquals(
                target + ": a file already exists here; confirm replacing it to export over it",
                refused.getMessage());
        assertEquals("another run's record\n", Files.readString(target, StandardCharsets.UTF_8));
        assertThrows(FileAlreadyExistsException.class, () -> loaded.exportRst(target, false));
        assertEquals("another run's record\n", Files.readString(target, StandardCharsets.UTF_8));

        loaded.exportRst(target, true);

        assertArrayEquals(
                Files.readAllBytes(run.layout().provenanceRstFile()), Files.readAllBytes(target));
    }

    @Test
    @DisplayName("the run's own sealed documents are never an export target, even when allowed")
    void ownRecord() throws IOException {
        LoadedRun loaded = reader(ExportRenderer.WRITERS).load(run.layout());
        byte[] json = Files.readAllBytes(run.layout().provenanceJsonFile());
        byte[] rst = Files.readAllBytes(run.layout().provenanceRstFile());

        for (Path own :
                List.of(run.layout().provenanceJsonFile(), run.layout().provenanceRstFile())) {
            FileAlreadyExistsException refused =
                    assertThrows(
                            FileAlreadyExistsException.class, () -> loaded.exportJson(own, true));
            assertEquals(
                    own + ": this is the run's own sealed record, which an export never replaces",
                    refused.getMessage());
            assertThrows(FileAlreadyExistsException.class, () -> loaded.exportRst(own, true));
        }
        assertArrayEquals(json, Files.readAllBytes(run.layout().provenanceJsonFile()));
        assertArrayEquals(rst, Files.readAllBytes(run.layout().provenanceRstFile()));
    }

    @Test
    @DisplayName("a target whose directory does not exist fails and leaves nothing behind")
    void noDirectory() throws IOException {
        LoadedRun loaded = reader(ExportRenderer.WRITERS).load(run.layout());
        Path target = tmp.resolve("missing/dir/out.json");
        assertThrows(IOException.class, () -> loaded.exportJson(target, false));
        assertFalse(Files.exists(target.getParent()));
        assertTrue(loaded.canExport());
    }
}
