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

package org.cometgui.params.comet.presets;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.fixtures.CometFixtures;
import org.cometgui.params.comet.fixtures.MigrationFixtures;
import org.cometgui.params.comet.fixtures.ParamsFiles;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.parser.CometParamsParser;
import org.cometgui.params.comet.schema.CuratedMetadata;
import org.cometgui.params.comet.schema.MetadataLoader;

/** The real parameter sets and the built-in presets the preset tests use. */
final class Real {

    static final CuratedMetadata METADATA = MetadataLoader.loadBundled();

    static final ToolVersion NEWER = ParamsFiles.COMET;

    static final ToolVersion OLDER = ToolVersion.parse(MigrationFixtures.VERSION);

    static final List<Preset> BUILT_IN = PresetLoader.loadBundled(METADATA);

    private Real() {}

    /** The real Comet 2026.02.2 -q file, parsed. */
    static CometParameters newer() {
        return new CometParamsParser(METADATA, NEWER)
                .parse(ParamsFiles.complete())
                .model()
                .orElseThrow();
    }

    /** The real Comet 2024.01.0 -q file (the migration fixture), parsed as 2024.01.0. */
    static CometParameters older() {
        try {
            return new CometParamsParser(METADATA, OLDER)
                    .parse(MigrationFixtures.text(CometFixtures.Mode.COMPLETE))
                    .model()
                    .orElseThrow();
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    static Preset builtIn(String id) {
        return BUILT_IN.stream().filter(p -> p.id().equals(id)).findFirst().orElseThrow();
    }

    /** A CONSTRUCTED user preset: test input, made here. */
    static Preset user(ToolVersion version, String... nameThenValue) {
        List<PresetDelta> deltas = new java.util.ArrayList<>();
        for (int at = 0; at < nameThenValue.length; at += 2) {
            deltas.add(new PresetDelta(nameThenValue[at], nameThenValue[at + 1], Optional.empty()));
        }
        return new Preset(
                "constructed",
                "Constructed",
                "A constructed user preset.",
                Preset.Origin.USER,
                version,
                1,
                Optional.empty(),
                deltas);
    }
}
