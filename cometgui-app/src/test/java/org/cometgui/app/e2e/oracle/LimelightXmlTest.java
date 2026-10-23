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

package org.cometgui.app.e2e.oracle;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The Limelight XML reader against a hand-written document in the converter's shape, every count
 * worked out by hand: two reported peptides, five PSMs whose Percolator q-values are 0.01 (equal to
 * the cutoff), 0.0099, 0.0100001, missing and 1e-3 -- so three are at most 0.01 and one has none --
 * with a Comet annotation named q-value on the fourth that must not be read as Percolator's, and
 * the Percolator PSM q-value's default filter 0.01 beside a Comet type of the same name with
 * another default.
 */
class LimelightXmlTest {

    private static String psm(String percolatorQ, String extra) {
        String q =
                percolatorQ == null
                        ? ""
                        : "<filterable_psm_annotation search_program=\"Percolator\""
                                + " annotation_name=\"q-value\" value=\""
                                + percolatorQ
                                + "\"/>";
        return "<psm scan_number=\"1\"><filterable_psm_annotations>"
                + extra
                + q
                + "</filterable_psm_annotations></psm>";
    }

    private static final String DOCUMENT =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?><limelight_input><search_program_info>"
                    + "<search_programs><search_program name=\"Comet\"><psm_annotation_types>"
                    + "<filterable_psm_annotation_types><filterable_psm_annotation_type"
                    + " name=\"q-value\" default_filter_value=\"0.5\"/>"
                    + "</filterable_psm_annotation_types></psm_annotation_types></search_program>"
                    + "<search_program name=\"Percolator\"><psm_annotation_types>"
                    + "<filterable_psm_annotation_types><filterable_psm_annotation_type"
                    + " name=\"q-value\" default_filter_value=\"0.01\"/>"
                    + "<filterable_psm_annotation_type name=\"PEP\"/>"
                    + "</filterable_psm_annotation_types></psm_annotation_types></search_program>"
                    + "</search_programs></search_program_info><reported_peptides>"
                    + "<reported_peptide sequence=\"AB\"><psms>"
                    + psm("0.01", "")
                    + psm("0.0099", "")
                    + psm("0.0100001", "")
                    + "</psms></reported_peptide><reported_peptide sequence=\"CD\"><psms>"
                    + psm(
                            null,
                            "<filterable_psm_annotation search_program=\"Comet\""
                                    + " annotation_name=\"q-value\" value=\"0.0001\"/>")
                    + psm("1e-3", "")
                    + "</psms></reported_peptide></reported_peptides></limelight_input>";

    @TempDir private Path directory;

    @Test
    @DisplayName("at 0.01: 5 PSMs, 3 at most the cutoff, 1 without a Percolator q-value")
    void counts() throws IOException {
        Path file = directory.resolve("limelight.xml");
        Files.writeString(file, DOCUMENT, StandardCharsets.UTF_8);
        LimelightXml read = LimelightXml.read(file, "0.01");
        assertAll(
                () -> assertEquals("limelight_input", read.root()),
                () -> assertEquals(5, read.psms()),
                () -> assertEquals(2, read.reportedPeptides()),
                () -> assertEquals(3, read.psmsAtCutoff(), "a strict < would give 2"),
                () -> assertEquals(1, read.psmsWithoutQ()),
                () -> assertEquals(Optional.of("0.01"), read.qFilter()));
        assertEquals(2, LimelightXml.read(file, "0.0099").psmsAtCutoff(), "0.0099 and 1e-3");
    }

    @Test
    @DisplayName("a document cut short is not well-formed, and says so")
    void truncated() throws IOException {
        Path file = directory.resolve("cut.xml");
        Files.writeString(file, DOCUMENT.substring(0, 400), StandardCharsets.UTF_8);
        IOException refused =
                assertThrows(IOException.class, () -> LimelightXml.read(file, "0.01"));
        assertTrue(
                refused.getMessage().startsWith(file + " is not well-formed XML: "),
                refused.getMessage());
    }
}
