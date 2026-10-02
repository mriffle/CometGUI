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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.cometgui.domain.tools.ToolVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The loader's rules, one test per rule, each over a CONSTRUCTED document ({@link
 * ConstructedMetadata}) broken in exactly one place, each asserting the parameter or section and
 * the field the diagnostic names.
 */
class MetadataLoaderTest {

    private static final String ISOTOPE = "parameter \"isotope_error\"";
    private static final String MISSED = "parameter \"allowed_missed_cleavage\"";

    private static InvalidMetadataException rejected(
            ConstructedMetadata doc, String where, String field, String fragment) {
        InvalidMetadataException failure = assertThrows(InvalidMetadataException.class, doc::load);
        assertEquals(where, failure.where(), failure.getMessage());
        assertEquals(field, failure.field(), failure.getMessage());
        assertTrue(
                failure.getMessage().contains(fragment),
                () -> "expected \"" + fragment + "\" in: " + failure.getMessage());
        assertTrue(
                failure.getMessage().contains(where) && failure.getMessage().contains(field),
                failure.getMessage());
        return failure;
    }

    @Test
    @DisplayName("the constructed document is valid, so every rejection below is its one change")
    void theConstructedDocumentLoads() {
        CuratedMetadata metadata = ConstructedMetadata.valid().load();
        assertEquals(9, metadata.parameters().size());
        assertEquals(1, metadata.schemaVersion());
        ParameterDefinition isotope = metadata.parameter("isotope_error").orElseThrow();
        assertEquals("Display isotope_error", isotope.displayName());
        assertEquals(ParameterCategory.PRECURSOR_MASS, isotope.category());
        assertEquals(ValueKind.INTEGER_ENUM, isotope.kind());
        assertEquals(VisibilityLevel.ADVANCED, isotope.visibility());
        assertEquals("2", isotope.defaultValue());
        assertEquals(
                List.of(
                        new Choice("0", "Off"),
                        new Choice("1", "0, +1"),
                        new Choice("2", "0, +1, +2")),
                isotope.choices());
        assertEquals(List.of(ValidatorId.CHOICE), isotope.validators());
        assertEquals("https://example.org/isotope_error", isotope.detailedHelpRef());
        assertEquals(SerializationRule.SINGLE_VALUE, isotope.serialization());
        ParameterDefinition missed = metadata.parameter("allowed_missed_cleavage").orElseThrow();
        assertEquals(Optional.of("0"), missed.minimum());
        assertEquals(Optional.of("5"), missed.maximum());
        assertEquals(List.of("missed cleavage"), missed.aliases());
        assertEquals(List.of("search_enzyme_number"), missed.related());
        assertEquals(
                new VersionRange(ToolVersion.parse("2026.02.2"), Optional.empty()),
                missed.supportedVersions());
        assertEquals(
                List.of(new InternalParameter("secret_knob", "constructed for this test only")),
                metadata.internal());
        assertEquals(List.of("search_enzyme_number"), metadata.enzymeTable().referencedBy());
        assertEquals(2, metadata.enzymeTable().senseChoices().size());
        assertEquals("[COMET_ENZYME_INFO]", metadata.enzymeTable().header());
        CometVersionRecord record = metadata.versions().get(0);
        assertEquals(ToolVersion.parse("2026.02.2"), record.version());
        assertEquals("2026.02 rev. 2 (6edec91)", record.marker().text());
        assertEquals("https://example.org/pages/", record.parameterPages());
        assertEquals("https://example.org/source/", record.source());
        VariableModLayout layout = record.variableModTuple();
        assertEquals("https://example.org/source/Comet.cpp", layout.source());
        assertEquals(
                List.of(
                        VariableModField.MASS,
                        VariableModField.RESIDUES,
                        VariableModField.BINARY_GROUP,
                        VariableModField.COUNT,
                        VariableModField.TERMINAL_DISTANCE,
                        VariableModField.TERMINUS,
                        VariableModField.REQUIRED,
                        VariableModField.NEUTRAL_LOSS),
                layout.fields().stream().map(VariableModLayout.Entry::field).toList());
        assertEquals(
                List.of(false, false, false, true, false, false, false, true),
                layout.fields().stream().map(VariableModLayout.Entry::acceptsPair).toList());
    }

    @Nested
    @DisplayName("the variable-modification tuple layout")
    class TupleLayout {

        @SuppressWarnings("unchecked")
        private Map<String, Object> layout(ConstructedMetadata doc) {
            return (Map<String, Object>)
                    ((Map<String, Object>) doc.list("versions").get(0)).get("variableModTuple");
        }

        @SuppressWarnings("unchecked")
        private List<Object> fields(ConstructedMetadata doc) {
            return (List<Object>) layout(doc).get("fields");
        }

