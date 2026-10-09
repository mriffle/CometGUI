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

package org.cometgui.domain.run;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DecimalStyle;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.cometgui.domain.project.ProjectLayout;

/**
 * Where everything of one run lives: design decision P8-3, as paths.
 *
 * <pre>
 *   runs/&lt;UTC yyyyMMdd'T'HHmmss'Z'&gt;-&lt;run id&gt;/
 *       run.json
 *       parameters/comet.params
 *       inputs/pin/merged.pin
 *       outputs/comet/&lt;base&gt;.pep.xml
 *       outputs/comet/&lt;base&gt;.pin
 *       logs/comet-&lt;nn&gt;.log
 *       provenance/provenance.json
 *       provenance/provenance.rst
 *       provenance/events.log
 *       results/index/                 (finalise-results; never under outputs/)
 *       results/view-state.json
 *       exports/
 * </pre>
 *
 * <p>Pure: no method touches the disk. {@code org.cometgui.workflow.storage.RunStore} creates the
 * directories this type names, and every other component asks this type for a path rather than
 * building one, so the layout is written down in exactly one place.
 *
 * <h2>The one divergence from the specification, stated rather than hidden</h2>
 *
 * <p>The specification's <em>Project model</em> shows Comet's logs as {@code
 * logs/comet.<spectrum-basename>.{stdout,stderr}.log}. The process service writes
 * <strong>one</strong> timestamped, stream-tagged log per invocation -- Phase 03's design, which
 * keeps the interleaving of the two streams that two files would lose -- and names it after the
 * invocation's stage identifier, which must match {@code [A-Za-z0-9_-]{1,64}} and so cannot carry
 * an arbitrary file name. So a Comet invocation's log is {@code logs/comet-<nn>.log}, {@code nn}
 * the input's 1-based position with at least two digits, and {@code run.json} maps each {@code nn}
 * to its spectrum file and base name. P8-3 escalates this as a proposed specification amendment. A
 * retried invocation's log is {@code comet-<nn>.1.log}, {@code .2.log} ..., because the process
 * service never overwrites a log.
 *
 * <h2>What CometGUI derives is never under {@code outputs/}</h2>
 *
 * <p>{@code outputs/} holds what the tools wrote and nothing else ({@code R-PERC-07}). Everything
 * CometGUI derives from those files has a directory of its own (design decision P10-6): {@link
 * #resultsDirectory() results/} holds the result stores' index files, in {@link
 * #resultIndexDirectory() results/index/} -- one directory per run, so two runs' stores of one
 * table kind never share an index -- and the run's display-filter {@link #viewStateFile() view
 * state}; {@link #exportsDirectory() exports/} holds filtered exports. Neither is in {@link
 * #directories()}: each is made by the step or action that first writes into it, so a run that
 * never reaches the results has neither.
 *
 * <h2>The event log's name is pinned here</h2>
 *
 * <p>Phase 04 escalated that no constant named the provenance event log's file; {@link
 * #EVENT_LOG_FILE_NAME} is that constant, and it is the name Phase 04's tests and run-directory
 * secret sweep already use.
 *
 * @param root the run directory, absolute and normalised
 */
public record RunLayout(Path root) {

    /** The run descriptor's file name. */
    public static final String RUN_FILE_NAME = "run.json";

    /** The directory holding the run's serialised scientific parameters, written once. */
    public static final String PARAMETERS_DIRECTORY_NAME = "parameters";

    /** The canonical Comet parameter file's name. */
    public static final String COMET_PARAMS_FILE_NAME = "comet.params";

    /** The directory of files the run prepared as inputs to a later stage. */
    public static final String INPUTS_DIRECTORY_NAME = "inputs";

    /** The subdirectory of {@code inputs/} holding PIN files. */
    public static final String PIN_DIRECTORY_NAME = "pin";

    /** The merged PIN file's name. */
    public static final String MERGED_PIN_FILE_NAME = "merged.pin";

    /** The directory of the tools' outputs. */
    public static final String OUTPUTS_DIRECTORY_NAME = "outputs";

    /** The subdirectory of {@code outputs/} holding Comet's per-file outputs. */
    public static final String COMET_DIRECTORY_NAME = "comet";

