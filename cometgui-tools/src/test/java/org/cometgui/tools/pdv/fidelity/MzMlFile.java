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

package org.cometgui.tools.pdv.fidelity;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * An mzML file read by the fidelity suite's own reader: StAX for the structure, and its own base64,
 * zlib and little-endian float decoding for the peaks (design decision P11-9). Nothing is shared
 * with the product's spectrum readers.
 */
final class MzMlFile {

    /**
     * One spectrum.
     *
     * @param position its 1-based position among the file's spectra, counted here
     * @param index the {@code index} attribute the file states
     * @param id the native id
     * @param msLevel the {@code ms level} value, as text
     * @param selectedIonMz the first selected ion's m/z, as text, or {@code null}
     * @param mz every m/z value, as the binary value (a 32-bit value widened exactly)
     * @param intensity every intensity value, likewise
     * @param wide whether both arrays were 64-bit
     */
    record Spectrum(
            int position,
            String index,
            String id,
            String msLevel,
            String selectedIonMz,
            double[] mz,
            double[] intensity,
            boolean wide) {}

    private final Path file;
    private final List<Spectrum> spectra;
    private final Map<String, List<Spectrum>> byId = new HashMap<>();

    private MzMlFile(Path file, List<Spectrum> spectra) {
        this.file = file;
        this.spectra = List.copyOf(spectra);
        for (Spectrum spectrum : spectra) {
            byId.computeIfAbsent(spectrum.id(), ignored -> new ArrayList<>()).add(spectrum);
        }
    }

    Path file() {
        return file;
    }

    List<Spectrum> spectra() {
        return spectra;
    }

    /**
     * The spectra with a native id.
     *
     * @param id the id
     * @return every spectrum with it
     */
    List<Spectrum> withId(String id) {
        return byId.getOrDefault(id, List.of());
    }

    /**
     * The scan number a Thermo-style native id carries.
     *
     * @param id the native id
     * @return the number after {@code scan=}, or -1 when there is none
     */
    static long scanOf(String id) {
        for (String part : id.split(" ")) {
            if (part.startsWith("scan=")) {
                return Long.parseLong(part.substring("scan=".length()));
            }
        }
        return -1;
    }

    /**
     * Reads an mzML file.
     *
     * @param file the file
     * @return its spectra
     * @throws IOException if it cannot be read or decoded
     */
    static MzMlFile read(Path file) throws IOException {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        List<Spectrum> spectra = new ArrayList<>();
        try (InputStream in = Files.newInputStream(file)) {
            XMLStreamReader xml = factory.createXMLStreamReader(in);
            try {
                read(xml, spectra);
            } finally {
                xml.close();
            }
        } catch (XMLStreamException | DataFormatException broken) {
            throw new IOException(file + " is not readable mzML: " + broken.getMessage(), broken);
        }
        return new MzMlFile(file, spectra);
    }

