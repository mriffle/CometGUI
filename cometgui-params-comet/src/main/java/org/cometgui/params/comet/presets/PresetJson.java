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

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.cometgui.domain.secrets.SecretRedactor;
import org.cometgui.params.comet.schema.CuratedMetadata;
import org.cometgui.provenance.json.JsonWriter;

/**
 * The JSON form of presets: the document {@link PresetLoader} reads, written with the project's one
 * JSON writer. User presets are kept in memory and in this form until a project file holds them (a
 * later phase).
 *
 * <p>The writer passes every string through the project's secret rules ({@link JsonWriter} has no
 * other mode), so a value that looked like a secret would be written redacted and would not read
 * back as itself. {@link #write} therefore reads its own output back and refuses to return a
 * document that does not load as exactly the presets it was given: a preset is never saved changed.
 */
public final class PresetJson {

    private PresetJson() {}

    /**
     * Writes presets as a preset document.
     *
     * @param description what the document holds
     * @param presets the presets
     * @param metadata the curated metadata the presets were checked against
     * @return the document, which {@link PresetLoader#load} reads back as {@code presets}
     * @throws IllegalStateException if the document would not read back as the same presets
     * @throws InvalidPresetException if a preset does not pass the loader's checks
     */
    public static String write(String description, List<Preset> presets, CuratedMetadata metadata) {
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(presets, "presets");
        Objects.requireNonNull(metadata, "metadata");
        JsonWriter json = JsonWriter.redactingWith(SecretRedactor.patternsOnly());
        json.beginObject();
        json.name("presetFormat").value(PresetLoader.PRESET_FORMAT);
        json.name("description").value(description);
        json.name("presets").beginArray();
        for (Preset preset : presets) {
            json.beginObject();
            json.name("id").value(preset.id());
            json.name("displayName").value(preset.displayName());
            json.name("description").value(preset.description());
            json.name("origin").value(preset.origin().name());
            json.name("cometVersion").value(preset.cometVersion().text());
            json.name("schemaVersion").value(preset.schemaVersion());
            optional(json.name("source"), preset.source());
            json.name("deltas").beginArray();
            for (PresetDelta delta : preset.deltas()) {
                json.beginObject();
                json.name("parameter").value(delta.parameter());
                json.name("value").value(delta.value());
                optional(json.name("citation"), delta.citation());
                json.endObject();
            }
            json.endArray();
            json.endObject();
        }
        json.endArray();
        json.endObject();
        String document = json.finish();
        List<Preset> readBack = PresetLoader.load(document, metadata);
        if (!readBack.equals(presets)) {
            throw new IllegalStateException(
                    "the JSON form of these presets does not read back as the same presets (a"
                            + " value may have matched a secret pattern and been redacted); they"
                            + " are not written");
        }
        return document;
    }

    private static void optional(JsonWriter json, Optional<String> value) {
        if (value.isPresent()) {
            json.value(value.get());
        } else {
            json.nullValue();
        }
    }
}
