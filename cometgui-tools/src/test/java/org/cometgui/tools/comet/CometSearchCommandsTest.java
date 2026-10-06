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

package org.cometgui.tools.comet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.domain.run.DatabaseDelivery;
import org.cometgui.domain.run.OutputBase;
import org.cometgui.domain.run.OutputBaseNames;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.tools.testing.Nulls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link CometSearchCommands}: every argument array hand-typed and compared element by element;
 * {@code -D} only for an override; one input per command; two commands of a run differing only in
 * {@code -N} and the input. The real binary runs these commands in {@link
 * CometAdapterRealBinaryTest}.
 */
class CometSearchCommandsTest {

    static final Path RUN = TestPaths.absolute("projects/p one/runs/20261006T120000Z-run-0001");

    static final Path COMET = TestPaths.absolute("tools/comet 2026.03.0/bin/comet");

    static final Path FIRST = TestPaths.absolute("data/K562 3.mzML");

    static final Path SECOND = TestPaths.absolute("data/fractions/k562_4.MZXML");

    static final Path INDEX =
            TestPaths.absolute("projects/p one/index-cache/key 1/subset.fasta.idx");

    private static List<OutputBase> inputs() {
        return OutputBaseNames.derive(List.of(FIRST, SECOND));
    }

    private static CometSearchCommands fromParameterFile() {
        return CometSearchCommands.databaseFromParameterFile(COMET, new RunLayout(RUN));
    }

    private static CometSearchCommands overriding() {
        return CometSearchCommands.databaseOverride(COMET, new RunLayout(RUN), INDEX);
    }

    @Test
    @DisplayName("database in the parameter file: -P, -N, the input; no -D")
    void parameterFileCommands() {
        List<ToolCommand> commands = fromParameterFile().commands(inputs());
        assertEquals(2, commands.size());
        assertEquals(
                List.of(
                        "/tools/comet 2026.03.0/bin/comet",
                        "-P/projects/p one/runs/20261006T120000Z-run-0001/parameters/comet.params",
                        "-N/projects/p one/runs/20261006T120000Z-run-0001/outputs/comet/K562 3",
                        "/data/K562 3.mzML"),
                commands.get(0).argv());
        assertEquals(
                List.of(
                        "/tools/comet 2026.03.0/bin/comet",
                        "-P/projects/p one/runs/20261006T120000Z-run-0001/parameters/comet.params",
                        "-N/projects/p one/runs/20261006T120000Z-run-0001/outputs/comet/k562_4",
                        "/data/fractions/k562_4.MZXML"),
                commands.get(1).argv());
        for (ToolCommand command : commands) {
            assertEquals(
                    TestPaths.absolute("projects/p one/runs/20261006T120000Z-run-0001"),
                    command.workingDirectory());
            assertEquals(Map.of("LANG", "C.UTF-8"), command.environment());
            assertTrue(
                    command.argv().stream().noneMatch(argument -> argument.startsWith("-D")),
                    "no -D when the parameter file names the database: " + command.argv());
        }
    }

    @Test
    @DisplayName("database overridden: -D between -P and -N, and the run records COMMAND_LINE")
    void overrideCommands() {
        CometSearchCommands commands = overriding();
        assertEquals(DatabaseDelivery.COMMAND_LINE, commands.delivery());
        assertEquals(Optional.of(INDEX), commands.database());
        assertEquals(
                List.of(
                        "/tools/comet 2026.03.0/bin/comet",
                        "-P/projects/p one/runs/20261006T120000Z-run-0001/parameters/comet.params",
                        "-D/projects/p one/index-cache/key 1/subset.fasta.idx",
                        "-N/projects/p one/runs/20261006T120000Z-run-0001/outputs/comet/K562 3",
                        "/data/K562 3.mzML"),
                commands.command(inputs().get(0)).argv());
        assertEquals(
                List.of(
                        "/tools/comet 2026.03.0/bin/comet",
                        "-P/projects/p one/runs/20261006T120000Z-run-0001/parameters/comet.params",
                        "-D/projects/p one/index-cache/key 1/subset.fasta.idx",
                        "-N/projects/p one/runs/20261006T120000Z-run-0001/outputs/comet/k562_4",
                        "/data/fractions/k562_4.MZXML"),
                commands.commands(inputs()).get(1).argv());
    }

