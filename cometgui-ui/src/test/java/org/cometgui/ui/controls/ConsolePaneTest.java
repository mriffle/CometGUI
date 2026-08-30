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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleButton;
import org.cometgui.domain.log.BoundedMessageLog;
import org.cometgui.domain.log.LogMessage;
import org.cometgui.domain.log.MessageSeverity;
import org.cometgui.domain.run.StageTag;
import org.cometgui.ui.controls.derived.ConsolePane;
import org.cometgui.ui.testing.FxToolkit;
import org.cometgui.ui.viewmodel.ConsoleViewModel;
import org.cometgui.workflow.state.WorkflowStage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The console pane: what it shows, what it says about what it is not showing, and the coalescing it
 * inherits from CasanovoGUI.
 *
 * <p>A test of derived code is not itself derived material, so this class lives outside the {@code
 * derived} path and carries the ordinary licence header -- see {@code CONTRIBUTING.rst}, <em>Files
 * derived from CasanovoGUI</em>. It therefore sees only the pane's public interface, which is the
 * right amount: what matters is the rendered document, the summary sentence and the flush count.
 *
 * <p>The flood test that proves the heap stays bounded under {@code R-PROC-03} belongs to the unit
 * that builds the UI driver; what is proved here is the mechanism that flood test depends on.
 */
class ConsolePaneTest {

    private BoundedMessageLog log;

    private ConsoleViewModel viewModel;

    private ConsolePane pane;

    private Scene scene;

    @BeforeAll
    static void startToolkit() throws InterruptedException {
        FxToolkit.start();
    }

    /** Builds a console over a log of the given capacity, with the given document cap. */
    private void build(int logCapacity, int renderCap) throws InterruptedException {
        log = new BoundedMessageLog(logCapacity);
        viewModel = new ConsoleViewModel(log);
        FxToolkit.onFxThread(
                () -> {
                    pane = new ConsolePane(viewModel, List.of(WorkflowStage.values()), renderCap);
                    scene = new Scene(pane, 900, 400);
                    scene.getRoot().applyCss();
                    scene.getRoot().layout();
                });
    }

    /** The four messages the filter tests are written against. */
    private void appendTheSampleRun() {
        append(WorkflowStage.COMET, MessageSeverity.INFO, "searching 3 spectrum files");
        append(WorkflowStage.COMET, MessageSeverity.STDERR, "progress 50%");
        append(WorkflowStage.PERCOLATOR, MessageSeverity.WARNING, "few decoys");
        append(null, MessageSeverity.ERROR, "the run could not be finalised");
    }

    @Test
    @DisplayName("the console shows exactly the messages the filters admit, severity first")
    void theConsoleShowsExactlyWhatTheFiltersAdmit() throws InterruptedException {
        build(64, ConsolePane.DEFAULT_MAX_RENDERED_LINES);
        appendTheSampleRun();

        FxToolkit.onFxThread(pane::refreshNow);

        assertEquals(
                List.of(
                        "INFO    [comet] searching 3 spectrum files",
                        "STDERR  [comet] progress 50%",
                        "WARNING [percolator] few decoys",
                        "ERROR   the run could not be finalised"),
                documentLines());
        assertEquals("No earlier lines discarded. Showing 4 matching lines.", summary().getText());
        assertEquals(summary().getText(), summary().getAccessibleText());
    }

    @Test
    @DisplayName("the stage filter shows one stage, and hides messages belonging to no stage")
    void theStageFilterShowsOneStageOnly() throws InterruptedException {
        build(64, ConsolePane.DEFAULT_MAX_RENDERED_LINES);
        appendTheSampleRun();
        FxToolkit.onFxThread(pane::refreshNow);

        fire(UiIds.consoleStageFilter(WorkflowStage.COMET));

        assertAll(
                () ->
                        assertEquals(
                                List.of(
                                        "INFO    [comet] searching 3 spectrum files",
                                        "STDERR  [comet] progress 50%"),
                                documentLines()),
                () ->
                        assertEquals(
                                "No earlier lines discarded. Showing 2 matching lines.",
                                summary().getText()),
                () ->
                        assertTrue(
                                toggle(UiIds.consoleStageFilter(WorkflowStage.COMET)).isSelected()),
                () -> assertFalse(toggle(UiIds.CONSOLE_STAGE_FILTER_ALL).isSelected()));

        fire(UiIds.CONSOLE_STAGE_FILTER_ALL);

        assertEquals(4, documentLines().size(), "'all stages' puts every message back");
        assertTrue(toggle(UiIds.CONSOLE_STAGE_FILTER_ALL).isSelected());
    }

