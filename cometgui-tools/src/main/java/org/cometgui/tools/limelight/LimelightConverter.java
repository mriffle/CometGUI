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

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.cometgui.domain.ports.ProcessRunner;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.tools.api.JavaRuntime;
import org.cometgui.tools.api.ToolRunOutcome;
import org.cometgui.tools.api.ToolRunner;
import org.cometgui.tools.limelight.LimelightConversionException.Failure;

/**
 * Runs the Limelight converter for one request and keeps its output only if the output is a valid
 * Limelight XML file ({@code R-LL-04}, design decision P12-3).
 *
 * <h2>The order of things</h2>
 *
 * <ol>
 *   <li>Nothing may be at the request's output path: a conversion never overwrites ({@link
 *       Failure#OUTPUT_EXISTS}).
 *   <li>The schema is read from the converter JAR this conversion will run ({@link
 *       LimelightSchema}); a JAR without one is refused before anything starts.
 *   <li>The converter runs on the bundled Java runtime, through the process service, as an argument
 *       array ({@link LimelightConverterCommand}), under a timeout, in the JAR's own directory,
 *       with an environment constructed from exactly {@code LANG=C.UTF-8}. It is told to write a
 *       <strong>staging</strong> file beside the output, {@code <output name>}{@value
 *       #STAGING_SUFFIX}.
 *   <li>The outcome is judged: a timeout or a non-zero exit fails; then the staging file is judged
 *       by {@link LimelightXmlJudge} -- the exit status of 0 is never the reason it passes -- and
 *       must carry the q-value cutoff asked for.
 *   <li>Only a file that passed every check is moved to the output path, atomically. On any failure
 *       the staging file is deleted, so nothing is left that looks like a finished conversion, and
 *       the failure names the file, the reason, the exit status and the converter's last lines.
 * </ol>
 *
 * <p>The converter prints {@code Error printing runtime information.} on every run, successful or
 * not (Phase 00); nothing here reads its output as a verdict.
 */
public final class LimelightConverter {

    /** How long one conversion is given unless the caller says otherwise. */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(30);

    /** What is appended to the output's file name to make the staging file's. */
    public static final String STAGING_SUFFIX = ".partial";

    /** The converter's whole environment. */
    static final Map<String, String> ENVIRONMENT = Map.of("LANG", "C.UTF-8");

    private static final int DIAGNOSTIC_LINES = 20;

    private final ToolRunner runner;
    private final JavaRuntime java;
    private final Path converterJar;

    /**
     * Creates the converter.
     *
     * @param processes the process service, the only thing that starts the converter
     * @param timeout how long one conversion is given before it is stopped
     * @param java the runtime the JAR runs on
     * @param converterJar the installed converter JAR, absolute
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if the timeout is not positive or the JAR path is relative
     */
    public LimelightConverter(
            ProcessRunner processes, Duration timeout, JavaRuntime java, Path converterJar) {
        this.runner = new ToolRunner(processes, timeout);
        this.java = Objects.requireNonNull(java, "java");
        this.converterJar = Objects.requireNonNull(converterJar, "converterJar");
        if (!converterJar.isAbsolute()) {
            throw new IllegalArgumentException(
                    "converterJar must be an absolute path, but was: " + converterJar);
        }
    }

    /**
     * The staging file the converter writes before its output is judged.
     *
     * @param output the request's output path
     * @return the staging path, beside it
     * @throws IllegalArgumentException if the output has no file name
     */
    public static Path stagingPath(Path output) {
        Path name = Objects.requireNonNull(output, "output").getFileName();
        if (name == null) {
            throw new IllegalArgumentException("the output path " + output + " names no file");
        }
        return output.resolveSibling(name.toString() + STAGING_SUFFIX);
    }

    /**
     * The command one conversion runs: the converter writing to the staging file.
     *
     * @param request the conversion
     * @return the command
     * @throws IOException if the JAR has no directory to run in
     */
    public ToolCommand command(LimelightConversionRequest request) throws IOException {
        Objects.requireNonNull(request, "request");
        return java.jarCommand(
                converterJar,
                LimelightConverterCommand.arguments(request, stagingPath(request.output())),
                ENVIRONMENT);
    }

