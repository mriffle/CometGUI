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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import org.cometgui.domain.tools.DeclaredCapability;
import org.cometgui.domain.tools.ToolAdvisory;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.provenance.manifest.ProvenanceSchema;

/**
 * What a run's tool records warn about the Comet and the Percolator it used, in one place: every
 * sentence and every settings key, so that the tool record and the settings say the same thing in
 * the same words and the Provenance section can tell which source each came from.
 *
 * <p>Two kinds of warning, both facts the Tool Manager's offer already holds -- nothing is probed
 * again at run time:
 *
 * <ul>
 *   <li><strong>Advisories</strong> active for the build ({@link ToolOffer#advisories()}): the
 *       manifest's, and the one a registered local binary carries. An advisory is shown as its
 *       text, exactly as written; it already names the version it is about.
 *   <li><strong>Manifest/probe discrepancies</strong> ({@code R-TOOL-07}): each capability the
 *       artefact manifest claims that the probe run when the build was installed did not find
 *       ({@link ToolOffer#unconfirmedClaims()}). The probe wins, and the rule's other half is that
 *       the disagreement is recorded as a warning in provenance; {@link #discrepancy} is its one
 *       sentence.
 * </ul>
 *
 * <p>The settings keys, each of the shape {@link ProvenanceSchema#SETTINGS_KEY_PATTERN}:
 *
 * <ul>
 *   <li>{@code comet.advisory.<id>} -- each Comet advisory's text, as {@link
 *       PercolatorProvenance#ADVISORY_PREFIX} records Percolator's;
 *   <li>{@code comet.probe-discrepancy.<nn>} and {@code percolator.probe-discrepancy.<nn>} -- each
 *       discrepancy's sentence, {@code nn} from {@code 01} in manifest order. Numbered, because a
 *       capability's identifier is upper case and no settings key may hold it.
 * </ul>
 *
 * <p>A build with no advisory and no unconfirmed claim -- every Linux build the shipped manifest
 * installs today -- has none of these keys and no such warning.
 */
public final class ToolWarnings {

    /** The prefix of each Comet advisory: {@code comet.advisory.<id>}. */
    public static final String COMET_ADVISORY_PREFIX = "comet.advisory.";

    /**
     * The prefix of each Comet manifest/probe discrepancy: {@code comet.probe-discrepancy.<nn>}.
     */
    public static final String COMET_DISCREPANCY_PREFIX = "comet.probe-discrepancy.";

    /**
     * The prefix of each Percolator manifest/probe discrepancy: {@code
     * percolator.probe-discrepancy.<nn>}.
     */
    public static final String PERCOLATOR_DISCREPANCY_PREFIX = "percolator.probe-discrepancy.";

    /** How a sentence names Comet. */
    static final String COMET = "Comet";

    /** How a sentence names Percolator. */
    static final String PERCOLATOR = "Percolator";

    private ToolWarnings() {}

    /**
     * The one sentence a manifest/probe discrepancy is recorded as.
     *
     * @param tool how the tool is named: {@link #COMET} or {@link #PERCOLATOR}
     * @param version the build's version
     * @param claim the manifest's claim the probe did not confirm
     * @return the sentence
     */
    static String discrepancy(String tool, ToolVersion version, DeclaredCapability claim) {
        String capability = claim.capability().id();
        return tool
                + " "
                + version.text()
                + ": the artefact manifest claims "
                + capability
                + " ("
                + claim.evidence().id()
                + " evidence), but the probe run when this build was installed did not find it,"
                + " so CometGUI treats the build as lacking "
                + capability
                + " (R-TOOL-07: where manifest and probe disagree, the probe wins).";
    }

    /**
     * The warnings the run's Comet tool record carries: each advisory's text, then each
     * discrepancy's sentence.
     *
     * @param comet the selected Comet
     * @return the warnings, immutable; empty when there are none
     */
    static List<String> comet(CometSelection comet) {
        Objects.requireNonNull(comet, "comet");
        return warnings(COMET, comet.release(), comet.advisories(), comet.unconfirmedClaims());
    }

    /**
     * The warnings the run's Percolator tool record carries, before {@code R-PERC-08}'s: each
     * advisory's text, then each discrepancy's sentence.
     *
     * @param offer the selected Percolator's offer
     * @return the warnings, immutable; empty when there are none
     */
    static List<String> percolator(ToolOffer offer) {
        Objects.requireNonNull(offer, "offer");
        return warnings(PERCOLATOR, offer.version(), offer.advisories(), offer.unconfirmedClaims());
    }

    /**
     * The Comet settings: {@code comet.advisory.<id>} and {@code comet.probe-discrepancy.<nn>}.
     *
     * @param comet the selected Comet
     * @return key to value, sorted; empty when there is nothing to warn about
     */
    static Map<String, String> cometSettings(CometSelection comet) {
        Objects.requireNonNull(comet, "comet");
        Map<String, String> settings = new TreeMap<>();
        for (ToolAdvisory advisory : comet.advisories()) {
            settings.put(COMET_ADVISORY_PREFIX + advisory.id(), advisory.text());
        }
        settings.putAll(
                numbered(
                        COMET_DISCREPANCY_PREFIX,
                        discrepancies(COMET, comet.release(), comet.unconfirmedClaims())));
        return settings;
    }

    /**
     * The Percolator discrepancy settings, {@code percolator.probe-discrepancy.<nn>}; its
     * advisories are {@link PercolatorProvenance}'s.
     *
     * @param offer the selected Percolator's offer
     * @return key to value, sorted; empty when the probe confirmed every claim
     */
    static Map<String, String> percolatorSettings(ToolOffer offer) {
        Objects.requireNonNull(offer, "offer");
        return numbered(
                PERCOLATOR_DISCREPANCY_PREFIX,
                discrepancies(PERCOLATOR, offer.version(), offer.unconfirmedClaims()));
    }

    private static List<String> warnings(
            String tool,
            ToolVersion version,
            List<ToolAdvisory> advisories,
            List<DeclaredCapability> claims) {
        List<String> warnings = new ArrayList<>();
        for (ToolAdvisory advisory : advisories) {
            warnings.add(advisory.text());
        }
        warnings.addAll(discrepancies(tool, version, claims));
        return List.copyOf(warnings);
    }

    private static List<String> discrepancies(
            String tool, ToolVersion version, List<DeclaredCapability> claims) {
        List<String> sentences = new ArrayList<>();
        for (DeclaredCapability claim : claims) {
            sentences.add(discrepancy(tool, version, claim));
        }
        return sentences;
    }

    private static Map<String, String> numbered(String prefix, List<String> values) {
        Map<String, String> settings = new TreeMap<>();
        for (int index = 0; index < values.size(); index++) {
            settings.put(prefix + String.format(Locale.ROOT, "%02d", index + 1), values.get(index));
        }
        return settings;
    }
}
