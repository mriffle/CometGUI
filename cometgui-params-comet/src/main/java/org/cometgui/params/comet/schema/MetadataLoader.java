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

package org.cometgui.params.comet.schema;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.parser.ParamsLineReader;
import org.cometgui.provenance.json.JsonParseException;
import org.cometgui.provenance.json.JsonReader;
import org.cometgui.provenance.json.JsonValue;

/**
 * Reads the curated metadata file into a {@link CuratedMetadata}, and refuses a file it cannot
 * stand behind.
 *
 * <p>The file is read with the project's one JSON reader, {@link JsonReader}, which accepts whole
 * numbers only; that is why every bound and default here is a JSON string. On top of JSON
 * well-formedness this loader enforces the metadata's own rules, and names the parameter and field
 * that broke one: every field present (JSON {@code null} where a value is absent) and no field it
 * does not know; a parameter name Comet could declare, used once; a non-blank display name and
 * help; a known category, kind, visibility, serialisation and validator; a serialisation the kind
 * allows; labelled, distinct choices for an enumerated kind and none for any other; a default that
 * fits its kind, its choices and its own bounds; bounds only on numeric kinds, with {@code min <=
 * max}; a version range starting at a curated version; related parameters that exist; an allow-list
 * entry with a reason, for a parameter that is not also modelled; and an enzyme-table section
 * consistent with the enzyme-reference parameters. {@code R-PARAM-04} is enforced here too: a
 * tolerance-pair member may not carry the generic ordering rule.
 *
 * <p>The format is documented in {@code docs/developer/comet_parameter_schema.rst}; the
 * documentation generator reads the same file with Python's standard library.
 */
public final class MetadataLoader {

    /** The bundled metadata, on the class path. */
    public static final String RESOURCE = "/org/cometgui/params/comet/schema/comet-parameters.json";

    /** The only {@code schemaVersion} this loader reads. */
    public static final int SCHEMA_VERSION = 1;

    private static final Pattern NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private static final Pattern WHOLE = Pattern.compile("-?[0-9]+");

    private static final Pattern NON_NEGATIVE_WHOLE = Pattern.compile("[0-9]+");

    private static final String HTTPS = "https://";

    private static final List<String> TOP_FIELDS =
            List.of(
                    "schemaVersion",
                    "description",
                    "versions",
                    "categories",
                    "enzymeTable",
                    "internal",
                    "parameters");

    private static final List<String> VERSION_FIELDS =
            List.of("version", "marker", "parameterPages", "source", "variableModTuple");

    private static final List<String> CATEGORY_FIELDS = List.of("id", "displayName");

    private static final List<String> ENZYME_FIELDS =
            List.of("header", "helpUrl", "rowFormat", "senseChoices", "referencedBy");

    private static final List<String> CHOICE_FIELDS = List.of("value", "label");

    private static final List<String> INTERNAL_FIELDS = List.of("name", "reason");

    private static final List<String> RANGE_FIELDS = List.of("from", "through");

    private static final List<String> PARAMETER_FIELDS =
            List.of(
                    "name",
                    "displayName",
                    "category",
                    "kind",
                    "visibility",
                    "default",
                    "min",
                    "max",
                    "choices",
                    "shortHelp",
                    "helpUrl",
                    "versions",
                    "serialization",
                    "validators",
                    "aliases",
                    "related");

    private MetadataLoader() {}

    /**
     * Loads the metadata bundled with this module.
     *
     * @return the checked metadata
     * @throws InvalidMetadataException if the bundled file breaks a rule
     * @throws UncheckedIOException if it cannot be read
     */
    public static CuratedMetadata loadBundled() {
        try (InputStream in = MetadataLoader.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new UncheckedIOException(
                        new IOException("the class path holds no " + RESOURCE));
            }
            return load(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException unreadable) {
            throw new UncheckedIOException("cannot read " + RESOURCE, unreadable);
        }
    }

