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

package org.cometgui.workflow.storage;

import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Thrown when something written once is about to be written again ({@code R-RUN-06}): a second
 * {@code run.json} identity for a run, a second {@code project.json} for a project.
 *
 * <p>A {@link FileAlreadyExistsException}, because that is what it is, and because {@code
 * CanonicalParamsWriter.writeOnce} -- the one writer of a run's {@code parameters/comet.params} --
 * throws exactly that type when its target exists, so a caller handles "already written" once for
 * both. The file is never touched: the refusal happens before anything is opened for writing.
 */
public final class AlreadyWrittenException extends FileAlreadyExistsException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the refusal.
     *
     * @param file the file that already exists
     * @param reason why it may not be written again
     */
    AlreadyWrittenException(Path file, String reason) {
        super(Objects.requireNonNull(file, "file").toString(), null, reason);
    }
}
