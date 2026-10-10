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

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The hits of one pepXML file that a caller asked for, keyed as Percolator names them.
 *
 * <p>The exporter's join (P11-13): a Percolator {@code PSMId} {@code <base>_<scan>_<charge>_<rank>}
 * names its pepXML by the base -- {@link PepXmlSearchSummary#outputBase()}, Comet's {@code -N} --
 * and its hit by {@link PepXmlHitKey}: the query's {@code start_scan} and {@code assumed_charge}
 * and the hit's 1-based position. With {@code decoy_search = 2} a target and a decoy PSM of one
 * spectrum share that whole identifier, and their hits are in different files ({@code
 * <base>.pep.xml} and {@code <base>.decoy.pep.xml}); the caller chooses the file by the row's label
 * and builds one index per file.
 *
 * <p><strong>Memory.</strong> The file is streamed by {@link PepXmlSearchHitReader}; only the hits
 * asked for are kept, each with its query's attributes (shared among a query's kept hits), plus one
 * small entry per spectrum query to refuse a repeated {@code (start_scan, assumed_charge)}. The
 * cost is proportional to the PSMs kept, not to the file. Measured on 2026-10-10 on a 725 MB pepXML
 * of 200,200 queries and 1,000,450 hits (the real K562 {@code _3} queries repeated with shifted
 * scans): keeping every hit took 1.39 GB of heap after collection, about 1.4 KB per kept PSM, in 11
 * s; it completed under a 1.5 GB heap and failed under 1.4 GB, the caller's own set of a million
 * keys (about 75 MB) included. Reading the same file through the reader alone, keeping nothing,
 * completed in 8 s under a 64 MB heap. A caller that cannot afford the index for its largest file
 * joins in file order through {@link PepXmlVisitor} instead.
 */
public final class PepXmlHitIndex {

    private final PepXmlSearchSummary summary;

    private final Map<PepXmlHitKey, PepXmlPsm> found;

    private final Set<PepXmlHitKey> missing;

    private final long spectrumQueries;

    private PepXmlHitIndex(
            PepXmlSearchSummary summary,
            Map<PepXmlHitKey, PepXmlPsm> found,
            Set<PepXmlHitKey> missing,
            long spectrumQueries) {
        this.summary = summary;
        this.found = found;
        this.missing = missing;
        this.spectrumQueries = spectrumQueries;
    }

    /**
     * Reads a pepXML file once, keeping the hits asked for.
     *
     * @param pepXml the file
     * @param wanted the keys of the hits to keep; a key the file does not hold is reported by
     *     {@link #missing()}, never invented. The set is read, not copied, and must not change
     *     during the call
     * @return the index
     * @throws CometOutputException if the reader refuses the file, or two of its spectrum queries
     *     share a {@code start_scan} and {@code assumed_charge} -- which would make a {@code PSMId}
     *     ambiguous -- naming both
     */
    public static PepXmlHitIndex of(Path pepXml, Set<PepXmlHitKey> wanted)
            throws CometOutputException {
        Objects.requireNonNull(pepXml, "pepXml");
        Objects.requireNonNull(wanted, "wanted");
        // Read, not copied: at a million keys a copy is tens of megabytes for nothing.
        Collector collector = new Collector(pepXml, wanted);
        PepXmlSearchSummary summary = PepXmlSearchHitReader.read(pepXml, collector);
        Set<PepXmlHitKey> missing = new HashSet<>();
        for (PepXmlHitKey key : wanted) {
            if (!collector.found.containsKey(key)) {
                missing.add(key);
            }
        }
        return new PepXmlHitIndex(summary, collector.found, missing, collector.queries.size());
    }

    /**
     * The file's search summary.
     *
     * @return the summary
     */
    public PepXmlSearchSummary summary() {
        return summary;
    }

    /**
     * The hit a key names, with its query.
     *
     * @param key the key
     * @return the hit, or empty if it was not asked for or the file does not hold it
     */
    public Optional<PepXmlPsm> find(PepXmlHitKey key) {
        return Optional.ofNullable(found.get(Objects.requireNonNull(key, "key")));
    }

    /**
     * The keys asked for that the file does not hold: no query with that scan and charge, or fewer
     * hits than the position.
     *
     * @return the keys, unordered
     */
    public Set<PepXmlHitKey> missing() {
        return Collections.unmodifiableSet(missing);
    }

    /**
     * How many hits were kept.
     *
     * @return the count
     */
    public int size() {
        return found.size();
    }

    /**
     * How many spectrum queries the file holds.
     *
     * @return the count
     */
    public long spectrumQueries() {
        return spectrumQueries;
    }

    /** A query's identity in a {@code PSMId}. */
    private record QueryKey(long startScan, int assumedCharge) {}

    /** Keeps the wanted hits and refuses an ambiguous query. */
    private static final class Collector implements PepXmlVisitor<CometOutputException> {

        private final Path file;

        private final Set<PepXmlHitKey> wanted;

        private final Map<PepXmlHitKey, PepXmlPsm> found = new HashMap<>();

        private final Map<QueryKey, String> queries = new HashMap<>();

        Collector(Path file, Set<PepXmlHitKey> wanted) {
            this.file = file;
            this.wanted = wanted;
        }

        @Override
        public void searchSummary(PepXmlSearchSummary summary) {
            // Kept by the reader's return value.
        }

        @Override
        public void spectrumQuery(PepXmlQuery query, List<PepXmlSearchHit> hits)
                throws CometOutputException {
            String earlier =
                    queries.putIfAbsent(
                            new QueryKey(query.startScan(), query.assumedCharge()),
                            query.spectrum());
            if (earlier != null) {
                throw new CometOutputException(
                        file,
                        "the pepXML file "
                                + file
                                + " holds two spectrum queries with start_scan "
                                + query.startScan()
                                + " and assumed_charge "
                                + query.assumedCharge()
                                + ", \""
                                + earlier
                                + "\" and \""
                                + query.spectrum()
                                + "\" (index "
                                + query.index()
                                + "), so a PSMId naming that scan and charge is ambiguous",
                        null);
            }
            for (PepXmlSearchHit hit : hits) {
                PepXmlPsm psm = new PepXmlPsm(query, hit);
                if (wanted.contains(psm.key())) {
                    found.put(psm.key(), psm);
                }
            }
        }
    }
}
