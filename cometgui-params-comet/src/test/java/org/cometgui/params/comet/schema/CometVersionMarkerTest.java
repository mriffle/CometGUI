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
import java.util.Optional;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.fixtures.CometFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The marker Comet writes, mapped to the version the manifest uses. */
class CometVersionMarkerTest {

    @Test
    @DisplayName("the real -q fixture's first line is Comet 2026.02.2, build 6edec91")
    void theRealMarkerMapsToTheManifestVersion() throws IOException {
        for (CometFixtures.Mode mode : CometFixtures.Mode.values()) {
            String first =
                    CometFixtures.lines(
                                    CometFixtures.COMET_2026_02_2, CometFixtures.LINUX_X86_64, mode)
                            .get(0);
            CometVersionMarker marker = CometVersionMarker.parseLine(first);
            assertEquals("2026.02", marker.release());
            assertEquals(2, marker.revision());
            assertEquals(Optional.of("6edec91"), marker.build());
            assertEquals(ToolVersion.parse(CometFixtures.COMET_2026_02_2), marker.toolVersion());
            assertEquals("2026.02.2", marker.toolVersion().text());
            assertEquals("2026.02 rev. 2 (6edec91)", marker.text());
            assertEquals(first, CometVersionMarker.LINE_PREFIX + marker.text());
        }
    }

    @Test
    @DisplayName("the real 2026.03.0 fixtures' first line is Comet 2026.03.0, build fa08489")
    void theReal202603MarkerMaps() throws IOException {
        for (CometFixtures.Mode mode : CometFixtures.Mode.values()) {
            String first =
                    CometFixtures.lines(
                                    CometFixtures.COMET_2026_03_0, CometFixtures.LINUX_X86_64, mode)
                            .get(0);
            CometVersionMarker marker = CometVersionMarker.parseLine(first);
            assertEquals("2026.03", marker.release());
            assertEquals(0, marker.revision());
            assertEquals(Optional.of("fa08489"), marker.build());
            assertEquals(ToolVersion.parse("2026.03.0"), marker.toolVersion());
            assertEquals("2026.03 rev. 0 (fa08489)", marker.text());
        }
        assertEquals(
                "2026.03 rev. 0",
                CometVersionMarker.releaseTextFor(ToolVersion.parse("2026.03.0")));
    }

    @Test
    @DisplayName("the manifest's version maps back to Comet's spelling")
    void theManifestVersionMapsBack() {
        assertEquals(
                "2026.02 rev. 2",
                CometVersionMarker.releaseTextFor(ToolVersion.parse("2026.02.2")));
        assertEquals(
                "2024.01 rev. 0",
                CometVersionMarker.releaseTextFor(ToolVersion.parse("2024.01.0")));
        assertEquals(
                "2025.03 rev. 12",
                CometVersionMarker.releaseTextFor(ToolVersion.parse("2025.03.12")));
        IllegalArgumentException twoParts =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> CometVersionMarker.releaseTextFor(ToolVersion.parse("3.09")));
        assertTrue(twoParts.getMessage().contains("\"3.09\" is not YYYY.NN.R"));
        assertThrows(
                IllegalArgumentException.class,
                () -> CometVersionMarker.releaseTextFor(ToolVersion.parse("2026.2.2")));
    }

    @Test
    @DisplayName("a marker without a build hash, and revision 0, map too")
    void withoutAHash() {
        CometVersionMarker marker = CometVersionMarker.parse("2024.01 rev. 0");
        assertEquals(Optional.empty(), marker.build());
        assertEquals(ToolVersion.parse("2024.01.0"), marker.toolVersion());
        assertEquals("2024.01 rev. 0", marker.text());
        assertEquals("2024.01.0", marker.toolVersion().text());
        assertEquals(marker, CometVersionMarker.parse("  2024.01 rev. 0\r"));
    }

    @Test
    @DisplayName("anything else is refused with what was rejected quoted")
    void malformedMarkersAreRefused() {
        for (String text :
                new String[] {
                    "2026.02.2",
                    "2026.02 rev 2",
                    "2026.2 rev. 2",
                    "2026.02 rev. 2 6edec91",
                    "2026.02 rev. 2 (XYZ)",
                    "2026.02 rev. 2 (6edec91) extra",
                    ""
                }) {
            IllegalArgumentException failure =
                    assertThrows(
                            IllegalArgumentException.class, () -> CometVersionMarker.parse(text));
            assertTrue(failure.getMessage().contains("is not a Comet version marker"), text);
        }
        IllegalArgumentException noPrefix =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> CometVersionMarker.parseLine("#comet_version 2026.02 rev. 2"));
        assertTrue(noPrefix.getMessage().contains("starts with \"# comet_version \""));
    }
}
