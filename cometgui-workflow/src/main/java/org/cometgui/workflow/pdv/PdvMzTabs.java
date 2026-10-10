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

package org.cometgui.workflow.pdv;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.ports.HashService;
import org.cometgui.domain.run.RunId;
import org.cometgui.domain.run.RunIdentity;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.domain.run.SpectrumInput;
import org.cometgui.domain.secrets.SecretRedactor;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.provenance.events.ProvenanceEvent;
import org.cometgui.provenance.events.ProvenanceEventLog;
import org.cometgui.provenance.events.ProvenanceEventLogReader;
import org.cometgui.provenance.events.ProvenanceEventType;
import org.cometgui.results.filtering.store.TableKind;
import org.cometgui.tools.pdv.PdvMzTabExport;
import org.cometgui.tools.pdv.PdvMzTabExporter;
import org.cometgui.tools.pdv.PdvMzTabMgf;
import org.cometgui.tools.pdv.PdvMzTabRequest;
import org.cometgui.tools.pdv.PdvMzTabSource;
import org.cometgui.tools.pdv.PdvSpectrumSupport;
import org.cometgui.workflow.steps.RunResultFiles;

/**
 * Makes a finished run's mzTab for PDV ({@code R-PDV-02}), or finds the one already made, and
 * records it in the run's event log (design decision P11-4) -- what "Open run in PDV" calls before
 * it launches {@code denovo-gui}.
 *
 * <h2>Where, and from what</h2>
 *
 * <p>The export is the directory {@value #MZTAB_DIRECTORY} in the run's PDV directory, {@code
 * <run>/pdv/mztab/} (P11-17: PDV writes beside its inputs, so never beside the source spectra):
 * {@link PdvMzTabExporter#MZTAB_FILE_NAME} and one MGF per spectrum file with PSMs. It is made by
 * {@link PdvMzTabExporter} from
 *
 * <ul>
 *   <li>the run's Percolator target PSM table, and its decoy PSM table when the run wrote one
 *       ({@link RunResultFiles});
 *   <li>for each of the run's spectrum files, in input order: the Comet pepXML {@code
 *       <base>.pep.xml} of the run that searched it ({@code searched}: the run itself, or for a
 *       Percolator rerun the run it was derived from), and {@code <base>}{@value
 *       #DECOY_PEP_XML_SUFFIX} beside it when that exists (Comet's {@code decoy_search = 2}); every
 *       {@code PSMId} from that file starts with the absolute {@code -N} base, {@code
 *       outputs/comet/<base>}, which Comet wrote into its PIN;
 *   <li>and the spectrum file itself, whose format the PDV version must support ({@link
 *       PdvSpectrumSupport#check}, refused before anything is read).
 * </ul>
 *
 * <h2>Reuse: the rule</h2>
 *
 * <p>Every source above is hashed with the one hasher. The export is <strong>reused</strong> --
 * nothing written, nothing recorded -- exactly when the run's event log holds an {@link
 * ProvenanceEventType#MZTAB_WRITTEN} event whose last occurrence records the same exporter {@link
 * PdvMzTabExporter#FORMAT_VERSION}, the same sources in the same order with the same roles, paths
 * and SHA-256s, and an mzTab and MGFs that still exist at the recorded paths with the recorded
 * SHA-256s. Anything else -- a changed or added source, a changed exporter, a missing or altered
 * output, no event -- deletes the whole export directory (and with it what PDV wrote beside it) and
 * writes it again. The sources are hashed again after the export, and an export whose sources
 * changed while it ran is deleted and refused.
 *
 * <h2>The record</h2>
 *
 * <p>One {@link ProvenanceEventType#MZTAB_WRITTEN} event per export, appended to the run's {@code
 * events.log} after the export is complete and verified: {@value #RUN_ID}, {@value #FORMAT};
 * {@value #MZTAB_PATH}, {@value #MZTAB_MD5}, {@value #MZTAB_SHA256}; {@value #MGF_COUNT} and, for
 * each MGF {@code n} from 1, {@code mgf.<n>.path}, {@code .md5}, {@code .sha256}, {@code .entries}
 * and {@code .spectrum-file}; {@value #SOURCE_COUNT} and, for each source {@code n} from 1, {@code
 * source.<n>.role} (one of {@value #ROLE_TARGET_PSMS}, {@value #ROLE_DECOY_PSMS}, {@value
 * #ROLE_PEPXML}, {@value #ROLE_DECOY_PEPXML}, {@value #ROLE_SPECTRA}), {@code .path}, {@code .md5}
 * and {@code .sha256}; and the counts {@value #PSMS_TARGET}, {@value #PSMS_DECOY}, {@value
 * #PSMS_IN} (their sum) and {@value #PSMS_OUT} (the PSM lines the mzTab holds, counted by reading
 * it back).
 *
 * <h2>A run that is executing</h2>
 *
 * <p>Refused, as Phase 10 refuses an export: the workflow engine holds the run's event log while it
 * executes, and its results are not final.
 */
