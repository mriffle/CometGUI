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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small, CONSTRUCTED metadata document for exercising one loader or drift rule at a time. It is
 * not Comet's output and not the bundled metadata: it is a valid document in the bundled file's
 * format, built as mutable maps so that a test can break exactly one field and serialise it.
 *
 * <p>Nine parameters, one of each shape the loader treats differently, plus one allow-listed name.
 */
final class ConstructedMetadata {

    static final String VERSION = "2026.02.2";

    private final Map<String, Object> root = new LinkedHashMap<>();

    private ConstructedMetadata() {}

    static ConstructedMetadata valid() {
        ConstructedMetadata doc = new ConstructedMetadata();
        Map<String, Object> version = new LinkedHashMap<>();
        version.put("version", VERSION);
        version.put("marker", "2026.02 rev. 2 (6edec91)");
        version.put("parameterPages", "https://example.org/pages/");
        version.put("source", "https://example.org/source/");
        version.put("variableModTuple", tupleLayout());
        version.put("overrides", new ArrayList<>());
        version.put("ruleSeverities", ruleSeverities());
        List<Object> categories = new ArrayList<>();
        for (ParameterCategory category : ParameterCategory.values()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", category.id());
            entry.put("displayName", category.displayName());
            categories.add(entry);
        }
        List<Object> parameters = new ArrayList<>();
        parameters.add(
                parameter("search_enzyme_number", "DIGESTION_ENZYMES", "ENZYME_REFERENCE", "1")
                        .validators("enzyme_in_table")
                        .map());
        parameters.add(
                parameter("isotope_error", "PRECURSOR_MASS", "INTEGER_ENUM", "2")
                        .choices("0", "Off", "1", "0, +1", "2", "0, +1, +2")
                        .validators("choice")
                        .map());
        parameters.add(
                parameter("allowed_missed_cleavage", "DIGESTION_ENZYMES", "INTEGER", "2")
                        .bounds("0", "5")
                        .related("search_enzyme_number")
                        .aliases("missed cleavage")
                        .map());
        parameters.add(
                parameter(
                                "peptide_mass_tolerance_lower",
                                "PRECURSOR_MASS",
                                "TOLERANCE_PAIR_MEMBER",
                                "-20.0")
                        .validators("signed_tolerance_pair")
                        .map());
        parameters.add(
                parameter("peff_obo", "DATABASE_PEFF", "FILE_PATH", "")
                        .serialization("EMPTY_ALLOWED")
                        .map());
        parameters.add(
                parameter("digest_mass_range", "SEARCH_RANGES", "DECIMAL_RANGE", "600.0 5000.0")
                        .serialization("TWO_VALUES")
                        .bounds("0.0", null)
                        .validators("ordered_range")
                        .map());
        parameters.add(
                parameter("mass_offsets", "PRECURSOR_MASS", "DECIMAL_LIST", "")
                        .serialization("VALUE_LIST")
                        .bounds("0.0", null)
                        .map());
        parameters.add(parameter("use_B_ions", "FRAGMENT_SCORING", "ION_SERIES_FLAG", "1").map());
        parameters.add(
                parameter("activation_method", "SPECTRUM_FILTERS", "STRING_ENUM", "ALL")
                        .choices("ALL", "All methods", "HCD", "HCD")
                        .map());
        Map<String, Object> enzymeTable = new LinkedHashMap<>();
        enzymeTable.put("header", "[COMET_ENZYME_INFO]");
        enzymeTable.put("helpUrl", "https://example.org/enzymes");
        enzymeTable.put("rowFormat", "number. name sense cut no-cut");
        enzymeTable.put(
                "senseChoices", List.of(choice("0", "N-terminal"), choice("1", "C-terminal")));
        enzymeTable.put("referencedBy", new ArrayList<>(List.of("search_enzyme_number")));
        Map<String, Object> internal = new LinkedHashMap<>();
        internal.put("name", "secret_knob");
        internal.put("reason", "constructed for this test only");
        doc.root.put("schemaVersion", 1L);
        doc.root.put("description", "constructed test metadata");
        doc.root.put("versions", new ArrayList<>(List.of(version)));
        doc.root.put("categories", categories);
        doc.root.put("enzymeTable", enzymeTable);
        doc.root.put("internal", new ArrayList<>(List.of(internal)));
        doc.root.put("parameters", parameters);
        return doc;
    }

    /**
     * A CONSTRUCTED {@code ruleSeverities} array stating both version-scoped rules, for a version
     * record a test adds.
     *
     * @return the array, mutable
     */
    static List<Object> ruleSeverities() {
        return new ArrayList<>(
                List.of(
                        ruleSeverity("variable_mod_tuple.distance_undocumented", "WARNING"),
                        ruleSeverity("index_search_type.ignored_without_idx", "OFF")));
    }

