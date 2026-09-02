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

/**
 * Getting the bytes of a managed artefact from upstream onto disk -- and nothing more than that.
 *
 * <p>{@link org.cometgui.install.download.HttpDownloader} implements {@link
 * org.cometgui.domain.ports.Downloader} over {@code java.net.http}: HTTPS only, redirects followed,
 * progress reported, cancellation honoured, resume or clean restart, timeouts on connect, on
 * headers and on a stalled read, and a download that reaches its destination only by one move at
 * the end.
 *
 * <p><strong>Nothing here decides whether a file is trustworthy.</strong> That is {@link
 * org.cometgui.install.verify}, and the separation is what lets verification be tested against a
 * deliberately corrupted download. The one checksum this package touches is the expected SHA-256
 * carried on a {@link org.cometgui.install.download.DownloadRequest}, which is put into the {@code
 * D-008} availability message and is never compared to anything.
 *
 * <h2>Three measured facts this package is designed around</h2>
 *
 * <ul>
 *   <li>A GitHub release URL answers {@code 302} with {@code content-length: 0} and redirects to a
 *       signed URL that expires in about an hour. {@code HttpClient} does <em>not</em> follow
 *       redirects by default, so a downloader that leaves the policy alone writes a zero-byte file
 *       and reports success -- and a resume must re-request the original URL for a fresh signature
 *       rather than reuse a stored redirect target, which is why nothing here ever stores a URL.
 *   <li>Range requests work: {@code accept-ranges: bytes}, {@code 206}, a {@code content-range} and
 *       an {@code ETag}. Resume is real.
 *   <li>{@code If-Range} is <em>ignored</em>. A stale validator is still answered {@code 206} with
 *       the partial range, so the standard "tell me if the file changed under my partial download"
 *       mechanism does not work against this host. The length and the {@code ETag} are recorded
 *       instead and a change in either restarts the download -- advisory only; {@code R-SEC-02}'s
 *       SHA-256 is the sole integrity authority.
 * </ul>
 *
 * <h2>Testing against a real server without the network</h2>
 *
 * <p>The routine suite serves real artefact bytes over a loopback HTTP server, which is why {@code
 * DownloadUrls} permits plain HTTP to a loopback address <em>literal</em> and to nothing else:
 * those packets never reach a network interface, so there is no intermediary for the HTTPS rule to
 * protect against. It is unreachable from product data -- every URL in {@code manifests/tools.json}
 * is refused by the registry unless it is https. One opt-in test fetches a real artefact from its
 * real URL; it is skipped unless {@code -Dcometgui.install.upstream=true} is given, so the ordinary
 * build never depends on reaching GitHub.
 */
package org.cometgui.install.download;
