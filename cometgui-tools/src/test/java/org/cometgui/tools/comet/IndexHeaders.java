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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.cometgui.domain.params.CometIndexDescription;
import org.cometgui.domain.params.CometIndexDescription.Enzyme;
import org.cometgui.domain.params.CometIndexDescription.VariableMod;
import org.cometgui.domain.run.IndexMode;

/**
 * The REAL index headers the two pinned Comet binaries wrote, committed as test resources under
 * {@code fixtures/comet-index/<release>/linux-x86-64/} and held to the {@code SHA256SUMS} beside
 * them, and their contents hand-typed from the bytes.
 *
 * <p>Each file is the header of an index built from the first 1000 records of the {@code D-006}
 * UniProt proteome (SHA-256 {@code 5005d961...}) by {@code comet -Pcomet.params -i -Dsubset.fasta}
 * (or {@code -j}), run in a directory holding {@code comet.params} -- that release's own {@code
 * comet -q} output with {@code database_name = subset.fasta}, {@code spectral_library_name} empty
 * and {@code num_threads = 4} -- and {@code subset.fasta}, a symbolic link to the subset, so that
 * the index lands there and its {@code InputDB:} line holds no machine path. The bytes are the file
 * from its start through the empty line that ends the header. {@link CometIndexRealBinaryTest}
 * repeats the capture with both binaries on every Linux build and requires these bytes.
 */
final class IndexHeaders {

    /** Comet 2026.03.0, which writes format 5. */
    static final String NEWER = "2026.03.0";

    /** Comet 2026.02.2, which writes format 4. */
    static final String OLDER = "2026.02.2";

    /** The capture's file name for each kind of index. */
    static String fileName(IndexMode mode) {
        return switch (mode) {
            case FRAGMENT_ION -> "fragment-ion.idx-header";
            case PEPTIDE -> "peptide.idx-header";
            case NONE -> throw new IllegalArgumentException("no index");
        };
    }

    /**
     * The capture of a fragment-ion index built with modifications Comet rewrites, merges, caps and
     * drops: the edits {@link #MODS_EDITS} make to the release's {@code -q} file, besides the three
     * every capture makes.
     */
    static final String MODS = "fragment-ion-mods.idx-header";

    /**
     * The edits of the modifications capture, in the order they are made. {@code variable_mod02} is
     * identical to slot 1 but for its residue, so Comet merges it into slot 1 ({@code MW}); slot
     * 3's count of 7 is capped at {@code max_variable_mods_in_peptide = 6} and then at 5, the
     * fragment-ion cap; slot 4, {@code n} at distance 0 from the protein N-terminus, becomes {@code
     * ^} in Comet 2026.03.0; and slot 6 is not held at all, because an index holds five slots.
     */
    static final List<String[]> MODS_EDITS =
            List.of(
                    new String[] {"decoy_search", "1"},
                    new String[] {"allowed_missed_cleavage", "1"},
                    new String[] {"variable_mod02", "15.9949 W 0 3 -1 0 0 0.0"},
                    new String[] {"variable_mod03", "79.966331 STY 0 7 -1 0 1 97.976896"},
                    new String[] {"variable_mod04", "42.010565 n 0 1 0 0 0 0.0"},
                    new String[] {"variable_mod06", "-17.026549 Q 0 1 0 2 0 0.0"},
                    new String[] {"max_variable_mods_in_peptide", "6"},
                    new String[] {"add_Nterm_peptide", "1.5"});

    private IndexHeaders() {}

    /**
     * One committed capture, held to its {@code SHA256SUMS} line.
     *
     * @param release the release that wrote it
     * @param mode the kind of index
     * @return its bytes
     */
    static byte[] bytes(String release, IndexMode mode) {
        return bytes(release, fileName(mode));
    }

