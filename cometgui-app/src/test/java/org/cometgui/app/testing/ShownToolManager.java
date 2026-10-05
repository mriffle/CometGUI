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

package org.cometgui.app.testing;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Labeled;
import javafx.stage.Stage;
import org.cometgui.app.uidriver.FxUiDriver;
import org.cometgui.app.uidriver.RunningApplication;
import org.cometgui.app.uidriver.TestFxUiDriver;
import org.cometgui.domain.log.BoundedMessageLog;
import org.cometgui.domain.platform.HostBaselineOutcome;
import org.cometgui.domain.platform.HostBaselineReport;
import org.cometgui.domain.tools.InstallPhase;
import org.cometgui.domain.tools.InstallProgress;
import org.cometgui.domain.tools.ToolManager;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.ui.controls.UiIds;
import org.cometgui.ui.view.ShellView;
import org.cometgui.ui.viewmodel.ConsoleViewModel;
import org.cometgui.ui.viewmodel.HostBaselineViewModel;
import org.cometgui.ui.viewmodel.NavigationViewModel;
import org.cometgui.ui.viewmodel.SectionId;
import org.cometgui.ui.viewmodel.StageStepperViewModel;
import org.cometgui.ui.viewmodel.ToolManagerViewModel;
import org.cometgui.ui.viewmodel.ToolRowViewModel;

/**
 * The product's Tool Manager section on a real, showing window, driven by its own controls.
 *
 * <h2>What this class may and may not do</h2>
 *
 * <p>It presses <strong>controls</strong>: {@link #pressInstall} and {@link #pressCancel} look up
 * the {@link ButtonBase} the pane built, refuse to press one that is disabled, and fire it. Neither
 * calls {@code ToolManagerViewModel.install}, and nothing here calls {@link ToolManager} at all --
 * gate item 1 says "driven through the Tool Manager UI, not from a test helper", and a helper that
 * reached past the button would be exactly the thing the sentence excludes. Reading is a different
 * matter: assertions read the rendered labels and the row a label was rendered from, because a test
 * that could only read through the interface would be asserting its own echo.
 *
 * <h2>The interface thread is the production one</h2>
 *
 * <p>The view-model is built with {@link Platform#runLater}, not {@code Runnable::run}. A live
 * scene is watching these properties, an install reports from a {@code cometgui-install-N} thread,
 * and {@code ProgressReachesTheInterfaceThreadTest} is the unit that grades that hop. Waiting is
 * therefore done on an observable fact rather than on a sleep: {@link #awaitTerminal} blocks on a
 * latch that a listener counts down, and the listener counts it down through one more {@code
 * runLater} so that the view-model's own terminal handling -- which settles the row and reads the
 * port again -- has finished before the test looks.
 */
public final class ShownToolManager implements AutoCloseable {

    /** How long any single install may take before the wait is called a deadlock. */
    public static final long TERMINAL_TIMEOUT_SECONDS = 600;

    private final ToolManagerViewModel viewModel;

    private final Stage stage;

    private final Scene scene;

    /** The synthetic-input driver, built on first use. */
    private FxUiDriver driver;

    /** One watch per row key, attached the first time a test asks about that row. */
    private final Map<String, RowWatch> watches = new ConcurrentHashMap<>();

    private ShownToolManager(ToolManagerViewModel viewModel, Stage stage, Scene scene) {
        this.viewModel = viewModel;
        this.stage = stage;
        this.scene = scene;
    }

    /** Every progress report one row showed, and a latch the terminal one releases. */
    private static final class RowWatch {

        private final List<InstallProgress> reports =
                Collections.synchronizedList(new ArrayList<>());

        private volatile CountDownLatch terminal = new CountDownLatch(1);

        void record(Optional<InstallProgress> report) {
            report.ifPresent(
                    shown -> {
                        reports.add(shown);
                        if (shown.phase().isTerminal()) {
                            CountDownLatch waiting = terminal;
                            Platform.runLater(waiting::countDown);
                        }
                    });
        }

        void arm() {
            terminal = new CountDownLatch(1);
        }
    }

    /**
     * Builds the real shell over a Tool Manager, shows it, opens the Tool Manager section and reads
     * the port once -- which is the order {@code CometGuiApplication.start} uses.
     *
     * @param manager the port the section is built over
     * @param stage the stage to show the window on
     * @return the shown section
     * @throws InterruptedException if interrupted while waiting for the interface thread
     * @throws NullPointerException if either argument is {@code null}
     */
    public static ShownToolManager showing(ToolManager manager, Stage stage)
            throws InterruptedException {
        Objects.requireNonNull(manager, "manager");
        Objects.requireNonNull(stage, "stage");
        ToolManagerViewModel viewModel = new ToolManagerViewModel(manager, Platform::runLater);
        Scene[] built = new Scene[1];
        FxToolkit.onFxThread(
                () -> {
                    ShellView shell =
                            TestEditors.shell(
                                    new NavigationViewModel(),
                                    new HostBaselineViewModel(
                                            new HostBaselineReport(
                                                    HostBaselineOutcome.SUPPORTED,
                                                    "64-bit host, glibc 2.36.")),
                                    new StageStepperViewModel(),
                                    new ConsoleViewModel(new BoundedMessageLog(64)),
                                    viewModel);
                    built[0] = new Scene(shell, 1280, 800);
                    stage.setScene(built[0]);
                    stage.show();
                    built[0].getRoot().applyCss();
                    built[0].getRoot().layout();
                });
        ShownToolManager shown = new ShownToolManager(viewModel, stage, built[0]);
        shown.press(UiIds.navigationEntry(SectionId.TOOL_MANAGER));
        FxToolkit.onFxThread(viewModel::refresh);
        assertTrue(
                shown.isShowing(UiIds.TOOL_MANAGER_PANE),
                "the Tool Manager section is not on screen after its navigation entry was pressed");
        return shown;
    }

