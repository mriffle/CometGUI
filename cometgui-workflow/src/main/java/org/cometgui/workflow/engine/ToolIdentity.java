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

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.cometgui.domain.ports.FileHashes;

/**
 * The tool an invocation runs, as provenance records it: everything in a {@code ToolRecord} except
 * the stage and the execution, which the engine supplies.
 *
 * <p>The step that resolved the tool knows these facts; the engine knows how the tool was run.
 * Keeping the two apart is what lets the engine stay ignorant of which tool it is running.
 *
 * @param name the tool's logical name, for example {@code comet}
 * @param version the reported version
 * @param releaseTag the release tag or commit, when known
 * @param executablePath the executable or JAR; absolute
 * @param hashes the executable's MD5 and SHA-256
 * @param managed whether CometGUI installed it, rather than the user registering a local copy
 * @param artefactIdentity the upstream or managed artefact identity, when known
 * @param capabilities the probed capabilities
 * @param warnings advisories active for this version
 */
public record ToolIdentity(
        String name,
        String version,
        Optional<String> releaseTag,
        Path executablePath,
        FileHashes hashes,
        boolean managed,
        Optional<String> artefactIdentity,
        Set<String> capabilities,
        List<String> warnings) {

    /**
     * Copies the collections. Every other rule is the provenance record's, applied when the record
     * is built, so the two can never disagree about what a valid tool is.
     *
     * @throws NullPointerException if a collection or an element is {@code null}
     */
    public ToolIdentity {
        capabilities = Set.copyOf(capabilities);
        warnings = List.copyOf(warnings);
    }

    @Override
    public Set<String> capabilities() {
        return Set.copyOf(capabilities);
    }

    @Override
    public List<String> warnings() {
        return List.copyOf(warnings);
    }
}
