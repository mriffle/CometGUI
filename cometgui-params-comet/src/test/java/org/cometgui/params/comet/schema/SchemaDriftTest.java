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

import java.util.List;
import java.util.Map;
import org.cometgui.domain.tools.ToolVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Each drift rule over a CONSTRUCTED dump and the CONSTRUCTED metadata of {@link
 * ConstructedMetadata}. The real-fixture drift test is {@link SchemaDriftFixtureTest}.
 */
class SchemaDriftTest {

    /**
     * Constructed, in Comet's format: the constructed metadata's nine names plus its allow-listed
     * one.
     */
    static final String DUMP =
            "# comet_version 2026.02 rev. 2 (6edec91)\n"
                    + "search_enzyme_number = 1\n"
                    + "isotope_error = 2\n"
                    + "allowed_missed_cleavage = 2\n"
                    + "peptide_mass_tolerance_lower = -20.0\n"
                    + "peff_obo =\n"
                    + "digest_mass_range = 600.0 5000.0\n"
                    + "mass_offsets =\n"
                    + "use_B_ions = 1\n"
                    + "activation_method = ALL\n"
                    + "secret_knob = 3\n"
                    + "[COMET_ENZYME_INFO]\n"
                    + "0.  Cut_everywhere 0 - -\n";

    private static DriftReport drift(String dump, DiscoveryMode mode, ConstructedMetadata doc) {
        return SchemaDrift.compare(SchemaDiscovery.discover(dump, mode), doc.load());
    }

    private static DriftFinding only(DriftReport report) {
        assertEquals(1, report.findings().size(), report::describe);
        return report.findings().get(0);
    }

    @Test
    @DisplayName("the constructed dump and metadata agree, and the counts add up")
    void agreement() {
        DriftReport report = drift(DUMP, DiscoveryMode.COMPLETE, ConstructedMetadata.valid());
        assertTrue(report.isClean(), report::describe);
        assertEquals(10, report.declared());
        assertEquals(9, report.modelled());
        assertEquals(1, report.allowListed());
        assertEquals(ToolVersion.parse("2026.02.2"), report.version());
        assertEquals(DiscoveryMode.COMPLETE, report.mode());
        assertEquals(
                "Comet 2026.02.2 COMPLETE: declared 10, modelled 9, allow-listed 1, findings 0",
                report.describe());
    }

    @Test
    @DisplayName("a declared parameter with no metadata is UNMODELLED, named, with its line")
    void unmodelled() {
        DriftReport report =
                drift(
                        DUMP.replace("secret_knob = 3\n", "secret_knob = 3\nnew_knob = 7\n"),
                        DiscoveryMode.COMPLETE,
                        ConstructedMetadata.valid());
        DriftFinding finding = only(report);
        assertEquals(DriftFinding.Kind.UNMODELLED, finding.kind());
        assertEquals("new_knob", finding.parameter());
        assertEquals(
                "Comet 2026.02.2 declares new_knob (line 12, default \"7\"), which has no"
                        + " metadata for that version and is not allow-listed",
                finding.message());
        assertEquals(9, report.modelled());
        assertFalse(report.isClean());
        assertTrue(
                report.describe().endsWith("\n  UNMODELLED: " + finding.message()),
                report.describe());
    }

    @Test
    @DisplayName("an unmodelled parameter is reported from a partial dump too")
    void unmodelledFromAPartialDump() {
        DriftReport report =
                drift(
                        DUMP.replace("secret_knob = 3\n", "secret_knob = 3\nnew_knob = 7\n")
                                .replace("isotope_error = 2\n", ""),
                        DiscoveryMode.PARTIAL_DISCOVERY,
                        ConstructedMetadata.valid());
        assertEquals(DriftFinding.Kind.UNMODELLED, only(report).kind());
    }

    @Test
    @DisplayName("metadata that does not claim the dump's version leaves the parameter unmodelled")
    void aDefinitionOutsideItsVersionRange() {
        ConstructedMetadata doc = ConstructedMetadata.valid();
        @SuppressWarnings("unchecked")
        Map<String, Object> range =
                (Map<String, Object>) doc.parameter("use_B_ions").get("versions");
        Map<String, Object> older =
                new java.util.LinkedHashMap<>((Map<String, Object>) doc.list("versions").get(0));
        older.put("version", "2026.02.1");
        older.put("marker", "2026.02 rev. 1");
        doc.list("versions").add(older);
        range.put("from", "2026.02.1");
        range.put("through", "2026.02.1");
        DriftReport report = drift(DUMP, DiscoveryMode.COMPLETE, doc);
        DriftFinding finding = only(report);
        assertEquals(DriftFinding.Kind.UNMODELLED, finding.kind());
        assertEquals("use_B_ions", finding.parameter());
        CometParameterSchema schema =
                new CometParameterSchema(
                        new CometToolIdentity(
                                java.nio.file.Path.of("comet").toAbsolutePath(),
                                ToolVersion.parse("2026.02.2"),
                                java.util.Set.of()),
                        SchemaDiscovery.discover(DUMP, DiscoveryMode.COMPLETE),
                        doc.load(),
                        report);
        List<String> defined =
                schema.definitions().stream().map(ParameterDefinition::name).toList();
        assertEquals(8, defined.size(), defined::toString);
        assertFalse(defined.contains("use_B_ions"), "a definition outside its versions was used");
    }