    /**
     * Loads metadata from a document.
     *
     * @param json the metadata file's text
     * @return the checked metadata
     * @throws InvalidMetadataException if the document is not JSON or breaks a rule, naming the
     *     parameter or section and the field
     */
    public static CuratedMetadata load(String json) {
        JsonValue root;
        try {
            root = JsonReader.parse(json);
        } catch (JsonParseException notJson) {
            throw new InvalidMetadataException("the document", "(none)", notJson.getMessage());
        }
        Node top = Node.of(root, "the document", "(root)");
        top.onlyFields(TOP_FIELDS);
        long schemaVersion = top.number("schemaVersion");
        if (schemaVersion != SCHEMA_VERSION) {
            throw top.failure(
                    "schemaVersion",
                    "is "
                            + schemaVersion
                            + ", and this loader reads version "
                            + SCHEMA_VERSION
                            + " only");
        }
        top.text("description");
        List<CometVersionRecord> versions = versions(top);
        categories(top);
        List<ParameterDefinition> parameters = parameters(top, versions);
        List<InternalParameter> internal = internal(top, parameters);
        EnzymeTableMetadata enzymeTable = enzymeTable(top, parameters);
        return new CuratedMetadata(SCHEMA_VERSION, versions, parameters, internal, enzymeTable);
    }

    private static List<CometVersionRecord> versions(Node top) {
        List<JsonValue> array = top.array("versions");
        if (array.isEmpty()) {
            throw top.failure(
                    "versions", "is empty; the metadata must name the Comet versions it describes");
        }
        List<CometVersionRecord> records = new ArrayList<>();
        Set<ToolVersion> seen = new HashSet<>();
        for (int index = 0; index < array.size(); index++) {
            Node node = Node.of(array.get(index), "versions[" + index + "]", "versions");
            node.onlyFields(VERSION_FIELDS);
            ToolVersion version = node.version("version");
            CometVersionMarker marker;
            try {
                marker = CometVersionMarker.parse(node.text("marker"));
            } catch (IllegalArgumentException unparseable) {
                throw node.failure("marker", unparseable.getMessage());
            }
            if (!marker.toolVersion().equals(version)) {
                throw node.failure(
                        "marker",
                        "\""
                                + marker.text()
                                + "\" is Comet "
                                + marker.toolVersion().text()
                                + ", not "
                                + version.text());
            }
            if (!seen.add(version)) {
                throw node.failure("version", version.text() + " is listed twice");
            }
            if (!(node.required("variableModTuple") instanceof JsonValue.JsonNull)) {
                throw node.failure(
                        "variableModTuple",
                        "must be null until the tuple layout is modelled; this loader would"
                                + " otherwise accept a layout it does not read");
            }
            records.add(
                    new CometVersionRecord(
                            version, marker, node.url("parameterPages"), node.url("source")));
        }
        return records;
    }

    private static void categories(Node top) {
        List<JsonValue> array = top.array("categories");
        ParameterCategory[] expected = ParameterCategory.values();
        if (array.size() != expected.length) {
            throw top.failure(
                    "categories",
                    "lists "
                            + array.size()
                            + " categories, and the specification's Advanced mode has "
                            + expected.length
                            + ": "
                            + Arrays.stream(expected).map(ParameterCategory::id).toList());
        }
        for (int index = 0; index < expected.length; index++) {
            Node node = Node.of(array.get(index), "categories[" + index + "]", "categories");
            node.onlyFields(CATEGORY_FIELDS);
            String id = node.text("id");
            if (!id.equals(expected[index].id())) {
                throw node.failure(
                        "id", "is \"" + id + "\" where \"" + expected[index].id() + "\" belongs");
            }
            String displayName = node.text("displayName");
            if (!displayName.equals(expected[index].displayName())) {
                throw node.failure(
                        "displayName",
                        "is \""
                                + displayName
                                + "\" where \""
                                + expected[index].displayName()
                                + "\" belongs");
            }
        }
    }

    private static List<ParameterDefinition> parameters(
            Node top, List<CometVersionRecord> versions) {
        List<JsonValue> array = top.array("parameters");
        Map<String, ParameterDefinition> byName = new LinkedHashMap<>();
        Set<ToolVersion> curated = new HashSet<>();
        versions.forEach(record -> curated.add(record.version()));
        for (int index = 0; index < array.size(); index++) {
            Node unnamed = Node.of(array.get(index), "parameters[" + index + "]", "parameters");
            String name = unnamed.text("name");
            if (!NAME.matcher(name).matches()) {
                throw unnamed.failure("name", "\"" + name + "\" is not a name Comet could declare");
            }
            Node node = unnamed.renamed("parameter \"" + name + "\"");
            if (byName.containsKey(name)) {
                throw node.failure("name", "is defined twice");
            }
            node.onlyFields(PARAMETER_FIELDS);
            byName.put(name, parameter(node, name, curated));
        }
        for (ParameterDefinition definition : byName.values()) {
            Node node = new Node(null, "parameter \"" + definition.name() + "\"");
            Set<String> seen = new HashSet<>();
            for (String related : definition.related()) {
                if (related.equals(definition.name())) {
                    throw node.failure("related", "names the parameter itself");
                }
                if (!byName.containsKey(related)) {
                    throw node.failure(
                            "related", "names \"" + related + "\", which is not modelled");
                }
                if (!seen.add(related)) {
                    throw node.failure("related", "names \"" + related + "\" twice");
                }
            }
        }
        return List.copyOf(byName.values());
    }

