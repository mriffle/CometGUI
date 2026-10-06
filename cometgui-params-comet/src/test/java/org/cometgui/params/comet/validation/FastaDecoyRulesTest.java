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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.cometgui.domain.params.FastaDecoyCensus;
import org.cometgui.domain.params.PreRunFacts;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.schema.ParameterCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * {@code R-DEC-02}'s two blocks in the one validator (Phase 08 gate items 4 and 5, the rule half):
 * "no decoys anywhere" and "double decoys", judged on the decoy census of the REAL {@code D-006}
 * subset and of the target-decoy FASTA built from it ({@link IndexDescriptions}), each asserted on
 * rule, severity, parameters, category and the whole, hand-typed message.
 */
class FastaDecoyRulesTest {

    private static final ToolVersion NEWER = ToolVersion.parse("2026.03.0");

    private static final ToolVersion OLDER = ToolVersion.parse("2026.02.2");

    private static ValidationReport validate(CometParameters model, FastaDecoyCensus census) {
        return CometValidator.standard().validate(model, PreRunFacts.none().withCensus(census));
    }

    /** The findings of the decoy rules alone. */
    private static List<Finding> decoyFindings(ValidationReport report) {
        List<Finding> found = new ArrayList<>(report.of(Rule.DECOY_NONE_ANYWHERE));
        found.addAll(report.of(Rule.DECOY_DOUBLE));
        return found;
    }

    @ParameterizedTest(name = "Comet {0}")
    @ValueSource(strings = {"2026.03.0", "2026.02.2"})
    @DisplayName("gate item 4: a FASTA with no decoys and decoy_search = 0 is an error naming it")
    void noDecoysAnywhere(String release) {
        CometParameters model = Models.with(ToolVersion.parse(release), "decoy_search", "0");
        ValidationReport report = validate(model, IndexDescriptions.subsetCensus());
        Finding finding = Models.only(new ValidationReport(decoyFindings(report)));
        Models.assertAttached(
                finding,
                Rule.DECOY_NONE_ANYWHERE,
                ParameterCategory.DATABASE_PEFF,
                "decoy_search",
                "database_name");
        assertEquals(Severity.ERROR, finding.severity());
        assertEquals(
                "decoy_search = 0 (no internal decoys) and /data/subset.fasta holds no entry whose"
                        + " accession begins with DECOY_ (0 of 1000 records): Percolator would have"
                        + " no negative examples; set decoy_search to 1 or 2 so that Comet makes"
                        + " decoys, or choose a FASTA whose decoys begin with DECOY_",
                finding.message());
        assertTrue(report.hasErrors());
        assertEquals(List.of(finding), report.forParameter("database_name"));
    }

    @Test
    @DisplayName("gate item 5: a FASTA holding decoys and decoy_search = 1 is an error naming it")
    void doubleDecoysConcatenated() {
        ValidationReport report =
                validate(
                        Models.with(NEWER, "decoy_search", "1"),
                        IndexDescriptions.targetDecoyCensus());
        Finding finding = Models.only(new ValidationReport(decoyFindings(report)));
        Models.assertAttached(
                finding,
                Rule.DECOY_DOUBLE,
                ParameterCategory.DATABASE_PEFF,
                "decoy_search",
                "database_name");
        assertEquals(Severity.ERROR, finding.severity());
        assertEquals(
                "decoy_search = 1 (Comet's internal decoys, concatenated) and"
                        + " /data/target-decoy.fasta already holds 1000 entries whose accession"
                        + " begins with DECOY_ (1000 of 2000 records; the first is"
                        + " DECOY_sp|A0A075B6H9|LV469_HUMAN): Comet would make decoys of those"
                        + " decoys too, so decoys would be counted twice; set decoy_search to 0 to"
                        + " use the FASTA's own decoys, or choose a FASTA of targets only",
                finding.message());
        assertTrue(report.hasErrors());
    }

    @Test
    @DisplayName("gate item 5: a FASTA holding decoys and decoy_search = 2 is an error naming it")
    void doubleDecoysSeparate() {
        ValidationReport report =
                validate(
                        Models.with(OLDER, "decoy_search", "2"),
                        IndexDescriptions.targetDecoyCensus());
        Finding finding = Models.only(new ValidationReport(decoyFindings(report)));
        Models.assertAttached(
                finding,
                Rule.DECOY_DOUBLE,
                ParameterCategory.DATABASE_PEFF,
                "decoy_search",
                "database_name");
        assertEquals(
                "decoy_search = 2 (Comet's internal decoys, reported separately) and"
                        + " /data/target-decoy.fasta already holds 1000 entries whose accession"
                        + " begins with DECOY_ (1000 of 2000 records; the first is"
                        + " DECOY_sp|A0A075B6H9|LV469_HUMAN): Comet would make decoys of those"
                        + " decoys too, so decoys would be counted twice; set decoy_search to 0 to"
                        + " use the FASTA's own decoys, or choose a FASTA of targets only",
                finding.message());
    }

