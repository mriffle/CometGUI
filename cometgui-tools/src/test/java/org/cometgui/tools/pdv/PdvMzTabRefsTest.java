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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The (decoy, PSMId) to {@code spectra_ref} index over an mzTab: written here column by column,
 * with the exporter's column names, in the exporter's order and in another order.
 */
class PdvMzTabRefsTest {

    @TempDir private Path work;

    private static final List<String> HEADER = PdvMzTabExporter.COLUMNS;

    private static String row(String psmId, String decoy, String ref) {
        List<String> fields = new ArrayList<>();
        for (String column : HEADER) {
            if ("spectra_ref".equals(column)) {
                fields.add(ref);
            } else if (column.equals(PdvMzTabExporter.DECOY_COLUMN)) {
                fields.add(decoy);
            } else if (column.equals(PdvMzTabExporter.PSM_ID_COLUMN)) {
                fields.add(psmId);
            } else {
                fields.add("null");
            }
        }
        return "PSM\t" + String.join("\t", fields);
    }

    private Path mzTab(String... lines) throws IOException {
        List<String> all = new ArrayList<>();
        all.add("MTD\tmzTab-version\t1.0.0");
        all.add("MTD\tms_run[1]-location\tfile:///x/ms_run-1.mgf");
        for (String line : lines) {
            all.add(line);
        }
        return Files.write(work.resolve("pdv.mztab"), all, StandardCharsets.UTF_8);
    }

    private static String header() {
        return "PSH\t" + String.join("\t", HEADER);
    }

    @Test
    @DisplayName("a target and a decoy row sharing a PSMId are two keys, each with its own ref")
    void targetAndDecoyShareAPsmId() throws IOException {
        PdvMzTabRefs refs =
                PdvMzTabRefs.read(
                        mzTab(
                                header(),
                                row("/r/outputs/comet/a_101_2_1", "0", "ms_run[1]:index=0"),
                                row("/r/outputs/comet/a_101_2_2", "0", "ms_run[1]:index=1"),
                                row("/r/outputs/comet/a_101_2_1", "1", "ms_run[1]:index=2")));

        assertAll(
                () -> assertEquals(3, refs.size()),
                () ->
                        assertEquals(
                                Optional.of("ms_run[1]:index=0"),
                                refs.spectraRef(false, "/r/outputs/comet/a_101_2_1")),
                () ->
                        assertEquals(
                                Optional.of("ms_run[1]:index=2"),
                                refs.spectraRef(true, "/r/outputs/comet/a_101_2_1")),
                () ->
                        assertEquals(
                                Optional.of("ms_run[1]:index=1"),
                                refs.spectraRef(false, "/r/outputs/comet/a_101_2_2")),
                () ->
                        assertEquals(
                                Optional.empty(),
                                refs.spectraRef(true, "/r/outputs/comet/a_101_2_2")),
                () -> assertEquals(Optional.empty(), refs.spectraRef(false, "a_101_2_1")));
    }

    @Test
    @DisplayName("columns are found by name, not by position")
    void columnsByName() throws IOException {
        PdvMzTabRefs refs =
                PdvMzTabRefs.read(
                        mzTab(
                                "PSH\t"
                                        + PdvMzTabExporter.PSM_ID_COLUMN
                                        + "\tsequence\t"
                                        + PdvMzTabExporter.DECOY_COLUMN
                                        + "\tspectra_ref",
                                "PSM\tp1\tPEPTIDEK\t0\tms_run[2]:index=4"));
        assertEquals(Optional.of("ms_run[2]:index=4"), refs.spectraRef(false, "p1"));
    }

    @Test
    @DisplayName("the exporter's own document is read: every row it wrote is a key")
    void theExportersDocument() throws IOException {
        PdvMzTabRefs refs = PdvMzTabRefs.read(mzTab(header()));
        assertEquals(0, refs.size());
    }

    @Test
    @DisplayName("a document of another shape is refused, naming the line and what it holds")
    void refusals() throws IOException {
        String good = row("p1", "0", "ms_run[1]:index=0");
        assertAll(
                () ->
                        assertEquals(
                                lineSays(5, "a second PSH line"),
                                refused(header(), good, header())),
                () -> assertEquals(lineSays(3, "a PSM line before the PSH line"), refused(good)),
                () -> assertEquals(lineSays(2, "no PSH line"), refused()),
                () ->
                        assertEquals(
                                lineSays(3, "a PSH line without the column spectra_ref"),
                                refused("PSH\tsequence")),
                () ->
                        assertEquals(
                                lineSays(
                                        3,
                                        "a PSH line without the column "
                                                + PdvMzTabExporter.DECOY_COLUMN),
                                refused("PSH\tspectra_ref\t" + PdvMzTabExporter.PSM_ID_COLUMN)),
                () ->
                        assertEquals(
                                lineSays(
                                        3,
                                        "a PSH line without the column "
                                                + PdvMzTabExporter.PSM_ID_COLUMN),
                                refused("PSH\tspectra_ref\t" + PdvMzTabExporter.DECOY_COLUMN)),
                () ->
                        assertEquals(
                                lineSays(
                                        4,
                                        "a PSM line of "
                                                + HEADER.size()
                                                + " fields under a PSH line of "
                                                + (HEADER.size() + 1)),
                                refused(header(), good.substring(0, good.lastIndexOf('\t')))),
                () ->
                        assertEquals(
                                lineSays(4, "the decoy flag \"true\", which is not 0 or 1"),
                                refused(header(), row("p1", "true", "ms_run[1]:index=0"))),
                () ->
                        assertEquals(
                                lineSays(4, "a PSM line without a spectra_ref"),
                                refused(header(), row("p1", "0", "null"))),
                () ->
                        assertEquals(
                                lineSays(4, "a PSM line without a spectra_ref"),
                                refused(header(), row("p1", "0", ""))),
                () ->
                        assertEquals(
                                lineSays(4, "a PSM line without a PSMId"),
                                refused(header(), row("null", "0", "ms_run[1]:index=0"))),
                () ->
                        assertEquals(
                                lineSays(4, "a PSM line without a PSMId"),
                                refused(header(), row("", "1", "ms_run[1]:index=0"))),
                () ->
                        assertEquals(
                                lineSays(5, "a second target row for the PSM p1"),
                                refused(header(), good, row("p1", "0", "ms_run[1]:index=1"))),
                () ->
                        assertEquals(
                                lineSays(5, "a second decoy row for the PSM p2"),
                                refused(
                                        header(),
                                        row("p2", "1", "ms_run[1]:index=0"),
                                        row("p2", "1", "ms_run[1]:index=1"))));
    }

    private String lineSays(int line, String what) {
        return work.resolve("pdv.mztab")
                + " is not an mzTab whose PSMs can be selected in PDV: line "
                + line
                + " holds "
                + what
                + ".";
    }

    private String refused(String... lines) throws IOException {
        Path file = mzTab(lines);
        return assertThrows(IOException.class, () -> PdvMzTabRefs.read(file)).getMessage();
    }
}