    private static void read(XMLStreamReader xml, List<Spectrum> spectra)
            throws XMLStreamException, DataFormatException, IOException {
        String[] spectrum = null;
        boolean inSelectedIon = false;
        boolean inArray = false;
        boolean wideArray = false;
        boolean zlib = false;
        boolean compressionStated = false;
        boolean widthStated = false;
        String arrayKind = null;
        double[] mz = null;
        double[] intensity = null;
        boolean mzWide = false;
        boolean intensityWide = false;
        StringBuilder binary = null;
        while (xml.hasNext()) {
            int event = xml.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                String name = xml.getLocalName();
                switch (name) {
                    case "spectrum" -> {
                        spectrum =
                                new String[] {
                                    xml.getAttributeValue(null, "index"),
                                    xml.getAttributeValue(null, "id"),
                                    null,
                                    null
                                };
                        mz = null;
                        intensity = null;
                    }
                    case "selectedIon" -> inSelectedIon = true;
                    case "binaryDataArray" -> {
                        inArray = spectrum != null;
                        widthStated = false;
                        compressionStated = false;
                        arrayKind = null;
                    }
                    case "binary" -> binary = new StringBuilder();
                    case "cvParam" -> {
                        if (spectrum == null) {
                            break;
                        }
                        String accession = xml.getAttributeValue(null, "accession");
                        String value = xml.getAttributeValue(null, "value");
                        if (inArray) {
                            switch (accession) {
                                case "MS:1000523" -> {
                                    wideArray = true;
                                    widthStated = true;
                                }
                                case "MS:1000521" -> {
                                    wideArray = false;
                                    widthStated = true;
                                }
                                case "MS:1000574" -> {
                                    zlib = true;
                                    compressionStated = true;
                                }
                                case "MS:1000576" -> {
                                    zlib = false;
                                    compressionStated = true;
                                }
                                case "MS:1000514" -> arrayKind = "mz";
                                case "MS:1000515" -> arrayKind = "intensity";
                                default -> {
                                    // no other array parameter changes the values
                                }
                            }
                        } else if ("MS:1000511".equals(accession)) {
                            spectrum[2] = value;
                        } else if (inSelectedIon
                                && "MS:1000744".equals(accession)
                                && spectrum[3] == null) {
                            spectrum[3] = value;
                        }
                    }
                    default -> {
                        // nothing else is read
                    }
                }
            } else if (event == XMLStreamConstants.CHARACTERS && binary != null) {
                binary.append(xml.getText());
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                switch (xml.getLocalName()) {
                    case "selectedIon" -> inSelectedIon = false;
                    case "binary" -> {
                        if (inArray) {
                            if (!widthStated || !compressionStated || arrayKind == null) {
                                throw new IOException(
                                        "spectrum "
                                                + (spectrum == null ? "?" : spectrum[1])
                                                + " has an array whose width, compression or kind"
                                                + " is not stated");
                            }
                            double[] values = decode(binary.toString(), wideArray, zlib);
                            if ("mz".equals(arrayKind)) {
                                mz = values;
                                mzWide = wideArray;
                            } else {
                                intensity = values;
                                intensityWide = wideArray;
                            }
                        }
                        binary = null;
                    }
                    case "binaryDataArray" -> inArray = false;
                    case "spectrum" -> {
                        if (spectrum == null || mz == null || intensity == null) {
                            throw new IOException("a spectrum without both peak arrays");
                        }
                        if (mz.length != intensity.length) {
                            throw new IOException(
                                    "spectrum " + spectrum[1] + " has arrays of unequal length");
                        }
                        spectra.add(
                                new Spectrum(
                                        spectra.size() + 1,
                                        spectrum[0],
                                        spectrum[1],
                                        spectrum[2],
                                        spectrum[3],
                                        mz,
                                        intensity,
                                        mzWide && intensityWide));
                        spectrum = null;
                    }
                    default -> {
                        // nothing else closes anything the reader tracks
                    }
                }
            }
        }
    }

    /**
     * Decodes one binary array: base64, then zlib if stated, then little-endian IEEE 754 values.
     *
     * @param text the element's text
     * @param wide 64-bit when {@code true}, else 32-bit
     * @param zlib whether the bytes are zlib-compressed
     * @return the values; a 32-bit value is widened to the double of exactly the same value
     */
    static double[] decode(String text, boolean wide, boolean zlib) throws DataFormatException {
        byte[] bytes = Base64.getMimeDecoder().decode(text.strip());
        if (zlib) {
            Inflater inflater = new Inflater();
            try {
                inflater.setInput(bytes);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                while (!inflater.finished()) {
                    int count = inflater.inflate(buffer);
                    if (count == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                        throw new DataFormatException("truncated zlib stream");
                    }
                    out.write(buffer, 0, count);
                }
                bytes = out.toByteArray();
            } finally {
                inflater.end();
            }
        }
        int width = wide ? Double.BYTES : Float.BYTES;
        if (bytes.length % width != 0) {
            throw new DataFormatException(bytes.length + " bytes is not a whole number of values");
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        double[] values = new double[bytes.length / width];
        for (int index = 0; index < values.length; index++) {
            values[index] = wide ? buffer.getDouble() : buffer.getFloat();
        }
        return values;
    }
}
