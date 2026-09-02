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

package org.cometgui.domain.tools;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.cometgui.domain.testing.Nulls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for {@link ToolVersion}.
 *
 * <p>The versions used here are the ones this product actually installs -- Percolator {@code
 * 3.06.5}, {@code 3.07.1} and {@code 3.09}, Comet {@code 2026.02.2}, PDV {@code 2.7.0}, the
 * Limelight converter {@code 2.8.1} -- because the defect this class exists to prevent is a
 * text-ordered comparison putting {@code 3.09} before {@code 3.07.1} and the product then offering
 * the wrong build.
 *
 * <p>The expected ordering in {@link Ordering#theRealVersionsSortInTheOrderUpstreamMeans()} is a
 * hand-typed literal. It is not derived from {@code compareTo}, which is the method under test: an
 * expected value computed by the code under test cannot fail.
 */
class ToolVersionTest {

    private static String rejectionMessage(String text) {
        return "not a recognised tool version: \""
                + text
                + "\" (expected two to four numeric components, such as 3.09, 3.07.1 or"
                + " 2026.02.2)";
    }

    @Nested
    @DisplayName("parse(..)")
    class Parsing {

        @ParameterizedTest(name = "[{index}] {0} -> {1}")
        @CsvSource({
            "3.09, '3.9'",
            "3.09.0, '3.9'",
            "3.07.1, '3.7.1'",
            "3.06.5, '3.6.5'",
            "3.05, '3.5'",
            "2026.02.2, '2026.2.2'",
            "2.7.0, '2.7'",
            "2.8.1, '2.8.1'",
            "1.2.3.4, '1.2.3.4'",
            "0.0, '0.0'",
            "999999999.0, '999999999.0'"
        })
        @DisplayName("reads the versions this product installs, leading zeros and all")
        void readsRealVersions(String text, String expectedNormalised) {
            ToolVersion version = ToolVersion.parse(text);

            assertAll(
                    () -> assertEquals(expectedNormalised, version.toString()),
                    () -> assertEquals(text, version.text()));
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @CsvSource({"3.07.1, 3, 7, 1", "2026.02.2, 2026, 2, 2", "3.06.5, 3, 6, 5"})
        @DisplayName("the components are the numbers, not the digits that were written")
        void componentsAreNumeric(String text, int first, int second, int third) {
            assertEquals(List.of(first, second, third), ToolVersion.parse(text).components());
        }

        @Test
        @DisplayName("a trailing zero component is dropped, never below two components")
        void trailingZerosAreDropped() {
            assertAll(
                    () -> assertEquals(List.of(3, 9), ToolVersion.parse("3.09.0").components()),
                    () -> assertEquals(List.of(3, 9), ToolVersion.parse("3.9.0.0").components()),
                    () -> assertEquals(List.of(2, 7), ToolVersion.parse("2.7.0").components()),
                    () -> assertEquals(List.of(3, 0), ToolVersion.parse("3.0").components()),
                    () -> assertEquals(List.of(0, 0), ToolVersion.parse("0.0.0").components()));
        }

        @Test
        @DisplayName("surrounding whitespace from a version banner is ignored")
        void ignoresSurroundingWhitespace() {
            ToolVersion version = ToolVersion.parse("  3.07.1\n");

            assertAll(
                    () -> assertEquals("3.07.1", version.text()),
                    () -> assertEquals("3.7.1", version.toString()));
        }

        @Test
        @DisplayName("a null version is rejected by name")
        void rejectsNull() {
            NullPointerException rejected =
                    assertThrows(
                            NullPointerException.class,
                            () -> ToolVersion.parse(Nulls.of(String.class)));

            assertEquals("text", rejected.getMessage());
        }

        @ParameterizedTest(name = "[{index}] \"{0}\"")
        @ValueSource(strings = {"", "   ", "\t\n"})
        @DisplayName("a blank version is rejected before the shape is examined")
        void rejectsBlank(String blank) {
            IllegalArgumentException rejected =
                    assertThrows(IllegalArgumentException.class, () -> ToolVersion.parse(blank));

            assertEquals("a tool version must not be blank", rejected.getMessage());
        }

        @ParameterizedTest(name = "[{index}] \"{0}\"")
        @ValueSource(
                strings = {
                    "3",
                    "3.",
                    ".09",
                    "3.x",
                    "3.07.",
                    "1.2.3.4.5",
                    "v3.07.1",
                    "3.07.1-rc1",
                    "3,07,1",
                    "rel-3-07-01",
                    "3.1234567890",
                    "Percolator version 3.07.1"
                })
        @DisplayName("anything else is rejected, quoting exactly what was rejected")
        void rejectsMalformedVersions(String malformed) {
            IllegalArgumentException rejected =
                    assertThrows(
                            IllegalArgumentException.class, () -> ToolVersion.parse(malformed));

            assertEquals(rejectionMessage(malformed), rejected.getMessage());
        }

        @Test
        @DisplayName("the rejection message quotes the stripped text, not the raw input")
        void rejectionQuotesTheStrippedText() {
            IllegalArgumentException rejected =
                    assertThrows(
                            IllegalArgumentException.class, () -> ToolVersion.parse("  3.x  "));

            assertEquals(rejectionMessage("3.x"), rejected.getMessage());
        }
    }

    @Nested
    @DisplayName("of(..)")
    class Construction {

        @Test
        @DisplayName("keeps its components and renders them normalised")
        void keepsItsComponents() {
            ToolVersion version = ToolVersion.of(3, 7, 1);

            assertAll(
                    () -> assertEquals(List.of(3, 7, 1), version.components()),
                    () -> assertEquals("3.7.1", version.text()),
                    () -> assertEquals("3.7.1", version.toString()));
        }

        @Test
        @DisplayName("a constructed version equals the one parsed from upstream's spelling")
        void constructedEqualsParsed() {
            assertEquals(ToolVersion.parse("3.07.1"), ToolVersion.of(3, 7, 1));
        }

        @ParameterizedTest(name = "[{index}] {0} component(s)")
        @ValueSource(ints = {0, 1, 5})
        @DisplayName("too few or too many components are rejected, naming how many were given")
        void rejectsWrongComponentCounts(int count) {
            int[] components = new int[count];

            IllegalArgumentException rejected =
                    assertThrows(IllegalArgumentException.class, () -> ToolVersion.of(components));

            assertEquals(
                    "a tool version must have two to four components, but was given " + count,
                    rejected.getMessage());
        }

        @Test
        @DisplayName("a negative component is rejected, naming which one")
        void rejectsNegativeComponents() {
            assertAll(
                    () ->
                            assertEquals(
                                    "component 1 of a tool version must not be negative, but was:"
                                            + " -1",
                                    assertThrows(
                                                    IllegalArgumentException.class,
                                                    () -> ToolVersion.of(-1, 0))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "component 3 of a tool version must not be negative, but was:"
                                            + " -5",
                                    assertThrows(
                                                    IllegalArgumentException.class,
                                                    () -> ToolVersion.of(3, 7, -5))
                                            .getMessage()));
        }

        @Test
        @DisplayName("a null component array is rejected by name")
        void rejectsNullComponents() {
            NullPointerException rejected =
                    assertThrows(
                            NullPointerException.class,
                            () -> ToolVersion.of(Nulls.of(int[].class)));

            assertEquals("components", rejected.getMessage());
        }

        @Test
        @DisplayName("zero is not negative and is accepted")
        void acceptsZeroComponents() {
            assertEquals("0.0", ToolVersion.of(0, 0).toString());
        }

        @Test
        @DisplayName("both ends of the accepted component count are accepted")
        void acceptsTheBoundaryCounts() {
            assertAll(
                    () -> assertEquals("3.9", ToolVersion.of(3, 9).toString()),
                    () -> assertEquals("1.2.3.4", ToolVersion.of(1, 2, 3, 4).toString()),
                    () ->
                            assertEquals(
                                    List.of(1, 2, 3, 4), ToolVersion.of(1, 2, 3, 4).components()));
        }
    }

    @Nested
    @DisplayName("ordering")
    class Ordering {

        /*
         * THE HAND-TYPED TABLE. This list is the answer, typed out in the order upstream means, and
         * nothing in it is computed. The test sorts a deliberately jumbled copy with compareTo --
         * the method under test -- and requires the result to be exactly this. Read as text,
         * "3.09" sorts before "3.07.1" and this assertion fails; that is the defect it exists to
         * catch.
         */
        private static final List<String> ASCENDING =
                List.of(
                        "2.7.0",
                        "2.8.1",
                        "3.05",
                        "3.06.5",
                        "3.07.1",
                        "3.7.2",
                        "3.08",
                        "3.09",
                        "3.9.1",
                        "2026.02.2");

        private static final List<String> JUMBLED =
                List.of(
                        "3.09",
                        "2026.02.2",
                        "3.06.5",
                        "2.8.1",
                        "3.9.1",
                        "3.05",
                        "3.07.1",
                        "2.7.0",
                        "3.08",
                        "3.7.2");

        @Test
        @DisplayName("the real versions sort in the order upstream means, not in text order")
        void theRealVersionsSortInTheOrderUpstreamMeans() {
            List<ToolVersion> parsed = new ArrayList<>();
            for (String text : JUMBLED) {
                parsed.add(ToolVersion.parse(text));
            }

            parsed.sort(ToolVersion::compareTo);

            List<String> sortedText = new ArrayList<>();
            for (ToolVersion version : parsed) {
                sortedText.add(version.text());
            }
            assertEquals(ASCENDING, sortedText);
        }

        @Test
        @DisplayName("3.06.5 < 3.07.1 < 3.09, stated directly")
        void thePercolatorTripleIsOrdered() {
            ToolVersion oldest = ToolVersion.parse("3.06.5");
            ToolVersion middle = ToolVersion.parse("3.07.1");
            ToolVersion newest = ToolVersion.parse("3.09");

            assertAll(
                    () -> assertTrue(oldest.compareTo(middle) < 0, "3.06.5 < 3.07.1"),
                    () -> assertTrue(middle.compareTo(newest) < 0, "3.07.1 < 3.09"),
                    () -> assertTrue(newest.compareTo(oldest) > 0, "3.09 > 3.06.5"),
                    () -> assertTrue(middle.compareTo(oldest) > 0, "3.07.1 > 3.06.5"),
                    () -> assertTrue(newest.isAtLeast(middle)),
                    () -> assertFalse(oldest.isAtLeast(middle)));
        }

        @ParameterizedTest(name = "[{index}] {0} vs {1}")
        @CsvSource({
            "3.09, 3.07.1",
            "3.07.1, 3.06.5",
            "3.07.1, 3.07",
            "2026.02.2, 2026.02.1",
            "2026.02.2, 3.09",
            "2.8.1, 2.7.0",
            "1.2.3.4, 1.2.3.3"
        })
        @DisplayName("a newer version sorts after an older one, and the reverse holds")
        void newerSortsAfterOlder(String newerText, String olderText) {
            ToolVersion newer = ToolVersion.parse(newerText);
            ToolVersion older = ToolVersion.parse(olderText);

            assertAll(
                    () -> assertTrue(newer.compareTo(older) > 0, newerText + " > " + olderText),
                    () -> assertTrue(older.compareTo(newer) < 0, olderText + " < " + newerText),
                    () -> assertTrue(newer.isAtLeast(older)),
                    () -> assertFalse(older.isAtLeast(newer)));
        }

        @Test
        @DisplayName("a more significant component outranks every less significant one")
        void significanceIsRespected() {
            assertAll(
                    () ->
                            assertTrue(
                                    ToolVersion.parse("4.00")
                                                    .compareTo(ToolVersion.parse("3.99.99"))
                                            > 0),
                    () ->
                            assertTrue(
                                    ToolVersion.parse("3.10")
                                                    .compareTo(ToolVersion.parse("3.09.99"))
                                            > 0));
        }

        @Test
        @DisplayName("the local-binary floor of Percolator 3.05 is a comparison, not a string test")
        void theLocalBinaryFloorCompares() {
            ToolVersion minimum = ToolVersion.parse("3.05");

            assertAll(
                    () -> assertFalse(ToolVersion.parse("3.04").isAtLeast(minimum)),
                    () -> assertTrue(ToolVersion.parse("3.05").isAtLeast(minimum)),
                    () -> assertTrue(ToolVersion.parse("3.5").isAtLeast(minimum)),
                    () -> assertTrue(ToolVersion.parse("3.06.5").isAtLeast(minimum)),
                    () -> assertTrue(ToolVersion.parse("3.09").isAtLeast(minimum)));
        }

        @Test
        @DisplayName("comparing with null is rejected by name")
        void rejectsNullComparisons() {
            ToolVersion version = ToolVersion.parse("3.07.1");
            ToolVersion absent = Nulls.of(ToolVersion.class);

            assertAll(
                    () ->
                            assertEquals(
                                    "other",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () -> version.compareTo(absent))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "required",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () -> version.isAtLeast(absent))
                                            .getMessage()));
        }
    }

    @Nested
    @DisplayName("value semantics")
    class ValueSemantics {

        @Test
        @DisplayName("3.07.1 and 3.7.1 are the same version written two ways")
        void leadingZerosDoNotChangeIdentity() {
            ToolVersion padded = ToolVersion.parse("3.07.1");
            ToolVersion plain = ToolVersion.parse("3.7.1");

            assertAll(
                    () -> assertEquals(padded, plain),
                    () -> assertEquals(padded.hashCode(), plain.hashCode()),
                    () -> assertEquals(0, padded.compareTo(plain)),
                    () -> assertEquals("3.07.1", padded.text()),
                    () -> assertEquals("3.7.1", plain.text()));
        }

        @Test
        @DisplayName("3.09 and 3.09.0 are the same version, and 3.09 is greater than 3.07.1")
        void absentComponentEqualsZero() {
            ToolVersion twoParts = ToolVersion.parse("3.09");
            ToolVersion threeParts = ToolVersion.parse("3.09.0");

            assertAll(
                    () -> assertEquals(twoParts, threeParts),
                    () -> assertEquals(twoParts.hashCode(), threeParts.hashCode()),
                    () -> assertTrue(twoParts.compareTo(ToolVersion.parse("3.07.1")) > 0),
                    () -> assertEquals("3.09.0", threeParts.text()));
        }

        @Test
        @DisplayName("versions that differ in any component are not equal")
        void differentComponentsAreNotEqual() {
            ToolVersion base = ToolVersion.parse("3.07.1");

            assertAll(
                    () -> assertNotEquals(base, ToolVersion.parse("4.07.1")),
                    () -> assertNotEquals(base, ToolVersion.parse("3.08.1")),
                    () -> assertNotEquals(base, ToolVersion.parse("3.07.2")),
                    () -> assertNotEquals(base, ToolVersion.parse("3.07.1.1")));
        }

        @Test
        @DisplayName("hashCode tells unequal versions apart")
        void hashCodeDistinguishesUnequalValues() {
            /*
             * Written to kill the mutation that replaces hashCode() with a constant. That mutant
             * keeps the equals/hashCode contract and passes every other test here, while turning
             * any HashMap of versions into a linked list. The components are fixed, so this is an
             * assertion rather than a probability.
             */
            ToolVersion base = ToolVersion.parse("3.07.1");

            assertAll(
                    () -> assertNotEquals(base.hashCode(), ToolVersion.parse("3.08.1").hashCode()),
                    () -> assertNotEquals(base.hashCode(), ToolVersion.parse("3.07.2").hashCode()),
                    () -> assertNotEquals(base.hashCode(), ToolVersion.parse("3.09").hashCode()));
        }

        @Test
        @DisplayName("a version is not equal to something that is not a version")
        void notEqualToOtherTypes() {
            assertNotEquals(ToolVersion.parse("3.07.1"), "3.07.1");
        }

        @Test
        @DisplayName("the component list handed out cannot be modified")
        void componentsAreImmutable() {
            List<Integer> components = ToolVersion.parse("3.07.1").components();

            assertThrows(UnsupportedOperationException.class, () -> components.add(9));
        }
    }
}
