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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.model.ValueOrigin;
import org.cometgui.params.comet.parser.ReleaseDefaults;
import org.cometgui.params.comet.writer.WrittenParams;
import org.cometgui.ui.testing.Editors;
import org.cometgui.ui.testing.Editors.KnownFiles;
import org.cometgui.ui.testing.Editors.ScriptedChooser;
import org.cometgui.ui.testing.Nulls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The editor bundle's own state: the level shown and where a summary entry's field is, the release
 * status, file choice for a file-path parameter, and saving through the chooser. Every expected
 * text is typed out.
 */
class ParameterEditorViewModelTest {

    private ParameterSession session;

    private SpectrumInputsViewModel inputs;

    private ParameterEditorViewModel editor;

    /** Builds the editor's parts over a new 2026.03.0 configuration, in this test's fields. */
    private void build(ScriptedChooser chooser, KnownFiles files) {
        session = Editors.session();
        inputs = Editors.inputs(session, chooser, files);
        editor = Editors.editor(session, inputs, chooser);
    }

    private static final ToolVersion C02 = ToolVersion.parse("2026.02.2");

    private static final ToolVersion C03 = ToolVersion.parse("2026.03.0");

    @Nested
    @DisplayName("the level shown")
    class Levels {

        @Test
        @DisplayName("a new editor shows Essentials, and the level follows setMode")
        void startsOnEssentials() {
            build(new ScriptedChooser(), new KnownFiles());
            assertEquals(EditorMode.ESSENTIALS, editor.mode());
            editor.setMode(EditorMode.EXPERT);
            assertEquals(EditorMode.EXPERT, editor.modeProperty().get());
            assertThrows(
                    NullPointerException.class, () -> editor.setMode(Nulls.of(EditorMode.class)));
        }

        @Test
        @DisplayName("each level is named in words")
        void words() {
            assertEquals("Essentials", EditorMode.ESSENTIALS.words());
            assertEquals("Advanced", EditorMode.ADVANCED.words());
            assertEquals("Expert", EditorMode.EXPERT.words());
        }

        @Test
        @DisplayName("a summary entry stays on Essentials for an Essentials parameter")
        void essentialsKeepsItsOwn() {
            build(new ScriptedChooser(), new KnownFiles());
            assertEquals(
                    EditorMode.ESSENTIALS, editor.showParameter("peptide_mass_tolerance_lower"));
            assertEquals(EditorMode.ESSENTIALS, editor.mode());
        }

        @Test
        @DisplayName("a parameter Essentials does not show is shown on Advanced")
        void advancedForTheRest() {
            build(new ScriptedChooser(), new KnownFiles());
            assertEquals(EditorMode.ADVANCED, editor.showParameter("activation_method"));
            assertEquals(EditorMode.ADVANCED, editor.mode());
        }

        @Test
        @DisplayName("from Advanced or Expert, even an Essentials parameter is shown on Advanced")
        void advancedFromElsewhere() {
            build(new ScriptedChooser(), new KnownFiles());
            editor.setMode(EditorMode.EXPERT);
            assertEquals(EditorMode.ADVANCED, editor.showParameter("decoy_prefix"));
            editor.setMode(EditorMode.ADVANCED);
            assertEquals(EditorMode.ADVANCED, editor.showParameter("decoy_prefix"));
        }

        @Test
        @DisplayName("a parameter the release does not model is refused")
        void unknownParameter() {
            build(new ScriptedChooser(), new KnownFiles());
            IllegalArgumentException refused =
                    assertThrows(
                            IllegalArgumentException.class, () -> editor.showParameter("nothing"));
            assertEquals("Comet 2026.03.0 has no parameter named nothing", refused.getMessage());
            assertEquals(EditorMode.ESSENTIALS, editor.mode(), "the level is not changed");
        }