public final class PdvMzTabs {

    /** The export's directory in the run's PDV directory. */
    public static final String MZTAB_DIRECTORY = "mztab";

    /** What Comet's {@code decoy_search = 2} names the decoy pepXML after the base. */
    public static final String DECOY_PEP_XML_SUFFIX = ".decoy.pep.xml";

    /** The run. */
    public static final String RUN_ID = ProvenanceEvent.RUN_ID_KEY;

    /** The exporter's {@link PdvMzTabExporter#FORMAT_VERSION}. */
    public static final String FORMAT = "mztab.format";

    /** The mzTab's absolute path. */
    public static final String MZTAB_PATH = "mztab.path";

    /** The mzTab's MD5. */
    public static final String MZTAB_MD5 = "mztab.md5";

    /** The mzTab's SHA-256. */
    public static final String MZTAB_SHA256 = "mztab.sha256";

    /** How many MGFs there are. */
    public static final String MGF_COUNT = "mgf.count";

    /** How many sources were read. */
    public static final String SOURCE_COUNT = "source.count";

    /** The rows of the target table. */
    public static final String PSMS_TARGET = "psms.target";

    /** The rows of the decoy table; 0 when the run wrote none. */
    public static final String PSMS_DECOY = "psms.decoy";

    /** Every row read. */
    public static final String PSMS_IN = "psms.in";

    /** Every PSM line written. */
    public static final String PSMS_OUT = "psms.out";

    /** A source's role: Percolator's target PSM table. */
    public static final String ROLE_TARGET_PSMS = "percolator-target-psms";

    /** A source's role: Percolator's decoy PSM table. */
    public static final String ROLE_DECOY_PSMS = "percolator-decoy-psms";

    /** A source's role: a Comet pepXML. */
    public static final String ROLE_PEPXML = "pepxml";

    /** A source's role: a Comet decoy pepXML. */
    public static final String ROLE_DECOY_PEPXML = "decoy-pepxml";

    /** A source's role: a spectrum file. */
    public static final String ROLE_SPECTRA = "spectra";

    private static final String STAGING_PREFIX = "." + MZTAB_DIRECTORY + "-staging-";

    private final HashService hashes;

    private final SecretRedactor redactor;

    private final Clock clock;

    private final Supplier<Set<RunId>> executing;

    /**
     * The exports, over the application's one hasher and event log.
     *
     * @param hashes the one hasher
     * @param redactor the one secret rule set
     * @param clock dates every event
     * @param executing the runs the session's engine is executing now
     */
    public PdvMzTabs(
            HashService hashes,
            SecretRedactor redactor,
            Clock clock,
            Supplier<Set<RunId>> executing) {
        this.hashes = Objects.requireNonNull(hashes, "hashes");
        this.redactor = Objects.requireNonNull(redactor, "redactor");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.executing = Objects.requireNonNull(executing, "executing");
    }

    /**
     * Where a run's export is.
     *
     * @param run the run
     * @return {@code <run>/pdv/mztab}
     */
    public static Path directoryOf(RunLayout run) {
        return run.root().resolve(PsmFigures.PDV_DIRECTORY).resolve(MZTAB_DIRECTORY);
    }

