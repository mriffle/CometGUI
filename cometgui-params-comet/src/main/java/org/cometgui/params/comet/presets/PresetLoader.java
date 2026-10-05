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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.model.ParameterValueCodec;
import org.cometgui.params.comet.schema.CuratedMetadata;
import org.cometgui.params.comet.schema.ParameterDefinition;
import org.cometgui.params.comet.value.ValueSyntaxException;
import org.cometgui.provenance.json.JsonParseException;
import org.cometgui.provenance.json.JsonReader;
import org.cometgui.provenance.json.JsonValue;

/**
 * Reads a preset document -- the bundled built-in presets, or a user's presets in the same JSON
 * form -- and refuses one it cannot stand behind, checked against the curated metadata.
 *
 * <p>The document is read with the project's one JSON reader ({@link JsonReader}). Every field is
 * required (JSON {@code null} where a value is absent) and a field the format does not have is an
 * error. On top of that a preset is refused, naming it and the field, when it:
 *
 * <ul>
 *   <li>has no target Comet version, or one the metadata was not curated against;
 *   <li>was made against another metadata {@code schemaVersion};
 *   <li>names a parameter the metadata does not model, or does not model for its version;
 *   <li>holds a value the parameter's codec cannot read for that version (so a preset can never
 *       hold a value the model could not);
 *   <li>breaks a rule of {@link Preset} or {@link PresetDelta}: an id that is not one, an id used
 *       twice, no deltas, a parameter set twice, a built-in without its source or a citation;
 *   <li>cites something that is not an {@code https://} reference.
 * </ul>
 *
 * <p>The format is documented in {@code docs/developer/comet_parameter_schema.rst}.
 */
public final class PresetLoader {

    /** The bundled built-in presets, on the class path beside the curated metadata. */
    public static final String RESOURCE = "/org/cometgui/params/comet/schema/comet-presets.json";

    /** The only {@code presetFormat} this loader reads. */
    public static final int PRESET_FORMAT = 1;

    /** The field named when a preset breaks a rule of {@link Preset} as a whole. */
    public static final String WHOLE_PRESET = "(preset)";

    private static final String HTTPS = "https://";

    private static final List<String> TOP_FIELDS =
            List.of("presetFormat", "description", "presets");

    private static final List<String> PRESET_FIELDS =
            List.of(
                    "id",
                    "displayName",
                    "description",
                    "origin",
                    "cometVersion",
                    "schemaVersion",
                    "source",
                    "deltas");

    private static final List<String> DELTA_FIELDS = List.of("parameter", "value", "citation");

    private PresetLoader() {}

