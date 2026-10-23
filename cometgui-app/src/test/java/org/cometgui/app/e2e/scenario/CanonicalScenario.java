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

package org.cometgui.app.e2e.scenario;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.cometgui.app.e2e.oracle.CometParamsFile;
import org.cometgui.app.e2e.oracle.DirectorySnapshot;
import org.cometgui.app.e2e.oracle.PercolatorWeights;
import org.cometgui.app.e2e.oracle.PinFile;
import org.cometgui.app.e2e.oracle.QValueCounts;
import org.cometgui.app.e2e.scenario.ShippedManifest.Artefact;
import org.cometgui.app.e2e.scenario.ShippedManifest.Claim;
import org.cometgui.app.testing.OwnDigests;
import org.cometgui.app.testing.TestJson;
import org.cometgui.app.uidriver.Await;
import org.cometgui.app.uidriver.RemotableUiDriver;

/**
 * The canonical end-to-end scenario of the specification ("Canonical E2E scenario"), written once
 * and tier-neutral (design decision P14-3): every step drives the interface through {@link
 * RemotableUiDriver} alone, answers file dialogs through {@link ChooserScript}, and proves what the
 * application claims with the test's own oracles reading the files on disk (P14-7). Nothing here
 * touches a node, a scene, the JavaFX thread or a product class, so Tier A runs it in-process and
 * Tier B (unit 7) runs it against a packaged application.
 *
 * <h2>How a tier uses it</h2>
 *
 * <ol>
 *   <li>Construct it over a {@link ScenarioLayout} (every path a parameter), a {@link
 *       ChooserScript} and an {@link OracleProcesses}.
 *   <li>Call {@link #freshHomeHoldsNoTool()} before the application starts (steps 1 and 2).
 *   <li>Start the application, then {@link #attach(RemotableUiDriver)} a driver to it.
 *   <li>Call {@link #applicationIsReady()}, then {@link
 *       #installCometAndPercolatorFromTheToolManager()} -- in the same application, which
 *       configures and runs the search afterwards with no relaunch in between.
 *   <li>Call the remaining step methods in their numbered order; each asserts values, never "did
 *       not throw", and each builds on the state the one before left -- the run directory, the
 *       counts.
 * </ol>
 *
 * <p>Steps 1 to 24 were written by Phase 14 unit 2; unit 3 added 25 to 30 ({@link
 * DownstreamSteps}): PDV, the converter, the upload -- which takes a {@link LimelightEndpoint} the
 * tier supplies -- provenance, and the relaunch, for which a tier calls {@link
 * #recordWhatIsShownBeforeTheClose()}, stops the application through its own stop, starts a new one
 * over a new composition root from the same home, {@link #attach}es its driver and calls {@link
 * #theRelaunchedApplicationIsCoherent()}. A tier must give the application a display PDV can reach
 * (step 25) and a loopback endpoint and test key (step 27).
 *
 * <h2>Where the product diverges from the specification's wording</h2>
 *
 * <ul>
 *   <li><strong>Step 5</strong> (P14-6): the product has no project chooser. Its project is the
 *       default one under the application data directory, created by the first pre-run check that
 *       finds a Comet -- the check the Run section makes again when the Comet install finishes. So
 *       the scenario asserts it absent when the application is ready and present, with no run in
 *       it, once the installs have reached the Run section and before Run is pressed, at the place
 *       {@link ScenarioLayout#projectDirectory()} says.
 *   <li><strong>Step 15</strong> (P14-5, escalated as E1): Run does not install Comet or
 *       Percolator. They are installed through the Tool Manager section's own Install controls,
 *       from an empty cache, right after the application is ready -- and the download, checksum and
 *       probe states are asserted there. They come before the configuration rather than at step
 *       15's place because steps 8 and 9 read the selected builds, which only installed builds can
 *       be. The rest of the scenario runs in the same application: the Run and Percolator sections
 *       must learn of the installs as they finish, which the scenario asserts on screen. (Phase 14
 *       unit 2 found that they did not -- defect E3, a listener held only weakly -- and worked
 *       around it with a relaunch; unit 2b repaired the product and removed the relaunch, and
 *       {@code InstallReachesTheSectionsUiTest} is the defect's own test.)
 *   <li><strong>Step 10</strong> (unit 3, escalated as E5): besides the precursor window the
 *       scenario sets Comet's results per spectrum to {@value #RESULTS_PER_SPECTRUM} in the
 *       Advanced level, because with Comet's default top 5 the pinned converter refuses this
 *       fixture's run (see {@link #RESULTS_PER_SPECTRUM}).
 *   <li><strong>Step 30</strong>: nothing diverges. The product keeps a run's q filters in the
 *       run's {@code results/view-state.json}, so the relaunched Results section shows 0.005 and
 *       0.02 again, and the counts under them; the scenario asserts the file and the screen.
 * </ul>
 *
 * <h2>Designed to go red</h2>
 *
 * <p>The specification requires this test to fail if a GUI control stops affecting the parameter
 * file (step 17 compares every parameter of the written file with Comet's own defaults plus the
 * changes made on screen), a downstream stage is bypassed (step 20 requires Percolator's execution
 * record, its exit, its XML and tables on disk, and step 21 the counts on screen equal to counts of
 * that very file; steps 25 to 27 require the figure, the converted file and the endpoint's own
 * record of the upload, each read by the test), filtering uses {@code <} instead of {@code <=}
 * (steps 21 to 23 also type a cutoff equal to a q-value the file holds, so the two comparisons
 * disagree), an output is omitted from provenance (step 28 requires every file under {@code
 * outputs/} declared as an output or named by its own event), or Comet writes beside its inputs
 * (step 18 compares the inputs' directory with its snapshot).
 */
public final class CanonicalScenario {

    /** How long the Tool Manager may take to read its list after the window shows. */
    public static final Duration READY_BOUND = Duration.ofMinutes(2);

    /**
     * How long one managed install -- the download from GitHub, the checks, the probe -- may take.
     */
    public static final Duration INSTALL_BOUND = Duration.ofMinutes(15);

    /** How long a pre-run check may take: it reads the whole proteome and hashes the inputs. */
    public static final Duration CHECK_BOUND = Duration.ofMinutes(10);

    /**
     * How long the real search of the whole proteome and Percolator may take. A bound, not a delay.
     */
    public static final Duration RUN_BOUND = Duration.ofMinutes(180);

    /**
     * What the Run section's engine says once Comet and Percolator are installed and nothing else
     * is chosen yet: neither tool is named any more, only the inputs -- no spectrum file, and the
     * database Comet's own defaults name.
     */
    static final String RUN_AFTER_THE_INSTALLS =
            "The workflow engine cannot start this search:\n"
                    + "there is no spectrum file to search\n"
                    + "the database /some/path/db.fasta (database_name) does not exist or cannot be"
                    + " read";

    /** How long one query of the Results section may take. */
    public static final Duration QUERY_BOUND = Duration.ofMinutes(2);

    /** How long the oracle's own {@code comet -q} may take. */
    public static final Duration ORACLE_BOUND = Duration.ofMinutes(2);

    /** The release the specification names as the default (revision 12 onwards; D-010). */
    public static final String DEFAULT_COMET = "2026.03.0";

    /** The precursor window the scenario types in place of the starting 20 ppm (step 10). */
    public static final String PRECURSOR_LOWER = "-10.0";

    /** The upper half of that window. */
    public static final String PRECURSOR_UPPER = "10.0";

    /** The variable modification the scenario edits: slot 1's oxidation, at most 2 per peptide. */
    public static final String EDITED_VARIABLE_MOD = "15.9949 M 0 2 -1 0 0 0.0";

    /**
     * How many results per spectrum the scenario has Comet report (step 10), in place of Comet's
     * default 5, set in the Advanced level.
     *
     * <p><strong>A workaround, escalated (Phase 14 unit 3, E5).</strong> With Comet's default top 5
     * on the {@code D-006} fixture and the whole proteome, Comet 2026.03.0 reports two hits of one
     * spectrum with the same modified peptide ({@code k562_3} scan 11483, charge 2, {@code
     * R.GGHM[15.9949]DDGGYSMNFNMSSSR.G}, ranks 1 and 3; {@code k562_4} scan 10245, charge 3), both
     * reach Percolator's XML, and the pinned converter v2.8.1 refuses the run ("Got more than one
     * entry for k562_3.pep.xml for the same scan to the same reported peptide"). The product runs
     * the converter faithfully and reports that refusal; it cannot convert such a run. Reporting
     * only the top hit removes the duplicate, so step 26 can prove the conversion; whether the
     * product should do this itself is the escalation's question.
     */
    public static final String RESULTS_PER_SPECTRUM = "1";