    /**
     * One CONSTRUCTED entry of a version record's {@code ruleSeverities}.
     *
     * @param rule the rule identifier
     * @param severity the level's name
     * @return the entry, mutable
     */
    static Map<String, Object> ruleSeverity(String rule, String severity) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("rule", rule);
        entry.put("severity", severity);
        entry.put("source", "https://example.org/source/" + rule);
        return entry;
    }

    /**
     * A tuple layout of the 2026.02.2 shape, CONSTRUCTED here as test input: eight fields, pairs on
     * the count and the neutral loss.
     */
    static Map<String, Object> tupleLayout() {
        Map<String, Object> layout = new LinkedHashMap<>();
        layout.put("source", "https://example.org/source/Comet.cpp");
        List<Object> fields = new ArrayList<>();
        fields.add(tupleField("MASS", "DECIMAL", false));
        fields.add(tupleField("RESIDUES", "RESIDUES", false));
        fields.add(tupleField("BINARY_GROUP", "INTEGER", false));
        fields.add(tupleField("COUNT", "INTEGER", true));
        fields.add(tupleField("TERMINAL_DISTANCE", "INTEGER", false));
        fields.add(tupleField("TERMINUS", "INTEGER", false));
        fields.add(tupleField("REQUIRED", "INTEGER", false));
        fields.add(tupleField("NEUTRAL_LOSS", "DECIMAL", true));
        layout.put("fields", fields);
        Map<String, Object> alphabet = new LinkedHashMap<>();
        alphabet.put("characters", "ABCDEFGHIJKLMNOPQRSTUVWXYZnc");
        alphabet.put("source", "https://example.org/source/CometSearchManager.cpp");
        layout.put("residueAlphabet", alphabet);
        return layout;
    }

    static Map<String, Object> tupleField(String field, String kind, Object pair) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("field", field);
        entry.put("kind", kind);
        entry.put("pair", pair);
        return entry;
    }

    /** The document's top level, to be changed in place. */
    Map<String, Object> root() {
        return root;
    }

    /** The parameter object with this name, to be changed in place. */
    @SuppressWarnings("unchecked")
    Map<String, Object> parameter(String name) {
        for (Object entry : (List<Object>) root.get("parameters")) {
            Map<String, Object> map = (Map<String, Object>) entry;
            if (name.equals(map.get("name"))) {
                return map;
            }
        }
        throw new AssertionError("the constructed document has no parameter " + name);
    }

    /** A named section, to be changed in place. */
    @SuppressWarnings("unchecked")
    Map<String, Object> section(String name) {
        return (Map<String, Object>) root.get(name);
    }

    /** A named list, to be changed in place. */
    @SuppressWarnings("unchecked")
    List<Object> list(String name) {
        return (List<Object>) root.get(name);
    }

    String json() {
        StringBuilder out = new StringBuilder();
        write(root, out);
        return out.toString();
    }

    CuratedMetadata load() {
        return MetadataLoader.load(json());
    }

    static Map<String, Object> choice(String value, String label) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("value", value);
        map.put("label", label);
        return map;
    }

    private static Builder parameter(String name, String category, String kind, String value) {
        return new Builder(name, category, kind, value);
    }

    /** Fills one parameter object with valid values for every field. */
    static final class Builder {

        private final Map<String, Object> map = new LinkedHashMap<>();

        Map<String, Object> map() {
            return map;
        }

        Builder(String name, String category, String kind, String value) {
            map.put("name", name);
            map.put("displayName", "Display " + name);
            map.put("category", ParameterCategory.valueOf(category).id());
            map.put("kind", kind);
            map.put("visibility", "ADVANCED");
            map.put("default", value);
            map.put("min", null);
            map.put("max", null);
            map.put("choices", new ArrayList<>());
            map.put("shortHelp", "Constructed help for " + name + ".");
            map.put("inlineComment", null);
            map.put("helpUrl", "https://example.org/" + name);
            Map<String, Object> versions = new LinkedHashMap<>();
            versions.put("from", VERSION);
            versions.put("through", null);
            map.put("versions", versions);
            map.put("serialization", "SINGLE_VALUE");
            map.put("validators", new ArrayList<>());
            map.put("aliases", new ArrayList<>());
            map.put("related", new ArrayList<>());
        }

        Builder choices(String... valueLabelPairs) {
            List<Object> choices = new ArrayList<>();
            for (int index = 0; index < valueLabelPairs.length; index += 2) {
                choices.add(choice(valueLabelPairs[index], valueLabelPairs[index + 1]));
            }
            map.put("choices", choices);
            return this;
        }

        Builder bounds(String min, String max) {
            map.put("min", min);
            map.put("max", max);
            return this;
        }

        Builder serialization(String rule) {
            map.put("serialization", rule);
            return this;
        }

        Builder validators(String... ids) {
            map.put("validators", new ArrayList<>(List.of(ids)));
            return this;
        }

        Builder aliases(String... words) {
            map.put("aliases", new ArrayList<>(List.of(words)));
            return this;
        }

        Builder related(String... names) {
            map.put("related", new ArrayList<>(List.of(names)));
            return this;
        }
    }

    @SuppressWarnings("unchecked")
    private static void write(Object value, StringBuilder out) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String text) {
            out.append('"');
            for (char c : text.toCharArray()) {
                if (c == '\n') {
                    out.append("\\n");
                    continue;
                }
                if (c == '\r') {
                    out.append("\\r");
                    continue;
                }
                if (c == '"' || c == '\\') {
                    out.append('\\');
                }
                out.append(c);
            }
            out.append('"');
        } else if (value instanceof Long || value instanceof Integer || value instanceof Boolean) {
            out.append(value);
        } else if (value instanceof Map<?, ?> map) {
            out.append('{');
            boolean first = true;
            for (Map.Entry<String, Object> entry : ((Map<String, Object>) map).entrySet()) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                write(entry.getKey(), out);
                out.append(':');
                write(entry.getValue(), out);
            }
            out.append('}');
        } else if (value instanceof List<?> list) {
            out.append('[');
            for (int index = 0; index < list.size(); index++) {
                if (index > 0) {
                    out.append(',');
                }
                write(list.get(index), out);
            }
            out.append(']');
        } else {
            throw new AssertionError("cannot write " + value.getClass());
        }
    }
}