        @Test
        @DisplayName("onEssentials: the curated surface, not every parameter")
        void onEssentials() {
            build(new ScriptedChooser(), new KnownFiles());
            assertTrue(editor.onEssentials("database_name"));
            assertTrue(editor.onEssentials("variable_mod15"));
            assertFalse(editor.onEssentials("activation_method"));
            assertFalse(editor.onEssentials("add_B_user_amino_acid"));
        }
    }

    @Nested
    @DisplayName("the release status")
    class ReleaseStatus {

        @Test
        @DisplayName("names the default release, then the migration after a switch")
        void namesTheMigration() {
            build(new ScriptedChooser(), new KnownFiles());
            assertEquals("Comet 2026.03.0 is selected.", editor.releaseStatus());
            // JavaFX hands a listener's exception to the thread's handler and carries on; the
            // status listens to the review, which the session publishes before the configuration.
            List<Throwable> thrownInListeners = new ArrayList<>();
            Thread.UncaughtExceptionHandler previous =
                    Thread.currentThread().getUncaughtExceptionHandler();
            Thread.currentThread()
                    .setUncaughtExceptionHandler((thread, thrown) -> thrownInListeners.add(thrown));
            try {
                assertTrue(editor.selectRelease(C02).accepted());
            } finally {
                Thread.currentThread().setUncaughtExceptionHandler(previous);
            }
            assertEquals(List.of(), thrownInListeners);
            assertEquals(
                    "Comet 2026.02.2 is selected. Migrated: Comet 2026.03.0 -> 2026.02.2: 118"
                            + " parameters, 1 changed, 117 unchanged; 0 need your decision.",
                    editor.releaseStatusProperty().get());
        }

        @Test
        @DisplayName("a refused switch says why, and the release stays")
        void refusedSwitch() {
            build(new ScriptedChooser(), new KnownFiles());
            assertTrue(editor.selectRelease(C02).accepted());
            // CONSTRUCTED: a terminus outside 0-3 at a distance, which 2026.03.0 refuses
            session.edit("variable_mod01", "15.9949 M 0 3 2 4 0 0.0");
            assertTrue(editor.selectRelease(C03).accepted());
            EditOutcome refused = editor.selectRelease(C02);
            assertFalse(refused.accepted());
            assertEquals(
                    "Not changed: Resolve the migration from Comet 2026.02.2 to Comet 2026.03.0"
                            + " before selecting another release; 1 still need your decision:"
                            + " [variable_mod01]",
                    editor.releaseStatus());
            assertEquals(C03, session.release());

            // An edit changes the configuration and nothing under review: the status follows it.
            session.edit("variable_mod01", "16.0 M 0 3 -1 0 0 0.0");
            assertEquals(
                    "Comet 2026.03.0 is selected. Migrated: Comet 2026.02.2 -> 2026.03.0: 118"
                            + " parameters, 2 changed, 116 unchanged; 0 need your decision.",
                    editor.releaseStatus());
        }

        @Test
        @DisplayName("follows a change of the configuration")
        void followsTheModel() {
            build(new ScriptedChooser(), new KnownFiles());
            assertTrue(editor.selectRelease(C02).accepted());
            session.resetAll();
            assertEquals("Comet 2026.02.2 is selected.", editor.releaseStatus());
        }
    }

    @Nested
    @DisplayName("file choice and saving")
    class FileChoiceAndSaving {

        @Test
        @DisplayName("the database goes through the inputs and is set as database_name")
        void database() {
            Path fasta = Path.of("data", "human.fasta").toAbsolutePath();
            build(new ScriptedChooser().file(fasta), new KnownFiles().with(fasta));
            Optional<EditOutcome> outcome = editor.chooseFileFor(session.field("database_name"));
            assertEquals(Optional.of(EditOutcome.applied()), outcome);
            assertEquals(fasta.toString(), session.model().text("database_name"));
            assertEquals(ValueOrigin.USER, session.model().origin("database_name"));
            assertEquals("Found and readable: " + fasta, inputs.database().text());
        }

