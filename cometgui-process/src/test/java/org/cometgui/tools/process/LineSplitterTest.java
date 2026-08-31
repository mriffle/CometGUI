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

package org.cometgui.tools.process;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Exhaustive tests for the one piece of pure logic in a stream pump.
 *
 * <p>Every expected value here is hand-typed. Nothing compares the splitter's output with the
 * splitter's output, and nothing computes an expectation from the class under test.
 *
 * <p>The chunk boundaries are the point. A pump reads whatever the operating system gives it, so a
 * {@code \r\n} pair arrives split down the middle whenever the pipe buffer happens to fill between
 * the two characters -- rarely, non-reproducibly, and in production rather than in a test. {@link
 * EveryChunkBoundary} therefore feeds the same text through every possible split rather than
 * through the convenient ones.
 */
class LineSplitterTest {

    /** Collects what the splitter emits, in order. */
    private final List<String> lines = new ArrayList<>();

    private LineSplitter splitter(int maximumLineLength) {
        return new LineSplitter(maximumLineLength, lines::add);
    }

    /**
     * A null the static analyser cannot see through.
     *
     * <p>Proving that a method rejects null means passing it null, and SpotBugs reports exactly
     * that as {@code NP_NULL_PARAM_DEREF_ALL_TARGETS_DANGEROUS}. Routing the null through a
     * collection keeps the test -- deleting it to quieten the analyser would be deleting the check
     * that the guard exists -- without adding an exclusion to the project's SpotBugs filter.
     *
     * @param <T> whatever the call site needs
     * @return null
     */
    private static <T> T opaqueNull() {
        List<T> holder = new ArrayList<>(1);
        holder.add(null);
        return holder.get(0);
    }

    private static void feed(LineSplitter splitter, String text) {
        char[] characters = text.toCharArray();
        splitter.accept(characters, 0, characters.length);
    }

    @Nested
    @DisplayName("terminators")
    class Terminators {

        @Test
        @DisplayName("a line feed ends a line and is not part of it")
        void lineFeedEndsALine() {
            LineSplitter splitter = splitter(100);

            feed(splitter, "one\ntwo\n");
            splitter.endOfStream();

            assertEquals(List.of("one", "two"), lines);
        }

        @Test
        @DisplayName("a carriage return followed by a line feed is ONE line break")
        void carriageReturnLineFeedIsOneBreak() {
            LineSplitter splitter = splitter(100);

            feed(splitter, "one\r\ntwo\r\n");
            splitter.endOfStream();

            assertEquals(List.of("one", "two"), lines);
            assertEquals(3, lines.get(0).length(), "no carriage return may survive on the line");
            assertEquals(3, lines.get(1).length());
        }

        @Test
        @DisplayName("a lone carriage return ends a line, as a progress bar writes it")
        void loneCarriageReturnEndsALine() {
            LineSplitter splitter = splitter(100);

            feed(splitter, "10%\r20%\r30%\r");
            splitter.endOfStream();

            assertEquals(List.of("10%", "20%", "30%"), lines);
        }

        @Test
        @DisplayName("a carriage return at the very end of the stream emits nothing further")
        void carriageReturnAtEndOfStreamEmitsNothingMore() {
            LineSplitter splitter = splitter(100);

            feed(splitter, "done\r");
            splitter.endOfStream();

            assertEquals(List.of("done"), lines);
        }

        @Test
        @DisplayName("two carriage returns in a row are two line breaks, the second line empty")
        void twoCarriageReturnsAreTwoBreaks() {
            LineSplitter splitter = splitter(100);

            feed(splitter, "a\r\rb\n");
            splitter.endOfStream();

            assertEquals(List.of("a", "", "b"), lines);
        }

        @Test
        @DisplayName("a line feed followed by a carriage return is two line breaks")
        void lineFeedThenCarriageReturnIsTwoBreaks() {
            LineSplitter splitter = splitter(100);

            feed(splitter, "a\n\rb\n");
            splitter.endOfStream();

            assertEquals(List.of("a", "", "b"), lines);
        }

        @Test
        @DisplayName("an empty line between two terminators is a line")
        void anEmptyLineIsALine() {
            LineSplitter splitter = splitter(100);

            feed(splitter, "a\n\n\nb\n");
            splitter.endOfStream();

            assertEquals(List.of("a", "", "", "b"), lines);
        }

        @Test
        @DisplayName("a stream that is only terminators is that many empty lines")
        void onlyTerminators() {
            LineSplitter splitter = splitter(100);

            feed(splitter, "\n\n");
            splitter.endOfStream();

            assertEquals(List.of("", ""), lines);
        }
    }

