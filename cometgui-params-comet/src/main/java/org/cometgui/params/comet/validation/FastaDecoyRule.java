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

package org.cometgui.params.comet.validation;

import java.util.List;
import java.util.Optional;
import org.cometgui.domain.params.FastaDecoyCensus;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.model.DecoySource;
import org.cometgui.params.comet.model.ParameterValue;

/**
 * {@code R-DEC-02}'s two blocks, judged on the FASTA's decoy census (design decision P8-7): {@link
 * Rule#DECOY_NONE_ANYWHERE} -- {@code decoy_search = 0} and no record of the FASTA is a decoy, so
 * Percolator would have no negative examples -- and {@link Rule#DECOY_DOUBLE} -- {@code
 * decoy_search} 1 or 2 and the FASTA already holds decoys, which Comet would decoy a second time.
 * Both are errors, so the run is blocked before Comet starts, and each message names the decoy
 * configuration: the {@code decoy_search} value and its meaning, the prefix, the FASTA and the
 * count.
 *
 * <p>The census must have been taken for the model's own {@code decoy_prefix} ({@code R-DEC-03}:
 * one prefix for the whole project); a census for another prefix is refused, never used. An
 * undocumented {@code decoy_search} has no decoy source and is the choice rule's error, not this
 * rule's.
 */
final class FastaDecoyRule {

    /** The parameter holding the decoy source. */
    static final String DECOY_SEARCH = DecoySource.PARAMETER;

    /** The database parameter. */
    static final String DATABASE = "database_name";

    private FastaDecoyRule() {}

    /**
     * Refuses a census taken for another prefix than the model's.
     *
     * @param model the model
     * @param census the census
     * @throws IllegalArgumentException if the prefixes differ
     */
    static void requireSamePrefix(CometParameters model, FastaDecoyCensus census) {
        String prefix = ((ParameterValue.Text) model.value(DecoyRule.PREFIX)).text();
        if (!census.prefix().equals(prefix)) {
            throw new IllegalArgumentException(
                    "the decoy census of "
                            + census.fasta()
                            + " counted accessions beginning with "
                            + census.prefix()
                            + ", but "
                            + DecoyRule.PREFIX
                            + " = "
                            + prefix
                            + "; scan the FASTA again for "
                            + DecoyRule.PREFIX
                            + " before validating");
        }
    }

    static void check(CometParameters model, FastaDecoyCensus census, Findings findings) {
        Optional<DecoySource> source = model.decoySource();
        if (source.isEmpty()) {
            return;
        }
        String configuration =
                DECOY_SEARCH
                        + " = "
                        + source.get().decoySearch()
                        + " ("
                        + meaning(source.get())
                        + ") and "
                        + census.fasta();
        if (source.get() == DecoySource.FASTA_CONTAINS_DECOYS && !census.hasDecoys()) {
            findings.add(
                    Rule.DECOY_NONE_ANYWHERE,
                    List.of(DECOY_SEARCH, DATABASE),
                    configuration
                            + " holds no entry whose accession begins with "
                            + census.prefix()
                            + " (0 of "
                            + census.records()
                            + " records): Percolator would have no negative examples; set "
                            + DECOY_SEARCH
                            + " to 1 or 2 so that Comet makes decoys, or choose a FASTA whose"
                            + " decoys begin with "
                            + census.prefix());
        } else if (source.get() != DecoySource.FASTA_CONTAINS_DECOYS && census.hasDecoys()) {
            findings.add(
                    Rule.DECOY_DOUBLE,
                    List.of(DECOY_SEARCH, DATABASE),
                    configuration
                            + " already holds "
                            + census.decoyRecords()
                            + (census.decoyRecords() == 1 ? " entry" : " entries")
                            + " whose accession begins with "
                            + census.prefix()
                            + " ("
                            + census.decoyRecords()
                            + " of "
                            + census.records()
                            + " records; the first is "
                            + census.firstDecoyAccession().orElseThrow()
                            + "): Comet would make decoys of those decoys too, so decoys would be"
                            + " counted twice; set "
                            + DECOY_SEARCH
                            + " to 0 to use the FASTA's own decoys, or choose a FASTA of targets"
                            + " only");
        }
    }

    /** What a decoy source means, in words. */
    static String meaning(DecoySource source) {
        return switch (source) {
            case FASTA_CONTAINS_DECOYS -> "no internal decoys";
            case COMET_INTERNAL_CONCATENATED -> "Comet's internal decoys, concatenated";
            case COMET_INTERNAL_SEPARATE -> "Comet's internal decoys, reported separately";
        };
    }
}
