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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.cometgui.domain.tools.CapabilityEvidence;
import org.cometgui.domain.tools.DeclaredCapability;
import org.cometgui.domain.tools.InstallHandle;
import org.cometgui.domain.tools.InstallPhase;
import org.cometgui.domain.tools.ProbeFailureKind;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.domain.tools.ToolInstallState;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.install.cache.InstallationState;
import org.cometgui.install.registry.ArtefactRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Installing through the Tool Manager, over real HTTP, with the bytes upstream really publishes.
 *
 * <p>What is proved here is the composition rather than any one of its parts: that pressing Install
 * on a row ends with that row saying {@code INSTALLED} and carrying the capabilities the probe
 * watched; that a corrupted artefact stops at {@code R-SEC-02} and the probe is never reached; that
 * cancelling leaves nothing that reports itself installed; and that a build whose binary would not
 * start stops being offered afterwards, which is {@code R-TOOL-06}'s last sentence at the only
 * point in the product where it can be observed.
 */
class ManagedToolManagerInstallTest {

    @TempDir private Path cacheRoot;

    /** What a Percolator build is scripted to be observed doing, so the marker has content. */
    private static final Set<ToolCapability> XML_CAPABLE =
            Set.of(ToolCapability.XML_OUTPUT, ToolCapability.XML_DECOY_OUTPUT);

