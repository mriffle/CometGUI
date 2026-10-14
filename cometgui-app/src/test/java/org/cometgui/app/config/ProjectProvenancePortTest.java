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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.cometgui.domain.ports.ProcessRunner;
import org.cometgui.domain.ports.RunningProcess;
import org.cometgui.domain.secrets.SecretRedactor;
import org.cometgui.domain.tools.HostOperatingSystem;
import org.cometgui.ui.viewmodel.provenance.OpenedRecord;
import org.cometgui.workflow.provenance.ProvenanceReader;
import org.cometgui.workflow.provenance.ProvenanceView;
import org.cometgui.workflow.provenance.RecordState;
import org.cometgui.workflow.provenance.RunCondition;
import org.cometgui.workflow.provenance.RunListing;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The Provenance section's port over a project on disk: it lists what the read model lists, opens a
 * run through it, refuses to act on a run it did not open, and hands Open file location to the
 * launcher. The real runs -- sealed, failed and cancelled, exported and verified -- are {@code
 * ProvenanceSectionUiTest}'s, through the running application.
 */
class ProjectProvenancePortTest {

    @TempDir private Path work;

    private ProjectSession project;

    private final List<List<String>> launched = new ArrayList<>();

    private ProjectProvenancePort port;

    @BeforeEach
    void build() {
        project = new ProjectSession(work.resolve("project"), Clock.systemUTC(), () -> null);
        ProcessRunner recorder =
                (command, listener) -> {
                    launched.add(command.argv());
                    listener.onExit(0);
                    return new RunningProcess() {
                        @Override
                        public boolean isAlive() {
                            return false;
                        }

                        @Override
                        public int waitForExit() {
                            return 0;
                        }

                        @Override
                        public void requestCancellation() {
                            // nothing runs
                        }
                    };
                };
        port =
                new ProjectProvenancePort(
                        project,
                        Set::of,
                        new ProvenanceReader(SecretRedactor::patternsOnly, RunWiring.hasher()),
                        new FileManagerLauncher(
                                Optional.of(HostOperatingSystem.LINUX),
                                Optional.of(recorder),
                                "unused",
                                SecretRedactor::patternsOnly));
    }

    @Test
    @DisplayName("a project with no runs/ lists nothing, and takes no lock")
    void noRuns() throws IOException {
        assertAll(
                () -> assertEquals(List.of(), port.runs()),
                () -> assertFalse(project.isOpen(), "listing opens no project"),
                () -> assertFalse(Files.exists(project.directory()), "and creates nothing"));
    }

    @Test
    @DisplayName(
            "a run directory with no run.json is listed as unreadable and opens with no sealed"
                    + " record, which has nothing to export or verify")
    void aDamagedRun() throws IOException {
        Path run = Files.createDirectories(project.layout().runsDirectory().resolve("damaged"));
        List<RunListing> listed = port.runs();
        assertEquals(1, listed.size());
        RunListing damaged = listed.get(0);
        assertAll(
                () -> assertEquals("damaged", damaged.directoryName()),
                () -> assertEquals(RunCondition.UNREADABLE, damaged.condition()),
                () -> assertFalse(damaged.sealedRecord()));
        OpenedRecord opened = port.open(damaged);
        ProvenanceView view = opened.view();
        assertAll(
                () -> assertEquals(damaged, opened.listing()),
                () -> assertEquals(RecordState.NO_SEALED_RECORD, view.summary().recordState()),
                () -> assertFalse(opened.canExport()),
                () -> assertEquals(Optional.empty(), opened.recordProblem()));
        IOException json =
                assertThrows(
                        IOException.class,
                        () -> port.exportJson(opened, work.resolve("out.json"), false));
        assertAll(
                () ->
                        assertEquals(
                                "run damaged has no sealed provenance record (its provenance.json"
                                        + " does not exist), so there is nothing to export or"
                                        + " verify",
                                json.getMessage()),
                () ->
                        assertThrows(
                                IOException.class,
                                () -> port.exportRst(opened, work.resolve("out.rst"), false)),
                () -> assertThrows(IOException.class, () -> port.verify(opened, () -> false)),
                () -> assertFalse(Files.exists(work.resolve("out.json"))),
                () -> assertFalse(Files.exists(work.resolve("out.rst"))));

        Files.delete(run);
        assertThrows(NoSuchFileException.class, () -> port.open(damaged));
    }

    @Test
    @DisplayName("a run the port did not open is refused, naming why")
    void aForeignRecord() {
        OpenedRecord foreign =
                new OpenedRecord() {
                    @Override
                    public RunListing listing() {
                        throw new AssertionError("not asked");
                    }

                    @Override
                    public ProvenanceView view() {
                        throw new AssertionError("not asked");
                    }

                    @Override
                    public Optional<String> recordProblem() {
                        return Optional.empty();
                    }

                    @Override
                    public boolean canExport() {
                        return true;
                    }
                };
        IOException thrown =
                assertThrows(
                        IOException.class,
                        () -> port.exportJson(foreign, work.resolve("x.json"), true));
        assertEquals(
                "this run was not opened by the application's provenance port, so there is no"
                        + " loaded record to act on",
                thrown.getMessage());
    }

    @Test
    @DisplayName("Open file location goes to the launcher: xdg-open on the file's folder")
    void openLocation() throws IOException {
        Path folder = Files.createDirectories(work.resolve("logs"));
        Path log = Files.writeString(folder.resolve("comet-01.log"), "x");
        assertEquals(
                "Started xdg-open: the file manager was asked to show it.", port.openLocation(log));
        assertEquals(List.of(List.of("xdg-open", folder.toString())), launched);
    }
}
