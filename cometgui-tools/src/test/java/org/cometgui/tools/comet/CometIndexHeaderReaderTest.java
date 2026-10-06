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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.Stream;
import org.cometgui.domain.params.CometIndexDescription;
import org.cometgui.domain.params.CometIndexDescription.Enzyme;
import org.cometgui.domain.params.CometIndexDescription.VariableMod;
import org.cometgui.domain.run.IndexMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * {@link CometIndexHeaderReader} on the REAL headers both pinned Comet binaries wrote ({@link
 * IndexHeaders}), field by field, and on CONSTRUCTED damage of them, each refused with its own
 * message naming the file.
 */
class CometIndexHeaderReaderTest {

    private static final Path FILE = Path.of("cache", "subset.fasta.idx");

    private static final String PREFIX = "the index file " + FILE + " ";

    private static final String CANNOT = PREFIX + "has a header CometGUI cannot read: ";

    static Stream<Arguments> captures() {
        return Stream.of(
                Arguments.of(IndexHeaders.NEWER, IndexMode.FRAGMENT_ION),
                Arguments.of(IndexHeaders.NEWER, IndexMode.PEPTIDE),
                Arguments.of(IndexHeaders.OLDER, IndexMode.FRAGMENT_ION),
                Arguments.of(IndexHeaders.OLDER, IndexMode.PEPTIDE));
    }

    @ParameterizedTest(name = "Comet {0}, {1}")
    @MethodSource("captures")
    @DisplayName("each real capture is read field by field into what its bytes say")
    void realCaptures(String release, IndexMode mode) throws IOException {
        CometIndexDescription read =
                CometIndexHeaderReader.parse(FILE, IndexHeaders.bytes(release, mode));
        CometIndexDescription expected = IndexHeaders.expected(release, FILE, mode);
        assertEquals(expected.file(), read.file());
        assertEquals(expected.formatVersion(), read.formatVersion());
        assertEquals(expected.firstLine(), read.firstLine());
        assertEquals(expected.cometVersion(), read.cometVersion());
        assertEquals(mode, read.type());
        assertEquals(Optional.of("subset.fasta"), read.inputDatabase());
        assertEquals(expected.massRange(), read.massRange());
        assertEquals("600.000000", read.massRange().low().toPlainString());
        assertEquals(expected.lengthRange(), read.lengthRange());
        assertEquals(1, read.parentMassType());
        assertEquals(1, read.fragmentMassType());
        assertEquals(0, read.decoySearch());
        assertEquals(expected.decoyPrefix(), read.decoyPrefix());
        assertEquals(new Enzyme("Trypsin", 1, "KR", "P"), read.enzyme());
        assertEquals(new Enzyme("Cut_everywhere", 0, "-", "-"), read.secondEnzyme());
        assertEquals(expected.enzymeTermini(), read.enzymeTermini());
        assertEquals(expected.missedCleavages(), read.missedCleavages());
        assertEquals(expected.clipNtermMethionine(), read.clipNtermMethionine());
        assertEquals(129327, read.peptides());
        assertEquals(expected.staticMods(), read.staticMods());
        assertEquals(expected.variableMods(), read.variableMods());
        assertEquals(false, read.proteinModList());
        assertEquals(0, read.requireVariableMod());
        assertEquals(5, read.maxVariableModsInPeptide());
        assertEquals(expected, read);
    }

    @ParameterizedTest(name = "Comet {0}")
    @ValueSource(strings = {IndexHeaders.NEWER, IndexHeaders.OLDER})
    @DisplayName("each real modifications capture is read into what its bytes say")
    void realModsCaptures(String release) throws IOException {
        CometIndexDescription read =
                CometIndexHeaderReader.parse(FILE, IndexHeaders.bytes(release, IndexHeaders.MODS));
        assertEquals(IndexHeaders.mods(release, FILE), read);
        assertEquals(8, read.requireVariableMod());
        assertEquals("1.500000", read.staticMods().get(26).toPlainString());
        assertEquals(
                List.of("MW", "-", "STY", IndexHeaders.NEWER.equals(release) ? "^" : "n", "X"),
                read.variableMods().stream().map(VariableMod::residues).toList());
    }

