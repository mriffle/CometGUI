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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import javafx.stage.Stage;
import org.cometgui.app.config.ToolManagerWiring;
import org.cometgui.app.testing.ArtefactMirror;
import org.cometgui.app.testing.LoopbackArtefactServer;
import org.cometgui.app.testing.RecordingProcessRunner;
import org.cometgui.app.testing.ShownToolManager;
import org.cometgui.app.uidriver.FxUiDriver;
import org.cometgui.domain.platform.GlibcVersion;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.domain.tools.HostArchitecture;
import org.cometgui.domain.tools.HostOperatingSystem;
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.domain.tools.InstallPhase;
import org.cometgui.domain.tools.InstallProgress;
import org.cometgui.domain.tools.ToolManager;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.install.cache.InstallationState;
import org.cometgui.install.cache.ToolCache;
import org.cometgui.install.probe.HostRuntimeVersions;
import org.cometgui.install.registry.ArtefactRecord;
import org.cometgui.provenance.hashing.StreamingHashService;
import org.cometgui.tools.api.ToolRunOutcome;
import org.cometgui.tools.api.ToolRunner;
import org.cometgui.tools.percolator.SyntheticPin;
import org.cometgui.tools.process.ProcessService;
import org.cometgui.ui.controls.UiIds;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.api.FxToolkit;

/**
 * Phase 05 exit-gate items 1 and 2, driven through the Tool Manager section itself.
 *
 * <blockquote>
 *
 * <p>1. From an empty cache, the application installs Comet, an XML-capable Percolator where one
 * exists for the platform, PDV and the converter, and probes each successfully -- driven through
 * the Tool Manager UI, not from a test helper.
 *
 * <p>2. A corrupted download is rejected and the tool is never executed; the test asserts no
 * process was launched.
 * </blockquote>
 *
 * <h2>What is real here, and the one thing that is not</h2>
 *
 * <p>The window is the product's own {@code ShellView} with its Tool Manager section; the port
 * behind it is {@code ManagedToolManager} as {@link ToolManagerWiring} composes it; the manifest is
 * the shipped one; the cache is an empty temporary directory; the bytes are the ones upstream
 * really publishes, taken from the gitignored mirror; the transfer is the real {@code
 * HttpDownloader} through the real {@code VerifiedDownloader}; the SHA-256, the extraction, the
 * atomic move and the completion marker are the product's; and the probe is the real {@code
 * StagedToolProbe}, which <strong>executes Comet and Percolator and reads the two JARs</strong>. An
 * install is started by pressing the row's Install control.
 *
 * <p>The one substitution is the <em>host</em> each request goes to: {@link LoopbackArtefactServer}
 * rewrites it to a loopback literal and serves the mirrored file for the path the manifest's URL
 * names. {@code UpstreamInstallUiTest} is the same interface driven against the real GitHub URLs,
 * opt-in.
 *
 * <h2>What it costs, measured on this build</h2>
 *
 * <p>Measured at Phase 05, when gate item 1's Comet was 2026.02.2: the four installs took
 * <strong>275 ms</strong> (Comet, 7 014 400 bytes; the default 2026.03.0 is 7 077 008), <strong>1
 * 412 ms</strong> (Percolator 3.07.1: two transfers totalling 2 798 963 bytes, plus a real binary
 * run over a 64-plus-64-row synthetic PIN, twice), <strong>2 200 ms</strong> (PDV: 103 407 417
 * bytes transferred and a 222-entry archive expanded) and <strong>653 ms</strong> (the converter,
 * whose identity is a second virtual machine). The whole class runs in about nine seconds. Reading
 * the offered list once, with all four installed, took <strong>353 ms on the JavaFX application
 * thread</strong> and launched three processes; the assertion at the end of the first test pins
 * that count, because it is the number that would grow silently.
 *
 * <h2>Why the assertions are on the rendered labels</h2>
 *
 * <p>A row that has reached {@code INSTALLED} in a view-model nobody drew is not what gate item 1
 * asks about. Each claim below is read off the control a scientist looks at, and the installed path
 * is then checked on disk against a location <strong>typed out here</strong> -- {@code
 * tools/<tool>/<normalised version>/<platform>/<the manifest's own executable path>} -- rather than
 * asked of the cache that created it.
 */
