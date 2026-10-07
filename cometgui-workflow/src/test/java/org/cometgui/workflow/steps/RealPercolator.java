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

package org.cometgui.workflow.steps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.cometgui.domain.tools.CapabilityEvidence;
import org.cometgui.domain.tools.DeclaredCapability;
import org.cometgui.domain.tools.HostArchitecture;
import org.cometgui.domain.tools.HostOperatingSystem;
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.domain.tools.ToolAdvisory;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.domain.tools.ToolInstallState;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolOrigin;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.tools.api.ToolRunner;
import org.cometgui.tools.percolator.PercolatorCapabilityProbe;
import org.cometgui.tools.process.ProcessService;

/**
 * The real Percolator builds of the real-binary tests, each held to its SHA-256 before use and each
 * probed here by the one capability probe -- never a hand-typed capability set (design decision
 * P9-13). A missing or changed fixture FAILS the test that needs it, naming how to refill it;
 * nothing skips.
 *
 * <ul>
 *   <li><strong>3.07.1</strong>, the managed Linux build: the {@code percolator} member of the
 *       mirrored portable zip, held to the manifest's {@code memberSha256} typed out here.
 *   <li><strong>3.09</strong>, as a registered local binary would be: the wrapper {@code
 *       scratch/percolator/3.09/run-percolator-3.09.sh}, the upstream {@code .rpm}'s binary and the
 *       two Boost 1.66 libraries behind it, each held to its measured SHA-256 (Phase 09 unit 1).
 * </ul>
 */
final class RealPercolator {

    static final String ZIP_3071 = "rel-3-07-01__percolator-noxml-ubuntu-portable.zip";

    /** The manifest's {@code memberSha256} for the 3.07.1 Linux portable zip, hand-typed. */
    static final String SHA256_3071 =
            "1ba38acf09520cc89d5ed907ed0382c4d23876a7e20ec3e91cbbaa2ed431237c";

    /** The 3.07.1 install's download size, the zip plus the {@code .deb} the XSDs come from. */
    static final long DOWNLOAD_3071 = 2_798_963L;

    static final String DIR_309 = "scratch/percolator/3.09";

    static final String WRAPPER_309 = DIR_309 + "/run-percolator-3.09.sh";

    static final String SHA256_WRAPPER_309 =
            "fe1b018a3afb0f97d6ff79e18264e282924f274d1ae7c71ff64d3825538f3e90";

    static final String BINARY_309 = DIR_309 + "/linux-x86_64-from-rpm/usr/bin/percolator";

    static final String SHA256_BINARY_309 =
            "c31f613929f06ef0f519623ed7ffd39253ce23d3681cc4cd825791c15e49ba26";

    static final String BOOST_FILESYSTEM_309 =
            DIR_309 + "/deps/usr/lib64/libboost_filesystem.so.1.66.0";

    static final String SHA256_BOOST_FILESYSTEM_309 =
            "18f3934a0ecb5d465fb369914626254400799e9302813dfe630e763ee437a3bd";

    static final String BOOST_SYSTEM_309 = DIR_309 + "/deps/usr/lib64/libboost_system.so.1.66.0";

    static final String SHA256_BOOST_SYSTEM_309 =
            "ded43dd2101680377021387e8bf7c4c8db07912deac657c53b1e62337691b06c";

    /** The manifest's two 3.07.1 advisories, typed out here. */
    static final List<ToolAdvisory> ADVISORIES_3071 =
            List.of(
                    new ToolAdvisory(
                            "percolator.3-07-1-predates-i-spline-pep-regressor",
                            "Percolator 3.07.1 predates 3.08's change of the default PEP regressor"
                                    + " to I-splines, so its posterior error probabilities are"
                                    + " computed the older way."),
                    new ToolAdvisory(
                            "percolator.3-07-1-predates-pep-above-one-fix",
                            "Percolator 3.07.1 predates the fix for PEP values exceeding 1.0"
                                    + " (upstream issue #394, fixed in 3.08.1 and 3.09), so a PEP"
                                    + " above 1.0 can appear in its output."));

