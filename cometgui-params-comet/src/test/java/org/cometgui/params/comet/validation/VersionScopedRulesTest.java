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

package org.cometgui.params.comet.validation;

import static org.cometgui.params.comet.validation.Models.COMET;
import static org.cometgui.params.comet.validation.Models.COMET_2026_03_0;
import static org.cometgui.params.comet.validation.Models.METADATA;
import static org.cometgui.params.comet.validation.Models.assertAttached;
import static org.cometgui.params.comet.validation.Models.validate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.schema.CometVersionRecord;
import org.cometgui.params.comet.schema.CuratedMetadata;
import org.cometgui.params.comet.schema.ParameterCategory;
import org.cometgui.params.comet.schema.RuleSeverity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The version-scoped rules (decision C-2 of the Comet 2026.03.0 intake): each takes its severity
 * from the version record of the release its model carries. The tuples and values are CONSTRUCTED
 * test input on each release's real {@code -q} model; each verdict is one the real binaries gave
 * ({@code ValidationCorpusRealBinaryTest} replays them).
 */
class VersionScopedRulesTest {

    private static final ToolVersion COMET_2024_01_0 = ToolVersion.parse("2024.01.0");

    private static List<Finding> of(Rule rule, CometParameters model) {
        return validate(model).of(rule);
    }

    @Nested
    @DisplayName("variable_mod_tuple.distance_undocumented")
    class Distance {

        private static final String BELOW = "15.9949 M 0 3 -3 0 0 0.0";

        @Test
        @DisplayName("below -2: an error for 2026.03.0, a warning for 2026.02.2 and 2024.01.0")
        void severityByRelease() {
            Finding newer =
                    Models.only(
                            new ValidationReport(
                                    of(
                                            Rule.VARMOD_DISTANCE_UNDOCUMENTED,
                                            Models.with(
                                                    COMET_2026_03_0, "variable_mod01", BELOW))));
            assertAttached(
                    newer,
                    Rule.VARMOD_DISTANCE_UNDOCUMENTED,
                    ParameterCategory.VARIABLE_MODS,
                    "variable_mod01");
            assertEquals(Severity.ERROR, newer.severity());
            Finding older =
                    Models.only(
                            new ValidationReport(
                                    of(
                                            Rule.VARMOD_DISTANCE_UNDOCUMENTED,
                                            Models.with(COMET, "variable_mod01", BELOW))));
            assertEquals(Severity.WARNING, older.severity());
            assertEquals(newer.message(), older.message());
            CometParameters oldest =
                    CometParameters.defaults(METADATA, COMET_2024_01_0, Models.real().enzymeTable())
                            .withText(
                                    "variable_mod01",
                                    "15.9949 M 0 3 -3 0 0 0.0",
                                    org.cometgui.params.comet.model.ValueOrigin.USER);
            List<Finding> oldestFound = of(Rule.VARMOD_DISTANCE_UNDOCUMENTED, oldest);
            assertEquals(1, oldestFound.size(), oldestFound::toString);
            assertEquals(Severity.WARNING, oldestFound.get(0).severity());
        }

        @Test
        @DisplayName("-2, -1 and 0 up are clean in both releases; an unused slot is never judged")
        void documentedValues() {
            for (ToolVersion version : List.of(COMET, COMET_2026_03_0)) {
                for (String tuple :
                        List.of(
                                "15.9949 M 0 3 -2 0 0 0.0",
                                "15.9949 M 0 3 -1 0 0 0.0",
                                "15.9949 M 0 3 0 0 0 0.0",
                                "15.9949 M 0 3 9 3 0 0.0")) {
                    assertEquals(
                            List.of(),
                            validate(Models.with(version, "variable_mod01", tuple)).findings(),
                            version + " " + tuple);
                }
                assertEquals(
                        List.of(),
                        validate(Models.with(version, "variable_mod02", "0.0 X 0 3 -3 0 0 0.0"))
                                .findings(),
                        version.text());
            }
        }

        @Test
        @DisplayName("the 2026.03.0 report has errors where the 2026.02.2 report does not")
        void blocksTheRunForTheNewerReleaseOnly() {
            assertEquals(
                    true,
                    validate(Models.with(COMET_2026_03_0, "variable_mod01", BELOW)).hasErrors());
            assertEquals(false, validate(Models.with(COMET, "variable_mod01", BELOW)).hasErrors());
        }
    }

    @Nested
    @DisplayName("index_search_type.ignored_without_idx")
    class IndexSearchType {

        private static final String FASTA = "/data/human.fasta";

