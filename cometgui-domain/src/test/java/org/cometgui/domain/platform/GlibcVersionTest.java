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

package org.cometgui.domain.platform;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.cometgui.domain.testing.Nulls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for {@link GlibcVersion}.
 *
 * <p>The accepted inputs are strings real systems emit: {@code gnu_get_libc_version()} on a current
 * distribution, an old three-component release, and the packaged form {@code ldd --version} prints
 * on Debian and Ubuntu. The rejected ones are the shapes a caller might hand it by mistake -- a
 * truncated version, a distribution's whole banner line, a component that is not a number -- and
 * each is asserted by the message, because "undetermined" and "wrong" have different consequences
 * for the user.
 */
class GlibcVersionTest {

    private static String rejectionMessage(String text) {
        return "not a recognised glibc version: \""
                + text
                + "\" (expected a form such as 2.36, 2.3.4 or 2.31-0ubuntu9.9)";
    }

    @Nested
    @DisplayName("parse(..)")
    class Parsing {

        @ParameterizedTest(name = "[{index}] {0} -> {1}.{2}.{3}")
        @CsvSource({
            "2.36, 2, 36, 0",
            "2.36.1, 2, 36, 1",
            "2.17, 2, 17, 0",
            "2.3.4, 2, 3, 4",
            "2.31-0ubuntu9.9, 2, 31, 0",
            "2.35-0ubuntu3.1, 2, 35, 0",
            "2.34.9000, 2, 34, 9000",
            "3.0, 3, 0, 0"
        })
        @DisplayName("reads the shapes real systems emit")
        void readsRealVersionStrings(String text, int major, int minor, int patch) {
            GlibcVersion version = GlibcVersion.parse(text);

            assertAll(
                    () -> assertEquals(major, version.major()),
                    () -> assertEquals(minor, version.minor()),
                    () -> assertEquals(patch, version.patch()),
                    () -> assertEquals(text, version.text()));
        }

        @Test
        @DisplayName("surrounding whitespace from a command's output is ignored")
        void ignoresSurroundingWhitespace() {
            GlibcVersion version = GlibcVersion.parse("  2.36\n");

            assertAll(
                    () -> assertEquals(36, version.minor()),
                    () -> assertEquals("2.36", version.text()));
        }

        @Test
        @DisplayName("a null version is rejected by name")
        void rejectsNull() {
            NullPointerException thrown =
                    assertThrows(NullPointerException.class, () -> GlibcVersion.parse(null));

            assertEquals("text", thrown.getMessage());
        }

        @ParameterizedTest(name = "[{index}] \"{0}\"")
        @ValueSource(strings = {"", "   ", "\t\n"})
        @DisplayName("a blank version is rejected before the shape is examined")
        void rejectsBlank(String blank) {
            IllegalArgumentException thrown =
                    assertThrows(IllegalArgumentException.class, () -> GlibcVersion.parse(blank));

            assertEquals("a glibc version must not be blank", thrown.getMessage());
        }

        @ParameterizedTest(name = "[{index}] \"{0}\"")
        @ValueSource(
                strings = {
                    "2",
                    "2.",
                    ".36",
                    "2.x",
                    "2.36.",
                    "2.36.1.2",
                    "2.-1",
                    "v2.36",
                    "abc",
                    "2.1234567890",
                    "ldd (Ubuntu GLIBC 2.35-0ubuntu3.1) 2.35"
                })
        @DisplayName("anything else is rejected, quoting exactly what was rejected")
        void rejectsMalformedVersions(String malformed) {
            IllegalArgumentException thrown =
                    assertThrows(
                            IllegalArgumentException.class, () -> GlibcVersion.parse(malformed));

            assertEquals(rejectionMessage(malformed), thrown.getMessage());
        }

        @Test
        @DisplayName("the rejection message quotes the stripped text, not the raw input")
        void rejectionQuotesTheStrippedText() {
            IllegalArgumentException thrown =
                    assertThrows(
                            IllegalArgumentException.class, () -> GlibcVersion.parse("  2.x  "));

            assertEquals(rejectionMessage("2.x"), thrown.getMessage());
        }
    }

    @Nested
    @DisplayName("of(..)")
    class Construction {

        @Test
        @DisplayName("keeps its components and renders them canonically")
        void keepsItsComponents() {
            GlibcVersion version = GlibcVersion.of(2, 36, 1);

            assertAll(
                    () -> assertEquals(2, version.major()),
                    () -> assertEquals(36, version.minor()),
                    () -> assertEquals(1, version.patch()),
                    () -> assertEquals("2.36.1", version.text()),
                    () -> assertEquals("2.36.1", version.toString()));
        }

        @Test
        @DisplayName("a negative component is rejected, naming which one")
        void rejectsNegativeComponents() {
            assertAll(
                    () ->
                            assertEquals(
                                    "the major component of a glibc version must not be negative,"
                                            + " but was: -1",
                                    assertThrows(
                                                    IllegalArgumentException.class,
                                                    () -> GlibcVersion.of(-1, 0, 0))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "the minor component of a glibc version must not be negative,"
                                            + " but was: -2",
                                    assertThrows(
                                                    IllegalArgumentException.class,
                                                    () -> GlibcVersion.of(2, -2, 0))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "the patch component of a glibc version must not be negative,"
                                            + " but was: -3",
                                    assertThrows(
                                                    IllegalArgumentException.class,
                                                    () -> GlibcVersion.of(2, 36, -3))
                                            .getMessage()));
        }

