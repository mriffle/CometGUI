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

package org.cometgui.tools.pdv;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.tools.api.JavaRuntime;
import org.cometgui.tools.pdv.derived.DenovoGuiCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The {@code denovo-gui} argument array, environment and refusals, element by element. */
class DenovoGuiCommandTest {

    /** The file system's root, so that no absolute path is a literal. */
    private static final Path TOP =
            java.util.Objects.requireNonNull(Path.of("").toAbsolutePath().getRoot(), "a root");

    private static final Path ROOT = TOP.resolve("runs/r1/pdv");
    private static final JavaRuntime JAVA = new JavaRuntime(TOP.resolve("app/runtime/bin/java"));
    private static final Path JAR = TOP.resolve("tools/pdv/PDV-2.7.0/PDV-2.7.0.jar");
    private static final Path MZTAB = ROOT.resolve("mztab/pdv.mztab");
    private static final Path MGF_1 = ROOT.resolve("mztab/ms_run-1.mgf");
    private static final Path MGF_2 = ROOT.resolve("mztab/ms_run-2.mgf");
    private static final Path HOME = ROOT.resolve("window/home");
    private static final Path WORK = ROOT.resolve("window");

    private static DenovoGuiCommand command(
            List<Path> spectra,
            Optional<DenovoGuiCommand.Tolerance> tolerance,
            boolean hide,
            Map<String, String> display) {
        return new DenovoGuiCommand(
                JAVA, JAR, MZTAB, spectra, tolerance, hide, HOME, WORK, display);
    }

    @Test
    @DisplayName("the product's command: upstream's array, PDV's home moved, the port last")
    void theProductCommand() {
        ToolCommand built =
                command(List.of(MGF_1, MGF_2), Optional.empty(), false, Map.of("DISPLAY", ":7"))
                        .forPort(41_547);

        assertAll(
                () ->
                        assertEquals(
                                List.of(
                                        JAVA.launcher().toString(),
                                        "-Duser.home=" + HOME,
                                        "-jar",
                                        JAR.toString(),
                                        "denovo-gui",
                                        "--mztab",
                                        MZTAB.toString(),
                                        "--spectrum",
                                        MGF_1 + "," + MGF_2,
                                        "--port",
                                        "41547"),
                                built.argv()),
                () -> assertEquals(WORK, built.workingDirectory()),
                () ->
                        assertEquals(
                                Map.of("DISPLAY", ":7", "LANG", "C.UTF-8", "HOME", HOME.toString()),
                                built.environment()));
    }

    @Test
    @DisplayName("a tolerance and the spectrum-only view are passed as upstream passed them")
    void theOptionalArguments() {
        List<String> argv =
                command(
                                List.of(MGF_1),
                                Optional.of(
                                        new DenovoGuiCommand.Tolerance(
                                                new BigDecimal("0.020"), "ppm")),
                                true,
                                Map.of())
                        .forPort(1)
                        .argv();

        assertEquals(
                List.of(
                        "--spectrum",
                        MGF_1.toString(),
                        "--tol",
                        "0.020",
                        "--tol-unit",
                        "ppm",
                        "--port",
                        "1",
                        "--hide-psm-table"),
                argv.subList(7, argv.size()));
        assertEquals(
                "65535",
                command(List.of(MGF_1), Optional.empty(), false, Map.of())
                        .forPort(65_535)
                        .argv()
                        .get(10));
    }

