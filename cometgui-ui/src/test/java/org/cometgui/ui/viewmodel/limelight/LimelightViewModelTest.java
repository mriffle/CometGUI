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

package org.cometgui.ui.viewmodel.limelight;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.beans.property.SimpleObjectProperty;
import org.cometgui.domain.run.AttemptOutcome;
import org.cometgui.domain.run.IndexMode;
import org.cometgui.domain.run.RunId;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.domain.tools.InstallPhase;
import org.cometgui.domain.tools.InstallProgress;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.percolator.PercolatorSettings;
import org.cometgui.params.percolator.resolution.CompatibilityAction;
import org.cometgui.params.percolator.resolution.DownstreamStage;
import org.cometgui.provenance.hashing.StreamingHashService;
import org.cometgui.provenance.manifest.ApplicationRecord;
import org.cometgui.provenance.manifest.ProvenanceManifest;
import org.cometgui.provenance.manifest.ProvenanceStatus;
import org.cometgui.provenance.manifest.RunRecord;
import org.cometgui.results.export.TableExport;
import org.cometgui.results.export.WeightsExport;
import org.cometgui.results.filtering.DisplayFilters;
import org.cometgui.results.filtering.QValueFilter;
import org.cometgui.results.filtering.store.Category;
import org.cometgui.results.filtering.store.ResultStore;
import org.cometgui.results.filtering.store.ResultStores;
import org.cometgui.results.filtering.store.TableKind;
import org.cometgui.results.parser.WeightsSummary;
import org.cometgui.tools.limelight.LimelightQValueCutoff;
import org.cometgui.ui.testing.Percolators;
import org.cometgui.ui.testing.ScriptedLimelight;
import org.cometgui.ui.testing.ToolOffers;
import org.cometgui.ui.viewmodel.params.PercolatorRequest;
import org.cometgui.ui.viewmodel.params.RunNotStartedException;
import org.cometgui.ui.viewmodel.results.DisplayFiltersViewModel;
import org.cometgui.ui.viewmodel.results.OpenedResults;
import org.cometgui.ui.viewmodel.results.ResultTableViewModel;
import org.cometgui.ui.viewmodel.results.ResultsPort;
import org.cometgui.ui.viewmodel.results.ResultsRun;
import org.cometgui.ui.viewmodel.results.ResultsViewModel;
import org.cometgui.ui.viewmodel.results.WeightsViewModel;
import org.cometgui.workflow.engine.RunResult;
import org.cometgui.workflow.engine.StepTransition;
import org.cometgui.workflow.limelight.LimelightCompatibility;
import org.cometgui.workflow.limelight.LimelightConversionRefusedException;
import org.cometgui.workflow.limelight.LimelightConverterStatus;
import org.cometgui.workflow.limelight.LimelightPrerequisite;
import org.cometgui.workflow.limelight.LimelightReadiness;
import org.cometgui.workflow.limelight.LimelightRefusal;
import org.cometgui.workflow.state.EngineStep;
import org.cometgui.workflow.state.Plan;
import org.cometgui.workflow.state.RunState;
import org.cometgui.workflow.state.StepState;
import org.cometgui.workflow.steps.PercolatorRerun;
import org.cometgui.workflow.storage.LimelightSettings;
import org.cometgui.workflow.storage.ViewStateStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The Limelight section's conversion half over a scripted port, with the Results section real:
 * compatibility first (gate item 3) -- every conversion control disabled and the workflow's
 * explanation shown before anything can be attempted, the action labelled exactly as the workflow's
 * {@link CompatibilityAction} labels it, both variants; the settings saved through the port with
 * every refusal shown; decoy import refused at configuration time (gate item 8); the cutoff
 * independent of the display filters (gate item 5); the converter installed on first use; the
 * conversion's live log and result; and the compatible rerun, its install first and its derived run
 * chosen in the Results section.
 */
class LimelightViewModelTest {

    private static final String RUN = "run-1";

    private static final Instant CREATED = Instant.parse("2026-10-12T08:00:00Z");

    private static final ToolVersion CONVERTER = ToolVersion.parse("2.8.1");

    private static final Path JAR = Path.of("cache/limelight/converter.jar").toAbsolutePath();

    private static final String NO_DECOYS =
            "Limelight decoy import is not available for this run: its Percolator XML holds no"
                    + " decoy PSMs.";

    private static final String NO_XML =
            "run run-1's Percolator 3.09 (registered local binary) wrote no Percolator XML.";

    @TempDir private Path work;

    private final Queue background = new Queue();

    private final Queue ui = new Queue();

    private final List<String> ended = new ArrayList<>();

    private final AtomicInteger registrations = new AtomicInteger();

    private boolean registers = true;

    private ScriptedLimelight port;

    private Runs runs;

    private DisplayFiltersViewModel filters;

    private ResultsViewModel results;

    private SimpleObjectProperty<PercolatorRequest> percolator;

    private LimelightViewModel section;

    @BeforeEach
    void build() throws IOException {
        port = new ScriptedLimelight("no Limelight yet");
        Path psms =
                Files.writeString(
                        work.resolve("psms.tsv"),
                        "PSMId\tscore\tq-value\tposterior_error_prob\tpeptide\tproteinIds\n"
                                + "a_101_2_1\t1.5\t0.001\t0.0001\tK.PEPTIDEA.R\tsp|P1|A\n",
                        StandardCharsets.UTF_8);
        runs = new Runs(work, psms);
        filters = new DisplayFiltersViewModel();
        ResultTableViewModel table =
                new ResultTableViewModel(filters, Runnable::run, Runnable::run);
        results =
                new ResultsViewModel(
                        runs, filters, table, new WeightsViewModel(), Runnable::run, Runnable::run);
        percolator = new SimpleObjectProperty<>(Percolators.ready());
        section =
                new LimelightViewModel(
                        port,
                        results,
                        percolator,
                        () -> {
                            registrations.incrementAndGet();
                            return registers;
                        },
                        background,
                        ui,
                        () -> ended.add("ended"));
    }

    @AfterEach
    void close() {
        results.close();
    }

    // ----------------------------------------------------------------------------- fixtures --

    private void settle() {
        while (background.pending() + ui.pending() > 0) {
            background.drain();
            ui.drain();
        }
    }

    private static LimelightOptions options(String cutoff) {
        return new LimelightOptions(cutoff, false, "", false);
    }

