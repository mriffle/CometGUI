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

package org.cometgui.workflow.pdv;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import org.cometgui.domain.ports.ProcessRunner;
import org.cometgui.domain.run.SpectrumInput;
import org.cometgui.results.parser.SpectrumReference;
import org.cometgui.tools.pdv.PdvFigure;
import org.cometgui.tools.pdv.PdvFigureRequest;
import org.cometgui.tools.pdv.PdvFigures;
import org.cometgui.tools.pdv.PdvSpectrumSupport;
import org.cometgui.tools.pdv.SourceSpectra;
import org.cometgui.tools.pdv.SpectrumFileFormat;

/**
 * Draws one PSM's annotated spectrum with PDV's command line, from the run's own Comet pepXML and
 * spectrum file -- the "selected source/spectrum context ... where a robust documented mapping
 * exists" ({@code PDV Integration}, <em>Baseline integration</em>).
 *
 * <h2>The mapping, and where it is refused</h2>
 *
 * <ol>
 *   <li>The PSM's identifier gives its {@code -N} base, scan and charge ({@link
 *       SpectrumReference}); one that does not is refused.
 *   <li>The base is the one the run gave Comet for exactly one of its spectrum files ({@code
 *       outputs/comet/<base>} of the searched run); the pepXML is {@code <base>.pep.xml} beside it.
 *   <li>The spectrum file is checked against the installed PDV version <strong>before anything is
 *       launched</strong> ({@link PdvSpectrumSupport#check}): an unsupported file is refused with
 *       that class's specified error.
 *   <li>The scan maps to the one spectrum of the file whose native id carries it as {@code
 *       scan=<n>} -- the Thermo form Comet copies into the pepXML as {@code spectrumNativeID}, and
 *       the documented mapping. No such spectrum, or more than one, is refused, naming the scan:
 *       there is then no robust mapping, and guessing one would draw some other spectrum.
 * </ol>
 *
 * <p>Then PDV draws that native id ({@link PdvFigures}) in a new, empty directory under the run's
 * {@code pdv/figures/} (design decision P11-17: PDV writes beside its inputs and into its home, so
 * none of that is beside the source spectra) -- through a runner from {@link PdvLaunches}, so the
 * launch is in the run's event log before PDV starts. Its failures are {@code PdvFigures}' own,
 * unchanged.
 */
public final class PsmFigures {

    /** The run's PDV directory. */
    public static final String PDV_DIRECTORY = "pdv";

    /** Under it, one numbered directory per figure run. */
    public static final String FIGURES_DIRECTORY = "figures";

    /** The context keys a figure launch records. */
    static final String PSM_ID = "psm-id";

    static final String NATIVE_ID = "native-id";

    static final String SPECTRUM_FILE = "spectrum-file";

    static final String PEPXML = "pepxml";

    private final PdvLaunches launches;

    private final Duration timeout;

    /**
     * Figures through the one launch path.
     *
     * @param launches the one way PDV is launched
     * @param timeout how long one PDV run is given
     */
    public PsmFigures(PdvLaunches launches, Duration timeout) {
        this.launches = Objects.requireNonNull(launches, "launches");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
    }

