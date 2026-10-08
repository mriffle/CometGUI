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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
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
import org.cometgui.domain.tools.ToolRegistrationException;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.percolator.PercolatorSettings;
import org.cometgui.params.percolator.resolution.DownstreamStage;
import org.cometgui.params.percolator.resolution.PercolatorResolver;
import org.cometgui.ui.viewmodel.params.PercolatorRequest;
import org.cometgui.ui.viewmodel.percolator.PercolatorOffers;
import org.cometgui.ui.viewmodel.percolator.PercolatorPort;

/**
 * Percolator offers a test writes out, and a Tool Manager port answering with them.
 *
 * <p>These are inputs, never expectations: what the interface makes of them is typed out in the
 * test that asserts it. The capability sets mirror what Phase 09 unit 1's probe established on this
 * host -- every eleven for 3.07.1, the nine non-XML ones for 3.09 -- and the advisories are the
 * shipped manifest's own sentences ({@link ToolOffers}).
 */
public final class Percolators {

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

    /** The same without the two XML capabilities: what 3.09 was observed to have. */
    public static final Set<ToolCapability> NO_XML = without(ALL, ToolCapability.XML_OUTPUT);

    private Percolators() {}

    private static Set<ToolCapability> without(Set<ToolCapability> from, ToolCapability xml) {
        EnumSet<ToolCapability> set = EnumSet.copyOf(from);
        set.remove(xml);
        set.remove(ToolCapability.XML_DECOY_OUTPUT);
        return set;
    }

    /**
     * An installed build whose capabilities were observed by running it.
     *
     * @param version the version
     * @param origin managed or local
     * @param path where it is installed
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
                origin == ToolOrigin.MANAGED ? OptionalLong.of(1000L) : OptionalLong.empty());
    }

    /**
     * A managed build that is not installed, claiming capabilities with some evidence.
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
                OptionalLong.of(1000L));
    }

    /**
     * Percolator 3.07.1, managed, installed at a path, with every capability and the manifest's two
     * advisories.
     *
     * @param path where it is installed
     * @return the offer
     */
    public static ToolOffer managed3071(Path path) {
        return installed(
                "3.07.1",
                ToolOrigin.MANAGED,
                path,
                ALL,
                List.of(
                        new ToolAdvisory(
                                "percolator.3-07-1-predates-i-spline-pep-regressor",
                                ToolOffers.I_SPLINE_ADVISORY),
                        new ToolAdvisory(
                                "percolator.3-07-1-predates-pep-above-one-fix",
                                ToolOffers.PEP_ABOVE_ONE_ADVISORY)));
    }

    /**
     * Percolator 3.09, registered as a local binary at a path, with the nine non-XML capabilities.
     *
     * @param path where it is
     * @return the offer
     */
    public static ToolOffer local309(Path path) {
        return installed("3.09", ToolOrigin.LOCAL, path, NO_XML, List.of());
    }

    /**
     * A Percolator half that can run: 3.07.1 installed, the default with no stage enabled, the
     * default settings.
     *
     * @return the request
     */
    public static PercolatorRequest ready() {
        ToolOffer offer = managed3071(Path.of("percolator-3.07.1", "percolator").toAbsolutePath());
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
        for (ToolCapability capability : capabilities) {
            list.add(new DeclaredCapability(capability, evidence, "written out by a test"));
        }
        return list;
    }

    /**
     * A Percolator port answering with offers a test writes out, registering from a script; calls
     * are counted, and which thread made them is the caller's to check with its own queues.
     */
    public static final class Port implements PercolatorPort {

        private PercolatorOffers answer;

        private final Deque<Object> registrations = new ArrayDeque<>();

        private final List<Path> registered = new ArrayList<>();

        private int reads;

        /**
         * A port answering with these offers.
         *
         * @param offers the offers
         */
        public Port(ToolOffer... offers) {
            this.answer = PercolatorOffers.of(List.of(offers));
        }

        /**
         * Changes what the next read answers.
         *
         * @param next the answer
         */
        public void answer(PercolatorOffers next) {
            this.answer = Objects.requireNonNull(next, "next");
        }

        /**
         * Makes the next registration return an offer -- and adds it to the answer, as the Tool
         * Manager does.
         *
         * @param offer the registered build
         */
        public void registers(ToolOffer offer) {
            registrations.add(offer);
        }

        /**
         * Makes the next registration refuse.
         *
         * @param refusal the refusal
         */
        public void refuses(ToolRegistrationException refusal) {
            registrations.add(refusal);
        }

        /**
         * Makes the next registration throw an unexpected exception.
         *
         * @param failure the failure
         */
        public void fails(IllegalStateException failure) {
            registrations.add(failure);
        }

        /**
         * How many times the offers were read.
         *
         * @return the count
         */
        public int reads() {
            return reads;
        }

        /**
         * The files registrations were asked for, oldest first.
         *
         * @return the paths
         */
        public List<Path> registered() {
            return List.copyOf(registered);
        }

        @Override
        public PercolatorOffers offers() {
            reads++;
            return answer;
        }

        @Override
        public ToolOffer register(Path executable) throws ToolRegistrationException {
            registered.add(executable);
            Object next = registrations.pollFirst();
            if (next instanceof ToolRegistrationException refusal) {
                throw refusal;
            }
            if (next instanceof IllegalStateException failure) {
                throw failure;
            }
            if (next instanceof ToolOffer offer) {
                List<ToolOffer> grown = new ArrayList<>(answer.offers());
                grown.add(offer);
                answer = PercolatorOffers.of(grown);
                return offer;
            }
            throw new IllegalStateException("no registration was scripted");
        }
    }
}
