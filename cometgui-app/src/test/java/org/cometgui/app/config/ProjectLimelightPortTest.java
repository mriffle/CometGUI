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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
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
import org.cometgui.app.testing.RecordingProcessRunner;
import org.cometgui.app.testing.ResultRuns;
import org.cometgui.domain.log.BoundedMessageLog;
import org.cometgui.domain.log.LogMessage;
import org.cometgui.domain.log.MessageSeverity;
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
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.percolator.PercolatorSettings;
import org.cometgui.params.percolator.resolution.CompatibilityAction;
import org.cometgui.params.percolator.resolution.DownstreamStage;
import org.cometgui.provenance.hashing.StreamingHashService;
import org.cometgui.results.filtering.store.TableKind;
import org.cometgui.tools.api.JavaRuntime;
import org.cometgui.ui.viewmodel.limelight.LimelightOptions;
import org.cometgui.ui.viewmodel.limelight.LimelightRerun;
import org.cometgui.ui.viewmodel.limelight.LimelightRunState;
import org.cometgui.ui.viewmodel.params.ActiveRun;
import org.cometgui.ui.viewmodel.params.EngineCheck;
import org.cometgui.ui.viewmodel.params.PercolatorRequest;
import org.cometgui.ui.viewmodel.params.RunNotStartedException;
import org.cometgui.ui.viewmodel.params.RunObserver;
import org.cometgui.ui.viewmodel.percolator.RerunCheck;
import org.cometgui.ui.viewmodel.results.ResultsRun;
import org.cometgui.workflow.engine.RunResult;
import org.cometgui.workflow.engine.StepTransition;
import org.cometgui.workflow.limelight.LimelightConversionRefusedException;
import org.cometgui.workflow.limelight.LimelightConversions;
import org.cometgui.workflow.limelight.LimelightConverterStatus;
import org.cometgui.workflow.limelight.LimelightPrerequisite;
import org.cometgui.workflow.limelight.LimelightProvisioning;
import org.cometgui.workflow.limelight.LimelightRefusal;
import org.cometgui.workflow.state.Plan;
import org.cometgui.workflow.storage.LimelightSettingsStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The Limelight section's port over a constructed run (no tool runs): the converter's state and its
 * install through the Tool Manager it is given; the run found from what the Results section lists;
 * the settings saved by the workflow's rules -- the cutoff's, the run's decoy rule -- and read
 * back; the output as typed; an executing run refused; a conversion refused by name before anything
 * starts; the converter's lines routed to the conversion that asked; and a rerun only when the
 * workflow offers that same action now. The real conversion and rerun are the GUI tests'.
 */
class ProjectLimelightPortTest {

    private static final Instant CREATED = Instant.parse("2026-10-12T08:00:00Z");

    private static final String RUN = "limelight-port-run";

    private static final String NO_TOOLS = "No Tool Manager in this test.";

    private static final String NOTHING = "Nothing can be converted in this test.";

    private static final HostPlatform LINUX =
            new HostPlatform(HostOperatingSystem.LINUX, HostArchitecture.X86_64);

    @TempDir private Path scratch;

    private ProjectSession session;

    private RunLayout run;

    private final Set<RunId> executing = new HashSet<>();

    private final Tools tools = new Tools();

    private final Engine engine = new Engine();

    private final List<LogMessage> console = new ArrayList<>();

    private RecordingProcessRunner processes;

