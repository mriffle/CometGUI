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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link PdvMzTabSource} and {@link PdvMzTabRequest} on the bases a {@code PSMId} may start with: a
 * source may name its base in more than one form (the bare {@code -N} base, and the absolute path
 * an earlier run's identifiers start with), and no base, in any form, may name two sources.
 */
class PdvMzTabSourceTest {

    private static final Path SPECTRA = Path.of("data", "a.mgf");

    private static final Path PEPXML = Path.of("run", "outputs", "comet", "a.pep.xml");

    private static PdvMzTabSource source(List<String> bases) {
        return new PdvMzTabSource(bases, SPECTRA, SpectrumFileFormat.MGF, PEPXML, Optional.empty());
    }

    @Test
    @DisplayName("a source keeps its bases in order, and a single base is a list of one")
    void bases() {
        assertEquals(
                List.of("a", "/run/outputs/comet/a"),
                source(List.of("a", "/run/outputs/comet/a")).psmIdBases());
        assertEquals(
                List.of("a"),
                new PdvMzTabSource("a", SPECTRA, SpectrumFileFormat.MGF, PEPXML, Optional.empty())
                        .psmIdBases());
    }

    @Test
    @DisplayName("the bases are copied in and cannot be changed through the list given or returned")
    void copied() {
        List<String> given = new ArrayList<>(List.of("a", "b"));
        PdvMzTabSource source = source(given);
        given.set(0, "changed");
        assertEquals(List.of("a", "b"), source.psmIdBases());
        assertThrows(UnsupportedOperationException.class, () -> source.psmIdBases().add("c"));
    }

    @Test
    @DisplayName("no base, an empty base, a base twice and a null base are refused in words")
    void refused() {
        assertEquals(
                "a source needs at least one PSMId base",
                assertThrows(IllegalArgumentException.class, () -> source(List.of())).getMessage());
        assertEquals(
                "a source's PSMId base must not be empty",
                assertThrows(IllegalArgumentException.class, () -> source(List.of("a", "")))
                        .getMessage());
        assertEquals(
                "a source's PSMId base must not be empty",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        new PdvMzTabSource(
                                                "",
                                                SPECTRA,
                                                SpectrumFileFormat.MGF,
                                                PEPXML,
                                                Optional.empty()))
                        .getMessage());
        assertEquals(
                "a source names the PSMId base twice: [a, b, a]",
                assertThrows(IllegalArgumentException.class, () -> source(List.of("a", "b", "a")))
                        .getMessage());
        assertThrows(NullPointerException.class, () -> source(Arrays.asList("a", null)));
    }

    @Test
    @DisplayName(
            "two sources may not share a base in any form: the second source's later base against"
                    + " the first's is refused, naming it")
    void requestRefusesASharedBase() {
        PdvMzTabSource first = source(List.of("a", "/run/outputs/comet/a"));
        PdvMzTabSource second = source(List.of("b", "/run/outputs/comet/a"));
        assertEquals(
                "two sources share the PSMId base \"/run/outputs/comet/a\"",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        new PdvMzTabRequest(
                                                Path.of("run", "psms.tsv"),
                                                Optional.empty(),
                                                List.of(first, second),
                                                Path.of("run", "pdv", "mztab")))
                        .getMessage());
        PdvMzTabSource distinct = source(List.of("b", "/run/outputs/comet/b"));
        assertEquals(
                List.of(first, distinct),
                new PdvMzTabRequest(
                                Path.of("run", "psms.tsv"),
                                Optional.empty(),
                                List.of(first, distinct),
                                Path.of("run", "pdv", "mztab"))
                        .sources());
    }
}
