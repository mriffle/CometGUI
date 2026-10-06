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

package org.cometgui.workflow.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** P8-15's bound, {@code max(1, min(n, cores / threads, cap))}, as a hand-typed table. */
class ConcurrencyBoundTest {

    @ParameterizedTest(name = "{0} invocations, {1} cores, {2} threads, cap {3} -> {4}")
    @CsvSource({
        // invocations, cores, threads, cap, bound
        "5, 4, 2, 8, 2", // the cores bind: 4 / 2
        "5, 64, 4, 3, 3", // the cap binds
        "2, 64, 4, 8, 2", // the number of invocations binds
        "5, 4, 0, 8, 1", // num_threads = 0 means all cores: one at a time
        "5, 4, -1, 8, 1", // so does anything below one
        "5, 4, 8, 8, 1", // more threads than cores: still one, never zero
        "0, 4, 1, 8, 1", // nothing to run: the bound is still at least one
        "1, 1, 1, 1, 1",
        "9, 9, 1, 9, 9",
        "9, 9, 1, 10, 9",
        "10, 9, 1, 10, 9",
        "7, 6, 3, 8, 2",
        "7, 8, 3, 8, 2"
    })
    void table(int invocations, int cores, int threads, int cap, int bound) {
        assertEquals(bound, ConcurrencyBound.of(invocations, cores, threads, cap));
    }

    @Test
    void refusesWhatCannotBeABound() {
        assertEquals(
                "invocations must not be negative, but was -1",
                assertThrows(IllegalArgumentException.class, () -> ConcurrencyBound.of(-1, 4, 1, 8))
                        .getMessage());
        assertEquals(
                "cores must be at least 1, but was 0",
                assertThrows(IllegalArgumentException.class, () -> ConcurrencyBound.of(1, 0, 1, 8))
                        .getMessage());
        assertEquals(
                "cap must be at least 1, but was 0",
                assertThrows(IllegalArgumentException.class, () -> ConcurrencyBound.of(1, 4, 1, 0))
                        .getMessage());
    }
}
