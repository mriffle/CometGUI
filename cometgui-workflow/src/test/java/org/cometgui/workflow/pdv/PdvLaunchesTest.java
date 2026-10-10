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

package org.cometgui.workflow.pdv;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.cometgui.domain.ports.ProcessListener;
import org.cometgui.domain.ports.ProcessRunner;
import org.cometgui.domain.ports.RunningProcess;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.domain.run.RunId;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.domain.secrets.SecretRedactor;
import org.cometgui.domain.tools.HostArchitecture;
import org.cometgui.domain.tools.HostOperatingSystem;
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.provenance.events.ProvenanceEvent;
import org.cometgui.provenance.hashing.StreamingHashService;
import org.cometgui.workflow.pdv.PdvFixtures.Behaviour;
import org.cometgui.workflow.pdv.PdvFixtures.FakePdv;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The one PDV launch path (gate item 4, design decision P11-4): every start through it is preceded
 * by its {@code pdv.launched} event in the run's event log, carrying the JAR's checksums and the
 * version its manifest reports -- both computed here independently -- and nothing is started when
 * the record cannot be made.
 */
class PdvLaunchesTest {

    private static final HostPlatform LINUX =
            new HostPlatform(HostOperatingSystem.LINUX, HostArchitecture.X86_64);

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-10-10T09:30:00.250Z"), ZoneOffset.UTC);

    @TempDir private Path directory;

    private Path jar;

    private RunLayout run;

    private PdvInstallation pdv;

    @BeforeEach
    void build() throws IOException {
        jar = PdvFixtures.jar(directory.resolve("tools/PDV-2.7.0/PDV-2.7.0.jar"), "PDV", "2.7.0");
        run = PdvFixtures.run(directory.resolve("project"), PdvFixtures.RUN_ID);
        pdv = new PdvInstallation(ToolVersion.parse("2.7.0"), jar);
    }

    private PdvLaunches launches(ProcessRunner processes) {
        return new PdvLaunches(
                processes, new StreamingHashService(), SecretRedactor.patternsOnly(), CLOCK, LINUX);
    }

    private ToolCommand command(String... arguments) {
        List<String> argv = new ArrayList<>();
        argv.add("/opt/java/bin/java");
        argv.add("-jar");
        argv.add(jar.toString());
        argv.addAll(List.of(arguments));
        Map<String, String> environment = new LinkedHashMap<>();
        environment.put("LANG", "C.UTF-8");
        environment.put("DISPLAY", ":7");
        return new ToolCommand(argv, directory, environment);
    }

    private static final ProcessListener NOBODY =
            new ProcessListener() {
                @Override
                public void onStandardOutput(String line) {
                    // not read
                }

                @Override
                public void onStandardError(String line) {
                    // not read
                }

                @Override
                public void onExit(int exitCode) {
                    // not read
                }
            };

    @Test
    @DisplayName(
            "a launch is recorded before PDV starts: the JAR's independent SHA-256 and MD5, its"
                    + " manifest's version, the argument array, the kind and what it was for")
    void theRecordPrecedesTheStart() throws IOException, InterruptedException {
        FakePdv processes = new FakePdv(run, Behaviour.EXITS_NON_ZERO, "");
        ToolCommand command = command("-r", "/data/a.pep.xml", "-ft", "png");

        RunningProcess process =
                launches(processes)
                        .forRun(
                                run,
                                new RunId(PdvFixtures.RUN_ID),
                                pdv,
                                PdvLaunchKind.CLI_FIGURE,
                                Map.of("psm-id", "/r/outputs/comet/a_101_2_1"))
                        .start(command, NOBODY);

        assertEquals(1, process.waitForExit(), "the process service's own handle is returned");

        List<ProvenanceEvent> launched = PdvFixtures.launches(run);
        assertEquals(1, launched.size(), "one launch, one event");
        Map<String, String> payload = launched.get(0).payload();
        String sha256 = PdvFixtures.digest(jar, "SHA-256");
        String md5 = PdvFixtures.digest(jar, "MD5");
        assertAll(
                () -> assertEquals(List.of(1), processes.eventsAtStart(), "recorded first"),
                () -> assertEquals(List.of(true), processes.lastEventWasThisCommand()),
                () -> assertEquals(sha256, payload.get("pdv.jar.sha256")),
                () -> assertEquals(md5, payload.get("pdv.jar.md5")),
                () ->
                        assertEquals(
                                PdvFixtures.manifestVersion(jar),
                                payload.get("tool.version"),
                                "the version the JAR's own manifest reports"),
                () -> assertEquals("2.7.0", payload.get("tool.version")),
                () -> assertEquals(jar.toString(), payload.get("pdv.jar.path")),
                () -> assertEquals("pdv", payload.get("tool")),
                () -> assertEquals("cli-figure", payload.get("pdv.launch")),
                () -> assertEquals(PdvFixtures.RUN_ID, payload.get("run.id")),
                () -> assertEquals(command.argv(), PdvFixtures.argv(launched.get(0))),
                () -> assertEquals("7", payload.get("argv.count")),
                () -> assertEquals(directory.toString(), payload.get("process.working-directory")),
                () -> assertEquals("DISPLAY=:7\nLANG=C.UTF-8", payload.get("process.environment")),
                () -> assertEquals("/r/outputs/comet/a_101_2_1", payload.get("launch.psm-id")),
                () ->
                        assertEquals(
                                Instant.parse("2026-10-10T09:30:00.250Z"),
                                launched.get(0).timestamp()));
        String line = Files.readAllLines(run.eventLogFile()).get(0);
        assertTrue(line.contains("\"type\":\"pdv.launched\""), line);
        assertTrue(line.contains("\"pdv.jar.sha256\":\"" + sha256 + "\""), line);
    }

