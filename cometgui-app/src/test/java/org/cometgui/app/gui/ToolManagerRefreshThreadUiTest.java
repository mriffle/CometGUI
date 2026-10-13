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

package org.cometgui.app.gui;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import org.cometgui.app.config.ToolManagerWiring;
import org.cometgui.app.testing.ArtefactMirror;
import org.cometgui.app.testing.FxToolkit;
import org.cometgui.app.testing.LoopbackArtefactServer;
import org.cometgui.app.testing.ScriptedChooser;
import org.cometgui.domain.platform.GlibcVersion;
import org.cometgui.domain.ports.ProcessListener;
import org.cometgui.domain.ports.ProcessRunner;
import org.cometgui.domain.ports.RunningProcess;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.domain.tools.HostArchitecture;
import org.cometgui.domain.tools.HostOperatingSystem;
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.domain.tools.ToolInstallState;
import org.cometgui.domain.tools.ToolManager;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.install.probe.HostRuntimeVersions;
import org.cometgui.install.registry.ArtefactRecord;
import org.cometgui.tools.process.ProcessService;
import org.cometgui.ui.controls.ToolManagerPane;
import org.cometgui.ui.viewmodel.ToolManagerViewModel;
import org.cometgui.ui.viewmodel.ToolRowViewModel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * Refreshing the Tool Manager starts no process on the JavaFX application thread.
 *
 * <p>Reading the Tool Manager's offers starts every installed build to check that it still runs
 * here; for the Limelight converter that is a second Java virtual machine, {@code java -jar ...
 * --version}. Until the Phase 12 to 13 repair, {@code ToolManagerViewModel.refresh()} did that read
 * where it was called -- on the interface thread -- so the window stalled on every refresh.
 *
 * <p><strong>Graded through the one process service, not by timing.</strong> Everything here is the
 * product's own: the manager {@code ToolManagerWiring} composes over the shipped manifest, an empty
 * cache, the real pinned converter JAR installed into it by pressing nothing but the view-model's
 * own {@code install}, and the view-model built as {@code CometGuiApplication} builds it -- {@code
 * Platform::runLater} and the Tool Manager's own install threads -- in a live scene. The one seam
 * is in front of the {@link ProcessService}: it records, for every process the application starts,
 * its argument array and whether it was started on the JavaFX application thread. The test then
 * asserts that the refresh really did start the converter's version check (so the grading cannot
 * pass by nothing having run) and that no process at all was started on the interface thread.
 */
@EnabledOnOs(
        value = OS.LINUX,
        disabledReason = "the converter install and its identity probe have run on Linux only")
class ToolManagerRefreshThreadUiTest {

    private static final HostPlatform LINUX =
            new HostPlatform(HostOperatingSystem.LINUX, HostArchitecture.X86_64);

    private static final HostRuntimeVersions DEBIAN_12 =
            new HostRuntimeVersions(
                    Optional.of(GlibcVersion.parse("2.36")),
                    Optional.of(GlibcVersion.parse("3.4.30")));

    private static final String MIRRORED = "v2.8.1__cometPercolator2LimelightXML.jar";

    private static final String CONVERTER_ROW = "limelight-converter-2_8_1-1";

    /** Long enough for an install of a 2.7 MB JAR and a second JVM's start on a loaded machine. */
    private static final long TIMEOUT_SECONDS = 300;

    @TempDir private Path scratch;

    /** One process the application started, and where. */
    private record Launch(List<String> argv, String thread, boolean interfaceThread) {}

    /** The one process service, with a record of every start in front of it. */
    private static final class WhereStarted implements ProcessRunner {

        private final ProcessRunner delegate;

        private final List<Launch> launches = Collections.synchronizedList(new ArrayList<>());

        WhereStarted(ProcessRunner delegate) {
            this.delegate = delegate;
        }

        @Override
        public RunningProcess start(ToolCommand command, ProcessListener listener)
                throws IOException {
            Objects.requireNonNull(command, "command");
            launches.add(
                    new Launch(
                            command.argv(),
                            Thread.currentThread().getName(),
                            Platform.isFxApplicationThread()));
            return delegate.start(command, listener);
        }

        List<Launch> launches() {
            synchronized (launches) {
                return List.copyOf(launches);
            }
        }
    }

    @BeforeAll
    static void startToolkit() throws InterruptedException {
        FxToolkit.start();
    }

