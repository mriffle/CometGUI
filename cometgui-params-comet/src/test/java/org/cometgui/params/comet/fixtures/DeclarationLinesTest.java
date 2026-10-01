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

package org.cometgui.params.comet.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The line rule, on <strong>constructed</strong> lines -- none of these is Comet output. */
class DeclarationLinesTest {

    @Test
    @DisplayName("a name at column one, optional blanks, then = is a declaration")
    void declarations() {
        assertEquals(
                List.of("num_threads", "add_W_tryptophan", "variable_mod01", "peff_format"),
                DeclarationLines.names(
                        List.of(
                                "num_threads = 0                        # comment",
                                "add_W_tryptophan=0.0000",
                                "variable_mod01 \t= 15.9949 M 0 3 -1 0 0 0.0",
                                "peff_format =")));
    }

    @Test
    @DisplayName("comments, indented lines, enzyme rows, the table marker and bare words are not")
    void nonDeclarations() {
        assertEquals(
                List.of(),
                DeclarationLines.names(
                        List.of(
                                "# comet_version 2026.02 rev. 2 (6edec91)",
                                "#num_threads = 0",
                                " num_threads = 0",
                                "\tnum_threads = 0",
                                "[COMET_ENZYME_INFO]",
                                "1.  Trypsin                1      KR          P",
                                "num_threads",
                                "num-threads = 0",
                                "= 0",
                                "")));
    }

    @Test
    @DisplayName("duplicates are kept, so a caller can see them")
    void duplicatesAreKept() {
        assertEquals(
                List.of("num_threads", "num_threads"),
                DeclarationLines.names(List.of("num_threads = 0", "num_threads = 8")));
    }
}