    /** The decoy source chosen: the FASTA holds no decoys, so Comet makes them (step 10). */
    public static final String CONCATENATED_DECOYS =
            "Concatenated: targets and decoys compete, one result per spectrum";

    private static final Pattern SUCCEEDED =
            Pattern.compile("The run succeeded: run (\\S+) in (.+)\\.");

    private final ScenarioLayout layout;

    private final ChooserScript chooser;

    private final OracleProcesses processes;

    private final ShippedManifest manifest;

    private final String platform;

    private final List<String> observations = new ArrayList<>();

    private Screen screen;

    private DirectorySnapshot inputsBefore;

    private Artefact comet;

    private Artefact percolator;

    private Path cometExecutable;

    private Path percolatorExecutable;

    private String percolatorVersion;

    private String runId;

    private Path run;

    private List<String> psmsAtOnePercent;

    private List<String> peptidesAtOnePercent;

    private final DownstreamSteps downstream = new DownstreamSteps(this);

    /**
     * A scenario over a layout.
     *
     * @param layout where everything is
     * @param chooser answers the file dialogs
     * @param processes runs the oracles' own tools
     * @throws IOException if the shipped manifest cannot be read
     */
    public CanonicalScenario(
            ScenarioLayout layout, ChooserScript chooser, OracleProcesses processes)
            throws IOException {
        this.layout = Objects.requireNonNull(layout, "layout");
        this.chooser = Objects.requireNonNull(chooser, "chooser");
        this.processes = Objects.requireNonNull(processes, "processes");
        this.manifest = ShippedManifest.read(layout.manifest());
        this.platform = ShippedManifest.hostPlatform();
    }

    /**
     * Drives an application from now on: the first one, or the relaunched one (step 30).
     *
     * @param driver the driver of the running application
     */
    public void attach(RemotableUiDriver driver) {
        this.screen = new Screen(driver);
    }

    /**
     * What the run measured -- byte counts, row counts, counts at each cutoff -- one line each, for
     * the report.
     *
     * @return the observations so far, in order
     */
    public List<String> observations() {
        return List.copyOf(observations);
    }

    /**
     * The run directory step 16 found, for the steps that follow (unit 3's among them).
     *
     * @return the run's directory
     */
    public Path runDirectory() {
        return Objects.requireNonNull(run, "no run has finished yet");
    }

    /**
     * The run's identifier, as the run section stated it.
     *
     * @return the identifier
     */
    public String runId() {
        return Objects.requireNonNull(runId, "no run has finished yet");
    }

    // ===================================================== for steps 25 to 30 ====

    Screen screen() {
        return Objects.requireNonNull(screen, "no application is attached");
    }

    ScenarioLayout layout() {
        return layout;
    }

    ChooserScript chooser() {
        return chooser;
    }

    ShippedManifest manifest() {
        return manifest;
    }

    String platform() {
        return platform;
    }

    void observe(String line) {
        observations.add(line);
    }

    Path cometExecutable() {
        return Objects.requireNonNull(cometExecutable, "Comet is not installed yet");
    }

    Path percolatorExecutable() {
        return Objects.requireNonNull(percolatorExecutable, "Percolator is not installed yet");
    }

    String percolatorVersion() {
        if (percolatorVersion != null) {
            return percolatorVersion;
        }
        return Objects.requireNonNull(percolator, "Percolator is not installed yet").version();
    }

    // ================================================================= steps 1 and 2 ====

    /**
     * Steps 1 and 2, before the application starts: the home is brand new and its application data
     * directory does not exist yet, and no Comet, Percolator, PDV or converter is anywhere under it
     * -- proved by walking the tree, never by asking the product.
     *
     * @throws IOException if the tree cannot be walked
     */
    public void freshHomeHoldsNoTool() throws IOException {
        List<String> walked = layout.home().assertNoToolPresent();
        assertAll(
                "step 1: a brand-new home",
                () ->
                        assertEquals(
                                List.of(".local", ".local/share", "AppData", "AppData/Roaming"),
                                walked.stream()
                                        .map(path -> path.replace('\\', '/'))
                                        .sorted()
                                        .toList(),
                                "the home holds only the directories the fresh home made"),
                () ->
                        assertFalse(
                                Files.exists(layout.home().applicationDataDirectory()),
                                "no application data directory yet"),
                () -> assertFalse(Files.exists(layout.toolCache()), "no tool cache yet"),
                () -> assertFalse(Files.exists(layout.projectDirectory()), "no project yet"));
    }

    // ================================================================= steps 3 and 4 ====

    /**
     * Steps 3 and 4, once the application shows its window: it is ready when the Tool Manager has
     * read its list -- every tool build the shipped manifest names, counted by the scenario's own
     * reader -- and starting it installed nothing and created no project.
     *
     * @throws IOException if the home cannot be walked
     */
    public void applicationIsReady() throws IOException {
        screen.show("tool-manager");
        String builds = manifest.toolBuilds() + " tool builds on this host.";
        screen.await(
                "tool-manager-summary",
                "the Tool Manager to read its list",
                builds::equals,
                READY_BOUND);
        layout.home().assertNoToolPresent();
        assertAll(
                "step 4: ready, and nothing done behind the user's back",
                () ->
                        assertEquals(
                                "Run state: Not started",
                                screen.ui().textOf("stage-stepper-run-state")),
                () ->
                        assertFalse(
                                Files.exists(layout.projectDirectory()),
                                "a window that cannot run anything creates no project (P14-6)"));
        observations.add("ready: " + builds);
    }

    // ============================================================ steps 5, 6 and 7 ====

    /**
     * Steps 5 to 7: the inputs' directory is snapshotted, the project is the product's default one
     * with no run in it yet (P14-6), and the two spectrum files and the FASTA are chosen through
     * the controls a user clicks -- Add spectrum files... and the database's Choose... -- with the
     * dialogs answered by the chooser script.
     *
     * @throws IOException if the inputs cannot be snapshotted
     */
    public void chooseSpectraAndDatabase() throws IOException {
        inputsBefore = DirectorySnapshot.of(layout.inputs());
        assertNoRunYet();
        screen.show("comet-parameters");
        screen.ui().clickOn("param-mode-essentials");
        assertEquals("No spectrum files chosen.", screen.ui().textOf("ess-spectra-summary"));

        int askedBefore = chooser.asked().size();
        chooser.nextSpectra(layout.spectra());
        screen.ui().clickOn("ess-spectra-add");
        chooser.nextDatabase(layout.database());
        screen.ui().clickOn("ess-database_name-choose");

        String database = layout.database().toString();
        List<String> asked = chooser.asked();
        assertAll(
                "steps 6 and 7: two spectrum files and the FASTA, chosen through the controls",
                () ->
                        assertEquals(
                                List.of("spectra", "database"),
                                asked.subList(askedBefore, asked.size()),
                                "the dialogs this step opened"),
                () ->
                        assertEquals(
                                "2 spectrum files chosen.",
                                screen.ui().textOf("ess-spectra-summary")),
                () -> assertSpectrumRow(0, layout.spectra().get(0)),
                () -> assertSpectrumRow(1, layout.spectra().get(1)),
                () -> assertFalse(screen.ui().exists("ess-spectrum-2"), "and no third"),
                () -> assertEquals(database, screen.ui().textOf("ess-database_name")),
                () ->
                        assertEquals(
                                "Value from: Set by you",
                                screen.ui().textOf("ess-database_name-origin")));
    }

    /**
     * Step 5 (P14-6): the product's project is its default one, created and locked by the first
     * pre-run check that finds a Comet -- here the check the Run section made again when the Comet
     * install finished -- in the fresh home's application data directory; it holds no run yet.
     * (Before any Comet was installed, {@link #applicationIsReady()} saw it absent.)
     */
    private void assertNoRunYet() throws IOException {
        Path runs = layout.projectDirectory().resolve("runs");
        List<Path> listed = List.of();
        if (Files.isDirectory(runs)) {
            try (Stream<Path> entries = Files.list(runs)) {
                listed = entries.toList();
            }
        }
        List<Path> found = listed;
        assertAll(
                "step 5: the default project, and no run in it yet",
                () ->
                        assertTrue(
                                Files.isRegularFile(
                                        layout.projectDirectory().resolve("project.json")),
                                () -> layout.projectDirectory() + " holds project.json"),
                () -> assertEquals(List.of(), found, "no run yet"));
    }