    private static LimelightReadiness readiness(
            String run,
            List<LimelightRefusal> refusals,
            LimelightCompatibility compatibility,
            Optional<String> decoys,
            Optional<Path> output) {
        Path root = Path.of("project/runs", run).toAbsolutePath();
        return new LimelightReadiness(
                new RunId(run),
                refusals,
                LimelightSettings.DEFAULTS,
                root.resolve("parameters/comet.params"),
                root.resolve("outputs/percolator/pout.xml"),
                root.resolve("outputs/comet"),
                Path.of("inputs/subset.fasta").toAbsolutePath(),
                output,
                decoys,
                Optional.empty(),
                compatibility);
    }

    private static LimelightCompatibility convertible(String run) {
        return new LimelightCompatibility(
                new RunId(run), Optional.of("3.07.1"), Optional.empty(), Optional.empty());
    }

    private static LimelightCompatibility noXml(CompatibilityAction action) {
        return new LimelightCompatibility(
                new RunId(RUN),
                Optional.of("3.09 (registered local binary)"),
                Optional.of(NO_XML),
                Optional.of(action));
    }

    private static Path defaultOutput(String run) {
        return Path.of("project/runs", run, "outputs/limelight/limelight-1.xml").toAbsolutePath();
    }

    /** A convertible run with every prerequisite but the converter's install met. */
    private static LimelightRunState ready(String run, String cutoff) {
        return new LimelightRunState(
                readiness(
                        run,
                        List.of(
                                new LimelightRefusal(
                                        LimelightPrerequisite.CONVERTER,
                                        "The Limelight converter 2.8.1 is not installed.",
                                        Optional.empty())),
                        convertible(run),
                        Optional.of(NO_DECOYS),
                        Optional.of(defaultOutput(run))),
                options(cutoff));
    }

    private static LimelightRunState incompatible(CompatibilityAction action) {
        return new LimelightRunState(
                readiness(
                        RUN,
                        List.of(
                                new LimelightRefusal(
                                        LimelightPrerequisite.PERCOLATOR_XML,
                                        NO_XML,
                                        Optional.empty())),
                        noXml(action),
                        Optional.of(NO_DECOYS),
                        Optional.of(defaultOutput(RUN))),
                options("0.01"));
    }

    private static CompatibilityAction rerunAction(ToolOffer build) {
        return CompatibilityAction.forStage(List.of(build), DownstreamStage.LIMELIGHT_CONVERSION);
    }

    private static LimelightConverterStatus installed() {
        return LimelightConverterStatus.installed(CONVERTER, JAR);
    }

    private static LimelightConverterStatus notInstalled() {
        return LimelightConverterStatus.notInstalled(CONVERTER, OptionalLong.of(2_762_075L), false);
    }

    private static LimelightResult result(Path file) {
        return new LimelightResult(
                file,
                3285,
                2500,
                "0123456789abcdef0123456789abcdef",
                "ab".repeat(32),
                "0.01",
                Optional.empty());
    }

    /** Opens the run in the Results section, with a converter state and a check answer. */
    private void open(LimelightConverterStatus converter, LimelightRunState state) {
        port.converter(converter);
        port.check(run -> state);
        section.refresh();
        results.refresh();
        settle();
    }

    private static InstallProgress progress(
            ToolName tool, String version, InstallPhase phase, long moved) {
        return new InstallProgress(tool, ToolVersion.parse(version), phase, moved, 2_762_075L);
    }

    // -------------------------------------------------------------------------------- tests --

    @Test
    @DisplayName("with no run chosen, everything is disabled and every refusal is in words")
    void nothingChosen() {
        assertAll(
                () -> assertEquals(LimelightViewModel.NO_RUN, section.runTextProperty().get()),
                () ->
                        assertEquals(
                                LimelightViewModel.NOT_READ, section.converterTextProperty().get()),
                () ->
                        assertEquals(
                                LimelightViewModel.NO_RUN
                                        + " Open a run there to convert it to Limelight XML.",
                                section.compatibilityTextProperty().get()),
                () -> assertFalse(section.settingsEnabledProperty().get()),
                () -> assertFalse(section.decoyEnabledProperty().get()),
                () -> assertFalse(section.convertEnabledProperty().get()),
                () -> assertFalse(section.actionVisibleProperty().get()),
                () -> assertFalse(section.convertibleProperty().get()),
                () -> assertEquals("", section.qCutoffTextProperty().get()),
                () ->
                        assertEquals(
                                LimelightViewModel.NOTHING_CONVERTED,
                                section.outcomeProperty().get()));
        assertFalse(section.convert());
        assertFalse(section.act());
        assertEquals(
                "Nothing was converted: "
                        + LimelightViewModel.NO_RUN
                        + " Open a run there to convert it.",
                section.outcomeProperty().get());
        section.editCutoff("0.05");
        assertFalse(section.setImportDecoys(true));
        settle();
        assertAll(
                () -> assertEquals(List.of(), port.saves()),
                () -> assertEquals(List.of(), port.conversions()),
                () -> assertEquals(List.of(), port.checks()));
    }