        @Test
        @DisplayName("any other file parameter is set through its own field")
        void otherFile() {
            Path obo = Path.of("data", "unimod.obo").toAbsolutePath();
            build(new ScriptedChooser().file(obo), new KnownFiles());
            assertEquals(
                    Optional.of(EditOutcome.applied()),
                    editor.chooseFileFor(session.field("peff_obo")));
            assertEquals(obo.toString(), session.model().text("peff_obo"));
        }

        @Test
        @DisplayName("a cancelled chooser changes nothing")
        void cancelled() {
            build(new ScriptedChooser(), new KnownFiles());
            Object before = session.model();
            assertEquals(Optional.empty(), editor.chooseFileFor(session.field("peff_obo")));
            assertEquals(Optional.empty(), editor.chooseFileFor(session.field("database_name")));
            assertSame(before, session.model());
            assertThrows(
                    NullPointerException.class,
                    () -> editor.chooseFileFor(Nulls.of(FieldViewModel.class)));
        }

        @Test
        @DisplayName("saving where the chooser says writes the file and says so")
        void saves(@TempDir Path directory) throws IOException {
            Path target = directory.resolve("search.params");
            build(new ScriptedChooser().saveTo(target), new KnownFiles());
            assertEquals("Not saved yet.", editor.saveStatus());
            Optional<SaveOutcome> outcome = editor.save();
            assertTrue(outcome.orElseThrow().accepted());
            String written = Files.readString(target, StandardCharsets.UTF_8);
            assertTrue(
                    written.startsWith(
                            "# comet_version 2026.03 rev. 0 (fa08489)\n"
                                    + "# Written by CometGUI 0.0.0-test for Comet 2026.03.0."),
                    written.substring(0, 120));
            WrittenParams file = outcome.orElseThrow().written().orElseThrow();
            assertEquals(
                    "Saved "
                            + target
                            + " ("
                            + Files.size(target)
                            + " bytes; SHA-256 "
                            + file.hashes().sha256()
                            + ", MD5 "
                            + file.hashes().md5()
                            + ").",
                    editor.saveStatusProperty().get());
        }

        @Test
        @DisplayName("a cancelled save says nothing was saved")
        void cancelledSave() {
            build(new ScriptedChooser(), new KnownFiles());
            assertEquals(Optional.empty(), editor.save());
            assertEquals("Not saved: no file was chosen.", editor.saveStatus());
        }

        @Test
        @DisplayName("a refused save lists every reason, one per line")
        void refusedSave(@TempDir Path directory) {
            Path target = directory.resolve("blocked.params");
            build(new ScriptedChooser().saveTo(target), new KnownFiles());
            session.edit("allowed_missed_cleavage", "many");
            Optional<SaveOutcome> outcome = editor.save();
            assertFalse(outcome.orElseThrow().accepted());
            assertFalse(Files.exists(target));
            List<String> lines = List.of(editor.saveStatus().split("\n"));
            assertEquals(
                    "The configuration was not saved: a parameter file is saved only when nothing"
                            + " would block a run. Resolve these first:",
                    lines.get(0));
            assertTrue(
                    lines.get(1).startsWith("Not applied -- Allowed missed cleavages"),
                    lines.get(1));
            assertEquals(2, lines.size());
        }

        @Test
        @DisplayName("describe: a written file in words")
        void describeWritten() {
            WrittenParams file =
                    new WrittenParams(
                            Path.of("runs", "a.params"),
                            new FileHashes(
                                    "0123456789abcdef0123456789abcdef",
                                    "0123456789abcdef0123456789abcdef"
                                            + "0123456789abcdef0123456789abcdef"),
                            42);
            assertEquals(
                    "Saved runs/a.params (42 bytes; SHA-256"
                            + " 0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef,"
                            + " MD5 0123456789abcdef0123456789abcdef).",
                    ParameterEditorViewModel.describe(SaveOutcome.saved(file)));
        }
    }

    @Nested
    @DisplayName("importing through the chooser")
    class Importing {

        private Path write(Path directory, String name, ToolVersion release) throws IOException {
            Path file = directory.resolve(name);
            Files.write(file, ReleaseDefaults.bundledFile(release));
            return file;
        }