    @Test
    @DisplayName("a launch that cannot be started still left its record")
    void aFailedStartLeavesItsRecord() throws IOException {
        FakePdv processes = new FakePdv(run, Behaviour.CANNOT_START, "");
        ProcessRunner recorded =
                launches(processes)
                        .forRun(
                                run,
                                new RunId(PdvFixtures.RUN_ID),
                                pdv,
                                PdvLaunchKind.DENOVO_GUI,
                                Map.of());
        ToolCommand command = command("denovo-gui", "--port", "40001");

        IOException failed = assertThrows(IOException.class, () -> recorded.start(command, NOBODY));

        assertEquals("the stand-in for PDV cannot be started", failed.getMessage());
        List<ProvenanceEvent> launched = PdvFixtures.launches(run);
        assertEquals(1, launched.size());
        assertEquals("denovo-gui", launched.get(0).payload().get("pdv.launch"));
        assertEquals(command.argv(), PdvFixtures.argv(launched.get(0)));
    }

    @Test
    @DisplayName("every start is preceded by its own event: three starts, three events, in order")
    void everyStartHasItsOwnEvent() throws IOException {
        FakePdv processes = new FakePdv(run, Behaviour.EXITS_NON_ZERO, "");
        ProcessRunner recorded =
                launches(processes)
                        .forRun(
                                run,
                                new RunId(PdvFixtures.RUN_ID),
                                pdv,
                                PdvLaunchKind.CLI_FIGURE,
                                Map.of());

        recorded.start(command("-i", "one"), NOBODY);
        recorded.start(command("-i", "two"), NOBODY);
        recorded.start(command("-i", "three"), NOBODY);

        List<ProvenanceEvent> launched = PdvFixtures.launches(run);
        assertAll(
                () -> assertEquals(List.of(1, 2, 3), processes.eventsAtStart()),
                () -> assertEquals(List.of(true, true, true), processes.lastEventWasThisCommand()),
                () -> assertEquals(3, launched.size()),
                () -> assertEquals("three", PdvFixtures.argv(launched.get(2)).get(4)),
                () -> assertEquals(List.of(1L, 2L, 3L), sequences(launched)));
    }

    @Test
    @DisplayName("a command that does not launch this PDV JAR is refused: no event, no start")
    void anotherProgramIsRefused() throws IOException {
        FakePdv processes = new FakePdv(run, Behaviour.EXITS_NON_ZERO, "");
        ProcessRunner recorded =
                launches(processes)
                        .forRun(
                                run,
                                new RunId(PdvFixtures.RUN_ID),
                                pdv,
                                PdvLaunchKind.CLI_FIGURE,
                                Map.of());
        ToolCommand other =
                new ToolCommand(List.of("/usr/bin/comet", "-Pcomet.params"), directory, Map.of());

        IOException refused = assertThrows(IOException.class, () -> recorded.start(other, NOBODY));

        assertEquals(
                "this runner launches PDV from "
                        + jar
                        + " and nothing else, and was asked to start: "
                        + other.displayString(),
                refused.getMessage());
        assertEquals(List.of(), processes.started());
        assertEquals(List.of(), PdvFixtures.launches(run));
    }

    @Test
    @DisplayName("a JAR whose manifest reports another version is refused: no event, no start")
    void aVersionMismatchIsRefused() throws IOException {
        Path other = PdvFixtures.jar(directory.resolve("other/PDV.jar"), "PDV", "2.8.0");
        FakePdv processes = new FakePdv(run, Behaviour.EXITS_NON_ZERO, "");
        ProcessRunner recorded =
                launches(processes)
                        .forRun(
                                run,
                                new RunId(PdvFixtures.RUN_ID),
                                new PdvInstallation(ToolVersion.parse("2.7.0"), other),
                                PdvLaunchKind.CLI_FIGURE,
                                Map.of());
        ToolCommand command =
                new ToolCommand(List.of("java", "-jar", other.toString()), directory, Map.of());

        IOException refused =
                assertThrows(IOException.class, () -> recorded.start(command, NOBODY));

        assertEquals(
                other
                        + " reports PDV 2.8.0 in its manifest, but the Tool Manager offered it as"
                        + " PDV 2.7.0; nothing was launched",
                refused.getMessage());
        assertEquals(List.of(), processes.started());
        assertEquals(List.of(), PdvFixtures.launches(run));
    }

