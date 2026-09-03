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

package org.cometgui.tools.comet;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * "Did Comet's own code run?", against the two forms the real binary prints.
 *
 * <p>Both lines below were observed on this project's Debian 12 host: {@code comet -q} prints the
 * quoted form on standard output and exits 0, {@code comet -h} prints the unquoted form on standard
 * error and exits 1.
 */
class CometBannerTest {

    /** Observed 2026-09-03 on standard output from {@code comet -q}, quoted, leading space kept. */
    private static final String QUOTED = " Comet version \"2026.02 rev. 2 (6edec91)\"";

    /** Observed 2026-09-02 on standard error from {@code comet -h}, unquoted. */
    private static final String UNQUOTED = " Comet version 2026.02 rev. 2 (6edec91)";

    @Test
    @DisplayName("both forms the real binary prints are recognised")
    void bothRealForms() {
        assertAll(
                () -> assertTrue(CometBanner.isPresentIn(List.of(QUOTED))),
                () -> assertTrue(CometBanner.isPresentIn(List.of(UNQUOTED))),
                () -> assertTrue(CometBanner.isPresentIn(List.of("noise", UNQUOTED, "more"))));
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @ValueSource(
            strings = {
                "comet version 2026.02 rev. 2",
                "Comet version 2026.02 rev 2",
                "Comet version 2026.02",
                "Comet 2026.02 rev. 2",
                "Comet version A.BB rev. 2",
                "bash: comet: cannot execute binary file: Exec format error",
                ""
            })
    @DisplayName("anything else is not evidence that Comet ran")
    void anythingElse(String line) {
        assertFalse(CometBanner.isPresentIn(List.of(line)));
    }

    @Test
    @DisplayName("a null line is skipped, and no lines at all is no evidence")
    void nullsAndEmptiness() {
        assertAll(
                () -> assertTrue(CometBanner.isPresentIn(Arrays.asList(null, QUOTED))),
                () -> assertFalse(CometBanner.isPresentIn(Arrays.asList((String) null))),
                () -> assertFalse(CometBanner.isPresentIn(List.of())),
                () ->
                        assertEquals(
                                "lines",
                                assertThrows(
                                                NullPointerException.class,
                                                () -> CometBanner.isPresentIn(null))
                                        .getMessage()));
    }
}
