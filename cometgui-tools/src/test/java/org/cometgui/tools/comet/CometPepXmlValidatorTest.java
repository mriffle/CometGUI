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

package org.cometgui.tools.comet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import javax.xml.stream.XMLInputFactory;
import org.cometgui.domain.run.OutputBase;
import org.cometgui.domain.run.OutputBaseNames;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.tools.testing.Nulls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link CometPepXmlValidator} on constructed pepXML in the shape Comet 2026.03.0 writes (typed
 * from a real file, with most of it left out): every failure mode names the file. The real files
 * are validated in {@link CometAdapterRealBinaryTest}.
 */
class CometPepXmlValidatorTest {

    static final Path INPUT = TestPaths.absolute("data/k562 3.mzML");

    static final Path BASE = TestPaths.absolute("run/outputs/comet/k562 3");

    @TempDir private Path directory;

    /** A pepXML document with the given run-summary and search-summary attributes and queries. */
    static String document(String runSummary, String searchSummary, int queries) {
        StringBuilder xml =
                new StringBuilder(
                        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                                + " <msms_pipeline_analysis date=\"2026-10-06T17:59:02\""
                                + " xmlns=\"http://regis-web.systemsbiology.net/pepXML\""
                                + " xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""
                                + " summary_xml=\"/run/outputs/comet/k562 3.pep.xml\">\n");
        xml.append(" <msms_run_summary ")
                .append(runSummary)
                .append(" msManufacturer=\"UNKNOWN\" raw_data_type=\"raw\">\n")
                .append(" <sample_enzyme name=\"Trypsin\">\n")
                .append("  <specificity cut=\"KR\" no_cut=\"P\" sense=\"C\"/>\n")
                .append(" </sample_enzyme>\n")
                .append(" <search_summary ")
                .append(searchSummary)
                .append(" search_engine=\"Comet\">\n")
                .append("  <parameter name=\"decoy_search\" value=\"1\"/>\n")
                .append(" </search_summary>\n");
        for (int query = 1; query <= queries; query++) {
            xml.append(" <spectrum_query spectrum=\"k562_3.")
                    .append(query)
                    .append(".")
                    .append(query)
                    .append(".2\" start_scan=\"")
                    .append(query)
                    .append("\">\n  <search_result/>\n </spectrum_query>\n");
        }
        return xml.append(" </msms_run_summary>\n</msms_pipeline_analysis>\n").toString();
    }

    static final String RUN_SUMMARY = "base_name=\"/data/k562 3\" raw_data=\".mzML\"";

    static final String SEARCH_SUMMARY = "base_name=\"/run/outputs/comet/k562 3\"";

    private Path write(String text) throws IOException {
        return Files.writeString(directory.resolve("k562 3.pep.xml"), text, StandardCharsets.UTF_8);
    }

    private static CometOutputException refused(Path file) {
        CometOutputException refused =
                assertThrows(
                        CometOutputException.class,
                        () -> CometPepXmlValidator.validate(file, INPUT, BASE));
        assertEquals(file, refused.file());
        return refused;
    }

    @Test
    @DisplayName("a valid pepXML: its input, its -N base, its spectrum queries")
    void valid() throws IOException {
        Path file = write(document(RUN_SUMMARY, SEARCH_SUMMARY, 3));
        assertEquals(
                new PepXmlSummary(file, "/data/k562 3.mzML", "/run/outputs/comet/k562 3", 3),
                CometPepXmlValidator.validate(file, INPUT, BASE));
    }

    @Test
    @DisplayName("zero spectrum queries is a valid pepXML; the PIN check refuses an empty search")
    void zeroQueries() throws IOException {
        Path file = write(document(RUN_SUMMARY, SEARCH_SUMMARY, 0));
        assertEquals(0, CometPepXmlValidator.validate(file, INPUT, BASE).spectrumQueries());
    }

