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

package org.cometgui.domain.params;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Consumer;
import org.cometgui.domain.params.CometIndexDescription.Enzyme;
import org.cometgui.domain.params.CometIndexDescription.LengthRange;
import org.cometgui.domain.params.CometIndexDescription.MassRange;
import org.cometgui.domain.params.CometIndexDescription.VariableMod;
import org.cometgui.domain.run.IndexMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The pre-run fact values: {@link FastaDecoyCensus}, {@link CometIndexDescription} and {@link
 * PreRunFacts}, each invariant shown refused with its message and each derived value proved.
 */
class PreRunFactValuesTest {

    private static final Path FASTA = Path.of("db", "subset.fasta");

    private static final Path INDEX = Path.of("cache", "subset.fasta.idx");

    private static String refused(Runnable construction) {
        return assertThrows(IllegalArgumentException.class, construction::run).getMessage();
    }

    @Test
    @DisplayName("a census with decoys names its first one and counts its targets")
    void census() {
        FastaDecoyCensus census =
                new FastaDecoyCensus(FASTA, "DECOY_", 2000, 1000, Optional.of("DECOY_sp|P1|X"));
        assertTrue(census.hasDecoys());
        assertEquals(1000, census.targetRecords());
        FastaDecoyCensus none = new FastaDecoyCensus(FASTA, "DECOY_", 1000, 0, Optional.empty());
        assertFalse(none.hasDecoys());
        assertEquals(1000, none.targetRecords());
        FastaDecoyCensus all = new FastaDecoyCensus(FASTA, "D", 1, 1, Optional.of("D"));
        assertTrue(all.hasDecoys());
        assertEquals(0, all.targetRecords());
    }

    @Test
    @DisplayName("a census refuses what cannot be one")
    void censusRefusals() {
        assertEquals(
                "a decoy prefix is one non-empty token, not \"\"",
                refused(() -> new FastaDecoyCensus(FASTA, "", 1, 0, Optional.empty())));
        assertEquals(
                "a decoy prefix is one non-empty token, not \"DE COY\"",
                refused(() -> new FastaDecoyCensus(FASTA, "DE COY", 1, 0, Optional.empty())));
        assertEquals(
                "a census counts at least one record, but " + FASTA + " has 0",
                refused(() -> new FastaDecoyCensus(FASTA, "DECOY_", 0, 0, Optional.empty())));
        assertEquals(
                "-1 decoy records cannot be among 5 records",
                refused(() -> new FastaDecoyCensus(FASTA, "DECOY_", 5, -1, Optional.empty())));
        assertEquals(
                "6 decoy records cannot be among 5 records",
                refused(() -> new FastaDecoyCensus(FASTA, "DECOY_", 5, 6, Optional.of("DECOY_a"))));
        assertEquals(
                "the first decoy accession is named exactly when there are decoys: 2 decoys,"
                        + " first Optional.empty",
                refused(() -> new FastaDecoyCensus(FASTA, "DECOY_", 5, 2, Optional.empty())));
        assertEquals(
                "the first decoy accession is named exactly when there are decoys: 0 decoys,"
                        + " first Optional[DECOY_a]",
                refused(() -> new FastaDecoyCensus(FASTA, "DECOY_", 5, 0, Optional.of("DECOY_a"))));
        assertEquals(
                "the first decoy \"REV_a\" does not begin with the prefix DECOY_",
                refused(() -> new FastaDecoyCensus(FASTA, "DECOY_", 5, 1, Optional.of("REV_a"))));
        assertThrows(
                NullPointerException.class,
                () -> new FastaDecoyCensus(null, "DECOY_", 1, 0, Optional.empty()));
        assertThrows(
                NullPointerException.class,
                () -> new FastaDecoyCensus(FASTA, null, 1, 0, Optional.empty()));
        assertThrows(
                NullPointerException.class,
                () -> new FastaDecoyCensus(FASTA, "DECOY_", 1, 0, null));
    }

    private static BigDecimal d(String text) {
        return new BigDecimal(text);
    }

