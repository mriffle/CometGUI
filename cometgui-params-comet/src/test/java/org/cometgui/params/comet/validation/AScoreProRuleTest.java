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
import static org.cometgui.params.comet.validation.Models.assertAttached;
import static org.cometgui.params.comet.validation.Models.validate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.fixtures.ParamsFiles;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.model.ValueOrigin;
import org.cometgui.params.comet.schema.CuratedMetadata;
import org.cometgui.params.comet.schema.ParameterCategory;
import org.cometgui.params.comet.schema.ParameterDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * {@link Rule#VARMODS_ASCOREPRO_SLOT} and the slot merge it models. Every tuple is CONSTRUCTED test
 * input applied to a release's real {@code -q} model, whose {@code variable_mod01} is {@code
 * 15.9949 M 0 3 -1 0 0 0.0}, whose {@code max_variable_mods_in_peptide} is 5 and whose {@code
 * print_ascorepro_score} is 1. Each merge case is one the real binaries were run on ({@code
 * ValidationCorpusRealBinaryTest} replays them); the rest vary one compared field at a time.
 */
class AScoreProRuleTest {

    private static final String PHOSPHO = "79.966331 STY 0 3 -1 0 0 0.0";

    private static final String OXIDATION = "15.9949 M 0 3 -1 0 0 0.0";

    static Stream<ToolVersion> releases() {
        return Stream.of(COMET, COMET_2026_03_0);
    }

    private static List<Finding> ascore(ToolVersion version, String... nameThenText) {
        return validate(Models.with(version, nameThenText)).of(Rule.VARMODS_ASCOREPRO_SLOT);
    }

    private static void flagged(ToolVersion version, String slot, String... nameThenText) {
        List<Finding> found = ascore(version, nameThenText);
        assertEquals(1, found.size(), () -> version + " " + List.of(nameThenText) + ": " + found);
        assertAttached(
                found.get(0),
                Rule.VARMODS_ASCOREPRO_SLOT,
                ParameterCategory.VARIABLE_MODS,
                slot,
                "print_ascorepro_score");
        assertEquals(Severity.ERROR, found.get(0).severity());
    }

    private static void clear(ToolVersion version, String... nameThenText) {
        assertEquals(
                List.of(),
                ascore(version, nameThenText),
                () -> version + " " + List.of(nameThenText));
    }

    @ParameterizedTest
    @MethodSource("releases")
    @DisplayName(
            "an active slot 10 to 15 with AScorePro on is an error; 1 to 9, or AScorePro off, not")
    void slotsAboveNine(ToolVersion version) {
        flagged(version, "variable_mod10", "variable_mod10", PHOSPHO);
        flagged(version, "variable_mod15", "variable_mod15", PHOSPHO);
        flagged(
                version,
                "variable_mod10",
                "variable_mod10",
                PHOSPHO,
                "print_ascorepro_score",
                "-1");
        flagged(version, "variable_mod12", "variable_mod12", PHOSPHO, "print_ascorepro_score", "5");
        clear(version, "variable_mod09", PHOSPHO);
        clear(version, "variable_mod10", PHOSPHO, "print_ascorepro_score", "0");
        clear(version);
        Finding finding = ascore(version, "variable_mod10", PHOSPHO).get(0);
        assertEquals(
                "print_ascorepro_score = 1 runs AScorePro, which supports variable_mod01 to"
                        + " variable_mod09 only, and variable_mod10 = "
                        + PHOSPHO
                        + " is active (no lower slot is identical to it, so Comet does not merge it"
                        + " away): AScorePro cannot tell slot 10 from two one-digit slots, so it"
                        + " can corrupt modification sites; set print_ascorepro_score to 0, or"
                        + " move the modification to an unused slot from variable_mod01 to"
                        + " variable_mod09",
                finding.message());
    }

    @ParameterizedTest
    @MethodSource("releases")
    @DisplayName("each active slot above nine is reported at itself")
    void eachSlotIsReported(ToolVersion version) {
        List<Finding> found =
                ascore(
                        version,
                        "variable_mod10",
                        PHOSPHO,
                        "variable_mod13",
                        "42.010565 K 0 1 -1 0 0 0.0");
        assertEquals(2, found.size(), found::toString);
        assertEquals("variable_mod10", found.get(0).parameters().get(0));
        assertEquals("variable_mod13", found.get(1).parameters().get(0));
    }

