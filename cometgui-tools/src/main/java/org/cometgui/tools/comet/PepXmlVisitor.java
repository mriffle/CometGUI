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

import java.util.List;

/**
 * Receives a pepXML file's content from {@link PepXmlSearchHitReader#read}, in file order: the
 * search summary once, then every spectrum query with its hits.
 *
 * <p><strong>Nothing is final until {@code read} returns.</strong> The file is streamed, so a
 * defect late in it -- a malformed hit, a truncated end -- is found after earlier queries were
 * delivered. A visitor that writes anything must not let it look complete before {@code read} has
 * returned normally.
 *
 * @param <X> what the visitor itself may throw, which {@code read} passes on unchanged
 */
public interface PepXmlVisitor<X extends Exception> {

    /**
     * The search summary, delivered before any query.
     *
     * @param summary the summary
     * @throws X if the visitor refuses it
     */
    void searchSummary(PepXmlSearchSummary summary) throws X;

    /**
     * One spectrum query and its hits.
     *
     * @param query the query's attributes
     * @param hits its hits in document order, {@link PepXmlSearchHit#position()} 1..n; empty for a
     *     query Comet matched no peptide to
     * @throws X if the visitor refuses it
     */
    void spectrumQuery(PepXmlQuery query, List<PepXmlSearchHit> hits) throws X;
}