    @Test
    @DisplayName(
            "gate 3: before the port answers, and when the run's Percolator wrote no XML, every"
                    + " conversion control is disabled and the explanation is shown; the action's"
                    + " label is the workflow's own")
    void compatibilityFirst() {
        ToolOffer build = ToolOffers.percolatorAvailable();
        CompatibilityAction action = rerunAction(build);
        port.converter(installed());
        port.check(run -> incompatible(action));
        results.refresh();
        assertAll(
                "chosen, not yet read",
                () ->
                        assertEquals(
                                LimelightViewModel.CHECKING,
                                section.compatibilityTextProperty().get()),
                () -> assertFalse(section.settingsEnabledProperty().get()),
                () -> assertFalse(section.decoyEnabledProperty().get()),
                () -> assertFalse(section.convertEnabledProperty().get()),
                () ->
                        assertEquals(
                                LimelightViewModel.CHECKING,
                                section.convertRefusalProperty().get()));
        assertFalse(section.convert(), "nothing can be attempted before the answer");
        settle();

        assertAll(
                "read: no XML",
                () -> assertEquals(NO_XML, section.compatibilityTextProperty().get()),
                () -> assertFalse(section.convertibleProperty().get()),
                () -> assertFalse(section.settingsEnabledProperty().get()),
                () -> assertFalse(section.decoyEnabledProperty().get()),
                () -> assertFalse(section.convertEnabledProperty().get()),
                () -> assertEquals(NO_XML, section.convertRefusalProperty().get()),
                () -> assertTrue(section.actionVisibleProperty().get()),
                () -> assertTrue(section.actionEnabledProperty().get()),
                () -> assertEquals(action.label(), section.actionTextProperty().get()),
                () ->
                        assertEquals(
                                "Rerun Percolator with 3.07.1 for Limelight",
                                section.actionTextProperty().get()),
                () -> assertEquals(action.explanation(), section.actionExplanationProperty().get()),
                () ->
                        assertEquals(
                                "PERCOLATOR_XML: " + NO_XML, section.refusalsTextProperty().get()));
        assertFalse(section.convert());
        assertEquals("Nothing was converted: " + NO_XML, section.outcomeProperty().get());
        section.editCutoff("0.05");
        assertFalse(section.setImportDecoys(true));
        section.setOpenMod(true);
        section.editOutput("/somewhere/else.xml");
        settle();
        assertAll(
                () -> assertEquals(List.of(), port.conversions()),
                () -> assertEquals(List.of(), port.saves()),
                () -> assertEquals(List.of(), port.installs()),
                () -> assertFalse(section.openModProperty().get()),
                () -> assertEquals("", section.outputTextProperty().get()));
    }

    @Test
    @DisplayName(
            "R-LL-02: where no XML-capable build can be obtained, the action is registration --"
                    + " no rerun -- and it starts the Percolator section's registration")
    void registrationWhenNothingCanBeObtained() {
        CompatibilityAction action =
                CompatibilityAction.forStage(
                        List.of(ToolOffers.percolatorUnavailableHere()),
                        DownstreamStage.LIMELIGHT_CONVERSION);
        open(installed(), incompatible(action));
        assertAll(
                () -> assertFalse(action.offersRerun()),
                () -> assertEquals(action.label(), section.actionTextProperty().get()),
                () ->
                        assertEquals(
                                "Register a local Percolator binary for Limelight",
                                section.actionTextProperty().get()),
                () ->
                        assertTrue(
                                section.actionExplanationProperty()
                                        .get()
                                        .startsWith(
                                                "No managed Percolator build that can write"
                                                        + " XML_OUTPUT exists for this platform"),
                                section.actionExplanationProperty().get()),
                () -> assertTrue(section.actionEnabledProperty().get()));
        assertTrue(section.act());
        assertEquals(1, registrations.get());
        assertEquals(LimelightViewModel.REGISTERING, section.actionOutcomeProperty().get());
        registers = false;
        assertFalse(section.act());
        assertEquals(LimelightViewModel.NOT_REGISTERING, section.actionOutcomeProperty().get());
        settle();
        assertEquals(List.of(), port.reruns(), "a registration is never a rerun");
        assertEquals(List.of(), port.installs());
    }

    @Test
    @DisplayName(
            "a convertible run: the saved cutoff 0.01, the resolved files read-only, decoy import"
                    + " disabled with the workflow's explanation (gate 8), Convert enabled")
    void aConvertibleRun() {
        open(installed(), ready(RUN, "0.01"));
        assertAll(
                () ->
                        assertEquals(
                                "Run " + RUN + ", created " + CREATED + ".",
                                section.runTextProperty().get()),
                () ->
                        assertEquals(
                                "Run run-1's Percolator 3.07.1 wrote the Percolator XML the"
                                        + " Limelight converter reads, so the run can be"
                                        + " converted.",
                                section.compatibilityTextProperty().get()),
                () -> assertTrue(section.convertibleProperty().get()),
                () -> assertEquals("0.01", section.qCutoffTextProperty().get()),
                () -> assertTrue(section.settingsEnabledProperty().get()),
                () -> assertFalse(section.decoyEnabledProperty().get()),
                () -> assertEquals(NO_DECOYS, section.decoyTextProperty().get()),
                () -> assertFalse(section.importDecoysProperty().get()),
                () ->
                        assertEquals(
                                "The FASTA the run recorded, which the converter reads: "
                                        + Path.of("inputs/subset.fasta").toAbsolutePath(),
                                section.fastaTextProperty().get()),
                () ->
                        assertEquals(
                                "The pepXML directory of the run that searched, which the"
                                        + " converter reads: "
                                        + Path.of("project/runs/run-1/outputs/comet")
                                                .toAbsolutePath(),
                                section.pepXmlTextProperty().get()),
                () ->
                        assertEquals(
                                "The next conversion writes " + defaultOutput(RUN) + ".",
                                section.outputStatusProperty().get()),
                () ->
                        assertEquals(
                                "CONVERTER: The Limelight converter 2.8.1 is not installed.",
                                section.refusalsTextProperty().get()),
                () -> assertEquals("", section.convertRefusalProperty().get()),
                () -> assertTrue(section.convertEnabledProperty().get()),
                () -> assertFalse(section.actionVisibleProperty().get()),
                () -> assertEquals("", section.actionTextProperty().get()),
                () -> assertEquals(List.of(RUN + "|"), port.checks()));

        assertFalse(section.setImportDecoys(true), "gate 8: refused at configuration time");
        settle();
        assertAll(
                () -> assertFalse(section.importDecoysProperty().get()),
                () -> assertEquals(List.of(), port.saves(), "nothing was even asked of the port"),
                () ->
                        assertEquals(
                                "Decoy import was not selected: " + NO_DECOYS,
                                section.settingsStatusProperty().get()));
    }

    @Test
    @DisplayName("decoy import is offered, and saved, only when the workflow allows it")
    void decoysWhenAllowed() {
        LimelightRunState allowed =
                new LimelightRunState(
                        readiness(
                                RUN,
                                List.of(),
                                convertible(RUN),
                                Optional.empty(),
                                Optional.of(defaultOutput(RUN))),
                        options("0.01"));
        open(installed(), allowed);
        assertTrue(section.decoyEnabledProperty().get());
        assertEquals(LimelightViewModel.DECOYS_AVAILABLE, section.decoyTextProperty().get());
        assertEquals(
                LimelightViewModel.EVERY_PREREQUISITE_MET, section.refusalsTextProperty().get());
        port.save(
                run ->
                        new LimelightRunState(
                                allowed.readiness(),
                                new LimelightOptions("0.01", true, "", false)));
        assertTrue(section.setImportDecoys(true));
        assertFalse(section.setImportDecoys(true), "already being saved as on");
        settle();
        assertAll(
                () ->
                        assertEquals(
                                List.of(new LimelightOptions("0.01", true, "", false)),
                                port.saves().stream()
                                        .map(ScriptedLimelight.Save::options)
                                        .toList()),
                () -> assertTrue(section.importDecoysProperty().get()),
                () ->
                        assertEquals(
                                "Saved: the next conversion of run run-1 uses the Limelight"
                                        + " q-value cutoff 0.01, imports decoys.",
                                section.settingsStatusProperty().get()));
    }

