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

package org.cometgui.domain.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.cometgui.domain.testing.Nulls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for {@link ArtefactKind}.
 *
 * <p>The constant set is asserted as a hand-typed literal, in declaration order, so that adding a
 * seventh kind fails this test and has to be argued for. That matters for one kind in particular:
 * {@code NSIS_PAYLOAD} was deleted from the product by {@code D-002} option C, an owner decision,
 * and "completing" this enumeration would reverse it.
 */
class ArtefactKindTest {

    @Test
    @DisplayName("exactly six kinds exist, and they are the six R-TOOL-01 enumerates")
    void theSixKindsArePinned() {
        List<String> names = new ArrayList<>();
        for (ArtefactKind kind : ArtefactKind.values()) {
            names.add(kind.name());
        }

        assertEquals(
                List.of("BARE_EXECUTABLE", "ZIP", "TAR_GZ", "JAR", "DEB_PAYLOAD", "PKG_PAYLOAD"),
                names);
        assertEquals(6, ArtefactKind.values().length);
    }

    @Test
    @DisplayName("there is no NSIS_PAYLOAD, and adding one fails this test")
    void nsisPayloadDoesNotExist() {
        for (ArtefactKind kind : ArtefactKind.values()) {
            assertFalse(
                    kind.name().contains("NSIS"),
                    "D-002 option C deleted NSIS payload extraction from the product; "
                            + kind.name()
                            + " reinstates it");
        }

        IllegalArgumentException rejected =
                assertThrows(
                        IllegalArgumentException.class, () -> ArtefactKind.fromId("NSIS_PAYLOAD"));
        assertTrue(
                rejected.getMessage().startsWith("no artefact kind has the id \"NSIS_PAYLOAD\""),
                rejected.getMessage());
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvSource({"BARE_EXECUTABLE", "ZIP", "TAR_GZ", "JAR", "DEB_PAYLOAD", "PKG_PAYLOAD"})
    @DisplayName("each kind's identifier is the token R-TOOL-01 uses, and resolves back")
    void identifiersArePinnedAndResolve(String id) {
        ArtefactKind kind = ArtefactKind.valueOf(id);

        assertEquals(id, kind.id());
        assertEquals(kind, ArtefactKind.fromId(id));
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @ValueSource(strings = {"zip", "Zip", " ZIP", "TARGZ", "TAR.GZ", "RPM", "MSI", ""})
    @DisplayName("an unknown identifier is rejected by name, with no trimming and no case folding")
    void unknownIdentifiersAreRejected(String unknown) {
        IllegalArgumentException rejected =
                assertThrows(IllegalArgumentException.class, () -> ArtefactKind.fromId(unknown));

        assertEquals(
                "no artefact kind has the id \""
                        + unknown
                        + "\"; expected one of [BARE_EXECUTABLE, ZIP, TAR_GZ, JAR, DEB_PAYLOAD,"
                        + " PKG_PAYLOAD]",
                rejected.getMessage());
    }

    @Test
    @DisplayName("the rejection message lists every identifier that is accepted")
    void theRejectionMessageDoesNotDrift() {
        IllegalArgumentException rejected =
                assertThrows(IllegalArgumentException.class, () -> ArtefactKind.fromId("nonsense"));

        for (ArtefactKind kind : ArtefactKind.values()) {
            assertTrue(
                    rejected.getMessage().contains(kind.id()),
                    "the rejection message does not mention " + kind.id());
        }
    }

    @Test
    @DisplayName("a null identifier is rejected as null, not as unknown")
    void nullIsRejectedByName() {
        NullPointerException rejected =
                assertThrows(
                        NullPointerException.class,
                        () -> ArtefactKind.fromId(Nulls.of(String.class)));

        assertEquals("id", rejected.getMessage());
    }
}
