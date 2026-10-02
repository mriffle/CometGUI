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

package org.cometgui.params.comet.presets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Optional;
import org.cometgui.params.comet.fixtures.ParamsFiles;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.model.UnknownParameter;
import org.cometgui.params.comet.model.ValueOrigin;
import org.cometgui.params.comet.parser.CometParamsParser;
import org.cometgui.params.comet.value.EnzymeDefinition;
import org.cometgui.params.comet.value.EnzymeTableCodec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The diff between two parameter sets of one version (Expert mode's "versus defaults" and "versus
 * the last saved"), over the real {@code -q} model with CONSTRUCTED changes.
 */
class ParameterDiffTest {

    private static DiffRow row(DiffRow.Kind kind, String key, String current, String other) {
        return new DiffRow(kind, key, Optional.ofNullable(current), Optional.ofNullable(other));
    }

    @Test
    @DisplayName("identical sets have no rows; an origin alone is not a difference")
    void noDifference() {
        CometParameters model = Real.newer();
        assertEquals(List.of(), ParameterDiff.between(model, Real.newer()));
        assertEquals(
                List.of(),
                ParameterDiff.between(model, model.withOrigin("num_threads", ValueOrigin.USER)));
    }

    @Test
    @DisplayName("parameters, then enzyme rows, then unknown parameters -- each in its order")
    void everyKindOfDifference() {
        CometParameters current = Real.newer();
        EnzymeDefinition gluC = EnzymeTableCodec.parseRow("12. Glu_C 1 DE P");
        String withUnknowns =
                ParamsFiles.completeWith(
                        "num_threads", "knob_a = 1\nknob_b = 2\n"); // CONSTRUCTED declarations
        CometParameters other =
                new CometParamsParser(Real.METADATA, Real.NEWER)
                        .parse(withUnknowns)
                        .model()
                        .orElseThrow()
                        .withText("num_threads", "8", ValueOrigin.USER)
                        .withText("decoy_search", "1", ValueOrigin.USER)
                        .withEnzymeTable(current.enzymeTable().without(11).with(gluC));
        CometParameters mine =
                current.withEnzymeTable(
                        current.enzymeTable()
                                .without(3)
                                .with(EnzymeTableCodec.parseRow("3. Lys_C 1 K -")));
        List<DiffRow> rows = ParameterDiff.between(mine, other);
        assertEquals(
                List.of(
                        row(DiffRow.Kind.PARAMETER, "decoy_search", "0", "1"),
                        row(DiffRow.Kind.PARAMETER, "num_threads", "0", "8"),
                        row(
                                DiffRow.Kind.ENZYME_ROW,
                                "3",
                                EnzymeTableCodec.formatRow(
                                        mine.enzymeTable().byNumber(3).orElseThrow()),
                                EnzymeTableCodec.formatRow(
                                        current.enzymeTable().byNumber(3).orElseThrow())),
                        row(
                                DiffRow.Kind.ENZYME_ROW,
                                "11",
                                EnzymeTableCodec.formatRow(
                                        current.enzymeTable().byNumber(11).orElseThrow()),
                                null),
                        row(DiffRow.Kind.ENZYME_ROW, "12", null, EnzymeTableCodec.formatRow(gluC)),
                        row(DiffRow.Kind.UNKNOWN_PARAMETER, "knob_a", null, "1"),
                        row(DiffRow.Kind.UNKNOWN_PARAMETER, "knob_b", null, "2")),
                rows);
        List<DiffRow> back = ParameterDiff.between(other, mine);
        assertEquals(rows.size(), back.size());
        assertEquals(row(DiffRow.Kind.UNKNOWN_PARAMETER, "knob_a", "1", null), back.get(5));
    }

    @Test
    @DisplayName("an unknown parameter on both sides with different values is one row")
    void unknownValueChanged() {
        CometParameters a =
                new CometParamsParser(Real.METADATA, Real.NEWER)
                        .parse(ParamsFiles.completeWith("num_threads", "knob = 1\n"))
                        .model()
                        .orElseThrow();
        CometParameters b =
                new CometParamsParser(Real.METADATA, Real.NEWER)
                        .parse(ParamsFiles.completeWith("num_threads", "knob = 2\n"))
                        .model()
                        .orElseThrow();
        CometParameters c = b.withoutUnknown("knob");
        CometParameters same =
                new CometParamsParser(Real.METADATA, Real.NEWER)
                        .parse(ParamsFiles.completeWith("num_threads", "knob = 1\n"))
                        .model()
                        .orElseThrow();
        assertEquals(List.of(), ParameterDiff.between(a, same), "the same unknown is no row");
        assertEquals(
                List.of(row(DiffRow.Kind.UNKNOWN_PARAMETER, "knob", "1", "2")),
                ParameterDiff.between(a, b));
        assertEquals(
                List.of(row(DiffRow.Kind.UNKNOWN_PARAMETER, "knob", null, "2")),
                ParameterDiff.between(c, b));
        UnknownParameter kept = a.unknownParameters().get(0);
        assertEquals("knob", kept.name());
    }

    @Test
    @DisplayName("sets of different versions are not diffed: that is a migration")
    void differentVersionsAreRefused() {
        IllegalArgumentException failure =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> ParameterDiff.between(Real.newer(), Real.older()));
        assertEquals(
                "the sets are for Comet 2026.02.2 and Comet 2024.01.0; migrate one to the other's"
                        + " version before comparing them",
                failure.getMessage());
    }
}
