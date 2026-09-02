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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.cometgui.domain.platform.GlibcVersion;

/**
 * What a machine must already have before an artefact will load on it ({@code R-TOOL-03}).
 *
 * <p>The point of declaring these in the manifest is to be able to say <em>in advance</em> that a
 * build will not run here, instead of discovering it when the loadability probe fails. Percolator
 * makes the difference concrete: its 3.09 Linux {@code .deb} needs {@code GLIBC_2.38} and its
 * 3.06.5 portable archive needs only {@code GLIBC_2.14}, so on a Debian 12 host with glibc 2.36 one
 * is a 946 KB download that ends in a loader failure and the other simply works.
 *
 * <p>Every field is optional or empty by default, because most artefacts declare nothing: {@link
 * #none()} is the common case. An empty requirement is not a promise that the artefact runs
 * everywhere -- it means the manifest declares no floor, and {@code R-PLAT-02} still requires the
 * binary to be executed before it is treated as usable.
 *
 * <p>{@code minimumGlibc} reuses {@link GlibcVersion}, the type the startup baseline check already
 * compares against. A second glibc version type would be a second parser and a second ordering, and
 * the two would eventually disagree about the same host.
 *
 * <p><strong>{@code minimumGlibcxx} is the C++ runtime floor, and it is not the same
 * floor.</strong> A GNU/Linux binary built with GCC records two independent sets of symbol
 * versions: {@code GLIBC_*} from the C library and {@code GLIBCXX_*} from {@code libstdc++}. They
 * move independently -- Percolator 3.07.1 needs {@code GLIBC_2.34} and {@code GLIBCXX_3.4.29}; the
 * 3.09 Debian payload needs {@code GLIBC_2.38} and {@code GLIBCXX_3.4.32} -- and in the loader
 * failure this project executed on its own Debian 12 host <em>the {@code GLIBCXX} line is reported
 * first</em>. A check that knew only about glibc would therefore predict "runnable" for a build
 * that fails on the C++ runtime, which is the specific wrong answer this component exists to
 * prevent.
 *
 * <p>It reuses {@link GlibcVersion} for the same reason {@code minimumGlibc} does, and the reuse is
 * deliberate rather than convenient: a {@code GLIBCXX} version is {@code major.minor.patch} of
 * decimal components compared numerically, which is exactly what that type is, and it is a version
 * of a <em>host library</em> rather than of a tool. The type's name says "glibc" and this is
 * libstdc++, which is the one thing wrong with the reuse; a dedicated {@code GlibcxxVersion} would
 * be a second parser of the same shape, and phase 05 unit 6 did not have the domain scope to add
 * one. Recorded here so that a later phase can weigh that trade rather than rediscover it.
 *
 * @param minimumGlibc the oldest glibc the artefact will load on, absent when none is declared or
 *     the platform is not Linux
 * @param minimumGlibcxx the oldest {@code libstdc++} symbol-version set the artefact will load on,
 *     written as the numbers of a {@code GLIBCXX_x.y.z} version -- {@code 3.4.29} for Percolator
 *     3.07.1. Absent when none is declared, which is the case for every artefact that is statically
 *     linked, is not an ELF binary, or is a JAR
 * @param minimumMacOsVersion the oldest macOS release the artefact will run on, as upstream states
 *     it -- {@code 12.7}, {@code 15.0}; a string rather than a parsed version because nothing in
 *     this project compares macOS versions yet and a type nobody orders would be a type nobody
 *     tests
 * @param requiredHostLibraries libraries the host must already provide, named exactly as the loader
 *     names them. This is how the Windows Visual C++ runtime is declared: {@code MSVCP140.dll},
 *     {@code VCRUNTIME140.dll}, {@code VCRUNTIME140_1.dll}, {@code VCOMP140.DLL}, which the
 *     portable Percolator zip does not ship. Their absence is an {@code R-PLAT-03} loader failure
 *     naming the DLL, never "not XML-capable".
 */
