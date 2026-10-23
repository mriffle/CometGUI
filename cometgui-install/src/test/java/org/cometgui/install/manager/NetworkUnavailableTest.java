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

package org.cometgui.install.manager;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.cometgui.domain.tools.InstallPhase;
import org.cometgui.domain.tools.ToolInstallState;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.install.cache.InstallationState;
import org.cometgui.install.download.DownloadCancellation;
import org.cometgui.install.download.DownloadFailedException;
import org.cometgui.install.registry.ArtefactRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The specification's failure paths "network unavailable on first install" and "network unavailable
 * after tools are cached", at the Tool Manager port: the shipped manifest, the real cache,
 * download, verification and extraction, and a {@link SwitchableNetwork} whose "down" is a refused
 * connection through the real {@code HttpDownloader}.
 *
 * <p>What a scientist sees in the window is {@code NetworkUnavailableUiTest} in cometgui-app. The
 * reason a failed install carries is stated here at the one layer that has it -- {@code
 * ArtefactInstaller}'s exception -- because {@code ManagedToolManager} records only that the
 * attempt failed (its {@code runInstall} catches the exception, and {@code ToolRowViewModel}
 * documents that a failed row carries no reason). That the window cannot say why is reported by
 * Phase 14 unit 8a as a defect for the orchestrator to decide; nothing here asserts it either way.
 *
 * <p>Files read outside this module: the real artefacts in {@code scratch/phase05/artefacts}, as
 * every test of {@link ToolManagerFixtures} does.
 */
class NetworkUnavailableTest {

    private static final String PERCOLATOR_ZIP =
            "rel-3-07-01__percolator-noxml-ubuntu-portable.zip";

    private static final String PERCOLATOR_DEB =
            "rel-3-07-01__percolator-noxml-v3-07-linux-amd64.deb";

    private static final ToolVersion PERCOLATOR_3_07_1 = ToolVersion.parse("3.07.1");

    private static final ToolVersion PERCOLATOR_3_06_5 = ToolVersion.parse("3.06.5");

    @TempDir private Path temporary;

    private static ArtefactRecord percolator() throws IOException {
        return ToolManagerFixtures.record(ToolName.PERCOLATOR, "3.07.1", ToolManagerFixtures.LINUX);
    }

    private static ToolOffer rowFor(List<ToolOffer> offers, ToolVersion version) {
        return offers.stream()
                .filter(offer -> offer.tool() == ToolName.PERCOLATOR)
                .filter(offer -> offer.version().equals(version))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no percolator row in " + offers));
    }

    @Test
    @DisplayName(
            "network unavailable on the first install: FAILED after the one transfer it tried,"
                    + " nothing probed, nothing left in the cache, and Install offered again")
    void firstInstallWithoutANetwork() throws IOException, InterruptedException {
        ArtefactRecord record = percolator();
        Path root = temporary.resolve("cache");
        RecordingListener listener = new RecordingListener();
        try (SwitchableNetwork network = SwitchableNetwork.unavailable()) {
            ToolManagerHarness harness =
                    ToolManagerHarness.at(root)
                            .fetching(network)
                            .probing(FixedProbe.refusingToBeCalled())
                            .build();
            harness.manager().install(ToolName.PERCOLATOR, PERCOLATOR_3_07_1, listener);
            InstallPhase terminal = listener.awaitTerminal().phase();
            List<ToolOffer> offers = harness.manager().offers();
            assertAll(
                    () -> assertEquals(InstallPhase.FAILED, terminal),
                    () ->
                            assertEquals(
                                    List.of(InstallPhase.DOWNLOADING, InstallPhase.FAILED),
                                    listener.phases(),
                                    "it reached the transfer and went no further: no"
                                            + " verification, extraction or probe of bytes that"
                                            + " never arrived"),
                    () -> assertEquals(0L, listener.bytesDownloaded(), "and not one byte arrived"),
                    () ->
                            assertEquals(
                                    List.of(record.url()),
                                    network.askedForWhileDown(),
                                    "one attempt, at the URL the manifest pins: no companion"
                                            + " fetched after the primary failed, and no retry"
                                            + " loop"),
                    () ->
                            assertEquals(
                                    ToolInstallState.FAILED,
                                    rowFor(offers, PERCOLATOR_3_07_1).state(),
                                    "the row says the attempt failed -- the state from which"
                                            + " the Tool Manager offers Install again"),
                    () ->
                            assertEquals(
                                    ToolInstallState.NOT_INSTALLED,
                                    rowFor(offers, PERCOLATOR_3_06_5).state(),
                                    "and no other row changed"),
                    () ->
                            assertEquals(
                                    InstallationState.NOT_PRESENT,
                                    harness.verify(record).state(),
                                    "R-TOOL-04: nothing that could look installed"),
                    () ->
                            assertEquals(
                                    List.of(),
                                    harness.stagingDirectories(record),
                                    "and no staging directory left behind"),
                    () ->
                            assertEquals(
                                    List.of(),
                                    filesUnder(root.resolve("tools")),
                                    "no file at all under the tool directories"));
        }
    }