    private static VariableMod slot(String residues, String mass) {
        return new VariableMod(
                residues, d(mass), d("0"), d("0"), 3, OptionalInt.of(-1), OptionalInt.of(0), 0);
    }

    private static List<BigDecimal> statics() {
        return new ArrayList<>(Collections.nCopies(CometIndexDescription.STATIC_MOD_COUNT, d("0")));
    }

    private static final class Parts {
        private int format = 5;
        private String firstLine =
                "Comet index database v5.  Comet version 2026.03 rev. 0 (fa08489)";
        private String comet = "2026.03 rev. 0 (fa08489)";
        private IndexMode type = IndexMode.PEPTIDE;
        private List<BigDecimal> statics = statics();
        private List<VariableMod> slots = List.of(slot("M", "15.9949"), slot("X", "0"));

        CometIndexDescription build() {
            return new CometIndexDescription(
                    INDEX,
                    format,
                    firstLine,
                    comet,
                    type,
                    Optional.of("subset.fasta"),
                    new MassRange(d("600.0"), d("5000.0")),
                    new LengthRange(5, 50),
                    1,
                    1,
                    0,
                    Optional.of("DECOY_"),
                    new Enzyme("Trypsin", 1, "KR", "P"),
                    new Enzyme("Cut_everywhere", 0, "-", "-"),
                    OptionalInt.of(2),
                    OptionalInt.of(2),
                    Optional.of(false),
                    129327,
                    statics,
                    slots,
                    false,
                    0,
                    5);
        }
    }

    private static CometIndexDescription with(Consumer<Parts> change) {
        Parts parts = new Parts();
        change.accept(parts);
        return parts.build();
    }

    @Test
    @DisplayName("a description keeps what it is given, and its lists cannot be changed")
    void description() {
        List<BigDecimal> statics = statics();
        statics.set(2, d("57.021464"));
        List<VariableMod> slots = new ArrayList<>(List.of(slot("M", "15.9949")));
        CometIndexDescription description =
                with(
                        parts -> {
                            parts.statics = statics;
                            parts.slots = slots;
                        });
        statics.set(2, d("1"));
        slots.clear();
        assertEquals(d("57.021464"), description.staticMods().get(2));
        assertEquals(1, description.variableMods().size());
        assertThrows(UnsupportedOperationException.class, () -> description.staticMods().clear());
        assertThrows(UnsupportedOperationException.class, () -> description.variableMods().clear());
        assertEquals(5, description.formatVersion());
        assertEquals(IndexMode.PEPTIDE, description.type());
    }

    @Test
    @DisplayName("a description refuses what cannot be one")
    void descriptionRefusals() {
        assertEquals(
                "an index format number is 1 or more, not 0",
                refused(() -> with(parts -> parts.format = 0)));
        assertEquals(1, with(parts -> parts.format = 1).formatVersion());
        assertEquals(
                "an index names its format and the Comet that wrote it: \" \", \"2026.03 rev. 0"
                        + " (fa08489)\"",
                refused(() -> with(parts -> parts.firstLine = " ")));
        assertEquals(
                "an index names its format and the Comet that wrote it: \"Comet index database"
                        + " v5.  Comet version 2026.03 rev. 0 (fa08489)\", \"\"",
                refused(() -> with(parts -> parts.comet = "")));
        assertEquals(
                "an index is a fragment-ion or a peptide index, not NONE",
                refused(() -> with(parts -> parts.type = IndexMode.NONE)));
        assertEquals(
                IndexMode.FRAGMENT_ION, with(parts -> parts.type = IndexMode.FRAGMENT_ION).type());
        assertEquals(
                "an index records 30 static modifications, not 29",
                refused(() -> with(parts -> parts.statics = parts.statics.subList(0, 29))));
        assertEquals(
                "an index records its variable-modification slots",
                refused(() -> with(parts -> parts.slots = List.of())));
        assertThrows(NullPointerException.class, () -> with(parts -> parts.firstLine = null));
    }

