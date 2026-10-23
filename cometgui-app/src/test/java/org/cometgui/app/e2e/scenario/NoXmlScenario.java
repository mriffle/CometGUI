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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.cometgui.app.e2e.oracle.DirectorySnapshot;
import org.cometgui.app.e2e.oracle.PercolatorWeights;
import org.cometgui.app.e2e.oracle.QValueCounts;
import org.cometgui.app.e2e.scenario.ShippedManifest.Artefact;
import org.cometgui.app.testing.OwnDigests;
import org.cometgui.app.testing.TestJson;
import org.cometgui.app.uidriver.RemotableUiDriver;

/**
 * The specification's second end-to-end scenario, "Percolator without XML", written once and
 * tier-neutral like {@link CanonicalScenario} (design decision P14-3): the interface is driven
 * through {@link RemotableUiDriver} alone, file dialogs are answered through {@link ChooserScript},
 * and every claim is proved by the test's own oracles reading the files on disk (P14-7). The steps
 * the two scenarios share -- the fresh home, the Comet install, the inputs, the parameters, the
 * run, the Comet outputs, the merged PIN, the counts, the weights and the conversion -- are {@link
 * CanonicalScenario}'s own, called on a canonical scenario this one holds; only what differs is
 * here.
 *
 * <h2>The steps, as the specification lists them</h2>
 *
 * <ol>
 *   <li><strong>Select Percolator 3.09.</strong> The shipped manifest has no Linux x86-64 managed
 *       Percolator 3.09 ({@code R-PERC-12}: its {@code .deb} needs {@code GLIBC_2.38}, its {@code
 *       .rpm} unshipped Boost), so after Comet is installed through the Tool Manager (P14-5) the
 *       3.09 binary is registered through the Percolator section's own registration control, the
 *       dialog answered with the wrapper a scientist would choose -- the specification's own
 *       fallback ({@code R-LL-02}). The scenario asserts the manifest has no such record, the
 *       section names 3.09.0 a registered local binary, and the selection is it. {@link
 *       #installCometAndRegisterPercolator()}.
 *   <li><strong>Run Comet and Percolator 3.09; standard viewing.</strong> The canonical steps 5 to
 *       19 (E5's top-1 setting included, see {@link CanonicalScenario#RESULTS_PER_SPECTRUM}), then
 *       {@link #percolatorRanWithoutXml()} -- one execution, the wrapper, no XML option, no XML on
 *       disk -- and the canonical counts at 0.01 and learned weights against the raw files.
 *   <li><strong>The explanation before any conversion</strong>: {@link
 *       #limelightExplainsBeforeAnyConversion()} -- the text on screen, Convert and its settings
 *       disabled, the action labelled with the version the scenario resolves itself, and nothing
 *       attempted: no converter in the cache, no Limelight output, no conversion event.
 *   <li><strong>The one-click compatible rerun</strong>: {@link
 *       #theCompatibleRerunInstallsOnlyAfterTheClick()} -- no 3.07.1 anywhere in the cache before
 *       the click; afterwards 3.07.1 installed (its own SHA-256 the manifest's) and used by the
 *       derived run. The specification's other branch -- "where no such managed build exists for
 *       the platform the UI offers local-binary registration instead" -- cannot occur on Linux with
 *       the real manifest, where 3.07.1 is managed; {@code LimelightRerunUiTest.noManagedBuild}
 *       (Phase 12) covers it, with every managed Percolator offered as unpublished.
 *   <li><strong>Comet is not rerun</strong> (P14-7, gate item 3): {@link #cometWasNotRerun()}, from
 *       disk first -- the first run's Comet outputs, merged PIN, parameters and logs unchanged by
 *       bytes, inode and modification time; no pepXML or PIN anywhere under the project but those
 *       that were there and the derived run's byte-identical copy of the merged PIN; nothing Comet
 *       writes in the derived run -- and only then against the product's records.
 *   <li><strong>The new execution's XML</strong>: {@link #theRerunWroteXml()}.
 *   <li><strong>The conversion</strong>: canonical step 26 on the derived run ({@link
 *       #limelightConvertsTheRerun()}).
 *   <li><strong>Provenance</strong>: {@link #provenanceHoldsBothPercolatorExecutions()}.
 * </ol>
 *
 * <p>A tier constructs it like the canonical scenario, plus the path of the Percolator 3.09 binary
 * to register; it calls {@link #freshHomeHoldsNoTool()}, starts the application, {@link #attach}es
 * a driver, and calls the steps in the order above.
 */
public final class NoXmlScenario {

    /** The version the 3.09 binary states, as the product names it. */
    public static final String VERSION_309 = "3.09.0";

    /** The version line of the 3.09 build, as the shipped manifest would name it. */
    static final String MANIFEST_309 = "3.09";

    /**
     * How long the compatible rerun -- the 3.07.1 install from GitHub and Percolator -- may take.
     */
    public static final Duration RERUN_BOUND = Duration.ofMinutes(20);

    /** What the registration dialog is asked as, by the chooser's naming. */
    static final String REGISTRATION_DIALOG = "file:a Percolator executable to register";

    /** Every option by which Percolator is asked for XML: output, and the XML input forms. */
    static final Set<String> XML_OPTIONS = Set.of("-X", "--xmloutput", "-E", "--input-xml");

    private static final Pattern RERUN =
            Pattern.compile(
                    "The Percolator rerun succeeded: run (\\S+) in (.+), rescoring the merged"
                            + " PIN of run (\\S+)\\.");

