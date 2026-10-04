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
 * The loader's rules for a version record's overrides beyond the default -- choices, inline
 * comment, short help and help reference -- each proved on CONSTRUCTED metadata (test input, not
 * Comet's output): the constructed document's 2026.02.2 plus a newer constructed record, {@code
 * 2026.03.0}, which every open-ended range claims and whose {@code overrides} carry one change at a
 * time.
 */
class VersionOverridesLoaderTest {

    private static final ToolVersion OLDER = ToolVersion.parse(ConstructedMetadata.VERSION);

    private static final ToolVersion NEWER = ToolVersion.parse("2026.03.0");

    private static final String WHERE = "versions[1] override for ";

    @SafeVarargs
    private static ConstructedMetadata withNewerVersion(Map<String, Object>... overrides) {
        ConstructedMetadata doc = ConstructedMetadata.valid();
        Map<String, Object> newer = new LinkedHashMap<>();
        newer.put("version", "2026.03.0");
        newer.put("marker", "2026.03 rev. 0 (fa08489)");
        newer.put("parameterPages", "https://example.org/newer/");
        newer.put("source", "https://example.org/newer-source/");
        newer.put("variableModTuple", ConstructedMetadata.tupleLayout());
        newer.put("overrides", new ArrayList<>(List.of(overrides)));
        doc.list("versions").add(newer);
        return doc;
    }

    private static Map<String, Object> override(String name, Object... fieldThenValue) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("name", name);
        entry.put("source", "https://example.org/newer-source/Comet.cpp#L1");
        for (int index = 0; index < fieldThenValue.length; index += 2) {
            entry.put((String) fieldThenValue[index], fieldThenValue[index + 1]);
        }
        return entry;
    }

    private static List<Object> choices(String... valueLabelPairs) {
        List<Object> choices = new ArrayList<>();
        for (int index = 0; index < valueLabelPairs.length; index += 2) {
            choices.add(
                    ConstructedMetadata.choice(valueLabelPairs[index], valueLabelPairs[index + 1]));
        }
        return choices;
    }

    /** isotope_error's constructed choices with one more value, -1. */
    private static List<Object> widened() {
        return choices("-1", "Not set", "0", "Off", "1", "0, +1", "2", "0, +1, +2");
    }

    private static void rejected(
            ConstructedMetadata doc, String where, String field, String fragment) {
        InvalidMetadataException failure = assertThrows(InvalidMetadataException.class, doc::load);
        assertEquals(where, failure.where(), failure.getMessage());
        assertEquals(field, failure.field(), failure.getMessage());
        assertTrue(failure.getMessage().contains(fragment), failure.getMessage());
    }

    @Nested
    @DisplayName("an override that loads")
    class Loads {

        @Test
        @DisplayName("all five fields apply to the version, and to that version only")
        void everyFieldApplies() {
            CuratedMetadata metadata =
                    withNewerVersion(
                                    override(
                                            "isotope_error",
                                            "default",
                                            "-1",
                                            "choices",
                                            widened(),
                                            "inlineComment",
                                            "-1=not set",
                                            "shortHelp",
                                            "Newer help.",
                                            "helpUrl",
                                            "https://example.org/newer/isotope_error.html"))
                            .load();
            ParameterDefinition curated = metadata.parameter("isotope_error").orElseThrow();
            ParameterDefinition newer = metadata.parameter("isotope_error", NEWER).orElseThrow();
            ParameterDefinition older = metadata.parameter("isotope_error", OLDER).orElseThrow();

            assertEquals("-1", newer.defaultValue());
            assertEquals(
                    List.of("-1", "0", "1", "2"),
                    newer.choices().stream().map(Choice::value).toList());
            assertEquals(Optional.of("-1=not set"), newer.inlineComment());
            assertEquals("Newer help.", newer.shortHelp());
            assertEquals("https://example.org/newer/isotope_error.html", newer.detailedHelpRef());

            assertEquals(curated, older, "the older version keeps the curated definition");
            assertEquals("2", older.defaultValue());
            assertEquals(
                    List.of("0", "1", "2"), older.choices().stream().map(Choice::value).toList());
            assertEquals(Optional.empty(), older.inlineComment());

            assertEquals(
                    newer,
                    metadata.parametersFor(NEWER).stream()
                            .filter(p -> p.name().equals("isotope_error"))
                            .findFirst()
                            .orElseThrow(),
                    "parametersFor and parameter(name, version) agree");
            ParameterOverride record =
                    metadata.version(NEWER).orElseThrow().override("isotope_error").orElseThrow();
            assertEquals(
                    List.of("default", "choices", "inlineComment", "shortHelp", "helpUrl"),
                    record.replacedFields());
            assertEquals(
                    Map.of("isotope_error", "-1"),
                    metadata.version(NEWER).orElseThrow().defaults());
        }

        @Test
        @DisplayName("everything an override does not name is the curated definition's")
        void unnamedFieldsAreInherited() {
            CuratedMetadata metadata =
                    withNewerVersion(override("isotope_error", "shortHelp", "Newer help.")).load();
            ParameterDefinition curated = metadata.parameter("isotope_error").orElseThrow();
            ParameterDefinition newer = metadata.parameter("isotope_error", NEWER).orElseThrow();
            assertEquals("Newer help.", newer.shortHelp());
            assertEquals(
                    new ParameterDefinition(
                            curated.name(),
                            curated.displayName(),
                            curated.category(),
                            curated.kind(),
                            curated.visibility(),
                            curated.defaultValue(),
                            curated.minimum(),
                            curated.maximum(),
                            curated.choices(),
                            "Newer help.",
                            curated.inlineComment(),
                            curated.detailedHelpRef(),
                            curated.supportedVersions(),
                            curated.serialization(),
                            curated.validators(),
                            curated.aliases(),
                            curated.related()),
                    newer);
            assertEquals(Map.of(), metadata.version(NEWER).orElseThrow().defaults());
        }

        @Test
        @DisplayName("an inline comment can be replaced by none, with JSON null")
        void anInlineCommentCanBeRemoved() {
            ConstructedMetadata doc =
                    withNewerVersion(override("isotope_error", "inlineComment", null));
            doc.parameter("isotope_error").put("inlineComment", "curated comment");
            CuratedMetadata metadata = doc.load();
            assertEquals(
                    Optional.of("curated comment"),
                    metadata.parameter("isotope_error", OLDER).orElseThrow().inlineComment());
            assertEquals(
                    Optional.empty(),
                    metadata.parameter("isotope_error", NEWER).orElseThrow().inlineComment());
        }

        @Test
        @DisplayName("new choices that keep the curated default need no default override")
        void choicesKeepingTheDefault() {
            CuratedMetadata metadata =
                    withNewerVersion(override("isotope_error", "choices", widened())).load();
            assertEquals(
                    "2", metadata.parameter("isotope_error", NEWER).orElseThrow().defaultValue());
            assertEquals(
                    4, metadata.parameter("isotope_error", NEWER).orElseThrow().choices().size());
        }
    }

    @Nested
    @DisplayName("what the loader refuses")
    class Refused {

        @Test
        void anOverrideThatReplacesNothing() {
            rejected(
                    withNewerVersion(override("isotope_error")),
                    WHERE + "\"isotope_error\"",
                    "name",
                    "replaces no field; an override names at least one of [default, choices,"
                            + " inlineComment, shortHelp, helpUrl]");
        }

        @Test
        @DisplayName("a field no release can override, such as the kind or the display name")
        void aFieldThatIsNotOverridable() {
            rejected(
                    withNewerVersion(override("isotope_error", "displayName", "Newer")),
                    "versions[1]",
                    "displayName",
                    "is not a field this format has; expected only [name, source, default,"
                            + " choices, inlineComment, shortHelp, helpUrl]");
            rejected(
                    withNewerVersion(override("isotope_error", "kind", "INTEGER")),
                    "versions[1]",
                    "kind",
                    "is not a field this format has");
        }

        @Test
        @DisplayName("an override cannot name a version of its own: it is its record's")
        void anOverrideNamingAVersion() {
            rejected(
                    withNewerVersion(
                            override(
                                    "isotope_error",
                                    "shortHelp",
                                    "Newer.",
                                    "version",
                                    "2099.01.0")),
                    "versions[1]",
                    "version",
                    "is not a field this format has");
        }

        @Test
        void anUnmodelledParameter() {
            rejected(
                    withNewerVersion(override("no_such_knob", "shortHelp", "Newer.")),
                    WHERE + "\"no_such_knob\"",
                    "name",
                    "is not a modelled parameter");
        }

        @Test
        @DisplayName("a parameter whose range ends before the version")
        void aParameterTheVersionDoesNotHave() {
            ConstructedMetadata doc =
                    withNewerVersion(override("isotope_error", "shortHelp", "N."));
            @SuppressWarnings("unchecked")
            Map<String, Object> range =
                    (Map<String, Object>) doc.parameter("isotope_error").get("versions");
            range.put("through", ConstructedMetadata.VERSION);
            rejected(
                    doc,
                    WHERE + "\"isotope_error\"",
                    "name",
                    "is not modelled for Comet 2026.03.0, so that version has nothing to override");
        }

        @Test
        void aParameterOverriddenTwice() {
            rejected(
                    withNewerVersion(
                            override("isotope_error", "shortHelp", "Newer."),
                            override("isotope_error", "helpUrl", "https://example.org/x")),
                    WHERE + "\"isotope_error\"",
                    "name",
                    "is overridden twice");
        }

        @Test
        void aSourceThatIsNotHttps() {
            Map<String, Object> entry = override("isotope_error", "shortHelp", "Newer.");
            entry.put("source", "Comet.cpp line 939");
            rejected(
                    withNewerVersion(entry),
                    WHERE + "\"isotope_error\"",
                    "source",
                    "is not an https:// reference");
        }

        @Test
        @DisplayName("each field equal to the curated one: an override records only a difference")
        void aFieldEqualToTheCuratedOne() {
            rejected(
                    withNewerVersion(override("isotope_error", "default", "2")),
                    WHERE + "\"isotope_error\"",
                    "default",
                    "repeats the parameter's own curated default \"2\"");
            rejected(
                    withNewerVersion(
                            override(
                                    "isotope_error",
                                    "choices",
                                    choices("0", "Off", "1", "0, +1", "2", "0, +1, +2"))),
                    WHERE + "\"isotope_error\"",
                    "choices",
                    "repeats the parameter's own curated choices");
            rejected(
                    withNewerVersion(override("isotope_error", "inlineComment", null)),
                    WHERE + "\"isotope_error\"",
                    "inlineComment",
                    "repeats the parameter's own curated inline comment");
            rejected(
                    withNewerVersion(
                            override(
                                    "isotope_error",
                                    "shortHelp",
                                    "Constructed help for isotope_error.")),
                    WHERE + "\"isotope_error\"",
                    "shortHelp",
                    "repeats the parameter's own curated short help");
            rejected(
                    withNewerVersion(
                            override(
                                    "isotope_error",
                                    "helpUrl",
                                    "https://example.org/isotope_error")),
                    WHERE + "\"isotope_error\"",
                    "helpUrl",
                    "repeats the parameter's own curated help reference");
        }

        @Test
        @DisplayName("the same choices with another label are a difference, not a repeat")
        void aRelabelledChoiceIsADifference() {
            CuratedMetadata metadata =
                    withNewerVersion(
                                    override(
                                            "isotope_error",
                                            "choices",
                                            choices("0", "Off", "1", "0, +1", "2", "relabelled")))
                            .load();
            assertEquals(
                    "relabelled",
                    metadata.parameter("isotope_error", NEWER)
                            .orElseThrow()
                            .choices()
                            .get(2)
                            .label());
        }

        @Test
        @DisplayName("a default that is not one of the version's choices")
        void aDefaultOutsideTheVersionsChoices() {
            rejected(
                    withNewerVersion(override("isotope_error", "default", "-1")),
                    WHERE + "\"isotope_error\"",
                    "default",
                    "\"-1\" is not one of its choices");
        }

        @Test
        @DisplayName("choices that drop the default the version keeps")
        void choicesThatDropTheKeptDefault() {
            rejected(
                    withNewerVersion(
                            override(
                                    "isotope_error", "choices", choices("0", "Off", "1", "0, +1"))),
                    WHERE + "\"isotope_error\"",
                    "default",
                    "\"2\" is not one of its choices");
        }

        @Test
        @DisplayName("a default of the wrong shape, or outside the curated bounds")
        void aDefaultBreakingItsKind() {
            rejected(
                    withNewerVersion(override("allowed_missed_cleavage", "default", "6")),
                    WHERE + "\"allowed_missed_cleavage\"",
                    "default",
                    "is above its own max 5");
            rejected(
                    withNewerVersion(override("allowed_missed_cleavage", "default", "2.5")),
                    WHERE + "\"allowed_missed_cleavage\"",
                    "default",
                    "is not a whole number");
        }

        @Test
        @DisplayName("choices for a kind that is not enumerated")
        void choicesOnANonEnumeratedKind() {
            rejected(
                    withNewerVersion(
                            override(
                                    "allowed_missed_cleavage",
                                    "choices",
                                    choices("1", "one", "2", "two"))),
                    WHERE + "\"allowed_missed_cleavage\"",
                    "choices",
                    "kind INTEGER is not enumerated, so it has no choices");
        }

        @Test
        @DisplayName("choices that break the curated choice rules")
        void malformedChoices() {
            rejected(
                    withNewerVersion(override("isotope_error", "choices", choices("0", "Off"))),
                    WHERE + "\"isotope_error\"",
                    "choices",
                    "an enumerated kind needs at least two labelled choices");
            rejected(
                    withNewerVersion(
                            override(
                                    "isotope_error",
                                    "choices",
                                    choices("0", "Off", "0.5", "Half"))),
                    WHERE + "\"isotope_error\"",
                    "choices",
                    "value \"0.5\" of an integer enum is not whole");
            rejected(
                    withNewerVersion(
                            override(
                                    "isotope_error", "choices", choices("0", "Off", "0", "Again"))),
                    WHERE + "\"isotope_error\"",
                    "choices",
                    "value \"0\" is listed twice");
            rejected(
                    withNewerVersion(
                            override("isotope_error", "choices", choices("0", "Off", "1", " "))),
                    WHERE + "\"isotope_error\"",
                    "label",
                    "is blank");
        }

        @Test
        @DisplayName("an inline comment that would not read back as written")
        void aMalformedInlineComment() {
            rejected(
                    withNewerVersion(override("isotope_error", "inlineComment", "  ")),
                    WHERE + "\"isotope_error\"",
                    "inlineComment",
                    "is blank; write null for no comment");
            rejected(
                    withNewerVersion(override("isotope_error", "inlineComment", "two\nlines")),
                    WHERE + "\"isotope_error\"",
                    "inlineComment",
                    "holds a line break");
            rejected(
                    withNewerVersion(override("isotope_error", "inlineComment", " padded")),
                    WHERE + "\"isotope_error\"",
                    "inlineComment",
                    "has surrounding white space");
            rejected(
                    withNewerVersion(override("isotope_error", "inlineComment", 7L)),
                    WHERE + "\"isotope_error\"",
                    "inlineComment",
                    "must be a string or null");
        }

        @Test
        @DisplayName("blank help, a help reference that is not https, a default that is not text")
        void malformedTextFields() {
            rejected(
                    withNewerVersion(override("isotope_error", "shortHelp", " ")),
                    WHERE + "\"isotope_error\"",
                    "shortHelp",
                    "is blank");
            rejected(
                    withNewerVersion(override("isotope_error", "helpUrl", "http://example.org/")),
                    WHERE + "\"isotope_error\"",
                    "helpUrl",
                    "is not an https:// reference");
            rejected(
                    withNewerVersion(override("isotope_error", "default", 1L)),
                    WHERE + "\"isotope_error\"",
                    "default",
                    "must be a string");
        }

        @Test
        @DisplayName("an overrides member that is not an array, or an entry that is not an object")
        void malformedContainer() {
            ConstructedMetadata doc = withNewerVersion();
            @SuppressWarnings("unchecked")
            Map<String, Object> record = (Map<String, Object>) doc.list("versions").get(1);
            record.put("overrides", "none");
            rejected(doc, "versions[1]", "overrides", "must be an array");
            record.put("overrides", new ArrayList<>(List.of("isotope_error")));
            rejected(doc, "versions[1]", "overrides[0]", "must be a JSON object");
        }
    }
}
