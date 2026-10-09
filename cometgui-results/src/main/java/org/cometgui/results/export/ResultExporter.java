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
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.locks.ReentrantLock;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.ports.HashService;
import org.cometgui.domain.run.RunId;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.domain.secrets.SecretRedactor;
import org.cometgui.provenance.events.ProvenanceEvent;
import org.cometgui.provenance.events.ProvenanceEventLog;
import org.cometgui.provenance.events.ProvenanceEventType;
import org.cometgui.provenance.io.AtomicDocumentWriter;
import org.cometgui.provenance.io.ContentWriter;
import org.cometgui.results.filtering.FilterCounts;
import org.cometgui.results.filtering.QValueFilter;
import org.cometgui.results.filtering.Visibility;
import org.cometgui.results.filtering.store.Category;
import org.cometgui.results.filtering.store.TableKind;
import org.cometgui.results.parser.LearnedWeights;
import org.cometgui.results.parser.ResultRow;
import org.cometgui.results.parser.ResultTableReader;
import org.cometgui.results.parser.WeightsReader;
import org.cometgui.results.parser.WeightsSummary;

/**
 * Writes a run's derived exports ({@code R-RES-04}, {@code R-RES-01}, {@code R-PERC-07}, design
 * decision P10-7): a filtered Percolator table, or the learned feature weights, as a new file under
 * the run's {@link RunLayout#exportsDirectory() exports/}, with a JSON sidecar beside it and one
 * {@code export.written} event in the run's provenance event log.
 *
 * <p><strong>A filtered table</strong> ({@link #exportTable}) holds the raw table's header line and
 * exactly the rows of one {@link Category} under one {@link QValueFilter}, each row's bytes copied
 * verbatim from the raw file -- line terminator included -- in the raw file's order. Where a row
 * falls is decided by {@link QValueFilter#classify}, the one q-value predicate (design decision
 * P10-1), applied to the row the one {@link ResultTableReader} parsed; so a row's category in the
 * export is its category in every view ({@code R-RES-02}, phase 10 gate item 8). The view's text
 * filter and sort are view-only and are not applied; the sidecar says so. The table is streamed --
 * read once to hash it, once to copy it -- and never held in memory.
 *
 * <p><strong>The weights</strong> ({@link #exportWeights}) are the {@link WeightsSummary} as {@link
 * WeightsTable} writes it.
 *
 * <p><strong>What is written, in order, and what a failure leaves.</strong>
 *
 * <ol>
 *   <li>The source is hashed with the one {@link HashService} (MD5 and SHA-256 in one pass, {@code
 *       R-PROV-03}).
 *   <li>A fresh name is chosen ({@link ExportFiles}: an existing file is never overwritten) and the
 *       export is written through the one {@link AtomicDocumentWriter}: a temporary file in {@code
 *       exports/}, forced, then renamed. A failure here -- an unreadable or refused table, a table
 *       whose size or modification time changed while it was copied -- leaves no file under the
 *       final name and no temporary file.
 *   <li>The export is hashed, and its sidecar written atomically.
 *   <li>One {@link ProvenanceEventType#EXPORT_WRITTEN} event is appended to the run's event log.
 * </ol>
 *
 * <p>If step 3 or 4 fails the export and its sidecar are deleted and the failure is thrown: <b>an
 * export is never reported as made unless its provenance event was recorded</b>, and a failure
 * leaves nothing behind that could be mistaken for one. Two things remain possible and are stated
 * rather than hidden: a process killed between steps 2 and 4 leaves an export without a sidecar or
 * event (an export file with no sidecar beside it is incomplete and is not an export of record);
 * and an append whose force to the device fails may leave the event in the log although the export
 * it names has been removed.
 *
 * <p><strong>The raw files are only ever read</strong>: the reader and the copier open the table
 * for reading only, nothing is written outside {@code exports/} except the event log's one line,
 * and nothing is ever written under {@code outputs/}.
 *
 * <p><strong>One export at a time.</strong> Every export holds {@link ExportFiles#LOCK} throughout,
 * because each opens the run's event log for its one append, and two logs open on one file would
 * number their events twice. For the same reason an export is for a run that is not executing: the
 * engine holds the run's event log open while it runs. Thread-safe.
 */
public final class ResultExporter {

    private final RunLayout run;
    private final RunId runId;
    private final String cometGuiVersion;
    private final Clock clock;
    private final HashService hasher;
    private final SecretRedactor redactor;
    private final EventRecorder recorder;

    /** Records one export's event; a seam so that a test can make the append fail. */
    @FunctionalInterface
    interface EventRecorder {

        /**
         * Appends the event.
         *
         * @param payload its payload
         * @return the event as written
         * @throws IOException if it could not be recorded
         */
        ProvenanceEvent record(Map<String, String> payload) throws IOException;
    }