    /**
     * The run's mzTab for PDV: the recorded one if its sources are unchanged, else a new one.
     *
     * @param run the run whose results are shown, whose event log records the export and under
     *     whose PDV directory it is written
     * @param identity that run's recorded identity: its identifier and spectrum files
     * @param searched the run whose {@code outputs/comet/} holds the pepXML: {@code run} itself, or
     *     for a derived run the run it was derived from
     * @param pdvVersion the PDV version the export is for, whose spectrum support is checked
     * @return the export
     * @throws org.cometgui.tools.pdv.PdvMzTabException if a PSM or source cannot be represented
     *     faithfully; nothing is then at the export directory
     * @throws org.cometgui.tools.pdv.UnsupportedSpectrumFileException if a spectrum file cannot be
     *     given to that PDV version
     * @throws IOException if the run is executing, a source changed during the export, or a file
     *     cannot be read, hashed or written
     */
    public synchronized PdvMzTab prepare(
            RunLayout run, RunIdentity identity, RunLayout searched, ToolVersion pdvVersion)
            throws IOException {
        Objects.requireNonNull(run, "run");
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(searched, "searched");
        Objects.requireNonNull(pdvVersion, "pdvVersion");
        if (executing.get().contains(identity.runId())) {
            throw new IOException(
                    "run "
                            + identity.runId().value()
                            + " is executing: its results are not final and the workflow engine"
                            + " holds its event log. Open it in PDV when the run has ended.");
        }
        PdvSpectrumSupport support = PdvSpectrumSupport.forVersion(pdvVersion);
        Path targets = RunResultFiles.table(run, TableKind.TARGET_PSMS);
        Path decoyTable = RunResultFiles.table(run, TableKind.DECOY_PSMS);
        Optional<Path> decoys =
                Files.isRegularFile(decoyTable) ? Optional.of(decoyTable) : Optional.empty();
        List<Source> inputs = new ArrayList<>();
        inputs.add(new Source(ROLE_TARGET_PSMS, targets));
        decoys.ifPresent(table -> inputs.add(new Source(ROLE_DECOY_PSMS, table)));
        List<PdvMzTabSource> sources = new ArrayList<>();
        for (SpectrumInput input : identity.spectra()) {
            Path base = searched.cometOutputBase(input.base());
            Path pepXml = searched.pepXmlFile(input.base());
            Path decoyPepXml = Path.of(base + DECOY_PEP_XML_SUFFIX);
            Optional<Path> decoy =
                    Files.isRegularFile(decoyPepXml) ? Optional.of(decoyPepXml) : Optional.empty();
            Path spectra = input.file().path();
            sources.add(
                    new PdvMzTabSource(
                            base.toString(), spectra, support.check(spectra), pepXml, decoy));
            inputs.add(new Source(ROLE_PEPXML, pepXml));
            decoy.ifPresent(file -> inputs.add(new Source(ROLE_DECOY_PEPXML, file)));
            inputs.add(new Source(ROLE_SPECTRA, spectra));
        }
        Map<String, String> sourceRecord = sourceRecord(inputs);
        Path directory = directoryOf(run);
        Optional<PdvMzTab> earlier = reusable(run, directory, sourceRecord);
        if (earlier.isPresent()) {
            return earlier.get();
        }
        Path pdv =
                Files.createDirectories(
                        Objects.requireNonNull(directory.getParent(), "the PDV directory"));
        deleteTree(directory);
        try (Stream<Path> stale = Files.list(pdv)) {
            for (Path leftover : stale.toList()) {
                if (String.valueOf(leftover.getFileName()).startsWith(STAGING_PREFIX)) {
                    deleteTree(leftover);
                }
            }
        }
        PdvMzTabExport export =
                PdvMzTabExporter.export(new PdvMzTabRequest(targets, decoys, sources, directory));
        Map<String, String> after = sourceRecord(inputs);
        if (!after.equals(sourceRecord)) {
            deleteTree(directory);
            throw new IOException(
                    "a source of run "
                            + identity.runId().value()
                            + "'s mzTab changed while it was being written, so the mzTab was"
                            + " deleted; open the run in PDV again");
        }
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put(RUN_ID, identity.runId().value());
        payload.put(FORMAT, PdvMzTabExporter.FORMAT_VERSION);
        FileHashes mzTabHashes = hashes.hash(export.mzTab());
        payload.put(MZTAB_PATH, export.mzTab().toString());
        payload.put(MZTAB_MD5, mzTabHashes.md5());
        payload.put(MZTAB_SHA256, mzTabHashes.sha256());
        payload.put(MGF_COUNT, Integer.toString(export.mgfs().size()));
        List<Path> mgfs = new ArrayList<>();
        for (PdvMzTabMgf mgf : export.mgfs()) {
            String key = "mgf." + mgf.msRun() + ".";
            FileHashes mgfHashes = hashes.hash(mgf.file());
            payload.put(key + "path", mgf.file().toString());
            payload.put(key + "md5", mgfHashes.md5());
            payload.put(key + "sha256", mgfHashes.sha256());
            payload.put(key + "entries", Long.toString(mgf.entries()));
            payload.put(key + "spectrum-file", mgf.source().spectrumFile().toString());
            mgfs.add(mgf.file());
        }
        payload.putAll(sourceRecord);
        payload.put(PSMS_TARGET, Long.toString(export.targetPsms()));
        payload.put(PSMS_DECOY, Long.toString(export.decoyPsms()));
        payload.put(PSMS_IN, Long.toString(export.targetPsms() + export.decoyPsms()));
        payload.put(PSMS_OUT, Long.toString(export.psmsWritten()));
        try (ProvenanceEventLog log =
                ProvenanceEventLog.openAppend(run.eventLogFile(), redactor, clock)) {
            log.append(ProvenanceEventType.MZTAB_WRITTEN, payload);
        }
        return new PdvMzTab(export.mzTab(), mgfs, mzTabHashes, export.psmsWritten(), false);
    }

