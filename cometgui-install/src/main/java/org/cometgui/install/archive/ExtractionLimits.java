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

package org.cometgui.install.archive;

/**
 * The three independent ceilings that stop a decompression bomb, and the floor the ratio test
 * needs.
 *
 * <p>Three limits rather than one, because a bomb can be shaped to slip past any single number. A
 * few hugely compressible entries defeat an entry-count limit; a million one-byte entries defeat a
 * total-size limit; a large, barely compressed archive defeats a ratio limit. Each is therefore
 * tested on its own, with the other two set generously, so that a change to one cannot silently
 * disable the others.
 *
 * <h2>Where the defaults come from</h2>
 *
 * <p>Measured on the real artefacts this product installs, not chosen by taste. The largest is PDV
 * 2.7.0: <strong>222 entries, 115057606 bytes uncompressed from a 103407417-byte archive, an
 * expansion ratio of 1.113</strong>. The most expansive is the Percolator 3.09 {@code .deb}
 * payload, whose {@code data.tar} is 13264896 bytes inside a 3278718-byte package -- a ratio of
 * 4.046. The defaults leave roughly 9x headroom on total size, 45x on entry count and 24x on ratio.
 *
 * <h2>Why the ratio needs a floor</h2>
 *
 * <p>A small archive has a meaninglessly large ratio: a tar of three short files is mostly
 * 512-byte-block padding, so it compresses twenty- or fiftyfold and would trip any useful ratio
 * ceiling. {@link #ratioCheckedAboveBytes} is the point past which the ratio starts being tested --
 * a bomb has to get there to do damage, and a legitimate small archive never does. It is a
 * threshold on the <em>attack</em>, not an exemption from the check: an archive that expands past
 * this floor is measured, whatever its size on disk.
 *
 * @param maxTotalUncompressedBytes the most this extractor will produce from one artefact, counting
 *     every byte that leaves the container whether it is written or discarded
 * @param maxEntryCount the most entries one artefact may contain
 * @param maxExpansionRatio the most the uncompressed bytes may exceed the artefact's size on disk
 * @param ratioCheckedAboveBytes the expansion past which the ratio is enforced
 */
public record ExtractionLimits(
        long maxTotalUncompressedBytes,
        int maxEntryCount,
        double maxExpansionRatio,
        long ratioCheckedAboveBytes) {

    /** 1 GiB: about nine times PDV 2.7.0's 115057606 uncompressed bytes. */
    public static final long DEFAULT_MAX_TOTAL_UNCOMPRESSED_BYTES = 1_073_741_824L;

    /** 10000 entries: about forty-five times PDV 2.7.0's 222. */
    public static final int DEFAULT_MAX_ENTRY_COUNT = 10_000;

    /** 100:1, about twenty-four times the 4.046 of the Percolator 3.09 {@code .deb} payload. */
    public static final double DEFAULT_MAX_EXPANSION_RATIO = 100.0d;

    /** 8 MiB, above every fixture-sized archive and below every artefact this product installs. */
    public static final long DEFAULT_RATIO_CHECKED_ABOVE_BYTES = 8_388_608L;

    /**
     * Validates the limits.
     *
     * @throws IllegalArgumentException if any limit is not positive, or if the ratio is not greater
     *     than one -- naming the field and the value
     */
    public ExtractionLimits {
        if (maxTotalUncompressedBytes <= 0) {
            throw new IllegalArgumentException(
                    "maxTotalUncompressedBytes must be a positive number of bytes, but was: "
                            + maxTotalUncompressedBytes);
        }
        if (maxEntryCount <= 0) {
            throw new IllegalArgumentException(
                    "maxEntryCount must be a positive number of entries, but was: "
                            + maxEntryCount);
        }
        if (!(maxExpansionRatio > 1.0d)) {
            throw new IllegalArgumentException(
                    "maxExpansionRatio must be greater than 1, because every compressed archive"
                            + " expands, but was: "
                            + maxExpansionRatio);
        }
        if (ratioCheckedAboveBytes < 0) {
            throw new IllegalArgumentException(
                    "ratioCheckedAboveBytes must not be negative, but was: "
                            + ratioCheckedAboveBytes);
        }
    }

    /**
     * The limits the product runs with, calibrated against the artefacts in the manifest.
     *
     * @return the default limits
     */
    public static ExtractionLimits defaults() {
        return new ExtractionLimits(
                DEFAULT_MAX_TOTAL_UNCOMPRESSED_BYTES,
                DEFAULT_MAX_ENTRY_COUNT,
                DEFAULT_MAX_EXPANSION_RATIO,
                DEFAULT_RATIO_CHECKED_ABOVE_BYTES);
    }
}
