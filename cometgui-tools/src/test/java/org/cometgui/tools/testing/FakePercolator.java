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

package org.cometgui.tools.testing;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.UnaryOperator;
import org.cometgui.domain.ports.ProcessListener;
import org.cometgui.domain.ports.ProcessRunner;
import org.cometgui.domain.ports.RunningProcess;
import org.cometgui.domain.ports.ToolCommand;

/**
 * A pretend Percolator that behaves the way the real 3.06.5, 3.07.1 and 3.09 binaries were observed
 * to behave on 2026-10-07, so that one behaviour at a time can be taken away.
 *
 * <p>For every invocation it reads the PIN named last on the command line, and then:
 *
 * <ul>
 *   <li>if any option it was told to <em>reject</em> is present, does what real Percolator does
 *       with an option it does not know -- {@code Exception caught: } before its banner on standard
 *       error, exit 1, nothing written anywhere;
 *   <li>otherwise prints its banner, writes every artefact the command asks for -- the PSM, peptide
 *       and decoy tables, the weights, the pout XML -- prints the target peptide table on standard
 *       output unless {@code --results-peptides} named a file for it, and exits 0.
 * </ul>
 *
 * <p>Any artefact can be damaged on its way out with {@link #altering}, and any run's exit code
 * overridden with {@link #exitingWith}, so a test can show that the probe judges each capability on
 * its own observable and nothing else. It records every command, like {@link ScriptedRunner}.
 */
public final class FakePercolator implements ProcessRunner {

    /** The banner, as the real 3.07.1 portable binary prints it. */
    public static final String BANNER =
            "Percolator version 3.07.1, Build Date Jun 20 2024 13:20:18";

    /** The table header all three real builds wrote, hand-typed. */
    public static final String TABLE_HEADER =
            "PSMId\tscore\tq-value\tposterior_error_prob\tpeptide\tproteinIds";

    /** The key {@link #altering} uses for the table printed on standard output. */
    public static final String STANDARD_OUTPUT = "stdout";

    private static final Set<String> FILE_OPTIONS =
            Set.of(
                    "-X",
                    "--results-psms",
                    "--results-peptides",
                    "--decoy-results-psms",
                    "--decoy-results-peptides",
                    "--weights");
    private static final Set<String> VALUE_OPTIONS =
            Set.of("--seed", "--num-threads", "--testFDR", "--trainFDR", "--maxiter");

    private final Set<String> rejected = new HashSet<>();
    private final Map<String, UnaryOperator<List<String>>> alterations = new HashMap<>();
    private final Map<String, Integer> exitCodes = new HashMap<>();
    private final List<ToolCommand> commands = new ArrayList<>();
    private boolean banner = true;

    /**
     * Makes every invocation carrying this option fail as real Percolator fails on an unknown one.
     *
     * @param option the option spelling, for example {@code --weights}
     * @return this fake
     */
    public FakePercolator rejecting(String option) {
        rejected.add(Objects.requireNonNull(option, "option"));
        return this;
    }

    /**
     * Damages what is written for one option, or for standard output.
     *
     * @param option the option whose artefact to alter, or {@link #STANDARD_OUTPUT}
     * @param alteration given the lines that would have been written, returns the lines to write
     * @return this fake
     */
    public FakePercolator altering(String option, UnaryOperator<List<String>> alteration) {
        alterations.put(option, alteration);
        return this;
    }

    /**
     * Overrides the exit code of every invocation carrying this option.
     *
     * @param option the option
     * @param exitCode what such a run exits with, whatever it wrote
     * @return this fake
     */
    public FakePercolator exitingWith(String option, int exitCode) {
        exitCodes.put(option, exitCode);
        return this;
    }

    /**
     * Never prints a banner: a binary that does not get as far as Percolator's own code.
     *
     * @return this fake
     */
    public FakePercolator withoutBanner() {
        banner = false;
        return this;
    }

    /**
     * Every command this fake was asked to run, in order.
     *
     * @return the commands
     */
    public List<ToolCommand> commands() {
        return List.copyOf(commands);
    }

