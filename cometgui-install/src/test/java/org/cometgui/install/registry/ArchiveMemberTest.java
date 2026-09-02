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
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.cometgui.domain.ports.FileHashes;
import org.cometgui.install.testing.Nulls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for {@link ArchiveMember}, and for the asymmetry at the heart of it.
 *
 * <p>The member's own name is upstream's and may be anything, including a name that escapes the
 * archive; the install path is this project's and may not. Both halves are asserted here, because
 * getting either one backwards breaks something real: refuse the escaping name and Percolator
 * 3.06.5 becomes uninstallable on macOS, accept an escaping install path and a manifest can write
 * anywhere on the machine.
 */
class ArchiveMemberTest {

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(
            strings = {
                "percolator",
                "percolator.exe",
                "../my_build/percolator-noxml/src/percolator",
                "Users/runner/work/percolator/percolator/build/percolator-noxml/src/percolator",
                "./usr/share/xml/percolator/xml-pout-1-5/percolator_out.xsd",
                "C:\\build\\percolator.exe"
            })
    @DisplayName("a member name upstream chose is kept exactly, however it escapes")
    void theMemberNameIsKeptExactly(String name) {
        ArchiveMember member =
                new ArchiveMember(name, 2538632, ManifestFixtures.memberHashes(), "bin/percolator");

        assertEquals(name, member.path());
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"../percolator", "/usr/bin/percolator", "bin\\percolator", "bin/"})
    @DisplayName("an install path that escapes the install directory is rejected")
    void anEscapingInstallPathIsRejected(String installedPath) {
        assertEquals(
                "member installedPath must be a relative path inside the install directory, with"
                        + " no empty, \".\" or \"..\" segment and no backslash, but was: \""
                        + installedPath
                        + "\"",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        new ArchiveMember(
                                                "percolator",
                                                2538632,
                                                ManifestFixtures.memberHashes(),
                                                installedPath))
                        .getMessage());
    }

    @Test
    @DisplayName("a blank name, a non-positive size and a null digest pair are each rejected")
    void theOtherPartsAreValidated() {
        FileHashes hashes = ManifestFixtures.memberHashes();
        assertAll(
                () ->
                        assertEquals(
                                "member path must not be blank",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () ->
                                                        new ArchiveMember(
                                                                "  ", 1, hashes, "bin/percolator"))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "member sizeBytes must be a positive number of bytes, but was: 0",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () ->
                                                        new ArchiveMember(
                                                                "percolator",
                                                                0,
                                                                hashes,
                                                                "bin/percolator"))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "member hashes",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new ArchiveMember(
                                                                "percolator",
                                                                1,
                                                                Nulls.of(FileHashes.class),
                                                                "bin/percolator"))
                                        .getMessage()));
    }

    @Test
    @DisplayName("two members with the same parts are equal")
    void valueSemantics() {
        assertEquals(ManifestFixtures.member(), ManifestFixtures.member());
    }
}
