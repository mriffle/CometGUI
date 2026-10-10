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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.zip.Deflater;

/**
 * Small, constructed, CometGUI-authored mzML documents for the reader's tests.
 *
 * <p>Nothing here comes from a real instrument: every value is typed in a test, so the fixture can
 * be committed and every encoding the reader must handle -- or refuse -- can be produced on demand.
 * The encoder is written independently of the reader: little-endian IEEE 754 through {@link
 * ByteBuffer}, {@link Deflater} for zlib, {@link Base64} for the text.
 */
final class MzMlText {

    private final List<String> groups = new ArrayList<>();
    private final List<String> spectra = new ArrayList<>();
    private boolean indexed;
    private String rootOverride;

    static MzMlText document() {
        return new MzMlText();
    }

    MzMlText indexed() {
        indexed = true;
        return this;
    }

    MzMlText root(String element) {
        rootOverride = element;
        return this;
    }

    MzMlText group(String id, String... cvParams) {
        groups.add(
                "<referenceableParamGroup id=\""
                        + id
                        + "\">"
                        + String.join("", cvParams)
                        + "</referenceableParamGroup>");
        return this;
    }

    MzMlText spectrum(String xml) {
        spectra.add(xml);
        return this;
    }

    String text() {
        String root = rootOverride != null ? rootOverride : "mzML";
        String body =
                "<"
                        + root
                        + " xmlns=\"http://psi.hupo.org/ms/mzml\" version=\"1.1.0\">"
                        + (groups.isEmpty()
                                ? ""
                                : "<referenceableParamGroupList count=\""
                                        + groups.size()
                                        + "\">"
                                        + String.join("", groups)
                                        + "</referenceableParamGroupList>")
                        + "<run id=\"r\"><spectrumList count=\""
                        + spectra.size()
                        + "\">\n"
                        + String.join("\n", spectra)
                        + "\n</spectrumList></run></"
                        + root
                        + ">";
        String xml =
                indexed
                        ? "<indexedmzML xmlns=\"http://psi.hupo.org/ms/mzml\">"
                                + body
                                + "<indexList count=\"0\"/></indexedmzML>"
                        : body;
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" + xml + "\n";
    }