    /** The directory of process logs. */
    public static final String LOGS_DIRECTORY_NAME = "logs";

    /** The directory of the provenance record. */
    public static final String PROVENANCE_DIRECTORY_NAME = "provenance";

    /** The provenance manifest's name, equal to {@code ManifestWriter.FILE_NAME}. */
    public static final String PROVENANCE_JSON_FILE_NAME = "provenance.json";

    /** The provenance report's name, equal to {@code ProvenanceReportWriter.FILE_NAME}. */
    public static final String PROVENANCE_RST_FILE_NAME = "provenance.rst";

    /** The provenance event log's name; see the class documentation. */
    public static final String EVENT_LOG_FILE_NAME = "events.log";

    /** The directory of the files CometGUI derives from a run's results; never under outputs/. */
    public static final String RESULTS_DIRECTORY_NAME = "results";

    /** The subdirectory of {@code results/} holding the result stores' index files. */
    public static final String RESULT_INDEX_DIRECTORY_NAME = "index";

    /** The run's display-filter view state, in {@code results/}. */
    public static final String VIEW_STATE_FILE_NAME = "view-state.json";

    /** The directory of filtered exports, which are new files and never overwritten. */
    public static final String EXPORTS_DIRECTORY_NAME = "exports";

    /** The suffix Comet gives a pepXML output. */
    public static final String PEP_XML_SUFFIX = ".pep.xml";

    /** The suffix Comet gives a PIN output. */
    public static final String PIN_SUFFIX = ".pin";

    /** The prefix of a Comet invocation's stage identifier. */
    public static final String COMET_STAGE_PREFIX = "comet-";

    /** The suffix the process service gives a stage log. */
    public static final String LOG_SUFFIX = ".log";

    /** How a run directory's timestamp is written: UTC, seconds, ASCII digits. */
    private static final DateTimeFormatter DIRECTORY_TIMESTAMP =
            DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss'Z'", Locale.ROOT)
                    .withZone(ZoneOffset.UTC)
                    .withDecimalStyle(DecimalStyle.STANDARD);

    /**
     * Normalises the root.
     *
     * @throws NullPointerException if {@code root} is {@code null}
     * @throws IllegalArgumentException if {@code root} is not absolute, naming it
     */
    public RunLayout {
        Objects.requireNonNull(root, "root");
        if (!root.isAbsolute()) {
            throw new IllegalArgumentException("a run directory must be absolute: " + root);
        }
        root = root.normalize();
    }

    /**
     * The layout of a new run of a project.
     *
     * @param project the project
     * @param created when the run was created
     * @param runId the run's identifier
     * @return {@code <project>/runs/<directoryName(created, runId)>}
     * @throws NullPointerException if an argument is {@code null}
     */
    public static RunLayout of(ProjectLayout project, Instant created, RunId runId) {
        Objects.requireNonNull(project, "project");
        return new RunLayout(project.runsDirectory().resolve(directoryName(created, runId)));
    }

    /**
     * A run directory's name: the UTC creation time to the second, a hyphen, and the run id.
     *
     * <p>The timestamp comes first so that a directory listing sorts runs by when they were made,
     * and the run id makes the name unique when two runs start within one second.
     *
     * @param created when the run was created
     * @param runId the run's identifier
     * @return for example {@code 20260828T231500Z-run-0001}
     * @throws NullPointerException if an argument is {@code null}
     */
    public static String directoryName(Instant created, RunId runId) {
        Objects.requireNonNull(created, "created");
        Objects.requireNonNull(runId, "runId");
        return DIRECTORY_TIMESTAMP.format(created) + "-" + runId.value();
    }

    /**
     * The process-service stage identifier of the Comet invocation for one input.
     *
     * @param position the input's 1-based position
     * @return {@code comet-} and the position with at least two digits: {@code comet-01}, {@code
     *     comet-12}, {@code comet-123}
     * @throws IllegalArgumentException if {@code position} is not positive
     */
    public static String cometStageId(int position) {
        if (position < 1) {
            throw new IllegalArgumentException("a position is 1-based, but was " + position);
        }
        return COMET_STAGE_PREFIX + (position < 10 ? "0" : "") + position;
    }

