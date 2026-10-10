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

package org.cometgui.tools.pdv;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.cometgui.results.parser.PercolatorOutputException;
import org.cometgui.results.parser.QValue;
import org.cometgui.results.parser.ResultRow;
import org.cometgui.results.parser.ResultTableReader;
import org.cometgui.results.parser.SpectrumReference;
import org.cometgui.tools.comet.CometOutputException;
import org.cometgui.tools.comet.PepXmlAminoAcidModification;
import org.cometgui.tools.comet.PepXmlQuery;
import org.cometgui.tools.comet.PepXmlSearchHit;
import org.cometgui.tools.comet.PepXmlSearchHitReader;
import org.cometgui.tools.comet.PepXmlSearchSummary;
import org.cometgui.tools.comet.PepXmlTerminalModification;
import org.cometgui.tools.comet.PepXmlTerminus;
import org.cometgui.tools.comet.PepXmlVisitor;
import org.cometgui.tools.pdv.PdvMzTabException.Reason;

/**
 * Writes the mzTab PDV's {@code denovo-gui} mode is given for a run, with the MGFs it references
 * ({@code R-PDV-02}, {@code R-PDV-03}; design decisions P11-8 and P11-11 to P11-15).
 *
 * <h2>Every PSM, once, transcribed</h2>
 *
 * <p>Every row of Percolator's target PSM table and then of its decoy table, in file order, becomes
 * one {@code PSM} line, and nothing else does. Each row is joined to <strong>exactly one</strong>
 * pepXML hit and <strong>exactly one</strong> spectrum:
 *
 * <ol>
 *   <li>its {@code PSMId} {@code <base>_<scan>_<charge>_<rank>} ({@link SpectrumReference}) names
 *       its source by the base, and its hit by {@code start_scan}, {@code assumed_charge} and the
 *       hit's 1-based <em>position</em> in its query (P11-13) -- in {@link
 *       PdvMzTabSource#decoyPepXml} for a decoy-table row when the search wrote decoys separately,
 *       else in {@link PdvMzTabSource#pepXml};
 *   <li>the Percolator peptide is checked against that hit as values ({@link PsmHit#mismatch});
 *   <li>the query's {@code spectrumNativeID} names the spectrum, which must be exactly one MS2
 *       spectrum of the source file, and whose native id, if it carries a scan number, must carry
 *       the PSM's.
 * </ol>
 *
 * <p>The values are the sources' own text: the peptide, flanking residues, charge ({@code
 * assumed_charge}), retention time ({@code retention_time_sec}, seconds) and modification masses
 * from the pepXML; the observed m/z from the spectrum file's selected ion; the score, q-value, PEP
 * and accessions ({@code proteinIds}, P11-14) from Percolator. A value mzTab names that no source
 * gives is {@code null}. {@code calc_mass_to_charge} is omitted because PDV dies on {@code null}
 * there (P11-15).
 *
 * <h2>One spectrum entry per row (P11-11, P11-12)</h2>
 *
 * <p>Each source with at least one PSM is an {@code ms_run}, numbered in source order, with its own
 * MGF {@value #MGF_PREFIX}{@code <n>.mgf}: one entry per PSM row of that run, in mzTab order, each
 * the exact {@link PdvMgfWriter} encoding of the producing spectrum. A row's {@code spectra_ref} is
 * {@code ms_run[n]:index=<k>}, {@code k} its entry's 0-based ordinal, so every row has its own
 * reference and every reference resolves to the spectrum that produced its PSM.
 *
 * <h2>Loud, and all or nothing</h2>
 *
 * <p>Anything that cannot be represented faithfully throws {@link PdvMzTabException}, naming the
 * PSM, the source files and the reason. Everything is written in a hidden staging directory beside
 * the requested one and renamed onto it only once the document has been read back and holds exactly
 * the rows read -- every {@code ms_run}'s references {@code 0..n-1} in order, and each MGF exactly
 * {@code n} entries -- so on any failure nothing exists at the requested path.
 *
 * <h2>Memory</h2>
 *
 * <p>No table, pepXML or spectrum file is held. The Percolator tables are read twice, each pepXML
 * and spectrum file once; between the passes the exporter keeps about 40 bytes per PSM in primitive
 * arrays (its keys, and where each row's pepXML values and spectrum were put in scratch files in
 * the staging directory), and while one source is read, the native ids of the spectra its PSMs
 * name. Measured on 2026-10-10 on 1,000,450 PSMs over 200,200 spectra (the real K562 {@code _3}
 * queries repeated with shifted scans, an MGF source): 54 to 65 s; the live heap peaked at 92 MB,
 * during the pepXML pass; the export completed under {@code -Xmx128m} and failed under 64 MB. The
 * MGF holds one entry per PSM, so it grows with the PSMs, not the spectra: 1.78 GB there, beside a
 * 278 MB mzTab.
 */
public final class PdvMzTabExporter {

    /** The mzTab's file name in the export directory. */
    public static final String MZTAB_FILE_NAME = "psms.mztab";

    /** Each MGF's file name is this prefix, the {@code ms_run} number and {@code .mgf}. */
    public static final String MGF_PREFIX = "ms_run-";

    /**
     * The version of the document this class writes. A reader that reuses an export compares it, so
     * an export written by an earlier shape is written again.
     */
    public static final String FORMAT_VERSION = "1";

    /** The column PDV reads modifications from (any header containing {@code proforma}). */
    public static final String PROFORMA_COLUMN =
            "opt_global_cv_MS:1003169_proforma_peptidoform_sequence";

    /** The decoy flag: {@code 1} for a row of Percolator's decoy table, {@code 0} for a target. */
    public static final String DECOY_COLUMN = "opt_global_cv_MS:1002217_decoy_peptide";

