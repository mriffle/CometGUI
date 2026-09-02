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

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * An operating system and a processor architecture together: the thing an artefact is built for and
 * the thing a machine is.
 *
 * <p>Both roles use this one type, and every question the installer asks is a comparison between
 * two of them -- "is there an artefact for this host?", "can this host run that artefact?" (see
 * {@link ArtefactExecutability}). Separate host and artefact types would have made those questions
 * type-safe and would also have doubled the vocabulary the Tool Manager renders; the pair is a
 * platform either way.
 *
 * <p><strong>Nothing here reads a system property.</strong> {@link #of(String, String)} is a pure
 * function of the two strings the application layer passes in, so every platform in the matrix --
 * including the ones this machine is not -- is reachable from a test on any machine. That is the
 * same seam {@code org.cometgui.domain.ports.EnvironmentReader} exists for, and it is why the
 * detector takes arguments instead of calling {@code System.getProperty}.
 *
 * <p><strong>An unrecognised pair is empty, never guessed.</strong> A wrong platform would make the
 * application download an artefact that cannot run and then report a loader failure the user cannot
 * act on. Empty means "this host is not one of the platforms the product knows", which the caller
 * can say plainly.
 *
 * @param operatingSystem the operating system half
 * @param architecture the processor architecture half
 */
public record HostPlatform(HostOperatingSystem operatingSystem, HostArchitecture architecture) {

    /**
     * Validates the pair.
     *
     * @throws NullPointerException if either component is {@code null}
     */
    public HostPlatform {
        Objects.requireNonNull(operatingSystem, "operatingSystem");
        Objects.requireNonNull(architecture, "architecture");
    }

    /**
     * The stable identifier used in the artefact manifest, in the tool cache path and in the
     * provenance record: the operating system's identifier, a hyphen, and the architecture's.
     *
     * <p>{@code linux-x86-64}, {@code macos-aarch64}, {@code windows-x86-64}.
     *
     * @return the identifier, never {@code null} or blank
     */
    public String id() {
        return operatingSystem.id() + "-" + architecture.id();
    }

    /**
     * Recognises a platform from the two system properties a JVM reports.
     *
     * <p>The accepted values are the ones {@code os.name} and {@code os.arch} actually take on the
     * platforms in the specification's matrix, not a general table of every string any JVM has ever
     * printed. Comparison is case-insensitive under {@link Locale#ROOT} and ignores surrounding
     * whitespace, because these values are sometimes read back from a file.
     *
     * <ul>
     *   <li>{@code os.name} beginning {@code Linux}, {@code Mac} or {@code Darwin}, or {@code
     *       Windows} -- the last of which is followed by a release name, as in {@code Windows 11}
     *       or {@code Windows Server 2022};
     *   <li>{@code os.arch} of {@code amd64}, {@code x86_64} or {@code x64}, all of which are
     *       64-bit x86 under different names; or {@code aarch64} or {@code arm64}, which are 64-bit
     *       ARM under different names.
     * </ul>
     *
     * <p>Anything else -- a 32-bit architecture, a platform the product does not support, a blank
     * value -- returns empty rather than a guess.
     *
     * @param osName the value of the {@code os.name} system property
     * @param osArch the value of the {@code os.arch} system property
     * @return the platform, or empty if either value is not one this product recognises
     * @throws NullPointerException if either argument is {@code null}; a JVM that does not set the
     *     property gives the caller an absent value to report, which is not the same thing as an
     *     unrecognised one
     */
    public static Optional<HostPlatform> of(String osName, String osArch) {
        Objects.requireNonNull(osName, "osName");
        Objects.requireNonNull(osArch, "osArch");
        Optional<HostOperatingSystem> operatingSystem = operatingSystemOf(osName);
        Optional<HostArchitecture> architecture = architectureOf(osArch);
        if (operatingSystem.isEmpty() || architecture.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new HostPlatform(operatingSystem.get(), architecture.get()));
    }

    private static Optional<HostOperatingSystem> operatingSystemOf(String osName) {
        String normalised = osName.strip().toLowerCase(Locale.ROOT);
        if (normalised.startsWith("linux")) {
            return Optional.of(HostOperatingSystem.LINUX);
        }
        if (normalised.startsWith("mac") || normalised.startsWith("darwin")) {
            return Optional.of(HostOperatingSystem.MACOS);
        }
        if (normalised.startsWith("windows")) {
            return Optional.of(HostOperatingSystem.WINDOWS);
        }
        return Optional.empty();
    }

    private static Optional<HostArchitecture> architectureOf(String osArch) {
        String normalised = osArch.strip().toLowerCase(Locale.ROOT);
        if ("amd64".equals(normalised) || "x86_64".equals(normalised) || "x64".equals(normalised)) {
            return Optional.of(HostArchitecture.X86_64);
        }
        if ("aarch64".equals(normalised) || "arm64".equals(normalised)) {
            return Optional.of(HostArchitecture.AARCH64);
        }
        return Optional.empty();
    }

    /**
     * Resolves a platform identifier read from a manifest back to a platform.
     *
     * <p>Exact match on both halves, split at the first hyphen -- no operating-system identifier
     * contains one and {@code x86-64} does, so the first hyphen is the boundary. This is the
     * manifest's path and is deliberately stricter than {@link #of(String, String)}: {@code
     * linux-amd64} is rejected here, because a manifest is the project's own file and is expected
     * to use the project's own spelling.
     *
     * @param id the identifier to resolve, for example {@code linux-x86-64}
     * @return the matching platform
     * @throws NullPointerException if {@code id} is {@code null}
     * @throws IllegalArgumentException if the identifier has no hyphen, or if either half is not a
     *     known identifier, with a message naming the rejected value
     */
    public static HostPlatform fromId(String id) {
        Objects.requireNonNull(id, "id");
        int boundary = id.indexOf('-');
        if (boundary < 0) {
            throw new IllegalArgumentException(
                    "not a platform id: \""
                            + id
                            + "\" (expected an operating system and an architecture joined by a"
                            + " hyphen, such as linux-x86-64)");
        }
        return new HostPlatform(
                HostOperatingSystem.fromId(id.substring(0, boundary)),
                HostArchitecture.fromId(id.substring(boundary + 1)));
    }

    /**
     * The platform identifier, so that a platform logged or put in a message reads the way the
     * manifest spells it.
     *
     * @return the same string as {@link #id()}
     */
    @Override
    public String toString() {
        return id();
    }
}
