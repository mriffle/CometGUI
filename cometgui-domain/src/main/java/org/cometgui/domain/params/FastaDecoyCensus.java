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

package org.cometgui.domain.params;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * What a scan of a FASTA file found about decoys ({@code R-DEC-02}): how many records it holds, how
 * many of them are decoys by the decoy prefix, and the first decoy's accession, so that a message
 * can name one.
 *
 * <p>A record is one {@code >} header line. Its accession is the first white-space delimited token
 * after the {@code >}; a record is a decoy when its accession begins with the prefix, compared case
 * for case. The census is taken for <strong>one</strong> prefix -- the project's {@code
 * decoy_prefix} ({@code R-DEC-03}) -- and says which, so that a census taken for another prefix can
 * be refused rather than silently used.
 *
 * <p>A pure value: {@code org.cometgui.tools.comet.FastaDecoyScanner} takes it from a file, and the
 * validator judges it against the decoy configuration.
 *
 * @param fasta the FASTA file scanned, as it was named to the scanner
 * @param prefix the decoy prefix scanned for; never empty, and one token
 * @param records how many records the file holds; at least one
 * @param decoyRecords how many of them have an accession beginning with the prefix
 * @param firstDecoyAccession the accession of the first decoy record; present exactly when there is
 *     one
 */
public record FastaDecoyCensus(
        Path fasta,
        String prefix,
        long records,
        long decoyRecords,
        Optional<String> firstDecoyAccession) {

    /**
     * Validates the census.
     *
     * @throws NullPointerException naming a component that is {@code null}
     * @throws IllegalArgumentException if the prefix is empty or holds white space, the file holds
     *     no record, the decoy count is negative or above the record count, or the first decoy's
     *     accession is absent although there are decoys, present although there are none, or does
     *     not begin with the prefix
     */
    public FastaDecoyCensus {
        Objects.requireNonNull(fasta, "fasta");
        Objects.requireNonNull(prefix, "prefix");
        Objects.requireNonNull(firstDecoyAccession, "firstDecoyAccession");
        if (prefix.isEmpty() || prefix.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException(
                    "a decoy prefix is one non-empty token, not \"" + prefix + "\"");
        }
        if (records < 1) {
            throw new IllegalArgumentException(
                    "a census counts at least one record, but " + fasta + " has " + records);
        }
        if (decoyRecords < 0 || decoyRecords > records) {
            throw new IllegalArgumentException(
                    decoyRecords + " decoy records cannot be among " + records + " records");
        }
        if (firstDecoyAccession.isPresent() != (decoyRecords > 0)) {
            throw new IllegalArgumentException(
                    "the first decoy accession is named exactly when there are decoys: "
                            + decoyRecords
                            + " decoys, first "
                            + firstDecoyAccession);
        }
        if (firstDecoyAccession.filter(accession -> !accession.startsWith(prefix)).isPresent()) {
            throw new IllegalArgumentException(
                    "the first decoy \""
                            + firstDecoyAccession.get()
                            + "\" does not begin with the prefix "
                            + prefix);
        }
    }

    /**
     * Whether the file holds any decoy.
     *
     * @return {@code true} if at least one accession begins with the prefix
     */
    public boolean hasDecoys() {
        return decoyRecords > 0;
    }

    /**
     * The records that are not decoys.
     *
     * @return the record count less the decoy count
     */
    public long targetRecords() {
        return records - decoyRecords;
    }
}
