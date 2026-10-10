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

package org.cometgui.tools.pdv;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.tools.api.JavaRuntime;

/**
 * One request for annotated figures from PDV's command line.
 *
 * @param java the Java runtime PDV runs on
 * @param pdvJar the installed PDV JAR, beside its {@code lib} directory
 * @param pdvVersion the selected PDV version, which decides what spectrum sources it can be given
 * @param pepXml the Comet pepXML the run preserved
 * @param spectrumFile the run's spectrum source that Comet searched, never handed to PDV itself: it
 *     is checked, then re-encoded as an MGF
 * @param spectrumIds the native ids to draw, as the pepXML's {@code spectrumNativeID} gives them;
 *     at least one, distinct, and distinct once PDV has stripped the characters it removes from a
 *     figure's file name, so that no two figures could be written to one file
 * @param workDirectory a run-owned directory that is absent or empty: the MGF, the id list, PDV's
 *     home and the figures all go under it (design decision P11-17)
 * @param displayEnvironment the variables PDV needs to reach a display -- on Linux at least {@code
 *     DISPLAY}, and {@code XAUTHORITY} when the display needs it, because PDV's {@code HOME} is
 *     moved; never {@code HOME} or {@code LANG}, which the command sets itself
 */
public record PdvFigureRequest(
        JavaRuntime java,
        Path pdvJar,
        ToolVersion pdvVersion,
        Path pepXml,
        Path spectrumFile,
        List<String> spectrumIds,
        Path workDirectory,
        Map<String, String> displayEnvironment) {

    /**
     * Validates the request and copies its collections.
     *
     * @throws NullPointerException if a component, an id, or an environment name or value is {@code
     *     null}
     * @throws IllegalArgumentException if a path is relative, no id is given, an id is blank,
     *     repeated or holds a line break, or the display environment names {@code HOME} or {@code
     *     LANG}
     */
    public PdvFigureRequest {
        Objects.requireNonNull(java, "java");
        Objects.requireNonNull(pdvVersion, "pdvVersion");
        absolute(pdvJar, "pdvJar");
        absolute(pepXml, "pepXml");
        absolute(spectrumFile, "spectrumFile");
        absolute(workDirectory, "workDirectory");
        spectrumIds = List.copyOf(Objects.requireNonNull(spectrumIds, "spectrumIds"));
        if (spectrumIds.isEmpty()) {
            throw new IllegalArgumentException("a figure request needs at least one spectrum id");
        }
        Map<String, String> byStem = new HashMap<>();
        for (String id : spectrumIds) {
            if (id.isBlank() || id.contains("\n") || id.contains("\r")) {
                throw new IllegalArgumentException(
                        "a spectrum id must be one non-blank line, but was \"" + id + "\"");
            }
            String earlier = byStem.putIfAbsent(PdvFigures.fileStem(id), id);
            if (earlier != null) {
                throw new IllegalArgumentException(
                        earlier.equals(id)
                                ? "the spectrum id \"" + id + "\" is repeated"
                                : "the spectrum ids \""
                                        + earlier
                                        + "\" and \""
                                        + id
                                        + "\" would give PDV's figures the same file name");
            }
        }
        displayEnvironment =
                Map.copyOf(Objects.requireNonNull(displayEnvironment, "displayEnvironment"));
        for (String name : PdvFigures.OWN_VARIABLES) {
            if (displayEnvironment.containsKey(name)) {
                throw new IllegalArgumentException(
                        "the display environment must not set "
                                + name
                                + "; the PDV command constructs it");
            }
        }
    }

    private static void absolute(Path path, String name) {
        Objects.requireNonNull(path, name);
        if (!path.isAbsolute()) {
            throw new IllegalArgumentException(name + " must be absolute, but was: " + path);
        }
    }

    @Override
    public List<String> spectrumIds() {
        return List.copyOf(spectrumIds);
    }

    @Override
    public Map<String, String> displayEnvironment() {
        return Map.copyOf(displayEnvironment);
    }
}
