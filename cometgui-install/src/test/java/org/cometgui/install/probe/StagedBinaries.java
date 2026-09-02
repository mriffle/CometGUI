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

package org.cometgui.install.probe;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.cometgui.install.archive.ArtefactMirror;

/**
 * The real Percolator binaries this phase extracted from the real upstream artefacts.
 *
 * <p><strong>They fail rather than skip when they are absent.</strong> A probe suite that quietly
 * stops running real binaries stops proving anything, which is the same rule {@link ArtefactMirror}
 * states for the archives they came out of -- and this class uses that one rather than growing a
 * second way to find the repository root.
 *
 * <p><strong>Every binary is checked against a pinned digest before it is run.</strong> Two of the
 * three are checked against the <em>shipped manifest's own</em> {@code memberSha256}, so the
 * fixture is provably the byte sequence the product would install rather than something that
 * happens to be lying in a scratch directory. The 3.09 Debian payload has no manifest row --
 * Percolator 3.09 publishes no Linux portable archive, and the honest entry is its absence -- so
 * its digest is hand-typed here from the extraction unit 0 performed.
 */
final class StagedBinaries {

    /** Where the phase's extracted payloads live, relative to the repository root. */
    static final String EXTRACT = "scratch/phase05/extract";

    /** SHA-256 of the 3.09 Debian payload's {@code usr/bin/percolator}, hand-typed. */
    static final String PAYLOAD_309_SHA256 =
            "1f067b5d438a3a88be8a88f636844baea824e239fd2c5c053462ae56fd0e7c15";

    /** Size in bytes of that payload, hand-typed. */
    static final long PAYLOAD_309_SIZE = 1786248L;

    private StagedBinaries() {}

    /**
     * The Percolator 3.07.1 portable binary, which runs on this host.
     *
     * @return the binary
     */
    static Path percolator3071() {
        return staged("perc-3.07.1/percolator");
    }

    /**
     * The Percolator 3.06.5 portable binary, which also runs on this host.
     *
     * @return the binary
     */
    static Path percolator3065() {
        return staged("perc-3.06.5/percolator");
    }

    /**
     * The Percolator 3.09 Debian payload, which does <strong>not</strong> load on this host.
     *
     * @return the binary
     */
    static Path payload309() {
        return staged("deb-3.09/usr/bin/percolator");
    }

    /**
     * The directory holding the stub {@code libboost_filesystem.so.1.83.0} that gets past the
     * missing-object failure and exposes the symbol-version failure beneath it.
     *
     * @return the directory
     */
    static Path stubLibraryDirectory() {
        Path directory = ArtefactMirror.repositoryRoot().resolve("scratch/phase05/stublib");
        Path library = directory.resolve("libboost_filesystem.so.1.83.0");
        if (!Files.isRegularFile(library)) {
            throw new AssertionError(
                    "the stub library is not at "
                            + library
                            + ". It is a gitignored working file built from"
                            + " scratch/phase05/stublib/stub.c, and it is what exposes the second"
                            + " layer of the R-PLAT-03 failure. This test fails rather than skips,"
                            + " because a probe suite that stops running real binaries stops"
                            + " proving anything.");
        }
        return directory;
    }

    private static Path staged(String relative) {
        Path file = ArtefactMirror.repositoryRoot().resolve(EXTRACT).resolve(relative);
        if (!Files.isRegularFile(file)) {
            throw new AssertionError(
                    "the extracted binary \""
                            + relative
                            + "\" is not at "
                            + file
                            + ". It is gitignored and is taken out of the real upstream artefact in"
                            + " "
                            + ArtefactMirror.MIRROR
                            + "; refill it by extracting the artefact named in"
                            + " manifests/tools.json and checking its SHA-256 before use. This test"
                            + " fails rather than skips.");
        }
        return file;
    }

    /**
     * Copies a binary into a staged install directory, at the path the manifest says it is
     * installed to, and makes it executable -- the state install step 6 hands the probe.
     *
     * @param binary the binary to stage
     * @param stagedDirectory the directory to lay it out in
     * @param installedPath its path relative to that directory, as the manifest declares it
     * @return the staged executable
     * @throws IOException if it cannot be copied
     */
    static Path stage(Path binary, Path stagedDirectory, String installedPath) throws IOException {
        Path destination = stagedDirectory.resolve(installedPath);
        Path parent = destination.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.copy(binary, destination, StandardCopyOption.REPLACE_EXISTING);
        if (!destination.toFile().setExecutable(true, true)) {
            throw new IOException("could not make " + destination + " executable");
        }
        return destination;
    }

    /**
     * Asserts that a staged binary is the bytes it is supposed to be.
     *
     * @param binary the binary
     * @param expectedSize its pinned length
     * @param expectedSha256 its pinned digest
     * @throws IOException if it cannot be read
     */
    static void assertIsPinned(Path binary, long expectedSize, String expectedSha256)
            throws IOException {
        assertEquals(expectedSize, Files.size(binary), () -> binary + " has the wrong length");
        assertEquals(
                expectedSha256,
                ArtefactMirror.sha256(binary),
                () -> binary + " is not the bytes this phase pinned");
    }
}
