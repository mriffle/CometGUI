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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import org.cometgui.tools.limelight.LimelightConversionException.Failure;
import org.cometgui.tools.testing.Nulls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xml.sax.SAXException;

/** The schema, read from inside the converter JAR, and every way that can fail. */
class LimelightSchemaTest {

    private static String sha256(byte[] bytes) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static Path zip(Path file, String entry, byte[] content) throws IOException {
        try (OutputStream out = Files.newOutputStream(file);
                ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry(entry));
            zip.write(content);
            zip.closeEntry();
        }
        return file;
    }

    @Test
    @DisplayName("the real JAR's entry is Phase 00's schema, byte for byte (SHA-256 e8c1b920...)")
    void theRealSchema(@TempDir Path directory) throws IOException {
        Path jar = LimelightFixtures.converterJar(directory);

        LimelightSchema schema = LimelightSchema.fromConverterJar(jar);

        byte[] bytes = schema.bytes();
        bytes[0] = 0;
        assertAll(
                () -> assertEquals(LimelightFixtures.SCHEMA_SHA256, sha256(schema.bytes())),
                () -> assertEquals(65_905, schema.bytes().length),
                () -> assertEquals(jar, schema.jar()),
                () -> assertNotSame(schema.bytes(), schema.bytes()),
                () -> assertEquals("limelight-xml.xsd", LimelightSchema.ENTRY));
    }

    @Test
    @DisplayName("a JAR without the entry is refused, naming the JAR")
    void noEntry(@TempDir Path directory) throws IOException {
        Path jar = zip(directory.resolve("other.jar"), "other.xsd", new byte[] {1});

        LimelightConversionException refused =
                assertThrows(
                        LimelightConversionException.class,
                        () -> LimelightSchema.fromConverterJar(jar));

        assertAll(
                () -> assertEquals(Failure.NO_SCHEMA, refused.failure()),
                () -> assertEquals(Optional.of(jar), refused.file()),
                () ->
                        assertEquals(
                                "the converter JAR "
                                        + jar
                                        + " holds no limelight-xml.xsd, so its output cannot be"
                                        + " validated against the schema it was written to",
                                refused.getMessage()));
    }

    @Test
    @DisplayName("a directory named like the entry is not the entry")
    void aDirectoryEntry(@TempDir Path directory) throws IOException {
        Path jar = zip(directory.resolve("dir.jar"), "limelight-xml.xsd/", new byte[0]);

        assertEquals(
                Failure.NO_SCHEMA,
                assertThrows(
                                LimelightConversionException.class,
                                () -> LimelightSchema.fromConverterJar(jar))
                        .failure());
    }

    @Test
    @DisplayName("a file that is not a JAR, and a JAR that is not there, are refused")
    void notAJar(@TempDir Path directory) throws IOException {
        Path text = Files.writeString(directory.resolve("x.jar"), "not a zip");
        Path absent = directory.resolve("absent.jar");

        LimelightConversionException notZip =
                assertThrows(
                        LimelightConversionException.class,
                        () -> LimelightSchema.fromConverterJar(text));

        assertAll(
                () -> assertEquals(Failure.NO_SCHEMA, notZip.failure()),
                () ->
                        assertTrue(
                                notZip.getMessage()
                                        .startsWith(
                                                "the converter JAR "
                                                        + text
                                                        + " cannot be read as a JAR: "),
                                notZip.getMessage()),
                () ->
                        assertEquals(
                                Failure.NO_SCHEMA,
                                assertThrows(
                                                LimelightConversionException.class,
                                                () -> LimelightSchema.fromConverterJar(absent))
                                        .failure()));
    }

    @Test
    @DisplayName("an entry that is not a schema, and one larger than any schema, are refused")
    void notASchema(@TempDir Path directory) throws IOException {
        Path garbage =
                zip(
                        directory.resolve("garbage.jar"),
                        LimelightSchema.ENTRY,
                        "<not-a-schema/>".getBytes(StandardCharsets.UTF_8));
        Path huge =
                zip(
                        directory.resolve("huge.jar"),
                        LimelightSchema.ENTRY,
                        new byte[LimelightSchema.MAX_BYTES + 1]);

        LimelightConversionException notSchema =
                assertThrows(
                        LimelightConversionException.class,
                        () -> LimelightSchema.fromConverterJar(garbage));
        LimelightConversionException tooLarge =
                assertThrows(
                        LimelightConversionException.class,
                        () -> LimelightSchema.fromConverterJar(huge));

        assertAll(
                () -> assertEquals(Failure.NO_SCHEMA, notSchema.failure()),
                () ->
                        assertTrue(
                                notSchema
                                        .getMessage()
                                        .startsWith(
                                                "the limelight-xml.xsd in "
                                                        + garbage
                                                        + " is not a schema: "),
                                notSchema.getMessage()),
                () -> assertEquals(Failure.NO_SCHEMA, tooLarge.failure()),
                () ->
                        assertEquals(
                                "the limelight-xml.xsd in "
                                        + huge
                                        + " is larger than 4194304 bytes, which no Limelight schema"
                                        + " is",
                                tooLarge.getMessage()));
    }

    @Test
    @DisplayName("a schema exactly at the size limit is read")
    void theSizeLimitIsInclusive(@TempDir Path directory) throws IOException {
        StringBuilder xsd =
                new StringBuilder(
                        "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">"
                                + "<xs:element name=\"a\"/></xs:schema>");
        xsd.append(" ".repeat(LimelightSchema.MAX_BYTES - xsd.length()));
        byte[] exactly = xsd.toString().getBytes(StandardCharsets.US_ASCII);
        Path jar = zip(directory.resolve("big.jar"), LimelightSchema.ENTRY, exactly);

        assertEquals(
                LimelightSchema.MAX_BYTES, LimelightSchema.fromConverterJar(jar).bytes().length);
    }

    @Test
    @DisplayName("a schema that reaches for an external file cannot: external access is forbidden")
    void externalSchemaAccessIsForbidden(@TempDir Path directory) throws IOException {
        Path other =
                Files.writeString(
                        directory.resolve("other.xsd"),
                        "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\""
                                + " targetNamespace=\"urn:o\"><xs:element name=\"o\"/>"
                                + "</xs:schema>");
        String importing =
                "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" xmlns:o=\"urn:o\">"
                        + "<xs:import namespace=\"urn:o\" schemaLocation=\""
                        + other.toUri()
                        + "\"/><xs:element name=\"a\"><xs:complexType><xs:sequence>"
                        + "<xs:element ref=\"o:o\"/></xs:sequence></xs:complexType></xs:element>"
                        + "</xs:schema>";
        Path jar =
                zip(
                        directory.resolve("import.jar"),
                        LimelightSchema.ENTRY,
                        importing.getBytes(StandardCharsets.UTF_8));

        LimelightConversionException refused =
                assertThrows(
                        LimelightConversionException.class,
                        () -> LimelightSchema.fromConverterJar(jar));

        assertAll(
                () -> assertEquals(Failure.NO_SCHEMA, refused.failure()),
                () ->
                        assertTrue(
                                refused.getMessage().contains("accessExternalSchema"),
                                refused.getMessage()));
    }

    @Test
    @DisplayName("the factory's settings are FORCED from their unsafe values")
    void hardeningIsForced() throws SAXException {
        SchemaFactory unsafe = SchemaFactory.newDefaultInstance();
        unsafe.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, false);
        unsafe.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "all");
        unsafe.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "all");

        SchemaFactory hardened = LimelightSchema.hardened(unsafe);

        assertAll(
                () -> assertSame(unsafe, hardened),
                () -> assertTrue(hardened.getFeature(XMLConstants.FEATURE_SECURE_PROCESSING)),
                () -> assertEquals("", hardened.getProperty(XMLConstants.ACCESS_EXTERNAL_DTD)),
                () -> assertEquals("", hardened.getProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA)),
                () ->
                        assertEquals(
                                "factory",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        LimelightSchema.hardened(
                                                                Nulls.of(SchemaFactory.class)))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "jar",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        LimelightSchema.fromConverterJar(
                                                                Nulls.of(Path.class)))
                                        .getMessage()));
    }

    @Test
    @DisplayName("a validator refuses to read a document's external DTD, even when given the file")
    void theValidatorReadsNoExternalDtd(@TempDir Path directory) throws IOException {
        Path dtd = Files.writeString(directory.resolve("x.dtd"), "<!ENTITY e \"expanded\">");
        Path document =
                Files.writeString(
                        directory.resolve("doc.xml"),
                        "<!DOCTYPE limelight_input SYSTEM \""
                                + dtd.toUri()
                                + "\"><limelight_input fasta_filename=\"&e;\"/>");
        Validator validator =
                LimelightSchema.fromConverterJar(LimelightFixtures.converterJar(directory))
                        .newValidator();

        SAXException refused =
                assertThrows(
                        SAXException.class,
                        () -> validator.validate(new StreamSource(document.toFile())));

        assertTrue(refused.getMessage().contains("accessExternalDTD"), refused.getMessage());
    }
}
