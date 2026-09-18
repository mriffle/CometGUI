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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.install.cache.ToolProbe;
import org.cometgui.install.registry.ArtefactRecord;

/**
 * Step 6 of the install, as a double, so that what the Tool Manager shows about an installed build
 * can be told apart from what the manifest declared.
 *
 * <p>{@code R-TOOL-07} says the probe wins where the two disagree, and a probe that answered the
 * manifest's own list would make that rule unobservable. So {@link #answering} is given a set the
 * manifest does not declare, and the row afterwards has to carry the probe's answer.
 *
 * <p>The real three-stage probe is units 6 and 7's and is graded there against real binaries; this
 * unit is about what the Tool Manager does with its answer.
 */
final class FixedProbe implements ToolProbe {

    private final Set<ToolCapability> capabilities;
    private final boolean mustNotBeCalled;
    private final List<Path> calls = new CopyOnWriteArrayList<>();

    private FixedProbe(Set<ToolCapability> capabilities, boolean mustNotBeCalled) {
        this.capabilities = capabilities;
        this.mustNotBeCalled = mustNotBeCalled;
    }

    /**
     * A probe that answers with these capabilities.
     *
     * @param capabilities what it answers
     * @return the probe
     */
    static FixedProbe answering(ToolCapability... capabilities) {
        return new FixedProbe(Set.of(capabilities), false);
    }

    /**
     * A probe whose being called at all is the failure.
     *
     * @return the probe
     */
    static FixedProbe refusingToBeCalled() {
        return new FixedProbe(Set.of(), true);
    }

    /**
     * How many times step 6 was entered.
     *
     * @return the call count
     */
    int callCount() {
        return calls.size();
    }

    /**
     * The staged directories it was given.
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
                    "step 6 was entered for "
                            + record.describe()
                            + " at "
                            + stagedDirectory
                            + ", and this test exists to prove it is not: an artefact whose"
                            + " SHA-256 does not match the manifest must be refused before"
                            + " anything can execute it (R-SEC-02, gate item 2)");
        }
        calls.add(stagedDirectory);
        if (!Files.isRegularFile(stagedDirectory.resolve(record.executablePath()))) {
            throw new IOException(
                    "step 6 was given "
                            + stagedDirectory
                            + ", which holds no "
                            + record.executablePath());
        }
        return Set.copyOf(capabilities);
    }
}
