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

package org.cometgui.tools.pdv;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The exporter's primitive key map, checked against values the test chose. */
class LongIntMapTest {

    @Test
    @DisplayName("a key is added once; a second add keeps and returns the first value")
    void putIfAbsent() {
        LongIntMap map = new LongIntMap();
        assertAll(
                () -> assertEquals(LongIntMap.EMPTY, map.putIfAbsent(7L, 3)),
                () -> assertEquals(3, map.putIfAbsent(7L, 9)),
                () -> assertEquals(3, map.get(7L)),
                () -> assertEquals(1, map.size()),
                () -> assertEquals(LongIntMap.EMPTY, map.get(8L)),
                () -> assertEquals(LongIntMap.EMPTY, map.get(-5L)),
                () -> assertEquals(LongIntMap.EMPTY, map.putIfAbsent(0L, 0)),
                () -> assertEquals(0, map.get(0L)));
    }

    @Test
    @DisplayName("a hundred thousand keys, adjacent and far apart, survive every growth")
    void growth() {
        LongIntMap map = new LongIntMap();
        for (int i = 0; i < 100_000; i++) {
            assertEquals(LongIntMap.EMPTY, map.putIfAbsent(i, i));
            assertEquals(LongIntMap.EMPTY, map.putIfAbsent((long) (i + 1) << 23, 100_000 + i));
        }
        assertEquals(200_000, map.size());
        for (int i = 0; i < 100_000; i++) {
            assertEquals(i, map.get(i));
            assertEquals(100_000 + i, map.get((long) (i + 1) << 23));
        }
        assertEquals(LongIntMap.EMPTY, map.get(100_000L));
    }

    @Test
    @DisplayName("a negative key or value is refused")
    void negatives() {
        LongIntMap map = new LongIntMap();
        IllegalArgumentException key =
                assertThrows(IllegalArgumentException.class, () -> map.putIfAbsent(-1L, 0));
        IllegalArgumentException value =
                assertThrows(IllegalArgumentException.class, () -> map.putIfAbsent(1L, -1));
        assertAll(
                () ->
                        assertEquals(
                                "keys and values must not be negative: -1 -> 0", key.getMessage()),
                () ->
                        assertEquals(
                                "keys and values must not be negative: 1 -> -1",
                                value.getMessage()),
                () -> assertEquals(0, map.size()));
    }
}
