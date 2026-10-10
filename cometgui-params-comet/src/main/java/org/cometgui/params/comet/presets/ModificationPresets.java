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
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.schema.CuratedMetadata;
import org.cometgui.params.comet.value.ValueSyntaxException;
import org.cometgui.params.comet.value.VariableModCodec;
import org.cometgui.params.comet.value.VariableModification;
import org.cometgui.provenance.json.JsonParseException;
import org.cometgui.provenance.json.JsonReader;
import org.cometgui.provenance.json.JsonValue;

/**
 * The common variable modifications offered by the variable-modification editor ({@code
 * R-PARAM-09}, "common modification presets"), read from the bundled {@code
 * comet-modification-presets.json} and checked against the curated metadata.
 *
 * <p>The document has the preset file's rules: read with the project's one JSON reader, every field
 * required and no other allowed. A preset is refused, naming it and the field, when its id is not
 * one or is used twice, it lists no release or one release twice, a listed {@code cometVersion} is
 * not a curated release, its tuple is not one every listed release reads, its mass difference is 0
 * (a preset that leaves a slot unused), or a source cites no {@code https://} reference -- and the
 * mass source must quote the mass exactly as the tuple writes it, so that a mass changed in one
 * place and not the other is refused.
 *
 * <p>Which presets a release offers is {@link #offeredIn(ToolVersion)}: exactly those that list it.
 * The release list is data, so one modification can be offered in one form per release -- protein
 * N-terminal acetylation as {@code n} at distance 0 for 2026.02.2 and as {@code ^} for 2026.03.0 --
 * and no code tests a version.
 */
public final class ModificationPresets {

    /** The bundled presets, on the class path beside the curated metadata. */
    public static final String RESOURCE =
            "/org/cometgui/params/comet/schema/comet-modification-presets.json";

    /** The only {@code modificationPresetFormat} this loader reads. */
    public static final int FORMAT = 2;

    private static final String HTTPS = "https://";

    private static final List<String> TOP_FIELDS =
            List.of("modificationPresetFormat", "description", "presets");

    private static final List<String> PRESET_FIELDS =
            List.of("id", "name", "description", "tuple", "massSource", "releases");

    private static final List<String> RELEASE_FIELDS = List.of("cometVersion", "formSource");

    private final List<ModificationPreset> presets;

    private ModificationPresets(List<ModificationPreset> presets) {
        this.presets = List.copyOf(presets);
    }

