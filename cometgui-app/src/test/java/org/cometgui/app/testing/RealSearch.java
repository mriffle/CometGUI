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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import javafx.application.Platform;
import org.cometgui.domain.ports.ProcessListener;
import org.cometgui.domain.ports.ProcessRunner;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.tools.process.ProcessService;
import org.cometgui.tools.process.StartedProcess;

/**
 * The real inputs a GUI test searches -- the pinned Comet 2026.03.0 binary and the {@code D-006}
 * fixture -- each held to the SHA-256 recorded for it, and a process runner that records every
 * launch. Per-module test helpers are this project's convention (P8-1); the hashes are the ones
 * {@code handoffs/PHASE-08-worklog.rst} records, typed out here.
 *
 * <p>Files read outside this module: {@code scratch/phase05/artefacts/v2026.03.0__comet.linux.exe},
 * both K562 mzML and the UniProt proteome under {@code scratch/fixture}. A missing or changed input
 * FAILS the test; nothing skips.
 */
public final class RealSearch {

    /** The release searched. */
    public static final String RELEASE = "2026.03.0";

    /** The pinned linux/x86-64 Comet 2026.03.0. */
    public static final String COMET_SHA256 =
            "ad93b4cf60c2ed7afc2f41b8a3a05567c3938d999f17deb7b3676bc1fa91e7ed";

    private static final String MIRRORED_COMET =
            "scratch/phase05/artefacts/v2026.03.0__comet.linux.exe";

    private static final String MZML_3 = "scratch/fixture/20100614_Velos1_TaGe_SA_K562_3.mzML";

    private static final String MZML_3_FETCHED =
            "cbd0c1b37fb990e6f44528278956306754145ff7318ec7f89fed3b4d3c9b0bc7";

    private static final String MZML_3_LF =
            "a562f6e642b2880bece3134c43aa9581124788926bd1b9305c2db0bb506954da";

    private static final String MZML_4 = "scratch/fixture/20100614_Velos1_TaGe_SA_K562_4.mzML";

    private static final String MZML_4_FETCHED =
            "d30aa5af4b15e1c927616fce4dacfd68c6249e6cc2b7461913481c12d8408cfa";

    private static final String MZML_4_LF =
            "602aad75e18257feabdb94ece0fa82e19e5f17153eee4fb89875976920855c82";

    private static final String PROTEOME = "scratch/fixture/UP000005640_9606.fasta";

    private static final String PROTEOME_SHA256 =
            "2329a517bec9bd7269f9ce3b9252d8b959ae98bc41405a945c2d6134b284d5a0";

    private static final String SUBSET_SHA256 =
            "5005d9614b20f944915616201389cc7529d209a0f039278cae29cb356192558f";

    private RealSearch() {}

    /**
     * The repository root, found by walking up to the directory holding {@code manifests}.
     *
     * @return the root
     */
    public static Path repositoryRoot() {
        Path cursor = Path.of("").toAbsolutePath();
        while (cursor != null && !Files.isDirectory(cursor.resolve("manifests"))) {
            cursor = cursor.getParent();
        }
        if (cursor == null) {
            throw new AssertionError("no repository root above " + Path.of("").toAbsolutePath());
        }
        return cursor;
    }

    /**
     * Stages the pinned Comet, executable, held to its SHA-256.
     *
     * @param destination where it goes
     * @return the executable
     * @throws IOException if it cannot be copied
     */
    public static Path stageComet(Path destination) throws IOException {
        Path mirrored = repositoryRoot().resolve(MIRRORED_COMET);
        assertTrue(
                Files.isRegularFile(mirrored),
                mirrored
                        + " does not exist. The mirror is gitignored; refill it by fetching the"
                        + " artefact from the URL in manifests/tools.json and checking its"
                        + " SHA-256. This test fails rather than skips.");
        Files.createDirectories(parentOf(destination));
        Files.copy(mirrored, destination, StandardCopyOption.REPLACE_EXISTING);
        Files.setPosixFilePermissions(
                destination,
                Set.of(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.OWNER_EXECUTE));
        assertEquals(COMET_SHA256, sha256(destination), "the staged Comet");
        return destination;
    }

    /**
     * The two K562 spectrum files, LF-repaired into a directory, each held to its hash.
     *
     * @param directory where they go
     * @return {@code k562_3.mzML} and {@code k562_4.mzML}
     * @throws IOException if they cannot be read or written
     */
    public static List<Path> spectra(Path directory) throws IOException {
        return List.of(
                lfCopy(MZML_3, MZML_3_FETCHED, MZML_3_LF, directory.resolve("k562_3.mzML")),
                lfCopy(MZML_4, MZML_4_FETCHED, MZML_4_LF, directory.resolve("k562_4.mzML")));
    }

    /**
     * The whole proteome, copied, held to its hash.
     *
     * @param destination where it goes
     * @return the copy
     * @throws IOException if it cannot be read or written
     */
    public static Path proteome(Path destination) throws IOException {
        Path proteome = repositoryRoot().resolve(PROTEOME);
        assertTrue(
                Files.isRegularFile(proteome),
                proteome
                        + " does not exist. It is a local, gitignored input (D-006); refill it"
                        + " with python3 scripts/feasibility/fetch_ephemeral_input.py.");
        Files.createDirectories(parentOf(destination));
        Files.copy(proteome, destination, StandardCopyOption.REPLACE_EXISTING);
        assertEquals(PROTEOME_SHA256, sha256(destination), "the proteome");
        return destination;
    }

