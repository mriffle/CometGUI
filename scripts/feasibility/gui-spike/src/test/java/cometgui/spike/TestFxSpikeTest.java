/*
 * CometGUI -- Phase 00, work unit 7: GUI automation spike.
 *
 * THROWAWAY FEASIBILITY SPIKE, NOT PRODUCT CODE.
 *
 * The TestFX half of the spike: TestFX 4.0.18 driving the pinned
 * JDK 25.0.4.1+1 / JavaFX 25.0.4+1 pair headless, through Monocle supplied by
 * org.testfx:openjfx-monocle:21.0.2 and injected with --patch-module.
 *
 * This is deliberately a real interaction test, not a "did not throw" test:
 * it types into a TextField, clicks a Button, and asserts the exact string the
 * button's handler put into a Label.  Change "=COMET" to anything else and the
 * test must fail -- that negative run is recorded in
 * docs/feasibility/gui-automation-spike.rst.
 */
package cometgui.spike;

import javafx.scene.control.Label;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestFxSpikeTest extends ApplicationTest {

    private SmokeApp app;

    @Override
    public void start(Stage stage) {
        app = new SmokeApp();
        stage.setScene(app.buildScene());
        stage.show();
        stage.toFront();
    }

    @Test
    public void typingAndClickingProducesTheUpperCasedValue() {
        clickOn("#input");
        write("comet");
        WaitForAsyncUtils.waitForFxEvents();

        // The keystrokes really reached the field.
        assertEquals("comet", lookup("#input").queryTextInputControl().getText(),
                "text typed by the robot did not reach the TextField");

        clickOn("#go");
        WaitForAsyncUtils.waitForFxEvents();

        Label output = lookup("#output").queryAs(Label.class);
        assertEquals("=COMET", output.getText(),
                "the button handler did not produce the expected label value");
        assertEquals(1, app.getPressCount(), "the button handler ran the wrong number of times");
    }

    @Test
    public void theSceneGraphIsRealAndLaidOut() {
        assertTrue(lookup("#go").queryAs(javafx.scene.control.Button.class).getWidth() > 0,
                "the Button has no width, so no layout pass ran");
    }
}
