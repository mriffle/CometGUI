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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import org.cometgui.tools.testing.Nulls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * {@link CometPinValidator} on constructed PIN files: every failure mode names the file, and the
 * {@code R-DEC-04} messages are hand-typed.
 */
class CometPinValidatorTest {

    static final PinDecoyConfiguration CONCATENATED =
            new PinDecoyConfiguration(1, "Comet's internal decoys, concatenated", "DECOY_");

    @TempDir private Path directory;

    private Path pin(String text) throws IOException {
        return Files.writeString(
                directory.resolve("k562 3.pin"), text, StandardCharsets.ISO_8859_1);
    }

    private CometOutputException refused(Path file) {
        CometOutputException refused =
                assertThrows(
                        CometOutputException.class,
                        () -> CometPinValidator.validate(file, CONCATENATED));
        assertEquals(file, refused.file());
        return refused;
    }

    @Test
    @DisplayName("a valid PIN: feature columns, target and decoy counts, extra protein fields")
    void valid() throws IOException {
        Path file = pin(lines(HEADER, target(10), decoy(10), target(11), target(12)));
        PinSummary summary = CometPinValidator.validate(file, CONCATENATED);
        assertEquals(new PinSummary(file, PinText.FEATURES, 3, 1), summary);
        assertEquals(4, summary.rows());
        assertEquals("ExpMass", summary.featureColumns().get(0));
        assertEquals("absdM", summary.featureColumns().get(22));
        assertEquals(23, summary.featureColumns().size());
    }

    @Test
    @DisplayName("CRLF line endings read as their LF form")
    void crlf() throws IOException {
        Path file = pin(lines(HEADER, target(10), decoy(10)).replace("\n", "\r\n"));
        assertEquals(
                new PinSummary(file, PinText.FEATURES, 1, 1),
                CometPinValidator.validate(file, CONCATENATED));
    }

    @Test
    @DisplayName("R-DEC-04: no decoy row fails naming the decoy configuration")
    void noDecoys() throws IOException {
        Path file = pin(lines(HEADER, target(10), target(11)));
        assertEquals(
                "the PIN file "
                        + file
                        + " holds 2 target rows and no decoy row (Label -1), so Percolator would"
                        + " have no negative examples; the decoy configuration was decoy_search ="
                        + " 1 (Comet's internal decoys, concatenated), decoy_prefix = \"DECOY_\"",
                refused(file).getMessage());
    }

    @Test
    @DisplayName("R-DEC-04: no target row fails naming the decoy configuration")
    void noTargets() throws IOException {
        Path file = pin(lines(HEADER, decoy(10), decoy(11), decoy(12)));
        CometOutputException refused =
                assertThrows(
                        CometOutputException.class,
                        () ->
                                CometPinValidator.validate(
                                        file,
                                        new PinDecoyConfiguration(
                                                0, "no internal decoys", "REV_")));
        assertEquals(
                "the PIN file "
                        + file
                        + " holds 3 decoy rows and no target row (Label 1), so there is nothing"
                        + " for Percolator to score; the decoy configuration was decoy_search = 0"
                        + " (no internal decoys), decoy_prefix = \"REV_\"",
                refused.getMessage());
    }

    @Test
    @DisplayName("R-DEC-04: a header and no row fails naming the decoy configuration")
    void noRows() throws IOException {
        Path file = pin(lines(HEADER));
        assertEquals(
                "the PIN file "
                        + file
                        + " holds a header and no PSM row, so there are neither targets nor"
                        + " decoys for Percolator; the decoy configuration was decoy_search = 1"
                        + " (Comet's internal decoys, concatenated), decoy_prefix = \"DECOY_\"",
                refused(file).getMessage());
    }

    @Test
    @DisplayName("a missing PIN fails naming it")
    void missing() {
        Path file = directory.resolve("absent.pin");
        assertEquals(
                "the PIN file " + file + " does not exist: Comet did not write it",
                refused(file).getMessage());
    }

    @Test
    @DisplayName("a directory in the PIN's place fails naming it")
    void directory() throws IOException {
        Path file = Files.createDirectories(directory.resolve("dir.pin"));
        assertEquals(
                "the PIN file " + file + " is a directory, not a file", refused(file).getMessage());
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    @DisplayName("an unreadable PIN fails naming it")
    void unreadable() throws IOException {
        Path file = pin(lines(HEADER, target(1), decoy(1)));
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("---------"));
        try {
            assertEquals(
                    "the PIN file " + file + " cannot be read: " + file,
                    refused(file).getMessage());
        } finally {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        }
    }

