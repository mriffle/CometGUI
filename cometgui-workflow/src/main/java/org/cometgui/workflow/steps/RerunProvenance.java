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
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.cometgui.domain.project.ProjectLayout;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.provenance.manifest.ProvenanceSchema;
import org.cometgui.workflow.state.EngineStep;

/**
 * The provenance settings a <em>derived</em> run records about the run it reuses (design decision
 * P9-11): every key in one place, beside {@link PercolatorProvenance}'s {@code percolator.*} keys,
 * each of the shape {@link ProvenanceSchema#SETTINGS_KEY_PATTERN}.
 *
 * <p><strong>Comet is recorded by reference, never as executed.</strong> A derived run launches no
 * Comet, so its {@code provenance.json} holds no Comet tool record and none of the {@code comet.*}
 * settings a search records. What it holds instead is the reference: which run's search it reused,
 * that run's manifest as it was then, the Comet release and executable that run's search used, the
 * parameter file it searched with and the merged PIN it produced -- each a value the source run
 * recorded and this run re-verified, so a reader can follow the reference to the source's own
 * records and check it.
 *
 * <p>Every one is known before anything runs, so they are the run's settings from the start and are
 * in {@code provenance.json} however the attempt ends. The keys and their values are listed in
 * {@code docs/reference/provenance_format.rst}.
 */
public final class RerunProvenance {

    /** The source run's identifier. */
    public static final String SOURCE_RUN_ID = "rerun.source-run-id";

    /** The source run's directory, relative to the project: {@code runs/<name>}. */
    public static final String SOURCE_RUN_DIRECTORY = "rerun.source-run-directory";

    /** The SHA-256 of the source's {@code provenance/provenance.json} when the run was derived. */
    public static final String SOURCE_PROVENANCE_SHA256 = "rerun.source-provenance-sha256";

    /** The Comet release the source's search ran. */
    public static final String SOURCE_COMET_RELEASE = "rerun.source-comet-release";

    /** The SHA-256 of the Comet executable the source's search ran. */
    public static final String SOURCE_COMET_SHA256 = "rerun.source-comet-binary-sha256";

    /** The SHA-256 of the source's archived {@code comet.params}, equal to this run's copy. */
    public static final String SOURCE_PARAMS_SHA256 = "rerun.source-comet-params-sha256";

    /** The SHA-256 of the merged PIN the source recorded, equal to this run's copy. */
    public static final String MERGED_PIN_SHA256 = "rerun.merged-pin-sha256";

    /** The Percolator version the source ran, or {@code none} for a Comet-only source. */
    public static final String SOURCE_PERCOLATOR_VERSION = "rerun.source-percolator-version";

    /** The steps whose results come from the source, not executed here; space-separated. */
    public static final String REUSED_STEPS = "rerun.reused-steps";

    /** The value of {@link #SOURCE_PERCOLATOR_VERSION} for a source that ran no Percolator. */
    public static final String NONE = "none";

    private RerunProvenance() {}

    /**
     * The settings a derived run records about its source.
     *
     * @param source the checked source run
     * @param reused the steps whose results come from it
     * @return key to value, sorted
     */
    static Map<String, String> settings(RerunSource source, Set<EngineStep> reused) {
        Map<String, String> settings = new TreeMap<>();
        settings.put(SOURCE_RUN_ID, source.identity().runId().value());
        settings.put(
                SOURCE_RUN_DIRECTORY,
                ProjectLayout.RUNS_DIRECTORY_NAME
                        + "/"
                        + RunLayout.directoryName(
                                source.identity().created(), source.identity().runId()));
        settings.put(SOURCE_PROVENANCE_SHA256, source.manifestFile().hashes().sha256());
        settings.put(SOURCE_COMET_RELEASE, source.identity().cometRelease());
        settings.put(SOURCE_COMET_SHA256, source.cometSha256());
        settings.put(SOURCE_PARAMS_SHA256, source.parameters().sha256());
        settings.put(MERGED_PIN_SHA256, source.mergedPin().hashes().sha256());
        settings.put(
                SOURCE_PERCOLATOR_VERSION,
                source.manifest().settings().getOrDefault(PercolatorProvenance.VERSION, NONE));
        List<String> ids = new ArrayList<>();
        for (EngineStep step : reused) {
            ids.add(step.id());
        }
        settings.put(REUSED_STEPS, String.join(" ", ids));
        return settings;
    }
}
