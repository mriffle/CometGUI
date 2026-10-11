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

package org.cometgui.workflow.limelight;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.cometgui.tools.limelight.PercolatorXmlPsms;

/**
 * The run's Percolator XML as the Limelight converter can read it (proposed design decision
 * P12-18).
 *
 * <h2>Why a CometGUI run needs this</h2>
 *
 * <p>CometGUI runs Comet with an <em>absolute</em> {@code -N} base, {@code
 * <run>/outputs/comet/<base>}, and Comet writes that base into every PIN {@code SpecId}, which
 * Percolator carries into every {@code p:psm_id} and every peptide's {@code psm_id}. The pinned
 * converter v2.8.1 cannot read such a file, whatever {@code -d} is given (observed 2026-10-11):
 *
 * <ul>
 *   <li>it opens {@code new File(<-d>, <psm id's file part> + ".pep.xml")}, and {@code
 *       java.io.File} concatenates an absolute child onto the directory, so with {@code -d
 *       <run>/outputs/comet} it looks for {@code
 *       <run>/outputs/comet<run>/outputs/comet/<base>.pep.xml} and fails;
 *   <li>with {@code -d /} it finds the file but then fails "Verifying all percolator results have
 *       comet results" ("Unable to find any comet results in ... for reported peptide"), because it
 *       keys the Comet side by the pepXML's file name and the Percolator side by the PSM id's whole
 *       file part.
 * </ul>
 *
 * <p>The same Comet and Percolator outputs with the directory removed from every PSM id convert
 * correctly with {@code -d <run>/outputs/comet}: 1838 of 1838 PSMs in the observed case. The
 * Limelight XML does not carry PSM ids (each {@code psm} names its scan, charge and the pepXML's
 * spectrum file), so removing the directory changes nothing the converter writes except the {@code
 * -p} path in its record of its own arguments.
 *
 * <h2>The rule</h2>
 *
 * <ul>
 *   <li>A PSM id with no path separator is left alone, and when every id is such, the run's own
 *       file is given to the converter unchanged.
 *   <li>Otherwise every id that holds a separator must be exactly {@code <pepXML directory>} plus a
 *       separator plus a name holding none -- the converter's pepXML directory, which is {@code
 *       outputs/comet/} of the run that searched -- or the conversion is refused naming the id: it
 *       names a Comet output outside that directory, so not this run's (P12-8).
 *   <li>The copy is the file's bytes with every occurrence of that directory-and-separator, in
 *       UTF-8, removed. Nothing else changes.
 *   <li>The copy is then read back through {@link PercolatorXmlPsms}, the one pout reader, and must
 *       hold exactly the original's PSM ids with the directory removed, in the same order, and the
 *       same number of decoy PSMs; otherwise it is deleted and the conversion refused.
 * </ul>
 *
 * <p>The copy lives in the run's {@code results/limelight/} for the length of one conversion and is
 * deleted afterwards; its MD5 and SHA-256 are recorded in the conversion's events beside the run's
 * own Percolator XML.
 */
final class PoutForConverter {

    private PoutForConverter() {}

    /**
     * Each PSM id as the converter must be given it, or the refusal.
     *
     * @param psms the run's Percolator XML, read
     * @param pepXmlDirectory the converter's pepXML directory
     * @return the ids, in document order, with the directory removed where it was present
     * @throws IllegalArgumentException naming the first id that holds a separator and is not a name
     *     directly in the directory
     */
    static List<String> converterIds(PercolatorXmlPsms psms, Path pepXmlDirectory) {
        String prefix = prefixOf(pepXmlDirectory);
        List<String> ids = new ArrayList<>(psms.psmIds().size());
        for (String id : psms.psmIds()) {
            if (!hasSeparator(id)) {
                ids.add(id);
                continue;
            }
            String rest = id.startsWith(prefix) ? id.substring(prefix.length()) : null;
            if (rest == null || rest.isEmpty() || hasSeparator(rest)) {
                throw new IllegalArgumentException(
                        "the Percolator PSM id \""
                                + id
                                + "\" in "
                                + psms.file()
                                + " names a Comet output that is not directly in "
                                + pepXmlDirectory
                                + ", so it is not one of this run's pepXML files");
            }
            ids.add(rest);
        }
        return List.copyOf(ids);
    }

