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

package org.cometgui.provenance.events;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link EventLineFormat}: the exact bytes of a line, and every way a line can be wrong.
 *
 * <p><strong>Every line in this file was typed by hand, and every character offset in an expected
 * message was counted independently</strong> (with {@code python3 -c 'print(s.index(...))'} on the
 * same literal) rather than read out of a failure. A parser test whose expected message came from
 * running the parser would agree with any offset it produced.
 *
 * <p>The round trips here are deliberately <em>not</em> writer-then-reader. Each one pins the line
 * as a literal and then asserts in both directions against it: rendering the event must produce
 * that exact text, and parsing that exact text must produce that exact event. A writer and a reader
 * that agreed with each other and with nothing else would pass a symmetric test and would still be
 * unable to read a log written by any other build.
 */
class EventLineFormatTest {

    /** The line every malformed variation below is a mutation of. Hand-typed. */
    private static final String VALID =
            "{\"seq\":1,\"time\":\"2026-08-31T09:15:00.000Z\",\"type\":\"run.started\","
                    + "\"payload\":{}}";

    @Test
    @DisplayName("an event renders as exactly this line, and this line parses back to that event")
    void oneLineBothWays() {
        ProvenanceEvent event =
                new ProvenanceEvent(
                        1L,
                        Instant.parse("2026-08-31T09:15:00.000Z"),
                        ProvenanceEventType.RUN_STARTED,
                        Map.of());

        assertAll(
                () -> assertEquals(VALID, EventLineFormat.toLine(event)),
                () -> assertEquals(event, EventLineFormat.parse(VALID)));
    }

    @Test
    @DisplayName("a payload renders in ascending key order, whatever order it was built in")
    void payloadRendersSorted() {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("version", "2026.02.2");
        payload.put("tool", "comet");
        payload.put("argv.0", "/opt/comet/comet.linux.exe");

        ProvenanceEvent event =
                new ProvenanceEvent(
                        3L,
                        Instant.parse("2026-08-31T09:15:02.000Z"),
                        ProvenanceEventType.TOOL_INVOKED,
                        payload);

        assertEquals(
                "{\"seq\":3,\"time\":\"2026-08-31T09:15:02.000Z\",\"type\":\"tool.invoked\","
                        + "\"payload\":{\"argv.0\":\"/opt/comet/comet.linux.exe\","
                        + "\"tool\":\"comet\",\"version\":\"2026.02.2\"}}",
                EventLineFormat.toLine(event));
    }

    @Test
    @DisplayName("a payload that could tear the line is escaped, and comes back byte for byte")
    void hostilePayloadIsEscapedBothWays() {
        // A quote, a backslash, all five short escapes, and a control character with none.
        // \u0001 and \u001f are both below the escape threshold and differ in their high
        // nibble, so a hexadecimal digit computed with the wrong shift cannot render both.
        String hostile = "a\"b\\c\nd\te\rf\bg\fh\u0001i\u001fj";
        String line =
                "{\"seq\":9,\"time\":\"2026-08-31T09:15:00.000Z\",\"type\":\"warning.raised\","
                        + "\"payload\":{\"message\":"
                        + "\"a\\\"b\\\\c\\nd\\te\\rf\\bg\\fh\\u0001i\\u001fj\"}}";
        ProvenanceEvent event =
                new ProvenanceEvent(
                        9L,
                        Instant.parse("2026-08-31T09:15:00.000Z"),
                        ProvenanceEventType.WARNING_RAISED,
                        Map.of("message", hostile));

        assertAll(
                () -> assertEquals(line, EventLineFormat.toLine(event)),
                () -> assertEquals(event, EventLineFormat.parse(line)),
                () ->
                        assertEquals(
                                hostile,
                                EventLineFormat.parse(line).payload().get("message"),
                                "the payload did not survive the round trip through the line"),
                () ->
                        assertEquals(
                                -1,
                                EventLineFormat.toLine(event).indexOf('\n'),
                                "a rendered line contains a newline, so one event is now two"));
    }

    @Test
    @DisplayName("a key is escaped exactly as a value is")
    void keysAreEscapedToo() {
        ProvenanceEvent event =
                new ProvenanceEvent(
                        2L,
                        Instant.parse("2026-08-31T09:15:00.000Z"),
                        ProvenanceEventType.FILE_HASHED,
                        Map.of("a\"b\nc", "1"));

        assertEquals(
                "{\"seq\":2,\"time\":\"2026-08-31T09:15:00.000Z\",\"type\":\"file.hashed\","
                        + "\"payload\":{\"a\\\"b\\nc\":\"1\"}}",
                EventLineFormat.toLine(event));
    }

