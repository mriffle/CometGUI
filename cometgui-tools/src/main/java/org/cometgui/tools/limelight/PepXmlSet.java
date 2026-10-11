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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.ports.HashService;
import org.cometgui.tools.limelight.LimelightConversionException.Failure;

/**
 * The pepXML files a conversion will read, each proved to be one of the run's own Comet outputs
 * before anything is launched ({@code R-LL-03}, design decision P12-8).
 *
 * <h2>How the converter finds its pepXML files</h2>
 *
 * <p>It does not list the directory. Phase 00 observed it ({@code
 * docs/feasibility/scientific-path}, <em>The converter's -d default</em>) and the v2.8.1 bytecode
 * states it: {@code ConversionUtils.getPepXMLPrefixFromPsmId} matches each Percolator PSM id
 * against {@code ^(.+)_\d+_\d+_\d+$} -- {@code <file>_<scan>_<charge>_<rank>}, the {@code .+}
 * greedy, so a file name that itself contains underscores and digits keeps them -- and {@code
 * PercolatorResultsReader} appends {@code .pep.xml} to the group; {@code getPepXMLFileWithPrefix}
 * then opens exactly that name in the {@code -d} directory and fails if it is absent. So {@code
 * 20100614_Velos1_TaGe_SA_K562_3_11367_2_1} is read from {@code
 * 20100614_Velos1_TaGe_SA_K562_3.pep.xml}. {@link #PSM_ID} is that expression, verbatim, and {@link
 * #expectedFileName} that rule.
 *
 * <p>The converter derives names from every PSM a peptide references, and refuses a reference to a
 * PSM the file does not hold, so the names derived here from every {@code psm} element are at least
 * every name it will open.
 *
 * <h2>What "belongs to the run" means here</h2>
 *
 * <p>Every derived name must exist in the directory as a regular file, must be one of the run's
 * recorded Comet outputs (the caller's map of file name to recorded SHA-256), and must hash to that
 * recorded SHA-256 now. A pepXML that has been replaced since the search, or one that a different
 * search left in the directory, is refused by name before the converter starts.
 *
 * @param percolatorXml the Percolator XML the names were derived from
 * @param directory the directory the converter is told to read them from ({@code -d})
 * @param files each pepXML's file name and the checksums it was verified with, in name order
 */
public record PepXmlSet(Path percolatorXml, Path directory, Map<String, FileHashes> files) {

    /** The converter's own PSM-id expression, from its v2.8.1 bytecode. */
    public static final Pattern PSM_ID = Pattern.compile("^(.+)_\\d+_\\d+_\\d+$");

    /** What the converter appends to the PSM id's file part. */
    public static final String EXTENSION = ".pep.xml";

    /**
     * Validates the set and copies its map, keeping its order.
     *
     * @throws NullPointerException if a component, a name or a checksum is {@code null}
     * @throws IllegalArgumentException if the set is empty
     */
    public PepXmlSet {
        Objects.requireNonNull(percolatorXml, "percolatorXml");
        Objects.requireNonNull(directory, "directory");
        Objects.requireNonNull(files, "files");
        if (files.isEmpty()) {
            throw new IllegalArgumentException("a conversion reads at least one pepXML file");
        }
        files = Collections.unmodifiableMap(new LinkedHashMap<>(files));
    }

