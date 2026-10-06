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
import java.util.Objects;
import org.cometgui.provenance.manifest.FileDirection;

/**
 * One file a step reads or writes, declared before the step runs.
 *
 * <p>A step declares its files rather than reporting them afterwards, for two reasons. When the
 * step executes, the engine hashes every declared file once the step's processes have exited and
 * records it in provenance -- an output that is declared but missing fails a step that reported
 * success, so no step can drop a required output from the record ({@code R-TEST-02}). And when the
 * step is <em>reused</em> in a retry, the declaration is what the engine re-hashes against the
 * recorded manifest before trusting it ({@code R-RUN-02}, P8-14).
 *
 * @param direction whether the step reads or writes the file
 * @param role what the file is to the run -- {@code spectrum}, {@code pepxml} and so on; the {@code
 *     role} of the provenance file record
 * @param path the file; absolute, and normalised by this constructor
 */
public record DeclaredFile(FileDirection direction, String role, Path path) {

    /**
     * Validates and normalises.
     *
     * @throws NullPointerException naming a component that is {@code null}
     * @throws IllegalArgumentException if the role is blank or the path is relative, quoting it
     */
    public DeclaredFile {
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(role, "role");
        if (role.isBlank()) {
            throw new IllegalArgumentException("a declared file needs a role");
        }
        Objects.requireNonNull(path, "path");
        if (!path.isAbsolute()) {
            throw new IllegalArgumentException("a declared file must be absolute: " + path);
        }
        path = path.normalize();
    }

    /**
     * A file the step reads.
     *
     * @param role its role
     * @param path the file
     * @return the declaration
     */
    public static DeclaredFile input(String role, Path path) {
        return new DeclaredFile(FileDirection.INPUT, role, path);
    }

    /**
     * A file the step writes.
     *
     * @param role its role
     * @param path the file
     * @return the declaration
     */
    public static DeclaredFile output(String role, Path path) {
        return new DeclaredFile(FileDirection.OUTPUT, role, path);
    }
}
