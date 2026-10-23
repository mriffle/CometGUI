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

package org.cometgui.app.e2e.tierb;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Where a launched application's bridge listens, as it wrote it in its port file: {@code port=},
 * {@code token=} and {@code pid=}, one per line.
 *
 * @param port the loopback port
 * @param token the per-launch token every request must carry
 * @param pid the process id of the JVM that wrote it -- the packaged launcher's own
 */
public record BridgeEndpoint(int port, String token, long pid) {

    /**
     * Checks the values.
     *
     * @throws IllegalArgumentException if the port is out of range or the token is empty
     */
    public BridgeEndpoint {
        Objects.requireNonNull(token, "token");
        if (port < 1 || port > 65_535 || token.isEmpty()) {
            throw new IllegalArgumentException("not a bridge endpoint: port " + port);
        }
    }

    /**
     * Reads a port file. The bridge writes it under a temporary name and moves it into place, so
     * once it exists it is whole.
     *
     * @param file the file
     * @return the endpoint it names
     * @throws AssertionError if it lacks a value or a value is malformed
     */
    public static BridgeEndpoint read(Path file) {
        Map<String, String> values = new LinkedHashMap<>();
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                int equals = line.indexOf('=');
                if (equals > 0) {
                    values.put(line.substring(0, equals), line.substring(equals + 1));
                }
            }
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
        try {
            return new BridgeEndpoint(
                    Integer.parseInt(required(values, "port", file)),
                    required(values, "token", file),
                    Long.parseLong(required(values, "pid", file)));
        } catch (IllegalArgumentException malformed) {
            throw new AssertionError("the bridge's port file " + file + " is malformed", malformed);
        }
    }

    private static String required(Map<String, String> values, String key, Path file) {
        return Optional.ofNullable(values.get(key))
                .orElseThrow(() -> new AssertionError(file + " names no " + key));
    }

    @Override
    public String toString() {
        // the token is a secret of this launch; it is never printed
        return "BridgeEndpoint[127.0.0.1:" + port + ", pid " + pid + "]";
    }
}
