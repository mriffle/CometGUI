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

package org.cometgui.provenance.manifest;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ProvenanceStatus}.
 *
 * <p><strong>Every expected value in this file is a hand-typed literal.</strong> A wire name is an
 * on-disk token: it appears in every manifest CometGUI has ever written, and a manifest written
 * today will be read by a build that does not exist yet. An assertion of the form {@code
 * assertEquals(RUNNING.wireName(), status.wireName())} would agree with whatever the enum currently
 * says and could not fail; these compare against text typed out here, so a renamed constant, a
 * re-ordered declaration or a new constant added without a wire name all fail.
 *
 * <p>The Turkish-locale group is the requirement, not a curiosity. {@code R-PROV-04} exists because
 * the JVM default locale reaches serialisation, and the naive implementation of a lower-case wire
 * name -- {@code name().toLowerCase()} -- spells {@code RUNNING} as {@code runnıng} with a dotless
 * i when that locale is Turkish. The group proves both halves: that the hazard is real, and that
 * this enum is not exposed to it.
 */
class ProvenanceStatusTest {

    @Nested
    @DisplayName("the constants themselves")
    class Constants {

        @Test
        @DisplayName("are exactly these five, in this order")
        void areExactlyTheseFive() {
            assertEquals(
                    List.of("RUNNING", "COMPLETED", "PARTIAL", "FAILED", "CANCELLED"),
                    Arrays.stream(ProvenanceStatus.values()).map(Enum::name).toList());
        }

        @Test
        @DisplayName("each carries the wire name written into the documents")
        void eachCarriesItsWireName() {
            assertAll(
                    () -> assertEquals("running", ProvenanceStatus.RUNNING.wireName()),
                    () -> assertEquals("completed", ProvenanceStatus.COMPLETED.wireName()),
                    () -> assertEquals("partial", ProvenanceStatus.PARTIAL.wireName()),
                    () -> assertEquals("failed", ProvenanceStatus.FAILED.wireName()),
                    () -> assertEquals("cancelled", ProvenanceStatus.CANCELLED.wireName()));
        }

        @Test
        @DisplayName("no two share a wire name, and there are no others")
        void wireNamesAreDistinctAndComplete() {
            Set<String> wireNames =
                    Arrays.stream(ProvenanceStatus.values())
                            .map(ProvenanceStatus::wireName)
                            .collect(Collectors.toUnmodifiableSet());

            assertAll(
                    () ->
                            assertEquals(
                                    Set.of(
                                            "running",
                                            "completed",
                                            "partial",
                                            "failed",
                                            "cancelled"),
                                    wireNames),
                    () -> assertEquals(5, wireNames.size()));
        }
    }

    @Nested
    @DisplayName("resolving a wire name back")
    class Resolving {

        @Test
        @DisplayName("every wire name resolves to its own constant")
        void everyWireNameResolves() {
            assertAll(
                    () ->
                            assertSame(
                                    ProvenanceStatus.RUNNING,
                                    ProvenanceStatus.fromWireName("running")),
                    () ->
                            assertSame(
                                    ProvenanceStatus.COMPLETED,
                                    ProvenanceStatus.fromWireName("completed")),
                    () ->
                            assertSame(
                                    ProvenanceStatus.PARTIAL,
                                    ProvenanceStatus.fromWireName("partial")),
                    () ->
                            assertSame(
                                    ProvenanceStatus.FAILED,
                                    ProvenanceStatus.fromWireName("failed")),
                    () ->
                            assertSame(
                                    ProvenanceStatus.CANCELLED,
                                    ProvenanceStatus.fromWireName("cancelled")));
        }

        @Test
        @DisplayName("the Java constant name is not a wire name")
        void theJavaNameIsNotAWireName() {
            IllegalArgumentException thrown =
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> ProvenanceStatus.fromWireName("RUNNING"));

            assertEquals(
                    "no provenance status has the wire name \"RUNNING\"; expected one of [running,"
                            + " completed, partial, failed, cancelled]",
                    thrown.getMessage());
        }

        @Test
        @DisplayName("an unknown token is rejected by name")
        void anUnknownTokenIsRejected() {
            IllegalArgumentException thrown =
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> ProvenanceStatus.fromWireName("done"));

            assertEquals(
                    "no provenance status has the wire name \"done\"; expected one of [running,"
                            + " completed, partial, failed, cancelled]",
                    thrown.getMessage());
        }

        @Test
        @DisplayName("null is rejected as null, naming the argument")
        void nullIsRejected() {
            NullPointerException thrown =
                    assertThrows(
                            NullPointerException.class, () -> ProvenanceStatus.fromWireName(null));

            assertEquals("wire", thrown.getMessage());
        }
    }

    @Nested
    @DisplayName("R-PROV-04: the JVM default locale cannot reach a wire name")
    class UnderATurkishDefaultLocale {

        @Test
        @DisplayName("the hazard is real: a naive lower-casing produces a dotless i")
        void theHazardIsReal() {
            Locale original = Locale.getDefault();
            try {
                Locale.setDefault(Locale.of("tr", "TR"));

                assertAll(
                        () -> assertEquals("runnıng", "RUNNING".toLowerCase(Locale.getDefault())),
                        () ->
                                assertNotEquals(
                                        "running", "RUNNING".toLowerCase(Locale.getDefault())),
                        () -> assertEquals("faıled", "FAILED".toLowerCase(Locale.getDefault())),
                        () -> assertEquals("partıal", "PARTIAL".toLowerCase(Locale.getDefault())));
            } finally {
                Locale.setDefault(original);
            }
        }

        @Test
        @DisplayName("the wire names are unchanged by it")
        void theWireNamesAreUnchanged() {
            Locale original = Locale.getDefault();
            try {
                Locale.setDefault(Locale.of("tr", "TR"));

                assertAll(
                        () -> assertEquals("running", ProvenanceStatus.RUNNING.wireName()),
                        () -> assertEquals("completed", ProvenanceStatus.COMPLETED.wireName()),
                        () -> assertEquals("partial", ProvenanceStatus.PARTIAL.wireName()),
                        () -> assertEquals("failed", ProvenanceStatus.FAILED.wireName()),
                        () -> assertEquals("cancelled", ProvenanceStatus.CANCELLED.wireName()),
                        () ->
                                assertSame(
                                        ProvenanceStatus.RUNNING,
                                        ProvenanceStatus.fromWireName("running")));
            } finally {
                Locale.setDefault(original);
            }
        }
    }
}
