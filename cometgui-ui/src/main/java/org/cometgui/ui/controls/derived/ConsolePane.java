/*
 * CometGUI -- Comet to Percolator proteomics search workflow with provenance.
 * Copyright (C) 2026 The CometGUI authors.
 *
 * DERIVED FILE. This file is derived from Noble-Lab/CasanovoGUI and has been
 * modified for CometGUI. Upstream project:
 * <https://github.com/Noble-Lab/CasanovoGUI>, licensed GPL-3.0.
 * Copyright (C) the CasanovoGUI authors.
 *
 * The attribution above is collective because upstream carries no per-file
 * copyright notice: every CasanovoGUI source file begins with its package
 * statement, and `grep -rl Copyright --include=*.java src` in a clone of that
 * repository matches nothing. No notice was dropped in copying.
 *
 * WHICH upstream file, and at WHICH commit, is recorded per file in the
 * documentation comment below, because this header block is fixed and
 * identical in every derived file. config/checkstyle/checkstyle-derived.xml
 * requires that record and fails the build when it is missing.
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

package org.cometgui.ui.controls.derived;

import static org.cometgui.ui.controls.AccessibleControls.named;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.AccessibleRole;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.cometgui.domain.log.LogMessage;
import org.cometgui.domain.log.MessageSeverity;
import org.cometgui.domain.run.StageTag;
import org.cometgui.ui.controls.UiIds;
import org.cometgui.ui.viewmodel.ConsoleViewModel;

/**
 * The live console: a read-only text view of the run's message log, a stage filter, a
 * minimum-severity filter, and an honest statement of what is not being shown.
 *
 * <p>Derived from Noble-Lab/CasanovoGUI src/main/java/org/casanovo/gui/ui/ConsoleView.java at
 * commit 480b3013e7f8fb51a2b8c58681043821e3e7f865, GPL-3.0, modified.
 *
 * <h2>What was kept from upstream</h2>
 *
 * <p><b>The coalescing flush, which is the whole reason this file is derived rather than
 * written.</b> A tool that emits a hundred thousand lines in ten seconds emits them from the
 * threads reading its stdout and stderr, and the naive way to show them is one {@code
 * Platform.runLater} per line. That does not merely render slowly: it queues a hundred thousand
 * tasks on the JavaFX application thread, and the interface stops responding to the user for as
 * long as it takes to drain them -- including to the Cancel button, which is the one control that
 * matters when a run is going wrong. Upstream's answer is a flag and one scheduled task: a producer
 * sets {@code flushScheduled} under a lock and schedules a single {@code runLater} only if one is
 * not already pending, and that task drains everything that has accumulated. However many lines
 * arrive between two pulses, the application thread does one unit of work. {@link
 * #requestRefresh()} is that design, unchanged in shape: the {@code synchronized} block, the
 * "schedule only if not already scheduled" decision, and the single flush that clears the flag.
 *
 * <p><b>The trimming cap.</b> Upstream drops the oldest committed lines once the document exceeds
 * {@code MAX_LINES = 5000}, so a chatty run cannot grow the {@code TextArea}'s document without
 * bound. The same cap is here, as {@link #DEFAULT_MAX_RENDERED_LINES}, for the same reason and with
 * the same default. {@code R-PROC-03} requires the in-memory console to be bounded with a
 * documented retention policy; a text document holding every line a tool ever wrote would be
 * exactly the unbounded buffer the rule forbids, whatever the model behind it did.
 *
 * <p><b>The layout and the two actions.</b> A {@code BorderPane} with a heading above, the text
 * view filling the centre, and a toolbar below whose Clear and Copy buttons are pinned right past a
 * growing spacer.
 *
 * <h2>What was changed for CometGUI</h2>
 *
 * <p><b>The document is no longer the model.</b> Upstream's {@code TextArea} <em>was</em> the log:
 * text was appended to it, trimmed out of it, and read back out of it for Copy. Here the retained
 * messages live in {@code org.cometgui.domain.log.BoundedMessageLog} behind {@link
 * ConsoleViewModel}, which the process service, the run's log files and the provenance record all
 * see, and this pane renders a snapshot of what the filters admit. That inversion is what makes the
 * two filters possible at all -- a filter has to be able to show a message the user previously
 * filtered out, and a document that had thrown that message away cannot -- and it is why {@link
 * #refreshNow()} rewrites the whole document instead of appending to it. The cost is bounded by the
 * same cap upstream used; the benefit is that "what is retained" is a domain decision with a flood
 * test behind it rather than a side effect of a text control.
 *
 * <p><b>The coalesced unit is a request, not a line.</b> Upstream accumulated the text itself in a
 * {@code StringBuilder} under the lock. Nothing needs to be accumulated here, because the log
 * already holds every message and is already thread safe, so what is coalesced is the intention to
 * re-read it. A producer thread calls {@link #requestRefresh()} as often as it likes and pays for
 * one snapshot per pulse.
 *
 * <p><b>The transient progress line is gone.</b> Upstream kept an uncommitted trailing line so that
 * a tqdm progress bar refreshed in place. Comet and Percolator do not emit carriage-return progress
 * bars, and keeping the mechanism would have meant keeping {@code committedLen} -- an index into a
 * document that this pane rewrites wholesale, where it would be meaningless. The phase that meets a
 * tool which needs it should reintroduce it against the log, not against the document.
 *
 * <p><b>Two filters and an honest summary were added.</b> The information architecture asks for "a
 * persistent or collapsible live console that can filter messages by workflow stage"; the
 * minimum-severity filter is the second half of making a hundred thousand lines usable. Both are
 * rows of toggle buttons rather than combo boxes, because a combo box's skin builds controls of its
 * own that no caller can give an accessible name to, and this phase's gate item 4 requires every
 * control to have one. The summary line states what the log's cap discarded and, separately,
 * whether the document cap is showing only the newest part of what the filters matched -- so a
 * truncated log is never presented as a whole one, in either of the two ways it can be truncated.
 *
 * <p><b>Severity is text.</b> Every rendered line begins with its severity in words. The
 * specification's <em>Accessibility</em> principle forbids conveying state by colour alone, and a
 * console is the place that rule is most often broken.
 */
