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
import java.nio.file.attribute.PosixFilePermissions;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import org.cometgui.domain.tools.CapabilityEvidence;
import org.cometgui.domain.tools.InstallPhase;
import org.cometgui.domain.tools.InstallProgress;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.domain.tools.ToolInstallState;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolOrigin;
import org.cometgui.domain.tools.ToolRegistrationException;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.install.cache.InstallationState;
import org.cometgui.install.registry.ArtefactCompanion;
import org.cometgui.install.registry.ArtefactRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * An install driven through the domain port, with no user interface anywhere: the whole of what
 * phase 05 built, from the shipped manifest to a verified cache entry, over the bytes upstream
 * really publishes and real HTTP.
 *
 * <p>Percolator 3.07.1 rather than PDV, because it is the artefact with a <em>companion</em> -- the
 * {@code .deb} the two XSDs are taken out of -- so one install exercises two transfers, the archive
 * extractor, the payload reader and the layout check. PDV's 103 MB is where the cancellation tests
 * live.
 */
class ManagedToolManagerInstallTest {

    private static final String PERCOLATOR_ZIP =
            "rel-3-07-01__percolator-noxml-ubuntu-portable.zip";
    private static final String PERCOLATOR_DEB =
            "rel-3-07-01__percolator-noxml-v3-07-linux-amd64.deb";
    private static final String PERCOLATOR_3_06_5_ZIP =
            "rel-3-06-05__percolator-noxml-linux-portable.zip";
    private static final ToolVersion PERCOLATOR_3_07_1 = ToolVersion.parse("3.07.1");
    private static final ToolVersion PERCOLATOR_3_06_5 = ToolVersion.parse("3.06.5");

    @TempDir private Path temporary;

    private static ArtefactRecord percolator() throws IOException {
        return ToolManagerFixtures.record(ToolName.PERCOLATOR, "3.07.1", ToolManagerFixtures.LINUX);
    }

    private ServedArtefacts servingPercolator(ArtefactRecord record) throws IOException {
        ArtefactCompanion companion = record.companions().get(0);
        return new ServedArtefacts()
                .serve(record.url(), ToolManagerFixtures.artefact(PERCOLATOR_ZIP))
                .serve(companion.url(), ToolManagerFixtures.artefact(PERCOLATOR_DEB));
    }

    private static ToolOffer rowFor(List<ToolOffer> offers, ToolVersion version) {
        return offers.stream()
                .filter(offer -> offer.tool() == ToolName.PERCOLATOR)
                .filter(offer -> offer.version().equals(version))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no percolator row in " + offers));
    }

