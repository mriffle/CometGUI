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

package org.cometgui.ui.controls;

import java.util.Objects;
import javafx.beans.value.ObservableValue;
import javafx.event.EventTarget;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Skin;
import javafx.scene.control.skin.ComboBoxListViewSkin;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;

/**
 * A combo box whose choice is made when it is committed, not while its list is walked: the run
 * choosers of the Results and Provenance sections.
 *
 * <p>Acting on a run -- opening its results, reading its provenance -- costs seconds for a large
 * run. A JavaFX combo box changes what it shows on every arrow key, with its list closed and with
 * it open, so a view that acted on whatever the box came to show would act on every run a keyboard
 * user passes on the way to the one they want. Over a box given to {@link #over}, what the box
 * shows is only what it <em>shows</em>; the view is told when the choice is committed:
 *
 * <ul>
 *   <li>Up or Down on the closed box opens its list and changes nothing;
 *   <li>in the open list the arrow keys move through the items, and the view is told nothing (with
 *       nothing chosen yet, the first Down shows the first item);
 *   <li>Enter, Space or a click on an item closes the list and commits the item then shown -- that
 *       one, once;
 *   <li>Escape, a click elsewhere or the focus leaving the box closes the list and the view is
 *       asked to put the box back on what was chosen before. (The toolkit's pop-up takes the keys
 *       before the box sees them and handles Escape itself, so the rule is kept the other way
 *       round: a closing list is a choice only when Enter, Space or a click on an item closed it,
 *       and those are watched on the list itself.)
 * </ul>
 *
 * <p>A change made while the list is closed -- by a test or an assistive technology acting on the
 * control directly -- is a committed choice.
 *
 * <p>The box keeps the toolkit's own cells: what it shows for an item is its converter's text and
 * nothing else, so a click on an item is seen from the list the cells are in, not from a cell
 * factory of this class's.
 */
public final class CommittedChoice {

    private final ComboBox<?> box;

    private final Runnable commit;

    private final Runnable restore;

    /**
     * Set when Enter, Space or a click on an item is closing the list: only then is what the box
     * shows a choice.
     */
    private boolean committing;

    private CommittedChoice(ComboBox<?> box, Runnable commit, Runnable restore) {
        this.box = box;
        this.commit = commit;
        this.restore = restore;
    }

    /**
     * Makes a combo box's choice a committed one.
     *
     * @param box the combo box
     * @param shown what changes when the box shows another item: its value, or its selected index
     * @param commit told that what the box shows now is the user's choice; the view acts on it
     * @param restore told that a walk was abandoned; the view puts the box back on what was chosen
     */
    public static void over(
            ComboBox<?> box, ObservableValue<?> shown, Runnable commit, Runnable restore) {
        Objects.requireNonNull(box, "box");
        Objects.requireNonNull(shown, "shown");
        Objects.requireNonNull(commit, "commit");
        Objects.requireNonNull(restore, "restore");
        new CommittedChoice(box, commit, restore).watch(shown);
    }

    private void watch(ObservableValue<?> shown) {
        box.addEventFilter(KeyEvent.KEY_PRESSED, this::keyPressed);
        box.skinProperty().addListener((observable, before, after) -> watchList(after));
        watchList(box.getSkin());
        box.showingProperty()
                .addListener(
                        (observable, was, showing) -> {
                            if (showing) {
                                committing = false;
                                startBeforeTheFirstItem();
                            } else if (committing) {
                                committing = false;
                                commit.run();
                            } else {
                                restore.run();
                            }
                        });
        shown.addListener(
                (observable, before, after) -> {
                    // While the list is open what the box shows only follows the arrow keys; the
                    // choice is made when the list closes.
                    if (!box.isShowing()) {
                        commit.run();
                    }
                });
    }

    /**
     * The box's keys: an arrow on the closed box opens its list instead of changing what it shows,
     * and Enter or Space in the open list makes the item shown the choice.
     */
    private void keyPressed(KeyEvent pressed) {
        KeyCode code = pressed.getCode();
        if (box.isShowing()) {
            if (commits(code)) {
                committing = true;
            }
            return;
        }
        boolean arrow =
                code == KeyCode.UP
                        || code == KeyCode.DOWN
                        || code == KeyCode.KP_UP
                        || code == KeyCode.KP_DOWN;
        if (arrow && !pressed.isAltDown()) {
            // Alt with an arrow is the toolkit's own key for opening the list; left to it.
            box.show();
            pressed.consume();
        }
    }

    /**
     * Watches the box's list for what makes a choice. While the list is open the toolkit hands
     * every key to the list first, and the list closes itself on Enter and Space before the box is
     * told, so the keys are seen here or not at all; and a mouse press in the list is a choice when
     * it is on an item, not on the empty space below the last one or on the scroll bar.
     */
    private void watchList(Skin<?> skin) {
        if (skin instanceof ComboBoxListViewSkin<?> listSkin) {
            Node list = listSkin.getPopupContent();
            list.addEventFilter(
                    KeyEvent.KEY_PRESSED,
                    pressed -> {
                        if (commits(pressed.getCode())) {
                            committing = true;
                        }
                    });
            list.addEventFilter(
                    MouseEvent.MOUSE_PRESSED,
                    pressed -> committing = onAnItem(pressed.getTarget()));
        }
    }

    /**
     * With nothing chosen, makes the first Down in the open list show the first item. The toolkit's
     * list starts with its focus on the first row whether or not that row is selected, and Down
     * moves on from the focus, so without this the first item would be passed over and could only
     * be reached by going back up to it.
     */
    private void startBeforeTheFirstItem() {
        if (box.getSelectionModel().getSelectedIndex() < 0
                && box.getSkin() instanceof ComboBoxListViewSkin<?> listSkin
                && listSkin.getPopupContent() instanceof ListView<?> list) {
            list.getFocusModel().focus(-1);
        }
    }

    private static boolean commits(KeyCode code) {
        return code == KeyCode.ENTER || code == KeyCode.SPACE;
    }

    /** Whether what the pointer pressed is a cell of the list that holds an item, or inside one. */
    private static boolean onAnItem(EventTarget pressed) {
        Node node = pressed instanceof Node target ? target : null;
        while (node != null && !(node instanceof ListCell<?>)) {
            node = node.getParent();
        }
        return node instanceof ListCell<?> cell && !cell.isEmpty();
    }
}
