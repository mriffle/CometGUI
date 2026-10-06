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

/**
 * How many tool invocations of one step may run at once (design decision P8-15, {@code R-CMT-05}).
 *
 * <p>The bound is {@code max(1, min(invocations, cores / threadsPerInvocation, cap))}:
 *
 * <ul>
 *   <li>never more invocations at once than there are to run;
 *   <li>never more than the cores can feed, given how many threads each invocation uses -- a {@code
 *       threadsPerInvocation} of zero or less means "all cores" (Comet's {@code num_threads = 0}),
 *       so such invocations run one at a time;
 *   <li>never more than the explicit cap the engine was configured with;
 *   <li>and always at least one, so a step with work to do always makes progress, even when one
 *       invocation asks for more threads than the machine has.
 * </ul>
 *
 * <p>The bound changes only how many invocations overlap. It never changes the command an
 * invocation runs, the files it writes, or the order its provenance is recorded in, which is the
 * order of the invocation list.
 */
public final class ConcurrencyBound {

    private ConcurrencyBound() {}

    /**
     * Computes the bound.
     *
     * @param invocations how many invocations the step will run; not negative
     * @param cores the processors available; at least one
     * @param threadsPerInvocation the threads each invocation uses; zero or less means all cores
     * @param cap the explicit upper limit; at least one
     * @return the number of invocations that may run at once, at least one
     * @throws IllegalArgumentException if {@code invocations} is negative or {@code cores} or
     *     {@code cap} is less than one, naming the argument and its value
     */
    public static int of(int invocations, int cores, int threadsPerInvocation, int cap) {
        if (invocations < 0) {
            throw new IllegalArgumentException(
                    "invocations must not be negative, but was " + invocations);
        }
        if (cores < 1) {
            throw new IllegalArgumentException("cores must be at least 1, but was " + cores);
        }
        if (cap < 1) {
            throw new IllegalArgumentException("cap must be at least 1, but was " + cap);
        }
        int perInvocation = threadsPerInvocation < 1 ? cores : threadsPerInvocation;
        int bound = Math.min(Math.min(invocations, cores / perInvocation), cap);
        return Math.max(1, bound);
    }
}
