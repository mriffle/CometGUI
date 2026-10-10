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

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;
import javax.xml.stream.Location;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * Streams the MS2 spectra of an mzML file, decoding every peak exactly or refusing the file.
 *
 * <p>StAX, one spectrum in memory at a time, and no third-party library. Peaks are base64, then
 * optionally zlib, then little-endian IEEE 754 numbers of 32 or 64 bits, per the mzML 1.1
 * specification. Anything else -- MS-Numpress, a compression this reader does not know, integer
 * arrays, a decoded length that differs from the declared one, an array missing -- is a {@link
 * SpectrumFileException} naming the file and the spectrum. Nothing is approximated.
 *
 * <p>What is reported for each spectrum whose {@code ms level} ({@code MS:1000511}) is 2, as {@link
 * SourceSpectrum}: the {@code id} (the native id), the {@code index} attribute, the 1-based
 * position among all spectra of the file, the scan number the id carries, the {@code selected ion
 * m/z} ({@code MS:1000744}) and {@code charge state} ({@code MS:1000041}) of the single selected
 * ion, the first scan's {@code scan start time} ({@code MS:1000016}) with its unit, and the {@code
 * m/z array} ({@code MS:1000514}) and {@code intensity array} ({@code MS:1000515}). Parameters
 * reached through a {@code referenceableParamGroupRef} count as if written in place. Spectra of any
 * other MS level are skipped without decoding their peaks.
 */
public final class MzMlSpectrumReader {

    static final String MS_LEVEL = "MS:1000511";
    static final String SELECTED_ION_MZ = "MS:1000744";
    static final String CHARGE_STATE = "MS:1000041";
    static final String SCAN_START_TIME = "MS:1000016";
    static final String FLOAT32 = "MS:1000521";
    static final String FLOAT64 = "MS:1000523";
    static final String INT32 = "MS:1000519";
    static final String INT64 = "MS:1000522";
    static final String ZLIB = "MS:1000574";
    static final String NO_COMPRESSION = "MS:1000576";
    static final String MZ_ARRAY = "MS:1000514";
    static final String INTENSITY_ARRAY = "MS:1000515";
    static final String UNIT_SECOND = "UO:0000010";
    static final String UNIT_MINUTE = "UO:0000031";

    /** The MS-Numpress compression terms, alone and combined with zlib. */
    static final Set<String> NUMPRESS =
            Set.of(
                    "MS:1002312",
                    "MS:1002313",
                    "MS:1002314",
                    "MS:1002746",
                    "MS:1002747",
                    "MS:1002748");

    private MzMlSpectrumReader() {}

    /**
     * Streams every MS2 spectrum of an mzML file, in file order.
     *
     * @param file the mzML file, plain or indexed
     * @param visitor receives each MS2 spectrum
     * @throws SpectrumFileException if the file is not mzML, or a spectrum cannot be read or
     *     decoded exactly, naming the file and the spectrum
     * @throws IOException if the file cannot be read, or the visitor throws
     * @throws NullPointerException if either argument is {@code null}
     */
    public static void read(Path file, SpectrumVisitor visitor) throws IOException {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(visitor, "visitor");
        XMLInputFactory factory = xmlFactory();
        try (InputStream in = new BufferedInputStream(Files.newInputStream(file), 1 << 16)) {
            XMLStreamReader xml = factory.createXMLStreamReader(in);
            try {
                new Parse(file, xml, visitor).run();
            } finally {
                xml.close();
            }
        } catch (XMLStreamException malformed) {
            Location at = malformed.getLocation();
            throw new SpectrumFileException(
                    file,
                    "is not well-formed XML"
                            + (at == null
                                    ? ""
                                    : " at line "
                                            + at.getLineNumber()
                                            + ", column "
                                            + at.getColumnNumber())
                            + ": "
                            + malformed.getMessage(),
                    malformed);
        }
    }

    /**
     * The one way this package reads XML: no document type processed, so no entity -- internal or
     * external -- is ever expanded, and nothing outside the file is ever fetched.
     *
     * @return a new factory
     */
    static XMLInputFactory xmlFactory() {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
        return factory;
    }

