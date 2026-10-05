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

package org.cometgui.ui.viewmodel.params;

import static org.cometgui.ui.viewmodel.params.Sessions.BUILD;
import static org.cometgui.ui.viewmodel.params.Sessions.C02;
import static org.cometgui.ui.viewmodel.params.Sessions.C03;
import static org.cometgui.ui.viewmodel.params.Sessions.cometQ;
import static org.cometgui.ui.viewmodel.params.Sessions.startingIn;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.migration.MigrationEntry;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.model.ValueOrigin;
import org.cometgui.params.comet.validation.Finding;
import org.cometgui.params.comet.validation.Rule;
import org.cometgui.params.comet.writer.WrittenParams;
import org.cometgui.provenance.hashing.StreamingHashService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Saving ({@code R-PARAM-12}: written once, hashed as written, never overwritten, refused while
 * anything would block a run) and importing (read for the selected release, or the choice offered
 * when the file names another release: migrate, read as its own release, or read with the mismatch
 * warning, {@code R-PARAM-06}). Files are written to and read from a JUnit temporary directory; the
 * parameter texts are each release's own {@code comet -q} output from the model module's jar.
 * Digests are checked against {@link MessageDigest} over the bytes read back.
 */
class ParameterFilesViewModelTest {

    private static ParameterFilesViewModel files(ParameterSession session) {
        return new ParameterFilesViewModel(session, BUILD, new StreamingHashService());
    }