    @Test
    @DisplayName("the real 3.07.1 artefact installs through the Tool Manager and the row says so")
    void aRealArtefactInstalls() throws IOException {
        try (ToolManagerHarness harness = ToolManagerHarness.onDebian12(cacheRoot)) {
            ArtefactRecord record = harness.recordOf(ToolName.PERCOLATOR, "3.07.1");
            harness.mirror().serving(record);
            harness.probe().observing(ToolName.PERCOLATOR, XML_CAPABLE);

            RecordingListener listener = new RecordingListener();
            harness.manager().install(ToolName.PERCOLATOR, ToolVersion.parse("3.07.1"), listener);

            assertEquals(InstallPhase.DONE, listener.awaitTerminal());
            ToolOffer offer = harness.offerOf(ToolName.PERCOLATOR, "3.07.1");
            Path installed =
                    cacheRoot
                            .resolve("tools")
                            .resolve("percolator")
                            .resolve("3.7.1")
                            .resolve("linux-x86-64")
                            .resolve("bin")
                            .resolve("percolator");
            assertAll(
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
                                            InstallPhase.DONE),
                                    listener.phases(),
                                    "the eight steps a scientist can see, in order"),
                    () -> assertEquals(ToolInstallState.INSTALLED, offer.state()),
                    () ->
                            assertEquals(
                                    Optional.of(installed),
                                    offer.installedPath(),
                                    "the directory is the NORMALISED version 3.7.1 while the row"
                                            + " itself keeps upstream's 3.07.1"),
                    () -> assertEquals("3.07.1", offer.version().text()),
                    () ->
                            assertEquals(
                                    OptionalLong.of(946303L),
                                    offer.downloadSizeBytes(),
                                    "an installed row still says what it cost to fetch"),
                    () ->
                            assertEquals(
                                    List.of("XML_DECOY_OUTPUT", "XML_OUTPUT"),
                                    capabilityIds(offer),
                                    "the probed set, read back out of the completion marker"),
                    () ->
                            assertTrue(
                                    offer.capabilities().stream()
                                            .allMatch(
                                                    declared ->
                                                            declared.evidence()
                                                                    == CapabilityEvidence
                                                                            .OBSERVED_BY_EXECUTION),
                                    "R-TOOL-07: where the manifest and the probe disagree, the"
                                            + " probe wins, so an installed row's evidence is"
                                            + " observation"),
                    () ->
                            assertEquals(
                                    List.of("percolator 3.07.1 linux-x86-64"),
                                    harness.probe().probed(),
                                    "and step 6 was reached exactly once"),
                    () ->
                            assertEquals(
                                    InstallationState.INSTALLED,
                                    harness.verify(record).state(),
                                    "R-TOOL-04 agrees, from the marker and the digests"));
        }
    }

    /*
     * GATE ITEM 2.  The bytes served are the real artefact with one byte flipped, so the LENGTH
     * still matches and only the SHA-256 can have caught it -- and the probe is a recorder, so
     * "nothing was executed" is an assertion about what was called rather than a hope.  The
     * process-level half of the same claim, with a recording ProcessRunner underneath the real
     * three-stage probe, is in cometgui-app.
     */
    @Test
    @DisplayName("a corrupted artefact is rejected and the probe is never reached")
    void aCorruptedArtefactIsRejectedBeforeAnythingRuns() throws IOException {
        try (ToolManagerHarness harness = ToolManagerHarness.onDebian12(cacheRoot)) {
            ArtefactRecord record = harness.recordOf(ToolName.PERCOLATOR, "3.07.1");
            harness.mirror().servingCorrupted(record);

            RecordingListener listener = new RecordingListener();
            harness.manager().install(ToolName.PERCOLATOR, ToolVersion.parse("3.07.1"), listener);

            assertAll(
                    () -> assertEquals(InstallPhase.FAILED, listener.awaitTerminal()),
                    () ->
                            assertEquals(
                                    List.of(),
                                    harness.probe().probed(),
                                    "R-SEC-02: verification is mandatory BEFORE an executable is"
                                            + " launched, and nothing was launched"),
                    () ->
                            assertEquals(
                                    ToolInstallState.FAILED,
                                    harness.offerOf(ToolName.PERCOLATOR, "3.07.1").state()),
                    () ->
                            assertEquals(
                                    Optional.empty(),
                                    harness.offerOf(ToolName.PERCOLATOR, "3.07.1").installedPath(),
                                    "and no path a caller could execute"),
                    () ->
                            assertEquals(
                                    InstallationState.NOT_PRESENT,
                                    harness.verify(record).state(),
                                    "nothing reached the tool cache at all"));
        }
    }

    /*
     * R-TOOL-04, on the transfer the phase document names for exactly this: PDV is 103407417 bytes,
     * and it is cancelled well inside the first four megabytes of it.  The byte count at
     * cancellation is asserted to be far short of the total, so that a test which happened to
     * cancel after the download finished would fail rather than pass for the wrong reason.
     */
    @Test
    @DisplayName("cancelling the PDV download reports CANCELLED and installs nothing")
    void cancellingMidDownload() throws IOException {
        try (ToolManagerHarness harness = ToolManagerHarness.onDebian12(cacheRoot)) {
            ArtefactRecord pdv = harness.recordOf(ToolName.PDV, "2.7.0");
            harness.mirror().serving(pdv);
            AtomicReference<InstallHandle> handle = new AtomicReference<>();
            RecordingListener listener =
                    new RecordingListener(
                            progress -> {
                                InstallHandle running = handle.get();
                                if (running != null && progress.bytesTransferred() > 4_000_000L) {
                                    running.cancel();
                                }
                            });

            handle.set(harness.installWith(ToolName.PDV, "2.7.0", listener));

            assertAll(
                    () -> assertEquals(InstallPhase.CANCELLED, listener.awaitTerminal()),
                    () ->
                            assertTrue(
                                    listener.bytesTransferred() < 103407417L / 2,
                                    () ->
                                            "the cancellation has to land in the middle of the"
                                                    + " transfer to mean anything, and it stopped"
                                                    + " at "
                                                    + listener.bytesTransferred()
                                                    + " of 103407417 bytes"),
                    () ->
                            assertEquals(
                                    ToolInstallState.NOT_INSTALLED,
                                    harness.offerOf(ToolName.PDV, "2.7.0").state(),
                                    "a user who cancelled has not encountered an error, so the row"
                                            + " does not say FAILED"),
                    () ->
                            assertEquals(
                                    InstallationState.NOT_PRESENT,
                                    harness.verify(pdv).state(),
                                    "and nothing was left behind that reports itself installed"),
                    () -> assertEquals(List.of(), harness.probe().probed()));
        }
    }

    @Test
    @DisplayName("a build that fails to load stops being offered, and its row says why")
    void aLoadabilityRefusalWithdrawsTheOffer() throws IOException {
        try (ToolManagerHarness harness = ToolManagerHarness.onDebian12(cacheRoot)) {
            ArtefactRecord record = harness.recordOf(ToolName.PERCOLATOR, "3.07.1");
            harness.mirror().serving(record);
            harness.probe()
                    .refusing(
                            record,
                            ProbeFailureKind.MISSING_SHARED_OBJECT,
                            "libboost_filesystem.so.1.83.0: cannot open shared object file");

            RecordingListener listener = new RecordingListener();
            harness.manager().install(ToolName.PERCOLATOR, ToolVersion.parse("3.07.1"), listener);

            assertEquals(InstallPhase.FAILED, listener.awaitTerminal());
            ToolOffer refused = harness.offerOf(ToolName.PERCOLATOR, "3.07.1");
            assertAll(
                    () -> assertEquals(ToolInstallState.FAILED, refused.state()),
                    () ->
                            assertEquals(
                                    "This build cannot run on this host: the dynamic loader could"
                                            + " not find the shared library percolator. Required:"
                                            + " not named by the loader. Available on this host:"
                                            + " none found. Alternatives: percolator 3.06.5"
                                            + " linux-x86-64.",
                                    refused.loaderDiagnostic().orElseThrow().message(),
                                    "R-PLAT-03 requires the alternatives to be named, and the one"
                                            + " that is named is the sibling version this host can"
                                            + " still run"),
                    () ->
                            assertEquals(
                                    "no offer on this host names percolator 3.07.1; linux-x86-64"
                                            + " is offered [percolator 3.06.5 linux-x86-64]",
                                    assertThrows(
                                                    IllegalArgumentException.class,
                                                    () ->
                                                            harness.manager()
                                                                    .install(
                                                                            ToolName.PERCOLATOR,
                                                                            ToolVersion.parse(
                                                                                    "3.07.1"),
                                                                            progress -> {}))
                                            .getMessage(),
                                    "R-TOOL-06: a tool that fails loadability is never offered for"
                                            + " selection, and that has to hold for the button"),
                    () ->
                            assertEquals(
                                    ToolInstallState.NOT_INSTALLED,
                                    harness.offerOf(ToolName.PERCOLATOR, "3.06.5").state(),
                                    "one refusal is one row, not the end of the list"));
        }
    }

    /*
     * THE AXIS THE RULE IS SILENT ABOUT.  R-TOOL-06 withholds the offer for a LOADABILITY failure
     * and for that stage only: "this binary says it is 3.06.5 and the manifest pinned 3.07.1" is a
     * different sentence from "this build will not start", and it is not a reason to stop offering
     * a build that starts.  Graded here because the offered-set rule is stated over what the probe
     * answered and is silent about which stage answered it.
     */
    @Test
    @DisplayName("an identity refusal is a failed install and not a withdrawn offer")
    void anIdentityRefusalLeavesTheOfferStanding() throws IOException {
        try (ToolManagerHarness harness = ToolManagerHarness.onDebian12(cacheRoot)) {
            ArtefactRecord record = harness.recordOf(ToolName.PERCOLATOR, "3.07.1");
            harness.mirror().serving(record);
            harness.probe()
                    .refusing(
                            record,
                            ProbeFailureKind.UNPARSEABLE_VERSION,
                            "printed no version this build recognises");

            RecordingListener listener = new RecordingListener();
            harness.manager().install(ToolName.PERCOLATOR, ToolVersion.parse("3.07.1"), listener);

            assertEquals(InstallPhase.FAILED, listener.awaitTerminal());
            ToolOffer offer = harness.offerOf(ToolName.PERCOLATOR, "3.07.1");
            assertAll(
                    () -> assertEquals(ToolInstallState.FAILED, offer.state()),
                    () ->
                            assertEquals(
                                    Optional.empty(),
                                    offer.loaderDiagnostic(),
                                    "an identity failure is not a loader failure and must not be"
                                            + " rendered as one"),
                    () -> assertEquals(0, harness.refusals().size()),
                    () ->
                            assertEquals(
                                    InstallPhase.DONE,
                                    retryWithAWorkingProbe(harness, record),
                                    "and the build can still be installed, which is what makes"
                                            + " this different from a loadability refusal"));
        }
    }

    private InstallPhase retryWithAWorkingProbe(ToolManagerHarness harness, ArtefactRecord record) {
        harness.probe().observing(ToolName.PERCOLATOR, XML_CAPABLE);
        harness.probe().stopRefusing(record);
        return harness.install(ToolName.PERCOLATOR, "3.07.1");
    }

    /*
     * THE ORDER THE ROW SETTLES IN.  A user interface refreshing from the terminal callback is the
     * ordinary case, and a finished install still described as running is what a row settled
     * afterwards would show.  The offers are read from inside the terminal report itself, which is
     * the earliest moment any caller can read them.
     */
    @Test
    @DisplayName("the row has stopped saying INSTALLING by the time the terminal report arrives")
    void theRowSettlesBeforeTheTerminalReportIsForwarded() throws IOException {
        try (ToolManagerHarness harness = ToolManagerHarness.onDebian12(cacheRoot)) {
            ArtefactRecord record = harness.recordOf(ToolName.PERCOLATOR, "3.06.5");
            harness.mirror().serving(record);
            harness.probe().observing(ToolName.PERCOLATOR, XML_CAPABLE);
            List<String> seenAtTheEnd = new ArrayList<>();
            List<String> seenWhileRunning = new ArrayList<>();
            RecordingListener listener =
                    new RecordingListener(
                            progress -> {
                                List<String> into =
                                        progress.phase().isTerminal()
                                                ? seenAtTheEnd
                                                : seenWhileRunning;
                                if (into.isEmpty()) {
                                    into.addAll(harness.describeOffers());
                                }
                            });

            harness.manager().install(ToolName.PERCOLATOR, ToolVersion.parse("3.06.5"), listener);

            assertAll(
                    () -> assertEquals(InstallPhase.DONE, listener.awaitTerminal()),
                    () ->
                            assertTrue(
                                    seenWhileRunning.contains("percolator 3.06.5 INSTALLING"),
                                    () ->
                                            "an install in flight has to be visible as one: "
                                                    + seenWhileRunning),
                    () ->
                            assertTrue(
                                    seenAtTheEnd.contains("percolator 3.06.5 INSTALLED"),
                                    () ->
                                            "and by the terminal report it is finished, not still"
                                                    + " running: "
                                                    + seenAtTheEnd));
        }
    }

    @Test
    @DisplayName("cancelling neither records a failure nor clears one, and installing clears it")
    void whatCancellingRemembers() throws IOException {
        try (ToolManagerHarness harness = ToolManagerHarness.onDebian12(cacheRoot)) {
            ArtefactRecord record = harness.recordOf(ToolName.PERCOLATOR, "3.07.1");
            harness.mirror().servingCorrupted(record);

            InstallPhase failed = harness.install(ToolName.PERCOLATOR, "3.07.1");
            ToolInstallState afterFailing = harness.offerOf(ToolName.PERCOLATOR, "3.07.1").state();
            InstallPhase cancelled = cancelAtOnce(harness);
            ToolInstallState afterCancelling =
                    harness.offerOf(ToolName.PERCOLATOR, "3.07.1").state();

            harness.mirror().serving(record);
            harness.probe().observing(ToolName.PERCOLATOR, XML_CAPABLE);
            InstallPhase done = harness.install(ToolName.PERCOLATOR, "3.07.1");

            assertAll(
                    () -> assertEquals(InstallPhase.FAILED, failed),
                    () -> assertEquals(ToolInstallState.FAILED, afterFailing),
                    () -> assertEquals(InstallPhase.CANCELLED, cancelled),
                    () ->
                            assertEquals(
                                    ToolInstallState.FAILED,
                                    afterCancelling,
                                    "cancelling says nothing about whether the last attempt"
                                            + " worked, so it must not clear the failure either"),
                    () -> assertEquals(InstallPhase.DONE, done),
                    () ->
                            assertEquals(
                                    ToolInstallState.INSTALLED,
                                    harness.offerOf(ToolName.PERCOLATOR, "3.07.1").state(),
                                    "and a successful install forgets it"));
        }
    }

    @Test
    @DisplayName("a cancelled install of a build that never failed leaves the row not-installed")
    void cancellingAloneRemembersNothing() throws IOException {
        try (ToolManagerHarness harness = ToolManagerHarness.onDebian12(cacheRoot)) {
            harness.mirror().serving(harness.recordOf(ToolName.PERCOLATOR, "3.07.1"));

            assertAll(
                    () -> assertEquals(InstallPhase.CANCELLED, cancelAtOnce(harness)),
                    () ->
                            assertEquals(
                                    ToolInstallState.NOT_INSTALLED,
                                    harness.offerOf(ToolName.PERCOLATOR, "3.07.1").state(),
                                    "a user who cancelled has not encountered an error"));
        }
    }

    /*
     * R-TOOL-04 makes an entry installed only when its recorded checksums MATCH, so an entry nobody
     * can hash is not an installed one -- and one such entry must not blank the rest of the list,
     * which is the rule ProbeGatedOffers already holds for a binary that cannot be reached.  The
     * install has to happen first for this to bite at all: with an empty cache ToolCache.verify
     * answers NOT_PRESENT before it hashes anything, so a version of this test that skipped the
     * install would pass with the hashing seam never entered.  The failure is produced through that
     * seam rather than through file permissions, so it is the same on every machine and does not
     * quietly stop happening when a suite runs as root.
     */
    @Test
    @DisplayName("an installed entry that cannot be hashed is not installed, and the list survives")
    void anUnreadableEntryIsNotInstalled() throws IOException {
        try (ToolManagerHarness installing = ToolManagerHarness.onDebian12(cacheRoot)) {
            installing.mirror().serving(installing.recordOf(ToolName.PERCOLATOR, "3.07.1"));
            installing.probe().observing(ToolName.PERCOLATOR, XML_CAPABLE);
            assertEquals(InstallPhase.DONE, installing.install(ToolName.PERCOLATOR, "3.07.1"));
            assertEquals(
                    ToolInstallState.INSTALLED,
                    installing.offerOf(ToolName.PERCOLATOR, "3.07.1").state(),
                    "the entry really is installed, so the next harness has something to fail on");
        }
        try (ToolManagerHarness unreadable =
                ToolManagerHarness.hashingWith(
                        cacheRoot,
                        file -> {
                            throw new IOException("refusing to read " + file);
                        })) {
            assertEquals(
                    List.of(
                            "comet 2026.02.2 NOT_INSTALLED",
                            "percolator 3.07.1 NOT_INSTALLED",
                            "percolator 3.06.5 NOT_INSTALLED",
                            "percolator 3.09 UNAVAILABLE_ON_THIS_PLATFORM",
                            "pdv 2.7.0 NOT_INSTALLED",
                            "limelight-converter 2.8.1 NOT_INSTALLED"),
                    unreadable.describeOffers(),
                    "the entry that cannot be verified reads as not installed, and every other row"
                            + " is still there");
        }
    }

    /**
     * Cancels a Percolator install at the first byte the transfer reports.
     *
     * <p>The pipeline asks whether the caller still wants the install <em>before every step</em>,
     * so a cancellation that arrives while a small artefact is already on disk still stops the
     * install -- which is why this is deterministic on a 946 KB file rather than a race.
     *
     * @param harness the harness
     * @return the phase the install ended in
     */
    private static InstallPhase cancelAtOnce(ToolManagerHarness harness) {
        AtomicReference<InstallHandle> handle = new AtomicReference<>();
        RecordingListener listener =
                new RecordingListener(
                        progress -> {
                            InstallHandle running = handle.get();
                            if (running != null) {
                                running.cancel();
                            }
                        });
        handle.set(harness.installWith(ToolName.PERCOLATOR, "3.07.1", listener));
        return listener.awaitTerminal();
    }

    private static List<String> capabilityIds(ToolOffer offer) {
        List<String> ids = new ArrayList<>();
        for (DeclaredCapability declared : offer.capabilities()) {
            ids.add(declared.capability().id());
        }
        ids.sort(String::compareTo);
        return ids;
    }
}
