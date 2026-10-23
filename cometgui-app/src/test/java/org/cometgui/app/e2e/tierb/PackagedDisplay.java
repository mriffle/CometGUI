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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.cometgui.app.testing.ProjectXvfb;
import org.cometgui.app.testing.RealSearch;

/**
 * The display a packaged CometGUI is shown on, and the environment its installed launcher needs on
 * this host -- exactly what {@code scripts/package/smoke-installed.sh} documents and passes:
 *
 * <pre>
 *   PATH=/usr/bin:/bin        LC_ALL=C.UTF-8        no JAVA_HOME
 *   HOME=&lt;fresh home&gt;      XDG_DATA_HOME=$HOME/.local/share
 *   DISPLAY                   the project Xvfb ({@link ProjectXvfb}, through the process service)
 *   LD_LIBRARY_PATH           the project X11 closure and the font stack's libraries
 *   FONTCONFIG_PATH           the font stack's etc/fonts
 *   FONTCONFIG_FILE           a fonts.conf written here: the stack's own plus an explicit
 *                             &lt;dir&gt; for its fonts, which that file otherwise finds
 *                             through XDG_DATA_HOME -- the fresh home now; without it JavaFX
 *                             finds no fonts and the application's start fails (unit 5)
 * </pre>
 *
 * <p>The library and font variables stand in for a desktop's system libraries, which this host does
 * not have; a user's machine needs none of them.
 */
public final class PackagedDisplay implements AutoCloseable {

    private final ProjectXvfb xvfb;

    private final Path fontsConf;

    private PackagedDisplay(ProjectXvfb xvfb, Path fontsConf) {
        this.xvfb = xvfb;
        this.fontsConf = fontsConf;
    }

    /**
     * Starts the project Xvfb and writes the fonts configuration.
     *
     * @param work a directory for the display's files
     * @return the display
     * @throws IOException if a file cannot be written or Xvfb cannot be started
     */
    public static PackagedDisplay start(Path work) throws IOException {
        Path directory = Files.createDirectories(work);
        Path fonts = RealSearch.repositoryRoot().resolve(ProjectXvfb.FONT_ROOT);
        Path conf = directory.resolve("fonts.conf");
        Files.writeString(
                conf,
                "<?xml version=\"1.0\"?>\n"
                        + "<!DOCTYPE fontconfig SYSTEM \"urn:fontconfig:fonts.dtd\">\n"
                        + "<fontconfig>\n"
                        + "  <include>"
                        + fonts.resolve("etc/fonts/fonts.conf")
                        + "</include>\n"
                        + "  <dir>"
                        + fonts.resolve("usr/share/fonts")
                        + "</dir>\n"
                        + "</fontconfig>\n",
                StandardCharsets.UTF_8);
        return new PackagedDisplay(ProjectXvfb.start(directory.resolve("xvfb")), conf);
    }

    /**
     * The display name, such as {@code :3}.
     *
     * @return the display
     */
    public String display() {
        return xvfb.display();
    }

    /**
     * The installed launcher's environment, for a fresh home.
     *
     * @param home the user's home: {@code HOME}, with {@code XDG_DATA_HOME} inside it
     * @return the variables, in the order above
     */
    public Map<String, String> launchEnvironment(Path home) {
        Objects.requireNonNull(home, "home");
        Map<String, String> client = xvfb.clientEnvironment();
        Map<String, String> environment = new LinkedHashMap<>();
        environment.put("PATH", "/usr/bin:/bin");
        environment.put("LC_ALL", "C.UTF-8");
        environment.put("HOME", home.toString());
        environment.put("XDG_DATA_HOME", home.resolve(".local").resolve("share").toString());
        environment.put("DISPLAY", xvfb.display());
        environment.put("LD_LIBRARY_PATH", client.get("LD_LIBRARY_PATH"));
        environment.put("FONTCONFIG_PATH", client.get("FONTCONFIG_PATH"));
        environment.put("FONTCONFIG_FILE", fontsConf.toString());
        return environment;
    }

    /**
     * What {@code scripts/package/x11_windows.py} needs to talk to this display: {@code DISPLAY}
     * and the X11 closure's libraries.
     *
     * @return the variables
     */
    public Map<String, String> helperEnvironment() {
        Map<String, String> environment = new LinkedHashMap<>();
        environment.put("PATH", "/usr/bin:/bin");
        environment.put("DISPLAY", xvfb.display());
        environment.put("LD_LIBRARY_PATH", xvfb.clientEnvironment().get("LD_LIBRARY_PATH"));
        return environment;
    }

    /** Stops the Xvfb. */
    @Override
    public void close() {
        xvfb.close();
    }
}
