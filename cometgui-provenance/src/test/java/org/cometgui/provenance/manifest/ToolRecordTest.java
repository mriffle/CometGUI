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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.cometgui.domain.ports.FileHashes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ToolRecord}.
 *
 * <p>Three groups carry the weight. The <em>ordering</em> group hands the record a set whose
 * iteration order is deliberately wrong -- a {@link LinkedHashSet} built in descending order -- and
 * asserts the hand-typed ascending order comes back. A record that merely copied the set would pass
 * an "are the capabilities there" test and fail this one, and it is this one that decides whether
 * two identical runs produce byte-identical manifests.
 *
 * <p>The <em>copying</em> group mutates the caller's collections after construction and asserts the
 * record did not move, then mutates the collections the record handed out and asserts they refuse.
 * A test that only checked the accessor returned something immutable would not notice a constructor
 * that kept the caller's set, which is the half that actually leaks -- so the group also reads the
 * backing field directly, through {@link ManifestFixtures#componentField}, because {@link
 * ToolRecord#capabilities()} re-sorts on the way out and would hide exactly that defect.
 *
 * <p>The <em>optional</em> group proves that every {@code Optional} component distinguishes absent
 * from present-and-empty. {@code Optional.of("")} is not a release tag, an artefact identity or a
 * stage; it is an absent fact recorded as present, and no reader can tell the two apart afterwards.
 */
class ToolRecordTest {

    private static final Path EXECUTABLE = ManifestFixtures.runFile("percolator");

    /** A valid record with two components left to the caller, for the value groups. */
    private static ToolRecord tool(Set<String> capabilities, List<String> warnings) {
        return build(
                "percolator",
                "3.07.1",
                Optional.of("rel-3-07-1"),
                EXECUTABLE,
                ManifestFixtures.ABC_HASHES,
                Optional.of("percolator-3.07.1-linux-amd64.deb"),
                capabilities,
                Optional.of("rescore"),
                ManifestFixtures.execution("OMP_NUM_THREADS", "8"),
                warnings);
    }

    /**
     * The canonical constructor with the {@code managed} flag fixed, so that a rejection test can
     * name the one component it is making invalid instead of restating ten valid ones.
     */
    private static ToolRecord build(
            String name,
            String version,
            Optional<String> releaseTag,
            Path executablePath,
            FileHashes hashes,
            Optional<String> artefactIdentity,
            Set<String> capabilities,
            Optional<String> stageId,
            ExecutionRecord execution,
            List<String> warnings) {
        return new ToolRecord(
                name,
                version,
                releaseTag,
                executablePath,
                hashes,
                true,
                artefactIdentity,
                capabilities,
                stageId,
                execution,
                warnings);
    }

    private static ToolRecord valid() {
        return tool(Set.of("xml"), List.of());
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
                    () -> assertEquals(Optional.of("rescore"), tool.stageId()),
                    () -> assertEquals("rescore", tool.stageId().orElseThrow()),
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
                            Optional.of("search"),
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
    @DisplayName("the workflow stage that ran the tool")
    class Stage {

        @Test
        @DisplayName("the stage identifier is kept as given")
        void theStageIdentifierIsKept() {
            assertEquals("rescore", valid().stageId().orElseThrow());
        }

        @Test
        @DisplayName("an invocation outside any stage records no stage, rather than a sentinel")
        void anInvocationOutsideAnyStageRecordsNone() {
            ToolRecord probe =
                    build(
                            "percolator",
                            "3.07.1",
                            Optional.empty(),
                            EXECUTABLE,
                            ManifestFixtures.ABC_HASHES,
                            Optional.empty(),
                            Set.of(),
                            Optional.empty(),
                            ManifestFixtures.execution("A", "1"),
                            List.of());

            assertAll(
                    () -> assertFalse(probe.stageId().isPresent()),
                    () -> assertEquals(Optional.empty(), probe.stageId()));
        }

        @Test
        @DisplayName("two invocations differing only in stage are different records")
        void theStageIsPartOfIdentity() {
            ToolRecord searched =
                    build(
                            "percolator",
                            "3.07.1",
                            Optional.empty(),
                            EXECUTABLE,
                            ManifestFixtures.ABC_HASHES,
                            Optional.empty(),
                            Set.of(),
                            Optional.of("search"),
                            ManifestFixtures.execution("A", "1"),
                            List.of());
            ToolRecord rescored =
                    build(
                            "percolator",
                            "3.07.1",
                            Optional.empty(),
                            EXECUTABLE,
                            ManifestFixtures.ABC_HASHES,
                            Optional.empty(),
                            Set.of(),
                            Optional.of("rescore"),
                            ManifestFixtures.execution("A", "1"),
                            List.of());

            assertAll(
                    () -> assertEquals("search", searched.stageId().orElseThrow()),
                    () -> assertEquals("rescore", rescored.stageId().orElseThrow()),
                    () -> assertNotEquals(searched, rescored));
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
        @DisplayName("the stored field is sorted too, not only the copy the accessor makes")
        void theStoredFieldIsSorted() throws ReflectiveOperationException {
            Set<String> descending = new LinkedHashSet<>();
            descending.add("xml");
            descending.add("tdf");
            descending.add("percolator-xml");
            descending.add("mzml");

            Set<?> stored =
                    (Set<?>)
                            ManifestFixtures.componentField(
                                    tool(descending, List.of()), "capabilities");

            assertEquals(List.of("mzml", "percolator-xml", "tdf", "xml"), List.copyOf(stored));
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
        @DisplayName("the stored field is isolated too, not only the copy the accessor makes")
        void theStoredFieldIsIsolated() throws ReflectiveOperationException {
            Set<String> callers = new LinkedHashSet<>(List.of("xml"));
            ToolRecord tool = tool(callers, List.of());

            callers.add("forged-capability");
            Set<?> stored = (Set<?>) ManifestFixtures.componentField(tool, "capabilities");

            assertEquals(List.of("xml"), List.copyOf(stored));
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
                                    handedOutCapabilities::clear),
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
    @DisplayName("an Optional component is absent or informative, never empty text")
    class Optionals {

        @Test
        @DisplayName("a blank release tag is rejected, naming the field and the value")
        void aBlankReleaseTagIsRejected() {
            IllegalArgumentException thrown =
                    assertThrows(
                            IllegalArgumentException.class,
                            () ->
                                    build(
                                            "percolator",
                                            "3.07.1",
                                            Optional.of(""),
                                            EXECUTABLE,
                                            ManifestFixtures.ABC_HASHES,
                                            Optional.empty(),
                                            Set.of(),
                                            Optional.empty(),
                                            ManifestFixtures.execution("A", "1"),
                                            List.of()));

            assertEquals("releaseTag must not be blank, but was: \"\"", thrown.getMessage());
        }

        @Test
        @DisplayName("a blank artefact identity is rejected, naming the field and the value")
        void aBlankArtefactIdentityIsRejected() {
            IllegalArgumentException thrown =
                    assertThrows(
                            IllegalArgumentException.class,
                            () ->
                                    build(
                                            "percolator",
                                            "3.07.1",
                                            Optional.empty(),
                                            EXECUTABLE,
                                            ManifestFixtures.ABC_HASHES,
                                            Optional.of(" "),
                                            Set.of(),
                                            Optional.empty(),
                                            ManifestFixtures.execution("A", "1"),
                                            List.of()));

            assertEquals("artefactIdentity must not be blank, but was: \" \"", thrown.getMessage());
        }

        @Test
        @DisplayName("a blank stage identifier is rejected, naming the field and the value")
        void aBlankStageIdentifierIsRejected() {
            IllegalArgumentException thrown =
                    assertThrows(
                            IllegalArgumentException.class,
                            () ->
                                    build(
                                            "percolator",
                                            "3.07.1",
                                            Optional.empty(),
                                            EXECUTABLE,
                                            ManifestFixtures.ABC_HASHES,
                                            Optional.empty(),
                                            Set.of(),
                                            Optional.of("\t"),
                                            ManifestFixtures.execution("A", "1"),
                                            List.of()));

            assertEquals("stageId must not be blank, but was: \"\t\"", thrown.getMessage());
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
                                    build(
                                            " ",
                                            "3.07.1",
                                            Optional.empty(),
                                            EXECUTABLE,
                                            ManifestFixtures.ABC_HASHES,
                                            Optional.empty(),
                                            Set.of(),
                                            Optional.empty(),
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
                                    build(
                                            "percolator",
                                            "",
                                            Optional.empty(),
                                            EXECUTABLE,
                                            ManifestFixtures.ABC_HASHES,
                                            Optional.empty(),
                                            Set.of(),
                                            Optional.empty(),
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
                                    build(
                                            "percolator",
                                            "3.07.1",
                                            Optional.empty(),
                                            relative,
                                            ManifestFixtures.ABC_HASHES,
                                            Optional.empty(),
                                            Set.of(),
                                            Optional.empty(),
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
            ExecutionRecord execution = ManifestFixtures.execution("A", "1");

            assertAll(
                    () ->
                            assertEquals(
                                    "releaseTag",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () ->
                                                            build(
                                                                    "percolator",
                                                                    "3.07.1",
                                                                    null,
                                                                    EXECUTABLE,
                                                                    ManifestFixtures.ABC_HASHES,
                                                                    Optional.empty(),
                                                                    Set.of(),
                                                                    Optional.empty(),
                                                                    execution,
                                                                    List.of()))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "hashes",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () ->
                                                            build(
                                                                    "percolator",
                                                                    "3.07.1",
                                                                    Optional.empty(),
                                                                    EXECUTABLE,
                                                                    null,
                                                                    Optional.empty(),
                                                                    Set.of(),
                                                                    Optional.empty(),
                                                                    execution,
                                                                    List.of()))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "artefactIdentity",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () ->
                                                            build(
                                                                    "percolator",
                                                                    "3.07.1",
                                                                    Optional.empty(),
                                                                    EXECUTABLE,
                                                                    ManifestFixtures.ABC_HASHES,
                                                                    null,
                                                                    Set.of(),
                                                                    Optional.empty(),
                                                                    execution,
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
                                    "stageId",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () ->
                                                            build(
                                                                    "percolator",
                                                                    "3.07.1",
                                                                    Optional.empty(),
                                                                    EXECUTABLE,
                                                                    ManifestFixtures.ABC_HASHES,
                                                                    Optional.empty(),
                                                                    Set.of(),
                                                                    null,
                                                                    execution,
                                                                    List.of()))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "execution",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () ->
                                                            build(
                                                                    "percolator",
                                                                    "3.07.1",
                                                                    Optional.empty(),
                                                                    EXECUTABLE,
                                                                    ManifestFixtures.ABC_HASHES,
                                                                    Optional.empty(),
                                                                    Set.of(),
                                                                    Optional.empty(),
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
