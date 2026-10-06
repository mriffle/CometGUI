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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.cometgui.domain.tools.ToolVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A version record's {@code indexFormats}: which formats of existing {@code .idx} file the release
 * can search (decision C-2 of the Comet 2026.03.0 intake, applied by Phase 08 unit 3). What the
 * bundled metadata states, what the loader reads and refuses on CONSTRUCTED metadata ({@link
 * ConstructedMetadata}), and the {@link IndexFormats} record and the record's copies.
 */
class IndexFormatsLoaderTest {

    private static final String WHERE = "versions[0]";

    private static final String FORMATS = WHERE + " indexFormats";

    @SuppressWarnings("unchecked")
    private static Map<String, Object> record(ConstructedMetadata doc) {
        return (Map<String, Object>) doc.list("versions").get(0);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> formats(ConstructedMetadata doc) {
        return (Map<String, Object>) record(doc).get("indexFormats");
    }

    private static void rejected(
            ConstructedMetadata doc, String where, String field, String fragment) {
        InvalidMetadataException failure = assertThrows(InvalidMetadataException.class, doc::load);
        assertEquals(where, failure.where(), failure.getMessage());
        assertEquals(field, failure.field(), failure.getMessage());
        assertTrue(failure.getMessage().contains(fragment), failure.getMessage());
    }

    private static IndexFormats loaded(ConstructedMetadata doc) {
        return doc.load()
                .version(ToolVersion.parse(ConstructedMetadata.VERSION))
                .orElseThrow()
                .indexFormats();
    }

    @Test
    @DisplayName("the bundled metadata states what each release's binary was seen to read")
    void bundled() {
        CuratedMetadata metadata = MetadataLoader.loadBundled();
        IndexFormats newer =
                metadata.version(ToolVersion.parse("2026.03.0")).orElseThrow().indexFormats();
        IndexFormats older =
                metadata.version(ToolVersion.parse("2026.02.2")).orElseThrow().indexFormats();
        IndexFormats oldest =
                metadata.version(ToolVersion.parse("2024.01.0")).orElseThrow().indexFormats();
        assertEquals(List.of(5), newer.readable());
        assertEquals(List.of(4), older.readable());
        assertEquals(List.of(), oldest.readable());
        assertEquals(
                Optional.of(
                        "https://github.com/UWPR/Comet/blob/v2026.03.0/CometSearch/"
                                + "CometPeptideIndex.cpp#L1640-L1653"),
                newer.source());
        assertEquals(
                Optional.of(
                        "https://github.com/UWPR/Comet/blob/v2026.02.2/CometSearch/"
                                + "CometPeptideIndex.cpp#L1112-L1120"),
                older.source());
        assertTrue(newer.reads(5));
        assertFalse(newer.reads(4));
        assertTrue(older.reads(4));
        assertFalse(older.reads(5));
        assertFalse(oldest.reads(4));
        assertFalse(oldest.reads(5));
    }

    @Test
    @DisplayName("the member loads into the record")
    void loads() {
        ConstructedMetadata doc = ConstructedMetadata.valid();
        assertEquals(
                new IndexFormats(
                        List.of(4),
                        Optional.of("https://example.org/source/CometPeptideIndex.cpp")),
                loaded(doc));
        formats(doc).put("readable", new ArrayList<>(List.of(5L, 4L)));
        assertEquals(List.of(5, 4), loaded(doc).readable());
        formats(doc).put("readable", new ArrayList<>());
        assertEquals(List.of(), loaded(doc).readable());
    }

    @Test
    @DisplayName("the member is required and is an object of exactly readable and source")
    void container() {
        ConstructedMetadata doc = ConstructedMetadata.valid();
        record(doc).remove("indexFormats");
        rejected(doc, WHERE, "indexFormats", "is missing");
        record(doc).put("indexFormats", List.of(5L));
        rejected(doc, WHERE, "indexFormats", "must be a JSON object");
        record(doc).put("indexFormats", ConstructedMetadata.indexFormats(5));
        formats(doc).put("since", "2026.03.0");
        rejected(doc, FORMATS, "since", "is not a field this format has");
        formats(doc).remove("since");
        formats(doc).remove("readable");
        rejected(doc, FORMATS, "readable", "is missing");
        formats(doc).put("readable", 5L);
        rejected(doc, FORMATS, "readable", "must be an array");
        formats(doc).put("readable", new ArrayList<>(List.of(5L)));
        formats(doc).remove("source");
        rejected(doc, FORMATS, "source", "is missing");
    }

    @Test
    @DisplayName("each format is a whole number of 1 or more, listed once")
    void formatNumbers() {
        String message =
                "must hold whole numbers of 1 or more: the N of the index formats (\"Comet index"
                        + " database vN\") the release reads";
        for (Object bad : List.of(0L, -4L, "5", 2147483648L, true)) {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            formats(doc).put("readable", new ArrayList<>(List.of(4L, bad)));
            rejected(doc, FORMATS, "readable", message);
        }
        ConstructedMetadata doc = ConstructedMetadata.valid();
        formats(doc).put("readable", new ArrayList<>(List.of(2147483647L)));
        assertEquals(List.of(Integer.MAX_VALUE), loaded(doc).readable());
        formats(doc).put("readable", new ArrayList<>(List.of(1L)));
        assertEquals(List.of(1), loaded(doc).readable());
        formats(doc).put("readable", new ArrayList<>(List.of(5L, 4L, 5L)));
        rejected(doc, FORMATS, "readable", "lists format 5 twice");
    }

    @Test
    @DisplayName("the source is an https:// reference")
    void source() {
        ConstructedMetadata doc = ConstructedMetadata.valid();
        formats(doc).put("source", "CometPeptideIndex.cpp line 1642");
        rejected(
                doc,
                FORMATS,
                "source",
                "\"CometPeptideIndex.cpp line 1642\" is not an https:// reference");
    }

    @Test
    @DisplayName("the record: reads, describes, refuses what cannot be one, and is immutable")
    void theRecord() {
        IndexFormats two = new IndexFormats(new ArrayList<>(List.of(4, 5)), Optional.empty());
        assertEquals("index formats v4 and v5", two.describe());
        assertEquals("index format v5", new IndexFormats(List.of(5), Optional.empty()).describe());
        assertEquals("none of the versioned index formats", IndexFormats.unstated().describe());
        assertEquals(List.of(), IndexFormats.unstated().readable());
        assertEquals(Optional.empty(), IndexFormats.unstated().source());
        assertFalse(IndexFormats.unstated().reads(5));
        assertThrows(UnsupportedOperationException.class, () -> two.readable().clear());
        assertEquals(
                "an index format number is 1 or more, not 0",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> new IndexFormats(List.of(5, 0), Optional.empty()))
                        .getMessage());
        assertEquals(1, new IndexFormats(List.of(1), Optional.empty()).readable().get(0));
        assertEquals(
                "an index format is listed twice: [5, 5]",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> new IndexFormats(List.of(5, 5), Optional.empty()))
                        .getMessage());
        assertEquals(
                "the source of the index formats is not an https:// reference: http://x",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> new IndexFormats(List.of(5), Optional.of("http://x")))
                        .getMessage());
        assertThrows(NullPointerException.class, () -> new IndexFormats(List.of(5), null));
    }

    @Test
    @DisplayName(
            "a version record keeps its formats through every copy, and states none by default")
    void recordCopies() {
        CometVersionRecord loaded =
                MetadataLoader.loadBundled().version(ToolVersion.parse("2026.03.0")).orElseThrow();
        IndexFormats formats = loaded.indexFormats();
        assertEquals(formats, loaded.withRuleSeverities(Map.of()).indexFormats());
        assertEquals(formats, loaded.withValueMigrations(List.of()).indexFormats());
        IndexFormats other = new IndexFormats(List.of(9), Optional.empty());
        CometVersionRecord changed = loaded.withIndexFormats(other);
        assertEquals(other, changed.indexFormats());
        assertEquals(loaded.ruleSeverities(), changed.ruleSeverities());
        assertEquals(loaded.valueMigrations(), changed.valueMigrations());
        assertEquals(loaded.overrides(), changed.overrides());
        assertEquals(loaded.variableModTuple(), changed.variableModTuple());
        assertEquals(loaded.marker(), changed.marker());
        assertEquals(loaded.parameterPages(), changed.parameterPages());
        assertEquals(loaded.source(), changed.source());
        assertEquals(loaded.version(), changed.version());
        CometVersionRecord plain =
                new CometVersionRecord(
                        loaded.version(),
                        loaded.marker(),
                        loaded.parameterPages(),
                        loaded.source(),
                        loaded.variableModTuple(),
                        loaded.overrides(),
                        loaded.ruleSeverities(),
                        loaded.valueMigrations());
        assertEquals(IndexFormats.unstated(), plain.indexFormats());
        assertThrows(
                NullPointerException.class,
                () ->
                        new CometVersionRecord(
                                loaded.version(),
                                loaded.marker(),
                                loaded.parameterPages(),
                                loaded.source(),
                                loaded.variableModTuple(),
                                loaded.overrides(),
                                loaded.ruleSeverities(),
                                loaded.valueMigrations(),
                                null));
    }
}
