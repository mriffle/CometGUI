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

package org.cometgui.tools.pdv;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.cometgui.tools.pdv.PdvMzTabException.Reason;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The one refusal's message: the PSM when there is one, the detail, the files, the reason. */
class PdvMzTabExceptionTest {

    @Test
    @DisplayName("the message names the PSM, the detail, every file and the reason")
    void message() {
        IOException cause = new IOException("underneath");
        PdvMzTabException none =
                new PdvMzTabException(Reason.NO_PSMS, null, List.of(), "nothing", null);
        PdvMzTabException one =
                new PdvMzTabException(
                        Reason.NO_SPECTRUM, "x_1_2_3", List.of(Path.of("a.tsv")), "missing", cause);
        PdvMzTabException two =
                new PdvMzTabException(
                        Reason.PEPTIDE_MISMATCH,
                        "y_4_5_6",
                        List.of(Path.of("a.tsv"), Path.of("b.pep.xml")),
                        "differs",
                        null);
        assertAll(
                () ->
                        assertEquals(
                                "The mzTab for PDV was not written: nothing [NO_PSMS]",
                                none.getMessage()),
                () -> assertEquals(Optional.empty(), none.psmId()),
                () -> assertEquals(List.of(), none.files()),
                () -> assertEquals(Reason.NO_PSMS, none.reason()),
                () ->
                        assertEquals(
                                "The mzTab for PDV was not written: PSM x_1_2_3 missing (source"
                                        + " a.tsv) [NO_SPECTRUM]",
                                one.getMessage()),
                () -> assertSame(cause, one.getCause()),
                () -> assertEquals(Optional.of("x_1_2_3"), one.psmId()),
                () ->
                        assertEquals(
                                "The mzTab for PDV was not written: PSM y_4_5_6 differs (sources"
                                        + " a.tsv, b.pep.xml) [PEPTIDE_MISMATCH]",
                                two.getMessage()),
                () -> assertEquals(List.of(Path.of("a.tsv"), Path.of("b.pep.xml")), two.files()));
    }
}