    @Test
    @DisplayName("an install runs end to end through the port and ends INSTALLED")
    void anInstallRunsEndToEndThroughThePort() throws IOException, InterruptedException {
        ArtefactRecord record = percolator();
        /*
         * The probe answers with ONE capability where the manifest declares two.  R-TOOL-07 says
         * the probe wins, and a probe that answered the manifest's own list would make that rule
         * unobservable -- the row would look right either way.
         */
        FixedProbe probe = FixedProbe.answering(ToolCapability.XML_OUTPUT);
        RecordingListener listener = new RecordingListener();
        try (ServedArtefacts served = servingPercolator(record)) {
            ToolManagerHarness harness =
                    ToolManagerHarness.at(temporary.resolve("cache"))
                            .fetching(served)
                            .probing(probe)
                            .build();
            assertEquals(
                    InstallationState.NOT_PRESENT,
                    harness.verify(record).state(),
                    "the cache starts empty");

            harness.manager().install(ToolName.PERCOLATOR, PERCOLATOR_3_07_1, listener);
            InstallProgress terminal = listener.awaitTerminal();

            ToolOffer row = rowFor(harness.manager().offers(), PERCOLATOR_3_07_1);
            Path installed = row.installedPath().orElseThrow();
            assertAll(
                    () -> assertEquals(InstallPhase.DONE, terminal.phase()),
                    () ->
                            assertEquals(
                                    1,
                                    listener.terminalReports().size(),
                                    () ->
                                            "exactly one terminal report, and last: "
                                                    + listener.phases()),
                    () ->
                            assertEquals(
                                    List.of(
                                            InstallPhase.DOWNLOADING,
                                            InstallPhase.VERIFYING,
                                            InstallPhase.EXTRACTING,
                                            InstallPhase.VERIFYING,
                                            InstallPhase.INSTALLING,
                                            InstallPhase.PROBING,
                                            InstallPhase.INSTALLING,
                                            InstallPhase.INSTALLING,
                                            InstallPhase.DONE),
                                    withoutDownloadChatter(listener.phases()),
                                    "the eight steps' phases, in order, then DONE"),
                    () -> assertEquals(ToolInstallState.INSTALLED, row.state()),
                    () ->
                            assertTrue(
                                    Files.isRegularFile(installed),
                                    () -> installed + " must exist"),
                    () ->
                            assertTrue(
                                    Files.isExecutable(installed),
                                    () -> installed + " must carry the R-PLAT-05 executable bit"),
                    () ->
                            assertEquals(
                                    "bin/percolator",
                                    record.executablePath(),
                                    "and it is at the path the manifest names"),
                    () ->
                            assertEquals(
                                    List.of("XML_OUTPUT"),
                                    row.capabilities().stream()
                                            .map(declared -> declared.capability().id())
                                            .toList(),
                                    "what the probe answered, not what the manifest declared:"
                                            + " the manifest claims XML_OUTPUT and"
                                            + " XML_DECOY_OUTPUT (R-TOOL-07)"),
                    () ->
                            assertEquals(
                                    CapabilityEvidence.OBSERVED_BY_EXECUTION,
                                    row.capabilities().get(0).evidence()),
                    () ->
                            assertEquals(
                                    OptionalLong.of(2_798_963L),
                                    row.downloadSizeBytes(),
                                    "an installed build still says how large the transfer was:"
                                            + " 946 303 for the archive and 1 852 660 for the .deb"
                                            + " this install really fetched, both of them served"
                                            + " above"),
                    () ->
                            assertEquals(
                                    List.of(record.url(), record.companions().get(0).url()),
                                    served.requested(),
                                    "the artefact and its companion, both fetched by the URL the"
                                            + " manifest pins"),
                    () -> assertEquals(1, probe.callCount()),
                    () -> assertTrue(harness.verify(record).installed()),
                    () -> assertEquals(List.of(), harness.stagingDirectories(record)));
        }
    }

    /*
     * A diagnostic of the shape unit 6's classifier produces for a host whose C library has moved
     * under an installed build: the case R-TOOL-06's last sentence is about.
     */
    private static org.cometgui.domain.tools.LoaderDiagnostic aLoaderRefusal() {
        return new org.cometgui.domain.tools.LoaderDiagnostic(
                org.cometgui.domain.tools.ProbeFailureKind.MISSING_SYMBOL_VERSION,
                "libc.so.6",
                Optional.of("GLIBC_2.34"),
                Optional.of("GLIBC_2.17"),
                List.of("percolator 3.06.5 linux-x86-64"));
    }

    /*
     * A download reports progress once per chunk, so the phase list holds hundreds of DOWNLOADING
     * entries; collapsing the runs leaves the step sequence, which is what is being asserted.
     */
    private static List<InstallPhase> withoutDownloadChatter(List<InstallPhase> phases) {
        List<InstallPhase> collapsed = new java.util.ArrayList<>();
        for (InstallPhase phase : phases) {
            if (phase != InstallPhase.DOWNLOADING
                    || collapsed.isEmpty()
                    || collapsed.get(collapsed.size() - 1) != InstallPhase.DOWNLOADING) {
                collapsed.add(phase);
            }
        }
        return collapsed;
    }

