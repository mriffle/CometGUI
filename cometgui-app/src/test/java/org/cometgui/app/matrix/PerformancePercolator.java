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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import org.cometgui.app.performance.PerfRecorder;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.domain.tools.ToolManager;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.percolator.PercolatorSettings;
import org.cometgui.results.parser.PercolatorOutputException;
import org.cometgui.tools.api.ToolRunOutcome;
import org.cometgui.tools.api.ToolRunner;
import org.cometgui.tools.percolator.PercolatorArtefact;
import org.cometgui.tools.percolator.PercolatorCommand;
import org.cometgui.tools.percolator.PercolatorCommands;
import org.cometgui.tools.percolator.PercolatorRefusedException;
import org.cometgui.tools.percolator.SyntheticPin;
import org.cometgui.tools.process.ProcessService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Performance (f), the half that needs no spectra: Percolator's execution time over CometGUI's own
 * committed synthetic PIN ({@link SyntheticPin}, 64 targets and 64 decoys). The build is the one
 * the runner names ({@code percolator.version}, the product's choice for a Limelight-enabled run),
 * installed from its pinned upstream URL into an empty cache by the product's own Tool Manager;
 * each sample is one run, in a fresh directory with a fresh copy of the PIN, of the command the
 * product's {@link PercolatorCommands} builds from its default settings, through the product's
 * {@link ToolRunner} and process service -- {@code -X} where the probe observed {@code XML_OUTPUT}.
 * Timed from the start of the run to its end; the checks require exit 0 and the product's readers
 * to parse every table and the weights (64 target PSMs, three splits).
 *
 * <p>Opt-in (P15-5). Network: the manifest's download URL for that build (P15-5b).
 */
@EnabledIfSystemProperty(
        named = PerfRecorder.ENABLE,
        matches = "true",
        disabledReason = PerfRecorder.DISABLED)
class PerformancePercolator {

    /** The measurement's identifier in the data. */
    static final String ID = "percolator-synthetic";

    private static final Duration PERCOLATOR_BOUND = Duration.ofMinutes(5);

    private Path work;

    @AfterEach
    void cleanUp() throws IOException {
        MatrixSupport.restoreGuard();
        PerfRecorder.delete(work);
    }

    @Test
    @DisplayName("Percolator over the synthetic PIN, through the product's command and runner")
    void percolatorOverTheSyntheticPin()
            throws IOException,
                    InterruptedException,
                    PercolatorRefusedException,
                    PercolatorOutputException {
        OptionalInt repeats = PerfRecorder.repeats(ID);
        if (repeats.isEmpty()) {
            return;
        }
        work = PerfRecorder.work("percolator");
        MatrixSupport.openGuardForUpstream();
        ProcessService processes = new ProcessService(Clock.systemUTC());
        ToolManager manager = MatrixSupport.toolManager(work.resolve("cache"), processes);
        ToolOffer percolator =
                MatrixSupport.install(
                        manager,
                        ToolName.PERCOLATOR,
                        ToolVersion.parse(PerfRecorder.parameter("percolator.version")));
        Set<ToolCapability> probed = EnumSet.noneOf(ToolCapability.class);
        percolator.capabilities().forEach(declared -> probed.add(declared.capability()));
        boolean xml = probed.contains(ToolCapability.XML_OUTPUT);

        PerfRecorder.Measurement measurement = PerfRecorder.start(ID, "ms");
        measurement.evidence("percolatorVersion", percolator.version().text());
        measurement.evidence("xml", xml);
        List<String> failures = new ArrayList<>();
        Map<String, Object> derived = Map.of();
        for (int sample = 0; sample < repeats.getAsInt(); sample++) {
            Path run = Files.createDirectories(work.resolve("run-" + sample));
            Path pin = SyntheticPin.writeForCapabilityProbe(run);
            Path out = Files.createDirectories(run.resolve("out"));
            PercolatorCommand command =
                    PercolatorCommands.build(
                            VersionMatrixTools.request(
                                    percolator.installedPath().orElseThrow(),
                                    pin,
                                    out,
                                    probed,
                                    xml,
                                    PercolatorSettings.defaults()));
            long start = System.nanoTime();
            ToolRunOutcome outcome =
                    new ToolRunner(processes, PERCOLATOR_BOUND).run(command.command());
            measurement.sample(PerfRecorder.millisSince(start));
            if (!outcome.exitedZero()) {
                failures.add("sample " + sample + ": " + outcome.joinedOutput());
                continue;
            }
            Map<String, Path> tables = new LinkedHashMap<>();
            for (PercolatorArtefact table :
                    List.of(PercolatorArtefact.TARGET_PSMS, PercolatorArtefact.DECOY_PSMS)) {
                tables.put(table.fileName(), command.artefacts().get(table));
            }
            derived =
                    DeterminismSupport.derived(
                            tables, command.artefacts().get(PercolatorArtefact.WEIGHTS));
            measurement.evidence("arguments", command.command().argv().size() - 1);
        }
        measurement.checkEquals("every run exited 0", List.of(), failures);
        measurement.checkEquals("the product parsed 64 target PSMs", 64, rows(derived, "psms.tsv"));
        measurement.checkEquals(
                "the product parsed 64 decoy PSMs", 64, rows(derived, "decoy-psms.tsv"));
        measurement.checkEquals("the product read three splits of weights", 3, splits(derived));
        measurement.finish();
    }

    private static Object rows(Map<String, Object> derived, String table) {
        Object values = derived.get(table);
        return values instanceof Map<?, ?> map ? map.get("rows") : null;
    }

    private static Object splits(Map<String, Object> derived) {
        Object values = derived.get("weights");
        return values instanceof Map<?, ?> map ? map.get("splits") : null;
    }
}
