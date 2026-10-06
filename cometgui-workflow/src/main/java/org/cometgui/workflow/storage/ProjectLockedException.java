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

package org.cometgui.workflow.storage;

import java.io.IOException;
import java.io.Serial;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.cometgui.domain.project.LockOwner;

/**
 * Thrown when a project cannot be locked because someone else holds, or may hold, its lock ({@code
 * R-RUN-05}).
 *
 * <p>It names the owner whenever the lock file records one -- process, host and since when -- and
 * says which of four situations this is, because each asks the user for something different.
 */
public final class ProjectLockedException extends IOException {

    @Serial private static final long serialVersionUID = 1L;

    /** Why the lock was refused. */
    public enum Reason {

        /** Another part of this same CometGUI process already holds the lock. */
        HELD_IN_THIS_PROCESS,

        /** Another process on this computer holds the operating-system lock: it is live. */
        HELD_BY_ANOTHER_PROCESS,

        /**
         * The lock file names a process on another computer. Never broken from here, because this
         * computer cannot tell whether that process is alive.
         */
        OWNER_ON_ANOTHER_HOST,

        /**
         * The lock file names a process on this computer that does not hold the operating-system
         * lock but is still running -- most likely an unrelated process that reused the number. Not
         * judged stale, because the specification defines stale as "no longer alive".
         */
        OWNER_STILL_RUNNING
    }

    /** Why. */
    private final Reason reason;

    /** The recorded owner's process id; zero when the lock file names no owner. */
    private final long ownerPid;

    /** The recorded owner's host; {@code null} when the lock file names no owner. */
    private final String ownerHost;

    /** When the recorded owner took the lock; {@code null} when the lock file names no owner. */
    private final Instant ownerStarted;

    /**
     * Creates the refusal.
     *
     * @param reason why
     * @param owner the recorded owner, or {@code null} if the file names none
     * @param message the full explanation
     */
    ProjectLockedException(Reason reason, LockOwner owner, String message) {
        super(message);
        this.reason = Objects.requireNonNull(reason, "reason");
        this.ownerPid = owner == null ? 0 : owner.pid();
        this.ownerHost = owner == null ? null : owner.host();
        this.ownerStarted = owner == null ? null : owner.started();
    }

    /**
     * Why the lock was refused.
     *
     * @return the reason
     */
    public Reason reason() {
        return reason;
    }

    /**
     * The owner the lock file records.
     *
     * @return the owner; empty when another process holds the lock but has not yet written who it
     *     is
     */
    public Optional<LockOwner> owner() {
        return ownerHost == null
                ? Optional.empty()
                : Optional.of(new LockOwner(ownerPid, ownerHost, ownerStarted));
    }
}
