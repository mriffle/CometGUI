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

package org.cometgui.app.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.cometgui.domain.ports.EnvironmentReader;

/**
 * The variables PDV needs to reach this desktop's display, read from the application's own
 * environment.
 *
 * <p>PDV is a Swing program and its command line constructs a window before it reads an argument,
 * so on Linux it needs {@code DISPLAY}. The PDV command moves PDV's {@code HOME} into a run-owned
 * directory (design decision P11-17), and an X server's cookie is found by default at {@code
 * $HOME/.Xauthority} -- so with {@code HOME} moved the cookie is lost unless {@code XAUTHORITY}
 * names it. This passes {@code XAUTHORITY} when the desktop sets it, and otherwise, when there is a
 * display, names the user's own {@code ~/.Xauthority} if that file exists. Nothing else of the
 * application's environment is passed. On macOS and Windows neither variable is set and PDV reaches
 * the display without them.
 */
public final class PdvDisplay {

    /** The X11 display. */
    public static final String DISPLAY = "DISPLAY";

    /** The X11 authority file. */
    public static final String XAUTHORITY = "XAUTHORITY";

    /** The default authority file's name in a home directory. */
    static final String DEFAULT_AUTHORITY = ".Xauthority";

    private PdvDisplay() {}

    /**
     * The display variables of this desktop.
     *
     * @param host the application's environment
     * @return {@code DISPLAY} and {@code XAUTHORITY} where there are values for them; possibly
     *     empty
     */
    public static Map<String, String> fromHost(EnvironmentReader host) {
        Objects.requireNonNull(host, "host");
        Map<String, String> display = new LinkedHashMap<>();
        Optional<String> screen = nonBlank(host.environmentVariable(DISPLAY));
        if (screen.isEmpty()) {
            return Map.of();
        }
        display.put(DISPLAY, screen.get());
        Optional<String> authority = nonBlank(host.environmentVariable(XAUTHORITY));
        if (authority.isPresent()) {
            display.put(XAUTHORITY, authority.get());
        } else {
            nonBlank(host.environmentVariable("HOME"))
                    .map(home -> Path.of(home).resolve(DEFAULT_AUTHORITY))
                    .filter(Files::isRegularFile)
                    .ifPresent(file -> display.put(XAUTHORITY, file.toString()));
        }
        return Map.copyOf(display);
    }

    private static Optional<String> nonBlank(Optional<String> value) {
        return value.filter(text -> !text.isBlank());
    }
}
