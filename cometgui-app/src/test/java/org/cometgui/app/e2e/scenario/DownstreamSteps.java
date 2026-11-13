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
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.cometgui.app.e2e.oracle.BestTargetPsm;
import org.cometgui.app.e2e.oracle.DirectorySnapshot;
import org.cometgui.app.e2e.oracle.FigureImage;
import org.cometgui.app.e2e.oracle.LimelightXml;
import org.cometgui.app.e2e.oracle.QValueCounts;
import org.cometgui.app.e2e.scenario.ShippedManifest.Artefact;
import org.cometgui.app.testing.LimelightFiles;
import org.cometgui.app.testing.OwnDigests;
import org.cometgui.app.testing.SecretSweep;
import org.cometgui.app.testing.TestJson;
import org.cometgui.app.uidriver.Await;

/**
 * Steps 25 to 30 of the canonical scenario (Phase 14 unit 3), tier-neutral as the rest (P14-3):
 * PDV, the Limelight converter, the upload, provenance and the relaunch. {@link CanonicalScenario}
 * owns the state the earlier steps left and delegates to this class; every interaction is through
 * {@link Screen}, every fact checked is read from disk by the test's own code (P14-7).
 */
final class DownstreamSteps {

    /** How long PDV's first-use install -- about 99 MB from GitHub -- and one figure may take. */
    static final Duration PDV_BOUND = Duration.ofMinutes(15);

    /** How long the converter's first-use install and one conversion may take. */
    static final Duration CONVERT_BOUND = Duration.ofMinutes(10);

    /** How long a check of a destination, or an upload to the loopback endpoint, may take. */
    static final Duration UPLOAD_BOUND = Duration.ofMinutes(2);

    /** How long the Provenance section may take to list, open, export or verify a run. */
    static final Duration SECTION_BOUND = Duration.ofMinutes(2);

    /**
     * The SHA-256 of {@code PDV-2.7.0.jar} inside the pinned release asset, as Phase 11 measured
     * and pinned it ({@code PdvFirstUseUiTest}); the manifest pins the archive, not the member.
     */
    static final String PDV_JAR_SHA256 =
            "fc876310b6f9dd95bd9538f212e28d45ac0226ea1615a9c09f2e904d70d07908";

    /** The Results cutoff the PSM is chosen at: the one the PSM filter shows after step 23. */
    static final String PSM_CUTOFF = "0.005";

    /** What the upload section says once Upload has shown a destination and sent nothing. */
    static final String NOTHING_SENT_YET =
            "Nothing has been sent. Check the destination below; press \"Send to Limelight\" to"
                    + " upload this file there, or Cancel.";

    /** What the key's status says once a key has been typed and committed. */
    static final String KEY_TYPED =
            "A submit key was typed (not shown). It is used for the next upload and then"
                    + " forgotten, unless \"Remember the key\" is ticked.";

    private static final Pattern MD5 = Pattern.compile("[0-9a-f]{32}");

    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    private final CanonicalScenario scenario;

    private BestTargetPsm drawn;

    private Path figure;

    private Path pdvJar;

    private Path converterJar;

    private Path converted;

    private String convertedSha256;

    private LimelightEndpoint endpoint;

    private Path exported;

    private Closed beforeTheClose;

    DownstreamSteps(CanonicalScenario scenario) {
        this.scenario = scenario;
    }

    Path converterJar() {
        return Objects.requireNonNull(converterJar, "step 26 has not run");
    }

    private Screen screen() {
        return scenario.screen();
    }

    private ScenarioLayout layout() {
        return scenario.layout();
    }

    private Path run() {
        return scenario.runDirectory();
    }

    private String runId() {
        return scenario.runId();
    }

    private void observe(String line) {
        scenario.observe(line);
    }

    // ==================================================================== step 25 ====

    /**
     * Step 25: PDV draws the figure of a PSM the test chose itself -- the best-scoring unmodified
     * target PSM at the PSM filter's cutoff, from Percolator's raw table -- selected in the Results
     * section with the keyboard and drawn from the Visualisation section. PDV is absent from the
     * cache before, and the Draw installs it (first use); the figure exists, decodes, is of a
     * plausible size and is annotated, for that PSM; and the run's {@code pdv.launched} event names
     * the JAR the test hashes itself.
     *
     * @throws IOException if a file cannot be read
     */
    void pdvDrawsAKnownPsm() throws IOException {
        pdvDrewTheKnownPsm(pdvIsLaunchedForAKnownPsm());
    }

