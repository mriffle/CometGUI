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

package org.cometgui.provenance.manifest;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ToolRecord}.
 *
 * <p>Two groups carry the weight. The <em>ordering</em> group hands the record a set whose
 * iteration order is deliberately wrong -- a {@link LinkedHashSet} built in descending order -- and
 * asserts the hand-typed ascending order comes back. A record that merely copied the set would pass
 * an "are the capabilities there" test and fail this one, and it is this one that decides whether
 * two identical runs produce byte-identical manifests.
 *
 * <p>The <em>copying</em> group mutates the caller's collections after construction and asserts the
 * record did not move, then mutates the collections the record handed out and asserts they refuse.
 * A test that only checked the accessor returned something immutable would not notice a constructor
 * that kept the caller's set, which is the half that actually leaks.
 */
class ToolRecordTest {

    private static final Path EXECUTABLE = ManifestFixtures.runFile("percolator");

    private static ToolRecord tool(Set<String> capabilities, List<String> warnings) {
        return new ToolRecord(
                "percolator",
                "3.07.1",
                Optional.of("rel-3-07-1"),
                EXECUTABLE,
                ManifestFixtures.ABC_HASHES,
                true,
                Optional.of("percolator-3.07.1-linux-amd64.deb"),
                capabilities,
                ManifestFixtures.execution("OMP_NUM_THREADS", "8"),
                warnings);
    }

    @Nested
    @DisplayName("the identity of a binary")
    class Identity {

        @Test
        @DisplayName("every component comes back exactly as given")
        void everyComponentComesBack() {
            ToolRecord tool = tool(Set.of("xml"), List.of("no advisory"));

            assertAll(
                    () -> assertEquals("percolator", tool.name()),
                    () -> assertEquals("3.07.1", tool.version()),
                    () -> assertEquals(Optional.of("rel-3-07-1"), tool.releaseTag()),
                    () -> assertEquals(EXECUTABLE, tool.executablePath()),
                    () -> assertEquals("900150983cd24fb0d6963f7d28e17f72", tool.hashes().md5()),
                    () -> assertTrue(tool.managed()),
                    () ->
                            assertEquals(
                                    Optional.of("percolator-3.07.1-linux-amd64.deb"),
                                    tool.artefactIdentity()),
                    () -> assertEquals(Set.of("xml"), tool.capabilities()),
                    () -> assertEquals(0, tool.execution().exitCode()),
                    () -> assertEquals(List.of("no advisory"), tool.warnings()));
        }

        @Test
        @DisplayName("a local binary has no release tag and no upstream artefact")
        void aLocalBinaryHasNeitherTagNorArtefact() {
            ToolRecord local =
                    new ToolRecord(
                            "comet",
                            "2026.02.2",
                            Optional.empty(),
                            EXECUTABLE,
                            ManifestFixtures.ABC_HASHES,
                            false,
                            Optional.empty(),
                            Set.of(),
                            ManifestFixtures.execution("OMP_NUM_THREADS", "8"),
                            List.of());

            assertAll(
                    () -> assertEquals(Optional.empty(), local.releaseTag()),
                    () -> assertEquals(Optional.empty(), local.artefactIdentity()),
                    () -> assertEquals(Set.of(), local.capabilities()),
                    () -> assertEquals(List.of(), local.warnings()),
                    () -> assertFalse(local.managed()));
        }

        @Test
        @DisplayName("advisories are kept in the order they were raised")
        void advisoriesKeepTheirOrder() {
            ToolRecord tool =
                    tool(
                            Set.of("xml"),
                            List.of(
                                    "3.07.1 is the newest release publishing XML-capable binaries",
                                    "the macOS artefact is x86-64 and runs under Rosetta 2"));

            assertEquals(
                    List.of(
                            "3.07.1 is the newest release publishing XML-capable binaries",
                            "the macOS artefact is x86-64 and runs under Rosetta 2"),
                    tool.warnings());
        }
    }

    @Nested
    @DisplayName("deterministic ordering")
    class Ordering {

