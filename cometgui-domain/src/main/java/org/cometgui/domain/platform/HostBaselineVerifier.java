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

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.cometgui.domain.ports.EnvironmentReader;

/**
 * Checks, at startup, that this machine meets the baseline CometGUI declares ({@code R-PLAT-01}).
 *
 * <p>Two questions are asked. Is the host 64-bit? Every managed Comet and Percolator build is, so a
 * 32-bit host is a blocking outcome. And on Linux, is glibc new enough for the tools the user has
 * selected?
 *
 * <p><strong>The glibc floor is per tool and is supplied by the caller.</strong> Comet 2026.02.2
 * and Percolator 3.07.1 are built on different distributions and do not require the same version,
 * and the only trustworthy statement about a binary's requirement comes from running it: that is
 * {@code R-PLAT-02}, and phase 05's runtime probe owns it. This class owns the mechanism -- the
 * comparison, the outcomes and the wording -- and takes the number as an argument. It must not grow
 * a hard-coded floor: a wrong floor here either blocks a host that works or admits one that does
 * not, and both are worse than the probe.
 *
 * <p>Nothing here touches the operating system directly. The environment arrives through {@link
 * EnvironmentReader} and the glibc version through {@link GlibcVersionSource}, so every outcome is
 * reachable in a test on any machine -- which is the point of {@code R-PROC-01}.
 */
public final class HostBaselineVerifier {

    /**
     * Architectures the supported platform matrix's 64-bit tiers run on, plus the ones a JVM may
     * report on hosts CometGUI does not support: recognising them is how {@code os.arch} answers
     * the word-size question at all.
     */
    private static final Set<String> SIXTY_FOUR_BIT_ARCHITECTURES =
            Set.of(
                    "amd64",
                    "x86_64",
                    "x86-64",
                    "aarch64",
                    "arm64",
                    "ppc64",
                    "ppc64le",
                    "s390x",
                    "riscv64",
                    "sparcv9",
                    "loongarch64");

    /** Architectures known to be 32-bit. Anything in neither set leaves the question open. */
    private static final Set<String> THIRTY_TWO_BIT_ARCHITECTURES =
            Set.of("x86", "i386", "i486", "i586", "i686", "arm", "armv7l", "ppc", "s390", "sparc");

    private static final String NOT_SET = "(not set)";

    private final EnvironmentReader environment;
    private final GlibcVersionSource glibcVersions;

    /**
     * Creates a verifier over the two seams it reads the host through.
     *
     * @param environment reads {@code os.name}, {@code os.arch} and the data model
     * @param glibcVersions reads the C library version, or reports that it cannot
     * @throws NullPointerException if either argument is {@code null}
     */
    public HostBaselineVerifier(EnvironmentReader environment, GlibcVersionSource glibcVersions) {
        this.environment = Objects.requireNonNull(environment, "environment");
        this.glibcVersions = Objects.requireNonNull(glibcVersions, "glibcVersions");
    }

    /**
     * Verifies the host against the baseline and a stated glibc requirement.
     *
     * <p>Blocking outcomes are reported ahead of warnings, and the architecture is settled first,
     * because a 32-bit host cannot run any managed tool whatever its C library says. Where two
     * warnings apply, the glibc one is reported: it is the one a Linux user can act on.
     *
     * @param requiredGlibcVersion the oldest glibc the selected tools will load on, as established
     *     for those specific binaries rather than assumed
     * @return the outcome and the sentence to show the user
     * @throws NullPointerException if {@code requiredGlibcVersion} is {@code null}
     */
    public HostBaselineReport verify(GlibcVersion requiredGlibcVersion) {
        Objects.requireNonNull(requiredGlibcVersion, "requiredGlibcVersion");
        Optional<Boolean> sixtyFourBit = sixtyFourBit();
        if (sixtyFourBit.isPresent() && !sixtyFourBit.get()) {
            return new HostBaselineReport(
                    HostBaselineOutcome.NOT_64_BIT,
                    "CometGUI requires a 64-bit operating system, but this host reports a 32-bit"
                            + " Java runtime ("
                            + architectureEvidence()
                            + "). Every managed Comet and Percolator build is 64-bit only.");
        }
        HostBaselineReport glibcReport = verifyGlibc(requiredGlibcVersion);
        if (glibcReport.outcome() != HostBaselineOutcome.SUPPORTED) {
            return glibcReport;
        }
        if (sixtyFourBit.isEmpty()) {
            return new HostBaselineReport(
                    HostBaselineOutcome.ARCHITECTURE_UNDETERMINED,
                    "CometGUI requires a 64-bit operating system and could not establish this"
                            + " host's word size ("
                            + architectureEvidence()
                            + "). Startup continues; a tool that cannot run here will report it"
                            + " when it is probed.");
        }
        return glibcReport;
    }