    @Test
    @DisplayName("a corrupted artefact is rejected and nothing is executed")
    void aCorruptedArtefactIsRejectedAndNothingIsExecuted()
            throws IOException, InterruptedException {
        ArtefactRecord record = percolator();
        Path corrupted = temporary.resolve("corrupted-percolator.zip");
        byte[] bytes = Files.readAllBytes(ToolManagerFixtures.artefact(PERCOLATOR_ZIP));
        bytes[bytes.length / 2] ^= 0x5a;
        Files.write(corrupted, bytes);
        FixedProbe probe = FixedProbe.refusingToBeCalled();
        RecordingListener listener = new RecordingListener();
        try (ServedArtefacts served =
                new ServedArtefacts()
                        .serve(record.url(), corrupted)
                        .serve(
                                record.companions().get(0).url(),
                                ToolManagerFixtures.artefact(PERCOLATOR_DEB))) {
            ToolManagerHarness harness =
                    ToolManagerHarness.at(temporary.resolve("cache"))
                            .fetching(served)
                            .probing(probe)
                            .build();

            harness.manager().install(ToolName.PERCOLATOR, PERCOLATOR_3_07_1, listener);
            InstallProgress terminal = listener.awaitTerminal();

            ToolOffer row = rowFor(harness.manager().offers(), PERCOLATOR_3_07_1);
            assertAll(
                    () ->
                            assertEquals(
                                    record.sizeBytes(),
                                    Files.size(corrupted),
                                    "the corruption kept the length, so only the SHA-256 could"
                                            + " have caught it"),
                    () -> assertEquals(InstallPhase.FAILED, terminal.phase()),
                    () -> assertEquals(0, probe.callCount(), "nothing was executed"),
                    () ->
                            assertEquals(
                                    ToolInstallState.FAILED,
                                    row.state(),
                                    "and the row says so rather than offering a fresh install as"
                                            + " though nothing had happened"),
                    () -> assertEquals(Optional.empty(), row.installedPath()),
                    () ->
                            assertEquals(
                                    InstallationState.NOT_PRESENT,
                                    harness.verify(record).state(),
                                    "R-TOOL-04: nothing in the cache reports itself installed"),
                    () -> assertEquals(List.of(), harness.stagingDirectories(record)));
        }
    }

    @Test
    @DisplayName("a running install shows as INSTALLING, and as INSTALLED when it finishes")
    void aRunningInstallShowsAsInstalling() throws IOException {
        ArtefactRecord record = percolator();
        ToolManagerHarness.QueuedInstalls queued = new ToolManagerHarness.QueuedInstalls();
        try (ServedArtefacts served = servingPercolator(record)) {
            ToolManagerHarness harness =
                    ToolManagerHarness.at(temporary.resolve("cache"))
                            .fetching(served)
                            .probing(FixedProbe.answering(ToolCapability.XML_OUTPUT))
                            .installingOn(queued)
                            .build();

            harness.manager().install(ToolName.PERCOLATOR, PERCOLATOR_3_07_1, progress -> {});
            ToolOffer whileRunning = rowFor(harness.manager().offers(), PERCOLATOR_3_07_1);
            List<URI> beforeTheWorkRan = served.requested();
            queued.runQueued();
            ToolOffer afterwards = rowFor(harness.manager().offers(), PERCOLATOR_3_07_1);

            assertAll(
                    () ->
                            assertEquals(
                                    List.of(),
                                    beforeTheWorkRan,
                                    "install() returned before a single byte was fetched: the port"
                                            + " promises it returns as soon as the install has"
                                            + " STARTED, which is what makes it safe to call from"
                                            + " the JavaFX application thread"),
                    () -> assertEquals(ToolInstallState.INSTALLING, whileRunning.state()),
                    () -> assertEquals(Optional.empty(), whileRunning.installedPath()),
                    () -> assertEquals(ToolInstallState.INSTALLED, afterwards.state()),
                    () -> assertTrue(afterwards.installedPath().isPresent()));
        }
    }

