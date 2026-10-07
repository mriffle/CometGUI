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

package org.cometgui.app.config;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.cometgui.app.testing.ScriptedChooser;
import org.cometgui.domain.build.BuildIdentity;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.install.registry.ArtefactManifest;
import org.cometgui.install.registry.ArtefactManifestReader;
import org.cometgui.install.registry.ArtefactRecord;
import org.cometgui.params.comet.schema.CuratedMetadata;
import org.cometgui.params.comet.schema.MetadataLoader;
import org.cometgui.ui.viewmodel.params.ExpertViewModel;
import org.cometgui.ui.viewmodel.params.ParameterEditorViewModel;
import org.cometgui.ui.viewmodel.params.ParameterSession;
import org.cometgui.ui.viewmodel.params.RunReadinessViewModel;
import org.cometgui.ui.viewmodel.params.SpectrumInputsViewModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The parameter editor's composition: the releases offered are derived from the shipped manifest,
 * the metadata and the bundled starting sets -- never listed -- and the default is the newest.
 */
class ParameterEditorWiringTest {

    private static final CuratedMetadata METADATA = MetadataLoader.loadBundled();

    @Test
    @DisplayName("the shipped manifest gives 2026.03.0 then 2026.02.2, the newest first")
    void offeredFromTheShippedManifest() throws IOException {
        List<ToolVersion> offered =
                ParameterEditorWiring.offeredReleases(
                        ArtefactManifestReader.readFromClasspath(), METADATA);
        assertEquals(
                List.of("2026.03.0", "2026.02.2"),
                offered.stream().map(ToolVersion::text).toList());
    }

    @Test
    @DisplayName("only what the manifest publishes is offered, and none at all is refused")
    void onlyWhatIsPublished() throws IOException {
        ArtefactManifest shipped = ArtefactManifestReader.readFromClasspath();
        ArtefactManifest older =
                new ArtefactManifest(
                        shipped.schemaVersion(),
                        shipped.artefacts().stream()
                                .filter(
                                        artefact ->
                                                artefact.tool() != ToolName.COMET
                                                        || artefact.version()
                                                                .text()
                                                                .equals("2026.02.2"))
                                .toList());
        assertEquals(
                List.of("2026.02.2"),
                ParameterEditorWiring.offeredReleases(older, METADATA).stream()
                        .map(ToolVersion::text)
                        .toList());

        ArtefactManifest noComet =
                new ArtefactManifest(
                        shipped.schemaVersion(),
                        shipped.artefacts().stream()
                                .filter(artefact -> artefact.tool() != ToolName.COMET)
                                .toList());
        IllegalStateException refused =
                assertThrows(
                        IllegalStateException.class,
                        () -> ParameterEditorWiring.offeredReleases(noComet, METADATA));
        assertEquals(
                "no Comet release can be offered: none is published by the artefact manifest,"
                        + " described by the parameter metadata and bundled with a starting set",
                refused.getMessage());

        // Another tool published at a Comet release's version number is not Comet.
        ArtefactRecord pdv =
                shipped.artefacts().stream()
                        .filter(artefact -> artefact.tool() == ToolName.PDV)
                        .findFirst()
                        .orElseThrow();
        ArtefactRecord pdvAtACometVersion =
                new ArtefactRecord(
                        pdv.tool(),
                        ToolVersion.parse("2026.03.0"),
                        pdv.releaseTag(),
                        pdv.platform(),
                        pdv.kind(),
                        pdv.url(),
                        pdv.sizeBytes(),
                        pdv.hashes(),
                        pdv.member(),
                        pdv.expectedExecutablePath(),
                        pdv.executable(),
                        pdv.licence(),
                        pdv.companions(),
                        pdv.capabilities(),
                        pdv.advisories(),
                        pdv.minimumHostRequirements(),
                        pdv.minimumCometGuiVersion());
        ArtefactManifest notComet =
                new ArtefactManifest(shipped.schemaVersion(), List.of(pdvAtACometVersion));
        assertThrows(
                IllegalStateException.class,
                () -> ParameterEditorWiring.offeredReleases(notComet, METADATA));
    }

    @Test
    @DisplayName("the session starts on the default release; the engine reason is stated")
    void theApplicationsEditor() {
        BuildIdentity build =
                BuildIdentity.of("0.0.0-test", "unknown", Instant.parse("2026-10-05T00:00:00Z"));
        ParameterSession session = ParameterEditorWiring.newSession();
        ScriptedChooser chooser = new ScriptedChooser();
        SpectrumInputsViewModel inputs =
                new SpectrumInputsViewModel(
                        session, chooser, ApplicationServices.forThisHost().fileSystem());
        ParameterEditorViewModel editor =
                ParameterEditorWiring.editor(session, inputs, chooser, build);
        assertAll(
                () -> assertEquals("2026.03.0", session.release().text()),
                () ->
                        assertEquals(
                                List.of("2026.03.0", "2026.02.2"),
                                session.offeredReleases().stream().map(ToolVersion::text).toList()),
                () ->
                        assertEquals(
                                List.of(RunReadinessViewModel.ENGINE_NOT_CHECKED),
                                editor.readiness().engineReasons(),
                                "the engine's half waits for the Run section's first check"),
                () -> assertEquals("1", session.model().text("output_percolatorfile")));
    }

    @Test
    @DisplayName("the Expert level names the running build and compares with the editor's saves")
    void theExpertLevel(@TempDir Path directory) {
        BuildIdentity build =
                BuildIdentity.of("0.0.0-test", "unknown", Instant.parse("2026-10-05T00:00:00Z"));
        ParameterSession session = ParameterEditorWiring.newSession();
        ScriptedChooser chooser = new ScriptedChooser();
        SpectrumInputsViewModel inputs =
                new SpectrumInputsViewModel(
                        session, chooser, ApplicationServices.forThisHost().fileSystem());
        ParameterEditorViewModel editor =
                ParameterEditorWiring.editor(session, inputs, chooser, build);
        ExpertViewModel expert = ParameterEditorWiring.expert(session, editor, build);
        assertEquals(
                "# Written by CometGUI 0.0.0-test for Comet 2026.03.0. Canonical form, generated"
                        + " from the typed model.",
                expert.canonicalText().lines().toList().get(1));
        assertEquals(
                Optional.of("Nothing has been saved yet."),
                expert.againstLastSaved().unavailable());
        editor.files().save(directory.resolve("saved.params"));
        session.edit("num_threads", "3");
        assertEquals(
                List.of("num_threads"),
                expert.againstLastSaved().rows().stream().map(r -> r.row().key()).toList());
    }
}
