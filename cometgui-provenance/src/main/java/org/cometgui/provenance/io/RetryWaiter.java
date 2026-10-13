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

package org.cometgui.provenance.io;

/**
 * How {@link AtomicDocumentWriter} waits between two attempts at a rename the platform refused.
 *
 * <p><strong>Why a seam at all.</strong> The retry policy is a schedule -- how many attempts, how
 * long between them, and what an interruption during a wait does -- and a schedule made of real
 * sleeps can only be tested by sleeping. A test that sleeps through two seconds of back-off is
 * slow, and worse, it cannot tell a schedule of {@code 10, 20, 40} milliseconds from one of {@code
 * 40, 20, 10}: elapsed time is the only thing it can observe, and elapsed time is noisy. Routing
 * every wait through this interface lets a test record the exact sequence of pauses and compare it
 * against a literal, and lets it interrupt a wait deterministically, without the product's own code
 * being different in any way.
 *
 * <p><strong>It is package-private, and it is not a configuration point</strong>, exactly like
 * {@link Durability}. The only implementation the product uses is {@link Thread#sleep(long)}.
 *
 * @see AtomicDocumentWriter
 */
@FunctionalInterface
interface RetryWaiter {

    /**
     * Waits before the next attempt.
     *
     * @param millis how long to wait, in milliseconds; always positive
     * @throws InterruptedException if the thread is interrupted while waiting, or was already
     *     interrupted when the wait began -- which {@link Thread#sleep(long)} reports by throwing
     *     with the thread's interrupt status <em>cleared</em>, so the caller must set it again
     */
    void pause(long millis) throws InterruptedException;
}
