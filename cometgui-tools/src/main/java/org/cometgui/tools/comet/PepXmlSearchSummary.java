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

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * What a pepXML file's header says that a reader of its hits needs: which file it is, which input
 * and {@code -N} base it names, and the modifications the search declared.
 *
 * @param file the pepXML file
 * @param input {@code msms_run_summary}'s {@code base_name} followed by its {@code raw_data}: the
 *     spectrum file the search was given, as {@link CometPepXmlValidator} reads it
 * @param outputBase {@code search_summary}'s {@code base_name}: Comet's {@code -N}, which is the
 *     base of every Percolator {@code PSMId} from this file
 * @param searchEngineVersion {@code search_engine_version}, verbatim, such as {@code "2026.03 rev.
 *     0 (fa08489)"}
 * @param aminoAcidModifications every {@code aminoacid_modification}, in document order
 * @param terminalModifications every {@code terminal_modification}, in document order
 */
public record PepXmlSearchSummary(
        Path file,
        String input,
        String outputBase,
        String searchEngineVersion,
        List<PepXmlAminoAcidModification> aminoAcidModifications,
        List<PepXmlTerminalModification> terminalModifications) {

    /**
     * Validates the summary.
     *
     * @throws NullPointerException if a component is {@code null}
     */
    public PepXmlSearchSummary {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(outputBase, "outputBase");
        Objects.requireNonNull(searchEngineVersion, "searchEngineVersion");
        aminoAcidModifications = List.copyOf(aminoAcidModifications);
        terminalModifications = List.copyOf(terminalModifications);
    }

    @Override
    public List<PepXmlAminoAcidModification> aminoAcidModifications() {
        return List.copyOf(aminoAcidModifications);
    }

    @Override
    public List<PepXmlTerminalModification> terminalModifications() {
        return List.copyOf(terminalModifications);
    }
}