public final class ConsolePane extends BorderPane {

    /**
     * The most lines the text document holds, whatever the log retained. Upstream's {@code
     * MAX_LINES}, kept at its value.
     */
    public static final int DEFAULT_MAX_RENDERED_LINES = 5_000;

    /** Widest severity name, so the severity column lines up without a monospace font. */
    private static final int SEVERITY_COLUMN = 7;

    private final ConsoleViewModel viewModel;

    private final int maxRenderedLines;

    private final TextArea output = new TextArea();

    private final Label summary = new Label();

    private final ToggleButton allStagesButton = new ToggleButton("All stages");

    private final Map<StageTag, ToggleButton> stageButtons = new LinkedHashMap<>();

    private final Map<MessageSeverity, ToggleButton> severityButtons = new LinkedHashMap<>();

    /** Guards {@link #refreshScheduled}. Upstream synchronised on its pending buffer. */
    private final Object flushLock = new Object();

    /**
     * How many flushes have completed. Read from other threads, hence atomic; see {@link
     * #flushCount()} for why it is published at all.
     */
    private final AtomicLong flushes = new AtomicLong();

    /**
     * True while a flush is already queued on the application thread; see {@link
     * #requestRefresh()}.
     */
    private boolean refreshScheduled;

    /**
     * A console over the given view-model, offering a filter button for each of the given stages.
     *
     * @param viewModel the filters and the messages they admit
     * @param stages the stages the filter offers, in the order they are offered; copied
     * @throws NullPointerException if either argument, or any stage, is {@code null}
     */
    public ConsolePane(ConsoleViewModel viewModel, List<? extends StageTag> stages) {
        this(viewModel, stages, DEFAULT_MAX_RENDERED_LINES);
    }