    @Test
    @DisplayName("an escaped input path is compared as the path it stands for")
    void escapedPath() throws IOException {
        Path file =
                write(
                        document(
                                "base_name=\"/data/a&amp;b\" raw_data=\".MZML\"",
                                SEARCH_SUMMARY,
                                1));
        assertEquals(
                "/data/a&b.MZML",
                CometPepXmlValidator.validate(file, TestPaths.absolute("data/a&b.MZML"), BASE)
                        .input());
    }

    @Test
    @DisplayName("the run-layout form finds outputs/comet/<base>.pep.xml and checks the same")
    void runLayoutForm() throws IOException {
        RunLayout run = new RunLayout(directory.resolve("run"));
        Files.createDirectories(run.cometOutputDirectory());
        OutputBase input = OutputBaseNames.derive(List.of(INPUT)).get(0);
        Path file = run.pepXmlFile("k562 3");
        Files.writeString(
                file,
                document(RUN_SUMMARY, "base_name=\"" + run.cometOutputBase("k562 3") + "\"", 2));
        assertEquals(
                new PepXmlSummary(
                        file, "/data/k562 3.mzML", run.cometOutputBase("k562 3").toString(), 2),
                CometPepXmlValidator.validate(run, input));
        Path elsewhere = directory.resolve("other/run");
        assertThrows(
                CometOutputException.class,
                () -> CometPepXmlValidator.validate(new RunLayout(elsewhere), input));
        assertThrows(
                NullPointerException.class,
                () -> CometPepXmlValidator.validate(Nulls.of(RunLayout.class), input));
        assertThrows(
                NullPointerException.class,
                () -> CometPepXmlValidator.validate(run, Nulls.of(OutputBase.class)));
    }

