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

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Which steps of a plan a rerun executes, and why (R-RUN-01, AC-WF-04): the preview the user sees
 * before a run starts, and the decision the engine then carries out.
 *
 * <p>It is computed from three things only -- the {@link Plan}, the current {@link StepInputs}, and
 * the fingerprints recorded for the steps that <em>succeeded</em> in the run being compared -- plus
 * an optional set of steps the caller requires to execute. There is no step-specific logic here:
 * every answer follows from the declared graph and each step's declared inputs.
 *
 * <h2>The rules</h2>
 *
 * <p>For each planned {@link StepKind#RESULT} step, in plan order, the reasons it re-executes are,
 * in this order:
 *
 * <ol>
 *   <li>{@link RerunReason.Forced} -- the caller required it;
 *   <li>{@link RerunReason.NotRecorded} -- nothing is recorded for it, so there is nothing to
 *       reuse; or else one {@link RerunReason.InputChanged} for each of its own declared inputs
 *       whose digest differs from the recorded one;
 *   <li>one {@link RerunReason.UpstreamReExecutes} for each planned {@code RESULT} step it reads
 *       from that re-executes -- this is what carries a change to <em>everything downstream</em>,
 *       including past a step the caller forced;
 *   <li>if none of those applies, {@link RerunReason.FingerprintChanged} when its current
 *       fingerprint still differs from the recorded one.
 * </ol>
 *
 * <p>No reason means {@link RerunDecision#REUSE}; any reason means {@link
 * RerunDecision#RE_EXECUTE}.
 *
 * <p>Then each planned {@link StepKind#PREPARATION} step, latest first, gets one {@link
 * RerunReason.PrerequisiteOf} for each planned step directly downstream of it that executes (after
 * a {@link RerunReason.Forced} if the caller required it): {@link RerunDecision#PREPARE} if it has
 * any, {@link RerunDecision#NOT_NEEDED} if not. A preparation step's own fingerprint does not
 * decide anything; see {@link StepKind}.
 *
 * <p>So if nothing a result depends on changed, nothing executes at all -- not even validation --
 * and {@link #executed()} is empty. That is the specification's "changing only the PSM or peptide
 * display filters requires no scientific rerun".
 */
public final class RerunPreview {

    private final Plan plan;

    private final Map<EngineStep, StepFingerprint> fingerprints;

    private final List<StepVerdict> verdicts;

    private RerunPreview(
            Plan plan, Map<EngineStep, StepFingerprint> fingerprints, List<StepVerdict> verdicts) {
        this.plan = plan;
        this.fingerprints = fingerprints;
        this.verdicts = verdicts;
    }

    /**
     * The preview with no step forced.
     *
     * @param plan the plan about to run
     * @param current the current input values
     * @param recorded the fingerprints recorded for the steps that succeeded in the run being
     *     compared; a step that failed, was cancelled or was not planned has no entry
     * @return the preview
     * @throws NullPointerException if an argument is {@code null}
     * @throws IllegalArgumentException as {@link #compute(Plan, StepInputs, Map, Set)}
     */
    public static RerunPreview compute(
            Plan plan, StepInputs current, Map<EngineStep, StepFingerprint> recorded) {
        return compute(plan, current, recorded, Set.of());
    }

    /**
     * The preview.
     *
     * @param plan the plan about to run
     * @param current the current input values
     * @param recorded the fingerprints recorded for the steps that succeeded in the run being
     *     compared; entries for steps outside the plan are ignored
     * @param forced planned steps that must execute whatever their fingerprints say
     * @return the preview
     * @throws NullPointerException if an argument, a key or a value is {@code null}
     * @throws IllegalArgumentException if a planned step cannot be fingerprinted (see {@link
     *     Fingerprints#compute}), a recorded fingerprint is filed under the wrong step, or a forced
     *     step is not planned -- each naming the step
     */
    public static RerunPreview compute(
            Plan plan,
            StepInputs current,
            Map<EngineStep, StepFingerprint> recorded,
            Set<EngineStep> forced) {
        Map<EngineStep, StepFingerprint> now = Fingerprints.compute(plan, current);
        checkRecorded(recorded);
        checkForced(plan, forced);

        Map<EngineStep, List<RerunReason>> reasons = new EnumMap<>(EngineStep.class);
        for (EngineStep step : plan.steps()) {
            if (step.kind() == StepKind.RESULT) {
                reasons.put(
                        step,
                        resultReasons(
                                plan, step, now.get(step), recorded.get(step), forced, reasons));
            }
        }
        List<EngineStep> latestFirst = new ArrayList<>(plan.steps());
        Collections.reverse(latestFirst);
        for (EngineStep step : latestFirst) {
            if (step.kind() == StepKind.PREPARATION) {
                reasons.put(step, preparationReasons(plan, step, forced, reasons));
            }
        }

        List<StepVerdict> verdicts = new ArrayList<>();
        for (EngineStep step : plan.steps()) {
            List<RerunReason> why = reasons.get(step);
            verdicts.add(new StepVerdict(step, decide(step, why), why));
        }
        return new RerunPreview(plan, now, List.copyOf(verdicts));
    }

    private static void checkRecorded(Map<EngineStep, StepFingerprint> recorded) {
        Objects.requireNonNull(recorded, "recorded");
        for (Map.Entry<EngineStep, StepFingerprint> entry : recorded.entrySet()) {
            EngineStep step = Objects.requireNonNull(entry.getKey(), "recorded has a null step");
            StepFingerprint fingerprint =
                    Objects.requireNonNull(
                            entry.getValue(), "recorded has no value for " + step.id());
            if (fingerprint.step() != step) {
                throw new IllegalArgumentException(
                        "the fingerprint recorded under "
                                + step.id()
                                + " is the fingerprint of "
                                + fingerprint.step().id());
            }
        }
    }

    private static void checkForced(Plan plan, Set<EngineStep> forced) {
        Objects.requireNonNull(forced, "forced");
        for (EngineStep step : forced) {
            Objects.requireNonNull(step, "forced contains null");
            if (!plan.contains(step)) {
                throw new IllegalArgumentException(
                        "step " + step.id() + " is forced but is not in the plan");
            }
        }
    }

    private static List<RerunReason> resultReasons(
            Plan plan,
            EngineStep step,
            StepFingerprint now,
            StepFingerprint was,
            Set<EngineStep> forced,
            Map<EngineStep, List<RerunReason>> decided) {
        List<RerunReason> why = new ArrayList<>();
        if (forced.contains(step)) {
            why.add(new RerunReason.Forced());
        }
        if (was == null) {
            why.add(new RerunReason.NotRecorded());
        } else {
            for (InputKind kind : step.inputs()) {
                if (!now.inputDigests().get(kind).equals(was.inputDigests().get(kind))) {
                    why.add(new RerunReason.InputChanged(kind));
                }
            }
        }
        for (EngineStep upstream : plan.upstreamOf(step)) {
            if (upstream.kind() == StepKind.RESULT && !decided.get(upstream).isEmpty()) {
                why.add(new RerunReason.UpstreamReExecutes(upstream));
            }
        }
        if (why.isEmpty() && !now.value().equals(was.value())) {
            why.add(new RerunReason.FingerprintChanged());
        }
        return why;
    }

    private static List<RerunReason> preparationReasons(
            Plan plan,
            EngineStep step,
            Set<EngineStep> forced,
            Map<EngineStep, List<RerunReason>> decided) {
        List<RerunReason> why = new ArrayList<>();
        if (forced.contains(step)) {
            why.add(new RerunReason.Forced());
        }
        for (EngineStep downstream : plan.downstreamOf(step)) {
            if (!decided.get(downstream).isEmpty()) {
                why.add(new RerunReason.PrerequisiteOf(downstream));
            }
        }
        return why;
    }

    private static RerunDecision decide(EngineStep step, List<RerunReason> why) {
        if (step.kind() == StepKind.RESULT) {
            return why.isEmpty() ? RerunDecision.REUSE : RerunDecision.RE_EXECUTE;
        }
        return why.isEmpty() ? RerunDecision.NOT_NEEDED : RerunDecision.PREPARE;
    }

    /**
     * The plan this preview is of.
     *
     * @return the plan
     */
    public Plan plan() {
        return plan;
    }

    /**
     * The current fingerprint of every planned step: what the run records for each step once it
     * succeeds.
     *
     * @return an immutable map in plan order
     */
    public Map<EngineStep, StepFingerprint> fingerprints() {
        return Collections.unmodifiableMap(fingerprints);
    }

    /**
     * One verdict per planned step.
     *
     * @return an immutable list in plan order
     */
    public List<StepVerdict> verdicts() {
        return List.copyOf(verdicts);
    }

    /**
     * One planned step's verdict.
     *
     * @param step a planned step
     * @return its verdict
     * @throws IllegalArgumentException if the step is not planned, naming it
     */
    public StepVerdict verdict(EngineStep step) {
        Objects.requireNonNull(step, "step");
        for (StepVerdict verdict : verdicts) {
            if (verdict.step() == step) {
                return verdict;
            }
        }
        throw new IllegalArgumentException("step " + step.id() + " is not in the plan");
    }

    /**
     * The result steps that execute again.
     *
     * @return an immutable set in {@link EngineStep} order
     */
    public Set<EngineStep> reExecuted() {
        return stepsDecided(EnumSet.of(RerunDecision.RE_EXECUTE));
    }

    /**
     * The preparation steps that execute.
     *
     * @return an immutable set in {@link EngineStep} order
     */
    public Set<EngineStep> prepared() {
        return stepsDecided(EnumSet.of(RerunDecision.PREPARE));
    }

    /**
     * The result steps whose recorded outputs are reused.
     *
     * @return an immutable set in {@link EngineStep} order
     */
    public Set<EngineStep> reused() {
        return stepsDecided(EnumSet.of(RerunDecision.REUSE));
    }

    /**
     * Every step that executes: re-executed and prepared.
     *
     * @return an immutable set in {@link EngineStep} order; empty when there is nothing to run
     */
    public Set<EngineStep> executed() {
        return stepsDecided(EnumSet.of(RerunDecision.RE_EXECUTE, RerunDecision.PREPARE));
    }

    private Set<EngineStep> stepsDecided(Set<RerunDecision> decisions) {
        Set<EngineStep> steps = EnumSet.noneOf(EngineStep.class);
        for (StepVerdict verdict : verdicts) {
            if (decisions.contains(verdict.decision())) {
                steps.add(verdict.step());
            }
        }
        return Collections.unmodifiableSet(steps);
    }
}
