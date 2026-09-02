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

package org.cometgui.install.cache;

import java.io.IOException;
import java.nio.file.Path;
import org.cometgui.install.download.DownloadCancellation;
import org.cometgui.install.registry.ArtefactRecord;

/**
 * A second JVM that runs a whole install of the shared fixture and says what it did.
 *
 * <p>Two of these, started together against one cache root, are what {@code R-TOOL-05} is about:
 * <em>"concurrent installation of the same artefact by two CometGUI processes shall be serialised
 * by a lock file or shall be made idempotent; a partially written cache entry shall never be
 * observed as complete."</em> Exactly one of them should do the work; the other should wait, find a
 * complete entry and return it.
 *
 * <p>Each transfer is made deliberately slow so that the two really do overlap in time. Without
 * that the test could pass because the first install finished before the second started, which
 * would prove nothing about the lock.
 *
 * <p>Arguments: the cache root, the fixture directory, and how long each transfer takes.
 */
public final class InstallRaceChild {

    private InstallRaceChild() {}

    /**
     * Runs the child.
     *
     * @param args the cache root, the fixture directory, and the per-transfer delay in milliseconds
     * @throws IOException if the cache or the fixture cannot be read or written
     * @throws InterruptedException if the process is interrupted while it works
     */
    public static void main(String[] args) throws IOException, InterruptedException {
        Path root = Path.of(args[0]);
        Path fixture = Path.of(args[1]);
        long transferMillis = Long.parseLong(args[2]);
        InstallHarness harness = InstallHarness.at(root);
        ArtefactRecord record = CacheFixtures.sharedRecord(fixture);
        CacheFixtures.serveSharedFixture(harness.fetcher(), record, fixture);
        harness.fetcher()
                .before(
                        () -> {
                            try {
                                Thread.sleep(transferMillis);
                            } catch (InterruptedException interrupted) {
                                Thread.currentThread().interrupt();
                            }
                        });
        long startedAt = System.currentTimeMillis();
        Installation installation =
                harness.installer()
                        .install(record, harness.listener(), DownloadCancellation.never());
        System.out.println("alreadyInstalled=" + installation.alreadyInstalled());
        System.out.println("fetches=" + harness.fetcher().requested().size());
        System.out.println("elapsedMillis=" + (System.currentTimeMillis() - startedAt));
        System.out.println("state=" + harness.verify(record).state());
        System.out.println("marker=" + installation.marker().installedAtUtc());
    }
}