    /**
     * Draws the PSM's annotated spectrum.
     *
     * @param request what to draw, and with what
     * @return the verified figure
     * @throws SpectrumNotDrawnException before anything is launched, if the PSM has no documented
     *     mapping to one spectrum
     * @throws org.cometgui.tools.pdv.UnsupportedSpectrumFileException before anything is launched,
     *     if the installed PDV cannot be given the spectrum file
     * @throws org.cometgui.tools.pdv.PdvFigureException if PDV was launched and did not draw it
     * @throws IOException if a file cannot be read or written, the launch cannot be recorded, or
     *     PDV cannot be started
     */
    public DrawnSpectrum draw(PsmFigureRequest request) throws IOException {
        Objects.requireNonNull(request, "request");
        String psmId = request.psmId();
        SpectrumReference reference =
                SpectrumReference.of(psmId)
                        .orElseThrow(
                                () ->
                                        new SpectrumNotDrawnException(
                                                SpectrumNotDrawnException.Reason
                                                        .NO_SPECTRUM_REFERENCE,
                                                "The PSM \""
                                                        + psmId
                                                        + "\" names no spectrum: its identifier"
                                                        + " has no file base, scan and charge, so"
                                                        + " no spectrum can be drawn for it."));
        SpectrumInput input = inputOf(request, reference);
        Path spectrumFile = input.file().path();
        Path pepXml = request.searched().pepXmlFile(input.base());
        if (!Files.isRegularFile(pepXml)) {
            throw new SpectrumNotDrawnException(
                    SpectrumNotDrawnException.Reason.NO_PEPXML,
                    "The Comet pepXML "
                            + pepXml
                            + " that PDV draws PSM "
                            + psmId
                            + " from is missing, so its spectrum cannot be drawn.");
        }
        PdvInstallation pdv = request.pdv();
        SpectrumFileFormat format =
                PdvSpectrumSupport.forVersion(pdv.version()).check(spectrumFile);
        String nativeId = nativeIdOf(spectrumFile, format, reference.scan(), psmId);
        Path work = newWorkDirectory(request.figuresDirectory());
        Map<String, String> context = new LinkedHashMap<>();
        context.put(PSM_ID, psmId);
        context.put(NATIVE_ID, nativeId);
        context.put(SPECTRUM_FILE, spectrumFile.toString());
        context.put(PEPXML, pepXml.toString());
        ProcessRunner recorded =
                launches.forRun(
                        request.run(),
                        request.identity().runId(),
                        pdv,
                        PdvLaunchKind.CLI_FIGURE,
                        context);
        List<PdvFigure> drawn =
                new PdvFigures(recorded, timeout)
                        .generate(
                                new PdvFigureRequest(
                                        request.java(),
                                        pdv.jar(),
                                        pdv.version(),
                                        pepXml,
                                        spectrumFile,
                                        List.of(nativeId),
                                        work,
                                        request.displayEnvironment()));
        PdvFigure figure = drawn.get(0);
        return new DrawnSpectrum(
                psmId,
                nativeId,
                spectrumFile,
                pepXml,
                figure.peptide(),
                figure.figure().file(),
                figure.figure().width(),
                figure.figure().height());
    }

    private static SpectrumInput inputOf(PsmFigureRequest request, SpectrumReference reference)
            throws SpectrumNotDrawnException {
        for (SpectrumInput input : request.identity().spectra()) {
            if (request.searched()
                    .cometOutputBase(input.base())
                    .toString()
                    .equals(reference.base())) {
                return input;
            }
        }
        throw new SpectrumNotDrawnException(
                SpectrumNotDrawnException.Reason.NO_SPECTRUM_FILE,
                "The PSM \""
                        + request.psmId()
                        + "\" was searched as "
                        + reference.base()
                        + ", which is none of run "
                        + request.identity().runId().value()
                        + "'s spectrum files, so there is no spectrum file to draw it from.");
    }

    /** The one spectrum whose native id carries the scan, or a refusal naming it. */
    static String nativeIdOf(Path file, SpectrumFileFormat format, long scan, String psmId)
            throws IOException {
        List<String> carrying = new ArrayList<>();
        SourceSpectra.read(
                file,
                format,
                spectrum -> {
                    OptionalLong number = spectrum.scanNumber();
                    if (number.isPresent() && number.getAsLong() == scan) {
                        carrying.add(spectrum.nativeId());
                    }
                });
        Optional<String> only =
                carrying.size() == 1 ? Optional.of(carrying.get(0)) : Optional.empty();
        if (only.isPresent()) {
            return only.get();
        }
        throw new SpectrumNotDrawnException(
                SpectrumNotDrawnException.Reason.NO_DOCUMENTED_MAPPING,
                "PSM "
                        + psmId
                        + " names scan "
                        + scan
                        + " of "
                        + file
                        + ", and "
                        + (carrying.isEmpty()
                                ? "no spectrum there carries that scan number"
                                : String.format(
                                                Locale.ROOT,
                                                "%d spectra there carry",
                                                carrying.size())
                                        + " that scan number")
                        + " in its native id (scan="
                        + scan
                        + "), so there is no documented mapping from the PSM to one spectrum and"
                        + " nothing was drawn.");
    }

    /** A new, empty, numbered directory under the run's figures directory. */
    static Path newWorkDirectory(Path figures) throws IOException {
        Files.createDirectories(figures);
        for (int number = 1; ; number++) {
            try {
                return Files.createDirectory(
                        figures.resolve(String.format(Locale.ROOT, "%04d", number)));
            } catch (FileAlreadyExistsException taken) {
                // an earlier figure's directory: the next number
            }
        }
    }
}
