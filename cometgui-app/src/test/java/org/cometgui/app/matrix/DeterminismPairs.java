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

package org.cometgui.app.matrix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.cometgui.app.config.ApplicationServices;
import org.cometgui.app.config.ProjectSession;
import org.cometgui.app.config.RunWiring;
import org.cometgui.app.config.SessionEngine;
import org.cometgui.app.matrix.DeterminismSupport.Planned;
import org.cometgui.app.matrix.VersionMatrixPairs.Fixture;
import org.cometgui.domain.build.BuildIdentity;
import org.cometgui.domain.log.BoundedMessageLog;
import org.cometgui.domain.run.AttemptOutcome;
import org.cometgui.domain.tools.ToolInstallState;
import org.cometgui.domain.tools.ToolManager;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolOrigin;
import org.cometgui.tools.process.ProcessService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

/**
 * The determinism suite's fixture half (Phase 15 unit 7, gate item 3; P15-4): the whole Comet ->
 * Percolator run through the product's run port, repeated as the plan says -- the same inputs, the
 * same parameters, the same seed, each run in a <em>fresh project</em> so that it is a new run in a
 * new run directory and never the session's retry of its last one -- then with Comet's and
 * Percolator's thread counts varied (which should change no result) and, as the control, the seed
 * (which should).
 *
 * <p>Runs whose plan scope is {@code every} run for every (Comet, Percolator) pair offered here;
 * {@code default} runs for the default pair the data names alone. Each run's whole directory stays
 * under the report directory -- every Comet and Percolator output, the parameter files, the
 * provenance record -- with a record of what the product derives from the tables. {@code
 * determinism.py} compares them with the documented tolerance; nothing here judges equality.
 *
 * <p>The fixture is the version matrix's one named input ({@value MatrixSupport#FIXTURE}, from
 * {@code COMETGUI_MATRIX_FIXTURE}); without it this class FAILS, saying so. Opt-in (P15-5).
 */
@EnabledIfSystemProperty(
        named = MatrixSupport.ENABLE,
        matches = "true",
        disabledReason = MatrixSupport.DISABLED)
class DeterminismPairs {

    private static final BuildIdentity BUILD =
            BuildIdentity.of("0.0.0-determinism", "unknown", Instant.parse("2026-11-06T00:00:00Z"));

    @TempDir private static Path temporary;

    @AfterAll
    static void restore() {
        MatrixSupport.restoreGuard();
    }

    @Test
    @DisplayName("the whole workflow over the fixture, repeated and varied as the plan says")
    void everyPairAsPlanned() throws IOException, InterruptedException {
        String named = System.getProperty(MatrixSupport.FIXTURE, "");
        assertFalse(
                named.isBlank(),
                "REFUSED: the fixture half of the determinism suite needs its one named input,"
                        + " COMETGUI_MATRIX_FIXTURE (a directory of *.mzML and one *.fasta). It is"
                        + " not set, so nothing was searched and this is not a pass. D-006's CI"
                        + " fixture is OPEN; locally, scratch/fixture.");
        List<Planned> plan = DeterminismSupport.plan("fixture");
        String[] defaultPair = DeterminismSupport.defaultPair();
        Fixture fixture = VersionMatrixPairs.fixture(Path.of(named), temporary.resolve("inputs"));

        MatrixSupport.openGuardForUpstream();
        Clock clock = Clock.systemUTC();
        ProcessService processes = new ProcessService(clock);
        ToolManager manager = MatrixSupport.toolManager(temporary.resolve("cache"), processes);
        List<ToolOffer> comets = new ArrayList<>();
        List<ToolOffer> percolators = new ArrayList<>();
        for (ToolOffer offer : manager.offers()) {
            if (offer.origin() != ToolOrigin.MANAGED
                    || offer.state() == ToolInstallState.UNAVAILABLE_ON_THIS_PLATFORM) {
                continue;
            }
            if (offer.tool() == ToolName.COMET) {
                comets.add(MatrixSupport.install(manager, offer.tool(), offer.version()));
            } else if (offer.tool() == ToolName.PERCOLATOR) {
                percolators.add(MatrixSupport.install(manager, offer.tool(), offer.version()));
            }
        }
        assertFalse(comets.isEmpty(), "no Comet is offered here");
        assertFalse(percolators.isEmpty(), "no Percolator is offered here");
        boolean defaultOffered =
                comets.stream().anyMatch(c -> c.version().text().equals(defaultPair[0]))
                        && percolators.stream()
                                .anyMatch(p -> p.version().text().equals(defaultPair[1]));
        assertEquals(
                true,
                defaultOffered,
                "the default pair the data names, Comet "
                        + defaultPair[0]
                        + " and Percolator "
                        + defaultPair[1]
                        + ", is not offered here");

        List<String> failed = new ArrayList<>();
        for (ToolOffer comet : comets) {
            for (ToolOffer percolator : percolators) {
                String c = comet.version().text();
                String p = percolator.version().text();
                boolean isDefault = c.equals(defaultPair[0]) && p.equals(defaultPair[1]);
                String key = "comet-" + c + "_percolator-" + p;
                for (Planned planned : plan) {
                    if (!"every".equals(planned.scope()) && !isDefault) {
                        continue;
                    }
                    String failure =
                            run(
                                    processes,
                                    clock,
                                    manager,
                                    fixture,
                                    comet,
                                    percolator,
                                    percolators,
                                    key,
                                    planned);
                    if (failure != null) {
                        failed.add(key + " " + planned.label() + ": " + failure);
                    }
                }
            }
        }
        assertEquals(List.of(), failed, "runs that did not finish");
    }

