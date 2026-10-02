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

package org.cometgui.params.comet.presets;

/**
 * CONSTRUCTED preset documents for the loader tests: test input, written here, never presented as
 * Comet's or CometGUI's own. Each starts valid and changes one thing.
 */
final class Docs {

    private Docs() {}

    static String delta(String parameter, String value, String citation) {
        return "{\"parameter\": "
                + quote(parameter)
                + ", \"value\": "
                + quote(value)
                + ", \"citation\": "
                + quote(citation)
                + "}";
    }

    static String userDelta(String parameter, String value) {
        return delta(parameter, value, null);
    }

    static String preset(
            String id,
            String origin,
            String cometVersion,
            Object schemaVersion,
            String source,
            String... deltas) {
        return "{\"id\": "
                + quote(id)
                + ", \"displayName\": \"Constructed "
                + id
                + "\", \"description\": \"A constructed preset for a loader test.\","
                + " \"origin\": "
                + quote(origin)
                + ", \"cometVersion\": "
                + quote(cometVersion)
                + ", \"schemaVersion\": "
                + schemaVersion
                + ", \"source\": "
                + quote(source)
                + ", \"deltas\": ["
                + String.join(", ", deltas)
                + "]}";
    }

    static String user(String id, String... deltas) {
        return preset(id, "USER", "2026.02.2", 1, null, deltas);
    }

    static String document(String... presets) {
        return "{\"presetFormat\": 1, \"description\": \"constructed\", \"presets\": ["
                + String.join(", ", presets)
                + "]}";
    }

    static String quote(String text) {
        return text == null ? "null" : "\"" + text.replace("\"", "\\\"") + "\"";
    }
}