    @Test
    @DisplayName("one decoy is named in the singular")
    void oneDecoy() {
        FastaDecoyCensus census =
                new FastaDecoyCensus(
                        Path.of("/d/one.fasta"), "DECOY_", 3, 1, Optional.of("DECOY_x"));
        Finding finding =
                Models.only(
                        new ValidationReport(
                                decoyFindings(
                                        validate(
                                                Models.with(NEWER, "decoy_search", "1"), census))));
        assertEquals(
                "decoy_search = 1 (Comet's internal decoys, concatenated) and /d/one.fasta already"
                        + " holds 1 entry whose accession begins with DECOY_ (1 of 3 records; the"
                        + " first is DECOY_x): Comet would make decoys of those decoys too, so"
                        + " decoys would be counted twice; set decoy_search to 0 to use the"
                        + " FASTA's own decoys, or choose a FASTA of targets only",
                finding.message());
    }

    @Test
    @DisplayName("the two good combinations draw no decoy finding, and add nothing to the report")
    void goodCombinations() {
        for (ToolVersion release : List.of(NEWER, OLDER)) {
            CometParameters fastaDecoys = Models.with(release, "decoy_search", "0");
            assertEquals(
                    Models.validate(fastaDecoys),
                    validate(fastaDecoys, IndexDescriptions.targetDecoyCensus()),
                    release.text());
            for (String internal : List.of("1", "2")) {
                CometParameters model = Models.with(release, "decoy_search", internal);
                assertEquals(
                        Models.validate(model),
                        validate(model, IndexDescriptions.subsetCensus()),
                        release.text() + " decoy_search = " + internal);
            }
        }
        assertFalse(
                validate(Models.with(NEWER, "decoy_search", "1"), IndexDescriptions.subsetCensus())
                        .hasErrors());
    }

    @Test
    @DisplayName("the decoy findings come after the model's own, before what the import left")
    void reportOrder() {
        CometParameters model =
                Models.with(NEWER, "decoy_search", "0", "peptide_mass_tolerance_lower", "-10.0");
        List<Finding> alone = Models.validate(model).findings();
        List<Finding> withCensus = validate(model, IndexDescriptions.subsetCensus()).findings();
        assertEquals(alone.size() + 1, withCensus.size());
        assertEquals(alone, withCensus.subList(0, alone.size()));
        assertEquals(Rule.DECOY_NONE_ANYWHERE, withCensus.get(alone.size()).rule());
    }

    @Test
    @DisplayName("no census, no decoy finding; validate(model) is validate(model, none)")
    void noCensus() {
        CometParameters model = Models.with(NEWER, "decoy_search", "0");
        assertEquals(
                Models.validate(model),
                CometValidator.standard().validate(model, PreRunFacts.none()));
        assertEquals(List.of(), decoyFindings(Models.validate(model)));
    }

    @Test
    @DisplayName("an undocumented decoy_search is the choice rule's error, not a decoy rule's")
    void undocumentedDecoySearch() {
        ValidationReport report =
                validate(Models.with(NEWER, "decoy_search", "3"), IndexDescriptions.subsetCensus());
        assertEquals(List.of(), decoyFindings(report));
        assertEquals(1, report.of(Rule.CHOICE_NOT_LISTED).size());
    }

    @Test
    @DisplayName("a census taken for another prefix is refused, never used")
    void otherPrefix() {
        FastaDecoyCensus reversed =
                new FastaDecoyCensus(IndexDescriptions.SUBSET, "REV_", 1000, 0, Optional.empty());
        assertEquals(
                "the decoy census of /data/subset.fasta counted accessions beginning with REV_, but"
                        + " decoy_prefix = DECOY_; scan the FASTA again for decoy_prefix before"
                        + " validating",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> validate(Models.with(NEWER, "decoy_search", "0"), reversed))
                        .getMessage());
        CometParameters rev = Models.with(NEWER, "decoy_search", "0", "decoy_prefix", "REV_");
        Finding finding = Models.only(new ValidationReport(decoyFindings(validate(rev, reversed))));
        assertTrue(finding.message().contains("begins with REV_ (0 of 1000 records)"));
        assertThrows(
                IllegalArgumentException.class,
                () -> validate(rev, IndexDescriptions.subsetCensus()));
    }

    @Test
    @DisplayName("null facts are refused")
    void nullFacts() {
        assertThrows(
                NullPointerException.class,
                () -> CometValidator.standard().validate(Models.enforced(NEWER), null));
        assertThrows(
                NullPointerException.class,
                () -> CometValidator.standard().validate(null, PreRunFacts.none()));
    }
}
