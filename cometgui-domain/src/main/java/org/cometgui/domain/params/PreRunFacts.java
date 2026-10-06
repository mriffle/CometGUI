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

import java.util.Objects;
import java.util.Optional;

/**
 * What the file system says about a search before Comet starts, handed to the one validator as data
 * (design decisions P8-7 and P8-8): the decoy census of the FASTA, and the self-description of the
 * existing index Comet will search.
 *
 * <p>Each is optional. A census is supplied whenever the FASTA has been scanned; an index
 * description exactly when Comet will read an <em>existing</em> {@code .idx} file -- named by
 * {@code database_name}, or by {@code -D} -- because only then do the index's recorded options
 * replace the search's. An index Comet has yet to build has no description.
 *
 * @param census the FASTA's decoy census, if it was taken
 * @param index the existing index's description, if Comet will search one
 */
public record PreRunFacts(
        Optional<FastaDecoyCensus> census, Optional<CometIndexDescription> index) {

    /**
     * Validates the components.
     *
     * @throws NullPointerException naming a component that is {@code null}
     */
    public PreRunFacts {
        Objects.requireNonNull(census, "census");
        Objects.requireNonNull(index, "index");
    }

    /**
     * No facts: what validating a model on its own means.
     *
     * @return facts with neither a census nor an index
     */
    public static PreRunFacts none() {
        return new PreRunFacts(Optional.empty(), Optional.empty());
    }

    /**
     * These facts with a census.
     *
     * @param taken the census
     * @return the facts
     */
    public PreRunFacts withCensus(FastaDecoyCensus taken) {
        return new PreRunFacts(Optional.of(taken), index);
    }

    /**
     * These facts with an index description.
     *
     * @param read the description
     * @return the facts
     */
    public PreRunFacts withIndex(CometIndexDescription read) {
        return new PreRunFacts(census, Optional.of(read));
    }
}
