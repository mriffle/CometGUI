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
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import javafx.stage.Stage;
import org.cometgui.app.config.ToolManagerWiring;
import org.cometgui.app.testing.ArtefactMirror;
import org.cometgui.app.testing.LoopbackArtefactServer;
import org.cometgui.app.testing.RecordingProcessRunner;
import org.cometgui.app.testing.ShownToolManager;
import org.cometgui.domain.platform.GlibcVersion;
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
import org.cometgui.ui.controls.UiIds;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.api.FxToolkit;

/**
 * The 103 407 417-byte transfer the phase document singles out: cancelled part way through the Tool
 * Manager's own Cancel control, and then started again and allowed to finish.
 *
 * <blockquote>
 *
 * <p>{@code phases/PHASE-05-tool-registry.rst}, <em>Risks and notes</em>: "PDV is a ~99 MB
 * download. Test the cancellation and restart path deliberately."
 * </blockquote>
 *
 * <h2>The second attempt starts from zero, and that is the product's decision</h2>
 *
 * <p>Unit 3 recorded it in as many words: <strong>cancellation deletes the partial file</strong>,
 * so resume survives a <em>failure</em> and not a cancellation. A test that expected a resumed byte
 * count here would be asserting something the product deliberately does not do, so what is asserted
 * instead is the evidence for the decision: the second request carries <strong>no {@code Range}
 * header</strong>, the second attempt's smallest reported byte count is a first chunk rather than
 * the offset the first attempt reached, and between the two attempts the cache holds no file at
 * all.
 *
 * <h2>Cancelling is not failing</h2>
 *
 * <p>Unit 8 fixed a cancellation that reported {@code FAILED} and graded the repair inside a
 * transfer. This is the same claim one layer up, on the control a user presses: the row shows
 * {@code Cancelled}, the state returns to <em>not installed</em> rather than <em>the last install
 * attempt did not succeed</em>, and the Install control becomes available again.
 *
 * <h2>The two attempts, measured on this build</h2>
 *
 * <p>The cancelled attempt showed <strong>20 004 761</strong> bytes -- the first chunk boundary
 * past the 20 000 000 the Cancel control was pressed at -- and the attempt that finished showed
 * every count from <strong>0</strong> to <strong>103 407 417</strong>. Neither request carried a
 * {@code Range} header. The whole test takes about 4.6 seconds, which includes 123 MB of loopback
 * transfer and a 222-entry archive expanded once. No process is launched for PDV at any point,
 * which {@code ToolManagerInstallUiTest} explains.
 */
@EnabledOnOs(
        value = OS.LINUX,
        disabledReason =
                "the artefact mirror and the installed-path expectations here are this phase's"
                        + " Linux ones; stated rather than left bare, so that a runner on which"
                        + " this does not run says so.")
class PdvCancelAndRestartUiTest {

    private static final HostPlatform LINUX =
            new HostPlatform(HostOperatingSystem.LINUX, HostArchitecture.X86_64);

    private static final HostRuntimeVersions DEBIAN_12 =
            new HostRuntimeVersions(
                    Optional.of(GlibcVersion.parse("2.36")),
                    Optional.of(GlibcVersion.parse("3.4.30")));

    private static final String PDV_ZIP = "v2.7.0__PDV-2.7.0.zip";

    private static final String PDV_ROW = "pdv-2_7_0-1";

    /** What upstream publishes, hand-typed from the manifest rather than read from the file. */
    private static final long PDV_BYTES = 103_407_417L;

    /**
     * How much of the transfer is allowed through before the Cancel control is pressed.
     *
     * <p>Far enough in that the row is certainly showing {@code INSTALLING} with a live handle --
     * the Cancel control is disabled until it is, and this test refuses to press a disabled control
     * rather than quietly doing nothing -- and far short of the end.
     */
    private static final long CANCEL_AFTER_BYTES = 20_000_000L;

