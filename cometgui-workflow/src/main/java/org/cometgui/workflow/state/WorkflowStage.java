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

package org.cometgui.workflow.state;

import java.util.List;
import java.util.Map;
import org.cometgui.domain.run.StageTag;

/**
 * A user-facing stage of the Run screen's stage stepper.
 *
 * <p>These are the stages the specification's <em>Information Architecture</em> draws, and the
 * edges below are that diagram:
 *
 * <pre>
 *     Inputs -&gt; Validate -&gt; Comet -&gt; Percolator -&gt; Results
 *
 *     Results -&gt; PDV
 *     Results -&gt; Limelight XML -&gt; Limelight Upload
 * </pre>
 *
 * <p>Five core stages, then three optional downstream ones hanging off Results. The core path is
 * what every run does; a downstream stage runs only if the user asks for it, and a run that never
 * opens PDV is a complete run.
 *
 * <h2>Scope: this is not the canonical workflow DAG</h2>
 *
 * <p><strong>The specification's <em>Canonical workflow DAG</em> has seventeen finer-grained engine
 * steps -- resolve and probe each tool, serialise the parameter file, hash inputs, run Comet once
 * per spectrum file, merge the PIN files, and so on. Those belong to phase 08 and to the workflow
 * engine, not here.</strong> These eight are the stepper's stages: what a scientist watching a run
 * sees. The two models must not be confused, and neither is derived from the other by this class --
 * the engine's step model will map many of its steps onto one of these stages when phase 08 is
 * written, and that mapping is phase 08's to declare.
 *
 * <h2>Identifiers</h2>
 *
 * <p>{@link #id()} is stable and lower-case-hyphenated: it is what a console message is tagged
 * with, what a provenance record names, and what a test identifier is built from. It is
 * deliberately not {@link #name()} and not {@link #displayName()} -- the first would leak Java
 * naming into a file format and the second would change if the wording of the UI changed.
 *
 * <p>Implementing {@link StageTag} is what lets a console message in {@code
 * org.cometgui.domain.log} carry a stage without the domain depending on this module. The
 * dependency points from the workflow to the domain and never the other way.
 */
public enum WorkflowStage implements StageTag {

    /** Choosing the spectrum files, the FASTA and the run's other inputs. */
    INPUTS("inputs", "Inputs"),

    /** Checking those inputs and the configuration before anything is launched. */
    VALIDATE("validate", "Validate"),

    /** The Comet database search. */
    COMET("comet", "Comet"),

    /** Percolator's rescoring of Comet's results. */
    PERCOLATOR("percolator", "Percolator"),

    /** The parsed, filtered results: PSMs, peptides and learned feature weights. */
    RESULTS("results", "Results"),

    /** Optional: viewing annotated spectra in PDV. */
    PDV("pdv", "PDV"),

    /** Optional: converting the results into Limelight XML. */
    LIMELIGHT_XML("limelight-xml", "Limelight XML"),

    /** Optional: uploading that Limelight XML to a Limelight server. */
    LIMELIGHT_UPLOAD("limelight-upload", "Limelight Upload");

    /**
     * The core path, in the order the stepper draws it.
     *
     * <p>Declared here rather than derived from {@link #isCore()} so that the <em>order</em> is
     * stated once and asserted, instead of resting on the accident of declaration order.
     */
    private static final List<WorkflowStage> CORE_PATH =
            List.of(INPUTS, VALIDATE, COMET, PERCOLATOR, RESULTS);

    /**
     * The optional branches hanging off {@link #RESULTS}, each in the order it is drawn.
     *
     * <p>Two branches, because that is what the diagram has: PDV on its own, and Limelight XML
     * followed by the upload that consumes it.
     */
    private static final List<List<WorkflowStage>> DOWNSTREAM_BRANCHES =
            List.of(List.of(PDV), List.of(LIMELIGHT_XML, LIMELIGHT_UPLOAD));

    /**
     * The diagram's edges, as a stage to its predecessors.
     *
     * <p>A static field rather than a constructor argument because an enum constant cannot
     * reference another constant of its own type from its constructor. The map is built after every
     * constant exists, which is exactly when the edges can be written down.
     */
    private static final Map<WorkflowStage, List<WorkflowStage>> PREDECESSORS =
            Map.of(
                    INPUTS, List.of(),
                    VALIDATE, List.of(INPUTS),
                    COMET, List.of(VALIDATE),
                    PERCOLATOR, List.of(COMET),
                    RESULTS, List.of(PERCOLATOR),
                    PDV, List.of(RESULTS),
                    LIMELIGHT_XML, List.of(RESULTS),
                    LIMELIGHT_UPLOAD, List.of(LIMELIGHT_XML));

    private final String id;

    private final String displayName;

    WorkflowStage(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    /**
     * The core path, in the order the stepper draws it: Inputs, Validate, Comet, Percolator,
     * Results.
     *
     * @return an immutable list of the five core stages, first to last
     */
    public static List<WorkflowStage> coreStages() {
        return CORE_PATH;
    }

    /**
     * The optional branches attached to {@link #RESULTS}, each in the order it is drawn.
     *
     * @return an immutable list of immutable branches: {@code [[PDV], [LIMELIGHT_XML,
     *     LIMELIGHT_UPLOAD]]}
     */
    public static List<List<WorkflowStage>> downstreamBranches() {
        return DOWNSTREAM_BRANCHES;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String displayName() {
        return displayName;
    }

    /**
     * The stages that must come before this one, as the diagram draws them.
     *
     * <p>{@link #INPUTS} has none; every other stage has at least one. {@link #LIMELIGHT_UPLOAD}
     * depends on {@link #LIMELIGHT_XML} rather than directly on {@link #RESULTS}, because there is
     * nothing to upload until the converter has produced it.
     *
     * @return an immutable list, empty only for {@link #INPUTS}
     */
    public List<WorkflowStage> predecessors() {
        return PREDECESSORS.get(this);
    }

    /**
     * Whether this stage is on the core path that every run performs.
     *
     * <p>The three stages for which this is false -- {@link #PDV}, {@link #LIMELIGHT_XML} and
     * {@link #LIMELIGHT_UPLOAD} -- are the optional downstream ones. The distinction is not
     * cosmetic: {@link RunState#deriveFrom(Map)} derives success and failure from the core stages
     * alone.
     *
     * @return {@code true} for the five core stages, {@code false} for the three downstream ones
     */
    public boolean isCore() {
        return CORE_PATH.contains(this);
    }
}