    /**
     * Step 25's first half, which needs no display PDV can draw on: the chosen PSM selected in the
     * Results section, PDV absent before and installed on first use by the Draw -- the pinned JAR
     * in the fresh home's tool cache, the Tool Manager listing it installed -- and exactly one
     * {@code pdv.launched} event naming that JAR, its digest and version, the launch kind and the
     * PSM. Whether PDV then drew is {@link #pdvDrewTheKnownPsm}'s question.
     *
     * @return what the launch was, as the run's event and the screen record it
     * @throws IOException if a file cannot be read
     */
    CanonicalScenario.PdvLaunch pdvIsLaunchedForAKnownPsm() throws IOException {
        Artefact pdv = scenario.manifest().onlyArtefact("pdv", scenario.platform());
        String row = CanonicalScenario.rowOf(pdv);
        assertEquals(List.of(), toolFiles("pdv.*\\.(jar|zip)"), "no PDV under the home before");
        assertFalse(Files.exists(layout().toolCache().resolve("pdv")), "no PDV in the cache");
        screen().show("tool-manager");
        assertEquals("CometGUI-managed: not installed", screen().ui().textOf(row + "-state"));

        drawn = BestTargetPsm.of(run().resolve("outputs/percolator/psms.tsv"), PSM_CUTOFF);
        screen().show("results");
        scenario.showTable("Target PSMs", PSM_CUTOFF);
        List<String> rows = screen().ui().rowsOf("results-table");
        int index = -1;
        for (int at = 0; at < rows.size() && index < 0; at++) {
            if (rows.get(at).startsWith(drawn.psmId() + "\t")) {
                index = at;
            }
        }
        int found = index;
        assertTrue(
                found >= 0,
                () ->
                        "the Results table's page holds the chosen PSM "
                                + drawn.psmId()
                                + " ("
                                + rows.size()
                                + " rows shown)");
        // The row's second cell is the source-file column. The PSM's identifier begins with the
        // bare -N base of its spectrum file -- no directory -- and the table shows that file by
        // name.
        String psmBase = drawn.psmId().replaceFirst("_[0-9]+_[0-9]+_[0-9]+$", "");
        List<String> sourceNames = new ArrayList<>();
        for (Path spectrum : layout().spectra()) {
            String name = String.valueOf(spectrum.getFileName());
            if (name.replaceFirst("\\.[^.]*$", "").equals(psmBase)) {
                sourceNames.add(name);
            }
        }
        assertAll(
                "step 25: the chosen PSM's identifier and its source file",
                () ->
                        assertFalse(
                                drawn.psmId().contains("/") || drawn.psmId().contains("\\"),
                                () -> "the PSM identifier holds no path: " + drawn.psmId()),
                () ->
                        assertEquals(
                                1,
                                sourceNames.size(),
                                () -> "one spectrum file has the base " + psmBase),
                () ->
                        assertEquals(
                                sourceNames,
                                List.of(rows.get(found).split("\t", -1)[1]),
                                "the Results table shows the PSM's spectrum file by name"));
        screen().ui().clickOn("results-table");
        screen().ui().pressKey("HOME");
        for (int step = 0; step < found; step++) {
            screen().ui().pressKey("DOWN");
        }

        screen().show("visualisation");
        String bytes = String.format(Locale.ROOT, "%,d", pdv.transferBytes());
        String before =
                screen().await(
                                "visualisation-pdv-status",
                                "the Visualisation section to read PDV's state",
                                text -> text.startsWith("PDV " + pdv.version() + " is"),
                                CanonicalScenario.CHECK_BOUND);
        String psmShown = screen().ui().textOf("visualisation-psm");
        assertAll(
                "step 25: the chosen PSM selected, PDV not yet installed",
                () ->
                        assertEquals(
                                "PDV "
                                        + pdv.version()
                                        + " is not installed. It is installed the first time a"
                                        + " spectrum is drawn: a download of "
                                        + bytes
                                        + " bytes, which no search waits for.",
                                before),
                () ->
                        assertTrue(
                                psmShown.startsWith(
                                        "PSM " + drawn.psmId() + ": peptide " + drawn.peptide()),
                                () -> "the selected PSM is " + drawn + ": " + psmShown),
                () ->
                        assertTrue(
                                screen().ui()
                                        .textOf("visualisation-run")
                                        .startsWith("Run " + runId() + ", created "),
                                () -> screen().ui().textOf("visualisation-run")),
                () -> assertEquals("", screen().ui().textOf("visualisation-draw-refusal")));

        long started = System.nanoTime();
        screen().ui().clickOn("visualisation-draw");
        String outcome =
                screen().await(
                                "visualisation-outcome",
                                "PDV to be installed and to draw",
                                text ->
                                        text.startsWith("PDV drew ")
                                                || text.startsWith("Nothing was drawn")
                                                || text.startsWith("PDV could not be installed")
                                                || text.startsWith("The PDV install was cancelled"),
                                PDV_BOUND);
        long seconds = (System.nanoTime() - started) / 1_000_000_000L;

        List<Path> jars = toolFiles("pdv.*\\.jar");
        assertEquals(
                1,
                jars.size(),
                () -> "one PDV JAR installed: " + jars + "; the outcome: " + outcome);
        pdvJar = jars.get(0);
        String version;
        try (JarFile jar = new JarFile(pdvJar.toFile())) {
            version =
                    jar.getManifest()
                            .getMainAttributes()
                            .getValue(Attributes.Name.IMPLEMENTATION_VERSION);
        }
        OwnDigests own = OwnDigests.of(pdvJar);
        List<Map<String, Object>> launches = events("pdv.launched");
        assertEquals(
                1,
                launches.size(),
                () -> "one PDV launch, one pdv.launched event; the outcome: " + outcome);
        Map<String, Object> launched = launches.get(0);
        String jarVersion = version;
        assertAll(
                "step 25: PDV installed on first use, and launched for the chosen PSM",
                () ->
                        assertTrue(
                                pdvJar.startsWith(layout().toolCache()),
                                () -> pdvJar + " is in the fresh home's tool cache"),
                () -> assertEquals(PDV_JAR_SHA256, own.sha256(), "the pinned PDV JAR"),
                () -> assertEquals(pdv.version(), jarVersion, "the JAR's own version"),
                () ->
                        assertEquals(
                                "PDV " + pdv.version() + " is installed: " + pdvJar + ".",
                                screen().ui().textOf("visualisation-pdv-status")),
                () -> assertEquals(own.sha256(), launched.get("pdv.jar.sha256")),
                () -> assertEquals(pdvJar.toString(), launched.get("pdv.jar.path")),
                () -> assertEquals(jarVersion, launched.get("tool.version")),
                () -> assertEquals("cli-figure", launched.get("pdv.launch")),
                () -> assertEquals(runId(), launched.get("run.id")),
                () -> assertEquals(drawn.psmId(), launched.get("launch.psm-id")));
        screen().show("tool-manager");
        assertEquals(
                "CometGUI-managed: installed",
                screen().await(
                                row + "-state",
                                "the Tool Manager to list PDV as installed",
                                text -> text.endsWith(": installed"),
                                CanonicalScenario.CHECK_BOUND));
        return new CanonicalScenario.PdvLaunch(
                outcome,
                pdvJar,
                argv(launched),
                text(launched, "process.environment"),
                text(launched, "process.working-directory"),
                found + 1,
                seconds);
    }

    /**
     * Step 25's second half, which needs a display PDV can draw on: the launch drew the chosen
     * PSM's annotated spectrum, and the figure -- the one file in the run's first figure directory
     * -- exists, decodes, is of a plausible size, is not blank and shows both ion colours.
     *
     * @param launch what {@link #pdvIsLaunchedForAKnownPsm()} returned
     * @throws IOException if the figure cannot be read
     */
    void pdvDrewTheKnownPsm(CanonicalScenario.PdvLaunch launch) throws IOException {
        String outcome = launch.outcome();
        assertTrue(
                outcome.startsWith(
                        "PDV drew the annotated spectrum of PSM "
                                + drawn.psmId()
                                + " ("
                                + drawn.sequence()
                                + ") from spectrum "),
                () -> "step 25: PDV drew " + drawn + ": " + outcome);
        figure = onlyFigure();
        FigureImage image = FigureImage.read(figure);
        assertAll(
                "step 25: the figure of the chosen PSM",
                () -> assertTrue(outcome.contains(figure.toString()), outcome),
                () ->
                        assertTrue(
                                outcome.endsWith(
                                        ", " + image.width() + " x " + image.height() + " pixels."),
                                outcome),
                () -> assertTrue(image.bytes() > 2_000, "bytes: " + image.bytes()),
                () -> assertTrue(image.width() >= 400 && image.width() <= 4_000, "" + image),
                () -> assertTrue(image.height() >= 200 && image.height() <= 4_000, "" + image),
                () ->
                        assertTrue(
                                image.ink() * 100 >= (long) image.width() * image.height(),
                                () -> "at least 1% ink, not blank: " + image),
                () -> assertTrue(image.orange() >= 100, () -> "orange ions: " + image),
                () -> assertTrue(image.green() >= 100, () -> "green ions: " + image));
        observe(
                "PDV: drew PSM "
                        + drawn.psmId()
                        + " ("
                        + drawn.peptide()
                        + ", score "
                        + drawn.score()
                        + ", q "
                        + drawn.qValue()
                        + "; the best of "
                        + drawn.candidates()
                        + " unmodified target PSMs at q <= "
                        + PSM_CUTOFF
                        + ", row "
                        + launch.row()
                        + " of the page) in "
                        + launch.seconds()
                        + " s including the first-use install; figure "
                        + figure
                        + ": "
                        + image.width()
                        + " x "
                        + image.height()
                        + " px, "
                        + image.bytes()
                        + " bytes, "
                        + image.ink()
                        + " ink, "
                        + image.orange()
                        + " orange, "
                        + image.green()
                        + " green");
    }

