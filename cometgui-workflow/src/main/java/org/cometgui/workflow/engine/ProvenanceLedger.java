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
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.cometgui.provenance.manifest.ApplicationRecord;
import org.cometgui.provenance.manifest.FileRecord;
import org.cometgui.provenance.manifest.ProvenanceManifest;
import org.cometgui.provenance.manifest.RunRecord;
import org.cometgui.provenance.manifest.ToolRecord;
import org.cometgui.workflow.state.EngineStep;
import org.cometgui.workflow.state.Plan;

/**
 * Collects each step's tool and file records as the step ends, from whichever thread ends it, and
 * assembles them into one manifest in a deterministic order: steps in plan order, and within a step
 * the order the step made its invocations and declared its files. Neither the order steps finished
 * in nor the order processes exited in can reach the record.
 *
 * <p>A file is listed once per direction: a file one step writes and a later step reads appears as
 * an output and as an input, each once, with the first step's record.
 */
final class ProvenanceLedger {

    private final Object lock = new Object();

    /** Guarded by {@link #lock}. */
    private final Map<EngineStep, List<ToolRecord>> tools = new EnumMap<>(EngineStep.class);

    /** Guarded by {@link #lock}. */
    private final Map<EngineStep, List<FileRecord>> files = new EnumMap<>(EngineStep.class);

    void put(EngineStep step, List<ToolRecord> stepTools, List<FileRecord> stepFiles) {
        synchronized (lock) {
            tools.put(step, List.copyOf(stepTools));
            files.put(step, List.copyOf(stepFiles));
        }
    }

    ProvenanceManifest manifest(
            Plan plan, RunRecord run, ApplicationRecord application, Map<String, String> settings) {
        List<ToolRecord> allTools = new ArrayList<>();
        List<FileRecord> allFiles = new ArrayList<>();
        Set<String> listed = new HashSet<>();
        synchronized (lock) {
            for (EngineStep step : plan.steps()) {
                allTools.addAll(tools.getOrDefault(step, List.of()));
                for (FileRecord file : files.getOrDefault(step, List.of())) {
                    if (listed.add(file.direction().wireName() + " " + file.path())) {
                        allFiles.add(file);
                    }
                }
            }
        }
        return ProvenanceManifest.current(run, application, settings, allTools, allFiles);
    }
}
