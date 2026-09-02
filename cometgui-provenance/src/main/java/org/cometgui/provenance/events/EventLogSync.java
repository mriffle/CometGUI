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

package org.cometgui.provenance.events;

import java.io.IOException;
import java.nio.channels.FileChannel;

/**
 * The one filesystem operation that makes an appended event survive the crash it is recording,
 * behind a seam so that it can be counted.
 *
 * <p><strong>Why a seam at all.</strong> The same argument {@code org.cometgui.provenance.io} makes
 * for its own durability seam, one level smaller. Forcing a channel has no observable result: a log
 * whose records were forced and a log whose records are still in the operating system's page cache
 * are byte-identical files, they pass every round-trip test that can be written, and they differ
 * only when the power goes out -- at which point the second one is empty and the run's history is
 * gone. {@code R-PROV-05} asks for provenance that survives a crash, so "each record is on the
 * device before the append returns" is the property this package sells, and a property that nothing
 * measures is a comment. With the force on a seam, a test counts the calls and reads the file's
 * length at the moment each one happened, which also pins the order: the bytes are written first
 * and forced second, never the other way round.
 *
 * <p><strong>It is package-private, and it is not a configuration point.</strong> Nothing outside
 * this package may substitute a weaker notion of durability for a provenance log; the only
 * implementation the product uses is {@link #TO_DEVICE}.
 */
@FunctionalInterface
interface EventLogSync {

    /**
     * The real one: {@code force(true)}, data and metadata alike.
     *
     * <p>{@code true} rather than {@code false} because a file whose appended bytes survived but
     * whose length did not is exactly the truncated tail this package spends its reader recovering
     * from. Stateless, so one instance serves every log and every thread.
     */
    EventLogSync TO_DEVICE = channel -> channel.force(true);

    /**
     * Forces everything written so far out of the operating system's cache and onto the device.
     *
     * @param channel the open, already-written channel of the log file
     * @throws IOException if the data cannot be forced, in which case the append has not happened
     *     as far as the caller is concerned
     */
    void force(FileChannel channel) throws IOException;
}