    @Test
    @DisplayName("the minimum-severity filter hides everything less severe")
    void theSeverityFilterHidesLessSevereMessages() throws InterruptedException {
        build(64, ConsolePane.DEFAULT_MAX_RENDERED_LINES);
        appendTheSampleRun();
        FxToolkit.onFxThread(pane::refreshNow);

        fire(UiIds.consoleSeverityFilter(MessageSeverity.WARNING));

        assertEquals(
                List.of(
                        "WARNING [percolator] few decoys",
                        "ERROR   the run could not be finalised"),
                documentLines());
        assertEquals(MessageSeverity.WARNING, viewModel.minimumSeverity());

        fire(UiIds.consoleSeverityFilter(MessageSeverity.ERROR));

        assertEquals(List.of("ERROR   the run could not be finalised"), documentLines());
        assertEquals(
                "No earlier lines discarded. Showing 1 matching line.",
                summary().getText(),
                "one line is singular");
    }

    @Test
    @DisplayName("every stage and every severity has a filter button, with an accessible name")
    void everyStageAndSeverityHasAFilterButton() throws InterruptedException {
        build(8, 8);
        for (WorkflowStage stage : WorkflowStage.values()) {
            ToggleButton button = toggle(UiIds.consoleStageFilter(stage));
            assertAll(
                    stage.id(),
                    () -> assertNotNull(button, "no filter button for " + stage.id()),
                    () -> assertEquals(stage.displayName(), button.getText()),
                    () ->
                            assertEquals(
                                    "show messages from the " + stage.displayName() + " stage only",
                                    button.getAccessibleText()));
        }
        for (MessageSeverity severity : MessageSeverity.values()) {
            assertNotNull(
                    toggle(UiIds.consoleSeverityFilter(severity)),
                    "no filter button for severity " + severity);
        }
        assertTrue(
                toggle(UiIds.CONSOLE_STAGE_FILTER_ALL).isSelected(),
                "a fresh console shows every stage");
        assertTrue(
                toggle(UiIds.consoleSeverityFilter(MessageSeverity.INFO)).isSelected(),
                "a fresh console shows every severity");
    }

    @Test
    @DisplayName("the summary says how many lines the log's cap threw away")
    void theSummaryReportsWhatTheLogsCapDiscarded() throws InterruptedException {
        build(3, ConsolePane.DEFAULT_MAX_RENDERED_LINES);
        for (int i = 1; i <= 5; i++) {
            append(WorkflowStage.COMET, MessageSeverity.INFO, "line " + i);
        }

        FxToolkit.onFxThread(pane::refreshNow);

        assertEquals(
                List.of(
                        "INFO    [comet] line 3",
                        "INFO    [comet] line 4",
                        "INFO    [comet] line 5"),
                documentLines());
        assertEquals("2 earlier lines discarded. Showing 3 matching lines.", summary().getText());
    }

    @Test
    @DisplayName("the document cap keeps the newest lines and says what it is not showing")
    void theDocumentCapTruncatesTheOldestAndSaysSo() throws InterruptedException {
        build(64, 10);
        for (int i = 1; i <= 20; i++) {
            append(WorkflowStage.COMET, MessageSeverity.INFO, "line " + i);
        }

        FxToolkit.onFxThread(pane::refreshNow);

        List<String> lines = documentLines();
        assertAll(
                () -> assertEquals(10, lines.size(), "the document holds the cap and no more"),
                () -> assertEquals("INFO    [comet] line 11", lines.get(0)),
                () -> assertEquals("INFO    [comet] line 20", lines.get(9)),
                () ->
                        assertEquals(
                                "No earlier lines discarded. Showing the newest 10 of 20 matching"
                                        + " lines.",
                                summary().getText()));
    }