    private Path onlyFigure() throws IOException {
        Path figures = run().resolve("pdv/figures/0001/figures");
        try (Stream<Path> files = Files.list(figures)) {
            List<Path> pngs =
                    files.filter(file -> String.valueOf(file.getFileName()).endsWith(".png"))
                            .toList();
            assertEquals(1, pngs.size(), () -> "one figure in " + figures + ": " + pngs);
            return pngs.get(0);
        }
    }

    // ==================================================================== step 26 ====

    /**
     * Step 26: the real converter, absent before, installed on first use and run through the
     * Limelight section's Convert; the file it wrote read by the test -- well-formed, non-empty,
     * valid against the schema the test extracts from the installed JAR ({@code D-007}: the XSD
     * lives in the JAR) -- and its counts checked against Percolator's raw table: one {@code psm}
     * element per target PSM row, and as many of them at most the Limelight cutoff 0.01 as the
     * table has rows there.
     *
     * @throws IOException if a file cannot be read
     */
    void limelightConverts() throws IOException {
        Artefact converter =
                scenario.manifest().onlyArtefact("limelight-converter", scenario.platform());
        String cutoff = "0.01";
        assertEquals(List.of(), toolFiles(".*limelight.*\\.jar"), "no converter before");
        screen().show("limelight");
        String compatibility =
                screen().await(
                                "limelight-compatibility",
                                "the Limelight section to judge the run",
                                text -> !text.startsWith("Checking"),
                                CanonicalScenario.CHECK_BOUND);
        Path output = run().resolve("outputs/limelight/limelight-1.xml");
        assertAll(
                "step 26: before the conversion",
                () ->
                        assertEquals(
                                "Run "
                                        + runId()
                                        + "'s Percolator "
                                        + scenario.percolatorVersion()
                                        + " wrote the Percolator XML the Limelight converter"
                                        + " reads, so the run can be converted.",
                                compatibility),
                () ->
                        assertTrue(
                                screen().ui()
                                        .textOf("limelight-converter-status")
                                        .startsWith(
                                                "The Limelight converter "
                                                        + converter.version()
                                                        + " is not installed."),
                                () -> screen().ui().textOf("limelight-converter-status")),
                () -> assertEquals(cutoff, screen().ui().textOf("limelight-q-cutoff")),
                () ->
                        assertEquals(
                                "The next conversion writes " + output + ".",
                                screen().ui().textOf("limelight-output-status")),
                () -> assertFalse(screen().ui().isDisabled("limelight-convert")));

        long started = System.nanoTime();
        screen().ui().clickOn("limelight-convert");
        Set<String> progress = new LinkedHashSet<>();
        String outcome =
                Await.until(
                        "the converter to be installed and to convert (#limelight-outcome)",
                        () -> {
                            String shown = screen().ui().textOf("limelight-converter-status");
                            if (!shown.isEmpty()) {
                                progress.add(shown);
                            }
                            return screen().ui().textOf("limelight-outcome");
                        },
                        text ->
                                text.startsWith("Run " + runId() + " was converted")
                                        || text.startsWith("Nothing was converted")
                                        || text.startsWith("The converter's output was not kept")
                                        || text.startsWith("The Limelight converter could not"),
                        CONVERT_BOUND);
        long seconds = (System.nanoTime() - started) / 1_000_000_000L;
        if (!outcome.startsWith("Run " + runId() + " was converted")) {
            throw new AssertionError(
                    "step 26: the conversion did not succeed: "
                            + outcome
                            + " -- the converter states shown: "
                            + progress
                            + " -- the Tool Manager's row: "
                            + rowTexts(CanonicalScenario.rowOf(converter)));
        }
        List<Path> jars = toolFiles(".*limelight.*\\.jar");
        assertEquals(1, jars.size(), () -> "one converter JAR installed: " + jars);
        converterJar = jars.get(0);
        converted = output;
        QValueCounts psms = QValueCounts.of(run().resolve("outputs/percolator/psms.tsv"), cutoff);
        assertTrue(Files.isRegularFile(output), () -> output + " was written: " + outcome);
        long size = Files.size(output);
        LimelightXml xml = LimelightXml.read(output, cutoff);
        OwnDigests own = OwnDigests.of(output);
        OwnDigests jar = OwnDigests.of(converterJar);
        convertedSha256 = own.sha256();
        List<Map<String, Object>> conversions = events("limelight.converted");
        assertEquals(1, conversions.size(), "one conversion, one limelight.converted event");
        Map<String, Object> event = conversions.get(0);
        assertAll(
                "step 26: the converter installed on first use, and the file it wrote",
                () ->
                        assertTrue(
                                outcome.startsWith(
                                        "Run "
                                                + runId()
                                                + " was converted to valid Limelight XML: "
                                                + output
                                                + ", holding "
                                                + psms.total()
                                                + " PSMs and "),
                                outcome),
                () ->
                        assertTrue(
                                converterJar.startsWith(layout().toolCache()),
                                () -> converterJar + " is in the fresh home's tool cache"),
                () -> assertEquals(converter.installedSha256(), jar.sha256(), "the pinned JAR"),
                () -> assertTrue(size > 0, "not empty"),
                () -> assertEquals("limelight_input", xml.root(), "a Limelight XML document"),
                () -> LimelightFiles.assertValid(output, converterJar),
                () -> assertEquals(psms.total(), xml.psms(), "a psm element per target PSM row"),
                () ->
                        assertEquals(
                                psms.passing(),
                                xml.psmsAtCutoff(),
                                "PSMs at q <= " + cutoff + ": the file's against the table's"),
                () -> assertEquals(0, xml.psmsWithoutQ(), "every PSM has a Percolator q-value"),
                () -> assertEquals(Optional.of(cutoff), xml.qFilter(), "the Limelight cutoff"),
                () ->
                        assertEquals(
                                output.toString(), screen().ui().textOf("limelight-result-file")),
                () ->
                        assertEquals(
                                Long.toString(psms.total()),
                                screen().ui().textOf("limelight-result-psms")),
                () -> assertEquals(own.sha256(), screen().ui().textOf("limelight-result-sha256")),
                () -> assertEquals(output.toString(), event.get("output.path")),
                () -> assertEquals(own.sha256(), event.get("output.sha256")),
                () -> assertEquals(Long.toString(psms.total()), event.get("output.psms")),
                () -> assertEquals(cutoff, event.get("limelight.q-cutoff")),
                () -> assertEquals(converterJar.toString(), event.get("converter.jar.path")),
                () -> assertEquals(jar.sha256(), event.get("converter.jar.sha256")),
                () -> assertEquals(jar.md5(), event.get("converter.jar.md5")),
                () -> assertEquals(converter.version(), event.get("tool.version")));
        observe(
                "Limelight: "
                        + output
                        + ", "
                        + size
                        + " bytes, valid against the XSD in "
                        + converterJar
                        + "; "
                        + xml.psms()
                        + " psm elements (= target PSM rows), "
                        + xml.psmsAtCutoff()
                        + " at q <= "
                        + cutoff
                        + " (= the table's "
                        + psms.passing()
                        + "), "
                        + xml.reportedPeptides()
                        + " reported peptides; installed and converted in "
                        + seconds
                        + " s");
    }

