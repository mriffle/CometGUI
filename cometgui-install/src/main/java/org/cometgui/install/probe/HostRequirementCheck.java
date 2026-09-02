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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.cometgui.domain.platform.GlibcVersion;
import org.cometgui.domain.tools.MinimumHostRequirements;

/**
 * Does this host meet what an artefact declares it needs? -- the advance check {@link
 * MinimumHostRequirements} does not have.
 *
 * <h2>The rule</h2>
 *
 * <p><strong>An offer is marked not-runnable only when a specific unmet floor can be
 * named.</strong> Absence of information is never a refusal, and it is never an approval either.
 * {@code R-PLAT-02} establishes compatibility by executing the binary, so this check exists to save
 * a download and a failure the user cannot act on -- not to replace the probe. Every floor this
 * class cannot measure therefore answers {@link HostRequirementVerdict.Status#UNDETERMINED}, and
 * {@link ProbeGatedOffers} offers the artefact anyway.
 *
 * <p><strong>The boundary is inclusive.</strong> A host with precisely {@code GLIBC_2.34} meets
 * Percolator 3.07.1's declared floor of {@code GLIBC_2.34} and must be offered it: a symbol-version
 * floor says "this version or newer", and refusing at equality would withhold the default
 * XML-capable Percolator from every RHEL 9, Ubuntu 22.04 and Debian 12 machine that upstream
 * actually built it for. The comparison is {@link GlibcVersion#isAtLeast}, which is inclusive, and
 * {@code HostRequirementCheckTest} grades it at the floor, one below and one above -- for both
 * Linux floors.
 *
 * <h2>Which floors are measured, and which are not</h2>
 *
 * <ul>
 *   <li>{@code minimumGlibc} -- measured, through the domain's {@code GlibcVersionSource}.
 *   <li>{@code minimumGlibcxx} -- measured, through {@link HostCxxRuntime}. It is a separate
 *       question from the C library's and it is the one the loader answers <em>first</em>: on this
 *       project's own Debian 12 host the 3.09 payload reports {@code GLIBCXX_3.4.32} before it
 *       reports {@code GLIBC_2.38}, so a check knowing only glibc would have called that build
 *       runnable.
 *   <li>{@code minimumMacOsVersion} -- <strong>not measured.</strong> Nothing in this project reads
 *       a macOS release version, and no macOS machine has ever run any of it.
 *   <li>{@code requiredHostLibraries} -- <strong>not measured.</strong> These are the four Visual
 *       C++ runtime DLLs the Windows portable zip does not ship, and checking for them means asking
 *       Windows; no Windows machine has run this probe.
 * </ul>
 *
 * <p>Both unmeasured floors answer {@code UNDETERMINED} rather than {@code MET}, which is the
 * difference between "we did not check" and "it is fine".
 */
public final class HostRequirementCheck {

    /** The C library, as the dynamic loader names it in its own diagnostics. */
    public static final String GLIBC_LIBRARY = "libc.so.6";

    /** The GNU C++ runtime, as the dynamic loader names it in its own diagnostics. */
    public static final String GLIBCXX_LIBRARY = "libstdc++.so.6";

    private HostRequirementCheck() {
        throw new AssertionError(
                "HostRequirementCheck is a utility class and is never instantiated");
    }

    /**
     * Checks one artefact's declared requirements against one host.
     *
     * <p>Every floor is evaluated, not the first one that has something to say. A named unmet floor
     * is returned ahead of any undetermined one, because it is the answer a user can act on; the
     * two Linux floors are evaluated in the order the manifest declares them, so a host with an
     * unknown C library and a known, unmet C++ runtime is still refused with the C++ floor named.
     *
     * @param requirements what the artefact declares
     * @param host what this machine was established to be
     * @return the verdict
     * @throws NullPointerException if either argument is {@code null}
     */
    public static HostRequirementVerdict check(
            MinimumHostRequirements requirements, HostRuntimeVersions host) {
        Objects.requireNonNull(requirements, "requirements");
        Objects.requireNonNull(host, "host");
        List<HostRequirementVerdict> verdicts = new ArrayList<>();
        requirements
                .minimumGlibc()
                .ifPresent(
                        floor ->
                                verdicts.add(
                                        symbolVersionVerdict(
                                                "minimumGlibc",
                                                GLIBC_LIBRARY,
                                                LoaderOutputClassifier.GLIBC_PREFIX,
                                                floor,
                                                host.glibc())));
        requirements
                .minimumGlibcxx()
                .ifPresent(
                        floor ->
                                verdicts.add(
                                        symbolVersionVerdict(
                                                "minimumGlibcxx",
                                                GLIBCXX_LIBRARY,
                                                LoaderOutputClassifier.GLIBCXX_PREFIX,
                                                floor,
                                                host.glibcxx())));
        if (requirements.minimumMacOsVersion().isPresent()) {
            verdicts.add(HostRequirementVerdict.undetermined("minimumMacOsVersion"));
        }
        if (!requirements.requiredHostLibraries().isEmpty()) {
            verdicts.add(HostRequirementVerdict.undetermined("requiredHostLibraries"));
        }
        return firstOf(verdicts);
    }

    private static HostRequirementVerdict symbolVersionVerdict(
            String field,
            String library,
            String prefix,
            GlibcVersion floor,
            Optional<GlibcVersion> onThisHost) {
        if (onThisHost.isEmpty()) {
            return HostRequirementVerdict.undetermined(field);
        }
        GlibcVersion available = onThisHost.get();
        if (available.isAtLeast(floor)) {
            return HostRequirementVerdict.met();
        }
        return HostRequirementVerdict.unmet(
                field, library, prefix + floor.text(), prefix + available.text());
    }

    private static HostRequirementVerdict firstOf(List<HostRequirementVerdict> verdicts) {
        return verdicts.stream()
                .filter(HostRequirementVerdict::isRefusal)
                .findFirst()
                .or(
                        () ->
                                verdicts.stream()
                                        .filter(
                                                verdict ->
                                                        verdict.status()
                                                                == HostRequirementVerdict.Status
                                                                        .UNDETERMINED)
                                        .findFirst())
                .orElseGet(HostRequirementVerdict::met);
    }
}
