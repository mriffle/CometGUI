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

package org.cometgui.params.comet.migration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Set;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.fixtures.CometFixtures;
import org.cometgui.params.comet.fixtures.MigrationFixtures;
import org.cometgui.params.comet.fixtures.ParamsFiles;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.parser.CometParamsParser;
import org.cometgui.params.comet.parser.ParseResult;
import org.cometgui.params.comet.schema.CuratedMetadata;
import org.cometgui.params.comet.schema.MetadataLoader;

/** The two real parameter sets the migration tests start from, and the facts typed about them. */
final class Sets {

    static final CuratedMetadata METADATA = MetadataLoader.loadBundled();

    static final ToolVersion OLDER = ToolVersion.parse(MigrationFixtures.VERSION);

    static final ToolVersion NEWER = ParamsFiles.COMET;

    /**
     * The parameters Comet 2026.02.2's {@code -q} declares and Comet 2024.01.0's does not, typed by
     * hand, in 2026.02.2's schema order, from a comparison of the two real dumps -- checked against
     * the same set derived from the fixtures, so each checks the other.
     */
    static final List<String> NEW_IN_2026 =
            List.of(
                    "index_search_type",
                    "compoundmods_file",
                    "spectral_library_name",
                    "spectral_library_ms_level",
                    "protein_modslist_file",
                    "print_ascorepro_score",
                    "pinfile_protein_delimiter",
                    "min_precursor_charge",
                    "percentage_base_peak");

    /** Typed by hand: the parameters 2024.01.0 declares that 2026.02.2 does not -- none. */
    static final Set<String> GONE_IN_2026 = Set.of();

    private Sets() {}

    static String olderText() {
        try {
            return MigrationFixtures.text(CometFixtures.Mode.COMPLETE);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    static String newerText() {
        return ParamsFiles.complete();
    }

    static CometParameters parse(ToolVersion version, String text) {
        ParseResult result = new CometParamsParser(METADATA, version).parse(text);
        return result.model()
                .orElseThrow(() -> new AssertionError("does not parse: " + result.errors()));
    }

    static CometParameters older() {
        return parse(OLDER, olderText());
    }

    static CometParameters newer() {
        return parse(NEWER, newerText());
    }

    /** A copy of {@code text} with {@code block} inserted before the line starting {@code at}. */
    static String inserting(String text, String at, String block) {
        int index = text.indexOf("\n" + at);
        if (index < 0 || text.indexOf("\n" + at, index + 1) >= 0) {
            throw new AssertionError("\"" + at + "\" does not start exactly one line");
        }
        return text.substring(0, index + 1) + block + text.substring(index + 1);
    }

    /** A copy of {@code text} with the line starting {@code at} replaced by {@code line}. */
    static String replacing(String text, String at, String line) {
        int index = text.indexOf("\n" + at);
        if (index < 0 || text.indexOf("\n" + at, index + 1) >= 0) {
            throw new AssertionError("\"" + at + "\" does not start exactly one line");
        }
        int end = text.indexOf('\n', index + 1);
        return text.substring(0, index + 1) + line + text.substring(end);
    }
}