    @Nested
    @DisplayName("end of stream")
    class EndOfStream {

        @Test
        @DisplayName("the final unterminated segment IS a line and is delivered")
        void theFinalUnterminatedSegmentIsALine() {
            LineSplitter splitter = splitter(100);

            feed(splitter, "first\nlast-without-newline");
            assertEquals(List.of("first"), lines, "the last line is not complete yet");

            splitter.endOfStream();

            assertEquals(List.of("first", "last-without-newline"), lines);
        }

        @Test
        @DisplayName("a stream that ended on a terminator delivers no extra empty line")
        void noExtraEmptyLineAtEndOfStream() {
            LineSplitter splitter = splitter(100);

            feed(splitter, "one\ntwo\n");
            splitter.endOfStream();

            assertEquals(2, lines.size());
            assertEquals(List.of("one", "two"), lines);
        }

        @Test
        @DisplayName("an empty stream delivers nothing")
        void anEmptyStreamDeliversNothing() {
            LineSplitter splitter = splitter(100);

            splitter.endOfStream();

            assertEquals(List.of(), lines);
        }

        @Test
        @DisplayName("endOfStream is idempotent: a pump that fails after EOF calls it twice")
        void endOfStreamIsIdempotent() {
            LineSplitter splitter = splitter(100);

            feed(splitter, "tail");
            splitter.endOfStream();
            splitter.endOfStream();
            splitter.endOfStream();

            assertEquals(List.of("tail"), lines);
        }
    }

    @Nested
    @DisplayName("the maximum line length")
    class MaximumLineLength {

        @Test
        @DisplayName("a line of exactly the cap is delivered whole, with no empty line after it")
        void exactlyTheCapIsOneLine() {
            LineSplitter splitter = splitter(4);

            feed(splitter, "abcd\n");
            splitter.endOfStream();

            assertEquals(List.of("abcd"), lines);
        }

        @Test
        @DisplayName("one character past the cap splits into the cap and the remainder")
        void onePastTheCapSplits() {
            LineSplitter splitter = splitter(4);

            feed(splitter, "abcde\n");
            splitter.endOfStream();

            assertEquals(List.of("abcd", "e"), lines);
        }

        @Test
        @DisplayName("a line with no terminator at all is split at the cap, repeatedly")
        void anUnterminatedFloodIsSplitRepeatedly() {
            LineSplitter splitter = splitter(3);

            feed(splitter, "abcdefghij");
            splitter.endOfStream();

            assertEquals(List.of("abc", "def", "ghi", "j"), lines);
        }

        @Test
        @DisplayName("the split does not consume a terminator that follows it")
        void theSplitDoesNotSwallowTheFollowingTerminator() {
            LineSplitter splitter = splitter(3);

            feed(splitter, "abcd\nxy\n");
            splitter.endOfStream();

            assertEquals(List.of("abc", "d", "xy"), lines);
        }

        @Test
        @DisplayName("the default cap is 65536 characters")
        void theDefaultCapIs65536() {
            assertEquals(65_536, LineSplitter.DEFAULT_MAXIMUM_LINE_LENGTH);
            LineSplitter splitter = new LineSplitter(lines::add);

            feed(splitter, "x".repeat(65_537));
            splitter.endOfStream();

            assertEquals(2, lines.size());
            assertEquals(65_536, lines.get(0).length());
            assertEquals(1, lines.get(1).length());
        }

        @Test
        @DisplayName("a cap of one delivers one character per line")
        void aCapOfOne() {
            LineSplitter splitter = splitter(1);

            feed(splitter, "abc");
            splitter.endOfStream();

            assertEquals(List.of("a", "b", "c"), lines);
        }
    }

    @Nested
    @DisplayName("chunk boundaries")
    class EveryChunkBoundary {

        /** Contains every terminator form, an empty line and a final unterminated segment. */
        private static final String TEXT = "a\r\nb\rc\n\nd";

        /** Hand-typed: 'a' then CRLF, 'b' then a lone CR, 'c', an empty line, and 'd' at EOF. */
        private static final List<String> EXPECTED = List.of("a", "b", "c", "", "d");

        @Test
        @DisplayName("one chunk gives the expected lines")
        void oneChunk() {
            LineSplitter splitter = splitter(100);

            feed(splitter, TEXT);
            splitter.endOfStream();

            assertEquals(EXPECTED, lines);
        }

