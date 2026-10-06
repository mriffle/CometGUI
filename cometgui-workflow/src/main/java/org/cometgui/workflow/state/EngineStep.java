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

/**
 * One of the seventeen steps of the specification's <em>Canonical workflow DAG</em>, in the order
 * the specification lists them.
 *
 * <p>These are the workflow engine's steps -- finer-grained than the eight {@link WorkflowStage
 * stepper stages} a scientist watches, and each drawn under exactly one of them ({@link #stage()}).
 * The dependency edges between them are declared once, in {@link StepGraph#canonical()}.
 *
 * <h2>What each constant carries, and why each is data</h2>
 *
 * <ul>
 *   <li>{@link #id()} -- stable and lower-case-hyphenated. It is written into provenance and logs,
 *       and it is used as or inside a process-service stage identifier, which must match {@code
 *       [A-Za-z0-9_-]{1,64}}. It is part of the fingerprint encoding.
 *   <li>{@link #displayName()} -- the specification's own wording for the step.
 *   <li>{@link #stage()} -- the stepper stage the step is drawn under. Every optional step maps to
 *       one of the optional downstream stages and every other step to a core stage; a test holds
 *       that, because {@link RunState} reads "core" from the stage and {@link
 *       RunState#deriveFrom(Plan, java.util.Map)} reads it from the step, and the two must agree.
 *   <li>{@link #kind()} -- {@link StepKind#PREPARATION} or {@link StepKind#RESULT}; see {@link
 *       StepKind} for why the rerun rules need the distinction.
 *   <li>{@link #isOptional()} -- true for exactly the three the specification marks "Optional:":
 *       PDV, the Limelight converter, and the Limelight upload.
 *   <li>{@link #implementedInPhase()} -- the phase that implements the step. The whole graph is
 *       declared in phase 08 because invalidation must be computed over the real graph (P8-9), but
 *       phase 08 implements only the steps up to merging the PIN files plus finalising core
 *       provenance. A later phase that implements a step changes nothing here but, at most, this
 *       one number.
 *   <li>{@link #inputs()} -- the input kinds the step reads, in the order they enter its
 *       fingerprint.
 * </ul>
 *
 * <h2>The stage mapping</h2>
 *
 * <p>Several steps map to one stage, and the stage's state is combined from theirs by {@link
 * StageProjection}. The choices that needed a decision:
 *
 * <ul>
 *   <li><strong>Resolving a tool is drawn under that tool's stage</strong>, not under Validate. A
 *       first-run Comet download is "the Comet stage is busy" to the person watching, and a
 *       Percolator-only rerun then shows Comet untouched -- which is the specification's own
 *       description of that rerun.
 *   <li><strong>Serialising the parameter file and building the index are Comet's</strong>, for the
 *       same reason: both exist only to feed the search.
 *   <li><strong>Hashing the inputs is drawn under Inputs.</strong> It is the step that records what
 *       the inputs <em>are</em> (R-RUN-03), and the Inputs stage would otherwise have no engine
 *       step at all.
 *   <li><strong>Finalising provenance and appending downstream provenance are drawn under
 *       Results</strong>, the last core stage. Neither is optional: a run whose provenance could
 *       not be written has not finished, whatever else it did.
 * </ul>
 */
public enum EngineStep {

    /** Step 1. Runs the one validator over the configuration and the input files' facts. */
    VALIDATE_CONFIGURATION(
            "validate-configuration",
            "Validate project inputs and configuration",
            WorkflowStage.VALIDATE,
            StepKind.PREPARATION,
            false,
            8,
            List.of(
                    InputKind.SPECTRUM_FILES,
                    InputKind.FASTA,
                    InputKind.COMET_PARAMETERS,
                    InputKind.COMET_INDEX_MODE)),

    /** Step 2. Makes the selected Comet binary present, verified and probed. */
    RESOLVE_COMET(
            "resolve-comet",
            "Resolve, install and probe Comet",
            WorkflowStage.COMET,
            StepKind.PREPARATION,
            false,
            8,
            List.of(InputKind.COMET_TOOL)),

    /** Step 3. Makes the selected Percolator binary present, verified and probed. */
    RESOLVE_PERCOLATOR(
            "resolve-percolator",
            "Resolve, install and probe Percolator",
            WorkflowStage.PERCOLATOR,
            StepKind.PREPARATION,
            false,
            9,
            List.of(InputKind.PERCOLATOR_TOOL)),

    /** Step 4. Writes the canonical {@code comet.params} into the run, once. */
    SERIALISE_COMET_PARAMS(
            "serialise-comet-params",
            "Serialise the canonical Comet parameter file",
            WorkflowStage.COMET,
            StepKind.RESULT,
            false,
            8,
            List.of(InputKind.COMET_PARAMETERS)),

    /** Step 5. Records the spectrum files' and the FASTA's MD5 and SHA-256. */
    HASH_INPUTS(
            "hash-inputs",
            "Hash immutable inputs",
            WorkflowStage.INPUTS,
            StepKind.PREPARATION,
            false,
            8,
            List.of(InputKind.SPECTRUM_FILES, InputKind.FASTA)),

    /**
     * Step 6. Builds or reuses the Comet index. Planned only when an index mode is selected: its
     * edge to {@link #RUN_COMET} is not a required one, so a plan does not pull it in.
     */
    BUILD_COMET_INDEX(
            "build-comet-index",
            "Build or reuse the Comet index",
            WorkflowStage.COMET,
            StepKind.RESULT,
            false,
            8,
            List.of(
                    InputKind.FASTA,
                    InputKind.COMET_PARAMETERS,
                    InputKind.COMET_TOOL,
                    InputKind.COMET_INDEX_MODE)),

