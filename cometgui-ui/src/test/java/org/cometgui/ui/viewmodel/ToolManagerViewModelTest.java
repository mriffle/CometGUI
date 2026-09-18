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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import org.cometgui.domain.tools.InstallHandle;
import org.cometgui.domain.tools.InstallPhase;
import org.cometgui.domain.tools.InstallProgress;
import org.cometgui.domain.tools.InstallProgressListener;
import org.cometgui.domain.tools.ToolManager;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.ui.testing.Nulls;
import org.cometgui.ui.testing.ScriptedToolManager;
import org.cometgui.ui.testing.ToolOffers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The Tool Manager over a scripted port, with no toolkit started.
 *
 * <p>{@code Runnable::run} stands in for the interface thread throughout, which is the whole point
 * of that seam: every sequence below is deterministic and nothing here waits for a frame.
 */
class ToolManagerViewModelTest {

    /*
     * Absolute, and not a literal: SpotBugs rejects a hard-coded absolute path
     * (DMI_HARDCODED_ABSOLUTE_FILENAME) and ToolOffer requires the installed path to be absolute,
     * so the fixture builds one from relative segments as the domain's own tests do.
     */
    private static final Path INSTALLED_AT =
            Path.of("opt", "cometgui", "tools", "comet", "2026.2.2", "bin", "comet")
                    .toAbsolutePath();

    private static final Path LOCAL_PERCOLATOR =
            Path.of("usr", "local", "bin", "percolator").toAbsolutePath();

    @Test
    @DisplayName("a fresh Tool Manager has read nothing and says so")
    void aFreshToolManagerHasReadNothing() {
        ToolManagerViewModel viewModel =
                new ToolManagerViewModel(
                        new ScriptedToolManager(ToolOffers.percolatorAvailable()), Runnable::run);

        assertAll(
                () -> assertEquals(List.of(), viewModel.rows()),
                () -> assertEquals("The tool list has not been read yet.", viewModel.summary()),
                () -> assertTrue(viewModel.isAvailable()),
                () -> assertEquals(Optional.empty(), viewModel.unavailableReason()));
    }

    @Test
    @DisplayName("refresh maps the port's offers to rows, one for one and in the port's order")
    void refreshMapsOffersToRowsInOrder() {
        ToolManagerViewModel viewModel = over(manager());

        viewModel.refresh();

        assertAll(
                () ->
                        assertEquals(
                                List.of(
                                        "comet-2026_02_2-1",
                                        "percolator-3_09-1",
                                        "percolator-3_07_1-1",
                                        "pdv-2_7_0-1",
                                        "percolator-3_05-1"),
                                keysOf(viewModel)),
                () ->
                        assertEquals(
                                List.of(
                                        "comet 2026.02.2",
                                        "percolator 3.09",
                                        "percolator 3.07.1",
                                        "pdv 2.7.0",
                                        "percolator 3.05"),
                                viewModel.rows().stream().map(ToolRowViewModel::nameText).toList()),
                () -> assertEquals("5 tool builds on this host.", viewModel.summary()));
    }

    @Test
    @DisplayName("two builds of one release get two keys, so neither row hides the other")
    void twoBuildsOfOneReleaseGetTwoKeys() {
        List<ToolOffer> twice = ToolOffers.oneReleaseOfferedTwice();
        ToolManagerViewModel viewModel = over(new ScriptedToolManager(twice.get(0), twice.get(1)));

        viewModel.refresh();

        assertAll(
                () ->
                        assertEquals(
                                List.of("comet-2026_02_2-1", "comet-2026_02_2-2"),
                                keysOf(viewModel),
                                "on Apple silicon Comet 2026.02.2 is two published builds and both"
                                        + " are offered; a key built from the tool and the version"
                                        + " alone would name both rows with one string"),
                () -> assertNotEquals(viewModel.rows().get(0).key(), viewModel.rows().get(1).key()),
                () -> assertEquals("2 tool builds on this host.", viewModel.summary()));
    }

    @Test
    @DisplayName("a row whose build is still offered keeps its identity across a refresh")
    void aRowKeepsItsIdentityAcrossARefresh() {
        ScriptedToolManager manager = new ScriptedToolManager(ToolOffers.percolatorAvailable());
        ToolManagerViewModel viewModel = over(manager);
        viewModel.refresh();
        ToolRowViewModel first = viewModel.rows().get(0);

        manager.answerWith(ToolOffers.percolatorAvailable(), ToolOffers.pdvFailed());
        viewModel.refresh();

        assertAll(
                () -> assertSame(first, viewModel.rows().get(0)),
                () -> assertEquals("pdv-2_7_0-1", viewModel.rows().get(1).key()),
                () -> assertEquals("2 tool builds on this host.", viewModel.summary()));
    }