    static final HostPlatform HOST =
            new HostPlatform(HostOperatingSystem.LINUX, HostArchitecture.X86_64);

    private RealPercolator() {}

    /** Stages the 3.07.1 binary out of the mirrored zip and holds it to its SHA-256. */
    static Path stage3071(Path destination) throws IOException {
        Path archive = RealComet.repositoryRoot().resolve(RealComet.MIRROR).resolve(ZIP_3071);
        assertTrue(
                Files.isRegularFile(archive),
                archive
                        + " does not exist. The mirror is gitignored; refill it by fetching the"
                        + " artefact from the URL in manifests/tools.json and checking its"
                        + " SHA-256. This test fails rather than skips.");
        Files.createDirectories(RealComet.parentOf(destination));
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            ZipEntry entry = zip.getEntry("percolator");
            assertTrue(entry != null, archive + " holds no member named percolator");
            try (InputStream in = zip.getInputStream(entry)) {
                Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        Files.setPosixFilePermissions(destination, PosixFilePermissions.fromString("rwx------"));
        assertEquals(SHA256_3071, RealComet.sha256(destination), "the staged Percolator 3.07.1");
        return destination;
    }

    /** The 3.09 wrapper, every file behind it held to its SHA-256 first. */
    static Path wrapper309() throws IOException {
        fixture309(BINARY_309, SHA256_BINARY_309);
        fixture309(BOOST_FILESYSTEM_309, SHA256_BOOST_FILESYSTEM_309);
        fixture309(BOOST_SYSTEM_309, SHA256_BOOST_SYSTEM_309);
        return fixture309(WRAPPER_309, SHA256_WRAPPER_309);
    }

    private static Path fixture309(String relative, String sha256) throws IOException {
        Path file = RealComet.repositoryRoot().resolve(relative);
        assertTrue(
                Files.isRegularFile(file),
                "the Percolator 3.09 Linux fixture file "
                        + file
                        + " is missing. It is gitignored scratch, rebuilt by hand: extract"
                        + " usr/bin/percolator from upstream's percolator-v3-09-linux-x86_64.rpm"
                        + " and libboost_filesystem.so.1.66.0 and libboost_system.so.1.66.0 from"
                        + " CentOS 8.5's boost 1.66.0-10.el8 packages with"
                        + " scripts/feasibility/extract_rpm.py; the wrapper's header records the"
                        + " recipe. This test fails rather than skips.");
        assertEquals(sha256, RealComet.sha256(file), () -> file + " is not the measured bytes");
        return file;
    }

    /** Probes a build with the one capability probe, through the real process service. */
    static Set<ToolCapability> probe(String version, Path executable) throws IOException {
        return new PercolatorCapabilityProbe(
                        new ToolRunner(
                                new ProcessService(Clock.systemUTC()), Duration.ofSeconds(120)))
                .probe(ToolName.PERCOLATOR, ToolVersion.parse(version), HOST, executable);
    }

    /**
     * An installed offer whose capabilities are the probe's verdict, as the Tool Manager reports an
     * installed build ({@code OBSERVED_BY_EXECUTION}).
     */
    static ToolOffer offer(
            String version,
            ToolOrigin origin,
            Path executable,
            Set<ToolCapability> probed,
            List<ToolAdvisory> advisories) {
        List<DeclaredCapability> declared = new ArrayList<>();
        for (ToolCapability capability : ToolCapability.values()) {
            if (probed.contains(capability)) {
                declared.add(
                        new DeclaredCapability(
                                capability,
                                CapabilityEvidence.OBSERVED_BY_EXECUTION,
                                "probed by RealPercolator on this host"));
            }
        }
        return new ToolOffer(
                ToolName.PERCOLATOR,
                ToolVersion.parse(version),
                origin,
                ToolInstallState.INSTALLED,
                declared,
                advisories,
                Optional.empty(),
                Optional.of(executable),
                origin == ToolOrigin.MANAGED
                        ? OptionalLong.of(DOWNLOAD_3071)
                        : OptionalLong.empty());
    }
}