    /** The sources' part of the record: role, path and checksums of each, in order. */
    private Map<String, String> sourceRecord(List<Source> inputs) throws IOException {
        Map<String, String> record = new LinkedHashMap<>();
        record.put(SOURCE_COUNT, Integer.toString(inputs.size()));
        for (int index = 0; index < inputs.size(); index++) {
            Source input = inputs.get(index);
            FileHashes checksums = hashes.hash(input.path());
            String key = "source." + (index + 1) + ".";
            record.put(key + "role", input.role());
            record.put(key + "path", input.path().toString());
            record.put(key + "md5", checksums.md5());
            record.put(key + "sha256", checksums.sha256());
        }
        return record;
    }

    /** The recorded export, if the rule above says it may be reused. */
    private Optional<PdvMzTab> reusable(RunLayout run, Path directory, Map<String, String> sources)
            throws IOException {
        if (!Files.isRegularFile(run.eventLogFile()) || !Files.isDirectory(directory)) {
            return Optional.empty();
        }
        ProvenanceEvent last = null;
        for (ProvenanceEvent event :
                ProvenanceEventLogReader.recover(run.eventLogFile(), redactor).events()) {
            if (event.type() == ProvenanceEventType.MZTAB_WRITTEN) {
                last = event;
            }
        }
        if (last == null) {
            return Optional.empty();
        }
        Map<String, String> payload = last.payload();
        if (!PdvMzTabExporter.FORMAT_VERSION.equals(payload.get(FORMAT))) {
            return Optional.empty();
        }
        for (Map.Entry<String, String> source : sources.entrySet()) {
            if (!source.getValue().equals(payload.get(source.getKey()))) {
                return Optional.empty();
            }
        }
        Path mzTab = directory.resolve(PdvMzTabExporter.MZTAB_FILE_NAME);
        if (!mzTab.toString().equals(payload.get(MZTAB_PATH))
                || !unchanged(mzTab, payload.get(MZTAB_SHA256))) {
            return Optional.empty();
        }
        int count = Integer.parseInt(payload.getOrDefault(MGF_COUNT, "0"));
        List<Path> mgfs = new ArrayList<>();
        for (int n = 1; n <= count; n++) {
            String recorded = payload.get("mgf." + n + ".path");
            if (recorded == null) {
                return Optional.empty();
            }
            Path mgf = Path.of(recorded);
            if (!directory.equals(mgf.getParent())
                    || !unchanged(mgf, payload.get("mgf." + n + ".sha256"))) {
                return Optional.empty();
            }
            mgfs.add(mgf);
        }
        if (count == 0) {
            return Optional.empty();
        }
        return Optional.of(
                new PdvMzTab(
                        mzTab,
                        mgfs,
                        new FileHashes(payload.get(MZTAB_MD5), payload.get(MZTAB_SHA256)),
                        Long.parseLong(payload.get(PSMS_OUT)),
                        true));
    }

    private boolean unchanged(Path file, String sha256) throws IOException {
        return sha256 != null
                && Files.isRegularFile(file)
                && hashes.hash(file).sha256().equals(sha256);
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    /** One file the export reads, and what it is. */
    private record Source(String role, Path path) {}
}
