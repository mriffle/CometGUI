/*
 * CometGUI -- Phase 00, work unit 7: JavaFX headless startup smoke test.
 *
 * THROWAWAY FEASIBILITY SPIKE, NOT PRODUCT CODE.  Phase 00 writes no product
 * Java; this class exists only to make the claim "a real JavaFX Application
 * starts on the pinned JDK 25 / JavaFX 25 pair, headless" falsifiable.
 *
 * It is a real javafx.application.Application: it is started through
 * Application.launch(), so the whole toolkit-startup path runs.  Inside
 * start() it proves, and asserts, eleven things.  Exit code 0 proves nothing,
 * so every check below computes a value and compares it -- the process exits
 * 1 if any check fails, and prints which.
 *
 *   1  start() runs on a thread named "JavaFX Application Thread"
 *   2  Platform.isFxApplicationThread() is true on that thread
 *   3  com.sun.javafx.tk.Toolkit.getToolkit() is QuantumToolkit
 *   4  System property javafx.runtime.version is the pinned JavaFX version
 *   5  java.version is the pinned JDK version and java.vendor is BellSoft
 *      (java.vendor.version is null on this build -- see docs/feasibility/
 *      toolchain.rst)
 *   6  the Glass platform in use is Monocle's MonocleApplication
 *   7  javafx.stage.Screen reports a usable primary screen
 *   8  a shown Stage lays out: the Button has a positive width
 *   9  real font metrics exist: a 100pt Text measures in a plausible range
 *      (a null font factory throws long before this, and a stub one would
 *      measure 0)
 *  10  the software rasteriser really rendered: Scene.snapshot() pixel (2,2)
 *      is the exact background colour set on the root
 *  11  a real event handler fires and changes a scene-graph value
 *
 * The expected values are pinned constants.  Each may be overridden with a
 * -Dsmoke.expect.* system property for one purpose only: demonstrating that
 * the harness genuinely fails when the expectation is wrong.  Overriding one
 * to make a real failure pass would be weakening a gate; do not.
 */
package cometgui.spike;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.WritableImage;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;

public final class HeadlessSmoke extends Application {

    private static final String EXPECT_FX_THREAD =
            System.getProperty("smoke.expect.threadName", "JavaFX Application Thread");
    private static final String EXPECT_TOOLKIT =
            System.getProperty("smoke.expect.toolkit", "com.sun.javafx.tk.quantum.QuantumToolkit");
    private static final String EXPECT_FX_VERSION =
            System.getProperty("smoke.expect.fxVersion", "25.0.4+1");
    private static final String EXPECT_JAVA_VERSION =
            System.getProperty("smoke.expect.javaVersion", "25.0.4.1");
    private static final String EXPECT_VENDOR =
            System.getProperty("smoke.expect.vendor", "BellSoft");
    private static final String EXPECT_GLASS =
            System.getProperty("smoke.expect.glass", "com.sun.glass.ui.monocle.MonocleApplication");
    /** 0xAARRGGBB for SmokeApp.BACKGROUND_CSS (#204080, fully opaque). */
    private static final int EXPECT_PIXEL =
            (int) Long.parseLong(System.getProperty("smoke.expect.pixel", "ff204080"), 16);

    private static final List<String> FAILURES = new ArrayList<>();

    private static void check(String name, Object actual, Object expected) {
        boolean ok = expected.equals(actual);
        System.out.printf("  [%s] %-22s actual=%s expected=%s%n",
                ok ? "PASS" : "FAIL", name, actual, expected);
        if (!ok) {
            FAILURES.add(name + ": actual=" + actual + " expected=" + expected);
        }
    }

    private static void checkTrue(String name, boolean ok, String detail) {
        System.out.printf("  [%s] %-22s %s%n", ok ? "PASS" : "FAIL", name, detail);
        if (!ok) {
            FAILURES.add(name + ": " + detail);
        }
    }

    @Override
    public void start(Stage stage) {
        try {
            System.out.println("== JavaFX headless startup evidence ==");

            check("fxThreadName", Thread.currentThread().getName(), EXPECT_FX_THREAD);
            checkTrue("isFxApplicationThread", Platform.isFxApplicationThread(),
                    "Platform.isFxApplicationThread()=" + Platform.isFxApplicationThread());
            check("toolkitClass",
                    com.sun.javafx.tk.Toolkit.getToolkit().getClass().getName(), EXPECT_TOOLKIT);
            check("javafx.runtime.version",
                    System.getProperty("javafx.runtime.version"), EXPECT_FX_VERSION);
            check("java.version", System.getProperty("java.version"), EXPECT_JAVA_VERSION);
            check("java.vendor", System.getProperty("java.vendor"), EXPECT_VENDOR);
            check("glassApplication",
                    com.sun.glass.ui.Application.GetApplication().getClass().getName(), EXPECT_GLASS);

            javafx.stage.Screen primary = javafx.stage.Screen.getPrimary();
            double sw = primary.getBounds().getWidth();
            double sh = primary.getBounds().getHeight();
            checkTrue("primaryScreen", sw > 0 && sh > 0,
                    "javafx.stage.Screen primary bounds = " + (int) sw + "x" + (int) sh);

            SmokeApp app = new SmokeApp();
            Scene scene = app.buildScene();
            stage.setScene(scene);
            stage.show();
            // Force a synchronous layout+CSS pass so measured values are real.
            scene.getRoot().applyCss();
            scene.getRoot().layout();

            Button go = (Button) scene.lookup("#go");
            Label output = (Label) scene.lookup("#output");
            TextField input = (TextField) scene.lookup("#input");
            checkTrue("buttonLaidOut", go != null && go.getWidth() > 0,
                    "Button#go width = " + (go == null ? "no such node" : go.getWidth()));

            Text probe = new Text("MMMM");
            probe.setFont(Font.font("System", 100));
            double textWidth = probe.getLayoutBounds().getWidth();
            checkTrue("fontMetrics", textWidth > 150 && textWidth < 900,
                    "width of \"MMMM\" at 100pt = " + textWidth + " (expected 150 < w < 900)");

            WritableImage shot = scene.snapshot(null);
            int argb = shot.getPixelReader().getArgb(2, 2);
            check("snapshotPixel(2,2)", String.format("%08x", argb),
                    String.format("%08x", EXPECT_PIXEL));
            checkTrue("snapshotSize",
                    shot.getWidth() == 320 && shot.getHeight() == 200,
                    "snapshot = " + (int) shot.getWidth() + "x" + (int) shot.getHeight());

            input.setText("comet");
            go.fireEvent(new ActionEvent());
            check("eventHandlerResult", output.getText(), "=COMET");
            check("pressCount", app.getPressCount(), 1);

        } catch (Throwable t) {
            FAILURES.add("threw " + t);
            t.printStackTrace(System.out);
        } finally {
            System.out.println();
            if (FAILURES.isEmpty()) {
                System.out.println("HEADLESS SMOKE: PASS -- all checks satisfied");
            } else {
                System.out.println("HEADLESS SMOKE: FAIL -- " + FAILURES.size() + " check(s) failed:");
                for (String f : FAILURES) {
                    System.out.println("    " + f);
                }
            }
            System.out.flush();
            Platform.exit();
            System.exit(FAILURES.isEmpty() ? 0 : 1);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