    /** One controlled-vocabulary parameter: accession, value and unit. */
    private record Param(String accession, String value, String unitAccession, String unitName) {}

    /** One binary data array as written. */
    private static final class ArrayData {
        private final List<Param> params = new ArrayList<>();
        private final StringBuilder base64 = new StringBuilder();
        private final String arrayLength;

        ArrayData(String arrayLength) {
            this.arrayLength = arrayLength;
        }

        boolean has(String accession) {
            for (Param param : params) {
                if (param.accession().equals(accession)) {
                    return true;
                }
            }
            return false;
        }
    }

    /** One spectrum element as it is read. */
    private static final class SpectrumData {
        private final String id;
        private final String indexText;
        private final String defaultArrayLength;
        private final int position;
        private final List<Param> params = new ArrayList<>();
        private final List<Param> scanParams = new ArrayList<>();
        private final List<Param> selectedIonParams = new ArrayList<>();
        private final List<ArrayData> arrays = new ArrayList<>();
        private int selectedIons;

        SpectrumData(String id, String indexText, String defaultArrayLength, int position) {
            this.id = id;
            this.indexText = indexText;
            this.defaultArrayLength = defaultArrayLength;
            this.position = position;
        }
    }

    /** The state of one read. */
    private static final class Parse {
        private final Path file;
        private final XMLStreamReader xml;
        private final SpectrumVisitor visitor;
        private final Deque<String> elements = new ArrayDeque<>();
        private final Map<String, List<Param>> groups = new HashMap<>();
        private List<Param> group;
        private SpectrumData spectrum;
        private ArrayData array;
        private int position;

        Parse(Path file, XMLStreamReader xml, SpectrumVisitor visitor) {
            this.file = file;
            this.xml = xml;
            this.visitor = visitor;
        }

        void run() throws XMLStreamException, IOException {
            boolean rootSeen = false;
            while (xml.hasNext()) {
                int event = xml.next();
                if (event == XMLStreamReader.START_ELEMENT) {
                    String name = xml.getLocalName();
                    if (!rootSeen) {
                        rootSeen = true;
                        if (!"mzML".equals(name) && !"indexedmzML".equals(name)) {
                            throw new SpectrumFileException(
                                    file, "is not mzML: its first element is <" + name + ">");
                        }
                    }
                    start(name);
                    elements.push(name);
                } else if (event == XMLStreamReader.END_ELEMENT) {
                    elements.pop();
                    end(xml.getLocalName());
                } else if (event == XMLStreamReader.CHARACTERS
                        && array != null
                        && "binary".equals(elements.peek())) {
                    array.base64.append(
                            xml.getTextCharacters(), xml.getTextStart(), xml.getTextLength());
                }
            }
        }

        private void start(String name) throws SpectrumFileException {
            String parent = elements.peek();
            switch (name) {
                case "referenceableParamGroup" -> {
                    group = new ArrayList<>();
                    groups.put(attribute("id"), group);
                }
                case "spectrum" -> {
                    position++;
                    spectrum =
                            new SpectrumData(
                                    attribute("id"),
                                    attribute("index"),
                                    attribute("defaultArrayLength"),
                                    position);
                }
                case "selectedIon" -> {
                    if (spectrum != null) {
                        spectrum.selectedIons++;
                    }
                }
                case "binaryDataArray" -> {
                    if (spectrum != null) {
                        array = new ArrayData(attribute("arrayLength"));
                        spectrum.arrays.add(array);
                    }
                }
                case "cvParam" ->
                        place(
                                parent,
                                List.of(
                                        new Param(
                                                attribute("accession"),
                                                attribute("value"),
                                                attribute("unitAccession"),
                                                attribute("unitName"))));
                case "referenceableParamGroupRef" -> {
                    String ref = attribute("ref");
                    List<Param> referenced = groups.get(ref);
                    if (referenced == null && spectrum != null) {
                        throw new SpectrumFileException(
                                file,
                                "a referenceableParamGroupRef names \""
                                        + ref
                                        + "\", which no referenceableParamGroup defines");
                    }
                    if (referenced != null) {
                        place(parent, referenced);
                    }
                }
                default -> {
                    // every other element only gives the parameters below it their context
                }
            }
        }

