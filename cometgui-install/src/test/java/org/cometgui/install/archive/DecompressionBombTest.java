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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.cometgui.domain.tools.ArtefactKind;
import org.cometgui.install.archive.ArchiveFixtures.Entry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The decompression-bomb guard, proved to bite on each of its three ceilings separately, for every
 * container kind.
 *
 * <h2>Why one test per ceiling and not one test for "a bomb"</h2>
 *
 * <p>A bomb can be shaped to slip past any single number: a few hugely compressible entries defeat
 * an entry-count limit, a million one-byte entries defeat a total-size limit, and a large, barely
 * compressed archive defeats a ratio limit. Three ceilings only help if all three are live, so each
 * one is exercised here <strong>with the other two set generously enough that they cannot be what
 * fired</strong>. If someone removes the total-size check, only the total-size test goes red -- and
 * that is the property being bought.
 *
 * <h2>The other half: the guard must not fire on the real thing</h2>
 *
 * <p>{@code RealArtefactExtractionTest.pdvIsNotRejected} unpacks the genuine 222-entry, 115057606
 * -byte PDV archive under the shipped limits. A bomb guard that rejected the largest artefact the
 * product installs would be discovered at install time by a scientist, not here.
 */
class DecompressionBombTest {

    /** One mebibyte of zeros: it compresses to about a kilobyte in every container here. */
    private static final int ONE_MEBIBYTE = 1024 * 1024;

    @TempDir private Path work;

    private Path archives;

    private Path destination;

    static List<ArtefactKind> multiEntryKinds() {
        return ArchiveFixtures.MULTI_ENTRY_KINDS;
    }

