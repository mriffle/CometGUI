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

package org.cometgui.params.comet.fixtures;

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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The database this module's real-binary tests search: a cut of the committed fixture FASTA ({@code
 * D-006}, {@code D-018}; {@code docs/developer/test_fixtures.rst}).
 *
 * <p>Nothing about the file is typed in this class. Which file, which accession list pins it, which
 * records are cut and the cut's SHA-256 are the {@code database} member of the validation corpus
 * ({@code fixtures/comet-validation/corpus.json}), the one place in this module that names them;
 * the committed file's own digest, size and record count are read from the header of the accession
 * list ({@code subset-sha256}, {@code subset-bytes}, {@code subset-records}), the lines {@code
 * scripts/fixtures/fixture_fasta.py} writes and reads, and the file is held to all three.
 *
 * <p>The cut is the first so-many records of the file, in its order and byte for byte, whose
 * accession the list marks as the corpus says. It is {@code cometgui-tools}' {@code FixtureFasta}'s
 * cut, whose documentation says why the tests search a cut and how it was chosen; test classes are
 * not published, so the same few lines are here.
 */
public final class FixtureFasta {

    private FixtureFasta() {}

    /**
     * The cut, held to its SHA-256, from the committed file held to its accession list's pins.
     *
     * @param root the repository root
     * @param fasta the committed FASTA, relative to the root
     * @param accessions the accession list that pins it, relative to the root
     * @param keptAs what the list calls the records the cut is taken from
     * @param records how many records so marked, from the first
     * @param cutSha256 the SHA-256 the cut must have
     * @return the cut's bytes
     * @throws IOException if a file cannot be read
     */
    public static byte[] cut(
            Path root,
            String fasta,
            String accessions,
            String keptAs,
            int records,
            String cutSha256)
            throws IOException {
        Path list = root.resolve(accessions);
        assertTrue(Files.isRegularFile(list), accessions + " does not exist; it is committed");
        Map<String, String> pins = new TreeMap<>();
        Set<String> wanted = new HashSet<>();
        for (String line : Files.readAllLines(list, StandardCharsets.US_ASCII)) {
            if (line.startsWith("#")) {
                String[] fields = line.substring(1).strip().split("\t", -1);
                if (fields.length == 2) {
                    pins.put(fields[0], fields[1]);
                }
            } else {
                String[] fields = line.split("\t", -1);
                if (fields.length == 2 && keptAs.equals(fields[1])) {
                    wanted.add(fields[0]);
                }
            }
        }
        Path file = root.resolve(fasta);
        assertTrue(Files.isRegularFile(file), fasta + " does not exist; it is committed");
        byte[] bytes = Files.readAllBytes(file);
        assertEquals(
                pins.get("subset-sha256"),
                sha256(bytes),
                fasta + " is not the file " + accessions + " pins (subset-sha256)");
        assertEquals(pins.get("subset-bytes"), Integer.toString(bytes.length), "subset-bytes");
        List<String> all = records(new String(bytes, StandardCharsets.ISO_8859_1));
        assertEquals(pins.get("subset-records"), Integer.toString(all.size()), "subset-records");

        StringBuilder cut = new StringBuilder();
        int taken = 0;
        for (String record : all) {
            String[] bars = record.split("\\|", 3);
            assertEquals(3, bars.length, "a header without an accession between bars: " + record);
            if (taken < records && wanted.contains(bars[1])) {
                cut.append(record);
                taken++;
            }
        }
        assertEquals(records, taken, "records of " + fasta + " marked " + keptAs);
        byte[] result = cut.toString().getBytes(StandardCharsets.ISO_8859_1);
        assertEquals(
                cutSha256,
                sha256(result),
                "the first " + records + " records of " + fasta + " marked " + keptAs);
        return result;
    }

    /** A FASTA's records, each from its header line through the line before the next header. */
    private static List<String> records(String text) {
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