    /**
     * A driver that drives this window with <strong>synthetic input</strong>, as a user would.
     *
     * <p>Built here rather than by handing the caller the {@link Stage}: publishing the stage is
     * what SpotBugs reports as {@code EI_EXPOSE_REP}, and a test that had it could set a scene on
     * it behind this class's back. What a test wants is the robot, and it gets exactly that --
     * {@code clickOn(UiIds.toolRowInstall(key))} is a real pointer movement to the control's own
     * screen bounds and a real button press, not a fired action event.
     *
     * @return the driver, built once and reused
     */
    public FxUiDriver syntheticInput() {
        if (driver == null) {
            driver = new TestFxUiDriver(RunningApplication.showing(stage));
        }
        return driver;
    }

    /**
     * The row keys the section is showing, in the order the rows are drawn.
     *
     * @return the keys
     * @throws InterruptedException if interrupted while waiting for the interface thread
     */
    public List<String> rowKeys() throws InterruptedException {
        return FxToolkit.callOnFxThread(
                () -> viewModel.rows().stream().map(ToolRowViewModel::key).toList());
    }

    /**
     * Reads the port again, as the section does around an install.
     *
     * @throws InterruptedException if interrupted while waiting for the interface thread
     */
    public void refresh() throws InterruptedException {
        FxToolkit.onFxThread(viewModel::refresh);
    }

    /**
     * Presses one row's Install control.
     *
     * @param rowKey the row
     * @throws InterruptedException if interrupted while waiting for the interface thread
     */
    public void pressInstall(String rowKey) throws InterruptedException {
        FxToolkit.onFxThread(
                () -> {
                    watchOf(rowKey).arm();
                    fire(UiIds.toolRowInstall(rowKey));
                });
    }

    /**
     * Presses one row's Cancel control.
     *
     * @param rowKey the row
     * @throws InterruptedException if interrupted while waiting for the interface thread
     */
    public void pressCancel(String rowKey) throws InterruptedException {
        FxToolkit.onFxThread(() -> fire(UiIds.toolRowCancel(rowKey)));
    }

    /**
     * Presses any control by its stable identifier, on the interface thread.
     *
     * @param id the control's identifier
     * @throws InterruptedException if interrupted while waiting for the interface thread
     */
    public void press(String id) throws InterruptedException {
        FxToolkit.onFxThread(() -> fire(id));
    }

    /**
     * Starts watching one row's progress, so that nothing reported before the first wait is lost.
     *
     * @param rowKey the row
     * @throws InterruptedException if interrupted while waiting for the interface thread
     */
    public void watch(String rowKey) throws InterruptedException {
        FxToolkit.onFxThread(() -> watchOf(rowKey));
    }

