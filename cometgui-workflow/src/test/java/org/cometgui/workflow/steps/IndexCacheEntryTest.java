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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.cometgui.domain.project.ProjectLayout;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** An index cache entry, without a Comet process. */
class IndexCacheEntryTest {

    @TempDir private Path directory;

    private IndexCacheEntry entry() {
        return IndexCacheEntry.of(new ProjectLayout(directory), "c".repeat(64), "db.fasta");
    }

    @Test
    @DisplayName("is index-cache/<key>/ with the link, the index and the marker in it")
    void paths() {
        IndexCacheEntry entry = entry();
        Path dir = directory.resolve("index-cache").resolve("c".repeat(64));
        assertEquals(dir, entry.directory());
        assertEquals(dir.resolve("db.fasta"), entry.link());
        assertEquals(dir.resolve("db.fasta.idx"), entry.indexFile());
        assertEquals(dir.resolve("index.complete"), entry.marker());
        assertEquals("index cache entry " + dir, entry.toString());
    }

    @Test
    @DisplayName("refuses a key that is not 64 lower-case hexadecimal characters")
    void keyShape() {
        for (String key : List.of("C".repeat(64), "c".repeat(63), "../" + "c".repeat(61))) {
            IllegalArgumentException refused =
                    assertThrows(
                            IllegalArgumentException.class,
                            () ->
                                    IndexCacheEntry.of(
                                            new ProjectLayout(directory), key, "db.fasta"));
            assertEquals(
                    "an index cache key is 64 lower-case hexadecimal characters, not \""
                            + key
                            + "\"",
                    refused.getMessage());
        }
    }

    @Test
    @DisplayName("is complete only with a well-formed marker and its index; written and cleared")
    void completion() throws IOException {
        IndexCacheEntry entry = entry();
        assertEquals(Optional.empty(), entry.completion());
        IndexCacheEntry.Completion done = new IndexCacheEntry.Completion("d".repeat(64), 1234);
        Files.createDirectories(entry.directory());
        entry.markComplete(done);
        assertEquals(
                "cometgui-index-cache 1\nsha256 " + "d".repeat(64) + "\nsize 1234\n",
                Files.readString(entry.marker()));
        assertEquals(Optional.empty(), entry.completion(), "no index yet");
        Files.writeString(entry.indexFile(), "index");
        Files.writeString(entry.link(), "a stand-in for the link");
        assertEquals(Optional.of(done), entry.completion());

        entry.clearIncomplete();
        assertFalse(Files.exists(entry.marker()));
        assertFalse(Files.exists(entry.indexFile()));
        assertTrue(Files.exists(entry.link()), "the link is kept");
        entry.clearIncomplete();
    }

    @Test
    @DisplayName("a marker's text is read exactly, and anything else is incomplete")
    void markerText() {
        String sha = "e".repeat(64);
        assertEquals(
                Optional.of(new IndexCacheEntry.Completion(sha, 0)),
                IndexCacheEntry.Completion.parse(
                        "cometgui-index-cache 1\nsha256 " + sha + "\nsize 0\n"));
        assertEquals(
                Optional.of(new IndexCacheEntry.Completion(sha, 9223372036854775807L)),
                IndexCacheEntry.Completion.parse(
                        "cometgui-index-cache 1\nsha256 " + sha + "\nsize 9223372036854775807\n"));
        for (String text :
                List.of(
                        "",
                        "cometgui-index-cache 1\nsha256 " + sha + "\nsize 5",
                        "cometgui-index-cache 2\nsha256 " + sha + "\nsize 5\n",
                        "cometgui-index-cache 1\nsha256 " + sha + "\nsize 5\n\n",
                        "cometgui-index-cache 1\nsha " + sha + "\nsize 5\n",
                        "cometgui-index-cache 1\nsha256 " + sha + "\nbytes 5\n",
                        "cometgui-index-cache 1\nsha256 " + "E".repeat(64) + "\nsize 5\n",
                        "cometgui-index-cache 1\nsha256 " + sha + "\nsize 05\n",
                        "cometgui-index-cache 1\nsha256 " + sha + "\nsize -5\n",
                        "cometgui-index-cache 1\nsha256 " + sha + "\nsize 9223372036854775808\n")) {
            assertEquals(Optional.empty(), IndexCacheEntry.Completion.parse(text), text);
        }
    }

    @Test
    @DisplayName("a completion refuses a malformed digest and a negative size")
    void completionValues() {
        assertEquals(
                "not a SHA-256: \"xyz\"",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> new IndexCacheEntry.Completion("xyz", 1))
                        .getMessage());
        assertEquals(
                "a size cannot be negative: -1",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> new IndexCacheEntry.Completion("f".repeat(64), -1))
                        .getMessage());
    }
}
