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

package org.cometgui.app.gui;

import static org.cometgui.app.gui.ParameterEditorApp.enter;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.KeyCode;
import org.cometgui.app.config.ApplicationServices;
import org.cometgui.app.config.ProjectSession;
import org.cometgui.app.config.RunWiring;
import org.cometgui.app.config.SessionEngine;
import org.cometgui.app.testing.InstalledComet;
import org.cometgui.app.testing.OwnDigests;
import org.cometgui.app.testing.RealPercolators;
import org.cometgui.app.testing.RealSearch;
import org.cometgui.app.testing.RealSearch.LaunchRecorder;
import org.cometgui.app.testing.ScriptedChooser;
import org.cometgui.app.testing.TestJson;
import org.cometgui.app.uidriver.FxUiDriver;
import org.cometgui.app.uidriver.TestFxUiDriver;
import org.cometgui.domain.build.BuildIdentity;
import org.cometgui.domain.log.BoundedMessageLog;
import org.cometgui.domain.ports.ProcessListener;
import org.cometgui.domain.ports.ProcessRunner;
import org.cometgui.domain.ports.RunningProcess;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.domain.secrets.SessionSecrets;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.tools.process.ProcessService;
import org.cometgui.ui.controls.UiIds;
import org.cometgui.ui.controls.provenance.ProvenancePane;
import org.cometgui.ui.viewmodel.provenance.OpenedRecord;
import org.cometgui.ui.viewmodel.provenance.ProvenancePort;
import org.cometgui.ui.viewmodel.provenance.ProvenanceViewModel;
import org.cometgui.workflow.provenance.DiskVerification;
import org.cometgui.workflow.provenance.RunListing;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * The Provenance section through the running application (phase 13, work unit 5; {@code AC-PRV-07},
 * gate items 3 and 4 in the interface): real runs started through the Run section -- the pinned
 * Comet 2026.03.0 and Percolator 3.07.1 on the two {@code D-006} K562 files, then a search
 * cancelled while Comet searches, then a search Comet fails on a damaged spectrum file -- and each
 * opened in the Provenance section from its run list.
 *
 * <ol>
 *   <li>The completed run: each tool row's MD5 and SHA-256 are this test's own digests of the
 *       executables it staged ({@link OwnDigests}, never the product's hasher, P13-7); Copy
 *       SHA-256, Copy MD5 and Copy command put exactly the expected text on the clipboard, read
 *       back -- the command the argument array this test reads from {@code provenance.json} with
 *       its own JSON reader, quoted as the read model's display form quotes it; Export JSON and
 *       Export RST through the save dialog (the scripted chooser) write files byte-identical to the
 *       run's {@code provenance.json} and {@code provenance.rst}, an existing file is refused and
 *       Replace is offered; Verify against disk shows every file matching; Open file location
 *       launches exactly {@code xdg-open <folder>} -- through a recording runner, never a real file
 *       manager -- off the JavaFX thread.
 *   <li>The cancelled run and the failed run, chosen from the list: their status, the stage that
 *       was cancelled or failed with its exit status, and their partial outputs marked PARTIAL.
 *   <li>The section is reached by keyboard: Tab from its navigation entry lands on the run
 *       selector.
 *   <li>No port call the section makes runs on the JavaFX thread: the section is built over the
 *       application's own port ({@link RunWiring#provenance}) with a recorder in front of it, on
 *       the application's threads, and every action is pressed.
 * </ol>
 *
 * <p>Files read outside this module: those of {@link RealSearch} and {@link RealPercolators} --
 * {@code scratch/fixture/} (both K562 mzML files and the UniProt proteome), {@code
 * scratch/phase05/artefacts/v2026.03.0__comet.linux.exe} and {@code
 * scratch/phase05/artefacts/rel-3-07-01__percolator-noxml-ubuntu-portable.zip}; all already read by
 * the existing GUI tests. Wall time about 45 seconds: one real search of the 1000-record subset,
 * one cancelled while loading spectra, one that fails on the damaged file.
 */
@EnabledOnOs(
        value = OS.LINUX,
        disabledReason =
                "only the linux/x86-64 Comet binary has ever been executed in this project")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ProvenanceSectionUiTest {

    private static final BuildIdentity BUILD =
            BuildIdentity.of("0.0.0-guitest", "unknown", Instant.parse("2026-10-14T00:00:00Z"));

    /** How long the section may take to list, open, export or verify. A bound, never a delay. */
    private static final Duration SECTION_BOUND = Duration.ofMinutes(2);

    /** The launchers the application may start to show a file's location. */
    private static final Set<String> FILE_MANAGERS = Set.of("xdg-open", "open", "explorer.exe");

    @TempDir private static Path scratch;

    private static ParameterEditorApp app;

    private static FxUiDriver driver;

    private static Launches launches;

    private static Path comet;

    private static Path percolator;

    private static Path project;

    private static Path exports;

    private static List<Path> spectra;

    private static Path proteome;

    private static Path damaged;

    @BeforeAll
    static void launch() throws IOException {
        Path root = scratch.toRealPath();
        comet = RealSearch.stageComet(root.resolve("bin/comet"));
        ToolOffer staged = RealPercolators.installed3071(root.resolve("bin/percolator"));
        percolator = staged.installedPath().orElseThrow();
        Path inputs = Files.createDirectories(root.resolve("inputs"));
        spectra = RealSearch.spectra(inputs);
        Path subset = RealSearch.subset(inputs.resolve("subset.fasta"));
        proteome = RealSearch.proteome(inputs.resolve("proteome.fasta"));
        damaged = inputs.resolve("damaged.mzML");
        Files.write(damaged, java.util.Arrays.copyOf(Files.readAllBytes(spectra.get(1)), 2000));
        exports = Files.createDirectories(root.resolve("exports"));
        project = root.resolve("project");
        launches = new Launches(new LaunchRecorder(new ProcessService(Clock.systemUTC())));
        app =
                ParameterEditorApp.launch(
                        BUILD,
                        launches,
                        new BoundedMessageLog(),
                        new RunWiring.Setup(
                                () -> InstalledComet.at(RealSearch.RELEASE, comet).with(staged),
                                project));
        driver = new TestFxUiDriver(app.application());

        app.chooser().spectra(spectra.get(0), spectra.get(1));
        ParameterEditorApp.openEditor(driver);
        driver.clickOn("param-mode-essentials");
        driver.clickOn("ess-spectra-add");
        app.chooser().database(subset);
        driver.clickOn("ess-database_name-choose");
        ParameterEditorApp.choose(
                driver,
                "ess-decoy_search",
                "Concatenated: targets and decoys compete, one result per spectrum");
        enter(driver, "ess-num_threads", "4");
    }

    @AfterAll
    static void stop() {
        if (app != null) {
            app.stop();
        }
    }

    // ------------------------------------------------------------------------------ helpers --

    /** Runs the configured search through the Run section and returns the outcome it states. */
    private static String runThroughTheRunSection() {
        driver.clickOn("nav-run");
        assertEquals(
                "The workflow engine can run this search.", RunSection.awaitEngineAnswer(driver));
        driver.clickOn("run-start");
        return RunSection.awaitText(
                driver, "run-outcome", text -> text.startsWith("The run "), RunSection.RUN_BOUND);
    }

    /** Opens the Provenance section and chooses the one listed run whose label ends so. */
    private static String chooseRun(String ending, int listed) {
        driver.clickOn(UiIds.navigationEntry(org.cometgui.ui.viewmodel.SectionId.PROVENANCE));
        await(
                () ->
                        ParameterEditorApp.comboItems(driver, UiIds.PROVENANCE_RUN).size() == listed
                                && ParameterEditorApp.comboItems(driver, UiIds.PROVENANCE_RUN)
                                        .stream()
                                        .anyMatch(label -> label.endsWith(ending)),
                () ->
                        "the run list to hold "
                                + listed
                                + " runs, one ending \""
                                + ending
                                + "\": "
                                + ParameterEditorApp.comboItems(driver, UiIds.PROVENANCE_RUN));
        String label =
                ParameterEditorApp.comboItems(driver, UiIds.PROVENANCE_RUN).stream()
                        .filter(item -> item.endsWith(ending))
                        .findFirst()
                        .orElseThrow();
        ParameterEditorApp.choose(driver, UiIds.PROVENANCE_RUN, label);
        RunSection.awaitText(
                driver,
                UiIds.PROVENANCE_RECORD,
                text -> text.startsWith("Sealed record"),
                SECTION_BOUND);
        return label;
    }

    private static List<String> rows(String id) {
        Node node = driver.node(id);
        if (!(node instanceof ListView<?> list)) {
            return fail("#" + id + " is not a list");
        }
        return driver.callOnFxThread(
                () -> {
                    List<String> texts = new ArrayList<>();
                    for (Object item : list.getItems()) {
                        texts.add(String.valueOf(item));
                    }
                    return texts;
                });
    }

    private static int rowWhere(String id, Predicate<String> wanted, String what) {
        List<String> texts = rows(id);
        for (int index = 0; index < texts.size(); index++) {
            if (wanted.test(texts.get(index))) {
                return index;
            }
        }
        return fail("#" + id + " has no row " + what + ": " + texts);
    }

    /** Selects a row with the keyboard: the list takes the focus, Home, then Down. */
    private static void selectRow(String id, int index) {
        focus(id);
        driver.press(KeyCode.HOME);
        for (int step = 0; step < index; step++) {
            driver.press(KeyCode.DOWN);
        }
        ListView<?> list = (ListView<?>) driver.node(id);
        assertEquals(
                index,
                (int) driver.callOnFxThread(() -> list.getSelectionModel().getSelectedIndex()),
                "#" + id + "'s selected row");
    }

    /** Presses a button with the keyboard: it takes the focus, then Space. */
    private static void press(String id) {
        focus(id);
        driver.press(KeyCode.SPACE);
    }

    private static void focus(String id) {
        Node node = driver.node(id);
        assertFalse(driver.callOnFxThread(node::isDisabled), "#" + id + " is enabled");
        driver.onFxThread(node::requestFocus);
        assertEquals(id, driver.focusedNodeId(), "the focus is on #" + id);
    }

    private static String clipboard() {
        return driver.callOnFxThread(() -> Clipboard.getSystemClipboard().getString());
    }

    private static void await(BooleanSupplier condition, java.util.function.Supplier<String> what) {
        long deadline = System.nanoTime() + SECTION_BOUND.toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                fail("not reached within " + SECTION_BOUND + ": " + what.get());
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                fail("interrupted", interrupted);
            }
        }
    }

    private static Path runDirectory(String label) {
        String id = label.substring(0, label.indexOf(' '));
        try (var listed = Files.list(project.resolve("runs"))) {
            return listed.filter(run -> String.valueOf(run.getFileName()).endsWith("-" + id))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("no run directory for " + id));
        } catch (IOException unreadable) {
            throw new AssertionError("the project's runs cannot be listed", unreadable);
        }
    }

    /** The display form of an argument array, as the read model quotes it, written out here. */
    private static String quoted(List<String> argv) {
        List<String> each = new ArrayList<>();
        for (String argument : argv) {
            assertFalse(
                    argument.contains("\"") || argument.contains("\\"),
                    "an argument this test can quote by hand: " + argument);
            each.add("\"" + argument + "\"");
        }
        return "[" + String.join(", ", each) + "]";
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> tools(Path run) throws IOException {
        Object parsed =
                TestJson.parse(
                        Files.readString(
                                run.resolve("provenance/provenance.json"), StandardCharsets.UTF_8));
        return (List<Map<String, Object>>) ((Map<String, Object>) parsed).get("tools");
    }

    // -------------------------------------------------------------------------------- tests --

    @Test
    @Order(1)
    @DisplayName(
            "a completed real run: tool digests are this test's own, the copies are exact, the"
                    + " exports equal the run's documents, every file verifies, and the location"
                    + " opens through xdg-open")
    void aCompletedRun() throws IOException {
        String outcome = runThroughTheRunSection();
        assertTrue(outcome.startsWith("The run succeeded: run "), outcome);
        String label = chooseRun(", completed, sealed record", 1);
        Path run = runDirectory(label);
        assertTrue(
                driver.textOf(UiIds.PROVENANCE_SUMMARY).contains("\nStatus: completed\n"),
                driver.textOf(UiIds.PROVENANCE_SUMMARY));

        OwnDigests cometDigests = OwnDigests.of(comet);
        OwnDigests percolatorDigests = OwnDigests.of(percolator);
        int cometRow =
                rowWhere(
                        UiIds.PROVENANCE_TOOLS,
                        row -> row.contains(". comet 2026.03.0 -- "),
                        "for Comet");
        int percolatorRow =
                rowWhere(
                        UiIds.PROVENANCE_TOOLS,
                        row -> row.contains(" percolator 3.07.1 -- "),
                        "for Percolator");
        String cometShown = rows(UiIds.PROVENANCE_TOOLS).get(cometRow);
        String percolatorShown = rows(UiIds.PROVENANCE_TOOLS).get(percolatorRow);
        assertAll(
                "the tool rows against this test's own digests of the staged executables",
                () -> assertTrue(cometShown.contains("\nPath: " + comet + "\n"), cometShown),
                () -> assertTrue(cometShown.contains("\nMD5: " + cometDigests.md5() + "\n")),
                () ->
                        assertTrue(
                                cometShown.contains("\nSHA-256: " + cometDigests.sha256() + "\n"),
                                cometShown),
                () -> assertTrue(percolatorShown.contains("\nPath: " + percolator + "\n")),
                () ->
                        assertTrue(
                                percolatorShown.contains(
                                        "\nSHA-256: " + percolatorDigests.sha256() + "\n"),
                                percolatorShown));

        selectRow(UiIds.PROVENANCE_TOOLS, cometRow);
        press(UiIds.PROVENANCE_TOOL_COPY_SHA256);
        assertEquals(cometDigests.sha256(), clipboard(), "Copy SHA-256 of Comet");
        selectRow(UiIds.PROVENANCE_TOOLS, percolatorRow);
        press(UiIds.PROVENANCE_TOOL_COPY_MD5);
        assertEquals(percolatorDigests.md5(), clipboard(), "Copy MD5 of Percolator");

        int spectrumRow =
                rowWhere(
                        UiIds.PROVENANCE_FILES,
                        row -> row.contains("\nPath: " + spectra.get(1) + "\n"),
                        "for k562_4.mzML");
        selectRow(UiIds.PROVENANCE_FILES, spectrumRow);
        press(UiIds.PROVENANCE_FILE_COPY_SHA256);
        assertEquals(OwnDigests.of(spectra.get(1)).sha256(), clipboard(), "a spectrum's SHA-256");
        press(UiIds.PROVENANCE_FILE_COPY_MD5);
        assertEquals(OwnDigests.of(spectra.get(1)).md5(), clipboard(), "a spectrum's MD5");

        List<String> cometArgv = null;
        for (Map<String, Object> tool : tools(run)) {
            @SuppressWarnings("unchecked")
            List<String> argv = (List<String>) TestJson.at(tool, "execution", "argv");
            if (argv.get(argv.size() - 1).equals(spectra.get(1).toString())) {
                cometArgv = argv;
            }
        }
        assertTrue(cometArgv != null, "the record has a Comet execution for k562_4");
        assertEquals(comet.toString(), cometArgv.get(0));
        String expectedCommand = quoted(cometArgv);
        int executionRow =
                rowWhere(
                        UiIds.PROVENANCE_EXECUTIONS,
                        row -> row.contains("\nCommand: " + expectedCommand + "\n"),
                        "with k562_4's command");
        selectRow(UiIds.PROVENANCE_EXECUTIONS, executionRow);
        press(UiIds.PROVENANCE_EXECUTION_COPY_COMMAND);
        assertEquals(expectedCommand, clipboard(), "Copy command");

        Path json = exports.resolve("completed.json");
        app.chooser().exportTo(json);
        press(UiIds.PROVENANCE_EXPORT_JSON);
        RunSection.awaitText(
                driver,
                UiIds.PROVENANCE_EXPORT_STATUS,
                text -> text.equals("Exported provenance JSON to " + json + "."),
                SECTION_BOUND);
        Path rst = exports.resolve("completed.rst");
        app.chooser().exportTo(rst);
        press(UiIds.PROVENANCE_EXPORT_RST);
        RunSection.awaitText(
                driver,
                UiIds.PROVENANCE_EXPORT_STATUS,
                text -> text.equals("Exported provenance RST to " + rst + "."),
                SECTION_BOUND);
        assertAll(
                "the exports, byte for byte the run's own documents",
                () ->
                        assertArrayEquals(
                                Files.readAllBytes(run.resolve("provenance/provenance.json")),
                                Files.readAllBytes(json)),
                () ->
                        assertArrayEquals(
                                Files.readAllBytes(run.resolve("provenance/provenance.rst")),
                                Files.readAllBytes(rst)),
                () ->
                        assertTrue(
                                app.chooser()
                                        .asked()
                                        .contains(
                                                "export:Export provenance JSON:provenance-"
                                                        + label.substring(0, label.indexOf(' '))
                                                        + ".json"),
                                () -> "the dialog was asked: " + app.chooser().asked()));

        Files.writeString(json, "not a record");
        app.chooser().exportTo(json);
        press(UiIds.PROVENANCE_EXPORT_JSON);
        String refused =
                RunSection.awaitText(
                        driver,
                        UiIds.PROVENANCE_EXPORT_STATUS,
                        text -> text.startsWith("Nothing was exported: "),
                        SECTION_BOUND);
        assertAll(
                () -> assertTrue(refused.contains(json + " already exists"), refused),
                () -> assertEquals("not a record", Files.readString(json), "left as it was"));
        press(UiIds.PROVENANCE_EXPORT_REPLACE);
        RunSection.awaitText(
                driver,
                UiIds.PROVENANCE_EXPORT_STATUS,
                text -> text.equals("Exported provenance JSON to " + json + "."),
                SECTION_BOUND);
        assertArrayEquals(
                Files.readAllBytes(run.resolve("provenance/provenance.json")),
                Files.readAllBytes(json),
                "replaced, once confirmed");

        press(UiIds.PROVENANCE_VERIFY);
        String verified =
                RunSection.awaitText(
                        driver,
                        UiIds.PROVENANCE_VERIFY_STATUS,
                        text -> !text.startsWith("Verifying"),
                        SECTION_BOUND);
        List<String> checked = rows(UiIds.PROVENANCE_VERIFICATION);
        assertAll(
                "every file matches",
                () ->
                        assertEquals(
                                "Every file the record lists matches its record on disk: "
                                        + checked.size()
                                        + " files checked.",
                                verified),
                () -> assertTrue(checked.size() > 12, "inputs, outputs, logs, tools: " + checked),
                () ->
                        assertEquals(
                                List.of(),
                                checked.stream()
                                        .filter(row -> !row.startsWith("MATCHES -- "))
                                        .toList()));

        int pinRow =
                rowWhere(
                        UiIds.PROVENANCE_FILES,
                        row -> row.contains("k562_4.pin\n") && row.startsWith("output "),
                        "for k562_4.pin");
        String pinPath = rows(UiIds.PROVENANCE_FILES).get(pinRow);
        Path pinFolder =
                Path.of(
                                pinPath.substring(
                                        pinPath.indexOf("\nPath: ") + 7,
                                        pinPath.indexOf('\n', pinPath.indexOf("\nPath: ") + 1)))
                        .getParent();
        selectRow(UiIds.PROVENANCE_FILES, pinRow);
        press(UiIds.PROVENANCE_FILE_OPEN_LOCATION);
        RunSection.awaitText(
                driver,
                UiIds.PROVENANCE_ACTION_STATUS,
                text -> text.equals("Started xdg-open: the file manager was asked to show it."),
                SECTION_BOUND);
        assertAll(
                "Open file location, through the one process runner",
                () ->
                        assertEquals(
                                List.of(
                                        List.of(
                                                "xdg-open",
                                                run.resolve("outputs/comet").toString())),
                                launches.fileManagerArgv()),
                () -> assertEquals(run.resolve("outputs/comet"), pinFolder),
                () ->
                        assertEquals(
                                List.of(false),
                                launches.fileManagerOnFxThread(),
                                "never on the JavaFX thread"));
    }

    @Test
    @Order(2)
    @DisplayName(
            "a real run cancelled while Comet searches: listed as cancelled, the stage cancelled,"
                    + " its outputs PARTIAL")
    void aCancelledRun() throws IOException {
        ParameterEditorApp.openEditor(driver);
        driver.clickOn("param-mode-essentials");
        driver.clickOn(UiIds.spectrumRemove(1));
        app.chooser().database(proteome);
        driver.clickOn("ess-database_name-choose");
        enter(driver, "ess-num_threads", "1");

        CompletableFuture<String> searching = launches.real().lineContaining("Load spectra:");
        driver.clickOn("nav-run");
        assertEquals(
                "The workflow engine can run this search.", RunSection.awaitEngineAnswer(driver));
        driver.clickOn("run-start");
        within(searching);
        driver.clickOn("run-cancel");
        String outcome =
                RunSection.awaitText(
                        driver,
                        "run-outcome",
                        text -> text.startsWith("The run "),
                        RunSection.RUN_BOUND);
        assertTrue(outcome.startsWith("The run was cancelled: "), outcome);

        chooseRun(", cancelled, sealed record", 2);
        assertShows("cancelled", "k562_3", null);
    }

    @Test
    @Order(3)
    @DisplayName(
            "a real run Comet fails on a damaged spectrum file: listed as failed, the stage"
                    + " failed with exit status 134, its outputs PARTIAL")
    void aFailedRun() throws IOException {
        ParameterEditorApp.openEditor(driver);
        driver.clickOn("param-mode-essentials");
        app.chooser().spectra(damaged);
        driver.clickOn("ess-spectra-add");
        String outcome = runThroughTheRunSection();
        assertTrue(outcome.startsWith("The run failed"), outcome);

        chooseRun(", failed, sealed record", 3);
        assertShows("failed", "damaged", 134);
    }

    /**
     * The chosen run's summary says its status; the Comet stage of the named input shows that
     * status and its exit status; and its pepXML and PIN are marked PARTIAL first, in words, with a
     * partial-file warning each.
     */
    private static void assertShows(String status, String stem, Integer exitCode) {
        String summary = driver.textOf(UiIds.PROVENANCE_SUMMARY);
        List<String> executions = rows(UiIds.PROVENANCE_EXECUTIONS);
        List<String> files = rows(UiIds.PROVENANCE_FILES);
        List<String> warnings = rows(UiIds.PROVENANCE_WARNINGS);
        String stage =
                executions.stream()
                        .filter(row -> row.contains(stem + ".mzML\"]\n"))
                        .findFirst()
                        .orElseGet(() -> fail("no execution for " + stem + ": " + executions));
        assertAll(
                status + " run",
                () -> assertTrue(summary.contains("\nStatus: " + status + "\n"), summary),
                () ->
                        assertTrue(
                                stage.contains(
                                        "-- "
                                                + status
                                                + ", exit status "
                                                + (exitCode == null ? "" : exitCode)),
                                stage),
                () ->
                        assertFalse(
                                stage.contains("-- " + status + ", exit status 0\n"),
                                "a stage that did not complete has a non-zero exit: " + stage),
                () ->
                        assertTrue(
                                files.stream()
                                        .anyMatch(
                                                row ->
                                                        row.startsWith("PARTIAL -- output pepxml\n")
                                                                && row.contains(
                                                                        stem + ".pep.xml\n")),
                                () -> "the pepXML marked PARTIAL: " + files),
                () ->
                        assertTrue(
                                files.stream()
                                        .anyMatch(
                                                row ->
                                                        row.startsWith("PARTIAL -- output pin\n")
                                                                && row.contains(stem + ".pin\n")),
                                () -> "the PIN marked PARTIAL: " + files),
                () ->
                        assertTrue(
                                warnings.stream().anyMatch(row -> row.startsWith("Partial file (")),
                                () -> "the partial files warned about: " + warnings));
    }

    @Test
    @Order(4)
    @DisplayName("the section is reached by keyboard: Tab from its entry lands on the run selector")
    void keyboard() {
        String entry = UiIds.navigationEntry(org.cometgui.ui.viewmodel.SectionId.PROVENANCE);
        driver.clickOn(entry);
        assertEquals(entry, driver.focusedNodeId());
        driver.tab();
        assertEquals(UiIds.PROVENANCE_RUN, driver.focusedNodeId());
        driver.tab();
        assertEquals(UiIds.PROVENANCE_REFRESH, driver.focusedNodeId());
    }

    @Test
    @Order(5)
    @DisplayName(
            "no port call the section makes runs on the JavaFX thread: the application's own port"
                    + " behind a recorder, every action pressed")
    void noPortCallOnTheFxThread() throws IOException {
        ApplicationServices real = ApplicationServices.forThisHost();
        ApplicationServices services =
                new ApplicationServices(
                        real.clock(),
                        real.environment(),
                        real.fileSystem(),
                        real.runIds(),
                        real.glibcVersions(),
                        launches,
                        null,
                        null);
        ProjectSession session = new ProjectSession(project, Clock.systemUTC(), real.runIds());
        SessionEngine engine =
                RunWiring.port(
                        services,
                        new BoundedMessageLog(),
                        Optional.empty(),
                        "no tools in this check",
                        session,
                        BUILD);
        ThreadRecorder port =
                new ThreadRecorder(
                        RunWiring.provenance(
                                services,
                                session,
                                RunWiring.hasher(),
                                engine,
                                new SessionSecrets()));
        ScriptedChooser chooser =
                new ScriptedChooser()
                        .exportTo(exports.resolve("threads.json"))
                        .exportTo(exports.resolve("threads.rst"));
        ExecutorService threads = RunWiring.backgroundThreads();
        int launchesBefore = launches.fileManagerArgv().size();
        try {
            ProvenanceViewModel section =
                    new ProvenanceViewModel(port, chooser, threads, Platform::runLater);
            Scene scene =
                    driver.callOnFxThread(
                            () -> {
                                // the pane's content joins the scene graph when its skin is built
                                Scene built = new Scene(new ProvenancePane(section), 900, 700);
                                built.getRoot().applyCss();
                                built.getRoot().layout();
                                return built;
                            });
            driver.onFxThread(section::refresh);
            await(
                    () -> driver.callOnFxThread(() -> section.runsProperty().get().size()) == 3,
                    () -> "three runs listed: " + section.runsProperty().get());
            for (int index = 2; index >= 0; index--) {
                int chosen = index;
                driver.onFxThread(() -> section.choose(chosen));
                await(
                        () -> driver.callOnFxThread(() -> section.recordShownProperty().get()),
                        () -> "run " + chosen + " shown: " + section.recordTextProperty().get());
            }
            fireOn(scene, UiIds.PROVENANCE_EXPORT_JSON);
            awaitLabel(scene, UiIds.PROVENANCE_EXPORT_STATUS, "Exported provenance JSON to ");
            fireOn(scene, UiIds.PROVENANCE_EXPORT_RST);
            awaitLabel(scene, UiIds.PROVENANCE_EXPORT_STATUS, "Exported provenance RST to ");
            fireOn(scene, UiIds.PROVENANCE_VERIFY);
            awaitLabel(scene, UiIds.PROVENANCE_VERIFY_STATUS, "Every file the record lists");
            driver.onFxThread(
                    () ->
                            ((ListView<?>) scene.lookup("#" + UiIds.PROVENANCE_LOGS))
                                    .getSelectionModel()
                                    .select(0));
            fireOn(scene, UiIds.PROVENANCE_LOG_OPEN_LOCATION);
            awaitLabel(scene, UiIds.PROVENANCE_ACTION_STATUS, "Started xdg-open");
        } finally {
            threads.shutdownNow();
        }
        List<ThreadRecorder.Call> calls = port.calls();
        assertAll(
                "every port call, off the JavaFX thread",
                () ->
                        assertEquals(
                                List.of(
                                        "runs",
                                        "open",
                                        "open",
                                        "open",
                                        "exportJson",
                                        "exportRst",
                                        "verify",
                                        "openLocation"),
                                calls.stream().map(ThreadRecorder.Call::method).toList(),
                                "every kind of call was made, so the check cannot pass empty"),
                () ->
                        assertEquals(
                                List.of(),
                                calls.stream().filter(ThreadRecorder.Call::onFxThread).toList(),
                                "a port call on the JavaFX application thread stalls the window"
                                        + " for as long as it blocks"),
                () ->
                        assertEquals(
                                List.of(),
                                calls.stream()
                                        .filter(call -> !call.thread().startsWith("cometgui-run-"))
                                        .toList(),
                                "every call on the application's background threads"),
                () ->
                        assertEquals(
                                List.of(false),
                                launches.fileManagerOnFxThread()
                                        .subList(
                                                launchesBefore,
                                                launches.fileManagerOnFxThread().size())));
    }

    private static void fireOn(Scene scene, String id) {
        driver.onFxThread(
                () -> {
                    Button button = (Button) scene.lookup("#" + id);
                    assertFalse(button.isDisabled(), "#" + id + " is enabled");
                    button.fire();
                });
    }

    private static void awaitLabel(Scene scene, String id, String start) {
        await(
                () ->
                        driver.callOnFxThread(
                                () -> ((Label) scene.lookup("#" + id)).getText().startsWith(start)),
                () ->
                        "#"
                                + id
                                + " to start \""
                                + start
                                + "\": "
                                + driver.callOnFxThread(
                                        () -> ((Label) scene.lookup("#" + id)).getText()));
    }

    private static void within(CompletableFuture<String> line) {
        try {
            line.get(RunSection.RUN_BOUND.toSeconds(), TimeUnit.SECONDS);
        } catch (TimeoutException timedOut) {
            fail("Comet did not start searching within " + RunSection.RUN_BOUND);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            fail("interrupted", interrupted);
        } catch (ExecutionException impossible) {
            fail("the wait failed", impossible);
        }
    }

    /**
     * The application's process runner: every tool is started for real through the recorder in
     * front of the product's process service, except a file manager, which is recorded -- its
     * argument array and whether it was asked for on the JavaFX thread -- and answered as a
     * launcher that ended at once with status 0. No test starts a real file manager.
     */
    private static final class Launches implements ProcessRunner {

        private final LaunchRecorder real;

        private final List<List<String>> fileManagers =
                Collections.synchronizedList(new ArrayList<>());

        private final List<Boolean> onFxThread = Collections.synchronizedList(new ArrayList<>());

        Launches(LaunchRecorder real) {
            this.real = real;
        }

        LaunchRecorder real() {
            return real;
        }

        List<List<String>> fileManagerArgv() {
            synchronized (fileManagers) {
                return List.copyOf(fileManagers);
            }
        }

        List<Boolean> fileManagerOnFxThread() {
            synchronized (onFxThread) {
                return List.copyOf(onFxThread);
            }
        }

        @Override
        public RunningProcess start(ToolCommand command, ProcessListener listener)
                throws IOException {
            if (!FILE_MANAGERS.contains(command.argv().get(0))) {
                return real.start(command, listener);
            }
            fileManagers.add(command.argv());
            onFxThread.add(Platform.isFxApplicationThread());
            listener.onExit(0);
            return new RunningProcess() {
                @Override
                public boolean isAlive() {
                    return false;
                }

                @Override
                public int waitForExit() {
                    return 0;
                }

                @Override
                public void requestCancellation() {
                    // nothing was started
                }
            };
        }
    }

    /** The application's provenance port, with every call's thread recorded before it is made. */
    private static final class ThreadRecorder implements ProvenancePort {

        /** One call: which, whether on the JavaFX thread, and the thread's name. */
        record Call(String method, boolean onFxThread, String thread) {}

        private final ProvenancePort delegate;

        private final List<Call> calls = Collections.synchronizedList(new ArrayList<>());

        ThreadRecorder(ProvenancePort delegate) {
            this.delegate = delegate;
        }

        List<Call> calls() {
            synchronized (calls) {
                return List.copyOf(calls);
            }
        }

        private void record(String method) {
            calls.add(
                    new Call(
                            method,
                            Platform.isFxApplicationThread(),
                            Thread.currentThread().getName()));
        }

        @Override
        public List<RunListing> runs() throws IOException {
            record("runs");
            return delegate.runs();
        }

        @Override
        public OpenedRecord open(RunListing run) throws IOException {
            record("open");
            return delegate.open(run);
        }

        @Override
        public Path exportJson(OpenedRecord run, Path target, boolean replaceExisting)
                throws IOException {
            record("exportJson");
            return delegate.exportJson(run, target, replaceExisting);
        }

        @Override
        public Path exportRst(OpenedRecord run, Path target, boolean replaceExisting)
                throws IOException {
            record("exportRst");
            return delegate.exportRst(run, target, replaceExisting);
        }

        @Override
        public DiskVerification verify(OpenedRecord run, BooleanSupplier cancelled)
                throws IOException {
            record("verify");
            return delegate.verify(run, cancelled);
        }

        @Override
        public String openLocation(Path file) throws IOException {
            record("openLocation");
            return delegate.openLocation(file);
        }
    }
}
