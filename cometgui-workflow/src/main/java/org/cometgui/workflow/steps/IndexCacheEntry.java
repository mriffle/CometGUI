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
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import org.cometgui.domain.project.ProjectLayout;
import org.cometgui.provenance.io.AtomicDocumentWriter;
import org.cometgui.tools.comet.CometIndexCommand;

/**
 * One entry of a project's Comet index cache: {@code index-cache/<key>/} (design decision P8-8).
 *
 * <pre>
 *     index-cache/&lt;key&gt;/
 *         &lt;fasta name&gt;            a symbolic link to the FASTA, which Comet's -D names
 *         &lt;fasta name&gt;.idx        the index Comet writes beside that link
 *         index.complete          written last: the index's SHA-256 and size
 * </pre>
 *
 * <p><strong>Complete or absent, never half.</strong> Comet writes the index's text header first
 * and its body after, so an index whose build was cancelled or failed has a header that reads as
 * valid over a truncated body. An entry is therefore {@linkplain #completion() complete} only when
 * its marker exists -- written atomically, after the build exited zero and its header was judged --
 * and a reuse re-hashes the index against the SHA-256 the marker records ({@code R-RUN-02}, P8-14).
 * A build into an entry first removes an index and a marker left there by an earlier build that did
 * not finish ({@link #clearIncomplete()}).
 */
public final class IndexCacheEntry {

    /** The completion marker's file name. */
    public static final String MARKER_FILE_NAME = "index.complete";

    /** The marker's first line: its format's name and version. */
    static final String MARKER_FORMAT = "cometgui-index-cache 1";

    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    private static final Pattern SIZE = Pattern.compile("0|[1-9][0-9]{0,18}");

    private final Path directory;

    private final String fastaName;

    private IndexCacheEntry(Path directory, String fastaName) {
        this.directory = directory;
        this.fastaName = fastaName;
    }

    /**
     * The entry of one key in a project's cache. Touches nothing.
     *
     * @param project the project
     * @param key the entry's key ({@link IndexCacheKey#of})
     * @param fastaName the FASTA's file name, which names the link and the index
     * @return the entry
     * @throws NullPointerException if an argument is {@code null}
     * @throws IllegalArgumentException if the key is not 64 lower-case hexadecimal characters
     */
    public static IndexCacheEntry of(ProjectLayout project, String key, String fastaName) {
        Objects.requireNonNull(project, "project");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(fastaName, "fastaName");
        if (!SHA256.matcher(key).matches()) {
            throw new IllegalArgumentException(
                    "an index cache key is 64 lower-case hexadecimal characters, not \""
                            + key
                            + "\"");
        }
        return new IndexCacheEntry(project.indexCacheDirectory().resolve(key), fastaName);
    }

    /**
     * The entry's directory.
     *
     * @return {@code index-cache/<key>}
     */
    public Path directory() {
        return directory;
    }

    /**
     * The symbolic link to the FASTA that the index build's {@code -D} names.
     *
     * @return {@code index-cache/<key>/<fasta name>}
     */
    public Path link() {
        return directory.resolve(fastaName);
    }

    /**
     * The index, which the search's {@code -D} names.
     *
     * @return {@code index-cache/<key>/<fasta name>.idx}
     */
    public Path indexFile() {
        return directory.resolve(fastaName + CometIndexCommand.INDEX_SUFFIX);
    }

    /**
     * The completion marker.
     *
     * @return {@code index-cache/<key>/index.complete}
     */
    public Path marker() {
        return directory.resolve(MARKER_FILE_NAME);
    }

    /**
     * What the marker records, if the entry is complete: the marker is well formed and the index it
     * describes exists. The index is not read here; a reuse re-hashes it against the result.
     *
     * @return the recorded SHA-256 and size, or empty if the entry is absent or incomplete
     * @throws IOException if the marker exists but cannot be read
     */
    public Optional<Completion> completion() throws IOException {
        if (!Files.isRegularFile(marker()) || !Files.isRegularFile(indexFile())) {
            return Optional.empty();
        }
        return Completion.parse(Files.readString(marker(), StandardCharsets.UTF_8));
    }

    /**
     * Records the entry as complete, atomically.
     *
     * @param completion the index's SHA-256 and size, computed after Comet closed it
     * @throws IOException if the marker cannot be written
     */
    void markComplete(Completion completion) throws IOException {
        AtomicDocumentWriter.write(marker(), completion.text());
    }

    /**
     * Removes the index and the marker an unfinished build may have left, and nothing else.
     *
     * @throws IOException if one exists and cannot be removed
     */
    void clearIncomplete() throws IOException {
        Files.deleteIfExists(marker());
        Files.deleteIfExists(indexFile());
    }

    @Override
    public String toString() {
        return "index cache entry " + directory;
    }

    /**
     * What a complete entry's marker records.
     *
     * @param sha256 the index's SHA-256
     * @param size the index's size in bytes
     */
    public record Completion(String sha256, long size) {

        /**
         * Validates.
         *
         * @throws NullPointerException if the digest is {@code null}
         * @throws IllegalArgumentException if the digest is malformed or the size negative
         */
        public Completion {
            Objects.requireNonNull(sha256, "sha256");
            if (!SHA256.matcher(sha256).matches()) {
                throw new IllegalArgumentException("not a SHA-256: \"" + sha256 + "\"");
            }
            if (size < 0) {
                throw new IllegalArgumentException("a size cannot be negative: " + size);
            }
        }

        /**
         * The marker's text.
         *
         * @return three lines: the format, {@code sha256 <hex>}, {@code size <bytes>}
         */
        String text() {
            return MARKER_FORMAT + "\nsha256 " + sha256 + "\nsize " + size + "\n";
        }

        /**
         * Reads a marker's text.
         *
         * @param text the marker's text
         * @return the completion, or empty if the text is not exactly a marker's
         */
        static Optional<Completion> parse(String text) {
            List<String> lines = List.of(text.split("\n", -1));
            if (lines.size() != 4
                    || !MARKER_FORMAT.equals(lines.get(0))
                    || !lines.get(1).startsWith("sha256 ")
                    || !lines.get(2).startsWith("size ")
                    || !lines.get(3).isEmpty()) {
                return Optional.empty();
            }
            String sha256 = lines.get(1).substring("sha256 ".length());
            String size = lines.get(2).substring("size ".length());
            if (!SHA256.matcher(sha256).matches() || !SIZE.matcher(size).matches()) {
                return Optional.empty();
            }
            try {
                return Optional.of(new Completion(sha256, Long.parseLong(size)));
            } catch (NumberFormatException beyondLong) {
                return Optional.empty();
            }
        }
    }
}