    @Test
    @DisplayName("an installed build that no longer starts here is not offered (R-TOOL-06)")
    void anInstalledBuildThatNoLongerStartsIsNotOffered() throws IOException, InterruptedException {
        ArtefactRecord record = percolator();
        RecordingListener listener = new RecordingListener();
        ToolManagerHarness.RecordedLoadability loadability =
                new ToolManagerHarness.RecordedLoadability();
        try (ServedArtefacts served = servingPercolator(record)) {
            ToolManagerHarness harness =
                    ToolManagerHarness.at(temporary.resolve("cache"))
                            .fetching(served)
                            .probing(FixedProbe.answering(ToolCapability.XML_OUTPUT))
                            .loadability(loadability)
                            .build();
            harness.manager().install(ToolName.PERCOLATOR, PERCOLATOR_3_07_1, listener);
            assertEquals(InstallPhase.DONE, listener.awaitTerminal().phase());
            List<ToolOffer> before = harness.manager().offers();

            loadability.refusing(record, aLoaderRefusal());
            List<ToolOffer> after = harness.manager().offers();

            assertAll(
                    () ->
                            assertEquals(
                                    ToolInstallState.INSTALLED,
                                    rowFor(before, PERCOLATOR_3_07_1).state()),
                    () ->
                            assertEquals(
                                    List.of(record.describe(), record.describe()),
                                    loadability.asked(),
                                    "once per offers() call, and about the one build that is"
                                            + " installed: the others have no binary to run and"
                                            + " are never asked"),
                    () ->
                            assertEquals(
                                    List.of(),
                                    after.stream()
                                            .filter(offer -> offer.tool() == ToolName.PERCOLATOR)
                                            .filter(
                                                    offer ->
                                                            offer.version()
                                                                    .equals(PERCOLATOR_3_07_1))
                                            .toList(),
                                    "R-TOOL-06: a tool that fails loadability is never offered"),
                    () ->
                            assertEquals(
                                    6,
                                    after.size(),
                                    "and only that row leaves; one unreachable build does not"
                                            + " blank the Tool Manager"));
        }
    }

    @Test
    @DisplayName("a failed install marks its own build and no other")
    void aFailedInstallMarksItsOwnBuildAndNoOther() throws IOException, InterruptedException {
        ArtefactRecord good = percolator();
        ArtefactRecord older =
                ToolManagerFixtures.record(
                        ToolName.PERCOLATOR, "3.06.5", ToolManagerFixtures.LINUX);
        Path corrupted = temporary.resolve("corrupted-3-06-5.zip");
        byte[] bytes = Files.readAllBytes(ToolManagerFixtures.artefact(PERCOLATOR_3_06_5_ZIP));
        bytes[bytes.length / 2] ^= 0x5a;
        Files.write(corrupted, bytes);
        RecordingListener listener = new RecordingListener();
        try (ServedArtefacts served =
                new ServedArtefacts()
                        .serve(good.url(), ToolManagerFixtures.artefact(PERCOLATOR_ZIP))
                        .serve(older.url(), corrupted)) {
            ToolManagerHarness harness =
                    ToolManagerHarness.at(temporary.resolve("cache"))
                            .fetching(served)
                            .probing(FixedProbe.refusingToBeCalled())
                            .build();

            harness.manager().install(ToolName.PERCOLATOR, PERCOLATOR_3_06_5, listener);
            assertEquals(InstallPhase.FAILED, listener.awaitTerminal().phase());

            List<ToolOffer> offers = harness.manager().offers();
            assertAll(
                    () ->
                            assertEquals(
                                    ToolInstallState.FAILED,
                                    rowFor(offers, PERCOLATOR_3_06_5).state(),
                                    "the build whose install failed"),
                    () ->
                            assertEquals(
                                    ToolInstallState.NOT_INSTALLED,
                                    rowFor(offers, PERCOLATOR_3_07_1).state(),
                                    "and not the one beside it: a failure is recorded against the"
                                            + " cache entry it was for, and two releases of one"
                                            + " tool are two entries"));
        }
    }

