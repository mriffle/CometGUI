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

package org.cometgui.domain.run;

import static org.cometgui.domain.run.RunFixtures.HASHES_A;
import static org.cometgui.domain.run.RunFixtures.HASHES_B;
import static org.cometgui.domain.run.RunFixtures.identity;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.cometgui.domain.project.ProjectId;
import org.cometgui.domain.testing.Nulls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link RunDerivation}, and what it changes in {@link RunIdentity} and {@link RunDescriptor}: a
 * derived run names the run whose Comet results it reuses, once, in its immutable identity, and is
 * written at schema version 2. Every expected value is typed out here.
 */
class RunDerivationTest {

    private static final Instant SOURCE_CREATED = Instant.parse("2026-08-28T23:15:00.250Z");

    private static final ArchivedFile PROVENANCE =
            new ArchivedFile("provenance/provenance.json", 4096, HASHES_A);

    private static final ArchivedFile MERGED_PIN =
            new ArchivedFile("inputs/pin/merged.pin", 777, HASHES_B);

    private static RunDerivation derivation() {
        return new RunDerivation(new RunId("run-0001"), SOURCE_CREATED, PROVENANCE, MERGED_PIN);
    }

    /** The fixture's identity as run-0002, derived from run-0001. */
    private static RunIdentity derived(Optional<RunDerivation> from) {
        RunIdentity g = identity();
        return new RunIdentity(
                new RunId("run-0002"),
                g.projectId(),
                Instant.parse("2026-08-29T08:00:00Z"),
                g.cometRelease(),
                g.spectra(),
                g.fasta(),
                g.parameters(),
                g.indexMode(),
                g.databaseDelivery(),
                from);
    }

    @Test
    @DisplayName("a derivation keeps its members and names its source's directory")
    void members() {
        RunDerivation derivation =
                new RunDerivation(
                        new RunId("run-0001"),
                        Instant.parse("2026-08-28T23:15:00.250987Z"),
                        PROVENANCE,
                        MERGED_PIN);
        assertAll(
                () -> assertEquals(new RunId("run-0001"), derivation.runId()),
                () -> assertEquals(SOURCE_CREATED, derivation.created(), "truncated to millis"),
                () -> assertEquals(PROVENANCE, derivation.provenance()),
                () -> assertEquals(MERGED_PIN, derivation.mergedPin()),
                () -> assertEquals("20260828T231500Z-run-0001", derivation.directoryName()));
    }

    @Test
    @DisplayName("the manifest and the merged PIN must be at their layout paths")
    void paths() {
        IllegalArgumentException provenance =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                new RunDerivation(
                                        new RunId("run-0001"),
                                        SOURCE_CREATED,
                                        new ArchivedFile("provenance/events.log", 1, HASHES_A),
                                        MERGED_PIN));
        IllegalArgumentException pin =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                new RunDerivation(
                                        new RunId("run-0001"),
                                        SOURCE_CREATED,
                                        PROVENANCE,
                                        new ArchivedFile("inputs/pin/other.pin", 1, HASHES_B)));
        assertAll(
                () ->
                        assertEquals(
                                "a derived run records its source's manifest at"
                                        + " provenance/provenance.json, but it was recorded at"
                                        + " \"provenance/events.log\"",
                                provenance.getMessage()),
                () ->
                        assertEquals(
                                "a derived run records its merged PIN at inputs/pin/merged.pin,"
                                        + " but it was recorded at \"inputs/pin/other.pin\"",
                                pin.getMessage()));
    }

    @Test
    @DisplayName("every null member of a derivation is refused by name")
    void nulls() {
        RunId id = new RunId("run-0001");
        assertAll(
                () ->
                        assertEquals(
                                "runId",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new RunDerivation(
                                                                Nulls.of(RunId.class),
                                                                SOURCE_CREATED,
                                                                PROVENANCE,
                                                                MERGED_PIN))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "created",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new RunDerivation(
                                                                id,
                                                                Nulls.of(Instant.class),
                                                                PROVENANCE,
                                                                MERGED_PIN))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "provenance",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new RunDerivation(
                                                                id,
                                                                SOURCE_CREATED,
                                                                Nulls.of(ArchivedFile.class),
                                                                MERGED_PIN))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "mergedPin",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new RunDerivation(
                                                                id,
                                                                SOURCE_CREATED,
                                                                PROVENANCE,
                                                                Nulls.of(ArchivedFile.class)))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "derivedFrom",
                                assertThrows(
                                                NullPointerException.class,
                                                () -> derived(Nulls.of(Optional.class)))
                                        .getMessage()));
    }

    @Test
    @DisplayName("a run that executes its own search is not derived and is schema version 1")
    void notDerived() {
        RunIdentity own = identity();
        assertAll(
                () -> assertEquals(Optional.empty(), own.derivedFrom()),
                () -> assertFalse(own.isDerived()),
                () -> assertEquals(1, RunDescriptor.SCHEMA_VERSION),
                () -> assertEquals(1, RunDescriptor.of(own).schemaVersion()));
    }

    @Test
    @DisplayName("a derived run carries its derivation and is schema version 2, for life")
    void derivedRun() {
        RunIdentity derived = derived(Optional.of(derivation()));
        RunDescriptor fresh = RunDescriptor.of(derived);
        RunDescriptor started = fresh.withNewAttempt(Instant.parse("2026-08-29T08:00:01Z"));
        assertAll(
                () -> assertEquals(Optional.of(derivation()), derived.derivedFrom()),
                () -> assertTrue(derived.isDerived()),
                () -> assertEquals(2, RunDescriptor.DERIVED_SCHEMA_VERSION),
                () -> assertEquals(2, fresh.schemaVersion()),
                () -> assertEquals(2, started.schemaVersion()));
    }

    @Test
    @DisplayName("a run cannot derive from itself")
    void notFromItself() {
        RunIdentity g = identity();
        IllegalArgumentException thrown =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                new RunIdentity(
                                        new RunId("run-0001"),
                                        new ProjectId("project-beta"),
                                        g.created(),
                                        g.cometRelease(),
                                        g.spectra(),
                                        g.fasta(),
                                        g.parameters(),
                                        g.indexMode(),
                                        g.databaseDelivery(),
                                        Optional.of(derivation())));
        assertEquals("run run-0001 cannot derive from itself", thrown.getMessage());
    }

    @Test
    @DisplayName("the derivation is identity: adding, removing or changing it is refused by name")
    void derivationImmutable() {
        RunDescriptor plain = RunDescriptor.of(derived(Optional.empty()));
        RunDescriptor withIt = RunDescriptor.of(derived(Optional.of(derivation())));
        RunDescriptor otherSource =
                RunDescriptor.of(
                        derived(
                                Optional.of(
                                        new RunDerivation(
                                                new RunId("run-0003"),
                                                SOURCE_CREATED,
                                                PROVENANCE,
                                                MERGED_PIN))));
        for (RunDescriptor[] pair :
                List.of(
                        new RunDescriptor[] {plain, withIt},
                        new RunDescriptor[] {withIt, plain},
                        new RunDescriptor[] {withIt, otherSource})) {
            RunImmutabilityException thrown =
                    assertThrows(
                            RunImmutabilityException.class,
                            () -> pair[0].requireSuccessor(pair[1]));
            assertEquals("derivedFrom", thrown.member());
        }
        withIt.requireSuccessor(RunDescriptor.of(derived(Optional.of(derivation()))));
        assertEquals(withIt, RunDescriptor.of(derived(Optional.of(derivation()))));
    }
}