@EnabledOnOs(
        value = OS.LINUX,
        disabledReason =
                "the artefacts are read from this phase's Linux artefact mirror and the probe runs"
                        + " Linux binaries. Stated rather than left bare: this class IS gate items"
                        + " 1 and 2, and a runner on which it silently did not run would be a gate"
                        + " that cannot go red.")
class ToolManagerInstallUiTest {

    private static final HostPlatform LINUX =
            new HostPlatform(HostOperatingSystem.LINUX, HostArchitecture.X86_64);

    /** This project's own build host, hand-typed rather than read from the machine. */
    private static final HostRuntimeVersions DEBIAN_12 =
            new HostRuntimeVersions(
                    Optional.of(GlibcVersion.parse("2.36")),
                    Optional.of(GlibcVersion.parse("3.4.30")));

    /** The default Comet (D-010), which gate item 1 installs. */
    private static final String COMET_ZIP = "v2026.03.0__comet.linux.exe";

    /** The release the default superseded, still offered, which the mouse test installs. */
    private static final String OLDER_COMET_FILE = "v2026.02.2__comet.linux.exe";

    private static final String PERCOLATOR_ZIP =
            "rel-3-07-01__percolator-noxml-ubuntu-portable.zip";
    private static final String PERCOLATOR_DEB =
            "rel-3-07-01__percolator-noxml-v3-07-linux-amd64.deb";
    private static final String PDV_ZIP = "v2.7.0__PDV-2.7.0.zip";
    private static final String CONVERTER_JAR = "v2.8.1__cometPercolator2LimelightXML.jar";

    private static final String COMET_ROW = "comet-2026_03_0-1";
    private static final String OLDER_COMET_ROW = "comet-2026_02_2-1";
    private static final String PERCOLATOR_ROW = "percolator-3_07_1-1";
    private static final String OLDER_PERCOLATOR_ROW = "percolator-3_06_5-1";
    private static final String PDV_ROW = "pdv-2_7_0-1";
    private static final String CONVERTER_ROW = "limelight-converter-2_8_1-1";

    /** The four builds gate item 1 names, in the order this test installs them. */
    private static final List<String> THE_FOUR =
            List.of(COMET_ROW, PERCOLATOR_ROW, PDV_ROW, CONVERTER_ROW);

    /**
     * Where each build lands, relative to the cache root.
     *
     * <p>Typed out, not asked of {@code ToolCache}. The middle component is the
     * <strong>normalised</strong> version, which {@code ToolVersion} documents as "the numeric
     * components, most significant first, with trailing zero components dropped": {@code 3.07.1}
     * installs under {@code 3.7.1}, {@code 2026.02.2} under {@code 2026.2.2}, and <strong>{@code
     * 2.7.0} under {@code 2.7}</strong> -- as the default Comet, {@code 2026.03.0}, installs under
     * {@code 2026.3}. The rule exists because {@code ToolVersion.equals} is numeric and two
     * spellings of one version must not become two directories. Every row still reads upstream's
     * own spelling, and {@link #theFourManagedToolsInstallFromAnEmptyCache} asserts both.
     */
    private static final Map<String, String> INSTALLED_AT =
            Map.of(
                    COMET_ROW, "tools/comet/2026.3/linux-x86-64/bin/comet",
                    PERCOLATOR_ROW, "tools/percolator/3.7.1/linux-x86-64/bin/percolator",
                    PDV_ROW, "tools/pdv/2.7/linux-x86-64/PDV-2.7.0/PDV-2.7.0.jar",
                    CONVERTER_ROW,
                            "tools/limelight-converter/2.8.1/linux-x86-64/"
                                    + "cometPercolator2LimelightXML.jar");

    /**
     * The two builds whose installed file is executed directly, and therefore gets the {@code
     * R-PLAT-05} executable bit. PDV and the converter are JARs: an argument to a launcher rather
     * than the launcher, so the manifest marks neither executable and neither is chmodded.
     */
    private static final Set<String> CARRIES_THE_EXECUTABLE_BIT = Set.of(COMET_ROW, PERCOLATOR_ROW);

