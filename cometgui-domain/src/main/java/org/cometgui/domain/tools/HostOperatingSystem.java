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
 * An operating system CometGUI publishes or probes tool artefacts for.
 *
 * <p>Three, matching the specification's supported platform matrix. This is the operating system
 * half of a {@link HostPlatform}; nothing here says whether a particular tool is available on it,
 * which is the manifest's business and the probe's.
 *
 * <p>The identifier is a stored field rather than {@code name().toLowerCase()}, for the reason
 * given on {@link ToolName}: it is written into manifest keys and artefact identifiers, and a
 * rename of a Java constant must not silently change a file format.
 */
public enum HostOperatingSystem {

    /** Linux, the project's reference platform for CI, real-tool tests and the canonical E2E. */
    LINUX("linux"),

    /** macOS, where an x86-64 Percolator runs under Rosetta 2 on Apple silicon ({@code D-004}). */
    MACOS("macos"),

    /** Windows, the only platform on which Comet reads Thermo RAW. */
    WINDOWS("windows");

    private final String id;

    HostOperatingSystem(String id) {
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
     * ToolName#fromId(String)}.
     *
     * @param id the identifier to resolve
     * @return the matching operating system
     * @throws NullPointerException if {@code id} is {@code null}
     * @throws IllegalArgumentException if no operating system has that identifier, with a message
     *     naming the rejected value and listing what is accepted
     */
    public static HostOperatingSystem fromId(String id) {
        Objects.requireNonNull(id, "id");
        for (HostOperatingSystem operatingSystem : values()) {
            if (operatingSystem.id.equals(id)) {
                return operatingSystem;
            }
        }
        throw new IllegalArgumentException(
                "no operating system has the id \""
                        + id
                        + "\"; expected one of [linux, macos, windows]");
    }
}