    @Test
    @DisplayName("a claimed parameter a complete dump lacks is NOT_DECLARED")
    void notDeclared() {
        DriftFinding finding =
                only(
                        drift(
                                DUMP.replace("isotope_error = 2\n", ""),
                                DiscoveryMode.COMPLETE,
                                ConstructedMetadata.valid()));
        assertEquals(DriftFinding.Kind.NOT_DECLARED, finding.kind());
        assertEquals("isotope_error", finding.parameter());
        assertEquals(
                "the metadata claims isotope_error for Comet 2026.02.2, and that binary's"
                        + " complete -q output does not declare it",
                finding.message());
    }

    @Test
    @DisplayName("a stale allow-list entry is NOT_DECLARED too")
    void staleAllowList() {
        DriftFinding finding =
                only(
                        drift(
                                DUMP.replace("secret_knob = 3\n", ""),
                                DiscoveryMode.COMPLETE,
                                ConstructedMetadata.valid()));
        assertEquals(DriftFinding.Kind.NOT_DECLARED, finding.kind());
        assertTrue(finding.message().startsWith("the metadata allow-lists secret_knob"));
    }

    @Test
    @DisplayName("a PARTIAL_DISCOVERY dump never reports a parameter as not declared (R-PARAM-02)")
    void partialNeverReportsRemoval() {
        DriftReport report =
                drift(
                        DUMP.replace("isotope_error = 2\n", "").replace("secret_knob = 3\n", ""),
                        DiscoveryMode.PARTIAL_DISCOVERY,
                        ConstructedMetadata.valid());
        assertTrue(report.isClean(), report::describe);
        assertEquals(8, report.declared());
        assertEquals(DiscoveryMode.PARTIAL_DISCOVERY, report.mode());
    }

    @Test
    @DisplayName("a different default is DEFAULT_DIFFERS, naming both values")
    void defaultDiffers() {
        DriftFinding finding =
                only(
                        drift(
                                DUMP.replace("isotope_error = 2", "isotope_error = 3"),
                                DiscoveryMode.COMPLETE,
                                ConstructedMetadata.valid()));
        assertEquals(DriftFinding.Kind.DEFAULT_DIFFERS, finding.kind());
        assertEquals(
                "the metadata's default for isotope_error is \"2\" and Comet 2026.02.2 writes"
                        + " \"3\" (line 3)",
                finding.message());
    }

    @Test
    @DisplayName("defaults are compared from a partial dump too")
    void defaultDiffersInAPartialDump() {
        assertEquals(
                DriftFinding.Kind.DEFAULT_DIFFERS,
                only(drift(
                                DUMP.replace("= 600.0 5000.0", "= 600.0 6000.0"),
                                DiscoveryMode.PARTIAL_DISCOVERY,
                                ConstructedMetadata.valid()))
                        .kind());
    }

    @Test
    @DisplayName("an uncurated version, or another build's marker, is VERSION_RECORD")
    void versionRecord() {
        DriftFinding unknown =
                only(
                        drift(
                                DUMP.replace("rev. 2 (6edec91)", "rev. 3 (6edec91)"),
                                DiscoveryMode.COMPLETE,
                                ConstructedMetadata.valid()));
        assertEquals(DriftFinding.Kind.VERSION_RECORD, unknown.kind());
        assertEquals("2026.02 rev. 3 (6edec91)", unknown.parameter());
        assertTrue(unknown.message().startsWith("the metadata has no record of Comet 2026.02.3"));
        DriftFinding rebuilt =
                only(
                        drift(
                                DUMP.replace("(6edec91)", "(abcdef0)"),
                                DiscoveryMode.COMPLETE,
                                ConstructedMetadata.valid()));
        assertEquals(DriftFinding.Kind.VERSION_RECORD, rebuilt.kind());
        assertTrue(
                rebuilt.message().contains("records \"2026.02 rev. 2 (6edec91)\""),
                rebuilt.message());
    }

    @Test
    @DisplayName("value texts are equal token by token, numerically where both are numbers")
    void sameValue() {
        assertTrue(SchemaDrift.sameValue("0.0", "0.0000"));
        assertTrue(SchemaDrift.sameValue("0.0 X 0 3", "0 X 0.0 3"));
        assertTrue(SchemaDrift.sameValue("", "  "));
        assertTrue(SchemaDrift.sameValue("DECOY_", "DECOY_"));
        assertFalse(SchemaDrift.sameValue("0.0", "0.01"));
        assertFalse(SchemaDrift.sameValue("0 0", "0"));
        assertFalse(SchemaDrift.sameValue("", "0"));
        assertFalse(SchemaDrift.sameValue("DECOY_", "decoy_"));
        assertFalse(SchemaDrift.sameValue("M", "X"));
    }

    @Test
    @DisplayName("the report's lists are immutable")
    void immutability() {
        DriftReport report = drift(DUMP, DiscoveryMode.COMPLETE, ConstructedMetadata.valid());
        assertThrows(UnsupportedOperationException.class, () -> report.findings().clear());
        assertEquals(List.of(), report.findings());
    }
}
