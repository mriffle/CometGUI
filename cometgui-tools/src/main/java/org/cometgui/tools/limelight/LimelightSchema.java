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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import org.cometgui.tools.limelight.LimelightConversionException.Failure;
import org.xml.sax.SAXException;

/**
 * The Limelight XML schema, read from the converter JAR a conversion actually used.
 *
 * <p>Phase 00 found no standalone copy of the schema anywhere upstream: it ships inside the
 * converter as the JAR-root entry {@value #ENTRY} (65 905 bytes, SHA-256 {@code e8c1b920...} in
 * v2.8.1, no target namespace), and it is the schema the converter validates its own output against
 * in its last step. Reading it from the JAR the conversion ran -- which the installer has verified
 * by SHA-256 -- means the output is held to the schema of the converter that wrote it, and a
 * converter upgrade brings its schema with it. The test suite pins the entry's SHA-256.
 *
 * <p><strong>Hardened.</strong> The factory is the JDK's own ({@code newDefaultInstance}, not a
 * provider found on the class path), with secure processing on and every external DTD and schema
 * access forbidden; every validator it makes is forbidden to read an external DTD. The schema is
 * self-contained, so nothing it needs is refused.
 */
public final class LimelightSchema {

    /** The schema's entry name, at the root of the converter JAR. */
    public static final String ENTRY = "limelight-xml.xsd";

    /**
     * The largest entry read, far above the real 65 905 bytes, so a hostile JAR cannot fill memory.
     */
    static final int MAX_BYTES = 4 * 1024 * 1024;

    private final Path jar;
    private final byte[] bytes;
    private final Schema schema;

    private LimelightSchema(Path jar, byte[] bytes, Schema schema) {
        this.jar = jar;
        this.bytes = bytes;
        this.schema = schema;
    }

    /**
     * Reads and compiles the schema from a converter JAR.
     *
     * @param jar the converter JAR the conversion runs
     * @return the schema
     * @throws LimelightConversionException ({@link Failure#NO_SCHEMA}) if the JAR cannot be opened,
     *     holds no {@value #ENTRY}, holds one larger than any real schema, or holds one that does
     *     not compile -- naming the JAR
     * @throws NullPointerException if {@code jar} is {@code null}
     */
    public static LimelightSchema fromConverterJar(Path jar) throws LimelightConversionException {
        Objects.requireNonNull(jar, "jar");
        byte[] bytes = entryBytes(jar);
        try {
            Schema schema =
                    hardened(SchemaFactory.newDefaultInstance())
                            .newSchema(new StreamSource(new ByteArrayInputStream(bytes), ENTRY));
            return new LimelightSchema(jar, bytes, schema);
        } catch (SAXException notASchema) {
            throw new LimelightConversionException(
                    Failure.NO_SCHEMA,
                    jar,
                    "the " + ENTRY + " in " + jar + " is not a schema: " + notASchema.getMessage(),
                    notASchema);
        }
    }

    private static byte[] entryBytes(Path jar) throws LimelightConversionException {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry entry = zip.getEntry(ENTRY);
            if (entry == null || entry.isDirectory()) {
                throw new LimelightConversionException(
                        Failure.NO_SCHEMA,
                        jar,
                        "the converter JAR "
                                + jar
                                + " holds no "
                                + ENTRY
                                + ", so its output cannot be validated against the schema it was"
                                + " written to",
                        null);
            }
            try (InputStream in = zip.getInputStream(entry)) {
                byte[] bytes = in.readNBytes(MAX_BYTES + 1);
                if (bytes.length > MAX_BYTES) {
                    throw new LimelightConversionException(
                            Failure.NO_SCHEMA,
                            jar,
                            "the "
                                    + ENTRY
                                    + " in "
                                    + jar
                                    + " is larger than "
                                    + MAX_BYTES
                                    + " bytes, which no Limelight schema is",
                            null);
                }
                return bytes;
            }
        } catch (LimelightConversionException refused) {
            throw refused;
        } catch (IOException unreadable) {
            throw new LimelightConversionException(
                    Failure.NO_SCHEMA,
                    jar,
                    "the converter JAR "
                            + jar
                            + " cannot be read as a JAR: "
                            + unreadable.getMessage(),
                    unreadable);
        }
    }

    /**
     * Forces a schema factory into the safe state.
     *
     * @param factory the factory
     * @return the same factory, with secure processing on and no external access
     * @throws SAXException if the factory does not support a setting, which the JDK's always does
     */
    static SchemaFactory hardened(SchemaFactory factory) throws SAXException {
        Objects.requireNonNull(factory, "factory");
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory;
    }

    /**
     * A new validator for this schema.
     *
     * <p>{@link LimelightXmlJudge} hands it a hardened streaming reader, which never resolves a
     * DTD, so the reader is the first lock. The second is the factory's: a validator made from a
     * schema compiled under {@link XMLConstants#FEATURE_SECURE_PROCESSING} with external access
     * forbidden inherits that, and refuses a document's external DTD even when handed the raw file
     * -- which {@code LimelightSchemaTest} shows. Setting the property again here was tried and is
     * unobservable (PIT: the removed call survives every test), so it is not done.
     *
     * @return the validator, for one document
     */
    Validator newValidator() {
        return schema.newValidator();
    }

    /**
     * The JAR the schema was read from.
     *
     * @return the JAR
     */
    public Path jar() {
        return jar;
    }

    /**
     * The schema's bytes, exactly as the JAR holds them.
     *
     * @return a copy of the bytes
     */
    public byte[] bytes() {
        return bytes.clone();
    }
}