    /** The namespace Percolator's own XML output carries, hand-typed from unit 0's measurement. */
    private static final String POUT_NAMESPACE = "http://per-colator.com/percolator_out/15";

    @TempDir private Path temporary;

    private static Stage window;

    @BeforeAll
    static void startTheToolkit() throws TimeoutException {
        window = FxToolkit.registerPrimaryStage();
    }

    @Test
    @DisplayName(
            "gate 1: the four managed tools install and probe from an empty cache, pressed in"
                    + " the Tool Manager")
    void theFourManagedToolsInstallFromAnEmptyCache() throws IOException, InterruptedException {
        Path cacheRoot = temporary.resolve("cometgui-data");
        RecordingProcessRunner processes = RecordingProcessRunner.inFrontOf(Clock.systemUTC());
        Map<String, Long> elapsedMillis = new LinkedHashMap<>();
        try (LoopbackArtefactServer served = servingTheFour()) {
            ToolManager manager =
                    ToolManagerWiring.toolManager(
                            LINUX,
                            DEBIAN_12,
                            cacheRoot,
                            processes,
                            Clock.systemUTC(),
                            ToolManagerWiring.installThreads(),
                            served);
            try (ShownToolManager ui = ShownToolManager.showing(manager, window)) {
                assertTrue(
                        Files.notExists(cacheRoot),
                        "the cache starts empty, and stays so until something is"
                                + " installed: reading the offered list creates nothing");
                List<String> shownRows = ui.rowKeys();
                assertTrue(
                        shownRows.containsAll(THE_FOUR),
                        () -> "the section is showing " + shownRows);

                for (String row : THE_FOUR) {
                    assertTrue(
                            ui.isEnabled(UiIds.toolRowInstall(row)),
                            () ->
                                    row
                                            + "'s Install control is disabled before anything is"
                                            + " installed");
                    assertFalse(
                            ui.isEnabled(UiIds.toolRowCancel(row)),
                            () -> row + " offers Cancel with no install running");
                    long startedAt = System.nanoTime();
                    ui.pressInstall(row);
                    InstallProgress terminal = ui.awaitTerminal(row);
                    elapsedMillis.put(row, (System.nanoTime() - startedAt) / 1_000_000L);
                    assertEquals(
                            InstallPhase.DONE,
                            terminal.phase(),
                            () -> row + " did not finish: " + ui.phases(row));
                }

                List<Executable> claims = new ArrayList<>();
                for (String row : THE_FOUR) {
                    claims.add(() -> assertRowIsInstalled(ui, cacheRoot, row));
                }
                claims.add(
                        () ->
                                assertEquals(
                                        "CometGUI-managed: installed",
                                        ui.textOf(UiIds.toolRowState(COMET_ROW))));
                claims.add(
                        () ->
                                assertEquals(
                                        "CometGUI-managed: not installed",
                                        ui.textOf(UiIds.toolRowState(OLDER_PERCOLATOR_ROW)),
                                        "installing four builds touches no fifth one"));
                claims.add(
                        () ->
                                assertEquals(
                                        "CometGUI-managed: not installed",
                                        ui.textOf(UiIds.toolRowState(OLDER_COMET_ROW)),
                                        "and installing the default Comet touches no other"
                                                + " release of it"));
                claims.add(
                        () ->
                                assertEquals(
                                        COMET_ROW,
                                        shownRows.stream()
                                                .filter(key -> key.startsWith("comet-"))
                                                .findFirst()
                                                .orElseThrow(),
                                        "D-010: the default Comet, 2026.03.0, is the first Comet"
                                                + " row the window shows, and 2026.02.2 follows"
                                                + " it: "
                                                + shownRows));
                claims.add(
                        () ->
                                assertEquals(
                                        capabilitiesOfComet(),
                                        ui.textOf(UiIds.toolRowCapabilities(COMET_ROW))));
                claims.add(
                        () ->
                                assertEquals(
                                        capabilitiesOfPercolator(),
                                        ui.textOf(UiIds.toolRowCapabilities(PERCOLATOR_ROW))));
                claims.add(
                        () ->
                                assertEquals(
                                        "Capabilities: none declared",
                                        ui.textOf(UiIds.toolRowCapabilities(PDV_ROW)),
                                        "no constant of ToolCapability belongs to PDV, so nothing"
                                                + " about it could have been observed: an empty"
                                                + " set here is the complete answer, not a missing"
                                                + " one"));
                claims.add(
                        () ->
                                assertEquals(
                                        "Capabilities: none declared",
                                        ui.textOf(UiIds.toolRowCapabilities(CONVERTER_ROW))));
                claims.add(
                        () ->
                                assertEquals(
                                        "percolator 3.07.1",
                                        ui.textOf(UiIds.toolRowName(PERCOLATOR_ROW)),
                                        "upstream's spelling, not the normalised directory name"));
                claims.add(() -> assertCometWasProbedByRunningIt(processes));
                claims.add(() -> assertPercolatorWasProbedFunctionally(processes));
                claims.add(() -> assertTheTwoJarsWereIdentifiedFromTheArtefact(processes));
                claims.add(
                        () ->
                                assertEquals(
                                        urlsOfTheFour(),
                                        served.requested(),
                                        "every transfer went to the URL the manifest pins, and the"
                                                + " companion .deb the two XSDs come out of is one"
                                                + " of them"));
                assertAll(claims);

                assertPercolatorReallyWritesTheXmlItsRowClaims(
                        cacheRoot.resolve(INSTALLED_AT.get(PERCOLATOR_ROW)));

                /*
                 * MEASURED, NOT ASSUMED, and pinned so that it cannot grow unnoticed.  Reading the
                 * offered list re-hashes every installed entry AND asks each installed build
                 * whether it still starts, which is three process launches once these four are
                 * installed -- Comet's banner, Percolator's banner and a whole second virtual
                 * machine for the converter.  PDV's is the fourth build and costs no process.  All
                 * of it happens on the JavaFX application thread, because that is where the port
                 * promises it is safe to call offers() from, and the elapsed time is in this
                 * message for the phase record.
                 */
                int launchesBefore = processes.launched().size();
                long readAgain = System.nanoTime();
                ui.refresh();
                long refreshMillis = (System.nanoTime() - readAgain) / 1_000_000L;
                assertEquals(
                        3,
                        processes.launched().size() - launchesBefore,
                        () ->
                                "one read of the offered list, with four builds installed, took "
                                        + refreshMillis
                                        + " ms on the interface thread and launched "
                                        + (processes.launched().size() - launchesBefore)
                                        + " process(es): "
                                        + processes
                                                .launchedByFileName()
                                                .subList(
                                                        launchesBefore, processes.launched().size())
                                        + ". The four installs took "
                                        + elapsedMillis
                                        + " ms.");
            }
        }
    }

