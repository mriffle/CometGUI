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

package org.cometgui.app.uidriver;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Labeled;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.KeyCode;

/**
 * Everything both drivers do the same way: marshalling onto the JavaFX application thread, and
 * reading scene-graph state once there.
 *
 * <p>Only the four synthetic-input methods and the node lookup differ between {@link
 * TestFxUiDriver} and {@link RobotFxUiDriver}, which is the point: when the two drivers disagree
 * about what the application did, the difference is in the input mechanism and nowhere else.
 *
 * <p>Package-private, because it is an implementation detail of this package rather than something
 * a test should name.
 */
abstract class AbstractFxUiDriver implements FxUiDriver {

    /**
     * How long to wait for the application thread. Generous because a first CSS pass on a cold JVM
     * is slow; it is a deadlock detector, not a timing assumption, and no assertion depends on the
     * work taking less than any particular time.
     */
    static final long TIMEOUT_SECONDS = 60;

    private final RunningApplication application;

    /**
     * The base of a driver.
     *
     * <p>It deliberately does no argument checking: the two implementations are {@code final} and
     * check the argument before calling this, and a constructor that can throw in a class that is
     * neither final nor private is what SpotBugs reports as {@code CT_CONSTRUCTOR_THROW} at {@code
     * threshold=Low}. Fixed in the code rather than by adding an exclusion.
     *
     * @param application the application to drive, already checked for {@code null}
     */
    AbstractFxUiDriver(RunningApplication application) {
        this.application = application;
    }

    /** The application under test. */
    final RunningApplication application() {
        return application;
    }

    /**
     * The node with this identifier, or {@code null}. Called on the JavaFX application thread.
     *
     * @param id the stable identifier, without a leading {@code #}
     * @return the node, or {@code null} if the application has no node with that identifier
     */
    abstract Node lookup(String id);

    /**
     * Waits until everything already queued on the application thread has run.
     *
     * <p>This is how synthetic input is followed by an assertion without a sleep: the events the
     * robot generated are queued on the application thread ahead of this empty task, so when this
     * returns they have been delivered.
     */
    final void barrier() {
        onFxThread(() -> {});
    }

    @Override
    public final Node node(String id) {
        Node found = callOnFxThread(() -> lookup(id));
        if (found == null) {
            fail(
                    "no node with the stable identifier #"
                            + id
                            + " exists in the running application (driver: "
                            + this
                            + ")");
        }
        return found;
    }

    @Override
    public final void typeInto(String id, String text) {
        clickOn(id);
        pressWith(KeyCode.CONTROL, KeyCode.A);
        type(text);
    }

    @Override
    public final boolean isVisible(String id) {
        return Boolean.TRUE.equals(
                callOnFxThread(
                        () -> {
                            Node found = lookup(id);
                            if (found == null || found.getScene() == null) {
                                return false;
                            }
                            for (Node above = found; above != null; above = above.getParent()) {
                                if (!above.isVisible()) {
                                    return false;
                                }
                            }
                            return true;
                        }));
    }

    @Override
    public final String focusedNodeId() {
        return callOnFxThread(
                () -> {
                    Scene scene = application.scene();
                    Node focused = scene.getFocusOwner();
                    return focused == null ? null : focused.getId();
                });
    }

    @Override
    public final String textOf(String id) {
        Node found = node(id);
        return callOnFxThread(
                () -> {
                    if (found instanceof Labeled labeled) {
                        return labeled.getText();
                    }
                    if (found instanceof TextInputControl text) {
                        return text.getText();
                    }
                    return fail(
                            "#"
                                    + id
                                    + " is a "
                                    + found.getClass().getName()
                                    + ", which has no text to read");
                });
    }

    @Override
    public final String accessibleTextOf(String id) {
        Node found = node(id);
        return callOnFxThread(found::getAccessibleText);
    }

    @Override
    public final boolean exists(String id) {
        return Boolean.TRUE.equals(callOnFxThread(() -> lookup(id) != null));
    }

    @Override
    public final boolean isDisabled(String id) {
        Node found = node(id);
        return Boolean.TRUE.equals(callOnFxThread(found::isDisabled));
    }

    @Override
    public final void pressKey(String key) {
        press(keyNamed(key));
    }

