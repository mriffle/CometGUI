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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Optional;
import org.cometgui.tools.limelight.LimelightConversionException.Failure;
import org.cometgui.tools.testing.Nulls;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * The output judge, proved to ACCEPT the real known-good file before it is trusted to reject
 * anything (the second caution under {@code D-007}).
 *
 * <p>The corruptions are Phase 00's three, read in place, and this test's own, written as copies of
 * the real file under a {@code @TempDir} -- never into the mirror.
 */
class LimelightXmlJudgeTest {

    private static LimelightSchema schema;
    private static Path known;
    private static String knownText;

    @BeforeAll
    static void readTheSchemaAndTheKnownGoodFile(@TempDir Path directory) throws IOException {
        schema = LimelightSchema.fromConverterJar(LimelightFixtures.converterJar(directory));
        known =
                LimelightFixtures.real(
                        LimelightFixtures.LIMELIGHT_XML, LimelightFixtures.LIMELIGHT_XML_SHA256);
        knownText = Files.readString(known, StandardCharsets.UTF_8);
    }

    private static Path variant(Path directory, String name, String from, String to)
            throws IOException {
        assertTrue(knownText.contains(from), "the known-good file holds " + from);
        return Files.writeString(
                directory.resolve(name),
                knownText.replaceFirst(
                        java.util.regex.Pattern.quote(from),
                        java.util.regex.Matcher.quoteReplacement(to)),
                StandardCharsets.UTF_8);
    }

    private static LimelightConversionException refused(Path file) {
        return assertThrows(
                LimelightConversionException.class, () -> LimelightXmlJudge.judge(file, schema));
    }

    @Test
    @DisplayName("FIRST: Phase 00's real converter output is ACCEPTED, and its counts are read")
    void theRealOutputIsAccepted() throws IOException {
        LimelightXmlSummary summary = LimelightXmlJudge.judge(known, schema);

        assertAll(
                () -> assertEquals(known, summary.file()),
                () -> assertEquals(12_797_113L, summary.size()),
                () -> assertEquals(3897, summary.psmCount()),
                () -> assertEquals(2985, summary.reportedPeptideCount()),
                () -> assertEquals(Optional.of("0.01"), summary.psmQValueDefault()));
    }

    @Test
    @DisplayName("Phase 00's injected element is SCHEMA_INVALID, quoting the line and the element")
    void phase00BogusElement() throws IOException {
        Path file =
                LimelightFixtures.real(
                        LimelightFixtures.CORRUPT_BOGUS, LimelightFixtures.CORRUPT_BOGUS_SHA256);

        LimelightConversionException refused = refused(file);

        assertAll(
                () -> assertEquals(Failure.SCHEMA_INVALID, refused.failure()),
                () -> assertEquals(Optional.of(file), refused.file()),
                () ->
                        assertTrue(
                                refused.getMessage()
                                        .startsWith(
                                                "the Limelight XML file "
                                                        + file
                                                        + " is not valid against limelight-xml.xsd"
                                                        + " from "
                                                        + schema.jar()
                                                        + ": 1 error(s), the first: line 3 column"),
                                refused.getMessage()),
                () -> assertTrue(refused.getMessage().contains("not_in_the_schema")));
    }

    @Test
    @DisplayName("Phase 00's missing required attribute is SCHEMA_INVALID, naming fasta_filename")
    void phase00MissingAttribute() throws IOException {
        LimelightConversionException refused =
                refused(
                        LimelightFixtures.real(
                                LimelightFixtures.CORRUPT_ATTRIBUTE,
                                LimelightFixtures.CORRUPT_ATTRIBUTE_SHA256));

        assertAll(
                () -> assertEquals(Failure.SCHEMA_INVALID, refused.failure()),
                () ->
                        assertTrue(
                                refused.getMessage().contains("line 3 column"),
                                refused.getMessage()),
                () -> assertTrue(refused.getMessage().contains("'fasta_filename'")));
    }