    @Test
    @DisplayName(
            "gate 2: a corrupted artefact is rejected, no process is launched, and the cache"
                    + " stays empty")
    void aCorruptedArtefactIsRejectedAndNothingIsExecuted()
            throws IOException, InterruptedException {
        ArtefactRecord record = ArtefactMirror.record(ToolName.PERCOLATOR, "3.07.1", LINUX);
        byte[] published = Files.readAllBytes(ArtefactMirror.artefact(PERCOLATOR_ZIP));
        byte[] damaged = published.clone();
        damaged[damaged.length / 2] ^= 0x5a;
        Path corrupted = temporary.resolve("corrupted-percolator.zip");
        Files.write(corrupted, damaged);
        Path cacheRoot = temporary.resolve("cometgui-data");
        RecordingProcessRunner processes = RecordingProcessRunner.inFrontOf(Clock.systemUTC());
        try (LoopbackArtefactServer served =
                new LoopbackArtefactServer()
                        .serve(record.url(), corrupted)
                        .serve(
                                record.companions().get(0).url(),
                                ArtefactMirror.artefact(PERCOLATOR_DEB))) {
            ToolManager manager =
                    ToolManagerWiring.toolManager(
                            LINUX,
                            DEBIAN_12,
                            cacheRoot,
                            processes,
                            Clock.systemUTC(),
                            ToolManagerWiring.installThreads(),
                            served);
            try (ShownToolManager ui = ShownToolManager.showing(manager, window)) {
                ui.pressInstall(PERCOLATOR_ROW);
                InstallProgress terminal = ui.awaitTerminal(PERCOLATOR_ROW);

                ToolCache cache = new ToolCache(cacheRoot, new StreamingHashService());
                assertAll(
                        () ->
                                assertEquals(
                                        List.of("DOWNLOADING", "FAILED"),
                                        ui.phases(PERCOLATOR_ROW),
                                        "R-SEC-02 is verify before you execute, so WHERE"
                                                + " the artefact was rejected is the claim."
                                                + " The transfer step verifies the SHA-256"
                                                + " itself, so the install never reached step"
                                                + " 2's VERIFYING and never reached EXTRACTING"
                                                + " at all. Without this assertion the test"
                                                + " stays green with the SHA-256 comparison"
                                                + " deleted -- measured, not supposed --"
                                                + " because a zip with a flipped byte also"
                                                + " fails to extract, and that is a different"
                                                + " guard"),
                        () ->
                                assertEquals(
                                        published.length,
                                        Files.size(corrupted),
                                        "the corruption kept the length, so only the SHA-256 could"
                                                + " have caught it"),
                        () ->
                                assertEquals(
                                        1,
                                        bytesDiffering(published, damaged),
                                        "exactly one byte differs from what upstream publishes"),
                        () -> assertEquals(InstallPhase.FAILED, terminal.phase()),
                        () ->
                                assertEquals(
                                        List.of(),
                                        processes.launched(),
                                        "R-SEC-02: nothing was executed. This is the"
                                                + " record of a process runner that really"
                                                + " starts processes -- it starts several in"
                                                + " the install above -- so an empty record is"
                                                + " a fact about the product"),
                        () ->
                                assertEquals(
                                        "CometGUI-managed: the last install attempt did not"
                                                + " succeed",
                                        ui.textOf(UiIds.toolRowState(PERCOLATOR_ROW))),
                        () ->
                                assertFalse(
                                        ui.isShowing(UiIds.toolRowPath(PERCOLATOR_ROW)),
                                        "a row that failed names no location"),
                        () ->
                                assertEquals(
                                        InstallationState.NOT_PRESENT,
                                        cache.verify(record).state(),
                                        "R-TOOL-04: the cache reports the tool not installed"),
                        () ->
                                assertTrue(
                                        ui.isEnabled(UiIds.toolRowInstall(PERCOLATOR_ROW)),
                                        "and the user can try again"));
            }
        }
    }