    private final CanonicalScenario canonical;

    private final Path wrapper;

    private Screen screen;

    private Artefact xmlCapable;

    private String firstId;

    private Path first;

    private String derivedId;

    private Path derived;

    private Path installed;

    private BeforeTheRerun before;

    /** What the disk held the moment before the rerun was asked for. */
    private record BeforeTheRerun(
            List<Map.Entry<String, DirectorySnapshot>> firstRun,
            DirectorySnapshot inputs,
            Map<String, String> pinsAndPepXml) {}

    /**
     * A scenario over a layout.
     *
     * @param layout where everything is
     * @param chooser answers the file dialogs
     * @param processes runs the oracles' own tools
     * @param wrapper the Percolator 3.09 executable a scientist registers, absolute
     * @throws IOException if the shipped manifest cannot be read
     */
    public NoXmlScenario(
            ScenarioLayout layout, ChooserScript chooser, OracleProcesses processes, Path wrapper)
            throws IOException {
        this.canonical = new CanonicalScenario(layout, chooser, processes);
        this.wrapper = Objects.requireNonNull(wrapper, "wrapper");
        if (!wrapper.isAbsolute()) {
            throw new IllegalArgumentException("not an absolute path: " + wrapper);
        }
    }

    /**
     * Drives an application from now on.
     *
     * @param driver the driver of the running application
     */
    public void attach(RemotableUiDriver driver) {
        canonical.attach(driver);
        this.screen = canonical.screen();
    }

    /**
     * What the run measured -- the canonical steps' lines and this scenario's, in the order the
     * steps ran -- one line each, for the report.
     *
     * @return the observations so far
     */
    public List<String> observations() {
        return canonical.observations();
    }

    /**
     * The derived run's directory, once the rerun has made it.
     *
     * @return the directory
     */
    public Path derivedRun() {
        return Objects.requireNonNull(derived, "the rerun has not run");
    }

    /**
     * The payloads of the current run's events of one type -- the first run's until the rerun, the
     * derived run's after it -- as {@link CanonicalScenario#events(String)} reads them.
     *
     * @param type the event type's wire name
     * @return the payloads, oldest first
     * @throws IOException if the log cannot be read
     */
    public List<Map<String, Object>> events(String type) throws IOException {
        return canonical.events(type);
    }

    // ================================================== canonical steps, shared ====

    /**
     * Canonical steps 1 and 2: a brand-new home, no tool in it.
     *
     * @throws IOException if the tree cannot be walked
     */
    public void freshHomeHoldsNoTool() throws IOException {
        canonical.freshHomeHoldsNoTool();
    }

    /**
     * Canonical steps 3 and 4: ready, nothing installed, no project.
     *
     * @throws IOException if the home cannot be walked
     */
    public void applicationIsReady() throws IOException {
        canonical.applicationIsReady();
    }

    /**
     * Canonical steps 5 to 7: the two spectrum files and the FASTA through the controls.
     *
     * @throws IOException if the inputs cannot be snapshotted
     */
    public void chooseSpectraAndDatabase() throws IOException {
        canonical.chooseSpectraAndDatabase();
    }

    /** Canonical step 8: the default Comet. */
    public void defaultCometIsSelected() {
        canonical.defaultCometIsSelected();
    }

    /** Canonical steps 10 to 12, E5's top-1 setting among them. */
    public void changeParameters() {
        canonical.changeParameters();
    }

    /** Canonical step 13: both q filters 0.01. */
    public void bothFiltersAreOnePercent() {
        canonical.bothFiltersAreOnePercent();
    }

    /**
     * Canonical steps 14 and 16: Run, and the run succeeds.
     *
     * @throws IOException if the project cannot be listed
     */
    public void run() throws IOException {
        canonical.run();
        firstId = canonical.runId();
        first = canonical.runDirectory();
    }

    /**
     * Canonical step 17: {@code comet.params} holds exactly the chosen values.
     *
     * @throws IOException if a file cannot be read
     */
    public void cometParamsHoldExactlyTheChosenValues() throws IOException {
        canonical.cometParamsHoldExactlyTheChosenValues();
    }

    /**
     * Canonical step 18: one Comet per file, nothing beside the inputs -- the baseline step 5 holds
     * the rerun to.
     *
     * @throws IOException if a file cannot be read
     */
    public void cometRanOncePerFileAndWroteNothingBesideTheInputs() throws IOException {
        canonical.cometRanOncePerFileAndWroteNothingBesideTheInputs();
    }

    /**
     * Canonical step 19: the merged PIN.
     *
     * @throws IOException if a file cannot be read
     */
    public void mergedPinHasOneHeaderAndEveryRow() throws IOException {
        canonical.mergedPinHasOneHeaderAndEveryRow();
    }

    /**
     * Standard viewing, PSMs and peptides: canonical step 21 on the 3.09 run.
     *
     * @throws IOException if a file cannot be read
     */
    public void countsAtOnePercentMatchTheRawFiles() throws IOException {
        canonical.countsAtOnePercentMatchTheRawFiles();
    }

    /**
     * Standard viewing, weights: canonical step 24 on the 3.09 run.
     *
     * @throws IOException if a file cannot be read
     */
    public void learnedWeightsMatchTheArtefact() throws IOException {
        canonical.learnedWeightsMatchTheArtefact();
    }

    // ===================================================================== step 1 ====