        @Test
        @DisplayName("every two-way split of the text gives exactly the same lines")
        void everyTwoWaySplit() {
            for (int cut = 0; cut <= TEXT.length(); cut++) {
                List<String> collected = new ArrayList<>();
                LineSplitter splitter = new LineSplitter(100, collected::add);

                feed(splitter, TEXT.substring(0, cut));
                feed(splitter, TEXT.substring(cut));
                splitter.endOfStream();

                assertEquals(EXPECTED, collected, "split after " + cut + " characters");
            }
        }

        @Test
        @DisplayName("a CRLF split across two chunks is still ONE line break")
        void carriageReturnLineFeedSplitAcrossChunks() {
            LineSplitter splitter = splitter(100);

            feed(splitter, "one\r");
            feed(splitter, "\ntwo\n");
            splitter.endOfStream();

            assertEquals(List.of("one", "two"), lines);
        }

        @Test
        @DisplayName("a carriage return ending a chunk, then a letter, is still one line break")
        void carriageReturnEndingAChunkFollowedByText() {
            LineSplitter splitter = splitter(100);

            feed(splitter, "one\r");
            feed(splitter, "two\n");
            splitter.endOfStream();

            assertEquals(List.of("one", "two"), lines);
        }

        @Test
        @DisplayName("a carriage return ending a chunk, then a carriage return, is two breaks")
        void carriageReturnEndingAChunkFollowedByAnother() {
            LineSplitter splitter = splitter(100);

            feed(splitter, "one\r");
            feed(splitter, "\rtwo\n");
            splitter.endOfStream();

            assertEquals(List.of("one", "", "two"), lines);
        }

        @Test
        @DisplayName("fed one character at a time the result is unchanged")
        void oneCharacterAtATime() {
            LineSplitter splitter = splitter(100);
            char[] characters = TEXT.toCharArray();

            for (char character : characters) {
                splitter.accept(new char[] {character}, 0, 1);
            }
            splitter.endOfStream();

            assertEquals(EXPECTED, lines);
        }

        @Test
        @DisplayName("an empty chunk changes nothing")
        void anEmptyChunkChangesNothing() {
            LineSplitter splitter = splitter(100);

            feed(splitter, "one\n");
            splitter.accept(new char[0], 0, 0);
            feed(splitter, "two\n");
            splitter.endOfStream();

            assertEquals(List.of("one", "two"), lines);
        }
    }

    @Nested
    @DisplayName("the buffer range")
    class BufferRange {

        @Test
        @DisplayName("only the requested range is read")
        void onlyTheRequestedRangeIsRead() {
            LineSplitter splitter = splitter(100);
            char[] buffer = "XXone\ntwo\nYY".toCharArray();

            splitter.accept(buffer, 2, 8);
            splitter.endOfStream();

            assertEquals(List.of("one", "two"), lines);
        }

        @Test
        @DisplayName("a range past the end of the buffer is rejected")
        void aRangePastTheEndIsRejected() {
            LineSplitter splitter = splitter(100);

            assertThrows(
                    IndexOutOfBoundsException.class,
                    () -> splitter.accept(new char[4], 2, 3),
                    "reading past the end of a pump's buffer would deliver stale characters");
        }

        @Test
        @DisplayName("a null buffer is rejected")
        void aNullBufferIsRejected() {
            LineSplitter splitter = splitter(100);
            char[] noBuffer = opaqueNull();

            assertThrows(NullPointerException.class, () -> splitter.accept(noBuffer, 0, 0));
        }
    }

    @Nested
    @DisplayName("construction")
    class Construction {

        @Test
        @DisplayName("a cap below one is rejected, naming the value")
        void aCapBelowOneIsRejected() {
            IllegalArgumentException rejected =
                    assertThrows(
                            IllegalArgumentException.class, () -> new LineSplitter(0, l -> {}));

            assertEquals("maximumLineLength must be at least 1, but was: 0", rejected.getMessage());
        }

        @Test
        @DisplayName("a negative cap is rejected, naming the value")
        void aNegativeCapIsRejected() {
            IllegalArgumentException rejected =
                    assertThrows(
                            IllegalArgumentException.class, () -> new LineSplitter(-7, l -> {}));

            assertEquals(
                    "maximumLineLength must be at least 1, but was: -7", rejected.getMessage());
        }

        @Test
        @DisplayName("a null sink is rejected")
        void aNullSinkIsRejected() {
            NullPointerException rejected =
                    assertThrows(NullPointerException.class, () -> new LineSplitter(10, null));

            assertTrue(
                    rejected.getMessage().contains("sink"),
                    "the message should name the argument, but was: " + rejected.getMessage());
        }
    }
}