    private static ParameterDefinition parameter(Node node, String name, Set<ToolVersion> curated) {
        String displayName = node.text("displayName");
        String categoryId = node.string("category");
        ParameterCategory category =
                ParameterCategory.fromId(categoryId)
                        .orElseThrow(
                                () ->
                                        node.failure(
                                                "category",
                                                "\""
                                                        + categoryId
                                                        + "\" is not one of "
                                                        + Arrays.stream(ParameterCategory.values())
                                                                .map(ParameterCategory::id)
                                                                .toList()));
        ValueKind kind = node.constant("kind", ValueKind.class);
        VisibilityLevel visibility = node.constant("visibility", VisibilityLevel.class);
        SerializationRule serialization = node.constant("serialization", SerializationRule.class);
        if (!kind.allows(serialization)) {
            throw node.failure("serialization", serialization + " does not fit kind " + kind);
        }
        List<Choice> choices = choices(node, kind);
        Optional<String> minimum = bound(node, "min", kind);
        Optional<String> maximum = bound(node, "max", kind);
        if (minimum.isPresent()
                && maximum.isPresent()
                && new BigDecimal(minimum.get()).compareTo(new BigDecimal(maximum.get())) > 0) {
            throw node.failure("max", maximum.get() + " is below min " + minimum.get());
        }
        String defaultValue = node.string("default");
        checkDefault(node, kind, serialization, defaultValue, minimum, maximum, choices);
        String shortHelp = node.text("shortHelp");
        String helpUrl = node.url("helpUrl");
        VersionRange range = versionRange(node, curated);
        List<ValidatorId> validators = validators(node, kind);
        List<String> aliases = node.distinctTexts("aliases");
        List<String> related = node.distinctTexts("related");
        return new ParameterDefinition(
                name,
                displayName,
                category,
                kind,
                visibility,
                defaultValue,
                minimum,
                maximum,
                choices,
                shortHelp,
                helpUrl,
                range,
                serialization,
                validators,
                aliases,
                related);
    }

    private static List<Choice> choices(Node node, ValueKind kind) {
        List<JsonValue> array = node.array("choices");
        if (kind.isEnumeration() && array.size() < 2) {
            throw node.failure("choices", "an enumerated kind needs at least two labelled choices");
        }
        if (!kind.isEnumeration() && !array.isEmpty()) {
            throw node.failure(
                    "choices", "kind " + kind + " is not enumerated, so it has no choices");
        }
        List<Choice> choices = new ArrayList<>();
        Set<String> values = new HashSet<>();
        for (int index = 0; index < array.size(); index++) {
            Node choice = Node.of(array.get(index), node.where(), "choices[" + index + "]");
            choice.onlyFields(CHOICE_FIELDS);
            String value = choice.text("value");
            String label = choice.text("label");
            if (kind == ValueKind.INTEGER_ENUM && !WHOLE.matcher(value).matches()) {
                throw node.failure(
                        "choices", "value \"" + value + "\" of an integer enum is not whole");
            }
            if (!values.add(value)) {
                throw node.failure("choices", "value \"" + value + "\" is listed twice");
            }
            choices.add(new Choice(value, label));
        }
        return choices;
    }

    private static Optional<String> bound(Node node, String field, ValueKind kind) {
        Optional<String> text = node.optionalString(field);
        if (text.isPresent()) {
            if (!kind.isBoundedNumeric()) {
                throw node.failure(
                        field, "is given, but kind " + kind + " is not a bounded number");
            }
            requireNumber(node, field, text.get(), kind.isWholeNumbers());
        }
        return text;
    }

