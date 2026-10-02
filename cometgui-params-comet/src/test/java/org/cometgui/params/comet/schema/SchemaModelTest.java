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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.cometgui.domain.tools.ToolVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The small value types: what each enum answers, ranges, lookups and immutability. */
class SchemaModelTest {

    private static ToolVersion v(String text) {
        return ToolVersion.parse(text);
    }

    @Test
    @DisplayName("a version range includes both ends, and an open one has no upper end")
    void versionRanges() {
        VersionRange closed = new VersionRange(v("2026.01.0"), Optional.of(v("2026.02.2")));
        assertTrue(closed.contains(v("2026.01.0")));
        assertTrue(closed.contains(v("2026.02.2")));
        assertFalse(closed.contains(v("2025.03.0")));
        assertFalse(closed.contains(v("2026.02.3")));
        VersionRange open = new VersionRange(v("2026.02.2"), Optional.empty());
        assertTrue(open.contains(v("2030.01.0")));
        assertFalse(open.contains(v("2026.02.1")));
        assertTrue(
                new VersionRange(v("2026.02.2"), Optional.of(v("2026.02.2")))
                        .contains(v("2026.02.2")));
        IllegalArgumentException backwards =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new VersionRange(v("2026.02.2"), Optional.of(v("2026.02.1"))));
        assertEquals(
                "a version range must not end (2026.02.1) before it starts (2026.02.2)",
                backwards.getMessage());
    }

    @Test
    @DisplayName("each kind allows exactly the serialisations that make sense for it")
    void kindsAndSerialisations() {
        for (ValueKind kind : ValueKind.values()) {
            Set<SerializationRule> allowed = EnumSet.noneOf(SerializationRule.class);
            for (SerializationRule rule : SerializationRule.values()) {
                if (kind.allows(rule)) {
                    allowed.add(rule);
                }
            }
            Set<SerializationRule> expected =
                    switch (kind) {
                        case STRING, FILE_PATH ->
                                EnumSet.of(
                                        SerializationRule.SINGLE_VALUE,
                                        SerializationRule.EMPTY_ALLOWED);
                        case INTEGER_RANGE, DECIMAL_RANGE ->
                                EnumSet.of(SerializationRule.TWO_VALUES);
                        case DECIMAL_LIST -> EnumSet.of(SerializationRule.VALUE_LIST);
                        case VARIABLE_MOD_TUPLE -> EnumSet.of(SerializationRule.TUPLE);
                        default -> EnumSet.of(SerializationRule.SINGLE_VALUE);
                    };
            assertEquals(expected, allowed, kind.name());
        }
    }

    @Test
    @DisplayName("enumerated, bounded and whole-number kinds are the right ones")
    void kindPredicates() {
        Set<ValueKind> enumerated = EnumSet.noneOf(ValueKind.class);
        Set<ValueKind> bounded = EnumSet.noneOf(ValueKind.class);
        Set<ValueKind> whole = EnumSet.noneOf(ValueKind.class);
        for (ValueKind kind : ValueKind.values()) {
            if (kind.isEnumeration()) {
                enumerated.add(kind);
            }
            if (kind.isBoundedNumeric()) {
                bounded.add(kind);
            }
            if (kind.isWholeNumbers()) {
                whole.add(kind);
            }
        }
        assertEquals(EnumSet.of(ValueKind.INTEGER_ENUM, ValueKind.STRING_ENUM), enumerated);
        assertEquals(
                EnumSet.of(
                        ValueKind.INTEGER,
                        ValueKind.DECIMAL,
                        ValueKind.INTEGER_RANGE,
                        ValueKind.DECIMAL_RANGE,
                        ValueKind.DECIMAL_LIST,
                        ValueKind.TOLERANCE_PAIR_MEMBER),
                bounded);
        assertEquals(EnumSet.of(ValueKind.INTEGER, ValueKind.INTEGER_RANGE), whole);
        Set<SerializationRule> empty = EnumSet.noneOf(SerializationRule.class);
        for (SerializationRule rule : SerializationRule.values()) {
            if (rule.allowsEmpty()) {
                empty.add(rule);
            }
        }
        assertEquals(
                EnumSet.of(SerializationRule.EMPTY_ALLOWED, SerializationRule.VALUE_LIST), empty);
    }

    @Test
    @DisplayName("categories and validators resolve by exact id only")
    void identifiers() {
        assertEquals(14, ParameterCategory.values().length);
        for (ParameterCategory category : ParameterCategory.values()) {
            assertEquals(Optional.of(category), ParameterCategory.fromId(category.id()));
        }
        assertEquals(Optional.empty(), ParameterCategory.fromId("Database_PEFF"));
        assertEquals(
                "Precursor mass and isotope handling",
                ParameterCategory.PRECURSOR_MASS.displayName());
        for (ValidatorId validator : ValidatorId.values()) {
            assertEquals(Optional.of(validator), ValidatorId.fromId(validator.id()));
        }
        assertEquals(Optional.empty(), ValidatorId.fromId("CHOICE"));
        assertEquals("signed_tolerance_pair", ValidatorId.SIGNED_TOLERANCE_PAIR.id());
    }

    @Test
    @DisplayName("metadata lookups find by name, by version and by allow-list")
    void lookups() {
        CuratedMetadata metadata = ConstructedMetadata.valid().load();
        assertTrue(metadata.parameter("isotope_error").isPresent());
        assertEquals(Optional.empty(), metadata.parameter("isotope"));
        assertTrue(metadata.isAllowListed("secret_knob"));
        assertFalse(metadata.isAllowListed("isotope_error"));
        assertEquals(9, metadata.parametersFor(v("2026.02.2")).size());
        assertEquals(List.of(), metadata.parametersFor(v("2026.02.1")));
        assertTrue(metadata.version(v("2026.02.2")).isPresent());
        assertEquals(Optional.empty(), metadata.version(v("2026.02.1")));
    }

    @Test
    @DisplayName("collections handed out cannot change the model")
    void immutability() {
        CuratedMetadata metadata = ConstructedMetadata.valid().load();
        ParameterDefinition isotope = metadata.parameter("isotope_error").orElseThrow();
        assertThrows(UnsupportedOperationException.class, () -> isotope.choices().clear());
        assertThrows(UnsupportedOperationException.class, () -> isotope.validators().clear());
        assertThrows(UnsupportedOperationException.class, () -> isotope.aliases().clear());
        assertThrows(UnsupportedOperationException.class, () -> isotope.related().clear());
        assertThrows(UnsupportedOperationException.class, () -> metadata.parameters().clear());
        assertThrows(UnsupportedOperationException.class, () -> metadata.versions().clear());
        assertThrows(UnsupportedOperationException.class, () -> metadata.internal().clear());
        assertThrows(
                UnsupportedOperationException.class,
                () -> metadata.enzymeTable().senseChoices().clear());
        assertThrows(
                UnsupportedOperationException.class,
                () -> metadata.enzymeTable().referencedBy().clear());
    }

    @Test
    @DisplayName("the metadata diagnostic carries its place and field")
    void invalidMetadataException() {
        InvalidMetadataException failure =
                new InvalidMetadataException("parameter \"x\"", "default", "is wrong");
        assertEquals("parameter \"x\"", failure.where());
        assertEquals("default", failure.field());
        assertEquals(
                "invalid Comet parameter metadata: parameter \"x\", field \"default\": is wrong",
                failure.getMessage());
    }

    @Test
    @DisplayName("a schema failure keeps its kind and an immutable copy of the output")
    void schemaException() {
        List<String> output = new java.util.ArrayList<>(List.of("[stderr] a"));
        CometSchemaException failure =
                new CometSchemaException(
                        CometSchemaException.Failure.NON_ZERO_EXIT, "m", output, null);
        output.add("[stderr] b");
        assertEquals(List.of("[stderr] a"), failure.output());
        assertEquals(CometSchemaException.Failure.NON_ZERO_EXIT, failure.failure());
        assertThrows(UnsupportedOperationException.class, () -> failure.output().clear());
    }
}