    /** Step 7. One Comet invocation per spectrum file. */
    RUN_COMET(
            "run-comet",
            "Run Comet once per spectrum file",
            WorkflowStage.COMET,
            StepKind.RESULT,
            false,
            8,
            List.of(
                    InputKind.SPECTRUM_FILES,
                    InputKind.FASTA,
                    InputKind.COMET_PARAMETERS,
                    InputKind.COMET_INDEX_MODE,
                    InputKind.COMET_TOOL)),

    /** Step 8. Validates each file's pepXML and PIN. */
    VALIDATE_COMET_OUTPUTS(
            "validate-comet-outputs",
            "Validate Comet pepXML and PIN outputs per file",
            WorkflowStage.COMET,
            StepKind.RESULT,
            false,
            8,
            List.of()),

    /** Step 9. Merges the per-file PINs into {@code inputs/pin/merged.pin}. */
    MERGE_PIN(
            "merge-pin",
            "Merge PIN files",
            WorkflowStage.COMET,
            StepKind.RESULT,
            false,
            8,
            List.of()),

    /** Step 10. Percolator over the merged PIN. */
    RUN_PERCOLATOR(
            "run-percolator",
            "Run Percolator",
            WorkflowStage.PERCOLATOR,
            StepKind.RESULT,
            false,
            9,
            List.of(InputKind.PERCOLATOR_SETTINGS, InputKind.PERCOLATOR_TOOL)),

    /** Step 11. Validates and parses Percolator's outputs and learned weights. */
    PARSE_PERCOLATOR(
            "parse-percolator",
            "Validate and parse Percolator outputs and learned weights",
            WorkflowStage.PERCOLATOR,
            StepKind.RESULT,
            false,
            9,
            List.of()),

    /** Step 12. Builds the result indexes and summaries the Results screen reads. */
    FINALISE_RESULTS(
            "finalise-results",
            "Finalise core result indexes and summaries",
            WorkflowStage.RESULTS,
            StepKind.RESULT,
            false,
            10,
            List.of()),

    /** Step 13. Hashes the run's outputs and finalises core provenance. */
    FINALISE_PROVENANCE(
            "finalise-provenance",
            "Hash outputs and finalise core provenance",
            WorkflowStage.RESULTS,
            StepKind.RESULT,
            false,
            8,
            List.of()),

    /** Step 14, optional. Launches PDV over the run's results. */
    LAUNCH_PDV(
            "launch-pdv",
            "Launch or use PDV",
            WorkflowStage.PDV,
            StepKind.RESULT,
            true,
            11,
            List.of(InputKind.PDV_TOOL)),

    /** Step 15, optional. Converts the run's results to Limelight XML. */
    CONVERT_LIMELIGHT(
            "convert-limelight",
            "Run the Limelight converter",
            WorkflowStage.LIMELIGHT_XML,
            StepKind.RESULT,
            true,
            12,
            List.of(
                    InputKind.LIMELIGHT_Q_CUTOFF,
                    InputKind.LIMELIGHT_CONVERTER_OPTIONS,
                    InputKind.LIMELIGHT_CONVERTER_TOOL)),

    /** Step 16, optional. Uploads the Limelight XML. */
    UPLOAD_LIMELIGHT(
            "upload-limelight",
            "Upload to Limelight",
            WorkflowStage.LIMELIGHT_UPLOAD,
            StepKind.RESULT,
            true,
            12,
            List.of(InputKind.LIMELIGHT_UPLOAD_TARGET)),

    /**
     * Step 17. Appends the optional steps' provenance events and refreshes the report. Implemented
     * with the first optional step, PDV, whose JAR checksum and version must reach provenance
     * whenever it is launched from a run.
     */
    APPEND_DOWNSTREAM_PROVENANCE(
            "append-downstream-provenance",
            "Append downstream provenance events and refresh the report",
            WorkflowStage.RESULTS,
            StepKind.RESULT,
            false,
            11,
            List.of());

    private final String id;

    private final String displayName;

    private final WorkflowStage stage;

    private final StepKind kind;

    private final boolean optional;

    private final int implementedInPhase;

    private final List<InputKind> inputs;

    EngineStep(
            String id,
            String displayName,
            WorkflowStage stage,
            StepKind kind,
            boolean optional,
            int implementedInPhase,
            List<InputKind> inputs) {
        this.id = id;
        this.displayName = displayName;
        this.stage = stage;
        this.kind = kind;
        this.optional = optional;
        this.implementedInPhase = implementedInPhase;
        this.inputs = inputs;
    }

    /**
     * The stable, lower-case-hyphenated identifier.
     *
     * @return the identifier; matches {@code [a-z0-9-]{1,64}}
     */
    public String id() {
        return id;
    }

    /**
     * The step as the specification words it.
     *
     * @return the display name, never {@code null}
     */
    public String displayName() {
        return displayName;
    }

    /**
     * The one stepper stage this step is drawn under.
     *
     * @return the stage, never {@code null}
     */
    public WorkflowStage stage() {
        return stage;
    }

    /**
     * Whether this step's outputs are reusable, or it only prepares for the steps that have some.
     *
     * @return the kind, never {@code null}
     */
    public StepKind kind() {
        return kind;
    }

    /**
     * Whether the specification marks this step optional: PDV, the Limelight converter and the
     * Limelight upload. A failed optional step does not fail the run.
     *
     * @return {@code true} for those three steps only
     */
    public boolean isOptional() {
        return optional;
    }

    /**
     * The phase that implements this step.
     *
     * @return a phase number from {@code phases/index.rst}
     */
    public int implementedInPhase() {
        return implementedInPhase;
    }

    /**
     * The input kinds this step reads, in the order they enter its fingerprint.
     *
     * @return an immutable list, possibly empty
     */
    public List<InputKind> inputs() {
        return List.copyOf(inputs);
    }
}
