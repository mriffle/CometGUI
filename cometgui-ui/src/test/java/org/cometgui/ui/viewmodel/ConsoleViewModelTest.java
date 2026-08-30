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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javafx.beans.property.Property;
import org.cometgui.domain.log.BoundedMessageLog;
import org.cometgui.domain.log.LogMessage;
import org.cometgui.domain.log.MessageSeverity;
import org.cometgui.domain.run.StageTag;
import org.cometgui.ui.testing.Nulls;
import org.cometgui.workflow.state.WorkflowStage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The console view-model: the two filters, the explicit refresh, and the discarded-line sentence.
 *
 * <p>Every message is built at a fixed instant. Nothing here reads a clock, and nothing here starts
 * a toolkit: this whole class is the evidence that the view-model half of the console is testable
 * without a display.
 */
class ConsoleViewModelTest {

    private static final Instant WHEN = Instant.parse("2026-08-30T12:00:00Z");

    private final BoundedMessageLog log = new BoundedMessageLog();

    private final ConsoleViewModel console = new ConsoleViewModel(log);

    private void append(StageTag stage, MessageSeverity severity, String text) {
        log.append(LogMessage.at(WHEN, stage, severity, text));
    }

    private List<String> visibleText() {
        List<String> texts = new ArrayList<>();
        for (LogMessage message : console.visibleMessages()) {
            texts.add(message.text());
        }
        return texts;
    }

    @Nested
    @DisplayName("construction")
    class Construction {

        @Test
        @DisplayName("requires a log, which it never creates itself")
        void rejectsANullLog() {
            NullPointerException thrown =
                    assertThrows(
                            NullPointerException.class,
                            () -> new ConsoleViewModel(Nulls.of(BoundedMessageLog.class)));
            assertEquals("log", thrown.getMessage());
        }

        @Test
        @DisplayName("starts with every stage, INFO and above, and nothing visible")
        void startsUnfiltered() {
            assertEquals(Optional.empty(), console.stageFilter());
            assertEquals(MessageSeverity.INFO, console.minimumSeverity());
            assertEquals(List.of(), console.visibleMessages());
            assertEquals(0L, console.discardedCount());
            assertEquals("No earlier lines discarded", console.discardedSummary());
        }

        @Test
        @DisplayName("shows nothing from a pre-filled log until refresh is called")
        void showsNothingBeforeTheFirstRefresh() {
            append(null, MessageSeverity.INFO, "already there");
            assertEquals(List.of(), visibleText());
            console.refresh();
            assertEquals(List.of("already there"), visibleText());
        }
    }

    @Nested
    @DisplayName("refresh")
    class Refresh {

        @Test
        @DisplayName("shows the retained messages oldest first")
        void showsRetainedMessagesOldestFirst() {
            append(null, MessageSeverity.INFO, "first");
            append(null, MessageSeverity.INFO, "second");
            append(null, MessageSeverity.INFO, "third");
            console.refresh();
            assertEquals(List.of("first", "second", "third"), visibleText());
        }

        @Test
        @DisplayName("is the only thing that moves messages into the view")
        void isExplicit() {
            append(null, MessageSeverity.INFO, "one");
            console.refresh();
            append(null, MessageSeverity.INFO, "two");
            append(null, MessageSeverity.INFO, "three");
            assertEquals(
                    List.of("one"),
                    visibleText(),
                    "appending to the log must not change the view on its own");
            console.refresh();
            assertEquals(List.of("one", "two", "three"), visibleText());
        }
    }

    @Nested
    @DisplayName("the severity filter")
    class SeverityFilter {

        @Test
        @DisplayName("keeps everything at INFO")
        void infoKeepsEverything() {
            append(null, MessageSeverity.INFO, "info");
            append(null, MessageSeverity.STDERR, "stderr");
            append(null, MessageSeverity.WARNING, "warning");
            append(null, MessageSeverity.ERROR, "error");
            console.refresh();
            assertEquals(List.of("info", "stderr", "warning", "error"), visibleText());
        }

        @Test
        @DisplayName("keeps only the severities at or above the minimum")
        void dropsAnythingLessSevere() {
            append(null, MessageSeverity.INFO, "info");
            append(null, MessageSeverity.STDERR, "stderr");
            append(null, MessageSeverity.WARNING, "warning");
            append(null, MessageSeverity.ERROR, "error");
            console.setMinimumSeverity(MessageSeverity.WARNING);
            console.refresh();
            assertEquals(List.of("warning", "error"), visibleText());
            assertEquals(MessageSeverity.WARNING, console.minimumSeverity());
        }

        @Test
        @DisplayName("takes effect only at the next refresh")
        void takesEffectAtTheNextRefresh() {
            append(null, MessageSeverity.INFO, "info");
            append(null, MessageSeverity.ERROR, "error");
            console.refresh();
            console.setMinimumSeverity(MessageSeverity.ERROR);
            assertEquals(List.of("info", "error"), visibleText());
            console.refresh();
            assertEquals(List.of("error"), visibleText());
        }