        private void place(String parent, List<Param> params) {
            if ("referenceableParamGroup".equals(parent) && group != null) {
                group.addAll(params);
            } else if (spectrum == null || parent == null) {
                return;
            } else if ("spectrum".equals(parent)) {
                spectrum.params.addAll(params);
            } else if ("scan".equals(parent)) {
                spectrum.scanParams.addAll(params);
            } else if ("selectedIon".equals(parent)) {
                spectrum.selectedIonParams.addAll(params);
            } else if ("binaryDataArray".equals(parent) && array != null) {
                array.params.addAll(params);
            }
        }

        private void end(String name) throws IOException {
            switch (name) {
                case "referenceableParamGroup" -> group = null;
                case "binaryDataArray" -> array = null;
                case "spectrum" -> {
                    SpectrumData finished = spectrum;
                    spectrum = null;
                    SourceSpectrum ms2 = finish(file, finished);
                    if (ms2 != null) {
                        visitor.visit(ms2);
                    }
                }
                default -> {
                    // nothing ends but the element
                }
            }
        }

        private String attribute(String name) {
            return xml.getAttributeValue(null, name);
        }
    }

    /** Builds the spectrum, or returns null for one that is not MS2. */
    private static SourceSpectrum finish(Path file, SpectrumData data)
            throws SpectrumFileException {
        if (data.id == null || data.id.isEmpty()) {
            throw new SpectrumFileException(
                    file, "the spectrum at position " + data.position + " has no id attribute");
        }
        int index = index(file, data);
        String level = single(file, data, index, data.params, MS_LEVEL, "ms level");
        if (level == null) {
            throw new SpectrumFileException(
                    file, data.id, index, "states no ms level (" + MS_LEVEL + ")");
        }
        if (!"2".equals(level.strip())) {
            return null;
        }
        if (data.selectedIons > 1) {
            throw new SpectrumFileException(
                    file,
                    data.id,
                    index,
                    "has "
                            + data.selectedIons
                            + " selected ions, and an MGF entry can carry only one precursor");
        }
        ArrayData mzArray = arrayOf(file, data, index, MZ_ARRAY, "m/z");
        ArrayData intensityArray = arrayOf(file, data, index, INTENSITY_ARRAY, "intensity");
        double[] mz = decode(file, data, index, mzArray, "m/z");
        double[] intensity = decode(file, data, index, intensityArray, "intensity");
        if (mz.length != intensity.length) {
            throw new SpectrumFileException(
                    file,
                    data.id,
                    index,
                    "has "
                            + mz.length
                            + " m/z values and "
                            + intensity.length
                            + " intensities, so its peaks cannot be paired");
        }
        PeakList peaks =
                new PeakList(
                        mz, encoding(file, data, index, mzArray, "m/z"),
                        intensity, encoding(file, data, index, intensityArray, "intensity"));
        return new SourceSpectrum(
                data.id,
                index,
                data.position,
                SourceSpectrum.scanNumberOf(data.id),
                Optional.ofNullable(
                        single(
                                file,
                                data,
                                index,
                                data.selectedIonParams,
                                SELECTED_ION_MZ,
                                "selected ion m/z")),
                charge(file, data, index),
                retentionTime(file, data, index),
                peaks);
    }

    private static int index(Path file, SpectrumData data) throws SpectrumFileException {
        try {
            int index = Integer.parseInt(Objects.requireNonNullElse(data.indexText, ""));
            if (index >= 0) {
                return index;
            }
        } catch (NumberFormatException notANumber) {
            // reported below with the text that was there
        }
        throw new SpectrumFileException(
                file,
                "spectrum \""
                        + data.id
                        + "\" (position "
                        + data.position
                        + ") has the index attribute \""
                        + data.indexText
                        + "\", which is not a non-negative integer");
    }

