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

package org.cometgui.ui.viewmodel;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import org.cometgui.domain.tools.InstallPhase;
import org.cometgui.domain.tools.InstallProgress;
import org.cometgui.domain.tools.ToolInstallState;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolOrigin;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.ui.testing.Nulls;
import org.cometgui.ui.testing.ScriptedToolManager;
import org.cometgui.ui.testing.ToolOffers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * One row's rendering, with every expected string typed out.
 *
 * <p>Nothing here builds its expectation by calling the class under test, and nothing calls the
 * domain to produce one either -- the {@code R-PLAT-03} diagnostic's sentence is written in full
 * below rather than taken from {@code LoaderDiagnostic.message()}, because a test that asked the
 * domain for the sentence would pass whatever the domain said, which is exactly what "the
 * diagnostic reaches the screen verbatim" is supposed to rule out.
 */
class ToolRowViewModelTest {

    /*
     * AN ABSOLUTE PATH THAT IS NOT A LITERAL.  SpotBugs rejects Path.of("/opt/...") as
     * DMI_HARDCODED_ABSOLUTE_FILENAME, and it is right to: an absolute path written into source is
     * not portable.  ToolOffer requires an absolute installed path, so the fixture builds one from
     * relative segments the way the domain's own tests do.  What the assertions below pin is
     * therefore the wording the view contributes -- "Installed at: " -- with the path itself an
     * input rather than an expectation.
     */
    private static final Path INSTALLED_AT =
            Path.of("opt", "cometgui", "tools", "comet", "2026.2.2", "bin", "comet")
                    .toAbsolutePath();

    /** The sentence {@link ToolOffers#hostRequirementDiagnostic()} renders, typed out in full. */
    private static final String EXPECTED_DIAGNOSTIC =
            "This build cannot run on this host: libstdc++.so.6 on this host does not provide a"
                    + " symbol version this build needs. Required: GLIBCXX_3.4.29. Available on"
                    + " this host: GLIBCXX_3.4.28. Alternatives: percolator 3.07.1; register a"
                    + " local binary.";

    @Test
    @DisplayName("a row names the tool and the version as upstream spells it")
    void aRowNamesTheToolAndTheVersion() {
        ToolRowViewModel row = row(ToolOffers.percolatorAvailable());

        assertEquals("percolator 3.07.1", row.nameText());
    }

    @Test
    @DisplayName("the state line says where the build came from and what state it is in")
    void theStateLineSaysOriginAndState() {
        assertAll(
                () ->
                        assertEquals(
                                "CometGUI-managed: not installed",
                                row(ToolOffers.percolatorAvailable()).stateText()),
                () ->
                        assertEquals(
                                "CometGUI-managed: installed",
                                row(ToolOffers.cometInstalled(INSTALLED_AT)).stateText()),
                () ->
                        assertEquals(
                                "CometGUI-managed: not published for this platform",
                                row(ToolOffers.percolatorUnavailableHere()).stateText()),
                () ->
                        assertEquals(
                                "CometGUI-managed: cannot run on this host",
                                row(ToolOffers.percolatorBeyondThisHost()).stateText()),
                () ->
                        assertEquals(
                                "CometGUI-managed: the last install attempt did not succeed",
                                row(ToolOffers.pdvFailed()).stateText()),
                () ->
                        assertEquals(
                                "Your own binary: installed",
                                row(ToolOffers.localPercolator(INSTALLED_AT)).stateText()));
    }

    @Test
    @DisplayName("a failed row carries no reason, because the port gives it none")
    void aFailedRowCarriesNoReason() {
        ToolRowViewModel row = row(ToolOffers.pdvFailed());

        assertAll(
                () ->
                        assertEquals(
                                Optional.empty(),
                                row.diagnosticText(),
                                "the only reason-carrying component of an offer is its loader"
                                        + " diagnostic, and a checksum, extraction or probe failure"
                                        + " sets none; inventing a sentence here would be this"
                                        + " layer making one up"),
                () ->
                        assertFalse(
                                row.stateText().contains("because"),
                                () -> "the state line claims a cause: " + row.stateText()));
    }

