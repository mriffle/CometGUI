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
import java.io.InputStream;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.stax.StAXSource;
import javax.xml.validation.Validator;
import org.cometgui.tools.limelight.LimelightConversionException.Failure;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * Decides whether a file is a Limelight XML document, from the file alone ({@code R-LL-04}, design
 * decision P12-3).
 *
 * <p>The converter's exit status is not evidence. Run on this host on 2026-10-11, the pinned v2.8.1
 * JAR exits <strong>0</strong> with no arguments, on an unknown option and on {@code -q NaN}, each
 * time printing its usage and writing nothing; it exits 1 only when its required options are
 * present and a file behind them is not. So every check here reads the file:
 *
 * <ol>
 *   <li>it exists and is a regular file ({@link Failure#NO_OUTPUT});
 *   <li>it is not empty ({@link Failure#EMPTY_OUTPUT});
 *   <li>it can be read ({@link Failure#UNREADABLE_OUTPUT});
 *   <li>it is well-formed XML with no DOCTYPE, read by a hardened streaming parser ({@link
 *       Failure#NOT_WELL_FORMED});
 *   <li>its document element is {@code limelight_input} in no namespace ({@link
 *       Failure#WRONG_ROOT});
 *   <li>it is valid against the converter's own schema ({@link LimelightSchema}, {@link
 *       Failure#SCHEMA_INVALID}), the first errors quoted with their line and column.
 * </ol>
 *
 * <p>The first pass also reads what a caller checks the conversion against: how many {@code psm}
 * and {@code reported_peptide} elements there are, and the {@code default_filter_value} of the
 * Percolator search program's PSM-level {@code q-value} annotation type, which is where the
 * converter writes its {@code -q} (see {@link LimelightQValueCutoff}).
 *
 * <p>Proved first against the real known-good output -- Phase 00's {@code limelight.xml} and a
 * fresh conversion -- and only then against corruptions, because a validator that has never
 * accepted a real file has not been shown to validate anything (the second caution under {@code
 * D-007}).
 */
public final class LimelightXmlJudge {

    /** The document element of every Limelight XML file. */
    public static final String ROOT_ELEMENT = "limelight_input";

    /** How many schema errors a refusal quotes. */
    static final int QUOTED_ERRORS = 5;

    private LimelightXmlJudge() {}

    /**
     * Judges one file.
     *
     * @param file the file the converter was told to write
     * @param schema the schema of the converter that wrote it
     * @return what the valid file holds
     * @throws LimelightConversionException naming the file and the first check it failed
     * @throws IOException if the file's size cannot be read
     * @throws NullPointerException if either argument is {@code null}
     */
    public static LimelightXmlSummary judge(Path file, LimelightSchema schema) throws IOException {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(schema, "schema");
        if (!Files.isRegularFile(file)) {
            throw new LimelightConversionException(
                    Failure.NO_OUTPUT, file, "there is no Limelight XML file at " + file, null);
        }
        long size = Files.size(file);
        if (size == 0L) {
            throw new LimelightConversionException(
                    Failure.EMPTY_OUTPUT,
                    file,
                    "the Limelight XML file " + file + " is empty (zero bytes)",
                    null);
        }
        if (!Files.isReadable(file)) {
            throw unreadable(file, null);
        }
        Structure structure = readStructure(file);
        validate(file, schema);
        return new LimelightXmlSummary(
                file,
                size,
                structure.psms,
                structure.reportedPeptides,
                Optional.ofNullable(structure.psmQValueDefault));
    }

    private static LimelightConversionException unreadable(Path file, IOException cause) {
        return new LimelightConversionException(
                Failure.UNREADABLE_OUTPUT,
                file,
                "the Limelight XML file "
                        + file
                        + " cannot be read"
                        + (cause == null ? "" : ": " + cause.getMessage()),
                cause);
    }

    /** What the structural pass found. */
    private static final class Structure {
        private int psms;
        private int reportedPeptides;
        private String psmQValueDefault;
    }

    /*
     * The reader is not closed, for the reason PoutDocument gives: XMLStreamReader.close does not
     * close the stream, which try-with-resources here already does.
     */
    private static Structure readStructure(Path file) throws IOException {
        try (InputStream in = open(file)) {
            XMLStreamReader reader =
                    PercolatorXmlPsms.harden(XMLInputFactory.newDefaultFactory())
                            .createXMLStreamReader(in);
            return walk(file, reader);
        } catch (XMLStreamException malformed) {
            throw new LimelightConversionException(
                    Failure.NOT_WELL_FORMED,
                    file,
                    "the Limelight XML file "
                            + file
                            + " is not well-formed XML: "
                            + malformed.getMessage(),
                    malformed);
        }
    }

    private static InputStream open(Path file) throws LimelightConversionException {
        try {
            return Files.newInputStream(file);
        } catch (AccessDeniedException denied) {
            throw unreadable(file, denied);
        } catch (IOException other) {
            throw unreadable(file, other);
        }
    }

    private static Structure walk(Path file, XMLStreamReader reader)
            throws XMLStreamException, LimelightConversionException {
        Structure structure = new Structure();
        boolean root = true;
        String searchProgram = null;
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.DTD) {
                throw new LimelightConversionException(
                        Failure.NOT_WELL_FORMED,
                        file,
                        "the Limelight XML file "
                                + file
                                + " carries a DOCTYPE, which the converter never writes, so it is"
                                + " not read",
                        null);
            }
            if (event != XMLStreamConstants.START_ELEMENT) {
                continue;
            }
            String local = reader.getLocalName();
            if (root) {
                root = false;
                String namespace = Objects.toString(reader.getNamespaceURI(), "");
                if (!ROOT_ELEMENT.equals(local) || !namespace.isEmpty()) {
                    throw new LimelightConversionException(
                            Failure.WRONG_ROOT,
                            file,
                            "the document element of "
                                    + file
                                    + " is "
                                    + (namespace.isEmpty() ? "" : "{" + namespace + "}")
                                    + local
                                    + ", not "
                                    + ROOT_ELEMENT
                                    + ", so it is not a Limelight XML file",
                            null);
                }
                continue;
            }
            switch (local) {
                case "psm" -> structure.psms++;
                case "reported_peptide" -> structure.reportedPeptides++;
                case "search_program" -> searchProgram = reader.getAttributeValue(null, "name");
                /*
                 * The schema puts filterable_psm_annotation_type only inside a search_program's
                 * psm_annotation_types, and search_program only inside search_programs, so the
                 * last search_program opened is the one this type belongs to; the schema pass
                 * that follows refuses any other arrangement.
                 */
                case "filterable_psm_annotation_type" -> {
                    if ("Percolator".equals(searchProgram)
                            && "q-value".equals(reader.getAttributeValue(null, "name"))) {
                        structure.psmQValueDefault =
                                reader.getAttributeValue(null, "default_filter_value");
                    }
                }
                default -> {
                    // every other element is the schema's business, not this pass's
                }
            }
        }
        return structure;
    }

    /*
     * Package-private for one test: the first pass has already refused every document that is not
     * well-formed, so the fatal-error path below is reached only by a file that changed between
     * the two passes, and a test has to call this directly to show it is not a pass.
     */
    static void validate(Path file, LimelightSchema schema) throws IOException {
        Errors errors = new Errors();
        try (InputStream in = open(file)) {
            Validator validator = schema.newValidator();
            validator.setErrorHandler(errors);
            validator.validate(
                    new StAXSource(
                            PercolatorXmlPsms.harden(XMLInputFactory.newDefaultFactory())
                                    .createXMLStreamReader(in)));
        } catch (XMLStreamException | SAXException stopped) {
            errors.stopped(stopped);
        }
        if (errors.count > 0) {
            throw new LimelightConversionException(
                    Failure.SCHEMA_INVALID,
                    file,
                    "the Limelight XML file "
                            + file
                            + " is not valid against "
                            + LimelightSchema.ENTRY
                            + " from "
                            + schema.jar()
                            + ": "
                            + errors.count
                            + " error(s), the first: "
                            + String.join(" | ", errors.quoted),
                    null);
        }
    }

    /** Counts schema errors and keeps the first few, with their position. */
    private static final class Errors implements ErrorHandler {

        private final List<String> quoted = new ArrayList<>();
        private int count;

        @Override
        public void warning(SAXParseException warning) {
            // a warning is not a validity verdict
        }

        @Override
        public void error(SAXParseException error) {
            record(error);
        }

        /*
         * Not recorded here: a fatal error stops the validation, so it arrives once more as the
         * exception validate() catches, and is recorded there -- together with the stream's own
         * well-formedness errors, which a StAXSource reports as exceptions and never as calls
         * to this handler.
         */
        @Override
        public void fatalError(SAXParseException fatal) throws SAXException {
            throw fatal;
        }

        private void stopped(Exception stopped) {
            count++;
            if (quoted.size() < QUOTED_ERRORS) {
                quoted.add(stopped.getMessage());
            }
        }

        private void record(SAXParseException problem) {
            count++;
            if (quoted.size() < QUOTED_ERRORS) {
                quoted.add(
                        "line "
                                + problem.getLineNumber()
                                + " column "
                                + problem.getColumnNumber()
                                + ": "
                                + problem.getMessage());
            }
        }
    }
}