    @Test
    @DisplayName(
            "the cutoff is saved through the port, a refusal is shown and blocks Convert until"
                    + " corrected, and the prefix and open-mod switch are saved too")
    void settingsAreSavedThroughThePort() {
        open(installed(), ready(RUN, "0.01"));
        port.save(run -> ready(RUN, "0.05"));
        section.editCutoff("0.05");
        assertEquals("0.05", section.qCutoffTextProperty().get(), "shown as typed at once");
        assertEquals(
                "Saving the Limelight settings of run run-1.",
                section.settingsStatusProperty().get());
        settle();
        assertAll(
                () -> assertEquals("0.05", section.qCutoffTextProperty().get()),
                () ->
                        assertEquals(
                                "Saved: the next conversion of run run-1 uses the Limelight"
                                        + " q-value cutoff 0.05, imports no decoys.",
                                section.settingsStatusProperty().get()),
                () -> assertEquals(1, port.saves().size()),
                () -> assertEquals(options("0.05"), port.saves().get(0).options()));
        section.editCutoff("0.05");
        settle();
        assertEquals(1, port.saves().size(), "an unchanged value is not saved again");

        port.save(
                run -> {
                    throw new IOException(
                            "the Limelight q-value cutoff must be a decimal number from 0 to 1");
                });
        section.editCutoff("1.5");
        settle();
        assertAll(
                "refused",
                () -> assertEquals("1.5", section.qCutoffTextProperty().get()),
                () ->
                        assertEquals(
                                "The Limelight settings were not saved, and no conversion starts"
                                        + " until they are corrected: the Limelight q-value cutoff"
                                        + " must be a decimal number from 0 to 1",
                                section.settingsStatusProperty().get()),
                () -> assertFalse(section.convertEnabledProperty().get()),
                () ->
                        assertEquals(
                                "The Limelight settings shown are not saved: the Limelight q-value"
                                        + " cutoff must be a decimal number from 0 to 1",
                                section.convertRefusalProperty().get()));
        section.refresh();
        settle();
        assertEquals(
                "1.5", section.qCutoffTextProperty().get(), "a refresh keeps the refused text");
        assertFalse(section.convertEnabledProperty().get());

        port.save(run -> ready(RUN, "0.05"));
        section.editCutoff("1.5");
        settle();
        assertEquals("0.05", section.qCutoffTextProperty().get(), "saved again when retried");
        assertTrue(section.convertEnabledProperty().get());

        port.save(
                run ->
                        new LimelightRunState(
                                ready(RUN, "0.05").readiness(),
                                new LimelightOptions("0.05", false, "rev_", false)));
        section.editDecoyPrefix("rev_");
        settle();
        port.save(
                run ->
                        new LimelightRunState(
                                ready(RUN, "0.05").readiness(),
                                new LimelightOptions("0.05", false, "rev_", true)));
        section.setOpenMod(true);
        settle();
        assertAll(
                () -> assertEquals("rev_", section.decoyPrefixTextProperty().get()),
                () -> assertTrue(section.openModProperty().get()),
                () ->
                        assertEquals(
                                new LimelightOptions("0.05", false, "rev_", false),
                                port.saves().get(port.saves().size() - 2).options()),
                () ->
                        assertEquals(
                                new LimelightOptions("0.05", false, "rev_", true),
                                port.saves().get(port.saves().size() - 1).options()),
                () ->
                        assertEquals(
                                "Saved: the next conversion of run run-1 uses the Limelight"
                                        + " q-value cutoff 0.05, imports no decoys,"
                                        + " independent-decoy prefix rev_ and open-modification"
                                        + " mode.",
                                section.settingsStatusProperty().get()));

        port.save(
                run -> {
                    throw new IOException("open-mod refused here");
                });
        section.setOpenMod(false);
        settle();
        assertTrue(section.openModProperty().get(), "a refused switch shows what is saved");
    }

    @Test
    @DisplayName(
            "gate 5: the Limelight cutoff is independent of the display filters -- changing one"
                    + " changes neither the other nor saves anything")
    void independentOfTheDisplayFilters() {
        open(installed(), ready(RUN, "0.01"));
        DisplayFilters before = filters.displayFiltersProperty().get();
        port.save(run -> ready(RUN, "0.05"));
        section.editCutoff("0.05");
        settle();
        assertEquals(before, filters.displayFiltersProperty().get(), "the filters did not move");
        filters.editPsmFilter("0.2");
        settle();
        assertAll(
                () -> assertEquals("0.05", section.qCutoffTextProperty().get()),
                () -> assertEquals(1, port.saves().size(), "a filter change saves nothing here"));
    }

    @Test
    @DisplayName("the output path as typed is checked by the workflow")
    void anExplicitOutput() {
        open(installed(), ready(RUN, "0.01"));
        Path elsewhere = work.resolve("elsewhere.xml");
        port.check(
                run ->
                        new LimelightRunState(
                                readiness(
                                        RUN,
                                        List.of(),
                                        convertible(RUN),
                                        Optional.of(NO_DECOYS),
                                        Optional.of(elsewhere)),
                                options("0.01")));
        section.editOutput(elsewhere.toString());
        section.editOutput(elsewhere.toString());
        settle();
        assertAll(
                () -> assertEquals(elsewhere.toString(), section.outputTextProperty().get()),
                () -> assertEquals(RUN + "|" + elsewhere, port.checks().get(1)),
                () -> assertEquals(2, port.checks().size(), "the same text is not checked again"),
                () ->
                        assertEquals(
                                "The next conversion writes " + elsewhere + ".",
                                section.outputStatusProperty().get()));
        port.check(
                run ->
                        new LimelightRunState(
                                readiness(
                                        RUN,
                                        List.of(
                                                new LimelightRefusal(
                                                        LimelightPrerequisite.OUTPUT,
                                                        "relative is not allowed",
                                                        Optional.empty())),
                                        convertible(RUN),
                                        Optional.of(NO_DECOYS),
                                        Optional.empty()),
                                options("0.01")));
        section.editOutput("relative.xml");
        settle();
        assertAll(
                () -> assertEquals("relative is not allowed", section.outputStatusProperty().get()),
                () -> assertFalse(section.convertEnabledProperty().get()),
                () ->
                        assertEquals(
                                "The conversion cannot start:\nOUTPUT: relative is not allowed",
                                section.convertRefusalProperty().get()));
    }