    // ==================================================================== step 27 ====

    /**
     * Step 27: the converted file uploaded through the Limelight section to the controlled endpoint
     * ({@code D-007}) with the endpoint's test key: Upload shows the destination and sends nothing;
     * Send uploads; the endpoint received exactly the converted file -- its SHA-256 and size,
     * computed here -- found it valid, and queued it; and the screen shows the endpoint's own
     * tracking id. The key never appears in any file (step 28's sweep).
     *
     * @param controlled the endpoint, listening
     * @throws IOException if a file cannot be read
     */
    void uploadsToTheEndpoint(LimelightEndpoint controlled) throws IOException {
        this.endpoint = controlled;
        String server = controlled.server();
        String project = controlled.project();
        String page = server + "/d/pg/project/" + project;
        assertEquals(0, controlled.requests(), "the endpoint has received nothing yet");
        screen().show("limelight");
        String files =
                screen().await(
                                "limelight-upload-files-status",
                                "the upload to list the converted file",
                                text -> text.startsWith("1 Limelight XML file"),
                                CanonicalScenario.CHECK_BOUND);
        assertAll(
                "step 27: the converted file is offered",
                () ->
                        assertEquals(
                                "1 Limelight XML file was converted from run "
                                        + runId()
                                        + ", newest first.",
                                files),
                () ->
                        assertEquals(
                                converted.toString(),
                                screen().ui().selectedTextOf("limelight-upload-file")));

        screen().enter("limelight-upload-server", server);
        assertEquals(
                "Uploads go to " + server + ".",
                screen().await(
                                "limelight-upload-server-status",
                                "the server to be checked",
                                text ->
                                        text.startsWith("Uploads go to")
                                                || text.startsWith("This server"),
                                UPLOAD_BOUND));
        screen().enter("limelight-upload-project", project);
        assertEquals(
                "Project " + project + ".",
                screen().await(
                                "limelight-upload-project-status",
                                "the project to be checked",
                                text ->
                                        text.startsWith("Project ")
                                                || text.startsWith("This project"),
                                UPLOAD_BOUND));
        screen().ui().typeInto("limelight-upload-key", controlled.key());
        screen().ui().pressKey("ENTER");
        assertAll(
                "the key typed, committed, and not shown",
                () -> assertEquals(KEY_TYPED, screen().ui().textOf("limelight-upload-key-status")),
                () -> assertEquals("", screen().ui().textOf("limelight-upload-key")));

        screen().ui().clickOn("limelight-upload-prepare");
        assertEquals(
                NOTHING_SENT_YET,
                screen().await(
                                "limelight-upload-outcome",
                                "the destination to be shown",
                                text ->
                                        text.equals(NOTHING_SENT_YET)
                                                || text.startsWith(
                                                        "The destination was not accepted")
                                                || text.startsWith("Nothing was checked"),
                                UPLOAD_BOUND));
        long size = Files.size(converted);
        assertAll(
                "step 27: the destination, shown before anything is sent",
                () -> assertEquals(List.of(), controlled.uploads(), "Upload sent no file"),
                () -> assertEquals(server, screen().ui().textOf("limelight-upload-confirm-server")),
                () ->
                        assertEquals(
                                project, screen().ui().textOf("limelight-upload-confirm-project")),
                () -> assertEquals(page, screen().ui().textOf("limelight-upload-confirm-page")),
                () ->
                        assertEquals(
                                converted.toString(),
                                screen().ui().textOf("limelight-upload-confirm-file")),
                () ->
                        assertEquals(
                                size + " bytes",
                                screen().ui().textOf("limelight-upload-confirm-size")),
                () ->
                        assertEquals(
                                convertedSha256,
                                screen().ui().textOf("limelight-upload-confirm-sha256")));

        screen().ui().clickOn("limelight-upload-send");
        String ended =
                screen().await(
                                "limelight-upload-outcome",
                                "the upload to end",
                                text ->
                                        !text.startsWith("Sending ")
                                                && !text.equals(NOTHING_SENT_YET),
                                UPLOAD_BOUND);
        List<LimelightEndpoint.Received> received = controlled.uploads();
        assertEquals(1, received.size(), () -> "one file arrived: " + received + "; " + ended);
        LimelightEndpoint.Received upload = received.get(0);
        List<Map<String, Object>> uploaded = events("limelight.uploaded");
        assertAll(
                "step 27: the endpoint received exactly the converted file, valid, and queued it",
                () ->
                        assertTrue(
                                ended.startsWith(
                                        "Submitted to Limelight: the server queued the import of "
                                                + converted
                                                + " into project "
                                                + project
                                                + " on "
                                                + server
                                                + ". Its tracking id (uploadKey) is "
                                                + upload.trackingId()
                                                + "."),
                                ended),
                () -> assertEquals(convertedSha256, upload.sha256(), "the bytes that arrived"),
                () -> assertEquals(size, upload.bytes(), "all of them"),
                () -> assertEquals("VALID", upload.verdict(), "valid against the converter XSD"),
                () -> assertTrue(upload.submitted(), "the final submit queued the import"),
                () -> assertFalse(upload.importerSubDir().isEmpty(), "the import's directory"),
                () -> assertEquals(List.of(), controlled.violations(), "the wire protocol"),
                () ->
                        assertEquals(
                                upload.trackingId(),
                                screen().ui().textOf("limelight-upload-tracking-id"),
                                "the endpoint's own tracking id, on screen"),
                () ->
                        assertEquals(
                                upload.importerSubDir(),
                                screen().ui().textOf("limelight-upload-importer-subdir")),
                () -> assertEquals(1, uploaded.size(), "one limelight.uploaded event"),
                () -> assertEquals(convertedSha256, uploaded.get(0).get("upload.file.sha256")),
                () ->
                        assertEquals(
                                upload.trackingId(), uploaded.get(0).get("limelight.upload-key")),
                () -> assertEquals(project, uploaded.get(0).get("limelight.project-id")),
                () -> assertEquals(server, uploaded.get(0).get("limelight.server")));
        observe(
                "upload: "
                        + size
                        + " bytes to "
                        + server
                        + ", project "
                        + project
                        + ", tracking id "
                        + upload.trackingId()
                        + ", verdict "
                        + upload.verdict()
                        + ", "
                        + controlled.requests()
                        + " requests");
    }

    // ==================================================================== step 28 ====

