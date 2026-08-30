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

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import javafx.beans.property.ReadOnlyLongProperty;
import javafx.beans.property.ReadOnlyLongWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.cometgui.domain.log.BoundedMessageLog;
import org.cometgui.domain.log.LogMessage;
import org.cometgui.domain.log.MessageSeverity;
import org.cometgui.domain.run.StageTag;

/**
 * The view-model over the console's {@link BoundedMessageLog}: two filters, the messages they let
 * through, and an honest statement of what the cap has already thrown away.
 *
 * <h2>The log is injected and is never created here</h2>
 *
 * <p>The log belongs to the run, not to the console pane. The process service appends to it from
 * the threads reading a tool's stdout and stderr, provenance and the run's log files see the same
 * lines, and a console that made its own would be showing a second, empty log while the real one
 * filled up. So it arrives through the constructor, which is also what lets a test hand this class
 * a log it has filled by hand.
 *
 * <h2>Refresh is explicit, and that is the point</h2>
 *
 * <p>{@link #visibleMessages()} does not follow the log by itself. It changes when {@link
 * #refresh()} is called, and at no other time. A tool can emit hundreds of thousands of lines in a
 * few seconds -- the log's own flood test appends a million -- and a list the user interface
 * re-rendered on every one of them would spend the whole run laying out text nobody can read at
 * that speed. The view therefore coalesces: it refreshes on a timer, when a stage ends, or when the
 * user changes a filter. Where that scheduling lives is the view's business; that it is the view's
 * business is why this class has no timer either.
 *
 * <h2>No {@code Platform.runLater}, anywhere</h2>
 *
 * <p><strong>Marshalling onto the JavaFX application thread is the view's job, not this class's.
 * There is no {@code Platform.runLater} in this file and there must never be one.</strong> That is
 * not a stylistic preference: {@code Platform.runLater} throws {@code IllegalStateException} when
 * no toolkit has been started, so a single call would turn every test of this class into a test
 * that needs a display, and the whole reason the view-model layer exists is that it does not.
 * {@link #refresh()} is therefore called <em>from</em> the FX application thread by the view, which
 * already knows it is on that thread; this class simply reads a thread-safe log and writes an
 * observable list.
 *
 * <p>{@link BoundedMessageLog} is itself thread safe, so a refresh sees a consistent snapshot even
 * while both of a tool's output streams are appending to it.
 */
public final class ConsoleViewModel {

    /**
     * The text shown when the cap has discarded nothing. See {@link #discardedSummaryFor(long)}.
     */
    private static final String NOTHING_DISCARDED = "No earlier lines discarded";

    private final BoundedMessageLog log;

    /**
     * Which stage's messages to show, or {@link Optional#empty()} for every stage.
     *
     * <p>An {@code Optional} rather than a nullable {@code StageTag} because "all stages" is a
     * choice the user makes, not the absence of one, and a view that renders a filter combo box has
     * to draw it as an entry alongside the stages.
     */
    private final NonNullProperty<Optional<StageTag>> stageFilter =
            new NonNullProperty<>(this, "stageFilter", Optional.empty());

    /** The least severity to show. {@link MessageSeverity#INFO} shows everything. */
    private final NonNullProperty<MessageSeverity> minimumSeverity =
            new NonNullProperty<>(this, "minimumSeverity", MessageSeverity.INFO);

    private final ObservableList<LogMessage> visible = FXCollections.observableArrayList();

    private final ReadOnlyLongWrapper discardedCount =
            new ReadOnlyLongWrapper(this, "discardedCount", 0L);

    private final ReadOnlyStringWrapper discardedSummary =
            new ReadOnlyStringWrapper(this, "discardedSummary", NOTHING_DISCARDED);

    /**
     * A console over the given log, showing every stage at {@link MessageSeverity#INFO} and above.
     *
     * <p>The new console starts empty even if the log is not: nothing is visible until {@link
     * #refresh()} is called, which keeps "what is on screen" a thing the view decided rather than a
     * thing that happened.
     *
     * @param log the run's message log, which this class reads and clears but never replaces
     * @throws NullPointerException if {@code log} is {@code null}
     */
    public ConsoleViewModel(BoundedMessageLog log) {
        this.log = Objects.requireNonNull(log, "log");
    }

    /**
     * The sentence shown above the console when the cap has discarded messages.
     *
     * <p><strong>Locale-independent by construction.</strong> The grouping separator comes from
     * {@link Locale#ROOT}, not from the default locale, so the string is the same on every machine
     * -- which matters because this project serialises and asserts text locale-independently
     * everywhere else, and because a number formatted in the user's locale in one place and the
     * root locale in another is how a provenance record stops matching its own console.
     *
     * <p>The forms are exactly: {@code "No earlier lines discarded"}, {@code "1 earlier line
     * discarded"}, and for anything larger {@code "12,431 earlier lines discarded"}.
     *
     * @param discarded how many messages the cap has thrown away, zero or more
     * @return the sentence
     * @throws IllegalArgumentException if {@code discarded} is negative, naming the value
     */
    public static String discardedSummaryFor(long discarded) {
        if (discarded < 0) {
            throw new IllegalArgumentException(
                    "a discarded-message count cannot be negative, but was: " + discarded);
        }
        if (discarded == 0) {
            return NOTHING_DISCARDED;
        }
        String count = String.format(Locale.ROOT, "%,d", discarded);
        return discarded == 1
                ? count + " earlier line discarded"
                : count + " earlier lines discarded";
    }

