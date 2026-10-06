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

package org.cometgui.workflow.steps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.cometgui.domain.params.FastaDecoyCensus;
import org.cometgui.domain.project.ProjectId;
import org.cometgui.domain.project.ProjectLayout;
import org.cometgui.domain.run.IndexMode;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.model.DecoySource;
import org.cometgui.params.comet.model.ValueOrigin;
import org.cometgui.params.comet.validation.ValidationReport;
import org.cometgui.provenance.hashing.CachingHashService;
import org.cometgui.tools.comet.CometIndexHeaderReader;
import org.cometgui.tools.comet.FastaDecoyScanner;
import org.cometgui.workflow.storage.ProjectStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The pre-run check over made-up files: every problem it reports, word for word, and the facts it
 * hands the validator. No Comet process is involved.
 */
class PreRunChecksTest {

    @TempDir private Path directory;

    private FakeSearch fake;

    private ProjectLayout project;

    private CachingHashService hashes;

    private PreRunChecks checks;

    @BeforeEach
    void setUp() throws IOException {
        fake = FakeSearch.create(directory);
        project = new ProjectLayout(fake.project());
        new ProjectStore(Clock.systemUTC()).create(project, new ProjectId("checks"));
        hashes = FakeSearch.hashes();
        checks = FakeSearch.checks(hashes, false);
    }

    private PreRunChecks.Outcome check(CometParameters model, List<Path> spectra, IndexMode mode)
            throws IOException {
        return checks.check(project, model, spectra, fake.selection(), mode);
    }

    private PreRunChecks.Outcome check(CometParameters model) throws IOException {
        return check(model, fake.spectra(), IndexMode.NONE);
    }

    private CometParameters model() {
        return fake.model(DecoySource.COMET_INTERNAL_CONCATENATED);
    }

    private CometParameters withDatabase(String database) {
        return model().withText("database_name", database, ValueOrigin.USER);
    }

    @Test
    @DisplayName("a good search: no problem, no error, and the FASTA's census handed over")
    void aGoodSearchIsNotBlocked() throws IOException {
        PreRunChecks.Outcome outcome = check(model());
        PreRunReport report = outcome.report();
        assertEquals(List.of(), report.problems());
        assertFalse(report.validation().hasErrors(), report::message);
        assertFalse(report.blocked());
        assertEquals("nothing blocks the run", report.message());
        assertEquals(
                Optional.of(new FastaDecoyCensus(fake.fasta(), "DECOY_", 2, 0, Optional.empty())),
                report.facts().census());
        assertEquals(Optional.empty(), report.facts().index());
        assertEquals(Optional.empty(), outcome.cacheEntry());
        assertFalse(outcome.cacheComplete());
    }

    @Test
    @DisplayName("the selected Comet must be the parameters' release")
    void releaseMismatch() throws IOException {
        CometSelection older =
                new CometSelection(
                        fake.executable(),
                        ToolVersion.parse("2026.02.2"),
                        fake.executableSha256(),
                        false,
                        Optional.empty());
        PreRunReport report =
                checks.check(project, model(), fake.spectra(), older, IndexMode.NONE).report();
        assertEquals(
                List.of(
                        "the parameters are for Comet 2026.03.0, but the selected Comet is"
                                + " 2026.02.2"),
                report.problems());
        assertTrue(report.blocked());
    }

    @Test
    @DisplayName("a missing or non-executable Comet, and one whose bytes changed, are refused")
    void executable() throws IOException {
        Path missing = fake.root().resolve("no-comet");
        CometSelection absent =
                new CometSelection(
                        missing,
                        ToolVersion.parse(RealComet.NEWER),
                        fake.executableSha256(),
                        false,
                        Optional.empty());
        assertEquals(
                List.of(
                        "the selected Comet executable "
                                + missing
                                + " does not exist or cannot be executed"),
                checks.check(project, model(), fake.spectra(), absent, IndexMode.NONE)
                        .report()
                        .problems());

        CometSelection changed =
                new CometSelection(
                        fake.executable(),
                        ToolVersion.parse(RealComet.NEWER),
                        "0".repeat(64),
                        false,
                        Optional.empty());
        assertEquals(
                List.of(
                        "the Comet executable "
                                + fake.executable()
                                + " has SHA-256 "
                                + fake.executableSha256()
                                + ", but Comet 2026.03.0 was selected at "
                                + "0".repeat(64)
                                + "; it has changed since it was selected"),
                checks.check(project, model(), fake.spectra(), changed, IndexMode.NONE)
                        .report()
                        .problems());

        Files.setPosixFilePermissions(
                fake.executable(), PosixFilePermissions.fromString("rw-------"));
        assertEquals(
                List.of(
                        "the selected Comet executable "
                                + fake.executable()
                                + " does not exist or cannot be executed"),
                check(model()).report().problems());
    }