    @Test
    @DisplayName("the Install control starts a real install when it is clicked with the mouse")
    void theInstallControlIsReachableByMouse() throws IOException, InterruptedException {
        Path cacheRoot = temporary.resolve("cometgui-data");
        RecordingProcessRunner processes = RecordingProcessRunner.inFrontOf(Clock.systemUTC());
        ArtefactRecord comet = ArtefactMirror.record(ToolName.COMET, "2026.02.2", LINUX);
        try (LoopbackArtefactServer served =
                new LoopbackArtefactServer()
                        .serve(comet.url(), ArtefactMirror.artefact(OLDER_COMET_FILE))) {
            ToolManager manager =
                    ToolManagerWiring.toolManager(
                            LINUX,
                            DEBIAN_12,
                            cacheRoot,
                            processes,
                            Clock.systemUTC(),
                            ToolManagerWiring.installThreads(),
                            served);
            try (ShownToolManager ui = ShownToolManager.showing(manager, window)) {
                FxUiDriver driver = ui.syntheticInput();
                ui.watch(OLDER_COMET_ROW);

                driver.clickOn(UiIds.toolRowInstall(OLDER_COMET_ROW));
                InstallProgress terminal = ui.awaitTerminal(OLDER_COMET_ROW);

                assertAll(
                        () ->
                                assertEquals(
                                        InstallPhase.DONE,
                                        terminal.phase(),
                                        () ->
                                                "the phases shown were "
                                                        + ui.phases(OLDER_COMET_ROW)),
                        () ->
                                assertEquals(
                                        "CometGUI-managed: installed",
                                        ui.textOf(UiIds.toolRowState(OLDER_COMET_ROW)),
                                        "a synthetic mouse click on the control a user would press,"
                                                + " through "
                                                + driver),
                        () ->
                                assertEquals(
                                        "Install comet 2026.02.2",
                                        driver.accessibleTextOf(
                                                UiIds.toolRowInstall(OLDER_COMET_ROW)),
                                        "and a screen reader user reaches the same control"));
            }
        }
    }