    @TempDir private Path temporary;

    private static Stage window;

    @BeforeAll
    static void startTheToolkit() throws TimeoutException {
        window = FxToolkit.registerPrimaryStage();
    }

    @Test
    @DisplayName("PDV is cancelled part way through the interface and then installed to completion")
    void pdvIsCancelledPartWayThroughAndThenInstalled() throws IOException, InterruptedException {
        ArtefactRecord pdv = ArtefactMirror.record(ToolName.PDV, "2.7.0", LINUX);
        Path cacheRoot = temporary.resolve("cometgui-data");
        RecordingProcessRunner processes = RecordingProcessRunner.inFrontOf(Clock.systemUTC());
        try (LoopbackArtefactServer served =
                new LoopbackArtefactServer().serve(pdv.url(), ArtefactMirror.artefact(PDV_ZIP))) {
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
                assertEquals(
                        "Download: 103,407,417 bytes",
                        ui.textOf(UiIds.toolRowDownload(PDV_ROW)),
                        "the row says how large the transfer is before it starts");

                served.trippingAfter(pdv.url(), CANCEL_AFTER_BYTES, () -> pressCancel(ui));
                ui.pressInstall(PDV_ROW);
                InstallProgress cancelled = ui.awaitTerminal(PDV_ROW);
                long firstAttemptBytes = ui.mostBytesShownDownloading(PDV_ROW, 0);
                String progressAfterCancel = ui.textOf(UiIds.toolRowProgress(PDV_ROW));
                List<Path> leftInTheCache = filesUnder(cacheRoot);
                ToolCache cache = new ToolCache(cacheRoot, new StreamingHashService());
                InstallationState between = cache.verify(pdv).state();
                int reportsSoFar = ui.reportCount(PDV_ROW);

                assertAll(
                        "the cancelled attempt",
                        () ->
                                assertEquals(
                                        InstallPhase.CANCELLED,
                                        cancelled.phase(),
                                        "cancelling is not failing"),
                        () ->
                                assertEquals(
                                        "CometGUI-managed: not installed",
                                        ui.textOf(UiIds.toolRowState(PDV_ROW)),
                                        "the honest state is the one the build was in before the"
                                                + " user pressed Install"),
                        () ->
                                assertTrue(
                                        progressAfterCancel.startsWith("Cancelled: "),
                                        () ->
                                                "the row tells the user what happened, and read: \""
                                                        + progressAfterCancel
                                                        + "\""),
                        () ->
                                assertTrue(
                                        firstAttemptBytes >= CANCEL_AFTER_BYTES,
                                        () ->
                                                "the cancel was tripped after "
                                                        + CANCEL_AFTER_BYTES
                                                        + " bytes and the row had shown "
                                                        + firstAttemptBytes),
                        () ->
                                assertTrue(
                                        firstAttemptBytes < PDV_BYTES,
                                        () ->
                                                "it stopped part way: "
                                                        + firstAttemptBytes
                                                        + " of "
                                                        + PDV_BYTES),
                        () ->
                                assertTrue(
                                        served.trippedAt(pdv.url()).orElse(0L)
                                                >= CANCEL_AFTER_BYTES,
                                        () ->
                                                "the cancel was pressed inside the transfer, after"
                                                        + " the listener had seen "
                                                        + served.trippedAt(pdv.url())
                                                        + " bytes"),
                        () ->
                                assertEquals(
                                        List.of(
                                                cacheRoot.resolve(
                                                        "cache/locks/"
                                                                + "pdv__2.7__linux-x86-64.lock")),
                                        leftInTheCache,
                                        "R-TOOL-04: a cancelled install leaves nothing behind but"
                                                + " the R-TOOL-05 lock file -- no partial download,"
                                                + " no staging tree, no tool directory. That empty"
                                                + " lock is why the attempt below starts from"
                                                + " zero"),
                        () ->
                                assertEquals(
                                        0L,
                                        Files.size(
                                                cacheRoot.resolve(
                                                        "cache/locks/"
                                                                + "pdv__2.7__linux-x86-64.lock")),
                                        "and the lock file carries no payload: it is a file to take"
                                                + " a FileLock on, not a record of anything"),
                        () -> assertEquals(InstallationState.NOT_PRESENT, between),
                        () ->
                                assertTrue(
                                        ui.isEnabled(UiIds.toolRowInstall(PDV_ROW)),
                                        "and the user can start it again"),
                        () ->
                                assertFalse(
                                        ui.isEnabled(UiIds.toolRowCancel(PDV_ROW)),
                                        "with nothing left to cancel"));

                ui.pressInstall(PDV_ROW);
                InstallProgress done = ui.awaitTerminal(PDV_ROW);
                long secondAttemptBytes = ui.mostBytesShownDownloading(PDV_ROW, reportsSoFar);
                long secondAttemptFirstBytes = ui.leastBytesShownDownloading(PDV_ROW, reportsSoFar);

                assertAll(
                        "the attempt that finished",
                        () -> assertEquals(InstallPhase.DONE, done.phase()),
                        () ->
                                assertEquals(
                                        PDV_BYTES,
                                        secondAttemptBytes,
                                        "the whole artefact moved again, and the row showed it"),
                        () ->
                                assertTrue(
                                        secondAttemptFirstBytes < CANCEL_AFTER_BYTES,
                                        () ->
                                                "the second attempt began at "
                                                        + secondAttemptFirstBytes
                                                        + " bytes, which is a first chunk rather"
                                                        + " than the "
                                                        + firstAttemptBytes
                                                        + " the cancelled one reached"),
                        () ->
                                assertEquals(
                                        List.of(Optional.empty(), Optional.empty()),
                                        served.ranges(),
                                        "two requests, neither of them ranged: the partial file was"
                                                + " deleted, so there was nothing to resume from."
                                                + " Resume survives a failure, not a cancellation"),
                        () ->
                                assertEquals(
                                        List.of(pdv.url(), pdv.url()),
                                        served.requested(),
                                        "both attempts fetched the URL the manifest pins"),
                        () ->
                                assertEquals(
                                        "CometGUI-managed: installed",
                                        ui.textOf(UiIds.toolRowState(PDV_ROW))),
                        () ->
                                assertEquals(
                                        "Installed at: "
                                                + cacheRoot.resolve(
                                                        "tools/pdv/2.7/linux-x86-64/"
                                                                + "PDV-2.7.0/PDV-2.7.0.jar"),
                                        ui.textOf(UiIds.toolRowPath(PDV_ROW))),
                        () ->
                                assertTrue(
                                        Files.isRegularFile(
                                                cacheRoot.resolve(
                                                        "tools/pdv/2.7/linux-x86-64/"
                                                                + "PDV-2.7.0/PDV-2.7.0.jar")),
                                        "and the jar is really there"),
                        () ->
                                assertEquals(
                                        List.of(),
                                        processes.launched(),
                                        "PDV is identified from the jar's own manifest, so"
                                                + " installing it starts no process at all"));
            }
        }
    }

    /**
     * Presses the row's Cancel control, from the thread the transfer is running on.
     *
     * <p>{@link ShownToolManager#pressCancel} hops onto the interface thread and refuses a disabled
     * control, so this is the user's click and not a back door into the handle.
     */
    private static void pressCancel(ShownToolManager ui) {
        try {
            ui.pressCancel(PDV_ROW);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while pressing Cancel", interrupted);
        }
    }

    /** Every regular file under a directory, or an empty list if it does not exist. */
    private static List<Path> filesUnder(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> tree = Files.walk(root)) {
            return tree.filter(Files::isRegularFile).sorted().toList();
        } catch (UncheckedIOException unreadable) {
            throw new IOException("cannot walk " + root, unreadable);
        }
    }
}