    private static void checkDefault(
            Node node,
            ValueKind kind,
            SerializationRule serialization,
            String value,
            Optional<String> minimum,
            Optional<String> maximum,
            List<Choice> choices) {
        if (!value.equals(value.strip())) {
            throw node.failure("default", "has surrounding white space, which Comet never writes");
        }
        if (value.isEmpty()) {
            if (!serialization.allowsEmpty()) {
                throw node.failure(
                        "default", "is empty, and serialization " + serialization + " is not");
            }
            return;
        }
        switch (kind) {
            case INTEGER_ENUM, STRING_ENUM -> {
                if (choices.stream().noneMatch(choice -> choice.value().equals(value))) {
                    throw node.failure("default", "\"" + value + "\" is not one of its choices");
                }
            }
            case BOOLEAN_FLAG, ION_SERIES_FLAG -> {
                if (!"0".equals(value) && !"1".equals(value)) {
                    throw node.failure("default", "\"" + value + "\" is not 0 or 1");
                }
            }
            case ENZYME_REFERENCE -> {
                if (!NON_NEGATIVE_WHOLE.matcher(value).matches()) {
                    throw node.failure("default", "\"" + value + "\" is not an enzyme number");
                }
            }
            case INTEGER,
                    DECIMAL,
                    TOLERANCE_PAIR_MEMBER,
                    INTEGER_RANGE,
                    DECIMAL_RANGE,
                    DECIMAL_LIST ->
                    checkNumbers(node, kind, serialization, value, minimum, maximum);
            default -> {
                // STRING, FILE_PATH and VARIABLE_MOD_TUPLE: free text here; the tuple's layout is
                // the structured value types' to check.
            }
        }
    }

    private static void checkNumbers(
            Node node,
            ValueKind kind,
            SerializationRule serialization,
            String value,
            Optional<String> minimum,
            Optional<String> maximum) {
        String[] tokens = value.split("\\s+");
        int expected =
                serialization == SerializationRule.TWO_VALUES
                        ? 2
                        : serialization == SerializationRule.SINGLE_VALUE ? 1 : tokens.length;
        if (tokens.length != expected) {
            throw node.failure(
                    "default",
                    "\"" + value + "\" holds " + tokens.length + " values, not " + expected);
        }
        for (String token : tokens) {
            BigDecimal number = requireNumber(node, "default", token, kind.isWholeNumbers());
            if (minimum.isPresent() && number.compareTo(new BigDecimal(minimum.get())) < 0) {
                throw node.failure("default", token + " is below its own min " + minimum.get());
            }
            if (maximum.isPresent() && number.compareTo(new BigDecimal(maximum.get())) > 0) {
                throw node.failure("default", token + " is above its own max " + maximum.get());
            }
        }
    }

    private static BigDecimal requireNumber(Node node, String field, String text, boolean whole) {
        if (whole && !WHOLE.matcher(text).matches()) {
            throw node.failure(field, "\"" + text + "\" is not a whole number");
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException notANumber) {
            throw node.failure(field, "\"" + text + "\" is not a number");
        }
    }

    private static VersionRange versionRange(Node node, Set<ToolVersion> curated) {
        Node range = Node.of(node.required("versions"), node.where(), "versions");
        range.onlyFields(RANGE_FIELDS);
        ToolVersion from = range.version("from");
        if (!curated.contains(from)) {
            throw node.failure(
                    "versions",
                    "starts at Comet "
                            + from.text()
                            + ", which is not one of the curated versions");
        }
        Optional<ToolVersion> through =
                range.optionalString("through").map(text -> range.parseVersion("through", text));
        try {
            return new VersionRange(from, through);
        } catch (IllegalArgumentException backwards) {
            throw node.failure("versions", backwards.getMessage());
        }
    }

    private static List<ValidatorId> validators(Node node, ValueKind kind) {
        List<ValidatorId> validators = new ArrayList<>();
        for (String id : node.distinctTexts("validators")) {
            validators.add(
                    ValidatorId.fromId(id)
                            .orElseThrow(
                                    () ->
                                            node.failure(
                                                    "validators",
                                                    "\""
                                                            + id
                                                            + "\" is not one of "
                                                            + Arrays.stream(ValidatorId.values())
                                                                    .map(ValidatorId::id)
                                                                    .toList())));
        }
        if (kind == ValueKind.TOLERANCE_PAIR_MEMBER
                && validators.contains(ValidatorId.ORDERED_RANGE)) {
            throw node.failure(
                    "validators",
                    "a signed tolerance-pair member is validated by its own rule (R-PARAM-04),"
                            + " never by the generic ordered_range rule");
        }
        return validators;
    }

