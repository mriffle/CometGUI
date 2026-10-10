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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.ports.HashService;
import org.cometgui.domain.run.RunId;
import org.cometgui.domain.run.RunIdentity;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.domain.secrets.SecretRedactor;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.provenance.events.ProvenanceEvent;
import org.cometgui.provenance.events.ProvenanceEventLogReader;
import org.cometgui.provenance.events.ProvenanceEventType;
import org.cometgui.provenance.hashing.StreamingHashService;
import org.cometgui.results.filtering.store.TableKind;
import org.cometgui.tools.pdv.PdvMzTabException;
import org.cometgui.tools.pdv.PdvSpectrumSupport;
import org.cometgui.tools.pdv.UnsupportedSpectrumFileException;
import org.cometgui.workflow.steps.RunResultFiles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Making a finished run's mzTab for PDV through the workflow: where it is written, what the run's
 * event log records, when an earlier export is reused and when it is written again, and what is
 * refused. Every checksum a test compares is computed here with {@link PdvFixtures#digest}.
 */
class PdvMzTabsTest {

    private static final ToolVersion PDV = PdvSpectrumSupport.VERIFIED_VERSION;
    private static final String SCAN_101 = PdvFixtures.SCAN_101;

    @TempDir private Path project;

    private RunLayout run;
    private RunIdentity identity;
    private Path spectra;
    private String psmBase;
    private final Set<RunId> executing = new TreeSet<>((a, b) -> a.value().compareTo(b.value()));
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-10T12:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void finishedRun() throws IOException {
        run = PdvFixtures.run(project, PdvFixtures.RUN_ID);
        spectra = PdvFixtures.mgf(project.resolve("inputs").resolve("sample.mgf"), SCAN_101);
        identity = PdvFixtures.identity(PdvFixtures.RUN_ID, spectra);
        String base = identity.spectra().get(0).base();
        psmBase = run.cometOutputBase(base).toString();
        pepXml(run.pepXmlFile(base), hit("PEPTIDEK", "R", "A"), hit("KEDITPEP", "K", "-"));
        targets(row(1, "2.5", "0.001", "R.PEPTIDEK.A"));
        decoys(row(2, "-1.5", "0.9", "K.KEDITPEP.-"));
    }

    private PdvMzTabs mzTabs(HashService hashes) {
        return new PdvMzTabs(
                hashes, SecretRedactor.patternsOnly(), clock, () -> Set.copyOf(executing));
    }

    private PdvMzTabs mzTabs() {
        return mzTabs(new StreamingHashService());
    }

    private String row(int rank, String score, String q, String peptide) {
        return psmBase
                + "_101_2_"
                + rank
                + "\t"
                + score
                + "\t"
                + q
                + "\t0.01\t"
                + peptide
                + "\tsp|P1|ONE";
    }

    private void targets(String... rows) throws IOException {
        table(RunResultFiles.table(run, TableKind.TARGET_PSMS), rows);
    }

    private void decoys(String... rows) throws IOException {
        table(RunResultFiles.table(run, TableKind.DECOY_PSMS), rows);
    }

    private static void table(Path file, String... rows) throws IOException {
        Files.createDirectories(Objects.requireNonNull(file.getParent(), "a directory"));
        StringBuilder text =
                new StringBuilder(
                        "PSMId\tscore\tq-value\tposterior_error_prob\tpeptide\tproteinIds\n");
        for (String row : rows) {
            text.append(row).append('\n');
        }
        Files.writeString(file, text.toString(), StandardCharsets.UTF_8);
    }

    private static String hit(String peptide, String previous, String next) {
        return "   <search_hit hit_rank=\"1\" peptide=\""
                + peptide
                + "\" peptide_prev_aa=\""
                + previous
                + "\" peptide_next_aa=\""
                + next
                + "\" protein=\"sp|P1|ONE\" num_tot_proteins=\"1\""
                + " calc_neutral_pep_mass=\"900.400000\" massdiff=\"0.001000\">\n"
                + "    <search_score name=\"xcorr\" value=\"2.0\"/>\n"
                + "   </search_hit>\n";
    }

    private static void pepXml(Path file, String... hits) throws IOException {
        StringBuilder text =
                new StringBuilder(
                        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                                + "<msms_pipeline_analysis"
                                + " xmlns=\"http://regis-web.systemsbiology.net/pepXML\">\n"
                                + " <msms_run_summary base_name=\"/data/sample\""
                                + " raw_data_type=\"raw\" raw_data=\".mgf\">\n"
                                + "  <search_summary base_name=\"/run/sample\""
                                + " search_engine=\"Comet\""
                                + " search_engine_version=\"2026.03 rev. 0 (fa08489)\""
                                + " precursor_mass_type=\"monoisotopic\""
                                + " fragment_mass_type=\"monoisotopic\" search_id=\"1\">\n"
                                + "  </search_summary>\n"
                                + "  <spectrum_query spectrum=\"sample.00101.00101.2\""
                                + " spectrumNativeID=\""
                                + SCAN_101
                                + "\" start_scan=\"101\" end_scan=\"101\""
                                + " precursor_neutral_mass=\"900.400000\" assumed_charge=\"2\""
                                + " index=\"1\" retention_time_sec=\"61.5\">\n"
                                + "   <search_result>\n");
        int rank = 0;
        for (String hit : hits) {
            rank++;
            text.append(hit.replace("hit_rank=\"1\"", "hit_rank=\"" + rank + "\""));
        }
        text.append("   </search_result>\n  </spectrum_query>\n </msms_run_summary>\n")
                .append("</msms_pipeline_analysis>\n");
        Files.createDirectories(Objects.requireNonNull(file.getParent(), "a directory"));
        Files.writeString(file, text.toString(), StandardCharsets.UTF_8);
    }

    private List<ProvenanceEvent> written() throws IOException {
        if (!Files.exists(run.eventLogFile())) {
            return List.of();
        }
        return ProvenanceEventLogReader.recover(run.eventLogFile()).events().stream()
                .filter(event -> event.type() == ProvenanceEventType.MZTAB_WRITTEN)
                .toList();
    }

    private Path directory() {
        return run.root().resolve("pdv").resolve("mztab");
    }

    private static List<String> names(Path directory) throws IOException {
        try (Stream<Path> files = Files.list(directory)) {
            return files.map(file -> String.valueOf(file.getFileName())).sorted().toList();
        }
    }

    private static List<String> psmLines(Path mzTab) throws IOException {
        List<String> lines = new ArrayList<>();
        for (String line : Files.readAllLines(mzTab, StandardCharsets.UTF_8)) {
            if (line.startsWith("PSM\t")) {
                lines.add(line);
            }
        }
        return lines;
    }

    @Test
    @DisplayName("a finished run's mzTab and MGF are written under <run>/pdv/mztab and recorded")
    void writesAndRecords() throws IOException {
        PdvMzTab mzTab = mzTabs().prepare(run, identity, run, PDV);

        Path directory = directory();
        Path file = directory.resolve("psms.mztab");
        Path mgf = directory.resolve("ms_run-1.mgf");
        Path target = RunResultFiles.table(run, TableKind.TARGET_PSMS);
        Path decoy = RunResultFiles.table(run, TableKind.DECOY_PSMS);
        Path pepXml = run.pepXmlFile(identity.spectra().get(0).base());
        List<ProvenanceEvent> events = written();
        assertEquals(1, events.size(), "one mztab.written event");
        Map<String, String> payload = events.get(0).payload();
        assertAll(
                () -> assertEquals(file, mzTab.mzTab()),
                () -> assertEquals(List.of(mgf), mzTab.mgfs()),
                () -> assertFalse(mzTab.reused()),
                () -> assertEquals(2, mzTab.psms()),
                () -> assertEquals(PdvMzTabs.directoryOf(run), directory),
                () -> assertEquals(List.of("ms_run-1.mgf", "psms.mztab"), names(directory)),
                () ->
                        assertEquals(
                                PdvFixtures.digest(file, "SHA-256"), mzTab.mzTabHashes().sha256()),
                () -> assertEquals(PdvFixtures.digest(file, "MD5"), mzTab.mzTabHashes().md5()),
                () -> assertEquals(PdvFixtures.RUN_ID, payload.get("run.id")),
                () -> assertEquals("1", payload.get("mztab.format")),
                () -> assertEquals(file.toString(), payload.get("mztab.path")),
                () ->
                        assertEquals(
                                PdvFixtures.digest(file, "SHA-256"), payload.get("mztab.sha256")),
                () -> assertEquals(PdvFixtures.digest(file, "MD5"), payload.get("mztab.md5")),
                () -> assertEquals("1", payload.get("mgf.count")),
                () -> assertEquals(mgf.toString(), payload.get("mgf.1.path")),
                () -> assertEquals(PdvFixtures.digest(mgf, "SHA-256"), payload.get("mgf.1.sha256")),
                () -> assertEquals(PdvFixtures.digest(mgf, "MD5"), payload.get("mgf.1.md5")),
                () -> assertEquals("2", payload.get("mgf.1.entries")),
                () -> assertEquals(spectra.toString(), payload.get("mgf.1.spectrum-file")),
                () -> assertEquals("4", payload.get("source.count")),
                () -> assertEquals("percolator-target-psms", payload.get("source.1.role")),
                () -> assertEquals(target.toString(), payload.get("source.1.path")),
                () ->
                        assertEquals(
                                PdvFixtures.digest(target, "SHA-256"),
                                payload.get("source.1.sha256")),
                () -> assertEquals(PdvFixtures.digest(target, "MD5"), payload.get("source.1.md5")),
                () -> assertEquals("percolator-decoy-psms", payload.get("source.2.role")),
                () -> assertEquals(decoy.toString(), payload.get("source.2.path")),
                () ->
                        assertEquals(
                                PdvFixtures.digest(decoy, "SHA-256"),
                                payload.get("source.2.sha256")),
                () -> assertEquals("pepxml", payload.get("source.3.role")),
                () -> assertEquals(pepXml.toString(), payload.get("source.3.path")),
                () ->
                        assertEquals(
                                PdvFixtures.digest(pepXml, "SHA-256"),
                                payload.get("source.3.sha256")),
                () -> assertEquals("spectra", payload.get("source.4.role")),
                () -> assertEquals(spectra.toString(), payload.get("source.4.path")),
                () ->
                        assertEquals(
                                PdvFixtures.digest(spectra, "SHA-256"),
                                payload.get("source.4.sha256")),
                () -> assertEquals("1", payload.get("psms.target")),
                () -> assertEquals("1", payload.get("psms.decoy")),
                () -> assertEquals("2", payload.get("psms.in")),
                () -> assertEquals("2", payload.get("psms.out")),
                () -> assertEquals(2, psmLines(file).size()),
                () -> assertTrue(psmLines(file).get(1).contains("\tKEDITPEP\t")));
        assertEquals(
                new TreeSet<>(
                        List.of(
                                "run.id",
                                "mztab.format",
                                "mztab.path",
                                "mztab.md5",
                                "mztab.sha256",
                                "mgf.count",
                                "mgf.1.path",
                                "mgf.1.md5",
                                "mgf.1.sha256",
                                "mgf.1.entries",
                                "mgf.1.spectrum-file",
                                "source.count",
                                "source.1.role",
                                "source.1.path",
                                "source.1.md5",
                                "source.1.sha256",
                                "source.2.role",
                                "source.2.path",
                                "source.2.md5",
                                "source.2.sha256",
                                "source.3.role",
                                "source.3.path",
                                "source.3.md5",
                                "source.3.sha256",
                                "source.4.role",
                                "source.4.path",
                                "source.4.md5",
                                "source.4.sha256",
                                "psms.target",
                                "psms.decoy",
                                "psms.in",
                                "psms.out")),
                new TreeSet<>(payload.keySet()),
                "the payload holds exactly the documented keys");
    }

    @Test
    @DisplayName("unchanged sources reuse the recorded export: nothing written, nothing recorded")
    void reuse() throws IOException {
        PdvMzTab first = mzTabs().prepare(run, identity, run, PDV);
        byte[] bytes = Files.readAllBytes(first.mzTab());
        Instant modified = Files.getLastModifiedTime(first.mzTab()).toInstant();
        Files.writeString(directory().resolve("psms.mztab.db"), "what PDV leaves beside it");

        PdvMzTab second = mzTabs().prepare(run, identity, run, PDV);

        assertAll(
                () -> assertTrue(second.reused()),
                () -> assertEquals(first.mzTab(), second.mzTab()),
                () -> assertEquals(first.mgfs(), second.mgfs()),
                () -> assertEquals(first.mzTabHashes(), second.mzTabHashes()),
                () -> assertEquals(2, second.psms()),
                () -> assertEquals(1, written().size()),
                () -> assertArrayEquals(bytes, Files.readAllBytes(second.mzTab())),
                () -> assertEquals(modified, Files.getLastModifiedTime(second.mzTab()).toInstant()),
                () -> assertTrue(Files.exists(directory().resolve("psms.mztab.db"))));
    }

    @Test
    @DisplayName("a changed source, or an altered output, writes the whole export again")
    void rewrite() throws IOException {
        mzTabs().prepare(run, identity, run, PDV);
        Files.writeString(directory().resolve("psms.mztab.db"), "what PDV leaves beside it");
        targets(row(1, "3.5", "0.002", "R.PEPTIDEK.A"));

        PdvMzTab afterSource = mzTabs().prepare(run, identity, run, PDV);

        assertAll(
                () -> assertFalse(afterSource.reused()),
                () -> assertEquals(2, written().size()),
                () -> assertTrue(psmLines(afterSource.mzTab()).get(0).contains("\t3.5\t0.002\t")),
                () -> assertEquals(List.of("ms_run-1.mgf", "psms.mztab"), names(directory())));

        Files.writeString(afterSource.mzTab(), "altered", StandardCharsets.UTF_8);
        PdvMzTab afterOutput = mzTabs().prepare(run, identity, run, PDV);
        assertAll(
                () -> assertFalse(afterOutput.reused()),
                () -> assertEquals(3, written().size()),
                () -> assertEquals(2, psmLines(afterOutput.mzTab()).size()));

        Files.writeString(afterOutput.mgfs().get(0), "altered", StandardCharsets.UTF_8);
        assertFalse(mzTabs().prepare(run, identity, run, PDV).reused());
        Files.delete(directory().resolve("ms_run-1.mgf"));
        assertFalse(mzTabs().prepare(run, identity, run, PDV).reused());
        assertEquals(5, written().size());
    }

    @Test
    @DisplayName("an event from another exporter version, or naming another file, is not reused")
    void recordMustMatch() throws IOException {
        mzTabs().prepare(run, identity, run, PDV);
        String log = Files.readString(run.eventLogFile(), StandardCharsets.UTF_8);
        Files.writeString(
                run.eventLogFile(),
                log.replace("\"mztab.format\":\"1\"", "\"mztab.format\":\"0\""),
                StandardCharsets.UTF_8);
        assertFalse(mzTabs().prepare(run, identity, run, PDV).reused());
        String second = Files.readString(run.eventLogFile(), StandardCharsets.UTF_8);
        int last = second.lastIndexOf("\"mgf.1.path\":\"");
        Files.writeString(
                run.eventLogFile(),
                second.substring(0, last) + second.substring(last).replace("ms_run-1.mgf", "x.mgf"),
                StandardCharsets.UTF_8);
        assertFalse(mzTabs().prepare(run, identity, run, PDV).reused());
        assertTrue(mzTabs().prepare(run, identity, run, PDV).reused());
        assertEquals(3, written().size());
    }

    @Test
    @DisplayName("an executing run is refused before anything is read or written")
    void executingRunIsRefused() throws IOException {
        executing.add(identity.runId());
        IOException refused =
                assertThrows(IOException.class, () -> mzTabs().prepare(run, identity, run, PDV));
        assertAll(
                () ->
                        assertEquals(
                                "run "
                                        + PdvFixtures.RUN_ID
                                        + " is executing: its results are not final and the"
                                        + " workflow engine holds its event log. Open it in PDV"
                                        + " when the run has ended.",
                                refused.getMessage()),
                () -> assertFalse(Files.exists(run.root().resolve("pdv"))),
                () -> assertFalse(Files.exists(run.eventLogFile())));
    }

    @Test
    @DisplayName("a PDV version whose spectrum support is unverified is refused before any write")
    void unsupportedVersion() {
        assertThrows(
                UnsupportedSpectrumFileException.class,
                () -> mzTabs().prepare(run, identity, run, ToolVersion.parse("2.6.0")));
        assertFalse(Files.exists(run.root().resolve("pdv")));
    }

    @Test
    @DisplayName(
            "a refused export leaves nothing, not even the earlier export, and records nothing")
    void refusedExport() throws IOException {
        mzTabs().prepare(run, identity, run, PDV);
        targets(row(1, "2.5", "0.001", "R.PEPTIDEQ.A"));

        PdvMzTabException refused =
                assertThrows(
                        PdvMzTabException.class, () -> mzTabs().prepare(run, identity, run, PDV));
        assertAll(
                () -> assertEquals(PdvMzTabException.Reason.PEPTIDE_MISMATCH, refused.reason()),
                () -> assertEquals(Optional.of(psmBase + "_101_2_1"), refused.psmId()),
                () -> assertFalse(Files.exists(directory())),
                () -> assertEquals(1, written().size()));
    }

    @Test
    @DisplayName("Comet's separate decoy pepXML is read for decoy rows and recorded as a source")
    void separateDecoyPepXml() throws IOException {
        String base = identity.spectra().get(0).base();
        pepXml(
                Path.of(run.cometOutputBase(base) + ".decoy.pep.xml"),
                hit("QQQK", "K", "-"),
                hit("RRRK", "K", "-"));
        decoys(row(2, "-1.5", "0.9", "K.RRRK.-"));

        PdvMzTab mzTab = mzTabs().prepare(run, identity, run, PDV);

        Map<String, String> payload = written().get(0).payload();
        assertAll(
                () -> assertTrue(psmLines(mzTab.mzTab()).get(1).contains("\tRRRK\t")),
                () -> assertEquals("5", payload.get("source.count")),
                () -> assertEquals("decoy-pepxml", payload.get("source.4.role")),
                () ->
                        assertEquals(
                                run.cometOutputBase(base) + ".decoy.pep.xml",
                                payload.get("source.4.path")),
                () -> assertEquals("spectra", payload.get("source.5.role")));
    }

    @Test
    @DisplayName(
            "a derived run's export reads the searched run's pepXML and is recorded in its own")
    void derivedRun() throws IOException {
        RunLayout rerun = PdvFixtures.run(project, "rerun");
        Files.createDirectories(RunResultFiles.percolatorOutputDirectory(rerun));
        Files.copy(
                RunResultFiles.table(run, TableKind.TARGET_PSMS),
                RunResultFiles.table(rerun, TableKind.TARGET_PSMS));

        PdvMzTab mzTab = mzTabs().prepare(rerun, identity, run, PDV);

        assertAll(
                () -> assertEquals(rerun.root().resolve("pdv/mztab/psms.mztab"), mzTab.mzTab()),
                () -> assertEquals(1, mzTab.psms()),
                () -> assertTrue(Files.exists(rerun.eventLogFile())),
                () -> assertFalse(Files.exists(run.eventLogFile())));
    }

    @Test
    @DisplayName("a source that changes during the export deletes it and is refused")
    void sourceChangedDuringExport() throws IOException {
        Path target = RunResultFiles.table(run, TableKind.TARGET_PSMS);
        AtomicInteger calls = new AtomicInteger();
        HashService hashes = new StreamingHashService();
        HashService changing =
                file -> {
                    FileHashes real = hashes.hash(file);
                    if (file.equals(target) && calls.incrementAndGet() == 2) {
                        return new FileHashes("0".repeat(32), "0".repeat(64));
                    }
                    return real;
                };
        IOException refused =
                assertThrows(
                        IOException.class, () -> mzTabs(changing).prepare(run, identity, run, PDV));
        assertAll(
                () ->
                        assertTrue(
                                refused.getMessage()
                                        .contains("changed while it was being written")),
                () -> assertFalse(Files.exists(directory())),
                () -> assertEquals(0, written().size()));
    }

    @Test
    @DisplayName("a staging directory a crash left behind is removed by the next export")
    void staleStaging() throws IOException {
        Path stale = Files.createDirectories(run.root().resolve("pdv").resolve(".mztab-staging-1"));
        Files.writeString(stale.resolve("psms.mztab"), "half", StandardCharsets.UTF_8);
        Path unrelated = Files.createDirectories(run.root().resolve("pdv").resolve("figures"));

        mzTabs().prepare(run, identity, run, PDV);

        assertAll(
                () -> assertFalse(Files.exists(stale)),
                () -> assertTrue(Files.isDirectory(unrelated)),
                () -> assertEquals(List.of("figures", "mztab"), names(run.root().resolve("pdv"))));
    }
}