    @Test
    @DisplayName(
            "Convert with the converter installed: the log lines as they arrive, then the valid"
                    + " file with its PSM count and the recorded SHA-256")
    void convertInstalled() {
        open(installed(), ready(RUN, "0.01"));
        Path file = defaultOutput(RUN);
        port.convert(run -> result(file), "first line", "second line");
        assertTrue(section.convert());
        assertAll(
                "while it converts",
                () -> assertFalse(section.convertEnabledProperty().get()),
                () -> assertFalse(section.settingsEnabledProperty().get()),
                () ->
                        assertEquals(
                                "A conversion is running; the next can start when it has"
                                        + " finished.",
                                section.convertRefusalProperty().get()),
                () ->
                        assertEquals(
                                "Converting run run-1 to Limelight XML with the Limelight q-value"
                                        + " cutoff 0.01.",
                                section.outcomeProperty().get()));
        background.drain();
        assertEquals("", section.logProperty().get(), "the lines arrive on the interface thread");
        ui.runFirst();
        assertEquals("first line", section.logProperty().get());
        ui.runFirst();
        assertEquals("first line\nsecond line", section.logProperty().get());
        settle();
        assertAll(
                () -> assertTrue(section.validProperty().get()),
                () -> assertEquals(file.toString(), section.resultFileProperty().get()),
                () -> assertEquals("3285", section.resultPsmsProperty().get()),
                () -> assertEquals("ab".repeat(32), section.resultSha256Property().get()),
                () ->
                        assertEquals(
                                "Run run-1 was converted to valid Limelight XML: "
                                        + file
                                        + ", holding 3285 PSMs and 2500 reported peptides, with"
                                        + " the PSM q-value filter 0.01. Its SHA-256, as recorded"
                                        + " in the run's provenance event log, is "
                                        + "ab".repeat(32)
                                        + ".",
                                section.outcomeProperty().get()),
                () -> assertEquals(List.of(), port.installs(), "nothing installed"),
                () -> assertEquals(1, port.conversions().size()),
                () -> assertEquals(options("0.01"), port.conversions().get(0).options()),
                () -> assertEquals("", port.conversions().get(0).output()),
                () -> assertTrue(section.convertEnabledProperty().get()),
                () -> assertEquals(2, port.checks().size(), "checked again afterwards"));
    }

    @Test
    @DisplayName("the log keeps the newest lines, at most LOG_LINES of them")
    void theLogIsBounded() {
        open(installed(), ready(RUN, "0.01"));
        String[] lines = new String[LimelightViewModel.LOG_LINES + 2];
        for (int index = 0; index < lines.length; index++) {
            lines[index] = "line " + index;
        }
        port.convert(run -> result(defaultOutput(RUN)), lines);
        section.convert();
        settle();
        String[] kept = section.logProperty().get().split("\n");
        assertAll(
                () -> assertEquals(LimelightViewModel.LOG_LINES, kept.length),
                () -> assertEquals("line 2", kept[0]),
                () -> assertEquals("line " + (lines.length - 1), kept[kept.length - 1]));
    }

    @Test
    @DisplayName(
            "Convert refused by the workflow names every refusal; a failed judge says its reason;"
                    + " neither keeps a result")
    void refusalsAndFailures() {
        open(installed(), ready(RUN, "0.01"));
        port.convert(
                run -> {
                    throw new LimelightConversionRefusedException(
                            List.of(
                                    new LimelightRefusal(
                                            LimelightPrerequisite.FASTA,
                                            "the FASTA changed",
                                            Optional.empty()),
                                    new LimelightRefusal(
                                            LimelightPrerequisite.COMET_PEPXML,
                                            "a pepXML changed",
                                            Optional.empty())));
                });
        section.convert();
        settle();
        assertAll(
                () ->
                        assertEquals(
                                "Nothing was converted: the conversion was refused before the"
                                        + " converter started.\n"
                                        + "FASTA: the FASTA changed\n"
                                        + "COMET_PEPXML: a pepXML changed",
                                section.outcomeProperty().get()),
                () -> assertFalse(section.validProperty().get()),
                () -> assertEquals("", section.resultSha256Property().get()));
        port.convert(
                run -> {
                    throw new IOException(
                            "the Limelight XML /x.xml is not valid against limelight-xml.xsd");
                });
        section.convert();
        settle();
        assertEquals(
                "The converter's output was not kept, because it is not valid Limelight XML or the"
                        + " conversion did not finish: the Limelight XML /x.xml is not valid"
                        + " against limelight-xml.xsd",
                section.outcomeProperty().get());
        assertFalse(section.validProperty().get());
    }

