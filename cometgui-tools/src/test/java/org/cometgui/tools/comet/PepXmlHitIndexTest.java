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

package org.cometgui.tools.comet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link PepXmlHitIndex} on the CometGUI-authored fixture ({@link PepXmlShapes}): the hits asked
 * for are found by scan, charge and <em>position</em>, the rest are reported missing, and an
 * ambiguous file is refused. {@link PepXmlSearchHitReaderRealFileTest} joins every Percolator PSM
 * of the real K562 runs through it.
 */
class PepXmlHitIndexTest {

    @TempDir private Path directory;

    private static final PepXmlHitKey FIRST_DECOY = new PepXmlHitKey(101, 2, 2);

    private static final PepXmlHitKey TIED = new PepXmlHitKey(102, 3, 3);

    private static final PepXmlHitKey OTHER_CHARGE = new PepXmlHitKey(102, 2, 1);

    private static final PepXmlHitKey PAST_LAST_HIT = new PepXmlHitKey(102, 3, 5);

    private static final PepXmlHitKey NO_HITS = new PepXmlHitKey(103, 2, 1);

    private static final PepXmlHitKey NO_QUERY = new PepXmlHitKey(999, 2, 1);

    private static final PepXmlHitKey WRONG_CHARGE = new PepXmlHitKey(101, 3, 1);

    @Test
    @DisplayName("the hits asked for, by scan, charge and position; the rest reported missing")
    void wanted() throws CometOutputException {
        Path file = PepXmlShapes.write(directory, PepXmlShapes.text());
        PepXmlHitIndex index =
                PepXmlHitIndex.of(
                        file,
                        Set.of(
                                FIRST_DECOY,
                                TIED,
                                OTHER_CHARGE,
                                PAST_LAST_HIT,
                                NO_HITS,
                                NO_QUERY,
                                WRONG_CHARGE));
        assertEquals(3, index.size());
        assertEquals(4, index.spectrumQueries());
        assertEquals(Set.of(PAST_LAST_HIT, NO_HITS, NO_QUERY, WRONG_CHARGE), index.missing());
        assertEquals(file, index.summary().file());
        assertEquals("/run/outputs/comet/shapes", index.summary().outputBase());

        PepXmlPsm decoy = index.find(FIRST_DECOY).orElseThrow();
        assertEquals("ACDEFGHIK", decoy.hit().peptide());
        assertEquals("shapes.00101.00101.2", decoy.query().spectrum());
        assertEquals(FIRST_DECOY, decoy.key());

        PepXmlPsm tied = index.find(TIED).orElseThrow();
        assertEquals(3, tied.hit().position());
        assertEquals(2, tied.hit().hitRank(), "position 3 carries hit_rank 2");
        assertEquals(
                List.of(2, 3),
                tied.hit().modificationInfo().orElseThrow().residues().stream()
                        .map(PepXmlResidueModification::position)
                        .toList());

        PepXmlPsm other = index.find(OTHER_CHARGE).orElseThrow();
        assertEquals("SCMEK", other.hit().peptide());
        assertEquals(new BigDecimal("1000.495000"), other.query().precursorNeutralMass().value());

        assertEquals(Optional.empty(), index.find(NO_QUERY));
        assertEquals(Optional.empty(), index.find(new PepXmlHitKey(101, 2, 1)), "not asked for");
    }

    @Test
    @DisplayName("two hits of one query share the query's attributes")
    void sharedQuery() throws CometOutputException {
        PepXmlHitIndex index =
                PepXmlHitIndex.of(
                        PepXmlShapes.write(directory, PepXmlShapes.text()),
                        Set.of(new PepXmlHitKey(102, 3, 2), TIED));
        assertSame(
                index.find(TIED).orElseThrow().query(),
                index.find(new PepXmlHitKey(102, 3, 2)).orElseThrow().query());
        assertEquals(Set.of(), index.missing());
    }

    @Test
    @DisplayName("nothing asked for: nothing kept, every query still read and checked")
    void nothingWanted() throws CometOutputException {
        PepXmlHitIndex index =
                PepXmlHitIndex.of(PepXmlShapes.write(directory, PepXmlShapes.text()), Set.of());
        assertEquals(0, index.size());
        assertEquals(4, index.spectrumQueries());
        assertEquals(Set.of(), index.missing());
    }

    @Test
    @DisplayName(
            "two queries with one scan and charge make a PSMId ambiguous: refused, naming both")
    void ambiguous() {
        Path file =
                PepXmlShapes.write(
                        directory,
                        PepXmlShapes.damaged(
                                "start_scan=\"103\" end_scan=\"103\""
                                        + " precursor_neutral_mass=\"800.400000\""
                                        + " assumed_charge=\"2\"",
                                "start_scan=\"101\" end_scan=\"103\""
                                        + " precursor_neutral_mass=\"800.400000\""
                                        + " assumed_charge=\"2\""));
        CometOutputException refused =
                assertThrows(CometOutputException.class, () -> PepXmlHitIndex.of(file, Set.of()));
        assertEquals(file, refused.file());
        assertEquals(
                "the pepXML file "
                        + file
                        + " holds two spectrum queries with start_scan 101 and assumed_charge 2,"
                        + " \"shapes.00101.00101.2\" and \"shapes.00103.00103.2\" (index 3), so a"
                        + " PSMId naming that scan and charge is ambiguous",
                refused.getMessage());
    }

    @Test
    @DisplayName("a file the reader refuses is refused by the index")
    void refusedFile() {
        Path file =
                PepXmlShapes.write(
                        directory, PepXmlShapes.damaged("peptide=\"VVVVR\"", "peptide=\"V V\""));
        CometOutputException refused =
                assertThrows(
                        CometOutputException.class, () -> PepXmlHitIndex.of(file, Set.of(TIED)));
        assertTrue(refused.getMessage().contains("peptide \"V V\""), refused.getMessage());
    }

    @Test
    @DisplayName("a key needs a non-negative scan and charge and a position of at least 1")
    void keys() {
        assertEquals(0, new PepXmlHitKey(0, 0, 1).startScan());
        assertThrows(IllegalArgumentException.class, () -> new PepXmlHitKey(-1, 2, 1));
        assertThrows(IllegalArgumentException.class, () -> new PepXmlHitKey(1, -1, 1));
        IllegalArgumentException zero =
                assertThrows(IllegalArgumentException.class, () -> new PepXmlHitKey(1, 2, 0));
        assertEquals(
                "a hit key needs a non-negative scan and charge and a position of at least 1:"
                        + " scan 1, charge 2, position 0",
                zero.getMessage());
    }
}
