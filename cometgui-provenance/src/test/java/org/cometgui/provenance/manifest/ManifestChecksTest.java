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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ManifestChecks}.
 *
 * <p>The validations themselves are proved through the records that use them -- that is where the
 * field names in the messages come from and where a caller meets them. What is left here is the two
 * things no record reaches: the utility constructor, and the point of the generic list check, which
 * is that a null element is reported <em>by index</em> rather than as the message-less {@link
 * NullPointerException} {@link List#copyOf} would throw.
 */
class ManifestChecksTest {

    @Test
    @DisplayName("the utility class cannot be instantiated, even reflectively")
    void theUtilityClassCannotBeInstantiated() throws NoSuchMethodException {
        Constructor<ManifestChecks> constructor = ManifestChecks.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        InvocationTargetException thrown =
                assertThrows(InvocationTargetException.class, constructor::newInstance);

        assertEquals(
                "ManifestChecks is a utility class and is never instantiated",
                thrown.getCause().getMessage());
    }

    @Test
    @DisplayName("a null element is rejected by index, not by an anonymous NullPointerException")
    void aNullElementIsRejectedByIndex() {
        List<String> withNull = Arrays.asList("first", null, "third");

        IllegalArgumentException thrown =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> ManifestChecks.copyOfNonNull(withNull, "tools"));

        assertEquals("tools[1] must not be null", thrown.getMessage());
    }
}