    @Test
    @DisplayName("the two formats differ exactly where the releases do")
    void formatsDiffer() throws IOException {
        CometIndexDescription newer =
                CometIndexHeaderReader.parse(
                        FILE, IndexHeaders.bytes(IndexHeaders.NEWER, IndexMode.FRAGMENT_ION));
        CometIndexDescription older =
                CometIndexHeaderReader.parse(
                        FILE, IndexHeaders.bytes(IndexHeaders.OLDER, IndexMode.FRAGMENT_ION));
        assertEquals(5, newer.formatVersion());
        assertEquals(4, older.formatVersion());
        assertEquals(Optional.of("DECOY_"), newer.decoyPrefix());
        assertEquals(Optional.empty(), older.decoyPrefix());
        assertEquals(OptionalInt.of(2), newer.enzymeTermini());
        assertEquals(OptionalInt.empty(), older.enzymeTermini());
        assertEquals(OptionalInt.of(-1), newer.variableMods().get(0).terminalDistance());
        assertEquals(OptionalInt.empty(), older.variableMods().get(0).terminalDistance());
    }

    @Test
    @DisplayName("read() takes the header from a whole file and stops at its empty line")
    void readsAFile(@TempDir Path directory) throws IOException {
        byte[] header = IndexHeaders.bytes(IndexHeaders.NEWER, IndexMode.PEPTIDE);
        ByteArrayOutputStream file = new ByteArrayOutputStream();
        file.write(header);
        byte[] proteins = new byte[200_000];
        byte[] name = "sp|P02769|ALBU_BOVIN".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(name, 0, proteins, 0, name.length);
        file.write(proteins);
        Path index = directory.resolve("whole.idx");
        Files.write(index, file.toByteArray());
        assertEquals(
                IndexHeaders.newer(index, IndexMode.PEPTIDE), CometIndexHeaderReader.read(index));
    }

    private static String text(String release, IndexMode mode) {
        return new String(IndexHeaders.bytes(release, mode), StandardCharsets.ISO_8859_1);
    }

    private static String newer() {
        return text(IndexHeaders.NEWER, IndexMode.FRAGMENT_ION);
    }

    private static CometIndexDescription parse(String header) throws CometIndexHeaderException {
        return CometIndexHeaderReader.parse(FILE, header.getBytes(StandardCharsets.ISO_8859_1));
    }

    private static String refusal(String header) {
        return assertThrows(CometIndexHeaderException.class, () -> parse(header)).getMessage();
    }

    private static String replaced(String line, String by) {
        String header = newer();
        assertTrue(header.contains(line + "\n"), line);
        return header.replace(line + "\n", by);
    }

    @Test
    @DisplayName("CRLF line endings read as LF ones")
    void crlf() throws IOException {
        assertEquals(parse(newer()), parse(newer().replace("\n", "\r\n")), "CRLF header");
    }

    @Test
    @DisplayName(
            "a header ending the bytes without a further line is still ended by its empty line")
    void endsExactly() throws IOException {
        String header = newer();
        assertTrue(header.endsWith("MaxVariableModsInPeptide: 5\n\n"));
        assertEquals(5, parse(header).maxVariableModsInPeptide());
    }

    @Test
    @DisplayName("an index written before the versioned format is refused, naming its first line")
    void legacyIndex() {
        String legacy =
                "Comet peptide index.  Comet version 2024.01 rev. 0 (f00df0c)\n"
                        + "InputDB:  subset.fasta\nMassRange: 600.000000 5000.000000\n";
        assertEquals(
                PREFIX
                        + "is not a versioned Comet index: its first line is \"Comet peptide"
                        + " index.  Comet version 2024.01 rev. 0 (f00df0c)\", and a versioned"
                        + " Comet index begins \"Comet index database v<N>.  Comet version"
                        + " <release>\"; rebuild the index from its FASTA",
                refusal(legacy));
    }

    @Test
    @DisplayName("a FASTA is not an index")
    void fasta() {
        assertEquals(
                PREFIX
                        + "is not a versioned Comet index: its first line is"
                        + " \">sp|P02769|ALBU_BOVIN Albumin\", and a versioned Comet index begins"
                        + " \"Comet index database v<N>.  Comet version <release>\"; rebuild the"
                        + " index from its FASTA",
                refusal(">sp|P02769|ALBU_BOVIN Albumin\nMKWVTFISLLLLFSSAYS\n"));
    }