    /**
     * The pepXML file name the converter derives from one PSM id.
     *
     * @param psmId a Percolator PSM id
     * @return the file name, for example {@code run_3.pep.xml} for {@code run_3_11367_2_1}
     * @throws IllegalArgumentException if the id does not have the converter's shape, or its file
     *     part holds a path separator and so would not name a file in the directory
     * @throws NullPointerException if {@code psmId} is {@code null}
     */
    public static String expectedFileName(String psmId) {
        Objects.requireNonNull(psmId, "psmId");
        Matcher matcher = PSM_ID.matcher(psmId);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "the PSM id \""
                            + psmId
                            + "\" is not <file>_<scan>_<charge>_<rank>, so the converter could not"
                            + " derive a pepXML file name from it");
        }
        String name = matcher.group(1) + EXTENSION;
        if (name.indexOf('/') >= 0 || name.indexOf('\\') >= 0 || name.indexOf('\0') >= 0) {
            throw new IllegalArgumentException(
                    "the PSM id \""
                            + psmId
                            + "\" gives the pepXML name \""
                            + name
                            + "\", which is a path rather than a file name in the pepXML"
                            + " directory");
        }
        return name;
    }

    /**
     * Derives every pepXML the converter will read and proves each is the run's own.
     *
     * @param percolatorXml the run's Percolator XML, already read
     * @param directory the pepXML directory the converter will be given
     * @param recordedSha256 the run's recorded Comet outputs: pepXML file name to the SHA-256
     *     recorded when Comet wrote it
     * @param hashes the product's one hashing service
     * @return the verified set
     * @throws LimelightConversionException naming the file and the reason, if the Percolator file
     *     is not Percolator XML or holds no PSM, a PSM id has no file part, or a pepXML is missing,
     *     not one of the run's outputs, or changed since it was recorded
     * @throws IOException if a pepXML cannot be hashed
     * @throws NullPointerException if any argument, or any name or checksum in the map, is {@code
     *     null}
     */
    public static PepXmlSet verify(
            PercolatorXmlPsms percolatorXml,
            Path directory,
            Map<String, String> recordedSha256,
            HashService hashes)
            throws IOException {
        Objects.requireNonNull(percolatorXml, "percolatorXml");
        Objects.requireNonNull(directory, "directory");
        Objects.requireNonNull(recordedSha256, "recordedSha256");
        Objects.requireNonNull(hashes, "hashes");
        Map<String, String> recorded = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : recordedSha256.entrySet()) {
            recorded.put(
                    Objects.requireNonNull(entry.getKey(), "recorded file name"),
                    Objects.requireNonNull(entry.getValue(), "recorded SHA-256")
                            .toLowerCase(Locale.ROOT));
        }
        Path source = percolatorXml.file();
        if (!percolatorXml.isPercolatorXml()) {
            throw new LimelightConversionException(
                    Failure.NOT_PERCOLATOR_XML,
                    source,
                    source + " is not Percolator XML, so the Limelight converter cannot read it",
                    null);
        }
        if (percolatorXml.psmIds().isEmpty()) {
            throw new LimelightConversionException(
                    Failure.NO_PSMS,
                    source,
                    "the Percolator XML "
                            + source
                            + " holds no PSM, so there is nothing to convert and no pepXML the"
                            + " converter would read",
                    null);
        }
        TreeSet<String> names = new TreeSet<>();
        for (String id : percolatorXml.psmIds()) {
            try {
                names.add(expectedFileName(id));
            } catch (IllegalArgumentException underivable) {
                throw new LimelightConversionException(
                        Failure.PSM_ID_UNDERIVABLE,
                        source,
                        "in " + source + ": " + underivable.getMessage(),
                        underivable);
            }
        }
        Map<String, FileHashes> verified = new LinkedHashMap<>();
        for (String name : names) {
            verified.put(name, verifyOne(directory, name, recorded, hashes));
        }
        return new PepXmlSet(source, directory, verified);
    }

    private static FileHashes verifyOne(
            Path directory, String name, Map<String, String> recorded, HashService hashes)
            throws IOException {
        Path file = directory.resolve(name);
        if (!Files.isRegularFile(file)) {
            throw new LimelightConversionException(
                    Failure.PEPXML_MISSING,
                    file,
                    "the Limelight converter will read "
                            + file
                            + ", derived from the Percolator PSM ids, and there is no such file",
                    null);
        }
        String expected = recorded.get(name);
        if (expected == null) {
            throw new LimelightConversionException(
                    Failure.PEPXML_NOT_OF_THIS_RUN,
                    file,
                    "the Limelight converter will read "
                            + file
                            + ", and it is not one of this run's recorded Comet outputs ("
                            + String.join(", ", new TreeSet<>(recorded.keySet()))
                            + ")",
                    null);
        }
        FileHashes actual = hashes.hash(file);
        if (!expected.equals(actual.sha256())) {
            throw new LimelightConversionException(
                    Failure.PEPXML_CHECKSUM_MISMATCH,
                    file,
                    "the pepXML "
                            + file
                            + " has SHA-256 "
                            + actual.sha256()
                            + ", not the "
                            + expected
                            + " this run recorded when Comet wrote it, so it is not this run's"
                            + " output any more",
                    null);
        }
        return actual;
    }

    /**
     * The pepXML files, as paths in the directory, in name order.
     *
     * @return the paths, immutable
     */
    public List<Path> paths() {
        return files.keySet().stream().map(directory::resolve).toList();
    }
}