    @Test
    @DisplayName("Phase 00's truncated file is NOT_WELL_FORMED")
    void phase00Truncated() throws IOException {
        Path file =
                LimelightFixtures.real(
                        LimelightFixtures.CORRUPT_TRUNCATED,
                        LimelightFixtures.CORRUPT_TRUNCATED_SHA256);

        LimelightConversionException refused = refused(file);

        assertAll(
                () -> assertEquals(Failure.NOT_WELL_FORMED, refused.failure()),
                () ->
                        assertTrue(
                                refused.getMessage()
                                        .startsWith(
                                                "the Limelight XML file "
                                                        + file
                                                        + " is not well-formed XML: ")));
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    @DisplayName(
            "no file, a directory, an empty file and an unreadable file each have their own name")
    void absentEmptyUnreadable(@TempDir Path directory) throws IOException {
        Path missing = directory.resolve("missing.xml");
        Path empty = Files.createFile(directory.resolve("empty.xml"));
        Path locked = Files.copy(known, directory.resolve("locked.xml"));
        Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("---------"));
        try {
            assertAll(
                    () -> assertEquals(Failure.NO_OUTPUT, refused(missing).failure()),
                    () ->
                            assertEquals(
                                    "there is no Limelight XML file at " + missing,
                                    refused(missing).getMessage()),
                    () -> assertEquals(Failure.NO_OUTPUT, refused(directory).failure()),
                    () -> assertEquals(Failure.EMPTY_OUTPUT, refused(empty).failure()),
                    () ->
                            assertEquals(
                                    "the Limelight XML file " + empty + " is empty (zero bytes)",
                                    refused(empty).getMessage()),
                    () -> assertEquals(Failure.UNREADABLE_OUTPUT, refused(locked).failure()),
                    () ->
                            assertEquals(
                                    "the Limelight XML file " + locked + " cannot be read",
                                    refused(locked).getMessage()));
        } finally {
            Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("rw-------"));
        }
    }

    @Test
    @DisplayName("a well-formed document with another root is WRONG_ROOT, its namespace named")
    void wrongRoot(@TempDir Path directory) throws IOException {
        Path pout = LimelightFixtures.pout(directory.resolve("p.xml"), java.util.List.of());
        Path namespaced =
                variant(
                        directory,
                        "ns.xml",
                        "<limelight_input ",
                        "<limelight_input xmlns=\"urn:x\" ");
        Path plain =
                Files.writeString(
                        directory.resolve("plain.xml"), "<?xml version=\"1.0\"?><other/>");

        assertAll(
                () -> assertEquals(Failure.WRONG_ROOT, refused(pout).failure()),
                () ->
                        assertEquals(
                                "the document element of "
                                        + pout
                                        + " is {http://per-colator.com/percolator_out/15}"
                                        + "percolator_output, not limelight_input, so it is not a"
                                        + " Limelight XML file",
                                refused(pout).getMessage()),
                () -> assertEquals(Failure.WRONG_ROOT, refused(namespaced).failure()),
                () ->
                        assertEquals(
                                "the document element of "
                                        + plain
                                        + " is other, not limelight_input, so it is not a"
                                        + " Limelight XML file",
                                refused(plain).getMessage()));
    }

    @Test
    @DisplayName("a DOCTYPE is refused and an external entity in it is never read")
    void aDoctypeIsRefused(@TempDir Path directory) throws IOException {
        Path secret = Files.writeString(directory.resolve("secret.txt"), "SECRET-CONTENT");
        Path file =
                variant(
                        directory,
                        "xxe.xml",
                        "<limelight_input ",
                        "<!DOCTYPE limelight_input [<!ENTITY s SYSTEM \""
                                + secret.toUri()
                                + "\">]>\n<limelight_input ");

        LimelightConversionException refused = refused(file);

        assertAll(
                () -> assertEquals(Failure.NOT_WELL_FORMED, refused.failure()),
                () ->
                        assertEquals(
                                "the Limelight XML file "
                                        + file
                                        + " carries a DOCTYPE, which the converter never writes, so"
                                        + " it is not read",
                                refused.getMessage()),
                () -> assertFalse(refused.getMessage().contains("SECRET-CONTENT")));
    }

    @Test
    @DisplayName(
            "my own corruptions: an unknown element, a non-integer charge, an emptied document")
    void ownCorruptions(@TempDir Path directory) throws IOException {
        Path element =
                variant(
                        directory,
                        "element.xml",
                        "</limelight_input>",
                        "<extra/></limelight_input>");
        Path notBoolean =
                variant(directory, "int.xml", " precursor_charge=\"", " precursor_charge=\"maybe");
        Path emptied =
                Files.writeString(
                        directory.resolve("emptied.xml"),
                        "<limelight_input fasta_filename=\"x\"/>");

        LimelightConversionException bool = refused(notBoolean);
        LimelightConversionException hollow = refused(emptied);

        assertAll(
                () -> assertEquals(Failure.SCHEMA_INVALID, refused(element).failure()),
                () -> assertTrue(refused(element).getMessage().contains("'extra'")),
                () -> assertEquals(Failure.SCHEMA_INVALID, bool.failure()),
                () -> assertTrue(bool.getMessage().contains("'maybe2'"), bool.getMessage()),
                () -> assertEquals(Failure.SCHEMA_INVALID, hollow.failure()),
                () ->
                        assertTrue(
                                hollow.getMessage().contains("line 1 column"),
                                hollow.getMessage()));
    }

    @Test
    @DisplayName("many schema errors are counted, and only the first five are quoted")
    void manyErrors(@TempDir Path directory) throws IOException {
        Path file =
                Files.writeString(
                        directory.resolve("many.xml"),
                        knownText.replace(" precursor_charge=\"", " precursor_charge=\"x"),
                        StandardCharsets.UTF_8);

        String message = refused(file).getMessage();

        assertAll(
                () -> assertTrue(message.contains(": 7794 error(s), the first: "), message),
                () ->
                        assertEquals(
                                4,
                                message.split(" \\| ").length - 1,
                                "five quoted, four separators"));
    }

    @Test
    @DisplayName("the q-value read is the Percolator PSM one, not the peptide one")
    void theQValueReadIsThePsmOne(@TempDir Path directory) throws IOException {
        String psmLine =
                "<filterable_psm_annotation_type name=\"q-value\" description=\"Q-value\""
                        + " filter_direction=\"below\" default_filter_value=\"0.01\"/>";
        String peptideLine =
                "<filterable_reported_peptide_annotation_type name=\"q-value\""
                        + " description=\"Q-value\" filter_direction=\"below\""
                        + " default_filter_value=\"0.01\"/>";
        Path psm = variant(directory, "psm.xml", psmLine, psmLine.replace("0.01", "0.2"));
        Path peptide =
                variant(directory, "pep.xml", peptideLine, peptideLine.replace("0.01", "0.3"));
        Path none =
                variant(
                        directory,
                        "none.xml",
                        psmLine,
                        psmLine.replace("\"q-value\"", "\"q value\""));

        assertAll(
                () ->
                        assertEquals(
                                Optional.of("0.2"),
                                LimelightXmlJudge.judge(psm, schema).psmQValueDefault()),
                () ->
                        assertEquals(
                                Optional.of("0.01"),
                                LimelightXmlJudge.judge(peptide, schema).psmQValueDefault()),
                () ->
                        assertEquals(
                                Optional.empty(),
                                LimelightXmlJudge.judge(none, schema).psmQValueDefault()));
    }

    @Test
    @DisplayName("a q-value annotation of another program, or outside the PSM types, is not read")
    void onlyPercolatorsPsmTypeIsRead(@TempDir Path directory) throws IOException {
        String comet =
                "<filterable_psm_annotation_type name=\"XCorr\" description=\"Comet"
                        + " cross-correlation coefficient\" filter_direction=\"above\"/>";
        String percolator =
                "<filterable_psm_annotation_type name=\"q-value\" description=\"Q-value\""
                        + " filter_direction=\"below\" default_filter_value=\"0.01\"/>";
        Path cometQ =
                Files.writeString(
                        directory.resolve("comet.xml"),
                        knownText
                                .replaceFirst(
                                        java.util.regex.Pattern.quote(percolator),
                                        percolator.replace("\"q-value\"", "\"q_value\""))
                                .replaceFirst(
                                        java.util.regex.Pattern.quote(comet),
                                        "<filterable_psm_annotation_type name=\"q-value\""
                                                + " description=\"x\" filter_direction=\"below\""
                                                + " default_filter_value=\"0.5\"/>"),
                        StandardCharsets.UTF_8);

        assertEquals(Optional.empty(), LimelightXmlJudge.judge(cometQ, schema).psmQValueDefault());
    }

    @Test
    @DisplayName("both arguments are required")
    void nulls() {
        assertAll(
                () ->
                        assertEquals(
                                "file",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        LimelightXmlJudge.judge(
                                                                Nulls.of(Path.class), schema))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "schema",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        LimelightXmlJudge.judge(
                                                                known,
                                                                Nulls.of(LimelightSchema.class)))
                                        .getMessage()));
    }

    @Test
    @DisplayName("the summary refuses impossible values")
    void theSummary(@TempDir Path directory) {
        Path file = directory.resolve("x.xml");
        assertAll(
                () ->
                        assertThrows(
                                IllegalArgumentException.class,
                                () -> new LimelightXmlSummary(file, 0, 0, 0, Optional.empty())),
                () ->
                        assertThrows(
                                IllegalArgumentException.class,
                                () -> new LimelightXmlSummary(file, 1, -1, 0, Optional.empty())),
                () ->
                        assertThrows(
                                IllegalArgumentException.class,
                                () -> new LimelightXmlSummary(file, 1, 0, -1, Optional.empty())),
                () ->
                        assertEquals(
                                1, new LimelightXmlSummary(file, 1, 0, 0, Optional.empty()).size()),
                () ->
                        assertEquals(
                                directory,
                                new LimelightXmlSummary(file, 1, 2, 3, Optional.of("0.1"))
                                        .at(directory)
                                        .file()),
                () ->
                        assertEquals(
                                Optional.of("0.1"),
                                new LimelightXmlSummary(file, 1, 2, 3, Optional.of("0.1"))
                                        .at(directory)
                                        .psmQValueDefault()),
                () ->
                        assertEquals(
                                3,
                                new LimelightXmlSummary(file, 1, 2, 3, Optional.of("0.1"))
                                        .at(directory)
                                        .reportedPeptideCount()));
    }

    @Test
    @DisplayName("the schema pass alone fails a file that is not well-formed, never passes it")
    void theSchemaPassAloneRefusesMalformed(@TempDir Path directory) throws IOException {
        Path truncated =
                Files.writeString(
                        directory.resolve("cut.xml"),
                        knownText.substring(0, 5000),
                        StandardCharsets.UTF_8);

        LimelightConversionException refused =
                assertThrows(
                        LimelightConversionException.class,
                        () -> LimelightXmlJudge.validate(truncated, schema));

        assertAll(
                () -> assertEquals(Failure.SCHEMA_INVALID, refused.failure()),
                () ->
                        assertTrue(
                                refused.getMessage().contains(": 1 error(s), the first: "),
                                refused.getMessage()));
    }

    @Test
    @DisplayName("a stop after five quoted errors is counted and not quoted: still five quoted")
    void aStopAfterManyErrors(@TempDir Path directory) throws IOException {
        String broken = knownText.replace(" precursor_charge=\"", " precursor_charge=\"x");
        Path file =
                Files.writeString(
                        directory.resolve("broken-and-cut.xml"),
                        broken.substring(0, broken.length() - 200),
                        StandardCharsets.UTF_8);

        String message =
                assertThrows(
                                LimelightConversionException.class,
                                () -> LimelightXmlJudge.validate(file, schema))
                        .getMessage();

        assertAll(
                () -> assertTrue(message.contains(": 7795 error(s), the first: "), message),
                () -> assertEquals(4, message.split(" \\| ").length - 1, message));
    }
}
