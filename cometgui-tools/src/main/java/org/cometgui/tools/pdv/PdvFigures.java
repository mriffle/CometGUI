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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.cometgui.domain.ports.ProcessRunner;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.tools.api.ToolRunOutcome;
import org.cometgui.tools.api.ToolRunner;

/**
 * Draws annotated spectra with PDV's command line, judged only by the files it writes ({@code
 * R-PDV-01}, the automated test path).
 *
 * <h2>The order of things</h2>
 *
 * <ol>
 *   <li>The spectrum source is checked against the selected PDV version ({@link
 *       PdvSpectrumSupport#check}). An unsupported source fails here with {@link
 *       UnsupportedSpectrumFileException} and <strong>nothing is launched</strong>.
 *   <li>Under the request's work directory, which must be absent or empty: {@value #HOME} (PDV's
 *       {@code HOME} and {@code user.home}: it writes {@code ~/.compomics} and {@code ~/.java}),
 *       {@value #MGF} (the source re-encoded by {@link PdvMgfWriter#writeEverySpectrum}, because
 *       PDV's mzML path numbers spectra by file position and exits 0 drawing nothing on a
 *       scan-range subset), {@value #IDS} (one native id per line) and {@value #FIGURES} (PDV's
 *       output directory, which therefore holds nothing PDV did not write).
 *   <li>PDV runs through the process service, as an argument array, under a timeout:
 *       <pre>{@code
 * java -Duser.home=<home> -jar <PDV jar> -r <pepXML> -rt 2 -s <MGF> -st 1
 *      -i <ids file> -k s -o <figures> -ft png
 * }</pre>
 *       in the work directory, with an environment constructed from exactly {@code LANG=C.UTF-8},
 *       {@code HOME} and the request's display variables. {@code -k s} selects spectra by the
 *       pepXML's {@code spectrumNativeID}, which is each MGF entry's title.
 *   <li>The outcome is read from the figures directory: PDV's {@code error.txt}, and every {@code
 *       .png} found by scanning -- never by a predicted name, since PDV names a figure {@code
 *       <peptide><native id>.png} with spaces and {@code =} in it. Each is parsed and verified by
 *       {@link PngFigure#read}. A figure is attributed to the requested id its name ends with
 *       (after the characters PDV strips from file names), the longest such id winning.
 * </ol>
 *
 * <p>Every failure has a name ({@link PdvFigureException.Failure}); "exit 0 and no figure" is
 * {@link PdvFigureException.Failure#NO_FIGURE}, never a pass. The exit status is used only to name
 * a failure, never to declare success.
 */
public final class PdvFigures {

    /** How long one PDV run is given unless the caller says otherwise. */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(2);

    /** PDV's home directory, under the work directory. */
    public static final String HOME = "home";

    /** The re-encoded spectra, under the work directory. */
    public static final String MGF = "spectra.mgf";

    /** The list of native ids to draw, under the work directory. */
    public static final String IDS = "spectrum-ids.txt";

    /** PDV's output directory, under the work directory. */
    public static final String FIGURES = "figures";

    /** The file PDV lists its failures in, inside its output directory. */
    public static final String ERROR_FILE = "error.txt";

    /** The variables the command sets itself and a display environment may not. */
    static final List<String> OWN_VARIABLES = List.of("HOME", "LANG");

    /** The characters PDV removes from a native id to make a file name. */
    private static final Pattern STRIPPED = Pattern.compile("[\\\\/:*?\"<>|]");

    private static final int DIAGNOSTIC_LINES = 20;

    private final ToolRunner runner;

    /**
     * Creates the figure generator.
     *
     * @param processes the process service, the only thing that starts PDV
     * @param timeout how long one PDV run is given before it is stopped
     * @throws NullPointerException if either argument is {@code null}
     * @throws IllegalArgumentException if the timeout is not positive
     */
    public PdvFigures(ProcessRunner processes, Duration timeout) {
        this.runner = new ToolRunner(processes, timeout);
    }

