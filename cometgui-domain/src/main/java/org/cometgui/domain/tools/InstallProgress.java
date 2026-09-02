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

package org.cometgui.domain.tools;

import java.util.Objects;

/**
 * One progress report from an install in flight.
 *
 * <p><strong>{@code totalBytes} may be negative.</strong> It is negative exactly when the server
 * declared no length, which is the convention {@code
 * org.cometgui.domain.ports.DownloadProgressListener} already uses and which this type keeps rather
 * than inventing a second one. <strong>A caller must not divide by it without checking</strong> --
 * {@link #hasKnownTotal()} is that check. A progress bar that divides blindly shows a negative
 * fraction, and a progress bar that treats a negative total as zero shows a completed install that
 * has not started.
 *
 * <p>{@code bytesTransferred} is a byte count and is meaningful only while bytes are moving. In the
 * phases after {@link InstallPhase#DOWNLOADING} it stays at whatever the download ended on, so that
 * a listener can still report the size of what it fetched.
 *
 * @param tool which tool is being installed
 * @param version which version of it
 * @param phase where the install has got to
 * @param bytesTransferred bytes fetched so far, never negative
 * @param totalBytes the expected total, or a negative number when the server declared none
 */
public record InstallProgress(
        ToolName tool,
        ToolVersion version,
        InstallPhase phase,
        long bytesTransferred,
        long totalBytes) {

    /**
     * Validates the report.
     *
     * @throws NullPointerException if {@code tool}, {@code version} or {@code phase} is {@code
     *     null}
     * @throws IllegalArgumentException if {@code bytesTransferred} is negative, with a message
     *     naming the field and the rejected value
     */
    public InstallProgress {
        Objects.requireNonNull(tool, "tool");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(phase, "phase");
        if (bytesTransferred < 0) {
            throw new IllegalArgumentException(
                    "bytesTransferred must not be negative, but was: " + bytesTransferred);
        }
    }

    /**
     * Whether {@link #totalBytes()} is a real size.
     *
     * <p>Ask this before dividing. False means the server declared no content length, which is
     * common enough on redirect chains that a progress bar has to have an indeterminate mode.
     *
     * @return {@code true} when the total is known and may be divided by
     */
    public boolean hasKnownTotal() {
        return totalBytes >= 0;
    }
}