    @Test
    @DisplayName("every capability is shown with the evidence behind it")
    void everyCapabilityIsShownWithItsEvidence() {
        assertAll(
                () ->
                        assertEquals(
                                "Capabilities: XML_OUTPUT (observed-by-execution),"
                                        + " XML_DECOY_OUTPUT (observed-by-execution)",
                                row(ToolOffers.percolatorAvailable()).capabilitiesText()),
                () ->
                        assertEquals(
                                "Capabilities: PSM_TSV_OUTPUT (unverified)",
                                row(ToolOffers.localPercolator(INSTALLED_AT)).capabilitiesText(),
                                "an unprobed local binary must not look like a probed one"),
                () ->
                        assertEquals(
                                "Capabilities: none declared",
                                row(ToolOffers.pdvFailed()).capabilitiesText()));
    }

    @Test
    @DisplayName("advisories reach the row verbatim, one per line (R-PERC-11)")
    void advisoriesReachTheRowVerbatim() {
        assertAll(
                () ->
                        assertEquals(
                                "Advisories:\n"
                                        + "Percolator 3.07.1 predates 3.08's change of the default"
                                        + " PEP regressor to I-splines, so its posterior error"
                                        + " probabilities are computed the older way.\n"
                                        + "Percolator 3.07.1 predates the fix for PEP values"
                                        + " exceeding 1.0 (upstream issue #394, fixed in 3.08.1 and"
                                        + " 3.09), so a PEP above 1.0 can appear in its output.",
                                row(ToolOffers.percolatorAvailable()).advisoriesText()),
                () ->
                        assertEquals(
                                "Advisories: none",
                                row(ToolOffers.cometInstalled(INSTALLED_AT)).advisoriesText()));
    }

    @Test
    @DisplayName("the download line is the whole transfer, grouped the same way on every machine")
    void theDownloadLineIsTheWholeTransfer() {
        assertAll(
                () ->
                        assertEquals(
                                "Download: 2,798,963 bytes",
                                row(ToolOffers.percolatorAvailable()).downloadText(),
                                "946,303 for the portable zip and 1,852,660 for the package the"
                                        + " two XSD companions come out of"),
                () ->
                        assertEquals(
                                "Download: nothing to fetch on this host",
                                row(ToolOffers.percolatorUnavailableHere()).downloadText()),
                () ->
                        assertEquals(
                                "Download: nothing to fetch on this host",
                                row(ToolOffers.localPercolator(INSTALLED_AT)).downloadText()));
    }

    @Test
    @DisplayName("the R-PLAT-03 diagnostic is passed through word for word")
    void theLoaderDiagnosticIsPassedThroughWordForWord() {
        assertAll(
                () ->
                        assertEquals(
                                Optional.of(EXPECTED_DIAGNOSTIC),
                                row(ToolOffers.percolatorBeyondThisHost()).diagnosticText()),
                () ->
                        assertEquals(
                                Optional.empty(),
                                row(ToolOffers.percolatorAvailable()).diagnosticText()));
    }

    @Test
    @DisplayName("an installed row names where the tool is, and one that is not installed does not")
    void anInstalledRowNamesWhereTheToolIs() {
        assertAll(
                () ->
                        assertEquals(
                                Optional.of("Installed at: " + INSTALLED_AT),
                                row(ToolOffers.cometInstalled(INSTALLED_AT)).installedPathText()),
                () ->
                        assertEquals(
                                Optional.empty(),
                                row(ToolOffers.percolatorAvailable()).installedPathText()));
    }

    @Test
    @DisplayName("a row shows nothing about progress until an install reports")
    void aRowShowsNoProgressUntilAnInstallReports() {
        ToolRowViewModel row = row(ToolOffers.percolatorAvailable());

        assertEquals(Optional.empty(), row.progressText());
    }

    @Test
    @DisplayName("a progress report becomes the phase and the bytes moved")
    void aProgressReportBecomesThePhaseAndTheBytes() {
        ToolRowViewModel row = row(ToolOffers.percolatorAvailable());

        row.reportProgress(
                new InstallProgress(
                        ToolName.PERCOLATOR,
                        ToolVersion.parse("3.07.1"),
                        InstallPhase.DOWNLOADING,
                        500_000L,
                        2_798_963L));
        Optional<String> known = row.progressText();
        row.reportProgress(
                new InstallProgress(
                        ToolName.PERCOLATOR,
                        ToolVersion.parse("3.07.1"),
                        InstallPhase.EXTRACTING,
                        946_303L,
                        -1L));

        assertAll(
                () -> assertEquals(Optional.of("Downloading: 500,000 of 2,798,963 bytes"), known),
                () -> assertEquals(Optional.of("Extracting: 946,303 bytes"), row.progressText()));
    }

