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

package org.cometgui.app.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;
import org.cometgui.app.bootstrap.CometGuiApplication;
import org.cometgui.app.config.ApplicationServices;
import org.cometgui.app.config.RunWiring;
import org.cometgui.app.testing.ScriptedChooser;
import org.cometgui.app.uidriver.FxUiDriver;
import org.cometgui.app.uidriver.RunningApplication;
import org.cometgui.domain.build.BuildIdentity;
import org.cometgui.domain.log.BoundedMessageLog;
import org.cometgui.domain.ports.FileSystemAccess;
import org.cometgui.domain.ports.ProcessRunner;
import org.testfx.api.FxToolkit;

/**
 * The real application with the parameter editor's two test seams filled -- a scripted file chooser
 * and a build this test names -- and the few driving steps the parameter-editor GUI tests share.
 * Every identifier these steps are given is a hand-typed literal in the calling test (P7-5: a GUI
 * test never calls {@code UiIds}).
 *
 * <p>The application is the real one, started through its real {@code start} method by TestFX's
 * toolkit, exactly as {@code ConsoleFloodUiTest} starts it; only the constructor's seams differ.
 */
final class ParameterEditorApp {

    /** The navigation entry of the Comet Parameters section. */
    static final String NAV_COMET_PARAMETERS = "nav-comet-parameters";

    /** The navigation entry of the Run section. */
    static final String NAV_RUN = "nav-run";

    private final ScriptedChooser chooser;

    private final RunningApplication application;

    private ParameterEditorApp(ScriptedChooser chooser, RunningApplication application) {
        this.chooser = chooser;
        this.application = application;
    }

    /**
     * Starts the application with a scripted chooser, a named build, and a file system in which the
     * given paths exist as readable files (every other question goes to the real one).
     *
     * @param build the build a saved file's header names
     * @param present the paths to report as readable files
     * @return the running application
     */
    static ParameterEditorApp launch(BuildIdentity build, Path... present) {
        ApplicationServices real = ApplicationServices.forThisHost();
        return launch(
                build,
                real.processRunner().orElse(null),
                new BoundedMessageLog(),
                RunWiring.Setup.forThisApplication(real),
                present);
    }

    /**
     * Starts the application as {@link #launch(BuildIdentity, Path...)} does, with the Run
     * section's seams chosen by the test: the process runner the engine launches through, the
     * console's log, and where the Tool Manager and the project come from.
     *
     * @param build the build a saved file's header names
     * @param processes the process runner; {@code null} for none
     * @param log the console's log
     * @param runSetup the Tool Manager and the project directory
     * @param present the paths to report as readable files
     * @return the running application
     */
    static ParameterEditorApp launch(
            BuildIdentity build,
            ProcessRunner processes,
            BoundedMessageLog log,
            RunWiring.Setup runSetup,
            Path... present) {
        ScriptedChooser chooser = new ScriptedChooser();
        ApplicationServices real = ApplicationServices.forThisHost();
        ApplicationServices services =
                new ApplicationServices(
                        real.clock(),
                        real.environment(),
                        new PresentFiles(real.fileSystem(), Set.of(present)),
                        real.runIds(),
                        real.glibcVersions(),
                        processes,
                        null,
                        null);
        try {
            Stage primary = FxToolkit.registerPrimaryStage();
            FxToolkit.setupApplication(
                    () ->
                            new CometGuiApplication(
                                    services, log, () -> build, owner -> chooser, runSetup));
            return new ParameterEditorApp(chooser, RunningApplication.showing(primary));
        } catch (TimeoutException timedOut) {
            return fail("the application did not start", timedOut);
        }
    }

    /**
     * The chooser the application asks; a test scripts it before the click that asks.
     *
     * @return the chooser
     */
    ScriptedChooser chooser() {
        return chooser;
    }

    /**
     * The running application.
     *
     * @return the application
     */
    RunningApplication application() {
        return application;
    }

    /** Ends the application. */
    void stop() {
        application.stop();
    }

    /**
     * Shows the Comet Parameters section with a click on its navigation entry.
     *
     * @param driver the driver
     */
    static void openEditor(FxUiDriver driver) {
        driver.clickOn(NAV_COMET_PARAMETERS);
        driver.onFxThread(
                () -> {
                    Parent root = driver.node("shell-root").getScene().getRoot();
                    root.applyCss();
                    root.layout();
                });
    }

    /**
     * Types a value into a text control and presses Enter, which commits it.
     *
     * @param driver the driver
     * @param id the text control
     * @param text the value
     */
    static void enter(FxUiDriver driver, String id, String text) {
        driver.typeInto(id, text);
        driver.press(KeyCode.ENTER);
    }