    /**
     * Loads the presets bundled with this module.
     *
     * @param metadata the curated metadata to check them against
     * @return the presets
     * @throws InvalidPresetException if the bundled file breaks a rule
     * @throws UncheckedIOException if it cannot be read
     */
    public static ModificationPresets loadBundled(CuratedMetadata metadata) {
        try (InputStream in = ModificationPresets.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new UncheckedIOException(
                        new IOException("the class path holds no " + RESOURCE));
            }
            return load(new String(in.readAllBytes(), StandardCharsets.UTF_8), metadata);
        } catch (IOException unreadable) {
            throw new UncheckedIOException("cannot read " + RESOURCE, unreadable);
        }
    }

    /**
     * Loads presets from a document.
     *
     * @param json the document
     * @param metadata the curated metadata to check them against
     * @return the presets, in document order
     * @throws InvalidPresetException if the document is not JSON or breaks a rule
     */
    public static ModificationPresets load(String json, CuratedMetadata metadata) {
        Objects.requireNonNull(metadata, "metadata");
        JsonValue root;
        try {
            root = JsonReader.parse(json);
        } catch (JsonParseException notJson) {
            throw new InvalidPresetException("the document", "(none)", notJson.getMessage());
        }
        PresetLoader.Fields top = PresetLoader.Fields.of(root, "the document", "(root)");
        top.only(TOP_FIELDS);
        long format = top.number("modificationPresetFormat");
        if (format != FORMAT) {
            throw top.failure(
                    "modificationPresetFormat",
                    "is " + format + ", and this loader reads format " + FORMAT + " only");
        }
        top.text("description");
        List<ModificationPreset> presets = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        List<JsonValue> array = top.array("presets");
        for (int index = 0; index < array.size(); index++) {
            ModificationPreset preset = preset(array.get(index), index, metadata);
            if (!ids.add(preset.id())) {
                throw new InvalidPresetException(
                        "modification preset \"" + preset.id() + "\"",
                        "id",
                        "is used by two presets");
            }
            presets.add(preset);
        }
        return new ModificationPresets(presets);
    }

    private static ModificationPreset preset(JsonValue value, int index, CuratedMetadata metadata) {
        PresetLoader.Fields unnamed =
                PresetLoader.Fields.of(value, "presets[" + index + "]", "presets");
        String id = unnamed.text("id");
        PresetLoader.Fields node = unnamed.renamed("modification preset \"" + id + "\"");
        node.only(PRESET_FIELDS);
        if (!Preset.ID.matcher(id).matches()) {
            throw node.failure(
                    "id",
                    "is not a preset id: lower-case letters, digits and hyphens, starting with a"
                            + " letter or digit");
        }
        String tuple = node.text("tuple");
        List<JsonValue> listed = node.array("releases");
        if (listed.isEmpty()) {
            throw node.failure("releases", "lists no release, so the preset is offered nowhere");
        }
        List<ModificationPreset.Release> releases = new ArrayList<>();
        Set<ToolVersion> seen = new HashSet<>();
        VariableModification modification = null;
        for (int at = 0; at < listed.size(); at++) {
            PresetLoader.Fields entry =
                    PresetLoader.Fields.of(
                            listed.get(at),
                            "modification preset \"" + id + "\" releases[" + at + "]",
                            "releases");
            entry.only(RELEASE_FIELDS);
            ToolVersion version = release(entry, metadata);
            if (!seen.add(version)) {
                throw entry.failure(
                        "cometVersion", "lists Comet " + version.text() + " twice for one preset");
            }
            VariableModification read = readIn(node, metadata, version, tuple);
            if (modification == null) {
                modification = read;
            }
            releases.add(new ModificationPreset.Release(version, source(entry, "formSource")));
        }
        if (modification.isUnused()) {
            throw node.failure(
                    "tuple",
                    "\"" + tuple + "\" has a mass difference of 0, which leaves a slot unused");
        }
        String massSource = source(node, "massSource");
        String mass = modification.mass().toPlainString();
        if (!massSource.contains(mass)) {
            throw node.failure(
                    "massSource",
                    "does not quote the mass difference " + mass + " the tuple writes");
        }
        return new ModificationPreset(
                id,
                node.text("name"),
                node.text("description"),
                tuple,
                modification,
                massSource,
                releases);
    }

    /*
     * The tuple read with one listed release's own codec, so a preset is never listed for a release
     * that cannot read it -- a tuple written with ^ listed for 2026.02.2 is refused here.
     * ModificationPresetsTest also holds every offered preset to its release's
     * VariableModSlots.unwritable, which the old slot-driven offer used.
     */
    private static VariableModification readIn(
            PresetLoader.Fields node, CuratedMetadata metadata, ToolVersion version, String tuple) {
        VariableModCodec codec = VariableModCodec.forVersion(metadata, version);
        try {
            return codec.parse(codec.slots().get(0), tuple);
        } catch (ValueSyntaxException unreadable) {
            throw node.failure("tuple", unreadable.getMessage());
        }
    }

    private static ToolVersion release(PresetLoader.Fields node, CuratedMetadata metadata) {
        String text = node.text("cometVersion");
        ToolVersion version;
        try {
            version = ToolVersion.parse(text);
        } catch (IllegalArgumentException notAVersion) {
            throw node.failure("cometVersion", notAVersion.getMessage());
        }
        if (metadata.version(version).isEmpty()) {
            throw node.failure(
                    "cometVersion",
                    "is Comet "
                            + version.text()
                            + ", which the curated metadata does not describe, so its tuple"
                            + " cannot be read");
        }
        return version;
    }

    private static String source(PresetLoader.Fields node, String field) {
        String text = node.text(field);
        if (!text.contains(HTTPS)) {
            throw node.failure(field, "\"" + text + "\" cites no https:// reference");
        }
        return text;
    }

    /**
     * Every preset, in document order.
     *
     * @return the presets
     */
    public List<ModificationPreset> all() {
        return presets;
    }

    /**
     * The presets a release offers: exactly those that list it. The loader has already refused a
     * preset that lists a release none of whose slots can hold it.
     *
     * @param release the Comet release
     * @return the presets, in document order
     */
    public List<ModificationPreset> offeredIn(ToolVersion release) {
        Objects.requireNonNull(release, "release");
        return presets.stream().filter(preset -> preset.offeredFor(release)).toList();
    }

    /**
     * The preset with an id.
     *
     * @param id the id
     * @return the preset, or empty
     */
    public Optional<ModificationPreset> byId(String id) {
        Objects.requireNonNull(id, "id");
        return presets.stream().filter(preset -> preset.id().equals(id)).findFirst();
    }
}