    /**
     * Step 1: Comet installed through the Tool Manager from the empty cache (P14-5, as canonical
     * step 14 installs it); then -- because the shipped manifest has no managed Percolator 3.09 for
     * this platform, which the scenario checks with its own reader -- the 3.09 binary registered
     * through the Percolator section's own control, the dialog answered with the wrapper. The
     * section names it a registered local binary with no {@code XML_OUTPUT}, and the next search's
     * Percolator is it; the Run section learns of both tools; and no Percolator is in the cache.
     *
     * @throws IOException if a file cannot be read
     */
    public void installCometAndRegisterPercolator() throws IOException {
        ShippedManifest manifest = canonical.manifest();
        String platform = canonical.platform();
        xmlCapable = manifest.newestXmlCapablePercolator(platform);
        List<String> managed309 =
                manifest.artefacts().stream()
                        .filter(artefact -> artefact.tool().equals("percolator"))
                        .filter(artefact -> artefact.version().startsWith(MANIFEST_309))
                        .filter(
                                artefact ->
                                        (artefact.os() + "/" + artefact.arch()).equals(platform))
                        .map(Artefact::url)
                        .toList();
        assertEquals(
                List.of(),
                managed309,
                "the shipped manifest has no managed Percolator 3.09 for "
                        + platform
                        + ", which is why this scenario registers a local binary (R-LL-02)");
        assertTrue(Files.isRegularFile(wrapper), () -> wrapper + " exists");
        assertFalse(
                wrapper.startsWith(canonical.layout().home().root()),
                "the binary registered lies outside the fresh home");

        canonical.installCometFromTheToolManager();

        screen.show("percolator");
        screen.await(
                "percolator-offers",
                "the Percolator section to read the offers",
                text -> text.contains("Percolator build"),
                CanonicalScenario.CHECK_BOUND);
        assertFalse(
                screen.ui().isSelected("percolator-limelight"),
                "Limelight conversion is off, so the newest Percolator may be chosen");
        int askedBefore = canonical.chooser().asked().size();
        canonical.chooser().nextFile(wrapper);
        screen.ui().clickOn("percolator-register");
        String registered =
                screen.await(
                        "percolator-register-status",
                        "the registration to finish",
                        text -> !text.startsWith("Registering") && !text.isEmpty(),
                        CanonicalScenario.CHECK_BOUND);
        String name = "Percolator " + VERSION_309 + " (registered local binary)";
        String selection =
                screen.await(
                        "percolator-selection",
                        "the selection to become 3.09",
                        text -> text.contains(VERSION_309),
                        CanonicalScenario.CHECK_BOUND);
        String badge = screen.ui().textOf("percolator-badge");
        List<String> asked = canonical.chooser().asked();
        assertAll(
                "step 1: Percolator 3.09 registered as a local binary, and selected",
                () ->
                        assertEquals(
                                List.of(REGISTRATION_DIALOG),
                                asked.subList(askedBefore, asked.size()),
                                "the registration control asked for one file"),
                () ->
                        assertTrue(
                                registered.startsWith(
                                        "Registered " + name + " from " + wrapper + "."),
                                registered),
                () ->
                        assertFalse(
                                registered.contains(ShippedManifest.XML_OUTPUT),
                                () -> "3.09 is not observed to write XML: " + registered),
                () ->
                        assertEquals(
                                name + " runs in the next search: the resolved default.",
                                selection),
                () ->
                        assertEquals(
                                name + " -- the resolved default",
                                screen.ui().selectedTextOf("percolator-version")),
                () ->
                        assertTrue(
                                badge.startsWith(
                                        name
                                                + ": a binary registered from this computer,"
                                                + " installed at "
                                                + wrapper
                                                + ". Capabilities observed by running it: "),
                                badge),
                () ->
                        assertFalse(
                                badge.contains(ShippedManifest.XML_OUTPUT),
                                () -> "no XML_OUTPUT observed: " + badge));
        canonical.percolatorIs(VERSION_309, wrapper);
        canonical.theRunSectionKnowsBothTools();
        assertEquals(
                List.of(),
                percolatorFilesInTheCache(),
                "registering a local binary installs no Percolator into the cache");
        canonical.observe("registered: " + registered);
    }

    // ===================================================================== step 2 ====

