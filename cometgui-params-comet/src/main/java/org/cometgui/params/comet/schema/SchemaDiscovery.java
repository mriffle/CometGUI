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

package org.cometgui.params.comet.schema;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.cometgui.params.comet.parser.ParamsLine;
import org.cometgui.params.comet.parser.ParamsLineReader;
import org.cometgui.params.comet.parser.ParamsText;

/**
 * Turns the text of a Comet parameter dump into a {@link DiscoveredSchema}.
 *
 * <p>A dump is Comet's own output, so anything unexpected in it is reported, not tolerated: a
 * malformed line, a name declared twice, a first line that is not a {@code # comet_version} marker
 * this project can map to a version, or a missing enzyme table each throw {@link
 * MalformedDumpException} naming the line. The line shapes come from {@link ParamsLineReader}, the
 * one place that knows them.
 */
public final class SchemaDiscovery {

    private SchemaDiscovery() {}

    /**
     * Reads a dump.
     *
     * @param text the whole {@code comet.params.new} Comet wrote
     * @param mode {@link DiscoveryMode#COMPLETE} for {@code -q} output, {@link
     *     DiscoveryMode#PARTIAL_DISCOVERY} for {@code -p}
     * @return the discovered schema
     * @throws MalformedDumpException if the text is not a well-formed dump
     */
    public static DiscoveredSchema discover(String text, DiscoveryMode mode) {
        Objects.requireNonNull(mode, "mode");
        ParamsText parsed = ParamsLineReader.read(text);
        List<ParamsLine.Malformed> malformed = parsed.malformed();
        if (!malformed.isEmpty()) {
            throw new MalformedDumpException(
                    "line "
                            + malformed.get(0).number()
                            + " of the dump: "
                            + malformed.get(0).reason());
        }
        CometVersionMarker marker = marker(parsed);
        List<DiscoveredParameter> parameters = new ArrayList<>();
        Map<String, Integer> firstLine = new HashMap<>();
        for (ParamsLine.Declaration declaration : parsed.declarations()) {
            Integer earlier = firstLine.putIfAbsent(declaration.name(), declaration.number());
            if (earlier != null) {
                throw new MalformedDumpException(
                        "line "
                                + declaration.number()
                                + " of the dump declares "
                                + declaration.name()
                                + " again; line "
                                + earlier
                                + " already did");
            }
            parameters.add(
                    new DiscoveredParameter(
                            declaration.name(),
                            declaration.value(),
                            declaration.inlineComment(),
                            declaration.number()));
        }
        List<String> rows = parsed.enzymeRows().stream().map(ParamsLine::text).toList();
        if (rows.isEmpty()) {
            throw new MalformedDumpException(
                    "the dump has no "
                            + ParamsLineReader.ENZYME_HEADER
                            + " rows, and every Comet dump ends with that table");
        }
        return new DiscoveredSchema(mode, marker, parameters, rows);
    }

    private static CometVersionMarker marker(ParamsText parsed) {
        List<ParamsLine> lines = parsed.lines();
        if (lines.isEmpty() || !(lines.get(0) instanceof ParamsLine.VersionMarker first)) {
            throw new MalformedDumpException(
                    "line 1 of the dump is not a \""
                            + ParamsLineReader.MARKER_PREFIX.strip()
                            + "\" marker, so the dump cannot be tied to a Comet version");
        }
        try {
            return CometVersionMarker.parseLine(first.text().strip());
        } catch (IllegalArgumentException unmappable) {
            throw new MalformedDumpException("line 1 of the dump: " + unmappable.getMessage());
        }
    }
}
