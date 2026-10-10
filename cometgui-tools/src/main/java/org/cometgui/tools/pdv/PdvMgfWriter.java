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
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Writes the MGF PDV is given in place of a run's spectrum source (design decisions P11-11 and
 * P11-12).
 *
 * <h2>Every peak re-encoded, none altered</h2>
 *
 * <p>Each entry is {@code BEGIN IONS}, {@code TITLE=} the source's native id (so a drawn spectrum
 * names itself, and PDV's CLI finds it by the pepXML's {@code spectrumNativeID}), {@code PEPMASS=}
 * the source's selected-ion m/z <em>text</em>, {@code CHARGE=} when the source gives one, {@code
 * RTINSECONDS=} when it gives a retention time (minutes converted by exact decimal arithmetic), one
 * {@code m/z intensity} line per peak in the source's order, and {@code END IONS}. Lines end with
 * {@code \n}; the file is UTF-8.
 *
 * <p>A peak value is written so that parsing the text gives back <strong>exactly</strong> the
 * source's binary value, never in exponent notation:
 *
 * <ul>
 *   <li>a 64-bit value as its shortest round-trip decimal ({@link Double#toString}, which has been
 *       the shortest uniquely-identifying decimal since JDK 19), so {@code Double.parseDouble}
 *       returns the identical {@code double};
 *   <li>a 32-bit value as the <em>exact</em> decimal expansion of the {@code float}, so that both
 *       {@code Float.parseFloat} and {@code Double.parseDouble} return it exactly. The shortest
 *       {@code float} text would round-trip only through {@code Float.parseFloat}; PDV parses peaks
 *       as {@code double}s, and would then hold a value the source never had -- {@code 755.841}
 *       instead of {@code 755.84100341796875}.
 * </ul>
 *
 * <p>NaN and infinite values have no faithful MGF form and are refused, as are a spectrum with no
 * selected-ion m/z (PDV needs {@code PEPMASS}), a charge of zero, and a native id that is empty,
 * holds a line break or begins or ends with whitespace (PDV trims both the pepXML id and the title,
 * so such an id could never be found again).
 *
 * <h2>Two shapes</h2>
 *
 * <ul>
 *   <li>{@link #writeEverySpectrum}: one entry per MS2 spectrum of the source, in source order --
 *       the CLI figure path, where PDV finds a spectrum by its title.
 *   <li>{@link #writeOnePerRow}: one entry per requested row, in the order given -- the {@code
 *       denovo-gui} path, where PDV keeps only the first mzTab row per {@code spectra_ref}, so a
 *       spectrum that produced several PSMs is written once per PSM, each an exact copy, and every
 *       row gets its own {@code ms_run[k]:index=n}. Duplicate titles are therefore expected there.
 * </ul>
 *
 * <p>Both write to a temporary file in the target directory and move it into place atomically, so a
 * reader never sees a partial MGF under the final name; on any failure the temporary file is
 * deleted and nothing is left behind.
 */
public final class PdvMgfWriter {

    /** The extension PDV recognises an MGF by; it decides the format from the name alone. */
    public static final String EXTENSION = ".mgf";

    private PdvMgfWriter() {}

    /**
     * Writes one entry per MS2 spectrum of the source, in source order.
     *
     * @param source the run's spectrum source
     * @param format its format, as {@link PdvSpectrumSupport#check} decided it
     * @param directory an existing directory to write into
     * @param fileName the MGF's file name, ending {@code .mgf}
     * @return the file written and its titles
     * @throws SpectrumFileException if the source holds no MS2 spectrum, repeats a native id, or a
     *     spectrum cannot be written faithfully, naming the file and the spectrum
     * @throws IOException if the source cannot be read or the MGF cannot be written
     * @throws IllegalArgumentException if the file name is not a plain {@code .mgf} name
     * @throws NullPointerException if any argument is {@code null}
     */
    public static MgfFile writeEverySpectrum(
            Path source, SpectrumFileFormat format, Path directory, String fileName)
            throws IOException {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(format, "format");
        Path target = target(directory, fileName);
        List<String> titles = new ArrayList<>();
        Map<String, Integer> seen = new HashMap<>();
        Path written =
                atomically(
                        directory,
                        target,
                        out ->
                                SourceSpectra.read(
                                        source,
                                        format,
                                        spectrum -> {
                                            unique(source, spectrum, seen);
                                            out.write(entry(source, spectrum));
                                            titles.add(spectrum.nativeId());
                                        }),
                        () -> {
                            if (titles.isEmpty()) {
                                throw new SpectrumFileException(
                                        source, "holds no MS2 spectrum to give PDV");
                            }
                        });
        return new MgfFile(written, titles);
    }

    /**
     * Writes one entry per requested row, in the order given, each an exact copy of the source
     * spectrum with that native id.
     *
     * @param source the run's spectrum source
     * @param format its format, as {@link PdvSpectrumSupport#check} decided it
     * @param nativeIds one native id per row, in row order; an id may repeat
     * @param directory an existing directory to write into
     * @param fileName the MGF's file name, ending {@code .mgf}
     * @return the file written; entry {@code n} is row {@code n}
     * @throws SpectrumFileException if a requested id is not an MS2 spectrum of the source, the
     *     source repeats a native id, or a spectrum cannot be written faithfully
     * @throws IOException if the source cannot be read or the MGF cannot be written
     * @throws IllegalArgumentException if no row is requested or the file name is not a plain
     *     {@code .mgf} name
     * @throws NullPointerException if any argument or requested id is {@code null}
     */
    public static MgfFile writeOnePerRow(
            Path source,
            SpectrumFileFormat format,
            List<String> nativeIds,
            Path directory,
            String fileName)
            throws IOException {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(format, "format");
        List<String> rows = List.copyOf(Objects.requireNonNull(nativeIds, "nativeIds"));
        if (rows.isEmpty()) {
            throw new IllegalArgumentException(
                    "an MGF for PDV needs at least one row; PDV cannot open an empty one");
        }
        Path target = target(directory, fileName);
        Set<String> wanted = new HashSet<>(rows);
        Map<String, SourceSpectrum> found = new HashMap<>();
        Map<String, Integer> seen = new HashMap<>();
        SourceSpectra.read(
                source,
                format,
                spectrum -> {
                    unique(source, spectrum, seen);
                    if (wanted.contains(spectrum.nativeId())) {
                        found.put(spectrum.nativeId(), spectrum);
                    }
                });
        Map<String, String> rendered = new HashMap<>();
        for (int row = 0; row < rows.size(); row++) {
            String id = rows.get(row);
            SourceSpectrum spectrum = found.get(id);
            if (spectrum == null) {
                throw new SpectrumFileException(
                        source,
                        "row "
                                + row
                                + " asks for the spectrum \""
                                + id
                                + "\", which is not an MS2 spectrum in this file");
            }
            if (!rendered.containsKey(id)) {
                rendered.put(id, entry(source, spectrum));
            }
        }
        Path written =
                atomically(
                        directory,
                        target,
                        out -> {
                            for (String id : rows) {
                                out.write(rendered.get(id));
                            }
                        },
                        () -> {});
        return new MgfFile(written, rows);
    }

    /**
     * One MGF entry for one spectrum, terminated by a line break.
     *
     * @param source the file the spectrum came from, for a diagnostic
     * @param spectrum the spectrum
     * @return the entry's text
     * @throws SpectrumFileException if the spectrum cannot be written faithfully
     */
    static String entry(Path source, SourceSpectrum spectrum) throws SpectrumFileException {
        String id = spectrum.nativeId();
        if (id.contains("\n") || id.contains("\r") || !id.equals(id.strip())) {
            throw new SpectrumFileException(
                    source,
                    id,
                    spectrum.index(),
                    "has a native id that holds a line break or begins or ends with whitespace,"
                            + " which an MGF title cannot carry so that PDV finds it again");
        }
        String precursor =
                spectrum.precursorMz()
                        .orElseThrow(
                                () ->
                                        new SpectrumFileException(
                                                source,
                                                id,
                                                spectrum.index(),
                                                "has no selected ion m/z, which PDV needs as the"
                                                        + " MGF PEPMASS"));
        if (!MgfSpectrumReader.DECIMAL.matcher(precursor).matches()) {
            throw new SpectrumFileException(
                    source,
                    id,
                    spectrum.index(),
                    "has the selected ion m/z \""
                            + precursor
                            + "\", which is not a decimal number");
        }
        StringBuilder out = new StringBuilder();
        out.append("BEGIN IONS\nTITLE=").append(id).append('\n');
        out.append("PEPMASS=").append(precursor).append('\n');
        if (spectrum.charge().isPresent()) {
            int charge = spectrum.charge().getAsInt();
            if (charge == 0) {
                throw new SpectrumFileException(
                        source,
                        id,
                        spectrum.index(),
                        "states the precursor charge 0, which an MGF CHARGE cannot carry");
            }
            out.append("CHARGE=")
                    .append(Math.abs(charge))
                    .append(charge > 0 ? '+' : '-')
                    .append('\n');
        }
        if (spectrum.retentionTime().isPresent()) {
            out.append("RTINSECONDS=")
                    .append(spectrum.retentionTime().get().secondsText())
                    .append('\n');
        }
        PeakList peaks = spectrum.peaks();
        double[] mz = peaks.mz();
        double[] intensity = peaks.intensity();
        for (int i = 0; i < mz.length; i++) {
            try {
                out.append(decimal(mz[i], peaks.mzEncoding()))
                        .append(' ')
                        .append(decimal(intensity[i], peaks.intensityEncoding()))
                        .append('\n');
            } catch (IllegalArgumentException unwritable) {
                throw new SpectrumFileException(
                        source,
                        id,
                        spectrum.index(),
                        "peak " + i + " cannot be written faithfully: " + unwritable.getMessage());
            }
        }
        return out.append("END IONS\n").toString();
    }

    /**
     * The decimal text whose parse is exactly the value: see the class documentation.
     *
     * @param value the value
     * @param encoding how the source stored it
     * @return the text, in plain notation
     * @throws IllegalArgumentException if the value is NaN or infinite, or is said to be a 32-bit
     *     value and is not one
     */
    static String decimal(double value, PeakEncoding encoding) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(value + " has no faithful MGF form");
        }
        long bits = Double.doubleToRawLongBits(value);
        if ((bits & Long.MAX_VALUE) == 0L) {
            return bits == 0L ? "0" : "-0";
        }
        if (encoding == PeakEncoding.FLOAT32) {
            if (Double.doubleToRawLongBits((float) value) != bits) {
                throw new IllegalArgumentException(
                        value + " is said to be a 32-bit float and is not one");
            }
            return new BigDecimal(value).toPlainString();
        }
        return new BigDecimal(Double.toString(value)).toPlainString();
    }

    private static void unique(Path source, SourceSpectrum spectrum, Map<String, Integer> seen)
            throws SpectrumFileException {
        Integer earlier = seen.putIfAbsent(spectrum.nativeId(), spectrum.index());
        if (earlier != null) {
            throw new SpectrumFileException(
                    source,
                    spectrum.nativeId(),
                    spectrum.index(),
                    "repeats the native id of the spectrum at index "
                            + earlier
                            + ", so a title could not say which spectrum it is");
        }
    }

    private static Path target(Path directory, String fileName) throws IOException {
        Objects.requireNonNull(directory, "directory");
        Objects.requireNonNull(fileName, "fileName");
        Path name = Path.of(fileName).getFileName();
        if (name == null
                || !name.toString().equals(fileName)
                || !fileName.endsWith(EXTENSION)
                || fileName.length() == EXTENSION.length()
                || fileName.contains("_cus_")) {
            throw new IllegalArgumentException(
                    "an MGF for PDV needs a plain file name ending "
                            + EXTENSION
                            + " and not containing _cus_ (PDV's own key separator), but was \""
                            + fileName
                            + "\"");
        }
        if (!Files.isDirectory(directory)) {
            throw new IOException(
                    "cannot write the MGF for PDV into " + directory + ": it is not a directory");
        }
        return directory.resolve(fileName);
    }

    /** Writes the body to a temporary file, then moves it into place. */
    private interface Body {
        void write(Writer out) throws IOException;
    }

    /** Checks run after the body, before the move. */
    private interface Check {
        void run() throws IOException;
    }

    private static Path atomically(Path directory, Path target, Body body, Check check)
            throws IOException {
        Path partial =
                Files.createTempFile(directory, "." + target.getFileName() + ".", ".partial");
        boolean moved = false;
        try {
            try (Writer out = Files.newBufferedWriter(partial, StandardCharsets.UTF_8)) {
                body.write(out);
            }
            check.run();
            Files.move(
                    partial,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            moved = true;
            return target;
        } finally {
            if (!moved) {
                Files.deleteIfExists(partial);
            }
        }
    }
}