    private static String single(
            Path file,
            SpectrumData data,
            int index,
            List<Param> params,
            String accession,
            String what)
            throws SpectrumFileException {
        Param found = null;
        for (Param param : params) {
            if (param.accession().equals(accession)) {
                if (found != null) {
                    throw new SpectrumFileException(
                            file,
                            data.id,
                            index,
                            "states its " + what + " (" + accession + ") more than once");
                }
                found = param;
            }
        }
        return found == null ? null : Objects.requireNonNullElse(found.value(), "");
    }

    private static OptionalInt charge(Path file, SpectrumData data, int index)
            throws SpectrumFileException {
        String text =
                single(file, data, index, data.selectedIonParams, CHARGE_STATE, "charge state");
        if (text == null) {
            return OptionalInt.empty();
        }
        try {
            return OptionalInt.of(Integer.parseInt(text.strip()));
        } catch (NumberFormatException notAnInteger) {
            throw new SpectrumFileException(
                    file,
                    data.id,
                    index,
                    "states the charge state \"" + text + "\", which is not an integer");
        }
    }

    private static Optional<RetentionTime> retentionTime(Path file, SpectrumData data, int index)
            throws SpectrumFileException {
        Param found = null;
        for (Param param : data.scanParams) {
            if (param.accession().equals(SCAN_START_TIME)) {
                if (found != null) {
                    throw new SpectrumFileException(
                            file,
                            data.id,
                            index,
                            "states more than one scan start time ("
                                    + SCAN_START_TIME
                                    + "), so it has no single retention time");
                }
                found = param;
            }
        }
        if (found == null) {
            return Optional.empty();
        }
        RetentionTime.Unit unit;
        if (UNIT_SECOND.equals(found.unitAccession())
                || (found.unitAccession() == null && "second".equals(found.unitName()))) {
            unit = RetentionTime.Unit.SECOND;
        } else if (UNIT_MINUTE.equals(found.unitAccession())
                || (found.unitAccession() == null && "minute".equals(found.unitName()))) {
            unit = RetentionTime.Unit.MINUTE;
        } else {
            throw new SpectrumFileException(
                    file,
                    data.id,
                    index,
                    "states its scan start time in the unit \""
                            + found.unitAccession()
                            + "\" (\""
                            + found.unitName()
                            + "\"), which is neither seconds ("
                            + UNIT_SECOND
                            + ") nor minutes ("
                            + UNIT_MINUTE
                            + ")");
        }
        try {
            return Optional.of(
                    new RetentionTime(Objects.requireNonNullElse(found.value(), ""), unit));
        } catch (IllegalArgumentException notANumber) {
            throw new SpectrumFileException(
                    file, data.id, index, "has a scan start time that " + notANumber.getMessage());
        }
    }

    private static ArrayData arrayOf(
            Path file, SpectrumData data, int index, String kind, String what)
            throws SpectrumFileException {
        ArrayData found = null;
        for (ArrayData candidate : data.arrays) {
            if (candidate.has(kind)) {
                if (found != null) {
                    throw new SpectrumFileException(
                            file, data.id, index, "has more than one " + what + " array");
                }
                found = candidate;
            }
        }
        if (found == null) {
            throw new SpectrumFileException(
                    file, data.id, index, "has no " + what + " array (" + kind + ")");
        }
        return found;
    }

    private static PeakEncoding encoding(
            Path file, SpectrumData data, int index, ArrayData array, String what)
            throws SpectrumFileException {
        if (array.has(INT32) || array.has(INT64)) {
            throw new SpectrumFileException(
                    file,
                    data.id,
                    index,
                    "stores its " + what + " array as integers, which this reader does not decode");
        }
        boolean single = array.has(FLOAT32);
        boolean dual = array.has(FLOAT64);
        if (single == dual) {
            throw new SpectrumFileException(
                    file,
                    data.id,
                    index,
                    "states "
                            + (single ? "both" : "neither")
                            + " of 32-bit float ("
                            + FLOAT32
                            + ") and 64-bit float ("
                            + FLOAT64
                            + ") for its "
                            + what
                            + " array");
        }
        return single ? PeakEncoding.FLOAT32 : PeakEncoding.FLOAT64;
    }