    /**
     * A console with an explicit document cap, which exists so that the trimming can be tested
     * without producing five thousand lines.
     *
     * @param viewModel the filters and the messages they admit
     * @param stages the stages the filter offers, in the order they are offered; copied
     * @param maxRenderedLines the most lines the document holds, at least one
     * @throws NullPointerException if either reference argument, or any stage, is {@code null}
     * @throws IllegalArgumentException if {@code maxRenderedLines} is less than one, naming it
     */
    public ConsolePane(
            ConsoleViewModel viewModel, List<? extends StageTag> stages, int maxRenderedLines) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        List<StageTag> offered = List.copyOf(Objects.requireNonNull(stages, "stages"));
        if (maxRenderedLines < 1) {
            throw new IllegalArgumentException(
                    "the console must render at least one line, but the cap was: "
                            + maxRenderedLines);
        }
        this.maxRenderedLines = maxRenderedLines;

        setId(UiIds.CONSOLE_PANE);
        setTop(buildHeader(offered));
        setCenter(buildOutput());
        setBottom(buildToolbar());
        syncFilterButtons();
        render();
    }

    /**
     * The heading and the two filter rows.
     *
     * @param stages the stages to offer a filter button for
     * @return the top of the pane
     */
    private VBox buildHeader(List<StageTag> stages) {
        Label title = new Label("Console");
        title.setId(UiIds.CONSOLE_TITLE);
        named(title, "console");

        HBox stageRow = new HBox(4);
        stageRow.setId(UiIds.CONSOLE_STAGE_FILTER);
        stageRow.setAlignment(Pos.CENTER_LEFT);
        ToggleGroup stageGroup = new ToggleGroup();
        allStagesButton.setId(UiIds.CONSOLE_STAGE_FILTER_ALL);
        allStagesButton.setToggleGroup(stageGroup);
        named(allStagesButton, AccessibleRole.RADIO_BUTTON, "show messages from every stage");
        allStagesButton.setOnAction(event -> applyStageFilter(null));
        stageRow.getChildren().add(allStagesButton);
        for (StageTag stage : stages) {
            ToggleButton button = new ToggleButton(stage.displayName());
            button.setId(UiIds.consoleStageFilter(stage));
            button.setToggleGroup(stageGroup);
            named(
                    button,
                    AccessibleRole.RADIO_BUTTON,
                    "show messages from the " + stage.displayName() + " stage only");
            button.setOnAction(event -> applyStageFilter(stage));
            stageButtons.put(stage, button);
            stageRow.getChildren().add(button);
        }

        HBox severityRow = new HBox(4);
        severityRow.setId(UiIds.CONSOLE_SEVERITY_FILTER);
        severityRow.setAlignment(Pos.CENTER_LEFT);
        ToggleGroup severityGroup = new ToggleGroup();
        for (MessageSeverity severity : MessageSeverity.values()) {
            ToggleButton button = new ToggleButton(inWords(severity.name()));
            button.setId(UiIds.consoleSeverityFilter(severity));
            button.setToggleGroup(severityGroup);
            named(
                    button,
                    AccessibleRole.RADIO_BUTTON,
                    "show messages of severity " + inWords(severity.name()) + " and above");
            button.setOnAction(event -> applySeverityFilter(severity));
            severityButtons.put(severity, button);
            severityRow.getChildren().add(button);
        }

        VBox header = new VBox(4, title, stageRow, severityRow);
        header.setId(UiIds.CONSOLE_FILTERS);
        header.setPadding(new Insets(4, 6, 4, 6));
        return header;
    }

    /**
     * The read-only text view.
     *
     * @return the centre of the pane
     */
    private TextArea buildOutput() {
        output.setId(UiIds.CONSOLE_OUTPUT);
        output.setEditable(false);
        output.setWrapText(false);
        named(output, "console output");
        return output;
    }

    /**
     * The summary line and the two actions.
     *
     * @return the bottom of the pane
     */
    private HBox buildToolbar() {
        summary.setId(UiIds.CONSOLE_SUMMARY);
        named(summary, "console retention summary");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button clear = new Button("Clear console");
        clear.setId(UiIds.CONSOLE_CLEAR);
        named(clear, "clear the console and the run's in-memory message log");
        clear.setOnAction(event -> clearLog());

        Button copy = new Button("Copy output");
        copy.setId(UiIds.CONSOLE_COPY);
        named(copy, "copy the visible console text to the clipboard");
        copy.setOnAction(event -> copyOutput());

        HBox toolbar = new HBox(8, summary, spacer, clear, copy);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(4, 6, 4, 6));
        return toolbar;
    }

    /**
     * Asks for the console to be brought up to date, from any thread.
     *
     * <p>Upstream's coalescing, retargeted: the first caller after a flush schedules exactly one
     * {@link Platform#runLater(Runnable)}; every caller until that task runs is absorbed by it. A
     * process service reading a tool's stdout may call this once per line without any risk of
     * flooding the application thread.
     */
    public void requestRefresh() {
        boolean schedule;
        synchronized (flushLock) {
            schedule = !refreshScheduled;
            if (schedule) {
                refreshScheduled = true;
            }
        }
        if (schedule) {
            Platform.runLater(this::refreshNow);
        }
    }

    /**
     * Brings the console up to date now, on the JavaFX application thread.
     *
     * <p>This is the flush {@link #requestRefresh()} schedules, and it is public because a caller
     * that already knows it is on the application thread -- a filter action, a test -- should not
     * have to wait a pulse to see the result.
     *
     * @throws IllegalStateException if called from any other thread, because {@link
     *     ConsoleViewModel#refresh()} writes observable collections the scene graph is reading
     */
    public void refreshNow() {
        if (!Platform.isFxApplicationThread()) {
            throw new IllegalStateException(
                    "refreshNow() writes the scene graph and must be called on the JavaFX"
                            + " application thread; call requestRefresh() from any other thread");
        }
        synchronized (flushLock) {
            refreshScheduled = false;
        }
        viewModel.refresh();
        render();
        flushes.incrementAndGet();
    }

    /**
     * How many times this console has flushed.
     *
     * <p>Published because the coalescing inherited from upstream is the reason this file exists,
     * and a test that cannot count flushes cannot tell coalescing from luck: a thousand requests
     * and a thousand flushes produce exactly the same document as a thousand requests and one. It
     * is a diagnostic, not a part of the console's behaviour, and nothing in the interface reads
     * it.
     *
     * @return the number of completed flushes, whether asked for by {@link #requestRefresh()} or by
     *     {@link #refreshNow()}
     */
    public long flushCount() {
        return flushes.get();
    }

    /**
     * Empties the run's in-memory message log and the view of it.
     *
     * <p>The lines are not lost: {@code R-PROC-03} has the process service write every one of them
     * to the run's log files as it arrives.
     */
    public void clearLog() {
        viewModel.clear();
        render();
    }

    /**
     * Puts the visible console text on the system clipboard.
     *
     * <p>Upstream's Copy action. The clipboard call itself is isolated in a one-line method because
     * it is the only thing here that needs a real windowing system: under the Monocle headless
     * platform the tests run on, {@code Clipboard.getSystemClipboard()} is not something any test
     * may depend on.
     */
    public void copyOutput() {
        ClipboardContent content = new ClipboardContent();
        content.putString(output.getText());
        putOnSystemClipboard(content);
    }

    /**
     * The one call in this class that needs a real windowing system.
     *
     * @param content what to put on the clipboard
     */
    private static void putOnSystemClipboard(ClipboardContent content) {
        Clipboard.getSystemClipboard().setContent(content);
    }

    /**
     * Applies a stage filter and re-renders.
     *
     * @param stage the only stage to show, or {@code null} for every stage
     */
    private void applyStageFilter(StageTag stage) {
        if (stage == null) {
            viewModel.showAllStages();
        } else {
            viewModel.showOnlyStage(stage);
        }
        refreshNow();
    }

    /**
     * Applies a minimum-severity filter and re-renders.
     *
     * @param severity the least severity to show
     */
    private void applySeverityFilter(MessageSeverity severity) {
        viewModel.setMinimumSeverity(severity);
        refreshNow();
    }

    /**
     * Rewrites the document and the summary from the view-model's current snapshot.
     *
     * <p>Upstream appended; this rewrites. See <em>What was changed</em> on this class: the filters
     * make an append-only document impossible, and the cap makes a rewrite affordable.
     */
    private void render() {
        List<LogMessage> messages = viewModel.visibleMessages();
        int matched = messages.size();
        int from = Math.max(0, matched - maxRenderedLines);
        StringBuilder document = new StringBuilder();
        for (int i = from; i < matched; i++) {
            document.append(asLine(messages.get(i))).append(System.lineSeparator());
        }
        output.setText(document.toString());
        output.positionCaret(output.getLength());
        String text = summaryText(viewModel.discardedSummary(), matched, matched - from);
        summary.setText(text);
        summary.setAccessibleText(text);
        syncFilterButtons();
    }

    /** Puts the filter buttons back in step with the view-model, which is the authority. */
    private void syncFilterButtons() {
        Optional<StageTag> stageFilter = viewModel.stageFilter();
        allStagesButton.setSelected(stageFilter.isEmpty());
        for (Map.Entry<StageTag, ToggleButton> entry : stageButtons.entrySet()) {
            entry.getValue()
                    .setSelected(stageFilter.isPresent() && stageFilter.get() == entry.getKey());
        }
        MessageSeverity minimum = viewModel.minimumSeverity();
        for (Map.Entry<MessageSeverity, ToggleButton> entry : severityButtons.entrySet()) {
            entry.getValue().setSelected(entry.getKey() == minimum);
        }
    }

    /**
     * One message as one line: its severity in words, its stage if it has one, then its text.
     *
     * @param message the message to render
     * @return the line, without a line terminator
     */
    private static String asLine(LogMessage message) {
        StringBuilder line = new StringBuilder();
        line.append(String.format(Locale.ROOT, "%-" + SEVERITY_COLUMN + "s", message.severity()));
        line.append(' ');
        Optional<StageTag> stage = message.stage();
        if (stage.isPresent()) {
            line.append('[').append(stage.get().id()).append("] ");
        }
        return line.append(message.text()).toString();
    }

    /**
     * The summary line: what the log's cap threw away, and what the document's cap is not showing.
     *
     * <p>Two truncations, stated separately, because they mean different things: the first is
     * output the application no longer has, and the second is output it has but is not drawing.
     *
     * @param discardedSummary the view-model's sentence about the log's cap
     * @param matched how many messages the filters admitted
     * @param shown how many of them the document holds
     * @return the whole summary sentence
     */
    private static String summaryText(String discardedSummary, int matched, int shown) {
        String counts =
                matched == shown
                        ? "Showing "
                                + count(shown)
                                + (shown == 1 ? " matching line" : " matching lines")
                        : "Showing the newest "
                                + count(shown)
                                + " of "
                                + count(matched)
                                + " matching lines";
        return discardedSummary + ". " + counts + ".";
    }

    /**
     * A count, grouped in the root locale so that the sentence is the same on every machine.
     *
     * @param value the count
     * @return for example {@code 12,431}
     */
    private static String count(int value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    /**
     * A constant name as a phrase: {@code STDERR} becomes {@code Stderr}.
     *
     * @param constantName an enum constant name
     * @return the phrase
     */
    private static String inWords(String constantName) {
        String lower = constantName.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
