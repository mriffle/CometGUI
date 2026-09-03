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

package org.cometgui.tools.percolator;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.tools.ToolInstallState;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolOrigin;
import org.cometgui.domain.tools.ToolVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The registration record's own invariants: it cannot describe a managed or uninstalled tool. */
class RegisteredLocalBinaryTest {

    private static final Path BINARY = Path.of("/opt/percolator/bin/percolator");
    private static final FileHashes HASHES =
            new FileHashes(
                    "0b77b68fd859639d7421f1c5e006ade5",
                    "1ba38acf09520cc89d5ed907ed0382c4d23876a7e20ec3e91cbbaa2ed431237c");

    private static ToolOffer offer(ToolOrigin origin, ToolInstallState state, Path path) {
        return new ToolOffer(
                ToolName.PERCOLATOR,
                ToolVersion.parse("3.07.1"),
                origin,
                state,
                List.of(),
                List.of(),
                Optional.empty(),
                Optional.ofNullable(path));
    }

    @Test
    @DisplayName("a local, installed offer whose path matches is accepted")
    void accepted() {
        RegisteredLocalBinary registered =
                new RegisteredLocalBinary(
                        offer(ToolOrigin.LOCAL, ToolInstallState.INSTALLED, BINARY),
                        HASHES,
                        BINARY);

        assertAll(
                () -> assertEquals(BINARY, registered.binary()),
                () -> assertEquals(HASHES, registered.checksums()),
                () -> assertEquals(ToolOrigin.LOCAL, registered.offer().origin()));
    }

    @Test
    @DisplayName("a managed offer is not a local registration, and the message says which it said")
    void aManagedOffer() {
        assertEquals(
                "a registered local binary must be recorded as LOCAL, but the offer says MANAGED",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        new RegisteredLocalBinary(
                                                offer(
                                                        ToolOrigin.MANAGED,
                                                        ToolInstallState.INSTALLED,
                                                        BINARY),
                                                HASHES,
                                                BINARY))
                        .getMessage());
    }

    @Test
    @DisplayName("an offer that is not installed is not a registration")
    void notInstalled() {
        assertEquals(
                "a registered local binary must be recorded as INSTALLED, but the offer says"
                        + " NOT_INSTALLED",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        new RegisteredLocalBinary(
                                                offer(
                                                        ToolOrigin.LOCAL,
                                                        ToolInstallState.NOT_INSTALLED,
                                                        BINARY),
                                                HASHES,
                                                BINARY))
                        .getMessage());
    }

    @Test
    @DisplayName("the checksums must be of the file the offer names, so the paths must agree")
    void aDifferentPath() {
        Path other = Path.of("/usr/local/bin/percolator");

        assertEquals(
                "the registered path " + other + " is not the one the offer names: " + BINARY,
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        new RegisteredLocalBinary(
                                                offer(
                                                        ToolOrigin.LOCAL,
                                                        ToolInstallState.INSTALLED,
                                                        BINARY),
                                                HASHES,
                                                other))
                        .getMessage());
    }

    @Test
    @DisplayName("every component is required")
    void everyComponentIsRequired() {
        ToolOffer good = offer(ToolOrigin.LOCAL, ToolInstallState.INSTALLED, BINARY);

        assertAll(
                () ->
                        assertEquals(
                                "offer",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new RegisteredLocalBinary(
                                                                null, HASHES, BINARY))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "checksums",
                                assertThrows(
                                                NullPointerException.class,
                                                () -> new RegisteredLocalBinary(good, null, BINARY))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "binary",
                                assertThrows(
                                                NullPointerException.class,
                                                () -> new RegisteredLocalBinary(good, HASHES, null))
                                        .getMessage()));
    }
}