    @Test
    @DisplayName("missing, a directory, empty: each fails naming the file")
    void absentOrEmpty() throws IOException {
        Path missing = directory.resolve("absent.pep.xml");
        assertEquals(
                "the pepXML file " + missing + " does not exist: Comet did not write it",
                refused(missing).getMessage());
        Path folder = Files.createDirectories(directory.resolve("folder.pep.xml"));
        assertEquals(
                "the pepXML file " + folder + " is a directory, not a file",
                refused(folder).getMessage());
        Path empty = write("");
        assertEquals(
                "the pepXML file " + empty + " is empty: Comet left a zero-byte file",
                refused(empty).getMessage());
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    @DisplayName("an unreadable file, and one in an unreadable directory, fail naming the file")
    void unreadable() throws IOException {
        Path file = write(document(RUN_SUMMARY, SEARCH_SUMMARY, 1));
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("---------"));
        try {
            assertEquals(
                    "the pepXML file " + file + " cannot be read: " + file,
                    refused(file).getMessage());
        } finally {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        }
        Path locked = Files.createDirectories(directory.resolve("locked"));
        Path inside = Files.writeString(locked.resolve("x.pep.xml"), "<x/>");
        Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("---------"));
        try {
            assertEquals(
                    "the pepXML file " + inside + " cannot be read: " + inside,
                    refused(inside).getMessage());
        } finally {
            Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("rwx------"));
        }
    }

    @Test
    @DisplayName("a truncated file is not well-formed and fails naming it")
    void truncated() throws IOException {
        String whole = document(RUN_SUMMARY, SEARCH_SUMMARY, 5);
        Path file = write(whole.substring(0, whole.length() / 2));
        CometOutputException refused = refused(file);
        assertTrue(
                refused.getMessage()
                        .startsWith("the pepXML file " + file + " is not well-formed XML: "),
                refused.getMessage());
    }

    @Test
    @DisplayName("a file losing only its closing tags is still refused")
    void lostClosingTags() throws IOException {
        String whole = document(RUN_SUMMARY, SEARCH_SUMMARY, 2);
        Path file = write(whole.substring(0, whole.indexOf(" </msms_run_summary>")));
        assertTrue(
                refused(file)
                        .getMessage()
                        .startsWith("the pepXML file " + file + " is not well-formed XML: "));
    }

    @Test
    @DisplayName("malformed XML fails naming the file")
    void malformed() throws IOException {
        Path file =
                write(
                        document(RUN_SUMMARY, SEARCH_SUMMARY, 1)
                                .replace("</sample_enzyme>", "</sample_enzym>"));
        assertTrue(
                refused(file)
                        .getMessage()
                        .startsWith("the pepXML file " + file + " is not well-formed XML: "));
    }

    @Test
    @DisplayName("a DOCTYPE with an external entity is refused unread")
    void doctype(@TempDir Path secrets) throws IOException {
        Path secret = Files.writeString(secrets.resolve("secret.txt"), "TOP-SECRET-VALUE");
        Path file =
                write(
                        "<?xml version=\"1.0\"?>\n<!DOCTYPE msms_pipeline_analysis [<!ENTITY s"
                                + " SYSTEM \""
                                + secret.toUri()
                                + "\">]>\n"
                                + document(RUN_SUMMARY, SEARCH_SUMMARY, 1)
                                        .substring(39)
                                        .replace("Trypsin", "&s;"));
        CometOutputException refused = refused(file);
        assertEquals(
                "the pepXML file "
                        + file
                        + " declares a DOCTYPE, which Comet never writes and which is refused"
                        + " unread",
                refused.getMessage());
        assertFalse(refused.getMessage().contains("TOP-SECRET-VALUE"));
    }

    @Test
    @DisplayName("hardening is forced: a factory set unsafe comes back safe")
    void hardened() {
        XMLInputFactory factory = XMLInputFactory.newDefaultFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.TRUE);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.TRUE);
        assertSame(factory, CometPepXmlValidator.harden(factory));
        assertEquals(Boolean.FALSE, factory.getProperty(XMLInputFactory.SUPPORT_DTD));
        assertEquals(
                Boolean.FALSE,
                factory.getProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES));
    }

    @Test
    @DisplayName("a document that is not pepXML fails naming its element and namespace")
    void notPepXml() throws IOException {
        Path other = write("<?xml version=\"1.0\"?>\n<percolator_output xmlns=\"urn:x\"/>\n");
        assertEquals(
                "the pepXML file "
                        + other
                        + " is not a pepXML file: its document element is {urn:x}percolator_output,"
                        + " not {http://regis-web.systemsbiology.net/pepXML}msms_pipeline_analysis",
                refused(other).getMessage());
        Path noNamespace =
                write(
                        document(RUN_SUMMARY, SEARCH_SUMMARY, 1)
                                .replace(
                                        " xmlns=\"http://regis-web.systemsbiology.net/pepXML\"",
                                        ""));
        assertEquals(
                "the pepXML file "
                        + noNamespace
                        + " is not a pepXML file: its document element is {}msms_pipeline_analysis,"
                        + " not {http://regis-web.systemsbiology.net/pepXML}msms_pipeline_analysis",
                refused(noNamespace).getMessage());
    }

    @Test
    @DisplayName("zero or two run summaries, zero or two search summaries, are refused")
    void summaryCounts() throws IOException {
        String one = document(RUN_SUMMARY, SEARCH_SUMMARY, 1);
        int start = one.indexOf(" <msms_run_summary");
        int end = one.indexOf("</msms_pipeline_analysis>");
        Path none = write(one.substring(0, start) + one.substring(end));
        assertEquals(
                "the pepXML file " + none + " holds 0 msms_run_summary elements, not exactly one",
                refused(none).getMessage());
        Path two = write(one.substring(0, end) + one.substring(start, end) + one.substring(end));
        assertEquals(
                "the pepXML file " + two + " holds 2 msms_run_summary elements, not exactly one",
                refused(two).getMessage());
        int searchStart = one.indexOf(" <search_summary");
        int searchEnd = one.indexOf(" </search_summary>\n") + " </search_summary>\n".length();
        Path noSearch = write(one.substring(0, searchStart) + one.substring(searchEnd));
        assertEquals(
                "the pepXML file " + noSearch + " holds 0 search_summary elements, not exactly one",
                refused(noSearch).getMessage());
        Path twoSearch =
                write(
                        one.substring(0, searchEnd)
                                + one.substring(searchStart, searchEnd)
                                + one.substring(searchEnd));
        assertEquals(
                "the pepXML file "
                        + twoSearch
                        + " holds 2 search_summary elements, not exactly one",
                refused(twoSearch).getMessage());
    }

    @Test
    @DisplayName("a pepXML naming another input fails naming both")
    void otherInput() throws IOException {
        Path file =
                write(document("base_name=\"/data/k562 4\" raw_data=\".mzML\"", SEARCH_SUMMARY, 1));
        assertEquals(
                "the pepXML file "
                        + file
                        + " names its input \"/data/k562 4.mzML\" (msms_run_summary base_name and"
                        + " raw_data), not the spectrum file the search was given, \"/data/k562"
                        + " 3.mzML\"",
                refused(file).getMessage());
        Path bare = write(document("msModel=\"x\"", SEARCH_SUMMARY, 1));
        assertEquals(
                "the pepXML file "
                        + bare
                        + " names its input \"\" (msms_run_summary base_name and raw_data), not"
                        + " the spectrum file the search was given, \"/data/k562 3.mzML\"",
                refused(bare).getMessage());
    }

    @Test
    @DisplayName("a pepXML naming another -N base (a stale file) fails naming both")
    void otherBase() throws IOException {
        Path file = write(document(RUN_SUMMARY, "base_name=\"/old/run/k562 3\"", 1));
        assertEquals(
                "the pepXML file "
                        + file
                        + " names the output base \"/old/run/k562 3\" (search_summary"
                        + " base_name), not the -N base the search was given,"
                        + " \"/run/outputs/comet/k562 3\"",
                refused(file).getMessage());
        Path bare = write(document(RUN_SUMMARY, "search_id=\"1\"", 1));
        assertTrue(refused(bare).getMessage().contains("names the output base \"\""));
    }

    @Test
    @DisplayName("elements outside the pepXML namespace are not counted")
    void foreignNamespace() throws IOException {
        Path file =
                write(
                        document(RUN_SUMMARY, SEARCH_SUMMARY, 2)
                                .replace(
                                        "<sample_enzyme name=\"Trypsin\">",
                                        "<sample_enzyme name=\"Trypsin\"><f:spectrum_query"
                                                + " xmlns:f=\"urn:f\"/><f:msms_run_summary"
                                                + " xmlns:f=\"urn:f\"/><f:search_summary"
                                                + " xmlns:f=\"urn:f\" base_name=\"/x\"/>"));
        assertEquals(2, CometPepXmlValidator.validate(file, INPUT, BASE).spectrumQueries());
    }

    @Test
    @DisplayName("nulls are refused, and a summary refuses a negative count")
    void nulls() {
        assertThrows(
                NullPointerException.class,
                () -> CometPepXmlValidator.validate(Nulls.of(Path.class), INPUT, BASE));
        assertThrows(
                NullPointerException.class,
                () -> CometPepXmlValidator.validate(INPUT, Nulls.of(Path.class), BASE));
        assertThrows(
                NullPointerException.class,
                () -> CometPepXmlValidator.validate(INPUT, INPUT, Nulls.of(Path.class)));
        assertThrows(
                NullPointerException.class,
                () -> CometPepXmlValidator.harden(Nulls.of(XMLInputFactory.class)));
        assertEquals(
                "a count cannot be negative: -1",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> new PepXmlSummary(INPUT, "a", "b", -1))
                        .getMessage());
    }
}
