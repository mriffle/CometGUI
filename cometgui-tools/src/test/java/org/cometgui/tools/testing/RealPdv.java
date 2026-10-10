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

package org.cometgui.tools.testing;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * The real PDV 2.7.0, unpacked from the upstream ZIP in the gitignored artefact mirror.
 *
 * <p>The ZIP is held to the SHA-256 and size that {@code manifests/tools.json} pins for every PDV
 * 2.7.0 row -- read from the manifest itself, so a test cannot keep passing against an artefact the
 * product no longer ships -- and the unpacked JAR to the SHA-256 the Phase 11 spike recorded. PDV
 * runs only from its unpacked tree, because its JAR finds 183 libraries through a manifest {@code
 * Class-Path} into {@code lib/}.
 *
 * <p>Unpacked once into this module's build directory and moved into place atomically, so several
 * test JVMs (PIT runs four) share one copy and none ever sees a half-written tree. Every member's
 * size is checked against the ZIP on each use.
 */
public final class RealPdv {

    /** The mirror's file name for the PDV 2.7.0 release asset. */
    public static final String ZIP = "v2.7.0__PDV-2.7.0.zip";

    /** The JAR's path inside the ZIP, as the manifest's {@code expectedExecutablePath} says. */
    public static final String JAR_MEMBER = "PDV-2.7.0/PDV-2.7.0.jar";

    /** The manifest's SHA-256 for the ZIP, typed again so a manifest change is noticed here. */
    public static final String ZIP_SHA256 =
            "58f95a5663d70e885c59a6aea883d2dd783f9daa3ec0709fc932d09b4a1f7982";

    /** The manifest's size for the ZIP. */
    public static final long ZIP_BYTES = 103_407_417L;

    /** The SHA-256 of {@code PDV-2.7.0.jar} inside that ZIP. */
    public static final String JAR_SHA256 =
            "fc876310b6f9dd95bd9538f212e28d45ac0226ea1615a9c09f2e904d70d07908";

    private static final Pattern PDV_ROW =
            Pattern.compile(
                    "\"tool\":\\s*\"pdv\",\\s*\"version\":\\s*\"2\\.7\\.0\"[\\s\\S]*?"
                            + "\"sizeBytes\":\\s*([0-9]+),\\s*\"sha256\":\\s*\"([0-9a-f]{64})\"");

    private static final Object LOCK = new Object();

    private RealPdv() {}

    /**
     * The unpacked, verified PDV 2.7.0 JAR.
     *
     * @return the JAR, beside its {@code lib} directory
     * @throws IOException if the artefact cannot be read or unpacked
     */
    public static Path jar() throws IOException {
        synchronized (LOCK) {
            return verifiedJar();
        }
    }

    private static Path verifiedJar() throws IOException {
        Path zip = UpstreamArtefacts.artefact(ZIP);
        verifyAgainstManifest(zip);
        Path build = Path.of(System.getProperty("cometgui.buildDirectory", "target"));
        Path tree = build.toAbsolutePath().resolve("real-pdv-2.7.0");
        if (!Files.isDirectory(tree)) {
            unpack(zip, build.toAbsolutePath(), tree);
        }
        verifyTree(zip, tree);
        Path jar = tree.resolve(JAR_MEMBER);
        String actual = UpstreamArtefacts.sha256(jar);
        if (!JAR_SHA256.equals(actual)) {
            throw new AssertionError(
                    jar
                            + " has SHA-256 "
                            + actual
                            + ", not the "
                            + JAR_SHA256
                            + " PDV 2.7.0 ships");
        }
        return jar;
    }

    private static void verifyAgainstManifest(Path zip) throws IOException {
        Path manifest = UpstreamArtefacts.repositoryRoot().resolve("manifests/tools.json");
        Matcher rows = PDV_ROW.matcher(Files.readString(manifest, StandardCharsets.UTF_8));
        int count = 0;
        while (rows.find()) {
            count++;
            if (Long.parseLong(rows.group(1)) != ZIP_BYTES || !ZIP_SHA256.equals(rows.group(2))) {
                throw new AssertionError(
                        "manifests/tools.json pins PDV 2.7.0 as "
                                + rows.group(1)
                                + " bytes, SHA-256 "
                                + rows.group(2)
                                + ", which is not the artefact these tests were written against");
            }
        }
        if (count == 0) {
            throw new AssertionError("manifests/tools.json holds no PDV 2.7.0 row");
        }
        long size = Files.size(zip);
        String sha = UpstreamArtefacts.sha256(zip);
        if (size != ZIP_BYTES || !ZIP_SHA256.equals(sha)) {
            throw new AssertionError(
                    "the mirrored "
                            + zip
                            + " is "
                            + size
                            + " bytes with SHA-256 "
                            + sha
                            + ", not the "
                            + ZIP_BYTES
                            + " bytes and "
                            + ZIP_SHA256
                            + " that manifests/tools.json pins; refetch it from the URL there");
        }
    }

    private static void unpack(Path zip, Path build, Path tree) throws IOException {
        Files.createDirectories(build);
        Path staging = Files.createTempDirectory(build, "real-pdv-unpacking-");
        try (ZipFile archive = new ZipFile(zip.toFile())) {
            Enumeration<? extends ZipEntry> entries = archive.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                Path target = staging.resolve(entry.getName()).normalize();
                if (!target.startsWith(staging)) {
                    throw new AssertionError(
                            "the PDV ZIP holds an entry outside its root: " + entry);
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                    continue;
                }
                Path parent = target.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                try (InputStream in = archive.getInputStream(entry)) {
                    Files.copy(in, target);
                }
            }
        }
        try {
            Files.move(staging, tree, StandardCopyOption.ATOMIC_MOVE);
        } catch (FileAlreadyExistsException | AtomicMoveNotSupportedException another) {
            deleteTree(staging);
        } catch (IOException raced) {
            if (!Files.isDirectory(tree)) {
                throw raced;
            }
            deleteTree(staging);
        }
    }

    private static void verifyTree(Path zip, Path tree) throws IOException {
        List<String> wrong = new ArrayList<>();
        try (ZipFile archive = new ZipFile(zip.toFile())) {
            Enumeration<? extends ZipEntry> entries = archive.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                Path file = tree.resolve(entry.getName());
                if (!entry.isDirectory()
                        && (!Files.isRegularFile(file) || Files.size(file) != entry.getSize())) {
                    wrong.add(entry.getName());
                }
            }
        }
        if (!wrong.isEmpty()) {
            throw new AssertionError(
                    "the unpacked PDV under "
                            + tree
                            + " differs from the ZIP in "
                            + wrong.size()
                            + " member(s), first "
                            + wrong.get(0)
                            + "; delete the directory and run again");
        }
    }

    private static void deleteTree(Path root) throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