    /**
     * The run descriptor.
     *
     * @return {@code run.json}
     */
    public Path runFile() {
        return root.resolve(RUN_FILE_NAME);
    }

    /**
     * The parameters directory, whose files are written once.
     *
     * @return {@code parameters/}
     */
    public Path parametersDirectory() {
        return root.resolve(PARAMETERS_DIRECTORY_NAME);
    }

    /**
     * The canonical Comet parameter file, passed to Comet with {@code -P}.
     *
     * @return {@code parameters/comet.params}
     */
    public Path cometParamsFile() {
        return parametersDirectory().resolve(COMET_PARAMS_FILE_NAME);
    }

    /**
     * The canonical Comet parameter file's path relative to the run, as {@code run.json} records
     * it.
     *
     * @return {@code parameters/comet.params}, with {@code /} on every platform
     */
    public static String cometParamsRelativePath() {
        return PARAMETERS_DIRECTORY_NAME + "/" + COMET_PARAMS_FILE_NAME;
    }

    /**
     * The inputs directory.
     *
     * @return {@code inputs/}
     */
    public Path inputsDirectory() {
        return root.resolve(INPUTS_DIRECTORY_NAME);
    }

    /**
     * The directory of PIN files prepared for Percolator.
     *
     * @return {@code inputs/pin/}
     */
    public Path pinInputsDirectory() {
        return inputsDirectory().resolve(PIN_DIRECTORY_NAME);
    }

    /**
     * The merged PIN file.
     *
     * @return {@code inputs/pin/merged.pin}
     */
    public Path mergedPinFile() {
        return pinInputsDirectory().resolve(MERGED_PIN_FILE_NAME);
    }

    /**
     * The merged PIN file's path relative to the run, as {@code run.json} records a derived run's
     * copy of it.
     *
     * @return {@code inputs/pin/merged.pin}, with {@code /} on every platform
     */
    public static String mergedPinRelativePath() {
        return INPUTS_DIRECTORY_NAME + "/" + PIN_DIRECTORY_NAME + "/" + MERGED_PIN_FILE_NAME;
    }

    /**
     * The outputs directory.
     *
     * @return {@code outputs/}
     */
    public Path outputsDirectory() {
        return root.resolve(OUTPUTS_DIRECTORY_NAME);
    }

    /**
     * Comet's output directory.
     *
     * @return {@code outputs/comet/}
     */
    public Path cometOutputDirectory() {
        return outputsDirectory().resolve(COMET_DIRECTORY_NAME);
    }

    /**
     * What Comet's {@code -N} names for one input: the output path without a suffix.
     *
     * @param base the input's base name from {@link OutputBaseNames}
     * @return {@code outputs/comet/<base>}
     * @throws NullPointerException if {@code base} is {@code null}
     * @throws IllegalArgumentException if the base could name anything outside {@code
     *     outputs/comet/}
     */
    public Path cometOutputBase(String base) {
        OutputBaseNames.requireSafe(base);
        return cometOutputDirectory().resolve(base);
    }

    /**
     * One input's pepXML output.
     *
     * @param base the input's base name
     * @return {@code outputs/comet/<base>.pep.xml}
     * @throws IllegalArgumentException as {@link #cometOutputBase(String)}
     */
    public Path pepXmlFile(String base) {
        return cometOutputDirectory().resolve(OutputBaseNames.requireSafe(base) + PEP_XML_SUFFIX);
    }

    /**
     * One input's PIN output.
     *
     * @param base the input's base name
     * @return {@code outputs/comet/<base>.pin}
     * @throws IllegalArgumentException as {@link #cometOutputBase(String)}
     */
    public Path pinFile(String base) {
        return cometOutputDirectory().resolve(OutputBaseNames.requireSafe(base) + PIN_SUFFIX);
    }

    /**
     * The logs directory, which the process service writes into.
     *
     * @return {@code logs/}
     */
    public Path logsDirectory() {
        return root.resolve(LOGS_DIRECTORY_NAME);
    }

