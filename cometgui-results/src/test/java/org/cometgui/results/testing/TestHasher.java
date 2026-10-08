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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.ports.HashService;

/**
 * A {@link HashService} for this module's tests, which cannot depend on the product's hasher in
 * {@code cometgui-provenance}: MD5 and SHA-256 streamed with the JDK's {@link MessageDigest}, and a
 * count of the files hashed, so a test can see that a store hashed the raw table through the port
 * it was given ({@code R-PROC-01}).
 */
public final class TestHasher implements HashService {

    private final AtomicInteger calls = new AtomicInteger();

    @Override
    public FileHashes hash(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        calls.incrementAndGet();
        try (InputStream in = Files.newInputStream(path)) {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[1 << 20];
            int read;
            while ((read = in.read(buffer)) > 0) {
                md5.update(buffer, 0, read);
                sha256.update(buffer, 0, read);
            }
            return new FileHashes(
                    HexFormat.of().formatHex(md5.digest()),
                    HexFormat.of().formatHex(sha256.digest()));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    /**
     * How many files this hasher has hashed.
     *
     * @return the count
     */
    public int calls() {
        return calls.get();
    }
}