    /**
     * Waits for one row's install to reach a terminal phase.
     *
     * @param rowKey the row
     * @return the terminal report the row showed
     * @throws InterruptedException if interrupted while waiting
     */
    public InstallProgress awaitTerminal(String rowKey) throws InterruptedException {
        RowWatch watch = FxToolkit.callOnFxThread(() -> watchOf(rowKey));
        if (!watch.terminal.await(TERMINAL_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            return fail(
                    "the row "
                            + rowKey
                            + " showed no terminal install phase within "
                            + TERMINAL_TIMEOUT_SECONDS
                            + "s; the phases it did show were "
                            + phases(rowKey));
        }
        List<InstallProgress> shown = reports(rowKey);
        return shown.get(shown.size() - 1);
    }

    /**
     * Every progress report one row has shown, in order.
     *
     * @param rowKey the row
     * @return the reports
     */
    public List<InstallProgress> reports(String rowKey) {
        RowWatch watch = watches.get(rowKey);
        return watch == null ? List.of() : List.copyOf(watch.reports);
    }

    /**
     * The phases one row has shown, in order, with repeated downloading reports collapsed.
     *
     * @param rowKey the row
     * @return the phases
     */
    public List<String> phases(String rowKey) {
        List<String> collapsed = new ArrayList<>();
        for (InstallProgress report : reports(rowKey)) {
            String phase = report.phase().name();
            if (collapsed.isEmpty() || !collapsed.get(collapsed.size() - 1).equals(phase)) {
                collapsed.add(phase);
            }
        }
        return List.copyOf(collapsed);
    }

    /**
     * The largest byte count any of one row's <em>downloading</em> reports showed.
     *
     * @param rowKey the row
     * @param from the first report to count, so that a second attempt can be measured on its own
     * @return the count, or zero if nothing was shown downloading from there
     */
    public long mostBytesShownDownloading(String rowKey, int from) {
        long most = 0L;
        for (InstallProgress report : downloadingReports(rowKey, from)) {
            most = Math.max(most, report.bytesTransferred());
        }
        return most;
    }

    /**
     * The smallest byte count any of one row's <em>downloading</em> reports showed.
     *
     * <p>This is what tells a restart from a resume: a transfer that begins again from zero shows a
     * small first number, and one that continues a partial file shows the offset it continued from.
     *
     * @param rowKey the row
     * @param from the first report to count
     * @return the count, or {@code -1} if nothing was shown downloading from there
     */
    public long leastBytesShownDownloading(String rowKey, int from) {
        long least = Long.MAX_VALUE;
        for (InstallProgress report : downloadingReports(rowKey, from)) {
            least = Math.min(least, report.bytesTransferred());
        }
        return least == Long.MAX_VALUE ? -1L : least;
    }

    /**
     * One row's downloading reports from a given point, in order.
     *
     * @param rowKey the row
     * @param from the first report to include
     * @return the reports
     */
    public List<InstallProgress> downloadingReports(String rowKey, int from) {
        List<InstallProgress> shown = reports(rowKey);
        List<InstallProgress> downloading = new ArrayList<>();
        for (int index = from; index < shown.size(); index++) {
            if (shown.get(index).phase() == InstallPhase.DOWNLOADING) {
                downloading.add(shown.get(index));
            }
        }
        return List.copyOf(downloading);
    }

    /**
     * How many reports one row has shown so far.
     *
     * @param rowKey the row
     * @return the count
     */
    public int reportCount(String rowKey) {
        return reports(rowKey).size();
    }

    /**
     * The offer one row is showing.
     *
     * @param rowKey the row
     * @return the offer
     * @throws InterruptedException if interrupted while waiting for the interface thread
     */
    public ToolOffer offer(String rowKey) throws InterruptedException {
        return FxToolkit.callOnFxThread(() -> rowOf(rowKey).offer());
    }

    /**
     * What one control says.
     *
     * @param id the control's identifier
     * @return its text
     * @throws InterruptedException if interrupted while waiting for the interface thread
     */
    public String textOf(String id) throws InterruptedException {
        return FxToolkit.callOnFxThread(
                () -> {
                    Node found = node(id);
                    if (found instanceof Labeled labeled) {
                        return labeled.getText();
                    }
                    return fail("#" + id + " has no text to read");
                });
    }

    /**
     * Whether one control is on screen: in a scene, managed, and under no invisible ancestor.
     *
     * @param id the control's identifier
     * @return {@code true} if a user can see it
     * @throws InterruptedException if interrupted while waiting for the interface thread
     */
    public boolean isShowing(String id) throws InterruptedException {
        return FxToolkit.callOnFxThread(
                () -> {
                    Node found = node(id);
                    if (found.getScene() == null || !found.isManaged()) {
                        return false;
                    }
                    for (Node above = found; above != null; above = above.getParent()) {
                        if (!above.isVisible()) {
                            return false;
                        }
                    }
                    return true;
                });
    }

    /**
     * Whether one control can be pressed.
     *
     * @param id the control's identifier
     * @return {@code true} if it is not disabled
     * @throws InterruptedException if interrupted while waiting for the interface thread
     */
    public boolean isEnabled(String id) throws InterruptedException {
        return FxToolkit.callOnFxThread(() -> !node(id).isDisabled());
    }

    @Override
    public void close() throws InterruptedException {
        FxToolkit.onFxThread(stage::hide);
    }

    /** Called on the interface thread. */
    private RowWatch watchOf(String rowKey) {
        return watches.computeIfAbsent(
                rowKey,
                key -> {
                    RowWatch watch = new RowWatch();
                    ToolRowViewModel row = rowOf(key);
                    watch.record(row.progressProperty().get());
                    row.progressProperty().addListener((property, was, now) -> watch.record(now));
                    return watch;
                });
    }

    /** Called on the interface thread. */
    private ToolRowViewModel rowOf(String rowKey) {
        for (ToolRowViewModel row : viewModel.rows()) {
            if (row.key().equals(rowKey)) {
                return row;
            }
        }
        return fail(
                "the Tool Manager is showing no row "
                        + rowKey
                        + "; it is showing "
                        + viewModel.rows().stream().map(ToolRowViewModel::key).toList());
    }

    /** Called on the interface thread. */
    private void fire(String id) {
        Node found = node(id);
        if (!(found instanceof ButtonBase control)) {
            fail("#" + id + " is a " + found.getClass().getName() + ", which cannot be pressed");
            return;
        }
        assertTrue(
                !control.isDisabled(),
                "#" + id + " is disabled, and a disabled control does nothing when it is pressed");
        control.fire();
    }

    /** Called on the interface thread. */
    private Node node(String id) {
        Node found = scene.lookup("#" + id);
        assertNotNull(found, "no control with the stable identifier #" + id + " is in the window");
        return found;
    }
}
