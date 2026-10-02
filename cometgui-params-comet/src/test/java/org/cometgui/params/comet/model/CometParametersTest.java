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

package org.cometgui.params.comet.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.fixtures.ParamsFiles;
import org.cometgui.params.comet.parser.ParamsLineReader;
import org.cometgui.params.comet.schema.CuratedMetadata;
import org.cometgui.params.comet.schema.ParameterDefinition;
import org.cometgui.params.comet.value.EnzymeTable;
import org.cometgui.params.comet.value.EnzymeTableCodec;
import org.cometgui.params.comet.value.IonSeries;
import org.cometgui.params.comet.value.TolerancePair;
import org.cometgui.params.comet.value.ValueSyntaxException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The model: built from the schema defaults of Comet 2026.02.2 with the REAL {@code -q} enzyme
 * table, changed only by producing new models, and refusing to be built incomplete.
 */
class CometParametersTest {

    private static final CuratedMetadata METADATA = ParamsFiles.metadata();

    private static final EnzymeTable TABLE =
            EnzymeTableCodec.parse(ParamsLineReader.read(ParamsFiles.complete()).enzymeRows());

    private static CometParameters defaults() {
        return CometParameters.defaults(METADATA, ParamsFiles.COMET, TABLE);
    }

    @Nested
    @DisplayName("defaults")
    class Defaults {

        @Test
        @DisplayName("every modelled parameter at its curated default, origin COMET_DEFAULT")
        void everyDefault() {
            CometParameters model = defaults();
            assertEquals(118, model.entries().size());
            for (ParameterEntry entry : model.entries()) {
                assertEquals(ValueOrigin.COMET_DEFAULT, entry.origin(), entry.name());
                assertEquals(entry.definition().defaultValue(), model.text(entry.name()));
            }
            assertEquals(List.of(), model.unknownParameters());
            assertEquals(List.of(), model.diagnostics());
            assertEquals(TABLE, model.enzymeTable());
            assertEquals(METADATA, model.metadata());
            assertEquals(ParamsFiles.COMET, model.version());
            assertEquals(
                    METADATA.parametersFor(ParamsFiles.COMET).stream()
                            .map(ParameterDefinition::name)
                            .toList(),
                    model.entries().stream().map(ParameterEntry::name).toList());
        }

        @Test
        @DisplayName("the structured views read the members")
        void views() {
            CometParameters model = defaults();
            assertEquals(
                    new TolerancePair(new BigDecimal("-20.0"), new BigDecimal("20.0")),
                    model.tolerancePair());
            assertEquals(Set.of(IonSeries.B, IonSeries.Y), model.ionSeries().series());
            CometParameters nl =
                    model.withValue("use_NL_ions", new ParameterValue.Flag(true), ValueOrigin.USER)
                            .withValue(
                                    "use_A_ions", new ParameterValue.Flag(true), ValueOrigin.USER);
            assertTrue(nl.ionSeries().neutralLossPeaks());
            assertEquals(Set.of(IonSeries.A, IonSeries.B, IonSeries.Y), nl.ionSeries().series());
        }

