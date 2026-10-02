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

package org.cometgui.params.comet.writer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.cometgui.domain.ports.ProcessListener;
import org.cometgui.domain.ports.RunningProcess;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.params.comet.fixtures.CometFixtures;
import org.cometgui.params.comet.fixtures.CometManifest;
import org.cometgui.params.comet.fixtures.ParamsFiles;
import org.cometgui.params.comet.fixtures.UpstreamMirror;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.model.ParameterValue;
import org.cometgui.params.comet.model.ValueOrigin;
import org.cometgui.params.comet.parser.CometParamsParser;
import org.cometgui.params.comet.value.EnzymeDefinition;
import org.cometgui.tools.process.ProcessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * The REAL pinned Comet 2026.02.2 binary reads the canonical file without complaint.
 *
 * <p>The check is {@code comet -P<file> missing.mzML}, run through {@link ProcessService}. Comet's
 * {@code ProcessCmdLine} ({@code Comet.cpp} at {@code v2026.02.2}) calls {@code LoadParameters} on
 * the {@code -P} file <em>before</em> it looks at any input file, and {@code LoadParameters} exits
 * with its own message when the marker is not in the first seven lines, when {@code
 * output_percolatorfile} is absent ("outdated params file"), and when a variable-modification tuple
 * does not have eight fields; it prints "Warning - invalid parameter found" for every name it does
 * not know. Only then does it reach the input file and stop with {@code Error - input file
 * "missing.mzML" not found.} So a transcript that ends at that error, with no warning or error but
 * those Comet's own {@code -q} file gives, proves the canonical file passed the parameter reader.
 * It does not prove Comet read each value as the model means it: Comet prints no parsed values.
 *
 * <p>Comet's own {@code -q} file is run first as the control, and must give the same transcript;
 * that transcript carries one warning, for {@code spectral_library_ms_level}, which {@code -q}
 * writes and the reader does not know (it reads {@code speclib_ms_level}). A model with one unknown
 * parameter is run last and must add exactly one warning, which shows this check can see a
 * difference.
 */
@EnabledOnOs(
        value = OS.LINUX,
        disabledReason =
                "only the linux/x86-64 Comet binary has ever been executed in this project")
class CometReadsCanonicalRealBinaryTest {

    private static final String MISSING_INPUT = "missing.mzML";

    private static final String REACHED_INPUTS =
            " Error - input file \"" + MISSING_INPUT + "\" not found.";

    private static final String KNOWN_WARNING =
            " Warning - invalid parameter found: spectral_library_ms_level.  Parameter will be"
                    + " ignored.";

    private Path scratch;

    private Path binary;

    @BeforeEach
    void stage(@TempDir Path directory) throws IOException {
        scratch = directory;
        CometManifest.Row row =
                CometManifest.cometRows(CometManifest.repositoryManifest()).stream()
                        .filter(r -> r.version().equals(CometFixtures.COMET_2026_02_2))
                        .filter(CometManifest.Row::isLinuxX8664)
                        .findFirst()
                        .orElseThrow(
                                () -> new AssertionError("no linux/x86-64 Comet 2026.02.2 row"));
        binary =
                UpstreamMirror.stage(
                                UpstreamMirror.repositoryRoot(),
                                row,
                                scratch.resolve("bin").resolve("comet"))
                        .toAbsolutePath();
    }

    /** Runs {@code comet -P<params> missing.mzML} in an empty directory; returns the transcript. */
    private Transcript run(String label, byte[] params) throws IOException, InterruptedException {
        Path directory = Files.createDirectory(scratch.resolve(label));
        Path file = directory.resolve(label + ".params");
        Files.write(file, params);
        ToolCommand command =
                new ToolCommand(
                        List.of(binary.toString(), "-P" + file.toAbsolutePath(), MISSING_INPUT),
                        directory.toAbsolutePath(),
                        Map.of());
        Collector collector = new Collector();
        RunningProcess process = new ProcessService(Clock.systemUTC()).start(command, collector);
        if (!collector.exited.await(60, TimeUnit.SECONDS)) {
            process.requestCancellation();
            throw new AssertionError(command.displayString() + " did not finish in 60 s");
        }
        return new Transcript(
                collector.exitCode.get(),
                List.copyOf(collector.standardOutput),
                List.copyOf(collector.standardError));
    }