    /**
     * Step 28: the Provenance view opens the run, and the sealed record -- read by the test's own
     * JSON reader -- is proved against the disk: MD5 and SHA-256 recomputed for every declared
     * input and output, every output under {@code outputs/} declared (or, written after the seal,
     * named by its own event with its digest), every tool binary and JAR the run used hashed again,
     * exact versions against each install's own marker, every argument array present and naming
     * what the run used; the section shows the record's files and digests; an export is
     * byte-identical; and the test key is in no file under the fresh home or the test's own work
     * directory, in any encoding.
     *
     * @throws IOException if a file cannot be read
     */
    void provenanceHoldsAgainstTheDisk() throws IOException {
        Path recordFile = run().resolve("provenance/provenance.json");
        Map<String, Object> record =
                TestJson.object(Files.readString(recordFile, StandardCharsets.UTF_8));

        // -- the section opens the run
        screen().show("provenance");
        String label =
                awaitItem(
                        "provenance-run",
                        item -> item.startsWith(runId() + " -- created "),
                        "the run list to offer run " + runId());
        if (!screen().ui().selectedTextOf("provenance-run").equals(label)) {
            screen().ui().select("provenance-run", label);
        }
        screen().await(
                        "provenance-record",
                        "the record to be opened",
                        text -> text.startsWith("Sealed record"),
                        SECTION_BOUND);
        assertTrue(label.endsWith(", completed, sealed record"), label);

        // -- every declared file, recomputed
        List<Map<String, Object>> files = objects(record.get("files"));
        List<String> wrong = new ArrayList<>();
        Set<String> declaredOutputs = new TreeSet<>();
        Set<String> declaredInputs = new TreeSet<>();
        int inputs = 0;
        int outputs = 0;
        for (Map<String, Object> file : files) {
            String path = text(file, "path");
            String direction = text(file, "direction");
            if ("output".equals(direction)) {
                declaredOutputs.add(path);
                outputs++;
            } else if ("input".equals(direction)) {
                declaredInputs.add(path);
                inputs++;
            } else {
                wrong.add(path + ": direction " + direction);
            }
            Path onDisk = Path.of(path);
            if (!Files.isRegularFile(onDisk, LinkOption.NOFOLLOW_LINKS)) {
                wrong.add(path + ": declared and not a file on disk");
                continue;
            }
            OwnDigests own = OwnDigests.of(onDisk);
            compare(wrong, path + " MD5", own.md5(), file.get("md5"), MD5);
            compare(wrong, path + " SHA-256", own.sha256(), file.get("sha256"), SHA256);
            if (!new BigDecimal(own.size()).equals(file.get("sizeBytes"))) {
                wrong.add(path + ": size " + own.size() + ", recorded " + file.get("sizeBytes"));
            }
            if (!"completed".equals(file.get("status"))) {
                wrong.add(path + ": status " + file.get("status"));
            }
        }

        // -- every output on disk declared; what came after the seal named by its event
        Map<String, String> afterTheSeal = new TreeMap<>();
        for (Map<String, Object> event : events("limelight.converted")) {
            afterTheSeal.put(text(event, "output.path"), text(event, "output.sha256"));
        }
        List<Path> onDisk = regularFiles(run().resolve("outputs"));
        for (Path file : onDisk) {
            if (declaredOutputs.contains(file.toString())) {
                continue;
            }
            String sha = afterTheSeal.get(file.toString());
            if (sha == null) {
                wrong.add(file + ": under outputs/, and neither declared nor named by an event");
            } else if (!sha.equals(OwnDigests.of(file).sha256())) {
                wrong.add(file + ": its limelight.converted event's SHA-256 is not the file's");
            }
        }
        for (Path spectrum : layout().spectra()) {
            requireDeclared(wrong, declaredInputs, spectrum, "an input");
        }
        requireDeclared(wrong, declaredInputs, layout().database(), "an input");
        requireDeclared(
                wrong, declaredOutputs, run().resolve("parameters/comet.params"), "an output");
        requireDeclared(
                wrong, declaredOutputs, run().resolve("inputs/pin/merged.pin"), "an output");
        assertEquals(
                List.of(),
                wrong,
                "step 28: the record's files against the disk (" + files.size() + ")");

        // -- tools: binaries and JARs, versions, argument arrays
        List<String> toolProblems = new ArrayList<>();
        List<Map<String, Object>> tools = objects(record.get("tools"));
        Map<String, List<String>> reportCommands =
                commandsInTheReport(run().resolve("provenance/provenance.rst"));
        Map<String, Map<String, Object>> invokedByStage = new TreeMap<>();
        for (Map<String, Object> invoked : events("tool.invoked")) {
            invokedByStage.put(text(invoked, "stage"), invoked);
        }
        Map<String, Integer> invocations = new TreeMap<>();
        for (Map<String, Object> tool : tools) {
            String name = text(tool, "name");
            invocations.merge(name, 1, Integer::sum);
            Path executable = Path.of(text(tool, "executablePath"));
            OwnDigests own = OwnDigests.of(executable);
            compare(toolProblems, name + " MD5", own.md5(), tool.get("md5"), MD5);
            compare(toolProblems, name + " SHA-256", own.sha256(), tool.get("sha256"), SHA256);
            Map<String, Object> marker = markerOf(executable);
            if (!text(marker, "version").equals(text(tool, "version"))) {
                toolProblems.add(
                        name
                                + ": version "
                                + tool.get("version")
                                + ", its install says "
                                + marker.get("version"));
            }
            Map<String, Object> execution = object(tool.get("execution"));
            List<String> argv = strings(execution.get("argv"));
            String stage = text(tool, "stageId");
            if (argv.isEmpty() || !argv.get(0).equals(executable.toString())) {
                toolProblems.add(name + ": the argument array does not start with it: " + argv);
            }
            if (!argv.equals(reportCommands.get(stage))) {
                toolProblems.add(
                        stage
                                + ": the record's argument array "
                                + argv
                                + " is not provenance.rst's command "
                                + reportCommands.get(stage));
            }
            Map<String, Object> invoked = invokedByStage.get(stage);
            if (invoked == null
                    || !name.equals(invoked.get("tool"))
                    || !text(tool, "version").equals(invoked.get("tool.version"))
                    || !String.valueOf(execution.get("exitCode")).equals(invoked.get("exit"))) {
                toolProblems.add(stage + ": its tool.invoked event says otherwise: " + invoked);
            }
            String banner = bannerOf(name, text(tool, "version"));
            Path log = Path.of(text(object(execution.get("stdout")), "path"));
            if (!Files.readString(log, StandardCharsets.UTF_8).contains(banner)) {
                toolProblems.add(stage + ": its log " + log + " does not say " + banner);
            }
            toolProblems.addAll(
                    argumentProblems(name, stage, argv, declaredInputs, declaredOutputs));
        }
        Map<String, Integer> expectedInvocations = new TreeMap<>();
        expectedInvocations.put("comet", layout().spectra().size());
        expectedInvocations.put("percolator", 1);
        String cometVersion = versionOf(tools, "comet");
        String percolatorVersion = versionOf(tools, "percolator");
        assertAll(
                "step 28: the tools the record names, against the files on disk",
                () -> assertEquals(List.of(), toolProblems),
                () -> assertEquals(expectedInvocations, invocations, "the tool executions"),
                () -> assertEquals(CanonicalScenario.DEFAULT_COMET, cometVersion),
                () -> assertEquals(scenario.percolatorVersion(), percolatorVersion),
                () ->
                        assertEquals(
                                scenario.cometExecutable().toString(),
                                executableOf(tools, "comet")),
                () ->
                        assertEquals(
                                scenario.percolatorExecutable().toString(),
                                executableOf(tools, "percolator")));
        // the converter and PDV, recorded after the seal in their own events
        OwnDigests converterOwn = OwnDigests.of(converterJar);
        OwnDigests pdvOwn = OwnDigests.of(pdvJar);
        Map<String, Object> conversion = events("limelight.converted").get(0);
        Map<String, Object> launch = events("pdv.launched").get(0);
        assertAll(
                "step 28: the converter's and PDV's JARs, as their events record them",
                () -> assertEquals(converterOwn.sha256(), conversion.get("converter.jar.sha256")),
                () -> assertEquals(converterOwn.md5(), conversion.get("converter.jar.md5")),
                () ->
                        assertEquals(
                                text(markerOf(converterJar), "version"),
                                conversion.get("tool.version")),
                () -> assertEquals(pdvOwn.sha256(), launch.get("pdv.jar.sha256")),
                () -> assertEquals(text(markerOf(pdvJar), "version"), launch.get("tool.version")),
                () ->
                        assertTrue(
                                argv(launch).contains(pdvJar.toString()), () -> "" + argv(launch)));

        // -- what the section shows
        List<String> fileRows = screen().ui().rowsOf("provenance-files");
        List<String> toolRows = screen().ui().rowsOf("provenance-tools");
        List<String> unshown = new ArrayList<>();
        for (Map<String, Object> file : files) {
            String wanted = "\nPath: " + text(file, "path") + "\n";
            String md5 = "\nMD5: " + text(file, "md5") + "\n";
            String sha = "\nSHA-256: " + text(file, "sha256");
            String heading = text(file, "direction") + " " + text(file, "role") + "\n";
            if (fileRows.stream()
                    .noneMatch(
                            row ->
                                    row.startsWith(heading)
                                            && row.contains(wanted)
                                            && row.contains(md5)
                                            && row.endsWith(sha))) {
                unshown.add(heading.strip() + " " + text(file, "path"));
            }
        }
        for (Map<String, Object> tool : tools) {
            String path = "\nPath: " + text(tool, "executablePath") + "\n";
            String md5 = "\nMD5: " + text(tool, "md5") + "\n";
            String sha = "\nSHA-256: " + text(tool, "sha256") + "\n";
            String heading = ". " + text(tool, "name") + " " + text(tool, "version") + " -- ";
            if (toolRows.stream()
                    .noneMatch(
                            row ->
                                    row.contains(heading)
                                            && row.contains(path)
                                            && row.contains(md5)
                                            && row.contains(sha))) {
                unshown.add("tool " + text(tool, "name") + " " + text(tool, "executablePath"));
            }
        }
        String summary = screen().ui().textOf("provenance-summary");
        int shownInputs = inputs;
        int shownOutputs = outputs;
        assertAll(
                "step 28: the Provenance section shows the record's files, tools and digests",
                () -> assertEquals(files.size(), fileRows.size(), "one row per declared file"),
                () -> assertEquals(tools.size(), toolRows.size(), "one row per tool execution"),
                () -> assertEquals(List.of(), unshown, "declared and not shown as recorded"),
                () ->
                        assertTrue(
                                summary.contains(
                                        "\nInput files: "
                                                + shownInputs
                                                + "; output files: "
                                                + shownOutputs
                                                + "\n"),
                                summary),
                () -> assertTrue(summary.contains("\nStatus: completed\n"), summary));

        // -- an export, byte for byte
        exported = layout().work().resolve("exports/provenance-" + runId() + ".json");
        Files.createDirectories(Objects.requireNonNull(exported.getParent(), "export directory"));
        scenario.chooser().nextExportTarget(exported);
        screen().ui().clickOn("provenance-export-json");
        screen().await(
                        "provenance-export-status",
                        "the export to be written",
                        text -> text.equals("Exported provenance JSON to " + exported + "."),
                        SECTION_BOUND);
        assertArrayEquals(
                Files.readAllBytes(recordFile),
                Files.readAllBytes(exported),
                "the export is the run's provenance.json, byte for byte");

        // -- no secret anywhere
        List<SecretSweep.Form> forms = SecretSweep.forms(endpoint.key());
        List<SecretSweep.Walk> walks =
                List.of(
                        SecretSweep.walk(layout().home().root(), forms, Optional.empty(), Set.of()),
                        SecretSweep.walk(layout().work(), forms, Optional.empty(), Set.of()));
        List<String> hits = new ArrayList<>();
        int searched = 0;
        for (SecretSweep.Walk walk : walks) {
            hits.addAll(walk.hits());
            searched += walk.searched();
        }
        int swept = searched;
        assertAll(
                "step 28: the test key is in no file",
                () -> assertEquals(List.of(), hits, "files holding the key in some encoding"),
                () ->
                        assertTrue(
                                walks.get(0).searchedFiles().contains(recordFile)
                                        && walks.get(1).searchedFiles().contains(exported),
                                "the record and the export were searched"),
                () -> assertTrue(forms.size() >= 15, () -> "encodings: " + forms.size()),
                () -> assertTrue(swept > 50, () -> "files searched: " + swept));
        observe(
                "provenance: "
                        + files.size()
                        + " declared files recomputed ("
                        + inputs
                        + " inputs, "
                        + outputs
                        + " outputs), "
                        + onDisk.size()
                        + " files under outputs/, "
                        + tools.size()
                        + " tool executions "
                        + invocations
                        + "; key swept in "
                        + forms.size()
                        + " encodings over "
                        + swept
                        + " files ("
                        + walks.get(0).summary()
                        + "; "
                        + walks.get(1).summary()
                        + ")");
    }

