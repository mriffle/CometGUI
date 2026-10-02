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

package org.cometgui.params.comet.validation;

import java.nio.charset.StandardCharsets;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.model.ParameterEntry;
import org.cometgui.params.comet.model.ParameterValue;
import org.cometgui.params.comet.schema.SerializationRule;

/**
 * Validator {@code path}: the <em>form</em> of a path -- present where the metadata does not allow
 * it to be empty, no NUL character, and no longer than Comet's buffer.
 *
 * <p>Whether the file exists and is readable is not checked here: that needs the file system and is
 * the workflow's check before a run (Phase 08). An empty value is legal where the metadata's
 * serialisation is {@code EMPTY_ALLOWED}; elsewhere it is an error. Comet 2026.02.2 copies the
 * trimmed value of {@code database_name}, {@code peff_obo}, {@code compoundmods_file} and {@code
 * spectral_library_name} into {@code char szFile[SIZE_FILE]} with {@code strcpy} ({@code Comet.cpp}
 * lines 342-345 at {@code v2026.02.2}), and {@code SIZE_FILE} is 4096 ({@code
 * CometSearch/CometData.h} line 20), so a path of more than {@value #FILE_NAME_LIMIT} bytes does
 * not fit. A path Comet reads as one token instead ({@code protein_modslist_file}) is held to that
 * reading by {@link TextTokenRule}.
 */
final class PathRule {

    /** The longest path, in bytes, that fits Comet's {@code SIZE_FILE} buffer with its NUL. */
    static final int FILE_NAME_LIMIT = 4095;

    private PathRule() {}

    static void check(CometParameters model, ParameterEntry entry, Findings findings) {
        String name = entry.name();
        String path = ((ParameterValue.Text) entry.value()).text();
        if (path.isEmpty()) {
            if (entry.definition().serialization() != SerializationRule.EMPTY_ALLOWED) {
                findings.add(Rule.PATH_EMPTY, name, name + " is empty; it needs a path to a file");
            }
            return;
        }
        if (path.indexOf('\0') >= 0) {
            findings.add(
                    Rule.PATH_NUL,
                    name,
                    name
                            + " holds a NUL character, where Comet's reader ends the path; remove"
                            + " it");
        }
        int bytes = path.getBytes(StandardCharsets.UTF_8).length;
        if (!TextTokenRule.readsAsToken(name) && bytes > FILE_NAME_LIMIT) {
            findings.add(
                    Rule.PATH_TOO_LONG,
                    name,
                    name
                            + " is "
                            + bytes
                            + " bytes long; Comet holds at most "
                            + FILE_NAME_LIMIT
                            + ", so use a shorter path");
        }
    }
}
