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

package org.cometgui.app;

import javafx.application.Application;

/**
 * Proof that this module compiles against the JavaFX modules bundled in the pinned JDK.
 *
 * <p><strong>Build-skeleton scaffolding created by phase 01.</strong> Phase 02 replaces it with the
 * real {@link javafx.application.Application} subclass in {@code org.cometgui.app.bootstrap}. See
 * {@code org.cometgui.ui.JavaFxAvailability} for why no {@code --add-modules} argument is needed.
 */
public final class JavaFxAvailability {

    private JavaFxAvailability() {}

    /**
     * @return the name of the JavaFX module the application bootstrap will extend, {@code
     *     javafx.graphics}
     */
    public static String javaFxModuleName() {
        return Application.class.getModule().getName();
    }
}