    /** Serves the four builds gate item 1 names, and Percolator's companion package. */
    private static LoopbackArtefactServer servingTheFour() throws IOException {
        ArtefactRecord percolator = ArtefactMirror.record(ToolName.PERCOLATOR, "3.07.1", LINUX);
        return new LoopbackArtefactServer()
                .serve(
                        ArtefactMirror.record(ToolName.COMET, "2026.03.0", LINUX).url(),
                        ArtefactMirror.artefact(COMET_ZIP))
                .serve(percolator.url(), ArtefactMirror.artefact(PERCOLATOR_ZIP))
                .serve(
                        percolator.companions().get(0).url(),
                        ArtefactMirror.artefact(PERCOLATOR_DEB))
                .serve(
                        ArtefactMirror.record(ToolName.PDV, "2.7.0", LINUX).url(),
                        ArtefactMirror.artefact(PDV_ZIP))
                .serve(
                        ArtefactMirror.record(ToolName.LIMELIGHT_CONVERTER, "2.8.1", LINUX).url(),
                        ArtefactMirror.artefact(CONVERTER_JAR));
    }

    private static List<URI> urlsOfTheFour() throws IOException {
        ArtefactRecord percolator = ArtefactMirror.record(ToolName.PERCOLATOR, "3.07.1", LINUX);
        return List.of(
                ArtefactMirror.record(ToolName.COMET, "2026.03.0", LINUX).url(),
                percolator.url(),
                percolator.companions().get(0).url(),
                ArtefactMirror.record(ToolName.PDV, "2.7.0", LINUX).url(),
                ArtefactMirror.record(ToolName.LIMELIGHT_CONVERTER, "2.8.1", LINUX).url());
    }

    /** What Comet's own capability probe established, in the order the row renders it. */
    private static String capabilitiesOfComet() {
        return "Capabilities: PEPXML_OUTPUT (observed-by-execution), PIN_OUTPUT"
                + " (observed-by-execution), COMPLETE_PARAMS_QUERY (observed-by-execution)";
    }

    /** What Percolator's functional probe established, in the order the row renders it. */
    private static String capabilitiesOfPercolator() {
        return "Capabilities: XML_OUTPUT (observed-by-execution), XML_DECOY_OUTPUT"
                + " (observed-by-execution)";
    }

    private void assertRowIsInstalled(ShownToolManager ui, Path cacheRoot, String row)
            throws IOException, InterruptedException {
        Path expected = cacheRoot.resolve(INSTALLED_AT.get(row));
        boolean shouldBeExecutable = CARRIES_THE_EXECUTABLE_BIT.contains(row);
        assertAll(
                "the row " + row,
                () ->
                        assertEquals(
                                "CometGUI-managed: installed", ui.textOf(UiIds.toolRowState(row))),
                () ->
                        assertEquals(
                                Optional.of(expected),
                                ui.offer(row).installedPath(),
                                "the installed location, at the path typed out in this test"),
                () ->
                        assertEquals(
                                "Installed at: " + expected,
                                ui.textOf(UiIds.toolRowPath(row)),
                                "and the row tells the user where it is"),
                () -> assertTrue(Files.isRegularFile(expected), () -> expected + " must exist"),
                () ->
                        assertEquals(
                                shouldBeExecutable,
                                Files.isExecutable(expected),
                                "R-PLAT-05 sets the executable bit on the files the manifest marks"
                                        + " executable, and a JAR is an argument to a launcher"
                                        + " rather than the launcher"),
                () ->
                        assertFalse(
                                ui.isEnabled(UiIds.toolRowInstall(row)),
                                "an installed build needs nothing"),
                () ->
                        assertFalse(
                                ui.isShowing(UiIds.toolRowDiagnostic(row)),
                                "nothing was refused by the loader"));
    }

