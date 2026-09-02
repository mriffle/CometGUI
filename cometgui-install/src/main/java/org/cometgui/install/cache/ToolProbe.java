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
import java.util.Set;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.install.registry.ArtefactRecord;

/**
 * Step 6 of the atomic install: run the thing and find out what it is.
 *
 * <p><strong>There is no implementation in this package and none in this work unit</strong>, and
 * that is the point of declaring it here. The pipeline needs a place to ask "does this build load,
 * is it the version it claims, and what can it do?" before the payload becomes a cache entry;
 * building the answer is somebody else's work. Phase 05 <strong>unit 6</strong> implements
 * loadability and identity together with the {@code R-PLAT-03} diagnostic, and <strong>unit
 * 7</strong> implements the functional capability probe in {@code org.cometgui.tools}. This is the
 * same shape {@link org.cometgui.domain.ports.Downloader}, {@link
 * org.cometgui.domain.ports.HashService} and {@link org.cometgui.domain.ports.ProcessRunner} were
 * declared in, one phase before the phase that implemented them.
 *
 * <p><strong>Why it is here and not in the domain.</strong> {@link
 * org.cometgui.domain.tools.ToolManager} is the seam the user interface is allowed to see, and it
 * speaks in offers and versions. This one takes an {@link ArtefactRecord}, which lives in the
 * installer, so it stays in the installer.
 *
 * <h2>What the pipeline guarantees the implementation</h2>
 *
 * <p>It is called on a <strong>staged</strong> directory, before anything reaches the tool cache,
 * and only after the SHA-256 has been verified, the layout has been checked and the executable bit
 * has been set. So {@code R-SEC-02}'s "verification is mandatory before an executable is launched"
 * is satisfied by the order of the steps rather than by anything the implementation remembers to do
 * -- and a corrupted download is refused two steps earlier and never arrives here at all.
 *
 * <h2>What the implementation owes the pipeline</h2>
 *
 * <p>Return the capabilities the build was <em>observed</em> to have. Refusing is throwing: {@code
 * R-TOOL-06} says a tool that fails loadability is never offered, so a probe that cannot confirm
 * the build throws and the install stops before the move. A richer failure type -- the loader
 * diagnostic, the three ordered probe stages -- belongs to the unit that builds it and may be an
 * {@link IOException} subclass; nothing here needs to know about it.
 */
@FunctionalInterface
public interface ToolProbe {

    /**
     * Probes a staged install.
     *
     * @param record the manifest record the artefact was installed from
     * @param stagedDirectory the directory holding the extracted, laid-out, fixed-up files, which
     *     is <em>not</em> the final cache directory
     * @return the capabilities the build was observed to have, possibly empty
     * @throws IOException if the build cannot be probed, or fails a stage {@code R-TOOL-06}
     *     requires it to pass
     * @throws NullPointerException if either argument is {@code null}
     */
    Set<ToolCapability> probe(ArtefactRecord record, Path stagedDirectory) throws IOException;
}