    @Test
    @DisplayName("an installed build that no longer starts cannot be installed again either")
    void anInstalledBuildThatNoLongerStartsCannotBeInstalledAgain()
            throws IOException, InterruptedException {
        ArtefactRecord record = percolator();
        RecordingListener listener = new RecordingListener();
        ToolManagerHarness.RecordedLoadability loadability =
                new ToolManagerHarness.RecordedLoadability();
        try (ServedArtefacts served = servingPercolator(record)) {
            ToolManagerHarness harness =
                    ToolManagerHarness.at(temporary.resolve("cache"))
                            .fetching(served)
                            .probing(FixedProbe.answering(ToolCapability.XML_OUTPUT))
                            .loadability(loadability)
                            .build();
            harness.manager().install(ToolName.PERCOLATOR, PERCOLATOR_3_07_1, listener);
            assertEquals(InstallPhase.DONE, listener.awaitTerminal().phase());

            loadability.refusing(record, aLoaderRefusal());
            IllegalArgumentException refused =
                    assertThrows(
                            IllegalArgumentException.class,
                            () ->
                                    harness.manager()
                                            .install(
                                                    ToolName.PERCOLATOR,
                                                    PERCOLATOR_3_07_1,
                                                    progress -> {}));

            assertEquals(
                    "no offer names percolator 3.07.1 on linux-x86-64 as something this host can"
                            + " install: This build cannot run on this host: libc.so.6 on this host"
                            + " does not provide a symbol version this build needs. Required:"
                            + " GLIBC_2.34. Available on this host: GLIBC_2.17. Alternatives:"
                            + " percolator 3.06.5 linux-x86-64.",
                    refused.getMessage(),
                    "R-TOOL-06 gates the button as well as the row: a build that fails loadability"
                            + " is never offered for selection");
        }
    }

    @Test
    @DisplayName("the manager says which host and which cache it answers for")
    void theManagerDescribesItself() throws IOException {
        Path root = temporary.resolve("cache");
        ToolManagerHarness harness = ToolManagerHarness.at(root).build();

        assertEquals(
                "ManagedToolManager[linux-x86-64, " + root.toAbsolutePath() + "]",
                harness.manager().toString());
    }

    @Test
    @DisplayName("a cache entry that cannot be read is not one that reports itself installed")
    void anUnreadableCacheEntryIsNotInstalled() throws IOException, InterruptedException {
        ArtefactRecord record = percolator();
        RecordingListener listener = new RecordingListener();
        try (ServedArtefacts served = servingPercolator(record)) {
            ToolManagerHarness harness =
                    ToolManagerHarness.at(temporary.resolve("cache"))
                            .fetching(served)
                            .probing(FixedProbe.answering(ToolCapability.XML_OUTPUT))
                            .build();
            harness.manager().install(ToolName.PERCOLATOR, PERCOLATOR_3_07_1, listener);
            assertEquals(InstallPhase.DONE, listener.awaitTerminal().phase());
            Path installed =
                    rowFor(harness.manager().offers(), PERCOLATOR_3_07_1)
                            .installedPath()
                            .orElseThrow();

            /*
             * The recorded file is still there and still the right length; only its permissions
             * change, so the one thing that cannot happen is hashing it -- which is the second half
             * of R-TOOL-04 and the half that decides.
             */
            Files.setPosixFilePermissions(installed, PosixFilePermissions.fromString("---------"));
            List<ToolOffer> offers = harness.manager().offers();

            assertAll(
                    () ->
                            assertThrows(
                                    IOException.class,
                                    () -> harness.verify(record),
                                    "the cache itself cannot answer at all, which is the condition"
                                            + " under test"),
                    () ->
                            assertEquals(
                                    7,
                                    offers.size(),
                                    "and the Tool Manager still draws every row: one unreadable"
                                            + " directory does not blank the list"),
                    () ->
                            assertEquals(
                                    ToolInstallState.NOT_INSTALLED,
                                    rowFor(offers, PERCOLATOR_3_07_1).state(),
                                    "R-TOOL-04 makes installed mean a marker present AND its"
                                            + " recorded checksums verified; neither can be"
                                            + " established through an I/O failure, so the row"
                                            + " offers the install that repairs it"));
        }
    }