        @Test
        @DisplayName("before any import, and a cancelled chooser: nothing imported, said so")
        void cancelled() {
            build(new ScriptedChooser(), new KnownFiles());
            assertEquals("No parameter file imported yet.", editor.importStatus());
            CometParameters before = session.model();
            assertEquals(Optional.empty(), editor.importFile());
            assertEquals(
                    "Nothing imported: no file was chosen.", editor.importStatusProperty().get());
            assertSame(before, session.model());
        }

        @Test
        @DisplayName("a file of the selected release is read and adopted")
        void sameRelease(@TempDir Path directory) throws IOException {
            ScriptedChooser chooser = new ScriptedChooser();
            build(chooser, new KnownFiles());
            chooser.parameterFile(write(directory, "new.params", C03));
            ImportOutcome outcome = editor.importFile().orElseThrow();
            assertEquals(ImportOutcome.Kind.IMPORTED, outcome.kind());
            assertEquals("Imported as a Comet 2026.03.0 parameter file.", editor.importStatus());
            assertEquals(ValueOrigin.IMPORTED, session.model().origin("num_threads"));
        }

        @Test
        @DisplayName("a file of another release waits; migrating it puts it under review")
        void offeredThenMigrated(@TempDir Path directory) throws IOException {
            ScriptedChooser chooser = new ScriptedChooser();
            build(chooser, new KnownFiles());
            Path old = write(directory, "old.params", C02);
            chooser.parameterFile(old);
            CometParameters before = session.model();
            assertEquals(ImportOutcome.Kind.OFFERED, editor.importFile().orElseThrow().kind());
            assertSame(before, session.model());
            assertEquals(
                    "Not imported yet: choose how to read it.\n"
                            + old
                            + " was written for Comet 2026.02.2; the editor is set to Comet"
                            + " 2026.03.0. Migrate it to Comet 2026.03.0 with a reviewable report,"
                            + " switch the editor to Comet 2026.02.2, or read it as Comet"
                            + " 2026.03.0 as it is.",
                    editor.importStatus());
            assertEquals(ImportOutcome.Kind.MIGRATED, editor.migrateOffered().kind());
            assertEquals(
                    "Migrated to Comet 2026.03.0; review the changes below before running:\n"
                            + "Comet 2026.02.2 -> 2026.03.0: 118 parameters, 1 changes, 0 needing"
                            + " attention",
                    editor.importStatus());
            assertTrue(editor.migrationReview().underReview());
        }

        @Test
        @DisplayName("read as its own release, or as the selected one with the mismatch warning")
        void readAs(@TempDir Path directory) throws IOException {
            ScriptedChooser chooser = new ScriptedChooser();
            build(chooser, new KnownFiles());
            Path old = write(directory, "old.params", C02);
            chooser.parameterFile(old).parameterFile(old);
            editor.importFile();
            assertEquals(ImportOutcome.Kind.IMPORTED, editor.readOfferedAsItsRelease().kind());
            assertEquals(C02, session.release());
            assertEquals("Imported as a Comet 2026.02.2 parameter file.", editor.importStatus());

            session.newConfiguration(C03);
            editor.importFile();
            assertEquals(ImportOutcome.Kind.IMPORTED, editor.readOfferedAsSelected().kind());
            assertEquals(C03, session.release());
            String status = editor.importStatus();
            assertTrue(
                    status.startsWith(
                            "Imported as a Comet 2026.03.0 parameter file, with warnings:\n"
                                    + "Warning, line 1: line 1 says the file was written for Comet"
                                    + " 2026.02.2 (\"2026.02 rev. 2 (6edec91)\"), and the"
                                    + " selected Comet is 2026.03.0"),
                    status);
        }