        private CometParameters newer(String type, String database) {
            return Models.with(
                    COMET_2026_03_0, "index_search_type", type, "database_name", database);
        }

        @Test
        @DisplayName("0 or 1 with a database that is not an .idx: a warning for 2026.03.0")
        void warnsForTheNewerRelease() {
            for (String type : List.of("0", "1")) {
                Finding finding =
                        Models.only(
                                new ValidationReport(
                                        of(Rule.INDEX_SEARCH_TYPE_IGNORED, newer(type, FASTA))));
                assertAttached(
                        finding,
                        Rule.INDEX_SEARCH_TYPE_IGNORED,
                        ParameterCategory.FRAGMENT_INDEX,
                        "index_search_type",
                        "database_name");
                assertEquals(Severity.WARNING, finding.severity());
                assertEquals(
                        "index_search_type = "
                                + type
                                + " only chooses the type of an index Comet builds when"
                                + " database_name names an .idx file that does not exist yet, and"
                                + " database_name = /data/human.fasta does not, so the value has no"
                                + " effect; set index_search_type to -1 (not set), or name the .idx"
                                + " file to build",
                        finding.message());
                assertEquals(List.of(finding), validate(newer(type, FASTA)).findings());
            }
        }

        @Test
        @DisplayName("-1, an .idx name, or another value are not this rule's")
        void notThisRule() {
            assertEquals(List.of(), validate(newer("-1", FASTA)).findings());
            assertEquals(List.of(), validate(newer("1", "/data/human.fasta.idx")).findings());
            assertEquals(List.of(), validate(newer("0", ".idx")).findings());
            assertEquals(
                    1, of(Rule.INDEX_SEARCH_TYPE_IGNORED, newer("1", "/data/HUMAN.IDX")).size());
            assertEquals(
                    1,
                    of(Rule.INDEX_SEARCH_TYPE_IGNORED, newer("1", "/data/human.idx.fasta")).size());
            List<Finding> outOfRange = validate(newer("99", FASTA)).findings();
            assertEquals(1, outOfRange.size(), outOfRange::toString);
            assertEquals(Rule.CHOICE_NOT_LISTED, outOfRange.get(0).rule());
        }

        @Test
        @DisplayName("2026.02.2 is silent about it, so the rule is off there")
        void silentForTheOlderRelease() {
            CometParameters older =
                    Models.with(COMET, "index_search_type", "1", "database_name", FASTA);
            assertEquals(List.of(), validate(older).findings());
            assertEquals(
                    List.of(),
                    validate(Models.with(COMET, "index_search_type", "0", "database_name", FASTA))
                            .findings());
        }
    }

    @Nested
    @DisplayName("the version records")
    class Records {

        private CometParameters withRecord(ToolVersion version, Map<String, RuleSeverity> stated) {
            List<CometVersionRecord> records = new ArrayList<>();
            for (CometVersionRecord record : METADATA.versions()) {
                records.add(
                        record.version().equals(version)
                                ? record.withRuleSeverities(stated)
                                : record);
            }
            CuratedMetadata changed =
                    new CuratedMetadata(
                            METADATA.schemaVersion(),
                            records,
                            METADATA.parameters(),
                            METADATA.internal(),
                            METADATA.enzymeTable());
            return CometParameters.defaults(changed, version, Models.real().enzymeTable())
                    .withWorkflowEnforcedOutputs();
        }

        private Map<String, RuleSeverity> bundled(ToolVersion version) {
            return new LinkedHashMap<>(METADATA.version(version).orElseThrow().ruleSeverities());
        }

        @Test
        @DisplayName("every bundled release states every version-scoped rule, and nothing else")
        void bundledRecords() {
            for (CometVersionRecord record : METADATA.versions()) {
                List<String> scoped = new ArrayList<>();
                for (Rule rule : Rule.values()) {
                    if (rule.isVersionScoped()) {
                        scoped.add(rule.id());
                    }
                }
                assertEquals(
                        scoped.stream().sorted().toList(),
                        List.copyOf(record.ruleSeverities().keySet()),
                        record.version().text());
                VersionSeverities.of(record);
            }
            assertEquals(
                    Optional.of(RuleSeverity.Level.ERROR),
                    level(COMET_2026_03_0, Rule.VARMOD_DISTANCE_UNDOCUMENTED));
            assertEquals(
                    Optional.of(RuleSeverity.Level.WARNING),
                    level(COMET, Rule.VARMOD_DISTANCE_UNDOCUMENTED));
            assertEquals(
                    Optional.of(RuleSeverity.Level.WARNING),
                    level(COMET_2024_01_0, Rule.VARMOD_DISTANCE_UNDOCUMENTED));
            assertEquals(
                    Optional.of(RuleSeverity.Level.WARNING),
                    level(COMET_2026_03_0, Rule.INDEX_SEARCH_TYPE_IGNORED));
            assertEquals(
                    Optional.of(RuleSeverity.Level.OFF),
                    level(COMET, Rule.INDEX_SEARCH_TYPE_IGNORED));
            assertEquals(
                    Optional.of(RuleSeverity.Level.OFF),
                    level(COMET_2024_01_0, Rule.INDEX_SEARCH_TYPE_IGNORED));
        }

