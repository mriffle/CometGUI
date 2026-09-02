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

package org.cometgui.install.archive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.cometgui.domain.tools.ArtefactKind;
import org.cometgui.install.archive.ArchiveFixtures.Entry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * Every artefact this extractor opens, it closes -- including the ones it refuses.
 *
 * <h2>Why this is counted rather than asserted</h2>
 *
 * <p>Four readers own an operating-system handle: a zip's input stream, a tar's, a cpio's and the
 * channel a flat package is read through. Nothing in the suite noticed when any of those {@code
 * close} calls was deleted, because a leaked handle changes no value any test reads -- it changes
 * how many files the process may open next, which is a property of the process rather than of a
 * return value. An installer that leaks one handle per artefact runs out during the first install
 * that fetches a tool, its companions and PDV.
 *
 * <p>So this counts. It opens and refuses many artefacts of every kind, including the ones that
 * fail while the reader is still being built -- which is the path where a package's channel has to
 * be closed by hand, because there is no reader yet to close it.
 *
 * <p>Counting open descriptors needs {@code /proc/self/fd}, so this runs on Linux and records a
 * reason where it does not. That is a stated limit rather than a silent one: on a platform without
 * it, this particular evidence is absent and the {@code close} calls rest on review.
 */
@EnabledOnOs(
        value = OS.LINUX,
        disabledReason =
                "counting open file descriptors needs /proc/self/fd; on another platform this"
                        + " evidence is unavailable rather than unnecessary")
class FileHandleTest {

    /** How many artefacts of each kind to open. */
    private static final int ROUNDS = 5;

    @TempDir private Path work;

    private Path archives;

    private Path destination;

    private final ArtefactExtractor extractor = new ArtefactExtractor();

    @BeforeEach
    void createTree() throws IOException {
        archives = Files.createDirectories(work.resolve("archives"));
        destination = Files.createDirectories(work.resolve("dest"));
    }

    @Test
    @DisplayName("opening and refusing artefacts of every kind leaves none of them open")
    void noKindLeavesAnArtefactOpen() throws IOException {
        for (int round = 1; round <= ROUNDS; round++) {
            oneRound(round);
        }
        List<String> stillOpen = artefactsStillOpen();
        assertEquals(
                List.of(),
                stillOpen,
                "an artefact whose handle is not closed is a tool install that runs out of them"
                        + " part way through, and the descriptor still points at the file that was"
                        + " not closed");
    }

    /**
     * The artefacts this test wrote that the process still holds open.
     *
     * <p>Named rather than counted. A count of all open descriptors moves for reasons that have
     * nothing to do with this code -- a class loader, a log file, another test's socket -- and a
     * check that drifts is a check that gets ignored. Every descriptor is resolved to the file it
     * refers to instead, and only the ones inside this test's own archive directory are the
     * subject.
     *
     * @return the artefact paths still open, which must be none
     * @throws IOException if the descriptor table cannot be read
     */
    private List<String> artefactsStillOpen() throws IOException {
        List<String> open = new ArrayList<>();
        try (Stream<Path> descriptors = Files.list(Path.of("/proc/self/fd"))) {
            for (Path descriptor : descriptors.toList()) {
                Path target;
                try {
                    target = Files.readSymbolicLink(descriptor);
                } catch (IOException closedWhileWalking) {
                    continue;
                }
                if (target.startsWith(archives)) {
                    open.add(String.valueOf(target.getFileName()));
                }
            }
        }
        return open.stream().sorted().toList();
    }

    /*
     * One of everything: a container of each multi-entry kind read to the end, and one of each
     * refused -- a zip refused after its central directory is read, a tar refused mid-stream, and
     * a package refused while the reader is still being constructed, which is the only path where
     * the channel has nobody but the constructor to close it.
     */
    private void oneRound(int round) throws IOException {
        for (ArtefactKind kind : ArchiveFixtures.MULTI_ENTRY_KINDS) {
            Path good =
                    ArchiveFixtures.build(
                            kind,
                            archives,
                            "good-" + kind.id() + "-" + round,
                            List.of(Entry.file("tool.bin", "payload")));
            Path into = Files.createDirectories(destination.resolve(kind.id() + "-" + round));
            assertEquals(
                    List.of("tool.bin"),
                    extractor.extractWholeArtefact(kind, good, into, "tool.bin").paths());

            Path hostile =
                    ArchiveFixtures.build(
                            kind,
                            archives,
                            "hostile-" + kind.id() + "-" + round,
                            List.of(Entry.file("../escape.txt", "pwned")));
            Path refusedInto =
                    Files.createDirectories(
                            destination.resolve("refused-" + kind.id() + "-" + round));
            assertThrows(
                    ExtractionRejectedException.class,
                    () -> extractor.extractWholeArtefact(kind, hostile, refusedInto, "tool.bin"));
        }

        Path brokenPackage =
                Files.write(
                        archives.resolve("broken-" + round + ".pkg"),
                        ArchiveFixtures.pkgBytesWithToc("<xar><toc>", new byte[] {1}));
        Path packageInto = Files.createDirectories(destination.resolve("broken-" + round));
        assertThrows(
                ExtractionRejectedException.class,
                () ->
                        extractor.extractWholeArtefact(
                                ArtefactKind.PKG_PAYLOAD, brokenPackage, packageInto, "tool.bin"),
                "a package whose table of contents will not parse fails before its reader exists,"
                        + " so its channel is closed by the constructor or not at all");
    }

    @Test
    @DisplayName("a stream-backed reader closes the stream it was handed")
    void aStreamBackedReaderClosesItsStream() throws IOException {
        /*
         * Counting descriptors cannot see these two: a cpio reader inside a package, and a tar
         * reader inside a Debian archive, each sit on top of a decompressor whose own resources are
         * not a file handle.  The stream is handed in, so the closing can simply be watched.
         */
        Recording forCpio = new Recording();
        new CpioArchiveReader(forCpio, "x.pkg").close();
        Recording forTar = new Recording();
        new TarArchiveReader(forTar, "x.tar").close();
        assertTrue(forCpio.closed, "the cpio reader must close the stream it reads");
        assertTrue(forTar.closed, "the tar reader must close the stream it reads");
    }

    /** A stream that remembers being closed. */
    private static final class Recording extends java.io.InputStream {

        private boolean closed;

        @Override
        public int read() {
            return -1;
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