    @Test
    @DisplayName(
            "Convert with the converter not installed installs it through the port first --"
                    + " progress shown, cancellable -- then converts")
    void firstUseInstallsTheConverter() {
        open(notInstalled(), ready(RUN, "0.01"));
        assertTrue(section.convertEnabledProperty().get(), "installable is convertible");
        port.convert(run -> result(defaultOutput(RUN)), "converted");
        assertTrue(section.convert());
        assertAll(
                () -> assertEquals(1, port.installs().size()),
                () -> assertEquals("limelight-converter", port.installs().get(0).tool()),
                () -> assertEquals(CONVERTER, port.installs().get(0).version()),
                () -> assertTrue(section.installingProperty().get()),
                () -> assertEquals(-1.0, section.installProgressProperty().get()),
                () ->
                        assertEquals(
                                "The Limelight converter 2.8.1 is being installed; run run-1 is"
                                        + " converted when it has finished.",
                                section.outcomeProperty().get()),
                () -> assertFalse(section.convertEnabledProperty().get()),
                () -> assertEquals(List.of(), port.conversions()));
        port.installs()
                .get(0)
                .listener()
                .onInstallProgress(
                        progress(
                                ToolName.LIMELIGHT_CONVERTER,
                                "2.8.1",
                                InstallPhase.DOWNLOADING,
                                1_381_037L));
        settle();
        assertAll(
                () -> assertEquals(0.5, section.installProgressProperty().get(), 1e-6),
                () ->
                        assertEquals(
                                "Installing the Limelight converter 2.8.1: Downloading: 1,381,037"
                                        + " of 2,762,075 bytes.",
                                section.converterTextProperty().get()));
        section.cancelInstall();
        assertEquals(1, port.cancels());
        port.converter(installed());
        port.installs()
                .get(0)
                .listener()
                .onInstallProgress(
                        progress(
                                ToolName.LIMELIGHT_CONVERTER,
                                "2.8.1",
                                InstallPhase.DONE,
                                2_762_075L));
        settle();
        assertAll(
                () -> assertFalse(section.installingProperty().get()),
                () -> assertEquals(List.of("ended"), ended),
                () -> assertEquals(1, port.conversions().size(), "converted after the install"),
                () -> assertTrue(section.validProperty().get()),
                () -> assertEquals("converted", section.logProperty().get()),
                () ->
                        assertEquals(
                                installed().explanation(), section.converterTextProperty().get()));
    }

    @Test
    @DisplayName("a cancelled or failed install converts nothing and says so")
    void aCancelledInstall() {
        open(notInstalled(), ready(RUN, "0.01"));
        section.convert();
        port.installs()
                .get(0)
                .listener()
                .onInstallProgress(
                        progress(ToolName.LIMELIGHT_CONVERTER, "2.8.1", InstallPhase.CANCELLED, 0));
        settle();
        assertAll(
                () ->
                        assertEquals(
                                "The Limelight converter's install was cancelled, so nothing was"
                                        + " converted.",
                                section.outcomeProperty().get()),
                () -> assertEquals(List.of(), port.conversions()),
                () -> assertTrue(section.convertEnabledProperty().get()),
                () -> assertEquals(List.of("ended"), ended));
        section.convert();
        port.installs()
                .get(1)
                .listener()
                .onInstallProgress(
                        progress(ToolName.LIMELIGHT_CONVERTER, "2.8.1", InstallPhase.FAILED, 0));
        settle();
        assertEquals(
                "The Limelight converter could not be installed, so nothing was converted. The"
                        + " Tool Manager section shows the attempt.",
                section.outcomeProperty().get());
        port.refuseNextInstall(new IOException("no Tool Manager"));
        assertFalse(section.convert());
        assertEquals(
                "The Limelight converter could not be installed, so nothing was converted: no Tool"
                        + " Manager",
                section.outcomeProperty().get());
        assertFalse(section.installingProperty().get());
        port.converter(LimelightConverterStatus.unavailable("No converter here."));
        section.refresh();
        settle();
        assertFalse(section.convertEnabledProperty().get());
        assertEquals("No converter here.", section.convertRefusalProperty().get());
        port.converter(LimelightConverterStatus.installing(CONVERTER));
        section.refresh();
        settle();
        assertEquals(
                LimelightConverterStatus.installing(CONVERTER).explanation(),
                section.convertRefusalProperty().get());
    }

    @Test
    @DisplayName(
            "gate 4: the rerun action starts Phase 09's rerun through the port with the action"
                    + " shown and the Percolator section's settings; its outcome is followed and"
                    + " the derived run is chosen in the Results section when listed")
    void theCompatibleRerun() {
        ToolOffer build = Percolators.managed3071(Path.of("p/percolator").toAbsolutePath());
        CompatibilityAction action = rerunAction(build);
        assertFalse(action.installFirst());
        open(installed(), incompatible(action));
        assertTrue(section.act());
        assertAll(
                () -> assertFalse(section.actionEnabledProperty().get()),
                () ->
                        assertEquals(
                                "Rerun Percolator with 3.07.1 for Limelight: recording a new run"
                                        + " for run run-1 and copying its merged PIN into it.",
                                section.actionOutcomeProperty().get()));
        settle();
        assertEquals(1, port.reruns().size());
        ScriptedLimelight.Rerun rerun = port.reruns().get(0);
        assertAll(
                () -> assertEquals(action, rerun.action()),
                () -> assertEquals(PercolatorSettings.defaults(), rerun.settings()),
                () -> assertEquals(RUN, rerun.run().id().value()),
                () -> assertTrue(section.rerunCancelEnabledProperty().get()),
                () ->
                        assertEquals(
                                "Rerunning Percolator: run run-derived in "
                                        + Path.of("project/runs/run-derived").toAbsolutePath()
                                        + ".",
                                section.actionOutcomeProperty().get()));
        rerun.observer().planned(PLAN, "run run-derived, rescoring the merged PIN of run run-1");
        rerun.observer()
                .onTransition(
                        new StepTransition(
                                EngineStep.RUN_PERCOLATOR,
                                StepState.READY,
                                StepState.RUNNING,
                                Instant.EPOCH,
                                RunState.RUNNING));
        settle();
        assertEquals(
                "Rerunning Percolator: run run-derived, rescoring the merged PIN of run run-1."
                        + " run-percolator: running.",
                section.actionOutcomeProperty().get());
        assertTrue(section.cancelRerun());
        settle();
        assertEquals(1, port.rerunCancels());

        runs.add(DERIVED);
        port.check(run -> ready(run.id().value(), "0.01"));
        rerun.observer().onRunFinished(result(AttemptOutcome.SUCCEEDED, Map.of()));
        settle();
        assertAll(
                () ->
                        assertEquals(
                                "The Percolator rerun succeeded: run run-derived, rescoring the"
                                        + " merged PIN of run run-1. The new run is chosen in the"
                                        + " Results section as soon as its results are listed"
                                        + " there, and can then be converted here.",
                                section.actionOutcomeProperty().get()),
                () ->
                        assertEquals(
                                Optional.of("run-derived"),
                                results.chosenRunProperty().get().map(run -> run.id().value())),
                () -> assertTrue(section.convertibleProperty().get()),
                () -> assertTrue(section.convertEnabledProperty().get()),
                () -> assertFalse(section.rerunCancelEnabledProperty().get()));
    }

