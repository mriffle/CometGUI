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

package org.cometgui.app.config;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.cometgui.app.testing.ScriptedChooser;
import org.cometgui.domain.build.BuildIdentity;
import org.cometgui.domain.log.BoundedMessageLog;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.ports.ProcessRunner;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.domain.run.RunId;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.domain.secrets.SecretRedactor;
import org.cometgui.domain.secrets.SessionSecrets;
import org.cometgui.provenance.manifest.ApplicationRecord;
import org.cometgui.provenance.manifest.ExecutionRecord;
import org.cometgui.provenance.manifest.FileDirection;
import org.cometgui.provenance.manifest.FileRecord;
import org.cometgui.provenance.manifest.LogRecord;
import org.cometgui.provenance.manifest.ManifestWriter;
import org.cometgui.provenance.manifest.ProvenanceManifest;
import org.cometgui.provenance.manifest.ProvenanceStatus;
import org.cometgui.provenance.manifest.RunRecord;
import org.cometgui.provenance.manifest.ToolRecord;
import org.cometgui.ui.viewmodel.provenance.ProvenanceViewModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The Provenance section redacts with the SESSION's redactor, not the patterns alone (R-SEC-03,
 * P12-21): composed exactly as the application composes it -- {@link RunWiring#provenance} over a
 * {@link SessionSecrets} -- and shown through the section's own view-model.
 *
 * <p>The record on disk holds a value the session knows is secret but the patterns do not recognise
 * -- written before the key was admitted, or by another session -- in an argument of a command, a
 * setting and a file's path. Before the session admits it, it is visible in what the section shows,
 * copies and exports (the control: so the test cannot pass by the value never reaching the screen).
 * Once admitted, it is in none of them: not in a single displayed text, not in Copy command, not in
 * either exported file's bytes.
 */
class ProvenanceSessionRedactionTest {

    /**
     * Known to the session only: no keyword, flag, URL or key shape the patterns recognise. The
     * test proves the patterns leave it alone.
     */
    private static final String SEED = "CGSEED-SESSIONONLY-7d41c2e9";

    private static final FileHashes HASHES =
            new FileHashes(
                    "900150983cd24fb0d6963f7d28e17f72",
                    "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");

    @TempDir private Path work;

    private SessionSecrets secrets;

    private ScriptedChooser chooser;

    private ProvenanceViewModel section;

    private Path spectrum;

    private String setting;

    private List<String> argv;

    @BeforeEach
    void build() throws IOException {
        Path project = work.resolve("project");
        RunLayout run =
                new RunLayout(Files.createDirectories(project.resolve("runs/20261014T080000Z-r1")));
        Files.createDirectories(run.root().resolve("provenance"));
        spectrum = work.resolve("data").resolve(SEED).resolve("k562.mzML");
        setting = "a lab label " + SEED + " for this search";
        argv = List.of(work.resolve("bin/comet").toString(), "--label", SEED, spectrum.toString());
        ProvenanceManifest manifest =
                new ProvenanceManifest(
                        1,
                        new RunRecord(
                                new RunId("r1"),
                                "cometgui-default",
                                ProvenanceStatus.COMPLETED,
                                Instant.parse("2026-10-14T08:00:00Z"),
                                Optional.of(Instant.parse("2026-10-14T08:01:00Z"))),
                        new ApplicationRecord(
                                "0.1.0",
                                "abc1234",
                                "Linux",
                                "6.8",
                                "amd64",
                                "25.0.1",
                                Locale.ROOT,
                                Locale.ROOT,
                                ZoneId.of("UTC")),
                        Map.of("percolator.lab-label", setting),
                        List.of(
                                new ToolRecord(
                                        "comet",
                                        "2026.03.0",
                                        Optional.empty(),
                                        work.resolve("bin/comet"),
                                        HASHES,
                                        false,
                                        Optional.empty(),
                                        Set.of(),
                                        Optional.of("comet-01"),
                                        new ExecutionRecord(
                                                new ToolCommand(argv, run.root(), Map.of()),
                                                Instant.parse("2026-10-14T08:00:01Z"),
                                                Instant.parse("2026-10-14T08:00:59Z"),
                                                0,
                                                Optional.of(
                                                        new LogRecord(
                                                                run.root().resolve("logs/c.log"),
                                                                HASHES)),
                                                Optional.empty(),
                                                ProvenanceStatus.COMPLETED),
                                        List.of())),
                        List.of(
                                new FileRecord(
                                        FileDirection.INPUT,
                                        "spectrum",
                                        spectrum,
                                        3,
                                        Instant.parse("2026-10-01T00:00:00Z"),
                                        HASHES,
                                        ProvenanceStatus.COMPLETED)));
        // written as a run of a session that did not know the value: the patterns alone
        ManifestWriter.redactingWith(SecretRedactor.patternsOnly())
                .writeTo(run.provenanceJsonFile(), manifest);

        ApplicationServices real = ApplicationServices.forThisHost();
        ProcessRunner nothing =
                (command, listener) -> {
                    throw new IOException("this test starts no process");
                };
        ApplicationServices services =
                new ApplicationServices(
                        real.clock(),
                        real.environment(),
                        real.fileSystem(),
                        real.runIds(),
                        real.glibcVersions(),
                        nothing,
                        null,
                        null);
        ProjectSession session = new ProjectSession(project, Clock.systemUTC(), real.runIds());
        SessionEngine engine =
                RunWiring.port(
                        services,
                        new BoundedMessageLog(),
                        Optional.empty(),
                        "no tools in this test",
                        session,
                        BuildIdentity.of("0.0.0-test", "unknown", Instant.EPOCH));
        secrets = new SessionSecrets();
        chooser = new ScriptedChooser();
        section =
                new ProvenanceViewModel(
                        RunWiring.provenance(
                                services, session, RunWiring.hasher(), engine, secrets),
                        chooser,
                        Runnable::run,
                        Runnable::run);
    }

    /** Opens the run again -- the redactor is asked afresh at every load -- and returns it all. */
    private List<String> shownAndCopied() {
        section.refresh();
        assertTrue(section.choose(0), "the run is listed");
        assertTrue(section.recordShownProperty().get(), section.recordTextProperty().get());
        List<String> texts = new ArrayList<>();
        texts.addAll(section.runsProperty().get());
        texts.add(section.recordTextProperty().get());
        texts.add(section.summaryTextProperty().get());
        texts.addAll(section.toolsProperty().get());
        texts.addAll(section.filesProperty().get());
        texts.add(section.cometParamsTextProperty().get());
        texts.add(section.cometParamsStatusProperty().get());
        texts.add(section.percolatorSettingsTextProperty().get());
        texts.addAll(section.parametersProperty().get());
        texts.add(section.presetsTextProperty().get());
        texts.add(section.parameterNotesProperty().get());
        texts.addAll(section.executionsProperty().get());
        texts.addAll(section.eventsProperty().get());
        texts.addAll(section.logsProperty().get());
        texts.addAll(section.warningsProperty().get());
        for (int index = 0; index < section.executionsProperty().get().size(); index++) {
            texts.add("copied: " + section.copyCommand(index).orElseThrow());
        }
        assertEquals(1, section.executionsProperty().get().size(), "one execution to copy");
        return texts;
    }

    /** Exports both documents and returns their bytes, JSON first. */
    private List<byte[]> exported(String name) throws IOException {
        Path json = work.resolve(name + ".json");
        Path rst = work.resolve(name + ".rst");
        chooser.exportTo(json).exportTo(rst);
        assertTrue(section.exportJson());
        assertEquals(
                "Exported provenance JSON to " + json + ".", section.exportStatusProperty().get());
        assertTrue(section.exportRst());
        assertEquals(
                "Exported provenance RST to " + rst + ".", section.exportStatusProperty().get());
        return List.of(Files.readAllBytes(json), Files.readAllBytes(rst));
    }

    private static List<String> holding(List<String> texts) {
        return texts.stream().filter(text -> text.contains(SEED)).toList();
    }

    private static boolean contains(byte[] document, String value) {
        return new String(document, StandardCharsets.ISO_8859_1)
                .contains(
                        new String(
                                value.getBytes(StandardCharsets.UTF_8),
                                StandardCharsets.ISO_8859_1));
    }

    @Test
    @DisplayName(
            "a value only the session knows is secret: visible before it is admitted, and in no"
                    + " displayed text, copy or exported byte after")
    void theSessionsRedactor() throws IOException {
        SecretRedactor patterns = SecretRedactor.patternsOnly();
        assertAll(
                "the patterns alone leave every carrier as it is, so only the session can hide it",
                () -> assertEquals(setting, patterns.redactText(setting)),
                () -> assertEquals(spectrum.toString(), patterns.redactText(spectrum.toString())),
                () -> assertEquals(argv, patterns.redactArgv(argv)));

        List<String> before = shownAndCopied();
        List<byte[]> exportedBefore = exported("before");
        assertAll(
                "the control: before the session admits it, the value is on screen and exported",
                () ->
                        assertTrue(
                                section.filesProperty().get().stream()
                                        .anyMatch(row -> row.contains(SEED)),
                                "the file's path"),
                () ->
                        assertTrue(
                                section.percolatorSettingsTextProperty().get().contains(SEED),
                                "the setting"),
                () ->
                        assertTrue(
                                before.stream()
                                        .anyMatch(
                                                text ->
                                                        text.startsWith("copied: ")
                                                                && text.contains(SEED)),
                                "Copy command"),
                () -> assertTrue(contains(exportedBefore.get(0), SEED), "the exported JSON"),
                () -> assertTrue(contains(exportedBefore.get(1), SEED), "the exported RST"));

        secrets.admit(SEED);
        List<String> after = shownAndCopied();
        List<byte[]> exportedAfter = exported("after");
        assertAll(
                "once admitted, nowhere",
                () -> assertEquals(List.of(), holding(after), "displayed or copied"),
                () -> assertFalse(contains(exportedAfter.get(0), SEED), "the exported JSON"),
                () -> assertFalse(contains(exportedAfter.get(1), SEED), "the exported RST"),
                () ->
                        assertTrue(
                                section.percolatorSettingsTextProperty()
                                        .get()
                                        .contains(SecretRedactor.REDACTION_MARKER),
                                "the setting is still shown, redacted -- not dropped"),
                () ->
                        assertTrue(
                                contains(exportedAfter.get(0), SecretRedactor.REDACTION_MARKER),
                                "the JSON carries the marker where the value was"),
                () ->
                        assertTrue(
                                Files.readString(
                                                work.resolve(
                                                        "project/runs/20261014T080000Z-r1"
                                                                + "/provenance/provenance.json"),
                                                StandardCharsets.UTF_8)
                                        .contains(SEED),
                                "the record on disk is untouched: only what is shown and exported"
                                        + " is redacted"));
    }
}