        @Test
        @DisplayName("a version the metadata was not curated against is refused")
        void uncurated() {
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            CometParameters.defaults(
                                    METADATA, ToolVersion.parse("2019.01.5"), TABLE));
        }
    }

    @Nested
    @DisplayName("lookup")
    class Lookup {

        @Test
        @DisplayName("by name: value, origin, definition, text; empty or refused when unknown")
        void byName() {
            CometParameters model = defaults();
            assertEquals(new ParameterValue.Whole(2), model.value("allowed_missed_cleavage"));
            assertEquals(ValueOrigin.COMET_DEFAULT, model.origin("allowed_missed_cleavage"));
            assertEquals(
                    METADATA.parameter("allowed_missed_cleavage").orElseThrow(),
                    model.definition("allowed_missed_cleavage"));
            assertEquals("15.9949 M 0 3 -1 0 0 0.0", model.text("variable_mod01"));
            assertEquals(Optional.empty(), model.entry("ms1_mass_range"));
            IllegalArgumentException failure =
                    assertThrows(
                            IllegalArgumentException.class, () -> model.value("ms1_mass_range"));
            assertEquals(
                    "ms1_mass_range is not a parameter modelled for Comet 2026.02.2",
                    failure.getMessage());
            assertThrows(IllegalArgumentException.class, () -> model.origin("nope"));
            assertThrows(IllegalArgumentException.class, () -> model.definition("nope"));
            assertThrows(IllegalArgumentException.class, () -> model.text("nope"));
        }
    }

    @Nested
    @DisplayName("changes produce new models")
    class Changes {

        @Test
        @DisplayName("withValue changes one entry and leaves the original alone")
        void withValue() {
            CometParameters original = defaults();
            CometParameters changed =
                    original.withValue(
                            "num_threads", new ParameterValue.Whole(16), ValueOrigin.USER);
            assertEquals(new ParameterValue.Whole(16), changed.value("num_threads"));
            assertEquals(ValueOrigin.USER, changed.origin("num_threads"));
            assertEquals(new ParameterValue.Whole(0), original.value("num_threads"));
            assertEquals(ValueOrigin.COMET_DEFAULT, original.origin("num_threads"));
            assertNotEquals(original, changed);
            assertEquals(original.entries().size(), changed.entries().size());
            assertEquals(
                    original.entries().stream().map(ParameterEntry::name).toList(),
                    changed.entries().stream().map(ParameterEntry::name).toList());
        }

        @Test
        @DisplayName("withText reads the text as the parameter's kind")
        void withText() {
            CometParameters changed =
                    defaults().withText("fragment_bin_tol", "1.0005", ValueOrigin.PRESET);
            assertEquals(
                    new ParameterValue.Decimal(new BigDecimal("1.0005")),
                    changed.value("fragment_bin_tol"));
            assertEquals(ValueOrigin.PRESET, changed.origin("fragment_bin_tol"));
            assertThrows(
                    ValueSyntaxException.class,
                    () ->
                            assertEquals(
                                    null,
                                    defaults()
                                            .withText(
                                                    "fragment_bin_tol", "0,02", ValueOrigin.USER)));
        }

        @Test
        @DisplayName("withOrigin keeps the value: the workflow marks what it enforces (R-CMT-01)")
        void withOrigin() {
            CometParameters enforced =
                    defaults()
                            .withValue(
                                    "output_percolatorfile",
                                    new ParameterValue.Flag(true),
                                    ValueOrigin.WORKFLOW_ENFORCED)
                            .withOrigin("output_pepxmlfile", ValueOrigin.WORKFLOW_ENFORCED);
            assertEquals(ValueOrigin.WORKFLOW_ENFORCED, enforced.origin("output_percolatorfile"));
            assertEquals(new ParameterValue.Flag(true), enforced.value("output_percolatorfile"));
            assertEquals(ValueOrigin.WORKFLOW_ENFORCED, enforced.origin("output_pepxmlfile"));
            assertEquals(new ParameterValue.Flag(true), enforced.value("output_pepxmlfile"));
        }

        @Test
        @DisplayName("resetToDefault restores the schema default and COMET_DEFAULT")
        void reset() {
            CometParameters changed = defaults().withText("decoy_prefix", "REV_", ValueOrigin.USER);
            CometParameters reset = changed.resetToDefault("decoy_prefix");
            assertEquals(new ParameterValue.Text("DECOY_"), reset.value("decoy_prefix"));
            assertEquals(ValueOrigin.COMET_DEFAULT, reset.origin("decoy_prefix"));
            assertEquals(defaults(), reset);
            assertEquals(new ParameterValue.Text("REV_"), changed.value("decoy_prefix"));
        }

        @Test
        @DisplayName("a value of the wrong variant is refused, naming parameter and kind")
        void wrongVariant() {
            IllegalArgumentException failure =
                    assertThrows(
                            IllegalArgumentException.class,
                            () ->
                                    assertEquals(
                                            null,
                                            defaults()
                                                    .withValue(
                                                            "num_threads",
                                                            new ParameterValue.Text("8"),
                                                            ValueOrigin.USER)));
            assertEquals(
                    "num_threads is of kind INTEGER and cannot hold a Text value",
                    failure.getMessage());
        }

        @Test
        @DisplayName("withEnzymeTable and withoutUnknown")
        void tableAndUnknown() {
            CometParameters model = defaults().withEnzymeTable(TABLE.without(11));
            assertEquals(11, model.enzymeTable().rows().size());
            assertEquals(12, defaults().enzymeTable().rows().size());
            CometParameters withUnknown =
                    CometParameters.of(
                            METADATA,
                            ParamsFiles.COMET,
                            defaults().entries(),
                            TABLE,
                            List.of(
                                    new UnknownParameter(
                                            "ms1_mass_range",
                                            "1 2",
                                            Optional.empty(),
                                            List.of(),
                                            9),
                                    new UnknownParameter(
                                            "precursor_NL_ions",
                                            "",
                                            Optional.empty(),
                                            List.of(),
                                            10)),
                            List.of());
            CometParameters removed = withUnknown.withoutUnknown("ms1_mass_range");
            assertEquals(
                    List.of("precursor_NL_ions"),
                    removed.unknownParameters().stream().map(UnknownParameter::name).toList());
            assertEquals(2, withUnknown.unknownParameters().size());
            IllegalArgumentException failure =
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> assertEquals(null, removed.withoutUnknown("ms1_mass_range")));
            assertEquals(
                    "there is no unknown parameter named ms1_mass_range", failure.getMessage());
        }
    }

    @Nested
    @DisplayName("of() refuses an incomplete or inconsistent model")
    class Construction {

        private List<ParameterEntry> entries() {
            return new ArrayList<>(defaults().entries());
        }

        private IllegalArgumentException refused(
                List<ParameterEntry> entries,
                List<UnknownParameter> unknowns,
                List<Diagnostic> diagnostics) {
            return assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            CometParameters.of(
                                    METADATA,
                                    ParamsFiles.COMET,
                                    entries,
                                    TABLE,
                                    unknowns,
                                    diagnostics));
        }

        @Test
        @DisplayName("a missing parameter")
        void missing() {
            List<ParameterEntry> entries = entries();
            entries.remove(5);
            assertTrue(
                    refused(entries, List.of(), List.of())
                            .getMessage()
                            .contains("is modelled for Comet 2026.02.2 and has no value"));
        }

        @Test
        @DisplayName("a parameter given twice")
        void twice() {
            List<ParameterEntry> entries = entries();
            entries.add(entries.get(3));
            assertTrue(
                    refused(entries, List.of(), List.of())
                            .getMessage()
                            .endsWith(" is given twice"));
        }

        @Test
        @DisplayName("an entry for a parameter the version does not model")
        void foreign() {
            ParameterDefinition real = METADATA.parameter("num_threads").orElseThrow();
            ParameterDefinition renamed =
                    new ParameterDefinition(
                            "num_threads_extra",
                            real.displayName(),
                            real.category(),
                            real.kind(),
                            real.visibility(),
                            real.defaultValue(),
                            real.minimum(),
                            real.maximum(),
                            real.choices(),
                            real.shortHelp(),
                            real.inlineComment(),
                            real.detailedHelpRef(),
                            real.supportedVersions(),
                            real.serialization(),
                            real.validators(),
                            real.aliases(),
                            real.related());
            List<ParameterEntry> entries = entries();
            entries.add(
                    new ParameterEntry(
                            renamed, new ParameterValue.Whole(1), ValueOrigin.COMET_DEFAULT));
            assertTrue(
                    refused(entries, List.of(), List.of())
                            .getMessage()
                            .contains("[num_threads_extra] are not modelled"));
        }

        @Test
        @DisplayName("an entry whose definition is not the metadata's")
        void otherDefinition() {
            ParameterDefinition real = METADATA.parameter("num_threads").orElseThrow();
            ParameterDefinition changed =
                    new ParameterDefinition(
                            real.name(),
                            "Another name",
                            real.category(),
                            real.kind(),
                            real.visibility(),
                            real.defaultValue(),
                            real.minimum(),
                            real.maximum(),
                            real.choices(),
                            real.shortHelp(),
                            real.inlineComment(),
                            real.detailedHelpRef(),
                            real.supportedVersions(),
                            real.serialization(),
                            real.validators(),
                            real.aliases(),
                            real.related());
            List<ParameterEntry> entries = entries();
            entries.removeIf(e -> e.name().equals("num_threads"));
            entries.add(
                    new ParameterEntry(
                            changed, new ParameterValue.Whole(1), ValueOrigin.COMET_DEFAULT));
            assertTrue(
                    refused(entries, List.of(), List.of())
                            .getMessage()
                            .contains("carries a definition that is not the metadata's"));
        }

        @Test
        @DisplayName("an unknown parameter that is modelled, or named twice")
        void badUnknowns() {
            UnknownParameter modelled =
                    new UnknownParameter("num_threads", "4", Optional.empty(), List.of(), 3);
            assertTrue(
                    refused(entries(), List.of(modelled), List.of())
                            .getMessage()
                            .contains("cannot also be unknown"));
            UnknownParameter knob =
                    new UnknownParameter("knob", "4", Optional.empty(), List.of(), 3);
            assertTrue(
                    refused(entries(), List.of(knob, knob), List.of())
                            .getMessage()
                            .contains("the unknown parameter knob is given twice"));
        }

        @Test
        @DisplayName("an error diagnostic: a parse with errors has no model")
        void error() {
            Diagnostic error =
                    Diagnostic.of(Diagnostic.Code.MALFORMED_LINE, List.of(3), null, "bad line");
            assertTrue(
                    refused(entries(), List.of(), List.of(error))
                            .getMessage()
                            .contains("a model cannot carry an error"));
            Diagnostic warning =
                    Diagnostic.of(Diagnostic.Code.UNKNOWN_PARAMETER, List.of(3), "k", "unknown");
            assertEquals(
                    List.of(warning),
                    CometParameters.of(
                                    METADATA,
                                    ParamsFiles.COMET,
                                    entries(),
                                    TABLE,
                                    List.of(),
                                    List.of(warning))
                            .diagnostics());
        }
    }

    @Test
    @DisplayName("equality covers entries, table, unknowns and diagnostics; toString summarises")
    void equality() {
        CometParameters model = defaults();
        assertEquals(model, defaults());
        assertEquals(model.hashCode(), defaults().hashCode());
        assertNotEquals(model, model.withEnzymeTable(TABLE.without(11)));
        assertNotEquals(model, model.withOrigin("num_threads", ValueOrigin.USER));
        assertNotEquals(
                model.hashCode(), model.withOrigin("num_threads", ValueOrigin.USER).hashCode());
        Diagnostic warning =
                Diagnostic.of(Diagnostic.Code.VERSION_MARKER_MISSING, List.of(), null, "none");
        CometParameters warned =
                CometParameters.of(
                        METADATA,
                        ParamsFiles.COMET,
                        model.entries(),
                        TABLE,
                        List.of(),
                        List.of(warning));
        assertNotEquals(model, warned);
        CometParameters unknown =
                CometParameters.of(
                        METADATA,
                        ParamsFiles.COMET,
                        model.entries(),
                        TABLE,
                        List.of(new UnknownParameter("k", "1", Optional.empty(), List.of(), 1)),
                        List.of());
        assertNotEquals(model, unknown);
        assertNotEquals(model, "a string");
        assertEquals(
                "CometParameters[Comet 2026.02.2, 118 parameters, 12 enzymes, 0 unknown,"
                        + " 0 diagnostics]",
                model.toString());
    }
}