    /**
     * The version banner a tool prints, as its log on disk must hold it: Comet's {@code Comet
     * version "2026.03 rev. 0} for release {@code 2026.03.0}, Percolator's {@code Percolator
     * version 3.07.1,} -- the exact version, as the binary itself states it.
     */
    private static String bannerOf(String name, String version) {
        if ("comet".equals(name)) {
            int last = version.lastIndexOf('.');
            return "Comet version \""
                    + version.substring(0, last)
                    + " rev. "
                    + version.substring(last + 1)
                    + " (";
        }
        return "Percolator version " + version + ",";
    }

    /**
     * What a tool's argument array must say, typed out here: Comet's is exactly the installed
     * executable, the archived {@code comet.params}, one bare {@code -N} base (Comet runs in the
     * run's {@code outputs/comet}) and one spectrum file, all declared; Percolator's names the
     * merged PIN last, as a declared input, and writes each of its tables, its weights and its XML
     * to a declared output.
     */
    private List<String> argumentProblems(
            String name,
            String stage,
            List<String> argv,
            Set<String> declaredInputs,
            Set<String> declaredOutputs) {
        List<String> problems = new ArrayList<>();
        if ("comet".equals(name)) {
            String spectrum = argv.get(argv.size() - 1);
            String stem =
                    String.valueOf(Path.of(spectrum).getFileName()).replaceFirst("\\.[^.]*$", "");
            List<String> expected =
                    List.of(
                            scenario.cometExecutable().toString(),
                            "-P" + run().resolve("parameters/comet.params"),
                            "-N" + stem,
                            spectrum);
            if (!expected.equals(argv) || !declaredInputs.contains(spectrum)) {
                problems.add(stage + ": Comet's argument array " + argv + ", not " + expected);
            }
            return problems;
        }
        if (!declaredInputs.contains(argv.get(argv.size() - 1))
                || !argv.get(argv.size() - 1)
                        .equals(run().resolve("inputs/pin/merged.pin").toString())) {
            problems.add(
                    stage + ": Percolator does not read the declared merged PIN last: " + argv);
        }
        for (String flag :
                List.of(
                        "--results-psms",
                        "--results-peptides",
                        "--decoy-results-psms",
                        "--decoy-results-peptides",
                        "--weights",
                        "-X")) {
            int at = argv.indexOf(flag);
            if (at < 0 || at + 1 >= argv.size() || !declaredOutputs.contains(argv.get(at + 1))) {
                problems.add(stage + ": " + flag + " does not name a declared output: " + argv);
            }
        }
        return problems;
    }

