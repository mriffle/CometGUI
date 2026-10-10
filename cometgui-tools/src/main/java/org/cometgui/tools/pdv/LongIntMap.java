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

import java.util.Arrays;

/**
 * A map from non-negative {@code long} keys to non-negative {@code int} values, held in two
 * primitive arrays: the mzTab exporter's key sets at a million PSMs cost about 24 bytes a key here
 * against about 90 in a {@code HashMap<PepXmlHitKey, Integer>}.
 *
 * <p>Open addressing with linear probing; {@value #EMPTY} marks a free slot, which is why keys and
 * values must not be negative. The table doubles when it is half full. Nothing is ever removed.
 */
final class LongIntMap {

    /** A free slot's key, and the value {@link #get} and {@link #putIfAbsent} use for "none". */
    static final int EMPTY = -1;

    private static final int MINIMUM_CAPACITY = 16;

    private long[] keys;
    private int[] values;
    private int size;

    /** An empty map. */
    LongIntMap() {
        keys = new long[MINIMUM_CAPACITY];
        values = new int[MINIMUM_CAPACITY];
        Arrays.fill(keys, EMPTY);
    }

    /**
     * Adds a key unless it is present.
     *
     * @param key the key, not negative
     * @param value its value, not negative
     * @return {@link #EMPTY} if the key was added, else the value it already had (unchanged)
     * @throws IllegalArgumentException if the key or the value is negative
     */
    int putIfAbsent(long key, int value) {
        if (key < 0 || value < 0) {
            throw new IllegalArgumentException(
                    "keys and values must not be negative: " + key + " -> " + value);
        }
        int slot = slotOf(key);
        if (keys[slot] == key) {
            return values[slot];
        }
        keys[slot] = key;
        values[slot] = value;
        size++;
        if (size * 2 > keys.length) {
            grow();
        }
        return EMPTY;
    }

    /**
     * The value of a key.
     *
     * @param key the key
     * @return its value, or {@link #EMPTY} if it is absent
     */
    int get(long key) {
        if (key < 0) {
            return EMPTY;
        }
        int slot = slotOf(key);
        return keys[slot] == key ? values[slot] : EMPTY;
    }

    /**
     * How many keys the map holds.
     *
     * @return the count
     */
    int size() {
        return size;
    }

    /** The slot holding the key, or the free slot where it would go. */
    private int slotOf(long key) {
        int mask = keys.length - 1;
        int slot = mix(key) & mask;
        while (keys[slot] != EMPTY && keys[slot] != key) {
            slot = (slot + 1) & mask;
        }
        return slot;
    }

    /** Spreads a key's bits over an {@code int}, so near keys do not crowd one run of slots. */
    private static int mix(long key) {
        long h = key * 0x9E3779B97F4A7C15L;
        return (int) (h ^ (h >>> 32));
    }

    private void grow() {
        long[] oldKeys = keys;
        int[] oldValues = values;
        keys = new long[oldKeys.length * 2];
        values = new int[oldKeys.length * 2];
        Arrays.fill(keys, EMPTY);
        for (int index = 0; index < oldKeys.length; index++) {
            if (oldKeys[index] != EMPTY) {
                int slot = slotOf(oldKeys[index]);
                keys[slot] = oldKeys[index];
                values[slot] = oldValues[index];
            }
        }
    }
}