        @Test
        @DisplayName("capabilities come back in ascending order however they were supplied")
        void capabilitiesComeBackSorted() {
            Set<String> descending = new LinkedHashSet<>();
            descending.add("xml");
            descending.add("tdf");
            descending.add("percolator-xml");
            descending.add("mzml");

            ToolRecord tool = tool(descending, List.of());

            assertAll(
                    () ->
                            assertEquals(
                                    List.of("mzml", "percolator-xml", "tdf", "xml"),
                                    List.copyOf(tool.capabilities())),
                    () ->
                            assertEquals(
                                    List.of("xml", "tdf", "percolator-xml", "mzml"),
                                    List.copyOf(descending)));
        }

        @Test
        @DisplayName("two records built from differently ordered sets are equal")
        void orderOfSupplyDoesNotChangeIdentity() {
            Set<String> oneWay = new LinkedHashSet<>(List.of("xml", "mzml"));
            Set<String> theOther = new LinkedHashSet<>(List.of("mzml", "xml"));

            assertAll(
                    () -> assertEquals(tool(oneWay, List.of()), tool(theOther, List.of())),
                    () ->
                            assertEquals(
                                    tool(oneWay, List.of()).hashCode(),
                                    tool(theOther, List.of()).hashCode()));
        }
    }

    @Nested
    @DisplayName("defensive copying")
    class Copying {

        @Test
        @DisplayName("mutating the caller's capability set afterwards does not change the record")
        void theCapabilitySetIsCopiedIn() {
            Set<String> callers = new LinkedHashSet<>(List.of("xml"));
            ToolRecord tool = tool(callers, List.of());

            callers.add("forged-capability");
            callers.remove("xml");

            assertEquals(Set.of("xml"), tool.capabilities());
        }

        @Test
        @DisplayName("mutating the caller's warning list afterwards does not change the record")
        void theWarningListIsCopiedIn() {
            List<String> callers = new ArrayList<>(List.of("first advisory"));
            ToolRecord tool = tool(Set.of("xml"), callers);

            callers.add("a second advisory added later");
            callers.set(0, "a rewritten first advisory");

            assertEquals(List.of("first advisory"), tool.warnings());
        }

        @Test
        @DisplayName("the collections handed out refuse to be modified, and the record holds")
        void theCollectionsHandedOutRefuseToBeModified() {
            ToolRecord tool = tool(Set.of("xml"), List.of("first advisory"));
            Set<String> handedOutCapabilities = tool.capabilities();
            List<String> handedOutWarnings = tool.warnings();

            assertAll(
                    () ->
                            assertThrows(
                                    UnsupportedOperationException.class,
                                    () -> handedOutCapabilities.add("forged-capability")),
                    () ->
                            assertThrows(
                                    UnsupportedOperationException.class,
                                    () -> handedOutCapabilities.clear()),
                    () ->
                            assertThrows(
                                    UnsupportedOperationException.class,
                                    () -> handedOutWarnings.add("forged advisory")),
                    () ->
                            assertThrows(
                                    UnsupportedOperationException.class,
                                    () -> handedOutWarnings.set(0, "rewritten")),
                    () -> assertEquals(Set.of("xml"), tool.capabilities()),
                    () -> assertEquals(List.of("first advisory"), tool.warnings()));
        }
    }

    @Nested
    @DisplayName("what it refuses")
    class Rejections {

        @Test
        @DisplayName("a blank logical name is rejected, naming the field and the value")
        void aBlankNameIsRejected() {
            IllegalArgumentException thrown =
                    assertThrows(
                            IllegalArgumentException.class,
                            () ->
                                    new ToolRecord(
                                            " ",
                                            "3.07.1",
                                            Optional.empty(),
                                            EXECUTABLE,
                                            ManifestFixtures.ABC_HASHES,
                                            true,
                                            Optional.empty(),
                                            Set.of(),
                                            ManifestFixtures.execution("A", "1"),
                                            List.of()));

            assertEquals("name must not be blank, but was: \" \"", thrown.getMessage());
        }

        @Test
        @DisplayName("a blank reported version is rejected, naming the field and the value")
        void aBlankVersionIsRejected() {
            IllegalArgumentException thrown =
                    assertThrows(
                            IllegalArgumentException.class,
                            () ->
                                    new ToolRecord(
                                            "percolator",
                                            "",
                                            Optional.empty(),
                                            EXECUTABLE,
                                            ManifestFixtures.ABC_HASHES,
                                            true,
                                            Optional.empty(),
                                            Set.of(),
                                            ManifestFixtures.execution("A", "1"),
                                            List.of()));

            assertEquals("version must not be blank, but was: \"\"", thrown.getMessage());
        }

