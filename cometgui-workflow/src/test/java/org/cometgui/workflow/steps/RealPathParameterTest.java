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

package org.cometgui.workflow.steps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.OptionalInt;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.domain.run.IndexMode;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.model.DecoySource;
import org.cometgui.params.comet.model.ValueOrigin;
import org.cometgui.params.comet.writer.CanonicalParamsWriter;
import org.cometgui.provenance.hashing.StreamingHashService;
import org.cometgui.tools.api.ToolRunOutcome;
import org.cometgui.tools.api.ToolRunner;
import org.cometgui.tools.comet.CometSearchCommands;
import org.cometgui.tools.process.ProcessService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit 7b against the real Comet 2026.03.0 and the real inputs: {@code comet -q}'s placeholder
 * {@code spectral_library_name = /some/path/speclib.file}, named as a file imported from {@code -q}
 * names it (a new configuration starts empty, D-012), makes Comet itself exit 1, and the pre-run
 * check refuses the same parameters BEFORE COMET STARTS -- no launch through the project's counting
 * process service, no run directory -- with a message naming the parameter, its value and what to
 * do. A readable library file is accepted by the check.
 */
@EnabledOnOs(
        value = OS.LINUX,
        disabledReason =
                "only the linux/x86-64 Comet binary has ever been executed in this project")
class RealPathParameterTest {

    @TempDir private static Path scratch;

    private static Path comet;

    private static Path inputs;

    private static List<Path> spectra;

    private static Path targets;

    private static RealProject project;

    @BeforeAll
    static void stage() throws IOException {
        Path root = scratch.toRealPath();
        comet = RealComet.stageComet(RealComet.NEWER, root.resolve("bin/comet"));
        inputs = Files.createDirectories(root.resolve("inputs"));
        spectra = RealComet.spectra(inputs);
        targets = RealComet.subset(inputs.resolve("targets.fasta"));
        project = RealProject.create(root.resolve("project"));
    }

    @AfterAll
    static void unlock() throws IOException {
        if (project != null) {
            project.close();
        }
    }

    /** The tests' starting parameters, as CometGUI starts them: no spectral library (D-012). */
    private static CometParameters starting() {
        return RealComet.model(
                RealComet.NEWER, targets, DecoySource.COMET_INTERNAL_CONCATENATED, 4);
    }

    /**
     * The tests' starting parameters with Comet's own default spectral library, -q's placeholder,
     * named -- as a file imported from {@code comet -q} names it.
     */
    private static CometParameters withReleaseDefaultLibrary() {
        return starting()
                .withText(
                        "spectral_library_name",
                        PathParameterChecksTest.PLACEHOLDER,
                        ValueOrigin.IMPORTED);
    }

    private static SearchRequest request(CometParameters model) {
        return new SearchRequest(
                model, spectra, RealComet.selection(RealComet.NEWER, comet), IndexMode.NONE);
    }

    @Test
    @DisplayName(
            "Comet 2026.03.0 itself exits 1 on -q's placeholder spectral library, naming the file")
    void cometItselfFailsOnThePlaceholder() throws IOException {
        CometParameters model = withReleaseDefaultLibrary();
        assertEquals(PathParameterChecksTest.PLACEHOLDER, model.text("spectral_library_name"));
        Path run = Files.createDirectories(scratch.toRealPath().resolve("direct"));
        Path params = run.resolve("comet.params");
        new CanonicalParamsWriter(RealComet.BUILD)
                .writeOnce(model, params, new StreamingHashService());
        assertTrue(
                Files.readAllLines(params)
                        .contains("spectral_library_name = /some/path/speclib.file"),
                "the written comet.params carries the placeholder");
        ToolRunOutcome outcome =
                new ToolRunner(new ProcessService(Clock.systemUTC()), Duration.ofMinutes(5))
                        .run(
                                new ToolCommand(
                                        List.of(
                                                comet.toString(),
                                                CometSearchCommands.PARAMS_OPTION + params,
                                                CometSearchCommands.OUTPUT_BASE_OPTION
                                                        + run.resolve("out"),
                                                spectra.get(0).toString()),
                                        run,
                                        CometSearchCommands.ENVIRONMENT));
        assertEquals(OptionalInt.of(1), outcome.exitCode(), outcome.joinedOutput());
        assertTrue(
                outcome.joinedOutput()
                        .contains(
                                "Error (5) - cannot read spectral library file"
                                        + " \"/some/path/speclib.file\"."),
                outcome.joinedOutput());
    }

    @Test
    @DisplayName(
            "the pre-run check refuses the same parameters before Comet starts: the message, no"
                    + " run directory, no launch")
    void thePreRunCheckRefusesThePlaceholder() throws IOException {
        SearchRequest request = request(withReleaseDefaultLibrary());
        String expected =
                "the run cannot start:\n- spectral_library_name (Spectral library file) ="
                        + " /some/path/speclib.file does not exist or cannot be read; clear it to"
                        + " search without one, or choose the file";
        PreRunReport readiness = project.workflow().check(project.project(), request);
        assertTrue(readiness.blocked(), readiness::message);
        assertEquals(expected, readiness.message());
        RunBlockedException blocked =
                assertThrows(RunBlockedException.class, () -> project.prepare(request));
        assertEquals(expected, blocked.getMessage());
        assertEquals(List.of(), project.runDirectories(), "a blocked run leaves no directory");
        assertEquals(List.of(), project.runner().launches(), "a blocked run launches nothing");
    }

    @Test
    @DisplayName("the starting set's empty spectral library (D-012) is accepted by the check")
    void theStartingLibraryIsAccepted() throws IOException {
        assertEquals("", starting().text("spectral_library_name"));
        PreRunReport readiness = project.workflow().check(project.project(), request(starting()));
        assertFalse(readiness.blocked(), readiness::message);
        assertEquals(List.of(), readiness.problems());
    }

    @Test
    @DisplayName("a set, readable spectral library file is accepted by the check")
    void aReadableLibraryIsAccepted() throws IOException {
        Path library = Files.writeString(inputs.resolve("library.speclib"), "library\n");
        SearchRequest request =
                request(
                        withReleaseDefaultLibrary()
                                .withText(
                                        "spectral_library_name",
                                        library.toString(),
                                        ValueOrigin.USER));
        PreRunReport readiness = project.workflow().check(project.project(), request);
        assertFalse(readiness.blocked(), readiness::message);
        assertEquals(List.of(), readiness.problems());
    }
}
