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

package org.cometgui.app.testing;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.cometgui.install.download.ArtefactFetcher;
import org.cometgui.install.download.DownloadReport;
import org.cometgui.install.download.DownloadRequest;
import org.cometgui.install.download.HttpDownloader;

/**
 * A network that can be taken away and given back: the failure paths "network unavailable on first
 * install" and "network unavailable after tools are cached", at the seam production composes the
 * downloader at.
 *
 * <p>While it is up, every fetch goes to the {@code online} fetcher -- normally {@link
 * LoopbackArtefactServer}. While it is down, every fetch is sent by the <strong>real {@link
 * HttpDownloader}</strong> to a loopback port on which nothing listens, so it fails as an
 * unreachable network fails a scientist: a refused connection, through the product's own transfer
 * code, with nothing stubbed. Every manifest URL asked for while it is down is recorded, so
 * "nothing was fetched" is a count.
 *
 * <p><strong>A sibling of {@code org.cometgui.install.manager.SwitchableNetwork}</strong>, for the
 * reason {@link LoopbackArtefactServer} gives: cometgui-install's test tree is not published to
 * this module.
 */
public final class SwitchableNetwork implements ArtefactFetcher, AutoCloseable {

    private final ArtefactFetcher online;

    private final HttpDownloader downloader = new HttpDownloader();

    private final int deadPort;

    private final List<URI> whileDown = Collections.synchronizedList(new ArrayList<>());

    private volatile boolean down;

    private SwitchableNetwork(ArtefactFetcher online, boolean down) throws IOException {
        this.online = Objects.requireNonNull(online, "online");
        this.down = down;
        try (ServerSocket released = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            this.deadPort = released.getLocalPort();
        }
    }

    /**
     * A network that is down until {@link #restore()}.
     *
     * @param online where fetches go once it is up
     * @return the network, down
     * @throws IOException if no loopback port can be bound to find a free one
     */
    public static SwitchableNetwork downUntilRestored(ArtefactFetcher online) throws IOException {
        return new SwitchableNetwork(online, true);
    }

    /** Gives the network back: later fetches go to the online fetcher. */
    public void restore() {
        down = false;
    }

    /** Takes the network away: every later fetch is a refused connection. */
    public void cutOff() {
        down = true;
    }

    /**
     * The port every fetch goes to while the network is down.
     *
     * @return the port
     */
    public int deadPort() {
        return deadPort;
    }

    /**
     * Every manifest URL asked for while the network was down, in order, over every period it was
     * down.
     *
     * @return the URLs
     */
    public List<URI> askedForWhileDown() {
        synchronized (whileDown) {
            return List.copyOf(whileDown);
        }
    }

    @Override
    public DownloadReport fetch(DownloadRequest request) throws IOException {
        if (!down) {
            return online.fetch(request);
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