    /**
     * Proves the Percolator row's claim independently of the probe that made it: run the installed
     * binary over a fresh 64-plus-64-row synthetic PIN and read the document it writes.
     */
    private void assertPercolatorReallyWritesTheXmlItsRowClaims(Path installed) throws IOException {
        Path work = Files.createDirectories(temporary.resolve("percolator-evidence"));
        Path pin = SyntheticPin.writeForCapabilityProbe(work);
        Path written = work.resolve("targets.pout.xml");
        ToolRunOutcome outcome =
                new ToolRunner(new ProcessService(Clock.systemUTC()), Duration.ofSeconds(120))
                        .run(
                                new ToolCommand(
                                        List.of(
                                                installed.toString(),
                                                "-X",
                                                written.toString(),
                                                pin.toString()),
                                        work,
                                        Map.of()));
        String document = Files.readString(written);
        assertAll(
                "the binary the Tool Manager installed, run again by this test",
                () -> assertEquals(OptionalInt.of(0), outcome.exitCode()),
                () ->
                        assertTrue(
                                document.contains("<percolator_output"),
                                "an 8-plus-8 fixture leaves this file existing and EMPTY, which is"
                                        + " why \"the output file exists\" is not the condition"),
                () ->
                        assertTrue(
                                document.contains(POUT_NAMESPACE),
                                "the percolator_out/15"
                                        + " namespace, hand-typed here from unit 0's measurement"),
                () ->
                        assertEquals(
                                SyntheticPin.PROBE_TARGET_ROWS,
                                occurrencesOf(document, "<psm "),
                                "one PSM per target row, which is what makes this a functional"
                                        + " verdict rather than a reading of the help text"));
    }

    /**
     * Comet's three stages, by the argument arrays the application really launched.
     *
     * <p>Stage 1 and 2 are one run: {@code -h} makes the binary print its banner, which is what
     * "does it start" and "what is it" are both answered from. The capability stage is two more
     * runs, {@code -p} and {@code -q}, and {@code COMPLETE_PARAMS_QUERY} is the difference between
     * the two listings -- so this tool's capabilities are also established by running it.
     *
     * <p>Every later {@code comet} run is the banner again, because reading the offered list asks
     * each installed build whether it still starts ({@code R-TOOL-06}).
     */
    private static void assertCometWasProbedByRunningIt(RecordingProcessRunner processes) {
        List<List<String>> runs = runsOf(processes, "comet");
        assertTrue(runs.size() >= 3, () -> "comet was run " + runs.size() + " time(s): " + runs);
        assertAll(
                () -> assertEquals(List.of("comet", "-h"), runs.get(0), "the version banner"),
                () ->
                        assertEquals(
                                List.of("comet", "-p"),
                                runs.get(1),
                                "the default parameter listing"),
                () ->
                        assertEquals(
                                List.of("comet", "-q"),
                                runs.get(2),
                                "and the complete one, whose extra parameters are what"
                                        + " COMPLETE_PARAMS_QUERY means"),
                () ->
                        assertEquals(
                                List.of(),
                                runs.subList(3, runs.size()).stream()
                                        .filter(argv -> !argv.equals(List.of("comet", "-h")))
                                        .toList(),
                                "and every later run is the banner, which is the offered-set gate"
                                        + " asking whether the installed build still starts"));
    }