    @Override
    public final void pressKeyWith(String modifier, String key) {
        pressWith(keyNamed(modifier), keyNamed(key));
    }

    @Override
    public final void select(String id, String item) {
        Objects.requireNonNull(item, "item");
        ComboBox<?> box = comboBox(id);
        List<String> items = itemsOf(id);
        int target = items.indexOf(item);
        if (target < 0) {
            fail("#" + id + " offers no item shown as \"" + item + "\"; it offers " + items);
        }
        if (items.lastIndexOf(item) != target) {
            fail("#" + id + " offers more than one item shown as \"" + item + "\": " + items);
        }
        if (Boolean.TRUE.equals(callOnFxThread(box::isEditable))) {
            fail("#" + id + " is an editable combo box; select chooses from a closed list");
        }
        if (isDisabled(id)) {
            fail("#" + id + " is disabled, so a user cannot choose \"" + item + "\" in it");
        }
        clickOn(id);
        if (!Boolean.TRUE.equals(callOnFxThread(box::isShowing))) {
            fail("a click on #" + id + " did not open its list");
        }
        // one keystroke moves one item, so the list's own length bounds the walk
        for (int step = 0; step <= items.size() && !item.equals(selectedTextOf(id)); step++) {
            int current = items.indexOf(selectedTextOf(id));
            press(current < target ? KeyCode.DOWN : KeyCode.UP);
        }
        press(KeyCode.ENTER);
        String shown = selectedTextOf(id);
        if (!item.equals(shown)) {
            fail("#" + id + " shows \"" + shown + "\" after choosing \"" + item + "\"");
        }
        if (Boolean.TRUE.equals(callOnFxThread(box::isShowing))) {
            fail("#" + id + "'s list is still open after Enter");
        }
    }

    @Override
    public final String selectedTextOf(String id) {
        ComboBox<?> box = comboBox(id);
        return callOnFxThread(() -> shownBy(box, box.getValue()));
    }

    @Override
    public final List<String> itemsOf(String id) {
        ComboBox<?> box = comboBox(id);
        return callOnFxThread(
                () -> {
                    List<String> shown = new ArrayList<>();
                    for (Object item : box.getItems()) {
                        shown.add(shownBy(box, item));
                    }
                    return List.copyOf(shown);
                });
    }

    @Override
    public final List<String> columnsOf(String id) {
        TableView<?> table = tableView(id);
        return callOnFxThread(
                () -> {
                    List<String> headings = new ArrayList<>();
                    for (TableColumn<?, ?> column : table.getVisibleLeafColumns()) {
                        headings.add(headingOf(column));
                    }
                    return List.copyOf(headings);
                });
    }

    @Override
    public final List<String> rowsOf(String id) {
        Node found = node(id);
        return callOnFxThread(
                () -> {
                    if (found instanceof TableView<?> table) {
                        return tableRows(id, table);
                    }
                    if (found instanceof ListView<?> list) {
                        return listRows(id, list);
                    }
                    return fail(
                            "#"
                                    + id
                                    + " is a "
                                    + found.getClass().getName()
                                    + ", neither a TableView nor a ListView");
                });
    }

    @Override
    public final void onFxThread(Runnable work) {
        Objects.requireNonNull(work, "work");
        if (Platform.isFxApplicationThread()) {
            /*
             * Re-entrant on purpose.  Reading state through this method is convenient enough that
             * a nested call is easy to write by accident -- node(id) inside a callOnFxThread, say
             * -- and posting a task from the application thread and then waiting for it is a
             * deadlock that presents as a test that simply never finishes.  Running inline is the
             * same work in the same place, so nothing about the semantics changes.
             */
            work.run();
            return;
        }
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(
                () -> {
                    try {
                        work.run();
                    } catch (RuntimeException | Error thrown) {
                        failure.set(thrown);
                    } finally {
                        done.countDown();
                    }
                });
        boolean finished;
        try {
            finished = done.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted waiting for the FX thread", interrupted);
        }
        assertTrue(
                finished,
                "the JavaFX application thread did not finish the work within "
                        + TIMEOUT_SECONDS
                        + "s");
        Throwable thrown = failure.get();
        if (thrown instanceof AssertionError failed) {
            // a check made on the application thread failed: that is the test's failure, as is
            throw failed;
        }
        if (thrown != null) {
            throw new IllegalStateException("work on the FX application thread failed", thrown);
        }
    }

