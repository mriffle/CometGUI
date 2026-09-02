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

package org.cometgui.domain.platform;

/**
 * The distinct results of a host baseline check.
 *
 * <p>Distinct and named because {@code R-PLAT-03} requires an actionable diagnostic rather than an
 * opaque failure, and because the two kinds of "not ideal" are not the same thing: a 32-bit host
 * cannot run the managed tools at all, while a glibc version that could not be read is a warning
 * that a later runtime probe may resolve.
 */
public enum HostBaselineOutcome {

    /** The host meets the baseline. */
    SUPPORTED(false),

    /** The host is not 64-bit. No managed tool build exists for it; nothing can proceed. */
    NOT_64_BIT(true),

    /**
     * The host's word size could not be established. Not blocking: the evidence is missing, not
     * negative, and a tool that cannot run will say so when it is probed.
     */
    ARCHITECTURE_UNDETERMINED(false),

    /** The host's glibc is older than the selected tools require. They will not load. */
    GLIBC_TOO_OLD(true),

    /**
     * The host is Linux but its glibc version could not be read. Not blocking: the runtime probe of
     * {@code R-PLAT-02} establishes tool compatibility by execution anyway, and refusing to start
     * on a machine that may well be fine would be worse than warning.
     */
    GLIBC_UNDETERMINED(false);

    private final boolean blocking;

    HostBaselineOutcome(boolean blocking) {
        this.blocking = blocking;
    }

    /**
     * Whether this outcome must stop the workflow rather than warn about it.
     *
     * @return {@code true} for an outcome no run can proceed through
     */
    public boolean blocking() {
        return blocking;
    }
}
