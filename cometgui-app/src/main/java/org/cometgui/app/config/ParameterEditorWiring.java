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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.cometgui.domain.build.BuildIdentity;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.install.registry.ArtefactManifest;
import org.cometgui.install.registry.ArtefactManifestReader;
import org.cometgui.install.registry.ArtefactRecord;
import org.cometgui.params.comet.parser.ReleaseDefaults;
import org.cometgui.params.comet.schema.CuratedMetadata;
import org.cometgui.params.comet.schema.MetadataLoader;
import org.cometgui.provenance.hashing.StreamingHashService;
import org.cometgui.ui.viewmodel.params.FileChooserPort;
import org.cometgui.ui.viewmodel.params.ParameterEditorViewModel;
import org.cometgui.ui.viewmodel.params.ParameterSession;
import org.cometgui.ui.viewmodel.params.RunReadinessViewModel;
import org.cometgui.ui.viewmodel.params.SpectrumInputsViewModel;
import org.cometgui.ui.viewmodel.params.StageSwitches;

/**
 * The Comet parameter editor's composition: which releases it offers, and the seams it is built
 * over.
 *
 * <h2>The releases offered are derived, never listed (decision P7-2)</h2>
 *
 * <p>A release is offered when three facts agree: the shipped artefact manifest publishes Comet at
 * that version (so the Tool Manager can install it), the curated parameter metadata describes it,
 * and the parameter model bundles its {@code comet -q} starting set. They are offered newest first,
 * and the newest is the default. Today that is Comet 2026.03.0, then 2026.02.2; the 2024.01.0
 * migration fixture is described by the metadata but neither published nor bundled, so it is never
 * offered. No list of versions is written here or in the interface.
 *
 * <h2>The seams</h2>
 *
 * <p>The session, the spectrum inputs and the variable-modification editor are made by the caller
 * and injected into the views, never published by a holder (see {@code ParameterEditorViewModel}).
 * The hash service is a {@link StreamingHashService} of the editor's own (a saved parameter file is
 * hashed once, by the file on disk); the build identity and the file chooser are the caller's, so
 * that a GUI test can name the build in the header and answer the chooser itself. Every downstream
 * stage is enabled ({@link StageSwitches#ALL_ENABLED}): no stage can be switched off before Phases
 * 11 and 12. The Run control says the workflow engine is not built ({@link
 * RunReadinessViewModel#ENGINE_NOT_BUILT}) until Phase 08 wires one.
 */
public final class ParameterEditorWiring {

    private ParameterEditorWiring() {}

    /**
     * A new configuration of the default release, every offered release derived.
     *
     * @return the session
     * @throws UncheckedIOException if the artefact manifest cannot be read
     * @throws IllegalStateException if no release can be offered
     */
    public static ParameterSession newSession() {
        CuratedMetadata metadata = MetadataLoader.loadBundled();
        ArtefactManifest manifest;
        try {
            manifest = ArtefactManifestReader.readFromClasspath();
        } catch (IOException unreadable) {
            throw new UncheckedIOException(
                    "the shipped artefact manifest cannot be read, so the Comet releases to offer"
                            + " are unknown",
                    unreadable);
        }
        return new ParameterSession(
                metadata, offeredReleases(manifest, metadata), StageSwitches.ALL_ENABLED);
    }

    /**
     * The editor's state over a session: saving through a {@link StreamingHashService} of its own,
     * and the Run control saying the workflow engine is not built.
     *
     * @param session the session
     * @param inputs the spectrum inputs over the session
     * @param chooser the file chooser
     * @param build the running build
     * @return the editor's state
     */
    public static ParameterEditorViewModel editor(
            ParameterSession session,
            SpectrumInputsViewModel inputs,
            FileChooserPort chooser,
            BuildIdentity build) {
        return new ParameterEditorViewModel(
                session,
                inputs,
                chooser,
                build,
                new StreamingHashService(),
                Optional.of(RunReadinessViewModel.ENGINE_NOT_BUILT));
    }

    /**
     * The Comet releases the editor offers: published by the manifest, described by the metadata
     * and bundled with a starting set, newest first.
     *
     * @param manifest the artefact manifest
     * @param metadata the curated metadata
     * @return the releases, the default first
     * @throws IllegalStateException if no release satisfies all three
     */
    public static List<ToolVersion> offeredReleases(
            ArtefactManifest manifest, CuratedMetadata metadata) {
        Objects.requireNonNull(manifest, "manifest");
        Objects.requireNonNull(metadata, "metadata");
        List<ToolVersion> offered =
                manifest.artefacts().stream()
                        .filter(artefact -> artefact.tool() == ToolName.COMET)
                        .map(ArtefactRecord::version)
                        .distinct()
                        .filter(version -> metadata.version(version).isPresent())
                        .filter(ReleaseDefaults::isBundled)
                        .sorted(Comparator.reverseOrder())
                        .toList();
        if (offered.isEmpty()) {
            throw new IllegalStateException(
                    "no Comet release can be offered: none is published by the artefact manifest,"
                            + " described by the parameter metadata and bundled with a starting"
                            + " set");
        }
        return offered;
    }
}
