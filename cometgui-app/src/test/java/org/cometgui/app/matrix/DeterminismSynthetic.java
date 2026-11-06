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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.cometgui.app.matrix.DeterminismSupport.Planned;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.domain.tools.ToolInstallState;
import org.cometgui.domain.tools.ToolManager;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolOrigin;
import org.cometgui.tools.api.ToolRunOutcome;
import org.cometgui.tools.api.ToolRunner;
import org.cometgui.tools.percolator.PercolatorArtefact;
import org.cometgui.tools.percolator.PercolatorCommand;
import org.cometgui.tools.percolator.PercolatorCommands;
import org.cometgui.tools.percolator.PercolatorRefusedException;
import org.cometgui.tools.percolator.SyntheticPin;
import org.cometgui.tools.process.ProcessService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

/**
 * The determinism suite's half that needs no spectra (Phase 15 unit 7, gate item 3; P15-4): every
 * Percolator build the product offers on this host, installed from its pinned upstream URL by the
 * product's Tool Manager, run over CometGUI's own committed synthetic PIN ({@link SyntheticPin}) as
 * the plan says -- the same run repeated, each into a fresh directory with a fresh copy of the PIN,
 * then the thread count varied and, as the control, the seed -- with every command built by the
 * product's {@link PercolatorCommands} from {@link
 * org.cometgui.params.percolator.PercolatorSettings} exactly as the workflow maps them. {@code -X}
 * is requested where the probe observed {@code XML_OUTPUT}, so pout XML is compared too.
 *
 * <p>Each run leaves its PIN, every artefact Percolator wrote, its standard output and error, the
 * argument array (the run's own directory written {@code <work>}) and the product's effective seed,
 * and a record of what the product derives from the tables. {@code determinism.py} compares them. A
 * run that did not exit 0 is recorded as failed and fails this class; nothing here judges equality.
 *
 * <p>Opt-in (P15-5): not a {@code *Test}, and disabled without {@value MatrixSupport#ENABLE}{@code
 * =true}. Run by {@code bash scripts/matrix/run-matrix.sh --part determinism-synthetic}.
 */
@EnabledIfSystemProperty(
        named = MatrixSupport.ENABLE,
        matches = "true",
        disabledReason = MatrixSupport.DISABLED)
class DeterminismSynthetic {

    /** What a Percolator invocation may take. A bound, never a delay. */
    private static final Duration PERCOLATOR_BOUND = Duration.ofMinutes(5);

    @TempDir private static Path temporary;

    @AfterAll
    static void restore() {
        MatrixSupport.restoreGuard();
    }

    @Test
    @DisplayName("every offered Percolator, over the synthetic PIN, as the determinism plan says")
    void everyPercolatorAsPlanned()
            throws IOException, InterruptedException, PercolatorRefusedException {
        List<Planned> plan = DeterminismSupport.plan("synthetic");
        MatrixSupport.openGuardForUpstream();
        ProcessService processes = new ProcessService(Clock.systemUTC());
        ToolManager manager = MatrixSupport.toolManager(temporary.resolve("cache"), processes);
        List<ToolOffer> percolators = new ArrayList<>();
        for (ToolOffer offer : manager.offers()) {
            if (offer.tool() == ToolName.PERCOLATOR
                    && offer.origin() == ToolOrigin.MANAGED
                    && offer.state() != ToolInstallState.UNAVAILABLE_ON_THIS_PLATFORM) {
                percolators.add(MatrixSupport.install(manager, offer.tool(), offer.version()));
            }
        }
        assertFalse(percolators.isEmpty(), "no Percolator is offered here");
        List<String> failed = new ArrayList<>();
        for (ToolOffer percolator : percolators) {
            String key = "percolator-" + percolator.version().text();
            for (Planned planned : plan) {
                String failure = run(processes, percolator, key, planned);
                if (failure != null) {
                    failed.add(key + " " + planned.label() + ": " + failure);
                }
            }
        }
        assertEquals(List.of(), failed, "runs that did not finish");
    }

    /** One planned run; returns why it failed, or null. */
    private static String run(
            ProcessService processes, ToolOffer percolator, String key, Planned planned)
            throws IOException, InterruptedException, PercolatorRefusedException {
        Path work = DeterminismSupport.runDirectory("synthetic", key, planned.label());
        Set<ToolCapability> probed = EnumSet.noneOf(ToolCapability.class);
        percolator.capabilities().forEach(declared -> probed.add(declared.capability()));
        boolean xml = probed.contains(ToolCapability.XML_OUTPUT);
        Path pin = SyntheticPin.writeForCapabilityProbe(work);
        Path out = Files.createDirectories(work.resolve("out"));
        PercolatorCommand command =
                PercolatorCommands.build(
                        VersionMatrixTools.request(
                                percolator.installedPath().orElseThrow(),
                                pin,
                                out,
                                probed,
                                xml,
                                planned.settings()));
        List<String> argv = command.command().argv();
        List<String> recordedArgv = new ArrayList<>();
        for (String argument : argv.subList(1, argv.size())) {
            recordedArgv.add(
                    argument.startsWith(work.toString())
                            ? "<work>" + argument.substring(work.toString().length())
                            : argument);
        }
        long started = System.nanoTime();
        ToolRunOutcome outcome = new ToolRunner(processes, PERCOLATOR_BOUND).run(command.command());
        long millis = (System.nanoTime() - started) / 1_000_000L;
        Files.write(
                work.resolve("percolator.stdout"),
                outcome.standardOutput(),
                StandardCharsets.UTF_8);
        Files.write(
                work.resolve("percolator.stderr"), outcome.standardError(), StandardCharsets.UTF_8);
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("percolatorVersion", percolator.version().text());
        values.put("xmlCapable", xml);
        values.put("argv", recordedArgv);
        values.put("effectiveSeed", planned.settings().effectiveSeed(probed).recordedValue());
        values.put("runMillis", millis);
        Map<String, String> artefacts = new LinkedHashMap<>();
        for (Map.Entry<PercolatorArtefact, Path> artefact : command.artefacts().entrySet()) {
            artefacts.put(
                    artefact.getKey().name(), work.relativize(artefact.getValue()).toString());
        }
        values.put("artefacts", artefacts);
        String failure = null;
        if (!outcome.exitedZero()) {
            failure = "Percolator did not exit 0: " + outcome.joinedOutput();
        } else {
            try {
                Map<String, Path> tables = new LinkedHashMap<>();
                for (PercolatorArtefact table :
                        List.of(
                                PercolatorArtefact.TARGET_PSMS,
                                PercolatorArtefact.TARGET_PEPTIDES,
                                PercolatorArtefact.DECOY_PSMS,
                                PercolatorArtefact.DECOY_PEPTIDES)) {
                    tables.put(table.fileName(), command.artefacts().get(table));
                }
                values.put(
                        "product",
                        DeterminismSupport.derived(
                                tables, command.artefacts().get(PercolatorArtefact.WEIGHTS)));
            } catch (IOException e) {
                failure = "the product could not read the outputs: " + e.getMessage();
            }
        }
        values.put("succeeded", failure == null);
        values.put("failure", failure == null ? "" : failure);
        DeterminismSupport.record(planned, key, work, values);
        return failure;
    }
}
