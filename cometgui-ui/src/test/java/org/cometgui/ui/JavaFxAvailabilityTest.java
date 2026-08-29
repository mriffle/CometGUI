package org.cometgui.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Proves JavaFX resolves at test runtime as well as at compile time. */
class JavaFxAvailabilityTest {

    @Test
    @DisplayName("javafx.controls and javafx.fxml resolve from the JDK image, not from a jar")
    void resolvesTheBundledJavaFxModules() {
        assertEquals("javafx.controls+javafx.fxml", JavaFxAvailability.javaFxModuleNames());
    }
}
