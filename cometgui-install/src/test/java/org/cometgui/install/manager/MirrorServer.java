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

package org.cometgui.install.manager;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.cometgui.install.download.LoopbackHttpServer;
import org.cometgui.install.registry.ArtefactCompanion;
import org.cometgui.install.registry.ArtefactRecord;

/**
 * The gitignored artefact mirror, served over loopback HTTP at the addresses the manifest's records
 * are rewritten to.
 *
 * <p><strong>What is real here and what is not.</strong> The bytes are the ones upstream publishes,
 * fetched by pinned URL and verified by SHA-256 into {@code scratch/phase05/artefacts}; the
 * transfer is a real HTTP transfer through the product's own {@code HttpDownloader}; the
 * verification is the product's own {@code R-SEC-02} check against the digest the shipped manifest
 * pins. The <em>only</em> thing swapped is the host part of the URL, so that the suite does not
 * depend on GitHub being reachable -- which is {@code R-TEST-08}'s subject and Phase 15's.
 *
 * <p>Streamed from the file rather than held in memory, because PDV's artefact is 103407417 bytes
 * and this class exists mainly so that a cancellation test has something large enough to cancel in
 * the middle of.
 *
 * <p><strong>{@code Range} is deliberately ignored</strong> and every request is answered with the
 * whole body: a server without range support is one of the two cases unit 3 built the downloader
 * for, it is the safe one, and this class is not where range handling is graded.
 */
final class MirrorServer implements AutoCloseable {

    /** Where the mirror lives, relative to the repository root. */
    static final String MIRROR = "scratch/phase05/artefacts";

    /** What one path serves, and whether the bytes are damaged on the way out. */
    private record Served(Path file, boolean corrupt) {}

    private final Map<String, Served> served = new ConcurrentHashMap<>();
    private final Map<URI, URI> rewritten = new ConcurrentHashMap<>();
    private final LoopbackHttpServer server;

    MirrorServer() throws IOException {
        this.server = new LoopbackHttpServer(this::respond);
    }

    /**
     * Serves a record's artefact and every companion it declares, unaltered.
     *
     * @param record the manifest record
     * @return this server
     */
    MirrorServer serving(ArtefactRecord record) {
        register(record.url(), mirrorFileOf(record.releaseTag(), record.url()), false);
        for (ArtefactCompanion companion : record.companions()) {
            register(companion.url(), mirrorFileOf(record.releaseTag(), companion.url()), false);
        }
        return this;
    }

    /**
     * Serves a record's artefact with its first byte flipped, and its companions unaltered.
     *
     * <p>The damage is one byte rather than a substitution of random bytes, and it is in the header
     * of the archive: a length that still matches and a digest that does not is the shape a
     * truncation or a re-tag has, and it proves the SHA-256 is what caught it rather than the size.
     *
     * @param record the manifest record
     * @return this server
     */
    MirrorServer servingCorrupted(ArtefactRecord record) {
        register(record.url(), mirrorFileOf(record.releaseTag(), record.url()), true);
        for (ArtefactCompanion companion : record.companions()) {
            register(companion.url(), mirrorFileOf(record.releaseTag(), companion.url()), false);
        }
        return this;
    }

    /**
     * The loopback address an upstream URL was rewritten to.
     *
     * @param upstream the URL the manifest pins
     * @return the loopback URL serving those bytes
     */
    URI addressOf(URI upstream) {
        URI local = rewritten.get(upstream);
        if (local == null) {
            throw new AssertionError(
                    "nothing was registered for "
                            + upstream
                            + "; call serving(record) for every record the test installs");
        }
        return local;
    }

    /**
     * Every request this server received, in order.
     *
     * @return the request log
     */
    java.util.List<LoopbackHttpServer.Request> requests() {
        return server.requests();
    }

    @Override
    public void close() throws IOException {
        server.close();
    }

    private void register(URI upstream, Path file, boolean corrupt) {
        String path =
                "/"
                        + Integer.toHexString(upstream.toString().hashCode())
                        + "/"
                        + file.getFileName();
        served.put(path, new Served(file, corrupt));
        rewritten.put(upstream, server.uri(path));
    }

    private void respond(LoopbackHttpServer.Request request, OutputStream out) throws IOException {
        Served target = served.get(request.path());
        if (target == null) {
            LoopbackHttpServer.head(out, 404, "Not Found", "Content-Length: 0");
            return;
        }
        long length = Files.size(target.file());
        LoopbackHttpServer.head(out, 200, "OK", "Content-Length: " + length);
        try (InputStream in = Files.newInputStream(target.file())) {
            byte[] buffer = new byte[65536];
            boolean first = true;
            int read;
            while ((read = in.read(buffer)) >= 0) {
                if (first && target.corrupt() && read > 0) {
                    buffer[0] ^= 0x5a;
                    first = false;
                }
                out.write(buffer, 0, read);
            }
        }
    }

    /**
     * The mirror file holding one upstream URL's bytes.
     *
     * <p>Named {@code <releaseTag>__<file name>}, which is the convention the survey script that
     * populated the mirror used. <strong>Fails rather than skips when it is not there</strong>: a
     * suite that quietly stops reading the real artefacts stops proving anything, and the message
     * says how to refill it.
     *
     * @param releaseTag the upstream release tag
     * @param url the URL the manifest pins
     * @return the file
     */
    static Path mirrorFileOf(String releaseTag, URI url) {
        String path = url.getPath();
        String name = path.substring(path.lastIndexOf('/') + 1);
        Path file = repositoryRoot().resolve(MIRROR).resolve(releaseTag + "__" + name);
        if (!Files.isRegularFile(file)) {
            throw new AssertionError(
                    "the real artefact \""
                            + name
                            + "\" of release "
                            + releaseTag
                            + " is not in the mirror at "
                            + file
                            + ". The mirror is gitignored and holds the bytes upstream publishes;"
                            + " refill it by fetching each artefact from the URL in"
                            + " manifests/tools.json and checking its SHA-256 before use. This test"
                            + " fails rather than skips, because a Tool Manager suite that stops"
                            + " installing the real artefacts stops proving anything.");
        }
        return file;
    }

    /**
     * The repository root, found by walking up to the directory holding {@code manifests}.
     *
     * @return the root
     */
    static Path repositoryRoot() {
        Path cursor = Path.of("").toAbsolutePath();
        while (cursor != null && !Files.isDirectory(cursor.resolve("manifests"))) {
            cursor = cursor.getParent();
        }
        if (cursor == null) {
            throw new AssertionError(
                    "no repository root above "
                            + Path.of("").toAbsolutePath()
                            + " holds a manifests directory");
        }
        return cursor;
    }
}
