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

package org.cometgui.workflow.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.workflow.testing.Nulls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link InputValue}: every digest pinned by a hex literal computed outside Java, with
 * the exact command in the comment above it. {@code A} below is 64 {@code a} characters, and so on.
 */
class InputValueTest {

    @Nested
    @DisplayName("digests, recomputed by sha256sum")
    class Digests {

        @Test
        @DisplayName("a file list: '<sha256> <n>:<name>\\n' per file, in order")
        void files() {
            // printf '%s 11:k562_3.mzML\n%s 11:k562_4.mzML\n' $A $B | sha256sum
            assertEquals(
                    "fd17b1126873ec964e7c4b3749d69b870960da2519c03a670c9de8b2c2ca850b",
                    Scenario.baseline().get(InputKind.SPECTRUM_FILES).orElseThrow().digest());
        }

        @Test
        @DisplayName("the order of the files counts")
        void fileOrderCounts() {
            InputValue reversed =
                    InputValue.files(
                            List.of(
                                    Scenario.file("k562_4.mzML", Scenario.SHA_B),
                                    Scenario.file("k562_3.mzML", Scenario.SHA_A)));
            // printf '%s 11:k562_4.mzML\n%s 11:k562_3.mzML\n' $B $A | sha256sum
            assertEquals(
                    "20384106612970d2f0fe0b5e8c8e9f48612f699c2a8017f0079e256a42f7e53e",
                    reversed.digest());
        }

        @Test
        @DisplayName("a single file, with its name's length in UTF-8 bytes")
        void singleFile() {
            // printf '%s 18:uniprot-1000.fasta\n' $C | sha256sum
            assertEquals(
                    "9579c2ff0817c9c8d5e00f42fc53f3177351c61593a37e76c572ad08db801728",
                    InputValue.file("uniprot-1000.fasta", Scenario.hashes(Scenario.SHA_C))
                            .digest());
            // printf '%s 6:\xc3\xbc.mgf\n' $A | sha256sum   (u-umlaut is two UTF-8 bytes)
            assertEquals(
                    "42834314c660e39d2ec62fdb12e63b61d6d5dbecc8f1cfb9ca77fd18f695072d",
                    InputValue.file("ü.mgf", Scenario.hashes(Scenario.SHA_A)).digest());
        }

        @Test
        @DisplayName("an empty file list hashes zero bytes")
        void emptyFiles() {
            // printf '' | sha256sum
            assertEquals(
                    "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                    InputValue.files(List.of()).digest());
        }

        @Test
        @DisplayName("bytes: their own SHA-256, as sha256sum of the written file would say")
        void bytes() {
            // printf '# comet_version 2026.03 rev. 0\nnum_threads = 4\n' | sha256sum
            assertEquals(
                    "56400815b85272afc1951a04d23e705cb56a785c189bef39e0b735cb52dc6d9a",
                    Scenario.cometParams(Scenario.COMET_PARAMS_TEXT).digest());
        }

        @Test
        @DisplayName("a tool: '<sha256> <n>:<version>\\n'")
        void tool() {
            // printf '%s 9:2026.03.0\n' $D | sha256sum
            assertEquals(
                    "e522cbb68f569b7a8fc831423b260965a608ed85b5d6eb5a730e64f370aeff98",
                    InputValue.tool("2026.03.0", Scenario.SHA_D).digest());
        }

        @Test
        @DisplayName("text: its UTF-8 bytes")
        void text() {
            // printf 'none' | sha256sum
            assertEquals(
                    "140bedbf9c3f6d56a9846d2ba7088798683f4da0c248231336e6a05679e4fdfe",
                    InputValue.text("none").digest());
        }

        @Test
        @DisplayName("a decimal: its plain form without trailing zeros")
        void decimal() {
            // printf '0.01' | sha256sum
            String expected = "312b95ee5a344d2f7a16ad817ff70788980da6e30b9bb04b651d53847abd476f";
            assertEquals(expected, InputValue.decimal(new BigDecimal("0.01")).digest());
            assertEquals(expected, InputValue.decimal(new BigDecimal("0.010")).digest());
            assertEquals(expected, InputValue.decimal(new BigDecimal("1E-2")).digest());
            assertEquals(
                    new InputValue.Decimal(new BigDecimal("0.01")),
                    new InputValue.Decimal(new BigDecimal("0.0100")));
            // printf '100' | sha256sum
            assertEquals(
                    "ad57366865126e55649ecb23ae1d48887544976efea46a48eb5d85a6eeb4d306",
                    InputValue.decimal(new BigDecimal("1E+2")).digest());
        }

        @Test
        @DisplayName("upper-case digests are accepted and held in lower case")
        void upperCaseDigests() {
            assertEquals(
                    Scenario.SHA_D,
                    InputValue.tool("2026.03.0", Scenario.SHA_D.toUpperCase(java.util.Locale.ROOT))
                            .binarySha256());
            assertEquals(
                    Scenario.SHA_D,
                    new InputValue.Bytes(Scenario.SHA_D.toUpperCase(java.util.Locale.ROOT))
                            .digest());
        }

        @Test
        @DisplayName("each value reports its type")
        void types() {
            List<ValueType> types = new ArrayList<>();
            types.add(InputValue.files(List.of()).type());
            types.add(InputValue.bytes(new byte[0]).type());
            types.add(InputValue.tool("1", Scenario.SHA_A).type());
            types.add(InputValue.text("").type());
            types.add(InputValue.decimal(BigDecimal.ONE).type());
            assertEquals(
                    List.of(
                            ValueType.FILES,
                            ValueType.BYTES,
                            ValueType.TOOL,
                            ValueType.TEXT,
                            ValueType.DECIMAL),
                    types);
        }
    }