    /**
     * Percolator's capability set, by the argument arrays the application really launched.
     *
     * <p>This is the acceptance condition {@code R-PERC-02} turns into a rule: the {@code noxml}
     * and XML-capable builds of 3.07.1 print <strong>byte-identical</strong> help text, both
     * listing {@code --xmloutput}, so a verdict taken from {@code --help} discriminates nothing.
     * The first run here is the banner, and the capability verdict comes from two further runs that
     * write documents -- {@code -X} for targets and {@code -X -Z} for decoys, over one synthetic
     * PIN. {@link #assertPercolatorReallyWritesTheXmlItsRowClaims} then reads such a document.
     */
    private static void assertPercolatorWasProbedFunctionally(RecordingProcessRunner processes) {
        List<List<String>> runs = runsOf(processes, "percolator");
        assertTrue(
                runs.size() >= 3, () -> "percolator was run " + runs.size() + " time(s): " + runs);
        List<String> targets = runs.get(1);
        List<String> decoys = runs.get(2);
        assertAll(
                () ->
                        assertEquals(
                                List.of("percolator", "--help"),
                                runs.get(0),
                                "the version banner, which arrives on standard error"),
                () ->
                        assertEquals(
                                "-X", targets.get(1), () -> "the first capability run: " + targets),
                () ->
                        assertTrue(
                                targets.get(2).endsWith(".pout.xml"),
                                () -> "which names the document to write: " + targets),
                () ->
                        assertFalse(
                                targets.contains("-Z"),
                                () -> "and asks for targets alone: " + targets),
                () ->
                        assertEquals(
                                "-X", decoys.get(1), () -> "the second capability run: " + decoys),
                () ->
                        assertTrue(
                                decoys.contains("-Z"),
                                () -> "XML_DECOY_OUTPUT is a separate run: " + decoys),
                () ->
                        assertEquals(
                                targets.get(targets.size() - 1),
                                decoys.get(decoys.size() - 1),
                                "both over the same synthetic PIN"),
                () ->
                        assertFalse(
                                targets.contains("--help") || decoys.contains("--help"),
                                "and neither capability verdict came from the help text"),
                () ->
                        assertEquals(
                                List.of(),
                                runs.subList(3, runs.size()).stream()
                                        .filter(
                                                argv ->
                                                        !argv.equals(
                                                                List.of("percolator", "--help")))
                                        .toList(),
                                "every later run is the banner: the offered-set gate, not a second"
                                        + " capability verdict"));
    }

    /**
     * The two JAR tools, which are identified from the artefact rather than by running it.
     *
     * <p>PDV constructs a Swing frame before it reads its first argument, so on a machine with no
     * display no launch could answer anything and its version is read out of the jar's own
     * manifest: <strong>no process at all</strong>. The Limelight converter answers {@code
     * --version} and exits 0, so its identity is a real JVM launch.
     */
    private static void assertTheTwoJarsWereIdentifiedFromTheArtefact(
            RecordingProcessRunner processes) {
        List<List<String>> jvmRuns = runsOf(processes, "java");
        assertTrue(jvmRuns.size() >= 1, "the converter's identity is a JVM launch");
        assertAll(
                () ->
                        assertEquals(
                                List.of(),
                                processes.launched().stream()
                                        .filter(ToolManagerInstallUiTest::namesPdvsJar)
                                        .toList(),
                                "nothing was ever launched with PDV's jar on its command line"),
                () ->
                        assertEquals(
                                List.of(),
                                jvmRuns.stream()
                                        .filter(argv -> !isConverterVersionRun(argv))
                                        .toList(),
                                "and every JVM this application started was the converter answering"
                                        + " --version"));
    }

    private static boolean namesPdvsJar(List<String> argv) {
        return argv.stream().anyMatch(argument -> argument.contains("PDV-2.7.0.jar"));
    }

    private static boolean isConverterVersionRun(List<String> argv) {
        return argv.contains("-jar")
                && argv.stream().anyMatch(a -> a.endsWith("cometPercolator2LimelightXML.jar"))
                && "--version".equals(argv.get(argv.size() - 1));
    }

    /** Every launched argument array whose executable has this file name, in order. */
    private static List<List<String>> runsOf(RecordingProcessRunner processes, String fileName) {
        return processes.launchedByFileName().stream()
                .filter(argv -> fileName.equals(argv.get(0)))
                .toList();
    }

    private static int bytesDiffering(byte[] left, byte[] right) {
        int differing = 0;
        for (int index = 0; index < Math.min(left.length, right.length); index++) {
            if (left[index] != right[index]) {
                differing++;
            }
        }
        return differing + Math.abs(left.length - right.length);
    }

    private static int occurrencesOf(String text, String needle) {
        int count = 0;
        for (int at = text.indexOf(needle); at >= 0; at = text.indexOf(needle, at + 1)) {
            count++;
        }
        return count;
    }
}