    @Test
    @DisplayName("a refused, failed or cancelled rerun says so and chooses nothing")
    void rerunFailures() {
        ToolOffer build = Percolators.managed3071(Path.of("p/percolator").toAbsolutePath());
        open(installed(), incompatible(rerunAction(build)));
        port.refuseNextRerun(new RunNotStartedException("Comet's PIN changed", null));
        section.act();
        settle();
        assertEquals(
                "The Percolator rerun did not start: Comet's PIN changed",
                section.actionOutcomeProperty().get());
        assertTrue(section.actionEnabledProperty().get());

        section.act();
        settle();
        ScriptedLimelight.Rerun rerun = port.reruns().get(0);
        rerun.observer().planned(PLAN, "run run-derived");
        rerun.observer()
                .onRunFinished(
                        result(AttemptOutcome.FAILED, Map.of(EngineStep.RUN_PERCOLATOR, "exit 1")));
        settle();
        assertEquals(
                "The Percolator rerun failed at step run-percolator ("
                        + EngineStep.RUN_PERCOLATOR.displayName()
                        + "): exit 1. The new run is run run-derived.",
                section.actionOutcomeProperty().get());
        assertEquals(Optional.of(RUN), results.chosenRunProperty().get().map(r -> r.id().value()));

        section.act();
        settle();
        port.reruns().get(1).observer().onRunFinished(result(AttemptOutcome.CANCELLED, Map.of()));
        settle();
        assertEquals(
                "The Percolator rerun was cancelled: run run-derived in "
                        + Path.of("project/runs/run-derived").toAbsolutePath()
                        + ". Its logs and provenance are kept.",
                section.actionOutcomeProperty().get());
    }

    @Test
    @DisplayName(
            "a rerun whose build is not installed installs it through the port first, checks"
                    + " again, and reruns with the installed build")
    void installFirst() {
        CompatibilityAction first = rerunAction(ToolOffers.percolatorAvailable());
        assertTrue(first.installFirst());
        open(installed(), incompatible(first));
        assertTrue(section.act());
        assertAll(
                () -> assertEquals(1, port.installs().size()),
                () -> assertEquals("percolator", port.installs().get(0).tool()),
                () -> assertEquals(ToolVersion.parse("3.07.1"), port.installs().get(0).version()),
                () -> assertEquals(List.of(), port.reruns()),
                () ->
                        assertEquals(
                                "Installing Percolator 3.07.1: starting. Percolator is rerun for"
                                        + " run run-1 when it has finished.",
                                section.actionOutcomeProperty().get()));
        CompatibilityAction installed =
                rerunAction(Percolators.managed3071(Path.of("p/percolator").toAbsolutePath()));
        port.check(run -> incompatible(installed));
        port.installs()
                .get(0)
                .listener()
                .onInstallProgress(
                        progress(ToolName.PERCOLATOR, "3.07.1", InstallPhase.DONE, 1000));
        settle();
        assertAll(
                () -> assertEquals(1, port.reruns().size()),
                () -> assertEquals(installed, port.reruns().get(0).action()),
                () -> assertEquals(List.of("ended"), ended));
    }

    @Test
    @DisplayName("an install that is cancelled, or a build still not installed, reruns nothing")
    void installFirstEndsWithoutARerun() {
        CompatibilityAction first = rerunAction(ToolOffers.percolatorAvailable());
        open(installed(), incompatible(first));
        section.act();
        port.installs()
                .get(0)
                .listener()
                .onInstallProgress(
                        progress(ToolName.PERCOLATOR, "3.07.1", InstallPhase.CANCELLED, 0));
        settle();
        assertEquals(
                "The Percolator install was cancelled, so Percolator was not rerun.",
                section.actionOutcomeProperty().get());
        section.act();
        port.installs()
                .get(1)
                .listener()
                .onInstallProgress(
                        progress(ToolName.PERCOLATOR, "3.07.1", InstallPhase.DONE, 1000));
        settle();
        assertTrue(
                section.actionOutcomeProperty()
                        .get()
                        .startsWith(
                                "Percolator was installed, but no rerun with an installed build"
                                        + " is offered for run run-1 now: "),
                section.actionOutcomeProperty().get());
        assertEquals(List.of(), port.reruns());
        port.check(
                run -> {
                    throw new IOException("unreadable");
                });
        section.act();
        port.installs()
                .get(2)
                .listener()
                .onInstallProgress(
                        progress(ToolName.PERCOLATOR, "3.07.1", InstallPhase.DONE, 1000));
        settle();
        assertEquals(
                "Percolator was installed, but run run-1 could not be checked again, so it was not"
                        + " rerun: unreadable",
                section.actionOutcomeProperty().get());
    }

    @Test
    @DisplayName("a rerun is not offered while the Percolator section's settings are not valid")
    void rerunNeedsValidSettings() {
        ToolOffer build = Percolators.managed3071(Path.of("p/percolator").toAbsolutePath());
        open(installed(), incompatible(rerunAction(build)));
        percolator.set(PercolatorRequest.blocked(List.of("The test FDR is not valid.")));
        assertFalse(section.actionEnabledProperty().get());
        assertFalse(section.act());
        percolator.set(Percolators.ready());
        assertTrue(section.actionEnabledProperty().get());
    }

    @Test
    @DisplayName("an executing run is explained and disabled")
    void anExecutingRun() {
        runs.executing(true);
        String executing =
                "run run-1 is executing: its results are not final and the workflow engine holds"
                        + " its event log. Convert it when the run has ended.";
        LimelightRunState state =
                new LimelightRunState(
                        readiness(
                                RUN,
                                List.of(
                                        new LimelightRefusal(
                                                LimelightPrerequisite.RUN_ENDED,
                                                executing,
                                                Optional.empty())),
                                new LimelightCompatibility(
                                        new RunId(RUN),
                                        Optional.empty(),
                                        Optional.of(executing),
                                        Optional.empty()),
                                Optional.of("decided when the run has ended"),
                                Optional.empty()),
                        options("0.01"));
        open(installed(), state);
        assertAll(
                () ->
                        assertEquals(
                                "Run " + RUN + ", created " + CREATED + ", executing now.",
                                section.runTextProperty().get()),
                () -> assertEquals(executing, section.compatibilityTextProperty().get()),
                () -> assertFalse(section.settingsEnabledProperty().get()),
                () -> assertFalse(section.convertEnabledProperty().get()),
                () -> assertFalse(section.actionVisibleProperty().get()));
    }

