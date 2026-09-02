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
 * A processor architecture a tool artefact is built for.
 *
 * <p>Two, matching the specification's supported platform matrix. Both 64-bit: every managed Comet
 * and Percolator build is, and {@code R-PLAT-01} blocks a 32-bit host at startup, so a 32-bit
 * constant here would describe a host the product has already refused.
 *
 * <p>The identifier is {@code x86-64} rather than any of the half-dozen spellings a JVM, a packager
 * or a release asset uses for the same thing -- {@code amd64}, {@code x86_64}, {@code x64}. Those
 * spellings are inputs to {@link HostPlatform#of(String, String)}; this one is the project's own,
 * written into manifests and cache paths.
 */
public enum HostArchitecture {

    /** 64-bit x86, which every tier-1 platform runs and every managed Percolator is built for. */
    X86_64("x86-64"),

    /** 64-bit ARM: Apple silicon, and the Linux builds Comet publishes but Percolator does not. */
    AARCH64("aarch64");

    private final String id;

    HostArchitecture(String id) {
        this.id = id;
    }

    /**
     * The stable identifier used in the artefact manifest and in a platform identifier.
     *
     * @return the identifier, never {@code null} or blank
     */
    public String id() {
        return id;
    }

    /**
     * Resolves an identifier read from a manifest back to its constant.
     *
     * <p>Exact match: no trimming and no case folding, for the reason given on {@link
     * ToolName#fromId(String)}. In particular {@code amd64} is <em>not</em> accepted here -- it is
     * a value {@code os.arch} takes, and translating it is {@link HostPlatform#of(String,
     * String)}'s job, not a manifest reader's.
     *
     * @param id the identifier to resolve
     * @return the matching architecture
     * @throws NullPointerException if {@code id} is {@code null}
     * @throws IllegalArgumentException if no architecture has that identifier, with a message
     *     naming the rejected value and listing what is accepted
     */
    public static HostArchitecture fromId(String id) {
        Objects.requireNonNull(id, "id");
        for (HostArchitecture architecture : values()) {
            if (architecture.id.equals(id)) {
                return architecture;
            }
        }
        throw new IllegalArgumentException(
                "no architecture has the id \"" + id + "\"; expected one of [x86-64, aarch64]");
    }
}
