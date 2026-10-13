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

package org.cometgui.workflow.steps;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.cometgui.domain.run.AttemptOutcome;
import org.cometgui.domain.run.IndexMode;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.model.DecoySource;
import org.cometgui.provenance.manifest.ManifestReader;
import org.cometgui.provenance.manifest.ProvenanceManifest;
import org.cometgui.provenance.manifest.ToolRecord;
import org.cometgui.workflow.engine.ReuseRefusedException;
import org.cometgui.workflow.engine.RunResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * What a tool record says about the binary that ran, beyond its digests: the probed capabilities
 * and the artefact a managed install came from (the specification's tool provenance), through the
 * real engine into {@code provenance.json}, read back with {@link ManifestReader}. Comet's record
 * carries the selection's probed set -- it was written empty before phase 13 -- and both tools'
 * records carry the offer's artefact identity, which was never written at all.
 *
 * <p>Comet is a stand-in that exits 0 and writes nothing, as in {@link ToolWarningsRunTest}; the
 * real install path, with real markers, is proved in {@code cometgui-app}. POSIX only.
 */
@EnabledOnOs(
        value = {OS.LINUX, OS.MAC},
        disabledReason = "the stand-in Comet and Percolator are POSIX shell scripts")
class ToolRecordIdentityRunTest {

    private static final String COMET_ARTEFACT =
            "https://github.com/UWPR/Comet/releases/download/v2026.03.0/comet.linux.exe"
                    + " sha256:ad93b4cf60c2ed7afc2f41b8a3a05567c3938d999f17deb7b3676bc1fa91e7ed";

    private static final String PERCOLATOR_ARTEFACT =
            "https://github.com/percolator/percolator/releases/download/rel-3-07-01/"
                    + "percolator-noxml-ubuntu-portable.zip"
                    + " sha256:4d0e94af851884ff8ab6a2223e73cf28ba3ced28f6af863d4a76d541009b9dd1";

    private static List<ToolRecord> toolsNamed(ProvenanceManifest manifest, String name) {
        return manifest.tools().stream().filter(tool -> tool.name().equals(name)).toList();
    }

    @Test
    @DisplayName(
            "Comet: every invocation's record holds the selection's probed capabilities, sorted,"
                    + " and the managed artefact it came from")
    void cometCapabilitiesAndArtefact(@TempDir Path directory)
            throws IOException, InterruptedException, RunBlockedException, ReuseRefusedException {
        FakeSearch search = FakeSearch.create(directory);
        ToolWarningsRunTest.exitZero(search.executable());
        CometSelection comet =
                new CometSelection(
                        search.executable(),
                        ToolVersion.parse(RealComet.NEWER),
                        search.executableSha256(),
                        true,
                        Optional.of(COMET_ARTEFACT),
                        List.of(),
                        List.of(),
                        EnumSet.of(
                                ToolCapability.PIN_OUTPUT,
                                ToolCapability.PEPXML_OUTPUT,
                                ToolCapability.COMPLETE_PARAMS_QUERY));
        try (RealProject project = RealProject.create(search.project())) {
            PreparedRun prepared =
                    project.prepare(
                            new SearchRequest(
                                    search.model(DecoySource.COMET_INTERNAL_CONCATENATED),
                                    search.spectra(),
                                    comet,
                                    IndexMode.NONE));
            RunResult result = project.run(prepared);
            assertEquals(AttemptOutcome.FAILED, result.outcome(), "no Comet output was written");

            List<ToolRecord> comets =
                    toolsNamed(
                            ManifestReader.readFrom(prepared.layout().provenanceJsonFile()),
                            "comet");
            Set<String> probed = Set.of("COMPLETE_PARAMS_QUERY", "PEPXML_OUTPUT", "PIN_OUTPUT");
            assertAll(
                    () -> assertEquals(2, comets.size(), "one Comet record per spectrum file"),
                    () ->
                            assertEquals(
                                    List.of("COMPLETE_PARAMS_QUERY", "PEPXML_OUTPUT", "PIN_OUTPUT"),
                                    List.copyOf(comets.get(0).capabilities())),
                    () -> assertEquals(probed, Set.copyOf(comets.get(1).capabilities())),
                    () ->
                            assertEquals(
                                    Optional.of(COMET_ARTEFACT), comets.get(0).artefactIdentity()),
                    () ->
                            assertEquals(
                                    Optional.of(COMET_ARTEFACT), comets.get(1).artefactIdentity()));
        }
    }

    @Test
    @DisplayName("Comet: a selection with no artefact identity records none")
    void cometWithoutArtefact(@TempDir Path directory)
            throws IOException, InterruptedException, RunBlockedException, ReuseRefusedException {
        FakeSearch search = FakeSearch.create(directory);
        ToolWarningsRunTest.exitZero(search.executable());
        try (RealProject project = RealProject.create(search.project())) {
            PreparedRun prepared =
                    project.prepare(
                            search.request(
                                    DecoySource.COMET_INTERNAL_CONCATENATED, IndexMode.NONE));
            project.run(prepared);
            List<ToolRecord> comets =
                    toolsNamed(
                            ManifestReader.readFrom(prepared.layout().provenanceJsonFile()),
                            "comet");
            assertAll(
                    () -> assertEquals(2, comets.size()),
                    () -> assertEquals(Optional.empty(), comets.get(0).artefactIdentity()),
                    () -> assertEquals(List.of(), List.copyOf(comets.get(0).capabilities())));
        }
    }

    @Test
    @DisplayName("Percolator: its record holds the offer's artefact identity")
    void percolatorArtefact(@TempDir Path directory)
            throws IOException, InterruptedException, RunBlockedException, ReuseRefusedException {
        FakeSearch search = FakeSearch.create(directory);
        try (RealProject project = RealProject.create(search.project())) {
            PreparedRun prepared =
                    ToolWarningsRunTest.percolatorRun(
                            search, project, List.of(), Optional.of(PERCOLATOR_ARTEFACT));
            List<ToolRecord> percolators =
                    toolsNamed(
                            ManifestReader.readFrom(prepared.layout().provenanceJsonFile()),
                            "percolator");
            assertAll(
                    () -> assertEquals(1, percolators.size()),
                    () ->
                            assertEquals(
                                    Optional.of(PERCOLATOR_ARTEFACT),
                                    percolators.get(0).artefactIdentity()));
        }
    }

    @Test
    @DisplayName("Percolator: an offer without one records none")
    void percolatorWithoutArtefact(@TempDir Path directory)
            throws IOException, InterruptedException, RunBlockedException, ReuseRefusedException {
        FakeSearch search = FakeSearch.create(directory);
        try (RealProject project = RealProject.create(search.project())) {
            PreparedRun prepared =
                    ToolWarningsRunTest.percolatorRun(search, project, List.of(), Optional.empty());
            List<ToolRecord> percolators =
                    toolsNamed(
                            ManifestReader.readFrom(prepared.layout().provenanceJsonFile()),
                            "percolator");
            assertEquals(Optional.empty(), percolators.get(0).artefactIdentity());
        }
    }
}