    @Test
    @DisplayName("an empty PIN fails naming it")
    void empty() throws IOException {
        Path file = pin("");
        assertEquals(
                "the PIN file " + file + " is empty: Comet wrote no header",
                refused(file).getMessage());
    }

    @Test
    @DisplayName("a truncated PIN -- half a row, no terminator -- fails naming file and line")
    void truncated() throws IOException {
        String whole = lines(HEADER, target(10), decoy(10));
        Path file = pin(whole.substring(0, whole.length() - 30));
        assertEquals(
                "the PIN file "
                        + file
                        + " ends without a line terminator: line 3 is incomplete, so the file is"
                        + " truncated",
                refused(file).getMessage());
    }

    @Test
    @DisplayName("a complete last row without its LF is still a truncated file")
    void unterminatedButComplete() throws IOException {
        String whole = lines(HEADER, target(10), decoy(10));
        Path file = pin(whole.substring(0, whole.length() - 1));
        assertEquals(
                "the PIN file "
                        + file
                        + " ends without a line terminator: line 3 is incomplete, so the file is"
                        + " truncated",
                refused(file).getMessage());
    }

    @Test
    @DisplayName("a header cut short fails as truncated, line 1")
    void truncatedHeader() throws IOException {
        Path file = pin("SpecId\tLabel\tScanN");
        assertEquals(
                "the PIN file "
                        + file
                        + " ends without a line terminator: line 1 is incomplete, so the file is"
                        + " truncated",
                refused(file).getMessage());
    }

