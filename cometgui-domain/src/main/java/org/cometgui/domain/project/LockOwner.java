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

package org.cometgui.domain.project;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DecimalStyle;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Objects;

/**
 * Who holds, or held, a project's lock ({@code R-RUN-05}): the process, the computer, and when it
 * took the lock.
 *
 * <p>The specification asks for "a stale-lock recovery path that identifies the owning process".
 * This is the identification: it is what {@code project.lock} records, what a refusal names, and
 * what a recovery reports it took the lock over from. The host matters as much as the process
 * number: a process number means nothing on another computer, so a lock recorded by another host is
 * never judged stale from this one.
 *
 * @param pid the operating-system process id of the owner
 * @param host the name of the computer the owner ran on
 * @param started when the owner took the lock, truncated to milliseconds
 */
public record LockOwner(long pid, String host, Instant started) {

    /** How {@link #describe()} writes {@link #started()}: UTC, seconds, ASCII digits. */
    private static final DateTimeFormatter SECONDS =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT)
                    .withZone(ZoneOffset.UTC)
                    .withDecimalStyle(DecimalStyle.STANDARD);

    /**
     * Validates the owner.
     *
     * @throws NullPointerException naming a component that is {@code null}
     * @throws IllegalArgumentException if {@code pid} is not positive, or {@code host} is blank or
     *     holds a control character
     */
    public LockOwner {
        if (pid <= 0) {
            throw new IllegalArgumentException("a process id must be positive, but was " + pid);
        }
        Objects.requireNonNull(host, "host");
        if (host.isBlank()) {
            throw new IllegalArgumentException("a host name must not be blank");
        }
        for (int index = 0; index < host.length(); index++) {
            if (Character.isISOControl(host.charAt(index))) {
                throw new IllegalArgumentException(
                        "a host name must not contain a control character (at index "
                                + index
                                + ")");
            }
        }
        started = Objects.requireNonNull(started, "started").truncatedTo(ChronoUnit.MILLIS);
    }

    /**
     * The owner in words, for a refusal or a recovery message.
     *
     * @return for example {@code process 4242 on host lab-pc, since 2026-10-06T09:00:00Z}
     */
    public String describe() {
        return "process " + pid + " on host " + host + ", since " + SECONDS.format(started);
    }
}
