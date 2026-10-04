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
import org.junit.jupiter.api.Test;

/** {@link ParameterOverride} and its place in {@link CometVersionRecord}, as values. */
class ParameterOverrideTest {

    private static final String SOURCE = "https://example.org/source";

    private static final CuratedMetadata CONSTRUCTED = ConstructedMetadata.valid().load();

    private static ParameterDefinition curated(String name) {
        return CONSTRUCTED.parameter(name).orElseThrow();
    }

    @Test
    @DisplayName("an override that replaces nothing is refused")
    void replacingNothingIsRefused() {
        IllegalArgumentException failure =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                new ParameterOverride(
                                        "isotope_error",
                                        SOURCE,
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        false,
                                        Optional.empty()));
        assertTrue(failure.getMessage().contains("replaces no field"), failure.getMessage());
    }

    @Test
    @DisplayName("a comment carried without replacing the comment is refused")
    void aCommentItDoesNotReplaceIsRefused() {
        IllegalArgumentException failure =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                new ParameterOverride(
                                        "isotope_error",
                                        SOURCE,
                                        Optional.of("1"),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        false,
                                        Optional.of("stray")));
        assertTrue(failure.getMessage().contains("does not replace"), failure.getMessage());
    }

    @Test
    @DisplayName("replacing the comment by none alone is an override")
    void removingTheCommentAloneIsAnOverride() {
        ParameterOverride override =
                new ParameterOverride(
                        "isotope_error",
                        SOURCE,
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        true,
                        Optional.empty());
        assertEquals(List.of("inlineComment"), override.replacedFields());
        ParameterDefinition commented =
                new ParameterOverride(
                                "isotope_error",
                                SOURCE,
                                Optional.empty(),
                                Optional.empty(),
                                Optional.empty(),
                                Optional.empty(),
                                true,
                                Optional.of("a comment"))
                        .applyTo(curated("isotope_error"));
        assertEquals(Optional.of("a comment"), commented.inlineComment());
        assertEquals(Optional.empty(), override.applyTo(commented).inlineComment());
    }

    @Test
    @DisplayName("the default alone: every other field is the curated one")
    void aDefaultOnly() {
        ParameterOverride override = ParameterOverride.ofDefault("isotope_error", SOURCE, "1");
        assertEquals(List.of("default"), override.replacedFields());
        ParameterDefinition applied = override.applyTo(curated("isotope_error"));
        assertEquals("1", applied.defaultValue());
        assertEquals(curated("isotope_error").choices(), applied.choices());
        assertEquals(curated("isotope_error").inlineComment(), applied.inlineComment());
        assertEquals(curated("isotope_error").shortHelp(), applied.shortHelp());
        assertEquals(curated("isotope_error").detailedHelpRef(), applied.detailedHelpRef());
    }

    @Test
    @DisplayName("an override applied to another parameter's definition is refused")
    void anotherParametersDefinitionIsRefused() {
        ParameterOverride override = ParameterOverride.ofDefault("isotope_error", SOURCE, "1");
        IllegalArgumentException failure =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> override.applyTo(curated("allowed_missed_cleavage")));
        assertTrue(failure.getMessage().contains("cannot apply"), failure.getMessage());
    }

    @Test
    @DisplayName("the choices are copied, so the caller's list cannot change them")
    void theChoicesAreCopied() {
        List<Choice> choices = new ArrayList<>(List.of(new Choice("0", "a"), new Choice("1", "b")));
        ParameterOverride override =
                new ParameterOverride(
                        "isotope_error",
                        SOURCE,
                        Optional.empty(),
                        Optional.of(choices),
                        Optional.empty(),
                        Optional.empty(),
                        false,
                        Optional.empty());
        choices.add(new Choice("2", "c"));
        assertEquals(2, override.choices().orElseThrow().size());
        assertThrows(
                UnsupportedOperationException.class,
                () -> override.choices().orElseThrow().add(new Choice("3", "d")));
    }

    @Test
    @DisplayName("a version record refuses an override filed under another name")
    void aMisfiledOverrideIsRefused() {
        CometVersionRecord record = CONSTRUCTED.versions().get(0);
        IllegalArgumentException failure =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                new CometVersionRecord(
                                        record.version(),
                                        record.marker(),
                                        record.parameterPages(),
                                        record.source(),
                                        record.variableModTuple(),
                                        Map.of(
                                                "allowed_missed_cleavage",
                                                ParameterOverride.ofDefault(
                                                        "isotope_error", SOURCE, "1"))));
        assertTrue(failure.getMessage().contains("is for isotope_error"), failure.getMessage());
    }

    @Test
    @DisplayName("a record's defaults are the overrides that replace a default, and only those")
    void theDefaultsViewHoldsOnlyDefaults() {
        CometVersionRecord template = CONSTRUCTED.versions().get(0);
        ParameterOverride helpOnly =
                new ParameterOverride(
                        "isotope_error",
                        SOURCE,
                        Optional.empty(),
                        Optional.empty(),
                        Optional.of("help"),
                        Optional.empty(),
                        false,
                        Optional.empty());
        CometVersionRecord record =
                new CometVersionRecord(
                        ToolVersion.parse("2026.03.0"),
                        CometVersionMarker.parse("2026.03 rev. 0"),
                        template.parameterPages(),
                        template.source(),
                        template.variableModTuple(),
                        Map.of(
                                "isotope_error",
                                helpOnly,
                                "allowed_missed_cleavage",
                                ParameterOverride.ofDefault(
                                        "allowed_missed_cleavage", SOURCE, "3")));
        assertEquals(Map.of("allowed_missed_cleavage", "3"), record.defaults());
        assertEquals(Optional.empty(), record.defaultOverride("isotope_error"));
        assertEquals(Optional.of(helpOnly), record.override("isotope_error"));
        assertEquals(Optional.empty(), record.override("num_threads"));
        assertEquals(
                List.of("allowed_missed_cleavage", "isotope_error"),
                List.copyOf(record.overrides().keySet()),
                "ordered by name");
        assertThrows(UnsupportedOperationException.class, () -> record.overrides().clear());
        assertThrows(UnsupportedOperationException.class, () -> record.defaults().clear());
    }
}