    /**
     * Step 2, Percolator's half: one Percolator execution, of the registered wrapper, on the merged
     * PIN, exit 0, asked for no XML in any form ({@code R-PERC-06}); no Percolator XML on disk; the
     * PSM, peptide and weights artefacts parse.
     *
     * @throws IOException if a file cannot be read
     */
    public void percolatorRanWithoutXml() throws IOException {
        List<Map<String, Object>> percolators = tools(record(first), "percolator");
        assertEquals(1, percolators.size(), "one Percolator execution");
        Map<String, Object> execution = object(percolators.get(0).get("execution"));
        List<String> argv = strings(execution.get("argv"));
        Path out = first.resolve("outputs/percolator");
        QValueCounts psms = QValueCounts.of(out.resolve("psms.tsv"), "1");
        QValueCounts peptides = QValueCounts.of(out.resolve("peptides.tsv"), "1");
        PercolatorWeights weights = PercolatorWeights.read(out.resolve("weights.txt"));
        List<String> xmlFiles = filesNamed(first, name -> name.endsWith(".xml"));
        assertAll(
                "step 2: Percolator 3.09 ran, without XML",
                () -> assertEquals(wrapper.toString(), argv.get(0), "the registered binary"),
                () -> assertEquals(VERSION_309, percolators.get(0).get("version")),
                () ->
                        assertEquals(
                                first.resolve("inputs/pin/merged.pin").toString(),
                                argv.get(argv.size() - 1),
                                "the merged PIN"),
                () ->
                        assertEquals(
                                List.of(),
                                argv.stream().filter(XML_OPTIONS::contains).toList(),
                                () -> "no XML option was given: " + argv),
                () -> assertEquals(new BigDecimal(0), execution.get("exitCode"), "exit 0"),
                () -> assertFalse(Files.exists(out.resolve("pout.xml")), "no pout.xml"),
                () ->
                        assertEquals(
                                List.of(),
                                xmlFiles.stream()
                                        .filter(file -> !file.endsWith(".pep.xml"))
                                        .toList(),
                                "no XML in the run but Comet's pepXML"),
                () -> assertTrue(psms.total() > 0, "target PSMs"),
                () -> assertEquals(0, psms.unknown(), "every PSM q-value a number"),
                () -> assertTrue(peptides.total() > 0, "target peptides"),
                () -> assertEquals(0, peptides.unknown(), "every peptide q-value a number"),
                () -> assertFalse(weights.splits().isEmpty(), "weights for each split"));
        canonical.observe(
                "Percolator "
                        + VERSION_309
                        + ": "
                        + psms.total()
                        + " target PSMs, "
                        + peptides.total()
                        + " target peptides, "
                        + weights.splits().size()
                        + " splits; argv "
                        + argv);
    }

    // ===================================================================== step 3 ====

    /**
     * Step 3: the Limelight section explains, before anything is attempted, that the run's
     * Percolator wrote no XML; Convert and every conversion setting are disabled; the action is
     * labelled with the version the scenario's own reader resolves for a Limelight-enabled run --
     * never a literal ({@code R-LL-01}); a click on the disabled Convert does nothing; and nothing
     * was attempted: no converter in the cache or the Tool Manager, no Limelight output, no
     * conversion event in the run's log, no 3.07.1 in the cache.
     *
     * @throws IOException if a file cannot be read
     */
    public void limelightExplainsBeforeAnyConversion() throws IOException {
        Artefact converter =
                canonical.manifest().onlyArtefact("limelight-converter", canonical.platform());
        screen.show("limelight");
        String explanation =
                screen.await(
                        "limelight-compatibility",
                        "the Limelight section to judge the run",
                        text -> !text.startsWith("Checking") && !text.isEmpty(),
                        CanonicalScenario.CHECK_BOUND);
        // The section sets its judgement, its action and the controls' states in one update, so
        // once the judgement shows they are what it decided.
        String action = screen.ui().textOf("limelight-action");
        String label = "Rerun Percolator with " + xmlCapable.version() + " for Limelight";
        assertAll(
                "step 3: the explanation, before anything is attempted",
                () ->
                        assertTrue(
                                screen.ui()
                                        .textOf("limelight-run")
                                        .startsWith("Run " + firstId + ","),
                                () -> screen.ui().textOf("limelight-run")),
                () ->
                        assertTrue(
                                explanation.contains(VERSION_309 + " (registered local binary)"),
                                explanation),
                () -> assertTrue(explanation.contains("XML"), explanation),
                () ->
                        assertFalse(
                                explanation.contains("can be converted."),
                                () -> "not convertible: " + explanation),
                () -> assertEquals(explanation, screen.ui().textOf("limelight-convert-refusal")),
                () ->
                        assertTrue(
                                screen.ui()
                                        .textOf("limelight-refusals")
                                        .startsWith("PERCOLATOR_XML: " + explanation),
                                () -> screen.ui().textOf("limelight-refusals")),
                () -> assertEquals(label, action, "labelled with the resolved version"),
                () -> assertFalse(screen.ui().textOf("limelight-action-explanation").isEmpty()),
                () -> assertTrue(screen.ui().isVisible("limelight-action")),
                () -> assertFalse(screen.ui().isDisabled("limelight-action")),
                () -> assertTrue(screen.ui().isDisabled("limelight-convert")),
                () -> assertTrue(screen.ui().isDisabled("limelight-q-cutoff")),
                () -> assertTrue(screen.ui().isDisabled("limelight-output")),
                () -> assertTrue(screen.ui().isDisabled("limelight-import-decoys")),
                () -> assertTrue(screen.ui().isDisabled("limelight-decoy-prefix")),
                () -> assertTrue(screen.ui().isDisabled("limelight-open-mod")));
        screen.ui().clickOn("limelight-convert");
        assertNothingAttempted(converter, "after a click on the disabled Convert");
        screen.show("tool-manager");
        assertAll(
                "step 3: the Tool Manager installed nothing for Limelight",
                () ->
                        assertEquals(
                                "CometGUI-managed: not installed",
                                screen.ui().textOf(CanonicalScenario.rowOf(converter) + "-state")),
                () ->
                        assertEquals(
                                "CometGUI-managed: not installed",
                                screen.ui()
                                        .textOf(CanonicalScenario.rowOf(xmlCapable) + "-state")));
        canonical.observe("Limelight explained: " + explanation + " -- action \"" + action + "\"");
    }

