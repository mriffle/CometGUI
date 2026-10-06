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

package org.cometgui.workflow.steps;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Real {@code .idx} headers, copied from indexes the pinned binaries built over the proteome's
 * first 1000 records with each release's {@code comet -q} defaults, {@code decoy_search = 1} and
 * {@code num_threads = 4} (measured 2026-10-06), followed by the empty line that ends a header and
 * one protein name -- enough for the header reader, which reads nothing after the empty line.
 */
final class IndexHeaders {

    private static final String STATIC =
            "StaticMod: 0.000000 0.000000 57.021464" + " 0.000000".repeat(27) + "\n";

    private IndexHeaders() {}

    /** Comet 2026.03.0's fragment-ion index header, naming {@code inputDb} as its FASTA. */
    static String v5(String inputDb) {
        return "Comet index database v5.  Comet version 2026.03 rev. 0 (fa08489)\n"
                + "IndexSearchType: fragment ion index\n"
                + "InputDB:  "
                + inputDb
                + "\n"
                + "MassRange: 600.000000 5000.000000\n"
                + "LengthRange: 5 50\n"
                + "MassType: 1 1\n"
                + "DecoySearch: 1\n"
                + "DecoyPrefix: DECOY_\n"
                + "Enzyme: Trypsin [1 KR P]\n"
                + "Enzyme2: Cut_everywhere [0 - -]\n"
                + "NumEnzymeTermini: 2\n"
                + "AllowedMissedCleavage: 2\n"
                + "ClipNtermMethionine: 0\n"
                + "NumPeptides: 129327\n"
                + STATIC
                + "VariableMod: M:15.994900:0.000000:0.000000:3:-1:0"
                + " X:0.000000:0.000000:0.000000:3:-1:0".repeat(4)
                + "\n"
                + "ProteinModList: 0\n"
                + "RequireVariableMod: 0 0 0 0 0 0\n"
                + "MaxVariableModsInPeptide: 5\n"
                + "\n"
                + "sp|A0A075B6H9|LV469_HUMAN\n";
    }

    /** Comet 2026.02.2's fragment-ion index header, naming {@code inputDb} as its FASTA. */
    static String v4(String inputDb) {
        return "Comet index database v4.  Comet version 2026.02 rev. 2 (6edec91)\n"
                + "IndexSearchType: fragment ion index\n"
                + "InputDB:  "
                + inputDb
                + "\n"
                + "MassRange: 600.000000 5000.000000\n"
                + "LengthRange: 5 50\n"
                + "MassType: 1 1\n"
                + "DecoySearch: 1\n"
                + "Enzyme: Trypsin [1 KR P]\n"
                + "Enzyme2: Cut_everywhere [0 - -]\n"
                + "NumPeptides: 129327\n"
                + STATIC
                + "VariableMod: M:15.994900:0.000000:0.000000:3"
                + " X:0.000000:0.000000:0.000000:3".repeat(4)
                + "\n"
                + "ProteinModList: 0\n"
                + "RequireVariableMod: 0 0 0 0 0 0\n"
                + "MaxVariableModsInPeptide: 5\n"
                + "\n"
                + "sp|A0A075B6H9|LV469_HUMAN\n";
    }

    /** Writes a header as an index file. */
    static Path write(Path file, String header) throws IOException {
        Files.createDirectories(file.getParent());
        return Files.writeString(file, header, StandardCharsets.US_ASCII);
    }
}
