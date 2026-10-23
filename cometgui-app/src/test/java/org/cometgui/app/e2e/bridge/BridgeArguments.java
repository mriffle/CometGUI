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

package org.cometgui.app.e2e.bridge;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The agent's arguments: where to write the port and token, and the one address it will listen on.
 *
 * <p>Written {@code port-file=/absolute/path} or {@code port-file=/absolute/path,bind=127.0.0.1}.
 * {@code bind} exists only so that asking for anything else is refused by name: the bridge binds
 * the IPv4 loopback address and nothing else -- not {@code 0.0.0.0}, not a host name, not {@code
 * ::1}, not {@code localhost} (which a resolver could answer with anything). A refused argument
 * means the bridge does not start at all.
 *
 * @param portFile where the server writes its port, its token and the JVM's pid, absolute
 */
public record BridgeArguments(Path portFile) {

    /** The only address the bridge listens on. */
    public static final String LOOPBACK = "127.0.0.1";

    /**
     * Checks the value.
     *
     * @throws IllegalArgumentException if the port file is not absolute
     */
    public BridgeArguments {
        Objects.requireNonNull(portFile, "portFile");
        if (!portFile.isAbsolute()) {
            throw new IllegalArgumentException("port-file must be absolute, but was " + portFile);
        }
    }

    /**
     * The address the server listens on: always {@value #LOOPBACK}, built afresh from its four
     * bytes -- there is no other value it can have.
     *
     * @return {@code 127.0.0.1}
     */
    public InetAddress bind() {
        return loopback();
    }

    /**
     * Parses the text after {@code =} in the {@code -javaagent} option.
     *
     * @param text the arguments, or {@code null} when none were given
     * @return the arguments
     * @throws IllegalArgumentException naming what is wrong: no port file, an unknown or repeated
     *     key, a relative path, or a bind address other than {@value #LOOPBACK}
     */
    public static BridgeArguments parse(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(
                    "no arguments; the agent needs port-file=/absolute/path");
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (String pair : text.split(",", -1)) {
            int equals = pair.indexOf('=');
            if (equals <= 0) {
                throw new IllegalArgumentException("not key=value: \"" + pair + "\"");
            }
            String key = pair.substring(0, equals);
            if (!"port-file".equals(key) && !"bind".equals(key)) {
                throw new IllegalArgumentException(
                        "unknown argument \"" + key + "\" (known: port-file, bind)");
            }
            if (values.put(key, pair.substring(equals + 1)) != null) {
                throw new IllegalArgumentException("\"" + key + "\" given twice");
            }
        }
        String file = values.get("port-file");
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("no port-file=/absolute/path");
        }
        String address = values.getOrDefault("bind", LOOPBACK);
        if (!LOOPBACK.equals(address)) {
            throw new IllegalArgumentException(
                    "bind=" + address + " refused: the bridge listens on " + LOOPBACK + " only");
        }
        Path path;
        try {
            path = Path.of(file);
        } catch (InvalidPathException unrepresentable) {
            throw new IllegalArgumentException(
                    "port-file " + file + " is not a path here: " + unrepresentable.getMessage(),
                    unrepresentable);
        }
        return new BridgeArguments(path);
    }

    /**
     * The IPv4 loopback address, built from its four bytes: no resolver is asked.
     *
     * @return {@code 127.0.0.1}
     */
    static InetAddress loopback() {
        try {
            return InetAddress.getByAddress(new byte[] {127, 0, 0, 1});
        } catch (UnknownHostException impossible) {
            throw new IllegalStateException("four bytes are always an address", impossible);
        }
    }
}
