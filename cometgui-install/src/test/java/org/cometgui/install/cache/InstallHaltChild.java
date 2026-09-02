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
 * A second JVM that runs a real install and then dies part way through, without unwinding.
 *
 * <p>{@link Runtime#halt(int)} rather than an exception or {@link System#exit(int)}: no {@code
 * finally}, no shutdown hook, no {@link InstallPipeline#close()}. That is what a power cut does,
 * and it is the case {@code R-TOOL-04} is written for -- <em>"interrupted installations shall be
 * safely discarded or resumed and shall never leave a tool that appears installed but is
 * incomplete"</em>. An in-process test that returned early would run the pipeline's own cleanup and
 * would therefore prove something weaker than the rule claims.
 *
 * <p>It runs the install through {@link ArtefactInstaller#begin}, which is the same lock, the same
 * recovery decision and the same eight actions {@link ArtefactInstaller#install} runs; it simply
 * stops after the number of steps it was told to.
 *
 * <p>Arguments: the cache root, the fixture directory, and how many steps to run.
 */
public final class InstallHaltChild {

    private InstallHaltChild() {}

    /**
     * Runs the child.
     *
     * @param args the cache root, the fixture directory, and the number of steps to run
     * @throws IOException if the cache or the fixture cannot be read or written
     * @throws InterruptedException if the process is interrupted while it works
     */
    public static void main(String[] args) throws IOException, InterruptedException {
        Path root = Path.of(args[0]);
        Path fixture = Path.of(args[1]);
        int stepsToRun = Integer.parseInt(args[2]);
        InstallHarness harness = InstallHarness.at(root);
        ArtefactRecord record = CacheFixtures.sharedRecord(fixture);
        CacheFixtures.serveSharedFixture(harness.fetcher(), record, fixture);
        InstallPipeline pipeline =
                harness.installer().begin(record, harness.listener(), DownloadCancellation.never());
        for (int step = 0; step < stepsToRun; step++) {
            System.out.println("ran=" + pipeline.runNextStep());
        }
        System.out.println("halting-after=" + pipeline.executedSteps().size());
        System.out.flush();
        /*
         * NO UNWINDING.  halt skips every finally block, every shutdown hook and this pipeline's
         * own close(), so the staging directory stays where it is, the download cache stays where
         * it is, and the file lock is released by the operating system rather than by this program.
         */
        Runtime.getRuntime().halt(17);
    }
}