public record MinimumHostRequirements(
        Optional<GlibcVersion> minimumGlibc,
        Optional<GlibcVersion> minimumGlibcxx,
        Optional<String> minimumMacOsVersion,
        List<String> requiredHostLibraries) {

    private static final MinimumHostRequirements NONE =
            new MinimumHostRequirements(
                    Optional.empty(), Optional.empty(), Optional.empty(), List.of());

    /**
     * Validates the requirements and takes a defensive, immutable copy of the library list.
     *
     * @throws NullPointerException if any component is {@code null}
     * @throws IllegalArgumentException if a present {@code minimumMacOsVersion} is blank, if a
     *     required library is blank, or if one is named twice -- with a message naming the field
     *     and the rejected value
     */
    public MinimumHostRequirements {
        Objects.requireNonNull(minimumGlibc, "minimumGlibc");
        Objects.requireNonNull(minimumGlibcxx, "minimumGlibcxx");
        Objects.requireNonNull(minimumMacOsVersion, "minimumMacOsVersion");
        minimumMacOsVersion = strippedIfPresent(minimumMacOsVersion);
        requiredHostLibraries = checkedLibraries(requiredHostLibraries);
    }

    /*
     * Written with map rather than an isEmpty early return so that no branch of it is equivalent
     * under mutation: a method that returns an already-empty Optional cannot be distinguished from
     * one mutated to return Optional.empty(), which leaves a survivor no honest test can kill.
     */
    private static Optional<String> strippedIfPresent(Optional<String> value) {
        return value.map(MinimumHostRequirements::strippedMacOsVersion);
    }

    private static String strippedMacOsVersion(String text) {
        if (text.isBlank()) {
            throw new IllegalArgumentException(
                    "minimumMacOsVersion must not be blank when it is present; leave it absent"
                            + " instead");
        }
        return text.strip();
    }

    private static List<String> checkedLibraries(List<String> libraries) {
        List<String> copy =
                new ArrayList<>(Objects.requireNonNull(libraries, "requiredHostLibraries"));
        Set<String> seen = new LinkedHashSet<>();
        for (int index = 0; index < copy.size(); index++) {
            String library = copy.get(index);
            if (library == null) {
                throw new IllegalArgumentException(
                        "requiredHostLibraries[" + index + "] must not be null");
            }
            if (library.isBlank()) {
                throw new IllegalArgumentException(
                        "requiredHostLibraries[" + index + "] must not be blank");
            }
            String stripped = library.strip();
            if (!seen.add(stripped)) {
                throw new IllegalArgumentException(
                        "requiredHostLibraries names \"" + stripped + "\" more than once");
            }
            copy.set(index, stripped);
        }
        return List.copyOf(copy);
    }

    /**
     * The requirements of an artefact that declares none.
     *
     * @return an instance with no glibc floor, no C++ runtime floor, no macOS floor and no required
     *     host libraries
     */
    public static MinimumHostRequirements none() {
        return NONE;
    }

    /**
     * Whether this artefact declares any host requirement at all.
     *
     * @return {@code true} when every field is absent or empty
     */
    public boolean isEmpty() {
        return minimumGlibc.isEmpty()
                && minimumGlibcxx.isEmpty()
                && minimumMacOsVersion.isEmpty()
                && requiredHostLibraries.isEmpty();
    }

    /**
     * The libraries the host must already provide.
     *
     * <p>Immutable, in the order the manifest declared them, and copied so that the guarantee is
     * visible at the call site -- and to SpotBugs, which reports a record accessor handing out a
     * collection field as {@code EI_EXPOSE_REP} because nothing there shows which kind of list it
     * received.
     *
     * @return the required libraries, immutable and possibly empty
     */
    public List<String> requiredHostLibraries() {
        return List.copyOf(requiredHostLibraries);
    }
}
