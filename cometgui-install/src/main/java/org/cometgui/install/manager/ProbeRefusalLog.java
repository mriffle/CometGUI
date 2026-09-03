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
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.cometgui.domain.tools.ProbeFailureKind;
import org.cometgui.domain.tools.ProbeStage;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.install.cache.ToolProbe;
import org.cometgui.install.probe.ProbeFailedException;
import org.cometgui.install.registry.ArtefactRecord;

/**
 * Which builds this process has watched fail to load, so that {@code R-TOOL-06}'s last sentence
 * survives the install that discovered it.
 *
 * <p>{@code R-TOOL-06} says <em>a tool that fails loadability shall never be offered for
 * selection</em>, and {@code R-PERC-01} says a build is presented as a one-click install only once
 * "its post-install runtime probe has passed on that platform". Both are claims about what happens
 * <strong>after</strong> an install has run the binary -- and the install that ran it throws the
 * refusal away as it unwinds. This is where the fact is kept.
 *
 * <h2>Why it wraps the probe rather than reading the exception the installer threw</h2>
 *
 * <p>Because of the order in which the two happen. {@code ArtefactInstaller} reports the terminal
 * {@link org.cometgui.domain.tools.InstallPhase} from a {@code finally} block, so a caller told
 * "this install failed" can ask for the offers again <em>before</em> any code catching that
 * exception has run. A refusal recorded from the catch would therefore be missing from the very
 * first list the user sees after the failure -- the build would be offered once more, exactly the
 * sentence {@code R-TOOL-06} forbids, and only for a moment, which is the hardest kind of defect to
 * find. Recording it inside step 6 puts it in the log four steps before the terminal report exists.
 *
 * <h2>Only a loadability refusal is remembered</h2>
 *
 * <p>An identity or capability refusal is a different sentence. "This binary says it is 3.06.5 and
 * the manifest pinned 3.07.1" and "this build cannot write XML" are not reasons to stop offering a
 * build that starts; {@code R-TOOL-06} withholds the offer for the first stage only, and unit 1's
 * rule that an ambiguous kind takes the earliest stage is what makes {@link ProbeStage#LOADABILITY}
 * the answer to "did we fail to establish that it starts".
 *
 * <p>A build that later probes successfully is <strong>forgotten</strong>: a machine that has had a
 * missing library installed since the last attempt is a machine where the build now runs, and a log
 * that only ever grew would keep telling the user otherwise.
 *
 * <p>Safe for concurrent use: an install runs on its own thread and the offers are read from
 * another.
 */
public final class ProbeRefusalLog {

    /** Keyed by download URL, which is how this product asks "is this the same build?". */
    private final Map<URI, ProbeFailureKind> refusals = new ConcurrentHashMap<>();

    /**
     * Wraps the probe install step 6 runs, so that a loadability refusal is recorded as it happens.
     *
     * @param delegate the probe that does the work, in production {@code
     *     org.cometgui.install.probe.StagedToolProbe}
     * @return a probe that behaves identically and remembers what it refused
     * @throws NullPointerException if {@code delegate} is {@code null}
     */
    public ToolProbe recording(ToolProbe delegate) {
        Objects.requireNonNull(delegate, "delegate");
        return (record, stagedDirectory) -> record(delegate, record, stagedDirectory);
    }

    private Set<ToolCapability> record(
            ToolProbe delegate, ArtefactRecord record, Path stagedDirectory) throws IOException {
        try {
            Set<ToolCapability> capabilities = delegate.probe(record, stagedDirectory);
            refusals.remove(record.url());
            return capabilities;
        } catch (ProbeFailedException refused) {
            if (refused.stage() == ProbeStage.LOADABILITY) {
                refusals.put(record.url(), refused.kind());
            }
            throw refused;
        }
    }

    /**
     * What was watched happen to this build's binary, if anything.
     *
     * @param record the artefact
     * @return the loadability failure the last probe of this download reported, or empty if the
     *     last one passed or none has run
     * @throws NullPointerException if {@code record} is {@code null}
     */
    public Optional<ProbeFailureKind> refusalFor(ArtefactRecord record) {
        Objects.requireNonNull(record, "record");
        return Optional.ofNullable(refusals.get(record.url()));
    }

    /**
     * How many builds are remembered as having failed to load.
     *
     * @return the number of recorded refusals
     */
    public int size() {
        return refusals.size();
    }

    /**
     * Describes the log by what it is holding.
     *
     * @return a description for a log line or an exception message
     */
    @Override
    public String toString() {
        return "ProbeRefusalLog[" + refusals.size() + " build(s) refused]";
    }
}
