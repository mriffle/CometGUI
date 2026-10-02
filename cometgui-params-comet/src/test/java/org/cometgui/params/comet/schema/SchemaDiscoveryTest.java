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

package org.cometgui.params.comet.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import org.cometgui.params.comet.fixtures.CometFixtures;
import org.cometgui.params.comet.fixtures.DeclarationLines;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Discovery from the real dumps, and refusal of CONSTRUCTED dumps that are not well formed. */
class SchemaDiscoveryTest {

    static String fixtureText(CometFixtures.Mode mode) throws IOException {
        return new String(
                CometFixtures.bytes(
                        CometFixtures.COMET_2026_02_2, CometFixtures.LINUX_X86_64, mode),
                StandardCharsets.UTF_8);
    }

    private static final String MINIMAL =
            "# comet_version 2026.02 rev. 2 (6edec91)\n"
                    + "num_threads = 0\n"
                    + "[COMET_ENZYME_INFO]\n"
                    + "0.  Cut_everywhere 0 - -\n";

    @Test
    @DisplayName("-q: complete, 118 names in file order, with defaults, comments and 12 enzymes")
    void theCompleteDump() throws IOException {
        String text = fixtureText(CometFixtures.Mode.COMPLETE);
        DiscoveredSchema schema = SchemaDiscovery.discover(text, DiscoveryMode.COMPLETE);
        assertEquals(DiscoveryMode.COMPLETE, schema.mode());
        assertEquals(DeclarationLines.names(text.lines().toList()), schema.names());
        assertEquals(118, schema.names().size());
        assertEquals("2026.02.2", schema.marker().toolVersion().text());
        DiscoveredParameter upper = schema.parameter("peptide_mass_tolerance_upper").orElseThrow();
        assertEquals("20.0", upper.defaultText());
        assertEquals(
                Optional.of("upper bound of the precursor mass tolerance"), upper.inlineComment());
        assertEquals(30, upper.lineNumber());
        assertEquals("", schema.parameter("pinfile_protein_delimiter").orElseThrow().defaultText());
        assertEquals(
                "0.0 X 0 3 -1 0 0 0.0",
                schema.parameter("variable_mod15").orElseThrow().defaultText());
        assertEquals(12, schema.enzymeRows().size());
        assertEquals("1.  Trypsin                1      KR          P", schema.enzymeRows().get(1));
        assertEquals(Optional.empty(), schema.parameter("no_such_parameter"));
    }

    @Test
    @DisplayName("-p: partial, 96 names, the 22 -q-only ones absent")
    void theDefaultsDump() throws IOException {
        DiscoveredSchema partial =
                SchemaDiscovery.discover(
                        fixtureText(CometFixtures.Mode.DEFAULTS), DiscoveryMode.PARTIAL_DISCOVERY);
        DiscoveredSchema complete =
                SchemaDiscovery.discover(
                        fixtureText(CometFixtures.Mode.COMPLETE), DiscoveryMode.COMPLETE);
        assertEquals(DiscoveryMode.PARTIAL_DISCOVERY, partial.mode());
        assertEquals(96, partial.names().size());
        TreeSet<String> missing = new TreeSet<>(complete.names());
        missing.removeAll(partial.names());
        assertEquals(22, missing.size());
        assertTrue(
                missing.contains("variable_mod06") && missing.contains("num_results"),
                missing::toString);
        assertEquals(complete.enzymeRows(), partial.enzymeRows());
    }

    @Test
    @DisplayName("a constructed minimal dump is read")
    void aMinimalDump() {
        DiscoveredSchema schema = SchemaDiscovery.discover(MINIMAL, DiscoveryMode.COMPLETE);
        assertEquals(List.of("num_threads"), schema.names());
        assertEquals(2, schema.parameters().get(0).lineNumber());
        assertEquals(List.of("0.  Cut_everywhere 0 - -"), schema.enzymeRows());
    }

    private static MalformedDumpException refused(String constructed) {
        return assertThrows(
                MalformedDumpException.class,
                () -> SchemaDiscovery.discover(constructed, DiscoveryMode.COMPLETE));
    }

    @Test
    @DisplayName("a malformed line is refused, naming the line")
    void aMalformedLine() {
        MalformedDumpException failure =
                refused(MINIMAL.replace("num_threads = 0\n", "num_threads = 0\nnonsense\n"));
        assertTrue(failure.getMessage().startsWith("line 3 of the dump: "), failure.getMessage());
    }

    @Test
    @DisplayName("a name declared twice is refused, naming both lines")
    void aDuplicate() {
        MalformedDumpException failure =
                refused(MINIMAL.replace("num_threads = 0\n", "num_threads = 0\nnum_threads = 4\n"));
        assertEquals(
                "line 3 of the dump declares num_threads again; line 2 already did",
                failure.getMessage());
    }

    @Test
    @DisplayName("a dump whose first line is not the marker is refused")
    void noMarker() {
        assertTrue(
                refused(MINIMAL.substring(MINIMAL.indexOf('\n') + 1))
                        .getMessage()
                        .contains("line 1 of the dump is not"));
        assertTrue(refused("").getMessage().contains("line 1 of the dump is not"));
        assertTrue(refused("\n" + MINIMAL).getMessage().contains("line 1 of the dump is not"));
    }

    @Test
    @DisplayName("a marker this project cannot map is refused")
    void anUnmappableMarker() {
        MalformedDumpException failure =
                refused(MINIMAL.replace("2026.02 rev. 2 (6edec91)", "2026.02.2"));
        assertTrue(failure.getMessage().startsWith("line 1 of the dump: "), failure.getMessage());
    }

    @Test
    @DisplayName("a dump without enzyme rows is refused")
    void noEnzymeRows() {
        assertTrue(
                refused(MINIMAL.replace("0.  Cut_everywhere 0 - -\n", ""))
                        .getMessage()
                        .contains("has no [COMET_ENZYME_INFO] rows"));
    }
}
