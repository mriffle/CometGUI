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
 * Whether a host can run an artefact built for a given platform, and how.
 *
 * <p>This exists because of {@code D-004}. The only XML-capable macOS Percolator upstream publishes
 * is <strong>x86-64</strong>, and the project does not build Percolator from source, so on Apple
 * silicon that stage runs under <strong>Rosetta 2</strong>. The application has to say so in
 * advance rather than fail with an exec-format error, which means "can this run here?" has three
 * answers and not two.
 *
 * <p><strong>Nothing else translates.</strong> Rosetta 2 is macOS running x86-64 code on Apple
 * silicon and that is the whole of it: a Linux host does not run a Windows artefact, Windows does
 * not run a Mach-O binary, and an x86-64 machine does not run {@code aarch64} code anywhere. The
 * rule below is therefore one special case and a default, and the test proves the special case does
 * not leak into the other direction.
 *
 * <p>A verdict of {@link #TRANSLATED_ROSETTA_2} says the artefact is <em>eligible</em>, not that it
 * will run: Rosetta 2 is an optional macOS component and may be absent. Confirming it is present is
 * a runtime check phase 05's probe makes on the host, and it is why this value is distinguishable
 * from {@link #NATIVE} rather than folded into it.
 */
public enum ArtefactExecutability {

    /** The artefact is built for this host's own operating system and architecture. */
    NATIVE,

    /**
     * The artefact is a macOS x86-64 build and the host is Apple silicon, so it runs through
     * Rosetta 2 -- subject to Rosetta 2 actually being installed, which is a separate runtime
     * check.
     */
    TRANSLATED_ROSETTA_2,

    /** The artefact cannot run on this host at all, translated or otherwise. */
    INCOMPATIBLE;

    /**
     * Whether an artefact with this verdict may be offered to the user at all.
     *
     * <p>True for {@link #NATIVE} and {@link #TRANSLATED_ROSETTA_2}, false for {@link
     * #INCOMPATIBLE}. {@code R-PERC-01} forbids presenting a build that cannot run, so this is the
     * question the Tool Manager asks before it renders a row.
     *
     * @return {@code true} unless the verdict is {@link #INCOMPATIBLE}
     */
    public boolean isRunnable() {
        return this != INCOMPATIBLE;
    }

    /**
     * Decides whether {@code host} can run an artefact built for {@code artefactPlatform}.
     *
     * <p>The argument order is host first, then artefact -- "can this machine run that build" --
     * and it matters: {@code macos-aarch64} running a {@code macos-x86-64} artefact is {@link
     * #TRANSLATED_ROSETTA_2}, while {@code macos-x86-64} running a {@code macos-aarch64} artefact
     * is {@link #INCOMPATIBLE}. Rosetta 2 translates in one direction only.
     *
     * @param host the machine in front of the user
     * @param artefactPlatform the platform the artefact was built for
     * @return how, or whether, the host can run it
     * @throws NullPointerException if either argument is {@code null}
     */
    public static ArtefactExecutability of(HostPlatform host, HostPlatform artefactPlatform) {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(artefactPlatform, "artefactPlatform");
        /*
         * The translation case is tested BEFORE the identity case, and the order is deliberate.
         * The two are disjoint -- an Apple silicon host is never equal to an x86-64 artefact
         * platform -- so the result is the same either way, but with the identity test first the
         * final clause below could never be observed to be false: the only pair that reaches it
         * with the first three clauses true is macos-aarch64 against itself, which an earlier
         * identity test would already have answered. An unreachable branch is a mutation no honest
         * test can kill, so the rule is written the way that leaves every clause reachable.
         */
        if (isRosettaEligible(host, artefactPlatform)) {
            return TRANSLATED_ROSETTA_2;
        }
        if (host.equals(artefactPlatform)) {
            return NATIVE;
        }
        return INCOMPATIBLE;
    }

    private static boolean isRosettaEligible(HostPlatform host, HostPlatform artefactPlatform) {
        return host.operatingSystem() == HostOperatingSystem.MACOS
                && host.architecture() == HostArchitecture.AARCH64
                && artefactPlatform.operatingSystem() == HostOperatingSystem.MACOS
                && artefactPlatform.architecture() == HostArchitecture.X86_64;
    }
}