    private static List<InternalParameter> internal(
            Node top, List<ParameterDefinition> parameters) {
        List<JsonValue> array = top.array("internal");
        List<InternalParameter> entries = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int index = 0; index < array.size(); index++) {
            Node unnamed = Node.of(array.get(index), "internal[" + index + "]", "internal");
            unnamed.onlyFields(INTERNAL_FIELDS);
            String name = unnamed.text("name");
            Node node = unnamed.renamed("internal parameter \"" + name + "\"");
            String reason = node.text("reason");
            if (parameters.stream().anyMatch(p -> p.name().equals(name))) {
                throw node.failure("name", "is both modelled and allow-listed");
            }
            if (!seen.add(name)) {
                throw node.failure("name", "is allow-listed twice");
            }
            entries.add(new InternalParameter(name, reason));
        }
        return entries;
    }

    private static EnzymeTableMetadata enzymeTable(Node top, List<ParameterDefinition> parameters) {
        Node node = Node.of(top.required("enzymeTable"), "enzymeTable", "enzymeTable");
        node.onlyFields(ENZYME_FIELDS);
        String header = node.text("header");
        if (!header.equals(ParamsLineReader.ENZYME_HEADER)) {
            throw node.failure(
                    "header", "is \"" + header + "\", not " + ParamsLineReader.ENZYME_HEADER);
        }
        List<Choice> sense = new ArrayList<>();
        List<JsonValue> array = node.array("senseChoices");
        if (array.isEmpty()) {
            throw node.failure("senseChoices", "is empty; the sense field's values need labels");
        }
        for (int index = 0; index < array.size(); index++) {
            Node choice = Node.of(array.get(index), "enzymeTable", "senseChoices[" + index + "]");
            choice.onlyFields(CHOICE_FIELDS);
            sense.add(new Choice(choice.text("value"), choice.text("label")));
        }
        List<String> referencedBy = node.distinctTexts("referencedBy");
        for (String name : referencedBy) {
            boolean reference =
                    parameters.stream()
                            .anyMatch(
                                    p ->
                                            p.name().equals(name)
                                                    && p.kind() == ValueKind.ENZYME_REFERENCE);
            if (!reference) {
                throw node.failure(
                        "referencedBy",
                        "names \"" + name + "\", which is not a modelled enzyme reference");
            }
        }
        for (ParameterDefinition definition : parameters) {
            if (definition.kind() == ValueKind.ENZYME_REFERENCE
                    && !referencedBy.contains(definition.name())) {
                throw node.failure(
                        "referencedBy",
                        "omits \"" + definition.name() + "\", which is an enzyme reference");
            }
        }
        return new EnzymeTableMetadata(
                header, node.url("helpUrl"), node.text("rowFormat"), sense, referencedBy);
    }

    /**
     * One JSON object being read, and where it is, so that every failure can say so.
     *
     * @param object the object, or {@code null} for a node used only to report
     * @param where where in the file it is
     */
    private record Node(JsonValue.JsonObject object, String where) {

        static Node of(JsonValue value, String where, String field) {
            if (!(value instanceof JsonValue.JsonObject jsonObject)) {
                throw new InvalidMetadataException(where, field, "must be a JSON object");
            }
            return new Node(jsonObject, where);
        }

        Node renamed(String newWhere) {
            return new Node(object, newWhere);
        }

        InvalidMetadataException failure(String field, String problem) {
            return new InvalidMetadataException(where, field, problem);
        }

        void onlyFields(List<String> allowed) {
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

        String url(String field) {
            String text = text(field);
            if (!text.startsWith(HTTPS)) {
                throw failure(field, "\"" + text + "\" is not an https:// reference");
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

        List<String> distinctTexts(String field) {
            List<String> texts = new ArrayList<>();
            for (JsonValue element : array(field)) {
                if (!(element instanceof JsonValue.JsonString text) || text.value().isBlank()) {
                    throw failure(field, "must hold non-blank strings only");
                }
                if (texts.contains(text.value())) {
                    throw failure(field, "holds \"" + text.value() + "\" twice");
                }
                texts.add(text.value());
            }
            return texts;
        }

        ToolVersion version(String field) {
            return parseVersion(field, text(field));
        }

        ToolVersion parseVersion(String field, String text) {
            try {
                return ToolVersion.parse(text);
            } catch (IllegalArgumentException notAVersion) {
                throw failure(field, notAVersion.getMessage());
            }
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
