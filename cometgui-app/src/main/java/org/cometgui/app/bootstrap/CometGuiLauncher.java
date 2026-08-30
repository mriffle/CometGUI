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

package org.cometgui.app.bootstrap;

import javafx.application.Application;

/**
 * The executable entry point: {@code public static void main}, and nothing else.
 *
 * <p><strong>Why it is a separate class from {@link CometGuiApplication}.</strong> A launcher that
 * does not itself extend {@link Application} is what lets the application be started from a plain
 * class path, and it is what phase 16's {@code jpackage} configuration names as its main class.
 * When the main class extends {@link Application}, the JDK's own launcher takes a different path
 * and reports {@code "JavaFX runtime components are missing"} on a class-path launch; a separate
 * launcher has no such problem and costs one file.
 *
 * <p><strong>Why it holds no logic.</strong> Anything here would be code that only runs in a
 * packaged application, which is the code hardest to test. Argument parsing, single-instance
 * handling and crash reporting -- if the product ever wants them -- belong in a class this method
 * calls, not in this method. What is here is one call, and the startup smoke test drives the real
 * application through it rather than through {@link Application#launch} directly, so this line is
 * covered by the same test that proves the window appears.
 */
public final class CometGuiLauncher {

    private CometGuiLauncher() {}

    /**
     * Starts the JavaFX application and returns when it has stopped.
     *
     * @param args the command line, passed through to JavaFX and reachable from {@link
     *     Application#getParameters()}
     */
    public static void main(String[] args) {
        Application.launch(CometGuiApplication.class, args);
    }
}
