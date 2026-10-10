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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.cometgui.domain.tools.ToolVersion;

/**
 * Which spectrum sources the selected PDV can be given, decided before anything is launched.
 *
 * <h2>What "can be given" means here (design decisions P11-7 and P11-11)</h2>
 *
 * <p>PDV is never handed the run's spectrum file itself. Phase 00 showed PDV 2.7.0's CLI exits 0
 * writing nothing on a scan-range subset mzML, and the Phase 11 spike showed its {@code denovo-gui}
 * mode draws nothing on the same file with an honest {@code spectra_ref} and reads no mzXML at all.
 * So CometGUI re-encodes every spectrum source as an MGF ({@link PdvMgfWriter}) and gives PDV that.
 * A source format is therefore supported for a PDV version when that version reads MGF <em>and</em>
 * CometGUI can re-encode the source faithfully. For PDV 2.7.0 that is {@link
 * SpectrumFileFormat#MZML} and {@link SpectrumFileFormat#MGF}:
 *
 * <ul>
 *   <li><strong>mzXML is refused, deliberately.</strong> PDV's CLI reads mzXML, but its {@code
 *       denovo-gui} mode does not (the spike, from {@code PDVMainClass.java:507}), and CometGUI
 *       does not re-encode it: an MGF title must repeat the native id Comet writes into the pepXML
 *       for the spectrum, and no mzXML has been searched by Comet in this project to establish what
 *       that is. Guessing it would be a title that silently matches nothing.
 *   <li>MS2, CMS2, BMS2 and Thermo RAW: PDV reads none of them.
 * </ul>
 *
 * <p>Only PDV 2.7.0 has been verified. Any other version is given no format at all, and says so,
 * rather than being assumed to behave like 2.7.0.
 *
 * <h2>Extension, then content</h2>
 *
 * <p>The format is the one the file's extension declares ({@link SpectrumFileFormat#ofFileName}),
 * which is how Comet itself decides. A file whose extension is supported then has its content
 * confirmed -- an mzML's first element must be {@code mzML} or {@code indexedmzML}; an MGF's first
 * line that is neither blank nor a comment must be {@code BEGIN IONS} or a {@code KEY=value}
 * parameter -- so that a mislabelled file is refused here, naming what it is, rather than failing
 * half-way through a re-encoding or, worse, inside PDV.
 */
public final class PdvSpectrumSupport {

    /** The one PDV version whose spectrum handling has been verified. */
    public static final ToolVersion VERIFIED_VERSION = ToolVersion.parse("2.7.0");

    /** How many bytes of a file are read to confirm its content. */
    static final int SNIFF_BYTES = 64 * 1024;

    private final ToolVersion pdvVersion;
    private final EnumSet<SpectrumFileFormat> supported;

    private PdvSpectrumSupport(ToolVersion pdvVersion, EnumSet<SpectrumFileFormat> supported) {
        this.pdvVersion = pdvVersion;
        this.supported = supported;
    }

    /**
     * The support of one PDV version.
     *
     * @param pdvVersion the selected PDV version
     * @return its support: mzML and MGF for 2.7.0, nothing for any version not verified
     * @throws NullPointerException if {@code pdvVersion} is {@code null}
     */
    public static PdvSpectrumSupport forVersion(ToolVersion pdvVersion) {
        Objects.requireNonNull(pdvVersion, "pdvVersion");
        EnumSet<SpectrumFileFormat> formats =
                VERIFIED_VERSION.equals(pdvVersion)
                        ? EnumSet.of(SpectrumFileFormat.MZML, SpectrumFileFormat.MGF)
                        : EnumSet.noneOf(SpectrumFileFormat.class);
        return new PdvSpectrumSupport(pdvVersion, formats);
    }

    /**
     * The PDV version this describes.
     *
     * @return the version
     */
    public ToolVersion pdvVersion() {
        return pdvVersion;
    }

    /**
     * The source formats this PDV version can be given, through CometGUI's MGF re-encoding.
     *
     * @return the formats, unmodifiable, in declaration order
     */
    public Set<SpectrumFileFormat> supported() {
        return Collections.unmodifiableSet(EnumSet.copyOf(supported));
    }

    /**
     * Decides whether a run's spectrum source can be given to this PDV version.
     *
     * @param spectrumFile the source the run searched
     * @return its format, which is one of {@link #supported()}
     * @throws UnsupportedSpectrumFileException the specified error, when it cannot be: naming the
     *     file, its format, this PDV version and what this version can be given
     * @throws IOException if the file does not exist or cannot be read
     * @throws NullPointerException if {@code spectrumFile} is {@code null}
     */
    public SpectrumFileFormat check(Path spectrumFile) throws IOException {
        Objects.requireNonNull(spectrumFile, "spectrumFile");
        Path name = spectrumFile.getFileName();
        SpectrumFileFormat format =
                SpectrumFileFormat.ofFileName(name == null ? "" : name.toString()).orElse(null);
        if (format == null) {
            throw refusal(
                    spectrumFile,
                    null,
                    "its name ends in none of the extensions Comet reads ("
                            + extensions()
                            + "), so it is not a spectrum file CometGUI knows");
        }
        if (!supported.contains(format)) {
            throw refusal(spectrumFile, format, reason(format));
        }
        byte[] head = head(spectrumFile);
        String mismatch =
                format == SpectrumFileFormat.MZML ? notMzMl(head) : notMgf(head, spectrumFile);
        if (mismatch != null) {
            throw refusal(
                    spectrumFile,
                    format,
                    "it is named as "
                            + article(format)
                            + " file but its content is not "
                            + format.displayName()
                            + ": "
                            + mismatch);
        }
        return format;
    }