    /**
     * One committed capture by its file name, held to its {@code SHA256SUMS} line.
     *
     * @param release the release that wrote it
     * @param name the capture's file name
     * @return its bytes
     */
    static byte[] bytes(String release, String name) {
        String directory = "/fixtures/comet-index/" + release + "/linux-x86-64/";
        byte[] bytes = resource(directory + name);
        String sums = new String(resource(directory + "SHA256SUMS"), StandardCharsets.US_ASCII);
        String expected =
                sums.lines()
                        .filter(line -> line.endsWith("  " + name))
                        .map(line -> line.substring(0, 64))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("SHA256SUMS names no " + name));
        assertEquals(expected, sha256(bytes), directory + name + " is not the capture");
        return bytes;
    }

    private static byte[] resource(String name) {
        try (InputStream in = IndexHeaders.class.getResourceAsStream(name)) {
            if (in == null) {
                throw new AssertionError("the test class path holds no " + name);
            }
            return in.readAllBytes();
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static BigDecimal d(String text) {
        return new BigDecimal(text);
    }

    /** The static modifications both captures record: 57.021464 on C, nothing else. */
    static List<BigDecimal> staticMods() {
        List<BigDecimal> mods = new ArrayList<>();
        for (int index = 0; index < CometIndexDescription.STATIC_MOD_COUNT; index++) {
            mods.add(index == 2 ? d("57.021464") : d("0.000000"));
        }
        return mods;
    }

    /**
     * The static modifications of the modifications capture: C, and 1.5 on the peptide N-terminus.
     */
    static List<BigDecimal> modsStaticMods() {
        List<BigDecimal> mods = staticMods();
        mods.set(26, d("1.500000"));
        return mods;
    }

    private static VariableMod modSlot(
            String residues, String mass, String loss, int count, boolean positioned, int need) {
        return new VariableMod(
                residues,
                d(mass),
                d(loss),
                d("0.000000"),
                count,
                positioned ? OptionalInt.of(-1) : OptionalInt.empty(),
                positioned ? OptionalInt.of(0) : OptionalInt.empty(),
                need);
    }

    /**
     * The five slots of a modifications capture.
     *
     * @param positioned whether the format records distance and terminus (format 5)
     * @param proteinN the residue token Comet holds for slot 4: {@code ^} or {@code n}
     * @return the slots
     */
    static List<VariableMod> modsSlots(boolean positioned, String proteinN) {
        return List.of(
                modSlot("MW", "15.994900", "0.000000", 3, positioned, 0),
                modSlot("-", "0.000000", "0.000000", 3, positioned, 0),
                modSlot("STY", "79.966331", "97.976896", 5, positioned, 1),
                modSlot(proteinN, "42.010565", "0.000000", 1, positioned, 0),
                modSlot("X", "0.000000", "0.000000", 3, positioned, 0));
    }

    /**
     * What a release's modifications capture says, typed from its bytes.
     *
     * @param release the release
     * @param file the path the description names
     * @return the description
     */
    static CometIndexDescription mods(String release, Path file) {
        boolean newer = NEWER.equals(release);
        return new CometIndexDescription(
                file,
                newer ? 5 : 4,
                newer
                        ? "Comet index database v5.  Comet version 2026.03 rev. 0 (fa08489)"
                        : "Comet index database v4.  Comet version 2026.02 rev. 2 (6edec91)",
                newer ? "2026.03 rev. 0 (fa08489)" : "2026.02 rev. 2 (6edec91)",
                IndexMode.FRAGMENT_ION,
                Optional.of("subset.fasta"),
                new CometIndexDescription.MassRange(d("600.000000"), d("5000.000000")),
                new CometIndexDescription.LengthRange(5, 50),
                1,
                1,
                1,
                newer ? Optional.of("DECOY_") : Optional.empty(),
                new Enzyme("Trypsin", 1, "KR", "P"),
                new Enzyme("Cut_everywhere", 0, "-", "-"),
                newer ? OptionalInt.of(2) : OptionalInt.empty(),
                newer ? OptionalInt.of(1) : OptionalInt.empty(),
                newer ? Optional.of(false) : Optional.empty(),
                81849,
                modsStaticMods(),
                modsSlots(newer, newer ? "^" : "n"),
                false,
                8,
                6);
    }

    /** The five slots of a format-5 capture: oxidised methionine, then four unused slots. */
    static List<VariableMod> formatFiveSlots() {
        List<VariableMod> slots = new ArrayList<>();
        slots.add(
                new VariableMod(
                        "M",
                        d("15.994900"),
                        d("0.000000"),
                        d("0.000000"),
                        3,
                        OptionalInt.of(-1),
                        OptionalInt.of(0),
                        0));
        for (int slot = 2; slot <= 5; slot++) {
            slots.add(
                    new VariableMod(
                            "X",
                            d("0.000000"),
                            d("0.000000"),
                            d("0.000000"),
                            3,
                            OptionalInt.of(-1),
                            OptionalInt.of(0),
                            0));
        }
        return slots;
    }

    /** The five slots of a format-4 capture: no terminal distance or terminus. */
    static List<VariableMod> formatFourSlots() {
        List<VariableMod> slots = new ArrayList<>();
        slots.add(
                new VariableMod(
                        "M",
                        d("15.994900"),
                        d("0.000000"),
                        d("0.000000"),
                        3,
                        OptionalInt.empty(),
                        OptionalInt.empty(),
                        0));
        for (int slot = 2; slot <= 5; slot++) {
            slots.add(
                    new VariableMod(
                            "X",
                            d("0.000000"),
                            d("0.000000"),
                            d("0.000000"),
                            3,
                            OptionalInt.empty(),
                            OptionalInt.empty(),
                            0));
        }
        return slots;
    }

    /**
     * What Comet 2026.03.0's capture says, typed from its bytes.
     *
     * @param file the path the description names
     * @param mode the kind of index
     * @return the description
     */
    static CometIndexDescription newer(Path file, IndexMode mode) {
        return new CometIndexDescription(
                file,
                5,
                "Comet index database v5.  Comet version 2026.03 rev. 0 (fa08489)",
                "2026.03 rev. 0 (fa08489)",
                mode,
                Optional.of("subset.fasta"),
                new CometIndexDescription.MassRange(d("600.000000"), d("5000.000000")),
                new CometIndexDescription.LengthRange(5, 50),
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
                staticMods(),
                formatFiveSlots(),
                false,
                0,
                5);
    }

    /**
     * What Comet 2026.02.2's capture says, typed from its bytes.
     *
     * @param file the path the description names
     * @param mode the kind of index
     * @return the description
     */
    static CometIndexDescription older(Path file, IndexMode mode) {
        return new CometIndexDescription(
                file,
                4,
                "Comet index database v4.  Comet version 2026.02 rev. 2 (6edec91)",
                "2026.02 rev. 2 (6edec91)",
                mode,
                Optional.of("subset.fasta"),
                new CometIndexDescription.MassRange(d("600.000000"), d("5000.000000")),
                new CometIndexDescription.LengthRange(5, 50),
                1,
                1,
                0,
                Optional.empty(),
                new Enzyme("Trypsin", 1, "KR", "P"),
                new Enzyme("Cut_everywhere", 0, "-", "-"),
                OptionalInt.empty(),
                OptionalInt.empty(),
                Optional.empty(),
                129327,
                staticMods(),
                formatFourSlots(),
                false,
                0,
                5);
    }

    /**
     * What a release's capture says.
     *
     * @param release the release
     * @param file the path the description names
     * @param mode the kind of index
     * @return the description
     */
    static CometIndexDescription expected(String release, Path file, IndexMode mode) {
        return NEWER.equals(release) ? newer(file, mode) : older(file, mode);
    }
}