    @Test
    @DisplayName("a header breaking the column rule is not a PIN file")
    void badHeader() throws IOException {
        Path file = pin(lines("SpecId\tLabel\tScanNr\tPeptide\tProteins", target(1)));
        assertEquals(
                "the PIN file "
                        + file
                        + " is not a PIN file: its header has 5 columns, fewer than SpecId, Label,"
                        + " ScanNr, one feature, Peptide and Proteins",
                refused(file).getMessage());
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(
            delimiter = '|',
            value = {
                "short row|run_1_2_1\t1\t1\t3.5|has 4 fields where the header has 28 columns",
                "one field|x|has 1 field where the header has 28 columns",
                "empty SpecId|ROW:\t1\t5|has an empty SpecId",
                "label 0|ROW:a\t0\t5|has the Label \"0\", which is neither 1 nor -1",
                "label +1|ROW:a\t+1\t5|has the Label \"+1\", which is neither 1 nor -1",
                "scan -|ROW:a\t1\t-5|has the ScanNr \"-5\", which is not a scan number",
                "scan x|ROW:a\t1\t5x|has the ScanNr \"5x\", which is not a scan number",
            })
    @DisplayName("broken leading fields fail naming the file and the line")
    void brokenLeadingFields(String name, String spec, String problem) throws IOException {
        String bad;
        if (spec.startsWith("ROW:")) {
            String[] leading = spec.substring(4).split("\t", -1);
            bad = row(leading[0], leading[1], leading[2], "K.PEPTIDE.R", "sp|P1|ONE");
        } else {
            bad = spec;
        }
        Path file = pin(lines(HEADER, target(1), bad, decoy(2)));
        assertEquals("the PIN file " + file + " line 3 " + problem, refused(file).getMessage());
    }

    @ParameterizedTest(name = "\"{0}\"")
    @CsvSource(
            delimiter = '|',
            value = {"nan", "inf", "-inf", "1,5", "''", "1.0.0", "0x1p3", "1e", "-", "."})
    @DisplayName("a feature that is not a finite decimal fails naming the column")
    void badFeature(String value) throws IOException {
        String good = target(7);
        String[] fields = good.split("\t", -1);
        fields[9] = value; // Xcorr
        Path file = pin(lines(HEADER, String.join("\t", fields), decoy(2)));
        assertEquals(
                "the PIN file "
                        + file
                        + " line 2 has \""
                        + value
                        + "\" in the feature column Xcorr, which is not a finite decimal",
                refused(file).getMessage());
    }

    @Test
    @DisplayName("the last feature column is checked too")
    void lastFeatureChecked() throws IOException {
        String[] fields = target(7).split("\t", -1);
        fields[25] = "x"; // absdM
        Path file = pin(lines(HEADER, decoy(1), String.join("\t", fields)));
        assertEquals(
                "the PIN file "
                        + file
                        + " line 3 has \"x\" in the feature column absdM, which is not a finite"
                        + " decimal",
                refused(file).getMessage());
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(
            delimiter = '|',
            value = {
                "-12.5",
                "0",
                "7",
                ".5",
                "3.",
                "1e-05",
                "-2.5E+10",
                "0.000546",
            })
    @DisplayName("finite decimals in every form Comet's printf can write are accepted")
    void goodFeature(String value) throws IOException {
        String[] fields = target(7).split("\t", -1);
        fields[3] = value;
        Path file = pin(lines(HEADER, String.join("\t", fields), decoy(2)));
        assertEquals(2, CometPinValidator.validate(file, CONCATENATED).rows());
    }

    @Test
    @DisplayName("an empty Peptide, an empty first protein and an empty extra protein fail")
    void emptyTrailingFields() throws IOException {
        Path noPeptide = pin(lines(HEADER, row("a", "1", "1", "", "sp|P1|ONE"), decoy(1)));
        assertEquals(
                "the PIN file " + noPeptide + " line 2 has an empty Peptide",
                refused(noPeptide).getMessage());
        Path noProtein = pin(lines(HEADER, row("a", "1", "1", "K.P.R", ""), decoy(1)));
        assertEquals(
                "the PIN file " + noProtein + " line 2 has an empty protein in field 28",
                refused(noProtein).getMessage());
        Path trailingTab =
                pin(lines(HEADER, decoy(1), row("a", "1", "1", "K.P.R", "sp|P1|ONE", "")));
        assertEquals(
                "the PIN file " + trailingTab + " line 3 has an empty protein in field 29",
                refused(trailingTab).getMessage());
    }

    @Test
    @DisplayName("an empty line inside the file is a broken row")
    void emptyLine() throws IOException {
        Path file = pin(lines(HEADER, target(1), "", decoy(1)));
        assertEquals(
                "the PIN file " + file + " line 3 has 1 field where the header has 28 columns",
                refused(file).getMessage());
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    @DisplayName("the file is closed after a valid read, a refused header and a refused row")
    void closesTheFile() throws IOException {
        Path valid =
                Files.writeString(
                        directory.resolve("valid.pin"), lines(HEADER, target(1), decoy(1)));
        Path badHeader =
                Files.writeString(
                        directory.resolve("header.pin"), lines("SpecId\tLabel", target(1)));
        Path badRow = Files.writeString(directory.resolve("row.pin"), lines(HEADER, "x", decoy(1)));
        assertEquals(2, CometPinValidator.validate(valid, CONCATENATED).rows());
        refused(badHeader);
        refused(badRow);
        assertEquals(List.of(), OpenFiles.to(valid, badHeader, badRow));
    }

    @Test
    @DisplayName("a refused file is closed, and a failure to close it is kept, not thrown")
    void closedAfter() {
        Path file = TestPaths.absolute("x.pin");
        CometOutputException refused = new CometOutputException(file, "refused", null);
        int[] closes = {0};
        assertSame(refused, PinReader.closedAfter(() -> closes[0]++, refused));
        assertEquals(1, closes[0]);
        assertEquals(0, refused.getSuppressed().length);
        IOException failed = new IOException("close failed");
        assertSame(
                refused,
                PinReader.closedAfter(
                        () -> {
                            throw failed;
                        },
                        refused));
        assertEquals(List.of(failed), List.of(refused.getSuppressed()));
    }

    @Test
    @DisplayName("nulls are refused")
    void nulls() throws IOException {
        Path file = pin(lines(HEADER, target(1), decoy(1)));
        assertThrows(
                NullPointerException.class,
                () -> CometPinValidator.validate(Nulls.of(Path.class), CONCATENATED));
        assertThrows(
                NullPointerException.class,
                () -> CometPinValidator.validate(file, Nulls.of(PinDecoyConfiguration.class)));
    }

    @Test
    @DisplayName("the decoy configuration requires its words and its prefix")
    void decoyConfiguration() {
        assertEquals(
                "decoy_search = 2 (Comet's internal decoys, reported separately), decoy_prefix ="
                        + " \"XXX_\"",
                new PinDecoyConfiguration(2, "Comet's internal decoys, reported separately", "XXX_")
                        .describe());
        assertEquals(
                "the meaning of decoy_search must be given",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> new PinDecoyConfiguration(1, " ", "DECOY_"))
                        .getMessage());
        assertEquals(
                "the decoy prefix must not be blank",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> new PinDecoyConfiguration(1, "x", ""))
                        .getMessage());
    }

    @Test
    @DisplayName("a summary refuses negative counts and copies its columns")
    void summary() {
        Path file = TestPaths.absolute("x.pin");
        assertEquals(
                "row counts cannot be negative: -1 targets, 0 decoys",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> new PinSummary(file, List.of("a"), -1, 0))
                        .getMessage());
        assertEquals(
                "row counts cannot be negative: 0 targets, -1 decoys",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> new PinSummary(file, List.of("a"), 0, -1))
                        .getMessage());
        assertEquals(0, new PinSummary(file, List.of("a"), 0, 0).rows());
    }
}
