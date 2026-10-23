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
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.cometgui.install.download.ArtefactFetcher;
import org.cometgui.install.download.DownloadReport;
import org.cometgui.install.download.DownloadRequest;
import org.cometgui.install.download.HttpDownloader;

/**
 * A network that can be taken away: the failure paths "network unavailable on first install" and
 * "network unavailable after tools are cached".
 *
 * <p>While it is up, every fetch goes to the {@code online} fetcher -- normally {@link
 * ServedArtefacts}. Once {@link #cutOff()} is called, or from the start when there is no online
 * fetcher, every fetch is sent by the <strong>real {@link HttpDownloader}</strong> to a loopback
 * port on which nothing listens, so it fails the way an unreachable network fails a scientist: a
 * refused connection, through the product's own transfer code, with nothing stubbed. Every manifest
 * URL asked for while the network is down is recorded, so "nothing was fetched" is a count.
 *
 * <p>The dead port is one this class bound and released; nothing in these tests listens on it
 * again.
 */
final class SwitchableNetwork implements ArtefactFetcher, AutoCloseable {

    private final Optional<ArtefactFetcher> online;

    private final HttpDownloader downloader = new HttpDownloader();

    private final int deadPort;

    private final List<URI> whileDown = Collections.synchronizedList(new ArrayList<>());

    private volatile boolean down;

    private SwitchableNetwork(Optional<ArtefactFetcher> online, boolean down) throws IOException {
        this.online = online;
        this.down = down;
        try (ServerSocket released = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            this.deadPort = released.getLocalPort();
        }
    }

    /**
     * A network that was never there.
     *
     * @return the network, down from the start
     * @throws IOException if no loopback port can be bound to find a free one
     */
    static SwitchableNetwork unavailable() throws IOException {
        return new SwitchableNetwork(Optional.empty(), true);
    }

    /**
     * A network that is up until {@link #cutOff()}.
     *
     * @param online where fetches go while it is up
     * @return the network
     * @throws IOException if no loopback port can be bound to find a free one
     */
    static SwitchableNetwork upUntilCut(ArtefactFetcher online) throws IOException {
        return new SwitchableNetwork(Optional.of(Objects.requireNonNull(online, "online")), false);
    }

    /** Takes the network away; every later fetch is a refused connection. */
    void cutOff() {
        down = true;
    }

    /**
     * The port every fetch goes to while the network is down.
     *
     * @return the port
     */
    int deadPort() {
        return deadPort;
    }

    /**
     * Every manifest URL asked for while the network was down, in order.
     *
     * @return the URLs
     */
    List<URI> askedForWhileDown() {
        synchronized (whileDown) {
            return List.copyOf(whileDown);
        }
    }

    @Override
    public DownloadReport fetch(DownloadRequest request) throws IOException {
        if (!down) {
            return online.orElseThrow().fetch(request);
        }
        whileDown.add(request.source());
        return downloader.fetch(
                new DownloadRequest(
                        URI.create("http://127.0.0.1:" + deadPort + request.source().getPath()),
                        request.destination(),
                        request.listener(),
                        request.cancellation(),
                        request.resume(),
                        request.expectedSha256()));
    }

    @Override
    public void close() {
        downloader.close();
    }
}