    @Test
    @DisplayName("bytes with no line feed at all are judged by what they begin with")
    void noLineFeed() {
        byte[] binary = new byte[100];
        Arrays.fill(binary, (byte) 'x');
        binary[3] = 0;
        assertEquals(
                PREFIX
                        + "is not a versioned Comet index: its first line is \"xxx?"
                        + "x".repeat(76)
                        + "...\", and a versioned Comet index begins \"Comet index database"
                        + " v<N>.  Comet version <release>\"; rebuild the index from its FASTA",
                assertThrows(
                                CometIndexHeaderException.class,
                                () -> CometIndexHeaderReader.parse(FILE, binary))
                        .getMessage());
    }

    @Test
    @DisplayName("an empty file is refused")
    void empty() {
        assertEquals(
                PREFIX + "is empty, not a Comet index",
                assertThrows(
                                CometIndexHeaderException.class,
                                () -> CometIndexHeaderReader.parse(FILE, new byte[0]))
                        .getMessage());
    }

    @Test
    @DisplayName("an empty first line is not a versioned index's")
    void emptyFirstLine() {
        assertTrue(refusal("\n" + newer()).startsWith(PREFIX + "is not a versioned Comet index"));
    }

    @Test
    @DisplayName("a header with no empty line within the limit is refused")
    void unended() {
        String header = newer().replace("MaxVariableModsInPeptide: 5\n\n", "");
        StringBuilder longer = new StringBuilder(header);
        while (longer.length() < CometIndexHeaderReader.HEADER_LIMIT) {
            longer.append("NumPeptides: 1\n");
        }
        assertEquals(
                CANNOT + "it has no empty line ending it within the first 65536 bytes",
                refusal(header));
        byte[] bytes = longer.toString().getBytes(StandardCharsets.ISO_8859_1);
        byte[] limited = Arrays.copyOf(bytes, CometIndexHeaderReader.HEADER_LIMIT);
        assertEquals(
                CANNOT + "it has no empty line ending it within the first 65536 bytes",
                assertThrows(
                                CometIndexHeaderException.class,
                                () -> CometIndexHeaderReader.parse(FILE, limited))
                        .getMessage());
    }

    /** The real header with its InputDB line padded to make the whole header a given length. */
    private static String padded(int length) {
        String header = newer();
        String padding = "x".repeat(length - header.length());
        return header.replace(
                "InputDB:  subset.fasta\n", "InputDB:  subset.fasta" + padding + "\n");
    }

    @Test
    @DisplayName(
            "read() reads no further than the limit: an empty line one byte beyond it is not seen")
    void readsOnlyTheLimit(@TempDir Path directory) throws IOException {
        Path fits = directory.resolve("fits.idx");
        Files.writeString(
                fits,
                padded(CometIndexHeaderReader.HEADER_LIMIT) + "protein names",
                StandardCharsets.ISO_8859_1);
        assertEquals(
                CometIndexHeaderReader.HEADER_LIMIT - newer().length() + "subset.fasta".length(),
                CometIndexHeaderReader.read(fits).inputDatabase().orElseThrow().length());
        Path beyond = directory.resolve("beyond.idx");
        Files.writeString(
                beyond,
                padded(CometIndexHeaderReader.HEADER_LIMIT + 1) + "protein names",
                StandardCharsets.ISO_8859_1);
        assertEquals(
                "the index file "
                        + beyond
                        + " has a header CometGUI cannot read: it has no empty line ending it"
                        + " within the first 65536 bytes",
                assertThrows(
                                CometIndexHeaderException.class,
                                () -> CometIndexHeaderReader.read(beyond))
                        .getMessage());
    }

    @Test
    @DisplayName("a NUL byte inside the header is refused, naming the line")
    void nul() {
        assertEquals(
                CANNOT
                        + "line 4 holds a NUL byte, before the empty line that ends a Comet index"
                        + " header",
                refusal(replaced("MassRange: 600.000000 5000.000000", "MassRange: 600\0\n")));
    }