        @Test
        @DisplayName("putting the waiting file aside imports nothing; a broken file is refused")
        void asideAndRefused(@TempDir Path directory) throws IOException {
            ScriptedChooser chooser = new ScriptedChooser();
            build(chooser, new KnownFiles());
            chooser.parameterFile(write(directory, "old.params", C02));
            editor.importFile();
            editor.dismissOffer();
            assertEquals("Nothing imported: the file was put aside.", editor.importStatus());
            assertEquals(Optional.empty(), editor.files().offer());

            Path broken = directory.resolve("broken.params");
            Files.writeString(broken, "num_threads 4\n", StandardCharsets.UTF_8);
            chooser.parameterFile(broken);
            CometParameters before = session.model();
            assertEquals(ImportOutcome.Kind.REFUSED, editor.importFile().orElseThrow().kind());
            assertSame(before, session.model());
            assertEquals(
                    "Not imported:\n"
                            + broken
                            + " was not imported: it does not read as a Comet 2026.03.0 parameter"
                            + " file.\nError: the file has no [COMET_ENZYME_INFO] table, so no"
                            + " enzyme number it names is defined\nError, line 1: line 1 is not a"
                            + " comment, a declaration or an enzyme row: not a comment, a blank"
                            + " line or a declaration: there is no '=' before any '#':"
                            + " \"num_threads 4\"",
                    editor.importStatus());
        }
    }

    @Nested
    @DisplayName("the view-models it holds")
    class Holds {

        @Test
        @DisplayName("every view-model it makes or holds is over the one session")
        void oneSession() {
            build(new ScriptedChooser(), new KnownFiles());
            session.edit("peptide_mass_tolerance_lower", "-10");
            session.edit("search_enzyme_number", "2");
            session.edit("peptide_length_range", "6 40");
            assertAll(
                    () -> assertEquals("-10", editor.tolerance().lower().text()),
                    () ->
                            assertEquals(
                                    "2. Trypsin/P",
                                    editor.enzymes()
                                            .selected("search_enzyme_number")
                                            .orElseThrow()
                                            .label()),
                    () ->
                            assertEquals(
                                    "6", editor.ranges().range("peptide_length_range").firstText()),
                    () ->
                            assertNotSame(
                                    editor.enzymes(),
                                    editor.enzymes(),
                                    "made on each call: nothing but the session is held"),
                    () -> assertFalse(editor.migrationReview().underReview()),
                    () -> assertEquals(Optional.empty(), editor.files().lastWritten()),
                    () ->
                            assertEquals(
                                    Optional.of(RunReadinessViewModel.ENGINE_NOT_BUILT),
                                    editor.readiness().engineReason()));
            session.edit("allowed_missed_cleavage", "many");
            assertTrue(editor.readiness().parametersBlockRun());
            assertEquals(1, editor.summary().notAppliedCount());
        }

        @Test
        @DisplayName("the presets are held, the built-in ones offered, over the one session")
        void presets() {
            build(new ScriptedChooser(), new KnownFiles());
            assertSame(editor.presets(), editor.presets());
            assertEquals(
                    List.of("low-low", "high-low", "high-high"),
                    editor.presets().presets().stream().map(p -> p.id()).toList());
            session.edit("fragment_bin_tol", "0.5");
            PresetPreview preview = editor.presets().preview(editor.presets().presets().get(0));
            assertEquals("0.5", preview.rows().get(5).view().current());
            assertEquals("1.0005", preview.rows().get(5).view().other());
        }

        @Test
        @DisplayName("a search result is shown on Advanced, whichever level is shown")
        void showInAdvanced() {
            build(new ScriptedChooser(), new KnownFiles());
            assertEquals(EditorMode.ADVANCED, editor.showInAdvanced("num_enzyme_termini"));
            assertEquals(EditorMode.ADVANCED, editor.mode());
            editor.setMode(EditorMode.EXPERT);
            editor.showInAdvanced("database_name");
            assertEquals(EditorMode.ADVANCED, editor.mode());
            editor.setMode(EditorMode.ESSENTIALS);
            assertThrows(IllegalArgumentException.class, () -> editor.showInAdvanced("nonsense"));
            assertEquals(EditorMode.ESSENTIALS, editor.mode(), "an unknown name changes nothing");
        }
    }
}
