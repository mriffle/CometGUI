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

package org.cometgui.tools.percolator;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Percolator was not started, and why: the build cannot write the results the run needs ({@link
 * PercolatorCommands}), or the merged PIN fails the check before launch ({@link
 * PercolatorPinCheck}). The message is the whole explanation, written for the scientist, and names
 * the file it is about when there is one.
 */
public final class PercolatorRefusedException extends Exception {

    private static final long serialVersionUID = 1L;

    /** The file the refusal is about, or {@code null}. */
    private final transient Path file;

    /**
     * A refusal.
     *
     * @param message the whole message
     * @param file the file it is about, or {@code null}
     * @param cause what was refused underneath, or {@code null}
     */
    PercolatorRefusedException(String message, Path file, Throwable cause) {
        super(Objects.requireNonNull(message, "message"), cause);
        this.file = file;
    }

    /**
     * The file the refusal is about.
     *
     * @return the merged PIN for a PIN refusal; empty for a refusal of the build
     */
    public Optional<Path> file() {
        return Optional.ofNullable(file);
    }
}