    @Test
    @DisplayName("a port outside 1..65535 is refused")
    void thePortRange() {
        DenovoGuiCommand command = command(List.of(MGF_1), Optional.empty(), false, Map.of());
        assertAll(
                () ->
                        assertEquals(
                                "a loopback port is 1 to 65535, not 0",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () -> command.forPort(0))
                                        .getMessage()),
                () -> assertThrows(IllegalArgumentException.class, () -> command.forPort(65_536)),
                () -> assertThrows(IllegalArgumentException.class, () -> command.forPort(-1)));
    }

    @Test
    @DisplayName("what PDV cannot be given is refused before anything starts")
    void refusals() {
        assertAll(
                () ->
                        assertEquals(
                                "PDV's denovo-gui needs at least one spectrum file",
                                refused(List.of(), Map.of())),
                () ->
                        assertEquals(
                                "PDV splits --spectrum on \",\", so it cannot be given "
                                        + TOP.resolve("data/a,b.mgf"),
                                refused(List.of(TOP.resolve("data/a,b.mgf")), Map.of())),
                () ->
                        assertEquals(
                                "PDV keys spectrum files by file name, and two are named x.mgf",
                                refused(
                                        List.of(
                                                TOP.resolve("data/one/x.mgf"),
                                                TOP.resolve("data/two/x.mgf")),
                                        Map.of())),
                () ->
                        assertEquals(
                                "a spectrum file must be absolute, but was: x.mgf",
                                refused(List.of(Path.of("x.mgf")), Map.of())),
                () ->
                        assertEquals(
                                "the display environment must not set HOME; the denovo-gui command"
                                        + " sets it",
                                refused(List.of(MGF_1), Map.of("HOME", "/root"))),
                () ->
                        assertEquals(
                                "the display environment must not set LANG; the denovo-gui command"
                                        + " sets it",
                                refused(List.of(MGF_1), Map.of("LANG", "C"))),
                () ->
                        assertEquals(
                                "mzTab must be absolute, but was: pdv.mztab",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () ->
                                                        new DenovoGuiCommand(
                                                                JAVA,
                                                                JAR,
                                                                Path.of("pdv.mztab"),
                                                                List.of(MGF_1),
                                                                Optional.empty(),
                                                                false,
                                                                HOME,
                                                                WORK,
                                                                Map.of()))
                                        .getMessage()),
                () ->
                        assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        new DenovoGuiCommand(
                                                JAVA,
                                                Path.of("PDV.jar"),
                                                MZTAB,
                                                List.of(MGF_1),
                                                Optional.empty(),
                                                false,
                                                HOME,
                                                WORK,
                                                Map.of())),
                () ->
                        assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        new DenovoGuiCommand(
                                                JAVA,
                                                JAR,
                                                MZTAB,
                                                List.of(MGF_1),
                                                Optional.empty(),
                                                false,
                                                Path.of("home"),
                                                WORK,
                                                Map.of())),
                () ->
                        assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        new DenovoGuiCommand(
                                                JAVA,
                                                JAR,
                                                MZTAB,
                                                List.of(MGF_1),
                                                Optional.empty(),
                                                false,
                                                HOME,
                                                Path.of("work"),
                                                Map.of())),
                () ->
                        assertThrows(
                                NullPointerException.class,
                                () ->
                                        new DenovoGuiCommand(
                                                null,
                                                JAR,
                                                MZTAB,
                                                List.of(MGF_1),
                                                Optional.empty(),
                                                false,
                                                HOME,
                                                WORK,
                                                Map.of())),
                () ->
                        assertThrows(
                                NullPointerException.class,
                                () ->
                                        new DenovoGuiCommand(
                                                JAVA,
                                                JAR,
                                                MZTAB,
                                                List.of(MGF_1),
                                                null,
                                                false,
                                                HOME,
                                                WORK,
                                                Map.of())));
    }

    private static String refused(List<Path> spectra, Map<String, String> display) {
        return assertThrows(
                        IllegalArgumentException.class,
                        () -> command(spectra, Optional.empty(), false, display))
                .getMessage();
    }

    @Test
    @DisplayName("the command's collections are copies: changing what was passed changes nothing")
    void copies() {
        List<Path> spectra = new ArrayList<>(List.of(MGF_1));
        java.util.HashMap<String, String> display =
                new java.util.HashMap<>(Map.of("DISPLAY", ":1"));
        DenovoGuiCommand command = command(spectra, Optional.empty(), false, display);
        spectra.add(MGF_2);
        display.put("XAUTHORITY", "/x");
        assertAll(
                () -> assertEquals(List.of(MGF_1), command.spectra()),
                () -> assertEquals(Map.of("DISPLAY", ":1"), command.displayEnvironment()),
                () ->
                        assertThrows(
                                UnsupportedOperationException.class,
                                () -> command.spectra().add(MGF_2)));
    }

    @Test
    @DisplayName("a tolerance is positive and in Da or ppm")
    void tolerances() {
        assertAll(
                () ->
                        assertEquals(
                                "a fragment tolerance must be positive, but was 0",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () ->
                                                        new DenovoGuiCommand.Tolerance(
                                                                BigDecimal.ZERO, "Da"))
                                        .getMessage()),
                () ->
                        assertThrows(
                                IllegalArgumentException.class,
                                () -> new DenovoGuiCommand.Tolerance(new BigDecimal("-1"), "Da")),
                () ->
                        assertEquals(
                                "PDV's --tol-unit is Da or ppm, but was \"mmu\"",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () ->
                                                        new DenovoGuiCommand.Tolerance(
                                                                BigDecimal.ONE, "mmu"))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "Da", new DenovoGuiCommand.Tolerance(BigDecimal.ONE, "Da").unit()));
    }
}