    /**
     * Loads the built-in presets bundled with this module.
     *
     * @param metadata the curated metadata to check them against
     * @return the presets, in file order
     * @throws InvalidPresetException if the bundled file breaks a rule
     * @throws UncheckedIOException if it cannot be read
     */
    public static List<Preset> loadBundled(CuratedMetadata metadata) {
        try (InputStream in = PresetLoader.class.getResourceAsStream(RESOURCE)) {
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
    public static List<Preset> load(String json, CuratedMetadata metadata) {
        JsonValue root;
        try {
            root = JsonReader.parse(json);
        } catch (JsonParseException notJson) {
            throw new InvalidPresetException("the document", "(none)", notJson.getMessage());
        }
        Fields top = Fields.of(root, "the document", "(root)");
        top.only(TOP_FIELDS);
        long format = top.number("presetFormat");
        if (format != PRESET_FORMAT) {
            throw top.failure(
                    "presetFormat",
                    "is " + format + ", and this loader reads format " + PRESET_FORMAT + " only");
        }
        top.text("description");
        List<Preset> presets = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        List<JsonValue> array = top.array("presets");
        for (int index = 0; index < array.size(); index++) {
            Preset preset = preset(array.get(index), index, metadata);
            if (!ids.add(preset.id())) {
                throw new InvalidPresetException(
                        "preset \"" + preset.id() + "\"", "id", "is used by two presets");
            }
            presets.add(preset);
        }
        return List.copyOf(presets);
    }

    private static Preset preset(JsonValue value, int index, CuratedMetadata metadata) {
        Fields unnamed = Fields.of(value, "presets[" + index + "]", "presets");
        String id = unnamed.text("id");
        Fields node = unnamed.renamed("preset \"" + id + "\"");
        node.only(PRESET_FIELDS);
        if (!Preset.ID.matcher(id).matches()) {
            throw node.failure(
                    "id",
                    "is not a preset id: lower-case letters, digits and hyphens, starting with a"
                            + " letter or digit");
        }
        Preset.Origin origin = node.constant("origin", Preset.Origin.class);
        ToolVersion version = targetVersion(node, metadata);
        long schemaVersion = node.number("schemaVersion");
        if (schemaVersion != metadata.schemaVersion()) {
            throw node.failure(
                    "schemaVersion",
                    "is "
                            + schemaVersion
                            + ", and the curated metadata is schema "
                            + metadata.schemaVersion());
        }
        Optional<String> source = node.optionalReference("source");
        ParameterValueCodec codec = ParameterValueCodec.forVersion(metadata, version);
        List<PresetDelta> deltas = new ArrayList<>();
        List<JsonValue> array = node.array("deltas");
        for (int at = 0; at < array.size(); at++) {
            Fields delta = Fields.of(array.get(at), node.where(), "deltas[" + at + "]");
            delta.only(DELTA_FIELDS);
            String parameter = delta.text("parameter");
            Fields named = delta.renamed(node.where() + " delta \"" + parameter + "\"");
            ParameterDefinition definition = definition(named, metadata, parameter, version);
            String text = named.string("value");
            try {
                codec.parse(definition, text);
            } catch (ValueSyntaxException unreadable) {
                throw named.failure("value", unreadable.getMessage());
            }
            deltas.add(new PresetDelta(parameter, text, named.optionalReference("citation")));
        }
        try {
            return new Preset(
                    id,
                    node.text("displayName"),
                    node.text("description"),
                    origin,
                    version,
                    Math.toIntExact(schemaVersion),
                    source,
                    deltas);
        } catch (IllegalArgumentException broken) {
            throw node.failure(WHOLE_PRESET, broken.getMessage());
        }
    }

    private static ToolVersion targetVersion(Fields node, CuratedMetadata metadata) {
        Optional<String> text = node.optionalString("cometVersion");
        if (text.isEmpty() || text.get().isBlank()) {
            throw node.failure(
                    "cometVersion",
                    "is missing; a preset must record the Comet version it was made against");
        }
        ToolVersion version;
        try {
            version = ToolVersion.parse(text.get());
        } catch (IllegalArgumentException notAVersion) {
            throw node.failure("cometVersion", notAVersion.getMessage());
        }
        if (metadata.version(version).isEmpty()) {
            throw node.failure(
                    "cometVersion",
                    "is Comet "
                            + version.text()
                            + ", which the curated metadata does not describe, so its values"
                            + " cannot be read");
        }
        return version;
    }

    private static ParameterDefinition definition(
            Fields named, CuratedMetadata metadata, String parameter, ToolVersion version) {
        if (metadata.parameter(parameter).isEmpty()) {
            throw named.failure(
                    "parameter", "is not a parameter CometGUI models for any Comet version");
        }
        return metadata.parameter(parameter, version)
                .orElseThrow(
                        () ->
                                named.failure(
                                        "parameter",
                                        "is not a parameter of Comet "
                                                + version.text()
                                                + ", the version the preset was made against"));
    }

    /**
     * One JSON object being read, and where it is, so that every failure can say so. Shared with
     * {@link ModificationPresets}, whose documents follow the same rules.
     */
    record Fields(JsonValue.JsonObject object, String where) {

        static Fields of(JsonValue value, String where, String field) {
            if (!(value instanceof JsonValue.JsonObject jsonObject)) {
                throw new InvalidPresetException(where, field, "must be a JSON object");
            }
            return new Fields(jsonObject, where);
        }

        Fields renamed(String newWhere) {
            return new Fields(object, newWhere);
        }

        InvalidPresetException failure(String field, String problem) {
            return new InvalidPresetException(where, field, problem);
        }

        void only(List<String> allowed) {
            for (String field : allowed) {
                required(field);
            }
            for (String present : object.members().keySet()) {
                if (!allowed.contains(present)) {
                    throw failure(
                            present, "is not a field this format has; expected only " + allowed);
                }
            }
        }

        JsonValue required(String field) {
            return object.member(field)
                    .orElseThrow(
                            () ->
                                    failure(
                                            field,
                                            "is missing; every field is required, null where"
                                                    + " absent"));
        }

        String string(String field) {
            if (!(required(field) instanceof JsonValue.JsonString text)) {
                throw failure(field, "must be a string");
            }
            return text.value();
        }

        String text(String field) {
            String text = string(field);
            if (text.isBlank()) {
                throw failure(field, "is blank");
            }
            return text;
        }

        Optional<String> optionalString(String field) {
            JsonValue value = required(field);
            if (value instanceof JsonValue.JsonNull) {
                return Optional.empty();
            }
            if (!(value instanceof JsonValue.JsonString text)) {
                throw failure(field, "must be a string or null");
            }
            return Optional.of(text.value());
        }

        Optional<String> optionalReference(String field) {
            Optional<String> text = optionalString(field);
            if (text.isPresent() && !text.get().contains(HTTPS)) {
                throw failure(field, "\"" + text.get() + "\" cites no https:// reference");
            }
            return text;
        }

        long number(String field) {
            if (!(required(field) instanceof JsonValue.JsonNumber number)) {
                throw failure(field, "must be a whole number");
            }
            return number.value();
        }

        List<JsonValue> array(String field) {
            if (!(required(field) instanceof JsonValue.JsonArray array)) {
                throw failure(field, "must be an array");
            }
            return array.elements();
        }

        <E extends Enum<E>> E constant(String field, Class<E> type) {
            String text = string(field);
            for (E constant : type.getEnumConstants()) {
                if (constant.name().equals(text)) {
                    return constant;
                }
            }
            throw failure(
                    field,
                    "\"" + text + "\" is not one of " + Arrays.toString(type.getEnumConstants()));
        }
    }
}