    private void assertNothingAttempted(Artefact converter, String when) throws IOException {
        List<String> conversionEvents = new ArrayList<>();
        for (Map<String, Object> event : allEvents(first)) {
            String type = String.valueOf(event.get("type"));
            if (type.startsWith("limelight.conver")) {
                conversionEvents.add(type);
            }
        }
        List<String> converterFiles = new ArrayList<>();
        for (String path : cacheEntries()) {
            if (path.toLowerCase(Locale.ROOT).contains("limelight")) {
                converterFiles.add(path);
            }
        }
        assertAll(
                "nothing was attempted " + when,
                () -> assertEquals(List.of(), converterFiles, "no converter in the cache"),
                () ->
                        assertEquals(
                                List.of(),
                                filesWithSha256(converter.installedSha256()),
                                "the converter JAR is nowhere under the home"),
                () ->
                        assertFalse(
                                Files.exists(first.resolve("outputs/limelight")),
                                "no Limelight output"),
                () -> assertEquals(List.of(), conversionEvents, "no conversion event"),
                () -> assertEquals(List.of(), percolatorFilesInTheCache(), "no 3.07.1 yet"),
                () -> assertEquals(List.of(first), runs(), "one run, the first"));
    }

    // ===================================================================== step 4 ====

    /**
     * Step 4, with step 5's snapshots taken just before the click: no Percolator 3.07.1 anywhere in
     * the cache -- by walking it, and by the installed binary's SHA-256 under the whole home -- and
     * the Tool Manager's 3.07.1 row not installed; the action pressed; the rerun succeeds in a new
     * run; afterwards 3.07.1 is installed in the fresh home's cache, holding the bytes the manifest
     * pins, and the new run's own record names it as its Percolator.
     *
     * @throws IOException if a file cannot be read
     */
    public void theCompatibleRerunInstallsOnlyAfterTheClick() throws IOException {
        String row = CanonicalScenario.rowOf(xmlCapable);
        screen.show("tool-manager");
        assertAll(
                "step 4: before the click, no " + xmlCapable.version(),
                () ->
                        assertEquals(
                                "CometGUI-managed: not installed",
                                screen.ui().textOf(row + "-state")),
                () -> assertEquals(List.of(), percolatorFilesInTheCache(), "nothing in the cache"),
                () ->
                        assertEquals(
                                List.of(),
                                filesWithSha256(xmlCapable.installedSha256()),
                                "the 3.07.1 binary is nowhere under the home"));
        before = snapshotBeforeTheRerun();

        screen.show("limelight");
        assertFalse(screen.ui().isDisabled("limelight-action"), "the action is offered");
        long started = System.nanoTime();
        screen.ui().clickOn("limelight-action");
        String outcome =
                screen.await(
                        "limelight-action-outcome",
                        "the compatible rerun to end",
                        text ->
                                text.startsWith("The Percolator rerun ")
                                        || text.startsWith("Percolator could not")
                                        || text.startsWith("Percolator was installed, but")
                                        || text.startsWith("The Percolator install"),
                        RERUN_BOUND);
        long seconds = (System.nanoTime() - started) / 1_000_000_000L;
        Matcher rerun = RERUN.matcher(outcome);
        assertTrue(rerun.lookingAt(), () -> "step 4: the rerun succeeded: " + outcome);
        derivedId = rerun.group(1);
        derived = Path.of(rerun.group(2));
        assertEquals(firstId, rerun.group(3), "the rerun rescored the first run's merged PIN");
        assertEquals(List.of(first, derived), runs(), "the first run and one new run");
        screen.await(
                "limelight-run",
                "the Limelight section to take the derived run from the Results section",
                text -> text.startsWith("Run " + derivedId + ","),
                CanonicalScenario.CHECK_BOUND);

        screen.show("tool-manager");
        String state =
                screen.await(
                        row + "-state",
                        "the Tool Manager to list " + xmlCapable.version() + " as installed",
                        text -> text.endsWith(": installed"),
                        CanonicalScenario.CHECK_BOUND);
        String path = screen.ui().textOf(row + "-path");
        assertTrue(
                path.startsWith("Installed at: " + canonical.layout().toolCache() + "/"),
                () -> "installed in the fresh home's tool cache: " + path);
        installed = Path.of(path.substring("Installed at: ".length()));
        OwnDigests own = OwnDigests.of(installed);
        List<Map<String, Object>> used = tools(record(derived), "percolator");
        assertEquals(1, used.size(), "one Percolator execution in the derived run");
        List<String> argv = strings(object(used.get(0).get("execution")).get("argv"));
        assertAll(
                "step 4: " + xmlCapable.version() + " installed by the click, and used",
                () -> assertEquals("CometGUI-managed: installed", state),
                () ->
                        assertEquals(
                                xmlCapable.installedSha256(),
                                own.sha256(),
                                "the installed binary holds the bytes the manifest pins"),
                () -> assertEquals(installed.toString(), argv.get(0), "the rerun ran it"),
                () -> assertEquals(xmlCapable.version(), used.get(0).get("version")),
                () -> assertEquals(installed.toString(), used.get(0).get("executablePath")));
        canonical.runIs(derivedId, derived);
        canonical.percolatorIs(xmlCapable.version(), installed);
        canonical.observe(
                "rerun: "
                        + outcome
                        + " ("
                        + seconds
                        + " s, the "
                        + xmlCapable.version()
                        + " install included); "
                        + installed);
    }

