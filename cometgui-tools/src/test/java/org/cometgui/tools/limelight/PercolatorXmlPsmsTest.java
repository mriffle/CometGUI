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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.cometgui.tools.limelight.LimelightFixtures.Psm;
import org.cometgui.tools.testing.Nulls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * The Percolator XML reader: hand-written documents for every rule, then the real 3.07.1 outputs
 * with and without {@code -Z}.
 */
class PercolatorXmlPsmsTest {

    @Test
    @DisplayName("a target-only document: every id in order, no decoy, Percolator XML")
    void targetOnly(@TempDir Path directory) throws IOException {
        Path file =
                LimelightFixtures.pout(
                        directory.resolve("t.pout.xml"),
                        List.of(
                                Psm.target("a_1_2_1"),
                                Psm.target("a_7_3_1"),
                                Psm.target("b_1_2_1")));

        PercolatorXmlPsms read = PercolatorXmlPsms.read(file);

        assertAll(
                () -> assertTrue(read.isPercolatorXml()),
                () -> assertEquals(List.of("a_1_2_1", "a_7_3_1", "b_1_2_1"), read.psmIds()),
                () -> assertEquals(0, read.decoyPsmCount()),
                () -> assertEquals(file, read.file()));
    }

    @Test
    @DisplayName("decoys are the psm elements saying p:decoy=\"true\", and nothing else")
    void decoysAreCounted(@TempDir Path directory) throws IOException {
        Path file =
                LimelightFixtures.pout(
                        directory.resolve("z.pout.xml"),
                        List.of(
                                new Psm("a_1_2_1", "false"),
                                new Psm("a_2_2_1", "true"),
                                new Psm("a_3_2_1", "true"),
                                Psm.target("a_4_2_1")));

        PercolatorXmlPsms read = PercolatorXmlPsms.read(file);

        assertAll(
                () -> assertEquals(2, read.decoyPsmCount()),
                () -> assertEquals(4, read.psmIds().size(), "psm_id references are not PSMs"));
    }

    @Test
    @DisplayName("a decoy value other than true or false is refused, naming the file and value")
    void anUnknownDecoyValueIsRefused(@TempDir Path directory) throws IOException {
        Path file =
                LimelightFixtures.pout(
                        directory.resolve("x.pout.xml"),
                        List.of(
                                Psm.target("a_1_2_1"),
                                new Psm("a_2_2_1", "TRUE"),
                                new Psm("a_3_2_1", "maybe"),
                                new Psm("a_4_2_1", "true")));

        assertEquals(
                "the Percolator XML file "
                        + file
                        + " says p:decoy=[TRUE, maybe]; Percolator writes only \"true\" or"
                        + " \"false\"",
                assertThrows(IOException.class, () -> PercolatorXmlPsms.read(file)).getMessage());
    }

    @Test
    @DisplayName("a psm without an id is refused, with how many there are")
    void aPsmWithoutAnIdIsRefused(@TempDir Path directory) throws IOException {
        Path file =
                LimelightFixtures.pout(
                        directory.resolve("x.pout.xml"),
                        List.of(Psm.target(""), Psm.target("a_1_2_1"), Psm.target("")));

        assertEquals(
                "the Percolator XML file " + file + " has 2 psm element(s) without a p:psm_id",
                assertThrows(IOException.class, () -> PercolatorXmlPsms.read(file)).getMessage());
    }

    @Test
    @DisplayName("an id in no namespace is not Percolator's p:psm_id")
    void anIdInNoNamespaceIsNotAnId(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("x.pout.xml");
        Files.writeString(
                file,
                "<percolator_output xmlns=\"http://per-colator.com/percolator_out/15\">"
                        + "<psms><psm psm_id=\"a_1_2_1\"/></psms></percolator_output>");

        assertTrue(
                assertThrows(IOException.class, () -> PercolatorXmlPsms.read(file))
                        .getMessage()
                        .contains("without a p:psm_id"));
    }