    @Test
    @DisplayName("installing asks the port for that build and the rows then say it is installing")
    void installingAsksThePortAndTheRowsFollow() {
        ScriptedToolManager manager =
                new ScriptedToolManager(
                        ToolOffers.cometInstalled(INSTALLED_AT), ToolOffers.percolatorAvailable());
        ToolManagerViewModel viewModel = over(manager);
        viewModel.refresh();
        ToolRowViewModel percolator = viewModel.rows().get(1);

        viewModel.install(percolator);

        assertAll(
                () -> assertEquals(List.of("percolator 3.07.1"), manager.installsAsked()),
                () -> assertEquals("CometGUI-managed: installing", percolator.stateText()),
                () ->
                        assertSame(
                                percolator,
                                viewModel.rows().get(1),
                                "the row that started the install is the row that is still shown,"
                                        + " which is what lets it be cancelled from there"),
                () -> assertTrue(percolator.canCancel()),
                () ->
                        assertEquals(
                                "CometGUI-managed: installed",
                                viewModel.rows().get(0).stateText(),
                                "no other row moved"));
    }

    @Test
    @DisplayName("progress reaches the row, and only a terminal report reads the port again")
    void progressReachesTheRowAndOnlyTerminalReportsReadThePortAgain() {
        CountingToolManager manager =
                new CountingToolManager(new ScriptedToolManager(ToolOffers.percolatorAvailable()));
        ToolManagerViewModel viewModel = over(manager);
        viewModel.refresh();
        ToolRowViewModel row = viewModel.rows().get(0);
        viewModel.install(row);
        int readsBeforeAnyProgress = manager.reads();

        manager.report(progress(InstallPhase.DOWNLOADING, 500_000L));
        Optional<String> midInstall = row.progressText();
        int readsAfterOneChunk = manager.reads();
        manager.report(progress(InstallPhase.DONE, 2_798_963L));

        assertAll(
                () ->
                        assertEquals(
                                Optional.of("Downloading: 500,000 of 2,798,963 bytes"), midInstall),
                () ->
                        assertEquals(
                                readsBeforeAnyProgress,
                                readsAfterOneChunk,
                                "reading the offered list verifies every installed tool against its"
                                        + " recorded checksums; doing that once per transferred"
                                        + " chunk would freeze the interface it is updating"),
                () ->
                        assertEquals(
                                readsBeforeAnyProgress + 1,
                                manager.reads(),
                                "a terminal phase is where the port has something new to say"),
                () ->
                        assertEquals(
                                Optional.of("Done: 2,798,963 of 2,798,963 bytes"),
                                row.progressText()),
                () ->
                        assertFalse(
                                row.canCancel(),
                                "an install that has finished has no handle left to ask"));
    }

    @Test
    @DisplayName("cancelling a row reaches the handle its install returned")
    void cancellingARowReachesTheHandle() {
        ScriptedToolManager manager = new ScriptedToolManager(ToolOffers.percolatorAvailable());
        ToolManagerViewModel viewModel = over(manager);
        viewModel.refresh();
        ToolRowViewModel row = viewModel.rows().get(0);
        viewModel.install(row);

        int beforeCancelling = manager.handle().cancellations();
        viewModel.cancel(row);

        assertAll(
                () -> assertEquals(0, beforeCancelling),
                () -> assertEquals(1, manager.handle().cancellations()));
    }

    @Test
    @DisplayName("a Tool Manager that is not available on this host holds no rows and says why")
    void anUnavailableToolManagerSaysWhy() {
        ToolManagerViewModel viewModel =
                ToolManagerViewModel.unavailable(
                        "CometGUI manages tool installations on 64-bit Linux, macOS and Windows.",
                        Runnable::run);

        viewModel.refresh();

        assertAll(
                () -> assertEquals(List.of(), viewModel.rows()),
                () -> assertFalse(viewModel.isAvailable()),
                () ->
                        assertEquals(
                                Optional.of(
                                        "CometGUI manages tool installations on 64-bit Linux, macOS"
                                                + " and Windows."),
                                viewModel.unavailableReason()),
                () ->
                        assertEquals(
                                "CometGUI manages tool installations on 64-bit Linux, macOS and"
                                        + " Windows.",
                                viewModel.summary()),
                () ->
                        assertThrows(
                                IllegalStateException.class,
                                () ->
                                        viewModel.install(
                                                new ToolRowViewModel(
                                                        "percolator-3_07_1-1",
                                                        ToolOffers.percolatorAvailable()))));
    }

    @Test
    @DisplayName("a row this Tool Manager is not showing cannot be acted on")
    void aForeignRowCannotBeActedOn() {
        ToolManagerViewModel viewModel =
                over(new ScriptedToolManager(ToolOffers.percolatorAvailable()));
        viewModel.refresh();
        ToolRowViewModel foreign =
                new ToolRowViewModel("percolator-3_07_1-1", ToolOffers.percolatorAvailable());

        assertAll(
                () ->
                        assertThrows(
                                IllegalArgumentException.class, () -> viewModel.install(foreign)),
                () ->
                        assertThrows(
                                IllegalArgumentException.class, () -> viewModel.cancel(foreign)));
    }

