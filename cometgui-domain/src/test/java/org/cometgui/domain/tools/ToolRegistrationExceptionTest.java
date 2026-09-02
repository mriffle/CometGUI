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

package org.cometgui.domain.tools;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests for {@link ToolRegistrationException}. */
class ToolRegistrationExceptionTest {

    @Test
    @DisplayName("the message is the sentence the user reads")
    void carriesItsMessage() {
        ToolRegistrationException rejected =
                new ToolRegistrationException(
                        "that binary reports Percolator 3.04, and 3.05 is the minimum CometGUI"
                                + " can use");

        assertAll(
                () ->
                        assertEquals(
                                "that binary reports Percolator 3.04, and 3.05 is the minimum"
                                        + " CometGUI can use",
                                rejected.getMessage()),
                () -> assertNull(rejected.getCause()));
    }

    @Test
    @DisplayName("a lower-level failure can be carried without being hidden")
    void carriesItsCause() {
        IllegalStateException cause = new IllegalStateException("the file could not be read");

        ToolRegistrationException rejected =
                new ToolRegistrationException("that file could not be probed", cause);

        assertAll(
                () -> assertEquals("that file could not be probed", rejected.getMessage()),
                () -> assertSame(cause, rejected.getCause()));
    }

    @Test
    @DisplayName("it is checked, so a caller cannot forget to handle a rejected binary")
    void itIsChecked() {
        assertEquals(Exception.class, ToolRegistrationException.class.getSuperclass());
    }
}
