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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import org.cometgui.params.comet.schema.CuratedMetadata;
import org.cometgui.params.comet.schema.MetadataLoader;

/**
 * Bundled metadata with one CONSTRUCTED extra Comet version, {@code 2099.01.0} -- test input for
 * the migration and preset tests, never any Comet's -- whose tuple layout a test chooses, so that a
 * value can be seen re-laid-out between versions. The real versions' layouts differ only in whether
 * the neutral loss takes a pair.
 */
public final class ConstructedVersions {

    private ConstructedVersions() {}

    /**
     * The bundled metadata with one more CONSTRUCTED version record, {@code 2099.01.0}: test input,
     * not any Comet's. Its tuple layout is {@code fields}, which lets a test see a tuple
     * re-laid-out; every open-ended version range claims it.
     */
    public static CuratedMetadata withConstructedVersion(String fields) {
        return withConstructedVersion(fields, "");
    }

    /**
     * As {@link #withConstructedVersion(String)}, with the version record's {@code defaults} array
     * holding {@code defaults} (JSON objects, comma separated) -- a layout of another field count
     * needs its tuples' defaults written in it.
     */
    public static CuratedMetadata withConstructedVersion(String fields, String defaults) {
        String json;
        try (var in = MetadataLoader.class.getResourceAsStream(MetadataLoader.RESOURCE)) {
            json = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
        String record =
                "{\"version\": \"2099.01.0\", \"marker\": \"2099.01 rev. 0\","
                        + " \"parameterPages\": \"https://example.org/constructed/\","
                        + " \"source\": \"https://example.org/constructed-source/\","
                        + " \"variableModTuple\": {\"source\": \"https://example.org/c\","
                        + " \"fields\": ["
                        + fields
                        + "]}, \"defaults\": ["
                        + defaults
                        + "]},\n";
        String anchor = "\"versions\": [\n";
        int at = json.indexOf(anchor);
        return MetadataLoader.load(
                json.substring(0, at + anchor.length())
                        + record
                        + json.substring(at + anchor.length()));
    }

    /**
     * The fifteen tuple defaults of {@code -q} without their neutral loss: overrides for a
     * CONSTRUCTED seven-field layout.
     */
    public static String sevenFieldTupleDefaults() {
        List<String> entries = new java.util.ArrayList<>();
        for (int slot = 1; slot <= 15; slot++) {
            entries.add(
                    "{\"name\": \""
                            + String.format(java.util.Locale.ROOT, "variable_mod%02d", slot)
                            + "\", \"default\": \""
                            + (slot == 1 ? "15.9949 M 0 3 -1 0 0" : "0.0 X 0 3 -1 0 0")
                            + "\", \"source\": \"https://example.org/constructed\"}");
        }
        return String.join(", ", entries);
    }

    /**
     * One entry of a tuple layout's {@code fields}, as JSON.
     *
     * @param field a {@code VariableModField} constant
     * @param kind its kind
     * @param pair whether the layout accepts a comma pair there
     * @return the JSON object
     */
    public static String field(String field, String kind, boolean pair) {
        return "{\"field\": \"" + field + "\", \"kind\": \"" + kind + "\", \"pair\": " + pair + "}";
    }
}
