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

package org.cometgui.results.export;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.cometgui.domain.run.RunId;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.domain.secrets.SecretRedactor;
import org.cometgui.results.filtering.PeptideQValueFilter;
import org.cometgui.results.filtering.PsmQValueFilter;
import org.cometgui.results.filtering.QValueFilter;
import org.cometgui.results.filtering.store.TableKind;
import org.cometgui.results.testing.Fixtures;
import org.cometgui.results.testing.IndependentCounter;
import org.cometgui.results.testing.IndependentCounts;
import org.cometgui.results.testing.RealK562;
import org.cometgui.results.testing.ScratchFixtures;
import org.cometgui.results.testing.TestHasher;

/**
 * The raw tables the export tests run over, each with counts from outside the code under test, and
 * the helpers every export test shares.
 *
 * <p>Tables: the constructed shuffled and unknown-q-value tables (hand counts typed from {@code
 * constructed/CONSTRUCTED.txt}, as {@code ResultStoreContract} has them), the twelve checked-in
 * real Percolator tables ({@link IndependentCounter}, itself checked against {@code awk}) and the
 * eight real K562 tables ({@code awk} pins in {@link RealK562}; fail, never skip, when absent).
 */
final class ExportTables {

    /** The gate's cutoffs (phase 10 gate item 3, used for gate items 4, 5 and 8 here). */
    static final List<String> GATE_CUTOFFS = List.of("0", "0.005", "0.01", "1");

    static final String RUN_ID = "run-0001";

    static final String VERSION = "0.1.0-export-test";

    static final Instant START = Instant.parse("2026-10-09T12:00:00.123Z");

    /** The checked-in real tables and their SHA-256s, as {@code real/PROVENANCE.txt} records. */
    static final Map<String, String> CHECKED_IN = new LinkedHashMap<>();

    static {
        String[][] versions = {
            {
                "percolator-3.07.1",
                "6e782dd7bbba9f16bce1c6556bc86032e7a9804b6494694db61573d88340ed53",
                "3f9557b82119a4f9900e5964c762dd9ce504de508afdb5be67dfdcca465259e4"
            },
            {
                "percolator-3.06.5",
                "848e26e570a2c5f736be9e00377adef43db1677796b9efa9dfc2221bb9430852",
                "2f60bf17a274b6e9f95860a8c635540938bd17fcf492e159fedd13722b52ec43"
            },
            {
                "percolator-3.09",
                "44aa04692c21aa47742f406d3da23adfc032d5fba363d07aecc9aa1f2379d07b",
                "9074109fa81a2ea2362de7d69894f0b660a0204baefc9f0f13172fac29d0b36c"
            }
        };
        for (String[] version : versions) {
            CHECKED_IN.put("real/" + version[0] + "/psms.tsv", version[1]);
            CHECKED_IN.put("real/" + version[0] + "/peptides.tsv", version[1]);
            CHECKED_IN.put("real/" + version[0] + "/decoy-psms.tsv", version[2]);
            CHECKED_IN.put("real/" + version[0] + "/decoy-peptides.tsv", version[2]);
        }
    }

    static final String SHUFFLED = "constructed/psms-shuffled.tsv";
    static final String SHUFFLED_SHA256 =
            "3754547ea1ca3ff35b67913a8249b98831e924c0a84029c2a9769fc581a6f5d4";
    static final String UNKNOWN_Q = "constructed/psms-unknown-q.tsv";
    static final String UNKNOWN_Q_SHA256 =
            "ea58b3b63f9031bf53b5675d117b1e7203137a0ebd2399c9def824334267b6c7";

    private ExportTables() {}

    /**
     * One raw table and its independent counts.
     *
     * @param name a readable name
     * @param path verifies the file's SHA-256 and returns it
     * @param kind which table it is
     * @param counts independent counts by cutoff text, at least the gate's four
     */
    record Table(
            String name,
            Supplier<Path> path,
            TableKind kind,
            Map<String, IndependentCounts> counts) {

        @Override
        public String toString() {
            return name;
        }

        QValueFilter filter(String cutoff) {
            return filterFor(kind, cutoff);
        }
    }