    @Override
    public final <T> T callOnFxThread(Supplier<T> work) {
        Objects.requireNonNull(work, "work");
        AtomicReference<T> value = new AtomicReference<>();
        onFxThread(() -> value.set(work.get()));
        return value.get();
    }

    /**
     * Scrolls every scroll pane above a node so that the node is inside its viewport, the way a
     * user scrolls to a control before clicking it. Called on the JavaFX application thread, before
     * a pointer is aimed: a node scrolled out of view still has screen bounds, and a click there
     * would land on whatever is drawn at that place instead.
     *
     * @param node the node about to be clicked
     */
    static void reveal(Node node) {
        settle(node);
        for (Node above = node.getParent(); above != null; above = above.getParent()) {
            if (above instanceof ScrollPane scroll && scroll.getContent() != null) {
                Node content = scroll.getContent();
                Bounds inContent = content.sceneToLocal(node.localToScene(node.getBoundsInLocal()));
                double contentHeight = content.getBoundsInLocal().getHeight();
                double viewport = scroll.getViewportBounds().getHeight();
                if (inContent != null && contentHeight > viewport) {
                    double top = Math.max(0, inContent.getMinY() - viewport / 3);
                    scroll.setVvalue(Math.min(1, top / (contentHeight - viewport)));
                    scroll.layout();
                }
            }
        }
        settle(node);
    }

    /**
     * Applies CSS and lays out the whole scene now, rather than at the next pulse: a control the
     * application has just created (a summary entry, say) has no size until CSS has given it a
     * skin, and a position computed before that is a position the next pulse moves.
     *
     * @param node any node of the scene
     */
    private static void settle(Node node) {
        if (node.getScene() != null) {
            node.getScene().getRoot().applyCss();
            node.getScene().getRoot().layout();
        }
    }

    /**
     * The key and the modifier that type one character on a US keyboard layout, which is the layout
     * Monocle's headless keyboard uses.
     *
     * @param character the character
     * @return the key, and whether Shift is held
     * @throws AssertionError if the character is not one this driver types
     */
    static KeyStroke strokeFor(char character) {
        if (character >= 'a' && character <= 'z') {
            return new KeyStroke(
                    KeyCode.getKeyCode(String.valueOf(Character.toUpperCase(character))), false);
        }
        if (character >= 'A' && character <= 'Z') {
            return new KeyStroke(KeyCode.getKeyCode(String.valueOf(character)), true);
        }
        if (character >= '0' && character <= '9') {
            return new KeyStroke(KeyCode.getKeyCode(String.valueOf(character)), false);
        }
        return switch (character) {
            case ' ' -> new KeyStroke(KeyCode.SPACE, false);
            case '-' -> new KeyStroke(KeyCode.MINUS, false);
            case '_' -> new KeyStroke(KeyCode.MINUS, true);
            case '.' -> new KeyStroke(KeyCode.PERIOD, false);
            case '/' -> new KeyStroke(KeyCode.SLASH, false);
            case ':' -> new KeyStroke(KeyCode.SEMICOLON, true);
            case ',' -> new KeyStroke(KeyCode.COMMA, false);
            case '+' -> new KeyStroke(KeyCode.EQUALS, true);
            case '=' -> new KeyStroke(KeyCode.EQUALS, false);
            case '^' -> new KeyStroke(KeyCode.DIGIT6, true);
            default -> fail("this driver does not type the character '" + character + "'");
        };
    }

    /** One key, with or without Shift held. */
    record KeyStroke(KeyCode code, boolean shift) {}

    /**
     * The key a {@code KeyCode} constant name names.
     *
     * @param name the constant's name, for example {@code ENTER}
     * @return the key
     * @throws AssertionError if no {@code KeyCode} has that name
     */
    static KeyCode keyNamed(String name) {
        Objects.requireNonNull(name, "name");
        try {
            return KeyCode.valueOf(name);
        } catch (IllegalArgumentException unknown) {
            return fail("there is no KeyCode named \"" + name + "\"");
        }
    }

    private ComboBox<?> comboBox(String id) {
        Node found = node(id);
        if (!(found instanceof ComboBox<?> box)) {
            return fail("#" + id + " is a " + found.getClass().getName() + ", not a combo box");
        }
        return box;
    }