    static Stream<Arguments> damaged() {
        return Stream.of(
                Arguments.of(
                        "not a key",
                        "LengthRange: 5 50",
                        "LengthRange 5 50\n",
                        "line 5, \"LengthRange 5 50\", is not a \"Key: value\" header line"),
                Arguments.of(
                        "an unknown key",
                        "ClipNtermMethionine: 0",
                        "ClipNtermMethionine: 0\nPeptideMassCap: 9\n",
                        "line 14 records \"PeptideMassCap:\", which this reader does not know,"
                                + " so CometGUI cannot check it against the search"),
                Arguments.of(
                        "a key twice",
                        "DecoySearch: 0",
                        "DecoySearch: 0\nDecoySearch: 1\n",
                        "line 8 records \"DecoySearch:\" a second time"),
                Arguments.of(
                        "a required key missing",
                        "MassType: 1 1",
                        "",
                        "has no \"MassType:\" line in its header"),
                Arguments.of(
                        "an unknown index type",
                        "IndexSearchType: fragment ion index",
                        "IndexSearchType: spectral index\n",
                        "\"IndexSearchType: spectral index\" is not \"fragment ion index\" or"
                                + " \"peptide index\", which is what Comet writes there"),
                Arguments.of(
                        "one mass",
                        "MassRange: 600.000000 5000.000000",
                        "MassRange: 600.000000\n",
                        "\"MassRange: 600.000000\" is not 2 decimal number(s), which is what"
                                + " Comet writes there"),
                Arguments.of(
                        "a mass that is no number",
                        "MassRange: 600.000000 5000.000000",
                        "MassRange: 600.000000 inf\n",
                        "\"MassRange: 600.000000 inf\" is not 2 decimal number(s), which is what"
                                + " Comet writes there"),
                Arguments.of(
                        "a length that is a decimal",
                        "LengthRange: 5 50",
                        "LengthRange: 5 50.5\n",
                        "\"LengthRange: 5 50.5\" is not 2 integer(s), which is what Comet writes"
                                + " there"),
                Arguments.of(
                        "three lengths",
                        "LengthRange: 5 50",
                        "LengthRange: 5 50 7\n",
                        "\"LengthRange: 5 50 7\" is not 2 integer(s), which is what Comet writes"
                                + " there"),
                Arguments.of(
                        "no decoy search",
                        "DecoySearch: 0",
                        "DecoySearch:\n",
                        "\"DecoySearch:\" is not 1 integer(s), which is what Comet writes there"),
                Arguments.of(
                        "a clip flag of 2",
                        "ClipNtermMethionine: 0",
                        "ClipNtermMethionine: 2\n",
                        "\"ClipNtermMethionine: 2\" is not 0 or 1, which is what Comet writes"
                                + " there"),
                Arguments.of(
                        "a protein list flag of -1",
                        "ProteinModList: 0",
                        "ProteinModList: -1\n",
                        "\"ProteinModList: -1\" is not 0 or 1, which is what Comet writes there"),
                Arguments.of(
                        "a negative peptide count",
                        "NumPeptides: 129327",
                        "NumPeptides: -3\n",
                        "\"NumPeptides: -3\" is not a count, which is what Comet writes there"),
                Arguments.of(
                        "an enzyme without brackets",
                        "Enzyme: Trypsin [1 KR P]",
                        "Enzyme: Trypsin 1 KR P\n",
                        "\"Enzyme: Trypsin 1 KR P\" is not an enzyme written \"name [sense cut"
                                + " nocut]\", which is what Comet writes there"),
                Arguments.of(
                        "an enzyme sense that is no integer",
                        "Enzyme2: Cut_everywhere [0 - -]",
                        "Enzyme2: Cut_everywhere [x - -]\n",
                        "\"Enzyme2: Cut_everywhere [x - -]\" is not an enzyme written \"name"
                                + " [sense cut nocut]\", which is what Comet writes there"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("damaged")
    @DisplayName("damaged header lines are refused with their own message")
    void damagedLines(String what, String line, String by, String problem) {
        assertEquals(CANNOT + problem, refusal(replaced(line, by)));
    }

    private static String staticLine() {
        return newer().lines()
                .filter(line -> line.startsWith("StaticMod:"))
                .findFirst()
                .orElseThrow();
    }

    private static String variableLine() {
        return newer().lines()
                .filter(line -> line.startsWith("VariableMod:"))
                .findFirst()
                .orElseThrow();
    }

    @Test
    @DisplayName("the static modifications must number exactly thirty")
    void staticCount() {
        String line = staticLine();
        assertEquals(
                CANNOT
                        + "\"StaticMod:"
                        + line.substring("StaticMod:".length(), "StaticMod:".length() + 80)
                        + "...\" is not 30 decimal number(s), which is what Comet writes there",
                refusal(replaced(line, line.substring(0, line.lastIndexOf(' ')) + "\n")));
        assertTrue(
                refusal(replaced(line, line + " 0.000000\n"))
                        .endsWith("is not 30 decimal number(s), which is what Comet writes there"));
    }

    static Stream<Arguments> damagedSlots() {
        return Stream.of(
                Arguments.of("six fields", "M:15.994900:0.000000:0.000000:3:-1", 1),
                Arguments.of("four fields", "M:15.994900:0.000000:0.000000", 1),
                Arguments.of("no residues", ":15.994900:0.000000:0.000000:3:-1:0", 1),
                Arguments.of("a mass that is no number", "M:x:0.000000:0.000000:3:-1:0", 1),
                Arguments.of("a loss that is no number", "M:15.994900:x:0.000000:3:-1:0", 1),
                Arguments.of("a second loss that is no number", "M:15.994900:0.000000:x:3:-1:0", 1),
                Arguments.of(
                        "a count that is a decimal", "M:15.994900:0.000000:0.000000:3.0:-1:0", 1),
                Arguments.of(
                        "a distance that is no number", "M:15.994900:0.000000:0.000000:3:x:0", 1),
                Arguments.of(
                        "a terminus that is no number", "M:15.994900:0.000000:0.000000:3:-1:x", 1));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("damagedSlots")
    @DisplayName("a damaged variable-modification slot is refused, naming the slot")
    void damagedSlot(String what, String slot, int number) {
        String line = variableLine();
        String damaged = line.replace("M:15.994900:0.000000:0.000000:3:-1:0", slot);
        assertTrue(
                refusal(replaced(line, damaged + "\n"))
                        .endsWith(
                                "is not residues:mass:loss:loss2:max[:distance:terminus] in slot "
                                        + number
                                        + ", which is what Comet writes there"),
                what);
    }

    @Test
    @DisplayName("a slot after the first is named by its own number")
    void laterSlot() {
        String line = variableLine();
        String damaged = line.substring(0, line.lastIndexOf(' ')) + " X:0.0:0.0";
        assertTrue(
                refusal(replaced(line, damaged + "\n"))
                        .endsWith("in slot 5, which is what Comet writes there"));
    }

    @Test
    @DisplayName("an empty VariableMod line is refused")
    void noSlots() {
        assertEquals(
                CANNOT
                        + "\"VariableMod:\" is not one residues:mass:loss:loss2:max token per slot,"
                        + " which is what Comet writes there",
                refusal(replaced(variableLine(), "VariableMod:\n")));
    }

    @Test
    @DisplayName("RequireVariableMod must hold one value more than there are slots")
    void requirementCount() {
        assertEquals(
                CANNOT
                        + "\"RequireVariableMod: 0 0 0 0 0\" is not 6 integer(s), which is what"
                        + " Comet writes there",
                refusal(
                        replaced(
                                "RequireVariableMod: 0 0 0 0 0 0",
                                "RequireVariableMod: 0 0 0 0 0\n")));
        assertEquals(
                CANNOT
                        + "\"RequireVariableMod: 0 0 x 0 0 0\" is not 6 integer(s), which is what"
                        + " Comet writes there",
                refusal(
                        replaced(
                                "RequireVariableMod: 0 0 0 0 0 0",
                                "RequireVariableMod: 0 0 x 0 0 0\n")));
        assertEquals(
                CANNOT
                        + "\"RequireVariableMod: x 0 0 0 0 0\" is not 6 integer(s), which is what"
                        + " Comet writes there",
                refusal(
                        replaced(
                                "RequireVariableMod: 0 0 0 0 0 0",
                                "RequireVariableMod: x 0 0 0 0 0\n")));
    }

    @Test
    @DisplayName("requirements, flags, protein list and a recorded clip are read as written")
    void valuesAsWritten() throws IOException {
        String header =
                replaced("RequireVariableMod: 0 0 0 0 0 0", "RequireVariableMod: 3 1 0 -1 0 0\n")
                        .replace("ClipNtermMethionine: 0\n", "ClipNtermMethionine: 1\n")
                        .replace("ProteinModList: 0\n", "ProteinModList: 1\n")
                        .replace("DecoySearch: 0\n", "DecoySearch: 2\n")
                        .replace("MassType: 1 1\n", "MassType: 0 1\n");
        CometIndexDescription read = parse(header);
        assertEquals(3, read.requireVariableMod());
        assertEquals(
                List.of(1, 0, -1, 0, 0),
                read.variableMods().stream().map(VariableMod::requirement).toList());
        assertEquals(Optional.of(true), read.clipNtermMethionine());
        assertTrue(read.proteinModList());
        assertEquals(2, read.decoySearch());
        assertEquals(0, read.parentMassType());
        assertEquals(1, read.fragmentMassType());
    }

    @Test
    @DisplayName("optional lines may be absent, and an empty decoy prefix or input is none")
    void optionalLines() throws IOException {
        String header =
                newer().replace("InputDB:  subset.fasta\n", "")
                        .replace("DecoyPrefix: DECOY_\n", "DecoyPrefix: \n")
                        .replace("NumEnzymeTermini: 2\n", "")
                        .replace("AllowedMissedCleavage: 2\n", "")
                        .replace("ClipNtermMethionine: 0\n", "");
        CometIndexDescription read = parse(header);
        assertEquals(Optional.empty(), read.inputDatabase());
        assertEquals(Optional.empty(), read.decoyPrefix());
        assertEquals(OptionalInt.empty(), read.enzymeTermini());
        assertEquals(OptionalInt.empty(), read.missedCleavages());
        assertEquals(Optional.empty(), read.clipNtermMethionine());
        assertEquals(
                Optional.empty(),
                parse(newer().replace("InputDB:  subset.fasta", "InputDB:  ")).inputDatabase());
    }

    @Test
    @DisplayName("values are kept as written: a slot's mass and losses keep their digits")
    void decimalsAsWritten() throws IOException {
        String line = variableLine();
        String header =
                replaced(
                        line,
                        line.replace(
                                        "M:15.994900:0.000000:0.000000:3:-1:0",
                                        "STY:79.966331:97.976896:0.5:2:0:3")
                                + "\n");
        VariableMod slot = parse(header).variableMods().get(0);
        assertEquals(
                new VariableMod(
                        "STY",
                        new BigDecimal("79.966331"),
                        new BigDecimal("97.976896"),
                        new BigDecimal("0.5"),
                        2,
                        OptionalInt.of(0),
                        OptionalInt.of(3),
                        0),
                slot);
    }

    @Test
    @DisplayName("a missing file is refused naming it")
    void missing(@TempDir Path directory) {
        Path index = directory.resolve("absent.idx");
        CometIndexHeaderException refused =
                assertThrows(
                        CometIndexHeaderException.class, () -> CometIndexHeaderReader.read(index));
        assertEquals("the index file " + index + " does not exist", refused.getMessage());
        assertSame(index, refused.file());
        assertTrue(refused.getCause() instanceof NoSuchFileException);
    }

    @Test
    @DisplayName("a directory is refused naming it")
    void directory(@TempDir Path directory) {
        CometIndexHeaderException refused =
                assertThrows(
                        CometIndexHeaderException.class,
                        () -> CometIndexHeaderReader.read(directory));
        assertTrue(
                refused.getMessage()
                        .startsWith("the index file " + directory + " cannot be read: "),
                refused.getMessage());
    }

    @Test
    @EnabledOnOs(
            value = {OS.LINUX, OS.MAC},
            disabledReason = "POSIX permissions")
    @DisplayName("an unreadable file is refused naming it")
    void unreadable(@TempDir Path directory) throws IOException {
        Path index = directory.resolve("locked.idx");
        Files.write(index, IndexHeaders.bytes(IndexHeaders.NEWER, IndexMode.PEPTIDE));
        Files.setPosixFilePermissions(index, PosixFilePermissions.fromString("---------"));
        try {
            if (Files.isReadable(index)) {
                throw new AssertionError(
                        "the file system ignores permissions here (running as root?), so this"
                                + " test cannot make a file unreadable");
            }
            CometIndexHeaderException refused =
                    assertThrows(
                            CometIndexHeaderException.class,
                            () -> CometIndexHeaderReader.read(index));
            assertEquals(
                    "the index file " + index + " cannot be read: permission denied",
                    refused.getMessage());
            assertTrue(refused.getCause() instanceof AccessDeniedException);
        } finally {
            Files.setPosixFilePermissions(index, PosixFilePermissions.fromString("rw-------"));
        }
    }

    @Test
    @DisplayName("the first-line pattern takes the format number and the release text")
    void firstLine() {
        var matcher =
                CometIndexHeaderReader.FIRST_LINE.matcher(
                        "Comet index database v12.  Comet version 2027.01 rev. 3 (abc)");
        assertTrue(matcher.matches());
        assertEquals("12", matcher.group(1));
        assertEquals("2027.01 rev. 3 (abc)", matcher.group(2));
    }
}