    /**
     * Each tool execution's command in {@code provenance.rst}, by stage, read by the test's own
     * walk of the report's {@code :Stage:} and {@code :Command:} fields and the bulleted, literal
     * arguments under it.
     */
    private static Map<String, List<String>> commandsInTheReport(Path report) throws IOException {
        Map<String, List<String>> commands = new TreeMap<>();
        String stage = null;
        List<String> command = null;
        for (String line : Files.readAllLines(report, StandardCharsets.UTF_8)) {
            if (command != null) {
                if (line.startsWith("   * ``") && line.endsWith("``")) {
                    command.add(line.substring(7, line.length() - 2));
                    continue;
                }
                commands.put(stage, command);
                command = null;
            }
            if (line.startsWith(":Stage: ``") && line.endsWith("``")) {
                stage = line.substring(10, line.length() - 2);
            } else if (":Command:".equals(line) && stage != null) {
                command = new ArrayList<>();
            }
        }
        if (command != null) {
            commands.put(stage, command);
        }
        return commands;
    }

    private static void requireDeclared(
            List<String> wrong, Set<String> declared, Path file, String as) {
        if (!declared.contains(file.toString())) {
            wrong.add(file + ": not declared as " + as);
        }
    }

    private static void compare(
            List<String> wrong, String what, String own, Object recorded, Pattern form) {
        if (!(recorded instanceof String text) || !form.matcher(text).matches()) {
            wrong.add(what + ": recorded " + recorded + ", not a digest");
        } else if (!own.equals(text)) {
            wrong.add(what + ": recomputed " + own + ", recorded " + text);
        }
    }

    private static String versionOf(List<Map<String, Object>> tools, String name) {
        Set<String> versions = new TreeSet<>();
        for (Map<String, Object> tool : tools) {
            if (name.equals(tool.get("name"))) {
                versions.add(text(tool, "version"));
            }
        }
        return versions.size() == 1 ? versions.iterator().next() : versions.toString();
    }

    private static String executableOf(List<Map<String, Object>> tools, String name) {
        Set<String> paths = new TreeSet<>();
        for (Map<String, Object> tool : tools) {
            if (name.equals(tool.get("name"))) {
                paths.add(text(tool, "executablePath"));
            }
        }
        return paths.size() == 1 ? paths.iterator().next() : paths.toString();
    }

    /** The completion marker of the install that holds a file: found by walking up from it. */
    private Map<String, Object> markerOf(Path file) throws IOException {
        for (Path at = file.getParent(); at != null; at = at.getParent()) {
            if (!at.startsWith(layout().toolCache())) {
                break;
            }
            Path marker = at.resolve(".cometgui-install.json");
            if (Files.isRegularFile(marker)) {
                return TestJson.object(Files.readString(marker, StandardCharsets.UTF_8));
            }
        }
        throw new AssertionError(
                file + " is in no install of the tool cache " + layout().toolCache());
    }

    // =============================================================== steps 29 and 30 ====

    /** What was on screen and on disk when the application was closed. */
    private record Closed(
            String resultsRun,
            String psmFilter,
            String peptideFilter,
            List<String> psmCounts,
            List<String> peptideCounts,
            String summary,
            List<String> toolRows,
            List<String> fileRows,
            DirectorySnapshot runDirectory,
            String provenanceSha256,
            Map<String, String> toolStates) {}

    /**
     * Before step 29's close: what the Results and Provenance sections show for the run, and a
     * snapshot of the run directory and its record, so that the relaunched application can be held
     * to them.
     *
     * @throws IOException if the run directory cannot be snapshotted
     */
    void recordWhatIsShownBeforeTheClose() throws IOException {
        screen().show("results");
        scenario.showTable("Target PSMs", screen().ui().textOf("results-psm-filter"));
        String resultsRun = screen().ui().selectedTextOf("results-run");
        String psm = screen().ui().textOf("results-psm-filter");
        String peptide = screen().ui().textOf("results-peptide-filter");
        List<String> counts = scenario.shownCounts();
        scenario.showTable("Target peptides", peptide);
        List<String> peptideCounts = scenario.shownCounts();
        scenario.showTable("Target PSMs", psm);
        screen().show("provenance");
        String summary = screen().ui().textOf("provenance-summary");
        List<String> tools = screen().ui().rowsOf("provenance-tools");
        List<String> files = screen().ui().rowsOf("provenance-files");
        screen().show("tool-manager");
        Map<String, String> states = toolStates();
        beforeTheClose =
                new Closed(
                        resultsRun,
                        psm,
                        peptide,
                        counts,
                        peptideCounts,
                        summary,
                        tools,
                        files,
                        DirectorySnapshot.of(run()),
                        OwnDigests.of(run().resolve("provenance/provenance.json")).sha256(),
                        states);
        observe(
                "before the close: Results "
                        + resultsRun
                        + ", filters PSM "
                        + psm
                        + " / peptide "
                        + peptide
                        + ", PSM counts "
                        + counts
                        + ", peptide counts "
                        + peptideCounts
                        + "; tools "
                        + states);
    }

    private Map<String, String> toolStates() {
        Map<String, String> states = new LinkedHashMap<>();
        for (Artefact artefact :
                List.of(
                        scenario.manifest()
                                .artefact(
                                        "comet",
                                        CanonicalScenario.DEFAULT_COMET,
                                        scenario.platform()),
                        scenario.manifest().newestXmlCapablePercolator(scenario.platform()),
                        scenario.manifest().onlyArtefact("pdv", scenario.platform()),
                        scenario.manifest()
                                .onlyArtefact("limelight-converter", scenario.platform()))) {
            String row = CanonicalScenario.rowOf(artefact);
            states.put(
                    artefact.tool() + " " + artefact.version(),
                    screen().ui().textOf(row + "-state"));
        }
        return states;
    }