    private void assertSpectrumRow(int index, Path file) {
        String shown = screen.ui().textOf("ess-spectrum-" + index);
        assertTrue(
                shown.contains(file.toString()),
                () -> "#ess-spectrum-" + index + " names " + file + ": " + shown);
    }

    // ===================================================================== step 8 ====

    /**
     * Step 8: the release control shows the default Comet, 2026.03.0, and the shipped manifest has
     * a build of it for this platform -- the one the scenario installs in step 14.
     */
    public void defaultCometIsSelected() {
        assertEquals(DEFAULT_COMET, comet.version(), "the Comet installed is the default one");
        assertAll(
                "step 8: the default Comet",
                () ->
                        assertEquals(
                                "Comet " + DEFAULT_COMET + " (default)",
                                screen.ui().selectedTextOf("param-release")),
                () ->
                        assertEquals(
                                "Comet " + DEFAULT_COMET + " is selected.",
                                screen.ui().textOf("param-release-status")));
    }

    // ===================================================================== step 9 ====

    /**
     * Step 9: Limelight conversion is switched on, so the run needs Percolator XML, and the
     * Percolator section's resolved default is the newest Percolator whose shipped manifest record
     * for this platform claims {@code XML_OUTPUT} -- computed here by the scenario's own reader of
     * {@code manifests/tools.json} from the records' fields, so a default that was hard-coded
     * rather than resolved goes red the day the manifest changes.
     */
    public void defaultXmlCapablePercolatorIsSelected() {
        assertEquals(
                manifest.newestXmlCapablePercolator(platform).version(),
                percolator.version(),
                "the Percolator installed is the one the manifest makes the default");
        String version = percolator.version();
        screen.show("percolator");
        if (!screen.ui().isSelected("percolator-limelight")) {
            screen.ui().clickOn("percolator-limelight");
        }
        int known = manifest.versionsOf("percolator");
        assertAll(
                "step 9: the default XML-capable Percolator, resolved from the manifest",
                () -> assertTrue(screen.ui().isSelected("percolator-limelight")),
                () ->
                        assertEquals(
                                "Percolator "
                                        + version
                                        + " is the newest Percolator that can be used on this"
                                        + " computer.",
                                screen.ui().textOf("percolator-reason")),
                () ->
                        assertEquals(
                                "Percolator "
                                        + version
                                        + " runs in the next search: the resolved default.",
                                screen.ui().textOf("percolator-selection")),
                () ->
                        assertEquals(
                                "Percolator " + version + " -- the resolved default",
                                screen.ui().selectedTextOf("percolator-version")),
                () ->
                        assertTrue(
                                screen.ui()
                                        .textOf("percolator-offers")
                                        .startsWith(
                                                known
                                                        + " Percolator builds are known on this"
                                                        + " computer, and 1 of them"),
                                () -> screen.ui().textOf("percolator-offers")),
                () ->
                        assertTrue(
                                screen.ui()
                                        .textOf("percolator-badge")
                                        .startsWith("Percolator " + version + ": a managed build"),
                                () -> screen.ui().textOf("percolator-badge")));
        observations.add(
                "resolved Percolator: "
                        + version
                        + " (the newest "
                        + platform
                        + " record claiming XML_OUTPUT; "
                        + known
                        + " Percolator versions known)");
    }

    // ======================================================== steps 10, 11 and 12 ====

    /**
     * Steps 10 to 12: the precursor window changed from the starting 20 ppm to 10 ppm (both
     * halves), the decoy source changed to Comet's concatenated decoys (the FASTA has none), slot
     * 1's variable modification edited through the structured editor, and the parameter summary --
     * the Modified only listing of the parameter search, and each control's origin -- reflecting
     * exactly those changes.
     */
    public void changeParameters() {
        screen.show("comet-parameters");
        screen.ui().clickOn("param-mode-essentials");
        assertAll(
                "the starting values",
                () -> assertEquals("-20.0", screen.ui().textOf("ess-peptide_mass_tolerance_lower")),
                () -> assertEquals("20.0", screen.ui().textOf("ess-peptide_mass_tolerance_upper")),
                () ->
                        assertEquals(
                                "Serialised: variable_mod01 = 15.9949 M 0 3 -1 0 0 0.0",
                                screen.ui().textOf("ess-variable_mod01-serialised")));

        screen.enter("ess-peptide_mass_tolerance_lower", PRECURSOR_LOWER);
        screen.enter("ess-peptide_mass_tolerance_upper", PRECURSOR_UPPER);
        screen.ui().select("ess-decoy_search", CONCATENATED_DECOYS);
        screen.enter("ess-variable_mod01-part-maximum-count", "2");
        screen.ui().clickOn("param-mode-advanced");
        if (!screen.ui().isSelected("adv-category-output-toggle")) {
            screen.ui().clickOn("adv-category-output-toggle");
        }
        assertEquals("5", screen.ui().textOf("adv-num_output_lines"), "Comet's default top 5");
        screen.enter("adv-num_output_lines", RESULTS_PER_SPECTRUM);
        String perSpectrumOrigin = screen.ui().textOf("adv-num_output_lines-origin");
        screen.ui().clickOn("param-mode-essentials");

        assertAll(
                "steps 10 and 11: the controls took the changes",
                () ->
                        assertEquals(
                                "Value from: Set by you",
                                screen.ui().textOf("ess-peptide_mass_tolerance_lower-origin")),
                () ->
                        assertEquals(
                                "Value from: Set by you",
                                screen.ui().textOf("ess-peptide_mass_tolerance_upper-origin")),
                () ->
                        assertEquals(
                                "Value from: Set by you",
                                screen.ui().textOf("ess-decoy_search-origin")),
                () ->
                        assertEquals(
                                "Serialised: variable_mod01 = " + EDITED_VARIABLE_MOD,
                                screen.ui().textOf("ess-variable_mod01-serialised")),
                () -> assertEquals("Value from: Set by you", perSpectrumOrigin),
                () ->
                        assertEquals(
                                "Validation: No errors or warnings.",
                                screen.ui().textOf("param-summary-headline")));

        screen.ui().clickOn("param-search-filter-modified");
        List<String> modified = screen.numbered("param-search-result-");
        screen.ui().clickOn("param-search-filter-modified");
        assertEquals(
                List.of(
                        "Sequence database (FASTA) (database_name) -- Listed by the filters",
                        "Internal decoy search (decoy_search) -- Listed by the filters",
                        "Precursor tolerance, upper bound (peptide_mass_tolerance_upper) --"
                                + " Listed by the filters",
                        "Precursor tolerance, lower bound (peptide_mass_tolerance_lower) --"
                                + " Listed by the filters",
                        "Variable modification 1 (variable_mod01) -- Listed by the filters",
                        "Write Percolator input (PIN) (output_percolatorfile) -- Listed by the"
                                + " filters",
                        "Results reported per spectrum (num_output_lines) -- Listed by the"
                                + " filters"),
                modified,
                "step 12: the parameter summary lists exactly the changed parameters");
    }

    // ==================================================================== step 13 ====

    /**
     * Step 13: both q-value filters are 0.01, in the Percolator section and in the Results section,
     * which share one filter state.
     */
    public void bothFiltersAreOnePercent() {
        screen.show("percolator");
        String psm = screen.ui().textOf("percolator-psm-filter");
        String peptide = screen.ui().textOf("percolator-peptide-filter");
        screen.show("results");
        assertAll(
                "step 13: both default q filters are 0.01",
                () -> assertEquals("0.01", psm, "the Percolator section's PSM filter"),
                () -> assertEquals("0.01", peptide, "the Percolator section's peptide filter"),
                () -> assertEquals("0.01", screen.ui().textOf("results-psm-filter")),
                () -> assertEquals("0.01", screen.ui().textOf("results-peptide-filter")));
    }

    // =========================================================== steps 14 and 15 ====