    @Test
    @DisplayName("text outside ASCII is written as itself, not as an escape")
    void nonAsciiIsNotEscaped() {
        String path = "/data/HeLa_1\u00b5g_\u65e5\u672c.mzML";
        ProvenanceEvent event =
                new ProvenanceEvent(
                        4L,
                        Instant.parse("2026-08-31T09:15:00.000Z"),
                        ProvenanceEventType.FILE_HASHED,
                        Map.of("path", path));
        String line =
                "{\"seq\":4,\"time\":\"2026-08-31T09:15:00.000Z\",\"type\":\"file.hashed\","
                        + "\"payload\":{\"path\":\"/data/HeLa_1\u00b5g_\u65e5\u672c.mzML\"}}";

        assertAll(
                () -> assertEquals(line, EventLineFormat.toLine(event)),
                () -> assertEquals(event, EventLineFormat.parse(line)));
    }

    @Test
    @DisplayName(
            "a four-digit escape is decoded, so a line this writer did not produce still reads")
    void unicodeEscapesAreDecoded() throws MalformedEventLineException {
        String line =
                "{\"seq\":1,\"time\":\"2026-08-31T09:15:00.000Z\",\"type\":\"run.started\","
                        + "\"payload\":{\"k\\u0041y\":\"v\\u00B5\\/w\","
                        + "\"z\":\"\\u00b5\",\"lower\":\"\\u09af\","
                        + "\"upper\":\"\\u09AF\"}}";

        // Upper case and lower case hexadecimal both decode: this format writes the lower-case
        // form, and a reader that only understood its own spelling could not read a log written
        // by anything else.  The two four-digit escapes below spell 0, 9, a, f, A and F between
        // them, which are the six characters the three hexadecimal ranges end on.
        assertEquals(
                Map.of("kAy", "v\u00b5/w", "z", "\u00b5", "lower", "\u09af", "upper", "\u09af"),
                EventLineFormat.parse(line).payload());
    }

    @Test
    @DisplayName("the rendered timestamp is fixed width and does not drop trailing zeros")
    void timestampIsFixedWidth() {
        assertAll(
                () ->
                        assertEquals(
                                "2026-08-31T09:15:00.000Z",
                                EventLineFormat.formatTimestamp(
                                        Instant.parse("2026-08-31T09:15:00Z"))),
                () ->
                        assertEquals(
                                "2026-08-31T09:15:00.100Z",
                                EventLineFormat.formatTimestamp(
                                        Instant.parse("2026-08-31T09:15:00.1Z"))),
                () ->
                        assertEquals(
                                "0000-01-01T00:00:00.000Z",
                                EventLineFormat.formatTimestamp(
                                        Instant.parse("0000-01-01T00:00:00Z"))));
    }

