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

package org.cometgui.workflow.steps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.cometgui.domain.build.BuildIdentity;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.model.DecoySource;
import org.cometgui.params.comet.model.ValueOrigin;
import org.cometgui.params.comet.parser.ReleaseDefaults;
import org.cometgui.params.comet.schema.MetadataLoader;
import org.cometgui.provenance.json.JsonReader;
import org.cometgui.provenance.json.JsonValue;

/**
 * The real inputs of the real-binary tests, each staged from the gitignored {@code D-006} local
 * fixture and held to its SHA-256 (design decision P8-6). A missing or changed input FAILS the test
 * that needs it, naming how to refill it; nothing skips.
 *
 * <ul>
 *   <li>The Comet binaries, from {@code scratch/phase05/artefacts}, held both to the SHA-256 {@code
 *       manifests/tools.json} pins for the release and to the same value typed out here, so a
 *       manifest edited to match a different binary is noticed too.
 *   <li>The two K562 mzML of {@code scratch/fixture}, held to the hashes they were fetched by, and
 *       their LF-repaired private copies held to {@code a562f6e6...} and {@code 602aad75...}.
 *   <li>The UniProt proteome, held to its hash, and its first 1000 records held to the validation
 *       corpus's subset hash {@code 5005d961...}.
 * </ul>
 *
 * <p>Phase 00's {@code .pep.xml}/{@code .pin} leftovers in {@code scratch/fixture} are never read
 * and never deleted.
 */
final class RealComet {

    /** Where the binaries are mirrored, relative to the repository root. */
    static final String MIRROR = "scratch/phase05/artefacts";

    static final String NEWER = "2026.03.0";

    static final String NEWER_SHA256 =
            "ad93b4cf60c2ed7afc2f41b8a3a05567c3938d999f17deb7b3676bc1fa91e7ed";

    static final String OLDER = "2026.02.2";

    static final String OLDER_SHA256 =
            "af515b6ed5a17efafff7277a6a9c73cee97e26d38f3c9b2a8da16adaa44e6d9e";

    static final String MZML_3 = "scratch/fixture/20100614_Velos1_TaGe_SA_K562_3.mzML";

    static final String MZML_3_FETCHED =
            "cbd0c1b37fb990e6f44528278956306754145ff7318ec7f89fed3b4d3c9b0bc7";

    static final String MZML_3_LF =
            "a562f6e642b2880bece3134c43aa9581124788926bd1b9305c2db0bb506954da";

    static final String MZML_4 = "scratch/fixture/20100614_Velos1_TaGe_SA_K562_4.mzML";

    static final String MZML_4_FETCHED =
            "d30aa5af4b15e1c927616fce4dacfd68c6249e6cc2b7461913481c12d8408cfa";

    static final String MZML_4_LF =
            "602aad75e18257feabdb94ece0fa82e19e5f17153eee4fb89875976920855c82";

    static final String PROTEOME = "scratch/fixture/UP000005640_9606.fasta";

    static final String PROTEOME_SHA256 =
            "2329a517bec9bd7269f9ce3b9252d8b959ae98bc41405a945c2d6134b284d5a0";

    static final String SUBSET_SHA256 =
            "5005d9614b20f944915616201389cc7529d209a0f039278cae29cb356192558f";

    /** The subset with a reversed {@code DECOY_} record after each record. */
    static final String DECOY_SHA256 =
            "ff098f19bba637aa33a736a01ad59cf35911292c9fb7598324ed02a7d5b07a99";

    static final BuildIdentity BUILD =
            BuildIdentity.of("0.1.0-test", "0".repeat(40), Instant.parse("2026-10-06T00:00:00Z"));