    /**
     * What one run printed. The two streams are kept apart: Comet logs warnings to standard output
     * and errors to standard error, and the order in which two streams' lines arrive is not fixed.
     */
    private record Transcript(
            int exitCode, List<String> standardOutput, List<String> standardError) {

        List<String> warningsAndErrors() {
            List<String> found = new ArrayList<>();
            for (String line : standardOutput) {
                if (line.contains("Warning") || line.contains("Error")) {
                    found.add(line);
                }
            }
            for (String line : standardError) {
                if (line.contains("Warning") || line.contains("Error")) {
                    found.add(line);
                }
            }
            return found;
        }
    }

    private static final class Collector implements ProcessListener {

        private final List<String> standardOutput = Collections.synchronizedList(new ArrayList<>());
        private final List<String> standardError = Collections.synchronizedList(new ArrayList<>());
        private final AtomicInteger exitCode = new AtomicInteger(Integer.MIN_VALUE);
        private final CountDownLatch exited = new CountDownLatch(1);

        @Override
        public void onStandardOutput(String line) {
            standardOutput.add(line);
        }

        @Override
        public void onStandardError(String line) {
            standardError.add(line);
        }

        @Override
        public void onExit(int code) {
            exitCode.set(code);
            exited.countDown();
        }
    }

    private static CometParameters realModel() {
        return new CometParamsParser(ParamsFiles.metadata(), ParamsFiles.COMET)
                .parse(ParamsFiles.complete())
                .model()
                .orElseThrow();
    }

    @Test
    @DisplayName("Comet reads its own -q file and the canonical file identically, to the inputs")
    void cometReadsTheCanonicalFile() throws IOException, InterruptedException {
        CanonicalParamsWriter writer = new CanonicalParamsWriter(ParamsFiles.build());
        Transcript control =
                run(
                        "control",
                        CometFixtures.bytes(
                                CometFixtures.COMET_2026_02_2,
                                CometFixtures.LINUX_X86_64,
                                CometFixtures.Mode.COMPLETE));
        assertEquals(List.of(KNOWN_WARNING, REACHED_INPUTS), control.warningsAndErrors());
        assertEquals(1, control.exitCode());

        Transcript canonical = run("canonical", writer.bytes(realModel()));
        assertEquals(control.warningsAndErrors(), canonical.warningsAndErrors());
        assertEquals(control, canonical);
        assertEquals(1, canonical.exitCode());

        CometParameters custom =
                realModel()
                        .withEnzymeTable(
                                realModel()
                                        .enzymeTable()
                                        .with(
                                                new EnzymeDefinition(
                                                        12,
                                                        "Glu_C",
                                                        EnzymeDefinition.Sense.AFTER_RESIDUE,
                                                        "DE",
                                                        "P")))
                        .withValue(
                                "search_enzyme_number",
                                new ParameterValue.Whole(12),
                                ValueOrigin.USER)
                        .withText(
                                "variable_mod02",
                                "79.966331 STY 0 2,4 -1 0 0 97.976896,79.966331",
                                ValueOrigin.USER);
        Transcript edited = run("custom", writer.bytes(custom));
        assertEquals(control, edited);

        String withUnknown = ParamsFiles.completeWith("num_results", "bogus_parameter = 3\n");
        Transcript unknown =
                run(
                        "unknown",
                        writer.bytes(
                                new CometParamsParser(ParamsFiles.metadata(), ParamsFiles.COMET)
                                        .parse(withUnknown)
                                        .model()
                                        .orElseThrow()));
        assertNotEquals(control, unknown);
        assertEquals(
                List.of(
                        KNOWN_WARNING,
                        " Warning - invalid parameter found: bogus_parameter.  Parameter will be"
                                + " ignored.",
                        REACHED_INPUTS),
                unknown.warningsAndErrors());
        assertTrue(unknown.standardError().contains(REACHED_INPUTS));
        assertEquals(control.standardError(), unknown.standardError());
    }
}
