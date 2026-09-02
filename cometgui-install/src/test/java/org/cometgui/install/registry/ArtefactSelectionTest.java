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

package org.cometgui.install.registry;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.cometgui.domain.tools.ArtefactExecutability;
import org.cometgui.domain.tools.HostArchitecture;
import org.cometgui.domain.tools.HostOperatingSystem;
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.install.testing.Nulls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests for {@link ArtefactSelection}. */
class ArtefactSelectionTest {

    private static final HostPlatform MACOS_X86_64 =
            new HostPlatform(HostOperatingSystem.MACOS, HostArchitecture.X86_64);

    private static ArtefactRecord artefact() {
        return ManifestFixtures.namedMember(ToolName.PERCOLATOR, "3.07.1", MACOS_X86_64);
    }

    @Test
    @DisplayName("a native selection is not translated, and a Rosetta 2 one is")
    void translationIsVisible() {
        assertAll(
                () ->
                        assertFalse(
                                new ArtefactSelection(artefact(), ArtefactExecutability.NATIVE)
                                        .isTranslated()),
                () ->
                        assertTrue(
                                new ArtefactSelection(
                                                artefact(),
                                                ArtefactExecutability.TRANSLATED_ROSETTA_2)
                                        .isTranslated()));
    }

    @Test
    @DisplayName(
            "a selection the host cannot run is refused, because R-PERC-01 forbids offering it")
    void anIncompatibleSelectionIsRefused() {
        assertEquals(
                "a selection is an artefact the host can run, so INCOMPATIBLE cannot be one:"
                        + " percolator 3.07.1 macos-x86-64",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        new ArtefactSelection(
                                                artefact(), ArtefactExecutability.INCOMPATIBLE))
                        .getMessage());
    }

    @Test
    @DisplayName("a null part is rejected by name")
    void nullPartsAreRejectedByName() {
        assertAll(
                () ->
                        assertEquals(
                                "artefact",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new ArtefactSelection(
                                                                Nulls.of(ArtefactRecord.class),
                                                                ArtefactExecutability.NATIVE))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "executability",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new ArtefactSelection(
                                                                artefact(),
                                                                Nulls.of(
                                                                        ArtefactExecutability
                                                                                .class)))
                                        .getMessage()));
    }
}
