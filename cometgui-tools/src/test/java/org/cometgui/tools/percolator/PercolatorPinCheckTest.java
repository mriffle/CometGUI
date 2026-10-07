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

package org.cometgui.tools.percolator;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.cometgui.tools.comet.CometOutputException;
import org.cometgui.tools.comet.PinDecoyConfiguration;
import org.cometgui.tools.comet.PinSummary;
import org.cometgui.tools.testing.Nulls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The check before launch on CONSTRUCTED PIN files, each named edge case its own file and its own
 * hand-typed message. The real merged PIN and the real zero-decoy PIN are graded in {@link
 * PercolatorCommandRealBinaryTest}.
 */
class PercolatorPinCheckTest {

    /** The decoy configuration SyntheticPin's prefix matches. */
    private static final PinDecoyConfiguration SYNTHETIC =
            new PinDecoyConfiguration(
                    1, "Comet's internal decoys, concatenated", SyntheticPin.DECOY_PROTEIN_PREFIX);

    private static final String HEADER =
            "SpecId\tLabel\tScanNr\tExpMass\tCalcMass\tfeat1\tPeptide\tProteins\n";

    @TempDir private Path directory;

    private Path write(String name, String text) throws IOException {
        return Files.writeString(directory.resolve(name), text, StandardCharsets.ISO_8859_1);
    }

    private static PercolatorRefusedException refused(Path pin) {
        PercolatorRefusedException refused =
                assertThrows(
                        PercolatorRefusedException.class,
                        () -> PercolatorPinCheck.check(pin, SYNTHETIC));
        assertEquals(Optional.of(pin), refused.file(), "every refusal names the file");
        return refused;
    }

    @Test
    @DisplayName("the probe's own 64 + 64 fixture passes, with its features and counts")
    void valid() throws IOException, PercolatorRefusedException {
        Path pin = SyntheticPin.writeForCapabilityProbe(directory);

        assertEquals(
                new PinSummary(
                        pin, List.of("ExpMass", "CalcMass", "feat1", "feat2", "feat3"), 64, 64),
                PercolatorPinCheck.check(pin, SYNTHETIC));
    }

    @Test
    @DisplayName("a missing file: its own message, and Percolator was not started")
    void missing() {
        Path pin = directory.resolve("merged.pin");

        assertEquals(
                "Percolator was not started: the merged PIN file " + pin + " does not exist",
                refused(pin).getMessage());
    }

    @Test
    @DisplayName("a directory where the file should be")
    void directory() throws IOException {
        Path pin = Files.createDirectory(directory.resolve("merged.pin"));

        assertEquals(
                "Percolator was not started: the merged PIN file " + pin + " is not a regular file",
                refused(pin).getMessage());
    }

    @Test
    @DisplayName("a dangling symbolic link is not a regular file")
    void danglingLink() throws IOException {
        Path pin =
                Files.createSymbolicLink(
                        directory.resolve("merged.pin"), directory.resolve("gone.pin"));

        assertEquals(
                "Percolator was not started: the merged PIN file " + pin + " is not a regular file",
                refused(pin).getMessage());
    }

    @Test
    @DisplayName("an empty file: its own message")
    void empty() throws IOException {
        Path pin = write("merged.pin", "");

        assertEquals(
                "Percolator was not started: the merged PIN file "
                        + pin
                        + " is empty (0 bytes): it has not even a header",
                refused(pin).getMessage());
    }

    @Test
    @DisplayName("a header and no row: R-DEC-04's message, naming the configuration")
    void headerOnly() throws IOException {
        Path pin = write("merged.pin", HEADER);

        assertEquals(
                "Percolator was not started: the PIN file "
                        + pin
                        + " holds a header and no PSM row, so there are neither targets nor"
                        + " decoys for Percolator; the decoy configuration was decoy_search = 1"
                        + " (Comet's internal decoys, concatenated), decoy_prefix = \"decoy_\"",
                refused(pin).getMessage());
    }

    @Test
    @DisplayName("a required header field missing: ScanNr")
    void missingHeaderField() throws IOException {
        Path pin =
                write(
                        "merged.pin",
                        "SpecId\tLabel\tExpMass\tfeat1\tPeptide\tProteins\n"
                                + "a\t1\t1.0\t2.0\tK.PEPTIDE.K\tp1\n");

        assertEquals(
                "Percolator was not started: the PIN file "
                        + pin
                        + " is not a PIN file: its header begins [SpecId, Label, ExpMass] where a"
                        + " PIN header begins [SpecId, Label, ScanNr]",
                refused(pin).getMessage());
    }

