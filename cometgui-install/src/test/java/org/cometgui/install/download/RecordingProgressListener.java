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

package org.cometgui.install.download;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.cometgui.domain.ports.DownloadProgressListener;

/**
 * Records every progress report, so the three properties the port promises can be asserted rather
 * than assumed: the byte count never goes backwards, the last report is the true size of the file,
 * and the declared total is negative -- never zero and never a guess -- when the server declared
 * none.
 */
final class RecordingProgressListener implements DownloadProgressListener {

    /** One report, exactly as the downloader made it. */
    record Event(long bytesTransferred, long totalBytes) {}

    private final List<Event> events = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void onProgress(long bytesTransferred, long totalBytes) {
        events.add(new Event(bytesTransferred, totalBytes));
    }

    /**
     * Every report, in order.
     *
     * @return the reports
     */
    List<Event> events() {
        return List.copyOf(events);
    }

    /**
     * The byte counts reported, in order.
     *
     * @return the counts
     */
    List<Long> byteCounts() {
        return events().stream().map(Event::bytesTransferred).toList();
    }

    /**
     * Every distinct total reported. A downloader that changed its mind about the size half way
     * through would show up here as more than one value.
     *
     * @return the totals
     */
    Set<Long> totals() {
        return new LinkedHashSet<>(events().stream().map(Event::totalBytes).toList());
    }

    /**
     * The last byte count reported.
     *
     * @return the count
     * @throws IllegalStateException if nothing was reported at all
     */
    long lastByteCount() {
        List<Event> recorded = events();
        if (recorded.isEmpty()) {
            throw new IllegalStateException("no progress was reported at all");
        }
        return recorded.get(recorded.size() - 1).bytesTransferred();
    }

    /**
     * Whether the byte counts never went backwards.
     *
     * @return {@code true} if every report was at least as large as the one before it
     */
    boolean isMonotone() {
        List<Long> counts = byteCounts();
        for (int i = 1; i < counts.size(); i++) {
            if (counts.get(i) < counts.get(i - 1)) {
                return false;
            }
        }
        return true;
    }
}
