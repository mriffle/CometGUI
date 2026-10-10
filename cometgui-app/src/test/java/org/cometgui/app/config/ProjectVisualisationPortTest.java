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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import javax.imageio.ImageIO;
import org.cometgui.app.testing.FakeEnvironment;
import org.cometgui.app.testing.ResultRuns;
import org.cometgui.app.testing.TestJson;
import org.cometgui.domain.ports.ProcessListener;
import org.cometgui.domain.ports.RunningProcess;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.domain.project.ProjectLayout;
import org.cometgui.domain.run.AttemptOutcome;
import org.cometgui.domain.run.RunId;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.domain.secrets.SecretRedactor;
import org.cometgui.domain.tools.HostArchitecture;
import org.cometgui.domain.tools.HostOperatingSystem;
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.domain.tools.InstallHandle;
import org.cometgui.domain.tools.InstallProgressListener;
import org.cometgui.domain.tools.NoManagedBuild;
import org.cometgui.domain.tools.ToolInstallState;
import org.cometgui.domain.tools.ToolManager;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolOrigin;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.provenance.hashing.StreamingHashService;
import org.cometgui.results.filtering.store.TableKind;
import org.cometgui.ui.viewmodel.results.ResultsRun;
import org.cometgui.workflow.pdv.DrawnSpectrum;
import org.cometgui.workflow.pdv.PdvLaunches;
import org.cometgui.workflow.pdv.PdvStatus;
import org.cometgui.workflow.pdv.PsmFigures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The Visualisation section's port: PDV's state and install through the Tool Manager it is given,
 * the run found from what the Results section lists, an executing run refused, and a drawing that
 * goes through the one recording launch path into the run's own event log. PDV itself is a stand-in
 * behind the process seam here; the GUI test draws with the real one.
 */
class ProjectVisualisationPortTest {

    private static final Instant CREATED = Instant.parse("2026-10-10T08:00:00Z");

    private static final String RUN = "port-run";

    @TempDir private Path scratch;

    private Path root;

    private ProjectSession session;

    private RunLayout run;

    private Path jar;

    private final Set<RunId> executing = new HashSet<>();

    private final List<List<String>> started = new ArrayList<>();

    private final List<Integer> eventLinesAtStart = new ArrayList<>();

