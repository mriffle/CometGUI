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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.cometgui.domain.tools.ToolVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A version record's {@code ruleSeverities} (decision C-2 of the Comet 2026.03.0 intake): what the
 * loader reads and what it refuses, on CONSTRUCTED metadata ({@link ConstructedMetadata}), and the
 * {@link RuleSeverity} record itself. Which rules exist, and which are version-scoped, is the
 * validator's to check ({@code VersionScopedRulesTest}).
 */
class RuleSeveritiesLoaderTest {

    private static final String WHERE = "versions[0]";

    @SuppressWarnings("unchecked")
    private static Map<String, Object> entry(ConstructedMetadata doc, int index) {
        return (Map<String, Object>) severities(doc).get(index);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> severities(ConstructedMetadata doc) {
        return (List<Object>)
                ((Map<String, Object>) doc.list("versions").get(0)).get("ruleSeverities");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> record(ConstructedMetadata doc) {
        return (Map<String, Object>) doc.list("versions").get(0);
    }

    private static void rejected(
            ConstructedMetadata doc, String where, String field, String fragment) {
        InvalidMetadataException failure = assertThrows(InvalidMetadataException.class, doc::load);
        assertEquals(where, failure.where(), failure.getMessage());
        assertEquals(field, failure.field(), failure.getMessage());
        assertTrue(failure.getMessage().contains(fragment), failure.getMessage());
    }

    private static String named(String rule) {
        return WHERE + " severity of rule \"" + rule + "\"";
    }

    @Test
    @DisplayName("each entry loads into the record, keyed by its rule")
    void loads() {
        CometVersionRecord record =
                ConstructedMetadata.valid()
                        .load()
                        .version(ToolVersion.parse(ConstructedMetadata.VERSION))
                        .orElseThrow();
        assertEquals(
                List.of(
                        "index_search_type.ignored_without_idx",
                        "variable_mod_tuple.distance_undocumented"),
                List.copyOf(record.ruleSeverities().keySet()));
        RuleSeverity distance =
                record.ruleSeverity("variable_mod_tuple.distance_undocumented").orElseThrow();
        assertEquals(RuleSeverity.Level.WARNING, distance.level());
        assertEquals(
                "https://example.org/source/variable_mod_tuple.distance_undocumented",
                distance.source());
        assertEquals(
                RuleSeverity.Level.OFF,
                record.ruleSeverity("index_search_type.ignored_without_idx").orElseThrow().level());
        assertEquals(Optional.empty(), record.ruleSeverity("variable_mod_tuple.count_zero"));
        assertThrows(UnsupportedOperationException.class, () -> record.ruleSeverities().clear());
    }

    @Test
    @DisplayName("an empty list loads; the validator, not the loader, requires the rules")
    void emptyLoads() {
        ConstructedMetadata doc = ConstructedMetadata.valid();
        severities(doc).clear();
        assertEquals(
                Map.of(),
                doc.load()
                        .version(ToolVersion.parse(ConstructedMetadata.VERSION))
                        .orElseThrow()
                        .ruleSeverities());
    }

    @Test
    @DisplayName("the member is required, and must be an array of objects")
    void container() {
        ConstructedMetadata doc = ConstructedMetadata.valid();
        record(doc).remove("ruleSeverities");
        rejected(doc, WHERE, "ruleSeverities", "is missing");
        record(doc).put("ruleSeverities", "none");
        rejected(doc, WHERE, "ruleSeverities", "must be an array");
        record(doc).put("ruleSeverities", new ArrayList<>(List.of("distance")));
        rejected(doc, WHERE, "ruleSeverities[0]", "must be a JSON object");
    }

    @Test
    @DisplayName("an entry has exactly rule, severity and source")
    void fields() {
        ConstructedMetadata doc = ConstructedMetadata.valid();
        entry(doc, 0).put("since", "2026.03.0");
        rejected(doc, WHERE, "since", "is not a field this format has");
        entry(doc, 0).remove("since");
        entry(doc, 0).remove("source");
        rejected(doc, WHERE, "source", "is missing");
    }

    @Test
    @DisplayName("the rule is an identifier, stated once")
    void rule() {
        ConstructedMetadata doc = ConstructedMetadata.valid();
        entry(doc, 0).put("rule", "Distance");
        rejected(doc, named("Distance"), "rule", "is not a rule identifier");
        entry(doc, 0).put("rule", "variable_mod_tuple.");
        rejected(doc, named("variable_mod_tuple."), "rule", "is not a rule identifier");
        entry(doc, 0).put("rule", "  ");
        rejected(doc, WHERE, "rule", "is blank");
        entry(doc, 0).put("rule", "index_search_type.ignored_without_idx");
        rejected(doc, named("index_search_type.ignored_without_idx"), "rule", "is stated twice");
    }

    @Test
    @DisplayName("the severity is ERROR, WARNING or OFF; the source is https://")
    void severityAndSource() {
        ConstructedMetadata doc = ConstructedMetadata.valid();
        String rule = "variable_mod_tuple.distance_undocumented";
        entry(doc, 0).put("severity", "FATAL");
        rejected(doc, named(rule), "severity", "\"FATAL\" is not one of [ERROR, WARNING, OFF]");
        entry(doc, 0).put("severity", "warning");
        rejected(doc, named(rule), "severity", "is not one of");
        entry(doc, 0).put("severity", "ERROR");
        entry(doc, 0).put("source", "http://example.org/");
        rejected(doc, named(rule), "source", "is not an https:// reference");
    }

    @Test
    @DisplayName("the record itself refuses a malformed rule or source, and a misfiled entry")
    void theRecord() {
        assertEquals(
                "\"Distance\" is not a rule identifier such as family.what_it_checks",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        new RuleSeverity(
                                                "Distance",
                                                RuleSeverity.Level.ERROR,
                                                "https://example.org/"))
                        .getMessage());
        assertThrows(
                IllegalArgumentException.class,
                () -> new RuleSeverity("a.b", RuleSeverity.Level.ERROR, "ftp://example.org/"));
        assertThrows(
                NullPointerException.class,
                () -> new RuleSeverity("a.b", null, "https://example.org/"));
        RuleSeverity ok = new RuleSeverity("a.b", RuleSeverity.Level.OFF, "https://example.org/");
        CometVersionRecord record =
                ConstructedMetadata.valid()
                        .load()
                        .version(ToolVersion.parse(ConstructedMetadata.VERSION))
                        .orElseThrow();
        Map<String, RuleSeverity> misfiled = new HashMap<>();
        misfiled.put("c.d", ok);
        assertEquals(
                "the severity filed under c.d is for a.b",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        new CometVersionRecord(
                                                record.version(),
                                                record.marker(),
                                                record.parameterPages(),
                                                record.source(),
                                                record.variableModTuple(),
                                                record.overrides(),
                                                misfiled))
                        .getMessage());
        CometVersionRecord changed = record.withRuleSeverities(Map.of("a.b", ok));
        assertEquals(Map.of("a.b", ok), changed.ruleSeverities());
        assertEquals(record.overrides(), changed.overrides());
        assertEquals(record.variableModTuple(), changed.variableModTuple());
        assertEquals(record.marker(), changed.marker());
    }
}