    @Test
    @DisplayName(
            "with the converter installed, a refresh runs its version check, and not on the JavaFX"
                    + " application thread")
    void aRefreshStartsNoProcessOnTheInterfaceThread() throws IOException, InterruptedException {
        Path cache = scratch.toRealPath().resolve("cometgui-data");
        ArtefactRecord record = ArtefactMirror.record(ToolName.LIMELIGHT_CONVERTER, "2.8.1", LINUX);
        WhereStarted processes = new WhereStarted(new ProcessService(Clock.systemUTC()));
        try (LoopbackArtefactServer served =
                new LoopbackArtefactServer()
                        .serve(record.url(), ArtefactMirror.artefact(MIRRORED))) {
            ToolManager manager =
                    ToolManagerWiring.toolManager(
                            LINUX,
                            DEBIAN_12,
                            cache,
                            processes,
                            Clock.systemUTC(),
                            ToolManagerWiring.installThreads(),
                            served);
            ToolManagerViewModel viewModel =
                    new ToolManagerViewModel(
                            manager,
                            Platform::runLater,
                            ToolManagerWiring.installThreads(),
                            new ScriptedChooser());
            ObservableList<ToolRowViewModel> rows = viewModel.rows();
            FxToolkit.onFxThread(
                    () -> {
                        Scene scene = new Scene(new ToolManagerPane(viewModel), 1024, 768);
                        scene.getRoot().applyCss();
                        scene.getRoot().layout();
                        viewModel.refresh();
                    });
            awaitOnFxThread(
                    "the converter's row after the first refresh",
                    () -> converterRow(rows).isPresent());

            FxToolkit.onFxThread(() -> viewModel.install(converterRow(rows).orElseThrow()));
            awaitOnFxThread(
                    "the converter's row to say it is installed",
                    () ->
                            converterRow(rows)
                                    .map(row -> row.offer().state() == ToolInstallState.INSTALLED)
                                    .orElse(false));
            Path jar = converterRow(rows).orElseThrow().offer().installedPath().orElseThrow();

            int launchesBeforeTheRefresh = processes.launches().size();
            CountDownLatch rebuilt = new CountDownLatch(1);
            FxToolkit.onFxThread(
                    () -> {
                        rows.addListener(
                                (ListChangeListener<ToolRowViewModel>)
                                        change -> rebuilt.countDown());
                        viewModel.refresh();
                    });
            assertTrue(
                    rebuilt.await(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                    "the refresh's answer never reached the rows, so this test would prove"
                            + " nothing");
            List<Launch> all = processes.launches();
            List<Launch> duringTheRefresh = all.subList(launchesBeforeTheRefresh, all.size());
            List<Launch> onTheInterfaceThread =
                    all.stream().filter(Launch::interfaceThread).toList();

            String converter = jar.toString();
            boolean versionCheckRan =
                    duringTheRefresh.stream()
                            .map(Launch::argv)
                            .anyMatch(
                                    argv -> argv.contains(converter) && argv.contains("--version"));
            assertAll(
                    () ->
                            assertTrue(
                                    versionCheckRan,
                                    () ->
                                            "the refresh did not run the installed converter's"
                                                    + " version check, so it graded nothing; it"
                                                    + " started: "
                                                    + duringTheRefresh),
                    () ->
                            assertEquals(
                                    List.of(),
                                    onTheInterfaceThread,
                                    () ->
                                            "a process was started on the JavaFX application"
                                                    + " thread -- the window stalls for as long as"
                                                    + " it runs. Of "
                                                    + all.size()
                                                    + " launches, these were on it"));
        }
    }

    private static Optional<ToolRowViewModel> converterRow(List<ToolRowViewModel> rows) {
        return rows.stream().filter(row -> CONVERTER_ROW.equals(row.key())).findFirst();
    }

    /**
     * Waits for a condition read on the interface thread, failing with what was awaited.
     *
     * @param what what is being waited for, named in the failure
     * @param condition the condition, evaluated on the interface thread
     * @throws InterruptedException if the wait is interrupted
     */
    private static void awaitOnFxThread(String what, BooleanSupplier condition)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        while (!FxToolkit.callOnFxThread(condition::getAsBoolean)) {
            assertTrue(
                    System.nanoTime() < deadline,
                    () -> "waited " + TIMEOUT_SECONDS + "s for " + what + " and it never came");
            Thread.sleep(50);
        }
    }
}