    @BeforeEach
    void build() throws IOException {
        root = scratch.toRealPath();
        ProjectLayout project = ResultRuns.project(root.resolve("project"));
        Path spectra =
                Files.writeString(
                        Files.createDirectories(root.resolve("inputs")).resolve("k562_3.mgf"),
                        "BEGIN IONS\nTITLE=controllerType=0 controllerNumber=1 scan=101\n"
                                + "PEPMASS=500.25\nCHARGE=2+\n100.5 10\n200.5 20\nEND IONS\n",
                        StandardCharsets.UTF_8);
        Path psms = root.resolve("psms.tsv");
        Files.writeString(
                psms,
                "PSMId\tscore\tq-value\tposterior_error_prob\tpeptide\tproteinIds\n",
                StandardCharsets.UTF_8);
        run =
                ResultRuns.run(
                        project,
                        RUN,
                        CREATED,
                        List.of(spectra),
                        Map.of(TableKind.TARGET_PSMS, psms),
                        Optional.empty());
        Files.createDirectories(run.provenanceDirectory());
        Files.createDirectories(run.cometOutputDirectory());
        Files.writeString(run.pepXmlFile("k562_3"), "<msms_pipeline_analysis/>\n");
        jar = root.resolve("cache/PDV-2.7.0/PDV-2.7.0.jar");
        Files.createDirectories(Objects.requireNonNull(jar.getParent(), "its directory"));
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.IMPLEMENTATION_TITLE, "PDV");
        manifest.getMainAttributes().put(Attributes.Name.IMPLEMENTATION_VERSION, "2.7.0");
        try (OutputStream out = Files.newOutputStream(jar);
                JarOutputStream file = new JarOutputStream(out, manifest)) {
            file.flush();
        }
        session =
                new ProjectSession(
                        root.resolve("project"),
                        Clock.systemUTC(),
                        ApplicationServices.forThisHost().runIds());
    }

    private ProjectVisualisationPort port(Optional<ToolManager> tools, boolean canLaunch) {
        Optional<PsmFigures> figures =
                canLaunch
                        ? Optional.of(
                                new PsmFigures(
                                        new PdvLaunches(
                                                this::standInPdv,
                                                new StreamingHashService(),
                                                SecretRedactor.patternsOnly(),
                                                Clock.systemUTC(),
                                                new HostPlatform(
                                                        HostOperatingSystem.LINUX,
                                                        HostArchitecture.X86_64)),
                                        Duration.ofSeconds(30)))
                        : Optional.empty();
        return new ProjectVisualisationPort(
                session,
                () -> tools,
                "No Tool Manager in this test.",
                figures,
                "Nothing can be launched in this test.",
                () -> Set.copyOf(executing),
                () -> Map.of("DISPLAY", ":7"));
    }

    private static ResultsRun listed() {
        return new ResultsRun(
                new RunId(RUN),
                CREATED,
                AttemptOutcome.SUCCEEDED,
                EnumSet.of(TableKind.TARGET_PSMS),
                false);
    }

    private String psm() {
        return run.cometOutputBase("k562_3") + "_101_2_1";
    }

    /** PDV behind the process seam: draws one figure per requested id and exits 0. */
    private RunningProcess standInPdv(ToolCommand command, ProcessListener listener)
            throws IOException {
        started.add(command.argv());
        eventLinesAtStart.add(
                Files.exists(run.eventLogFile())
                        ? Files.readAllLines(run.eventLogFile()).size()
                        : 0);
        List<String> argv = command.argv();
        Path figures = Path.of(argv.get(argv.indexOf("-o") + 1));
        for (String id : Files.readAllLines(Path.of(argv.get(argv.indexOf("-i") + 1)))) {
            BufferedImage image = new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB);
            for (int x = 0; x < 200; x++) {
                for (int y = 0; y < 100; y++) {
                    image.setRGB(x, y, x % 10 == 0 ? 0xFF6600 : 0xFFFFFF);
                }
            }
            ImageIO.write(image, "png", figures.resolve("PEPTIDE" + id + ".png").toFile());
        }
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
                // already exited
            }
        };
    }

    @Test
    @DisplayName("with no Tool Manager, PDV is unavailable for its reason and cannot be installed")
    void noToolManager() {
        ProjectVisualisationPort port = port(Optional.empty(), true);

        assertEquals(PdvStatus.unavailable("No Tool Manager in this test."), port.pdv());
        IOException refused =
                assertThrows(
                        IOException.class,
                        () -> port.install(ToolVersion.parse("2.7.0"), progress -> {}));
        assertEquals("No Tool Manager in this test.", refused.getMessage());
    }

    @Test
    @DisplayName("PDV's state and its install are the Tool Manager's")
    void throughTheToolManager() throws IOException {
        Pdv tools = new Pdv(ToolInstallState.NOT_INSTALLED);
        ProjectVisualisationPort port = port(Optional.of(tools), true);

        assertEquals(PdvStatus.State.NOT_INSTALLED, port.pdv().state());
        port.install(ToolVersion.parse("2.7.0"), progress -> {});
        assertEquals(List.of("pdv 2.7.0"), tools.installs);
    }

    @Test
    @DisplayName("a drawing is recorded in the run's own event log before PDV starts")
    void aDrawingIsRecorded() throws IOException {
        DrawnSpectrum drawn =
                port(Optional.of(new Pdv(ToolInstallState.INSTALLED)), true).draw(listed(), psm());

        assertAll(
                () ->
                        assertEquals(
                                "controllerType=0 controllerNumber=1 scan=101", drawn.nativeId()),
                () -> assertTrue(drawn.figure().startsWith(run.root().resolve("pdv/figures/0001"))),
                () -> assertEquals(1, started.size()),
                () -> assertEquals(List.of(1), eventLinesAtStart, "the event was written first"));
        List<String> lines = Files.readAllLines(run.eventLogFile(), StandardCharsets.UTF_8);
        Map<String, Object> event = TestJson.object(lines.get(lines.size() - 1));
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) event.get("payload");
        assertAll(
                () -> assertEquals("pdv.launched", event.get("type")),
                () -> assertEquals(RUN, payload.get("run.id")),
                () -> assertEquals(psm(), payload.get("launch.psm-id")),
                () -> assertEquals(ResultRuns.sha256(jar), payload.get("pdv.jar.sha256")),
                () -> assertEquals("2.7.0", payload.get("tool.version")));
        assertTrue(session.isOpen(), "the project's lock was taken before writing into the run");
        session.close();
    }

    @Test
    @DisplayName("a run that is executing is refused: the engine holds its event log")
    void executingIsRefused() {
        executing.add(new RunId(RUN));

        IOException refused =
                assertThrows(
                        IOException.class,
                        () ->
                                port(Optional.of(new Pdv(ToolInstallState.INSTALLED)), true)
                                        .draw(listed(), psm()));

        assertEquals(
                "run "
                        + RUN
                        + " is executing: the workflow engine holds its provenance event log,"
                        + " where every PDV launch is recorded. Draw when the run has ended.",
                refused.getMessage());
        assertEquals(List.of(), started);
        assertFalse(Files.exists(run.eventLogFile()));
    }

    @Test
    @DisplayName("PDV not installed, or nothing able to launch, is refused before anything starts")
    void notInstalledOrNoLauncher() {
        IOException notInstalled =
                assertThrows(
                        IOException.class,
                        () ->
                                port(Optional.of(new Pdv(ToolInstallState.NOT_INSTALLED)), true)
                                        .draw(listed(), psm()));
        assertTrue(
                notInstalled.getMessage().startsWith("PDV is not installed: PDV 2.7.0 is not"),
                notInstalled.getMessage());
        IOException noLauncher =
                assertThrows(
                        IOException.class,
                        () ->
                                port(Optional.of(new Pdv(ToolInstallState.INSTALLED)), false)
                                        .draw(listed(), psm()));
        assertEquals("Nothing can be launched in this test.", noLauncher.getMessage());
        assertEquals(List.of(), started);
    }

    @Test
    @DisplayName("a run the Results section listed under another identity is refused")
    void anotherRun() {
        ResultsRun other =
                new ResultsRun(
                        new RunId("no-such-run"),
                        CREATED,
                        AttemptOutcome.SUCCEEDED,
                        EnumSet.of(TableKind.TARGET_PSMS),
                        false);

        assertThrows(
                IOException.class,
                () ->
                        port(Optional.of(new Pdv(ToolInstallState.INSTALLED)), true)
                                .draw(other, psm()));
        assertEquals(List.of(), started);
    }

    @Test
    @DisplayName(
            "the desktop's display: DISPLAY and XAUTHORITY, the user's .Xauthority when unset, and"
                    + " nothing without a display")
    void theDisplay() throws IOException {
        Path home = Files.createDirectories(root.resolve("home"));
        assertEquals(Map.of(), PdvDisplay.fromHost(new FakeEnvironment()));
        assertEquals(
                Map.of(), PdvDisplay.fromHost(new FakeEnvironment().withVariable("DISPLAY", " ")));
        assertEquals(
                Map.of("DISPLAY", ":1"),
                PdvDisplay.fromHost(
                        new FakeEnvironment()
                                .withVariable("DISPLAY", ":1")
                                .withVariable("HOME", home.toString())));
        Path cookie = Files.writeString(home.resolve(".Xauthority"), "cookie");
        assertEquals(
                Map.of("DISPLAY", ":1", "XAUTHORITY", cookie.toString()),
                PdvDisplay.fromHost(
                        new FakeEnvironment()
                                .withVariable("DISPLAY", ":1")
                                .withVariable("HOME", home.toString())));
        assertEquals(
                Map.of("DISPLAY", ":1", "XAUTHORITY", "/run/user/1000/gdm/Xauthority"),
                PdvDisplay.fromHost(
                        new FakeEnvironment()
                                .withVariable("DISPLAY", ":1")
                                .withVariable("XAUTHORITY", "/run/user/1000/gdm/Xauthority")
                                .withVariable("HOME", home.toString())
                                .withVariable("PATH", "/usr/bin")));
    }

    /** A Tool Manager offering one PDV 2.7.0 in a given state, recording installs. */
    private final class Pdv implements ToolManager {

        private final ToolInstallState state;

        private final List<String> installs = new ArrayList<>();

        Pdv(ToolInstallState state) {
            this.state = state;
        }

        @Override
        public List<ToolOffer> offers() {
            boolean installed = state == ToolInstallState.INSTALLED;
            return List.of(
                    new ToolOffer(
                            ToolName.PDV,
                            ToolVersion.parse("2.7.0"),
                            ToolOrigin.MANAGED,
                            state,
                            List.of(),
                            List.of(),
                            Optional.empty(),
                            installed ? Optional.of(jar) : Optional.empty(),
                            OptionalLong.of(103_407_417L)));
        }

        @Override
        public List<NoManagedBuild> noManagedBuild() {
            return List.of();
        }

        @Override
        public InstallHandle install(
                ToolName tool, ToolVersion version, InstallProgressListener listener) {
            installs.add(tool.id() + " " + version.text());
            return () -> {};
        }

        @Override
        public ToolOffer registerLocalBinary(ToolName tool, Path executable) {
            throw new UnsupportedOperationException();
        }
    }
}
