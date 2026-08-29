package org.cometgui.app;

import javafx.application.Application;

/**
 * Proof that this module compiles against the JavaFX modules bundled in the pinned JDK.
 *
 * <p><strong>Build-skeleton scaffolding created by phase 01.</strong> Phase 02 replaces it with the
 * real {@link javafx.application.Application} subclass in {@code org.cometgui.app.bootstrap}. See
 * {@code org.cometgui.ui.JavaFxAvailability} for why no {@code --add-modules} argument is needed.</p>
 */
public final class JavaFxAvailability {

    private JavaFxAvailability() {
    }

    /**
     * @return the name of the JavaFX module the application bootstrap will extend, {@code
     *     javafx.graphics}
     */
    public static String javaFxModuleName() {
        return Application.class.getModule().getName();
    }
}
