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

package org.cometgui.provenance.manifest;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ProvenanceSchema}.
 *
 * <p>Both constants are asserted against hand-typed literals, which is the only assertion that
 * means anything here. {@code assertEquals(ProvenanceSchema.VERSION, manifest.schemaVersion())}
 * would agree with any value the constant happened to hold, including the wrong one; these fail the
 * moment either constant changes, which is the point -- both are part of an on-disk contract that
 * documents written by earlier builds still depend on.
 */
class ProvenanceSchemaTest {

    @Test
    @DisplayName("the schema version is 1")
    void theSchemaVersionIsOne() {
        assertEquals(1, ProvenanceSchema.VERSION);
    }

    @Test
    @DisplayName("the Percolator seed settings key is pinned to \"percolator.seed\"")
    void thePercolatorSeedKeyIsPinned() {
        assertEquals("percolator.seed", ProvenanceSchema.PERCOLATOR_SEED_SETTING);
    }

    @Test
    @DisplayName("the settings key pattern is pinned to this exact regular expression")
    void theSettingsKeyPatternIsPinned() {
        assertEquals("[a-z0-9]+(\\.[a-z0-9-]+)+", ProvenanceSchema.SETTINGS_KEY_PATTERN);
    }

    @Test
    @DisplayName("the Percolator seed key obeys the convention this class pins")
    void thePercolatorSeedKeyObeysTheConvention() {
        assertTrue(
                Pattern.compile(ProvenanceSchema.SETTINGS_KEY_PATTERN)
                        .matcher(ProvenanceSchema.PERCOLATOR_SEED_SETTING)
                        .matches(),
                "the pinned seed key does not match the pinned key pattern: "
                        + ProvenanceSchema.PERCOLATOR_SEED_SETTING);
    }

    @Test
    @DisplayName("the pattern accepts what a later phase will need and refuses the near misses")
    void thePatternAcceptsAndRefusesTheRightThings() {
        Pattern pattern = Pattern.compile(ProvenanceSchema.SETTINGS_KEY_PATTERN);

        assertAll(
                () -> assertTrue(pattern.matcher("percolator.seed").matches()),
                () -> assertTrue(pattern.matcher("comet.enzyme").matches()),
                () -> assertTrue(pattern.matcher("upload.api.key").matches()),
                () -> assertTrue(pattern.matcher("limelight.import.decoy-prefix").matches()),
                () -> assertTrue(pattern.matcher("results.q.psm-1pct").matches()),
                () -> assertFalse(pattern.matcher("PERCOLATOR_SEED").matches()),
                () -> assertFalse(pattern.matcher("percolator_seed").matches()),
                () -> assertFalse(pattern.matcher("Percolator.Seed").matches()),
                () -> assertFalse(pattern.matcher("percolator.Seed").matches()),
                () -> assertFalse(pattern.matcher("seed").matches()),
                () -> assertFalse(pattern.matcher("percolator.").matches()),
                () -> assertFalse(pattern.matcher(".seed").matches()),
                () -> assertFalse(pattern.matcher("percolator..seed").matches()),
                () -> assertFalse(pattern.matcher("per-colator.seed").matches()),
                () -> assertFalse(pattern.matcher("percolator seed").matches()));
    }

    @Test
    @DisplayName("the constant holder cannot be instantiated, even reflectively")
    void theConstantHolderCannotBeInstantiated() throws NoSuchMethodException {
        Constructor<ProvenanceSchema> constructor = ProvenanceSchema.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        InvocationTargetException thrown =
                assertThrows(InvocationTargetException.class, constructor::newInstance);

        assertAll(
                () -> assertInstanceOf(AssertionError.class, thrown.getCause()),
                () ->
                        assertEquals(
                                "ProvenanceSchema is a constant holder and is never instantiated",
                                thrown.getCause().getMessage()));
    }
}