    private static String hex(String algorithm, byte[] bytes) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance(algorithm).digest(bytes));
    }

    @Nested
    @DisplayName("saving")
    class Saving {

        @ParameterizedTest(name = "Comet {0}")
        @CsvSource({"2026.03.0, 10725", "2026.02.2, 10656"})
        @DisplayName("writes the canonical file once and hashes what is on disk")
        void saves(String release, long size, @TempDir Path directory)
                throws IOException, NoSuchAlgorithmException {
            ParameterSession session = startingIn(ToolVersion.parse(release));
            ParameterFilesViewModel files = files(session);
            Path target = directory.resolve("comet.params");

            SaveOutcome outcome = files.save(target);

            assertTrue(outcome.accepted());
            assertEquals(List.of(), outcome.refusals());
            WrittenParams written = outcome.written().orElseThrow();
            byte[] bytes = Files.readAllBytes(target);
            assertEquals(target, written.path());
            assertEquals(size, written.size());
            assertEquals(size, bytes.length);
            assertEquals(hex("SHA-256", bytes), written.hashes().sha256());
            assertEquals(hex("MD5", bytes), written.hashes().md5());
            String text = new String(bytes, StandardCharsets.UTF_8);
            assertTrue(text.startsWith("# comet_version "), text.substring(0, 40));
            assertTrue(text.contains("\noutput_percolatorfile = 1 "));
            assertEquals(Optional.of(written), files.lastWritten());
            assertSame(session.model(), files.lastSaved().orElseThrow());
            assertEquals(
                    new ExpertViewModel(session, BUILD, files).canonicalText(),
                    text,
                    "the file saved is the text Expert mode shows");
        }

        @Test
        @DisplayName("never overwrites a file, and says so")
        void neverOverwrites(@TempDir Path directory) throws IOException {
            ParameterSession session = startingIn(C03);
            ParameterFilesViewModel files = files(session);
            Path target = directory.resolve("comet.params");
            Files.writeString(target, "someone else's file\n");
            SaveOutcome outcome = files.save(target);
            assertFalse(outcome.accepted());
            assertEquals(
                    List.of(
                            target
                                    + " already exists. CometGUI never overwrites a parameter file;"
                                    + " choose another name."),
                    outcome.refusals());
            assertEquals("someone else's file\n", Files.readString(target));
            assertEquals(Optional.empty(), files.lastSaved());
            assertEquals(Optional.empty(), files.lastWrittenProperty().get());
        }

        @Test
        @DisplayName("refuses while the configuration would block a run, with every reason")
        void refusesWhileBlocked(@TempDir Path directory) {
            ParameterSession session = startingIn(C03);
            ParameterFilesViewModel files = files(session);
            session.edit("num_threads", "many");
            session.edit("peptide_mass_tolerance_lower", "30.0");
            Path target = directory.resolve("comet.params");

            SaveOutcome outcome = files.save(target);

            assertFalse(outcome.accepted());
            assertEquals(Optional.empty(), outcome.written());
            List<String> refusals = outcome.refusals();
            assertEquals(
                    "The configuration was not saved: a parameter file is saved only when nothing"
                            + " would block a run. Resolve these first:",
                    refusals.get(0));
            assertEquals(3, refusals.size(), refusals::toString);
            assertTrue(
                    refusals.get(1).startsWith("Not applied -- Search threads (num_threads)"),
                    refusals.get(1));
            assertTrue(
                    refusals.get(2)
                            .startsWith(
                                    "Error -- Precursor tolerance, lower bound"
                                            + " (peptide_mass_tolerance_lower), Precursor mass and"
                                            + " isotope handling: peptide_mass_tolerance_lower ="
                                            + " 30.0 and peptide_mass_tolerance_upper = 20.0: the"
                                            + " lower bound is above the upper bound"),
                    refusals.get(2));
            assertFalse(Files.exists(target));
            assertEquals(Optional.empty(), files.lastSaved());
        }

        @Test
        @DisplayName("an unresolved migration entry blocks saving like any error")
        void migrationBlocks(@TempDir Path directory) {
            ParameterSession session = startingIn(C02);
            session.edit("variable_mod01", "15.9949 M 0 3 2 4 0 0.0");
            session.selectRelease(C03);
            ParameterFilesViewModel files = files(session);
            Path target = directory.resolve("comet.params");
            SaveOutcome blocked = files.save(target);
            assertEquals(2, blocked.refusals().size());
            assertTrue(
                    blocked.refusals()
                            .get(1)
                            .startsWith(
                                    "Error -- Variable modification 1 (variable_mod01), Variable"
                                            + " modifications: variable_mod01 needs your"
                                            + " decision:"),
                    blocked.refusals().get(1));
            assertFalse(Files.exists(target));
            session.resolve("variable_mod01");
            assertTrue(files.save(target).accepted());
        }

        @Test
        @DisplayName("a file that cannot be written is reported")
        void unwritable(@TempDir Path directory) {
            ParameterFilesViewModel files = files(startingIn(C03));
            Path target = directory.resolve("missing-directory").resolve("comet.params");
            SaveOutcome outcome = files.save(target);
            assertEquals(1, outcome.refusals().size());
            assertTrue(
                    outcome.refusals().get(0).startsWith("Could not write " + target + ": "),
                    outcome.refusals().get(0));
        }

        @Test
        @DisplayName("a warning does not block saving")
        void warningSaves(@TempDir Path directory) {
            ParameterSession session = startingIn(C03);
            ParameterFilesViewModel files = files(session);
            session.edit("peptide_mass_tolerance_lower", "-10.0");
            assertFalse(session.report().warnings().isEmpty());
            assertFalse(session.report().hasErrors());
            assertTrue(files.save(directory.resolve("asymmetric.params")).accepted());
            assertSame(session.model(), files.lastSavedProperty().get().orElseThrow());
        }

        @Test
        @DisplayName("an outcome is either a file or its reasons")
        void outcomeRefusals(@TempDir Path directory) {
            WrittenParams file =
                    files(startingIn(C03))
                            .save(directory.resolve("x.params"))
                            .written()
                            .orElseThrow();
            assertEquals(
                    "a saved file has no refusal",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () -> new SaveOutcome(Optional.of(file), List.of("why")))
                            .getMessage());
            assertEquals(
                    "a refused save has to say why",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () -> new SaveOutcome(Optional.empty(), List.of()))
                            .getMessage());
        }
    }

    @Nested
    @DisplayName("importing")
    class Importing {

        @Test
        @DisplayName("a file of the selected release is read and adopted, origin IMPORTED")
        void sameRelease(@TempDir Path directory) throws IOException {
            ParameterSession session = startingIn(C02);
            ParameterFilesViewModel files = files(session);
            // CONSTRUCTED edit of the real 2026.02.2 -q file
            Path file = directory.resolve("mine.params");
            Files.writeString(
                    file,
                    cometQ(C02).replace("\nnum_threads = 0 ", "\nnum_threads = 6 "),
                    StandardCharsets.UTF_8);

            ImportOutcome outcome = files.importFile(file);

            assertEquals(new ImportOutcome(ImportOutcome.Kind.IMPORTED, List.of()), outcome);
            assertEquals(C02, session.release());
            assertEquals("6", session.model().text("num_threads"));
            assertEquals(ValueOrigin.IMPORTED, session.model().origin("num_threads"));
            assertEquals(
                    ValueOrigin.WORKFLOW_ENFORCED, session.model().origin("output_percolatorfile"));
            assertEquals(Optional.empty(), files.offer());
        }

        @Test
        @DisplayName("a file of another offered release: the choice is offered and nothing changes")
        void offered() {
            ParameterSession session = startingIn(C03);
            ParameterFilesViewModel files = files(session);
            CometParameters before = session.model();

            ImportOutcome outcome = files.importText(cometQ(C02), "old.params");

            assertEquals(
                    new ImportOutcome(
                            ImportOutcome.Kind.OFFERED,
                            List.of(
                                    "old.params was written for Comet 2026.02.2; the editor is set"
                                            + " to Comet 2026.03.0. Migrate it to Comet 2026.03.0"
                                            + " with a reviewable report, switch the editor to"
                                            + " Comet 2026.02.2, or read it as Comet 2026.03.0 as"
                                            + " it is.")),
                    outcome);
            assertSame(before, session.model());
            ImportOffer offer = files.offerProperty().get().orElseThrow();
            assertEquals(C02, offer.declared());
            assertEquals(C03, offer.selected());
            assertTrue(offer.declaredOffered());
            // another import replaces the waiting choice
            assertEquals(
                    ImportOutcome.Kind.IMPORTED,
                    files.importText(cometQ(C03), "new.params").kind());
            assertEquals(Optional.empty(), files.offer());
            before = session.model();
            files.importText(cometQ(C02), "old.params");
            assertTrue(files.offer().isPresent());
            files.dismissOffer();
            assertEquals(Optional.empty(), files.offer());
            assertEquals(
                    new ImportOutcome(
                            ImportOutcome.Kind.REFUSED,
                            List.of("No import is waiting for a choice.")),
                    files.migrateOffered());
            assertEquals(ImportOutcome.Kind.REFUSED, files.readOfferedAsItsRelease().kind());
            assertEquals(ImportOutcome.Kind.REFUSED, files.readOfferedAsSelected().kind());
            assertSame(before, session.model());
        }

        @Test
        @DisplayName("migrating the offered file puts its report under review")
        void migrate() {
            ParameterSession session = startingIn(C03);
            ParameterFilesViewModel files = files(session);
            files.importText(cometQ(C02), "old.params");

            ImportOutcome outcome = files.migrateOffered();

            assertEquals(
                    new ImportOutcome(
                            ImportOutcome.Kind.MIGRATED,
                            List.of(
                                    "Comet 2026.02.2 -> 2026.03.0: 118 parameters, 1 changes, 0"
                                            + " needing attention")),
                    outcome);
            assertEquals(C03, session.release());
            assertEquals("-1", session.model().text("index_search_type"));
            assertEquals(
                    List.of("index_search_type"),
                    session.review()
                            .orElseThrow()
                            .report()
                            .names(MigrationEntry.Outcome.CONVERTED));
            assertEquals(Optional.empty(), files.offer());
        }

        @Test
        @DisplayName("reading the offered file as its own release switches the editor to it")
        void readAsItsRelease() {
            ParameterSession session = startingIn(C03);
            ParameterFilesViewModel files = files(session);
            files.importText(cometQ(C02), "old.params");
            assertEquals(
                    new ImportOutcome(ImportOutcome.Kind.IMPORTED, List.of()),
                    files.readOfferedAsItsRelease());
            assertEquals(C02, session.release());
            assertEquals("1", session.model().text("index_search_type"));
            assertEquals(Optional.empty(), session.review());
            assertEquals(Optional.empty(), files.offer());
        }

        @Test
        @DisplayName("reading it as the selected release keeps the mismatch warning in view")
        void readAsSelected() {
            ParameterSession session = startingIn(C02);
            ParameterFilesViewModel files = files(session);
            files.importText(cometQ(C03), "new.params");
            String warning =
                    "line 1 says the file was written for Comet 2026.03.0 (\"2026.03 rev. 0"
                            + " (fa08489)\"), and the selected Comet is 2026.02.2 (\"2026.02 rev. 2"
                            + " (6edec91)\"); the parameters are read and will be written as that"
                            + " version's";

            ImportOutcome outcome = files.readOfferedAsSelected();

            assertEquals(
                    new ImportOutcome(
                            ImportOutcome.Kind.IMPORTED, List.of("Warning, line 1: " + warning)),
                    outcome);
            assertEquals(C02, session.release());
            assertEquals("-1", session.model().text("index_search_type"));
            List<Finding> warnings = session.report().warnings();
            assertEquals(
                    List.of(Rule.IMPORT_DIAGNOSTIC), warnings.stream().map(Finding::rule).toList());
            assertEquals(warning, warnings.get(0).message());
        }

        @Test
        @DisplayName("a release the metadata describes but the editor does not offer: migrate only")
        void notOffered() {
            ParameterSession session = startingIn(C03);
            ParameterFilesViewModel files = files(session);
            // CONSTRUCTED: 2026.02.2's -q text claiming to be 2024.01.0's
            String text =
                    cometQ(C02)
                            .replace(
                                    "# comet_version 2026.02 rev. 2 (6edec91)",
                                    "# comet_version 2024.01 rev. 0");
            ImportOutcome outcome = files.importText(text, "older.params");
            assertEquals(
                    List.of(
                            "older.params was written for Comet 2024.01.0; the editor is set to"
                                    + " Comet 2026.03.0. Migrate it to Comet 2026.03.0 with a"
                                    + " reviewable report, or read it as Comet 2026.03.0 as it"
                                    + " is."),
                    outcome.messages());
            assertFalse(files.offer().orElseThrow().declaredOffered());
            assertEquals(
                    new ImportOutcome(
                            ImportOutcome.Kind.REFUSED,
                            List.of(
                                    "Comet 2024.01.0 is not a release the editor offers; migrate"
                                            + " the file or read it as Comet 2026.03.0.")),
                    files.readOfferedAsItsRelease());
            assertEquals(C03, session.release());
            assertEquals(ImportOutcome.Kind.MIGRATED, files.migrateOffered().kind());
            assertEquals(C03, session.release());
            assertTrue(session.review().isPresent());
        }

        @Test
        @DisplayName("an offered file that does not parse as its own release cannot be migrated")
        void migrationRefused() {
            ParameterSession session = startingIn(C03);
            ParameterFilesViewModel files = files(session);
            CometParameters before = session.model();
            // CONSTRUCTED: a 2026.02.2 file with a line no release reads
            files.importText(cometQ(C02) + "not a line\n", "broken.params");
            ImportOutcome outcome = files.migrateOffered();
            assertEquals(ImportOutcome.Kind.REFUSED, outcome.kind());
            assertTrue(
                    outcome.messages()
                            .get(0)
                            .startsWith("the file does not parse as Comet 2026.02.2, so nothing"),
                    outcome.messages().get(0));
            assertSame(before, session.model());
            assertTrue(files.offer().isPresent(), "the choice is still open");
            ImportOutcome read = files.readOfferedAsSelected();
            assertEquals(ImportOutcome.Kind.REFUSED, read.kind());
            assertTrue(files.offer().isPresent());
        }

        @Test
        @DisplayName("a file that does not parse changes nothing and names every error's line")
        void parseErrors() {
            ParameterSession session = startingIn(C03);
            ParameterFilesViewModel files = files(session);
            CometParameters before = session.model();
            // CONSTRUCTED edit of the real 2026.03.0 -q file
            String text = cometQ(C03).replace("\nnum_threads = 0 ", "\nnum_threads = many ");
            ImportOutcome outcome = files.importText(text, "bad.params");
            assertEquals(
                    new ImportOutcome(
                            ImportOutcome.Kind.REFUSED,
                            List.of(
                                    "bad.params was not imported: it does not read as a Comet"
                                            + " 2026.03.0 parameter file.",
                                    "Error, line 16: line 16: num_threads, value: \"many\" is not a"
                                            + " whole number")),
                    outcome);
            assertSame(before, session.model());
        }

        @Test
        @DisplayName("no marker, or one no release describes: read with the parser's warning")
        void noOffer() {
            ParameterSession session = startingIn(C03);
            ParameterFilesViewModel files = files(session);
            String bare = cometQ(C03).replace("# comet_version 2026.03 rev. 0 (fa08489)\n", "");
            ImportOutcome outcome = files.importText(bare, "bare.params");
            assertEquals(ImportOutcome.Kind.IMPORTED, outcome.kind());
            assertEquals(
                    List.of(
                            "Warning: the file has no \"# comet_version\" line, so the Comet"
                                    + " version it was written for is unknown; the selected"
                                    + " Comet is 2026.03.0 (\"2026.03 rev. 0 (fa08489)\"), and"
                                    + " its parameters are read and will be written as that"
                                    + " version's"),
                    outcome.messages());
            String future =
                    cometQ(C03)
                            .replace(
                                    "# comet_version 2026.03 rev. 0 (fa08489)",
                                    "# comet_version 2099.01 rev. 0");
            ImportOutcome later = files.importText(future, "future.params");
            assertEquals(ImportOutcome.Kind.IMPORTED, later.kind());
            assertTrue(later.messages().get(0).startsWith("Warning, line 1: line 1 says the file"));
            assertEquals(Optional.empty(), files.offer());
        }

        @Test
        @DisplayName("a file that cannot be read as text is reported")
        void unreadable(@TempDir Path directory) throws IOException {
            ParameterSession session = startingIn(C03);
            ParameterFilesViewModel files = files(session);
            Path file = directory.resolve("binary.params");
            Files.write(file, new byte[] {(byte) 0xC3, (byte) 0x28});
            ImportOutcome outcome = files.importFile(file);
            assertEquals(ImportOutcome.Kind.REFUSED, outcome.kind());
            assertTrue(
                    outcome.messages()
                            .get(0)
                            .startsWith("Could not read " + file + " as a UTF-8 text file: "),
                    outcome.messages().get(0));
            assertArrayEquals(new byte[] {(byte) 0xC3, (byte) 0x28}, Files.readAllBytes(file));
        }

        @Test
        @DisplayName("an offer or a refusal has to say why")
        void outcomeRefusals() {
            assertEquals(
                    "an import REFUSED has to say why",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () -> new ImportOutcome(ImportOutcome.Kind.REFUSED, List.of()))
                            .getMessage());
            assertEquals(
                    "an import OFFERED has to say why",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () -> new ImportOutcome(ImportOutcome.Kind.OFFERED, List.of()))
                            .getMessage());
            assertEquals(
                    List.of(),
                    new ImportOutcome(ImportOutcome.Kind.MIGRATED, List.of()).messages());
        }
    }
}