    @Test
    @DisplayName(
            "the failed transfer's own reason names the URL and the refused connection, at the"
                    + " installer that has it")
    void theReasonNamesTheUrlAndTheConnection() throws IOException {
        ArtefactRecord record = percolator();
        try (SwitchableNetwork network = SwitchableNetwork.unavailable()) {
            ToolManagerHarness harness =
                    ToolManagerHarness.at(temporary.resolve("cache"))
                            .fetching(network)
                            .probing(FixedProbe.refusingToBeCalled())
                            .build();
            RecordingListener listener = new RecordingListener();
            DownloadFailedException thrown =
                    assertThrows(
                            DownloadFailedException.class,
                            () ->
                                    harness.installer()
                                            .install(
                                                    record,
                                                    listener,
                                                    DownloadCancellation.never()));
            URI attempted =
                    URI.create("http://127.0.0.1:" + network.deadPort() + record.url().getPath());
            assertAll(
                    () ->
                            assertTrue(
                                    thrown.getMessage().contains(attempted.toString()),
                                    () -> "the URL the transfer went to: " + thrown.getMessage()),
                    () ->
                            assertTrue(
                                    thrown.getMessage().contains("java.net.ConnectException"),
                                    () -> "and what went wrong: " + thrown.getMessage()),
                    () ->
                            assertEquals(
                                    List.of(InstallPhase.DOWNLOADING, InstallPhase.FAILED),
                                    listener.phases()));
        }
    }

    @Test
    @DisplayName(
            "network unavailable after the tool is cached: still INSTALLED, in this session and"
                    + " in a new one, with not one fetch -- and the network really is down")
    void cachedToolsNeedNoNetwork() throws IOException, InterruptedException {
        ArtefactRecord record = percolator();
        Path root = temporary.resolve("cache");
        try (ServedArtefacts served =
                        new ServedArtefacts()
                                .serve(record.url(), ToolManagerFixtures.artefact(PERCOLATOR_ZIP))
                                .serve(
                                        record.companions().get(0).url(),
                                        ToolManagerFixtures.artefact(PERCOLATOR_DEB));
                SwitchableNetwork network = SwitchableNetwork.upUntilCut(served)) {
            ToolManagerHarness harness =
                    ToolManagerHarness.at(root)
                            .fetching(network)
                            .probing(FixedProbe.answering())
                            .build();
            RecordingListener installed = new RecordingListener();
            harness.manager().install(ToolName.PERCOLATOR, PERCOLATOR_3_07_1, installed);
            assertEquals(InstallPhase.DONE, installed.awaitTerminal().phase(), "online install");
            int fetchedOnline = served.requested().size();
            ToolOffer online = rowFor(harness.manager().offers(), PERCOLATOR_3_07_1);
            assertEquals(ToolInstallState.INSTALLED, online.state(), "online, the build installed");
            Path executable = online.installedPath().orElseThrow();

            network.cutOff();

            ToolOffer stillThere = rowFor(harness.manager().offers(), PERCOLATOR_3_07_1);
            try (SwitchableNetwork never = SwitchableNetwork.unavailable()) {
                ToolManagerHarness restarted =
                        ToolManagerHarness.at(root)
                                .fetching(never)
                                .probing(FixedProbe.refusingToBeCalled())
                                .build();
                ToolOffer afterRestart = rowFor(restarted.manager().offers(), PERCOLATOR_3_07_1);
                RecordingListener another = new RecordingListener();
                harness.manager().install(ToolName.PERCOLATOR, PERCOLATOR_3_06_5, another);
                InstallPhase anotherEnded = another.awaitTerminal().phase();
                assertAll(
                        () -> assertEquals(ToolInstallState.INSTALLED, stillThere.state()),
                        () -> assertEquals(Optional.of(executable), stillThere.installedPath()),
                        () ->
                                assertEquals(
                                        ToolInstallState.INSTALLED,
                                        afterRestart.state(),
                                        "a new session over the same cache, started with no"
                                                + " network"),
                        () ->
                                assertEquals(
                                        InstallationState.INSTALLED,
                                        restarted.verify(record).state(),
                                        "the cache verifies on its own: the marker and every"
                                                + " recorded checksum"),
                        () ->
                                assertEquals(
                                        List.of(),
                                        never.askedForWhileDown(),
                                        "the new session fetched nothing"),
                        () ->
                                assertEquals(
                                        2,
                                        fetchedOnline,
                                        "online, the binary and its companion were fetched"),
                        () ->
                                assertEquals(
                                        InstallPhase.FAILED,
                                        anotherEnded,
                                        "the control: the network really is down, so a build"
                                                + " that is not cached cannot be installed"),
                        () ->
                                assertEquals(
                                        List.of(
                                                ToolManagerFixtures.record(
                                                                ToolName.PERCOLATOR,
                                                                "3.06.5",
                                                                ToolManagerFixtures.LINUX)
                                                        .url()),
                                        network.askedForWhileDown(),
                                        "and the only fetch after the cut is that control's:"
                                                + " reading the cached build's offer asked for"
                                                + " nothing"),
                        () ->
                                assertEquals(
                                        2,
                                        served.requested().size(),
                                        "and nothing reached the server after the cut"));
            }
        }
    }

    private static List<String> filesUnder(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return List.of();
        }
        try (Stream<Path> walked = Files.walk(directory)) {
            return walked.filter(Files::isRegularFile).map(Path::toString).sorted().toList();
        }
    }
}
