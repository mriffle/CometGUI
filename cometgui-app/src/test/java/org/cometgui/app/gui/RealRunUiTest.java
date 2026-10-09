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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import javafx.scene.input.KeyCode;
import org.cometgui.app.config.RunWiring;
import org.cometgui.app.testing.InstalledComet;
import org.cometgui.app.testing.RealPercolators;
import org.cometgui.app.testing.RealSearch;
import org.cometgui.app.testing.RealSearch.LaunchRecorder;
import org.cometgui.app.uidriver.FxUiDriver;
import org.cometgui.app.uidriver.TestFxUiDriver;
import org.cometgui.domain.build.BuildIdentity;
import org.cometgui.domain.log.BoundedMessageLog;
import org.cometgui.domain.log.LogMessage;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.provenance.manifest.ManifestReader;
import org.cometgui.provenance.manifest.ProvenanceManifest;
import org.cometgui.provenance.manifest.ToolRecord;
import org.cometgui.tools.process.ProcessService;
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
 * A real search through the interface (decision P8-16, {@code R-RUN-01} in the interface): the
 * pinned Comet 2026.03.0 registered with the Tool Manager, the two {@code D-006} K562 files and the
 * proteome's first 1000 records chosen through the interface, Comet's own concatenated decoys, and
 * Run pressed.
 *
 * <ol>
 *   <li>The run: launched off the JavaFX thread through the process service, the stepper moving to
 *       Comet succeeded, the outputs on disk (one {@code -N} base per file, the merged PIN with one
 *       header and 3554 + 2918 = 6472 rows, the numbers unit 4 recorded), the console carrying
 *       Comet's own output, the outcome stated, and no Comet left running. Since Phase 09 a run
 *       includes Percolator: the pinned 3.07.1, staged and probed and offered as an installed
 *       managed build, is the only Percolator here and so the resolved default; it rescores the
 *       merged PIN with no XML option (Limelight conversion is off), writes one PSM row per target
 *       row, and provenance names it and why.
 *   <li>Nothing changed: Run is disabled because nothing would run, and the preview says every step
 *       is reused.
 *   <li>A parameter changed: the rerun preview names every step and why, before anything starts.
 *   <li>A spectrum file changed in place: a new run, and the last run's own refusal to reuse it in
 *       the words of Phase 08 gate 8 -- the file, its role, both SHA-256s.
 *   <li>The release's placeholder spectral library is refused before Comet, in words, Run disabled;
 *       then a configuration Comet itself refuses (an empty file as the spectral library): the
 *       failure is stated with its step, never swallowed, and the stepper shows Comet failed.
 * </ol>
 *
 * <p>Files read outside this module: see {@link RealSearch} and {@link RealPercolators}. Every
 * expected text is typed out; a run's identifier and directory, which a clock names, are read from
 * the project.
 */