        private Optional<RuleSeverity.Level> level(ToolVersion version, Rule rule) {
            return METADATA.version(version)
                    .orElseThrow()
                    .ruleSeverity(rule.id())
                    .map(RuleSeverity::level);
        }

        @Test
        @DisplayName("the severity comes from the record, whatever it says")
        void theRecordDecides() {
            Map<String, RuleSeverity> stated = bundled(COMET);
            stated.put(
                    Rule.VARMOD_DISTANCE_UNDOCUMENTED.id(),
                    new RuleSeverity(
                            Rule.VARMOD_DISTANCE_UNDOCUMENTED.id(),
                            RuleSeverity.Level.OFF,
                            "https://example.org/constructed"));
            CometParameters off =
                    withRecord(COMET, stated)
                            .withText(
                                    "variable_mod01",
                                    "15.9949 M 0 3 -3 0 0 0.0",
                                    org.cometgui.params.comet.model.ValueOrigin.USER);
            assertEquals(List.of(), validate(off).findings());
            stated.put(
                    Rule.INDEX_SEARCH_TYPE_IGNORED.id(),
                    new RuleSeverity(
                            Rule.INDEX_SEARCH_TYPE_IGNORED.id(),
                            RuleSeverity.Level.ERROR,
                            "https://example.org/constructed"));
            CometParameters error =
                    withRecord(COMET, stated)
                            .withText(
                                    "database_name",
                                    "/data/human.fasta",
                                    org.cometgui.params.comet.model.ValueOrigin.USER);
            List<Finding> found = of(Rule.INDEX_SEARCH_TYPE_IGNORED, error);
            assertEquals(1, found.size(), found::toString);
            assertEquals(Severity.ERROR, found.get(0).severity());
        }

        @Test
        @DisplayName("a record that leaves a version-scoped rule unstated is refused")
        void unstated() {
            Map<String, RuleSeverity> stated = bundled(COMET);
            stated.remove(Rule.INDEX_SEARCH_TYPE_IGNORED.id());
            CometParameters model = withRecord(COMET, stated);
            IllegalStateException refused =
                    assertThrows(IllegalStateException.class, () -> validate(model));
            assertEquals(
                    "Comet 2026.02.2's version record states no severity for the version-scoped"
                            + " rule index_search_type.ignored_without_idx; every release must"
                            + " state one",
                    refused.getMessage());
        }

        @Test
        @DisplayName("a record that states a rule that does not exist, or a fixed one, is refused")
        void misstated() {
            Map<String, RuleSeverity> unknown = bundled(COMET);
            unknown.put(
                    "variable_mod_tuple.no_such_rule",
                    new RuleSeverity(
                            "variable_mod_tuple.no_such_rule",
                            RuleSeverity.Level.WARNING,
                            "https://example.org/constructed"));
            CometParameters model = withRecord(COMET, unknown);
            assertEquals(
                    "Comet 2026.02.2's version record states a severity for"
                            + " \"variable_mod_tuple.no_such_rule\", which is not a rule",
                    assertThrows(IllegalStateException.class, () -> validate(model)).getMessage());
            Map<String, RuleSeverity> fixed = bundled(COMET);
            fixed.put(
                    Rule.VARMOD_TERMINUS_UNDOCUMENTED.id(),
                    new RuleSeverity(
                            Rule.VARMOD_TERMINUS_UNDOCUMENTED.id(),
                            RuleSeverity.Level.WARNING,
                            "https://example.org/constructed"));
            CometParameters fixedModel = withRecord(COMET, fixed);
            assertEquals(
                    "Comet 2026.02.2's version record states a severity for"
                            + " variable_mod_tuple.terminus_undocumented, whose severity is fixed"
                            + " (ERROR) in every release",
                    assertThrows(IllegalStateException.class, () -> validate(fixedModel))
                            .getMessage());
        }
    }
}