    static QValueFilter filterFor(TableKind kind, String cutoff) {
        return kind.isPsms() ? PsmQValueFilter.parse(cutoff) : PeptideQValueFilter.parse(cutoff);
    }

    static Table shuffled() {
        Map<String, IndependentCounts> hand = new LinkedHashMap<>();
        hand.put("0", new IndependentCounts(23, 2, 14, 7));
        hand.put("0.005", new IndependentCounts(23, 6, 10, 7));
        hand.put("0.01", new IndependentCounts(23, 9, 7, 7));
        hand.put("1", new IndependentCounts(23, 16, 0, 7));
        return new Table(
                SHUFFLED,
                () -> Fixtures.verified(SHUFFLED, SHUFFLED_SHA256),
                TableKind.TARGET_PSMS,
                hand);
    }

    static Table unknownQ() {
        Map<String, IndependentCounts> hand = new LinkedHashMap<>();
        hand.put("0", new IndependentCounts(15, 1, 6, 8));
        hand.put("0.005", new IndependentCounts(15, 3, 4, 8));
        hand.put("0.01", new IndependentCounts(15, 5, 2, 8));
        hand.put("1", new IndependentCounts(15, 7, 0, 8));
        return new Table(
                UNKNOWN_Q,
                () -> Fixtures.verified(UNKNOWN_Q, UNKNOWN_Q_SHA256),
                TableKind.TARGET_PSMS,
                hand);
    }

    static Table k562(RealK562.Table table) {
        String name = table.name();
        TableKind kind =
                name.contains("DECOY")
                        ? name.contains("PSMS") ? TableKind.DECOY_PSMS : TableKind.DECOY_PEPTIDES
                        : name.contains("PSMS") ? TableKind.TARGET_PSMS : TableKind.TARGET_PEPTIDES;
        return new Table("k562/" + table.relative(), table::path, kind, table.awkCounts());
    }

    static Table checkedIn(String relative) throws IOException {
        TableKind kind =
                relative.contains("decoy-psms")
                        ? TableKind.DECOY_PSMS
                        : relative.contains("decoy-peptides")
                                ? TableKind.DECOY_PEPTIDES
                                : relative.contains("psms")
                                        ? TableKind.TARGET_PSMS
                                        : TableKind.TARGET_PEPTIDES;
        Path path = Fixtures.verified(relative, CHECKED_IN.get(relative));
        IndependentCounter.Tally tally = IndependentCounter.count(path, GATE_CUTOFFS);
        return new Table(relative, () -> path, kind, tally.byCutoff());
    }

    static Stream<Table> all() throws IOException {
        List<Table> all = new ArrayList<>();
        all.add(shuffled());
        all.add(unknownQ());
        for (String relative : CHECKED_IN.keySet()) {
            all.add(checkedIn(relative));
        }
        for (RealK562.Table table : RealK562.Table.values()) {
            all.add(k562(table));
        }
        return all.stream();
    }

    /** A new run directory with its provenance directory, so its event log can be opened. */
    static RunLayout newRun(Path parent) throws IOException {
        RunLayout run = new RunLayout(parent.toAbsolutePath().resolve("run"));
        Files.createDirectories(run.provenanceDirectory());
        return run;
    }

    static ResultExporter exporter(RunLayout run, Clock clock) {
        return new ResultExporter(
                run,
                new RunId(RUN_ID),
                VERSION,
                clock,
                new TestHasher(),
                SecretRedactor.patternsOnly());
    }

    /** A clock one millisecond later at every reading, from {@link #START}. */
    static Clock ticking() {
        AtomicLong ticks = new AtomicLong();
        return new Clock() {
            @Override
            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(ZoneId zone) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Instant instant() {
                return START.plusMillis(ticks.getAndIncrement());
            }
        };
    }

    static Clock fixed() {
        return Clock.fixed(START, ZoneOffset.UTC);
    }

    /**
     * A file as gate item 4 compares it: its SHA-256 (computed here), size and modification time.
     */
    record Identity(String sha256, long size, FileTime modified) {

        static Identity of(Path file) throws IOException {
            return new Identity(
                    ScratchFixtures.sha256(file),
                    Files.size(file),
                    Files.getLastModifiedTime(file, LinkOption.NOFOLLOW_LINKS));
        }
    }
}