    @Test
    @DisplayName(
            "spectrum files: none, missing, an unknown extension, RAW off Windows, an unusable"
                    + " name")
    void spectra() throws IOException {
        assertEquals(
                List.of("there is no spectrum file to search"),
                check(model(), List.of(), IndexMode.NONE).report().problems());

        Path missing = fake.root().resolve("inputs/k562_9.mzML");
        assertEquals(
                List.of("spectrum file 2 (" + missing + ") does not exist or cannot be read"),
                check(model(), List.of(fake.spectra().get(0), missing), IndexMode.NONE)
                        .report()
                        .problems());

        Path text = Files.writeString(fake.root().resolve("inputs/spectra.txt"), "x\n");
        assertEquals(
                List.of(
                        "spectrum file 1 ("
                                + text
                                + ") has none of the spectrum extensions Comet reads: .mzML,"
                                + " .mzXML, .mgf, .ms2, .cms2, .bms2, .raw"),
                check(model(), List.of(text), IndexMode.NONE).report().problems());

        Path raw = Files.writeString(fake.root().resolve("inputs/k562_5.RAW"), "x\n");
        assertEquals(
                List.of(
                        "spectrum file 1 ("
                                + raw
                                + ") is a Thermo RAW file, which Comet reads only on Windows"),
                check(model(), List.of(raw), IndexMode.NONE).report().problems());
        assertEquals(
                List.of(),
                FakeSearch.checks(hashes, true)
                        .check(project, model(), List.of(raw), fake.selection(), IndexMode.NONE)
                        .report()
                        .problems());

        Path dots = Files.writeString(fake.root().resolve("inputs/..mzML"), "x\n");
        assertEquals(
                List.of(
                        "spectrum file 1 (\"..mzML\") cannot name a Comet output: its base name"
                                + " would be \".\", which names a directory, not a file in"
                                + " outputs/comet"),
                check(model(), List.of(dots), IndexMode.NONE).report().problems());
    }

    @Test
    @DisplayName("the database: relative, empty, missing, not a FASTA; a link is resolved")
    void database() throws IOException {
        assertEquals(
                List.of(
                        "database_name = db.fasta must name the database by its absolute path;"
                                + " Comet runs in the run directory, where a relative path would"
                                + " name another file"),
                check(withDatabase("db.fasta")).report().problems());
        assertEquals(
                List.of(
                        "database_name =  must name the database by its absolute path; Comet"
                                + " runs in the run directory, where a relative path would name"
                                + " another file"),
                check(withDatabase("")).report().problems());
        Path missing = fake.root().resolve("inputs/none.fasta");
        assertEquals(
                List.of(
                        "the database "
                                + missing
                                + " (database_name) does not exist or cannot be read"),
                check(withDatabase(missing.toString())).report().problems());

        Path notFasta = Files.writeString(fake.root().resolve("inputs/notes.fasta"), "hello\n");
        String scanned;
        try {
            FastaDecoyScanner.scan(notFasta, "DECOY_");
            throw new AssertionError("the scanner accepted a file that is not a FASTA");
        } catch (IOException refused) {
            scanned = refused.getMessage();
        }
        assertEquals(
                List.of(
                        "the decoys in "
                                + notFasta
                                + " cannot be counted, so the decoy configuration cannot be"
                                + " checked: "
                                + scanned),
                check(withDatabase(notFasta.toString())).report().problems());

        Path link = Files.createSymbolicLink(fake.root().resolve("linked.fasta"), fake.fasta());
        PreRunReport linked = check(withDatabase(link.toString())).report();
        assertEquals(List.of(), linked.problems());
        assertEquals(fake.fasta(), linked.facts().census().orElseThrow().fasta());
    }

