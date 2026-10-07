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

package org.cometgui.ui.viewmodel.params;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import org.cometgui.params.comet.validation.ValidationReport;
import org.cometgui.ui.viewmodel.NonNullProperty;

/**
 * Whether a run may start, and why not, for the Run section's Run control (decisions P7-6, P8-16).
 *
 * <p>Two independent halves, both stated in text:
 *
 * <ul>
 *   <li><strong>The parameters.</strong> {@link #parametersBlockRun()} is true exactly when a field
 *       holds an edit the model refused -- the screen then shows a value that is not the one that
 *       would be searched, which is view-model state, not a scientific rule -- or when the
 *       session's one report holds an error, including an unresolved entry of a migration under
 *       review, which that report carries as an error ({@code R-PARAM-13}). Each error is a
 *       blocking reason (Phase 07 exit gate item 6 asserts this half).
 *   <li><strong>The workflow engine.</strong> Phase 08's engine says why it cannot run the
 *       configuration: no Comet of the release installed, the pre-run check's problems with the
 *       files and the validator's errors over the file-system facts (the decoy blocks of {@code
 *       R-DEC-02} and the index refusal among them), that the check is still running, that a run is
 *       in progress, or that nothing would run. {@link RunViewModel} works those out off the JavaFX
 *       thread and puts them here with {@link #showEngineReasons}; until it first has, the engine's
 *       half says the check has not run, so Run is never enabled on an answer nobody computed.
 * </ul>
 *
 * <p>The Run control is enabled only when neither half has a reason.
 */
public final class RunReadinessViewModel {

    /** The engine's reason before the first pre-run check has answered. */
    public static final String ENGINE_NOT_CHECKED =
            "The pre-run check has not run yet, so the workflow engine has not said whether it"
                    + " can run this search.";

    private final ParameterSession session;

    private final ReadOnlyBooleanWrapper parametersBlockRun;

    private final NonNullProperty<List<String>> blockingReasons;

    private final NonNullProperty<List<String>> engineReasons;

    private final ReadOnlyBooleanWrapper runEnabled;

    private final NonNullProperty<String> reasonsText;

    /**
     * Run readiness following a session's report, with the engine's half saying the pre-run check
     * has not run yet ({@link #ENGINE_NOT_CHECKED}).
     *
     * @param session the session
     */
    public RunReadinessViewModel(ParameterSession session) {
        this.session = Objects.requireNonNull(session, "session");
        this.parametersBlockRun = new ReadOnlyBooleanWrapper(this, "parametersBlockRun", false);
        this.blockingReasons = new NonNullProperty<>(this, "blockingReasons", List.of());
        this.engineReasons =
                new NonNullProperty<>(this, "engineReasons", List.of(ENGINE_NOT_CHECKED));
        this.runEnabled = new ReadOnlyBooleanWrapper(this, "runEnabled", false);
        this.reasonsText = new NonNullProperty<>(this, "reasonsText", "");
        show(session.report());
        session.reportProperty().addListener((observable, before, after) -> show(after));
        session.pendingRefusalsProperty()
                .addListener((observable, before, after) -> show(session.report()));
    }

    /**
     * Whether the parameters block a run: a field holds a refused edit, or the report holds an
     * error.
     *
     * @return the read-only property
     */
    public ReadOnlyBooleanProperty parametersBlockRunProperty() {
        return parametersBlockRun.getReadOnlyProperty();
    }

    /**
     * Whether the parameters block a run.
     *
     * @return {@code true} if a refused edit is pending or the report holds an error
     */
    public boolean parametersBlockRun() {
        return parametersBlockRun.get();
    }

    /**
     * Why the parameters block a run: one line per field holding a refused edit, in field order,
     * naming the field and the model's refusal; then one per error of the report, in its order.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<List<String>> blockingReasonsProperty() {
        return blockingReasons.getReadOnlyProperty();
    }

    /**
     * Why the parameters block a run.
     *
     * @return the reasons, empty when they do not
     */
    public List<String> blockingReasons() {
        return blockingReasons.get();
    }

    /**
     * Why the workflow engine cannot run this configuration now, one sentence each.
     *
     * @return the read-only property; an empty list when it can
     */
    public ReadOnlyObjectProperty<List<String>> engineReasonsProperty() {
        return engineReasons.getReadOnlyProperty();
    }

    /**
     * Why the workflow engine cannot run this configuration now.
     *
     * @return the reasons, empty when it can
     */
    public List<String> engineReasons() {
        return engineReasons.get();
    }

    /**
     * Replaces the engine's half. Called by {@link RunViewModel}, on the interface thread, with
     * what the engine said.
     *
     * @param reasons why the engine cannot run the configuration; empty when it can
     * @throws NullPointerException if the list or a reason is {@code null}
     * @throws IllegalArgumentException if a reason is blank: the Run control would be disabled with
     *     no explanation
     */
    public void showEngineReasons(List<String> reasons) {
        List<String> copy = List.copyOf(reasons);
        for (String reason : copy) {
            if (reason.isBlank()) {
                throw new IllegalArgumentException(
                        "a workflow engine that cannot run has to say why: a blank reason leaves"
                                + " the Run control disabled with no explanation");
            }
        }
        engineReasons.set(copy);
        show(session.report());
    }

    /**
     * Whether the Run control is enabled: no parameter error and nothing against it from the
     * engine.
     *
     * @return the read-only property
     */
    public ReadOnlyBooleanProperty runEnabledProperty() {
        return runEnabled.getReadOnlyProperty();
    }

    /**
     * Whether the Run control is enabled.
     *
     * @return {@code true} if a run may start
     */
    public boolean runEnabled() {
        return runEnabled.get();
    }

    /**
     * Every reason a run cannot start, the parameters' first, one per line.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<String> reasonsTextProperty() {
        return reasonsText.getReadOnlyProperty();
    }

    /**
     * Every reason a run cannot start.
     *
     * @return the reasons, one per line, or {@code Ready to run.}
     */
    public String reasonsText() {
        return reasonsText.get();
    }

    private void show(ValidationReport report) {
        List<String> reasons = new ArrayList<>();
        for (SummaryEntry entry : SummaryEntry.listOf(report, session)) {
            if (entry.kind().blocksRun()) {
                reasons.add(entry.text());
            }
        }
        boolean blocked = !reasons.isEmpty();
        parametersBlockRun.set(blocked);
        blockingReasons.set(List.copyOf(reasons));
        List<String> engine = engineReasons.get();
        List<String> all = new ArrayList<>(reasons);
        all.addAll(engine);
        reasonsText.set(all.isEmpty() ? "Ready to run." : String.join("\n", all));
        runEnabled.set(!blocked && engine.isEmpty());
    }
}