    /** The row's Percolator {@code PSMId}, verbatim. */
    public static final String PSM_ID_COLUMN = "opt_global_percolator_PSMId";

    /** Percolator's {@code score}, verbatim; also {@code search_engine_score[1]}. */
    public static final String SCORE_COLUMN = "opt_global_percolator_score";

    /** Percolator's {@code q-value}, verbatim; also {@code search_engine_score[2]}. */
    public static final String Q_VALUE_COLUMN = "opt_global_percolator_q_value";

    /** Percolator's {@code posterior_error_prob}, verbatim; also {@code search_engine_score[3]}. */
    public static final String PEP_COLUMN = "opt_global_percolator_posterior_error_prob";

    /** How the accessions of a row's proteins are joined. */
    public static final String ACCESSION_SEPARATOR = ";";

    /** The PSM section's columns, in order. */
    public static final List<String> COLUMNS =
            List.of(
                    "sequence",
                    "PSM_ID",
                    "accession",
                    "unique",
                    "database",
                    "database_version",
                    "search_engine",
                    "search_engine_score[1]",
                    "search_engine_score[2]",
                    "search_engine_score[3]",
                    "modifications",
                    "retention_time",
                    "charge",
                    "exp_mass_to_charge",
                    "uri",
                    "spectra_ref",
                    "pre",
                    "post",
                    "start",
                    "end",
                    PROFORMA_COLUMN,
                    DECOY_COLUMN,
                    PSM_ID_COLUMN,
                    SCORE_COLUMN,
                    Q_VALUE_COLUMN,
                    PEP_COLUMN);

    static final String SEARCH_ENGINE = "[MS, MS:1002251, Comet, ]|[MS, MS:1001490, Percolator, ]";

    static final String NULL = PsmHit.NULL;

    /** Bits of a packed hit key: scan, charge, position. */
    static final int SCAN_BITS = 40;

    static final int CHARGE_BITS = 12;

    static final int POSITION_BITS = 11;

    private PdvMzTabExporter() {}