    @Test
    @DisplayName("only a managed build that is not installed, or whose attempt failed, can be got")
    void onlyAFetchableBuildOffersInstall() {
        Map<ToolInstallState, Boolean> expected = new EnumMap<>(ToolInstallState.class);
        expected.put(ToolInstallState.NOT_INSTALLED, true);
        expected.put(ToolInstallState.FAILED, true);
        expected.put(ToolInstallState.INSTALLING, false);
        expected.put(ToolInstallState.INSTALLED, false);
        expected.put(ToolInstallState.UNAVAILABLE_ON_THIS_PLATFORM, false);
        expected.put(ToolInstallState.HOST_REQUIREMENTS_NOT_MET, false);

        Map<ToolInstallState, Boolean> actual = new EnumMap<>(ToolInstallState.class);
        for (ToolInstallState state : ToolInstallState.values()) {
            actual.put(state, row(managedPercolatorIn(state)).canInstall());
        }

        assertAll(
                () -> assertEquals(expected, actual),
                () ->
                        assertFalse(
                                row(ToolOffers.localPercolator(INSTALLED_AT)).canInstall(),
                                "a binary the user pointed at was never downloaded and cannot be"));
    }

    @Test
    @DisplayName("Cancel is offered only while an install this row started is running")
    void cancelIsOfferedOnlyWhileAnInstallIsRunning() {
        ToolRowViewModel notStarted = row(managedPercolatorIn(ToolInstallState.INSTALLING));
        ToolRowViewModel started = row(ToolOffers.percolatorAvailable());
        ScriptedToolManager.RecordingInstallHandle handle =
                new ScriptedToolManager.RecordingInstallHandle();
        started.installStarted(handle);
        boolean beforeTheStateMoves = started.canCancel();
        started.update(managedPercolatorIn(ToolInstallState.INSTALLING));

        assertAll(
                () ->
                        assertFalse(
                                notStarted.canCancel(),
                                "a row that holds no handle has nothing to stop, however the port"
                                        + " reports the build"),
                () ->
                        assertFalse(
                                beforeTheStateMoves,
                                "a handle alone is not an install: the port has to say it is"
                                        + " running"),
                () -> assertTrue(started.canCancel()));
    }

    @Test
    @DisplayName("cancelling reaches the handle the install returned, once per ask")
    void cancellingReachesTheHandle() {
        ToolRowViewModel row = row(ToolOffers.percolatorAvailable());
        ScriptedToolManager.RecordingInstallHandle handle =
                new ScriptedToolManager.RecordingInstallHandle();

        row.cancelInstall();
        int beforeAnyInstall = handle.cancellations();
        row.installStarted(handle);
        row.cancelInstall();
        row.cancelInstall();
        int whileRunning = handle.cancellations();
        row.installFinished();
        row.cancelInstall();

        assertAll(
                () -> assertEquals(0, beforeAnyInstall),
                () -> assertEquals(2, whileRunning),
                () ->
                        assertEquals(
                                2,
                                handle.cancellations(),
                                "an install that has finished has no handle to ask again"));
    }

    @Test
    @DisplayName("a newer answer from the port replaces what the row shows")
    void aNewerAnswerReplacesWhatTheRowShows() {
        ToolRowViewModel row = row(ToolOffers.percolatorAvailable());
        String before = row.stateText();

        row.update(managedPercolatorIn(ToolInstallState.INSTALLING));

        assertAll(
                () -> assertEquals("CometGUI-managed: not installed", before),
                () -> assertEquals("CometGUI-managed: installing", row.stateText()),
                () ->
                        assertEquals(
                                ToolInstallState.INSTALLING,
                                row.offerProperty().get().state(),
                                "the property a view watches moves with it"));
    }

    @Test
    @DisplayName("starting an install forgets the progress of the attempt before it")
    void startingAnInstallForgetsTheEarlierProgress() {
        ToolRowViewModel row = row(ToolOffers.pdvFailed());
        row.reportProgress(
                new InstallProgress(
                        ToolName.PDV,
                        ToolVersion.parse("2.7.0"),
                        InstallPhase.FAILED,
                        12L,
                        103_407_417L));
        Optional<String> stale = row.progressText();

        row.installStarted(new ScriptedToolManager.RecordingInstallHandle());

        assertAll(
                () -> assertEquals(Optional.of("Failed: 12 of 103,407,417 bytes"), stale),
                () -> assertEquals(Optional.empty(), row.progressText()));
    }

