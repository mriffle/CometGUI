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

package org.cometgui.workflow.steps;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.workflow.testing.Nulls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The probed capabilities a {@link CometSelection} carries into Comet's tool record. */
class CometSelectionCapabilitiesTest {

    private static final Path COMET =
            Path.of("cometgui-tools", "comet", "bin", "comet").toAbsolutePath();

    private static final String SHA256 =
            "ad93b4cf60c2ed7afc2f41b8a3a05567c3938d999f17deb7b3676bc1fa91e7ed";

    private static CometSelection with(Set<ToolCapability> capabilities) {
        return new CometSelection(
                COMET,
                ToolVersion.parse("2026.03.0"),
                SHA256,
                true,
                Optional.empty(),
                List.of(),
                List.of(),
                capabilities);
    }

    @Test
    @DisplayName("kept, copied, and unmodifiable")
    void kept() {
        Set<ToolCapability> given = EnumSet.of(ToolCapability.PIN_OUTPUT);
        CometSelection selection = with(given);
        given.add(ToolCapability.PEPXML_OUTPUT);
        assertAll(
                () -> assertEquals(Set.of(ToolCapability.PIN_OUTPUT), selection.capabilities()),
                () ->
                        assertThrows(
                                UnsupportedOperationException.class,
                                () -> selection.capabilities().add(ToolCapability.PEPXML_OUTPUT)));
    }

    @Test
    @DisplayName("the shorter constructors record no probed capability")
    void noneByDefault() {
        assertAll(
                () ->
                        assertEquals(
                                Set.of(),
                                new CometSelection(
                                                COMET,
                                                ToolVersion.parse("2026.03.0"),
                                                SHA256,
                                                true,
                                                Optional.empty())
                                        .capabilities()),
                () ->
                        assertEquals(
                                Set.of(),
                                new CometSelection(
                                                COMET,
                                                ToolVersion.parse("2026.03.0"),
                                                SHA256,
                                                true,
                                                Optional.empty(),
                                                List.of(),
                                                List.of())
                                        .capabilities()));
    }

    @Test
    @DisplayName("a capability of another tool is refused")
    void onlyComets() {
        IllegalArgumentException refused =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> with(EnumSet.of(ToolCapability.XML_OUTPUT)));
        assertEquals(
                "XML_OUTPUT is a capability of percolator and cannot be declared for comet",
                refused.getMessage());
    }

    @Test
    @DisplayName("a null set is refused naming the component")
    void nullSet() {
        Set<ToolCapability> absent = Nulls.of(Set.class);
        NullPointerException refused = assertThrows(NullPointerException.class, () -> with(absent));
        assertEquals("capabilities", refused.getMessage());
    }

    @Test
    @DisplayName("the release tag: kept when given, absent by default, never blank or null")
    void releaseTag() {
        CometSelection tagged =
                new CometSelection(
                        COMET,
                        ToolVersion.parse("2026.03.0"),
                        SHA256,
                        true,
                        Optional.empty(),
                        List.of(),
                        List.of(),
                        Set.of(),
                        Optional.of("v2026.03.0"));
        IllegalArgumentException blank =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                new CometSelection(
                                        COMET,
                                        ToolVersion.parse("2026.03.0"),
                                        SHA256,
                                        true,
                                        Optional.empty(),
                                        List.of(),
                                        List.of(),
                                        Set.of(),
                                        Optional.of(" ")));
        Optional<String> absent = Nulls.of(Optional.class);
        NullPointerException missing =
                assertThrows(
                        NullPointerException.class,
                        () ->
                                new CometSelection(
                                        COMET,
                                        ToolVersion.parse("2026.03.0"),
                                        SHA256,
                                        true,
                                        Optional.empty(),
                                        List.of(),
                                        List.of(),
                                        Set.of(),
                                        absent));
        assertAll(
                () -> assertEquals(Optional.of("v2026.03.0"), tagged.releaseTag()),
                () -> assertEquals(Optional.empty(), with(Set.of()).releaseTag()),
                () ->
                        assertEquals(
                                "a release tag is not blank when present, but was: \" \"",
                                blank.getMessage()),
                () -> assertEquals("releaseTag", missing.getMessage()));
    }
}