    @Test
    @DisplayName("an existing index: its header and its FASTA's census go to the validator")
    void anExistingIndex() throws IOException {
        Path index =
                IndexHeaders.write(
                        fake.root().resolve("inputs/db.fasta.idx"), IndexHeaders.v5("db.fasta"));
        PreRunReport report = check(withDatabase(index.toString())).report();
        assertEquals(List.of(), report.problems());
        assertEquals(CometIndexHeaderReader.read(index), report.facts().index().orElseThrow());
        assertEquals(fake.fasta(), report.facts().census().orElseThrow().fasta());

        assertEquals(
                List.of(
                        "database_name = "
                                + index
                                + " is already a Comet index, but the index mode fragment-ion"
                                + " builds one from a FASTA; choose the FASTA, or the index mode"
                                + " none to search this index as it is"),
                check(withDatabase(index.toString()), fake.spectra(), IndexMode.FRAGMENT_ION)
                        .report()
                        .problems());

        Path orphan =
                IndexHeaders.write(
                        fake.root().resolve("orphan/db.fasta.idx"), IndexHeaders.v5("gone.fasta"));
        PreRunReport noFasta = check(withDatabase(orphan.toString())).report();
        assertEquals(
                List.of(
                        "the index "
                                + orphan
                                + " names no FASTA that can be read (InputDB: gone.fasta), so its"
                                + " decoys cannot be counted and the decoy configuration cannot be"
                                + " checked"),
                noFasta.problems());
        assertTrue(noFasta.facts().index().isPresent());

        Path garbage = Files.writeString(fake.root().resolve("inputs/bad.idx"), "not an index\n");
        String refused;
        try {
            CometIndexHeaderReader.read(garbage);
            throw new AssertionError("the reader accepted a file that is not an index");
        } catch (IOException unreadable) {
            refused = unreadable.getMessage();
        }
        assertEquals(List.of(refused), check(withDatabase(garbage.toString())).report().problems());
    }

    @Test
    @DisplayName("the FASTA a header names: absolute, relative to the index, absent, unreadable")
    void indexedFasta() throws IOException {
        Path index =
                IndexHeaders.write(
                        fake.root().resolve("inputs/x.idx"), IndexHeaders.v5("db.fasta"));
        assertEquals(
                Optional.of(fake.fasta()),
                PreRunChecks.indexedFasta(index, CometIndexHeaderReader.read(index)));
        Path absolute =
                IndexHeaders.write(
                        fake.root().resolve("elsewhere/x.idx"),
                        IndexHeaders.v5(fake.fasta().toString()));
        assertEquals(
                Optional.of(fake.fasta()),
                PreRunChecks.indexedFasta(absolute, CometIndexHeaderReader.read(absolute)));
        Path gone =
                IndexHeaders.write(
                        fake.root().resolve("elsewhere/y.idx"), IndexHeaders.v5("db.fasta"));
        assertEquals(
                Optional.empty(),
                PreRunChecks.indexedFasta(gone, CometIndexHeaderReader.read(gone)));
        Path unreadable =
                Files.writeString(fake.root().resolve("elsewhere/db.fasta"), FakeSearch.FASTA_TEXT);
        Files.setPosixFilePermissions(unreadable, PosixFilePermissions.fromString("---------"));
        try {
            assertEquals(
                    Optional.empty(),
                    PreRunChecks.indexedFasta(gone, CometIndexHeaderReader.read(gone)));
        } finally {
            Files.setPosixFilePermissions(unreadable, PosixFilePermissions.fromString("rw-------"));
        }
    }

    @Test
    @DisplayName("the runs directory must exist and be writable")
    void runsDirectory() throws IOException {
        Files.setPosixFilePermissions(
                project.runsDirectory(), PosixFilePermissions.fromString("r-x------"));
        try {
            assertEquals(
                    List.of(
                            "the project's runs directory "
                                    + project.runsDirectory()
                                    + " does not exist or cannot be written"),
                    check(model()).report().problems());
        } finally {
            Files.setPosixFilePermissions(
                    project.runsDirectory(), PosixFilePermissions.fromString("rwx------"));
        }
        Files.delete(project.runsDirectory());
        assertEquals(
                List.of(
                        "the project's runs directory "
                                + project.runsDirectory()
                                + " does not exist or cannot be written"),
                check(model()).report().problems());
    }