    /**
     * An exporter for one run.
     *
     * @param run the run's directories: {@code exports/} and the event log are its
     * @param runId the run's identifier, written into every sidecar and event
     * @param cometGuiVersion this build's version, written into every sidecar
     * @param clock the time an export is made, for its name, sidecar and event
     * @param hasher the one hasher
     * @param redactor the one rule set, applied to every sidecar value and event payload
     * @throws NullPointerException if an argument is {@code null}
     * @throws IllegalArgumentException if {@code cometGuiVersion} is blank
     */
    public ResultExporter(
            RunLayout run,
            RunId runId,
            String cometGuiVersion,
            Clock clock,
            HashService hasher,
            SecretRedactor redactor) {
        this(run, runId, cometGuiVersion, clock, hasher, redactor, null);
    }

    /**
     * As the public constructor, with the event recorder given.
     *
     * @param recorder records an event; {@code null} for the run's provenance event log
     */
    ResultExporter(
            RunLayout run,
            RunId runId,
            String cometGuiVersion,
            Clock clock,
            HashService hasher,
            SecretRedactor redactor,
            EventRecorder recorder) {
        this.run = Objects.requireNonNull(run, "run");
        this.runId = Objects.requireNonNull(runId, "runId");
        this.cometGuiVersion = Objects.requireNonNull(cometGuiVersion, "cometGuiVersion");
        if (cometGuiVersion.isBlank()) {
            throw new IllegalArgumentException("the CometGUI version must not be blank");
        }
        this.clock = Objects.requireNonNull(clock, "clock");
        this.hasher = Objects.requireNonNull(hasher, "hasher");
        this.redactor = Objects.requireNonNull(redactor, "redactor");
        this.recorder = recorder != null ? recorder : this::appendToRunLog;
    }

    /**
     * Exports the rows of one category of a raw Percolator table under one filter.
     *
     * @param table the raw table, only ever read
     * @param kind which table it is
     * @param filter the filter applied: the PSM filter for a PSM table, the peptide filter for a
     *     peptide table
     * @param category the rows written
     * @return what was written
     * @throws IllegalArgumentException if the filter is the other table kind's
     * @throws org.cometgui.results.parser.PercolatorOutputException if the reader refuses the table
     * @throws IOException if the table changed while it was exported, or a file cannot be read or
     *     written, or the event cannot be recorded; nothing is then left under the export's name
     * @throws NullPointerException if an argument is {@code null}
     */
    public TableExport exportTable(
            Path table, TableKind kind, QValueFilter filter, Category category) throws IOException {
        Objects.requireNonNull(table, "table");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(category, "category");
        kind.check(filter);
        Path source = table.toAbsolutePath().normalize();
        return locked(() -> exportTableLocked(source, kind, filter, category));
    }

    /**
     * Exports a learned-feature-weights summary ({@code AC-RES-08}).
     *
     * @param summary the summary; its artefact must still hold exactly the weights summarised
     * @return what was written
     * @throws IOException if the artefact changed since it was summarised or while it was read, a
     *     file cannot be read or written, or the event cannot be recorded; nothing is then left
     *     under the export's name
     * @throws NullPointerException if {@code summary} is {@code null}
     */
    public WeightsExport exportWeights(WeightsSummary summary) throws IOException {
        Objects.requireNonNull(summary, "summary");
        return locked(() -> exportWeightsLocked(summary));
    }

    /** One export's work, run under {@link ExportFiles#LOCK}. */
    @FunctionalInterface
    private interface Export<T> {
        T run() throws IOException;
    }

    private static <T> T locked(Export<T> export) throws IOException {
        ReentrantLock lock = ExportFiles.LOCK;
        lock.lock();
        try {
            return export.run();
        } finally {
            lock.unlock();
        }
    }