    private HostBaselineReport verifyGlibc(GlibcVersion required) {
        Optional<String> osName = environment.osName();
        if (osName.isEmpty()) {
            return new HostBaselineReport(
                    HostBaselineOutcome.GLIBC_UNDETERMINED,
                    "The operating system could not be determined ("
                            + EnvironmentReader.OS_NAME_PROPERTY
                            + " is not set), so the selected tools' requirement of glibc "
                            + required
                            + " or newer could not be checked. Startup continues.");
        }
        if (!isLinux(osName.get())) {
            return new HostBaselineReport(
                    HostBaselineOutcome.SUPPORTED,
                    "Host baseline OK: 64-bit ("
                            + architectureEvidence()
                            + "). The glibc requirement does not apply to "
                            + osName.get()
                            + ".");
        }
        Optional<GlibcVersion> host = glibcVersions.detect();
        if (host.isEmpty()) {
            return new HostBaselineReport(
                    HostBaselineOutcome.GLIBC_UNDETERMINED,
                    "This host is Linux but its glibc version could not be determined; the"
                            + " selected tools require glibc "
                            + required
                            + " or newer. Startup continues, and a tool that will not load will"
                            + " report the missing symbol version by name.");
        }
        GlibcVersion found = host.get();
        if (!found.isAtLeast(required)) {
            return new HostBaselineReport(
                    HostBaselineOutcome.GLIBC_TOO_OLD,
                    "This host has glibc "
                            + found.text()
                            + ", but the selected tools require glibc "
                            + required
                            + " or newer. Select tool versions built for an older glibc, or run"
                            + " CometGUI on a distribution with glibc "
                            + required
                            + " or newer.");
        }
        return new HostBaselineReport(
                HostBaselineOutcome.SUPPORTED,
                "Host baseline OK: 64-bit ("
                        + architectureEvidence()
                        + "), glibc "
                        + found.text()
                        + " meets the required "
                        + required
                        + ".");
    }

    private static boolean isLinux(String osName) {
        return osName.toLowerCase(Locale.ROOT).contains("linux");
    }

    /**
     * Answers the word-size question from the data model first -- it is unambiguous where it is set
     * -- and from {@code os.arch} when it is not. Empty means neither answered, which is a warning
     * rather than a verdict.
     */
    private Optional<Boolean> sixtyFourBit() {
        Optional<String> dataModel = environment.dataModel();
        if (dataModel.isPresent()) {
            String bits = dataModel.get().strip();
            if ("64".equals(bits)) {
                return Optional.of(Boolean.TRUE);
            }
            if ("32".equals(bits)) {
                return Optional.of(Boolean.FALSE);
            }
        }
        Optional<String> osArch = environment.osArch();
        if (osArch.isPresent()) {
            String architecture = osArch.get().strip().toLowerCase(Locale.ROOT);
            if (SIXTY_FOUR_BIT_ARCHITECTURES.contains(architecture)) {
                return Optional.of(Boolean.TRUE);
            }
            if (THIRTY_TWO_BIT_ARCHITECTURES.contains(architecture)) {
                return Optional.of(Boolean.FALSE);
            }
        }
        return Optional.empty();
    }

    /** The host values a diagnostic has to name, whether or not they are set. */
    private String architectureEvidence() {
        return EnvironmentReader.DATA_MODEL_PROPERTY
                + "="
                + environment.dataModel().orElse(NOT_SET)
                + ", "
                + EnvironmentReader.OS_ARCH_PROPERTY
                + "="
                + environment.osArch().orElse(NOT_SET);
    }
}
