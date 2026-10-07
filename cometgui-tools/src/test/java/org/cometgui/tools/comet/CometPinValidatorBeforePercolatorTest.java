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

import static org.cometgui.tools.comet.PinText.HEADER;
import static org.cometgui.tools.comet.PinText.decoy;
import static org.cometgui.tools.comet.PinText.lines;
import static org.cometgui.tools.comet.PinText.row;
import static org.cometgui.tools.comet.PinText.target;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.cometgui.tools.testing.Nulls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link CometPinValidator#validateBeforePercolator} on constructed PIN files: the same rules and
 * messages as {@link CometPinValidator#validate}, then the two decoy-prefix contradictions, each
 * message hand-typed. The real files are graded in the Percolator real-binary test.
 */
class CometPinValidatorBeforePercolatorTest {

    static final PinDecoyConfiguration CONCATENATED =
            new PinDecoyConfiguration(1, "Comet's internal decoys, concatenated", "DECOY_");

    @TempDir private Path directory;

    private Path pin(String text) throws IOException {
        return Files.writeString(
                directory.resolve("merged one.pin"), text, StandardCharsets.ISO_8859_1);
    }

    private static CometOutputException refused(Path file, PinDecoyConfiguration decoys) {
        CometOutputException refused =
                assertThrows(
                        CometOutputException.class,
                        () -> CometPinValidator.validateBeforePercolator(file, decoys));
        assertEquals(file, refused.file());
        return refused;
    }

    /** A target row naming a target protein and then a decoy one: what Comet writes, measured. */
    private static String mixedTarget(int scan) {
        return row(
                "run_" + scan + "_2_1",
                "1",
                Integer.toString(scan),
                "K.AHNSVNK.M",
                "sp|Q96T58|MINT_HUMAN",
                "DECOY_sp|Q06730|ZN33A_HUMAN");
    }

    /** A target row whose every protein carries the prefix: what Comet never writes. */
    private static String prefixedTarget(int scan) {
        return row(
                "run_" + scan + "_2_1",
                "1",
                Integer.toString(scan),
                "K.AHNSVNK.M",
                "DECOY_sp|Q96T58|MINT_HUMAN",
                "DECOY_sp|Q06730|ZN33A_HUMAN");
    }

    @Test
    @DisplayName("a consistent PIN, mixed target rows included, gives the summary")
    void consistent() throws IOException {
        Path file = pin(lines(HEADER, target(10), decoy(10), mixedTarget(11), target(12)));

        assertEquals(
                new PinSummary(file, PinText.FEATURES, 3, 1),
                CometPinValidator.validateBeforePercolator(file, CONCATENATED));
    }

    @Test
    @DisplayName("rule 1: not one decoy row carries the configured prefix")
    void prefixMarksNoDecoy() throws IOException {
        Path file = pin(lines(HEADER, target(10), decoy(10), decoy(11)));
        PinDecoyConfiguration reversed =
                new PinDecoyConfiguration(0, "decoys supplied in the FASTA", "REV_");

        assertEquals(
                "the PIN file "
                        + file
                        + " holds 2 decoy rows (Label -1) and not one of them names a protein"
                        + " beginning with the decoy prefix \"REV_\", so that prefix is not the one"
                        + " that marked these decoys; the decoy configuration was decoy_search = 0"
                        + " (decoys supplied in the FASTA), decoy_prefix = \"REV_\"",
                refused(file, reversed).getMessage());
    }

    @Test
    @DisplayName("rule 1 is about the file, not each row: one decoy carrying the prefix is enough")
    void oneDecoyCarryingThePrefixIsEnough() throws IOException {
        String unprefixedDecoy =
                row("run_12_3_1", "-1", "12", "K.EWFAK.S", "sp|Q06730|ZN33A_HUMAN");
        Path file = pin(lines(HEADER, target(10), unprefixedDecoy, decoy(11)));

        assertEquals(
                new PinSummary(file, PinText.FEATURES, 1, 2),
                CometPinValidator.validateBeforePercolator(file, CONCATENATED));
    }

    @Test
    @DisplayName("rule 2: a target row every protein of which carries the prefix, with its line")
    void targetOnlyPrefixed() throws IOException {
        Path file =
                pin(
                        lines(
                                HEADER,
                                target(10),
                                decoy(10),
                                prefixedTarget(11),
                                mixedTarget(12),
                                prefixedTarget(13)));

        assertEquals(
                "the PIN file "
                        + file
                        + " holds 2 target rows (Label 1) every protein of which begins with the"
                        + " decoy prefix \"DECOY_\", the first at line 4; Comet labels a match a"
                        + " decoy when every protein it names carries the prefix, so this file was"
                        + " not written with this prefix; the decoy configuration was"
                        + " decoy_search = 1 (Comet's internal decoys, concatenated), decoy_prefix"
                        + " = \"DECOY_\"",
                refused(file, CONCATENATED).getMessage());
    }

    @Test
    @DisplayName("R-DEC-04 comes first, with validate's own message")
    void zeroDecoysFirst() throws IOException {
        Path file = pin(lines(HEADER, prefixedTarget(10), target(11)));

        assertEquals(
                "the PIN file "
                        + file
                        + " holds 2 target rows and no decoy row (Label -1), so Percolator would"
                        + " have no negative examples; the decoy configuration was decoy_search ="
                        + " 1 (Comet's internal decoys, concatenated), decoy_prefix = \"DECOY_\"",
                refused(file, CONCATENATED).getMessage());
    }

    @Test
    @DisplayName("the structural rules are validate's, with validate's messages")
    void structure() throws IOException {
        Path file = pin(lines(HEADER, target(10), "x", decoy(10)));

        assertEquals(
                "the PIN file " + file + " line 3 has 1 field where the header has 28 columns",
                refused(file, CONCATENATED).getMessage());
    }

    @Test
    @DisplayName("validate itself is unchanged: it never judges the prefix")
    void validateIgnoresThePrefix() throws IOException {
        Path file = pin(lines(HEADER, target(10), decoy(10), prefixedTarget(11)));
        PinDecoyConfiguration reversed =
                new PinDecoyConfiguration(0, "decoys supplied in the FASTA", "REV_");

        assertAll(
                () ->
                        assertEquals(
                                new PinSummary(file, PinText.FEATURES, 2, 1),
                                CometPinValidator.validate(file, reversed)),
                () ->
                        assertEquals(
                                new PinSummary(file, PinText.FEATURES, 2, 1),
                                CometPinValidator.validate(file, CONCATENATED)));
    }

    @Test
    @DisplayName("the reader's prefix counts, row by row, and none without a prefix")
    void readerCounts() throws IOException {
        Path file =
                pin(
                        lines(
                                HEADER,
                                target(10),
                                decoy(10),
                                mixedTarget(11),
                                prefixedTarget(12),
                                decoy(12),
                                row("run_13_3_1", "-1", "13", "K.EWFAK.S", "sp|P1|A_HUMAN")));
        try (PinReader counting = PinReader.open(file, "DECOY_");
                PinReader plain = PinReader.open(file)) {
            while (counting.nextRow() != null) {
                // count
            }
            while (plain.nextRow() != null) {
                // count
            }
            assertAll(
                    () -> assertEquals(2, counting.decoysCarryingPrefix()),
                    () -> assertEquals(1, counting.targetsOnlyPrefixed()),
                    () -> assertEquals(5, counting.firstTargetOnlyPrefixedLine()),
                    () -> assertEquals(3, counting.targets()),
                    () -> assertEquals(3, counting.decoys()),
                    () -> assertEquals(0, plain.decoysCarryingPrefix()),
                    () -> assertEquals(0, plain.targetsOnlyPrefixed()),
                    () -> assertEquals(0, plain.firstTargetOnlyPrefixedLine()));
        }
    }

    @Test
    @DisplayName("an empty prefix marks nothing and is refused; nulls are refused")
    void badPrefix() throws IOException {
        Path file = pin(lines(HEADER, target(10), decoy(10)));

        assertAll(
                () ->
                        assertEquals(
                                "an empty decoy prefix begins every protein name, so it marks"
                                        + " nothing",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () -> PinReader.open(file, ""))
                                        .getMessage()),
                () ->
                        assertThrows(
                                NullPointerException.class,
                                () -> PinReader.open(file, Nulls.of(String.class))),
                () ->
                        assertThrows(
                                NullPointerException.class,
                                () ->
                                        CometPinValidator.validateBeforePercolator(
                                                Nulls.of(Path.class), CONCATENATED)),
                () ->
                        assertThrows(
                                NullPointerException.class,
                                () ->
                                        CometPinValidator.validateBeforePercolator(
                                                file, Nulls.of(PinDecoyConfiguration.class))));
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    @DisplayName("the file is closed after a pass and after each prefix refusal")
    void closesTheFile() throws IOException {
        Path valid =
                Files.writeString(
                        directory.resolve("valid.pin"), lines(HEADER, target(1), decoy(1)));
        Path prefixed =
                Files.writeString(
                        directory.resolve("prefixed.pin"),
                        lines(HEADER, prefixedTarget(1), decoy(1)));
        assertEquals(2, CometPinValidator.validateBeforePercolator(valid, CONCATENATED).rows());
        refused(prefixed, CONCATENATED);
        assertEquals(List.of(), OpenFiles.to(valid, prefixed));
    }
}