    private TableExport exportTableLocked(
            Path source, TableKind kind, QValueFilter filter, Category category)
            throws IOException {
        Instant created = now();
        Path directory = exportsDirectory();
        Snapshot snapshot = Snapshot.of(source);
        FileHashes sourceHashes = hasher.hash(source);
        String stem =
                ExportVocabulary.table(kind)
                        + "_q"
                        + filter.text()
                        + "_"
                        + ExportVocabulary.category(category)
                        + "_"
                        + ExportFiles.stamp(created);
        Path target = ExportFiles.fresh(directory, stem);
        TableCopy copy = new TableCopy(source, snapshot, filter, category);
        AtomicDocumentWriter.write(target, copy);
        FilterCounts before = copy.counts();
        long written = copy.rowsWritten();
        return complete(
                target,
                (exportHashes, exportSize) -> {
                    ExportSidecar.Common common =
                            common(
                                    created,
                                    target,
                                    exportSize,
                                    exportHashes,
                                    source,
                                    snapshot,
                                    sourceHashes);
                    String sidecar =
                            ExportSidecar.table(
                                    common,
                                    ExportVocabulary.table(kind),
                                    ExportVocabulary.filter(filter),
                                    filter.text(),
                                    ExportVocabulary.category(category),
                                    before,
                                    written,
                                    redactor);
                    Map<String, String> payload =
                            payload(ExportSidecar.FILTERED_TABLE, common, target);
                    payload.put("export.table", ExportVocabulary.table(kind));
                    payload.put("export.category", ExportVocabulary.category(category));
                    payload.put("filter.name", ExportVocabulary.filter(filter));
                    payload.put("filter.cutoff", filter.text());
                    payload.put("counts.total", Long.toString(before.total()));
                    payload.put("counts.passing", Long.toString(before.passing()));
                    payload.put("counts.failing", Long.toString(before.failing()));
                    payload.put("counts.unknown-q-value", Long.toString(before.unknownQValue()));
                    payload.put("rows.written", Long.toString(written));
                    return new Recording(sidecar, payload);
                },
                (event, exportHashes) ->
                        new TableExport(
                                target,
                                ExportFiles.sidecarOf(target),
                                source,
                                kind,
                                filter,
                                category,
                                before,
                                written,
                                sourceHashes,
                                exportHashes,
                                created,
                                event.sequence()));
    }

    private WeightsExport exportWeightsLocked(WeightsSummary summary) throws IOException {
        Instant created = now();
        Path directory = exportsDirectory();
        Path source = summary.file().toAbsolutePath().normalize();
        Snapshot snapshot = Snapshot.of(source);
        FileHashes sourceHashes = hasher.hash(source);
        LearnedWeights now = WeightsReader.read(summary.file());
        if (!now.equals(summary.weights())) {
            throw new IOException(
                    "The weights artefact "
                            + source
                            + " no longer holds the weights that were summarised; summarise it"
                            + " again before exporting");
        }
        snapshot.requireUnchanged("read");
        Path target =
                ExportFiles.fresh(
                        directory, "learned-feature-weights_" + ExportFiles.stamp(created));
        AtomicDocumentWriter.write(target, out -> WeightsTable.write(summary, out));
        int splits = summary.splitCount();
        int features = summary.features().size();
        return complete(
                target,
                (exportHashes, exportSize) -> {
                    ExportSidecar.Common common =
                            common(
                                    created,
                                    target,
                                    exportSize,
                                    exportHashes,
                                    source,
                                    snapshot,
                                    sourceHashes);
                    String sidecar = ExportSidecar.weights(common, splits, features, redactor);
                    Map<String, String> payload =
                            payload(ExportSidecar.LEARNED_FEATURE_WEIGHTS, common, target);
                    payload.put("weights.split-count", Integer.toString(splits));
                    payload.put("weights.feature-count", Integer.toString(features));
                    return new Recording(sidecar, payload);
                },
                (event, exportHashes) ->
                        new WeightsExport(
                                target,
                                ExportFiles.sidecarOf(target),
                                source,
                                splits,
                                features,
                                sourceHashes,
                                exportHashes,
                                created,
                                event.sequence()));
    }

    /** The sidecar's text and the event's payload, made once the export's checksums are known. */
    private record Recording(String sidecar, Map<String, String> payload) {}

    @FunctionalInterface
    private interface RecordMaker {
        Recording make(FileHashes exportHashes, long exportSize);
    }

    @FunctionalInterface
    private interface ResultMaker<T> {
        T make(ProvenanceEvent event, FileHashes exportHashes);
    }

    /**
     * Steps 3 and 4: hashes the export, writes its sidecar, records its event; on any failure
     * deletes both files and rethrows.
     */
    private <T> T complete(Path target, RecordMaker records, ResultMaker<T> results)
            throws IOException {
        Path sidecar = ExportFiles.sidecarOf(target);
        try {
            FileHashes exportHashes = hasher.hash(target);
            Recording record = records.make(exportHashes, Files.size(target));
            AtomicDocumentWriter.write(sidecar, record.sidecar());
            ProvenanceEvent event = recorder.record(record.payload());
            return results.make(event, exportHashes);
        } catch (IOException | RuntimeException failed) {
            removeAfterFailure(target, failed);
            removeAfterFailure(sidecar, failed);
            if (failed instanceof IOException unrecorded) {
                throw new IOException(
                        "The export "
                                + target.getFileName()
                                + " could not be completed and has been removed: "
                                + unrecorded.getMessage(),
                        unrecorded);
            }
            throw failed;
        }
    }