    @Test
    @DisplayName("the rendered timestamp does not depend on the JVM default locale")
    void timestampIgnoresTheDefaultLocale() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("ar-SA-u-nu-arab"));
            String underArabicIndicDigits =
                    EventLineFormat.formatTimestamp(Instant.parse("2026-08-31T09:15:00Z"));
            Locale.setDefault(new Locale.Builder().setLanguage("tr").setRegion("TR").build());
            String underTurkish =
                    EventLineFormat.formatTimestamp(Instant.parse("2026-08-31T09:15:00Z"));

            assertAll(
                    () -> assertEquals("2026-08-31T09:15:00.000Z", underArabicIndicDigits),
                    () -> assertEquals("2026-08-31T09:15:00.000Z", underTurkish));
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    @DisplayName("every structural mistake is reported with the offset counted here by hand")
    void malformedStructure() {
        assertAll(
                () -> assertMalformed("", "expected {\"seq\": at offset 0"),
                () -> assertMalformed("[]", "expected {\"seq\": at offset 0"),
                () -> assertMalformed("{\"sec\":1}", "expected {\"seq\": at offset 0"),
                () -> assertMalformed(VALID + " ", "trailing characters at offset 77"),
                () -> assertMalformed(VALID + "}", "trailing characters at offset 77"),
                () ->
                        assertMalformed(
                                "{\"seq\":1;\"time\":\"2026-08-31T09:15:00.000Z\"}",
                                "expected ,\"time\": at offset 8"),
                () ->
                        assertMalformed(
                                "{\"seq\":1,\"time\":\"2026-08-31T09:15:00.000Z\",\"kind\":\"x\"}",
                                "expected ,\"type\": at offset 42"),
                () ->
                        assertMalformed(
                                "{\"seq\":1,\"time\":\"2026-08-31T09:15:00.000Z\","
                                        + "\"type\":\"run.started\",\"body\":{}}",
                                "expected ,\"payload\":{ at offset 63"),
                () ->
                        assertMalformed(
                                "{\"seq\":1,\"time\":\"2026-08-31T09:15:00.000Z\","
                                        + "\"type\":\"run.started\",\"payload\":{\"a\":\"1\"}",
                                "expected }} at offset 82"),
                // The line stops exactly where the payload's first key would start, so the cursor
                // is asked what comes next while there is nothing left at all.
                () ->
                        assertMalformed(
                                "{\"seq\":1,\"time\":\"2026-08-31T09:15:00.000Z\","
                                        + "\"type\":\"run.started\",\"payload\":{",
                                "expected a quoted string at offset 75"),
                () ->
                        assertMalformed(
                                "{\"seq\":1,\"time\":\"2026-08-31T09:15:00.000Z\","
                                        + "\"type\":\"run.started\","
                                        + "\"payload\":{\"a\":\"1\"\"b\":\"2\"}}",
                                "expected }} at offset 82"),
                () ->
                        assertMalformed(
                                "{\"seq\":1,\"time\":\"2026-08-31T09:15:00.000Z\","
                                        + "\"type\":\"run.started\","
                                        + "\"payload\":{\"a\":\"1\",\"a\":\"2\"}}",
                                "a repeated payload key at offset 83"));
    }

    @Test
    @DisplayName("a sequence number must be ASCII digits with no leading zero and must fit")
    void malformedSequence() {
        assertAll(
                () ->
                        assertMalformed(
                                "{\"seq\":,\"time\":\"x\"}",
                                "expected a sequence number at offset 7"),
                () ->
                        assertMalformed(
                                "{\"seq\":-1,\"time\":\"x\"}",
                                "expected a sequence number at offset 7"),
                // Long.parseLong accepts every Unicode decimal digit, so this one is the reason
                // the cursor tests characters itself instead of trusting the JDK.
                () ->
                        assertMalformed(
                                "{\"seq\":\u0661\u0662,\"time\":\"x\"}",
                                "expected a sequence number at offset 7"),
                () ->
                        assertMalformed(
                                "{\"seq\":01,\"time\":\"x\"}",
                                "a sequence number with a leading zero at offset 7"),
                () ->
                        assertMalformed(
                                "{\"seq\":99999999999999999999,\"time\":\"x\"}",
                                "a sequence number too large for a long at offset 7"),
                // Digits that run to the very end of the line: the scan has to stop because the
                // line stopped, not because it met a character that was not a digit.
                () -> assertMalformed("{\"seq\":12", "expected ,\"time\": at offset 9"),
                () ->
                        assertMalformed(
                                "{\"seq\":0,\"time\":\"2026-08-31T09:15:00.000Z\","
                                        + "\"type\":\"run.started\",\"payload\":{}}",
                                "a record this application would not have written: an event"
                                        + " sequence number starts at 1, but was: 0"));
    }

    @Test
    @DisplayName("every way a quoted string can be wrong is reported with its own offset")
    void malformedStrings() {
        assertAll(
                () ->
                        assertMalformed(
                                "{\"seq\":1,\"time\":2026}",
                                "expected a quoted string at offset 16"),
                () ->
                        assertMalformed(
                                "{\"seq\":1,\"time\":\"2026",
                                "an unterminated string from offset 16"),
                () ->
                        assertMalformed(
                                "{\"seq\":1,\"time\":\"2026-08-31T09:15:00.000\u0001Z\"}",
                                "an unescaped control character at offset 40"),
                () ->
                        assertMalformed(
                                "{\"seq\":1,\"time\":\"\\q\"}", "an unknown escape at offset 17"),
                () ->
                        assertMalformed(
                                "{\"seq\":1,\"time\":\"\\", "a truncated escape at offset 17"),
                () ->
                        assertMalformed(
                                "{\"seq\":1,\"time\":\"\\u00",
                                "a truncated \\u escape at offset 17"),
                () ->
                        assertMalformed(
                                "{\"seq\":1,\"time\":\"\\u00zz\"}",
                                "an invalid \\u escape at offset 17"),
                // The four digits end exactly where the line does: the escape is complete and the
                // string is not, which is a different complaint from a truncated escape.
                () ->
                        assertMalformed(
                                "{\"seq\":1,\"time\":\"\\u0041",
                                "an unterminated string from offset 16"),
                // The three rejected characters sit below '0', between 'F' and 'a', and above
                // 'f', so each of the hexadecimal ranges is tested from the outside.
                () ->
                        assertMalformed(
                                "{\"seq\":1,\"time\":\"\\u00!1\"}",
                                "an invalid \\u escape at offset 17"),
                () ->
                        assertMalformed(
                                "{\"seq\":1,\"time\":\"\\u00Z1\"}",
                                "an invalid \\u escape at offset 17"),
                () ->
                        assertMalformed(
                                "{\"seq\":1,\"time\":\"2026-08-31T09:15:00.000Z\","
                                        + "\"type\":\"run.started\",\"payload\":{\"a\"=\"1\"}}",
                                "expected : at offset 78"));
    }

    @Test
    @DisplayName("a timestamp that is not the exact form, or is not a real date, is damage")
    void malformedTimestamps() {
        assertAll(
                () -> assertMalformedTimestamp("2026-08-31 09:15:00.000Z"),
                () -> assertMalformedTimestamp("2026-08-31T09:15:00Z"),
                () -> assertMalformedTimestamp("2026-08-31T09:15:00.000"),
                () -> assertMalformedTimestamp("2026-08-31T09:15:00.000+01:00"),
                // SMART resolution, the default, would silently make this the 28th.
                () -> assertMalformedTimestamp("2026-02-30T00:00:00.000Z"),
                () -> assertMalformedTimestamp("2026-13-01T00:00:00.000Z"));
    }

    @Test
    @DisplayName("an unknown event type is damage, and the message does not quote it")
    void unknownEventType() {
        MalformedEventLineException rejected =
                assertThrows(
                        MalformedEventLineException.class,
                        () ->
                                EventLineFormat.parse(
                                        "{\"seq\":1,\"time\":\"2026-08-31T09:15:00.000Z\","
                                                + "\"type\":\"run.begun\",\"payload\":{}}"));

        assertAll(
                () -> assertEquals("an unknown event type at offset 50", rejected.getMessage()),
                () ->
                        assertFalse(
                                rejected.getMessage().contains("run.begun"),
                                "the message quotes content out of the file"));
    }

    @Test
    @DisplayName("a line the record itself would reject is damage, not an exception")
    void recordInvariantsBecomeDamage() {
        assertAll(
                () ->
                        assertMalformed(
                                "{\"seq\":1,\"time\":\"2026-08-31T09:15:00.000Z\","
                                        + "\"type\":\"run.finished\",\"payload\":{}}",
                                "a record this application would not have written: a run.finished"
                                        + " event must carry its terminal status under the"
                                        + " \"status\" payload key, and this one carries no such"
                                        + " key"),
                () ->
                        assertMalformed(
                                "{\"seq\":1,\"time\":\"2026-08-31T09:15:00.000Z\","
                                        + "\"type\":\"run.finished\","
                                        + "\"payload\":{\"status\":\"running\"}}",
                                "a record this application would not have written: a run.finished"
                                        + " event must carry a terminal status, and \"running\" is"
                                        + " not one"),
                () ->
                        assertMalformed(
                                "{\"seq\":1,\"time\":\"2026-08-31T09:15:00.000Z\","
                                        + "\"type\":\"run.started\","
                                        + "\"payload\":{\" \":\"1\"}}",
                                "a record this application would not have written: a payload key"
                                        + " must not be blank, and one of them is"));
    }

    @Test
    @DisplayName("null arguments are rejected by name")
    void nullArgumentsAreRejected() {
        assertAll(
                () ->
                        assertEquals(
                                "event",
                                assertThrows(
                                                NullPointerException.class,
                                                () -> EventLineFormat.toLine(deliberateNull()))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "line",
                                assertThrows(
                                                NullPointerException.class,
                                                () -> EventLineFormat.parse(deliberateNull()))
                                        .getMessage()));
    }

    @Test
    @DisplayName("the format holds no state and refuses to be instantiated, even by reflection")
    void utilityClassIsNotInstantiable() throws ReflectiveOperationException {
        Constructor<EventLineFormat> constructor = EventLineFormat.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        InvocationTargetException wrapper =
                assertThrows(InvocationTargetException.class, constructor::newInstance);

        assertEquals(
                "EventLineFormat is a utility class and is never instantiated",
                assertInstanceOf(AssertionError.class, wrapper.getCause()).getMessage());
    }

    private static void assertMalformed(String line, String expectedMessage) {
        MalformedEventLineException rejected =
                assertThrows(
                        MalformedEventLineException.class,
                        () -> EventLineFormat.parse(line),
                        "this line was accepted as a record");

        assertEquals(expectedMessage, rejected.getMessage());
    }

    private static void assertMalformedTimestamp(String timestamp) {
        assertMalformed(
                "{\"seq\":1,\"time\":\""
                        + timestamp
                        + "\",\"type\":\"run.started\",\"payload\":{}}",
                "a timestamp that is not of the form 2026-08-31T09:15:00.000Z at offset 16");
    }

    /**
     * A {@code null} that SpotBugs cannot see; see {@code ProvenanceEventTypeTest} for why.
     *
     * @param <T> whatever the call site needs
     * @return {@code null}
     */
    private static <T> T deliberateNull() {
        return null;
    }
}
