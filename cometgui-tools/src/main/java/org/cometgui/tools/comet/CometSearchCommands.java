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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.domain.run.DatabaseDelivery;
import org.cometgui.domain.run.OutputBase;
import org.cometgui.domain.run.OutputBaseNames;
import org.cometgui.domain.run.RunLayout;

/**
 * The Comet search commands of one run: one {@link ToolCommand} per spectrum file ({@code
 * R-CMT-03}, {@code R-CMT-04}, design decisions P8-4 and P8-8).
 *
 * <p>Pure: nothing here touches the disk or starts a process. The workflow runs each command
 * through the process service.
 *
 * <h2>The argument array</h2>
 *
 * <pre>
 *   comet  -P{run}/parameters/comet.params  [-D{database}]  -N{run}/outputs/comet/{base}  {input}
 * </pre>
 *
 * <p>Comet's options take their value <em>attached</em> -- its own usage text reads {@code
 * -P<params>}, {@code -N<name>}, {@code -D<dbase>} -- so each is one argument array element, and a
 * path containing a space is part of that one element, never quoted. The parameter file is always
 * the run's archived {@code parameters/comet.params} ({@code R-PARAM-12}: the file written once and
 * hashed is the file executed), which is why this class takes no parameter-file path at all. {@code
 * -D} appears only for a run built with {@link #databaseOverride}, and the run records which of the
 * two it was through {@link #delivery()}.
 *
 * <h2>Exactly one input per command, by construction</h2>
 *
 * <p>Comet's {@code -N} is "valid only with one input file", and with two it is <strong>silently
 * ignored</strong>: Comet exits 0 having written {@code <input>.pep.xml} and {@code <input>.pin}
 * beside each input, in the user's data directory ({@code docs/feasibility/scientific-path.rst}).
 * So no method of this class can put two inputs on one command line: {@link #command(OutputBase)}
 * takes one {@link OutputBase}, which holds one path, and {@link #commands(List)} returns one
 * command per element of its list. Every path is absolute, so no input can begin with {@code -} and
 * be read by Comet as an option.
 *
 * <p>Two commands of one run differ in exactly two elements: the {@code -N} base and the input.
 *
 * <h2>The working directory and the environment ({@code R-PROC-04})</h2>
 *
 * <p>The working directory is the run directory. The environment is constructed, never inherited:
 * it is exactly {@link #ENVIRONMENT}, {@code LANG=C.UTF-8}. Measured on linux/x86-64: Comet
 * 2026.03.0 searches correctly with a completely empty environment (it needs no {@code PATH},
 * {@code HOME} or {@code TMPDIR}, and writes nothing outside {@code -N}'s directory), so {@code
 * LANG} is not there because Comet fails without it. It is there to pin the C library's locale to
 * one whose decimal separator is a point, whatever the user's shell has, and because Phase 03
 * measured that a process given no {@code LANG} cannot decode a non-ASCII path. Windows and macOS
 * have not been run; Windows programs commonly need {@code SystemRoot}, which a later platform twin
 * must establish rather than this class assume.
 *
 * <h2>A run directory containing a control character is refused</h2>
 *
 * <p>Comet writes the {@code -N} base, verbatim, at the start of every PIN row's {@code SpecId} --
 * measured: {@code <run>/outputs/comet/k562_3_11188_2_1}. A tab in the run directory's path would
 * therefore shift every column of the PIN and a newline would split every row, so a {@code -N} path
 * holding any control character is refused here, before Comet starts.
 */
public final class CometSearchCommands {

    /** The constructed environment every Comet invocation gets: see the class documentation. */
    public static final Map<String, String> ENVIRONMENT = Map.of("LANG", "C.UTF-8");

    /** Comet's parameter-file option; the path is attached. */
    public static final String PARAMS_OPTION = "-P";

    /** Comet's database option; the path is attached. */
    public static final String DATABASE_OPTION = "-D";

    /** Comet's output-base option; the path is attached. */
    public static final String OUTPUT_BASE_OPTION = "-N";

    private final Path executable;

    private final RunLayout run;

    private final Path database;

    private CometSearchCommands(Path executable, RunLayout run, Path database) {
        this.executable = absolute(executable, "the Comet executable");
        this.run = Objects.requireNonNull(run, "run");
        this.database = database;
    }

    /**
     * The commands of a run whose database reaches Comet through {@code database_name} in the
     * parameter file: no {@code -D}.
     *
     * @param executable the Comet executable, absolute
     * @param run the run
     * @return the run's commands
     * @throws NullPointerException if an argument is {@code null}
     * @throws IllegalArgumentException if the executable is not absolute
     */
    public static CometSearchCommands databaseFromParameterFile(Path executable, RunLayout run) {
        return new CometSearchCommands(executable, run, null);
    }