    private BeforeTheRerun snapshotBeforeTheRerun() throws IOException {
        List<Map.Entry<String, DirectorySnapshot>> trees = new ArrayList<>();
        for (String tree : List.of("outputs/comet", "inputs", "parameters", "logs")) {
            trees.add(Map.entry(tree, DirectorySnapshot.of(first.resolve(tree))));
        }
        return new BeforeTheRerun(
                List.copyOf(trees),
                DirectorySnapshot.of(canonical.layout().inputs()),
                pinsAndPepXml());
    }

    // ===================================================================== step 5 ====

    /**
     * Step 5, Comet was not rerun -- from disk, then against the product's records (P14-7):
     *
     * <ol>
     *   <li>the first run's {@code outputs/comet} (both pepXML, both PIN), {@code inputs} (the
     *       merged PIN), {@code parameters} ({@code comet.params}) and {@code logs} (Comet's logs
     *       among them) are unchanged -- every entry's bytes, size, inode and modification time --
     *       and so is the inputs' directory beside the spectra;
     *   <li>every pepXML and PIN under the project is one that was there before, unchanged, or the
     *       derived run's merged PIN, whose bytes are the first run's merged PIN's;
     *   <li>the derived run holds nothing Comet writes: no {@code outputs/comet}, no Comet log;
     *   <li>its own records name no Comet execution: no Comet tool record, no {@code tool.invoked}
     *       of Comet, no Comet stage;
     *   <li>and only then the product's own account agrees: its {@code rerun.*} settings name the
     *       first run, the Comet binary and the merged PIN the test hashes itself, and say {@code
     *       run-comet} was reused.
     * </ol>
     *
     * @throws IOException if a file cannot be read
     */
    public void cometWasNotRerun() throws IOException {
        List<String> changed = new ArrayList<>();
        for (Map.Entry<String, DirectorySnapshot> tree : before.firstRun()) {
            DirectorySnapshot.Diff diff =
                    tree.getValue().diff(DirectorySnapshot.of(first.resolve(tree.getKey())));
            if (!diff.isEmpty()) {
                changed.add(tree.getKey() + ": " + diff);
            }
        }
        DirectorySnapshot.Diff inputs =
                before.inputs().diff(DirectorySnapshot.of(canonical.layout().inputs()));
        Map<String, String> pinsNow = pinsAndPepXml();
        Map<String, String> added = new TreeMap<>(pinsNow);
        added.keySet().removeAll(before.pinsAndPepXml().keySet());
        List<String> altered = new ArrayList<>();
        for (Map.Entry<String, String> was : before.pinsAndPepXml().entrySet()) {
            if (!was.getValue().equals(pinsNow.get(was.getKey()))) {
                altered.add(
                        was.getKey() + ": " + was.getValue() + " -> " + pinsNow.get(was.getKey()));
            }
        }
        Path copy = derived.resolve("inputs/pin/merged.pin");
        String mergedSha256 = OwnDigests.of(first.resolve("inputs/pin/merged.pin")).sha256();
        Path paramsCopy = derived.resolve("parameters/comet.params");
        String paramsSha256 = OwnDigests.of(first.resolve("parameters/comet.params")).sha256();
        List<String> cometFiles =
                new ArrayList<>(
                        filesNamed(
                                derived,
                                name -> name.toLowerCase(Locale.ROOT).startsWith("comet")));
        cometFiles.remove(paramsCopy.toString());
        List<String> cometOutputs =
                Files.isDirectory(derived.resolve("outputs/comet"))
                        ? filesNamed(derived.resolve("outputs/comet"), name -> true)
                        : List.of();
        assertAll(
                "step 5: Comet was not rerun -- the disk",
                () -> assertEquals(List.of(), changed, "the first run's Comet trees, unchanged"),
                () ->
                        assertTrue(
                                inputs.isEmpty(), () -> "the inputs' directory changed: " + inputs),
                () -> assertEquals(List.of(), altered, "every earlier pepXML and PIN, unchanged"),
                () ->
                        assertEquals(
                                Map.of(copy.toString(), mergedSha256),
                                added,
                                "the one new PIN is the derived run's copy of the merged PIN"),
                () ->
                        assertEquals(
                                List.of(),
                                cometOutputs,
                                "the derived run's outputs/comet holds no file"),
                () ->
                        assertEquals(
                                paramsSha256,
                                OwnDigests.of(paramsCopy).sha256(),
                                "the derived run's comet.params is the first run's, copied"),
                () ->
                        assertEquals(
                                List.of(),
                                cometFiles,
                                "the derived run holds no other file named like Comet's: no"
                                        + " Comet log"));

        Map<String, Object> record = record(derived);
        List<String> invoked = new ArrayList<>();
        List<String> stages = new ArrayList<>();
        for (Map<String, Object> event : allEvents(derived)) {
            Map<String, Object> payload = object(event.get("payload"));
            if ("tool.invoked".equals(event.get("type"))) {
                invoked.add(String.valueOf(payload.get("tool")));
            }
            Object stage = payload.get("stage");
            if (stage != null && String.valueOf(stage).startsWith("comet")) {
                stages.add(event.get("type") + " " + stage);
            }
        }
        assertAll(
                "step 5: the derived run's own records name no Comet execution",
                () -> assertEquals(List.of(), tools(record, "comet"), "no Comet tool record"),
                () -> assertEquals(List.of("percolator"), invoked, "tools invoked"),
                () -> assertEquals(List.of(), stages, "no Comet stage event"));

        Map<String, Object> settings = object(record.get("settings"));
        String cometSha256 = OwnDigests.of(canonical.cometExecutable()).sha256();
        assertAll(
                "step 5: the product's own account agrees with the disk",
                () -> assertEquals(firstId, settings.get("rerun.source-run-id")),
                () -> assertEquals(mergedSha256, settings.get("rerun.merged-pin-sha256")),
                () -> assertEquals(cometSha256, settings.get("rerun.source-comet-binary-sha256")),
                () -> assertEquals(paramsSha256, settings.get("rerun.source-comet-params-sha256")),
                () ->
                        assertEquals(
                                OwnDigests.of(first.resolve("provenance/provenance.json")).sha256(),
                                settings.get("rerun.source-provenance-sha256"),
                                "the first run's record, unchanged since"),
                () ->
                        assertTrue(
                                Arrays.asList(
                                                String.valueOf(settings.get("rerun.reused-steps"))
                                                        .split(" "))
                                        .contains("run-comet"),
                                () -> "run-comet reused: " + settings.get("rerun.reused-steps")),
                () -> assertEquals(VERSION_309, settings.get("rerun.source-percolator-version")));
        canonical.observe(
                "Comet not rerun: "
                        + before.firstRun().stream()
                                .mapToInt(tree -> tree.getValue().entries().size())
                                .sum()
                        + " entries of the first run unchanged (bytes, inode, mtime); "
                        + pinsNow.size()
                        + " pepXML/PIN under the project, the one new one the merged PIN's copy "
                        + mergedSha256
                        + "; reused "
                        + settings.get("rerun.reused-steps"));
    }

