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
import java.util.Optional;
import org.cometgui.domain.tools.ToolVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The loader's rules for a version record's {@code valueMigrations}, each proved on CONSTRUCTED
 * metadata (test input, not Comet's output): the constructed document's 2026.02.2 plus a newer
 * constructed record, {@code 2026.03.0}, whose {@code valueMigrations} carry the entries under
 * test, from 2026.02.2.
 */
class ValueMigrationsLoaderTest {

    private static final ToolVersion OLDER = ToolVersion.parse(ConstructedMetadata.VERSION);

    private static final ToolVersion NEWER = ToolVersion.parse("2026.03.0");

    private static final String WHERE = "versions[1] value migration 0";

    @SafeVarargs
    private static ConstructedMetadata withNewerVersion(Map<String, Object>... migrations) {
        ConstructedMetadata doc = ConstructedMetadata.valid();
        Map<String, Object> newer = new LinkedHashMap<>();
        newer.put("version", "2026.03.0");
        newer.put("marker", "2026.03 rev. 0 (fa08489)");
        newer.put("parameterPages", "https://example.org/newer/");
        newer.put("source", "https://example.org/newer-source/");
        newer.put("variableModTuple", ConstructedMetadata.tupleLayout());
        newer.put("overrides", new ArrayList<>());
        newer.put("ruleSeverities", ConstructedMetadata.ruleSeverities());
        newer.put("valueMigrations", new ArrayList<>(List.of(migrations)));
        newer.put("indexFormats", ConstructedMetadata.indexFormats(5));
        doc.list("versions").add(newer);
        return doc;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> record(ConstructedMetadata doc, int index) {
        return (Map<String, Object>) doc.list("versions").get(index);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> entry(ConstructedMetadata doc, int index) {
        return (Map<String, Object>)
                ((List<Object>) record(doc, 1).get("valueMigrations")).get(index);
    }

    /** An entry from 2026.02.2, with a reason and a source, and the given fields. */
    private static Map<String, Object> migration(String action, Object... fieldThenValue) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("from", ConstructedMetadata.VERSION);
        entry.put("action", action);
        entry.put("reason", "Constructed reason.");
        entry.put("source", "https://example.org/newer-source/Comet.cpp#L1");
        for (int index = 0; index < fieldThenValue.length; index += 2) {
            entry.put((String) fieldThenValue[index], fieldThenValue[index + 1]);
        }
        return entry;
    }

    private static Map<String, Object> convertIsotope() {
        return migration("CONVERT", "parameter", "isotope_error", "value", "2", "becomes", "1");
    }

    private static Map<String, Object> convertDistance() {
        return migration(
                "CONVERT",
                "rule",
                "variable_mod_tuple.distance_undocumented",
                "field",
                "TERMINAL_DISTANCE",
                "becomes",
                "-1");
    }

    private static void rejected(
            ConstructedMetadata doc, String where, String field, String fragment) {
        InvalidMetadataException failure = assertThrows(InvalidMetadataException.class, doc::load);
        assertEquals(where, failure.where(), failure.getMessage());
        assertEquals(field, failure.field(), failure.getMessage());
        assertTrue(failure.getMessage().contains(fragment), failure.getMessage());
    }

    @Nested
    @DisplayName("entries that load")
    class Loads {

        @Test
        @DisplayName("each shape loads as written, on its record, keyed by its source release")
        void everyShape() {
            CuratedMetadata metadata =
                    withNewerVersion(
                                    convertIsotope(),
                                    migration("NOTICE", "parameter", "isotope_error", "value", "0"),
                                    convertDistance(),
                                    migration(
                                            "NEEDS_ATTENTION",
                                            "rule",
                                            "variable_mod_tuple.terminus_undocumented"))
                            .load();
            CometVersionRecord newer = metadata.version(NEWER).orElseThrow();
            List<ValueMigration> all = newer.valueMigrations();
            assertEquals(4, all.size());
            assertEquals(all, newer.valueMigrationsFrom(OLDER));
            assertEquals(List.of(), newer.valueMigrationsFrom(NEWER));
            assertEquals(List.of(), metadata.version(OLDER).orElseThrow().valueMigrations());

            ValueMigration convert = all.get(0);
            assertEquals(OLDER, convert.from());
            assertEquals(Optional.of("isotope_error"), convert.parameter());
            assertEquals(Optional.of("2"), convert.value());
            assertEquals(Optional.empty(), convert.rule());
            assertEquals(ValueMigration.Action.CONVERT, convert.action());
            assertEquals(Optional.empty(), convert.field());
            assertEquals(Optional.of("1"), convert.becomes());
            assertEquals("Constructed reason.", convert.reason());
            assertEquals("https://example.org/newer-source/Comet.cpp#L1", convert.source());
            assertTrue(convert.matchesValue("isotope_error", "2"));
            assertEquals("isotope_error = 2", convert.matches());

            assertEquals(ValueMigration.Action.NOTICE, all.get(1).action());
            assertEquals(Optional.empty(), all.get(1).becomes());

            ValueMigration distance = all.get(2);
            assertEquals(Optional.of("variable_mod_tuple.distance_undocumented"), distance.rule());
            assertEquals(Optional.empty(), distance.parameter());
            assertEquals(Optional.empty(), distance.value());
            assertEquals(Optional.of(VariableModField.TERMINAL_DISTANCE), distance.field());
            assertEquals(Optional.of("-1"), distance.becomes());
            assertEquals("rule variable_mod_tuple.distance_undocumented", distance.matches());

            ValueMigration attention = all.get(3);
            assertEquals(ValueMigration.Action.NEEDS_ATTENTION, attention.action());
            assertEquals(Optional.empty(), attention.field());
            assertEquals(Optional.empty(), attention.becomes());
        }

        @Test
        @DisplayName("a choice that only an override gives a release counts as that release's")
        @SuppressWarnings("unchecked")
        void choicesAreTheReleasesOwn() {
            ConstructedMetadata doc = withNewerVersion();
            Map<String, Object> widened = new LinkedHashMap<>();
            widened.put("name", "isotope_error");
            widened.put("source", "https://example.org/newer-source/Comet.cpp#L2");
            widened.put(
                    "choices",
                    List.of(
                            ConstructedMetadata.choice("-1", "Not set"),
                            ConstructedMetadata.choice("0", "Off"),
                            ConstructedMetadata.choice("1", "0, +1"),
                            ConstructedMetadata.choice("2", "0, +1, +2")));
            ((List<Object>) record(doc, 1).get("overrides")).add(widened);
            Map<String, Object> back =
                    migration(
                            "CONVERT", "parameter", "isotope_error", "value", "-1", "becomes", "2");
            back.put("from", "2026.03.0");
            ((List<Object>) record(doc, 0).get("valueMigrations")).add(back);
            ValueMigration loaded =
                    doc.load().version(OLDER).orElseThrow().valueMigrationsFrom(NEWER).get(0);
            assertEquals(Optional.of("-1"), loaded.value());

            ((List<Object>) record(doc, 1).get("valueMigrations"))
                    .add(
                            migration(
                                    "CONVERT",
                                    "parameter",
                                    "isotope_error",
                                    "value",
                                    "2",
                                    "becomes",
                                    "-1"));
            assertEquals(
                    Optional.of("-1"),
                    doc.load().version(NEWER).orElseThrow().valueMigrations().get(0).becomes());

            back.put("value", "-1");
            back.put("action", "NOTICE");
            back.remove("becomes");
            rejected(
                    doc,
                    "versions[0] value migration 0",
                    "value",
                    "\"-1\" is not one of Comet 2026.02.2's choices, so it cannot be carried"
                            + " unchanged");
        }

        @Test
        @DisplayName("the bundled metadata's entries: six into 2026.03.0, one into 2026.02.2")
        void bundled() {
            CuratedMetadata metadata = MetadataLoader.loadBundled();
            CometVersionRecord newest = metadata.version(NEWER).orElseThrow();
            assertEquals(6, newest.valueMigrations().size());
            assertEquals(4, newest.valueMigrationsFrom(OLDER).size());
            assertEquals(2, newest.valueMigrationsFrom(ToolVersion.parse("2024.01.0")).size());
            assertEquals(
                    List.of(
                            "index_search_type = 1",
                            "index_search_type = 0",
                            "rule variable_mod_tuple.distance_undocumented",
                            "rule variable_mod_tuple.terminus_undocumented"),
                    newest.valueMigrationsFrom(OLDER).stream()
                            .map(ValueMigration::matches)
                            .toList());
            List<ValueMigration> back = metadata.version(OLDER).orElseThrow().valueMigrations();
            assertEquals(1, back.size());
            assertEquals("index_search_type = -1", back.get(0).matches());
            assertEquals(Optional.of("1"), back.get(0).becomes());
            assertEquals(
                    List.of(),
                    metadata.version(ToolVersion.parse("2024.01.0"))
                            .orElseThrow()
                            .valueMigrations());
        }
    }

    @Nested
    @DisplayName("what the loader refuses, naming the record, the entry and the field")
    class Refuses {

        @Test
        @DisplayName("the member is required and is an array of objects with known fields")
        void member() {
            ConstructedMetadata doc = withNewerVersion();
            record(doc, 1).remove("valueMigrations");
            rejected(doc, "versions[1]", "valueMigrations", "is missing");
            record(doc, 1).put("valueMigrations", "none");
            rejected(doc, "versions[1]", "valueMigrations", "must be an array");
            record(doc, 1).put("valueMigrations", new ArrayList<>(List.of("x")));
            rejected(doc, "versions[1]", "valueMigrations[0]", "must be a JSON object");

            doc = withNewerVersion(convertIsotope());
            entry(doc, 0).put("since", "2026.03.0");
            rejected(doc, "versions[1]", "since", "is not a field this format has");
            entry(doc, 0).remove("since");
            entry(doc, 0).remove("reason");
            rejected(doc, "versions[1]", "reason", "is missing");
        }

        @Test
        @DisplayName("the source release is another curated release")
        void from() {
            ConstructedMetadata doc = withNewerVersion(convertIsotope());
            entry(doc, 0).put("from", "2020.01.0");
            rejected(
                    doc, WHERE, "from", "is Comet 2020.01.0, which the metadata does not describe");
            entry(doc, 0).put("from", "2026.03.0");
            rejected(doc, WHERE, "from", "is this record's own version");
            entry(doc, 0).put("from", "latest");
            rejected(doc, WHERE, "from", "latest");
        }

        @Test
        @DisplayName("the action is a constant; the reason is text; the source is https://")
        void actionReasonSource() {
            ConstructedMetadata doc = withNewerVersion(convertIsotope());
            entry(doc, 0).put("action", "convert");
            rejected(doc, WHERE, "action", "is not one of [CONVERT, NEEDS_ATTENTION, NOTICE]");
            entry(doc, 0).put("action", "CONVERT");
            entry(doc, 0).put("reason", " ");
            rejected(doc, WHERE, "reason", "is blank");
            entry(doc, 0).put("reason", "Why.");
            entry(doc, 0).put("source", "http://example.org/");
            rejected(doc, WHERE, "source", "is not an https:// reference");
        }

        @Test
        @DisplayName("an entry is matched by value or by rule, exactly one")
        void matchedOneWay() {
            ConstructedMetadata doc = withNewerVersion(convertIsotope());
            entry(doc, 0).put("rule", "variable_mod_tuple.distance_undocumented");
            rejected(doc, WHERE, "parameter", "exactly one");
            entry(doc, 0).remove("rule");
            entry(doc, 0).remove("parameter");
            rejected(doc, WHERE, "parameter", "exactly one");
        }

        @Test
        @DisplayName("a target text exactly for CONVERT")
        void becomes() {
            ConstructedMetadata doc = withNewerVersion(convertIsotope());
            entry(doc, 0).remove("becomes");
            rejected(doc, WHERE, "becomes", "is missing; a CONVERT entry names the target text");
            entry(doc, 0).put("becomes", "1");
            entry(doc, 0).put("action", "NOTICE");
            rejected(doc, WHERE, "becomes", "is given, and only a CONVERT entry has a target text");
            entry(doc, 0).put("action", "NEEDS_ATTENTION");
            rejected(doc, WHERE, "becomes", "is given, and only a CONVERT entry has a target text");
        }

        @Test
        @DisplayName("matched by value: a choice of an enumerated parameter both releases model")
        void byValue() {
            ConstructedMetadata doc = withNewerVersion(convertIsotope());
            entry(doc, 0).put("field", "TERMINAL_DISTANCE");
            rejected(doc, WHERE, "field", "only an entry matched by rule names a tuple field");
            entry(doc, 0).remove("field");
            entry(doc, 0).put("parameter", "no_such_knob");
            rejected(doc, WHERE, "parameter", "is not a modelled parameter");
            entry(doc, 0).put("parameter", "allowed_missed_cleavage");
            rejected(
                    doc,
                    WHERE,
                    "parameter",
                    "allowed_missed_cleavage is of kind INTEGER; an entry matched by value names a"
                            + " choice of an enumerated parameter");
            entry(doc, 0).put("parameter", "isotope_error");
            entry(doc, 0).put("value", "7");
            rejected(
                    doc,
                    WHERE,
                    "value",
                    "\"7\" is not one of Comet 2026.02.2's choices for isotope_error");
            entry(doc, 0).put("value", "2");
            entry(doc, 0).put("becomes", "7");
            rejected(
                    doc,
                    WHERE,
                    "becomes",
                    "\"7\" is not one of Comet 2026.03.0's choices for isotope_error");
            entry(doc, 0).put("becomes", "2");
            rejected(doc, WHERE, "becomes", "repeats the value; a conversion writes another one");
            entry(doc, 0).remove("value");
            rejected(doc, WHERE, "value", "is missing");
        }

        @Test
        @DisplayName("matched by value: the parameter is modelled for both releases")
        @SuppressWarnings("unchecked")
        void modelledForBoth() {
            ConstructedMetadata doc = withNewerVersion(convertIsotope());
            ((Map<String, Object>) doc.parameter("isotope_error").get("versions"))
                    .put("through", ConstructedMetadata.VERSION);
            rejected(doc, WHERE, "parameter", "isotope_error is not modelled for Comet 2026.03.0");
            ((Map<String, Object>) doc.parameter("isotope_error").get("versions"))
                    .put("from", "2026.03.0");
            ((Map<String, Object>) doc.parameter("isotope_error").get("versions"))
                    .put("through", null);
            rejected(doc, WHERE, "parameter", "isotope_error is not modelled for Comet 2026.02.2");
        }

        @Test
        @DisplayName("matched by rule: an identifier, and a field and its text only to convert")
        void byRule() {
            ConstructedMetadata doc = withNewerVersion(convertDistance());
            entry(doc, 0).put("value", "-3");
            rejected(doc, WHERE, "value", "only an entry matched by a parameter names a value");
            entry(doc, 0).remove("value");
            entry(doc, 0).put("rule", "Distance");
            rejected(doc, WHERE, "rule", "is not a rule identifier such as family.what_it_checks");
            entry(doc, 0).put("rule", "variable_mod_tuple.distance_undocumented");
            entry(doc, 0).remove("field");
            rejected(doc, WHERE, "field", "is missing");
            entry(doc, 0).put("field", "DISTANCE");
            rejected(doc, WHERE, "field", "is not one of");
            entry(doc, 0).put("field", "TERMINAL_DISTANCE");
            entry(doc, 0).put("becomes", "-1.5");
            rejected(doc, WHERE, "becomes", "\"-1.5\" is not a whole number");
            entry(doc, 0).put("field", "MASS");
            entry(doc, 0).put("becomes", "heavy");
            rejected(doc, WHERE, "becomes", "\"heavy\" is not a number");
            entry(doc, 0).put("becomes", "15.9949");
            assertEquals(
                    Optional.of(VariableModField.MASS),
                    doc.load().version(NEWER).orElseThrow().valueMigrations().get(0).field());
            entry(doc, 0).put("field", "RESIDUES");
            entry(doc, 0).put("becomes", "S T");
            rejected(doc, WHERE, "becomes", "\"S T\" is not one residue token");
            entry(doc, 0).put("becomes", " ");
            rejected(doc, WHERE, "becomes", "is not one residue token");
            entry(doc, 0).put("becomes", "STY");
            assertEquals(
                    Optional.of("STY"),
                    doc.load().version(NEWER).orElseThrow().valueMigrations().get(0).becomes());
            entry(doc, 0).put("action", "NEEDS_ATTENTION");
            entry(doc, 0).remove("becomes");
            rejected(doc, WHERE, "field", "only an entry that converts rewrites a tuple field");
        }

        @Test
        @DisplayName("matched by rule: the field is in both releases' tuple layouts")
        @SuppressWarnings("unchecked")
        void fieldInBothLayouts() {
            ConstructedMetadata doc = withNewerVersion(convertDistance());
            entry(doc, 0).put("field", "NEUTRAL_LOSS");
            entry(doc, 0).put("becomes", "0.0");
            Map<String, Object> seven = ConstructedMetadata.tupleLayout();
            ((List<Object>) seven.get("fields")).remove(7);
            record(doc, 1).put("variableModTuple", seven);
            rejected(
                    doc,
                    WHERE,
                    "field",
                    "Comet 2026.03.0's variable-modification tuple has no neutral loss field");
            record(doc, 1).put("variableModTuple", ConstructedMetadata.tupleLayout());
            record(doc, 0).put("variableModTuple", seven);
            rejected(
                    doc,
                    WHERE,
                    "field",
                    "Comet 2026.02.2's variable-modification tuple has no neutral loss field");
        }

        @Test
        @DisplayName("a value or a rule is matched once per source release")
        void once() {
            rejected(
                    withNewerVersion(convertIsotope(), convertIsotope()),
                    "versions[1] value migration 1",
                    "value",
                    "isotope_error = 2 is matched a second time for Comet 2026.02.2");
            rejected(
                    withNewerVersion(convertDistance(), convertDistance()),
                    "versions[1] value migration 1",
                    "rule",
                    "rule variable_mod_tuple.distance_undocumented is matched a second time");
            assertEquals(
                    2,
                    withNewerVersion(
                                    convertIsotope(),
                                    migration(
                                            "CONVERT",
                                            "parameter",
                                            "isotope_error",
                                            "value",
                                            "1",
                                            "becomes",
                                            "2"))
                            .load()
                            .version(NEWER)
                            .orElseThrow()
                            .valueMigrations()
                            .size());
        }
    }

    @Nested
    @DisplayName("the record itself")
    class TheRecord {

        private ValueMigration make(
                Optional<String> parameter,
                Optional<String> value,
                Optional<String> rule,
                ValueMigration.Action action,
                Optional<VariableModField> field,
                Optional<String> becomes,
                String reason,
                String source) {
            return new ValueMigration(
                    OLDER, parameter, value, rule, action, field, becomes, reason, source);
        }

        private String refused(
                Optional<String> parameter,
                Optional<String> value,
                Optional<String> rule,
                ValueMigration.Action action,
                Optional<VariableModField> field,
                Optional<String> becomes,
                String reason,
                String source) {
            return assertThrows(
                            IllegalArgumentException.class,
                            () ->
                                    make(
                                            parameter, value, rule, action, field, becomes, reason,
                                            source))
                    .getMessage();
        }

        @Test
        @DisplayName("refuses every malformed combination, naming what is wrong")
        void refuses() {
            Optional<String> none = Optional.empty();
            Optional<String> isotope = Optional.of("isotope_error");
            Optional<String> two = Optional.of("2");
            Optional<String> one = Optional.of("1");
            Optional<String> rule = Optional.of("variable_mod_tuple.distance_undocumented");
            Optional<VariableModField> distance = Optional.of(VariableModField.TERMINAL_DISTANCE);
            Optional<VariableModField> noField = Optional.empty();
            ValueMigration.Action convert = ValueMigration.Action.CONVERT;
            ValueMigration.Action notice = ValueMigration.Action.NOTICE;
            String url = "https://example.org/";
            assertEquals(
                    "a value migration is matched by a value or by a rule, exactly one",
                    refused(isotope, two, rule, convert, noField, one, "Why.", url));
            assertEquals(
                    "a value migration is matched by a value or by a rule, exactly one",
                    refused(none, none, none, notice, noField, none, "Why.", url));
            assertEquals(
                    "a value migration matched by value names the parameter and the value",
                    refused(isotope, none, none, notice, noField, none, "Why.", url));
            assertEquals(
                    "a value migration matched by value names the parameter and the value",
                    refused(none, two, rule, notice, noField, none, "Why.", url));
            assertEquals(
                    "a value migration has a target text exactly when it converts; this one"
                            + " NOTICE",
                    refused(isotope, two, none, notice, noField, one, "Why.", url));
            assertTrue(
                    refused(isotope, two, none, convert, noField, none, "Why.", url)
                            .endsWith("this one CONVERT"));
            assertEquals(
                    "a value migration names a field exactly when it is matched by rule and"
                            + " converts",
                    refused(isotope, two, none, convert, distance, one, "Why.", url));
            assertEquals(
                    "a value migration names a field exactly when it is matched by rule and"
                            + " converts",
                    refused(none, none, rule, convert, noField, one, "Why.", url));
            assertEquals(
                    "a value migration names a field exactly when it is matched by rule and"
                            + " converts",
                    refused(none, none, rule, notice, distance, none, "Why.", url));
            assertEquals(
                    "\"Distance\" is not a rule identifier such as family.what",
                    refused(
                            none,
                            none,
                            Optional.of("Distance"),
                            notice,
                            noField,
                            none,
                            "Why.",
                            url));
            assertEquals(
                    "a value migration needs a reason",
                    refused(isotope, two, none, notice, noField, none, " ", url));
            assertEquals(
                    "the source of a value migration is not an https:// reference: ftp://x",
                    refused(isotope, two, none, notice, noField, none, "Why.", "ftp://x"));
            ValueMigration ok = make(none, none, rule, convert, distance, one, "Why.", url);
            assertEquals(false, ok.matchesValue("variable_mod01", "1"));
            ValueMigration byValue = make(isotope, two, none, notice, noField, none, "Why.", url);
            assertEquals(true, byValue.matchesValue("isotope_error", "2"));
            assertEquals(false, byValue.matchesValue("isotope_error", "1"));
            assertEquals(false, byValue.matchesValue("activation_method", "2"));
        }

        @Test
        @DisplayName("a version record refuses a value migration from its own release")
        void fromItself() {
            CometVersionRecord record =
                    ConstructedMetadata.valid().load().version(OLDER).orElseThrow();
            ValueMigration own =
                    new ValueMigration(
                            OLDER,
                            Optional.of("isotope_error"),
                            Optional.of("2"),
                            Optional.empty(),
                            ValueMigration.Action.NOTICE,
                            Optional.empty(),
                            Optional.empty(),
                            "Why.",
                            "https://example.org/");
            assertEquals(
                    "Comet 2026.02.2 has a value migration from itself: isotope_error = 2",
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
                                                    record.ruleSeverities(),
                                                    List.of(own)))
                            .getMessage());
            ValueMigration other =
                    new ValueMigration(
                            NEWER,
                            Optional.of("isotope_error"),
                            Optional.of("2"),
                            Optional.empty(),
                            ValueMigration.Action.NOTICE,
                            Optional.empty(),
                            Optional.empty(),
                            "Why.",
                            "https://example.org/");
            CometVersionRecord with = record.withValueMigrations(List.of(other));
            assertEquals(List.of(other), with.valueMigrations());
            assertEquals(
                    List.of(other),
                    with.withRuleSeverities(record.ruleSeverities()).valueMigrations());
            assertThrows(UnsupportedOperationException.class, () -> with.valueMigrations().clear());
        }
    }
}
