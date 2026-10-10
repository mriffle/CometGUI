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
import java.util.Set;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;
import javax.xml.XMLConstants;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * TEST-SIDE: the peaks and selected-ion m/z of chosen mzML spectra, read here with StAX, base64,
 * zlib and {@link ByteBuffer} and nothing of the product's, so that what PDV drew is compared with
 * the source file itself rather than with the MGF the product wrote from it.
 *
 * <p>Handles what the K562 fixture holds: 32- or 64-bit little-endian arrays, zlib or uncompressed.
 */
final class MzMlPeaks {

    /** One spectrum: its native id, its selected-ion m/z text, and its peaks as doubles. */
    record Spectrum(String id, String selectedIonMz, List<double[]> peaks) {}

    private MzMlPeaks() {}

    /**
     * Reads the spectra whose native ids end with {@code scan=<n>} for the given scans.
     *
     * @param file the mzML
     * @param scans the instrument scan numbers
     * @return scan number to spectrum
     * @throws IOException if the file cannot be read or decoded
     */
    static Map<Long, Spectrum> byScan(Path file, Set<Long> scans) throws IOException {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        Map<Long, Spectrum> found = new HashMap<>();
        try (InputStream in = Files.newInputStream(file)) {
            XMLStreamReader xml = factory.createXMLStreamReader(in);
            String id = null;
            String selected = null;
            double[] mz = null;
            double[] intensity = null;
            boolean wide = false;
            boolean zlib = false;
            String array = null;
            boolean wanted = false;
            while (xml.hasNext()) {
                int event = xml.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    String name = xml.getLocalName();
                    if ("spectrum".equals(name)) {
                        id = xml.getAttributeValue(null, "id");
                        wanted = scans.contains(scanOf(id));
                        selected = null;
                        mz = null;
                        intensity = null;
                    } else if (wanted && "binaryDataArray".equals(name)) {
                        wide = false;
                        zlib = false;
                        array = null;
                    } else if (wanted && "cvParam".equals(name)) {
                        String accession = xml.getAttributeValue(null, "accession");
                        switch (accession) {
                            case "MS:1000744" -> selected = xml.getAttributeValue(null, "value");
                            case "MS:1000523" -> wide = true;
                            case "MS:1000521" -> wide = false;
                            case "MS:1000574" -> zlib = true;
                            case "MS:1000576" -> zlib = false;
                            case "MS:1000514" -> array = "mz";
                            case "MS:1000515" -> array = "intensity";
                            default -> {
                                // not needed
                            }
                        }
                    } else if (wanted && "binary".equals(name)) {
                        double[] values = decode(xml.getElementText().strip(), wide, zlib);
                        if ("mz".equals(array)) {
                            mz = values;
                        } else if ("intensity".equals(array)) {
                            intensity = values;
                        }
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT
                        && xml.getLocalName().equals("spectrum")
                        && wanted) {
                    if (mz == null || intensity == null || mz.length != intensity.length) {
                        throw new IOException("spectrum " + id + " has no matching peak arrays");
                    }
                    List<double[]> peaks = new ArrayList<>(mz.length);
                    for (int index = 0; index < mz.length; index++) {
                        peaks.add(new double[] {mz[index], intensity[index]});
                    }
                    if (found.put(scanOf(id), new Spectrum(id, selected, peaks)) != null) {
                        throw new IOException("two spectra carry scan " + scanOf(id));
                    }
                    wanted = false;
                }
            }
        } catch (XMLStreamException | DataFormatException unreadable) {
            throw new IOException(file + " could not be read: " + unreadable, unreadable);
        }
        return found;
    }

    static long scanOf(String id) {
        int at = id.lastIndexOf("scan=");
        return at < 0 ? -1 : Long.parseLong(id.substring(at + 5).split(" ")[0]);
    }

    private static double[] decode(String text, boolean wide, boolean zlib)
            throws DataFormatException {
        byte[] bytes = Base64.getDecoder().decode(text);
        if (zlib) {
            Inflater inflater = new Inflater();
            try {
                inflater.setInput(bytes);
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                byte[] chunk = new byte[65_536];
                while (!inflater.finished()) {
                    int read = inflater.inflate(chunk);
                    if (read == 0 && inflater.needsInput()) {
                        throw new DataFormatException("truncated zlib stream");
                    }
                    out.write(chunk, 0, read);
                }
                bytes = out.toByteArray();
            } finally {
                inflater.end();
            }
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        double[] values = new double[bytes.length / (wide ? 8 : 4)];
        for (int index = 0; index < values.length; index++) {
            values[index] = wide ? buffer.getDouble() : buffer.getFloat();
        }
        return values;
    }
}