    // ===================================================================== step 6 ====

    /**
     * Step 6: the derived run's Percolator wrote XML -- a Percolator XML document, parsed here,
     * with a {@code psm} element per row of its target PSM table and a {@code peptide} element per
     * row of its target peptide table.
     *
     * @throws IOException if a file cannot be read
     */
    public void theRerunWroteXml() throws IOException {
        Path out = derived.resolve("outputs/percolator");
        Map<String, Object> execution =
                object(tools(record(derived), "percolator").get(0).get("execution"));
        List<String> argv = strings(execution.get("argv"));
        QValueCounts psms = QValueCounts.of(out.resolve("psms.tsv"), "1");
        QValueCounts peptides = QValueCounts.of(out.resolve("peptides.tsv"), "1");
        XmlCensus xml = XmlCensus.of(out.resolve("pout.xml"), "decoy");
        int at = argv.indexOf("-X");
        assertAll(
                "step 6: the new execution produced XML",
                () -> assertTrue(at > 0, () -> "XML was asked for: " + argv),
                () -> assertEquals(out.resolve("pout.xml").toString(), argv.get(at + 1)),
                () ->
                        assertEquals(
                                derived.resolve("inputs/pin/merged.pin").toString(),
                                argv.get(argv.size() - 1),
                                "the derived run's merged PIN"),
                () -> assertEquals(new BigDecimal(0), execution.get("exitCode"), "exit 0"),
                () -> assertEquals("percolator_output", xml.root(), "a Percolator XML document"),
                () -> assertTrue(psms.total() > 0, "target PSMs"),
                () -> assertEquals(psms.total(), xml.count("psm"), "a psm per target PSM row"),
                () ->
                        assertEquals(
                                peptides.total(),
                                xml.count("peptide"),
                                "a peptide per target peptide row"));
        canonical.observe(
                "rerun XML: "
                        + xml.count("psm")
                        + " psm and "
                        + xml.count("peptide")
                        + " peptide elements, = the tables' "
                        + psms.total()
                        + " and "
                        + peptides.total());
    }

    // ===================================================================== step 7 ====

    /**
     * Step 7: canonical step 26 on the derived run -- the converter, absent until now, installs on
     * first use and converts it; the XML is valid against the JAR's own schema and its PSMs are the
     * derived run's table's.
     *
     * @throws IOException if a file cannot be read
     */
    public void limelightConvertsTheRerun() throws IOException {
        canonical.limelightConverts();
    }

    // ===================================================================== step 8 ====

    /**
     * Step 8: the two runs' provenance records hold both Percolator executions -- each the run's
     * only one -- with distinct versions, each binary's SHA-256 and MD5 equal to the test's own
     * digest of the file on disk, and distinct commands: 3.09's without any XML option, 3.07.1's
     * with {@code -X} naming its {@code pout.xml}.
     *
     * @throws IOException if a file cannot be read
     */
    public void provenanceHoldsBothPercolatorExecutions() throws IOException {
        Map<String, Object> original = only(tools(record(first), "percolator"), first);
        Map<String, Object> rerun = only(tools(record(derived), "percolator"), derived);
        OwnDigests wrapperOwn = OwnDigests.of(wrapper);
        OwnDigests installedOwn = OwnDigests.of(installed);
        List<String> originalArgv = strings(object(original.get("execution")).get("argv"));
        List<String> rerunArgv = strings(object(rerun.get("execution")).get("argv"));
        assertAll(
                "step 8: both Percolator executions, in provenance",
                () -> assertEquals(VERSION_309, original.get("version")),
                () -> assertEquals(xmlCapable.version(), rerun.get("version")),
                () -> assertNotEquals(original.get("version"), rerun.get("version")),
                () -> assertEquals(wrapper.toString(), original.get("executablePath")),
                () -> assertEquals(installed.toString(), rerun.get("executablePath")),
                () -> assertEquals(wrapperOwn.sha256(), original.get("sha256"), "3.09's SHA-256"),
                () -> assertEquals(wrapperOwn.md5(), original.get("md5"), "3.09's MD5"),
                () -> assertEquals(installedOwn.sha256(), rerun.get("sha256"), "3.07.1's SHA-256"),
                () -> assertEquals(installedOwn.md5(), rerun.get("md5"), "3.07.1's MD5"),
                () -> assertNotEquals(original.get("sha256"), rerun.get("sha256")),
                () -> assertEquals(wrapper.toString(), originalArgv.get(0)),
                () -> assertEquals(installed.toString(), rerunArgv.get(0)),
                () ->
                        assertEquals(
                                List.of(),
                                originalArgv.stream().filter(XML_OPTIONS::contains).toList(),
                                () -> "3.09's command has no XML option: " + originalArgv),
                () ->
                        assertTrue(
                                rerunArgv.contains("-X"),
                                () -> "3.07.1's command asks for XML: " + rerunArgv),
                () -> assertNotEquals(originalArgv, rerunArgv));
        canonical.observe(
                "provenance: 3.09.0 "
                        + wrapperOwn.sha256()
                        + " argv "
                        + originalArgv
                        + "; "
                        + xmlCapable.version()
                        + " "
                        + installedOwn.sha256()
                        + " argv "
                        + rerunArgv);
    }

