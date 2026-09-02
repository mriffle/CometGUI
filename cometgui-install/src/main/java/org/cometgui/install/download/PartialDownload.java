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

package org.cometgui.install.download;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The two files a transfer in flight owns: the bytes so far, and what is known about the thing they
 * came from.
 *
 * <p>The bytes go to {@code <destination>.part} and the destination is created only by an atomic
 * move once the transfer is complete, which is step one of the specification's atomic install
 * ("download to a temporary file"). Nothing ever sees a half-written artefact at the path an
 * installer will read.
 *
 * <h2>Why there is a state file at all</h2>
 *
 * <p>Resuming means sending {@code Range: bytes=N-} and trusting that the first N bytes on disk
 * belong to the same file the rest will come from. HTTP has a mechanism for exactly that -- {@code
 * If-Range} -- and <strong>it does not work on the host this product downloads from</strong>: the
 * phase's measurements against GitHub's release-asset host recorded a deliberately stale validator
 * being answered with {@code 206} and the partial range, not {@code 200} and the whole body,
 * through the redirect and again directly against the signed URL. A client relying on it would
 * splice bytes from two different files and no HTTP status would say so.
 *
 * <p>So the length and the {@code ETag} seen on the first attempt are written here, and a resume
 * that sees either of them change discards the partial file and starts again. That check is
 * <strong>advisory</strong>: an ETag is not required to be stable, and a server may re-tag an
 * artefact without changing either value. The mandatory SHA-256 verification is what actually
 * catches a spliced download ({@code R-SEC-02}), and {@link
 * org.cometgui.install.verify.VerifiedDownloader} is where the recovery from one lives.
 *
 * <h2>No URL is stored</h2>
 *
 * <p>A release download redirects to a signed URL that expires in about an hour, so a resume must
 * re-request the original URL for a fresh signature. Storing the redirect target would produce a
 * resume that works for an hour and then fails for a reason nobody can see, which is why this file
 * holds a length and a validator and nothing else.
 */
final class PartialDownload {

    /** Appended to the destination to name the file the bytes are written to. */
    static final String PART_SUFFIX = ".part";

    /** Appended to the destination to name the file the resume state is written to. */
    static final String STATE_SUFFIX = ".part.state";

    /** First line of a state file: format name and version, so an old one is rejected, not read. */
    static final String STATE_MAGIC = "cometgui-download-state 1";

    /** Key of the declared total length line. */
    private static final String TOTAL_KEY = "total=";

    /** Key of the entity-tag line; its value is empty when the server sent no {@code ETag}. */
    private static final String ETAG_KEY = "etag=";

    /** Where the bytes go. */
    private final Path file;

    /** Where the length and the validator go. */
    private final Path state;

    private PartialDownload(Path file, Path state) {
        this.file = file;
        this.state = state;
    }

    /**
     * Names the partial file and the state file that belong to one destination.
     *
     * @param destination the file the finished download will be moved to
     * @return the pair, which may or may not exist on disk yet
     */
    static PartialDownload beside(Path destination) {
        Path name = Objects.requireNonNull(destination, "destination").getFileName();
        return new PartialDownload(
                destination.resolveSibling(name + PART_SUFFIX),
                destination.resolveSibling(name + STATE_SUFFIX));
    }

    /**
     * Where the bytes are written.
     *
     * @return the partial file's path
     */
    Path file() {
        return file;
    }

    /**
     * Where the resume state is written.
     *
     * @return the state file's path
     */
    Path stateFile() {
        return state;
    }

    /**
     * Deletes both files if they exist.
     *
     * @throws IOException if either cannot be deleted
     */
    void discard() throws IOException {
        Files.deleteIfExists(file);
        Files.deleteIfExists(state);
    }

    /**
     * Where a resumed transfer would continue from, if it may continue at all.
     *
     * <p>Empty unless the partial file exists, holds at least one byte, and is accompanied by a
     * state file this version wrote and can still parse. A partial file with no usable state is
     * bytes of unknown provenance, so it is not resumed: the caller discards it.
     *
     * @return the resume point, or empty when there is nothing safe to continue
     * @throws IOException if the files exist but cannot be read
     */
    Optional<ResumePoint> resumePoint() throws IOException {
        if (!Files.isRegularFile(file) || !Files.isRegularFile(state)) {
            return Optional.empty();
        }
        long offset = Files.size(file);
        if (offset <= 0) {
            return Optional.empty();
        }
        return readState()
                .map(recorded -> new ResumePoint(offset, recorded.total(), recorded.etag()));
    }

    /**
     * Records what the server said about the artefact, so the next attempt can notice a change.
     *
     * @param totalBytes the total length the server declared, or a negative number for none
     * @param etag the {@code ETag} header, or the empty string when the server sent none
     * @throws IOException if the state file cannot be written
     */
    void recordState(long totalBytes, String etag) throws IOException {
        Objects.requireNonNull(etag, "etag");
        Files.write(
                state,
                List.of(STATE_MAGIC, TOTAL_KEY + totalBytes, ETAG_KEY + etag),
                StandardCharsets.UTF_8);
    }

    /**
     * Moves the finished bytes onto the destination and deletes the state file.
     *
     * @param destination the file to create
     * @throws IOException if the move fails
     */
    void moveInto(Path destination) throws IOException {
        // ATOMIC_MOVE, with no fallback. The partial file is created beside the destination by
        // construction, so this is always a rename within one directory, which every file system
        // this product supports performs atomically. A fallback to a non-atomic move would be a
        // branch nothing could reach and nothing could test.
        Files.move(
                file,
                destination,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
        Files.deleteIfExists(state);
    }

    private Optional<RecordedState> readState() throws IOException {
        List<String> lines = Files.readAllLines(state, StandardCharsets.UTF_8);
        if (lines.size() != 3
                || !STATE_MAGIC.equals(lines.get(0))
                || !lines.get(1).startsWith(TOTAL_KEY)
                || !lines.get(2).startsWith(ETAG_KEY)) {
            return Optional.empty();
        }
        long total;
        try {
            total = Long.parseLong(lines.get(1).substring(TOTAL_KEY.length()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
        return Optional.of(new RecordedState(total, lines.get(2).substring(ETAG_KEY.length())));
    }

    /** What the state file held. */
    private record RecordedState(long total, String etag) {}

    /**
     * Where a resume would start, and what the first attempt was told about the artefact.
     *
     * @param offsetBytes how many bytes are already on disk
     * @param declaredTotalBytes the total length the first attempt was given, or negative for none
     * @param etag the {@code ETag} the first attempt saw, or the empty string for none
     */
    record ResumePoint(long offsetBytes, long declaredTotalBytes, String etag) {}
}