        @Test
        @DisplayName("rejects null, naming the argument, and keeps the previous minimum")
        void rejectsNull() {
            console.setMinimumSeverity(MessageSeverity.WARNING);
            NullPointerException thrown =
                    assertThrows(
                            NullPointerException.class,
                            () -> console.setMinimumSeverity(Nulls.of(MessageSeverity.class)));
            assertEquals("severity", thrown.getMessage());
            assertEquals(MessageSeverity.WARNING, console.minimumSeverity());
        }

        @Test
        @DisplayName("is observable")
        void isObservable() {
            List<String> events = new ArrayList<>();
            console.minimumSeverityProperty()
                    .addListener((observable, was, now) -> events.add(was + "->" + now));
            console.setMinimumSeverity(MessageSeverity.ERROR);
            assertEquals(List.of("INFO->ERROR"), events);
        }
    }

    @Nested
    @DisplayName("the stage filter")
    class StageFilter {

        @Test
        @DisplayName("shows one stage only, and hides messages belonging to no stage")
        void showsOneStageOnly() {
            append(WorkflowStage.COMET, MessageSeverity.INFO, "comet line");
            append(WorkflowStage.PERCOLATOR, MessageSeverity.INFO, "percolator line");
            append(null, MessageSeverity.INFO, "unattributed line");
            console.showOnlyStage(WorkflowStage.COMET);
            console.refresh();
            assertEquals(List.of("comet line"), visibleText());
            assertEquals(Optional.of(WorkflowStage.COMET), console.stageFilter());
        }

        @Test
        @DisplayName("goes back to every stage, unattributed messages included")
        void showAllStagesRestoresEverything() {
            append(WorkflowStage.COMET, MessageSeverity.INFO, "comet line");
            append(null, MessageSeverity.INFO, "unattributed line");
            console.showOnlyStage(WorkflowStage.COMET);
            console.refresh();
            assertEquals(List.of("comet line"), visibleText());
            console.showAllStages();
            console.refresh();
            assertEquals(List.of("comet line", "unattributed line"), visibleText());
            assertEquals(Optional.empty(), console.stageFilter());
        }

        @Test
        @DisplayName("combines with the severity filter")
        void combinesWithSeverity() {
            append(WorkflowStage.COMET, MessageSeverity.INFO, "comet info");
            append(WorkflowStage.COMET, MessageSeverity.ERROR, "comet error");
            append(WorkflowStage.PERCOLATOR, MessageSeverity.ERROR, "percolator error");
            console.showOnlyStage(WorkflowStage.COMET);
            console.setMinimumSeverity(MessageSeverity.WARNING);
            console.refresh();
            assertEquals(List.of("comet error"), visibleText());
        }

        @Test
        @DisplayName("rejects null, naming the argument")
        void rejectsNull() {
            NullPointerException thrown =
                    assertThrows(
                            NullPointerException.class,
                            () -> console.showOnlyStage(Nulls.of(StageTag.class)));
            assertEquals("stage", thrown.getMessage());
            assertEquals(Optional.empty(), console.stageFilter());
        }

        @Test
        @DisplayName("is observable")
        void isObservable() {
            List<String> events = new ArrayList<>();
            console.stageFilterProperty()
                    .addListener((observable, was, now) -> events.add(was + "->" + now));
            console.showOnlyStage(WorkflowStage.RESULTS);
            console.showAllStages();
            assertEquals(
                    List.of(
                            "Optional.empty->Optional[RESULTS]",
                            "Optional[RESULTS]->Optional.empty"),
                    events);
        }
    }

    @Nested
    @DisplayName("the discarded-line summary")
    class DiscardedSummary {

        @Test
        @DisplayName("says nothing was discarded when nothing was")
        void zeroReadsAsNothingDiscarded() {
            assertEquals("No earlier lines discarded", ConsoleViewModel.discardedSummaryFor(0));
        }

        @Test
        @DisplayName("is singular for exactly one")
        void oneIsSingular() {
            assertEquals("1 earlier line discarded", ConsoleViewModel.discardedSummaryFor(1));
        }

        @Test
        @DisplayName("is plural for two")
        void twoIsPlural() {
            assertEquals("2 earlier lines discarded", ConsoleViewModel.discardedSummaryFor(2));
        }

        @Test
        @DisplayName("groups thousands with commas")
        void groupsThousands() {
            assertEquals(
                    "12,431 earlier lines discarded", ConsoleViewModel.discardedSummaryFor(12_431));
            assertEquals(
                    "1,000 earlier lines discarded", ConsoleViewModel.discardedSummaryFor(1_000));
            assertEquals(
                    "9,876,543,210 earlier lines discarded",
                    ConsoleViewModel.discardedSummaryFor(9_876_543_210L));
        }

