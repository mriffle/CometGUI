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

package org.cometgui.params.percolator.resolution;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import org.cometgui.domain.tools.CapabilityEvidence;
import org.cometgui.domain.tools.DeclaredCapability;
import org.cometgui.domain.tools.ToolAdvisory;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.domain.tools.ToolInstallState;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolOrigin;
import org.cometgui.domain.tools.ToolVersion;

/**
 * Percolator offers shaped like the Tool Manager's real rows (manifests/tools.json and
 * ManagedToolManager.offerFor, 2026-10-07): an installed build carries its probed set as
 * observed-by-execution; a build that is not installed carries the manifest's two XML claims with
 * their evidence; a registered local binary is INSTALLED and LOCAL with no download size.
 */
final class Offers {

    /** The eleven capabilities unit 1's probe establishes on 3.07.1 and 3.06.5. */
    static final List<ToolCapability> ALL_ELEVEN =
            List.of(
                    ToolCapability.XML_OUTPUT,
                    ToolCapability.XML_DECOY_OUTPUT,
                    ToolCapability.PSM_TSV_OUTPUT,
                    ToolCapability.PEPTIDE_TSV_OUTPUT,
                    ToolCapability.DECOY_OUTPUT,
                    ToolCapability.WEIGHTS_OUTPUT,
                    ToolCapability.THREAD_OPTION,
                    ToolCapability.SEED_OPTION,
                    ToolCapability.TEST_FDR_OPTION,
                    ToolCapability.TRAIN_FDR_OPTION,
                    ToolCapability.MAX_ITERATIONS_OPTION);

    /** The nine non-XML capabilities unit 1's probe establishes on 3.09. */
    static final List<ToolCapability> NINE_WITHOUT_XML = ALL_ELEVEN.subList(2, 11);

    static final ToolAdvisory ISPLINE =
            new ToolAdvisory(
                    "percolator.3-07-1-predates-i-spline-pep-regressor",
                    "Percolator 3.07.1 predates 3.08's change of the default PEP regressor to"
                            + " I-splines, so its posterior error probabilities are computed the"
                            + " older way.");

    static final ToolAdvisory PEP_ABOVE_ONE =
            new ToolAdvisory(
                    "percolator.3-07-1-predates-pep-above-one-fix",
                    "Percolator 3.07.1 predates the fix for PEP values exceeding 1.0 (upstream"
                            + " issue #394, fixed in 3.08.1 and 3.09), so a PEP above 1.0 can"
                            + " appear in its output.");

    private Offers() {}

    static List<DeclaredCapability> observed(List<ToolCapability> capabilities) {
        return declared(capabilities, CapabilityEvidence.OBSERVED_BY_EXECUTION);
    }

    static List<DeclaredCapability> inferred(List<ToolCapability> capabilities) {
        return declared(capabilities, CapabilityEvidence.INFERRED_FROM_ARTEFACT_BYTES);
    }

    static List<DeclaredCapability> declared(
            List<ToolCapability> capabilities, CapabilityEvidence evidence) {
        List<DeclaredCapability> declared = new ArrayList<>();
        for (ToolCapability capability : capabilities) {
            declared.add(new DeclaredCapability(capability, evidence, "test fixture"));
        }
        return declared;
    }

    static ToolOffer managed(
            String version,
            ToolInstallState state,
            List<DeclaredCapability> capabilities,
            List<ToolAdvisory> advisories) {
        return managed(version, state, capabilities, advisories, 2_798_963L);
    }

    static ToolOffer managed(
            String version,
            ToolInstallState state,
            List<DeclaredCapability> capabilities,
            List<ToolAdvisory> advisories,
            long downloadSize) {
        boolean fetchable = state != ToolInstallState.UNAVAILABLE_ON_THIS_PLATFORM;
        return new ToolOffer(
                ToolName.PERCOLATOR,
                ToolVersion.parse(version),
                ToolOrigin.MANAGED,
                state,
                capabilities,
                advisories,
                Optional.empty(),
                state == ToolInstallState.INSTALLED
                        ? Optional.of(Path.of("/tools/percolator/" + version + "/bin/percolator"))
                        : Optional.empty(),
                fetchable ? OptionalLong.of(downloadSize) : OptionalLong.empty());
    }

    static ToolOffer local(String version, List<DeclaredCapability> capabilities) {
        return local(version, ToolInstallState.INSTALLED, capabilities);
    }

    static ToolOffer local(
            String version, ToolInstallState state, List<DeclaredCapability> capabilities) {
        return new ToolOffer(
                ToolName.PERCOLATOR,
                ToolVersion.parse(version),
                ToolOrigin.LOCAL,
                state,
                capabilities,
                List.of(),
                Optional.empty(),
                state == ToolInstallState.INSTALLED
                        ? Optional.of(Path.of("/home/user/percolator-" + version))
                        : Optional.empty(),
                OptionalLong.empty());
    }

    /** 3.07.1 on Linux, installed: the probe's eleven capabilities, and its two advisories. */
    static ToolOffer linux3071Installed() {
        return managed(
                "3.07.1",
                ToolInstallState.INSTALLED,
                observed(ALL_ELEVEN),
                List.of(ISPLINE, PEP_ABOVE_ONE));
    }

    /** 3.09, registered local (the rpm build with Boost): nine capabilities, no XML. */
    static ToolOffer local309() {
        return local("3.09", observed(NINE_WITHOUT_XML));
    }

    /** 3.06.5 on Linux, not installed: the manifest's two XML claims, observed on Linux. */
    static ToolOffer linux3065NotInstalled() {
        return managed(
                "3.06.5",
                ToolInstallState.NOT_INSTALLED,
                observed(List.of(ToolCapability.XML_OUTPUT, ToolCapability.XML_DECOY_OUTPUT)),
                List.of());
    }

    /** 3.07.1 on macOS, not installed: XML inferred from the artefact's bytes. */
    static ToolOffer macos3071NotInstalled() {
        return managed(
                "3.07.1",
                ToolInstallState.NOT_INSTALLED,
                inferred(List.of(ToolCapability.XML_OUTPUT, ToolCapability.XML_DECOY_OUTPUT)),
                List.of(ISPLINE, PEP_ABOVE_ONE));
    }

    /** 3.06.5 on macOS, not installed: XML inferred from the artefact's bytes. */
    static ToolOffer macos3065NotInstalled() {
        return managed(
                "3.06.5",
                ToolInstallState.NOT_INSTALLED,
                inferred(List.of(ToolCapability.XML_OUTPUT, ToolCapability.XML_DECOY_OUTPUT)),
                List.of());
    }

    /** 3.09 on macOS, not installed: the manifest claims nothing for it. */
    static ToolOffer macos309NotInstalled() {
        return managed("3.09", ToolInstallState.NOT_INSTALLED, List.of(), List.of());
    }
}