    @Test
    @DisplayName("PSMs are known by local name, as PoutDocument has always counted them")
    void psmsAreKnownByLocalName(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("x.pout.xml");
        Files.writeString(
                file,
                "<percolator_output xmlns=\"http://per-colator.com/percolator_out/15\""
                        + " xmlns:p=\"http://per-colator.com/percolator_out/15\""
                        + " xmlns:o=\"urn:other\"><psms><o:psm p:psm_id=\"a_1_2_1\""
                        + " p:decoy=\"true\"/><psm p:psm_id=\"b_1_2_1\"/></psms>"
                        + "</percolator_output>");

        PercolatorXmlPsms read = PercolatorXmlPsms.read(file);

        assertAll(
                () -> assertEquals(List.of("a_1_2_1", "b_1_2_1"), read.psmIds()),
                () -> assertEquals(1, read.decoyPsmCount()));
    }

    @Test
    @DisplayName("another document is read no further: not Percolator XML, no PSMs")
    void anotherDocumentIsNotPercolatorXml(@TempDir Path directory) throws IOException {
        Path other = directory.resolve("other.xml");
        Files.writeString(
                other,
                "<msms_pipeline_analysis xmlns=\"http://regis-web.systemsbiology.net/pepXML\">"
                        + "<psm xmlns=\"http://per-colator.com/percolator_out/15\""
                        + " xmlns:p=\"http://per-colator.com/percolator_out/15\""
                        + " p:psm_id=\"a_1_2_1\" p:decoy=\"true\"/></msms_pipeline_analysis>");
        Path wrongNamespace = directory.resolve("ns.xml");
        Files.writeString(
                wrongNamespace,
                "<percolator_output xmlns=\"http://per-colator.com/percolator_out/14\">"
                        + "</percolator_output>");
        Path noNamespace = directory.resolve("none.xml");
        Files.writeString(noNamespace, "<percolator_output/>");

        assertAll(
                () -> assertFalse(PercolatorXmlPsms.read(other).isPercolatorXml()),
                () -> assertEquals(List.of(), PercolatorXmlPsms.read(other).psmIds()),
                () -> assertEquals(0, PercolatorXmlPsms.read(other).decoyPsmCount()),
                () -> assertFalse(PercolatorXmlPsms.read(wrongNamespace).isPercolatorXml()),
                () -> assertFalse(PercolatorXmlPsms.read(noNamespace).isPercolatorXml()));
    }

    @Test
    @DisplayName("a missing file, an empty file and a malformed one each have their own refusal")
    void missingEmptyMalformed(@TempDir Path directory) throws IOException {
        Path missing = directory.resolve("missing.pout.xml");
        Path empty = Files.createFile(directory.resolve("empty.pout.xml"));
        Path malformed =
                Files.writeString(
                        directory.resolve("bad.pout.xml"),
                        "<percolator_output xmlns=\"http://per-colator.com/percolator_out/15\">"
                                + "<psms>");

        assertAll(
                () ->
                        assertEquals(
                                "Percolator wrote no file at "
                                        + missing
                                        + ", so it produced no XML to inspect",
                                assertThrows(
                                                IOException.class,
                                                () -> PercolatorXmlPsms.read(missing))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "Percolator wrote no file at "
                                        + directory
                                        + ", so it produced no XML to inspect",
                                assertThrows(
                                                IOException.class,
                                                () -> PercolatorXmlPsms.read(directory))
                                        .getMessage()),
                () ->
                        assertTrue(
                                assertThrows(IOException.class, () -> PercolatorXmlPsms.read(empty))
                                        .getMessage()
                                        .startsWith(
                                                "Percolator left a ZERO-BYTE file at " + empty)),
                () ->
                        assertTrue(
                                assertThrows(
                                                IOException.class,
                                                () -> PercolatorXmlPsms.read(malformed))
                                        .getMessage()
                                        .startsWith(
                                                "the file Percolator wrote at "
                                                        + malformed
                                                        + " is not well-formed XML: ")));
    }