    @Test
    @DisplayName("an answer for a run no longer chosen is dropped; a read failure is said")
    void staleAnswersAreDropped() {
        port.converter(installed());
        port.check(run -> ready(RUN, "0.01"));
        results.refresh();
        assertEquals(1, background.pending());
        port.check(
                run -> {
                    throw new IOException("the run's provenance cannot be read");
                });
        section.refresh();
        background.drain();
        ui.runFirst();
        assertEquals(LimelightViewModel.CHECKING, section.compatibilityTextProperty().get());
        settle();
        assertAll(
                () ->
                        assertEquals(
                                "Whether run run-1 can be converted could not be read: the run's"
                                        + " provenance cannot be read",
                                section.compatibilityTextProperty().get()),
                () -> assertFalse(section.settingsEnabledProperty().get()));
    }

    @Test
    @DisplayName("the converter's state that cannot be read is said, and refuses Convert")
    void converterUnreadable() {
        LimelightPort failing =
                new ScriptedLimelight("x") {
                    @Override
                    public LimelightConverterStatus converter() {
                        throw new IllegalStateException("the cache is unreadable");
                    }
                };
        LimelightViewModel other =
                new LimelightViewModel(
                        failing,
                        results,
                        percolator,
                        () -> true,
                        Runnable::run,
                        Runnable::run,
                        () -> {});
        other.refresh();
        assertEquals(
                "The Limelight converter's state could not be read: the cache is unreadable",
                other.converterTextProperty().get());
    }

    @Test
    @DisplayName("the records refuse what cannot be")
    void theRecords() {
        assertAll(
                () ->
                        assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        new LimelightResult(
                                                JAR, -1, 0, "m", "s", "0.01", Optional.empty())),
                () ->
                        assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        new LimelightResult(
                                                JAR, 0, -1, "m", "s", "0.01", Optional.empty())),
                () ->
                        assertThrows(
                                NullPointerException.class,
                                () -> new LimelightOptions(null, false, "", false)),
                () ->
                        assertThrows(
                                NullPointerException.class,
                                () -> new LimelightOptions("0.01", false, null, false)),
                () ->
                        assertThrows(
                                NullPointerException.class,
                                () -> new LimelightRunState(null, options("0.01"))),
                () ->
                        assertEquals(
                                new LimelightOptions("0.2", true, "x", true),
                                options("0.01")
                                        .withQCutoff("0.2")
                                        .withImportDecoys(true)
                                        .withDecoyPrefix("x")
                                        .withOpenMod(true)),
                () -> assertEquals("0.01", LimelightQValueCutoff.DEFAULT.argument()));
    }

    // ------------------------------------------------------------------------------ helpers --

    private static final Plan PLAN = PercolatorRerun.planFor(IndexMode.NONE);

    private static final ResultsRun DERIVED =
            new ResultsRun(
                    new RunId("run-derived"),
                    CREATED.plusSeconds(60),
                    AttemptOutcome.SUCCEEDED,
                    EnumSet.of(TableKind.TARGET_PSMS),
                    false);

    private static RunResult result(AttemptOutcome outcome, Map<EngineStep, String> failures) {
        Map<EngineStep, StepState> states = new EnumMap<>(EngineStep.class);
        for (EngineStep step : PLAN.steps()) {
            states.put(step, failures.containsKey(step) ? StepState.FAILED : StepState.SUCCEEDED);
        }
        Instant at = Instant.parse("2026-10-12T00:00:00Z");
        return new RunResult(
                1,
                outcome,
                RunState.deriveFrom(PLAN, states),
                states,
                failures,
                ProvenanceManifest.current(
                        new RunRecord(
                                new RunId("run-derived"),
                                "p",
                                ProvenanceStatus.COMPLETED,
                                at,
                                Optional.of(at)),
                        ApplicationRecord.capture("0.0.0-test", "unknown"),
                        Map.of(),
                        List.of(),
                        List.of()),
                List.of(),
                0);
    }

    /** An executor that holds its tasks until the test runs them. */
    private static final class Queue implements Executor {

        private final Deque<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable task) {
            tasks.add(task);
        }

        int pending() {
            return tasks.size();
        }

        void runFirst() {
            tasks.pollFirst().run();
        }

        void drain() {
            while (!tasks.isEmpty()) {
                runFirst();
            }
        }
    }

    /** A Results port with one run and its target PSM table, and runs a test adds. */
    private static final class Runs implements ResultsPort {

        private final Path project;

        private final Path psms;

        private final List<ResultsRun> extra = new ArrayList<>();

        private boolean executing;

        Runs(Path project, Path psms) {
            this.project = project;
            this.psms = psms;
        }

        void executing(boolean now) {
            executing = now;
        }

        void add(ResultsRun run) {
            extra.add(run);
        }

        @Override
        public List<ResultsRun> runs() {
            List<ResultsRun> all = new ArrayList<>();
            all.add(
                    new ResultsRun(
                            new RunId(RUN),
                            CREATED,
                            executing ? AttemptOutcome.RUNNING : AttemptOutcome.SUCCEEDED,
                            EnumSet.of(TableKind.TARGET_PSMS),
                            executing));
            all.addAll(extra);
            return all;
        }

        @Override
        public OpenedResults open(RunId run) throws IOException {
            RunLayout layout = new RunLayout(project.resolve("runs").resolve(run.value()));
            Map<TableKind, ResultStore> stores = new EnumMap<>(TableKind.class);
            stores.put(
                    TableKind.TARGET_PSMS,
                    ResultStores.open(
                            psms,
                            TableKind.TARGET_PSMS,
                            layout.resultIndexDirectory(),
                            new StreamingHashService()));
            return new OpenedResults(
                    run, stores, Optional.empty(), Map.of(), ViewStateStore.read(layout));
        }

        @Override
        public void close(OpenedResults results) {
            for (ResultStore store : results.stores().values()) {
                try {
                    store.close();
                } catch (IOException ignored) {
                    // closed already
                }
            }
        }

        @Override
        public void saveViewState(RunId run, DisplayFilters filters) {
            // nothing is saved here
        }

        @Override
        public TableExport exportTable(
                RunId run, TableKind kind, QValueFilter filter, Category category)
                throws IOException {
            throw new IOException("no exports here");
        }

        @Override
        public WeightsExport exportWeights(RunId run, WeightsSummary weights) throws IOException {
            throw new IOException("no exports here");
        }
    }
}