    private static double[] decode(
            Path file, SpectrumData data, int index, ArrayData array, String what)
            throws SpectrumFileException {
        PeakEncoding encoding = encoding(file, data, index, array, what);
        for (String accession : NUMPRESS) {
            if (array.has(accession)) {
                throw new SpectrumFileException(
                        file,
                        data.id,
                        index,
                        "compresses its "
                                + what
                                + " array with MS-Numpress ("
                                + accession
                                + "), which this reader does not decode; re-export the file"
                                + " with zlib or no compression");
            }
        }
        boolean zlib = array.has(ZLIB);
        if (zlib == array.has(NO_COMPRESSION)) {
            throw new SpectrumFileException(
                    file,
                    data.id,
                    index,
                    "names no compression this reader decodes for its "
                            + what
                            + " array: exactly one of zlib ("
                            + ZLIB
                            + ") and no compression ("
                            + NO_COMPRESSION
                            + ") must be stated");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(withoutWhitespace(array.base64));
        } catch (IllegalArgumentException notBase64) {
            throw new SpectrumFileException(
                    file,
                    data.id,
                    index,
                    "has a " + what + " array that is not base64: " + notBase64.getMessage());
        }
        if (zlib) {
            bytes = inflate(file, data, index, bytes, what);
        }
        int width = encoding == PeakEncoding.FLOAT32 ? Float.BYTES : Double.BYTES;
        if (bytes.length % width != 0) {
            throw new SpectrumFileException(
                    file,
                    data.id,
                    index,
                    "has a "
                            + what
                            + " array of "
                            + bytes.length
                            + " bytes, which is not a whole number of "
                            + width
                            + "-byte values");
        }
        int count = bytes.length / width;
        int declared = declaredLength(file, data, index, array, what);
        if (count != declared) {
            throw new SpectrumFileException(
                    file,
                    data.id,
                    index,
                    "has a "
                            + what
                            + " array that decodes to "
                            + count
                            + " values but declares "
                            + declared);
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        double[] values = new double[count];
        for (int i = 0; i < count; i++) {
            values[i] = width == Float.BYTES ? buffer.getFloat() : buffer.getDouble();
        }
        return values;
    }

    private static int declaredLength(
            Path file, SpectrumData data, int index, ArrayData array, String what)
            throws SpectrumFileException {
        String text = array.arrayLength != null ? array.arrayLength : data.defaultArrayLength;
        try {
            return Integer.parseInt(Objects.requireNonNullElse(text, ""));
        } catch (NumberFormatException notANumber) {
            throw new SpectrumFileException(
                    file,
                    data.id,
                    index,
                    "declares the length of its "
                            + what
                            + " array as \""
                            + text
                            + "\", which is not an integer");
        }
    }

    private static String withoutWhitespace(CharSequence text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!Character.isWhitespace(c)) {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static byte[] inflate(
            Path file, SpectrumData data, int index, byte[] compressed, String what)
            throws SpectrumFileException {
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(compressed);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            while (!inflater.finished()) {
                int n = inflater.inflate(buffer);
                if (!inflater.finished() && (inflater.needsInput() || inflater.needsDictionary())) {
                    throw new SpectrumFileException(
                            file,
                            data.id,
                            index,
                            "has a "
                                    + what
                                    + " array whose zlib stream ends before it is complete");
                }
                out.write(buffer, 0, n);
            }
            if (inflater.getRemaining() > 0) {
                throw new SpectrumFileException(
                        file,
                        data.id,
                        index,
                        "has a "
                                + what
                                + " array with "
                                + inflater.getRemaining()
                                + " bytes after the end of its zlib stream");
            }
            return out.toByteArray();
        } catch (DataFormatException corrupt) {
            throw new SpectrumFileException(
                    file,
                    data.id,
                    index,
                    "has a "
                            + what
                            + " array that is not a valid zlib stream: "
                            + corrupt.getMessage());
        } finally {
            inflater.end();
        }
    }
}