    /**
     * The commands of a run whose database is given with {@code -D}, overriding the parameter
     * file's {@code database_name} -- the index flow's {@code <cache>/<fasta>.idx} (P8-8).
     *
     * @param executable the Comet executable, absolute
     * @param run the run
     * @param database the database or index Comet is to search, absolute
     * @return the run's commands
     * @throws NullPointerException if an argument is {@code null}
     * @throws IllegalArgumentException if a path is not absolute
     */
    public static CometSearchCommands databaseOverride(
            Path executable, RunLayout run, Path database) {
        return new CometSearchCommands(
                executable, run, absolute(database, "the database given with -D"));
    }

    /**
     * How the database reaches Comet, for the run to record ({@code R-CMT-04}).
     *
     * @return {@link DatabaseDelivery#COMMAND_LINE} for an override, else {@link
     *     DatabaseDelivery#PARAMETER_FILE}
     */
    public DatabaseDelivery delivery() {
        return database == null ? DatabaseDelivery.PARAMETER_FILE : DatabaseDelivery.COMMAND_LINE;
    }

    /**
     * The database given with {@code -D}.
     *
     * @return the override, or empty when the parameter file names the database
     */
    public Optional<Path> database() {
        return Optional.ofNullable(database);
    }

    /**
     * The command that searches one spectrum file.
     *
     * @param input the file, with its position and the base name {@link OutputBaseNames} gave it
     * @return the command: the argument array, the run directory, {@link #ENVIRONMENT}
     * @throws NullPointerException if {@code input} is {@code null}
     * @throws IllegalArgumentException if the input path is not absolute, or the {@code -N} path
     *     holds a control character
     */
    public ToolCommand command(OutputBase input) {
        Objects.requireNonNull(input, "input");
        Path spectra = absolute(input.input(), "spectrum file " + input.position());
        Path outputBase = run.cometOutputBase(input.base());
        String base = outputBase.toString();
        for (int index = 0; index < base.length(); index++) {
            if (Character.isISOControl(base.charAt(index))) {
                throw new IllegalArgumentException(
                        "the Comet output base \""
                                + base
                                + "\" holds a control character; Comet writes it verbatim into"
                                + " every PIN row's SpecId, where a tab or a line break would"
                                + " corrupt the file");
            }
        }
        List<String> argv = new ArrayList<>(5);
        argv.add(executable.toString());
        argv.add(PARAMS_OPTION + run.cometParamsFile());
        if (database != null) {
            argv.add(DATABASE_OPTION + database);
        }
        argv.add(OUTPUT_BASE_OPTION + base);
        argv.add(spectra.toString());
        return new ToolCommand(argv, run.root(), ENVIRONMENT);
    }

    /**
     * One command per spectrum file of the run, in input order.
     *
     * <p>The list must be exactly what {@link OutputBaseNames#derive(List)} gives for its own input
     * paths: positions 1, 2, 3 ... in order, and each base the one the P8-4 rule assigns. A list
     * assembled any other way could give two files one base, and the second search would overwrite
     * the first one's outputs, so it is refused.
     *
     * @param inputs the run's spectrum files, as {@link OutputBaseNames#derive(List)} returned them
     * @return one command per file, in the same order
     * @throws NullPointerException if the list or an element is {@code null}
     * @throws IllegalArgumentException if the list is empty, is not the derivation of its own
     *     paths, or a command cannot be built
     */
    public List<ToolCommand> commands(List<OutputBase> inputs) {
        Objects.requireNonNull(inputs, "inputs");
        if (inputs.isEmpty()) {
            throw new IllegalArgumentException(
                    "a Comet search needs at least one spectrum file, and none was given");
        }
        List<Path> paths = new ArrayList<>(inputs.size());
        for (int index = 0; index < inputs.size(); index++) {
            paths.add(Objects.requireNonNull(inputs.get(index), "inputs[" + index + "]").input());
        }
        List<OutputBase> derived = OutputBaseNames.derive(paths);
        if (!derived.equals(inputs)) {
            throw new IllegalArgumentException(
                    "the spectrum files' positions and base names are not the ones the output-base"
                            + " rule assigns: given "
                            + inputs
                            + ", expected "
                            + derived);
        }
        List<ToolCommand> commands = new ArrayList<>(inputs.size());
        for (OutputBase input : inputs) {
            commands.add(command(input));
        }
        return List.copyOf(commands);
    }

    /**
     * Requires a path to be absolute.
     *
     * @param path the path
     * @param what what it is, for the message
     * @return the path
     */
    static Path absolute(Path path, String what) {
        Objects.requireNonNull(path, what);
        if (!path.isAbsolute()) {
            throw new IllegalArgumentException(
                    what + " must be an absolute path, not \"" + path + "\"");
        }
        return path;
    }
}