    /**
     * Whether the converter can be given the run's own file.
     *
     * @param psms the run's Percolator XML, read
     * @return {@code true} when no PSM id holds a path separator
     */
    static boolean needsCopy(PercolatorXmlPsms psms) {
        return psms.psmIds().stream().anyMatch(PoutForConverter::hasSeparator);
    }

    /**
     * Writes the copy and proves it.
     *
     * @param psms the run's Percolator XML, read
     * @param pepXmlDirectory the converter's pepXML directory
     * @param copy where to write it; nothing may be there
     * @return the copy, read back
     * @throws IOException naming the reason, if the copy could not be written or does not hold
     *     exactly the expected PSM ids; nothing is then left at {@code copy}
     * @throws IllegalArgumentException as {@link #converterIds}
     */
    static PercolatorXmlPsms write(PercolatorXmlPsms psms, Path pepXmlDirectory, Path copy)
            throws IOException {
        Objects.requireNonNull(copy, "copy");
        List<String> expected = converterIds(psms, pepXmlDirectory);
        byte[] prefix = prefixOf(pepXmlDirectory).getBytes(StandardCharsets.UTF_8);
        try {
            try (InputStream in = new BufferedInputStream(Files.newInputStream(psms.file()));
                    OutputStream out =
                            new BufferedOutputStream(
                                    Files.newOutputStream(copy, StandardOpenOption.CREATE_NEW))) {
                removeAll(in, out, prefix);
            }
            PercolatorXmlPsms read = PercolatorXmlPsms.read(copy);
            if (!read.psmIds().equals(expected)
                    || read.decoyPsmCount() != psms.decoyPsmCount()
                    || !read.isPercolatorXml()) {
                throw new IOException(
                        "the copy of "
                                + psms.file()
                                + " with "
                                + pepXmlDirectory
                                + " removed from its PSM ids does not hold exactly the original's"
                                + " "
                                + expected.size()
                                + " PSM ids and "
                                + psms.decoyPsmCount()
                                + " decoy PSMs (it holds "
                                + read.psmIds().size()
                                + " and "
                                + read.decoyPsmCount()
                                + "), so the converter is not given it");
            }
            return read;
        } catch (IOException | RuntimeException failed) {
            Files.deleteIfExists(copy);
            throw failed;
        }
    }

    /**
     * Copies {@code in} to {@code out} without any occurrence of {@code pattern}: a streaming
     * Knuth-Morris-Pratt match, so a partial match is written out exactly as it was read.
     *
     * @return how many occurrences were removed
     */
    static long removeAll(InputStream in, OutputStream out, byte[] pattern) throws IOException {
        int[] border = borders(pattern);
        long removed = 0;
        int matched = 0;
        int next;
        while ((next = in.read()) != -1) {
            byte b = (byte) next;
            while (true) {
                if (b == pattern[matched]) {
                    matched++;
                    if (matched == pattern.length) {
                        removed++;
                        matched = 0;
                    }
                    break;
                }
                if (matched == 0) {
                    out.write(b);
                    break;
                }
                int keep = border[matched - 1];
                out.write(pattern, 0, matched - keep);
                matched = keep;
            }
        }
        out.write(pattern, 0, matched);
        return removed;
    }

    /** {@code border[i]}: the length of the longest proper border of {@code pattern[0..i]}. */
    private static int[] borders(byte[] pattern) {
        int[] border = new int[pattern.length];
        int length = 0;
        for (int i = 1; i < pattern.length; i++) {
            while (length > 0 && pattern[i] != pattern[length]) {
                length = border[length - 1];
            }
            if (pattern[i] == pattern[length]) {
                length++;
            }
            border[i] = length;
        }
        return border;
    }

    private static String prefixOf(Path directory) {
        Objects.requireNonNull(directory, "directory");
        return directory.toString() + directory.getFileSystem().getSeparator();
    }

    private static boolean hasSeparator(String id) {
        return id.indexOf('/') >= 0 || id.indexOf('\\') >= 0;
    }

    /**
     * The first id that is refused, as a sentence, if any.
     *
     * @param psms the run's Percolator XML
     * @param pepXmlDirectory the converter's pepXML directory
     * @return the refusal, or empty
     */
    static Optional<String> problem(PercolatorXmlPsms psms, Path pepXmlDirectory) {
        try {
            converterIds(psms, pepXmlDirectory);
            return Optional.empty();
        } catch (IllegalArgumentException refused) {
            return Optional.of(refused.getMessage());
        }
    }
}
