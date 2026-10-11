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

package org.cometgui.workflow.limelight;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.cometgui.tools.limelight.PercolatorXmlPsms;
import org.cometgui.workflow.limelight.LimelightFixtures.Psm;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The Percolator XML as the converter is given it (proposed P12-18), every value typed out. */
class PoutForConverterTest {

    @TempDir private Path root;

    private final java.util.concurrent.atomic.AtomicInteger written =
            new java.util.concurrent.atomic.AtomicInteger();

    private static String strip(String input, String pattern) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        long removed =
                PoutForConverter.removeAll(
                        new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
                        out,
                        pattern.getBytes(StandardCharsets.UTF_8));
        return out.toString(StandardCharsets.UTF_8) + "#" + removed;
    }

    @ParameterizedTest(name = "[{index}] \"{0}\" without \"{1}\" is \"{2}\"")
    @CsvSource(
            delimiter = '|',
            value = {
                "abc|/d/|abc#0",
                "/d/x|/d/|x#1",
                "x/d/y/d/z|/d/|xyz#2",
                "aaab|aab|a#1",
                "aabaab|aab|#2",
                "aaaa|aab|aaaa#0",
                "aa|aab|aa#0",
                "abab_abac|abac|abab_#1",
                "ababac|abac|ab#1",
                "/d/d/|/d/|d/#1",
                "''|/d/|#0",
                "aabaabaaabx|aabaaab|aabx#1",
                "abababcab|ababc|abab#1",
                "aaaaab|aaab|aa#1",
                "abcabdabcabcabd|abcabd|abc#2",
                "xyxyxz|xyxz|xy#1"
            })
    @DisplayName("every occurrence is removed and a partial match is written as it was read")
    void removeAll(String input, String pattern, String expected) throws IOException {
        assertEquals(expected, strip(input, pattern));
    }

    private PercolatorXmlPsms pout(String prefix, List<Psm> psms) throws IOException {
        Path file = root.resolve("pout-" + written.incrementAndGet() + ".xml");
        Files.writeString(
                file, LimelightFixtures.pout(prefix, psms, false), StandardCharsets.UTF_8);
        return PercolatorXmlPsms.read(file);
    }

    @Test
    @DisplayName(
            "ids under the pepXML directory lose it, a bare id is kept, and a copy is needed only"
                    + " when some id holds a separator")
    void theIds() throws IOException {
        Path directory = root.resolve("run/outputs/comet").toAbsolutePath();
        PercolatorXmlPsms absolute = pout(directory + "/", LimelightFixtures.TARGETS);
        PercolatorXmlPsms bare = pout("", LimelightFixtures.TARGETS);

        assertAll(
                () ->
                        assertEquals(
                                List.of("k562_3_101_2_1", "k562_3_102_2_1", "k562_4_201_2_1"),
                                PoutForConverter.converterIds(absolute, directory)),
                () ->
                        assertEquals(
                                List.of("k562_3_101_2_1", "k562_3_102_2_1", "k562_4_201_2_1"),
                                PoutForConverter.converterIds(bare, directory)),
                () -> assertTrue(PoutForConverter.needsCopy(absolute)),
                () -> assertFalse(PoutForConverter.needsCopy(bare)),
                () ->
                        assertEquals(
                                Optional.empty(), PoutForConverter.problem(absolute, directory)));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvSource({
        "another directory, /elsewhere/comet/",
        "a subdirectory, SUB",
        "a backslash, BACKSLASH",
        "a leading backslash, LEADING"
    })
    @DisplayName("an id that is not a name directly in the pepXML directory is refused, naming it")
    void idsOutsideTheDirectory(String what, String prefixKind) throws IOException {
        Path directory = root.resolve("run/outputs/comet").toAbsolutePath();
        String prefix =
                switch (prefixKind) {
                    case "SUB" -> directory + "/deeper/";
                    case "BACKSLASH" -> directory + "/a\\";
                    case "LEADING" -> "\\";
                    default -> prefixKind;
                };
        PercolatorXmlPsms psms = pout(prefix, List.of(new Psm("k562_3", 7, false)));

        IllegalArgumentException refused =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> PoutForConverter.converterIds(psms, directory));

        assertEquals(
                "the Percolator PSM id \""
                        + prefix
                        + "k562_3_7_2_1\" in "
                        + psms.file()
                        + " names a Comet output that is not directly in "
                        + directory
                        + ", so it is not one of this run's pepXML files",
                refused.getMessage(),
                what);
        assertEquals(Optional.of(refused.getMessage()), PoutForConverter.problem(psms, directory));
    }

    @Test
    @DisplayName(
            "the copy holds the original's bytes with the directory removed, and is read back to"
                    + " exactly the expected ids")
    void theCopy() throws IOException {
        Path directory = root.resolve("run/outputs/comet").toAbsolutePath();
        PercolatorXmlPsms original = pout(directory + "/", LimelightFixtures.TARGETS);
        Path copy = root.resolve("copy.xml");

        PercolatorXmlPsms read = PoutForConverter.write(original, directory, copy);

        assertAll(
                () ->
                        assertEquals(
                                Files.readString(original.file()).replace(directory + "/", ""),
                                Files.readString(copy)),
                () -> assertEquals(copy, read.file()),
                () ->
                        assertEquals(
                                List.of("k562_3_101_2_1", "k562_3_102_2_1", "k562_4_201_2_1"),
                                read.psmIds()),
                () -> assertEquals(0, read.decoyPsmCount()));
    }

    @Test
    @DisplayName(
            "a directory the file spells differently (XML-escaped) leaves the ids unchanged, so the"
                    + " copy is refused and deleted")
    void aCopyThatDoesNotProveIsDeleted() throws IOException {
        Path directory = root.resolve("a&b/outputs/comet").toAbsolutePath();
        String escaped = directory.toString().replace("&", "&amp;") + "/";
        Path file = root.resolve("escaped.xml");
        Files.writeString(file, LimelightFixtures.pout(escaped, LimelightFixtures.TARGETS, false));
        PercolatorXmlPsms original = PercolatorXmlPsms.read(file);
        Path copy = root.resolve("copy.xml");

        IOException refused =
                assertThrows(
                        IOException.class, () -> PoutForConverter.write(original, directory, copy));

        assertAll(
                () ->
                        assertEquals(
                                directory + "/k562_3_101_2_1",
                                original.psmIds().get(0),
                                "the reader decodes the id"),
                () ->
                        assertEquals(
                                "the copy of "
                                        + file
                                        + " with "
                                        + directory
                                        + " removed from its PSM ids does not hold exactly the"
                                        + " original's 3 PSM ids and 0 decoy PSMs (it holds 3 and"
                                        + " 0), so the converter is not given it",
                                refused.getMessage()),
                () -> assertFalse(Files.exists(copy)));
    }

    @Test
    @DisplayName("a copy over an existing file is refused, and the existing file is left alone")
    void theCopyIsNew() throws IOException {
        Path directory = root.resolve("run/outputs/comet").toAbsolutePath();
        PercolatorXmlPsms original = pout(directory + "/", LimelightFixtures.TARGETS);
        Path copy = Files.writeString(root.resolve("copy.xml"), "x");

        assertThrows(IOException.class, () -> PoutForConverter.write(original, directory, copy));
    }
}