    @Test
    @DisplayName("the parameter-file run records PARAMETER_FILE and has no database")
    void parameterFileDelivery() {
        assertEquals(DatabaseDelivery.PARAMETER_FILE, fromParameterFile().delivery());
        assertEquals(Optional.empty(), fromParameterFile().database());
    }

    /** The positions at which two argument arrays differ. */
    private static List<Integer> differences(List<String> first, List<String> second) {
        assertEquals(first.size(), second.size(), "two commands of one run have one length");
        List<Integer> differing = new ArrayList<>();
        for (int index = 0; index < first.size(); index++) {
            if (!first.get(index).equals(second.get(index))) {
                differing.add(index);
            }
        }
        return differing;
    }

    @Test
    @DisplayName("two commands of one run differ ONLY in the -N base and the input (R-CMT-04)")
    void differOnlyInInputAndBase() {
        List<ToolCommand> plain = fromParameterFile().commands(inputs());
        List<String> first = plain.get(0).argv();
        List<String> second = plain.get(1).argv();
        assertEquals(List.of(2, 3), differences(first, second));
        assertTrue(first.get(2).startsWith("-N") && second.get(2).startsWith("-N"));
        assertEquals(plain.get(0).workingDirectory(), plain.get(1).workingDirectory());
        assertEquals(plain.get(0).environment(), plain.get(1).environment());

        List<ToolCommand> overridden = overriding().commands(inputs());
        assertEquals(
                List.of(3, 4), differences(overridden.get(0).argv(), overridden.get(1).argv()));
    }

    @Test
    @DisplayName("each command carries exactly one input: its own, last, and no other")
    void oneInputPerCommand() {
        List<OutputBase> inputs = inputs();
        List<ToolCommand> commands = overriding().commands(inputs);
        for (int index = 0; index < commands.size(); index++) {
            List<String> argv = commands.get(index).argv();
            List<String> operands = new ArrayList<>();
            for (String argument : argv.subList(1, argv.size())) {
                if (!argument.startsWith("-")) {
                    operands.add(argument);
                }
            }
            assertEquals(List.of(inputs.get(index).input().toString()), operands);
        }
    }