        @Test
        @DisplayName("a relative executable path is rejected")
        void aRelativeExecutablePathIsRejected() {
            Path relative = Path.of("bin", "percolator");

            IllegalArgumentException thrown =
                    assertThrows(
                            IllegalArgumentException.class,
                            () ->
                                    new ToolRecord(
                                            "percolator",
                                            "3.07.1",
                                            Optional.empty(),
                                            relative,
                                            ManifestFixtures.ABC_HASHES,
                                            true,
                                            Optional.empty(),
                                            Set.of(),
                                            ManifestFixtures.execution("A", "1"),
                                            List.of()));

            assertEquals(
                    "executablePath must be absolute, but was: " + relative, thrown.getMessage());
        }

        @Test
        @DisplayName("a null or blank capability is rejected, naming the field")
        void aNullOrBlankCapabilityIsRejected() {
            Set<String> withNull = new LinkedHashSet<>(Arrays.asList("xml", null));
            Set<String> withBlank = new LinkedHashSet<>(List.of("xml", "   "));

            assertAll(
                    () ->
                            assertEquals(
                                    "capabilities must not contain a null element",
                                    assertThrows(
                                                    IllegalArgumentException.class,
                                                    () -> tool(withNull, List.of()))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "capabilities must not contain a blank element, but contained:"
                                            + " \"   \"",
                                    assertThrows(
                                                    IllegalArgumentException.class,
                                                    () -> tool(withBlank, List.of()))
                                            .getMessage()));
        }

        @Test
        @DisplayName("a null or blank warning is rejected by index")
        void aNullOrBlankWarningIsRejected() {
            List<String> withNull = Arrays.asList("first", null);
            List<String> withBlank = List.of("first", "");

            assertAll(
                    () ->
                            assertEquals(
                                    "warnings[1] must not be null",
                                    assertThrows(
                                                    IllegalArgumentException.class,
                                                    () -> tool(Set.of("xml"), withNull))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "warnings[1] must not be blank, but was: \"\"",
                                    assertThrows(
                                                    IllegalArgumentException.class,
                                                    () -> tool(Set.of("xml"), withBlank))
                                            .getMessage()));
        }

        @Test
        @DisplayName("every reference component is required, and the message names it")
        void everyReferenceComponentIsRequired() {
            assertAll(
                    () ->
                            assertEquals(
                                    "releaseTag",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () ->
                                                            new ToolRecord(
                                                                    "percolator",
                                                                    "3.07.1",
                                                                    null,
                                                                    EXECUTABLE,
                                                                    ManifestFixtures.ABC_HASHES,
                                                                    true,
                                                                    Optional.empty(),
                                                                    Set.of(),
                                                                    ManifestFixtures.execution(
                                                                            "A", "1"),
                                                                    List.of()))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "hashes",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () ->
                                                            new ToolRecord(
                                                                    "percolator",
                                                                    "3.07.1",
                                                                    Optional.empty(),
                                                                    EXECUTABLE,
                                                                    null,
                                                                    true,
                                                                    Optional.empty(),
                                                                    Set.of(),
                                                                    ManifestFixtures.execution(
                                                                            "A", "1"),
                                                                    List.of()))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "artefactIdentity",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () ->
                                                            new ToolRecord(
                                                                    "percolator",
                                                                    "3.07.1",
                                                                    Optional.empty(),
                                                                    EXECUTABLE,
                                                                    ManifestFixtures.ABC_HASHES,
                                                                    true,
                                                                    null,
                                                                    Set.of(),
                                                                    ManifestFixtures.execution(
                                                                            "A", "1"),
                                                                    List.of()))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "capabilities",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () -> tool(null, List.of()))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "execution",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () ->
                                                            new ToolRecord(
                                                                    "percolator",
                                                                    "3.07.1",
                                                                    Optional.empty(),
                                                                    EXECUTABLE,
                                                                    ManifestFixtures.ABC_HASHES,
                                                                    true,
                                                                    Optional.empty(),
                                                                    Set.of(),
                                                                    null,
                                                                    List.of()))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "warnings",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () -> tool(Set.of("xml"), null))
                                            .getMessage()));
        }
    }
}