    @Test
    @DisplayName("an index mode looks up the cache: an incomplete entry, then a complete one read")
    void theIndexCache() throws IOException {
        PreRunChecks.Outcome incomplete = check(model(), fake.spectra(), IndexMode.FRAGMENT_ION);
        assertEquals(List.of(), incomplete.report().problems());
        IndexCacheEntry entry = incomplete.cacheEntry().orElseThrow();
        assertEquals(
                checks.cacheEntry(
                                project,
                                model(),
                                fake.selection(),
                                IndexMode.FRAGMENT_ION,
                                fake.fasta())
                        .directory(),
                entry.directory());
        assertFalse(incomplete.cacheComplete());
        assertEquals(Optional.empty(), incomplete.report().facts().index());

        Path index =
                IndexHeaders.write(entry.indexFile(), IndexHeaders.v5(fake.fasta().toString()));
        entry.markComplete(
                new IndexCacheEntry.Completion(RealComet.sha256(index), Files.size(index)));
        PreRunChecks.Outcome complete = check(model(), fake.spectra(), IndexMode.FRAGMENT_ION);
        assertTrue(complete.cacheComplete());
        assertEquals(
                CometIndexHeaderReader.read(index),
                complete.report().facts().index().orElseThrow());

        Files.writeString(index, "not an index\n");
        PreRunReport broken = check(model(), fake.spectra(), IndexMode.FRAGMENT_ION).report();
        assertEquals(1, broken.problems().size());
        assertTrue(
                broken.problems()
                        .get(0)
                        .startsWith("the index cache for " + fake.fasta() + " cannot be used: "),
                broken.problems()::toString);
    }

    @Test
    @DisplayName(
            "an index is judged with the census and the header: a v4 index for 2026.03.0 is an"
                    + " error")
    void judgeIndex() throws IOException {
        Path v5 = IndexHeaders.write(fake.root().resolve("j/a.idx"), IndexHeaders.v5("x"));
        assertFalse(checks.judgeIndex(model(), fake.fasta(), v5).hasErrors());
        Path v4 = IndexHeaders.write(fake.root().resolve("j/b.idx"), IndexHeaders.v4("x"));
        ValidationReport report = checks.judgeIndex(model(), fake.fasta(), v4);
        assertEquals(1, report.errors().size(), report::toString);
        assertEquals("index.format_unreadable", report.errors().get(0).rule().id());
    }

    @Test
    @DisplayName("the extension and index helpers")
    void helpers() {
        assertEquals(".mzML", PreRunChecks.extensionOf(Path.of("a.MZML")));
        assertEquals(".mgf", PreRunChecks.extensionOf(Path.of("a.mgf")));
        assertEquals("", PreRunChecks.extensionOf(Path.of("a.txt")));
        assertTrue(PreRunChecks.isIndex(Path.of("db.fasta.idx")));
        assertFalse(PreRunChecks.isIndex(Path.of("db.fasta")));
        assertFalse(PreRunChecks.isIndex(Path.of("db.IDX")));
    }

    @Test
    @DisplayName("Windows is recognised from os.name")
    void windows() {
        String saved = System.getProperty("os.name");
        try {
            System.setProperty("os.name", "Windows 11");
            assertTrue(PreRunChecks.onWindows());
            System.setProperty("os.name", "Linux");
            assertFalse(PreRunChecks.onWindows());
        } finally {
            System.setProperty("os.name", saved);
        }
    }

    @Test
    @DisplayName("readiness and prepare see the same report")
    void sameReportBothWays() throws IOException {
        CometWorkflow workflow = new CometWorkflow(hashes, RealComet.BUILD, false);
        PreRunReport readiness =
                workflow.check(
                        project, fake.request(DecoySource.FASTA_CONTAINS_DECOYS, IndexMode.NONE));
        assertTrue(readiness.blocked());
        assertEquals(
                "the run cannot start:\n- [decoy.none_anywhere] decoy_search = 0 (no internal"
                        + " decoys) and "
                        + fake.fasta()
                        + " holds no entry whose accession begins with DECOY_ (0 of 2 records):"
                        + " Percolator would have no negative examples; set decoy_search to 1 or 2"
                        + " so that Comet makes decoys, or choose a FASTA whose decoys begin with"
                        + " DECOY_",
                readiness.message());
        assertEquals(
                readiness.message(),
                check(fake.model(DecoySource.FASTA_CONTAINS_DECOYS)).report().message());
        Files.writeString(
                fake.fasta(),
                ">DECOY_x\nK\n",
                StandardCharsets.US_ASCII,
                java.nio.file.StandardOpenOption.APPEND);
        assertFalse(
                workflow.check(
                                project,
                                fake.request(DecoySource.FASTA_CONTAINS_DECOYS, IndexMode.NONE))
                        .blocked());
    }
}
