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

package org.cometgui.app.config;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import org.cometgui.domain.run.RunId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The production run-ID source: exact strings from a fixed clock, and the uniqueness {@code
 * RunIdSource} demands.
 *
 * <p>The character-set assertion below is a copy of {@link RunId}'s own rule rather than a
 * reference to it, on purpose: {@code RunId}'s pattern is private, and a test that reused it would
 * pass even if both were wrong together. Here the rule is written out from {@code RunId}'s
 * documentation -- start with a letter or digit, then letters, digits, {@code .}, {@code -} and
 * {@code _} -- so the two have to agree independently.
 */
class ClockRunIdSourceTest {

    private static final Instant WHEN = Instant.parse("2026-08-30T14:25:30.123Z");

    private static final Pattern RUN_ID_RULE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");

    private static Clock fixedAt(Instant instant, ZoneId zone) {
        return Clock.fixed(instant, zone);
    }

    @Test
    @DisplayName("a fixed clock gives exactly these strings, in this order")
    void producesTheExactIdentifiers() {
        ClockRunIdSource source = new ClockRunIdSource(fixedAt(WHEN, ZoneOffset.UTC));

        assertAll(
                () -> assertEquals(0L, source.issuedCount(), "a fresh source has issued nothing"),
                () -> assertEquals("run-20260830T142530123Z-1", source.newRunId().value()),
                () -> assertEquals("run-20260830T142530123Z-2", source.newRunId().value()),
                () -> assertEquals("run-20260830T142530123Z-3", source.newRunId().value()),
                () -> assertEquals(3L, source.issuedCount()));
    }

    @Test
    @DisplayName("the timestamp is UTC whatever zone the clock carries")
    void rendersInUtcRegardlessOfTheClocksZone() {
        String sydney =
                new ClockRunIdSource(fixedAt(WHEN, ZoneId.of("Australia/Sydney")))
                        .newRunId()
                        .value();
        String losAngeles =
                new ClockRunIdSource(fixedAt(WHEN, ZoneId.of("America/Los_Angeles")))
                        .newRunId()
                        .value();

        assertAll(
                () -> assertEquals("run-20260830T142530123Z-1", sydney),
                () -> assertEquals("run-20260830T142530123Z-1", losAngeles));
    }

    @Test
    @DisplayName("the timestamp follows the clock")
    void followsTheClock() {
        Clock later =
                Clock.offset(fixedAt(WHEN, ZoneOffset.UTC), Duration.ofDays(1).plusSeconds(1));

        assertEquals("run-20260831T142531123Z-1", new ClockRunIdSource(later).newRunId().value());
    }

    @Test
    @DisplayName("two identifiers from the SAME fixed clock differ -- the documented decision")
    void identifiersFromOneFixedClockAreStillUnique() {
        ClockRunIdSource source = new ClockRunIdSource(fixedAt(WHEN, ZoneOffset.UTC));
        Set<String> seen = new HashSet<>();

        for (int i = 0; i < 1000; i++) {
            seen.add(source.newRunId().value());
        }

        assertEquals(
                1000,
                seen.size(),
                "a run id names a run directory: a repeat would overwrite an earlier run's"
                        + " evidence, and a fixed test clock must not be able to cause one");
    }

    @Test
    @DisplayName("every identifier is one RunId accepts, and short enough to be a directory name")
    void everyIdentifierSatisfiesRunIdsRule() {
        ClockRunIdSource source = new ClockRunIdSource(fixedAt(WHEN, ZoneOffset.UTC));

        for (int i = 0; i < 1000; i++) {
            RunId id = source.newRunId();
            String value = id.value();
            assertAll(
                    value,
                    () ->
                            assertTrue(
                                    RUN_ID_RULE.matcher(value).matches(),
                                    "does not satisfy RunId's documented character set"),
                    () ->
                            assertTrue(
                                    value.length() <= RunId.MAX_LENGTH,
                                    "is longer than RunId.MAX_LENGTH"),
                    () -> assertEquals(value, new RunId(value).value(), "RunId rejected it"),
                    () -> assertTrue(value.startsWith(ClockRunIdSource.PREFIX)));
        }
        assertEquals(
                25,
                new ClockRunIdSource(fixedAt(WHEN, ZoneOffset.UTC)).newRunId().value().length(),
                "the shape run-uuuuMMddTHHmmssSSSZ-<n> is 25 characters at a one-digit sequence");
    }

    @Test
    @DisplayName("a null clock is rejected, naming the argument")
    void rejectsANullClock() {
        assertEquals(
                "clock",
                assertThrows(NullPointerException.class, () -> new ClockRunIdSource(null))
                        .getMessage());
    }
}
