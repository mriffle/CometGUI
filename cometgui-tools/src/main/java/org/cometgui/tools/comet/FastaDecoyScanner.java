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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import org.cometgui.domain.params.FastaDecoyCensus;

/**
 * Counts the records of a FASTA file and those whose accession begins with the decoy prefix ({@code
 * R-DEC-02}: "detect whether the selected FASTA already contains decoy entries by scanning
 * accessions for the configured decoy prefix, and report the count").
 *
 * <p>The file is <strong>streamed</strong>, one line at a time: a proteome is hundreds of megabytes
 * and is never held in memory. Lines end in LF, CRLF or a lone CR alike. A record is a line that
 * begins with {@code >}; its accession is the first white-space delimited token after the {@code
 * >}, and it is a decoy when that token begins with the prefix, compared byte for byte (the prefix
 * is matched in UTF-8, so a file in any ASCII-compatible encoding is scanned correctly).
 *
 * <p>What is refused, with a {@link FastaScanException} naming the file: a file that does not
 * exist, cannot be read or is a directory; a file holding no record; and a file whose first line
 * that is not blank does not begin with {@code >}, which is not a FASTA file (an index, a spectrum
 * file or a parameter file chosen by mistake).
 */
public final class FastaDecoyScanner {

    /** The character that begins a record's header line. */
    static final char HEADER = '>';

    private FastaDecoyScanner() {}

    /**
     * Scans a FASTA file for decoys.
     *
     * @param fasta the file
     * @param prefix the decoy prefix: the project's {@code decoy_prefix}
     * @return the census
     * @throws FastaScanException if the file is missing, unreadable, empty or not a FASTA file
     * @throws IllegalArgumentException if the prefix is empty or holds white space
     */
    public static FastaDecoyCensus scan(Path fasta, String prefix) throws FastaScanException {
        Objects.requireNonNull(fasta, "fasta");
        Objects.requireNonNull(prefix, "prefix");
        if (prefix.isEmpty() || prefix.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException(
                    "a decoy prefix is one non-empty token, not \"" + prefix + "\"");
        }
        if (Files.isDirectory(fasta)) {
            throw new FastaScanException(fasta, "is a directory, not a file", null);
        }
        // ISO-8859-1 maps every byte to one character, so the comparison below is byte for byte
        // whatever the file's encoding; the prefix is turned into the same characters.
        String bytePrefix =
                new String(prefix.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1);
        long records = 0;
        long decoys = 0;
        String firstDecoy = null;
        boolean seenContent = false;
        try (BufferedReader reader =
                new BufferedReader(
                        new InputStreamReader(
                                Files.newInputStream(fasta), StandardCharsets.ISO_8859_1))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isEmpty() && line.charAt(0) == HEADER) {
                    seenContent = true;
                    records++;
                    String accession = accession(line);
                    if (accession.startsWith(bytePrefix)) {
                        decoys++;
                        if (firstDecoy == null) {
                            firstDecoy = accession;
                        }
                    }
                } else if (!seenContent && !line.isBlank()) {
                    throw new FastaScanException(
                            fasta,
                            "is not a FASTA file: its first line that is not blank does not begin"
                                    + " with '>' (it begins \""
                                    + shown(line)
                                    + "\")",
                            null);
                }
            }
        } catch (NoSuchFileException missing) {
            throw new FastaScanException(fasta, "does not exist", missing);
        } catch (AccessDeniedException denied) {
            throw new FastaScanException(fasta, "cannot be read: permission denied", denied);
        } catch (FastaScanException refused) {
            throw refused;
        } catch (IOException unreadable) {
            throw new FastaScanException(
                    fasta, "cannot be read: " + unreadable.getMessage(), unreadable);
        }
        if (records == 0) {
            throw new FastaScanException(
                    fasta, "holds no FASTA record (no line begins with '>')", null);
        }
        return new FastaDecoyCensus(
                fasta,
                prefix,
                records,
                decoys,
                Optional.ofNullable(firstDecoy)
                        .map(
                                accession ->
                                        new String(
                                                accession.getBytes(StandardCharsets.ISO_8859_1),
                                                StandardCharsets.UTF_8)));
    }

    /**
     * The accession of a header line: its first white-space delimited token after the {@code >}.
     *
     * @param header the line, beginning with {@code >}
     * @return the token; empty for a header with none
     */
    static String accession(String header) {
        int start = 1;
        while (start < header.length() && Character.isWhitespace(header.charAt(start))) {
            start++;
        }
        int end = start;
        while (end < header.length() && !Character.isWhitespace(header.charAt(end))) {
            end++;
        }
        return header.substring(start, end);
    }

    /** At most the first 40 characters of a line, for a message. */
    private static String shown(String line) {
        return line.length() <= 40 ? line : line.substring(0, 40) + "...";
    }
}
