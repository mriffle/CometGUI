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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The {@code comet.params} oracle against a hand-written file, every expected value typed out. */
class CometParamsFileTest {

    /** Twelve lines: a header comment, five parameters, a blank, a duplicate, junk, the enzymes. */
    private static final String FILE =
            "# comet_version 2026.03 rev. 0\n"
                    + "database_name = /data/my proteome.fasta\n"
                    + "peptide_mass_tolerance_upper = 20.0    # upper bound of the window\n"
                    + "variable_mod01 = 15.994915 M 0 3 -1 0 0 0.0\n"
                    + "num_threads=4\n"
                    + "spectrum_batch_size =\n"
                    + "\n"
                    + "decoy_search = 1\n"
                    + "decoy_search = 0   # given twice\n"
                    + "this line is wrong\n"
                    + "[COMET_ENZYME_INFO]\n"
                    + "0.  Cut_everywhere         0      -           -\n"
                    + "1.  Trypsin                1      KR          P\n";

    @TempDir private Path directory;

    @Test
    @DisplayName("each value is the text between = and the comment, trimmed and otherwise exact")
    void valuesAreExact() throws IOException {
        Path file = directory.resolve("comet.params");
        Files.writeString(file, FILE, StandardCharsets.UTF_8);
        CometParamsFile params = CometParamsFile.read(file);
        assertAll(
                () ->
                        assertEquals(
                                List.of(
                                        "database_name",
                                        "peptide_mass_tolerance_upper",
                                        "variable_mod01",
                                        "num_threads",
                                        "spectrum_batch_size",
                                        "decoy_search"),
                                List.copyOf(params.values().keySet()),
                                "names, in file order"),
                () -> assertEquals("/data/my proteome.fasta", params.value("database_name")),
                () -> assertEquals("20.0", params.value("peptide_mass_tolerance_upper")),
                () -> assertEquals("15.994915 M 0 3 -1 0 0 0.0", params.value("variable_mod01")),
                () -> assertEquals("4", params.value("num_threads")),
                () -> assertEquals("", params.value("spectrum_batch_size")),
                () -> assertEquals("1", params.value("decoy_search"), "the first of two"),
                () -> assertEquals(Map.of("decoy_search", List.of(8, 9)), params.duplicates()),
                () -> assertEquals(Integer.valueOf(4), params.lines().get("variable_mod01")),
                () -> assertEquals(List.of("10: this line is wrong"), params.unparsed()),
                () ->
                        assertEquals(
                                List.of(
                                        "0.  Cut_everywhere         0      -           -",
                                        "1.  Trypsin                1      KR          P"),
                                params.enzymes()));
    }

    @Test
    @DisplayName("CRLF line ends read the same as LF")
    void crlfIsTheSame() {
        CometParamsFile lf = CometParamsFile.parse(FILE);
        CometParamsFile crlf = CometParamsFile.parse(FILE.replace("\n", "\r\n"));
        assertEquals(lf, crlf);
    }

    @Test
    @DisplayName("asking for a parameter the file does not set fails, naming it and what is set")
    void anAbsentParameterFails() {
        CometParamsFile params = CometParamsFile.parse("num_threads = 4\n");
        AssertionError failed =
                assertThrows(AssertionError.class, () -> params.value("output_pepxmlfile"));
        assertEquals(
                "comet.params sets no output_pepxmlfile; it sets [num_threads]",
                failed.getMessage());
    }
}