    @Test
    @DisplayName("a DOCTYPE is refused, and an external entity is never read")
    void aDoctypeIsRefused(@TempDir Path directory) throws IOException {
        Path secret = Files.writeString(directory.resolve("secret.txt"), "a_9_9_9");
        String body =
                "<percolator_output xmlns=\"http://per-colator.com/percolator_out/15\""
                        + " xmlns:p=\"http://per-colator.com/percolator_out/15\"><psms>";
        Path referenced =
                Files.writeString(
                        directory.resolve("xxe.pout.xml"),
                        "<?xml version=\"1.0\"?>\n<!DOCTYPE percolator_output [<!ENTITY s SYSTEM \""
                                + secret.toUri()
                                + "\">]>\n"
                                + body
                                + "<psm p:psm_id=\"&s;\"/></psms></percolator_output>");
        Path declared =
                Files.writeString(
                        directory.resolve("dtd.pout.xml"),
                        "<?xml version=\"1.0\"?>\n<!DOCTYPE percolator_output [<!ENTITY s SYSTEM \""
                                + secret.toUri()
                                + "\">]>\n"
                                + body
                                + "<psm p:psm_id=\"a_1_2_1\"/></psms></percolator_output>");

        IOException entity =
                assertThrows(IOException.class, () -> PercolatorXmlPsms.read(referenced));
        IOException doctype =
                assertThrows(IOException.class, () -> PercolatorXmlPsms.read(declared));

        assertAll(
                () -> assertFalse(entity.getMessage().contains("a_9_9_9"), entity.getMessage()),
                () ->
                        assertEquals(
                                "the Percolator XML file "
                                        + declared
                                        + " carries a DOCTYPE, which Percolator never writes, so it"
                                        + " is not read",
                                doctype.getMessage()),
                () ->
                        assertEquals(
                                "file",
                                assertThrows(
                                                NullPointerException.class,
                                                () -> PercolatorXmlPsms.read(Nulls.of(Path.class)))
                                        .getMessage()));
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    @DisplayName(
            "REAL 3.07.1 -X output: 3897 PSMs over both files, no decoy attribute, zero decoys")
    void realTargetOnlyOutput() throws IOException {
        PercolatorXmlPsms read =
                PercolatorXmlPsms.read(
                        LimelightFixtures.real(
                                LimelightFixtures.POUT, LimelightFixtures.POUT_SHA256));

        assertAll(
                () -> assertTrue(read.isPercolatorXml()),
                () -> assertEquals(3897, read.psmIds().size()),
                () -> assertEquals(0, read.decoyPsmCount()),
                () ->
                        assertEquals(
                                "20100614_Velos1_TaGe_SA_K562_3_11367_2_1", read.psmIds().get(0)),
                () ->
                        assertEquals(
                                Map.of(
                                        "20100614_Velos1_TaGe_SA_K562_3.pep.xml", 2130L,
                                        "20100614_Velos1_TaGe_SA_K562_4.pep.xml", 1767L),
                                byFile(read)));
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    @DisplayName("REAL 3.07.1 -X -Z outputs: 2773 and 2780 decoy PSMs, read from p:decoy")
    void realDecoyOutputs() throws IOException {
        PercolatorXmlPsms internal =
                PercolatorXmlPsms.read(
                        LimelightFixtures.real(
                                LimelightFixtures.WITH_Z, LimelightFixtures.WITH_Z_SHA256));
        PercolatorXmlPsms concatenated =
                PercolatorXmlPsms.read(
                        LimelightFixtures.real(
                                LimelightFixtures.CONCAT, LimelightFixtures.CONCAT_SHA256));

        assertAll(
                () -> assertTrue(internal.isPercolatorXml()),
                () -> assertEquals(6670, internal.psmIds().size()),
                () -> assertEquals(2773, internal.decoyPsmCount()),
                () -> assertEquals(6670, concatenated.psmIds().size()),
                () -> assertEquals(2780, concatenated.decoyPsmCount()));
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    @DisplayName("REAL Comet pepXML is well-formed XML and is not Percolator XML")
    void realPepXmlIsNotPercolatorXml() throws IOException {
        Path pepXml =
                LimelightFixtures.real(
                        LimelightFixtures.COMET_DIRECTORY + "/" + LimelightFixtures.PEPXML_3,
                        LimelightFixtures.PEPXML_3_SHA256);

        PercolatorXmlPsms read = PercolatorXmlPsms.read(pepXml);

        assertAll(
                () -> assertFalse(read.isPercolatorXml()),
                () -> assertEquals(List.of(), read.psmIds()));
    }

    private static Map<String, Long> byFile(PercolatorXmlPsms read) {
        return read.psmIds().stream()
                .collect(
                        Collectors.groupingBy(
                                PepXmlSet::expectedFileName, TreeMap::new, Collectors.counting()));
    }
}
