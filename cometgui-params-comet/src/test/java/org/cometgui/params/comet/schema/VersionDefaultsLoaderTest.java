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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.cometgui.domain.tools.ToolVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The loader's rules for a version record's default overrides, each proved on CONSTRUCTED metadata
 * (test input, not Comet's output): a second, older version record whose {@code defaults} carry one
 * change at a time.
 */
class VersionDefaultsLoaderTest {

    private static final String WHERE = "versions[1] override for ";

    @SuppressWarnings("unchecked")
    private static ConstructedMetadata withOlderVersion(Map<String, Object>... overrides) {
        ConstructedMetadata doc = ConstructedMetadata.valid();
        Map<String, Object> older = new LinkedHashMap<>();
        older.put("version", "2024.01.0");
        older.put("marker", "2024.01 rev. 0 (f00df0c)");
        older.put("parameterPages", "https://example.org/older/");
        older.put("source", "https://example.org/older-source/");
        older.put("variableModTuple", ConstructedMetadata.tupleLayout());
        older.put("overrides", new ArrayList<>(List.of(overrides)));
        older.put("ruleSeverities", ConstructedMetadata.ruleSeverities());
        older.put("valueMigrations", new ArrayList<>());
        older.put("indexFormats", ConstructedMetadata.indexFormats(5));
        doc.list("versions").add(older);
        ((Map<String, Object>) doc.parameter("allowed_missed_cleavage").get("versions"))
                .put("from", "2024.01.0");
        return doc;
    }

    private static Map<String, Object> override(String name, String value) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("name", name);
        entry.put("default", value);
        entry.put("source", "https://example.org/older-source/Comet.cpp#L1");
        return entry;
    }

    private static void rejected(
            ConstructedMetadata doc, String where, String field, String fragment) {
        InvalidMetadataException failure = assertThrows(InvalidMetadataException.class, doc::load);
        assertEquals(where, failure.where(), failure.getMessage());
        assertEquals(field, failure.field(), failure.getMessage());
        assertTrue(failure.getMessage().contains(fragment), failure.getMessage());
    }

    @Test
    @DisplayName("an override is the version's default; the curated default holds elsewhere")
    void anOverrideLoads() {
        CuratedMetadata metadata =
                withOlderVersion(override("allowed_missed_cleavage", "3")).load();
        ToolVersion older = ToolVersion.parse("2024.01.0");
        ToolVersion newer = ToolVersion.parse("2026.02.2");
        assertEquals(
                Map.of("allowed_missed_cleavage", "3"),
                metadata.version(older).orElseThrow().defaults());
        assertEquals(
                "3",
                metadata.parameter("allowed_missed_cleavage", older).orElseThrow().defaultValue());
        assertEquals(
                "2",
                metadata.parameter("allowed_missed_cleavage", newer).orElseThrow().defaultValue());
        assertEquals(
                "2", metadata.parameter("allowed_missed_cleavage").orElseThrow().defaultValue());
        assertEquals(
                List.of("allowed_missed_cleavage"),
                metadata.parametersFor(older).stream().map(ParameterDefinition::name).toList());
        assertEquals("3", metadata.parametersFor(older).get(0).defaultValue());
    }

    @Test
    @DisplayName("an older version with no overrides loads with an empty map")
    void noOverrides() {
        CuratedMetadata metadata = withOlderVersion().load();
        assertEquals(
                Map.of(),
                metadata.version(ToolVersion.parse("2024.01.0")).orElseThrow().defaults());
    }

    @Test
    void anUnmodelledNameIsRejected() {
        rejected(
                withOlderVersion(override("no_such_knob", "1")),
                WHERE + "\"no_such_knob\"",
                "name",
                "is not a modelled parameter");
    }

    @Test
    void aNameTheVersionDoesNotHaveIsRejected() {
        rejected(
                withOlderVersion(override("isotope_error", "1")),
                WHERE + "\"isotope_error\"",
                "name",
                "is not modelled for Comet 2024.01.0");
    }

    @Test
    void aNameOverriddenTwiceIsRejected() {
        rejected(
                withOlderVersion(
                        override("allowed_missed_cleavage", "3"),
                        override("allowed_missed_cleavage", "4")),
                WHERE + "\"allowed_missed_cleavage\"",
                "name",
                "is overridden twice");
    }

    @Test
    void aValueAboveTheBoundIsRejected() {
        rejected(
                withOlderVersion(override("allowed_missed_cleavage", "6")),
                WHERE + "\"allowed_missed_cleavage\"",
                "default",
                "is above its own max 5");
    }

    @Test
    void aValueOfTheWrongShapeIsRejected() {
        rejected(
                withOlderVersion(override("allowed_missed_cleavage", "2.5")),
                WHERE + "\"allowed_missed_cleavage\"",
                "default",
                "is not a whole number");
    }

    @Test
    void theCuratedDefaultRepeatedIsRejected() {
        rejected(
                withOlderVersion(override("allowed_missed_cleavage", "2")),
                WHERE + "\"allowed_missed_cleavage\"",
                "default",
                "repeats the parameter's own curated default");
    }

    @Test
    void aSourceThatIsNotHttpsIsRejected() {
        Map<String, Object> entry = override("allowed_missed_cleavage", "3");
        entry.put("source", "Comet.cpp line 1");
        rejected(
                withOlderVersion(entry),
                WHERE + "\"allowed_missed_cleavage\"",
                "source",
                "is not an https:// reference");
    }

    @Test
    void aMissingFieldIsRejected() {
        Map<String, Object> entry = override("allowed_missed_cleavage", "3");
        entry.remove("source");
        rejected(withOlderVersion(entry), "versions[1]", "source", "is missing");
    }

    @Test
    void anUnknownFieldIsRejected() {
        Map<String, Object> entry = override("allowed_missed_cleavage", "3");
        entry.put("since", "2024.01.0");
        rejected(withOlderVersion(entry), "versions[1]", "since", "is not a field this format has");
    }

    @Test
    void defaultsThatAreNotAnArrayAreRejected() {
        ConstructedMetadata doc = withOlderVersion();
        ((Map<String, Object>) doc.list("versions").get(1)).put("overrides", "none");
        rejected(doc, "versions[1]", "overrides", "must be an array");
    }

    @Test
    void aMissingDefaultsFieldIsRejected() {
        ConstructedMetadata doc = ConstructedMetadata.valid();
        ((Map<String, Object>) doc.list("versions").get(0)).remove("overrides");
        rejected(doc, "versions[0]", "overrides", "is missing");
    }

    @Test
    @DisplayName("a tuple override is held to the version's layout, not the curated default's")
    void aTupleOverrideIsCountedAgainstItsVersionsLayout() {
        ConstructedMetadata doc = withOlderVersion(override("variable_mod01", "15.9949 M 0 3"));
        Map<String, Object> tuple =
                new ConstructedMetadata.Builder(
                                "variable_mod01",
                                "VARIABLE_MODS",
                                "VARIABLE_MOD_TUPLE",
                                "15.9949 M 0 3 -1 0 0 0.0")
                        .serialization("TUPLE")
                        .validators("variable_mod_tuple")
                        .map();
        ((Map<String, Object>) tuple.get("versions")).put("from", "2024.01.0");
        doc.list("parameters").add(tuple);
        rejected(
                doc,
                "parameter \"variable_mod01\"",
                "default",
                "\"15.9949 M 0 3\" holds 4 fields, and the tuple layout of Comet 2024.01.0 has 8");
    }

    @Test
    @DisplayName("the record's defaults are immutable")
    void theDefaultsAreImmutable() {
        CometVersionRecord record =
                withOlderVersion(override("allowed_missed_cleavage", "3"))
                        .load()
                        .version(ToolVersion.parse("2024.01.0"))
                        .orElseThrow();
        assertThrows(UnsupportedOperationException.class, () -> record.defaults().clear());
        assertEquals(java.util.Optional.of("3"), record.defaultOverride("allowed_missed_cleavage"));
        assertEquals(java.util.Optional.empty(), record.defaultOverride("isotope_error"));
    }
}
