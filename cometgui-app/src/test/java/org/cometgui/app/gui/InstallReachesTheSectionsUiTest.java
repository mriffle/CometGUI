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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.ref.WeakReference;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.cometgui.app.config.RunWiring;
import org.cometgui.app.config.ToolManagerWiring;
import org.cometgui.app.testing.ArtefactMirror;
import org.cometgui.app.testing.LoopbackArtefactServer;
import org.cometgui.app.uidriver.Await;
import org.cometgui.app.uidriver.FxUiDriver;
import org.cometgui.app.uidriver.TestFxUiDriver;
import org.cometgui.domain.build.BuildIdentity;
import org.cometgui.domain.log.BoundedMessageLog;
import org.cometgui.domain.platform.GlibcVersion;
import org.cometgui.domain.tools.HostArchitecture;
import org.cometgui.domain.tools.HostOperatingSystem;
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.domain.tools.ToolManager;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.install.probe.HostRuntimeVersions;
import org.cometgui.install.registry.ArtefactRecord;
import org.cometgui.tools.process.ProcessService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * An install in the Tool Manager section reaches the Run and Percolator sections of the launched
 * application <em>after a garbage collection</em> -- the regression test of Phase 14's defect E3.
 *
 * <p><strong>The defect.</strong> {@code CometGuiApplication.start} tells the Run, Percolator,
 * Visualisation and Limelight sections about an install through a listener on the Tool Manager's
 * rows. {@code ToolManagerViewModel.rows()} builds a fresh unmodifiable view on each call, and that
 * view observes the list behind it <em>weakly</em>; the listener was registered on a view nobody
 * kept, so the first garbage collection silently removed it, and Run went on saying "Comet
 * 2026.03.0 is not installed" ten minutes after the Tool Manager showed it installed.
 *
 * <p><strong>Why it is deterministic.</strong> A collection is what decides whether the defect
 * shows, so this test does not leave it to chance: after the application is up it asks the virtual
 * machine for a full collection ({@code MemoryMXBean.gc()}, the API the platform documents as
 * equivalent to {@code System.gc()}, which SpotBugs reports as {@code DM_GC}) and waits, through
 * {@link Await} and on a bound, until a weakly held sentinel has been cleared -- proof that a
 * collection which clears weakly reachable objects has run. The view {@code start} registered on
 * was only ever weakly reachable, so by then, on the old code, the listener is gone.
 *
 * <p><strong>What is real.</strong> The application is the real {@code CometGuiApplication} with
 * its real {@code start}, its Tool Manager the product's {@code ManagedToolManager} as {@link
 * ToolManagerWiring} composes it over an empty cache, the shipped manifest, the real downloader,
 * checksum, extraction and probe (which <strong>executes</strong> Comet and Percolator). The one
 * substitution is the host each request goes to: {@link LoopbackArtefactServer} serves the bytes
 * upstream publishes from the gitignored mirror ({@code scratch/phase05/artefacts}), exactly as
 * {@code ToolManagerInstallUiTest} does -- so this class reads that mirror, outside the module, and
 * its absence is a failure, never a skip. No network. The installs are pressed on the rows' own
 * Install controls; every text is typed out here.
 */
@EnabledOnOs(
        value = OS.LINUX,
        disabledReason =
                "the artefacts are read from the project's Linux artefact mirror and the probe runs"
                        + " Linux binaries. Stated rather than left bare: this class is the"
                        + " regression test of defect E3, and a runner on which it silently did"
                        + " not run would be a check that cannot go red.")
class InstallReachesTheSectionsUiTest {

    private static final BuildIdentity BUILD =
            BuildIdentity.of("0.0.0-guitest", "unknown", Instant.parse("2026-10-22T00:00:00Z"));

    private static final HostPlatform LINUX =
            new HostPlatform(HostOperatingSystem.LINUX, HostArchitecture.X86_64);

    /** This project's own build host, hand-typed rather than read from the machine. */
    private static final HostRuntimeVersions DEBIAN_12 =
            new HostRuntimeVersions(
                    Optional.of(GlibcVersion.parse("2.36")),
                    Optional.of(GlibcVersion.parse("3.4.30")));

    private static final String COMET_ROW = "tool-row-comet-2026_03_0-1";

    private static final String PERCOLATOR_ROW = "tool-row-percolator-3_07_1-1";

    /** The Run section's reason while the default Comet is not installed. */
    private static final String COMET_MISSING =
            "Comet 2026.03.0 is not installed, and the parameters are for that release: install it"
                    + " in the Tool Manager section, or register a Comet 2026.03.0 already on this"
                    + " computer there.";

    /** The engine's first line when it cannot start. */
    private static final String CANNOT_START = "The workflow engine cannot start this search:\n";

    /** The Run section's reason while the default Percolator is not installed. */
    private static final String PERCOLATOR_MISSING =
            "Percolator 3.07.1, the default Percolator for the enabled downstream stages, is not"
                    + " installed: install it in the Tool Manager section, register a local"
                    + " Percolator binary in the Percolator section, or choose an installed build"
                    + " there.";

    /** What the Run section's engine says once both are installed: only the unchosen inputs. */
    private static final String RUN_AFTER =
            CANNOT_START
                    + "there is no spectrum file to search\n"
                    + "the database /some/path/db.fasta (database_name) does not exist or cannot be"
                    + " read";

    /** What the Percolator section says before anything is installed. */
    private static final String OFFERS_BEFORE =
            "3 Percolator builds are known on this computer, and 0 of them are installed and can"
                    + " run.";

