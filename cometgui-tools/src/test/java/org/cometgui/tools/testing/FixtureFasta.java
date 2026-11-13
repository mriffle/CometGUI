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

package org.cometgui.tools.testing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The FASTA this module's real-binary tests search: the committed fixture FASTA ({@code D-006},
 * {@code D-018}; {@code docs/developer/test_fixtures.rst}) and the cut of it they are given.
 *
 * <p><strong>The committed file</strong> is {@value #FASTA}, read from the checkout. Its SHA-256,
 * size and record count are not typed here: they are read from the header of the accession list
 * that pins it, {@value #ACCESSIONS} ({@code subset-sha256}, {@code subset-bytes}, {@code
 * subset-records}), the lines {@code scripts/fixtures/fixture_fasta.py} writes and reads, and the
 * file is held to all three.
 *
 * <p><strong>The cut</strong> is the first {@value #RECORDS} records of the committed file, in the
 * file's order and byte for byte, whose accession the list marks {@value #KEPT_AS}. Which records
 * the tests search was decided by measured test time (Phase 16, unit 5a; the measurements are in
 * {@code docs/developer/test_fixtures.rst}). Over all 3,698 records Comet takes about 2.5 s a
 * spectrum file; over the cut about 1.2 s, where the first 1,000 records of the whole proteome,
 * which these tests searched before, took 1.3 s. A cut of the file's first records, whatever they
 * are marked, was measured from 100 to 3,000 records and refused: under 450 a search this module
 * holds to more than 3,000 PSMs gives fewer, and from 300 to 1,250 Percolator 3.07.1 takes anything
 * from 1 to 22 s on the PIN.
 *
 * <p><strong>What the cut is not.</strong> It holds only proteins these spectra identify, so a
 * search of it is the over-filtered search the fixture's acceptance check refuses. The tests behind
 * it prove files, counts and records, never a rate of identification; the whole committed file is
 * what the version matrix and the acceptance check search.
 *
 * <p>The cut's SHA-256 is pinned once in this module, in {@link #CUT_SHA256}; it was computed from
 * the committed file and list by a separate implementation of the same rule, not copied from a
 * failing run. Test classes are not published, so {@code cometgui-workflow} and {@code
 * cometgui-app} each hold the same few lines (design decision P8-1).
 */
public final class FixtureFasta {

    /** The committed fixture FASTA, relative to the repository root. */
    public static final String FASTA =
            "fixtures/human-k562-subset/uniprot-human-2026_02-k562-subset.fasta";

    /** The accession list whose header pins the FASTA, relative to the repository root. */
    public static final String ACCESSIONS = "fixtures/human-k562-subset/accessions.tsv";

    /** What the accession list calls the records the cut is taken from. */
    public static final String KEPT_AS = "identified";

    /** How many records so marked, from the first, the real-binary tests search. */
    public static final int RECORDS = 500;

    /** The SHA-256 of the cut: 392,463 bytes, the first record {@code sp|P22626|ROA2_HUMAN}. */
    public static final String CUT_SHA256 =
            "c12a0261c8ae410cbaf7d5060e23a53c7d9a6d7f628d28422f102a09d978088e";

    private FixtureFasta() {}

    private static List<String> listLines() throws IOException {
        Path list = UpstreamArtefacts.repositoryRoot().resolve(ACCESSIONS);
        assertTrue(Files.isRegularFile(list), list + " does not exist; it is a committed file");
        return Files.readAllLines(list, StandardCharsets.US_ASCII);
    }

    /**
     * The values the accession list's header records, by name.
     *
     * @return the header's {@code # name<TAB>value} lines
     * @throws IOException if the list cannot be read
     */
    public static Map<String, String> pins() throws IOException {
        Map<String, String> pins = new LinkedHashMap<>();
        for (String line : listLines()) {
            if (line.startsWith("#")) {
                String[] fields = line.substring(1).strip().split("\t", -1);
                if (fields.length == 2) {
                    pins.put(fields[0], fields[1]);
                }
            }
        }
        return pins;
    }

    /**
     * The committed fixture FASTA, held to the digest, the size and the record count its accession
     * list pins.
     *
     * @return the file in the checkout
     * @throws IOException if a file cannot be read
     */
    public static Path committed() throws IOException {
        Path fasta = UpstreamArtefacts.repositoryRoot().resolve(FASTA);
        assertTrue(Files.isRegularFile(fasta), fasta + " does not exist; it is a committed file");
        Map<String, String> pins = pins();
        byte[] bytes = Files.readAllBytes(fasta);
        assertEquals(
                pins.get("subset-sha256"),
                sha256(bytes),
                FASTA + " is not the file " + ACCESSIONS + " pins (subset-sha256)");
        assertEquals(pins.get("subset-bytes"), Integer.toString(bytes.length), "subset-bytes");
        assertEquals(
                pins.get("subset-records"),
                Integer.toString(records(bytes).size()),
                "subset-records");
        return fasta;
    }

    /**
     * The cut, written to a file and held to {@link #CUT_SHA256}.
     *
     * @param destination where the cut goes; its directory must exist
     * @return the destination
     * @throws IOException if a file cannot be read or written
     */
    public static Path cut(Path destination) throws IOException {
        Set<String> wanted = new HashSet<>();
        for (String line : listLines()) {
            String[] fields = line.split("\t", -1);
            if (!line.startsWith("#") && fields.length == 2 && KEPT_AS.equals(fields[1])) {
                wanted.add(fields[0]);
            }
        }
        StringBuilder cut = new StringBuilder();
        int taken = 0;
        for (String record : records(Files.readAllBytes(committed()))) {
            String[] bars = record.split("\\|", 3);
            assertEquals(3, bars.length, "a header without an accession between bars: " + record);
            if (taken < RECORDS && wanted.contains(bars[1])) {
                cut.append(record);
                taken++;
            }
        }
        assertEquals(RECORDS, taken, "records of " + FASTA + " marked " + KEPT_AS);
        byte[] bytes = cut.toString().getBytes(StandardCharsets.ISO_8859_1);
        assertEquals(
                CUT_SHA256,
                sha256(bytes),
                "the first " + RECORDS + " records of " + FASTA + " marked " + KEPT_AS);
        return Files.write(destination, bytes);
    }

    /** A FASTA's records, each from its header line through the line before the next header. */
    private static List<String> records(byte[] fasta) {
        String text = new String(fasta, StandardCharsets.ISO_8859_1);
        List<String> records = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int next = text.indexOf("\n>", start);
            int end = next < 0 ? text.length() : next + 1;
            records.add(text.substring(start, end));
            start = end;
        }
        return records;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError("every Java runtime provides SHA-256", impossible);
        }
    }
}
