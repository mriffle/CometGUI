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

package org.cometgui.ui.controls;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.stage.Stage;
import org.cometgui.domain.tools.InstallPhase;
import org.cometgui.domain.tools.InstallProgress;
import org.cometgui.domain.tools.ToolInstallState;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolOrigin;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.ui.testing.Editors;
import org.cometgui.ui.testing.FxToolkit;
import org.cometgui.ui.testing.ScriptedToolManager;
import org.cometgui.ui.testing.ToolOffers;
import org.cometgui.ui.viewmodel.ToolManagerViewModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * An install that starts, ends or fails does not move the Tool Manager's list, and the keyboard
 * focus stays in the row the install belongs to.
 *
 * <p><strong>The defect this pins (Phase 16, unit 4c).</strong> A row disables the action that can
 * no longer be taken: Install once the build is installing, Cancel once the install has ended. The
 * action that was pressed holds the keyboard focus, and JavaFX does not leave the focus on a
 * disabled control: at the <em>next pulse</em> it moves it to the next control in traversal order
 * -- another build's Install -- and the scroll pane the rows are in scrolls that control into view.
 * A user who had scrolled down to another build while the first one installed had the list jump
 * back under the pointer the moment the first install ended, and a press aimed at that other
 * build's Install landed on whatever was there instead. The end-to-end suite lost exactly that
 * press, intermittently ({@code NetworkUnavailableUiTest.theNetworkBack}: Percolator's Install was
 * aimed after Comet's row said "installed" and before the pulse that moved the list).
 *
 * <p><strong>Why each test is deterministic.</strong> The view-model is given {@code Runnable::run}
 * for both executors, so a report and the read it starts are applied in the task that raises them;
 * and the pulse in which JavaFX would move the focus is awaited through the scene's own post-layout
 * pulse listener, never by waiting a length of time. The window is shown, because a scene in no
 * window has no pulse at all and the defect could not appear.
 */
class ToolManagerRowFocusTest {

    /** How long to wait for a pulse that should arrive within a frame. A deadlock detector. */
    private static final long TIMEOUT_SECONDS = 30;

    /** The row whose install these tests start: the first row of the list. */
    private static final String FIRST = "percolator-3_07_1-1";

    /** The row after it: where JavaFX would move the focus from the first row's last action. */
    private static final String SECOND = "comet-2026_03_0-1";

    /** A build further down: the one a user scrolls to while the first one installs. */
    private static final String LATER = "comet-2025_03_0-1";

    private static final Path INSTALLED_AT =
            Path.of("opt", "cometgui", "tools", "percolator", "3.7.1", "bin", "percolator")
                    .toAbsolutePath();

    private static final List<String> COMET_RELEASES =
            List.of("2026.03.0", "2026.02.2", "2026.01.1", "2025.03.0", "2025.02.0", "2024.01.0");

    private ScriptedToolManager manager;

    private ToolManagerViewModel viewModel;

    private Scene scene;

    private Stage stage;

    @BeforeAll
    static void startToolkit() throws InterruptedException {
        FxToolkit.start();
    }

    @BeforeEach
    void show() throws InterruptedException {
        manager = new ScriptedToolManager(offers(ToolOffers.percolatorAvailable()));
        viewModel =
                new ToolManagerViewModel(
                        manager, Runnable::run, Runnable::run, new Editors.ScriptedChooser());
        FxToolkit.onFxThread(
                () -> {
                    scene = new Scene(new ToolManagerPane(viewModel), 1024, 420);
                    stage = new Stage();
                    stage.setScene(scene);
                    stage.show();
                    viewModel.refresh();
                    settle();
                });
        pulse();
    }

    @AfterEach
    void close() throws InterruptedException {
        FxToolkit.onFxThread(stage::close);
    }

    @Test
    @DisplayName(
            "an install that ends while the user has scrolled to another build leaves the list"
                    + " where it is, and the focus on the finished row's state")
    void anInstallThatEndsLeavesTheListWhereTheUserScrolledIt() throws InterruptedException {
        pressInstallAndScrollToALaterBuild();
        double scrolledTo = scrollOffset();
        boolean firstRowWasOutOfView = !inView(UiIds.toolRowCancel(FIRST));
        boolean laterBuildWasInView = inView(UiIds.toolRowInstall(LATER));
        double laterBuildWasAt = placeOf(UiIds.toolRowInstall(LATER));
        double firstRowWas = heightOf(UiIds.toolRow(FIRST));

        String focusAtOnce = end(InstallPhase.DONE, installed());
        pulse();
        double grewBy = heightOf(UiIds.toolRow(FIRST)) - firstRowWas;

        assertAll(
                () ->
                        assertTrue(
                                firstRowWasOutOfView,
                                "the precondition: the installing row was scrolled out of view,"
                                        + " so moving the focus to its neighbour would scroll"),
                () ->
                        assertTrue(
                                laterBuildWasInView,
                                "the precondition: the build scrolled to was in view"),
                () ->
                        assertEquals(
                                "CometGUI-managed: installed",
                                textOf(UiIds.toolRowState(FIRST)),
                                "the precondition: the row was read again as installed"),
                () ->
                        assertEquals(
                                UiIds.toolRowState(FIRST),
                                focusAtOnce,
                                "the focus is handed to the finished row's state as its Cancel"
                                        + " is disabled, not left for the next pulse to move"),
                () ->
                        assertEquals(
                                UiIds.toolRowState(FIRST),
                                focusOwner(),
                                "and after the pulse it is still there: it did not go to another"
                                        + " build's Install"),
                () ->
                        assertEquals(
                                scrolledTo,
                                scrollOffset(),
                                0.5,
                                "the list was not scrolled: it is at the offset, in pixels, the"
                                        + " user left it at"),
                () ->
                        assertTrue(
                                inView(UiIds.toolRowInstall(LATER)),
                                "the build the user scrolled to is still in view"),
                () ->
                        assertTrue(
                                grewBy > 0,
                                () ->
                                        "the precondition: the finished row grew (by "
                                                + grewBy
                                                + " px), having gained its installed path"),
                () ->
                        assertEquals(
                                grewBy,
                                placeOf(UiIds.toolRowInstall(LATER)) - laterBuildWasAt,
                                0.5,
                                "and its Install moved down by what the finished row above it"
                                        + " grew by, and by nothing else"));
    }

    @Test
    @DisplayName(
            "an install that fails hands the focus to that row's Install, to try again, and"
                    + " does not scroll the list")
    void aFailedInstallHandsTheFocusToThatRowsInstall() throws InterruptedException {
        pressInstallAndScrollToALaterBuild();
        double scrolledTo = scrollOffset();

        String focusAtOnce =
                end(
                        InstallPhase.FAILED,
                        ToolOffers.failed(
                                ToolOffers.percolatorAvailable(),
                                "Install step 2 of 8, VERIFY_SHA256, failed: digest mismatch"));
        pulse();

        assertAll(
                () ->
                        assertEquals(
                                "CometGUI-managed: the last install attempt did not succeed",
                                textOf(UiIds.toolRowState(FIRST))),
                () -> assertEquals(UiIds.toolRowInstall(FIRST), focusAtOnce),
                () ->
                        assertEquals(
                                UiIds.toolRowInstall(FIRST),
                                focusOwner(),
                                "the focus is on the Install of the build that failed, not on"
                                        + " the next build's"),
                () -> assertEquals(scrolledTo, scrollOffset(), 0.5, "the list was not scrolled"));
    }

    @Test
    @DisplayName(
            "a cancelled install hands the focus back to that row's Install and does not scroll"
                    + " the list")
    void aCancelledInstallHandsTheFocusBackToThatRowsInstall() throws InterruptedException {
        pressInstallAndScrollToALaterBuild();
        double scrolledTo = scrollOffset();

        String focusAtOnce = end(InstallPhase.CANCELLED, ToolOffers.percolatorAvailable());
        pulse();

        assertAll(
                () ->
                        assertEquals(
                                "CometGUI-managed: not installed",
                                textOf(UiIds.toolRowState(FIRST))),
                () -> assertEquals(UiIds.toolRowInstall(FIRST), focusAtOnce),
                () -> assertEquals(UiIds.toolRowInstall(FIRST), focusOwner()),
                () -> assertEquals(scrolledTo, scrollOffset(), 0.5, "the list was not scrolled"));
    }

    @Test
    @DisplayName("pressing Install hands the focus to that row's Cancel as Install is disabled")
    void pressingInstallHandsTheFocusToThatRowsCancel() throws InterruptedException {
        String focusAtOnce = pressInstall();
        pulse();

        assertAll(
                () ->
                        assertEquals(
                                "CometGUI-managed: installing", textOf(UiIds.toolRowState(FIRST))),
                () ->
                        assertEquals(
                                UiIds.toolRowCancel(FIRST),
                                focusAtOnce,
                                "in the task that disabled Install, not at the next pulse"),
                () -> assertEquals(UiIds.toolRowCancel(FIRST), focusOwner()),
                () ->
                        assertFalse(
                                ((Button) node(UiIds.toolRowCancel(FIRST))).isDisabled(),
                                "and the action that now has the focus can be taken"));
    }

    @Test
    @DisplayName(
            "a build read as installed while its Install holds the focus hands the focus to its"
                    + " state: an install too quick for the row ever to say installing")
    void aBuildReadAsInstalledHandsTheFocusFromInstallToItsState() throws InterruptedException {
        String focusAtOnce =
                FxToolkit.callOnFxThread(
                        () -> {
                            node(UiIds.toolRowInstall(FIRST)).requestFocus();
                            manager.answerWith(offers(installed()));
                            viewModel.refresh();
                            return idOf(scene.getFocusOwner());
                        });
        pulse();

        assertAll(
                () ->
                        assertEquals(
                                "CometGUI-managed: installed", textOf(UiIds.toolRowState(FIRST))),
                () -> assertEquals(UiIds.toolRowState(FIRST), focusAtOnce),
                () ->
                        assertEquals(
                                UiIds.toolRowState(FIRST),
                                focusOwner(),
                                "neither action of the row can be taken, so the focus is on its"
                                        + " state and not on the next build's Install"));
    }

    @Test
    @DisplayName("an install that ends takes the focus from nowhere else")
    void anInstallThatEndsTakesTheFocusFromNowhereElse() throws InterruptedException {
        pressInstall();
        pulse();
        FxToolkit.onFxThread(() -> node(UiIds.toolRowInstall(SECOND)).requestFocus());

        String focusAtOnce = end(InstallPhase.DONE, installed());
        pulse();

        assertAll(
                () ->
                        assertEquals(
                                "CometGUI-managed: installed", textOf(UiIds.toolRowState(FIRST))),
                () -> assertEquals(UiIds.toolRowInstall(SECOND), focusAtOnce),
                () ->
                        assertEquals(
                                UiIds.toolRowInstall(SECOND),
                                focusOwner(),
                                "the user had moved to another build's Install, and the focus"
                                        + " is still there"));
    }

    // ------------------------------------------------------------------------- the scenario --

    /**
     * Gives the first row's Install the focus and presses it, as a click or the space bar does.
     *
     * @return the identifier of the focus owner in the task that pressed it, before any pulse
     */
    private String pressInstall() throws InterruptedException {
        return FxToolkit.callOnFxThread(
                () -> {
                    Button install = (Button) node(UiIds.toolRowInstall(FIRST));
                    install.requestFocus();
                    install.fire();
                    return idOf(scene.getFocusOwner());
                });
    }

    /**
     * Starts the first row's install, shows one progress report, and scrolls the list until a later
     * build's Install is in the middle of the viewport -- what a user does who goes on to another
     * build while the first one downloads.
     */
    private void pressInstallAndScrollToALaterBuild() throws InterruptedException {
        pressInstall();
        pulse();
        FxToolkit.onFxThread(
                () -> {
                    manager.report(report(InstallPhase.DOWNLOADING));
                    settle();
                    ScrollPane scroll = scroller();
                    Node content = scroll.getContent();
                    Node later = node(UiIds.toolRowInstall(LATER));
                    double place =
                            content.sceneToLocal(later.localToScene(later.getBoundsInLocal()))
                                    .getMinY();
                    double viewport = scroll.getViewportBounds().getHeight();
                    double height = content.getBoundsInLocal().getHeight();
                    scroll.setVvalue((place - viewport / 2) / (height - viewport));
                    settle();
                });
        pulse();
        assertEquals(
                UiIds.toolRowCancel(FIRST),
                focusOwner(),
                "the precondition: the focus is on the installing row's Cancel");
    }

    /**
     * Ends the first row's install: the port answers with the row as it now is, and the terminal
     * report arrives.
     *
     * @param terminal the phase the install ended in
     * @param now the first row's offer after it
     * @return the identifier of the focus owner in the task that applied it, before any pulse
     */
    private String end(InstallPhase terminal, ToolOffer now) throws InterruptedException {
        return FxToolkit.callOnFxThread(
                () -> {
                    manager.answerWith(offers(now));
                    manager.report(report(terminal));
                    return idOf(scene.getFocusOwner());
                });
    }

    private static InstallProgress report(InstallPhase phase) {
        return new InstallProgress(
                ToolName.PERCOLATOR,
                ToolVersion.parse("3.07.1"),
                phase,
                1_048_576L,
                ToolOffers.PERCOLATOR_3_07_1_LINUX_BYTES);
    }

    /** The first row's offer, then six Comet releases nobody has installed. */
    private static ToolOffer[] offers(ToolOffer first) {
        ToolOffer[] offers = new ToolOffer[COMET_RELEASES.size() + 1];
        offers[0] = first;
        for (int index = 0; index < COMET_RELEASES.size(); index++) {
            offers[index + 1] =
                    new ToolOffer(
                            ToolName.COMET,
                            ToolVersion.parse(COMET_RELEASES.get(index)),
                            ToolOrigin.MANAGED,
                            ToolInstallState.NOT_INSTALLED,
                            List.of(),
                            List.of(),
                            Optional.empty(),
                            Optional.empty(),
                            OptionalLong.of(7_077_008L));
        }
        return offers;
    }

    private static ToolOffer installed() {
        ToolOffer available = ToolOffers.percolatorAvailable();
        return new ToolOffer(
                available.tool(),
                available.version(),
                available.origin(),
                ToolInstallState.INSTALLED,
                available.capabilities(),
                available.advisories(),
                Optional.empty(),
                Optional.of(INSTALLED_AT),
                available.downloadSizeBytes());
    }

    // ---------------------------------------------------------------------------- the scene --

    /**
     * Waits for one whole pulse of the scene: the focus clean-up, the CSS pass and the layout pass.
     */
    private void pulse() throws InterruptedException {
        CountDownLatch pulsed = new CountDownLatch(1);
        Runnable counted = pulsed::countDown;
        FxToolkit.onFxThread(
                () -> {
                    scene.addPostLayoutPulseListener(counted);
                    Platform.requestNextPulse();
                });
        boolean ran = pulsed.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        FxToolkit.onFxThread(() -> scene.removePostLayoutPulseListener(counted));
        assertTrue(
                ran,
                "the shown scene ran no pulse within "
                        + TIMEOUT_SECONDS
                        + "s, so nothing here would have been graded after JavaFX's focus"
                        + " clean-up");
    }

    private void settle() {
        scene.getRoot().applyCss();
        scene.getRoot().layout();
    }

    private Node node(String id) {
        Node found = scene.lookup("#" + id);
        assertNotNull(found, "no control with id " + id);
        return found;
    }

    private ScrollPane scroller() {
        for (Node above = node(UiIds.TOOL_MANAGER_ROWS); above != null; above = above.getParent()) {
            if (above instanceof ScrollPane scroll) {
                return scroll;
            }
        }
        throw new AssertionError("the Tool Manager's rows are in no scroll pane");
    }

    private String textOf(String id) throws InterruptedException {
        return FxToolkit.callOnFxThread(() -> ((javafx.scene.control.Label) node(id)).getText());
    }

    private String focusOwner() throws InterruptedException {
        return FxToolkit.callOnFxThread(() -> idOf(scene.getFocusOwner()));
    }

    private static String idOf(Node node) {
        return node == null ? "nothing" : String.valueOf(node.getId());
    }

    /** How far the list is scrolled, in pixels of its content above the viewport's top edge. */
    private double scrollOffset() throws InterruptedException {
        return FxToolkit.callOnFxThread(
                () -> {
                    settle();
                    ScrollPane scroll = scroller();
                    double content = scroll.getContent().getBoundsInLocal().getHeight();
                    double viewport = scroll.getViewportBounds().getHeight();
                    assertTrue(
                            content > viewport + 200,
                            () ->
                                    "the list ("
                                            + content
                                            + " px) must be much taller than its viewport ("
                                            + viewport
                                            + " px), or nothing here can scroll");
                    return scroll.getVvalue() * (content - viewport);
                });
    }

    /** Where a control's top edge is in the window, in pixels from the top of the scene. */
    private double placeOf(String id) throws InterruptedException {
        return FxToolkit.callOnFxThread(
                () -> {
                    settle();
                    Node control = node(id);
                    return control.localToScene(control.getBoundsInLocal()).getMinY();
                });
    }

    private double heightOf(String id) throws InterruptedException {
        return FxToolkit.callOnFxThread(
                () -> {
                    settle();
                    return node(id).getBoundsInLocal().getHeight();
                });
    }

    /** Whether any part of a control is inside the scroll pane's bounds in the window. */
    private boolean inView(String id) throws InterruptedException {
        return FxToolkit.callOnFxThread(
                () -> {
                    settle();
                    ScrollPane scroll = scroller();
                    Node control = node(id);
                    Bounds window = scroll.localToScene(scroll.getBoundsInLocal());
                    Bounds place = control.localToScene(control.getBoundsInLocal());
                    return window.intersects(place);
                });
    }
}