    /**
     * Draws one annotated figure per requested spectrum.
     *
     * @param request what to draw, from what
     * @return one verified figure per requested id (more if PDV drew several for one), in request
     *     order
     * @throws UnsupportedSpectrumFileException before anything is launched, if the selected PDV
     *     cannot be given the spectrum source
     * @throws SpectrumFileException if the source cannot be re-encoded faithfully
     * @throws PdvFigureException if PDV did not produce every figure, naming how it failed
     * @throws IOException if a file cannot be read or written, the work directory is not empty, or
     *     PDV cannot be started
     * @throws NullPointerException if {@code request} is {@code null}
     */
    public List<PdvFigure> generate(PdvFigureRequest request) throws IOException {
        Objects.requireNonNull(request, "request");
        SpectrumFileFormat format =
                PdvSpectrumSupport.forVersion(request.pdvVersion()).check(request.spectrumFile());
        Path work = emptyDirectory(request.workDirectory());
        Path home = Files.createDirectory(work.resolve(HOME));
        Path figures = Files.createDirectory(work.resolve(FIGURES));
        Path mgf =
                PdvMgfWriter.writeEverySpectrum(request.spectrumFile(), format, work, MGF).file();
        Path ids =
                Files.writeString(
                        work.resolve(IDS),
                        String.join("\n", request.spectrumIds()) + "\n",
                        StandardCharsets.UTF_8);
        ToolCommand command = command(request, mgf, ids, figures, home);
        ToolRunOutcome outcome = runner.run(command);
        return judge(request, figures, outcome);
    }

    /**
     * The argument array, working directory and environment of one PDV figure run.
     *
     * @param request the request
     * @param mgf the re-encoded spectra
     * @param ids the id list
     * @param figures PDV's output directory
     * @param home PDV's home directory
     * @return the command
     */
    static ToolCommand command(
            PdvFigureRequest request, Path mgf, Path ids, Path figures, Path home) {
        List<String> argv =
                List.of(
                        request.java().launcher().toString(),
                        "-Duser.home=" + home,
                        "-jar",
                        request.pdvJar().toString(),
                        "-r",
                        request.pepXml().toString(),
                        "-rt",
                        "2",
                        "-s",
                        mgf.toString(),
                        "-st",
                        "1",
                        "-i",
                        ids.toString(),
                        "-k",
                        "s",
                        "-o",
                        figures.toString(),
                        "-ft",
                        "png");
        Map<String, String> environment = new LinkedHashMap<>(request.displayEnvironment());
        environment.put("LANG", "C.UTF-8");
        environment.put("HOME", home.toString());
        return new ToolCommand(argv, request.workDirectory(), environment);
    }

    private static Path emptyDirectory(Path work) throws IOException {
        Files.createDirectories(work);
        try (Stream<Path> entries = Files.list(work)) {
            if (entries.findAny().isPresent()) {
                throw new IOException(
                        "the PDV work directory "
                                + work
                                + " is not empty, so the figures PDV writes could not be told"
                                + " from files already there");
            }
        }
        return work;
    }