    @Test
    @DisplayName("enzymes, slots and ranges refuse what cannot be one, and describe themselves")
    void parts() {
        assertEquals("Trypsin [1 KR P]", new Enzyme("Trypsin", 1, "KR", "P").text());
        assertEquals(
                "an enzyme has a name and two residue sets, each written; got \"\" [1 KR P]",
                refused(() -> new Enzyme("", 1, "KR", "P")));
        assertEquals(
                "an enzyme has a name and two residue sets, each written; got \"Trypsin\" [1  P]",
                refused(() -> new Enzyme("Trypsin", 1, "", "P")));
        assertEquals(
                "an enzyme has a name and two residue sets, each written; got \"Trypsin\" [1 KR ]",
                refused(() -> new Enzyme("Trypsin", 1, "KR", "")));
        assertEquals(
                "a variable modification names its residues",
                refused(
                        () ->
                                new VariableMod(
                                        "",
                                        d("1"),
                                        d("0"),
                                        d("0"),
                                        3,
                                        OptionalInt.empty(),
                                        OptionalInt.empty(),
                                        0)));
        assertEquals(
                "a slot records both its terminal distance and its terminus, or neither",
                refused(
                        () ->
                                new VariableMod(
                                        "M",
                                        d("1"),
                                        d("0"),
                                        d("0"),
                                        3,
                                        OptionalInt.of(-1),
                                        OptionalInt.empty(),
                                        0)));
        assertEquals(
                "a slot records both its terminal distance and its terminus, or neither",
                refused(
                        () ->
                                new VariableMod(
                                        "M",
                                        d("1"),
                                        d("0"),
                                        d("0"),
                                        3,
                                        OptionalInt.empty(),
                                        OptionalInt.of(0),
                                        0)));
        assertTrue(slot("M", "15.9949").isActive());
        assertTrue(slot("M", "-0.984").isActive());
        assertFalse(slot("X", "0.000000").isActive());
        assertThrows(NullPointerException.class, () -> new MassRange(null, d("1")));
        assertThrows(NullPointerException.class, () -> new MassRange(d("1"), null));
    }

    @Test
    @DisplayName("each static modification's site is named in the header's order")
    void staticSites() {
        assertEquals("A", CometIndexDescription.staticSite(0));
        assertEquals("C", CometIndexDescription.staticSite(2));
        assertEquals("Z", CometIndexDescription.staticSite(25));
        assertEquals("peptide N-terminus", CometIndexDescription.staticSite(26));
        assertEquals("peptide C-terminus", CometIndexDescription.staticSite(27));
        assertEquals("protein N-terminus", CometIndexDescription.staticSite(28));
        assertEquals("protein C-terminus", CometIndexDescription.staticSite(29));
        assertThrows(IndexOutOfBoundsException.class, () -> CometIndexDescription.staticSite(30));
        assertEquals(
                CometIndexDescription.STATIC_MOD_COUNT,
                CometIndexDescription.STATIC_RESIDUES.length()
                        + CometIndexDescription.STATIC_TERMINI.size());
    }

    @Test
    @DisplayName("pre-run facts carry a census, a description, both or neither")
    void facts() {
        FastaDecoyCensus census = new FastaDecoyCensus(FASTA, "DECOY_", 1, 0, Optional.empty());
        CometIndexDescription index = new Parts().build();
        PreRunFacts none = PreRunFacts.none();
        assertEquals(Optional.empty(), none.census());
        assertEquals(Optional.empty(), none.index());
        PreRunFacts both = none.withCensus(census).withIndex(index);
        assertEquals(Optional.of(census), both.census());
        assertEquals(Optional.of(index), both.index());
        assertEquals(Optional.empty(), none.withIndex(index).census());
        assertEquals(Optional.empty(), none.withCensus(census).index());
        assertThrows(NullPointerException.class, () -> new PreRunFacts(null, Optional.empty()));
        assertThrows(NullPointerException.class, () -> new PreRunFacts(Optional.empty(), null));
    }
}
