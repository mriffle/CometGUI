/*
 * CometGUI -- Comet to Percolator proteomics search workflow with provenance.
 * Copyright (C) 2026 The CometGUI authors.
 *
 * DERIVED FILE. This file is derived from Noble-Lab/CasanovoGUI and has been
 * modified for CometGUI. Upstream project:
 * <https://github.com/Noble-Lab/CasanovoGUI>, licensed GPL-3.0.
 * Copyright (C) the CasanovoGUI authors.
 *
 * The attribution above is collective because upstream carries no per-file
 * copyright notice: every CasanovoGUI source file begins with its package
 * statement, and `grep -rl Copyright --include=*.java src` in a clone of that
 * repository matches nothing. No notice was dropped in copying.
 *
 * WHICH upstream file, and at WHICH commit, is recorded per file in the
 * documentation comment below, because this header block is fixed and
 * identical in every derived file. config/checkstyle/checkstyle-derived.xml
 * requires that record and fails the build when it is missing.
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

package org.cometgui.tools.pdv.derived;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.tools.api.JavaRuntime;

/**
 * The argument array, working directory and environment of one PDV {@code denovo-gui} window:
 *
 * <pre>
 * java -Duser.home=&lt;home&gt; -jar &lt;PDV jar&gt; denovo-gui --mztab &lt;f&gt;
 *      --spectrum &lt;mgf1[,mgf2]&gt; --port &lt;ephemeral&gt; [--tol &lt;v&gt; --tol-unit Da|ppm]
 *      [--hide-psm-table]
 * </pre>
 *
 * <p>Derived from Noble-Lab/CasanovoGUI src/main/java/org/casanovo/gui/core/PdvLauncher.java at
 * commit 480b3013e7f8fb51a2b8c58681043821e3e7f865, GPL-3.0, modified.
 *
 * <h2>What was kept from upstream</h2>
 *
 * <p>The argument array of upstream's {@code launchDenovo}, and only that: {@code java -jar <jar>
 * denovo-gui --mztab <file> --spectrum <files joined by ","> [--tol <v> --tol-unit <u>] [--port
 * <n>] [--hide-psm-table]}, in that order, absolute paths throughout. The spike ({@code
 * docs/developer/pdv_mztab_spike.rst}, experiment R1) ran exactly this form against PDV 2.7.0.
 *
 * <h2>What was changed for CometGUI</h2>
 *
 * <ul>
 *   <li><b>It builds a command; it starts nothing.</b> Upstream started the process itself with
 *       {@code ProcessBuilder} and discarded its output. Here the result is a {@link ToolCommand}
 *       that {@link PdvController} hands to the {@code ProcessRunner} it was given -- in the
 *       product, the run's recording runner from {@code org.cometgui.workflow.pdv.PdvLaunches}, so
 *       a {@code pdv.launched} event precedes every start, and through it the one process service
 *       ({@code R-PROC-02}, design decision P11-1).
 *   <li><b>The port is required and has no default.</b> Upstream launched without {@code --port}
 *       when no port could be reserved; a window CometGUI cannot control is not one it opens.
 *       {@link #forPort} is called with the ephemeral port the controller reserved.
 *   <li><b>The tolerance is optional and absent by default.</b> Upstream always passed {@code
 *       --tol} and {@code --tol-unit}, from the de novo model's fragment tolerance. Comet has no
 *       fragment <em>tolerance</em>: its {@code fragment_bin_tol} is the width of the bins it
 *       scores in, and turning it into an annotation tolerance would be inventing a value the
 *       search never stated. So the product passes {@link Optional#empty()} and PDV annotates with
 *       its own default (0.05 Da in 2.7.0's {@code denovo-gui} parser). Annotation tolerance
 *       decides which peaks PDV labels, never which spectrum it draws.
 *   <li><b>PDV's home is moved</b>, with {@code -Duser.home} and {@code HOME}, into a directory the
 *       caller owns (design decision P11-17), and {@code LANG} is fixed, as {@code
 *       org.cometgui.tools.pdv.PdvFigures} does for the figure command. The display variables come
 *       from the caller and may not set either.
 *   <li><b>The Java launcher is the application's own</b> ({@link JavaRuntime}), not one found on
 *       the {@code PATH}.
 *   <li><b>What PDV cannot be given is refused here</b>, before anything starts: a spectrum path
 *       containing the {@code ","} PDV splits {@code --spectrum} on, and two spectrum files with
 *       one file name (PDV keys loaded files by name, so the second would shadow the first).
 * </ul>
 *
 * @param java the Java runtime PDV runs on
 * @param pdvJar the installed PDV JAR, absolute
 * @param mzTab the mzTab PDV is given, absolute
 * @param spectra the spectrum files the mzTab's {@code ms_run}s name, in {@code ms_run} order,
 *     absolute
 * @param tolerance the fragment ion tolerance PDV annotates with, or empty for PDV's own default
 * @param hidePsmTable whether PDV opens into a spectrum-only view, without its own PSM table
 * @param home PDV's home directory, absolute: its preferences and caches are written there
 * @param workDirectory the process's working directory, absolute
 * @param displayEnvironment what PDV needs to reach a display ({@code DISPLAY}, {@code XAUTHORITY},
 *     or a test's own); never {@code HOME} or {@code LANG}
 */
