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

package org.cometgui.tools.limelight;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * One Limelight conversion, fully typed: everything the converter is given, and nothing it is left
 * to infer.
 *
 * <p>The converter would infer three things if allowed to, and each inference is one this product
 * refuses: the pepXML directory (it defaults to the Percolator file's directory, which in a run's
 * layout holds no pepXML), the FASTA (it falls back to {@code database_name} in {@code
 * comet.params}, which records wherever the FASTA was when Comet ran), and the q-value cutoff (its
 * default is 0.01 today and is not this product's to leave implicit -- design decision P12-6). So
 * {@code -d}, {@code -f} and {@code -q} are always passed.
 *
 * <p><strong>The pepXML directory is a {@link PepXmlSet}, not a path</strong>: a request cannot be
 * made until every pepXML the converter will read has been proved to be the run's own (P12-8), and
 * the set must have been derived from this request's own Percolator XML.
 *
 * <p><strong>Decoy import is decided here, before any process exists</strong> (P12-7, {@code
 * R-LL-05}). Phase 00 ran all four combinations against the real converter: {@code --import-decoys}
 * against a Percolator run without {@code -Z} exits 0 and silently imports no decoy; a {@code -Z}
 * run converted <em>without</em> {@code --import-decoys} fails outright ("Unable to find any comet
 * results for reported peptide", re-observed 2026-10-11 on {@code decoy-chain/concat.pout.xml}). So
 * the two must agree, and a request whose decoy choice disagrees with the decoy PSMs its Percolator
 * XML actually holds cannot be constructed. {@link #decoyImportUnavailable} is the same rule, for a
 * control that must be disabled before anyone asks.
 *
 * @param cometParams the run's {@code comet.params} ({@code -c})
 * @param percolatorXml the run's Percolator XML, read ({@code -p})
 * @param pepXmls the verified pepXML files and their directory ({@code -d})
 * @param fasta the FASTA the search used ({@code -f})
 * @param output where the Limelight XML must end up; nothing may be there yet
 * @param qCutoff the Limelight q-value cutoff ({@code -q})
 * @param importDecoys whether to import decoys ({@code --import-decoys})
 * @param independentDecoyPrefix the independent-decoy protein prefix, if any ({@code
 *     --independent-decoy-prefix})
 * @param openMod whether to run in open-modification mode ({@code --open-mod})
 * @param verbose whether errors carry full stack traces ({@code -v})
 */
public record LimelightConversionRequest(
        Path cometParams,
        PercolatorXmlPsms percolatorXml,
        PepXmlSet pepXmls,
        Path fasta,
        Path output,
        LimelightQValueCutoff qCutoff,
        boolean importDecoys,
        Optional<String> independentDecoyPrefix,
        boolean openMod,
        boolean verbose) {

    /**
     * Validates the request.
     *
     * @throws NullPointerException if a component, or a present prefix, is {@code null}
     * @throws IllegalArgumentException if a path is relative, the pepXML set was derived from a
     *     different Percolator file, the decoy choice disagrees with the Percolator XML, or the
     *     prefix is blank or holds whitespace
     */
    public LimelightConversionRequest {
        absolute(cometParams, "cometParams");
        Objects.requireNonNull(percolatorXml, "percolatorXml");
        absolute(percolatorXml.file(), "percolatorXml");
        Objects.requireNonNull(pepXmls, "pepXmls");
        absolute(pepXmls.directory(), "pepXmls");
        absolute(fasta, "fasta");
        absolute(output, "output");
        Objects.requireNonNull(qCutoff, "qCutoff");
        Objects.requireNonNull(independentDecoyPrefix, "independentDecoyPrefix");
        if (!pepXmls.percolatorXml().equals(percolatorXml.file())) {
            throw new IllegalArgumentException(
                    "the pepXML set was derived from "
                            + pepXmls.percolatorXml()
                            + ", not from this conversion's Percolator XML "
                            + percolatorXml.file());
        }
        if (importDecoys) {
            Optional<String> refusal = decoyImportUnavailable(percolatorXml);
            if (refusal.isPresent()) {
                throw new IllegalArgumentException(refusal.get());
            }
        } else if (percolatorXml.decoyPsmCount() > 0) {
            throw new IllegalArgumentException(
                    "the Percolator XML "
                            + percolatorXml.file()
                            + " holds "
                            + percolatorXml.decoyPsmCount()
                            + " decoy PSMs (Percolator ran with -Z), and the converter fails on"
                            + " such a file unless decoy import is selected");
        }
        independentDecoyPrefix.ifPresent(LimelightConversionRequest::checkPrefix);
    }

    /**
     * Why decoy import cannot be offered for a Percolator XML, if it cannot.
     *
     * @param percolatorXml the run's Percolator XML, read
     * @return the explanation, or empty when the file holds decoy PSMs
     * @throws NullPointerException if {@code percolatorXml} is {@code null}
     */
    public static Optional<String> decoyImportUnavailable(PercolatorXmlPsms percolatorXml) {
        Objects.requireNonNull(percolatorXml, "percolatorXml");
        if (percolatorXml.decoyPsmCount() > 0) {
            return Optional.empty();
        }
        return Optional.of(
                "Limelight decoy import is not available for this run: its Percolator output "
                        + percolatorXml.file()
                        + " holds no decoy PSMs, because Percolator was not run with -Z (decoy"
                        + " XML output). Asking the converter to import decoys from it would"
                        + " silently import none. Comet's internal decoys cannot be imported"
                        + " either: the converter looks every protein up in the FASTA, and"
                        + " Comet's generated decoys are not FASTA entries.");
    }

    private static void checkPrefix(String prefix) {
        if (prefix.isBlank()
                || prefix.codePoints()
                        .anyMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c))) {
            throw new IllegalArgumentException(
                    "an independent decoy prefix is the start of a protein name and cannot be"
                            + " blank or hold whitespace, but was \""
                            + prefix
                            + "\"");
        }
    }

    private static void absolute(Path path, String field) {
        Objects.requireNonNull(path, field);
        if (!path.isAbsolute()) {
            throw new IllegalArgumentException(
                    field
                            + " must be an absolute path, because the converter runs in its own"
                            + " directory and would resolve a relative one there, but was: "
                            + path);
        }
    }
}