    /** One planned run in a fresh project; returns why it failed, or null. */
    private static String run(
            ProcessService processes,
            Clock clock,
            ToolManager manager,
            Fixture fixture,
            ToolOffer comet,
            ToolOffer percolator,
            List<ToolOffer> every,
            String key,
            Planned planned)
            throws IOException {
        Path work = DeterminismSupport.runDirectory("fixture", key, planned.label());
        Path project = work.resolve("project");
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("cometVersion", comet.version().text());
        values.put("percolatorVersion", percolator.version().text());
        values.put("fixtureDigest", fixture.digest());
        String failure = null;
        Path runDirectory = null;
        ApplicationServices services = VersionMatrixPairs.services(clock, processes);
        try (ProjectSession session = new ProjectSession(project, clock, services.runIds())) {
            SessionEngine engine =
                    RunWiring.port(
                            services,
                            new BoundedMessageLog(),
                            Optional.of(manager),
                            "every tool is installed by the determinism suite",
                            session,
                            BUILD);
            VersionMatrixPairs.Run run =
                    VersionMatrixPairs.runOnce(
                            engine,
                            project,
                            fixture,
                            comet,
                            percolator,
                            every,
                            planned.cometThreads(),
                            planned.settings());
            runDirectory = run.directory();
            values.put("runSeconds", run.seconds());
            values.put("runDirectory", work.relativize(run.directory()).toString());
            if (run.result().outcome() != AttemptOutcome.SUCCEEDED) {
                failure = "the attempt did not succeed: " + run.result().failures();
            }
        } catch (Exception | AssertionError broken) {
            failure = "the run could not be completed: " + broken;
        }
        if (failure == null) {
            try {
                Map<String, Path> tables = new LinkedHashMap<>();
                for (String name :
                        List.of(
                                "psms.tsv",
                                "peptides.tsv",
                                "decoy-psms.tsv",
                                "decoy-peptides.tsv")) {
                    tables.put(name, VersionMatrixPairs.find(runDirectory, name));
                }
                values.put(
                        "product",
                        DeterminismSupport.derived(
                                tables, VersionMatrixPairs.find(runDirectory, "weights.txt")));
            } catch (Exception | AssertionError unreadable) {
                failure = "the product could not read the outputs: " + unreadable;
            }
        }
        values.put("succeeded", failure == null);
        values.put("failure", failure == null ? "" : failure);
        if (!Files.isDirectory(work)) {
            Files.createDirectories(work);
        }
        DeterminismSupport.record(planned, key, work, values);
        return failure;
    }
}