public record DenovoGuiCommand(
        JavaRuntime java,
        Path pdvJar,
        Path mzTab,
        List<Path> spectra,
        Optional<Tolerance> tolerance,
        boolean hidePsmTable,
        Path home,
        Path workDirectory,
        Map<String, String> displayEnvironment) {

    /** PDV's {@code denovo-gui} subcommand. */
    public static final String SUBCOMMAND = "denovo-gui";

    /** The character PDV splits {@code --spectrum} on. */
    public static final String SPECTRUM_SEPARATOR = ",";

    /** The variables this command sets itself and a display environment may not. */
    public static final List<String> OWN_VARIABLES = List.of("HOME", "LANG");

    /** The highest TCP port. */
    static final int HIGHEST_PORT = 65_535;

    /**
     * A fragment ion tolerance as PDV's {@code --tol} and {@code --tol-unit} take it.
     *
     * @param value the tolerance, positive
     * @param unit {@code Da} or {@code ppm}
     */
    public record Tolerance(BigDecimal value, String unit) {

        /**
         * Validates the tolerance.
         *
         * @throws IllegalArgumentException if the value is not positive or the unit is neither
         *     {@code Da} nor {@code ppm}
         * @throws NullPointerException if a component is {@code null}
         */
        public Tolerance {
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(unit, "unit");
            if (value.signum() <= 0) {
                throw new IllegalArgumentException(
                        "a fragment tolerance must be positive, but was " + value.toPlainString());
            }
            if (!"Da".equals(unit) && !"ppm".equals(unit)) {
                throw new IllegalArgumentException(
                        "PDV's --tol-unit is Da or ppm, but was \"" + unit + "\"");
            }
        }
    }

    /**
     * Validates the command's parts and copies its collections.
     *
     * @throws IllegalArgumentException if a path is relative, no spectrum file is given, a spectrum
     *     path contains {@value #SPECTRUM_SEPARATOR}, two spectrum files share a file name, or the
     *     display environment sets {@code HOME} or {@code LANG}
     * @throws NullPointerException if a component, a spectrum file, or an environment name or value
     *     is {@code null}
     */
    public DenovoGuiCommand {
        Objects.requireNonNull(java, "java");
        absolute(pdvJar, "pdvJar");
        absolute(mzTab, "mzTab");
        absolute(home, "home");
        absolute(workDirectory, "workDirectory");
        Objects.requireNonNull(tolerance, "tolerance");
        spectra = List.copyOf(Objects.requireNonNull(spectra, "spectra"));
        if (spectra.isEmpty()) {
            throw new IllegalArgumentException("PDV's denovo-gui needs at least one spectrum file");
        }
        Set<String> names = new HashSet<>();
        for (Path file : spectra) {
            absolute(file, "a spectrum file");
            if (file.toString().contains(SPECTRUM_SEPARATOR)) {
                throw new IllegalArgumentException(
                        "PDV splits --spectrum on \""
                                + SPECTRUM_SEPARATOR
                                + "\", so it cannot be given "
                                + file);
            }
            if (!names.add(String.valueOf(file.getFileName()))) {
                throw new IllegalArgumentException(
                        "PDV keys spectrum files by file name, and two are named "
                                + file.getFileName());
            }
        }
        displayEnvironment =
                Map.copyOf(Objects.requireNonNull(displayEnvironment, "displayEnvironment"));
        for (String name : OWN_VARIABLES) {
            if (displayEnvironment.containsKey(name)) {
                throw new IllegalArgumentException(
                        "the display environment must not set "
                                + name
                                + "; the denovo-gui command sets it");
            }
        }
    }

    private static void absolute(Path path, String what) {
        Objects.requireNonNull(path, what);
        if (!path.isAbsolute()) {
            throw new IllegalArgumentException(what + " must be absolute, but was: " + path);
        }
    }

    @Override
    public List<Path> spectra() {
        return List.copyOf(spectra);
    }

    @Override
    public Map<String, String> displayEnvironment() {
        return Map.copyOf(displayEnvironment);
    }

    /**
     * The command that opens PDV's window with its control server on {@code port}.
     *
     * @param port the loopback port PDV's control server listens on, 1 to 65535
     * @return the command
     * @throws IllegalArgumentException if the port is out of range
     */
    public ToolCommand forPort(int port) {
        if (port < 1 || port > HIGHEST_PORT) {
            throw new IllegalArgumentException(
                    "a loopback port is 1 to " + HIGHEST_PORT + ", not " + port);
        }
        List<String> files = new ArrayList<>(spectra.size());
        for (Path file : spectra) {
            files.add(file.toString());
        }
        List<String> argv = new ArrayList<>();
        argv.add(java.launcher().toString());
        argv.add("-Duser.home=" + home);
        argv.add("-jar");
        argv.add(pdvJar.toString());
        argv.add(SUBCOMMAND);
        argv.add("--mztab");
        argv.add(mzTab.toString());
        argv.add("--spectrum");
        argv.add(String.join(SPECTRUM_SEPARATOR, files));
        tolerance.ifPresent(
                given -> {
                    argv.add("--tol");
                    argv.add(given.value().toPlainString());
                    argv.add("--tol-unit");
                    argv.add(given.unit());
                });
        argv.add("--port");
        argv.add(Integer.toString(port));
        if (hidePsmTable) {
            argv.add("--hide-psm-table");
        }
        Map<String, String> environment = new LinkedHashMap<>(displayEnvironment);
        environment.put("LANG", "C.UTF-8");
        environment.put("HOME", home.toString());
        return new ToolCommand(argv, workDirectory, environment);
    }
}