    @Test
    @DisplayName("a JAR that is not PDV is refused: no event, no start")
    void notPdvIsRefused() throws IOException {
        Path other = PdvFixtures.jar(directory.resolve("other/x.jar"), "Something", "2.7.0");
        FakePdv processes = new FakePdv(run, Behaviour.EXITS_NON_ZERO, "");
        ProcessRunner recorded =
                launches(processes)
                        .forRun(
                                run,
                                new RunId(PdvFixtures.RUN_ID),
                                new PdvInstallation(ToolVersion.parse("2.7.0"), other),
                                PdvLaunchKind.CLI_FIGURE,
                                Map.of());
        ToolCommand command =
                new ToolCommand(List.of("java", "-jar", other.toString()), directory, Map.of());

        IOException refused =
                assertThrows(IOException.class, () -> recorded.start(command, NOBODY));

        assertTrue(refused.getMessage().contains(" is not PDV"), refused.getMessage());
        assertEquals(List.of(), processes.started());
        assertFalse(Files.exists(run.eventLogFile()));
    }

    @Test
    @DisplayName("when the event cannot be written nothing is started")
    void noRecordNoLaunch() throws IOException {
        RunLayout broken = new RunLayout(directory.resolve("project/runs/no-provenance-dir"));
        Files.createDirectories(broken.root());
        FakePdv processes = new FakePdv(broken, Behaviour.EXITS_NON_ZERO, "");
        ProcessRunner recorded =
                launches(processes)
                        .forRun(
                                broken,
                                new RunId("no-provenance-dir"),
                                pdv,
                                PdvLaunchKind.CLI_FIGURE,
                                Map.of());

        assertThrows(IOException.class, () -> recorded.start(command("-x"), NOBODY));

        assertEquals(List.of(), processes.started());
        assertFalse(Files.exists(broken.eventLogFile()));
    }

    @Test
    @DisplayName("a credential in the argument array or the environment is redacted in the record")
    void secretsAreRedacted() throws IOException {
        FakePdv processes = new FakePdv(run, Behaviour.EXITS_NON_ZERO, "");
        ToolCommand command =
                new ToolCommand(
                        List.of("java", "-jar", jar.toString(), "--password", "hunter2-hunter2"),
                        directory,
                        Map.of("API_TOKEN", "abcdefghijklmnop"));

        launches(processes)
                .forRun(run, new RunId(PdvFixtures.RUN_ID), pdv, PdvLaunchKind.CLI_FIGURE, Map.of())
                .start(command, NOBODY);

        Map<String, String> payload = PdvFixtures.launches(run).get(0).payload();
        assertAll(
                () -> assertEquals("--password", payload.get("argv.3")),
                () -> assertEquals(SecretRedactor.REDACTION_MARKER, payload.get("argv.4")),
                () ->
                        assertEquals(
                                "API_TOKEN=" + SecretRedactor.REDACTION_MARKER,
                                payload.get("process.environment")),
                () ->
                        assertFalse(
                                Files.readString(run.eventLogFile()).contains("hunter2"),
                                "the credential is nowhere in the log"));
        assertEquals(
                command.argv(),
                processes.started().get(0).argv(),
                "the process itself is given the real argument array");
    }

    @Test
    @DisplayName("a context key that is not lower case, digits and inner hyphens is refused")
    void contextKeysAreChecked() {
        PdvLaunches launches = launches(new FakePdv(run, Behaviour.EXITS_NON_ZERO, ""));
        RunId id = new RunId(PdvFixtures.RUN_ID);
        for (String key : List.of("PSM", "psm.id", "-psm", "psm-", "psm--id", "")) {
            IllegalArgumentException refused =
                    assertThrows(
                            IllegalArgumentException.class,
                            () ->
                                    launches.forRun(
                                            run,
                                            id,
                                            pdv,
                                            PdvLaunchKind.CLI_FIGURE,
                                            Map.of(key, "x")));
            assertEquals(
                    "a PDV launch's context key must be lower case, digits and inner hyphens,"
                            + " but was: \""
                            + key
                            + "\"",
                    refused.getMessage());
        }
    }

    @Test
    @DisplayName("a context value that is null is refused, naming its key")
    void nullContextValues() {
        PdvLaunches launches = launches(new FakePdv(run, Behaviour.EXITS_NON_ZERO, ""));
        Map<String, String> context = new HashMap<>();
        context.put("psm-id", null);

        NullPointerException refused =
                assertThrows(
                        NullPointerException.class,
                        () ->
                                launches.forRun(
                                        run,
                                        new RunId(PdvFixtures.RUN_ID),
                                        pdv,
                                        PdvLaunchKind.CLI_FIGURE,
                                        context));
        assertEquals("the value of psm-id", refused.getMessage());
    }

    @Test
    @DisplayName("the launch kinds' wire names are typed out")
    void kindWireNames() {
        assertEquals("cli-figure", PdvLaunchKind.CLI_FIGURE.wireName());
        assertEquals("denovo-gui", PdvLaunchKind.DENOVO_GUI.wireName());
    }

    private static List<Long> sequences(List<ProvenanceEvent> events) {
        return events.stream().map(ProvenanceEvent::sequence).toList();
    }
}