    /**
     * Steps 14 and 15, as the product does them (P14-5): from the empty cache, the default Comet
     * and the resolved Percolator are installed through the Tool Manager's own Install controls.
     * Each row goes from not installed to installed; the transfer is the manifest's byte count;
     * every capability the manifest claims is observed by execution (the probe); and the installed
     * executable's own SHA-256 is the manifest's (the checksum) -- computed here, not taken from
     * the product.
     *
     * @throws IOException if an installed file cannot be read
     */
    public void installCometAndPercolatorFromTheToolManager() throws IOException {
        percolator = manifest.newestXmlCapablePercolator(platform);
        installCometFromTheToolManager();
        percolatorExecutable = install(percolator);
        for (ShippedManifest.Companion companion : percolator.companions()) {
            Path installRoot =
                    Objects.requireNonNull(
                            Objects.requireNonNull(
                                            percolatorExecutable.getParent(), "bin directory")
                                    .getParent(),
                            "install root");
            for (Map.Entry<String, String> member : companion.members().entrySet()) {
                Path file = installRoot.resolve(member.getKey());
                assertEquals(
                        member.getValue(),
                        OwnDigests.of(file).sha256(),
                        () -> "the companion member " + file + " is the manifest's bytes");
            }
        }
        theRunSectionKnowsBothTools();
    }

    /**
     * Step 14's Comet half (P14-5), alone: from a cache with no tool in it, the default Comet is
     * installed through the Tool Manager's Install control and checked as {@link
     * #installCometAndPercolatorFromTheToolManager()} checks it. The no-XML scenario uses it, and
     * gives the run its Percolator another way ({@link #percolatorIs}).
     *
     * @return the installed Comet executable
     * @throws IOException if an installed file cannot be read
     */
    Path installCometFromTheToolManager() throws IOException {
        comet = manifest.artefact("comet", DEFAULT_COMET, platform);
        layout.home().assertNoToolPresent();
        screen.show("tool-manager");
        cometExecutable = install(comet);
        return cometExecutable;
    }

    /**
     * The Percolator the steps that follow hold the run to, where it is not the managed build
     * {@link #installCometAndPercolatorFromTheToolManager()} installs: a registered local binary,
     * or the build a compatible rerun installed.
     *
     * @param version the version, as the binary states it
     * @param executable the executable the run's record must name
     */
    void percolatorIs(String version, Path executable) {
        percolatorVersion = Objects.requireNonNull(version, "version");
        percolatorExecutable = Objects.requireNonNull(executable, "executable");
    }

    /**
     * The run the steps that follow work on, where it is not the one {@link #run()} started: the
     * derived run of a compatible Percolator rerun.
     *
     * @param id the run's identifier, as the screen stated it
     * @param directory the run's directory
     */
    void runIs(String id, Path directory) {
        runId = Objects.requireNonNull(id, "id");
        run = Objects.requireNonNull(directory, "directory");
    }

    /**
     * Once Comet and a Percolator are both usable: the Run section has learned of them -- its
     * engine names neither tool, only the missing inputs (defect E3) -- and the default project
     * holds no run yet.
     *
     * @throws IOException if the project cannot be listed
     */
    void theRunSectionKnowsBothTools() throws IOException {
        screen.show("run");
        screen.await(
                "run-engine",
                "the Run section to learn of both installs (defect E3)",
                RUN_AFTER_THE_INSTALLS::equals,
                CHECK_BOUND);
        assertNoRunYet();
    }

    private static String lastOf(Set<String> shown) {
        return shown.isEmpty() ? "(none observed)" : List.copyOf(shown).get(shown.size() - 1);
    }

    static String rowOf(Artefact artefact) {
        return "tool-row-" + artefact.tool() + "-" + artefact.version().replace('.', '_') + "-1";
    }

    private Path install(Artefact artefact) throws IOException {
        String row = rowOf(artefact);
        String name = artefact.tool() + " " + artefact.version();
        String download =
                "Download: "
                        + String.format(Locale.ROOT, "%,d", artefact.transferBytes())
                        + " bytes";
        assertAll(
                "the " + name + " row before the install",
                () -> assertEquals(name, screen.ui().textOf(row + "-name")),
                () ->
                        assertEquals(
                                "CometGUI-managed: not installed",
                                screen.ui().textOf(row + "-state")),
                () -> assertEquals(download, screen.ui().textOf(row + "-download")),
                () -> assertFalse(screen.ui().isDisabled(row + "-install"), "Install is offered"));

        Set<String> progress = new LinkedHashSet<>();
        long started = System.nanoTime();
        screen.ui().clickOn(row + "-install");
        String state =
                Await.until(
                        name + " to finish installing (" + row + ")",
                        () -> {
                            if (screen.ui().exists(row + "-progress")) {
                                String shown = screen.ui().textOf(row + "-progress");
                                if (!shown.isEmpty()) {
                                    progress.add(shown);
                                }
                            }
                            return screen.ui().textOf(row + "-state");
                        },
                        shown ->
                                shown.equals("CometGUI-managed: installed")
                                        || shown.contains("did not succeed"),
                        INSTALL_BOUND);
        long seconds = (System.nanoTime() - started) / 1_000_000_000L;
        String path = screen.ui().textOf(row + "-path");
        List<String> phases =
                progress.stream().map(text -> text.split(":", 2)[0]).distinct().toList();
        List<String> capabilities = new ArrayList<>();
        for (Claim claim : artefact.claims()) {
            capabilities.add(claim.capability() + " (observed-by-execution)");
        }
        String shownCapabilities = screen.ui().textOf(row + "-capabilities");
        assertAll(
                "step 15: " + name + " downloaded, checked and probed (" + progress + ")",
                () -> assertEquals("CometGUI-managed: installed", state),
                () ->
                        assertEquals(
                                "Done",
                                phases.isEmpty() ? "" : phases.get(phases.size() - 1),
                                "the last phase shown"),
                () ->
                        assertTrue(
                                lastOf(progress).startsWith("Done: "),
                                () -> "the install's last report is Done: " + progress),
                () ->
                        assertTrue(
                                path.startsWith("Installed at: " + layout.toolCache() + "/"),
                                () -> "installed in the fresh home's tool cache: " + path),
                () ->
                        assertTrue(
                                capabilities.stream()
                                        .allMatch(claim -> shownCapabilities.contains(claim)),
                                () ->
                                        "every capability the manifest claims was observed by"
                                                + " running the binary: "
                                                + shownCapabilities));
        Path executable = Path.of(path.substring("Installed at: ".length()));
        assertEquals(
                artefact.installedSha256(),
                OwnDigests.of(executable).sha256(),
                () -> "the checksum: " + executable + " holds the bytes the manifest pins");
        observations.add(
                "installed "
                        + name
                        + " from "
                        + artefact.url()
                        + ": "
                        + artefact.transferBytes()
                        + " bytes in "
                        + seconds
                        + " s; phases shown "
                        + phases
                        + ", last \""
                        + lastOf(progress)
                        + "\"; "
                        + executable);
        return executable;
    }

    // =========================================================== steps 14 and 16 ====

    /**
     * Steps 14 and 16: the pre-run check says the engine can run the search -- and in doing so
     * creates the default project (step 5's other half) -- Run is pressed, and the scenario waits
     * on the run's stated outcome alone, which must be a success naming the run.
     *
     * @throws IOException if the project cannot be listed
     */
    public void run() throws IOException {
        screen.show("run");
        screen.await(
                "run-engine",
                "the pre-run check to say the search can run",
                "The workflow engine can run this search."::equals,
                CHECK_BOUND);
        assertAll(
                "the default project, in the fresh home, and Run offered",
                () ->
                        assertTrue(
                                Files.isRegularFile(
                                        layout.projectDirectory().resolve("project.json")),
                                () -> layout.projectDirectory() + " holds project.json"),
                () -> assertFalse(screen.ui().isDisabled("run-start"), "Run is offered"));

        long started = System.nanoTime();
        screen.ui().clickOn("run-start");
        String outcome =
                screen.await(
                        "run-outcome",
                        "the run to end",
                        text -> text.startsWith("The run "),
                        RUN_BOUND);
        long seconds = (System.nanoTime() - started) / 1_000_000_000L;
        Matcher succeeded = SUCCEEDED.matcher(outcome);
        assertTrue(succeeded.matches(), () -> "step 16: the run succeeded: " + outcome);
        runId = succeeded.group(1);
        run = Path.of(succeeded.group(2));
        List<Path> runs;
        try (Stream<Path> listed = Files.list(layout.projectDirectory().resolve("runs"))) {
            runs = listed.toList();
        }
        assertAll(
                "step 16: the run, as the stepper and the project show it",
                () ->
                        assertEquals(
                                List.of(run), runs, "the one run in the project is the stated one"),
                () -> assertEquals("Succeeded", screen.ui().textOf("stage-inputs-state")),
                () -> assertEquals("Succeeded", screen.ui().textOf("stage-validate-state")),
                () -> assertEquals("Succeeded", screen.ui().textOf("stage-comet-state")),
                () -> assertEquals("Succeeded", screen.ui().textOf("stage-percolator-state")),
                () -> assertTrue(screen.ui().isDisabled("run-cancel"), "nothing to cancel"));
        observations.add("run " + runId + " succeeded in " + seconds + " s: " + run);
    }