    /**
     * The proteome's first 1000 records, held to the validation corpus's subset hash.
     *
     * @param destination where it goes
     * @return the subset
     * @throws IOException if it cannot be read or written
     */
    public static Path subset(Path destination) throws IOException {
        Path proteome = proteome(destination.resolveSibling("whole-proteome.fasta"));
        String text = Files.readString(proteome, StandardCharsets.ISO_8859_1);
        Files.delete(proteome);
        int end = -1;
        for (int record = 0; record < 1000; record++) {
            end = text.indexOf("\n>", end + 1);
            assertTrue(end > 0, "the proteome has fewer than 1000 records");
        }
        Files.write(
                destination, (text.substring(0, end) + "\n").getBytes(StandardCharsets.ISO_8859_1));
        assertEquals(SUBSET_SHA256, sha256(destination), "the first 1000 records");
        return destination;
    }

    private static Path lfCopy(String fixture, String fetchedSha256, String lfSha256, Path copy)
            throws IOException {
        Path fetched = repositoryRoot().resolve(fixture);
        assertTrue(
                Files.isRegularFile(fetched),
                fetched
                        + " does not exist. It is a local, gitignored input (D-006); refill it"
                        + " with python3 scripts/feasibility/fetch_ephemeral_input.py.");
        assertEquals(fetchedSha256, sha256(fetched), "the fetched " + fetched);
        byte[] bytes = Files.readAllBytes(fetched);
        ByteArrayOutputStream repaired = new ByteArrayOutputStream(bytes.length);
        for (byte value : bytes) {
            if (value != '\r') {
                repaired.write(value);
            }
        }
        Files.createDirectories(parentOf(copy));
        Files.write(copy, repaired.toByteArray());
        assertEquals(lfSha256, sha256(copy), "the LF-repaired copy of " + fetched);
        return copy;
    }

    private static Path parentOf(Path file) {
        Path parent = file.toAbsolutePath().getParent();
        if (parent == null) {
            throw new AssertionError(file + " has no directory");
        }
        return parent;
    }

    /**
     * The SHA-256 of a file, computed here, independently of the product's hasher.
     *
     * @param file the file
     * @return 64 lower-case hexadecimal characters
     * @throws IOException if it cannot be read
     */
    public static String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[65536];
            try (InputStream in = Files.newInputStream(file)) {
                int read;
                while ((read = in.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError("every Java runtime provides SHA-256", impossible);
        }
    }

    /**
     * The product's own process service, with every launch recorded: its argument array, the
     * started process (for its pid), and whether it was launched on the JavaFX thread.
     */
    public static final class LaunchRecorder implements ProcessRunner {

        /** One launch. */
        public record Launch(List<String> argv, StartedProcess process, boolean onFxThread) {

            /** Copies the argument array. */
            public Launch {
                argv = List.copyOf(argv);
            }

            @Override
            public List<String> argv() {
                return List.copyOf(argv);
            }
        }

        private final ProcessService delegate;

        private final List<Launch> launches = Collections.synchronizedList(new ArrayList<>());

        private final List<Watch> watches = new CopyOnWriteArrayList<>();

        /** A line some launched tool will print, awaited. */
        private record Watch(String needle, CompletableFuture<String> seen) {}

        /**
         * A recorder in front of a process service.
         *
         * @param delegate the service
         */
        public LaunchRecorder(ProcessService delegate) {
            this.delegate = delegate;
        }

        @Override
        public StartedProcess start(ToolCommand command, ProcessListener listener)
                throws IOException {
            boolean onFx = Platform.isFxApplicationThread();
            ProcessListener watched =
                    new ProcessListener() {
                        @Override
                        public void onStandardOutput(String line) {
                            listener.onStandardOutput(line);
                            seen(line);
                        }

                        @Override
                        public void onStandardError(String line) {
                            listener.onStandardError(line);
                            seen(line);
                        }

                        @Override
                        public void onExit(int exitCode) {
                            listener.onExit(exitCode);
                        }
                    };
            StartedProcess started = delegate.start(command, watched);
            launches.add(new Launch(command.argv(), started, onFx));
            return started;
        }

        /**
         * A future completed with the first line a launched tool prints, from now on, that contains
         * a text.
         *
         * @param needle the text
         * @return the future
         */
        public CompletableFuture<String> lineContaining(String needle) {
            Watch watch = new Watch(needle, new CompletableFuture<>());
            watches.add(watch);
            return watch.seen();
        }

        private void seen(String line) {
            for (Watch watch : watches) {
                if (line.contains(watch.needle())) {
                    watch.seen().complete(line);
                }
            }
        }

        /**
         * Every launch, oldest first.
         *
         * @return the launches
         */
        public List<Launch> launches() {
            synchronized (launches) {
                return List.copyOf(launches);
            }
        }
    }
}