    @Test
    @DisplayName("the published row list cannot be changed from outside")
    void thePublishedRowListCannotBeChangedFromOutside() {
        ToolManagerViewModel viewModel =
                over(new ScriptedToolManager(ToolOffers.percolatorAvailable()));
        viewModel.refresh();

        assertThrows(
                UnsupportedOperationException.class,
                () -> viewModel.rows().remove(0),
                "a view that could delete a row would be a second answer to what is offered");
    }

    @Test
    @DisplayName("the summary counts builds, and a count that cannot be is rejected")
    void theSummaryCountsBuilds() {
        assertAll(
                () ->
                        assertEquals(
                                "No tool builds on this host.", ToolManagerViewModel.summaryFor(0)),
                () ->
                        assertEquals(
                                "1 tool build on this host.", ToolManagerViewModel.summaryFor(1)),
                () ->
                        assertEquals(
                                "7 tool builds on this host.", ToolManagerViewModel.summaryFor(7)),
                () ->
                        assertThrows(
                                IllegalArgumentException.class,
                                () -> ToolManagerViewModel.summaryFor(-1)));
    }

    @Test
    @DisplayName("an empty offered list is a statement, not a blank section")
    void anEmptyOfferedListIsAStatement() {
        ToolManagerViewModel viewModel = over(new ScriptedToolManager());

        viewModel.refresh();

        assertAll(
                () -> assertEquals(List.of(), viewModel.rows()),
                () -> assertEquals("No tool builds on this host.", viewModel.summary()));
    }

    @Test
    @DisplayName("every argument is rejected when null, and an unavailable reason when blank")
    void everyArgumentIsRejectedWhenNull() {
        ToolManagerViewModel viewModel =
                over(new ScriptedToolManager(ToolOffers.percolatorAvailable()));
        assertAll(
                () ->
                        assertThrows(
                                NullPointerException.class,
                                () ->
                                        new ToolManagerViewModel(
                                                Nulls.of(ToolManager.class), Runnable::run)),
                () ->
                        assertThrows(
                                NullPointerException.class,
                                () ->
                                        new ToolManagerViewModel(
                                                new ScriptedToolManager(),
                                                Nulls.of(Executor.class))),
                () ->
                        assertThrows(
                                NullPointerException.class,
                                () -> ToolManagerViewModel.unavailable(null, Runnable::run)),
                () ->
                        assertThrows(
                                IllegalArgumentException.class,
                                () -> ToolManagerViewModel.unavailable("   ", Runnable::run)),
                () ->
                        assertThrows(
                                NullPointerException.class,
                                () -> viewModel.install(Nulls.of(ToolRowViewModel.class))),
                () ->
                        assertThrows(
                                NullPointerException.class,
                                () -> viewModel.cancel(Nulls.of(ToolRowViewModel.class))));
    }

    private static ToolManagerViewModel over(ToolManager tools) {
        return new ToolManagerViewModel(tools, Runnable::run);
    }

    private static ScriptedToolManager manager() {
        return new ScriptedToolManager(
                ToolOffers.cometInstalled(INSTALLED_AT),
                ToolOffers.percolatorUnavailableHere(),
                ToolOffers.percolatorAvailable(),
                ToolOffers.pdvFailed(),
                ToolOffers.localPercolator(LOCAL_PERCOLATOR));
    }

    private static List<String> keysOf(ToolManagerViewModel viewModel) {
        List<String> keys = new ArrayList<>();
        for (ToolRowViewModel row : viewModel.rows()) {
            keys.add(row.key());
        }
        return keys;
    }

    private static InstallProgress progress(InstallPhase phase, long bytes) {
        return new InstallProgress(
                ToolName.PERCOLATOR,
                ToolVersion.parse("3.07.1"),
                phase,
                bytes,
                ToolOffers.PERCOLATOR_3_07_1_LINUX_BYTES);
    }

    /** A manager that counts how often the offered list was read. */
    private static final class CountingToolManager implements ToolManager {

        private final ScriptedToolManager delegate;

        private int reads;

        CountingToolManager(ScriptedToolManager delegate) {
            this.delegate = delegate;
        }

        @Override
        public List<ToolOffer> offers() {
            reads++;
            return delegate.offers();
        }

        @Override
        public InstallHandle install(
                ToolName tool, ToolVersion version, InstallProgressListener listener) {
            return delegate.install(tool, version, listener);
        }

        @Override
        public ToolOffer registerLocalBinary(ToolName tool, Path executable) {
            return delegate.registerLocalBinary(tool, executable);
        }

        void report(InstallProgress progress) {
            delegate.report(progress);
        }

        int reads() {
            return reads;
        }
    }
}