    // ==================================================================== step 17 ====

    /**
     * Step 17: the run's {@code comet.params} holds exactly the values chosen on screen, and the
     * two outputs forced on. "Exactly" is every parameter: the expectation is Comet's own complete
     * default file for this release ({@code comet -q}, run here on the installed binary), with the
     * changes made on screen and CometGUI's documented starting values applied -- built without the
     * product's writer -- and the written file must equal it name by name, value by value, with the
     * same enzyme table, no name given twice and no line Comet would not read.
     *
     * @throws IOException if a file cannot be read or the oracle's Comet cannot be started
     */
    public void cometParamsHoldExactlyTheChosenValues() throws IOException {
        Path oracle = Files.createDirectories(layout.work().resolve("comet-q"));
        int exit = processes.run(List.of(cometExecutable.toString(), "-q"), oracle, ORACLE_BOUND);
        assertEquals(0, exit, "comet -q, the oracle's own run of the installed Comet");
        CometParamsFile defaults = CometParamsFile.read(oracle.resolve("comet.params.new"));
        CometParamsFile written = CometParamsFile.read(run.resolve("parameters/comet.params"));

        Map<String, String> expected = new LinkedHashMap<>(defaults.values());
        expected.putAll(COMETGUI_STARTING_VALUES);
        expected.put("database_name", layout.database().toString());
        expected.put("peptide_mass_tolerance_lower", PRECURSOR_LOWER);
        expected.put("peptide_mass_tolerance_upper", PRECURSOR_UPPER);
        expected.put("decoy_search", "1");
        expected.put("variable_mod01", EDITED_VARIABLE_MOD);
        expected.put("num_output_lines", RESULTS_PER_SPECTRUM);
        expected.put("output_pepxmlfile", "1");
        expected.put("output_percolatorfile", "1");

        List<String> differences = new ArrayList<>();
        Set<String> names = new TreeSet<>(expected.keySet());
        names.addAll(written.values().keySet());
        for (String name : names) {
            String want = expected.get(name);
            String got = written.values().get(name);
            if (!Objects.equals(want, got)) {
                differences.add(name + ": expected " + want + ", written " + got);
            }
        }
        assertAll(
                "step 17: comet.params holds exactly the chosen values",
                () -> assertEquals(List.of(), differences, "every parameter, by name"),
                () -> assertEquals(defaults.enzymes(), written.enzymes(), "the enzyme table"),
                () -> assertEquals(Map.of(), written.duplicates(), "no parameter given twice"),
                () -> assertEquals(List.of(), written.unparsed(), "no line Comet would not read"),
                () -> assertEquals("1", written.value("output_pepxmlfile")),
                () -> assertEquals("1", written.value("output_percolatorfile")));
        observations.add(
                "comet.params: "
                        + written.values().size()
                        + " parameters, all equal to comet -q's "
                        + defaults.values().size()
                        + " defaults with "
                        + COMETGUI_STARTING_VALUES.size()
                        + " CometGUI starting value, the 6 on-screen changes and the 2 forced"
                        + " outputs applied");
    }

    /**
     * CometGUI's documented starting values where they differ from {@code comet -q}'s, each with
     * its reason. Typed out, so a starting value that changes is a red test rather than a silent
     * drift.
     */
    static final Map<String, String> COMETGUI_STARTING_VALUES =
            Map.of(
                    // D-012: comet -q names the placeholder /some/path/speclib.file, which exists
                    // nowhere and makes Comet fail; a new CometGUI configuration searches without
                    // a spectral library, so the value is empty.
                    "spectral_library_name", "");

    // ==================================================================== step 18 ====

    /**
     * Step 18: Comet ran once per spectrum file with its own {@code -N} base -- from the run's own
     * execution records and from the files that exist -- nothing was written beside the input
     * spectra, and both pepXML and both PIN files parse.
     *
     * @throws IOException if a file cannot be read
     */
    public void cometRanOncePerFileAndWroteNothingBesideTheInputs() throws IOException {
        DirectorySnapshot.of(layout.inputs())
                .assertUnchangedIn(inputsBefore, "no file was written next to the input spectra");
        List<Map<String, Object>> comets = toolsNamed("comet");
        assertEquals(2, comets.size(), "step 18: two spectrum files, two Comet invocations");
        Set<String> bases = new TreeSet<>();
        Set<String> searched = new TreeSet<>();
        for (Map<String, Object> invocation : comets) {
            Map<String, Object> execution = object(invocation.get("execution"));
            List<String> argv = strings(execution.get("argv"));
            List<String> inputs =
                    argv.stream()
                            .filter(
                                    argument ->
                                            layout.spectra().stream()
                                                    .anyMatch(s -> s.toString().equals(argument)))
                            .toList();
            List<String> named =
                    argv.stream().filter(argument -> argument.startsWith("-N")).toList();
            assertAll(
                    "the Comet invocation " + argv,
                    () ->
                            assertEquals(
                                    cometExecutable.toString(), argv.get(0), "the installed Comet"),
                    () -> assertEquals(1, inputs.size(), "exactly one spectrum file"),
                    () -> assertEquals(1, named.size(), "exactly one -N"),
                    () -> assertEquals(new BigDecimal(0), execution.get("exitCode"), "exit 0"));
            searched.add(inputs.get(0));
            bases.add(named.get(0).substring(2));
        }
        List<String> expectedBases = new ArrayList<>();
        for (Path spectrum : layout.spectra()) {
            expectedBases.add(run.resolve("outputs/comet").resolve(stem(spectrum)).toString());
        }
        assertAll(
                "step 18: once per file, each with its own -N base",
                () ->
                        assertEquals(
                                new TreeSet<>(
                                        layout.spectra().stream().map(Path::toString).toList()),
                                searched,
                                "every spectrum file searched once"),
                () -> assertEquals(new TreeSet<>(expectedBases), bases, "two distinct -N bases"));

        Set<String> written = new TreeSet<>();
        try (Stream<Path> listed = Files.list(run.resolve("outputs/comet"))) {
            listed.map(
                            path ->
                                    Objects.requireNonNull(path.getFileName(), "a listed name")
                                            .toString())
                    .forEach(written::add);
        }
        Set<String> outputs = new TreeSet<>();
        for (Path spectrum : layout.spectra()) {
            outputs.add(stem(spectrum) + ".pep.xml");
            outputs.add(stem(spectrum) + ".pin");
        }
        assertTrue(
                written.containsAll(outputs),
                () -> "each -N base's pepXML and PIN exist: " + written);
        for (Path spectrum : layout.spectra()) {
            String base = stem(spectrum);
            String nBase = run.resolve("outputs/comet").resolve(base).toString();
            XmlCensus pepxml =
                    XmlCensus.of(run.resolve("outputs/comet/" + base + ".pep.xml"), "base_name");
            PinFile pin = PinFile.read(run.resolve("outputs/comet/" + base + ".pin"));
            assertAll(
                    "the outputs of " + base,
                    () ->
                            assertEquals(
                                    "msms_pipeline_analysis", pepxml.root(), "a pepXML document"),
                    () -> assertTrue(pepxml.count("spectrum_query") > 0, "with spectrum queries"),
                    () ->
                            assertEquals(
                                    Map.of(
                                            "search_summary=" + nBase,
                                            1L,
                                            "msms_run_summary=" + layout.inputs().resolve(base),
                                            1L),
                                    pepxml.attributeValues(),
                                    "its search summary names its own -N base, and its run"
                                            + " summary the spectrum file it searched"),
                    () -> assertEquals(List.of("SpecId", "Label"), pin.header().subList(0, 2)),
                    () -> assertEquals(1, pin.headerLines(), "one header"),
                    () -> assertTrue(pin.rows() > 0, "data rows"),
                    () -> assertEquals(List.of(), pin.malformed()),
                    () ->
                            assertEquals(
                                    Set.of(nBase),
                                    pin.rowsBySource().keySet(),
                                    "every row's SpecId begins with its own -N base"));
            observations.add(
                    base
                            + ": "
                            + pepxml.count("spectrum_query")
                            + " pepXML spectrum queries, "
                            + pin.rows()
                            + " PIN rows ("
                            + pin.labels()
                            + ")");
        }
    }