    private TableView<?> tableView(String id) {
        Node found = node(id);
        if (!(found instanceof TableView<?> table)) {
            return fail("#" + id + " is a " + found.getClass().getName() + ", not a TableView");
        }
        return table;
    }

    /**
     * What a combo box shows for an item: its converter's text, which is what its default cells
     * display. Called on the JavaFX application thread.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static String shownBy(ComboBox box, Object item) {
        if (box.getCellFactory() != null) {
            return fail(
                    "#"
                            + box.getId()
                            + " has its own cell factory, so its converter is not what it shows");
        }
        if (item == null) {
            return "";
        }
        return box.getConverter() == null ? item.toString() : box.getConverter().toString(item);
    }

    /** A column's heading: its text, or the text of the control that is its heading. */
    private static String headingOf(TableColumn<?, ?> column) {
        String text = column.getText();
        if ((text == null || text.isEmpty()) && column.getGraphic() instanceof Labeled heading) {
            return heading.getText() == null ? "" : heading.getText();
        }
        return text == null ? "" : text;
    }

    /** Every item of a table as its cells' text. Called on the JavaFX application thread. */
    private static List<String> tableRows(String id, TableView<?> table) {
        List<TableColumn<?, ?>> columns = new ArrayList<>(table.getVisibleLeafColumns());
        for (TableColumn<?, ?> column : columns) {
            if (column.getCellFactory() != TableColumn.DEFAULT_CELL_FACTORY) {
                fail(
                        "#"
                                + id
                                + "'s column \""
                                + headingOf(column)
                                + "\" has its own cell factory, so its cell value is not"
                                + " necessarily what it shows");
            }
        }
        List<String> rows = new ArrayList<>();
        for (int row = 0; row < table.getItems().size(); row++) {
            List<String> cells = new ArrayList<>();
            for (TableColumn<?, ?> column : columns) {
                cells.add(defaultCellText(id, headingOf(column), column.getCellData(row)));
            }
            rows.add(String.join("\t", cells));
        }
        return List.copyOf(rows);
    }

    /**
     * Every item of a list as the text its cell shows. Called on the JavaFX application thread.
     *
     * <p>A list with its own cell factory is read by making one of its cells and pointing it at
     * each item in turn -- {@code updateListView}, then {@code updateIndex}, which is what the
     * list's virtual flow does to the cells it shows -- so the text is the cell's own, whatever the
     * cell does to an item.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static List<String> listRows(String id, ListView list) {
        List<String> rows = new ArrayList<>();
        if (list.getCellFactory() == null) {
            for (Object item : list.getItems()) {
                rows.add(defaultCellText(id, "the list", item));
            }
            return List.copyOf(rows);
        }
        ListCell cell = (ListCell) list.getCellFactory().call(list);
        cell.updateListView(list);
        for (int index = 0; index < list.getItems().size(); index++) {
            cell.updateIndex(index);
            String text = cell.getText();
            if (text == null && cell.getGraphic() != null) {
                fail("#" + id + "'s cell shows item " + index + " as a graphic, not as text");
            }
            rows.add(text == null ? "" : text);
        }
        cell.updateIndex(-1);
        return List.copyOf(rows);
    }

    /** What a default table or list cell shows for a value. */
    private static String defaultCellText(String id, String where, Object value) {
        if (value instanceof Node) {
            return fail("#" + id + " shows a node in " + where + ", not text");
        }
        return value == null ? "" : value.toString();
    }

    /**
     * The centre of a node in screen coordinates. Called on the JavaFX application thread.
     *
     * <p>This is the one place a coordinate appears anywhere in the driver, and it is derived from
     * the node the test named rather than written down: {@code R-TEST-04} forbids locating a
     * control by pixel coordinates, not moving a pointer to one the toolkit computed.
     *
     * @param node the node to aim at
     * @return its centre, in screen coordinates
     */
    static Point2D centreOf(Node node) {
        Bounds screen = node.localToScreen(node.getBoundsInLocal());
        if (screen == null) {
            return fail(
                    "#"
                            + node.getId()
                            + " has no screen bounds, so it is not on screen and cannot be"
                            + " clicked");
        }
        return new Point2D(
                screen.getMinX() + screen.getWidth() / 2,
                screen.getMinY() + screen.getHeight() / 2);
    }
}
