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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
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