    /**
     * Exports a run.
     *
     * @param request what to export and where
     * @return what was written
     * @throws PdvMzTabException if a PSM or a source cannot be represented faithfully; nothing is
     *     then at the requested directory
     * @throws FileAlreadyExistsException if the requested directory exists
     * @throws IOException if the export cannot be written
     */
    public static PdvMzTabExport export(PdvMzTabRequest request) throws IOException {
        Path directory = request.directory().toAbsolutePath();
        if (Files.exists(directory)) {
            throw new FileAlreadyExistsException(
                    directory.toString(),
                    null,
                    "an mzTab export is written only to a new directory");
        }
        Path parent = Objects.requireNonNull(directory.getParent(), "the directory's parent");
        Path staging =
                Files.createTempDirectory(parent, "." + directory.getFileName() + "-staging-");
        boolean moved = false;
        try {
            PdvMzTabExport export = new Export(request, staging).run(directory);
            Files.move(staging, directory, StandardCopyOption.ATOMIC_MOVE);
            moved = true;
            return export;
        } finally {
            if (!moved) {
                deleteTree(staging);
            }
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                try {
                    Files.deleteIfExists(path);
                } catch (NoSuchFileException gone) {
                    // already gone
                }
            }
        }
    }

    /**
     * Packs a hit's place into one non-negative key.
     *
     * @return the key, or -1 if a number is beyond what any Comet pepXML holds
     */
    static long hitKey(long scan, int charge, int position) {
        if (scan < 0
                || scan >= 1L << SCAN_BITS
                || charge < 0
                || charge >= 1 << CHARGE_BITS
                || position < 0
                || position >= 1 << POSITION_BITS) {
            return -1;
        }
        return scan << (CHARGE_BITS + POSITION_BITS) | (long) charge << POSITION_BITS | position;
    }

    /** One export: the state its passes share. */
    private static final class Export {

        private final List<PdvMzTabSource> sources;
        private final List<Path> tables = new ArrayList<>();
        private final Map<String, Integer> sourceByBase = new HashMap<>();
        private final long[] tableRows = new long[2];

        /** Per source, per pepXML (0 the main, 1 the decoy): hit key to row. */
        private final LongIntMap[][] hits;

        /** Per source, per pepXML: query key to the first row naming that query. */
        private final LongIntMap[][] queries;

        /** Per source: its spectra, once read. */
        private final Slots[] slots;

        private final Set<String> cometVersions = new LinkedHashSet<>();
        private final Set<String> fixedModifications = new LinkedHashSet<>();
        private final Set<String> variableModifications = new LinkedHashSet<>();

        private final Path staging;
        private final Path records;
        private int rows;
        private long[] recordOffset;
        private int[] rowSlot;
        private long recordsWritten;

        Export(PdvMzTabRequest request, Path staging) {
            this.staging = staging;
            this.records = staging.resolve(".hits.tmp");
            this.sources = request.sources();
            tables.add(request.targetPsms());
            request.decoyPsms().ifPresent(tables::add);
            hits = new LongIntMap[sources.size()][2];
            queries = new LongIntMap[sources.size()][2];
            slots = new Slots[sources.size()];
            for (int s = 0; s < sources.size(); s++) {
                sourceByBase.put(sources.get(s).psmIdBase(), s);
                for (int f = 0; f < 2; f++) {
                    hits[s][f] = new LongIntMap();
                    queries[s][f] = new LongIntMap();
                }
            }
        }

        /** Everything but the final rename, into the staging directory. */
        PdvMzTabExport run(Path directory) throws IOException {
            indexRows();
            try (OutputStream out =
                    new BufferedOutputStream(
                            Files.newOutputStream(records, StandardOpenOption.CREATE_NEW))) {
                for (int s = 0; s < sources.size(); s++) {
                    readPepXmls(s, out);
                }
            }
            for (int r = 0; r < rows; r++) {
                if (recordOffset[r] < 0) {
                    int s = sourceOf(r);
                    throw refusal(
                            Reason.NO_PEPXML_HIT,
                            r,
                            filesOf(r, s),
                            "names a search hit its pepXML does not hold: no spectrum_query with"
                                    + " that start_scan and assumed_charge, or fewer search_hits"
                                    + " than its rank");
                }
            }
            for (int s = 0; s < sources.size(); s++) {
                readSpectra(s);
            }
            PdvMzTabExport export = write(directory);
            Files.delete(records);
            for (Slots spectra : slots) {
                if (spectra != null) {
                    Files.delete(spectra.file);
                }
            }
            return export;
        }

        // ---------------------------------------------------------------------------------
        // Pass 1: every Percolator row, keyed.

        private void indexRows() throws IOException {
            int r = 0;
            for (int t = 0; t < tables.size(); t++) {
                Path table = tables.get(t);
                try (ResultTableReader reader = ResultTableReader.open(table)) {
                    for (ResultRow row = reader.next(); row != null; row = reader.next()) {
                        if (r == Integer.MAX_VALUE - 8) {
                            throw new PdvMzTabException(
                                    Reason.UNREADABLE_SOURCE,
                                    null,
                                    List.of(table),
                                    "holds more PSMs than one export can index",
                                    null);
                        }
                        Located located = locate(t, row);
                        int existing =
                                hits[located.source][located.file].putIfAbsent(located.hit, r);
                        if (existing != LongIntMap.EMPTY) {
                            throw new PdvMzTabException(
                                    Reason.DUPLICATE_PSM,
                                    row.psmId(),
                                    List.of(table),
                                    "names the same pepXML search hit as the row at "
                                            + rowPlace(existing)
                                            + ", so one hit would appear twice",
                                    null);
                        }
                        queries[located.source][located.file].putIfAbsent(located.query, r);
                        r++;
                    }
                    tableRows[t] = reader.counts().rows();
                } catch (PercolatorOutputException unreadable) {
                    throw unreadable(table, unreadable);
                }
            }
            rows = r;
            if (tableRows[0] + tableRows[1] != rows) {
                throw new PdvMzTabException(
                        Reason.INCOMPLETE,
                        null,
                        tables,
                        "the export's own check failed: the reader counted "
                                + (tableRows[0] + tableRows[1])
                                + " rows and the export indexed "
                                + rows,
                        null);
            }
            if (rows == 0) {
                throw new PdvMzTabException(
                        Reason.NO_PSMS,
                        null,
                        tables,
                        "the Percolator tables hold no PSM, and PDV cannot show an empty result"
                                + " set",
                        null);
            }
            recordOffset = new long[rows];
            Arrays.fill(recordOffset, -1L);
            rowSlot = new int[rows];
        }

        /** Where a row's hit is, after the checks every row must pass. */
        private Located locate(int table, ResultRow row) throws PdvMzTabException {
            Path file = tables.get(table);
            String psmId = row.psmId();
            SpectrumReference reference =
                    row.spectrumReference()
                            .orElseThrow(
                                    () ->
                                            new PdvMzTabException(
                                                    Reason.NO_SPECTRUM_REFERENCE,
                                                    psmId,
                                                    List.of(file),
                                                    "is not <base>_<scan>_<charge>_<rank>, so it"
                                                            + " names no search hit",
                                                    null));
            Integer source = sourceByBase.get(reference.base());
            if (source == null) {
                throw new PdvMzTabException(
                        Reason.UNKNOWN_BASE,
                        psmId,
                        List.of(file),
                        "has the base \""
                                + reference.base()
                                + "\", which is none of the run's Comet outputs",
                        null);
            }
            PdvMzTabSource named = sources.get(source);
            int pepXml = table == 1 && named.decoyPepXml().isPresent() ? 1 : 0;
            long hit = hitKey(reference.scan(), reference.charge(), reference.rank());
            if (hit < 0 || reference.rank() == 0) {
                throw new PdvMzTabException(
                        Reason.NO_PEPXML_HIT,
                        psmId,
                        List.of(file, pepXmlOf(named, pepXml)),
                        "names scan "
                                + reference.scan()
                                + ", charge "
                                + reference.charge()
                                + " and rank "
                                + reference.rank()
                                + ", which no Comet pepXML search hit has",
                        null);
            }
            checkValues(table, row);
            return new Located(
                    source, pepXml, hit, hitKey(reference.scan(), reference.charge(), 0));
        }

        /** The checks on a row's own values, made on both passes. */
        private void checkValues(int table, ResultRow row) throws PdvMzTabException {
            Path file = tables.get(table);
            text(row.psmId(), row.psmId(), file, "PSMId");
            decimal(row, row.scoreText(), "score", file);
            decimal(row, row.qValue().text(), "q-value", file);
            decimal(row, row.posteriorErrorProbabilityText(), "posterior_error_prob", file);
            accession(row, file);
        }

        private static void decimal(ResultRow row, String text, String column, Path file)
                throws PdvMzTabException {
            // QValue.of applies the results model's one definition of a decimal number; its
            // MISSING and UNPARSABLE are "not a decimal" for any column, not only the q-value.
            QValue.Status status = QValue.of(text).status();
            if (status == QValue.Status.MISSING || status == QValue.Status.UNPARSABLE) {
                throw new PdvMzTabException(
                        Reason.NOT_A_DECIMAL,
                        row.psmId(),
                        List.of(file),
                        "has the "
                                + column
                                + " \""
                                + text
                                + "\", which is not a decimal number as written",
                        null);
            }
        }

        private static String accession(ResultRow row, Path file) throws PdvMzTabException {
            List<String> proteins = row.proteinIds();
            if (proteins.size() == 1 && proteins.get(0).isEmpty()) {
                return NULL;
            }
            for (String protein : proteins) {
                if (protein.isEmpty()
                        || protein.contains(ACCESSION_SEPARATOR)
                        || protein.equals(NULL)) {
                    throw new PdvMzTabException(
                            Reason.UNREPRESENTABLE_TEXT,
                            row.psmId(),
                            List.of(file),
                            "has the protein \""
                                    + protein
                                    + "\" among "
                                    + proteins
                                    + ", which the accession column cannot carry: it is empty,"
                                    + " holds \""
                                    + ACCESSION_SEPARATOR
                                    + "\" or reads as mzTab's null",
                            null);
                }
            }
            return text(String.join(ACCESSION_SEPARATOR, proteins), row.psmId(), file, "accession");
        }

        /** A value fit for an mzTab field, or the refusal. */
        private static String text(String value, String psmId, Path file, String what)
                throws PdvMzTabException {
            if (value.isEmpty()
                    || value.indexOf('\t') >= 0
                    || value.indexOf('\n') >= 0
                    || value.indexOf('\r') >= 0) {
                throw new PdvMzTabException(
                        Reason.UNREPRESENTABLE_TEXT,
                        psmId,
                        List.of(file),
                        "has the "
                                + what
                                + " \""
                                + value
                                + "\", which an mzTab field cannot carry: it is empty or holds a"
                                + " tab or line break",
                        null);
            }
            return value;
        }

        // ---------------------------------------------------------------------------------
        // Pass 2: each source's pepXML files, keeping the hits the rows name.

        private void readPepXmls(int s, OutputStream out) throws IOException {
            PdvMzTabSource source = sources.get(s);
            Map<String, Integer> slotOf = new HashMap<>();
            List<Integer> firstRow = new ArrayList<>();
            for (int f = 0; f < 2; f++) {
                if (hits[s][f].size() == 0) {
                    continue;
                }
                Path pepXml = pepXmlOf(source, f);
                HitCollector collector = new HitCollector(s, f, pepXml, slotOf, firstRow, out);
                PepXmlSearchSummary summary;
                try {
                    summary = PepXmlSearchHitReader.read(pepXml, collector);
                } catch (CometOutputException unreadable) {
                    throw unreadable(pepXml, unreadable);
                }
                declare(summary);
            }
            if (!slotOf.isEmpty()) {
                slots[s] = new Slots(slotOf, firstRow, staging.resolve(".spectra-" + s + ".tmp"));
            }
        }

        /** Keeps the search summary's statements the metadata repeats. */
        private void declare(PepXmlSearchSummary summary) throws PdvMzTabException {
            String version = summary.searchEngineVersion();
            if (version.isEmpty() || !version.matches("[^,\\[\\]\\t\\r\\n|]+")) {
                throw new PdvMzTabException(
                        Reason.UNREPRESENTABLE_TEXT,
                        null,
                        List.of(summary.file()),
                        "states the Comet version \""
                                + version
                                + "\", which an mzTab parameter cannot carry",
                        null);
            }
            cometVersions.add(version);
            for (PepXmlAminoAcidModification declared : summary.aminoAcidModifications()) {
                (declared.variable() ? variableModifications : fixedModifications)
                        .add(
                                PsmHit.signed(declared.massDiff().text())
                                        + "\t"
                                        + declared.aminoAcid()
                                        + "\tAnywhere");
            }
            for (PepXmlTerminalModification declared : summary.terminalModifications()) {
                String terminus = declared.terminus() == PepXmlTerminus.N ? "N-term" : "C-term";
                (declared.variable() ? variableModifications : fixedModifications)
                        .add(
                                PsmHit.signed(declared.massDiff().text())
                                        + "\t"
                                        + terminus
                                        + "\t"
                                        + (declared.proteinTerminus() ? "Protein " : "Any ")
                                        + terminus);
            }
        }

        /** Receives one pepXML file, keeping the hits its rows name. */
        private final class HitCollector implements PepXmlVisitor<IOException> {

            private final int source;
            private final int file;
            private final Path pepXml;
            private final Map<String, Integer> slotOf;
            private final List<Integer> firstRow;
            private final OutputStream out;
            private final LongIntMap seen = new LongIntMap();
            private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            private List<PepXmlTerminalModification> declared = List.of();

            HitCollector(
                    int source,
                    int file,
                    Path pepXml,
                    Map<String, Integer> slotOf,
                    List<Integer> firstRow,
                    OutputStream out) {
                this.source = source;
                this.file = file;
                this.pepXml = pepXml;
                this.slotOf = slotOf;
                this.firstRow = firstRow;
                this.out = out;
            }

            @Override
            public void searchSummary(PepXmlSearchSummary summary) {
                declared = summary.terminalModifications();
            }

            @Override
            public void spectrumQuery(PepXmlQuery query, List<PepXmlSearchHit> found)
                    throws IOException {
                long queryKey = hitKey(query.startScan(), query.assumedCharge(), 0);
                int named = queries[source][file].get(queryKey);
                if (named == LongIntMap.EMPTY) {
                    return;
                }
                if (seen.putIfAbsent(queryKey, 0) != LongIntMap.EMPTY) {
                    throw refusal(
                            Reason.AMBIGUOUS_QUERY,
                            named,
                            List.of(tableOf(named), pepXml),
                            "names start_scan "
                                    + query.startScan()
                                    + " and assumed_charge "
                                    + query.assumedCharge()
                                    + ", which two spectrum_query elements of the pepXML share"
                                    + " (the second is \""
                                    + query.spectrum()
                                    + "\"), so its search hit is ambiguous");
                }
                for (PepXmlSearchHit hit : found) {
                    int r =
                            hits[source][file].get(
                                    hitKey(
                                            query.startScan(),
                                            query.assumedCharge(),
                                            hit.position()));
                    if (r != LongIntMap.EMPTY) {
                        keep(r, query, hit);
                    }
                }
            }

            private void keep(int r, PepXmlQuery query, PepXmlSearchHit hit) throws IOException {
                PsmHit values;
                try {
                    values = PsmHit.of(query, hit, declared);
                } catch (MzTabRefusal refused) {
                    throw refusal(
                            refused.reason(), r, List.of(tableOf(r), pepXml), refused.getMessage());
                }
                Integer slot = slotOf.get(values.nativeId());
                if (slot == null) {
                    slot = slotOf.size();
                    slotOf.put(values.nativeId(), slot);
                    firstRow.add(r);
                }
                rowSlot[r] = slot;
                buffer.reset();
                try (DataOutputStream data = new DataOutputStream(buffer)) {
                    data.writeInt(source);
                    data.writeInt(file);
                    values.write(data);
                } catch (IOException impossible) {
                    throw new IllegalStateException("writing to memory failed", impossible);
                }
                byte[] bytes = buffer.toByteArray();
                recordOffset[r] = recordsWritten;
                out.write(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
                out.write(bytes);
                recordsWritten += Integer.BYTES + bytes.length;
            }
        }

        // ---------------------------------------------------------------------------------
        // Pass 3: each source's spectrum file, keeping the spectra the hits name.

        private void readSpectra(int s) throws IOException {
            Slots spectra = slots[s];
            if (spectra == null) {
                return;
            }
            PdvMzTabSource source = sources.get(s);
            Path file = source.spectrumFile();
            try (OutputStream out =
                    new BufferedOutputStream(
                            Files.newOutputStream(spectra.file, StandardOpenOption.CREATE_NEW))) {
                long[] written = {0L};
                SourceSpectra.read(
                        file,
                        source.format(),
                        spectrum -> {
                            Integer slot = spectra.slotOf.get(spectrum.nativeId());
                            if (slot == null) {
                                return;
                            }
                            int r = spectra.firstRow[slot];
                            if (spectra.offset[slot] >= 0) {
                                throw refusal(
                                        Reason.AMBIGUOUS_SPECTRUM,
                                        r,
                                        List.of(file),
                                        "names the spectrum \""
                                                + spectrum.nativeId()
                                                + "\", which the spectrum file holds more than"
                                                + " once (again at index "
                                                + spectrum.index()
                                                + ")");
                            }
                            String entry;
                            try {
                                entry = PdvMgfWriter.entry(file, spectrum);
                            } catch (SpectrumFileException unwritable) {
                                throw refusal(
                                        Reason.UNREPRESENTABLE_SPECTRUM,
                                        r,
                                        List.of(file),
                                        "names a spectrum that cannot be written to an MGF"
                                                + " exactly: "
                                                + unwritable.getMessage());
                            }
                            byte[] bytes = entry.getBytes(StandardCharsets.UTF_8);
                            spectra.offset[slot] = written[0];
                            spectra.length[slot] = bytes.length;
                            spectra.precursor[slot] = spectrum.precursorMz().orElseThrow();
                            spectra.scan[slot] = spectrum.scanNumber().orElse(-1L);
                            try {
                                out.write(bytes);
                            } catch (IOException failed) {
                                throw new UncheckedIOException(failed);
                            }
                            written[0] += bytes.length;
                        });
            } catch (UncheckedIOException scratch) {
                throw scratch.getCause();
            } catch (PdvMzTabException refused) {
                throw refused;
            } catch (IOException unreadable) {
                throw unreadable(file, unreadable);
            }
            for (int slot = 0; slot < spectra.offset.length; slot++) {
                if (spectra.offset[slot] < 0) {
                    throw refusal(
                            Reason.NO_SPECTRUM,
                            spectra.firstRow[slot],
                            List.of(file),
                            "names the spectrum \""
                                    + spectra.nativeIdOf(slot)
                                    + "\", which is no MS2 spectrum of the spectrum file");
                }
            }
            spectra.slotOf = null;
        }

        // ---------------------------------------------------------------------------------
        // Pass 4: the document, then reading it back.

        private PdvMzTabExport write(Path directory) throws IOException {
            List<Integer> runs = new ArrayList<>();
            int[] msRunOf = new int[sources.size()];
            for (int s = 0; s < sources.size(); s++) {
                if (slots[s] != null) {
                    runs.add(s);
                    msRunOf[s] = runs.size();
                }
            }
            Path mzTab = staging.resolve(MZTAB_FILE_NAME);
            FileChannel[] mgfs = new FileChannel[sources.size()];
            FileChannel[] spectra = new FileChannel[sources.size()];
            long[] entries = new long[sources.size()];
            try (FileChannel document =
                            FileChannel.open(
                                    mzTab,
                                    StandardOpenOption.CREATE_NEW,
                                    StandardOpenOption.WRITE);
                    Writer out =
                            new BufferedWriter(
                                    Channels.newWriter(document, StandardCharsets.UTF_8));
                    FileChannel hitsIn = FileChannel.open(records, StandardOpenOption.READ)) {
                for (int s : runs) {
                    mgfs[s] =
                            FileChannel.open(
                                    staging.resolve(mgfName(msRunOf[s])),
                                    StandardOpenOption.CREATE_NEW,
                                    StandardOpenOption.WRITE);
                    spectra[s] = FileChannel.open(slots[s].file, StandardOpenOption.READ);
                }
                for (String line : metadata(directory, runs, msRunOf)) {
                    out.write(line);
                    out.write('\n');
                }
                out.write("PSH\t" + String.join("\t", COLUMNS) + "\n");
                Rows pass = new Rows(out, hitsIn, mgfs, spectra, msRunOf, entries);
                pass.writeAll();
                out.flush();
                document.force(true);
                for (int s : runs) {
                    mgfs[s].force(true);
                }
            } finally {
                for (int s = 0; s < sources.size(); s++) {
                    closeQuietly(mgfs[s]);
                    closeQuietly(spectra[s]);
                }
            }
            long written = readBack(mzTab, runs, msRunOf, entries);
            List<PdvMzTabMgf> files = new ArrayList<>();
            for (int s : runs) {
                files.add(
                        new PdvMzTabMgf(
                                msRunOf[s],
                                directory.resolve(mgfName(msRunOf[s])),
                                entries[s],
                                sources.get(s)));
            }
            return new PdvMzTabExport(
                    directory,
                    directory.resolve(MZTAB_FILE_NAME),
                    files,
                    tableRows[0],
                    tables.size() > 1 ? tableRows[1] : 0L,
                    written);
        }

        private List<String> metadata(Path directory, List<Integer> runs, int[] msRunOf) {
            List<String> lines = new ArrayList<>();
            lines.add("MTD\tmzTab-version\t1.0.0");
            lines.add("MTD\tmzTab-mode\tSummary");
            lines.add("MTD\tmzTab-type\tIdentification");
            lines.add(
                    "MTD\tdescription\tThe PSMs of one CometGUI run, generated only for PDV's"
                            + " denovo-gui display: not a deliverable and not an interchange file."
                            + " Every value is transcribed from Percolator's PSM tables, Comet's"
                            + " pepXML and the spectrum files; a value no source gives is null."
                            + " Rows: the target table, then the decoy table, each in file order."
                            + " accession: Percolator's proteinIds joined by '"
                            + ACCESSION_SEPARATOR
                            + "'. spectra_ref: the entry of the ms_run's MGF, one per row.");
            int software = 0;
            for (String version : cometVersions) {
                software++;
                lines.add(
                        "MTD\tsoftware["
                                + software
                                + "]\t[MS, MS:1002251, Comet, "
                                + version
                                + "]");
            }
            lines.add("MTD\tsoftware[" + (software + 1) + "]\t[MS, MS:1001490, Percolator, ]");
            lines.add("MTD\tpsm_search_engine_score[1]\t[MS, MS:1001492, percolator:score, ]");
            lines.add("MTD\tpsm_search_engine_score[2]\t[MS, MS:1001491, percolator:Q value, ]");
            lines.add("MTD\tpsm_search_engine_score[3]\t[MS, MS:1001493, percolator:PEP, ]");
            modifications(
                    lines,
                    "fixed_mod",
                    fixedModifications,
                    "[MS, MS:1002453, No fixed modifications searched, ]");
            modifications(
                    lines,
                    "variable_mod",
                    variableModifications,
                    "[MS, MS:1002454, No variable modifications searched, ]");
            lines.add("MTD\tcolunit-psm\tretention_time=[UO, UO:0000010, second, ]");
            for (int s : runs) {
                String run = "MTD\tms_run[" + msRunOf[s] + "]-";
                lines.add(run + "format\t[MS, MS:1001062, Mascot MGF format, ]");
                lines.add(
                        run + "id_format\t[MS, MS:1000774, multiple peak list nativeID format, ]");
                // Last of the ms_run lines: PDV maps every ms_run key to the text after the last
                // '/' of its value, so a later ms_run line would replace the file (P11-15).
                lines.add(run + "location\t" + directory.resolve(mgfName(msRunOf[s])).toUri());
            }
            return lines;
        }

        private static void modifications(
                List<String> lines, String key, Set<String> declared, String none) {
            if (declared.isEmpty()) {
                lines.add("MTD\t" + key + "[1]\t" + none);
                return;
            }
            int n = 0;
            for (String modification : declared) {
                n++;
                String[] parts = modification.split("\t", -1);
                String prefix = "MTD\t" + key + "[" + n + "]";
                lines.add(prefix + "\t[, CHEMMOD:" + parts[0] + ", , ]");
                lines.add(prefix + "-site\t" + parts[1]);
                lines.add(prefix + "-position\t" + parts[2]);
            }
        }

        /** The second pass over the Percolator rows, writing one line and one entry per row. */
        private final class Rows {

            private final Writer out;
            private final FileChannel hitsIn;
            private final FileChannel[] mgfs;
            private final FileChannel[] spectra;
            private final int[] msRunOf;
            private final long[] entries;
            private final ByteBuffer prefix = ByteBuffer.allocate(Integer.BYTES);
            private int r;

            Rows(
                    Writer out,
                    FileChannel hitsIn,
                    FileChannel[] mgfs,
                    FileChannel[] spectra,
                    int[] msRunOf,
                    long[] entries) {
                this.out = out;
                this.hitsIn = hitsIn;
                this.mgfs = mgfs;
                this.spectra = spectra;
                this.msRunOf = msRunOf;
                this.entries = entries;
            }

            void writeAll() throws IOException {
                for (int t = 0; t < tables.size(); t++) {
                    Path table = tables.get(t);
                    long read = 0;
                    try (ResultTableReader reader = ResultTableReader.open(table)) {
                        for (ResultRow row = reader.next(); row != null; row = reader.next()) {
                            if (r >= rows) {
                                throw changed(table);
                            }
                            line(t, row);
                            read++;
                            r++;
                        }
                    } catch (PercolatorOutputException unreadable) {
                        throw unreadable(table, unreadable);
                    }
                    if (read != tableRows[t]) {
                        throw changed(table);
                    }
                }
                if (r != rows) {
                    throw changed(tables.get(tables.size() - 1));
                }
            }

            private void line(int t, ResultRow row) throws IOException {
                Path table = tables.get(t);
                Located located = locate(t, row);
                int[] place = new int[2];
                PsmHit hit = record(recordOffset[r], place);
                PdvMzTabSource source = sources.get(located.source);
                if (place[0] != located.source
                        || place[1] != located.file
                        || hitKey(hit.startScan(), hit.charge(), hit.position()) != located.hit) {
                    throw changed(table);
                }
                Path pepXml = pepXmlOf(source, located.file);
                Optional<String> mismatch = hit.mismatch(row.peptide());
                if (mismatch.isPresent()) {
                    throw new PdvMzTabException(
                            Reason.PEPTIDE_MISMATCH,
                            row.psmId(),
                            List.of(table, pepXml),
                            mismatch.get(),
                            null);
                }
                Slots spectrum = slots[located.source];
                int slot = rowSlot[r];
                long scan = spectrum.scan[slot];
                if (scan >= 0 && scan != hit.startScan()) {
                    throw new PdvMzTabException(
                            Reason.SPECTRUM_MISMATCH,
                            row.psmId(),
                            List.of(pepXml, source.spectrumFile()),
                            "is scan "
                                    + hit.startScan()
                                    + ", but the spectrum its pepXML names, \""
                                    + hit.nativeId()
                                    + "\", is scan "
                                    + scan,
                            null);
                }
                int run = msRunOf[located.source];
                long index = entries[located.source];
                List<String> fields =
                        List.of(
                                hit.peptide(),
                                Integer.toString(r + 1),
                                accession(row, table),
                                NULL,
                                NULL,
                                NULL,
                                SEARCH_ENGINE,
                                row.scoreText(),
                                row.qValue().text(),
                                row.posteriorErrorProbabilityText(),
                                hit.modifications(),
                                hit.retentionTime().orElse(NULL),
                                Integer.toString(hit.charge()),
                                spectrum.precursor[slot],
                                NULL,
                                "ms_run[" + run + "]:index=" + index,
                                hit.previous(),
                                hit.next(),
                                NULL,
                                NULL,
                                hit.proForma(),
                                t == 1 ? "1" : "0",
                                row.psmId(),
                                row.scoreText(),
                                row.qValue().text(),
                                row.posteriorErrorProbabilityText());
                StringBuilder line = new StringBuilder("PSM");
                for (int column = 0; column < fields.size(); column++) {
                    line.append('\t')
                            .append(
                                    text(
                                            fields.get(column),
                                            row.psmId(),
                                            table,
                                            COLUMNS.get(column)));
                }
                out.write(line.append('\n').toString());
                long offset = spectrum.offset[slot];
                long length = spectrum.length[slot];
                long copied = 0;
                while (copied < length) {
                    long moved =
                            spectra[located.source].transferTo(
                                    offset + copied, length - copied, mgfs[located.source]);
                    if (moved <= 0) {
                        throw new IOException(
                                "the export's scratch file "
                                        + slots[located.source].file
                                        + " ended early");
                    }
                    copied += moved;
                }
                entries[located.source]++;
            }

            private PsmHit record(long offset, int[] place) throws IOException {
                prefix.clear();
                readFully(prefix, offset);
                byte[] bytes = new byte[prefix.getInt(0)];
                readFully(ByteBuffer.wrap(bytes), offset + Integer.BYTES);
                try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
                    place[0] = in.readInt();
                    place[1] = in.readInt();
                    return PsmHit.read(in);
                }
            }

            private void readFully(ByteBuffer buffer, long offset) throws IOException {
                long at = offset;
                while (buffer.hasRemaining()) {
                    int read = hitsIn.read(buffer, at);
                    if (read < 0) {
                        throw new IOException(
                                "the export's scratch file " + records + " ended early");
                    }
                    at += read;
                }
            }
        }

        /**
         * Reads the written document back and requires it to hold exactly the rows read: one PSH
         * line with every column, one PSM line per row with a field per column, each {@code
         * ms_run}'s references {@code 0..n-1} in order, and each MGF exactly {@code n} entries.
         */
        private long readBack(Path mzTab, List<Integer> runs, int[] msRunOf, long[] entries)
                throws IOException {
            Map<String, Integer> sourceOfRun = new HashMap<>();
            for (int s : runs) {
                sourceOfRun.put(Integer.toString(msRunOf[s]), s);
            }
            long[] next = new long[sources.size()];
            long psms = 0;
            int headers = 0;
            int refColumn = COLUMNS.indexOf("spectra_ref");
            try (BufferedReader in = Files.newBufferedReader(mzTab, StandardCharsets.UTF_8)) {
                for (String line = in.readLine(); line != null; line = in.readLine()) {
                    String[] fields = line.split("\t", -1);
                    if (line.startsWith("PSH\t")) {
                        headers++;
                        if (fields.length != COLUMNS.size() + 1) {
                            throw incomplete(
                                    mzTab, "its PSH line has " + fields.length + " fields");
                        }
                    } else if (line.startsWith("PSM\t")) {
                        if (fields.length != COLUMNS.size() + 1) {
                            throw incomplete(
                                    mzTab,
                                    "PSM line " + (psms + 1) + " has " + fields.length + " fields");
                        }
                        String ref = fields[refColumn + 1];
                        int at = ref.indexOf("]:index=");
                        Integer s =
                                ref.startsWith("ms_run[") && at > 7
                                        ? sourceOfRun.get(ref.substring(7, at))
                                        : null;
                        if (s == null
                                || !ref.substring(at + "]:index=".length())
                                        .equals(Long.toString(next[s]))) {
                            throw incomplete(
                                    mzTab,
                                    "PSM line " + (psms + 1) + " has the spectra_ref " + ref);
                        }
                        next[s]++;
                        psms++;
                    } else if (!line.startsWith("MTD\t")) {
                        throw incomplete(mzTab, "it holds the line \"" + line + "\"");
                    }
                }
            }
            if (headers != 1 || psms != rows) {
                throw incomplete(
                        mzTab,
                        "it holds "
                                + headers
                                + " PSH lines and "
                                + psms
                                + " PSM lines for the "
                                + rows
                                + " rows of the Percolator tables");
            }
            for (int s : runs) {
                Path mgf = staging.resolve(mgfName(msRunOf[s]));
                long count = countEntries(mgf);
                if (count != next[s] || count != entries[s]) {
                    throw incomplete(
                            mgf,
                            "it holds "
                                    + count
                                    + " entries for the "
                                    + next[s]
                                    + " PSM lines of ms_run["
                                    + msRunOf[s]
                                    + "]");
                }
            }
            return psms;
        }

        private static long countEntries(Path mgf) throws IOException {
            try (Stream<String> lines = Files.lines(mgf, StandardCharsets.UTF_8)) {
                return lines.filter("BEGIN IONS"::equals).count();
            }
        }

        private PdvMzTabException incomplete(Path file, String what) {
            return new PdvMzTabException(
                    Reason.INCOMPLETE,
                    null,
                    tables,
                    "the export's own check of " + file + " failed: " + what,
                    null);
        }

        // ---------------------------------------------------------------------------------
        // Naming rows and files in refusals.

        private PdvMzTabException refusal(Reason reason, int r, List<Path> files, String detail) {
            return new PdvMzTabException(reason, psmIdOf(r), files, detail, null);
        }

        private PdvMzTabException changed(Path table) {
            return new PdvMzTabException(
                    Reason.SOURCE_CHANGED,
                    null,
                    List.of(table),
                    "the Percolator table changed while it was being exported",
                    null);
        }

        private static PdvMzTabException unreadable(Path file, IOException cause) {
            return new PdvMzTabException(
                    Reason.UNREADABLE_SOURCE,
                    null,
                    List.of(file),
                    "a source could not be read: " + cause.getMessage(),
                    cause);
        }

        /** The table a row is in. */
        private Path tableOf(int r) {
            return tables.get(r < tableRows[0] || tables.size() == 1 ? 0 : 1);
        }

        private String rowPlace(int r) {
            boolean target = r < tableRows[0] || tables.size() == 1;
            long line = (target ? r : r - tableRows[0]) + 2;
            return "line " + line + " of " + tableOf(r);
        }

        /** A row's PSMId, read again from its table: refusals only. */
        private String psmIdOf(int r) {
            boolean target = r < tableRows[0] || tables.size() == 1;
            long wanted = (target ? r : r - tableRows[0]);
            try (ResultTableReader reader = ResultTableReader.open(tableOf(r))) {
                long at = 0;
                for (ResultRow row = reader.next(); row != null; row = reader.next()) {
                    if (at++ == wanted) {
                        return row.psmId();
                    }
                }
            } catch (IOException unreadable) {
                // Named by its place instead.
            }
            return "at " + rowPlace(r);
        }

        private int sourceOf(int r) throws IOException {
            try (ResultTableReader reader = ResultTableReader.open(tableOf(r))) {
                boolean target = r < tableRows[0] || tables.size() == 1;
                long wanted = (target ? r : r - tableRows[0]);
                long at = 0;
                for (ResultRow row = reader.next(); row != null; row = reader.next()) {
                    if (at++ == wanted) {
                        return sourceByBase.get(row.spectrumReference().orElseThrow().base());
                    }
                }
            }
            throw changed(tableOf(r));
        }

        private List<Path> filesOf(int r, int s) {
            boolean decoy = !(r < tableRows[0] || tables.size() == 1);
            PdvMzTabSource source = sources.get(s);
            return List.of(
                    tableOf(r),
                    pepXmlOf(source, decoy && source.decoyPepXml().isPresent() ? 1 : 0));
        }

        private static Path pepXmlOf(PdvMzTabSource source, int file) {
            return file == 1 ? source.decoyPepXml().orElseThrow() : source.pepXml();
        }

        private static String mgfName(int msRun) {
            return MGF_PREFIX + msRun + PdvMgfWriter.EXTENSION;
        }

        private static void closeQuietly(FileChannel channel) {
            if (channel != null) {
                try {
                    channel.close();
                } catch (IOException ignored) {
                    // Closing after a failure: the failure is what is reported.
                }
            }
        }
    }

    /** Where a row's hit is. */
    private record Located(int source, int file, long hit, long query) {}

    /** The spectra one source's PSMs name, and where each was put. */
    private static final class Slots {

        private Map<String, Integer> slotOf;
        private final int[] firstRow;
        private final long[] offset;
        private final int[] length;
        private final String[] precursor;
        private final long[] scan;
        private final Path file;

        Slots(Map<String, Integer> slotOf, List<Integer> firstRow, Path file) {
            this.slotOf = slotOf;
            this.firstRow = firstRow.stream().mapToInt(Integer::intValue).toArray();
            this.offset = new long[slotOf.size()];
            Arrays.fill(offset, -1L);
            this.length = new int[slotOf.size()];
            this.precursor = new String[slotOf.size()];
            this.scan = new long[slotOf.size()];
            this.file = file;
        }

        String nativeIdOf(int slot) {
            for (Map.Entry<String, Integer> entry : slotOf.entrySet()) {
                if (entry.getValue() == slot) {
                    return entry.getKey();
                }
            }
            return "#" + slot;
        }
    }
}