    @Test
    @DisplayName("a local binary is registered through the port, by the tool's own registrar")
    void aLocalBinaryIsRegisteredThroughThePort() throws IOException, ToolRegistrationException {
        Path binary = temporary.resolve("percolator");
        Files.writeString(binary, "not really percolator");
        ToolOffer registered =
                new ToolOffer(
                        ToolName.PERCOLATOR,
                        PERCOLATOR_3_07_1,
                        ToolOrigin.LOCAL,
                        ToolInstallState.INSTALLED,
                        List.of(),
                        List.of(),
                        Optional.empty(),
                        Optional.of(binary),
                        OptionalLong.empty());
        ToolManagerHarness harness =
                ToolManagerHarness.at(temporary.resolve("cache"))
                        .registering(ToolName.PERCOLATOR, chosen -> registered)
                        .build();

        ToolOffer offer = harness.manager().registerLocalBinary(ToolName.PERCOLATOR, binary);

        assertAll(
                () -> assertEquals(registered, offer),
                () -> assertEquals(ToolOrigin.LOCAL, offer.origin()),
                () ->
                        assertEquals(
                                OptionalLong.empty(),
                                offer.downloadSizeBytes(),
                                "nothing was downloaded for it"));
    }

    @Test
    @DisplayName("a tool with no local registrar is refused, naming what can be registered")
    void aToolWithNoRegistrarIsRefused() throws IOException {
        Path binary = temporary.resolve("comet");
        Files.writeString(binary, "not really comet");
        ToolOffer anything =
                new ToolOffer(
                        ToolName.PERCOLATOR,
                        PERCOLATOR_3_07_1,
                        ToolOrigin.LOCAL,
                        ToolInstallState.INSTALLED,
                        List.of(),
                        List.of(),
                        Optional.empty(),
                        Optional.of(binary),
                        OptionalLong.empty());
        ToolManagerHarness withPercolator =
                ToolManagerHarness.at(temporary.resolve("cache"))
                        .registering(ToolName.PERCOLATOR, chosen -> anything)
                        .build();
        ToolManagerHarness withNone = ToolManagerHarness.at(temporary.resolve("none")).build();

        assertAll(
                () ->
                        assertEquals(
                                "CometGUI cannot register a local comet binary. It registers a"
                                        + " local binary for percolator; every other tool is"
                                        + " installed from the artefact manifest, where its"
                                        + " checksum is pinned.",
                                assertThrows(
                                                ToolRegistrationException.class,
                                                () ->
                                                        withPercolator
                                                                .manager()
                                                                .registerLocalBinary(
                                                                        ToolName.COMET, binary))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "CometGUI cannot register a local percolator binary. It"
                                        + " registers a local binary for no tool at all in this"
                                        + " build; every other tool is installed from the artefact"
                                        + " manifest, where its checksum is pinned.",
                                assertThrows(
                                                ToolRegistrationException.class,
                                                () ->
                                                        withNone.manager()
                                                                .registerLocalBinary(
                                                                        ToolName.PERCOLATOR,
                                                                        binary))
                                        .getMessage()));
    }

