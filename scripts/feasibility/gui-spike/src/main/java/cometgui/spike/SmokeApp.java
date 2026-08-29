/*
 * CometGUI -- Phase 00, work unit 7: GUI automation spike.
 *
 * THROWAWAY FEASIBILITY SPIKE, NOT PRODUCT CODE.  Phase 00 writes no product
 * Java (see phases/PHASE-00-feasibility.rst, "This phase writes no product
 * code").  This class exists only so the GUI automation spike has a real
 * scene graph with a real event handler to drive, and so that a broken
 * assertion is visibly a broken assertion.  Phase 01 owns the real source
 * tree; nothing here is meant to survive into it.
 *
 * The scene is deliberately tiny and deterministic:
 *
 *   - a TextField with id "input"
 *   - a Button   with id "go", labelled "Convert"
 *   - a Label    with id "output", initially "-"
 *
 * Pressing the button (or Enter in the field) sets the label to the field's
 * text upper-cased with a "=" prefix, and increments a counter.  That gives a
 * value an automation test can assert on -- not merely "nothing threw".
 */
package cometgui.spike;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.Locale;

public final class SmokeApp extends Application {

    /** Marker colour used by the headless render proof; also the scene fill. */
    public static final String BACKGROUND_CSS = "-fx-background-color: #204080;";

    private int pressCount;

    /** Builds the scene graph without needing an Application launch. */
    public Scene buildScene() {
        TextField input = new TextField();
        input.setId("input");
        input.setPrefColumnCount(16);

        Label output = new Label("-");
        output.setId("output");

        Button go = new Button("Convert");
        go.setId("go");
        go.setOnAction(e -> {
            pressCount++;
            output.setText("=" + input.getText().toUpperCase(Locale.ROOT));
        });
        input.setOnAction(go.getOnAction());

        VBox root = new VBox(8, input, go, output);
        root.setId("root");
        root.setPadding(new Insets(12));
        root.setStyle(BACKGROUND_CSS);
        return new Scene(root, 320, 200);
    }

    public int getPressCount() {
        return pressCount;
    }

    @Override
    public void start(Stage stage) {
        stage.setTitle("CometGUI Phase 00 spike");
        stage.setScene(buildScene());
        stage.show();
    }
}