    /**
     * Reads what PDV left in its output directory and decides.
     *
     * @param request the request
     * @param figures PDV's output directory
     * @param outcome the run's outcome
     * @return the verified figures, in request order
     * @throws PdvFigureException if the run failed, naming how
     * @throws IOException if the directory cannot be read
     */
    List<PdvFigure> judge(PdvFigureRequest request, Path figures, ToolRunOutcome outcome)
            throws IOException {
        if (outcome.timedOut()) {
            throw new PdvFigureException(
                    PdvFigureException.Failure.TIMED_OUT,
                    "PDV did not finish within "
                            + runner.timeout()
                            + " and was stopped"
                            + diagnostics(figures, outcome),
                    null);
        }
        Map<String, PngFigure> pngs = new TreeMap<>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(figures)) {
            for (Path file : files) {
                Path name = file.getFileName();
                if (name != null
                        && Files.isRegularFile(file)
                        && name.toString().toLowerCase(Locale.ROOT).endsWith(".png")) {
                    try {
                        pngs.put(name.toString(), PngFigure.read(file));
                    } catch (InvalidFigureException invalid) {
                        throw new PdvFigureException(
                                PdvFigureException.Failure.INVALID_FIGURE,
                                "PDV wrote a figure that is not valid: "
                                        + invalid.getMessage()
                                        + diagnostics(figures, outcome),
                                invalid);
                    }
                }
            }
        }
        Map<String, List<PdvFigure>> byId = new LinkedHashMap<>();
        for (String id : request.spectrumIds()) {
            byId.put(id, new ArrayList<>());
        }
        for (Map.Entry<String, PngFigure> png : pngs.entrySet()) {
            String owner = owner(png.getKey(), request.spectrumIds());
            if (owner == null) {
                throw new PdvFigureException(
                        PdvFigureException.Failure.INVALID_FIGURE,
                        "PDV wrote the figure "
                                + png.getValue().file()
                                + ", whose name ends with none of the requested spectrum ids"
                                + diagnostics(figures, outcome),
                        null);
            }
            String base = withoutExtension(png.getKey());
            String peptide = base.substring(0, base.length() - fileStem(owner).length());
            byId.get(owner).add(new PdvFigure(owner, peptide, png.getValue()));
        }
        List<String> missing = new ArrayList<>();
        List<PdvFigure> drawn = new ArrayList<>();
        for (Map.Entry<String, List<PdvFigure>> entry : byId.entrySet()) {
            if (entry.getValue().isEmpty()) {
                missing.add(entry.getKey());
            }
            drawn.addAll(entry.getValue());
        }
        int exit = outcome.exitCode().getAsInt();
        if (exit != 0) {
            throw new PdvFigureException(
                    PdvFigureException.Failure.EXITED_NON_ZERO,
                    "PDV exited with status "
                            + exit
                            + (missing.isEmpty()
                                    ? ""
                                    : " and drew no figure for " + quoted(missing))
                            + diagnostics(figures, outcome),
                    null);
        }
        if (!missing.isEmpty()) {
            throw new PdvFigureException(
                    PdvFigureException.Failure.NO_FIGURE,
                    "PDV exited 0 and wrote no figure for "
                            + quoted(missing)
                            + diagnostics(figures, outcome),
                    null);
        }
        List<String> reported = errorLines(figures);
        if (!reported.isEmpty()) {
            throw new PdvFigureException(
                    PdvFigureException.Failure.PDV_REPORTED_ERRORS,
                    "PDV drew every figure asked for but reported problems in its "
                            + ERROR_FILE
                            + diagnostics(figures, outcome),
                    null);
        }
        return List.copyOf(drawn);
    }

    /** The requested id a figure's name ends with, the longest winning; null if none. */
    static String owner(String fileName, List<String> ids) {
        String base = withoutExtension(fileName);
        String owner = null;
        for (String id : ids) {
            String stem = fileStem(id);
            if (base.endsWith(stem)
                    && (owner == null || stem.length() > fileStem(owner).length())) {
                owner = id;
            }
        }
        return owner;
    }

    private static String withoutExtension(String fileName) {
        return fileName.substring(0, fileName.length() - ".png".length());
    }

    /** A native id as PDV puts it in a file name. */
    static String fileStem(String id) {
        return STRIPPED.matcher(id).replaceAll("");
    }

    private static String quoted(List<String> ids) {
        return "\"" + String.join("\", \"", ids) + "\"";
    }

    private static List<String> errorLines(Path figures) throws IOException {
        Path errors = figures.resolve(ERROR_FILE);
        if (!Files.isRegularFile(errors)) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        for (String line : Files.readAllLines(errors, StandardCharsets.UTF_8)) {
            if (!line.isBlank()) {
                lines.add(line);
            }
        }
        return lines;
    }

    private static String diagnostics(Path figures, ToolRunOutcome outcome) throws IOException {
        StringBuilder text = new StringBuilder();
        Path errors = figures.resolve(ERROR_FILE);
        if (Files.isRegularFile(errors)) {
            List<String> lines = errorLines(figures);
            text.append(". Its ")
                    .append(ERROR_FILE)
                    .append(lines.isEmpty() ? " is empty" : " says: " + String.join(" | ", lines));
        } else {
            text.append(". It wrote no ").append(ERROR_FILE);
        }
        List<String> output = outcome.errorFirst();
        if (!output.isEmpty()) {
            int from = Math.max(0, output.size() - DIAGNOSTIC_LINES);
            text.append(". Its last output: ")
                    .append(String.join(" | ", output.subList(from, output.size())));
        }
        return text.toString();
    }
}