    @Nested
    @DisplayName("refusals")
    class Refusals {

        @Test
        @DisplayName("a file named by a path, or not named")
        void fileNames() {
            FileHashes hashes = Scenario.hashes(Scenario.SHA_A);
            for (String name :
                    List.of("", "in/k562.mzML", "in\\k562.mzML", "/k562.mzML", "\\k562.mzML")) {
                assertEquals(
                        "a file input is named by its file name alone, but was: \"" + name + "\"",
                        assertThrows(
                                        IllegalArgumentException.class,
                                        () -> new InputValue.NamedFile(name, hashes))
                                .getMessage());
            }
            assertEquals(
                    "name",
                    assertThrows(
                                    NullPointerException.class,
                                    () -> new InputValue.NamedFile(Nulls.of(String.class), hashes))
                            .getMessage());
            assertEquals(
                    "hashes",
                    assertThrows(
                                    NullPointerException.class,
                                    () ->
                                            new InputValue.NamedFile(
                                                    "a.mzML", Nulls.of(FileHashes.class)))
                            .getMessage());
        }

        @Test
        @DisplayName("a tool with no version or a malformed digest")
        void tools() {
            assertEquals(
                    "a tool identity needs a version",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () -> InputValue.tool("", Scenario.SHA_A))
                            .getMessage());
            assertEquals(
                    "binarySha256 must be 64 hexadecimal characters (a SHA-256), but was: \"abc\"",
                    assertThrows(IllegalArgumentException.class, () -> InputValue.tool("1", "abc"))
                            .getMessage());
            assertEquals(
                    "binarySha256 must be 64 hexadecimal characters (a SHA-256), but was: \""
                            + "g".repeat(64)
                            + "\"",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () -> InputValue.tool("1", "g".repeat(64)))
                            .getMessage());
            assertEquals(
                    "version",
                    assertThrows(
                                    NullPointerException.class,
                                    () -> InputValue.tool(Nulls.of(String.class), Scenario.SHA_A))
                            .getMessage());
            assertEquals(
                    "binarySha256",
                    assertThrows(
                                    NullPointerException.class,
                                    () -> InputValue.tool("1", Nulls.of(String.class)))
                            .getMessage());
        }

        @Test
        @DisplayName("null contents")
        void nulls() {
            assertEquals(
                    "content",
                    assertThrows(
                                    NullPointerException.class,
                                    () -> InputValue.bytes(Nulls.of(byte[].class)))
                            .getMessage());
            assertEquals(
                    "value",
                    assertThrows(
                                    NullPointerException.class,
                                    () -> InputValue.text(Nulls.of(String.class)))
                            .getMessage());
            assertEquals(
                    "value",
                    assertThrows(
                                    NullPointerException.class,
                                    () -> InputValue.decimal(Nulls.of(BigDecimal.class)))
                            .getMessage());
            assertEquals(
                    "sha256",
                    assertThrows(
                                    NullPointerException.class,
                                    () -> new InputValue.Bytes(Nulls.of(String.class)))
                            .getMessage());
        }
    }
}
