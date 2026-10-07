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

package org.cometgui.results.testing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * The checked-in fixtures under {@code src/test/resources/org/cometgui/results/parser/}, read from
 * the test classpath -- never from outside the module -- and verified by SHA-256 before use.
 *
 * <p>{@code real/} holds real Percolator 3.06.5, 3.07.1 and 3.09 output over CometGUI's synthetic
 * PIN; {@code real/PROVENANCE.txt} records how. {@code constructed/} holds hand-written edge cases;
 * {@code constructed/CONSTRUCTED.txt} says which and why.
 */
public final class Fixtures {

    private static final String ROOT = "/org/cometgui/results/parser/";

    private Fixtures() {}

    /**
     * A fixture, its digest checked first.
     *
     * @param relative for example {@code real/percolator-3.07.1/psms.tsv}
     * @param sha256 the digest hand-typed into the test from {@code PROVENANCE.txt} or {@code
     *     CONSTRUCTED.txt}
     * @return its path in {@code target/test-classes}
     */
    public static Path verified(String relative, String sha256) {
        URL url = Fixtures.class.getResource(ROOT + relative);
        assertNotNull(
                url,
                "the fixture "
                        + relative
                        + " is not on the test classpath; it is checked in under"
                        + " cometgui-results/src/test/resources"
                        + ROOT
                        + ", so its absence means the checkout or the build lost it");
        Path path;
        try {
            path = Path.of(url.toURI());
        } catch (URISyntaxException impossible) {
            throw new IllegalStateException(impossible);
        }
        assertEquals(
                sha256,
                sha256(path),
                "the fixture " + relative + " is not the file recorded in its provenance");
        return path;
    }

    /**
     * A file's SHA-256.
     *
     * @param file the file
     * @return the digest, lower-case hexadecimal
     */
    public static String sha256(Path file) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
        } catch (IOException failed) {
            throw new UncheckedIOException(failed);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
