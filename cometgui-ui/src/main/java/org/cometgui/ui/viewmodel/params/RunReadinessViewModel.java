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
import java.util.Optional;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import org.cometgui.params.comet.validation.ValidationReport;
import org.cometgui.ui.viewmodel.NonNullProperty;

/**
 * Whether a run may start, and why not, for the Run section's Run control (decision P7-6).
 *
 * <p>Two independent halves, both stated in text:
 *
 * <ul>
 *   <li><strong>The parameters.</strong> {@link #parametersBlockRun()} is true exactly when a field
 *       holds an edit the model refused -- the screen then shows a value that is not the one that
 *       would be searched, which is view-model state, not a scientific rule -- or when the
 *       session's one report holds an error, including an unresolved entry of a migration under
 *       review, which that report carries as an error ({@code R-PARAM-13}). Each error is a
 *       blocking reason (exit gate item 6 asserts this half).
 *   <li><strong>The workflow engine.</strong> Phase 08 builds the engine that runs Comet and
 *       Percolator. Until the composition root says otherwise, a separate reason is always present,
 *       so the Run control never pretends it could start a run.
 * </ul>
 *
 * <p>The Run control is enabled only when neither half has a reason.
 */
public final class RunReadinessViewModel {

    /** The reason no run can start before the workflow engine exists. */
    public static final String ENGINE_NOT_BUILT =
            "No run can start yet: the workflow engine that runs Comet and Percolator arrives in"
                    + " Phase 08.";

    private final ParameterSession session;

    private final Optional<String> engineUnavailable;

    private final ReadOnlyBooleanWrapper parametersBlockRun;

    private final NonNullProperty<List<String>> blockingReasons;

    private final ReadOnlyBooleanWrapper runEnabled;

    private final NonNullProperty<String> reasonsText;

    /**
     * Run readiness following a session's report.
     *
     * @param session the session
     * @param engineUnavailable why the workflow engine cannot run anything, or empty when it can;
     *     the composition root passes {@link #ENGINE_NOT_BUILT} until Phase 08
     * @throws IllegalArgumentException if the engine's reason is blank
     */
    public RunReadinessViewModel(ParameterSession session, Optional<String> engineUnavailable) {
        this.session = Objects.requireNonNull(session, "session");
        this.engineUnavailable = Objects.requireNonNull(engineUnavailable, "engineUnavailable");
        if (engineUnavailable.filter(String::isBlank).isPresent()) {
            throw new IllegalArgumentException(
                    "a workflow engine that cannot run has to say why: a blank reason leaves the"
                            + " Run control disabled with no explanation");
        }
        this.parametersBlockRun = new ReadOnlyBooleanWrapper(this, "parametersBlockRun", false);
        this.blockingReasons = new NonNullProperty<>(this, "blockingReasons", List.of());
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
     * Why the workflow engine cannot run anything.
     *
     * @return the reason, or empty when it can
     */
    public Optional<String> engineReason() {
        return engineUnavailable;
    }

    /**
     * Whether the Run control is enabled: no parameter error and an engine that can run.
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
        runEnabled.set(!blocked && engineUnavailable.isEmpty());
        List<String> all = new ArrayList<>(reasons);
        engineUnavailable.ifPresent(all::add);
        reasonsText.set(all.isEmpty() ? "Ready to run." : String.join("\n", all));
    }
}
