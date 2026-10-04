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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.fixtures.UpstreamMirror;
import org.cometgui.params.comet.model.CometParameters;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The validator half of the agreement, on every platform: for each case and release, the
 * validator's findings for the case's parameters are exactly the recorded ones, and each recorded
 * verdict meets the agreement criterion against what the real binary was recorded doing. {@link
 * ValidationCorpusRealBinaryTest} holds the real binaries to the same recorded verdicts on Linux;
 * together the two prove the validator agrees with each release's binary. The database path is a
 * stand-in here: no rule reads the file, and the one rule that reads the name looks only at its
 * {@code .idx} suffix.
 */
class ValidationCorpusTest {

    private static final ValidationCorpus CORPUS = ValidationCorpus.load();

    private static final String STAND_IN = "/data/comet-validation/subset.fasta";

    static Stream<ValidationCorpus.Case> cases() {
        return CORPUS.cases().stream();
    }

    private static List<ValidationCorpus.Edit> at(List<ValidationCorpus.Edit> edits) {
        return edits.stream().map(edit -> edit.at(STAND_IN)).toList();
    }

    static List<String> findings(ValidationReport report) {
        return report.findings().stream()
                .map(f -> f.severity() + " " + f.rule().id())
                .sorted()
                .toList();
    }

    @ParameterizedTest
    @MethodSource("cases")
    @DisplayName("the validator gives each release's recorded verdict, which meets the criterion")
    void theValidatorAgrees(ValidationCorpus.Case kase) throws IOException {
        assertEquals(2, kase.verdicts().size(), kase.id());
        for (ValidationCorpus.Verdict verdict : kase.verdicts()) {
            List<String> refused = new ArrayList<>();
            CometParameters model =
                    ValidationCorpus.model(
                            verdict.version(), at(CORPUS.base()), at(kase.edits()), refused);
            ValidationReport report = CometValidator.standard().validate(model);
            String where = kase.id() + ", Comet " + verdict.version().text();
            assertEquals(verdict.findings(), findings(report), where + ": " + report);
            assertEquals(verdict.builtInCode(), !refused.isEmpty(), where + ": " + refused);
            ValidationCorpus.assertAgreement(kase, verdict);
        }
    }

    @Test
    @DisplayName("the base model is clean in every release, and the corpus covers both releases")
    void theBaseIsClean() throws IOException {
        for (ToolVersion version : List.of(Models.COMET, Models.COMET_2026_03_0)) {
            CometParameters base =
                    ValidationCorpus.model(
                            version, at(CORPUS.base()), List.of(), new ArrayList<>());
            assertEquals(
                    List.of(), CometValidator.standard().validate(base).findings(), version.text());
        }
        assertEquals(
                List.of(Models.COMET_2026_03_0, Models.COMET),
                CORPUS.controls().stream().map(ValidationCorpus.Control::version).toList());
        Set<String> ids = new HashSet<>();
        for (ValidationCorpus.Case kase : CORPUS.cases()) {
            assertTrue(ids.add(kase.id()), "case " + kase.id() + " is listed twice");
            assertEquals(
                    List.of(Models.COMET_2026_03_0, Models.COMET),
                    kase.verdicts().stream().map(ValidationCorpus.Verdict::version).toList(),
                    kase.id());
        }
        assertTrue(CORPUS.cases().size() >= 40, "the corpus holds " + CORPUS.cases().size());
    }

    @Test
    @DisplayName("every case is in the developer page's corpus table")
    void everyCaseIsDocumented() throws IOException {
        String page =
                Files.readString(
                        UpstreamMirror.repositoryRoot()
                                .resolve("docs/developer/comet_parameter_schema.rst"),
                        StandardCharsets.UTF_8);
        for (ValidationCorpus.Case kase : CORPUS.cases()) {
            assertTrue(
                    page.contains("   * - ``" + kase.id() + "``"),
                    "the corpus table of comet_parameter_schema.rst has no row for " + kase.id());
        }
    }

    @Test
    @DisplayName("each topic the brief names has cases that settle both releases")
    void everyTopicIsCovered() {
        Set<String> topics = new HashSet<>();
        CORPUS.cases().forEach(kase -> topics.add(kase.topic()));
        assertEquals(
                Set.of(
                        "control",
                        "residue codes ^ and $",
                        "fifth field: distance",
                        "sixth field: terminus",
                        "AScorePro and slots 10-15",
                        "index_search_type",
                        "undefined enzyme numbers",
                        "add_U_selenocysteine",
                        "spectral_library_ms_level"),
                topics);
    }
}