    Path write(Path directory, String name) throws IOException {
        return Files.writeString(directory.resolve(name), text(), StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------ pieces of a spectrum

    static String cv(String accession, String value) {
        return "<cvParam cvRef=\"MS\" accession=\""
                + accession
                + "\" name=\"x\" value=\""
                + value
                + "\"/>";
    }

    static String cv(String accession) {
        return cv(accession, "");
    }

    static String cvUnit(String accession, String value, String unitAccession, String unitName) {
        return "<cvParam cvRef=\"MS\" accession=\""
                + accession
                + "\" name=\"x\" value=\""
                + value
                + "\""
                + (unitAccession == null ? "" : " unitAccession=\"" + unitAccession + "\"")
                + (unitName == null ? "" : " unitName=\"" + unitName + "\"")
                + "/>";
    }

    /** An MS2 spectrum with the usual parts; any part may be empty. */
    static String spectrum(
            int index,
            String id,
            int defaultArrayLength,
            String spectrumParams,
            String scanParams,
            String selectedIons,
            String arrays) {
        return "<spectrum index=\""
                + index
                + "\" id=\""
                + id
                + "\" defaultArrayLength=\""
                + defaultArrayLength
                + "\">"
                + spectrumParams
                + (scanParams.isEmpty()
                        ? ""
                        : "<scanList count=\"1\">"
                                + cv("MS:1000795")
                                + "<scan>"
                                + scanParams
                                + "</scan></scanList>")
                + (selectedIons.isEmpty()
                        ? ""
                        : "<precursorList count=\"1\"><precursor><isolationWindow>"
                                + cv("MS:1000827", "999.0")
                                + "</isolationWindow><selectedIonList count=\"1\">"
                                + selectedIons
                                + "</selectedIonList></precursor></precursorList>")
                + "<binaryDataArrayList count=\"2\">"
                + arrays
                + "</binaryDataArrayList></spectrum>";
    }

    static String selectedIon(String mz, String charge) {
        return "<selectedIon>"
                + (mz == null ? "" : cv(MzMlSpectrumReader.SELECTED_ION_MZ, mz))
                + (charge == null ? "" : cv(MzMlSpectrumReader.CHARGE_STATE, charge))
                + "</selectedIon>";
    }

    static String level(int msLevel) {
        return cv(MzMlSpectrumReader.MS_LEVEL, Integer.toString(msLevel));
    }

    static String minutes(String value) {
        return cvUnit(
                MzMlSpectrumReader.SCAN_START_TIME,
                value,
                MzMlSpectrumReader.UNIT_MINUTE,
                "minute");
    }

    static String seconds(String value) {
        return cvUnit(
                MzMlSpectrumReader.SCAN_START_TIME,
                value,
                MzMlSpectrumReader.UNIT_SECOND,
                "second");
    }

    /** A binary data array, with its parameters and payload exactly as given. */
    static String array(List<String> params, String base64, String arrayLength) {
        return "<binaryDataArray encodedLength=\""
                + base64.length()
                + "\""
                + (arrayLength == null ? "" : " arrayLength=\"" + arrayLength + "\"")
                + ">"
                + String.join("", params)
                + "<binary>"
                + base64
                + "</binary></binaryDataArray>";
    }

    /** A well-formed m/z or intensity array of 64-bit floats. */
    static String doubles(String kind, boolean zlib, double... values) {
        return array(
                List.of(
                        cv(MzMlSpectrumReader.FLOAT64),
                        cv(zlib ? MzMlSpectrumReader.ZLIB : MzMlSpectrumReader.NO_COMPRESSION),
                        cv(kind)),
                base64(zlib, encode64(values)),
                null);
    }

    /** A well-formed m/z or intensity array of 32-bit floats. */
    static String floats(String kind, boolean zlib, float... values) {
        return array(
                List.of(
                        cv(MzMlSpectrumReader.FLOAT32),
                        cv(zlib ? MzMlSpectrumReader.ZLIB : MzMlSpectrumReader.NO_COMPRESSION),
                        cv(kind)),
                base64(zlib, encode32(values)),
                null);
    }

    static byte[] encode64(double... values) {
        ByteBuffer buffer = ByteBuffer.allocate(values.length * 8).order(ByteOrder.LITTLE_ENDIAN);
        for (double value : values) {
            buffer.putDouble(value);
        }
        return buffer.array();
    }

    static byte[] encode32(float... values) {
        ByteBuffer buffer = ByteBuffer.allocate(values.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (float value : values) {
            buffer.putFloat(value);
        }
        return buffer.array();
    }

    static String base64(boolean zlib, byte[] bytes) {
        return Base64.getEncoder().encodeToString(zlib ? deflate(bytes) : bytes);
    }

    static byte[] deflate(byte[] bytes) {
        Deflater deflater = new Deflater();
        deflater.setInput(bytes);
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        while (!deflater.finished()) {
            int n = deflater.deflate(buffer);
            out.write(buffer, 0, n);
        }
        deflater.end();
        return out.toByteArray();
    }

    /** An MS2 spectrum with two peaks in 64-bit zlib arrays and everything a writer needs. */
    static String ms2(int index, String id, String precursor, String charge, double... peaks) {
        double[] mz = new double[peaks.length / 2];
        double[] intensity = new double[peaks.length / 2];
        for (int i = 0; i < mz.length; i++) {
            mz[i] = peaks[2 * i];
            intensity[i] = peaks[2 * i + 1];
        }
        return spectrum(
                index,
                id,
                mz.length,
                level(2),
                minutes("1.5"),
                selectedIon(precursor, charge),
                doubles(MzMlSpectrumReader.MZ_ARRAY, true, mz)
                        + doubles(MzMlSpectrumReader.INTENSITY_ARRAY, true, intensity));
    }

    /** An MS1 spectrum, which the reader must skip without decoding. */
    static String ms1(int index, String id) {
        return spectrum(
                index,
                id,
                1,
                level(1),
                minutes("1.0"),
                "",
                array(List.of(cv(MzMlSpectrumReader.MZ_ARRAY)), "not base64 at all!", null));
    }
}