    // ==================================================================== step 19 ====

    /**
     * Step 19: the merged PIN Percolator read has one header, the sources' header, and exactly the
     * sources' rows -- each source's count, and their sum.
     *
     * @throws IOException if a file cannot be read
     */
    public void mergedPinHasOneHeaderAndEveryRow() throws IOException {
        PinFile merged = PinFile.read(run.resolve("inputs/pin/merged.pin"));
        Map<String, Long> bySource = new TreeMap<>();
        long sum = 0;
        List<String> header = null;
        for (Path spectrum : layout.spectra()) {
            PinFile source = PinFile.read(run.resolve("outputs/comet/" + stem(spectrum) + ".pin"));
            bySource.put(
                    run.resolve("outputs/comet").resolve(stem(spectrum)).toString(), source.rows());
            sum += source.rows();
            header = source.header();
        }
        long total = sum;
        List<String> sourceHeader = header;
        assertAll(
                "step 19: the merged PIN",
                () -> assertEquals(1, merged.headerLines(), "one header"),
                () -> assertEquals(sourceHeader, merged.header(), "the sources' header"),
                () -> assertEquals(total, merged.rows(), "the summed row count of its sources"),
                () -> assertEquals(bySource, new TreeMap<>(merged.rowsBySource()), "row by source"),
                () -> assertEquals(List.of(), merged.malformed()));
        observations.add("merged PIN: " + merged.rows() + " rows = " + bySource);
    }

    // ==================================================================== step 20 ====

    /**
     * Step 20: Percolator ran once on the merged PIN, asked for XML, exited 0 -- by its execution
     * record -- and its PSM, peptide, weights and XML artefacts parse, the XML holding a psm and a
     * peptide element for each row of the two target tables (Phase 00's real 3.07.1 output has 3897
     * and 2985 of each, the tables' row counts).
     *
     * @throws IOException if a file cannot be read
     */
    public void percolatorRanAndItsArtefactsParse() throws IOException {
        List<Map<String, Object>> percolators = toolsNamed("percolator");
        assertEquals(1, percolators.size(), "step 20: one Percolator execution");
        Map<String, Object> execution = object(percolators.get(0).get("execution"));
        List<String> argv = strings(execution.get("argv"));
        Path merged = run.resolve("inputs/pin/merged.pin");
        Path out = run.resolve("outputs/percolator");
        QValueCounts psms = QValueCounts.of(out.resolve("psms.tsv"), "1");
        QValueCounts peptides = QValueCounts.of(out.resolve("peptides.tsv"), "1");
        PercolatorWeights weights = PercolatorWeights.read(out.resolve("weights.txt"));
        XmlCensus xml = XmlCensus.of(out.resolve("pout.xml"), "decoy");
        assertAll(
                "step 20: Percolator exited 0 and its artefacts parse",
                () -> assertEquals(percolatorExecutable.toString(), argv.get(0)),
                () -> assertEquals(merged.toString(), argv.get(argv.size() - 1), "the merged PIN"),
                () -> assertTrue(argv.contains("-X"), () -> "XML was asked for: " + argv),
                () -> assertEquals(new BigDecimal(0), execution.get("exitCode"), "exit 0"),
                () -> assertTrue(psms.total() > 0, "target PSMs"),
                () -> assertEquals(0, psms.unknown(), "every PSM q-value a number"),
                () -> assertTrue(peptides.total() > 0, "target peptides"),
                () -> assertEquals(0, peptides.unknown(), "every peptide q-value a number"),
                () -> assertEquals(3, weights.splits().size(), "three cross-validation splits"),
                () -> assertEquals("percolator_output", xml.root(), "a Percolator XML document"),
                () ->
                        assertEquals(
                                psms.total(), xml.count("psm"), "a psm element per target PSM row"),
                () ->
                        assertEquals(
                                peptides.total(),
                                xml.count("peptide"),
                                "a peptide element per target peptide row"),
                () ->
                        assertEquals(
                                Map.of(),
                                xml.attributeValues(),
                                "no decoy was asked for in the XML, so no element says p:decoy"));
        observations.add(
                "Percolator: "
                        + psms.total()
                        + " target PSMs, "
                        + peptides.total()
                        + " target peptides, "
                        + weights.names().size()
                        + " weight columns over "
                        + weights.splits().size()
                        + " splits; XML with "
                        + xml.count("psm")
                        + " psm and "
                        + xml.count("peptide")
                        + " peptide elements");
    }

    // ============================================================= steps 21 to 23 ====

    /**
     * Step 21: the Results section opens the run, and the PSM and peptide counts it shows at 0.01
     * equal the counts the scenario computes itself from Percolator's raw target files.
     *
     * @throws IOException if a file cannot be read
     */
    public void countsAtOnePercentMatchTheRawFiles() throws IOException {
        screen.show("results");
        screen.await(
                "results-readiness",
                "the Results section to open the run",
                text -> text.startsWith("Showing the results of run " + runId + ","),
                QUERY_BOUND);
        assertEquals("Target PSMs", screen.ui().selectedTextOf("results-table-choice"));
        settle("0.01");
        psmsAtOnePercent = shownCounts();
        assertEquals(oracle("psms.tsv", "0.01"), psmsAtOnePercent, "step 21: PSMs at q <= 0.01");
        showTable("Target peptides", "0.01");
        peptidesAtOnePercent = shownCounts();
        assertEquals(
                oracle("peptides.tsv", "0.01"),
                peptidesAtOnePercent,
                "step 21: peptides at q <= 0.01");
        observations.add(
                "at 0.01: PSMs "
                        + psmsAtOnePercent
                        + ", peptides "
                        + peptidesAtOnePercent
                        + " (total, passing, failing, unknown)");
    }

    /**
     * Step 22: the PSM filter changed to 0.005 in the Results section; the PSM counts become the
     * independent counts at 0.005, which differ from those at 0.01, and the peptide counts and
     * filter do not change.
     *
     * @throws IOException if a file cannot be read
     */
    public void psmFilterAtHalfAPercentChangesOnlyThePsms() throws IOException {
        showTable("Target PSMs", "0.01");
        screen.enter("results-psm-filter", "0.005");
        settle("0.005");
        List<String> psms = shownCounts();
        showTable("Target peptides", "0.01");
        List<String> peptides = shownCounts();
        assertAll(
                "step 22: only the PSM count changes",
                () -> assertEquals(oracle("psms.tsv", "0.005"), psms, "PSMs at q <= 0.005"),
                () -> assertNotEquals(psmsAtOnePercent.get(1), psms.get(1), "the PSM count moved"),
                () -> assertEquals(peptidesAtOnePercent, peptides, "the peptide counts did not"),
                () -> assertEquals("0.01", screen.ui().textOf("results-peptide-filter")));
        observations.add("at PSM 0.005: PSMs " + psms + ", peptides " + peptides);
    }

    /**
     * Step 23: the peptide filter changed to 0.02; the peptide counts become the independent counts
     * at 0.02, which differ from those at 0.01, and the PSM filter stays 0.005.
     *
     * @throws IOException if a file cannot be read
     */
    public void peptideFilterAtTwoPercent() throws IOException {
        showTable("Target peptides", "0.01");
        screen.enter("results-peptide-filter", "0.02");
        settle("0.02");
        List<String> peptides = shownCounts();
        showTable("Target PSMs", "0.005");
        List<String> psms = shownCounts();
        assertAll(
                "step 23: the peptide count at 0.02",
                () -> assertEquals(oracle("peptides.tsv", "0.02"), peptides, "peptides at 0.02"),
                () ->
                        assertNotEquals(
                                peptidesAtOnePercent.get(1), peptides.get(1), "the count moved"),
                () -> assertEquals(oracle("psms.tsv", "0.005"), psms, "the PSMs unchanged"),
                () -> assertEquals("0.005", screen.ui().textOf("results-psm-filter")));
        observations.add("at peptide 0.02: peptides " + peptides + ", PSMs " + psms);
    }