    @Test
    @DisplayName("clearing empties the run's log as well as the document")
    void clearingEmptiesTheLogAndTheDocument() throws InterruptedException {
        build(64, ConsolePane.DEFAULT_MAX_RENDERED_LINES);
        appendTheSampleRun();
        FxToolkit.onFxThread(pane::refreshNow);
        assertEquals(4, documentLines().size());

        fireButton(UiIds.CONSOLE_CLEAR);

        assertAll(
                () -> assertEquals(List.of(), documentLines()),
                () -> assertEquals(0, log.size()),
                () -> assertEquals(0L, log.discardedCount()),
                () ->
                        assertEquals(
                                "No earlier lines discarded. Showing 0 matching lines.",
                                summary().getText()));
    }

    @Test
    @DisplayName("five hundred refresh requests during one busy pulse cost exactly one flush")
    void requestRefreshCoalescesManyRequestsIntoOneFlush() throws InterruptedException {
        build(1024, ConsolePane.DEFAULT_MAX_RENDERED_LINES);
        long before = pane.flushCount();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean releasedInTime = new AtomicBoolean();
        Platform.runLater(
                () -> {
                    entered.countDown();
                    try {
                        releasedInTime.set(
                                release.await(FxToolkit.TIMEOUT_SECONDS, TimeUnit.SECONDS));
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    }
                });
        assertTrue(
                entered.await(FxToolkit.TIMEOUT_SECONDS, TimeUnit.SECONDS),
                "the blocking task never reached the application thread");

        for (int i = 1; i <= 500; i++) {
            append(WorkflowStage.COMET, MessageSeverity.INFO, "line " + i);
            pane.requestRefresh();
        }
        release.countDown();
        FxToolkit.drainFxQueue();

        assertTrue(releasedInTime.get(), "the application thread was never released");
        assertEquals(
                before + 1,
                pane.flushCount(),
                "500 requests arriving while the application thread was busy must coalesce into"
                        + " one flush");
        assertEquals(500, documentLines().size(), "and that one flush shows all 500 lines");
    }

    @Test
    @DisplayName(
            "refreshNow off the application thread is refused rather than corrupting the scene")
    void refreshNowOffTheApplicationThreadIsRefused() throws InterruptedException {
        build(8, 8);
        IllegalStateException wrongThread =
                assertThrows(IllegalStateException.class, () -> pane.refreshNow());
        assertTrue(
                wrongThread.getMessage().contains("requestRefresh()"),
                () -> "the diagnostic must say what to do instead: " + wrongThread.getMessage());
    }

    @Test
    @DisplayName("a document cap below one line is refused")
    void aDocumentCapBelowOneLineIsRefused() throws InterruptedException {
        ConsoleViewModel model = new ConsoleViewModel(new BoundedMessageLog(4));
        IllegalArgumentException tooSmall =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new ConsolePane(model, List.of(WorkflowStage.values()), 0));
        assertTrue(tooSmall.getMessage().contains("0"), tooSmall.getMessage());
    }

    private void append(StageTag stage, MessageSeverity severity, String text) {
        log.append(LogMessage.at(Instant.parse("2026-08-30T12:00:00Z"), stage, severity, text));
    }

    /** Fires a filter toggle the way a click would, on the application thread. */
    private void fire(String id) throws InterruptedException {
        FxToolkit.onFxThread(() -> toggle(id).fire());
    }

    private void fireButton(String id) throws InterruptedException {
        FxToolkit.onFxThread(() -> ((Button) scene.lookup("#" + id)).fire());
    }

    private ToggleButton toggle(String id) {
        return (ToggleButton) scene.lookup("#" + id);
    }

    private Label summary() {
        return (Label) scene.lookup("#" + UiIds.CONSOLE_SUMMARY);
    }

    /** The console document, one entry per rendered line, with no trailing blank. */
    private List<String> documentLines() {
        TextArea output = (TextArea) scene.lookup("#" + UiIds.CONSOLE_OUTPUT);
        String text = output.getText();
        List<String> lines = new ArrayList<>();
        for (String line : text.split(System.lineSeparator(), -1)) {
            lines.add(line);
        }
        while (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
            lines.remove(lines.size() - 1);
        }
        return List.copyOf(lines);
    }
}