    @BeforeEach
    void createTree() throws IOException {
        archives = Files.createDirectories(work.resolve("archives"));
        destination = Files.createDirectories(work.resolve("dest"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("multiEntryKinds")
    @DisplayName("the expansion ratio bites on its own, whatever the container")
    void expansionRatio(ArtefactKind kind) throws IOException {
        ExtractionLimits limits =
                new ExtractionLimits(64L * ONE_MEBIBYTE, 1000, 10.0d, 64L * 1024L);
        Path artefact =
                ArchiveFixtures.build(
                        kind,
                        archives,
                        "ratio-" + kind.id(),
                        List.of(Entry.file("big.bin", ArchiveFixtures.compressible(ONE_MEBIBYTE))));
        ExtractionRejectedException rejection = assertRejected(kind, artefact, limits);
        assertAll(
                () -> assertEquals(RejectionReason.BOMB_EXPANSION_RATIO, rejection.reason()),
                () ->
                        assertEquals(
                                "the archive entry \"big.bin\" was rejected because expanding it"
                                        + " takes the artefact past the ratio of uncompressed to"
                                        + " compressed bytes this extractor will produce -- this"
                                        + " extractor expands an artefact at most 10.0 times, and"
                                        + " this one is "
                                        + Files.size(artefact)
                                        + " bytes on disk",
                                rejection.getMessage()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("multiEntryKinds")
    @DisplayName("the total uncompressed size bites on its own, whatever the container")
    void totalUncompressedSize(ArtefactKind kind) throws IOException {
        ExtractionLimits limits =
                new ExtractionLimits(262_144L, 1000, 1_000_000.0d, Long.MAX_VALUE);
        Path artefact =
                ArchiveFixtures.build(
                        kind,
                        archives,
                        "total-" + kind.id(),
                        List.of(Entry.file("big.bin", ArchiveFixtures.compressible(ONE_MEBIBYTE))));
        ExtractionRejectedException rejection = assertRejected(kind, artefact, limits);
        assertAll(
                () ->
                        assertEquals(
                                RejectionReason.BOMB_TOTAL_UNCOMPRESSED_SIZE, rejection.reason()),
                () ->
                        assertEquals(
                                "the archive entry \"big.bin\" was rejected because expanding it"
                                        + " takes the artefact past the total uncompressed size"
                                        + " this extractor will produce -- this extractor produces"
                                        + " at most 262144 bytes from one artefact",
                                rejection.getMessage()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("multiEntryKinds")
    @DisplayName("the entry count bites on its own, whatever the container")
    void entryCount(ArtefactKind kind) throws IOException {
        ExtractionLimits limits =
                new ExtractionLimits(64L * ONE_MEBIBYTE, 3, 1_000_000.0d, Long.MAX_VALUE);
        List<Entry> entries = new ArrayList<>();
        for (int index = 1; index <= 5; index++) {
            entries.add(Entry.file("file-" + index + ".txt", "small"));
        }
        Path artefact = ArchiveFixtures.build(kind, archives, "count-" + kind.id(), entries);
        ExtractionRejectedException rejection = assertRejected(kind, artefact, limits);
        assertAll(
                () -> assertEquals(RejectionReason.BOMB_ENTRY_COUNT, rejection.reason()),
                () ->
                        assertEquals(
                                "the archive entry \"file-4.txt\" was rejected because the artefact"
                                        + " holds more entries than this extractor will read -- it"
                                        + " is entry number 4 and this extractor reads at most 3",
                                rejection.getMessage()));
    }

    @Test
    @DisplayName(
            "the ratio floor is not an exemption: a small, hugely compressible archive is"
                    + " accepted and a large one with the same ratio is not")
    void theRatioFloorLetsSmallArchivesThrough() throws IOException {
        ExtractionLimits limits =
                new ExtractionLimits(64L * ONE_MEBIBYTE, 1000, 10.0d, ONE_MEBIBYTE);
        Path small =
                ArchiveFixtures.build(
                        ArtefactKind.TAR_GZ,
                        archives,
                        "small.tar.gz",
                        List.of(Entry.file("a.bin", ArchiveFixtures.compressible(64 * 1024))));
        ExtractionReport report =
                new ArtefactExtractor(limits)
                        .extractWholeArtefact(ArtefactKind.TAR_GZ, small, destination, "a.bin");
        Path large =
                ArchiveFixtures.build(
                        ArtefactKind.TAR_GZ,
                        archives,
                        "large.tar.gz",
                        List.of(
                                Entry.file(
                                        "b.bin", ArchiveFixtures.compressible(4 * ONE_MEBIBYTE))));
        Path other = Files.createDirectories(work.resolve("dest2"));
        ExtractionRejectedException rejection =
                assertThrows(
                        ExtractionRejectedException.class,
                        () ->
                                new ArtefactExtractor(limits)
                                        .extractWholeArtefact(
                                                ArtefactKind.TAR_GZ, large, other, "b.bin"));
        assertAll(
                () ->
                        assertEquals(
                                65_536L,
                                report.expandedBytes(),
                                "the small archive expands past ten times its own size and is"
                                        + " accepted, because it never reaches the floor"),
                () -> assertEquals(RejectionReason.BOMB_EXPANSION_RATIO, rejection.reason()));
    }

    @Test
    @DisplayName("the shipped limits leave the headroom the manifest's own artefacts need")
    void theShippedLimitsAreCalibratedAgainstRealArtefacts() {
        ExtractionLimits limits = ExtractionLimits.defaults();
        assertAll(
                () -> assertEquals(1_073_741_824L, limits.maxTotalUncompressedBytes()),
                () -> assertEquals(10_000, limits.maxEntryCount()),
                () -> assertEquals(100.0d, limits.maxExpansionRatio()),
                () -> assertEquals(8_388_608L, limits.ratioCheckedAboveBytes()),
                () ->
                        assertEquals(
                                true,
                                limits.maxTotalUncompressedBytes() > 115_057_606L * 9,
                                "PDV 2.7.0 expands to 115057606 bytes; the ceiling leaves at least"
                                        + " nine times that"),
                () ->
                        assertEquals(
                                true,
                                limits.maxEntryCount() > 222 * 45,
                                "PDV 2.7.0 holds 222 entries; the ceiling leaves at least"
                                        + " forty-five times that"),
                () ->
                        assertEquals(
                                true,
                                limits.maxExpansionRatio() > 4.046d * 24,
                                "the Percolator 3.09 Debian payload expands 4.046 times; the"
                                        + " ceiling leaves at least twenty-four times that"));
    }

    private ExtractionRejectedException assertRejected(
            ArtefactKind kind, Path artefact, ExtractionLimits limits) throws IOException {
        List<String> before = DestinationSnapshot.outside(work, destination);
        ExtractionRejectedException rejection =
                assertThrows(
                        ExtractionRejectedException.class,
                        () ->
                                new ArtefactExtractor(limits)
                                        .extractWholeArtefact(
                                                kind, artefact, destination, "big.bin"),
                        () -> kind + " accepted a decompression bomb");
        DestinationSnapshot.assertNothingOutside(
                work, destination, before, "unpacking a bomb from a " + kind.id());
        return rejection;
    }
}