    /**
     * Steps 29 and 30, once the tier has closed the application through its own stop and started a
     * new one over a new composition root from the same home, and attached the scenario to it: the
     * new application is ready, finds the same project with the same one run, every tool still
     * installed, and shows coherent results, the selected run, tool versions, q-filter state and
     * provenance -- the run directory and its record untouched by the close and the relaunch.
     *
     * @throws IOException if the run directory cannot be read
     */
    void theRelaunchedApplicationIsCoherent() throws IOException {
        Closed closed = beforeTheClose;
        screen().show("tool-manager");
        String builds = scenario.manifest().toolBuilds() + " tool builds on this host.";
        screen().await(
                        "tool-manager-summary",
                        "the relaunched Tool Manager to read its list",
                        builds::equals,
                        CanonicalScenario.READY_BOUND);
        Map<String, String> states = toolStates();
        List<Path> runs;
        try (Stream<Path> listed = Files.list(layout().projectDirectory().resolve("runs"))) {
            runs = listed.toList();
        }
        screen().show("run");
        String engine =
                screen().await(
                                "run-engine",
                                "the relaunched Run section's pre-run check",
                                CanonicalScenario.RUN_AFTER_THE_INSTALLS::equals,
                                CanonicalScenario.CHECK_BOUND);
        assertAll(
                "step 30: the same project, reopened and locked again, the same run, every tool"
                        + " still installed",
                () -> assertEquals(closed.toolStates(), states, "the Tool Manager's rows"),
                () -> assertEquals(List.of(run()), runs, "the project still holds exactly the run"),
                () ->
                        assertTrue(
                                Files.isRegularFile(
                                        layout().projectDirectory().resolve("project.json")),
                                "the same project.json"),
                () ->
                        assertEquals(
                                "Run state: Not started",
                                screen().ui().textOf("stage-stepper-run-state"),
                                "nothing was run by the relaunch: " + engine));

        screen().show("results");
        screen().await(
                        "results-readiness",
                        "the relaunched Results section to open the run",
                        text -> text.startsWith("Showing the results of run " + runId() + ","),
                        CanonicalScenario.QUERY_BOUND);
        String psm = screen().ui().textOf("results-psm-filter");
        String peptide = screen().ui().textOf("results-peptide-filter");
        String resultsRun = screen().ui().selectedTextOf("results-run");
        scenario.showTable("Target PSMs", psm);
        List<String> psmCounts = scenario.shownCounts();
        scenario.showTable("Target peptides", peptide);
        List<String> peptideCounts = scenario.shownCounts();
        scenario.showTable("Target PSMs", psm);
        List<String> psmOracle = scenario.oracle("psms.tsv", psm);
        List<String> peptideOracle = scenario.oracle("peptides.tsv", peptide);
        Map<String, Object> viewState =
                TestJson.object(
                        Files.readString(
                                run().resolve("results/view-state.json"), StandardCharsets.UTF_8));
        assertAll(
                "step 30: the results, the selected run and the q filters",
                () -> assertEquals(closed.psmFilter(), viewState.get("psmQValueFilter")),
                () -> assertEquals(closed.peptideFilter(), viewState.get("peptideQValueFilter")),
                () -> assertEquals(closed.resultsRun(), resultsRun, "the selected run"),
                () -> assertEquals(closed.psmFilter(), psm, "the PSM filter"),
                () -> assertEquals(closed.peptideFilter(), peptide, "the peptide filter"),
                () -> assertEquals(psmOracle, psmCounts, "the PSM counts, by the raw table"),
                () -> assertEquals(peptideOracle, peptideCounts, "the peptide counts, likewise"),
                () -> assertEquals(closed.psmCounts(), psmCounts, "as shown before the close"),
                () -> assertEquals(closed.peptideCounts(), peptideCounts, "likewise"));
        observe(
                "after the relaunch: Results "
                        + resultsRun
                        + ", filters PSM "
                        + psm
                        + " / peptide "
                        + peptide
                        + ", PSM counts "
                        + psmCounts
                        + ", peptide counts "
                        + peptideCounts
                        + "; tools "
                        + states);

        screen().show("provenance");
        String label =
                awaitItem(
                        "provenance-run",
                        item -> item.startsWith(runId() + " -- created "),
                        "the relaunched run list to offer run " + runId());
        if (!screen().ui().selectedTextOf("provenance-run").equals(label)) {
            screen().ui().select("provenance-run", label);
        }
        screen().await(
                        "provenance-record",
                        "the record to be opened",
                        text -> text.startsWith("Sealed record"),
                        SECTION_BOUND);
        assertAll(
                "step 30: the provenance shown, and on disk, as before the close",
                () -> assertEquals(closed.summary(), screen().ui().textOf("provenance-summary")),
                () -> assertEquals(closed.toolRows(), screen().ui().rowsOf("provenance-tools")),
                () -> assertEquals(closed.fileRows(), screen().ui().rowsOf("provenance-files")),
                () ->
                        assertEquals(
                                closed.provenanceSha256(),
                                OwnDigests.of(run().resolve("provenance/provenance.json")).sha256(),
                                "the record is unchanged"));
        DirectorySnapshot.of(run())
                .assertUnchangedIn(
                        closed.runDirectory(), "the close and the relaunch left the run");
    }

    // ================================================================== internals ====

    /** Waits until a combo box offers an item that satisfies a condition, and returns it. */
    private String awaitItem(String id, Predicate<String> wanted, String what) {
        return Await.until(
                what + " (#" + id + ")",
                () -> screen().ui().itemsOf(id).stream().filter(wanted).findFirst().orElse(""),
                item -> !item.isEmpty(),
                SECTION_BOUND);
    }

    /** What a Tool Manager row says, part by part, for a failure's message. */
    private String rowTexts(String row) {
        screen().show("tool-manager");
        List<String> texts = new ArrayList<>();
        for (String part : List.of("state", "diagnostic", "progress", "path", "download")) {
            String id = row + "-" + part;
            texts.add(
                    part + "=" + (screen().ui().exists(id) ? screen().ui().textOf(id) : "(none)"));
        }
        return String.join("; ", texts);
    }

    /** Every file under the fresh home whose lower-case name matches, sorted. */
    private List<Path> toolFiles(String regex) throws IOException {
        Pattern pattern = Pattern.compile(regex);
        try (Stream<Path> walk = Files.walk(layout().home().home())) {
            return walk.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(
                            path ->
                                    pattern.matcher(
                                                    String.valueOf(path.getFileName())
                                                            .toLowerCase(Locale.ROOT))
                                            .matches())
                    .sorted()
                    .toList();
        }
    }

    private static List<Path> regularFiles(Path root) throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .sorted()
                    .toList();
        }
    }

    /** The payloads of the run's events of one type, oldest first, read with the test's JSON. */
    List<Map<String, Object>> events(String type) throws IOException {
        List<Map<String, Object>> found = new ArrayList<>();
        for (String line :
                Files.readAllLines(
                        run().resolve("provenance/events.log"), StandardCharsets.UTF_8)) {
            if (line.isBlank()) {
                continue;
            }
            Map<String, Object> event = TestJson.object(line);
            if (type.equals(event.get("type"))) {
                found.add(object(event.get("payload")));
            }
        }
        return found;
    }

    private static List<String> argv(Map<String, Object> payload) {
        int count = Integer.parseInt(String.valueOf(payload.get("argv.count")));
        List<String> argv = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            argv.add(String.valueOf(payload.get("argv." + index)));
        }
        return argv;
    }

    private static String text(Map<String, Object> object, String name) {
        Object value = object.get(name);
        if (!(value instanceof String string)) {
            throw new AssertionError("no text " + name + " in " + object.keySet());
        }
        return string;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value) {
        if (!(value instanceof Map<?, ?>)) {
            throw new AssertionError("not a JSON object: " + value);
        }
        return (Map<String, Object>) value;
    }

    private static List<Map<String, Object>> objects(Object value) {
        if (!(value instanceof List<?> listed)) {
            throw new AssertionError("not a JSON array: " + value);
        }
        List<Map<String, Object>> found = new ArrayList<>();
        for (Object item : listed) {
            found.add(object(item));
        }
        return found;
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
