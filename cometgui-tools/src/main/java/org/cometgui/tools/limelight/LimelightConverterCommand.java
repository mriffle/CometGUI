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

package org.cometgui.tools.limelight;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The converter's argument array for one request -- what follows {@code java -jar
 * cometPercolator2LimelightXML.jar}.
 *
 * <p><strong>Every option name comes from the pinned JAR's own help</strong> (design decision
 * P12-4): Phase 00's recorded {@code --help} ({@code docs/feasibility/scientific-path}, <em>Stage
 * 3</em>) and a re-run of it on 2026-10-11, which printed the same usage line and option list. A
 * test runs the real JAR's {@code --help} and requires every option this class can emit to appear
 * in it. The short forms are used where the help gives one, as Phase 00's recorded command does.
 *
 * <p>Each path is its own argument after its option. Every path in a request is absolute, so none
 * begins with {@code -} and none can be taken for an option. The independent decoy prefix is the
 * one free-text value; it is attached with {@code =} ({@code --independent-decoy-prefix=DECOY_},
 * the form the help prints), so a prefix that begins with {@code -} is still a value.
 *
 * <p>The order is fixed: {@code -c -p -d -f -o -q}, then the switches. {@code -o} is the staging
 * path the conversion writes to, not the request's final output (see {@link LimelightConverter}).
 */
public final class LimelightConverterCommand {

    /** The Comet parameter file. */
    public static final String COMET_PARAMS = "-c";

    /** The Percolator XML file. */
    public static final String PERCOLATOR_FILE = "-p";

    /** The pepXML directory. */
    public static final String PEPXML_DIRECTORY = "-d";

    /** The FASTA file. */
    public static final String FASTA_FILE = "-f";

    /** The Limelight XML to write. */
    public static final String OUT_FILE = "-o";

    /** The q-value cutoff. */
    public static final String Q_VALUE = "-q";

    /** Import decoys. */
    public static final String IMPORT_DECOYS = "--import-decoys";

    /** The independent-decoy prefix, attached to its value with {@code =}. */
    public static final String INDEPENDENT_DECOY_PREFIX = "--independent-decoy-prefix";

    /** Open-modification mode. */
    public static final String OPEN_MOD = "--open-mod";

    /** Full stack traces on error. */
    public static final String VERBOSE = "-v";

    /** Every option this class can emit, in the order it emits them. */
    public static final List<String> OPTIONS =
            List.of(
                    COMET_PARAMS,
                    PERCOLATOR_FILE,
                    PEPXML_DIRECTORY,
                    FASTA_FILE,
                    OUT_FILE,
                    Q_VALUE,
                    IMPORT_DECOYS,
                    INDEPENDENT_DECOY_PREFIX,
                    OPEN_MOD,
                    VERBOSE);

    private LimelightConverterCommand() {}

    /**
     * The program arguments for one conversion.
     *
     * @param request the conversion
     * @param outFile the file the converter is told to write
     * @return the arguments, immutable
     * @throws NullPointerException if either argument is {@code null}
     * @throws IllegalArgumentException if {@code outFile} is relative
     */
    public static List<String> arguments(LimelightConversionRequest request, Path outFile) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(outFile, "outFile");
        if (!outFile.isAbsolute()) {
            throw new IllegalArgumentException(
                    "outFile must be an absolute path, but was: " + outFile);
        }
        List<String> arguments = new ArrayList<>();
        arguments.add(COMET_PARAMS);
        arguments.add(request.cometParams().toString());
        arguments.add(PERCOLATOR_FILE);
        arguments.add(request.percolatorXml().file().toString());
        arguments.add(PEPXML_DIRECTORY);
        arguments.add(request.pepXmls().directory().toString());
        arguments.add(FASTA_FILE);
        arguments.add(request.fasta().toString());
        arguments.add(OUT_FILE);
        arguments.add(outFile.toString());
        arguments.add(Q_VALUE);
        arguments.add(request.qCutoff().argument());
        if (request.importDecoys()) {
            arguments.add(IMPORT_DECOYS);
        }
        request.independentDecoyPrefix()
                .ifPresent(prefix -> arguments.add(INDEPENDENT_DECOY_PREFIX + "=" + prefix));
        if (request.openMod()) {
            arguments.add(OPEN_MOD);
        }
        if (request.verbose()) {
            arguments.add(VERBOSE);
        }
        return List.copyOf(arguments);
    }
}
