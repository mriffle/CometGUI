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

import static org.cometgui.domain.run.RunFixtures.CREATED;
import static org.cometgui.domain.run.RunFixtures.HASHES_A;
import static org.cometgui.domain.run.RunFixtures.HASHES_B;
import static org.cometgui.domain.run.RunFixtures.HASHES_P;
import static org.cometgui.domain.run.RunFixtures.identity;
import static org.cometgui.domain.run.RunFixtures.input;
import static org.cometgui.domain.run.RunFixtures.params;
import static org.cometgui.domain.testing.TestPaths.absolute;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.cometgui.domain.project.ProjectId;
import org.cometgui.domain.testing.Nulls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** {@link RunIdentity}: the members of run.json written once, and what holds them consistent. */
class RunIdentityTest {

    private static RunIdentity with(List<SpectrumInput> spectra) {
        return new RunIdentity(
                new RunId("run-0001"),
                new ProjectId("p"),
                CREATED,
                "2026.03.0",
                spectra,
                input("db/x.fasta", HASHES_P),
                params(),
                IndexMode.NONE,
                DatabaseDelivery.PARAMETER_FILE);
    }

    @Test
    @DisplayName("spectraOf names the inputs in order by the output naming rule")
    void spectraOf() {
        List<SpectrumInput> spectra =
                RunIdentity.spectraOf(
                        List.of(input("in/a.mzML", HASHES_A), input("in/A.mzXML", HASHES_B)));
        assertAll(
                () -> assertEquals(2, spectra.size()),
                () ->
                        assertEquals(
                                new SpectrumInput(1, input("in/a.mzML", HASHES_A), "a"),
                                spectra.get(0)),
                () ->
                        assertEquals(
                                new SpectrumInput(2, input("in/A.mzXML", HASHES_B), "A_2"),
                                spectra.get(1)));
    }

    @Test
    @DisplayName("the identity keeps every member; created is truncated to milliseconds")
    void members() {
        RunIdentity identity = identity();
        assertAll(
                () -> assertEquals(new RunId("run-0001"), identity.runId()),
                () -> assertEquals(new ProjectId("project-beta"), identity.projectId()),
                () -> assertEquals(CREATED, identity.created()),
                () -> assertEquals("2026.03.0", identity.cometRelease()),
                () -> assertEquals("k562_3", identity.spectra().get(0).base()),
                () -> assertEquals("k562_4", identity.spectra().get(1).base()),
                () -> assertEquals(absolute("data/db/sub.fasta"), identity.fasta().path()),
                () -> assertEquals(params(), identity.parameters()),
                () -> assertEquals(IndexMode.NONE, identity.indexMode()),
                () -> assertEquals(DatabaseDelivery.PARAMETER_FILE, identity.databaseDelivery()),
                () ->
                        assertEquals(
                                Instant.parse("2026-01-01T00:00:00.001Z"),
                                RunFixtures.withCreated(
                                                identity,
                                                Instant.parse("2026-01-01T00:00:00.001999Z"))
                                        .created()));
    }

    @Test
    @DisplayName("a spectrum list out of position order is refused, naming the entry")
    void positionsOutOfOrder() {
        List<SpectrumInput> spectra = new ArrayList<>(identity().spectra());
        SpectrumInput second = spectra.get(1);
        spectra.set(1, new SpectrumInput(3, second.file(), second.base()));
        assertEquals(
                "spectra[1] has position 3, but positions must run 1, 2, 3 ... in input order",
                assertThrows(IllegalArgumentException.class, () -> with(spectra)).getMessage());
    }

    @Test
    @DisplayName("a base name the naming rule would not give is refused, naming both")
    void wrongBase() {
        List<SpectrumInput> spectra =
                List.of(
                        new SpectrumInput(1, input("in/a.mzML", HASHES_A), "a"),
                        new SpectrumInput(2, input("in/A.mgf", HASHES_B), "A"));
        assertEquals(
                "spectra[1] has base name \"A\", but the output naming rule gives \"A_2\" for these"
                        + " inputs",
                assertThrows(IllegalArgumentException.class, () -> with(spectra)).getMessage());
    }

    @Test
    @DisplayName("no spectrum files is refused")
    void noSpectra() {
        assertEquals(
                "a run needs at least one spectrum file",
                assertThrows(IllegalArgumentException.class, () -> with(List.of())).getMessage());
    }

