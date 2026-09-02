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
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link FileDirection}.
 *
 * <p>Hand-typed literals throughout, for the reason given on {@link ProvenanceStatusTest}: these
 * two tokens are written into every manifest and read back by builds that do not exist yet, so an
 * assertion that asked the enum what it says would prove nothing.
 */
class FileDirectionTest {

    @Test
    @DisplayName("the constants are exactly these two, in this order")
    void theConstantsAreExactlyTheseTwo() {
        assertEquals(
                List.of("INPUT", "OUTPUT"),
                Arrays.stream(FileDirection.values()).map(Enum::name).toList());
    }

    @Test
    @DisplayName("each carries the wire name written into the documents")
    void eachCarriesItsWireName() {
        assertAll(
                () -> assertEquals("input", FileDirection.INPUT.wireName()),
                () -> assertEquals("output", FileDirection.OUTPUT.wireName()));
    }

    @Test
    @DisplayName("no two share a wire name, and there are no others")
    void wireNamesAreDistinctAndComplete() {
        Set<String> wireNames =
                Arrays.stream(FileDirection.values())
                        .map(FileDirection::wireName)
                        .collect(Collectors.toUnmodifiableSet());

        assertAll(
                () -> assertEquals(Set.of("input", "output"), wireNames),
                () -> assertEquals(2, wireNames.size()));
    }

    @Test
    @DisplayName("every wire name resolves to its own constant")
    void everyWireNameResolves() {
        assertAll(
                () -> assertSame(FileDirection.INPUT, FileDirection.fromWireName("input")),
                () -> assertSame(FileDirection.OUTPUT, FileDirection.fromWireName("output")));
    }

    @Test
    @DisplayName("the Java constant name is not a wire name")
    void theJavaNameIsNotAWireName() {
        IllegalArgumentException thrown =
                assertThrows(
                        IllegalArgumentException.class, () -> FileDirection.fromWireName("INPUT"));

        assertEquals(
                "no file direction has the wire name \"INPUT\"; expected one of [input, output]",
                thrown.getMessage());
    }

    @Test
    @DisplayName("an unknown token is rejected by name")
    void anUnknownTokenIsRejected() {
        IllegalArgumentException thrown =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> FileDirection.fromWireName("intermediate"));

        assertEquals(
                "no file direction has the wire name \"intermediate\"; expected one of [input,"
                        + " output]",
                thrown.getMessage());
    }

    @Test
    @DisplayName("null is rejected as null, naming the argument")
    void nullIsRejected() {
        NullPointerException thrown =
                assertThrows(NullPointerException.class, () -> FileDirection.fromWireName(null));

        assertEquals("wire", thrown.getMessage());
    }

    @Test
    @DisplayName("R-PROV-04: a Turkish default locale changes neither wire name")
    void aTurkishDefaultLocaleChangesNeitherWireName() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.of("tr", "TR"));

            assertAll(
                    () -> assertEquals("ınput", "INPUT".toLowerCase(Locale.getDefault())),
                    () -> assertNotEquals("input", "INPUT".toLowerCase(Locale.getDefault())),
                    () -> assertEquals("input", FileDirection.INPUT.wireName()),
                    () -> assertEquals("output", FileDirection.OUTPUT.wireName()),
                    () -> assertSame(FileDirection.INPUT, FileDirection.fromWireName("input")));
        } finally {
            Locale.setDefault(original);
        }
    }
}