    /**
     * The log of the first Comet invocation for one input.
     *
     * @param position the input's 1-based position
     * @return {@code logs/comet-<nn>.log}
     * @throws IllegalArgumentException if {@code position} is not positive
     */
    public Path cometLogFile(int position) {
        return logsDirectory().resolve(cometStageId(position) + LOG_SUFFIX);
    }

    /**
     * The provenance directory.
     *
     * @return {@code provenance/}
     */
    public Path provenanceDirectory() {
        return root.resolve(PROVENANCE_DIRECTORY_NAME);
    }

    /**
     * The provenance manifest.
     *
     * @return {@code provenance/provenance.json}
     */
    public Path provenanceJsonFile() {
        return provenanceDirectory().resolve(PROVENANCE_JSON_FILE_NAME);
    }

    /**
     * The provenance manifest's path relative to the run, as {@code run.json} records the manifest
     * of the run a derived run was made from.
     *
     * @return {@code provenance/provenance.json}, with {@code /} on every platform
     */
    public static String provenanceJsonRelativePath() {
        return PROVENANCE_DIRECTORY_NAME + "/" + PROVENANCE_JSON_FILE_NAME;
    }

    /**
     * The provenance report.
     *
     * @return {@code provenance/provenance.rst}
     */
    public Path provenanceRstFile() {
        return provenanceDirectory().resolve(PROVENANCE_RST_FILE_NAME);
    }

    /**
     * The provenance event log.
     *
     * @return {@code provenance/events.log}
     */
    public Path eventLogFile() {
        return provenanceDirectory().resolve(EVENT_LOG_FILE_NAME);
    }

    /**
     * The directory of what CometGUI derives from the run's results -- the result indexes and the
     * view state. Never under {@code outputs/}, and not in {@link #directories()}.
     *
     * @return {@code results/}
     */
    public Path resultsDirectory() {
        return root.resolve(RESULTS_DIRECTORY_NAME);
    }

    /**
     * The directory the result stores keep their index files in: this run's own, so no two runs'
     * stores share one.
     *
     * @return {@code results/index/}
     */
    public Path resultIndexDirectory() {
        return resultsDirectory().resolve(RESULT_INDEX_DIRECTORY_NAME);
    }

    /**
     * The result index directory's path relative to the run.
     *
     * @return {@code results/index}, with {@code /} on every platform
     */
    public static String resultIndexRelativePath() {
        return RESULTS_DIRECTORY_NAME + "/" + RESULT_INDEX_DIRECTORY_NAME;
    }

    /**
     * The run's view state: the display-filter values the results were last shown with ({@code
     * R-RES-01}).
     *
     * @return {@code results/view-state.json}
     */
    public Path viewStateFile() {
        return resultsDirectory().resolve(VIEW_STATE_FILE_NAME);
    }

    /**
     * The view state's path relative to the run.
     *
     * @return {@code results/view-state.json}, with {@code /} on every platform
     */
    public static String viewStateRelativePath() {
        return RESULTS_DIRECTORY_NAME + "/" + VIEW_STATE_FILE_NAME;
    }

    /**
     * The directory of the run's filtered exports ({@code R-PERC-07}: derived filtered exports are
     * new files under a distinct directory). Not in {@link #directories()}.
     *
     * @return {@code exports/}
     */
    public Path exportsDirectory() {
        return root.resolve(EXPORTS_DIRECTORY_NAME);
    }

    /**
     * The exports directory's path relative to the run.
     *
     * @return {@code exports}
     */
    public static String exportsRelativePath() {
        return EXPORTS_DIRECTORY_NAME;
    }

    /**
     * Every directory of the layout, each after its parent, starting with the run directory.
     *
     * <p>{@link #resultsDirectory() results/} and {@link #exportsDirectory() exports/} are not
     * among them: each is made by what first writes into it.
     *
     * @return the directories to create for a new run
     */
    public List<Path> directories() {
        return List.of(
                root,
                parametersDirectory(),
                inputsDirectory(),
                pinInputsDirectory(),
                outputsDirectory(),
                cometOutputDirectory(),
                logsDirectory(),
                provenanceDirectory());
    }
}
