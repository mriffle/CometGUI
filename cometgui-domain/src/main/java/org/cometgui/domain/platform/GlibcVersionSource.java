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

import java.util.Optional;

/**
 * Tells the host baseline check which glibc this machine has, if it can.
 *
 * <p>Deliberately no implementation in this module. Reading the real value means calling {@code
 * gnu_get_libc_version} through {@link java.lang.foreign}, and the branches that matter for
 * correctness -- "this is not Linux", "the symbol is not there", "the C library is musl" -- cannot
 * be exercised on a Linux/glibc build machine at all. Keeping them out of the domain keeps them out
 * of a coverage measurement that could only be satisfied by pretending; the implementation lives in
 * {@code cometgui-app}, and tests here use fakes.
 */
@FunctionalInterface
public interface GlibcVersionSource {

    /**
     * Reads the C library version of the running host.
     *
     * <p>Returns empty rather than throwing whenever the answer is not knowable -- a non-Linux
     * host, a C library that is not glibc, a version string that will not parse. "Undetermined" is
     * a supported outcome of the baseline check ({@link HostBaselineOutcome#GLIBC_UNDETERMINED}),
     * and an implementation that threw would turn a warning into a startup failure.
     *
     * @return the host's glibc version, or empty when it cannot be determined
     */
    Optional<GlibcVersion> detect();
}
