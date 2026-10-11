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

package org.cometgui.tools.limelight.fake;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The one fake and its guard stay one (phase 12, design decision P12-16): every module that carries
 * a copy of this package carries it byte for byte.
 *
 * <p>The original is in {@code cometgui-tools}' test sources. A module whose tests upload copies
 * {@link #COPIED} -- this test included, unchanged -- into the same paths of its own. This test,
 * wherever it runs, finds every module holding the package and compares each copied file with the
 * original, so a copy edited in one place fails the build in every module that has one. It reads
 * the other modules' test sources, which the {@code tests} harness's sandbox carries.
 */
class FakeCopiesAreIdenticalTest {

    private static final String PACKAGE = "src/test/java/org/cometgui/tools/limelight/fake";

    private static final String FAKE = PACKAGE + "/";

    /** What a copy consists of, relative to a module's directory. */
    static final List<String> COPIED =
            List.of(
                    FAKE + "FakeLimelightServer.java",
                    FAKE + "LoopbackGuard.java",
                    FAKE + "FakeCopiesAreIdenticalTest.java",
                    "src/test/resources/org/cometgui/tools/limelight/fake/"
                            + "submit-import-wire-v16.xsd",
                    "src/test/resources/META-INF/services/"
                            + "org.junit.jupiter.api.extension.Extension",
                    "src/test/resources/junit-platform.properties");

    /** The module that holds the original. */
    static final String ORIGINAL = "cometgui-tools";

    @Test
    @DisplayName("every module's copy of the fake and the guard is the original, byte for byte")
    void everyCopyIsTheOriginal() throws IOException {
        Path root = repositoryRoot();
        Path original = root.resolve(ORIGINAL);
        List<Path> copies = copies(root);
        Path here = Path.of("").toAbsolutePath().normalize();

        assertTrue(
                here.equals(original) || copies.contains(here),
                "this module, " + here + ", is neither the original nor a copy the scan found");
        for (String file : COPIED) {
            assertTrue(Files.isRegularFile(original.resolve(file)), "the original lacks " + file);
        }
        List<String> drift = new ArrayList<>();
        for (Path copy : copies) {
            drift.addAll(differences(original, copy));
        }
        assertEquals(List.of(), drift, "a copy of the fake has drifted from " + original);
    }

    @Test
    @DisplayName("the comparison sees a changed byte and a missing file (its own control)")
    void theComparisonCanFail(@TempDir Path tmp) throws IOException {
        Path original = repositoryRoot().resolve(ORIGINAL);
        Path copy = tmp.resolve("cometgui-copy");
        for (String file : COPIED) {
            Path target = copy.resolve(file);
            Files.createDirectories(target.resolveSibling("."));
            Files.copy(original.resolve(file), target);
        }
        assertEquals(List.of(), differences(original, copy));

        Path guard = copy.resolve(COPIED.get(1));
        Files.writeString(
                guard,
                Files.readString(guard, StandardCharsets.UTF_8).replace("127.*", "128.*"),
                StandardCharsets.UTF_8);
        Files.delete(copy.resolve(COPIED.get(3)));

        List<String> drift = differences(original, copy);
        assertEquals(2, drift.size(), drift.toString());
        assertTrue(drift.get(0).startsWith(copy.resolve(COPIED.get(1)) + " differs from "));
        assertTrue(drift.get(1).startsWith(copy.resolve(COPIED.get(3)) + " is missing"));
        assertFalse(copies(repositoryRoot()).contains(original), "the original counted as a copy");
    }

    /** Every module, other than the original, whose test sources hold this package. */
    static List<Path> copies(Path root) throws IOException {
        try (Stream<Path> modules = Files.list(root)) {
            return modules.filter(
                            module -> String.valueOf(module.getFileName()).startsWith("cometgui-"))
                    .filter(module -> !String.valueOf(module.getFileName()).equals(ORIGINAL))
                    .filter(module -> Files.isDirectory(module.resolve(PACKAGE)))
                    .map(module -> module.toAbsolutePath().normalize())
                    .sorted()
                    .toList();
        }
    }

    /** Each copied file of {@code copy} that is missing or not byte-identical to the original. */
    static List<String> differences(Path original, Path copy) throws IOException {
        List<String> drift = new ArrayList<>();
        for (String file : COPIED) {
            Path copied = copy.resolve(file);
            if (!Files.isRegularFile(copied)) {
                drift.add(copied + " is missing");
            } else if (!Arrays.equals(
                    Files.readAllBytes(original.resolve(file)), Files.readAllBytes(copied))) {
                drift.add(copied + " differs from " + original.resolve(file));
            }
        }
        return drift;
    }

    /** The nearest directory up from here that holds {@code manifests}. */
    static Path repositoryRoot() {
        for (Path at = Path.of("").toAbsolutePath(); at != null; at = at.getParent()) {
            if (Files.isDirectory(at.resolve("manifests"))) {
                return at;
            }
        }
        throw new AssertionError("no manifests directory above " + Path.of("").toAbsolutePath());
    }
}