    /**
     * Converts, judges and keeps.
     *
     * @param request the conversion
     * @return the conversion, its output at the request's output path
     * @throws LimelightConversionException if the output path is taken, the JAR holds no schema, or
     *     the converter did not produce a valid Limelight XML file carrying the cutoff asked for --
     *     naming the file and the reason
     * @throws IOException if the output directory does not exist, the converter cannot be started,
     *     or a file cannot be moved or deleted
     * @throws NullPointerException if {@code request} is {@code null}
     */
    public LimelightConversion convert(LimelightConversionRequest request) throws IOException {
        Objects.requireNonNull(request, "request");
        Path output = request.output();
        if (Files.exists(output, LinkOption.NOFOLLOW_LINKS)) {
            throw new LimelightConversionException(
                    Failure.OUTPUT_EXISTS,
                    output,
                    "something is already at "
                            + output
                            + ", and a Limelight conversion never overwrites a file",
                    null);
        }
        Path directory = output.getParent();
        if (directory == null || !Files.isDirectory(directory)) {
            throw new IOException(
                    "the directory of the Limelight output " + output + " does not exist");
        }
        LimelightSchema schema = LimelightSchema.fromConverterJar(converterJar);
        Path staging = stagingPath(output);
        Files.deleteIfExists(staging);
        ToolCommand command = command(request);
        ToolRunOutcome outcome = runner.run(command);
        LimelightXmlSummary judged;
        try {
            judged = judge(request, staging, schema, outcome);
        } catch (LimelightConversionException failed) {
            Files.deleteIfExists(staging);
            throw failed;
        }
        Files.move(staging, output, StandardCopyOption.ATOMIC_MOVE);
        return new LimelightConversion(request, command.argv(), outcome, judged.at(output));
    }

    private LimelightXmlSummary judge(
            LimelightConversionRequest request,
            Path staging,
            LimelightSchema schema,
            ToolRunOutcome outcome)
            throws IOException {
        if (outcome.timedOut()) {
            throw new LimelightConversionException(
                    Failure.TIMED_OUT,
                    staging,
                    "the Limelight converter did not finish within "
                            + runner.timeout()
                            + " and was stopped"
                            + afterwards(request, outcome),
                    null);
        }
        int exit = outcome.exitCode().getAsInt();
        if (exit != 0) {
            throw new LimelightConversionException(
                    Failure.EXITED_NON_ZERO,
                    staging,
                    "the Limelight converter exited with status "
                            + exit
                            + afterwards(request, outcome),
                    null);
        }
        LimelightXmlSummary summary;
        try {
            summary = LimelightXmlJudge.judge(staging, schema);
        } catch (LimelightConversionException invalid) {
            throw new LimelightConversionException(
                    invalid.failure(),
                    staging,
                    invalid.getMessage()
                            + ". The converter exited 0, which it also does when it writes"
                            + " nothing"
                            + afterwards(request, outcome),
                    invalid);
        }
        String written = summary.psmQValueDefault().orElse(null);
        if (written == null || !sameNumber(written, request.qCutoff().asDecimal())) {
            throw new LimelightConversionException(
                    Failure.Q_CUTOFF_NOT_APPLIED,
                    staging,
                    "the Limelight XML file "
                            + staging
                            + " carries "
                            + (written == null
                                    ? "no Percolator PSM q-value filter"
                                    : "the Percolator PSM q-value filter " + written)
                            + ", not the cutoff "
                            + request.qCutoff().argument()
                            + " the conversion asked for"
                            + afterwards(request, outcome),
                    null);
        }
        return summary;
    }

    /* Package-private: the schema refuses an unparseable value first, so only a test gets here. */
    static boolean sameNumber(String written, BigDecimal asked) {
        try {
            return new BigDecimal(written).compareTo(asked) == 0;
        } catch (NumberFormatException notANumber) {
            return false;
        }
    }

    private static String afterwards(LimelightConversionRequest request, ToolRunOutcome outcome) {
        StringBuilder text =
                new StringBuilder(". Nothing was written to ").append(request.output());
        List<String> lines = outcome.errorFirst();
        if (!lines.isEmpty()) {
            int from = Math.max(0, lines.size() - DIAGNOSTIC_LINES);
            text.append(". Its last output: ")
                    .append(String.join(" | ", lines.subList(from, lines.size())));
        }
        return text.toString();
    }
}