    @Test
    @DisplayName("no public member can put two inputs on one command line")
    void twoInputsAreInexpressible() {
        List<String> collectionTaking = new ArrayList<>();
        for (Method method : CometSearchCommands.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers())) {
                continue;
            }
            for (Class<?> parameter : method.getParameterTypes()) {
                assertTrue(
                        !parameter.isArray(), method + " takes an array, which could hold inputs");
                if (Collection.class.isAssignableFrom(parameter)) {
                    collectionTaking.add(method.getName());
                }
            }
            if (method.getReturnType() == ToolCommand.class) {
                assertEquals(
                        List.of(OutputBase.class),
                        List.of(method.getParameterTypes()),
                        method + " builds a command, so it takes exactly one input");
            }
        }
        assertEquals(0, CometSearchCommands.class.getConstructors().length);
        // the only collection-taking member returns one command per element
        assertEquals(List.of("commands"), collectionTaking);
        assertEquals(2, fromParameterFile().commands(inputs()).size());
    }

    @Test
    @DisplayName("zero inputs are refused")
    void zeroInputsRefused() {
        IllegalArgumentException refused =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> fromParameterFile().commands(List.of()));
        assertEquals(
                "a Comet search needs at least one spectrum file, and none was given",
                refused.getMessage());
        assertThrows(
                NullPointerException.class,
                () -> fromParameterFile().command(Nulls.of(OutputBase.class)));
    }

    @Test
    @DisplayName("a list that is not the base-name rule's derivation is refused")
    void underivedListRefused() {
        List<OutputBase> derived = inputs();
        List<OutputBase> swapped = List.of(derived.get(1), derived.get(0));
        IllegalArgumentException refused =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> fromParameterFile().commands(swapped));
        assertTrue(
                refused.getMessage()
                        .startsWith(
                                "the spectrum files' positions and base names are not the ones the"
                                        + " output-base rule assigns: given "),
                refused.getMessage());
        // Two files with one natural base: the rule gives the second "a_2", never "a" again.
        List<OutputBase> clashing =
                List.of(
                        new OutputBase(1, TestPaths.absolute("d/a.mzML"), "a"),
                        new OutputBase(2, TestPaths.absolute("e/A.mgf"), "A"));
        assertThrows(IllegalArgumentException.class, () -> fromParameterFile().commands(clashing));
        List<OutputBase> fixed =
                List.of(
                        new OutputBase(1, TestPaths.absolute("d/a.mzML"), "a"),
                        new OutputBase(2, TestPaths.absolute("e/A.mgf"), "A_2"));
        assertEquals(
                "-N/projects/p one/runs/20261006T120000Z-run-0001/outputs/comet/A_2",
                fromParameterFile().commands(fixed).get(1).argv().get(2));
    }

    @Test
    @DisplayName("relative paths are refused, naming what was relative")
    void relativePathsRefused() {
        RunLayout run = new RunLayout(RUN);
        assertEquals(
                "the Comet executable must be an absolute path, not \"bin/comet\"",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        CometSearchCommands.databaseFromParameterFile(
                                                Path.of("bin/comet"), run))
                        .getMessage());
        assertEquals(
                "the database given with -D must be an absolute path, not \"cache/x.idx\"",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        CometSearchCommands.databaseOverride(
                                                COMET, run, Path.of("cache/x.idx")))
                        .getMessage());
        assertEquals(
                "spectrum file 1 must be an absolute path, not \"data/k.mzML\"",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        fromParameterFile()
                                                .command(
                                                        new OutputBase(
                                                                1, Path.of("data/k.mzML"), "k")))
                        .getMessage());
        assertThrows(
                NullPointerException.class,
                () -> CometSearchCommands.databaseOverride(COMET, run, Nulls.of(Path.class)));
        assertThrows(
                NullPointerException.class,
                () ->
                        CometSearchCommands.databaseFromParameterFile(
                                COMET, Nulls.of(RunLayout.class)));
    }

    @Test
    @DisplayName("a run directory holding a tab is refused: -N lands verbatim in every SpecId")
    void controlCharacterInBaseRefused() {
        CometSearchCommands commands =
                CometSearchCommands.databaseFromParameterFile(
                        COMET, new RunLayout(TestPaths.absolute("projects/a\tb/run")));
        IllegalArgumentException refused =
                assertThrows(
                        IllegalArgumentException.class, () -> commands.command(inputs().get(0)));
        assertEquals(
                "the Comet output base \"/projects/a\tb/run/outputs/comet/K562 3\" holds a control"
                        + " character; Comet writes it verbatim into every PIN row's SpecId, where"
                        + " a tab or a line break would corrupt the file",
                refused.getMessage());
        CometSearchCommands newline =
                CometSearchCommands.databaseFromParameterFile(
                        COMET, new RunLayout(TestPaths.absolute("projects/a\nb/run")));
        assertThrows(IllegalArgumentException.class, () -> newline.command(inputs().get(0)));
        CometSearchCommands last =
                CometSearchCommands.databaseFromParameterFile(
                        COMET, new RunLayout(TestPaths.absolute("projects/ab/run\u007f")));
        assertThrows(IllegalArgumentException.class, () -> last.command(inputs().get(0)));
    }

    @Test
    @DisplayName("the environment is exactly LANG=C.UTF-8")
    void environment() {
        assertEquals(Map.of("LANG", "C.UTF-8"), CometSearchCommands.ENVIRONMENT);
    }
}