    @Test
    @DisplayName("every state, origin and phase has words, and they are these words")
    void everyStateOriginAndPhaseHasWords() {
        Map<String, String> states = new LinkedHashMap<>();
        for (ToolInstallState state : ToolInstallState.values()) {
            states.put(state.name(), ToolRowViewModel.stateWords(state));
        }
        Map<String, String> origins = new LinkedHashMap<>();
        for (ToolOrigin origin : ToolOrigin.values()) {
            origins.put(origin.name(), ToolRowViewModel.originWords(origin));
        }
        Map<String, String> phases = new LinkedHashMap<>();
        for (InstallPhase phase : InstallPhase.values()) {
            phases.put(phase.name(), ToolRowViewModel.phaseWords(phase));
        }

        assertAll(
                () ->
                        assertEquals(
                                Map.of(
                                        "NOT_INSTALLED", "not installed",
                                        "INSTALLING", "installing",
                                        "INSTALLED", "installed",
                                        "FAILED", "the last install attempt did not succeed",
                                        "UNAVAILABLE_ON_THIS_PLATFORM",
                                                "not published for this platform",
                                        "HOST_REQUIREMENTS_NOT_MET", "cannot run on this host"),
                                states),
                () ->
                        assertEquals(
                                Map.of("MANAGED", "CometGUI-managed", "LOCAL", "Your own binary"),
                                origins),
                () ->
                        assertEquals(
                                Map.of(
                                        "DOWNLOADING", "Downloading",
                                        "VERIFYING", "Verifying the download",
                                        "EXTRACTING", "Extracting",
                                        "INSTALLING", "Installing",
                                        "PROBING", "Probing",
                                        "DONE", "Done",
                                        "CANCELLED", "Cancelled",
                                        "FAILED", "Failed"),
                                phases));
    }

    @Test
    @DisplayName("a row describes itself by its key, its build and its state")
    void aRowDescribesItself() {
        assertEquals(
                "ToolRowViewModel[percolator-3_07_1-1, percolator 3.07.1 NOT_INSTALLED]",
                row(ToolOffers.percolatorAvailable()).toString());
    }

    @Test
    @DisplayName("a row rejects a null or blank key, a null offer and null arguments")
    void aRowRejectsNullsAndABlankKey() {
        ToolRowViewModel row = row(ToolOffers.percolatorAvailable());
        assertAll(
                () ->
                        assertThrows(
                                NullPointerException.class,
                                () -> new ToolRowViewModel(null, ToolOffers.percolatorAvailable())),
                () ->
                        assertThrows(
                                IllegalArgumentException.class,
                                () -> new ToolRowViewModel("  ", ToolOffers.percolatorAvailable())),
                () ->
                        assertThrows(
                                NullPointerException.class,
                                () -> new ToolRowViewModel("k", Nulls.of(ToolOffer.class))),
                () ->
                        assertThrows(
                                NullPointerException.class,
                                () -> row.update(Nulls.of(ToolOffer.class))),
                () ->
                        assertThrows(
                                NullPointerException.class,
                                () -> row.reportProgress(Nulls.of(InstallProgress.class))),
                () ->
                        assertThrows(
                                NullPointerException.class,
                                () -> ToolRowViewModel.stateWords(null)),
                () ->
                        assertThrows(
                                NullPointerException.class,
                                () -> ToolRowViewModel.originWords(null)),
                () ->
                        assertThrows(
                                NullPointerException.class,
                                () -> ToolRowViewModel.phaseWords(null)),
                () ->
                        assertThrows(
                                NullPointerException.class,
                                () -> ToolRowViewModel.progressTextFor(null)));
    }

    /** A row over one offer, with the key {@code ToolManagerViewModel} would give it. */
    private static ToolRowViewModel row(ToolOffer offer) {
        return new ToolRowViewModel(
                offer.tool().id() + "-" + offer.version().text().replace('.', '_') + "-1", offer);
    }

    /** Percolator 3.07.1 as the port would report it in one particular state. */
    private static ToolOffer managedPercolatorIn(ToolInstallState state) {
        ToolOffer available = ToolOffers.percolatorAvailable();
        return new ToolOffer(
                available.tool(),
                available.version(),
                available.origin(),
                state,
                available.capabilities(),
                available.advisories(),
                state == ToolInstallState.HOST_REQUIREMENTS_NOT_MET
                        ? Optional.of(ToolOffers.hostRequirementDiagnostic())
                        : Optional.empty(),
                state == ToolInstallState.INSTALLED ? Optional.of(INSTALLED_AT) : Optional.empty(),
                state == ToolInstallState.UNAVAILABLE_ON_THIS_PLATFORM
                        ? OptionalLong.empty()
                        : available.downloadSizeBytes());
    }
}