    /**
     * What a combo box shows for its value, through its own converter.
     *
     * @param driver the driver
     * @param id the combo box
     * @return the shown text, empty when nothing is chosen
     */
    static String comboText(FxUiDriver driver, String id) {
        ComboBox<?> box = combo(driver, id);
        return driver.callOnFxThread(() -> shown(box, box.getValue()));
    }

    /**
     * What a combo box offers, through its own converter, in order.
     *
     * @param driver the driver
     * @param id the combo box
     * @return the offered texts
     */
    static List<String> comboItems(FxUiDriver driver, String id) {
        ComboBox<?> box = combo(driver, id);
        return driver.callOnFxThread(
                () -> {
                    List<String> items = new ArrayList<>();
                    for (Object item : box.getItems()) {
                        items.add(shown(box, item));
                    }
                    return items;
                });
    }

    /**
     * Chooses an item of a combo box the way a user does with the keyboard: a click opens it, the
     * arrow keys move to the item, and Enter closes it.
     *
     * @param driver the driver
     * @param id the combo box
     * @param item the item's text, as the combo box shows it
     */
    static void choose(FxUiDriver driver, String id, String item) {
        List<String> items = comboItems(driver, id);
        int target = items.indexOf(item);
        assertTrue(target >= 0, () -> "#" + id + " offers no \"" + item + "\"; it offers " + items);
        driver.clickOn(id);
        for (int i = 0; i <= items.size() && !item.equals(comboText(driver, id)); i++) {
            int current = items.indexOf(comboText(driver, id));
            driver.press(current < target ? KeyCode.DOWN : KeyCode.UP);
        }
        driver.press(KeyCode.ENTER);
        assertEquals(
                item, comboText(driver, id), () -> "#" + id + " after choosing \"" + item + "\"");
    }

    /**
     * Shows an Advanced category's parameters, with a click on its switch if it is hidden.
     *
     * @param driver the driver
     * @param toggleId the category's switch
     */
    static void showCategory(FxUiDriver driver, String toggleId) {
        ToggleButton toggle = (ToggleButton) driver.node(toggleId);
        if (!driver.callOnFxThread(toggle::isSelected)) {
            driver.clickOn(toggleId);
        }
        assertTrue(driver.callOnFxThread(toggle::isSelected), "#" + toggleId + " is shown");
    }

    /**
     * Every control at or under a node, after CSS and layout, depth first.
     *
     * @param driver the driver
     * @param id the node to walk from
     * @return the controls
     */
    static List<Control> controlsUnder(FxUiDriver driver, String id) {
        Node top = driver.node(id);
        return driver.callOnFxThread(
                () -> {
                    top.getScene().getRoot().applyCss();
                    top.getScene().getRoot().layout();
                    List<Control> found = new ArrayList<>();
                    collect(top, found);
                    return found;
                });
    }

    /**
     * Whether a node with an identifier exists anywhere in the scene, shown or not.
     *
     * @param driver the driver
     * @param id the identifier
     * @return {@code true} if the scene has it
     */
    static boolean exists(FxUiDriver driver, String id) {
        Node root = driver.node("shell-root");
        return driver.callOnFxThread(() -> root.lookup("#" + id) != null);
    }

    private static void collect(Node node, List<Control> found) {
        if (node instanceof Control control) {
            found.add(control);
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                collect(child, found);
            }
        }
    }

    private static ComboBox<?> combo(FxUiDriver driver, String id) {
        Node node = driver.node(id);
        if (!(node instanceof ComboBox<?> box)) {
            return fail("#" + id + " is a " + node.getClass().getName() + ", not a combo box");
        }
        return box;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static String shown(ComboBox box, Object item) {
        return item == null ? "" : box.getConverter().toString(item);
    }

    /** The real file system, except that some paths exist as readable files. */
    private static final class PresentFiles implements FileSystemAccess {

        private final FileSystemAccess real;

        private final Set<Path> present;

        PresentFiles(FileSystemAccess real, Set<Path> present) {
            this.real = Objects.requireNonNull(real, "real");
            this.present = new HashSet<>(present);
        }

        @Override
        public boolean exists(Path path) {
            return present.contains(path) || real.exists(path);
        }

        @Override
        public boolean isReadable(Path path) {
            return present.contains(path) || real.isReadable(path);
        }

        @Override
        public boolean isDirectory(Path path) {
            return !present.contains(path) && real.isDirectory(path);
        }

        @Override
        public void createDirectories(Path path) throws IOException {
            real.createDirectories(path);
        }

        @Override
        public Path applicationDataDirectory() {
            return real.applicationDataDirectory();
        }
    }
}
