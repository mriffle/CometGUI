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

package org.cometgui.install.cache;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.install.registry.ArtefactRecord;

/**
 * A probe that records every call, and one that fails the test if it is called at all.
 *
 * <p>{@link #refusingToBeCalled()} is how phase 05's exit gate item 2 is served at the installer:
 * <em>"a corrupted download is rejected and the tool is never executed; the test asserts no process
 * was launched."</em> Probing is the only route from this installer to a process, so a probe that
 * throws the moment it is entered turns "the tool was never executed" into an assertion rather than
 * a hope. The recorded call count is asserted as well, because a stub that was never wired in would
 * also never be called.
 */
final class RecordingProbe implements ToolProbe {

    /** Every directory this probe was asked to look at, in order. */
    private final List<Path> calls = new ArrayList<>();

    /** What the probe answers. */
    private final Set<ToolCapability> capabilities;

    /** What the probe throws instead of answering, or {@code null}. */
    private final IOException refusal;

    /** True when being called at all is a test failure. */
    private final boolean mustNotBeCalled;

    private RecordingProbe(
            Set<ToolCapability> capabilities, IOException refusal, boolean mustNotBeCalled) {
        this.capabilities = capabilities;
        this.refusal = refusal;
        this.mustNotBeCalled = mustNotBeCalled;
    }

    /**
     * A probe that confirms the given capabilities.
     *
     * @param capabilities what it answers
     * @return the probe
     */
    static RecordingProbe confirming(ToolCapability... capabilities) {
        return new RecordingProbe(Set.of(capabilities), null, false);
    }

    /**
     * A probe that refuses the build.
     *
     * @param message the refusal, standing in for units 6 and 7's loader diagnostic
     * @return the probe
     */
    static RecordingProbe refusing(String message) {
        return new RecordingProbe(Set.of(), new IOException(message), false);
    }

    /**
     * A probe whose being called is itself the failure.
     *
     * @return the probe
     */
    static RecordingProbe refusingToBeCalled() {
        return new RecordingProbe(Set.of(), null, true);
    }

    /**
     * How many times the probe was entered.
     *
     * @return the call count
     */
    int callCount() {
        return calls.size();
    }

    /**
     * The directories the probe was given.
     *
     * @return the calls, in order
     */
    List<Path> calls() {
        return List.copyOf(calls);
    }

    @Override
    public Set<ToolCapability> probe(ArtefactRecord record, Path stagedDirectory)
            throws IOException {
        if (mustNotBeCalled) {
            throw new AssertionError(
                    "the probe was called for "
                            + record.describe()
                            + " at "
                            + stagedDirectory
                            + ", and this test exists to prove it is not: a corrupted artefact must"
                            + " be rejected before anything can execute it (R-SEC-02, gate"
                            + " item 2)");
        }
        calls.add(stagedDirectory);
        if (refusal != null) {
            throw refusal;
        }
        return Set.copyOf(capabilities);
    }
}