        @Test
        @DisplayName("zero is not negative and is accepted")
        void acceptsZeroComponents() {
            assertEquals("0.0.0", GlibcVersion.of(0, 0, 0).toString());
        }
    }

    @Nested
    @DisplayName("ordering")
    class Ordering {

        @ParameterizedTest(name = "[{index}] {0} vs {1}")
        @CsvSource({"3.0, 2.99", "2.36, 2.17", "2.36.1, 2.36", "2.36.2, 2.36.1"})
        @DisplayName("a newer version sorts after an older one, and the reverse holds")
        void newerSortsAfterOlder(String newerText, String olderText) {
            GlibcVersion newer = GlibcVersion.parse(newerText);
            GlibcVersion older = GlibcVersion.parse(olderText);

            assertAll(
                    () -> assertTrue(newer.compareTo(older) > 0, newerText + " > " + olderText),
                    () -> assertTrue(older.compareTo(newer) < 0, olderText + " < " + newerText),
                    () -> assertTrue(newer.isAtLeast(older)),
                    () -> assertFalse(older.isAtLeast(newer)));
        }

        @Test
        @DisplayName("a major difference outranks a larger minor difference")
        void majorOutranksMinor() {
            assertTrue(GlibcVersion.parse("3.1").compareTo(GlibcVersion.parse("2.99")) > 0);
        }

        @Test
        @DisplayName("a minor difference outranks a larger patch difference")
        void minorOutranksPatch() {
            assertTrue(GlibcVersion.parse("2.36.0").compareTo(GlibcVersion.parse("2.35.99")) > 0);
        }

        @Test
        @DisplayName("equal versions compare equal and each is at least the other")
        void equalVersionsCompareEqual() {
            GlibcVersion left = GlibcVersion.parse("2.36");
            GlibcVersion right = GlibcVersion.of(2, 36, 0);

            assertAll(
                    () -> assertEquals(0, left.compareTo(right)),
                    () -> assertTrue(left.isAtLeast(right)),
                    () -> assertTrue(right.isAtLeast(left)));
        }

        @Test
        @DisplayName("comparing with null is rejected by name")
        void rejectsNullComparisons() {
            GlibcVersion version = GlibcVersion.parse("2.36");
            GlibcVersion absent = Nulls.of(GlibcVersion.class);

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
        @DisplayName("an absent third component and an explicit zero are the same version")
        void absentPatchEqualsZero() {
            assertAll(
                    () -> assertEquals(GlibcVersion.parse("2.36.0"), GlibcVersion.parse("2.36")),
                    () ->
                            assertEquals(
                                    GlibcVersion.parse("2.36.0").hashCode(),
                                    GlibcVersion.parse("2.36").hashCode()));
        }

        @Test
        @DisplayName("a distribution suffix is kept for diagnostics but not for identity")
        void theDistributionSuffixIsNotPartOfIdentity() {
            GlibcVersion packaged = GlibcVersion.parse("2.31-0ubuntu9.9");

            assertAll(
                    () -> assertEquals(GlibcVersion.parse("2.31"), packaged),
                    () -> assertEquals("2.31-0ubuntu9.9", packaged.text()),
                    () -> assertEquals("2.31.0", packaged.toString()));
        }

        @Test
        @DisplayName("versions that differ in any component are not equal")
        void differentComponentsAreNotEqual() {
            GlibcVersion base = GlibcVersion.of(2, 36, 1);

            assertAll(
                    () -> assertNotEquals(base, GlibcVersion.of(3, 36, 1)),
                    () -> assertNotEquals(base, GlibcVersion.of(2, 37, 1)),
                    () -> assertNotEquals(base, GlibcVersion.of(2, 36, 2)));
        }

        @Test
        @DisplayName("hashCode tells unequal versions apart")
        void hashCodeDistinguishesUnequalValues() {
            /*
             * Written to kill the PrimitiveReturnsMutator mutation that replaces hashCode()'s
             * return with 0. That mutant keeps the equals/hashCode contract and passes every other
             * test here, while turning any HashMap or HashSet of versions into a linked list. The
             * components are fixed, so Objects.hash is deterministic and this is an assertion
             * rather than a probability.
             */
            GlibcVersion base = GlibcVersion.of(2, 36, 1);

            assertAll(
                    () -> assertNotEquals(base.hashCode(), GlibcVersion.of(3, 36, 1).hashCode()),
                    () -> assertNotEquals(base.hashCode(), GlibcVersion.of(2, 37, 1).hashCode()),
                    () -> assertNotEquals(base.hashCode(), GlibcVersion.of(2, 36, 2).hashCode()));
        }

        @Test
        @DisplayName("a version is not equal to something that is not a version")
        void notEqualToOtherTypes() {
            assertNotEquals(GlibcVersion.of(2, 36, 0), "2.36.0");
        }
    }
}