    @BeforeEach
    void build() throws IOException {
        Path root = scratch.toRealPath();
        ProjectLayout project = ResultRuns.project(root.resolve("project"));
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
                        List.of(root.resolve("inputs/k562_3.mgf")),
                        Map.of(TableKind.TARGET_PSMS, psms),
                        Optional.empty());
        session =
                new ProjectSession(
                        root.resolve("project"),
                        Clock.systemUTC(),
                        ApplicationServices.forThisHost().runIds());
        processes = RecordingProcessRunner.inFrontOf(Clock.systemUTC());
    }

    private ProjectLimelightPort port() throws IOException {
        ProjectLimelightPort.ConverterLines lines =
                new ProjectLimelightPort.ConverterLines(console::add);
        return new ProjectLimelightPort(
                session,
                () -> Optional.of(tools),
                NO_TOOLS,
                Optional.of(
                        new LimelightConversions(
                                new LimelightProvisioning(tools),
                                processes,
                                new StreamingHashService(),
                                SecretRedactor.patternsOnly(),
                                lines,
                                Clock.systemUTC(),
                                LINUX,
                                JavaRuntime.ofThisApplication(),
                                Duration.ofMinutes(1),
                                () -> Set.copyOf(executing))),
                NOTHING,
                engine,
                lines);
    }

    private ProjectLimelightPort nothing() {
        return new ProjectLimelightPort(
                session,
                Optional::empty,
                NO_TOOLS,
                Optional.empty(),
                NOTHING,
                engine,
                new ProjectLimelightPort.ConverterLines(console::add));
    }

    private static ResultsRun listed() {
        return new ResultsRun(
                new RunId(RUN),
                CREATED,
                AttemptOutcome.SUCCEEDED,
                EnumSet.of(TableKind.TARGET_PSMS),
                false);
    }

    private static LimelightOptions options(String cutoff, boolean decoys, String prefix) {
        return new LimelightOptions(cutoff, decoys, prefix, false);
    }

    @Test
    @DisplayName(
            "a run's check: its saved settings (the defaults, cutoff 0.01), the workflow's"
                    + " refusals by name, the default output under the run's outputs")
    void aRunIsChecked() throws IOException {
        LimelightRunState state = port().check(listed(), "  ");
        assertAll(
                () -> assertEquals(options("0.01", false, ""), state.saved()),
                () -> assertEquals(new RunId(RUN), state.readiness().runId()),
                () -> assertFalse(state.readiness().ready()),
                () ->
                        assertTrue(
                                state.readiness()
                                        .refusal(LimelightPrerequisite.PERCOLATOR_XML)
                                        .isPresent()),
                () ->
                        assertTrue(
                                state.readiness()
                                        .refusal(LimelightPrerequisite.CONVERTER)
                                        .isPresent(),
                                "no converter is offered here"),
                () ->
                        assertEquals(
                                Optional.of(
                                        run.root().resolve("outputs/limelight/limelight-1.xml")),
                                state.readiness().output()),
                () -> assertEquals(run.cometOutputDirectory(), state.readiness().pepXmlDirectory()),
                () -> assertEquals(List.of(), processes.launched(), "nothing was started"));
    }

    @Test
    @DisplayName(
            "settings are saved by the workflow's rules and read back; a refused cutoff names the"
                    + " text and the rule; decoy import is refused for a run without decoys")
    void settingsAreSavedByTheWorkflowsRules() throws IOException {
        ProjectLimelightPort port = port();
        LimelightRunState saved = port.save(listed(), options(" 0.050 ", false, " rev_ "), "");
        assertAll(
                () -> assertEquals(options("0.05", false, "rev_"), saved.saved()),
                () ->
                        assertEquals(
                                "0.05",
                                LimelightSettingsStore.read(run).qCutoff().argument(),
                                "written to the run's own settings file"),
                () ->
                        assertEquals(
                                options("0.05", false, "rev_"), port.check(listed(), "").saved()));
        IOException refused =
                assertThrows(
                        IOException.class,
                        () -> port.save(listed(), options("1.5", false, ""), ""));
        assertEquals(
                "\"1.5\" was not accepted: the Limelight q-value cutoff must be a decimal number"
                        + " from 0 to 1",
                refused.getMessage());
        assertThrows(IOException.class, () -> port.save(listed(), options("abc", false, ""), ""));
        LimelightConversionRefusedException decoys =
                assertThrows(
                        LimelightConversionRefusedException.class,
                        () -> port.save(listed(), options("0.01", true, ""), ""));
        assertAll(
                () ->
                        assertEquals(
                                List.of(LimelightPrerequisite.DECOY_IMPORT),
                                decoys.refusals().stream()
                                        .map(LimelightRefusal::prerequisite)
                                        .toList()),
                () ->
                        assertEquals(
                                options("0.05", false, "rev_"),
                                port.check(listed(), "").saved(),
                                "nothing refused was written"));
        LimelightRunState cleared = port.save(listed(), options("0.01", false, "   "), "");
        assertEquals(options("0.01", false, ""), cleared.saved());
    }

    @Test
    @DisplayName(
            "the output as typed: a relative path is the workflow's refusal; a non-path is ours")
    void theOutputAsTyped() throws IOException {
        ProjectLimelightPort port = port();
        LimelightRunState relative = port.check(listed(), "relative.xml");
        assertAll(
                () -> assertEquals(Optional.empty(), relative.readiness().output()),
                () ->
                        assertTrue(
                                relative.readiness()
                                        .refusal(LimelightPrerequisite.OUTPUT)
                                        .isPresent()));
        Path explicit = scratch.toRealPath().resolve("out/explicit.xml");
        Files.createDirectories(Objects.requireNonNull(explicit.getParent(), "its directory"));
        assertEquals(
                Optional.of(explicit),
                port.check(listed(), " " + explicit + " ").readiness().output());
        IOException notAPath =
                assertThrows(IOException.class, () -> port.check(listed(), "bad\0path"));
        assertTrue(
                notAPath.getMessage().startsWith("the output \"bad\0path\" is not a path on this"),
                notAPath.getMessage());
    }

    @Test
    @DisplayName(
            "a conversion of a run that is not ready is refused by name, starting nothing; the"
                    + " settings it was asked with are saved first")
    void aConversionIsRefusedByName() throws IOException {
        List<String> heard = new ArrayList<>();
        LimelightConversionRefusedException refused =
                assertThrows(
                        LimelightConversionRefusedException.class,
                        () -> port().convert(listed(), options("0.2", false, ""), "", heard::add));
        assertAll(
                () ->
                        assertTrue(
                                refused.refusals().stream()
                                        .anyMatch(
                                                refusal ->
                                                        refusal.prerequisite()
                                                                == LimelightPrerequisite
                                                                        .PERCOLATOR_XML)),
                () -> assertEquals(List.of(), processes.launched()),
                () -> assertEquals(List.of(), heard),
                () -> assertEquals("0.2", LimelightSettingsStore.read(run).qCutoff().argument()));
        assertThrows(
                IOException.class,
                () -> port().convert(listed(), options("2", false, ""), "", heard::add));
    }

    @Test
    @DisplayName("a run that is executing is refused: saved, converted and rerun alike")
    void anExecutingRunIsRefused() throws IOException {
        executing.add(new RunId(RUN));
        engine.executing.add(new RunId(RUN));
        ProjectLimelightPort port = port();
        String why =
                "run "
                        + RUN
                        + " is executing: its results are not final and the workflow engine holds"
                        + " its event log. Convert it when the run has ended.";
        IOException saving =
                assertThrows(
                        IOException.class,
                        () -> port.save(listed(), options("0.01", false, ""), ""));
        IOException converting =
                assertThrows(
                        IOException.class,
                        () -> port.convert(listed(), options("0.01", false, ""), "", line -> {}));
        RunNotStartedException rerunning =
                assertThrows(
                        RunNotStartedException.class,
                        () ->
                                port.rerun(
                                        listed(),
                                        action(),
                                        PercolatorSettings.defaults(),
                                        engine.observer));
        assertAll(
                () -> assertEquals(why, saving.getMessage()),
                () -> assertEquals(why, converting.getMessage()),
                () -> assertEquals(why, rerunning.getMessage()),
                () ->
                        assertFalse(
                                Files.exists(
                                        run.root().resolve("results/limelight-settings.json"))),
                () -> assertEquals(0, engine.reruns));
        assertTrue(
                port.check(listed(), "")
                        .readiness()
                        .refusal(LimelightPrerequisite.RUN_ENDED)
                        .isPresent(),
                "a check says so by name");
    }

    @Test
    @DisplayName(
            "a rerun is started only when the workflow offers that same action, with an installed"
                    + " build, now")
    void aRerunNeedsTheSameActionNow() throws IOException {
        RunNotStartedException refused =
                assertThrows(
                        RunNotStartedException.class,
                        () ->
                                port().rerun(
                                                listed(),
                                                action(),
                                                PercolatorSettings.defaults(),
                                                engine.observer));
        assertAll(
                () ->
                        assertTrue(
                                refused.getMessage()
                                        .startsWith(
                                                "The action offered for run "
                                                        + RUN
                                                        + " is no longer \""
                                                        + action().label()
                                                        + "\" with an installed build: "),
                                refused.getMessage()),
                () -> assertTrue(refused.getMessage().endsWith(". Check again.")),
                () -> assertEquals(0, engine.reruns));
        RunNotStartedException none =
                assertThrows(
                        RunNotStartedException.class,
                        () ->
                                nothing()
                                        .rerun(
                                                listed(),
                                                action(),
                                                PercolatorSettings.defaults(),
                                                engine.observer));
        assertEquals(
                "Whether run " + RUN + " can be rerun could not be read: " + NOTHING,
                none.getMessage());
    }

    @Test
    @DisplayName("the converter's state and both installs are the Tool Manager's")
    void theToolManager() throws IOException {
        ProjectLimelightPort port = port();
        assertEquals(LimelightConverterStatus.State.NOT_INSTALLED, port.converter().state());
        InstallProgressListener listener = progress -> {};
        InstallHandle converter = port.installConverter(ToolVersion.parse("2.8.1"), listener);
        InstallHandle percolator = port.installPercolator(ToolVersion.parse("3.07.1"), listener);
        assertAll(
                () ->
                        assertEquals(
                                List.of("limelight-converter 2.8.1", "percolator 3.07.1"),
                                tools.installs),
                () -> assertSame(tools.handle, converter),
                () -> assertSame(tools.handle, percolator));
        tools.refuse = true;
        IOException notOffered =
                assertThrows(
                        IOException.class,
                        () -> port.installPercolator(ToolVersion.parse("9.9"), listener));
        assertEquals("not offered: percolator 9.9", notOffered.getMessage());
        assertThrows(
                IOException.class, () -> port.installConverter(ToolVersion.parse("9.9"), listener));

        ProjectLimelightPort none = nothing();
        assertAll(
                () ->
                        assertEquals(
                                LimelightConverterStatus.unavailable(NO_TOOLS), none.converter()),
                () ->
                        assertEquals(
                                NO_TOOLS,
                                assertThrows(
                                                IOException.class,
                                                () ->
                                                        none.installConverter(
                                                                ToolVersion.parse("2.8.1"),
                                                                listener))
                                        .getMessage()),
                () ->
                        assertEquals(
                                NO_TOOLS,
                                assertThrows(
                                                IOException.class,
                                                () ->
                                                        none.installPercolator(
                                                                ToolVersion.parse("3.07.1"),
                                                                listener))
                                        .getMessage()),
                () ->
                        assertEquals(
                                NOTHING,
                                assertThrows(IOException.class, () -> none.check(listed(), ""))
                                        .getMessage()),
                () ->
                        assertEquals(
                                NOTHING,
                                assertThrows(
                                                IOException.class,
                                                () ->
                                                        none.save(
                                                                listed(),
                                                                options("0.01", false, ""),
                                                                ""))
                                        .getMessage()),
                () ->
                        assertEquals(
                                NOTHING,
                                assertThrows(
                                                IOException.class,
                                                () ->
                                                        none.convert(
                                                                listed(),
                                                                options("0.01", false, ""),
                                                                "",
                                                                line -> {}))
                                        .getMessage()));
    }

    @Test
    @DisplayName(
            "the converter's lines always reach the console, and the waiting conversion while it"
                    + " waits")
    void converterLines() {
        List<String> heard = new ArrayList<>();
        ProjectLimelightPort.ConverterLines lines =
                new ProjectLimelightPort.ConverterLines(console::add);
        LimelightMessages.append(lines, "before");
        lines.listen(heard::add);
        LimelightMessages.append(lines, "during");
        lines.listen(null);
        LimelightMessages.append(lines, "after");
        assertAll(
                () -> assertEquals(List.of("during"), heard),
                () ->
                        assertEquals(
                                List.of("before", "during", "after"),
                                console.stream().map(LogMessage::text).toList()));
    }

    @Test
    @DisplayName(
            "the composition: no Tool Manager or no process service means nothing can convert,"
                    + " and the port says why")
    void theComposition() {
        ApplicationServices real = ApplicationServices.forThisHost();
        ApplicationServices noProcesses =
                new ApplicationServices(
                        real.clock(),
                        real.environment(),
                        real.fileSystem(),
                        real.runIds(),
                        real.glibcVersions(),
                        null,
                        null,
                        null);
        BoundedMessageLog log = new BoundedMessageLog();
        assertAll(
                () ->
                        assertEquals(
                                "No run can be converted to Limelight XML: " + NO_TOOLS,
                                assertThrows(
                                                IOException.class,
                                                () ->
                                                        RunWiring.limelight(
                                                                        real,
                                                                        log,
                                                                        session,
                                                                        new StreamingHashService(),
                                                                        engine,
                                                                        Optional.empty(),
                                                                        NO_TOOLS)
                                                                .check(listed(), ""))
                                        .getMessage()),
                () ->
                        assertEquals(
                                RunWiring.NO_PROCESS_SERVICE,
                                assertThrows(
                                                IOException.class,
                                                () ->
                                                        RunWiring.limelight(
                                                                        noProcesses,
                                                                        log,
                                                                        session,
                                                                        new StreamingHashService(),
                                                                        engine,
                                                                        Optional.of(tools),
                                                                        NO_TOOLS)
                                                                .check(listed(), ""))
                                        .getMessage()),
                () ->
                        assertFalse(
                                RunWiring.limelight(
                                                real,
                                                log,
                                                session,
                                                new StreamingHashService(),
                                                engine,
                                                Optional.of(tools),
                                                NO_TOOLS)
                                        .check(listed(), "")
                                        .readiness()
                                        .ready(),
                                "with both, the workflow answers"));
    }

    private static CompatibilityAction action() {
        return CompatibilityAction.forStage(
                List.of(
                        new ToolOffer(
                                ToolName.PERCOLATOR,
                                ToolVersion.parse("3.07.1"),
                                ToolOrigin.MANAGED,
                                ToolInstallState.NOT_INSTALLED,
                                List.of(),
                                List.of(),
                                Optional.empty(),
                                Optional.empty(),
                                OptionalLong.of(1L))),
                DownstreamStage.LIMELIGHT_CONVERSION);
    }

    /** A message the converter's lines carry. */
    private static final class LimelightMessages {

        private LimelightMessages() {}

        static void append(ProjectLimelightPort.ConverterLines lines, String text) {
            lines.append(LogMessage.at(Instant.EPOCH, null, MessageSeverity.INFO, text));
        }
    }

    /** A Tool Manager offering the converter not installed, recording installs. */
    private static final class Tools implements ToolManager {

        private final List<String> installs = new ArrayList<>();

        private final InstallHandle handle = () -> {};

        private boolean refuse;

        @Override
        public List<ToolOffer> offers() {
            return List.of(
                    new ToolOffer(
                            ToolName.LIMELIGHT_CONVERTER,
                            ToolVersion.parse("2.8.1"),
                            ToolOrigin.MANAGED,
                            ToolInstallState.NOT_INSTALLED,
                            List.of(),
                            List.of(),
                            Optional.empty(),
                            Optional.empty(),
                            OptionalLong.of(2_762_075L)));
        }

        @Override
        public List<NoManagedBuild> noManagedBuild() {
            return List.of();
        }

        @Override
        public InstallHandle install(
                ToolName tool, ToolVersion version, InstallProgressListener listener) {
            if (refuse) {
                throw new IllegalArgumentException(
                        "not offered: " + tool.id() + " " + version.text());
            }
            installs.add(tool.id() + " " + version.text());
            return handle;
        }

        @Override
        public ToolOffer registerLocalBinary(ToolName tool, Path executable) {
            throw new UnsupportedOperationException("no registration here");
        }
    }

    /** A session engine that only says what is executing and counts reruns. */
    private static final class Engine implements SessionEngine {

        private final Set<RunId> executing = new HashSet<>();

        private int reruns;

        private final RunObserver observer =
                new RunObserver() {
                    @Override
                    public void planned(Plan plan, String description) {
                        // nothing runs
                    }

                    @Override
                    public void onTransition(StepTransition transition) {
                        // nothing runs
                    }

                    @Override
                    public void onRunFinished(RunResult result) {
                        // nothing runs
                    }
                };

        @Override
        public EngineCheck check(
                CometParameters model, List<Path> spectra, PercolatorRequest percolator) {
            return EngineCheck.unavailable("no engine");
        }

        @Override
        public ActiveRun start(
                CometParameters model,
                List<Path> spectra,
                PercolatorRequest percolator,
                RunObserver runObserver)
                throws RunNotStartedException {
            throw new RunNotStartedException("no engine", null);
        }

        @Override
        public RerunCheck check(PercolatorRequest percolator) {
            return RerunCheck.refused("no engine");
        }

        @Override
        public ActiveRun start(PercolatorRequest percolator, RunObserver runObserver)
                throws RunNotStartedException {
            throw new RunNotStartedException("no engine", null);
        }

        @Override
        public Set<RunId> executingRuns() {
            return Set.copyOf(executing);
        }

        @Override
        public LimelightRerun rerunCompatible(
                RunLayout source,
                CompatibilityAction action,
                PercolatorSettings settings,
                RunObserver runObserver) {
            reruns++;
            return new LimelightRerun(new RunId("derived"), source.root(), () -> {});
        }
    }
}