    private String reason(SpectrumFileFormat format) {
        if (!VERIFIED_VERSION.equals(pdvVersion)) {
            return "it is "
                    + article(format)
                    + " file, and CometGUI has verified which spectrum formats PDV can be given"
                    + " only for PDV "
                    + VERIFIED_VERSION.text();
        }
        if (format == SpectrumFileFormat.MZXML) {
            return "it is an mzXML file, which PDV "
                    + pdvVersion.text()
                    + "'s denovo-gui mode cannot read and which CometGUI does not yet re-encode"
                    + " for it: the MGF title must repeat the native id Comet records for each"
                    + " spectrum, and what Comet records for an mzXML spectrum has not been"
                    + " established";
        }
        return "it is "
                + article(format)
                + " file, and PDV "
                + pdvVersion.text()
                + " reads only MGF, mzML and mzXML spectrum files";
    }

    /** "an mzML", "an MGF", "an MS2", but "a CMS2", "a BMS2", "a Thermo RAW". */
    private static String article(SpectrumFileFormat format) {
        return (format.displayName().startsWith("m") || format.displayName().startsWith("M")
                        ? "an "
                        : "a ")
                + format.displayName();
    }

    private UnsupportedSpectrumFileException refusal(
            Path file, SpectrumFileFormat format, String reason) {
        return new UnsupportedSpectrumFileException(
                file,
                format,
                pdvVersion,
                "The spectrum file "
                        + file
                        + " cannot be visualised by PDV "
                        + pdvVersion.text()
                        + ": "
                        + reason
                        + ". "
                        + offer());
    }

    private String offer() {
        if (supported.isEmpty()) {
            return "PDV "
                    + pdvVersion.text()
                    + " can be given no spectrum format until it is verified; PDV "
                    + VERIFIED_VERSION.text()
                    + " can be given mzML and MGF.";
        }
        List<String> names = new ArrayList<>();
        for (SpectrumFileFormat format : supported) {
            names.add(format.displayName());
        }
        return "PDV "
                + pdvVersion.text()
                + " can be given "
                + String.join(" and ", names)
                + " spectrum files, which CometGUI re-encodes as MGF for it.";
    }

    private static String extensions() {
        List<String> all = new ArrayList<>();
        for (SpectrumFileFormat format : SpectrumFileFormat.values()) {
            all.add(format.extension());
        }
        return String.join(", ", all);
    }

    private static byte[] head(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            return in.readNBytes(SNIFF_BYTES);
        }
    }

    /** Returns why the bytes are not the start of an mzML document, or null if they are. */
    static String notMzMl(byte[] head) {
        XMLInputFactory factory = MzMlSpectrumReader.xmlFactory();
        try {
            XMLStreamReader reader = factory.createXMLStreamReader(new ByteArrayInputStream(head));
            try {
                reader.nextTag();
                String root = reader.getLocalName();
                return "mzML".equals(root) || "indexedmzML".equals(root)
                        ? null
                        : "its first element is <" + root + ">, not <mzML>";
            } finally {
                reader.close();
            }
        } catch (XMLStreamException notXml) {
            return "it does not begin as XML (" + notXml.getMessage().strip() + ")";
        }
    }

    /** Returns why the bytes are not the start of an MGF file, or null if they are. */
    static String notMgf(byte[] head, Path file) throws IOException {
        try (BufferedReader lines =
                new BufferedReader(
                        new InputStreamReader(
                                new ByteArrayInputStream(head), StandardCharsets.ISO_8859_1))) {
            String line;
            while ((line = lines.readLine()) != null) {
                String text = line.strip();
                if (text.isEmpty() || MgfSpectrumReader.isComment(text)) {
                    continue;
                }
                if (MgfSpectrumReader.BEGIN.equals(text)
                        || MgfSpectrumReader.PARAMETER.matcher(text).matches()) {
                    return null;
                }
                return "its first line of content is \""
                        + abbreviated(text)
                        + "\", which is neither BEGIN IONS nor a KEY=value parameter";
            }
        }
        return Files.size(file) == 0
                ? "it is empty"
                : "it holds nothing but blank and comment lines";
    }

    private static String abbreviated(String text) {
        return text.length() <= 60 ? text : text.substring(0, 60) + "...";
    }
}