        @Test
        @DisplayName("does not change with the default locale")
        void isLocaleIndependent() {
            Locale original = Locale.getDefault();
            try {
                Locale.setDefault(Locale.GERMANY);
                assertEquals(
                        "12,431 earlier lines discarded",
                        ConsoleViewModel.discardedSummaryFor(12_431),
                        "the grouping separator must come from Locale.ROOT, not the default"
                                + " locale (German would group as 12.431)");
                Locale.setDefault(Locale.of("hi", "IN"));
                assertEquals(
                        "12,431 earlier lines discarded",
                        ConsoleViewModel.discardedSummaryFor(12_431));
            } finally {
                Locale.setDefault(original);
            }
        }

        @Test
        @DisplayName("rejects a negative count, naming the value")
        void rejectsANegativeCount() {
            IllegalArgumentException thrown =
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> ConsoleViewModel.discardedSummaryFor(-1));
            assertEquals(
                    "a discarded-message count cannot be negative, but was: -1",
                    thrown.getMessage());
        }
    }

    @Nested
    @DisplayName("the discarded count over a real log")
    class DiscardedOverARealLog {

        private final BoundedMessageLog small = new BoundedMessageLog(3);

        private final ConsoleViewModel smallConsole = new ConsoleViewModel(small);

        private void fill(int count) {
            for (int i = 1; i <= count; i++) {
                small.append(LogMessage.at(WHEN, null, MessageSeverity.INFO, "line " + i));
            }
        }

        @Test
        @DisplayName("reports the cap's discards, oldest first gone")
        void reportsTheCapsDiscards() {
            fill(5);
            smallConsole.refresh();
            assertEquals(2L, smallConsole.discardedCount());
            assertEquals("2 earlier lines discarded", smallConsole.discardedSummary());
            List<String> texts = new ArrayList<>();
            for (LogMessage message : smallConsole.visibleMessages()) {
                texts.add(message.text());
            }
            assertEquals(List.of("line 3", "line 4", "line 5"), texts);
        }

        @Test
        @DisplayName("is singular when exactly one line was discarded")
        void isSingularForOneDiscard() {
            fill(4);
            smallConsole.refresh();
            assertEquals(1L, smallConsole.discardedCount());
            assertEquals("1 earlier line discarded", smallConsole.discardedSummary());
        }

        @Test
        @DisplayName("is published as an observable count and an observable sentence")
        void isObservable() {
            List<String> counts = new ArrayList<>();
            List<String> sentences = new ArrayList<>();
            smallConsole
                    .discardedCountProperty()
                    .addListener((observable, was, now) -> counts.add(was + "->" + now));
            smallConsole
                    .discardedSummaryProperty()
                    .addListener((observable, was, now) -> sentences.add(now));
            fill(5);
            smallConsole.refresh();
            assertEquals(List.of("0->2"), counts);
            assertEquals(List.of("2 earlier lines discarded"), sentences);
        }
    }

    @Nested
    @DisplayName("clear")
    class Clear {

        @Test
        @DisplayName("empties the underlying log, the view and the discard count")
        void clearsEverything() {
            BoundedMessageLog small = new BoundedMessageLog(2);
            ConsoleViewModel smallConsole = new ConsoleViewModel(small);
            for (int i = 0; i < 6; i++) {
                small.append(LogMessage.at(WHEN, null, MessageSeverity.INFO, "line " + i));
            }
            smallConsole.refresh();
            assertEquals(4L, smallConsole.discardedCount());
            smallConsole.clear();
            assertEquals(0, small.size(), "the underlying log is what was cleared");
            assertEquals(List.of(), smallConsole.visibleMessages());
            assertEquals(0L, smallConsole.discardedCount());
            assertEquals("No earlier lines discarded", smallConsole.discardedSummary());
        }

        @Test
        @DisplayName("leaves the filters alone")
        void keepsTheFilters() {
            console.showOnlyStage(WorkflowStage.PERCOLATOR);
            console.setMinimumSeverity(MessageSeverity.WARNING);
            console.clear();
            assertEquals(Optional.of(WorkflowStage.PERCOLATOR), console.stageFilter());
            assertEquals(MessageSeverity.WARNING, console.minimumSeverity());
        }
    }

    @Nested
    @DisplayName("the published state")
    class PublishedState {

        @Test
        @DisplayName("cannot be modified by a view")
        void theVisibleListIsUnmodifiable() {
            append(null, MessageSeverity.INFO, "line");
            console.refresh();
            LogMessage forged = LogMessage.at(WHEN, null, MessageSeverity.ERROR, "forged");
            assertThrows(
                    UnsupportedOperationException.class,
                    () -> console.visibleMessages().add(forged));
            assertThrows(
                    UnsupportedOperationException.class, () -> console.visibleMessages().clear());
        }

        @Test
        @DisplayName("keeps every filter property read-only")
        void thePublishedPropertiesAreNotWritable() {
            assertFalse(console.stageFilterProperty() instanceof Property);
            assertFalse(console.minimumSeverityProperty() instanceof Property);
            assertFalse(console.discardedCountProperty() instanceof Property);
            assertFalse(console.discardedSummaryProperty() instanceof Property);
        }
    }
}