    /**
     * The stage filter: which stage's messages to show, or empty for all of them.
     *
     * <p>Read-only, like every published property in this package: {@link #showAllStages()} and
     * {@link #showOnlyStage(StageTag)} are the two ways it changes. See {@link NonNullProperty}.
     *
     * @return the read-only property, never holding {@code null}
     */
    public ReadOnlyObjectProperty<Optional<StageTag>> stageFilterProperty() {
        return stageFilter.getReadOnlyProperty();
    }

    /**
     * The stage filter's current value.
     *
     * @return the stage being shown, or {@link Optional#empty()} for every stage
     */
    public Optional<StageTag> stageFilter() {
        return stageFilter.get();
    }

    /** Shows every stage. Takes effect at the next {@link #refresh()}. */
    public void showAllStages() {
        stageFilter.set(Optional.empty());
    }

    /**
     * Shows one stage only. Takes effect at the next {@link #refresh()}.
     *
     * <p>Messages that belong to no stage are then hidden, which is {@link
     * BoundedMessageLog#snapshotForStage(StageTag, MessageSeverity)}'s behaviour and is what the
     * filter means: "show me the Comet stage" is not "show me the Comet stage plus everything
     * unattributed".
     *
     * @param stage the stage to show
     * @throws NullPointerException if {@code stage} is {@code null}
     */
    public void showOnlyStage(StageTag stage) {
        stageFilter.set(Optional.of(Objects.requireNonNull(stage, "stage")));
    }

    /**
     * The minimum-severity filter.
     *
     * <p>Read-only; {@link #setMinimumSeverity(MessageSeverity)} is how it changes.
     *
     * @return the read-only property, never holding {@code null}
     */
    public ReadOnlyObjectProperty<MessageSeverity> minimumSeverityProperty() {
        return minimumSeverity.getReadOnlyProperty();
    }

    /**
     * The minimum severity currently shown.
     *
     * @return the least severity that passes the filter
     */
    public MessageSeverity minimumSeverity() {
        return minimumSeverity.get();
    }

    /**
     * Sets the minimum severity. Takes effect at the next {@link #refresh()}.
     *
     * @param severity the least severity to show
     * @throws NullPointerException if {@code severity} is {@code null}
     */
    public void setMinimumSeverity(MessageSeverity severity) {
        minimumSeverity.set(Objects.requireNonNull(severity, "severity"));
    }

    /**
     * The messages the filters currently let through, oldest first.
     *
     * <p>Updated only by {@link #refresh()} and {@link #clear()}. Unmodifiable: what the console
     * shows is derived from the log and the filters, and a caller that could insert a line into it
     * would be showing something no tool ever emitted. The wrapper is built here rather than
     * stored, so that no caller ever holds a reference to the backing list; it observes that list
     * weakly, so a discarded view leaks nothing.
     *
     * @return an unmodifiable observable list; attempting to change it throws {@link
     *     UnsupportedOperationException}
     */
    public ObservableList<LogMessage> visibleMessages() {
        return FXCollections.unmodifiableObservableList(visible);
    }

    /**
     * How many messages the log's cap has discarded, as an observable value.
     *
     * @return the read-only property; zero until the log overflows
     */
    public ReadOnlyLongProperty discardedCountProperty() {
        return discardedCount.getReadOnlyProperty();
    }

    /**
     * How many messages the log's cap has discarded.
     *
     * @return the count as of the last {@link #refresh()} or {@link #clear()}
     */
    public long discardedCount() {
        return discardedCount.get();
    }

    /**
     * The human-readable discarded-message line, as an observable value.
     *
     * @return the read-only property, holding the sentence {@link #discardedSummaryFor(long)}
     *     produces
     */
    public ReadOnlyStringProperty discardedSummaryProperty() {
        return discardedSummary.getReadOnlyProperty();
    }

    /**
     * The human-readable discarded-message line.
     *
     * @return the sentence as of the last {@link #refresh()} or {@link #clear()}
     */
    public String discardedSummary() {
        return discardedSummary.get();
    }

    /**
     * Re-reads the log through the current filters.
     *
     * <p>This is the only thing that changes {@link #visibleMessages()}, {@link
     * #discardedCountProperty()} and {@link #discardedSummaryProperty()}, apart from {@link
     * #clear()}. Call it from the JavaFX application thread: it writes observable collections a
     * scene graph may be observing, and this class does no marshalling of its own.
     */
    public void refresh() {
        Optional<StageTag> stage = stageFilter.get();
        MessageSeverity minimum = minimumSeverity.get();
        List<LogMessage> matched =
                stage.isPresent()
                        ? log.snapshotForStage(stage.get(), minimum)
                        : log.snapshotAtLeast(minimum);
        visible.setAll(matched);
        long discarded = log.discardedCount();
        discardedCount.set(discarded);
        discardedSummary.set(discardedSummaryFor(discarded));
    }

    /**
     * Empties the underlying log and the view of it.
     *
     * <p>The log's own {@link BoundedMessageLog#clear()} resets the discard count as well as the
     * retained messages, because a console the user has just emptied on purpose has nothing missing
     * to report. This method then refreshes, so the visible list and the summary agree with the log
     * immediately rather than at some later refresh.
     *
     * <p>The lines are not lost: {@code R-PROC-03} has the process service write every one of them
     * to the run's log files as it arrives, and this clears only what is in memory.
     */
    public void clear() {
        log.clear();
        refresh();
    }
}
