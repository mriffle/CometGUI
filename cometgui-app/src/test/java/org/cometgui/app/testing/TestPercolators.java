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

package org.cometgui.app.testing;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import org.cometgui.domain.tools.CapabilityEvidence;
import org.cometgui.domain.tools.DeclaredCapability;
import org.cometgui.domain.tools.ToolAdvisory;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.domain.tools.ToolInstallState;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolOrigin;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.percolator.PercolatorSettings;
import org.cometgui.params.percolator.resolution.DownstreamStage;
import org.cometgui.params.percolator.resolution.PercolatorResolver;
import org.cometgui.ui.viewmodel.params.PercolatorRequest;

/**
 * Percolator offers a GUI test writes out where the test is about the interface's logic rather than
 * a binary's behaviour -- inputs, never expectations. The capability sets mirror what Phase 09 unit
 * 1's probe established on this host: all eleven for 3.07.1, the nine non-XML ones for 3.09. A test
 * that runs Percolator uses {@link RealPercolators} instead, whose capabilities are probed.
 */
public final class TestPercolators {

    /** Every capability the probe establishes for a Percolator that writes XML. */
    public static final Set<ToolCapability> ALL =
            EnumSet.of(
                    ToolCapability.XML_OUTPUT,
                    ToolCapability.XML_DECOY_OUTPUT,
                    ToolCapability.PSM_TSV_OUTPUT,
                    ToolCapability.PEPTIDE_TSV_OUTPUT,
                    ToolCapability.DECOY_OUTPUT,
                    ToolCapability.WEIGHTS_OUTPUT,
                    ToolCapability.SEED_OPTION,
                    ToolCapability.THREAD_OPTION,
                    ToolCapability.TEST_FDR_OPTION,
                    ToolCapability.TRAIN_FDR_OPTION,
                    ToolCapability.MAX_ITERATIONS_OPTION);

    private TestPercolators() {}

    /**
     * {@link #ALL} without some capabilities.
     *
     * @param missing the capabilities to leave out
     * @return the set
     */
    public static Set<ToolCapability> allBut(ToolCapability... missing) {
        EnumSet<ToolCapability> set = EnumSet.copyOf(ALL);
        set.removeAll(List.of(missing));
        return set;
    }

    /**
     * An installed build whose capabilities were observed by running it.
     *
     * @param version the version
     * @param origin managed or local
     * @param path where it is installed, absolute
     * @param observed what the probe observed
     * @param advisories its advisories
     * @return the offer
     */
    public static ToolOffer installed(
            String version,
            ToolOrigin origin,
            Path path,
            Set<ToolCapability> observed,
            List<ToolAdvisory> advisories) {
        return new ToolOffer(
                ToolName.PERCOLATOR,
                ToolVersion.parse(version),
                origin,
                ToolInstallState.INSTALLED,
                declared(observed, CapabilityEvidence.OBSERVED_BY_EXECUTION),
                advisories,
                Optional.empty(),
                Optional.of(path),
                origin == ToolOrigin.MANAGED ? OptionalLong.of(2_798_963L) : OptionalLong.empty());
    }

    /**
     * A managed build that is not installed, its manifest claiming capabilities with some evidence.
     *
     * @param version the version
     * @param claimed what its manifest row claims
     * @param evidence the evidence of every claim
     * @return the offer
     */
    public static ToolOffer notInstalled(
            String version, Set<ToolCapability> claimed, CapabilityEvidence evidence) {
        return new ToolOffer(
                ToolName.PERCOLATOR,
                ToolVersion.parse(version),
                ToolOrigin.MANAGED,
                ToolInstallState.NOT_INSTALLED,
                declared(claimed, evidence),
                List.of(),
                Optional.empty(),
                Optional.empty(),
                OptionalLong.of(2_798_963L));
    }

    /**
     * A Percolator half that can run with one installed build, its default with no stage enabled,
     * the default settings.
     *
     * @param offer the installed build
     * @return the request
     */
    public static PercolatorRequest ready(ToolOffer offer) {
        return new PercolatorRequest(
                Optional.of(offer),
                true,
                Optional.of(
                        PercolatorResolver.resolve(
                                List.of(offer), EnumSet.noneOf(DownstreamStage.class))),
                Optional.of(PercolatorSettings.defaults()),
                List.of(),
                false);
    }

    private static List<DeclaredCapability> declared(
            Set<ToolCapability> capabilities, CapabilityEvidence evidence) {
        List<DeclaredCapability> list = new ArrayList<>();
        for (ToolCapability capability : ToolCapability.values()) {
            if (capabilities.contains(capability)) {
                list.add(new DeclaredCapability(capability, evidence, "written out by a test"));
            }
        }
        return list;
    }
}
