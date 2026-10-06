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

import java.time.Clock;
import java.util.Objects;
import org.cometgui.domain.ports.ProcessRunner;
import org.cometgui.domain.secrets.SecretRedactor;
import org.cometgui.provenance.hashing.CachingHashService;
import org.cometgui.tools.process.RunMessageSink;

/**
 * The collaborators the engine is built from, all injected ({@code R-PROC-01}).
 *
 * <p>There is one of each (design decision P8-1): one process runner, behind the process service's
 * {@code StageRunner}; one hasher; one rule set for removing secrets, which the engine hands to the
 * process service, the event log, the manifest writer and the report writer alike, so that a value
 * cannot be redacted in one and not in another.
 *
 * <p>The hasher is the caching one, by type, on purpose. The engine records files through {@link
 * CachingHashService#hash}, which may serve a revalidated entry, and re-hashes a recorded file it
 * is about to <em>reuse</em> through {@link CachingHashService#rehash}, which never serves from the
 * cache (P8-14, {@code R-PROV-02} "bypassable"). Taking the interface would leave that bypass to
 * the wiring; taking this type makes it a property of the engine. A deployment that wants no cache
 * passes {@link CachingHashService#disabled}.
 */
public final class EngineServices {

    private final ProcessRunner processes;

    private final Clock clock;

    private final SecretRedactor redactor;

    private final RunMessageSink sink;

    private final CachingHashService hashes;

    private final int availableCores;

    private final int invocationCap;

    /**
     * Validates and keeps the collaborators.
     *
     * @param processes the process runner, normally {@code ProcessService}
     * @param clock the clock every state change, event and record is dated by
     * @param redactor the one rule set for secrets
     * @param sink where each tool output line goes as it arrives -- the console
     * @param hashes the one hasher
     * @param availableCores the processors the bounded concurrency divides among invocations; at
     *     least one
     * @param invocationCap the explicit cap on concurrent invocations of one step (P8-15); at least
     *     one
     * @throws NullPointerException naming a reference that is {@code null}
     * @throws IllegalArgumentException if {@code availableCores} or {@code invocationCap} is less
     *     than one
     */
    public EngineServices(
            ProcessRunner processes,
            Clock clock,
            SecretRedactor redactor,
            RunMessageSink sink,
            CachingHashService hashes,
            int availableCores,
            int invocationCap) {
        this.processes = Objects.requireNonNull(processes, "processes");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.redactor = Objects.requireNonNull(redactor, "redactor");
        this.sink = Objects.requireNonNull(sink, "sink");
        this.hashes = Objects.requireNonNull(hashes, "hashes");
        if (availableCores < 1) {
            throw new IllegalArgumentException(
                    "availableCores must be at least 1, but was " + availableCores);
        }
        if (invocationCap < 1) {
            throw new IllegalArgumentException(
                    "invocationCap must be at least 1, but was " + invocationCap);
        }
        this.availableCores = availableCores;
        this.invocationCap = invocationCap;
    }

    ProcessRunner processes() {
        return processes;
    }

    Clock clock() {
        return clock;
    }

    SecretRedactor redactor() {
        return redactor;
    }

    RunMessageSink sink() {
        return sink;
    }

    CachingHashService hashes() {
        return hashes;
    }

    /**
     * The processors the bounded concurrency divides among invocations.
     *
     * @return at least one
     */
    public int availableCores() {
        return availableCores;
    }

    /**
     * The explicit cap on concurrent invocations of one step.
     *
     * @return at least one
     */
    public int invocationCap() {
        return invocationCap;
    }
}
