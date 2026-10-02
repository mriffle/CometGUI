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

package org.cometgui.params.comet.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.fixtures.ConstructedVersions;
import org.cometgui.params.comet.schema.CuratedMetadata;
import org.cometgui.params.comet.value.ValueSyntaxException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The one cross-version value rule, between the two real versions and CONSTRUCTED layouts. */
class VersionConversionTest {

    private static final VersionConversion DOWN =
            VersionConversion.between(Sets.METADATA, Sets.NEWER, Sets.OLDER);

    private static final VersionConversion UP =
            VersionConversion.between(Sets.METADATA, Sets.OLDER, Sets.NEWER);

    @Test
    @DisplayName("a value both versions hold is the same text")
    void same() {
        VersionConversion.Result result = DOWN.convert("num_threads", "8");
        assertEquals(VersionConversion.Status.SAME, result.status());
        assertEquals("8", result.sourceText());
        assertEquals(Optional.of("8"), result.targetText());
        assertEquals(
                "num_threads = 8 means the same in Comet 2026.02.2 and Comet 2024.01.0",
                result.explanation());
        assertEquals(Sets.NEWER, DOWN.from());
        assertEquals(Sets.OLDER, DOWN.to());
    }

    @Test
    @DisplayName("text is compared as each version writes it, not as typed")
    void canonicalText() {
        VersionConversion.Result result = UP.convert("fragment_bin_tol", "+1.0005");
        assertEquals(VersionConversion.Status.SAME, result.status());
        assertEquals("1.0005", result.sourceText());
        assertEquals(Optional.of("1.0005"), result.targetText());
    }

    @Test
    @DisplayName("a parameter the target lacks is NOT_IN_TARGET, naming both versions")
    void notInTarget() {
        VersionConversion.Result result = DOWN.convert("pinfile_protein_delimiter", ";");
        assertEquals(VersionConversion.Status.NOT_IN_TARGET, result.status());
        assertEquals(Optional.empty(), result.targetText());
        assertEquals(";", result.sourceText());
        assertEquals(
                "Comet 2024.01.0 has no parameter pinfile_protein_delimiter; it is a parameter of"
                        + " Comet 2026.02.2",
                result.explanation());
        assertFalse(result.status().usable());
    }

    @Test
    @DisplayName("two neutral losses cannot be held by 2024.01.0's one-loss tuple")
    void notConvertible() {
        VersionConversion.Result result =
                DOWN.convert("variable_mod01", "79.966331 STY 0 3 -1 0 0 97.976896,79.966331");
        assertEquals(VersionConversion.Status.NOT_CONVERTIBLE, result.status());
        assertTrue(result.explanation().contains("cannot be written for Comet 2024.01.0"));
        assertTrue(result.explanation().contains("two neutral losses"), result.explanation());
        VersionConversion.Result one =
                DOWN.convert("variable_mod01", "79.966331 STY 0 3 -1 0 0 97.976896");
        assertEquals(VersionConversion.Status.SAME, one.status());
        VersionConversion.Result back =
                UP.convert("variable_mod01", "79.966331 STY 0 2,3 -1 0 -1 97.976896");
        assertEquals(VersionConversion.Status.SAME, back.status());
    }

    @Test
    @DisplayName("a CONSTRUCTED seven-field layout: converted where it can hold the value")
    void reshapedOrRefused() {
        CuratedMetadata metadata =
                ConstructedVersions.withConstructedVersion(
                        String.join(
                                ", ",
                                ConstructedVersions.field("MASS", "DECIMAL", false),
                                ConstructedVersions.field("RESIDUES", "RESIDUES", false),
                                ConstructedVersions.field("BINARY_GROUP", "INTEGER", false),
                                ConstructedVersions.field("COUNT", "INTEGER", true),
                                ConstructedVersions.field("TERMINAL_DISTANCE", "INTEGER", false),
                                ConstructedVersions.field("TERMINUS", "INTEGER", false),
                                ConstructedVersions.field("REQUIRED", "INTEGER", false)),
                        ConstructedVersions.sevenFieldTupleDefaults());
        ToolVersion seven = ToolVersion.parse("2099.01.0");
        VersionConversion to = VersionConversion.between(metadata, Sets.NEWER, seven);
        VersionConversion.Result converted =
                to.convert("variable_mod01", "15.9949 M 0 3 -1 0 0 0.0");
        assertEquals(VersionConversion.Status.CONVERTED, converted.status());
        assertEquals(Optional.of("15.9949 M 0 3 -1 0 0"), converted.targetText());
        assertTrue(converted.explanation().contains("is written 15.9949 M 0 3 -1 0 0 for Comet"));
        VersionConversion.Result refused =
                to.convert("variable_mod01", "15.9949 M 0 3 -1 0 0 63.998285");
        assertEquals(VersionConversion.Status.NOT_CONVERTIBLE, refused.status());
        assertTrue(refused.explanation().contains("no neutral loss field"), refused.explanation());
        VersionConversion.Result fromSeven =
                VersionConversion.between(metadata, seven, Sets.NEWER)
                        .convert("variable_mod01", "15.9949 M 0 3 -1 0 1");
        assertEquals(VersionConversion.Status.CONVERTED, fromSeven.status());
        assertEquals(Optional.of("15.9949 M 0 3 -1 0 1 0.0"), fromSeven.targetText());
    }

    @Test
    void refusals() {
        IllegalArgumentException notInSource =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> UP.convert("pinfile_protein_delimiter", ";"));
        assertEquals(
                "pinfile_protein_delimiter is not a parameter of Comet 2024.01.0",
                notInSource.getMessage());
        assertThrows(ValueSyntaxException.class, () -> UP.convert("num_threads", "many"));
        ToolVersion uncurated = ToolVersion.parse("2019.01.5");
        IllegalArgumentException from =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> VersionConversion.between(Sets.METADATA, uncurated, Sets.NEWER));
        assertTrue(from.getMessage().endsWith("carried from it"), from.getMessage());
        IllegalArgumentException to =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> VersionConversion.between(Sets.METADATA, Sets.NEWER, uncurated));
        assertTrue(to.getMessage().endsWith("carried to it"), to.getMessage());
        assertTrue(to.getMessage().contains("2019.01.5"), to.getMessage());
    }

    @Test
    @DisplayName("a result's target text is present exactly when the status is usable")
    void resultRules() {
        IllegalArgumentException missing =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                new VersionConversion.Result(
                                        "x",
                                        VersionConversion.Status.SAME,
                                        "1",
                                        Optional.empty(),
                                        "why"));
        assertEquals("x: a SAME result needs a target text", missing.getMessage());
        IllegalArgumentException extra =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                new VersionConversion.Result(
                                        "x",
                                        VersionConversion.Status.NOT_IN_TARGET,
                                        "1",
                                        Optional.of("1"),
                                        "why"));
        assertEquals("x: a NOT_IN_TARGET result cannot have a target text", extra.getMessage());
        assertTrue(VersionConversion.Status.SAME.usable());
        assertTrue(VersionConversion.Status.CONVERTED.usable());
        assertFalse(VersionConversion.Status.NOT_IN_TARGET.usable());
        assertFalse(VersionConversion.Status.NOT_CONVERTIBLE.usable());
    }
}
