/*
 * CometGUI -- Phase 00, work unit 7: GUI automation spike.
 *
 * THROWAWAY FEASIBILITY SPIKE, NOT PRODUCT CODE.
 *
 * The FALLBACK half of the spike, spiked whether or not TestFX works so the
 * verdict rests on evidence for both.  It uses no TestFX at all: plain JUnit 5
 * plus the JavaFX toolkit, started once with Platform.startup(), driven from
 * the test thread with Platform.runLater + CountDownLatch, and asserted on
 * real scene-graph state.
 *
 * Two levels are proved here:
 *
 *   1  controller-level driving -- fire the Button's own ActionEvent and
 *      assert the resulting Label text (no synthetic input at all);
 *   2  synthetic input -- javafx.scene.robot.Robot (in-process, JavaFX 11+)
 *      moving the pointer to the Button's real screen coordinates, clicking,
 *      and typing into the TextField, asserting the same value.
 *
 * Level 2 is what a TestFX replacement would be built on; level 1 is what
 * works even if no robot is available.
 */
package cometgui.spike;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.geometry.Bounds;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.robot.Robot;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ToolkitFallbackTest {

    @BeforeAll
    static void startToolkit() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyRunning) {
            started.countDown();
        }
        assertTrue(started.await(30, TimeUnit.SECONDS), "the FX toolkit did not start");
        Platform.setImplicitExit(false);
    }

    /** Runs {@code c} on the FX Application Thread and returns its value. */
    private static <T> T onFx(Callable<T> c) throws Exception {
        FutureTask<T> task = new FutureTask<>(c);
        Platform.runLater(task);
        return task.get(30, TimeUnit.SECONDS);
    }

    private static void fxWait() throws Exception {
        onFx(() -> null);
    }

    @Test
    public void firingTheHandlerProducesTheUpperCasedValue() throws Exception {
        SmokeApp app = new SmokeApp();
        String text = onFx(() -> {
            Scene scene = app.buildScene();
            Stage stage = new Stage();
            stage.setScene(scene);
            stage.show();
            scene.getRoot().applyCss();
            scene.getRoot().layout();
            ((TextField) scene.lookup("#input")).setText("percolator");
            ((Button) scene.lookup("#go")).fireEvent(new ActionEvent());
            String result = ((Label) scene.lookup("#output")).getText();
            stage.hide();
            return result;
        });
        assertEquals("=PERCOLATOR", text);
        assertEquals(1, app.getPressCount());
    }

    @Test
    public void theRobotClicksAndTypesForReal() throws Exception {
        SmokeApp app = new SmokeApp();
        Stage stage = onFx(() -> {
            Scene scene = app.buildScene();
            Stage s = new Stage();
            s.setScene(scene);
            s.setX(0);
            s.setY(0);
            s.show();
            scene.getRoot().applyCss();
            scene.getRoot().layout();
            return s;
        });
        fxWait();
        try {
            Robot robot = onFx(Robot::new);

            // Click the text field at its real screen position, then type.
            onFx(() -> {
                TextField input = (TextField) stage.getScene().lookup("#input");
                Bounds b = input.localToScreen(input.getBoundsInLocal());
                robot.mouseMove(b.getMinX() + b.getWidth() / 2, b.getMinY() + b.getHeight() / 2);
                robot.mouseClick(MouseButton.PRIMARY);
                return null;
            });
            fxWait();
            for (KeyCode k : new KeyCode[]{KeyCode.C, KeyCode.O, KeyCode.M, KeyCode.E, KeyCode.T}) {
                onFx(() -> {
                    robot.keyType(k);
                    return null;
                });
            }
            fxWait();
            String typed = onFx(() -> ((TextField) stage.getScene().lookup("#input")).getText());
            assertEquals("comet", typed, "the robot's keystrokes did not reach the TextField");

            onFx(() -> {
                Button go = (Button) stage.getScene().lookup("#go");
                Bounds b = go.localToScreen(go.getBoundsInLocal());
                robot.mouseMove(b.getMinX() + b.getWidth() / 2, b.getMinY() + b.getHeight() / 2);
                robot.mouseClick(MouseButton.PRIMARY);
                return null;
            });
            fxWait();

            String out = onFx(() -> ((Label) stage.getScene().lookup("#output")).getText());
            assertEquals("=COMET", out, "the robot's click did not run the button handler");
            assertEquals(1, app.getPressCount());
        } finally {
            onFx(() -> {
                stage.hide();
                return null;
            });
        }
    }
}
