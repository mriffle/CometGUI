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
 *
 * <h2>Before Percolator: the decoy prefix as well</h2>
 *
 * <p>{@link #validateBeforePercolator} is the specification's <em>Input validation before
 * Percolator</em> over the merged PIN: everything {@link #validate} checks, in the same pass and
 * with the same messages, and then that the labels are not <strong>obviously</strong> inconsistent
 * with the configured {@code decoy_prefix}. "Obviously" is defined narrowly, as two contradictions
 * of how Comet assigns a label, measured on every real PIN this project has (2026.02.2 and
 * 2026.03.0, {@code decoy_search = 1}): every decoy row names only proteins beginning with the
 * prefix, and a target row names either none or a mixture (31 and 12 such mixed rows in the two
 * K562 files) -- Comet calls a match a decoy exactly when every protein it names is one. So:
 *
 * <ol>
 *   <li>decoy rows exist, and <em>not one</em> names a protein beginning with the prefix: the
 *       prefix the run is configured with is not the one that marked these decoys (a PIN searched
 *       with {@code REV_} and checked against {@code DECOY_});
 *   <li>a target row every protein of which begins with the prefix: Comet would have labelled it a
 *       decoy had it used this prefix.
 * </ol>
 *
 * <p>Nothing finer is judged -- not the share of decoys, not a per-row decoy rule -- because a
 * finer rule would refuse files no measurement has shown to be wrong.
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
            return validated(pin, decoys, reader);
        }
    }

    /**
     * Validates the merged PIN before Percolator reads it: everything {@link #validate} checks,
     * then the decoy prefix (see the class documentation).
     *
     * @param pin the file
     * @param decoys the decoy configuration the search ran with; its prefix is checked against the
     *     file, and it is named in every message
     * @return its feature columns and row counts
     * @throws CometOutputException for every reason {@link #validate} gives, with the same message,
     *     or if the labels contradict the decoy prefix, naming the file
     * @throws IOException if the file cannot be closed after reading
     */
    public static PinSummary validateBeforePercolator(Path pin, PinDecoyConfiguration decoys)
            throws IOException {
        Objects.requireNonNull(pin, "pin");
        Objects.requireNonNull(decoys, "decoys");
        try (PinReader reader = PinReader.open(pin, decoys.prefix())) {
            PinSummary summary = validated(pin, decoys, reader);
            if (reader.decoysCarryingPrefix() == 0) {
                throw new CometOutputException(
                        pin,
                        "the PIN file "
                                + pin
                                + " holds "
                                + reader.decoys()
                                + " decoy rows (Label -1) and not one of them names a protein"
                                + " beginning with the decoy prefix \""
                                + decoys.prefix()
                                + "\", so that prefix is not the one that marked these decoys;"
                                + " the decoy configuration was "
                                + decoys.describe(),
                        null);
            }
            if (reader.targetsOnlyPrefixed() > 0) {
                throw new CometOutputException(
                        pin,
                        "the PIN file "
                                + pin
                                + " holds "
                                + reader.targetsOnlyPrefixed()
                                + " target rows (Label 1) every protein of which begins with the"
                                + " decoy prefix \""
                                + decoys.prefix()
                                + "\", the first at line "
                                + reader.firstTargetOnlyPrefixedLine()
                                + "; Comet labels a match a decoy when every protein it names"
                                + " carries the prefix, so this file was not written with this"
                                + " prefix; the decoy configuration was "
                                + decoys.describe(),
                        null);
            }
            return summary;
        }
    }

    /** Reads every row of an open file and applies R-DEC-04. */
    private static PinSummary validated(Path pin, PinDecoyConfiguration decoys, PinReader reader)
            throws CometOutputException {
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
