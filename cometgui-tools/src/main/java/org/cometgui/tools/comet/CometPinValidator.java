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
 * Validates one PIN file Comet wrote ({@code R-CMT-03}'s per-file output check, and {@code
 * R-DEC-04}).
 *
 * <p>Two halves, in order. <strong>Structure</strong>, through the one PIN parser ({@link
 * PinReader}, whose documentation states every rule): the file exists, is not empty, has a header
 * obeying the column rule ({@link PinHeader}), every data row's fields parse, and the file is not
 * truncated. <strong>Decoys</strong> ({@code R-DEC-04}): the file holds at least one target row and
 * at least one decoy row, because Percolator learns from decoys as negative examples and a PIN
 * without them yields a run that "succeeds" and means nothing. In every decoy configuration --
 * Comet's own decoys, concatenated or separate, or a FASTA's own -- the PIN must hold both (with
 * {@code decoy_search = 2} Comet writes a second, {@code .decoy.pep.xml} file, but its PIN still
 * holds both labels: measured, 3492 target and 3497 decoy rows). A missing half fails with a
 * message naming the decoy configuration rather than leaving Percolator to fail obscurely.
 */
public final class CometPinValidator {

    private CometPinValidator() {}

    /**
     * Validates a PIN file.
     *
     * @param pin the file
     * @param decoys the decoy configuration the search ran with, for the message
     * @return its feature columns and row counts
     * @throws CometOutputException if the file is missing, empty, malformed or truncated, or holds
     *     no target or no decoy row, naming the file
     * @throws IOException if the file cannot be closed after reading
     */
    public static PinSummary validate(Path pin, PinDecoyConfiguration decoys) throws IOException {
        Objects.requireNonNull(pin, "pin");
        Objects.requireNonNull(decoys, "decoys");
        try (PinReader reader = PinReader.open(pin)) {
            String row;
            do {
                row = reader.nextRow(); // checks the row and counts its label
            } while (row != null);
            if (reader.rows() == 0) {
                throw new CometOutputException(
                        pin,
                        "the PIN file "
                                + pin
                                + " holds a header and no PSM row, so there are neither targets"
                                + " nor decoys for Percolator; the decoy configuration was "
                                + decoys.describe(),
                        null);
            }
            if (reader.decoys() == 0) {
                throw new CometOutputException(
                        pin,
                        "the PIN file "
                                + pin
                                + " holds "
                                + reader.targets()
                                + " target rows and no decoy row (Label -1), so Percolator would"
                                + " have no negative examples; the decoy configuration was "
                                + decoys.describe(),
                        null);
            }
            if (reader.targets() == 0) {
                throw new CometOutputException(
                        pin,
                        "the PIN file "
                                + pin
                                + " holds "
                                + reader.decoys()
                                + " decoy rows and no target row (Label 1), so there is nothing"
                                + " for Percolator to score; the decoy configuration was "
                                + decoys.describe(),
                        null);
            }
            return new PinSummary(
                    pin, reader.header().featureColumns(), reader.targets(), reader.decoys());
        }
    }
}