    @Test
    @DisplayName("the registrars given at construction are copied, not borrowed")
    void theRegistrarMapIsCopied() throws IOException {
        Path binary = temporary.resolve("comet");
        Files.writeString(binary, "not really comet");
        Map<ToolName, LocalBinaryRegistrar> mutable = new LinkedHashMap<>();
        ManagedToolManager manager =
                new ManagedToolManager(
                        ToolManagerFixtures.shippedManifest(),
                        ToolManagerFixtures.LINUX,
                        ToolManagerFixtures.DEBIAN_12,
                        ToolManagerHarness.at(temporary.resolve("cache")).build().installer(),
                        record -> Optional.empty(),
                        mutable,
                        Runnable::run);

        mutable.put(
                ToolName.COMET,
                chosen -> {
                    throw new AssertionError(
                            "a registrar added to the caller's map after construction was used: "
                                    + chosen);
                });

        assertEquals(
                "CometGUI cannot register a local comet binary. It registers a local binary for"
                        + " no tool at all in this build; every other tool is installed from the"
                        + " artefact manifest, where its checksum is pinned.",
                assertThrows(
                                ToolRegistrationException.class,
                                () -> manager.registerLocalBinary(ToolName.COMET, binary))
                        .getMessage(),
                "what the manager was built with, not what the caller's map says now");
    }

    @Test
    @DisplayName("a relative path is refused before any registrar is asked")
    void aRelativePathIsRefused() throws IOException {
        ToolManagerHarness harness =
                ToolManagerHarness.at(temporary.resolve("cache"))
                        .registering(
                                ToolName.PERCOLATOR,
                                chosen -> {
                                    throw new AssertionError(
                                            "the registrar was asked about a relative path: "
                                                    + chosen);
                                })
                        .build();

        assertEquals(
                "executable must be an absolute path, because the registration is recorded in a"
                        + " provenance record that is read on another machine, but was:"
                        + " tools/percolator",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        harness.manager()
                                                .registerLocalBinary(
                                                        ToolName.PERCOLATOR,
                                                        Path.of("tools/percolator")))
                        .getMessage());
    }

    @Test
    @DisplayName("a registrar that answers for another tool, or as MANAGED, is refused here")
    void aRegistrarThatAnswersWrongIsRefused() throws IOException {
        Path binary = temporary.resolve("percolator");
        Files.writeString(binary, "not really percolator");
        ToolOffer managed =
                new ToolOffer(
                        ToolName.PERCOLATOR,
                        PERCOLATOR_3_07_1,
                        ToolOrigin.MANAGED,
                        ToolInstallState.INSTALLED,
                        List.of(),
                        List.of(),
                        Optional.empty(),
                        Optional.of(binary),
                        OptionalLong.of(946_303L));
        ToolOffer anotherTool =
                new ToolOffer(
                        ToolName.COMET,
                        ToolVersion.parse("2026.02.2"),
                        ToolOrigin.LOCAL,
                        ToolInstallState.INSTALLED,
                        List.of(),
                        List.of(),
                        Optional.empty(),
                        Optional.of(binary),
                        OptionalLong.empty());
        ToolManagerHarness sayingManaged =
                ToolManagerHarness.at(temporary.resolve("a"))
                        .registering(ToolName.PERCOLATOR, chosen -> managed)
                        .build();
        ToolManagerHarness sayingComet =
                ToolManagerHarness.at(temporary.resolve("b"))
                        .registering(ToolName.PERCOLATOR, chosen -> anotherTool)
                        .build();

        assertAll(
                () ->
                        assertEquals(
                                "the registrar for percolator answered with a MANAGED offer, and a"
                                        + " binary the user pointed at is LOCAL",
                                assertThrows(
                                                IllegalStateException.class,
                                                () ->
                                                        sayingManaged
                                                                .manager()
                                                                .registerLocalBinary(
                                                                        ToolName.PERCOLATOR,
                                                                        binary))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "the registrar for percolator answered with an offer for comet",
                                assertThrows(
                                                IllegalStateException.class,
                                                () ->
                                                        sayingComet
                                                                .manager()
                                                                .registerLocalBinary(
                                                                        ToolName.PERCOLATOR,
                                                                        binary))
                                        .getMessage()));
    }
}
