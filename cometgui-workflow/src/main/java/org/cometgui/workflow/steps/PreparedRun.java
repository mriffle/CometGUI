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

import java.nio.file.Path;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import org.cometgui.domain.run.RunIdentity;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.params.comet.writer.WrittenParams;
import org.cometgui.provenance.manifest.ApplicationRecord;
import org.cometgui.workflow.engine.RunRequest;
import org.cometgui.workflow.engine.StepAction;
import org.cometgui.workflow.state.EngineStep;
import org.cometgui.workflow.state.Plan;
import org.cometgui.workflow.state.StepInputs;
import org.cometgui.workflow.storage.ProjectLock;
import org.cometgui.workflow.storage.RunStore;

/**
 * A recorded Comet run, ready for the engine: its directory exists, its {@code comet.params} is
 * archived and hashed, its {@code run.json} identity is written, and every planned step has its
 * action.
 *
 * <p>{@link #request()} is an attempt of this run, for {@code WorkflowEngine.start} -- the first,
 * or a retry: each call fingerprints the plan from the run's <em>recorded</em> inputs, so a retry
 * reuses what still matches and the engine refuses to reuse anything whose file changed.
 */
public final class PreparedRun {

    private final CometRun run;

    private final WrittenParams parameters;

    private final Plan plan;

    private final RunStore store;

    private final ProjectLock lock;

    private final ApplicationRecord application;

    private final Map<String, String> settings;

    PreparedRun(
            CometRun run,
            WrittenParams parameters,
            Plan plan,
            RunStore store,
            ProjectLock lock,
            ApplicationRecord application,
            Map<String, String> settings) {
        this.run = Objects.requireNonNull(run, "run");
        this.parameters = Objects.requireNonNull(parameters, "parameters");
        this.plan = Objects.requireNonNull(plan, "plan");
        this.store = Objects.requireNonNull(store, "store");
        this.lock = Objects.requireNonNull(lock, "lock");
        this.application = Objects.requireNonNull(application, "application");
        this.settings = Collections.unmodifiableMap(new TreeMap<>(settings));
    }

    /**
     * The run's directory.
     *
     * @return the layout
     */
    public RunLayout layout() {
        return run.layout();
    }

    /**
     * The run's immutable identity, as it was written to {@code run.json}.
     *
     * @return the identity
     */
    public RunIdentity identity() {
        return run.identity();
    }

    /**
     * What {@code CanonicalParamsWriter.writeOnce} wrote and hashed: the file every Comet
     * invocation of this run is given with {@code -P} ({@code R-PARAM-12}).
     *
     * @return the written file, its digests and its size
     */
    public WrittenParams parameters() {
        return parameters;
    }

    /**
     * The steps this run executes.
     *
     * @return the plan: up to {@code finalise-provenance}, with {@code build-comet-index} when an
     *     index mode is set
     */
    public Plan plan() {
        return plan;
    }

    /**
     * The cached index the run builds or reuses, when it has an index mode.
     *
     * @return {@code index-cache/<key>/<fasta name>.idx}, or empty
     */
    public Optional<Path> indexFile() {
        return run.cacheEntry().map(IndexCacheEntry::indexFile);
    }

    /**
     * Whether the run builds its index, rather than reusing a complete cache entry.
     *
     * @return {@code true} if the index step invokes Comet
     */
    public boolean buildsIndex() {
        return run.buildIndex();
    }

    /**
     * The settings provenance records for the run (each key a {@code ProvenanceSchema} settings
     * key).
     *
     * @return an immutable, sorted map
     */
    public Map<String, String> settings() {
        return settings;
    }

    /**
     * The input values of the run as it was recorded.
     *
     * @return the inputs the plan is fingerprinted from
     */
    public StepInputs inputs() {
        return RunInputs.recorded(run.identity(), run.tool().hashes().sha256());
    }

    /**
     * One action per planned step.
     *
     * @return an immutable map in step order
     */
    public Map<EngineStep, StepAction> actions() {
        Map<EngineStep, StepAction> actions = new EnumMap<>(EngineStep.class);
        actions.put(
                EngineStep.VALIDATE_CONFIGURATION, new PreparationSteps.ValidateConfiguration(run));
        actions.put(EngineStep.RESOLVE_COMET, new PreparationSteps.ResolveComet(run));
        actions.put(EngineStep.SERIALISE_COMET_PARAMS, new PreparationSteps.SerialiseParams(run));
        actions.put(EngineStep.HASH_INPUTS, new PreparationSteps.HashInputs(run));
        if (plan.contains(EngineStep.BUILD_COMET_INDEX)) {
            actions.put(EngineStep.BUILD_COMET_INDEX, new SearchSteps.BuildIndex(run));
        }
        actions.put(EngineStep.RUN_COMET, new SearchSteps.RunComet(run));
        actions.put(EngineStep.VALIDATE_COMET_OUTPUTS, new SearchSteps.ValidateOutputs(run));
        actions.put(EngineStep.MERGE_PIN, new SearchSteps.MergePin(run));
        actions.put(EngineStep.FINALISE_PROVENANCE, new SearchSteps.FinaliseProvenance(run));
        return Collections.unmodifiableMap(actions);
    }

    /**
     * An attempt of this run, fingerprinted from its recorded inputs.
     *
     * @return the request for {@code WorkflowEngine.start} or {@code checkReuse}
     */
    public RunRequest request() {
        return request(inputs());
    }

    /**
     * This run's plan and actions with other input values -- what a rerun preview compares with the
     * run's record.
     *
     * @param inputs the input values
     * @return the request
     */
    RunRequest request(StepInputs inputs) {
        return new RunRequest(
                store,
                lock,
                run.layout(),
                plan,
                inputs,
                Set.of(),
                actions(),
                application,
                settings);
    }
}