    // ================================================================== internals ====

    private static Map<String, Object> only(List<Map<String, Object>> found, Path run) {
        assertEquals(1, found.size(), () -> "one Percolator record in " + run + ": " + found);
        return found.get(0);
    }

    /** Every Percolator file or directory in the tool cache and the download cache. */
    private List<String> percolatorFilesInTheCache() throws IOException {
        List<String> found = new ArrayList<>();
        for (String path : cacheEntries()) {
            if (path.toLowerCase(Locale.ROOT).contains("percolator")) {
                found.add(path);
            }
        }
        return found;
    }

    /** Every path under the tool cache and the download cache, walked. */
    private List<String> cacheEntries() throws IOException {
        Path data = canonical.layout().home().applicationDataDirectory();
        List<String> found = new ArrayList<>();
        for (Path cache : List.of(canonical.layout().toolCache(), data.resolve("cache"))) {
            if (Files.isDirectory(cache, LinkOption.NOFOLLOW_LINKS)) {
                try (Stream<Path> tree = Files.walk(cache)) {
                    tree.filter(path -> !path.equals(cache))
                            .forEach(path -> found.add(path.toString()));
                }
            }
        }
        return found;
    }

    /** Every regular file under the home with this SHA-256, by the test's own digest. */
    private List<String> filesWithSha256(String sha256) throws IOException {
        List<String> found = new ArrayList<>();
        Path root = canonical.layout().home().root();
        try (Stream<Path> tree = Files.walk(root)) {
            for (Path path : (Iterable<Path>) tree::iterator) {
                if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                        && OwnDigests.of(path).sha256().equals(sha256)) {
                    found.add(path.toString());
                }
            }
        }
        return found;
    }

    /** Every pepXML and PIN under the project, with its SHA-256. */
    private Map<String, String> pinsAndPepXml() throws IOException {
        Map<String, String> found = new TreeMap<>();
        try (Stream<Path> tree = Files.walk(canonical.layout().projectDirectory())) {
            for (Path path : (Iterable<Path>) tree::iterator) {
                String name = String.valueOf(path.getFileName()).toLowerCase(Locale.ROOT);
                if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                        && (name.endsWith(".pin") || name.endsWith(".pep.xml"))) {
                    found.put(path.toString(), OwnDigests.of(path).sha256());
                }
            }
        }
        return found;
    }

    /** Every regular file under a directory whose name a test accepts, sorted. */
    private static List<String> filesNamed(Path root, Predicate<String> name) throws IOException {
        Set<String> found = new TreeSet<>();
        try (Stream<Path> tree = Files.walk(root)) {
            for (Path path : (Iterable<Path>) tree::iterator) {
                if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                        && name.test(String.valueOf(path.getFileName()))) {
                    found.add(path.toString());
                }
            }
        }
        return List.copyOf(found);
    }

    /** The project's run directories, sorted. */
    private List<Path> runs() throws IOException {
        try (Stream<Path> listed =
                Files.list(canonical.layout().projectDirectory().resolve("runs"))) {
            return listed.sorted().toList();
        }
    }

    private static Map<String, Object> record(Path run) throws IOException {
        return TestJson.object(
                Files.readString(
                        run.resolve("provenance/provenance.json"), StandardCharsets.UTF_8));
    }

    private static List<Map<String, Object>> tools(Map<String, Object> record, String name) {
        Object tools = record.get("tools");
        if (!(tools instanceof List<?> listed)) {
            throw new AssertionError(
                    "the provenance record has no tools array: " + record.keySet());
        }
        List<Map<String, Object>> found = new ArrayList<>();
        for (Object tool : listed) {
            Map<String, Object> entry = object(tool);
            if (name.equals(entry.get("name"))) {
                found.add(entry);
            }
        }
        return found;
    }

    /** Every event of a run's log, oldest first, read with the test's JSON reader. */
    private static List<Map<String, Object>> allEvents(Path run) throws IOException {
        List<Map<String, Object>> events = new ArrayList<>();
        for (String line :
                Files.readAllLines(run.resolve("provenance/events.log"), StandardCharsets.UTF_8)) {
            if (!line.isBlank()) {
                events.add(TestJson.object(line));
            }
        }
        return events;
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
}
