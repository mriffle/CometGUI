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

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.cometgui.provenance.manifest.FileRecord;
import org.cometgui.provenance.manifest.ToolRecord;
import org.cometgui.workflow.state.EngineStep;
import org.cometgui.workflow.state.RerunPreview;

/**
 * What a run is about to execute and reuse, after every recorded result it would reuse has been
 * re-hashed against the recorded manifest ({@code R-RUN-01}, {@code R-RUN-02}, P8-14).
 *
 * <p>This is the preview the user sees before a run starts. It is {@link #accepted()} when every
 * file of every step the {@link #preview()} would reuse still has its recorded SHA-256 and every
 * invocation of those steps is recorded as completed. Otherwise it carries one {@link
 * ReuseMismatch} per problem, and {@link #offered()} is the preview recomputed with the affected
 * steps forced -- so the producing step, and everything downstream of it, executes again.
 */
public final class ReuseCheck {

    private final RerunPreview preview;

    private final List<ReuseMismatch> mismatches;

    private final Optional<RerunPreview> offered;

    private final Map<EngineStep, List<FileRecord>> verifiedFiles;

    private final Map<EngineStep, List<ToolRecord>> carriedTools;

    ReuseCheck(
            RerunPreview preview,
            List<ReuseMismatch> mismatches,
            Optional<RerunPreview> offered,
            Map<EngineStep, List<FileRecord>> verifiedFiles,
            Map<EngineStep, List<ToolRecord>> carriedTools) {
        this.preview = Objects.requireNonNull(preview, "preview");
        this.mismatches = List.copyOf(mismatches);
        this.offered = Objects.requireNonNull(offered, "offered");
        if (this.mismatches.isEmpty() == offered.isPresent()) {
            throw new IllegalArgumentException("a plan is offered exactly when reuse is refused");
        }
        this.verifiedFiles = copy(verifiedFiles);
        this.carriedTools = copy(carriedTools);
    }

    private static <T> Map<EngineStep, List<T>> copy(Map<EngineStep, List<T>> source) {
        Map<EngineStep, List<T>> copied = new EnumMap<>(EngineStep.class);
        for (Map.Entry<EngineStep, List<T>> entry : source.entrySet()) {
            copied.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Collections.unmodifiableMap(copied);
    }

    /**
     * The preview of the run as requested.
     *
     * @return what executes and what is reused if the check is accepted
     */
    public RerunPreview preview() {
        return preview;
    }

    /**
     * Whether every result the preview reuses was revalidated.
     *
     * @return {@code true} when there is nothing wrong
     */
    public boolean accepted() {
        return mismatches.isEmpty();
    }

    /**
     * Every problem found, in plan order and, within a step, declaration order.
     *
     * @return an immutable list; empty exactly when {@link #accepted()}
     */
    public List<ReuseMismatch> mismatches() {
        return List.copyOf(mismatches);
    }

    /**
     * The preview recomputed with every step that has a problem forced to execute.
     *
     * @return the offered preview; empty when accepted
     */
    public Optional<RerunPreview> offered() {
        return offered;
    }

    /**
     * The steps the offered plan forces, to pass to {@link RunRequest#withForced}.
     *
     * @return an immutable set in {@link EngineStep} order; empty when accepted
     */
    public Set<EngineStep> offeredForced() {
        Set<EngineStep> forced = EnumSet.noneOf(EngineStep.class);
        for (ReuseMismatch mismatch : mismatches) {
            forced.add(mismatch.step());
        }
        return Collections.unmodifiableSet(forced);
    }

    /**
     * The check in words: every problem, then the steps that must run again.
     *
     * @return a message for the user
     */
    public String message() {
        if (accepted()) {
            List<String> reused = new ArrayList<>();
            for (EngineStep step : preview.reused()) {
                reused.add(step.id());
            }
            return reused.isEmpty()
                    ? "nothing recorded is reused"
                    : "every recorded result to be reused still matches its record: "
                            + String.join(", ", reused);
        }
        StringBuilder text =
                new StringBuilder("recorded results cannot be reused because they no longer")
                        .append(" match the record:");
        for (ReuseMismatch mismatch : mismatches) {
            text.append("\n- ").append(mismatch.describe());
        }
        List<String> again = new ArrayList<>();
        for (EngineStep step : offered.orElseThrow().reExecuted()) {
            again.add(step.id());
        }
        return text.append("\nthese steps must run again: ")
                .append(String.join(", ", again))
                .toString();
    }

    Map<EngineStep, List<FileRecord>> verifiedFiles() {
        return verifiedFiles;
    }

    Map<EngineStep, List<ToolRecord>> carriedTools() {
        return carriedTools;
    }
}
