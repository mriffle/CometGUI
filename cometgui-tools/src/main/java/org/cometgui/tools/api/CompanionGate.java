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

package org.cometgui.tools.api;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import org.cometgui.domain.tools.HostOperatingSystem;
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.domain.tools.ToolCapability;

/**
 * A capability that only exists when named files are installed beside the executable.
 *
 * <p>{@code R-TOOL-02}'s rule, as data. Comet reads Thermo RAW files on Windows only when {@code
 * CometWrapper.dll}, {@code ThermoFisher.CommonCore.Data.dll} and {@code
 * ThermoFisher.CommonCore.RawFileReader.dll} sit beside {@code comet.exe}, and "an install missing
 * them shall not advertise {@code THERMO_RAW_WINDOWS}". The artefact manifest already states that:
 * each of Comet's three Windows companions carries {@code "gatesCapability": "THERMO_RAW_WINDOWS"},
 * so a probe reads a gate rather than deciding one.
 *
 * <p><strong>The operating system is part of the gate, not a test the probe invents.</strong> The
 * three companions hang off the manifest's <em>Windows row</em>, and it is that row's own {@code
 * os} field the gate carries. Keying it on anything else would repeat the mistake this phase paid
 * for twice: a rule keyed on the wrong attribute of the right idea. A gate built from the Linux row
 * -- which declares no companions at all -- gates nothing, which is why a Linux Comet never
 * advertises a Windows-only capability however many files happen to be lying beside it.
 *
 * @param capability what the files unlock
 * @param operatingSystem the host the gate is a fact about, from the manifest row the companions
 *     belong to
 * @param fileNames the file names that must all be present beside the executable; at least one, and
 *     names only -- a gate is about what is installed next to the binary, not about a path
 */
public record CompanionGate(
        ToolCapability capability, HostOperatingSystem operatingSystem, Set<String> fileNames) {

    /**
     * Validates the gate and takes an immutable copy of the names.
     *
     * @throws NullPointerException if any component or any name is {@code null}
     * @throws IllegalArgumentException if there are no names, if a name is blank, or if a name
     *     carries a path separator -- with a message naming the rejected value
     */
    public CompanionGate {
        Objects.requireNonNull(capability, "capability");
        Objects.requireNonNull(operatingSystem, "operatingSystem");
        fileNames = checkedNames(fileNames);
    }

    private static Set<String> checkedNames(Set<String> fileNames) {
        Set<String> copy = new LinkedHashSet<>(Objects.requireNonNull(fileNames, "fileNames"));
        if (copy.isEmpty()) {
            throw new IllegalArgumentException(
                    "a companion gate with no file names would grant its capability to every"
                            + " install, which is the opposite of what R-TOOL-02 asks for");
        }
        for (String name : copy) {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException(
                        "a companion file name must not be blank, but was: \"" + name + "\"");
            }
            if (name.indexOf('/') >= 0 || name.indexOf('\\') >= 0) {
                throw new IllegalArgumentException(
                        "a companion file name must be a name beside the executable, not a path,"
                                + " but was: \""
                                + name
                                + "\"");
            }
        }
        return Set.copyOf(copy);
    }

    /**
     * The file names, immutable.
     *
     * @return the names, never empty
     */
    @Override
    public Set<String> fileNames() {
        return Set.copyOf(fileNames);
    }

    /**
     * Whether this gate is open for an installed executable on a host.
     *
     * @param host the machine being probed
     * @param executable the installed executable; the companions are looked for in its directory
     * @return {@code true} only when the host's operating system is the gate's and every named file
     *     is a regular file beside the executable
     * @throws NullPointerException if either argument is {@code null}
     */
    public boolean isOpenFor(HostPlatform host, Path executable) {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(executable, "executable");
        if (host.operatingSystem() != operatingSystem) {
            return false;
        }
        Path directory = executable.toAbsolutePath().getParent();
        if (directory == null) {
            return false;
        }
        for (String name : fileNames) {
            if (!Files.isRegularFile(directory.resolve(name))) {
                return false;
            }
        }
        return true;
    }
}
