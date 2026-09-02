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

package org.cometgui.install.probe;

import java.util.Objects;
import java.util.Optional;
import org.cometgui.domain.platform.GlibcVersion;
import org.cometgui.domain.platform.GlibcVersionSource;
import org.cometgui.domain.tools.MinimumHostRequirements;

/**
 * What this machine's C and C++ runtimes actually are, as far as anything could establish.
 *
 * <p>The pair mirrors {@link MinimumHostRequirements}'s two Linux floors, because they are the two
 * questions the advance check asks and they have different answers. A binary built with GCC records
 * {@code GLIBC_*} and {@code GLIBCXX_*} symbol versions independently: Percolator 3.07.1 needs
 * {@code GLIBC_2.34} and {@code GLIBCXX_3.4.29}, the 3.09 Debian payload needs {@code GLIBC_2.38}
 * and {@code GLIBCXX_3.4.32}, and on the Debian 12 host this project runs on the loader reports the
 * {@code GLIBCXX} line <em>first</em>.
 *
 * <p><strong>Either half may be absent, and absent is a supported answer.</strong> It means "this
 * was not established", which {@link HostRequirementCheck} turns into {@link
 * HostRequirementVerdict.Status#UNDETERMINED} -- never into a refusal and never into an approval.
 * {@code R-PLAT-02} settles compatibility by executing the binary, so an unknown host version costs
 * an advance answer, not a probe.
 *
 * @param glibc the host's C library version, from {@link GlibcVersionSource}; empty when that
 *     source could not determine one, which is every non-Linux host and any host whose C library is
 *     not glibc
 * @param glibcxx the newest {@code GLIBCXX_x.y.z} version this host's {@code libstdc++} provides,
 *     as the numbers alone; empty when no {@code libstdc++} could be found or read
 */
public record HostRuntimeVersions(Optional<GlibcVersion> glibc, Optional<GlibcVersion> glibcxx) {

    private static final HostRuntimeVersions UNKNOWN =
            new HostRuntimeVersions(Optional.empty(), Optional.empty());

    /**
     * Validates the pair.
     *
     * @throws NullPointerException if either component is {@code null}
     */
    public HostRuntimeVersions {
        Objects.requireNonNull(glibc, "glibc");
        Objects.requireNonNull(glibcxx, "glibcxx");
    }

    /**
     * A host about which nothing was established.
     *
     * @return an instance with both versions absent
     */
    public static HostRuntimeVersions unknown() {
        return UNKNOWN;
    }

    /**
     * Reads what this machine reports, through the two seams that can answer.
     *
     * <p>The C library version comes from the caller's {@link GlibcVersionSource}, which is the
     * port {@code org.cometgui.domain.platform.HostBaselineVerifier} already uses -- one reader,
     * one parser, one ordering. The C++ runtime version comes from {@link HostCxxRuntime}, which
     * reads the version definitions out of the {@code libstdc++} this process's own loader
     * resolved.
     *
     * @param glibcVersions the C library reader
     * @return what could be established, with an absent half wherever nothing could
     * @throws NullPointerException if {@code glibcVersions} is {@code null}
     */
    public static HostRuntimeVersions detect(GlibcVersionSource glibcVersions) {
        Objects.requireNonNull(glibcVersions, "glibcVersions");
        return new HostRuntimeVersions(glibcVersions.detect(), HostCxxRuntime.hostGlibcxx());
    }
}