    /** What the Percolator section says once 3.07.1 is installed. */
    private static final String OFFERS_AFTER =
            "3 Percolator builds are known on this computer, and 1 of them is installed and can"
                    + " run.";

    /** How long one install from the loopback mirror, probe included, may take. A bound. */
    private static final Duration INSTALL_BOUND = Duration.ofMinutes(2);

    /** How long the virtual machine may take to run a collection that clears the sentinel. */
    private static final Duration COLLECTION_BOUND = Duration.ofMinutes(1);

    @TempDir private static Path scratch;

    private static LoopbackArtefactServer served;

    private static ParameterEditorApp app;

    private static FxUiDriver driver;

    @BeforeAll
    static void launch() throws IOException {
        Path root = scratch.toRealPath();
        ArtefactRecord percolator = ArtefactMirror.record(ToolName.PERCOLATOR, "3.07.1", LINUX);
        served =
                new LoopbackArtefactServer()
                        .serve(
                                ArtefactMirror.record(ToolName.COMET, "2026.03.0", LINUX).url(),
                                ArtefactMirror.artefact("v2026.03.0__comet.linux.exe"))
                        .serve(
                                percolator.url(),
                                ArtefactMirror.artefact(
                                        "rel-3-07-01__percolator-noxml-ubuntu-portable.zip"))
                        .serve(
                                percolator.companions().get(0).url(),
                                ArtefactMirror.artefact(
                                        "rel-3-07-01__percolator-noxml-v3-07-linux-amd64.deb"));
        ProcessService processes = new ProcessService(Clock.systemUTC());
        ToolManager manager =
                ToolManagerWiring.toolManager(
                        LINUX,
                        DEBIAN_12,
                        root.resolve("cometgui-data"),
                        processes,
                        Clock.systemUTC(),
                        ToolManagerWiring.installThreads(),
                        served);
        app =
                ParameterEditorApp.launch(
                        BUILD,
                        processes,
                        new BoundedMessageLog(),
                        new RunWiring.Setup(() -> manager, root.resolve("project")));
        driver = new TestFxUiDriver(app.application());
    }

    @AfterAll
    static void stop() throws IOException {
        if (app != null) {
            app.stop();
        }
        if (served != null) {
            served.close();
        }
    }

    @Test
    @DisplayName(
            "E3: after a garbage collection, installing Comet and Percolator in the Tool Manager"
                    + " still reaches the Run and Percolator sections")
    void anInstallAfterACollectionReachesRunAndPercolator() {
        driver.clickOn("nav-run");
        assertEquals(
                CANNOT_START + COMET_MISSING + "\n" + PERCOLATOR_MISSING,
                RunSection.awaitEngineAnswer(driver),
                "Run, before the installs");
        driver.clickOn("nav-percolator");
        assertEquals(
                OFFERS_BEFORE,
                RunSection.awaitText(
                        driver, "percolator-offers", OFFERS_BEFORE::equals, RunSection.CHECK_BOUND),
                "the Percolator section, before the installs");

        collectGarbage();

        driver.clickOn("nav-tool-manager");
        install(COMET_ROW);
        install(PERCOLATOR_ROW);

        driver.clickOn("nav-run");
        assertEquals(
                RUN_AFTER,
                RunSection.awaitText(
                        driver, "run-engine", RUN_AFTER::equals, RunSection.CHECK_BOUND),
                "Run, read again after the installs: neither tool is named any more, and what"
                        + " remains is the inputs this test never chose");
        driver.clickOn("nav-percolator");
        assertEquals(
                OFFERS_AFTER,
                RunSection.awaitText(
                        driver, "percolator-offers", OFFERS_AFTER::equals, RunSection.CHECK_BOUND),
                "the Percolator section, read again after the installs");
    }

    /**
     * Presses a row's Install control and waits until the row, as the Tool Manager reads it again
     * after the install, says it is installed and where.
     *
     * <p>The wait looks the labels up afresh on every observation rather than listening to one
     * node: the Tool Manager's re-read can rebuild the rows, and a listener on a row it replaced
     * would wait on a label no longer in the window. Waiting for the installed path, which only the
     * re-read supplies, also means the next row is pressed in a list that has settled.
     *
     * @param row the row's identifier
     */
    private static void install(String row) {
        // The Tool Manager re-reads its rows after the previous install, and a row's Install
        // button can be briefly not visible while that happens; under load the click once landed
        // in that window ("no nodes were visible"). Wait on the observable state instead.
        Await.until(
                row + "'s Install control to be shown and enabled",
                () ->
                        driver.exists(row + "-install")
                                && driver.isVisible(row + "-install")
                                && !driver.isDisabled(row + "-install"),
                Boolean::booleanValue,
                INSTALL_BOUND);
        driver.clickOn(row + "-install");
        Await.until(
                row + " to be read again as installed, with its installed path",
                () -> driver.textOf(row + "-state") + " | " + driver.textOf(row + "-path"),
                shown -> shown.startsWith("CometGUI-managed: installed | Installed at: "),
                INSTALL_BOUND);
    }

    /**
     * Has the virtual machine run a collection that clears weakly reachable objects, and waits on a
     * bound until a weakly held sentinel shows that it did.
     */
    private static void collectGarbage() {
        WeakReference<Object> sentinel = new WeakReference<>(new Object());
        Await.until(
                "a garbage collection to clear a weakly held sentinel",
                () -> {
                    ManagementFactory.getMemoryMXBean().gc();
                    return sentinel.get() == null;
                },
                Boolean::booleanValue,
                COLLECTION_BOUND);
    }
}