    @Test
    @DisplayName("a parameter file outside parameters/ is refused")
    void parametersOutsideTheirDirectory() {
        RunIdentity good = identity();
        assertEquals(
                "the parameter file must be archived under parameters/, but was recorded at"
                        + " \"outputs/comet.params\"",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        new RunIdentity(
                                                good.runId(),
                                                good.projectId(),
                                                good.created(),
                                                good.cometRelease(),
                                                good.spectra(),
                                                good.fasta(),
                                                new ArchivedFile(
                                                        "outputs/comet.params", 1, HASHES_P),
                                                good.indexMode(),
                                                good.databaseDelivery()))
                        .getMessage());
    }

    @Test
    @DisplayName("a parameter file named parameters (no directory) is refused too")
    void parametersFileNamedParameters() {
        RunIdentity good = identity();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new RunIdentity(
                                good.runId(),
                                good.projectId(),
                                good.created(),
                                good.cometRelease(),
                                good.spectra(),
                                good.fasta(),
                                new ArchivedFile("parameters", 1, HASHES_P),
                                good.indexMode(),
                                good.databaseDelivery()));
    }

    @Test
    @DisplayName("a blank Comet release or one with a control character is refused")
    void release() {
        assertAll(
                () ->
                        assertEquals(
                                "cometRelease must not be blank",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () -> RunFixtures.withRelease(identity(), " "))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "cometRelease must not contain a control character",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () ->
                                                        RunFixtures.withRelease(
                                                                identity(), "2026.03.0\n"))
                                        .getMessage()),
                () -> assertEquals("x", RunFixtures.withRelease(identity(), "x").cometRelease()));
    }

    @Test
    @DisplayName("every null member is refused by name")
    void nulls() {
        RunIdentity g = identity();
        assertAll(
                () ->
                        assertEquals(
                                "runId",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new RunIdentity(
                                                                Nulls.of(RunId.class),
                                                                g.projectId(),
                                                                g.created(),
                                                                g.cometRelease(),
                                                                g.spectra(),
                                                                g.fasta(),
                                                                g.parameters(),
                                                                g.indexMode(),
                                                                g.databaseDelivery()))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "projectId",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new RunIdentity(
                                                                g.runId(),
                                                                Nulls.of(ProjectId.class),
                                                                g.created(),
                                                                g.cometRelease(),
                                                                g.spectra(),
                                                                g.fasta(),
                                                                g.parameters(),
                                                                g.indexMode(),
                                                                g.databaseDelivery()))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "created",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        RunFixtures.withCreated(
                                                                g, Nulls.of(Instant.class)))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "cometRelease",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        RunFixtures.withRelease(
                                                                g, Nulls.of(String.class)))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "spectra",
                                assertThrows(
                                                NullPointerException.class,
                                                () -> with(Nulls.of(List.class)))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "fasta",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new RunIdentity(
                                                                g.runId(),
                                                                g.projectId(),
                                                                g.created(),
                                                                g.cometRelease(),
                                                                g.spectra(),
                                                                Nulls.of(RecordedInput.class),
                                                                g.parameters(),
                                                                g.indexMode(),
                                                                g.databaseDelivery()))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "parameters",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new RunIdentity(
                                                                g.runId(),
                                                                g.projectId(),
                                                                g.created(),
                                                                g.cometRelease(),
                                                                g.spectra(),
                                                                g.fasta(),
                                                                Nulls.of(ArchivedFile.class),
                                                                g.indexMode(),
                                                                g.databaseDelivery()))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "indexMode",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new RunIdentity(
                                                                g.runId(),
                                                                g.projectId(),
                                                                g.created(),
                                                                g.cometRelease(),
                                                                g.spectra(),
                                                                g.fasta(),
                                                                g.parameters(),
                                                                Nulls.of(IndexMode.class),
                                                                g.databaseDelivery()))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "databaseDelivery",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new RunIdentity(
                                                                g.runId(),
                                                                g.projectId(),
                                                                g.created(),
                                                                g.cometRelease(),
                                                                g.spectra(),
                                                                g.fasta(),
                                                                g.parameters(),
                                                                g.indexMode(),
                                                                Nulls.of(DatabaseDelivery.class)))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "files",
                                assertThrows(
                                                NullPointerException.class,
                                                () -> RunIdentity.spectraOf(Nulls.of(List.class)))
                                        .getMessage()));
    }

    @Test
    @DisplayName("the spectra list is a copy and unmodifiable")
    void spectraCopied() {
        List<SpectrumInput> spectra = new ArrayList<>(identity().spectra());
        RunIdentity identity = with(spectra);
        spectra.clear();
        assertAll(
                () -> assertEquals(2, identity.spectra().size()),
                () ->
                        assertThrows(
                                UnsupportedOperationException.class,
                                () -> identity.spectra().clear()));
    }
}