    /**
     * Steps 21 to 23's inclusive comparison, made falsifiable: each filter typed as a q-value the
     * raw file really holds (the largest at most 0.01), so that the rows equal to the cutoff --
     * counted here, at least one -- pass under {@code <=} and would fail under {@code <}. The
     * counts shown must be the inclusive ones. The filters are then put back to 0.005 and 0.02, as
     * step 23 left them.
     *
     * @throws IOException if a file cannot be read
     */
    public void aCutoffEqualToAQValuePasses() throws IOException {
        QValueBoundary psm =
                QValueBoundary.atMost(run.resolve("outputs/percolator/psms.tsv"), "0.01");
        QValueBoundary peptide =
                QValueBoundary.atMost(run.resolve("outputs/percolator/peptides.tsv"), "0.01");
        showTable("Target PSMs", "0.005");
        screen.enter("results-psm-filter", psm.text());
        settle(psm.text());
        List<String> psms = shownCounts();
        showTable("Target peptides", "0.02");
        screen.enter("results-peptide-filter", peptide.text());
        settle(peptide.text());
        List<String> peptides = shownCounts();
        List<String> psmOracle = oracle("psms.tsv", psm.text());
        List<String> peptideOracle = oracle("peptides.tsv", peptide.text());
        assertAll(
                "a cutoff equal to a q-value passes (q <= cutoff, never q < cutoff)",
                () -> assertTrue(psm.ties() > 0, "some PSM rows equal the PSM cutoff"),
                () -> assertTrue(peptide.ties() > 0, "some peptide rows equal the peptide cutoff"),
                () -> assertEquals(psmOracle, psms, "PSMs at q <= " + psm.text()),
                () -> assertEquals(peptideOracle, peptides, "peptides at q <= " + peptide.text()));

        screen.enter("results-peptide-filter", "0.02");
        settle("0.02");
        showTable("Target PSMs", psm.text());
        screen.enter("results-psm-filter", "0.005");
        settle("0.005");
        assertEquals(oracle("psms.tsv", "0.005"), shownCounts(), "back to 0.005");
        observations.add(
                "boundary: PSM cutoff "
                        + psm.text()
                        + " ("
                        + psm.ties()
                        + " rows equal) shows "
                        + psms
                        + "; peptide cutoff "
                        + peptide.text()
                        + " ("
                        + peptide.ties()
                        + " rows equal) shows "
                        + peptides);
    }

    // ==================================================================== step 24 ====

    /**
     * Step 24: the learned feature weights on screen equal the weights artefact's -- every feature,
     * each split's normalised weight to the four places shown, the mean signed and mean absolute
     * weights and the standard deviation to six, the sign verdict and the rank -- all recomputed
     * here from the raw file; and sorting by mean absolute weight, descending, gives the artefact's
     * ranking.
     *
     * @throws IOException if the file cannot be read
     */
    public void learnedWeightsMatchTheArtefact() throws IOException {
        screen.show("results");
        Path file = run.resolve("outputs/percolator/weights.txt");
        PercolatorWeights weights = PercolatorWeights.read(file);
        int splits = weights.splits().size();
        assertEquals(
                splits
                        + " cross-validation splits, as read from "
                        + file
                        + "; "
                        + weights.names().size()
                        + " features.",
                screen.ui().textOf("weights-status"));
        List<String> expectedColumns = new ArrayList<>(List.of("Feature"));
        for (int split = 1; split <= splits; split++) {
            expectedColumns.add("Split " + split);
        }
        expectedColumns.addAll(
                List.of(
                        "Mean signed",
                        "Mean absolute",
                        "Standard deviation",
                        "Sign consistency",
                        "Rank"));
        assertEquals(expectedColumns, screen.ui().columnsOf("weights-table"));
        List<String> shown = screen.ui().rowsOf("weights-table");
        List<String> wrong = new ArrayList<>();
        List<PercolatorWeights.Feature> features = weights.features();
        assertEquals(features.size(), shown.size(), "one row per feature");
        for (int index = 0; index < features.size(); index++) {
            PercolatorWeights.Feature feature = features.get(index);
            String[] cells = shown.get(index).split("\t", -1);
            String where = "row " + (index + 1) + " (" + feature.name() + "): ";
            if (cells.length != expectedColumns.size()) {
                wrong.add(where + "has " + cells.length + " cells: " + shown.get(index));
                continue;
            }
            String label =
                    feature.name().equals(PercolatorWeights.BIAS)
                            ? "m0 (bias term, not ranked)"
                            : feature.name();
            if (!cells[0].equals(label)) {
                wrong.add(where + "named " + cells[0]);
            }
            for (int split = 0; split < splits; split++) {
                shownAs(
                        cells[1 + split],
                        feature.normalised().get(split),
                        4,
                        where + "split " + (split + 1),
                        wrong);
            }
            shownAs(cells[1 + splits], feature.meanSigned(), 6, where + "mean signed", wrong);
            shownAs(cells[2 + splits], feature.meanAbsolute(), 6, where + "mean absolute", wrong);
            shownAs(cells[3 + splits], deviation(feature), 6, where + "standard deviation", wrong);
            if (!cells[4 + splits].equals(signVerdict(feature))) {
                wrong.add(
                        where
                                + "sign consistency "
                                + cells[4 + splits]
                                + ", not "
                                + signVerdict(feature));
            }
            String rank = feature.rank() == 0 ? "" : Integer.toString(feature.rank());
            if (!cells[5 + splits].equals(rank)) {
                wrong.add(where + "rank '" + cells[5 + splits] + "', not '" + rank + "'");
            }
        }
        assertEquals(List.of(), wrong, "step 24: cells that differ from the weights artefact");

        screen.ui().clickOn("weights-sort-mean-absolute");
        screen.ui().clickOn("weights-sort-mean-absolute");
        assertEquals("Mean absolute, descending", screen.ui().textOf("weights-sort-mean-absolute"));
        List<String> byImportance = new ArrayList<>();
        for (String row : screen.ui().rowsOf("weights-table")) {
            String feature = row.substring(0, row.indexOf('\t'));
            if (!feature.startsWith(PercolatorWeights.BIAS + " ")) {
                byImportance.add(feature);
            }
        }
        assertEquals(
                weights.ranking(),
                byImportance,
                "step 24: sorted by mean absolute weight, the artefact's ranking");
        screen.ui().clickOn("weights-sort-mean-absolute");
        observations.add(
                "weights: "
                        + features.size()
                        + " features over "
                        + splits
                        + " splits; ranking "
                        + weights.ranking());
    }

    /** A shown number must be the exact value rounded half-up to its displayed places. */
    private static void shownAs(
            String shown, BigDecimal exact, int places, String where, List<String> wrong) {
        BigDecimal rounded = exact.setScale(places, RoundingMode.HALF_UP);
        boolean form = shown.matches("-?[0-9]+\\.[0-9]{" + places + "}");
        if (!form || new BigDecimal(shown).compareTo(rounded) != 0) {
            wrong.add(where + " shown as " + shown + ", expected " + rounded.toPlainString());
        }
    }