    private RealComet() {}

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
            throw new AssertionError("no repository root above " + Path.of("").toAbsolutePath());
        }
        return cursor;
    }

    /** A file's directory, which every file these tests name has. */
    static Path parentOf(Path file) {
        Path parent = file.getParent();
        if (parent == null) {
            throw new AssertionError(file + " has no directory");
        }
        return parent;
    }

    /** The pinned SHA-256 of a linux/x86-64 Comet release, typed out here. */
    static String pinnedSha256(String release) {
        return switch (release) {
            case NEWER -> NEWER_SHA256;
            case OLDER -> OLDER_SHA256;
            default -> throw new AssertionError("no pinned Comet " + release);
        };
    }

    /**
     * The SHA-256 {@code manifests/tools.json} pins for a linux/x86-64 Comet release, read with the
     * project's own JSON reader.
     */
    static String manifestSha256(String release) throws IOException {
        Path manifest = repositoryRoot().resolve("manifests/tools.json");
        JsonValue.JsonObject document =
                (JsonValue.JsonObject)
                        JsonReader.parse(Files.readString(manifest, StandardCharsets.UTF_8));
        List<String> found = new ArrayList<>();
        JsonValue.JsonArray artefacts =
                (JsonValue.JsonArray) document.member("artefacts").orElseThrow();
        for (JsonValue element : artefacts.elements()) {
            JsonValue.JsonObject artefact = (JsonValue.JsonObject) element;
            if ("comet".equals(text(artefact, "tool"))
                    && release.equals(text(artefact, "version"))
                    && "linux".equals(text(artefact, "os"))
                    && "x86-64".equals(text(artefact, "arch"))) {
                found.add(text(artefact, "sha256"));
            }
        }
        assertEquals(1, found.size(), "manifests/tools.json rows for Comet " + release);
        return found.get(0);
    }

    private static String text(JsonValue.JsonObject object, String member) {
        return object.member(member)
                .filter(JsonValue.JsonString.class::isInstance)
                .map(value -> ((JsonValue.JsonString) value).value())
                .orElse("");
    }

    /**
     * Stages a pinned Comet binary from the mirror and holds it to its SHA-256.
     *
     * @param release the release
     * @param destination where the executable goes
     * @return the executable
     */
    static Path stageComet(String release, Path destination) throws IOException {
        Path mirrored =
                repositoryRoot().resolve(MIRROR).resolve("v" + release + "__comet.linux.exe");
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
        String sha256 = sha256(destination);
        assertEquals(manifestSha256(release), sha256, "the staged Comet against the manifest");
        assertEquals(pinnedSha256(release), sha256, "the staged Comet against the pinned value");
        return destination;
    }

    /**
     * The selection of a staged binary, at its pinned hash.
     *
     * @param release the release
     * @param executable the staged executable
     * @return the selection
     */
    static CometSelection selection(String release, Path executable) {
        return new CometSelection(
                executable,
                ToolVersion.parse(release),
                pinnedSha256(release),
                true,
                Optional.of("comet " + release + " linux x86-64"));
    }

    /** Copies a fetched mzML held to its hash, dropping every CR byte, held to its LF hash. */
    static Path lfCopy(String fixture, String fetchedSha256, String lfSha256, Path copy)
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

    /** The two K562 spectrum files, LF-repaired into a directory. */
    static List<Path> spectra(Path directory) throws IOException {
        return List.of(
                lfCopy(MZML_3, MZML_3_FETCHED, MZML_3_LF, directory.resolve("k562_3.mzML")),
                lfCopy(MZML_4, MZML_4_FETCHED, MZML_4_LF, directory.resolve("k562_4.mzML")));
    }

    /** The whole proteome, held to its hash. */
    static Path proteome() throws IOException {
        Path proteome = repositoryRoot().resolve(PROTEOME);
        assertTrue(
                Files.isRegularFile(proteome),
                proteome
                        + " does not exist. It is a local, gitignored input (D-006); refill it"
                        + " with python3 scripts/feasibility/fetch_ephemeral_input.py.");
        assertEquals(PROTEOME_SHA256, sha256(proteome), "the proteome");
        return proteome;
    }

    /** The proteome's first 1000 records, held to the corpus's subset hash. */
    static Path subset(Path destination) throws IOException {
        String text = Files.readString(proteome(), StandardCharsets.ISO_8859_1);
        int end = -1;
        for (int record = 0; record < 1000; record++) {
            end = text.indexOf("\n>", end + 1);
            assertTrue(end > 0, "the proteome has fewer than 1000 records");
        }
        Files.createDirectories(parentOf(destination));
        Files.write(
                destination, (text.substring(0, end) + "\n").getBytes(StandardCharsets.ISO_8859_1));
        assertEquals(SUBSET_SHA256, sha256(destination), "the first 1000 records");
        return destination;
    }

    /** The subset with a reversed {@code DECOY_} record after each record. */
    static Path decoySubset(Path destination) throws IOException {
        Path targets = subset(destination.resolveSibling("targets-for-decoys.fasta"));
        String text = Files.readString(targets, StandardCharsets.ISO_8859_1);
        Files.delete(targets);
        StringBuilder out = new StringBuilder();
        for (String chunk : text.split(">", -1)) {
            if (chunk.isEmpty()) {
                continue;
            }
            int newline = chunk.indexOf('\n');
            String header = chunk.substring(0, newline);
            String sequence = chunk.substring(newline + 1).replace("\n", "");
            appendRecord(out, header, sequence);
            appendRecord(out, "DECOY_" + header, new StringBuilder(sequence).reverse().toString());
        }
        Files.write(destination, out.toString().getBytes(StandardCharsets.ISO_8859_1));
        assertEquals(DECOY_SHA256, sha256(destination), "the target-decoy FASTA");
        return destination;
    }

    private static void appendRecord(StringBuilder out, String header, String sequence) {
        out.append('>').append(header).append('\n');
        for (int at = 0; at < sequence.length(); at += 60) {
            out.append(sequence, at, Math.min(sequence.length(), at + 60)).append('\n');
        }
    }

    /**
     * The release's starting parameters, as a search in these tests uses them: the given database,
     * no spectral library, the given decoy source and threads, and the workflow's required outputs.
     */
    static CometParameters model(String release, Path database, DecoySource decoys, int threads) {
        return ReleaseDefaults.load(MetadataLoader.loadBundled(), ToolVersion.parse(release))
                .withText("database_name", database.toString(), ValueOrigin.USER)
                .withText("spectral_library_name", "", ValueOrigin.USER)
                .withText("num_threads", Integer.toString(threads), ValueOrigin.USER)
                .withDecoySource(decoys, ValueOrigin.USER)
                .withWorkflowEnforcedOutputs();
    }

    /**
     * Removes a directory's write permission and proves a write into it is refused (P8-5). If the
     * write succeeds -- running as root, or a file system ignoring modes -- this FAILS, naming why.
     */
    static void makeReadOnly(Path directory) throws IOException {
        Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("r-x------"));
        Path probe = directory.resolve("write-probe");
        assertThrows(
                AccessDeniedException.class,
                () -> Files.createFile(probe),
                "the input directory accepted a write after its write permission was removed --"
                        + " running as root, or a file system ignoring modes -- so it cannot"
                        + " prove containment");
    }

    /** Gives a directory its write permission back, so the temporary directory can be deleted. */
    static void makeWritable(Path directory) throws IOException {
        if (Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"));
        }
    }

    /** The SHA-256 of a file, computed here, independently of the product's hasher. */
    static String sha256(Path file) throws IOException {
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
     * Every path under a root, with what it is: a directory with its modification time, a file with
     * its size, modification time and SHA-256, or a link with its target. Relative to the root,
     * sorted.
     */
    static Map<String, String> snapshot(Path root) throws IOException {
        Map<String, String> entries = new TreeMap<>();
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path path : walk.toList()) {
                String name = root.relativize(path).toString();
                if (Files.isSymbolicLink(path)) {
                    entries.put(name, "link -> " + Files.readSymbolicLink(path));
                } else if (Files.isDirectory(path)) {
                    entries.put(name, "dir mtime " + Files.getLastModifiedTime(path).toMillis());
                } else {
                    entries.put(
                            name,
                            "file size "
                                    + Files.size(path)
                                    + " mtime "
                                    + Files.getLastModifiedTime(path).toMillis()
                                    + " sha256 "
                                    + sha256(path));
                }
            }
        }
        return entries;
    }

    /** The paths whose entry is new, changed or gone between two snapshots. */
    static List<String> differences(Map<String, String> before, Map<String, String> after) {
        Set<String> names = new java.util.TreeSet<>(before.keySet());
        names.addAll(after.keySet());
        List<String> changed = new ArrayList<>();
        for (String name : names) {
            if (!java.util.Objects.equals(before.get(name), after.get(name))) {
                changed.add(name);
            }
        }
        return changed;
    }
}
