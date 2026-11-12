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

package org.cometgui.tools.comet;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * A PIN merge wrote a file that does not hold exactly the rows it read ({@code R-CMT-06}): rows
 * were lost, or gained, between the per-file PINs and the merged one.
 *
 * <p>{@link PinMerger} counts the data rows of the file it wrote by reading that file back from the
 * disk, and compares the count with the number of rows it read from the inputs. A difference is
 * this exception, and the merged file is not left behind: Percolator trained on a PIN that is
 * missing rows gives results that look complete and are not.
 */
public final class PinMergeRowCountException extends IOException {

    private static final long serialVersionUID = 1L;

    /** The merged file that was not produced. */
    private final transient Path output;

    private final long rowsRead;

    private final long rowsWritten;

    /**
     * A merge whose written rows are not its read rows.
     *
     * @param output the merged file the rows were being written for
     * @param inputs how many per-file PINs were merged
     * @param rowsRead the data rows read from the inputs
     * @param rowsWritten the data rows counted in the file that was written
     */
    PinMergeRowCountException(Path output, int inputs, long rowsRead, long rowsWritten) {
        super(
                "the merged PIN file "
                        + Objects.requireNonNull(output, "output")
                        + " was not written completely: "
                        + rowsRead
                        + (rowsRead == 1 ? " data row was" : " data rows were")
                        + " read from the "
                        + inputs
                        + (inputs == 1 ? " per-file PIN" : " per-file PINs")
                        + ", but the file that was written holds "
                        + rowsWritten
                        + ". The merged file was discarded, so Percolator is not run on an"
                        + " incomplete PIN");
        this.output = output;
        this.rowsRead = rowsRead;
        this.rowsWritten = rowsWritten;
    }

    /**
     * The merged file that was not produced.
     *
     * @return the path the merge was writing for; nothing is there
     */
    public Path output() {
        return output;
    }

    /**
     * The data rows read from the inputs.
     *
     * @return the sum of the per-file PINs' data rows
     */
    public long rowsRead() {
        return rowsRead;
    }

    /**
     * The data rows counted in the file that was written.
     *
     * @return the rows read back from the disk
     */
    public long rowsWritten() {
        return rowsWritten;
    }
}
