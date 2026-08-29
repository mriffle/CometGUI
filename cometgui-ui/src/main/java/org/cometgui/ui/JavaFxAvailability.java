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

package org.cometgui.ui;

import javafx.fxml.FXMLLoader;
import javafx.scene.control.Label;

/**
 * Proof that this module compiles against the JavaFX modules bundled in the pinned JDK.
 *
 * <p><strong>Build-skeleton scaffolding created by phase 01.</strong> It contains no UI behaviour
 * and phase 02 should delete it once the real shell exists. Its only job is to fail the build the
 * day {@code javafx.controls} or {@code javafx.fxml} stops resolving -- for example if someone adds
 * an {@code org.openjfx:javafx-*} Maven dependency, or swaps the Liberica Full JDK for one without
 * JavaFX. No {@code --add-modules} argument is needed: they are system modules of the JDK image and
 * are root modules by default, which was verified by compiling this class.
 */
public final class JavaFxAvailability {

    private JavaFxAvailability() {}

    /**
     * @return the names of the JavaFX modules this module compiles against, in the form {@code
     *     javafx.controls+javafx.fxml}
     */
    public static String javaFxModuleNames() {
        return Label.class.getModule().getName() + "+" + FXMLLoader.class.getModule().getName();
    }
}
