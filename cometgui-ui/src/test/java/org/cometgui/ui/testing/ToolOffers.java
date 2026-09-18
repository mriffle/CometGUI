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

package org.cometgui.ui.testing;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import org.cometgui.domain.tools.CapabilityEvidence;
import org.cometgui.domain.tools.DeclaredCapability;
import org.cometgui.domain.tools.LoaderDiagnostic;
import org.cometgui.domain.tools.ProbeFailureKind;
import org.cometgui.domain.tools.ToolAdvisory;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.domain.tools.ToolInstallState;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolOrigin;
import org.cometgui.domain.tools.ToolVersion;

/**
 * Offers a test writes out, for the states this machine cannot produce.
 *
 * <p>These are inputs, never expectations: what the interface makes of them is typed out in the
 * test that asserts it. The numbers and sentences are the shipped manifest's real ones where a real
 * one exists, so that a reader can tell a fixture from an invention.
 */
public final class ToolOffers {

    /** Percolator 3.07.1's real download on Linux: the portable zip plus the XSD companion. */
    public static final long PERCOLATOR_3_07_1_LINUX_BYTES = 2_798_963L;

    /** The first of Percolator 3.07.1's two advisories, from {@code manifests/tools.json}. */
    public static final String I_SPLINE_ADVISORY =
            "Percolator 3.07.1 predates 3.08's change of the default PEP regressor to I-splines, so"
                    + " its posterior error probabilities are computed the older way.";

    /** The second of Percolator 3.07.1's two advisories, from {@code manifests/tools.json}. */
    public static final String PEP_ABOVE_ONE_ADVISORY =
            "Percolator 3.07.1 predates the fix for PEP values exceeding 1.0 (upstream issue #394,"
                    + " fixed in 3.08.1 and 3.09), so a PEP above 1.0 can appear in its output.";

    private ToolOffers() {}

    /**
     * Percolator 3.07.1, available to install and not installed.
     *
     * @return the offer
     */
    public static ToolOffer percolatorAvailable() {
        return new ToolOffer(
                ToolName.PERCOLATOR,
                ToolVersion.parse("3.07.1"),
                ToolOrigin.MANAGED,
                ToolInstallState.NOT_INSTALLED,
                List.of(
                        new DeclaredCapability(
                                ToolCapability.XML_OUTPUT,
                                CapabilityEvidence.OBSERVED_BY_EXECUTION,
                                "run on linux-x86-64 by phase 00"),
                        new DeclaredCapability(
                                ToolCapability.XML_DECOY_OUTPUT,
                                CapabilityEvidence.OBSERVED_BY_EXECUTION,
                                "run on linux-x86-64 by phase 00")),
                List.of(
                        new ToolAdvisory(
                                "percolator.3-07-1-predates-i-spline-pep-regressor",
                                I_SPLINE_ADVISORY),
                        new ToolAdvisory(
                                "percolator.3-07-1-predates-pep-above-one-fix",
                                PEP_ABOVE_ONE_ADVISORY)),
                Optional.empty(),
                Optional.empty(),
                OptionalLong.of(PERCOLATOR_3_07_1_LINUX_BYTES));
    }

    /**
     * Comet 2026.02.2, installed and probed, at a path.
     *
     * @param installedAt where it lives, which must be absolute
     * @return the offer
     */
    public static ToolOffer cometInstalled(Path installedAt) {
        return new ToolOffer(
                ToolName.COMET,
                ToolVersion.parse("2026.02.2"),
                ToolOrigin.MANAGED,
                ToolInstallState.INSTALLED,
                List.of(
                        new DeclaredCapability(
                                ToolCapability.PIN_OUTPUT,
                                CapabilityEvidence.OBSERVED_BY_EXECUTION,
                                "probed by execution when the build was installed")),
                List.of(),
                Optional.empty(),
                Optional.of(installedAt),
                OptionalLong.of(7_014_400L));
    }

    /**
     * Percolator 3.09, which upstream publishes no Linux artefact of.
     *
     * @return the offer
     */
    public static ToolOffer percolatorUnavailableHere() {
        return new ToolOffer(
                ToolName.PERCOLATOR,
                ToolVersion.parse("3.09"),
                ToolOrigin.MANAGED,
                ToolInstallState.UNAVAILABLE_ON_THIS_PLATFORM,
                List.of(),
                List.of(),
                Optional.empty(),
                Optional.empty(),
                OptionalLong.empty());
    }

    /**
     * Percolator 3.06.5, published for this platform but beyond what this host provides.
     *
     * @return the offer, carrying the {@code R-PLAT-03} diagnostic
     */
    public static ToolOffer percolatorBeyondThisHost() {
        return new ToolOffer(
                ToolName.PERCOLATOR,
                ToolVersion.parse("3.06.5"),
                ToolOrigin.MANAGED,
                ToolInstallState.HOST_REQUIREMENTS_NOT_MET,
                List.of(),
                List.of(),
                Optional.of(hostRequirementDiagnostic()),
                Optional.empty(),
                OptionalLong.of(2_799_981L));
    }

    /**
     * The diagnostic {@link #percolatorBeyondThisHost()} carries.
     *
     * @return the diagnostic
     */
    public static LoaderDiagnostic hostRequirementDiagnostic() {
        return new LoaderDiagnostic(
                ProbeFailureKind.MISSING_SYMBOL_VERSION,
                "libstdc++.so.6",
                Optional.of("GLIBCXX_3.4.29"),
                Optional.of("GLIBCXX_3.4.28"),
                List.of("percolator 3.07.1", "register a local binary"));
    }

    /**
     * PDV 2.7.0, whose last install attempt did not succeed.
     *
     * @return the offer
     */
    public static ToolOffer pdvFailed() {
        return new ToolOffer(
                ToolName.PDV,
                ToolVersion.parse("2.7.0"),
                ToolOrigin.MANAGED,
                ToolInstallState.FAILED,
                List.of(),
                List.of(),
                Optional.empty(),
                Optional.empty(),
                OptionalLong.of(103_407_417L));
    }

    /**
     * A Percolator binary the user pointed CometGUI at.
     *
     * @param executable where it lives, which must be absolute
     * @return the offer
     */
    public static ToolOffer localPercolator(Path executable) {
        return new ToolOffer(
                ToolName.PERCOLATOR,
                ToolVersion.parse("3.05"),
                ToolOrigin.LOCAL,
                ToolInstallState.INSTALLED,
                List.of(
                        new DeclaredCapability(
                                ToolCapability.PSM_TSV_OUTPUT,
                                CapabilityEvidence.UNVERIFIED,
                                "not probed: absent positive evidence a capability is absent")),
                List.of(),
                Optional.empty(),
                Optional.of(executable),
                OptionalLong.empty());
    }

    /**
     * Two builds of one release, as Apple silicon offers Comet 2026.02.2.
     *
     * @return the native build and the translated one, in that order, identical in every component
     *     a view can render
     */
    public static List<ToolOffer> oneReleaseOfferedTwice() {
        ToolOffer build =
                new ToolOffer(
                        ToolName.COMET,
                        ToolVersion.parse("2026.02.2"),
                        ToolOrigin.MANAGED,
                        ToolInstallState.NOT_INSTALLED,
                        List.of(),
                        List.of(),
                        Optional.empty(),
                        Optional.empty(),
                        OptionalLong.of(3_998_328L));
        return List.of(build, build);
    }
}
