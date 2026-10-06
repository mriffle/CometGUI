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

package org.cometgui.workflow.state;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests for {@link InputKind}: identifiers and value types, pinned. */
class InputKindTest {

    @Test
    @DisplayName("every input kind's identifier and value type")
    void identifiersAndTypes() {
        List<String> rows = new ArrayList<>();
        for (InputKind kind : InputKind.values()) {
            rows.add(kind.id() + " " + kind.valueType());
        }
        assertEquals(
                List.of(
                        "spectrum-files FILES",
                        "fasta FILES",
                        "comet-parameters BYTES",
                        "comet-index-mode TEXT",
                        "comet-tool TOOL",
                        "percolator-settings TEXT",
                        "percolator-tool TOOL",
                        "pdv-tool TOOL",
                        "limelight-q-cutoff DECIMAL",
                        "limelight-converter-options TEXT",
                        "limelight-converter-tool TOOL",
                        "limelight-upload-target TEXT",
                        "psm-display-filter DECIMAL",
                        "peptide-display-filter DECIMAL"),
                rows);
    }
}
