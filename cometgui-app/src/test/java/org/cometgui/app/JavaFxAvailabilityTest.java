package org.cometgui.app;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Proves JavaFX resolves at test runtime as well as at compile time. */
class JavaFxAvailabilityTest {

    @Test
    @DisplayName("javafx.graphics resolves from the JDK image, not from a jar")
    void resolvesTheBundledJavaFxModule() {
        assertEquals("javafx.graphics", JavaFxAvailability.javaFxModuleName());
    }
}