    private static void removeAfterFailure(Path file, Exception failed) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException alsoFailed) {
            failed.addSuppressed(alsoFailed);
        }
    }

    private ProvenanceEvent appendToRunLog(Map<String, String> payload) throws IOException {
        try (ProvenanceEventLog log =
                ProvenanceEventLog.openAppend(run.eventLogFile(), redactor, clock)) {
            return log.append(ProvenanceEventType.EXPORT_WRITTEN, payload);
        }
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }

    private Path exportsDirectory() throws IOException {
        return Files.createDirectories(run.exportsDirectory());
    }

    private ExportSidecar.Common common(
            Instant created,
            Path target,
            long exportSize,
            FileHashes exportHashes,
            Path source,
            Snapshot snapshot,
            FileHashes sourceHashes) {
        return new ExportSidecar.Common(
                runId.value(),
                cometGuiVersion,
                created,
                runRelative(target),
                exportSize,
                exportHashes,
                runRelative(source),
                snapshot.size(),
                sourceHashes);
    }

    private Map<String, String> payload(String export, ExportSidecar.Common common, Path target) {
        Map<String, String> payload = new TreeMap<>();
        payload.put(ProvenanceEvent.RUN_ID_KEY, common.runId());
        payload.put("export.kind", export);
        payload.put(ProvenanceEvent.FILE_PATH_KEY, common.filePath());
        payload.put(ProvenanceEvent.FILE_MD5_KEY, common.fileHashes().md5());
        payload.put(ProvenanceEvent.FILE_SHA256_KEY, common.fileHashes().sha256());
        payload.put("export.sidecar", runRelative(ExportFiles.sidecarOf(target)));
        payload.put("source.path", common.sourcePath());
        payload.put("source.md5", common.sourceHashes().md5());
        payload.put("source.sha256", common.sourceHashes().sha256());
        return payload;
    }

    /**
     * A path as the sidecar and event name it: relative to the run directory, with {@code /}
     * between names, when it is inside it; otherwise absolute.
     */
    String runRelative(Path path) {
        Path absolute = path.toAbsolutePath().normalize();
        if (!absolute.startsWith(run.root())) {
            return absolute.toString();
        }
        StringBuilder relative = new StringBuilder();
        for (Path name : run.root().relativize(absolute)) {
            if (relative.length() > 0) {
                relative.append('/');
            }
            relative.append(name);
        }
        return relative.toString();
    }

    /** A source file's size and modification time, to tell whether it changed while exported. */
    private record Snapshot(Path file, long size, FileTime modified) {

        static Snapshot of(Path file) throws IOException {
            return new Snapshot(file, Files.size(file), Files.getLastModifiedTime(file));
        }

        void requireUnchanged(String doing) throws IOException {
            if (Files.size(file) != size || !Files.getLastModifiedTime(file).equals(modified)) {
                throw new IOException(
                        "The file "
                                + file
                                + " changed while it was being "
                                + doing
                                + " for export; nothing was exported");
            }
        }
    }

    /**
     * Streams one table's selected rows to the export: the one reader parses each row and says
     * where its bytes are, {@link QValueFilter#classify} says where it falls, and {@link
     * ForwardCopier} copies the header and each selected row verbatim. A row's bytes run from its
     * offset to the next row's, so its terminator goes with it; the last row's run to the end of
     * the file as it was when the export began.
     */
    private static final class TableCopy implements ContentWriter {

        private final Path source;
        private final Snapshot snapshot;
        private final QValueFilter filter;
        private final Category category;
        private long passing;
        private long failing;
        private long unknown;
        private long written;

        TableCopy(Path source, Snapshot snapshot, QValueFilter filter, Category category) {
            this.source = source;
            this.snapshot = snapshot;
            this.filter = filter;
            this.category = category;
        }

        @Override
        public void writeTo(OutputStream out) throws IOException {
            try (ResultTableReader reader = ResultTableReader.open(source);
                    FileChannel channel = FileChannel.open(source, StandardOpenOption.READ)) {
                ForwardCopier copier = new ForwardCopier(source, channel);
                boolean headerCopied = false;
                long pending = -1;
                for (ResultRow row = reader.next(); row != null; row = reader.next()) {
                    long start = reader.lastRowOffset();
                    if (!headerCopied) {
                        copier.copy(0, start, out);
                        headerCopied = true;
                    }
                    if (pending >= 0) {
                        copier.copy(pending, start, out);
                        pending = -1;
                    }
                    Visibility where = filter.classify(row);
                    switch (where) {
                        case PASSES -> passing++;
                        case FAILS -> failing++;
                        case UNKNOWN_Q_VALUE -> unknown++;
                    }
                    if (category.includes(where)) {
                        pending = start;
                        written++;
                    }
                }
                if (!headerCopied) {
                    copier.copy(0, snapshot.size(), out);
                }
                if (pending >= 0) {
                    copier.copy(pending, snapshot.size(), out);
                }
            }
            snapshot.requireUnchanged("copied");
        }

        FilterCounts counts() {
            return new FilterCounts(passing + failing + unknown, passing, failing, unknown);
        }

        long rowsWritten() {
            return written;
        }
    }
}