    @Test
    @DisplayName("a feature that does not parse as a number, named with its column and line")
    void nonNumericFeature() throws IOException {
        Path pin =
                write(
                        "merged.pin",
                        HEADER
                                + "t1\t1\t1\t1000.5\t1000.4\t0.25\tK.PEPTIDE.K\tp1\n"
                                + "d1\t-1\t2\t1000.5\t1000.4\tNaN\tK.EDITPEP.K\tdecoy_p1\n");

        assertEquals(
                "Percolator was not started: the PIN file "
                        + pin
                        + " line 3 has \"NaN\" in the feature column feat1, which is not a finite"
                        + " decimal",
                refused(pin).getMessage());
    }

    @Test
    @DisplayName("R-DEC-04: targets and no decoy, naming the decoy configuration")
    void zeroDecoys() throws IOException {
        Path pin =
                write(
                        "merged.pin",
                        HEADER
                                + "t1\t1\t1\t1000.5\t1000.4\t0.25\tK.PEPTIDE.K\tp1\n"
                                + "t2\t1\t2\t1000.5\t1000.4\t0.5\tK.EDITPEP.K\tp2\n");
        PercolatorRefusedException refused = refused(pin);

        assertAll(
                () ->
                        assertEquals(
                                "Percolator was not started: the PIN file "
                                        + pin
                                        + " holds 2 target rows and no decoy row (Label -1), so"
                                        + " Percolator would have no negative examples; the decoy"
                                        + " configuration was decoy_search = 1 (Comet's internal"
                                        + " decoys, concatenated), decoy_prefix = \"decoy_\"",
                                refused.getMessage()),
                () -> assertInstanceOf(CometOutputException.class, refused.getCause()));
    }

    @Test
    @DisplayName("R-DEC-04: decoys and no target")
    void zeroTargets() throws IOException {
        Path pin =
                write(
                        "merged.pin",
                        HEADER + "d1\t-1\t2\t1000.5\t1000.4\t0.5\tK.EDITPEP.K\tdecoy_p2\n");

        assertEquals(
                "Percolator was not started: the PIN file "
                        + pin
                        + " holds 1 decoy rows and no target row (Label 1), so there is nothing"
                        + " for Percolator to score; the decoy configuration was decoy_search = 1"
                        + " (Comet's internal decoys, concatenated), decoy_prefix = \"decoy_\"",
                refused(pin).getMessage());
    }

    @Test
    @DisplayName("the prefix: decoys marked by a prefix other than the configured one")
    void otherPrefix() throws IOException {
        Path pin =
                write(
                        "merged.pin",
                        HEADER
                                + "t1\t1\t1\t1000.5\t1000.4\t0.25\tK.PEPTIDE.K\tp1\n"
                                + "d1\t-1\t2\t1000.5\t1000.4\t0.5\tK.EDITPEP.K\tREV_p2\n");

        assertEquals(
                "Percolator was not started: the PIN file "
                        + pin
                        + " holds 1 decoy rows (Label -1) and not one of them names a protein"
                        + " beginning with the decoy prefix \"decoy_\", so that prefix is not the"
                        + " one that marked these decoys; the decoy configuration was decoy_search"
                        + " = 1 (Comet's internal decoys, concatenated), decoy_prefix = \"decoy_\"",
                refused(pin).getMessage());
    }

    @Test
    @DisplayName("the prefix: a target row naming only prefixed proteins")
    void prefixedTarget() throws IOException {
        Path pin =
                write(
                        "merged.pin",
                        HEADER
                                + "t1\t1\t1\t1000.5\t1000.4\t0.25\tK.PEPTIDE.K\tdecoy_p1\n"
                                + "d1\t-1\t2\t1000.5\t1000.4\t0.5\tK.EDITPEP.K\tdecoy_p2\n");

        assertEquals(
                "Percolator was not started: the PIN file "
                        + pin
                        + " holds 1 target rows (Label 1) every protein of which begins with the"
                        + " decoy prefix \"decoy_\", the first at line 2; Comet labels a match a"
                        + " decoy when every protein it names carries the prefix, so this file was"
                        + " not written with this prefix; the decoy configuration was"
                        + " decoy_search = 1 (Comet's internal decoys, concatenated), decoy_prefix"
                        + " = \"decoy_\"",
                refused(pin).getMessage());
    }

    @Test
    @DisplayName("nulls are refused")
    void nulls() throws IOException {
        Path pin = SyntheticPin.writeForCapabilityProbe(directory);

        assertAll(
                () ->
                        assertThrows(
                                NullPointerException.class,
                                () -> PercolatorPinCheck.check(Nulls.of(Path.class), SYNTHETIC)),
                () ->
                        assertThrows(
                                NullPointerException.class,
                                () ->
                                        PercolatorPinCheck.check(
                                                pin, Nulls.of(PinDecoyConfiguration.class))));
    }
}