    @ParameterizedTest
    @MethodSource("releases")
    @DisplayName("a slot identical to a lower one is merged away and draws nothing")
    void mergedSlots(ToolVersion version) {
        clear(version, "variable_mod10", OXIDATION);
        clear(version, "variable_mod10", "15.9949 STY 0 3 -1 0 0 0.0");
        clear(version, "variable_mod10", "15.99490 M 0 3 -1 0 0 0.00");
        clear(version, "variable_mod10", "15.9949 M 0 3 -1 0 0 -0.0");
        clear(version, "variable_mod10", "15.9949 M 0 0,3 -1 0 0 0.0");
        clear(
                version,
                "variable_mod02",
                PHOSPHO,
                "variable_mod11",
                "79.966331 K 0 3 -1 0 0 0.0",
                "variable_mod14",
                OXIDATION);
    }

    @ParameterizedTest
    @MethodSource("releases")
    @DisplayName("each compared field, changed alone, keeps the slot active")
    void eachComparedField(ToolVersion version) {
        flagged(version, "variable_mod10", "variable_mod10", "15.9948 M 0 3 -1 0 0 0.0");
        flagged(version, "variable_mod10", "variable_mod10", "15.9949 M 0 3 -1 0 0 63.998285");
        flagged(
                version,
                "variable_mod10",
                "variable_mod01",
                "15.9949 M 0 3 -1 0 0 63.998285,0.0",
                "variable_mod10",
                "15.9949 M 0 3 -1 0 0 63.998285,1.0");
        flagged(version, "variable_mod10", "variable_mod10", "15.9949 M 1 3 -1 0 0 0.0");
        flagged(version, "variable_mod10", "variable_mod10", "15.9949 M 0 2 -1 0 0 0.0");
        flagged(version, "variable_mod10", "variable_mod10", "15.9949 M 0 1,3 -1 0 0 0.0");
        flagged(version, "variable_mod10", "variable_mod10", "15.9949 M 0 3 -1 0 1 0.0");
        flagged(version, "variable_mod10", "variable_mod10", "15.9949 M 0 3 -2 0 0 0.0");
        flagged(version, "variable_mod10", "variable_mod10", "15.9949 M 0 3 -1 2 0 0.0");
    }

    @ParameterizedTest
    @MethodSource("releases")
    @DisplayName("exclusive slots are never merged; an unused lower slot merges nothing")
    void notMerged(ToolVersion version) {
        String exclusive = "15.9949 M 0 3 -1 0 -1 0.0";
        flagged(
                version,
                "variable_mod10",
                "variable_mod01",
                exclusive,
                "variable_mod10",
                exclusive);
        flagged(
                version,
                "variable_mod10",
                "variable_mod01",
                "0.0 M 0 3 -1 0 0 0.0",
                "variable_mod10",
                OXIDATION);
    }

    @ParameterizedTest
    @MethodSource("releases")
    @DisplayName("counts are compared after Comet caps them at max_variable_mods_in_peptide")
    void countsAreCapped(ToolVersion version) {
        clear(
                version,
                "variable_mod10",
                "15.9949 M 0 7 -1 0 0 0.0",
                "max_variable_mods_in_peptide",
                "3");
        flagged(version, "variable_mod10", "variable_mod10", "15.9949 M 0 7 -1 0 0 0.0");
        clear(
                version,
                "variable_mod01",
                "15.9949 M 0 5 -1 0 0 0.0",
                "variable_mod10",
                "15.9949 M 0 9 -1 0 0 0.0",
                "max_variable_mods_in_peptide",
                "-1");
        flagged(
                version,
                "variable_mod10",
                "variable_mod10",
                "15.9949 M 0 9 -1 0 0 0.0",
                "max_variable_mods_in_peptide",
                "-1");
    }

    @ParameterizedTest
    @MethodSource("releases")
    @DisplayName("a limit of 0 caps every count at 0, so counts no longer tell slots apart")
    void aLimitOfZero(ToolVersion version) {
        clear(
                version,
                "variable_mod10",
                "15.9949 M 0 5 -1 0 0 0.0",
                "max_variable_mods_in_peptide",
                "0");
        flagged(
                version,
                "variable_mod10",
                "variable_mod10",
                "15.9949 M 0 5 -1 0 0 0.0",
                "max_variable_mods_in_peptide",
                "4");
    }

