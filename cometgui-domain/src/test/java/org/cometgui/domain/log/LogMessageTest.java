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

package org.cometgui.domain.log;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.cometgui.domain.run.StageTag;
import org.cometgui.domain.testing.FakeStage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for {@link LogMessage}.
 *
 * <p>Two properties are worth more than the rest and are asserted directly rather than implied: the
 * timestamp is exactly the instant the caller's clock reported, so no code here reads the system
 * clock; and the text is stored byte for byte, including an empty line, because a console that
 * quietly drops a tool's blank line is misreporting the tool.
 */
class LogMessageTest {

    private static final Instant RECORDED_AT = Instant.parse("2026-08-30T09:15:30.250Z");
    private static final StageTag COMET = FakeStage.named("comet");

    @Test
    @DisplayName("carries the instant, the stage, the severity and the text it was given")
    void carriesEveryComponent() {
        LogMessage message =
                new LogMessage(
                        RECORDED_AT, Optional.of(COMET), MessageSeverity.WARNING, "0 spectra read");

        assertAll(
                () -> assertEquals(RECORDED_AT, message.timestamp()),
                () -> assertEquals(Optional.of(COMET), message.stage()),
                () -> assertEquals(MessageSeverity.WARNING, message.severity()),
                () -> assertEquals("0 spectra read", message.text()));
    }

    @Test
    @DisplayName("at() wraps a stage, and a null stage means a message belonging to no stage")
    void atWrapsTheStage() {
        LogMessage staged = LogMessage.at(RECORDED_AT, COMET, MessageSeverity.INFO, "searching");
        LogMessage unstaged = LogMessage.at(RECORDED_AT, null, MessageSeverity.INFO, "started");

        assertAll(
                () -> assertEquals(Optional.of(COMET), staged.stage()),
                () -> assertEquals(Optional.empty(), unstaged.stage()),
                () -> assertEquals(RECORDED_AT, unstaged.timestamp()),
                () -> assertEquals("started", unstaged.text()));
    }

    @Test
    @DisplayName("recordedBy() takes the instant from the caller's clock, and nowhere else")
    void recordedByReadsTheSuppliedClock() {
        Clock stopped = Clock.fixed(RECORDED_AT, ZoneOffset.UTC);
        Clock later = Clock.fixed(RECORDED_AT.plusSeconds(90), ZoneOffset.UTC);

        LogMessage first = LogMessage.recordedBy(stopped, COMET, MessageSeverity.STDERR, "line 1");
        LogMessage second = LogMessage.recordedBy(later, COMET, MessageSeverity.STDERR, "line 2");

        assertAll(
                () -> assertEquals(RECORDED_AT, first.timestamp()),
                () -> assertEquals(RECORDED_AT.plusSeconds(90), second.timestamp()),
                () -> assertEquals(Optional.of(COMET), first.stage()),
                () -> assertEquals(MessageSeverity.STDERR, first.severity()),
                () -> assertEquals("line 1", first.text()),
                () -> assertNotEquals(first.timestamp(), second.timestamp()));
    }

    @Test
    @DisplayName("recordedBy() accepts a null stage, for narration outside any stage")
    void recordedByAcceptsNoStage() {
        LogMessage message =
                LogMessage.recordedBy(
                        Clock.fixed(RECORDED_AT, ZoneOffset.UTC),
                        null,
                        MessageSeverity.INFO,
                        "CometGUI 0.1.0 starting");

        assertAll(
                () -> assertEquals(Optional.empty(), message.stage()),
                () -> assertEquals(RECORDED_AT, message.timestamp()));
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @ValueSource(strings = {"", " ", "\t", "  leading and trailing  ", "\u00e9\u00fc"})
    @DisplayName("the text is stored exactly as given, empty and whitespace included")
    void storesTheTextExactlyAsGiven(String text) {
        LogMessage message = LogMessage.at(RECORDED_AT, COMET, MessageSeverity.INFO, text);

        assertEquals(text, message.text());
    }

    @Test
    @DisplayName("a null timestamp is rejected by name")
    void rejectsANullTimestamp() {
        NullPointerException thrown =
                assertThrows(
                        NullPointerException.class,
                        () ->
                                new LogMessage(
                                        null, Optional.of(COMET), MessageSeverity.INFO, "text"));

        assertEquals("timestamp", thrown.getMessage());
    }

    @Test
    @DisplayName("a null Optional stage is rejected by name -- an absent stage is Optional.empty()")
    void rejectsANullStageOptional() {
        NullPointerException thrown =
                assertThrows(
                        NullPointerException.class,
                        () -> new LogMessage(RECORDED_AT, null, MessageSeverity.INFO, "text"));

        assertEquals("stage", thrown.getMessage());
    }

    @Test
    @DisplayName("a null severity is rejected by name")
    void rejectsANullSeverity() {
        NullPointerException thrown =
                assertThrows(
                        NullPointerException.class,
                        () -> LogMessage.at(RECORDED_AT, COMET, null, "text"));

        assertEquals("severity", thrown.getMessage());
    }

    @Test
    @DisplayName("a null text is rejected by name -- an empty line is \"\", not null")
    void rejectsANullText() {
        NullPointerException thrown =
                assertThrows(
                        NullPointerException.class,
                        () -> LogMessage.at(RECORDED_AT, COMET, MessageSeverity.INFO, null));

        assertEquals("text", thrown.getMessage());
    }

    @Test
    @DisplayName("a null clock is rejected by name")
    void rejectsANullClock() {
        NullPointerException thrown =
                assertThrows(
                        NullPointerException.class,
                        () -> LogMessage.recordedBy(null, COMET, MessageSeverity.INFO, "text"));

        assertEquals("clock", thrown.getMessage());
    }

    @Test
    @DisplayName("two messages with the same components are equal and hash alike")
    void equalMessagesAreEqual() {
        LogMessage one = LogMessage.at(RECORDED_AT, COMET, MessageSeverity.ERROR, "failed");
        LogMessage same = LogMessage.at(RECORDED_AT, COMET, MessageSeverity.ERROR, "failed");
        LogMessage other = LogMessage.at(RECORDED_AT, COMET, MessageSeverity.ERROR, "failed.");

        assertAll(
                () -> assertEquals(one, same),
                () -> assertEquals(one.hashCode(), same.hashCode()),
                () -> assertNotEquals(one, other),
                () -> assertTrue(one.toString().contains("failed")));
    }
}