    @Override
    public RunningProcess start(ToolCommand command, ProcessListener listener) {
        commands.add(command);
        List<String> argv = command.argv();
        List<String> options = argv.subList(1, argv.size() - 1);
        for (String option : options) {
            if (rejected.contains(option)) {
                listener.onStandardError("Exception caught: " + BANNER);
                listener.onExit(1);
                return new ScriptedRunner.Cancellable();
            }
        }
        if (!banner) {
            listener.onStandardError("percolator: error while loading shared libraries");
            listener.onExit(127);
            return new ScriptedRunner.Cancellable();
        }
        listener.onStandardError(BANNER);
        Pin pin = Pin.read(Path.of(argv.get(argv.size() - 1)));
        int exitCode = 0;
        boolean peptidesToFile = false;
        int index = 0;
        while (index < options.size()) {
            String option = options.get(index);
            exitCode = exitCodes.getOrDefault(option, exitCode);
            if (FILE_OPTIONS.contains(option)) {
                Path file = Path.of(options.get(index + 1));
                write(file, alter(option, contentFor(option, pin, options.contains("-Z"))));
                peptidesToFile |= "--results-peptides".equals(option);
            }
            index += FILE_OPTIONS.contains(option) || VALUE_OPTIONS.contains(option) ? 2 : 1;
        }
        if (!peptidesToFile) {
            alter(STANDARD_OUTPUT, pin.table(false)).forEach(listener::onStandardOutput);
        }
        listener.onExit(exitCode);
        return new ScriptedRunner.Cancellable();
    }

    private List<String> alter(String key, List<String> lines) {
        return alterations.getOrDefault(key, UnaryOperator.identity()).apply(lines);
    }

    private static List<String> contentFor(String option, Pin pin, boolean withDecoys) {
        return switch (option) {
            case "-X" -> pin.pout(withDecoys);
            case "--results-psms", "--results-peptides" -> pin.table(false);
            case "--decoy-results-psms", "--decoy-results-peptides" -> pin.table(true);
            default -> weights();
        };
    }

    private static List<String> weights() {
        List<String> lines = new ArrayList<>();
        lines.add("# This file contains the weights from each cross validation bin");
        lines.add("# First line is the feature names, followed by normalized weights");
        lines.add("# This is repeated for the other bins");
        for (int bin = 0; bin < 3; bin++) {
            lines.add("feat1\tfeat2\tfeat3\tm0");
            lines.add("0.367\t0.0000\t0.0000\t-0.2914");
            lines.add("0.4167\t0.0000\t0.0000\t-0.4876");
        }
        return lines;
    }

    private static void write(Path file, List<String> lines) {
        try {
            Files.write(file, lines, StandardCharsets.UTF_8);
        } catch (IOException notWritten) {
            throw new UncheckedIOException(notWritten);
        }
    }

    /** The rows of a synthetic PIN, as Percolator would report them. */
    private record Pin(List<String[]> targets, List<String[]> decoys) {

        static Pin read(Path file) {
            List<String[]> targets = new ArrayList<>();
            List<String[]> decoys = new ArrayList<>();
            try {
                List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                for (String line : lines.subList(1, lines.size())) {
                    String[] fields = line.split("\t", -1);
                    (fields[1].equals("1") ? targets : decoys).add(fields);
                }
            } catch (IOException unreadable) {
                throw new UncheckedIOException(unreadable);
            }
            return new Pin(targets, decoys);
        }

        List<String> table(boolean decoyRows) {
            List<String> lines = new ArrayList<>();
            lines.add(TABLE_HEADER);
            for (String[] row : decoyRows ? decoys : targets) {
                lines.add(row[0] + "\t0.5\t0.01\t0.02\t" + row[8] + "\t" + row[9]);
            }
            return lines;
        }

        List<String> pout(boolean withDecoys) {
            StringBuilder document =
                    new StringBuilder(
                            "<percolator_output xmlns=\"http://per-colator.com/percolator_out/15\""
                                    + " xmlns:p=\"http://per-colator.com/percolator_out/15\"><psms>");
            for (String[] row : targets) {
                document.append(psm(row[0], withDecoys, false));
            }
            if (withDecoys) {
                for (String[] row : decoys) {
                    document.append(psm(row[0], true, true));
                }
            }
            return List.of(document.append("</psms></percolator_output>").toString());
        }

        private static String psm(String id, boolean decoyAttribute, boolean decoy) {
            String attribute = decoyAttribute ? " p:decoy=\"" + decoy + "\"" : "";
            return "<psm p:psm_id=\"" + id + "\"" + attribute + "/>";
        }
    }
}
