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

import java.net.URI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for {@link ArtefactLicence}.
 *
 * <p>The note is required and not decoration: PDV's upstream {@code LICENSE} says GPL-3.0 while its
 * {@code pom.xml} says Apache-2.0, and a record that carried only the winning identifier would hide
 * the disagreement the owner had to rule on.
 */
class ArtefactLicenceTest {

    private static final URI URL =
            URI.create("https://raw.githubusercontent.com/example/example/t/LICENSE");

    @Test
    @DisplayName("a licence keeps its identifier, its URL and its note")
    void keepsItsParts() {
        ArtefactLicence licence = ManifestFixtures.licence();

        assertAll(
                () -> assertEquals("Apache-2.0", licence.spdx()),
                () -> assertEquals(URL, licence.url()),
                () ->
                        assertEquals(
                                "upstream LICENSE at tag t is the Apache License 2.0",
                                licence.note()));
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @ValueSource(strings = {"", " ", "\t", "\n"})
    @DisplayName("a blank identifier and a blank note are each rejected, naming the field")
    void blankPartsAreRejected(String blank) {
        assertAll(
                () ->
                        assertEquals(
                                "spdx must not be blank",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () -> new ArtefactLicence(blank, URL, "a note"))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "licence note must not be blank",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () -> new ArtefactLicence("Apache-2.0", URL, blank))
                                        .getMessage()));
    }

    @Test
    @DisplayName(
            "a licence URL that is not https is rejected, like every other URL in the manifest")
    void aNonHttpsUrlIsRejected() {
        assertEquals(
                "licence url must be an absolute https URL with a host and no credentials, but"
                        + " was: \"http://example.org/LICENSE\"",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        new ArtefactLicence(
                                                "Apache-2.0",
                                                URI.create("http://example.org/LICENSE"),
                                                "a note"))
                        .getMessage());
    }
}