        @SuppressWarnings("unchecked")
        private Map<String, Object> field(ConstructedMetadata doc, int index) {
            return (Map<String, Object>) fields(doc).get(index);
        }

        private void addTuple(ConstructedMetadata doc, String name, String value) {
            doc.list("parameters")
                    .add(
                            new ConstructedMetadata.Builder(
                                            name, "VARIABLE_MODS", "VARIABLE_MOD_TUPLE", value)
                                    .serialization("TUPLE")
                                    .validators("variable_mod_tuple")
                                    .map());
        }

        @Test
        void aMissingFieldListIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            layout(doc).remove("fields");
            rejected(doc, "versions[0]", "fields", "is missing");
        }

        @Test
        void anUnknownLayoutFieldIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            layout(doc).put("fieldCount", 8L);
            rejected(doc, "versions[0]", "fieldCount", "is not a field this format has");
        }

        @Test
        void aSourceThatIsNotHttpsIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            layout(doc).put("source", "Comet.cpp line 552");
            rejected(doc, "versions[0]", "source", "is not an https:// reference");
        }

        @Test
        void anEntryThatIsNotAnObjectIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            fields(doc).set(2, "BINARY_GROUP");
            rejected(doc, "versions[0]", "variableModTuple.fields[2]", "must be a JSON object");
        }

        @Test
        void anEntryWithAnUnknownMemberIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            field(doc, 0).put("since", "2026.02.2");
            rejected(doc, "versions[0]", "since", "is not a field this format has");
        }

        @Test
        void anUnknownTupleFieldIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            field(doc, 2).put("field", "BINARY");
            rejected(doc, "versions[0]", "field", "\"BINARY\" is not one of");
        }

        @Test
        void anUnknownFieldKindIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            field(doc, 2).put("kind", "WHOLE");
            rejected(doc, "versions[0]", "kind", "\"WHOLE\" is not one of");
        }

        @Test
        void aPairFlagThatIsNotABooleanIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            field(doc, 3).put("pair", "true");
            rejected(doc, "versions[0]", "pair", "must be true or false");
        }

        @Test
        void aKindThatIsNotTheFieldsKindIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            field(doc, 3).put("kind", "DECIMAL");
            rejected(
                    doc,
                    "versions[0]",
                    "variableModTuple",
                    "field 4 (COUNT) is declared DECIMAL, and a count per peptide is INTEGER");
        }

        @Test
        void aPairOnAFieldNoCometPairsIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            field(doc, 6).put("pair", true);
            rejected(
                    doc,
                    "versions[0]",
                    "variableModTuple",
                    "field 7 (REQUIRED) is given a comma pair");
        }

        @Test
        void aFieldListedTwiceIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            fields(doc).add(ConstructedMetadata.tupleField("COUNT", "INTEGER", true));
            rejected(doc, "versions[0]", "variableModTuple", "field 9 (COUNT) is listed twice");
        }

        @Test
        void aLayoutWithoutTheMassIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            fields(doc).remove(0);
            rejected(doc, "versions[0]", "variableModTuple", "the layout has no MASS field");
        }

        @Test
        void anEmptyLayoutIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            fields(doc).clear();
            rejected(doc, "versions[0]", "variableModTuple", "the layout has no MASS field");
        }

        @Test
        void aLayoutWithoutTheResiduesIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            fields(doc).remove(1);
            rejected(doc, "versions[0]", "variableModTuple", "the layout has no RESIDUES field");
        }

        @Test
        void aTupleSlotWithTheLayoutsFieldCountLoads() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            addTuple(doc, "variable_mod01", "15.9949 M 0 3 -1 0 0 0.0");
            assertEquals(
                    ValueKind.VARIABLE_MOD_TUPLE,
                    doc.load().parameter("variable_mod01").orElseThrow().kind());
        }

        @Test
        void aTupleDefaultWithTheWrongFieldCountIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            addTuple(doc, "variable_mod01", "15.9949 M 0 3 -1 0 0");
            rejected(
                    doc,
                    "parameter \"variable_mod01\"",
                    "default",
                    "holds 7 fields, and the tuple layout of Comet 2026.02.2 has 8");
        }

        @Test
        void aTupleDefaultForAVersionItDoesNotClaimIsNotCounted() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            Map<String, Object> older = new java.util.LinkedHashMap<>();
            older.put("version", "2025.03.1");
            older.put("marker", "2025.03 rev. 1");
            older.put("parameterPages", "https://example.org/older/");
            older.put("source", "https://example.org/older-source/");
            Map<String, Object> sevenFields = ConstructedMetadata.tupleLayout();
            ((List<?>) sevenFields.get("fields")).remove(7);
            older.put("variableModTuple", sevenFields);
            older.put("defaults", List.of());
            doc.list("versions").add(older);
            addTuple(doc, "variable_mod01", "15.9949 M 0 3 -1 0 0 0.0");
            CuratedMetadata metadata = doc.load();
            assertEquals(
                    7,
                    metadata.version(ToolVersion.parse("2025.03.1"))
                            .orElseThrow()
                            .variableModTuple()
                            .fields()
                            .size());
        }

        @Test
        void aTupleUnderAnotherNameIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            addTuple(doc, "variable_mod1", "15.9949 M 0 3 -1 0 0 0.0");
            rejected(
                    doc,
                    "parameter \"variable_mod1\"",
                    "kind",
                    "Comet reads a tuple only under a variable_modNN name");
        }

        @Test
        void aSlotNameOfAnotherKindIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            doc.list("parameters")
                    .add(
                            new ConstructedMetadata.Builder(
                                            "variable_mod16", "VARIABLE_MODS", "STRING", "x")
                                    .map());
            rejected(
                    doc,
                    "parameter \"variable_mod16\"",
                    "kind",
                    "is STRING, and Comet reads every variable_modNN as a tuple");
        }
    }

    @Nested
    @DisplayName("the document as a whole")
    class Document {

        @Test
        void notJsonIsRejected() {
            InvalidMetadataException failure =
                    assertThrows(InvalidMetadataException.class, () -> MetadataLoader.load("{"));
            assertEquals("the document", failure.where());
            assertTrue(failure.getMessage().contains("line 1"), failure.getMessage());
        }

        @Test
        void aRootThatIsNotAnObjectIsRejected() {
            InvalidMetadataException failure =
                    assertThrows(InvalidMetadataException.class, () -> MetadataLoader.load("[]"));
            assertEquals("(root)", failure.field());
        }

        @Test
        void aMissingTopLevelFieldIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            doc.root().remove("description");
            rejected(doc, "the document", "description", "is missing");
        }

        @Test
        void anUnknownTopLevelFieldIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            doc.root().put("extra", "x");
            rejected(doc, "the document", "extra", "is not a field this format has");
        }

        @Test
        void anotherSchemaVersionIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            doc.root().put("schemaVersion", 2L);
            rejected(doc, "the document", "schemaVersion", "is 2");
        }

        @Test
        void aSchemaVersionThatIsNotANumberIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            doc.root().put("schemaVersion", "1");
            rejected(doc, "the document", "schemaVersion", "must be a whole number");
        }

        @Test
        void aBlankDescriptionIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            doc.root().put("description", " ");
            rejected(doc, "the document", "description", "is blank");
        }

        @Test
        void aSectionThatIsNotAnArrayIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            doc.root().put("parameters", "none");
            rejected(doc, "the document", "parameters", "must be an array");
        }
    }

    @Nested
    @DisplayName("versions")
    class Versions {

        @SuppressWarnings("unchecked")
        private Map<String, Object> version(ConstructedMetadata doc) {
            return (Map<String, Object>) doc.list("versions").get(0);
        }

        @Test
        void noVersionIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            doc.list("versions").clear();
            rejected(doc, "the document", "versions", "is empty");
        }

        @Test
        void anUnparseableVersionIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            version(doc).put("version", "2026");
            rejected(doc, "versions[0]", "version", "not a recognised tool version");
        }

        @Test
        void anUnparseableMarkerIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            version(doc).put("marker", "2026.02.2");
            rejected(doc, "versions[0]", "marker", "is not a Comet version marker");
        }

        @Test
        void aMarkerForAnotherVersionIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            version(doc).put("marker", "2026.02 rev. 3 (6edec91)");
            rejected(doc, "versions[0]", "marker", "is Comet 2026.02.3, not 2026.02.2");
        }

        @Test
        void aVersionListedTwiceIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            doc.list("versions").add(doc.list("versions").get(0));
            rejected(doc, "versions[1]", "version", "2026.02.2 is listed twice");
        }

        @Test
        void aTupleLayoutThatIsNotAnObjectIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            version(doc).put("variableModTuple", "mass residues ...");
            rejected(doc, "versions[0]", "variableModTuple", "must be a JSON object");
        }

        @Test
        void aNullTupleLayoutIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            version(doc).put("variableModTuple", null);
            rejected(doc, "versions[0]", "variableModTuple", "must be a JSON object");
        }

        @Test
        void aReferenceThatIsNotHttpsIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            version(doc).put("source", "http://example.org/");
            rejected(doc, "versions[0]", "source", "is not an https:// reference");
        }
    }

    @Nested
    @DisplayName("categories")
    class Categories {

        @SuppressWarnings("unchecked")
        private Map<String, Object> category(ConstructedMetadata doc, int index) {
            return (Map<String, Object>) doc.list("categories").get(index);
        }

        @Test
        void aMissingCategoryIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            doc.list("categories").remove(13);
            rejected(doc, "the document", "categories", "lists 13 categories");
        }

        @Test
        void aRenamedCategoryIdIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            category(doc, 2).put("id", "precursor");
            rejected(doc, "categories[2]", "id", "\"precursor_mass\" belongs");
        }

        @Test
        void aChangedDisplayNameIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            category(doc, 0).put("displayName", "Database");
            rejected(doc, "categories[0]", "displayName", "\"Database and PEFF\" belongs");
        }
    }

    @Nested
    @DisplayName("a parameter's identity and words")
    class Identity {

        @Test
        void aMissingNameIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            doc.parameter("isotope_error").remove("name");
            rejected(doc, "parameters[1]", "name", "is missing");
        }

        @Test
        void aNameCometCouldNotDeclareIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            doc.parameter("isotope_error").put("name", "isotope error");
            rejected(doc, "parameters[1]", "name", "is not a name Comet could declare");
        }

        @Test
        void aDuplicateNameIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            doc.parameter("use_B_ions").put("name", "isotope_error");
            rejected(doc, ISOTOPE, "name", "is defined twice");
        }

        @Test
        void aMissingDisplayNameIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            doc.parameter("isotope_error").remove("displayName");
            rejected(doc, ISOTOPE, "displayName", "is missing");
        }

        @Test
        void aBlankDisplayNameIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            doc.parameter("isotope_error").put("displayName", "");
            rejected(doc, ISOTOPE, "displayName", "is blank");
        }

        @Test
        void aDisplayNameThatIsNotAStringIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            doc.parameter("isotope_error").put("displayName", 3L);
            rejected(doc, ISOTOPE, "displayName", "must be a string");
        }

        @Test
        void anUnknownFieldIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            doc.parameter("isotope_error").put("units", "Da");
            rejected(doc, ISOTOPE, "units", "is not a field this format has");
        }

        @Test
        void blankHelpIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            doc.parameter("isotope_error").put("shortHelp", "  ");
            rejected(doc, ISOTOPE, "shortHelp", "is blank");
        }

        @Test
        @DisplayName("a curated inline comment loads exactly as written, and null means none")
        void anInlineCommentLoads() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            doc.parameter("isotope_error").put("inlineComment", "0=off, 1=0/1 (C13 error)");
            CuratedMetadata metadata = doc.load();
            assertEquals(
                    Optional.of("0=off, 1=0/1 (C13 error)"),
                    metadata.parameter("isotope_error").orElseThrow().inlineComment());
            assertEquals(
                    Optional.empty(),
                    metadata.parameter("allowed_missed_cleavage").orElseThrow().inlineComment());
        }

        @Test
        void aMissingInlineCommentIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            doc.parameter("isotope_error").remove("inlineComment");
            rejected(doc, ISOTOPE, "inlineComment", "is missing");
        }

        @Test
        void anInlineCommentThatIsNotAStringIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            doc.parameter("isotope_error").put("inlineComment", 2L);
            rejected(doc, ISOTOPE, "inlineComment", "must be a string or null");
        }

        @Test
        void aBlankInlineCommentIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            doc.parameter("isotope_error").put("inlineComment", "   ");
            rejected(doc, ISOTOPE, "inlineComment", "is blank; write null for no comment");
        }

        @Test
        void anEmptyInlineCommentIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            doc.parameter("isotope_error").put("inlineComment", "");
            rejected(doc, ISOTOPE, "inlineComment", "is blank; write null for no comment");
        }

        @Test
        void anInlineCommentWithANewlineIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            doc.parameter("isotope_error").put("inlineComment", "first\nsecond");
            rejected(doc, ISOTOPE, "inlineComment", "holds a line break");
        }

        @Test
        void anInlineCommentWithACarriageReturnIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            doc.parameter("isotope_error").put("inlineComment", "first\rsecond");
            rejected(doc, ISOTOPE, "inlineComment", "holds a line break");
        }

        @Test
        void anInlineCommentStartingWithALineBreakIsRejectedAsOne() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            doc.parameter("isotope_error").put("inlineComment", "\n0=off");
            rejected(doc, ISOTOPE, "inlineComment", "holds a line break");
            doc.parameter("isotope_error").put("inlineComment", "\r0=off");
            rejected(doc, ISOTOPE, "inlineComment", "holds a line break");
        }

        @Test
        void aPaddedInlineCommentIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            doc.parameter("isotope_error").put("inlineComment", " 0=off");
            rejected(doc, ISOTOPE, "inlineComment", "has surrounding white space");
        }

        @Test
        void anInlineCommentWithTrailingSpaceIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            doc.parameter("isotope_error").put("inlineComment", "0=off ");
            rejected(doc, ISOTOPE, "inlineComment", "has surrounding white space");
        }

        @Test
        void aHelpReferenceThatIsNotHttpsIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            doc.parameter("isotope_error").put("helpUrl", "isotope_error.html");
            rejected(doc, ISOTOPE, "helpUrl", "is not an https:// reference");
        }

        @Test
        void aBlankAliasIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            doc.parameter("isotope_error").put("aliases", new ArrayList<>(List.of("C13", "")));
            rejected(doc, ISOTOPE, "aliases", "non-blank strings only");
        }

        @Test
        void anAliasThatIsNotAStringIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            doc.parameter("isotope_error").put("aliases", new ArrayList<>(List.of(1L)));
            rejected(doc, ISOTOPE, "aliases", "non-blank strings only");
        }

        @Test
        void aRepeatedAliasIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            doc.parameter("isotope_error").put("aliases", new ArrayList<>(List.of("C13", "C13")));
            rejected(doc, ISOTOPE, "aliases", "holds \"C13\" twice");
        }
    }

    @Nested
    @DisplayName("a parameter's category, kind, visibility and serialisation")
    class Vocabulary {

        @Test
        void anUnknownCategoryIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            doc.parameter("isotope_error").put("category", "isotopes");
            rejected(doc, ISOTOPE, "category", "\"isotopes\" is not one of [database_peff");
        }

        @Test
        void anUnknownKindIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            doc.parameter("isotope_error").put("kind", "ENUM");
            rejected(doc, ISOTOPE, "kind", "\"ENUM\" is not one of [INTEGER, DECIMAL");
        }

        @Test
        void anUnknownVisibilityIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            doc.parameter("isotope_error").put("visibility", "BASIC");
            rejected(doc, ISOTOPE, "visibility", "\"BASIC\" is not one of [ESSENTIALS");
        }

        @Test
        void anUnknownSerializationIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            doc.parameter("isotope_error").put("serialization", "single_value");
            rejected(doc, ISOTOPE, "serialization", "is not one of [SINGLE_VALUE");
        }

        @Test
        void aSerializationTheKindDoesNotAllowIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            doc.parameter("digest_mass_range").put("serialization", "SINGLE_VALUE");
            rejected(
                    doc,
                    "parameter \"digest_mass_range\"",
                    "serialization",
                    "SINGLE_VALUE does not fit kind DECIMAL_RANGE");
        }
    }

    @Nested
    @DisplayName("choices")
    class Choices {

        @Test
        void anEnumWithOneChoiceIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            doc.parameter("isotope_error")
                    .put("choices", new ArrayList<>(List.of(ConstructedMetadata.choice("2", "x"))));
            rejected(doc, ISOTOPE, "choices", "at least two labelled choices");
        }

        @Test
        void choicesOnANonEnumKindAreRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            doc.parameter("allowed_missed_cleavage")
                    .put(
                            "choices",
                            new ArrayList<>(
                                    List.of(
                                            ConstructedMetadata.choice("1", "one"),
                                            ConstructedMetadata.choice("2", "two"))));
            rejected(doc, MISSED, "choices", "kind INTEGER is not enumerated");
        }

        @Test
        void aChoiceWithoutALabelIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            Map<String, Object> unlabelled = ConstructedMetadata.choice("3", "x");
            unlabelled.remove("label");
            doc.parameter("isotope_error")
                    .put(
                            "choices",
                            new ArrayList<>(
                                    List.of(ConstructedMetadata.choice("2", "two"), unlabelled)));
            rejected(doc, ISOTOPE, "label", "is missing");
        }

        @Test
        void aChoiceWithABlankLabelIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            doc.parameter("isotope_error")
                    .put(
                            "choices",
                            new ArrayList<>(
                                    List.of(
                                            ConstructedMetadata.choice("2", "two"),
                                            ConstructedMetadata.choice("3", " "))));
            rejected(doc, ISOTOPE, "label", "is blank");
        }

        @Test
        void aChoiceListedTwiceIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            doc.parameter("isotope_error")
                    .put(
                            "choices",
                            new ArrayList<>(
                                    List.of(
                                            ConstructedMetadata.choice("2", "two"),
                                            ConstructedMetadata.choice("2", "again"))));
            rejected(doc, ISOTOPE, "choices", "value \"2\" is listed twice");
        }

        @Test
        void aNonIntegerChoiceOfAnIntegerEnumIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            doc.parameter("isotope_error")
                    .put(
                            "choices",
                            new ArrayList<>(
                                    List.of(
                                            ConstructedMetadata.choice("2", "two"),
                                            ConstructedMetadata.choice("2.5", "half"))));
            rejected(doc, ISOTOPE, "choices", "value \"2.5\" of an integer enum is not whole");
        }

        @Test
        void aWordChoiceOfAStringEnumIsAccepted() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            CuratedMetadata metadata = doc.load();
            assertEquals(
                    List.of(new Choice("ALL", "All methods"), new Choice("HCD", "HCD")),
                    metadata.parameter("activation_method").orElseThrow().choices());
        }
    }

    @Nested
    @DisplayName("defaults")
    class Defaults {

        @Test
        void anEnumDefaultOutsideItsChoicesIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            doc.parameter("isotope_error").put("default", "7");
            rejected(doc, ISOTOPE, "default", "\"7\" is not one of its choices");
        }

        @Test
        void aStringEnumDefaultOutsideItsChoicesIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            doc.parameter("activation_method").put("default", "all");
            rejected(
                    doc,
                    "parameter \"activation_method\"",
                    "default",
                    "\"all\" is not one of its choices");
        }

        @Test
        void aDefaultBelowItsOwnMinIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            doc.parameter("allowed_missed_cleavage").put("default", "-1");
            rejected(doc, MISSED, "default", "-1 is below its own min 0");
        }

        @Test
        void aDefaultAboveItsOwnMaxIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            doc.parameter("allowed_missed_cleavage").put("default", "6");
            rejected(doc, MISSED, "default", "6 is above its own max 5");
        }

        @Test
        void aDefaultAtItsBoundsIsAccepted() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            doc.parameter("allowed_missed_cleavage").put("default", "5");
            assertEquals(
                    "5",
                    doc.load().parameter("allowed_missed_cleavage").orElseThrow().defaultValue());
            doc.parameter("allowed_missed_cleavage").put("default", "0");
            assertEquals(
                    "0",
                    doc.load().parameter("allowed_missed_cleavage").orElseThrow().defaultValue());
        }

        @Test
        void aRangeElementBelowTheMinIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            doc.parameter("digest_mass_range").put("default", "-1.0 5000.0");
            rejected(
                    doc,
                    "parameter \"digest_mass_range\"",
                    "default",
                    "-1.0 is below its own min 0.0");
        }

        @Test
        void aListElementBelowTheMinIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            doc.parameter("mass_offsets").put("default", "0.0 -18.0");
            rejected(
                    doc, "parameter \"mass_offsets\"", "default", "-18.0 is below its own min 0.0");
        }

        @Test
        void aListOfSeveralValuesIsAccepted() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            doc.parameter("mass_offsets").put("default", "0.0 18.0 36.0");
            assertEquals(
                    "0.0 18.0 36.0",
                    doc.load().parameter("mass_offsets").orElseThrow().defaultValue());
        }

        @Test
        void aRangeWithOneValueIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            doc.parameter("digest_mass_range").put("default", "600.0");
            rejected(doc, "parameter \"digest_mass_range\"", "default", "holds 1 values, not 2");
        }

        @Test
        void aScalarWithTwoValuesIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            doc.parameter("allowed_missed_cleavage").put("default", "1 2");
            rejected(doc, MISSED, "default", "holds 2 values, not 1");
        }

        @Test
        void aNonNumericNumberIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            doc.parameter("peptide_mass_tolerance_lower").put("default", "minus");
            rejected(
                    doc,
                    "parameter \"peptide_mass_tolerance_lower\"",
                    "default",
                    "\"minus\" is not a number");
        }

        @Test
        void aFractionForAWholeNumberIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            doc.parameter("allowed_missed_cleavage").put("default", "2.0");
            rejected(doc, MISSED, "default", "\"2.0\" is not a whole number");
        }

        @Test
        void anEmptyDefaultWhereEmptyIsNotAValueIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            doc.parameter("allowed_missed_cleavage").put("default", "");
            rejected(doc, MISSED, "default", "is empty, and serialization SINGLE_VALUE is not");
        }

        @Test
        void anEmptyDefaultWhereEmptyIsAValueIsAccepted() {
            CuratedMetadata metadata = ConstructedMetadata.valid().load();
            assertEquals("", metadata.parameter("peff_obo").orElseThrow().defaultValue());
            assertEquals(
                    SerializationRule.EMPTY_ALLOWED,
                    metadata.parameter("peff_obo").orElseThrow().serialization());
        }

        @Test
        void surroundingWhiteSpaceIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            doc.parameter("allowed_missed_cleavage").put("default", "2 ");
            rejected(doc, MISSED, "default", "surrounding white space");
        }

        @Test
        void aFlagThatIsNotZeroOrOneIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            doc.parameter("use_B_ions").put("default", "2");
            rejected(doc, "parameter \"use_B_ions\"", "default", "\"2\" is not 0 or 1");
        }

        @Test
        void bothFlagValuesAreAccepted() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            doc.parameter("use_B_ions").put("default", "0");
            assertEquals("0", doc.load().parameter("use_B_ions").orElseThrow().defaultValue());
        }

        @Test
        void aNegativeEnzymeNumberIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            doc.parameter("search_enzyme_number").put("default", "-1");
            rejected(
                    doc,
                    "parameter \"search_enzyme_number\"",
                    "default",
                    "\"-1\" is not an enzyme number");
        }

        @Test
        void aDefaultThatIsNullIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            doc.parameter("allowed_missed_cleavage").put("default", null);
            rejected(doc, MISSED, "default", "must be a string");
        }
    }

    @Nested
    @DisplayName("bounds")
    class Bounds {

        @Test
        void boundsOnANonNumericKindAreRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            doc.parameter("isotope_error").put("min", "0");
            rejected(doc, ISOTOPE, "min", "kind INTEGER_ENUM is not a bounded number");
        }

        @Test
        void aMaxOnANonNumericKindIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            doc.parameter("peff_obo").put("max", "9");
            rejected(
                    doc, "parameter \"peff_obo\"", "max", "kind FILE_PATH is not a bounded number");
        }

        @Test
        void aMinAboveTheMaxIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            doc.parameter("allowed_missed_cleavage").put("min", "6");
            rejected(doc, MISSED, "max", "5 is below min 6");
        }

        @Test
        void anEqualMinAndMaxAreAccepted() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            doc.parameter("allowed_missed_cleavage").put("min", "2");
            doc.parameter("allowed_missed_cleavage").put("max", "2");
            assertEquals(
                    Optional.of("2"),
                    doc.load().parameter("allowed_missed_cleavage").orElseThrow().minimum());
        }

        @Test
        void aBoundThatIsNotANumberIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            doc.parameter("digest_mass_range").put("min", "zero");
            rejected(doc, "parameter \"digest_mass_range\"", "min", "\"zero\" is not a number");
        }

        @Test
        void aFractionalBoundOnAWholeKindIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            doc.parameter("allowed_missed_cleavage").put("max", "5.5");
            rejected(doc, MISSED, "max", "\"5.5\" is not a whole number");
        }

        @Test
        void aBoundThatIsANumberLiteralIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            doc.parameter("allowed_missed_cleavage").put("max", 5L);
            rejected(doc, MISSED, "max", "must be a string or null");
        }
    }

    @Nested
    @DisplayName("version ranges, validators and relationships")
    class Relations {

        @SuppressWarnings("unchecked")
        private Map<String, Object> range(ConstructedMetadata doc, String name) {
            return (Map<String, Object>) doc.parameter(name).get("versions");
        }

        @Test
        void aRangeStartingAtAnUncuratedVersionIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            range(doc, "isotope_error").put("from", "2025.01.0");
            rejected(doc, ISOTOPE, "versions", "starts at Comet 2025.01.0, which is not one of");
        }

        @Test
        void aRangeEndingBeforeItStartsIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            range(doc, "isotope_error").put("through", "2026.01.0");
            rejected(doc, ISOTOPE, "versions", "must not end (2026.01.0) before it starts");
        }

        @Test
        void aRangeEndIsKept() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            range(doc, "isotope_error").put("through", "2026.02.2");
            assertEquals(
                    Optional.of(ToolVersion.parse("2026.02.2")),
                    doc.load()
                            .parameter("isotope_error")
                            .orElseThrow()
                            .supportedVersions()
                            .through());
        }

        @Test
        void anUnparseableRangeEndIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            range(doc, "isotope_error").put("through", "later");
            rejected(doc, ISOTOPE, "through", "not a recognised tool version");
        }

        @Test
        void anUnknownValidatorIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            doc.parameter("isotope_error").put("validators", new ArrayList<>(List.of("choices")));
            rejected(doc, ISOTOPE, "validators", "\"choices\" is not one of [choice");
        }

        @Test
        void theGenericOrderingRuleOnATolerancePairMemberIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            doc.parameter("peptide_mass_tolerance_lower")
                    .put(
                            "validators",
                            new ArrayList<>(List.of("signed_tolerance_pair", "ordered_range")));
            rejected(
                    doc,
                    "parameter \"peptide_mass_tolerance_lower\"",
                    "validators",
                    "never by the generic ordered_range rule");
        }

        @Test
        void aRelatedParameterThatIsNotModelledIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            doc.parameter("allowed_missed_cleavage")
                    .put("related", new ArrayList<>(List.of("num_enzyme_termini")));
            rejected(doc, MISSED, "related", "names \"num_enzyme_termini\", which is not modelled");
        }

        @Test
        void aParameterRelatedToItselfIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            doc.parameter("allowed_missed_cleavage")
                    .put("related", new ArrayList<>(List.of("allowed_missed_cleavage")));
            rejected(doc, MISSED, "related", "names the parameter itself");
        }

        @Test
        void aRelatedParameterNamedTwiceIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            doc.parameter("allowed_missed_cleavage")
                    .put(
                            "related",
                            new ArrayList<>(
                                    List.of("search_enzyme_number", "search_enzyme_number")));
            rejected(doc, MISSED, "related", "holds \"search_enzyme_number\" twice");
        }
    }

    @Nested
    @DisplayName("the allow-list and the enzyme table")
    class AllowListAndEnzymes {

        @SuppressWarnings("unchecked")
        private Map<String, Object> entry(ConstructedMetadata doc) {
            return (Map<String, Object>) doc.list("internal").get(0);
        }

        @Test
        void anAllowListEntryWithoutAReasonIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            entry(doc).put("reason", "");
            rejected(doc, "internal parameter \"secret_knob\"", "reason", "is blank");
        }

        @Test
        void aParameterBothModelledAndAllowListedIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            entry(doc).put("name", "isotope_error");
            rejected(
                    doc,
                    "internal parameter \"isotope_error\"",
                    "name",
                    "is both modelled and allow-listed");
        }

        @Test
        void aParameterAllowListedTwiceIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            doc.list("internal").add(entry(doc));
            rejected(doc, "internal parameter \"secret_knob\"", "name", "is allow-listed twice");
        }

        @Test
        void anEnzymeReferenceMissingFromTheTableSectionIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            doc.section("enzymeTable").put("referencedBy", new ArrayList<>());
            rejected(
                    doc,
                    "enzymeTable",
                    "referencedBy",
                    "omits \"search_enzyme_number\", which is an enzyme reference");
        }

        @Test
        void aTableReferenceThatIsNotAnEnzymeParameterIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            doc.section("enzymeTable")
                    .put(
                            "referencedBy",
                            new ArrayList<>(List.of("search_enzyme_number", "isotope_error")));
            rejected(
                    doc,
                    "enzymeTable",
                    "referencedBy",
                    "names \"isotope_error\", which is not a modelled enzyme reference");
        }

        @Test
        void aTableReferenceToAnUnknownNameIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            doc.section("enzymeTable")
                    .put(
                            "referencedBy",
                            new ArrayList<>(List.of("search_enzyme_number", "enzyme3")));
            rejected(doc, "enzymeTable", "referencedBy", "names \"enzyme3\"");
        }

        @Test
        void aWrongTableHeaderIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            doc.section("enzymeTable").put("header", "[ENZYMES]");
            rejected(doc, "enzymeTable", "header", "is \"[ENZYMES]\", not [COMET_ENZYME_INFO]");
        }

        @Test
        void noSenseLabelsIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            doc.section("enzymeTable").put("senseChoices", new ArrayList<>());
            rejected(doc, "enzymeTable", "senseChoices", "is empty");
        }

        @Test
        void aSenseChoiceWithoutALabelIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            doc.section("enzymeTable")
                    .put(
                            "senseChoices",
                            new ArrayList<>(List.of(ConstructedMetadata.choice("0", ""))));
            rejected(doc, "enzymeTable", "label", "is blank");
        }

        @Test
        void aTableSectionThatIsNotAnObjectIsRejected() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            doc.root().put("enzymeTable", new ArrayList<>());
            rejected(doc, "enzymeTable", "enzymeTable", "must be a JSON object");
        }
    }

    @Nested
    @DisplayName("an unknown field is refused in every nested object, not only at the top")
    class NestedFields {

        @SuppressWarnings("unchecked")
        private Map<String, Object> first(ConstructedMetadata doc, String list) {
            return (Map<String, Object>) doc.list(list).get(0);
        }

        @Test
        void inAVersionRecord() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            first(doc, "versions").put("hash", "6edec91");
            rejected(doc, "versions[0]", "hash", "is not a field this format has");
        }

        @Test
        void inACategory() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            first(doc, "categories").put("order", 1L);
            rejected(doc, "categories[0]", "order", "is not a field this format has");
        }

        @Test
        void inAChoice() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            Map<String, Object> extra = ConstructedMetadata.choice("3", "three");
            extra.put("hint", "x");
            doc.parameter("isotope_error")
                    .put(
                            "choices",
                            new ArrayList<>(
                                    List.of(ConstructedMetadata.choice("2", "two"), extra)));
            rejected(doc, ISOTOPE, "hint", "is not a field this format has");
        }

        @Test
        void inAVersionRange() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            @SuppressWarnings("unchecked")
            Map<String, Object> range =
                    (Map<String, Object>) doc.parameter("isotope_error").get("versions");
            range.put("until", "2026.02.2");
            rejected(doc, ISOTOPE, "until", "is not a field this format has");
        }

        @Test
        void inAnAllowListEntry() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            first(doc, "internal").put("since", "2026.02.2");
            rejected(doc, "internal[0]", "since", "is not a field this format has");
        }

        @Test
        void inTheEnzymeTable() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            doc.section("enzymeTable").put("rows", new ArrayList<>());
            rejected(doc, "enzymeTable", "rows", "is not a field this format has");
        }

        @Test
        void inASenseChoice() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            Map<String, Object> extra = ConstructedMetadata.choice("0", "N-terminal");
            extra.put("note", "x");
            doc.section("enzymeTable").put("senseChoices", new ArrayList<>(List.of(extra)));
            rejected(doc, "enzymeTable", "note", "is not a field this format has");
        }
    }
}
