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

package org.cometgui.workflow.engine;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.provenance.manifest.ApplicationRecord;
import org.cometgui.provenance.manifest.ProvenanceSchema;
import org.cometgui.workflow.state.EngineStep;
import org.cometgui.workflow.state.Plan;
import org.cometgui.workflow.state.StepInputs;
import org.cometgui.workflow.storage.ProjectLock;
import org.cometgui.workflow.storage.RunStore;

/**
 * One attempt of one run, as the caller asks for it.
 *
 * <p>The run must already be recorded: {@code run.json} exists with its immutable identity ({@link
 * RunStore#record}). The engine adds an attempt to it, records the fingerprint of every step that
 * succeeds, and ends the attempt. A retry is simply another request for the same run: the engine
 * compares the plan's fingerprints with the ones recorded for steps that succeeded before, and
 * reuses what it can after re-hashing it ({@link WorkflowEngine#checkReuse}).
 *
 * @param store the project's run store
 * @param lock the project's lock, held for the whole attempt
 * @param layout the run's directory
 * @param plan the steps the run covers
 * @param inputs the current input values the plan is fingerprinted from
 * @param forced planned steps that must execute even if their recorded outputs could be reused --
 *     what a refused reuse offers ({@link ReuseCheck#offeredForced()})
 * @param actions an implementation for every planned step; a planned step without one is refused by
 *     name (P8-9)
 * @param application the application record for provenance
 * @param settings settings for provenance, each key matching {@link
 *     ProvenanceSchema#SETTINGS_KEY_PATTERN}; the engine's own keys ({@link
 *     WorkflowEngine#ATTEMPT_SETTING}, {@link WorkflowEngine#PLAN_SETTING}) are reserved
 */
public record RunRequest(
        RunStore store,
        ProjectLock lock,
        RunLayout layout,
        Plan plan,
        StepInputs inputs,
        Set<EngineStep> forced,
        Map<EngineStep, StepAction> actions,
        ApplicationRecord application,
        Map<String, String> settings) {

    private static final Pattern SETTINGS_KEY =
            Pattern.compile(ProvenanceSchema.SETTINGS_KEY_PATTERN);

    /**
     * Validates and copies.
     *
     * @throws NullPointerException naming a component, a key or a value that is {@code null}
     * @throws IllegalArgumentException if a settings key is malformed or reserved, quoting it
     */
    public RunRequest {
        Objects.requireNonNull(store, "store");
        Objects.requireNonNull(lock, "lock");
        Objects.requireNonNull(layout, "layout");
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(inputs, "inputs");
        Set<EngineStep> forcedCopy = EnumSet.noneOf(EngineStep.class);
        forcedCopy.addAll(Objects.requireNonNull(forced, "forced"));
        forced = Collections.unmodifiableSet(forcedCopy);
        Map<EngineStep, StepAction> actionsCopy = new EnumMap<>(EngineStep.class);
        for (Map.Entry<EngineStep, StepAction> entry : actions.entrySet()) {
            actionsCopy.put(
                    Objects.requireNonNull(entry.getKey(), "actions has a null step"),
                    Objects.requireNonNull(entry.getValue(), "actions has a null action"));
        }
        actions = Collections.unmodifiableMap(actionsCopy);
        Objects.requireNonNull(application, "application");
        Map<String, String> settingsCopy = new TreeMap<>();
        for (Map.Entry<String, String> setting : settings.entrySet()) {
            String key = Objects.requireNonNull(setting.getKey(), "settings has a null key");
            if (!SETTINGS_KEY.matcher(key).matches()) {
                throw new IllegalArgumentException(
                        "a settings key must match "
                                + ProvenanceSchema.SETTINGS_KEY_PATTERN
                                + ", but was: \""
                                + key
                                + "\"");
            }
            if (List.of(WorkflowEngine.ATTEMPT_SETTING, WorkflowEngine.PLAN_SETTING)
                    .contains(key)) {
                throw new IllegalArgumentException(
                        "the settings key \"" + key + "\" is reserved for the engine");
            }
            settingsCopy.put(
                    key, Objects.requireNonNull(setting.getValue(), "no value for " + key));
        }
        settings = Collections.unmodifiableMap(settingsCopy);
    }

    /**
     * This request with a different set of forced steps -- for example the steps a refused reuse
     * offers to re-execute.
     *
     * @param newForced the steps to force
     * @return a new request; this one is unchanged
     */
    public RunRequest withForced(Set<EngineStep> newForced) {
        return new RunRequest(
                store, lock, layout, plan, inputs, newForced, actions, application, settings);
    }

    @Override
    public Set<EngineStep> forced() {
        return Collections.unmodifiableSet(forced);
    }

    @Override
    public Map<EngineStep, StepAction> actions() {
        return Collections.unmodifiableMap(actions);
    }

    @Override
    public Map<String, String> settings() {
        return Collections.unmodifiableMap(settings);
    }
}