@EnabledOnOs(
        value = OS.LINUX,
        disabledReason =
                "only the linux/x86-64 Comet binary has ever been executed in this project")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RealRunUiTest {

    private static final BuildIdentity BUILD =
            BuildIdentity.of("0.0.0-guitest", "unknown", Instant.parse("2026-10-07T00:00:00Z"));

    private static final String ENGINE_CAN_RUN = "The workflow engine can run this search.";

    @TempDir private static Path scratch;

    private static ParameterEditorApp app;

    private static FxUiDriver driver;

    private static LaunchRecorder launches;

    private static BoundedMessageLog console;

    private static Path comet;

    /** The staged Percolator 3.07.1, the only Percolator the Tool Manager offers here. */
    private static Path percolatorExecutable;

    private static Path project;

    private static List<Path> spectra;

    /**
     * An empty, readable file named {@code .file}: it passes the pre-run check, and Comet 2026.03.0
     * refuses it, exit 1, "Error, expecting sqlite .db or Thermo .raw file for the spectral
     * library." Measured on the pinned binary: the same empty file with no extension, or named
     * {@code .msp}, is searched with exit 0, so the extension is load-bearing.
     */
    private static Path emptyLibrary;

    @BeforeAll
    static void launch() throws IOException {
        Path root = scratch.toRealPath();
        comet = RealSearch.stageComet(root.resolve("bin/comet"));
        ToolOffer percolator = RealPercolators.installed3071(root.resolve("bin/percolator"));
        percolatorExecutable = percolator.installedPath().orElseThrow();
        Path inputs = Files.createDirectories(root.resolve("inputs"));
        spectra = RealSearch.spectra(inputs);
        Path subset = RealSearch.subset(inputs.resolve("subset.fasta"));
        emptyLibrary = Files.createFile(inputs.resolve("empty-library.file"));
        project = root.resolve("project");
        launches = new LaunchRecorder(new ProcessService(Clock.systemUTC()));
        console = new BoundedMessageLog();
        app =
                ParameterEditorApp.launch(
                        BUILD,
                        launches,
                        console,
                        new RunWiring.Setup(
                                () -> InstalledComet.at(RealSearch.RELEASE, comet).with(percolator),
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
        // A new configuration searches without a spectral library (D-012): nothing to clear, and
        // every real search below runs with the starting set's empty value.
        driver.clickOn("param-mode-advanced");
        ParameterEditorApp.showCategory(driver, "adv-category-ms1_realtime-toggle");
        assertEquals("", driver.textOf("adv-spectral_library_name"));
        driver.clickOn("param-mode-essentials");
    }

    @AfterAll
    static void stop() {
        try {
            assertNoCometAlive();
        } finally {
            if (app != null) {
                app.stop();
            }
        }
    }

    /** Names a spectral library in Advanced. */
    private static void spectralLibrary(String path) {
        ParameterEditorApp.openEditor(driver);
        driver.clickOn("param-mode-advanced");
        ParameterEditorApp.showCategory(driver, "adv-category-ms1_realtime-toggle");
        driver.typeInto("adv-spectral_library_name", path);
        driver.press(KeyCode.ENTER);
        assertEquals(path, driver.textOf("adv-spectral_library_name"));
        driver.clickOn("param-mode-essentials");
    }

    @Test
    @Order(1)
    @DisplayName("Run searches both files with the real Comet: stepper, outputs, console, outcome")
    void aRealRunThroughTheInterface() throws IOException {
        driver.clickOn("nav-run");
        assertEquals(ENGINE_CAN_RUN, RunSection.awaitEngineAnswer(driver));
        assertEquals("Not started", driver.textOf("stage-comet-state"));
        assertTrue(RunSection.isDisabled(driver, "run-cancel"), "nothing to cancel yet");

        driver.clickOn("run-start");
        assertTrue(
                RunSection.isDisabled(driver, "run-start"),
                "Run is disabled the moment it is pressed");
        String outcome =
                RunSection.awaitText(
                        driver,
                        "run-outcome",
                        text -> text.startsWith("The run "),
                        RunSection.RUN_BOUND);

        Path run = onlyRun(0);
        String id = idOf(run);
        assertEquals("The run succeeded: run " + id + " in " + run + ".", outcome);
        assertAll(
                "the stepper followed the engine",
                () -> assertEquals("Succeeded", driver.textOf("stage-inputs-state")),
                () -> assertEquals("Succeeded", driver.textOf("stage-validate-state")),
                () -> assertEquals("Succeeded", driver.textOf("stage-comet-state")),
                () ->
                        assertEquals(
                                "Succeeded",
                                driver.textOf("stage-percolator-state"),
                                "Percolator rescored the merged PIN (phase 09)"),
                () -> assertTrue(RunSection.isDisabled(driver, "run-cancel")));

        for (String output :
                List.of(
                        "outputs/comet/k562_3.pep.xml",
                        "outputs/comet/k562_3.pin",
                        "outputs/comet/k562_4.pep.xml",
                        "outputs/comet/k562_4.pin",
                        "parameters/comet.params",
                        "parameters/percolator-settings.json",
                        "outputs/percolator/psms.tsv",
                        "outputs/percolator/peptides.tsv",
                        "outputs/percolator/decoy-psms.tsv",
                        "outputs/percolator/decoy-peptides.tsv",
                        "outputs/percolator/weights.txt",
                        "provenance/provenance.json")) {
            assertTrue(Files.isRegularFile(run.resolve(output)), run.resolve(output) + " exists");
        }
        List<String> merged =
                Files.readAllLines(run.resolve("inputs/pin/merged.pin"), StandardCharsets.UTF_8);
        assertEquals(
                1,
                merged.stream().filter(line -> line.startsWith("SpecId\t")).count(),
                "one header");
        assertTrue(merged.get(0).startsWith("SpecId\t"), "the header first");
        assertEquals(6472, merged.size() - 1, "3554 + 2918 data rows");
        assertPercolatorRescored(run, merged);

        List<LaunchRecorder.Launch> comets = cometLaunches();
        assertEquals(2, comets.size(), "one Comet per spectrum file");
        for (LaunchRecorder.Launch launch : comets) {
            assertFalse(launch.onFxThread(), "Comet was launched on the JavaFX thread");
            assertTrue(
                    launch.argv().stream().anyMatch(argument -> argument.startsWith("-N")),
                    () -> "every invocation names its output base: " + launch.argv());
        }
        assertTrue(
                console.snapshot().stream()
                        .map(LogMessage::text)
                        .anyMatch(text -> text.contains("Comet version \"2026.03 rev. 0")),
                "the console shows Comet's own output");
        assertNoCometAlive();
    }

    @Test
    @Order(2)
    @DisplayName("nothing changed after a success: nothing would run, every step reused")
    void nothingChanged() {
        driver.clickOn("nav-run");
        String id = idOf(onlyRun(0));
        assertEquals(
                "The workflow engine cannot start this search:\nNothing would run: every step of"
                        + " run "
                        + id
                        + " succeeded and its recorded results still match. Change a parameter or"
                        + " an input file to search again.",
                RunSection.awaitEngineAnswer(driver));
        assertTrue(RunSection.isDisabled(driver, "run-start"));
        assertEquals(
                "Rerun preview against run "
                        + id
                        + ":\nnothing the steps read has changed, so Run retries run "
                        + id
                        + " and runs exactly the steps marked below.\n"
                        + "- validate-configuration: not needed\n"
                        + "- resolve-comet: not needed\n"
                        + "- resolve-percolator: not needed\n"
                        + "- serialise-comet-params: reused from run "
                        + id
                        + "\n- hash-inputs: not needed\n"
                        + "- run-comet: reused from run "
                        + id
                        + "\n- validate-comet-outputs: reused from run "
                        + id
                        + "\n- merge-pin: reused from run "
                        + id
                        + "\n- run-percolator: reused from run "
                        + id
                        + "\n- parse-percolator: reused from run "
                        + id
                        + "\n- finalise-results: reused from run "
                        + id
                        + "\n- finalise-provenance: reused from run "
                        + id,
                driver.textOf("run-preview"));
    }

    @Test
    @Order(3)
    @DisplayName("a changed parameter: the rerun preview names every step and why, before Run")
    void previewAfterAParameterChange() {
        ParameterEditorApp.openEditor(driver);
        enter(driver, "ess-fragment_bin_tol", "1.0005");
        driver.clickOn("nav-run");
        assertEquals(ENGINE_CAN_RUN, RunSection.awaitEngineAnswer(driver));
        String id = idOf(onlyRun(0));
        assertEquals(
                "Rerun preview against run "
                        + id
                        + ":\ncomet-parameters changed, so Run starts a new run -- a run's"
                        + " configuration never changes once it starts -- and every step executes"
                        + " in it.\n"
                        + "- validate-configuration: runs again, as a prerequisite (needed by"
                        + " resolve-comet; needed by resolve-percolator; needed by"
                        + " serialise-comet-params; needed by hash-inputs)\n"
                        + "- resolve-comet: runs again, as a prerequisite (needed by run-comet)\n"
                        + "- resolve-percolator: runs again, as a prerequisite (needed by"
                        + " run-percolator)\n"
                        + "- serialise-comet-params: re-executes (comet-parameters changed)\n"
                        + "- hash-inputs: runs again, as a prerequisite (needed by run-comet)\n"
                        + "- run-comet: re-executes (comet-parameters changed;"
                        + " serialise-comet-params re-executes)\n"
                        + "- validate-comet-outputs: re-executes (run-comet re-executes)\n"
                        + "- merge-pin: re-executes (validate-comet-outputs re-executes)\n"
                        + "- run-percolator: re-executes (merge-pin re-executes)\n"
                        + "- parse-percolator: re-executes (run-percolator re-executes)\n"
                        + "- finalise-results: re-executes (parse-percolator re-executes)\n"
                        + "- finalise-provenance: re-executes (merge-pin re-executes;"
                        + " finalise-results re-executes)",
                driver.textOf("run-preview"));
        assertFalse(RunSection.isDisabled(driver, "run-start"), "and Run is offered");
        assertEquals(2, cometLaunches().size(), "a preview launches nothing");
    }

    @Test
    @Order(4)
    @DisplayName(
            "a spectrum file changed in place: a new run, and the last run's refusal to reuse"
                    + " it in gate 8's words, naming the file and both hashes")
    void aChangedInputIsNamed() throws IOException {
        ParameterEditorApp.openEditor(driver);
        driver.clickOn("ess-fragment_bin_tol-reset");
        assertEquals("0.02", driver.textOf("ess-fragment_bin_tol"));
        Path k4 = spectra.get(1);
        Files.writeString(k4, "\n", StandardCharsets.US_ASCII, StandardOpenOption.APPEND);
        String now = RealSearch.sha256(k4);
        // The check reads the files when the configuration changes; choosing the same database
        // again changes nothing in the parameters, so a spectrum file is chosen again instead.
        driver.clickOn("ess-spectrum-1-remove");
        app.chooser().spectra(k4);
        driver.clickOn("ess-spectra-add");
        driver.clickOn("nav-run");
        assertEquals(ENGINE_CAN_RUN, RunSection.awaitEngineAnswer(driver));
        String id = idOf(onlyRun(0));
        assertEquals(
                "Rerun preview against run "
                        + id
                        + ":\nspectrum-files changed, so Run starts a new run -- a run's"
                        + " configuration never changes once it starts -- and every step executes"
                        + " in it.\n"
                        + "- validate-configuration: runs again, as a prerequisite (needed by"
                        + " resolve-comet; needed by resolve-percolator; needed by hash-inputs)\n"
                        + "- resolve-comet: runs again, as a prerequisite (needed by run-comet)\n"
                        + "- resolve-percolator: runs again, as a prerequisite (needed by"
                        + " run-percolator)\n"
                        + "- serialise-comet-params: executes in the new run (unchanged since run "
                        + id
                        + ", but a new run records its own results)\n"
                        + "- hash-inputs: runs again, as a prerequisite (needed by run-comet)\n"
                        + "- run-comet: re-executes (spectrum-files changed)\n"
                        + "- validate-comet-outputs: re-executes (run-comet re-executes)\n"
                        + "- merge-pin: re-executes (validate-comet-outputs re-executes)\n"
                        + "- run-percolator: re-executes (merge-pin re-executes)\n"
                        + "- parse-percolator: re-executes (run-percolator re-executes)\n"
                        + "- finalise-results: re-executes (parse-percolator re-executes)\n"
                        + "- finalise-provenance: re-executes (merge-pin re-executes;"
                        + " finalise-results re-executes)\n"
                        + "Run "
                        + id
                        + "'s recorded results cannot be reused because they no longer match the"
                        + " record:\n- "
                        + k4
                        + " (input file, role spectrum, of step run-comet) has changed since it"
                        + " was recorded: recorded SHA-256"
                        + " 602aad75e18257feabdb94ece0fa82e19e5f17153eee4fb89875976920855c82, now "
                        + now
                        + "\nthese steps must run again: run-comet, validate-comet-outputs,"
                        + " merge-pin, run-percolator, parse-percolator, finalise-results,"
                        + " finalise-provenance",
                driver.textOf("run-preview"));
    }

    @Test
    @Order(5)
    @DisplayName("a run Comet refuses: the failure is stated with its step, never swallowed")
    void aFailureIsStated() throws IOException {
        // Comet's own placeholder spectral library, NAMED (a new configuration starts empty), is
        // still refused before Comet (unit 7b), on screen.
        spectralLibrary("/some/path/speclib.file");
        driver.clickOn("nav-run");
        assertEquals(
                "The workflow engine cannot start this search:\nspectral_library_name (Spectral"
                        + " library file) = /some/path/speclib.file does not exist or cannot be"
                        + " read; clear it to search without one, or choose the file",
                RunSection.awaitEngineAnswer(driver));
        assertTrue(RunSection.isDisabled(driver, "run-start"), "the placeholder disables Run");
        int launched = cometLaunches().size();

        // An empty, readable file passes the check, and Comet itself refuses it.
        spectralLibrary(emptyLibrary.toString());
        driver.clickOn("nav-run");
        assertEquals(ENGINE_CAN_RUN, RunSection.awaitEngineAnswer(driver));
        assertEquals(launched, cometLaunches().size(), "the refused placeholder launched nothing");
        driver.clickOn("run-start");
        String outcome =
                RunSection.awaitText(
                        driver,
                        "run-outcome",
                        text -> text.startsWith("The run "),
                        RunSection.RUN_BOUND);
        Path run = onlyRun(1);
        String id = idOf(run);
        List<String> expected =
                Stream.of("01", "02")
                        .map(
                                position ->
                                        "The run failed at step run-comet (Run Comet once per"
                                                + " spectrum file): invocation comet-"
                                                + position
                                                + " exited with code 1; its log is "
                                                + run
                                                + "/logs/comet-"
                                                + position
                                                + ".log. Run "
                                                + id
                                                + " in "
                                                + run
                                                + ".")
                        .toList();
        assertTrue(expected.contains(outcome), () -> outcome + "\nis none of " + expected);
        String position =
                outcome.substring(outcome.indexOf("invocation comet-") + 17).substring(0, 2);
        assertTrue(
                Files.readString(run.resolve("logs/comet-" + position + ".log"))
                        .contains(
                                "Error, expecting sqlite .db or Thermo .raw file for the spectral"
                                        + " library."),
                "the failure is Comet's own refusal of the library, in its stage log");
        assertEquals("Failed", driver.textOf("stage-comet-state"));
        assertEquals(
                ENGINE_CAN_RUN,
                RunSection.awaitEngineAnswer(driver),
                "checked again once the run ended");
        assertFalse(RunSection.isDisabled(driver, "run-start"), "Run is offered again");
        assertTrue(
                driver.textOf("run-preview")
                        .startsWith(
                                "Rerun preview against run "
                                        + id
                                        + ":\nnothing the steps read has changed, so Run retries"
                                        + " run "
                                        + id),
                () -> "a retry of the failed run: " + driver.textOf("run-preview"));
        assertNoCometAlive();
    }

    /**
     * Percolator 3.07.1 -- the only build here, so the resolved default with Limelight conversion
     * off -- rescored the merged PIN: launched once, off the JavaFX thread, with no XML option, one
     * PSM row per target row of the merged PIN (3285, unit 5's figure, counted here from the PIN as
     * well), no {@code pout.xml}, and provenance naming the build and why.
     */
    private static void assertPercolatorRescored(Path run, List<String> merged) throws IOException {
        List<LaunchRecorder.Launch> percolators = percolatorLaunches();
        assertEquals(1, percolators.size(), "one Percolator invocation");
        LaunchRecorder.Launch launch = percolators.get(0);
        assertFalse(launch.onFxThread(), "Percolator was launched on the JavaFX thread");
        assertFalse(launch.argv().contains("-X"), () -> "no XML is asked for: " + launch.argv());
        assertEquals(
                run.resolve("inputs/pin/merged.pin").toString(),
                launch.argv().get(launch.argv().size() - 1),
                "Percolator reads the merged PIN");
        long targets =
                merged.stream().skip(1).filter(row -> row.split("\t", 3)[1].equals("1")).count();
        assertEquals(3285, targets, "the merged PIN's target rows");
        List<String> psms =
                Files.readAllLines(
                        run.resolve("outputs/percolator/psms.tsv"), StandardCharsets.UTF_8);
        assertEquals(targets, psms.size() - 1, "one PSM row per target row");
        assertFalse(Files.exists(run.resolve("outputs/percolator/pout.xml")), "no XML written");
        ProvenanceManifest manifest =
                ManifestReader.readFrom(run.resolve("provenance/provenance.json"));
        Map<String, String> settings = manifest.settings();
        assertAll(
                "provenance",
                () -> assertEquals("3.07.1", settings.get("percolator.version")),
                () -> assertEquals("managed", settings.get("percolator.origin")),
                () -> assertEquals("resolved-default", settings.get("percolator.selection")),
                () -> assertEquals("none", settings.get("percolator.downstream-stages")),
                () ->
                        assertEquals(
                                "Percolator 3.07.1 is the newest Percolator that can be used on"
                                        + " this computer.",
                                settings.get("percolator.selection-reason")),
                () -> assertEquals("1", settings.get("percolator.seed")),
                () ->
                        assertEquals(
                                RealPercolators.SHA256_3071,
                                settings.get("percolator.binary-sha256")),
                () ->
                        assertEquals(
                                RealPercolators.I_SPLINE_ADVISORY,
                                settings.get(
                                        "percolator.advisory.percolator.3-07-1-predates-i-spline"
                                                + "-pep-regressor")),
                () ->
                        assertEquals(
                                List.of(percolatorExecutable),
                                manifest.tools().stream()
                                        .filter(tool -> tool.name().equals("percolator"))
                                        .map(ToolRecord::executablePath)
                                        .toList()));
    }

    private static List<LaunchRecorder.Launch> percolatorLaunches() {
        return launches.launches().stream()
                .filter(launch -> launch.argv().get(0).equals(percolatorExecutable.toString()))
                .toList();
    }

    /** The project's runs, oldest first; the one at a position. */
    private static Path onlyRun(int position) {
        try (Stream<Path> runs = Files.list(project.resolve("runs"))) {
            List<Path> sorted = runs.sorted().toList();
            assertEquals(position + 1, sorted.size(), () -> "the runs: " + sorted);
            return sorted.get(position);
        } catch (IOException unreadable) {
            throw new AssertionError("the project's runs cannot be listed", unreadable);
        }
    }

    /** A run's identifier: its directory's name after the creation time and the hyphen. */
    private static String idOf(Path run) {
        String name = String.valueOf(run.getFileName());
        return name.substring(name.indexOf('-') + 1);
    }

    private static List<LaunchRecorder.Launch> cometLaunches() {
        return launches.launches().stream()
                .filter(launch -> launch.argv().get(0).equals(comet.toString()))
                .toList();
    }

    private static void assertNoCometAlive() {
        for (LaunchRecorder.Launch launch : launches.launches()) {
            long pid = launch.process().pid();
            assertFalse(
                    ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false),
                    () -> "Comet pid " + pid + " is still running");
        }
    }
}
