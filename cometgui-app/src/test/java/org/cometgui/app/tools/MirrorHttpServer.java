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

package org.cometgui.app.tools;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.cometgui.install.registry.ArtefactCompanion;
import org.cometgui.install.registry.ArtefactRecord;

/**
 * The gitignored artefact mirror, served over loopback HTTP so that this module's end-to-end test
 * fetches the real bytes through the product's own downloader.
 *
 * <p><strong>Why this is a second small server and not {@code
 * org.cometgui.install.download.LoopbackHttpServer}.</strong> That one is unit 3's and lives in
 * {@code cometgui-install}'s <em>test</em> sources, which no other module can see: Maven publishes
 * a module's main classes to its dependents and not its test classes, and adding a test-jar to a
 * signed-off module's POM to share it would be a larger change than this file. The duplication is
 * deliberately kept small: this one answers {@code 200} with a {@code Content-Length} and streams a
 * file, and does nothing else. Every pathological response -- a short body, a lying {@code
 * Content-Range}, a server that ignores {@code Range} -- is unit 3's subject and is graded there
 * against that server; nothing here is a second statement of any of it.
 *
 * <p><strong>What stays real.</strong> The bytes are the ones upstream publishes, verified into
 * {@code scratch/phase05/artefacts} by pinned URL and SHA-256; the client is the product's own
 * {@code HttpDownloader}; the verification is the product's own {@code R-SEC-02} check against the
 * digest {@code manifests/tools.json} pins. Only the host part of the URL is rewritten, so that the
 * suite does not depend on GitHub being reachable -- which is {@code R-TEST-08} and Phase 15's.
 *
 * <p>Streamed rather than held in memory: PDV's artefact is 103407417 bytes.
 */
final class MirrorHttpServer implements AutoCloseable {

    /** Where the mirror lives, relative to the repository root. */
    static final String MIRROR = "scratch/phase05/artefacts";

    private final Map<String, Path> served = new ConcurrentHashMap<>();
    private final java.util.Set<String> corrupt = ConcurrentHashMap.newKeySet();
    private final Map<URI, URI> rewritten = new ConcurrentHashMap<>();
    private final ServerSocket socket;
    private final Thread thread;

    MirrorHttpServer() throws IOException {
        this.socket = new ServerSocket(0, 16, InetAddress.getLoopbackAddress());
        this.thread = new Thread(this::acceptLoop, "mirror-http-server");
        this.thread.setDaemon(true);
        this.thread.start();
    }

    /**
     * Serves a record's artefact and every companion it declares.
     *
     * @param record the manifest record
     * @return this server
     */
    MirrorHttpServer serving(ArtefactRecord record) {
        register(record.url(), mirrorFileOf(record.releaseTag(), record.url()));
        for (ArtefactCompanion companion : record.companions()) {
            register(companion.url(), mirrorFileOf(record.releaseTag(), companion.url()));
        }
        return this;
    }

    /**
     * Serves a record's artefact with its first byte flipped, and its companions unaltered.
     *
     * <p>One byte rather than random bytes, and in the archive's header: the length still matches,
     * so only the SHA-256 can have caught it.
     *
     * @param record the manifest record
     * @return this server
     */
    MirrorHttpServer servingCorrupted(ArtefactRecord record) {
        serving(record);
        corrupt.add(pathFor(record.url()));
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

    @Override
    public void close() throws IOException {
        socket.close();
        try {
            thread.join(5_000);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private void register(URI upstream, Path file) {
        String path = pathFor(upstream);
        served.put(path, file);
        rewritten.put(upstream, URI.create("http://127.0.0.1:" + socket.getLocalPort() + path));
    }

    private static String pathFor(URI upstream) {
        return "/" + Integer.toHexString(upstream.toString().hashCode());
    }

    private void acceptLoop() {
        while (!socket.isClosed()) {
            try (Socket connection = socket.accept()) {
                connection.setTcpNoDelay(true);
                String path = readRequestTarget(connection.getInputStream());
                if (path != null) {
                    respond(path, connection.getOutputStream());
                    connection.getOutputStream().flush();
                }
            } catch (IOException closedOrCancelled) {
                /*
                 * A cancelled transfer closes the connection under this thread, which is normal
                 * and is not the test's subject; a closed server socket ends the loop.
                 */
                if (socket.isClosed()) {
                    return;
                }
            }
        }
    }

    private void respond(String path, OutputStream out) throws IOException {
        Path file = served.get(path);
        if (file == null) {
            write(out, "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n");
            return;
        }
        write(
                out,
                "HTTP/1.1 200 OK\r\nContent-Length: "
                        + Files.size(file)
                        + "\r\nConnection: close\r\n\r\n");
        try (InputStream in = Files.newInputStream(file)) {
            if (!corrupt.contains(path)) {
                in.transferTo(out);
                return;
            }
            byte[] buffer = new byte[65536];
            boolean first = true;
            int read;
            while ((read = in.read(buffer)) >= 0) {
                if (first && read > 0) {
                    buffer[0] ^= 0x5a;
                    first = false;
                }
                out.write(buffer, 0, read);
            }
        }
    }

    private static void write(OutputStream out, String text) throws IOException {
        out.write(text.getBytes(StandardCharsets.ISO_8859_1));
    }

    private static String readRequestTarget(InputStream in) throws IOException {
        ByteArrayOutputStream head = new ByteArrayOutputStream();
        int newlines = 0;
        int read;
        while ((read = in.read()) != -1) {
            head.write(read);
            if (read == '\n') {
                newlines++;
                if (newlines == 2) {
                    break;
                }
            } else if (read != '\r') {
                newlines = 0;
            }
        }
        String text = head.toString(StandardCharsets.ISO_8859_1);
        if (text.isBlank()) {
            return null;
        }
        String[] start = text.split("\r\n")[0].split(" ");
        return start.length < 2 ? null : start[1];
    }

    /**
     * The mirror file holding one upstream URL's bytes, named {@code <releaseTag>__<file name>}.
     *
     * <p><strong>Fails rather than skips when it is not there</strong>: a suite that quietly stops
     * installing the real artefacts stops proving anything.
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
                            + " manifests/tools.json and checking its SHA-256 before use.");
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