    @Test
    @DisplayName("the lowest slot survives the merge even where the metadata lists slots otherwise")
    void slotOrderIsComets() {
        List<ParameterDefinition> reordered = new ArrayList<>(Models.METADATA.parameters());
        Collections.swap(
                reordered,
                indexOf(reordered, "variable_mod01"),
                indexOf(reordered, "variable_mod10"));
        CuratedMetadata swapped =
                new CuratedMetadata(
                        Models.METADATA.schemaVersion(),
                        Models.METADATA.versions(),
                        reordered,
                        Models.METADATA.internal(),
                        Models.METADATA.enzymeTable());
        CometParameters model =
                Models.parse(swapped, ParamsFiles.complete())
                        .withWorkflowEnforcedOutputs()
                        .withText("variable_mod10", OXIDATION, ValueOrigin.USER);
        List<ParameterDefinition> order =
                model.entries().stream().map(entry -> entry.definition()).toList();
        assertTrue(
                indexOf(order, "variable_mod10") < indexOf(order, "variable_mod01"),
                "the CONSTRUCTED metadata lists variable_mod10 first");
        assertEquals(
                List.of("variable_mod01"),
                AScoreProRule.activeAfterMerge(model).stream()
                        .map(AScoreProRule.Slot::name)
                        .toList());
        assertEquals(List.of(), validate(model).of(Rule.VARMODS_ASCOREPRO_SLOT));
    }

    private static int indexOf(List<ParameterDefinition> definitions, String name) {
        for (int index = 0; index < definitions.size(); index++) {
            if (definitions.get(index).name().equals(name)) {
                return index;
            }
        }
        throw new AssertionError(name);
    }

    @Test
    @DisplayName(
            "n or c at distance 0 from the protein terminus merges as ^ or $ in 2026.03.0 only")
    void proteinTerminusRewrite() {
        String[] nTerminus = {
            "variable_mod01",
            "42.010565 n 0 1 0 0 0 0.0",
            "variable_mod10",
            "42.010565 n 0 1 -1 0 0 0.0"
        };
        clear(COMET_2026_03_0, nTerminus);
        flagged(COMET, "variable_mod10", nTerminus);
        String[] cTerminus = {
            "variable_mod01", "0.984 c 0 1 0 1 0 0.0", "variable_mod10", "0.984 c 0 1 -1 0 0 0.0"
        };
        clear(COMET_2026_03_0, cTerminus);
        flagged(COMET, "variable_mod10", cTerminus);
        clear(
                COMET_2026_03_0,
                "variable_mod01",
                "42.010565 n^ 0 1 0 0 0 0.0",
                "variable_mod10",
                "42.010565 ^ 0 1 -1 0 0 0.0");
        for (String notRewritten :
                List.of(
                        "42.010565 nK 0 1 0 0 0 0.0",
                        "42.010565 n 0 1 0 2 0 0.0",
                        "42.010565 n 0 1 1 0 0 0.0",
                        "42.010565 c 0 1 0 0 0 0.0",
                        "42.010565 n 0 1 0 1 0 0.0")) {
            flagged(
                    COMET_2026_03_0,
                    "variable_mod10",
                    "variable_mod01",
                    notRewritten,
                    "variable_mod10",
                    "42.010565 " + notRewritten.split(" ")[1] + " 0 1 -1 0 0 0.0");
        }
    }

    @Test
    @DisplayName("a release without print_ascorepro_score is not judged")
    void releaseWithoutTheParameter() {
        ToolVersion older = ToolVersion.parse("2024.01.0");
        CometParameters defaults =
                CometParameters.defaults(Models.METADATA, older, Models.real().enzymeTable())
                        .withText("variable_mod10", PHOSPHO, ValueOrigin.USER);
        assertTrue(defaults.entry("print_ascorepro_score").isEmpty());
        assertEquals(List.of(), validate(defaults).of(Rule.VARMODS_ASCOREPRO_SLOT));
    }

    @Test
    @DisplayName("the merge keeps the lowest slot of each identical set, in slot order")
    void survivors() {
        CometParameters model =
                Models.with(
                        COMET_2026_03_0,
                        "variable_mod12",
                        PHOSPHO,
                        "variable_mod03",
                        PHOSPHO,
                        "variable_mod11",
                        "42.010565 K 0 1 -1 0 0 0.0");
        assertEquals(
                List.of("variable_mod01", "variable_mod03", "variable_mod11"),
                AScoreProRule.activeAfterMerge(model).stream()
                        .map(AScoreProRule.Slot::name)
                        .toList());
        assertEquals(
                List.of(1, 3, 11),
                AScoreProRule.activeAfterMerge(model).stream()
                        .map(AScoreProRule.Slot::number)
                        .toList());
    }
}
