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

package org.cometgui.install.manager;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.cometgui.domain.tools.ProbeFailureKind;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.install.cache.ToolProbe;
import org.cometgui.install.probe.ProbeFailedException;
import org.cometgui.install.registry.ArtefactRecord;

/**
 * Install step 6, scripted, so that the Tool Manager's behaviour after a probe can be graded
 * without a real binary for every case.
 *
 * <p>The real three-stage probe is unit 6's and unit 7's and is exercised end to end from {@code
 * cometgui-app}, where the tool adapters are visible. What this class is for is the other half:
 * what the Tool Manager does with a refusal, which needs a refusal of every stage on demand and a
 * host that cannot produce one.
 *
 * <p><strong>It also records every call</strong>, which is how a test proves a probe was never
 * reached -- gate item 2's second half: a corrupted download is rejected at step 2 and the binary
 * is never executed.
 */
final class ScriptedProbe implements ToolProbe {

    /** Every artefact this probe was asked about, in order, by {@code describe()}. */
    private final List<String> probed = Collections.synchronizedList(new ArrayList<>());

    /** What to throw instead of answering, by download URL. */
    private final Map<URI, Supplier<ProbeFailedException>> refusals = new ConcurrentHashMap<>();

    /** What each tool's build is said to be able to do. */
    private final Map<ToolName, Set<ToolCapability>> capabilities = new EnumMap<>(ToolName.class);

    /**
     * Says what a tool's build will be observed to do.
     *
     * @param tool the tool
     * @param observed the capabilities to answer with
     * @return this probe
     */
    ScriptedProbe observing(ToolName tool, Set<ToolCapability> observed) {
        capabilities.put(tool, Set.copyOf(observed));
        return this;
    }

    /**
     * Makes this probe refuse one build, at a chosen stage.
     *
     * @param record the artefact whose build is refused
     * @param kind what went wrong; its stage decides whether the build stops being offered
     * @param message the whole diagnostic
     * @return this probe
     */
    ScriptedProbe refusing(ArtefactRecord record, ProbeFailureKind kind, String message) {
        refusals.put(record.url(), () -> new ProbeFailedException(kind, message));
        return this;
    }

    /**
     * Stops refusing one build, so that a second attempt can be watched succeed.
     *
     * @param record the artefact
     * @return this probe
     */
    ScriptedProbe stopRefusing(ArtefactRecord record) {
        refusals.remove(record.url());
        return this;
    }

    /**
     * Every artefact this probe was asked about, in order.
     *
     * @return the log, by {@code ArtefactRecord.describe()}
     */
    List<String> probed() {
        return List.copyOf(probed);
    }

    @Override
    public Set<ToolCapability> probe(ArtefactRecord record, Path stagedDirectory)
            throws IOException {
        probed.add(record.describe());
        Supplier<ProbeFailedException> refusal = refusals.get(record.url());
        if (refusal != null) {
            throw refusal.get();
        }
        return capabilities.getOrDefault(record.tool(), Set.of());
    }
}
