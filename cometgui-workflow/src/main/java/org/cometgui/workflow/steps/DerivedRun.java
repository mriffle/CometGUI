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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import org.cometgui.domain.project.ProjectLayout;
import org.cometgui.domain.run.RunDerivation;
import org.cometgui.domain.run.RunIdentity;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.provenance.manifest.ApplicationRecord;
import org.cometgui.workflow.engine.RunRequest;
import org.cometgui.workflow.engine.StepAction;
import org.cometgui.workflow.state.EngineStep;
import org.cometgui.workflow.state.Plan;
import org.cometgui.workflow.state.StepInputs;
import org.cometgui.workflow.storage.ProjectLock;
import org.cometgui.workflow.storage.RunStore;

/**
 * A recorded <em>derived</em> run, ready for the engine: the compatible-version Percolator rerun of
 * an earlier run ({@link PercolatorRerun#prepare}). Its directory exists; its {@code
 * parameters/comet.params} and {@code inputs/pin/merged.pin} are byte copies of the source run's,
 * each re-hashed and equal to what the source recorded; its {@code percolator-settings.json} is
 * written once; and its {@code run.json} identity, schema version 2, names the source in {@code
 * derivedFrom}.
 *
 * <p>Its plan executes {@code validate-configuration}, {@code resolve-percolator}, {@code
 * run-percolator}, {@code parse-percolator} and {@code finalise-provenance}; the Comet result steps
 * are {@linkplain Plan#provided() provided} by the source and have no action here, so no Comet can
 * be launched. {@link #request()} is an attempt -- the first, or a retry -- for {@code
 * WorkflowEngine.start}, exactly as for {@link PreparedRun}.
 */
public final class DerivedRun {

    private final ProjectLayout project;

    private final RunIdentity identity;

    private final PercolatorRun percolator;

    private final Plan plan;

    private final RunStore store;

    private final ProjectLock lock;

    private final ApplicationRecord application;

    private final Map<String, String> settings;

    private final String sourceCometSha256;

    DerivedRun(
            ProjectLayout project,
            RunIdentity identity,
            PercolatorRun percolator,
            Plan plan,
            RunStore store,
            ProjectLock lock,
            ApplicationRecord application,
            Map<String, String> settings,
            String sourceCometSha256) {
        this.project = Objects.requireNonNull(project, "project");
        this.identity = Objects.requireNonNull(identity, "identity");
        if (identity.derivedFrom().isEmpty()) {
            throw new IllegalArgumentException(
                    "run " + identity.runId() + " is not a derived run: it names no source");
        }
        this.percolator = Objects.requireNonNull(percolator, "percolator");
        this.plan = Objects.requireNonNull(plan, "plan");
        this.store = Objects.requireNonNull(store, "store");
        this.lock = Objects.requireNonNull(lock, "lock");
        this.application = Objects.requireNonNull(application, "application");
        this.settings = Collections.unmodifiableMap(new TreeMap<>(settings));
        this.sourceCometSha256 = Objects.requireNonNull(sourceCometSha256, "sourceCometSha256");
    }

    /**
     * The run's directory.
     *
     * @return the layout
     */
    public RunLayout layout() {
        return percolator.layout();
    }

    /**
     * The run's immutable identity, as it was written to {@code run.json}.
     *
     * @return the identity, whose {@code derivedFrom} is present
     */
    public RunIdentity identity() {
        return identity;
    }

    /**
     * The run whose Comet results this one reuses.
     *
     * @return the derivation {@code run.json} records
     */
    public RunDerivation source() {
        return identity.derivedFrom().orElseThrow();
    }

    /**
     * The steps this run executes.
     *
     * @return the plan, whose provided steps are the Comet result steps reused from the source
     */
    public Plan plan() {
        return plan;
    }

    /**
     * The directory of the raw Percolator outputs.
     *
     * @return {@code outputs/percolator}
     */
    public Path percolatorOutputDirectory() {
        return percolator.outputDirectory();
    }

    /**
     * The Percolator argument array the run executes, built once when the run was prepared from the
     * selection's probed capabilities.
     *
     * @return the argv
     */
    public List<String> percolatorArgv() {
        return percolator.command().command().argv();
    }

    /**
     * The settings provenance records for the run: the Percolator keys and the {@code rerun.*} keys
     * naming the source.
     *
     * @return an immutable, sorted map
     */
    public Map<String, String> settings() {
        return settings;
    }

    /** The project the run belongs to. */
    ProjectLayout project() {
        return project;
    }

    /** The Percolator half. */
    PercolatorRun percolator() {
        return percolator;
    }

    /**
     * The input values the plan is fingerprinted from: the source's recorded Comet inputs, which
     * this run's identity copies, and its own Percolator settings and build.
     *
     * @return the inputs
     */
    public StepInputs inputs() {
        return RunInputs.withPercolator(
                RunInputs.recorded(identity, sourceCometSha256),
                percolator.settings().text(),
                percolator.tool().version(),
                percolator.choice().selection().sha256());
    }

    /**
     * One action per planned step; none for a provided step.
     *
     * @return an immutable map in step order
     */
    public Map<EngineStep, StepAction> actions() {
        Map<EngineStep, StepAction> actions = new EnumMap<>(EngineStep.class);
        actions.put(EngineStep.VALIDATE_CONFIGURATION, new DerivedSteps.ValidateDerived(this));
        actions.put(
                EngineStep.RESOLVE_PERCOLATOR, new PercolatorSteps.ResolvePercolator(percolator));
        actions.put(EngineStep.RUN_PERCOLATOR, new PercolatorSteps.RunPercolator(percolator));
        actions.put(EngineStep.PARSE_PERCOLATOR, new PercolatorSteps.ParsePercolator(percolator));
        actions.put(EngineStep.FINALISE_PROVENANCE, new DerivedSteps.FinaliseDerived(this));
        return Collections.unmodifiableMap(actions);
    }

    /**
     * An attempt of this run, fingerprinted from its recorded inputs.
     *
     * @return the request for {@code WorkflowEngine.start} or {@code checkReuse}
     */
    public RunRequest request() {
        return new RunRequest(
                store, lock, layout(), plan, inputs(), Set.of(), actions(), application, settings);
    }
}