    /** The population standard deviation of a feature's normalised weights, computed exactly. */
    private static BigDecimal deviation(PercolatorWeights.Feature feature) {
        BigDecimal n = BigDecimal.valueOf(feature.normalised().size());
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal weight : feature.normalised()) {
            BigDecimal away = weight.subtract(feature.meanSigned());
            sum = sum.add(away.multiply(away));
        }
        return sum.divide(n, MathContext.DECIMAL128).sqrt(new MathContext(40));
    }

    /**
     * The sign verdict, by the specification's rule as {@code R-PERC-09}'s view documents it: all
     * positive, all negative, all zero, or mixed, with the count of each class.
     */
    private static String signVerdict(PercolatorWeights.Feature feature) {
        int positive = 0;
        int negative = 0;
        int zero = 0;
        for (BigDecimal weight : feature.normalised()) {
            switch (weight.signum()) {
                case 1 -> positive++;
                case -1 -> negative++;
                default -> zero++;
            }
        }
        int splits = feature.normalised().size();
        String verdict;
        if (positive == splits) {
            verdict = "all positive";
        } else if (negative == splits) {
            verdict = "all negative";
        } else if (zero == splits) {
            verdict = "all zero";
        } else {
            verdict = "mixed";
        }
        return verdict
                + " ("
                + positive
                + " positive, "
                + negative
                + " negative, "
                + zero
                + " zero)";
    }

    // ============================================================= steps 25 to 30 ====

    /**
     * Step 25: PDV, absent before and installed on first use, draws the figure of a PSM the test
     * chose from Percolator's raw table, selected in the Results section; the figure is checked
     * from disk. The tier must have given the application a display PDV can reach.
     *
     * @throws IOException if a file cannot be read
     */
    public void pdvDrawsAKnownPsm() throws IOException {
        downstream.pdvDrawsAKnownPsm();
    }

    /**
     * Step 25's first half alone, for a tier whose display PDV cannot draw on (Phase 14 E6): the
     * chosen PSM selected, PDV absent before and installed on first use by the Draw, and exactly
     * one {@code pdv.launched} event naming the pinned JAR in the fresh home's tool cache. It
     * asserts nothing about the outcome; {@link #pdvDrewTheKnownPsm(PdvLaunch)} is the other half,
     * and {@link #pdvDrawsAKnownPsm()} is both.
     *
     * @return the launch, as recorded
     * @throws IOException if a file cannot be read
     */
    public PdvLaunch pdvIsLaunchedForAKnownPsm() throws IOException {
        return downstream.pdvIsLaunchedForAKnownPsm();
    }

    /**
     * Step 25's second half: the launch drew the chosen PSM, and the figure on disk is its.
     *
     * @param launch what {@link #pdvIsLaunchedForAKnownPsm()} returned
     * @throws IOException if the figure cannot be read
     */
    public void pdvDrewTheKnownPsm(PdvLaunch launch) throws IOException {
        downstream.pdvDrewTheKnownPsm(launch);
    }

    /**
     * One PDV launch of step 25, as the run's {@code pdv.launched} event and the screen recorded
     * it.
     *
     * @param outcome what the Visualisation section said when the launch ended
     * @param jar the installed PDV JAR the test found under the fresh home
     * @param argv the event's argument array, the Java launcher first
     * @param environment the event's {@code process.environment}: the variables the command set,
     *     one {@code NAME=value} per line, sorted
     * @param workingDirectory the event's {@code process.working-directory}
     * @param row the chosen PSM's 1-based row on the Results page
     * @param seconds how long the Draw took, the first-use install included
     */
    public record PdvLaunch(
            String outcome,
            Path jar,
            List<String> argv,
            String environment,
            String workingDirectory,
            int row,
            long seconds) {

        /**
         * Defensive copy.
         *
         * @throws NullPointerException if a part is {@code null}
         */
        public PdvLaunch {
            Objects.requireNonNull(outcome, "outcome");
            Objects.requireNonNull(jar, "jar");
            argv = List.copyOf(argv);
            Objects.requireNonNull(environment, "environment");
            Objects.requireNonNull(workingDirectory, "workingDirectory");
        }
    }

    /**
     * The payloads of the current run's events of one type in its {@code provenance/events.log},
     * oldest first, read with the test's own JSON reader: for a tier's own invariants, such as
     * which Java launcher the converter ran on.
     *
     * @param type the event type's wire name, such as {@code limelight.converted}
     * @return the payloads
     * @throws IOException if the log cannot be read
     */
    public List<Map<String, Object>> events(String type) throws IOException {
        return downstream.events(type);
    }

    /**
     * Step 26: the real converter, absent before and installed on first use, converts the run
     * through the Limelight section; the XML is validated against the schema in the installed JAR
     * and its counts against Percolator's raw table.
     *
     * @throws IOException if a file cannot be read
     */
    public void limelightConverts() throws IOException {
        downstream.limelightConverts();
    }

    /**
     * The converter JAR step 26 found installed in the tool cache: what a tier's fake endpoint
     * reads the Limelight schema from ({@code D-007}).
     *
     * @return the installed JAR
     */
    public Path installedConverter() {
        return downstream.converterJar();
    }

    /**
     * Step 27: the converted file uploaded through the Limelight section to a controlled endpoint
     * with its test key ({@code D-007}), and what the endpoint received checked.
     *
     * @param endpoint the endpoint, listening on loopback
     * @throws IOException if a file cannot be read
     */
    public void uploadsToTheEndpoint(LimelightEndpoint endpoint) throws IOException {
        downstream.uploadsToTheEndpoint(endpoint);
    }

    /**
     * Step 28: the Provenance view opens the run; the record is proved against the disk, the
     * section's display against the record, an export against the record, and the test key is in no
     * file.
     *
     * @throws IOException if a file cannot be read
     */
    public void provenanceHoldsAgainstTheDisk() throws IOException {
        downstream.provenanceHoldsAgainstTheDisk();
    }

    /**
     * Before step 29: records what is shown and on disk, to hold the relaunched application to. The
     * tier then stops the application through its own stop, starts a new one over a new composition
     * root from the same home, and {@link #attach}es the new driver.
     *
     * @throws IOException if the run directory cannot be read
     */
    public void recordWhatIsShownBeforeTheClose() throws IOException {
        downstream.recordWhatIsShownBeforeTheClose();
    }

    /**
     * Steps 29 and 30, after the relaunch: the project reopens, and results, the selected run, tool
     * versions, q-filter state and provenance are coherent with what was shown before.
     *
     * @throws IOException if the run directory cannot be read
     */
    public void theRelaunchedApplicationIsCoherent() throws IOException {
        downstream.theRelaunchedApplicationIsCoherent();
    }

    // ================================================================== internals ====

    /**
     * Shows one of the run's tables and waits for its answer under the cutoff it is filtered at.
     */
    void showTable(String table, String cutoff) {
        if (!screen.ui().selectedTextOf("results-table-choice").equals(table)) {
            screen.ui().select("results-table-choice", table);
        }
        settle(cutoff);
    }

    /** Waits until the shown table has answered under a cutoff. */
    private void settle(String cutoff) {
        screen.await(
                "results-table-status",
                "the results table to answer",
                String::isEmpty,
                QUERY_BOUND);
        screen.await(
                "results-counts",
                "the counts at a cutoff of " + cutoff,
                text -> text.contains("cutoff of " + cutoff + " "),
                QUERY_BOUND);
    }

    /** The four counts shown: total, passing, failing, unknown. */
    List<String> shownCounts() {
        return List.of(
                screen.ui().textOf("results-count-total"),
                screen.ui().textOf("results-count-passing"),
                screen.ui().textOf("results-count-failing"),
                screen.ui().textOf("results-count-unknown"));
    }

    /** The scenario's own counts of a raw Percolator table under a cutoff, in the same order. */
    List<String> oracle(String table, String cutoff) throws IOException {
        QValueCounts counts =
                QValueCounts.of(run.resolve("outputs/percolator").resolve(table), cutoff);
        return Stream.of(counts.total(), counts.passing(), counts.failing(), counts.unknown())
                .map(String::valueOf)
                .toList();
    }

    /**
     * The run's provenance record's tool executions of one tool, read by the test's own JSON
     * reader.
     */
    private List<Map<String, Object>> toolsNamed(String name) throws IOException {
        Map<String, Object> record =
                TestJson.object(
                        Files.readString(
                                run.resolve("provenance/provenance.json"), StandardCharsets.UTF_8));
        List<Map<String, Object>> found = new ArrayList<>();
        Object tools = record.get("tools");
        if (!(tools instanceof List<?> listed)) {
            throw new AssertionError(
                    "the provenance record has no tools array: " + record.keySet());
        }
        for (Object tool : listed) {
            Map<String, Object> entry = object(tool);
            if (name.equals(entry.get("name"))) {
                found.add(entry);
            }
        }
        return found;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value) {
        if (!(value instanceof Map<?, ?>)) {
            throw new AssertionError("not a JSON object: " + value);
        }
        return (Map<String, Object>) value;
    }

    private static List<String> strings(Object value) {
        if (!(value instanceof List<?> listed)) {
            throw new AssertionError("not a JSON array: " + value);
        }
        List<String> texts = new ArrayList<>();
        for (Object item : listed) {
            texts.add(String.valueOf(item));
        }
        return texts;
    }

    /** A spectrum file's name without its extension: the {@code -N} base Comet is given. */
    private static String stem(Path spectrum) {
        String name = String.valueOf(spectrum.getFileName());
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }
}
